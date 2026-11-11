import pytest
from httpx import AsyncClient, ASGITransport
from backend.main import app

@pytest.mark.asyncio
async def test_admin_fleet_map_endpoint(db_session):
    from backend.domain.models import User, DriverProfile, DriverAvailability, DriverLocation
    from datetime import datetime, timezone
    import uuid

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # 1. Baseline fleet test
        resp = await client.get("/api/v1/location/admin/fleet-map")
        assert resp.status_code == 200
        data = resp.json()
        assert "fleet" in data
        assert "drivers" in data
        initial_count = data.get("count", 0)

        # 2. Insert real driver & location records
        suffix = uuid.uuid4().hex[:6]
        user = User(
            email=f"pilot_{suffix}@vaisentra.luxury",
            password_hash="hash",
            full_name="Rajesh Test Chauffeur",
            role="driver",
            phone_number=f"+9198{suffix[:8]}"
        )
        db_session.add(user)
        await db_session.flush()

        profile = DriverProfile(
            user_id=user.id,
            license_number=f"DL-1ER-{suffix}",
            approval_status="APPROVED"
        )
        db_session.add(profile)
        await db_session.flush()

        avail = DriverAvailability(
            driver_id=profile.id,
            is_online=True
        )
        db_session.add(avail)

        loc = DriverLocation(
            driver_id=profile.id,
            latitude=28.6328,
            longitude=77.2195,
            speed=20.0,
            heading=90.0,
            recorded_at=datetime.now(timezone.utc)
        )
        db_session.add(loc)
        await db_session.commit()

        # 3. Verify fleet map returns persisted driver
        resp2 = await client.get("/api/v1/location/admin/fleet-map")
        assert resp2.status_code == 200
        data2 = resp2.json()
        assert data2["count"] >= initial_count + 1
        pilot = next((p for p in data2["fleet"] if p.get("plate") == f"DL-1ER-{suffix}"), None)
        assert pilot is not None
        assert "driver" in pilot
        assert "plate" in pilot
        assert pilot["plate"] == f"DL-1ER-{suffix}"
        assert pilot["is_synthetic"] is False

@pytest.mark.asyncio
async def test_trip_history_breadcrumbs_playback(db_session):
    from backend.domain.models import User, DriverProfile, Ride, RideLocationHistory
    from datetime import datetime, timezone
    import uuid

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # 1. Empty trip history without recorded crumbs returns real empty structure
        resp = await client.get("/api/v1/location/trip-history/RIDE-DEL-8801")
        assert resp.status_code == 200
        data = resp.json()
        assert data["ride_id"] == "RIDE-DEL-8801"
        assert data["breadcrumbs"] == []
        assert data["count"] == 0
        assert data["is_synthetic"] is False

        # 2. Add real driver and breadcrumb to database
        suffix = uuid.uuid4().hex[:6]
        user = User(
            email=f"rider_{suffix}@vaisentra.luxury",
            password_hash="hash",
            full_name="Driver Crumb Test",
            role="driver",
            phone_number=f"+9197{suffix[:8]}"
        )
        db_session.add(user)
        await db_session.flush()

        profile = DriverProfile(
            user_id=user.id,
            license_number=f"DL-2ER-{suffix}",
            approval_status="APPROVED"
        )
        db_session.add(profile)
        await db_session.flush()

        # Create ride record
        ride = Ride(
            id="RIDE-DEL-8801",
            customer_id=user.id,
            vehicle_category_id="cat-vip",
            fare_rule_id="rule-vip",
            status="COMPLETED",
            pickup_address="Connaught Place",
            pickup_latitude=28.6328,
            pickup_longitude=77.2195,
            destination_address="India Gate",
            destination_latitude=28.6128,
            destination_longitude=77.2305,
            estimated_distance_km=3.2,
            estimated_duration_minutes=15,
            estimated_fare_cents=15000,
            start_otp="1234"
        )
        db_session.add(ride)
        await db_session.flush()

        crumb = RideLocationHistory(
            ride_id="RIDE-DEL-8801",
            driver_id=profile.id,
            latitude=28.6328,
            longitude=77.2195,
            speed_kmh=24.5,
            heading=90.0,
            recorded_at=datetime.now(timezone.utc)
        )
        db_session.add(crumb)
        await db_session.commit()

        # 3. Verify real breadcrumb returned
        resp2 = await client.get("/api/v1/location/trip-history/RIDE-DEL-8801")
        assert resp2.status_code == 200
        data2 = resp2.json()
        assert data2["count"] == 1
        assert len(data2["breadcrumbs"]) == 1
        c = data2["breadcrumbs"][0]
        assert c["latitude"] == 28.6328
        assert c["longitude"] == 77.2195
        assert c["speed_kmh"] == 24.5

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

