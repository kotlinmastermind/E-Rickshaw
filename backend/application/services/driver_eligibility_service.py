"""
Driver Discovery & Eligibility Validation Service for Vasantra Mobility.
Combines H3 concentric hexagonal grid search with Redis fast telemetry,
driver status verification, active cooldown checks, and vehicle eligibility.
"""
import logging
from typing import Optional, Dict, Any, List, Set
from dataclasses import dataclass

from sqlalchemy import select, and_, or_
from sqlalchemy.ext.asyncio import AsyncSession

from backend.application.services.h3_geospatial_service import h3_service
from backend.application.services.live_location_service import LiveLocationService, FRESHNESS_THRESHOLD_SECONDS
from backend.infrastructure.cache.redis_client import redis_client
from backend.domain.models import DriverProfile, DriverAvailability, DriverVehicleAssignment, Vehicle

logger = logging.getLogger("backend.dispatch.eligibility")


@dataclass
class CandidateDriver:
    driver_id: str
    user_id: Optional[str]
    latitude: float
    longitude: float
    heading: Optional[float]
    speed_mps: Optional[float]
    accuracy_m: Optional[float]
    h3_cell: str
    ring_index: int
    haversine_distance_km: float
    rating_average: float
    total_trips: int
    wallet_balance_cents: int
    vehicle_category_id: Optional[str] = None


