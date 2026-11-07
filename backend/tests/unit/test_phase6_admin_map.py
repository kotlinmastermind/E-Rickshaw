import pytest
from httpx import AsyncClient, ASGITransport
from backend.main import app

@pytest.mark.asyncio
async def test_admin_fleet_map_endpoint(db_session):
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/location/admin/fleet-map")
        assert resp.status_code == 200
        data = resp.json()
        assert "fleet" in data
        assert "drivers" in data
        assert data["count"] > 0
        pilot = data["fleet"][0]
        assert "driver" in pilot
        assert "plate" in pilot
        assert "battery" in pilot
        assert "lat" in pilot
        assert "lng" in pilot
        assert "status" in pilot

@pytest.mark.asyncio
async def test_trip_history_breadcrumbs_playback(db_session):
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/location/trip-history/RIDE-DEL-8801")
        assert resp.status_code == 200
        data = resp.json()
        assert data["ride_id"] == "RIDE-DEL-8801"
        assert "breadcrumbs" in data
        assert len(data["breadcrumbs"]) >= 10
        crumb = data["breadcrumbs"][0]
        assert "latitude" in crumb
        assert "longitude" in crumb
        assert "speed_kmh" in crumb
        assert "heading" in crumb
        assert "recorded_at" in crumb

@pytest.mark.asyncio
async def test_geofence_location_endpoints(db_session):
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # 1. List default geofences
        resp = await client.get("/api/v1/location/geofences")
        assert resp.status_code == 200
        data = resp.json()
        assert "geofences" in data
        assert len(data["geofences"]) >= 3

        # 2. Create custom geofence
        new_zone = {
            "name": "Barakhamba Metro Commercial Zone",
            "zone_type": "Surge Zone (+1.5x)",
            "color": "#d4af37",
            "surge_multiplier": 1.5,
            "coordinates": [
                [77.2200, 28.6310],
                [77.2260, 28.6310],
                [77.2260, 28.6270],
                [77.2200, 28.6270],
                [77.2200, 28.6310]
            ]
        }
        post_resp = await client.post("/api/v1/location/geofences", json=new_zone)
        assert post_resp.status_code == 200
        created = post_resp.json()
        assert created["status"] == "CREATED"
        assert created["name"] == "Barakhamba Metro Commercial Zone"
        assert created["surge_multiplier"] == 1.5

@pytest.mark.asyncio
async def test_road_restrictions_endpoints(db_session):
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # List road restrictions
        resp = await client.get("/api/v1/location/road-restrictions")
        assert resp.status_code == 200
        data = resp.json()
        assert isinstance(data, list)
        assert len(data) >= 1
