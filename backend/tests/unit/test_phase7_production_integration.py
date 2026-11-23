import pytest
from httpx import AsyncClient, ASGITransport
from datetime import datetime, timezone, timedelta
from sqlalchemy import select
from backend.main import app
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, DriverAvailability,
    DriverLocation, Ride, RideAssignment, RideLocationHistory,
    VehicleCategory, Vehicle, DriverVehicleAssignment
)
from backend.infrastructure.security.crypto import hash_password

@pytest.mark.asyncio
async def test_production_ride_booking_with_coordinates(db_session):
    import uuid
    suffix = uuid.uuid4().hex[:6]
    test_email = f"commuter.{suffix}@ridego.com"
    test_phone = f"+91987{suffix[:5]}0"
    # Setup test customer user and profile
    cust_user = User(
        email=test_email,
        phone_number=test_phone,
        password_hash=hash_password("CustomerPass123!"),
        role="customer",
        is_active=True
    )
    db_session.add(cust_user)
    await db_session.flush()

    cust_profile = CustomerProfile(
        user_id=cust_user.id
    )
    db_session.add(cust_profile)
    await db_session.commit()

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # Authenticate via real password login to create an active DB session
        login_resp = await client.post(
            "/api/v1/auth/password/login",
            json={"email": test_email, "password": "CustomerPass123!"}
        )
        assert login_resp.status_code == 200, login_resp.text
        cust_token = login_resp.json()["access_token"]

        # Fetch active vehicle category
        cat_resp = await client.get("/api/v1/vehicles/categories")
        categories = cat_resp.json()
        assert len(categories) > 0
        cat_id = categories[0]["id"]

        # Negative test 1: missing category_id strictly rejected with 400
        payload_no_cat = {
            "pickup_address": "Connaught Place Inner Circle",
            "destination_address": "India Gate C-Hexagon",
            "pickup_latitude": 28.6328,
            "pickup_longitude": 77.2195,
            "destination_latitude": 28.6129,
            "destination_longitude": 77.2295,
            "payment_method": "WALLET"
        }
        headers = {"Authorization": f"Bearer {cust_token}"}
        resp_bad = await client.post("/api/v1/rides/request", json=payload_no_cat, headers=headers)
        assert resp_bad.status_code == 400
        assert "category_id is required" in resp_bad.text

        # Negative test 2: null category_id strictly rejected with 400
        resp_null = await client.post("/api/v1/rides/request", json=dict(payload_no_cat, category_id=None), headers=headers)
        assert resp_null.status_code == 400
        assert "category_id is required" in resp_null.text

        # Negative test 3: invalid category_id strictly rejected with 404
        resp_inv = await client.post("/api/v1/rides/request", json=dict(payload_no_cat, category_id="invalid_cat_99999"), headers=headers)
        assert resp_inv.status_code == 404

        # Request ride with real GPS coordinates and valid category_id
        payload = dict(payload_no_cat, category_id=cat_id)
        resp = await client.post("/api/v1/rides/request", json=payload, headers=headers)
        assert resp.status_code == 200, resp.text
        data = resp.json()
        assert "ride_id" in data
        assert "start_otp" in data
        assert len(data["start_otp"]) == 6
        ride_id = data["ride_id"]

        # Verify persisted Ride in database
        stmt = select(Ride).where(Ride.id == ride_id)
        ride = (await db_session.execute(stmt)).scalar_one_or_none()
        assert ride is not None
        assert ride.status == "REQUESTED"
        assert abs(ride.pickup_latitude - 28.6328) < 1e-4
        assert abs(ride.pickup_longitude - 77.2195) < 1e-4
        assert abs(ride.destination_latitude - 28.6129) < 1e-4
        assert abs(ride.destination_longitude - 77.2295) < 1e-4

