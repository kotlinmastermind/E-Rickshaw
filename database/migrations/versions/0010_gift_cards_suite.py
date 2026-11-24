"""Gift cards suite and transaction ledger migration

Revision ID: 0010_gift_cards_suite
Revises: 0009_audio_safety_recording_suite
Create Date: 2026-11-23

"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.engine.reflection import Inspector

# revision identifiers, used by Alembic.
revision = '0010_gift_cards_suite'
down_revision = '0009_audio_safety_recording_suite'
branch_labels = None
depends_on = None


def upgrade():
    conn = op.get_bind()
    inspector = Inspector.from_engine(conn)
    existing_tables = set(inspector.get_table_names())

    # 1. Table: gift_cards
    if 'gift_cards' not in existing_tables:
        op.create_table(
            'gift_cards',
            sa.Column('id', sa.String(36), primary_key=True),
            sa.Column('code', sa.String(64), unique=True, nullable=False),
            sa.Column('code_hash', sa.String(64), nullable=True),
            sa.Column('pin', sa.String(8), nullable=True),
            sa.Column('initial_value_cents', sa.Integer(), nullable=False),
            sa.Column('remaining_value_cents', sa.Integer(), nullable=False),
            sa.Column('currency', sa.String(8), server_default='INR', nullable=False),
            sa.Column('purchased_by_user_id', sa.String(36), sa.ForeignKey('users.id'), nullable=False),
            sa.Column('card_design', sa.String(32), server_default='ROYAL_GOLD', nullable=False),
            sa.Column('recipient_name', sa.String(128), nullable=True),
            sa.Column('recipient_email', sa.String(255), nullable=True),
            sa.Column('recipient_phone', sa.String(20), nullable=True),
            sa.Column('sender_name', sa.String(128), nullable=True),
            sa.Column('message', sa.Text(), nullable=True),
            sa.Column('delivery_method', sa.String(32), server_default='IN_APP', nullable=False),
            sa.Column('status', sa.String(32), server_default='ACTIVE', nullable=False),
            sa.Column('payment_status', sa.String(32), server_default='COMPLETED', nullable=False),
            sa.Column('payment_order_id', sa.String(64), nullable=True),
            sa.Column('payment_id', sa.String(64), nullable=True),
            sa.Column('is_active', sa.Boolean(), server_default=sa.true(), nullable=False),
            sa.Column('expires_at', sa.DateTime(timezone=True), nullable=False),
            sa.Column('redeemed_by_user_id', sa.String(36), sa.ForeignKey('users.id'), nullable=True),
            sa.Column('redeemed_at', sa.DateTime(timezone=True), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
            sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=True)
        )
        op.create_index('ix_gift_cards_code', 'gift_cards', ['code'], unique=True)
        op.create_index('ix_gift_cards_code_hash', 'gift_cards', ['code_hash'])
        op.create_index('ix_gift_cards_purchaser', 'gift_cards', ['purchased_by_user_id'])
        op.create_index('ix_gift_cards_status', 'gift_cards', ['status'])
        op.create_index('ix_gift_cards_redeemer', 'gift_cards', ['redeemed_by_user_id'])
    else:
        cols = {c['name'] for c in inspector.get_columns('gift_cards')}
        if 'code_hash' not in cols:
            op.add_column('gift_cards', sa.Column('code_hash', sa.String(64), nullable=True))
            op.create_index('ix_gift_cards_code_hash', 'gift_cards', ['code_hash'])
        if 'pin' not in cols:
            op.add_column('gift_cards', sa.Column('pin', sa.String(8), nullable=True))
        if 'currency' not in cols:
            op.add_column('gift_cards', sa.Column('currency', sa.String(8), server_default='INR', nullable=False))
        if 'card_design' not in cols:
            op.add_column('gift_cards', sa.Column('card_design', sa.String(32), server_default='ROYAL_GOLD', nullable=False))
        if 'recipient_name' not in cols:
            op.add_column('gift_cards', sa.Column('recipient_name', sa.String(128), nullable=True))
        if 'delivery_method' not in cols:
            op.add_column('gift_cards', sa.Column('delivery_method', sa.String(32), server_default='IN_APP', nullable=False))
        if 'status' not in cols:
            op.add_column('gift_cards', sa.Column('status', sa.String(32), server_default='ACTIVE', nullable=False))
            op.create_index('ix_gift_cards_status', 'gift_cards', ['status'])
        if 'payment_status' not in cols:
            op.add_column('gift_cards', sa.Column('payment_status', sa.String(32), server_default='COMPLETED', nullable=False))
        if 'payment_order_id' not in cols:
            op.add_column('gift_cards', sa.Column('payment_order_id', sa.String(64), nullable=True))
        if 'payment_id' not in cols:
            op.add_column('gift_cards', sa.Column('payment_id', sa.String(64), nullable=True))
        if 'redeemed_by_user_id' not in cols:
            op.add_column('gift_cards', sa.Column('redeemed_by_user_id', sa.String(36), sa.ForeignKey('users.id'), nullable=True))
            op.create_index('ix_gift_cards_redeemer', 'gift_cards', ['redeemed_by_user_id'])
        if 'redeemed_at' not in cols:
            op.add_column('gift_cards', sa.Column('redeemed_at', sa.DateTime(timezone=True), nullable=True))
        if 'updated_at' not in cols:
            op.add_column('gift_cards', sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=True))

    # 2. Table: gift_card_transactions
    if 'gift_card_transactions' not in existing_tables:
        op.create_table(
            'gift_card_transactions',
            sa.Column('id', sa.String(36), primary_key=True),
            sa.Column('gift_card_id', sa.String(36), sa.ForeignKey('gift_cards.id', ondelete='CASCADE'), nullable=False),
            sa.Column('user_id', sa.String(36), sa.ForeignKey('users.id'), nullable=False),
            sa.Column('transaction_type', sa.String(32), nullable=False),
            sa.Column('amount_cents', sa.Integer(), nullable=False),
            sa.Column('balance_before_cents', sa.Integer(), nullable=False),
            sa.Column('balance_after_cents', sa.Integer(), nullable=False),
            sa.Column('reference_id', sa.String(64), nullable=True),
            sa.Column('notes', sa.Text(), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False)
        )
        op.create_index('ix_gift_card_transactions_gift_card_id', 'gift_card_transactions', ['gift_card_id'])
        op.create_index('ix_gift_card_transactions_user_id', 'gift_card_transactions', ['user_id'])


def downgrade():
    conn = op.get_bind()
    inspector = Inspector.from_engine(conn)
    existing_tables = set(inspector.get_table_names())

    if 'gift_card_transactions' in existing_tables:
        op.drop_table('gift_card_transactions')
    if 'gift_cards' in existing_tables:
        op.drop_table('gift_cards')
