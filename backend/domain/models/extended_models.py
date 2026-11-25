import uuid
import secrets
from datetime import datetime, timezone, date
from typing import Optional, List
from sqlalchemy import (
    String, Integer, Float, Boolean, DateTime, Date, ForeignKey, Text, Index, UniqueConstraint
)
from sqlalchemy.orm import Mapped, mapped_column, relationship
from backend.infrastructure.database.session import Base
from backend.domain.models.models import generate_uuid, utc_now

# =====================================================================
# ENTERPRISE PERSISTENT MODELS (REPLACED PLACEHOLDER STUBS)
# All models are genuine SQLAlchemy Declarative entities.
# =====================================================================

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
    trade_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    tax_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)  # GSTIN
    pan: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    registered_address: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    billing_address: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    billing_contact_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    billing_contact_email: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    billing_email: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    billing_contact_phone: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    admin_user_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True)
    credit_limit_cents: Mapped[Optional[int]] = mapped_column(Integer, default=50000000, nullable=True)
    balance_due_cents: Mapped[Optional[int]] = mapped_column(Integer, default=0, nullable=True)
    monthly_spending_limit_cents: Mapped[Optional[int]] = mapped_column(Integer, default=10000000, nullable=True)
    currency: Mapped[Optional[str]] = mapped_column(String(8), default="INR", nullable=True)
    payment_terms: Mapped[Optional[str]] = mapped_column(String(32), default="NET_30", nullable=True)
    default_cost_center: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    status: Mapped[Optional[str]] = mapped_column(String(32), default="DRAFT", nullable=True)  # DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, SUSPENDED, ACTIVE
    rejection_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    suspension_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    approved_by: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    approved_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=True)


class CorporateEmployee(Base):
    __tablename__ = "corporate_employees"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    corporate_id: Mapped[str] = mapped_column(String(36), ForeignKey("corporate_accounts.id", ondelete="CASCADE"), index=True, nullable=False)
    user_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id", ondelete="SET NULL"), index=True, nullable=True)
    employee_id: Mapped[str] = mapped_column(String(64), nullable=False)
    employee_name: Mapped[str] = mapped_column(String(128), nullable=False)
    employee_email: Mapped[str] = mapped_column(String(128), nullable=False)
    employee_phone: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    department: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    cost_center: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    designation: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    status: Mapped[str] = mapped_column(String(32), default="ACTIVE", nullable=False)  # ACTIVE, INACTIVE, SUSPENDED
    monthly_ride_limit: Mapped[Optional[int]] = mapped_column(Integer, default=50, nullable=True)
    monthly_spending_limit_cents: Mapped[Optional[int]] = mapped_column(Integer, default=5000000, nullable=True)
    per_ride_limit_cents: Mapped[Optional[int]] = mapped_column(Integer, default=200000, nullable=True)
    allowed_vehicle_types_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    approval_required: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=True)


class CorporateInvoice(Base):
    __tablename__ = "corporate_invoices"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    invoice_number: Mapped[Optional[str]] = mapped_column(String(64), unique=True, index=True, nullable=True)
    corporate_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("corporate_accounts.id", ondelete="CASCADE"), index=True, nullable=True)
    billing_period: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)  # e.g. "2026-11"
    subtotal_cents: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    tax_amount_cents: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    discount_cents: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    platform_fee_cents: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    total_amount_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    amount_paid_cents: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    status: Mapped[Optional[str]] = mapped_column(String(32), default="ISSUED", nullable=True)  # DRAFT, GENERATED, ISSUED, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED
    pdf_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    due_date: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    paid_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=True)


class CorporateInvoiceItem(Base):
    __tablename__ = "corporate_invoice_items"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    invoice_id: Mapped[str] = mapped_column(String(36), ForeignKey("corporate_invoices.id", ondelete="CASCADE"), index=True, nullable=False)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id", ondelete="SET NULL"), nullable=True)
    employee_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    passenger_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    cost_center: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    fare_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    tax_cents: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)


class CorporateTravelPolicy(Base):
    __tablename__ = "corporate_travel_policies"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    corporate_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("corporate_accounts.id", ondelete="CASCADE"), index=True, nullable=True)
    policy_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    allowed_categories_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    max_fare_per_ride_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    curfew_start_time: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    curfew_end_time: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    monthly_ride_limit: Mapped[Optional[int]] = mapped_column(Integer, default=60, nullable=True)
    monthly_spending_limit_cents: Mapped[Optional[int]] = mapped_column(Integer, default=10000000, nullable=True)
    daily_ride_limit: Mapped[Optional[int]] = mapped_column(Integer, default=4, nullable=True)
    approval_required: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    is_active: Mapped[Optional[bool]] = mapped_column(Boolean, default=True, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=True)


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
    auto_accept_pool: Mapped[Optional[bool]] = mapped_column(Boolean, default=True, nullable=True)
    high_surge_alerts: Mapped[Optional[bool]] = mapped_column(Boolean, default=True, nullable=True)
    preferred_corridor: Mapped[Optional[str]] = mapped_column(String(128), default="Anand Vihar / CP", nullable=True)
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
    inspection_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("driver_vehicle_inspections.id", ondelete="CASCADE"), nullable=True, index=True)
    driver_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True, index=True)
    vehicle_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True, index=True)
    photo_category: Mapped[str] = mapped_column(String(32), default="OTHER", nullable=False)
    photo_type: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    storage_bucket: Mapped[str] = mapped_column(String(64), default="driver-vehicle-inspections", nullable=False)
    storage_path: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    mime_type: Mapped[Optional[str]] = mapped_column(String(100), default="image/jpeg", nullable=True)
    file_size_bytes: Mapped[Optional[int]] = mapped_column(Integer, default=0, nullable=True)
    file_hash_sha256: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    width: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    height: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    captured_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    uploaded_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)
    upload_status: Mapped[str] = mapped_column(String(32), default="COMPLETED", nullable=False)
    verification_status: Mapped[str] = mapped_column(String(32), default="PENDING", nullable=False)
    metadata_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=True)

    inspection: Mapped[Optional["DriverVehicleInspection"]] = relationship("DriverVehicleInspection", back_populates="photos", lazy="selectin")


