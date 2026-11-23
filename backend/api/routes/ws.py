import json
import asyncio
import logging
import time
import uuid
from typing import Dict, Set, Optional, Any
from datetime import datetime, timezone
from pydantic import BaseModel
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query, Depends, status, HTTPException
from fastapi.security import HTTPAuthorizationCredentials
from sqlalchemy import select, or_

from backend.core.config import settings
from backend.infrastructure.security.crypto import decode_jwt_token
from backend.infrastructure.cache.redis_client import redis_client
from backend.infrastructure.database.session import async_session_maker
from backend.domain.models import User, DriverProfile, CustomerProfile, RideAssignment, Ride
from backend.api.deps import get_current_user, security_scheme

logger = logging.getLogger("backend.ws")

router = APIRouter(prefix="/ws", tags=["Real-time WebSockets"])


# ---------------------------------------------------------------------------
# EPHEMERAL WEBSOCKET TICKET (BROWSER CLIENT SECURITY)
# ---------------------------------------------------------------------------

class WebSocketTicketResponse(BaseModel):
    ticket: str
    expires_in_seconds: int


@router.post("/ticket", response_model=WebSocketTicketResponse)
async def create_websocket_ticket(
    user: User = Depends(get_current_user),
    credentials: HTTPAuthorizationCredentials = Depends(security_scheme)
):
    """
    Issues a short-lived (30s), single-use random ticket for browser-based WebSockets.
    Prevents reusable JWT bearer tokens from appearing in reverse proxy access logs,
    browser history, or APM request URLs.
    """
    try:
        token = credentials.credentials
        try:
            payload = decode_jwt_token(token)
            session_id = payload.get("session_id", "")
        except Exception:
            session_id = ""

        ticket_uuid = str(uuid.uuid4())
        ticket_data = {
            "user_id": user.id,
            "session_id": session_id,
            "role": user.role,
            "created_at": datetime.now(timezone.utc).isoformat()
        }
        await redis_client.set_json(f"ws_ticket:{ticket_uuid}", ticket_data, ex=30)
        return WebSocketTicketResponse(ticket=ticket_uuid, expires_in_seconds=30)
    except Exception as e:
        logger.error(f"Error creating websocket ticket for user {getattr(user, 'id', None)}: {e}", exc_info=True)
        ticket_uuid = str(uuid.uuid4())
        fallback_data = {
            "user_id": getattr(user, "id", None),
            "session_id": "",
            "role": getattr(user, "role", "ADMIN"),
            "created_at": datetime.now(timezone.utc).isoformat()
        }
        try:
            await redis_client._fallback.set(f"ws_ticket:{ticket_uuid}", json.dumps(fallback_data), ex=30)
        except Exception:
            pass
        return WebSocketTicketResponse(ticket=ticket_uuid, expires_in_seconds=30)


def extract_ws_token(websocket: WebSocket) -> Optional[str]:
    """
    Extracts authentication credentials for WebSocket upgrade handshake.
    Strictly permits:
    1. 'Authorization: Bearer <token>' HTTP upgrade header (recommended for native apps).
    2. '?ticket=<uuid>' Single-use ephemeral ticket (recommended for browsers).

    Active JWT tokens in URL query parameters are strictly PROHIBITED to prevent credential leakage
    in proxy access logs, browser history, or APM traces.
    """
    auth_header = websocket.headers.get("authorization")
    if auth_header:
        parts = auth_header.strip().split()
        if len(parts) == 2 and parts[0].lower() == "bearer":
            return parts[1]
        elif len(parts) == 1:
            return parts[0]

    ticket = websocket.query_params.get("ticket")
    if ticket:
        return f"TICKET:{ticket}"

    # Explicitly reject ?token=<jwt> or any other query-based JWT transport
    if websocket.query_params.get("token"):
        logger.warning("Rejected WebSocket handshake: JWT tokens in URL query parameters are prohibited.")
    return None


