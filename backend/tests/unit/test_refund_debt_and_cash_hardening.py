"""
Unit and Concurrency Test Suite for Refund Debt Recovery, Cash Ride Accounting,
Payment State Machine, and Authorization Security.
"""
import uuid
import json
import asyncio
import hashlib
import hmac
from datetime import datetime, timezone, timedelta
import pytest
from httpx import AsyncClient, ASGITransport
from sqlalchemy import select

from backend.main import app
from backend.core.config import settings
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, DriverWallet, DriverLedgerEntry,
    Ride, Payment, PaymentRefund, RefundLiability, Wallet, WalletTransaction,
    VehicleCategory, FareRule, Session, AdminRole, AdminUserRole
)
from backend.application.services.wallet_service import WalletService
from backend.application.services.payment_service import PaymentService
from backend.application.services.payment_reconciliation_service import PaymentReconciliationService
from backend.integrations.payments.provider import get_payment_provider
from backend.infrastructure.security.crypto import create_jwt_token, hash_secret


async def _get_or_create_category_and_rule(db_session):
    cat = (await db_session.execute(select(VehicleCategory).limit(1))).scalar_one_or_none()
    if not cat:
        cat = VehicleCategory(
            code="PRIME_SEDAN_HARDEN",
            name="Prime Sedan",
            capacity=4,
            is_active=True,
            sort_order=1
        )
        db_session.add(cat)
        await db_session.flush()

    rule = (await db_session.execute(select(FareRule).where(FareRule.category_id == cat.id))).scalar_one_or_none()
    if not rule:
        rule = FareRule(
            category_id=cat.id,
            version=1,
            base_fare_cents=5000,
            per_km_cents=1500,
            per_minute_cents=200,
            minimum_fare_cents=10000,
            cancellation_fee_cents=5000,
            surge_multiplier=1.0,
            is_active=True
        )
        db_session.add(rule)
        await db_session.flush()
    return cat, rule


def _make_test_ride(customer_id: str, category_id: str, rule_id: str, **kwargs) -> Ride:
    data = {
        "customer_id": customer_id,
        "vehicle_category_id": category_id,
        "fare_rule_id": rule_id,
        "status": "IN_PROGRESS",
        "pickup_address": "Connaught Place, New Delhi",
        "pickup_latitude": 28.6328,
        "pickup_longitude": 77.2195,
        "destination_address": "Cyber City, Gurugram",
        "destination_latitude": 28.4950,
        "destination_longitude": 77.0895,
        "start_otp": "123456",
        "estimated_distance_km": 10.0,
        "estimated_duration_minutes": 25.0,
        "estimated_fare_cents": 15000,
        "final_fare_cents": 20000
    }
    data.update(kwargs)
    return Ride(**data)


def _generate_webhook_signature(body: bytes, secret: str) -> str:
    return hmac.new(secret.encode("utf-8"), body, hashlib.sha256).hexdigest()


