"""Add co-passenger stop privacy and shared trip coordination messages."""
from alembic import op
import sqlalchemy as sa

revision = "e9c7a2b641d5"
down_revision = "d31f8a2e70c4"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "pooled_bookings",
        sa.Column("share_stop_details_with_co_passengers", sa.Boolean(), nullable=False, server_default=sa.false()),
    )
    op.create_table(
        "pooled_trip_messages",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("pooled_trip_id", sa.String(36), sa.ForeignKey("pooled_trips.id"), nullable=False),
        sa.Column("booking_id", sa.String(36), sa.ForeignKey("pooled_bookings.id"), nullable=True),
        sa.Column("sender_user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("sender_role", sa.String(20), nullable=False),
        sa.Column("message", sa.String(1000), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_pooled_trip_messages_pooled_trip_id", "pooled_trip_messages", ["pooled_trip_id"])
    op.create_index("ix_pooled_trip_messages_booking_id", "pooled_trip_messages", ["booking_id"])
    op.create_index("ix_pooled_trip_messages_sender_user_id", "pooled_trip_messages", ["sender_user_id"])
    op.create_index("ix_pooled_trip_messages_created_at", "pooled_trip_messages", ["created_at"])
    op.create_table(
        "shared_ride_preferences",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, unique=True),
        sa.Column("maximum_detour_km", sa.Float(), nullable=False, server_default="5.0"),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )


def downgrade() -> None:
    op.drop_table("shared_ride_preferences")
    op.drop_table("pooled_trip_messages")
    op.drop_column("pooled_bookings", "share_stop_details_with_co_passengers")
