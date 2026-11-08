import uuid
import secrets
from datetime import datetime, timezone
from typing import Optional, List
from sqlalchemy import (
    String, Integer, Float, Boolean, DateTime, ForeignKey, Text, Index, UniqueConstraint
)
from sqlalchemy.orm import Mapped, mapped_column, relationship
from backend.infrastructure.database.session import Base
from backend.domain.models.models import generate_uuid, utc_now

# =====================================================================
# ENTERPRISE PERSISTENT MODELS (REPLACED PLACEHOLDER STUBS)
# All models are genuine SQLAlchemy Declarative entities.
# =====================================================================

class ABTestExperiment(Base):
    __tablename__ = "ab_test_experiments"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    experiment_key: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    variants_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    rollout_status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)


class AddressQualityFeedback(Base):
    __tablename__ = "address_quality_feedback"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    rating: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    issue_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    provider_mode: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    coordinates_verified: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class AdminCalendarEvent(Base):
    __tablename__ = "admin_calendar_events"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[Optional[str]] = mapped_column(String(120), nullable=True)
    event_date: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    event_type: Mapped[Optional[str]] = mapped_column(String(24), nullable=True)
    zone_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    notes: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    created_by: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class AdminRecordNote(Base):
    __tablename__ = "admin_record_notes"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    subject_type: Mapped[Optional[str]] = mapped_column(String(16), nullable=True)
    subject_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    author_user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    note: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class AdminReviewTemplate(Base):
    __tablename__ = "admin_review_templates"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[Optional[str]] = mapped_column(String(80), nullable=True)
    reason: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    is_active: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    created_by: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class AdminWalletAdjustmentRequest(Base):
    __tablename__ = "admin_wallet_adjustment_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    amount_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    adjustment_type: Mapped[Optional[str]] = mapped_column(String(12), nullable=True)
    reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    requested_by: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    first_approved_by: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    second_approved_by: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    reviewed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    applied_transaction_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class AdministrativeAuditLog(Base):
    __tablename__ = "administrative_audit_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    admin_user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    action: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    target_resource: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    target_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    changes_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    ip_address: Mapped[Optional[str]] = mapped_column(String(45), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class AirportTransferPackage(Base):
    __tablename__ = "airport_transfer_packages"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    airport_code: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    package_title: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    category_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    flat_fare_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    meets_and_greets_included: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)


class ApiErrorCatalogEntry(Base):
    __tablename__ = "api_error_catalog"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    error_code: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    http_status_code: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    title: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    resolution_guidance: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    category: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    is_active: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class ApiVersionRecord(Base):
    __tablename__ = "api_version_records"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    version_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    app_type: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    min_supported_version: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    latest_version: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    deprecation_message: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class BackgroundJob(Base):
    __tablename__ = "background_jobs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    job_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    queue_name: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    payload_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    attempt_count: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    max_retries: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    last_error: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    next_retry_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    scheduled_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    started_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class BackupPolicy(Base):
    __tablename__ = "backup_policies"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    policy_name: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    backup_type: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    retention_days: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    encryption_algorithm: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    key_rotation_status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    kms_key_arn: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    last_backup_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    next_backup_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    is_active: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class BusinessApprovalRequest(Base):
    __tablename__ = "business_approval_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    employee_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    approver_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    cost_center: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    project_code: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    justification: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class ChauffeurServiceAddon(Base):
    __tablename__ = "chauffeur_service_addons"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    addon_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    title: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    price_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    category_compatibility: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)


class CommissionRule(Base):
    __tablename__ = "commission_rules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    category_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    version: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    platform_commission_percentage: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    driver_share_percentage: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    effective_from: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    effective_until: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    is_active: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    approval_status: Mapped[str] = mapped_column(String(20), default="ACTIVE")
    approval_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_by: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class CorporateAccount(Base):
    __tablename__ = "corporate_accounts"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    company_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    tax_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    billing_email: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    credit_limit_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    balance_due_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class CorporateInvoice(Base):
    __tablename__ = "corporate_invoices"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    invoice_number: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    corporate_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    billing_period: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    total_amount_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    pdf_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    due_date: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class CorporateTravelPolicy(Base):
    __tablename__ = "corporate_travel_policies"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    corporate_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    policy_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    allowed_categories_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    max_fare_per_ride_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    curfew_start_time: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    curfew_end_time: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    is_active: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class CustomerRidePass(Base):
    __tablename__ = "customer_ride_passes"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    pass_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    rides_remaining: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    valid_from: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    valid_until: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class CustomerSubscription(Base):
    __tablename__ = "customer_subscriptions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    plan_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    rides_remaining: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    starts_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    expires_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)