@pytest.mark.asyncio
async def test_full_refund_after_topup_fully_spent_records_liability_and_zero_balance(db_session):
    """
    Priority 1 Requirement:
    Never discard an authoritative successful refund event because customer has insufficient balance.
    Maintain non-negative balance while tracking explicit platform receivable / debt.
    """
    user_id = str(uuid.uuid4())
    user = User(
        id=user_id,
        phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}",
        email=f"user_{user_id[:8]}@example.com",
        full_name="Spent Commuter",
        role="customer",
        is_active=True
    )
    db_session.add(user)
    await db_session.flush()

    # User tops up ₹500 (50,000 paise)
    topup_tx = await WalletService.credit_wallet(
        session=db_session,
        user_id=user_id,
        amount_cents=50000,
        source="TOP_UP",
        idempotency_key=f"topup_{user_id}"
    )
    payment = Payment(
        user_id=user_id,
        amount_cents=50000,
        currency="INR",
        purpose="WALLET_TOPUP",
        status="CAPTURED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_payment_id=f"pay_topup_{user_id[:12]}",
        provider_order_id=f"order_topup_{user_id[:12]}",
        idempotency_key=f"pay_key_{user_id}"
    )
    db_session.add(payment)
    await db_session.commit()

    # User spends 100% of wallet balance on rides -> balance becomes 0
    await WalletService.debit_wallet(
        session=db_session,
        user_id=user_id,
        amount_cents=50000,
        source="RIDE_PAYMENT",
        idempotency_key=f"spend_{user_id}"
    )
    bal_before = await WalletService.get_balance(db_session, user_id)
    assert bal_before["balance_cents"] == 0

    # Razorpay notifies of full refund of ₹500 via webhook
    secret = "whsec_test_refund_spent_2026"
    settings.RAZORPAY_WEBHOOK_SECRET = secret
    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    provider.webhook_secret = secret

    refund_id = f"rfnd_spent_{uuid.uuid4().hex[:10]}"
    webhook_payload = {
        "event": "refund.processed",
        "payload": {
            "payment": {
                "entity": {
                    "id": payment.provider_payment_id,
                    "order_id": payment.provider_order_id,
                    "amount": 50000,
                    "status": "captured"
                }
            },
            "refund": {
                "entity": {
                    "id": refund_id,
                    "payment_id": payment.provider_payment_id,
                    "amount": 50000,
                    "status": "processed"
                }
            }
        }
    }
    raw_body = json.dumps(webhook_payload).encode("utf-8")
    sig = _generate_webhook_signature(raw_body, secret)

    try:
        service = PaymentService(db_session)
        result = await service.process_webhook(raw_body, sig)
        assert result["status"] == "SUCCESS"

        # Verify payment updated to REFUNDED
        await db_session.refresh(payment)
        assert payment.status == "REFUNDED"
        assert payment.refunded_amount_cents == 50000

        # Verify wallet balance is NOT negative, but debt is tracked
        bal_after = await WalletService.get_balance(db_session, user_id)
        assert bal_after["balance_cents"] == 0
        assert bal_after["debt_cents"] == 50000

        # Verify RefundLiability audit record
        liab_stmt = select(RefundLiability).where(RefundLiability.gateway_refund_id == refund_id)
        liab = (await db_session.execute(liab_stmt)).scalar_one_or_none()
        assert liab is not None
        assert liab.refund_amount_cents == 50000
        assert liab.debited_from_wallet_cents == 0
        assert liab.outstanding_recovery_cents == 50000
        assert liab.status == "ACTIVE"
    finally:
        provider.webhook_secret = orig_wh


