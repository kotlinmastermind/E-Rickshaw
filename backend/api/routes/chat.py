from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user
from backend.domain.models import User
from backend.application.services.support_chat_service import SupportChatService
from backend.application.services.tracking_advanced_service import TrackingAdvancedService

router = APIRouter(prefix="/rides", tags=["Ride In-App Chat"])

class ChatMessagePayload(BaseModel):
    message_text: str = Field(..., json_schema_extra={"example": "I am waiting at the east foyer gate."})

class TranslateMessagePayload(BaseModel):
    text: str
    target_language: str = "hi"  # hi, mr, en

class MeetingPointPayload(BaseModel):
    point_name: str
    latitude: float
    longitude: float

@router.post("/{ride_id}/chat/messages")
async def send_ride_message(
    ride_id: str,
    payload: ChatMessagePayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    msg = await SupportChatService.send_chat_message(
        session=db,
        ride_id=ride_id,
        sender_user_id=user.id,
        sender_role=user.role,
        message_text=payload.message_text
    )
    await db.commit()
    return {
        "status": "SENT",
        "message_id": msg.id,
        "chat_id": msg.chat_id,
        "sender_user_id": msg.sender_user_id,
        "sender_role": msg.sender_role,
        "message": msg.message,
        "message_text": msg.message,
        "sent_at": msg.sent_at.isoformat()
    }

@router.get("/{ride_id}/chat/messages")
async def get_ride_messages(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    messages = await SupportChatService.get_chat_history(
        session=db,
        ride_id=ride_id,
        current_user_id=user.id
    )
    return [
        {
            "id": m.id,
            "sender_user_id": m.sender_user_id,
            "sender_role": m.sender_role,
            "message": m.message,
            "message_text": m.message,
            "translated_text": m.translated_text,
            "translated_language": m.translated_language,
            "meeting_point_name": m.meeting_point_name,
            "meeting_point_lat": m.meeting_point_lat,
            "meeting_point_lng": m.meeting_point_lng,
            "is_read": m.is_read,
            "sent_at": m.sent_at.isoformat()
        }
        for m in messages
    ]

# Feature 112: Suggested Quick-Reply Messages
@router.get("/{ride_id}/chat/quick-replies")
async def get_quick_replies(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = TrackingAdvancedService(db)
    return service.get_quick_reply_templates(user.role)

# Feature 113: Message Translation
@router.post("/{ride_id}/chat/translate")
async def translate_chat_message(
    ride_id: str,
    payload: TranslateMessagePayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = TrackingAdvancedService(db)
    return await service.translate_message(payload.text, payload.target_language)

# Feature 114: Pickup Meeting-Point Chat
@router.post("/{ride_id}/chat/meeting-point")
async def send_meeting_point(
    ride_id: str,
    payload: MeetingPointPayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = TrackingAdvancedService(db)
    return await service.send_meeting_point_message(
        ride_id=ride_id,
        sender_user_id=user.id,
        sender_role=user.role,
        point_name=payload.point_name,
        lat=payload.latitude,
        lng=payload.longitude
    )
