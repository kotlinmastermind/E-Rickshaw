"""
Unit tests for HungarianMatcher and BatchDispatchOptimizer.
"""
import pytest
from backend.application.services.batch_dispatch_optimizer import (
    HungarianMatcher, BatchDispatchOptimizer, RideRequestItem, DriverCandidateItem
)
from backend.infrastructure.cache.redis_client import redis_client


def test_hungarian_matcher_1x1():
    cost = [[4.2]]
    res = HungarianMatcher.solve_min_cost_assignment(cost)
    assert res == [(0, 0)]


def test_hungarian_matcher_2x2():
    # Ride 0: 1 min to Driver 0, 10 min to Driver 1
    # Ride 1: 10 min to Driver 0, 2 min to Driver 1
    cost = [
        [1.0, 10.0],
        [10.0, 2.0]
    ]
    res = HungarianMatcher.solve_min_cost_assignment(cost)
    assert res == [(0, 0), (1, 1)]


def test_hungarian_matcher_global_optimality():
    # Demonstrates global wait-time reduction:
    # Ride 0: Driver 0 is 2 min, Driver 1 is 3 min
    # Ride 1: Driver 0 is 2.5 min, Driver 1 is 15 min
    # If Ride 0 greedily took Driver 0, Ride 1 would have to take Driver 1 (Total = 2 + 15 = 17 min)
    # Optimal bipartite matching gives Ride 0 -> Driver 1 (3 min) and Ride 1 -> Driver 0 (2.5 min) (Total = 5.5 min)!
    cost = [
        [2.0, 3.0],
        [2.5, 15.0]
    ]
    res = HungarianMatcher.solve_min_cost_assignment(cost)
    assert res == [(0, 1), (1, 0)]


def test_hungarian_matcher_rectangular_more_drivers():
    # 2 rides, 3 drivers
    cost = [
        [5.0, 1.0, 8.0],
        [2.0, 6.0, 4.0]
    ]
    # Optimal: Ride 0 -> Driver 1 (cost 1.0), Ride 1 -> Driver 0 (cost 2.0). Total = 3.0
    res = HungarianMatcher.solve_min_cost_assignment(cost)
    assert (0, 1) in res
    assert (1, 0) in res


@pytest.mark.asyncio
async def test_atomic_driver_lock():
    optimizer = BatchDispatchOptimizer(lock_ttl_seconds=30)
    driver_id = "driver_lock_test_001"
    ride_id = "ride_lock_test_001"

    # Initial lock should succeed
    acquired1 = await optimizer.acquire_atomic_driver_lock(driver_id, ride_id)
    assert acquired1 is True

    # Immediate second lock for another ride should FAIL
    acquired2 = await optimizer.acquire_atomic_driver_lock(driver_id, "ride_different_002")
    assert acquired2 is False

    # Release lock
    await optimizer.release_driver_lock(driver_id, ride_id)

    # Now locking for another ride should succeed
    acquired3 = await optimizer.acquire_atomic_driver_lock(driver_id, "ride_different_002")
    assert acquired3 is True
    await optimizer.release_driver_lock(driver_id, "ride_different_002")


@pytest.mark.asyncio
async def test_optimize_and_assign():
    optimizer = BatchDispatchOptimizer()

    r1 = RideRequestItem(ride_id="ride_1", pickup_lat=28.61, pickup_lng=77.20)
    r2 = RideRequestItem(ride_id="ride_2", pickup_lat=28.62, pickup_lng=77.21)

    d1 = DriverCandidateItem(
        driver_id="d1", user_id="u1", latitude=28.611, longitude=77.201,
        road_eta_minutes=2.0, total_score=0.9, ring_index=0
    )
    d2 = DriverCandidateItem(
        driver_id="d2", user_id="u2", latitude=28.621, longitude=77.211,
        road_eta_minutes=2.5, total_score=0.85, ring_index=0
    )

    eta_matrix = [
        [2.0, 8.0],
        [9.0, 2.5]
    ]

    assignments = await optimizer.optimize_and_assign([r1, r2], [d1, d2], eta_matrix)
    assert len(assignments) == 2
    for a in assignments:
        assert a.lock_acquired is True
        # Clean up lock
        await optimizer.release_driver_lock(a.driver_id, a.ride_id)

