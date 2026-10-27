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
    preferences: Mapped[Optional["UserPreferences"]] = relationship(back_populates="user", uselist=False)
    saved_phones: Mapped[List["UserSavedPhone"]] = relationship(back_populates="user", cascade="all, delete-orphan")
    trusted_devices: Mapped[List["TrustedDevice"]] = relationship(back_populates="user", cascade="all, delete-orphan")
    deletion_requests: Mapped[List["AccountDeletionRequest"]] = relationship(back_populates="user", cascade="all, delete-orphan")
    data_archives: Mapped[List["DataExportArchive"]] = relationship(back_populates="user", cascade="all, delete-orphan")
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
    passes: Mapped[List["CustomerRidePass"]] = relationship(back_populates="customer")
    recurring_schedules: Mapped[List["RecurringRideSchedule"]] = relationship(back_populates="customer", cascade="all, delete-orphan")
    trip_templates: Mapped[List["TripTemplate"]] = relationship(back_populates="customer", cascade="all, delete-orphan")
    dependents: Mapped[List["UserDependent"]] = relationship(back_populates="customer", cascade="all, delete-orphan")

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

class RidePass(Base):
    __tablename__ = "ride_passes"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    code: Mapped[str] = mapped_column(String(32), unique=True, nullable=False)
    name: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[str] = mapped_column(Text, default="")
    price_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    validity_days: Mapped[int] = mapped_column(Integer, nullable=False)
    total_rides: Mapped[int] = mapped_column(Integer, nullable=False)
    discount_percentage: Mapped[float] = mapped_column(Float, default=20.0)
    max_discount_per_ride_cents: Mapped[int] = mapped_column(Integer, default=50000)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class CustomerRidePass(Base):
    __tablename__ = "customer_ride_passes"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    pass_id: Mapped[str] = mapped_column(String(36), ForeignKey("ride_passes.id"), nullable=False)
    rides_remaining: Mapped[int] = mapped_column(Integer, nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="ACTIVE")  # ACTIVE, EXPIRED, EXHAUSTED
    valid_from: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    valid_until: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    customer: Mapped["CustomerProfile"] = relationship(back_populates="passes")
    pass_info: Mapped["RidePass"] = relationship()

class RidePassRedemption(Base):
    __tablename__ = "ride_pass_redemptions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_pass_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_ride_passes.id"), nullable=False)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), unique=True, nullable=False)
    discount_applied_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    redeemed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

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


class AddressQualityFeedback(Base):
    __tablename__ = "address_quality_feedback"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    rating: Mapped[int] = mapped_column(Integer, nullable=False)
    issue_code: Mapped[str] = mapped_column(String(32), nullable=False)
    provider_mode: Mapped[str] = mapped_column(String(32), nullable=False)
    coordinates_verified: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False, index=True)


class GeofenceZoneAudit(Base):
    __tablename__ = "geofence_zone_audits"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    zone_id: Mapped[str] = mapped_column(String(36), ForeignKey("geofence_zones.id"), nullable=False, index=True)
    admin_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    action: Mapped[str] = mapped_column(String(24), nullable=False)
    previous_state_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    new_state_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False, index=True)


# Part 18: Location Operations, GPS Governance & Road Restrictions Models
class LocationRetentionSetting(Base):
    __tablename__ = "location_retention_settings"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    category: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)  # DRIVER_RAW_TELEMETRY, PASSENGER_SEARCH_HISTORY, ANONYMIZED_HEATMAP_CELLS
    retention_ttl_days: Mapped[int] = mapped_column(Integer, default=90, nullable=False)
    auto_purge_enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    last_purged_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)


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


class DriverBatteryOptimizationSetting(Base):
    __tablename__ = "driver_battery_optimization_settings"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=True, index=True)
    mode: Mapped[str] = mapped_column(String(32), default="ADAPTIVE")  # ADAPTIVE, HIGH_PERFORMANCE, BATTERY_SAVER
    stationary_interval_sec: Mapped[int] = mapped_column(Integer, default=30)
    city_interval_sec: Mapped[int] = mapped_column(Integer, default=10)
    highway_interval_sec: Mapped[int] = mapped_column(Integer, default=5)
    displacement_threshold_m: Mapped[float] = mapped_column(Float, default=15.0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

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

class CommissionRule(Base):
    __tablename__ = "commission_rules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    category_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("vehicle_categories.id"), nullable=True)
    version: Mapped[int] = mapped_column(Integer, default=1)
    platform_commission_percentage: Mapped[float] = mapped_column(Float, default=20.0)
    driver_share_percentage: Mapped[float] = mapped_column(Float, default=80.0)
    effective_from: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    effective_until: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    approval_status: Mapped[str] = mapped_column(String(20), default="ACTIVE", nullable=False)
    approval_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

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

# Phase 1 Models preserved
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

    ride: Mapped["Ride"] = relationship(back_populates="assignments")

class DriverDispatchPreferences(Base):
    __tablename__ = "driver_dispatch_preferences"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), unique=True, nullable=False, index=True)
    minimum_fare_cents: Mapped[int] = mapped_column(Integer, default=0)
    maximum_pickup_distance_km: Mapped[float] = mapped_column(Float, default=20.0)
    vehicle_categories_json: Mapped[str] = mapped_column(Text, default="[]")
    navigation_provider: Mapped[str] = mapped_column(String(24), default="GOOGLE_MAPS")
    voice_guidance_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    reroute_on_closure_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

class RideDestinationChangeRequest(Base):
    __tablename__ = "ride_destination_change_requests"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    requested_by_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    status: Mapped[str] = mapped_column(String(32), default="PENDING_CUSTOMER_CONFIRMATION", index=True)
    destination_address: Mapped[str] = mapped_column(String(255), nullable=False)
    destination_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    proposed_fare_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    proposed_distance_km: Mapped[float] = mapped_column(Float, nullable=False)
    proposed_duration_minutes: Mapped[float] = mapped_column(Float, nullable=False)
    fare_difference_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    customer_responded_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    driver_responded_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    rejection_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class RideOperationalEvent(Base):
    __tablename__ = "ride_operational_events"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    actor_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    action: Mapped[str] = mapped_column(String(40), nullable=False, index=True)
    details_json: Mapped[str] = mapped_column(Text, default="{}")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, index=True)

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
    events: Mapped[List["PaymentEvent"]] = relationship(back_populates="payment", cascade="all, delete-orphan")

class PaymentEvent(Base):
    __tablename__ = "payment_events"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    payment_id: Mapped[str] = mapped_column(String(36), ForeignKey("payments.id"), nullable=False, index=True)
    event_type: Mapped[str] = mapped_column(String(64), nullable=False)
    payload_json: Mapped[str] = mapped_column(Text, nullable=False)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    payment: Mapped["Payment"] = relationship(back_populates="events")

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


class UserNotificationSettings(Base):
    __tablename__ = "user_notification_settings"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), unique=True, nullable=False)
    push_enabled: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    email_enabled: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    sms_enabled: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    quiet_hours_enabled: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    quiet_start: Mapped[str] = mapped_column(String(5), default="22:00", nullable=False)
    quiet_end: Mapped[str] = mapped_column(String(5), default="07:00", nullable=False)
    timezone_name: Mapped[str] = mapped_column(String(64), default="Asia/Kolkata", nullable=False)
    language_code: Mapped[str] = mapped_column(String(10), default="en", nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False)


