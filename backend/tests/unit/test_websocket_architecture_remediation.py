import uuid
from datetime import datetime, timezone, timedelta
import pytest
from starlette.testclient import TestClient
from starlette.websockets import WebSocketDisconnect
from httpx import AsyncClient, ASGITransport

from backend.main import app
from backend.core.config import settings
from backend.domain.models import User, DriverProfile, CustomerProfile, Session, Ride
from backend.infrastructure.security.crypto import create_jwt_token, hash_secret
from backend.infrastructure.database.session import async_session_maker

@pytest.mark.asyncio
async def test_websocket_single_canonical_registration():
    """Verify WebSocket router is mounted exactly once under /api/v1/ws."""
    canonical_ws_routes = set()
    all_ws_routes = []

    for r in app.routes:
        if hasattr(r, "include_context"):
            prefix = r.include_context.prefix
            for sub in r.original_router.routes:
                if "WebSocket" in type(sub).__name__:
                    full_path = f"{prefix}{sub.path}"
                    all_ws_routes.append(full_path)
                    canonical_ws_routes.add(full_path)
        elif "WebSocket" in type(r).__name__:
            all_ws_routes.append(r.path)
            canonical_ws_routes.add(r.path)

    # 1. Exactly 4 WebSocket endpoints must exist
    assert len(all_ws_routes) == 4, f"Found unexpected duplicate WS routes: {all_ws_routes}"
    assert len(canonical_ws_routes) == 4

    # 2. Verify all paths match the canonical contract
    expected = {
        "/api/v1/ws/driver/dispatch",
        "/api/v1/ws/rides/{ride_id}/tracking",
        "/api/v1/ws/location",
        "/api/v1/ws/admin/telemetry"
    }
    assert canonical_ws_routes == expected

    # 3. Ensure NO /ws/ws or /api/v1/ws/ws duplicate prefixes exist
    for path in all_ws_routes:
        assert "/ws/ws" not in path
        assert path.startswith("/api/v1/ws/")

@pytest.mark.asyncio
async def test_ws_authentication_enforcement():
    """Verify all WebSocket endpoints strictly require valid credentials."""
    client = TestClient(app)

    # Missing credentials must trigger 1008 POLICY_VIOLATION
    for endpoint in ["/api/v1/ws/driver/dispatch", "/api/v1/ws/location", "/api/v1/ws/admin/telemetry", "/api/v1/ws/rides/dummy/tracking"]:
        with pytest.raises(WebSocketDisconnect) as exc:
            with client.websocket_connect(endpoint) as ws:
                pass
        assert exc.value.code == 1008

    # Query param ?token= is strictly rejected
    with pytest.raises(WebSocketDisconnect) as exc:
        with client.websocket_connect("/api/v1/ws/driver/dispatch?token=invalid.jwt.token") as ws:
            pass
    assert exc.value.code == 1008

