"""create ride_commission_snapshots table for immutable per-ride commission snapshots

Revision ID: 20261125_05
Revises: 20261125_04
Create Date: 2026-11-25 18:50:00.000000

"""
from alembic import op
import sqlalchemy as sa


revision = '20261125_05'
down_revision = '20261125_04'
branch_labels = None
depends_on = None


def upgrade() -> None:
    conn = op.get_bind()
    inspector = sa.inspect(conn)
    existing_tables = set(inspector.get_table_names())

    if 'ride_commission_snapshots' not in existing_tables:
        op.create_table(
            'ride_commission_snapshots',
            sa.Column('id', sa.String(length=36), nullable=False),
            sa.Column('ride_id', sa.String(length=36), nullable=False),
            sa.Column('commission_rule_id', sa.String(length=36), nullable=True),
            sa.Column('commission_version', sa.Integer(), nullable=False, server_default='0'),
            sa.Column('commission_percentage', sa.Float(), nullable=False),
            sa.Column('category_id', sa.String(length=36), nullable=True),
            sa.Column('gross_fare_cents', sa.Integer(), nullable=False),
            sa.Column('commission_cents', sa.Integer(), nullable=False),
            sa.Column('driver_gross_cents', sa.Integer(), nullable=False),
            sa.Column('commission_classification', sa.String(length=20), nullable=False, server_default='PAYABLE'),
            sa.Column('currency', sa.String(length=8), nullable=False, server_default='INR'),
            sa.Column('calculation_basis', sa.String(length=32), nullable=False, server_default='RIDE_COMPLETION'),
            sa.Column('effective_at', sa.DateTime(timezone=True), nullable=False),
            sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
            sa.ForeignKeyConstraint(['ride_id'], ['rides.id'], ondelete='CASCADE'),
            sa.ForeignKeyConstraint(['commission_rule_id'], ['commission_rules.id'], ondelete='SET NULL'),
            sa.PrimaryKeyConstraint('id'),
            sa.UniqueConstraint('ride_id', name='uq_ride_commission_snapshots_ride_id'),
        )
        op.create_index('ix_ride_commission_snapshots_ride_id', 'ride_commission_snapshots', ['ride_id'], unique=True)


def downgrade() -> None:
    conn = op.get_bind()
    inspector = sa.inspect(conn)
    if 'ride_commission_snapshots' in set(inspector.get_table_names()):
        op.drop_index('ix_ride_commission_snapshots_ride_id', table_name='ride_commission_snapshots')
        op.drop_table('ride_commission_snapshots')
