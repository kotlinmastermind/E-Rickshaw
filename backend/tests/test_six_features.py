import pytest
from httpx import AsyncClient, ASGITransport
from backend.main import app

# 1. Test Public Ride Tracking HTML Page
@pytest.mark.asyncio
async def test_public_tracking_view():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        res = await client.get("/track/share/invalid-token-test")
        assert res.status_code == 404
        assert "Trip Not Found" in res.text

# 2. Test Audio Playback Security Validation (Requires Reason)
@pytest.mark.asyncio
async def test_audio_playback_validation():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # Bina authentication ya galat reason ke access reject hona chahiye (401 ya 400)
        res = await client.post("/api/v1/admin/audio-recordings/test-sess/playback", json={
            "segment_id": "seg-1",
            "justification_reason": "short"
        })
        assert res.status_code in [400, 401, 403, 422]

# 3. Test Fleet Inventory Stats Endpoint
@pytest.mark.asyncio
async def test_fleet_stats_unauthorized():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # Non-admin user access block hona chahiye
        res = await client.get("/api/v1/admin/fleet/stats")
        assert res.status_code in [401, 403]