class DriverEligibilityService:
    def __init__(self, db: Optional[AsyncSession] = None, live_loc_service: Optional[LiveLocationService] = None):
        self.db = db
        self.live_loc = live_loc_service or LiveLocationService(db=db)

    async def is_driver_eligible_for_offer(
        self,
        driver_id: str,
        ride_id: Optional[str] = None,
        max_age_seconds: int = FRESHNESS_THRESHOLD_SECONDS
    ) -> bool:
        """
        Fast Redis-based filter:
        1. Checks whether driver has an active lock (currently considering an offer).
        2. Checks whether driver is in cooldown for this specific ride.
        3. Checks whether driver's GPS telemetry is fresh (<60s).
        """
        # 1. Dispatch lock check
        is_locked = await redis_client.exists(f"dispatch:lock:{driver_id}")
        if is_locked:
            logger.debug(f"Driver {driver_id} is locked by an in-flight dispatch offer.")
            return False

        # 2. Ride cooldown check
        if ride_id:
            on_cooldown = await redis_client.exists(f"cooldown:{driver_id}:{ride_id}")
            if on_cooldown:
                logger.debug(f"Driver {driver_id} is on cooldown for ride {ride_id}.")
                return False

        # 3. Telemetry freshness check
        is_fresh = await self.live_loc.is_driver_fresh(driver_id, max_age_seconds=max_age_seconds)
        if not is_fresh:
            logger.debug(f"Driver {driver_id} GPS telemetry is stale (> {max_age_seconds}s).")
            return False

        return True

    async def discover_eligible_candidates(
        self,
        pickup_lat: float,
        pickup_lng: float,
        max_rings: int = 3,
        ride_id: Optional[str] = None,
        required_category_id: Optional[str] = None,
        max_candidates: int = 20
    ) -> List[CandidateDriver]:
        """
        Discovers nearby eligible drivers using concentric H3 hexagonal rings:
        Ring 0: ~1.0 km diameter (immediate pickup zone)
        Ring 1: ~2.5 km diameter
        Ring 2: ~4.5 km diameter
        Ring 3: ~7.0 km diameter

        Progressively searches each ring until enough candidates are collected.
        """
        if not h3_service.validate_coordinates(pickup_lat, pickup_lng):
            logger.warning(f"Invalid pickup coordinates: ({pickup_lat}, {pickup_lng})")
            return []

        pickup_cell = h3_service.get_h3_cell(pickup_lat, pickup_lng)
        search_rings = h3_service.get_progressive_search_rings(pickup_cell, max_rings=max_rings)

        all_candidate_drivers: List[CandidateDriver] = []
        already_seen_driver_ids: Set[str] = set()

        for ring_idx, ring_cells in enumerate(search_rings):
            # Fast O(1) Redis set union for all drivers in this ring
            driver_ids_in_ring = await self.live_loc.get_drivers_in_cells(ring_cells)
            candidate_ids_in_ring = [
                d_id for d_id in driver_ids_in_ring if d_id not in already_seen_driver_ids
            ]

            if not candidate_ids_in_ring:
                continue

            # Filter candidates through Redis fast eligibility checks
            eligible_ring_ids: List[str] = []
            for d_id in candidate_ids_in_ring:
                already_seen_driver_ids.add(d_id)
                if await self.is_driver_eligible_for_offer(d_id, ride_id=ride_id):
                    eligible_ring_ids.append(d_id)

            if not eligible_ring_ids:
                continue

            # Batch retrieve database profiles and vehicle assignments if DB session is available
            db_profile_map: Dict[str, Dict[str, Any]] = {}
            if self.db:
                db_profile_map = await self._batch_fetch_driver_profiles(
                    eligible_ring_ids, required_category_id=required_category_id
                )
                # Keep only those approved and matching required category
                eligible_ring_ids = [d_id for d_id in eligible_ring_ids if d_id in db_profile_map]

            # Construct CandidateDriver objects
            for d_id in eligible_ring_ids:
                loc = await self.live_loc.get_driver_live_location(d_id)
                if not loc:
                    continue

                lat = loc.get("latitude")
                lng = loc.get("longitude")
                if lat is None or lng is None:
                    continue

                h_dist = h3_service.haversine_distance_km(pickup_lat, pickup_lng, lat, lng)

                prof = db_profile_map.get(d_id, {})
                candidate = CandidateDriver(
                    driver_id=d_id,
                    user_id=prof.get("user_id"),
                    latitude=lat,
                    longitude=lng,
                    heading=loc.get("heading"),
                    speed_mps=loc.get("speed_mps"),
                    accuracy_m=loc.get("accuracy_m"),
                    h3_cell=loc.get("h3_cell") or h3_service.get_h3_cell(lat, lng),
                    ring_index=ring_idx,
                    haversine_distance_km=round(h_dist, 3),
                    rating_average=prof.get("rating_average", 5.0),
                    total_trips=prof.get("total_trips", 0),
                    wallet_balance_cents=prof.get("wallet_balance_cents", 0),
                    vehicle_category_id=prof.get("vehicle_category_id")
                )
                all_candidate_drivers.append(candidate)

            # If we've gathered enough candidates, we don't need to expand to further outer rings
            if len(all_candidate_drivers) >= max_candidates:
                break

        # Sort primarily by ring index, then by haversine distance
        all_candidate_drivers.sort(key=lambda c: (c.ring_index, c.haversine_distance_km))
        return all_candidate_drivers[:max_candidates]

    async def _batch_fetch_driver_profiles(
        self,
        driver_ids: List[str],
        required_category_id: Optional[str] = None
    ) -> Dict[str, Dict[str, Any]]:
        """
        Batch fetches driver status, ratings, and vehicle category from PostgreSQL.
        Guarantees O(1) query complexity regardless of how many drivers are candidate in the cell.
        """
        if not self.db or not driver_ids:
            return {}

        query = (
            select(
                DriverProfile.id,
                DriverProfile.user_id,
                DriverProfile.approval_status,
                DriverProfile.rating_average,
                DriverProfile.total_trips,
                DriverProfile.wallet_balance_cents,
                DriverAvailability.is_online,
                DriverAvailability.is_busy,
                Vehicle.category_id.label("vehicle_category_id")
            )
            .join(DriverAvailability, DriverAvailability.driver_id == DriverProfile.id)
            .outerjoin(
                DriverVehicleAssignment,
                and_(
                    DriverVehicleAssignment.driver_id == DriverProfile.id,
                    DriverVehicleAssignment.is_active.is_(True)
                )
            )
            .outerjoin(Vehicle, Vehicle.id == DriverVehicleAssignment.vehicle_id)
            .where(
                DriverProfile.id.in_(driver_ids),
                DriverProfile.approval_status == "APPROVED",
                DriverAvailability.is_online.is_(True),
                DriverAvailability.is_busy.is_(False)
            )
        )

        if required_category_id:
            query = query.where(
                or_(
                    Vehicle.category_id == required_category_id,
                    Vehicle.category_id.is_(None)
                )
            )

        res = await self.db.execute(query)
        rows = res.all()

        profile_map: Dict[str, Dict[str, Any]] = {}
        for r in rows:
            profile_map[r.id] = {
                "user_id": r.user_id,
                "rating_average": float(r.rating_average or 5.0),
                "total_trips": int(r.total_trips or 0),
                "wallet_balance_cents": int(r.wallet_balance_cents or 0),
                "vehicle_category_id": r.vehicle_category_id
            }

        return profile_map
