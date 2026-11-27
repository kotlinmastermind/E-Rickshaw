import os
import json
os.environ["TESTING"] = "1"
os.environ["DATABASE_URL"] = "sqlite+aiosqlite:///file:testdb?mode=memory&cache=shared&uri=true"
os.environ["REDIS_URL"] = os.getenv("REDIS_TEST_URL", "")

from backend.core.config import settings
settings.TESTING = True
settings.DATABASE_URL = "sqlite+aiosqlite:///file:testdb?mode=memory&cache=shared&uri=true"
settings.DATABASE_URL_SYNC = "sqlite:///file:testdb?mode=memory&cache=shared&uri=true"
settings.REDIS_URL = os.getenv("REDIS_TEST_URL") or None

import pytest
import pytest_asyncio
from sqlalchemy import select
from backend.domain.models import VehicleCategory, FareRule, GeofenceZone
from backend.infrastructure.database.session import async_session_maker, init_db

import sqlite3
_persistent_sqlite_conn = sqlite3.connect("file:testdb?mode=memory&cache=shared&uri=true", uri=True)

_db_initialized = False

async def _seed_test_data(session):
    stmt = select(VehicleCategory)
    res = await session.execute(stmt)
    if not res.scalars().first():
        standard = VehicleCategory(code="E_RICKSHAW_STANDARD", name="Standard E-Rickshaw", capacity=4, sort_order=1)
        shared = VehicleCategory(code="E_RICKSHAW_SHARE", name="Shared E-Rickshaw", capacity=4, sort_order=2)
        e_auto = VehicleCategory(code="E_AUTO", name="High-Speed Electric Auto", capacity=3, sort_order=3)
        e_cargo = VehicleCategory(code="E_CARGO_LOADER", name="E-Loader Cargo", capacity=1, sort_order=4)
        sedan = VehicleCategory(code="SEDAN", name="Executive Sedan", capacity=4, sort_order=5)
        session.add_all([standard, shared, e_auto, e_cargo, sedan])
        await session.flush()
        rules = [
            FareRule(category_id=standard.id, city_code="DELHI_NCR", version=1, base_fare_cents=2000, per_km_cents=1000, per_minute_cents=100, minimum_fare_cents=2000, cancellation_fee_cents=1000, surge_multiplier=1.0, is_active=True),
            FareRule(category_id=shared.id, city_code="DELHI_NCR", version=1, base_fare_cents=1000, per_km_cents=600, per_minute_cents=50, minimum_fare_cents=1500, cancellation_fee_cents=500, surge_multiplier=1.0, is_active=True),
            FareRule(category_id=e_auto.id, city_code="DELHI_NCR", version=1, base_fare_cents=3000, per_km_cents=1200, per_minute_cents=150, minimum_fare_cents=3000, cancellation_fee_cents=1500, surge_multiplier=1.0, is_active=True),
            FareRule(category_id=e_cargo.id, city_code="DELHI_NCR", version=1, base_fare_cents=5000, per_km_cents=1500, per_minute_cents=200, minimum_fare_cents=6000, cancellation_fee_cents=2000, surge_multiplier=1.0, is_active=True),
            FareRule(category_id=sedan.id, city_code="DELHI_NCR", version=1, base_fare_cents=4000, per_km_cents=1400, per_minute_cents=150, minimum_fare_cents=4000, cancellation_fee_cents=2000, surge_multiplier=1.0, is_active=True),
            FareRule(category_id=sedan.id, city_code="MUMBAI", version=1, base_fare_cents=4000, per_km_cents=1400, per_minute_cents=150, minimum_fare_cents=4000, cancellation_fee_cents=2000, surge_multiplier=1.0, is_active=True),
        ]
        session.add_all(rules)
        await session.commit()

    existing_areas = (await session.execute(
        select(GeofenceZone).where(
            GeofenceZone.zone_type == "SERVICE_AREA",
            GeofenceZone.is_active.is_(True),
        )
    )).scalars().all()
    if not existing_areas:
        test_areas = [
            ("TEST_DELHI_NCR", [{"lat": 27.0, "lng": 76.0}, {"lat": 27.0, "lng": 78.0}, {"lat": 29.5, "lng": 78.0}, {"lat": 29.5, "lng": 76.0}]),
            ("TEST_MUMBAI", [{"lat": 17.5, "lng": 72.0}, {"lat": 17.5, "lng": 74.0}, {"lat": 20.0, "lng": 74.0}, {"lat": 20.0, "lng": 72.0}]),
            ("TEST_BENGALURU", [{"lat": 11.5, "lng": 76.5}, {"lat": 11.5, "lng": 78.5}, {"lat": 14.0, "lng": 78.5}, {"lat": 14.0, "lng": 76.5}]),
            ("TEST_PATNA", [{"lat": 24.5, "lng": 84.0}, {"lat": 24.5, "lng": 86.0}, {"lat": 26.5, "lng": 86.0}, {"lat": 26.5, "lng": 84.0}]),
            ("TEST_LUCKNOW", [{"lat": 25.5, "lng": 80.0}, {"lat": 25.5, "lng": 82.0}, {"lat": 28.0, "lng": 82.0}, {"lat": 28.0, "lng": 80.0}]),
        ]
        session.add_all([
            GeofenceZone(
                name=f"Test coverage {city_code}",
                city_code=city_code,
                zone_type="SERVICE_AREA",
                coordinates_json=json.dumps(points),
                is_active=True,
            )
            for city_code, points in test_areas
        ])
        await session.commit()

