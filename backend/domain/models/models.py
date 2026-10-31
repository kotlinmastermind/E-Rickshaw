import uuid
import secrets
from datetime import datetime, timezone
from typing import Optional, List
from sqlalchemy import (
    String, Integer, Float, Boolean, DateTime, ForeignKey, Text, Index, UniqueConstraint
)
from sqlalchemy.orm import Mapped, mapped_column, relationship, relationship as orm_relationship
from backend.infrastructure.database.session import Base

def generate_uuid() -> str:
    return str(uuid.uuid4())

def generate_referral_code() -> str:
    return "VIP-" + secrets.token_hex(4).upper()

def utc_now() -> datetime:
    return datetime.now(timezone.utc)

class User(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    phone_number: Mapped[str] = mapped_column(String(20), unique=True, index=True, nullable=False)
    username: Mapped[Optional[str]] = mapped_column(String(64), unique=True, index=True, nullable=True)
    email: Mapped[Optional[str]] = mapped_column(String(255), unique=True, index=True, nullable=True)
    password_hash: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    email_verified: Mapped[bool] = mapped_column(Boolean, default=False)
    avatar_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    mfa_enabled: Mapped[bool] = mapped_column(Boolean, default=False)
    mfa_secret: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    app_lock_pin_hash: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    biometric_enabled: Mapped[bool] = mapped_column(Boolean, default=False)
    passkey_credential_id: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    passkey_public_key: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    full_name: Mapped[str] = mapped_column(String(128), default="Distinguished Guest")
    role: Mapped[str] = mapped_column(String(20), default="customer")  # customer, driver, admin
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    is_verified: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    customer_profile: Mapped[Optional["CustomerProfile"]] = relationship(back_populates="user", uselist=False)
    driver_profile: Mapped[Optional["DriverProfile"]] = relationship(back_populates="user", uselist=False)
    device_tokens: Mapped[List["DeviceToken"]] = relationship(back_populates="user")
    sessions: Mapped[List["Session"]] = relationship(back_populates="user")
    notifications: Mapped[List["Notification"]] = relationship(back_populates="user")
    wallet: Mapped[Optional["Wallet"]] = relationship(back_populates="user", uselist=False)
    emergency_contacts: Mapped[List["EmergencyContact"]] = relationship(back_populates="user", cascade="all, delete-orphan")


class CustomerProfile(Base):
    __tablename__ = "customer_profiles"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), unique=True, nullable=False)
    preferred_payment_method: Mapped[str] = mapped_column(String(20), default="UPI")  # UPI, CASH, WALLET
    emergency_contact_phone: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    rating_average: Mapped[float] = mapped_column(Float, default=5.0)
    rating_count: Mapped[int] = mapped_column(Integer, default=0)
    referral_code: Mapped[Optional[str]] = mapped_column(String(32), unique=True, index=True, nullable=True, default=generate_referral_code)
    referred_by_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    tier: Mapped[str] = mapped_column(String(32), default="ROYAL_SOVEREIGN")
    temperature_celsius: Mapped[float] = mapped_column(Float, default=21.0)
    quiet_ride: Mapped[bool] = mapped_column(Boolean, default=True)
    preferred_music: Mapped[str] = mapped_column(String(64), default="Classical Symphony")
    preferred_water: Mapped[str] = mapped_column(String(64), default="Sparkling Mineral")
    luggage_assistance: Mapped[bool] = mapped_column(Boolean, default=True)

    # Part 7 Loyalty, Tier, Streak & Celebration Attributes (Features 158, 160, 161, 167, 168, 170)
    loyalty_points_balance: Mapped[int] = mapped_column(Integer, default=0)
    lifetime_points_earned: Mapped[int] = mapped_column(Integer, default=0)
    membership_tier: Mapped[str] = mapped_column(String(32), default="SILVER")  # SILVER, GOLD, PLATINUM, BLACK_SOVEREIGN
    membership_tier_expires_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    membership_renewal_reminder_sent: Mapped[bool] = mapped_column(Boolean, default=False)
    ride_streak_count: Mapped[int] = mapped_column(Integer, default=0)
    last_ride_streak_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    birthday_date: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)  # YYYY-MM-DD
    anniversary_date: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)  # YYYY-MM-DD
    offer_notification_frequency: Mapped[str] = mapped_column(String(32), default="WEEKLY")  # DAILY, WEEKLY, EXCLUSIVE_ONLY, MUTED

    # Part 3 Bespoke Preferences & Accessibility (Features 56-74)
    no_phone_call_ride: Mapped[bool] = mapped_column(Boolean, default=False)
    preferred_driver_language: Mapped[str] = mapped_column(String(32), default="English")
    female_driver_preferred: Mapped[bool] = mapped_column(Boolean, default=False)
    vehicle_color_preference: Mapped[str] = mapped_column(String(32), default="Obsidian Black")
    premium_amenities: Mapped[str] = mapped_column(String(255), default="Evian Sparkling Water, Braided Fast-Charging Kit")
    electric_vehicle_preferred: Mapped[bool] = mapped_column(Boolean, default=False)
    low_emission_filter: Mapped[bool] = mapped_column(Boolean, default=False)
    wheelchair_access_required: Mapped[bool] = mapped_column(Boolean, default=False)
    step_free_pickup_required: Mapped[bool] = mapped_column(Boolean, default=False)
    assistance_animal_present: Mapped[bool] = mapped_column(Boolean, default=False)
    extra_legroom_required: Mapped[bool] = mapped_column(Boolean, default=False)
    pet_friendly_preferred: Mapped[bool] = mapped_column(Boolean, default=False)
    accessibility_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    user: Mapped["User"] = relationship(back_populates="customer_profile")
    rides: Mapped[List["Ride"]] = relationship(back_populates="customer")
    favorites: Mapped[List["FavoriteLocation"]] = relationship(back_populates="customer")


class DriverProfile(Base):
    __tablename__ = "driver_profiles"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), unique=True, nullable=False)
    license_number: Mapped[str] = mapped_column(String(50), unique=True, nullable=False)
    approval_status: Mapped[str] = mapped_column(String(20), default="PENDING")  # PENDING, APPROVED, REJECTED, SUSPENDED
    rejection_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    rating_average: Mapped[float] = mapped_column(Float, default=5.0)
    rating_count: Mapped[int] = mapped_column(Integer, default=0)
    total_trips: Mapped[int] = mapped_column(Integer, default=0)
    wallet_balance_cents: Mapped[int] = mapped_column(Integer, default=0)
    
    # Part 6 Security Credentials (Features 131, 141)
    security_badge_id: Mapped[str] = mapped_column(String(32), default="")
    background_check_status: Mapped[str] = mapped_column(String(32), default="PENDING")
    background_check_date: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    academy_certified: Mapped[bool] = mapped_column(Boolean, default=False)
    academy_level: Mapped[str] = mapped_column(String(64), default="Training not completed")

    # Part 9 Driver Onboarding, Identity & Preferences (Features 201, 202, 214, 215, 216, 218, 219, 221, 222, 223, 224)
    onboarding_step: Mapped[str] = mapped_column(String(32), default="IDENTITY_SUBMITTED")  # IDENTITY_SUBMITTED, DOCUMENTS_PENDING, BACKGROUND_CHECK, TRAINING_COMPLETED, INSPECTION_PENDING, APPROVED, ACTIVE
    profile_completeness_percentage: Mapped[int] = mapped_column(Integer, default=65)
    identity_verified: Mapped[bool] = mapped_column(Boolean, default=False)
    identity_reverification_due: Mapped[bool] = mapped_column(Boolean, default=False)
    last_identity_verified_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    preferred_operating_zones: Mapped[str] = mapped_column(String(255), default="AIRPORT_CORRIDOR,SOUTH_MUMBAI,BKC_FINANCIAL")
    preferred_vehicle_categories: Mapped[str] = mapped_column(String(255), default="EXECUTIVE_SEDAN,PRESTIGE_SUV")
    languages_spoken: Mapped[str] = mapped_column(String(128), default="English, Hindi, Marathi")
    service_etiquette_level: Mapped[str] = mapped_column(String(64), default="Black Sovereign Executive")
    cabin_music_preference: Mapped[str] = mapped_column(String(64), default="Classical & Soft Jazz")
    cabin_temp_celsius: Mapped[int] = mapped_column(Integer, default=21)
    emergency_contact_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    emergency_contact_phone: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    emergency_contact_relationship: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    account_deletion_requested: Mapped[bool] = mapped_column(Boolean, default=False)
    account_deletion_requested_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    policy_acknowledged_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    user: Mapped["User"] = relationship(back_populates="driver_profile")
    documents: Mapped[List["DriverDocument"]] = relationship(back_populates="driver")
    vehicle_assignments: Mapped[List["DriverVehicleAssignment"]] = relationship(back_populates="driver")
    availability: Mapped[Optional["DriverAvailability"]] = relationship(back_populates="driver", uselist=False)
    locations: Mapped[List["DriverLocation"]] = relationship(back_populates="driver")
    assigned_rides: Mapped[List["Ride"]] = relationship(back_populates="driver")
    targets: Mapped[List["DriverDailyTarget"]] = relationship(back_populates="driver")
    payout_requests: Mapped[List["DriverPayoutRequest"]] = relationship(back_populates="driver")


