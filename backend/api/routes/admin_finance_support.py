"""Authenticated Part 15 finance, support and communications operations."""
from datetime import datetime
from typing import Any, Dict, Optional

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.api.deps import get_current_admin
from backend.infrastructure.database.session import get_db
from backend.domain.models import AdminPermission, AdminRole, AdminRolePermission, AdminUserRole, User
from sqlalchemy import select
from backend.application.services.admin_finance_support_service import AdminFinanceSupportService

router = APIRouter(prefix="/admin", tags=["Admin Finance, Support and Communications"])


class NotePayload(BaseModel):
    note: str = Field(min_length=8, max_length=4000)


class PayoutApprovalPayload(BaseModel):
    reason: str = Field(min_length=10, max_length=1000)


class RefundRequestPayload(BaseModel):
    payment_id: str
    amount_cents: int = Field(gt=0)
    reason: str = Field(min_length=10, max_length=1000)


class ReviewPayload(BaseModel):
    approve: bool
    reason: str = Field(min_length=10, max_length=1000)


class WalletAdjustmentPayload(BaseModel):
    user_id: str
    amount_cents: int = Field(gt=0, le=10_000_000)
    adjustment_type: str
    reason: str = Field(min_length=12, max_length=1000)


class InvoiceCorrectionPayload(BaseModel):
    ride_id: str
    changes: Dict[str, Any]
    reason: str = Field(min_length=12, max_length=1000)


class AssignmentPayload(BaseModel):
    assignee_id: str
    squad: str = Field(min_length=2, max_length=50)


class MessagePayload(BaseModel):
    message: str = Field(min_length=2, max_length=4000)


class TemplatePayload(BaseModel):
    template_id: Optional[str] = None
    title: str = Field(min_length=3, max_length=100)
    category: str = Field(min_length=2, max_length=50)
    language_code: str = Field(min_length=2, max_length=10)
    body: str = Field(min_length=3, max_length=4000)


class EscalationPolicyPayload(BaseModel):
    policy_id: Optional[str] = None
    name: str = Field(min_length=3, max_length=100)
    priority: str
    sla_minutes: int = Field(gt=0, le=10080)
    escalation_minutes: int = Field(gt=0, le=20160)
    target_squad: str = Field(min_length=2, max_length=50)
    is_active: bool = True


class EscalationPayload(BaseModel):
    reason: str = Field(min_length=8, max_length=1000)


class CommissionPreviewPayload(BaseModel):
    category_id: Optional[str] = None
    commission_percent: float = Field(ge=0, le=100)
    start: Optional[datetime] = None
    end: Optional[datetime] = None


class CommissionDraftPayload(BaseModel):
    category_id: Optional[str] = None
    commission_percent: float = Field(ge=0, le=100)
    effective_from: Optional[datetime] = None
    reason: str = Field(min_length=12, max_length=1000)


class BroadcastPayload(BaseModel):
    title: str = Field(min_length=3, max_length=128)
    message: str = Field(min_length=1, max_length=5000)
    target_role: str = "ALL"
    language: Optional[str] = None
    localized_content: Dict[str, Dict[str, str]] = Field(default_factory=dict)
    scheduled_at: Optional[datetime] = None
    emergency: bool = False


class BroadcastPreviewPayload(BroadcastPayload):
    pass


def service(db: AsyncSession) -> AdminFinanceSupportService:
    return AdminFinanceSupportService(db)


@router.get("/financeops/payouts")
async def finance_payouts(status: Optional[str] = None, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).payouts(status)


@router.get("/financeops/payouts/reconciliation")
async def payout_reconciliation(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).reconcile_payouts()


@router.post("/financeops/payouts/{payout_id}/investigate")
async def investigate_payout(payout_id: str, payload: NotePayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).annotate_payout_exception(admin.id, payout_id, payload.note)


@router.post("/financeops/payouts/{payout_id}/approve")
async def approve_payout(payout_id: str, payload: PayoutApprovalPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).approve_payout(admin.id, payout_id, payload.reason)


@router.post("/financeops/commission-impact")
async def commission_impact(payload: CommissionPreviewPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).commission_impact(payload.category_id, payload.commission_percent, payload.start, payload.end)


@router.get("/financeops/commissions")
async def commission_versions(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).commission_rules()


@router.post("/financeops/commissions")
async def create_commission_version(payload: CommissionDraftPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).create_commission_draft(admin.id, payload.category_id, payload.commission_percent, payload.effective_from, payload.reason)


@router.post("/financeops/commissions/{rule_id}/review")
async def review_commission_version(rule_id: str, payload: ReviewPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).approve_commission_rule(admin.id, rule_id, payload.approve, payload.reason)


@router.get("/financeops/refunds")
async def refund_queue(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).refund_requests()


@router.post("/financeops/refunds")
async def create_refund(payload: RefundRequestPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).create_refund_request(admin.id, payload.payment_id, payload.amount_cents, payload.reason)


@router.post("/financeops/refunds/{refund_id}/review")
async def review_refund(refund_id: str, payload: ReviewPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).review_refund(admin.id, refund_id, payload.approve, payload.reason)


