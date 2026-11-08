import json
import uuid
import hmac
import hashlib
from datetime import datetime, timezone, timedelta
import pytest
from httpx import AsyncClient, ASGITransport
from sqlalchemy import select
from fastapi import HTTPException

from backend.main import app
from backend.core.config import settings
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, Ride, Payment,
    PaymentWebhookInbox, Wallet, WalletTransaction, VehicleCategory, FareRule, Session
)
from backend.application.services.payment_service import PaymentService
from backend.application.services.wallet_service import WalletService
from backend.integrations.payments.provider import get_payment_provider
from backend.infrastructure.security.crypto import create_jwt_token

async def _get_or_create_category_and_rule(db_session):
    cat = (await db_session.execute(select(VehicleCategory).limit(1))).scalar_one_or_none()
    if not cat:
        cat = VehicleCategory(
            code="PRIME_SEDAN",
            name="Prime Sedan",
            description="Luxury sedan chauffeur vehicle",
            capacity=4,
            icon_url="https://example.com/icon.png",
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
            is_active=True,
            effective_from=datetime.now(timezone.utc)
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
        "final_fare_cents": 18000
    }
    data.update(kwargs)
    return Ride(**data)

@pytest.mark.asyncio
async def test_razorpay_signature_verification_authentic_and_tampered():
    """Tasks 031: Verify HMAC-SHA256 signature verification."""
    provider = get_payment_provider()
    orig_secret = provider.key_secret
    provider.key_secret = "rzp_secret_test_2026"

    try:
        order_id = f"order_{uuid.uuid4().hex[:10]}"
        payment_id = f"pay_{uuid.uuid4().hex[:10]}"
        secret = provider.key_secret

        # Compute valid signature
        msg = f"{order_id}|{payment_id}".encode("utf-8")
        valid_sig = hmac.new(secret.encode("utf-8"), msg, hashlib.sha256).hexdigest()

        assert provider.verify_payment_signature(order_id, payment_id, valid_sig) is True
        assert provider.verify_payment_signature(order_id, payment_id, "forged_signature_123") is False
        assert provider.verify_payment_signature(order_id, "different_pay_id", valid_sig) is False
    finally:
        provider.key_secret = orig_secret

@pytest.mark.asyncio
async def test_server_side_fare_and_ownership_enforcement(db_session):
    """Tasks 026, 027, 028, 029: Fare enforcement and ownership check."""
    cat, rule = await _get_or_create_category_and_rule(db_session)
    provider = get_payment_provider()
    orig_secret = provider.key_secret
    provider.key_secret = "rzp_secret_test_2026"

    try:
        uid1 = uuid.uuid4().hex[:8]
        user1 = User(phone_number=f"+9188{uid1}", role="customer", full_name="User 1")
        uid2 = uuid.uuid4().hex[:8]
        user2 = User(phone_number=f"+9189{uid2}", role="customer", full_name="User 2")
        db_session.add_all([user1, user2])
        await db_session.flush()

        prof1 = CustomerProfile(user_id=user1.id, preferred_payment_method="RAZORPAY")
        prof2 = CustomerProfile(user_id=user2.id, preferred_payment_method="RAZORPAY")
        db_session.add_all([prof1, prof2])
        await db_session.flush()

        ride = _make_test_ride(
            customer_id=prof1.id,
            category_id=cat.id,
            rule_id=rule.id,
            estimated_fare_cents=15000,
            final_fare_cents=18000
        )
        db_session.add(ride)
        await db_session.commit()

        sess1 = Session(
            user_id=user1.id,
            token_hash=f"hash_{uuid.uuid4().hex}",
            expires_at=datetime.now(timezone.utc) + timedelta(days=1),
            is_revoked=False
        )
        sess2 = Session(
            user_id=user2.id,
            token_hash=f"hash_{uuid.uuid4().hex}",
            expires_at=datetime.now(timezone.utc) + timedelta(days=1),
            is_revoked=False
        )
        db_session.add_all([sess1, sess2])
        await db_session.commit()

        token1 = create_jwt_token(user_id=user1.id, role="customer", session_id=sess1.id)
        token2 = create_jwt_token(user_id=user2.id, role="customer", session_id=sess2.id)

        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as ac:
            # User 2 tries to create order for User 1's ride -> 403 Forbidden
            bad_order_res = await ac.post(
                "/api/v1/payments/create-order",
                json={"ride_id": ride.id, "amount_cents": 500},
                headers={"Authorization": f"Bearer {token2}"}
            )
            assert bad_order_res.status_code == 403

            # User 1 creates order for their ride -> Fare must be 18000 cents (not client supplied 500)
            good_order_res = await ac.post(
                "/api/v1/payments/create-order",
                json={"ride_id": ride.id, "amount_cents": 500},
                headers={"Authorization": f"Bearer {token1}"}
            )
            assert good_order_res.status_code == 200
            order_data = good_order_res.json()
            assert order_data["amount"] == 18000

            # Verify Payment record in DB has non-null customer_id and idempotency_key
            p_res = await db_session.execute(select(Payment).where(Payment.ride_id == ride.id))
            payment = p_res.scalar_one_or_none()
            assert payment is not None
            assert payment.customer_id == prof1.id
            assert payment.idempotency_key is not None
            assert payment.amount_cents == 18000
    finally:
        provider.key_secret = orig_secret

