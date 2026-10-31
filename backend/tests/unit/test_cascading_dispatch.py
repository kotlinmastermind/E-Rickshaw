import pytest
import uuid
import json
from datetime import datetime, timezone, timedelta
from backend.application.services.cascading_dispatch_service import CascadingDispatchService
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, DriverAvailability,
    DriverLocation, VehicleCategory,
    Ride, RideAssignment, RideStatusEvent
)
from backend.infrastructure.cache.redis_client import redis_client


@pytest.mark.asyncio
async def test_driver_timeout_cascades_to_driver_b(db_session):
    """
    Test 1: Driver A dispatch timeout / decline triggers immediate cascading re-queue
    and offers the ride to eligible Driver B.
    """
    from sqlalchemy import update
    # Reset existing ambient driver availabilities so test is strictly deterministic
    await db_session.execute(update(DriverAvailability).values(is_online=False))
    await db_session.flush()

    service = CascadingDispatchService(db_session)
    suffix = uuid.uuid4().hex[:6]

    # Category specific to this test
    cat = VehicleCategory(
        id=str(uuid.uuid4()),
        code=f"ROYAL_LUX_{suffix}",
        name="Royal Sovereign Fleet"
    )
    db_session.add(cat)
    await db_session.flush()

    # 1. Create Customer
    customer_user = User(
        phone_number=f"+91981{suffix}01",
        full_name="VIP Commuter",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add(customer_user)
    await db_session.flush()

    customer_profile = CustomerProfile(user_id=customer_user.id)
    db_session.add(customer_profile)
    await db_session.flush()

    # 2. Create Driver A & Driver B
    driver_a_user = User(
        phone_number=f"+91981{suffix}02",
        full_name="Chauffeur Alpha",
        role="driver",
        is_active=True,
        is_verified=True
    )
    driver_b_user = User(
        phone_number=f"+91981{suffix}03",
        full_name="Chauffeur Beta",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add_all([driver_a_user, driver_b_user])
    await db_session.flush()

    driver_a = DriverProfile(
        user_id=driver_a_user.id,
        license_number=f"DL-{suffix}-A",
        approval_status="APPROVED"
    )
    driver_b = DriverProfile(
        user_id=driver_b_user.id,
        license_number=f"DL-{suffix}-B",
        approval_status="APPROVED"
    )
    db_session.add_all([driver_a, driver_b])
    await db_session.flush()

    avail_a = DriverAvailability(driver_id=driver_a.id, is_online=True, is_busy=False)
    avail_b = DriverAvailability(driver_id=driver_b.id, is_online=True, is_busy=False)
    db_session.add_all([avail_a, avail_b])

    # Telemetry: Driver A is 10m away, Driver B is 50m away
    # Pickup at (19.0760, 72.8777)
    loc_a = DriverLocation(driver_id=driver_a.id, latitude=19.0761, longitude=72.8777)
    loc_b = DriverLocation(driver_id=driver_b.id, latitude=19.0765, longitude=72.8777)
    db_session.add_all([loc_a, loc_b])

    # 3. Create requested Ride
    ride = Ride(
        customer_id=customer_profile.id,
        vehicle_category_id=cat.id,
        fare_rule_id="1461db09-f2da-4799-b25c-c0c0e967c48b",
        start_otp="4821",
        status="REQUESTED",
        pickup_address="Marine Drive, Mumbai",
        pickup_latitude=19.0760,
        pickup_longitude=72.8777,
        destination_address="Bandra Kurla Complex, Mumbai",
        destination_latitude=19.0657,
        destination_longitude=72.8683,
        estimated_fare_cents=150000,
        estimated_distance_km=12.5,
        estimated_duration_minutes=35.0
    )
    db_session.add(ride)
    await db_session.flush()

    # 4. Initial dispatch offer goes to nearest Driver A
    assignment_a = await service.dispatch_next_available_driver(ride.id)
    assert assignment_a is not None
    assert assignment_a.driver_id == driver_a.id
    assert assignment_a.status == "OFFERED"
    assert assignment_a.is_accepted is None

    # Verify Redis offer safety key
    safety_key = f"dispatch:offer:{ride.id}:{driver_a.id}"
    assert await redis_client.exists(safety_key)

    # 5. Driver A times out / rejects with DISPATCH_TIMEOUT
    reject_res = await service.reject_offer_and_cascade(
        driver_user_id=driver_a_user.id,
        ride_id=ride.id,
        reason="DISPATCH_TIMEOUT",
        cascade_now=False  # Explicit cascade invocation for test assertion
    )
    assert reject_res["status"] == "REJECTED"

    # Verify Driver A assignment updated to REJECTED with is_accepted=False
    await db_session.refresh(assignment_a)
    assert assignment_a.status == "REJECTED"
    assert assignment_a.is_accepted is False
    assert assignment_a.rejection_reason == "DISPATCH_TIMEOUT"
    assert assignment_a.responded_at is not None

    # Verify Driver A cooldown placed in Redis
    cooldown_key = f"cooldown:{driver_a.id}:{ride.id}"
    assert await redis_client.exists(cooldown_key)

    # 6. Cascading dispatch runs -> Driver B must be offered the ride
    assignment_b = await service.dispatch_next_available_driver(ride.id)
    assert assignment_b is not None
    assert assignment_b.driver_id == driver_b.id
    assert assignment_b.status == "OFFERED"
    assert assignment_b.escalation_level == 1


@pytest.mark.asyncio
async def test_redis_cooldown_prevents_driver_reselection_in_cycle(db_session):
    """
    Test 2: Cooldown key prevents Driver A from being re-selected during the active cycle.
    """
    from sqlalchemy import update
    await db_session.execute(update(DriverAvailability).values(is_online=False))
    await db_session.flush()

    service = CascadingDispatchService(db_session)
    suffix = uuid.uuid4().hex[:6]

    cat = VehicleCategory(
        id=str(uuid.uuid4()),
        code=f"EXCLUSIVE_CYCLE_{suffix}",
        name="Exclusive Cycle Fleet"
    )
    db_session.add(cat)
    await db_session.flush()

    customer_user = User(phone_number=f"+91982{suffix}01", full_name="VIP User 2", role="customer", is_active=True, is_verified=True)
    db_session.add(customer_user)
    await db_session.flush()
    customer_profile = CustomerProfile(user_id=customer_user.id)
    db_session.add(customer_profile)
    await db_session.flush()

    driver_user = User(phone_number=f"+91982{suffix}02", full_name="Driver Solo", role="driver", is_active=True, is_verified=True)
    db_session.add(driver_user)
    await db_session.flush()
    driver = DriverProfile(user_id=driver_user.id, license_number=f"DL-{suffix}-S", approval_status="APPROVED")
    db_session.add(driver)
    await db_session.flush()

    avail = DriverAvailability(driver_id=driver.id, is_online=True, is_busy=False)
    loc = DriverLocation(driver_id=driver.id, latitude=19.0765, longitude=72.8775)
    db_session.add_all([avail, loc])

    ride = Ride(
        customer_id=customer_profile.id,
        vehicle_category_id=cat.id,
        fare_rule_id="1461db09-f2da-4799-b25c-c0c0e967c48b",
        start_otp="4821",
        status="REQUESTED",
        pickup_address="Nariman Point",
        pickup_latitude=19.0760,
        pickup_longitude=72.8777,
        destination_address="Airport T2",
        destination_latitude=19.0880,
        destination_longitude=72.8680,
        estimated_fare_cents=120000,
        estimated_distance_km=10.0,
        estimated_duration_minutes=30.0
    )
    db_session.add(ride)
    await db_session.flush()

    # Pre-set cooldown for Driver Solo on this ride
    await CascadingDispatchService.place_driver_on_cooldown(driver.id, ride.id, ttl_seconds=60)
    assert await CascadingDispatchService.is_driver_on_cooldown(driver.id, ride.id)

    # Dispatch attempt must NOT select Driver Solo because they are on cooldown
    assignment = await service.dispatch_next_available_driver(ride.id)
    # Since driver was skipped due to cooldown, no eligible driver was matched -> Ride expired
    assert assignment is None
    await db_session.refresh(ride)
    assert ride.status == "EXPIRED"


@pytest.mark.asyncio
async def test_all_drivers_rejecting_leads_to_expired_lifecycle(db_session):
    """
    Test 3: When all eligible drivers decline / time out, the cascading engine
    transitions the Ride to EXPIRED and records the proper RideStatusEvent.
    """
    from sqlalchemy import update
    await db_session.execute(update(DriverAvailability).values(is_online=False))
    await db_session.flush()

    service = CascadingDispatchService(db_session)
    suffix = uuid.uuid4().hex[:6]

    cat = VehicleCategory(
        id=str(uuid.uuid4()),
        code=f"EXPIRING_FLEET_{suffix}",
        name="Expiring Test Fleet"
    )
    db_session.add(cat)
    await db_session.flush()

    customer_user = User(phone_number=f"+91983{suffix}01", full_name="VIP User 3", role="customer", is_active=True, is_verified=True)
    db_session.add(customer_user)
    await db_session.flush()
    customer_profile = CustomerProfile(user_id=customer_user.id)
    db_session.add(customer_profile)
    await db_session.flush()

    driver_user = User(phone_number=f"+91983{suffix}02", full_name="Sole Chauffeur", role="driver", is_active=True, is_verified=True)
    db_session.add(driver_user)
    await db_session.flush()
    driver = DriverProfile(user_id=driver_user.id, license_number=f"DL-{suffix}-EXP", approval_status="APPROVED")
    db_session.add(driver)
    await db_session.flush()

    avail = DriverAvailability(driver_id=driver.id, is_online=True, is_busy=False)
    loc = DriverLocation(driver_id=driver.id, latitude=19.0760, longitude=72.8777)
    db_session.add_all([avail, loc])

    ride = Ride(
        customer_id=customer_profile.id,
        vehicle_category_id=cat.id,
        fare_rule_id="1461db09-f2da-4799-b25c-c0c0e967c48b",
        start_otp="4821",
        status="REQUESTED",
        pickup_address="Worli Sea Face",
        pickup_latitude=19.0760,
        pickup_longitude=72.8777,
        destination_address="Colaba",
        destination_latitude=19.0500,
        destination_longitude=72.8300,
        estimated_fare_cents=90000,
        estimated_distance_km=8.0,
        estimated_duration_minutes=25.0
    )
    db_session.add(ride)
    await db_session.flush()

    # Step 1: Offer to the sole driver
    assignment = await service.dispatch_next_available_driver(ride.id)
    assert assignment is not None
    assert assignment.driver_id == driver.id
    assert assignment.status == "OFFERED"

    # Step 2: Driver rejects with DISPATCH_TIMEOUT
    await service.reject_offer_and_cascade(
        driver_user_id=driver_user.id,
        ride_id=ride.id,
        reason="DISPATCH_TIMEOUT",
        cascade_now=False
    )

    # Step 3: Cascading runs again, but all drivers in radius are exhausted
    next_assignment = await service.dispatch_next_available_driver(ride.id)
    assert next_assignment is None

    # Verify ride state transitioned to EXPIRED
    await db_session.refresh(ride)
    assert ride.status == "EXPIRED"

    # Verify RideStatusEvent recorded
    from sqlalchemy import select
    event_stmt = select(RideStatusEvent).where(
        RideStatusEvent.ride_id == ride.id,
        RideStatusEvent.new_status == "EXPIRED"
    )
    event_res = await db_session.execute(event_stmt)
    event = event_res.scalar_one_or_none()
    assert event is not None
    assert event.previous_status == "REQUESTED"
    assert event.trigger_actor == "SYSTEM"


@pytest.mark.asyncio
async def test_server_authoritative_safety_fallback_execution(db_session):
    """
    Test 4: Verify that the server safety fallback forcefully expires an offer
    and places the driver on cooldown if no client accept/reject is received.
    """
    from sqlalchemy import update
    await db_session.execute(update(DriverAvailability).values(is_online=False))
    await db_session.flush()

    service = CascadingDispatchService(db_session)
    suffix = uuid.uuid4().hex[:6]

    cat = VehicleCategory(
        id=str(uuid.uuid4()),
        code=f"FALLBACK_FLEET_{suffix}",
        name="Fallback Test Fleet"
    )
    db_session.add(cat)
    await db_session.flush()

    customer_user = User(phone_number=f"+91984{suffix}01", full_name="VIP User 4", role="customer", is_active=True, is_verified=True)
    db_session.add(customer_user)
    await db_session.flush()
    customer_profile = CustomerProfile(user_id=customer_user.id)
    db_session.add(customer_profile)
    await db_session.flush()

    driver_user = User(phone_number=f"+91984{suffix}02", full_name="Unresponsive Driver", role="driver", is_active=True, is_verified=True)
    db_session.add(driver_user)
    await db_session.flush()
    driver = DriverProfile(user_id=driver_user.id, license_number=f"DL-{suffix}-AFK", approval_status="APPROVED")
    db_session.add(driver)
    await db_session.flush()

    avail = DriverAvailability(driver_id=driver.id, is_online=True, is_busy=False)
    loc = DriverLocation(driver_id=driver.id, latitude=19.0760, longitude=72.8777)
    db_session.add_all([avail, loc])

    ride = Ride(
        customer_id=customer_profile.id,
        vehicle_category_id=cat.id,
        fare_rule_id="1461db09-f2da-4799-b25c-c0c0e967c48b",
        start_otp="4821",
        status="REQUESTED",
        pickup_address="Juhu Beach",
        pickup_latitude=19.0760,
        pickup_longitude=72.8777,
        destination_address="Versova",
        destination_latitude=19.0800,
        destination_longitude=72.8700,
        estimated_fare_cents=75000,
        estimated_distance_km=5.0,
        estimated_duration_minutes=15.0
    )
    db_session.add(ride)
    await db_session.flush()

    assignment = await service.dispatch_next_available_driver(ride.id)
    assert assignment is not None
    assert assignment.status == "OFFERED"
    await db_session.commit()

    # Simulate server-side safety fallback execution (with delay_seconds=0 for immediate test verification)
    await CascadingDispatchService.run_offer_safety_fallback(
        ride_id=ride.id,
        driver_id=driver.id,
        delay_seconds=0
    )

    # Verify assignment is forcefully expired
    await db_session.refresh(assignment)
    assert assignment.status == "EXPIRED"
    assert assignment.is_accepted is False
    assert assignment.rejection_reason == "DISPATCH_TIMEOUT"

    # Verify cooldown was applied
    cooldown_key = f"cooldown:{driver.id}:{ride.id}"
    assert await redis_client.exists(cooldown_key)
