"""drop unused phantom tables from extended models

Revision ID: 0005_drop_unused_phantom_tables
Revises: 0004_active_fare_rule_unique_index
Create Date: 2026-11-23

"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.engine.reflection import Inspector

# revision identifiers, used by Alembic.
revision = '0005_drop_unused_phantom_tables'
down_revision = '0004_active_fare_rule_unique_index'
branch_labels = None
depends_on = None

UNUSED_PHANTOM_TABLES = [
    'ab_test_experiments',
    'airport_transfer_packages',
    'api_error_catalog',
    'api_version_records',
    'background_jobs',
    'backup_policies',
    'business_approval_requests',
    'chauffeur_service_addons',
    'customer_subscriptions',
    'db_migration_verifications',
    'dependency_vulnerability_reports',
    'disaster_recovery_plans',
    'distributed_trace_spans',
    'employee_profiles',
    'event_transport_workspaces',
    'feature_request_votes',
    'fleet_subscription_plans',
    'hourly_chauffeur_bookings',
    'intercity_products',
    'localization_catalog',
    'loyalty_partner_catalog',
    'mobile_crash_reports',
    'partner_accounts',
    'partner_bookings',
    'prepaid_ride_bundles',
    'product_feedback_analytics',
    'provider_health_configs',
    'redis_namespace_configs',
    'restore_drill_schedules',
    'scheduled_job_monitors',
    'secret_rotation_workflows',
    'tourism_places',
    'white_label_tenant_configs'
]

def upgrade() -> None:
    bind = op.get_bind()
    dialect_name = bind.dialect.name
    inspector = Inspector.from_engine(bind)
    existing_tables = set(inspector.get_table_names())

    for table in UNUSED_PHANTOM_TABLES:
        if table in existing_tables:
            if dialect_name == 'postgresql':
                bind.execute(sa.text(f'DROP TABLE IF EXISTS "{table}" CASCADE;'))
            else:
                bind.execute(sa.text(f'DROP TABLE IF EXISTS "{table}";'))


def downgrade() -> None:
    # Phantom tables are decommissioned and had no production data.
    pass
