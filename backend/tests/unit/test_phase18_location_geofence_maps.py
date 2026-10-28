"""
Unit Tests for Part 18: Location Operations, Geofence Intelligence, Route Diagnostics & GPS Telemetry Governance (Features 426 - 450).
"""
import pytest
import json
from datetime import datetime, timezone, timedelta
from fastapi import HTTPException

from backend.domain.models import (
    User, CustomerProfile, DriverProfile, GeofenceZone, SavedLocationPin,
    AddressQualityFeedback, RoadRestriction, LocationRetentionSetting,
    DriverBatteryOptimizationSetting, DriverLocation, DriverAvailability
)
from backend.application.services.location_ops_service import LocationOpsService


import uuid

@pytest.mark.asyncio
async def test_part18_map_provider_and_pin_snapping(db_session):
    """Features 426, 427, 428, 432, 433: Health, Feedback, Verification, Road Snapping, Disclosures."""
    service = LocationOpsService(db_session)

    # 426. Live map provider health check
    health = await service.get_live_map_provider_health()
    assert "status" in health
    assert "probe_latency_ms" in health
    assert health["probe_latency_ms"] >= 0
    assert "attribution" in health

    # 427. Address autocomplete quality feedback
    customer = User(
        phone_number=f"+9198{uuid.uuid4().hex[:8]}",
        full_name="Maharaja Quality Tester",
        role="customer"
    )
    db_session.add(customer)
    await db_session.commit()
    await db_session.refresh(customer)

    feedback = await service.record_quality_feedback(
        user_id=customer.id,
        values={"rating": 5, "issue_code": "ACCURATE", "provider_mode": "GOOGLE_PLACES", "coordinates_verified": True}
    )
    assert feedback["recorded"] is True
    assert feedback["rating"] == 5

    # 428. Place selection coordinate verification
    # Within India territory
    ver_ok = await service.verify_place_coordinates("poi_taj_palace", 18.9217, 72.8332)
    assert ver_ok["in_national_boundary"] is True
    assert ver_ok["is_verified"] is True

    # Outside territory
    ver_out = await service.verify_place_coordinates("poi_london", 51.5074, -0.1278)
    assert ver_out["in_national_boundary"] is False
    assert ver_out["is_verified"] is False

    # 432. Pickup pin snapping to roads
    snapped = await service.snap_pickup_to_road(28.6139, 77.2090)
    assert snapped["is_snapped"] is True
    assert "snapped_latitude" in snapped
    assert "offset_distance_meters" in snapped

    # 433. Map attribution and provider disclosure
    attrib = service.get_map_attribution_disclosure()
    assert "tile_attribution" in attrib
    assert "privacy_notice" in attrib


@pytest.mark.asyncio
async def test_part18_saved_pins_and_entrances_and_airports(db_session):
    """Features 429, 430, 431: Saved Pins, Building Entrances, Airport Guidance."""
    service = LocationOpsService(db_session)

    user = User(
        phone_number=f"+9199{uuid.uuid4().hex[:8]}",
        full_name="VVIP Pin Collector",
        role="customer"
    )
    db_session.add(user)
    await db_session.commit()
    await db_session.refresh(user)

    # 429. Saved pickup pins
    pin = await service.save_pin(
        user_id=user.id,
        values={
            "label": "Penthouse Residency",
            "address": "42 Malabar Hill Boulevard, Mumbai",
            "latitude": 18.9548,
            "longitude": 72.8025,
            "pin_type": "BUILDING_ENTRANCE",
            "entrance_notes": "Security intercom call 402; step-free ramp",
            "entrance_code": "GATE-4A"
        }
    )
    assert pin["id"] is not None
    assert pin["label"] == "Penthouse Residency"
    assert pin["entrance_notes"] == "Security intercom call 402; step-free ramp"

    pins = await service.list_pins(user.id)
    assert len(pins) >= 1

    updated = await service.update_pin(
        user_id=user.id,
        pin_id=pin["id"],
        values={"label": "Malabar Penthouse Executive"}
    )
    assert updated["label"] == "Malabar Penthouse Executive"

    # 430. Building entrance pins
    entrances = await service.get_building_entrances(18.9217, 72.8332, "The Taj Mahal Palace")
    assert len(entrances) >= 2
    assert any("Porte-Cochère" in e["name"] for e in entrances)

    # 431. Airport pickup zone guidance
    airport_del = service.get_airport_guidance("DEL", "T3")
    assert airport_del["airport_code"] == "DEL"
    assert "Pillars 4, 5, and 6" in airport_del["guidance"]["designated_pillars"]

    airport_bom = service.get_airport_guidance("BOM", "T2")
    assert airport_bom["airport_code"] == "BOM"
    assert "P4" in airport_bom["guidance"]["designated_pillars"]

    # Delete pin cleanup
    del_res = await service.delete_pin(user.id, pin["id"])
    assert del_res["deleted"] is True


