"""
Road-Based Distance & Real-Time ETA Computation Service for Vasantra Mobility.
Provides road network distance and travel duration estimation between driver locations
and passenger pickup coordinates using Mapbox Directions, OSRM, and calibrated fallback,
backed by Redis short-lived spatial caching.
"""
import asyncio
import logging
from typing import Dict, Any, List, Optional

from backend.integrations.maps.mapbox_service import MapboxService
from backend.infrastructure.cache.redis_client import redis_client

logger = logging.getLogger("backend.dispatch.road_eta")

ETA_CACHE_TTL_SECONDS: int = 45  # 45 seconds TTL for road ETA calculations


class RoadETAService:
    def __init__(self, mapbox_service: Optional[MapboxService] = None):
        self.map_service = mapbox_service or MapboxService()

    @staticmethod
    def _generate_cache_key(
        origin_lat: float,
        origin_lng: float,
        dest_lat: float,
        dest_lng: float
    ) -> str:
        """
        Quantizes coordinates to 3 decimal places (~110 meters) to enable
        effective cache hits for nearby vehicles heading to the same pickup spot.
        """
        o_lat_q = round(origin_lat, 3)
        o_lng_q = round(origin_lng, 3)
        d_lat_q = round(dest_lat, 3)
        d_lng_q = round(dest_lng, 3)
        return f"road_eta:{o_lat_q}_{o_lng_q}:{d_lat_q}_{d_lng_q}"

    async def compute_pickup_eta(
        self,
        driver_lat: float,
        driver_lng: float,
        pickup_lat: float,
        pickup_lng: float
    ) -> Dict[str, Any]:
        """
        Calculates road network distance and travel time from driver to pickup.
        Checks Redis cache first. If cache miss, requests road geometry from routing provider.
        """
        cache_key = self._generate_cache_key(driver_lat, driver_lng, pickup_lat, pickup_lng)
        cached_result = await redis_client.get_json(cache_key)
        if cached_result and isinstance(cached_result, dict):
            return cached_result

        # Call road routing service
        try:
            route_res = await self.map_service.get_directions(
                coordinates=[(driver_lat, driver_lng), (pickup_lat, pickup_lng)],
                profile="mapbox/driving-traffic"
            )
            dist_km = float(route_res.get("distance_km", 0.0))
            dur_min = float(route_res.get("duration_minutes", 0.0))
            provider = route_res.get("provider", "UNKNOWN")
        except Exception as err:
            logger.warning(f"Error calling routing service for ETA: {err}. Using calibrated road estimation.")
            # Calibrated urban road detour factor
            from backend.application.services.h3_geospatial_service import h3_service
            direct_km = h3_service.haversine_distance_km(driver_lat, driver_lng, pickup_lat, pickup_lng)
            dist_km = round(max(direct_km * 1.3, 0.4), 2)
            dur_min = round(max((dist_km / 20.0) * 60.0, 2.0), 1)  # 20 km/h average e-rickshaw urban speed
            provider = "LOCAL_CALIBRATED_FALLBACK"

        is_routed = provider in ["MAPBOX_DIRECTIONS_API", "OSRM_ROAD_FALLBACK"]
        result = {
            "distance_km": dist_km,
            "duration_minutes": dur_min,
            "duration_seconds": int(dur_min * 60),
            "provider": provider,
            "is_road_network_routed": is_routed,
            "is_approximate_fallback": not is_routed
        }

        # Cache in Redis with short TTL
        await redis_client.set_json(cache_key, result, ex=ETA_CACHE_TTL_SECONDS)
        return result

    async def batch_compute_candidates_eta(
        self,
        pickup_lat: float,
        pickup_lng: float,
        candidates: List[Any],
        max_concurrent: int = 5
    ) -> List[Any]:
        """
        Concurrently computes road ETAs for a list of candidate drivers.
        Attaches road_distance_km, road_eta_minutes, road_eta_seconds, and eta_provider to each candidate.
        """
        if not candidates:
            return []

        semaphore = asyncio.Semaphore(max_concurrent)

        async def _enrich_candidate(candidate: Any) -> Any:
            lat = getattr(candidate, "latitude", None)
            lng = getattr(candidate, "longitude", None)
            if lat is None or lng is None:
                return candidate

            async with semaphore:
                eta_info = await self.compute_pickup_eta(
                    driver_lat=lat,
                    driver_lng=lng,
                    pickup_lat=pickup_lat,
                    pickup_lng=pickup_lng
                )

            # Assign road metrics to candidate
            setattr(candidate, "road_distance_km", eta_info["distance_km"])
            setattr(candidate, "road_eta_minutes", eta_info["duration_minutes"])
            setattr(candidate, "road_eta_seconds", eta_info["duration_seconds"])
            setattr(candidate, "eta_provider", eta_info["provider"])
            return candidate

        enriched = await asyncio.gather(*[_enrich_candidate(c) for c in candidates])
        return enriched


road_eta_service = RoadETAService()
