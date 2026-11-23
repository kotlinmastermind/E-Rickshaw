"""add unique index for active fare rules per category and city

Revision ID: 0004_active_fare_rule_unique_index
Revises: 0003_india_wide_city_fare_and_price_lock
Create Date: 2026-11-23

"""
from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision = '0004_active_fare_rule_unique_index'
down_revision = '0003_india_wide_city_fare_and_price_lock'
branch_labels = None
depends_on = None

def upgrade() -> None:
    bind = op.get_bind()
    dialect_name = bind.dialect.name
    if dialect_name == 'postgresql':
        bind.execute(
            sa.text(
                "CREATE UNIQUE INDEX IF NOT EXISTS uq_fare_rules_active_category_city "
                "ON fare_rules (category_id, city_code) "
                "WHERE is_active = TRUE;"
            )
        )

def downgrade() -> None:
    bind = op.get_bind()
    dialect_name = bind.dialect.name
    if dialect_name == 'postgresql':
        bind.execute(
            sa.text(
                "DROP INDEX IF EXISTS uq_fare_rules_active_category_city;"
            )
        )