class DriverDocument(Base):
    __tablename__ = "driver_documents"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    document_type: Mapped[str] = mapped_column(String(40), nullable=False)
    storage_path: Mapped[str] = mapped_column(String(512), nullable=False)
    file_name: Mapped[str] = mapped_column(String(255), nullable=False)
    mime_type: Mapped[str] = mapped_column(String(100), nullable=False)
    file_size_bytes: Mapped[int] = mapped_column(Integer, nullable=False)
    file_hash_sha256: Mapped[str] = mapped_column(String(64), nullable=False)
    verification_status: Mapped[str] = mapped_column(String(20), default="PENDING")
    rejection_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    reviewed_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    reviewed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    expires_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    expiry_reminder_sent_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    image_quality_score: Mapped[float] = mapped_column(Float, default=1.0)
    quality_assessment_notes: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    resubmission_count: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    driver: Mapped["DriverProfile"] = relationship(back_populates="documents")


class VehicleCategory(Base):
    __tablename__ = "vehicle_categories"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    code: Mapped[str] = mapped_column(String(30), unique=True, nullable=False)
    name: Mapped[str] = mapped_column(String(80), nullable=False)
    description: Mapped[str] = mapped_column(Text, default="")
    icon_url: Mapped[str] = mapped_column(String(512), default="")
    capacity: Mapped[int] = mapped_column(Integer, default=4)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    sort_order: Mapped[int] = mapped_column(Integer, default=1)

    # Part 3 Vehicle Specs, Accessibility & Gallery (Features 51, 52, 53, 55, 68, 69, 72)
    photos_gallery_json: Mapped[str] = mapped_column(Text, default="[]")
    legroom_inches: Mapped[float] = mapped_column(Float, default=40.0)
    quietness_db: Mapped[float] = mapped_column(Float, default=54.0)
    luggage_large_capacity: Mapped[int] = mapped_column(Integer, default=2)
    luggage_small_capacity: Mapped[int] = mapped_column(Integer, default=2)
    has_wifi: Mapped[bool] = mapped_column(Boolean, default=True)
    has_massage_seats: Mapped[bool] = mapped_column(Boolean, default=False)
    has_champagne_cooler: Mapped[bool] = mapped_column(Boolean, default=False)
    has_reclining_seats: Mapped[bool] = mapped_column(Boolean, default=True)
    is_electric: Mapped[bool] = mapped_column(Boolean, default=False)
    emissions_g_per_km: Mapped[float] = mapped_column(Float, default=115.0)
    wheelchair_accessible: Mapped[bool] = mapped_column(Boolean, default=False)
    step_free_height_cm: Mapped[float] = mapped_column(Float, default=38.0)
    pet_friendly: Mapped[bool] = mapped_column(Boolean, default=True)
    child_seat_available: Mapped[bool] = mapped_column(Boolean, default=True)
    available_colors_json: Mapped[str] = mapped_column(String(255), default='["Obsidian Black", "Pearl White", "Royal Navy Blue"]')
    available_amenities_json: Mapped[str] = mapped_column(String(512), default='["Evian Sparkling Water", "Braided Fast-Charging Kit", "Financial Times", "Cashmere Blanket", "Amber Wood Diffuser"]')
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    vehicles: Mapped[List["Vehicle"]] = relationship(back_populates="category")
    fare_rules: Mapped[List["FareRule"]] = relationship(back_populates="category")


class Vehicle(Base):
    __tablename__ = "vehicles"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    category_id: Mapped[str] = mapped_column(String(36), ForeignKey("vehicle_categories.id"), nullable=False)
    make: Mapped[str] = mapped_column(String(64), nullable=False)
    model: Mapped[str] = mapped_column(String(64), nullable=False)
    year: Mapped[int] = mapped_column(Integer, nullable=False)
    license_plate: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    color: Mapped[str] = mapped_column(String(32), default="Obsidian Black")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)

    # Part 6 Commercial Registration & Compliance Credentials (Feature 132)
    rc_number: Mapped[Optional[str]] = mapped_column(String(64), nullable=True, default="MH-01-2024-RC98214")
    permit_number: Mapped[Optional[str]] = mapped_column(String(64), nullable=True, default="AITP-LUX-2026-08492")
    puc_expiry_date: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    fitness_expiry_date: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    insurance_policy_number: Mapped[Optional[str]] = mapped_column(String(64), nullable=True, default="NIC-COMM-LUX-849201")

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    category: Mapped["VehicleCategory"] = relationship(back_populates="vehicles")
    driver_assignments: Mapped[List["DriverVehicleAssignment"]] = relationship(back_populates="vehicle")


class DriverVehicleAssignment(Base):
    __tablename__ = "driver_vehicle_assignments"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False)
    vehicle_id: Mapped[str] = mapped_column(String(36), ForeignKey("vehicles.id"), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    assigned_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    unassigned_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    driver: Mapped["DriverProfile"] = relationship(back_populates="vehicle_assignments")
    vehicle: Mapped["Vehicle"] = relationship(back_populates="driver_assignments")


class DriverAvailability(Base):
    __tablename__ = "driver_availability"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), unique=True, nullable=False)
    is_online: Mapped[bool] = mapped_column(Boolean, default=False)
    is_busy: Mapped[bool] = mapped_column(Boolean, default=False)
    auto_accept_enabled: Mapped[bool] = mapped_column(Boolean, default=False)
    auto_accept_max_distance_km: Mapped[float] = mapped_column(Float, default=10.0)
    last_heartbeat: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    driver: Mapped["DriverProfile"] = relationship(back_populates="availability")


class DriverLocation(Base):
    __tablename__ = "driver_locations"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    latitude: Mapped[float] = mapped_column(Float, nullable=False)
    longitude: Mapped[float] = mapped_column(Float, nullable=False)
    heading: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    speed: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    accuracy: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    spoof_risk_score: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    quality_flags_json: Mapped[str] = mapped_column(Text, default="[]", nullable=False)
    source: Mapped[str] = mapped_column(String(24), default="FUSED", nullable=False)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, index=True)

    driver: Mapped["DriverProfile"] = relationship(back_populates="locations")


class FareRule(Base):
    __tablename__ = "fare_rules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    category_id: Mapped[str] = mapped_column(String(36), ForeignKey("vehicle_categories.id"), nullable=False)
    version: Mapped[int] = mapped_column(Integer, default=1)
    base_fare_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    per_km_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    per_minute_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    minimum_fare_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    cancellation_fee_cents: Mapped[int] = mapped_column(Integer, default=5000)
    surge_multiplier: Mapped[float] = mapped_column(Float, default=1.0)
    grace_waiting_minutes: Mapped[int] = mapped_column(Integer, default=5)
    per_minute_waiting_cents: Mapped[int] = mapped_column(Integer, default=1000)
    shared_discount_percentage: Mapped[float] = mapped_column(Float, default=25.0, server_default="25.0")
    fare_cap_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    tax_percentage: Mapped[float] = mapped_column(Float, default=5.0)
    amenities_fee_cents: Mapped[int] = mapped_column(Integer, default=5000)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    effective_from: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    effective_until: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    workflow_status: Mapped[str] = mapped_column(String(20), default="ACTIVE", nullable=False)
    approved_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    approved_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    approval_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

    category: Mapped["VehicleCategory"] = relationship(back_populates="fare_rules")


