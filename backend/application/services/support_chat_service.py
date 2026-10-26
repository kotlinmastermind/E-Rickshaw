"""
Support & In-App Chat Service
Handles customer support ticket lifecycles, internal staff notes, and ride-scoped real-time messaging.
"""
import uuid
from typing import Optional, List, Dict, Any
from datetime import datetime, timezone
from sqlalchemy import select, and_, or_
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException, status

from backend.domain.models import (
    SupportTicket, SupportMessage, RideChat, RideChatMessage, Ride, User
)

class SupportChatService:
    # ---------------- SUPPORT TICKETS ----------------
    @staticmethod
    async def create_ticket(
        session: AsyncSession,
        user_id: str,
        subject: str,
        category: str,
        description: str,
        ride_id: Optional[str] = None,
        priority: str = "MEDIUM"
    ) -> SupportTicket:
        if ride_id:
            ride = await session.get(Ride, ride_id)
            if not ride:
                raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Associated ride not found")

        ticket_number = f"TCK-{datetime.now(timezone.utc).strftime('%Y%m%d')}-{uuid.uuid4().hex[:6].upper()}"

        ticket = SupportTicket(
            ticket_number=ticket_number,
            user_id=user_id,
            ride_id=ride_id,
            category=category,
            subject=subject,
            priority=priority,
            status="OPEN"
        )
        session.add(ticket)
        await session.flush()

        initial_msg = SupportMessage(
            ticket_id=ticket.id,
            sender_user_id=user_id,
            sender_role="CUSTOMER",
            message=description,
            is_internal_only=False
        )
        session.add(initial_msg)
        await session.flush()
        return ticket

    @staticmethod
    async def list_user_tickets(session: AsyncSession, user_id: str) -> List[SupportTicket]:
        stmt = (
            select(SupportTicket)
            .where(SupportTicket.user_id == user_id)
            .order_by(SupportTicket.created_at.desc())
        )
        res = await session.execute(stmt)
        return list(res.scalars().all())

    @staticmethod
    async def get_ticket_with_messages(
        session: AsyncSession,
        ticket_id: str,
        current_user_id: str,
        is_staff: bool = False
    ) -> Dict[str, Any]:
        ticket = await session.get(SupportTicket, ticket_id)
        if not ticket:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Support ticket not found")

        if not is_staff and ticket.user_id != current_user_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied to this support ticket")

        msg_stmt = (
            select(SupportMessage)
            .where(SupportMessage.ticket_id == ticket_id)
            .order_by(SupportMessage.created_at.asc())
        )
        if not is_staff:
            msg_stmt = msg_stmt.where(SupportMessage.is_internal_only == False)

        msg_res = await session.execute(msg_stmt)
        messages = msg_res.scalars().all()

        return {
            "id": ticket.id,
            "ticket_number": ticket.ticket_number,
            "user_id": ticket.user_id,
            "ride_id": ticket.ride_id,
            "category": ticket.category,
            "subject": ticket.subject,
            "status": ticket.status,
            "priority": ticket.priority,
            "assigned_admin_id": ticket.assigned_admin_id,
            "internal_notes": ticket.internal_notes if is_staff else None,
            "created_at": ticket.created_at.isoformat(),
            "updated_at": ticket.updated_at.isoformat() if ticket.updated_at else ticket.created_at.isoformat(),
            "messages": [
                {
                    "id": m.id,
                    "sender_user_id": m.sender_user_id,
                    "message": m.message,
                    "message_text": m.message,
                    "attachment_path": m.attachment_path,
                    "is_internal_only": m.is_internal_only,
                    "created_at": m.created_at.isoformat()
                }
                for m in messages
            ]
        }

    @staticmethod
    async def post_ticket_message(
        session: AsyncSession,
        ticket_id: str,
        sender_user_id: str,
        message_text: str,
        is_internal_only: bool = False,
        attachment_path: Optional[str] = None,
        is_staff: bool = False
    ) -> SupportMessage:
        ticket = await session.get(SupportTicket, ticket_id)
        if not ticket:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Support ticket not found")

        if not is_staff and ticket.user_id != sender_user_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied")

        if is_internal_only and not is_staff:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only staff can create internal notes")

        msg = SupportMessage(
            ticket_id=ticket.id,
            sender_user_id=sender_user_id,
            sender_role="ADMIN" if is_staff else "CUSTOMER",
            message=message_text,
            is_internal_only=is_internal_only,
            attachment_path=attachment_path
        )
        session.add(msg)
        ticket.updated_at = datetime.now(timezone.utc)
        await session.flush()
        return msg

    @staticmethod
    async def admin_update_ticket(
        session: AsyncSession,
        ticket_id: str,
        status_val: Optional[str] = None,
        assigned_to: Optional[str] = None
    ) -> SupportTicket:
        ticket = await session.get(SupportTicket, ticket_id)
        if not ticket:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ticket not found")

        if status_val:
            ticket.status = status_val
        if assigned_to:
            ticket.assigned_admin_id = assigned_to

        ticket.updated_at = datetime.now(timezone.utc)
        await session.flush()
        return ticket

    # ---------------- RIDE IN-APP CHAT ----------------
    @staticmethod
    async def get_or_create_ride_chat(session: AsyncSession, ride_id: str) -> RideChat:
        stmt = select(RideChat).where(RideChat.ride_id == ride_id)
        res = await session.execute(stmt)
        chat = res.scalars().first()
        if not chat:
            chat = RideChat(ride_id=ride_id)
            session.add(chat)
            await session.flush()
        return chat

    @staticmethod
    async def send_chat_message(
        session: AsyncSession,
        ride_id: str,
        sender_user_id: str,
        sender_role: str,
        message_text: str
    ) -> RideChatMessage:
        ride = await session.get(Ride, ride_id)
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found")

        chat = await SupportChatService.get_or_create_ride_chat(session, ride_id)
        msg = RideChatMessage(
            chat_id=chat.id,
            ride_id=ride.id,
            sender_user_id=sender_user_id,
            sender_role=sender_role.upper(),
            message=message_text,
            is_read=False
        )
        session.add(msg)
        await session.flush()
        return msg

    @staticmethod
    async def get_chat_history(
        session: AsyncSession,
        ride_id: str,
        current_user_id: str
    ) -> List[RideChatMessage]:
        ride = await session.get(Ride, ride_id)
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found")

        chat = await SupportChatService.get_or_create_ride_chat(session, ride_id)
        stmt = (
            select(RideChatMessage)
            .where(RideChatMessage.chat_id == chat.id)
            .order_by(RideChatMessage.sent_at.asc())
        )
        res = await session.execute(stmt)
        return list(res.scalars().all())
