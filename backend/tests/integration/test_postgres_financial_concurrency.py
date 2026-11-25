"""
PostgreSQL Concurrent Integration Tests for Financial Correctness & Row Locking.

Requires POSTGRES_TEST_DATABASE_URL pointing to a dedicated non-production PostgreSQL test database.
If POSTGRES_TEST_DATABASE_URL is not configured, tests skip cleanly without touching production DB.
"""
import asyncio
import hashlib
import hmac
import json
import os
import uuid
from unittest.mock import AsyncMock, patch

import pytest
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from backend.application.services.payment_service import PaymentService
from backend.application.services.settlement_advanced_service import SettlementAdvancedService
from backend.application.services.wallet_service import WalletService
from backend.domain.models import (
    Base,
    CustomerProfile,
    FareRule,
    Payment,
    PaymentOrderAttempt,
    PaymentRefund,
    Ride,
    User,
    VehicleCategory,
    WalletTransaction,
)
from backend.integrations.payments.provider import get_payment_provider

POSTGRES_TEST_URL = os.getenv("POSTGRES_TEST_DATABASE_URL", "").strip()

pytestmark = pytest.mark.skipif(
    not POSTGRES_TEST_URL or not POSTGRES_TEST_URL.startswith("postgresql"),
    reason="BLOCKED: POSTGRES_TEST_DATABASE_URL is not configured; PostgreSQL concurrency integration tests not executed.",
)


