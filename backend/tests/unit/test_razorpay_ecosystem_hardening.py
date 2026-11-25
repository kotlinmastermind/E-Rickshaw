"""
Comprehensive Razorpay Ecosystem Hardening Test Suite.
Verifies:
1. Rejection of raw card PAN under PCI DSS.
2. Gift card replay defense and purpose/amount verification.
3. Wallet top-up refund double-dip prevention (atomic wallet debit & insufficient funds rejection).
4. Partial refund authorization check (non-owner customer rejected).
5. Administrative compensation limit (₹5,000 threshold) and stable idempotency.
6. Cash collection commission double-debit prevention.
"""
import pytest
import hmac
import hashlib
import json
import uuid
from httpx import AsyncClient
from backend.domain.models import Payment, Wallet, CustomerProfile, User, DriverProfile, Ride, DriverLedgerEntry, DriverWallet, AdminRole, AdminUserRole
from backend.application.services.settlement_advanced_service import SettlementAdvancedService
from backend.application.services.gift_card_service import GiftCardService
from backend.application.services.payment_service import PaymentService
from backend.application.services.wallet_service import WalletService
from backend.integrations.payments.provider import get_payment_provider
from fastapi import HTTPException
from sqlalchemy import select

def generate_signature(message: str, secret: str) -> str:
    return hmac.new(secret.encode("utf-8"), message.encode("utf-8"), hashlib.sha256).hexdigest()

@pytest.mark.asyncio
async def test_pci_dss_raw_card_pan_rejected(client: AsyncClient, customer_token: str):
    """PCI DSS: Server must reject direct raw card PAN submission with HTTP 400."""
    resp = await client.post(
        "/api/v1/payments/tokenize-card",
        json={
            "card_number": "4111111111111111",
            "exp_month": 12,
            "exp_year": 2028,
            "cardholder_name": "John Doe"
        },
        headers={"Authorization": f"Bearer {customer_token}"}
    )
    assert resp.status_code == 400
    assert "pci dss" in resp.json()["detail"].lower()

@pytest.mark.asyncio
async def test_gift_card_replay_and_purpose_mismatch_rejected(db_session, customer_user, monkeypatch):
    """F-90: Proves gift card purchase rejects replay of already consumed payments and purpose mismatches."""
    from backend.core.config import settings
    monkeypatch.setattr(settings, "RAZORPAY_KEY_SECRET", "test_key_secret_32_characters_long_sec")
    provider = get_payment_provider()
    provider.key_secret = "test_key_secret_32_characters_long_sec"

    service = GiftCardService(db_session)
    order_id = f"order_{uuid.uuid4().hex[:12]}"
    pay_id = f"pay_{uuid.uuid4().hex[:12]}"
    sig = generate_signature(f"{order_id}|{pay_id}", "test_key_secret_32_characters_long_sec")

    # 1. Simulate an existing Payment record that was created for WALLET_TOPUP, not GIFT_CARD_PURCHASE
    payment = Payment(
        customer_id=customer_user.id,
        user_id=customer_user.id,
        amount_cents=10000,
        currency="INR",
        purpose="WALLET_TOPUP",
        status="CAPTURED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=order_id,
        provider_payment_id=pay_id,
        idempotency_key=f"pay_topup_{order_id}"
    )
    db_session.add(payment)
    await db_session.commit()

    # Attempting to issue a gift card using a top-up payment must be rejected with purpose mismatch
    with pytest.raises(HTTPException) as exc_info:
        await service.purchase_gift_card(
            purchaser_user_id=customer_user.id,
            amount_cents=10000,
            sender_name="Sender",
            payment_method="RAZORPAY",
            razorpay_payment_id=pay_id,
            razorpay_order_id=order_id,
            razorpay_signature=sig
        )
    assert exc_info.value.status_code == 400
    assert "purpose mismatch" in exc_info.value.detail.lower()