@pytest.mark.asyncio
async def test_webhook_idempotency_and_authorization_separation(db_session):
    """Tasks 032, 033, 034, 035: Inbound webhook verification, inbox persistence, idempotency, auth vs capture."""
    cat, rule = await _get_or_create_category_and_rule(db_session)
    provider = get_payment_provider()
    orig_webhook_sec = provider.webhook_secret
    provider.webhook_secret = "whsec_test_2026"
    secret = provider.webhook_secret
    payment_service = PaymentService(db_session)

    try:
        uid = uuid.uuid4().hex[:8]
        user = User(phone_number=f"+9187{uid}", role="customer", full_name="Customer Wh")
        db_session.add(user)
        await db_session.flush()

        prof = CustomerProfile(user_id=user.id, preferred_payment_method="RAZORPAY")
        db_session.add(prof)
        await db_session.flush()

        ride = _make_test_ride(
            customer_id=prof.id,
            category_id=cat.id,
            rule_id=rule.id,
            status="ACCEPTED",
            final_fare_cents=12000
        )
        db_session.add(ride)
        await db_session.flush()

        order_id = f"order_{uuid.uuid4().hex[:10]}"
        payment = Payment(
            ride_id=ride.id,
            customer_id=prof.id,
            amount_cents=12000,
            currency="INR",
            status="CREATED",
            payment_method="RAZORPAY",
            provider_name="RAZORPAY",
            provider_order_id=order_id,
            idempotency_key=f"pay_{order_id}"
        )
        db_session.add(payment)
        await db_session.commit()

        # 1. Test payment.authorized webhook
        pay_id = f"pay_{uuid.uuid4().hex[:10]}"
        auth_payload = {
            "event": "payment.authorized",
            "order_id": order_id,
            "payload": {
                "payment": {
                    "entity": {
                        "id": pay_id,
                        "order_id": order_id,
                        "amount": 12000,
                        "status": "authorized",
                        "notes": {"ride_id": ride.id}
                    }
                }
            }
        }
        raw_auth = json.dumps(auth_payload).encode("utf-8")
        auth_sig = hmac.new(secret.encode("utf-8"), raw_auth, hashlib.sha256).hexdigest()

        auth_result = await payment_service.process_webhook(raw_auth, auth_sig)
        assert auth_result["status"] == "SUCCESS"

        # Verify status is AUTHORIZED, not prematurely CAPTURED (Task 032)
        await db_session.refresh(payment)
        assert payment.status == "AUTHORIZED"

        # Verify inbox entry exists (Task 033)
        auth_digest = hashlib.sha256(raw_auth).hexdigest()
        inbox_entry = (await db_session.execute(select(PaymentWebhookInbox).where(PaymentWebhookInbox.event_digest == auth_digest))).scalar_one_or_none()
        assert inbox_entry is not None
        assert inbox_entry.status == "PROCESSED"
        assert inbox_entry.signature_verified is True

        # 2. Test payment.captured webhook
        cap_payload = {
            "event": "payment.captured",
            "order_id": order_id,
            "payload": {
                "payment": {
                    "entity": {
                        "id": pay_id,
                        "order_id": order_id,
                        "amount": 12000,
                        "status": "captured",
                        "notes": {"ride_id": ride.id}
                    }
                }
            }
        }
        raw_cap = json.dumps(cap_payload).encode("utf-8")
        cap_sig = hmac.new(secret.encode("utf-8"), raw_cap, hashlib.sha256).hexdigest()

        cap_result = await payment_service.process_webhook(raw_cap, cap_sig)
        assert cap_result["status"] == "SUCCESS"

        await db_session.refresh(payment)
        assert payment.status == "CAPTURED"

        # 3. Test Idempotency: Replaying payment.captured returns ALREADY_PROCESSED (Task 034)
        replay_result = await payment_service.process_webhook(raw_cap, cap_sig)
        assert replay_result["status"] == "ALREADY_PROCESSED"
    finally:
        provider.webhook_secret = orig_webhook_sec

