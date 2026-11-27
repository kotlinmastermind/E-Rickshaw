import hashlib
import hmac
import json
import os
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest
from httpx import ASGITransport, AsyncClient
from sqlalchemy import select, update

from backend.application.services.auth_service import AuthService
from backend.application.services.payment_service import PaymentService
from backend.application.services.user_governance_service import UserGovernanceService
from backend.core.config import Settings, settings
from backend.domain.models import (
    AdminRole,
    AdminUserRole,
    CustomerProfile,
    FareRule,
    Payment,
    PaymentReconciliationAnomaly,
    PaymentWebhookInbox,
    Ride,
    Session,
    User,
    VehicleCategory,
)
from backend.domain.models.extended_models import AdministrativeAuditLog
from backend.infrastructure.security.crypto import create_jwt_token, hash_password, hash_secret, verify_password
from backend.integrations.payments.provider import get_payment_provider
from backend.main import _seed_baseline_data, app


def _valid_prod_kwargs(**overrides):
    base = dict(
        _env_file=None,
        ENVIRONMENT="production",
        DEBUG=False,
        ACCESS_TOKEN_EXPIRE_MINUTES=15,
        DATABASE_URL="postgresql+asyncpg://user:strong_synth_pass_99@db.example.internal:5432/postgres",
        DATABASE_URL_SYNC="postgresql://user:strong_synth_pass_99@db.example.internal:5432/postgres",
        SECRET_KEY="SyntheticHighEntropyJwtSigningKey_AtLeast32CharsLong!",
        PAYOUT_DESTINATION_ENCRYPTION_KEY="SyntheticTestPayoutEncryptionKey_AtLeast32Chars!",
        RAZORPAY_KEY_ID="rzp_live_SyntheticKey123456",
        RAZORPAY_KEY_SECRET="synthetic_live_api_secret_1234",
        RAZORPAY_WEBHOOK_SECRET="synthetic_live_webhook_secret_5678",
        RAZORPAY_MERCHANT_ACCOUNT_ID="acc_FourthMerchant01",
    )
    base.update(overrides)
    return base


def _sign_webhook(raw_body: bytes, secret: str) -> str:
    return hmac.new(secret.encode("utf-8"), raw_body, hashlib.sha256).hexdigest()


# ----------------------------------------------------------------------
# 1. Production Configuration & Environment Fail-Closed Validation
# ----------------------------------------------------------------------

def test_production_config_requires_debug_false_and_bounded_jwt_lifetime(monkeypatch):
    monkeypatch.delenv("TESTING", raising=False)

    valid = Settings(**_valid_prod_kwargs())
    assert valid.ENVIRONMENT == "production"
    assert valid.DEBUG is False
    assert valid.ACCESS_TOKEN_EXPIRE_MINUTES == 15

    with pytest.raises(ValueError, match="DEBUG must be False"):
        Settings(**_valid_prod_kwargs(DEBUG=True))

    with pytest.raises(ValueError, match="ACCESS_TOKEN_EXPIRE_MINUTES"):
        Settings(**_valid_prod_kwargs(ACCESS_TOKEN_EXPIRE_MINUTES=10080))

    with pytest.raises(ValueError, match="ACCESS_TOKEN_EXPIRE_MINUTES"):
        Settings(**_valid_prod_kwargs(ACCESS_TOKEN_EXPIRE_MINUTES=61))


def test_production_config_rejects_weak_secrets_and_non_live_or_shared_razorpay_secrets(monkeypatch):
    monkeypatch.delenv("TESTING", raising=False)

    with pytest.raises(ValueError, match="SECRET_KEY"):
        Settings(**_valid_prod_kwargs(SECRET_KEY="too-short-secret"))

    with pytest.raises(ValueError, match="SECRET_KEY"):
        Settings(**_valid_prod_kwargs(SECRET_KEY="dev-insecure-secret-key-change-in-production"))

    with pytest.raises(ValueError, match="Live RAZORPAY_KEY_ID"):
        Settings(**_valid_prod_kwargs(RAZORPAY_KEY_ID="rzp_test_SyntheticTest123"))

    with pytest.raises(ValueError, match="RAZORPAY_WEBHOOK_SECRET"):
        Settings(**_valid_prod_kwargs(RAZORPAY_WEBHOOK_SECRET=""))

    with pytest.raises(ValueError, match="distinct from RAZORPAY_KEY_SECRET"):
        Settings(
            **_valid_prod_kwargs(
                RAZORPAY_KEY_SECRET="same_secret_value_12345",
                RAZORPAY_WEBHOOK_SECRET="same_secret_value_12345",
            )
        )

    with pytest.raises(ValueError, match="RAZORPAY_MERCHANT_ACCOUNT_ID"):
        Settings(**_valid_prod_kwargs(RAZORPAY_MERCHANT_ACCOUNT_ID="invalid_acct"))


