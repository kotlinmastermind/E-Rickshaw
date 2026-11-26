"""
End-to-End Razorpay Financial Lifecycle, Security, Rotation, Idempotency & Reconciliation Tests.

Covers:
1. Fail-closed merchant account isolation & zero hardcoded merchant IDs in code
2. Webhook secret overlap rotation (RAZORPAY_WEBHOOK_SECRET + RAZORPAY_WEBHOOK_SECRET_PREVIOUS)
3. Full Webhook Event Compatibility Matrix (financial, dispute, informational, unknown)
4. Two-phase crash-safe refund lifecycle, idempotency, ceiling reservation, and refund.failed reversal
5. DB-backed wallet & ride order idempotency, lost-order recovery via receipt, and superseded order capture
6. Production fail-closed create_razorpay_order (no fake fallback in production)
7. Gift card gateway capture validation & reconciliation worker (lost orders + stuck refunds)
8. Admin finance endpoints (payments search, initiate refund, review_refund gateway execution, webhooks, anomalies)
9. Ride status polling endpoint for Driver UPI capture verification
"""
import hashlib
import hmac
import json
import pathlib
import uuid
from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, patch

import pytest
from fastapi import HTTPException
from httpx import ASGITransport, AsyncClient
from sqlalchemy import select

from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
from backend.application.services.gift_card_service import GiftCardService
from backend.application.services.payment_reconciliation_service import PaymentReconciliationService
from backend.application.services.payment_service import PaymentService
from backend.application.services.settlement_advanced_service import SettlementAdvancedService
from backend.application.services.wallet_service import WalletService
from backend.core.config import Settings, settings
from backend.domain.models import (
    CustomerProfile,
    DriverProfile,
    FareRule,
    Payment,
    PaymentReconciliationAnomaly,
    PaymentRefund,
    Ride,
    Session as DBSession,
    User,
    VehicleCategory,
)
from backend.infrastructure.security.crypto import create_jwt_token
from backend.integrations.payments.provider import RazorpayPaymentProvider, get_payment_provider
from backend.main import app


def _sign(raw: bytes, secret: str) -> str:
    return hmac.new(secret.encode("utf-8"), raw, hashlib.sha256).hexdigest()


async def _make_user_and_token(session, *, role: str = "customer") -> tuple[User, str]:
    uid = str(uuid.uuid4())
    user = User(
        id=uid,
        phone_number=f"+919{uuid.uuid4().int % 10**9:09d}",
        email=f"{role}_{uid[:8]}@vaisentra.test",
        full_name=f"Test {role.title()}",
        role=role,
        is_active=True,
    )
    session.add(user)
    sess_id = str(uuid.uuid4())
    session.add(
        DBSession(
            id=sess_id,
            user_id=uid,
            token_hash=hashlib.sha256(sess_id.encode()).hexdigest(),
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(hours=2),
        )
    )
    await session.flush()
    token = create_jwt_token(user_id=uid, role=role, session_id=sess_id, token_type="access")
    return user, token


async def _get_category_and_rule(session) -> tuple[str, str]:
    cat = (await session.execute(select(VehicleCategory))).scalars().first()
    rule = (await session.execute(select(FareRule))).scalars().first()
    return cat.id, rule.id


# ------------------------------------------------------------------------------
# 1. Merchant Account Isolation & Zero Hardcoded Secrets/IDs
# ------------------------------------------------------------------------------

def test_merchant_account_fail_closed_in_production_and_no_hardcoded_ids():
    prod_provider = RazorpayPaymentProvider(
        key_id="rzp_live_1234567890abcd",
        key_secret="live_secret_value_at_least_16_chars",
        webhook_secret="live_webhook_secret_at_least_16",
        merchant_account_id="acc_FourthMerchant01",
    )

    with patch.object(settings, "ENVIRONMENT", "production"):
        ok, _ = prod_provider.validate_merchant_account("acc_FourthMerchant01")
        assert ok is True

        ok_missing, msg_missing = prod_provider.validate_merchant_account(None)
        assert ok_missing is False
        assert "missing" in msg_missing.lower()

        ok_wrong, _ = prod_provider.validate_merchant_account("acc_WrongMerchant99")
        assert ok_wrong is False

        unconfigured = RazorpayPaymentProvider(
            key_id="rzp_live_1234567890abcd",
            key_secret="live_secret_value_at_least_16_chars",
            webhook_secret="live_webhook_secret_at_least_16",
            merchant_account_id="",
        )
        ok_unconf, msg_unconf = unconfigured.validate_merchant_account("acc_FourthMerchant01")
        assert ok_unconf is False
        assert "RAZORPAY_MERCHANT_ACCOUNT_ID" in msg_unconf

    # Verify the production merchant account literal is NEVER hardcoded in backend/ or apps/ source
    forbidden_literal = "acc_" + "TsYVnWlMsFX1O7"
    for folder in ("backend", "apps"):
        for path in pathlib.Path(folder).rglob("*"):
            if not path.is_file():
                continue
            if path.suffix not in {".py", ".kt", ".java", ".html", ".js", ".ts", ".json", ".yaml", ".yml"}:
                continue
            if "tests" in path.parts:
                continue
            content = path.read_text(encoding="utf-8", errors="ignore")
            assert forbidden_literal not in content, f"Hardcoded merchant account found in {path}"


def test_production_settings_reject_missing_merchant_id(monkeypatch):
    monkeypatch.setenv("TESTING", "0")
    with pytest.raises(ValueError, match="RAZORPAY_MERCHANT_ACCOUNT_ID"):
        Settings(
            _env_file=None,
            ENVIRONMENT="production",
            TESTING=False,
            DATABASE_URL="postgresql+asyncpg://user:pass@localhost:5432/prod_db",
            DATABASE_URL_SYNC="postgresql+psycopg2://user:pass@localhost:5432/prod_db",
            SECRET_KEY="S3cureJwtProdSecretValue_AtLeast32Chars!!",
            RAZORPAY_KEY_ID="rzp_live_1234567890abcd",
            RAZORPAY_KEY_SECRET="live_secret_value_at_least_16_chars",
            RAZORPAY_WEBHOOK_SECRET="live_webhook_secret_at_least_16",
            RAZORPAY_MERCHANT_ACCOUNT_ID="",
            CORS_ORIGINS=["https://app.vaisentra.in"],
        )


@pytest.mark.asyncio
async def test_production_create_razorpay_order_fails_closed_without_fallback():
    provider = RazorpayPaymentProvider(
        key_id="rzp_live_testkey1234",
        key_secret="live_secret_val_12345",
        webhook_secret="live_whsec_val_123456",
        merchant_account_id="acc_FourthMerchant01",
    )
    with patch.object(settings, "ENVIRONMENT", "production"), patch.object(settings, "TESTING", False):
        with patch("httpx.AsyncClient.post", new=AsyncMock(side_effect=RuntimeError("gateway down"))):
            with pytest.raises(HTTPException) as exc:
                await provider.create_razorpay_order(amount_cents=15000, receipt="rcpt_prod_1", notes={})
            assert exc.value.status_code == 502


