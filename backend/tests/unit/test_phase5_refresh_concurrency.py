"""
VAISENTRA MOBILITY — Phase 5 Refresh Token Concurrency & Replay Remediation Test Suite
Scope: C-P5-01 Targeted Verification

Tests:
1. First refresh succeeds and returns valid T1.
2. Two simultaneous requests using T0 do not invalidate winning T1.
3. Losing request receives documented non-destructive HTTP 409 response.
4. T1 remains usable after duplicate request within grace window.
5. T0 replay after grace window revokes session family.
6. T1 becomes invalid after confirmed replay revocation.
7. Different sessions remain isolated.
8. Expired tokens remain rejected.
9. Revoked sessions remain rejected.
10. Database commit failure rolls back without returning credentials.
11. Redis outage does not cause legitimate session revocation.
12. Process restart preserves authoritative grace period state.
13. Environment-gated PostgreSQL multi-worker concurrency integration test.
"""
import os
import asyncio
import uuid
import pytest
from datetime import datetime, timezone, timedelta
from unittest.mock import patch, AsyncMock
from httpx import AsyncClient, ASGITransport
from sqlalchemy import select, update

from backend.main import app
from backend.core.config import settings
from backend.infrastructure.database.session import async_session_maker
from backend.infrastructure.cache.redis_client import redis_client
from backend.infrastructure.security.crypto import create_jwt_token, hash_secret
from backend.domain.models.models import User, Session
from backend.application.services.auth_service import AuthService


# Helper function to create an active test user and session
async def create_test_user_and_session(db, prefix="rot"):
    uid = str(uuid.uuid4())
    sid = str(uuid.uuid4())
    email = f"{prefix}_{uid[:8]}@vaisentra.com"
    phone = f"+9199{uid[:8]}"
    
    user = User(
        id=uid,
        email=email,
        phone_number=phone,
        full_name=f"User {prefix}",
        role="customer",
        is_active=True,
        password_hash="hashed_dummy_password"
    )
    db.add(user)
    
    t0 = create_jwt_token(user_id=uid, role="customer", session_id=sid, token_type="refresh")
    t0_hash = hash_secret(t0)
    
    session = Session(
        id=sid,
        user_id=uid,
        token_hash=t0_hash,
        is_revoked=False,
        expires_at=datetime.now(timezone.utc) + timedelta(days=7)
    )
    db.add(session)
    await db.commit()
    return user, session, t0


# 1. First refresh succeeds and returns valid T1
@pytest.mark.asyncio
async def test_first_refresh_succeeds_and_returns_valid_t1():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        async with async_session_maker() as db:
            _, session, t0 = await create_test_user_and_session(db, "first_rot")
            session_id = session.id

        resp = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
        assert resp.status_code == 200
        data = resp.json()
        assert "access_token" in data
        assert "refresh_token" in data
        t1 = data["refresh_token"]
        assert t1 != t0

        # Verify DB state
        async with async_session_maker() as db:
            res = await db.execute(select(Session).where(Session.id == session_id))
            updated_session = res.scalar_one()
            assert updated_session.token_hash == hash_secret(t1)
            assert updated_session.previous_token_hash == hash_secret(t0)
            assert updated_session.rotated_at is not None
            assert updated_session.is_revoked is False


# 2 & 3. Two simultaneous requests using T0: Winner gets 200, loser gets 409, winning T1 NOT invalidated
@pytest.mark.asyncio
async def test_concurrent_refresh_requests_winner_200_loser_409_t1_preserved():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        async with async_session_maker() as db:
            _, session, t0 = await create_test_user_and_session(db, "concur_win")
            session_id = session.id

        # Launch concurrent refresh calls with the exact same T0
        req1 = client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
        req2 = client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
        res1, res2 = await asyncio.gather(req1, req2)

        statuses = [res1.status_code, res2.status_code]
        # Requirement: Winner receives 200, losing duplicate receives non-destructive 409
        assert 200 in statuses, f"Expected 200 in statuses, got {statuses}"
        assert 409 in statuses, f"Expected 409 in statuses, got {statuses}"

        # Identify winner and loser responses
        winner_res = res1 if res1.status_code == 200 else res2
        loser_res = res2 if res1.status_code == 200 else res1

        t1 = winner_res.json()["refresh_token"]
        loser_data = loser_res.json()
        assert "concurrency grace period active" in loser_data["detail"].lower()

        # Requirement: T1 must REMAIN USABLE and session MUST NOT be revoked!
        async with async_session_maker() as db:
            res = await db.execute(select(Session).where(Session.id == session_id))
            current_session = res.scalar_one()
            assert current_session.is_revoked is False, "DEFECT: Session family was revoked by concurrent race!"

        # Use T1 in a subsequent rotation -> MUST SUCCEED with 200!
        t1_rot_res = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t1})
        assert t1_rot_res.status_code == 200, f"Winning T1 was invalidated! Status: {t1_rot_res.status_code}"
        t2 = t1_rot_res.json()["refresh_token"]
        assert t2 != t1


