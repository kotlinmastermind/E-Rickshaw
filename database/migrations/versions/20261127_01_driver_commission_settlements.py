"""add immutable driver commission settlements tied to payout requests

Revision ID: 20261127_01
Revises: 20261126_01
Create Date: 2026-11-27
"""
from alembic import op
import sqlalchemy as sa

revision = "20261127_01"
down_revision = "20261126_01"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.drop_index("ix_driver_ledger_unique_category_ref", table_name="driver_ledger_entries")
    op.create_index(
        "ix_driver_ledger_unique_category_ref",
        "driver_ledger_entries",
        ["driver_id", "category", "reference_id"],
        unique=True,
        postgresql_where=sa.text("category IN ('TRIP_EARNING', 'PAYOUT_HOLD', 'PAYOUT_REVERSAL', 'COMMISSION', 'COMMISSION_SETTLEMENT') AND reference_id IS NOT NULL"),
        sqlite_where=sa.text("category IN ('TRIP_EARNING', 'PAYOUT_HOLD', 'PAYOUT_REVERSAL', 'COMMISSION', 'COMMISSION_SETTLEMENT') AND reference_id IS NOT NULL"),
    )
    op.create_table(
        "driver_commission_settlements",
        sa.Column("id", sa.String(length=36), nullable=False),
        sa.Column("driver_id", sa.String(length=36), nullable=False),
        sa.Column("payout_request_id", sa.String(length=36), nullable=False),
        sa.Column("amount_cents", sa.Integer(), nullable=False),
        sa.Column("currency", sa.String(length=10), server_default="INR", nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.ForeignKeyConstraint(["driver_id"], ["driver_profiles.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["payout_request_id"], ["driver_payout_requests.id"], ondelete="RESTRICT"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("payout_request_id", name="uq_driver_commission_settlements_payout_request_id"),
    )
    op.create_index("ix_driver_commission_settlements_driver_id", "driver_commission_settlements", ["driver_id"])


def downgrade() -> None:
    op.drop_index("ix_driver_ledger_unique_category_ref", table_name="driver_ledger_entries")
    op.create_index(
        "ix_driver_ledger_unique_category_ref",
        "driver_ledger_entries",
        ["driver_id", "category", "reference_id"],
        unique=True,
        postgresql_where=sa.text("category IN ('TRIP_EARNING', 'PAYOUT_HOLD', 'PAYOUT_REVERSAL', 'COMMISSION') AND reference_id IS NOT NULL"),
        sqlite_where=sa.text("category IN ('TRIP_EARNING', 'PAYOUT_HOLD', 'PAYOUT_REVERSAL', 'COMMISSION') AND reference_id IS NOT NULL"),
    )
    op.drop_index("ix_driver_commission_settlements_driver_id", table_name="driver_commission_settlements")
    op.drop_table("driver_commission_settlements")