class NotificationConsentAudit(Base):
    __tablename__ = "notification_consent_audits"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    source: Mapped[str] = mapped_column(String(32), nullable=False)
    changes_json: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False, index=True)


class NotificationDeliveryAttempt(Base):
    __tablename__ = "notification_delivery_attempts"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    notification_id: Mapped[str] = mapped_column(String(36), ForeignKey("notifications.id", ondelete="CASCADE"), nullable=False, index=True)
    channel: Mapped[str] = mapped_column(String(16), nullable=False)
    attempt_number: Mapped[int] = mapped_column(Integer, nullable=False)
    status: Mapped[str] = mapped_column(String(20), nullable=False)
    provider_message_id: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    error_code: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    error_message: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    attempted_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False, index=True)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


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

class AdministrativeAuditLog(Base):
    __tablename__ = "administrative_audit_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    admin_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    action: Mapped[str] = mapped_column(String(64), nullable=False)
    target_resource: Mapped[str] = mapped_column(String(64), nullable=False)
    target_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    changes_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    ip_address: Mapped[Optional[str]] = mapped_column(String(45), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


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


class AdminRecordNote(Base):
    __tablename__ = "admin_record_notes"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    subject_type: Mapped[str] = mapped_column(String(16), nullable=False)
    subject_id: Mapped[str] = mapped_column(String(36), nullable=False, index=True)
    author_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    note: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class AdminCalendarEvent(Base):
    __tablename__ = "admin_calendar_events"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(120), nullable=False)
    event_date: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, index=True)
    event_type: Mapped[str] = mapped_column(String(24), nullable=False, default="HOLIDAY")
    zone_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("geofence_zones.id"), nullable=True)
    notes: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    created_by: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class AdminReviewTemplate(Base):
    __tablename__ = "admin_review_templates"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(80), nullable=False)
    reason: Mapped[str] = mapped_column(String(500), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    created_by: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class AdminWalletAdjustmentRequest(Base):
    __tablename__ = "admin_wallet_adjustment_requests"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    adjustment_type: Mapped[str] = mapped_column(String(12), nullable=False)
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="PENDING", nullable=False, index=True)
    requested_by: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    first_approved_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    second_approved_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    reviewed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    applied_transaction_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("wallet_transactions.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)


class PaymentWebhookInbox(Base):
    __tablename__ = "payment_webhook_inbox"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    provider_name: Mapped[str] = mapped_column(String(32), nullable=False)
    event_digest: Mapped[str] = mapped_column(String(64), unique=True, nullable=False, index=True)
    payload_json: Mapped[str] = mapped_column(Text, nullable=False)
    signature_verified: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="FAILED", nullable=False, index=True)
    attempts: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    last_error: Mapped[Optional[str]] = mapped_column(String(500), nullable=True)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    last_attempt_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


class SupportResponseTemplate(Base):
    __tablename__ = "support_response_templates"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(100), nullable=False)
    category: Mapped[str] = mapped_column(String(50), default="GENERAL", nullable=False)
    language_code: Mapped[str] = mapped_column(String(10), default="en", nullable=False)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    created_by: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)


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


class InvoiceCorrectionRequest(Base):
    __tablename__ = "invoice_correction_requests"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    requested_changes_json: Mapped[str] = mapped_column(Text, nullable=False)
    original_values_json: Mapped[str] = mapped_column(Text, nullable=False)
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="PENDING", nullable=False, index=True)
    requested_by: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    reviewed_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    reviewed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    review_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)


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
    occupancy_events: Mapped[List["PassengerOccupancyEvent"]] = relationship(back_populates="pooled_trip", cascade="all, delete-orphan")

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
    occupancy_events: Mapped[List["PassengerOccupancyEvent"]] = relationship(back_populates="booking")


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


class PassengerOccupancyEvent(Base):
    __tablename__ = "passenger_occupancy_events"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    pooled_trip_id: Mapped[str] = mapped_column(String(36), ForeignKey("pooled_trips.id"), nullable=False, index=True)
    booking_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("pooled_bookings.id"), nullable=True, index=True)
    event_type: Mapped[str] = mapped_column(String(40), nullable=False)  # SEATS_RESERVED, BOARDED, ALIGHTED, NO_SHOW, CANCELLED
    occupancy_before: Mapped[int] = mapped_column(Integer, nullable=False)
    occupancy_after: Mapped[int] = mapped_column(Integer, nullable=False)
    actor_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, index=True)

    pooled_trip: Mapped["PooledTrip"] = relationship(back_populates="occupancy_events")
    booking: Mapped[Optional["PooledBooking"]] = relationship(back_populates="occupancy_events")

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

class UserSavedPhone(Base):
    __tablename__ = "user_saved_phones"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    phone_number: Mapped[str] = mapped_column(String(20), nullable=False)
    label: Mapped[str] = mapped_column(String(40), default="Personal")  # Personal, Office, Family, Emergency
    is_primary: Mapped[bool] = mapped_column(Boolean, default=False)
    is_verified: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    user: Mapped["User"] = relationship(back_populates="saved_phones")

class TrustedDevice(Base):
    __tablename__ = "trusted_devices"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    device_id: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    device_name: Mapped[str] = mapped_column(String(128), default="Executive Mobile Device")
    device_os: Mapped[str] = mapped_column(String(32), default="Android")
    trust_token_hash: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    is_trusted: Mapped[bool] = mapped_column(Boolean, default=True)
    last_used_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    user: Mapped["User"] = relationship(back_populates="trusted_devices")

class UserPreferences(Base):
    __tablename__ = "user_preferences"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), unique=True, nullable=False)
    preferred_language: Mapped[str] = mapped_column(String(10), default="en")  # en, hi, mr, gu
    currency_code: Mapped[str] = mapped_column(String(10), default="INR")  # INR, USD, EUR, AED
    number_format: Mapped[str] = mapped_column(String(10), default="IN")  # IN, US, EU
    text_scale: Mapped[str] = mapped_column(String(20), default="NORMAL")  # NORMAL, LARGE, EXTRA_LARGE
    high_contrast: Mapped[bool] = mapped_column(Boolean, default=False)
    reduced_motion: Mapped[bool] = mapped_column(Boolean, default=False)
    analytics_consent: Mapped[bool] = mapped_column(Boolean, default=True)
    crash_reports_consent: Mapped[bool] = mapped_column(Boolean, default=True)
    background_location_consent: Mapped[bool] = mapped_column(Boolean, default=True)
    third_party_sharing_consent: Mapped[bool] = mapped_column(Boolean, default=False)
    marketing_push_opt_in: Mapped[bool] = mapped_column(Boolean, default=True)
    marketing_email_opt_in: Mapped[bool] = mapped_column(Boolean, default=True)
    marketing_sms_opt_in: Mapped[bool] = mapped_column(Boolean, default=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    user: Mapped["User"] = relationship(back_populates="preferences")

class AccountDeletionRequest(Base):
    __tablename__ = "account_deletion_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    status: Mapped[str] = mapped_column(String(30), default="PENDING_GRACE_PERIOD")  # PENDING_GRACE_PERIOD, PURGED, CANCELLED
    cooldown_days_total: Mapped[int] = mapped_column(Integer, default=30)
    requested_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    scheduled_purge_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    cancelled_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    cancellation_reason: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)

    user: Mapped["User"] = relationship(back_populates="deletion_requests")

