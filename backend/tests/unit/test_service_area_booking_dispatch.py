"""End-to-end service-area isolation for candidate discovery and offer acceptance."""

import json
import uuid
from datetime import datetime, timedelta, timezone

import pytest
from fastapi import HTTPException
from sqlalchemy import select

from backend.application.services.driver_eligibility_service import DriverEligibilityService
from backend.application.services.live_location_service import LiveLocationService
from backend.application.services.ride_service import RideService
from backend.domain.models import (
    DriverAvailability,
    DriverProfile,
    DriverVehicleAssignment,
    FareRule,
    GeofenceZone,
    Ride,
    RideAssignment,
    FareEstimate,
    CustomerProfile,
    User,
    Vehicle,
    VehicleCategory,
)


@pytest.mark.asyncio
async def test_driver_from_adjacent_service_area_cannot_receive_or_accept_ride(db_session):
    suffix = uuid.uuid4().hex[:8]
    phone_suffix = str(uuid.uuid4().int % 100000000).zfill(8)

    # Replace the broad test Delhi polygon with two adjacent, non-overlapping
    # operational areas. Both are deliberately close enough for the same H3 rings.
    broad_areas = (await db_session.execute(
        select(GeofenceZone).where(
            GeofenceZone.zone_type == "SERVICE_AREA",
            GeofenceZone.city_code == "DELHI_NCR",
        )
    )).scalars().all()
    for zone in broad_areas:
        zone.is_active = False

    area_a = GeofenceZone(
        name=f"Test Area A {suffix}",
        city_code="DELHI_NCR",
        zone_type="SERVICE_AREA",
        is_active=True,
        coordinates_json=json.dumps([
            {"lat": 28.60, "lng": 77.20},
            {"lat": 28.60, "lng": 77.22},
            {"lat": 28.62, "lng": 77.22},
            {"lat": 28.62, "lng": 77.20},
        ]),
    )
    area_b = GeofenceZone(
        name=f"Test Area B {suffix}",
        city_code="DELHI_NCR",
        zone_type="SERVICE_AREA",
        is_active=True,
        coordinates_json=json.dumps([
            {"lat": 28.60, "lng": 77.22},
            {"lat": 28.60, "lng": 77.24},
            {"lat": 28.62, "lng": 77.24},
            {"lat": 28.62, "lng": 77.22},
        ]),
    )
    db_session.add_all([area_a, area_b])

    category = VehicleCategory(
        id=f"svc_cat_{suffix}",
        code=f"SVC_{suffix.upper()}",
        name="Service Area Test Vehicle",
        capacity=4,
        is_active=True,
    )
    fare_rule = FareRule(
        id=f"svc_rule_{suffix}",
        category_id=category.id,
        city_code="DELHI_NCR",
        version=1,
        base_fare_cents=2000,
        per_km_cents=500,
        per_minute_cents=100,
        minimum_fare_cents=2000,
        is_active=True,
        workflow_status="ACTIVE",
        effective_from=datetime.now(timezone.utc) - timedelta(days=1),
    )
    driver_user = User(
        phone_number=f"+9197{phone_suffix}",
        full_name="Area B Test Driver",
        role="driver",
        is_active=True,
        is_verified=True,
    )
    db_session.add_all([category, fare_rule, driver_user])
    await db_session.flush()

    driver = DriverProfile(
        id=f"svc_driver_{suffix}",
        user_id=driver_user.id,
        license_number=f"DL-SVC-{suffix}",
        approval_status="APPROVED",
    )
    vehicle = Vehicle(
        id=f"svc_vehicle_{suffix}",
        category_id=category.id,
        make="Test",
        model="E-Rickshaw",
        year=2025,
        license_plate=f"DL-SVC-{suffix}",
        is_active=True,
    )
    db_session.add_all([driver, vehicle])
    await db_session.flush()
    db_session.add_all([
        DriverAvailability(driver_id=driver.id, is_online=True, is_busy=False),
        DriverVehicleAssignment(driver_id=driver.id, vehicle_id=vehicle.id, is_active=True),
    ])
    await db_session.commit()

    # Pickup is in A; the only online driver is 200m away, but inside B.
    pickup_lat, pickup_lng = 28.61, 77.219
    driver_lat, driver_lng = 28.61, 77.221
    await LiveLocationService(db=db_session).ingest_driver_location(
        driver.id, driver_lat, driver_lng, is_online=True
    )

    eligibility = DriverEligibilityService(db=db_session)
    candidates = await eligibility.discover_eligible_candidates(
        pickup_lat=pickup_lat,
        pickup_lng=pickup_lng,
        max_rings=3,
        required_category_id=category.id,
        required_service_area_id=area_a.id,
    )
    assert candidates == []

    customer_user = User(
        phone_number=f"+9196{phone_suffix}",
        full_name="Area Isolation Customer",
        role="customer",
        is_active=True,
        is_verified=True,
    )
    db_session.add(customer_user)
    await db_session.flush()
    from backend.domain.models import CustomerProfile
    customer = CustomerProfile(user_id=customer_user.id)
    db_session.add(customer)
    await db_session.flush()

    ride = Ride(
        customer_id=customer.id,
        vehicle_category_id=category.id,
        fare_rule_id=fare_rule.id,
        service_area_id=area_a.id,
        status="REQUESTED",
        pickup_address="Area A pickup",
        pickup_latitude=pickup_lat,
        pickup_longitude=pickup_lng,
        destination_address="Area A destination",
        destination_latitude=28.615,
        destination_longitude=77.215,
        start_otp="123456",
        estimated_distance_km=1.0,
        estimated_duration_minutes=5.0,
        estimated_fare_cents=2500,
    )
    db_session.add(ride)
    await db_session.flush()
    offer = RideAssignment(
        ride_id=ride.id,
        driver_id=driver.id,
        status="OFFERED",
        offered_at=datetime.now(timezone.utc),
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=5),
    )
    db_session.add(offer)
    await db_session.commit()

    with pytest.raises(HTTPException) as exc:
        await RideService(db_session).accept_ride_atomic(driver_user.id, ride.id)
    assert exc.value.status_code == 403
    assert "outside this ride's active service area" in exc.value.detail

    # The separate /dispatch/offer/{ride_id}/accept path must enforce the same rule.
    from backend.application.services.smart_dispatch_service import SmartDispatchService
    with pytest.raises(HTTPException) as dispatch_exc:
        await SmartDispatchService(db_session).accept_offer(driver_user.id, ride.id)
    assert dispatch_exc.value.status_code == 403

    await db_session.refresh(ride)
    assert ride.status == "REQUESTED"
    assert ride.assigned_driver_id is None