@pytest.mark.asyncio
async def test_cash_collection_assigned_driver_authorization(db_session):
    """Task 036: Enforce assigned driver authorization for cash collection."""
    cat, rule = await _get_or_create_category_and_rule(db_session)
    uid_c = uuid.uuid4().hex[:8]
    cust_u = User(phone_number=f"+9186{uid_c}", role="customer", full_name="Cash Cust")
    uid_d1 = uuid.uuid4().hex[:8]
    driver1_u = User(phone_number=f"+9185{uid_d1}", role="driver", full_name="Driver Assigned")
    uid_d2 = uuid.uuid4().hex[:8]
    driver2_u = User(phone_number=f"+9184{uid_d2}", role="driver", full_name="Driver Rogue")
    db_session.add_all([cust_u, driver1_u, driver2_u])
    await db_session.flush()

    cust_prof = CustomerProfile(user_id=cust_u.id, preferred_payment_method="CASH")
    driver1_prof = DriverProfile(user_id=driver1_u.id, license_number=f"DL-ASSGN-{uid_d1}")
    driver2_prof = DriverProfile(user_id=driver2_u.id, license_number=f"DL-ROGUE-{uid_d2}")
    db_session.add_all([cust_prof, driver1_prof, driver2_prof])
    await db_session.flush()

    ride = _make_test_ride(
        customer_id=cust_prof.id,
        category_id=cat.id,
        rule_id=rule.id,
        assigned_driver_id=driver1_prof.id,
        status="IN_PROGRESS",
        final_fare_cents=14000
    )
    db_session.add(ride)
    await db_session.commit()

    service = PaymentService(db_session)

    # Rogue driver (driver 2) attempts cash collection -> 403 Forbidden
    with pytest.raises(HTTPException) as exc_info:
        await service.confirm_cash_collection(driver_user_id=driver2_u.id, ride_id=ride.id)
    assert exc_info.value.status_code == 403

    # Assigned driver (driver 1) collects cash -> SUCCESS
    success_res = await service.confirm_cash_collection(driver_user_id=driver1_u.id, ride_id=ride.id)
    assert success_res["cash_collected"] is True
    assert success_res["status"] == "CAPTURED"

    await db_session.refresh(ride)
    assert ride.status == "COMPLETED"

@pytest.mark.asyncio
async def test_wallet_transactions_ledger_locking_and_reconciliation(db_session):
    """Tasks 037, 038, 039, 040: Wallet locking, immutable ledger, balance reconciliation, refunds."""
    uid = uuid.uuid4().hex[:8]
    user = User(phone_number=f"+9183{uid}", role="customer", full_name="Wallet User")
    db_session.add(user)
    await db_session.commit()

    # Credit ₹500
    tx1 = await WalletService.credit_wallet(
        session=db_session,
        user_id=user.id,
        amount_cents=50000,
        source="TOP_UP",
        description="Top-up ₹500"
    )
    assert tx1.balance_after_cents == 50000

    # Debit ₹200
    tx2 = await WalletService.debit_wallet(
        session=db_session,
        user_id=user.id,
        amount_cents=20000,
        source="RIDE_PAYMENT",
        description="Ride payment"
    )
    assert tx2.balance_after_cents == 30000

    # Verify balance
    bal = await WalletService.get_balance(db_session, user.id)
    assert bal["balance_cents"] == 30000

    # Test Task 039: Reconcile wallet balance
    # Simulate an unexplained out-of-band discrepancy in wallet cache
    wallet = await WalletService.get_or_create_wallet(db_session, user.id)
    wallet.balance_cents = 99999  # Corrupted cached value
    await db_session.flush()

    recon = await WalletService.reconcile_wallet_balance(db_session, user.id, auto_correct=True)
    assert recon["cached_balance_cents"] == 99999
    assert recon["ledger_balance_cents"] == 30000  # Authoritative ledger sum: 50000 - 20000 = 30000
    assert recon["discrepancy_cents"] == 69999
    assert wallet.balance_cents == 30000

    # Test Task 040: Process refund of ₹50
    ref_tx = await WalletService.process_wallet_refund(
        session=db_session,
        user_id=user.id,
        amount_cents=5000,
        payment_id="pay_ref_test_123",
        reason="Driver cancellation fee waiver"
    )
    assert ref_tx.amount_cents == 5000
    assert ref_tx.balance_after_cents == 35000

    bal_after_refund = await WalletService.get_balance(db_session, user.id)
    assert bal_after_refund["balance_cents"] == 35000