@pytest.mark.asyncio
async def test_partial_refund_with_insufficient_wallet_balance_and_subsequent_auto_recovery(db_session):
    """
    Priority 1 Requirement:
    Partial refund where available balance covers part of refund; remainder recorded as debt.
    Subsequent credit automatically settles outstanding liability.
    """
    user_id = str(uuid.uuid4())
    user = User(
        id=user_id,
        phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}",
        email=f"user_{user_id[:8]}@example.com",
        full_name="Partial Commuter",
        role="customer",
        is_active=True
    )
    db_session.add(user)
    await db_session.flush()

    # User tops up ₹500 (50,000 paise)
    await WalletService.credit_wallet(
        session=db_session,
        user_id=user_id,
        amount_cents=50000,
        source="TOP_UP",
        idempotency_key=f"topup_p_{user_id}"
    )
    payment = Payment(
        user_id=user_id,
        amount_cents=50000,
        currency="INR",
        purpose="WALLET_TOPUP",
        status="CAPTURED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_payment_id=f"pay_p_{user_id[:12]}",
        provider_order_id=f"order_p_{user_id[:12]}",
        idempotency_key=f"pay_key_p_{user_id}"
    )
    db_session.add(payment)
    await db_session.commit()

    # User spends ₹400 -> remaining balance is ₹100 (10,000 paise)
    await WalletService.debit_wallet(
        session=db_session,
        user_id=user_id,
        amount_cents=40000,
        source="RIDE_PAYMENT",
        idempotency_key=f"spend_p_{user_id}"
    )
    bal1 = await WalletService.get_balance(db_session, user_id)
    assert bal1["balance_cents"] == 10000

    # Gateway processes partial refund of ₹300 (30,000 paise)
    # Available ₹100 covers 10,000 paise; deficit ₹200 (20,000 paise) becomes outstanding debt
    secret = "whsec_test_partial_2026"
    settings.RAZORPAY_WEBHOOK_SECRET = secret
    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    provider.webhook_secret = secret

    refund_id = f"rfnd_part_{uuid.uuid4().hex[:10]}"
    webhook_payload = {
        "event": "refund.processed",
        "payload": {
            "payment": {
                "entity": {
                    "id": payment.provider_payment_id,
                    "order_id": payment.provider_order_id,
                    "amount": 50000,
                    "status": "captured"
                }
            },
            "refund": {
                "entity": {
                    "id": refund_id,
                    "payment_id": payment.provider_payment_id,
                    "amount": 30000,
                    "status": "processed"
                }
            }
        }
    }
    raw_body = json.dumps(webhook_payload).encode("utf-8")
    sig = _generate_webhook_signature(raw_body, secret)

    try:
        service = PaymentService(db_session)
        result = await service.process_webhook(raw_body, sig)
        assert result["status"] == "SUCCESS"

        await db_session.refresh(payment)
        assert payment.status == "PARTIALLY_REFUNDED"
        assert payment.refunded_amount_cents == 30000

        bal2 = await WalletService.get_balance(db_session, user_id)
        assert bal2["balance_cents"] == 0
        assert bal2["debt_cents"] == 20000

        liab = (await db_session.execute(
            select(RefundLiability).where(RefundLiability.gateway_refund_id == refund_id)
        )).scalar_one_or_none()
        assert liab.debited_from_wallet_cents == 10000
        assert liab.outstanding_recovery_cents == 20000
        assert liab.status == "ACTIVE"

        # Subsequent topup of ₹500 (50,000 paise) automatically settles the ₹200 debt
        await WalletService.credit_wallet(
            session=db_session,
            user_id=user_id,
            amount_cents=50000,
            source="TOP_UP",
            idempotency_key=f"topup_recovery_{user_id}"
        )
        bal3 = await WalletService.get_balance(db_session, user_id)
        assert bal3["debt_cents"] == 0
        assert bal3["balance_cents"] == 30000  # 50,000 - 20,000 recovered

        await db_session.refresh(liab)
        assert liab.outstanding_recovery_cents == 0
        assert liab.status == "RECOVERED"
    finally:
        provider.webhook_secret = orig_wh


@pytest.mark.asyncio
async def test_duplicate_refund_webhook_idempotency(db_session):
    """
    Priority 1 & 3 Requirement:
    Duplicate refund webhook deliveries must return ALREADY_PROCESSED and not double-increment refund amounts.
    """
    user_id = str(uuid.uuid4())
    user = User(
        id=user_id,
        phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}",
        full_name="Dupe Commuter",
        role="customer"
    )
    db_session.add(user)
    await db_session.flush()

    payment = Payment(
        user_id=user_id,
        amount_cents=20000,
        currency="INR",
        purpose="WALLET_TOPUP",
        status="CAPTURED",
        provider_name="RAZORPAY",
        provider_payment_id=f"pay_dupe_{user_id[:10]}",
        provider_order_id=f"order_dupe_{user_id[:10]}",
        idempotency_key=f"dupe_key_{user_id}"
    )
    db_session.add(payment)
    await db_session.commit()

    secret = "whsec_test_dupe_2026"
    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    provider.webhook_secret = secret

    refund_id = f"rfnd_dupe_{uuid.uuid4().hex[:10]}"
    webhook_payload = {
        "event": "refund.processed",
        "payload": {
            "payment": {
                "entity": {
                    "id": payment.provider_payment_id,
                    "order_id": payment.provider_order_id,
                    "amount": 20000,
                    "status": "captured"
                }
            },
            "refund": {
                "entity": {
                    "id": refund_id,
                    "payment_id": payment.provider_payment_id,
                    "amount": 10000,
                    "status": "processed"
                }
            }
        }
    }
    raw_body = json.dumps(webhook_payload).encode("utf-8")
    sig = _generate_webhook_signature(raw_body, secret)

    try:
        service = PaymentService(db_session)
        # First processing -> SUCCESS
        res1 = await service.process_webhook(raw_body, sig)
        assert res1["status"] == "SUCCESS"

        await db_session.refresh(payment)
        assert payment.refunded_amount_cents == 10000

        # Duplicate processing -> ALREADY_PROCESSED
        res2 = await service.process_webhook(raw_body, sig)
        assert res2["status"] == "ALREADY_PROCESSED"

        # Amount must NOT double to 20,000!
        await db_session.refresh(payment)
        assert payment.refunded_amount_cents == 10000
    finally:
        provider.webhook_secret = orig_wh


