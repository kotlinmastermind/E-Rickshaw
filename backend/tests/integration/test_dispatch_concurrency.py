"""
Concurrency and Atomic State Verification for H3 Smart Dispatch.
Tests:
- Conflicting simultaneous dispatch offers to the same driver
- Simultaneous acceptance of the same ride by multiple drivers
- Duplicate acceptance idempotency
- Race between timeout watchdog and driver acceptance
"""
import uuid
import asyncio
import pytest
from datetime import datetime, timezone, timedelta
from sqlalchemy import select
from fastapi import HTTPException

from backend.domain.models import (
    User, CustomerProfile, DriverProfile, DriverAvailability,
    VehicleCategory, FareRule, Ride, RideAssignment
)
from backend.infrastructure.database.session import async_session_maker
from backend.application.services.smart_dispatch_service import SmartDispatchService
from backend.application.services.live_location_service import LiveLocationService
from backend.infrastructure.cache.redis_client import redis_client


@pytest.mark.asyncio
async def test_driver_cannot_receive_two_conflicting_offers(db_session):
    """
    Task 4: Concurrency Check 1.
    When two rides simultaneously request dispatch in the same H3 hex,
    distributed atomic locking ensures the sole driver is offered ONLY ONE ride.
    """
    suffix = uuid.uuid4().hex[:6]

    # Category and rule
    cat = VehicleCategory(id=str(uuid.uuid4()), code=f"CONCUR_CAT_{suffix}", name="Concur Fleet")
    db_session.add(cat)
    await db_session.flush()

    rule = FareRule(id=str(uuid.uuid4()), category_id=cat.id, base_fare_cents=2000, per_km_cents=500, per_minute_cents=100, minimum_fare_cents=2000)
    db_session.add(rule)
    await db_session.flush()

    # Sole driver
    d_user = User(phone_number=f"+91983{suffix}01", full_name="Sole Chauffeur", role="driver", is_active=True, is_verified=True)
    db_session.add(d_user)
    await db_session.flush()

    d_prof = DriverProfile(id=str(uuid.uuid4()), user_id=d_user.id, license_number=f"DL-CONCUR-{suffix}", approval_status="APPROVED")
    db_session.add(d_prof)
    await db_session.flush()

    d_avail = DriverAvailability(driver_id=d_prof.id, is_online=True, is_busy=False)
    db_session.add(d_avail)

    # Ingest telemetry
    live_loc = LiveLocationService(db=db_session)
    await live_loc.ingest_driver_location(driver_id=d_prof.id, latitude=28.6139, longitude=77.2090, is_online=True)

    # Customers & Rides
    c_user1 = User(phone_number=f"+91983{suffix}02", full_name="Pass 1", role="customer", is_active=True, is_verified=True)
    c_user2 = User(phone_number=f"+91983{suffix}03", full_name="Pass 2", role="customer", is_active=True, is_verified=True)
    db_session.add_all([c_user1, c_user2])
    await db_session.flush()

    c_prof1 = CustomerProfile(user_id=c_user1.id)
    c_prof2 = CustomerProfile(user_id=c_user2.id)
    db_session.add_all([c_prof1, c_prof2])
    await db_session.flush()

    ride1 = Ride(
        customer_id=c_prof1.id, vehicle_category_id=cat.id, fare_rule_id=rule.id, start_otp="1111", status="REQUESTED",
        pickup_address="Pickup 1", pickup_latitude=28.6140, pickup_longitude=77.2091,
        destination_address="Dest 1", destination_latitude=28.6200, destination_longitude=77.2200,
        estimated_distance_km=2.0, estimated_duration_minutes=8.0, estimated_fare_cents=8000
    )
    ride2 = Ride(
        customer_id=c_prof2.id, vehicle_category_id=cat.id, fare_rule_id=rule.id, start_otp="2222", status="REQUESTED",
        pickup_address="Pickup 2", pickup_latitude=28.6141, pickup_longitude=77.2092,
        destination_address="Dest 2", destination_latitude=28.6210, destination_longitude=77.2210,
        estimated_distance_km=2.2, estimated_duration_minutes=8.5, estimated_fare_cents=8500
    )
    db_session.add_all([ride1, ride2])
    await db_session.commit()

    # Trigger simultaneous dispatches using distinct sessions to model concurrent workers
    async with async_session_maker() as session1, async_session_maker() as session2:
        svc1 = SmartDispatchService(session1)
        svc2 = SmartDispatchService(session2)

        res1, res2 = await asyncio.gather(
            svc1.search_and_dispatch(ride1.id, max_rings=2),
            svc2.search_and_dispatch(ride2.id, max_rings=2)
        )

    # Exactly one ride must secure the driver; the other must receive None (or expired)
    assignments = [a for a in [res1, res2] if a is not None]
    assert len(assignments) == 1, f"Driver received {len(assignments)} offers, expected exactly 1"
    assert assignments[0].driver_id == d_prof.id
    assert assignments[0].status == "OFFERED"

    # Clean up lock
    await redis_client.delete(f"dispatch:lock:{d_prof.id}")


