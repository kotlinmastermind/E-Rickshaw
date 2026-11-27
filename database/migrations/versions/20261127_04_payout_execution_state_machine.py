"""Add payout execution mode, provider state machine, and manual transfer confirmation columns

Revision ID: 20261127_04
Revises: 20261127_03
Create Date: 2026-11-27 15:00:00.000000
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy import inspect


revision = "20261127_04"
down_revision = "20261127_03"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    inspector = inspect(bind)
    if "driver_payout_requests" not in inspector.get_table_names():
        return

    cols = {c["name"] for c in inspector.get_columns("driver_payout_requests")}
    uqs = {u["name"] for u in inspector.get_unique_constraints("driver_payout_requests")}

    with op.batch_alter_table("driver_payout_requests") as batch_op:
        if "execution_mode" not in cols:
            batch_op.add_column(sa.Column("execution_mode", sa.String(length=30), nullable=True))
        if "provider_name" not in cols:
            batch_op.add_column(sa.Column("provider_name", sa.String(length=32), nullable=True))
        if "provider_payout_id" not in cols:
            batch_op.add_column(sa.Column("provider_payout_id", sa.String(length=128), nullable=True))
        if "provider_idempotency_key" not in cols:
            batch_op.add_column(sa.Column("provider_idempotency_key", sa.String(length=128), nullable=True))
        if "provider_metadata_json" not in cols:
            batch_op.add_column(sa.Column("provider_metadata_json", sa.Text(), nullable=True))
        if "manual_transfer_initiated_by" not in cols:
            batch_op.add_column(sa.Column("manual_transfer_initiated_by", sa.String(length=36), nullable=True))
        if "manual_transfer_initiated_at" not in cols:
            batch_op.add_column(sa.Column("manual_transfer_initiated_at", sa.DateTime(timezone=True), nullable=True))
        if "confirmed_by" not in cols:
            batch_op.add_column(sa.Column("confirmed_by", sa.String(length=36), nullable=True))
        if "confirmed_at" not in cols:
            batch_op.add_column(sa.Column("confirmed_at", sa.DateTime(timezone=True), nullable=True))
        if "paid_at" not in cols:
            batch_op.add_column(sa.Column("paid_at", sa.DateTime(timezone=True), nullable=True))
        if "failed_at" not in cols:
            batch_op.add_column(sa.Column("failed_at", sa.DateTime(timezone=True), nullable=True))
        if "failure_reason" not in cols:
            batch_op.add_column(sa.Column("failure_reason", sa.Text(), nullable=True))
        if "retry_count" not in cols:
            batch_op.add_column(sa.Column("retry_count", sa.Integer(), nullable=False, server_default=sa.text("0")))
        if "uq_driver_payout_requests_provider_payout_id" not in uqs:
            batch_op.create_unique_constraint(
                "uq_driver_payout_requests_provider_payout_id",
                ["provider_payout_id"],
            )

    # Reconcile legacy rows safely without altering historical financial outcomes
    op.execute(
        sa.text(
            "UPDATE driver_payout_requests "
            "SET execution_mode = 'MANUAL_TRANSFER' "
            "WHERE execution_mode IS NULL AND status IN ('APPROVED', 'PAID')"
        )
    )
    # Do not synthesize historical payment timestamps from approval/update timestamps.
    # Legacy PAID rows without provider/manual confirmation evidence intentionally keep paid_at NULL.


def downgrade() -> None:
    bind = op.get_bind()
    inspector = inspect(bind)
    if "driver_payout_requests" not in inspector.get_table_names():
        return

    cols = {c["name"] for c in inspector.get_columns("driver_payout_requests")}
    uqs = {u["name"] for u in inspector.get_unique_constraints("driver_payout_requests")}

    with op.batch_alter_table("driver_payout_requests") as batch_op:
        if "uq_driver_payout_requests_provider_payout_id" in uqs:
            batch_op.drop_constraint("uq_driver_payout_requests_provider_payout_id", type_="unique")
        for col in (
            "retry_count",
            "failure_reason",
            "failed_at",
            "paid_at",
            "confirmed_at",
            "confirmed_by",
            "manual_transfer_initiated_at",
            "manual_transfer_initiated_by",
            "provider_metadata_json",
            "provider_idempotency_key",
            "provider_payout_id",
            "provider_name",
            "execution_mode",
        ):
            if col in cols:
                batch_op.drop_column(col)