@pytest.mark.asyncio
async def test_multiple_partial_refunds_cannot_exceed_captured_amount(db_session):
    """
    Priority 1 Requirement:
    Multiple partial refunds are supported, but cumulative refunds cannot exceed payment.amount_cents.
    """
    user_id = str(uuid.uuid4())
    user = User(
        id=user_id,
        phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}",
        full_name="Multi Refund User",
        role="customer"
    )
    db_session.add(user)
    await db_session.flush()

    payment = Payment(
        user_id=user_id,
        amount_cents=10000,  # ₹100
        currency="INR",
        purpose="WALLET_TOPUP",
        status="CAPTURED",
        provider_name="RAZORPAY",
        provider_payment_id=f"pay_multi_{user_id[:10]}",
        provider_order_id=f"order_multi_{user_id[:10]}",
        idempotency_key=f"multi_key_{user_id}"
    )
    db_session.add(payment)
    await db_session.commit()

    secret = "whsec_test_multi_2026"
    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    provider.webhook_secret = secret

    try:
        service = PaymentService(db_session)

        # 1. First partial refund: ₹60 (6,000 paise)
        p1 = {
            "event": "refund.processed",
            "payload": {
                "payment": {"entity": {"id": payment.provider_payment_id, "amount": 10000}},
                "refund": {"entity": {"id": f"rfnd_m1_{uuid.uuid4().hex[:6]}", "amount": 6000}}
            }
        }
        b1 = json.dumps(p1).encode("utf-8")
        await service.process_webhook(b1, _generate_webhook_signature(b1, secret))
        await db_session.refresh(payment)
        assert payment.status == "PARTIALLY_REFUNDED"
        assert payment.refunded_amount_cents == 6000

        # 2. Second partial refund: ₹40 (4,000 paise) -> Exactly completes the ₹100
        p2 = {
            "event": "refund.processed",
            "payload": {
                "payment": {"entity": {"id": payment.provider_payment_id, "amount": 10000}},
                "refund": {"entity": {"id": f"rfnd_m2_{uuid.uuid4().hex[:6]}", "amount": 4000}}
            }
        }
        b2 = json.dumps(p2).encode("utf-8")
        await service.process_webhook(b2, _generate_webhook_signature(b2, secret))
        await db_session.refresh(payment)
        assert payment.status == "REFUNDED"
        assert payment.refunded_amount_cents == 10000

        # 3. Third partial refund: ₹20 (2,000 paise) -> Exceeds captured; clamped to 0 excess
        p3 = {
            "event": "refund.processed",
            "payload": {
                "payment": {"entity": {"id": payment.provider_payment_id, "amount": 10000}},
                "refund": {"entity": {"id": f"rfnd_m3_{uuid.uuid4().hex[:6]}", "amount": 2000}}
            }
        }
        b3 = json.dumps(p3).encode("utf-8")
        await service.process_webhook(b3, _generate_webhook_signature(b3, secret))
        await db_session.refresh(payment)
        assert payment.status == "REFUNDED"
        assert payment.refunded_amount_cents == 10000  # Stays at 10,000, cannot exceed
    finally:
        provider.webhook_secret = orig_wh