# 4. T1 remains usable after duplicate request within grace window
@pytest.mark.asyncio
async def test_t1_remains_usable_after_duplicate_request_within_grace_window():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        async with async_session_maker() as db:
            _, session, t0 = await create_test_user_and_session(db, "grace_dup")
            session_id = session.id

        # First rotation: T0 -> T1
        rot1 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
        assert rot1.status_code == 200
        t1 = rot1.json()["refresh_token"]

        # Duplicate request with T0 immediately (within 10s grace window)
        dup_rot = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
        assert dup_rot.status_code == 409
        assert "recently rotated" in dup_rot.json()["detail"].lower()

        # Session must still be active
        async with async_session_maker() as db:
            res = await db.execute(select(Session).where(Session.id == session_id))
            assert res.scalar_one().is_revoked is False

        # T1 must be usable to rotate to T2
        rot2 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t1})
        assert rot2.status_code == 200
        assert rot2.json()["refresh_token"] != t1


# 5 & 6. T0 replay after grace window revokes session family, T1 becomes invalid
@pytest.mark.asyncio
async def test_t0_replay_after_grace_window_revokes_session_family_and_invalidates_t1():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        async with async_session_maker() as db:
            _, session, t0 = await create_test_user_and_session(db, "stale_rep")
            session_id = session.id

        # First rotation: T0 -> T1
        rot1 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
        assert rot1.status_code == 200
        t1 = rot1.json()["refresh_token"]

        # Manually age the rotation timestamp beyond the 10-second grace window (e.g., 30s ago)
        stale_time = datetime.now(timezone.utc) - timedelta(seconds=30)
        async with async_session_maker() as db:
            await db.execute(
                update(Session)
                .where(Session.id == session_id)
                .values(rotated_at=stale_time)
            )
            await db.commit()

        # Replay T0 after grace period -> MUST TRIGGER REPLAY DEFENSE (401)
        stale_replay = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
        assert stale_replay.status_code == 401
        assert "reuse detected" in stale_replay.json()["detail"].lower()

        # Verify DB: Session family must now be revoked
        async with async_session_maker() as db:
            res = await db.execute(select(Session).where(Session.id == session_id))
            assert res.scalar_one().is_revoked is True

        # Requirement 6: T1 must now ALSO be rejected because session was revoked!
        t1_attempt = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t1})
        assert t1_attempt.status_code == 401
        assert "revoked" in t1_attempt.json()["detail"].lower()


# 7. Different sessions remain isolated
@pytest.mark.asyncio
async def test_different_sessions_remain_isolated():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        async with async_session_maker() as db:
            _, s1, t0_s1 = await create_test_user_and_session(db, "sess1")
            _, s2, t0_s2 = await create_test_user_and_session(db, "sess2")
            s1_id, s2_id = s1.id, s2.id

        # Rotate session 1
        rot_s1 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0_s1})
        assert rot_s1.status_code == 200

        # Session 2 must remain completely intact and unaffected
        async with async_session_maker() as db:
            res_s2 = await db.execute(select(Session).where(Session.id == s2_id))
            sess2 = res_s2.scalar_one()
            assert sess2.is_revoked is False
            assert sess2.previous_token_hash is None

        # Rotate session 2 with its own token -> MUST SUCCEED
        rot_s2 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0_s2})
        assert rot_s2.status_code == 200


# 8. Expired tokens remain rejected
@pytest.mark.asyncio
async def test_expired_tokens_remain_rejected():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # Create token with expired timestamp
        expired_token = create_jwt_token(
            user_id="fake-user",
            role="customer",
            session_id="fake-session",
            token_type="refresh",
            expires_delta=timedelta(seconds=-60)
        )
        resp = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": expired_token})
        assert resp.status_code == 401
        assert "invalid, expired" in resp.json()["detail"].lower()


# 9. Revoked sessions remain rejected
@pytest.mark.asyncio
async def test_revoked_sessions_remain_rejected():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        async with async_session_maker() as db:
            _, session, t0 = await create_test_user_and_session(db, "revoked_pre")
            session.is_revoked = True
            await db.commit()

        resp = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
        assert resp.status_code == 401
        assert "revoked" in resp.json()["detail"].lower()


