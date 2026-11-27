"""Allow unrated drivers and vehicles without verified credential metadata.

Revision ID: 20261127_06
Revises: 20261127_05
Create Date: 2026-11-27
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy import inspect

revision = "20261127_06"
down_revision = "20261127_05"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    inspector = inspect(bind)
    tables = set(inspector.get_table_names())

    if "driver_profiles" in tables:
        columns = {column["name"]: column for column in inspector.get_columns("driver_profiles")}
        if "rating_average" in columns and "rating_count" in columns:
            # The old ORM default was 5.0 even when no customer had rated the driver.
            bind.execute(sa.text(
                "UPDATE driver_profiles "
                "SET rating_average = NULL "
                "WHERE COALESCE(rating_count, 0) = 0"
            ))
            if not columns["rating_average"]["nullable"]:
                op.alter_column(
                    "driver_profiles",
                    "rating_average",
                    existing_type=sa.Float(),
                    existing_nullable=False,
                    nullable=True,
                )

    if "vehicles" in tables:
        columns = {column["name"] for column in inspector.get_columns("vehicles")}
        # Clear only the known ORM seed defaults; preserve all other stored credentials.
        for column, sentinel in (
            ("rc_number", "MH-01-2024-RC98214"),
            ("permit_number", "AITP-LUX-2026-08492"),
            ("insurance_policy_number", "NIC-COMM-LUX-849201"),
        ):
            if column in columns:
                bind.execute(
                    sa.text(f"UPDATE vehicles SET {column} = NULL WHERE {column} = :sentinel"),
                    {"sentinel": sentinel},
                )


def downgrade() -> None:
    bind = op.get_bind()
    inspector = inspect(bind)
    tables = set(inspector.get_table_names())
    if "driver_profiles" not in tables:
        return

    columns = {column["name"]: column for column in inspector.get_columns("driver_profiles")}
    if "rating_average" not in columns or columns["rating_average"]["nullable"]:
        # Never recreate fabricated 5-star values merely to make a downgrade succeed.
        null_count = bind.execute(
            sa.text("SELECT count(*) FROM driver_profiles WHERE rating_average IS NULL")
        ).scalar_one()
        if null_count:
            raise RuntimeError(
                "Cannot downgrade truthful driver ratings while unrated profiles contain NULL. "
                "A manual, evidence-backed rating backfill is required."
            )
        op.alter_column(
            "driver_profiles",
            "rating_average",
            existing_type=sa.Float(),
            existing_nullable=True,
            nullable=False,
        )
