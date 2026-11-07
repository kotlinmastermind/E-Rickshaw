import os
os.environ["TESTING"] = "1"
os.environ["DATABASE_URL"] = "sqlite+aiosqlite:///file:testdb?mode=memory&cache=shared&uri=true"
os.environ["REDIS_URL"] = ""

from backend.core.config import settings
settings.DATABASE_URL = "sqlite+aiosqlite:///file:testdb?mode=memory&cache=shared&uri=true"
settings.DATABASE_URL_SYNC = "sqlite:///file:testdb?mode=memory&cache=shared&uri=true"
settings.REDIS_URL = None

import pytest
import pytest_asyncio
from sqlalchemy import select
from backend.domain.models import VehicleCategory, FareRule
from backend.infrastructure.database.session import async_session_maker, init_db

_db_initialized = False

async def _seed_test_data(session):
    stmt = select(VehicleCategory)
    res = await session.execute(stmt)
    if not res.scalars().first():
        standard = VehicleCategory(code="E_RICKSHAW_STANDARD", name="Standard E-Rickshaw", capacity=4, sort_order=1)
        shared = VehicleCategory(code="E_RICKSHAW_SHARE", name="Shared E-Rickshaw", capacity=4, sort_order=2)
        e_auto = VehicleCategory(code="E_AUTO", name="High-Speed Electric Auto", capacity=3, sort_order=3)
        e_cargo = VehicleCategory(code="E_CARGO_LOADER", name="E-Loader Cargo", capacity=1, sort_order=4)
        session.add_all([standard, shared, e_auto, e_cargo])
        await session.flush()
        rules = [
            FareRule(category_id=standard.id, version=1, base_fare_cents=2000, per_km_cents=1000, per_minute_cents=100, minimum_fare_cents=2000, cancellation_fee_cents=1000, surge_multiplier=1.0, is_active=True),
            FareRule(category_id=shared.id, version=1, base_fare_cents=1000, per_km_cents=600, per_minute_cents=50, minimum_fare_cents=1500, cancellation_fee_cents=500, surge_multiplier=1.0, is_active=True),
            FareRule(category_id=e_auto.id, version=1, base_fare_cents=3000, per_km_cents=1200, per_minute_cents=150, minimum_fare_cents=3000, cancellation_fee_cents=1500, surge_multiplier=1.0, is_active=True),
            FareRule(category_id=e_cargo.id, version=1, base_fare_cents=5000, per_km_cents=1500, per_minute_cents=200, minimum_fare_cents=6000, cancellation_fee_cents=2000, surge_multiplier=1.0, is_active=True)
        ]
        session.add_all(rules)
        await session.commit()

@pytest_asyncio.fixture(scope="function")
async def db_session():
    global _db_initialized
    if not _db_initialized:
        await init_db()
        async with async_session_maker() as session:
            await _seed_test_data(session)
        _db_initialized = True
    async with async_session_maker() as session:
        yield session
