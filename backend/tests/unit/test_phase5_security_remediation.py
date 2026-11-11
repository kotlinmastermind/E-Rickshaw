import pytest
import hmac
import hashlib
import uuid
from datetime import datetime, timezone, timedelta
from httpx import AsyncClient, ASGITransport
from backend.main import app
from backend.domain.models import User, Session
from backend.application.services.user_governance_service import UserGovernanceService
from backend.infrastructure.cache.redis_client import redis_client
from backend.integrations.payments.provider import get_payment_provider
from backend.core.config import settings
from backend.infrastructure.database.session import async_session_maker

@pytest.mark.asyncio
async def test_passkey_login_challenge_fails_closed():
    """Verify that passkey challenge endpoint fails closed with HTTP 503."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.get("/api/v1/auth/passkey/login-challenge")
        assert resp.status_code == 503
        data = resp.json()
        assert "temporarily disabled" in data["detail"].lower()

@pytest.mark.asyncio
async def test_passkey_login_verify_fails_closed():
    """Verify that passkey verify endpoint fails closed with HTTP 503 and never issues tokens."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/api/v1/auth/passkey/login-verify", json={
            "credential_id": "test_cred_12345",
            "signature": "test_signature_hex"
        })
        assert resp.status_code == 503
        data = resp.json()
        assert "temporarily disabled" in data["detail"].lower()
        assert "access_token" not in data

@pytest.mark.asyncio
async def test_passkey_login_verify_missing_fields_rejected():
    """Verify that missing credential_id or signature is rejected with HTTP 400."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # Missing both
        resp1 = await client.post("/api/v1/auth/passkey/login-verify", json={})
        assert resp1.status_code == 400
        assert "credential id is required" in resp1.json()["detail"].lower()

        # Missing signature
        resp2 = await client.post("/api/v1/auth/passkey/login-verify", json={"credential_id": "cred_abc"})
        assert resp2.status_code == 400
        assert "signature is required" in resp2.json()["detail"].lower()

@pytest.mark.asyncio
async def test_passkey_registration_fails_closed(db_session):
    """Verify that passkey registration method fails closed with HTTP 503."""
    service = UserGovernanceService(db_session)
    with pytest.raises(Exception) as exc_info:
        await service.register_passkey("user_123", "cred_123", "pubkey_123")
    assert getattr(exc_info.value, "status_code", None) == 503
    assert "temporarily disabled" in str(exc_info.value.detail).lower()

def test_no_hardcoded_live_credentials_in_active_source():
    """Scan source code files to verify compromised credentials are not present."""
    import glob
    forbidden_tokens = [
        "rzp_live_TunGmzfpWlcatO",
        "CSIhchRsNTdHx270VZthHSUE"
    ]
    files_to_check = (
        glob.glob("backend/api/**/*.py", recursive=True) +
        glob.glob("backend/integrations/**/*.py", recursive=True) +
        glob.glob("backend/application/**/*.py", recursive=True) +
        glob.glob("backend/core/**/*.py", recursive=True) +
        glob.glob("apps/customer-android/src/main/java/**/*.kt", recursive=True) +
        glob.glob("apps/driver-android/src/main/java/**/*.kt", recursive=True)
    )
    for fpath in files_to_check:
        with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
            content = f.read()
            for token in forbidden_tokens:
                assert token not in content, f"Hardcoded credential '{token}' found in {fpath}"

@pytest.mark.asyncio
async def test_razorpay_config_endpoint_safe():
    """Verify that /razorpay/config returns safe brand and empty default key without hardcoded live secret."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.get("/api/v1/payments/razorpay/config")
        assert resp.status_code == 200
        data = resp.json()
        assert data["company_name"] == "VAISENTRA MOBILITY"
        assert "rzp_live_TunGmzfpWlcatO" not in data.get("key_id", "")

def test_payment_signature_verification_rejects_missing_secret():
    """Verify that payment signature verification fails safely when key_secret is blank."""
    provider = get_payment_provider()
    # Save original
    orig_secret = provider.key_secret
    provider.key_secret = ""
    try:
        assert provider.verify_payment_signature("order_123", "pay_123", "fake_sig") is False
    finally:
        provider.key_secret = orig_secret

