import asyncio
import logging
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import DeclarativeBase
from typing import AsyncGenerator
from backend.core.config import settings

logger = logging.getLogger("backend.database")

class Base(DeclarativeBase):
    pass

connect_args = {"check_same_thread": False, "timeout": 30} if settings.DATABASE_URL.startswith("sqlite") else {"timeout": 6, "command_timeout": 6}

engine = create_async_engine(
    settings.DATABASE_URL,
    echo=False,
    connect_args=connect_args,
    future=True
)

async_session_maker = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autoflush=False
)

async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with async_session_maker() as session:
        try:
            yield session
        finally:
            await session.close()

def sync_missing_columns(connection):
    from sqlalchemy import text, inspect
    try:
        if True:
            dialect_name = connection.dialect.name
            is_pg = dialect_name == "postgresql"
            if is_pg:
                pg_user_cols = {
                    "password_hash": "VARCHAR(255)",
                    "email_verified": "BOOLEAN DEFAULT FALSE",
                    "avatar_url": "VARCHAR(512)",
                    "mfa_enabled": "BOOLEAN DEFAULT FALSE",
                    "mfa_secret": "VARCHAR(64)",
                    "app_lock_pin_hash": "VARCHAR(128)",
                    "biometric_enabled": "BOOLEAN DEFAULT FALSE",
                    "passkey_credential_id": "VARCHAR(255)",
                    "passkey_public_key": "TEXT"
                }
                for col_name, col_type in pg_user_cols.items():
                    connection.execute(text(f"ALTER TABLE users ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_profile_cols = {
                    "tier": "VARCHAR(32) DEFAULT 'ROYAL_SOVEREIGN'",
                    "temperature_celsius": "FLOAT DEFAULT 21.0",
                    "quiet_ride": "BOOLEAN DEFAULT TRUE",
                    "preferred_music": "VARCHAR(64) DEFAULT 'Classical Symphony'",
                    "preferred_water": "VARCHAR(64) DEFAULT 'Sparkling Mineral'",
                    "luggage_assistance": "BOOLEAN DEFAULT TRUE",
                    "no_phone_call_ride": "BOOLEAN DEFAULT FALSE",
                    "preferred_driver_language": "VARCHAR(32) DEFAULT 'English'",
                    "female_driver_preferred": "BOOLEAN DEFAULT FALSE",
                    "vehicle_color_preference": "VARCHAR(32) DEFAULT 'Obsidian Black'",
                    "premium_amenities": "VARCHAR(255) DEFAULT 'Evian Sparkling Water, Braided Fast-Charging Kit'",
                    "electric_vehicle_preferred": "BOOLEAN DEFAULT FALSE",
                    "low_emission_filter": "BOOLEAN DEFAULT FALSE",
                    "wheelchair_access_required": "BOOLEAN DEFAULT FALSE",
                    "step_free_pickup_required": "BOOLEAN DEFAULT FALSE",
                    "assistance_animal_present": "BOOLEAN DEFAULT FALSE",
                    "extra_legroom_required": "BOOLEAN DEFAULT FALSE",
                    "pet_friendly_preferred": "BOOLEAN DEFAULT FALSE",
                    "accessibility_notes": "TEXT"
                }
                for col_name, col_type in pg_profile_cols.items():
                    connection.execute(text(f"ALTER TABLE customer_profiles ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_ride_cols = {
                    "booking_for_self": "BOOLEAN DEFAULT TRUE",
                    "passenger_name": "VARCHAR(128)",
                    "passenger_phone": "VARCHAR(32)",
                    "passenger_relationship": "VARCHAR(64)",
                    "is_return_trip": "BOOLEAN DEFAULT FALSE",
                    "paired_return_ride_id": "VARCHAR(36)",
                    "is_round_trip": "BOOLEAN DEFAULT FALSE",
                    "pickup_notes": "TEXT",
                    "destination_notes": "TEXT",
                    "entrance_guidance": "VARCHAR(255)",
                    "landmark_guidance": "VARCHAR(255)",
                    "airport_code": "VARCHAR(10)",
                    "terminal_name": "VARCHAR(64)",
                    "airline_code": "VARCHAR(32)",
                    "flight_number": "VARCHAR(32)",
                    "flight_delay_minutes": "INTEGER DEFAULT 0",
                    "flight_status": "VARCHAR(32) DEFAULT 'ON_TIME'",
                    "booking_mode": "VARCHAR(32) DEFAULT 'STANDARD'",
                    "hotel_room_number": "VARCHAR(32)",
                    "hotel_guest_name": "VARCHAR(128)",
                    "business_cost_center": "VARCHAR(64)",
                    "business_project_code": "VARCHAR(64)",
                    "business_tax_id": "VARCHAR(64)",
                    "dependent_id": "VARCHAR(36)",
                    "shared_location_source": "VARCHAR(512)",
                    "original_fare_cents": "INTEGER",
                    "fare_difference_cents": "INTEGER DEFAULT 0",
                    "fare_change_reason": "TEXT",
                    "fare_cap_applied": "BOOLEAN DEFAULT FALSE",
                    "price_locked": "BOOLEAN DEFAULT FALSE",
                    "locked_fare_cents": "INTEGER",
                    "price_lock_expires_at": "TIMESTAMP WITH TIME ZONE",
                    "invoice_number": "VARCHAR(64)",
                    "corporate_billing_ref": "VARCHAR(64)",
                    "split_fare_enabled": "BOOLEAN DEFAULT FALSE",
                    "route_preference": "VARCHAR(32) DEFAULT 'FASTEST'",
                    "route_deviation_detected": "BOOLEAN DEFAULT FALSE",
                    "route_deviation_reason": "VARCHAR(255)",
                    "driver_arrival_photo_url": "VARCHAR(512)",
                    "vehicle_model_override": "VARCHAR(64)",
                    "vehicle_plate_override": "VARCHAR(32)",
                    "vehicle_color_override": "VARCHAR(32)",
                    "masked_virtual_number": "VARCHAR(32) DEFAULT '+91 22 6900 4820'",
                    "masked_virtual_ext": "VARCHAR(8) DEFAULT '4921'",
                    "post_trip_contact_expires_at": "TIMESTAMP WITH TIME ZONE",
                    "sos_triggered": "BOOLEAN DEFAULT FALSE",
                    "sos_triggered_at": "TIMESTAMP WITH TIME ZONE",
                    "safety_checkin_due_at": "TIMESTAMP WITH TIME ZONE",
                    "safety_checkin_status": "VARCHAR(32) DEFAULT 'NORMAL'",
                    "audio_recording_consent_passenger": "BOOLEAN DEFAULT FALSE",
                    "audio_recording_consent_driver": "BOOLEAN DEFAULT FALSE",
                    "audio_recording_active": "BOOLEAN DEFAULT FALSE",
                    "audio_recording_session_id": "VARCHAR(64)",
                    "unexpected_stop_detected": "BOOLEAN DEFAULT FALSE",
                    "unexpected_stop_duration_seconds": "INTEGER DEFAULT 0",
                    "route_anomaly_detected": "BOOLEAN DEFAULT FALSE",
                    "route_anomaly_type": "VARCHAR(64)",
                    "high_risk_pickup": "BOOLEAN DEFAULT FALSE",
                    "high_risk_reason": "VARCHAR(128)",
                    # Part 3 Bespoke Ride Parameters
                    "child_seat_requested": "BOOLEAN DEFAULT FALSE",
                    "child_seat_type": "VARCHAR(64)",
                    "luggage_large_count": "INTEGER DEFAULT 0",
                    "luggage_small_count": "INTEGER DEFAULT 0",
                    "wheelchair_requested": "BOOLEAN DEFAULT FALSE",
                    "step_free_pickup": "BOOLEAN DEFAULT FALSE",
                    "assistance_animal": "BOOLEAN DEFAULT FALSE",
                    "extra_legroom_requested": "BOOLEAN DEFAULT FALSE",
                    "quiet_ride_requested": "BOOLEAN DEFAULT TRUE",
                    "cabin_temp_requested": "FLOAT",
                    "music_requested": "VARCHAR(64)",
                    "no_phone_call": "BOOLEAN DEFAULT FALSE",
                    "driver_language_requested": "VARCHAR(64)",
                    "female_driver_requested": "BOOLEAN DEFAULT FALSE",
                    "vehicle_color_requested": "VARCHAR(64)",
                    "amenities_requested": "VARCHAR(255)",
                    "ev_ride_requested": "BOOLEAN DEFAULT FALSE",
                    "pet_friendly_requested": "BOOLEAN DEFAULT FALSE",
                    "special_assistance_notes": "TEXT"
                }
                for col_name, col_type in pg_ride_cols.items():
                    connection.execute(text(f"ALTER TABLE rides ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_category_cols = {
                    "photos_gallery_json": "TEXT",
                    "legroom_inches": "FLOAT DEFAULT 42.0",
                    "quietness_db": "FLOAT DEFAULT 52.0",
                    "luggage_large_capacity": "INTEGER DEFAULT 2",
                    "luggage_small_capacity": "INTEGER DEFAULT 2",
                    "has_wifi": "BOOLEAN DEFAULT TRUE",
                    "has_massage_seats": "BOOLEAN DEFAULT FALSE",
                    "has_champagne_cooler": "BOOLEAN DEFAULT FALSE",
                    "has_reclining_seats": "BOOLEAN DEFAULT FALSE",
                    "is_electric": "BOOLEAN DEFAULT FALSE",
                    "emissions_g_per_km": "FLOAT DEFAULT 140.0",
                    "wheelchair_accessible": "BOOLEAN DEFAULT FALSE",
                    "step_free_height_cm": "FLOAT DEFAULT 35.0",
                    "pet_friendly": "BOOLEAN DEFAULT FALSE",
                    "child_seat_available": "BOOLEAN DEFAULT TRUE",
                    "available_colors_json": "TEXT",
                    "available_amenities_json": "TEXT"
                }
                for col_name, col_type in pg_category_cols.items():
                    connection.execute(text(f"ALTER TABLE vehicle_categories ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_customer_profile_cols = {
                    "tier": "VARCHAR(32) DEFAULT 'ROYAL_SOVEREIGN'",
                    "temperature_celsius": "FLOAT DEFAULT 21.0",
                    "quiet_ride": "BOOLEAN DEFAULT TRUE",
                    "preferred_music": "VARCHAR(64) DEFAULT 'Classical Chamber'",
                    "preferred_water": "VARCHAR(64) DEFAULT 'Chilled Sparkling'",
                    "no_phone_call_ride": "BOOLEAN DEFAULT FALSE",
                    "preferred_driver_language": "VARCHAR(64) DEFAULT 'English'",
                    "female_driver_preferred": "BOOLEAN DEFAULT FALSE",
                    "vehicle_color_preference": "VARCHAR(64) DEFAULT 'Obsidian Black'",
                    "premium_amenities": "VARCHAR(255) DEFAULT 'Evian Mineral Water, Braided Charger'",
                    "electric_vehicle_preferred": "BOOLEAN DEFAULT FALSE",
                    "low_emission_filter": "BOOLEAN DEFAULT FALSE",
                    "wheelchair_access_required": "BOOLEAN DEFAULT FALSE",
                    "step_free_pickup_required": "BOOLEAN DEFAULT FALSE",
                    "assistance_animal_present": "BOOLEAN DEFAULT FALSE",
                    "extra_legroom_required": "BOOLEAN DEFAULT FALSE",
                    "pet_friendly_preferred": "BOOLEAN DEFAULT FALSE",
                    "accessibility_notes": "TEXT"
                }
                for col_name, col_type in pg_customer_profile_cols.items():
                    connection.execute(text(f"ALTER TABLE customer_profiles ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_driver_profile_cols = {
                    "security_badge_id": "VARCHAR(32) DEFAULT 'GC-SEC-8821'",
                    "background_check_status": "VARCHAR(32) DEFAULT 'VERIFIED'",
                    "background_check_date": "TIMESTAMP WITH TIME ZONE",
                    "academy_certified": "BOOLEAN DEFAULT TRUE",
                    "academy_level": "VARCHAR(32) DEFAULT 'MASTER_CHAUFFEUR'",
                    "onboarding_step": "VARCHAR(32) DEFAULT 'IDENTITY_SUBMITTED'",
                    "profile_completeness_percentage": "INTEGER DEFAULT 65",
                    "identity_verified": "BOOLEAN DEFAULT FALSE",
                    "identity_reverification_due": "BOOLEAN DEFAULT FALSE",
                    "last_identity_verified_at": "TIMESTAMP WITH TIME ZONE",
                    "preferred_operating_zones": "VARCHAR(255) DEFAULT 'AIRPORT_CORRIDOR,SOUTH_MUMBAI,BKC_FINANCIAL'",
                    "preferred_vehicle_categories": "VARCHAR(255) DEFAULT 'EXECUTIVE_SEDAN,PRESTIGE_SUV'",
                    "languages_spoken": "VARCHAR(128) DEFAULT 'English, Hindi, Marathi'",
                    "service_etiquette_level": "VARCHAR(64) DEFAULT 'Black Sovereign Executive'",
                    "cabin_music_preference": "VARCHAR(64) DEFAULT 'Classical & Soft Jazz'",
                    "cabin_temp_celsius": "INTEGER DEFAULT 21",
                    "emergency_contact_name": "VARCHAR(128)",
                    "emergency_contact_phone": "VARCHAR(32)",
                    "emergency_contact_relationship": "VARCHAR(64)",
                    "account_deletion_requested": "BOOLEAN DEFAULT FALSE",
                    "account_deletion_requested_at": "TIMESTAMP WITH TIME ZONE",
                    "policy_acknowledged_at": "TIMESTAMP WITH TIME ZONE"
                }
                for col_name, col_type in pg_driver_profile_cols.items():
                    connection.execute(text(f"ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_driver_doc_cols = {
                    "expiry_reminder_sent_at": "TIMESTAMP WITH TIME ZONE",
                    "image_quality_score": "FLOAT DEFAULT 1.0",
                    "quality_assessment_notes": "VARCHAR(255)",
                    "resubmission_count": "INTEGER DEFAULT 0"
                }
                for col_name, col_type in pg_driver_doc_cols.items():
                    connection.execute(text(f"ALTER TABLE driver_documents ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_vehicle_cols = {
                    "rc_number": "VARCHAR(64) DEFAULT 'DL-01-RC-2024-9912'",
                    "permit_number": "VARCHAR(64) DEFAULT 'DL-COMM-PVT-09882'",
                    "puc_expiry_date": "TIMESTAMP WITH TIME ZONE",
                    "fitness_expiry_date": "TIMESTAMP WITH TIME ZONE",
                    "insurance_policy_number": "VARCHAR(64) DEFAULT 'POL-BAJAJ-ALL-88912'"
                }
                for col_name, col_type in pg_vehicle_cols.items():
                    connection.execute(text(f"ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_chat_cols = {
                    "translated_text": "TEXT",
                    "translated_language": "VARCHAR(10)",
                    "meeting_point_name": "VARCHAR(128)",
                    "meeting_point_lat": "FLOAT",
                    "meeting_point_lng": "FLOAT"
                }
                for col_name, col_type in pg_chat_cols.items():
                    connection.execute(text(f"ALTER TABLE ride_chat_messages ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_stop_cols = {
                    "sequence_order": "INTEGER DEFAULT 1",
                    "dwell_time_minutes": "INTEGER DEFAULT 0",
                    "passenger_instructions": "VARCHAR(255)",
                    "entrance_landmark": "VARCHAR(255)"
                }
                for col_name, col_type in pg_stop_cols.items():
                    connection.execute(text(f"ALTER TABLE ride_stops ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_fare_rule_cols = {
                    "fare_cap_cents": "INTEGER",
                    "tax_percentage": "FLOAT DEFAULT 5.0",
                    "amenities_fee_cents": "INTEGER DEFAULT 5000"
                }
                for col_name, col_type in pg_fare_rule_cols.items():
                    connection.execute(text(f"ALTER TABLE fare_rules ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_estimate_cols = {
                    "base_fare_cents": "INTEGER DEFAULT 0",
                    "distance_charge_cents": "INTEGER DEFAULT 0",
                    "time_charge_cents": "INTEGER DEFAULT 0",
                    "tax_cents": "INTEGER DEFAULT 0",
                    "amenities_fee_cents": "INTEGER DEFAULT 0",
                    "fare_min_cents": "INTEGER DEFAULT 0",
                    "fare_max_cents": "INTEGER DEFAULT 0",
                    "confidence_level": "VARCHAR(20) DEFAULT 'HIGH'",
                    "confidence_percentage": "INTEGER DEFAULT 95",
                    "is_capped": "BOOLEAN DEFAULT FALSE",
                    "cap_discount_cents": "INTEGER DEFAULT 0"
                }
                for col_name, col_type in pg_estimate_cols.items():
                    connection.execute(text(f"ALTER TABLE fare_estimates ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_wallet_cols = {
                    "auto_topup_enabled": "BOOLEAN DEFAULT FALSE",
                    "auto_topup_threshold_cents": "INTEGER DEFAULT 50000",
                    "auto_topup_amount_cents": "INTEGER DEFAULT 200000",
                    "auto_topup_method_id": "VARCHAR(36)",
                    "low_balance_alert_sent": "BOOLEAN DEFAULT FALSE"
                }
                for col_name, col_type in pg_wallet_cols.items():
                    connection.execute(text(f"ALTER TABLE wallets ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_payment_cols = {
                    "refunded_amount_cents": "INTEGER DEFAULT 0",
                    "upi_intent_uri": "VARCHAR(512)",
                    "saved_method_id": "VARCHAR(36)",
                    "corporate_account_id": "VARCHAR(64)",
                    "split_id": "VARCHAR(36)"
                }
                for col_name, col_type in pg_payment_cols.items():
                    connection.execute(text(f"ALTER TABLE payments ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_pooled_booking_cols = {
                    "share_stop_details_with_co_passengers": "BOOLEAN DEFAULT FALSE",
                    "cancellation_reason": "TEXT"
                }
                for col_name, col_type in pg_pooled_booking_cols.items():
                    connection.execute(text(f"ALTER TABLE pooled_bookings ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_driver_loc_cols = {
                    "spoof_risk_score": "FLOAT DEFAULT 0.0",
                    "quality_flags_json": "TEXT DEFAULT '[]'",
                    "source": "VARCHAR(24) DEFAULT 'FUSED'"
                }
                for col_name, col_type in pg_driver_loc_cols.items():
                    connection.execute(text(f"ALTER TABLE driver_locations ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_saved_pin_cols = {
                    "entrance_notes": "VARCHAR(255)",
                    "entrance_code": "VARCHAR(32)"
                }
                for col_name, col_type in pg_saved_pin_cols.items():
                    connection.execute(text(f"ALTER TABLE saved_location_pins ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_commission_cols = {
                    "approval_status": "VARCHAR(32) DEFAULT 'APPROVED'",
                    "approval_reason": "TEXT"
                }
                for col_name, col_type in pg_commission_cols.items():
                    connection.execute(text(f"ALTER TABLE commission_rules ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_support_cols = {
                    "complaint_reference_number": "VARCHAR(64)",
                    "language_preference": "VARCHAR(10) DEFAULT 'en'",
                    "assigned_squad": "VARCHAR(64) DEFAULT 'VIP_CONCIERGE'",
                    "is_escalated": "BOOLEAN DEFAULT FALSE",
                    "escalation_reason": "VARCHAR(255)",
                    "escalated_at": "TIMESTAMP WITH TIME ZONE",
                    "sla_due_at": "TIMESTAMP WITH TIME ZONE",
                    "last_staff_update_at": "TIMESTAMP WITH TIME ZONE",
                    "lost_item_category": "VARCHAR(64)",
                    "lost_item_description": "TEXT",
                    "lost_item_status": "VARCHAR(32)",
                    "csat_score": "INTEGER",
                    "csat_comment": "TEXT",
                    "resolved_at": "TIMESTAMP WITH TIME ZONE",
                    "data_retention_days": "INTEGER DEFAULT 365",
                    "refund_amount_cents": "INTEGER",
                    "fare_review_evidence": "TEXT",
                    "cancellation_reason_code": "VARCHAR(50)",
                    "fee_waiver_requested": "BOOLEAN DEFAULT FALSE",
                    "duplicate_of_ticket_id": "VARCHAR(36)"
                }
                for col_name, col_type in pg_support_cols.items():
                    connection.execute(text(f"ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_support_msg_cols = {
                    "read_at": "TIMESTAMP WITH TIME ZONE"
                }
                for col_name, col_type in pg_support_msg_cols.items():
                    connection.execute(text(f"ALTER TABLE support_messages ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_payout_cols = {
                    "exception_notes": "TEXT",
                    "settlement_reference": "VARCHAR(128)"
                }
                for col_name, col_type in pg_payout_cols.items():
                    connection.execute(text(f"ALTER TABLE driver_payout_requests ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_broadcast_cols = {
                    "channel": "VARCHAR(20) DEFAULT 'IN_APP'",
                    "audience_filter_json": "TEXT",
                    "localized_content_json": "TEXT",
                    "delivery_success_count": "INTEGER DEFAULT 0",
                    "delivery_failure_count": "INTEGER DEFAULT 0",
                    "opt_out_count": "INTEGER DEFAULT 0",
                    "is_emergency": "BOOLEAN DEFAULT FALSE",
                    "preview_json": "TEXT"
                }
                for col_name, col_type in pg_broadcast_cols.items():
                    connection.execute(text(f"ALTER TABLE broadcast_notifications ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_ride_assignment_cols = {
                    "expires_at": "TIMESTAMP WITH TIME ZONE",
                    "escalation_level": "INTEGER DEFAULT 0",
                    "is_accepted": "BOOLEAN DEFAULT FALSE",
                    "metadata_json": "TEXT"
                }
                for col_name, col_type in pg_ride_assignment_cols.items():
                    connection.execute(text(f"ALTER TABLE ride_assignments ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_driver_rating_cols = {
                    "cleanliness_score": "INTEGER",
                    "comfort_score": "INTEGER",
                    "service_quality_score": "INTEGER",
                    "navigation_score": "INTEGER",
                    "feedback_tags_json": "TEXT",
                    "private_feedback": "TEXT",
                    "tip_cents": "INTEGER DEFAULT 0"
                }
                for col_name, col_type in pg_driver_rating_cols.items():
                    connection.execute(text(f"ALTER TABLE driver_ratings ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_device_token_cols = {
                    "is_active": "BOOLEAN DEFAULT TRUE",
                    "last_error_at": "TIMESTAMP WITH TIME ZONE"
                }
                for col_name, col_type in pg_device_token_cols.items():
                    connection.execute(text(f"ALTER TABLE device_tokens ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_notification_cols = {
                    "channel": "VARCHAR(16) DEFAULT 'IN_APP'",
                    "critical": "BOOLEAN DEFAULT FALSE",
                    "dedupe_key": "VARCHAR(160)",
                    "action_url": "VARCHAR(512)",
                    "scheduled_at": "TIMESTAMP WITH TIME ZONE",
                    "next_attempt_at": "TIMESTAMP WITH TIME ZONE",
                    "attempt_count": "INTEGER DEFAULT 0",
                    "last_attempt_at": "TIMESTAMP WITH TIME ZONE",
                    "last_error": "VARCHAR(500)"
                }
                for col_name, col_type in pg_notification_cols.items():
                    connection.execute(text(f"ALTER TABLE notifications ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))

                pg_payment_refund_cols = {
                    "approval_status": "VARCHAR(24) DEFAULT 'APPROVED'",
                    "requested_by_admin_id": "VARCHAR(36)",
                    "reviewed_by_admin_id": "VARCHAR(36)",
                    "reviewed_at": "TIMESTAMP WITH TIME ZONE",
                    "review_reason": "TEXT"
                }
                for col_name, col_type in pg_payment_refund_cols.items():
                    connection.execute(text(f"ALTER TABLE payment_refunds ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))
            else:
                inspector = inspect(connection)
                tables = inspector.get_table_names()
                if "users" in tables:
                    cols = {c["name"] for c in inspector.get_columns("users")}
                    add_cols = {
                        "password_hash": "VARCHAR(255)",
                        "email_verified": "BOOLEAN DEFAULT 0",
                        "avatar_url": "VARCHAR(512)",
                        "mfa_enabled": "BOOLEAN DEFAULT 0",
                        "mfa_secret": "VARCHAR(64)",
                        "app_lock_pin_hash": "VARCHAR(128)",
                        "biometric_enabled": "BOOLEAN DEFAULT 0",
                        "passkey_credential_id": "VARCHAR(255)",
                        "passkey_public_key": "TEXT"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE users ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass
                if "customer_profiles" in tables:
                    cols = {c["name"] for c in inspector.get_columns("customer_profiles")}
                    add_cols = {
                        "tier": "VARCHAR(32) DEFAULT 'ROYAL_SOVEREIGN'",
                        "temperature_celsius": "FLOAT DEFAULT 21.0",
                        "quiet_ride": "BOOLEAN DEFAULT TRUE" if is_pg else "BOOLEAN DEFAULT 1",
                        "preferred_music": "VARCHAR(64) DEFAULT 'Classical Symphony'",
                        "preferred_water": "VARCHAR(64) DEFAULT 'Sparkling Mineral'",
                        "luggage_assistance": "BOOLEAN DEFAULT TRUE" if is_pg else "BOOLEAN DEFAULT 1",
                        "loyalty_points_balance": "INTEGER DEFAULT 0",
                        "lifetime_points_earned": "INTEGER DEFAULT 0",
                        "membership_tier": "VARCHAR(32) DEFAULT 'SILVER'",
                        "membership_tier_expires_at": "TIMESTAMP WITH TIME ZONE" if is_pg else "DATETIME",
                        "membership_renewal_reminder_sent": "BOOLEAN DEFAULT FALSE" if is_pg else "BOOLEAN DEFAULT 0",
                        "ride_streak_count": "INTEGER DEFAULT 0",
                        "last_ride_streak_at": "TIMESTAMP WITH TIME ZONE" if is_pg else "DATETIME",
                        "birthday_date": "VARCHAR(10)",
                        "anniversary_date": "VARCHAR(10)",
                        "offer_notification_frequency": "VARCHAR(32) DEFAULT 'WEEKLY'",
                        "no_phone_call_ride": "BOOLEAN DEFAULT 0",
                        "preferred_driver_language": "VARCHAR(32) DEFAULT 'English'",
                        "female_driver_preferred": "BOOLEAN DEFAULT 0",
                        "vehicle_color_preference": "VARCHAR(32) DEFAULT 'Obsidian Black'",
                        "premium_amenities": "VARCHAR(255) DEFAULT 'Evian Sparkling Water, Braided Fast-Charging Kit'",
                        "electric_vehicle_preferred": "BOOLEAN DEFAULT 0",
                        "low_emission_filter": "BOOLEAN DEFAULT 0",
                        "wheelchair_access_required": "BOOLEAN DEFAULT 0",
                        "step_free_pickup_required": "BOOLEAN DEFAULT 0",
                        "assistance_animal_present": "BOOLEAN DEFAULT 0",
                        "extra_legroom_required": "BOOLEAN DEFAULT 0",
                        "pet_friendly_preferred": "BOOLEAN DEFAULT 0",
                        "accessibility_notes": "TEXT"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                if is_pg:
                                    connection.execute(text(f"ALTER TABLE customer_profiles ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))
                                else:
                                    connection.execute(text(f"ALTER TABLE customer_profiles ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "rides" in tables:
                    cols = {c["name"] for c in inspector.get_columns("rides")}
                    add_cols = {
                        "booking_for_self": "BOOLEAN DEFAULT TRUE" if is_pg else "BOOLEAN DEFAULT 1",
                        "passenger_name": "VARCHAR(128)",
                        "passenger_phone": "VARCHAR(32)",
                        "passenger_relationship": "VARCHAR(64)",
                        "is_return_trip": "BOOLEAN DEFAULT FALSE" if is_pg else "BOOLEAN DEFAULT 0",
                        "paired_return_ride_id": "VARCHAR(36)",
                        "is_round_trip": "BOOLEAN DEFAULT FALSE" if is_pg else "BOOLEAN DEFAULT 0",
                        "pickup_notes": "TEXT",
                        "destination_notes": "TEXT",
                        "entrance_guidance": "VARCHAR(255)",
                        "landmark_guidance": "VARCHAR(255)",
                        "airport_code": "VARCHAR(10)",
                        "terminal_name": "VARCHAR(64)",
                        "airline_code": "VARCHAR(32)",
                        "flight_number": "VARCHAR(32)",
                        "flight_delay_minutes": "INTEGER DEFAULT 0",
                        "flight_status": "VARCHAR(32) DEFAULT 'ON_TIME'",
                        "booking_mode": "VARCHAR(32) DEFAULT 'STANDARD'",
                        "hotel_room_number": "VARCHAR(32)",
                        "hotel_guest_name": "VARCHAR(128)",
                        "business_cost_center": "VARCHAR(64)",
                        "business_project_code": "VARCHAR(64)",
                        "business_tax_id": "VARCHAR(64)",
                        "dependent_id": "VARCHAR(36)",
                        "shared_location_source": "VARCHAR(512)",
                        "original_fare_cents": "INTEGER",
                        "fare_difference_cents": "INTEGER DEFAULT 0",
                        "fare_change_reason": "TEXT",
                        "fare_cap_applied": "BOOLEAN DEFAULT 0",
                        "price_locked": "BOOLEAN DEFAULT 0",
                        "locked_fare_cents": "INTEGER",
                        "price_lock_expires_at": "DATETIME",
                        "invoice_number": "VARCHAR(64)",
                        "corporate_billing_ref": "VARCHAR(64)",
                        "split_fare_enabled": "BOOLEAN DEFAULT 0",
                        "route_preference": "VARCHAR(32) DEFAULT 'FASTEST'",
                        "route_deviation_detected": "BOOLEAN DEFAULT 0",
                        "route_deviation_reason": "VARCHAR(255)",
                        "driver_arrival_photo_url": "VARCHAR(512)",
                        "vehicle_model_override": "VARCHAR(64)",
                        "vehicle_plate_override": "VARCHAR(32)",
                        "vehicle_color_override": "VARCHAR(32)",
                        "masked_virtual_number": "VARCHAR(32) DEFAULT '+91 22 6900 4820'",
                        "masked_virtual_ext": "VARCHAR(8) DEFAULT '4921'",
                        "post_trip_contact_expires_at": "DATETIME",
                        "sos_triggered": "BOOLEAN DEFAULT 0",
                        "sos_triggered_at": "DATETIME",
                        "safety_checkin_due_at": "DATETIME",
                        "safety_checkin_status": "VARCHAR(32) DEFAULT 'NORMAL'",
                        "audio_recording_consent_passenger": "BOOLEAN DEFAULT 0",
                        "audio_recording_consent_driver": "BOOLEAN DEFAULT 0",
                        "audio_recording_active": "BOOLEAN DEFAULT 0",
                        "audio_recording_session_id": "VARCHAR(64)",
                        "unexpected_stop_detected": "BOOLEAN DEFAULT 0",
                        "unexpected_stop_duration_seconds": "INTEGER DEFAULT 0",
                        "route_anomaly_detected": "BOOLEAN DEFAULT 0",
                        "route_anomaly_type": "VARCHAR(64)",
                        "high_risk_pickup": "BOOLEAN DEFAULT 0",
                        "high_risk_reason": "VARCHAR(128)",
                        "tip_cents": "INTEGER DEFAULT 0",
                        "tip_status": "VARCHAR(32) DEFAULT 'NONE'",
                        "tip_adjustment_expires_at": "TIMESTAMP WITH TIME ZONE" if is_pg else "DATETIME",
                        "tip_adjusted_at": "TIMESTAMP WITH TIME ZONE" if is_pg else "DATETIME",
                        "points_earned": "INTEGER DEFAULT 0",
                        "child_seat_requested": "BOOLEAN DEFAULT 0",
                        "child_seat_type": "VARCHAR(32)",
                        "luggage_large_count": "INTEGER DEFAULT 0",
                        "luggage_small_count": "INTEGER DEFAULT 0",
                        "wheelchair_requested": "BOOLEAN DEFAULT 0",
                        "step_free_pickup": "BOOLEAN DEFAULT 0",
                        "assistance_animal": "BOOLEAN DEFAULT 0",
                        "extra_legroom_requested": "BOOLEAN DEFAULT 0",
                        "quiet_ride_requested": "BOOLEAN DEFAULT 1",
                        "cabin_temp_requested": "FLOAT",
                        "music_requested": "VARCHAR(64)",
                        "no_phone_call": "BOOLEAN DEFAULT 0",
                        "driver_language_requested": "VARCHAR(32)",
                        "female_driver_requested": "BOOLEAN DEFAULT 0",
                        "vehicle_color_requested": "VARCHAR(32)",
                        "amenities_requested": "VARCHAR(255)",
                        "ev_ride_requested": "BOOLEAN DEFAULT 0",
                        "pet_friendly_requested": "BOOLEAN DEFAULT 0",
                        "special_assistance_notes": "TEXT"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                if is_pg:
                                    connection.execute(text(f"ALTER TABLE rides ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))
                                else:
                                    connection.execute(text(f"ALTER TABLE rides ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "driver_ratings" in tables:
                    cols = {c["name"] for c in inspector.get_columns("driver_ratings")}
                    add_cols = {
                        "cleanliness_score": "INTEGER DEFAULT 5",
                        "comfort_score": "INTEGER DEFAULT 5",
                        "service_quality_score": "INTEGER DEFAULT 5",
                        "navigation_score": "INTEGER DEFAULT 5",
                        "feedback_tags_json": "TEXT",
                        "private_feedback": "TEXT",
                        "tip_cents": "INTEGER DEFAULT 0"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                if is_pg:
                                    connection.execute(text(f"ALTER TABLE driver_ratings ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))
                                else:
                                    connection.execute(text(f"ALTER TABLE driver_ratings ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "referral_records" in tables:
                    cols = {c["name"] for c in inspector.get_columns("referral_records")}
                    add_cols = {
                        "fraud_review_status": "VARCHAR(32) DEFAULT 'APPROVED'",
                        "fraud_risk_score": "FLOAT DEFAULT 0.0",
                        "fraud_reason": "TEXT",
                        "reviewed_by": "VARCHAR(36)",
                        "reviewed_at": "TIMESTAMP WITH TIME ZONE" if is_pg else "DATETIME"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                if is_pg:
                                    connection.execute(text(f"ALTER TABLE referral_records ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))
                                else:
                                    connection.execute(text(f"ALTER TABLE referral_records ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "driver_profiles" in tables:
                    cols = {c["name"] for c in inspector.get_columns("driver_profiles")}
                    add_cols = {
                        "security_badge_id": "VARCHAR(32) DEFAULT 'GC-SEC-8821'",
                        "background_check_status": "VARCHAR(32) DEFAULT 'VERIFIED'",
                        "background_check_date": "DATETIME",
                        "academy_certified": "BOOLEAN DEFAULT 1",
                        "academy_level": "VARCHAR(32) DEFAULT 'MASTER_CHAUFFEUR'",
                        "onboarding_step": "VARCHAR(32) DEFAULT 'IDENTITY_SUBMITTED'",
                        "profile_completeness_percentage": "INTEGER DEFAULT 65",
                        "identity_verified": "BOOLEAN DEFAULT 0",
                        "identity_reverification_due": "BOOLEAN DEFAULT 0",
                        "last_identity_verified_at": "DATETIME",
                        "preferred_operating_zones": "VARCHAR(255) DEFAULT 'AIRPORT_CORRIDOR,SOUTH_MUMBAI,BKC_FINANCIAL'",
                        "preferred_vehicle_categories": "VARCHAR(255) DEFAULT 'EXECUTIVE_SEDAN,PRESTIGE_SUV'",
                        "languages_spoken": "VARCHAR(128) DEFAULT 'English, Hindi, Marathi'",
                        "service_etiquette_level": "VARCHAR(64) DEFAULT 'Black Sovereign Executive'",
                        "cabin_music_preference": "VARCHAR(64) DEFAULT 'Classical & Soft Jazz'",
                        "cabin_temp_celsius": "INTEGER DEFAULT 21",
                        "emergency_contact_name": "VARCHAR(128)",
                        "emergency_contact_phone": "VARCHAR(32)",
                        "emergency_contact_relationship": "VARCHAR(64)",
                        "account_deletion_requested": "BOOLEAN DEFAULT 0",
                        "account_deletion_requested_at": "DATETIME",
                        "policy_acknowledged_at": "DATETIME"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE driver_profiles ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "driver_documents" in tables:
                    cols = {c["name"] for c in inspector.get_columns("driver_documents")}
                    add_cols = {
                        "expiry_reminder_sent_at": "DATETIME",
                        "image_quality_score": "FLOAT DEFAULT 1.0",
                        "quality_assessment_notes": "VARCHAR(255)",
                        "resubmission_count": "INTEGER DEFAULT 0"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE driver_documents ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "vehicles" in tables:
                    cols = {c["name"] for c in inspector.get_columns("vehicles")}
                    add_cols = {
                        "rc_number": "VARCHAR(64) DEFAULT 'DL-01-RC-2024-9912'",
                        "permit_number": "VARCHAR(64) DEFAULT 'DL-COMM-PVT-09882'",
                        "puc_expiry_date": "DATETIME",
                        "fitness_expiry_date": "DATETIME",
                        "insurance_policy_number": "VARCHAR(64) DEFAULT 'POL-BAJAJ-ALL-88912'"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE vehicles ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "vehicle_categories" in tables:
                    cols = {c["name"] for c in inspector.get_columns("vehicle_categories")}
                    add_cols = {
                        "photos_gallery_json": "TEXT DEFAULT '[]'",
                        "legroom_inches": "FLOAT DEFAULT 40.0",
                        "quietness_db": "FLOAT DEFAULT 54.0",
                        "luggage_large_capacity": "INTEGER DEFAULT 2",
                        "luggage_small_capacity": "INTEGER DEFAULT 2",
                        "has_wifi": "BOOLEAN DEFAULT 1",
                        "has_massage_seats": "BOOLEAN DEFAULT 0",
                        "has_champagne_cooler": "BOOLEAN DEFAULT 0",
                        "has_reclining_seats": "BOOLEAN DEFAULT 1",
                        "is_electric": "BOOLEAN DEFAULT 0",
                        "emissions_g_per_km": "FLOAT DEFAULT 115.0",
                        "wheelchair_accessible": "BOOLEAN DEFAULT 0",
                        "step_free_height_cm": "FLOAT DEFAULT 38.0",
                        "pet_friendly": "BOOLEAN DEFAULT 1",
                        "child_seat_available": "BOOLEAN DEFAULT 1",
                        "available_colors_json": "VARCHAR(255) DEFAULT '[\"Obsidian Black\", \"Pearl White\", \"Royal Navy Blue\"]'",
                        "available_amenities_json": "VARCHAR(512) DEFAULT '[\"Evian Sparkling Water\", \"Braided Fast-Charging Kit\", \"Financial Times\", \"Cashmere Blanket\", \"Amber Wood Diffuser\"]'"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE vehicle_categories ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "ride_chat_messages" in tables:
                    cols = {c["name"] for c in inspector.get_columns("ride_chat_messages")}
                    add_cols = {
                        "translated_text": "TEXT",
                        "translated_language": "VARCHAR(10)",
                        "meeting_point_name": "VARCHAR(128)",
                        "meeting_point_lat": "FLOAT",
                        "meeting_point_lng": "FLOAT"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE ride_chat_messages ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "ride_stops" in tables:
                    cols = {c["name"] for c in inspector.get_columns("ride_stops")}
                    add_cols = {
                        "sequence_order": "INTEGER DEFAULT 1",
                        "dwell_time_minutes": "INTEGER DEFAULT 0",
                        "passenger_instructions": "VARCHAR(255)",
                        "entrance_landmark": "VARCHAR(255)"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                if is_pg:
                                    connection.execute(text(f"ALTER TABLE ride_stops ADD COLUMN IF NOT EXISTS {col_name} {col_type}"))
                                else:
                                    connection.execute(text(f"ALTER TABLE ride_stops ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "fare_rules" in tables:
                    cols = {c["name"] for c in inspector.get_columns("fare_rules")}
                    add_cols = {
                        "fare_cap_cents": "INTEGER",
                        "tax_percentage": "FLOAT DEFAULT 5.0",
                        "amenities_fee_cents": "INTEGER DEFAULT 5000"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE fare_rules ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "fare_estimates" in tables:
                    cols = {c["name"] for c in inspector.get_columns("fare_estimates")}
                    add_cols = {
                        "base_fare_cents": "INTEGER DEFAULT 0",
                        "distance_charge_cents": "INTEGER DEFAULT 0",
                        "time_charge_cents": "INTEGER DEFAULT 0",
                        "tax_cents": "INTEGER DEFAULT 0",
                        "amenities_fee_cents": "INTEGER DEFAULT 0",
                        "fare_min_cents": "INTEGER DEFAULT 0",
                        "fare_max_cents": "INTEGER DEFAULT 0",
                        "confidence_level": "VARCHAR(20) DEFAULT 'HIGH'",
                        "confidence_percentage": "INTEGER DEFAULT 95",
                        "is_capped": "BOOLEAN DEFAULT 0",
                        "cap_discount_cents": "INTEGER DEFAULT 0"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE fare_estimates ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "wallets" in tables:
                    cols = {c["name"] for c in inspector.get_columns("wallets")}
                    add_cols = {
                        "auto_topup_enabled": "BOOLEAN DEFAULT 0",
                        "auto_topup_threshold_cents": "INTEGER DEFAULT 50000",
                        "auto_topup_amount_cents": "INTEGER DEFAULT 200000",
                        "auto_topup_method_id": "VARCHAR(36)",
                        "low_balance_alert_sent": "BOOLEAN DEFAULT 0"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE wallets ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "payments" in tables:
                    cols = {c["name"] for c in inspector.get_columns("payments")}
                    add_cols = {
                        "refunded_amount_cents": "INTEGER DEFAULT 0",
                        "upi_intent_uri": "VARCHAR(512)",
                        "saved_method_id": "VARCHAR(36)",
                        "corporate_account_id": "VARCHAR(64)",
                        "split_id": "VARCHAR(36)"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE payments ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "support_tickets" in tables:
                    cols = {c["name"] for c in inspector.get_columns("support_tickets")}
                    add_cols = {
                        "complaint_reference_number": "VARCHAR(64)",
                        "language_preference": "VARCHAR(10) DEFAULT 'en'",
                        "assigned_squad": "VARCHAR(64) DEFAULT 'VIP_CONCIERGE'",
                        "is_escalated": "BOOLEAN DEFAULT 0",
                        "escalation_reason": "VARCHAR(255)",
                        "escalated_at": "DATETIME",
                        "sla_due_at": "DATETIME",
                        "last_staff_update_at": "DATETIME",
                        "lost_item_category": "VARCHAR(64)",
                        "lost_item_description": "TEXT",
                        "lost_item_status": "VARCHAR(32)",
                        "csat_score": "INTEGER",
                        "csat_comment": "TEXT",
                        "resolved_at": "DATETIME",
                        "data_retention_days": "INTEGER DEFAULT 365",
                        "refund_amount_cents": "INTEGER",
                        "fare_review_evidence": "TEXT",
                        "cancellation_reason_code": "VARCHAR(50)",
                        "fee_waiver_requested": "BOOLEAN DEFAULT 0",
                        "duplicate_of_ticket_id": "VARCHAR(36)"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE support_tickets ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "support_messages" in tables:
                    cols = {c["name"] for c in inspector.get_columns("support_messages")}
                    if "read_at" not in cols:
                        try:
                            connection.execute(text("ALTER TABLE support_messages ADD COLUMN read_at DATETIME"))
                        except Exception:
                            pass

                if "pooled_bookings" in tables:
                    cols = {c["name"] for c in inspector.get_columns("pooled_bookings")}
                    add_cols = {
                        "share_stop_details_with_co_passengers": "BOOLEAN DEFAULT 0",
                        "cancellation_reason": "TEXT"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE pooled_bookings ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "driver_locations" in tables:
                    cols = {c["name"] for c in inspector.get_columns("driver_locations")}
                    add_cols = {
                        "spoof_risk_score": "FLOAT DEFAULT 0.0",
                        "quality_flags_json": "TEXT DEFAULT '[]'",
                        "source": "VARCHAR(24) DEFAULT 'FUSED'"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE driver_locations ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "saved_location_pins" in tables:
                    cols = {c["name"] for c in inspector.get_columns("saved_location_pins")}
                    add_cols = {
                        "entrance_notes": "VARCHAR(255)",
                        "entrance_code": "VARCHAR(32)"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE saved_location_pins ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "commission_rules" in tables:
                    cols = {c["name"] for c in inspector.get_columns("commission_rules")}
                    add_cols = {
                        "approval_status": "VARCHAR(32) DEFAULT 'APPROVED'",
                        "approval_reason": "TEXT"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE commission_rules ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "notifications" in tables:
                    cols = {c["name"] for c in inspector.get_columns("notifications")}
                    add_cols = {
                        "channel": "VARCHAR(16) DEFAULT 'IN_APP'",
                        "critical": "BOOLEAN DEFAULT 0",
                        "dedupe_key": "VARCHAR(160)",
                        "action_url": "VARCHAR(512)",
                        "scheduled_at": "DATETIME",
                        "next_attempt_at": "DATETIME",
                        "attempt_count": "INTEGER DEFAULT 0",
                        "last_attempt_at": "DATETIME",
                        "last_error": "VARCHAR(500)",
                        "sent_at": "DATETIME"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE notifications ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass

                if "ride_assignments" in tables:
                    cols = {c["name"] for c in inspector.get_columns("ride_assignments")}
                    add_cols = {
                        "is_accepted": "BOOLEAN",
                        "metadata_json": "TEXT"
                    }
                    for col_name, col_type in add_cols.items():
                        if col_name not in cols:
                            try:
                                connection.execute(text(f"ALTER TABLE ride_assignments ADD COLUMN {col_name} {col_type}"))
                            except Exception:
                                pass
    except Exception as e:
        logger.warning(f"Error syncing columns: {e}")

async def init_db():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    try:
        async with engine.connect() as conn:
            await conn.run_sync(sync_missing_columns)
            await conn.commit()
    except Exception as e:
        logger.warning(f"Schema sync completed: {e}")