# 10. Database commit failure rolls back without returning credentials
@pytest.mark.asyncio
async def test_database_commit_failure_rolls_back_without_returning_credentials():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        async with async_session_maker() as db:
            _, session, t0 = await create_test_user_and_session(db, "commit_fail")
            session_id = session.id

        # Patch AsyncSession.commit to simulate database failure during commit
        with patch("sqlalchemy.ext.asyncio.AsyncSession.commit", side_effect=Exception("Database disk I/O error")):
            resp = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
            assert resp.status_code == 500
            assert "failed to persist rotated session credentials" in resp.json()["detail"].lower()

        # Verify rollback: token_hash must still be original t0_hash, previous_token_hash is None
        async with async_session_maker() as db:
            res = await db.execute(select(Session).where(Session.id == session_id))
            sess = res.scalar_one()
            assert sess.token_hash == hash_secret(t0)
            assert sess.previous_token_hash is None


# 11. Redis outage does not cause legitimate session revocation
@pytest.mark.asyncio
async def test_redis_outage_does_not_cause_legitimate_session_revocation():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        async with async_session_maker() as db:
            _, session, t0 = await create_test_user_and_session(db, "redis_out")
            session_id = session.id

        # Simulate Redis total outage by throwing exceptions on all Redis client methods
        with patch.object(redis_client, "exists", side_effect=Exception("Redis down")):
            with patch.object(redis_client, "set", side_effect=Exception("Redis down")):
                # 1. First rotation must succeed via PostgreSQL authority
                rot1 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
                assert rot1.status_code == 200
                t1 = rot1.json()["refresh_token"]

                # 2. Duplicate request within grace period while Redis is STILL down:
                # Must return non-destructive 409, NOT revoke the session!
                dup_rot = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
                assert dup_rot.status_code == 409
                assert "concurrency grace period active" in dup_rot.json()["detail"].lower()

                # Session must still be valid in DB
                async with async_session_maker() as db:
                    res = await db.execute(select(Session).where(Session.id == session_id))
                    assert res.scalar_one().is_revoked is False

                # 3. T1 must rotate cleanly to T2 while Redis is down
                rot2 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t1})
                assert rot2.status_code == 200


# 12. Process restart preserves authoritative grace period state
@pytest.mark.asyncio
async def test_process_restart_preserves_authoritative_grace_period_state():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        async with async_session_maker() as db:
            _, session, t0 = await create_test_user_and_session(db, "restart_test")
            session_id = session.id

        # First rotation on initial "process"
        rot1 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
        assert rot1.status_code == 200
        t1 = rot1.json()["refresh_token"]

        # Simulate process restart by clearing all Redis cache / in-memory structures completely
        if hasattr(redis_client, "fallback") and redis_client.fallback:
            redis_client.fallback._cache.clear()

        # New request arrives on the "restarted" process with T0 within grace period:
        # PostgreSQL has the authoritative state (previous_token_hash and rotated_at)
        rot_dup = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
        assert rot_dup.status_code == 409
        assert "concurrency grace period active" in rot_dup.json()["detail"].lower()

        # Session is preserved and T1 can rotate to T2
        rot2 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": t1})
        assert rot2.status_code == 200


# 13. Environment-gated PostgreSQL multi-worker concurrency integration test
@pytest.mark.asyncio
async def test_postgres_concurrency_gated():
    """Environment-gated integration test running against real PostgreSQL when TEST_POSTGRES_URL is configured."""
    pg_url = os.environ.get("TEST_POSTGRES_URL")
    if not pg_url:
        pytest.skip("TEST_POSTGRES_URL environment variable is not configured. Gated PostgreSQL concurrency verification skipped locally (must run in CI with PostgreSQL container).")

    from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
    from backend.domain.models import Base

    engine = create_async_engine(pg_url, pool_size=10, max_overflow=20)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    pg_session_maker = async_sessionmaker(engine, expire_on_commit=False)

    async with pg_session_maker() as db:
        user, session, t0 = await create_test_user_and_session(db, "pg_concur")
        session_id = session.id

    # Spawn 5 concurrent requests using independent database sessions against PostgreSQL
    async def run_rotation():
        async with pg_session_maker() as db:
            service = AuthService(db)
            try:
                result = await service.refresh_token_rotation(t0)
                return ("SUCCESS", result)
            except Exception as e:
                return ("EXCEPTION", e)

    tasks = [run_rotation() for _ in range(5)]
    results = await asyncio.gather(*tasks)

    successes = [r for r in results if r[0] == "SUCCESS"]
    exceptions = [r for r in results if r[0] == "EXCEPTION"]

    # Exactly 1 request must win the CAS update (200), and the remaining 4 must receive HTTP 409
    assert len(successes) == 1, f"Expected exactly 1 winner, got {len(successes)}"
    assert len(exceptions) == 4

    for _, exc in exceptions:
        assert hasattr(exc, "status_code")
        assert exc.status_code == 409

    # Winning credentials must remain valid in PostgreSQL
    async with pg_session_maker() as db:
        res = await db.execute(select(Session).where(Session.id == session_id))
        sess = res.scalar_one()
        assert sess.is_revoked is False

    await engine.dispose()