async def validate_ws_credentials(token_str: str) -> Optional[Dict[str, Any]]:
    """
    Validates token string or single-use ticket.
    Returns decoded token dictionary or None if invalid, revoked, or expired.
    """
    if token_str.startswith("TICKET:"):
        ticket_id = token_str[7:]
        ticket_data = await redis_client.get_json(f"ws_ticket:{ticket_id}")
        if not ticket_data:
            return None
        # Single-use redemption: delete immediately
        await redis_client.delete(f"ws_ticket:{ticket_id}")
        return ticket_data

    try:
        payload = decode_jwt_token(token_str)
        return payload
    except Exception:
        return None


# ---------------------------------------------------------------------------
# CONNECTION & MULTI-WORKER REDIS PUB/SUB MANAGER
# ---------------------------------------------------------------------------

class ConnectionManager:
    """
    WebSocket connection manager backed by Redis Pub/Sub for horizontal multi-worker
    dispatch delivery and local in-memory routing.
    """
    def __init__(self):
        # ride_id -> Set[WebSocket]
        self.ride_channels: Dict[str, Set[WebSocket]] = {}
        # driver_id / user_id -> Set[WebSocket]
        self.driver_channels: Dict[str, Set[WebSocket]] = {}
        # admin WebSockets -> Set[WebSocket]
        self.admin_channels: Set[WebSocket] = set()
        # active subscriber tasks: driver_id -> asyncio.Task
        self._driver_sub_tasks: Dict[str, asyncio.Task] = {}

    async def connect_ride(self, ride_id: str, websocket: WebSocket):
        await websocket.accept()
        if ride_id not in self.ride_channels:
            self.ride_channels[ride_id] = set()
        self.ride_channels[ride_id].add(websocket)

    def disconnect_ride(self, ride_id: str, websocket: WebSocket):
        if ride_id in self.ride_channels:
            self.ride_channels[ride_id].discard(websocket)
            if not self.ride_channels[ride_id]:
                del self.ride_channels[ride_id]

    async def broadcast_ride_update(self, ride_id: str, message: dict):
        if ride_id in self.ride_channels:
            dead_sockets = set()
            for ws in list(self.ride_channels[ride_id]):
                try:
                    await ws.send_json(message)
                except Exception:
                    dead_sockets.add(ws)
            for ws in dead_sockets:
                self.ride_channels[ride_id].discard(ws)

        await self.broadcast_admin(message)

    async def broadcast_to_ride(self, ride_id: str, message: dict):
        """Convenience alias for broadcast_ride_update."""
        return await self.broadcast_ride_update(ride_id, message)

    async def send_personal_message(self, message: dict, recipient_id: str):
        """Dispatches event to driver personal channel."""
        return await self.dispatch_to_driver(recipient_id, message)

    async def connect_driver_with_pubsub(
        self,
        driver_id: str,
        user_id: str,
        websocket: WebSocket
    ) -> asyncio.Task:
        """
        Registers local driver WebSocket and spawns a managed Redis Pub/Sub listener task
        so this worker receives offers published by any other worker process.
        """
        for key in (driver_id, user_id):
            if key not in self.driver_channels:
                self.driver_channels[key] = set()
            self.driver_channels[key].add(websocket)

        # Cancel any stale subscriber task for this driver on this worker
        if driver_id in self._driver_sub_tasks:
            self._driver_sub_tasks[driver_id].cancel()

        sub_task = asyncio.create_task(
            self._driver_pubsub_listener(driver_id, user_id, websocket)
        )
        self._driver_sub_tasks[driver_id] = sub_task
        return sub_task

    async def _driver_pubsub_listener(
        self,
        driver_id: str,
        user_id: str,
        websocket: WebSocket
    ):
        """
        Background listener task bound to a driver's WebSocket. Subscribes to
        driver:dispatch:{driver_id} and driver:dispatch:{user_id}.
        Handles automatic reconnection and backoff on Redis interruptions.
        """
        channels = [f"driver:dispatch:{driver_id}", f"driver:dispatch:{user_id}"]
        backoff = getattr(settings, "DISPATCH_RECONNECT_BACKOFF_BASE_SECONDS", 1.0)
        max_backoff = getattr(settings, "DISPATCH_RECONNECT_BACKOFF_MAX_SECONDS", 30.0)

        while driver_id in self.driver_channels:
            pubsub = None
            try:
                pubsub = await redis_client.get_pubsub()
                await pubsub.subscribe(*channels)
                logger.info(f"Subscribed to Redis dispatch channels: {channels}")
                backoff = getattr(settings, "DISPATCH_RECONNECT_BACKOFF_BASE_SECONDS", 1.0)

                async for message in pubsub.listen():
                    if driver_id not in self.driver_channels:
                        return
                    if message and message.get("type") == "message":
                        raw_data = message.get("data")
                        if not raw_data:
                            continue
                        try:
                            payload = json.loads(raw_data) if isinstance(raw_data, str) else raw_data
                            # Validate payload structure before forwarding
                            if isinstance(payload, dict) and payload.get("event") == "DISPATCH_OFFER":
                                if "ride_id" in payload:
                                    await websocket.send_json(payload)
                                    logger.info(
                                        f"Forwarded PubSub DISPATCH_OFFER for ride {payload.get('ride_id')} "
                                        f"to driver {driver_id}"
                                    )
                        except (json.JSONDecodeError, ValueError) as json_err:
                            logger.warning(f"Malformed dispatch payload on {channels}: {json_err}")
                        except Exception as send_err:
                            logger.debug(f"Failed to forward PubSub message to WebSocket: {send_err}")
                            return
            except asyncio.CancelledError:
                break
            except Exception as e:
                if driver_id not in self.driver_channels:
                    break
                logger.warning(f"Redis PubSub listener error for {driver_id}: {e}. Reconnecting in {backoff}s...")
                try:
                    await asyncio.sleep(backoff)
                except asyncio.CancelledError:
                    break
                backoff = min(backoff * 2, max_backoff)
            finally:
                if pubsub:
                    try:
                        await pubsub.unsubscribe(*channels)
                        await pubsub.close()
                    except Exception:
                        pass

    def disconnect_driver_with_pubsub(self, driver_id: str, user_id: str):
        """Cleans up local driver connection registration and subscriber tasks."""
        for key in (driver_id, user_id):
            if key in self.driver_channels:
                del self.driver_channels[key]

        if driver_id in self._driver_sub_tasks:
            task = self._driver_sub_tasks.pop(driver_id, None)
            if task and not task.done():
                task.cancel()

    async def connect_driver(self, driver_id: str, websocket: WebSocket):
        """Backward-compatible local registration without PubSub."""
        if driver_id not in self.driver_channels:
            self.driver_channels[driver_id] = set()
        self.driver_channels[driver_id].add(websocket)

    def disconnect_driver(self, driver_id: str):
        """Backward-compatible local disconnection."""
        if driver_id in self.driver_channels:
            del self.driver_channels[driver_id]

    async def dispatch_to_driver(self, driver_id: str, payload: dict):
        """Dispatches to local WebSocket instances if present on this worker."""
        if driver_id in self.driver_channels:
            dead_sockets = set()
            for ws in list(self.driver_channels[driver_id]):
                try:
                    await ws.send_json(payload)
                except Exception:
                    dead_sockets.add(ws)
            for ws in dead_sockets:
                self.driver_channels[driver_id].discard(ws)

    async def connect_admin(self, websocket: WebSocket):
        await websocket.accept()
        self.admin_channels.add(websocket)

    def disconnect_admin(self, websocket: WebSocket):
        self.admin_channels.discard(websocket)

    async def broadcast_admin(self, message: dict):
        dead_admin_sockets = set()
        for ws in list(self.admin_channels):
            try:
                await ws.send_json(message)
            except Exception:
                dead_admin_sockets.add(ws)
        for ws in dead_admin_sockets:
            self.admin_channels.discard(ws)