class DataExportArchive(Base):
    __tablename__ = "data_export_archives"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    status: Mapped[str] = mapped_column(String(20), default="READY")  # GENERATING, READY, EXPIRED
    archive_format: Mapped[str] = mapped_column(String(10), default="JSON")
    file_size_bytes: Mapped[int] = mapped_column(Integer, default=42000)
    download_url: Mapped[str] = mapped_column(String(512), nullable=False)
    payload_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    user: Mapped["User"] = relationship(back_populates="data_archives")

class RecurringRideSchedule(Base):
    __tablename__ = "recurring_ride_schedules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    frequency: Mapped[str] = mapped_column(String(20), default="WEEKDAYS")  # DAILY, WEEKDAYS, WEEKLY
    days_of_week: Mapped[str] = mapped_column(String(64), default="MON,TUE,WED,THU,FRI")
    pickup_time_str: Mapped[str] = mapped_column(String(10), default="08:30")
    pickup_address: Mapped[str] = mapped_column(String(255), nullable=False)
    pickup_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    pickup_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_address: Mapped[str] = mapped_column(String(255), nullable=False)
    destination_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    vehicle_category_id: Mapped[str] = mapped_column(String(36), ForeignKey("vehicle_categories.id"), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    customer: Mapped["CustomerProfile"] = relationship(back_populates="recurring_schedules")

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
    intermediate_stops_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    vehicle_category_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    cabin_temperature: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    customer: Mapped["CustomerProfile"] = relationship(back_populates="trip_templates")

class UserDependent(Base):
    __tablename__ = "user_dependents"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    full_name: Mapped[str] = mapped_column(String(128), nullable=False)
    phone_number: Mapped[str] = mapped_column(String(32), nullable=False)
    relationship_label: Mapped[str] = mapped_column("relationship", String(64), default="Family")  # Parent, Child, Spouse, VIP Guest
    emergency_notes: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    customer: Mapped["CustomerProfile"] = orm_relationship(back_populates="dependents")

    def __init__(self, **kwargs):
        if "relationship" in kwargs and "relationship_label" not in kwargs:
            kwargs["relationship_label"] = kwargs.pop("relationship")
        super().__init__(**kwargs)

    @property
    def relationship(self) -> str:
        return self.relationship_label

    @relationship.setter
    def relationship(self, value: str):
        self.relationship_label = value

# =========================================================================
# Part 4 Models: SavedPaymentMethod, RideFareSplit, GiftCard, PaymentRefund, FareDispute
# =========================================================================

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

class GiftCard(Base):
    __tablename__ = "gift_cards"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    code: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    initial_value_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    remaining_value_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    sender_name: Mapped[str] = mapped_column(String(128), nullable=False)
    recipient_email: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    recipient_phone: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    message: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    purchased_by_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    redeemed_by_user_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
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

# -----------------------------------------------------------------------------
# Part 5 Models: Trip Sharing Links & Lost Item Reports (Features 107-109, 124)
# -----------------------------------------------------------------------------
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

# -----------------------------------------------------------------------------
# Part 6 Models: Emergency Contacts, Incident Reports, Driver Blocks, SOS (Features 126-150)
# -----------------------------------------------------------------------------
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

class DriverBlock(Base):
    __tablename__ = "driver_blocks"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True)
    reason: Mapped[str] = mapped_column(Text, default="Guest requested driver block")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

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

# =========================================================================
# Part 7: Loyalty, Tier Benefits, Milestones, Surveys & Service Recovery
# Features 158 - 175
# =========================================================================

class LoyaltyPointsLedger(Base):
    __tablename__ = "loyalty_points_ledger"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    transaction_type: Mapped[str] = mapped_column(String(40), nullable=False)  # EARNED_RIDE, REDEEMED_RIDE_CREDIT, STREAK_BONUS, BIRTHDAY_BONUS, REFERRAL_BONUS, RECOVERY_BONUS, ADJUSTMENT
    points_change: Mapped[int] = mapped_column(Integer, nullable=False)
    balance_after: Mapped[int] = mapped_column(Integer, nullable=False)
    reference_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    description: Mapped[str] = mapped_column(String(255), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    customer: Mapped["CustomerProfile"] = relationship()

class CustomerMilestoneBadge(Base):
    __tablename__ = "customer_milestone_badges"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    badge_code: Mapped[str] = mapped_column(String(64), nullable=False)  # FIRST_VOYAGE, CENTURION_10, ROYAL_PATRON_50, NIGHT_OWL, FIVE_STAR_GUEST, GREEN_FLEET_CHAMPION, STREAK_MASTER
    badge_name: Mapped[str] = mapped_column(String(128), nullable=False)
    badge_icon: Mapped[str] = mapped_column(String(32), default="🎖️")
    description: Mapped[str] = mapped_column(String(255), nullable=False)
    unlocked_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    customer: Mapped["CustomerProfile"] = relationship()

class PersonalizedOffer(Base):
    __tablename__ = "personalized_offers"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=True, index=True)
    offer_type: Mapped[str] = mapped_column(String(40), default="PERSONALIZED")  # BIRTHDAY, ANNIVERSARY, WEEKEND_ESCORT, STREAK_PERK, RECOVERY_PERK
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    tagline: Mapped[str] = mapped_column(String(255), default="")
    promo_code: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    discount_percent: Mapped[int] = mapped_column(Integer, default=20)
    max_discount_cents: Mapped[int] = mapped_column(Integer, default=50000)
    badge_label: Mapped[str] = mapped_column(String(64), default="EXCLUSIVE")
    starts_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    is_claimed: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    customer: Mapped[Optional["CustomerProfile"]] = relationship()

class CustomerSurvey(Base):
    __tablename__ = "customer_surveys"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True)
    survey_type: Mapped[str] = mapped_column(String(40), default="POST_TRIP_CSAT")  # POST_TRIP_CSAT, QUARTERLY_NPS, ANNUAL_EXPERIENCE
    score: Mapped[int] = mapped_column(Integer, nullable=False)  # 1 to 10 scale
    dimensions_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)  # Cleanliness, comfort, punctuality, app ease
    comments: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    customer: Mapped["CustomerProfile"] = relationship()
    ride: Mapped[Optional["Ride"]] = relationship()

class ServiceRecoveryCredit(Base):
    __tablename__ = "service_recovery_credits"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True)
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    reason_category: Mapped[str] = mapped_column(String(50), nullable=False)  # LATE_ARRIVAL, VEHICLE_DEFECT, CHAUFFEUR_CONDUCT, ROUTE_DEVIATION, GOODWILL
    notes: Mapped[str] = mapped_column(Text, nullable=False)
    issued_by_admin_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    wallet_transaction_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    status: Mapped[str] = mapped_column(String(30), default="ISSUED")  # ISSUED, REVERSED
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    customer: Mapped["CustomerProfile"] = relationship()
    ride: Mapped[Optional["Ride"]] = relationship()
    issued_by: Mapped["User"] = relationship()

# =========================================================================
# Part 8 VIP Support, Knowledge Base, Incident Advisories & Callbacks (Features 176 - 200)
# =========================================================================

