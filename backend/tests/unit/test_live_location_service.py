"""
Unit tests for LiveLocationService & fast Redis geospatial indexing.
"""
import pytest
import time
from backend.application.services.live_location_service import LiveLocationService
from backend.infrastructure.cache.redis_client import redis_client


@pytest.mark.asyncio
async def test_ingest_driver_location_valid():
    service = LiveLocationService(db=None)
    driver_id = "driver_test_001"
    lat, lng = 28.6139, 77.2090  # New Delhi

    res = await service.ingest_driver_location(
        driver_id=driver_id,
        latitude=lat,
        longitude=lng,
        heading=180.0,
        speed_mps=5.2,
        accuracy_m=4.0,
        is_online=True,
        is_busy=False
    )

    assert res["status"] == "RECORDED"
    assert res["driver_id"] == driver_id
    assert res["h3_cell"] is not None

    # Check cached location
    cached = await service.get_driver_live_location(driver_id)
    assert cached is not None
    assert cached["latitude"] == round(lat, 6)
    assert cached["longitude"] == round(lng, 6)
    assert cached["heading"] == 180.0
    assert cached["speed_mps"] == 5.2

    # Check driver freshness
    is_fresh = await service.is_driver_fresh(driver_id, max_age_seconds=60)
    assert is_fresh is True

    # Check membership in H3 cell
    cell = res["h3_cell"]
    drivers_in_cell = await service.get_drivers_in_cells({cell})
    assert driver_id in drivers_in_cell


@pytest.mark.asyncio
async def test_ingest_driver_location_invalid_coords():
    service = LiveLocationService(db=None)
    res = await service.ingest_driver_location(
        driver_id="driver_bad",
        latitude=100.0,  # Invalid latitude > 90
        longitude=77.2090
    )
    assert res["status"] == "REJECTED"
    assert res["reason"] == "INVALID_COORDINATES"


@pytest.mark.asyncio
async def test_driver_cell_migration_on_movement():
    service = LiveLocationService(db=None)
    driver_id = "driver_moving_001"

    # Location 1: New Delhi Connaught Place
    res1 = await service.ingest_driver_location(
        driver_id=driver_id,
        latitude=28.6315,
        longitude=77.2167,
        is_online=True,
        is_busy=False
    )
    cell1 = res1["h3_cell"]

    # Location 2: Far away in Noida (~15km away, different H3 Res 8 cell)
    res2 = await service.ingest_driver_location(
        driver_id=driver_id,
        latitude=28.5355,
        longitude=77.3910,
        is_online=True,
        is_busy=False
    )
    cell2 = res2["h3_cell"]
    assert cell1 != cell2

    # Driver should NO LONGER be in cell1
    drivers_in_cell1 = await service.get_drivers_in_cells({cell1})
    assert driver_id not in drivers_in_cell1

    # Driver SHOULD be in cell2
    drivers_in_cell2 = await service.get_drivers_in_cells({cell2})
    assert driver_id in drivers_in_cell2


@pytest.mark.asyncio
async def test_driver_offline_removes_from_h3_index():
    service = LiveLocationService(db=None)
    driver_id = "driver_offline_001"

    # Online ping
    res = await service.ingest_driver_location(
        driver_id=driver_id,
        latitude=28.6139,
        longitude=77.2090,
        is_online=True,
        is_busy=False
    )
    cell = res["h3_cell"]
    assert driver_id in await service.get_drivers_in_cells({cell})

    # Go offline
    await service.ingest_driver_location(
        driver_id=driver_id,
        latitude=28.6139,
        longitude=77.2090,
        is_online=False,
        is_busy=False
    )

    # Should be removed from cell
    assert driver_id not in await service.get_drivers_in_cells({cell})


@pytest.mark.asyncio
async def test_driver_freshness_threshold():
    service = LiveLocationService(db=None)
    driver_id = "driver_stale_test"

    # Artificially set last seen 120 seconds ago
    past_time = time.time() - 120
    await redis_client.set(f"driver:last_seen:{driver_id}", str(past_time))

    assert await service.is_driver_fresh(driver_id, max_age_seconds=60) is False
    assert await service.is_driver_fresh(driver_id, max_age_seconds=180) is True

