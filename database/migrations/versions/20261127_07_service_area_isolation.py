"""Persist operational service areas and driver assignments.

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


def _inside(lat, lng, polygon):
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


def _ensure_area_reference(table_name):
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    columns = {column["name"] for column in inspector.get_columns(table_name)}
    foreign_keys = inspector.get_foreign_keys(table_name)
    indexes = {index["name"] for index in inspector.get_indexes(table_name)}
    has_fk = any(
        fk.get("constrained_columns") == ["service_area_id"]
        and fk.get("referred_table") == "geofence_zones"
        for fk in foreign_keys
    )
    index_name = f"ix_{table_name}_service_area_id"
    with op.batch_alter_table(table_name) as batch_op:
        if "service_area_id" not in columns:
            batch_op.add_column(sa.Column("service_area_id", sa.String(36), nullable=True))
        if not has_fk:
            batch_op.create_foreign_key(
                f"fk_{table_name}_service_area_id_geofence_zones",
                "geofence_zones",
                ["service_area_id"],
                ["id"],
                ondelete="RESTRICT",
            )
        if index_name not in indexes:
            batch_op.create_index(index_name, ["service_area_id"])


def _drop_area_reference(table_name):
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    columns = {column["name"] for column in inspector.get_columns(table_name)}
    foreign_keys = inspector.get_foreign_keys(table_name)
    indexes = {index["name"] for index in inspector.get_indexes(table_name)}
    fk_name = next((
        fk.get("name") for fk in foreign_keys
        if fk.get("constrained_columns") == ["service_area_id"]
        and fk.get("referred_table") == "geofence_zones"
        and fk.get("name")
    ), None)
    index_name = f"ix_{table_name}_service_area_id"
    if "service_area_id" not in columns:
        return
    with op.batch_alter_table(table_name) as batch_op:
        if index_name in indexes:
            batch_op.drop_index(index_name)
        if fk_name:
            batch_op.drop_constraint(fk_name, type_="foreignkey")
        batch_op.drop_column("service_area_id")


def upgrade() -> None:
    bind = op.get_bind()
    tables = set(sa.inspect(bind).get_table_names())

    if "rides" in tables and "geofence_zones" in tables:
        _ensure_area_reference("rides")
        zones = bind.execute(sa.text(
            "SELECT id, coordinates_json FROM geofence_zones "
            "WHERE is_active = TRUE AND zone_type = 'SERVICE_AREA'"
        )).mappings().all()
        pending_rides = bind.execute(sa.text(
            "SELECT id, pickup_latitude, pickup_longitude, destination_latitude, destination_longitude "
            "FROM rides WHERE service_area_id IS NULL AND status = 'REQUESTED'"
        )).mappings().all()
        for ride in pending_rides:
            matching_ids = []
            for zone in zones:
                try:
                    raw = json.loads(zone["coordinates_json"])
                    polygon = [(float(point["lat"]), float(point["lng"])) for point in raw]
                    if (
                        len(polygon) >= 3
                        and _inside(ride["pickup_latitude"], ride["pickup_longitude"], polygon)
                        and _inside(ride["destination_latitude"], ride["destination_longitude"], polygon)
                    ):
                        matching_ids.append(zone["id"])
                except (TypeError, ValueError, KeyError, IndexError, json.JSONDecodeError):
                    continue
            if len(matching_ids) == 1:
                bind.execute(
                    sa.text("UPDATE rides SET service_area_id = :area_id WHERE id = :ride_id"),
                    {"area_id": matching_ids[0], "ride_id": ride["id"]},
                )

    if "pooled_trips" in tables and "geofence_zones" in tables:
        _ensure_area_reference("pooled_trips")
        if "pooled_bookings" in tables and "rides" in tables:
            rows = bind.execute(sa.text(
                "SELECT pb.pooled_trip_id, MIN(r.service_area_id) AS area_id "
                "FROM pooled_bookings pb JOIN rides r ON r.id = pb.ride_id "
                "WHERE r.service_area_id IS NOT NULL "
                "GROUP BY pb.pooled_trip_id "
                "HAVING COUNT(DISTINCT r.service_area_id) = 1"
            )).mappings().all()
            for row in rows:
                bind.execute(
                    sa.text(
                        "UPDATE pooled_trips SET service_area_id = :area_id "
                        "WHERE id = :trip_id AND service_area_id IS NULL"
                    ),
                    {"area_id": row["area_id"], "trip_id": row["pooled_trip_id"]},
                )

    tables = set(sa.inspect(bind).get_table_names())
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
    inspector = sa.inspect(bind)
    existing_indexes = {index["name"] for index in inspector.get_indexes("driver_service_area_assignments")}
    if "ix_driver_service_area_assignments_driver_id" not in existing_indexes:
        op.create_index("ix_driver_service_area_assignments_driver_id", "driver_service_area_assignments", ["driver_id"])
    if "ix_driver_service_area_assignments_service_area_id" not in existing_indexes:
        op.create_index("ix_driver_service_area_assignments_service_area_id", "driver_service_area_assignments", ["service_area_id"])
    if "ix_driver_service_area_active" not in existing_indexes:
        op.create_index("ix_driver_service_area_active", "driver_service_area_assignments", ["service_area_id", "is_active", "driver_id"])


def downgrade() -> None:
    bind = op.get_bind()
    tables = set(sa.inspect(bind).get_table_names())
    if "driver_service_area_assignments" in tables:
        op.drop_table("driver_service_area_assignments")
    if "pooled_trips" in tables:
        _drop_area_reference("pooled_trips")
    if "rides" in tables:
        _drop_area_reference("rides")
