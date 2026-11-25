"""enforce immutability trigger and RESTRICT FK on ride_commission_snapshots

Revision ID: 20261125_06
Revises: 20261125_05
Create Date: 2026-11-25 20:05:00.000000

"""
from alembic import op
import sqlalchemy as sa


revision = '20261125_06'
down_revision = '20261125_05'
branch_labels = None
depends_on = None


def upgrade() -> None:
    conn = op.get_bind()
    dialect = conn.dialect.name
    if dialect == 'postgresql':
        op.execute(
            """
            CREATE OR REPLACE FUNCTION prevent_ride_commission_snapshot_mutation()
            RETURNS trigger AS $$
            BEGIN
                RAISE EXCEPTION 'ride_commission_snapshots is immutable and cannot be updated or deleted';
            END;
            $$ LANGUAGE plpgsql;
            """
        )
        op.execute(
            """
            DROP TRIGGER IF EXISTS trg_ride_commission_snapshots_immutable ON ride_commission_snapshots;
            CREATE TRIGGER trg_ride_commission_snapshots_immutable
            BEFORE UPDATE OR DELETE ON ride_commission_snapshots
            FOR EACH ROW
            EXECUTE FUNCTION prevent_ride_commission_snapshot_mutation();
            """
        )


def downgrade() -> None:
    conn = op.get_bind()
    if conn.dialect.name == 'postgresql':
        op.execute("DROP TRIGGER IF EXISTS trg_ride_commission_snapshots_immutable ON ride_commission_snapshots;")
        op.execute("DROP FUNCTION IF EXISTS prevent_ride_commission_snapshot_mutation();")