class DatabaseMigrationVerification(Base):
    __tablename__ = "db_migration_verifications"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    revision_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    down_revision_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    rollback_verified: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    verification_status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    verified_by: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    verified_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class DependencyVulnerabilityReport(Base):
    __tablename__ = "dependency_vulnerability_reports"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    package_name: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    installed_version: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    fixed_version: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    cve_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    severity: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    advisory_url: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    detected_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class DisasterRecoveryPlan(Base):
    __tablename__ = "disaster_recovery_plans"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    plan_code: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    primary_region: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    dr_region: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    replication_status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    failover_readiness: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    last_failover_test_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class DistributedTraceSpan(Base):
    __tablename__ = "distributed_trace_spans"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    trace_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    span_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    parent_span_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    operation_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    service_name: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    duration_ms: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    http_method: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    http_status: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    route_path: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    correlation_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    error_message: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    timestamp: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class DriverDispatchPreferences(Base):
    __tablename__ = "driver_dispatch_preferences"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    minimum_fare_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    maximum_pickup_distance_km: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    vehicle_categories_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    navigation_provider: Mapped[Optional[str]] = mapped_column(String(24), nullable=True)
    voice_guidance_enabled: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    reroute_on_closure_enabled: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class DriverRatingAppeal(Base):
    __tablename__ = "driver_rating_appeals"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    rating_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    driver_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(24), nullable=True)
    reviewer_user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    reviewer_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)
    resolved_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class DriverSafetyPreferences(Base):
    __tablename__ = "driver_safety_preferences"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    share_trip_location_with_customer: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    privacy_notice_acknowledged_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class DriverScheduleAvailability(Base):
    __tablename__ = "driver_schedule_availabilities"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    day_of_week: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    start_time: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    end_time: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    is_active: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    is_rest_day: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class DriverTrainingModule(Base):
    __tablename__ = "driver_training_modules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    module_code: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)
    title: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    content_markdown: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    video_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    estimated_duration_minutes: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    sort_order: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    is_mandatory: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class DriverTrainingProgress(Base):
    __tablename__ = "driver_training_progress"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    module_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    is_completed: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    assessment_score_percentage: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    assessment_passed: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class DriverVehicleInspectionPhoto(Base):
    __tablename__ = "driver_vehicle_inspection_photos"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    vehicle_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    photo_type: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    storage_path: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    mime_type: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    file_size_bytes: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    file_hash_sha256: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class DriverVehicleInspection(Base):
    __tablename__ = "driver_vehicle_inspections"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    vehicle_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    inspection_status: Mapped[Optional[str]] = mapped_column(String(30), nullable=True)
    checklist_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    photo_front_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_rear_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_left_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_right_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_interior_front_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_interior_rear_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    inspector_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    inspected_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class EmployeeProfile(Base):
    __tablename__ = "employee_profiles"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    corporate_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    cost_center: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    max_ride_fare_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    requires_approval: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class EventTransportWorkspace(Base):
    __tablename__ = "event_transport_workspaces"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    event_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    venue_address: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    event_date: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    allocated_vehicles_count: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    total_guests: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class FeatureFlag(Base):
    __tablename__ = "feature_flags"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    flag_key: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    is_enabled: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    rollout_percentage: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    target_roles_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class FeatureRequestVote(Base):
    __tablename__ = "feature_request_votes"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    feature_key: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    title: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    category: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    upvote_count: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)


