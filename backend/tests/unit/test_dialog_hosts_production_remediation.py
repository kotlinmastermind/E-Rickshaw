"""
Comprehensive unit tests for Rider and Driver Dialog Hosts Production Remediation.
Tests all 5 features:
1. Split Fare popup backend logic (equal share, DB persistence, split queries).
2. Rider & Pilot Emergency SOS (GPS validation, active ride vs non-ride SOS, EmergencyAlert DB persistence).
3. Driver Route Deviation (anomaly reporting, detour reason, driver comments, RouteDeviationEvent DB persistence).
4. Driver Toll Entry (driver ownership, fare adjustment, ledger credit, duplicate prevention, RideToll DB persistence).
5. Airport Flight Delay Auto-Rescheduling (pickup rescheduling, flight sync, RideRescheduleLog DB persistence).
"""

import uuid
import pytest
from datetime import datetime, timezone, timedelta
from fastapi import HTTPException
from sqlalchemy import select

from backend.domain.models import (
    User, CustomerProfile, DriverProfile, Vehicle, VehicleCategory, Ride,
    EmergencyContact, EmergencyAlert, RouteDeviationEvent, RideToll,
    RideRescheduleLog, DriverLedgerEntry
)
from backend.application.services.settlement_advanced_service import SettlementAdvancedService
from backend.application.services.safety_advanced_service import SafetyAdvancedService
from backend.application.services.driver_financial_service import DriverFinancialService
from backend.application.services.booking_advanced_service import BookingAdvancedService


@pytest.mark.asyncio
async def test_feature_1_split_fare_advanced(db_session):
    """Test Feature 1: Split fare initiation with phone numbers and equal share calculation."""
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    uid = uuid.uuid4().hex[:6]

    customer = User(phone_number=f"+91981{uid}1", full_name="Initiator Customer", role="customer", is_active=True, is_verified=True)
    db_session.add(customer)
    await db_session.flush()

    ride = Ride(
        customer_id=customer.id,
        vehicle_category_id=cat.id if cat else "cat-std",
        fare_rule_id="rule-std",
        status="IN_PROGRESS",
        pickup_address="Connaught Place, New Delhi",
        pickup_latitude=28.6315,
        pickup_longitude=77.2167,
        destination_address="Cyber Hub, Gurugram",
        destination_latitude=28.4986,
        destination_longitude=77.0878,
        estimated_distance_km=25.0,
        estimated_duration_minutes=45.0,
        estimated_fare_cents=60000,
        final_fare_cents=60000,
        start_otp="4321"
    )
    db_session.add(ride)
    await db_session.commit()

    service = SettlementAdvancedService(db_session)
    co_passenger_phones = ["+919876543210", "+919876543211"]

    # 1. Initiate split fare (3 participants total: inviter + 2 co-passengers)
    splits_created = await service.initiate_split_fare(
        ride_id=ride.id,
        inviter_user_id=customer.id,
        splits=co_passenger_phones
    )

    assert len(splits_created) == 2
    # 60000 cents / 3 people = 20000 cents (₹200 each)
    assert splits_created[0]["share_amount_cents"] == 20000
    assert splits_created[0]["share_amount_rupees"] == 200.0
    assert splits_created[1]["share_amount_cents"] == 20000

    # 2. Query split fare status
    status_res = await service.get_ride_split_status(ride_id=ride.id, user_id=customer.id)
    assert status_res["total_fare_cents"] == 60000
    assert len(status_res["splits"]) == 2

    # 3. Validation: Non-initiator cannot re-initiate
    unauthorized_user = User(phone_number=f"+91982{uid}2", role="customer", is_active=True)
    db_session.add(unauthorized_user)
    await db_session.commit()

    with pytest.raises(HTTPException) as exc_info:
        await service.initiate_split_fare(ride_id=ride.id, inviter_user_id=unauthorized_user.id, splits=co_passenger_phones)
    assert exc_info.value.status_code == 403


