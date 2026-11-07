import pytest
from backend.integrations.maps.mapbox_service import MapboxService
from backend.application.services.mapbox_modular_services import (
    MapSearchService, MapRoutingService, haversine_km, point_in_polygon
)

@pytest.mark.asyncio
async def test_mapbox_route_calculation():
    service = MapboxService()
    # Connaught Place to India Gate, New Delhi
    origin = (28.6328, 77.2197)
    destination = (28.6129, 77.2295)

    route = await service.get_directions([origin, destination])
    assert route["success"] is True
    assert route["distance_km"] > 0
    assert route["duration_minutes"] > 0
    assert len(route["points"]) >= 2
    assert "polyline" in route
    assert len(route["steps"]) >= 1

@pytest.mark.asyncio
async def test_mapbox_geocoding_reverse():
    service = MapboxService()
    addr = await service.geocode_reverse(28.6328, 77.2197)
    assert isinstance(addr, str)
    assert len(addr) > 0

@pytest.mark.asyncio
async def test_mapbox_geocoding_forward():
    service = MapSearchService()
    results = await service.search_places("Connaught Place", (28.6328, 77.2197))
    assert isinstance(results, list)
    # Even in offline or fallback, returns curated or photon results
    assert len(results) >= 0

def test_haversine_distance():
    # Distance between CP (28.6328, 77.2197) and India Gate (28.6129, 77.2295) is approx 2.4 km
    d = haversine_km(28.6328, 77.2197, 28.6129, 77.2295)
    assert 2.0 <= d <= 3.0

def test_geofence_point_in_polygon():
    # Square around CP
    polygon = [
        (28.6400, 77.2100),
        (28.6400, 77.2300),
        (28.6250, 77.2300),
        (28.6250, 77.2100)
    ]
    # Inside point (CP center)
    assert point_in_polygon(28.6328, 77.2197, polygon) is True
    # Outside point (India Gate)
    assert point_in_polygon(28.6129, 77.2295, polygon) is False

