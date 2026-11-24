"""Audio safety recording suite migration

Revision ID: 0009_audio_safety_recording_suite
Revises: 0008_dialog_hosts_production_remediation
Create Date: 2026-11-23

"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.engine.reflection import Inspector

# revision identifiers, used by Alembic.
revision = '0009_audio_safety_recording_suite'
down_revision = '0008_dialog_hosts_production_remediation'
branch_labels = None
depends_on = None


def upgrade():
    conn = op.get_bind()
    inspector = Inspector.from_engine(conn)
    existing_tables = set(inspector.get_table_names())

    # 1. Table: audio_recording_consents
    if 'audio_recording_consents' not in existing_tables:
        op.create_table(
            'audio_recording_consents',
            sa.Column('id', sa.String(36), primary_key=True),
            sa.Column('ride_id', sa.String(36), sa.ForeignKey('rides.id', ondelete='CASCADE'), nullable=False),
            sa.Column('user_id', sa.String(36), sa.ForeignKey('users.id'), nullable=False),
            sa.Column('user_role', sa.String(32), nullable=False),  # CUSTOMER, DRIVER
            sa.Column('recording_session_id', sa.String(64), nullable=True),
            sa.Column('consent_purpose', sa.String(128), server_default='IN_CABIN_SAFETY_DPDP_COMPLIANT', nullable=False),
            sa.Column('consent_version', sa.String(32), server_default='v1.0', nullable=False),
            sa.Column('consent_status', sa.String(32), server_default='PENDING', nullable=False),  # NOT_REQUESTED, PENDING, GRANTED, DECLINED, WITHDRAWN, EXPIRED
            sa.Column('granted_at', sa.DateTime(timezone=True), nullable=True),
            sa.Column('withdrawn_at', sa.DateTime(timezone=True), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
            sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.func.now(), onupdate=sa.func.now(), nullable=False),
            sa.UniqueConstraint('ride_id', 'user_id', 'consent_version', name='uq_audio_consent_ride_user_version')
        )
        op.create_index('ix_audio_consents_ride_id', 'audio_recording_consents', ['ride_id'])
        op.create_index('ix_audio_consents_user_id', 'audio_recording_consents', ['user_id'])
        op.create_index('ix_audio_consents_status', 'audio_recording_consents', ['consent_status'])

    # 2. Table: audio_recording_sessions
    if 'audio_recording_sessions' not in existing_tables:
        op.create_table(
            'audio_recording_sessions',
            sa.Column('id', sa.String(36), primary_key=True),
            sa.Column('session_id', sa.String(64), unique=True, nullable=False),
            sa.Column('ride_id', sa.String(36), sa.ForeignKey('rides.id', ondelete='CASCADE'), nullable=False),
            sa.Column('rider_user_id', sa.String(36), sa.ForeignKey('users.id'), nullable=False),
            sa.Column('driver_user_id', sa.String(36), sa.ForeignKey('users.id'), nullable=False),
            sa.Column('recording_status', sa.String(32), server_default='IDLE', nullable=False),  # IDLE, AWAITING_CONSENT, AUTHORIZED, RECORDING, STOPPED, COMPLETED, FAILED, CANCELLED
            sa.Column('authorization_status', sa.String(32), server_default='PENDING', nullable=False),  # PENDING, AUTHORIZED, REVOKED, EXPIRED
            sa.Column('rider_consent_id', sa.String(36), nullable=True),
            sa.Column('driver_consent_id', sa.String(36), nullable=True),
            sa.Column('started_at', sa.DateTime(timezone=True), nullable=True),
            sa.Column('stopped_at', sa.DateTime(timezone=True), nullable=True),
            sa.Column('stopped_reason', sa.String(128), nullable=True),
            sa.Column('encryption_algorithm', sa.String(32), server_default='AES-256-GCM', nullable=False),
            sa.Column('encryption_key_id', sa.String(64), server_default='managed-envelope-v1', nullable=False),
            sa.Column('total_segments', sa.Integer(), server_default='0', nullable=False),
            sa.Column('total_duration_seconds', sa.Float(), server_default='0.0', nullable=False),
            sa.Column('last_heartbeat_at', sa.DateTime(timezone=True), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
            sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.func.now(), onupdate=sa.func.now(), nullable=False)
        )
        op.create_index('ix_audio_sessions_ride_id', 'audio_recording_sessions', ['ride_id'])
        op.create_index('ix_audio_sessions_session_id', 'audio_recording_sessions', ['session_id'])
        op.create_index('ix_audio_sessions_status', 'audio_recording_sessions', ['recording_status'])

    # 3. Table: audio_recording_segments
    if 'audio_recording_segments' not in existing_tables:
        op.create_table(
            'audio_recording_segments',
            sa.Column('id', sa.String(36), primary_key=True),
            sa.Column('session_id', sa.String(64), nullable=False),
            sa.Column('ride_id', sa.String(36), sa.ForeignKey('rides.id', ondelete='CASCADE'), nullable=False),
            sa.Column('segment_sequence', sa.Integer(), nullable=False),
            sa.Column('storage_bucket', sa.String(64), server_default='ride-audio-recordings', nullable=False),
            sa.Column('storage_object_path', sa.String(512), nullable=False),
            sa.Column('file_format', sa.String(32), server_default='audio/aac', nullable=False),
            sa.Column('encryption_nonce', sa.String(64), nullable=False),
            sa.Column('encryption_tag', sa.String(64), nullable=False),
            sa.Column('duration_seconds', sa.Float(), server_default='0.0', nullable=False),
            sa.Column('file_size_bytes', sa.Integer(), server_default='0', nullable=False),
            sa.Column('sha256_hash', sa.String(64), nullable=False),
            sa.Column('upload_status', sa.String(32), server_default='UPLOADED', nullable=False),  # PENDING, UPLOADED, FAILED, VERIFIED
            sa.Column('retention_expiry_at', sa.DateTime(timezone=True), nullable=False),
            sa.Column('is_purged', sa.Boolean(), server_default='0', nullable=False),
            sa.Column('purged_at', sa.DateTime(timezone=True), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
            sa.UniqueConstraint('session_id', 'segment_sequence', name='uq_audio_segment_session_seq')
        )
        op.create_index('ix_audio_segments_session_id', 'audio_recording_segments', ['session_id'])
        op.create_index('ix_audio_segments_ride_id', 'audio_recording_segments', ['ride_id'])
        op.create_index('ix_audio_segments_retention_expiry', 'audio_recording_segments', ['retention_expiry_at'])

    # 4. Table: audio_recording_audit_logs
    if 'audio_recording_audit_logs' not in existing_tables:
        op.create_table(
            'audio_recording_audit_logs',
            sa.Column('id', sa.String(36), primary_key=True),
            sa.Column('session_id', sa.String(64), nullable=True),
            sa.Column('ride_id', sa.String(36), nullable=True),
            sa.Column('actor_id', sa.String(36), sa.ForeignKey('users.id'), nullable=False),
            sa.Column('actor_role', sa.String(32), nullable=False),  # CUSTOMER, DRIVER, ADMIN, SYSTEM
            sa.Column('action', sa.String(64), nullable=False),
            sa.Column('access_reason', sa.Text(), nullable=True),
            sa.Column('authorization_result', sa.String(32), server_default='ALLOWED', nullable=False),  # ALLOWED, DENIED
            sa.Column('metadata_json', sa.Text(), server_default='{}', nullable=False),
            sa.Column('ip_address', sa.String(64), nullable=True),
            sa.Column('user_agent', sa.String(255), nullable=True),
            sa.Column('correlation_id', sa.String(64), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False)
        )
        op.create_index('ix_audio_audit_session_id', 'audio_recording_audit_logs', ['session_id'])
        op.create_index('ix_audio_audit_ride_id', 'audio_recording_audit_logs', ['ride_id'])
        op.create_index('ix_audio_audit_actor_id', 'audio_recording_audit_logs', ['actor_id'])
        op.create_index('ix_audio_audit_action', 'audio_recording_audit_logs', ['action'])


def downgrade():
    op.drop_table('audio_recording_audit_logs')
    op.drop_table('audio_recording_segments')
    op.drop_table('audio_recording_sessions')
    op.drop_table('audio_recording_consents')