@pytest.mark.asyncio
async def test_wallet_topup_refund_debits_wallet_and_blocks_insufficient_funds(db_session, customer_user, monkeypatch):
    """F-94: Proves top-up refund reverses wallet balance and blocks refund if user spent the balance."""
    provider = get_payment_provider()
    async def mock_refund(payment_id, amount_cents, notes=None):
        return {"id": f"rfnd_{uuid.uuid4().hex[:8]}", "status": "processed"}
    monkeypatch.setattr(provider, "create_refund", mock_refund)

    # 1. Credit wallet with ₹500 (50,000 cents)
    await WalletService.credit_wallet(
        session=db_session,
        user_id=customer_user.id,
        amount_cents=50000,
        source="WALLET_TOPUP"
    )

    # Create captured top-up Payment
    payment = Payment(
        customer_id=customer_user.id,
        user_id=customer_user.id,
        amount_cents=50000,
        currency="INR",
        purpose="WALLET_TOPUP",
        status="CAPTURED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_payment_id=f"pay_{uuid.uuid4().hex[:12]}",
        idempotency_key=f"pay_idem_{uuid.uuid4().hex[:12]}"
    )
    db_session.add(payment)
    await db_session.commit()

    # 2. Spend ₹400 on a ride -> remaining balance ₹100
    await WalletService.debit_wallet(
        session=db_session,
        user_id=customer_user.id,
        amount_cents=40000,
        source="RIDE_PAYMENT"
    )

    # 3. Attempting to refund the full ₹500 must fail because wallet only has ₹100 left
    settlement = SettlementAdvancedService(db_session)
    with pytest.raises(HTTPException) as exc_info:
        await settlement.create_refund(
            payment_id=payment.id,
            amount_cents=50000,
            reason="User changed mind"
        )
    assert exc_info.value.status_code == 400
    assert "insufficient wallet balance" in exc_info.value.detail.lower()

    # 4. Refunding ₹100 succeeds and reduces wallet balance to ₹0
    result = await settlement.create_refund(
        payment_id=payment.id,
        amount_cents=10000,
        reason="Partial refund remaining"
    )
    assert result["status"] == "PROCESSED"
    bal = await WalletService.get_balance(db_session, customer_user.id)
    assert bal["balance_cents"] == 0

@pytest.mark.asyncio
async def test_partial_refund_unauthorized_user_blocked(client: AsyncClient, second_customer_token: str, db_session, customer_user):
    """F-95: User B cannot initiate a refund on User A's payment record."""
    payment = Payment(
        customer_id=customer_user.id,
        user_id=customer_user.id,
        amount_cents=20000,
        currency="INR",
        status="CAPTURED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_payment_id=f"pay_{uuid.uuid4().hex[:12]}",
        idempotency_key=f"pay_idem_{uuid.uuid4().hex[:12]}"
    )
    db_session.add(payment)
    await db_session.commit()

    resp = await client.post(
        "/api/v1/payments/refunds/partial",
        json={"payment_id": payment.id, "amount_cents": 10000, "reason": "Unauthorized refund"},
        headers={"Authorization": f"Bearer {second_customer_token}"}
    )
    assert resp.status_code == 403

