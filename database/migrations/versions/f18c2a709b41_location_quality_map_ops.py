"""Location quality, saved pins, and geofence change history."""
from alembic import op
import sqlalchemy as sa

revision = "f18c2a709b41"
down_revision = "f17a4d29c603"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("driver_locations", sa.Column("spoof_risk_score", sa.Float(), nullable=False, server_default="0"))
    op.add_column("driver_locations", sa.Column("quality_flags_json", sa.Text(), nullable=False, server_default="[]"))
    op.add_column("driver_locations", sa.Column("source", sa.String(24), nullable=False, server_default="FUSED"))
    op.create_table(
        "saved_location_pins",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("label", sa.String(80), nullable=False),
        sa.Column("address", sa.String(255), nullable=False),
        sa.Column("latitude", sa.Float(), nullable=False),
        sa.Column("longitude", sa.Float(), nullable=False),
        sa.Column("pin_type", sa.String(24), nullable=False, server_default="PICKUP"),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_saved_location_pins_user_id", "saved_location_pins", ["user_id"])
    op.create_table(
        "address_quality_feedback",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("rating", sa.Integer(), nullable=False),
        sa.Column("issue_code", sa.String(32), nullable=False),
        sa.Column("provider_mode", sa.String(32), nullable=False),
        sa.Column("coordinates_verified", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_address_quality_feedback_user_id", "address_quality_feedback", ["user_id"])
    op.create_index("ix_address_quality_feedback_created_at", "address_quality_feedback", ["created_at"])
    op.create_table(
        "geofence_zone_audits",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("zone_id", sa.String(36), sa.ForeignKey("geofence_zones.id"), nullable=False),
        sa.Column("admin_user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("action", sa.String(24), nullable=False),
        sa.Column("previous_state_json", sa.Text(), nullable=True),
        sa.Column("new_state_json", sa.Text(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_geofence_zone_audits_zone_id", "geofence_zone_audits", ["zone_id"])
    op.create_index("ix_geofence_zone_audits_admin_user_id", "geofence_zone_audits", ["admin_user_id"])
    op.create_index("ix_geofence_zone_audits_created_at", "geofence_zone_audits", ["created_at"])


def downgrade() -> None:
    op.drop_index("ix_geofence_zone_audits_created_at", table_name="geofence_zone_audits")
    op.drop_index("ix_geofence_zone_audits_admin_user_id", table_name="geofence_zone_audits")
    op.drop_index("ix_geofence_zone_audits_zone_id", table_name="geofence_zone_audits")
    op.drop_table("geofence_zone_audits")
    op.drop_index("ix_address_quality_feedback_created_at", table_name="address_quality_feedback")
    op.drop_index("ix_address_quality_feedback_user_id", table_name="address_quality_feedback")
    op.drop_table("address_quality_feedback")
    op.drop_index("ix_saved_location_pins_user_id", table_name="saved_location_pins")
    op.drop_table("saved_location_pins")
    op.drop_column("driver_locations", "source")
    op.drop_column("driver_locations", "quality_flags_json")
    op.drop_column("driver_locations", "spoof_risk_score")