@pytest.mark.asyncio
async def test_cash_ride_concurrency_and_commission_deduction(db_session):
    """
    Priority 2 Requirement:
    Cash confirmation distinguishes physical cash collected from net earnings & commission receivable.
    Duplicate concurrent confirmation calls are rejected.
    """
    cat, rule = await _get_or_create_category_and_rule(db_session)
    uid_c = uuid.uuid4().hex[:8]
    cust_u = User(phone_number=f"+9186{uid_c}", role="customer", full_name="Cash Cust")
    uid_d1 = uuid.uuid4().hex[:8]
    driver1_u = User(phone_number=f"+9185{uid_d1}", role="driver", full_name="Driver Assigned")
    db_session.add_all([cust_u, driver1_u])
    await db_session.flush()

    cust_prof = CustomerProfile(user_id=cust_u.id, preferred_payment_method="CASH")
    driver1_prof = DriverProfile(user_id=driver1_u.id, license_number=f"DL-ASSGN-{uid_d1}")
    db_session.add_all([cust_prof, driver1_prof])
    await db_session.flush()

    ride = _make_test_ride(
        customer_id=cust_prof.id,
        category_id=cat.id,
        rule_id=rule.id,
        assigned_driver_id=driver1_prof.id,
        status="IN_PROGRESS",
        final_fare_cents=20000
    )
    db_session.add(ride)
    await db_session.commit()

    service = PaymentService(db_session)
    # First confirmation succeeds
    res = await service.confirm_cash_collection(driver_user_id=driver1_u.id, ride_id=ride.id)
    assert res["status"] == "CAPTURED"
    assert res["payment_method"] == "CASH"
    assert res["provider_name"] == "CASH"
    assert res["cash_collected_cents"] == 20000
    assert res["commission_receivable_cents"] == 3000  # 15% of ₹200
    assert res["driver_net_earnings_cents"] == 17000   # 85% of ₹200
    assert res["driver_wallet_balance_cents"] == -3000

    # Second confirmation attempt raises 400 Bad Request
    with pytest.raises(Exception) as exc:
        await service.confirm_cash_collection(driver_user_id=driver1_u.id, ride_id=ride.id)
    assert "already been confirmed" in str(exc.value)


@pytest.mark.asyncio
async def test_pending_driver_cannot_go_online_or_operate():
    """
    Priority 7 Requirement:
    Ensure onboarding exceptions do NOT permit pending drivers to become online / available.
    """
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # Signup a new pending driver
        phone = f"+9198{uuid.uuid4().int % 100000000:08d}"
        signup_res = await client.post("/api/v1/auth/signup/driver", json={
            "phone_number": phone,
            "full_name": "Pending Driver Security Test",
            "password": "SecurePassword123!"
        })
        assert signup_res.status_code == 200
        token = signup_res.json()["access_token"]
        headers = {"Authorization": f"Bearer {token}"}

        # 1. GET /availability is allowed for viewing read-only onboarding status
        get_res = await client.get("/api/v1/drivers/availability", headers=headers)
        assert get_res.status_code == 200

        # 2. POST /availability with is_online: true MUST BE REJECTED with 403 Forbidden!
        post_res = await client.post("/api/v1/drivers/availability", json={"is_online": True}, headers=headers)
        assert post_res.status_code == 403
        assert "must be approved" in post_res.text.lower() or "not operational" in post_res.text.lower()


@pytest.mark.asyncio
async def test_reconciliation_detects_outstanding_refund_liabilities(db_session):
    """
    Priority 1 & 5 Requirement:
    Reconciliation worker detects active refund liabilities and reports unrecovered platform debt.
    """
    recon = await PaymentReconciliationService.reconcile_refund_liabilities()
    assert "active_count" in recon
    assert "total_unrecovered_cents" in recon
    assert "total_unrecovered_rupees" in recon


