from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user, get_current_admin
from backend.domain.models import User, SupportTicket
from backend.application.services.support_chat_service import SupportChatService

router = APIRouter(prefix="/support", tags=["Support Desk"])

class TicketCreateRequest(BaseModel):
    subject: str = Field(..., json_schema_extra={"example": "Luggage assistance query"})
    category: str = Field("RIDE_INQUIRY", json_schema_extra={"example": "RIDE_INQUIRY"})
    description: str = Field(..., json_schema_extra={"example": "I left an umbrella in the Mercedes S680."})
    ride_id: Optional[str] = None
    priority: str = "NORMAL"

class MessageCreateRequest(BaseModel):
    message_text: str = Field(..., json_schema_extra={"example": "Thank you, our concierge is reviewing this."})
    is_internal_only: bool = False
    attachment_path: Optional[str] = None

class TicketStatusUpdate(BaseModel):
    status: str = Field(..., json_schema_extra={"example": "RESOLVED"})
    assigned_to: Optional[str] = None

@router.post("/tickets")
async def create_ticket(
    payload: TicketCreateRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    ticket = await SupportChatService.create_ticket(
        session=db,
        user_id=user.id,
        subject=payload.subject,
        category=payload.category,
        description=payload.description,
        ride_id=payload.ride_id,
        priority=payload.priority
    )
    await db.commit()
    return {
        "status": "CREATED",
        "ticket_id": ticket.id,
        "subject": ticket.subject,
        "ticket_status": ticket.status,
        "created_at": ticket.created_at.isoformat()
    }

@router.get("/tickets")
async def list_my_tickets(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    tickets = await SupportChatService.list_user_tickets(db, user.id)
    return [
        {
            "id": t.id,
            "subject": t.subject,
            "category": t.category,
            "status": t.status,
            "priority": t.priority,
            "ride_id": t.ride_id,
            "created_at": t.created_at.isoformat(),
            "updated_at": t.updated_at.isoformat()
        }
        for t in tickets
    ]

@router.get("/tickets/{ticket_id}")
async def get_ticket_details(
    ticket_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    is_staff = user.role == "admin"
    return await SupportChatService.get_ticket_with_messages(
        session=db,
        ticket_id=ticket_id,
        current_user_id=user.id,
        is_staff=is_staff
    )

@router.post("/tickets/{ticket_id}/messages")
async def reply_to_ticket(
    ticket_id: str,
    payload: MessageCreateRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    is_staff = user.role == "admin"
    msg = await SupportChatService.post_ticket_message(
        session=db,
        ticket_id=ticket_id,
        sender_user_id=user.id,
        message_text=payload.message_text,
        is_internal_only=payload.is_internal_only,
        attachment_path=payload.attachment_path,
        is_staff=is_staff
    )
    await db.commit()
    return {
        "status": "SENT",
        "message_id": msg.id,
        "created_at": msg.created_at.isoformat()
    }
