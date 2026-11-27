import httpx
import pytest
from httpx import ASGITransport

from backend.main import app


@pytest.mark.asyncio
async def test_anonymous_identity_lookup_endpoint_is_not_exposed():
    transport = ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/api/v1/auth/lookup",
            json={"identifier": "+919876543210", "role": "customer"},
        )

    assert response.status_code in (404, 405)
    assert not any(key in response.json() for key in ("full_name", "phone_number", "email", "user_id"))