# ------------------------------------------------------------------------------
# 2. Webhook Secret Rotation & Full Compatibility Matrix
# ------------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_webhook_secret_rotation_header_conflict_and_event_matrix(db_session):
    provider = get_payment_provider()
    orig_cur = provider.webhook_secret
    orig_prev = provider.previous_webhook_secret
    orig_merch = provider.merchant_account_id

    cur_secret = "whsec_current_2026_primary"
    prev_secret = "whsec_previous_2026_rotation"
    provider.webhook_secret = cur_secret
    provider.previous_webhook_secret = prev_secret
    provider.merchant_account_id = "acc_TestRotation01"

    try:
        user, _ = await _make_user_and_token(db_session)
        payment = Payment(
            user_id=user.id,
            amount_cents=20000,
            currency="INR",
            purpose="WALLET_TOPUP",
            status="CAPTURED",
            payment_method="RAZORPAY",
            provider_name="RAZORPAY",
            merchant_account_id="acc_TestRotation01",
            provider_order_id=f"order_rot_{uuid.uuid4().hex[:8]}",
            provider_payment_id=f"pay_rot_{uuid.uuid4().hex[:8]}",
            idempotency_key=f"idem_rot_{uuid.uuid4().hex[:8]}",
        )
        db_session.add(payment)
        await db_session.commit()

        svc = PaymentService(db_session)

        # A. Header vs payload merchant account conflict -> 403 + anomaly
        conflict_raw = json.dumps({"event": "settlement.processed", "account_id": "acc_TestRotation01", "payload": {}}).encode()
        with pytest.raises(HTTPException) as exc_conf:
            await svc.process_webhook(
                conflict_raw,
                _sign(conflict_raw, cur_secret),
                account_header="acc_OtherMerchant99",
            )
        assert exc_conf.value.status_code == 403

        # B. Informational events accepted cleanly with BOTH current & previous secret
        for idx, ev in enumerate([
            "settlement.processed",
            "payment.downtime.started",
            "payment.downtime.resolved",
            "payment.downtime.updated",
            "fund_account.validation.completed",
            "fund_account.validation.failed",
            "refund.speed_changed",
        ]):
            raw = json.dumps({"id": f"evt_info_{idx}_{uuid.uuid4().hex[:6]}", "event": ev, "account_id": "acc_TestRotation01", "payload": {}}).encode()
            secret = prev_secret if idx % 2 == 1 else cur_secret
            res = await svc.process_webhook(raw, _sign(raw, secret), account_header="acc_TestRotation01")
            assert res["status"] == "ACKNOWLEDGED_INFORMATIONAL"
            assert res["secret_slot"] == ("previous" if idx % 2 == 1 else "current")

        # C. Dispute created (signed with previous secret) -> OPEN anomaly, then dispute won -> RESOLVED
        dispute_id = f"disp_{uuid.uuid4().hex[:8]}"
        disp_create = json.dumps({
            "event": "payment.dispute.created",
            "account_id": "acc_TestRotation01",
            "payload": {"dispute": {"entity": {"id": dispute_id, "payment_id": payment.provider_payment_id, "amount": 20000, "currency": "INR", "phase": "chargeback", "status": "open"}}},
        }).encode()
        res_dc = await svc.process_webhook(disp_create, _sign(disp_create, prev_secret))
        assert res_dc["status"] == "DISPUTE_RECORDED"
        open_disp = (
            await db_session.execute(
                select(PaymentReconciliationAnomaly).where(
                    PaymentReconciliationAnomaly.provider_payment_id == payment.provider_payment_id,
                    PaymentReconciliationAnomaly.anomaly_type == "PAYMENT_DISPUTE",
                    PaymentReconciliationAnomaly.status == "OPEN",
                )
            )
        ).scalars().all()
        assert len(open_disp) >= 1

        disp_won = json.dumps({
            "event": "payment.dispute.won",
            "account_id": "acc_TestRotation01",
            "payload": {"dispute": {"entity": {"id": dispute_id, "payment_id": payment.provider_payment_id, "amount": 20000, "currency": "INR", "phase": "chargeback", "status": "won"}}},
        }).encode()
        res_dw = await svc.process_webhook(disp_won, _sign(disp_won, cur_secret))
        assert res_dw["status"] == "DISPUTE_RESOLVED"

        # D. Unsupported unknown event still fails cleanly (400 + inbox FAILED)
        unknown_raw = json.dumps({"event": "subscription.activated", "account_id": "acc_TestRotation01", "payload": {}}).encode()
        with pytest.raises(HTTPException) as exc_unk:
            await svc.process_webhook(unknown_raw, _sign(unknown_raw, cur_secret))
        assert exc_unk.value.status_code == 400
    finally:
        provider.webhook_secret = orig_cur
        provider.previous_webhook_secret = orig_prev
        provider.merchant_account_id = orig_merch


# ------------------------------------------------------------------------------
# 3. Crash-Safe Two-Phase Refunds, Ceiling Reservation & refund.failed Reversal
# ------------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_two_phase_refund_pending_then_failed_reverses_cleanly(db_session):
    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    wh_secret = "whsec_ref_lifecycle_2026"
    provider.webhook_secret = wh_secret

    try:
        user, _ = await _make_user_and_token(db_session)
        await WalletService.credit_wallet(
            session=db_session,
            user_id=user.id,
            amount_cents=50000,
            transaction_type="CREDIT",
            source="TOP_UP",
            reference_id="pay_seed_1",
            description="Seed balance",
            idempotency_key=f"seed_{uuid.uuid4().hex[:8]}",
        )
        pay = Payment(
            user_id=user.id,
            amount_cents=50000,
            currency="INR",
            purpose="WALLET_TOPUP",
            status="CAPTURED",
            payment_method="RAZORPAY",
            provider_name="RAZORPAY",
            provider_order_id=f"order_ref2p_{uuid.uuid4().hex[:8]}",
            provider_payment_id=f"pay_ref2p_{uuid.uuid4().hex[:8]}",
            idempotency_key=f"idem_ref2p_{uuid.uuid4().hex[:8]}",
        )
        db_session.add(pay)
        await db_session.commit()

        gw_ref_id = f"rfnd_{uuid.uuid4().hex[:10]}"
        settle_svc = SettlementAdvancedService(db_session)
        with patch.object(
            provider,
            "create_refund",
            new=AsyncMock(return_value={"id": gw_ref_id, "amount": 30000, "status": "pending", "acquirer_data": {}}),
        ):
            res = await settle_svc.create_refund(
                payment_id=pay.id,
                amount_cents=30000,
                reason="Customer requested async partial refund",
                is_partial=True,
                idempotency_key=f"ref_idem_{uuid.uuid4().hex[:8]}",
            )

        assert res["status"] == "GATEWAY_PROCESSING"
        await db_session.refresh(pay)
        assert pay.status == "REFUND_PENDING"
        assert (pay.refunded_amount_cents or 0) == 0  # NO premature inflation before settlement!
        bal_mid = await WalletService.get_balance(db_session, user.id)
        assert bal_mid["balance_cents"] == 50000  # NO premature wallet debit!

        # Concurrent/second refund trying to reserve more than unreserved 20000 paise must FAIL!
        with pytest.raises(HTTPException) as exc_over:
            await settle_svc.create_refund(
                payment_id=pay.id,
                amount_cents=25000,
                reason="Over-reservation check",
                is_partial=True,
            )
        assert exc_over.value.status_code == 400

        # Gateway later sends refund.failed webhook -> refund becomes FAILED, payment reverts to CAPTURED!
        wh_svc = PaymentService(db_session)
        fail_raw = json.dumps({
            "event": "refund.failed",
            "payload": {
                "refund": {"entity": {"id": gw_ref_id, "payment_id": pay.provider_payment_id, "amount": 30000, "currency": "INR", "status": "failed"}}
            },
        }).encode()
        fail_res = await wh_svc.process_webhook(fail_raw, _sign(fail_raw, wh_secret))
        assert fail_res["status"] == "REFUND_FAILED"

        await db_session.refresh(pay)
        assert pay.status == "CAPTURED"
        assert (pay.refunded_amount_cents or 0) == 0
    finally:
        provider.webhook_secret = orig_wh


