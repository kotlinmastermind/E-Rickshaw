import uuid
from datetime import datetime, timedelta, timezone

import pytest
from starlette.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

from backend.main import app
from backend.api.routes import ws as ws_routes
from backend.domain.models import (
    User,
    Session,
    CustomerProfile,
    DriverProfile,
    DriverAvailability,
    Ride,
)
from backend.infrastructure.security.crypto import create_jwt_token, hash_secret
from backend.application.services.live_location_service import LiveLocationService


def _identity(prefix: str):
    suffix = uuid.uuid4().hex[:10]
    user_id = str(uuid.uuid4())
    session_id = str(uuid.uuid4())
    user = User(
        id=user_id,
        email=f"{prefix}_{suffix}@location-ws.test",
        phone_number=f"+9198{suffix[:8]}",
        full_name=f"{prefix.title()} Location Test",
        role=prefix,
        is_active=True,
    )
    session = Session(
        id=session_id,
        user_id=user_id,
        token_hash=hash_secret(str(uuid.uuid4())),
        is_revoked=False,
        expires_at=datetime.now(timezone.utc) + timedelta(hours=1),
    )
    token = create_jwt_token(
        user_id=user_id,
        role=prefix,
        session_id=session_id,
        expires_delta=timedelta(minutes=30),
    )
    return user_id, session_id, user, session, token, suffix


@pytest.mark.asyncio
async def test_driver_location_websocket_ignores_client_ride_id(db_session, monkeypatch):
    user_id, _, user, session, token, suffix = _identity("driver")
    driver = DriverProfile(
        id=str(uuid.uuid4()),
        user_id=user_id,
        license_number=f"DL-LOC-{suffix}",
        approval_status="APPROVED",
    )
    availability = DriverAvailability(
        driver_id=driver.id,
        is_online=True,
        is_busy=True,
    )
    customer_id, _, customer, customer_session, _, _ = _identity("customer")
    customer_profile = CustomerProfile(id=customer_id, user_id=customer_id)
    ride = Ride(
        id=f"RIDE-LOC-{suffix}",
        customer_id=customer_id,
        assigned_driver_id=driver.id,
        vehicle_category_id="cat-location-test",
        fare_rule_id="rule-location-test",
        status="IN_PROGRESS",
        pickup_address="Pickup",
        pickup_latitude=25.5941,
        pickup_longitude=85.1376,
        destination_address="Destination",
        destination_latitude=25.6000,
        destination_longitude=85.1400,
        estimated_distance_km=2.0,
        estimated_duration_minutes=10,
        estimated_fare_cents=5000,
    )
    db_session.add_all([user, session, driver, availability, customer, customer_session, customer_profile, ride])
    await db_session.commit()

    async def no_revocation(_key):
        return False

    received = {}

    async def capture_location(self, **kwargs):
        received.update(kwargs)
        return {"status": "RECORDED", "driver_id": kwargs["driver_id"]}

    monkeypatch.setattr(ws_routes.redis_client, "exists", no_revocation)
    monkeypatch.setattr(LiveLocationService, "ingest_driver_location", capture_location)

    client = TestClient(app)
    with client.websocket_connect(
        "/api/v1/ws/location",
        headers={"Authorization": f"Bearer {token}"},
    ) as socket:
        socket.send_json({
            "latitude": 25.5941,
            "longitude": 85.1376,
            "ride_id": "RIDE-CONTROLLED-BY-CLIENT",
        })
        socket.send_text("ping")
        assert socket.receive_text() == "pong"

    assert received["driver_id"] == driver.id
    assert received["active_ride_id"] == ride.id


@pytest.mark.asyncio
async def test_customer_location_websocket_rejects_another_customers_ride(db_session, monkeypatch):
    owner_id, _, owner, owner_session, _, suffix = _identity("customer")
    intruder_id, _, intruder, intruder_session, intruder_token, _ = _identity("customer")
    owner_profile = CustomerProfile(id=owner_id, user_id=owner_id)
    intruder_profile = CustomerProfile(id=intruder_id, user_id=intruder_id)
    ride = Ride(
        id=f"RIDE-OWNER-{suffix}",
        customer_id=owner_profile.id,
        vehicle_category_id="cat-location-test",
        fare_rule_id="rule-location-test",
        status="IN_PROGRESS",
        pickup_address="Pickup",
        pickup_latitude=25.5941,
        pickup_longitude=85.1376,
        destination_address="Destination",
        destination_latitude=25.6000,
        destination_longitude=85.1400,
        estimated_distance_km=2.0,
        estimated_duration_minutes=10,
        estimated_fare_cents=5000,
    )
    db_session.add_all([
        owner, owner_session, owner_profile,
        intruder, intruder_session, intruder_profile, ride,
    ])
    await db_session.commit()

    async def no_revocation(_key):
        return False

    broadcasts = []

    async def capture_broadcast(ride_id, payload):
        broadcasts.append((ride_id, payload))

    monkeypatch.setattr(ws_routes.redis_client, "exists", no_revocation)
    monkeypatch.setattr(ws_routes.ws_manager, "broadcast_ride_update", capture_broadcast)

    client = TestClient(app)
    with pytest.raises(WebSocketDisconnect) as error:
        with client.websocket_connect(
            "/api/v1/ws/location",
            headers={"Authorization": f"Bearer {intruder_token}"},
        ) as socket:
            socket.send_json({
                "latitude": 25.5941,
                "longitude": 85.1376,
                "ride_id": ride.id,
            })
            socket.receive_text()

    assert error.value.code == 1008
    assert broadcasts == []
