"""Encrypt driver payout destination details and invalidate unverifiable legacy records.

Revision ID: 20261127_03
Revises: 20261127_02
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy import inspect

revision = "20261127_03"
down_revision = "20261127_02"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    if "driver_payout_accounts" not in inspect(bind).get_table_names():
        return
    columns = {c["name"] for c in inspect(bind).get_columns("driver_payout_accounts")}
    with op.batch_alter_table("driver_payout_accounts") as batch:
        if "account_number_encrypted" not in columns:
            batch.add_column(sa.Column("account_number_encrypted", sa.Text(), nullable=True))
        if "upi_vpa_encrypted" not in columns:
            batch.add_column(sa.Column("upi_vpa_encrypted", sa.Text(), nullable=True))

    # Existing rows never stored encrypted bank account numbers. Force re-registration;
    # do not leave legacy VERIFIED/PRIMARY destinations eligible for payouts.
    op.execute(sa.text(
        "UPDATE driver_payout_accounts "
        "SET verification_status = 'PENDING_VERIFICATION', is_primary = FALSE, "
        "verified_by = NULL, verified_at = NULL, verification_method = NULL, "
        "penny_drop_reference = NULL "
        "WHERE account_number_encrypted IS NULL AND upi_vpa_encrypted IS NULL"
    ))
    # Older releases stored UPI IDs in both columns in plaintext. Retain only a masked suffix.
    op.execute(sa.text(
        "UPDATE driver_payout_accounts "
        "SET account_number_mask = CASE WHEN length(upi_vpa) > 4 "
        "THEN repeat('*', greatest(4, length(upi_vpa) - 4)) || right(upi_vpa, 4) "
        "ELSE '****' END, upi_vpa = NULL "
        "WHERE account_type = 'UPI' AND upi_vpa IS NOT NULL"
    ))


def downgrade() -> None:
    bind = op.get_bind()
    if "driver_payout_accounts" not in inspect(bind).get_table_names():
        return
    columns = {c["name"] for c in inspect(bind).get_columns("driver_payout_accounts")}
    with op.batch_alter_table("driver_payout_accounts") as batch:
        if "upi_vpa_encrypted" in columns:
            batch.drop_column("upi_vpa_encrypted")
        if "account_number_encrypted" in columns:
            batch.drop_column("account_number_encrypted")