class FleetSubscriptionPlan(Base):
    __tablename__ = "fleet_subscription_plans"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    plan_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    monthly_price_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    included_rides_count: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    category_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    is_active: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class GeofenceZoneAudit(Base):
    __tablename__ = "geofence_zone_audits"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    zone_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    admin_user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    action: Mapped[Optional[str]] = mapped_column(String(24), nullable=True)
    previous_state_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    new_state_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class HourlyChauffeurBooking(Base):
    __tablename__ = "hourly_chauffeur_bookings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    requested_hours: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    hourly_rate_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    kilometers_included: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class IdempotencyRecord(Base):
    __tablename__ = "idempotency_records"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    idempotency_key: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    route_path: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    request_hash: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    response_code: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    response_body_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)
    expires_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class IntercityProduct(Base):
    __tablename__ = "intercity_products"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    route_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    origin_city: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    destination_city: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    distance_km: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    fixed_fare_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    toll_included: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)


class InvoiceCorrectionRequest(Base):
    __tablename__ = "invoice_correction_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    requested_changes_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    original_values_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    requested_by: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    reviewed_by: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    reviewed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    review_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class LocalizationCatalog(Base):
    __tablename__ = "localization_catalog"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    string_key: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    locale: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    category: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    translated_text: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class LoyaltyPartnerCatalog(Base):
    __tablename__ = "loyalty_partner_catalog"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    partner_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    offer_title: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    required_points: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    promo_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    logo_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    is_active: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)


class MobileCrashReport(Base):
    __tablename__ = "mobile_crash_reports"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    app_type: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    app_version: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    device_model: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    os_version: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    report_type: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    stack_trace: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    error_message: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class MultiCityOperatingConfig(Base):
    __tablename__ = "multi_city_operating_configs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    city_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    city_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    country: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    timezone: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    tax_rate_percentage: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    min_fare_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    is_active: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)


class NotificationConsentAudit(Base):
    __tablename__ = "notification_consent_audits"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    source: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    changes_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class NotificationDeliveryAttempt(Base):
    __tablename__ = "notification_delivery_attempts"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    notification_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    channel: Mapped[Optional[str]] = mapped_column(String(16), nullable=True)
    attempt_number: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    provider_message_id: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    error_code: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    error_message: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    attempted_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class PartnerAccount(Base):
    __tablename__ = "partner_accounts"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    partner_type: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    partner_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    api_key_hash: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    commission_rate_pct: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    referral_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class PartnerBooking(Base):
    __tablename__ = "partner_bookings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    partner_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    guest_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    guest_phone: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    commission_earned_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    attribution_status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class PassengerOccupancyEvent(Base):
    __tablename__ = "passenger_occupancy_events"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    pooled_trip_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    booking_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    event_type: Mapped[Optional[str]] = mapped_column(String(40), nullable=True)
    occupancy_before: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    occupancy_after: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    actor_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    recorded_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class PaymentEvent(Base):
    __tablename__ = "payment_events"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    payment_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    event_type: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    payload_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    recorded_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class PaymentWebhookInbox(Base):
    __tablename__ = "payment_webhook_inbox"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    provider_name: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    event_digest: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    payload_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    signature_verified: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    attempts: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    last_error: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    received_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)
    last_attempt_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class PrepaidRideBundle(Base):
    __tablename__ = "prepaid_ride_bundles"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    bundle_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    title: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    ride_count: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    price_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    discount_percentage: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    is_active: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)


class ProductFeedbackAnalytics(Base):
    __tablename__ = "product_feedback_analytics"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    rating_score: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    feedback_category: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    comments: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    sentiment: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class ProviderHealthConfig(Base):
    __tablename__ = "provider_health_configs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    provider_name: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    provider_type: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    is_healthy: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    circuit_breaker_state: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    consecutive_failures: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    failover_provider_name: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    quota_used_percentage: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    last_health_check_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class RedisNamespaceConfig(Base):
    __tablename__ = "redis_namespace_configs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    namespace: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    default_ttl_seconds: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    keys_count: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    memory_bytes: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    eviction_policy: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class RestoreDrillSchedule(Base):
    __tablename__ = "restore_drill_schedules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    drill_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    target_region: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    scheduled_date: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    completed_date: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    rto_minutes_achieved: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    rpo_minutes_achieved: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    conducted_by: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class RideDestinationChangeRequest(Base):
    __tablename__ = "ride_destination_change_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    requested_by_user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    destination_address: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    destination_latitude: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    destination_longitude: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    proposed_fare_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    proposed_distance_km: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    proposed_duration_minutes: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    fare_difference_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    customer_responded_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    driver_responded_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    rejection_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class RideOperationalEvent(Base):
    __tablename__ = "ride_operational_events"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    actor_user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    action: Mapped[Optional[str]] = mapped_column(String(40), nullable=True)
    details_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class RidePassRedemption(Base):
    __tablename__ = "ride_pass_redemptions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_pass_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    discount_applied_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    redeemed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class RidePass(Base):
    __tablename__ = "ride_passes"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    price_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    validity_days: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    total_rides: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    discount_percentage: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    max_discount_per_ride_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    is_active: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class ScheduledJobMonitor(Base):
    __tablename__ = "scheduled_job_monitors"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    job_key: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    job_title: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    cron_schedule: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    last_run_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    next_run_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    last_status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    last_duration_ms: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    is_enabled: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)


