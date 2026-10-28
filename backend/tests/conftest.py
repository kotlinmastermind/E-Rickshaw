import os
os.environ["TESTING"] = "1"
os.environ["DATABASE_URL"] = "sqlite+aiosqlite:///./luxury_ride.db"
os.environ["REDIS_URL"] = ""

from backend.core.config import settings
settings.DATABASE_URL = "sqlite+aiosqlite:///./luxury_ride.db"
settings.DATABASE_URL_SYNC = "sqlite:///./luxury_ride.db"
settings.REDIS_URL = None

import pytest
import pytest_asyncio
from backend.infrastructure.database.session import async_session_maker, init_db

_db_initialized = False

@pytest_asyncio.fixture(scope="function")
async def db_session():
    global _db_initialized
    if not _db_initialized:
        await init_db()
        _db_initialized = True
    async with async_session_maker() as session:
        yield session
        await session.rollback()
