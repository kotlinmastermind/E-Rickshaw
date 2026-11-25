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

import pytest
from sqlalchemy import select
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from backend.application.services.payment_service import PaymentService
from backend.application.services.wallet_service import WalletService
from backend.domain.models import Base, Payment, User, WalletTransaction
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