@pytest.mark.asyncio
async def test_booking_rejects_pickup_and_destination_in_different_service_areas(db_session):
    suffix = uuid.uuid4().hex[:8]
    phone_suffix = str(uuid.uuid4().int % 100000000).zfill(8)
    category = VehicleCategory(
        id=f"cross_cat_{suffix}",
        code=f"CROSS_{suffix.upper()}",
        name="Cross Area Test Vehicle",
        capacity=4,
        is_active=True,
    )
    fare_rule = FareRule(
        id=f"cross_rule_{suffix}",
        category_id=category.id,
        city_code="DELHI_NCR",
        version=1,
        base_fare_cents=2000,
        per_km_cents=500,
        per_minute_cents=100,
        minimum_fare_cents=2000,
        is_active=True,
        workflow_status="ACTIVE",
        effective_from=datetime.now(timezone.utc) - timedelta(days=1),
    )
    customer_user = User(
        phone_number=f"+9195{phone_suffix}",
        full_name="Cross Area Booking Customer",
        role="customer",
        is_active=True,
        is_verified=True,
    )
    db_session.add_all([category, fare_rule, customer_user])
    await db_session.flush()
    customer = CustomerProfile(user_id=customer_user.id)
    db_session.add(customer)
    await db_session.flush()

    # The test database's Delhi and Patna polygons are separate active areas.
    estimate = FareEstimate(
        id=str(uuid.uuid4()),
        category_id=category.id,
        fare_rule_id=fare_rule.id,
        pickup_latitude=28.6139,
        pickup_longitude=77.2090,
        destination_latitude=25.5941,
        destination_longitude=85.1376,
        distance_km=100.0,
        estimated_duration_minutes=180.0,
        estimated_fare_cents=10000,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=10),
    )
    db_session.add(estimate)
    await db_session.commit()

    with pytest.raises(HTTPException) as exc:
        await RideService(db_session).create_ride_request(
            customer_user_id=customer_user.id,
            estimate_id=estimate.id,
            category_id=category.id,
        )
    assert exc.value.status_code == 400
    assert "same active service area" in exc.value.detail

@pytest.mark.asyncio
async def test_booking_is_not_created_when_no_eligible_driver_is_available(db_session):
    from backend.domain.models import VehicleCategory

    category = (await db_session.execute(
        select(VehicleCategory).where(VehicleCategory.code == "E_RICKSHAW_STANDARD")
    )).scalars().first()
    assert category is not None
    fare_rule = (await db_session.execute(
        select(FareRule).where(
            FareRule.category_id == category.id,
            FareRule.city_code == "DELHI_NCR",
            FareRule.is_active.is_(True),
        )
    )).scalars().first()
    assert fare_rule is not None

    phone_suffix = str(uuid.uuid4().int % 100000000).zfill(8)
    customer_user = User(
        phone_number=f"+9194{phone_suffix}",
        full_name="No Driver Booking Customer",
        role="customer",
        is_active=True,
        is_verified=True,
    )
    db_session.add(customer_user)
    await db_session.flush()
    customer = CustomerProfile(user_id=customer_user.id)
    db_session.add(customer)
    await db_session.flush()

    estimate = FareEstimate(
        id=str(uuid.uuid4()),
        category_id=category.id,
        fare_rule_id=fare_rule.id,
        pickup_latitude=28.6139,
        pickup_longitude=77.2090,
        destination_latitude=28.6150,
        destination_longitude=77.2150,
        distance_km=1.0,
        estimated_duration_minutes=5.0,
        estimated_fare_cents=2500,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=10),
    )
    db_session.add(estimate)
    await db_session.commit()

    with pytest.raises(HTTPException) as exc:
        await RideService(db_session).create_ride_request(
            customer_user_id=customer_user.id,
            estimate_id=estimate.id,
            category_id=category.id,
        )
    assert exc.value.status_code == 409
    assert "No eligible online driver" in exc.value.detail

    created = (await db_session.execute(
        select(Ride).where(Ride.customer_id == customer.id)
    )).scalars().all()
    assert created == []
