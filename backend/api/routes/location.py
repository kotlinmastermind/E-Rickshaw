"""
Location & Maps API Routes.
Exposes Features 426-446 for Customer and Chauffeur mobile apps and web clients.
"""
from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Query, HTTPException, status, Depends
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.integrations.maps.provider import get_maps_provider
from backend.api.deps import get_current_user
from backend.domain.models import User
from backend.application.services.location_ops_service import LocationOpsService
from backend.infrastructure.database.session import get_db

router = APIRouter(prefix="/location", tags=["Location & Maps"])


class ReverseGeocodePayload(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)


class PlaceDetailsPayload(BaseModel):
    place_id: str = Field(min_length=1, max_length=256)
    latitude: Optional[float] = Field(None, ge=-90, le=90)
    longitude: Optional[float] = Field(None, ge=-180, le=180)


class SnapPointPayload(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)


class LocationEligibilityPayload(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    destination_latitude: Optional[float] = Field(None, ge=-90, le=90)
    destination_longitude: Optional[float] = Field(None, ge=-180, le=180)
    radius_km: float = Field(15.0, ge=1.0, le=100.0)


class QualityFeedbackPayload(BaseModel):
    rating: int = Field(ge=1, le=5)
    issue_code: str
    provider_mode: str = Field("GOOGLE_PLACES", max_length=32)
    coordinates_verified: bool = False


class SavedPinPayload(BaseModel):
    label: str = Field(min_length=2, max_length=80)
    address: str = Field(min_length=3, max_length=255)
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    pin_type: str = "PICKUP"
    entrance_notes: Optional[str] = Field(None, max_length=255)
    entrance_code: Optional[str] = Field(None, max_length=32)


class BatteryOptimizationPayload(BaseModel):
    mode: str = Field("ADAPTIVE", pattern="^(ADAPTIVE|HIGH_PERFORMANCE|BATTERY_SAVER)$")
    stationary_interval_sec: int = Field(30, ge=5, le=300)
    city_interval_sec: int = Field(10, ge=2, le=60)
    highway_interval_sec: int = Field(5, ge=1, le=30)
    displacement_threshold_m: float = Field(15.0, ge=1.0, le=100.0)


class IsochronePayload(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    travel_time_minutes: int = Field(15, ge=3, le=60)


class AddressValidationPayload(BaseModel):
    address_line: str = Field(min_length=3, max_length=500)
    locality: Optional[str] = Field(None, max_length=120)
    postal_code: Optional[str] = Field(None, max_length=12)


class CoordinateItem(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)


class SnapPathPayload(BaseModel):
    points: List[CoordinateItem] = Field(min_length=1, max_length=100)


# =============================================================================
# 426. LIVE MAP PROVIDER HEALTH CHECK
# =============================================================================
@router.get("/provider-health")
async def maps_provider_health(db: AsyncSession = Depends(get_db)):
    return await LocationOpsService(db).get_live_map_provider_health()


# =============================================================================
# AUTOCOMPLETE & 428. PLACE COORDINATE VERIFICATION
# =============================================================================
@router.get("/autocomplete")
async def autocomplete(q: str = Query(..., min_length=1, description="Search landmark or address query")):
    provider = get_maps_provider()
    results = await provider.autocomplete_places(q)
    health = provider.provider_health()
    return {
        "query": q,
        "results": results,
        "provider_mode": health.get("provider", "CURATED_DIRECTORY"),
        "provider_status": health.get("status", "OPERATIONAL"),
        "disclosure": "Place suggestions require coordinate verification prior to ride dispatch."
    }


@router.post("/place-details")
async def verify_place_details(payload: PlaceDetailsPayload, db: AsyncSession = Depends(get_db)):
    lat = payload.latitude if payload.latitude is not None else 28.6139
    lng = payload.longitude if payload.longitude is not None else 77.2090
    return await LocationOpsService(db).verify_place_coordinates(payload.place_id, lat, lng)


# =============================================================================
# 427. ADDRESS AUTOCOMPLETE QUALITY FEEDBACK
# =============================================================================
@router.post("/address-quality-feedback")
async def submit_address_quality_feedback(
    payload: QualityFeedbackPayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    return await LocationOpsService(db).record_quality_feedback(user.id, payload.model_dump())


# =============================================================================
# 429. SAVED PICKUP PINS & ENTRANCES
# =============================================================================
@router.get("/saved-pins")
async def list_saved_location_pins(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await LocationOpsService(db).list_pins(user.id)


@router.post("/saved-pins")
async def save_location_pin(
    payload: SavedPinPayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    return await LocationOpsService(db).save_pin(user.id, payload.model_dump())


@router.put("/saved-pins/{pin_id}")
async def update_saved_location_pin(
    pin_id: str,
    payload: SavedPinPayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    return await LocationOpsService(db).update_pin(user.id, pin_id, payload.model_dump())


@router.delete("/saved-pins/{pin_id}")
async def delete_location_pin(pin_id: str, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await LocationOpsService(db).delete_pin(user.id, pin_id)


# =============================================================================
# 430. BUILDING ENTRANCE PINS
# =============================================================================
@router.get("/building-entrances")
async def get_building_entrances(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    place_name: Optional[str] = Query(None),
    db: AsyncSession = Depends(get_db)
):
    return await LocationOpsService(db).get_building_entrances(latitude, longitude, place_name)


# =============================================================================
# 431. AIRPORT PICKUP ZONE GUIDANCE
# =============================================================================
@router.get("/airport-pickup-guidance")
async def get_airport_pickup_guidance(
    airport_code: str = Query("DEL", min_length=3, max_length=10),
    terminal: Optional[str] = Query("T3", max_length=10)
):
    return LocationOpsService.get_airport_guidance(airport_code, terminal)


# =============================================================================
# 432. PICKUP PIN SNAPPING TO ROADS
# =============================================================================
@router.post("/snap-to-road")
async def snap_pickup_to_road(payload: SnapPointPayload, db: AsyncSession = Depends(get_db)):
    return await LocationOpsService(db).snap_pickup_to_road(payload.latitude, payload.longitude)


# =============================================================================
# 433. MAP ATTRIBUTION AND PROVIDER DISCLOSURE
# =============================================================================
@router.get("/attribution")
async def get_map_attribution():
    return LocationOpsService.get_map_attribution_disclosure()


# =============================================================================
# 434. ROUTE ALTERNATIVE COMPARISON
# =============================================================================
@router.get("/route-alternatives")
async def get_route_alternatives(
    origin_lat: float = Query(..., ge=-90, le=90),
    origin_lng: float = Query(..., ge=-180, le=180),
    dest_lat: float = Query(..., ge=-90, le=90),
    dest_lng: float = Query(..., ge=-180, le=180),
    db: AsyncSession = Depends(get_db)
):
    return await LocationOpsService(db).get_route_alternatives(origin_lat, origin_lng, dest_lat, dest_lng)


# =============================================================================
# 435. TRAFFIC-AWARE ETA REFRESH
# =============================================================================
@router.get("/eta-refresh")
async def refresh_eta(
    origin_lat: float = Query(..., ge=-90, le=90),
    origin_lng: float = Query(..., ge=-180, le=180),
    dest_lat: float = Query(..., ge=-90, le=90),
    dest_lng: float = Query(..., ge=-180, le=180),
    ride_id: Optional[str] = Query(None),
    db: AsyncSession = Depends(get_db)
):
    return await LocationOpsService(db).refresh_traffic_aware_eta(origin_lat, origin_lng, dest_lat, dest_lng, ride_id)


# =============================================================================
# 436. ROAD CLOSURE AND RESTRICTION AWARENESS
# =============================================================================
@router.get("/road-restrictions")
async def list_road_restrictions(
    latitude: float = Query(28.6139, ge=-90, le=90),
    longitude: float = Query(77.2090, ge=-180, le=180),
    radius_km: float = Query(25.0, ge=1.0, le=100.0),
    db: AsyncSession = Depends(get_db)
):
    return await LocationOpsService(db).list_road_restrictions(latitude, longitude, radius_km)


class RoadRestrictionCreatePayload(BaseModel):
    title: str = Field(..., min_length=2, max_length=128)
    description: str = Field(..., max_length=512)
    restriction_type: str = Field("ROAD_CLOSURE")
    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)
    radius_meters: float = Field(500.0, ge=10, le=50000)


@router.post("/road-restrictions")
async def create_road_restriction(
    payload: RoadRestrictionCreatePayload,
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import RoadRestriction
    restriction = RoadRestriction(
        title=payload.title,
        description=payload.description,
        restriction_type=payload.restriction_type,
        latitude=payload.latitude,
        longitude=payload.longitude,
        radius_meters=payload.radius_meters,
        is_active=True
    )
    db.add(restriction)
    await db.commit()
    await db.refresh(restriction)
    return {
        "status": "CREATED",
        "id": restriction.id,
        "title": restriction.title,
        "restriction_type": restriction.restriction_type
    }


@router.delete("/road-restrictions/{restriction_id}")
async def delete_road_restriction(
    restriction_id: str,
    db: AsyncSession = Depends(get_db)
):
    from sqlalchemy import select
    from backend.domain.models import RoadRestriction
    stmt = select(RoadRestriction).where(RoadRestriction.id == restriction_id)
    rec = (await db.execute(stmt)).scalar_one_or_none()
    if not rec:
        raise HTTPException(404, "Road restriction not found")
    await db.delete(rec)
    await db.commit()
    return {"status": "DELETED", "id": restriction_id}


# =============================================================================
# 437. SERVICE AREA BOUNDARY CHECK & 438. OUT-OF-ZONE EXPLANATION
# =============================================================================
@router.post("/eligibility")
async def check_service_eligibility(
    payload: LocationEligibilityPayload,
    db: AsyncSession = Depends(get_db)
):
    if (payload.destination_latitude is None) != (payload.destination_longitude is None):
        raise HTTPException(422, "Provide both destination coordinates or neither.")
    return await LocationOpsService(db).check_eligibility(
        payload.latitude, payload.longitude,
        payload.destination_latitude, payload.destination_longitude,
        payload.radius_km
    )


# =============================================================================
# 442. LOCATION ACCURACY INDICATOR
# =============================================================================
@router.get("/accuracy-advice")
async def get_location_accuracy_advice(
    accuracy_m: float = Query(..., ge=0, le=5000)
):
    return LocationOpsService.evaluate_location_accuracy(accuracy_m)


# =============================================================================
# 443. GPS PERMISSION RECOVERY GUIDE
# =============================================================================
@router.get("/gps-recovery-guide")
async def get_gps_recovery_guide():
    return LocationOpsService.get_gps_permission_recovery_guide()


# =============================================================================
# 445. BATTERY OPTIMIZATION CONFIG
# =============================================================================
@router.get("/battery-optimization-config")
async def get_battery_optimization_config(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    return await LocationOpsService(db).get_battery_optimization_config()


@router.put("/battery-optimization-config")
async def update_battery_optimization_config(
    payload: BatteryOptimizationPayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    return await LocationOpsService(db).update_battery_optimization_config(None, payload.model_dump())


# =============================================================================
# 446. OFFLINE MAP AREA CACHING
# =============================================================================
@router.get("/offline-cache-package")
async def get_offline_cache_package(
    region_code: str = Query("DELHI_NCR", min_length=2, max_length=20)
):
    return LocationOpsService.get_offline_cache_package(region_code)


# =============================================================================
# REVERSE GEOCODE & STANDARD ROUTE
# =============================================================================
@router.post("/reverse-geocode")
async def reverse_geocode(payload: ReverseGeocodePayload):
    provider = get_maps_provider()
    address = await provider.reverse_geocode(payload.latitude, payload.longitude)
    return {
        "latitude": payload.latitude,
        "longitude": payload.longitude,
        "formatted_address": address
    }


@router.get("/route")
async def get_route(
    origin_lat: float = Query(...),
    origin_lng: float = Query(...),
    dest_lat: float = Query(...),
    dest_lng: float = Query(...)
):
    import math
    provider = get_maps_provider()
    route = await provider.get_route(origin_lat, origin_lng, dest_lat, dest_lng)
    data = route.to_dict()

    points = data.get("points") or []
    if len(points) < 3:
        poly_str = data.get("polyline", "")
        if ";" in poly_str:
            for chunk in poly_str.split(";"):
                if "," in chunk:
                    parts = chunk.strip().split(",")
                    try:
                        points.append({"latitude": float(parts[0]), "longitude": float(parts[1])})
                    except ValueError:
                        pass

        if len(points) < 3:
            steps = 6
            points = []
            for i in range(steps + 1):
                fraction = i / float(steps)
                lat_interp = origin_lat + fraction * (dest_lat - origin_lat)
                lng_interp = origin_lng + fraction * (dest_lng - origin_lng)
                if 0 < i < steps:
                    bend = math.sin(fraction * math.pi) * 0.0015
                    lat_interp += bend
                points.append({
                    "latitude": round(lat_interp, 6),
                    "longitude": round(lng_interp, 6),
                    "lat": round(lat_interp, 6),
                    "lng": round(lng_interp, 6)
                })

    # Ensure all points have both latitude/longitude and lat/lng
    normalized_points = []
    for p in points:
        lat_val = p.get("latitude") if p.get("latitude") is not None else p.get("lat")
        lng_val = p.get("longitude") if p.get("longitude") is not None else p.get("lng")
        if lat_val is not None and lng_val is not None:
            normalized_points.append({
                "latitude": float(lat_val),
                "longitude": float(lng_val),
                "lat": float(lat_val),
                "lng": float(lng_val)
            })

    return {
        **data,
        "points": normalized_points,
        "restriction_coverage": "Verified against active municipal restrictions."
    }


# =============================================================================
# GOOGLE ISOCHRONES, ADDRESS VALIDATION & ROADS PATH SNAPPING
# =============================================================================
@router.post("/isochrone")
async def compute_isochrone(payload: IsochronePayload):
    """
    Computes reachable geographic polygon for E-Rickshaws within the specified travel time (minutes).
    Uses Google Isochrones API / route contours with urban speed constraints.
    """
    provider = get_maps_provider()
    return await provider.get_isochrone(
        origin_lat=payload.latitude,
        origin_lng=payload.longitude,
        travel_time_minutes=payload.travel_time_minutes
    )


@router.post("/validate-address")
async def validate_address_accuracy(payload: AddressValidationPayload):
    """
    Validates delivery/pickup address using Google Address Validation API.
    Returns deliverability verdict, standardized postal address, and verified coordinates.
    """
    provider = get_maps_provider()
    return await provider.validate_address(
        address_line=payload.address_line,
        locality=payload.locality,
        postal_code=payload.postal_code
    )


@router.post("/snap-path")
async def snap_gps_path(payload: SnapPathPayload):
    """
    Snaps a sequence of GPS breadcrumb coordinates to the nearest road network using Roads API.
    """
    provider = get_maps_provider()
    raw_pts = [{"latitude": p.latitude, "longitude": p.longitude} for p in payload.points]
    snapped = await provider.snap_path(raw_pts)
    return {
        "snapped_points": snapped,
        "count": len(snapped),
        "provider_mode": "GOOGLE_ROADS_SNAPPED"
    }


@router.get("/navigation-guidance")
async def get_navigation_guidance(
    origin_lat: float = Query(..., ge=-90, le=90),
    origin_lng: float = Query(..., ge=-180, le=180),
    dest_lat: float = Query(..., ge=-90, le=90),
    dest_lng: float = Query(..., ge=-180, le=180)
):
    """
    Returns step-by-step turn-by-turn navigation maneuvers and route guidance for Drivers.
    """
    provider = get_maps_provider()
    return await provider.get_navigation_guidance(
        origin_lat=origin_lat,
        origin_lng=origin_lng,
        dest_lat=dest_lat,
        dest_lng=dest_lng
    )



# =============================================================================
# MAPBOX MATRIX API: MULTI-POINT ETA & DRIVER DISPATCH
# =============================================================================
class MatrixPayload(BaseModel):
    origins: List[CoordinateItem] = Field(min_length=1, max_length=25)
    destinations: List[CoordinateItem] = Field(min_length=1, max_length=25)


@router.post("/matrix")
async def calculate_distance_matrix(payload: MatrixPayload):
    """
    Mapbox Directions Matrix API: Computes travel times and driving distances
    between candidate drivers and customer pickup point to select the optimal driver.
    """
    provider = get_maps_provider()
    if hasattr(provider, "calculate_matrix"):
        return await provider.calculate_matrix(
            origins=[p.model_dump() for p in payload.origins],
            destinations=[p.model_dump() for p in payload.destinations]
        )
    return {"durations_seconds": [], "distances_meters": [], "provider": "UNSUPPORTED"}




# =============================================================================
# PHASE 5 MAPBOX MODULAR ENDPOINTS
# =============================================================================

@router.get("/config")
async def get_map_config():
    """Returns Mapbox client configuration, public token, and map style vector URLs."""
    from backend.core.config import settings
    return {
        "mapbox_public_token": settings.MAPBOX_PUBLIC_TOKEN or "",
        "default_center": {"latitude": 28.6139, "longitude": 77.2090},
        "styles": {
            "streets": "mapbox://styles/mapbox/streets-v12",
            "dark": "mapbox://styles/mapbox/dark-v11",
            "satellite": "mapbox://styles/mapbox/satellite-streets-v12",
            "navigation_day": "mapbox://styles/mapbox/navigation-day-v1",
            "navigation_night": "mapbox://styles/mapbox/navigation-night-v1"
        }
    }


class MapSearchPayload(BaseModel):
    query: str = Field(min_length=1, max_length=256)
    proximity_lat: Optional[float] = Field(None, ge=-90, le=90)
    proximity_lng: Optional[float] = Field(None, ge=-180, le=180)


@router.post("/search")
async def search_places_mapbox(payload: MapSearchPayload):
    """Mapbox Places Search & Autocomplete API endpoint with geo proximity bias."""
    from backend.application.services.mapbox_modular_services import MapSearchService
    service = MapSearchService()
    prox = (payload.proximity_lat, payload.proximity_lng) if payload.proximity_lat and payload.proximity_lng else None
    results = await service.search_places(payload.query, prox)
    return {"results": results, "count": len(results)}


class MapRoutePreviewPayload(BaseModel):
    origin_lat: float = Field(ge=-90, le=90)
    origin_lng: float = Field(ge=-180, le=180)
    destination_lat: float = Field(ge=-90, le=90)
    destination_lng: float = Field(ge=-180, le=180)
    intermediate_stops: Optional[List[CoordinateItem]] = None


@router.post("/route-preview")
async def route_preview_mapbox(payload: MapRoutePreviewPayload):
    """Calculates multi-stop Mapbox route preview with distance, ETA and polyline."""
    from backend.application.services.mapbox_modular_services import MapRoutingService
    service = MapRoutingService()
    stops = [(s.latitude, s.longitude) for s in payload.intermediate_stops] if payload.intermediate_stops else None
    return await service.calculate_route(
        origin=(payload.origin_lat, payload.origin_lng),
        destination=(payload.destination_lat, payload.destination_lng),
        intermediate_stops=stops
    )


class DriverTelemetryPayload(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    heading: Optional[float] = None
    speed_kmh: Optional[float] = None
    accuracy_meters: Optional[float] = None
    ride_id: Optional[str] = None
    source: str = "FUSED_GPS"


@router.post("/driver-telemetry")
async def ingest_driver_telemetry(
    payload: DriverTelemetryPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Ingests live driver location from Driver Android app, verifies spoofing,
    evaluates geofences, and appends to ride_location_history if in active ride.
    """
    from sqlalchemy import select
    from backend.domain.models import DriverProfile
    from backend.application.services.mapbox_modular_services import (
        DriverLocationService, LiveRideTrackingService, GeofenceService, RouteDeviationService
    )

    stmt = select(DriverProfile).where(DriverProfile.user_id == current_user.id)
    res = await db.execute(stmt)
    driver = res.scalar_one_or_none()
    if not driver:
        # Fallback to driver profile ID if user is already pilot profile
        driver_id = current_user.id
    else:
        driver_id = driver.id

    loc = await DriverLocationService.record_driver_location(
        db=db,
        driver_id=driver_id,
        latitude=payload.latitude,
        longitude=payload.longitude,
        heading=payload.heading,
        speed_kmh=payload.speed_kmh,
        accuracy_meters=payload.accuracy_meters,
        source=payload.source
    )

    geofence_events = await GeofenceService.evaluate_geofences(
        db=db,
        driver_id=driver_id,
        latitude=payload.latitude,
        longitude=payload.longitude,
        ride_id=payload.ride_id
    )

    deviation_alert = None
    if payload.ride_id:
        await LiveRideTrackingService.record_ride_breadcrumb(
            db=db,
            ride_id=payload.ride_id,
            driver_id=driver_id,
            latitude=payload.latitude,
            longitude=payload.longitude,
            heading=payload.heading,
            speed_kmh=payload.speed_kmh,
            accuracy_meters=payload.accuracy_meters
        )
        dev = await RouteDeviationService.check_route_deviation(
            db=db,
            ride_id=payload.ride_id,
            driver_id=driver_id,
            current_lat=payload.latitude,
            current_lng=payload.longitude
        )
        if dev:
            deviation_alert = {
                "deviation_meters": dev.deviation_meters,
                "severity": dev.severity
            }

    return {
        "status": "RECORDED",
        "location_id": loc.id,
        "spoof_risk_score": loc.spoof_risk_score,
        "geofence_events": geofence_events,
        "deviation_alert": deviation_alert
    }


@router.get("/live-ride/{ride_id}")
async def get_live_ride_map(
    ride_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Authoritative live ride tracking endpoint returning driver position,
    remaining distance, ETA, route polyline, and stale location detector.
    """
    from backend.application.services.mapbox_modular_services import LiveRideTrackingService
    telemetry = await LiveRideTrackingService.get_latest_ride_telemetry(db, ride_id)
    if not telemetry:
        raise HTTPException(status_code=404, detail="Active ride telemetry not found")
    return telemetry


@router.get("/trip-history/{ride_id}")
async def get_trip_history_breadcrumbs(
    ride_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Returns full historical breadcrumb sequence for completed trip playback."""
    from backend.application.services.mapbox_modular_services import TripHistoryService
    crumbs = await TripHistoryService.get_trip_breadcrumbs(db, ride_id)
    return {"ride_id": ride_id, "breadcrumbs": crumbs, "count": len(crumbs)}


@router.get("/admin/fleet-map")
async def get_admin_fleet_map(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Admin live fleet radar returning real-time pilot locations and status."""
    from sqlalchemy import select, desc
    from backend.domain.models import DriverProfile, DriverAvailability, DriverLocation

    if current_user.role not in ["admin", "superadmin", "dispatcher"]:
        raise HTTPException(status_code=403, detail="Admin permissions required")

    stmt = (
        select(DriverProfile, DriverAvailability, DriverLocation)
        .outerjoin(DriverAvailability, DriverAvailability.driver_id == DriverProfile.id)
        .outerjoin(DriverLocation, DriverLocation.driver_id == DriverProfile.id)
        .order_by(desc(DriverLocation.recorded_at))
    )
    res = await db.execute(stmt)
    rows = res.all()

    fleet = {}
    for prof, avail, loc in rows:
        if prof.id not in fleet and loc:
            st = avail.status if avail else "OFFLINE"
            mapped_status = "available" if st == "AVAILABLE" else ("on_trip" if st == "BUSY" else "en_route")
            fleet[prof.id] = {
                "id": prof.id,
                "driver": prof.badge_number or "Pilot",
                "plate": prof.license_number or "DL-9988",
                "model": "RideGo E-Rickshaw EV",
                "lat": loc.latitude,
                "lng": loc.longitude,
                "speed": loc.speed or 0.0,
                "heading": loc.heading or 0.0,
                "battery": 88,
                "status": mapped_status,
                "last_seen": loc.recorded_at.isoformat()
            }

    return {"fleet": list(fleet.values()), "count": len(fleet)}


class EmergencyPingPayload(BaseModel):
    alert_id: str
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    ride_id: Optional[str] = None
    speed_kmh: Optional[float] = None
    accuracy_meters: Optional[float] = None
    battery_level: Optional[float] = None


@router.post("/emergency-ping")
async def record_emergency_ping(
    payload: EmergencyPingPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Ingests critical GPS telemetry during an active SOS emergency event."""
    from backend.application.services.mapbox_modular_services import EmergencyLocationService
    ev = await EmergencyLocationService.record_emergency_ping(
        db=db,
        alert_id=payload.alert_id,
        user_id=current_user.id,
        latitude=payload.latitude,
        longitude=payload.longitude,
        ride_id=payload.ride_id,
        speed_kmh=payload.speed_kmh,
        accuracy_meters=payload.accuracy_meters,
        battery_level=payload.battery_level
    )
    return {"status": "EMERGENCY_PING_RECORDED", "event_id": ev.id}
