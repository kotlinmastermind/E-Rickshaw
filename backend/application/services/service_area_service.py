"""Database-backed operational service-area resolution and authorization.

GeofenceZone records with zone_type=SERVICE_AREA define actual operating polygons.
Geographic master-data coverage is deliberately independent from operational coverage.
"""
import json
from typing import Optional

from fastapi import HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from backend.domain.models import DriverServiceAreaAssignment, GeofenceZone
from backend.application.services.admin_advanced_service import AdminAdvancedService


class ServiceAreaService:
    def __init__(self, db: AsyncSession):
        self.db = db

    @staticmethod
    def _polygon(zone: GeofenceZone):
        try:
            points = json.loads(zone.coordinates_json)
            polygon = [(float(point["lat"]), float(point["lng"])) for point in points]
        except (TypeError, ValueError, KeyError, json.JSONDecodeError):
            return None
        return polygon if len(polygon) >= 3 else None

    async def lock_active_service_area_for_booking(self, service_area_id: str) -> GeofenceZone:
        """Acquire a transaction-scoped FOR SHARE lock on an operational SERVICE_AREA so
        concurrent admin deactivation (FOR UPDATE) must serialize with active booking creation."""
        zone = (
            await self.db.execute(
                select(GeofenceZone)
                .where(GeofenceZone.id == service_area_id)
                .with_for_update(read=True)
                .execution_options(populate_existing=True)
            )
        ).scalar_one_or_none()
        if not zone or not zone.is_active or str(zone.zone_type or "").strip().upper() != "SERVICE_AREA":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Selected service area is no longer active for booking.",
            )
        return zone

    async def resolve_route(
        self,
        pickup_lat: float,
        pickup_lng: float,
        destination_lat: float,
        destination_lng: float,
        requested_city_code: Optional[str] = None,
        lock_for_booking: bool = False,
    ) -> GeofenceZone:
        zones = (await self.db.execute(
            select(GeofenceZone).where(
                GeofenceZone.is_active.is_(True),
                GeofenceZone.zone_type == "SERVICE_AREA",
            ).order_by(GeofenceZone.id)
        )).scalars().all()
        matches = []
        for zone in zones:
            polygon = self._polygon(zone)
            if polygon is None:
                raise HTTPException(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail=f"Active service area '{zone.name}' has invalid polygon data; booking and dispatch are blocked.",
                )
            if not zone.city_code or not zone.city_code.strip():
                raise HTTPException(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail=f"Active service area '{zone.name}' has no city code; booking and dispatch are blocked.",
                )
            if (
                AdminAdvancedService.is_point_in_polygon(pickup_lat, pickup_lng, polygon)
                and AdminAdvancedService.is_point_in_polygon(destination_lat, destination_lng, polygon)
            ):
                matches.append(zone)

        if not matches:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Pickup and destination must both be inside the same active operational service area. This location is not currently serviceable.",
            )
        if len(matches) > 1:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Route falls inside overlapping active service areas. Resolve the overlapping service-area configuration before booking.",
            )
        zone = matches[0]
        if lock_for_booking:
            zone = await self.lock_active_service_area_for_booking(zone.id)
        _, service_polygon, restricted_polygons = await self.load_area_boundary(zone.id)
        if not self.location_is_inside_boundary(pickup_lat, pickup_lng, service_polygon, restricted_polygons):
            raise HTTPException(status_code=400, detail="Pickup is inside a restricted zone or outside the active service boundary.")
        if not self.location_is_inside_boundary(destination_lat, destination_lng, service_polygon, restricted_polygons):
            raise HTTPException(status_code=400, detail="Destination is inside a restricted zone or outside the active service boundary.")
        if requested_city_code and requested_city_code.strip().upper() != (zone.city_code or "").strip().upper():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Provided city code does not match the service area resolved from GPS coordinates.",
            )
        return zone

    async def resolve_point(self, latitude: float, longitude: float) -> GeofenceZone:
        zones = (await self.db.execute(
            select(GeofenceZone).where(
                GeofenceZone.is_active.is_(True),
                GeofenceZone.zone_type == "SERVICE_AREA",
            ).order_by(GeofenceZone.id)
        )).scalars().all()
        matches = []
        for zone in zones:
            polygon = self._polygon(zone)
            if polygon is None:
                raise HTTPException(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail=f"Active service area '{zone.name}' has invalid polygon data; location checks are blocked.",
                )
            if not zone.city_code or not zone.city_code.strip():
                raise HTTPException(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail=f"Active service area '{zone.name}' has no city code; location checks are blocked.",
                )
            if AdminAdvancedService.is_point_in_polygon(latitude, longitude, polygon):
                matches.append(zone)
        if not matches:
            raise HTTPException(status_code=400, detail="Location is outside every active operational service area.")
        if len(matches) > 1:
            raise HTTPException(status_code=409, detail="Location falls inside overlapping active service areas.")
        zone = matches[0]
        if not await self.is_driver_inside(zone.id, latitude, longitude):
            raise HTTPException(status_code=400, detail="Location is inside a restricted zone or outside the active service boundary.")
        return zone

    async def require_point_in_area(self, service_area_id: str, latitude: float, longitude: float) -> None:
        if not await self.is_driver_inside(service_area_id, latitude, longitude):
            raise HTTPException(status_code=400, detail="Every pickup, destination, and intermediate stop must remain inside the ride service area and outside restricted zones.")

    async def require_driver_assignment(self, driver_id: str, service_area_id: str) -> None:
        assignment = (await self.db.execute(
            select(DriverServiceAreaAssignment.id).where(
                DriverServiceAreaAssignment.driver_id == driver_id,
                DriverServiceAreaAssignment.service_area_id == service_area_id,
                DriverServiceAreaAssignment.is_active.is_(True),
            ).limit(1)
        )).scalar_one_or_none()
        if assignment is None:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Driver is not authorized for this ride's operational service area.",
            )

    async def load_area_boundary(self, service_area_id: str):
        """Load one active service polygon and all active restricted polygons.

        Invalid active geometry fails closed: silently skipping a malformed
        restriction could make an otherwise blocked location dispatchable.
        """
        zone = await self.db.get(GeofenceZone, service_area_id)
        if not zone or not zone.is_active or str(zone.zone_type or "").upper() != "SERVICE_AREA":
            raise HTTPException(status_code=409, detail="Ride service area is missing or inactive; dispatch is blocked.")
        service_polygon = self._polygon(zone)
        if service_polygon is None:
            raise HTTPException(status_code=503, detail="Ride service-area polygon is invalid; dispatch is blocked.")

        restricted_zones = (await self.db.execute(
            select(GeofenceZone).where(
                GeofenceZone.is_active.is_(True),
                GeofenceZone.zone_type.in_(["RESTRICTED", "RESTRICTED_ZONE"]),
            )
        )).scalars().all()
        restricted_polygons = []
        for restricted_zone in restricted_zones:
            polygon = self._polygon(restricted_zone)
            if polygon is None:
                raise HTTPException(
                    status_code=503,
                    detail=f"Active restricted geofence '{restricted_zone.name}' is invalid; dispatch is blocked.",
                )
            restricted_polygons.append(polygon)
        return zone, service_polygon, restricted_polygons

    @staticmethod
    def location_is_inside_boundary(
        latitude: float,
        longitude: float,
        service_polygon,
        restricted_polygons,
    ) -> bool:
        try:
            lat, lng = float(latitude), float(longitude)
        except (TypeError, ValueError):
            return False
        if not (-90 <= lat <= 90 and -180 <= lng <= 180):
            return False
        return (
            AdminAdvancedService.is_point_in_polygon(lat, lng, service_polygon)
            and not any(
                AdminAdvancedService.is_point_in_polygon(lat, lng, polygon)
                for polygon in restricted_polygons
            )
        )

    async def is_driver_inside(self, service_area_id: str, latitude: float, longitude: float) -> bool:
        _, service_polygon, restricted_polygons = await self.load_area_boundary(service_area_id)
        return self.location_is_inside_boundary(
            latitude, longitude, service_polygon, restricted_polygons
        )

    async def require_fresh_driver_inside(self, driver_id: str, service_area_id: str) -> None:
        """Require current, fresh telemetry inside the ride area and outside restrictions."""
        from backend.application.services.live_location_service import LiveLocationService

        live_locations = LiveLocationService(db=self.db)
        if not await live_locations.is_driver_fresh(driver_id):
            raise HTTPException(
                status_code=403,
                detail="Fresh driver GPS inside the ride service area is required to accept or assign this ride.",
            )
        location = await live_locations.get_driver_live_location(driver_id)
        if (
            not location
            or location.get("latitude") is None
            or location.get("longitude") is None
            or not await self.is_driver_inside(
                service_area_id, location.get("latitude"), location.get("longitude")
            )
        ):
            raise HTTPException(
                status_code=403,
                detail="Driver is outside this ride's active service area or inside a restricted zone.",
            )

    async def active_driver_ids(self, service_area_id: str, driver_ids: list[str]) -> set[str]:
        if not driver_ids:
            return set()
        rows = (await self.db.execute(
            select(DriverServiceAreaAssignment.driver_id).where(
                DriverServiceAreaAssignment.service_area_id == service_area_id,
                DriverServiceAreaAssignment.is_active.is_(True),
                DriverServiceAreaAssignment.driver_id.in_(driver_ids),
            )
        )).scalars().all()
        return set(rows)
