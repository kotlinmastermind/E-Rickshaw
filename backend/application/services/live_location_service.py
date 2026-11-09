"""
Live Location & Fast Geospatial Storage Service for Vasantra Mobility.
Manages driver real-time telemetry ingestion, H3 cell spatial mapping,
Redis atomic cell migration, stale telemetry detection, and WebSocket broadcasting.
"""
import time
import json
import logging
from datetime import datetime, timezone
from typing import Optional, Dict, Any, Set

from sqlalchemy import select, and_, func
from sqlalchemy.ext.asyncio import AsyncSession

from backend.domain.models import DriverLocation, DriverProfile, DriverAvailability
from backend.application.services.h3_geospatial_service import h3_service
from backend.infrastructure.cache.redis_client import redis_client
from backend.api.routes.ws import ws_manager

logger = logging.getLogger("backend.geospatial.location")

LOCATION_TTL_SECONDS: int = 180        # 3 minutes cache for latest telemetry
CELL_SET_TTL_SECONDS: int = 600        # 10 minutes TTL for H3 cell index sets
FRESHNESS_THRESHOLD_SECONDS: int = 60  # Freshness threshold to qualify for dispatch


class LiveLocationService:
    def __init__(self, db: Optional[AsyncSession] = None):
        self.db = db

    async def ingest_driver_location(
        self,
        driver_id: str,
        latitude: float,
        longitude: float,
        heading: Optional[float] = None,
        speed_mps: Optional[float] = None,
        accuracy_m: Optional[float] = None,
        is_mock_location: bool = False,
        location_source: str = "FUSED",
        is_online: bool = True,
        is_busy: bool = False,
        active_ride_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Processes and indexes real-time driver GPS ping:
        1. Validates coordinate bounds.
        2. Computes H3 cell at configured resolution.
        3. Updates Redis fast spatial index (driver:location, driver:h3_cell, h3:drivers).
        4. Broadcasts live location to ride subscribers if driver is on an active trip.
        5. Persists location record to PostgreSQL DriverLocation table if DB session is active.
        """
        if not h3_service.validate_coordinates(latitude, longitude):
            return {"status": "REJECTED", "reason": "INVALID_COORDINATES"}

        h3_cell = h3_service.get_h3_cell(latitude, longitude)
        now = datetime.now(timezone.utc)
        epoch_now = time.time()

        location_payload = {
            "driver_id": driver_id,
            "latitude": round(latitude, 6),
            "longitude": round(longitude, 6),
            "heading": round(heading, 1) if heading is not None else None,
            "speed_mps": round(speed_mps, 2) if speed_mps is not None else None,
            "accuracy_m": round(accuracy_m, 1) if accuracy_m is not None else None,
            "h3_cell": h3_cell,
            "is_online": is_online,
            "is_busy": is_busy,
            "recorded_at": now.isoformat(),
            "epoch_timestamp": epoch_now
        }

        # 1. Update Redis telemetry cache
        loc_key = f"driver:location:{driver_id}"
        await redis_client.set_json(loc_key, location_payload, ex=LOCATION_TTL_SECONDS)
        await redis_client.set(f"driver:last_seen:{driver_id}", str(epoch_now), ex=LOCATION_TTL_SECONDS)

        # 2. Update H3 spatial index if driver is eligible for dispatch (online & not busy)
        if h3_cell and is_online and not is_busy:
            await self._update_h3_cell_membership(driver_id, h3_cell)
        else:
            # If driver went offline or busy, remove from H3 dispatch search
            await self.remove_driver_from_h3_index(driver_id)

        # 3. Broadcast real-time location to WebSocket ride channel if on active ride
        if active_ride_id:
            try:
                ws_payload = {
                    "event": "DRIVER_LOCATION_UPDATE",
                    "driver_id": driver_id,
                    "ride_id": active_ride_id,
                    "latitude": location_payload["latitude"],
                    "longitude": location_payload["longitude"],
                    "heading": location_payload["heading"],
                    "speed_mps": location_payload["speed_mps"],
                    "timestamp": location_payload["recorded_at"]
                }
                await ws_manager.broadcast_to_ride(active_ride_id, ws_payload)
            except Exception as ws_err:
                logger.debug(f"Failed to broadcast location update to ride {active_ride_id}: {ws_err}")

        # 4. Optional asynchronous persistence in PostgreSQL
        if self.db:
            try:
                loc_record = DriverLocation(
                    driver_id=driver_id,
                    latitude=latitude,
                    longitude=longitude,
                    heading=heading,
                    speed=speed_mps,
                    accuracy=accuracy_m,
                    spoof_risk_score=0.9 if is_mock_location else 0.0,
                    quality_flags_json=json.dumps(["MOCK_REPORTED"] if is_mock_location else []),
                    source=location_source,
                    recorded_at=now
                )
                self.db.add(loc_record)
                await self.db.commit()
            except Exception as db_err:
                logger.warning(f"Failed to persist DriverLocation in PostgreSQL: {db_err}")

        return {
            "status": "RECORDED",
            "driver_id": driver_id,
            "h3_cell": h3_cell,
            "timestamp": location_payload["recorded_at"]
        }

    async def _update_h3_cell_membership(self, driver_id: str, new_cell: str) -> None:
        """
        Atomically updates driver's H3 cell set membership in Redis:
        Removes driver from old cell set (if changed) and registers in new cell set.
        """
        cell_key = f"driver:h3_cell:{driver_id}"
        old_cell = await redis_client.get(cell_key)

        if old_cell and old_cell != new_cell:
            await redis_client.srem(f"h3:drivers:{old_cell}", driver_id)

        await redis_client.sadd(f"h3:drivers:{new_cell}", driver_id)
        await redis_client.expire(f"h3:drivers:{new_cell}", CELL_SET_TTL_SECONDS)
        await redis_client.set(cell_key, new_cell, ex=LOCATION_TTL_SECONDS)

    async def remove_driver_from_h3_index(self, driver_id: str) -> None:
        """Removes driver from all H3 cell search indices when going offline or becoming busy."""
        cell_key = f"driver:h3_cell:{driver_id}"
        current_cell = await redis_client.get(cell_key)
        if current_cell:
            await redis_client.srem(f"h3:drivers:{current_cell}", driver_id)
            await redis_client.delete(cell_key)

    async def get_driver_live_location(self, driver_id: str) -> Optional[Dict[str, Any]]:
        """
        Retrieves the latest driver location from Redis cache.
        Falls back to database if Redis entry is missing.
        """
        cached = await redis_client.get_json(f"driver:location:{driver_id}")
        if cached and isinstance(cached, dict):
            return cached

        # Database fallback
        if self.db:
            stmt = (
                select(DriverLocation)
                .where(DriverLocation.driver_id == driver_id)
                .order_by(DriverLocation.recorded_at.desc())
                .limit(1)
            )
            res = await self.db.execute(stmt)
            loc = res.scalar_one_or_none()
            if loc:
                h3_cell = h3_service.get_h3_cell(loc.latitude, loc.longitude)
                loc_at = (loc.recorded_at if loc.recorded_at.tzinfo else loc.recorded_at.replace(tzinfo=timezone.utc)) if loc.recorded_at else None
                return {
                    "driver_id": driver_id,
                    "latitude": loc.latitude,
                    "longitude": loc.longitude,
                    "heading": loc.heading,
                    "speed_mps": loc.speed,
                    "accuracy_m": loc.accuracy,
                    "h3_cell": h3_cell,
                    "recorded_at": loc_at.isoformat() if loc_at else datetime.now(timezone.utc).isoformat(),
                    "epoch_timestamp": loc_at.timestamp() if loc_at else time.time()
                }
        return None

    async def is_driver_fresh(self, driver_id: str, max_age_seconds: int = FRESHNESS_THRESHOLD_SECONDS) -> bool:
        """
        Verifies whether a driver's GPS telemetry was received within the freshness window.
        Stale drivers (>60s) are automatically disqualified from nearby candidate matching.
        """
        last_seen = await redis_client.get(f"driver:last_seen:{driver_id}")
        if not last_seen:
            return False
        try:
            age_seconds = time.time() - float(last_seen)
            return 0 <= age_seconds <= max_age_seconds
        except (ValueError, TypeError):
            return False

    async def get_drivers_in_cells(self, h3_cells: Set[str]) -> Set[str]:
        """
        Fast spatial lookup: returns all driver IDs currently registered in the provided H3 cells.
        Performs O(1) set operations without querying the relational database.
        """
        if not h3_cells:
            return set()

        all_drivers: Set[str] = set()
        for cell in h3_cells:
            drivers = await redis_client.smembers(f"h3:drivers:{cell}")
            if drivers:
                all_drivers.update(drivers)
        return all_drivers

    async def rebuild_h3_index_from_db(self, max_age_minutes: int = 120) -> int:
        """
        Disaster Recovery & Cold-Start Rebuilder:
        Reconstructs the Redis H3 spatial index and telemetry cache directly
        from authoritative PostgreSQL tables (DriverProfile, DriverAvailability, DriverLocation).
        Returns the number of drivers successfully re-indexed.
        """
        if not self.db:
            return 0

        latest_loc_subquery = (
            select(
                DriverLocation.driver_id,
                func.max(DriverLocation.recorded_at).label("max_recorded_at")
            )
            .group_by(DriverLocation.driver_id)
            .subquery()
        )

        query = (
            select(
                DriverProfile.id.label("driver_id"),
                DriverLocation.latitude,
                DriverLocation.longitude,
                DriverLocation.heading,
                DriverLocation.speed,
                DriverLocation.accuracy,
                DriverLocation.recorded_at,
                DriverAvailability.is_online,
                DriverAvailability.is_busy
            )
            .join(DriverAvailability, DriverAvailability.driver_id == DriverProfile.id)
            .join(latest_loc_subquery, latest_loc_subquery.c.driver_id == DriverProfile.id)
            .join(
                DriverLocation,
                and_(
                    DriverLocation.driver_id == latest_loc_subquery.c.driver_id,
                    DriverLocation.recorded_at == latest_loc_subquery.c.max_recorded_at
                )
            )
            .where(
                DriverProfile.approval_status == "APPROVED",
                DriverAvailability.is_online.is_(True),
                DriverAvailability.is_busy.is_(False)
            )
        )

        res = await self.db.execute(query)
        rows = res.all()
        reindexed_count = 0
        now_dt = datetime.now(timezone.utc)

        for r in rows:
            r_at = (r.recorded_at if r.recorded_at.tzinfo else r.recorded_at.replace(tzinfo=timezone.utc)) if r.recorded_at else None
            if r_at and max_age_minutes:
                if (now_dt - r_at).total_seconds() > (max_age_minutes * 60):
                    continue
            driver_id = r.driver_id
            lat = r.latitude
            lng = r.longitude
            if not h3_service.validate_coordinates(lat, lng):
                continue

            h3_cell = h3_service.get_h3_cell(lat, lng)
            epoch_time = r_at.timestamp() if r_at else time.time()

            payload = {
                "driver_id": driver_id,
                "latitude": round(lat, 6),
                "longitude": round(lng, 6),
                "heading": round(r.heading, 1) if r.heading is not None else None,
                "speed_mps": round(r.speed, 2) if r.speed is not None else None,
                "accuracy_m": round(r.accuracy, 1) if r.accuracy is not None else None,
                "h3_cell": h3_cell,
                "is_online": True,
                "is_busy": False,
                "recorded_at": r.recorded_at.isoformat() if r.recorded_at else datetime.now(timezone.utc).isoformat(),
                "epoch_timestamp": epoch_time
            }

            await redis_client.set_json(f"driver:location:{driver_id}", payload, ex=LOCATION_TTL_SECONDS)
            await redis_client.set(f"driver:last_seen:{driver_id}", str(epoch_time), ex=LOCATION_TTL_SECONDS)
            await self._update_h3_cell_membership(driver_id, h3_cell)
            reindexed_count += 1

        logger.info(f"Rebuilt Redis H3 spatial index: {reindexed_count} active drivers re-indexed from DB.")
        return reindexed_count
