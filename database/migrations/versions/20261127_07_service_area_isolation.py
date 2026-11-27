"""Persist ride service areas and driver operational assignments.

Revision ID: 20261127_07
Revises: 20261127_06
"""
from alembic import op
import json
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

        # Preserve dispatchability for existing requested rides only when their route
        # resolves unambiguously to one already-configured active service-area polygon.
        if "geofence_zones" in tables:
            zones = bind.execute(sa.text(
                "SELECT id, coordinates_json FROM geofence_zones "
                "WHERE is_active = TRUE AND zone_type = 'SERVICE_AREA'"
            )).mappings().all()
            pending_rides = bind.execute(sa.text(
                "SELECT id, pickup_latitude, pickup_longitude, destination_latitude, destination_longitude "
                "FROM rides WHERE service_area_id IS NULL AND status = 'REQUESTED'"
            )).mappings().all()

            def inside(lat, lng, polygon):
                result = False
                previous = polygon[-1]
                for current in polygon:
                    y1, x1 = previous
                    y2, x2 = current
                    if (x1 > lng) != (x2 > lng):
                        intersection = (y2 - y1) * (lng - x1) / (x2 - x1) + y1
                        if lat < intersection:
                            result = not result
                    previous = current
                return result

            for ride in pending_rides:
                matching_ids = []
                for zone in zones:
                    try:
                        raw = json.loads(zone["coordinates_json"])
                        polygon = [(float(point["lat"]), float(point["lng"])) for point in raw]
                        if len(polygon) >= 3 and inside(ride["pickup_latitude"], ride["pickup_longitude"], polygon) and inside(ride["destination_latitude"], ride["destination_longitude"], polygon):
                            matching_ids.append(zone["id"])
                    except (TypeError, ValueError, KeyError, json.JSONDecodeError):
                        continue
                if len(matching_ids) == 1:
                    bind.execute(
                        sa.text("UPDATE rides SET service_area_id = :area_id WHERE id = :ride_id"),
                        {"area_id": matching_ids[0], "ride_id": ride["id"]},
                    )

    if "pooled_trips" in tables:
        pooled_columns = {column["name"] for column in inspector.get_columns("pooled_trips")}
        if "service_area_id" not in pooled_columns:
            with op.batch_alter_table("pooled_trips") as batch_op:
                batch_op.add_column(sa.Column("service_area_id", sa.String(36), nullable=True))
                batch_op.create_foreign_key(
                    "fk_pooled_trips_service_area_id_geofence_zones",
                    "geofence_zones",
                    ["service_area_id"],
                    ["id"],
                    ondelete="RESTRICT",
                )
                batch_op.create_index("ix_pooled_trips_service_area_id", ["service_area_id"])
        if "pooled_bookings" in tables:
            rows = bind.execute(sa.text(
                "SELECT pb.pooled_trip_id, MIN(r.service_area_id) AS area_id "
                "FROM pooled_bookings pb JOIN rides r ON r.id = pb.ride_id "
                "WHERE r.service_area_id IS NOT NULL "
                "GROUP BY pb.pooled_trip_id "
                "HAVING COUNT(DISTINCT r.service_area_id) = 1"
            )).mappings().all()
            for row in rows:
                bind.execute(
                    sa.text("UPDATE pooled_trips SET service_area_id = :area_id WHERE id = :trip_id AND service_area_id IS NULL"),
                    {"area_id": row["area_id"], "trip_id": row["pooled_trip_id"]},
                )

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