def test_unrecognized_environment_fails_loudly(monkeypatch):
    monkeypatch.delenv("TESTING", raising=False)
    with pytest.raises(ValueError, match="Unrecognized ENVIRONMENT"):
        Settings(_env_file=None, ENVIRONMENT="prod_typo")


# ----------------------------------------------------------------------
# 2. Razorpay Webhook Verification, Merchant Account Isolation & Refunds
# ----------------------------------------------------------------------

@pytest.mark.asyncio
async def test_webhook_merchant_account_isolation_rejects_foreign_or_conflicting_accounts(db_session, customer_user, monkeypatch):
    monkeypatch.setattr(settings, "RAZORPAY_KEY_ID", "rzp_live_SyntheticKey123456")
    monkeypatch.setattr(settings, "RAZORPAY_KEY_SECRET", "synthetic_live_api_secret_1234")
    monkeypatch.setattr(settings, "RAZORPAY_WEBHOOK_SECRET", "synthetic_live_webhook_secret_5678")
    monkeypatch.setattr(settings, "RAZORPAY_MERCHANT_ACCOUNT_ID", "acc_FourthMerchant01")

    cust_prof = (
        await db_session.execute(select(CustomerProfile).where(CustomerProfile.user_id == customer_user.id))
    ).scalar_one()
    veh_cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    fare_rule = (await db_session.execute(select(FareRule))).scalars().first()

    ride = Ride(
        id=str(uuid.uuid4()),
        customer_id=cust_prof.id,
        vehicle_category_id=veh_cat.id,
        fare_rule_id=fare_rule.id,
        pickup_address="Connaught Place",
        destination_address="India Gate",
        pickup_latitude=28.6315,
        pickup_longitude=77.2167,
        destination_latitude=28.6129,
        destination_longitude=77.2295,
        start_otp="1234",
        status="COMPLETED",
        estimated_distance_km=4.2,
        estimated_duration_minutes=14.0,
        estimated_fare_cents=25000,
        final_fare_cents=25000,
    )
    order_id = f"order_{uuid.uuid4().hex[:14]}"
    payment = Payment(
        id=str(uuid.uuid4()),
        ride_id=ride.id,
        customer_id=cust_prof.id,
        amount_cents=25000,
        currency="INR",
        status="CREATED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=order_id,
        idempotency_key=f"idem_{uuid.uuid4().hex[:12]}",
    )
    db_session.add_all([ride, payment])
    await db_session.commit()

    # Case A: Wrong merchant account in payload & header (e.g. Merchant 2 instead of 4th)
    foreign_payload = json.dumps({
        "id": f"evt_{uuid.uuid4().hex[:12]}",
        "event": "payment.captured",
        "account_id": "acc_SecondMerchant99",
        "payload": {
            "payment": {
                "entity": {
                    "id": f"pay_{uuid.uuid4().hex[:14]}",
                    "order_id": order_id,
                    "amount": 25000,
                    "currency": "INR",
                    "status": "captured",
                }
            }
        },
    }).encode("utf-8")
    foreign_sig = _sign_webhook(foreign_payload, settings.RAZORPAY_WEBHOOK_SECRET)

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp_foreign = await client.post(
            "/api/v1/payments/razorpay/webhook",
            content=foreign_payload,
            headers={
                "X-Razorpay-Signature": foreign_sig,
                "X-Razorpay-Account": "acc_SecondMerchant99",
                "Content-Type": "application/json",
            },
        )
    assert resp_foreign.status_code == 403
    await db_session.refresh(payment)
    assert payment.status == "CREATED"

    foreign_digest = hashlib.sha256(foreign_payload).hexdigest()
    inbox_row = (
        await db_session.execute(
            select(PaymentWebhookInbox).where(PaymentWebhookInbox.event_digest == foreign_digest)
        )
    ).scalar_one()
    assert inbox_row.status == "RECONCILIATION_REQUIRED"

    anomaly = (
        await db_session.execute(
            select(PaymentReconciliationAnomaly).where(
                PaymentReconciliationAnomaly.webhook_inbox_id == inbox_row.id,
                PaymentReconciliationAnomaly.anomaly_type == "MERCHANT_ACCOUNT_MISMATCH",
            )
        )
    ).scalar_one()
    assert "acc_SecondMerchant99" in anomaly.reason

    # Case B: Missing account_id when strict merchant isolation is active
    missing_acct_payload = json.dumps({
        "id": f"evt_{uuid.uuid4().hex[:12]}",
        "event": "payment.captured",
        "payload": {
            "payment": {
                "entity": {
                    "id": f"pay_{uuid.uuid4().hex[:14]}",
                    "order_id": order_id,
                    "amount": 25000,
                    "currency": "INR",
                    "status": "captured",
                }
            }
        },
    }).encode("utf-8")
    missing_sig = _sign_webhook(missing_acct_payload, settings.RAZORPAY_WEBHOOK_SECRET)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp_missing = await client.post(
            "/api/v1/payments/webhook",
            content=missing_acct_payload,
            headers={"X-Razorpay-Signature": missing_sig, "Content-Type": "application/json"},
        )
    assert resp_missing.status_code == 403

    # Case C: Matching 4th merchant account succeeds and captures payment
    valid_pay_id = f"pay_{uuid.uuid4().hex[:14]}"
    valid_payload = json.dumps({
        "id": f"evt_{uuid.uuid4().hex[:12]}",
        "event": "payment.captured",
        "account_id": "acc_FourthMerchant01",
        "payload": {
            "payment": {
                "entity": {
                    "id": valid_pay_id,
                    "order_id": order_id,
                    "amount": 25000,
                    "currency": "INR",
                    "status": "captured",
                }
            }
        },
    }).encode("utf-8")
    valid_sig = _sign_webhook(valid_payload, settings.RAZORPAY_WEBHOOK_SECRET)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp_ok = await client.post(
            "/api/v1/payments/razorpay/webhook",
            content=valid_payload,
            headers={
                "X-Razorpay-Signature": valid_sig,
                "X-Razorpay-Account": "acc_FourthMerchant01",
                "Content-Type": "application/json",
            },
        )
    assert resp_ok.status_code == 200
    await db_session.refresh(payment)
    assert payment.status == "CAPTURED"
    assert payment.provider_payment_id == valid_pay_id

    # Duplicate replay is idempotent
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp_dup = await client.post(
            "/api/v1/payments/razorpay/webhook",
            content=valid_payload,
            headers={
                "X-Razorpay-Signature": valid_sig,
                "X-Razorpay-Account": "acc_FourthMerchant01",
                "Content-Type": "application/json",
            },
        )
    assert resp_dup.status_code == 200
    assert resp_dup.json()["status"] == "ALREADY_PROCESSED"

    # Case D: Partial refund processed webhook from 4th merchant updates ledger/refund status cleanly
    refund_id = f"rfnd_{uuid.uuid4().hex[:14]}"
    refund_payload = json.dumps({
        "id": f"evt_{uuid.uuid4().hex[:12]}",
        "event": "refund.processed",
        "account_id": "acc_FourthMerchant01",
        "payload": {
            "refund": {
                "entity": {
                    "id": refund_id,
                    "payment_id": valid_pay_id,
                    "amount": 5000,
                    "currency": "INR",
                    "status": "processed",
                }
            },
            "payment": {
                "entity": {
                    "id": valid_pay_id,
                    "order_id": order_id,
                    "amount": 25000,
                    "currency": "INR",
                    "status": "captured",
                }
            },
        },
    }).encode("utf-8")
    refund_sig = _sign_webhook(refund_payload, settings.RAZORPAY_WEBHOOK_SECRET)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp_refund = await client.post(
            "/api/v1/payments/webhook",
            content=refund_payload,
            headers={
                "X-Razorpay-Signature": refund_sig,
                "X-Razorpay-Account": "acc_FourthMerchant01",
                "Content-Type": "application/json",
            },
        )
    assert resp_refund.status_code == 200
    await db_session.refresh(payment)
    assert payment.status == "PARTIALLY_REFUNDED"
    assert payment.refunded_amount_cents == 5000


