"""razorpay refund idempotency, merchant account tracking, and order attempt history

Revision ID: 20261125_02
Revises: 20261125_01
Create Date: 2026-11-25 13:30:00.000000

"""
from alembic import op
import sqlalchemy as sa


revision = '20261125_02'
down_revision = '20261125_01'
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
            if 'merchant_account_id' not in pay_cols:
                batch_op.add_column(sa.Column('merchant_account_id', sa.String(length=64), nullable=True))
            if 'ix_payments_merchant_account_id' not in pay_indexes:
                batch_op.create_index('ix_payments_merchant_account_id', ['merchant_account_id'], unique=False)

    if 'payment_refunds' in existing_tables:
        ref_cols = {c['name'] for c in inspector.get_columns('payment_refunds')}
        ref_indexes = {ix['name'] for ix in inspector.get_indexes('payment_refunds')}
        with op.batch_alter_table('payment_refunds', schema=None) as batch_op:
            if 'idempotency_key' not in ref_cols:
                batch_op.add_column(sa.Column('idempotency_key', sa.String(length=96), nullable=True))
            if 'last_error' not in ref_cols:
                batch_op.add_column(sa.Column('last_error', sa.String(length=500), nullable=True))
            if 'settled_at' not in ref_cols:
                batch_op.add_column(sa.Column('settled_at', sa.DateTime(timezone=True), nullable=True))
            if 'ix_payment_refunds_idempotency_key' not in ref_indexes:
                batch_op.create_index('ix_payment_refunds_idempotency_key', ['idempotency_key'], unique=True)

    if 'payment_webhook_inbox' in existing_tables:
        inbox_cols = {c['name'] for c in inspector.get_columns('payment_webhook_inbox')}
        with op.batch_alter_table('payment_webhook_inbox', schema=None) as batch_op:
            if 'merchant_account_id' not in inbox_cols:
                batch_op.add_column(sa.Column('merchant_account_id', sa.String(length=64), nullable=True))

    if 'payment_order_attempts' not in existing_tables:
        op.create_table(
            'payment_order_attempts',
            sa.Column('id', sa.String(length=36), primary_key=True),
            sa.Column('payment_id', sa.String(length=36), sa.ForeignKey('payments.id'), nullable=False),
            sa.Column('provider_order_id', sa.String(length=128), nullable=True),
            sa.Column('receipt', sa.String(length=64), nullable=True),
            sa.Column('idempotency_key', sa.String(length=96), nullable=True),
            sa.Column('amount_cents', sa.Integer(), nullable=False),
            sa.Column('currency', sa.String(length=10), server_default='INR', nullable=False),
            sa.Column('merchant_account_id', sa.String(length=64), nullable=True),
            sa.Column('status', sa.String(length=32), server_default='CREATED', nullable=False),
            sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
            sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        )
        with op.batch_alter_table('payment_order_attempts', schema=None) as batch_op:
            batch_op.create_index('ix_payment_order_attempts_payment_id', ['payment_id'], unique=False)
            batch_op.create_index('ix_payment_order_attempts_provider_order_id', ['provider_order_id'], unique=True)
            batch_op.create_index('ix_payment_order_attempts_receipt', ['receipt'], unique=False)
            batch_op.create_index('ix_payment_order_attempts_idempotency_key', ['idempotency_key'], unique=False)


def downgrade() -> None:
    conn = op.get_bind()
    inspector = sa.inspect(conn)
    existing_tables = set(inspector.get_table_names())

    if 'payment_order_attempts' in existing_tables:
        op.drop_table('payment_order_attempts')

    if 'payment_webhook_inbox' in existing_tables:
        inbox_cols = {c['name'] for c in inspector.get_columns('payment_webhook_inbox')}
        if 'merchant_account_id' in inbox_cols:
            with op.batch_alter_table('payment_webhook_inbox', schema=None) as batch_op:
                batch_op.drop_column('merchant_account_id')

    if 'payment_refunds' in existing_tables:
        ref_cols = {c['name'] for c in inspector.get_columns('payment_refunds')}
        ref_indexes = {ix['name'] for ix in inspector.get_indexes('payment_refunds')}
        with op.batch_alter_table('payment_refunds', schema=None) as batch_op:
            if 'ix_payment_refunds_idempotency_key' in ref_indexes:
                batch_op.drop_index('ix_payment_refunds_idempotency_key')
            for col in ('settled_at', 'last_error', 'idempotency_key'):
                if col in ref_cols:
                    batch_op.drop_column(col)

    if 'payments' in existing_tables:
        pay_cols = {c['name'] for c in inspector.get_columns('payments')}
        pay_indexes = {ix['name'] for ix in inspector.get_indexes('payments')}
        with op.batch_alter_table('payments', schema=None) as batch_op:
            if 'ix_payments_merchant_account_id' in pay_indexes:
                batch_op.drop_index('ix_payments_merchant_account_id')
            if 'merchant_account_id' in pay_cols:
                batch_op.drop_column('merchant_account_id')