@pytest.mark.asyncio
async def test_wallet_debt_invariant_across_multiple_liabilities_and_partial_topups(db_session):
    """
    Section 1 Acceptance Requirement:
    Verify the strict invariant:
    wallet.debt_cents == SUM(active refund liabilities outstanding_recovery_cents)
    holds true after every single clawback and partial/full debt settlement.
    """
    user_id = str(uuid.uuid4())
    wallet = await WalletService.get_or_create_wallet(db_session, user_id)
    assert wallet.balance_cents == 0
    assert wallet.debt_cents == 0

    async def verify_invariant():
        await db_session.refresh(wallet)
        active_liabs = (await db_session.execute(
            select(RefundLiability).where(
                RefundLiability.user_id == user_id,
                RefundLiability.status == "ACTIVE"
            )
        )).scalars().all()
        expected_sum = sum(l.outstanding_recovery_cents for l in active_liabs)
        assert wallet.debt_cents == expected_sum, f"Invariant violated! wallet.debt={wallet.debt_cents} != sum={expected_sum}"

    # 1. Accrue first refund liability of ₹100 (10,000 paise)
    res1 = await WalletService.process_refund_clawback(
        session=db_session,
        user_id=user_id,
        refund_amount_cents=10000,
        payment_id=f"pay_{uuid.uuid4().hex[:8]}",
        gateway_refund_id=f"rfnd_inv_1_{uuid.uuid4().hex[:8]}",
        idempotency_key=f"idem_inv_1_{uuid.uuid4().hex[:8]}"
    )
    assert res1["status"] == "SUCCESS"
    await verify_invariant()
    assert wallet.debt_cents == 10000

    # 2. Accrue second refund liability of ₹150 (15,000 paise)
    res2 = await WalletService.process_refund_clawback(
        session=db_session,
        user_id=user_id,
        refund_amount_cents=15000,
        payment_id=f"pay_{uuid.uuid4().hex[:8]}",
        gateway_refund_id=f"rfnd_inv_2_{uuid.uuid4().hex[:8]}",
        idempotency_key=f"idem_inv_2_{uuid.uuid4().hex[:8]}"
    )
    assert res2["status"] == "SUCCESS"
    await verify_invariant()
    assert wallet.debt_cents == 25000

    # 3. Accrue third refund liability of ₹200 (20,000 paise)
    res3 = await WalletService.process_refund_clawback(
        session=db_session,
        user_id=user_id,
        refund_amount_cents=20000,
        payment_id=f"pay_{uuid.uuid4().hex[:8]}",
        gateway_refund_id=f"rfnd_inv_3_{uuid.uuid4().hex[:8]}",
        idempotency_key=f"idem_inv_3_{uuid.uuid4().hex[:8]}"
    )
    assert res3["status"] == "SUCCESS"
    await verify_invariant()
    assert wallet.debt_cents == 45000

    # 4. Partial top-up of ₹120 (12,000 paise):
    # Should completely recover liab 1 (10,000) and partially recover liab 2 (2,000), leaving 33,000 debt
    tx1 = await WalletService.credit_wallet(
        session=db_session,
        user_id=user_id,
        amount_cents=12000,
        source="TOP_UP",
        idempotency_key=f"topup_inv_1_{uuid.uuid4().hex[:8]}"
    )
    await verify_invariant()
    assert wallet.debt_cents == 33000
    assert wallet.balance_cents == 0

    # 5. Second partial top-up of ₹200 (20,000 paise):
    # Recovers remaining of liab 2 (13,000) and partial of liab 3 (7,000), leaving 13,000 debt
    tx2 = await WalletService.credit_wallet(
        session=db_session,
        user_id=user_id,
        amount_cents=20000,
        source="TOP_UP",
        idempotency_key=f"topup_inv_2_{uuid.uuid4().hex[:8]}"
    )
    await verify_invariant()
    assert wallet.debt_cents == 13000
    assert wallet.balance_cents == 0

    # 6. Final top-up of ₹500 (50,000 paise):
    # Recovers remaining 13,000 debt, leaving 37,000 in spendable balance!
    tx3 = await WalletService.credit_wallet(
        session=db_session,
        user_id=user_id,
        amount_cents=50000,
        source="TOP_UP",
        idempotency_key=f"topup_inv_3_{uuid.uuid4().hex[:8]}"
    )
    await verify_invariant()
    assert wallet.debt_cents == 0
    assert wallet.balance_cents == 37000


