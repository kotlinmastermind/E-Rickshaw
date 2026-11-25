"""order creation concurrency leases, partial unique index, and widened idempotency keys

Revision ID: 20261125_03
Revises: 20261125_02
Create Date: 2026-11-25 14:42:00.000000

"""
from alembic import op
import sqlalchemy as sa


revision = '20261125_03'
down_revision = '20261125_02'
branch_labels = None
depends_on = None


def upgrade() -> None:
    conn = op.get_bind()
    inspector = sa.inspect(conn)
    existing_tables = set(inspector.get_table_names())

    if 'payments' in existing_tables:
        pay_cols = {c['name'] for c in inspector.get_columns('payments')}
        pay_indexes = {ix['name'] for ix in inspector.get_indexes('payments')}
        with op.batch_alter_table('payments', schema=None) as batch_op:
            if 'order_creation_lease_owner' not in pay_cols:
                batch_op.add_column(sa.Column('order_creation_lease_owner', sa.String(length=64), nullable=True))
            if 'order_creation_lease_expires_at' not in pay_cols:
                batch_op.add_column(sa.Column('order_creation_lease_expires_at', sa.DateTime(timezone=True), nullable=True))
            if 'order_creation_receipt' not in pay_cols:
                batch_op.add_column(sa.Column('order_creation_receipt', sa.String(length=64), nullable=True))
            if 'idempotency_key' in pay_cols:
                batch_op.alter_column(
                    'idempotency_key',
                    existing_type=sa.String(length=64),
                    type_=sa.String(length=128),
                    existing_nullable=False,
                )
            if 'ix_payments_order_creation_receipt' not in pay_indexes:
                batch_op.create_index('ix_payments_order_creation_receipt', ['order_creation_receipt'], unique=False)

    if 'payment_order_attempts' in existing_tables:
        att_cols = {c['name'] for c in inspector.get_columns('payment_order_attempts')}
        att_indexes = {ix['name'] for ix in inspector.get_indexes('payment_order_attempts')}
        with op.batch_alter_table('payment_order_attempts', schema=None) as batch_op:
            if 'lease_owner' not in att_cols:
                batch_op.add_column(sa.Column('lease_owner', sa.String(length=64), nullable=True))
            if 'failure_reason' not in att_cols:
                batch_op.add_column(sa.Column('failure_reason', sa.String(length=500), nullable=True))
            if 'idempotency_key' in att_cols:
                batch_op.alter_column(
                    'idempotency_key',
                    existing_type=sa.String(length=96),
                    type_=sa.String(length=128),
                    existing_nullable=True,
                )
            if 'ix_payment_order_attempts_payment_status' not in att_indexes:
                batch_op.create_index('ix_payment_order_attempts_payment_status', ['payment_id', 'status'], unique=False)
        if 'uq_payment_order_attempts_one_creating' not in att_indexes:
            op.create_index(
                'uq_payment_order_attempts_one_creating',
                'payment_order_attempts',
                ['payment_id'],
                unique=True,
                postgresql_where=sa.text("status = 'CREATING'"),
                sqlite_where=sa.text("status = 'CREATING'"),
            )


def downgrade() -> None:
    conn = op.get_bind()
    inspector = sa.inspect(conn)
    existing_tables = set(inspector.get_table_names())

    if 'payment_order_attempts' in existing_tables:
        att_cols = {c['name'] for c in inspector.get_columns('payment_order_attempts')}
        att_indexes = {ix['name'] for ix in inspector.get_indexes('payment_order_attempts')}
        if 'uq_payment_order_attempts_one_creating' in att_indexes:
            op.drop_index('uq_payment_order_attempts_one_creating', table_name='payment_order_attempts')
        with op.batch_alter_table('payment_order_attempts', schema=None) as batch_op:
            if 'ix_payment_order_attempts_payment_status' in att_indexes:
                batch_op.drop_index('ix_payment_order_attempts_payment_status')
            if 'failure_reason' in att_cols:
                batch_op.drop_column('failure_reason')
            if 'lease_owner' in att_cols:
                batch_op.drop_column('lease_owner')

    if 'payments' in existing_tables:
        pay_cols = {c['name'] for c in inspector.get_columns('payments')}
        pay_indexes = {ix['name'] for ix in inspector.get_indexes('payments')}
        with op.batch_alter_table('payments', schema=None) as batch_op:
            if 'ix_payments_order_creation_receipt' in pay_indexes:
                batch_op.drop_index('ix_payments_order_creation_receipt')
            if 'order_creation_receipt' in pay_cols:
                batch_op.drop_column('order_creation_receipt')
            if 'order_creation_lease_expires_at' in pay_cols:
                batch_op.drop_column('order_creation_lease_expires_at')
            if 'order_creation_lease_owner' in pay_cols:
                batch_op.drop_column('order_creation_lease_owner')
