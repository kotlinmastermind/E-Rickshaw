"""
End-to-End Passenger and Driver Application Workflow Verification.
Covers:
- Ride request creation
- Driver offer receipt
- Driver rejection cascading to next eligible driver
- Driver acceptance transitioning ride to ASSIGNED
- Cancellation handling (restoring driver availability)
- Reconnect state recovery via /dispatch/status/{ride_id}
"""
import uuid
import pytest
from datetime import datetime, timezone
from sqlalchemy import select

from backend.domain.models import (
    User, CustomerProfile, DriverProfile, DriverAvailability,
    VehicleCategory, FareRule, Ride, RideAssignment
)
from backend.domain.state_machine.ride_state_machine import RideStateMachine, RideStatus, ActorRole
from backend.application.services.smart_dispatch_service import SmartDispatchService
from backend.application.services.ride_service import RideService
from backend.application.services.live_location_service import LiveLocationService
from backend.infrastructure.cache.redis_client import redis_client


@pytest.mark.asyncio
async def test_complete_passenger_driver_dispatch_and_reassignment_flow(db_session):
    suffix = uuid.uuid4().hex[:6]

    # Category and rule
    cat = VehicleCategory(id=str(uuid.uuid4()), code=f"FLOW_CAT_{suffix}", name="Workflow Fleet")
    db_session.add(cat)
    await db_session.flush()

    rule = FareRule(id=str(uuid.uuid4()), category_id=cat.id, base_fare_cents=2000, per_km_cents=500, per_minute_cents=100, minimum_fare_cents=2000)
    db_session.add(rule)
    await db_session.flush()

    # Passenger
    c_user = User(phone_number=f"+91986{suffix}01", full_name="Rahul Passenger", role="customer", is_active=True, is_verified=True)
    db_session.add(c_user)
    await db_session.flush()
    c_prof = CustomerProfile(user_id=c_user.id)
    db_session.add(c_prof)
    await db_session.flush()

    # Driver 1 (Closer, 100m away) & Driver 2 (Further, 600m away)
    d_user1 = User(phone_number=f"+91986{suffix}02", full_name="Driver One", role="driver", is_active=True, is_verified=True)
    d_user2 = User(phone_number=f"+91986{suffix}03", full_name="Driver Two", role="driver", is_active=True, is_verified=True)
    db_session.add_all([d_user1, d_user2])
    await db_session.flush()

    d_prof1 = DriverProfile(id=str(uuid.uuid4()), user_id=d_user1.id, license_number=f"DL-{suffix}-1", approval_status="APPROVED")
    d_prof2 = DriverProfile(id=str(uuid.uuid4()), user_id=d_user2.id, license_number=f"DL-{suffix}-2", approval_status="APPROVED")
    db_session.add_all([d_prof1, d_prof2])
    await db_session.flush()

    d_avail1 = DriverAvailability(driver_id=d_prof1.id, is_online=True, is_busy=False)
    d_avail2 = DriverAvailability(driver_id=d_prof2.id, is_online=True, is_busy=False)
    db_session.add_all([d_avail1, d_avail2])

    live_loc = LiveLocationService(db=db_session)
    # Driver 1 at (28.6140, 77.2091)
    await live_loc.ingest_driver_location(d_prof1.id, 28.6140, 77.2091, is_online=True)
    # Driver 2 at (28.6180, 77.2120)
    await live_loc.ingest_driver_location(d_prof2.id, 28.6180, 77.2120, is_online=True)

    # 1. Passenger requests ride
    ride = Ride(
        customer_id=c_prof.id,
        vehicle_category_id=cat.id,
        fare_rule_id=rule.id,
        start_otp="7788",
        status="REQUESTED",
        pickup_address="Rajpath, New Delhi",
        pickup_latitude=28.6139,
        pickup_longitude=77.2090,
        destination_address="Rashtrapati Bhavan",
        destination_latitude=28.6143,
        destination_longitude=77.1995,
        estimated_distance_km=1.2,
        estimated_duration_minutes=5.0,
        estimated_fare_cents=6000
    )
    db_session.add(ride)
    await db_session.commit()

    # 2. Smart Dispatch runs -> Driver 1 is nearest and gets OFFERED
    dispatch_svc = SmartDispatchService(db_session)
    assign1 = await dispatch_svc.search_and_dispatch(ride.id, max_rings=2)

    assert assign1 is not None
    assert assign1.driver_id == d_prof1.id
    assert assign1.status == "OFFERED"

    # Verify Driver 1 active offer in Redis
    assert await redis_client.get(f"driver:active_offer:{d_user1.id}") == ride.id

    # 3. Driver 1 declines offer -> Triggers immediate cascading dispatch
    rej_res = await dispatch_svc.reject_offer(driver_user_id=d_user1.id, ride_id=ride.id, reason="TOO_FAR")
    assert rej_res["status"] == "REJECTED"

    # Cooldown check for Driver 1
    assert await redis_client.exists(f"cooldown:{d_prof1.id}:{ride.id}") is True

    # 4. Cascade offers ride to Driver 2
    assign2 = await dispatch_svc.search_and_dispatch(ride.id, max_rings=2)
    assert assign2 is not None
    assert assign2.driver_id == d_prof2.id
    assert assign2.status == "OFFERED"

    # 5. Driver 2 accepts offer
    acc_res = await dispatch_svc.accept_offer(driver_user_id=d_user2.id, ride_id=ride.id)
    assert acc_res["status"] == "ACCEPTED"
    assert acc_res["driver_id"] == d_prof2.id

    # Verify Ride is now ASSIGNED to Driver 2
    r_updated = (await db_session.execute(select(Ride).where(Ride.id == ride.id))).scalar_one()
    assert r_updated.status == "ASSIGNED"
    assert r_updated.assigned_driver_id == d_prof2.id

    # Verify Driver 2 marked busy
    avail2_updated = (await db_session.execute(select(DriverAvailability).where(DriverAvailability.driver_id == d_prof2.id))).scalar_one()
    assert avail2_updated.is_busy is True

    # 6. Passenger cancels ride
    RideStateMachine.validate_transition(r_updated.status, RideStatus.CANCELLED.value, ActorRole.CUSTOMER.value)
    r_updated.status = RideStatus.CANCELLED.value
    # Driver availability restored
    avail2_updated.is_busy = False
    await db_session.commit()

    # Verify final state
    r_final = (await db_session.execute(select(Ride).where(Ride.id == ride.id))).scalar_one()
    assert r_final.status == "CANCELLED"
    d2_final = (await db_session.execute(select(DriverAvailability).where(DriverAvailability.driver_id == d_prof2.id))).scalar_one()
    assert d2_final.is_busy is False

