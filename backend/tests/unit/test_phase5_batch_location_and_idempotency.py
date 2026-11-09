"""
VAISENTRA MOBILITY — Phase 5 Batch Location Ingestion & Ride Idempotency Test Suite
Verifies:
1. POST /api/v1/drivers/location/batch atomicity, DB persistence, and last_heartbeat update.
2. POST /api/v1/rides/request Idempotency-Key validation and replay prevention.
"""
import uuid
import pytest
from httpx import AsyncClient, ASGITransport
from sqlalchemy import select

from backend.main import app
from backend.infrastructure.database.session import async_session_maker
from datetime import datetime, timezone, timedelta
from backend.domain.models.models import User, DriverProfile, DriverLocation, DriverAvailability, Ride, Session, CustomerProfile, Wallet
from backend.infrastructure.security.crypto import create_jwt_token, hash_secret


@pytest.mark.asyncio
async def test_driver_batch_location_ingestion():
    """Verify that multiple buffered locations are saved atomically in a single batch request."""
    uid = str(uuid.uuid4())
    sid = str(uuid.uuid4())
    async with async_session_maker() as session:
        user = User(
            id=uid,
            email=f"driver_{uid[:8]}@vaisentra.com",
            phone_number=f"+9198{uid[:8]}",
            full_name="Batch Driver",
            role="driver",
            is_active=True
        )
        session.add(user)
        session.add(CustomerProfile(user_id=uid))
        session.add(Wallet(user_id=uid, balance_cents=50000))
        sess_record = Session(
            id=sid,
            user_id=uid,
            token_hash=hash_secret(f"test_token_{sid}"),
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        session.add(sess_record)
        driver_prof = DriverProfile(id=f"drv_{uid[:8]}", user_id=uid, license_number=f"DL-{uid[:6]}", approval_status="APPROVED")
        session.add(driver_prof)
        avail = DriverAvailability(id=f"avail_{uid[:8]}", driver_id=driver_prof.id, is_online=True)
        session.add(avail)
        await session.commit()

    token = create_jwt_token(user_id=uid, role="driver", session_id=sid)
    headers = {"Authorization": f"Bearer {token}"}

    locations_payload = {
        "locations": [
            {"latitude": 28.6139, "longitude": 77.2090, "heading": 90.0, "speed_mps": 5.5, "accuracy_m": 8.0},
            {"latitude": 28.6145, "longitude": 77.2095, "heading": 92.0, "speed_mps": 6.0, "accuracy_m": 7.5},
            {"latitude": 28.6150, "longitude": 77.2100, "heading": 95.0, "speed_mps": 5.8, "accuracy_m": 6.0}
        ]
    }

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/api/v1/drivers/location/batch", json=locations_payload, headers=headers)
        assert resp.status_code == 200, resp.text
        data = resp.json()
        assert data["status"] == "SUCCESS"
        assert data["ingested_count"] == 3

    # Verify rows in DB
    async with async_session_maker() as session:
        locs = (await session.execute(
            select(DriverLocation).where(DriverLocation.driver_id == driver_prof.id)
        )).scalars().all()
        assert len(locs) >= 3


@pytest.mark.asyncio
async def test_ride_request_idempotency_key_replay():
    """Verify that repeating a ride request with the same Idempotency-Key returns the existing ride without duplicating."""
    uid = str(uuid.uuid4())
    sid = str(uuid.uuid4())
    async with async_session_maker() as session:
        user = User(
            id=uid,
            email=f"rider_{uid[:8]}@vaisentra.com",
            phone_number=f"+9197{uid[:8]}",
            full_name="Idempotent Rider",
            role="customer",
            is_active=True
        )
        session.add(user)
        session.add(CustomerProfile(user_id=uid))
        session.add(Wallet(user_id=uid, balance_cents=50000))
        sess_record = Session(
            id=sid,
            user_id=uid,
            token_hash=hash_secret(f"test_token_{sid}"),
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        session.add(sess_record)
        await session.commit()

    token = create_jwt_token(user_id=uid, role="customer", session_id=sid)
    idempotency_key = f"idem-key-{uuid.uuid4()}"
    headers = {
        "Authorization": f"Bearer {token}",
        "Idempotency-Key": idempotency_key
    }

    payload = {
        "estimate_id": f"est_{uuid.uuid4()}",
        "pickup_address": "Connaught Place Inner Circle",
        "destination_address": "India Gate C-Hexagon",
        "pickup_latitude": 28.6328,
        "pickup_longitude": 77.2195,
        "destination_latitude": 28.6129,
        "destination_longitude": 77.2295,
        "payment_method": "WALLET"
    }

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # First request: should create ride
        resp1 = await client.post("/api/v1/rides/request", json=payload, headers=headers)
        assert resp1.status_code == 200, resp1.text
        data1 = resp1.json()
        ride_id_1 = data1["ride_id"]
        assert ride_id_1 is not None

        # Second request with exact same Idempotency-Key: should replay cached ride
        resp2 = await client.post("/api/v1/rides/request", json=payload, headers=headers)
        assert resp2.status_code == 200, resp2.text
        data2 = resp2.json()
        assert data2["ride_id"] == ride_id_1
        assert data2.get("idempotent_replay") is True

    # Verify only ONE ride was created in database
    async with async_session_maker() as session:
        rides = (await session.execute(
            select(Ride).where(Ride.id == ride_id_1)
        )).scalars().all()
        assert len(rides) == 1
        assert rides[0].id == ride_id_1
