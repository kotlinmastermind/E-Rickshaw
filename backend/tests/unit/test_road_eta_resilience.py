"""
Resilience & Verification Tests for RoadETAService.
Covers:
- Valid Mapbox Directions API route
- OSRM route fallback
- Provider timeout handling
- Malformed/invalid API response
- Both routing providers offline (calibrated road geometric fallback)
- Cache expiration and re-computation
- Non-misrepresentation check (is_road_network_routed vs is_approximate_fallback)
"""
import pytest
from unittest.mock import AsyncMock
from backend.application.services.road_eta_service import RoadETAService
from backend.infrastructure.cache.redis_client import redis_client


@pytest.mark.asyncio
async def test_road_eta_valid_mapbox():
    mock_mapbox = AsyncMock()
    mock_mapbox.get_directions.return_value = {
        "success": True,
        "provider": "MAPBOX_DIRECTIONS_API",
        "distance_km": 3.2,
        "duration_minutes": 9.5
    }

    service = RoadETAService(mapbox_service=mock_mapbox)
    res = await service.compute_pickup_eta(28.6139, 77.2090, 28.6300, 77.2200)

    assert res["distance_km"] == 3.2
    assert res["duration_minutes"] == 9.5
    assert res["provider"] == "MAPBOX_DIRECTIONS_API"
    assert res["is_road_network_routed"] is True
    assert res["is_approximate_fallback"] is False


@pytest.mark.asyncio
async def test_road_eta_osrm_fallback():
    mock_mapbox = AsyncMock()
    mock_mapbox.get_directions.return_value = {
        "success": True,
        "provider": "OSRM_ROAD_FALLBACK",
        "distance_km": 3.4,
        "duration_minutes": 10.2
    }

    service = RoadETAService(mapbox_service=mock_mapbox)
    # Using distinct coords to avoid collision with other test cache
    res = await service.compute_pickup_eta(28.5001, 77.1001, 28.5201, 77.1201)

    assert res["distance_km"] == 3.4
    assert res["provider"] == "OSRM_ROAD_FALLBACK"
    assert res["is_road_network_routed"] is True
    assert res["is_approximate_fallback"] is False


@pytest.mark.asyncio
async def test_road_eta_provider_timeout_and_calibrated_fallback():
    mock_mapbox = AsyncMock()
    mock_mapbox.get_directions.side_effect = TimeoutError("HTTP request timed out after 10.0s")

    service = RoadETAService(mapbox_service=mock_mapbox)
    res = await service.compute_pickup_eta(28.5500, 77.2500, 28.5700, 77.2700)

    # Must fall back gracefully and explicitly flag that it is approximate
    assert res["distance_km"] > 0
    assert res["duration_minutes"] > 0
    assert res["provider"] == "LOCAL_CALIBRATED_FALLBACK"
    assert res["is_road_network_routed"] is False
    assert res["is_approximate_fallback"] is True


@pytest.mark.asyncio
async def test_road_eta_malformed_response_handling():
    mock_mapbox = AsyncMock()
    mock_mapbox.get_directions.return_value = {
        "success": False,
        "error": "No route could be found between points"
    }

    service = RoadETAService(mapbox_service=mock_mapbox)
    res = await service.compute_pickup_eta(28.7000, 77.3000, 28.7200, 77.3200)

    assert res["distance_km"] >= 0.0
    assert res["duration_minutes"] >= 0.0


@pytest.mark.asyncio
async def test_road_eta_cache_hit_and_expiration():
    mock_mapbox = AsyncMock()
    mock_mapbox.get_directions.return_value = {
        "success": True,
        "provider": "MAPBOX_DIRECTIONS_API",
        "distance_km": 1.8,
        "duration_minutes": 5.0
    }

    service = RoadETAService(mapbox_service=mock_mapbox)
    lat1, lng1, lat2, lng2 = 28.6501, 77.2301, 28.6601, 77.2401
    cache_key = service._generate_cache_key(lat1, lng1, lat2, lng2)

    # Clean initial state
    await redis_client.delete(cache_key)

    # Call 1: Misses cache, calls provider
    res1 = await service.compute_pickup_eta(lat1, lng1, lat2, lng2)
    assert res1["distance_km"] == 1.8
    assert mock_mapbox.get_directions.call_count == 1

    # Call 2: Hits cache, provider NOT called again
    res2 = await service.compute_pickup_eta(lat1, lng1, lat2, lng2)
    assert res2["distance_km"] == 1.8
    assert mock_mapbox.get_directions.call_count == 1

    # Simulate cache eviction / expiration
    await redis_client.delete(cache_key)

    # Call 3: Re-fetches from provider
    res3 = await service.compute_pickup_eta(lat1, lng1, lat2, lng2)
    assert res3["distance_km"] == 1.8
    assert mock_mapbox.get_directions.call_count == 2

