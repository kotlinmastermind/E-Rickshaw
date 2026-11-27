"""Payout destination security snapshots and RESTRICT settlement history FK

Revision ID: 20261127_02
Revises: 20261127_01
Create Date: 2026-11-27 13:20:00.000000
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy import inspect


revision = "20261127_02"
down_revision = "20261127_01"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    inspector = inspect(bind)
    existing_tables = set(inspector.get_table_names())

    if "driver_payout_accounts" in existing_tables:
        with op.batch_alter_table("driver_payout_accounts") as batch_op:
            batch_op.alter_column(
                "ifsc_code",
                existing_type=sa.String(length=20),
                nullable=True,
            )
            batch_op.alter_column(
                "verification_status",
                existing_type=sa.String(length=30),
                server_default=sa.text("'PENDING_VERIFICATION'"),
            )

    if "driver_payout_requests" in existing_tables:
        cols = {c["name"] for c in inspector.get_columns("driver_payout_requests")}
        with op.batch_alter_table("driver_payout_requests") as batch_op:
            if "payout_account_id" not in cols:
                batch_op.add_column(sa.Column("payout_account_id", sa.String(length=36), nullable=True))
                batch_op.create_index("ix_driver_payout_requests_payout_account_id", ["payout_account_id"])
                batch_op.create_foreign_key(
                    "fk_driver_payout_requests_payout_account_id",
                    "driver_payout_accounts",
                    ["payout_account_id"],
                    ["id"],
                    ondelete="RESTRICT",
                )
            if "account_type_snapshot" not in cols:
                batch_op.add_column(sa.Column("account_type_snapshot", sa.String(length=20), nullable=True))
            if "bank_name_snapshot" not in cols:
                batch_op.add_column(sa.Column("bank_name_snapshot", sa.String(length=128), nullable=True))
            if "ifsc_code_snapshot" not in cols:
                batch_op.add_column(sa.Column("ifsc_code_snapshot", sa.String(length=20), nullable=True))
            if "beneficiary_name_snapshot" not in cols:
                batch_op.add_column(sa.Column("beneficiary_name_snapshot", sa.String(length=128), nullable=True))

    if "driver_commission_settlements" in existing_tables and bind.dialect.name == "postgresql":
        fks = inspector.get_foreign_keys("driver_commission_settlements")
        for fk in fks:
            if fk.get("constrained_columns") == ["driver_id"] and fk.get("name"):
                op.drop_constraint(fk["name"], "driver_commission_settlements", type_="foreignkey")
        op.create_foreign_key(
            "fk_driver_commission_settlements_driver_id_restrict",
            "driver_commission_settlements",
            "driver_profiles",
            ["driver_id"],
            ["id"],
            ondelete="RESTRICT",
        )


def downgrade() -> None:
    bind = op.get_bind()
    inspector = inspect(bind)
    existing_tables = set(inspector.get_table_names())

    if "driver_commission_settlements" in existing_tables and bind.dialect.name == "postgresql":
        op.drop_constraint(
            "fk_driver_commission_settlements_driver_id_restrict",
            "driver_commission_settlements",
            type_="foreignkey",
        )
        op.create_foreign_key(
            "driver_commission_settlements_driver_id_fkey",
            "driver_commission_settlements",
            "driver_profiles",
            ["driver_id"],
            ["id"],
            ondelete="CASCADE",
        )

    if "driver_payout_requests" in existing_tables:
        cols = {c["name"] for c in inspector.get_columns("driver_payout_requests")}
        with op.batch_alter_table("driver_payout_requests") as batch_op:
            for col in (
                "beneficiary_name_snapshot",
                "ifsc_code_snapshot",
                "bank_name_snapshot",
                "account_type_snapshot",
            ):
                if col in cols:
                    batch_op.drop_column(col)
            if "payout_account_id" in cols:
                batch_op.drop_constraint("fk_driver_payout_requests_payout_account_id", type_="foreignkey")
                batch_op.drop_index("ix_driver_payout_requests_payout_account_id")
                batch_op.drop_column("payout_account_id")
