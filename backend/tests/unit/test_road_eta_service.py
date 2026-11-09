"""
Unit tests for RoadETAService & road network ETA computation.
"""
import pytest
from unittest.mock import AsyncMock, patch
from backend.application.services.road_eta_service import RoadETAService, ETA_CACHE_TTL_SECONDS
from backend.application.services.driver_eligibility_service import CandidateDriver


@pytest.mark.asyncio
async def test_compute_pickup_eta():
    mock_mapbox = AsyncMock()
    mock_mapbox.get_directions.return_value = {
        "success": True,
        "provider": "MAPBOX_DIRECTIONS_API",
        "distance_km": 2.4,
        "duration_minutes": 7.2
    }

    service = RoadETAService(mapbox_service=mock_mapbox)
    res = await service.compute_pickup_eta(
        driver_lat=28.6139,
        driver_lng=77.2090,
        pickup_lat=28.6250,
        pickup_lng=77.2150
    )

    assert res["distance_km"] == 2.4
    assert res["duration_minutes"] == 7.2
    assert res["duration_seconds"] == int(7.2 * 60)
    assert res["provider"] == "MAPBOX_DIRECTIONS_API"
    assert mock_mapbox.get_directions.call_count == 1

    # Second call with same coordinates should hit Redis cache (get_directions not called again)
    res2 = await service.compute_pickup_eta(
        driver_lat=28.6139,
        driver_lng=77.2090,
        pickup_lat=28.6250,
        pickup_lng=77.2150
    )
    assert res2["distance_km"] == 2.4
    assert mock_mapbox.get_directions.call_count == 1


@pytest.mark.asyncio
async def test_compute_pickup_eta_fallback_on_error():
    mock_mapbox = AsyncMock()
    mock_mapbox.get_directions.side_effect = RuntimeError("Network timeout")

    service = RoadETAService(mapbox_service=mock_mapbox)
    res = await service.compute_pickup_eta(
        driver_lat=28.6139,
        driver_lng=77.2090,
        pickup_lat=28.6300,
        pickup_lng=77.2200
    )

    assert res["distance_km"] > 0
    assert res["duration_minutes"] > 0
    assert res["provider"] == "LOCAL_CALIBRATED_FALLBACK"


@pytest.mark.asyncio
async def test_batch_compute_candidates_eta():
    mock_mapbox = AsyncMock()
    mock_mapbox.get_directions.return_value = {
        "success": True,
        "provider": "MOCK_ROUTER",
        "distance_km": 1.5,
        "duration_minutes": 4.5
    }

    service = RoadETAService(mapbox_service=mock_mapbox)

    c1 = CandidateDriver(
        driver_id="d1",
        user_id="u1",
        latitude=28.6139,
        longitude=77.2090,
        heading=0.0,
        speed_mps=3.0,
        accuracy_m=5.0,
        h3_cell="882d431053fffff",
        ring_index=0,
        haversine_distance_km=1.0,
        rating_average=4.9,
        total_trips=150,
        wallet_balance_cents=2000
    )
    c2 = CandidateDriver(
        driver_id="d2",
        user_id="u2",
        latitude=28.6180,
        longitude=77.2120,
        heading=90.0,
        speed_mps=4.0,
        accuracy_m=5.0,
        h3_cell="882d431053fffff",
        ring_index=0,
        haversine_distance_km=1.2,
        rating_average=4.7,
        total_trips=80,
        wallet_balance_cents=1500
    )

    enriched = await service.batch_compute_candidates_eta(
        pickup_lat=28.6250,
        pickup_lng=77.2150,
        candidates=[c1, c2]
    )

    assert len(enriched) == 2
    for c in enriched:
        assert hasattr(c, "road_distance_km")
        assert hasattr(c, "road_eta_minutes")
        assert hasattr(c, "road_eta_seconds")
        assert c.road_distance_km > 0

