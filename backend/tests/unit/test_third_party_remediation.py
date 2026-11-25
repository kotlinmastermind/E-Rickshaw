import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from fastapi import HTTPException

from backend.infrastructure.cache.redis_client import _redact_redis_url, RedisManager
from backend.integrations.maps.provider import MapboxProvider
from backend.integrations.sms.provider import TwilioVerifyProvider, get_sms_provider
import backend.integrations.sms.provider as sms_mod


def test_redact_redis_url_masks_credentials():
    assert _redact_redis_url("redis://:supersecret@redis.example.com:6379/0") == "redis://***@redis.example.com:6379/0"
    assert _redact_redis_url("rediss://default:token123@upstash.io:6380") == "rediss://default:***@upstash.io:6380"
    assert _redact_redis_url("redis://localhost:6379/0") == "redis://localhost:6379/0"
    assert _redact_redis_url(None) == "<unset>"


@pytest.mark.asyncio
async def test_redis_acquire_lock_fails_closed_in_production():
    client = RedisManager()
    with patch.object(client, "_get_redis", new=AsyncMock(return_value=None)), \
         patch("backend.infrastructure.cache.redis_client.settings") as mock_settings:
        mock_settings.ENVIRONMENT = "production"
        acquired = await client.acquire_lock("ride:lock:1", "tok-1", timeout_seconds=5)
        assert acquired is False


@pytest.mark.asyncio
async def test_mapbox_validate_address_unconfigured_returns_unverified():
    provider = MapboxProvider(access_token="")
    res = await provider.validate_address("MG Road", "Bengaluru", "560001")
    assert res["valid"] is False
    assert res["verdict"] == "UNVERIFIED"


@pytest.mark.asyncio
async def test_twilio_error_does_not_leak_provider_message():
    provider = TwilioVerifyProvider(account_sid="AC123", auth_token="tok", verify_service_sid="VA123")
    mock_resp = MagicMock()
    mock_resp.status_code = 400
    mock_resp.json.return_value = {"code": 99999, "message": "Internal Twilio secret detail"}
    mock_resp.text = "Internal Twilio secret detail"

    with patch("httpx.AsyncClient.post", new=AsyncMock(return_value=mock_resp)):
        with pytest.raises(HTTPException) as exc_info:
            await provider.send_verification_otp("+919876543210")
        assert "Internal Twilio secret detail" not in str(exc_info.value.detail)
        assert exc_info.value.status_code == 400


def test_sms_provider_factory_rejects_unsupported_provider():
    sms_mod._sms_provider_instance = None
    with patch.object(sms_mod.settings, "SMS_PROVIDER", "unsupported_sms"):
        with pytest.raises(ValueError, match="Unsupported SMS_PROVIDER"):
            get_sms_provider()
    sms_mod._sms_provider_instance = None
