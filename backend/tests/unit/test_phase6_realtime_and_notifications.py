import pytest
import asyncio
import time
import json
import uuid
from datetime import datetime, timezone, timedelta
from unittest.mock import AsyncMock, patch

from backend.integrations.notifications.provider import (
    build_fcm_v1_payload,
    get_notification_provider,
    send_transactional_email,
)
from backend.infrastructure.cache.redis_client import RedisManager, InMemoryCacheFallback
from backend.api.routes.ws import (
    extract_ws_token,
    validate_ws_credentials,
    ws_manager,
)
from backend.application.services.cascading_dispatch_service import CascadingDispatchService
from backend.application.services.safety_advanced_service import SafetyAdvancedService
from backend.domain.models import User, EmergencyContact, Ride


# ===========================================================================
# Task 066: FCM Push Notification Payload Structure (FCM v1 Specifications)
# ===========================================================================
def test_fcm_v1_payload_conforms_to_specifications():
    token = "fcm_test_token_1234567890_abcdef"
    title = "Ride Confirmed"
    body = "Your electric rickshaw is on the way."
    data = {
        "ride_id": "ride-uuid-999",
        "fare_cents": 15000,
        "is_active": True,
        "meta": {"sub_field": "val"}
    }

    payload = build_fcm_v1_payload(token, title, body, data)

    assert "message" in payload
    msg = payload["message"]
    assert msg["token"] == token
    assert msg["notification"]["title"] == title
    assert msg["notification"]["body"] == body

    # Strict FCM requirement: every entry in data must be serialized as string
    for k, v in msg["data"].items():
        assert isinstance(v, str), f"Data field {k} is not a string!"

    assert msg["data"]["fare_cents"] == "15000"
    assert msg["data"]["is_active"] == "True"
    assert json.loads(msg["data"]["meta"]) == {"sub_field": "val"}

    # Android specific configuration
    assert msg["android"]["priority"] == "HIGH"
    assert msg["android"]["notification"]["channel_id"] == "ride_notifications"


@pytest.mark.asyncio
async def test_fcm_provider_rejects_empty_token():
    provider = get_notification_provider()
    result = await provider.send_push_notification(
        device_token="",
        title="Test",
        body="Empty Token Test"
    )
    assert result is False
    assert provider.last_error_code == "INVALID_TOKEN"


# ===========================================================================
# Task 067: Transactional Email Delivery Service with Fallback Logging
# ===========================================================================
@pytest.mark.asyncio
async def test_transactional_email_delivery_and_validation():
    # 1. Invalid email rejected
    invalid_res = await send_transactional_email("not-an-email", "Subject", "Body")
    assert invalid_res["success"] is False
    assert invalid_res["error_code"] == "INVALID_EMAIL_ADDRESS"

    # 2. Valid email dispatches via fallback logging when SMTP unconfigured
    valid_res = await send_transactional_email(
        to_email="customer@vaisentra.com",
        subject="Your Ride Receipt",
        body_text="Thank you for riding with Vaisentra Mobility."
    )
    assert valid_res["success"] is True
    assert valid_res["recipient"] == "customer@vaisentra.com"
    assert valid_res["provider"] in ("SMTP", "FALLBACK_LOG")


# ===========================================================================
# Task 068: Redis Connection Retry with Exponential Backoff and Jitter
# ===========================================================================
@pytest.mark.asyncio
async def test_redis_connection_retry_and_backoff():
    mgr = RedisManager()
    mgr.redis_url = "redis://invalid-host-for-testing:6379"

    # Initial state
    assert mgr.is_connected is False
    assert mgr._reconnect_backoff == 1.0

    # First connection attempt fails and initiates backoff
    client = await mgr._get_redis()
    assert client is None
    assert mgr.is_connected is False
    assert mgr._reconnect_backoff > 1.0  # Backoff escalated

    # Subsequent immediate call returns None without re-hammering network (backoff active)
    client2 = await mgr._get_redis()
    assert client2 is None


