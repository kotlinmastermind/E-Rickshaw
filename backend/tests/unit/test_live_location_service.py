"""
Unit tests for LiveLocationService & fast Redis geospatial indexing.
"""
import asyncio
import pytest
import time
from backend.application.services.live_location_service import LiveLocationService
from backend.infrastructure.cache.redis_client import redis_client
from backend.api.routes.ws import ws_manager


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
async def test_ride_location_broadcast_matches_rider_websocket_contract(monkeypatch):
    service = LiveLocationService(db=None)
    broadcasts = []

    async def capture_broadcast(ride_id, payload):
        broadcasts.append((ride_id, payload))

    monkeypatch.setattr(ws_manager, "broadcast_to_ride", capture_broadcast)
    result = await service.ingest_driver_location(
        driver_id="driver_ws_contract",
        latitude=25.5941,
        longitude=85.1376,
        speed_mps=4.5,
        is_online=True,
        is_busy=True,
        active_ride_id="ride_ws_contract",
    )

    assert result["status"] == "RECORDED"
    assert len(broadcasts) == 1
    ride_id, payload = broadcasts[0]
    assert ride_id == "ride_ws_contract"
    assert payload["event_type"] == "DRIVER_LOCATION_UPDATE"
    assert payload["payload"]["latitude"] == 25.5941
    assert payload["payload"]["longitude"] == 85.1376
    assert payload["payload"]["speed_mps"] == 4.5


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



@pytest.mark.asyncio
async def test_inflight_ride_websocket_rejects_revoked_session(monkeypatch):
    from backend.api.routes import ws

    async def revoked_exists(key):
        return key == "revoked_session:session_revoked"

    monkeypatch.setattr(ws.redis_client, "exists", revoked_exists)
    monkeypatch.setattr(
        ws,
        "decode_jwt_token",
        lambda token: {"exp": time.time() + 60},
    )

    class UnusedDatabase:
        async def execute(self, statement):
            raise AssertionError("Database must not be queried after revocation")

    authorized = await ws.verify_inflight_driver_authorization(
        user_id="user_revoked",
        session_id="session_revoked",
        role="customer",
        driver_id="",
        token_str="valid.jwt.token",
        db_session=UnusedDatabase(),
    )

    assert authorized is False


@pytest.mark.asyncio
async def test_inflight_ride_websocket_rejects_driver_after_approval_removed(monkeypatch):
    from backend.api.routes import ws

    async def not_revoked(key):
        return False

    monkeypatch.setattr(ws.redis_client, "exists", not_revoked)
    monkeypatch.setattr(
        ws,
        "decode_jwt_token",
        lambda token: {"exp": time.time() + 60},
    )

    class ScalarResult:
        def __init__(self, value):
            self.value = value

        def scalar_one_or_none(self):
            return self.value

    class DriverApprovalDatabase:
        def __init__(self):
            self.calls = 0

        async def execute(self, statement):
            self.calls += 1
            return ScalarResult(True if self.calls == 1 else "PENDING")

    db = DriverApprovalDatabase()
    authorized = await ws.verify_inflight_driver_authorization(
        user_id="user_driver",
        session_id="session_driver",
        role="driver",
        driver_id="driver_profile",
        token_str="valid.jwt.token",
        db_session=db,
    )

    assert authorized is False
    assert db.calls == 2



@pytest.mark.asyncio
async def test_out_of_order_location_update_cannot_overwrite_newer_snapshot():
    service = LiveLocationService(db=None)
    driver_id = "driver_stale_snapshot_001"
    newer_lat, newer_lng = 25.5941, 85.1376
    from backend.application.services.h3_geospatial_service import h3_service
    newer_cell = h3_service.get_h3_cell(newer_lat, newer_lng)
    newer_epoch = time.time() + 30
    newer_payload = {
        "driver_id": driver_id,
        "latitude": newer_lat,
        "longitude": newer_lng,
        "h3_cell": newer_cell,
        "is_online": True,
        "is_busy": False,
        "recorded_at": "future-test-snapshot",
        "epoch_timestamp": newer_epoch,
    }
    await redis_client.set_json(f"driver:location:{driver_id}", newer_payload, ex=180)
    await redis_client.set(f"driver:last_seen:{driver_id}", str(newer_epoch), ex=180)
    await redis_client.set(f"driver:h3_cell:{driver_id}", newer_cell, ex=180)
    await redis_client.sadd(f"h3:drivers:{newer_cell}", driver_id)

    result = await service.ingest_driver_location(
        driver_id=driver_id,
        latitude=28.6139,
        longitude=77.2090,
        is_online=True,
        is_busy=False,
    )

    assert result["status"] == "REJECTED"
    assert result["reason"] == "STALE_LOCATION_UPDATE"
    cached = await redis_client.get_json(f"driver:location:{driver_id}")
    assert cached["latitude"] == newer_lat
    assert cached["longitude"] == newer_lng
    assert cached["h3_cell"] == newer_cell
    assert await redis_client.get(f"driver:h3_cell:{driver_id}") == newer_cell
    assert driver_id in await redis_client.smembers(f"h3:drivers:{newer_cell}")


@pytest.mark.asyncio
async def test_concurrent_location_updates_leave_one_consistent_h3_membership():
    service = LiveLocationService(db=None)
    driver_id = "driver_concurrent_index_001"

    await asyncio.gather(
        service.ingest_driver_location(
            driver_id=driver_id,
            latitude=28.6139,
            longitude=77.2090,
            is_online=True,
            is_busy=False,
        ),
        service.ingest_driver_location(
            driver_id=driver_id,
            latitude=25.5941,
            longitude=85.1376,
            is_online=True,
            is_busy=False,
        ),
    )

    cached = await redis_client.get_json(f"driver:location:{driver_id}")
    indexed_cell = await redis_client.get(f"driver:h3_cell:{driver_id}")
    assert cached is not None
    assert indexed_cell == cached["h3_cell"]

    cell_members = await redis_client.smembers(f"h3:drivers:{indexed_cell}")
    assert driver_id in cell_members
    other_cell = h3_service.get_h3_cell(
        25.5941 if cached["latitude"] == 28.6139 else 28.6139,
        85.1376 if cached["longitude"] == 77.2090 else 77.2090,
    )
    assert driver_id not in await redis_client.smembers(f"h3:drivers:{other_cell}")