@pytest.mark.asyncio
async def test_webhook_rejects_raw_byte_tampering_and_exposes_refund_failed_and_live_flag(monkeypatch):
    monkeypatch.setattr(settings, "RAZORPAY_KEY_ID", "rzp_test_Synthetic1234")
    monkeypatch.setattr(settings, "RAZORPAY_WEBHOOK_SECRET", "synthetic_live_webhook_secret_5678")
    monkeypatch.setattr(settings, "RAZORPAY_MERCHANT_ACCOUNT_ID", None)

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        cfg_test = await client.get("/api/v1/payments/razorpay/config")
        assert cfg_test.status_code == 200
        assert cfg_test.json()["is_live"] is False

        monkeypatch.setattr(settings, "RAZORPAY_KEY_ID", "")
        cfg_empty = await client.get("/api/v1/payments/razorpay/config")
        assert cfg_empty.json()["is_live"] is False

        monkeypatch.setattr(settings, "RAZORPAY_KEY_ID", "rzp_live_SyntheticKey123456")
        cfg_live = await client.get("/api/v1/payments/razorpay/config")
        assert cfg_live.json()["is_live"] is True

        wh_status = await client.get("/api/v1/payments/webhook")
        assert "refund.failed" in wh_status.json()["supported_events"]

        # Sign compact JSON but transmit altered formatting bytes -> must fail HMAC
        compact = b'{"event":"payment.captured","payload":{}}'
        spaced = b'{ "event" : "payment.captured", "payload" : {} }'
        sig_compact = _sign_webhook(compact, settings.RAZORPAY_WEBHOOK_SECRET)
        bad_raw = await client.post(
            "/api/v1/payments/webhook",
            content=spaced,
            headers={"X-Razorpay-Signature": sig_compact, "Content-Type": "application/json"},
        )
        assert bad_raw.status_code == 400


