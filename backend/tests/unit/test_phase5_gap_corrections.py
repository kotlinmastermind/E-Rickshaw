"""
VAISENTRA MOBILITY — Phase 5 Targeted Security Gap Corrections Test Suite
Covers:
- Work Package C: WebSocket JWT Transport Security (Header vs Ticket vs Prohibited Query Param)
- Work Package D: Refresh Token Concurrency, Atomicity, and PostgreSQL Authority
- Work Package D: Bounded Progressive Account Lockout & Redis Resilience
"""
import asyncio
import time
import uuid
import os
import pytest
from datetime import datetime, timezone, timedelta
from unittest.mock import patch, AsyncMock
from httpx import AsyncClient, ASGITransport
from starlette.testclient import TestClient
from starlette.websockets import WebSocketDisconnect
from sqlalchemy import select, update

from backend.main import app
from backend.core.config import settings
from backend.infrastructure.database.session import async_session_maker
from backend.infrastructure.cache.redis_client import redis_client
from backend.infrastructure.security.crypto import create_jwt_token, hash_secret
from backend.domain.models.models import User, Session, DriverProfile
from backend.application.services.auth_service import AuthService
from backend.infrastructure.security.lockout import (
    check_account_lockout,
    record_failed_login,
    reset_account_lockout,
    get_lockout_keys,
    BASE_LOCKOUT_SECONDS,
    MAX_LOCKOUT_SECONDS
)


# ---------------------------------------------------------------------------
# WORK PACKAGE C: WEBSOCKET TRANSPORT SECURITY TESTS
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_ws_jwt_query_transport_rejected():
    """Verify that passing JWT token in URL query parameter (?token=...) is strictly rejected."""
    client = TestClient(app)
    # Even if valid JWT is passed in ?token=, it must be rejected with code 1008
    with pytest.raises(WebSocketDisconnect) as exc:
        with client.websocket_connect("/api/v1/ws/driver/dispatch?token=eyJhbGciOiJIUzI1NiJ9.dummy.sig") as ws:
            pass
    assert exc.value.code == 1008