@pytest.mark.asyncio
async def test_feature_2_emergency_sos_with_and_without_ride(db_session):
    """Test Feature 2: SOS with GPS coordinate validation, with active ride and without active ride."""
    uid = uuid.uuid4().hex[:6]
    customer = User(phone_number=f"+91983{uid}1", full_name="Emergency Rider", role="customer", is_active=True, is_verified=True)
    db_session.add(customer)
    await db_session.flush()

    contact = EmergencyContact(
        user_id=customer.id,
        name="Security Contact",
        relationship="Parent",
        phone_number="+919811122299",
        notify_on_trip_start=True,
        notify_on_arrival=True,
        is_active=True
    )
    db_session.add(contact)
    await db_session.flush()

    service = SafetyAdvancedService(db_session)

    # 1. Coordinate validation failure
    with pytest.raises(HTTPException) as exc_info:
        await service.trigger_emergency_sos(
            ride_id=None,
            current_user=customer,
            latitude=95.0,  # Invalid latitude > 90
            longitude=77.2,
            notes="Invalid coords"
        )
    assert exc_info.value.status_code == 400

    # 2. Standalone SOS (without active ride - e.g. waiting at stop)
    res_no_ride = await service.trigger_emergency_sos(
        ride_id=None,
        current_user=customer,
        latitude=28.6289,
        longitude=77.3710,
        notes="Urgent: Suspicious activity near transit hub"
    )
    assert res_no_ride["success"] is True
    assert res_no_ride["status"] == "SOS_ACTIVE"
    assert res_no_ride["alert_id"] is not None
    assert res_no_ride["ride_id"] is None
    assert res_no_ride["contacts_notified_count"] == 1

    # Verify EmergencyAlert persisted in DB
    alert_db = await db_session.get(EmergencyAlert, res_no_ride["alert_id"])
    assert alert_db is not None
    assert alert_db.triggered_by_user_id == customer.id
    assert alert_db.latitude == 28.6289
    assert alert_db.longitude == 77.3710
    assert alert_db.ride_id is None

    # 3. SOS with active ride
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    ride = Ride(
        customer_id=customer.id,
        vehicle_category_id=cat.id if cat else "cat-std",
        fare_rule_id="rule-std",
        status="IN_PROGRESS",
        pickup_address="Noida City Centre",
        pickup_latitude=28.5747,
        pickup_longitude=77.3560,
        destination_address="Botanical Garden",
        destination_latitude=28.5645,
        destination_longitude=77.3343,
        estimated_distance_km=3.5,
        estimated_duration_minutes=10.0,
        estimated_fare_cents=10000,
        start_otp="8877"
    )
    db_session.add(ride)
    await db_session.commit()

    res_with_ride = await service.trigger_emergency_sos(
        ride_id=ride.id,
        current_user=customer,
        latitude=28.5700,
        longitude=77.3400,
        notes="Pilot deviating in unknown direction"
    )
    assert res_with_ride["success"] is True
    assert res_with_ride["ride_id"] == ride.id
    assert res_with_ride["status"] == "SOS_ACTIVE"

    await db_session.refresh(ride)
    assert ride.sos_triggered is True
    assert ride.sos_triggered_at is not None