@pytest.mark.asyncio
async def test_create_refund_disables_test_stub_in_production(monkeypatch):
    monkeypatch.setattr(settings, "ENVIRONMENT", "production")
    monkeypatch.setattr(settings, "RAZORPAY_KEY_ID", "rzp_live_SyntheticKey123456")
    monkeypatch.setattr(settings, "RAZORPAY_KEY_SECRET", "")
    provider = get_payment_provider()

    with pytest.raises(Exception):
        await provider.create_refund("pay_test_1234567890", 5000)


# ----------------------------------------------------------------------
# 3. Admin Bootstrap, Existing Credential Preservation & Separate MFA
# ----------------------------------------------------------------------

@pytest.mark.asyncio
async def test_startup_seed_never_creates_default_passwords_or_resets_existing_admins(db_session, monkeypatch):
    monkeypatch.delenv("BOOTSTRAP_ADMIN_EMAIL", raising=False)
    monkeypatch.delenv("BOOTSTRAP_ADMIN_PASSWORD", raising=False)
    monkeypatch.delenv("BOOTSTRAP_ADMIN_PHONE", raising=False)
    monkeypatch.delenv("ADMIN_INITIAL_PASSWORD", raising=False)

    existing_pwd = "Owner_Custom_UltraStrong#9988!"
    owner_email = f"owner_{uuid.uuid4().hex[:8]}@vaisentra.example"
    owner = User(
        id=str(uuid.uuid4()),
        phone_number=f"+9191{uuid.uuid4().int % 100000000:08d}",
        email=owner_email,
        full_name="Existing Owner",
        role="admin",
        is_active=True,
        is_verified=True,
        email_verified=True,
        mfa_enabled=True,
        mfa_secret="OWNERUNIQUEBASE32SECRETABCDEFGH",
        password_hash=hash_password(existing_pwd),
    )
    db_session.add(owner)
    await db_session.commit()

    await _seed_baseline_data()
    await db_session.refresh(owner)

    assert verify_password(existing_pwd, owner.password_hash)
    assert owner.mfa_secret == "OWNERUNIQUEBASE32SECRETABCDEFGH"
    assert owner.mfa_enabled is True

    # Ensure neither legacy hardcoded email exists unless created separately
    legacy_default = (
        await db_session.execute(select(User).where(User.email == "admin@luxurychauffeur.com"))
    ).scalar_one_or_none()
    assert legacy_default is None