@pytest.mark.asyncio
async def test_refund_lifecycle_created_failed_and_authoritative_processed(db_session):
    """
    Section 3 Requirement:
    - refund.created (pending) does not prematurely claw back funds.
    - refund.failed records failure without financial mutation.
    - later authoritative refund.processed finalizes the refund and executes clawback.
    """
    user_id = str(uuid.uuid4())
    provider = get_payment_provider()
    secret = "whsec_test_state_machine_998877"
    orig_wh = provider.webhook_secret
    provider.webhook_secret = secret

    # Topup wallet with ₹500
    await WalletService.credit_wallet(
        session=db_session,
        user_id=user_id,
        amount_cents=50000,
        source="TOP_UP",
        idempotency_key=f"topup_init_{user_id}"
    )
    bal1 = await WalletService.get_balance(db_session, user_id)
    assert bal1["balance_cents"] == 50000

    order_id = f"order_rfnd_sm_{uuid.uuid4().hex[:8]}"
    pay_id = f"pay_rfnd_sm_{uuid.uuid4().hex[:8]}"
    payment = Payment(
        user_id=user_id,
        customer_id=user_id,
        amount_cents=50000,
        status="CAPTURED",
        purpose="WALLET_TOPUP",
        provider_name="RAZORPAY",
        provider_order_id=order_id,
        provider_payment_id=pay_id,
        idempotency_key=f"idem_pay_{uuid.uuid4().hex[:8]}"
    )
    db_session.add(payment)
    await db_session.commit()

    refund_id = f"rfnd_seq_{uuid.uuid4().hex[:8]}"

    try:
        service = PaymentService(db_session)

        # 1. Dispatch refund.created (status: pending)
        body_created = json.dumps({
            "event": "refund.created",
            "order_id": order_id,
            "payload": {
                "payment": {"entity": {"id": pay_id, "amount": 50000}},
                "refund": {"entity": {"id": refund_id, "amount": 50000, "status": "pending"}}
            }
        }).encode("utf-8")
        sig_created = _generate_webhook_signature(body_created, secret)
        res_created = await service.process_webhook(body_created, sig_created)
        assert res_created["status"] == "REFUND_PENDING"

        # Balance remains 50,000! No premature clawback!
        bal2 = await WalletService.get_balance(db_session, user_id)
        assert bal2["balance_cents"] == 50000
        assert bal2["debt_cents"] == 0

        # 2. Dispatch refund.failed
        body_failed = json.dumps({
            "event": "refund.failed",
            "order_id": order_id,
            "payload": {
                "payment": {"entity": {"id": pay_id, "amount": 50000}},
                "refund": {"entity": {"id": refund_id, "amount": 50000, "status": "failed"}}
            }
        }).encode("utf-8")
        sig_failed = _generate_webhook_signature(body_failed, secret)
        res_failed = await service.process_webhook(body_failed, sig_failed)
        assert res_failed["status"] == "REFUND_FAILED"

        # Balance still remains 50,000!
        bal3 = await WalletService.get_balance(db_session, user_id)
        assert bal3["balance_cents"] == 50000
        assert bal3["debt_cents"] == 0

        # 3. Later authoritative refund.processed arrives
        body_proc = json.dumps({
            "event": "refund.processed",
            "order_id": order_id,
            "payload": {
                "payment": {"entity": {"id": pay_id, "amount": 50000}},
                "refund": {"entity": {"id": refund_id, "amount": 50000, "status": "processed"}}
            }
        }).encode("utf-8")
        sig_proc = _generate_webhook_signature(body_proc, secret)
        res_proc = await service.process_webhook(body_proc, sig_proc)
        assert res_proc["status"] == "SUCCESS"

        # Authoritative clawback now executed! Balance becomes 0
        bal4 = await WalletService.get_balance(db_session, user_id)
        assert bal4["balance_cents"] == 0
        assert bal4["debt_cents"] == 0

        # Check Payment model status
        await db_session.refresh(payment)
        assert payment.status == "REFUNDED"
        assert payment.refunded_amount_cents == 50000
    finally:
        provider.webhook_secret = orig_wh


