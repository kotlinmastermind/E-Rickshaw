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
    pay_indexes = {ix['name'] for ix in inspector.get_indexes('payments')}
    pay_uqs = {uq['name'] for uq in inspector.get_unique_constraints('payments')}
    pay_fks = {fk['name'] for fk in inspector.get_foreign_keys('payments') if fk.get('name')}
    inbox_uqs = {uq['name'] for uq in inspector.get_unique_constraints('payment_webhook_inbox')}
    inbox_indexes = {ix['name'] for ix in inspector.get_indexes('payment_webhook_inbox')}

    # 1. Relax payments.ride_id from NOT NULL to NULL & add columns via batch_alter_table
    with op.batch_alter_table('payments', schema=None) as batch_op:
        batch_op.alter_column('ride_id',
                   existing_type=sa.String(length=36),
                   nullable=True)
        if 'user_id' not in existing_cols:
            batch_op.add_column(sa.Column('user_id', sa.String(length=36), nullable=True))
            if 'fk_payments_user_id' not in pay_fks:
                batch_op.create_foreign_key('fk_payments_user_id', 'users', ['user_id'], ['id'])
            if 'ix_payments_user_id' not in pay_indexes:
                batch_op.create_index('ix_payments_user_id', ['user_id'], unique=False)
        if 'purpose' not in existing_cols:
            batch_op.add_column(sa.Column('purpose', sa.String(length=32), server_default='RIDE_FARE', nullable=False))
            if 'ix_payments_purpose' not in pay_indexes:
                batch_op.create_index('ix_payments_purpose', ['purpose'], unique=False)
        if 'ix_payments_provider_order_id_unique' not in (pay_indexes | pay_uqs):
            batch_op.create_index('ix_payments_provider_order_id_unique', ['provider_order_id'], unique=True)

    # 2. Add unique constraint to payment_webhook_inbox.event_digest
    with op.batch_alter_table('payment_webhook_inbox', schema=None) as batch_op:
        batch_op.alter_column('event_digest',
                   existing_type=sa.String(length=64),
                   nullable=False)
        if 'uq_payment_webhook_inbox_event_digest' not in (inbox_uqs | inbox_indexes):
            batch_op.create_unique_constraint('uq_payment_webhook_inbox_event_digest', ['event_digest'])

def downgrade() -> None:
    conn = op.get_bind()
    dialect = conn.dialect.name
    inspector = sa.inspect(conn)
    existing_cols = {c['name'] for c in inspector.get_columns('payments')}
    pay_indexes = {ix['name'] for ix in inspector.get_indexes('payments')}
    pay_fks = {fk['name'] for fk in inspector.get_foreign_keys('payments') if fk.get('name')}
    inbox_uqs = {uq['name'] for uq in inspector.get_unique_constraints('payment_webhook_inbox')}

    with op.batch_alter_table('payment_webhook_inbox', schema=None) as batch_op:
        if 'uq_payment_webhook_inbox_event_digest' in inbox_uqs:
            batch_op.drop_constraint('uq_payment_webhook_inbox_event_digest', type_='unique')
    with op.batch_alter_table('payments', schema=None) as batch_op:
        if 'ix_payments_provider_order_id_unique' in pay_indexes:
            batch_op.drop_index('ix_payments_provider_order_id_unique')
        if 'ix_payments_purpose' in pay_indexes:
            batch_op.drop_index('ix_payments_purpose')
        if 'ix_payments_user_id' in pay_indexes:
            batch_op.drop_index('ix_payments_user_id')
        if dialect != 'sqlite' and 'fk_payments_user_id' in pay_fks:
            batch_op.drop_constraint('fk_payments_user_id', type_='foreignkey')
        if 'purpose' in existing_cols:
            batch_op.drop_column('purpose')
        if 'user_id' in existing_cols:
            batch_op.drop_column('user_id')
