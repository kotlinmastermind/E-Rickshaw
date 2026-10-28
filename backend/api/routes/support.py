from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File, Form, Query
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user, get_current_admin
from backend.domain.models import User, SupportTicket
from backend.application.services.support_chat_service import SupportChatService
from backend.application.services.support_advanced_service import SupportAdvancedService

router = APIRouter(prefix="/support", tags=["Support & Concierge Desk"])

# =============================================================================
# PYDANTIC SCHEMAS
# =============================================================================
class TicketCreateRequest(BaseModel):
    subject: str = Field(..., json_schema_extra={"example": "Luggage assistance query"})
    category: str = Field("RIDE_INQUIRY", json_schema_extra={"example": "RIDE_INQUIRY"})
    description: str = Field(..., json_schema_extra={"example": "I left an umbrella in the Mercedes S680."})
    ride_id: Optional[str] = None
    priority: str = "MEDIUM"
    language_preference: str = "en"
    data_retention_days: int = 365
    allow_duplicate: bool = False

class MessageCreateRequest(BaseModel):
    message_text: str = Field(..., json_schema_extra={"example": "Thank you, our concierge is reviewing this."})
    is_internal_only: bool = False
    attachment_path: Optional[str] = None

class TicketStatusUpdate(BaseModel):
    status: str = Field(..., json_schema_extra={"example": "RESOLVED"})
    assigned_to: Optional[str] = None

class ArticleRatingRequest(BaseModel):
    was_helpful: bool
    feedback_text: Optional[str] = None

class GuidedCancellationRequest(BaseModel):
    reason_code: str
    notes: Optional[str] = None
    waiver_requested: bool = False

class PaymentTroubleshootingRequest(BaseModel):
    error_code: Optional[str] = None
    payment_method: Optional[str] = None
    ride_id: Optional[str] = None

class AccountRecoveryRequest(BaseModel):
    recovery_type: str  # PIN_RESET, BIOMETRIC_RESYNC, PHONE_MIGRATION, EMAIL_VERIFICATION
    details: Optional[str] = None

class RefundClaimRequest(BaseModel):
    ride_id: str
    reason: str
    requested_cents: Optional[int] = None
    explanation: str = ""

class FareReviewRequest(BaseModel):
    ride_id: str
    disputed_amount_cents: int
    evidence_text: str
    evidence_url: Optional[str] = None

class LostItemClaimRequest(BaseModel):
    ride_id: str
    item_category: str
    item_description: str
    seat_location: Optional[str] = None

class CallbackRequestModel(BaseModel):
    phone_number: str
    preferred_time_window: str = "IMMEDIATE"  # IMMEDIATE, WITHIN_1_HOUR, EVENING_WINDOW
    subject: str = "VIP Concierge Inquiry"
    ticket_id: Optional[str] = None

class EscalationRequest(BaseModel):
    reason: str

class CsatSurveyRequest(BaseModel):
    score: int = Field(..., ge=1, le=5)
    comment: Optional[str] = None

class LanguagePreferenceRequest(BaseModel):
    language_code: str = Field(..., max_length=10)