# ===========================================================================
# Task 069: Distributed Lock Timeout and Deadlock Prevention
# ===========================================================================
@pytest.mark.asyncio
async def test_distributed_lock_timeout_and_ownership():
    mgr = RedisManager()
    lock_key = f"test_lock_{uuid.uuid4().hex}"
    token1 = "token_worker_1"
    token2 = "token_worker_2"

    # Worker 1 acquires lock with 1 second timeout
    acquired1 = await mgr.acquire_lock(lock_key, token1, timeout_seconds=1)
    assert acquired1 is True

    # Worker 2 cannot immediately acquire
    acquired2 = await mgr.acquire_lock(lock_key, token2, timeout_seconds=1)
    assert acquired2 is False

    # Worker 2 cannot release Worker 1's lock (token ownership validation)
    wrong_release = await mgr.release_lock(lock_key, token2)
    assert wrong_release is False

    # Wait for lock to expire automatically (deadlock prevention)
    await asyncio.sleep(1.1)

    # Worker 2 can now acquire because Worker 1's lock expired
    acquired3 = await mgr.acquire_lock(lock_key, token2, timeout_seconds=2)
    assert acquired3 is True

    # Worker 2 cleanly releases its own lock
    clean_release = await mgr.release_lock(lock_key, token2)
    assert clean_release is True


# ===========================================================================
# Task 070: WebSocket Authentication & Rejection of Query Tokens
# ===========================================================================
def test_websocket_token_extraction_rules():
    class DummyWS:
        def __init__(self, headers=None, query_params=None):
            self.headers = headers or {}
            self.query_params = query_params or {}

    # 1. Bearer Header accepted
    ws_header = DummyWS(headers={"authorization": "Bearer valid_jwt_token_here"})
    assert extract_ws_token(ws_header) == "valid_jwt_token_here"

    # 2. Ephemeral single-use ticket accepted
    ws_ticket = DummyWS(query_params={"ticket": "ticket-uuid-abc-123"})
    assert extract_ws_token(ws_ticket) == "TICKET:ticket-uuid-abc-123"

    # 3. Active JWT in URL query parameters is explicitly REJECTED
    ws_query_jwt = DummyWS(query_params={"token": "bearer_jwt_in_url"})
    assert extract_ws_token(ws_query_jwt) is None

    # 4. Empty returns None
    ws_empty = DummyWS()
    assert extract_ws_token(ws_empty) is None


# ===========================================================================
# Task 074: Cascading Dispatch Timeout and Cooldown
# ===========================================================================
@pytest.mark.asyncio
async def test_cascading_dispatch_cooldown(db_session):
    driver_id = f"drv_{uuid.uuid4().hex[:8]}"
    ride_id = f"ride_{uuid.uuid4().hex[:8]}"

    # Driver initially not on cooldown
    on_cooldown = await CascadingDispatchService.is_driver_on_cooldown(driver_id, ride_id)
    assert on_cooldown is False

    # Place driver on 2-second cooldown
    await CascadingDispatchService.place_driver_on_cooldown(driver_id, ride_id, ttl_seconds=2)
    assert await CascadingDispatchService.is_driver_on_cooldown(driver_id, ride_id) is True

    # Cooldown expires after TTL
    await asyncio.sleep(2.1)
    assert await CascadingDispatchService.is_driver_on_cooldown(driver_id, ride_id) is False


# ===========================================================================
# Task 075: Emergency SOS Alerting
# ===========================================================================
@pytest.mark.asyncio
async def test_emergency_sos_alerting_workflow(db_session):
    suffix = uuid.uuid4().hex[:6]
    user = User(
        phone_number=f"+91982{suffix}1",
        full_name="Emergency Test User",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add(user)
    await db_session.flush()

    contact = EmergencyContact(
        user_id=user.id,
        name="Guardian Contact",
        relationship="Parent",
        phone_number="+919800011122",
        notify_on_trip_start=True,
        notify_on_arrival=True
    )
    db_session.add(contact)
    await db_session.flush()

    ride = Ride(
        customer_id=user.id,
        vehicle_category_id="cat-std",
        fare_rule_id="rule-std",
        status="IN_PROGRESS",
        pickup_address="City Center",
        pickup_latitude=28.6139,
        pickup_longitude=77.2090,
        destination_address="Outer Suburb",
        destination_latitude=28.7041,
        destination_longitude=77.1025,
        estimated_distance_km=12.0,
        estimated_duration_minutes=25.0,
        estimated_fare_cents=10000,
        start_otp="5521"
    )
    db_session.add(ride)
    await db_session.commit()

    service = SafetyAdvancedService(db_session)
    sos_res = await service.trigger_emergency_sos(
        ride_id=ride.id,
        current_user=user,
        latitude=28.6500,
        longitude=77.1500,
        alert_type="SOS_BUTTON",
        notes="Urgent assistance triggered from customer app"
    )

    assert sos_res["success"] is True
    assert sos_res["status"] == "SOS_ACTIVE"
    assert sos_res["contacts_notified_count"] >= 1
    assert "track" in sos_res["emergency_share_url"]

