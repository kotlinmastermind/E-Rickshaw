"""financial lifecycle constraints, refund provider ids, webhook status widening, and reconciliation anomalies

Revision ID: 20261125_01
Revises: 20261124_02
Create Date: 2026-11-25 08:15:00.000000

"""
from alembic import op
import sqlalchemy as sa


revision = '20261125_01'
down_revision = '20261124_02'
branch_labels = None
depends_on = None


def upgrade() -> None:
    conn = op.get_bind()
    inspector = sa.inspect(conn)
    existing_tables = set(inspector.get_table_names())

    # 1. Widen payment_webhook_inbox.status and add audit columns
    if 'payment_webhook_inbox' in existing_tables:
        inbox_cols = {c['name'] for c in inspector.get_columns('payment_webhook_inbox')}
        inbox_indexes = {ix['name'] for ix in inspector.get_indexes('payment_webhook_inbox')}
        with op.batch_alter_table('payment_webhook_inbox', schema=None) as batch_op:
            batch_op.alter_column(
                'status',
                existing_type=sa.String(length=20),
                type_=sa.String(length=32),
                existing_nullable=False,
            )
            if 'event_type' not in inbox_cols:
                batch_op.add_column(sa.Column('event_type', sa.String(length=64), nullable=True))
            if 'provider_event_id' not in inbox_cols:
                batch_op.add_column(sa.Column('provider_event_id', sa.String(length=128), nullable=True))
            if 'processed_at' not in inbox_cols:
                batch_op.add_column(sa.Column('processed_at', sa.DateTime(timezone=True), nullable=True))
            if 'ix_payment_webhook_inbox_provider_event_id' not in inbox_indexes:
                batch_op.create_index(
                    'ix_payment_webhook_inbox_provider_event_id',
                    ['provider_event_id'],
                    unique=False,
                )

    # 2. Extend payment_refunds with provider_refund_id, raw_gateway_amount_cents, excess_amount_cents and checks
    if 'payment_refunds' in existing_tables:
        ref_cols = {c['name'] for c in inspector.get_columns('payment_refunds')}
        ref_indexes = {ix['name'] for ix in inspector.get_indexes('payment_refunds')}
        ref_checks = {ck['name'] for ck in inspector.get_check_constraints('payment_refunds')}
        with op.batch_alter_table('payment_refunds', schema=None) as batch_op:
            if 'provider_refund_id' not in ref_cols:
                batch_op.add_column(sa.Column('provider_refund_id', sa.String(length=128), nullable=True))
            if 'raw_gateway_amount_cents' not in ref_cols:
                batch_op.add_column(sa.Column('raw_gateway_amount_cents', sa.Integer(), nullable=True))
            if 'excess_amount_cents' not in ref_cols:
                batch_op.add_column(sa.Column('excess_amount_cents', sa.Integer(), server_default='0', nullable=False))
            if 'ix_payment_refunds_provider_refund_id' not in ref_indexes:
                batch_op.create_index('ix_payment_refunds_provider_refund_id', ['provider_refund_id'], unique=True)
            if 'ck_payment_refunds_non_negative_amount' not in ref_checks:
                batch_op.create_check_constraint('ck_payment_refunds_non_negative_amount', 'amount_cents >= 0')
            if 'ck_payment_refunds_non_negative_excess' not in ref_checks:
                batch_op.create_check_constraint('ck_payment_refunds_non_negative_excess', 'excess_amount_cents >= 0')

    # 3. Payments ceiling/non-negativity constraints
    if 'payments' in existing_tables:
        pay_checks = {ck['name'] for ck in inspector.get_check_constraints('payments')}
        with op.batch_alter_table('payments', schema=None) as batch_op:
            if 'ck_payments_non_negative_amount' not in pay_checks:
                batch_op.create_check_constraint('ck_payments_non_negative_amount', 'amount_cents >= 0')
            if 'ck_payments_non_negative_refunded' not in pay_checks:
                batch_op.create_check_constraint('ck_payments_non_negative_refunded', 'refunded_amount_cents >= 0')
            if 'ck_payments_refunded_lte_amount' not in pay_checks:
                batch_op.create_check_constraint('ck_payments_refunded_lte_amount', 'refunded_amount_cents <= amount_cents')

    # 4. Enforce DB-level unique settlement reference on driver_payout_requests
    if 'driver_payout_requests' in existing_tables:
        payout_uqs = {uq['name'] for uq in inspector.get_unique_constraints('driver_payout_requests')}
        if 'uq_driver_payout_requests_settlement_reference' not in payout_uqs:
            with op.batch_alter_table('driver_payout_requests', schema=None) as batch_op:
                batch_op.create_unique_constraint(
                    'uq_driver_payout_requests_settlement_reference',
                    ['settlement_reference'],
                )

    # 5. Create dedicated payment_reconciliation_anomalies table
    if 'payment_reconciliation_anomalies' not in existing_tables:
        op.create_table(
            'payment_reconciliation_anomalies',
            sa.Column('id', sa.String(length=36), primary_key=True),
            sa.Column('payment_id', sa.String(length=36), sa.ForeignKey('payments.id'), nullable=True),
            sa.Column('webhook_inbox_id', sa.String(length=36), nullable=True),
            sa.Column('provider_name', sa.String(length=32), server_default='RAZORPAY', nullable=False),
            sa.Column('provider_payment_id', sa.String(length=128), nullable=True),
            sa.Column('provider_order_id', sa.String(length=128), nullable=True),
            sa.Column('provider_refund_id', sa.String(length=128), nullable=True),
            sa.Column('anomaly_type', sa.String(length=64), nullable=False),
            sa.Column('expected_amount_cents', sa.Integer(), nullable=True),
            sa.Column('raw_gateway_amount_cents', sa.Integer(), nullable=True),
            sa.Column('applied_amount_cents', sa.Integer(), nullable=True),
            sa.Column('excess_amount_cents', sa.Integer(), server_default='0', nullable=False),
            sa.Column('currency', sa.String(length=10), server_default='INR', nullable=False),
            sa.Column('status', sa.String(length=32), server_default='OPEN', nullable=False),
            sa.Column('reason', sa.String(length=500), nullable=False),
            sa.Column('details_json', sa.Text(), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
            sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
            sa.UniqueConstraint('anomaly_type', 'provider_refund_id', name='uq_recon_anomaly_type_refund_id'),
        )
        with op.batch_alter_table('payment_reconciliation_anomalies', schema=None) as batch_op:
            batch_op.create_index('ix_recon_anomalies_payment_id', ['payment_id'], unique=False)
            batch_op.create_index('ix_recon_anomalies_webhook_inbox_id', ['webhook_inbox_id'], unique=False)
            batch_op.create_index('ix_recon_anomalies_provider_payment_id', ['provider_payment_id'], unique=False)
            batch_op.create_index('ix_recon_anomalies_provider_order_id', ['provider_order_id'], unique=False)
            batch_op.create_index('ix_recon_anomalies_provider_refund_id', ['provider_refund_id'], unique=False)
            batch_op.create_index('ix_recon_anomalies_anomaly_type', ['anomaly_type'], unique=False)
            batch_op.create_index('ix_recon_anomalies_status', ['status'], unique=False)

    # 6. Partial unique index preventing duplicate driver ledger holds/reversals/earnings/commissions
    if 'driver_ledger_entries' in existing_tables:
        dl_indexes = {ix['name'] for ix in inspector.get_indexes('driver_ledger_entries')}
        if 'ix_driver_ledger_unique_category_ref' not in dl_indexes:
            op.create_index(
                'ix_driver_ledger_unique_category_ref',
                'driver_ledger_entries',
                ['driver_id', 'category', 'reference_id'],
                unique=True,
                sqlite_where=sa.text("category IN ('TRIP_EARNING', 'PAYOUT_HOLD', 'PAYOUT_REVERSAL', 'COMMISSION') AND reference_id IS NOT NULL"),
                postgresql_where=sa.text("category IN ('TRIP_EARNING', 'PAYOUT_HOLD', 'PAYOUT_REVERSAL', 'COMMISSION') AND reference_id IS NOT NULL"),
            )


def downgrade() -> None:
    conn = op.get_bind()
    inspector = sa.inspect(conn)
    existing_tables = set(inspector.get_table_names())

    if 'driver_ledger_entries' in existing_tables:
        dl_indexes = {ix['name'] for ix in inspector.get_indexes('driver_ledger_entries')}
        if 'ix_driver_ledger_unique_category_ref' in dl_indexes:
            op.drop_index('ix_driver_ledger_unique_category_ref', table_name='driver_ledger_entries')

    if 'payment_reconciliation_anomalies' in existing_tables:
        op.drop_table('payment_reconciliation_anomalies')

    if 'driver_payout_requests' in existing_tables:
        payout_uqs = {uq['name'] for uq in inspector.get_unique_constraints('driver_payout_requests')}
        if 'uq_driver_payout_requests_settlement_reference' in payout_uqs:
            with op.batch_alter_table('driver_payout_requests', schema=None) as batch_op:
                batch_op.drop_constraint('uq_driver_payout_requests_settlement_reference', type_='unique')

    if 'payments' in existing_tables:
        pay_checks = {ck['name'] for ck in inspector.get_check_constraints('payments')}
        with op.batch_alter_table('payments', schema=None) as batch_op:
            for name in ('ck_payments_refunded_lte_amount', 'ck_payments_non_negative_refunded', 'ck_payments_non_negative_amount'):
                if name in pay_checks:
                    batch_op.drop_constraint(name, type_='check')

    if 'payment_refunds' in existing_tables:
        ref_cols = {c['name'] for c in inspector.get_columns('payment_refunds')}
        ref_indexes = {ix['name'] for ix in inspector.get_indexes('payment_refunds')}
        ref_checks = {ck['name'] for ck in inspector.get_check_constraints('payment_refunds')}
        with op.batch_alter_table('payment_refunds', schema=None) as batch_op:
            for ck_name in ('ck_payment_refunds_non_negative_excess', 'ck_payment_refunds_non_negative_amount'):
                if ck_name in ref_checks:
                    batch_op.drop_constraint(ck_name, type_='check')
            if 'ix_payment_refunds_provider_refund_id' in ref_indexes:
                batch_op.drop_index('ix_payment_refunds_provider_refund_id')
            for col in ('excess_amount_cents', 'raw_gateway_amount_cents', 'provider_refund_id'):
                if col in ref_cols:
                    batch_op.drop_column(col)

    if 'payment_webhook_inbox' in existing_tables:
        inbox_cols = {c['name'] for c in inspector.get_columns('payment_webhook_inbox')}
        inbox_indexes = {ix['name'] for ix in inspector.get_indexes('payment_webhook_inbox')}
        with op.batch_alter_table('payment_webhook_inbox', schema=None) as batch_op:
            if 'ix_payment_webhook_inbox_provider_event_id' in inbox_indexes:
                batch_op.drop_index('ix_payment_webhook_inbox_provider_event_id')
            for col in ('processed_at', 'provider_event_id', 'event_type'):
                if col in inbox_cols:
                    batch_op.drop_column(col)
            batch_op.alter_column(
                'status',
                existing_type=sa.String(length=32),
                type_=sa.String(length=20),
                existing_nullable=False,
            )