@pytest.mark.asyncio
async def test_sync_processed_refund_then_duplicate_webhook_no_double_debit(db_session):
    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    wh_secret = "whsec_nodouble_2026"
    provider.webhook_secret = wh_secret

    try:
        user, _ = await _make_user_and_token(db_session)
        await WalletService.credit_wallet(
            session=db_session,
            user_id=user.id,
            amount_cents=40000,
            transaction_type="CREDIT",
            source="TOP_UP",
            reference_id="pay_nd_seed",
            description="Seed wallet",
            idempotency_key=f"nd_seed_{uuid.uuid4().hex[:8]}",
        )
        pay = Payment(
            user_id=user.id,
            amount_cents=40000,
            currency="INR",
            purpose="WALLET_TOPUP",
            status="CAPTURED",
            payment_method="RAZORPAY",
            provider_name="RAZORPAY",
            provider_order_id=f"order_nd_{uuid.uuid4().hex[:8]}",
            provider_payment_id=f"pay_nd_{uuid.uuid4().hex[:8]}",
            idempotency_key=f"idem_nd_{uuid.uuid4().hex[:8]}",
        )
        db_session.add(pay)
        await db_session.commit()

        gw_ref_id = f"rfnd_sync_{uuid.uuid4().hex[:8]}"
        settle_svc = SettlementAdvancedService(db_session)
        with patch.object(
            provider,
            "create_refund",
            new=AsyncMock(return_value={"id": gw_ref_id, "amount": 25000, "status": "processed", "acquirer_data": {"arn": "ARN123"}}),
        ):
            r1 = await settle_svc.create_refund(
                payment_id=pay.id,
                amount_cents=25000,
                reason="Immediate sync refund",
                is_partial=True,
                idempotency_key="idem_fixed_ref_key_1",
            )
            r_replay = await settle_svc.create_refund(
                payment_id=pay.id,
                amount_cents=25000,
                reason="Immediate sync refund",
                is_partial=True,
                idempotency_key="idem_fixed_ref_key_1",
            )
        assert r1["status"] == "PROCESSED"
        assert r_replay["idempotent_replay"] is True

        # Webhook arrives afterwards for the exact same gw_ref_id -> must NOT double-debit wallet!
        wh_raw = json.dumps({
            "event": "refund.processed",
            "payload": {
                "refund": {"entity": {"id": gw_ref_id, "payment_id": pay.provider_payment_id, "amount": 25000, "currency": "INR", "status": "processed"}}
            },
        }).encode()
        wh_res = await PaymentService(db_session).process_webhook(wh_raw, _sign(wh_raw, wh_secret))
        assert wh_res["status"] == "ALREADY_PROCESSED"

        bal = await WalletService.get_balance(db_session, user.id)
        assert bal["balance_cents"] == 15000
    finally:
        provider.webhook_secret = orig_wh


# ------------------------------------------------------------------------------
# 4. Order Idempotency & Superseded Order Capture
# ------------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_wallet_topup_idempotency_and_superseded_ride_order_capture(db_session):
    user, token = await _make_user_and_token(db_session)
    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    wh_secret = "whsec_ord_hist_2026"
    provider.webhook_secret = wh_secret

    try:
        gw_calls = 0

        async def _mock_order(**kwargs):
            nonlocal gw_calls
            gw_calls += 1
            return {"id": f"order_wtop_{gw_calls}", "amount": kwargs["amount_cents"], "currency": "INR", "receipt": kwargs["receipt"], "status": "created"}

        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as ac:
            with patch.object(provider, "create_razorpay_order", side_effect=_mock_order):
                res1 = await ac.post(
                    "/api/v1/wallets/topup/create-order",
                    headers={"Authorization": f"Bearer {token}"},
                    json={"amount_cents": 20000, "currency": "INR", "idempotency_key": "wallet_retry_idem_key_001"},
                )
                res2 = await ac.post(
                    "/api/v1/wallets/topup/create-order",
                    headers={"Authorization": f"Bearer {token}"},
                    json={"amount_cents": 20000, "currency": "INR", "idempotency_key": "wallet_retry_idem_key_001"},
                )
                res_conflict = await ac.post(
                    "/api/v1/wallets/topup/create-order",
                    headers={"Authorization": f"Bearer {token}"},
                    json={"amount_cents": 30000, "currency": "INR", "idempotency_key": "wallet_retry_idem_key_001"},
                )
        assert res1.status_code == 200 and res2.status_code == 200
        assert res1.json()["order_id"] == res2.json()["order_id"]
        assert res2.json()["reused_existing_order"] is True
        assert res_conflict.status_code == 409
        assert gw_calls == 1

        # Superseded order_A paid after fare adjusted to order_B, plus double-order capture detection
        cat_id, rule_id = await _get_category_and_rule(db_session)
        cust = CustomerProfile(user_id=user.id, preferred_payment_method="RAZORPAY", rating_average=5.0, rating_count=0)
        db_session.add(cust)
        await db_session.flush()
        ride = Ride(
            customer_id=cust.id,
            vehicle_category_id=cat_id,
            fare_rule_id=rule_id,
            pickup_latitude=28.61,
            pickup_longitude=77.20,
            destination_latitude=28.63,
            destination_longitude=77.22,
            pickup_address="Point A",
            destination_address="Point B",
            start_otp="1234",
            estimated_distance_km=5.0,
            estimated_duration_minutes=15.0,
            estimated_fare_cents=15000,
            final_fare_cents=15000,
            payment_method="RAZORPAY",
            status="COMPLETED",
        )
        db_session.add(ride)
        await db_session.commit()

        pay_svc = PaymentService(db_session)
        with patch.object(provider, "create_razorpay_order", side_effect=[
            {"id": "order_sup_A", "amount": 15000, "currency": "INR", "status": "created"},
            {"id": "order_sup_B", "amount": 18000, "currency": "INR", "status": "created"},
        ]):
            intent_a = await pay_svc.create_payment_intent(user.id, ride.id, "idem_ride_a_1234")
            ride.final_fare_cents = 18000
            await db_session.commit()
            intent_b = await pay_svc.create_payment_intent(user.id, ride.id, "idem_ride_b_1234")

        assert intent_a["order_id"] == "order_sup_A"
        assert intent_b["order_id"] == "order_sup_B"

        # Rider completes payment on superseded order_A! Must NOT orphan!
        wh_a = json.dumps({
            "event": "payment.captured",
            "payload": {"payment": {"entity": {"id": "pay_from_A", "order_id": "order_sup_A", "amount": 15000, "currency": "INR", "status": "captured"}}},
        }).encode()
        res_wh_a = await pay_svc.process_webhook(wh_a, _sign(wh_a, wh_secret))
        assert res_wh_a["status"] == "SUCCESS"

        # Now order_B ALSO captures -> flagged as DOUBLE_ORDER_PAYMENT reconciliation anomaly!
        wh_b = json.dumps({
            "event": "payment.captured",
            "payload": {"payment": {"entity": {"id": "pay_from_B", "order_id": "order_sup_B", "amount": 18000, "currency": "INR", "status": "captured"}}},
        }).encode()
        res_wh_b = await pay_svc.process_webhook(wh_b, _sign(wh_b, wh_secret))
        assert res_wh_b["status"] == "DUPLICATE_ORDER_CAPTURE_ANOMALY"
    finally:
        provider.webhook_secret = orig_wh