@pytest.mark.asyncio
async def test_razorpay_order_association_and_verification(db_session):
    """Tasks 029, 030, 031: Verify exact Razorpay order association and signature verification."""
    cat, rule = await _get_or_create_category_and_rule(db_session)
    provider = get_payment_provider()
    orig_secret = provider.key_secret
    provider.key_secret = "rzp_secret_test_2026"

    try:
        uid = uuid.uuid4().hex[:8]
        user = User(phone_number=f"+9182{uid}", role="customer", full_name="Verify User")
        db_session.add(user)
        await db_session.flush()

        prof = CustomerProfile(user_id=user.id, preferred_payment_method="RAZORPAY")
        db_session.add(prof)
        await db_session.flush()

        ride = _make_test_ride(
            customer_id=prof.id,
            category_id=cat.id,
            rule_id=rule.id,
            final_fare_cents=16000
        )
        db_session.add(ride)
        await db_session.flush()

        order_id = f"order_{uuid.uuid4().hex[:10]}"
        pay_id = f"pay_{uuid.uuid4().hex[:10]}"
        payment = Payment(
            ride_id=ride.id,
            customer_id=prof.id,
            amount_cents=16000,
            currency="INR",
            status="CREATED",
            payment_method="RAZORPAY",
            provider_name="RAZORPAY",
            provider_order_id=order_id,
            idempotency_key=f"pay_{order_id}"
        )
        db_session.add(payment)

        sess = Session(
            user_id=user.id,
            token_hash=f"hash_{uuid.uuid4().hex}",
            expires_at=datetime.now(timezone.utc) + timedelta(days=1),
            is_revoked=False
        )
        db_session.add(sess)
        await db_session.commit()

        token = create_jwt_token(user_id=user.id, role="customer", session_id=sess.id)

        # Generate HMAC signature
        msg = f"{order_id}|{pay_id}".encode("utf-8")
        valid_sig = hmac.new(provider.key_secret.encode("utf-8"), msg, hashlib.sha256).hexdigest()

        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as ac:
            # 1. Verification with invalid signature fails with 400
            bad_sig_res = await ac.post(
                "/api/v1/payments/verify",
                json={
                    "razorpay_order_id": order_id,
                    "razorpay_payment_id": pay_id,
                    "razorpay_signature": "invalid_sig",
                    "ride_id": ride.id
                },
                headers={"Authorization": f"Bearer {token}"}
            )
            assert bad_sig_res.status_code == 400

            # 2. Verification with mismatched ride_id fails with 400
            mismatch_res = await ac.post(
                "/api/v1/payments/verify",
                json={
                    "razorpay_order_id": order_id,
                    "razorpay_payment_id": pay_id,
                    "razorpay_signature": valid_sig,
                    "ride_id": "wrong_ride_id"
                },
                headers={"Authorization": f"Bearer {token}"}
            )
            assert mismatch_res.status_code == 400

            # 3. Verification with valid signature and exact order association succeeds
            good_res = await ac.post(
                "/api/v1/payments/verify",
                json={
                    "razorpay_order_id": order_id,
                    "razorpay_payment_id": pay_id,
                    "razorpay_signature": valid_sig,
                    "ride_id": ride.id
                },
                headers={"Authorization": f"Bearer {token}"}
            )
            assert good_res.status_code == 200
            assert good_res.json()["status"] == "CAPTURED"

            await db_session.refresh(payment)
            assert payment.status == "CAPTURED"
            assert payment.provider_payment_id == pay_id
            assert payment.provider_signature == valid_sig

            await db_session.refresh(ride)
            assert ride.status == "COMPLETED"
    finally:
        provider.key_secret = orig_secret