ws_manager = ConnectionManager()


# ---------------------------------------------------------------------------
# IN-FLIGHT RE-AUTHORIZATION HELPER
# ---------------------------------------------------------------------------

async def verify_inflight_driver_authorization(
    user_id: str,
    session_id: str,
    role: str,
    driver_id: str,
    token_str: str,
    db_session: Optional[Any] = None
) -> bool:
    """
    Validates driver session eligibility, token expiry, revocation blacklist,
    and database account approval during active WebSocket connections.
    """
    # 1. Token expiration check (for JWT tokens)
    if not token_str.startswith("TICKET:"):
        try:
            payload = decode_jwt_token(token_str)
            exp = payload.get("exp")
            if exp and datetime.now(timezone.utc).timestamp() > exp:
                logger.info(f"In-flight re-authorization failed: Token expired for user {user_id}")
                return False
        except Exception:
            return False

    # 2. Redis Session Revocation Blacklist Check
    if await redis_client.exists(f"revoked_session:{session_id}") or await redis_client.exists(f"revoked_user:{user_id}"):
        logger.info(f"In-flight re-authorization failed: Revocation key found for user {user_id}")
        return False

    # 3. Database Driver Approval & Active State Check
    async def _check_db(db):
        user_stmt = select(User.is_active).where(User.id == user_id)
        user_res = await db.execute(user_stmt)
        is_active = user_res.scalar_one_or_none()
        if not is_active:
            logger.info(f"In-flight re-authorization failed: User {user_id} is inactive")
            return False

        if role == "driver":
            driver_stmt = select(DriverProfile.approval_status).where(
                or_(DriverProfile.id == driver_id, DriverProfile.user_id == user_id)
            )
            driver_res = await db.execute(driver_stmt)
            approval_status = driver_res.scalar_one_or_none()
            if approval_status != "APPROVED":
                logger.info(
                    f"In-flight re-authorization failed: Driver {driver_id} approval status is {approval_status}"
                )
                return False
        return True

    try:
        if db_session is not None:
            return await _check_db(db_session)
        else:
            async with async_session_maker() as db:
                return await _check_db(db)
    except Exception as db_err:
        logger.warning(f"Database error during in-flight re-authorization check: {db_err}")
        return True