def test_websocket_canonical_route_registration():
    """Verify that the registered WebSocket route is /api/v1/ws/driver/dispatch."""
    paths = set()
    for r in app.routes:
        if hasattr(r, "path"):
            paths.add(r.path)
        elif type(r).__name__ == "_IncludedRouter":
            prefix = r.include_context.prefix
            for sub in r.original_router.routes:
                sub_path = getattr(sub, "path", "")
                paths.add(f"{prefix}{sub_path}")

    assert "/api/v1/ws/driver/dispatch" in paths
    assert "/ws/driver/{driver_id}" not in paths

@pytest.mark.asyncio
async def test_refresh_token_rotation_and_reuse_revocation():
    """Verify single-use refresh token rotation and session revocation upon reuse."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. Register customer
        test_uid = uuid.uuid4().hex[:8]
        phone = f"+9198{uuid.uuid4().int % 100000000:08d}"
        email = f"p5_rot_{test_uid}@vaisentra.com"
        reg_resp = await client.post("/api/v1/auth/signup/customer", json={
            "full_name": "Phase 5 Rotation Test User",
            "email": email,
            "phone_number": phone,
            "password": "Password123!"
        })
        assert reg_resp.status_code == 200, reg_resp.text
        tokens_1 = reg_resp.json()
        r1 = tokens_1["refresh_token"]

        # 2. First rotation (valid)
        rot_resp1 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": r1})
        assert rot_resp1.status_code == 200
        tokens_2 = rot_resp1.json()
        r2 = tokens_2["refresh_token"]
        assert r2 != r1

        # Duplicate within grace period returns 409 (non-destructive)
        dup_resp = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": r1})
        assert dup_resp.status_code == 409

        # Age rotation timestamp beyond grace period to simulate stale token replay
        async with async_session_maker() as db:
            from sqlalchemy import update
            from backend.domain.models import Session
            await db.execute(
                update(Session)
                .values(rotated_at=datetime.now(timezone.utc) - timedelta(seconds=20))
            )
            await db.commit()

        # 3. Replay attack after grace period: attempt to use r1 again -> must fail with 401
        rot_resp2 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": r1})
        assert rot_resp2.status_code == 401
        assert "reuse detected" in rot_resp2.json()["detail"].lower()

        # 4. Confirm that the entire session was revoked: even r2 is now invalid
        rot_resp3 = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": r2})
        assert rot_resp3.status_code == 401

@pytest.mark.asyncio
async def test_progressive_account_lockout():
    """Verify that 5 failed password attempts trigger a 15-minute lockout (HTTP 429)."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # Register a test user
        test_uid = uuid.uuid4().hex[:8]
        phone = f"+9198{uuid.uuid4().int % 100000000:08d}"
        email = f"p5_lockout_{test_uid}@vaisentra.com"
        reg_resp = await client.post("/api/v1/auth/signup/customer", json={
            "full_name": "Lockout Test User",
            "email": email,
            "phone_number": phone,
            "password": "CorrectPassword123!"
        })
        assert reg_resp.status_code == 200

        # Attempt 4 failed logins -> each returns 401
        for i in range(4):
            fail_resp = await client.post("/api/v1/auth/login", json={
                "login_type": "PASSWORD",
                "identifier": phone,
                "password": f"WrongPassword_{i}"
            })
            assert fail_resp.status_code == 401

        # 5th failed login triggers lockout (HTTP 429)
        lockout_resp = await client.post("/api/v1/auth/login", json={
            "login_type": "PASSWORD",
            "identifier": phone,
            "password": "WrongPassword_5"
        })
        assert lockout_resp.status_code == 429
        assert "temporarily locked" in lockout_resp.json()["detail"].lower()
        assert lockout_resp.headers.get("retry-after") == "900"

        # 6th attempt with CORRECT password is still locked out
        blocked_resp = await client.post("/api/v1/auth/login", json={
            "login_type": "PASSWORD",
            "identifier": phone,
            "password": "CorrectPassword123!"
        })
        assert blocked_resp.status_code == 429


