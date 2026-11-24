"""Dialog hosts production remediation migration

Revision ID: 0008_dialog_hosts_production_remediation
Revises: 0007_driver_vehicle_inspections_suite
Create Date: 2026-11-23

"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.engine.reflection import Inspector

# revision identifiers, used by Alembic.
revision = '0008_dialog_hosts_production_remediation'
down_revision = '0007_driver_vehicle_inspections_suite'
branch_labels = None
depends_on = None


def upgrade():
    conn = op.get_bind()
    inspector = Inspector.from_engine(conn)
    existing_tables = set(inspector.get_table_names())

    # 1. Update rides table with toll_cents
    if 'rides' in existing_tables:
        ride_cols = {col['name'] for col in inspector.get_columns('rides')}
        if 'toll_cents' not in ride_cols:
            op.add_column('rides', sa.Column('toll_cents', sa.Integer(), nullable=False, server_default='0'))

    # 2. Update route_deviation_events table with reason and driver_comments
    if 'route_deviation_events' in existing_tables:
        dev_cols = {col['name'] for col in inspector.get_columns('route_deviation_events')}
        if 'reason' not in dev_cols:
            op.add_column('route_deviation_events', sa.Column('reason', sa.String(255), nullable=True))
        if 'driver_comments' not in dev_cols:
            op.add_column('route_deviation_events', sa.Column('driver_comments', sa.Text(), nullable=True))

    # 3. Create ride_tolls table
    if 'ride_tolls' not in existing_tables:
        op.create_table(
            'ride_tolls',
            sa.Column('id', sa.String(36), primary_key=True),
            sa.Column('ride_id', sa.String(36), sa.ForeignKey('rides.id', ondelete='CASCADE'), nullable=False),
            sa.Column('driver_id', sa.String(36), sa.ForeignKey('driver_profiles.id'), nullable=False),
            sa.Column('amount_cents', sa.Integer(), nullable=False),
            sa.Column('toll_name', sa.String(128), nullable=False),
            sa.Column('latitude', sa.Float(), nullable=True),
            sa.Column('longitude', sa.Float(), nullable=True),
            sa.Column('status', sa.String(32), server_default='APPROVED', nullable=False),
            sa.Column('receipt_url', sa.String(512), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now())
        )
        op.create_index('ix_ride_tolls_ride_id', 'ride_tolls', ['ride_id'])
        op.create_index('ix_ride_tolls_driver_id', 'ride_tolls', ['driver_id'])

    # 4. Create ride_reschedule_logs table
    if 'ride_reschedule_logs' not in existing_tables:
        op.create_table(
            'ride_reschedule_logs',
            sa.Column('id', sa.String(36), primary_key=True),
            sa.Column('ride_id', sa.String(36), sa.ForeignKey('rides.id', ondelete='CASCADE'), nullable=False),
            sa.Column('requested_by_user_id', sa.String(36), sa.ForeignKey('users.id'), nullable=False),
            sa.Column('previous_scheduled_time', sa.DateTime(timezone=True), nullable=True),
            sa.Column('new_scheduled_time', sa.DateTime(timezone=True), nullable=False),
            sa.Column('delay_minutes', sa.Integer(), server_default='0', nullable=False),
            sa.Column('flight_number', sa.String(32), nullable=True),
            sa.Column('reason', sa.String(255), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now())
        )
        op.create_index('ix_ride_reschedule_logs_ride_id', 'ride_reschedule_logs', ['ride_id'])


def downgrade():
    conn = op.get_bind()
    inspector = Inspector.from_engine(conn)
    existing_tables = set(inspector.get_table_names())

    if 'ride_reschedule_logs' in existing_tables:
        op.drop_table('ride_reschedule_logs')

    if 'ride_tolls' in existing_tables:
        op.drop_table('ride_tolls')

    if 'route_deviation_events' in existing_tables:
        dev_cols = {col['name'] for col in inspector.get_columns('route_deviation_events')}
        if 'driver_comments' in dev_cols:
            op.drop_column('route_deviation_events', 'driver_comments')
        if 'reason' in dev_cols:
            op.drop_column('route_deviation_events', 'reason')

    if 'rides' in existing_tables:
        ride_cols = {col['name'] for col in inspector.get_columns('rides')}
        if 'toll_cents' in ride_cols:
            op.drop_column('rides', 'toll_cents')
