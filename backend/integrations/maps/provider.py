import math
from abc import ABC, abstractmethod
from typing import List, Dict, Any, Optional
import httpx
from backend.core.config import settings

class RouteDetails:
    def __init__(self, distance_km: float, duration_minutes: float, polyline: str,
                 provider: str = "APPROXIMATE", traffic_aware: bool = False, route_label: str = "Approximate"):
        self.distance_km = distance_km
        self.duration_minutes = duration_minutes
        self.polyline = polyline
        self.provider = provider
        self.traffic_aware = traffic_aware
        self.route_label = route_label

    def to_dict(self) -> Dict[str, Any]:
        return {
            "distance_km": round(self.distance_km, 2),
            "duration_minutes": round(self.duration_minutes, 1),
            "polyline": self.polyline,
            "provider": self.provider,
            "traffic_aware": self.traffic_aware,
            "route_label": self.route_label,
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

    async def get_place_details(self, place_id: str) -> Dict[str, Any]:
        return {"available": False, "provider_verified": False, "message": "Place coordinate verification requires a configured maps provider."}

    async def get_route_alternatives(self, origin_lat: float, origin_lng: float, dest_lat: float, dest_lng: float) -> List[Dict[str, Any]]:
        route = await self.get_route(origin_lat, origin_lng, dest_lat, dest_lng)
        return [{**route.to_dict(), "route_index": 0, "provider_mode": "APPROXIMATE_NOT_LIVE"}]

    async def snap_to_road(self, latitude: float, longitude: float) -> Dict[str, Any]:
        return {"available": False, "provider_verified": False, "message": "Road snapping requires a configured Roads API provider; the selected pin is unchanged."}

    def provider_health(self) -> Dict[str, Any]:
        return {"provider": "UNKNOWN", "status": "UNAVAILABLE", "configured": False, "live_data": False}

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

    def provider_health(self) -> Dict[str, Any]:
        return {"provider": "CURATED_DIRECTORY", "status": "OFFLINE_FALLBACK", "configured": False,
                "live_data": False, "message": "Approximate curated places and geometric routes only; no live map provider is configured."}

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
            return [{**p, "coordinates_verified": False, "provider_mode": "CURATED_DIRECTORY"} for p in self.LUXURY_POIS[:4]]
        matches = [p for p in self.LUXURY_POIS if q in p["name"].lower() or q in p["address"].lower()]
        return [{**p, "coordinates_verified": False, "provider_mode": "CURATED_DIRECTORY"} for p in matches]

    async def get_place_details(self, place_id: str) -> Dict[str, Any]:
        place = next((p for p in self.LUXURY_POIS if p["place_id"] == place_id), None)
        if not place:
            return {"available": False, "provider_verified": False, "message": "This place is not in the offline curated directory. Adjust and confirm the pin on the map."}
        return {**place, "available": True, "provider_verified": False, "provider_mode": "CURATED_DIRECTORY", "coordinate_disclosure": "Directory coordinates are approximate; confirm the map pin before booking."}

    async def reverse_geocode(self, lat: float, lng: float) -> str:
        closest = min(
            self.LUXURY_POIS,
            key=lambda p: (p["lat"] - lat)**2 + (p["lng"] - lng)**2
        )
        # Never present a distant directory result as the address of the selected pin.
        if (closest["lat"] - lat) ** 2 + (closest["lng"] - lng) ** 2 > 0.0004:
            return f"Dropped pin ({lat:.5f}, {lng:.5f}) — address lookup unavailable"
        return f"Approx. near {closest['name']}, {closest['address']}"

class GoogleMapsProvider(MapsProvider):
    def __init__(self, api_key: str):
        self.api_key = api_key
        self.fallback = HaversineMockMapsProvider()

    def provider_health(self) -> Dict[str, Any]:
        return {"provider": "GOOGLE_MAPS", "status": "CONFIGURED_UNVERIFIED", "configured": True, "live_data": "not_probed",
                "message": "Credentials are present. This status does not verify API enablement, billing, quota, or live response health."}

    async def get_route(
        self, origin_lat: float, origin_lng: float, dest_lat: float, dest_lng: float
    ) -> RouteDetails:
        if not self.api_key:
            return await self.fallback.get_route(origin_lat, origin_lng, dest_lat, dest_lng)

        # 1. Try Google Routes API v2 (modern recommended endpoint)
        routes_url = f"https://routes.googleapis.com/directions/v2:computeRoutes?key={self.api_key}"
        routes_headers = {
            "Content-Type": "application/json",
            "X-Goog-FieldMask": "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline"
        }
        routes_payload = {
            "origin": {"location": {"latLng": {"latitude": origin_lat, "longitude": origin_lng}}},
            "destination": {"location": {"latLng": {"latitude": dest_lat, "longitude": dest_lng}}},
            "travelMode": "DRIVE",
            "routingPreference": "TRAFFIC_AWARE"
        }
        async with httpx.AsyncClient() as client:
            try:
                resp = await client.post(routes_url, json=routes_payload, headers=routes_headers, timeout=8.0)
                if resp.status_code == 200:
                    data = resp.json()
                    routes = data.get("routes", [])
                    if routes:
                        r = routes[0]
                        dist_km = r.get("distanceMeters", 0) / 1000.0
                        dur_str = r.get("duration", "0s").rstrip("s")
                        dur_min = float(dur_str) / 60.0
                        poly = r.get("polyline", {}).get("encodedPolyline", "")
                        return RouteDetails(distance_km=dist_km, duration_minutes=dur_min, polyline=poly)
            except Exception:
                pass

            # 2. Fallback to Google Directions API (v1)
            try:
                dir_url = "https://maps.googleapis.com/maps/api/directions/json"
                params = {
                    "origin": f"{origin_lat},{origin_lng}",
                    "destination": f"{dest_lat},{dest_lng}",
                    "mode": "driving",
                    "key": self.api_key
                }
                resp = await client.get(dir_url, params=params, timeout=8.0)
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
        if not self.api_key or not query.strip():
            return await self.fallback.autocomplete_places(query)

        # 1. Try Google Places API (New)
        new_places_url = f"https://places.googleapis.com/v1/places:autocomplete?key={self.api_key}"
        new_places_payload = {
            "input": query,
            "includedRegionCodes": ["in"],
            "languageCode": "en"
        }
        async with httpx.AsyncClient() as client:
            try:
                resp = await client.post(new_places_url, json=new_places_payload, timeout=5.0)
                if resp.status_code == 200:
                    data = resp.json()
                    sugg = data.get("suggestions", [])
                    if sugg:
                        results = []
                        for s in sugg:
                            p = s.get("placePrediction", {})
                            text = p.get("text", {}).get("text", "")
                            main_t = p.get("structuredFormat", {}).get("mainText", {}).get("text", text)
                            pid = p.get("placeId", "")
                            results.append({
                                "place_id": pid,
                                "name": main_t,
                                "address": text,
                                "coordinates_verified": False,
                                "provider_mode": "GOOGLE_PLACES"
                            })
                        return results
            except Exception:
                pass

            # 2. Fallback to Legacy Places Autocomplete API
            try:
                legacy_url = "https://maps.googleapis.com/maps/api/place/autocomplete/json"
                params = {
                    "input": query,
                    "key": self.api_key,
                    "components": "country:in"
                }
                resp = await client.get(legacy_url, params=params, timeout=5.0)
                if resp.status_code == 200:
                    data = resp.json()
                    preds = data.get("predictions", [])
                    if preds:
                        return [
                            {
                                "place_id": p.get("place_id", f"poi_{idx}"),
                                "name": p.get("structured_formatting", {}).get("main_text", p.get("description")),
                                "address": p.get("description", ""),
                                "coordinates_verified": False,
                                "provider_mode": "GOOGLE_PLACES"
                            }
                            for idx, p in enumerate(preds)
                        ]
            except Exception:
                pass

        return await self.fallback.autocomplete_places(query)

    async def get_place_details(self, place_id: str) -> Dict[str, Any]:
        if not place_id or len(place_id) > 256:
            return {"available": False, "provider_verified": False, "message": "Select a valid place suggestion and retry."}
        url = f"https://places.googleapis.com/v1/places/{place_id}"
        headers = {"X-Goog-Api-Key": self.api_key, "X-Goog-FieldMask": "id,displayName,formattedAddress,location"}
        async with httpx.AsyncClient() as client:
            try:
                response = await client.get(url, headers=headers, timeout=5.0)
                if response.status_code == 200:
                    data = response.json()
                    location = data.get("location") or {}
                    lat, lng = location.get("latitude"), location.get("longitude")
                    if isinstance(lat, (int, float)) and isinstance(lng, (int, float)) and 6 <= lat <= 38 and 68 <= lng <= 98:
                        return {"available": True, "provider_verified": True, "provider_mode": "GOOGLE_PLACES",
                                "place_id": data.get("id"), "name": (data.get("displayName") or {}).get("text", ""),
                                "address": data.get("formattedAddress", ""), "lat": float(lat), "lng": float(lng)}
            except Exception:
                pass
        return await self.fallback.get_place_details(place_id)

    async def get_route_alternatives(self, origin_lat: float, origin_lng: float, dest_lat: float, dest_lng: float) -> List[Dict[str, Any]]:
        url = f"https://routes.googleapis.com/directions/v2:computeRoutes?key={self.api_key}"
        headers = {"Content-Type": "application/json", "X-Goog-FieldMask": "routes.duration,routes.staticDuration,routes.distanceMeters,routes.polyline.encodedPolyline"}
        payload = {"origin": {"location": {"latLng": {"latitude": origin_lat, "longitude": origin_lng}}},
                   "destination": {"location": {"latLng": {"latitude": dest_lat, "longitude": dest_lng}}},
                   "travelMode": "DRIVE", "routingPreference": "TRAFFIC_AWARE_OPTIMAL",
                   "computeAlternativeRoutes": True}
        try:
            async with httpx.AsyncClient() as client:
                response = await client.post(url, json=payload, headers=headers, timeout=8.0)
                if response.status_code == 200:
                    routes = response.json().get("routes", [])
                    if routes:
                        return [{"distance_km": round(float(r.get("distanceMeters", 0)) / 1000.0, 2),
                                 "duration_minutes": round(float(str(r.get("duration", "0s")).rstrip("s")) / 60.0, 1),
                                 "static_duration_minutes": round(float(str(r.get("staticDuration", r.get("duration", "0s"))).rstrip("s")) / 60.0, 1),
                                 "polyline": r.get("polyline", {}).get("encodedPolyline", ""),
                                 "route_index": idx, "route_label": "Recommended" if idx == 0 else f"Alternative {idx}",
                                 "provider": "GOOGLE_ROUTES", "traffic_aware": True, "provider_mode": "LIVE_PROVIDER_RESPONSE"}
                                for idx, r in enumerate(routes[:3])]
        except Exception:
            pass
        route = await self.fallback.get_route(origin_lat, origin_lng, dest_lat, dest_lng)
        return [{**route.to_dict(), "route_index": 0, "provider_mode": "APPROXIMATE_NOT_LIVE",
                 "static_duration_minutes": route.duration_minutes,
                 "disclosure": "Live routing is unavailable. This straight-line estimate is approximate and does not include traffic or road restrictions."}]

    async def snap_to_road(self, latitude: float, longitude: float) -> Dict[str, Any]:
        url = "https://roads.googleapis.com/v1/nearestRoads"
        try:
            async with httpx.AsyncClient() as client:
                response = await client.get(url, params={"points": f"{latitude},{longitude}", "key": self.api_key}, timeout=5.0)
                if response.status_code == 200:
                    points = response.json().get("snappedPoints", [])
                    if points:
                        location = points[0]["location"]
                        return {"available": True, "provider_verified": True, "latitude": location["latitude"], "longitude": location["longitude"],
                                "place_id": points[0].get("placeId"), "provider_mode": "GOOGLE_ROADS"}
        except Exception:
            pass
        return {"available": False, "provider_verified": False, "message": "Road snapping is unavailable. Your original pin remains unchanged."}

    async def reverse_geocode(self, lat: float, lng: float) -> str:
        if not self.api_key:
            return await self.fallback.reverse_geocode(lat, lng)
        url = "https://maps.googleapis.com/maps/api/geocode/json"
        params = {
            "latlng": f"{lat},{lng}",
            "key": self.api_key
        }
        async with httpx.AsyncClient() as client:
            try:
                resp = await client.get(url, params=params, timeout=5.0)
                if resp.status_code == 200:
                    data = resp.json()
                    results = data.get("results", [])
                    if results:
                        return results[0].get("formatted_address", "")
            except Exception:
                pass
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
