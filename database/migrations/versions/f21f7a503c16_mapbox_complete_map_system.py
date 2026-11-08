"""Phase 6: Mapbox Complete Map System migration.

Creates tables for:
- ride_location_history
- ride_routes
- geofence_events
- route_deviation_events
- map_api_usage_logs
- location_sharing_sessions
- emergency_location_events
"""
from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision = 'f21f7a503c16'
down_revision = 'f20e6f492b05'
branch_labels = None
depends_on = None


def upgrade() -> None:
    # 1. ride_location_history
    op.create_table(
        'ride_location_history',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('ride_id', sa.String(length=36), sa.ForeignKey('rides.id', ondelete='CASCADE'), nullable=False),
        sa.Column('driver_id', sa.String(length=36), sa.ForeignKey('driver_profiles.id'), nullable=False),
        sa.Column('latitude', sa.Float(), nullable=False),
        sa.Column('longitude', sa.Float(), nullable=False),
        sa.Column('heading', sa.Float(), nullable=True),
        sa.Column('speed_kmh', sa.Float(), nullable=True),
        sa.Column('accuracy_meters', sa.Float(), nullable=True),
        sa.Column('recorded_at', sa.DateTime(timezone=True), nullable=False)
    )
    op.create_index('idx_ride_loc_hist_ride_rec', 'ride_location_history', ['ride_id', 'recorded_at'])
    op.create_index('ix_ride_location_history_driver_id', 'ride_location_history', ['driver_id'])
    op.create_index('ix_ride_location_history_recorded_at', 'ride_location_history', ['recorded_at'])

    # 2. ride_routes
    op.create_table(
        'ride_routes',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('ride_id', sa.String(length=36), sa.ForeignKey('rides.id', ondelete='CASCADE'), nullable=False),
        sa.Column('polyline', sa.Text(), nullable=False),
        sa.Column('waypoints_json', sa.Text(), nullable=False, server_default='[]'),
        sa.Column('distance_km', sa.Float(), nullable=False),
        sa.Column('duration_minutes', sa.Float(), nullable=False),
        sa.Column('traffic_aware', sa.Boolean(), nullable=False, server_default=sa.text('true')),
        sa.Column('route_label', sa.String(length=64), nullable=False, server_default='Mapbox Driving'),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False)
    )
    op.create_index('ix_ride_routes_ride_id', 'ride_routes', ['ride_id'])

    # 3. geofence_events
    op.create_table(
        'geofence_events',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('driver_id', sa.String(length=36), sa.ForeignKey('driver_profiles.id'), nullable=False),
        sa.Column('zone_id', sa.String(length=36), sa.ForeignKey('geofence_zones.id'), nullable=False),
        sa.Column('ride_id', sa.String(length=36), nullable=True),
        sa.Column('event_type', sa.String(length=32), nullable=False),
        sa.Column('latitude', sa.Float(), nullable=False),
        sa.Column('longitude', sa.Float(), nullable=False),
        sa.Column('recorded_at', sa.DateTime(timezone=True), nullable=False)
    )
    op.create_index('idx_geofence_ev_zone', 'geofence_events', ['zone_id', 'recorded_at'])
    op.create_index('ix_geofence_events_driver_id', 'geofence_events', ['driver_id'])
    op.create_index('ix_geofence_events_recorded_at', 'geofence_events', ['recorded_at'])

    # 4. route_deviation_events
    op.create_table(
        'route_deviation_events',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('ride_id', sa.String(length=36), sa.ForeignKey('rides.id', ondelete='CASCADE'), nullable=False),
        sa.Column('driver_id', sa.String(length=36), sa.ForeignKey('driver_profiles.id'), nullable=False),
        sa.Column('deviation_meters', sa.Float(), nullable=False),
        sa.Column('current_latitude', sa.Float(), nullable=False),
        sa.Column('current_longitude', sa.Float(), nullable=False),
        sa.Column('expected_latitude', sa.Float(), nullable=False),
        sa.Column('expected_longitude', sa.Float(), nullable=False),
        sa.Column('is_alert_triggered', sa.Boolean(), nullable=False, server_default=sa.text('false')),
        sa.Column('recorded_at', sa.DateTime(timezone=True), nullable=False)
    )
    op.create_index('idx_route_dev_ride', 'route_deviation_events', ['ride_id', 'recorded_at'])
    op.create_index('ix_route_deviation_events_driver_id', 'route_deviation_events', ['driver_id'])

    # 5. map_api_usage_logs
    op.create_table(
        'map_api_usage_logs',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('service_name', sa.String(length=64), nullable=False),
        sa.Column('endpoint', sa.String(length=128), nullable=False),
        sa.Column('cost_units', sa.Integer(), nullable=False, server_default='1'),
        sa.Column('status_code', sa.Integer(), nullable=False, server_default='200'),
        sa.Column('latency_ms', sa.Float(), nullable=False, server_default='0.0'),
        sa.Column('recorded_at', sa.DateTime(timezone=True), nullable=False)
    )
    op.create_index('idx_map_usage_serv', 'map_api_usage_logs', ['service_name', 'recorded_at'])

    # 6. location_sharing_sessions
    op.create_table(
        'location_sharing_sessions',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('ride_id', sa.String(length=36), sa.ForeignKey('rides.id', ondelete='CASCADE'), nullable=False),
        sa.Column('share_token', sa.String(length=64), nullable=False, unique=True),
        sa.Column('is_active', sa.Boolean(), nullable=False, server_default=sa.text('true')),
        sa.Column('expires_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False)
    )
    op.create_index('ix_location_sharing_sessions_ride_id', 'location_sharing_sessions', ['ride_id'])
    op.create_index('ix_location_sharing_sessions_share_token', 'location_sharing_sessions', ['share_token'])

    # 7. emergency_location_events
    bind = op.get_bind()
    if getattr(op.get_context(), "as_sql", False):
        has_alerts = False
    else:
        insp = sa.inspect(bind)
        has_alerts = insp.has_table('emergency_alerts')
    if not has_alerts:
        op.create_table(
            'emergency_alerts',
            sa.Column('id', sa.String(length=36), primary_key=True),
            sa.Column('ride_id', sa.String(length=36), sa.ForeignKey('rides.id'), nullable=False),
            sa.Column('triggered_by_user_id', sa.String(length=36), sa.ForeignKey('users.id'), nullable=False),
            sa.Column('alert_type', sa.String(length=32), nullable=False, server_default='SOS_BUTTON'),
            sa.Column('latitude', sa.Float(), nullable=False),
            sa.Column('longitude', sa.Float(), nullable=False),
            sa.Column('status', sa.String(length=30), nullable=False, server_default='ACTIVE'),
            sa.Column('contacts_notified_count', sa.Integer(), nullable=False, server_default='0'),
            sa.Column('soc_agent_notes', sa.Text(), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
            sa.Column('resolved_at', sa.DateTime(timezone=True), nullable=True),
        )
        op.create_index('ix_emergency_alerts_ride_id', 'emergency_alerts', ['ride_id'])
        op.create_index('ix_emergency_alerts_triggered_by_user_id', 'emergency_alerts', ['triggered_by_user_id'])

    op.create_table(
        'emergency_location_events',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('alert_id', sa.String(length=36), sa.ForeignKey('emergency_alerts.id', ondelete='CASCADE'), nullable=False),
        sa.Column('ride_id', sa.String(length=36), nullable=True),
        sa.Column('user_id', sa.String(length=36), sa.ForeignKey('users.id'), nullable=False),
        sa.Column('latitude', sa.Float(), nullable=False),
        sa.Column('longitude', sa.Float(), nullable=False),
        sa.Column('speed_kmh', sa.Float(), nullable=True),
        sa.Column('accuracy_meters', sa.Float(), nullable=True),
        sa.Column('battery_level', sa.Float(), nullable=True),
        sa.Column('recorded_at', sa.DateTime(timezone=True), nullable=False)
    )
    op.create_index('idx_emerg_loc_alert', 'emergency_location_events', ['alert_id', 'recorded_at'])
    op.create_index('ix_emergency_location_events_ride_id', 'emergency_location_events', ['ride_id'])
    op.create_index('ix_emergency_location_events_user_id', 'emergency_location_events', ['user_id'])


def downgrade() -> None:
    op.drop_table('emergency_location_events')
    op.drop_table('location_sharing_sessions')
    op.drop_table('map_api_usage_logs')
    op.drop_table('route_deviation_events')
    op.drop_table('geofence_events')
    op.drop_table('ride_routes')
    op.drop_table('ride_location_history')

