"""Persist ride service areas and driver operational assignments.

Revision ID: 20261127_07
Revises: 20261127_06
"""
from alembic import op
import sqlalchemy as sa

revision = "20261127_07"
down_revision = "20261127_06"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    tables = set(inspector.get_table_names())

    if "rides" in tables:
        columns = {column["name"] for column in inspector.get_columns("rides")}
        if "service_area_id" not in columns:
            with op.batch_alter_table("rides") as batch_op:
                batch_op.add_column(sa.Column("service_area_id", sa.String(36), nullable=True))
                batch_op.create_foreign_key(
                    "fk_rides_service_area_id_geofence_zones",
                    "geofence_zones",
                    ["service_area_id"],
                    ["id"],
                    ondelete="RESTRICT",
                )
                batch_op.create_index("ix_rides_service_area_id", ["service_area_id"])

    if "driver_service_area_assignments" not in tables:
        op.create_table(
            "driver_service_area_assignments",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("driver_id", sa.String(36), sa.ForeignKey("driver_profiles.id", ondelete="CASCADE"), nullable=False),
            sa.Column("service_area_id", sa.String(36), sa.ForeignKey("geofence_zones.id", ondelete="RESTRICT"), nullable=False),
            sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.true()),
            sa.Column("assigned_by_user_id", sa.String(36), sa.ForeignKey("users.id", ondelete="SET NULL"), nullable=True),
            sa.Column("assigned_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
            sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
            sa.UniqueConstraint("driver_id", "service_area_id", name="uq_driver_service_area_assignment"),
        )
        op.create_index("ix_driver_service_area_assignments_driver_id", "driver_service_area_assignments", ["driver_id"])
        op.create_index("ix_driver_service_area_assignments_service_area_id", "driver_service_area_assignments", ["service_area_id"])
        op.create_index("ix_driver_service_area_active", "driver_service_area_assignments", ["service_area_id", "is_active", "driver_id"])


def downgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    tables = set(inspector.get_table_names())
    if "driver_service_area_assignments" in tables:
        op.drop_index("ix_driver_service_area_active", table_name="driver_service_area_assignments")
        op.drop_table("driver_service_area_assignments")
    if "rides" in tables:
        columns = {column["name"] for column in inspector.get_columns("rides")}
        if "service_area_id" in columns:
            with op.batch_alter_table("rides") as batch_op:
                batch_op.drop_index("ix_rides_service_area_id")
                batch_op.drop_constraint("fk_rides_service_area_id_geofence_zones", type_="foreignkey")
                batch_op.drop_column("service_area_id")