# ------------------------------------------------------------------------------
# 5. Gift Card Gateway Check, Reconciliation Worker & Admin Finance RBAC
# ------------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_gift_card_blocks_uncaptured_and_reconciliation_settles_refunds(db_session):
    user, _ = await _make_user_and_token(db_session)
    provider = get_payment_provider()

    gc_svc = GiftCardService(db_session)
    with patch.object(provider, "verify_payment_signature", return_value=True):
        with patch.object(
            provider,
            "fetch_payment",
            new=AsyncMock(return_value={"id": "pay_gc_auth", "order_id": "order_gc_auth", "amount": 50000, "currency": "INR", "status": "authorized"}),
        ):
            with pytest.raises(HTTPException) as exc_gc:
                await gc_svc.purchase_gift_card(
                    purchaser_user_id=user.id,
                    amount_cents=50000,
                    recipient_phone="+919999988888",
                    recipient_name="Friend",
                    payment_method="RAZORPAY",
                    razorpay_payment_id="pay_gc_auth",
                    razorpay_order_id="order_gc_auth",
                    razorpay_signature="sig_gc_mock_1234567890",
                )
            assert exc_gc.value.status_code == 400

    # Background refund reconciliation settles GATEWAY_PROCESSING refund once gateway says processed
    stale_pay = Payment(
        user_id=user.id,
        amount_cents=25000,
        currency="INR",
        purpose="RIDE_FARE",
        status="REFUND_PENDING",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=f"order_rec_{uuid.uuid4().hex[:8]}",
        provider_payment_id=f"pay_rec_{uuid.uuid4().hex[:8]}",
        idempotency_key=f"idem_rec_{uuid.uuid4().hex[:8]}",
    )
    db_session.add(stale_pay)
    await db_session.flush()
    stale_ref = PaymentRefund(
        payment_id=stale_pay.id,
        amount_cents=25000,
        reason="Stuck gateway refund",
        status="GATEWAY_PROCESSING",
        provider_refund_id=f"rfnd_stuck_{uuid.uuid4().hex[:8]}",
        created_at=datetime.now(timezone.utc) - timedelta(minutes=15),
    )
    db_session.add(stale_ref)
    await db_session.commit()

    with patch.object(
        provider,
        "fetch_refund",
        new=AsyncMock(return_value={"id": stale_ref.provider_refund_id, "amount": 25000, "status": "processed", "acquirer_data": {"arn": "ARN_REC_999"}}),
    ):
        rec_res = await PaymentReconciliationService.reconcile_pending_refunds()
    assert rec_res["settled_refunds"] >= 1
    await db_session.refresh(stale_pay)
    await db_session.refresh(stale_ref)
    assert stale_pay.status == "REFUNDED"
    assert stale_ref.status == "PROCESSED"


@pytest.mark.asyncio
async def test_admin_finance_endpoints_rbac_refund_execution_and_driver_ride_status(db_session):
    from backend.domain.models import AdminRole, AdminUserRole

    cust_user, cust_token = await _make_user_and_token(db_session, role="customer")
    drv_user, drv_token = await _make_user_and_token(db_session, role="driver")
    admin_user, admin_token = await _make_user_and_token(db_session, role="admin")

    sup_role = AdminRole(name=f"super_fin_{uuid.uuid4().hex[:6]}", description="Super Admin", is_superuser=True)
    db_session.add(sup_role)
    await db_session.flush()
    db_session.add(AdminUserRole(user_id=admin_user.id, role_id=sup_role.id, assigned_by=admin_user.id))

    cat_id, rule_id = await _get_category_and_rule(db_session)
    cust_prof = CustomerProfile(user_id=cust_user.id, preferred_payment_method="RAZORPAY", rating_average=5.0, rating_count=0)
    drv_prof = DriverProfile(
        user_id=drv_user.id,
        license_number=f"DL-{uuid.uuid4().hex[:8].upper()}",
        approval_status="APPROVED",
        onboarding_step="APPROVED",
        identity_verified=True,
    )
    db_session.add_all([cust_prof, drv_prof])
    await db_session.flush()

    ride = Ride(
        customer_id=cust_prof.id,
        assigned_driver_id=drv_prof.id,
        vehicle_category_id=cat_id,
        fare_rule_id=rule_id,
        pickup_latitude=28.61,
        pickup_longitude=77.20,
        destination_latitude=28.63,
        destination_longitude=77.22,
        pickup_address="Start",
        destination_address="End",
        start_otp="5678",
        estimated_distance_km=4.0,
        estimated_duration_minutes=12.0,
        estimated_fare_cents=12000,
        final_fare_cents=12000,
        payment_method="RAZORPAY",
        status="COMPLETED",
    )
    db_session.add(ride)
    await db_session.flush()

    pay = Payment(
        ride_id=ride.id,
        user_id=cust_user.id,
        customer_id=cust_prof.id,
        amount_cents=12000,
        currency="INR",
        purpose="RIDE_FARE",
        status="CREATED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=f"order_adm_{uuid.uuid4().hex[:8]}",
        idempotency_key=f"idem_adm_{uuid.uuid4().hex[:8]}",
    )
    db_session.add(pay)
    await db_session.commit()

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        # 1. Driver checks UPI capture status BEFORE payment is captured -> gateway_verified must be False!
        drv_stat_before = await ac.get(f"/api/v1/payments/ride/{ride.id}/status", headers={"Authorization": f"Bearer {drv_token}"})
        assert drv_stat_before.status_code == 200
        assert drv_stat_before.json()["gateway_verified"] is False

        # Capture payment properly
        pay.status = "CAPTURED"
        pay.provider_payment_id = f"pay_adm_{uuid.uuid4().hex[:8]}"
        await db_session.commit()

        drv_stat_after = await ac.get(f"/api/v1/payments/ride/{ride.id}/status", headers={"Authorization": f"Bearer {drv_token}"})
        assert drv_stat_after.status_code == 200
        assert drv_stat_after.json()["gateway_verified"] is True

        # 2. Non-admin blocked from /admin/payments
        unauth = await ac.get("/api/v1/admin/payments", headers={"Authorization": f"Bearer {cust_token}"})
        assert unauth.status_code == 403

        # 3. Admin searches payments & initiates real Razorpay refund via admin endpoint
        adm_search = await ac.get(f"/api/v1/admin/payments?q={pay.provider_order_id}", headers={"Authorization": f"Bearer {admin_token}"})
        assert adm_search.status_code == 200
        assert any(row["id"] == pay.id for row in adm_search.json())

        provider = get_payment_provider()
        with patch.object(
            provider,
            "create_refund",
            new=AsyncMock(return_value={"id": "rfnd_admin_exec_1", "amount": 6000, "status": "processed", "acquirer_data": {"arn": "ARN_ADM_1"}}),
        ):
            adm_ref = await ac.post(
                f"/api/v1/admin/payments/{pay.id}/refund",
                headers={"Authorization": f"Bearer {admin_token}"},
                json={"amount_cents": 6000, "reason": "Partial compensation refund approved", "is_partial": True, "idempotency_key": f"adm_idem_{uuid.uuid4().hex[:8]}"},
            )
        assert adm_ref.status_code == 200
        assert adm_ref.json()["status"] == "PROCESSED"
        assert adm_ref.json()["arn_reference"] == "ARN_ADM_1"

        # 4. Admin creates a refund request & approves it via /admin/refunds/{id}/review -> executes second partial refund
        fin_svc = AdminFinanceSupportService(db_session)
        req_row = await fin_svc.create_refund_request(admin_user.id, pay.id, 6000, "Remaining fare dispute approved")
        with patch.object(
            provider,
            "create_refund",
            new=AsyncMock(return_value={"id": "rfnd_admin_rev_2", "amount": 6000, "status": "processed", "acquirer_data": {"arn": "ARN_ADM_2"}}),
        ):
            rev_res = await ac.post(
                f"/api/v1/admin/refunds/{req_row['id']}/review",
                headers={"Authorization": f"Bearer {admin_token}"},
                json={"approve": True, "reason": "Full dispute evidence verified against trip logs"},
            )
        assert rev_res.status_code == 200
        assert rev_res.json()["status"] == "PROCESSED"
        assert rev_res.json()["arn_reference"] == "ARN_ADM_2"