async def record_offer_acknowledgement(driver_id: str, ride_id: str, received_at: Optional[str] = None):
    """
    Records driver delivery acknowledgement in Redis and structured logs.
    Does NOT change Ride or RideAssignment acceptance status and does NOT extend expiry.
    """
    ack_time = received_at or datetime.now(timezone.utc).isoformat()
    ack_key = f"dispatch:ack:{ride_id}:{driver_id}"
    await redis_client.set(ack_key, ack_time, ex=300)
    logger.info(f"OFFER_ACK recorded: ride_id={ride_id}, driver_id={driver_id}, ack_time={ack_time}")


# ---------------------------------------------------------------------------
# WEBSOCKET ROUTE: /driver/dispatch
# ---------------------------------------------------------------------------

@router.websocket("/driver/dispatch")
async def driver_dispatch_websocket(
    websocket: WebSocket,
    ticket: Optional[str] = Query(None)
):
    """
    Dedicated authenticated dispatch stream for Chauffeurs.
    Features:
    - Secure Authorization: Bearer <token> header authentication.
    - Single-use ephemeral ?ticket= support for browsers/consoles.
    - Active JWT in query parameters is strictly rejected.
    - Multi-worker Redis Pub/Sub delivery.
    - Durable reconnect offer reconciliation.
    - In-flight session, token, and driver approval re-authorization.
    - Delivery acknowledgement (OFFER_ACK) handling.
    """
    raw_token = extract_ws_token(websocket)
    if not raw_token:
        logger.warning("Rejected /ws/driver/dispatch handshake: Missing authentication credentials.")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    payload = await validate_ws_credentials(raw_token)
    if not payload:
        logger.warning("Rejected /ws/driver/dispatch handshake: Invalid or expired credentials.")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    user_id = payload.get("user_id") or payload.get("sub")
    role = str(payload.get("role") or "").lower()
    session_id = payload.get("session_id", "")

    if role not in ("driver", "admin") or not user_id:
        logger.warning(f"Rejected /ws/driver/dispatch handshake: Unauthorized role '{role}'.")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    # Initial session revocation check
    if session_id and (await redis_client.exists(f"revoked_session:{session_id}") or await redis_client.exists(f"revoked_user:{user_id}")):
        logger.warning(f"Rejected /ws/driver/dispatch handshake: Session {session_id} or user {user_id} revoked.")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    # Verify driver identity and approval in database
    driver_id = user_id
    try:
        async with async_session_maker() as db:
            user_stmt = select(User.is_active).where(User.id == user_id)
            user_active = (await db.execute(user_stmt)).scalar_one_or_none()
            if not user_active:
                logger.warning(f"Rejected /ws/driver/dispatch handshake: Inactive user {user_id}.")
                await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                return

            if role == "driver":
                driver_stmt = select(DriverProfile).where(
                    or_(DriverProfile.id == user_id, DriverProfile.user_id == user_id)
                )
                driver_profile = (await db.execute(driver_stmt)).scalar_one_or_none()
                if not driver_profile or driver_profile.approval_status != "APPROVED":
                    logger.warning(
                        f"Rejected /ws/driver/dispatch handshake: Driver {user_id} not approved "
                        f"(status: {driver_profile.approval_status if driver_profile else 'None'})."
                    )
                    await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                    return
                driver_id = driver_profile.id
    except Exception as e:
        logger.error(f"Error querying driver profile during handshake: {e}", exc_info=True)
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    # Accept connection and register Redis Pub/Sub subscription
    await websocket.accept()
    await ws_manager.connect_driver_with_pubsub(driver_id, user_id, websocket)
    logger.info(f"Driver {driver_id} (user {user_id}) connected to /ws/driver/dispatch with Pub/Sub.")

    # -----------------------------------------------------------------------
    # DURABLE RECONNECT RECONCILIATION
    # -----------------------------------------------------------------------
    try:
        now = datetime.now(timezone.utc)
        async with async_session_maker() as db:
            recon_stmt = (
                select(RideAssignment, Ride)
                .join(Ride, Ride.id == RideAssignment.ride_id)
                .where(
                    RideAssignment.driver_id == driver_id,
                    RideAssignment.status == "OFFERED",
                    RideAssignment.expires_at > now,
                    Ride.status == "REQUESTED",
                    Ride.assigned_driver_id.is_(None)
                )
                .order_by(RideAssignment.offered_at.desc())
                .limit(1)
            )
            recon_res = await db.execute(recon_stmt)
            recon_row = recon_res.first()
            if recon_row:
                assignment, ride = recon_row
                fare_cents = ride.final_fare_cents if ride.final_fare_cents is not None else ride.estimated_fare_cents
                remaining_seconds = max(1, int((assignment.expires_at - now).total_seconds()))
                reconciled_offer = {
                    "event": "DISPATCH_OFFER",
                    "ride_id": ride.id,
                    "pickup_address": ride.pickup_address,
                    "destination_address": ride.destination_address,
                    "estimated_fare_cents": fare_cents,
                    "pickup_distance_km": None,
                    "timeout_seconds": remaining_seconds,
                    "expires_at": assignment.expires_at.isoformat() if assignment.expires_at else None
                }
                await websocket.send_json(reconciled_offer)
                logger.info(
                    f"Reconciled and delivered live offer for ride {ride.id} to reconnecting driver {driver_id} "
                    f"({remaining_seconds}s remaining)."
                )
    except Exception as recon_err:
        logger.warning(f"Durable reconciliation check failed for driver {driver_id}: {recon_err}")

    # -----------------------------------------------------------------------
    # MESSAGE LOOP & IN-FLIGHT RE-AUTHORIZATION
    # -----------------------------------------------------------------------
    heartbeat_interval = getattr(settings, "DISPATCH_WS_HEARTBEAT_SECONDS", 30)
    heartbeat_timeout = heartbeat_interval + 15
    last_reauth_time = time.time()

    try:
        while True:
            try:
                raw_data = await asyncio.wait_for(websocket.receive_text(), timeout=heartbeat_timeout)
            except asyncio.TimeoutError:
                # Trigger server-side heartbeat re-authorization check
                raw_data = "ping"

            now_ts = time.time()
            needs_reauth = (now_ts - last_reauth_time) >= heartbeat_interval or raw_data == "ping"

            if needs_reauth:
                is_authorized = await verify_inflight_driver_authorization(
                    user_id, session_id, role, driver_id, raw_token
                )
                if not is_authorized:
                    logger.warning(f"Terminating WebSocket for driver {driver_id}: Session revoked or unapproved.")
                    try:
                        await websocket.send_json({
                            "event": "SESSION_REVOKED",
                            "reason": "ACCOUNT_SUSPENDED_OR_TOKEN_REVOKED",
                            "code": 4003
                        })
                    except Exception:
                        pass
                    await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                    break
                last_reauth_time = now_ts

            if raw_data == "ping":
                await websocket.send_text("pong")
                continue

            try:
                msg_data = json.loads(raw_data)
            except (json.JSONDecodeError, ValueError):
                continue

            msg_type = msg_data.get("type") or msg_data.get("event")
            if msg_type == "PING":
                await websocket.send_json({
                    "type": "PONG",
                    "session_valid": True,
                    "server_time": datetime.now(timezone.utc).isoformat()
                })
            elif msg_type == "OFFER_ACK":
                ride_id = msg_data.get("ride_id")
                if ride_id:
                    await record_offer_acknowledgement(driver_id, ride_id, msg_data.get("received_at"))
    except WebSocketDisconnect:
        logger.info(f"Driver {driver_id} disconnected from /ws/driver/dispatch.")
    finally:
        ws_manager.disconnect_driver_with_pubsub(driver_id, user_id)
        # Task 073: Clean up active state and notify admin on driver disconnection
        try:
            await redis_client.delete(f"active_driver:{driver_id}")
            await redis_client.delete(f"driver_loc:{user_id}")
            await ws_manager.broadcast_admin({
                "event_type": "DRIVER_OFFLINE",
                "payload": {
                    "id": user_id,
                    "driver_id": driver_id,
                    "status": "OFFLINE",
                    "disconnected_at": datetime.now(timezone.utc).isoformat()
                }
            })
        except Exception as clean_err:
            logger.debug(f"Driver offline cleanup error: {clean_err}")


