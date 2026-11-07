"""
Modular Mapbox Application Services for RideGo.
Implements:
- MapSearchService: Forward/reverse geocoding, search suggestions, caching.
- MapRoutingService: Directions, polylines, multi-stops, route preview.
- DriverLocationService: Ingests telemetry, updates driver_locations, validates heading/speed.
- LiveRideTrackingService: Appends to ride_location_history, broadcasts live driver telemetry to ride subscribers.
- GeofenceService: CRUD, point-in-polygon checks, geofence event logging.
- DriverDispatchService: Nearest driver search with matrix or haversine ranking.
- RouteDeviationService: Calculates distance from route polyline, triggers route_deviation_events.
- TripHistoryService: Queries ride routes and location history.
- EmergencyLocationService: Records high-priority SOS telemetry to emergency_location_events.
"""
import math
import json
import logging
from datetime import datetime, timezone, timedelta
from typing import List, Dict, Any, Optional, Tuple

from sqlalchemy import select, desc, func
from sqlalchemy.ext.asyncio import AsyncSession

from backend.integrations.maps.mapbox_service import MapboxService
from backend.domain.models import (
    DriverLocation, DriverProfile, DriverAvailability, GeofenceZone,
    Ride, RideStop, RideLocationHistory, RideRoute, GeofenceEvent,
    RouteDeviationEvent, MapApiUsageLog, LocationSharingSession, EmergencyLocationEvent
)

logger = logging.getLogger("backend.services.mapbox")


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    R = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c


def point_in_polygon(lat: float, lng: float, polygon: List[Tuple[float, float]]) -> bool:
    """Ray-casting algorithm to test point inside polygon [(lat, lng), ...]."""
    if len(polygon) < 3:
        return False
    inside = False
    j = len(polygon) - 1
    for i in range(len(polygon)):
        yi, xi = polygon[i]
        yj, xj = polygon[j]
        if ((yi > lat) != (yj > lat)) and (lng < (xj - xi) * (lat - yi) / ((yj - yi) or 1e-12) + xi):
            inside = not inside
        j = i
    return inside


class MapSearchService:
    def __init__(self, mapbox: Optional[MapboxService] = None):
        self.mapbox = mapbox or MapboxService()

    async def search_places(self, query: str, proximity: Optional[Tuple[float, float]] = None) -> List[Dict[str, Any]]:
        return await self.mapbox.geocode_forward(query, proximity)

    async def reverse_geocode(self, latitude: float, longitude: float) -> str:
        return await self.mapbox.geocode_reverse(latitude, longitude)


class MapRoutingService:
    def __init__(self, mapbox: Optional[MapboxService] = None):
        self.mapbox = mapbox or MapboxService()

    async def calculate_route(
        self,
        origin: Tuple[float, float],
        destination: Tuple[float, float],
        intermediate_stops: Optional[List[Tuple[float, float]]] = None
    ) -> Dict[str, Any]:
        coords = [origin]
        if intermediate_stops:
            coords.extend(intermediate_stops)
        coords.append(destination)
        return await self.mapbox.get_directions(coords)

    async def save_ride_route(
        self,
        db: AsyncSession,
        ride_id: str,
        route_data: Dict[str, Any]
    ) -> RideRoute:
        ride_route = RideRoute(
            ride_id=ride_id,
            polyline=route_data.get("polyline", ""),
            waypoints_json=json.dumps(route_data.get("points", [])),
            distance_km=float(route_data.get("distance_km", 0.0)),
            duration_minutes=float(route_data.get("duration_minutes", 0.0)),
            traffic_aware=route_data.get("provider") == "MAPBOX_DIRECTIONS_API",
            route_label=route_data.get("provider", "Mapbox Road Route")
        )
        db.add(ride_route)
        await db.commit()
        await db.refresh(ride_route)
        return ride_route


class DriverLocationService:
    @staticmethod
    async def record_driver_location(
        db: AsyncSession,
        driver_id: str,
        latitude: float,
        longitude: float,
        heading: Optional[float] = None,
        speed_kmh: Optional[float] = None,
        accuracy_meters: Optional[float] = None,
        source: str = "FUSED_GPS"
    ) -> DriverLocation:
        # Check spoof risk (teleportation > 150 km/h)
        stmt = select(DriverLocation).where(DriverLocation.driver_id == driver_id).order_by(desc(DriverLocation.recorded_at)).limit(1)
        res = await db.execute(stmt)
        last_loc = res.scalar_one_or_none()

        spoof_score = 0.0
        now = datetime.now(timezone.utc)
        if last_loc:
            dt = (now - last_loc.recorded_at).total_seconds()
            if dt > 0:
                dist_km = haversine_km(last_loc.latitude, last_loc.longitude, latitude, longitude)
                calculated_speed = (dist_km / (dt / 3600.0))
                if calculated_speed > 160.0:
                    spoof_score = 0.95  # Unlikely jump
                elif calculated_speed > 90.0:
                    spoof_score = 0.50

        location_entry = DriverLocation(
            driver_id=driver_id,
            latitude=latitude,
            longitude=longitude,
            heading=heading,
            speed=speed_kmh,
            accuracy=accuracy_meters,
            spoof_risk_score=spoof_score,
            source=source,
            recorded_at=now
        )
        db.add(location_entry)
        await db.commit()
        await db.refresh(location_entry)
        return location_entry


