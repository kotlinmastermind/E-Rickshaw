"""payment purpose polymorphism and webhook inbox idempotency

Revision ID: 20261124_01
Revises: 0010_gift_cards_suite
Create Date: 2026-11-24 14:40:00.000000

"""
from alembic import op
import sqlalchemy as sa

revision = '20261124_01'
down_revision = '0010_gift_cards_suite'
branch_labels = None
depends_on = None

def upgrade() -> None:
    conn = op.get_bind()
    inspector = sa.inspect(conn)
    existing_cols = {c['name'] for c in inspector.get_columns('payments')}
    
    # 1. Relax payments.ride_id from NOT NULL to NULL & add columns via batch_alter_table
    with op.batch_alter_table('payments', schema=None) as batch_op:
        batch_op.alter_column('ride_id',
                   existing_type=sa.String(length=36),
                   nullable=True)
        if 'user_id' not in existing_cols:
            batch_op.add_column(sa.Column('user_id', sa.String(length=36), nullable=True))
            batch_op.create_foreign_key('fk_payments_user_id', 'users', ['user_id'], ['id'])
            batch_op.create_index('ix_payments_user_id', ['user_id'], unique=False)
        if 'purpose' not in existing_cols:
            batch_op.add_column(sa.Column('purpose', sa.String(length=32), server_default='RIDE_FARE', nullable=False))
            batch_op.create_index('ix_payments_purpose', ['purpose'], unique=False)
        batch_op.create_index('ix_payments_provider_order_id_unique', ['provider_order_id'], unique=True)

    # 2. Add unique constraint to payment_webhook_inbox.event_digest
    with op.batch_alter_table('payment_webhook_inbox', schema=None) as batch_op:
        batch_op.alter_column('event_digest',
                   existing_type=sa.String(length=64),
                   nullable=False)
        batch_op.create_unique_constraint('uq_payment_webhook_inbox_event_digest', ['event_digest'])

def downgrade() -> None:
    conn = op.get_bind()
    dialect = conn.dialect.name
    with op.batch_alter_table('payment_webhook_inbox', schema=None) as batch_op:
        batch_op.drop_constraint('uq_payment_webhook_inbox_event_digest', type_='unique')
    with op.batch_alter_table('payments', schema=None) as batch_op:
        batch_op.drop_index('ix_payments_provider_order_id_unique')
        batch_op.drop_index('ix_payments_purpose')
        batch_op.drop_index('ix_payments_user_id')
        if dialect != 'sqlite':
            batch_op.drop_constraint('fk_payments_user_id', type_='foreignkey')
        batch_op.drop_column('purpose')
        batch_op.drop_column('user_id')
