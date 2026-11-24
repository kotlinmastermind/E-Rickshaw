"""Driver vehicle inspection and photo evidence suite migration

Revision ID: 0007_driver_vehicle_inspections_suite
Revises: 0006_corporate_enterprise_suite
Create Date: 2026-11-23

"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.engine.reflection import Inspector
import datetime

# revision identifiers, used by Alembic.
revision = '0007_driver_vehicle_inspections_suite'
down_revision = '0006_corporate_enterprise_suite'
branch_labels = None
depends_on = None


def upgrade():
    conn = op.get_bind()
    inspector = Inspector.from_engine(conn)
    existing_tables = set(inspector.get_table_names())

    # 1. driver_vehicle_inspections table
    if 'driver_vehicle_inspections' not in existing_tables:
        op.create_table(
            'driver_vehicle_inspections',
            sa.Column('id', sa.String(36), primary_key=True),
            sa.Column('driver_id', sa.String(36), sa.ForeignKey('driver_profiles.id', ondelete='CASCADE'), nullable=False),
            sa.Column('vehicle_id', sa.String(36), sa.ForeignKey('vehicles.id', ondelete='CASCADE'), nullable=False),
            sa.Column('inspection_date', sa.Date(), nullable=True),
            sa.Column('inspection_status', sa.String(32), server_default='DRAFT', nullable=False),
            sa.Column('odometer_reading', sa.Float(), nullable=True),
            sa.Column('odometer_unit', sa.String(16), server_default='KM', nullable=False),
            sa.Column('previous_odometer_reading', sa.Float(), nullable=True),
            sa.Column('checklist_json', sa.Text(), nullable=True),
            sa.Column('overall_result', sa.String(32), server_default='PENDING', nullable=True),
            sa.Column('failed_items_json', sa.Text(), nullable=True),
            sa.Column('driver_remarks', sa.Text(), nullable=True),
            sa.Column('inspector_notes', sa.Text(), nullable=True),
            sa.Column('odometer_photo_url', sa.String(512), nullable=True),
            sa.Column('photo_front_url', sa.String(512), nullable=True),
            sa.Column('photo_rear_url', sa.String(512), nullable=True),
            sa.Column('photo_left_url', sa.String(512), nullable=True),
            sa.Column('photo_right_url', sa.String(512), nullable=True),
            sa.Column('photo_interior_front_url', sa.String(512), nullable=True),
            sa.Column('photo_interior_rear_url', sa.String(512), nullable=True),
            sa.Column('inspected_at', sa.DateTime(timezone=True), nullable=True),
            sa.Column('submitted_at', sa.DateTime(timezone=True), nullable=True),
            sa.Column('verification_status', sa.String(32), server_default='PENDING', nullable=False),
            sa.Column('verified_by', sa.String(36), nullable=True),
            sa.Column('verified_at', sa.DateTime(timezone=True), nullable=True),
            sa.Column('rejection_reason', sa.Text(), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
            sa.Column('updated_at', sa.DateTime(timezone=True), nullable=True)
        )
        op.create_index('ix_driver_vehicle_inspections_driver_id', 'driver_vehicle_inspections', ['driver_id'])
        op.create_index('ix_driver_vehicle_inspections_vehicle_id', 'driver_vehicle_inspections', ['vehicle_id'])
        op.create_index('ix_driver_vehicle_inspections_inspection_date', 'driver_vehicle_inspections', ['inspection_date'])
        op.create_index('ix_driver_vehicle_inspections_status', 'driver_vehicle_inspections', ['inspection_status'])
        op.create_index('ix_dvi_driver_date', 'driver_vehicle_inspections', ['driver_id', 'inspection_date'])
        op.create_index('ix_dvi_vehicle_date', 'driver_vehicle_inspections', ['vehicle_id', 'inspection_date'])
    else:
        cols = {c['name'] for c in inspector.get_columns('driver_vehicle_inspections')}
        if 'inspection_date' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('inspection_date', sa.Date(), nullable=True))
            op.create_index('ix_driver_vehicle_inspections_inspection_date', 'driver_vehicle_inspections', ['inspection_date'])
        if 'odometer_reading' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('odometer_reading', sa.Float(), nullable=True))
        if 'odometer_unit' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('odometer_unit', sa.String(16), server_default='KM', nullable=False))
        if 'previous_odometer_reading' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('previous_odometer_reading', sa.Float(), nullable=True))
        if 'overall_result' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('overall_result', sa.String(32), server_default='PENDING', nullable=True))
        if 'failed_items_json' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('failed_items_json', sa.Text(), nullable=True))
        if 'driver_remarks' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('driver_remarks', sa.Text(), nullable=True))
        if 'odometer_photo_url' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('odometer_photo_url', sa.String(512), nullable=True))
        if 'submitted_at' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('submitted_at', sa.DateTime(timezone=True), nullable=True))
        if 'verification_status' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('verification_status', sa.String(32), server_default='PENDING', nullable=False))
        if 'verified_by' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('verified_by', sa.String(36), nullable=True))
        if 'verified_at' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('verified_at', sa.DateTime(timezone=True), nullable=True))
        if 'rejection_reason' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('rejection_reason', sa.Text(), nullable=True))
        if 'created_at' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False))
        if 'updated_at' not in cols:
            op.add_column('driver_vehicle_inspections', sa.Column('updated_at', sa.DateTime(timezone=True), nullable=True))

    # 2. driver_vehicle_inspection_photos table
    if 'driver_vehicle_inspection_photos' not in existing_tables:
        op.create_table(
            'driver_vehicle_inspection_photos',
            sa.Column('id', sa.String(36), primary_key=True),
            sa.Column('inspection_id', sa.String(36), sa.ForeignKey('driver_vehicle_inspections.id', ondelete='CASCADE'), nullable=True),
            sa.Column('driver_id', sa.String(36), nullable=True),
            sa.Column('vehicle_id', sa.String(36), nullable=True),
            sa.Column('photo_category', sa.String(32), server_default='OTHER', nullable=False),
            sa.Column('photo_type', sa.String(32), nullable=True),
            sa.Column('storage_bucket', sa.String(64), server_default='driver-vehicle-inspections', nullable=False),
            sa.Column('storage_path', sa.String(512), nullable=False),
            sa.Column('mime_type', sa.String(100), server_default='image/jpeg', nullable=False),
            sa.Column('file_size_bytes', sa.Integer(), server_default='0', nullable=False),
            sa.Column('file_hash_sha256', sa.String(64), nullable=True),
            sa.Column('width', sa.Integer(), nullable=True),
            sa.Column('height', sa.Integer(), nullable=True),
            sa.Column('captured_at', sa.DateTime(timezone=True), nullable=True),
            sa.Column('uploaded_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=True),
            sa.Column('upload_status', sa.String(32), server_default='COMPLETED', nullable=False),
            sa.Column('verification_status', sa.String(32), server_default='PENDING', nullable=False),
            sa.Column('metadata_json', sa.Text(), nullable=True),
            sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False)
        )
        op.create_index('ix_driver_vehicle_inspection_photos_inspection_id', 'driver_vehicle_inspection_photos', ['inspection_id'])
        op.create_index('ix_driver_vehicle_inspection_photos_driver_id', 'driver_vehicle_inspection_photos', ['driver_id'])
        op.create_index('ix_driver_vehicle_inspection_photos_vehicle_id', 'driver_vehicle_inspection_photos', ['vehicle_id'])
        op.create_index('ix_driver_vehicle_inspection_photos_category', 'driver_vehicle_inspection_photos', ['photo_category'])
    else:
        cols = {c['name'] for c in inspector.get_columns('driver_vehicle_inspection_photos')}
        if 'inspection_id' not in cols:
            op.add_column('driver_vehicle_inspection_photos', sa.Column('inspection_id', sa.String(36), sa.ForeignKey('driver_vehicle_inspections.id', ondelete='CASCADE'), nullable=True))
            op.create_index('ix_driver_vehicle_inspection_photos_inspection_id', 'driver_vehicle_inspection_photos', ['inspection_id'])
        if 'photo_category' not in cols:
            op.add_column('driver_vehicle_inspection_photos', sa.Column('photo_category', sa.String(32), server_default='OTHER', nullable=False))
            op.create_index('ix_driver_vehicle_inspection_photos_category', 'driver_vehicle_inspection_photos', ['photo_category'])
        if 'storage_bucket' not in cols:
            op.add_column('driver_vehicle_inspection_photos', sa.Column('storage_bucket', sa.String(64), server_default='driver-vehicle-inspections', nullable=False))
        if 'width' not in cols:
            op.add_column('driver_vehicle_inspection_photos', sa.Column('width', sa.Integer(), nullable=True))
        if 'height' not in cols:
            op.add_column('driver_vehicle_inspection_photos', sa.Column('height', sa.Integer(), nullable=True))
        if 'captured_at' not in cols:
            op.add_column('driver_vehicle_inspection_photos', sa.Column('captured_at', sa.DateTime(timezone=True), nullable=True))
        if 'uploaded_at' not in cols:
            op.add_column('driver_vehicle_inspection_photos', sa.Column('uploaded_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=True))
        if 'upload_status' not in cols:
            op.add_column('driver_vehicle_inspection_photos', sa.Column('upload_status', sa.String(32), server_default='COMPLETED', nullable=False))
        if 'verification_status' not in cols:
            op.add_column('driver_vehicle_inspection_photos', sa.Column('verification_status', sa.String(32), server_default='PENDING', nullable=False))
        if 'metadata_json' not in cols:
            op.add_column('driver_vehicle_inspection_photos', sa.Column('metadata_json', sa.Text(), nullable=True))


def downgrade():
    op.drop_table('driver_vehicle_inspection_photos')
    op.drop_table('driver_vehicle_inspections')