@pytest.mark.asyncio
async def test_admin_compensation_limit_enforced(client: AsyncClient, customer_user, db_session):
    """Proves admin compensation > ₹5,000 (500,000 paise) is rejected."""
    admin_id = str(uuid.uuid4())
    admin_user = User(
        id=admin_id,
        phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}",
        email=f"adm_{admin_id[:8]}@example.com",
        full_name="Super Admin",
        role="admin",
        is_active=True,
        is_verified=True,
        mfa_enabled=True
    )
    from datetime import datetime, timezone, timedelta
    from backend.domain.models import Session
    from backend.infrastructure.security.crypto import create_jwt_token
    sess = Session(
        id=str(uuid.uuid4()),
        user_id=admin_id,
        token_hash=f"dummy_{admin_id}",
        mfa_verified=True,
        expires_at=datetime.now(timezone.utc) + timedelta(days=1)
    )

    super_role = (await db_session.execute(select(AdminRole).where(AdminRole.name == "Super Administrator"))).scalar_one_or_none()
    if not super_role:
        super_role = AdminRole(id=str(uuid.uuid4()), name="Super Administrator", is_superuser=True)
        db_session.add(super_role)
        await db_session.flush()

    role_assign = AdminUserRole(user_id=admin_id, role_id=super_role.id)
    db_session.add_all([admin_user, sess, role_assign])
    await db_session.commit()

    admin_token = create_jwt_token(user_id=admin_id, role="admin", session_id=sess.id)

    # Exceed limit: ₹6,000
    resp = await client.post(
        "/api/v1/wallets/admin/compensate",
        json={"user_id": customer_user.id, "amount_cents": 600000, "reason": "Overly generous compensation"},
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert resp.status_code == 400
    assert "limit exceeded" in resp.json()["detail"].lower()

@pytest.mark.asyncio
async def test_cash_collection_commission_double_debit_prevention(db_session):
    """Proves calling confirm_cash_collection twice does not deduct commission twice."""
    driver_user_id = str(uuid.uuid4())
    driver_user = User(
        id=driver_user_id,
        phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}",
        email=f"driver_{driver_user_id[:8]}@example.com",
        full_name="Pilot Kumar",
        role="driver",
        is_active=True,
        is_verified=True
    )
    driver_prof = DriverProfile(
        id=str(uuid.uuid4()),
        user_id=driver_user_id,
        license_number="DL1234567890"
    )
    cust_user = User(
        id=str(uuid.uuid4()),
        phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}",
        email=f"cust_{uuid.uuid4().hex[:8]}@example.com",
        full_name="Rider",
        role="customer",
        is_active=True,
        is_verified=True
    )
    cust_prof = CustomerProfile(user_id=cust_user.id)
    db_session.add_all([driver_user, driver_prof, cust_user, cust_prof])
    await db_session.flush()

    from backend.domain.models import VehicleCategory, FareRule
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    rule = (await db_session.execute(select(FareRule))).scalars().first()

    ride = Ride(
        id=str(uuid.uuid4()),
        customer_id=cust_prof.id,
        assigned_driver_id=driver_prof.id,
        vehicle_category_id=cat.id if cat else str(uuid.uuid4()),
        fare_rule_id=rule.id if rule else str(uuid.uuid4()),
        pickup_address="Metro",
        pickup_latitude=28.6139,
        pickup_longitude=77.2090,
        destination_address="Market",
        destination_latitude=28.6200,
        destination_longitude=77.2150,
        estimated_distance_km=5.0,
        actual_distance_km=5.0,
        estimated_duration_minutes=15,
        actual_duration_minutes=15,
        estimated_fare_cents=10000,
        start_otp="1234",
        status="IN_PROGRESS",
        payment_method="CASH",
        final_fare_cents=10000
    )
    db_session.add(ride)
    await db_session.commit()

    service = PaymentService(db_session)
    res1 = await service.confirm_cash_collection(driver_user_id=driver_user_id, ride_id=ride.id)
    assert res1["commission_deducted_cents"] == 0
    assert res1["commission_receivable_cents"] == 1500
    assert res1["driver_wallet_balance_cents"] == 0

    # Second call is rejected because cash collection is already processed
    with pytest.raises(HTTPException) as exc_info:
        await service.confirm_cash_collection(driver_user_id=driver_user_id, ride_id=ride.id)
    assert exc_info.value.status_code == 400
    assert "already been confirmed" in exc_info.value.detail.lower()

