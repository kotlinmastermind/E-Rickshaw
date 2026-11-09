"""
Unit and API integration tests for /api/v1/dispatch endpoints.
"""
import uuid
import pytest
from datetime import datetime, timezone
from httpx import AsyncClient, ASGITransport

from backend.main import app
from backend.api.deps import get_current_user, get_current_driver
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, DriverAvailability,
    VehicleCategory, FareRule, Ride
)


@pytest.mark.asyncio
async def test_dispatch_api_endpoints(db_session):
    suffix = uuid.uuid4().hex[:6]

    # Category and fare rule
    cat = VehicleCategory(
        id=str(uuid.uuid4()),
        code=f"H3_FLEET_{suffix}",
        name="H3 Fleet E-Rickshaw"
    )
    db_session.add(cat)
    await db_session.flush()

    fare_rule = FareRule(
        id=str(uuid.uuid4()),
        category_id=cat.id,
        base_fare_cents=2500,
        per_km_cents=800,
        per_minute_cents=100,
        minimum_fare_cents=2500
    )
    db_session.add(fare_rule)
    await db_session.flush()

    # Driver User & Profile
    driver_user = User(
        phone_number=f"+91981{suffix}05",
        full_name="Sunil Driver",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    driver_profile = DriverProfile(
        id=str(uuid.uuid4()),
        user_id=driver_user.id,
        license_number=f"DL-API-{suffix}",
        approval_status="APPROVED",
        rating_average=4.95
    )
    db_session.add(driver_profile)
    await db_session.flush()

    driver_avail = DriverAvailability(
        driver_id=driver_profile.id,
        is_online=True,
        is_busy=False
    )
    db_session.add(driver_avail)
    await db_session.flush()

    # Customer User & Profile
    cust_user = User(
        phone_number=f"+91981{suffix}06",
        full_name="Meera Customer",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add(cust_user)
    await db_session.flush()

    cust_profile = CustomerProfile(user_id=cust_user.id)
    db_session.add(cust_profile)
    await db_session.flush()

    # Ride
    ride = Ride(
        customer_id=cust_profile.id,
        vehicle_category_id=cat.id,
        fare_rule_id=fare_rule.id,
        start_otp="5521",
        status="REQUESTED",
        pickup_address="Janpath, New Delhi",
        pickup_latitude=28.6200,
        pickup_longitude=77.2180,
        destination_address="Lodhi Garden, New Delhi",
        destination_latitude=28.5930,
        destination_longitude=77.2190,
        estimated_distance_km=3.0,
        estimated_duration_minutes=10.0,
        estimated_fare_cents=12000
    )
    db_session.add(ride)
    await db_session.commit()

    # Setup dependency overrides for auth
    app.dependency_overrides[get_current_driver] = lambda: driver_user
    app.dependency_overrides[get_current_user] = lambda: cust_user

    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            # 1. Driver Ingests Telemetry
            loc_payload = {
                "latitude": 28.6190,
                "longitude": 77.2170,
                "heading": 45.0,
                "speed_mps": 6.5,
                "accuracy_m": 4.0
            }
            resp_loc = await client.post("/api/v1/dispatch/driver/location", json=loc_payload)
            assert resp_loc.status_code == 200, resp_loc.text
            loc_data = resp_loc.json()
            assert loc_data["status"] == "RECORDED"
            assert "h3_cell" in loc_data

            # 2. Nearby Drivers Discovery
            resp_nearby = await client.get(
                "/api/v1/dispatch/nearby-drivers",
                params={"latitude": 28.6200, "longitude": 77.2180, "max_rings": 2}
            )
            assert resp_nearby.status_code == 200, resp_nearby.text
            nearby_data = resp_nearby.json()
            assert nearby_data["total_count"] >= 1
            driver_found = any(d["driver_id"] == driver_profile.id for d in nearby_data["drivers"])
            assert driver_found is True

            # 3. Trigger Smart Dispatch Search
            resp_search = await client.post(
                "/api/v1/dispatch/search",
                json={"ride_id": ride.id, "max_rings": 2}
            )
            assert resp_search.status_code == 200, resp_search.text
            search_data = resp_search.json()
            assert search_data["status"] == "OFFERED"
            assert search_data["driver_id"] == driver_profile.id

            # 4. Check Dispatch Status
            resp_status = await client.get(f"/api/v1/dispatch/status/{ride.id}")
            assert resp_status.status_code == 200, resp_status.text
            status_data = resp_status.json()
            assert status_data["ride_status"] == "REQUESTED"
            assert status_data["current_offer"]["status"] == "OFFERED"

            # 5. Driver Accepts Offer
            resp_accept = await client.post(f"/api/v1/dispatch/offer/{ride.id}/accept")
            assert resp_accept.status_code == 200, resp_accept.text
            accept_data = resp_accept.json()
            assert accept_data["status"] == "ACCEPTED"
            assert accept_data["driver_id"] == driver_profile.id

            # Verify Ride is now ASSIGNED
            resp_status_after = await client.get(f"/api/v1/dispatch/status/{ride.id}")
            assert resp_status_after.json()["ride_status"] == "ASSIGNED"

    finally:
        app.dependency_overrides.clear()