class HelpArticle(Base):
    __tablename__ = "help_articles"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    slug: Mapped[str] = mapped_column(String(100), unique=True, index=True, nullable=False)
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    category: Mapped[str] = mapped_column(String(50), nullable=False, index=True)  # BOOKING, PAYMENTS, SAFETY, CHAUFFEUR, PRIVACY, CANCELLATION, LOST_FOUND
    content_markdown: Mapped[str] = mapped_column(Text, nullable=False)
    keywords: Mapped[str] = mapped_column(String(512), default="")  # comma-separated for fast searching
    helpful_count: Mapped[int] = mapped_column(Integer, default=0)
    not_helpful_count: Mapped[int] = mapped_column(Integer, default=0)
    context_tags: Mapped[str] = mapped_column(String(255), default="")  # booking_screen, airport, multi_stop, cancellation
    is_published: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

class HelpArticleRating(Base):
    __tablename__ = "help_article_ratings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    article_id: Mapped[str] = mapped_column(String(36), ForeignKey("help_articles.id"), nullable=False, index=True)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    was_helpful: Mapped[bool] = mapped_column(Boolean, nullable=False)
    feedback_text: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    article: Mapped["HelpArticle"] = relationship()
    user: Mapped["User"] = relationship()

class SupportCallbackRequest(Base):
    __tablename__ = "support_callback_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    phone_number: Mapped[str] = mapped_column(String(32), nullable=False)
    preferred_time_window: Mapped[str] = mapped_column(String(50), default="IMMEDIATE")  # IMMEDIATE, WITHIN_1_HOUR, EVENING_WINDOW
    subject: Mapped[str] = mapped_column(String(255), nullable=False)
    ticket_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("support_tickets.id"), nullable=True)
    status: Mapped[str] = mapped_column(String(30), default="PENDING")  # PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    user: Mapped["User"] = relationship()
    ticket: Mapped[Optional["SupportTicket"]] = relationship()

class ServiceIncidentAdvisory(Base):
    __tablename__ = "service_incident_advisories"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    severity: Mapped[str] = mapped_column(String(30), default="ADVISORY")  # ADVISORY, DEGRADED, OUTAGE, MAINTENANCE
    affected_service: Mapped[str] = mapped_column(String(64), nullable=False)  # DISPATCH_FLEET, PAYMENT_GATEWAYS, GPS_TELEMETRY, CONCIERGE_DESK
    description: Mapped[str] = mapped_column(Text, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    resolved_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

# =========================================================================
# Part 9 Driver Onboarding, Training, Vehicle Inspection & Preferences (Features 201 - 225)
# =========================================================================

class DriverTrainingModule(Base):
    __tablename__ = "driver_training_modules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    module_code: Mapped[str] = mapped_column(String(50), unique=True, index=True, nullable=False)
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    description: Mapped[str] = mapped_column(Text, default="")
    content_markdown: Mapped[str] = mapped_column(Text, nullable=False)
    video_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    estimated_duration_minutes: Mapped[int] = mapped_column(Integer, default=15)
    sort_order: Mapped[int] = mapped_column(Integer, default=1)
    is_mandatory: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class DriverTrainingProgress(Base):
    __tablename__ = "driver_training_progress"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    module_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_training_modules.id"), nullable=False, index=True)
    is_completed: Mapped[bool] = mapped_column(Boolean, default=False)
    assessment_score_percentage: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)  # 0 to 100
    assessment_passed: Mapped[bool] = mapped_column(Boolean, default=False)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    driver: Mapped["DriverProfile"] = relationship()
    module: Mapped["DriverTrainingModule"] = relationship()

class DriverVehicleInspection(Base):
    __tablename__ = "driver_vehicle_inspections"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    vehicle_id: Mapped[str] = mapped_column(String(36), ForeignKey("vehicles.id"), nullable=False, index=True)
    inspection_status: Mapped[str] = mapped_column(String(30), default="SUBMITTED")  # SUBMITTED, PASSED, FAILED
    checklist_json: Mapped[str] = mapped_column(Text, nullable=False)  # JSON dict of 20 check items
    photo_front_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_rear_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_left_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_right_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_interior_front_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    photo_interior_rear_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    inspector_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    inspected_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    driver: Mapped["DriverProfile"] = relationship()
    vehicle: Mapped["Vehicle"] = relationship()

class DriverVehicleInspectionPhoto(Base):
    __tablename__ = "driver_vehicle_inspection_photos"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    vehicle_id: Mapped[str] = mapped_column(String(36), ForeignKey("vehicles.id"), nullable=False, index=True)
    photo_type: Mapped[str] = mapped_column(String(32), nullable=False)
    storage_path: Mapped[str] = mapped_column(String(512), nullable=False)
    mime_type: Mapped[str] = mapped_column(String(100), nullable=False)
    file_size_bytes: Mapped[int] = mapped_column(Integer, nullable=False)
    file_hash_sha256: Mapped[str] = mapped_column(String(64), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class DriverScheduleAvailability(Base):
    __tablename__ = "driver_schedule_availabilities"
    __table_args__ = (UniqueConstraint("driver_id", "day_of_week", name="uq_driver_schedule_day"),)

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    day_of_week: Mapped[int] = mapped_column(Integer, nullable=False)  # 0 = Monday, 6 = Sunday
    start_time: Mapped[str] = mapped_column(String(10), default="08:00")
    end_time: Mapped[str] = mapped_column(String(10), default="20:00")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    is_rest_day: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    driver: Mapped["DriverProfile"] = relationship()

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

# Part 12 driver safety and performance records.
class DriverSafetyPreferences(Base):
    __tablename__ = "driver_safety_preferences"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), unique=True, nullable=False, index=True)
    share_trip_location_with_customer: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    privacy_notice_acknowledged_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

class DriverFatigueCheck(Base):
    __tablename__ = "driver_fatigue_checks"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True, index=True)
    result: Mapped[str] = mapped_column(String(24), nullable=False)  # FIT, NEEDS_REST, UNFIT
    note: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, index=True)

class DriverRatingAppeal(Base):
    __tablename__ = "driver_rating_appeals"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    rating_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_ratings.id"), unique=True, nullable=False, index=True)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(String(24), default="OPEN", nullable=False, index=True)  # OPEN, REVIEWING, UPHELD, REMOVED
    reviewer_user_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    reviewer_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    resolved_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

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


# =============================================================================
# Part 11: Driver Financials, Shift Tracking, Incentives & Payouts (Features 251–275)
# =============================================================================

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


class DriverPayoutSchedule(Base):
    __tablename__ = "driver_payout_schedules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), unique=True, nullable=False, index=True)
    schedule_type: Mapped[str] = mapped_column(String(20), default="WEEKLY")  # WEEKLY, DAILY, MANUAL
    min_threshold_cents: Mapped[int] = mapped_column(Integer, default=50000)  # ₹500
    payout_account_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("driver_payout_accounts.id"), nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    last_scheduled_run_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
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


class DriverOperatingExpense(Base):
    __tablename__ = "driver_operating_expenses"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    category: Mapped[str] = mapped_column(String(40), nullable=False)  # FUEL, EV_CHARGING, MAINTENANCE, TOLL, PARKING, CLEANING, UNIFORM, OTHER
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    liters_or_kwh: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    unit_rate_cents: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    odometer_km: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    station_or_vendor: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    receipt_photo_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    driver: Mapped["DriverProfile"] = relationship()