class SecretRotationWorkflow(Base):
    __tablename__ = "secret_rotation_workflows"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    secret_name: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    vault_path: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    current_version: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    auto_rotate_days: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    last_rotated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    next_rotation_due: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class SupportResponseTemplate(Base):
    __tablename__ = "support_response_templates"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    category: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)
    language_code: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    body: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    is_active: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    created_by: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class UserNotificationSettings(Base):
    __tablename__ = "user_notification_settings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    push_enabled: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    email_enabled: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    sms_enabled: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    quiet_hours_enabled: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True)
    quiet_start: Mapped[Optional[str]] = mapped_column(String(5), nullable=True)
    quiet_end: Mapped[Optional[str]] = mapped_column(String(5), nullable=True)
    timezone_name: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    language_code: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class WhiteLabelTenantConfig(Base):
    __tablename__ = "white_label_tenant_configs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    tenant_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    brand_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    primary_color: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    secondary_color: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    custom_domain: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    logo_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)


class AccountDeletionRequest(Base):
    __tablename__ = "account_deletion_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    status: Mapped[str] = mapped_column(String(32), default="PENDING_GRACE_PERIOD", index=True)
    cooldown_days_total: Mapped[int] = mapped_column(Integer, default=30)
    requested_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    scheduled_purge_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    cancellation_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    cancelled_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class AdminActionJustification(Base):
    __tablename__ = "admin_action_justifications"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    admin_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    action_type: Mapped[str] = mapped_column(String(64), nullable=False)
    target_resource: Mapped[str] = mapped_column(String(64), nullable=False)
    target_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    justification_reason: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class CustomReportConfig(Base):
    __tablename__ = "custom_report_configs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    domain: Mapped[str] = mapped_column(String(64), default="RIDES")
    selected_metrics_json: Mapped[str] = mapped_column(Text, default="[]")
    selected_dimensions_json: Mapped[str] = mapped_column(Text, default="[]")
    filter_conditions_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    time_grouping: Mapped[str] = mapped_column(String(32), default="DAILY")
    created_by_admin_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class CustomerMilestoneBadge(Base):
    __tablename__ = "customer_milestone_badges"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    badge_code: Mapped[str] = mapped_column(String(64), nullable=False)
    badge_name: Mapped[str] = mapped_column(String(128), nullable=False)
    badge_icon: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    unlocked_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class CustomerSurvey(Base):
    __tablename__ = "customer_surveys"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True)
    survey_type: Mapped[str] = mapped_column(String(64), default="POST_RIDE")
    score: Mapped[int] = mapped_column(Integer, default=5)
    dimensions_json: Mapped[str] = mapped_column(Text, default="{}")
    comments: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class DataExportArchive(Base):
    __tablename__ = "data_export_archives"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    status: Mapped[str] = mapped_column(String(32), default="READY")
    archive_format: Mapped[str] = mapped_column(String(16), default="JSON")
    file_size_bytes: Mapped[int] = mapped_column(Integer, default=0)
    download_url: Mapped[str] = mapped_column(String(512), nullable=False)
    payload_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    storage_path: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class DataRetentionPolicy(Base):
    __tablename__ = "data_retention_policies"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    data_category: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    retention_days: Mapped[int] = mapped_column(Integer, default=365)
    auto_purge_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    legal_basis: Mapped[str] = mapped_column(String(128), default="DPDP_ACT_2023")
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)