# =============================================================================
# 176 & 177: KNOWLEDGE BASE & CONTEXTUAL HELP
# =============================================================================
@router.get("/articles")
async def search_help_center(
    query: Optional[str] = Query(None, description="Search term across title, content, and keywords"),
    category: Optional[str] = Query(None, description="Article category: BOOKING, PAYMENTS, SAFETY, CHAUFFEUR, PRIVACY, CANCELLATION, LOST_FOUND"),
    tag: Optional[str] = Query(None, description="Context tag from screen: booking_screen, cancellation, payment, airport, multi_stop"),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.search_help_articles(query=query, category=category, context_tag=tag)

@router.get("/articles/{slug_or_id}")
async def get_help_article(
    slug_or_id: str,
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.get_article_by_slug_or_id(slug_or_id)

@router.post("/articles/{article_id}/rate")
async def rate_article(
    article_id: str,
    payload: ArticleRatingRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.rate_help_article(
        user_id=user.id,
        article_id=article_id,
        was_helpful=payload.was_helpful,
        feedback_text=payload.feedback_text
    )

# =============================================================================
# 193 & 194: INCIDENT BANNER & LIVE SERVICE STATUS
# =============================================================================
@router.get("/incidents/active")
async def get_active_incident_banners(
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.get_active_incidents()

@router.get("/service-status")
async def get_platform_service_status(
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.get_service_status()

# =============================================================================
# 178 & 179: GUIDED RIDE CANCELLATION & FEE BREAKDOWN
# =============================================================================
@router.get("/rides/{ride_id}/cancellation-quote")
async def get_cancellation_quote(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.get_cancellation_guidance(user_id=user.id, ride_id=ride_id)

@router.post("/rides/{ride_id}/guided-cancel")
async def cancel_ride_guided(
    ride_id: str,
    payload: GuidedCancellationRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.execute_guided_cancellation(
        user_id=user.id,
        ride_id=ride_id,
        reason_code=payload.reason_code,
        notes=payload.notes,
        waiver_requested=payload.waiver_requested
    )

# =============================================================================
# 185 - 190: SELF-SERVICE CAPABILITIES & CALLBACK REQUESTS
# =============================================================================
@router.post("/self-service/payment-troubleshooting")
async def diagnose_payment_issue(
    payload: PaymentTroubleshootingRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.troubleshoot_payment(
        user_id=user.id,
        error_code=payload.error_code,
        payment_method=payload.payment_method,
        ride_id=payload.ride_id
    )

@router.post("/self-service/account-recovery")
async def request_account_recovery(
    payload: AccountRecoveryRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.initiate_account_recovery(
        user_id=user.id,
        recovery_type=payload.recovery_type,
        details=payload.details
    )

@router.post("/self-service/refund-request")
async def submit_refund_claim(
    payload: RefundClaimRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.submit_refund_request(
        user_id=user.id,
        ride_id=payload.ride_id,
        reason=payload.reason,
        requested_cents=payload.requested_cents,
        explanation=payload.explanation
    )

@router.post("/self-service/fare-review")
async def submit_fare_review_evidence(
    payload: FareReviewRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.submit_fare_review(
        user_id=user.id,
        ride_id=payload.ride_id,
        disputed_amount_cents=payload.disputed_amount_cents,
        evidence_text=payload.evidence_text,
        evidence_url=payload.evidence_url
    )

@router.post("/self-service/lost-item")
async def report_lost_property(
    payload: LostItemClaimRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.report_lost_item(
        user_id=user.id,
        ride_id=payload.ride_id,
        item_category=payload.item_category,
        item_description=payload.item_description,
        seat_location=payload.seat_location
    )

@router.post("/callbacks")
async def request_concierge_callback(
    payload: CallbackRequestModel,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.request_callback(
        user_id=user.id,
        phone_number=payload.phone_number,
        preferred_time_window=payload.preferred_time_window,
        subject=payload.subject,
        ticket_id=payload.ticket_id
    )

# =============================================================================
# 180, 184, 191, 196, 198, 200: TICKETS CRUD & SQUAD ROUTING
# =============================================================================
@router.post("/tickets")
async def create_ticket(
    payload: TicketCreateRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.create_support_ticket(
        user_id=user.id,
        subject=payload.subject,
        category=payload.category,
        description=payload.description,
        priority=payload.priority,
        ride_id=payload.ride_id,
        language_preference=payload.language_preference,
        data_retention_days=payload.data_retention_days,
        allow_duplicate=payload.allow_duplicate
    )

@router.get("/tickets")
async def list_my_tickets(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = (
        select(SupportTicket)
        .where(SupportTicket.user_id == user.id)
        .order_by(SupportTicket.created_at.desc())
    )
    res = await db.execute(stmt)
    tickets = res.scalars().all()
    return [
        {
            "id": t.id,
            "ticket_number": t.ticket_number,
            "complaint_reference_number": t.complaint_reference_number,
            "subject": t.subject,
            "category": t.category,
            "status": t.status,
            "priority": t.priority,
            "assigned_squad": t.assigned_squad,
            "language_preference": t.language_preference,
            "is_escalated": t.is_escalated,
            "lost_item_status": t.lost_item_status,
            "csat_score": t.csat_score,
            "data_retention_days": t.data_retention_days,
            "ride_id": t.ride_id,
            "created_at": t.created_at.isoformat(),
            "updated_at": t.updated_at.isoformat() if t.updated_at else t.created_at.isoformat()
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
    data = await SupportChatService.get_ticket_with_messages(
        session=db,
        ticket_id=ticket_id,
        current_user_id=user.id,
        is_staff=is_staff
    )
    ticket = await db.get(SupportTicket, ticket_id)
    if ticket:
        data.update({
            "complaint_reference_number": ticket.complaint_reference_number,
            "assigned_squad": ticket.assigned_squad,
            "language_preference": ticket.language_preference,
            "is_escalated": ticket.is_escalated,
            "escalation_reason": ticket.escalation_reason,
            "lost_item_category": ticket.lost_item_category,
            "lost_item_description": ticket.lost_item_description,
            "lost_item_status": ticket.lost_item_status,
            "csat_score": ticket.csat_score,
            "csat_comment": ticket.csat_comment,
            "data_retention_days": ticket.data_retention_days
        })
    return data

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

# =============================================================================
# 181: ATTACHMENT UPLOAD
# =============================================================================
@router.post("/tickets/{ticket_id}/attachments")
async def upload_attachment(
    ticket_id: str,
    file: UploadFile = File(...),
    note: Optional[str] = Form(None),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.upload_ticket_attachment(
        user_id=user.id,
        ticket_id=ticket_id,
        file=file,
        note=note
    )

# =============================================================================
# 182: TICKET RESPONSE TIMELINE
# =============================================================================
@router.get("/tickets/{ticket_id}/timeline")
async def get_timeline(
    ticket_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.get_ticket_timeline(ticket_id=ticket_id, current_user_id=user.id)

# =============================================================================
# 183: SUPPORT CONVERSATION NOTIFICATIONS
# =============================================================================
@router.get("/notifications")
async def list_support_notifications(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.get_support_notifications(user.id)

# =============================================================================
# 191: LANGUAGE PREFERENCE
# =============================================================================
@router.put("/tickets/{ticket_id}/language")
async def update_ticket_language(
    ticket_id: str,
    payload: LanguagePreferenceRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.set_ticket_language(
        user_id=user.id,
        ticket_id=ticket_id,
        language_code=payload.language_code
    )

# =============================================================================
# 195: ESCALATION REQUEST
# =============================================================================
@router.post("/tickets/{ticket_id}/escalate")
async def escalate_ticket_case(
    ticket_id: str,
    payload: EscalationRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.escalate_ticket(
        user_id=user.id,
        ticket_id=ticket_id,
        reason=payload.reason
    )

# =============================================================================
# 197: CSAT SURVEY
# =============================================================================
@router.post("/tickets/{ticket_id}/survey")
async def submit_ticket_survey(
    ticket_id: str,
    payload: CsatSurveyRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.submit_csat_survey(
        user_id=user.id,
        ticket_id=ticket_id,
        score=payload.score,
        comment=payload.comment
    )

# =============================================================================
# 199: READ RECEIPTS
# =============================================================================
@router.post("/tickets/{ticket_id}/read")
async def mark_ticket_read(
    ticket_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.mark_messages_read(user_id=user.id, ticket_id=ticket_id)

# =============================================================================
# 200: DATA RETENTION PREFERENCE DISPLAY
# =============================================================================
@router.get("/retention-policy")
async def get_data_retention_policy(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SupportAdvancedService(db)
    return await service.get_data_retention_policy(user.id)