class DriverFinancialSupportRequest(Base):
    __tablename__ = "driver_financial_support_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    support_type: Mapped[str] = mapped_column(String(50), nullable=False)  # EMERGENCY_REPAIR, MEDICAL_ASSISTANCE, FUEL_WORKING_CAPITAL, HARDSHIP_GRANT
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    repayment_terms_weeks: Mapped[int] = mapped_column(Integer, default=4)
    status: Mapped[str] = mapped_column(String(30), default="REQUESTED")  # REQUESTED, UNDER_REVIEW, APPROVED, DISBURSED, REJECTED
    admin_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    reviewed_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    reviewed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    driver: Mapped["DriverProfile"] = relationship()


class DriverPayoutReconciliation(Base):
    __tablename__ = "driver_payout_reconciliations"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    settlement_period: Mapped[str] = mapped_column(String(32), nullable=False, index=True)  # e.g., 2026-W43
    gross_earnings_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    platform_commission_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    tax_withheld_tds_cents: Mapped[int] = mapped_column(Integer, default=0)
    net_transferred_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    bank_utr: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    status: Mapped[str] = mapped_column(String(30), default="SETTLED")  # SETTLED, RECONCILED, PENDING_SETTLEMENT, FAILED
    failure_code: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)
    failure_explanation: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    statement_pdf_path: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    generated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    driver: Mapped["DriverProfile"] = relationship()


# =============================================================================
# Part 16: Analytics, BI Reporting, Governance Auditing & DPDP Privacy (Features 376–400)
# =============================================================================

class ScheduledReportDelivery(Base):
    __tablename__ = "scheduled_report_deliveries"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    report_name: Mapped[str] = mapped_column(String(128), nullable=False)
    report_type: Mapped[str] = mapped_column(String(64), nullable=False)  # RIDE_FUNNEL, CANCELLATION_ANALYTICS, DRIVER_UTILIZATION, ACCURACY_AUDIT, COHORTS, CUSTOM
    custom_report_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    frequency: Mapped[str] = mapped_column(String(32), default="WEEKLY")  # DAILY, WEEKLY, MONTHLY
    delivery_channel: Mapped[str] = mapped_column(String(32), default="EMAIL")  # EMAIL, WEBHOOK, SFTP
    destination: Mapped[str] = mapped_column(String(255), nullable=False)  # executive@grandchauffeur.com, webhook URL
    export_format: Mapped[str] = mapped_column(String(16), default="CSV")  # CSV, JSON, PDF
    filters_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_by_admin_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    last_run_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    next_run_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    created_by: Mapped["User"] = relationship()


class CustomReportConfig(Base):
    __tablename__ = "custom_report_configs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    domain: Mapped[str] = mapped_column(String(64), default="RIDES")  # RIDES, DRIVERS, FINANCE, SAFETY, SUPPORT
    selected_metrics_json: Mapped[str] = mapped_column(Text, nullable=False)  # JSON array: ["total_requests", "completed_count", "conversion_pct", "gross_gmv_cents"]
    selected_dimensions_json: Mapped[str] = mapped_column(Text, nullable=False)  # JSON array: ["date", "zone_name", "vehicle_category", "driver_tier"]
    filter_conditions_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)  # JSON dict of criteria
    time_grouping: Mapped[str] = mapped_column(String(32), default="DAILY")  # HOURLY, DAILY, WEEKLY, MONTHLY
    created_by_admin_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    is_shared: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    created_by: Mapped["User"] = relationship()


class SensitiveRecordAccessLog(Base):
    __tablename__ = "sensitive_record_access_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    admin_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    target_user_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True, index=True)
    record_type: Mapped[str] = mapped_column(String(64), nullable=False)  # DRIVER_BANK_DETAILS, DRIVER_AADHAAR_PAN, CUSTOMER_PHONE, GPS_RAW_TELEMETRY, PAYMENT_PAN_MASK
    record_identifier: Mapped[str] = mapped_column(String(128), nullable=False)  # account_id, driver_id, ride_id
    action_type: Mapped[str] = mapped_column(String(32), default="VIEW_UNMASKED")  # VIEW_UNMASKED, EXPORT_PII, UPDATE_CRITICAL
    justification_reason: Mapped[str] = mapped_column(Text, nullable=False)
    ticket_reference: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    ip_address: Mapped[Optional[str]] = mapped_column(String(45), nullable=True)
    user_agent: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    accessed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    admin_user: Mapped["User"] = relationship(foreign_keys=[admin_user_id])


class DataRetentionPolicy(Base):
    __tablename__ = "data_retention_policies"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    data_category: Mapped[str] = mapped_column(String(64), unique=True, nullable=False, index=True)  # GPS_TELEMETRY, RIDE_CHATS, SUPPORT_AUDIO_CALLS, LOCATION_SEARCH_HISTORY, INVOICES_TAX_RECORDS
    description: Mapped[str] = mapped_column(String(255), nullable=False)
    retention_ttl_days: Mapped[int] = mapped_column(Integer, nullable=False)  # 90, 180, 365, 2555
    is_statutory_mandatory: Mapped[bool] = mapped_column(Boolean, default=False)  # True for tax/financial records (7 years per Indian IT Act & DPDP)
    min_legal_ttl_days: Mapped[int] = mapped_column(Integer, default=30)
    purge_frequency: Mapped[str] = mapped_column(String(32), default="DAILY")  # DAILY, WEEKLY, MONTHLY
    is_purge_active: Mapped[bool] = mapped_column(Boolean, default=True)
    last_purged_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    purged_records_count: Mapped[int] = mapped_column(Integer, default=0)
    updated_by_admin_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)


class PrivacyDataSubjectRequest(Base):
    __tablename__ = "privacy_data_subject_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    request_number: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    user_role: Mapped[str] = mapped_column(String(20), default="CUSTOMER")  # CUSTOMER, DRIVER
    request_type: Mapped[str] = mapped_column(String(50), nullable=False)  # DATA_EXPORT_PORTABILITY, RIGHT_TO_ERASURE, RECTIFICATION_CORRECTION, RESTRICT_PROCESSING
    details: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    status: Mapped[str] = mapped_column(String(32), default="PENDING_VERIFICATION")  # PENDING_VERIFICATION, UNDER_PROCESSING, FULFILLED_EXPORTED, FULFILLED_ERASED, REJECTED_LEGAL_OVERRIDE
    legal_retention_exclusion_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    export_download_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    assigned_dpo_admin_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    resolution_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    fulfilled_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    user: Mapped["User"] = relationship(foreign_keys=[user_id])


