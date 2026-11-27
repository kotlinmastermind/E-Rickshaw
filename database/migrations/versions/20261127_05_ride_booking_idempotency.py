"""Persist ride booking idempotency keys on the authoritative ride record.

Revision ID: 20261127_05
Revises: 20261127_04
Create Date: 2026-11-27
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy import inspect

revision = "20261127_05"
down_revision = "20261127_04"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    inspector = inspect(bind)
    if "rides" not in inspector.get_table_names():
        return

    columns = {column["name"] for column in inspector.get_columns("rides")}
    constraints = {item["name"] for item in inspector.get_unique_constraints("rides")}
    with op.batch_alter_table("rides") as batch:
        if "booking_idempotency_key" not in columns:
            batch.add_column(sa.Column("booking_idempotency_key", sa.String(length=128), nullable=True))
        if "booking_request_hash" not in columns:
            batch.add_column(sa.Column("booking_request_hash", sa.String(length=64), nullable=True))
        if "uq_rides_customer_booking_idempotency" not in constraints:
            batch.create_unique_constraint(
                "uq_rides_customer_booking_idempotency",
                ["customer_id", "booking_idempotency_key"],
            )


def downgrade() -> None:
    bind = op.get_bind()
    inspector = inspect(bind)
    if "rides" not in inspector.get_table_names():
        return

    columns = {column["name"] for column in inspector.get_columns("rides")}
    constraints = {item["name"] for item in inspector.get_unique_constraints("rides")}
    with op.batch_alter_table("rides") as batch:
        if "uq_rides_customer_booking_idempotency" in constraints:
            batch.drop_constraint("uq_rides_customer_booking_idempotency", type_="unique")
        if "booking_request_hash" in columns:
            batch.drop_column("booking_request_hash")
        if "booking_idempotency_key" in columns:
            batch.drop_column("booking_idempotency_key")
