"""Persist dispatch preferences, expiring offers, and trip actions.

Revision ID: c27ad1845b60
Revises: b90c3f4a1d27
"""
from alembic import op
import sqlalchemy as sa

revision = "c27ad1845b60"
down_revision = "b90c3f4a1d27"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("ride_assignments", sa.Column("expires_at", sa.DateTime(timezone=True), nullable=True))
    op.add_column("ride_assignments", sa.Column("escalation_level", sa.Integer(), nullable=False, server_default="0"))
    op.create_index("ix_ride_assignments_expires_at", "ride_assignments", ["expires_at"])
    op.create_table(
        "driver_dispatch_preferences",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("driver_id", sa.String(36), sa.ForeignKey("driver_profiles.id"), nullable=False),
        sa.Column("minimum_fare_cents", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("maximum_pickup_distance_km", sa.Float(), nullable=False, server_default="20"),
        sa.Column("vehicle_categories_json", sa.Text(), nullable=False, server_default="[]"),
        sa.Column("navigation_provider", sa.String(24), nullable=False, server_default="GOOGLE_MAPS"),
        sa.Column("voice_guidance_enabled", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("reroute_on_closure_enabled", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_driver_dispatch_preferences_driver_id", "driver_dispatch_preferences", ["driver_id"], unique=True)
    op.create_table(
        "ride_destination_change_requests",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("ride_id", sa.String(36), sa.ForeignKey("rides.id"), nullable=False),
        sa.Column("requested_by_user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("status", sa.String(32), nullable=False, server_default="PENDING_CUSTOMER_CONFIRMATION"),
        sa.Column("destination_address", sa.String(255), nullable=False),
        sa.Column("destination_latitude", sa.Float(), nullable=False),
        sa.Column("destination_longitude", sa.Float(), nullable=False),
        sa.Column("proposed_fare_cents", sa.Integer(), nullable=False),
        sa.Column("proposed_distance_km", sa.Float(), nullable=False),
        sa.Column("proposed_duration_minutes", sa.Float(), nullable=False),
        sa.Column("fare_difference_cents", sa.Integer(), nullable=False),
        sa.Column("customer_responded_at", sa.DateTime(timezone=True)),
        sa.Column("driver_responded_at", sa.DateTime(timezone=True)),
        sa.Column("rejection_reason", sa.Text()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_ride_destination_change_requests_ride_id", "ride_destination_change_requests", ["ride_id"])
    op.create_index("ix_ride_destination_change_requests_status", "ride_destination_change_requests", ["status"])
    op.create_table(
        "ride_operational_events",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("ride_id", sa.String(36), sa.ForeignKey("rides.id"), nullable=False),
        sa.Column("actor_user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("action", sa.String(40), nullable=False),
        sa.Column("details_json", sa.Text(), nullable=False, server_default="{}"),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_ride_operational_events_ride_id", "ride_operational_events", ["ride_id"])
    op.create_index("ix_ride_operational_events_action", "ride_operational_events", ["action"])
    op.create_index("ix_ride_operational_events_created_at", "ride_operational_events", ["created_at"])


def downgrade() -> None:
    op.drop_table("ride_operational_events")
    op.drop_table("ride_destination_change_requests")
    op.drop_table("driver_dispatch_preferences")
    op.drop_index("ix_ride_assignments_expires_at", table_name="ride_assignments")
    op.drop_column("ride_assignments", "escalation_level")
    op.drop_column("ride_assignments", "expires_at")
