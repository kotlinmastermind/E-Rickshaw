import json
import asyncio
from typing import Dict, Set, Optional
from datetime import datetime, timezone
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query, status
from backend.infrastructure.security.crypto import decode_jwt_token

router = APIRouter(prefix="/ws", tags=["Real-time WebSockets"])

class ConnectionManager:
    def __init__(self):
        # ride_id -> Set[WebSocket]
        self.ride_channels: Dict[str, Set[WebSocket]] = {}
        # driver_id -> WebSocket
        self.driver_channels: Dict[str, WebSocket] = {}
        # admin WebSockets -> Set[WebSocket]
        self.admin_channels: Set[WebSocket] = set()

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

        # Mirror ride updates to admin mission control telemetry
        await self.broadcast_admin(message)

    async def connect_driver(self, driver_id: str, websocket: WebSocket):
        await websocket.accept()
        self.driver_channels[driver_id] = websocket

    def disconnect_driver(self, driver_id: str):
        if driver_id in self.driver_channels:
            del self.driver_channels[driver_id]

    async def dispatch_to_driver(self, driver_id: str, payload: dict):
        if driver_id in self.driver_channels:
            try:
                await self.driver_channels[driver_id].send_json(payload)
            except Exception:
                self.disconnect_driver(driver_id)

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

@router.websocket("/rides/{ride_id}/tracking")
async def ride_tracking_websocket(
    websocket: WebSocket,
    ride_id: str,
    token: str = Query(...)
):
    from backend.infrastructure.cache.redis_client import redis_client
    try:
        payload = decode_jwt_token(token)
        session_id = payload.get("session_id")
        user_id = payload.get("sub")
        if not session_id or not user_id:
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return
        if await redis_client.exists(f"revoked_session:{session_id}") or await redis_client.exists(f"revoked_user:{user_id}"):
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return
    except Exception:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    await ws_manager.connect_ride(ride_id, websocket)
    try:
        while True:
            # Keepalive / ping or client message
            data = await websocket.receive_text()
            if data == "ping":
                await websocket.send_text("pong")
    except WebSocketDisconnect:
        ws_manager.disconnect_ride(ride_id, websocket)

@router.websocket("/driver/dispatch")
async def driver_dispatch_websocket(
    websocket: WebSocket,
    token: str = Query(...)
):
    from backend.infrastructure.cache.redis_client import redis_client
    try:
        payload = decode_jwt_token(token)
        user_id = payload.get("sub")
        role = payload.get("role")
        session_id = payload.get("session_id")
        if role != "driver" and role != "admin":
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return
        if await redis_client.exists(f"revoked_session:{session_id}") or await redis_client.exists(f"revoked_user:{user_id}"):
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return
    except Exception:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    await ws_manager.connect_driver(user_id, websocket)
    try:
        while True:
            data = await websocket.receive_text()
            if data == "ping":
                await websocket.send_text("pong")
    except WebSocketDisconnect:
        ws_manager.disconnect_driver(user_id)

@router.websocket("/location")
async def location_telemetry_websocket(
    websocket: WebSocket,
    token: str = Query(...)
):
    """
    Real-time high-frequency GPS stream for Drivers and Commuters.
    Updates Redis location cache and broadcasts to active ride and admin channels.
    """
    from backend.infrastructure.cache.redis_client import redis_client
    try:
        payload = decode_jwt_token(token)
        user_id = payload.get("sub")
        role = payload.get("role")
        session_id = payload.get("session_id")
        if not user_id or not session_id:
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return
        if await redis_client.exists(f"revoked_session:{session_id}") or await redis_client.exists(f"revoked_user:{user_id}"):
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return
    except Exception:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    await websocket.accept()
    try:
        while True:
            data = await websocket.receive_text()
            if data == "ping":
                await websocket.send_text("pong")
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
                    # Cache latest telemetry in Redis
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

                    # If ride_id provided, broadcast to ride channel
                    if ride_id:
                        event_type = "DRIVER_LOCATION_UPDATE" if role == "driver" else "CUSTOMER_LOCATION_UPDATE"
                        broadcast_payload = {
                            "event_type": event_type,
                            "ride_id": ride_id,
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

                    # Forward driver positions to admin radar
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

@router.websocket("/admin/telemetry")
async def admin_telemetry_websocket(
    websocket: WebSocket,
    token: str = Query(...)
):
    """
    Live real-time telemetry stream for Admin Real Map and Mission Control.
    Receives all live fleet coordinates and ride lifecycle transitions.
    """
    from backend.infrastructure.cache.redis_client import redis_client
    try:
        payload = decode_jwt_token(token)
        user_id = payload.get("sub")
        role = payload.get("role")
        session_id = payload.get("session_id")
        if role not in ("admin", "superadmin"):
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return
        if await redis_client.exists(f"revoked_session:{session_id}") or await redis_client.exists(f"revoked_user:{user_id}"):
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return
    except Exception:
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
