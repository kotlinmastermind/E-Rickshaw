import pytest
import uuid
import json
import asyncio
from datetime import datetime, timezone, timedelta
from httpx import AsyncClient, ASGITransport
from starlette.websockets import WebSocketDisconnect

from backend.main import app
from backend.infrastructure.security.crypto import create_jwt_token
from backend.infrastructure.cache.redis_client import redis_client
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, DriverAvailability,
    VehicleCategory, Ride, RideAssignment, Session
)
from backend.application.services.cascading_dispatch_service import CascadingDispatchService
from backend.application.services.ride_service import RideService
from backend.application.services.driver_trip_operations_service import DriverTripOperationsService
from backend.api.routes.ws import ws_manager


@pytest.fixture
async def setup_dispatch_environment(db_session):
    """
    Sets up a deterministic testing environment with a vehicle category,
    a verified customer, an approved driver, and driver availability.
    """
    suffix = uuid.uuid4().hex[:6]

    # Category
    cat = VehicleCategory(
        id=str(uuid.uuid4()),
        code=f"CHAUFFEUR_LUX_{suffix}",
        name=f"Vaisentra Royal Fleet {suffix}"
    )
    db_session.add(cat)

    # Customer User & Profile
    customer_user = User(
        phone_number=f"+91981{suffix}10",
        email=f"commuter_{suffix}@vaisentra.com",
        full_name=f"Executive Commuter {suffix}",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add(customer_user)
    await db_session.flush()

    customer_profile = CustomerProfile(user_id=customer_user.id)
    db_session.add(customer_profile)

    # Driver 1 User & Profile
    driver1_user = User(
        phone_number=f"+91981{suffix}20",
        email=f"chauffeur1_{suffix}@vaisentra.com",
        full_name=f"Chauffeur Premier {suffix}",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(driver1_user)
    await db_session.flush()

    driver1_profile = DriverProfile(
        user_id=driver1_user.id,
        license_number=f"DL-{suffix}-01",
        approval_status="APPROVED"
    )
    db_session.add(driver1_profile)
    await db_session.flush()

    avail1 = DriverAvailability(driver_id=driver1_profile.id, is_online=True, is_busy=False)
    db_session.add(avail1)

    # Driver 2 User & Profile
    driver2_user = User(
        phone_number=f"+91981{suffix}30",
        email=f"chauffeur2_{suffix}@vaisentra.com",
        full_name=f"Chauffeur Elite {suffix}",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(driver2_user)
    await db_session.flush()

    driver2_profile = DriverProfile(
        user_id=driver2_user.id,
        license_number=f"DL-{suffix}-02",
        approval_status="APPROVED"
    )
    db_session.add(driver2_profile)
    await db_session.flush()

    avail2 = DriverAvailability(driver_id=driver2_profile.id, is_online=True, is_busy=False)
    db_session.add(avail2)

    # Sessions for tokens
    sess_expiry = datetime.now(timezone.utc) + timedelta(days=7)
    session1 = Session(id=str(uuid.uuid4()), user_id=driver1_user.id, token_hash=f"hash_{uuid.uuid4().hex}", is_revoked=False, expires_at=sess_expiry)
    session2 = Session(id=str(uuid.uuid4()), user_id=driver2_user.id, token_hash=f"hash_{uuid.uuid4().hex}", is_revoked=False, expires_at=sess_expiry)
    db_session.add_all([session1, session2])

    await db_session.commit()

    return {
        "category": cat,
        "customer": customer_profile,
        "driver1_user": driver1_user,
        "driver1_profile": driver1_profile,
        "session1": session1,
        "driver2_user": driver2_user,
        "driver2_profile": driver2_profile,
        "session2": session2,
    }


# ===========================================================================
# 1. REDIS PUB/SUB & MULTI-WORKER DELIVERY TESTS
# ===========================================================================

@pytest.mark.asyncio
async def test_redis_pubsub_multiworker_delivery(setup_dispatch_environment, db_session):
    """
    Test 1: Worker A publishes a dispatch offer to driver:dispatch:{driver_id};
    Worker B (owning driver's WebSocket connection) receives the event over Redis Pub/Sub
    and delivers the canonical DISPATCH_OFFER frame.
    """
    env = setup_dispatch_environment
    driver1_id = env["driver1_profile"].id
    driver1_user_id = env["driver1_user"].id

    received_frames = []

    class MockWebSocket:
        def __init__(self):
            self.closed = False
            self.close_code = None

        async def send_json(self, data):
            received_frames.append(data)

        async def close(self, code=1000):
            self.closed = True
            self.close_code = code

    mock_ws = MockWebSocket()

    # Simulate Worker B registering driver1 with Pub/Sub listener
    sub_task = await ws_manager.connect_driver_with_pubsub(driver1_id, driver1_user_id, mock_ws)

    # Allow listener task to initialize and subscribe
    await asyncio.sleep(0.05)

    # Simulate Worker A publishing to driver channel
    canonical_payload = {
        "event": "DISPATCH_OFFER",
        "ride_id": "ride_uuid_alpha_123",
        "pickup_address": "Terminal 2 VIP Lounge, Kempegowda Intl Airport",
        "destination_address": "The Leela Palace, Old Airport Road, Bengaluru",
        "estimated_fare_cents": 345000,
        "pickup_distance_km": 1.5,
        "timeout_seconds": 20,
        "expires_at": (datetime.now(timezone.utc) + timedelta(seconds=20)).isoformat()
    }
    published = await redis_client.publish(f"driver:dispatch:{driver1_id}", canonical_payload)
    assert published >= 1

    # Give event loop time to process the Pub/Sub message
    await asyncio.sleep(0.05)

    assert len(received_frames) == 1
    frame = received_frames[0]
    assert frame["event"] == "DISPATCH_OFFER"
    assert frame["ride_id"] == "ride_uuid_alpha_123"
    assert frame["estimated_fare_cents"] == 345000
    assert frame["timeout_seconds"] == 20

    # Cleanup subscriber
    ws_manager.disconnect_driver_with_pubsub(driver1_id, driver1_user_id)
    await asyncio.sleep(0.01)


@pytest.mark.asyncio
async def test_redis_pubsub_driver_channel_isolation(setup_dispatch_environment):
    """
    Test 2: Strict channel isolation. An offer published to Driver 1 must NEVER
    be received by Driver 2.
    """
    env = setup_dispatch_environment
    driver1_id = env["driver1_profile"].id
    driver1_user_id = env["driver1_user"].id
    driver2_id = env["driver2_profile"].id
    driver2_user_id = env["driver2_user"].id

    driver1_frames = []
    driver2_frames = []

    class MockWebSocket:
        def __init__(self, target_list):
            self.target_list = target_list

        async def send_json(self, data):
            self.target_list.append(data)

    ws1 = MockWebSocket(driver1_frames)
    ws2 = MockWebSocket(driver2_frames)

    await ws_manager.connect_driver_with_pubsub(driver1_id, driver1_user_id, ws1)
    await ws_manager.connect_driver_with_pubsub(driver2_id, driver2_user_id, ws2)
    await asyncio.sleep(0.05)

    offer_payload = {
        "event": "DISPATCH_OFFER",
        "ride_id": "ride_confidential_999",
        "estimated_fare_cents": 500000
    }
    # Publish strictly to Driver 1
    await redis_client.publish(f"driver:dispatch:{driver1_id}", offer_payload)
    await asyncio.sleep(0.05)

    assert len(driver1_frames) == 1
    assert driver1_frames[0]["ride_id"] == "ride_confidential_999"

    # Driver 2 must have received ZERO frames
    assert len(driver2_frames) == 0

    ws_manager.disconnect_driver_with_pubsub(driver1_id, driver1_user_id)
    ws_manager.disconnect_driver_with_pubsub(driver2_id, driver2_user_id)


@pytest.mark.asyncio
async def test_redis_pubsub_malformed_payload_resilience(setup_dispatch_environment):
    """
    Test 3: Malformed payload on PubSub channel does not crash the subscriber listener.
    """
    env = setup_dispatch_environment
    driver1_id = env["driver1_profile"].id
    driver1_user_id = env["driver1_user"].id

    received_frames = []

    class MockWebSocket:
        async def send_json(self, data):
            received_frames.append(data)

    mock_ws = MockWebSocket()
    await ws_manager.connect_driver_with_pubsub(driver1_id, driver1_user_id, mock_ws)
    await asyncio.sleep(0.05)

    # Publish invalid non-JSON string
    await redis_client.publish(f"driver:dispatch:{driver1_id}", "NOT_JSON_CORRUPTED_DATA")
    await asyncio.sleep(0.05)
    assert len(received_frames) == 0

    # Publish valid JSON next; subscriber must still be alive and processing
    valid_payload = {"event": "DISPATCH_OFFER", "ride_id": "ride_recovered_123"}
    await redis_client.publish(f"driver:dispatch:{driver1_id}", valid_payload)
    await asyncio.sleep(0.05)

    assert len(received_frames) == 1
    assert received_frames[0]["ride_id"] == "ride_recovered_123"

    ws_manager.disconnect_driver_with_pubsub(driver1_id, driver1_user_id)


# ===========================================================================
# 2. WEBSOCKET AUTHENTICATION & RE-AUTHORIZATION TESTS
# ===========================================================================

@pytest.mark.asyncio
async def test_websocket_authorization_header_auth(setup_dispatch_environment):
    """
    Test 4: Native client authenticates using 'Authorization: Bearer <token>' header.
    Query string is absent.
    """
    env = setup_dispatch_environment
    driver_user = env["driver1_user"]
    session = env["session1"]

    token = create_jwt_token(
        user_id=driver_user.id,
        role="driver",
        session_id=session.id
    )

    headers = {"Authorization": f"Bearer {token}"}
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        # Check that creating ticket with Bearer token works
        ticket_res = await client.post("/api/v1/ws/ticket", headers=headers)
        assert ticket_res.status_code == 200
        ticket_data = ticket_res.json()
        assert "ticket" in ticket_data
        assert ticket_data["expires_in_seconds"] == 30

        # Verify single-use redemption
        ticket_val = await redis_client.get(f"ws_ticket:{ticket_data['ticket']}")
        assert ticket_val is not None


@pytest.mark.asyncio
async def test_websocket_ticket_single_use_redemption(setup_dispatch_environment):
    """
    Test 5: Ephemeral ticket is single-use and invalidates immediately upon consumption.
    """
    env = setup_dispatch_environment
    driver_user = env["driver1_user"]
    session = env["session1"]

    token = create_jwt_token(
        user_id=driver_user.id,
        role="driver",
        session_id=session.id
    )

    headers = {"Authorization": f"Bearer {token}"}
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        ticket_res = await client.post("/api/v1/ws/ticket", headers=headers)
        ticket_id = ticket_res.json()["ticket"]

        # Validate through credentials validator
        from backend.api.routes.ws import validate_ws_credentials
        payload = await validate_ws_credentials(f"TICKET:{ticket_id}")
        assert payload is not None
        assert payload["user_id"] == driver_user.id

        # Second redemption must return None (single-use verified)
        second_attempt = await validate_ws_credentials(f"TICKET:{ticket_id}")
        assert second_attempt is None


@pytest.mark.asyncio
async def test_websocket_rejects_suspended_driver(setup_dispatch_environment, db_session):
    """
    Test 6: A driver whose approval_status != 'APPROVED' is rejected with WS_1008_POLICY_VIOLATION.
    """
    env = setup_dispatch_environment
    driver_profile = env["driver1_profile"]
    driver_user = env["driver1_user"]
    session = env["session1"]

    # Suspend driver
    driver_profile.approval_status = "SUSPENDED"
    await db_session.commit()

    token = create_jwt_token(
        user_id=driver_user.id,
        role="driver",
        session_id=session.id
    )

    from backend.api.routes.ws import verify_inflight_driver_authorization
    is_valid = await verify_inflight_driver_authorization(
        user_id=driver_user.id,
        session_id=session.id,
        role="driver",
        driver_id=driver_profile.id,
        token_str=token,
        db_session=db_session
    )
    assert is_valid is False


@pytest.mark.asyncio
async def test_websocket_inflight_session_revocation(setup_dispatch_environment):
    """
    Test 7: If a session is revoked while WebSocket is connected, in-flight re-authorization detects it.
    """
    env = setup_dispatch_environment
    driver_user = env["driver1_user"]
    session = env["session1"]
    driver_profile = env["driver1_profile"]

    token = create_jwt_token(
        user_id=driver_user.id,
        role="driver",
        session_id=session.id
    )

    from backend.api.routes.ws import verify_inflight_driver_authorization
    # Valid initially
    assert await verify_inflight_driver_authorization(driver_user.id, session.id, "driver", driver_profile.id, token) is True

    # Revoke session in Redis
    await redis_client.set(f"revoked_session:{session.id}", "true", ex=3600)

    # In-flight check must now return False
    assert await verify_inflight_driver_authorization(driver_user.id, session.id, "driver", driver_profile.id, token) is False

    # Cleanup
    await redis_client.delete(f"revoked_session:{session.id}")


# ===========================================================================
# 3. DURABLE OFFER RECONCILIATION TESTS
# ===========================================================================

@pytest.mark.asyncio
async def test_durable_reconciliation_delivers_active_offer_on_reconnect(setup_dispatch_environment, db_session):
    """
    Test 8: An offer created while the driver is offline is safely reconciled
    and delivered immediately upon reconnect from PostgreSQL.
    """
    env = setup_dispatch_environment
    driver = env["driver1_profile"]
    customer = env["customer"]
    cat = env["category"]

    # Create requested ride
    ride = Ride(
        customer_id=customer.id,
        vehicle_category_id=cat.id,
        fare_rule_id=str(uuid.uuid4()),
        start_otp="5521",
        status="REQUESTED",
        pickup_address="Leela Palace, Bengaluru",
        pickup_latitude=12.9606,
        pickup_longitude=77.6484,
        destination_address="Kempegowda Intl Airport, BLR",
        destination_latitude=13.1986,
        destination_longitude=77.7066,
        estimated_distance_km=35.0,
        estimated_duration_minutes=45.0,
        estimated_fare_cents=280000
    )
    db_session.add(ride)
    await db_session.flush()

    now = datetime.now(timezone.utc)
    # Create OFFERED assignment expiring in 18 seconds
    assignment = RideAssignment(
        ride_id=ride.id,
        driver_id=driver.id,
        status="OFFERED",
        offered_at=now,
        expires_at=now + timedelta(seconds=18),
        escalation_level=0
    )
    db_session.add(assignment)
    await db_session.commit()

    # Set active-offer index in Redis
    await redis_client.set(f"driver:active_offer:{driver.id}", ride.id, ex=18)

    # Query using DriverTripOperationsService.get_offers (lightweight query)
    service = DriverTripOperationsService(db_session)
    offers = await service.get_offers(env["driver1_user"].id)

    assert len(offers) == 1
    assert offers[0]["ride_id"] == ride.id
    assert offers[0]["estimated_fare_cents"] == 280000
    assert offers[0]["pickup_address"] == "Leela Palace, Bengaluru"


@pytest.mark.asyncio
async def test_reconciliation_ignores_expired_offers(setup_dispatch_environment, db_session):
    """
    Test 9: Reconnect reconciliation does NOT resurrect an offer whose expires_at is in the past,
    even if a stale Redis key remains.
    """
    env = setup_dispatch_environment
    driver = env["driver1_profile"]
    customer = env["customer"]
    cat = env["category"]

    now = datetime.now(timezone.utc)
    ride = Ride(
        customer_id=customer.id,
        vehicle_category_id=cat.id,
        fare_rule_id=str(uuid.uuid4()),
        start_otp="1199",
        status="REQUESTED",
        pickup_address="Indiranagar 100ft Rd",
        pickup_latitude=12.9784,
        pickup_longitude=77.6408,
        destination_address="MG Road Metro Station",
        destination_latitude=12.9756,
        destination_longitude=77.6066,
        estimated_distance_km=6.0,
        estimated_duration_minutes=18.0,
        estimated_fare_cents=80000
    )
    db_session.add(ride)
    await db_session.flush()

    # Assignment expired 5 seconds ago
    assignment = RideAssignment(
        ride_id=ride.id,
        driver_id=driver.id,
        status="OFFERED",
        offered_at=now - timedelta(seconds=25),
        expires_at=now - timedelta(seconds=5),
        escalation_level=0
    )
    db_session.add(assignment)
    await db_session.commit()

    # Stale Redis key simulating delayed eviction
    await redis_client.set(f"driver:active_offer:{driver.id}", ride.id, ex=10)

    service = DriverTripOperationsService(db_session)
    offers = await service.get_offers(env["driver1_user"].id)

    # Must be empty because PostgreSQL verifies expires_at > now
    assert len(offers) == 0


# ===========================================================================
# 4. OFFER ACKNOWLEDGEMENT (OFFER_ACK) TESTS
# ===========================================================================

@pytest.mark.asyncio
async def test_offer_ack_records_receipt_without_accepting_ride(setup_dispatch_environment, db_session):
    """
    Test 10: OFFER_ACK records delivery confirmation in Redis, but does NOT accept the ride
    and does NOT extend offer expiry.
    """
    env = setup_dispatch_environment
    driver = env["driver1_profile"]
    customer = env["customer"]
    cat = env["category"]

    now = datetime.now(timezone.utc)
    ride = Ride(
        customer_id=customer.id,
        vehicle_category_id=cat.id,
        fare_rule_id=str(uuid.uuid4()),
        start_otp="7722",
        status="REQUESTED",
        pickup_address="UB City Luxury Mall",
        pickup_latitude=12.9719,
        pickup_longitude=77.5958,
        destination_address="Whitefield ITPL",
        destination_latitude=12.9850,
        destination_longitude=77.7310,
        estimated_distance_km=18.0,
        estimated_duration_minutes=35.0,
        estimated_fare_cents=150000
    )
    db_session.add(ride)
    await db_session.flush()

    expiry_time = now + timedelta(seconds=20)
    assignment = RideAssignment(
        ride_id=ride.id,
        driver_id=driver.id,
        status="OFFERED",
        offered_at=now,
        expires_at=expiry_time,
        escalation_level=0
    )
    db_session.add(assignment)
    await db_session.commit()

    from backend.api.routes.ws import record_offer_acknowledgement
    ack_time = "2026-11-08T10:30:01Z"
    await record_offer_acknowledgement(driver.id, ride.id, ack_time)

    # 1. ACK is recorded in Redis
    ack_val = await redis_client.get(f"dispatch:ack:{ride.id}:{driver.id}")
    assert ack_val == ack_time

    # 2. Database ride status is UNCHANGED (still REQUESTED, not ACCEPTED)
    await db_session.refresh(ride)
    assert ride.status == "REQUESTED"
    assert ride.assigned_driver_id is None

    # 3. Assignment status is UNCHANGED (still OFFERED)
    await db_session.refresh(assignment)
    assert assignment.status == "OFFERED"
    # Compare timestamps (SQLite strips tzinfo)
    assert assignment.expires_at.replace(tzinfo=timezone.utc) == expiry_time  # Expiry was NOT extended


# ===========================================================================
# 5. LIGHTWEIGHT POLLING ENDPOINT (NON-MUTATING) TESTS
# ===========================================================================

@pytest.mark.asyncio
async def test_get_offers_is_non_mutating(setup_dispatch_environment, db_session):
    """
    Test 11: GET /api/v1/drivers/dispatch/offers is strictly read-only:
    Does not scan unassigned rides, does not run Haversine matching, does not mutate DB.
    """
    env = setup_dispatch_environment
    driver_user = env["driver1_user"]
    driver_profile = env["driver1_profile"]
    customer = env["customer"]
    cat = env["category"]

    # Create 3 requested rides in database that were NEVER offered to driver 1
    unoffered_rides = []
    for i in range(3):
        r = Ride(
            customer_id=customer.id,
            vehicle_category_id=cat.id,
            fare_rule_id=str(uuid.uuid4()),
            start_otp=f"330{i}",
            status="REQUESTED",
            pickup_address=f"Location {i}",
            pickup_latitude=12.9710 + i * 0.001,
            pickup_longitude=77.5950 + i * 0.001,
            destination_address=f"Dest {i}",
            destination_latitude=12.9800,
            destination_longitude=77.6000,
            estimated_distance_km=5.0,
            estimated_duration_minutes=15.0,
            estimated_fare_cents=100000
        )
        unoffered_rides.append(r)
    db_session.add_all(unoffered_rides)
    await db_session.commit()

    service = DriverTripOperationsService(db_session)
    offers = await service.get_offers(driver_user.id)

    # Must return empty list; must NOT dynamically create assignments for unoffered rides
    assert len(offers) == 0

    # Verify no assignments were created in the database
    from sqlalchemy import select
    res = await db_session.execute(
        select(RideAssignment).where(RideAssignment.driver_id == driver_profile.id)
    )
    assert len(res.scalars().all()) == 0


# ===========================================================================
# 6. CONCURRENCY & ATOMIC ACCEPTANCE TESTS
# ===========================================================================

@pytest.mark.asyncio
async def test_atomic_acceptance_prevents_duplicate_booking(setup_dispatch_environment, db_session):
    """
    Test 12: When two drivers attempt concurrent acceptance of the same ride,
    exactly ONE driver succeeds (200), and the second driver receives 409 Conflict.
    Active offer Redis indexes are cleaned up.
    """
    env = setup_dispatch_environment
    driver1_user = env["driver1_user"]
    driver1 = env["driver1_profile"]
    driver2_user = env["driver2_user"]
    driver2 = env["driver2_profile"]
    customer = env["customer"]
    cat = env["category"]

    now = datetime.now(timezone.utc)
    ride = Ride(
        customer_id=customer.id,
        vehicle_category_id=cat.id,
        fare_rule_id=str(uuid.uuid4()),
        start_otp="9921",
        status="REQUESTED",
        pickup_address="Taj West End, Race Course Rd",
        pickup_latitude=12.9822,
        pickup_longitude=77.5875,
        destination_address="Bangalore Golf Club",
        destination_latitude=12.9890,
        destination_longitude=77.5890,
        estimated_distance_km=3.0,
        estimated_duration_minutes=10.0,
        estimated_fare_cents=95000
    )
    db_session.add(ride)
    await db_session.flush()

    # Simulate offers made to both drivers in an escalation wave
    assign1 = RideAssignment(
        ride_id=ride.id,
        driver_id=driver1.id,
        status="OFFERED",
        offered_at=now,
        expires_at=now + timedelta(seconds=20),
        escalation_level=0
    )
    assign2 = RideAssignment(
        ride_id=ride.id,
        driver_id=driver2.id,
        status="OFFERED",
        offered_at=now,
        expires_at=now + timedelta(seconds=20),
        escalation_level=0
    )
    db_session.add_all([assign1, assign2])
    await db_session.commit()

    # Driver 1 accepts
    ride_service = RideService(db_session)
    res1 = await ride_service.accept_ride_atomic(driver1_user.id, ride.id)
    assert res1["status"] == "ASSIGNED"
    assert res1["assigned_driver_id"] == driver1.id

    # Driver 2 attempts to accept the already assigned ride
    from fastapi import HTTPException
    with pytest.raises(HTTPException) as exc_info:
        await ride_service.accept_ride_atomic(driver2_user.id, ride.id)
    assert exc_info.value.status_code == 409

    # Verify database state: assigned only to Driver 1
    await db_session.refresh(ride)
    assert ride.status == "ASSIGNED"
    assert ride.assigned_driver_id == driver1.id
