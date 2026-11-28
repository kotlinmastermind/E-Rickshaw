"""
Tests verifying that Customer Vehicle Category Selection propagates to Backend,
saves to the database with the exact requested category, and dispatches only to
eligible drivers of that specific vehicle category.
"""
import uuid
import pytest
from datetime import datetime, timezone
from sqlalchemy import select
from httpx import AsyncClient, ASGITransport

from backend.main import app
from backend.api.deps import get_current_customer, get_current_user
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, DriverAvailability,
    VehicleCategory, FareRule, Ride, Vehicle, DriverVehicleAssignment, FareEstimate,
    GeofenceZone, DriverServiceAreaAssignment
)
from backend.application.services.smart_dispatch_service import SmartDispatchService
from backend.application.services.live_location_service import LiveLocationService
from backend.application.services.ride_service import RideService


@pytest.mark.asyncio
async def test_ride_request_preserves_selected_category_in_db(db_session):
    """
    Verifies that when a customer selects a category (e.g. Electric Auto),
    the category_id is accepted by the backend and the ride is saved with
    vehicle_category_id == selected category (and NOT defaulted to E-Rickshaw).
    """
    suffix = uuid.uuid4().hex[:6]
    service_area = (await db_session.execute(select(GeofenceZone).where(GeofenceZone.city_code == "DELHI_NCR", GeofenceZone.zone_type == "SERVICE_AREA"))).scalars().one()

    # Create two categories in DB: Rickshaw and Auto
    cat_rickshaw = VehicleCategory(
        id=f"cat_rickshaw_{suffix}",
        code=f"RICKSHAW_{suffix}",
        name="Standard Rickshaw",
        sort_order=1,
        is_active=True
    )
    cat_auto = VehicleCategory(
        id=f"cat_auto_{suffix}",
        code=f"AUTO_{suffix}",
        name="High-Speed Electric Auto",
        sort_order=2,
        is_active=True
    )
    db_session.add_all([cat_rickshaw, cat_auto])
    await db_session.flush()

    rule_auto = FareRule(
        id=f"rule_auto_{suffix}",
        category_id=cat_auto.id,
        base_fare_cents=3000,
        per_km_cents=1200,
        per_minute_cents=150,
        minimum_fare_cents=3000,
        is_active=True
    )
    db_session.add(rule_auto)
    await db_session.flush()

    # Create customer
    c_user = User(
        phone_number=f"+91981{suffix}11",
        full_name="Ananya Verma",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add(c_user)
    await db_session.flush()
    c_prof = CustomerProfile(user_id=c_user.id)
    db_session.add(c_prof)
    await db_session.flush()

    # Customer books ride choosing cat_auto.id with mock/fallback estimate
    ride_svc = RideService(db_session)
    res = await ride_svc.create_ride_request(
        customer_user_id=c_user.id,
        estimate_id="mock-client-fallback-id",
        category_id=cat_auto.id,
        pickup_address="Anand Vihar Metro Gate 2",
        destination_address="Preet Vihar Metro Gate 3",
        pickup_latitude=28.6475,
        pickup_longitude=77.3150,
        destination_latitude=28.6350,
        destination_longitude=77.2910
    )

    assert "ride_id" in res
    assert res["category_id"] == cat_auto.id

    # Verify directly from Database
    db_ride = (await db_session.execute(select(Ride).where(Ride.id == res["ride_id"]))).scalar_one()
    assert db_ride.vehicle_category_id == cat_auto.id
    assert db_ride.vehicle_category_id != cat_rickshaw.id


@pytest.mark.asyncio
async def test_dispatch_targets_only_matching_category_driver(db_session):
    """
    Verifies that when a ride is requested for Category Auto,
    SmartDispatchService matches ONLY the driver assigned to Category Auto,
    and does NOT offer it to a driver assigned to Category Rickshaw or Cab.
    """
    suffix = uuid.uuid4().hex[:6]
    service_area = (await db_session.execute(select(GeofenceZone).where(GeofenceZone.city_code == "DELHI_NCR", GeofenceZone.zone_type == "SERVICE_AREA"))).scalars().one()

    # Categories
    cat_rickshaw = VehicleCategory(
        id=f"cat_r_{suffix}",
        code=f"E_RICKSHAW_{suffix}",
        name="E-Rickshaw Fleet",
        sort_order=1,
        is_active=True
    )
    cat_auto = VehicleCategory(
        id=f"cat_a_{suffix}",
        code=f"E_AUTO_{suffix}",
        name="E-Auto Fleet",
        sort_order=2,
        is_active=True
    )
    db_session.add_all([cat_rickshaw, cat_auto])
    await db_session.flush()

    rule_auto = FareRule(
        id=f"rule_a_{suffix}",
        category_id=cat_auto.id,
        base_fare_cents=3000,
        per_km_cents=1000,
        per_minute_cents=100,
        minimum_fare_cents=3000,
        is_active=True
    )
    db_session.add(rule_auto)
    await db_session.flush()

    # Passenger
    c_user = User(phone_number=f"+91981{suffix}20", full_name="Passenger Test", role="customer", is_active=True, is_verified=True)
    db_session.add(c_user)
    await db_session.flush()
    c_prof = CustomerProfile(user_id=c_user.id)
    db_session.add(c_prof)
    await db_session.flush()

    # Driver 1: Has an E-Rickshaw vehicle
    d_user1 = User(phone_number=f"+91981{suffix}21", full_name="Rickshaw Pilot", role="driver", is_active=True, is_verified=True)
    db_session.add(d_user1)
    await db_session.flush()
    d_prof1 = DriverProfile(id=f"dp1_{suffix}", user_id=d_user1.id, license_number=f"DL-R-{suffix}", approval_status="APPROVED")
    db_session.add(d_prof1)
    await db_session.flush()
    db_session.add(DriverServiceAreaAssignment(driver_id=d_prof1.id, service_area_id=service_area.id))
    await db_session.flush()

    veh1 = Vehicle(id=f"v1_{suffix}", category_id=cat_rickshaw.id, make="Mahindra", model="Treo EV", year=2024, license_plate=f"DL-1R-{suffix}")
    db_session.add(veh1)
    await db_session.flush()
    assign1 = DriverVehicleAssignment(driver_id=d_prof1.id, vehicle_id=veh1.id, is_active=True)
    db_session.add(assign1)

    avail1 = DriverAvailability(driver_id=d_prof1.id, is_online=True, is_busy=False)
    db_session.add(avail1)

    # Driver 2: Has an E-Auto vehicle
    d_user2 = User(phone_number=f"+91981{suffix}22", full_name="Auto Pilot", role="driver", is_active=True, is_verified=True)
    db_session.add(d_user2)
    await db_session.flush()
    d_prof2 = DriverProfile(id=f"dp2_{suffix}", user_id=d_user2.id, license_number=f"DL-A-{suffix}", approval_status="APPROVED")
    db_session.add(d_prof2)
    await db_session.flush()
    db_session.add(DriverServiceAreaAssignment(driver_id=d_prof2.id, service_area_id=service_area.id))
    await db_session.flush()

    veh2 = Vehicle(id=f"v2_{suffix}", category_id=cat_auto.id, make="Bajaj", model="RE E-Auto", year=2024, license_plate=f"DL-1A-{suffix}")
    db_session.add(veh2)
    await db_session.flush()
    assign2 = DriverVehicleAssignment(driver_id=d_prof2.id, vehicle_id=veh2.id, is_active=True)
    db_session.add(assign2)

    avail2 = DriverAvailability(driver_id=d_prof2.id, is_online=True, is_busy=False)
    db_session.add(avail2)

    # Put both drivers at identical pickup location
    live_loc = LiveLocationService(db=db_session)
    await live_loc.ingest_driver_location(d_prof1.id, 28.6140, 77.2090, is_online=True)
    await live_loc.ingest_driver_location(d_prof2.id, 28.6140, 77.2090, is_online=True)

    # Passenger books ride for Auto category
    ride = Ride(
        service_area_id=service_area.id,
        customer_id=c_prof.id,
        vehicle_category_id=cat_auto.id,
        fare_rule_id=rule_auto.id,
        start_otp="123456",
        status="REQUESTED",
        pickup_address="Connaught Place",
        pickup_latitude=28.6140,
        pickup_longitude=77.2090,
        destination_address="India Gate",
        destination_latitude=28.6129,
        destination_longitude=77.2295,
        estimated_distance_km=2.0,
        estimated_duration_minutes=8.0,
        estimated_fare_cents=4500,
        requested_at=datetime.now(timezone.utc)
    )
    db_session.add(ride)
    await db_session.commit()

    # Run Smart Dispatch
    dispatch_svc = SmartDispatchService(db_session)
    assignment = await dispatch_svc.search_and_dispatch(ride.id, max_rings=2)

    assert assignment is not None
    # Crucial assertion: Must be offered to Driver 2 (Auto Pilot), NOT Driver 1 (Rickshaw Pilot)
    assert assignment.driver_id == d_prof2.id
    assert assignment.driver_id != d_prof1.id


@pytest.mark.asyncio
async def test_rides_api_endpoint_accepts_category_id(db_session):
    """
    Verifies that POST /api/v1/rides/request accepts category_id in JSON payload,
    saves the ride with that category, and returns it in active ride status.
    """
    suffix = uuid.uuid4().hex[:6]
    service_area = (await db_session.execute(select(GeofenceZone).where(GeofenceZone.city_code == "DELHI_NCR", GeofenceZone.zone_type == "SERVICE_AREA"))).scalars().one()

    cat_cargo = VehicleCategory(
        id=f"cat_cargo_{suffix}",
        code=f"CARGO_{suffix}",
        name="E-Cargo Loader",
        sort_order=4,
        is_active=True
    )
    db_session.add(cat_cargo)
    await db_session.flush()

    rule_cargo = FareRule(
        id=f"rule_cargo_{suffix}",
        category_id=cat_cargo.id,
        base_fare_cents=5000,
        per_km_cents=1500,
        per_minute_cents=200,
        minimum_fare_cents=5000,
        is_active=True
    )
    db_session.add(rule_cargo)
    await db_session.flush()

    c_user = User(
        phone_number=f"+91981{suffix}99",
        full_name="Merchant Logistics",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add(c_user)
    await db_session.flush()
    c_prof = CustomerProfile(user_id=c_user.id)
    db_session.add(c_prof)
    await db_session.commit()

    app.dependency_overrides[get_current_customer] = lambda: c_user
    app.dependency_overrides[get_current_user] = lambda: c_user

    try:
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            resp = await client.post(
                "/api/v1/rides/request",
                json={
                    "category_id": cat_cargo.id,
                    "pickup_address": "Okhla Phase 3",
                    "destination_address": "Nehru Place",
                    "pickup_latitude": 28.5355,
                    "pickup_longitude": 77.2732,
                    "destination_latitude": 28.5494,
                    "destination_longitude": 77.2528,
                    "payment_method": "UPI"
                }
            )
            assert resp.status_code == 200, resp.text
            data = resp.json()
            assert data["category_id"] == cat_cargo.id

            # Check active ride endpoint
            active_resp = await client.get("/api/v1/rides/active")
            assert active_resp.status_code == 200
            active_data = active_resp.json()
            assert active_data["active_ride"] is not None
            assert active_data["active_ride"]["category_id"] == cat_cargo.id
    finally:
        app.dependency_overrides.pop(get_current_customer, None)
        app.dependency_overrides.pop(get_current_user, None)


@pytest.mark.asyncio
async def test_invalid_and_inactive_category_error_handling(db_session):
    """
    Verifies that requesting an invalid or inactive category_id returns
    appropriate HTTP 404 and HTTP 400 errors instead of silently defaulting.
    """
    suffix = uuid.uuid4().hex[:6]
    service_area = (await db_session.execute(select(GeofenceZone).where(GeofenceZone.city_code == "DELHI_NCR", GeofenceZone.zone_type == "SERVICE_AREA"))).scalars().one()
    cat_inactive = VehicleCategory(
        id=f"cat_inact_{suffix}",
        code=f"INACTIVE_{suffix}",
        name="Decommissioned Fleet",
        sort_order=99,
        is_active=False
    )
    db_session.add(cat_inactive)
    await db_session.flush()

    c_user = User(
        phone_number=f"+91981{suffix}88",
        full_name="Error Test Passenger",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add(c_user)
    await db_session.flush()
    c_prof = CustomerProfile(user_id=c_user.id)
    db_session.add(c_prof)
    await db_session.commit()

    app.dependency_overrides[get_current_customer] = lambda: c_user
    app.dependency_overrides[get_current_user] = lambda: c_user

    try:
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            # 1. Nonexistent category -> 404
            resp_nonexistent = await client.post(
                "/api/v1/rides/request",
                json={
                    "category_id": "nonexistent-category-uuid-000",
                    "pickup_address": "Kashmere Gate",
                    "destination_address": "Chandni Chowk",
                    "payment_method": "UPI"
                }
            )
            assert resp_nonexistent.status_code == 404, resp_nonexistent.text
            assert "not found" in resp_nonexistent.json()["detail"].lower()

            # 2. Inactive category -> 400
            resp_inactive = await client.post(
                "/api/v1/rides/request",
                json={
                    "category_id": cat_inactive.id,
                    "pickup_address": "Kashmere Gate",
                    "destination_address": "Chandni Chowk",
                    "payment_method": "UPI"
                }
            )
            assert resp_inactive.status_code == 400, resp_inactive.text
            assert "inactive" in resp_inactive.json()["detail"].lower()
    finally:
        app.dependency_overrides.pop(get_current_customer, None)
        app.dependency_overrides.pop(get_current_user, None)


@pytest.mark.asyncio
async def test_ride_request_without_category_rejected_with_400(db_session):
    """
    Verifies MANDATORY BUSINESS RULE: When category_id is omitted from request payload,
    the request is strictly REJECTED with HTTP 400. NO default category or E-Rickshaw fallback.
    """
    suffix = uuid.uuid4().hex[:6]
    service_area = (await db_session.execute(select(GeofenceZone).where(GeofenceZone.city_code == "DELHI_NCR", GeofenceZone.zone_type == "SERVICE_AREA"))).scalars().one()
    cat_default = VehicleCategory(
        id=f"cat_def_{suffix}",
        code="E_RICKSHAW",
        name="Default E-Rickshaw",
        sort_order=1,
        is_active=True
    )
    db_session.add(cat_default)
    await db_session.flush()

    c_user = User(
        phone_number=f"+91981{suffix}77",
        full_name="No Category Passenger",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add(c_user)
    await db_session.flush()
    c_prof = CustomerProfile(user_id=c_user.id)
    db_session.add(c_prof)
    await db_session.commit()

    app.dependency_overrides[get_current_customer] = lambda: c_user
    app.dependency_overrides[get_current_user] = lambda: c_user

    try:
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            # 1. category_id omitted completely
            resp_omitted = await client.post(
                "/api/v1/rides/request",
                json={
                    "pickup_address": "Rajiv Chowk Metro Gate 1",
                    "destination_address": "Barakhamba Road",
                    "payment_method": "UPI"
                }
            )
            assert resp_omitted.status_code == 400, resp_omitted.text
            assert "category_id is required" in resp_omitted.json()["detail"].lower()

            # 2. category_id passed as None / null
            resp_null = await client.post(
                "/api/v1/rides/request",
                json={
                    "category_id": None,
                    "pickup_address": "Rajiv Chowk Metro Gate 1",
                    "destination_address": "Barakhamba Road",
                    "payment_method": "UPI"
                }
            )
            assert resp_null.status_code == 400, resp_null.text
            assert "category_id is required" in resp_null.json()["detail"].lower()

            # 3. category_id passed as empty string or whitespace
            resp_empty = await client.post(
                "/api/v1/rides/request",
                json={
                    "category_id": "   ",
                    "pickup_address": "Rajiv Chowk Metro Gate 1",
                    "destination_address": "Barakhamba Road",
                    "payment_method": "UPI"
                }
            )
            assert resp_empty.status_code == 400, resp_empty.text
            assert "category_id is required" in resp_empty.json()["detail"].lower()
    finally:
        app.dependency_overrides.pop(get_current_customer, None)
        app.dependency_overrides.pop(get_current_user, None)


@pytest.mark.asyncio
async def test_ride_service_rejects_none_or_empty_category(db_session):
    """
    Verifies RideService directly raises HTTP 400 when category_id is None or empty.
    """
    suffix = uuid.uuid4().hex[:6]
    service_area = (await db_session.execute(select(GeofenceZone).where(GeofenceZone.city_code == "DELHI_NCR", GeofenceZone.zone_type == "SERVICE_AREA"))).scalars().one()
    c_user = User(
        phone_number=f"+91981{suffix}91",
        full_name="Service Validation User",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add(c_user)
    await db_session.flush()
    db_session.add(CustomerProfile(user_id=c_user.id))
    await db_session.commit()

    ride_svc = RideService(db=db_session)
    # 1. category_id=None
    with pytest.raises(Exception) as exc_none:
        await ride_svc.create_ride_request(
            customer_user_id=c_user.id,
            pickup_address="Connaught Place",
            pickup_latitude=28.6140,
            pickup_longitude=77.2090,
            destination_address="India Gate",
            destination_latitude=28.6129,
            destination_longitude=77.2295,
            category_id=None
        )
    assert "mandatory" in str(exc_none.value.detail).lower()

    # 2. category_id=""
    with pytest.raises(Exception) as exc_empty:
        await ride_svc.create_ride_request(
            customer_user_id=c_user.id,
            pickup_address="Connaught Place",
            pickup_latitude=28.6140,
            pickup_longitude=77.2090,
            destination_address="India Gate",
            destination_latitude=28.6129,
            destination_longitude=77.2295,
            category_id="  "
        )
    assert "mandatory" in str(exc_empty.value.detail).lower()


@pytest.mark.asyncio
async def test_driver_registration_binds_dynamic_category(db_session):
    """
    Verifies that a driver registering with a dynamic category (e.g. E_AUTO)
    has their Vehicle.category_id correctly persisted in the database.
    """
    from backend.application.services.auth_service import AuthService

    suffix = uuid.uuid4().hex[:6].upper()
    cat_bike = VehicleCategory(
        id=f"cat_bike_{suffix}",
        code=f"E_BIKE_{suffix}",
        name="Electric Bike Taxi",
        sort_order=3,
        is_active=True
    )
    db_session.add(cat_bike)
    await db_session.commit()

    auth_svc = AuthService(db_session)
    reg_res = await auth_svc.register_driver_account(
        phone_number=f"+91{6_000_000_000 + (int(suffix, 16) % 4_000_000_000):010d}",
        full_name="Bike Captain",
        password="ValidPassword123!",
        license_number=f"DL-BIKE-{suffix}",
        vehicle_type=cat_bike.code
    )

    assert "access_token" in reg_res
    assert reg_res["vehicle"] is None
    user_id = reg_res["user"]["id"]

    # Category intent is persisted; fleet vehicle creation and assignment remain an Admin workflow.
    d_prof = (await db_session.execute(select(DriverProfile).where(DriverProfile.user_id == user_id))).scalar_one()
    assert d_prof.license_number == f"DL-BIKE-{suffix}"
    assert d_prof.preferred_vehicle_categories == cat_bike.code
    assignment = (await db_session.execute(
        select(DriverVehicleAssignment).where(DriverVehicleAssignment.driver_id == d_prof.id)
    )).scalar_one_or_none()
    assert assignment is None


@pytest.mark.asyncio
async def test_offline_or_unapproved_driver_excluded_from_dispatch(db_session):
    """
    Verifies that drivers matching the requested category are excluded if
    they are unapproved (PENDING) or offline.
    """
    suffix = uuid.uuid4().hex[:6]
    service_area = (await db_session.execute(select(GeofenceZone).where(GeofenceZone.city_code == "DELHI_NCR", GeofenceZone.zone_type == "SERVICE_AREA"))).scalars().one()

    cat_test = VehicleCategory(
        id=f"cat_test_{suffix}",
        code=f"EXCL_TEST_{suffix}",
        name="Exclusion Test Category",
        sort_order=1,
        is_active=True
    )
    db_session.add(cat_test)
    await db_session.flush()

    rule_test = FareRule(
        id=f"rule_t_{suffix}",
        category_id=cat_test.id,
        base_fare_cents=2500,
        per_km_cents=800,
        per_minute_cents=100,
        minimum_fare_cents=2500,
        is_active=True
    )
    db_session.add(rule_test)
    await db_session.flush()

    c_user = User(phone_number=f"+91981{suffix}61", full_name="Customer Excl", role="customer", is_active=True, is_verified=True)
    db_session.add(c_user)
    await db_session.flush()
    c_prof = CustomerProfile(user_id=c_user.id)
    db_session.add(c_prof)
    await db_session.flush()

    # Driver 1: Matching category BUT PENDING approval
    d_user1 = User(phone_number=f"+91981{suffix}62", full_name="Pending Driver", role="driver", is_active=True, is_verified=True)
    db_session.add(d_user1)
    await db_session.flush()
    d_prof1 = DriverProfile(id=f"dp_pend_{suffix}", user_id=d_user1.id, license_number=f"DL-P-{suffix}", approval_status="PENDING")
    db_session.add(d_prof1)
    await db_session.flush()
    db_session.add(DriverServiceAreaAssignment(driver_id=d_prof1.id, service_area_id=service_area.id))
    db_session.add(DriverAvailability(driver_id=d_prof1.id, is_online=True, is_busy=False))
    await db_session.flush()
    veh1 = Vehicle(id=f"vp_{suffix}", category_id=cat_test.id, make="Hero", model="Electric", year=2024, license_plate=f"DL-1P-{suffix}")
    db_session.add(veh1)
    await db_session.flush()
    db_session.add(DriverVehicleAssignment(driver_id=d_prof1.id, vehicle_id=veh1.id, is_active=True))
    db_session.add(DriverAvailability(driver_id=d_prof1.id, is_online=True, is_busy=False))

    # Driver 2: Matching category & APPROVED BUT OFFLINE
    d_user2 = User(phone_number=f"+91981{suffix}63", full_name="Offline Driver", role="driver", is_active=True, is_verified=True)
    db_session.add(d_user2)
    await db_session.flush()
    d_prof2 = DriverProfile(id=f"dp_off_{suffix}", user_id=d_user2.id, license_number=f"DL-O-{suffix}", approval_status="APPROVED")
    db_session.add(d_prof2)
    await db_session.flush()
    db_session.add(DriverServiceAreaAssignment(driver_id=d_prof2.id, service_area_id=service_area.id))
    db_session.add(DriverAvailability(driver_id=d_prof2.id, is_online=True, is_busy=False))
    await db_session.flush()
    veh2 = Vehicle(id=f"vo_{suffix}", category_id=cat_test.id, make="Hero", model="Electric", year=2024, license_plate=f"DL-1O-{suffix}")
    db_session.add(veh2)
    await db_session.flush()
    db_session.add(DriverVehicleAssignment(driver_id=d_prof2.id, vehicle_id=veh2.id, is_active=True))
    db_session.add(DriverAvailability(driver_id=d_prof2.id, is_online=False, is_busy=False))

    await db_session.commit()

    live_loc = LiveLocationService(db=db_session)
    await live_loc.ingest_driver_location(d_prof1.id, 28.6140, 77.2090, is_online=True)
    await live_loc.ingest_driver_location(d_prof2.id, 28.6140, 77.2090, is_online=False)

    ride = Ride(
        service_area_id=service_area.id,
        customer_id=c_prof.id,
        vehicle_category_id=cat_test.id,
        fare_rule_id=rule_test.id,
        start_otp="654321",
        status="REQUESTED",
        pickup_address="Saket",
        pickup_latitude=28.6140,
        pickup_longitude=77.2090,
        destination_address="Hauz Khas",
        destination_latitude=28.6129,
        destination_longitude=77.2295,
        estimated_distance_km=2.0,
        estimated_duration_minutes=8.0,
        estimated_fare_cents=3500,
        requested_at=datetime.now(timezone.utc)
    )
    db_session.add(ride)
    await db_session.commit()

    dispatch_svc = SmartDispatchService(db_session)
    assignment = await dispatch_svc.search_and_dispatch(ride.id, max_rings=2)

    # Neither pending nor offline driver can be offered the ride
    assert assignment is None
    # Verify ride transitions to EXPIRED
    await db_session.refresh(ride)
    assert ride.status == "EXPIRED"


@pytest.mark.asyncio
async def test_unassigned_driver_never_receives_categorized_ride(db_session):
    """
    Point 1 Verification:
    Drivers with NO vehicle assigned (or unassigned vehicle) must NEVER receive
    ride offers for a categorized ride.
    """
    suffix = uuid.uuid4().hex[:6]
    service_area = (await db_session.execute(select(GeofenceZone).where(GeofenceZone.city_code == "DELHI_NCR", GeofenceZone.zone_type == "SERVICE_AREA"))).scalars().one()
    cat_cab = VehicleCategory(id=f"cat_cab_{suffix}", code=f"CAB_{suffix.upper()}", name="Cab Fleet", is_active=True)
    rule_cab = FareRule(id=f"rule_cab_{suffix}", category_id=cat_cab.id, base_fare_cents=5000, per_km_cents=1500, per_minute_cents=200, minimum_fare_cents=5000)
    db_session.add_all([cat_cab, rule_cab])
    await db_session.flush()

    c_user = User(phone_number=f"+91981{suffix}70", full_name="Cab Customer", role="customer", is_active=True, is_verified=True)
    db_session.add(c_user)
    await db_session.flush()
    c_prof = CustomerProfile(user_id=c_user.id)
    db_session.add(c_prof)

    # Driver: Approved, Online, Fresh Location, BUT NO VEHICLE ASSIGNED
    d_user = User(phone_number=f"+91981{suffix}71", full_name="Unassigned Driver", role="driver", is_active=True, is_verified=True)
    db_session.add(d_user)
    await db_session.flush()
    d_prof = DriverProfile(id=f"dp_unassign_{suffix}", user_id=d_user.id, license_number=f"DL-U-{suffix}", approval_status="APPROVED")
    db_session.add(d_prof)
    await db_session.flush()
    db_session.add(DriverServiceAreaAssignment(driver_id=d_prof.id, service_area_id=service_area.id))
    await db_session.flush()
    db_session.add(DriverAvailability(driver_id=d_prof.id, is_online=True, is_busy=False))
    await db_session.commit()

    live_loc = LiveLocationService(db=db_session)
    await live_loc.ingest_driver_location(d_prof.id, 28.6140, 77.2090, is_online=True)

    ride = Ride(
        service_area_id=service_area.id,
        customer_id=c_prof.id,
        vehicle_category_id=cat_cab.id,
        fare_rule_id=rule_cab.id,
        start_otp="778899",
        status="REQUESTED",
        pickup_address="Rajiv Chowk",
        pickup_latitude=28.6140,
        pickup_longitude=77.2090,
        destination_address="Airport",
        destination_latitude=28.5562,
        destination_longitude=77.1000,
        estimated_distance_km=15.0,
        estimated_duration_minutes=35.0,
        estimated_fare_cents=25000,
        requested_at=datetime.now(timezone.utc)
    )
    db_session.add(ride)
    await db_session.commit()

    dispatch_svc = SmartDispatchService(db_session)
    assignment = await dispatch_svc.search_and_dispatch(ride.id, max_rings=2)

    # Unassigned driver must NOT be matched
    assert assignment is None
    await db_session.refresh(ride)
    assert ride.status == "EXPIRED"


@pytest.mark.asyncio
async def test_accept_ride_atomic_validates_category_match(db_session):
    """
    Verifies that when a driver accepts a ride (accept_ride_atomic):
    1. If the driver's vehicle category does NOT match the ride category, it is REJECTED with HTTP 400.
    2. If the driver's vehicle category matches, acceptance SUCCEEDS and binds driver and vehicle.
    """
    suffix = uuid.uuid4().hex[:6]
    service_area = (await db_session.execute(select(GeofenceZone).where(GeofenceZone.city_code == "DELHI_NCR", GeofenceZone.zone_type == "SERVICE_AREA"))).scalars().one()
    cat_bike = VehicleCategory(id=f"cat_bk_{suffix}", code=f"BIKE_{suffix.upper()}", name="Speed Bike", sort_order=1, is_active=True)
    cat_cab = VehicleCategory(id=f"cat_cb_{suffix}", code=f"CAB_{suffix.upper()}", name="City Cab", sort_order=2, is_active=True)
    rule_cab = FareRule(id=f"rule_cb_{suffix}", category_id=cat_cab.id, base_fare_cents=5000, per_km_cents=1200, per_minute_cents=200, minimum_fare_cents=5000)
    db_session.add_all([cat_bike, cat_cab, rule_cab])
    await db_session.flush()

    # Passenger
    c_user = User(phone_number=f"+91981{suffix}81", full_name="Passenger Test", role="customer", is_active=True, is_verified=True)
    db_session.add(c_user)
    await db_session.flush()
    c_prof = CustomerProfile(user_id=c_user.id)
    db_session.add(c_prof)
    await db_session.flush()

    # Driver 1: Bike driver
    d_user1 = User(phone_number=f"+91981{suffix}82", full_name="Bike Pilot", role="driver", is_active=True, is_verified=True)
    db_session.add(d_user1)
    await db_session.flush()
    d_prof1 = DriverProfile(id=f"dp_bike_{suffix}", user_id=d_user1.id, license_number=f"DL-BK-{suffix}", approval_status="APPROVED")
    db_session.add(d_prof1)
    await db_session.flush()
    db_session.add(DriverServiceAreaAssignment(driver_id=d_prof1.id, service_area_id=service_area.id))
    await db_session.flush()
    veh1 = Vehicle(id=f"v_bike_{suffix}", category_id=cat_bike.id, make="Hero", model="Splendor", year=2024, license_plate=f"DL-BK-{suffix}")
    db_session.add(veh1)
    await db_session.flush()
    db_session.add(DriverVehicleAssignment(driver_id=d_prof1.id, vehicle_id=veh1.id, is_active=True))

    # Driver 2: Cab driver
    d_user2 = User(phone_number=f"+91981{suffix}83", full_name="Cab Pilot", role="driver", is_active=True, is_verified=True)
    db_session.add(d_user2)
    await db_session.flush()
    d_prof2 = DriverProfile(id=f"dp_cab_{suffix}", user_id=d_user2.id, license_number=f"DL-CB-{suffix}", approval_status="APPROVED")
    db_session.add(d_prof2)
    await db_session.flush()
    db_session.add(DriverServiceAreaAssignment(driver_id=d_prof2.id, service_area_id=service_area.id))
    await db_session.flush()
    veh2 = Vehicle(id=f"v_cab_{suffix}", category_id=cat_cab.id, make="Maruti", model="Dzire", year=2024, license_plate=f"DL-CB-{suffix}")
    db_session.add(veh2)
    await db_session.flush()
    db_session.add(DriverVehicleAssignment(driver_id=d_prof2.id, vehicle_id=veh2.id, is_active=True))
    await db_session.commit()

    # Create a Cab Ride
    ride = Ride(
        service_area_id=service_area.id,
        customer_id=c_prof.id,
        vehicle_category_id=cat_cab.id,
        fare_rule_id=rule_cab.id,
        start_otp="445566",
        status="REQUESTED",
        pickup_address="Rajiv Chowk",
        pickup_latitude=28.6140,
        pickup_longitude=77.2090,
        destination_address="Airport",
        destination_latitude=28.5562,
        destination_longitude=77.1000,
        estimated_distance_km=15.0,
        estimated_duration_minutes=35.0,
        estimated_fare_cents=25000,
        requested_at=datetime.now(timezone.utc)
    )
    db_session.add(ride)
    await db_session.commit()

    ride_svc = RideService(db=db_session)

    # Both drivers have a persisted live offer; eligibility is checked by the
    # same authoritative acceptance service used by the production API.
    from backend.domain.models import RideAssignment
    from datetime import timedelta
    offer_expiry = datetime.now(timezone.utc) + timedelta(minutes=5)
    db_session.add_all([
        RideAssignment(id=f"asgn_bike_{suffix}", ride_id=ride.id, driver_id=d_prof1.id,
                       status="OFFERED", offered_at=datetime.now(timezone.utc), expires_at=offer_expiry),
        RideAssignment(id=f"asgn_{suffix}", ride_id=ride.id, driver_id=d_prof2.id,
                       status="OFFERED", offered_at=datetime.now(timezone.utc), expires_at=offer_expiry),
    ])
    await db_session.commit()

    await LiveLocationService(db=db_session).ingest_driver_location(
        d_prof1.id, 28.6140, 77.2090, is_online=True
    )

    # 1. Driver 1 (Bike) tries to accept Cab ride -> rejected for category mismatch.
    with pytest.raises(Exception) as exc_mismatch:
        await ride_svc.accept_ride_atomic(d_user1.id, ride.id)
    assert "category" in str(exc_mismatch.value.detail).lower()

    # Driver 2 has a valid category but no fresh GPS yet.
    with pytest.raises(Exception) as stale_smart_gps_error:
        await SmartDispatchService(db_session).accept_offer(d_user2.id, ride.id)
    assert "Fresh driver GPS" in str(stale_smart_gps_error.value.detail)
    with pytest.raises(Exception) as stale_gps_error:
        await ride_svc.accept_ride_atomic(d_user2.id, ride.id)
    assert "Fresh driver GPS" in str(stale_gps_error.value.detail)

    # Fresh GPS must be inside the ride's active service polygon.
    await LiveLocationService(db=db_session).ingest_driver_location(
        d_prof2.id, 28.6140, 77.2090, is_online=True
    )

    # 2. Driver 2 (Cab) accepts Cab ride -> SUCCEEDS
    res = await ride_svc.accept_ride_atomic(d_user2.id, ride.id)
    assert res["status"] in ["CONFIRMED", "ACCEPTED", "SCHEDULED", "ASSIGNED"]

    await db_session.refresh(ride)
    assert ride.assigned_driver_id == d_prof2.id
    assert ride.vehicle_id == veh2.id


@pytest.mark.asyncio
async def test_fare_estimate_id_is_full_valid_uuid(db_session):
    """
    Point 3 Verification:
    Fare Estimate ID must be a full valid UUID (36 chars), avoiding collisions and truncations.
    """
    suffix = uuid.uuid4().hex[:6]
    service_area = (await db_session.execute(select(GeofenceZone).where(GeofenceZone.city_code == "DELHI_NCR", GeofenceZone.zone_type == "SERVICE_AREA"))).scalars().one()
    cat = VehicleCategory(id=f"cat_est_{suffix}", code=f"CAT_EST_{suffix.upper()}", name="Estimate Cat", is_active=True)
    rule = FareRule(id=f"rule_est_{suffix}", category_id=cat.id, base_fare_cents=3000, per_km_cents=800, per_minute_cents=150, minimum_fare_cents=3000)
    db_session.add_all([cat, rule])
    await db_session.flush()

    c_user = User(phone_number=f"+91981{suffix}90", full_name="Estimate Passenger", role="customer", is_active=True, is_verified=True)
    db_session.add(c_user)
    await db_session.flush()
    c_prof = CustomerProfile(user_id=c_user.id)
    db_session.add(c_prof)
    await db_session.commit()

    ride_svc = RideService(db=db_session)
    ride = await ride_svc.create_ride_request(
        customer_user_id=c_user.id,
        pickup_address="Noida 18",
        pickup_latitude=28.5700,
        pickup_longitude=77.3200,
        destination_address="Botanical Garden",
        destination_latitude=28.5640,
        destination_longitude=77.3340,
        category_id=cat.id,
        estimate_id=None  # triggers auto-generation
    )

    # Query the generated FareEstimate
    stmt = select(FareEstimate).where(FareEstimate.category_id == cat.id)
    est = (await db_session.execute(stmt)).scalars().first()
    assert est is not None
    assert len(est.id) == 36
    # Verify it is parseable as a valid UUID
    parsed_uuid = uuid.UUID(est.id)
    assert str(parsed_uuid) == est.id



@pytest.mark.asyncio
async def test_service_area_candidate_guard_excludes_restricted_and_outside_locations(db_session):
    service_area = (await db_session.execute(
        select(GeofenceZone).where(
            GeofenceZone.city_code == "DELHI_NCR",
            GeofenceZone.zone_type == "SERVICE_AREA",
            GeofenceZone.is_active.is_(True),
        )
    )).scalars().one()
    restricted = GeofenceZone(
        name="Synthetic restricted dispatch test zone",
        zone_type="RESTRICTED",
        city_code=None,
        coordinates_json='[{"lat":28.60,"lng":77.20},{"lat":28.60,"lng":77.22},{"lat":28.62,"lng":77.22},{"lat":28.62,"lng":77.20}]',
        is_active=True,
    )
    db_session.add(restricted)
    await db_session.flush()

    from backend.application.services.service_area_service import ServiceAreaService
    service = ServiceAreaService(db_session)
    _, service_polygon, restricted_polygons = await service.load_area_boundary(service_area.id)

    assert service.location_is_inside_boundary(28.70, 77.30, service_polygon, restricted_polygons)
    assert not service.location_is_inside_boundary(28.61, 77.21, service_polygon, restricted_polygons)
    assert not service.location_is_inside_boundary(30.00, 80.00, service_polygon, restricted_polygons)