@pytest.mark.asyncio
async def test_lost_order_recovery_wallet_anomaly_and_support_ledger_credit(db_session):
    from backend.application.services.support_advanced_service import SupportAdvancedService
    from backend.domain.models import Wallet, WalletTransaction

    user, _ = await _make_user_and_token(db_session, role="customer")
    cat_id, rule_id = await _get_category_and_rule(db_session)
    cust = CustomerProfile(user_id=user.id, preferred_payment_method="RAZORPAY", rating_average=5.0, rating_count=0)
    db_session.add(cust)
    await db_session.flush()

    ride = Ride(
        customer_id=cust.id,
        vehicle_category_id=cat_id,
        fare_rule_id=rule_id,
        pickup_latitude=28.61,
        pickup_longitude=77.20,
        destination_latitude=28.63,
        destination_longitude=77.22,
        pickup_address="Start L",
        destination_address="End L",
        start_otp="4321",
        estimated_distance_km=3.0,
        estimated_duration_minutes=10.0,
        estimated_fare_cents=14000,
        final_fare_cents=14000,
        payment_method="RAZORPAY",
        status="COMPLETED",
    )
    db_session.add(ride)
    await db_session.flush()

    # 1. Support goodwill refund uses WalletService.credit_wallet() + immutable ledger entry
    sup_svc = SupportAdvancedService(db_session)
    claim_res = await sup_svc.submit_refund_request(
        user_id=user.id,
        ride_id=ride.id,
        reason="DELAYED_PICKUP",
        requested_cents=5000,
        explanation="Goodwill delay credit",
    )
    assert claim_res["auto_approved"] is True
    bal = await WalletService.get_balance(db_session, user.id)
    assert bal["balance_cents"] == 5000
    tx_rows = (
        await db_session.execute(
            select(WalletTransaction).join(Wallet).where(Wallet.user_id == user.id, WalletTransaction.source == "REFUND")
        )
    ).scalars().all()
    assert len(tx_rows) == 1 and tx_rows[0].amount_cents == 5000

    # 2. Wallet balance discrepancy reconciliation records WALLET_BALANCE_DISCREPANCY anomaly
    wallet = await WalletService.get_or_create_wallet(db_session, user.id, for_update=True)
    wallet.balance_cents += 1234  # inject drift
    await db_session.commit()
    rec_bal = await WalletService.reconcile_wallet_balance(db_session, user.id, auto_correct=True)
    await db_session.commit()
    assert rec_bal["discrepancy_cents"] == 1234
    bal_anoms = (
        await db_session.execute(
            select(PaymentReconciliationAnomaly).where(PaymentReconciliationAnomaly.anomaly_type == "WALLET_BALANCE_DISCREPANCY")
        )
    ).scalars().all()
    assert len(bal_anoms) >= 1

    # 3. Lost order crash recovery via receipt lookup
    lost_pay = Payment(
        ride_id=ride.id,
        user_id=user.id,
        customer_id=cust.id,
        amount_cents=14000,
        currency="INR",
        purpose="RIDE_FARE",
        status="CREATED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=None,
        idempotency_key=f"lost_{uuid.uuid4().hex[:8]}",
        created_at=datetime.now(timezone.utc) - timedelta(minutes=10),
    )
    db_session.add(lost_pay)
    await db_session.commit()

    provider = get_payment_provider()
    with patch.object(
        provider,
        "fetch_orders_by_receipt",
        new=AsyncMock(return_value={"items": [{"id": "order_recov_lost_1", "amount": 14000, "currency": "INR", "status": "created"}]}),
    ):
        lost_res = await PaymentReconciliationService.reconcile_lost_orders()
    assert lost_res["recovered_orders"] >= 1
    await db_session.refresh(lost_pay)
    assert lost_pay.provider_order_id == "order_recov_lost_1"


# ------------------------------------------------------------------------------
# 6. Concurrent Order Lease, Network Timeout Recovery, Notification Webhooks & Concurrency
# ------------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_concurrent_duplicate_order_creation_uses_db_lease_single_gateway_call(db_session):
    import asyncio
    from backend.domain.models import PaymentOrderAttempt
    from backend.infrastructure.database.session import async_session_maker

    user, token = await _make_user_and_token(db_session, role="customer")
    cat_id, rule_id = await _get_category_and_rule(db_session)
    cust = CustomerProfile(user_id=user.id, preferred_payment_method="RAZORPAY", rating_average=5.0, rating_count=0)
    db_session.add(cust)
    await db_session.flush()
    ride = Ride(
        customer_id=cust.id,
        vehicle_category_id=cat_id,
        fare_rule_id=rule_id,
        pickup_latitude=28.61,
        pickup_longitude=77.20,
        destination_latitude=28.63,
        destination_longitude=77.22,
        pickup_address="Lease Start",
        destination_address="Lease End",
        start_otp="9876",
        estimated_distance_km=5.0,
        estimated_duration_minutes=15.0,
        estimated_fare_cents=22000,
        final_fare_cents=22000,
        payment_method="RAZORPAY",
        status="COMPLETED",
    )
    db_session.add(ride)
    await db_session.commit()

    provider = get_payment_provider()
    gw_calls = 0

    async def _slow_create_order(**kwargs):
        nonlocal gw_calls
        gw_calls += 1
        await asyncio.sleep(0.2)
        return {
            "id": f"order_conc_single_{gw_calls}",
            "amount": kwargs["amount_cents"],
            "currency": "INR",
            "receipt": kwargs["receipt"],
            "status": "created",
        }

    # Pre-seed not needed: two independent sessions racing on the same ride & wallet topup
    async def _worker_ride(idem_k: str):
        async with async_session_maker() as s:
            return await PaymentService(s).create_payment_intent(user.id, ride.id, idem_k)

    with patch.object(provider, "create_razorpay_order", side_effect=_slow_create_order):
        r1, r2 = await asyncio.gather(
            _worker_ride("conc_ride_key_alpha"),
            _worker_ride("conc_ride_key_beta"),
        )

    assert gw_calls == 1, f"Expected single Razorpay order call under race, got {gw_calls}"
    assert r1["order_id"] == r2["order_id"] == "order_conc_single_1"
    assert r1["payment_id"] == r2["payment_id"]

    # Also test two simultaneous HTTP wallet topup requests with the same idempotency_key
    gw_calls_w = 0

    async def _slow_wallet_order(**kwargs):
        nonlocal gw_calls_w
        gw_calls_w += 1
        await asyncio.sleep(0.2)
        return {
            "id": f"order_wtop_conc_{gw_calls_w}",
            "amount": kwargs["amount_cents"],
            "currency": "INR",
            "receipt": kwargs["receipt"],
            "status": "created",
        }

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        with patch.object(provider, "create_razorpay_order", side_effect=_slow_wallet_order):
            w1, w2 = await asyncio.gather(
                ac.post(
                    "/api/v1/wallets/topup/create-order",
                    headers={"Authorization": f"Bearer {token}"},
                    json={"amount_cents": 30000, "currency": "INR", "idempotency_key": "same_concurrent_wallet_key_01"},
                ),
                ac.post(
                    "/api/v1/wallets/topup/create-order",
                    headers={"Authorization": f"Bearer {token}"},
                    json={"amount_cents": 30000, "currency": "INR", "idempotency_key": "same_concurrent_wallet_key_01"},
                ),
            )
    assert w1.status_code == 200 and w2.status_code == 200
    assert gw_calls_w == 1
    assert w1.json()["order_id"] == w2.json()["order_id"] == "order_wtop_conc_1"