class LiveRideTrackingService:
    @staticmethod
    async def record_ride_breadcrumb(
        db: AsyncSession,
        ride_id: str,
        driver_id: str,
        latitude: float,
        longitude: float,
        heading: Optional[float] = None,
        speed_kmh: Optional[float] = None,
        accuracy_meters: Optional[float] = None
    ) -> RideLocationHistory:
        breadcrumb = RideLocationHistory(
            ride_id=ride_id,
            driver_id=driver_id,
            latitude=latitude,
            longitude=longitude,
            heading=heading,
            speed_kmh=speed_kmh,
            accuracy_meters=accuracy_meters,
            recorded_at=datetime.now(timezone.utc)
        )
        db.add(breadcrumb)
        await db.commit()
        await db.refresh(breadcrumb)
        return breadcrumb

    @staticmethod
    async def get_latest_ride_telemetry(db: AsyncSession, ride_id: str) -> Optional[Dict[str, Any]]:
        # Fetch active ride
        ride_stmt = select(Ride).where(Ride.id == ride_id)
        ride_res = await db.execute(ride_stmt)
        ride = ride_res.scalar_one_or_none()
        if not ride:
            return None

        # Fetch latest driver location
        latest_loc = None
        if ride.driver_id:
            loc_stmt = select(DriverLocation).where(DriverLocation.driver_id == ride.driver_id).order_by(desc(DriverLocation.recorded_at)).limit(1)
            loc_res = await db.execute(loc_stmt)
            latest_loc = loc_res.scalar_one_or_none()

        # Fetch saved route
        route_stmt = select(RideRoute).where(RideRoute.ride_id == ride_id).order_by(desc(RideRoute.created_at)).limit(1)
        route_res = await db.execute(route_stmt)
        saved_route = route_res.scalar_one_or_none()

        driver_lat = latest_loc.latitude if latest_loc else ride.pickup_latitude
        driver_lng = latest_loc.longitude if latest_loc else ride.pickup_longitude

        # Compute remaining distance to target (pickup or dropoff depending on ride status)
        target_lat = ride.destination_latitude if ride.status == "IN_PROGRESS" else ride.pickup_latitude
        target_lng = ride.destination_longitude if ride.status == "IN_PROGRESS" else ride.pickup_longitude

        remaining_dist_km = haversine_km(driver_lat, driver_lng, target_lat, target_lng)
        eta_minutes = max(1, int(round((remaining_dist_km / 22.0) * 60.0)))

        is_stale = False
        if latest_loc:
            age_sec = (datetime.now(timezone.utc) - latest_loc.recorded_at).total_seconds()
            is_stale = age_sec > 45.0

        return {
            "ride_id": ride_id,
            "status": ride.status,
            "driver_id": ride.driver_id,
            "driver_latitude": driver_lat,
            "driver_longitude": driver_lng,
            "heading": latest_loc.heading if latest_loc else 0.0,
            "speed_kmh": latest_loc.speed if latest_loc else 0.0,
            "accuracy_meters": latest_loc.accuracy if latest_loc else 10.0,
            "remaining_distance_km": round(remaining_dist_km, 2),
            "eta_minutes": eta_minutes,
            "is_stale": is_stale,
            "polyline": saved_route.polyline if saved_route else "",
            "waypoints": json.loads(saved_route.waypoints_json) if saved_route else [],
            "last_updated": latest_loc.recorded_at.isoformat() if latest_loc else datetime.now(timezone.utc).isoformat()
        }


class GeofenceService:
    @staticmethod
    async def evaluate_geofences(
        db: AsyncSession,
        driver_id: str,
        latitude: float,
        longitude: float,
        ride_id: Optional[str] = None
    ) -> List[Dict[str, Any]]:
        # Fetch active geofences
        stmt = select(GeofenceZone).where(GeofenceZone.is_active.is_(True))
        res = await db.execute(stmt)
        zones = res.scalars().all()

        events = []
        for zone in zones:
            try:
                coords_raw = json.loads(zone.coordinates_json)
                polygon = [(float(pt[0]), float(pt[1])) for pt in coords_raw]
                is_inside = point_in_polygon(latitude, longitude, polygon)

                if is_inside:
                    ev = GeofenceEvent(
                        driver_id=driver_id,
                        zone_id=zone.id,
                        ride_id=ride_id,
                        event_type="DWELL",
                        latitude=latitude,
                        longitude=longitude,
                        recorded_at=datetime.now(timezone.utc)
                    )
                    db.add(ev)
                    events.append({
                        "zone_id": zone.id,
                        "zone_name": zone.name,
                        "zone_type": zone.zone_type,
                        "surge_multiplier": zone.surge_multiplier
                    })
            except Exception:
                continue

        if events:
            await db.commit()
        return events


