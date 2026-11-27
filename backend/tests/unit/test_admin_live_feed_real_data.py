import uuid
from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import select

from backend.api.routes.admin import get_live_mobility_feed
from backend.api.routes.rides import get_current_active_ride, get_ride_history
from backend.domain.models import (
    CustomerProfile, DriverAvailability, DriverLocation, DriverProfile,
    DriverVehicleAssignment, FareRule, Ride, User, Vehicle, VehicleCategory,
)


@pytest.mark.asyncio
async def test_admin_live_feed_reports_only_persisted_location_and_vehicle(db_session, customer_user):
    customer = (await db_session.execute(select(CustomerProfile).where(CustomerProfile.user_id == customer_user.id))).scalar_one()
    category = (await db_session.execute(select(VehicleCategory))).scalars().first()
    rule = (await db_session.execute(select(FareRule).where(FareRule.category_id == category.id))).scalars().first()
    suffix = uuid.uuid4().hex[:8]
    user = User(email=f"feed-{suffix}@example.com", full_name="Actual Driver", role="driver", phone_number="+919876543210", is_active=True)
    db_session.add(user)
    await db_session.flush()
    driver = DriverProfile(user_id=user.id, license_number=f"DL-{suffix}", approval_status="APPROVED", total_trips=7, rating_count=0)
    db_session.add(driver)
    await db_session.flush()
    vehicle = Vehicle(category_id=category.id, make="Test Make", model="Actual EV", year=2025, license_plate=f"BR01{suffix.upper()}", is_active=True)
    db_session.add(vehicle)
    await db_session.flush()
    db_session.add_all([
        DriverVehicleAssignment(driver_id=driver.id, vehicle_id=vehicle.id, is_active=True),
        DriverAvailability(driver_id=driver.id, is_online=True, is_busy=True),
    ])
    now = datetime.now(timezone.utc)
    db_session.add_all([
        DriverLocation(driver_id=driver.id, latitude=25.59, longitude=85.13, speed=2.0, recorded_at=now-timedelta(minutes=2)),
        DriverLocation(driver_id=driver.id, latitude=25.60, longitude=85.14, speed=10.0, recorded_at=now),
    ])
    def ride(ride_id, status, final_fare=None, assigned=None):
        return Ride(id=ride_id, customer_id=customer.id, assigned_driver_id=assigned, vehicle_id=vehicle.id if assigned else None, vehicle_category_id=category.id, fare_rule_id=rule.id, status=status, pickup_address="Patna Junction", pickup_latitude=25.61, pickup_longitude=85.12, destination_address="Gandhi Maidan", destination_latitude=25.62, destination_longitude=85.11, start_otp="1234", estimated_distance_km=3.0, estimated_duration_minutes=12.0, estimated_fare_cents=9000, final_fare_cents=final_fare)
    assigned = ride(f"feed-assigned-{suffix}", "IN_PROGRESS", assigned=driver.id)
    paused = ride(f"feed-paused-{suffix}", "PAUSED", assigned=driver.id)
    waiting = ride(f"feed-waiting-{suffix}", "REQUESTED")
    waiting.requested_at = now - timedelta(minutes=10)
    completed = ride(f"feed-completed-{suffix}", "COMPLETED", final_fare=12345)
    completed_without_final_fare = ride(f"feed-completed-no-fare-{suffix}", "COMPLETED", final_fare=None)
    db_session.add_all([assigned, paused, waiting, completed, completed_without_final_fare])
    await db_session.commit()
    feed = await get_live_mobility_feed(admin=None, db=db_session)
    assert feed["stats"]["online_drivers"] == 1
    assert feed["stats"]["active_rides"] == 3
    assert feed["stats"]["waiting_riders"] == 1
    assert feed["stats"]["gross_booking_inr"] == 123.45
    actual_driver = next(item for item in feed["drivers"] if item["id"] == driver.id)
    assert (actual_driver["lat"], actual_driver["lng"]) == (25.60, 85.14)
    assert actual_driver["speed"] == 36.0
    assert actual_driver["location_is_stale"] is False
    assert actual_driver["plate"] == vehicle.license_plate
    assert actual_driver["battery"] is None
    assert actual_driver["rating"] is None
    paused_ride = next(item for item in feed["active_rides"] if item["id"] == paused.id)
    assert paused_ride["status"] == "PAUSED"
    actual_ride = next(item for item in feed["active_rides"] if item["id"] == assigned.id)
    assert actual_ride["driver"] == "Actual Driver"
    assert actual_ride["plate"] == vehicle.license_plate
    assert len(feed["riders"]) == 1 and feed["riders"][0]["ride_id"] == waiting.id
    actual_customer = next(item for item in feed["customers"] if item["id"] == customer_user.id)
    assert actual_customer["rides"] == 2
    assert actual_customer["spend"] == "₹123.45"
    assert actual_customer["rating"] is None

    # Active ride polling must retain PAUSED and expose the start OTP only to its customer.
    customer_active = await get_current_active_ride(user=customer_user, db=db_session)
    assert customer_active["active_ride"]["status"] in {"IN_PROGRESS", "PAUSED"}
    assert customer_active["active_ride"]["start_otp"] == "1234"
    driver_active = await get_current_active_ride(user=user, db=db_session)
    assert driver_active["active_ride"]["status"] in {"IN_PROGRESS", "PAUSED"}
    assert driver_active["active_ride"]["start_otp"] is None
    driver_history = await get_ride_history(limit=50, user=user, db=db_session)
    assert driver_history
    assert all(item["otp_code"] is None for item in driver_history)


@pytest.mark.asyncio
async def test_admin_live_feed_does_not_count_stale_online_driver_or_fabricate_location(db_session):
    suffix = uuid.uuid4().hex[:8]
    user = User(
        email=f"stale-feed-{suffix}@example.com",
        full_name="Stale Driver",
        role="driver",
        phone_number=f"+9199{suffix[:8]}",
        is_active=True,
    )
    db_session.add(user)
    await db_session.flush()
    driver = DriverProfile(
        user_id=user.id,
        license_number=f"DL-STALE-{suffix}",
        approval_status="APPROVED",
    )
    db_session.add(driver)
    await db_session.flush()
    stale = datetime.now(timezone.utc) - timedelta(minutes=30)
    db_session.add(DriverAvailability(
        driver_id=driver.id,
        is_online=True,
        is_busy=False,
        last_heartbeat=stale,
        updated_at=stale,
    ))
    orphan_customer = User(
        email=f"no-profile-{suffix}@example.com",
        full_name="Customer Without Profile",
        role="customer",
        phone_number=f"+9198{suffix[:8]}",
        is_active=True,
    )
    db_session.add(orphan_customer)
    await db_session.commit()

    feed = await get_live_mobility_feed(admin=None, db=db_session)
    record = next(item for item in feed["drivers"] if item["id"] == driver.id)
    assert feed["stats"]["online_drivers"] == 0
    assert record["status"] == "OFFLINE"
    assert record["lat"] is None
    assert record["lng"] is None
    assert record["battery"] is None
    assert record["plate"] == ""
    unprofiled = next(item for item in feed["customers"] if item["id"] == orphan_customer.id)
    assert unprofiled["profile_id"] is None
    assert unprofiled["rides"] == 0
    assert unprofiled["rating"] is None