@pytest.mark.asyncio
async def test_order_creation_network_timeout_reconciles_on_retry_and_blocks_failed_attempts(db_session):
    from backend.domain.models import PaymentOrderAttempt

    user, _ = await _make_user_and_token(db_session, role="customer")
    cat_id, rule_id = await _get_category_and_rule(db_session)
    cust = CustomerProfile(user_id=user.id, preferred_payment_method="RAZORPAY", rating_average=5.0, rating_count=0)
    db_session.add(cust)
    await db_session.flush()
    ride = Ride(
        customer_id=cust.id,
        vehicle_category_id=cat_id,
        fare_rule_id=rule_id,
        pickup_latitude=28.61,
        pickup_longitude=77.20,
        destination_latitude=28.63,
        destination_longitude=77.22,
        pickup_address="Timeout A",
        destination_address="Timeout B",
        start_otp="2468",
        estimated_distance_km=4.0,
        estimated_duration_minutes=12.0,
        estimated_fare_cents=19000,
        final_fare_cents=19000,
        payment_method="RAZORPAY",
        status="COMPLETED",
    )
    db_session.add(ride)
    await db_session.commit()

    provider = get_payment_provider()
    svc = PaymentService(db_session)

    # 1. Gateway rejects with 400 -> attempt marked FAILED & never active
    with patch.object(provider, "create_razorpay_order", side_effect=HTTPException(status_code=400, detail="Bad amount")):
        with pytest.raises(HTTPException) as exc400:
            await svc.create_payment_intent(user.id, ride.id, "idem_timeout_flow_01")
        assert exc400.value.status_code == 400

    # 2. Gateway times out -> attempt marked UNCERTAIN, lease cleared
    with patch.object(provider, "create_razorpay_order", side_effect=TimeoutError("Razorpay timeout")):
        with pytest.raises(HTTPException) as exc504:
            await svc.create_payment_intent(user.id, ride.id, "idem_timeout_flow_01")
        assert exc504.value.status_code == 504

    attempts_mid = (
        await db_session.execute(select(PaymentOrderAttempt).where(PaymentOrderAttempt.amount_cents == 19000))
    ).scalars().all()
    statuses = {a.status for a in attempts_mid}
    assert "FAILED" in statuses
    assert "UNCERTAIN" in statuses

    # 3. Retry reconciles uncertain attempt by receipt BEFORE calling create_razorpay_order!
    rec_order_id = "order_recovered_from_uncertain_01"
    with patch.object(
        provider,
        "fetch_orders_by_receipt",
        new=AsyncMock(return_value={"items": [{"id": rec_order_id, "amount": 19000, "currency": "INR", "status": "created"}]}),
    ), patch.object(provider, "create_razorpay_order", new=AsyncMock(side_effect=AssertionError("Must not create duplicate order"))):
        retried = await svc.create_payment_intent(user.id, ride.id, "idem_timeout_flow_01")

    assert retried["order_id"] == rec_order_id
    assert retried["recovered_order"] is True


@pytest.mark.asyncio
async def test_notification_webhooks_diagnostic_sync_concurrent_refunds_and_dup_failed(db_session):
    import asyncio

    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    wh_secret = "whsec_notif_matrix_2026"
    provider.webhook_secret = wh_secret

    try:
        user, _ = await _make_user_and_token(db_session)
        await WalletService.credit_wallet(
            session=db_session,
            user_id=user.id,
            amount_cents=50000,
            transaction_type="CREDIT",
            source="TOP_UP",
            reference_id="pay_dupfail_seed",
            description="Seed for refund reversal",
            idempotency_key=f"seed_df_{uuid.uuid4().hex[:8]}",
        )
        pay = Payment(
            user_id=user.id,
            amount_cents=50000,
            currency="INR",
            purpose="WALLET_TOPUP",
            status="CAPTURED",
            payment_method="RAZORPAY",
            provider_name="RAZORPAY",
            provider_order_id=f"order_notif_{uuid.uuid4().hex[:8]}",
            provider_payment_id=f"pay_notif_{uuid.uuid4().hex[:8]}",
            idempotency_key=f"idem_notif_{uuid.uuid4().hex[:8]}",
        )
        db_session.add(pay)
        await db_session.commit()

        # 1. Notification webhooks & diagnostic endpoint sync
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as ac:
            diag = await ac.get("/api/v1/payments/webhook")
            assert diag.status_code == 200
            diag_json = diag.json()
            assert "order.notification.delivered" in diag_json["supported_events"]
            assert "order.notification.failed" in diag_json["supported_events"]
            assert "payment.dispute.action_required" in diag_json["supported_events"]
            assert "settlement.processed" in diag_json["supported_events"]

        svc = PaymentService(db_session)
        for ev in ("order.notification.delivered", "order.notification.failed"):
            raw_n = json.dumps({
                "event": ev,
                "payload": {"order": {"entity": {"id": pay.provider_order_id}}},
            }).encode()
            res_n = await svc.process_webhook(raw_n, _sign(raw_n, wh_secret))
            assert res_n["status"] == "ACKNOWLEDGED_NOTIFICATION"

        # 2. Provisional RefundLiability reversal on duplicate refund.failed delivery
        fail_gw_id = f"rfnd_fail_dup_{uuid.uuid4().hex[:8]}"
        await WalletService.process_refund_clawback(
            session=db_session,
            user_id=user.id,
            refund_amount_cents=50000,
            payment_id=pay.id,
            gateway_refund_id=fail_gw_id,
            idempotency_key=f"prov_cb_{fail_gw_id}",
        )
        pay.status = "REFUND_PENDING"
        db_session.add(
            PaymentRefund(
                payment_id=pay.id,
                amount_cents=50000,
                reason="Provisional async refund",
                status="GATEWAY_PROCESSING",
                provider_refund_id=fail_gw_id,
            )
        )
        await db_session.commit()

        fail_raw = json.dumps({
            "event": "refund.failed",
            "payload": {"refund": {"entity": {"id": fail_gw_id, "payment_id": pay.provider_payment_id, "amount": 50000, "currency": "INR", "status": "failed"}}},
        }).encode()
        fail_res_1 = await svc.process_webhook(fail_raw, _sign(fail_raw, wh_secret))
        fail_res_2 = await svc.process_webhook(fail_raw, _sign(fail_raw, wh_secret))
        assert fail_res_1["status"] == "REFUND_FAILED"
        assert fail_res_2["status"] == "ALREADY_PROCESSED"

        bal_restored = await WalletService.get_balance(db_session, user.id)
        assert bal_restored["balance_cents"] == 50000  # restored ONCE, not doubled!
        await db_session.refresh(pay)
        assert pay.status == "CAPTURED"

        # 3. Concurrent partial refunds respect ceiling (only one 35000 refund succeeds out of 50000)
        from backend.infrastructure.database.session import async_session_maker
        ref_count = 0

        async def _slow_refund(**kwargs):
            nonlocal ref_count
            ref_count += 1
            my_ref = ref_count
            await asyncio.sleep(0.1)
            return {"id": f"rfnd_c_{my_ref}", "amount": kwargs["amount_cents"], "status": "processed", "acquirer_data": {}}

        async def _try_partial(amt: int):
            async with async_session_maker() as s:
                try:
                    return await SettlementAdvancedService(s).create_refund(
                        payment_id=pay.id,
                        amount_cents=amt,
                        reason="Concurrent partial refund test",
                        is_partial=True,
                    )
                except HTTPException as exc:
                    return {"error_status": exc.status_code}

        with patch.object(provider, "create_refund", side_effect=_slow_refund):
            p1, p2 = await asyncio.gather(_try_partial(35000), _try_partial(35000))

        outcomes = [p1, p2]
        assert sum(1 for o in outcomes if o.get("status") == "PROCESSED") == 1
        # A concurrent request may observe the exhausted refund ceiling (400) or the
        # active DB reservation lease (409); both are safe fail-closed outcomes.
        assert sum(1 for o in outcomes if o.get("error_status") in {400, 409}) == 1

        # 4. Android checkout isolation check (no mutable global checkout state or placeholder keys)
        rider_repo_src = pathlib.Path("apps/customer-android/src/main/java/com/vaisentra/mobility/rider/data/RiderRepository.kt").read_text(encoding="utf-8")
        assert "activeCheckoutType" not in rider_repo_src
        assert "activeCheckoutRideId" not in rider_repo_src
        assert "rzp_test_placeholder" not in rider_repo_src
    finally:
        provider.webhook_secret = orig_wh