class FareEstimate(Base):
    __tablename__ = "fare_estimates"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    category_id: Mapped[str] = mapped_column(String(36), ForeignKey("vehicle_categories.id"), nullable=False)
    fare_rule_id: Mapped[str] = mapped_column(String(36), ForeignKey("fare_rules.id"), nullable=False)
    pickup_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    pickup_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    distance_km: Mapped[float] = mapped_column(Float, nullable=False)
    estimated_duration_minutes: Mapped[float] = mapped_column(Float, nullable=False)
    estimated_fare_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    surge_multiplier: Mapped[float] = mapped_column(Float, default=1.0)
    
    # Part 4 Fares & Settlement Additions (Features 76, 77, 79, 81)
    base_fare_cents: Mapped[int] = mapped_column(Integer, default=0)
    distance_charge_cents: Mapped[int] = mapped_column(Integer, default=0)
    time_charge_cents: Mapped[int] = mapped_column(Integer, default=0)
    tax_cents: Mapped[int] = mapped_column(Integer, default=0)
    amenities_fee_cents: Mapped[int] = mapped_column(Integer, default=0)
    fare_min_cents: Mapped[int] = mapped_column(Integer, default=0)
    fare_max_cents: Mapped[int] = mapped_column(Integer, default=0)
    confidence_level: Mapped[str] = mapped_column(String(20), default="HIGH")
    confidence_percentage: Mapped[int] = mapped_column(Integer, default=95)
    is_capped: Mapped[bool] = mapped_column(Boolean, default=False)
    cap_discount_cents: Mapped[int] = mapped_column(Integer, default=0)
    
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class Ride(Base):
    __tablename__ = "rides"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    assigned_driver_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=True, index=True)
    vehicle_category_id: Mapped[str] = mapped_column(String(36), ForeignKey("vehicle_categories.id"), nullable=False)
    vehicle_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("vehicles.id"), nullable=True)
    fare_rule_id: Mapped[str] = mapped_column(String(36), ForeignKey("fare_rules.id"), nullable=False)
    
    # States: REQUESTED, ASSIGNED, ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED, EXPIRED
    status: Mapped[str] = mapped_column(String(30), default="REQUESTED", index=True)
    
    pickup_address: Mapped[str] = mapped_column(String(255), nullable=False)
    pickup_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    pickup_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_address: Mapped[str] = mapped_column(String(255), nullable=False)
    destination_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    
    start_otp: Mapped[str] = mapped_column(String(6), nullable=False)
    otp_verified: Mapped[bool] = mapped_column(Boolean, default=False)
    
    # Scheduled ride support
    is_scheduled: Mapped[bool] = mapped_column(Boolean, default=False, index=True)
    scheduled_pickup_time: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True, index=True)
    scheduled_window_minutes: Mapped[int] = mapped_column(Integer, default=15)
    auto_dispatch_status: Mapped[str] = mapped_column(String(30), default="NOT_SCHEDULED")
    
    # Waiting timer support
    waiting_started_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    waiting_ended_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    billable_waiting_minutes: Mapped[float] = mapped_column(Float, default=0.0)
    waiting_charge_cents: Mapped[int] = mapped_column(Integer, default=0)

    # Promo / Pass discounts
    promo_code_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    discount_cents: Mapped[int] = mapped_column(Integer, default=0)

    # Shared ride support (Phase 3)
    is_shared: Mapped[bool] = mapped_column(Boolean, default=False, server_default="0", index=True)
    seats_booked: Mapped[int] = mapped_column(Integer, default=1, server_default="1")
    pooled_trip_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("pooled_trips.id"), nullable=True, index=True)

    estimated_distance_km: Mapped[float] = mapped_column(Float, nullable=False)
    actual_distance_km: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    estimated_duration_minutes: Mapped[float] = mapped_column(Float, nullable=False)
    actual_duration_minutes: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    estimated_fare_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    final_fare_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    payment_method: Mapped[str] = mapped_column(String(20), default="UPI")  # UPI, CASH, WALLET
    
    cancellation_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    cancelled_by_role: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)

    # Part 2 Booking Attributes (Features 26-50)
    booking_for_self: Mapped[bool] = mapped_column(Boolean, default=True)
    passenger_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    passenger_phone: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    passenger_relationship: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    is_return_trip: Mapped[bool] = mapped_column(Boolean, default=False)
    paired_return_ride_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    is_round_trip: Mapped[bool] = mapped_column(Boolean, default=False)
    pickup_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    destination_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    entrance_guidance: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    landmark_guidance: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    airport_code: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    terminal_name: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    airline_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    flight_number: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    flight_delay_minutes: Mapped[int] = mapped_column(Integer, default=0)
    flight_status: Mapped[Optional[str]] = mapped_column(String(32), default="ON_TIME")
    booking_mode: Mapped[str] = mapped_column(String(32), default="STANDARD")  # STANDARD, HOTEL_CONCIERGE, BUSINESS_EXPENSE, DEPENDENT
    hotel_room_number: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    hotel_guest_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    business_cost_center: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    business_project_code: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    business_tax_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    dependent_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    shared_location_source: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    
    # Part 4 Fares & Settlement Additions (Features 80, 81, 82, 83, 89, 96)
    original_fare_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    fare_difference_cents: Mapped[int] = mapped_column(Integer, default=0)
    fare_change_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    fare_cap_applied: Mapped[bool] = mapped_column(Boolean, default=False)
    price_locked: Mapped[bool] = mapped_column(Boolean, default=False)
    locked_fare_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    price_lock_expires_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    invoice_number: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    corporate_billing_ref: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    split_fare_enabled: Mapped[bool] = mapped_column(Boolean, default=False)
    
    # Part 5 Tracking, Route & Chauffeur Attributes (Features 108, 111, 115, 116, 118, 119, 125)
    route_preference: Mapped[str] = mapped_column(String(32), default="FASTEST")  # FASTEST, SCENIC, SMOOTHEST
    route_deviation_detected: Mapped[bool] = mapped_column(Boolean, default=False)
    route_deviation_reason: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    driver_arrival_photo_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    vehicle_model_override: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    vehicle_plate_override: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    vehicle_color_override: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    masked_virtual_number: Mapped[Optional[str]] = mapped_column(String(32), default="+91 22 6900 4820")
    masked_virtual_ext: Mapped[Optional[str]] = mapped_column(String(8), default="4921")
    post_trip_contact_expires_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    
    # Part 6 Safety, Emergency & Anomaly Attributes (Features 126, 128, 134, 135, 136, 137, 146)
    sos_triggered: Mapped[bool] = mapped_column(Boolean, default=False)
    sos_triggered_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    safety_checkin_due_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    safety_checkin_status: Mapped[str] = mapped_column(String(32), default="NONE")  # NONE, PENDING, CONFIRMED, MISSED, ESCALATED
    audio_recording_consent_passenger: Mapped[bool] = mapped_column(Boolean, default=False)
    audio_recording_consent_driver: Mapped[bool] = mapped_column(Boolean, default=False)
    audio_recording_active: Mapped[bool] = mapped_column(Boolean, default=False)
    audio_recording_session_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    unexpected_stop_detected: Mapped[bool] = mapped_column(Boolean, default=False)
    unexpected_stop_duration_seconds: Mapped[int] = mapped_column(Integer, default=0)
    route_anomaly_detected: Mapped[bool] = mapped_column(Boolean, default=False)
    route_anomaly_type: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    high_risk_pickup: Mapped[bool] = mapped_column(Boolean, default=False)
    high_risk_reason: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)

    # Part 7 Post-Ride Tipping & Loyalty (Features 156, 157, 159)
    tip_cents: Mapped[int] = mapped_column(Integer, default=0)
    tip_status: Mapped[str] = mapped_column(String(32), default="NONE")  # NONE, PENDING, CHARGED, ADJUSTED
    tip_adjustment_expires_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    tip_adjusted_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    points_earned: Mapped[int] = mapped_column(Integer, default=0)

    # Part 3 Ride Bespoke Requests (Features 53-75)
    child_seat_requested: Mapped[bool] = mapped_column(Boolean, default=False)
    child_seat_type: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    luggage_large_count: Mapped[int] = mapped_column(Integer, default=0)
    luggage_small_count: Mapped[int] = mapped_column(Integer, default=0)
    wheelchair_requested: Mapped[bool] = mapped_column(Boolean, default=False)
    step_free_pickup: Mapped[bool] = mapped_column(Boolean, default=False)
    assistance_animal: Mapped[bool] = mapped_column(Boolean, default=False)
    extra_legroom_requested: Mapped[bool] = mapped_column(Boolean, default=False)
    quiet_ride_requested: Mapped[bool] = mapped_column(Boolean, default=True)
    cabin_temp_requested: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    music_requested: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    no_phone_call: Mapped[bool] = mapped_column(Boolean, default=False)
    driver_language_requested: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    female_driver_requested: Mapped[bool] = mapped_column(Boolean, default=False)
    vehicle_color_requested: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    amenities_requested: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    ev_ride_requested: Mapped[bool] = mapped_column(Boolean, default=False)
    pet_friendly_requested: Mapped[bool] = mapped_column(Boolean, default=False)
    special_assistance_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

    requested_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    assigned_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    arrived_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    started_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    cancelled_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    customer: Mapped["CustomerProfile"] = relationship(back_populates="rides")
    driver: Mapped[Optional["DriverProfile"]] = relationship(back_populates="assigned_rides")
    events: Mapped[List["RideStatusEvent"]] = relationship(back_populates="ride", cascade="all, delete-orphan")
    assignments: Mapped[List["RideAssignment"]] = relationship(back_populates="ride")
    payment: Mapped[Optional["Payment"]] = relationship(back_populates="ride", uselist=False)
    rating: Mapped[Optional["DriverRating"]] = relationship(back_populates="ride", uselist=False)
    stops: Mapped[List["RideStop"]] = relationship(back_populates="ride", cascade="all, delete-orphan", order_by="RideStop.stop_order")
    chat: Mapped[Optional["RideChat"]] = relationship(back_populates="ride", uselist=False, cascade="all, delete-orphan")
    pooled_booking: Mapped[Optional["PooledBooking"]] = relationship(back_populates="ride", uselist=False)


