"""india wide city fare and price lock

Revision ID: 0003_india_wide_city_fare_and_price_lock
Revises: 0002_enforce_ride_vehicle_category_not_null
Create Date: 2026-11-23

"""
from alembic import op
import sqlalchemy as sa
from datetime import datetime, timezone
import uuid

# revision identifiers, used by Alembic.
revision = '0003_india_wide_city_fare_and_price_lock'
down_revision = '0002_enforce_ride_vehicle_category_not_null'
branch_labels = None
depends_on = None

def upgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    tables = inspector.get_table_names()

    # 1. Create fare_price_locks table if not present
    if 'fare_price_locks' not in tables:
        op.create_table(
            'fare_price_locks',
            sa.Column('id', sa.String(length=36), primary_key=True),
            sa.Column('customer_id', sa.String(length=36), sa.ForeignKey('customer_profiles.id'), nullable=True),
            sa.Column('category_id', sa.String(length=36), sa.ForeignKey('vehicle_categories.id'), nullable=False),
            sa.Column('city_code', sa.String(length=32), nullable=True),
            sa.Column('fare_rule_id', sa.String(length=36), sa.ForeignKey('fare_rules.id'), nullable=False),
            sa.Column('fare_rule_version', sa.Integer(), nullable=False, server_default='1'),
            sa.Column('pickup_latitude', sa.Float(), nullable=False),
            sa.Column('pickup_longitude', sa.Float(), nullable=False),
            sa.Column('destination_latitude', sa.Float(), nullable=False),
            sa.Column('destination_longitude', sa.Float(), nullable=False),
            sa.Column('distance_km', sa.Float(), nullable=False, server_default='0.0'),
            sa.Column('duration_minutes', sa.Float(), nullable=False, server_default='0.0'),
            sa.Column('locked_fare_cents', sa.Integer(), nullable=False),
            sa.Column('surge_multiplier', sa.Float(), nullable=False, server_default='1.0'),
            sa.Column('lock_duration_hours', sa.Integer(), nullable=False, server_default='24'),
            sa.Column('status', sa.String(length=32), nullable=False, server_default='ACTIVE'),
            sa.Column('expires_at', sa.DateTime(timezone=True), nullable=False),
            sa.Column('consumed_at', sa.DateTime(timezone=True), nullable=True),
            sa.Column('consumed_ride_id', sa.String(length=36), sa.ForeignKey('rides.id'), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('CURRENT_TIMESTAMP'))
        )
        op.create_index('ix_fare_price_locks_status', 'fare_price_locks', ['status'])
        op.create_index('ix_fare_price_locks_expires_at', 'fare_price_locks', ['expires_at'])
        op.create_index('ix_fare_price_locks_customer_id', 'fare_price_locks', ['customer_id'])

    # 2. Add city_code to fare_rules if not present
    fare_rules_cols = [c['name'] for c in inspector.get_columns('fare_rules')]
    if 'city_code' not in fare_rules_cols:
        with op.batch_alter_table('fare_rules', schema=None) as batch_op:
            batch_op.add_column(sa.Column('city_code', sa.String(length=32), nullable=True, server_default='DELHI_NCR'))
            batch_op.create_index('ix_fare_rules_city_code', ['city_code'])

    # 3. Add city_code to geofence_zones if not present
    if 'geofence_zones' in tables:
        geofence_cols = [c['name'] for c in inspector.get_columns('geofence_zones')]
        if 'city_code' not in geofence_cols:
            with op.batch_alter_table('geofence_zones', schema=None) as batch_op:
                batch_op.add_column(sa.Column('city_code', sa.String(length=32), nullable=True, server_default='DELHI_NCR'))
                batch_op.create_index('ix_geofence_zones_city_code', ['city_code'])

    # 4. Seed initial Indian operating cities into multi_city_operating_configs if empty
    if 'multi_city_operating_configs' in tables:
        # Check existing count
        count_res = bind.execute(sa.text("SELECT COUNT(*) FROM multi_city_operating_configs")).scalar()
        if count_res == 0:
            initial_cities = [
                {
                    "id": str(uuid.uuid4()),
                    "city_code": "DELHI_NCR",
                    "city_name": "Delhi NCR",
                    "country": "IND",
                    "timezone": "Asia/Kolkata",
                    "tax_rate_percentage": 5.0,
                    "min_fare_cents": 2500,
                    "is_active": True
                },
                {
                    "id": str(uuid.uuid4()),
                    "city_code": "PATNA",
                    "city_name": "Patna",
                    "country": "IND",
                    "timezone": "Asia/Kolkata",
                    "tax_rate_percentage": 5.0,
                    "min_fare_cents": 1500,
                    "is_active": True
                },
                {
                    "id": str(uuid.uuid4()),
                    "city_code": "MUMBAI",
                    "city_name": "Mumbai",
                    "country": "IND",
                    "timezone": "Asia/Kolkata",
                    "tax_rate_percentage": 5.0,
                    "min_fare_cents": 3000,
                    "is_active": True
                },
                {
                    "id": str(uuid.uuid4()),
                    "city_code": "LUCKNOW",
                    "city_name": "Lucknow",
                    "country": "IND",
                    "timezone": "Asia/Kolkata",
                    "tax_rate_percentage": 5.0,
                    "min_fare_cents": 2000,
                    "is_active": True
                },
                {
                    "id": str(uuid.uuid4()),
                    "city_code": "BENGALURU",
                    "city_name": "Bengaluru",
                    "country": "IND",
                    "timezone": "Asia/Kolkata",
                    "tax_rate_percentage": 5.0,
                    "min_fare_cents": 3500,
                    "is_active": True
                }
            ]
            for c in initial_cities:
                bind.execute(
                    sa.text(
                        "INSERT INTO multi_city_operating_configs "
                        "(id, city_code, city_name, country, timezone, tax_rate_percentage, min_fare_cents, is_active) "
                        "VALUES (:id, :city_code, :city_name, :country, :timezone, :tax_rate_percentage, :min_fare_cents, :is_active)"
                    ),
                    c
                )

def downgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    tables = inspector.get_table_names()

    if 'fare_price_locks' in tables:
        op.drop_table('fare_price_locks')

    if 'fare_rules' in tables:
        fare_rules_cols = [c['name'] for c in inspector.get_columns('fare_rules')]
        if 'city_code' in fare_rules_cols:
            with op.batch_alter_table('fare_rules', schema=None) as batch_op:
                batch_op.drop_column('city_code')
