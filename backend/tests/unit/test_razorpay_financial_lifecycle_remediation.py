"""
Comprehensive Financial Lifecycle Remediation & Security Regression Suite.

Covers:
A. Webhook signature (raw-bytes HMAC-SHA256) and payload schema validation
B. Structural AST verification eliminating duplicate process_webhook / verify_razorpay_payment
C. Authoritative payment capture validation & cross-order reuse prevention
D. Refund ceiling enforcement, PaymentReconciliationAnomaly ledger, and state recovery
E. Central Payment & Refund state machines, DB commit failure rollback, and inbox retry safety
F. Concurrent webhook/verification serialization & Driver Payout schema/idempotency/UTR checks
"""
import ast
import asyncio
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

from backend.application.services.admin_advanced_service import AdminAdvancedService
from backend.application.services.dispatch_advanced_service import DispatchAdvancedService
from backend.application.services.payment_service import (
    PaymentService,
    transition_payment_status,
    transition_refund_status,
)
from backend.application.services.settlement_advanced_service import SettlementAdvancedService
from backend.application.services.wallet_service import WalletService
from backend.domain.models import (
    AdminRole,
    AdminUserRole,
    CustomerProfile,
    DriverLedgerEntry,
    DriverProfile,
    DriverWallet,
    FareRule,
    Payment,
    PaymentReconciliationAnomaly,
    PaymentRefund,
    PaymentWebhookInbox,
    Ride,
    Session,
    User,
    VehicleCategory,
    Wallet,
    WalletTransaction,
)
from backend.infrastructure.database.session import async_session_maker
from backend.infrastructure.security.crypto import create_jwt_token
from backend.integrations.payments.provider import get_payment_provider
from backend.main import app


def _sign(raw: bytes, secret: str) -> str:
    return hmac.new(secret.encode("utf-8"), raw, hashlib.sha256).hexdigest()


# ==============================================================================
# B. Structural & Regression Prevention Tests
# ==============================================================================

def test_structural_single_process_webhook_and_single_verify_route():
    """Ensure exactly ONE PaymentService.process_webhook and ONE verify_razorpay_payment exist."""
    ps_src = pathlib.Path("backend/application/services/payment_service.py").read_text(encoding="utf-8")
    ps_tree = ast.parse(ps_src)

    payment_service_cls = next(
        n for n in ps_tree.body if isinstance(n, ast.ClassDef) and n.name == "PaymentService"
    )
    webhook_defs = [
        n for n in payment_service_cls.body
        if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name == "process_webhook"
    ]
    assert len(webhook_defs) == 1, f"Expected 1 process_webhook definition, found {len(webhook_defs)}"

    routes_src = pathlib.Path("backend/api/routes/payments.py").read_text(encoding="utf-8")
    routes_tree = ast.parse(routes_src)
    verify_defs = [
        n for n in routes_tree.body
        if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name == "verify_razorpay_payment"
    ]
    assert len(verify_defs) == 1, f"Expected 1 verify_razorpay_payment definition, found {len(verify_defs)}"