@pytest.mark.asyncio
async def test_explicit_bootstrap_provisions_once_with_audit_and_distinct_mfa(db_session, monkeypatch):
    monkeypatch.setenv("ADMIN_INITIAL_PASSWORD", "Admin@1234")
    with pytest.raises(RuntimeError, match="Insecure default"):
        await _seed_baseline_data()
    monkeypatch.delenv("ADMIN_INITIAL_PASSWORD", raising=False)

    # Ensure zero-admin starting state cleanly without FK cascade conflicts on prior test rows
    await db_session.execute(update(User).where(User.role == "admin").values(role="customer"))
    await db_session.commit()

    boot_email = f"boot_{uuid.uuid4().hex[:8]}@vaisentra.example"
    boot_pwd = "ExplicitBootstrap#StrongPass2026!"
    boot_phone = f"+9182{uuid.uuid4().int % 100000000:08d}"

    monkeypatch.setenv("BOOTSTRAP_ADMIN_EMAIL", boot_email)
    monkeypatch.setenv("BOOTSTRAP_ADMIN_PASSWORD", boot_pwd)
    monkeypatch.setenv("BOOTSTRAP_ADMIN_PHONE", boot_phone)
    monkeypatch.setenv("BOOTSTRAP_ADMIN_NAME", "Primary Owner")

    assert await _seed_baseline_data() is True

    provisioned = (await db_session.execute(select(User).where(User.email == boot_email))).scalar_one()
    assert provisioned.role == "admin"
    assert provisioned.mfa_enabled is False
    assert provisioned.mfa_secret is None
    assert verify_password(boot_pwd, provisioned.password_hash)

    audit = (
        await db_session.execute(
            select(AdministrativeAuditLog).where(
                AdministrativeAuditLog.target_id == provisioned.id,
                AdministrativeAuditLog.action == "ADMIN_BOOTSTRAP_PROVISIONED",
            )
        )
    ).scalar_one()
    assert boot_pwd not in (audit.changes_json or "")

    # Subsequent startup with a different password in env must NOT overwrite existing admin
    monkeypatch.setenv("BOOTSTRAP_ADMIN_PASSWORD", "AttemptedOverwrite#Password999!")
    await _seed_baseline_data()
    await db_session.refresh(provisioned)
    assert verify_password(boot_pwd, provisioned.password_hash)

    # Create second admin and confirm each admin gets a unique TOTP secret
    second_pwd = "SecondAdmin#StrongPass2026!"
    second_admin = User(
        id=str(uuid.uuid4()),
        phone_number=f"+9183{uuid.uuid4().int % 100000000:08d}",
        email=f"second_{uuid.uuid4().hex[:8]}@vaisentra.example",
        full_name="Second Ops Admin",
        role="admin",
        is_active=True,
        is_verified=True,
        email_verified=True,
        mfa_enabled=False,
        password_hash=hash_password(second_pwd),
    )
    db_session.add(second_admin)
    await db_session.commit()

    gov = UserGovernanceService(db_session)
    setup1 = await gov.setup_admin_mfa_by_password(boot_email, boot_pwd)
    setup2 = await gov.setup_admin_mfa_by_password(second_admin.email, second_pwd)
    assert setup1["secret"] != "JBSWY3DPEHPK3PXP"
    assert setup2["secret"] != "JBSWY3DPEHPK3PXP"
    assert setup1["secret"] != setup2["secret"]


# ----------------------------------------------------------------------
# 4. Privilege Escalation & Admin Authorization Boundaries
# ----------------------------------------------------------------------