@pytest.mark.asyncio
async def test_end_to_end_ride_lifecycle_and_trip_breadcrumbs(db_session):
    # Setup test customer
    cust_user = User(
        email="cust.lifecycle@ridego.com",
        phone_number="+919876543211",
        password_hash=hash_password("CustomerPass123!"),
        role="customer",
        is_active=True
    )
    # Setup test driver
    driver_user = User(
        email="pilot.lifecycle@ridego.com",
        phone_number="+919876543212",
        password_hash=hash_password("DriverPass123!"),
        role="driver",
        is_active=True
    )
    db_session.add_all([cust_user, driver_user])
    await db_session.flush()

    cust_prof = CustomerProfile(user_id=cust_user.id)
    driver_prof = DriverProfile(user_id=driver_user.id, license_number="DL-04-2024-9988", approval_status="APPROVED")
    db_session.add_all([cust_prof, driver_prof])
    await db_session.flush()

    driver_avail = DriverAvailability(driver_id=driver_prof.id, is_online=True, is_busy=False)
    db_session.add(driver_avail)

    cat_stmt = select(VehicleCategory).where(VehicleCategory.is_active == True)
    active_cat = (await db_session.execute(cat_stmt)).scalars().first()

    driver_veh = Vehicle(
        id=f"veh_p7_{driver_user.id[:8]}",
        category_id=active_cat.id,
        make="Mahindra",
        model="Treo",
        year=2024,
        license_plate="DL-04-2024-9988",
        color="Green",
        is_active=True
    )
    db_session.add(driver_veh)
    await db_session.flush()

    veh_assign = DriverVehicleAssignment(
        driver_id=driver_prof.id,
        vehicle_id=driver_veh.id,
        is_active=True
    )
    db_session.add(veh_assign)
    await db_session.commit()

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # Authenticate customer and driver via real login to create active DB sessions
        c_login = await client.post(
            "/api/v1/auth/password/login",
            json={"email": "cust.lifecycle@ridego.com", "password": "CustomerPass123!"}
        )
        assert c_login.status_code == 200, c_login.text
        cust_token = c_login.json()["access_token"]

        d_login = await client.post(
            "/api/v1/auth/password/login",
            json={"email": "pilot.lifecycle@ridego.com", "password": "DriverPass123!"}
        )
        assert d_login.status_code == 200, d_login.text
        driver_token = d_login.json()["access_token"]

        # 1. Customer creates ride with mandatory category_id
        req_resp = await client.post(
            "/api/v1/rides/request",
            json={
                "category_id": active_cat.id,
                "pickup_address": "Janpath Metro Station",
                "destination_address": "Khan Market",
                "pickup_latitude": 28.6250,
                "pickup_longitude": 77.2185,
                "destination_latitude": 28.6000,
                "destination_longitude": 77.2270
            },
            headers={"Authorization": f"Bearer {cust_token}"}
        )
        assert req_resp.status_code == 200
        ride_data = req_resp.json()
        ride_id = ride_data["ride_id"]
        start_otp = ride_data["start_otp"]

        # Offer ride assignment to driver so they can accept
        assignment = RideAssignment(
            ride_id=ride_id,
            driver_id=driver_prof.id,
            status="OFFERED",
            expires_at=datetime.now(timezone.utc) + timedelta(minutes=10)
        )
        db_session.add(assignment)
        await db_session.commit()

        # 2. Driver accepts ride
        accept_resp = await client.post(
            f"/api/v1/rides/{ride_id}/accept",
            headers={"Authorization": f"Bearer {driver_token}"}
        )
        assert accept_resp.status_code == 200

        # 3. Driver arrives at pickup
        arrive_resp = await client.post(
            f"/api/v1/rides/{ride_id}/arrive",
            headers={"Authorization": f"Bearer {driver_token}"}
        )
        assert arrive_resp.status_code == 200

        # 4. Driver starts trip with OTP
        start_resp = await client.post(
            f"/api/v1/rides/{ride_id}/start",
            json={"otp_code": start_otp},
            headers={"Authorization": f"Bearer {driver_token}"}
        )
        assert start_resp.status_code == 200

        # 5. Ingest real driver GPS telemetry while on active trip
        telemetry_payload = {
            "latitude": 28.6180,
            "longitude": 77.2210,
            "heading": 160.0,
            "speed_kmh": 21.5,
            "accuracy_meters": 4.0,
            "ride_id": ride_id
        }
        telem_resp = await client.post(
            "/api/v1/location/driver-telemetry",
            json=telemetry_payload,
            headers={"Authorization": f"Bearer {driver_token}"}
        )
        assert telem_resp.status_code == 200

        # 6. Driver completes trip
        finish_resp = await client.post(
            f"/api/v1/rides/{ride_id}/finish",
            json={"actual_distance_km": 2.8, "actual_duration_minutes": 11.2},
            headers={"Authorization": f"Bearer {driver_token}"}
        )
        assert finish_resp.status_code == 200

        # 7. Verify Historical Trip Breadcrumb endpoint returns real persisted breadcrumbs
        history_resp = await client.get(f"/api/v1/location/trip-history/{ride_id}")
        assert history_resp.status_code == 200
        hist_data = history_resp.json()
        assert hist_data["ride_id"] == ride_id
        assert hist_data["is_synthetic"] is False
        assert hist_data["source"] == "database_records"
        assert len(hist_data["breadcrumbs"]) >= 1
        crumb = hist_data["breadcrumbs"][0]
        assert abs(crumb["latitude"] - 28.6180) < 1e-4
        assert abs(crumb["longitude"] - 77.2210) < 1e-4
        assert crumb["speed_kmh"] == 21.5