@pytest.mark.asyncio
async def test_feature_3_driver_route_deviation_reason(db_session):
    """Test Feature 3: Driver route deviation / anomaly check capturing driver comments & reason."""
    uid = uuid.uuid4().hex[:6]
    driver_user = User(phone_number=f"+91984{uid}1", full_name="Pilot Kumar", role="driver", is_active=True)
    db_session.add(driver_user)
    await db_session.flush()

    driver_profile = DriverProfile(user_id=driver_user.id, license_number=f"DL-DEV-{uid}", approval_status="APPROVED")
    db_session.add(driver_profile)
    await db_session.flush()

    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    ride = Ride(
        customer_id=driver_user.id,
        assigned_driver_id=driver_profile.id,
        vehicle_category_id=cat.id if cat else "cat-std",
        fare_rule_id="rule-std",
        status="IN_PROGRESS",
        pickup_address="Akshardham",
        pickup_latitude=28.6127,
        pickup_longitude=77.2773,
        destination_address="Mayur Vihar Phase 1",
        destination_latitude=28.6045,
        destination_longitude=77.2974,
        estimated_distance_km=4.0,
        estimated_duration_minutes=12.0,
        estimated_fare_cents=12000,
        start_otp="5521"
    )
    db_session.add(ride)
    await db_session.commit()

    service = SafetyAdvancedService(db_session)

    # Pilot submits detour reason: Construction work ahead
    result = await service.check_route_anomaly(
        ride_id=ride.id,
        current_lat=28.6180,
        current_lng=77.2850,
        deviation_reason="ROAD_CONSTRUCTION_DETOUR",
        comments="NH24 flyover maintenance, taking service road bypass",
        user=driver_user
    )

    assert result["anomaly_detected"] is True
    assert result["deviation_reason"] == "ROAD_CONSTRUCTION_DETOUR"
    assert result["status"] == "ALERT_TRIGGERED"

    # Verify RouteDeviationEvent persisted in DB
    ev_stmt = select(RouteDeviationEvent).where(RouteDeviationEvent.ride_id == ride.id)
    ev = (await db_session.execute(ev_stmt)).scalars().first()
    assert ev is not None
    assert ev.reason == "ROAD_CONSTRUCTION_DETOUR"
    assert ev.driver_comments == "NH24 flyover maintenance, taking service road bypass"

    # Verify ride anomaly flag updated
    await db_session.refresh(ride)
    assert ride.route_deviation_detected is True
    assert ride.route_deviation_reason == "ROAD_CONSTRUCTION_DETOUR"


@pytest.mark.asyncio
async def test_feature_4_driver_toll_entry_and_fare_credit(db_session):
    """Test Feature 4: Driver toll submission, validation, fare adjustment, ledger entry, and duplicate prevention."""
    uid = uuid.uuid4().hex[:6]
    driver_user = User(phone_number=f"+91985{uid}1", full_name="Pilot Toll", role="driver", is_active=True)
    other_user = User(phone_number=f"+91985{uid}2", full_name="Other Pilot", role="driver", is_active=True)
    db_session.add_all([driver_user, other_user])
    await db_session.flush()

    driver_profile = DriverProfile(user_id=driver_user.id, license_number=f"DL-TOLL-{uid}", approval_status="APPROVED")
    other_profile = DriverProfile(user_id=other_user.id, license_number=f"DL-TOLL2-{uid}", approval_status="APPROVED")
    db_session.add_all([driver_profile, other_profile])
    await db_session.flush()

    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    ride = Ride(
        customer_id=driver_user.id,
        assigned_driver_id=driver_profile.id,
        vehicle_category_id=cat.id if cat else "cat-std",
        fare_rule_id="rule-std",
        status="IN_PROGRESS",
        pickup_address="Noida Sector 18",
        pickup_latitude=28.5708,
        pickup_longitude=77.3271,
        destination_address="Connaught Place, Delhi",
        destination_latitude=28.6315,
        destination_longitude=77.2167,
        estimated_distance_km=18.0,
        estimated_duration_minutes=35.0,
        estimated_fare_cents=45000,
        final_fare_cents=45000,
        start_otp="7711"
    )
    db_session.add(ride)
    await db_session.commit()

    service = DriverFinancialService(db_session)

    # 1. Unauthorized driver cannot submit toll
    with pytest.raises(HTTPException) as exc_info:
        await service.record_ride_toll(
            user_id_or_driver_id=other_user.id,
            ride_id=ride.id,
            toll_amount_rupees=100.0,
            toll_name="DND Flyway Toll Plaza"
        )
    assert exc_info.value.status_code == 403

    # 2. Authorized driver submits ₹100 toll
    toll_res = await service.record_ride_toll(
        user_id_or_driver_id=driver_user.id,
        ride_id=ride.id,
        toll_amount_rupees=100.0,
        toll_name="DND Flyway Toll Plaza",
        latitude=28.5823,
        longitude=77.3001
    )

    assert toll_res["status"] == "APPROVED"
    assert toll_res["amount_cents"] == 10000
    assert toll_res["toll_name"] == "DND Flyway Toll Plaza"
    assert toll_res["total_ride_toll_cents"] == 10000
    assert toll_res["total_ride_fare_cents"] == 55000  # 45000 + 10000

    # Verify Ride table updated
    await db_session.refresh(ride)
    assert ride.toll_cents == 10000
    assert ride.final_fare_cents == 55000

    # Verify DriverLedgerEntry created
    ledger_entries = (await db_session.execute(
        select(DriverLedgerEntry).filter_by(reference_id=ride.id, category="TOLL_REIMBURSEMENT")
    )).scalars().all()
    assert len(ledger_entries) == 1
    assert ledger_entries[0].amount_cents == 10000

    # 3. Duplicate submission prevention (same amount within 5 mins)
    with pytest.raises(HTTPException) as exc_info:
        await service.record_ride_toll(
            user_id_or_driver_id=driver_user.id,
            ride_id=ride.id,
            toll_amount_rupees=100.0,
            toll_name="DND Flyway Toll Plaza"
        )
    assert exc_info.value.status_code == 409

    # 4. Query ride tolls
    all_tolls = await service.get_ride_tolls(user_id_or_driver_id=driver_user.id, ride_id=ride.id)
    assert len(all_tolls) == 1
    assert all_tolls[0]["amount_cents"] == 10000