class DriverVehicleInspection(Base):
    __tablename__ = "driver_vehicle_inspections"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id", ondelete="CASCADE"), nullable=False, index=True)
    vehicle_id: Mapped[str] = mapped_column(String(36), ForeignKey("vehicles.id", ondelete="CASCADE"), nullable=False, index=True)
    inspection_date: Mapped[Optional[date]] = mapped_column(Date, nullable=True, index=True)
    inspection_status: Mapped[str] = mapped_column(String(32), default="DRAFT", nullable=False, index=True)
    odometer_reading: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    odometer_unit: Mapped[str] = mapped_column(String(16), default="KM", nullable=False)
    previous_odometer_reading: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    checklist_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    overall_result: Mapped[Optional[str]] = mapped_column(String(32), default="PENDING", nullable=True)
    failed_items_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    driver_remarks: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    inspector_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    odometer_photo_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_front_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_rear_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_left_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_right_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_interior_front_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_interior_rear_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    inspected_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    submitted_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    verification_status: Mapped[str] = mapped_column(String(32), default="PENDING", nullable=False)
    verified_by: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    verified_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    rejection_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    photos: Mapped[List["DriverVehicleInspectionPhoto"]] = relationship("DriverVehicleInspectionPhoto", back_populates="inspection", cascade="all, delete-orphan", lazy="selectin")


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


class GeofenceZoneAudit(Base):
    __tablename__ = "geofence_zone_audits"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    zone_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    admin_user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    action: Mapped[Optional[str]] = mapped_column(String(24), nullable=True)
    previous_state_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    new_state_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
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
    provider_name: Mapped[str] = mapped_column(String(32), default="RAZORPAY", nullable=False)
    event_digest: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    event_type: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    provider_event_id: Mapped[Optional[str]] = mapped_column(String(128), nullable=True, index=True)
    payload_json: Mapped[str] = mapped_column(Text, nullable=False)
    signature_verified: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    status: Mapped[str] = mapped_column(String(32), default="RECEIVED", index=True, nullable=False)
    attempts: Mapped[int] = mapped_column(Integer, default=1, nullable=False)
    last_error: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    last_attempt_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    processed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    
    __table_args__ = (
        UniqueConstraint("event_digest", name="uq_payment_webhook_inbox_event_digest"),
    )



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
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, unique=True)
    push_enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    email_enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    sms_enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    quiet_hours_enabled: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    quiet_start: Mapped[str] = mapped_column(String(5), default="22:00", nullable=False)
    quiet_end: Mapped[str] = mapped_column(String(5), default="07:00", nullable=False)
    timezone_name: Mapped[str] = mapped_column(String(64), default="Asia/Kolkata", nullable=False)
    language_code: Mapped[str] = mapped_column(String(10), default="en", nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False)


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
    repayment_terms_weeks: Mapped[int] = mapped_column(Integer, default=4, server_default="4")
    admin_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
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
    code_hash: Mapped[Optional[str]] = mapped_column(String(64), index=True, nullable=True)
    pin: Mapped[Optional[str]] = mapped_column(String(8), nullable=True)
    initial_value_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    remaining_value_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    currency: Mapped[str] = mapped_column(String(8), default="INR", nullable=False)
    purchased_by_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    card_design: Mapped[str] = mapped_column(String(32), default="ROYAL_GOLD", nullable=False)
    recipient_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    recipient_email: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    recipient_phone: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    sender_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    message: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    delivery_method: Mapped[str] = mapped_column(String(32), default="IN_APP", nullable=False)
    status: Mapped[str] = mapped_column(String(32), default="ACTIVE", nullable=False, index=True)
    payment_status: Mapped[str] = mapped_column(String(32), default="COMPLETED", nullable=False)
    payment_order_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    payment_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    redeemed_by_user_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True, index=True)
    redeemed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=True)

    transactions: Mapped[List["GiftCardTransaction"]] = relationship("GiftCardTransaction", back_populates="gift_card", cascade="all, delete-orphan", lazy="selectin")


