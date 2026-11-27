"""Database-backed service-area and restricted-geofence enforcement."""

import json
import math
from typing import Any, Dict, List, Optional, Tuple

from fastapi import HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from backend.domain.models import GeofenceZone, MultiCityOperatingConfig


class ServiceAreaService:
    """Resolve operational coverage only from active Admin-managed geofence records."""

    def __init__(self, db: AsyncSession):
        self.db = db

    @staticmethod
    def _validate_point(latitude: float, longitude: float) -> Tuple[float, float]:
        try:
            lat, lng = float(latitude), float(longitude)
        except (TypeError, ValueError):
            raise HTTPException(status_code=400, detail="Valid pickup and destination GPS coordinates are required.")
        if not math.isfinite(lat) or not math.isfinite(lng) or not (-90 <= lat <= 90) or not (-180 <= lng <= 180):
            raise HTTPException(status_code=400, detail="GPS coordinates are outside the valid latitude/longitude range.")
        return lat, lng

    @staticmethod
    def parse_polygon(coordinates_json: str) -> List[Dict[str, float]]:
        try:
            raw = json.loads(coordinates_json)
            points: List[Dict[str, float]] = []
            for item in raw:
                if isinstance(item, dict):
                    lat, lng = float(item["lat"]), float(item["lng"])
                elif isinstance(item, (list, tuple)) and len(item) >= 2:
                    lat, lng = float(item[0]), float(item[1])
                else:
                    raise ValueError("Invalid polygon coordinate")
                if not math.isfinite(lat) or not math.isfinite(lng):
                    raise ValueError("Non-finite polygon coordinate")
                if not (-90 <= lat <= 90 and -180 <= lng <= 180):
                    raise ValueError("Polygon coordinate outside valid range")
                points.append({"lat": lat, "lng": lng})
            if len(points) < 3:
                raise ValueError("A polygon needs at least three points")
            return points
        except (ValueError, TypeError, KeyError, json.JSONDecodeError) as exc:
            raise ValueError("Invalid service-area polygon") from exc

    @staticmethod
    def contains_point(latitude: float, longitude: float, polygon: List[Dict[str, float]]) -> bool:
        inside = False
        j = len(polygon) - 1
        for i, point in enumerate(polygon):
            a, b = polygon[i], polygon[j]
            yi, yj = a["lat"], b["lat"]
            xi, xj = a["lng"], b["lng"]
            if ((yi > latitude) != (yj > latitude)) and longitude < (
                (xj - xi) * (latitude - yi) / ((yj - yi) or 1e-12) + xi
            ):
                inside = not inside
            j = i
        return inside

    async def _load_active_zones(self):
        zones = (await self.db.execute(
            select(GeofenceZone).where(GeofenceZone.is_active.is_(True))
        )).scalars().all()
        service_areas = []
        restricted_areas = []
        for zone in zones:
            zone_type = str(zone.zone_type or "").upper()
            if zone_type not in ("SERVICE_AREA", "RESTRICTED", "RESTRICTED_ZONE"):
                continue
            try:
                polygon = self.parse_polygon(zone.coordinates_json)
            except ValueError as exc:
                raise HTTPException(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail=f"Active geofence '{zone.name}' has invalid polygon data. Serviceability checks are blocked until Admin repairs it.",
                ) from exc
            if zone_type == "SERVICE_AREA":
                if not zone.city_code or not zone.city_code.strip():
                    raise HTTPException(
                        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                        detail=f"Active service area '{zone.name}' has no city code. Booking and dispatch are blocked until Admin configures it.",
                    )
                service_areas.append((zone, polygon))
            else:
                restricted_areas.append((zone, polygon))
        if not service_areas:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Service-area coverage is not configured. Booking and dispatch are temporarily unavailable.",
            )
        return service_areas, restricted_areas

    async def _ensure_city_active(self, city_code: str) -> None:
        config = (await self.db.execute(
            select(MultiCityOperatingConfig).where(MultiCityOperatingConfig.city_code == city_code)
        )).scalars().first()
        if config is not None and config.is_active is False:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Service area '{city_code}' is currently inactive.",
            )

    async def resolve_point(self, latitude: float, longitude: float, expected_city_code: Optional[str] = None) -> GeofenceZone:
        lat, lng = self._validate_point(latitude, longitude)
        service_areas, restricted_areas = await self._load_active_zones()
        matches = [(zone, polygon) for zone, polygon in service_areas if self.contains_point(lat, lng, polygon)]
        if not matches:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Location is outside all active service areas. Select a pickup within an enabled coverage zone.",
            )
        if len(matches) != 1:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Location intersects overlapping active service areas. Admin must resolve the boundary overlap before booking.",
            )
        zone, _ = matches[0]
        city_code = zone.city_code.strip().upper()
        if expected_city_code and expected_city_code.strip().upper() != city_code:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Specified city does not match the database-resolved service area. Cross-area fare spoofing is prohibited.",
            )
        if any(self.contains_point(lat, lng, polygon) for _, polygon in restricted_areas):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Location is inside an active restricted geofence and cannot be used for booking or dispatch.",
            )
        await self._ensure_city_active(city_code)
        return zone

    async def resolve_trip(
        self,
        pickup_latitude: float,
        pickup_longitude: float,
        destination_latitude: float,
        destination_longitude: float,
        expected_city_code: Optional[str] = None,
    ) -> GeofenceZone:
        pickup = await self.resolve_point(pickup_latitude, pickup_longitude, expected_city_code)
        destination = await self.resolve_point(destination_latitude, destination_longitude)
        if destination.id != pickup.id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Pickup and destination must belong to the same active service area. Cross-area rides are not enabled.",
            )
        return pickup

    async def load_area_boundary(self, service_area_id: str):
        zone = (await self.db.execute(
            select(GeofenceZone).where(
                GeofenceZone.id == service_area_id,
                GeofenceZone.zone_type == "SERVICE_AREA",
                GeofenceZone.is_active.is_(True),
            )
        )).scalars().first()
        if zone is None or not zone.city_code:
            raise HTTPException(status_code=400, detail="Ride service area is missing or inactive; dispatch is blocked.")
        try:
            polygon = self.parse_polygon(zone.coordinates_json)
        except ValueError as exc:
            raise HTTPException(status_code=503, detail="Ride service-area boundary is invalid; dispatch is blocked.") from exc
        await self._ensure_city_active(zone.city_code.strip().upper())
        _, restricted_areas = await self._load_active_zones()
        return zone, polygon, [polygon for _, polygon in restricted_areas]

    @classmethod
    def location_is_inside_boundary(
        cls,
        latitude: float,
        longitude: float,
        service_polygon: List[Dict[str, float]],
        restricted_polygons: List[List[Dict[str, float]]],
    ) -> bool:
        try:
            lat, lng = cls._validate_point(latitude, longitude)
        except HTTPException:
            return False
        return (
            cls.contains_point(lat, lng, service_polygon)
            and not any(cls.contains_point(lat, lng, polygon) for polygon in restricted_polygons)
        )

    async def is_driver_inside(self, service_area_id: str, latitude: float, longitude: float) -> bool:
        _, polygon, restricted = await self.load_area_boundary(service_area_id)
        return self.location_is_inside_boundary(latitude, longitude, polygon, restricted)