class AdminActionJustification(Base):
    __tablename__ = "admin_action_justifications"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    admin_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    action_code: Mapped[str] = mapped_column(String(64), nullable=False, index=True)  # DRIVER_SUSPENSION, MANUAL_REFUND_FORCE, FARE_RULE_MODIFICATION, DSAR_ERASURE_EXECUTE, RETENTION_PURGE_OVERRIDE, SENSITIVE_RECORD_UNMASK
    target_resource_type: Mapped[str] = mapped_column(String(64), nullable=False)
    target_resource_id: Mapped[str] = mapped_column(String(128), nullable=False)
    justification_notes: Mapped[str] = mapped_column(Text, nullable=False)  # Must be >= 15 characters
    ticket_incident_ref: Mapped[str] = mapped_column(String(64), nullable=False)  # E.g. INC-2026-904, JIRA-CHAUFFEUR-102
    ip_address: Mapped[Optional[str]] = mapped_column(String(45), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    admin_user: Mapped["User"] = relationship(foreign_keys=[admin_user_id])


# =============================================================================
# Part 19: Infrastructure, Observability, Resilience & Ops (Features 451–475)
# =============================================================================

class IdempotencyRecord(Base):
    __tablename__ = "idempotency_records"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    idempotency_key: Mapped[str] = mapped_column(String(128), unique=True, index=True, nullable=False)
    route_path: Mapped[str] = mapped_column(String(255), nullable=False)
    request_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    response_code: Mapped[int] = mapped_column(Integer, nullable=False)
    response_body_json: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

class ApiVersionRecord(Base):
    __tablename__ = "api_version_records"
    __table_args__ = (UniqueConstraint("version_code", "app_type", name="uq_version_app"),)

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    version_code: Mapped[str] = mapped_column(String(32), index=True, nullable=False)
    app_type: Mapped[str] = mapped_column(String(32), default="CUSTOMER")  # CUSTOMER, DRIVER, ADMIN
    min_supported_version: Mapped[str] = mapped_column(String(32), nullable=False)
    latest_version: Mapped[str] = mapped_column(String(32), nullable=False)
    status: Mapped[str] = mapped_column(String(32), default="SUPPORTED")  # SUPPORTED, DEPRECATED, BLOCKED
    deprecation_message: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

class ApiErrorCatalogEntry(Base):
    __tablename__ = "api_error_catalog"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    error_code: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    http_status_code: Mapped[int] = mapped_column(Integer, nullable=False, default=400)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False)
    resolution_guidance: Mapped[str] = mapped_column(Text, nullable=False)
    category: Mapped[str] = mapped_column(String(32), default="SYSTEM")  # AUTH, PAYMENT, RIDE, RATE_LIMIT, SYSTEM
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

class DistributedTraceSpan(Base):
    __tablename__ = "distributed_trace_spans"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    trace_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    span_id: Mapped[str] = mapped_column(String(64), nullable=False)
    parent_span_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    operation_name: Mapped[str] = mapped_column(String(128), nullable=False)
    service_name: Mapped[str] = mapped_column(String(64), default="fastapi-backend")
    duration_ms: Mapped[float] = mapped_column(Float, nullable=False)
    http_method: Mapped[str] = mapped_column(String(10), default="GET")
    http_status: Mapped[int] = mapped_column(Integer, default=200)
    route_path: Mapped[str] = mapped_column(String(255), nullable=False)
    correlation_id: Mapped[Optional[str]] = mapped_column(String(64), index=True, nullable=True)
    error_message: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    timestamp: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, index=True)

class BackgroundJob(Base):
    __tablename__ = "background_jobs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    job_name: Mapped[str] = mapped_column(String(128), nullable=False, index=True)
    queue_name: Mapped[str] = mapped_column(String(64), default="default")
    payload_json: Mapped[str] = mapped_column(Text, default="{}")
    status: Mapped[str] = mapped_column(String(32), default="QUEUED", index=True)  # QUEUED, RUNNING, COMPLETED, FAILED, RETRYING
    attempt_count: Mapped[int] = mapped_column(Integer, default=0)
    max_retries: Mapped[int] = mapped_column(Integer, default=3)
    last_error: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    next_retry_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    scheduled_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    started_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class ScheduledJobMonitor(Base):
    __tablename__ = "scheduled_job_monitors"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    job_key: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    job_title: Mapped[str] = mapped_column(String(128), nullable=False)
    cron_schedule: Mapped[str] = mapped_column(String(32), default="0 * * * *")
    last_run_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    next_run_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    last_status: Mapped[str] = mapped_column(String(32), default="SUCCESS")  # SUCCESS, FAILED, MISSED
    last_duration_ms: Mapped[float] = mapped_column(Float, default=0.0)
    is_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

class DatabaseMigrationVerification(Base):
    __tablename__ = "database_migration_verifications"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    revision_id: Mapped[str] = mapped_column(String(64), nullable=False)
    down_revision_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    rollback_verified: Mapped[bool] = mapped_column(Boolean, default=True)
    verification_status: Mapped[str] = mapped_column(String(32), default="PASSED")  # PASSED, FAILED, PENDING
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    verified_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    verified_by: Mapped[str] = mapped_column(String(128), default="automated_ci_pipeline")

class BackupPolicy(Base):
    __tablename__ = "backup_policies"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    policy_name: Mapped[str] = mapped_column(String(128), nullable=False)
    backup_type: Mapped[str] = mapped_column(String(32), default="FULL")  # FULL, INCREMENTAL
    retention_days: Mapped[int] = mapped_column(Integer, default=30)
    encryption_algorithm: Mapped[str] = mapped_column(String(32), default="AES_256_GCM")
    key_rotation_status: Mapped[str] = mapped_column(String(32), default="CURRENT")
    kms_key_arn: Mapped[str] = mapped_column(String(255), default="arn:aws:kms:ap-south-1:123456789012:key/gcdb-master")
    last_backup_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    next_backup_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class RestoreDrillSchedule(Base):
    __tablename__ = "restore_drill_schedules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    drill_name: Mapped[str] = mapped_column(String(128), nullable=False)
    target_region: Mapped[str] = mapped_column(String(32), default="ap-south-1-b")
    scheduled_date: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    status: Mapped[str] = mapped_column(String(32), default="SCHEDULED")  # SCHEDULED, IN_PROGRESS, COMPLETED_SUCCESS, FAILED
    rto_minutes_achieved: Mapped[float] = mapped_column(Float, default=12.5)
    rpo_minutes_achieved: Mapped[float] = mapped_column(Float, default=1.0)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    conducted_by: Mapped[str] = mapped_column(String(128), default="SRE_DR_Team")
    conducted_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

class DisasterRecoveryPlan(Base):
    __tablename__ = "disaster_recovery_plans"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    plan_code: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    primary_region: Mapped[str] = mapped_column(String(32), default="ap-south-1 (Mumbai)")
    dr_region: Mapped[str] = mapped_column(String(32), default="ap-southeast-1 (Singapore)")
    replication_status: Mapped[str] = mapped_column(String(32), default="SYNCED")  # SYNCED, LAG, DEGRADED
    failover_readiness: Mapped[str] = mapped_column(String(32), default="READY")  # READY, TESTING, MANUAL_ONLY
    last_failover_test_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

class RedisNamespaceConfig(Base):
    __tablename__ = "redis_namespace_configs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    namespace: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    default_ttl_seconds: Mapped[int] = mapped_column(Integer, default=3600)
    keys_count: Mapped[int] = mapped_column(Integer, default=1240)
    memory_bytes: Mapped[int] = mapped_column(Integer, default=1048576)
    eviction_policy: Mapped[str] = mapped_column(String(32), default="volatile-lru")
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

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

class ProviderHealthConfig(Base):
    __tablename__ = "provider_health_configs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    provider_name: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    provider_type: Mapped[str] = mapped_column(String(32), nullable=False)  # SMS, PAYMENT, MAPS
    is_healthy: Mapped[bool] = mapped_column(Boolean, default=True)
    circuit_breaker_state: Mapped[str] = mapped_column(String(20), default="CLOSED")  # CLOSED, OPEN, HALF_OPEN
    consecutive_failures: Mapped[int] = mapped_column(Integer, default=0)
    failure_threshold: Mapped[int] = mapped_column(Integer, default=5)
    failover_provider_name: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    quota_used_percentage: Mapped[float] = mapped_column(Float, default=12.5)
    last_health_check_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

