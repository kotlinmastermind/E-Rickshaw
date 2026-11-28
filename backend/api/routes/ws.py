import json
import asyncio
import logging
import math
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
from backend.domain.models import User, Session, AdminUserRole, AdminRole, AdminRolePermission, AdminPermission, DriverProfile, CustomerProfile, RideAssignment, Ride
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
    token = credentials.credentials
    try:
        payload = decode_jwt_token(token)
        session_id = payload.get("session_id")
        if not session_id:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="An active authenticated session is required.")
        ticket_uuid = str(uuid.uuid4())
        ticket_data = {
            "user_id": user.id,
            "session_id": session_id,
            "role": str(user.role or "").strip().lower(),
            "created_at": datetime.now(timezone.utc).isoformat()
        }
        # Tickets must be shared across workers and atomically expire in Redis.
        # Never fall back to process-local storage: another worker cannot redeem it.
        await redis_client.set_json_strict(f"ws_ticket:{ticket_uuid}", ticket_data, ex=30)
        return WebSocketTicketResponse(ticket=ticket_uuid, expires_in_seconds=30)
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Unable to create WebSocket ticket for user %s: %s", user.id, e, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="WebSocket ticket service is temporarily unavailable."
        )


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
        try:
            # Atomic Redis GET+DEL prevents simultaneous replay and never accepts
            # a process-local fallback ticket on a different worker.
            return await redis_client.consume_json_strict(f"ws_ticket:{ticket_id}")
        except Exception:
            logger.exception("Unable to redeem WebSocket ticket from Redis.")
            return None

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
        # Authorization must fail closed when account/approval state cannot be verified.
        return False


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
                from backend.application.services.commission_service import CommissionService
                commission_estimate = await CommissionService.estimate_for_ride(db, ride)
                remaining_seconds = max(0, int((assignment.expires_at - now).total_seconds()))
                reconciled_offer = {
                    "event": "DISPATCH_OFFER",
                    "ride_id": ride.id,
                    "assignment_id": assignment.id,
                    "pickup_address": ride.pickup_address,
                    "pickup_latitude": ride.pickup_latitude,
                    "pickup_longitude": ride.pickup_longitude,
                    "destination_address": ride.destination_address,
                    "destination_latitude": ride.destination_latitude,
                    "destination_longitude": ride.destination_longitude,
                    "estimated_fare_cents": fare_cents,
                    "estimated_trip_distance_km": ride.estimated_distance_km,
                    "trip_duration_minutes": ride.estimated_duration_minutes,
                    "payment_mode": ride.payment_method,
                    **commission_estimate,
                    "pickup_distance_km": None,
                    "pickup_duration_minutes": None,
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
    authorized_driver_id = None
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

                if is_driver:
                    authorized_driver_id = ride.assigned_driver_id

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

    # Revalidate long-lived subscriptions; the initial handshake is not a
    # permanent authorization grant. A server-side timeout also checks idle sockets.
    reauth_interval = max(5, int(getattr(settings, "RIDE_TRACKING_WS_REAUTH_SECONDS", 30)))
    last_reauth_time = time.monotonic()
    try:
        while True:
            try:
                data = await asyncio.wait_for(
                    websocket.receive_text(),
                    timeout=reauth_interval
                )
            except asyncio.TimeoutError:
                data = None

            now_monotonic = time.monotonic()
            if data == "ping" or now_monotonic - last_reauth_time >= reauth_interval:
                is_authorized = await verify_inflight_driver_authorization(
                    user_id,
                    session_id,
                    role,
                    authorized_driver_id or "",
                    raw_token
                )

                # A driver must remain assigned to this exact ride while subscribed.
                if is_authorized and role == "driver":
                    try:
                        async with async_session_maker() as db:
                            current_ride = await db.get(Ride, ride_id)
                            is_authorized = bool(
                                current_ride
                                and authorized_driver_id
                                and current_ride.assigned_driver_id == authorized_driver_id
                            )
                    except Exception:
                        logger.exception(
                            "Ride tracking re-authorization lookup failed for ride %s.",
                            ride_id
                        )
                        is_authorized = False

                if not is_authorized:
                    logger.warning(
                        "Closing ride tracking WebSocket after authorization changed: "
                        "ride_id=%s user_id=%s role=%s",
                        ride_id,
                        user_id,
                        role
                    )
                    try:
                        await websocket.send_json({
                            "event_type": "SESSION_REVOKED",
                            "ride_id": ride_id,
                            "reason": "RIDE_TRACKING_AUTHORIZATION_CHANGED"
                        })
                    except Exception:
                        pass
                    await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                    break

                last_reauth_time = now_monotonic

            if data == "ping":
                await websocket.send_text("pong")
    except WebSocketDisconnect:
        pass
    finally:
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
    Compatibility GPS stream.

    Driver ride IDs are always resolved from the driver's assigned active ride.
    Customer location is accepted only for a ride owned by that customer.
    Session/account authorization is revalidated while the socket is alive.
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
    role = str(payload.get("role") or "").strip().lower()
    session_id = payload.get("session_id", "")
    if not user_id or not session_id or role not in ("driver", "customer"):
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    async def current_session_is_valid() -> bool:
        if not raw_token.startswith("TICKET:"):
            try:
                token_payload = decode_jwt_token(raw_token)
                exp = token_payload.get("exp")
                if exp and datetime.now(timezone.utc).timestamp() >= float(exp):
                    return False
            except Exception:
                return False

        if (
            await redis_client.exists(f"revoked_session:{session_id}")
            or await redis_client.exists(f"revoked_user:{user_id}")
        ):
            return False

        try:
            async with async_session_maker() as db:
                user = (await db.execute(
                    select(User).where(User.id == user_id, User.is_active.is_(True))
                )).scalar_one_or_none()
                session = (await db.execute(
                    select(Session).where(
                        Session.id == session_id,
                        Session.user_id == user_id,
                        Session.is_revoked.is_(False),
                        Session.expires_at > datetime.now(timezone.utc)
                    )
                )).scalar_one_or_none()
                return bool(
                    user
                    and str(user.role or "").strip().lower() == role
                    and session
                )
        except Exception:
            logger.exception("Location WebSocket session revalidation failed for user %s.", user_id)
            return False

    if not await current_session_is_valid():
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    await websocket.accept()
    last_update_ts = 0.0
    min_update_interval = 0.5
    reauth_interval = max(5, int(getattr(settings, "RIDE_TRACKING_WS_REAUTH_SECONDS", 30)))
    last_reauth_time = time.monotonic()

    try:
        while True:
            try:
                data = await asyncio.wait_for(
                    websocket.receive_text(),
                    timeout=reauth_interval
                )
            except asyncio.TimeoutError:
                data = None

            now_monotonic = time.monotonic()
            if data is None or data == "ping" or now_monotonic - last_reauth_time >= reauth_interval:
                if not await current_session_is_valid():
                    await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                    break
                last_reauth_time = now_monotonic

            if data is None:
                continue
            if data == "ping":
                await websocket.send_text("pong")
                continue

            now_ts = time.time()
            if (now_ts - last_update_ts) < min_update_interval:
                continue

            try:
                msg = json.loads(data)
                if not isinstance(msg, dict):
                    continue
                lat = float(msg.get("latitude"))
                lng = float(msg.get("longitude"))
                heading_raw = msg.get("heading")
                speed_raw = msg.get("speed_mps", msg.get("speed"))
                heading = float(heading_raw) if heading_raw is not None else None
                speed_mps = float(speed_raw) if speed_raw is not None else None
                source = str(msg.get("source", "GPS")).upper()

                if (
                    not math.isfinite(lat) or not math.isfinite(lng)
                    or not -90 <= lat <= 90 or not -180 <= lng <= 180
                    or (heading is not None and (not math.isfinite(heading) or not 0 <= heading <= 360))
                    or (speed_mps is not None and (not math.isfinite(speed_mps) or not 0 <= speed_mps <= 100))
                    or source not in {"GPS", "NETWORK", "PASSIVE", "MANUAL", "GNSS", "FUSED"}
                ):
                    continue

                async with async_session_maker() as db:
                    user = (await db.execute(
                        select(User).where(User.id == user_id, User.is_active.is_(True))
                    )).scalar_one_or_none()
                    session = (await db.execute(
                        select(Session).where(
                            Session.id == session_id,
                            Session.user_id == user_id,
                            Session.is_revoked.is_(False),
                            Session.expires_at > datetime.now(timezone.utc)
                        )
                    )).scalar_one_or_none()
                    if not user or not session or str(user.role or "").strip().lower() != role:
                        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                        break

                    if role == "driver":
                        from backend.domain.models import DriverAvailability
                        driver = (await db.execute(
                            select(DriverProfile).where(DriverProfile.user_id == user_id)
                        )).scalar_one_or_none()
                        if not driver or driver.approval_status != "APPROVED":
                            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                            break

                        availability = (await db.execute(
                            select(DriverAvailability).where(DriverAvailability.driver_id == driver.id)
                        )).scalar_one_or_none()
                        active_ride_ids = (await db.execute(
                            select(Ride.id).where(
                                Ride.assigned_driver_id == driver.id,
                                Ride.status.in_(("ASSIGNED", "ARRIVED", "IN_PROGRESS", "PAUSED"))
                            ).limit(2)
                        )).scalars().all()
                        if len(active_ride_ids) > 1:
                            logger.error("Driver %s has multiple active rides; location broadcast denied.", driver.id)
                            await websocket.close(code=status.WS_1011_INTERNAL_ERROR)
                            break

                        from backend.application.services.live_location_service import LiveLocationService
                        await LiveLocationService(db=db).ingest_driver_location(
                            driver_id=driver.id,
                            latitude=lat,
                            longitude=lng,
                            heading=heading,
                            speed_mps=speed_mps,
                            location_source=source,
                            is_online=bool(availability and availability.is_online),
                            is_busy=bool(availability and availability.is_busy),
                            active_ride_id=active_ride_ids[0] if active_ride_ids else None
                        )
                    else:
                        customer = (await db.execute(
                            select(CustomerProfile).where(CustomerProfile.user_id == user_id)
                        )).scalar_one_or_none()
                        ride_id = str(msg.get("ride_id") or "").strip()
                        if not customer or not ride_id:
                            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                            break

                        customer_ride = (await db.execute(
                            select(Ride).where(
                                Ride.id == ride_id,
                                Ride.customer_id == customer.id,
                                Ride.status.in_(("REQUESTED", "ASSIGNED", "ARRIVED", "IN_PROGRESS", "PAUSED"))
                            )
                        )).scalar_one_or_none()
                        if not customer_ride:
                            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                            break

                        now_iso = datetime.now(timezone.utc).isoformat()
                        location_data = {
                            "user_id": user_id,
                            "latitude": lat,
                            "longitude": lng,
                            "heading": heading,
                            "speed_mps": speed_mps,
                            "source": source,
                            "updated_at": now_iso
                        }
                        await redis_client.set_json(f"customer_loc:{user_id}", location_data, ex=120)
                        await ws_manager.broadcast_ride_update(ride_id, {
                            "event_type": "CUSTOMER_LOCATION_UPDATE",
                            "ride_id": ride_id,
                            "payload": location_data
                        })

                last_update_ts = now_ts
            except WebSocketDisconnect:
                raise
            except Exception:
                logger.exception("Rejected or failed location telemetry update for user %s.", user_id)
                # Invalid telemetry is dropped; authorization and persistence errors fail closed.
                continue
    except WebSocketDisconnect:
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
        logger.warning("Admin telemetry WebSocket rejected: reason=missing_credentials")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    payload = await validate_ws_credentials(raw_token)
    if not payload:
        logger.warning("Admin telemetry WebSocket rejected: reason=invalid_or_expired_ticket")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    user_id = payload.get("user_id") or payload.get("sub")
    role = str(payload.get("role") or "").lower()
    session_id = payload.get("session_id", "")

    if role not in ("admin", "superadmin") or not user_id or not session_id:
        logger.warning("Admin telemetry WebSocket rejected: reason=invalid_role_or_missing_identity_or_session")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    if await redis_client.exists(f"revoked_session:{session_id}") or await redis_client.exists(f"revoked_user:{user_id}"):
        logger.warning("Admin telemetry WebSocket rejected: reason=revoked_session_or_user")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    # A ticket is only a transport credential. Re-check the authoritative account,
    # MFA-verified session, and admin.access assignment before accepting telemetry.
    try:
        async with async_session_maker() as db:
            user = (await db.execute(
                select(User).where(User.id == user_id, User.is_active.is_(True))
            )).scalar_one_or_none()
            session = (await db.execute(
                select(Session).where(
                    Session.id == session_id,
                    Session.user_id == user_id,
                    Session.is_revoked.is_(False),
                    Session.mfa_verified.is_(True),
                    Session.expires_at > datetime.now(timezone.utc)
                )
            )).scalar_one_or_none()
            if not user or str(user.role or "").strip().lower() not in ("admin", "superadmin") or not user.mfa_enabled or not session:
                logger.warning("Admin telemetry WebSocket rejected: reason=inactive_admin_or_mfa_or_session_validation_failed")
                await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                return

            assigned_role_ids = (await db.execute(
                select(AdminUserRole.role_id).where(AdminUserRole.user_id == user_id)
            )).scalars().all()
            if not assigned_role_ids:
                logger.warning("Admin telemetry WebSocket rejected: reason=no_admin_role_assignment")
                await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                return

            admin_roles = (await db.execute(
                select(AdminRole).where(AdminRole.id.in_(assigned_role_ids))
            )).scalars().all()
            has_admin_access = any(role_row.is_superuser for role_row in admin_roles)
            if not has_admin_access:
                permission = (await db.execute(
                    select(AdminPermission.id)
                    .join(AdminRolePermission, AdminRolePermission.permission_id == AdminPermission.id)
                    .where(
                        AdminRolePermission.role_id.in_(assigned_role_ids),
                        AdminPermission.permission_key == "admin.access"
                    )
                    .limit(1)
                )).first()
                has_admin_access = permission is not None
            if not has_admin_access:
                logger.warning("Admin telemetry WebSocket rejected: reason=admin_access_permission_missing")
                await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                return
    except Exception:
        logger.exception("Admin telemetry WebSocket authorization lookup failed.")
        await websocket.close(code=status.WS_1011_INTERNAL_ERROR)
        return

    await ws_manager.connect_admin(websocket)
    try:
        while True:
            data = await websocket.receive_text()
            if data == "ping":
                await websocket.send_text("pong")
    except WebSocketDisconnect:
        ws_manager.disconnect_admin(websocket)
