"""
Unit and lifecycle tests for SmartDispatchService.
"""
import uuid
import pytest
from fastapi import HTTPException
from datetime import datetime, timezone
from sqlalchemy import select

from backend.domain.models import (
    Ride, RideAssignment, DriverProfile, DriverAvailability,
    User, CustomerProfile, VehicleCategory, FareRule, Vehicle, DriverVehicleAssignment,
    GeofenceZone, DriverServiceAreaAssignment
)
from backend.application.services.smart_dispatch_service import SmartDispatchService
from backend.application.services.live_location_service import LiveLocationService
from backend.infrastructure.cache.redis_client import redis_client


@pytest.mark.asyncio
async def test_smart_dispatch_lifecycle(db_session):
    suffix = uuid.uuid4().hex[:6]
    service_area = (await db_session.execute(select(GeofenceZone).where(GeofenceZone.city_code == "DELHI_NCR", GeofenceZone.zone_type == "SERVICE_AREA"))).scalars().one()

    # Category
    cat = VehicleCategory(
        id=str(uuid.uuid4()),
        code=f"H3_E_RICKSHAW_{suffix}",
        name="H3 Smart E-Rickshaw"
    )
    db_session.add(cat)
    await db_session.flush()

    # Fare rule
    fare_rule = FareRule(
        id=str(uuid.uuid4()),
        category_id=cat.id,
        base_fare_cents=3000,
        per_km_cents=1000,
        per_minute_cents=200,
        minimum_fare_cents=3000
    )
    db_session.add(fare_rule)
    await db_session.flush()

    # 1. Create test driver user & profile
    driver_user = User(
        phone_number=f"+91981{suffix}01",
        full_name="Rajesh Kumar",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    driver_profile = DriverProfile(
        id=str(uuid.uuid4()),
        user_id=driver_user.id,
        license_number=f"DL-H3-{suffix}",
        approval_status="APPROVED",
        rating_average=4.9
    )
    db_session.add(driver_profile)
    await db_session.flush()
    db_session.add(DriverServiceAreaAssignment(driver_id=driver_profile.id, service_area_id=service_area.id))
    await db_session.flush()

    driver_avail = DriverAvailability(
        driver_id=driver_profile.id,
        is_online=True,
        is_busy=False
    )
    db_session.add(driver_avail)
    await db_session.flush()

    veh = Vehicle(
        id=str(uuid.uuid4()),
        category_id=cat.id,
        make="Mayuri",
        model="Deluxe",
        year=2024,
        license_plate=f"DL-H3-{suffix}",
        is_active=True
    )
    db_session.add(veh)
    await db_session.flush()
    db_session.add(DriverVehicleAssignment(driver_id=driver_profile.id, vehicle_id=veh.id, is_active=True))
    await db_session.flush()

    # 2. Ingest driver location in Central Delhi (28.6139, 77.2090)
    live_loc = LiveLocationService(db=db_session)
    await live_loc.ingest_driver_location(
        driver_id=driver_profile.id,
        latitude=28.6250,
        longitude=77.2200,
        is_online=True,
        is_busy=False
    )

    # 3. Create passenger customer profile
    customer_user = User(
        phone_number=f"+91981{suffix}02",
        full_name="Pooja Sharma",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add(customer_user)
    await db_session.flush()

    customer_profile = CustomerProfile(user_id=customer_user.id)
    db_session.add(customer_profile)
    await db_session.flush()

    now = datetime.now(timezone.utc)
    ride = Ride(
        service_area_id=service_area.id,
        customer_id=customer_profile.id,
        vehicle_category_id=cat.id,
        fare_rule_id=fare_rule.id,
        start_otp="4821",
        status="REQUESTED",
        pickup_address="Connaught Place, New Delhi",
        pickup_latitude=28.6150,
        pickup_longitude=77.2100,
        destination_address="India Gate, New Delhi",
        destination_latitude=28.6129,
        destination_longitude=77.2295,
        estimated_distance_km=3.5,
        estimated_duration_minutes=12.0,
        estimated_fare_cents=15000,
        requested_at=now
    )
    db_session.add(ride)
    await db_session.commit()

    # 4. Run Smart Dispatch
    dispatch_svc = SmartDispatchService(db_session)
    assignment = await dispatch_svc.search_and_dispatch(ride.id, max_rings=2)

    assert assignment is not None
    assert assignment.status == "OFFERED"
    assert assignment.driver_id == driver_profile.id

    # Verify atomic lock in Redis
    assert await redis_client.exists(f"dispatch:lock:{driver_profile.id}") is True
    # Verify offer safety key
    assert await redis_client.exists(f"dispatch:offer:{ride.id}:{driver_profile.id}") is True

    # 5. Driver Accepts Offer
    res = await dispatch_svc.accept_offer(driver_user_id=driver_user.id, ride_id=ride.id)
    assert res["status"] == "ACCEPTED"

    # Refresh Ride & Assignment
    r_updated = (await db_session.execute(select(Ride).where(Ride.id == ride.id))).scalar_one()
    assert r_updated.status == "ASSIGNED"
    assert r_updated.assigned_driver_id == driver_profile.id

    a_updated = (await db_session.execute(select(RideAssignment).where(RideAssignment.id == assignment.id))).scalar_one()
    assert a_updated.status == "ACCEPTED"
    assert a_updated.is_accepted is True

    # Verify driver availability set to busy
    avail_updated = (await db_session.execute(select(DriverAvailability).where(DriverAvailability.driver_id == driver_profile.id))).scalar_one()
    assert avail_updated.is_busy is True

    # Verify lock released
    assert await redis_client.exists(f"dispatch:lock:{driver_profile.id}") is False


@pytest.mark.asyncio
async def test_smart_dispatch_rejection_and_exhaustion(db_session):
    suffix = uuid.uuid4().hex[:6]
    service_area = (await db_session.execute(select(GeofenceZone).where(GeofenceZone.city_code == "DELHI_NCR", GeofenceZone.zone_type == "SERVICE_AREA"))).scalars().one()

    cat = VehicleCategory(
        id=str(uuid.uuid4()),
        code=f"H3_E_RICKSHAW_{suffix}",
        name="H3 Smart E-Rickshaw"
    )
    db_session.add(cat)
    await db_session.flush()

    fare_rule = FareRule(
        id=str(uuid.uuid4()),
        category_id=cat.id,
        base_fare_cents=3000,
        per_km_cents=1000,
        per_minute_cents=200,
        minimum_fare_cents=3000
    )
    db_session.add(fare_rule)
    await db_session.flush()

    # 1. Driver setup
    driver_user = User(
        phone_number=f"+91981{suffix}03",
        full_name="Vikram Singh",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    driver_profile = DriverProfile(
        id=str(uuid.uuid4()),
        user_id=driver_user.id,
        license_number=f"DL-H3-{suffix}",
        approval_status="APPROVED",
        rating_average=4.7
    )
    db_session.add(driver_profile)
    await db_session.flush()
    db_session.add(DriverServiceAreaAssignment(driver_id=driver_profile.id, service_area_id=service_area.id))
    await db_session.flush()

    driver_avail = DriverAvailability(
        driver_id=driver_profile.id,
        is_online=True,
        is_busy=False
    )
    db_session.add(driver_avail)
    await db_session.flush()

    veh = Vehicle(
        id=str(uuid.uuid4()),
        category_id=cat.id,
        make="Mayuri",
        model="Deluxe",
        year=2024,
        license_plate=f"DL-H3-{suffix}",
        is_active=True
    )
    db_session.add(veh)
    await db_session.flush()
    db_session.add(DriverVehicleAssignment(driver_id=driver_profile.id, vehicle_id=veh.id, is_active=True))
    await db_session.flush()

    live_loc = LiveLocationService(db=db_session)
    await live_loc.ingest_driver_location(
        driver_id=driver_profile.id,
        latitude=28.6250,
        longitude=77.2200,
        is_online=True,
        is_busy=False
    )

    # 2. Passenger & Ride setup
    customer_user = User(
        phone_number=f"+91981{suffix}04",
        full_name="Amit Patel",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add(customer_user)
    await db_session.flush()

    customer_profile = CustomerProfile(user_id=customer_user.id)
    db_session.add(customer_profile)
    await db_session.flush()

    ride = Ride(
        service_area_id=service_area.id,
        customer_id=customer_profile.id,
        vehicle_category_id=cat.id,
        fare_rule_id=fare_rule.id,
        start_otp="9921",
        status="REQUESTED",
        pickup_address="Barakhamba Road, New Delhi",
        pickup_latitude=28.6280,
        pickup_longitude=77.2250,
        destination_address="Khan Market, New Delhi",
        destination_latitude=28.5980,
        destination_longitude=77.2300,
        estimated_distance_km=4.2,
        estimated_duration_minutes=15.0,
        estimated_fare_cents=18000
    )
    db_session.add(ride)
    await db_session.commit()

    dispatch_svc = SmartDispatchService(db_session)
    assignment = await dispatch_svc.search_and_dispatch(ride.id, max_rings=2)
    assert assignment is not None
    assert assignment.driver_id == driver_profile.id

    # 3. Driver Rejects Offer
    rej_res = await dispatch_svc.reject_offer(
        driver_user_id=driver_user.id,
        ride_id=ride.id,
        reason="TOO_FAR"
    )
    assert rej_res["status"] == "REJECTED"

    # Cooldown must be active for this driver and ride
    assert await redis_client.exists(f"cooldown:{driver_profile.id}:{ride.id}") is True

    # 4. Immediate cascade when no other drivers exist transitions ride to EXPIRED
    await dispatch_svc.search_and_dispatch(ride.id, max_rings=2)

    r_expired = (await db_session.execute(select(Ride).where(Ride.id == ride.id))).scalar_one()
    assert r_expired.status == "EXPIRED"


async def _create_dispatch_fixture(db_session):
    """Build a real service-area ride and one fully eligible driver without dispatching."""
    suffix = uuid.uuid4().hex[:6]
    service_area = (
        await db_session.execute(
            select(GeofenceZone).where(
                GeofenceZone.city_code == "DELHI_NCR",
                GeofenceZone.zone_type == "SERVICE_AREA",
            )
        )
    ).scalars().one()

    category = VehicleCategory(
        id=f"dispatch_cat_{suffix}",
        code=f"DISPATCH_{suffix}",
        name="Dispatch Test E-Rickshaw",
        is_active=True,
    )
    fare_rule = FareRule(
        id=f"dispatch_fare_{suffix}",
        category_id=category.id,
        base_fare_cents=3000,
        per_km_cents=1000,
        per_minute_cents=200,
        minimum_fare_cents=3000,
        is_active=True,
    )
    driver_user = User(
        phone_number=f"+91981{suffix}11",
        full_name="Dispatch Eligibility Driver",
        role="driver",
        is_active=True,
        is_verified=True,
    )
    db_session.add_all([category, fare_rule, driver_user])
    await db_session.flush()

    driver = DriverProfile(
        id=f"dispatch_driver_{suffix}",
        user_id=driver_user.id,
        license_number=f"DL-DISPATCH-{suffix}",
        approval_status="APPROVED",
    )
    vehicle = Vehicle(
        id=f"dispatch_vehicle_{suffix}",
        category_id=category.id,
        make="Test",
        model="E-Rickshaw",
        year=2025,
        license_plate=f"DL-DSP-{suffix}",
        is_active=True,
    )
    availability = DriverAvailability(
        driver_id=driver.id,
        is_online=True,
        is_busy=False,
    )
    db_session.add_all([driver, vehicle, availability])
    await db_session.flush()
    db_session.add_all([
        DriverServiceAreaAssignment(
            driver_id=driver.id,
            service_area_id=service_area.id,
            is_active=True,
        ),
        DriverVehicleAssignment(
            driver_id=driver.id,
            vehicle_id=vehicle.id,
            is_active=True,
        ),
    ])

    customer_user = User(
        phone_number=f"+91981{suffix}12",
        full_name="Dispatch Test Customer",
        role="customer",
        is_active=True,
        is_verified=True,
    )
    db_session.add(customer_user)
    await db_session.flush()
    customer = CustomerProfile(user_id=customer_user.id)
    db_session.add(customer)
    await db_session.flush()

    ride = Ride(
        service_area_id=service_area.id,
        customer_id=customer.id,
        vehicle_category_id=category.id,
        fare_rule_id=fare_rule.id,
        start_otp="123456",
        status="REQUESTED",
        pickup_address="Connaught Place, New Delhi",
        pickup_latitude=28.6150,
        pickup_longitude=77.2100,
        destination_address="India Gate, New Delhi",
        destination_latitude=28.6129,
        destination_longitude=77.2295,
        estimated_distance_km=3.5,
        estimated_duration_minutes=12.0,
        estimated_fare_cents=15000,
        requested_at=datetime.now(timezone.utc),
    )
    db_session.add(ride)
    await db_session.commit()

    await LiveLocationService(db=db_session).ingest_driver_location(
        driver_id=driver.id,
        latitude=28.6250,
        longitude=77.2200,
        is_online=True,
        is_busy=False,
    )
    return driver_user, driver, vehicle, availability, ride


@pytest.mark.asyncio
@pytest.mark.parametrize("eligibility_change", ["approval", "vehicle", "availability"])
async def test_dispatch_offer_acceptance_revalidates_current_driver_eligibility(
    db_session, eligibility_change
):
    driver_user, driver, vehicle, availability, ride = await _create_dispatch_fixture(db_session)
    dispatch = SmartDispatchService(db_session)
    offer = await dispatch.search_and_dispatch(ride.id, max_rings=2)

    assert offer is not None
    assert offer.driver_id == driver.id
    assert offer.status == "OFFERED"

    if eligibility_change == "approval":
        driver.approval_status = "PENDING"
    elif eligibility_change == "vehicle":
        vehicle.is_active = False
    else:
        availability.is_online = False
    await db_session.commit()

    with pytest.raises(HTTPException) as exc_info:
        await dispatch.accept_offer(driver_user_id=driver_user.id, ride_id=ride.id)

    assert exc_info.value.status_code in (403, 409)
    await db_session.refresh(ride)
    await db_session.refresh(offer)
    assert ride.status == "REQUESTED"
    assert ride.assigned_driver_id is None
    assert offer.status == "OFFERED"


@pytest.mark.asyncio
async def test_smart_dispatch_excludes_inactive_driver_accounts(db_session):
    driver_user, driver, _, _, ride = await _create_dispatch_fixture(db_session)
    driver_user.is_active = False
    await db_session.commit()

    dispatch = SmartDispatchService(db_session)
    offer = await dispatch.search_and_dispatch(ride.id, max_rings=2)

    assert offer is None
    await db_session.refresh(ride)
    assert ride.status == "EXPIRED"
    assignments = (
        await db_session.execute(
            select(RideAssignment).where(RideAssignment.ride_id == ride.id)
        )
    ).scalars().all()
    assert assignments == []
