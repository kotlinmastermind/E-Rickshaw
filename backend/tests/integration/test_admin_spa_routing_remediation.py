import pytest
import os
import re
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



@pytest.mark.asyncio
async def test_admin_console_does_not_advertise_unverified_live_state():
    admin_path = os.path.join(STATIC_ADMIN_DIR, "index.html")
    with open(admin_path, "r", encoding="utf-8") as source:
        html = source.read()

    # Live badges start empty and only show counts after the authenticated feed responds.
    assert re.search(r'id="badge-pending-kyc"[^>]*>\s*</span>', html)
    assert re.search(r'id="badge-active-rides-tab"[^>]*>\s*</span>', html)
    assert "SUPABASE LIVE (AWS)" not in html
    assert "CONNECTING TO ADMIN API" in html
    assert "if (!response.ok)" in html
    assert "setAdminConnectionStatus('unavailable')" in html

    # Admin telemetry accepts the legacy location event emitted by the FastAPI
    # compatibility route and rejects out-of-order GPS samples.
    assert "'DRIVER.LOCATION.UPDATED': 'DRIVER_LOCATION_UPDATE'" in html
    assert "incomingTimestamp < currentTimestamp" in html

    # Ride lifecycle events reconcile against the persisted live-feed snapshot,
    # including rides missing from the Admin's previously loaded list.
    assert "scheduleAdminRideSnapshotRefresh();" in html
    assert "_nativeFetch('/api/v1/admin/live-feed'" in html
    assert "STATE.activeRides = Array.isArray(data.active_rides) ? data.active_rides : [];" in html
    assert "ride.current_offer" in html
    assert "Dispatch: ${dispatchState}" in html

    admin_routes_path = os.path.join(os.path.dirname(STATIC_ADMIN_DIR), "api", "routes", "admin.py")
    with open(admin_routes_path, "r", encoding="utf-8") as source:
        admin_routes = source.read()
    assert '"dispatch_status": "OFFERED" if offer_is_live' in admin_routes
    assert '"current_offer": current_offer' in admin_routes

    governance_path = os.path.join(os.path.dirname(STATIC_ADMIN_DIR), "application", "services", "admin_governance_service.py")
    with open(governance_path, "r", encoding="utf-8") as source:
        governance = source.read()
    assert '"offer_history"' in governance

    # Transient Admin WebSocket failures keep polling alive and retry indefinitely
    # with capped exponential backoff; only an authorization close prompts login.
    assert "function scheduleAdminWebSocketReconnect(reason)" in html
    assert "const _adminWsReconnectMaxDelayMs = 30000;" in html
    assert "Math.min(2000 * (2 ** exponent), _adminWsReconnectMaxDelayMs)" in html
    assert "scheduleAdminWebSocketReconnect(`socket closed (${event.code})`);" in html
    assert "scheduleAdminWebSocketReconnect('connection or ticket request failed');" in html
    assert "if (!isCurrentSocket || !STATE.adminToken) return;" in html
    assert "if (event.code === 1008)" in html
    assert "_adminWsMaxReconnectAttempts" not in html