@pytest.mark.asyncio
async def test_active_ride_driver_location_auto_crumb_persistence(db_session):
    """Verifies that driver location updates automatically record breadcrumbs for active rides."""
    from backend.domain.models import User, DriverProfile, DriverAvailability, Ride, RideLocationHistory
    from backend.application.services.driver_service import DriverService
    import uuid

    suffix = uuid.uuid4().hex[:6]
    user = User(
        email=f"driver_crumb_{suffix}@vaisentra.luxury",
        password_hash="hash",
        full_name="Auto Crumb Driver",
        role="driver",
        phone_number=f"+9196{suffix[:8]}"
    )
    db_session.add(user)
    await db_session.flush()

    profile = DriverProfile(
        user_id=user.id,
        license_number=f"DL-CRUMB-{suffix}",
        approval_status="APPROVED"
    )
    db_session.add(profile)
    await db_session.flush()

    avail = DriverAvailability(
        driver_id=profile.id,
        is_online=True,
        is_busy=True
    )
    db_session.add(avail)

    ride_id = f"RIDE-REAL-{suffix}"
    ride = Ride(
        id=ride_id,
        customer_id=user.id,
        assigned_driver_id=profile.id,
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="IN_PROGRESS",
        pickup_address="Rajiv Chowk Gate 2",
        pickup_latitude=28.6328,
        pickup_longitude=77.2195,
        destination_address="Barakhamba Road",
        destination_latitude=28.6300,
        destination_longitude=77.2250,
        estimated_distance_km=1.2,
        estimated_duration_minutes=6,
        estimated_fare_cents=5000,
        start_otp="5678"
    )
    db_session.add(ride)
    await db_session.commit()

    service = DriverService(db_session)
    # 1. Update single location
    res1 = await service.update_location(
        user_id=user.id,
        lat=28.6328,
        lng=77.2195,
        heading=120.0,
        speed_mps=6.5,
        accuracy_m=4.2,
        is_mock_location=False,
        location_source="FUSED"
    )
    assert res1["status"] == "RECORDED"

    # 2. Update batch location (simulating offline replay)
    batch_items = [
        {
            "latitude": 28.6315,
            "longitude": 77.2220,
            "heading": 122.0,
            "speed_mps": 7.0,
            "accuracy_m": 3.8,
            "is_mock_location": False,
            "location_source": "FUSED"
        },
        {
            "latitude": 28.6300,
            "longitude": 77.2250,
            "heading": 125.0,
            "speed_mps": 5.5,
            "accuracy_m": 3.5,
            "is_mock_location": False,
            "location_source": "FUSED"
        }
    ]
    res2 = await service.update_location_batch(user_id=user.id, locations=batch_items)
    assert res2["status"] == "SUCCESS"
    assert res2["ingested_count"] == 2

    # 3. Verify via trip-history endpoint
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get(f"/api/v1/location/trip-history/{ride_id}")
        assert resp.status_code == 200
        data = resp.json()
        assert data["ride_id"] == ride_id
        assert data["count"] == 3
        assert len(data["breadcrumbs"]) == 3
        assert data["is_synthetic"] is False
        assert data["source"] == "database_records"
        assert data["summary"] is not None
        assert data["summary"]["origin"] == "Rajiv Chowk Gate 2"
        assert data["summary"]["destination"] == "Barakhamba Road"
        assert data["summary"]["distance_km"] > 0.0

