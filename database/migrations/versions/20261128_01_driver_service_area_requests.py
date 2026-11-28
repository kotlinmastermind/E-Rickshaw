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
    bind = op.get_bind()
    tables = set(sa.inspect(bind).get_table_names())

    # The application may have already created ORM metadata tables before
    # Alembic runs (the CI bootstrap does this). Make this migration safe for
    # both metadata-initialized and migration-only databases.
    if "driver_service_area_requests" not in tables:
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

    existing_indexes = {
        index["name"]
        for index in sa.inspect(bind).get_indexes("driver_service_area_requests")
    }
    index_specs = [
        ("ix_driver_service_area_requests_driver_id", ["driver_id"], {}),
        ("ix_driver_service_area_requests_status", ["status"], {}),
        ("ix_driver_service_area_requests_service_area_id", ["service_area_id"], {}),
        (
            "uq_driver_area_request_one_pending_per_driver",
            ["driver_id"],
            {
                "unique": True,
                "postgresql_where": sa.text("status = 'PENDING'"),
                "sqlite_where": sa.text("status = 'PENDING'"),
            },
        ),
        ("ix_driver_area_request_driver_status", ["driver_id", "status"], {}),
        ("ix_driver_area_request_status_created", ["status", "created_at"], {}),
    ]
    for name, columns, options in index_specs:
        if name not in existing_indexes:
            op.create_index(name, "driver_service_area_requests", columns, **options)


def downgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    if "driver_service_area_requests" not in set(inspector.get_table_names()):
        return
    existing_indexes = {index["name"] for index in inspector.get_indexes("driver_service_area_requests")}
    for name in (
        "ix_driver_area_request_status_created",
        "ix_driver_area_request_driver_status",
        "uq_driver_area_request_one_pending_per_driver",
        "ix_driver_service_area_requests_service_area_id",
        "ix_driver_service_area_requests_status",
        "ix_driver_service_area_requests_driver_id",
    ):
        if name in existing_indexes:
            op.drop_index(name, table_name="driver_service_area_requests")
    op.drop_table("driver_service_area_requests")
