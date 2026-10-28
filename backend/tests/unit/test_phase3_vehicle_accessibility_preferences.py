import pytest
import uuid
from datetime import datetime, timezone, timedelta
from backend.domain.models import User, CustomerProfile, VehicleCategory, FareRule, Ride, FareEstimate
from backend.application.services.booking_advanced_service import BookingAdvancedService
from backend.api.routes.vehicles import (
    list_vehicle_categories, get_category_gallery, compare_vehicle_categories, get_category_accessibility_details
)
from backend.api.routes.customer import (
    get_customer_preferences, update_customer_preferences, get_prebooking_preference_summary, CustomerPreferencesUpdate
)

@pytest.mark.asyncio
async def test_part3_vehicle_gallery_and_comparison(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    # Ensure baseline categories exist
    sedan = VehicleCategory(
        code=f"SEDAN_{u_suffix}",
        name="Executive Sedan",
        description="Mercedes E-Class",
        icon_url="https://images.unsplash.com/photo-1555215695-3004980ad54e",
        capacity=3,
        legroom_inches=40.5,
        quietness_db=54.0,
        luggage_large_capacity=2,
        luggage_small_capacity=2,
        emissions_g_per_km=110.0,
        wheelchair_accessible=False,
        step_free_height_cm=38.0,
        is_electric=False,
        pet_friendly=True,
        child_seat_available=True,
        sort_order=1
    )
    suv = VehicleCategory(
        code=f"SUV_{u_suffix}",
        name="Prestige SUV",
        description="Range Rover",
        icon_url="https://images.unsplash.com/photo-1549399542-7e3f8b79c341",
        capacity=5,
        legroom_inches=42.0,
        quietness_db=52.0,
        luggage_large_capacity=4,
        luggage_small_capacity=4,
        emissions_g_per_km=145.0,
        wheelchair_accessible=True,
        step_free_height_cm=32.0,
        is_electric=False,
        pet_friendly=True,
        child_seat_available=True,
        sort_order=2
    )
    ev = VehicleCategory(
        code=f"EV_{u_suffix}",
        name="Ultra Luxury EV",
        description="BMW i7",
        icon_url="https://images.unsplash.com/photo-1555215695-3004980ad54e",
        capacity=4,
        legroom_inches=43.0,
        quietness_db=48.0,
        luggage_large_capacity=2,
        luggage_small_capacity=2,
        emissions_g_per_km=0.0,
        wheelchair_accessible=True,
        step_free_height_cm=35.0,
        is_electric=True,
        pet_friendly=True,
        child_seat_available=True,
        sort_order=3
    )
    db_session.add_all([sedan, suv, ev])
    await db_session.commit()

    # Feature 51: Vehicle photos and interior gallery
    gallery_res = await get_category_gallery(code="SEDAN", db=db_session)
    assert gallery_res["category_code"] == "SEDAN"
    assert len(gallery_res["photos"]) >= 4
    tags = [p["tag"] for p in gallery_res["photos"]]
    assert "EXTERIOR_HERO" in tags
    assert "COCKPIT" in tags
    assert "REAR_CABIN" in tags

    # Feature 52: Vehicle feature comparison
    comp_res = await compare_vehicle_categories(db=db_session)
    assert comp_res["categories_compared"] >= 3
    assert len(comp_res["matrix"]) >= 3

    # Feature 69: Vehicle accessibility details
    acc_res = await get_category_accessibility_details(code="LUXURY_SUV", db=db_session)
    assert acc_res["accessibility_specs"]["wheelchair_hoist"] is True
    assert acc_res["accessibility_specs"]["step_free_height_cm"] <= 35.0

    # Feature 72: Vehicle availability by passenger capacity filter
    cats_passengers = await list_vehicle_categories(passengers=5, db=db_session)
    for c in cats_passengers:
        assert c["capacity"] >= 5

    # Feature 53 & 73: Luggage capacity selection & capture filter
    cats_luggage = await list_vehicle_categories(luggage_large=3, db=db_session)
    for c in cats_luggage:
        assert c["luggage_large_capacity"] >= 3

    # Feature 55: Wheelchair-accessible vehicle filter
    cats_wheelchair = await list_vehicle_categories(wheelchair_accessible=True, db=db_session)
    for c in cats_wheelchair:
        assert c["wheelchair_accessible"] is True

    # Feature 67: Electric vehicle preference filter
    cats_ev = await list_vehicle_categories(electric_only=True, db=db_session)
    for c in cats_ev:
        assert c["is_electric"] is True

    # Feature 68: Low-emission ride filter (<= 95g/km)
    cats_eco = await list_vehicle_categories(low_emissions_only=True, db=db_session)
    for c in cats_eco:
        assert c["emissions_g_per_km"] <= 95.0


@pytest.mark.asyncio
async def test_part3_customer_preferences_and_ride_booking(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    user = User(
        phone_number=f"+91986{u_suffix}1",
        full_name="Lady Genevieve",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add(user)
    await db_session.flush()

    profile = CustomerProfile(user_id=user.id)
    db_session.add(profile)
    await db_session.commit()

    # Features 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 70, 71, 74:
    # Update preferences and verify persistent profile saving
    payload = CustomerPreferencesUpdate(
        quiet_ride=True,
        temperature_celsius=20.5,
        preferred_music="Classical & Soft Jazz",
        preferred_water="Chilled Evian Sparkling",
        no_phone_call_ride=True,
        preferred_driver_language="English, French",
        female_driver_preferred=True,
        vehicle_color_preference="Obsidian Black",
        premium_amenities="Evian Water, Braided Charging Kit, Daily FT",
        electric_vehicle_preferred=True,
        low_emission_filter=True,
        wheelchair_access_required=True,
        step_free_pickup_required=True,
        assistance_animal_present=True,
        extra_legroom_required=True,
        pet_friendly_preferred=True,
        accessibility_notes="Passenger requires curb step-free assistance."
    )
    update_res = await update_customer_preferences(payload=payload, user=user, db=db_session)
    assert update_res["status"] == "PREFERENCES_SAVED"

    prefs = await get_customer_preferences(user=user, db=db_session)
    assert prefs["quiet_ride"] is True
    assert prefs["temperature_celsius"] == 20.5
    assert prefs["no_phone_call_ride"] is True
    assert prefs["female_driver_preferred"] is True
    assert prefs["wheelchair_access_required"] is True
    assert prefs["step_free_pickup_required"] is True
    assert prefs["assistance_animal_present"] is True
    assert prefs["extra_legroom_required"] is True
    assert prefs["pet_friendly_preferred"] is True
    assert prefs["accessibility_notes"] == "Passenger requires curb step-free assistance."

    # Feature 75: Customer preference summary before booking
    summary = await get_prebooking_preference_summary(user=user, db=db_session)
    assert summary["passenger_name"] == "Lady Genevieve"
    assert any("20.5°C" in item for item in summary["cabin_atmosphere"])
    assert any("Silent Discretion" in item for item in summary["cabin_atmosphere"])
    assert any("Female Chauffeur Preferred" in item for item in summary["service_etiquette"])
    assert any("Wheelchair Accessible" in item for item in summary["accessibility_compliance"])
    assert summary["ready_for_dispatch"] is True

    # Test advanced booking with Part 3 customizations (Features 53, 54, 55, 71, 73, etc.)
    # Prepare estimate & rule
    cat = VehicleCategory(code=f"VIP_TEST_{u_suffix}", name="VIP Limousine", capacity=4, sort_order=10)
    db_session.add(cat)
    await db_session.flush()

    rule = FareRule(category_id=cat.id, version=1, base_fare_cents=100000, per_km_cents=5000, per_minute_cents=1000, minimum_fare_cents=100000, is_active=True)
    db_session.add(rule)
    await db_session.flush()

    estimate = FareEstimate(
        category_id=cat.id,
        fare_rule_id=rule.id,
        pickup_latitude=18.9217,
        pickup_longitude=72.8332,
        destination_latitude=19.0657,
        destination_longitude=72.8687,
        distance_km=15.0,
        estimated_duration_minutes=35.0,
        estimated_fare_cents=180000,
        surge_multiplier=1.0,
        fare_min_cents=180000,
        fare_max_cents=210000,
        base_fare_cents=100000,
        distance_charge_cents=75000,
        time_charge_cents=35000,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=30)
    )
    db_session.add(estimate)
    await db_session.commit()

    service = BookingAdvancedService(db_session)
    booking_res = await service.create_advanced_booking(
        customer_user_id=user.id,
        estimate_id=estimate.id,
        pickup_address="The Oberoi, Nariman Point",
        destination_address="Bandra Kurla Complex",
        payment_method="UPI",
        # Features 53 & 73: Luggage Count & Size
        luggage_large_count=2,
        luggage_small_count=2,
        # Feature 54: Child Seat Request
        child_seat_requested=True,
        child_seat_type="TODDLER_FORWARD",
        # Feature 55: Wheelchair Accessible Request
        wheelchair_requested=True,
        step_free_pickup=True,
        assistance_animal=True,
        extra_legroom_requested=True,
        quiet_ride_requested=True,
        cabin_temp_requested=20.0,
        music_requested="Classical Chamber",
        no_phone_call=True,
        driver_language_requested="English",
        female_driver_requested=True,
        vehicle_color_requested="Obsidian Black",
        amenities_requested="Evian, Braided Charger",
        ev_ride_requested=True,
        pet_friendly_requested=True,
        special_assistance_notes="Passenger requires assistance with wheelchair stowage."
    )
    assert booking_res["status"] == "REQUESTED"
    ride_id = booking_res["ride_id"]

    ride = await db_session.get(Ride, ride_id)
    assert ride.child_seat_requested is True
    assert ride.child_seat_type == "TODDLER_FORWARD"
    assert ride.luggage_large_count == 2
    assert ride.luggage_small_count == 2
    assert ride.wheelchair_requested is True
    assert ride.step_free_pickup is True
    assert ride.assistance_animal is True
    assert ride.extra_legroom_requested is True
    assert ride.quiet_ride_requested is True
    assert ride.cabin_temp_requested == 20.0
    assert ride.no_phone_call is True
    assert ride.female_driver_requested is True
    assert ride.vehicle_color_requested == "Obsidian Black"
    assert ride.ev_ride_requested is True
    assert ride.pet_friendly_requested is True
    assert ride.special_assistance_notes == "Passenger requires assistance with wheelchair stowage."
