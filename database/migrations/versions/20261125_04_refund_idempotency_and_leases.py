"""widen payment_refunds.idempotency_key to 128 chars, add refund reservation lease columns, and index

Revision ID: 20261125_04
Revises: 20261125_03
Create Date: 2026-11-25 16:30:00.000000

"""
from alembic import op
import sqlalchemy as sa


revision = '20261125_04'
down_revision = '20261125_03'
branch_labels = None
depends_on = None


def upgrade() -> None:
    conn = op.get_bind()
    inspector = sa.inspect(conn)
    existing_tables = set(inspector.get_table_names())

    if 'payments' in existing_tables:
        pay_cols = {c['name'] for c in inspector.get_columns('payments')}
        with op.batch_alter_table('payments', schema=None) as batch_op:
            if 'refund_lease_owner' not in pay_cols:
                batch_op.add_column(sa.Column('refund_lease_owner', sa.String(length=64), nullable=True))
            if 'refund_lease_expires_at' not in pay_cols:
                batch_op.add_column(sa.Column('refund_lease_expires_at', sa.DateTime(timezone=True), nullable=True))

    if 'payment_refunds' in existing_tables:
        ref_cols = {c['name'] for c in inspector.get_columns('payment_refunds')}
        ref_indexes = {ix['name'] for ix in inspector.get_indexes('payment_refunds')}
        with op.batch_alter_table('payment_refunds', schema=None) as batch_op:
            if 'idempotency_key' in ref_cols:
                batch_op.alter_column(
                    'idempotency_key',
                    existing_type=sa.String(length=96),
                    type_=sa.String(length=128),
                    existing_nullable=True,
                )
            if 'ix_payment_refunds_payment_status' not in ref_indexes:
                batch_op.create_index('ix_payment_refunds_payment_status', ['payment_id', 'status'], unique=False)


def downgrade() -> None:
    """
    Financial safety note: payment_refunds.idempotency_key is preserved at 128 chars on downgrade
    so existing production idempotency records >96 chars are never truncated or deleted.
    """
    conn = op.get_bind()
    inspector = sa.inspect(conn)
    existing_tables = set(inspector.get_table_names())

    if 'payment_refunds' in existing_tables:
        ref_indexes = {ix['name'] for ix in inspector.get_indexes('payment_refunds')}
        with op.batch_alter_table('payment_refunds', schema=None) as batch_op:
            if 'ix_payment_refunds_payment_status' in ref_indexes:
                batch_op.drop_index('ix_payment_refunds_payment_status')

    if 'payments' in existing_tables:
        pay_cols = {c['name'] for c in inspector.get_columns('payments')}
        with op.batch_alter_table('payments', schema=None) as batch_op:
            if 'refund_lease_expires_at' in pay_cols:
                batch_op.drop_column('refund_lease_expires_at')
            if 'refund_lease_owner' in pay_cols:
                batch_op.drop_column('refund_lease_owner')
