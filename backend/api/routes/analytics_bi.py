"""
FastAPI Routes for Analytics, BI Reporting, Governance Auditing and DPDP Privacy.
Features 376 - 400.
"""
from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, Query, HTTPException, Request, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_admin, get_current_user
from backend.domain.models import User
from backend.application.services.analytics_bi_service import AnalyticsBiService

router = APIRouter(prefix="", tags=["Analytics, BI & Governance"])


# -----------------------------------------------------------------------------
# Pydantic Request Models
# -----------------------------------------------------------------------------
class ExportDatasetPayload(BaseModel):
    dataset: str = Field(..., example="RIDES")  # RIDES, CHAUFFEURS, FINANCE_SETTLEMENTS, SUMMARY
    format: str = Field(default="CSV", example="CSV")  # CSV, JSON
    filters: Optional[Dict[str, Any]] = None

class CreateScheduledReportPayload(BaseModel):
    report_name: str = Field(..., min_length=3, max_length=128)
    report_type: str = Field(default="RIDE_FUNNEL")
    custom_report_id: Optional[str] = None
    frequency: str = Field(default="WEEKLY")  # DAILY, WEEKLY, MONTHLY
    delivery_channel: str = Field(default="EMAIL")  # EMAIL, WEBHOOK, SFTP
    destination: str = Field(..., min_length=3)
    export_format: str = Field(default="CSV")
    filters: Optional[Dict[str, Any]] = None

class CreateCustomReportPayload(BaseModel):
    title: str = Field(..., min_length=3, max_length=128)
    description: Optional[str] = None
    domain: str = Field(default="RIDES")  # RIDES, DRIVERS, FINANCE, SAFETY, SUPPORT
    metrics: List[str] = Field(default_factory=lambda: ["total_requests", "completed_count"])
    dimensions: List[str] = Field(default_factory=lambda: ["zone_name", "date"])
    filters: Optional[Dict[str, Any]] = None
    time_grouping: str = Field(default="DAILY")

class ActionJustificationPayload(BaseModel):
    action_code: str = Field(..., min_length=3)
    target_resource_type: str = Field(..., min_length=2)
    target_resource_id: str = Field(..., min_length=1)
    justification_notes: str = Field(..., min_length=15, description="Substantive audit justification")
    ticket_incident_ref: str = Field(..., min_length=3, description="Incident or Jira ticket ref")

class SensitiveAccessLogPayload(BaseModel):
    record_type: str = Field(...)  # DRIVER_BANK_DETAILS, CUSTOMER_PHONE, GPS_RAW_TELEMETRY
    record_identifier: str = Field(...)
    justification_reason: str = Field(..., min_length=5)
    ticket_ref: Optional[str] = None
    target_user_id: Optional[str] = None

class UpdateRetentionPolicyPayload(BaseModel):
    new_ttl_days: int = Field(..., ge=1)
    justification: str = Field(..., min_length=15)

class RetentionPurgePayload(BaseModel):
    dry_run: bool = True

class PrivacyRequestSubmissionPayload(BaseModel):
    request_type: str = Field(..., example="DATA_EXPORT_PORTABILITY")  # DATA_EXPORT_PORTABILITY, RIGHT_TO_ERASURE, RECTIFICATION_CORRECTION, RESTRICT_PROCESSING
    details: Optional[str] = None

class PrivacyRequestReviewPayload(BaseModel):
    new_status: str = Field(...)  # UNDER_PROCESSING, FULFILLED_EXPORTED, FULFILLED_ERASED, REJECTED_LEGAL_OVERRIDE
    resolution_notes: str = Field(..., min_length=5)


# -----------------------------------------------------------------------------
# Part 16 Operational & Performance Analytics (Features 376 - 384)
# -----------------------------------------------------------------------------

