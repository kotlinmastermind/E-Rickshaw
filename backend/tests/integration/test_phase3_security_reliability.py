import pytest
import httpx
from httpx import ASGITransport
from datetime import datetime, timezone
from backend.main import app
from backend.integrations.sms.provider import get_sms_provider
from backend.infrastructure.cache.redis_client import redis_client
from backend.infrastructure.security.encryption import (
    encrypt_sensitive_field, decrypt_sensitive_field,
    mask_phone_number, mask_email, mask_financial_identifier,
    redact_sensitive_dict
)
from backend.infrastructure.telemetry.crash_reporter import crash_reporter
from backend.application.services.backup_service import DatabaseBackupService

@pytest.mark.asyncio
async def test_data_encryption_and_pii_redaction():
    # 1. AES-256-GCM encryption at rest
    secret_note = "VIP Passenger Private Note: Prefers French sparkling water"
    ciphertext = encrypt_sensitive_field(secret_note)
    assert ciphertext != secret_note
    decrypted = decrypt_sensitive_field(ciphertext)
    assert decrypted == secret_note

    # 2. PII masking
    masked_phone = mask_phone_number("+919876543210")
    assert masked_phone.startswith("+91")
    assert masked_phone.endswith("3210")
    assert "*" in masked_phone

    masked_mail = mask_email("executive.guest@luxurychauffeur.com")
    assert "@luxurychauffeur.com" in masked_mail
    assert "*" in masked_mail

    masked_bank = mask_financial_identifier("HDFC000123456789")
    assert masked_bank.endswith("6789")
    assert masked_bank.startswith("************")

    # 3. Payload redaction
    payload = {
        "password": "SuperSecretPassword123!",
        "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9",
        "phone_number": "+919876543210",
        "user_profile": {
            "otp_code": "123456",
            "full_name": "Lord Sterling"
        }
    }
    redacted = redact_sensitive_dict(payload)
    assert redacted["password"] == "[REDACTED]"
    assert redacted["token"] == "[REDACTED]"
    assert redacted["user_profile"]["otp_code"] == "[REDACTED]"
    assert redacted["user_profile"]["full_name"] == "Lord Sterling"

@pytest.mark.asyncio
async def test_jwt_refresh_rotation_and_revocation():
    transport = ASGITransport(app=app)
    sms_mock = get_sms_provider()

    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        # Authenticate user
        phone = "+919876543777"
        await client.post("/api/v1/auth/otp/request", json={"phone_number": phone})
        otp = sms_mock.get_latest_otp(phone)
        auth_resp = await client.post("/api/v1/auth/otp/verify", json={
            "phone_number": phone,
            "otp_code": otp,
            "role": "customer"
        })
        assert auth_resp.status_code == 200
        auth_data = auth_resp.json()
        assert "access_token" in auth_data
        assert "refresh_token" in auth_data

        access_token = auth_data["access_token"]
        refresh_token = auth_data["refresh_token"]

        # Call /me with access token
        me_resp = await client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {access_token}"})
        assert me_resp.status_code == 200

        # Rotate token
        refresh_resp = await client.post("/api/v1/auth/token/refresh", json={"refresh_token": refresh_token})
        assert refresh_resp.status_code == 200
        refreshed_data = refresh_resp.json()
        assert "access_token" in refreshed_data
        assert "refresh_token" in refreshed_data
        new_access = refreshed_data["access_token"]

        # Test single session logout (revocation)
        logout_resp = await client.post("/api/v1/auth/logout", headers={"Authorization": f"Bearer {new_access}"})
        assert logout_resp.status_code == 200

        # Subsequent request with revoked token must fail with 401
        revoked_check = await client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {new_access}"})
        assert revoked_check.status_code == 401

