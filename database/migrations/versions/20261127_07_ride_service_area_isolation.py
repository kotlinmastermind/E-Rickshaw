"""Persist the Admin-resolved service area for every new ride.

Revision ID: 20261127_07
Revises: 20261127_06
Create Date: 2026-11-27
"""
from alembic import op
import sqlalchemy as sa

revision = "20261127_07"
down_revision = "20261127_06"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("rides") as batch_op:
        batch_op.add_column(sa.Column("service_area_id", sa.String(length=36), nullable=True))
        batch_op.create_foreign_key(
            "fk_rides_service_area_id_geofence_zones",
            "geofence_zones",
            ["service_area_id"],
            ["id"],
            ondelete="RESTRICT",
        )
        batch_op.create_index("ix_rides_service_area_id", ["service_area_id"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("rides") as batch_op:
        batch_op.drop_index("ix_rides_service_area_id")
        batch_op.drop_constraint("fk_rides_service_area_id_geofence_zones", type_="foreignkey")
        batch_op.drop_column("service_area_id")
