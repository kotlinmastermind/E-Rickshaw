from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user
from backend.domain.models import User
from backend.application.services.support_chat_service import SupportChatService

router = APIRouter(prefix="/rides", tags=["Ride In-App Chat"])

class ChatMessagePayload(BaseModel):
    message_text: str = Field(..., json_schema_extra={"example": "I am waiting at the east foyer gate."})

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
            "is_read": m.is_read,
            "sent_at": m.sent_at.isoformat()
        }
        for m in messages
    ]