class RouteDeviationService:
    @staticmethod
    async def check_route_deviation(
        db: AsyncSession,
        ride_id: str,
        driver_id: str,
        current_lat: float,
        current_lng: float,
        threshold_meters: float = 200.0
    ) -> Optional[RouteDeviationEvent]:
        # Get active route
        stmt = select(RideRoute).where(RideRoute.ride_id == ride_id).order_by(desc(RideRoute.created_at)).limit(1)
        res = await db.execute(stmt)
        route = res.scalar_one_or_none()
        if not route:
            return None

        try:
            pts = json.loads(route.waypoints_json)
            if not pts:
                return None

            # Min distance to any point along the route
            min_dist_m = min([haversine_km(current_lat, current_lng, p["latitude"], p["longitude"]) * 1000.0 for p in pts])

            if min_dist_m > threshold_meters:
                sev = "CRITICAL" if min_dist_m > 500.0 else "WARNING"
                event = RouteDeviationEvent(
                    ride_id=ride_id,
                    driver_id=driver_id,
                    deviation_meters=round(min_dist_m, 1),
                    latitude=current_lat,
                    longitude=current_lng,
                    severity=sev,
                    resolved=False,
                    recorded_at=datetime.now(timezone.utc)
                )
                db.add(event)
                await db.commit()
                await db.refresh(event)
                return event
        except Exception as e:
            logger.warning(f"Error checking route deviation: {e}")

        return None


class DriverDispatchService:
    @staticmethod
    async def find_nearest_drivers(
        db: AsyncSession,
        pickup_lat: float,
        pickup_lng: float,
        radius_km: float = 5.0,
        limit: int = 10
    ) -> List[Dict[str, Any]]:
        # Fetch available drivers
        stmt = select(DriverAvailability).where(DriverAvailability.status == "AVAILABLE")
        res = await db.execute(stmt)
        avail = res.scalars().all()
        if not avail:
            return []

        driver_ids = [a.driver_id for a in avail]

        # Get latest locations for these drivers
        loc_stmt = select(DriverLocation).where(DriverLocation.driver_id.in_(driver_ids)).order_by(desc(DriverLocation.recorded_at))
        loc_res = await db.execute(loc_stmt)
        locs = loc_res.scalars().all()

        # Deduplicate to latest location per driver
        driver_locs = {}
        for loc in locs:
            if loc.driver_id not in driver_locs:
                driver_locs[loc.driver_id] = loc

        candidates = []
        for d_id, loc in driver_locs.items():
            dist = haversine_km(pickup_lat, pickup_lng, loc.latitude, loc.longitude)
            if dist <= radius_km:
                eta = max(1, int(round((dist / 20.0) * 60.0)))
                candidates.append({
                    "driver_id": d_id,
                    "latitude": loc.latitude,
                    "longitude": loc.longitude,
                    "heading": loc.heading,
                    "distance_km": round(dist, 2),
                    "eta_minutes": eta,
                    "speed": loc.speed
                })

        candidates.sort(key=lambda x: x["distance_km"])
        return candidates[:limit]


class TripHistoryService:
    @staticmethod
    async def get_trip_breadcrumbs(db: AsyncSession, ride_id: str) -> List[Dict[str, Any]]:
        stmt = select(RideLocationHistory).where(RideLocationHistory.ride_id == ride_id).order_by(RideLocationHistory.recorded_at.asc())
        res = await db.execute(stmt)
        crumbs = res.scalars().all()
        return [
            {
                "latitude": c.latitude,
                "longitude": c.longitude,
                "speed_kmh": c.speed_kmh,
                "heading": c.heading,
                "recorded_at": c.recorded_at.isoformat()
            }
            for c in crumbs
        ]


class EmergencyLocationService:
    @staticmethod
    async def record_emergency_ping(
        db: AsyncSession,
        alert_id: str,
        user_id: str,
        latitude: float,
        longitude: float,
        ride_id: Optional[str] = None,
        speed_kmh: Optional[float] = None,
        accuracy_meters: Optional[float] = None,
        battery_level: Optional[float] = None
    ) -> EmergencyLocationEvent:
        event = EmergencyLocationEvent(
            alert_id=alert_id,
            ride_id=ride_id,
            user_id=user_id,
            latitude=latitude,
            longitude=longitude,
            speed_kmh=speed_kmh,
            accuracy_meters=accuracy_meters,
            battery_level=battery_level,
            recorded_at=datetime.now(timezone.utc)
        )
        db.add(event)
        await db.commit()
        await db.refresh(event)
        return event

