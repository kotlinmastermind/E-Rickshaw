"""
Unit tests for DriverEligibilityService and H3 concentric ring candidate discovery.
"""
import pytest
import time
from backend.application.services.h3_geospatial_service import h3_service
from backend.application.services.live_location_service import LiveLocationService
from backend.application.services.driver_eligibility_service import DriverEligibilityService
from backend.infrastructure.cache.redis_client import redis_client


@pytest.mark.asyncio
async def test_driver_eligibility_checks():
    live_loc = LiveLocationService(db=None)
    eligibility_svc = DriverEligibilityService(db=None, live_loc_service=live_loc)

    driver_id = "driver_eligibility_001"
    ride_id = "ride_test_999"

    # 1. Fresh driver location
    await live_loc.ingest_driver_location(
        driver_id=driver_id,
        latitude=28.6139,
        longitude=77.2090,
        is_online=True,
        is_busy=False
    )

    # Initial check: eligible
    is_eligible = await eligibility_svc.is_driver_eligible_for_offer(driver_id, ride_id=ride_id)
    assert is_eligible is True

    # 2. Add dispatch lock
    await redis_client.set(f"dispatch:lock:{driver_id}", "LOCKED", ex=30)
    assert await eligibility_svc.is_driver_eligible_for_offer(driver_id, ride_id=ride_id) is False

    # Remove dispatch lock
    await redis_client.delete(f"dispatch:lock:{driver_id}")
    assert await eligibility_svc.is_driver_eligible_for_offer(driver_id, ride_id=ride_id) is True

    # 3. Add ride cooldown
    await redis_client.set(f"cooldown:{driver_id}:{ride_id}", "1", ex=60)
    assert await eligibility_svc.is_driver_eligible_for_offer(driver_id, ride_id=ride_id) is False

    # Check for another ride: should be eligible
    assert await eligibility_svc.is_driver_eligible_for_offer(driver_id, ride_id="ride_other") is True
    await redis_client.delete(f"cooldown:{driver_id}:{ride_id}")

    # 4. Stale driver location
    past_time = time.time() - 90
    await redis_client.set(f"driver:last_seen:{driver_id}", str(past_time))
    assert await eligibility_svc.is_driver_eligible_for_offer(driver_id, ride_id=ride_id) is False


@pytest.mark.asyncio
async def test_discover_eligible_candidates_concentric_rings():
    live_loc = LiveLocationService(db=None)
    eligibility_svc = DriverEligibilityService(db=None, live_loc_service=live_loc)

    pickup_lat, pickup_lng = 28.6139, 77.2090  # Connaught Place / Central Delhi
    pickup_cell = h3_service.get_h3_cell(pickup_lat, pickup_lng)

    # Get center coordinates of ring 0, ring 1 neighbor, ring 2 neighbor
    ring0_coords = h3_service.get_cell_center(pickup_cell)
    ring1_cells = list(h3_service.get_h3_ring(pickup_cell, k=1))
    ring1_coords = h3_service.get_cell_center(ring1_cells[0])
    ring2_cells = list(h3_service.get_h3_ring(pickup_cell, k=2))
    ring2_coords = h3_service.get_cell_center(ring2_cells[0])

    driver_r0 = "driver_ring0"
    driver_r1 = "driver_ring1"
    driver_r2 = "driver_ring2"

    await live_loc.ingest_driver_location(driver_r0, ring0_coords[0], ring0_coords[1], is_online=True)
    await live_loc.ingest_driver_location(driver_r1, ring1_coords[0], ring1_coords[1], is_online=True)
    await live_loc.ingest_driver_location(driver_r2, ring2_coords[0], ring2_coords[1], is_online=True)

    candidates = await eligibility_svc.discover_eligible_candidates(
        pickup_lat=pickup_lat,
        pickup_lng=pickup_lng,
        max_rings=3
    )

    found_ids = [c.driver_id for c in candidates]
    assert driver_r0 in found_ids
    assert driver_r1 in found_ids
    assert driver_r2 in found_ids

    # Verify ring order: ring 0 first, then ring 1, then ring 2
    idx0 = found_ids.index(driver_r0)
    idx1 = found_ids.index(driver_r1)
    idx2 = found_ids.index(driver_r2)
    assert idx0 < idx1 < idx2

    # Verify that locking driver_r0 excludes him on next search
    await redis_client.set(f"dispatch:lock:{driver_r0}", "LOCKED", ex=20)
    filtered_candidates = await eligibility_svc.discover_eligible_candidates(
        pickup_lat=pickup_lat,
        pickup_lng=pickup_lng,
        max_rings=3
    )
    filtered_ids = [c.driver_id for c in filtered_candidates]
    assert driver_r0 not in filtered_ids
    assert driver_r1 in filtered_ids
    await redis_client.delete(f"dispatch:lock:{driver_r0}")