class RideStop(Base):
    __tablename__ = "ride_stops"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    stop_order: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    sequence_order: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    address: Mapped[str] = mapped_column(String(255), nullable=False)
    latitude: Mapped[float] = mapped_column(Float, nullable=False)
    longitude: Mapped[float] = mapped_column(Float, nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="PENDING")  # PENDING, ARRIVED, COMPLETED
    dwell_time_minutes: Mapped[int] = mapped_column(Integer, default=0)
    passenger_instructions: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    entrance_landmark: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    arrived_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    def __init__(self, **kwargs):
        if "sequence_order" in kwargs and "stop_order" not in kwargs:
            kwargs["stop_order"] = kwargs["sequence_order"]
        elif "stop_order" in kwargs and "sequence_order" not in kwargs:
            kwargs["sequence_order"] = kwargs["stop_order"]
        super().__init__(**kwargs)

    ride: Mapped["Ride"] = relationship(back_populates="stops")
    __table_args__ = (UniqueConstraint("ride_id", "stop_order", name="uq_ride_stop_order"),)


class FavoriteLocation(Base):
    __tablename__ = "favorite_locations"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    label: Mapped[str] = mapped_column(String(64), nullable=False)  # Home, Office, Country Club
    address: Mapped[str] = mapped_column(String(255), nullable=False)
    latitude: Mapped[float] = mapped_column(Float, nullable=False)
    longitude: Mapped[float] = mapped_column(Float, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    customer: Mapped["CustomerProfile"] = relationship(back_populates="favorites")


class ReferralRecord(Base):
    __tablename__ = "referral_records"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    referrer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    referred_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), unique=True, nullable=False, index=True)
    reward_status: Mapped[str] = mapped_column(String(30), default="PENDING_FIRST_RIDE")  # PENDING_FIRST_RIDE, CREDITED, REVERSED
    referrer_reward_cents: Mapped[int] = mapped_column(Integer, default=50000)
    referred_reward_cents: Mapped[int] = mapped_column(Integer, default=50000)
    rewarded_ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True)
    
    # Part 7 Referral Fraud Review Queue (Feature 165)
    fraud_review_status: Mapped[str] = mapped_column(String(32), default="APPROVED")  # APPROVED, FLAGGED, REJECTED, UNDER_REVIEW
    fraud_risk_score: Mapped[float] = mapped_column(Float, default=0.0)
    fraud_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    reviewed_by: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    reviewed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    credited_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class PromoCode(Base):
    __tablename__ = "promo_codes"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    code: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    description: Mapped[str] = mapped_column(String(255), default="")
    discount_type: Mapped[str] = mapped_column(String(20), default="PERCENTAGE")  # PERCENTAGE, FLAT
    discount_value: Mapped[float] = mapped_column(Float, nullable=False)
    max_discount_cents: Mapped[int] = mapped_column(Integer, default=50000)
    min_fare_cents: Mapped[int] = mapped_column(Integer, default=100000)
    total_usage_limit: Mapped[int] = mapped_column(Integer, default=1000)
    per_customer_limit: Mapped[int] = mapped_column(Integer, default=1)
    times_used: Mapped[int] = mapped_column(Integer, default=0)
    starts_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    vehicle_category_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("vehicle_categories.id"), nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class PromoRedemption(Base):
    __tablename__ = "promo_redemptions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    promo_id: Mapped[str] = mapped_column(String(36), ForeignKey("promo_codes.id"), nullable=False, index=True)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), unique=True, nullable=False)
    discount_applied_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    redeemed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class Wallet(Base):
    __tablename__ = "wallets"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), unique=True, index=True, nullable=False)
    currency: Mapped[str] = mapped_column(String(10), default="INR")
    balance_cents: Mapped[int] = mapped_column(Integer, default=0)
    
    # Part 4 Wallet Auto Top-up & Alerts (Features 91, 92)
    auto_topup_enabled: Mapped[bool] = mapped_column(Boolean, default=False)
    auto_topup_threshold_cents: Mapped[int] = mapped_column(Integer, default=50000)  # Default ₹500
    auto_topup_amount_cents: Mapped[int] = mapped_column(Integer, default=200000)    # Default ₹2,000
    auto_topup_method_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    low_balance_alert_sent: Mapped[bool] = mapped_column(Boolean, default=False)
    
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    user: Mapped["User"] = relationship(back_populates="wallet")
    transactions: Mapped[List["WalletTransaction"]] = relationship(back_populates="wallet", order_by="WalletTransaction.created_at.desc()")


class WalletTransaction(Base):
    __tablename__ = "wallet_transactions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    wallet_id: Mapped[str] = mapped_column(String(36), ForeignKey("wallets.id"), nullable=False, index=True)
    transaction_type: Mapped[str] = mapped_column(String(30), nullable=False)  # CREDIT, DEBIT, COMPENSATION_CREDIT, COMPENSATION_DEBIT
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    balance_after_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    source: Mapped[str] = mapped_column(String(40), nullable=False)  # TOP_UP, RIDE_PAYMENT, REFERRAL_REWARD, REFUND, ADMIN_ADJUSTMENT
    reference_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True, index=True)
    idempotency_key: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    description: Mapped[str] = mapped_column(String(255), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    wallet: Mapped["Wallet"] = relationship(back_populates="transactions")


class SupportTicket(Base):
    __tablename__ = "support_tickets"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ticket_number: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True)
    category: Mapped[str] = mapped_column(String(50), nullable=False)  # BILLING, CHAUFFEUR_CONDUCT, LOST_PROPERTY, SAFETY
    subject: Mapped[str] = mapped_column(String(255), nullable=False)
    status: Mapped[str] = mapped_column(String(30), default="OPEN")  # OPEN, INVESTIGATING, RESOLVED, CLOSED
    priority: Mapped[str] = mapped_column(String(20), default="MEDIUM")  # LOW, MEDIUM, HIGH, URGENT
    assigned_admin_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    internal_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    sla_due_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    last_staff_update_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    # Part 8 Support Additions (Features 184, 189, 191, 195, 196, 197, 200, 178, 179, 187, 188, 198)
    complaint_reference_number: Mapped[Optional[str]] = mapped_column(String(64), nullable=True, unique=True, index=True)
    language_preference: Mapped[str] = mapped_column(String(10), default="en")
    assigned_squad: Mapped[str] = mapped_column(String(50), default="VIP_CONCIERGE")  # VIP_CONCIERGE, BILLING_REFUNDS, SAFETY_INCIDENTS, FLEET_OPERATIONS
    is_escalated: Mapped[bool] = mapped_column(Boolean, default=False)
    escalation_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    escalated_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    lost_item_category: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)  # PHONE, WALLET, BAG, JEWELRY, ELECTRONICS, OTHER
    lost_item_description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    lost_item_status: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)  # REPORTED, LOCATED_WITH_CHAUFFEUR, DISPATCHED_RETURN, RETURNED, NOT_FOUND
    csat_score: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)  # 1 to 5
    csat_comment: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    resolved_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    data_retention_days: Mapped[int] = mapped_column(Integer, default=365)
    refund_amount_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    fare_review_evidence: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    cancellation_reason_code: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)
    fee_waiver_requested: Mapped[bool] = mapped_column(Boolean, default=False)
    duplicate_of_ticket_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    messages: Mapped[List["SupportMessage"]] = relationship(back_populates="ticket", cascade="all, delete-orphan", order_by="SupportMessage.created_at.asc()")


class SupportMessage(Base):
    __tablename__ = "support_messages"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ticket_id: Mapped[str] = mapped_column(String(36), ForeignKey("support_tickets.id"), nullable=False, index=True)
    sender_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    sender_role: Mapped[str] = mapped_column(String(20), nullable=False)  # CUSTOMER, DRIVER, ADMIN
    message: Mapped[str] = mapped_column(Text, nullable=False)
    attachment_path: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    is_internal_only: Mapped[bool] = mapped_column(Boolean, default=False)
    read_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    ticket: Mapped["SupportTicket"] = relationship(back_populates="messages")


class RideChat(Base):
    __tablename__ = "ride_chats"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), unique=True, nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    ride: Mapped["Ride"] = relationship(back_populates="chat")
    messages: Mapped[List["RideChatMessage"]] = relationship(back_populates="chat", cascade="all, delete-orphan", order_by="RideChatMessage.sent_at.asc()")