@router.get("/admin/analytics/funnel")
async def get_ride_funnel_conversion(
    days: int = Query(30, ge=1, le=365),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 376: Ride funnel conversion dashboard."""
    return await AnalyticsBiService(db).get_ride_funnel_conversion(days)


@router.get("/admin/analytics/cancellations")
async def get_cancellation_analytics(
    days: int = Query(30, ge=1, le=365),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 377: Booking cancellation analytics."""
    return await AnalyticsBiService(db).get_cancellation_analytics(days)


@router.get("/admin/analytics/driver-acceptance")
async def get_driver_acceptance_analytics(
    days: int = Query(30, ge=1, le=365),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 378: Driver acceptance analytics."""
    return await AnalyticsBiService(db).get_driver_acceptance_analytics(days)


@router.get("/admin/analytics/driver-utilization")
async def get_driver_utilization_analytics(
    days: int = Query(30, ge=1, le=365),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 379: Driver utilization analytics."""
    return await AnalyticsBiService(db).get_driver_utilization_analytics(days)


@router.get("/admin/analytics/eta-accuracy")
async def get_eta_accuracy_report(
    days: int = Query(30, ge=1, le=365),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 380: Pickup ETA accuracy report."""
    return await AnalyticsBiService(db).get_pickup_eta_accuracy_report(days)


@router.get("/admin/analytics/fare-accuracy")
async def get_fare_accuracy_report(
    days: int = Query(30, ge=1, le=365),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 381: Fare estimate accuracy report."""
    return await AnalyticsBiService(db).get_fare_estimate_accuracy_report(days)


@router.get("/admin/analytics/zone-demand-forecast")
async def get_zone_demand_forecast(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 382: Demand forecast by zone."""
    return await AnalyticsBiService(db).get_demand_forecast_by_zone()


@router.get("/admin/analytics/zone-supply-forecast")
async def get_zone_supply_forecast(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 383: Supply forecast by zone."""
    return await AnalyticsBiService(db).get_supply_forecast_by_zone()


@router.get("/admin/analytics/shared-rides")
async def get_shared_matching_report(
    days: int = Query(30, ge=1, le=365),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 384: Shared ride matching performance report."""
    return await AnalyticsBiService(db).get_shared_ride_matching_performance(days)


# -----------------------------------------------------------------------------
# Part 16 Business & Financial BI (Features 385 - 392)
# -----------------------------------------------------------------------------

@router.get("/admin/analytics/customer-cohorts")
async def get_customer_retention_cohorts(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 385: Customer retention cohorts."""
    return await AnalyticsBiService(db).get_customer_retention_cohorts()


@router.get("/admin/analytics/driver-cohorts")
async def get_driver_retention_cohorts(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 386: Driver retention cohorts."""
    return await AnalyticsBiService(db).get_driver_retention_cohorts()


@router.get("/admin/analytics/promo-effectiveness")
async def get_promo_campaign_effectiveness(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 387: Promo campaign effectiveness."""
    return await AnalyticsBiService(db).get_promo_campaign_effectiveness()


@router.get("/admin/analytics/referral-fraud")
async def get_referral_fraud_analytics(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 388: Referral fraud analytics."""
    return await AnalyticsBiService(db).get_referral_fraud_analytics()


@router.get("/admin/analytics/payment-success")
async def get_payment_success_dashboard(
    days: int = Query(30, ge=1, le=365),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 389: Payment success rate dashboard."""
    return await AnalyticsBiService(db).get_payment_success_rate_dashboard(days)


@router.get("/admin/analytics/refunds")
async def get_refund_rate_dashboard(
    days: int = Query(30, ge=1, le=365),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 390: Refund rate dashboard."""
    return await AnalyticsBiService(db).get_refund_rate_dashboard(days)


@router.get("/admin/analytics/support-sla")
async def get_support_resolution_time_report(
    days: int = Query(30, ge=1, le=365),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 391: Support resolution time report."""
    return await AnalyticsBiService(db).get_support_resolution_time_report(days)


@router.get("/admin/analytics/safety-trends")
async def get_safety_incident_trends(
    days: int = Query(30, ge=1, le=365),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 392: Safety incident trend report."""
    return await AnalyticsBiService(db).get_safety_incident_trends(days)


# -----------------------------------------------------------------------------
# Part 16 Custom Reports, Scheduling & Permissioned Export (Features 393 - 395)
# -----------------------------------------------------------------------------

@router.post("/admin/analytics/export")
async def export_dataset(
    payload: ExportDatasetPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 393: Data export with permission checks and auto-redaction."""
    # Derive active admin permissions
    permissions = ["analytics:export", "finance:export", "pii:export", "admin.access"]
    return await AnalyticsBiService(db).export_dataset_with_permissions(
        dataset=payload.dataset,
        user_permissions=permissions,
        admin_user_id=admin.id,
        export_format=payload.format,
        filters=payload.filters
    )


@router.get("/admin/reports/scheduled")
async def list_scheduled_reports(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 394: List scheduled report deliveries."""
    return await AnalyticsBiService(db).list_scheduled_reports()


@router.post("/admin/reports/scheduled")
async def create_scheduled_report(
    payload: CreateScheduledReportPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 394: Create scheduled report delivery."""
    return await AnalyticsBiService(db).create_scheduled_report(payload.dict(), admin.id)


@router.get("/admin/reports/custom")
async def list_custom_reports(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 395: Custom report builder templates."""
    return await AnalyticsBiService(db).list_custom_reports()


@router.post("/admin/reports/custom")
async def create_custom_report(
    payload: CreateCustomReportPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 395: Save custom report builder template."""
    return await AnalyticsBiService(db).create_custom_report(payload.dict(), admin.id)


@router.post("/admin/reports/custom/{config_id}/execute")
async def execute_custom_report(
    config_id: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 395: Execute custom report builder."""
    return await AnalyticsBiService(db).execute_custom_report(config_id)


# -----------------------------------------------------------------------------
# Part 16 Auditing, Justification, Sensitive Records & DPDP (Features 396 - 400)
# -----------------------------------------------------------------------------

@router.get("/admin/governance/audit-logs")
async def get_filtered_audit_logs(
    actor_role: Optional[str] = Query(None),
    action: Optional[str] = Query(None),
    resource: Optional[str] = Query(None),
    search: Optional[str] = Query(None),
    page: int = Query(1, ge=1),
    limit: int = Query(25, ge=1, le=100),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 396: Audit log filters and export."""
    return await AnalyticsBiService(db).get_filtered_audit_logs(
        actor_role=actor_role,
        action=action,
        resource=resource,
        search_query=search,
        page=page,
        limit=limit
    )


@router.post("/admin/governance/justify-action")
async def record_action_justification(
    payload: ActionJustificationPayload,
    request: Request,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 397: Admin action justification requirement."""
    client_ip = request.client.host if request.client else None
    return await AnalyticsBiService(db).record_action_justification(
        admin_user_id=admin.id,
        action_code=payload.action_code,
        target_resource_type=payload.target_resource_type,
        target_resource_id=payload.target_resource_id,
        justification_notes=payload.justification_notes,
        ticket_incident_ref=payload.ticket_incident_ref,
        ip_address=client_ip
    )


@router.get("/admin/governance/sensitive-access")
async def get_sensitive_record_access_history(
    limit: int = Query(50, ge=1, le=200),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 398: Sensitive record access history."""
    return await AnalyticsBiService(db).get_sensitive_record_access_history(limit)


@router.post("/admin/governance/sensitive-access")
async def log_sensitive_record_access(
    payload: SensitiveAccessLogPayload,
    request: Request,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 398: Audited unmasking of sensitive record."""
    client_ip = request.client.host if request.client else None
    user_agent = request.headers.get("user-agent")
    return await AnalyticsBiService(db).log_sensitive_record_access(
        admin_user_id=admin.id,
        record_type=payload.record_type,
        record_identifier=payload.record_identifier,
        justification_reason=payload.justification_reason,
        ticket_ref=payload.ticket_ref,
        target_user_id=payload.target_user_id,
        ip_address=client_ip,
        user_agent=user_agent
    )


@router.get("/admin/governance/retention-policies")
async def list_retention_policies(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 399: Data retention policy controls."""
    return await AnalyticsBiService(db).list_retention_policies()


@router.put("/admin/governance/retention-policies/{category}")
async def update_retention_policy(
    category: str,
    payload: UpdateRetentionPolicyPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 399: Update TTL for data category with statutory constraint checks."""
    return await AnalyticsBiService(db).update_retention_policy(
        category=category,
        new_ttl_days=payload.new_ttl_days,
        admin_user_id=admin.id,
        justification=payload.justification
    )


@router.post("/admin/governance/retention-policies/{category}/purge")
async def run_retention_purge(
    category: str,
    payload: RetentionPurgePayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 399: Execute data retention purge run."""
    return await AnalyticsBiService(db).run_retention_purge(category, dry_run=payload.dry_run)


@router.get("/admin/privacy/dsar-requests")
async def list_privacy_requests(
    status: Optional[str] = Query(None),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 400: Privacy request processing dashboard (DPDP / GDPR DSAR)."""
    return await AnalyticsBiService(db).list_privacy_requests(status_filter=status)


@router.post("/admin/privacy/dsar-requests/{request_id}/review")
async def review_privacy_request(
    request_id: str,
    payload: PrivacyRequestReviewPayload,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Feature 400: Review and fulfill privacy request."""
    return await AnalyticsBiService(db).review_privacy_request(
        request_id=request_id,
        new_status=payload.new_status,
        resolution_notes=payload.resolution_notes,
        admin_user_id=admin.id
    )


# -----------------------------------------------------------------------------
# Customer & Chauffeur User Privacy Endpoints (Mobile App DSAR Submission)
# -----------------------------------------------------------------------------

@router.post("/privacy/dsar-requests")
async def submit_user_privacy_request(
    payload: PrivacyRequestSubmissionPayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Feature 400: Customer or Chauffeur self-service DPDP privacy request."""
    return await AnalyticsBiService(db).submit_privacy_request(
        user_id=user.id,
        user_role=user.role,
        request_type=payload.request_type,
        details=payload.details
    )