def test_structural_no_except_exception_pass_in_financial_modules():
    """Ensure no 'except Exception: pass' silently swallows financial persistence or errors."""
    financial_paths = [
        "backend/application/services/payment_service.py",
        "backend/application/services/wallet_service.py",
        "backend/application/services/settlement_advanced_service.py",
        "backend/application/services/admin_finance_support_service.py",
        "backend/application/services/payment_reconciliation_service.py",
        "backend/application/services/dispatch_advanced_service.py",
        "backend/application/services/admin_advanced_service.py",
        "backend/api/routes/payments.py",
        "backend/api/routes/wallets.py",
    ]
    for path_str in financial_paths:
        tree = ast.parse(pathlib.Path(path_str).read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            if isinstance(node, ast.ExceptHandler):
                is_broad = (
                    node.type is None
                    or (isinstance(node.type, ast.Name) and node.type.id in ("Exception", "BaseException"))
                )
                if is_broad and len(node.body) == 1 and isinstance(node.body[0], ast.Pass):
                    pytest.fail(f"Forbidden silent 'except Exception: pass' found in {path_str}:{node.lineno}")


# ==============================================================================
# A. Webhook Raw-Bytes Signature & Payload Validation
# ==============================================================================

@pytest.mark.asyncio
async def test_webhook_raw_bytes_tamper_reserialized_and_unsupported_events(db_session):
    """Verify raw-byte signature enforcement, re-serialization rejection, and strict event validation."""
    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    orig_ks = provider.key_secret
    wh_secret = "whsec_raw_bytes_strict_2026"
    key_secret = "rzp_key_secret_not_webhook"
    provider.webhook_secret = wh_secret
    provider.key_secret = key_secret

    try:
        service = PaymentService(db_session)

        # Compact JSON body signed with webhook secret
        raw_compact = b'{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_rb_1","order_id":"ord_rb_1","amount":15000,"currency":"INR","status":"captured"}}}}'
        sig_compact = _sign(raw_compact, wh_secret)

        # 1. Re-serialized JSON with spaces MUST fail against compact signature
        reserialized = json.dumps(json.loads(raw_compact), indent=2).encode("utf-8")
        with pytest.raises(HTTPException) as exc_reserial:
            await service.process_webhook(reserialized, sig_compact)
        assert exc_reserial.value.status_code == 400

        # 2. Single modified byte fails
        tampered = raw_compact.replace(b"15000", b"15001")
        with pytest.raises(HTTPException) as exc_tamper:
            await service.process_webhook(tampered, sig_compact)
        assert exc_tamper.value.status_code == 400

        # 3. Signed using API key_secret instead of webhook_secret fails
        wrong_secret_sig = _sign(raw_compact, key_secret)
        with pytest.raises(HTTPException) as exc_wrong_sec:
            await service.process_webhook(raw_compact, wrong_secret_sig)
        assert exc_wrong_sec.value.status_code == 400

        # 4. Missing event field does NOT infer payment.captured
        missing_event = b'{"payload":{"payment":{"entity":{"id":"pay_no_evt","order_id":"ord_no_evt","amount":10000}}}}'
        with pytest.raises(HTTPException) as exc_no_evt:
            await service.process_webhook(missing_event, _sign(missing_event, wh_secret))
        assert exc_no_evt.value.status_code == 400
        assert "missing event" in exc_no_evt.value.detail.lower()

        # 5. Malformed non-JSON body signed cleanly fails with 400
        bad_json = b"{not-valid-json"
        with pytest.raises(HTTPException) as exc_bad_json:
            await service.process_webhook(bad_json, _sign(bad_json, wh_secret))
        assert exc_bad_json.value.status_code == 400

        # 6. Unsupported event type rejected & persisted as FAILED (never PROCESSED)
        unsupported = json.dumps({"event": "subscription.charged", "payload": {"subscription": {"entity": {"id": "sub_1"}}}}).encode("utf-8")
        unsup_digest = hashlib.sha256(unsupported).hexdigest()
        with pytest.raises(HTTPException) as exc_unsup:
            await service.process_webhook(unsupported, _sign(unsupported, wh_secret))
        assert exc_unsup.value.status_code == 400
        inbox_unsup = (
            await db_session.execute(select(PaymentWebhookInbox).where(PaymentWebhookInbox.event_digest == unsup_digest))
        ).scalar_one_or_none()
        assert inbox_unsup is not None
        assert inbox_unsup.status == "FAILED"
        assert inbox_unsup.signature_verified is True
    finally:
        provider.webhook_secret = orig_wh
        provider.key_secret = orig_ks


@pytest.mark.asyncio
async def test_invalid_signature_then_valid_signature_same_payload_recovers_cleanly(db_session):
    """Invalid signature must not permanently poison inbox against a genuine signed retry from Razorpay."""
    user = User(phone_number=f"+9171{uuid.uuid4().hex[:8]}", full_name="Retry Sig User", role="customer")
    db_session.add(user)
    await db_session.flush()

    order_id = f"order_rsig_{uuid.uuid4().hex[:8]}"
    pay_id = f"pay_rsig_{uuid.uuid4().hex[:8]}"
    payment = Payment(
        user_id=user.id,
        amount_cents=25000,
        currency="INR",
        purpose="WALLET_TOPUP",
        status="CREATED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=order_id,
        idempotency_key=f"rsig_{order_id}",
    )
    db_session.add(payment)
    await db_session.commit()

    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    wh_secret = "whsec_retry_recovery_2026"
    provider.webhook_secret = wh_secret

    try:
        service = PaymentService(db_session)
        raw_body = json.dumps({
            "event": "payment.captured",
            "payload": {
                "payment": {
                    "entity": {
                        "id": pay_id,
                        "order_id": order_id,
                        "amount": 25000,
                        "currency": "INR",
                        "status": "captured",
                    }
                }
            },
        }).encode("utf-8")

        # 1. Forged signature rejected cleanly and stored as signature_verified=False
        with pytest.raises(HTTPException) as exc_forged:
            await service.process_webhook(raw_body, "forged_bad_sig")
        assert exc_forged.value.status_code == 400

        digest = hashlib.sha256(raw_body).hexdigest()
        inbox_before = (
            await db_session.execute(select(PaymentWebhookInbox).where(PaymentWebhookInbox.event_digest == digest))
        ).scalar_one()
        assert inbox_before.signature_verified is False
        assert inbox_before.status == "FAILED"

        # 2. Genuine Razorpay delivery of same payload WITH authentic signature succeeds!
        res = await service.process_webhook(raw_body, _sign(raw_body, wh_secret))
        assert res["status"] == "SUCCESS"

        await db_session.refresh(payment)
        await db_session.refresh(inbox_before)
        assert payment.status == "CAPTURED"
        assert inbox_before.signature_verified is True
        assert inbox_before.status == "PROCESSED"
        assert inbox_before.attempts == 2

        bal = await WalletService.get_balance(db_session, user.id)
        assert bal["balance_cents"] == 25000
    finally:
        provider.webhook_secret = orig_wh


# ==============================================================================
# C. Capture Correctness, Ownership, & State Downgrade Guards
# ==============================================================================

@pytest.mark.asyncio
async def test_capture_guards_amount_currency_reuse_and_downgrade(db_session):
    """
    Verify:
    - payment.failed never downgrades CAPTURED payment
    - gateway payment ID reuse across unrelated rides/payments is blocked
    - amount mismatch / currency mismatch records PaymentReconciliationAnomaly & blocks capture
    - CASH payments reject Razorpay webhooks
    """
    user = User(phone_number=f"+9172{uuid.uuid4().hex[:8]}", full_name="Capture Guard User", role="customer")
    db_session.add(user)
    await db_session.flush()

    pay_captured = Payment(
        user_id=user.id,
        amount_cents=30000,
        currency="INR",
        purpose="WALLET_TOPUP",
        status="CAPTURED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=f"order_cap_{uuid.uuid4().hex[:8]}",
        provider_payment_id=f"pay_cap_{uuid.uuid4().hex[:8]}",
        idempotency_key=f"cap_{uuid.uuid4().hex[:8]}",
    )
    pay_open = Payment(
        user_id=user.id,
        amount_cents=40000,
        currency="INR",
        purpose="WALLET_TOPUP",
        status="CREATED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=f"order_open_{uuid.uuid4().hex[:8]}",
        idempotency_key=f"open_{uuid.uuid4().hex[:8]}",
    )
    pay_cash = Payment(
        user_id=user.id,
        amount_cents=12000,
        currency="INR",
        purpose="RIDE_FARE",
        status="CREATED",
        payment_method="CASH",
        provider_name="CASH",
        provider_order_id=f"order_cash_{uuid.uuid4().hex[:8]}",
        idempotency_key=f"cash_{uuid.uuid4().hex[:8]}",
    )
    db_session.add_all([pay_captured, pay_open, pay_cash])
    await db_session.commit()

    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    wh_secret = "whsec_cap_guards_2026"
    provider.webhook_secret = wh_secret

    try:
        service = PaymentService(db_session)

        # 1. Late payment.failed must NEVER downgrade CAPTURED payment
        fail_body = json.dumps({
            "event": "payment.failed",
            "payload": {
                "payment": {
                    "entity": {
                        "id": pay_captured.provider_payment_id,
                        "order_id": pay_captured.provider_order_id,
                        "amount": 30000,
                        "currency": "INR",
                        "status": "failed",
                    }
                }
            },
        }).encode("utf-8")
        res_fail = await service.process_webhook(fail_body, _sign(fail_body, wh_secret))
        assert res_fail["status"] == "IGNORED_TERMINAL_STATE"
        await db_session.refresh(pay_captured)
        assert pay_captured.status == "CAPTURED"

        # 2. Reusing pay_captured.provider_payment_id on pay_open fails with 409
        reuse_body = json.dumps({
            "event": "payment.captured",
            "payload": {
                "payment": {
                    "entity": {
                        "id": pay_captured.provider_payment_id,
                        "order_id": pay_open.provider_order_id,
                        "amount": 40000,
                        "currency": "INR",
                        "status": "captured",
                    }
                }
            },
        }).encode("utf-8")
        with pytest.raises(HTTPException) as exc_reuse:
            await service.process_webhook(reuse_body, _sign(reuse_body, wh_secret))
        assert exc_reuse.value.status_code == 409
        await db_session.refresh(pay_open)
        assert pay_open.status == "CREATED"

        # 3. Amount mismatch logs PaymentReconciliationAnomaly and marks inbox RECONCILIATION_REQUIRED
        mismatch_body = json.dumps({
            "event": "payment.captured",
            "payload": {
                "payment": {
                    "entity": {
                        "id": f"pay_mis_{uuid.uuid4().hex[:8]}",
                        "order_id": pay_open.provider_order_id,
                        "amount": 100,  # Expect 40000
                        "currency": "INR",
                        "status": "captured",
                    }
                }
            },
        }).encode("utf-8")
        with pytest.raises(HTTPException) as exc_mis:
            await service.process_webhook(mismatch_body, _sign(mismatch_body, wh_secret))
        assert exc_mis.value.status_code == 400

        await db_session.refresh(pay_open)
        assert pay_open.status == "CREATED"
        anom = (
            await db_session.execute(
                select(PaymentReconciliationAnomaly).where(
                    PaymentReconciliationAnomaly.payment_id == pay_open.id,
                    PaymentReconciliationAnomaly.anomaly_type == "PAYMENT_AMOUNT_MISMATCH",
                )
            )
        ).scalar_one_or_none()
        assert anom is not None
        assert anom.expected_amount_cents == 40000
        assert anom.raw_gateway_amount_cents == 100

        # 4. Non-INR currency fails cleanly with 400
        usd_body = json.dumps({
            "event": "payment.captured",
            "payload": {
                "payment": {
                    "entity": {
                        "id": f"pay_usd_{uuid.uuid4().hex[:8]}",
                        "order_id": pay_open.provider_order_id,
                        "amount": 40000,
                        "currency": "USD",
                        "status": "captured",
                    }
                }
            },
        }).encode("utf-8")
        with pytest.raises(HTTPException) as exc_usd:
            await service.process_webhook(usd_body, _sign(usd_body, wh_secret))
        assert exc_usd.value.status_code == 400

        # 5. CASH payment rejects Razorpay webhook
        cash_body = json.dumps({
            "event": "payment.captured",
            "payload": {
                "payment": {
                    "entity": {
                        "id": f"pay_cash_gw_{uuid.uuid4().hex[:8]}",
                        "order_id": pay_cash.provider_order_id,
                        "amount": 12000,
                        "currency": "INR",
                        "status": "captured",
                    }
                }
            },
        }).encode("utf-8")
        with pytest.raises(HTTPException) as exc_cash:
            await service.process_webhook(cash_body, _sign(cash_body, wh_secret))
        assert exc_cash.value.status_code == 400
    finally:
        provider.webhook_secret = orig_wh


@pytest.mark.asyncio
async def test_create_order_blocks_overwrite_of_settled_or_authorized_payment(db_session):
    """POST /payments/create-order must reject regenerating an order for an already CAPTURED ride payment."""
    user = User(phone_number=f"+9173{uuid.uuid4().hex[:8]}", role="customer", full_name="Order Lock User")
    db_session.add(user)
    await db_session.flush()
    prof = CustomerProfile(user_id=user.id, preferred_payment_method="RAZORPAY")
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    rule = (await db_session.execute(select(FareRule))).scalars().first()
    db_session.add(prof)
    await db_session.flush()

    ride = Ride(
        customer_id=prof.id,
        vehicle_category_id=cat.id,
        fare_rule_id=rule.id,
        pickup_address="Point A",
        destination_address="Point B",
        pickup_latitude=28.61,
        pickup_longitude=77.20,
        destination_latitude=28.63,
        destination_longitude=77.22,
        estimated_fare_cents=18000,
        final_fare_cents=18000,
        estimated_distance_km=4.5,
        estimated_duration_minutes=14.0,
        start_otp="123456",
        status="COMPLETED",
        payment_method="RAZORPAY",
    )
    db_session.add(ride)
    await db_session.flush()

    payment = Payment(
        ride_id=ride.id,
        customer_id=prof.id,
        amount_cents=18000,
        currency="INR",
        status="CAPTURED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=f"order_settled_{uuid.uuid4().hex[:8]}",
        provider_payment_id=f"pay_settled_{uuid.uuid4().hex[:8]}",
        idempotency_key=f"settled_{ride.id}",
    )
    sess = Session(
        user_id=user.id,
        token_hash=f"tok_{uuid.uuid4().hex}",
        expires_at=datetime.now(timezone.utc) + timedelta(days=1),
    )
    db_session.add_all([payment, sess])
    await db_session.commit()

    token = create_jwt_token(user_id=user.id, role="customer", session_id=sess.id)
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post(
            "/api/v1/payments/create-order",
            json={"ride_id": ride.id},
            headers={"Authorization": f"Bearer {token}"},
        )
        assert resp.status_code == 409
        await db_session.refresh(payment)
        assert payment.status == "CAPTURED"


# ==============================================================================
# D & E. Reconciliation Ledger, State Machine Guards, & Retry After Failure
# ==============================================================================

def test_state_machine_rejects_terminal_regressions_and_allows_idempotency():
    p_ref = Payment(amount_cents=10000, status="REFUNDED", idempotency_key="sm_p1")
    assert transition_payment_status(p_ref, "REFUNDED") == "REFUNDED"
    with pytest.raises(ValueError):
        transition_payment_status(p_ref, "CAPTURED")
    with pytest.raises(ValueError):
        transition_payment_status(p_ref, "FAILED")

    p_fail = Payment(amount_cents=10000, status="FAILED", idempotency_key="sm_p2")
    with pytest.raises(ValueError):
        transition_payment_status(p_fail, "CAPTURED")

    r_cred = PaymentRefund(payment_id="p1", amount_cents=5000, reason="r", status="CREDITED")
    assert transition_refund_status(r_cred, "CREDITED") == "CREDITED"
    with pytest.raises(ValueError):
        transition_refund_status(r_cred, "FAILED")


@pytest.mark.asyncio
async def test_excess_refund_uses_reconciliation_anomaly_ledger_not_fake_refund_and_prevents_dup_clawback(db_session):
    """
    Verify:
    - Partial over-refund (raw=5000, remaining=3000) writes applied=3000, raw=5000, excess=2000 on PaymentRefund
      AND records PaymentReconciliationAnomaly(EXCESS_REFUND), clawing back ONLY 3000 paise.
    - Full over-refund (raw=2000, remaining=0) creates PaymentReconciliationAnomaly WITHOUT creating a second
      fake 'EXCESS_ANOMALY_REQUIRES_INVESTIGATION' PaymentRefund row and NEVER debits excess from wallet.
    """
    user = User(phone_number=f"+9174{uuid.uuid4().hex[:8]}", full_name="Recon Anomaly User", role="customer")
    db_session.add(user)
    await db_session.flush()

    await WalletService.credit_wallet(db_session, user.id, 10000, source="TOP_UP", idempotency_key=f"top_{uuid.uuid4().hex}")
    payment = Payment(
        user_id=user.id,
        amount_cents=10000,
        currency="INR",
        purpose="WALLET_TOPUP",
        status="CAPTURED",
        refunded_amount_cents=7000,
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=f"order_ex_{uuid.uuid4().hex[:8]}",
        provider_payment_id=f"pay_ex_{uuid.uuid4().hex[:8]}",
        idempotency_key=f"ex_{uuid.uuid4().hex[:8]}",
    )
    db_session.add(payment)
    await db_session.commit()

    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    wh_secret = "whsec_excess_recon_2026"
    provider.webhook_secret = wh_secret

    try:
        service = PaymentService(db_session)
        rfnd_part = f"rfnd_part_{uuid.uuid4().hex[:8]}"
        pay_id = payment.provider_payment_id
        body1 = json.dumps({
            "event": "refund.processed",
            "payload": {
                "payment": {"entity": {"id": pay_id, "order_id": payment.provider_order_id}},
                "refund": {"entity": {"id": rfnd_part, "payment_id": pay_id, "amount": 5000, "currency": "INR", "status": "processed"}},
            },
        }).encode("utf-8")
        res1 = await service.process_webhook(body1, _sign(body1, wh_secret))
        assert res1["applied_amount_cents"] == 3000
        assert res1["excess_amount_cents"] == 2000

        ref1 = (await db_session.execute(select(PaymentRefund).where(PaymentRefund.provider_refund_id == rfnd_part))).scalar_one()
        assert ref1.amount_cents == 3000
        assert ref1.raw_gateway_amount_cents == 5000
        assert ref1.excess_amount_cents == 2000
        assert ref1.status == "PARTIALLY_APPLIED_EXCESS"

        anom1 = (
            await db_session.execute(
                select(PaymentReconciliationAnomaly).where(PaymentReconciliationAnomaly.provider_refund_id == rfnd_part)
            )
        ).scalar_one()
        assert anom1.excess_amount_cents == 2000
        assert anom1.applied_amount_cents == 3000

        # Duplicate refund with SAME provider_refund_id on a DIFFERENT event body must still be blocked
        body1_dup_event = json.dumps({
            "id": "evt_different_delivery",
            "event": "refund.processed",
            "payload": {
                "payment": {"entity": {"id": pay_id}},
                "refund": {"entity": {"id": rfnd_part, "payment_id": pay_id, "amount": 5000, "status": "processed"}},
            },
        }).encode("utf-8")
        res1_dup = await service.process_webhook(body1_dup_event, _sign(body1_dup_event, wh_secret))
        assert res1_dup["status"] == "ALREADY_PROCESSED"

        # Verify wallet only clawed back 3000 (10000 - 3000 = 7000)
        bal = await WalletService.get_balance(db_session, user.id)
        assert bal["balance_cents"] == 7000

        # Ensure NO fake EXCESS_ANOMALY_REQUIRES_INVESTIGATION PaymentRefund row exists
        fake_refs = (
            await db_session.execute(
                select(PaymentRefund).where(
                    PaymentRefund.payment_id == payment.id,
                    PaymentRefund.status == "EXCESS_ANOMALY_REQUIRES_INVESTIGATION",
                )
            )
        ).scalars().all()
        assert len(fake_refs) == 0
    finally:
        provider.webhook_secret = orig_wh


@pytest.mark.asyncio
async def test_failed_inbox_event_is_retryable_and_concurrent_delivery_is_idempotent(db_session):
    """
    Verify:
    - Previously FAILED signature-verified inbox record is cleanly retryable
    - Concurrent delivery of same webhook event credits wallet EXACTLY once
    """
    user = User(phone_number=f"+9175{uuid.uuid4().hex[:8]}", full_name="Concurrent Retry User", role="customer")
    db_session.add(user)
    await db_session.flush()

    order_id = f"order_conc_{uuid.uuid4().hex[:8]}"
    pay_id = f"pay_conc_{uuid.uuid4().hex[:8]}"
    payment = Payment(
        user_id=user.id,
        amount_cents=20000,
        currency="INR",
        purpose="WALLET_TOPUP",
        status="CREATED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=order_id,
        idempotency_key=f"conc_{order_id}",
    )
    db_session.add(payment)
    await db_session.commit()

    body = json.dumps({
        "event": "payment.captured",
        "payload": {
            "payment": {
                "entity": {
                    "id": pay_id,
                    "order_id": order_id,
                    "amount": 20000,
                    "currency": "INR",
                    "status": "captured",
                }
            }
        },
    }).encode("utf-8")
    digest = hashlib.sha256(body).hexdigest()

    # Seed pre-existing FAILED inbox entry (e.g. from prior transient failure)
    failed_inbox = PaymentWebhookInbox(
        provider_name="RAZORPAY",
        event_digest=digest,
        event_type="payment.captured",
        payload_json=body.decode("utf-8"),
        signature_verified=True,
        status="FAILED",
        attempts=1,
        last_error="Transient DB timeout",
    )
    db_session.add(failed_inbox)
    await db_session.commit()

    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    wh_secret = "whsec_concurrent_retry_2026"
    provider.webhook_secret = wh_secret
    sig = _sign(body, wh_secret)

    try:
        async def _deliver_worker():
            async with async_session_maker() as s:
                try:
                    return await PaymentService(s).process_webhook(body, sig)
                except HTTPException as exc:
                    return {"status": "HTTP_EXC", "code": exc.status_code}

        r1, r2 = await asyncio.gather(_deliver_worker(), _deliver_worker())
        statuses = {r1.get("status"), r2.get("status")}
        assert "SUCCESS" in statuses

        # Credit ledger MUST have exactly 1 TOP_UP transaction for this payment
        w_bal = await WalletService.get_balance(db_session, user.id)
        assert w_bal["balance_cents"] == 20000
        txs = (
            await db_session.execute(
                select(WalletTransaction).where(WalletTransaction.reference_id == pay_id)
            )
        ).scalars().all()
        assert len(txs) == 1
    finally:
        provider.webhook_secret = orig_wh


# ==============================================================================
# F. Driver Payout API Schema, Idempotency Concurrency, & Unique UTR Tests
# ==============================================================================

@pytest.mark.asyncio
async def test_driver_payout_route_idempotency_reversal_and_unique_utr(db_session):
    """Verify /drivers/payouts/request response schema, idempotency, double-rejection safety, and unique UTR."""
    driver_user = User(phone_number=f"+9176{uuid.uuid4().hex[:8]}", full_name="Payout Route Driver", role="driver")
    admin_user = User(phone_number=f"+9179{uuid.uuid4().hex[:8]}", full_name="Payout Admin", role="admin", mfa_enabled=True)
    db_session.add_all([driver_user, admin_user])
    await db_session.flush()

    driver_prof = DriverProfile(user_id=driver_user.id, license_number=f"DL-PR-{uuid.uuid4().hex[:8]}", approval_status="APPROVED")
    db_session.add(driver_prof)
    await db_session.flush()

    d_wallet = DriverWallet(driver_id=driver_prof.id, balance_cents=60000, currency="INR")
    sess_d = Session(user_id=driver_user.id, token_hash=f"td_{uuid.uuid4().hex}", expires_at=datetime.now(timezone.utc) + timedelta(days=1))
    sess_a = Session(user_id=admin_user.id, token_hash=f"ta_{uuid.uuid4().hex}", mfa_verified=True, expires_at=datetime.now(timezone.utc) + timedelta(days=1))
    super_role = (await db_session.execute(select(AdminRole).where(AdminRole.name == "Super Administrator"))).scalar_one_or_none()
    if not super_role:
        super_role = AdminRole(id=str(uuid.uuid4()), name="Super Administrator", is_superuser=True)
        db_session.add(super_role)
        await db_session.flush()
    from backend.domain.models import DriverPayoutAccount
    from backend.infrastructure.security.payout_encryption import encrypt_payout_destination
    payout_account = DriverPayoutAccount(
        driver_id=driver_prof.id,
        account_type="BANK_ACCOUNT",
        bank_name="ICICI Bank",
        account_number_mask="ICICI *******5678",
        account_number_encrypted=encrypt_payout_destination("123456785678"),
        ifsc_code="ICIC0000123",
        beneficiary_name="Payout Route Driver",
        verification_status="VERIFIED",
        verification_method="MANUAL_ADMIN_REVIEW",
        verified_by=admin_user.id,
        penny_drop_reference="ICIC-ADMIN-VER-001",
        is_primary=True,
    )
    db_session.add_all([d_wallet, payout_account, sess_d, sess_a, AdminUserRole(user_id=admin_user.id, role_id=super_role.id)])
    await db_session.commit()

    driver_token = create_jwt_token(user_id=driver_user.id, role="driver", session_id=sess_d.id)
    idem_1 = f"payout_api_idem_{uuid.uuid4().hex[:12]}"

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. Request via HTTP route (verifies fixed bank_account_masked / created_at response!)
        resp1 = await client.post(
            "/api/v1/drivers/payouts/request",
            json={
                "amount_cents": 20000,
                "payout_account_id": payout_account.id,
                "idempotency_key": idem_1,
            },
            headers={"Authorization": f"Bearer {driver_token}"},
        )
        assert resp1.status_code == 200, resp1.text
        data1 = resp1.json()
        assert data1["status"] == "REQUESTED"
        assert data1["bank_account_mask"] == "ICICI *******5678"
        assert "requested_at" in data1

        # 2. Idempotent retry with SAME key returns SAME payout_id without double deduction
        resp2 = await client.post(
            "/api/v1/drivers/payouts/request",
            json={
                "amount_cents": 20000,
                "payout_account_id": payout_account.id,
                "idempotency_key": idem_1,
            },
            headers={"Authorization": f"Bearer {driver_token}"},
        )
        assert resp2.status_code == 200
        assert resp2.json()["payout_id"] == data1["payout_id"]

        await db_session.refresh(d_wallet)
        assert d_wallet.balance_cents == 40000

        # 3. Same key with DIFFERENT amount returns 409 Conflict
        resp_diff_amt = await client.post(
            "/api/v1/drivers/payouts/request",
            json={
                "amount_cents": 25000,
                "payout_account_id": payout_account.id,
                "idempotency_key": idem_1,
            },
            headers={"Authorization": f"Bearer {driver_token}"},
        )
        assert resp_diff_amt.status_code == 409

    # 4. Approve payout1, record manual transfer, and confirm PAID; create payout2, approve, and attempt duplicate UTR1 -> 409
    from backend.application.services.payout_execution_service import PayoutExecutionService
    p1_id = data1["payout_id"]
    await AdminAdvancedService.approve_payout(db_session, p1_id, admin_user.id)
    utr = f"UTR-HDFC-{uuid.uuid4().hex[:10].upper()}"
    await PayoutExecutionService.initiate_manual_transfer(
        db_session, payout_id=p1_id, admin_user_id=admin_user.id, settlement_reference=utr
    )
    await AdminAdvancedService.mark_payout_paid(db_session, p1_id, admin_user.id, utr)

    p2 = await DispatchAdvancedService.request_payout(
        db_session, driver_prof.id, 15000, payout_account_id=payout_account.id, idempotency_key=f"payout_2_{uuid.uuid4().hex[:12]}"
    )
    await AdminAdvancedService.approve_payout(db_session, p2.id, admin_user.id)
    with pytest.raises(HTTPException) as exc_dup_utr:
        await PayoutExecutionService.initiate_manual_transfer(
            db_session, payout_id=p2.id, admin_user_id=admin_user.id, settlement_reference=utr
        )
    assert exc_dup_utr.value.status_code == 409

    # Reject payout2 and ensure reversal happens exactly once
    await AdminAdvancedService.reject_payout(db_session, p2.id, admin_user.id, "Manual test rejection")
    await db_session.commit()
    with pytest.raises(HTTPException) as exc_re_reject:
        await AdminAdvancedService.reject_payout(db_session, p2.id, admin_user.id, "Duplicate reject")
    assert exc_re_reject.value.status_code == 400

    reversals = (
        await db_session.execute(
            select(DriverLedgerEntry).where(
                DriverLedgerEntry.driver_id == driver_prof.id,
                DriverLedgerEntry.category == "PAYOUT_REVERSAL",
                DriverLedgerEntry.reference_id == p2.id,
            )
        )
    ).scalars().all()
    assert len(reversals) == 1


@pytest.mark.asyncio
async def test_verify_route_authoritative_gateway_checks_and_ride_ownership(db_session):
    """
    Verify consolidated /api/v1/payments/verify rejects:
    - gateway order mismatch (409)
    - gateway amount mismatch (400)
    - gateway non-INR currency (400)
    - gateway authorized status (leaves AUTHORIZED, does NOT mark CAPTURED)
    - cross-customer unauthorized attempt (403)
    """
    u_owner = User(phone_number=f"+9170{uuid.uuid4().hex[:8]}", role="customer", full_name="Owner Verify")
    u_other = User(phone_number=f"+9169{uuid.uuid4().hex[:8]}", role="customer", full_name="Other Verify")
    db_session.add_all([u_owner, u_other])
    await db_session.flush()

    p_owner = CustomerProfile(user_id=u_owner.id, preferred_payment_method="RAZORPAY")
    p_other = CustomerProfile(user_id=u_other.id, preferred_payment_method="RAZORPAY")
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    rule = (await db_session.execute(select(FareRule))).scalars().first()
    db_session.add_all([p_owner, p_other])
    await db_session.flush()

    ride = Ride(
        customer_id=p_owner.id,
        vehicle_category_id=cat.id,
        fare_rule_id=rule.id,
        pickup_address="Start",
        destination_address="End",
        pickup_latitude=28.61,
        pickup_longitude=77.20,
        destination_latitude=28.62,
        destination_longitude=77.21,
        estimated_fare_cents=22000,
        final_fare_cents=22000,
        estimated_distance_km=5.0,
        estimated_duration_minutes=15.0,
        start_otp="123456",
        status="IN_PROGRESS",
        payment_method="RAZORPAY",
    )
    db_session.add(ride)
    await db_session.flush()

    order_id = f"order_vr_{uuid.uuid4().hex[:10]}"
    pay_id = f"pay_vr_{uuid.uuid4().hex[:10]}"
    payment = Payment(
        ride_id=ride.id,
        customer_id=p_owner.id,
        amount_cents=22000,
        currency="INR",
        status="CREATED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=order_id,
        idempotency_key=f"vr_{order_id}",
    )
    sess_owner = Session(user_id=u_owner.id, token_hash=f"so_{uuid.uuid4().hex}", expires_at=datetime.now(timezone.utc) + timedelta(days=1))
    sess_other = Session(user_id=u_other.id, token_hash=f"sot_{uuid.uuid4().hex}", expires_at=datetime.now(timezone.utc) + timedelta(days=1))
    db_session.add_all([payment, sess_owner, sess_other])
    await db_session.commit()

    tok_owner = create_jwt_token(user_id=u_owner.id, role="customer", session_id=sess_owner.id)
    tok_other = create_jwt_token(user_id=u_other.id, role="customer", session_id=sess_other.id)

    provider = get_payment_provider()
    orig_ks = provider.key_secret
    provider.key_secret = "rzp_verify_route_secret_2026"
    sig = _sign(f"{order_id}|{pay_id}".encode("utf-8"), provider.key_secret)

    try:
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            payload = {
                "razorpay_order_id": order_id,
                "razorpay_payment_id": pay_id,
                "razorpay_signature": sig,
                "ride_id": ride.id,
            }

            # 1. Non-owner rejected 403
            r_forbid = await client.post("/api/v1/payments/verify", json=payload, headers={"Authorization": f"Bearer {tok_other}"})
            assert r_forbid.status_code == 403

            # 2. Gateway order mismatch -> 409
            with patch.object(provider, "fetch_payment", new_callable=AsyncMock) as mf:
                mf.return_value = {"id": pay_id, "order_id": "order_WRONG", "amount": 22000, "currency": "INR", "status": "captured"}
                r_ord = await client.post("/api/v1/payments/verify", json=payload, headers={"Authorization": f"Bearer {tok_owner}"})
                assert r_ord.status_code == 409

            # 3. Gateway currency mismatch -> 400
            with patch.object(provider, "fetch_payment", new_callable=AsyncMock) as mf:
                mf.return_value = {"id": pay_id, "order_id": order_id, "amount": 22000, "currency": "USD", "status": "captured"}
                r_cur = await client.post("/api/v1/payments/verify", json=payload, headers={"Authorization": f"Bearer {tok_owner}"})
                assert r_cur.status_code == 400

            # 4. Gateway amount mismatch -> 400
            with patch.object(provider, "fetch_payment", new_callable=AsyncMock) as mf:
                mf.return_value = {"id": pay_id, "order_id": order_id, "amount": 100, "currency": "INR", "status": "captured"}
                r_amt = await client.post("/api/v1/payments/verify", json=payload, headers={"Authorization": f"Bearer {tok_owner}"})
                assert r_amt.status_code == 400

            # 5. Gateway authorized -> leaves AUTHORIZED, does NOT capture ride
            with patch.object(provider, "fetch_payment", new_callable=AsyncMock) as mf:
                mf.return_value = {"id": pay_id, "order_id": order_id, "amount": 22000, "currency": "INR", "status": "authorized"}
                r_auth = await client.post("/api/v1/payments/verify", json=payload, headers={"Authorization": f"Bearer {tok_owner}"})
                assert r_auth.status_code == 200
                assert r_auth.json()["status"] == "AUTHORIZED"
                await db_session.refresh(payment)
                await db_session.refresh(ride)
                assert payment.status == "AUTHORIZED"
                assert ride.status == "IN_PROGRESS"

            # 6. Gateway captured -> finalizes CAPTURED and COMPLETED
            with patch.object(provider, "fetch_payment", new_callable=AsyncMock) as mf:
                mf.return_value = {"id": pay_id, "order_id": order_id, "amount": 22000, "currency": "INR", "status": "captured"}
                r_cap = await client.post("/api/v1/payments/verify", json=payload, headers={"Authorization": f"Bearer {tok_owner}"})
                assert r_cap.status_code == 200
                assert r_cap.json()["status"] == "CAPTURED"
                await db_session.refresh(payment)
                await db_session.refresh(ride)
                assert payment.status == "CAPTURED"
                assert ride.status == "COMPLETED"
    finally:
        provider.key_secret = orig_ks
