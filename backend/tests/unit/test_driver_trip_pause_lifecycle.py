import pytest
from types import SimpleNamespace
from fastapi import HTTPException

from backend.api.routes import dispatch
from backend.application.services.driver_trip_operations_service import DriverTripOperationsService
from backend.api.routes.ws import ws_manager


class _ScalarResult:
    def __init__(self, value):
        self.value = value

    def scalar_one_or_none(self):
        return self.value


class _PauseDb:
    def __init__(self, ride):
        self.ride = ride
        self.added = []
        self.commits = 0

    async def execute(self, _statement):
        return _ScalarResult(self.ride)

    def add(self, value):
        self.added.append(value)

    async def commit(self):
        self.commits += 1


@pytest.mark.asyncio
async def test_pause_resume_broadcasts_authoritative_state(monkeypatch):
    ride = SimpleNamespace(id="ride-1", assigned_driver_id="driver-1", status="IN_PROGRESS")
    db = _PauseDb(ride)
    service = DriverTripOperationsService(db)

    async def resolve_driver(_user_id):
        return SimpleNamespace(id="driver-1")

    broadcasts = []

    async def capture_broadcast(ride_id, payload):
        broadcasts.append((ride_id, payload))

    monkeypatch.setattr(service, "_driver", resolve_driver)
    monkeypatch.setattr(ws_manager, "broadcast_ride_update", capture_broadcast)

    result = await service.pause_or_resume("ride-1", "user-1", True, "Passenger requested a short stop")

    assert result == {"ride_id": "ride-1", "status": "PAUSED"}
    assert ride.status == "PAUSED"
    assert db.commits == 1
    assert len(db.added) == 2
    assert len(broadcasts) == 1
    broadcast_ride_id, payload = broadcasts[0]
    assert broadcast_ride_id == "ride-1"
    assert payload["event_type"] == payload["event"] == payload["type"] == "TRIP_PAUSED"
    assert payload["status"] == payload["payload"]["status"] == "PAUSED"


@pytest.mark.asyncio
async def test_resume_broadcasts_in_progress_state(monkeypatch):
    ride = SimpleNamespace(id="ride-2", assigned_driver_id="driver-1", status="PAUSED")
    db = _PauseDb(ride)
    service = DriverTripOperationsService(db)

    async def resolve_driver(_user_id):
        return SimpleNamespace(id="driver-1")

    broadcasts = []

    async def capture_broadcast(ride_id, payload):
        broadcasts.append((ride_id, payload))

    monkeypatch.setattr(service, "_driver", resolve_driver)
    monkeypatch.setattr(ws_manager, "broadcast_ride_update", capture_broadcast)

    result = await service.pause_or_resume("ride-2", "user-1", False, "Passenger is ready")

    assert result == {"ride_id": "ride-2", "status": "IN_PROGRESS"}
    assert ride.status == "IN_PROGRESS"
    assert db.commits == 1
    assert len(db.added) == 2
    assert broadcasts[0][1]["event_type"] == "TRIP_RESUMED"
    assert broadcasts[0][1]["payload"]["status"] == "IN_PROGRESS"


@pytest.mark.asyncio
async def test_invalid_pause_does_not_write_events_or_commit(monkeypatch):
    ride = SimpleNamespace(id="ride-1", assigned_driver_id="driver-1", status="PAUSED")
    db = _PauseDb(ride)
    service = DriverTripOperationsService(db)

    async def resolve_driver(_user_id):
        return SimpleNamespace(id="driver-1")

    monkeypatch.setattr(service, "_driver", resolve_driver)

    with pytest.raises(HTTPException) as error:
        await service.pause_or_resume("ride-1", "user-1", True, "Duplicate pause")

    assert error.value.status_code == 409
    assert db.added == []
    assert db.commits == 0
    assert ride.status == "PAUSED"


class _DispatchDb:
    def __init__(self):
        self.calls = 0

    async def execute(self, _statement):
        self.calls += 1
        if self.calls == 1:
            return SimpleNamespace(first=lambda: ("driver-profile-1", True, True))
        return SimpleNamespace(scalars=lambda: SimpleNamespace(all=lambda: ["ride-assigned-to-driver"]))


@pytest.mark.asyncio
async def test_driver_location_ignores_client_supplied_ride_id(monkeypatch):
    db = _DispatchDb()
    received = {}

    class _LocationService:
        def __init__(self, db):
            self.db = db

        async def ingest_driver_location(self, **kwargs):
            received.update(kwargs)
            return {"status": "RECORDED", "driver_id": kwargs["driver_id"]}

    monkeypatch.setattr(dispatch, "LiveLocationService", _LocationService)
    payload = dispatch.DriverLocationPing(
        latitude=25.5941,
        longitude=85.1376,
        active_ride_id="ride-controlled-by-client"
    )

    result = await dispatch.ingest_driver_telemetry(
        payload=payload,
        user=SimpleNamespace(id="driver-user-1"),
        db=db
    )

    assert result["status"] == "RECORDED"
    assert received["driver_id"] == "driver-profile-1"
    assert received["active_ride_id"] == "ride-assigned-to-driver"