class RideChatMessage(Base):
    __tablename__ = "ride_chat_messages"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    chat_id: Mapped[str] = mapped_column(String(36), ForeignKey("ride_chats.id"), nullable=False, index=True)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    sender_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    sender_role: Mapped[str] = mapped_column(String(20), nullable=False)  # CUSTOMER, DRIVER
    message: Mapped[str] = mapped_column(Text, nullable=False)
    is_read: Mapped[bool] = mapped_column(Boolean, default=False)
    
    # Part 5 Translation & Meeting Point Fields (Features 113, 114)
    translated_text: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    translated_language: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)
    meeting_point_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    meeting_point_lat: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    meeting_point_lng: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    
    sent_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    chat: Mapped["RideChat"] = relationship(back_populates="messages")


class DriverDailyTarget(Base):
    __tablename__ = "driver_daily_targets"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    target_date: Mapped[str] = mapped_column(String(10), nullable=False, index=True)  # YYYY-MM-DD
    target_trips: Mapped[int] = mapped_column(Integer, default=5)
    target_earnings_cents: Mapped[int] = mapped_column(Integer, default=800000)
    bonus_incentive_cents: Mapped[int] = mapped_column(Integer, default=150000)
    is_achieved: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    driver: Mapped["DriverProfile"] = relationship(back_populates="targets")
    __table_args__ = (UniqueConstraint("driver_id", "target_date", name="uq_driver_target_date"),)


class DriverPayoutRequest(Base):
    __tablename__ = "driver_payout_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    status: Mapped[str] = mapped_column(String(30), default="REQUESTED")  # REQUESTED, APPROVED, PAID, REJECTED
    bank_account_masked: Mapped[str] = mapped_column(String(32), default="HDFC *******4921")
    payout_reference: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    reviewed_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    reviewed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    rejection_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    exception_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    settlement_reference: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    idempotency_key: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    driver: Mapped["DriverProfile"] = relationship(back_populates="payout_requests")


class GeofenceZone(Base):
    __tablename__ = "geofence_zones"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    name: Mapped[str] = mapped_column(String(128), nullable=False)
    zone_type: Mapped[str] = mapped_column(String(30), default="SERVICE_AREA")  # SERVICE_AREA, SURGE_ZONE, RESTRICTED
    coordinates_json: Mapped[str] = mapped_column(Text, nullable=False)  # JSON array [[lat, lng], ...]
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    surge_multiplier: Mapped[float] = mapped_column(Float, default=1.0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)


class SavedLocationPin(Base):
    __tablename__ = "saved_location_pins"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    label: Mapped[str] = mapped_column(String(80), nullable=False)
    address: Mapped[str] = mapped_column(String(255), nullable=False)
    latitude: Mapped[float] = mapped_column(Float, nullable=False)
    longitude: Mapped[float] = mapped_column(Float, nullable=False)
    pin_type: Mapped[str] = mapped_column(String(24), default="PICKUP", nullable=False)
    entrance_notes: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    entrance_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)


class RoadRestriction(Base):
    __tablename__ = "road_restrictions"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[str] = mapped_column(String(512), nullable=False)
    restriction_type: Mapped[str] = mapped_column(String(32), default="ROAD_CLOSURE")  # ROAD_CLOSURE, VIP_CORRIDOR, MONSOON_WATERLOGGING, ZERO_EMISSION_ONLY
    latitude: Mapped[float] = mapped_column(Float, nullable=False)
    longitude: Mapped[float] = mapped_column(Float, nullable=False)
    radius_meters: Mapped[float] = mapped_column(Float, default=500.0)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    starts_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    ends_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class SurgeRule(Base):
    __tablename__ = "surge_rules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    zone_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("geofence_zones.id"), nullable=True)
    category_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("vehicle_categories.id"), nullable=True)
    multiplier: Mapped[float] = mapped_column(Float, default=1.2)
    max_multiplier: Mapped[float] = mapped_column(Float, default=3.0)
    reason: Mapped[str] = mapped_column(String(255), default="High Demand")
    starts_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    ends_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class BroadcastNotification(Base):
    __tablename__ = "broadcast_notifications"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    target_audience: Mapped[str] = mapped_column(String(30), default="ALL")  # ALL, CUSTOMERS, DRIVERS
    status: Mapped[str] = mapped_column(String(20), default="QUEUED")  # QUEUED, SENT, CANCELLED
    dispatched_count: Mapped[int] = mapped_column(Integer, default=0)
    scheduled_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    sent_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    channel: Mapped[str] = mapped_column(String(20), default="IN_APP", nullable=False)
    audience_filter_json: Mapped[str] = mapped_column(Text, default="{}", nullable=False)
    localized_content_json: Mapped[str] = mapped_column(Text, default="{}", nullable=False)
    delivery_success_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    delivery_failure_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    opt_out_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    is_emergency: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    preview_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)


class RideStatusEvent(Base):
    __tablename__ = "ride_status_events"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    previous_status: Mapped[Optional[str]] = mapped_column(String(30), nullable=True)
    new_status: Mapped[str] = mapped_column(String(30), nullable=False)
    triggered_by_user_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    trigger_actor: Mapped[str] = mapped_column(String(20), default="SYSTEM")
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    metadata_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    ride: Mapped["Ride"] = relationship(back_populates="events")


class RideAssignment(Base):
    __tablename__ = "ride_assignments"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    status: Mapped[str] = mapped_column(String(20), default="OFFERED")
    offered_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    expires_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True, index=True)
    escalation_level: Mapped[int] = mapped_column(Integer, default=0)
    responded_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    rejection_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    is_accepted: Mapped[Optional[bool]] = mapped_column(Boolean, nullable=True, default=None)
    metadata_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

    ride: Mapped["Ride"] = relationship(back_populates="assignments")


class Payment(Base):
    __tablename__ = "payments"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), unique=True, nullable=False)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False)
    driver_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=True)
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    currency: Mapped[str] = mapped_column(String(10), default="INR")
    payment_method: Mapped[str] = mapped_column(String(20), default="UPI")  # UPI, CASH, WALLET
    status: Mapped[str] = mapped_column(String(20), default="PENDING")
    provider_name: Mapped[str] = mapped_column(String(30), default="RAZORPAY")
    provider_order_id: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    provider_payment_id: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    provider_signature: Mapped[Optional[str]] = mapped_column(String(256), nullable=True)
    idempotency_key: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    cash_collected_confirmed: Mapped[bool] = mapped_column(Boolean, default=False)
    cash_confirmed_by_driver_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    
    # Part 4 Settlements Additions (Features 85, 87, 89, 95)
    refunded_amount_cents: Mapped[int] = mapped_column(Integer, default=0)
    upi_intent_uri: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    saved_method_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    corporate_account_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    split_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    ride: Mapped["Ride"] = relationship(back_populates="payment")


class DriverRating(Base):
    __tablename__ = "driver_ratings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), unique=True, nullable=False)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False)
    rating: Mapped[int] = mapped_column(Integer, nullable=False)
    feedback: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    
    # Part 7 Categorical Feedback, Tags & Private Notes (Features 151, 152, 153, 154, 155, 156)
    cleanliness_score: Mapped[int] = mapped_column(Integer, default=5)
    comfort_score: Mapped[int] = mapped_column(Integer, default=5)
    service_quality_score: Mapped[int] = mapped_column(Integer, default=5)
    navigation_score: Mapped[int] = mapped_column(Integer, default=5)
    feedback_tags_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    private_feedback: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    tip_cents: Mapped[int] = mapped_column(Integer, default=0)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    ride: Mapped["Ride"] = relationship(back_populates="rating")


class DeviceToken(Base):
    __tablename__ = "device_tokens"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    fcm_token: Mapped[str] = mapped_column(String(512), unique=True, index=True, nullable=False)
    device_os: Mapped[str] = mapped_column(String(20), default="ANDROID")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    last_error_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    last_used_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    user: Mapped["User"] = relationship(back_populates="device_tokens")


class Notification(Base):
    __tablename__ = "notifications"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    data_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    event_type: Mapped[str] = mapped_column(String(64), nullable=False)
    is_read: Mapped[bool] = mapped_column(Boolean, default=False)
    delivery_status: Mapped[str] = mapped_column(String(20), default="QUEUED")
    delivery_provider_id: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    channel: Mapped[str] = mapped_column(String(16), default="IN_APP", nullable=False)
    critical: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    dedupe_key: Mapped[Optional[str]] = mapped_column(String(160), nullable=True)
    action_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    scheduled_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    next_attempt_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True, index=True)
    attempt_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    last_attempt_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    last_error: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    sent_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    __table_args__ = (UniqueConstraint("dedupe_key", name="uq_notifications_dedupe_key"),)

    user: Mapped["User"] = relationship(back_populates="notifications")


