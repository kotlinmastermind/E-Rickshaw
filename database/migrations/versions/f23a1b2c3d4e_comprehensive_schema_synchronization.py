"""Comprehensive schema reconciliation and persistent entity migration.

Revision ID: f23a1b2c3d4e
Revises: f22a1b2c3d4e
Create Date: 2026-11-08 21:30:00
"""
from alembic import op
import sqlalchemy as sa

revision = "f23a1b2c3d4e"
down_revision = "f22a1b2c3d4e"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    insp = sa.inspect(bind) if not getattr(op.get_context(), "as_sql", False) else None
    tables = set(insp.get_table_names()) if insp else set()

    # 1. Account Deletion Requests
    if "account_deletion_requests" not in tables:
        op.create_table(
            "account_deletion_requests",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False, index=True),
            sa.Column("status", sa.String(32), nullable=False, server_default="PENDING_GRACE_PERIOD", index=True),
            sa.Column("cooldown_days_total", sa.Integer(), nullable=False, server_default="30"),
            sa.Column("requested_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("scheduled_purge_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("cancellation_reason", sa.Text(), nullable=True),
            sa.Column("cancelled_at", sa.DateTime(timezone=True), nullable=True),
        )
    # 2. Data Export Archives
    if "data_export_archives" not in tables:
        op.create_table(
            "data_export_archives",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False, index=True),
            sa.Column("status", sa.String(32), nullable=False, server_default="READY"),
            sa.Column("archive_format", sa.String(16), nullable=False, server_default="JSON"),
            sa.Column("file_size_bytes", sa.Integer(), nullable=False, server_default="0"),
            sa.Column("download_url", sa.String(512), nullable=False),
            sa.Column("payload_json", sa.Text(), nullable=True),
            sa.Column("storage_path", sa.String(512), nullable=True),
            sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )
    # 3. Recurring Ride Schedules
    if "recurring_ride_schedules" not in tables:
        op.create_table(
            "recurring_ride_schedules",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("frequency", sa.String(32), nullable=False, server_default="WEEKLY"),
            sa.Column("days_of_week", sa.String(64), nullable=False, server_default="MON,TUE,WED,THU,FRI"),
            sa.Column("pickup_time_str", sa.String(10), nullable=False),
            sa.Column("pickup_address", sa.String(255), nullable=False),
            sa.Column("pickup_latitude", sa.Float(), nullable=False),
            sa.Column("pickup_longitude", sa.Float(), nullable=False),
            sa.Column("destination_address", sa.String(255), nullable=False),
            sa.Column("destination_latitude", sa.Float(), nullable=False),
            sa.Column("destination_longitude", sa.Float(), nullable=False),
            sa.Column("vehicle_category_id", sa.String(36), sa.ForeignKey("vehicle_categories.id"), nullable=False),
            sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.true()),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )
    # 4. Loyalty Points Ledger
    if "loyalty_points_ledger" not in tables:
        op.create_table(
            "loyalty_points_ledger",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("transaction_type", sa.String(50), nullable=False),
            sa.Column("points_change", sa.Integer(), nullable=False),
            sa.Column("balance_after", sa.Integer(), nullable=False),
            sa.Column("reference_id", sa.String(64), nullable=True),
            sa.Column("description", sa.String(255), nullable=True),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )
    # 5. Customer Milestone Badges
    if "customer_milestone_badges" not in tables:
        op.create_table(
            "customer_milestone_badges",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("badge_code", sa.String(64), nullable=False),
            sa.Column("badge_name", sa.String(128), nullable=False),
            sa.Column("badge_icon", sa.String(512), nullable=True),
            sa.Column("description", sa.Text(), nullable=True),
            sa.Column("unlocked_at", sa.DateTime(timezone=True), nullable=False),
        )
    # 6. Personalized Offers
    if "personalized_offers" not in tables:
        op.create_table(
            "personalized_offers",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("title", sa.String(128), nullable=False),
            sa.Column("tagline", sa.String(255), nullable=True),
            sa.Column("offer_type", sa.String(64), nullable=False, server_default="DISCOUNT"),
            sa.Column("discount_percent", sa.Float(), nullable=False, server_default="10.0"),
            sa.Column("max_discount_cents", sa.Integer(), nullable=False, server_default="10000"),
            sa.Column("promo_code", sa.String(64), nullable=True),
            sa.Column("badge_label", sa.String(64), nullable=True),
            sa.Column("days", sa.Integer(), nullable=False, server_default="7"),
            sa.Column("starts_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("is_claimed", sa.Boolean(), nullable=False, server_default=sa.false()),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )
    # 7. Service Recovery Credits
    if "service_recovery_credits" not in tables:
        op.create_table(
            "service_recovery_credits",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("ride_id", sa.String(36), sa.ForeignKey("rides.id"), nullable=True),
            sa.Column("credit_amount_cents", sa.Integer(), nullable=False),
            sa.Column("reason_category", sa.String(64), nullable=False),
            sa.Column("notes", sa.Text(), nullable=True),
            sa.Column("issued_by_admin_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
            sa.Column("status", sa.String(32), nullable=False, server_default="ISSUED"),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )
    # 8. User Preferences
    if "user_preferences" not in tables:
        op.create_table(
            "user_preferences",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), unique=True, nullable=False),
            sa.Column("preferred_language", sa.String(10), nullable=False, server_default="en"),
            sa.Column("marketing_push_opt_in", sa.Boolean(), nullable=False, server_default=sa.true()),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        )
    # 9. User Saved Phones
    if "user_saved_phones" not in tables:
        op.create_table(
            "user_saved_phones",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False, index=True),
            sa.Column("phone_number", sa.String(20), nullable=False),
            sa.Column("label", sa.String(32), nullable=False, server_default="MOBILE"),
            sa.Column("is_primary", sa.Boolean(), nullable=False, server_default=sa.false()),
            sa.Column("is_verified", sa.Boolean(), nullable=False, server_default=sa.false()),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )
    # 10. Privacy Data Subject Requests
    if "privacy_data_subject_requests" not in tables:
        op.create_table(
            "privacy_data_subject_requests",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("request_number", sa.String(64), unique=True, index=True, nullable=False),
            sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False, index=True),
            sa.Column("user_role", sa.String(20), nullable=False, server_default="CUSTOMER"),
            sa.Column("request_type", sa.String(64), nullable=False),
            sa.Column("details", sa.Text(), nullable=True),
            sa.Column("status", sa.String(32), nullable=False, server_default="UNDER_PROCESSING"),
            sa.Column("resolution_notes", sa.Text(), nullable=True),
            sa.Column("assigned_dpo_admin_id", sa.String(36), sa.ForeignKey("users.id"), nullable=True),
            sa.Column("fulfilled_at", sa.DateTime(timezone=True), nullable=True),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )
    # 11. Customer Surveys
    if "customer_surveys" not in tables:
        op.create_table(
            "customer_surveys",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("ride_id", sa.String(36), sa.ForeignKey("rides.id"), nullable=True),
            sa.Column("survey_type", sa.String(64), nullable=False, server_default="POST_RIDE"),
            sa.Column("score", sa.Integer(), nullable=False, server_default="5"),
            sa.Column("dimensions_json", sa.Text(), nullable=False, server_default="{}"),
            sa.Column("comments", sa.Text(), nullable=True),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )

    # 2. Add Missing Columns to Existing Tables
    # --- Table: broadcast_notifications ---
    if "broadcast_notifications" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("broadcast_notifications")} if insp else set()
        if "audience_filter_json" not in existing_cols:
            op.add_column("broadcast_notifications", sa.Column('audience_filter_json', sa.Text(), nullable=True))
        if "channel" not in existing_cols:
            op.add_column("broadcast_notifications", sa.Column('channel', sa.String(length=20), nullable=True))
        if "delivery_failure_count" not in existing_cols:
            op.add_column("broadcast_notifications", sa.Column('delivery_failure_count', sa.Integer(), nullable=True, server_default='0'))
        if "delivery_success_count" not in existing_cols:
            op.add_column("broadcast_notifications", sa.Column('delivery_success_count', sa.Integer(), nullable=True, server_default='0'))
        if "is_emergency" not in existing_cols:
            op.add_column("broadcast_notifications", sa.Column('is_emergency', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "localized_content_json" not in existing_cols:
            op.add_column("broadcast_notifications", sa.Column('localized_content_json', sa.Text(), nullable=True))
        if "opt_out_count" not in existing_cols:
            op.add_column("broadcast_notifications", sa.Column('opt_out_count', sa.Integer(), nullable=True, server_default='0'))
        if "preview_json" not in existing_cols:
            op.add_column("broadcast_notifications", sa.Column('preview_json', sa.Text(), nullable=True))

    # --- Table: customer_profiles ---
    if "customer_profiles" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("customer_profiles")} if insp else set()
        if "accessibility_notes" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('accessibility_notes', sa.Text(), nullable=True))
        if "assistance_animal_present" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('assistance_animal_present', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "electric_vehicle_preferred" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('electric_vehicle_preferred', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "extra_legroom_required" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('extra_legroom_required', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "female_driver_preferred" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('female_driver_preferred', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "low_emission_filter" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('low_emission_filter', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "luggage_assistance" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('luggage_assistance', sa.Boolean(), nullable=True, server_default='TRUE'))
        if "no_phone_call_ride" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('no_phone_call_ride', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "pet_friendly_preferred" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('pet_friendly_preferred', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "preferred_driver_language" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('preferred_driver_language', sa.String(length=64), nullable=True))
        if "preferred_music" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('preferred_music', sa.String(length=64), nullable=True))
        if "preferred_water" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('preferred_water', sa.String(length=64), nullable=True))
        if "premium_amenities" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('premium_amenities', sa.String(length=255), nullable=True))
        if "quiet_ride" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('quiet_ride', sa.Boolean(), nullable=True, server_default='TRUE'))
        if "step_free_pickup_required" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('step_free_pickup_required', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "temperature_celsius" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('temperature_celsius', sa.Float(), nullable=True, server_default='21.0'))
        if "tier" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('tier', sa.String(length=32), nullable=True))
        if "vehicle_color_preference" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('vehicle_color_preference', sa.String(length=64), nullable=True))
        if "wheelchair_access_required" not in existing_cols:
            op.add_column("customer_profiles", sa.Column('wheelchair_access_required', sa.Boolean(), nullable=True, server_default='FALSE'))

    # --- Table: device_tokens ---
    if "device_tokens" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("device_tokens")} if insp else set()
        if "is_active" not in existing_cols:
            op.add_column("device_tokens", sa.Column('is_active', sa.Boolean(), nullable=True, server_default='TRUE'))
        if "last_error_at" not in existing_cols:
            op.add_column("device_tokens", sa.Column('last_error_at', sa.DateTime(timezone=True), nullable=True))

    # --- Table: driver_documents ---
    if "driver_documents" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("driver_documents")} if insp else set()
        if "expiry_reminder_sent_at" not in existing_cols:
            op.add_column("driver_documents", sa.Column('expiry_reminder_sent_at', sa.DateTime(timezone=True), nullable=True))
        if "image_quality_score" not in existing_cols:
            op.add_column("driver_documents", sa.Column('image_quality_score', sa.Float(), nullable=True, server_default='1.0'))
        if "quality_assessment_notes" not in existing_cols:
            op.add_column("driver_documents", sa.Column('quality_assessment_notes', sa.String(length=255), nullable=True))
        if "resubmission_count" not in existing_cols:
            op.add_column("driver_documents", sa.Column('resubmission_count', sa.Integer(), nullable=True, server_default='0'))

    # --- Table: driver_locations ---
    if "driver_locations" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("driver_locations")} if insp else set()
        if "quality_flags_json" not in existing_cols:
            op.add_column("driver_locations", sa.Column('quality_flags_json', sa.Text(), nullable=True))
        if "source" not in existing_cols:
            op.add_column("driver_locations", sa.Column('source', sa.String(length=24), nullable=True))
        if "spoof_risk_score" not in existing_cols:
            op.add_column("driver_locations", sa.Column('spoof_risk_score', sa.Float(), nullable=True, server_default='0.0'))

    # --- Table: driver_payout_requests ---
    if "driver_payout_requests" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("driver_payout_requests")} if insp else set()
        if "exception_notes" not in existing_cols:
            op.add_column("driver_payout_requests", sa.Column('exception_notes', sa.Text(), nullable=True))
        if "settlement_reference" not in existing_cols:
            op.add_column("driver_payout_requests", sa.Column('settlement_reference', sa.String(length=128), nullable=True))

    # --- Table: driver_profiles ---
    if "driver_profiles" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("driver_profiles")} if insp else set()
        if "academy_certified" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('academy_certified', sa.Boolean(), nullable=True, server_default='TRUE'))
        if "academy_level" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('academy_level', sa.String(length=32), nullable=True))
        if "account_deletion_requested" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('account_deletion_requested', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "account_deletion_requested_at" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('account_deletion_requested_at', sa.DateTime(timezone=True), nullable=True))
        if "background_check_date" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('background_check_date', sa.DateTime(timezone=True), nullable=True))
        if "background_check_status" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('background_check_status', sa.String(length=32), nullable=True))
        if "cabin_music_preference" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('cabin_music_preference', sa.String(length=64), nullable=True))
        if "cabin_temp_celsius" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('cabin_temp_celsius', sa.Integer(), nullable=True, server_default='21'))
        if "emergency_contact_name" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('emergency_contact_name', sa.String(length=128), nullable=True))
        if "emergency_contact_phone" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('emergency_contact_phone', sa.String(length=32), nullable=True))
        if "emergency_contact_relationship" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('emergency_contact_relationship', sa.String(length=64), nullable=True))
        if "identity_reverification_due" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('identity_reverification_due', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "identity_verified" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('identity_verified', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "languages_spoken" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('languages_spoken', sa.String(length=128), nullable=True))
        if "last_identity_verified_at" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('last_identity_verified_at', sa.DateTime(timezone=True), nullable=True))
        if "onboarding_step" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('onboarding_step', sa.String(length=32), nullable=True))
        if "policy_acknowledged_at" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('policy_acknowledged_at', sa.DateTime(timezone=True), nullable=True))
        if "preferred_operating_zones" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('preferred_operating_zones', sa.String(length=255), nullable=True))
        if "preferred_vehicle_categories" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('preferred_vehicle_categories', sa.String(length=255), nullable=True))
        if "profile_completeness_percentage" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('profile_completeness_percentage', sa.Integer(), nullable=True, server_default='65'))
        if "security_badge_id" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('security_badge_id', sa.String(length=32), nullable=True))
        if "service_etiquette_level" not in existing_cols:
            op.add_column("driver_profiles", sa.Column('service_etiquette_level', sa.String(length=64), nullable=True))

    # --- Table: driver_ratings ---
    if "driver_ratings" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("driver_ratings")} if insp else set()
        if "cleanliness_score" not in existing_cols:
            op.add_column("driver_ratings", sa.Column('cleanliness_score', sa.Integer(), nullable=True))
        if "comfort_score" not in existing_cols:
            op.add_column("driver_ratings", sa.Column('comfort_score', sa.Integer(), nullable=True))
        if "feedback_tags_json" not in existing_cols:
            op.add_column("driver_ratings", sa.Column('feedback_tags_json', sa.Text(), nullable=True))
        if "navigation_score" not in existing_cols:
            op.add_column("driver_ratings", sa.Column('navigation_score', sa.Integer(), nullable=True))
        if "private_feedback" not in existing_cols:
            op.add_column("driver_ratings", sa.Column('private_feedback', sa.Text(), nullable=True))
        if "service_quality_score" not in existing_cols:
            op.add_column("driver_ratings", sa.Column('service_quality_score', sa.Integer(), nullable=True))
        if "tip_cents" not in existing_cols:
            op.add_column("driver_ratings", sa.Column('tip_cents', sa.Integer(), nullable=True, server_default='0'))

    # --- Table: fare_estimates ---
    if "fare_estimates" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("fare_estimates")} if insp else set()
        if "amenities_fee_cents" not in existing_cols:
            op.add_column("fare_estimates", sa.Column('amenities_fee_cents', sa.Integer(), nullable=True, server_default='0'))
        if "base_fare_cents" not in existing_cols:
            op.add_column("fare_estimates", sa.Column('base_fare_cents', sa.Integer(), nullable=True, server_default='0'))
        if "cap_discount_cents" not in existing_cols:
            op.add_column("fare_estimates", sa.Column('cap_discount_cents', sa.Integer(), nullable=True, server_default='0'))
        if "confidence_level" not in existing_cols:
            op.add_column("fare_estimates", sa.Column('confidence_level', sa.String(length=20), nullable=True))
        if "confidence_percentage" not in existing_cols:
            op.add_column("fare_estimates", sa.Column('confidence_percentage', sa.Integer(), nullable=True, server_default='95'))
        if "distance_charge_cents" not in existing_cols:
            op.add_column("fare_estimates", sa.Column('distance_charge_cents', sa.Integer(), nullable=True, server_default='0'))
        if "fare_max_cents" not in existing_cols:
            op.add_column("fare_estimates", sa.Column('fare_max_cents', sa.Integer(), nullable=True, server_default='0'))
        if "fare_min_cents" not in existing_cols:
            op.add_column("fare_estimates", sa.Column('fare_min_cents', sa.Integer(), nullable=True, server_default='0'))
        if "is_capped" not in existing_cols:
            op.add_column("fare_estimates", sa.Column('is_capped', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "tax_cents" not in existing_cols:
            op.add_column("fare_estimates", sa.Column('tax_cents', sa.Integer(), nullable=True, server_default='0'))
        if "time_charge_cents" not in existing_cols:
            op.add_column("fare_estimates", sa.Column('time_charge_cents', sa.Integer(), nullable=True, server_default='0'))

    # --- Table: fare_rules ---
    if "fare_rules" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("fare_rules")} if insp else set()
        if "amenities_fee_cents" not in existing_cols:
            op.add_column("fare_rules", sa.Column('amenities_fee_cents', sa.Integer(), nullable=True, server_default='5000'))
        if "fare_cap_cents" not in existing_cols:
            op.add_column("fare_rules", sa.Column('fare_cap_cents', sa.Integer(), nullable=True))
        if "tax_percentage" not in existing_cols:
            op.add_column("fare_rules", sa.Column('tax_percentage', sa.Float(), nullable=True, server_default='5.0'))

    # --- Table: notifications ---
    if "notifications" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("notifications")} if insp else set()
        if "action_url" not in existing_cols:
            op.add_column("notifications", sa.Column('action_url', sa.String(length=512), nullable=True))
        if "attempt_count" not in existing_cols:
            op.add_column("notifications", sa.Column('attempt_count', sa.Integer(), nullable=True, server_default='0'))
        if "channel" not in existing_cols:
            op.add_column("notifications", sa.Column('channel', sa.String(length=16), nullable=True))
        if "critical" not in existing_cols:
            op.add_column("notifications", sa.Column('critical', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "dedupe_key" not in existing_cols:
            op.add_column("notifications", sa.Column('dedupe_key', sa.String(length=160), nullable=True))
        if "last_attempt_at" not in existing_cols:
            op.add_column("notifications", sa.Column('last_attempt_at', sa.DateTime(timezone=True), nullable=True))
        if "last_error" not in existing_cols:
            op.add_column("notifications", sa.Column('last_error', sa.String(length=500), nullable=True))
        if "next_attempt_at" not in existing_cols:
            op.add_column("notifications", sa.Column('next_attempt_at', sa.DateTime(timezone=True), nullable=True))
        if "scheduled_at" not in existing_cols:
            op.add_column("notifications", sa.Column('scheduled_at', sa.DateTime(timezone=True), nullable=True))

    # --- Table: payment_refunds ---
    if "payment_refunds" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("payment_refunds")} if insp else set()
        if "approval_status" not in existing_cols:
            op.add_column("payment_refunds", sa.Column('approval_status', sa.String(length=24), nullable=True))
        if "requested_by_admin_id" not in existing_cols:
            op.add_column("payment_refunds", sa.Column('requested_by_admin_id', sa.String(length=36), nullable=True))
        if "review_reason" not in existing_cols:
            op.add_column("payment_refunds", sa.Column('review_reason', sa.Text(), nullable=True))
        if "reviewed_at" not in existing_cols:
            op.add_column("payment_refunds", sa.Column('reviewed_at', sa.DateTime(timezone=True), nullable=True))
        if "reviewed_by_admin_id" not in existing_cols:
            op.add_column("payment_refunds", sa.Column('reviewed_by_admin_id', sa.String(length=36), nullable=True))

    # --- Table: payments ---
    if "payments" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("payments")} if insp else set()
        if "corporate_account_id" not in existing_cols:
            op.add_column("payments", sa.Column('corporate_account_id', sa.String(length=64), nullable=True))
        if "refunded_amount_cents" not in existing_cols:
            op.add_column("payments", sa.Column('refunded_amount_cents', sa.Integer(), nullable=True, server_default='0'))
        if "saved_method_id" not in existing_cols:
            op.add_column("payments", sa.Column('saved_method_id', sa.String(length=36), nullable=True))
        if "split_id" not in existing_cols:
            op.add_column("payments", sa.Column('split_id', sa.String(length=36), nullable=True))
        if "upi_intent_uri" not in existing_cols:
            op.add_column("payments", sa.Column('upi_intent_uri', sa.String(length=512), nullable=True))

    # --- Table: pooled_bookings ---
    if "pooled_bookings" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("pooled_bookings")} if insp else set()
        if "cancellation_reason" not in existing_cols:
            op.add_column("pooled_bookings", sa.Column('cancellation_reason', sa.Text(), nullable=True))
        if "share_stop_details_with_co_passengers" not in existing_cols:
            op.add_column("pooled_bookings", sa.Column('share_stop_details_with_co_passengers', sa.Boolean(), nullable=True, server_default='FALSE'))

    # --- Table: ride_assignments ---
    if "ride_assignments" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("ride_assignments")} if insp else set()
        if "escalation_level" not in existing_cols:
            op.add_column("ride_assignments", sa.Column('escalation_level', sa.Integer(), nullable=True, server_default='0'))
        if "expires_at" not in existing_cols:
            op.add_column("ride_assignments", sa.Column('expires_at', sa.DateTime(timezone=True), nullable=True))
        if "is_accepted" not in existing_cols:
            op.add_column("ride_assignments", sa.Column('is_accepted', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "metadata_json" not in existing_cols:
            op.add_column("ride_assignments", sa.Column('metadata_json', sa.Text(), nullable=True))

    # --- Table: ride_chat_messages ---
    if "ride_chat_messages" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("ride_chat_messages")} if insp else set()
        if "meeting_point_lat" not in existing_cols:
            op.add_column("ride_chat_messages", sa.Column('meeting_point_lat', sa.Float(), nullable=True))
        if "meeting_point_lng" not in existing_cols:
            op.add_column("ride_chat_messages", sa.Column('meeting_point_lng', sa.Float(), nullable=True))
        if "meeting_point_name" not in existing_cols:
            op.add_column("ride_chat_messages", sa.Column('meeting_point_name', sa.String(length=128), nullable=True))
        if "translated_language" not in existing_cols:
            op.add_column("ride_chat_messages", sa.Column('translated_language', sa.String(length=10), nullable=True))
        if "translated_text" not in existing_cols:
            op.add_column("ride_chat_messages", sa.Column('translated_text', sa.Text(), nullable=True))

    # --- Table: ride_stops ---
    if "ride_stops" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("ride_stops")} if insp else set()
        if "dwell_time_minutes" not in existing_cols:
            op.add_column("ride_stops", sa.Column('dwell_time_minutes', sa.Integer(), nullable=True, server_default='0'))
        if "entrance_landmark" not in existing_cols:
            op.add_column("ride_stops", sa.Column('entrance_landmark', sa.String(length=255), nullable=True))
        if "passenger_instructions" not in existing_cols:
            op.add_column("ride_stops", sa.Column('passenger_instructions', sa.String(length=255), nullable=True))
        if "sequence_order" not in existing_cols:
            op.add_column("ride_stops", sa.Column('sequence_order', sa.Integer(), nullable=True, server_default='1'))

    # --- Table: rides ---
    if "rides" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("rides")} if insp else set()
        if "airline_code" not in existing_cols:
            op.add_column("rides", sa.Column('airline_code', sa.String(length=32), nullable=True))
        if "airport_code" not in existing_cols:
            op.add_column("rides", sa.Column('airport_code', sa.String(length=10), nullable=True))
        if "amenities_requested" not in existing_cols:
            op.add_column("rides", sa.Column('amenities_requested', sa.String(length=255), nullable=True))
        if "assistance_animal" not in existing_cols:
            op.add_column("rides", sa.Column('assistance_animal', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "audio_recording_active" not in existing_cols:
            op.add_column("rides", sa.Column('audio_recording_active', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "audio_recording_consent_driver" not in existing_cols:
            op.add_column("rides", sa.Column('audio_recording_consent_driver', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "audio_recording_consent_passenger" not in existing_cols:
            op.add_column("rides", sa.Column('audio_recording_consent_passenger', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "audio_recording_session_id" not in existing_cols:
            op.add_column("rides", sa.Column('audio_recording_session_id', sa.String(length=64), nullable=True))
        if "booking_for_self" not in existing_cols:
            op.add_column("rides", sa.Column('booking_for_self', sa.Boolean(), nullable=True, server_default='TRUE'))
        if "booking_mode" not in existing_cols:
            op.add_column("rides", sa.Column('booking_mode', sa.String(length=32), nullable=True))
        if "business_cost_center" not in existing_cols:
            op.add_column("rides", sa.Column('business_cost_center', sa.String(length=64), nullable=True))
        if "business_project_code" not in existing_cols:
            op.add_column("rides", sa.Column('business_project_code', sa.String(length=64), nullable=True))
        if "business_tax_id" not in existing_cols:
            op.add_column("rides", sa.Column('business_tax_id', sa.String(length=64), nullable=True))
        if "cabin_temp_requested" not in existing_cols:
            op.add_column("rides", sa.Column('cabin_temp_requested', sa.Float(), nullable=True))
        if "child_seat_requested" not in existing_cols:
            op.add_column("rides", sa.Column('child_seat_requested', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "child_seat_type" not in existing_cols:
            op.add_column("rides", sa.Column('child_seat_type', sa.String(length=64), nullable=True))
        if "corporate_billing_ref" not in existing_cols:
            op.add_column("rides", sa.Column('corporate_billing_ref', sa.String(length=64), nullable=True))
        if "dependent_id" not in existing_cols:
            op.add_column("rides", sa.Column('dependent_id', sa.String(length=36), nullable=True))
        if "destination_notes" not in existing_cols:
            op.add_column("rides", sa.Column('destination_notes', sa.Text(), nullable=True))
        if "driver_arrival_photo_url" not in existing_cols:
            op.add_column("rides", sa.Column('driver_arrival_photo_url', sa.String(length=512), nullable=True))
        if "driver_language_requested" not in existing_cols:
            op.add_column("rides", sa.Column('driver_language_requested', sa.String(length=64), nullable=True))
        if "entrance_guidance" not in existing_cols:
            op.add_column("rides", sa.Column('entrance_guidance', sa.String(length=255), nullable=True))
        if "ev_ride_requested" not in existing_cols:
            op.add_column("rides", sa.Column('ev_ride_requested', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "extra_legroom_requested" not in existing_cols:
            op.add_column("rides", sa.Column('extra_legroom_requested', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "fare_cap_applied" not in existing_cols:
            op.add_column("rides", sa.Column('fare_cap_applied', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "fare_change_reason" not in existing_cols:
            op.add_column("rides", sa.Column('fare_change_reason', sa.Text(), nullable=True))
        if "fare_difference_cents" not in existing_cols:
            op.add_column("rides", sa.Column('fare_difference_cents', sa.Integer(), nullable=True, server_default='0'))
        if "female_driver_requested" not in existing_cols:
            op.add_column("rides", sa.Column('female_driver_requested', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "flight_delay_minutes" not in existing_cols:
            op.add_column("rides", sa.Column('flight_delay_minutes', sa.Integer(), nullable=True, server_default='0'))
        if "flight_number" not in existing_cols:
            op.add_column("rides", sa.Column('flight_number', sa.String(length=32), nullable=True))
        if "flight_status" not in existing_cols:
            op.add_column("rides", sa.Column('flight_status', sa.String(length=32), nullable=True))
        if "high_risk_pickup" not in existing_cols:
            op.add_column("rides", sa.Column('high_risk_pickup', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "high_risk_reason" not in existing_cols:
            op.add_column("rides", sa.Column('high_risk_reason', sa.String(length=128), nullable=True))
        if "hotel_guest_name" not in existing_cols:
            op.add_column("rides", sa.Column('hotel_guest_name', sa.String(length=128), nullable=True))
        if "hotel_room_number" not in existing_cols:
            op.add_column("rides", sa.Column('hotel_room_number', sa.String(length=32), nullable=True))
        if "invoice_number" not in existing_cols:
            op.add_column("rides", sa.Column('invoice_number', sa.String(length=64), nullable=True))
        if "is_return_trip" not in existing_cols:
            op.add_column("rides", sa.Column('is_return_trip', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "is_round_trip" not in existing_cols:
            op.add_column("rides", sa.Column('is_round_trip', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "landmark_guidance" not in existing_cols:
            op.add_column("rides", sa.Column('landmark_guidance', sa.String(length=255), nullable=True))
        if "locked_fare_cents" not in existing_cols:
            op.add_column("rides", sa.Column('locked_fare_cents', sa.Integer(), nullable=True))
        if "luggage_large_count" not in existing_cols:
            op.add_column("rides", sa.Column('luggage_large_count', sa.Integer(), nullable=True, server_default='0'))
        if "luggage_small_count" not in existing_cols:
            op.add_column("rides", sa.Column('luggage_small_count', sa.Integer(), nullable=True, server_default='0'))
        if "masked_virtual_ext" not in existing_cols:
            op.add_column("rides", sa.Column('masked_virtual_ext', sa.String(length=8), nullable=True))
        if "masked_virtual_number" not in existing_cols:
            op.add_column("rides", sa.Column('masked_virtual_number', sa.String(length=32), nullable=True))
        if "music_requested" not in existing_cols:
            op.add_column("rides", sa.Column('music_requested', sa.String(length=64), nullable=True))
        if "no_phone_call" not in existing_cols:
            op.add_column("rides", sa.Column('no_phone_call', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "original_fare_cents" not in existing_cols:
            op.add_column("rides", sa.Column('original_fare_cents', sa.Integer(), nullable=True))
        if "paired_return_ride_id" not in existing_cols:
            op.add_column("rides", sa.Column('paired_return_ride_id', sa.String(length=36), nullable=True))
        if "passenger_name" not in existing_cols:
            op.add_column("rides", sa.Column('passenger_name', sa.String(length=128), nullable=True))
        if "passenger_phone" not in existing_cols:
            op.add_column("rides", sa.Column('passenger_phone', sa.String(length=32), nullable=True))
        if "passenger_relationship" not in existing_cols:
            op.add_column("rides", sa.Column('passenger_relationship', sa.String(length=64), nullable=True))
        if "pet_friendly_requested" not in existing_cols:
            op.add_column("rides", sa.Column('pet_friendly_requested', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "pickup_notes" not in existing_cols:
            op.add_column("rides", sa.Column('pickup_notes', sa.Text(), nullable=True))
        if "post_trip_contact_expires_at" not in existing_cols:
            op.add_column("rides", sa.Column('post_trip_contact_expires_at', sa.DateTime(timezone=True), nullable=True))
        if "price_lock_expires_at" not in existing_cols:
            op.add_column("rides", sa.Column('price_lock_expires_at', sa.DateTime(timezone=True), nullable=True))
        if "price_locked" not in existing_cols:
            op.add_column("rides", sa.Column('price_locked', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "quiet_ride_requested" not in existing_cols:
            op.add_column("rides", sa.Column('quiet_ride_requested', sa.Boolean(), nullable=True, server_default='TRUE'))
        if "route_anomaly_detected" not in existing_cols:
            op.add_column("rides", sa.Column('route_anomaly_detected', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "route_anomaly_type" not in existing_cols:
            op.add_column("rides", sa.Column('route_anomaly_type', sa.String(length=64), nullable=True))
        if "route_deviation_detected" not in existing_cols:
            op.add_column("rides", sa.Column('route_deviation_detected', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "route_deviation_reason" not in existing_cols:
            op.add_column("rides", sa.Column('route_deviation_reason', sa.String(length=255), nullable=True))
        if "route_preference" not in existing_cols:
            op.add_column("rides", sa.Column('route_preference', sa.String(length=32), nullable=True))
        if "safety_checkin_due_at" not in existing_cols:
            op.add_column("rides", sa.Column('safety_checkin_due_at', sa.DateTime(timezone=True), nullable=True))
        if "safety_checkin_status" not in existing_cols:
            op.add_column("rides", sa.Column('safety_checkin_status', sa.String(length=32), nullable=True))
        if "shared_location_source" not in existing_cols:
            op.add_column("rides", sa.Column('shared_location_source', sa.String(length=512), nullable=True))
        if "sos_triggered" not in existing_cols:
            op.add_column("rides", sa.Column('sos_triggered', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "sos_triggered_at" not in existing_cols:
            op.add_column("rides", sa.Column('sos_triggered_at', sa.DateTime(timezone=True), nullable=True))
        if "special_assistance_notes" not in existing_cols:
            op.add_column("rides", sa.Column('special_assistance_notes', sa.Text(), nullable=True))
        if "split_fare_enabled" not in existing_cols:
            op.add_column("rides", sa.Column('split_fare_enabled', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "step_free_pickup" not in existing_cols:
            op.add_column("rides", sa.Column('step_free_pickup', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "terminal_name" not in existing_cols:
            op.add_column("rides", sa.Column('terminal_name', sa.String(length=64), nullable=True))
        if "unexpected_stop_detected" not in existing_cols:
            op.add_column("rides", sa.Column('unexpected_stop_detected', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "unexpected_stop_duration_seconds" not in existing_cols:
            op.add_column("rides", sa.Column('unexpected_stop_duration_seconds', sa.Integer(), nullable=True, server_default='0'))
        if "vehicle_color_override" not in existing_cols:
            op.add_column("rides", sa.Column('vehicle_color_override', sa.String(length=32), nullable=True))
        if "vehicle_color_requested" not in existing_cols:
            op.add_column("rides", sa.Column('vehicle_color_requested', sa.String(length=64), nullable=True))
        if "vehicle_model_override" not in existing_cols:
            op.add_column("rides", sa.Column('vehicle_model_override', sa.String(length=64), nullable=True))
        if "vehicle_plate_override" not in existing_cols:
            op.add_column("rides", sa.Column('vehicle_plate_override', sa.String(length=32), nullable=True))
        if "wheelchair_requested" not in existing_cols:
            op.add_column("rides", sa.Column('wheelchair_requested', sa.Boolean(), nullable=True, server_default='FALSE'))

    # --- Table: saved_location_pins ---
    if "saved_location_pins" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("saved_location_pins")} if insp else set()
        if "entrance_code" not in existing_cols:
            op.add_column("saved_location_pins", sa.Column('entrance_code', sa.String(length=32), nullable=True))
        if "entrance_notes" not in existing_cols:
            op.add_column("saved_location_pins", sa.Column('entrance_notes', sa.String(length=255), nullable=True))

    # --- Table: support_messages ---
    if "support_messages" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("support_messages")} if insp else set()
        if "read_at" not in existing_cols:
            op.add_column("support_messages", sa.Column('read_at', sa.DateTime(timezone=True), nullable=True))

    # --- Table: support_tickets ---
    if "support_tickets" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("support_tickets")} if insp else set()
        if "assigned_squad" not in existing_cols:
            op.add_column("support_tickets", sa.Column('assigned_squad', sa.String(length=64), nullable=True))
        if "cancellation_reason_code" not in existing_cols:
            op.add_column("support_tickets", sa.Column('cancellation_reason_code', sa.String(length=50), nullable=True))
        if "complaint_reference_number" not in existing_cols:
            op.add_column("support_tickets", sa.Column('complaint_reference_number', sa.String(length=64), nullable=True))
        if "csat_comment" not in existing_cols:
            op.add_column("support_tickets", sa.Column('csat_comment', sa.Text(), nullable=True))
        if "csat_score" not in existing_cols:
            op.add_column("support_tickets", sa.Column('csat_score', sa.Integer(), nullable=True))
        if "data_retention_days" not in existing_cols:
            op.add_column("support_tickets", sa.Column('data_retention_days', sa.Integer(), nullable=True, server_default='365'))
        if "duplicate_of_ticket_id" not in existing_cols:
            op.add_column("support_tickets", sa.Column('duplicate_of_ticket_id', sa.String(length=36), nullable=True))
        if "escalated_at" not in existing_cols:
            op.add_column("support_tickets", sa.Column('escalated_at', sa.DateTime(timezone=True), nullable=True))
        if "escalation_reason" not in existing_cols:
            op.add_column("support_tickets", sa.Column('escalation_reason', sa.String(length=255), nullable=True))
        if "fare_review_evidence" not in existing_cols:
            op.add_column("support_tickets", sa.Column('fare_review_evidence', sa.Text(), nullable=True))
        if "fee_waiver_requested" not in existing_cols:
            op.add_column("support_tickets", sa.Column('fee_waiver_requested', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "is_escalated" not in existing_cols:
            op.add_column("support_tickets", sa.Column('is_escalated', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "language_preference" not in existing_cols:
            op.add_column("support_tickets", sa.Column('language_preference', sa.String(length=10), nullable=True))
        if "last_staff_update_at" not in existing_cols:
            op.add_column("support_tickets", sa.Column('last_staff_update_at', sa.DateTime(timezone=True), nullable=True))
        if "lost_item_category" not in existing_cols:
            op.add_column("support_tickets", sa.Column('lost_item_category', sa.String(length=64), nullable=True))
        if "lost_item_description" not in existing_cols:
            op.add_column("support_tickets", sa.Column('lost_item_description', sa.Text(), nullable=True))
        if "lost_item_status" not in existing_cols:
            op.add_column("support_tickets", sa.Column('lost_item_status', sa.String(length=32), nullable=True))
        if "refund_amount_cents" not in existing_cols:
            op.add_column("support_tickets", sa.Column('refund_amount_cents', sa.Integer(), nullable=True))
        if "resolved_at" not in existing_cols:
            op.add_column("support_tickets", sa.Column('resolved_at', sa.DateTime(timezone=True), nullable=True))
        if "sla_due_at" not in existing_cols:
            op.add_column("support_tickets", sa.Column('sla_due_at', sa.DateTime(timezone=True), nullable=True))

    # --- Table: users ---
    if "users" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("users")} if insp else set()
        if "app_lock_pin_hash" not in existing_cols:
            op.add_column("users", sa.Column('app_lock_pin_hash', sa.String(length=128), nullable=True))
        if "avatar_url" not in existing_cols:
            op.add_column("users", sa.Column('avatar_url', sa.String(length=512), nullable=True))
        if "biometric_enabled" not in existing_cols:
            op.add_column("users", sa.Column('biometric_enabled', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "email_verified" not in existing_cols:
            op.add_column("users", sa.Column('email_verified', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "mfa_enabled" not in existing_cols:
            op.add_column("users", sa.Column('mfa_enabled', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "mfa_secret" not in existing_cols:
            op.add_column("users", sa.Column('mfa_secret', sa.String(length=64), nullable=True))
        if "passkey_credential_id" not in existing_cols:
            op.add_column("users", sa.Column('passkey_credential_id', sa.String(length=255), nullable=True))
        if "passkey_public_key" not in existing_cols:
            op.add_column("users", sa.Column('passkey_public_key', sa.Text(), nullable=True))
        if "password_hash" not in existing_cols:
            op.add_column("users", sa.Column('password_hash', sa.String(length=255), nullable=True))

    # --- Table: vehicle_categories ---
    if "vehicle_categories" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("vehicle_categories")} if insp else set()
        if "available_amenities_json" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('available_amenities_json', sa.Text(), nullable=True))
        if "available_colors_json" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('available_colors_json', sa.Text(), nullable=True))
        if "child_seat_available" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('child_seat_available', sa.Boolean(), nullable=True, server_default='TRUE'))
        if "emissions_g_per_km" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('emissions_g_per_km', sa.Float(), nullable=True, server_default='140.0'))
        if "has_champagne_cooler" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('has_champagne_cooler', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "has_massage_seats" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('has_massage_seats', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "has_reclining_seats" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('has_reclining_seats', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "has_wifi" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('has_wifi', sa.Boolean(), nullable=True, server_default='TRUE'))
        if "is_electric" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('is_electric', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "legroom_inches" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('legroom_inches', sa.Float(), nullable=True, server_default='42.0'))
        if "luggage_large_capacity" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('luggage_large_capacity', sa.Integer(), nullable=True, server_default='2'))
        if "luggage_small_capacity" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('luggage_small_capacity', sa.Integer(), nullable=True, server_default='2'))
        if "pet_friendly" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('pet_friendly', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "photos_gallery_json" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('photos_gallery_json', sa.Text(), nullable=True))
        if "quietness_db" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('quietness_db', sa.Float(), nullable=True, server_default='52.0'))
        if "step_free_height_cm" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('step_free_height_cm', sa.Float(), nullable=True, server_default='35.0'))
        if "wheelchair_accessible" not in existing_cols:
            op.add_column("vehicle_categories", sa.Column('wheelchair_accessible', sa.Boolean(), nullable=True, server_default='FALSE'))

    # --- Table: vehicles ---
    if "vehicles" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("vehicles")} if insp else set()
        if "fitness_expiry_date" not in existing_cols:
            op.add_column("vehicles", sa.Column('fitness_expiry_date', sa.DateTime(timezone=True), nullable=True))
        if "insurance_policy_number" not in existing_cols:
            op.add_column("vehicles", sa.Column('insurance_policy_number', sa.String(length=64), nullable=True))
        if "permit_number" not in existing_cols:
            op.add_column("vehicles", sa.Column('permit_number', sa.String(length=64), nullable=True))
        if "puc_expiry_date" not in existing_cols:
            op.add_column("vehicles", sa.Column('puc_expiry_date', sa.DateTime(timezone=True), nullable=True))
        if "rc_number" not in existing_cols:
            op.add_column("vehicles", sa.Column('rc_number', sa.String(length=64), nullable=True))

    # --- Table: wallets ---
    if "wallets" in tables:
        existing_cols = {c["name"] for c in insp.get_columns("wallets")} if insp else set()
        if "auto_topup_amount_cents" not in existing_cols:
            op.add_column("wallets", sa.Column('auto_topup_amount_cents', sa.Integer(), nullable=True, server_default='200000'))
        if "auto_topup_enabled" not in existing_cols:
            op.add_column("wallets", sa.Column('auto_topup_enabled', sa.Boolean(), nullable=True, server_default='FALSE'))
        if "auto_topup_method_id" not in existing_cols:
            op.add_column("wallets", sa.Column('auto_topup_method_id', sa.String(length=36), nullable=True))
        if "auto_topup_threshold_cents" not in existing_cols:
            op.add_column("wallets", sa.Column('auto_topup_threshold_cents', sa.Integer(), nullable=True, server_default='50000'))
        if "low_balance_alert_sent" not in existing_cols:
            op.add_column("wallets", sa.Column('low_balance_alert_sent', sa.Boolean(), nullable=True, server_default='FALSE'))

def downgrade() -> None:
    pass
