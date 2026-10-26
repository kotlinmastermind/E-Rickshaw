from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, Query, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_admin
from backend.domain.models import User
from backend.application.services.admin_service import AdminService

router = APIRouter(prefix="/admin", tags=["Platform Administration"])

class UserStatusUpdate(BaseModel):
    is_active: bool

class DriverApprovalUpdate(BaseModel):
    approval_status: str = Field(..., json_schema_extra={"example": "APPROVED"})  # APPROVED, REJECTED, SUSPENDED
    rejection_reason: Optional[str] = None

class CreateFareRulePayload(BaseModel):
    category_id: str
    base_fare_cents: int = Field(..., ge=0)
    per_km_cents: int = Field(..., ge=0)
    per_minute_cents: int = Field(..., ge=0)
    minimum_fare_cents: int = Field(..., ge=0)
    surge_multiplier: float = Field(default=1.0, ge=1.0)

@router.get("/dashboard/stats")
async def get_dashboard_stats(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    service = AdminService(db)
    return await service.get_dashboard_metrics()

@router.get("/users")
async def list_users(
    role: Optional[str] = Query(None),
    search: Optional[str] = Query(None),
    page: int = Query(1, ge=1),
    limit: int = Query(20, ge=1, le=100),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminService(db)
    return await service.list_users(role=role, search=search, page=page, limit=limit)

@router.put("/users/{user_id}/status")
async def update_user_status(
    user_id: str,
    payload: UserStatusUpdate,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminService(db)
    return await service.update_user_status(admin_id=admin.id, user_id=user_id, is_active=payload.is_active)

@router.get("/drivers")
async def list_drivers(
    approval_status: Optional[str] = Query(None),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminService(db)
    return await service.list_drivers(approval_status=approval_status)

@router.put("/drivers/{driver_id}/approval")
async def review_driver(
    driver_id: str,
    payload: DriverApprovalUpdate,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminService(db)
    return await service.review_driver_approval(
        admin_id=admin.id,
        driver_id=driver_id,
        status=payload.approval_status,
        rejection_reason=payload.rejection_reason
    )

@router.get("/rides")
async def list_rides(
    status: Optional[str] = Query(None),
    page: int = Query(1, ge=1),
    limit: int = Query(20, ge=1, le=100),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminService(db)
    return await service.list_rides(status=status, page=page, limit=limit)

@router.get("/rides/{ride_id}")
async def get_ride_admin_details(
    ride_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminService(db)
    timeline = await service.get_ride_timeline(ride_id)
    return {"ride_id": ride_id, "timeline": timeline}

@router.post("/fares")
async def create_fare_rule(
    payload: CreateFareRulePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminService(db)
    return await service.create_or_update_fare_rule(
        admin_id=admin.id,
        category_id=payload.category_id,
        base_fare_cents=payload.base_fare_cents,
        per_km_cents=payload.per_km_cents,
        per_minute_cents=payload.per_minute_cents,
        minimum_fare_cents=payload.minimum_fare_cents,
        surge_multiplier=payload.surge_multiplier
    )

# ==================== PHASE 2 ADMIN FEATURES ====================
from backend.application.services.admin_advanced_service import AdminAdvancedService
from backend.application.services.support_chat_service import SupportChatService
from backend.application.services.dispatch_advanced_service import DispatchAdvancedService

class BroadcastPayload(BaseModel):
    title: str = Field(..., json_schema_extra={"example": "Monsoon Fleet Operations Notice"})
    message: str = Field(..., json_schema_extra={"example": "Expect increased surge multipliers across Mumbai South."})
    target_role: str = "ALL"  # ALL, CUSTOMER, DRIVER
    channel: str = "IN_APP"

class SurgeRulePayload(BaseModel):
    zone_name: str
    category_id: Optional[str] = None
    multiplier: float = Field(..., ge=1.0, le=5.0)
    reason: str

class GeofencePayload(BaseModel):
    name: str
    zone_type: str = "RESTRICTED"  # RESTRICTED, SERVICE_AREA, HIGH_DEMAND
    coordinates: List[dict]  # [{"lat": 19.0, "lng": 72.8}, ...]
    surge_multiplier: float = 1.0

class VerifyPointPayload(BaseModel):
    latitude: float
    longitude: float

class CommissionRulePayload(BaseModel):
    rule_name: str
    category_id: Optional[str] = None
    commission_percentage: float = Field(..., ge=0.0, le=100.0)
    fixed_cut_cents: int = 0

class PayoutRejectPayload(BaseModel):
    rejection_reason: str = Field(..., min_length=3)

class PayoutSettlePayload(BaseModel):
    settlement_reference: str = Field(..., min_length=3)

# 1. Support Tickets
@router.get("/support/tickets")
async def list_all_support_tickets(
    status: Optional[str] = None,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import SupportTicket
    from sqlalchemy import select
    stmt = select(SupportTicket).order_by(SupportTicket.created_at.desc())
    if status:
        stmt = stmt.where(SupportTicket.status == status)
    res = await db.execute(stmt)
    tickets = res.scalars().all()
    return [
        {
            "id": t.id,
            "user_id": t.user_id,
            "ride_id": t.ride_id,
            "subject": t.subject,
            "category": t.category,
            "status": t.status,
            "priority": t.priority,
            "assigned_to": t.assigned_to,
            "created_at": t.created_at.isoformat()
        }
        for t in tickets
    ]

@router.put("/support/tickets/{ticket_id}/status")
async def update_support_ticket_status(
    ticket_id: str,
    status_val: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    ticket = await SupportChatService.admin_update_ticket(db, ticket_id, status_val=status_val, assigned_to=admin.id)
    await db.commit()
    return {"status": "UPDATED", "ticket_id": ticket.id, "ticket_status": ticket.status}

@router.post("/support/tickets/{ticket_id}/internal-note")
async def add_internal_support_note(
    ticket_id: str,
    note: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    msg = await SupportChatService.post_ticket_message(
        session=db,
        ticket_id=ticket_id,
        sender_user_id=admin.id,
        message_text=note,
        is_internal_only=True,
        is_staff=True
    )
    await db.commit()
    return {"status": "NOTE_ADDED", "message_id": msg.id}

# 2. Broadcast Notifications
@router.post("/broadcasts")
async def send_broadcast_notification(
    payload: BroadcastPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    bc = await AdminAdvancedService.send_broadcast(
        session=db,
        admin_user_id=admin.id,
        title=payload.title,
        message=payload.message,
        target_role=payload.target_role,
        channel=payload.channel
    )
    await db.commit()
    return {
        "status": "SENT",
        "broadcast_id": bc.id,
        "recipient_count": bc.dispatched_count,
        "dispatched_count": bc.dispatched_count,
        "sent_at": bc.sent_at.isoformat() if bc.sent_at else None
    }

@router.get("/broadcasts")
async def list_broadcast_notifications(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    bcs = await AdminAdvancedService.list_broadcasts(db)
    return [
        {
            "id": b.id,
            "title": b.title,
            "message": b.message,
            "target_role": b.target_audience,
            "target_audience": b.target_audience,
            "recipient_count": b.dispatched_count,
            "dispatched_count": b.dispatched_count,
            "sent_at": b.sent_at.isoformat() if b.sent_at else None
        }
        for b in bcs
    ]

# 3. Heat Map Monitor
@router.get("/heat-map/monitor")
async def get_admin_heat_map(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    return await DispatchAdvancedService.get_privacy_preserving_heatmap(db)

# 4. Surge Pricing Config
@router.post("/surge/rules")
async def create_surge_rule(
    payload: SurgeRulePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    rule = await AdminAdvancedService.create_surge_rule(
        session=db,
        zone_id=None,
        category_id=payload.category_id,
        multiplier=payload.multiplier,
        reason=payload.reason,
        admin_user_id=admin.id
    )
    await db.commit()
    return {"status": "CREATED", "rule_id": rule.id, "multiplier": rule.multiplier}

@router.get("/surge/rules")
async def list_surge_rules(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    rules = await AdminAdvancedService.list_surge_rules(db)
    return [
        {
            "id": r.id,
            "zone_id": r.zone_id,
            "category_id": r.category_id,
            "multiplier": r.multiplier,
            "max_multiplier": r.max_multiplier,
            "reason": r.reason,
            "is_active": r.is_active
        }
        for r in rules
    ]

# 5. Geo-Fencing
@router.post("/geofences")
async def create_geofence(
    payload: GeofencePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    zone = await AdminAdvancedService.create_geofence(
        session=db,
        name=payload.name,
        zone_type=payload.zone_type,
        coordinates=payload.coordinates,
        surge_multiplier=payload.surge_multiplier
    )
    await db.commit()
    return {"status": "CREATED", "zone_id": zone.id, "name": zone.name}

@router.get("/geofences")
async def list_geofences(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    zones = await AdminAdvancedService.list_geofences(db)
    import json
    return [
        {
            "id": z.id,
            "name": z.name,
            "zone_type": z.zone_type,
            "coordinates": json.loads(z.coordinates_json),
            "is_active": z.is_active,
            "surge_multiplier": z.surge_multiplier
        }
        for z in zones
    ]

@router.post("/geofences/verify-point")
async def verify_point_in_geofence(
    payload: VerifyPointPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    return await AdminAdvancedService.check_location_geofence(db, payload.latitude, payload.longitude)

# 6. Driver Payouts Approval & Settlement
@router.get("/payouts")
async def list_payouts(
    status: Optional[str] = None,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    payouts = await AdminAdvancedService.list_payout_requests(db, status_filter=status)
    return [
        {
            "id": p.id,
            "driver_id": p.driver_id,
            "amount_cents": p.amount_cents,
            "status": p.status,
            "bank_account_mask": p.bank_account_masked,
            "bank_account_masked": p.bank_account_masked,
            "requested_at": p.created_at.isoformat(),
            "reviewed_at": p.reviewed_at.isoformat() if p.reviewed_at else None,
            "rejection_reason": p.rejection_reason,
            "payout_reference": p.payout_reference,
            "settlement_reference": p.payout_reference
        }
        for p in payouts
    ]

@router.post("/payouts/{payout_id}/approve")
async def approve_payout(
    payout_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    payout = await AdminAdvancedService.approve_payout(db, payout_id, admin.id)
    await db.commit()
    return {"status": "APPROVED", "payout_id": payout.id}

@router.post("/payouts/{payout_id}/reject")
async def reject_payout(
    payout_id: str,
    payload: PayoutRejectPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    payout = await AdminAdvancedService.reject_payout(db, payout_id, admin.id, payload.rejection_reason)
    await db.commit()
    return {"status": "REJECTED", "payout_id": payout.id}

@router.post("/payouts/{payout_id}/settle")
async def settle_payout(
    payout_id: str,
    payload: PayoutSettlePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    payout = await AdminAdvancedService.mark_payout_paid(db, payout_id, admin.id, payload.settlement_reference)
    await db.commit()
    return {"status": "PAID", "payout_id": payout.id, "settlement_reference": payout.payout_reference}

# 7. Commission Settings
@router.post("/commissions")
async def create_commission_rule(
    payload: CommissionRulePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    rule = await AdminAdvancedService.create_commission_rule(
        session=db,
        category_id=payload.category_id,
        percentage=payload.commission_percentage,
        admin_user_id=admin.id
    )
    await db.commit()
    return {"status": "CREATED", "rule_id": rule.id, "commission_percentage": rule.platform_commission_percentage}

@router.get("/commissions")
async def list_commission_rules(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    rules = await AdminAdvancedService.list_commission_rules(db)
    return [
        {
            "id": r.id,
            "category_id": r.category_id,
            "version": r.version,
            "commission_percentage": r.platform_commission_percentage,
            "driver_share_percentage": r.driver_share_percentage,
            "is_active": r.is_active
        }
        for r in rules
    ]

# 8. Phase 3: Analytics Reports
@router.get("/reports")
async def get_admin_reports(
    start_date: Optional[str] = Query(None),
    end_date: Optional[str] = Query(None),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminService(db)
    return await service.get_analytics_reports(start_date=start_date, end_date=end_date)

# 9. Phase 3: Audit Logs Viewer
@router.get("/audit-logs")
async def get_admin_audit_logs(
    actor_role: Optional[str] = Query(None),
    action: Optional[str] = Query(None),
    target_resource: Optional[str] = Query(None),
    page: int = Query(1, ge=1),
    limit: int = Query(50, ge=1, le=200),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminService(db)
    return await service.get_audit_logs(
        actor_role=actor_role,
        action=action,
        target_resource=target_resource,
        page=page,
        limit=limit
    )

# 10. Phase 3: Database Backup & Recovery Control
@router.get("/backups")
async def list_database_backups(
    admin: User = Depends(get_current_admin)
):
    from backend.application.services.backup_service import DatabaseBackupService
    service = DatabaseBackupService()
    return service.list_backups()

@router.post("/backups/trigger")
async def trigger_database_backup(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.backup_service import DatabaseBackupService
    from backend.domain.models import AuditLog

    service = DatabaseBackupService(db)
    snapshot_meta = service.generate_snapshot(trigger_source=f"ADMIN:{admin.id}")

    # Audit log
    audit = AuditLog(
        actor_id=admin.id,
        actor_role="admin",
        action="BACKUP_TRIGGERED",
        target_resource="database_backups",
        target_id=snapshot_meta["backup_id"],
        status="SUCCESS" if snapshot_meta["test_restore_passed"] else "FAILED",
        metadata_json=f'{{"filename": "{snapshot_meta["filename"]}", "sha256": "{snapshot_meta["sha256_checksum"][:16]}..."}}'
    )
    db.add(audit)
    await db.commit()

    return snapshot_meta