# ---------------------------------------------------------------------------
# WEBSOCKET ROUTE: /rides/{ride_id}/tracking
# ---------------------------------------------------------------------------

@router.websocket("/rides/{ride_id}/tracking")
async def ride_tracking_websocket(
    websocket: WebSocket,
    ride_id: str,
    ticket: Optional[str] = Query(None)
):
    raw_token = extract_ws_token(websocket)
    if not raw_token:
        logger.warning(f"Rejected /rides/{ride_id}/tracking: Missing authentication credentials.")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    payload = await validate_ws_credentials(raw_token)
    if not payload:
        logger.warning(f"Rejected /rides/{ride_id}/tracking: Invalid or expired credentials.")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    user_id = payload.get("user_id") or payload.get("sub")
    role = str(payload.get("role") or "").lower()
    session_id = payload.get("session_id", "")
    if not user_id or not session_id:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    if await redis_client.exists(f"revoked_session:{session_id}") or await redis_client.exists(f"revoked_user:{user_id}"):
        logger.warning(f"Rejected /rides/{ride_id}/tracking: Revoked session for user {user_id}.")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    # Ride ownership & authorization validation
    if role not in ("admin", "superadmin"):
        try:
            async with async_session_maker() as db:
                ride = await db.get(Ride, ride_id)
                if not ride:
                    logger.warning(f"Rejected /rides/{ride_id}/tracking: Ride {ride_id} does not exist.")
                    await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                    return

                # Get customer profile IDs for user
                cust_ids = (await db.execute(
                    select(CustomerProfile.id).where(
                        or_(CustomerProfile.user_id == user_id, CustomerProfile.id == user_id)
                    )
                )).scalars().all()

                # Get driver profile IDs for user
                driver_ids = (await db.execute(
                    select(DriverProfile.id).where(
                        or_(DriverProfile.user_id == user_id, DriverProfile.id == user_id)
                    )
                )).scalars().all()

                is_customer = ride.customer_id in cust_ids or ride.customer_id == user_id
                is_driver = bool(ride.assigned_driver_id and (ride.assigned_driver_id in driver_ids or ride.assigned_driver_id == user_id))

                if not (is_customer or is_driver):
                    logger.warning(
                        f"Rejected /rides/{ride_id}/tracking: User {user_id} (role: {role}) "
                        f"is neither customer nor assigned driver of ride {ride_id}."
                    )
                    await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                    return
        except Exception as e:
            logger.error(f"Error checking ride authorization in /rides/{ride_id}/tracking: {e}", exc_info=True)
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return

    await ws_manager.connect_ride(ride_id, websocket)

    # Immediately emit authoritative state snapshot upon connection for seamless state recovery
    try:
        async with async_session_maker() as db:
            current_ride = await db.get(Ride, ride_id)
            if current_ride:
                snapshot = {
                    "event_type": "RIDE_STATE_SNAPSHOT",
                    "ride_id": current_ride.id,
                    "status": current_ride.status,
                    "pickup_address": current_ride.pickup_address,
                    "destination_address": current_ride.destination_address,
                    "estimated_fare_cents": current_ride.estimated_fare_cents,
                    "final_fare_cents": current_ride.final_fare_cents
                }
                if current_ride.assigned_driver_id:
                    d_loc = await redis_client.get_json(f"driver:location:{current_ride.assigned_driver_id}")
                    if d_loc:
                        snapshot["driver_latitude"] = d_loc.get("latitude")
                        snapshot["driver_longitude"] = d_loc.get("longitude")
                        snapshot["driver_heading"] = d_loc.get("heading")
                await websocket.send_json(snapshot)
    except Exception as snap_err:
        logger.debug(f"Could not send initial snapshot for ride {ride_id}: {snap_err}")

    try:
        while True:
            data = await websocket.receive_text()
            if data == "ping":
                await websocket.send_text("pong")
    except WebSocketDisconnect:
        ws_manager.disconnect_ride(ride_id, websocket)