class GiftCardTransaction(Base):
    __tablename__ = "gift_card_transactions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    gift_card_id: Mapped[str] = mapped_column(String(36), ForeignKey("gift_cards.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    transaction_type: Mapped[str] = mapped_column(String(32), nullable=False)  # PURCHASE, REDEMPTION, PARTIAL_REDEMPTION, REFUND, ADMIN_ADJUSTMENT, EXPIRATION
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    balance_before_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    balance_after_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    reference_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    gift_card: Mapped["GiftCard"] = relationship("GiftCard", back_populates="transactions", lazy="selectin")


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


# =========================================================================
# FEATURE 6: IN-CABIN AUDIO SAFETY RECORDING SUITE (DPDP / IT ACT COMPLIANT)
# =========================================================================

class AudioRecordingConsent(Base):
    __tablename__ = "audio_recording_consents"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    user_role: Mapped[str] = mapped_column(String(32), nullable=False)  # CUSTOMER, DRIVER
    recording_session_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    consent_purpose: Mapped[str] = mapped_column(String(128), default="IN_CABIN_SAFETY_DPDP_COMPLIANT")
    consent_version: Mapped[str] = mapped_column(String(32), default="v1.0")
    consent_status: Mapped[str] = mapped_column(String(32), default="PENDING", index=True)  # NOT_REQUESTED, PENDING, GRANTED, DECLINED, WITHDRAWN, EXPIRED
    granted_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    withdrawn_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)


class AudioRecordingSession(Base):
    __tablename__ = "audio_recording_sessions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    session_id: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id", ondelete="CASCADE"), nullable=False, index=True)
    rider_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    driver_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    recording_status: Mapped[str] = mapped_column(String(32), default="IDLE", index=True)  # IDLE, AWAITING_CONSENT, AUTHORIZED, RECORDING, STOPPED, COMPLETED, FAILED, CANCELLED
    authorization_status: Mapped[str] = mapped_column(String(32), default="PENDING")  # PENDING, AUTHORIZED, REVOKED, EXPIRED
    rider_consent_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    driver_consent_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    started_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    stopped_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    stopped_reason: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    encryption_algorithm: Mapped[str] = mapped_column(String(32), default="AES-256-GCM")
    encryption_key_id: Mapped[str] = mapped_column(String(64), default="managed-envelope-v1")
    total_segments: Mapped[int] = mapped_column(Integer, default=0)
    total_duration_seconds: Mapped[float] = mapped_column(Float, default=0.0)
    last_heartbeat_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)


class AudioRecordingSegment(Base):
    __tablename__ = "audio_recording_segments"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    session_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id", ondelete="CASCADE"), nullable=False, index=True)
    segment_sequence: Mapped[int] = mapped_column(Integer, nullable=False)
    storage_bucket: Mapped[str] = mapped_column(String(64), default="ride-audio-recordings")
    storage_object_path: Mapped[str] = mapped_column(String(512), nullable=False)
    file_format: Mapped[str] = mapped_column(String(32), default="audio/aac")
    encryption_nonce: Mapped[str] = mapped_column(String(64), nullable=False)
    encryption_tag: Mapped[str] = mapped_column(String(64), nullable=False)
    duration_seconds: Mapped[float] = mapped_column(Float, default=0.0)
    file_size_bytes: Mapped[int] = mapped_column(Integer, default=0)
    sha256_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    upload_status: Mapped[str] = mapped_column(String(32), default="UPLOADED")  # PENDING, UPLOADED, FAILED, VERIFIED
    retention_expiry_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True, nullable=False)
    is_purged: Mapped[bool] = mapped_column(Boolean, default=False)
    purged_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class AudioRecordingAuditLog(Base):
    __tablename__ = "audio_recording_audit_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    session_id: Mapped[Optional[str]] = mapped_column(String(64), index=True, nullable=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), index=True, nullable=True)
    actor_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    actor_role: Mapped[str] = mapped_column(String(32), nullable=False)  # CUSTOMER, DRIVER, ADMIN, SYSTEM
    action: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    access_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    authorization_result: Mapped[str] = mapped_column(String(32), default="ALLOWED")  # ALLOWED, DENIED
    metadata_json: Mapped[str] = mapped_column(Text, default="{}")
    ip_address: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    user_agent: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    correlation_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)



class SupportTicketEscalationLog(Base):
    __tablename__ = "support_ticket_escalation_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ticket_id: Mapped[str] = mapped_column(String(36), ForeignKey("support_tickets.id", ondelete="CASCADE"), nullable=False, index=True)
    policy_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("support_escalation_policies.id"), nullable=True)
    from_priority: Mapped[str] = mapped_column(String(20), nullable=False)
    to_priority: Mapped[str] = mapped_column(String(20), nullable=False)
    from_squad: Mapped[str] = mapped_column(String(50), nullable=False)
    to_squad: Mapped[str] = mapped_column(String(50), nullable=False)
    escalation_reason: Mapped[str] = mapped_column(Text, nullable=False)
    idempotency_key: Mapped[str] = mapped_column(String(128), unique=True, index=True, nullable=False)
    escalated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)