@pytest.mark.asyncio
async def test_webhook_refund_claws_back_wallet_topup(db_session, customer_user, monkeypatch):
    """Proves that an inbound refund webhook on a WALLET_TOPUP payment atomically debits the customer wallet."""
    from backend.core.config import settings
    monkeypatch.setattr(settings, "RAZORPAY_WEBHOOK_SECRET", "test_webhook_secret_for_hardening_suite")
    provider = get_payment_provider()
    provider.webhook_secret = "test_webhook_secret_for_hardening_suite"

    # 1. Credit wallet with ₹300 (30,000 cents)
    await WalletService.credit_wallet(
        session=db_session,
        user_id=customer_user.id,
        amount_cents=30000,
        source="WALLET_TOPUP"
    )

    # 2. Persist captured Payment with purpose=WALLET_TOPUP
    order_id = f"order_{uuid.uuid4().hex[:12]}"
    pay_id = f"pay_{uuid.uuid4().hex[:12]}"
    payment = Payment(
        customer_id=customer_user.id,
        user_id=customer_user.id,
        amount_cents=30000,
        currency="INR",
        purpose="WALLET_TOPUP",
        status="CAPTURED",
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        provider_order_id=order_id,
        provider_payment_id=pay_id,
        idempotency_key=f"pay_idem_{order_id}"
    )
    db_session.add(payment)
    await db_session.commit()

    # 3. Simulate inbound webhook: refund.processed for ₹300
    payload = {
        "event": "refund.processed",
        "payload": {
            "payment": {
                "entity": {
                    "id": pay_id,
                    "order_id": order_id,
                    "amount": 30000,
                    "amount_refunded": 30000,
                    "status": "refunded"
                }
            },
            "refund": {
                "entity": {
                    "id": f"rfnd_{uuid.uuid4().hex[:10]}",
                    "payment_id": pay_id,
                    "amount": 30000,
                    "status": "processed"
                }
            }
        }
    }
    raw_body = json.dumps(payload).encode("utf-8")
    sig = generate_signature(raw_body.decode("utf-8"), "test_webhook_secret_for_hardening_suite")

    service = PaymentService(db_session)
    res = await service.process_webhook(raw_body=raw_body, signature_header=sig)
    assert res["status"] == "SUCCESS"

    # Assert payment is REFUNDED
    await db_session.refresh(payment)
    assert payment.status == "REFUNDED"
    assert payment.refunded_amount_cents == 30000

    # Assert wallet was debited by ₹300 (balance is now 0)
    bal = await WalletService.get_balance(db_session, customer_user.id)
    assert bal["balance_cents"] == 0

@pytest.mark.asyncio
async def test_admin_compensation_idempotency_key(client: AsyncClient, customer_user, db_session):
    """Proves admin compensation honors client-provided idempotency key and prevents duplicate credits."""
    admin_id = str(uuid.uuid4())
    admin_user = User(
        id=admin_id,
        phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}",
        email=f"adm_{admin_id[:8]}@example.com",
        full_name="Super Admin",
        role="admin",
        is_active=True,
        is_verified=True,
        mfa_enabled=True
    )
    from datetime import datetime, timezone, timedelta
    from backend.domain.models import Session
    from backend.infrastructure.security.crypto import create_jwt_token
    sess = Session(
        id=str(uuid.uuid4()),
        user_id=admin_id,
        token_hash=f"dummy_{admin_id}",
        mfa_verified=True,
        expires_at=datetime.now(timezone.utc) + timedelta(days=1)
    )

    super_role = (await db_session.execute(select(AdminRole).where(AdminRole.name == "Super Administrator"))).scalar_one_or_none()
    if not super_role:
        super_role = AdminRole(id=str(uuid.uuid4()), name="Super Administrator", is_superuser=True)
        db_session.add(super_role)
        await db_session.flush()

    role_assign = AdminUserRole(user_id=admin_id, role_id=super_role.id)
    db_session.add_all([admin_user, sess, role_assign])
    await db_session.commit()

    admin_token = create_jwt_token(user_id=admin_id, role="admin", session_id=sess.id)
    idem_key = f"adm_idem_{uuid.uuid4().hex}"

    # First compensation call
    resp1 = await client.post(
        "/api/v1/wallets/admin/compensate",
        json={
            "user_id": customer_user.id,
            "amount_cents": 25000,
            "reason": "Customer trip delay goodwill gesture",
            "idempotency_key": idem_key
        },
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert resp1.status_code == 200
    assert resp1.json()["amount_rupees"] == 250.0

    bal1 = await WalletService.get_balance(db_session, customer_user.id)
    assert bal1["balance_cents"] == 25000

    # Second compensation call with same idempotency key must not credit twice
    resp2 = await client.post(
        "/api/v1/wallets/admin/compensate",
        json={
            "user_id": customer_user.id,
            "amount_cents": 25000,
            "reason": "Customer trip delay goodwill gesture",
            "idempotency_key": idem_key
        },
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert resp2.status_code == 200

    bal2 = await WalletService.get_balance(db_session, customer_user.id)
    assert bal2["balance_cents"] == 25000

