"""enforce ride vehicle_category_id not null

Revision ID: 0002_enforce_ride_vehicle_category_not_null
Revises: 0001_initial_schema
Create Date: 2026-11-23

"""
from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision = '0002_enforce_ride_vehicle_category_not_null'
down_revision = '0001_initial_schema'
branch_labels = None
depends_on = None

def upgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    tables = inspector.get_table_names()
    if 'rides' in tables:
        with op.batch_alter_table('rides', schema=None) as batch_op:
            batch_op.alter_column(
                'vehicle_category_id',
                existing_type=sa.String(length=36),
                nullable=False
            )

def downgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    tables = inspector.get_table_names()
    if 'rides' in tables:
        with op.batch_alter_table('rides', schema=None) as batch_op:
            batch_op.alter_column(
                'vehicle_category_id',
                existing_type=sa.String(length=36),
                nullable=True
            )
