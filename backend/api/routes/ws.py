import json
import asyncio
from typing import Dict, Set
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query, status
from backend.infrastructure.security.crypto import decode_jwt_token

router = APIRouter(prefix="/ws", tags=["Real-time WebSockets"])

class ConnectionManager:
    def __init__(self):
        # ride_id -> Set[WebSocket]
        self.ride_channels: Dict[str, Set[WebSocket]] = {}
        # driver_id -> WebSocket
        self.driver_channels: Dict[str, WebSocket] = {}

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
            for ws in self.ride_channels[ride_id]:
                try:
                    await ws.send_json(message)
                except Exception:
                    dead_sockets.add(ws)
            for ws in dead_sockets:
                self.ride_channels[ride_id].discard(ws)

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
            # Keepalive / ping
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