class DriverBatteryOptimizationSetting(Base):
    __tablename__ = "driver_battery_optimization_settings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), unique=True, nullable=False)
    mode: Mapped[str] = mapped_column(String(32), default="BALANCED")
    throttle_ping_background: Mapped[bool] = mapped_column(Boolean, default=True)
    high_accuracy_only_when_moving: Mapped[bool] = mapped_column(Boolean, default=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class DriverBlock(Base):
    __tablename__ = "driver_blocks"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True)
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class DriverFinancialSupportRequest(Base):
    __tablename__ = "driver_financial_support_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    support_type: Mapped[str] = mapped_column(String(64), nullable=False)
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    status: Mapped[str] = mapped_column(String(32), default="PENDING")
    reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class DriverOperatingExpense(Base):
    __tablename__ = "driver_operating_expenses"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    category: Mapped[str] = mapped_column(String(64), nullable=False)
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class DriverPayoutReconciliation(Base):
    __tablename__ = "driver_payout_reconciliations"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    period_start: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    period_end: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    total_earned_cents: Mapped[int] = mapped_column(Integer, default=0)
    total_settled_cents: Mapped[int] = mapped_column(Integer, default=0)
    variance_cents: Mapped[int] = mapped_column(Integer, default=0)
    status: Mapped[str] = mapped_column(String(32), default="MATCHED")
    generated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class DriverPayoutSchedule(Base):
    __tablename__ = "driver_payout_schedules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), unique=True, nullable=False)
    schedule_type: Mapped[str] = mapped_column(String(32), default="DAILY")
    min_threshold_cents: Mapped[int] = mapped_column(Integer, default=50000)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class GiftCard(Base):
    __tablename__ = "gift_cards"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    code: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    initial_value_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    remaining_value_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    purchased_by_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    recipient_email: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    recipient_phone: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    sender_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    message: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class HelpArticleRating(Base):
    __tablename__ = "help_article_ratings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    article_id: Mapped[str] = mapped_column(String(36), ForeignKey("help_articles.id"), nullable=False, index=True)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    was_helpful: Mapped[bool] = mapped_column(Boolean, nullable=False)
    feedback_text: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class HelpArticle(Base):
    __tablename__ = "help_articles"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    slug: Mapped[str] = mapped_column(String(128), unique=True, index=True, nullable=False)
    category: Mapped[str] = mapped_column(String(64), nullable=False)
    content_markdown: Mapped[str] = mapped_column(Text, nullable=False)
    is_published: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)


