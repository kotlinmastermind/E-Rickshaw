from typing import Optional, List, Dict, Any
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, Query, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_admin
from backend.domain.models import User
from backend.application.services.admin_service import AdminService
from backend.application.services.shared_ride_analytics_service import SharedRideAnalyticsService
from backend.application.services.admin_governance_service import AdminGovernanceService

router = APIRouter(prefix="/admin", tags=["Platform Administration"])

@router.get("/shared-rides/analytics")
async def get_shared_ride_operational_analytics(
    days: int = Query(30, ge=1, le=365),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    return await SharedRideAnalyticsService(db).get_operational_analytics(days)

class UserStatusUpdate(BaseModel):
    is_active: bool
    reason: str = Field(min_length=10, max_length=1000)

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
    fare_cap_cents: Optional[int] = Field(default=None, ge=0)

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
    active: Optional[bool] = Query(None),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    return await AdminGovernanceService(db).list_users(role=role or "", search=search or "", active=active, page=page, limit=limit)

@router.put("/users/{user_id}/status")
async def update_user_status(
    user_id: str,
    payload: UserStatusUpdate,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminService(db)
    return await AdminGovernanceService(db).set_customer_status(admin.id, user_id, payload.is_active, payload.reason)

@router.get("/drivers")
async def list_drivers(
    approval_status: Optional[str] = Query(None),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    return await AdminGovernanceService(db).list_drivers(approval_status or "")

@router.put("/drivers/{driver_id}/approval")
async def review_driver(
    driver_id: str,
    payload: DriverApprovalUpdate,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    if payload.approval_status.upper() == "APPROVED":
        from sqlalchemy import select
        from backend.domain.models import DriverDocument
        documents = (await db.execute(select(DriverDocument).where(DriverDocument.driver_id == driver_id))).scalars().all()
        if not documents or any(doc.verification_status != "VERIFIED" for doc in documents):
            raise HTTPException(status_code=409, detail="Every required driver document must be verified before approval.")
    service = AdminService(db)
    return await service.review_driver_approval(
        admin_id=admin.id,
        driver_id=driver_id,
        status=payload.approval_status,
        rejection_reason=payload.rejection_reason
    )

@router.get("/rides")
async def list_rides(
    ride_id: Optional[str] = Query(None, max_length=64),
    status: Optional[str] = Query(None),
    page: int = Query(1, ge=1),
    limit: int = Query(20, ge=1, le=100),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    return await AdminGovernanceService(db).list_rides(ride_id or "", status or "", page, limit)

@router.get("/rides/{ride_id}")
async def get_ride_admin_details(
    ride_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    return await AdminGovernanceService(db).ride_timeline(ride_id)

@router.get("/fares")
async def list_fare_rules(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    return await AdminGovernanceService(db).list_fare_rules()

@router.post("/fares")
async def create_fare_rule(
    payload: CreateFareRulePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    service = AdminGovernanceService(db)
    return await service.create_fare_draft(admin.id, {
        "category_id": payload.category_id, "base_fare_cents": payload.base_fare_cents,
        "per_km_cents": payload.per_km_cents, "per_minute_cents": payload.per_minute_cents,
        "minimum_fare_cents": payload.minimum_fare_cents, "surge_multiplier": payload.surge_multiplier,
        "fare_cap_cents": getattr(payload, "fare_cap_cents", None), "effective_from": datetime.now(timezone.utc),
    })

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
    zone_name: Optional[str] = None
    zone_id: Optional[str] = None
    category_id: Optional[str] = None
    multiplier: float = Field(..., ge=1.0, le=5.0)
    reason: str
    starts_at: Optional[datetime] = None
    ends_at: Optional[datetime] = None

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
    reason: str = Field(..., min_length=12, max_length=1000)

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
            "ticket_number": t.ticket_number,
            "complaint_reference_number": t.complaint_reference_number,
            "user_id": t.user_id,
            "ride_id": t.ride_id,
            "subject": t.subject,
            "category": t.category,
            "status": t.status,
            "priority": t.priority,
            "assigned_squad": t.assigned_squad,
            "language_preference": t.language_preference,
            "is_escalated": t.is_escalated,
            "escalation_reason": t.escalation_reason,
            "lost_item_category": t.lost_item_category,
            "lost_item_status": t.lost_item_status,
            "csat_score": t.csat_score,
            "data_retention_days": t.data_retention_days,
            "refund_amount_cents": t.refund_amount_cents,
            "assigned_to": t.assigned_admin_id,
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
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    result = await AdminFinanceSupportService(db).create_broadcast(
        admin.id, payload.title, payload.message, payload.target_role, None, {}, None, False)
    return {**result, "broadcast_id": result["id"], "recipient_count": result["eligible_count"]}

@router.get("/broadcasts")
async def list_broadcast_notifications(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    return await AdminFinanceSupportService(db).broadcasts()

class LocationRetentionUpdatePayload(BaseModel):
    category: str
    retention_ttl_days: int = Field(..., ge=7, le=1095)

class LocationRetentionPurgePayload(BaseModel):
    category: str = "DRIVER_RAW_TELEMETRY"
    dry_run: bool = True

class AdminRoadRestrictionPayload(BaseModel):
    title: str = Field(..., min_length=3, max_length=128)
    description: str = Field(..., min_length=5, max_length=512)
    restriction_type: str = Field("ROAD_CLOSURE", pattern="^(ROAD_CLOSURE|VIP_CORRIDOR|MONSOON_WATERLOGGING|ZERO_EMISSION_ONLY)$")
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    radius_meters: float = Field(500.0, ge=50, le=10000)
    starts_at: Optional[datetime] = None
    ends_at: Optional[datetime] = None

# 3. Heat Map Monitor (Feature 440: Time Filtered Demand & Supply)
@router.get("/heat-map/monitor")
async def get_admin_heat_map(
    window_hours: int = Query(2, ge=1, le=72),
    metric: str = Query("demand", pattern="^(demand|supply)$"),
    minimum_count: int = Query(5, ge=5, le=100),
    time_filter: str = Query("ALL_DAY", pattern="^(ALL_DAY|MORNING_RUSH|AFTERNOON_LULL|EVENING_GALA|LATE_NIGHT)$"),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).get_demand_heatmap_filtered(time_filter, window_hours, metric, minimum_count)

# Feature 441. Driver density map with privacy aggregation
@router.get("/location-ops/driver-density")
async def driver_density_map(
    resolution_km: float = Query(2.0, ge=0.5, le=10.0),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).get_driver_density_privacy_grid(resolution_km)

# Feature 444. Driver GPS spoofing risk signals
@router.get("/location-ops/spoof-signals")
async def get_spoofing_signals(
    driver_id: Optional[str] = Query(None),
    limit: int = Query(50, ge=1, le=200),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).get_driver_spoofing_signals(driver_id, limit)

# Feature 447. Location data retention controls
@router.get("/location-ops/retention-controls")
async def get_location_retention_controls(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).get_location_retention_controls()

@router.put("/location-ops/retention-controls")
async def update_location_retention_controls(
    payload: LocationRetentionUpdatePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).update_location_retention_controls(payload.category, payload.retention_ttl_days)

@router.post("/location-ops/retention-purge")
async def purge_location_records(
    payload: LocationRetentionPurgePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).run_location_retention_purge(payload.category, payload.dry_run)

# Feature 448. Location event quality dashboard
@router.get("/location-ops/quality")
async def location_quality_dashboard(
    window_hours: int = Query(24, ge=1, le=168),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).admin_location_quality(window_hours)

# Feature 426. Live map provider health check
@router.get("/location-ops/provider-health")
async def location_provider_health(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).get_live_map_provider_health()

# Feature 436. Road restrictions management
@router.get("/location-ops/road-restrictions")
async def list_admin_road_restrictions(
    latitude: float = Query(28.6139, ge=-90, le=90),
    longitude: float = Query(77.2090, ge=-180, le=180),
    radius_km: float = Query(50.0, ge=1.0, le=200.0),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.location_ops_service import LocationOpsService
    return await LocationOpsService(db).list_road_restrictions(latitude, longitude, radius_km)

@router.post("/location-ops/road-restrictions")
async def create_road_restriction(
    payload: AdminRoadRestrictionPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import RoadRestriction
    row = RoadRestriction(
        title=payload.title,
        description=payload.description,
        restriction_type=payload.restriction_type,
        latitude=payload.latitude,
        longitude=payload.longitude,
        radius_meters=payload.radius_meters,
        starts_at=payload.starts_at or datetime.now(timezone.utc),
        ends_at=payload.ends_at
    )
    db.add(row)
    await db.commit()
    await db.refresh(row)
    return {"created": True, "id": row.id, "title": row.title}

# 4. Surge Pricing Config
@router.post("/surge/rules")
async def create_surge_rule(
    payload: SurgeRulePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    zone_id = payload.zone_id
    if zone_id is None and payload.zone_name:
        from sqlalchemy import select
        from backend.domain.models import GeofenceZone
        zone = (await db.execute(select(GeofenceZone).where(GeofenceZone.name == payload.zone_name))).scalar_one_or_none()
        zone_id = zone.id if zone else None
    rule = await AdminAdvancedService.create_surge_rule(
        session=db,
        zone_id=zone_id,
        category_id=payload.category_id,
        multiplier=payload.multiplier,
        reason=payload.reason,
        admin_user_id=admin.id,
        starts_at=payload.starts_at,
        ends_at=payload.ends_at,
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
    result = await AdminGovernanceService(db).save_geofence(admin.id, None, {
        "name": payload.name, "zone_type": payload.zone_type, "coordinates": payload.coordinates,
        "surge_multiplier": payload.surge_multiplier, "is_active": True,
    })
    return {**result, "status": "CREATED", "zone_id": result["id"]}

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
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    rule = await AdminFinanceSupportService(db).create_commission_draft(
        admin.id, payload.category_id, payload.commission_percentage, None, payload.reason)
    return {"status": "PENDING_APPROVAL", "rule_id": rule["id"], "version": rule["version"], "commission_percentage": payload.commission_percentage}

@router.get("/commissions")
async def list_commission_rules(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    rules = await AdminFinanceSupportService(db).commission_rules()
    return [{**r, "commission_percentage": r["commission_percent"]} for r in rules]

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

# ----------------- Part 4 Disputes, Refunds & Corporate (Features 89, 94, 95, 100) -----------------
class ResolveDisputePayload(BaseModel):
    resolution: str  # APPROVE_REFUND or REJECT
    resolution_notes: str
    refund_amount_cents: Optional[int] = None

@router.get("/disputes")
async def list_all_disputes(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import FareDispute
    stmt = select(FareDispute).order_by(desc(FareDispute.created_at))
    res = await db.execute(stmt)
    disputes = res.scalars().all()
    return [
        {
            "id": d.id,
            "ride_id": d.ride_id,
            "customer_id": d.customer_id,
            "dispute_reason": d.dispute_reason,
            "claimed_amount_rupees": round(d.claimed_amount_cents / 100, 2),
            "customer_comment": d.customer_comment,
            "status": d.status,
            "admin_resolution_notes": d.admin_resolution_notes,
            "created_at": d.created_at.isoformat()
        }
        for d in disputes
    ]

@router.post("/disputes/{dispute_id}/resolve")
async def resolve_fare_dispute(
    dispute_id: str,
    payload: ResolveDisputePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    service = SettlementAdvancedService(db)
    return await service.admin_resolve_dispute(
        dispute_id=dispute_id,
        admin_id=admin.id,
        resolution=payload.resolution,
        resolution_notes=payload.resolution_notes,
        refund_amount_cents=payload.refund_amount_cents
    )

@router.get("/refunds")
async def list_all_refunds(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import PaymentRefund
    stmt = select(PaymentRefund).order_by(desc(PaymentRefund.created_at))
    res = await db.execute(stmt)
    refunds = res.scalars().all()
    return [
        {
            "id": r.id,
            "payment_id": r.payment_id,
            "ride_id": r.ride_id,
            "customer_id": r.customer_id,
            "amount_rupees": round(r.amount_cents / 100, 2),
            "is_partial": r.is_partial,
            "status": r.status,
            "arn_reference": r.arn_reference,
            "reason": r.reason,
            "created_at": r.created_at.isoformat()
        }
        for r in refunds
    ]

@router.get("/corporate-billings")
async def list_corporate_billings(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    from backend.domain.models import Ride
    stmt = select(Ride).where(Ride.payment_method == "CORPORATE_BILLING").order_by(desc(Ride.requested_at))
    res = await db.execute(stmt)
    rides = res.scalars().all()
    return [
        {
            "ride_id": r.id,
            "cost_center": r.business_cost_center or "CC-CORP-EXEC",
            "project_code": r.business_project_code or "PRJ-GLOBAL",
            "tax_id": r.business_tax_id or "27AAACG1234F1Z8",
            "billing_ref": r.corporate_billing_ref or f"CORP-{r.id[:8].upper()}",
            "amount_rupees": round((r.final_fare_cents or r.estimated_fare_cents) / 100, 2),
            "passenger_name": r.passenger_name or "Executive Passenger",
            "date": r.requested_at.isoformat()
        }
        for r in rides
    ]


