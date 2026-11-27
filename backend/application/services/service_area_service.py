"""Database-backed operational service-area resolution and authorization.

GeofenceZone records with zone_type=SERVICE_AREA define actual operating polygons.
Geographic master-data coverage is deliberately independent from operational coverage.
"""
import json
from typing import Optional, Tuple

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

    async def resolve_route(
        self,
        pickup_lat: float,
        pickup_lng: float,
        destination_lat: float,
        destination_lng: float,
        requested_city_code: Optional[str] = None,
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
                continue
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
        if requested_city_code and requested_city_code.strip().upper() != (zone.city_code or "").strip().upper():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Provided city code does not match the service area resolved from GPS coordinates.",
            )
        return zone

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