class LocationRetentionSetting(Base):
    __tablename__ = "location_retention_settings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    retention_days: Mapped[int] = mapped_column(Integer, default=30)
    anonymize_after_days: Mapped[int] = mapped_column(Integer, default=90)
    allow_historical_playback: Mapped[bool] = mapped_column(Boolean, default=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class LoyaltyPointsLedger(Base):
    __tablename__ = "loyalty_points_ledger"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    transaction_type: Mapped[str] = mapped_column(String(50), nullable=False)
    points_change: Mapped[int] = mapped_column(Integer, nullable=False)
    balance_after: Mapped[int] = mapped_column(Integer, nullable=False)
    reference_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    description: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class PersonalizedOffer(Base):
    __tablename__ = "personalized_offers"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    tagline: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    offer_type: Mapped[str] = mapped_column(String(64), default="DISCOUNT")
    discount_percent: Mapped[float] = mapped_column(Float, default=10.0)
    max_discount_cents: Mapped[int] = mapped_column(Integer, default=10000)
    promo_code: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    badge_label: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    days: Mapped[int] = mapped_column(Integer, default=7)
    starts_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    is_claimed: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class PrivacyDataSubjectRequest(Base):
    __tablename__ = "privacy_data_subject_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    request_number: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    user_role: Mapped[str] = mapped_column(String(20), default="CUSTOMER")
    request_type: Mapped[str] = mapped_column(String(64), nullable=False)
    details: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    status: Mapped[str] = mapped_column(String(32), default="UNDER_PROCESSING")
    resolution_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    assigned_dpo_admin_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    fulfilled_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class RecurringRideSchedule(Base):
    __tablename__ = "recurring_ride_schedules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    frequency: Mapped[str] = mapped_column(String(32), default="WEEKLY")
    days_of_week: Mapped[str] = mapped_column(String(64), default="MON,TUE,WED,THU,FRI")
    pickup_time_str: Mapped[str] = mapped_column(String(10), nullable=False)
    pickup_address: Mapped[str] = mapped_column(String(255), nullable=False)
    pickup_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    pickup_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_address: Mapped[str] = mapped_column(String(255), nullable=False)
    destination_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    vehicle_category_id: Mapped[str] = mapped_column(String(36), ForeignKey("vehicle_categories.id"), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class ScheduledReportDelivery(Base):
    __tablename__ = "scheduled_report_deliveries"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    report_name: Mapped[str] = mapped_column(String(128), nullable=False)
    recipients_json: Mapped[str] = mapped_column(Text, default="[]")
    frequency: Mapped[str] = mapped_column(String(32), default="WEEKLY")
    created_by_admin_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    next_run_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class SensitiveRecordAccessLog(Base):
    __tablename__ = "sensitive_record_access_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    actor_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    record_type: Mapped[str] = mapped_column(String(64), nullable=False)
    record_id: Mapped[str] = mapped_column(String(64), nullable=False)
    purpose: Mapped[str] = mapped_column(String(255), nullable=False)
    accessed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class ServiceIncidentAdvisory(Base):
    __tablename__ = "service_incident_advisories"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False)
    affected_service: Mapped[str] = mapped_column(String(64), nullable=False)
    severity: Mapped[str] = mapped_column(String(32), default="MEDIUM")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class ServiceRecoveryCredit(Base):
    __tablename__ = "service_recovery_credits"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True)
    credit_amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    reason_category: Mapped[str] = mapped_column(String(64), nullable=False)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    issued_by_admin_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    status: Mapped[str] = mapped_column(String(32), default="ISSUED")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class SupportCallbackRequest(Base):
    __tablename__ = "support_callback_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    ticket_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("support_tickets.id"), nullable=True)
    phone_number: Mapped[str] = mapped_column(String(20), nullable=False)
    subject: Mapped[str] = mapped_column(String(128), nullable=False)
    preferred_time_window: Mapped[str] = mapped_column(String(64), default="IMMEDIATE")
    status: Mapped[str] = mapped_column(String(32), default="PENDING")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class TourismPlace(Base):
    __tablename__ = "tourism_places"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    name: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    latitude: Mapped[float] = mapped_column(Float, nullable=False)
    longitude: Mapped[float] = mapped_column(Float, nullable=False)
    image_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class TripTemplate(Base):
    __tablename__ = "trip_templates"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    pickup_address: Mapped[str] = mapped_column(String(255), nullable=False)
    pickup_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    pickup_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_address: Mapped[str] = mapped_column(String(255), nullable=False)
    destination_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    intermediate_stops_json: Mapped[Optional[str]] = mapped_column(Text, default="[]")
    cabin_temperature: Mapped[Optional[float]] = mapped_column(Float, default=21.0)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class TrustedDevice(Base):
    __tablename__ = "trusted_devices"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    device_id: Mapped[str] = mapped_column(String(128), nullable=False)
    device_name: Mapped[str] = mapped_column(String(128), nullable=False)
    device_os: Mapped[str] = mapped_column(String(64), nullable=False)
    is_trusted: Mapped[bool] = mapped_column(Boolean, default=True)
    last_used_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class UserDependent(Base):
    __tablename__ = "user_dependents"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    full_name: Mapped[str] = mapped_column(String(128), nullable=False)
    phone_number: Mapped[str] = mapped_column(String(20), nullable=False)
    relationship: Mapped[str] = mapped_column(String(32), default="FAMILY")
    emergency_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class UserPreferences(Base):
    __tablename__ = "user_preferences"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), unique=True, nullable=False)
    preferred_language: Mapped[str] = mapped_column(String(10), default="en")
    marketing_push_opt_in: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)


class UserSavedPhone(Base):
    __tablename__ = "user_saved_phones"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    phone_number: Mapped[str] = mapped_column(String(20), nullable=False)
    label: Mapped[str] = mapped_column(String(32), default="MOBILE")
    is_primary: Mapped[bool] = mapped_column(Boolean, default=False)
    is_verified: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