@pytest_asyncio.fixture(scope="function", loop_scope="function", autouse=True)
async def db_session():
    global _db_initialized
    import asyncio
    from backend.infrastructure.database.session import engine

    # Ensure any cached connection's async lock is bound to the current running event loop
    try:
        pool = engine.sync_engine.pool
        if hasattr(pool, "connection") and pool.connection and pool.connection.dbapi_connection:
            dbapi_conn = pool.connection.dbapi_connection
            if hasattr(dbapi_conn, "_execute_mutex"):
                dbapi_conn._execute_mutex = asyncio.Lock()
    except Exception:
        pass

    from backend.infrastructure.database.session import Base
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
    await init_db()
    async with async_session_maker() as session:
        await _seed_test_data(session)
    _db_initialized = True
    async with async_session_maker() as session:
        yield session

@pytest_asyncio.fixture(scope="function", loop_scope="function", autouse=True)
async def flush_cache_isolation():
    from backend.infrastructure.cache.redis_client import redis_client

    async def clear_cache_without_binding_async_redis():
        # Do not call redis_client.flushdb() here: that initializes the
        # global redis.asyncio pool on pytest's loop. WebSocket TestClient
        # runs its ASGI app on a different portal loop and must create/use
        # its own async Redis client there.
        redis_client._redis = None
        redis_client.is_connected = False
        redis_client._last_reconnect_attempt = 0.0
        redis_client._reconnect_backoff = 1.0

        if redis_client.redis_url:
            import redis as sync_redis
            client = sync_redis.Redis.from_url(
                redis_client.redis_url,
                encoding="utf-8",
                decode_responses=True
            )
            try:
                client.flushdb()
            finally:
                client.close()
        else:
            await redis_client._fallback.clear()

    await clear_cache_without_binding_async_redis()
    yield
    await clear_cache_without_binding_async_redis()

@pytest_asyncio.fixture(scope="function", loop_scope="function")
async def client():
    from httpx import AsyncClient, ASGITransport
    from backend.main import app
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        yield c

@pytest_asyncio.fixture(scope="function", loop_scope="function")
async def customer_user(db_session):
    import uuid
    from datetime import datetime, timezone, timedelta
    from backend.domain.models import User, CustomerProfile, Session
    user_id = str(uuid.uuid4())
    user = User(
        id=user_id,
        phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}",
        email=f"customer_{user_id[:8]}@example.com",
        full_name="Test Customer",
        role="customer",
        is_active=True,
        is_verified=True
    )
    prof = CustomerProfile(user_id=user_id, rating_average=5.0)
    sess = Session(id=str(uuid.uuid4()), user_id=user_id, token_hash=f"dummy_{user_id}", expires_at=datetime.now(timezone.utc) + timedelta(days=1))
    db_session.add_all([user, prof, sess])
    await db_session.commit()
    return user

@pytest_asyncio.fixture(scope="function", loop_scope="function")
async def customer_token(customer_user, db_session):
    from sqlalchemy import select
    from backend.domain.models import Session
    from backend.infrastructure.security.crypto import create_jwt_token
    stmt = select(Session).where(Session.user_id == customer_user.id)
    sess = (await db_session.execute(stmt)).scalars().first()
    return create_jwt_token(user_id=customer_user.id, role="customer", session_id=sess.id)

@pytest_asyncio.fixture(scope="function", loop_scope="function")
async def second_customer_user(db_session):
    import uuid
    from datetime import datetime, timezone, timedelta
    from backend.domain.models import User, CustomerProfile, Session
    user_id = str(uuid.uuid4())
    user = User(
        id=user_id,
        phone_number=f"+9198{uuid.uuid4().int % 100000000:08d}",
        email=f"customer2_{user_id[:8]}@example.com",
        full_name="Second Test Customer",
        role="customer",
        is_active=True,
        is_verified=True
    )
    prof = CustomerProfile(user_id=user_id, rating_average=5.0)
    sess = Session(id=str(uuid.uuid4()), user_id=user_id, token_hash=f"dummy_{user_id}", expires_at=datetime.now(timezone.utc) + timedelta(days=1))
    db_session.add_all([user, prof, sess])
    await db_session.commit()
    return user

@pytest_asyncio.fixture(scope="function", loop_scope="function")
async def second_customer_token(second_customer_user, db_session):
    from sqlalchemy import select
    from backend.domain.models import Session
    from backend.infrastructure.security.crypto import create_jwt_token
    stmt = select(Session).where(Session.user_id == second_customer_user.id)
    sess = (await db_session.execute(stmt)).scalars().first()
    return create_jwt_token(user_id=second_customer_user.id, role="customer", session_id=sess.id)