@pytest.mark.asyncio
async def test_payment_webhook_invalid_signature_rejected():
    """Verify that webhook endpoint strictly rejects requests with invalid or forged HMAC signatures."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {"event": "payment.captured", "payload": {"payment": {"entity": {"id": "pay_fake123", "amount": 50000}}}}
        resp = await client.post(
            "/api/v1/payments/webhook",
            json=payload,
            headers={"X-Razorpay-Signature": "forged_invalid_signature_hex"}
        )
        assert resp.status_code == 400
        assert "invalid webhook signature" in resp.json()["detail"].lower()


@pytest.mark.asyncio
async def test_payment_webhook_hmac_verification_and_rejection():
    """Verify that payment provider verifies valid HMAC-SHA256 signatures and rejects forged/missing ones."""
    provider = get_payment_provider()
    orig_secret = provider.webhook_secret
    provider.webhook_secret = "test_webhook_secret_key_12345"
    try:
        raw_body = b'{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_123"}}}}'
        valid_sig = hmac.new(b"test_webhook_secret_key_12345", raw_body, hashlib.sha256).hexdigest()

        # 1. Valid signature succeeds
        res_valid = await provider.verify_webhook_payload(raw_body, valid_sig)
        assert res_valid.is_valid is True
        assert res_valid.status == "CAPTURED"
        assert res_valid.payment_id == "pay_123"

        # 2. Forged signature fails
        res_invalid = await provider.verify_webhook_payload(raw_body, "forged_sig_abc123")
        assert res_invalid.is_valid is False
        assert res_invalid.status == "FAILED"

        # 3. Missing signature fails
        res_missing = await provider.verify_webhook_payload(raw_body, "")
        assert res_missing.is_valid is False
        assert res_missing.status == "FAILED"
    finally:
        provider.webhook_secret = orig_secret


@pytest.mark.asyncio
async def test_payment_webhook_duplicate_idempotency_acknowledged():
    """Verify that a payment already in CAPTURED status returns ALREADY_PROCESSED gracefully."""
    from unittest.mock import AsyncMock, MagicMock
    from backend.application.services.payment_service import PaymentService
    from backend.domain.models import Payment

    mock_db = AsyncMock()
    # In SQLAlchemy, AsyncSession.add() is strictly synchronous
    mock_db.add = MagicMock()
    mock_payment = MagicMock(spec=Payment)
    mock_payment.id = "pay_rec_123"
    mock_payment.status = "CAPTURED"

    # Mock execute to return mock_payment
    mock_result = MagicMock()
    mock_result.scalar_one_or_none.return_value = mock_payment
    mock_db.execute.return_value = mock_result

    service = PaymentService(mock_db)
    service.provider = MagicMock()
    service.provider.verify_webhook_payload = AsyncMock(
        return_value=MagicMock(is_valid=True, payment_id="pay_razor_123")
    )

    body = b'{"event":"payment.captured","order_id":"order_dup_123","payload":{"payment":{"entity":{"id":"pay_razor_123"}}}}'
    res = await service.process_webhook(body, "valid_sig")
    assert res["status"] == "ALREADY_PROCESSED"
    assert res["payment_id"] == "pay_rec_123"


@pytest.mark.asyncio
async def test_admin_demo_session_endpoint_deleted_returns_404():
    """Verify that unauthenticated admin demo backdoor does not exist and returns 404/405."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/api/v1/auth/admin/demo-session", json={})
        assert resp.status_code in (404, 405)


@pytest.mark.asyncio
async def test_anonymous_and_customer_cannot_access_admin_endpoints():
    """Verify RBAC: Anonymous or non-admin cannot access administrative endpoints."""
    from backend.infrastructure.security.crypto import create_jwt_token
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. Anonymous request rejected
        resp_anon = await client.get("/api/v1/admin/dashboard/stats")
        assert resp_anon.status_code in (401, 403)

        # 2. Customer token rejected from administrative endpoints
        customer_token = create_jwt_token(
            user_id="cust-user-1",
            role="customer",
            session_id="sess-cust-123",
            expires_delta=timedelta(minutes=15)
        )
        resp_cust = await client.get(
            "/api/v1/admin/dashboard/stats",
            headers={"Authorization": f"Bearer {customer_token}"}
        )
        assert resp_cust.status_code in (401, 403)