@pytest.mark.asyncio
async def test_admin_direct_api_rbac_and_compensation_authorization(db_session):
    """
    Section 5 Requirement:
    Direct API testing of Admin endpoints:
    1. Unauthenticated request -> 401 Unauthorized
    2. Customer token -> 403 Forbidden
    3. Admin token with MFA session -> 200 OK
    """
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. Unauthenticated request
        res_unauth = await client.post("/api/v1/wallets/admin/compensate", json={
            "user_id": str(uuid.uuid4()),
            "amount_cents": 5000,
            "reason": "Test unauth"
        })
        assert res_unauth.status_code == 401

        # 2. Customer token request -> 403 Forbidden
        cust_u = User(
            phone_number=f"+9197{uuid.uuid4().hex[:8]}",
            email=f"cust_test_{uuid.uuid4().hex[:6]}@vaisentra.com",
            role="customer",
            full_name="Regular Customer",
            is_active=True
        )
        db_session.add(cust_u)
        await db_session.flush()

        cust_session = Session(
            id=str(uuid.uuid4()),
            user_id=cust_u.id,
            token_hash=hash_secret("token_cust_test"),
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(hours=1)
        )
        db_session.add(cust_session)
        await db_session.commit()

        cust_token = create_jwt_token(
            user_id=cust_u.id,
            role="customer",
            session_id=cust_session.id
        )
        res_cust = await client.post(
            "/api/v1/wallets/admin/compensate",
            headers={"Authorization": f"Bearer {cust_token}"},
            json={
                "user_id": str(uuid.uuid4()),
                "amount_cents": 5000,
                "reason": "Test cust"
            }
        )
        assert res_cust.status_code == 403

        # 3. Create real Admin user with MFA session & Super Administrator role
        admin_u = User(
            phone_number=f"+9198{uuid.uuid4().hex[:8]}",
            email=f"admin_test_{uuid.uuid4().hex[:6]}@vaisentra.com",
            role="admin",
            full_name="Verified Admin Auditor",
            is_active=True,
            mfa_enabled=True
        )
        db_session.add(admin_u)
        await db_session.flush()

        admin_session = Session(
            id=str(uuid.uuid4()),
            user_id=admin_u.id,
            token_hash=hash_secret("token_admin_test"),
            is_revoked=False,
            mfa_verified=True,
            expires_at=datetime.now(timezone.utc) + timedelta(hours=1)
        )
        db_session.add(admin_session)

        super_role = AdminRole(
            name=f"Super Admin {uuid.uuid4().hex[:6]}",
            description="Full access",
            is_superuser=True
        )
        db_session.add(super_role)
        await db_session.flush()

        db_session.add(AdminUserRole(user_id=admin_u.id, role_id=super_role.id))
        await db_session.commit()

        admin_token = create_jwt_token(
            user_id=admin_u.id,
            role="admin",
            session_id=admin_session.id
        )

        target_cust_id = str(uuid.uuid4())
        res_admin = await client.post(
            "/api/v1/wallets/admin/compensate",
            headers={"Authorization": f"Bearer {admin_token}"},
            json={
                "user_id": target_cust_id,
                "amount_cents": 25000,  # ₹250.00
                "reason": "Trip cancellation goodwill gesture",
                "idempotency_key": f"comp_test_{uuid.uuid4().hex[:8]}"
            }
        )
        assert res_admin.status_code == 200
        comp_data = res_admin.json()
        assert comp_data["status"] == "COMPENSATED"
        assert comp_data["amount_rupees"] == 250.0


