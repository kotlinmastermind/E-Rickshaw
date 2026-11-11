import pytest
import os
from httpx import AsyncClient, ASGITransport
from backend.main import app, STATIC_ADMIN_DIR
from backend.infrastructure.security.crypto import create_jwt_token

@pytest.mark.asyncio
async def test_admin_spa_and_api_route_separation():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. GET /admin -> serves SPA entry point (200, HTML)
        resp = await client.get("/admin")
        assert resp.status_code == 200
        assert "text/html" in resp.headers.get("content-type", "")

        # 2. GET /admin/dashboard -> serves SPA entry point (200, HTML)
        resp = await client.get("/admin/dashboard")
        assert resp.status_code == 200
        assert "text/html" in resp.headers.get("content-type", "")

        # 3. GET /admin/assets/... for an existing asset -> returns 200 with appropriate media type
        assets_dir = os.path.join(STATIC_ADMIN_DIR, "assets")
        if os.path.exists(assets_dir):
            files = [f for f in os.listdir(assets_dir) if f.endswith(".js")]
            if files:
                asset_name = files[0]
                resp = await client.get(f"/admin/assets/{asset_name}")
                assert resp.status_code == 200
                assert ("javascript" in resp.headers.get("content-type", "") or
                        "application" in resp.headers.get("content-type", "") or
                        "text" in resp.headers.get("content-type", ""))

        # 4. GET /admin/dashboard/stats -> MUST reach the API endpoint (JSON response, NOT index.html)
        # Without auth header, it must return 401 Unauthorized JSON, NEVER 200 index.html!
        resp = await client.get("/admin/dashboard/stats")
        assert resp.status_code == 401
        assert "application/json" in resp.headers.get("content-type", "")
        data = resp.json()
        assert "detail" in data

        # 5. GET /api/v1/admin/dashboard/stats -> Canonical API endpoint
        resp = await client.get("/api/v1/admin/dashboard/stats")
        assert resp.status_code == 401
        assert "application/json" in resp.headers.get("content-type", "")

        # 6. Unknown Admin API endpoint -> returns 404 JSON, NOT index.html
        resp = await client.get("/admin/api/v1/nonexistent_api_endpoint")
        assert resp.status_code == 404
        assert "application/json" in resp.headers.get("content-type", "")
        assert resp.json()["detail"] == "Not Found"

        # 7. Unknown API endpoint under /api/v1 -> returns 404 JSON
        resp = await client.get("/api/v1/nonexistent_route_test")
        assert resp.status_code == 404
        assert "application/json" in resp.headers.get("content-type", "")

        # 8. Missing JavaScript asset under /admin/assets/ -> returns 404, NEVER index.html!
        resp = await client.get("/admin/assets/missing_chunk_99999.js")
        assert resp.status_code == 404
        assert "<!DOCTYPE html>" not in resp.text
        assert resp.json()["detail"] in ("Asset not found", "Not Found")

        # 9. Missing CSS asset under /admin/assets/ -> returns 404, NEVER index.html!
        resp = await client.get("/admin/assets/missing_style_99999.css")
        assert resp.status_code == 404
        assert "<!DOCTYPE html>" not in resp.text

        # 10. Unknown frontend route under /admin -> serves SPA shell (200, HTML)
        resp = await client.get("/admin/live-fleet-command-center")
        assert resp.status_code == 200
        assert "text/html" in resp.headers.get("content-type", "")