@pytest.mark.asyncio
async def test_postgres_concurrent_duplicate_webhook_serializes_cleanly():
    """Verify PostgreSQL SELECT FOR UPDATE + uq_payment_webhook_inbox_event_digest serializes concurrent deliveries."""
    engine = create_async_engine(POSTGRES_TEST_URL, echo=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    session_factory = async_sessionmaker(engine, expire_on_commit=False)

    order_id = f"order_pg_{uuid.uuid4().hex[:10]}"
    pay_id = f"pay_pg_{uuid.uuid4().hex[:10]}"
    async with session_factory() as setup_db:
        user = User(phone_number=f"+9190{uuid.uuid4().hex[:8]}", full_name="PG Test", role="customer")
        setup_db.add(user)
        await setup_db.flush()
        user_id = user.id
        setup_db.add(
            Payment(
                user_id=user_id,
                amount_cents=50000,
                currency="INR",
                purpose="WALLET_TOPUP",
                status="CREATED",
                payment_method="RAZORPAY",
                provider_name="RAZORPAY",
                provider_order_id=order_id,
                idempotency_key=f"pg_{order_id}",
            )
        )
        await setup_db.commit()

    provider = get_payment_provider()
    orig_wh = provider.webhook_secret
    wh_secret = "whsec_pg_concurrent_2026"
    provider.webhook_secret = wh_secret

    try:
        raw_body = json.dumps({
            "event": "payment.captured",
            "payload": {
                "payment": {
                    "entity": {
                        "id": pay_id,
                        "order_id": order_id,
                        "amount": 50000,
                        "currency": "INR",
                        "status": "captured",
                    }
                }
            },
        }).encode("utf-8")
        sig = hmac.new(wh_secret.encode("utf-8"), raw_body, hashlib.sha256).hexdigest()

        async def _worker():
            async with session_factory() as db:
                return await PaymentService(db).process_webhook(raw_body, sig)

        res_a, res_b = await asyncio.gather(_worker(), _worker())
        assert {res_a["status"], res_b["status"]} == {"SUCCESS", "ALREADY_PROCESSED"}

        async with session_factory() as verify_db:
            bal = await WalletService.get_balance(verify_db, user_id)
            assert bal["balance_cents"] == 50000
            txs = (
                await verify_db.execute(select(WalletTransaction).where(WalletTransaction.reference_id == pay_id))
            ).scalars().all()
            assert len(txs) == 1
    finally:
        provider.webhook_secret = orig_wh
        await engine.dispose()


@pytest.mark.asyncio
async def test_postgres_concurrent_identical_and_partial_refunds_enforce_invariants():
    """
    Verify on genuine PostgreSQL (with SELECT FOR UPDATE + DB leases + UNIQUE idempotency_key):
    1. Two concurrent identical refund requests with a 120-char idempotency_key trigger at most 1 gateway call.
    2. Two concurrent partial refunds totaling > payment.amount_cents only allow 1 within ceiling.
    """
    engine = create_async_engine(POSTGRES_TEST_URL, echo=False, pool_size=10, max_overflow=10)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    session_factory = async_sessionmaker(engine, expire_on_commit=False)

    uid = None
    pay_idem_id = None
    pay_part_id = None
    async with session_factory() as db:
        u = User(phone_number=f"+9191{uuid.uuid4().hex[:8]}", full_name="PG Refund User", role="customer")
        db.add(u)
        await db.flush()
        uid = u.id

        await WalletService.credit_wallet(
            session=db,
            user_id=uid,
            amount_cents=100000,
            transaction_type="CREDIT",
            source="TOP_UP",
            reference_id=f"pg_ref_seed_{uuid.uuid4().hex[:8]}",
            description="PG concurrent refund seed",
            idempotency_key=f"pg_seed_{uuid.uuid4().hex[:16]}",
        )

        p_idem = Payment(
            user_id=uid,
            amount_cents=40000,
            currency="INR",
            purpose="WALLET_TOPUP",
            status="CAPTURED",
            payment_method="RAZORPAY",
            provider_name="RAZORPAY",
            provider_order_id=f"order_pg_i_{uuid.uuid4().hex[:8]}",
            provider_payment_id=f"pay_pg_i_{uuid.uuid4().hex[:8]}",
            idempotency_key=f"pg_pay_i_{uuid.uuid4().hex[:12]}",
        )
        p_part = Payment(
            user_id=uid,
            amount_cents=50000,
            currency="INR",
            purpose="WALLET_TOPUP",
            status="CAPTURED",
            payment_method="RAZORPAY",
            provider_name="RAZORPAY",
            provider_order_id=f"order_pg_p_{uuid.uuid4().hex[:8]}",
            provider_payment_id=f"pay_pg_p_{uuid.uuid4().hex[:8]}",
            idempotency_key=f"pg_pay_p_{uuid.uuid4().hex[:12]}",
        )
        db.add_all([p_idem, p_part])
        await db.flush()
        pay_idem_id = p_idem.id
        pay_part_id = p_part.id
        await db.commit()

    provider = get_payment_provider()
    long_idem_120 = f"pg_long_refund_key_{uuid.uuid4().hex[:10]}_" + ("a" * 90)
    assert len(long_idem_120) == 120

    gw_idem_calls = 0

    async def _slow_pg_idem_refund(**kwargs):
        nonlocal gw_idem_calls
        gw_idem_calls += 1
        await asyncio.sleep(0.08)
        return {"id": f"rfnd_pg_idem_{gw_idem_calls}_{uuid.uuid4().hex[:6]}", "amount": kwargs["amount_cents"], "status": "processed", "acquirer_data": {}}

    async def _worker_idem():
        async with session_factory() as s:
            return await SettlementAdvancedService(s).create_refund(
                payment_id=pay_idem_id,
                amount_cents=20000,
                reason="PG concurrent identical refund",
                is_partial=True,
                idempotency_key=long_idem_120,
            )

    with patch.object(provider, "create_refund", side_effect=_slow_pg_idem_refund):
        res1, res2 = await asyncio.gather(_worker_idem(), _worker_idem())

    assert gw_idem_calls == 1, f"Expected single Razorpay refund call for concurrent identical key, got {gw_idem_calls}"
    assert res1["internal_refund_id"] == res2["internal_refund_id"]
    assert res1["refund_id"] == res2["refund_id"]

    # Concurrent partial refunds: two 35,000 paise refunds on 50,000 paise payment
    gw_part_calls = 0

    async def _slow_pg_part_refund(**kwargs):
        nonlocal gw_part_calls
        gw_part_calls += 1
        n = gw_part_calls
        await asyncio.sleep(0.08)
        return {"id": f"rfnd_pg_part_{n}_{uuid.uuid4().hex[:6]}", "amount": kwargs["amount_cents"], "status": "processed", "acquirer_data": {}}

    async def _worker_part():
        async with session_factory() as s:
            try:
                return await SettlementAdvancedService(s).create_refund(
                    payment_id=pay_part_id,
                    amount_cents=35000,
                    reason="PG concurrent partial refund",
                    is_partial=True,
                )
            except HTTPException as exc:
                return {"error_status": exc.status_code}

    with patch.object(provider, "create_refund", side_effect=_slow_pg_part_refund):
        p1, p2 = await asyncio.gather(_worker_part(), _worker_part())

    outcomes = [p1, p2]
    assert gw_part_calls == 1
    assert sum(1 for o in outcomes if o.get("status") == "PROCESSED") == 1
    assert sum(1 for o in outcomes if o.get("error_status") == 400) == 1

    async with session_factory() as check_db:
        refreshed_part = (await check_db.execute(select(Payment).where(Payment.id == pay_part_id))).scalar_one()
        assert refreshed_part.refunded_amount_cents == 35000

    await engine.dispose()


@pytest.mark.asyncio
async def test_postgres_concurrent_order_creation_leases_multi_instance():
    """Verify PostgreSQL atomic order lease prevents duplicate gateway orders across concurrent instances."""
    engine = create_async_engine(POSTGRES_TEST_URL, echo=False, pool_size=10, max_overflow=10)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    session_factory = async_sessionmaker(engine, expire_on_commit=False)

    uid = None
    ride_id = None
    async with session_factory() as db:
        u = User(phone_number=f"+9192{uuid.uuid4().hex[:8]}", full_name="PG Order User", role="customer")
        db.add(u)
        await db.flush()
        uid = u.id
        cust = CustomerProfile(user_id=uid, preferred_payment_method="RAZORPAY", rating_average=5.0, rating_count=0)
        db.add(cust)
        await db.flush()
        cat = VehicleCategory(code=f"PG_{uuid.uuid4().hex[:6]}", name="PG EV", description="PG", capacity=3, is_active=True)
        db.add(cat)
        await db.flush()
        fr = FareRule(category_id=cat.id, city_code="DELHI_NCR", base_fare_cents=2500, per_km_cents=900, per_minute_cents=200, minimum_fare_cents=4000, is_active=True)
        db.add(fr)
        await db.flush()
        ride = Ride(
            customer_id=cust.id,
            vehicle_category_id=cat.id,
            fare_rule_id=fr.id,
            pickup_latitude=28.61,
            pickup_longitude=77.20,
            destination_latitude=28.63,
            destination_longitude=77.22,
            pickup_address="PG Start",
            destination_address="PG End",
            start_otp="1111",
            estimated_distance_km=4.0,
            estimated_duration_minutes=12.0,
            estimated_fare_cents=18000,
            final_fare_cents=18000,
            payment_method="RAZORPAY",
            status="COMPLETED",
        )
        db.add(ride)
        await db.commit()
        ride_id = ride.id

    provider = get_payment_provider()
    calls = 0

    async def _slow_order(**kwargs):
        nonlocal calls
        calls += 1
        await asyncio.sleep(0.1)
        return {"id": f"order_pg_lease_{calls}_{uuid.uuid4().hex[:6]}", "amount": kwargs["amount_cents"], "currency": "INR", "receipt": kwargs["receipt"], "status": "created"}

    async def _worker(idem: str):
        async with session_factory() as s:
            return await PaymentService(s).create_payment_intent(uid, ride_id, idem)

    with patch.object(provider, "create_razorpay_order", side_effect=_slow_order):
        o1, o2 = await asyncio.gather(_worker(f"pg_k1_{uuid.uuid4().hex[:8]}"), _worker(f"pg_k2_{uuid.uuid4().hex[:8]}"))

    assert calls == 1
    assert o1["order_id"] == o2["order_id"]

    await engine.dispose()