class NotificationTemplate(Base):
    __tablename__ = "notification_templates"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    template_key: Mapped[str] = mapped_column(String(100), nullable=False, index=True)
    locale: Mapped[str] = mapped_column(String(10), default="en", nullable=False)
    channel: Mapped[str] = mapped_column(String(16), default="PUSH", nullable=False)
    title_template: Mapped[str] = mapped_column(String(128), nullable=False)
    body_template: Mapped[str] = mapped_column(Text, nullable=False)
    required_variables_json: Mapped[str] = mapped_column(Text, default="[]", nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    updated_by: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False)
    __table_args__ = (UniqueConstraint("template_key", "locale", "channel", name="uq_notification_template_variant"),)


class Session(Base):
    __tablename__ = "sessions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    ip_address: Mapped[Optional[str]] = mapped_column(String(45), nullable=True)
    user_agent: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    is_revoked: Mapped[bool] = mapped_column(Boolean, default=False)
    mfa_verified: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    user: Mapped["User"] = relationship(back_populates="sessions")


class AdminRole(Base):
    __tablename__ = "admin_roles"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    name: Mapped[str] = mapped_column(String(80), unique=True, nullable=False)
    description: Mapped[str] = mapped_column(String(255), default="")
    is_superuser: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    created_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class AdminPermission(Base):
    __tablename__ = "admin_permissions"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    permission_key: Mapped[str] = mapped_column(String(80), unique=True, nullable=False)
    description: Mapped[str] = mapped_column(String(255), nullable=False)
    category: Mapped[str] = mapped_column(String(40), nullable=False)


class AdminRolePermission(Base):
    __tablename__ = "admin_role_permissions"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    role_id: Mapped[str] = mapped_column(String(36), ForeignKey("admin_roles.id", ondelete="CASCADE"), nullable=False)
    permission_id: Mapped[str] = mapped_column(String(36), ForeignKey("admin_permissions.id", ondelete="CASCADE"), nullable=False)
    __table_args__ = (UniqueConstraint("role_id", "permission_id", name="uq_admin_role_permission"),)


class AdminUserRole(Base):
    __tablename__ = "admin_user_roles"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    role_id: Mapped[str] = mapped_column(String(36), ForeignKey("admin_roles.id", ondelete="CASCADE"), nullable=False)
    assigned_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    assigned_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    __table_args__ = (UniqueConstraint("user_id", "role_id", name="uq_admin_user_role"),)


class SupportEscalationPolicy(Base):
    __tablename__ = "support_escalation_policies"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    name: Mapped[str] = mapped_column(String(100), nullable=False, unique=True)
    priority: Mapped[str] = mapped_column(String(16), nullable=False)
    sla_minutes: Mapped[int] = mapped_column(Integer, nullable=False)
    escalation_minutes: Mapped[int] = mapped_column(Integer, nullable=False)
    target_squad: Mapped[str] = mapped_column(String(50), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    updated_by: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)


class BroadcastDelivery(Base):
    __tablename__ = "broadcast_deliveries"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    broadcast_id: Mapped[str] = mapped_column(String(36), ForeignKey("broadcast_notifications.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    notification_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("notifications.id"), nullable=True)
    status: Mapped[str] = mapped_column(String(20), nullable=False)
    language_code: Mapped[str] = mapped_column(String(10), default="en", nullable=False)
    failure_reason: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    __table_args__ = (UniqueConstraint("broadcast_id", "user_id", name="uq_broadcast_delivery_user"),)


class PooledTrip(Base):
    __tablename__ = "pooled_trips"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    vehicle_category_id: Mapped[str] = mapped_column(String(36), ForeignKey("vehicle_categories.id"), nullable=False, index=True)
    driver_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=True, index=True)
    vehicle_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("vehicles.id"), nullable=True)
    status: Mapped[str] = mapped_column(String(30), default="OPEN", index=True)  # OPEN, ACTIVE, FULL, COMPLETED, CANCELLED
    max_seats: Mapped[int] = mapped_column(Integer, default=4)
    reserved_seats: Mapped[int] = mapped_column(Integer, default=0)
    current_occupancy: Mapped[int] = mapped_column(Integer, default=0)
    pricing_policy_version: Mapped[int] = mapped_column(Integer, default=1)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    driver: Mapped[Optional["DriverProfile"]] = relationship()
    vehicle_category: Mapped["VehicleCategory"] = relationship()
    bookings: Mapped[List["PooledBooking"]] = relationship(back_populates="pooled_trip")
    stops: Mapped[List["PooledTripStop"]] = relationship(back_populates="pooled_trip", cascade="all, delete-orphan", order_by="PooledTripStop.sequence_order")

    __table_args__ = (
        Index("ix_pooled_trips_status_seats", "status", "max_seats", "current_occupancy"),
    )


class PooledBooking(Base):
    __tablename__ = "pooled_bookings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    pooled_trip_id: Mapped[str] = mapped_column(String(36), ForeignKey("pooled_trips.id"), nullable=False, index=True)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), unique=True, nullable=False, index=True)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    seats_reserved: Mapped[int] = mapped_column(Integer, default=1)
    booking_status: Mapped[str] = mapped_column(String(30), default="PENDING", index=True)  # PENDING, CONFIRMED, BOARDED, COMPLETED, CANCELLED, NO_SHOW
    allocated_fare_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    base_fare_split_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    distance_time_fare_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    discount_cents: Mapped[int] = mapped_column(Integer, default=0)
    driver_earnings_cents: Mapped[int] = mapped_column(Integer, default=0)
    platform_commission_cents: Mapped[int] = mapped_column(Integer, default=0)
    pickup_eta_minutes: Mapped[float] = mapped_column(Float, default=0.0)
    share_stop_details_with_co_passengers: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    cancellation_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    pooled_trip: Mapped["PooledTrip"] = relationship(back_populates="bookings")
    ride: Mapped["Ride"] = relationship(back_populates="pooled_booking")
    customer: Mapped["CustomerProfile"] = relationship()
    stops: Mapped[List["PooledTripStop"]] = relationship(back_populates="booking", cascade="all, delete-orphan")


class PooledTripStop(Base):
    __tablename__ = "pooled_trip_stops"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    pooled_trip_id: Mapped[str] = mapped_column(String(36), ForeignKey("pooled_trips.id"), nullable=False, index=True)
    booking_id: Mapped[str] = mapped_column(String(36), ForeignKey("pooled_bookings.id"), nullable=False, index=True)
    stop_type: Mapped[str] = mapped_column(String(20), nullable=False, index=True)  # PICKUP, DROPOFF
    sequence_order: Mapped[int] = mapped_column(Integer, nullable=False)
    address: Mapped[str] = mapped_column(String(255), nullable=False)
    latitude: Mapped[float] = mapped_column(Float, nullable=False)
    longitude: Mapped[float] = mapped_column(Float, nullable=False)
    planned_arrival_time: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    actual_arrival_time: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    status: Mapped[str] = mapped_column(String(20), default="SCHEDULED")  # SCHEDULED, REACHED, SKIPPED
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    pooled_trip: Mapped["PooledTrip"] = relationship(back_populates="stops")
    booking: Mapped["PooledBooking"] = relationship(back_populates="stops")


class PooledTripMessage(Base):
    __tablename__ = "pooled_trip_messages"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    pooled_trip_id: Mapped[str] = mapped_column(String(36), ForeignKey("pooled_trips.id"), nullable=False, index=True)
    booking_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("pooled_bookings.id"), nullable=True, index=True)
    sender_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    sender_role: Mapped[str] = mapped_column(String(20), nullable=False)
    message: Mapped[str] = mapped_column(String(1000), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, index=True)


class SharedRidePreference(Base):
    __tablename__ = "shared_ride_preferences"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, unique=True)
    maximum_detour_km: Mapped[float] = mapped_column(Float, nullable=False, default=5.0)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)


class AuditLog(Base):
    __tablename__ = "audit_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    actor_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True, index=True)
    actor_role: Mapped[str] = mapped_column(String(30), default="SYSTEM", index=True)  # customer, driver, admin, SYSTEM
    action: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    target_resource: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    target_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True, index=True)
    status: Mapped[str] = mapped_column(String(20), default="SUCCESS")  # SUCCESS, FAILURE, REJECTED
    correlation_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True, index=True)
    ip_address: Mapped[Optional[str]] = mapped_column(String(45), nullable=True)
    user_agent: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    metadata_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)  # sensitive data redacted
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, index=True)

    __table_args__ = (
        Index("ix_audit_logs_action_time", "action", "created_at"),
    )


