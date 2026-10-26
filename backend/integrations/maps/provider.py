import math
from abc import ABC, abstractmethod
from typing import List, Dict, Any, Optional
import httpx
from backend.core.config import settings

class RouteDetails:
    def __init__(self, distance_km: float, duration_minutes: float, polyline: str):
        self.distance_km = distance_km
        self.duration_minutes = duration_minutes
        self.polyline = polyline

    def to_dict(self) -> Dict[str, Any]:
        return {
            "distance_km": round(self.distance_km, 2),
            "duration_minutes": round(self.duration_minutes, 1),
            "polyline": self.polyline
        }

class MapsProvider(ABC):
    @abstractmethod
    async def get_route(
        self,
        origin_lat: float, origin_lng: float,
        dest_lat: float, dest_lng: float
    ) -> RouteDetails:
        pass

    @abstractmethod
    async def autocomplete_places(self, query: str) -> List[Dict[str, Any]]:
        pass

    @abstractmethod
    async def reverse_geocode(self, lat: float, lng: float) -> str:
        pass

class HaversineMockMapsProvider(MapsProvider):
    """
    Deterministic geometric routing engine utilizing the Haversine formula
    with real-world urban road winding coefficients (1.28x factor) for accurate estimates.
    """
    LUXURY_POIS = [
        {"place_id": "poi_taj_palace", "name": "The Taj Mahal Palace & Tower", "address": "Apollo Bunder, Colaba, Mumbai", "lat": 18.9217, "lng": 72.8332},
        {"place_id": "poi_oberoi", "name": "The Oberoi Grand Pavilion", "address": "Nariman Point, Marine Drive, Mumbai", "lat": 18.9272, "lng": 72.8205},
        {"place_id": "poi_bkc_ritz", "name": "Bandra Kurla Executive Financial District", "address": "G Block BKC, Bandra East, Mumbai", "lat": 19.0657, "lng": 72.8687},
        {"place_id": "poi_airport_t2", "name": "Chhatrapati Shivaji Maharaj T2 VIP Terminal", "address": "Sahar, Andheri East, Mumbai", "lat": 19.0974, "lng": 72.8744},
        {"place_id": "poi_st_regis", "name": "The St. Regis Penthouse Suites", "address": "462 Senapati Bapat Marg, Lower Parel, Mumbai", "lat": 18.9934, "lng": 72.8242},
        {"place_id": "poi_worli_seaface", "name": "Worli Sea Face Luxury Boulevard", "address": "Worli Sea Face, Worli, Mumbai", "lat": 19.0134, "lng": 72.8152}
    ]

    async def get_route(
        self,
        origin_lat: float, origin_lng: float,
        dest_lat: float, dest_lng: float
    ) -> RouteDetails:
        # Haversine calculation
        R = 6371.0 # Earth radius in km
        dlat = math.radians(dest_lat - origin_lat)
        dlng = math.radians(dest_lng - origin_lng)
        a = (math.sin(dlat / 2) ** 2 +
             math.cos(math.radians(origin_lat)) * math.cos(math.radians(dest_lat)) *
             math.sin(dlng / 2) ** 2)
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
        straight_km = R * c
        # Urban routing factor (accounting for turns, signals, flyovers)
        distance_km = max(straight_km * 1.28, 0.5)
        # Average luxury chauffeur speed in metro: 28 km/h + 3 min baseline buffer
        duration_minutes = max((distance_km / 28.0) * 60.0 + 3.0, 5.0)
        
        # Simplified polyline representation
        polyline = f"{origin_lat},{origin_lng};{(origin_lat+dest_lat)/2},{(origin_lng+dest_lng)/2};{dest_lat},{dest_lng}"
        return RouteDetails(distance_km=distance_km, duration_minutes=duration_minutes, polyline=polyline)

    async def autocomplete_places(self, query: str) -> List[Dict[str, Any]]:
        q = query.lower().strip()
        if not q:
            return self.LUXURY_POIS[:4]
        matches = [p for p in self.LUXURY_POIS if q in p["name"].lower() or q in p["address"].lower()]
        if not matches:
            # Fallback dynamic result
            return [{
                "place_id": f"dyn_{abs(hash(query)) % 100000}",
                "name": query.title(),
                "address": f"{query.title()} Executive Enclave, Metropolitan District",
                "lat": 19.0760,
                "lng": 72.8777
            }]
        return matches

    async def reverse_geocode(self, lat: float, lng: float) -> str:
        closest = min(
            self.LUXURY_POIS,
            key=lambda p: (p["lat"] - lat)**2 + (p["lng"] - lng)**2
        )
        return f"{closest['name']}, {closest['address']}"

class GoogleMapsProvider(MapsProvider):
    def __init__(self, api_key: str):
        self.api_key = api_key
        self.fallback = HaversineMockMapsProvider()

    async def get_route(
        self, origin_lat: float, origin_lng: float, dest_lat: float, dest_lng: float
    ) -> RouteDetails:
        if not self.api_key:
            return await self.fallback.get_route(origin_lat, origin_lng, dest_lat, dest_lng)
        url = "https://maps.googleapis.com/maps/api/directions/json"
        params = {
            "origin": f"{origin_lat},{origin_lng}",
            "destination": f"{dest_lat},{dest_lng}",
            "mode": "driving",
            "key": self.api_key
        }
        async with httpx.AsyncClient() as client:
            try:
                resp = await client.get(url, params=params, timeout=10.0)
                if resp.status_code == 200:
                    data = resp.json()
                    if data.get("routes"):
                        leg = data["routes"][0]["legs"][0]
                        dist_km = leg["distance"]["value"] / 1000.0
                        dur_min = leg["duration"]["value"] / 60.0
                        poly = data["routes"][0]["overview_polyline"]["points"]
                        return RouteDetails(distance_km=dist_km, duration_minutes=dur_min, polyline=poly)
            except Exception:
                pass
        return await self.fallback.get_route(origin_lat, origin_lng, dest_lat, dest_lng)

    async def autocomplete_places(self, query: str) -> List[Dict[str, Any]]:
        return await self.fallback.autocomplete_places(query)

    async def reverse_geocode(self, lat: float, lng: float) -> str:
        return await self.fallback.reverse_geocode(lat, lng)

_maps_instance: Optional[MapsProvider] = None

def get_maps_provider() -> MapsProvider:
    global _maps_instance
    if _maps_instance is None:
        if settings.GOOGLE_MAPS_API_KEY:
            _maps_instance = GoogleMapsProvider(settings.GOOGLE_MAPS_API_KEY)
        else:
            _maps_instance = HaversineMockMapsProvider()
    return _maps_instance
