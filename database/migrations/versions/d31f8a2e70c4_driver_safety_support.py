"""Driver safety, fatigue, rating appeals, and evidence records."""
from alembic import op
import sqlalchemy as sa

revision = "d31f8a2e70c4"
down_revision = "c27ad1845b60"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table("driver_safety_preferences",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("driver_id", sa.String(36), sa.ForeignKey("driver_profiles.id"), nullable=False, unique=True),
        sa.Column("share_trip_location_with_customer", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("privacy_notice_acknowledged_at", sa.DateTime(timezone=True)),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False))
    op.create_index("ix_driver_safety_preferences_driver_id", "driver_safety_preferences", ["driver_id"])
    op.create_table("driver_fatigue_checks",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("driver_id", sa.String(36), sa.ForeignKey("driver_profiles.id"), nullable=False),
        sa.Column("ride_id", sa.String(36), sa.ForeignKey("rides.id")),
        sa.Column("result", sa.String(24), nullable=False), sa.Column("note", sa.Text()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False))
    op.create_index("ix_driver_fatigue_checks_driver_id", "driver_fatigue_checks", ["driver_id"])
    op.create_index("ix_driver_fatigue_checks_ride_id", "driver_fatigue_checks", ["ride_id"])
    op.create_index("ix_driver_fatigue_checks_created_at", "driver_fatigue_checks", ["created_at"])
    op.create_table("driver_rating_appeals",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("rating_id", sa.String(36), sa.ForeignKey("driver_ratings.id"), nullable=False, unique=True),
        sa.Column("driver_id", sa.String(36), sa.ForeignKey("driver_profiles.id"), nullable=False),
        sa.Column("reason", sa.Text(), nullable=False), sa.Column("status", sa.String(24), nullable=False, server_default="OPEN"),
        sa.Column("reviewer_user_id", sa.String(36), sa.ForeignKey("users.id")), sa.Column("reviewer_notes", sa.Text()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False), sa.Column("resolved_at", sa.DateTime(timezone=True)))
    op.create_index("ix_driver_rating_appeals_rating_id", "driver_rating_appeals", ["rating_id"])
    op.create_index("ix_driver_rating_appeals_driver_id", "driver_rating_appeals", ["driver_id"])
    op.create_index("ix_driver_rating_appeals_status", "driver_rating_appeals", ["status"])
    op.create_table("incident_evidence",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("incident_report_id", sa.String(36), sa.ForeignKey("incident_reports.id"), nullable=False),
        sa.Column("uploaded_by_user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("storage_path", sa.String(512), nullable=False), sa.Column("mime_type", sa.String(100), nullable=False),
        sa.Column("file_size_bytes", sa.Integer(), nullable=False), sa.Column("file_hash_sha256", sa.String(64), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False))
    op.create_index("ix_incident_evidence_incident_report_id", "incident_evidence", ["incident_report_id"])
    op.create_index("ix_incident_evidence_uploaded_by_user_id", "incident_evidence", ["uploaded_by_user_id"])


def downgrade() -> None:
    op.drop_table("incident_evidence")
    op.drop_table("driver_rating_appeals")
    op.drop_table("driver_fatigue_checks")
    op.drop_table("driver_safety_preferences")