@pytest.mark.asyncio
async def test_ws_bearer_header_accepted():
    """Verify that passing JWT token in Authorization: Bearer header is accepted for approved drivers."""
    uid = str(uuid.uuid4())
    sid = str(uuid.uuid4())
    async with async_session_maker() as db:
        user = User(
            id=uid,
            email=f"ws_driver_{uid[:8]}@vaisentra.com",
            phone_number=f"+9188{uid[:8]}",
            full_name="WS Driver Test",
            role="driver",
            is_active=True
        )
        profile = DriverProfile(
            id=uid,
            user_id=uid,
            license_number=f"DL-{uid[:8]}",
            approval_status="APPROVED"
        )
        session = Session(
            id=sid,
            user_id=uid,
            token_hash=hash_secret("sample_refresh_token"),
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        db.add_all([user, profile, session])
        await db.commit()

    token = create_jwt_token(user_id=uid, role="driver", session_id=sid, expires_delta=timedelta(hours=1))

    client = TestClient(app)
    headers = {"Authorization": f"Bearer {token}"}
    with client.websocket_connect("/api/v1/ws/driver/dispatch", headers=headers) as ws:
        # Connected successfully! Send a ping
        ws.send_text("ping")
        resp = ws.receive_text()
        assert resp == "pong"


@pytest.mark.asyncio
async def test_ws_ticket_auth_success():
    """Verify that browser clients can obtain single-use ticket and authenticate via ?ticket=<uuid>."""
    uid = str(uuid.uuid4())
    sid = str(uuid.uuid4())
    async with async_session_maker() as db:
        user = User(
            id=uid,
            email=f"ws_admin_{uid[:8]}@vaisentra.com",
            phone_number=f"+9177{uid[:8]}",
            full_name="WS Admin Test",
            role="admin",
            is_active=True
        )
        session = Session(
            id=sid,
            user_id=uid,
            token_hash=hash_secret("sample_refresh_admin"),
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        db.add_all([user, session])
        await db.commit()

    token = create_jwt_token(user_id=uid, role="admin", session_id=sid, expires_delta=timedelta(hours=1))

    # Obtain ticket via POST /api/v1/ws/ticket
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as aclient:
        ticket_res = await aclient.post("/api/v1/ws/ticket", headers={"Authorization": f"Bearer {token}"})
        assert ticket_res.status_code == 200
        ticket = ticket_res.json()["ticket"]

    # Connect to admin telemetry via ?ticket=
    client = TestClient(app)
    with client.websocket_connect(f"/api/v1/ws/admin/telemetry?ticket={ticket}") as ws:
        # Connection succeeds
        ws.send_text("ping")
        resp = ws.receive_text()
        assert resp == "pong"


@pytest.mark.asyncio
async def test_ws_ticket_single_use_replay_rejected():
    """Verify that replaying an already consumed ticket is strictly rejected."""
    uid = str(uuid.uuid4())
    sid = str(uuid.uuid4())
    async with async_session_maker() as db:
        user = User(
            id=uid,
            email=f"ws_replay_{uid[:8]}@vaisentra.com",
            phone_number=f"+9166{uid[:8]}",
            full_name="WS Replay Test",
            role="admin",
            is_active=True
        )
        session = Session(
            id=sid,
            user_id=uid,
            token_hash=hash_secret("sample_refresh_replay"),
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        db.add_all([user, session])
        await db.commit()

    token = create_jwt_token(user_id=uid, role="admin", session_id=sid, expires_delta=timedelta(hours=1))

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as aclient:
        ticket_res = await aclient.post("/api/v1/ws/ticket", headers={"Authorization": f"Bearer {token}"})
        ticket = ticket_res.json()["ticket"]

    client = TestClient(app)
    # First connection consumes the ticket
    with client.websocket_connect(f"/api/v1/ws/admin/telemetry?ticket={ticket}") as ws:
        pass

    # Second connection using same ticket MUST be rejected
    with pytest.raises(WebSocketDisconnect) as exc:
        with client.websocket_connect(f"/api/v1/ws/admin/telemetry?ticket={ticket}") as ws:
            pass
    assert exc.value.code == 1008


@pytest.mark.asyncio
async def test_ws_expired_ticket_rejected():
    """Verify that nonexistent or expired ticket UUID is rejected."""
    client = TestClient(app)
    fake_ticket = str(uuid.uuid4())
    with pytest.raises(WebSocketDisconnect) as exc:
        with client.websocket_connect(f"/api/v1/ws/admin/telemetry?ticket={fake_ticket}") as ws:
            pass
    assert exc.value.code == 1008


@pytest.mark.asyncio
async def test_ws_session_revocation_enforced():
    """Verify that a driver whose session is revoked in Redis is rejected during handshake."""
    uid = str(uuid.uuid4())
    sid = str(uuid.uuid4())
    async with async_session_maker() as db:
        user = User(
            id=uid,
            email=f"ws_revoked_{uid[:8]}@vaisentra.com",
            phone_number=f"+9155{uid[:8]}",
            full_name="WS Revoked Driver",
            role="driver",
            is_active=True
        )
        profile = DriverProfile(id=uid, user_id=uid, license_number=f"DL-{uid[:8]}", approval_status="APPROVED")
        session = Session(
            id=sid,
            user_id=uid,
            token_hash=hash_secret("sample_refresh_rev"),
            is_revoked=True,
            expires_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        db.add_all([user, profile, session])
        await db.commit()

    await redis_client.set(f"revoked_session:{sid}", "true", ex=300)
    token = create_jwt_token(user_id=uid, role="driver", session_id=sid, expires_delta=timedelta(hours=1))

    client = TestClient(app)
    with pytest.raises(WebSocketDisconnect) as exc:
        with client.websocket_connect("/api/v1/ws/driver/dispatch", headers={"Authorization": f"Bearer {token}"}) as ws:
            pass
    assert exc.value.code == 1008


def test_android_and_web_clients_no_jwt_query_params():
    """Verify source files in Android and Admin web do not contain ?token= in WebSocket connections."""
    dashboard_path = "C:/androidAi/apps/driver-android/src/main/java/com/vaisentra/mobility/driver/feature/dashboard/DriverDashboardScreen.kt"
    with open(dashboard_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "api/v1/ws/driver/dispatch?token=" not in content
    assert 'addHeader("Authorization", "Bearer $token")' in content

    cust_fg_path = "C:/androidAi/apps/customer-android/src/main/java/com/vaisentra/mobility/rider/core/location/CustomerLocationForegroundService.kt"
    with open(cust_fg_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "api/v1/ws/location?token=" not in content
    assert 'addHeader("Authorization", "Bearer $token")' in content

    tracking_path = "C:/androidAi/apps/customer-android/src/main/java/com/vaisentra/mobility/rider/feature/tracking/LiveTrackingScreen.kt"
    with open(tracking_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "tracking?token=" not in content

    admin_map_path = "C:/androidAi/apps/admin-web/src/components/AdminRealMap.tsx"
    with open(admin_map_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "telemetry?token=" not in content
    assert "telemetry?ticket=" in content


# ---------------------------------------------------------------------------
# WORK PACKAGE D: REFRESH TOKEN CONCURRENCY & ATOMICITY TESTS
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_refresh_token_valid_rotation_authoritative():
    """Verify that a valid refresh token rotates atomically in PostgreSQL and updates token hash."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        phone = f"+9191{uuid.uuid4().hex[:8]}"
        reg_res = await client.post("/api/v1/auth/signup/customer", json={
            "full_name": "Rotation Authority User",
            "email": f"auth_rot_{uuid.uuid4().hex[:6]}@vaisentra.com",
            "phone_number": phone,
            "password": "ValidPassword123!"
        })
        assert reg_res.status_code == 200
        r1 = reg_res.json()["refresh_token"]

        # Rotate
        rot_res = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": r1})
        assert rot_res.status_code == 200
        r2 = rot_res.json()["refresh_token"]
        assert r2 != r1


@pytest.mark.asyncio
async def test_refresh_token_replay_revokes_session_family():
    """Verify that reusing an old refresh token after grace period revokes the session family in PostgreSQL and Redis."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        phone = f"+9192{uuid.uuid4().hex[:8]}"
        reg_res = await client.post("/api/v1/auth/signup/customer", json={
            "full_name": "Replay Revocation User",
            "email": f"replay_{uuid.uuid4().hex[:6]}@vaisentra.com",
            "phone_number": phone,
            "password": "ValidPassword123!"
        })
        r1 = reg_res.json()["refresh_token"]

        # 1st rotation
        rot_res1 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": r1})
        assert rot_res1.status_code == 200
        r2 = rot_res1.json()["refresh_token"]

        # Duplicate within grace period -> 409 Conflict (non-destructive)
        dup_res = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": r1})
        assert dup_res.status_code == 409
        assert "concurrency grace period active" in dup_res.json()["detail"].lower()

        # Age the rotation timestamp beyond the grace period to simulate stale token replay
        async with async_session_maker() as db:
            await db.execute(
                update(Session)
                .values(rotated_at=datetime.now(timezone.utc) - timedelta(seconds=20))
            )
            await db.commit()

        # Replay r1 after grace period -> must fail with 401 and revoke session
        replay_res = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": r1})
        assert replay_res.status_code == 401
        assert "reuse detected" in replay_res.json()["detail"].lower()

        # New token r2 must now ALSO be rejected because the session family was revoked
        rot_res2 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": r2})
        assert rot_res2.status_code == 401


@pytest.mark.asyncio
async def test_concurrent_refresh_requests_atomicity():
    """Verify that two concurrent refresh requests using the exact same token: winner gets 200, loser gets 409, winner's token preserved."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        uid = str(uuid.uuid4())
        sid = str(uuid.uuid4())
        t0 = create_jwt_token(user_id=uid, role="customer", session_id=sid, token_type="refresh", expires_delta=timedelta(days=7))
        async with async_session_maker() as db:
            user = User(
                id=uid,
                email=f"concur_{uid[:8]}@vaisentra.com",
                phone_number=f"+9193{uid[:8]}",
                full_name="Concurrency Test User",
                role="customer",
                is_active=True
            )
            session = Session(
                id=sid,
                user_id=uid,
                token_hash=hash_secret(t0),
                is_revoked=False,
                expires_at=datetime.now(timezone.utc) + timedelta(days=7)
            )
            db.add_all([user, session])
            await db.commit()

        # Issue 2 simultaneous requests with the SAME token t0
        req1 = client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})
        req2 = client.post("/api/v1/auth/token/refresh", json={"refresh_token": t0})

        res1, res2 = await asyncio.gather(req1, req2)
        statuses = [res1.status_code, res2.status_code]

        # Exactly one must succeed (200), and the losing duplicate receives 409 (grace period active)
        assert 200 in statuses, f"Expected 200 in {statuses}"
        assert 409 in statuses, f"Expected 409 in {statuses}"
        assert not (res1.status_code == 200 and res2.status_code == 200), "FATAL: Both concurrent refresh requests succeeded!"


@pytest.mark.asyncio
async def test_refresh_token_redis_down_rotation_succeeds_and_replay_prevented():
    """Verify that when Redis is down, PostgreSQL remains authoritative: rotation succeeds and replay is rejected."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        phone = f"+9194{uuid.uuid4().hex[:8]}"
        reg_res = await client.post("/api/v1/auth/signup/customer", json={
            "full_name": "Redis Down User",
            "email": f"redis_down_{uuid.uuid4().hex[:6]}@vaisentra.com",
            "phone_number": phone,
            "password": "ValidPassword123!"
        })
        r1 = reg_res.json()["refresh_token"]

        # Simulate Redis failure by patching redis_client methods to raise ConnectionError
        with patch.object(redis_client, "exists", side_effect=Exception("Redis connection refused")):
            with patch.object(redis_client, "set", side_effect=Exception("Redis connection refused")):
                # 1. Rotation must still succeed via PostgreSQL authority
                rot_res = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": r1})
                assert rot_res.status_code == 200
                r2 = rot_res.json()["refresh_token"]
                assert r2 != r1

                # 2. Duplicate within grace period while Redis is down returns 409 and does not revoke
                dup_res = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": r1})
                assert dup_res.status_code == 409

                # Age rotation timestamp beyond grace period
                async with async_session_maker() as db:
                    await db.execute(
                        update(Session)
                        .values(rotated_at=datetime.now(timezone.utc) - timedelta(seconds=20))
                    )
                    await db.commit()

                # 3. Replay of r1 after grace period while Redis is STILL down must be caught by PostgreSQL and rejected!
                replay_res = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": r1})
                assert replay_res.status_code == 401
                assert "reuse detected" in replay_res.json()["detail"].lower()


@pytest.mark.asyncio
async def test_refresh_token_db_commit_failure_rolls_back():
    """Verify that if PostgreSQL commit fails, the rotation rolls back and no valid rotation is reported."""
    async with async_session_maker() as db:
        service = AuthService(db)
        uid = str(uuid.uuid4())
        sid = str(uuid.uuid4())
        user = User(id=uid, email=f"commit_fail_{uid[:8]}@vaisentra.com", phone_number=f"+9195{uid[:8]}", role="customer", is_active=True)
        session = Session(
            id=sid,
            user_id=uid,
            token_hash=hash_secret("token_to_rotate"),
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        db.add_all([user, session])
        await db.commit()

        token = create_jwt_token(user_id=uid, role="customer", session_id=sid, token_type="refresh", expires_delta=timedelta(days=7))

        with patch.object(db, "commit", side_effect=Exception("Simulated PostgreSQL commit crash")):
            with pytest.raises(Exception):
                await service.refresh_token_rotation(token)

        # Verify session token_hash was NOT changed in DB
        check_sess = (await db.execute(select(Session).where(Session.id == sid))).scalar_one()
        assert check_sess.token_hash == hash_secret("token_to_rotate")


@pytest.mark.asyncio
async def test_refresh_token_expired_rejected():
    """Verify that an expired refresh token is rejected."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # Create token with -1 hour expiry
        expired_token = create_jwt_token(
            user_id=str(uuid.uuid4()),
            role="customer",
            session_id=str(uuid.uuid4()),
            token_type="refresh",
            expires_delta=timedelta(hours=-1)
        )
        rot_res = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": expired_token})
        assert rot_res.status_code == 401


@pytest.mark.asyncio
async def test_refresh_token_revoked_session_rejected():
    """Verify that an explicitly revoked session cannot be rotated."""
    uid = str(uuid.uuid4())
    sid = str(uuid.uuid4())
    async with async_session_maker() as db:
        user = User(id=uid, email=f"rev_{uid[:8]}@vaisentra.com", phone_number=f"+9196{uid[:8]}", role="customer", is_active=True)
        session = Session(
            id=sid,
            user_id=uid,
            token_hash=hash_secret("revoked_refresh"),
            is_revoked=True,
            expires_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        db.add_all([user, session])
        await db.commit()

    token = create_jwt_token(user_id=uid, role="customer", session_id=sid, token_type="refresh", expires_delta=timedelta(days=7))
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        rot_res = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": token})
        assert rot_res.status_code == 401
        assert "revoked" in rot_res.json()["detail"].lower()


# ---------------------------------------------------------------------------
# WORK PACKAGE D: BOUNDED PROGRESSIVE ACCOUNT LOCKOUT TESTS
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_progressive_lockout_cycles_and_duration_escalation():
    """Verify that 5 failed logins trigger 15m lockout, and subsequent cycles escalate duration."""
    ident = f"+9197{uuid.uuid4().hex[:8]}"
    keys = get_lockout_keys(ident)

    # Clean test keys
    await reset_account_lockout(ident)

    # 1. 4 failures -> no lockout
    for _ in range(4):
        await record_failed_login(ident)
    assert not await redis_client.exists(keys["lockout_key"])

    # 2. 5th failure -> triggers Cycle 1: 900 seconds (15 minutes)
    with pytest.raises(Exception) as exc:
        await record_failed_login(ident)
    assert exc.value.status_code == 429
    assert exc.value.headers.get("Retry-After") == "900"
    assert await redis_client.exists(keys["lockout_key"])

    # 3. Simulate cycle 1 expiration: delete lockout key, but tier key remains (tier = 1)
    await redis_client.delete(keys["lockout_key"])

    # 4. Next 5 failures -> triggers Cycle 2: 1800 seconds (30 minutes)
    for _ in range(4):
        await record_failed_login(ident)
    with pytest.raises(Exception) as exc:
        await record_failed_login(ident)
    assert exc.value.status_code == 429
    assert exc.value.headers.get("Retry-After") == "1800"

    # 5. Simulate cycle 2 expiration: delete lockout key (tier = 2)
    await redis_client.delete(keys["lockout_key"])

    # 6. Next 5 failures -> triggers Cycle 3: 3600 seconds (60 minutes)
    for _ in range(4):
        await record_failed_login(ident)
    with pytest.raises(Exception) as exc:
        await record_failed_login(ident)
    assert exc.value.status_code == 429
    assert exc.value.headers.get("Retry-After") == "3600"

    # 7. Clean up
    await reset_account_lockout(ident)


@pytest.mark.asyncio
async def test_progressive_lockout_maximum_bound():
    """Verify that progressive lockout cannot exceed the maximum configured cap (86400s / 24h)."""
    ident = f"+9198{uuid.uuid4().hex[:8]}"
    keys = get_lockout_keys(ident)
    await reset_account_lockout(ident)

    # Set tier artificially high to 10
    await redis_client.set(keys["tier_key"], "10")

    # Trigger threshold
    for _ in range(4):
        await record_failed_login(ident)
    with pytest.raises(Exception) as exc:
        await record_failed_login(ident)

    assert exc.value.status_code == 429
    # Must be bounded at MAX_LOCKOUT_SECONDS (86400s)
    assert exc.value.headers.get("Retry-After") == str(MAX_LOCKOUT_SECONDS)
    await reset_account_lockout(ident)


@pytest.mark.asyncio
async def test_successful_login_resets_lockout_tier_and_attempts():
    """Verify that successful login completely clears attempts, lockout, and tier."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        phone = f"+9199{uuid.uuid4().hex[:8]}"
        pwd = "CorrectSecretPass123!"
        await client.post("/api/v1/auth/signup/customer", json={
            "full_name": "Reset Test User",
            "email": f"reset_{uuid.uuid4().hex[:6]}@vaisentra.com",
            "phone_number": phone,
            "password": pwd
        })

        # 3 failed attempts
        for _ in range(3):
            await client.post("/api/v1/auth/login", json={"login_type": "PASSWORD", "identifier": phone, "password": "BadPassword"})

        keys = get_lockout_keys(phone)
        assert await redis_client.exists(keys["attempts_key"])

        # Successful login
        ok_res = await client.post("/api/v1/auth/login", json={"login_type": "PASSWORD", "identifier": phone, "password": pwd})
        assert ok_res.status_code == 200

        # All lockout state cleared!
        assert not await redis_client.exists(keys["attempts_key"])
        assert not await redis_client.exists(keys["lockout_key"])
        assert not await redis_client.exists(keys["tier_key"])


@pytest.mark.asyncio
async def test_unknown_account_consistent_lockout():
    """Verify that unknown accounts return 401 without revealing existence, and still trigger 429 after 5 failures."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        non_existent_phone = f"+9100{uuid.uuid4().hex[:8]}"

        for _ in range(4):
            res = await client.post("/api/v1/auth/login", json={
                "login_type": "PASSWORD",
                "identifier": non_existent_phone,
                "password": "RandomPassword"
            })
            assert res.status_code == 401
            assert "invalid" in res.json()["detail"].lower()

        # 5th attempt triggers 429 lockout
        lockout_res = await client.post("/api/v1/auth/login", json={
            "login_type": "PASSWORD",
            "identifier": non_existent_phone,
            "password": "RandomPassword"
        })
        assert lockout_res.status_code == 429
        assert lockout_res.headers.get("retry-after") == "900"


@pytest.mark.asyncio
async def test_redis_failure_lockout_safe_fallback():
    """Verify that when Redis is unreachable, login proceeds against PostgreSQL authority without crashing."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        phone = f"+9187{uuid.uuid4().hex[:8]}"
        pwd = "SafeFallbackPass123!"
        await client.post("/api/v1/auth/signup/customer", json={
            "full_name": "Fallback User",
            "email": f"fallback_{uuid.uuid4().hex[:6]}@vaisentra.com",
            "phone_number": phone,
            "password": pwd
        })

        with patch.object(redis_client, "exists", side_effect=Exception("Redis connection refused")):
            with patch.object(redis_client, "ttl", side_effect=Exception("Redis connection refused")):
                with patch.object(redis_client, "delete", side_effect=Exception("Redis connection refused")):
                    # Login still succeeds!
                    res = await client.post("/api/v1/auth/login", json={
                        "login_type": "PASSWORD",
                        "identifier": phone,
                        "password": pwd
                    })
                    assert res.status_code == 200