@pytest.mark.asyncio
async def test_rate_limiter_enforcement():
    from backend.infrastructure.security.rate_limiter import RateLimiter
    from fastapi import Request

    limiter = RateLimiter(max_requests=3, window_seconds=60, scope="test_limit")

    class DummyClient:
        host = "10.0.0.99"

    class DummyRequest:
        client = DummyClient()
        state = type("State", (), {"rate_limit_headers": {}})()

    req = DummyRequest()
    # Requests 1, 2, 3 must pass
    assert await limiter(req) is True
    assert await limiter(req) is True
    assert await limiter(req) is True

    # 4th request must be rejected with 429
    from fastapi import HTTPException
    with pytest.raises(HTTPException) as exc:
        await limiter(req)
    assert exc.value.status_code == 429
    assert "Retry-After" in exc.value.headers
    assert exc.value.headers["X-RateLimit-Remaining"] == "0"

@pytest.mark.asyncio
async def test_redis_cache_and_in_memory_fallback():
    # Verify set and get
    await redis_client.set("luxury:test:key", "sovereign_value", ex=60)
    val = await redis_client.get("luxury:test:key")
    assert val == "sovereign_value"

    # Verify atomic incr
    counter = await redis_client.incr("luxury:test:counter", ex=60)
    assert counter >= 1

    # Verify health check
    health = await redis_client.health_check()
    assert health["status"] in ["HEALTHY", "DEGRADED_FALLBACK_ACTIVE"]
    assert "latency_ms" in health

@pytest.mark.asyncio
async def test_telemetry_crash_reporting_and_test_crash_endpoint():
    transport = ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/health/test-crash")
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] == "CRASH_CAPTURED"
        assert "event_id" in data

        # Check metrics endpoint
        metrics_resp = await client.get("/health/metrics")
        assert metrics_resp.status_code == 200
        metrics = metrics_resp.json()
        assert "uptime_seconds" in metrics
        assert "database" in metrics
        assert "redis_cache" in metrics
        assert "websockets" in metrics
        assert metrics["telemetry"]["recent_crashes_count"] >= 1

@pytest.mark.asyncio
async def test_database_backup_and_isolated_restore_verification():
    service = DatabaseBackupService()
    snapshot = service.generate_snapshot(trigger_source="AUTOMATED_TEST")

    assert snapshot["filename"].startswith("luxury_db_snapshot_")
    assert snapshot["size_bytes"] > 0
    assert len(snapshot["sha256_checksum"]) == 64
    assert snapshot["test_restore_passed"] is True
    assert snapshot["validation_report"]["sandbox_test_status"] == "PASSED"

    # List backups
    backups = service.list_backups()
    assert len(backups) >= 1
    assert backups[0]["status"] == "VERIFIED_VALID"

@pytest.mark.asyncio
async def test_admin_reports_and_audit_logs():
    transport = ASGITransport(app=app)
    sms_mock = get_sms_provider()

    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        # Authenticate Admin
        admin_phone = "+919999900000"
        await client.post("/api/v1/auth/otp/request", json={"phone_number": admin_phone})
        admin_otp = sms_mock.get_latest_otp(admin_phone)
        admin_auth = await client.post("/api/v1/auth/otp/verify", json={
            "phone_number": admin_phone,
            "otp_code": admin_otp,
            "role": "admin"
        })
        assert admin_auth.status_code == 200
        admin_headers = {"Authorization": f"Bearer {admin_auth.json()['access_token']}"}

        # 1. Admin Reports endpoint
        reports_resp = await client.get("/api/v1/admin/reports", headers=admin_headers)
        assert reports_resp.status_code == 200
        reports = reports_resp.json()
        assert "volume_metrics" in reports
        assert "financial_metrics" in reports
        assert "shared_ride_metrics" in reports
        assert reports["financial_metrics"]["conservation_verified"] is True

        # 2. Admin Audit Logs endpoint
        audit_resp = await client.get("/api/v1/admin/audit-logs", headers=admin_headers)
        assert audit_resp.status_code == 200
        audit_data = audit_resp.json()
        assert "total_count" in audit_data
        assert "logs" in audit_data

        # 3. Admin Backups API
        backups_resp = await client.get("/api/v1/admin/backups", headers=admin_headers)
        assert backups_resp.status_code == 200
        assert len(backups_resp.json()) >= 1

        trigger_resp = await client.post("/api/v1/admin/backups/trigger", headers=admin_headers)
        assert trigger_resp.status_code == 200
        assert trigger_resp.json()["test_restore_passed"] is True