class SavedPaymentMethod(Base):
    __tablename__ = "saved_payment_methods"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    method_type: Mapped[str] = mapped_column(String(20), default="CARD")  # CARD, UPI_VPA, NET_BANKING
    brand: Mapped[str] = mapped_column(String(30), default="VISA")         # VISA, MASTERCARD, AMEX, RUPAY, BHIM_UPI
    last4: Mapped[str] = mapped_column(String(4), default="0000")
    exp_month: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    exp_year: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    vpa_address: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    provider_token_id: Mapped[str] = mapped_column(String(128), nullable=False)  # RBI compliant token ref
    cardholder_name_mask: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    issuer_bank: Mapped[Optional[str]] = mapped_column(String(64), default="HDFC Bank")
    is_default: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class RideFareSplit(Base):
    __tablename__ = "ride_fare_splits"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    inviter_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    payer_phone: Mapped[str] = mapped_column(String(32), nullable=False)
    payer_name: Mapped[str] = mapped_column(String(128), nullable=False)
    payer_user_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    share_amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="PENDING")  # PENDING, ACCEPTED, PAID, DECLINED
    payment_method: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    paid_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class PaymentRefund(Base):
    __tablename__ = "payment_refunds"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    payment_id: Mapped[str] = mapped_column(String(36), ForeignKey("payments.id"), nullable=False, index=True)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False)
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    is_partial: Mapped[bool] = mapped_column(Boolean, default=False)
    reason: Mapped[str] = mapped_column(String(255), nullable=False)
    status: Mapped[str] = mapped_column(String(30), default="INITIATED")  # INITIATED, GATEWAY_PROCESSING, CREDITED, FAILED
    arn_reference: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)  # Bank ARN
    estimated_settlement_date: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    approval_status: Mapped[str] = mapped_column(String(24), default="APPROVED", nullable=False)
    requested_by_admin_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    reviewed_by_admin_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    reviewed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    review_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)


class FareDispute(Base):
    __tablename__ = "fare_disputes"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    dispute_reason: Mapped[str] = mapped_column(String(64), nullable=False)  # INCORRECT_ROUTE, EXTRA_WAIT_CHARGED, SURGE_MISTAKE, DRIVER_EARLY_STOP, OTHER
    claimed_amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    customer_comment: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(String(30), default="SUBMITTED")  # SUBMITTED, UNDER_INVESTIGATION, RESOLVED_REFUNDED, REJECTED
    refund_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("payment_refunds.id"), nullable=True)
    admin_resolution_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    resolved_by_admin_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    resolved_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class TripShareLink(Base):
    __tablename__ = "trip_share_links"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    share_token: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    created_by_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    recipient_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    recipient_phone: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    hide_exact_address: Mapped[bool] = mapped_column(Boolean, default=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    access_count: Mapped[int] = mapped_column(Integer, default=0)
    last_accessed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    ride: Mapped["Ride"] = relationship()


class LostItemReport(Base):
    __tablename__ = "lost_item_reports"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ticket_number: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    item_type: Mapped[str] = mapped_column(String(50), nullable=False)  # PHONE, WALLET, BAG, KEYS, JEWELRY, ELECTRONICS, OTHER
    description: Mapped[str] = mapped_column(Text, nullable=False)
    seat_location: Mapped[str] = mapped_column(String(50), default="BACK_SEAT")  # BACK_LEFT, BACK_RIGHT, BOOT_LUGGAGE, FRONT_PASSENGER
    contact_phone: Mapped[str] = mapped_column(String(32), nullable=False)
    status: Mapped[str] = mapped_column(String(30), default="REPORTED")  # REPORTED, DRIVER_CONTACTED, LOCATED, IN_TRANSIT, DELIVERED, CLOSED
    driver_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    concierge_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    reported_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    resolved_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    ride: Mapped["Ride"] = relationship()


class EmergencyContact(Base):
    __tablename__ = "emergency_contacts"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    name: Mapped[str] = mapped_column(String(128), nullable=False)
    relationship: Mapped[str] = mapped_column(String(64), default="Family")  # Family, Spouse, Executive Assistant, Security Detail, Other
    phone_number: Mapped[str] = mapped_column(String(32), nullable=False)
    notify_on_trip_start: Mapped[bool] = mapped_column(Boolean, default=True)
    notify_on_arrival: Mapped[bool] = mapped_column(Boolean, default=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    user: Mapped["User"] = orm_relationship(back_populates="emergency_contacts")


class IncidentReport(Base):
    __tablename__ = "incident_reports"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ticket_number: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True, index=True)
    reporter_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    reporter_role: Mapped[str] = mapped_column(String(20), default="CUSTOMER")  # CUSTOMER, DRIVER
    category: Mapped[str] = mapped_column(String(64), nullable=False)  # AGGRESSIVE_DRIVING, HARASSMENT_MISCONDUCT, VEHICLE_DEFECT, ROUTE_ALTERATION, CABIN_HYGIENE, PASSENGER_DISORDER, OTHER
    severity: Mapped[str] = mapped_column(String(20), default="MEDIUM")  # LOW, MEDIUM, HIGH, CRITICAL
    description: Mapped[str] = mapped_column(Text, nullable=False)
    photo_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    status: Mapped[str] = mapped_column(String(30), default="OPEN")  # OPEN, INVESTIGATING, ESCALATED, RESOLVED, DISMISSED
    assigned_agent_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    resolution_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    follow_up_action: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)  # DRIVER_SUSPENDED, WARNING_ISSUED, REFUND_ISSUED, EXONERATED
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    resolved_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class EmergencyAlert(Base):
    __tablename__ = "emergency_alerts"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    triggered_by_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    alert_type: Mapped[str] = mapped_column(String(32), default="SOS_BUTTON")  # SOS_BUTTON, MISSED_CHECKIN, ROUTE_ANOMALY
    latitude: Mapped[float] = mapped_column(Float, nullable=False)
    longitude: Mapped[float] = mapped_column(Float, nullable=False)
    status: Mapped[str] = mapped_column(String(30), default="ACTIVE")  # ACTIVE, DISPATCHED, RESOLVED, FALSE_ALARM
    contacts_notified_count: Mapped[int] = mapped_column(Integer, default=0)
    soc_agent_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    resolved_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    ride: Mapped["Ride"] = relationship()


class DriverOnboardingTicket(Base):
    __tablename__ = "driver_onboarding_tickets"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    subject: Mapped[str] = mapped_column(String(255), nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(String(30), default="OPEN")  # OPEN, IN_REVIEW, RESOLVED
    admin_response: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    resolved_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    driver: Mapped["DriverProfile"] = relationship()


class DriverFatigueCheck(Base):
    __tablename__ = "driver_fatigue_checks"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True, index=True)
    result: Mapped[str] = mapped_column(String(24), nullable=False)  # FIT, NEEDS_REST, UNFIT
    note: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, index=True)


class IncidentEvidence(Base):
    __tablename__ = "incident_evidence"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    incident_report_id: Mapped[str] = mapped_column(String(36), ForeignKey("incident_reports.id"), nullable=False, index=True)
    uploaded_by_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    storage_path: Mapped[str] = mapped_column(String(512), nullable=False)
    mime_type: Mapped[str] = mapped_column(String(100), nullable=False)
    file_size_bytes: Mapped[int] = mapped_column(Integer, nullable=False)
    file_hash_sha256: Mapped[str] = mapped_column(String(64), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class DriverShift(Base):
    __tablename__ = "driver_shifts"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    vehicle_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("vehicles.id"), nullable=True)
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    ended_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    status: Mapped[str] = mapped_column(String(30), default="ACTIVE")  # ACTIVE, PAUSED_BREAK, COMPLETED
    start_odometer_km: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    end_odometer_km: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    online_minutes: Mapped[int] = mapped_column(Integer, default=0)
    on_trip_minutes: Mapped[int] = mapped_column(Integer, default=0)
    idle_minutes: Mapped[int] = mapped_column(Integer, default=0)
    break_minutes: Mapped[int] = mapped_column(Integer, default=0)
    continuous_driving_minutes: Mapped[int] = mapped_column(Integer, default=0)
    last_break_prompt_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    trips_completed: Mapped[int] = mapped_column(Integer, default=0)
    shift_gross_earnings_cents: Mapped[int] = mapped_column(Integer, default=0)
    shift_net_earnings_cents: Mapped[int] = mapped_column(Integer, default=0)
    shift_commission_cents: Mapped[int] = mapped_column(Integer, default=0)
    shift_tips_cents: Mapped[int] = mapped_column(Integer, default=0)
    shift_forecast_cents: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    driver: Mapped["DriverProfile"] = relationship()


class DriverWeeklyGoal(Base):
    __tablename__ = "driver_weekly_goals"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    week_start_date: Mapped[str] = mapped_column(String(10), nullable=False, index=True)  # YYYY-MM-DD
    target_earnings_cents: Mapped[int] = mapped_column(Integer, default=2500000)  # Default ₹25,000
    current_earnings_cents: Mapped[int] = mapped_column(Integer, default=0)
    target_trips: Mapped[int] = mapped_column(Integer, default=30)
    current_trips: Mapped[int] = mapped_column(Integer, default=0)
    is_achieved: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    driver: Mapped["DriverProfile"] = relationship()
    __table_args__ = (UniqueConstraint("driver_id", "week_start_date", name="uq_driver_weekly_goal"),)


