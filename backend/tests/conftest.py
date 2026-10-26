import os
os.environ["DATABASE_URL"] = "sqlite+aiosqlite:///./luxury_ride.db"
import pytest
import pytest_asyncio
from backend.infrastructure.database.session import async_session_maker, init_db

@pytest_asyncio.fixture(scope="function")
async def db_session():
    await init_db()
    async with async_session_maker() as session:
        yield session
        await session.rollback()
