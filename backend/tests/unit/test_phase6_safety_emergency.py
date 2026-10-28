import pytest
import uuid
from datetime import datetime, timezone, timedelta
from fastapi import HTTPException
from backend.application.services.safety_advanced_service import SafetyAdvancedService
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, Vehicle, Ride,
    EmergencyContact, IncidentReport, DriverBlock, EmergencyAlert
)

@pytest.mark.asyncio
async def test_part6_emergency_sos_and_broadcast(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    customer = User(phone_number=f"+91983{u_suffix}1", full_name="Lord Julian", role="customer", is_active=True, is_verified=True)
    db_session.add(customer)
    await db_session.flush()

    contact1 = EmergencyContact(
        user_id=customer.id,
        name="Lady Eleanor",
        relationship="Spouse",
        phone_number="+919811122233",
        notify_on_trip_start=True,
        notify_on_arrival=True
    )
    contact2 = EmergencyContact(
        user_id=customer.id,
        name="Chief Security Officer",
        relationship="Security Detail",
        phone_number="+919811144455",
        notify_on_trip_start=True,
        notify_on_arrival=True
    )
    db_session.add_all([contact1, contact2])
    await db_session.flush()

    ride = Ride(
        customer_id=customer.id,
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="IN_PROGRESS",
        pickup_address="The Oberoi, New Delhi",
        pickup_latitude=28.5990,
        pickup_longitude=77.2370,
        destination_address="Indira Gandhi International Airport T3",
        destination_latitude=28.5562,
        destination_longitude=77.1000,
        estimated_distance_km=18.5,
        estimated_duration_minutes=30.0,
        estimated_fare_cents=150000,
        start_otp="8492"
    )
    db_session.add(ride)
    await db_session.commit()

    service = SafetyAdvancedService(db_session)

    # Feature 126 & 129: In-Trip Emergency SOS Button
    sos_res = await service.trigger_emergency_sos(
        ride_id=ride.id,
        current_user=customer,
        latitude=28.5800,
        longitude=77.1800,
        alert_type="SOS_BUTTON",
        notes="Customer felt vehicle was following unusual route."
    )
    assert sos_res["success"] is True
    assert sos_res["status"] == "SOS_ACTIVE"
    assert sos_res["contacts_notified_count"] == 2
    assert "track" in sos_res["emergency_share_url"]
    assert sos_res["soc_hotline"] == "+91 22 6900 4800"

    await db_session.refresh(ride)
    assert ride.sos_triggered is True
    assert ride.sos_triggered_at is not None

@pytest.mark.asyncio
async def test_part6_emergency_contacts_crud(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+91983{u_suffix}2", full_name="Ambassador Vance", role="customer", is_active=True, is_verified=True)
    db_session.add(user)
    await db_session.commit()

    service = SafetyAdvancedService(db_session)

    # Feature 127: Setup contacts
    c1 = await service.add_emergency_contact(user.id, {
        "name": "Diplomatic Attache",
        "relationship": "Executive Assistant",
        "phone_number": "+919822233344",
        "notify_on_trip_start": True,
        "notify_on_arrival": True
    })
    assert c1["name"] == "Diplomatic Attache"

    contacts = await service.get_emergency_contacts(user.id)
    assert len(contacts) == 1
    assert contacts[0]["relationship"] == "Executive Assistant"

    # Delete contact
    del_res = await service.delete_emergency_contact(user.id, c1["id"])
    assert del_res["success"] is True

    contacts_after = await service.get_emergency_contacts(user.id)
    assert len(contacts_after) == 0

@pytest.mark.asyncio
async def test_part6_safety_checkin_and_auto_escalation(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+91983{u_suffix}3", full_name="Baroness Sterling", role="customer", is_active=True, is_verified=True)
    db_session.add(user)
    await db_session.flush()

    ride = Ride(
        customer_id=user.id,
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="IN_PROGRESS",
        pickup_address="Aerocity Worldmark",
        pickup_latitude=28.5502,
        pickup_longitude=77.1213,
        destination_address="Golf Course Road, Gurgaon",
        destination_latitude=28.4600,
        destination_longitude=77.0900,
        estimated_distance_km=14.0,
        estimated_duration_minutes=25.0,
        estimated_fare_cents=120000,
        start_otp="5912"
    )
    db_session.add(ride)
    await db_session.commit()

    service = SafetyAdvancedService(db_session)

    # Feature 128: Schedule safety check-in timer
    sched = await service.schedule_safety_checkin(ride.id, minutes_from_now=15)
    assert sched["status"] == "SCHEDULED"
    assert "safety_checkin_due_at" in sched

    # Acknowledge all good
    ack = await service.respond_to_safety_checkin(ride.id, user, "ALL_GOOD")
    assert ack["status"] == "ACKNOWLEDGED"

    # Test NEED_HELP auto escalation
    sos_from_checkin = await service.respond_to_safety_checkin(ride.id, user, "NEED_HELP")
    assert sos_from_checkin["status"] == "SOS_ACTIVE"

@pytest.mark.asyncio
async def test_part6_driver_and_vehicle_verification(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    driver_user = User(phone_number=f"+91983{u_suffix}4", full_name="Master Chauffeur Vikramaditya", role="driver", is_active=True, is_verified=True)
    db_session.add(driver_user)
    await db_session.flush()

    drv_profile = DriverProfile(
        user_id=driver_user.id,
        license_number=f"DL01-{u_suffix}",
        rating_average=4.99,
        total_trips=1680,
        security_badge_id="GC-SEC-9901",
        background_check_status="VERIFIED",
        academy_certified=True,
        academy_level="MASTER_CHAUFFEUR"
    )
    vehicle = Vehicle(
        license_plate=f"DL 1V BB {u_suffix}",
        make="Mercedes-Benz",
        model="Maybach S680",
        year=2025,
        color="Obsidian Black",
        category_id="cat-vip",
        rc_number=f"DL-01-RC-2025-{u_suffix}",
        permit_number=f"DL-COMM-PVT-{u_suffix}",
        insurance_policy_number=f"POL-BAJAJ-ALL-{u_suffix}"
    )
    db_session.add_all([drv_profile, vehicle])
    await db_session.flush()

    ride = Ride(
        customer_id="cust-test-verif",
        assigned_driver_id=drv_profile.id,
        vehicle_id=vehicle.id,
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="ASSIGNED",
        pickup_address="Taj Palace Hotel, Chanakyapuri",
        pickup_latitude=28.5975,
        pickup_longitude=77.1738,
        destination_address="Noida Film City",
        destination_latitude=28.5670,
        destination_longitude=77.3200,
        estimated_distance_km=24.0,
        estimated_duration_minutes=40.0,
        estimated_fare_cents=210000,
        start_otp="4198"
    )
    db_session.add(ride)
    await db_session.commit()

    service = SafetyAdvancedService(db_session)

    # Feature 130: Pickup verification code
    code_res = await service.get_pickup_verification_code(ride.id, driver_user)
    assert code_res["verification_code"] == "4198"
    assert code_res["status"] == "AWAITING_CHAUFFEUR"

    # Feature 131: Driver identity verification display
    d_res = await service.get_driver_credentials(ride.id)
    assert d_res["security_badge_id"] == "GC-SEC-9901"
    assert d_res["background_check_status"] == "VERIFIED"
    assert d_res["academy_level"] == "MASTER_CHAUFFEUR"

    # Feature 132: Vehicle registration verification display
    v_res = await service.get_vehicle_credentials(ride.id)
    assert v_res["license_plate"] == f"DL 1V BB {u_suffix}"
    assert v_res["registration_certificate_rc"] == f"DL-01-RC-2025-{u_suffix}"
    assert v_res["commercial_permit_number"] == f"DL-COMM-PVT-{u_suffix}"
    assert v_res["insurance_policy_number"] == f"POL-BAJAJ-ALL-{u_suffix}"

@pytest.mark.asyncio
async def test_part6_route_anomaly_and_unexpected_stop(db_session):
    ride = Ride(
        customer_id="cust-test-anomaly",
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="IN_PROGRESS",
        pickup_address="The Lodhi, Lodhi Road",
        pickup_latitude=28.5910,
        pickup_longitude=77.2380,
        destination_address="Aerocity Worldmark",
        destination_latitude=28.5502,
        destination_longitude=77.1213,
        estimated_distance_km=15.0,
        estimated_duration_minutes=25.0,
        estimated_fare_cents=130000,
        start_otp="8833"
    )
    db_session.add(ride)
    await db_session.commit()

    service = SafetyAdvancedService(db_session)

    # Feature 134: Route anomaly detection
    # Normal point along Delhi-Gurgaon expressway: 28.57, 77.18
    norm_res = await service.check_route_anomaly(ride.id, 28.5700, 77.1800)
    assert norm_res["anomaly_detected"] is False

    # Severe divergence to far north Delhi (28.72, 77.15) > 15km detour
    anom_res = await service.check_route_anomaly(ride.id, 28.7200, 77.1500)
    assert anom_res["anomaly_detected"] is True
    assert anom_res["anomaly_type"] == "UNEXPECTED_DETOUR"

    # Feature 135: Unexpected stop detection
    # Stationary for 240 seconds (> 180s threshold) outside destination
    stop_res = await service.check_unexpected_stop(ride.id, 28.5700, 77.1800, stationary_duration_seconds=240)
    assert stop_res["unexpected_stop_detected"] is True
    assert stop_res["safety_prompt_required"] is True

@pytest.mark.asyncio
async def test_part6_audio_recording_consent_and_status(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    cust = User(phone_number=f"+91983{u_suffix}5", full_name="Marquess of Bath", role="customer", is_active=True, is_verified=True)
    db_session.add(cust)
    await db_session.flush()

    ride = Ride(
        customer_id="cust-test-rec",
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="IN_PROGRESS",
        pickup_address="Oberoi New Delhi",
        pickup_latitude=28.5990,
        pickup_longitude=77.2370,
        destination_address="Taj Mahal Hotel",
        destination_latitude=28.6053,
        destination_longitude=77.2250,
        estimated_distance_km=4.0,
        estimated_duration_minutes=10.0,
        estimated_fare_cents=60000,
        start_otp="1122"
    )
    db_session.add(ride)
    await db_session.commit()

    service = SafetyAdvancedService(db_session)

    # Feature 136 & 137: Recording consent workflow & dual-party activation
    # 1. Passenger gives consent
    c_res = await service.update_recording_consent(ride.id, cust, role="CUSTOMER", consented=True)
    assert c_res["passenger_consent"] is True
    assert c_res["is_recording_active"] is False  # Awaiting driver consent

    # 2. Driver gives consent
    d_res = await service.update_recording_consent(ride.id, cust, role="DRIVER", consented=True)
    assert d_res["driver_consent"] is True
    assert d_res["is_recording_active"] is True
    assert d_res["recording_session_id"] is not None

    # Status check
    stat = await service.get_recording_status(ride.id)
    assert stat["is_recording_active"] is True
    assert stat["encryption_algorithm"] == "AES-256-GCM"

@pytest.mark.asyncio
async def test_part6_incident_reporting_and_driver_block(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    cust = User(phone_number=f"+91983{u_suffix}6", full_name="Sir Arthur", role="customer", is_active=True, is_verified=True)
    admin = User(phone_number=f"+91983{u_suffix}7", full_name="Security Director", role="ADMIN", is_active=True, is_verified=True)
    db_session.add_all([cust, admin])
    await db_session.flush()

    cust_prof = CustomerProfile(user_id=cust.id)
    db_session.add(cust_prof)
    await db_session.commit()

    service = SafetyAdvancedService(db_session)

    # Feature 138: Customer incident report
    inc = await service.lodge_incident_report(cust, "CUSTOMER", {
        "category": "AGGRESSIVE_DRIVING",
        "description": "Chauffeur exceeded lane boundaries on Barapullah flyover.",
        "severity": "MEDIUM"
    })
    ticket = inc["ticket_number"]
    assert ticket.startswith("INC-")

    # Feature 140: Incident tracking
    track = await service.get_incident_tracking(ticket, cust)
    assert track["ticket_number"] == ticket
    assert len(track["timeline"]) == 5

    # Feature 141: Block driver
    block = await service.block_driver(
        customer_user=cust,
        driver_id="drv-bad-apple",
        ride_id=None,
        reason="Speeding and rude demeanor",
        report_incident=True,
        incident_description="Repeatedly ignored speed calming requests."
    )
    assert block["success"] is True
    assert block["incident_ticket_number"] is not None

    # Feature 142: Report unsafe vehicle
    veh_inc = await service.report_unsafe_vehicle(
        current_user=cust,
        ride_id="ride-xyz",
        defect_type="AIR_CONDITIONING_ODOR",
        description="Air purification system exhibited electrical ozone smell."
    )
    assert veh_inc["defect_type"] == "AIR_CONDITIONING_ODOR"

    # Feature 143: Report harassment zero-tolerance
    crit_inc = await service.report_harassment(
        current_user=cust,
        ride_id="ride-xyz",
        perpetrator_role="DRIVER",
        description="Chauffeur used inappropriate language toward executive guest.",
        request_supervisor_call=True
    )
    assert crit_inc["severity"] == "CRITICAL"
    assert crit_inc["status"] == "ESCALATED"

    # Feature 150: Admin audit trail & resolution
    admin_cases = await service.list_safety_cases_for_admin(admin)
    assert len(admin_cases["incident_cases"]) >= 3

    resolve_res = await service.resolve_safety_case(
        ticket_number=ticket,
        resolution_notes="Chauffeur re-trained in VIP defensive driving academy; 24-hour route probation applied.",
        follow_up_action="DRIVER_ACADEMY_REFRESHER",
        current_user=admin
    )
    assert resolve_res["status"] == "RESOLVED"

@pytest.mark.asyncio
async def test_part6_safe_zones_restricted_zones_and_education(db_session):
    service = SafetyAdvancedService(db_session)

    # Feature 133: Safety center
    safety_center = await service.get_safety_center_info()
    assert safety_center["soc_command_center"]["phone_number"] == "+91 22 6900 4800"
    assert len(safety_center["emergency_helplines"]) >= 4

    # Feature 147: Safe pickup zone suggestions near Mansingh Road
    safe_zones = await service.get_safe_pickup_zones(28.6053, 77.2250)
    assert len(safe_zones) >= 4
    assert safe_zones[0]["name"] == "The Taj Mahal Hotel - VIP Portico"
    assert safe_zones[0]["distance_km"] < 0.1

    # Feature 148: Geofenced restricted pickup warning near Red Fort
    restr_check = await service.check_restricted_pickup_warning(28.6562, 77.2410)
    assert restr_check["is_restricted"] is True
    assert "Red Fort" in restr_check["zone_name"]

    # Outside restricted zone
    restr_clear = await service.check_restricted_pickup_warning(28.5500, 77.1000)
    assert restr_clear["is_restricted"] is False

    # Feature 149: Safety education cards
    cards = await service.get_safety_education_cards()
    assert len(cards) >= 5
    assert any(c["category"] == "VERIFICATION" for c in cards)
    assert any(c["category"] == "LEGAL_TECH" for c in cards)
