"""refund liability and wallet debt ledger tracking

Revision ID: 20261124_02
Revises: 20261124_01
Create Date: 2026-11-24 19:50:00.000000

"""
from alembic import op
import sqlalchemy as sa

revision = '20261124_02'
down_revision = '20261124_01'
branch_labels = None
depends_on = None

def upgrade() -> None:
    conn = op.get_bind()
    inspector = sa.inspect(conn)
    existing_tables = set(inspector.get_table_names())
    
    # 1. Add debt_cents to wallets
    wallet_cols = {c['name'] for c in inspector.get_columns('wallets')}
    if 'debt_cents' not in wallet_cols:
        with op.batch_alter_table('wallets', schema=None) as batch_op:
            batch_op.add_column(sa.Column('debt_cents', sa.Integer(), server_default='0', nullable=False))

    # 2. Create refund_liabilities table
    if 'refund_liabilities' not in existing_tables:
        op.create_table(
            'refund_liabilities',
            sa.Column('id', sa.String(length=36), primary_key=True),
            sa.Column('payment_id', sa.String(length=36), sa.ForeignKey('payments.id'), nullable=False),
            sa.Column('user_id', sa.String(length=36), sa.ForeignKey('users.id'), nullable=False),
            sa.Column('gateway_refund_id', sa.String(length=128), nullable=False),
            sa.Column('refund_amount_cents', sa.Integer(), nullable=False),
            sa.Column('debited_from_wallet_cents', sa.Integer(), server_default='0', nullable=False),
            sa.Column('outstanding_recovery_cents', sa.Integer(), server_default='0', nullable=False),
            sa.Column('status', sa.String(length=30), server_default='ACTIVE', nullable=False),
            sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
            sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False)
        )
        with op.batch_alter_table('refund_liabilities', schema=None) as batch_op:
            batch_op.create_index('ix_refund_liabilities_payment_id', ['payment_id'], unique=False)
            batch_op.create_index('ix_refund_liabilities_user_id', ['user_id'], unique=False)
            batch_op.create_index('ix_refund_liabilities_gateway_refund_id', ['gateway_refund_id'], unique=True)

def downgrade() -> None:
    conn = op.get_bind()
    inspector = sa.inspect(conn)
    existing_tables = set(inspector.get_table_names())

    if 'refund_liabilities' in existing_tables:
        with op.batch_alter_table('refund_liabilities', schema=None) as batch_op:
            batch_op.drop_index('ix_refund_liabilities_gateway_refund_id')
            batch_op.drop_index('ix_refund_liabilities_user_id')
            batch_op.drop_index('ix_refund_liabilities_payment_id')
        op.drop_table('refund_liabilities')

    wallet_cols = {c['name'] for c in inspector.get_columns('wallets')}
    if 'debt_cents' in wallet_cols:
        with op.batch_alter_table('wallets', schema=None) as batch_op:
            batch_op.drop_column('debt_cents')
