import uuid
import secrets
from datetime import datetime, timezone
from typing import Optional, List
from sqlalchemy import (
    String, Integer, Float, Boolean, DateTime, ForeignKey, Text, Index, UniqueConstraint
)
from sqlalchemy.orm import Mapped, mapped_column, relationship
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
    email: Mapped[Optional[str]] = mapped_column(String(255), unique=True, index=True, nullable=True)
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
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)

    user: Mapped["User"] = relationship(back_populates="customer_profile")
    rides: Mapped[List["Ride"]] = relationship(back_populates="customer")
    favorites: Mapped[List["FavoriteLocation"]] = relationship(back_populates="customer")
    passes: Mapped[List["CustomerRidePass"]] = relationship(back_populates="customer")

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
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    effective_from: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    effective_until: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_by: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

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
    stop_order: Mapped[int] = mapped_column(Integer, nullable=False)
    address: Mapped[str] = mapped_column(String(255), nullable=False)
    latitude: Mapped[float] = mapped_column(Float, nullable=False)
    longitude: Mapped[float] = mapped_column(Float, nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="PENDING")  # PENDING, ARRIVED, COMPLETED
    arrived_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

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
    responded_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    rejection_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)

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
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    ride: Mapped["Ride"] = relationship(back_populates="rating")

class DeviceToken(Base):
    __tablename__ = "device_tokens"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    fcm_token: Mapped[str] = mapped_column(String(512), unique=True, index=True, nullable=False)
    device_os: Mapped[str] = mapped_column(String(20), default="ANDROID")
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
    sent_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)

    user: Mapped["User"] = relationship(back_populates="notifications")

class Session(Base):
    __tablename__ = "sessions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    ip_address: Mapped[Optional[str]] = mapped_column(String(45), nullable=True)
    user_agent: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    is_revoked: Mapped[bool] = mapped_column(Boolean, default=False)
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