@pytest.mark.asyncio
async def test_strict_wallet_and_ride_verify_fail_closed_missing_gateway_fields(db_session):
    """
    Phase 2 & 12: Verify /wallets/topup/verify and /payments/razorpay/verify reject missing/null/empty
    gateway id, order_id, currency, wrong purpose, wrong amount, wrong user, and prevent duplicate credits.
    """
    import hmac
    from backend.api.deps import get_current_user

    provider = get_payment_provider()
    orig_secret = provider.key_secret
    orig_id = provider.key_id
    test_secret = "strict_verify_key_secret_123456"
    provider.key_secret = test_secret
    provider.key_id = "rzp_test_strict1234"

    try:
        owner, _ = await _make_user_and_token(db_session, role="customer")
        other_user, _ = await _make_user_and_token(db_session, role="customer")

        order_w = f"order_strict_w_{uuid.uuid4().hex[:8]}"
        pay_w = Payment(
            user_id=owner.id,
            amount_cents=30000,
            currency="INR",
            purpose="WALLET_TOPUP",
            status="CREATED",
            payment_method="RAZORPAY",
            provider_name="RAZORPAY",
            provider_order_id=order_w,
            idempotency_key=f"idem_sw_{uuid.uuid4().hex[:8]}",
        )
        cat_id, rule_id = await _get_category_and_rule(db_session)
        cust = CustomerProfile(user_id=owner.id, preferred_payment_method="RAZORPAY", rating_average=5.0, rating_count=0)
        db_session.add(cust)
        await db_session.flush()
        ride = Ride(
            customer_id=cust.id,
            vehicle_category_id=cat_id,
            fare_rule_id=rule_id,
            pickup_latitude=28.61,
            pickup_longitude=77.20,
            destination_latitude=28.63,
            destination_longitude=77.22,
            pickup_address="Verify A",
            destination_address="Verify B",
            start_otp="4444",
            estimated_distance_km=3.0,
            estimated_duration_minutes=10.0,
            estimated_fare_cents=15000,
            final_fare_cents=15000,
            payment_method="RAZORPAY",
            status="COMPLETED",
        )
        db_session.add(ride)
        await db_session.flush()
        order_r = f"order_strict_r_{uuid.uuid4().hex[:8]}"
        pay_r = Payment(
            ride_id=ride.id,
            user_id=owner.id,
            customer_id=cust.id,
            amount_cents=15000,
            currency="INR",
            purpose="RIDE_FARE",
            status="CREATED",
            payment_method="RAZORPAY",
            provider_name="RAZORPAY",
            provider_order_id=order_r,
            idempotency_key=f"idem_sr_{uuid.uuid4().hex[:8]}",
        )
        db_session.add_all([pay_w, pay_r])
        await db_session.commit()

        gw_pid_w = f"pay_gw_w_{uuid.uuid4().hex[:8]}"
        sig_w = hmac.new(test_secret.encode("utf-8"), f"{order_w}|{gw_pid_w}".encode("utf-8"), hashlib.sha256).hexdigest()

        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as ac:
            app.dependency_overrides[get_current_user] = lambda: owner

            # 1. Fail-closed on missing gateway id
            with patch.object(provider, "fetch_payment", new=AsyncMock(return_value={"order_id": order_w, "amount": 30000, "currency": "INR", "status": "captured"})):
                r_no_id = await ac.post("/api/v1/wallets/topup/verify", json={"razorpay_order_id": order_w, "razorpay_payment_id": gw_pid_w, "razorpay_signature": sig_w})
                assert r_no_id.status_code == 400

            # 2. Fail-closed on missing gateway order_id
            with patch.object(provider, "fetch_payment", new=AsyncMock(return_value={"id": gw_pid_w, "amount": 30000, "currency": "INR", "status": "captured"})):
                r_no_ord = await ac.post("/api/v1/wallets/topup/verify", json={"razorpay_order_id": order_w, "razorpay_payment_id": gw_pid_w, "razorpay_signature": sig_w})
                assert r_no_ord.status_code == 400

            # 3. Fail-closed on missing gateway currency (previously defaulted to INR!)
            with patch.object(provider, "fetch_payment", new=AsyncMock(return_value={"id": gw_pid_w, "order_id": order_w, "amount": 30000, "status": "captured"})):
                r_no_cur = await ac.post("/api/v1/wallets/topup/verify", json={"razorpay_order_id": order_w, "razorpay_payment_id": gw_pid_w, "razorpay_signature": sig_w})
                assert r_no_cur.status_code == 400

            # 4. Non-INR currency & wrong amount rejected
            with patch.object(provider, "fetch_payment", new=AsyncMock(return_value={"id": gw_pid_w, "order_id": order_w, "amount": 30000, "currency": "USD", "status": "captured"})):
                r_usd = await ac.post("/api/v1/wallets/topup/verify", json={"razorpay_order_id": order_w, "razorpay_payment_id": gw_pid_w, "razorpay_signature": sig_w})
                assert r_usd.status_code == 400

            with patch.object(provider, "fetch_payment", new=AsyncMock(return_value={"id": gw_pid_w, "order_id": order_w, "amount": 10000, "currency": "INR", "status": "captured"})):
                r_amt = await ac.post("/api/v1/wallets/topup/verify", json={"razorpay_order_id": order_w, "razorpay_payment_id": gw_pid_w, "razorpay_signature": sig_w})
                assert r_amt.status_code == 400

            # 5. Mismatched gateway id or order_id -> 409
            with patch.object(provider, "fetch_payment", new=AsyncMock(return_value={"id": "pay_other_gw", "order_id": order_w, "amount": 30000, "currency": "INR", "status": "captured"})):
                r_mid = await ac.post("/api/v1/wallets/topup/verify", json={"razorpay_order_id": order_w, "razorpay_payment_id": gw_pid_w, "razorpay_signature": sig_w})
                assert r_mid.status_code == 409

            with patch.object(provider, "fetch_payment", new=AsyncMock(return_value={"id": gw_pid_w, "order_id": "order_other_gw", "amount": 30000, "currency": "INR", "status": "captured"})):
                r_mord = await ac.post("/api/v1/wallets/topup/verify", json={"razorpay_order_id": order_w, "razorpay_payment_id": gw_pid_w, "razorpay_signature": sig_w})
                assert r_mord.status_code == 409

            # 6. Wrong purpose: submitting RIDE_FARE order to /wallets/topup/verify rejected -> 400
            gw_pid_r = f"pay_gw_r_{uuid.uuid4().hex[:8]}"
            sig_r = hmac.new(test_secret.encode("utf-8"), f"{order_r}|{gw_pid_r}".encode("utf-8"), hashlib.sha256).hexdigest()
            r_purpose = await ac.post("/api/v1/wallets/topup/verify", json={"razorpay_order_id": order_r, "razorpay_payment_id": gw_pid_r, "razorpay_signature": sig_r})
            assert r_purpose.status_code == 400

            # 7. Wrong user -> 403
            app.dependency_overrides[get_current_user] = lambda: other_user
            r_owner = await ac.post("/api/v1/wallets/topup/verify", json={"razorpay_order_id": order_w, "razorpay_payment_id": gw_pid_w, "razorpay_signature": sig_w})
            assert r_owner.status_code == 403

            # 8. Valid capture + duplicate verification credits wallet strictly ONCE
            app.dependency_overrides[get_current_user] = lambda: owner
            with patch.object(provider, "fetch_payment", new=AsyncMock(return_value={"id": gw_pid_w, "order_id": order_w, "amount": 30000, "currency": "INR", "status": "captured"})):
                ok1 = await ac.post("/api/v1/wallets/topup/verify", json={"razorpay_order_id": order_w, "razorpay_payment_id": gw_pid_w, "razorpay_signature": sig_w})
                ok2 = await ac.post("/api/v1/wallets/topup/verify", json={"razorpay_order_id": order_w, "razorpay_payment_id": gw_pid_w, "razorpay_signature": sig_w})
            assert ok1.status_code == 200 and ok1.json()["status"] == "SUCCESS"
            assert ok2.status_code == 200 and ok2.json()["status"] == "ALREADY_PROCESSED"
            assert ok2.json()["balance_cents"] == 30000

            # 9. Ride verify also fails closed when gateway omits id
            with patch.object(provider, "fetch_payment", new=AsyncMock(return_value={"order_id": order_r, "amount": 15000, "currency": "INR", "status": "captured"})):
                r_ride_noid = await ac.post("/api/v1/payments/razorpay/verify", json={"razorpay_order_id": order_r, "razorpay_payment_id": gw_pid_r, "razorpay_signature": sig_r, "ride_id": ride.id})
                assert r_ride_noid.status_code == 400
    finally:
        app.dependency_overrides.pop(get_current_user, None)
        provider.key_secret = orig_secret
        provider.key_id = orig_id