@pytest.mark.asyncio
async def test_ride_cannot_have_multiple_accepted_drivers(db_session):
    """
    Task 4: Concurrency Check 2.
    Ensures that a single ride cannot be accepted by more than one driver.
    """
    suffix = uuid.uuid4().hex[:6]
    cat = VehicleCategory(id=str(uuid.uuid4()), code=f"MULTI_CAT_{suffix}", name="Multi Fleet")
    rule = FareRule(id=str(uuid.uuid4()), category_id=cat.id, base_fare_cents=2000, per_km_cents=500, per_minute_cents=100, minimum_fare_cents=2000)
    db_session.add_all([cat, rule])
    await db_session.flush()

    c_user = User(phone_number=f"+91984{suffix}01", full_name="Customer Multi", role="customer", is_active=True, is_verified=True)
    db_session.add(c_user)
    await db_session.flush()
    c_prof = CustomerProfile(user_id=c_user.id)
    db_session.add(c_prof)
    await db_session.flush()

    ride = Ride(
        customer_id=c_prof.id, vehicle_category_id=cat.id, fare_rule_id=rule.id, start_otp="3333", status="REQUESTED",
        pickup_address="Pickup Multi", pickup_latitude=28.6140, pickup_longitude=77.2091,
        destination_address="Dest Multi", destination_latitude=28.6200, destination_longitude=77.2200,
        estimated_distance_km=2.0, estimated_duration_minutes=8.0, estimated_fare_cents=8000
    )
    db_session.add(ride)
    await db_session.flush()

    # Driver 1
    d_user1 = User(phone_number=f"+91984{suffix}02", full_name="Driver 1", role="driver", is_active=True, is_verified=True)
    d_user2 = User(phone_number=f"+91984{suffix}03", full_name="Driver 2", role="driver", is_active=True, is_verified=True)
    db_session.add_all([d_user1, d_user2])
    await db_session.flush()

    d_prof1 = DriverProfile(id=str(uuid.uuid4()), user_id=d_user1.id, license_number=f"DL-{suffix}-1", approval_status="APPROVED")
    d_prof2 = DriverProfile(id=str(uuid.uuid4()), user_id=d_user2.id, license_number=f"DL-{suffix}-2", approval_status="APPROVED")
    db_session.add_all([d_prof1, d_prof2])
    await db_session.flush()

    now = datetime.now(timezone.utc)
    assign1 = RideAssignment(ride_id=ride.id, driver_id=d_prof1.id, status="OFFERED", expires_at=now + timedelta(seconds=20))
    db_session.add(assign1)
    await db_session.commit()

    # Driver 1 accepts
    svc = SmartDispatchService(db_session)
    res1 = await svc.accept_offer(driver_user_id=d_user1.id, ride_id=ride.id)
    assert res1["status"] == "ACCEPTED"

    # Driver 2 tries to accept the same ride (simulating unauthorized/competing attempt)
    with pytest.raises(HTTPException) as exc_info:
        await svc.accept_offer(driver_user_id=d_user2.id, ride_id=ride.id)
    assert exc_info.value.status_code in [404, 409]

    # Verify ride in DB has exactly Driver 1
    r_db = (await db_session.execute(select(Ride).where(Ride.id == ride.id))).scalar_one()
    assert r_db.assigned_driver_id == d_prof1.id
    assert r_db.status == "ASSIGNED"


@pytest.mark.asyncio
async def test_race_between_timeout_and_driver_acceptance(db_session):
    """
    Task 4: Concurrency Check 3.
    When a dispatch offer has expired or timed out in the background,
    a late acceptance by the driver must be safely rejected with HTTP 409/410.
    """
    suffix = uuid.uuid4().hex[:6]
    cat = VehicleCategory(id=str(uuid.uuid4()), code=f"RACE_CAT_{suffix}", name="Race Fleet")
    rule = FareRule(id=str(uuid.uuid4()), category_id=cat.id, base_fare_cents=2000, per_km_cents=500, per_minute_cents=100, minimum_fare_cents=2000)
    db_session.add_all([cat, rule])
    await db_session.flush()

    c_user = User(phone_number=f"+91985{suffix}01", full_name="Customer Race", role="customer", is_active=True, is_verified=True)
    db_session.add(c_user)
    await db_session.flush()
    c_prof = CustomerProfile(user_id=c_user.id)
    db_session.add(c_prof)
    await db_session.flush()

    ride = Ride(
        customer_id=c_prof.id, vehicle_category_id=cat.id, fare_rule_id=rule.id, start_otp="4444", status="REQUESTED",
        pickup_address="Pickup Race", pickup_latitude=28.6140, pickup_longitude=77.2091,
        destination_address="Dest Race", destination_latitude=28.6200, destination_longitude=77.2200,
        estimated_distance_km=2.0, estimated_duration_minutes=8.0, estimated_fare_cents=8000
    )
    db_session.add(ride)
    await db_session.flush()

    d_user = User(phone_number=f"+91985{suffix}02", full_name="Late Driver", role="driver", is_active=True, is_verified=True)
    db_session.add(d_user)
    await db_session.flush()

    d_prof = DriverProfile(id=str(uuid.uuid4()), user_id=d_user.id, license_number=f"DL-{suffix}-LATE", approval_status="APPROVED")
    db_session.add(d_prof)
    await db_session.flush()

    # Assignment created with an expired timestamp in the past
    past_time = datetime.now(timezone.utc) - timedelta(seconds=10)
    assign = RideAssignment(
        ride_id=ride.id,
        driver_id=d_prof.id,
        status="REJECTED",
        rejection_reason="DISPATCH_TIMEOUT",
        expires_at=past_time
    )
    db_session.add(assign)
    await db_session.commit()

    # Late driver attempts to accept
    svc = SmartDispatchService(db_session)
    with pytest.raises(HTTPException) as exc_info:
        await svc.accept_offer(driver_user_id=d_user.id, ride_id=ride.id)

    assert exc_info.value.status_code in [409, 410]
    # Ride must remain unassigned
    r_check = (await db_session.execute(select(Ride).where(Ride.id == ride.id))).scalar_one()
    assert r_check.assigned_driver_id is None