@pytest.mark.asyncio
async def test_feature_5_flight_delay_rescheduling(db_session):
    """Test Feature 5: Flight delay sync and ride rescheduling with audit history logging."""
    uid = uuid.uuid4().hex[:6]
    customer = User(phone_number=f"+91986{uid}1", full_name="Airport Traveler", role="customer", is_active=True)
    db_session.add(customer)
    await db_session.flush()

    scheduled_orig = datetime.now(timezone.utc) + timedelta(hours=3)
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    ride = Ride(
        customer_id=customer.id,
        vehicle_category_id=cat.id if cat else "cat-std",
        fare_rule_id="rule-std",
        status="SCHEDULED",
        pickup_address="Indira Gandhi International Airport T3",
        pickup_latitude=28.5562,
        pickup_longitude=77.1000,
        destination_address="Sector 62, Noida",
        destination_latitude=28.6289,
        destination_longitude=77.3710,
        estimated_distance_km=38.0,
        estimated_duration_minutes=60.0,
        estimated_fare_cents=120000,
        scheduled_pickup_time=scheduled_orig,
        start_otp="9922"
    )
    db_session.add(ride)
    await db_session.commit()

    service = BookingAdvancedService(db_session)

    # 1. Reschedule ride by 90 minutes due to flight delay
    new_pickup = scheduled_orig + timedelta(minutes=90)
    resched_res = await service.reschedule_ride(
        ride_id=ride.id,
        user_id=customer.id,
        new_pickup_time=new_pickup,
        reason="FLIGHT_DELAY_AIRPORT_POPUP",
        flight_number="AI-102"
    )

    assert resched_res["status"] == "RESCHEDULED"
    assert resched_res["delay_minutes"] == 90
    assert resched_res["flight_number"] == "AI-102"

    # Verify Ride table scheduled_pickup_time was updated
    await db_session.refresh(ride)
    ride_pickup = ride.scheduled_pickup_time if ride.scheduled_pickup_time.tzinfo else ride.scheduled_pickup_time.replace(tzinfo=timezone.utc)
    assert abs((ride_pickup - new_pickup).total_seconds()) < 5

    # Verify RideRescheduleLog audit entry was recorded
    logs = (await db_session.execute(
        select(RideRescheduleLog).filter_by(ride_id=ride.id)
    )).scalars().all()
    assert len(logs) == 1
    assert logs[0].requested_by_user_id == customer.id
    assert logs[0].flight_number == "AI-102"
    assert logs[0].delay_minutes == 90
    assert logs[0].reason == "FLIGHT_DELAY_AIRPORT_POPUP"

    # 2. Automated flight sync
    sync_res = await service.sync_flight_delay_to_ride(
        ride_id=ride.id,
        flight_number="AI-102",
        user_id=customer.id
    )
    assert sync_res["flight_number"] == "AI-102"
    assert "delay_minutes" in sync_res

    # Verify second audit entry logged
    logs_after = (await db_session.execute(
        select(RideRescheduleLog).filter_by(ride_id=ride.id)
    )).scalars().all()
    assert len(logs_after) == 2
