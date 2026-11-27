import pytest
import httpx
from httpx import ASGITransport

from backend.main import app


@pytest.mark.asyncio
async def test_vehicle_category_mutations_require_admin_authentication():
    transport = ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        create_response = await client.post(
            "/api/v1/vehicles/categories",
            json={
                "code": "SECURITY_TEST_CATEGORY",
                "name": "Security Test Category",
                "capacity": 3,
                "sort_order": 99,
                "is_electric": True,
            },
        )
        update_response = await client.put(
            "/api/v1/vehicles/categories/00000000-0000-0000-0000-000000000001",
            json={"name": "Unauthorized Update"},
        )
        delete_response = await client.delete(
            "/api/v1/vehicles/categories/00000000-0000-0000-0000-000000000001"
        )

    assert create_response.status_code in (401, 403)
    assert update_response.status_code in (401, 403)
    assert delete_response.status_code in (401, 403)
