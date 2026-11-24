"""payment purpose polymorphism and webhook inbox idempotency

Revision ID: 20261124_01
Revises: 
Create Date: 2026-11-24 14:40:00.000000

"""
from alembic import op
import sqlalchemy as sa

revision = '20261124_01'
down_revision = None
branch_labels = None
depends_on = None

def upgrade() -> None:
    # 1. Relax payments.ride_id from NOT NULL to NULL
    op.alter_column('payments', 'ride_id',
               existing_type=sa.String(length=36),
               nullable=True)
    
    # 2. Add user_id and purpose to payments
    op.add_column('payments', sa.Column('user_id', sa.String(length=36), nullable=True))
    op.add_column('payments', sa.Column('purpose', sa.String(length=32), server_default='RIDE_FARE', nullable=False))
    
    op.create_index('ix_payments_user_id', 'payments', ['user_id'], unique=False)
    op.create_index('ix_payments_purpose', 'payments', ['purpose'], unique=False)
    op.create_foreign_key('fk_payments_user_id', 'payments', 'users', ['user_id'], ['id'])
    
    # Ensure provider_order_id is indexed uniquely
    op.create_index('ix_payments_provider_order_id_unique', 'payments', ['provider_order_id'], unique=True)

    # 3. Add unique constraint to payment_webhook_inbox.event_digest
    op.alter_column('payment_webhook_inbox', 'event_digest',
               existing_type=sa.String(length=64),
               nullable=False)
    op.create_unique_constraint('uq_payment_webhook_inbox_event_digest', 'payment_webhook_inbox', ['event_digest'])

def downgrade() -> None:
    op.drop_constraint('uq_payment_webhook_inbox_event_digest', 'payment_webhook_inbox', type_='unique')
    op.drop_index('ix_payments_provider_order_id_unique', table_name='payments')
    op.drop_constraint('fk_payments_user_id', 'payments', type_='foreignkey')
    op.drop_index('ix_payments_purpose', table_name='payments')
    op.drop_index('ix_payments_user_id', table_name='payments')
    op.drop_column('payments', 'purpose')
    op.drop_column('payments', 'user_id')
    op.alter_column('payments', 'ride_id',
               existing_type=sa.String(length=36),
               nullable=False)