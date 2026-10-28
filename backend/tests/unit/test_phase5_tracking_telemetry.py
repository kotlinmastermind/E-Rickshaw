import pytest
import uuid
from datetime import datetime, timezone, timedelta
from fastapi import HTTPException
from backend.application.services.tracking_advanced_service import TrackingAdvancedService
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, Ride, TripShareLink, LostItemReport
)

@pytest.mark.asyncio
async def test_part5_telemetry_and_confidence_range(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    customer = User(phone_number=f"+91981{u_suffix}1", full_name="VIP Lord Hastings", role="customer", is_active=True, is_verified=True)
    driver_user = User(phone_number=f"+91981{u_suffix}2", full_name="Master Chauffeur Alistair", role="driver", is_active=True, is_verified=True)
    db_session.add_all([customer, driver_user])
    await db_session.flush()

    cust_profile = CustomerProfile(user_id=customer.id)
    drv_profile = DriverProfile(user_id=driver_user.id, license_number=f"MH01-{u_suffix}", rating_average=4.98, total_trips=210)
    db_session.add_all([cust_profile, drv_profile])
    await db_session.flush()

    ride = Ride(
        customer_id=cust_profile.id,
        assigned_driver_id=drv_profile.id,
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="ASSIGNED",
        pickup_address="Portico A, The Taj Mahal Palace, Mumbai",
        pickup_latitude=18.9220,
        pickup_longitude=72.8347,
        destination_address="VIP Terminal 2, Chhatrapati Shivaji Maharaj International Airport",
        destination_latitude=19.0974,
        destination_longitude=72.8744,
        estimated_distance_km=22.5,
        estimated_duration_minutes=35.0,
        estimated_fare_cents=180000,
        start_otp="8492"
    )
    db_session.add(ride)
    await db_session.commit()

    service = TrackingAdvancedService(db_session)
    telemetry = await service.get_advanced_tracking_telemetry(ride.id, customer)

    # Feature 101, 102, 103, 104, 116, 120, 121, 122
    assert telemetry["ride_id"] == ride.id
    assert telemetry["eta_range_min"] <= telemetry["eta_range_max"]
    assert telemetry["eta_confidence_percentage"] == 95
    assert telemetry["pickup_countdown_seconds"] > 0
    assert telemetry["freshness_status"] in ["FRESH_GPS", "SLIGHT_DELAY", "STALE_SIGNAL"]
    assert telemetry["vehicle_plate_number"] is not None
    assert "8492" in telemetry["accessibility_announcement"]
    assert len(telemetry["timeline"]) == 5
    assert telemetry["masked_virtual_number"] == "+91 22 6900 4820"

@pytest.mark.asyncio
async def test_part5_trip_share_link_and_privacy_shield(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    customer = User(phone_number=f"+91982{u_suffix}3", full_name="Lady Catherine", role="customer", is_active=True, is_verified=True)
    db_session.add(customer)
    await db_session.flush()

    cust_profile = CustomerProfile(user_id=customer.id)
    db_session.add(cust_profile)
    await db_session.flush()

    ride = Ride(
        customer_id=cust_profile.id,
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="IN_PROGRESS",
        pickup_address="Flat 402, Sea Face Towers, Worli, Mumbai",
        pickup_latitude=19.0144,
        pickup_longitude=72.8179,
        destination_address="Bandra Kurla Complex, G Block, Bandra East, Mumbai",
        destination_latitude=19.0657,
        destination_longitude=72.8687,
        estimated_distance_km=14.0,
        estimated_duration_minutes=22.0,
        estimated_fare_cents=120000,
        start_otp="1234"
    )
    db_session.add(ride)
    await db_session.commit()

    service = TrackingAdvancedService(db_session)

    # Feature 107, 108, 109: Generate time-limited link with privacy shield
    share_res = await service.create_trip_share_link(
        ride_id=ride.id,
        user_id=customer.id,
        recipient_name="Executive Assistant",
        recipient_phone="+919820099999",
        hide_exact_address=True,
        expiry_hours=4
    )

    assert share_res["share_token"].startswith("trk_")
    assert share_res["hide_exact_address"] is True
    assert "track/share/" in share_res["share_url"]

    # Resolve shared trip as external contact
    resolved = await service.resolve_shared_trip(share_res["share_token"])
    assert resolved["ride_id"] == ride.id
    assert resolved["privacy_shield_active"] is True
    # Verify street/flat number was shielded to neighborhood
    assert "Flat 402" not in resolved["pickup_label"]
    assert "Worli" in resolved["pickup_label"] or "Near" in resolved["pickup_label"]

@pytest.mark.asyncio
async def test_part5_chat_quick_replies_translations_and_meeting_point(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    customer = User(phone_number=f"+91983{u_suffix}4", full_name="Baron Sterling", role="customer", is_active=True, is_verified=True)
    db_session.add(customer)
    await db_session.flush()

    cust_profile = CustomerProfile(user_id=customer.id)
    db_session.add(cust_profile)
    await db_session.flush()

    ride = Ride(
        customer_id=cust_profile.id,
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="ASSIGNED",
        pickup_address="The Oberoi, Nariman Point, Mumbai",
        pickup_latitude=18.9272,
        pickup_longitude=72.8205,
        destination_address="Antilia, Altamount Road, Mumbai",
        destination_latitude=18.9678,
        destination_longitude=72.8093,
        estimated_distance_km=8.5,
        estimated_duration_minutes=18.0,
        estimated_fare_cents=150000,
        start_otp="1234"
    )
    db_session.add(ride)
    await db_session.commit()

    service = TrackingAdvancedService(db_session)

    # Feature 112: Suggested Quick replies
    c_replies = service.get_quick_reply_templates(sender_role="customer")
    assert len(c_replies) >= 3
    d_replies = service.get_quick_reply_templates(sender_role="driver")
    assert len(d_replies) >= 3

    # Feature 113: Translations
    hi_trans = await service.translate_message("I have arrived at the main VIP portico.", "hi")
    assert "पोर्च" in hi_trans["translated_text"] or "पहुंच" in hi_trans["translated_text"]

    mr_trans = await service.translate_message("I have arrived at the main VIP portico.", "mr")
    assert "पोहोचलो" in mr_trans["translated_text"] or "व्हीआयपी" in mr_trans["translated_text"]

    # Feature 114: Pickup Meeting Point Chat
    point_res = await service.send_meeting_point_message(
        ride_id=ride.id,
        sender_user_id=customer.id,
        sender_role="CUSTOMER",
        point_name="Grand Valet Bay 2",
        lat=18.9275,
        lng=72.8210
    )
    assert point_res["meeting_point_name"] == "Grand Valet Bay 2"
    assert point_res["latitude"] == 18.9275

@pytest.mark.asyncio
async def test_part5_arrival_photo_and_route_preferences(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    customer = User(phone_number=f"+91984{u_suffix}5", full_name="Duchess Eleanor", role="customer", is_active=True, is_verified=True)
    db_session.add(customer)
    await db_session.flush()

    cust_profile = CustomerProfile(user_id=customer.id)
    db_session.add(cust_profile)
    await db_session.flush()

    ride = Ride(
        customer_id=cust_profile.id,
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="ASSIGNED",
        pickup_address="St. Regis Hotel, Lower Parel, Mumbai",
        pickup_latitude=18.9944,
        pickup_longitude=72.8248,
        destination_address="Bandra Fort, Bandra West, Mumbai",
        destination_latitude=19.0416,
        destination_longitude=72.8190,
        estimated_distance_km=11.2,
        estimated_duration_minutes=20.0,
        estimated_fare_cents=130000,
        start_otp="1234"
    )
    db_session.add(ride)
    await db_session.commit()

    service = TrackingAdvancedService(db_session)

    # Feature 115: Driver Arrival Photo
    photo_res = await service.update_driver_arrival_photo(
        ride_id=ride.id,
        photo_url="https://images.luxurychauffeur.com/staging/taj-arrival.jpg"
    )
    assert photo_res["status"] == "ARRIVED"
    assert "taj-arrival.jpg" in photo_res["driver_arrival_photo_url"]

    # Feature 117 & 119: Route preview and preference selection
    pref_res = await service.update_route_preference(ride.id, "SCENIC")
    assert pref_res["route_preference"] == "SCENIC"

    preview = await service.get_route_preview(ride.id)
    assert preview["route_preference"] == "SCENIC"
    assert len(preview["waypoints"]) == 4

@pytest.mark.asyncio
async def test_part5_receipt_lost_item_and_contact_driver_window(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    customer = User(phone_number=f"+91985{u_suffix}6", full_name="Sir Arthur Conan", role="customer", is_active=True, is_verified=True)
    db_session.add(customer)
    await db_session.flush()

    cust_profile = CustomerProfile(user_id=customer.id)
    db_session.add(cust_profile)
    await db_session.flush()

    ride = Ride(
        customer_id=cust_profile.id,
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="COMPLETED",
        pickup_address="Churchgate Terminal, Mumbai",
        pickup_latitude=18.9322,
        pickup_longitude=72.8264,
        destination_address="Nariman Point Business Bay, Mumbai",
        destination_latitude=18.9250,
        destination_longitude=72.8220,
        estimated_distance_km=4.5,
        estimated_duration_minutes=12.0,
        estimated_fare_cents=95000,
        final_fare_cents=95000,
        start_otp="1234",
        completed_at=datetime.now(timezone.utc) - timedelta(hours=2) # 2 hours ago
    )
    db_session.add(ride)
    await db_session.commit()

    service = TrackingAdvancedService(db_session)

    # Feature 123: Trip completion digital receipt with GST SAC 9964
    rcp = await service.get_completion_receipt(ride.id)
    assert rcp["sac_code"].startswith("9964")
    assert rcp["fare_breakdown"]["total_charged_rupees"] == 950.0

    # Feature 124: Lost Item Report
    lost_res = await service.lodge_lost_item_report(
        ride_id=ride.id,
        customer_user_id=customer.id,
        item_type="LEATHER_GOODS",
        description="Montblanc Meisterstück black leather wallet",
        seat_location="Rear Executive Right",
        contact_phone="+919820055555"
    )
    assert lost_res["ticket_number"].startswith("LOST-")
    assert lost_res["status"] == "REPORTED"

    # Feature 125: Post-trip driver contact (within 72-hour window)
    contact_active = await service.get_driver_post_trip_contact(ride.id, customer.id)
    assert contact_active["can_contact_driver"] is True
    assert contact_active["time_remaining_hours"] >= 69

    # Past 72-hour window test
    ride.completed_at = datetime.now(timezone.utc) - timedelta(hours=75)
    await db_session.commit()

    contact_expired = await service.get_driver_post_trip_contact(ride.id, customer.id)
    assert contact_expired["can_contact_driver"] is False
    assert contact_expired["time_remaining_hours"] == 0
