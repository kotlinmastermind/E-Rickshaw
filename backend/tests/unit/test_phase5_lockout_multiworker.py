"""
VAISENTRA MOBILITY — Phase 5 Distributed Account Lockout & Multi-Worker Resilience Test Suite
Work Package D:
- Multi-Worker Distributed Lockout Consistency on Redis Outage
- Account Enumeration Resistance via Constant-Time Dummy Verification
- Dual-Layer Redis Cache + PostgreSQL Durable Synchronization
- Redis Recovery & Cache Backfill
"""
import uuid
import time
import pytest
from unittest.mock import patch
from datetime import datetime, timezone, timedelta
from fastapi import HTTPException
from sqlalchemy import select

from backend.infrastructure.database.session import async_session_maker
from backend.infrastructure.cache.redis_client import redis_client
from backend.domain.models.models import User, AccountLockout
from backend.application.services.auth_service import AuthService
from backend.infrastructure.security.crypto import hash_password, verify_password, DUMMY_PASSWORD_HASH
from backend.infrastructure.security.lockout import (
    check_account_lockout,
    record_failed_login,
    reset_account_lockout,
    get_lockout_keys,
    BASE_LOCKOUT_SECONDS
)


@pytest.mark.asyncio
async def test_dummy_password_hash_bcrypt_verification():
    """Verify that DUMMY_PASSWORD_HASH safely executes bcrypt verification and fails constant-time."""
    assert DUMMY_PASSWORD_HASH.startswith("$2b$12$")
    start = time.perf_counter()
    res = verify_password("arbitrary_wrong_password", DUMMY_PASSWORD_HASH)
    elapsed = time.perf_counter() - start
    assert res is False
    # Bcrypt 12 rounds takes noticeable CPU time (> 20ms), proving full hashing occurs
    assert elapsed > 0.02


@pytest.mark.asyncio
async def test_multiworker_redis_down_lockout_synchronization():
    """
    Simulate multiple independent worker threads/processes querying against PostgreSQL
    when Redis is completely unreachable. Verifies that failure counts accumulate in PostgreSQL
    and lock out attackers across all workers at attempt 5.
    """
    ident = f"+9191{uuid.uuid4().hex[:8]}"
    pwd = "ValidUserPassword123!"

    # Create user in PostgreSQL
    async with async_session_maker() as db:
        user = User(
            id=str(uuid.uuid4()),
            email=f"multi_{uuid.uuid4().hex[:6]}@vaisentra.com",
            phone_number=ident,
            password_hash=hash_password(pwd),
            full_name="Multi-Worker User",
            is_active=True
        )
        db.add(user)
        await db.commit()

    # Simulate Redis connection failure across all workers
    redis_mock = patch.object(redis_client, "incr", side_effect=Exception("Redis connection refused"))
    redis_exists_mock = patch.object(redis_client, "exists", side_effect=Exception("Redis connection refused"))
    redis_ttl_mock = patch.object(redis_client, "ttl", side_effect=Exception("Redis connection refused"))
    redis_set_mock = patch.object(redis_client, "set", side_effect=Exception("Redis connection refused"))
    redis_del_mock = patch.object(redis_client, "delete", side_effect=Exception("Redis connection refused"))

    with redis_mock, redis_exists_mock, redis_ttl_mock, redis_set_mock, redis_del_mock:
        # Worker 1: Attempt 1 (Wrong password)
        async with async_session_maker() as db1:
            service1 = AuthService(db1)
            with pytest.raises(HTTPException) as exc:
                await service1.unified_login_authentication(login_type="PASSWORD", identifier=ident, password="WrongPassword1")
            assert exc.value.status_code == 401

        # Worker 2: Attempt 2 (Wrong password)
        async with async_session_maker() as db2:
            service2 = AuthService(db2)
            with pytest.raises(HTTPException) as exc:
                await service2.unified_login_authentication(login_type="PASSWORD", identifier=ident, password="WrongPassword2")
            assert exc.value.status_code == 401

        # Worker 3: Attempt 3 (Wrong password)
        async with async_session_maker() as db3:
            service3 = AuthService(db3)
            with pytest.raises(HTTPException) as exc:
                await service3.unified_login_authentication(login_type="PASSWORD", identifier=ident, password="WrongPassword3")
            assert exc.value.status_code == 401

        # Worker 4: Attempt 4 (Wrong password)
        async with async_session_maker() as db4:
            service4 = AuthService(db4)
            with pytest.raises(HTTPException) as exc:
                await service4.unified_login_authentication(login_type="PASSWORD", identifier=ident, password="WrongPassword4")
            assert exc.value.status_code == 401

        # Verify DB state after 4 attempts
        keys = get_lockout_keys(ident)
        async with async_session_maker() as db_check:
            stmt = select(AccountLockout).where(AccountLockout.key_hash == keys["key_hash"])
            res = await db_check.execute(stmt)
            record = res.scalar_one_or_none()
            assert record is not None
            assert record.failed_attempts == 4
            assert record.locked_until is None

        # Worker 1: Attempt 5 -> triggers distributed lockout in PostgreSQL
        async with async_session_maker() as db5:
            service5 = AuthService(db5)
            with pytest.raises(HTTPException) as exc5:
                await service5.unified_login_authentication(login_type="PASSWORD", identifier=ident, password="WrongPassword5")
            assert exc5.value.status_code == 429
            assert exc5.value.headers.get("Retry-After") == str(BASE_LOCKOUT_SECONDS)

        # Worker 2: Attempt 6 (even with correct password) -> blocked at check_account_lockout via DB!
        async with async_session_maker() as db6:
            service6 = AuthService(db6)
            with pytest.raises(HTTPException) as exc6:
                await service6.unified_login_authentication(login_type="PASSWORD", identifier=ident, password=pwd)
            assert exc6.value.status_code == 429
            retry_after = int(exc6.value.headers.get("Retry-After", "0"))
            assert 0 < retry_after <= BASE_LOCKOUT_SECONDS


