"""Persist driver onboarding, training, inspection, and support records.

Revision ID: b90c3f4a1d27
Revises: 866dcc677e2a
"""
from alembic import op
import sqlalchemy as sa

revision = "b90c3f4a1d27"
down_revision = "866dcc677e2a"
branch_labels = None
depends_on = None


def upgrade() -> None:
    profile_columns = [
        sa.Column("security_badge_id", sa.String(32), nullable=False, server_default=""),
        sa.Column("background_check_status", sa.String(32), nullable=False, server_default="PENDING"),
        sa.Column("background_check_date", sa.DateTime(timezone=True), nullable=True),
        sa.Column("academy_certified", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("academy_level", sa.String(64), nullable=False, server_default="Training not completed"),
        sa.Column("onboarding_step", sa.String(32), nullable=False, server_default="IDENTITY_SUBMITTED"),
        sa.Column("profile_completeness_percentage", sa.Integer(), nullable=False, server_default="10"),
        sa.Column("identity_verified", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("identity_reverification_due", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("last_identity_verified_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("preferred_operating_zones", sa.String(255), nullable=False, server_default=""),
        sa.Column("preferred_vehicle_categories", sa.String(255), nullable=False, server_default=""),
        sa.Column("languages_spoken", sa.String(128), nullable=False, server_default=""),
        sa.Column("service_etiquette_level", sa.String(64), nullable=False, server_default="Professional"),
        sa.Column("cabin_music_preference", sa.String(64), nullable=False, server_default="Passenger preference"),
        sa.Column("cabin_temp_celsius", sa.Integer(), nullable=False, server_default="21"),
        sa.Column("emergency_contact_name", sa.String(128), nullable=True),
        sa.Column("emergency_contact_phone", sa.String(32), nullable=True),
        sa.Column("emergency_contact_relationship", sa.String(64), nullable=True),
        sa.Column("account_deletion_requested", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("account_deletion_requested_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("policy_acknowledged_at", sa.DateTime(timezone=True), nullable=True),
    ]
    for column in profile_columns:
        op.add_column("driver_profiles", column)

    op.create_table(
        "driver_training_modules",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("module_code", sa.String(50), nullable=False, unique=True),
        sa.Column("title", sa.String(255), nullable=False),
        sa.Column("description", sa.Text(), nullable=False),
        sa.Column("content_markdown", sa.Text(), nullable=False),
        sa.Column("video_url", sa.String(512)),
        sa.Column("estimated_duration_minutes", sa.Integer(), nullable=False),
        sa.Column("sort_order", sa.Integer(), nullable=False),
        sa.Column("is_mandatory", sa.Boolean(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_driver_training_modules_module_code", "driver_training_modules", ["module_code"], unique=True)
    op.create_table(
        "driver_training_progress",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("driver_id", sa.String(36), sa.ForeignKey("driver_profiles.id"), nullable=False),
        sa.Column("module_id", sa.String(36), sa.ForeignKey("driver_training_modules.id"), nullable=False),
        sa.Column("is_completed", sa.Boolean(), nullable=False),
        sa.Column("assessment_score_percentage", sa.Integer()),
        sa.Column("assessment_passed", sa.Boolean(), nullable=False),
        sa.Column("completed_at", sa.DateTime(timezone=True)),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_driver_training_progress_driver_id", "driver_training_progress", ["driver_id"])
    op.create_index("ix_driver_training_progress_module_id", "driver_training_progress", ["module_id"])
    op.create_table(
        "driver_vehicle_inspections",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("driver_id", sa.String(36), sa.ForeignKey("driver_profiles.id"), nullable=False),
        sa.Column("vehicle_id", sa.String(36), sa.ForeignKey("vehicles.id"), nullable=False),
        sa.Column("inspection_status", sa.String(30), nullable=False),
        sa.Column("checklist_json", sa.Text(), nullable=False),
        sa.Column("photo_front_url", sa.String(512)), sa.Column("photo_rear_url", sa.String(512)),
        sa.Column("photo_left_url", sa.String(512)), sa.Column("photo_right_url", sa.String(512)),
        sa.Column("photo_interior_front_url", sa.String(512)), sa.Column("photo_interior_rear_url", sa.String(512)),
        sa.Column("inspector_notes", sa.Text()),
        sa.Column("inspected_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_driver_vehicle_inspections_driver_id", "driver_vehicle_inspections", ["driver_id"])
    op.create_index("ix_driver_vehicle_inspections_vehicle_id", "driver_vehicle_inspections", ["vehicle_id"])
    op.create_table(
        "driver_vehicle_inspection_photos",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("driver_id", sa.String(36), sa.ForeignKey("driver_profiles.id"), nullable=False),
        sa.Column("vehicle_id", sa.String(36), sa.ForeignKey("vehicles.id"), nullable=False),
        sa.Column("photo_type", sa.String(32), nullable=False),
        sa.Column("storage_path", sa.String(512), nullable=False),
        sa.Column("mime_type", sa.String(100), nullable=False),
        sa.Column("file_size_bytes", sa.Integer(), nullable=False),
        sa.Column("file_hash_sha256", sa.String(64), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_driver_vehicle_inspection_photos_driver_id", "driver_vehicle_inspection_photos", ["driver_id"])
    op.create_index("ix_driver_vehicle_inspection_photos_vehicle_id", "driver_vehicle_inspection_photos", ["vehicle_id"])
    op.create_table(
        "driver_schedule_availabilities",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("driver_id", sa.String(36), sa.ForeignKey("driver_profiles.id"), nullable=False),
        sa.Column("day_of_week", sa.Integer(), nullable=False),
        sa.Column("start_time", sa.String(10), nullable=False), sa.Column("end_time", sa.String(10), nullable=False),
        sa.Column("is_active", sa.Boolean(), nullable=False), sa.Column("is_rest_day", sa.Boolean(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("driver_id", "day_of_week", name="uq_driver_schedule_day"),
    )
    op.create_index("ix_driver_schedule_availabilities_driver_id", "driver_schedule_availabilities", ["driver_id"])
    op.create_table(
        "driver_onboarding_tickets",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("driver_id", sa.String(36), sa.ForeignKey("driver_profiles.id"), nullable=False),
        sa.Column("subject", sa.String(255), nullable=False), sa.Column("message", sa.Text(), nullable=False),
        sa.Column("status", sa.String(30), nullable=False), sa.Column("admin_response", sa.Text()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False), sa.Column("resolved_at", sa.DateTime(timezone=True)),
    )
    op.create_index("ix_driver_onboarding_tickets_driver_id", "driver_onboarding_tickets", ["driver_id"])


def downgrade() -> None:
    op.drop_table("driver_onboarding_tickets")
    op.drop_table("driver_schedule_availabilities")
    op.drop_table("driver_vehicle_inspections")
    op.drop_table("driver_vehicle_inspection_photos")
    op.drop_table("driver_training_progress")
    op.drop_table("driver_training_modules")
    for name in ["policy_acknowledged_at", "account_deletion_requested_at", "account_deletion_requested", "emergency_contact_relationship", "emergency_contact_phone", "emergency_contact_name", "cabin_temp_celsius", "cabin_music_preference", "service_etiquette_level", "languages_spoken", "preferred_vehicle_categories", "preferred_operating_zones", "last_identity_verified_at", "identity_reverification_due", "identity_verified", "profile_completeness_percentage", "onboarding_step", "academy_level", "academy_certified", "background_check_date", "background_check_status", "security_badge_id"]:
        op.drop_column("driver_profiles", name)