class DriverPayoutAccount(Base):
    __tablename__ = "driver_payout_accounts"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    account_type: Mapped[str] = mapped_column(String(20), default="BANK_ACCOUNT")  # BANK_ACCOUNT, UPI
    bank_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    account_number_mask: Mapped[str] = mapped_column(String(32), nullable=False)
    ifsc_code: Mapped[str] = mapped_column(String(20), nullable=False)
    upi_vpa: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    beneficiary_name: Mapped[str] = mapped_column(String(128), nullable=False)
    verification_status: Mapped[str] = mapped_column(String(30), default="VERIFIED")  # PENDING_VERIFICATION, VERIFIED, REJECTED
    rejection_reason: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    is_primary: Mapped[bool] = mapped_column(Boolean, default=False)
    penny_drop_reference: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    driver: Mapped["DriverProfile"] = relationship()


class DriverLedgerEntry(Base):
    __tablename__ = "driver_ledger_entries"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    entry_type: Mapped[str] = mapped_column(String(10), nullable=False)  # CREDIT, DEBIT
    category: Mapped[str] = mapped_column(String(40), nullable=False)  # RIDE_NET_FARE, RIDE_TIP, INCENTIVE_BONUS, SURGE_BONUS, TOLL_REIMBURSEMENT, PAYOUT_WITHDRAWAL, EXPENSE_DEDUCTION, ADVANCE_REPAYMENT, ADJUSTMENT
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    balance_after_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    reference_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    description: Mapped[str] = mapped_column(String(255), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    driver: Mapped["DriverProfile"] = relationship()


class DriverIncentiveProgress(Base):
    __tablename__ = "driver_incentive_progress"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    program_code: Mapped[str] = mapped_column(String(64), nullable=False)  # WEEKEND_SOVEREIGN_RUSH, AIRPORT_VELOCITY_QUEST, EXCELLENCE_STREAK
    program_name: Mapped[str] = mapped_column(String(128), nullable=False)
    tier_name: Mapped[str] = mapped_column(String(32), default="BRONZE")  # BRONZE, SILVER, SOVEREIGN_GOLD
    target_trips: Mapped[int] = mapped_column(Integer, nullable=False)
    current_trips: Mapped[int] = mapped_column(Integer, default=0)
    reward_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    is_unlocked: Mapped[bool] = mapped_column(Boolean, default=False)
    is_paid: Mapped[bool] = mapped_column(Boolean, default=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    is_eligible: Mapped[bool] = mapped_column(Boolean, default=True)
    eligibility_notes: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    driver: Mapped["DriverProfile"] = relationship()


class RouteLatencyMetric(Base):
    __tablename__ = "route_latency_metrics"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    route_path: Mapped[str] = mapped_column(String(255), index=True, nullable=False)
    http_method: Mapped[str] = mapped_column(String(10), default="GET")
    avg_latency_ms: Mapped[float] = mapped_column(Float, nullable=False)
    p95_latency_ms: Mapped[float] = mapped_column(Float, nullable=False)
    p99_latency_ms: Mapped[float] = mapped_column(Float, nullable=False)
    request_count: Mapped[int] = mapped_column(Integer, default=1)
    error_count: Mapped[int] = mapped_column(Integer, default=0)
    time_bucket: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, index=True)



# =====================================================================
# DECOMMISSIONED ENTERPRISE BLOAT MODELS (COMPATIBILITY STUBS)
# These models have been dropped from database schema to optimize performance.
# =====================================================================

class DroppedModelStub:
    """Placeholder for purged enterprise bloat models."""
    def __init__(self, *args, **kwargs):
        for k, v in kwargs.items():
            setattr(self, k, v)
    def __repr__(self):
        return f"<DroppedModelStub {self.__class__.__name__}>"

ABTestExperiment = DroppedModelStub
AccountDeletionRequest = DroppedModelStub
AddressQualityFeedback = DroppedModelStub
AdminActionJustification = DroppedModelStub
AdminCalendarEvent = DroppedModelStub
AdminRecordNote = DroppedModelStub
AdminReviewTemplate = DroppedModelStub
AdminWalletAdjustmentRequest = DroppedModelStub
AdministrativeAuditLog = DroppedModelStub
AirportTransferPackage = DroppedModelStub
ApiErrorCatalogEntry = DroppedModelStub
ApiVersionRecord = DroppedModelStub
BackgroundJob = DroppedModelStub
BackupPolicy = DroppedModelStub
BusinessApprovalRequest = DroppedModelStub
ChauffeurServiceAddon = DroppedModelStub
CommissionRule = DroppedModelStub
CorporateAccount = DroppedModelStub
CorporateInvoice = DroppedModelStub
CorporateTravelPolicy = DroppedModelStub
CustomReportConfig = DroppedModelStub
CustomerMilestoneBadge = DroppedModelStub
CustomerRidePass = DroppedModelStub
CustomerSubscription = DroppedModelStub
CustomerSurvey = DroppedModelStub
DataExportArchive = DroppedModelStub
DataRetentionPolicy = DroppedModelStub
DatabaseMigrationVerification = DroppedModelStub
DependencyVulnerabilityReport = DroppedModelStub
DisasterRecoveryPlan = DroppedModelStub
DistributedTraceSpan = DroppedModelStub
DriverBatteryOptimizationSetting = DroppedModelStub
DriverBlock = DroppedModelStub
DriverDispatchPreferences = DroppedModelStub
DriverFinancialSupportRequest = DroppedModelStub
DriverOperatingExpense = DroppedModelStub
DriverPayoutReconciliation = DroppedModelStub
DriverPayoutSchedule = DroppedModelStub
DriverRatingAppeal = DroppedModelStub
DriverSafetyPreferences = DroppedModelStub
DriverScheduleAvailability = DroppedModelStub
DriverTrainingModule = DroppedModelStub
DriverTrainingProgress = DroppedModelStub
DriverVehicleInspectionPhoto = DroppedModelStub
DriverVehicleInspection = DroppedModelStub
EmployeeProfile = DroppedModelStub
EventTransportWorkspace = DroppedModelStub
FeatureFlag = DroppedModelStub
FeatureRequestVote = DroppedModelStub
FleetSubscriptionPlan = DroppedModelStub
GeofenceZoneAudit = DroppedModelStub
GiftCard = DroppedModelStub
HelpArticleRating = DroppedModelStub
HelpArticle = DroppedModelStub
HourlyChauffeurBooking = DroppedModelStub
IdempotencyRecord = DroppedModelStub
IntercityProduct = DroppedModelStub
InvoiceCorrectionRequest = DroppedModelStub
LocalizationCatalog = DroppedModelStub
LocationRetentionSetting = DroppedModelStub
LoyaltyPartnerCatalog = DroppedModelStub
LoyaltyPointsLedger = DroppedModelStub
MobileCrashReport = DroppedModelStub
MultiCityOperatingConfig = DroppedModelStub
NotificationConsentAudit = DroppedModelStub
NotificationDeliveryAttempt = DroppedModelStub
PartnerAccount = DroppedModelStub
PartnerBooking = DroppedModelStub
PassengerOccupancyEvent = DroppedModelStub
PaymentEvent = DroppedModelStub
PaymentWebhookInbox = DroppedModelStub
PersonalizedOffer = DroppedModelStub
PrepaidRideBundle = DroppedModelStub
PrivacyDataSubjectRequest = DroppedModelStub
ProductFeedbackAnalytics = DroppedModelStub
ProviderHealthConfig = DroppedModelStub
RecurringRideSchedule = DroppedModelStub
RedisNamespaceConfig = DroppedModelStub
RestoreDrillSchedule = DroppedModelStub
RideDestinationChangeRequest = DroppedModelStub
RideOperationalEvent = DroppedModelStub
RidePassRedemption = DroppedModelStub
RidePass = DroppedModelStub
ScheduledJobMonitor = DroppedModelStub
ScheduledReportDelivery = DroppedModelStub
SecretRotationWorkflow = DroppedModelStub
SensitiveRecordAccessLog = DroppedModelStub
ServiceIncidentAdvisory = DroppedModelStub
ServiceRecoveryCredit = DroppedModelStub
SupportCallbackRequest = DroppedModelStub
SupportResponseTemplate = DroppedModelStub
TourismPlace = DroppedModelStub
TripTemplate = DroppedModelStub
TrustedDevice = DroppedModelStub
UserDependent = DroppedModelStub
UserNotificationSettings = DroppedModelStub
UserPreferences = DroppedModelStub
UserSavedPhone = DroppedModelStub
WhiteLabelTenantConfig = DroppedModelStub