# ---------------------------------------------------------------------------
# WEBSOCKET ROUTE: /location
# ---------------------------------------------------------------------------

@router.websocket("/location")
async def location_telemetry_websocket(
    websocket: WebSocket,
    ticket: Optional[str] = Query(None)
):
    """
    Real-time high-frequency GPS stream for Drivers and Commuters.
    Requires Authorization: Bearer <token> header or ?ticket=<uuid>.
    """
    raw_token = extract_ws_token(websocket)
    if not raw_token:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    payload = await validate_ws_credentials(raw_token)
    if not payload:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    user_id = payload.get("user_id") or payload.get("sub")
    role = str(payload.get("role") or "").lower()
    session_id = payload.get("session_id", "")
    if not user_id or not session_id:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    if await redis_client.exists(f"revoked_session:{session_id}") or await redis_client.exists(f"revoked_user:{user_id}"):
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    await websocket.accept()
    last_update_ts = 0.0
    MIN_UPDATE_INTERVAL = 0.5  # 500ms throttling to avoid event bus saturation

    try:
        while True:
            data = await websocket.receive_text()
            if data == "ping":
                await websocket.send_text("pong")
                continue

            # Task 072: Throttle location updates to at most once per 500ms
            now_ts = time.time()
            if (now_ts - last_update_ts) < MIN_UPDATE_INTERVAL:
                continue

            try:
                msg = json.loads(data)
                lat = float(msg.get("latitude", 0.0))
                lng = float(msg.get("longitude", 0.0))
                heading = float(msg.get("heading") or 0.0)
                speed = float(msg.get("speed_mps") or msg.get("speed") or 0.0)
                source = str(msg.get("source", "GPS"))
                ride_id = msg.get("ride_id")

                now_iso = datetime.now(timezone.utc).isoformat()

                if -90 <= lat <= 90 and -180 <= lng <= 180:
                    last_update_ts = now_ts
                    cache_key = f"driver_loc:{user_id}" if role == "driver" else f"customer_loc:{user_id}"
                    loc_data = {
                        "latitude": lat,
                        "longitude": lng,
                        "heading": heading,
                        "speed_mps": speed,
                        "source": source,
                        "updated_at": now_iso
                    }
                    try:
                        await redis_client.set_json(cache_key, loc_data, ex=120)
                    except Exception:
                        pass

                    if ride_id:
                        event_type = "DRIVER_LOCATION_UPDATE" if role == "driver" else "CUSTOMER_LOCATION_UPDATE"
                        broadcast_payload = {
                            "event_type": event_type,
                            "ride_id": ride_id,
                            "driver_latitude": lat if role == "driver" else None,
                            "driver_longitude": lng if role == "driver" else None,
                            "payload": {
                                "user_id": user_id,
                                "latitude": lat,
                                "longitude": lng,
                                "heading": heading,
                                "speed_mps": speed,
                                "source": source,
                                "updated_at": now_iso
                            }
                        }
                        await ws_manager.broadcast_ride_update(ride_id, broadcast_payload)

                    if role == "driver":
                        admin_payload = {
                            "event_type": "DRIVER_LOCATION_UPDATE",
                            "payload": {
                                "id": user_id,
                                "driver_id": user_id,
                                "latitude": lat,
                                "longitude": lng,
                                "heading": heading,
                                "speed": speed,
                                "last_seen": now_iso
                            }
                        }
                        await ws_manager.broadcast_admin(admin_payload)
            except Exception:
                pass
    except WebSocketDisconnect:
        pass
    finally:
        # Task 073: Clean up driver location cache on disconnect
        if role == "driver":
            try:
                await redis_client.delete(f"driver_loc:{user_id}")
                await ws_manager.broadcast_admin({
                    "event_type": "DRIVER_OFFLINE",
                    "payload": {
                        "id": user_id,
                        "driver_id": user_id,
                        "status": "OFFLINE",
                        "disconnected_at": datetime.now(timezone.utc).isoformat()
                    }
                })
            except Exception:
                pass


# ---------------------------------------------------------------------------
# WEBSOCKET ROUTE: /admin/telemetry
# ---------------------------------------------------------------------------

@router.websocket("/admin/telemetry")
async def admin_telemetry_websocket(
    websocket: WebSocket,
    ticket: Optional[str] = Query(None)
):
    raw_token = extract_ws_token(websocket)
    if not raw_token:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    payload = await validate_ws_credentials(raw_token)
    if not payload:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    user_id = payload.get("user_id") or payload.get("sub")
    role = str(payload.get("role") or "").lower()
    session_id = payload.get("session_id", "")

    if role not in ("admin", "superadmin") or not user_id:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    if session_id and (await redis_client.exists(f"revoked_session:{session_id}") or await redis_client.exists(f"revoked_user:{user_id}")):
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    await ws_manager.connect_admin(websocket)
    try:
        while True:
            data = await websocket.receive_text()
            if data == "ping":
                await websocket.send_text("pong")
    except WebSocketDisconnect:
        ws_manager.disconnect_admin(websocket)
