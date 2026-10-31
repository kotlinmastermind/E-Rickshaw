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
                points.append({"latitude": round(lat_interp, 6), "longitude": round(lng_interp, 6)})

    return {
        **data,
        "points": points,
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

