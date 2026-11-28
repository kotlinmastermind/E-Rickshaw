"""Track driver-submitted locality requests for nationwide service expansion.

Revision ID: 20261128_01
Revises: 20261127_07
"""
from alembic import op
import sqlalchemy as sa

revision = "20261128_01"
down_revision = "20261127_07"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "driver_service_area_requests",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column(
            "driver_id",
            sa.String(length=36),
            sa.ForeignKey("driver_profiles.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("state_name", sa.String(length=80), nullable=False),
        sa.Column("district_name", sa.String(length=100), nullable=False),
        sa.Column("locality_name", sa.String(length=120), nullable=False),
        sa.Column("postal_code", sa.String(length=6), nullable=True),
        sa.Column("latitude", sa.Float(), nullable=False),
        sa.Column("longitude", sa.Float(), nullable=False),
        sa.Column("status", sa.String(length=20), nullable=False, server_default="PENDING"),
        sa.Column(
            "service_area_id",
            sa.String(length=36),
            sa.ForeignKey("geofence_zones.id", ondelete="RESTRICT"),
            nullable=True,
        ),
        sa.Column("admin_response", sa.Text(), nullable=True),
        sa.Column(
            "reviewed_by_user_id",
            sa.String(length=36),
            sa.ForeignKey("users.id", ondelete="SET NULL"),
            nullable=True,
        ),
        sa.Column("reviewed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
    )
    op.create_index(
        "ix_driver_service_area_requests_driver_id",
        "driver_service_area_requests",
        ["driver_id"],
    )
    op.create_index(
        "ix_driver_service_area_requests_status",
        "driver_service_area_requests",
        ["status"],
    )
    op.create_index(
        "ix_driver_service_area_requests_service_area_id",
        "driver_service_area_requests",
        ["service_area_id"],
    )
    op.create_index(
        "ix_driver_area_request_driver_status",
        "driver_service_area_requests",
        ["driver_id", "status"],
    )
    op.create_index(
        "ix_driver_area_request_status_created",
        "driver_service_area_requests",
        ["status", "created_at"],
    )


def downgrade() -> None:
    op.drop_index("ix_driver_area_request_status_created", table_name="driver_service_area_requests")
    op.drop_index("ix_driver_area_request_driver_status", table_name="driver_service_area_requests")
    op.drop_index("ix_driver_service_area_requests_service_area_id", table_name="driver_service_area_requests")
    op.drop_index("ix_driver_service_area_requests_status", table_name="driver_service_area_requests")
    op.drop_index("ix_driver_service_area_requests_driver_id", table_name="driver_service_area_requests")
    op.drop_table("driver_service_area_requests")
