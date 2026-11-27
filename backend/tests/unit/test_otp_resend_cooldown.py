import asyncio
from unittest.mock import AsyncMock

import pytest
from fastapi import HTTPException

from backend.application.services import auth_service
from backend.application.services.auth_service import AuthService
from backend.infrastructure.cache.redis_client import RedisManager


@pytest.mark.asyncio
async def test_strict_nx_reservation_is_atomic_with_local_test_store(monkeypatch):
    monkeypatch.setenv("TESTING", "1")
    manager = RedisManager()
    results = await asyncio.gather(
        *(manager.set_nx_strict("otp_cooldown:test", "1", ex=60) for _ in range(20))
    )
    assert sum(results) == 1


@pytest.mark.asyncio
async def test_otp_request_rejects_duplicate_reservation(monkeypatch):
    monkeypatch.setattr(auth_service.redis_client, "set_nx_strict", AsyncMock(return_value=False))
    provider = AsyncMock()
    monkeypatch.setattr(auth_service, "get_sms_provider", lambda: provider)

    with pytest.raises(HTTPException) as exc:
        await AuthService(db=None).request_otp("+919876543210")

    assert exc.value.status_code == 429
    provider.send_verification_otp.assert_not_awaited()


@pytest.mark.asyncio
async def test_otp_request_fails_closed_when_redis_is_unavailable(monkeypatch):
    monkeypatch.setattr(
        auth_service.redis_client,
        "set_nx_strict",
        AsyncMock(side_effect=ConnectionError("redis unavailable")),
    )
    provider = AsyncMock()
    monkeypatch.setattr(auth_service, "get_sms_provider", lambda: provider)

    with pytest.raises(HTTPException) as exc:
        await AuthService(db=None).request_otp("+919876543210")

    assert exc.value.status_code == 503
    provider.send_verification_otp.assert_not_awaited()


@pytest.mark.asyncio
async def test_otp_provider_failure_releases_reserved_cooldown(monkeypatch):
    monkeypatch.setattr(auth_service.redis_client, "set_nx_strict", AsyncMock(return_value=True))
    release = AsyncMock(return_value=True)
    monkeypatch.setattr(auth_service.redis_client, "delete_strict", release)
    provider = AsyncMock()
    provider.send_verification_otp.side_effect = RuntimeError("provider unavailable")
    monkeypatch.setattr(auth_service, "get_sms_provider", lambda: provider)

    with pytest.raises(RuntimeError, match="provider unavailable"):
        await AuthService(db=None).request_otp("+919876543210")

    release.assert_awaited_once_with("otp_cooldown:+919876543210")