@pytest.mark.asyncio
async def test_128_char_refund_idempotency_concurrent_identical_dispute_and_goodwill_guards(db_session):
    """
    Phase 4, 9 & 12:
    - 128-char refund idempotency_key stored without truncation & identical concurrent refund calls single gateway call
    - Conflicting amount/payment for same idempotency_key returns HTTP 409
    - Unauthorized non-owner refund attempt rejected HTTP 403
    - Duplicate goodwill submit_refund_request blocked HTTP 409
    - Double admin_resolve_dispute blocked HTTP 409
    """
    import asyncio
    from backend.api.deps import get_current_user
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    from backend.application.services.support_advanced_service import SupportAdvancedService
    from backend.domain.models import FareDispute
    from backend.infrastructure.database.session import async_session_maker

    owner, _ = await _make_user_and_token(db_session, role="customer")
    attacker, _ = await _make_user_and_token(db_session, role="customer")
    admin_user, _ = await _make_user_and_token(db_session, role="admin")

    cat_id, rule_id = await _get_category_and_rule(db_session)
    cust = CustomerProfile(user_id=owner.id, preferred_payment_method="RAZORPAY", rating_average=5.0, rating_count=0)
    db_session.add(cust)
    await db_session.flush()
    ride = Ride(
        customer_id=cust.id,
        vehicle_category_id=cat_id,
        fare_rule_id=rule_id,
        pickup_latitude=28.61,
        pickup_longitude=77.20,
        destination_latitude=28.63,
        destination_longitude=77.22,
        pickup_address="Ref A",
        destination_address="Ref B",
        start_otp="5555",
        estimated_distance_km=4.0,
        estimated_duration_minutes=12.0,
        estimated_fare_cents=25000,
        final_fare_cents=25000,
        payment_method="RAZORPAY",
        status="COMPLETED",
    )
    db_session.add(ride)
    await db_session.flush()

    pay = Payment(
        ride_id=ride.id,
        user_id=owner.id,
        customer_id=cust.id,
        amount_cents=25000,
        currency="INR",
        purpose="RIDE_FARE",
        status="CAPTURED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=f"order_ref128_{uuid.uuid4().hex[:8]}",
        provider_payment_id=f"pay_ref128_{uuid.uuid4().hex[:8]}",
        idempotency_key=f"idem_r128_{uuid.uuid4().hex[:8]}",
    )
    db_session.add(pay)
    await db_session.flush()
    dispute = FareDispute(
        ride_id=ride.id,
        customer_id=cust.id,
        dispute_reason="OVERCHARGE",
        claimed_amount_cents=5000,
        customer_comment="Route fare discrepancy",
        status="SUBMITTED",
    )
    db_session.add(dispute)
    await db_session.commit()

    # 1. Duplicate goodwill self-service claim blocked after 1st credit
    sup_svc = SupportAdvancedService(db_session)
    g1 = await sup_svc.submit_refund_request(owner.id, ride.id, "LATE_ARRIVAL", 2000, "Late arrival goodwill")
    assert g1["auto_approved"] is True
    with pytest.raises(HTTPException) as dup_gw_exc:
        await sup_svc.submit_refund_request(owner.id, ride.id, "LATE_ARRIVAL", 2000, "Duplicate attempt")
    assert dup_gw_exc.value.status_code == 409

    # 2. Unauthorized user attempting partial refund -> 403
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        app.dependency_overrides[get_current_user] = lambda: attacker
        try:
            r_unauth = await ac.post(
                "/api/v1/payments/refunds/partial",
                json={"payment_id": pay.id, "amount_cents": 5000, "reason": "Unauthorized probe", "is_partial": True},
            )
            assert r_unauth.status_code == 403
        finally:
            app.dependency_overrides.pop(get_current_user, None)

    # 3. Concurrent identical refund requests with exact 128-char key -> single gateway call
    provider = get_payment_provider()
    exact_128_key = "idem_128_" + ("x" * (128 - 9))
    assert len(exact_128_key) == 128

    gw_calls = 0

    async def _slow_idem_ref(**kwargs):
        nonlocal gw_calls
        gw_calls += 1
        await asyncio.sleep(0.08)
        return {"id": f"rfnd_128_{gw_calls}", "amount": kwargs["amount_cents"], "status": "processed", "acquirer_data": {}}

    async def _worker():
        async with async_session_maker() as s:
            return await SettlementAdvancedService(s).create_refund(
                payment_id=pay.id,
                amount_cents=10000,
                reason="128-char idempotent test",
                is_partial=True,
                idempotency_key=exact_128_key,
            )

    with patch.object(provider, "create_refund", side_effect=_slow_idem_ref):
        r1, r2 = await asyncio.gather(_worker(), _worker())

    assert gw_calls == 1
    assert r1["internal_refund_id"] == r2["internal_refund_id"]
    assert r1["refund_id"] == r2["refund_id"]
    assert r1["status"] == r2["status"] == "PROCESSED"

    # Same 128-char key with different amount -> 409 Conflict
    with pytest.raises(HTTPException) as conflict_exc:
        await SettlementAdvancedService(db_session).create_refund(
            payment_id=pay.id,
            amount_cents=12000,
            reason="Amount mismatch on reused key",
            is_partial=True,
            idempotency_key=exact_128_key,
        )
    assert conflict_exc.value.status_code == 409

    # 4. Double admin dispute resolution blocked -> 409
    with patch.object(
        provider,
        "create_refund",
        new=AsyncMock(return_value={"id": f"rfnd_disp_{uuid.uuid4().hex[:8]}", "amount": 5000, "status": "processed", "acquirer_data": {}}),
    ):
        d_ok = await SettlementAdvancedService(db_session).admin_resolve_dispute(
            dispute_id=dispute.id,
            admin_id=admin_user.id,
            resolution="APPROVE_REFUND",
            resolution_notes="Verified GPS deviation evidence",
        )
    assert d_ok["status"] == "RESOLVED_REFUNDED"
    with pytest.raises(HTTPException) as d_dup:
        await SettlementAdvancedService(db_session).admin_resolve_dispute(
            dispute_id=dispute.id,
            admin_id=admin_user.id,
            resolution="APPROVE_REFUND",
            resolution_notes="Duplicate resolve click",
        )
    assert d_dup.value.status_code == 409


@pytest.mark.asyncio
async def test_webhook_malformed_json_and_non_object_payload_rejected(db_session):
    """Phase 6 & 12: Malformed JSON / non-object payloads rejected 400 cleanly after raw HMAC check."""
    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    wh_sec = "whsec_malformed_test_2026"
    provider.webhook_secret = wh_sec
    try:
        bad_json = b"{invalid-json"
        svc = PaymentService(db_session)
        with pytest.raises(HTTPException) as exc1:
            await svc.process_webhook(bad_json, _sign(bad_json, wh_sec))
        assert exc1.value.status_code == 400

        array_payload = b"[1, 2, 3]"
        with pytest.raises(HTTPException) as exc2:
            await svc.process_webhook(array_payload, _sign(array_payload, wh_sec))
        assert exc2.value.status_code == 400
    finally:
        provider.webhook_secret = orig_wh