class SecretRotationWorkflow(Base):
    __tablename__ = "secret_rotation_workflows"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    secret_name: Mapped[str] = mapped_column(String(128), unique=True, index=True, nullable=False)
    vault_path: Mapped[str] = mapped_column(String(255), nullable=False)
    current_version: Mapped[str] = mapped_column(String(32), default="v3")
    status: Mapped[str] = mapped_column(String(32), default="CURRENT")  # CURRENT, ROTATING, PENDING_VERIFICATION, REVOKED
    auto_rotate_days: Mapped[int] = mapped_column(Integer, default=90)
    last_rotated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    next_rotation_due: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_by: Mapped[str] = mapped_column(String(128), default="security-automation-bot")

class DependencyVulnerabilityReport(Base):
    __tablename__ = "dependency_vulnerability_reports"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    package_name: Mapped[str] = mapped_column(String(128), nullable=False, index=True)
    installed_version: Mapped[str] = mapped_column(String(32), nullable=False)
    fixed_version: Mapped[str] = mapped_column(String(32), nullable=False)
    cve_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    severity: Mapped[str] = mapped_column(String(20), default="MEDIUM")  # LOW, MEDIUM, HIGH, CRITICAL
    status: Mapped[str] = mapped_column(String(20), default="OPEN")  # OPEN, PATCHED, IGNORABLE
    advisory_url: Mapped[str] = mapped_column(String(512), nullable=False)
    reported_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class MobileCrashReport(Base):
    __tablename__ = "mobile_crash_reports"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    app_type: Mapped[str] = mapped_column(String(32), default="CUSTOMER")  # CUSTOMER, DRIVER
    app_version: Mapped[str] = mapped_column(String(32), default="1.0.0")
    device_model: Mapped[str] = mapped_column(String(128), default="Android Emulator")
    os_version: Mapped[str] = mapped_column(String(32), default="Android 14 (API 34)")
    report_type: Mapped[str] = mapped_column(String(20), default="CRASH")  # CRASH, ANR, NON_FATAL
    stack_trace: Mapped[str] = mapped_column(Text, nullable=False)
    error_message: Mapped[str] = mapped_column(String(512), nullable=False)
    user_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, index=True)

class FeatureFlag(Base):
    __tablename__ = "feature_flags"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    flag_key: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    name: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[str] = mapped_column(Text, default="")
    is_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    rollout_percentage: Mapped[int] = mapped_column(Integer, default=100)
    target_roles_json: Mapped[str] = mapped_column(Text, default="[\"admin\", \"customer\", \"driver\"]")
    target_users_json: Mapped[str] = mapped_column(Text, default="[]")
    created_by: Mapped[str] = mapped_column(String(128), default="System")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)


# =============================================================================
# Part 20: Enterprise, Corporate, Partnerships, Multi-City & Growth (Features 476–500)
# =============================================================================

class CorporateAccount(Base):
    __tablename__ = "corporate_accounts"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    company_name: Mapped[str] = mapped_column(String(128), unique=True, index=True, nullable=False)
    tax_id: Mapped[str] = mapped_column(String(64), nullable=False)
    billing_email: Mapped[str] = mapped_column(String(128), nullable=False)
    credit_limit_cents: Mapped[int] = mapped_column(Integer, default=50000000)  # ₹5,00,000
    balance_due_cents: Mapped[int] = mapped_column(Integer, default=0)
    status: Mapped[str] = mapped_column(String(32), default="ACTIVE")  # ACTIVE, SUSPENDED, PENDING_REVIEW
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

class EmployeeProfile(Base):
    __tablename__ = "employee_profiles"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    corporate_id: Mapped[str] = mapped_column(String(36), ForeignKey("corporate_accounts.id"), nullable=False, index=True)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    cost_center: Mapped[str] = mapped_column(String(64), default="GENERAL_OPS")
    max_ride_fare_cents: Mapped[int] = mapped_column(Integer, default=250000)  # ₹2,500
    requires_approval: Mapped[bool] = mapped_column(Boolean, default=False)
    status: Mapped[str] = mapped_column(String(32), default="ACTIVE")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class CorporateTravelPolicy(Base):
    __tablename__ = "corporate_travel_policies"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    corporate_id: Mapped[str] = mapped_column(String(36), ForeignKey("corporate_accounts.id"), nullable=False, index=True)
    policy_name: Mapped[str] = mapped_column(String(128), nullable=False)
    allowed_categories_json: Mapped[str] = mapped_column(Text, default="[\"SEDAN\", \"LUXURY_SUV\", \"EXECUTIVE_LIMO\", \"ULTRA_LUXURY_EV\"]")
    max_fare_per_ride_cents: Mapped[int] = mapped_column(Integer, default=500000)
    curfew_start_time: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)  # e.g. "23:00"
    curfew_end_time: Mapped[Optional[str]] = mapped_column(String(10), nullable=True)    # e.g. "06:00"
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class CorporateInvoice(Base):
    __tablename__ = "corporate_invoices"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    invoice_number: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    corporate_id: Mapped[str] = mapped_column(String(36), ForeignKey("corporate_accounts.id"), nullable=False, index=True)
    billing_period: Mapped[str] = mapped_column(String(32), nullable=False)  # e.g. "2026-M10"
    total_amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    status: Mapped[str] = mapped_column(String(32), default="ISSUED")  # ISSUED, PAID, OVERDUE
    pdf_url: Mapped[str] = mapped_column(String(512), default="/invoices/corp-202610.pdf")
    due_date: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class BusinessApprovalRequest(Base):
    __tablename__ = "business_approval_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    employee_id: Mapped[str] = mapped_column(String(36), ForeignKey("employee_profiles.id"), nullable=False)
    approver_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    cost_center: Mapped[str] = mapped_column(String(64), nullable=False)
    project_code: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    status: Mapped[str] = mapped_column(String(32), default="PENDING")  # PENDING, APPROVED, REJECTED
    justification: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class PartnerAccount(Base):
    __tablename__ = "partner_accounts"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    partner_type: Mapped[str] = mapped_column(String(32), default="HOTEL")  # HOTEL, VENUE, EVENT, CONCIERGE
    partner_name: Mapped[str] = mapped_column(String(128), unique=True, index=True, nullable=False)
    api_key_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    commission_rate_pct: Mapped[float] = mapped_column(Float, default=10.0)
    referral_code: Mapped[str] = mapped_column(String(32), unique=True, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class PartnerBooking(Base):
    __tablename__ = "partner_bookings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    partner_id: Mapped[str] = mapped_column(String(36), ForeignKey("partner_accounts.id"), nullable=False, index=True)
    ride_id: Mapped[str] = mapped_column(String(36), ForeignKey("rides.id"), nullable=False, index=True)
    guest_name: Mapped[str] = mapped_column(String(128), nullable=False)
    guest_phone: Mapped[str] = mapped_column(String(32), nullable=False)
    commission_earned_cents: Mapped[int] = mapped_column(Integer, default=0)
    attribution_status: Mapped[str] = mapped_column(String(32), default="ATTRIBUTED")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class EventTransportWorkspace(Base):
    __tablename__ = "event_transport_workspaces"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    event_name: Mapped[str] = mapped_column(String(128), nullable=False)
    venue_address: Mapped[str] = mapped_column(String(255), nullable=False)
    event_date: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    allocated_vehicles_count: Mapped[int] = mapped_column(Integer, default=10)
    total_guests: Mapped[int] = mapped_column(Integer, default=100)
    status: Mapped[str] = mapped_column(String(32), default="PLANNING")  # PLANNING, ACTIVE, COMPLETED
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class FleetSubscriptionPlan(Base):
    __tablename__ = "fleet_subscription_plans"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    plan_code: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    name: Mapped[str] = mapped_column(String(128), nullable=False)
    monthly_price_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    included_rides_count: Mapped[int] = mapped_column(Integer, nullable=False)
    category_code: Mapped[str] = mapped_column(String(32), default="SEDAN")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class CustomerSubscription(Base):
    __tablename__ = "customer_subscriptions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    plan_id: Mapped[str] = mapped_column(String(36), ForeignKey("fleet_subscription_plans.id"), nullable=False)
    rides_remaining: Mapped[int] = mapped_column(Integer, nullable=False)
    starts_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    status: Mapped[str] = mapped_column(String(32), default="ACTIVE")

class PrepaidRideBundle(Base):
    __tablename__ = "prepaid_ride_bundles"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    bundle_code: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    ride_count: Mapped[int] = mapped_column(Integer, default=10)
    price_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    discount_percentage: Mapped[float] = mapped_column(Float, default=15.0)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)