@pytest.mark.asyncio
async def test_self_registration_and_otp_oauth_cannot_create_or_login_admins(db_session, customer_user):
    gov = UserGovernanceService(db_session)
    auth = AuthService(db_session)

    # Password register cannot set role=admin
    with pytest.raises(Exception) as exc1:
        await gov.register_with_password(
            email=f"attacker_{uuid.uuid4().hex[:8]}@example.com",
            password="Attacker#Password2026",
            full_name="Fake Admin",
            phone_number=f"+9194{uuid.uuid4().int % 100000000:08d}",
            role="admin",
        )
    assert exc1.value.status_code == 403

    # Password register cannot overwrite an existing phone user's credentials
    with pytest.raises(Exception) as exc2:
        await gov.register_with_password(
            email=f"takeover_{uuid.uuid4().hex[:8]}@example.com",
            password="Takeover#Password2026",
            full_name="Hijacker",
            phone_number=customer_user.phone_number,
            role="customer",
        )
    assert exc2.value.status_code == 409

    # Google OAuth is disabled (must reject with 403 even for non-admin roles)
    with pytest.raises(Exception) as exc_google:
        await gov.oauth_login_or_register(
            provider="google",
            identity_token="id_tok",
            email=f"oauth_google_{uuid.uuid4().hex[:8]}@example.com",
            role="customer",
        )
    assert exc_google.value.status_code == 403

    # OAuth cannot self-register admin
    with pytest.raises(Exception) as exc3:
        await gov.oauth_login_or_register(
            provider="apple",
            identity_token="id_tok",
            email=f"oauth_admin_{uuid.uuid4().hex[:8]}@example.com",
            role="admin",
        )
    assert exc3.value.status_code == 403

    # OTP verify cannot self-register admin
    with pytest.raises(Exception) as exc4:
        await auth.verify_otp(
            phone_number=f"+9195{uuid.uuid4().int % 100000000:08d}",
            otp_code="123456",
            role="admin",
        )
    assert exc4.value.status_code == 403

    # Existing admin account cannot sign in via OTP even when role='customer' is supplied
    existing_admin = User(
        id=str(uuid.uuid4()),
        phone_number=f"+9186{uuid.uuid4().int % 100000000:08d}",
        email=f"otp_adm_{uuid.uuid4().hex[:8]}@vaisentra.example",
        full_name="Existing Admin No OTP",
        role="admin",
        is_active=True,
        is_verified=True,
        password_hash=hash_password("StrongAdmin#Pass2026!"),
    )
    db_session.add(existing_admin)
    await db_session.commit()

    class _StubSms:
        async def verify_otp(self, phone: str, code: str) -> bool:
            return True

    import backend.application.services.auth_service as auth_mod
    orig_get_sms = auth_mod.get_sms_provider
    auth_mod.get_sms_provider = lambda: _StubSms()
    try:
        with pytest.raises(Exception) as exc5:
            await auth.verify_otp(
                phone_number=existing_admin.phone_number,
                otp_code="123456",
                role="customer",
            )
        assert exc5.value.status_code == 403
    finally:
        auth_mod.get_sms_provider = orig_get_sms


@pytest.mark.asyncio
async def test_admin_endpoints_reject_unauthenticated_no_role_and_unverified_mfa_in_prod(db_session, monkeypatch):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        unauth = await client.get("/api/v1/admin/live-feed")
        assert unauth.status_code in (401, 403)

    # Admin with NO AdminUserRole assignment must be rejected (no Super Administrator auto-grant)
    no_role_admin = User(
        id=str(uuid.uuid4()),
        phone_number=f"+9184{uuid.uuid4().int % 100000000:08d}",
        email=f"norole_{uuid.uuid4().hex[:8]}@vaisentra.example",
        full_name="Unassigned Admin",
        role="admin",
        is_active=True,
        is_verified=True,
        mfa_enabled=True,
        mfa_secret="UNIQUEADMINSECRET32BYTESABCDEFG",
        password_hash=hash_password("StrongAdmin#Pass2026!"),
    )
    sess = Session(
        id=str(uuid.uuid4()),
        user_id=no_role_admin.id,
        token_hash=hash_secret("no-role-sess"),
        is_revoked=False,
        mfa_verified=True,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=15),
    )
    db_session.add_all([no_role_admin, sess])
    await db_session.commit()

    tok = create_jwt_token(user_id=no_role_admin.id, role="admin", session_id=sess.id, token_type="access")
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp_no_role = await client.get(
            "/api/v1/admin/live-feed",
            headers={"Authorization": f"Bearer {tok}"},
        )
    assert resp_no_role.status_code == 403

    assigned_roles = (
        await db_session.execute(select(AdminUserRole).where(AdminUserRole.user_id == no_role_admin.id))
    ).scalars().all()
    assert assigned_roles == []

    # In production, unverified MFA session must fail without mutating mfa_enabled
    super_role = (await db_session.execute(select(AdminRole).where(AdminRole.name == "Super Administrator"))).scalar_one_or_none()
    if not super_role:
        super_role = AdminRole(id=str(uuid.uuid4()), name="Super Administrator", is_superuser=True)
        db_session.add(super_role)
        await db_session.flush()

    unverified_admin = User(
        id=str(uuid.uuid4()),
        phone_number=f"+9185{uuid.uuid4().int % 100000000:08d}",
        email=f"nomfa_{uuid.uuid4().hex[:8]}@vaisentra.example",
        full_name="No MFA Admin",
        role="admin",
        is_active=True,
        is_verified=True,
        mfa_enabled=False,
        password_hash=hash_password("StrongAdmin#Pass2026!"),
    )
    db_session.add(unverified_admin)
    await db_session.flush()
    db_session.add(AdminUserRole(user_id=unverified_admin.id, role_id=super_role.id))
    unverified_sess = Session(
        id=str(uuid.uuid4()),
        user_id=unverified_admin.id,
        token_hash=hash_secret("unverified-sess"),
        is_revoked=False,
        mfa_verified=False,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=15),
    )
    db_session.add(unverified_sess)
    await db_session.commit()

    monkeypatch.setattr(settings, "ENVIRONMENT", "production")
    unverified_tok = create_jwt_token(user_id=unverified_admin.id, role="admin", session_id=unverified_sess.id, token_type="access")
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp_unverified = await client.get(
            "/api/v1/admin/live-feed",
            headers={"Authorization": f"Bearer {unverified_tok}"},
        )
    assert resp_unverified.status_code == 403
    await db_session.refresh(unverified_admin)
    assert unverified_admin.mfa_enabled is False