@pytest.mark.asyncio
async def test_ride_tracking_ownership_authorization(db_session):
    """Verify ride tracking websocket allows ONLY the customer, assigned driver, or admin."""
    suffix = uuid.uuid4().hex[:6]
    
    # 1. Create Customer
    cust_uid = str(uuid.uuid4())
    cust_user = User(
        id=cust_uid,
        email=f"cust_{suffix}@test.luxury",
        phone_number=f"+9191{suffix[:8]}",
        full_name="Ride Customer",
        role="customer",
        is_active=True
    )
    cust_profile = CustomerProfile(id=cust_uid, user_id=cust_uid)
    cust_sid = str(uuid.uuid4())
    cust_session = Session(
        id=cust_sid,
        user_id=cust_uid,
        token_hash=hash_secret(str(uuid.uuid4())),
        is_revoked=False,
        expires_at=datetime.now(timezone.utc) + timedelta(days=1)
    )
    
    # 2. Create Assigned Driver
    driver_uid = str(uuid.uuid4())
    driver_user = User(
        id=driver_uid,
        email=f"driver_{suffix}@test.luxury",
        phone_number=f"+9192{suffix[:8]}",
        full_name="Ride Driver",
        role="driver",
        is_active=True
    )
    driver_profile = DriverProfile(
        id=driver_uid,
        user_id=driver_uid,
        license_number=f"DL-WS-{suffix}",
        approval_status="APPROVED"
    )
    driver_sid = str(uuid.uuid4())
    driver_session = Session(
        id=driver_sid,
        user_id=driver_uid,
        token_hash=hash_secret(str(uuid.uuid4())),
        is_revoked=False,
        expires_at=datetime.now(timezone.utc) + timedelta(days=1)
    )

    # 3. Create Unrelated Intruder User
    intruder_uid = str(uuid.uuid4())
    intruder_user = User(
        id=intruder_uid,
        email=f"intruder_{suffix}@test.luxury",
        phone_number=f"+9193{suffix[:8]}",
        full_name="Intruder User",
        role="customer",
        is_active=True
    )
    intruder_profile = CustomerProfile(id=intruder_uid, user_id=intruder_uid)
    intruder_sid = str(uuid.uuid4())
    intruder_session = Session(
        id=intruder_sid,
        user_id=intruder_uid,
        token_hash=hash_secret(str(uuid.uuid4())),
        is_revoked=False,
        expires_at=datetime.now(timezone.utc) + timedelta(days=1)
    )

    # 4. Create Ride
    ride_id = f"RIDE-WS-{suffix}"
    ride = Ride(
        id=ride_id,
        customer_id=cust_uid,
        assigned_driver_id=driver_uid,
        vehicle_category_id="cat-vip",
        fare_rule_id="rule-vip",
        status="IN_PROGRESS",
        pickup_address="AeroCity Gate 1",
        pickup_latitude=28.5562,
        pickup_longitude=77.1000,
        destination_address="CyberHub Tower",
        destination_latitude=28.4900,
        destination_longitude=77.0900,
        estimated_distance_km=8.5,
        estimated_duration_minutes=20,
        estimated_fare_cents=25000,
        start_otp="9999"
    )
    db_session.add_all([
        cust_user, cust_profile, cust_session,
        driver_user, driver_profile, driver_session,
        intruder_user, intruder_profile, intruder_session,
        ride
    ])
    await db_session.commit()

    # Generate tokens
    cust_token = create_jwt_token(user_id=cust_uid, role="customer", session_id=cust_sid, expires_delta=timedelta(hours=1))
    driver_token = create_jwt_token(user_id=driver_uid, role="driver", session_id=driver_sid, expires_delta=timedelta(hours=1))
    intruder_token = create_jwt_token(user_id=intruder_uid, role="customer", session_id=intruder_sid, expires_delta=timedelta(hours=1))

    client = TestClient(app)

    # Intruder MUST be rejected with 1008
    with pytest.raises(WebSocketDisconnect) as exc:
        with client.websocket_connect(
            f"/api/v1/ws/rides/{ride_id}/tracking",
            headers={"Authorization": f"Bearer {intruder_token}"}
        ) as ws:
            pass
    assert exc.value.code == 1008

    # Authorized Customer MUST succeed and immediately receive initial state snapshot
    with client.websocket_connect(
        f"/api/v1/ws/rides/{ride_id}/tracking",
        headers={"Authorization": f"Bearer {cust_token}"}
    ) as ws:
        snapshot = ws.receive_json()
        assert snapshot["event_type"] == "RIDE_STATE_SNAPSHOT"
        assert snapshot["ride_id"] == ride_id
        assert snapshot["status"] == "IN_PROGRESS"
        assert snapshot["pickup_address"] == "AeroCity Gate 1"

        # Ping/Pong test
        ws.send_text("ping")
        assert ws.receive_text() == "pong"

    # Authorized Driver MUST succeed
    with client.websocket_connect(
        f"/api/v1/ws/rides/{ride_id}/tracking",
        headers={"Authorization": f"Bearer {driver_token}"}
    ) as ws:
        snapshot = ws.receive_json()
        assert snapshot["event_type"] == "RIDE_STATE_SNAPSHOT"
        assert snapshot["ride_id"] == ride_id

@pytest.mark.asyncio
async def test_admin_telemetry_role_restriction(db_session):
    """Verify only admin role can access /api/v1/ws/admin/telemetry."""
    suffix = uuid.uuid4().hex[:6]

    # Create admin
    admin_uid = str(uuid.uuid4())
    admin_user = User(
        id=admin_uid,
        email=f"admin_{suffix}@test.luxury",
        phone_number=f"+9194{suffix[:8]}",
        full_name="Admin User",
        role="admin",
        is_active=True
    )
    admin_sid = str(uuid.uuid4())
    admin_session = Session(
        id=admin_sid,
        user_id=admin_uid,
        token_hash=hash_secret(str(uuid.uuid4())),
        is_revoked=False,
        expires_at=datetime.now(timezone.utc) + timedelta(days=1)
    )

    # Create non-admin customer
    customer_uid = str(uuid.uuid4())
    customer_user = User(
        id=customer_uid,
        email=f"regular_{suffix}@test.luxury",
        phone_number=f"+9195{suffix[:8]}",
        full_name="Regular Customer",
        role="customer",
        is_active=True
    )
    customer_sid = str(uuid.uuid4())
    customer_session = Session(
        id=customer_sid,
        user_id=customer_uid,
        token_hash=hash_secret(str(uuid.uuid4())),
        is_revoked=False,
        expires_at=datetime.now(timezone.utc) + timedelta(days=1)
    )

    db_session.add_all([admin_user, admin_session, customer_user, customer_session])
    await db_session.commit()

    admin_token = create_jwt_token(user_id=admin_uid, role="admin", session_id=admin_sid, expires_delta=timedelta(hours=1))
    customer_token = create_jwt_token(user_id=customer_uid, role="customer", session_id=customer_sid, expires_delta=timedelta(hours=1))

    client = TestClient(app)

    # Regular customer rejected from admin telemetry
    with pytest.raises(WebSocketDisconnect) as exc:
        with client.websocket_connect(
            "/api/v1/ws/admin/telemetry",
            headers={"Authorization": f"Bearer {customer_token}"}
        ) as ws:
            pass
    assert exc.value.code == 1008

    # Admin accepted
    with client.websocket_connect(
        "/api/v1/ws/admin/telemetry",
        headers={"Authorization": f"Bearer {admin_token}"}
    ) as ws:
        ws.send_text("ping")
        assert ws.receive_text() == "pong"