@router.get("/financeops/payment-exceptions")
async def payment_exceptions(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).payment_exceptions()


@router.get("/financeops/webhooks")
async def webhook_inbox(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).webhook_inbox()


@router.post("/financeops/webhooks/{inbox_id}/replay")
async def replay_webhook(inbox_id: str, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).replay_webhook(admin.id, inbox_id)


@router.get("/financeops/wallet-adjustments")
async def wallet_adjustment_queue(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).wallet_adjustments()


@router.post("/financeops/wallet-adjustments")
async def request_wallet_adjustment(payload: WalletAdjustmentPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).create_wallet_adjustment(admin.id, payload.user_id, payload.amount_cents, payload.adjustment_type, payload.reason)


@router.post("/financeops/wallet-adjustments/{request_id}/approve")
async def approve_wallet_adjustment(request_id: str, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).approve_wallet_adjustment(admin.id, request_id)


@router.get("/financeops/ledger.csv")
async def export_ledger(start: Optional[datetime] = None, end: Optional[datetime] = None, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    content = await service(db).ledger_csv(start, end)
    return StreamingResponse(iter([content]), media_type="text/csv", headers={"Content-Disposition": "attachment; filename=financial-ledger.csv"})


@router.get("/financeops/invoice-corrections")
async def invoice_correction_queue(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).invoice_corrections()


@router.post("/financeops/invoice-corrections")
async def request_invoice_correction(payload: InvoiceCorrectionPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).create_invoice_correction(admin.id, payload.ride_id, payload.changes, payload.reason)


@router.post("/financeops/invoice-corrections/{request_id}/review")
async def review_invoice_correction(request_id: str, payload: ReviewPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).review_invoice_correction(admin.id, request_id, payload.approve, payload.reason)


@router.get("/supportops/tickets")
async def support_tickets(status: Optional[str] = None, squad: Optional[str] = None, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).support_tickets(status, squad)


@router.get("/supportops/workload")
async def support_workload(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).support_workload()


@router.post("/supportops/tickets/{ticket_id}/assign")
async def assign_ticket(ticket_id: str, payload: AssignmentPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).assign_support_ticket(admin.id, ticket_id, payload.assignee_id, payload.squad)


@router.get("/supportops/tickets/{ticket_id}/internal-notes")
async def internal_notes(ticket_id: str, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).list_internal_notes(ticket_id)


@router.post("/supportops/tickets/{ticket_id}/internal-notes")
async def add_internal_note(ticket_id: str, payload: MessagePayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).internal_collaboration(admin.id, ticket_id, payload.message)


@router.post("/supportops/tickets/{ticket_id}/templates/{template_id}/send")
async def send_ticket_template(ticket_id: str, template_id: str, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).send_response_template(admin.id, ticket_id, template_id)


@router.get("/supportops/templates")
async def response_templates(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).response_templates()


@router.post("/supportops/templates")
async def save_response_template(payload: TemplatePayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).save_response_template(admin.id, payload.template_id, payload.title, payload.category, payload.language_code, payload.body)


@router.get("/supportops/escalation-policies")
async def escalation_policies(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).escalation_policies()


@router.post("/supportops/escalation-policies")
async def save_escalation_policy(payload: EscalationPolicyPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).save_escalation_policy(admin.id, payload.policy_id, payload.model_dump(exclude={"policy_id"}))


@router.post("/supportops/tickets/{ticket_id}/escalate")
async def escalate_ticket(ticket_id: str, payload: EscalationPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).escalate_ticket(admin.id, ticket_id, payload.reason)


@router.post("/communications/preview")
async def preview_broadcast(payload: BroadcastPreviewPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).preview_broadcast(payload.title, payload.message, payload.target_role, payload.language, payload.localized_content, payload.emergency)


@router.get("/communications/broadcasts")
async def list_broadcasts(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).broadcasts()


@router.post("/communications/broadcasts")
async def create_broadcast(payload: BroadcastPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    if payload.emergency:
        role_ids = (await db.execute(select(AdminUserRole.role_id).where(AdminUserRole.user_id == admin.id))).scalars().all()
        roles = (await db.execute(select(AdminRole).where(AdminRole.id.in_(role_ids)))).scalars().all()
        allowed = any(r.is_superuser for r in roles)
        if not allowed:
            allowed = (await db.execute(select(AdminPermission.id).join(AdminRolePermission,
                AdminRolePermission.permission_id == AdminPermission.id).where(
                    AdminRolePermission.role_id.in_(role_ids),
                    AdminPermission.permission_key == "admin.broadcasts.emergency").limit(1))).first() is not None
        if not allowed:
            raise HTTPException(status_code=403, detail="Emergency announcement permission is required.")
    return await service(db).create_broadcast(admin.id, payload.title, payload.message, payload.target_role, payload.language, payload.localized_content, payload.scheduled_at, payload.emergency)


@router.get("/communications/opt-outs")
async def broadcast_optout_report(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await service(db).optout_report()