# ----------------------------------------------------------------------
# 5. Deployment Hygiene, Render Config, Admin SPA & Android Refresh Wiring
# ----------------------------------------------------------------------

def test_deployment_configs_and_client_auth_wiring():
    root = Path(".")

    render_yaml = (root / "render.yaml").read_text(encoding="utf-8")
    assert 'value: "15"' in render_yaml
    assert 'value: "10080"' not in render_yaml
    assert "RAZORPAY_MERCHANT_ACCOUNT_ID" in render_yaml

    gitignore = (root / ".gitignore").read_text(encoding="utf-8")
    assert "!.env.production" not in gitignore

    dockerignore = (root / ".dockerignore").read_text(encoding="utf-8")
    assert "**/.env" in dockerignore
    assert "backend/.env" in dockerignore

    admin_html = (root / "backend/static/admin/index.html").read_text(encoding="utf-8")
    assert "/api/v1/admin/session/token" not in admin_html
    assert "/api/v1/auth/admin/password/login" in admin_html
    assert "/api/v1/auth/mfa/challenge-verify" in admin_html
    assert "/api/v1/auth/admin/mfa/setup" in admin_html
    assert "/api/v1/auth/token/refresh" in admin_html

    customer_net = (
        root / "apps/customer-android/src/main/java/com/vaisentra/mobility/rider/data/NetworkClient.kt"
    ).read_text(encoding="utf-8")
    assert "api/v1/auth/token/refresh" in customer_net
    assert "KEY_REFRESH_TOKEN" in customer_net

    customer_repo = (
        root / "apps/customer-android/src/main/java/com/vaisentra/mobility/rider/data/RiderRepository.kt"
    ).read_text(encoding="utf-8")
    assert "authResponse.refreshToken" in customer_repo

@pytest.mark.asyncio
async def test_apple_oauth_fails_closed_without_creating_accounts_or_sessions(db_session, customer_user):
    transport = ASGITransport(app=app)
    original_email = customer_user.email

    before_sessions = (
        await db_session.execute(
            select(Session).where(Session.user_id == customer_user.id)
        )
    ).scalars().all()
    before_session_ids = {session.id for session in before_sessions}

    async with AsyncClient(transport=transport, base_url="http://test") as client:
        forged_existing_account = await client.post(
            "/api/v1/auth/oauth/apple",
            json={
                "identity_token": "not-a-signed-apple-token",
                "email": original_email,
                "role": "customer",
            },
        )
        forged_new_account = await client.post(
            "/api/v1/auth/oauth/apple",
            json={
                "email": f"forged_{uuid.uuid4().hex[:12]}@example.com",
                "role": "driver",
            },
        )
        admin_attempt = await client.post(
            "/api/v1/auth/oauth/apple",
            json={
                "identity_token": "forged",
                "email": f"admin_{uuid.uuid4().hex[:8]}@example.com",
                "role": "admin",
            },
        )

    assert forged_existing_account.status_code == 503
    assert forged_new_account.status_code == 503
    assert admin_attempt.status_code == 403

    await db_session.refresh(customer_user)
    assert customer_user.email == original_email
    after_sessions = (
        await db_session.execute(
            select(Session).where(Session.user_id == customer_user.id)
        )
    ).scalars().all()
    assert {session.id for session in after_sessions} == before_session_ids

    forged_user = (
        await db_session.execute(
            select(User).where(User.email.like("forged_%@example.com"))
        )
    ).scalars().first()
    assert forged_user is None