class LoyaltyPartnerCatalog(Base):
    __tablename__ = "loyalty_partner_catalog"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    partner_name: Mapped[str] = mapped_column(String(128), nullable=False)
    offer_title: Mapped[str] = mapped_column(String(255), nullable=False)
    required_points: Mapped[int] = mapped_column(Integer, default=500)
    promo_code: Mapped[str] = mapped_column(String(32), nullable=False)
    logo_url: Mapped[str] = mapped_column(String(512), default="https://images.unsplash.com/photo-1563720223185-11003d516935")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)

class AirportTransferPackage(Base):
    __tablename__ = "airport_transfer_packages"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    airport_code: Mapped[str] = mapped_column(String(10), index=True, nullable=False)  # BOM, DEL, BLR
    package_title: Mapped[str] = mapped_column(String(128), nullable=False)
    category_code: Mapped[str] = mapped_column(String(32), default="EXECUTIVE_LIMO")
    flat_fare_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    meets_and_greets_included: Mapped[bool] = mapped_column(Boolean, default=True)

class IntercityProduct(Base):
    __tablename__ = "intercity_products"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    route_name: Mapped[str] = mapped_column(String(128), nullable=False)  # Mumbai to Pune
    origin_city: Mapped[str] = mapped_column(String(64), nullable=False)
    destination_city: Mapped[str] = mapped_column(String(64), nullable=False)
    distance_km: Mapped[float] = mapped_column(Float, nullable=False)
    fixed_fare_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    toll_included: Mapped[bool] = mapped_column(Boolean, default=True)

class HourlyChauffeurBooking(Base):
    __tablename__ = "hourly_chauffeur_bookings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True)
    requested_hours: Mapped[int] = mapped_column(Integer, default=4)
    hourly_rate_cents: Mapped[int] = mapped_column(Integer, default=150000)  # ₹1,500/hr
    kilometers_included: Mapped[int] = mapped_column(Integer, default=40)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class ChauffeurServiceAddon(Base):
    __tablename__ = "chauffeur_service_addons"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    addon_code: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    price_cents: Mapped[int] = mapped_column(Integer, default=20000)
    description: Mapped[str] = mapped_column(Text, default="")
    category_compatibility: Mapped[str] = mapped_column(String(255), default="ALL")

class WhiteLabelTenantConfig(Base):
    __tablename__ = "white_label_tenant_configs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    tenant_code: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    brand_name: Mapped[str] = mapped_column(String(128), nullable=False)
    primary_color: Mapped[str] = mapped_column(String(10), default="#D4AF37")
    secondary_color: Mapped[str] = mapped_column(String(10), default="#0B0F17")
    custom_domain: Mapped[str] = mapped_column(String(128), nullable=False)
    logo_url: Mapped[str] = mapped_column(String(512), default="https://grandchauffeur.com/logo.png")

class MultiCityOperatingConfig(Base):
    __tablename__ = "multi_city_operating_configs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    city_code: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)  # MUMBAI, DELHI, BANGALORE
    city_name: Mapped[str] = mapped_column(String(128), nullable=False)
    country: Mapped[str] = mapped_column(String(64), default="India")
    timezone: Mapped[str] = mapped_column(String(64), default="Asia/Kolkata")
    tax_rate_percentage: Mapped[float] = mapped_column(Float, default=5.0)
    min_fare_cents: Mapped[int] = mapped_column(Integer, default=100000)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)

class ABTestExperiment(Base):
    __tablename__ = "ab_test_experiments"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    experiment_key: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    name: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[str] = mapped_column(Text, default="")
    variants_json: Mapped[str] = mapped_column(Text, default="[\"control\", \"variant_a\"]")
    rollout_status: Mapped[str] = mapped_column(String(32), default="RUNNING")  # DRAFT, RUNNING, CONCLUDED

class FeatureRequestVote(Base):
    __tablename__ = "feature_request_votes"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    feature_key: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[str] = mapped_column(Text, default="")
    category: Mapped[str] = mapped_column(String(64), default="BESPOKE_AMENITIES")
    upvote_count: Mapped[int] = mapped_column(Integer, default=1)
    status: Mapped[str] = mapped_column(String(32), default="UNDER_CONSIDERATION")

class ProductFeedbackAnalytics(Base):
    __tablename__ = "product_feedback_analytics"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    rating_score: Mapped[int] = mapped_column(Integer, default=5)
    feedback_category: Mapped[str] = mapped_column(String(64), default="CHAUFFEUR_SERVICE")
    comments: Mapped[str] = mapped_column(Text, nullable=False)
    sentiment: Mapped[str] = mapped_column(String(20), default="POSITIVE")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

class LocalizationCatalog(Base):
    __tablename__ = "localization_catalog"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    string_key: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    locale: Mapped[str] = mapped_column(String(10), index=True, default="en")  # en, hi, mr, gu
    category: Mapped[str] = mapped_column(String(32), default="COMMON")       # CUSTOMER_UI, DRIVER_UI, ADMIN_UI, COMMON
    translated_text: Mapped[str] = mapped_column(Text, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

class TourismPlace(Base):
    __tablename__ = "tourism_places"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    category: Mapped[str] = mapped_column(String(64), default="HISTORICAL", index=True) # HISTORICAL, TOURISM, SPIRITUAL_HERITAGE
    short_description: Mapped[str] = mapped_column(Text, nullable=False)
    photo_url: Mapped[str] = mapped_column(String(512), nullable=False)
    latitude: Mapped[float] = mapped_column(Float, nullable=False)
    longitude: Mapped[float] = mapped_column(Float, nullable=False)
    address: Mapped[str] = mapped_column(String(255), nullable=False)
    city: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    rating: Mapped[float] = mapped_column(Float, default=4.8)
    review_count: Mapped[int] = mapped_column(Integer, default=1500)
    entry_fee: Mapped[str] = mapped_column(String(64), default="Free")
    timings: Mapped[str] = mapped_column(String(64), default="06:00 AM – 06:00 PM")
    tag: Mapped[str] = mapped_column(String(64), default="Famous Heritage")
    week_rotation_group: Mapped[int] = mapped_column(Integer, default=0, index=True)
    is_featured: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