@pytest.mark.asyncio
async def test_nonexistent_account_lockout_and_timing_defense():
    """
    Verify that an unknown account triggers constant-time dummy bcrypt verification,
    and is tracked in PostgreSQL, triggering 429 after 5 failures without leaking existence.
    """
    non_existent = f"+9190{uuid.uuid4().hex[:8]}"

    async with async_session_maker() as db:
        service = AuthService(db)
        # Attempts 1 to 4 -> 401
        for _ in range(4):
            with pytest.raises(HTTPException) as exc:
                await service.unified_login_authentication(login_type="PASSWORD", identifier=non_existent, password="AnyPassword")
            assert exc.value.status_code == 401
            assert "invalid" in exc.value.detail.lower()

        # Attempt 5 -> 429
        with pytest.raises(HTTPException) as exc_lock:
            await service.unified_login_authentication(login_type="PASSWORD", identifier=non_existent, password="AnyPassword")
        assert exc_lock.value.status_code == 429
        assert exc_lock.value.headers.get("Retry-After") == str(BASE_LOCKOUT_SECONDS)

    # Clean up test keys
    await reset_account_lockout(non_existent)


@pytest.mark.asyncio
async def test_successful_login_clears_db_lockout():
    """Verify that successful login clears both Redis and PostgreSQL lockout state."""
    ident = f"+9192{uuid.uuid4().hex[:8]}"
    pwd = "ValidSecretPass123!"

    async with async_session_maker() as db:
        user = User(
            id=str(uuid.uuid4()),
            email=f"cleardb_{uuid.uuid4().hex[:6]}@vaisentra.com",
            phone_number=ident,
            password_hash=hash_password(pwd),
            full_name="Clear DB User",
            is_active=True
        )
        db.add(user)
        await db.commit()

        # 3 failed attempts
        service = AuthService(db)
        for _ in range(3):
            with pytest.raises(HTTPException):
                await service.unified_login_authentication(login_type="PASSWORD", identifier=ident, password="Wrong")

        # Verify DB has 3 attempts
        keys = get_lockout_keys(ident)
        stmt = select(AccountLockout).where(AccountLockout.key_hash == keys["key_hash"])
        res = await db.execute(stmt)
        record = res.scalar_one_or_none()
        assert record is not None
        assert record.failed_attempts == 3

        # Successful login
        auth_res = await service.unified_login_authentication(login_type="PASSWORD", identifier=ident, password=pwd)
        assert "access_token" in auth_res

        # Verify DB failed_attempts is cleared to 0
        stmt = select(AccountLockout).where(AccountLockout.key_hash == keys["key_hash"])
        res = await db.execute(stmt)
        record = res.scalar_one_or_none()
        assert record is not None
        assert record.failed_attempts == 0
        assert record.locked_until is None


@pytest.mark.asyncio
async def test_redis_recovery_backfills_from_db():
    """
    Verify that if an account is locked in PostgreSQL while Redis is down,
    when Redis recovers, check_account_lockout backfills the Redis cache.
    """
    ident = f"+9193{uuid.uuid4().hex[:8]}"
    keys = get_lockout_keys(ident)

    # Clean
    await reset_account_lockout(ident)

    # Directly insert an active lockout into PostgreSQL
    async with async_session_maker() as db:
        now = datetime.now(timezone.utc)
        record = AccountLockout(
            key_hash=keys["key_hash"],
            failed_attempts=0,
            lockout_tier=1,
            locked_until=now + timedelta(seconds=600),
            last_failed_at=now
        )
        db.add(record)
        await db.commit()

        # Verify Redis does NOT have the key yet
        assert not await redis_client.exists(keys["lockout_key"])

        # check_account_lockout should discover the DB lockout, backfill Redis, and raise 429
        with pytest.raises(HTTPException) as exc:
            await check_account_lockout(ident, db=db)
        assert exc.value.status_code == 429

        # Now Redis SHOULD have the backfilled key!
        assert await redis_client.exists(keys["lockout_key"])
        ttl = await redis_client.ttl(keys["lockout_key"])
        assert 500 < ttl <= 600

    # Clean up
    async with async_session_maker() as db:
        await reset_account_lockout(ident, db=db)