@pytest.mark.asyncio
async def test_part18_routing_eta_and_restrictions(db_session):
    """Features 434, 435, 436, 442, 443, 446: Alternatives, ETA, Restrictions, Accuracy, Guide, Caching."""
    service = LocationOpsService(db_session)

    # 434. Route alternative comparison
    routes = await service.get_route_alternatives(28.6139, 77.2090, 28.5355, 77.3910)
    assert routes["routes_count"] >= 3
    tags = [r["tag"] for r in routes["routes"]]
    assert "RECOMMENDED" in tags
    assert "SMOOTHEST" in tags
    assert "LOW_EMISSION" in tags

    # 435. Traffic-aware ETA refresh
    eta = await service.refresh_traffic_aware_eta(28.6139, 77.2090, 28.5355, 77.3910)
    assert eta["estimated_duration_minutes"] > 0
    assert "congestion_level" in eta
    assert "estimated_arrival_iso" in eta

    # 436. Road closure and restriction awareness
    restr = await service.list_road_restrictions(28.6139, 77.2090, radius_km=30.0)
    assert len(restr) >= 1
    assert any("VIP" in r["restriction_type"] or "CLOSURE" in r["restriction_type"] or "ZERO" in r["restriction_type"] for r in restr)

    # 442. Location accuracy indicator
    acc_high = service.evaluate_location_accuracy(8.0)
    assert acc_high["status"] == "HIGH_PRECISION"
    assert acc_high["needs_manual_confirmation"] is False

    acc_deg = service.evaluate_location_accuracy(85.0)
    assert acc_deg["status"] == "DEGRADED_ACCURACY"
    assert acc_deg["needs_manual_confirmation"] is True

    # 443. GPS permission recovery guide
    guide = service.get_gps_permission_recovery_guide()
    assert "android" in guide["platform_guides"]
    assert "ios" in guide["platform_guides"]

    # 446. Offline map area caching
    pkg = service.get_offline_cache_package("DELHI_NCR")
    assert pkg["region_code"] == "DELHI_NCR"
    assert pkg["package_size_mb"] > 0
    assert "bounding_box" in pkg


@pytest.mark.asyncio
async def test_part18_geofence_and_governance(db_session):
    """Features 437-441, 444, 445, 447, 448: Geofences, Out-of-Zone, Heatmap, Density, Spoofing, Battery, Retention, Quality."""
    service = LocationOpsService(db_session)

    # 437 & 438 & 439: Boundary check, Out-of-zone explanation, Vehicle availability
    # Create test service zone
    delhi_poly = [
        {"lat": 28.50, "lng": 77.10},
        {"lat": 28.70, "lng": 77.10},
        {"lat": 28.70, "lng": 77.30},
        {"lat": 28.50, "lng": 77.30}
    ]
    zone = GeofenceZone(
        name="Delhi Executive Metro Core",
        zone_type="SERVICE_AREA",
        coordinates_json=json.dumps(delhi_poly),
        is_active=True
    )
    db_session.add(zone)
    await db_session.commit()

    # Inside zone
    elig_in = await service.check_eligibility(28.60, 77.20, 28.65, 77.22)
    assert elig_in["eligible"] is True
    assert elig_in["status"] == "ELIGIBLE"

    # Far outside zone
    elig_out = await service.check_eligibility(26.9124, 75.7873)  # Jaipur
    assert elig_out["eligible"] is False
    assert elig_out["status"] == "OUT_OF_ZONE"
    assert elig_out["nearest_border_distance_km"] > 50.0

    # 440. Demand heat map time filters
    heat = await service.get_demand_heatmap_filtered("MORNING_RUSH", 24, "demand", 5)
    assert heat["time_filter"] == "MORNING_RUSH"
    assert "clusters" in heat

    # 441. Driver density map with privacy aggregation
    density = await service.get_driver_density_privacy_grid(2.0)
    assert "grid_cells" in density
    assert "privacy_disclosure" in density

    # 444. Driver GPS spoofing risk signals
    spoofs = await service.get_driver_spoofing_signals(limit=10)
    assert isinstance(spoofs, list)

    # 445. Location update battery optimization
    battery = await service.get_battery_optimization_config()
    assert battery["mode"] == "ADAPTIVE"
    assert battery["stationary_interval_sec"] == 30

    up_bat = await service.update_battery_optimization_config(None, {"mode": "BATTERY_SAVER", "stationary_interval_sec": 45})
    assert up_bat["status"] == "SAVED"
    assert up_bat["stationary_interval_sec"] == 45

    # 447. Location data retention controls
    retention = await service.get_location_retention_controls()
    assert len(retention) >= 2
    assert any(r["category"] == "DRIVER_RAW_TELEMETRY" for r in retention)

    up_ret = await service.update_location_retention_controls("DRIVER_RAW_TELEMETRY", 60)
    assert up_ret["status"] == "UPDATED"
    assert up_ret["new_retention_ttl_days"] == 60

    purge = await service.run_location_retention_purge("DRIVER_RAW_TELEMETRY", dry_run=True)
    assert purge["dry_run"] is True
    assert "records_eligible_for_purge" in purge

    # 448. Location event quality dashboard
    quality = await service.admin_location_quality(24)
    assert "events" in quality
    assert "mean_accuracy_m" in quality
    assert "poor_accuracy_rate_pct" in quality
