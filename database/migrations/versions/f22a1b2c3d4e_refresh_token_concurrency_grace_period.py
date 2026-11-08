"""Phase 5: Refresh token concurrency grace period (Finding C-P5-01) & Distributed Account Lockout (WP-D).

Adds columns to `sessions` table:
- previous_token_hash: VARCHAR(64), nullable, indexed
- rotated_at: TIMESTAMPTZ, nullable

Creates `account_lockouts` table:
- key_hash: VARCHAR(64), PK
- failed_attempts: INTEGER
- lockout_tier: INTEGER
- locked_until: TIMESTAMPTZ, indexed
- last_failed_at: TIMESTAMPTZ
- created_at: TIMESTAMPTZ
- updated_at: TIMESTAMPTZ
"""
from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision = 'f22a1b2c3d4e'
down_revision = 'f21f7a503c16'
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    if getattr(op.get_context(), "as_sql", False):
        session_cols = []
        tables = []
    else:
        insp = sa.inspect(bind)
        session_cols = [c['name'] for c in insp.get_columns('sessions')]
        tables = insp.get_table_names()
    
    if 'previous_token_hash' not in session_cols:
        op.add_column(
            'sessions',
            sa.Column('previous_token_hash', sa.String(length=64), nullable=True)
        )
        op.create_index(
            'ix_sessions_previous_token_hash',
            'sessions',
            ['previous_token_hash']
        )
    if 'rotated_at' not in session_cols:
        op.add_column(
            'sessions',
            sa.Column('rotated_at', sa.DateTime(timezone=True), nullable=True)
        )

    if 'account_lockouts' not in tables:
        op.create_table(
            'account_lockouts',
            sa.Column('key_hash', sa.String(length=64), primary_key=True),
            sa.Column('failed_attempts', sa.Integer(), nullable=False, server_default='0'),
            sa.Column('lockout_tier', sa.Integer(), nullable=False, server_default='0'),
            sa.Column('locked_until', sa.DateTime(timezone=True), nullable=True),
            sa.Column('last_failed_at', sa.DateTime(timezone=True), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
            sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )
        op.create_index(
            'ix_account_lockouts_locked_until',
            'account_lockouts',
            ['locked_until']
        )


def downgrade() -> None:
    bind = op.get_bind()
    if getattr(op.get_context(), "as_sql", False):
        tables = []
        session_cols = []
    else:
        insp = sa.inspect(bind)
        tables = insp.get_table_names()
        session_cols = [c['name'] for c in insp.get_columns('sessions')]

    if 'account_lockouts' in tables or getattr(op.get_context(), "as_sql", False):
        op.drop_index('ix_account_lockouts_locked_until', table_name='account_lockouts')
        op.drop_table('account_lockouts')

    if 'rotated_at' in session_cols or getattr(op.get_context(), "as_sql", False):
        op.drop_column('sessions', 'rotated_at')
    if 'previous_token_hash' in session_cols or getattr(op.get_context(), "as_sql", False):
        op.drop_index('ix_sessions_previous_token_hash', table_name='sessions')
        op.drop_column('sessions', 'previous_token_hash')


