import asyncio
import logging
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import DeclarativeBase, Session
from typing import AsyncGenerator
from backend.core.config import settings

logger = logging.getLogger("backend.database")

class Base(DeclarativeBase):
    pass

connect_args = {"check_same_thread": False, "timeout": 30} if settings.DATABASE_URL.startswith("sqlite") else {"timeout": 15, "command_timeout": 15}

engine_kwargs = {
    "echo": False,
    "future": True,
    "connect_args": connect_args
}
if not settings.DATABASE_URL.startswith("sqlite"):
    engine_kwargs.update({
        "pool_pre_ping": True,
        "pool_size": 20,
        "max_overflow": 10,
        "isolation_level": "READ COMMITTED"
    })

engine = create_async_engine(
    settings.DATABASE_URL,
    **engine_kwargs
)

async_session_maker = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autoflush=False
)

async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with async_session_maker() as session:
        try:
            yield session
        finally:
            await session.close()

async def init_db():
    """
    Initialize database schema. In development/test environments without full migration
    execution, Base.metadata.create_all creates any missing tables.
    In production environments, DDL schema management is strictly managed by Alembic.
    """
    if getattr(settings, "ENVIRONMENT", "") == "production":
        logger.info("Production mode detected: Skipping automatic DDL schema creation. Alembic manages production schemas.")
        return
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
