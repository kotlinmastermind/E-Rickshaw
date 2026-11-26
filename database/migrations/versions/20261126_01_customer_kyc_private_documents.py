"""add private customer KYC document metadata and access audit trail

Revision ID: 20261126_01
Revises: 20261125_06
Create Date: 2026-11-26
"""
from alembic import op
import sqlalchemy as sa

revision = "20261126_01"
down_revision = "20261125_06"
branch_labels = None
depends_on = None


def _ensure_indexes(table_name: str, indexes: list[tuple[str, list[str]]]) -> None:
    inspector = sa.inspect(op.get_bind())
    existing = {item["name"] for item in inspector.get_indexes(table_name)}
    for name, columns in indexes:
        if name not in existing:
            op.create_index(name, table_name, columns)


def upgrade() -> None:
    # Revision 0001 uses Base.metadata.create_all(). On a fresh install that
    # legacy migration sees current model metadata and may already create these
    # new tables. Existing deployments, however, need this revision to create them.
    bind = op.get_bind()
    existing_tables = set(sa.inspect(bind).get_table_names())

    if "customer_kyc_documents" not in existing_tables:
        op.create_table(
            "customer_kyc_documents",
            sa.Column("id", sa.String(length=36), primary_key=True, nullable=False),
            sa.Column("user_id", sa.String(length=36), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
            sa.Column("document_type", sa.String(length=32), nullable=False),
            sa.Column("storage_path", sa.String(length=512), nullable=False),
            sa.Column("mime_type", sa.String(length=100), nullable=False),
            sa.Column("file_size_bytes", sa.Integer(), nullable=False),
            sa.Column("sha256_hash", sa.String(length=64), nullable=False),
            sa.Column("status", sa.String(length=32), nullable=False, server_default="PENDING_REVIEW"),
            sa.Column("submitted_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
            sa.Column("reviewed_by_admin_id", sa.String(length=36), sa.ForeignKey("users.id", ondelete="SET NULL"), nullable=True),
            sa.Column("reviewed_at", sa.DateTime(timezone=True), nullable=True),
            sa.Column("review_notes", sa.String(length=500), nullable=True),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
            sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
            sa.UniqueConstraint("storage_path", name="uq_customer_kyc_documents_storage_path"),
        )
    _ensure_indexes(
        "customer_kyc_documents",
        [
            ("ix_customer_kyc_documents_user_id", ["user_id"]),
            ("ix_customer_kyc_user_status", ["user_id", "status"]),
            ("ix_customer_kyc_status_submitted", ["status", "submitted_at"]),
        ],
    )
    existing_document_indexes = {
        item["name"] for item in sa.inspect(bind).get_indexes("customer_kyc_documents")
    }
    if "uq_customer_kyc_one_pending_document_type" not in existing_document_indexes:
        op.create_index(
            "uq_customer_kyc_one_pending_document_type",
            "customer_kyc_documents",
            ["user_id", "document_type"],
            unique=True,
            postgresql_where=sa.text("status = 'PENDING_REVIEW'"),
            sqlite_where=sa.text("status = 'PENDING_REVIEW'"),
        )

    existing_tables = set(sa.inspect(bind).get_table_names())
    if "customer_kyc_audit_logs" not in existing_tables:
        op.create_table(
            "customer_kyc_audit_logs",
            sa.Column("id", sa.String(length=36), primary_key=True, nullable=False),
            sa.Column("document_id", sa.String(length=36), sa.ForeignKey("customer_kyc_documents.id", ondelete="SET NULL"), nullable=True),
            sa.Column("actor_id", sa.String(length=36), sa.ForeignKey("users.id", ondelete="RESTRICT"), nullable=False),
            sa.Column("actor_role", sa.String(length=24), nullable=False),
            sa.Column("action", sa.String(length=48), nullable=False),
            sa.Column("reason", sa.String(length=500), nullable=True),
            sa.Column("metadata_json", sa.Text(), nullable=False, server_default="{}"),
            sa.Column("ip_address", sa.String(length=64), nullable=True),
            sa.Column("user_agent", sa.String(length=255), nullable=True),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )
    _ensure_indexes(
        "customer_kyc_audit_logs",
        [
            ("ix_customer_kyc_audit_logs_document_id", ["document_id"]),
            ("ix_customer_kyc_audit_logs_actor_id", ["actor_id"]),
            ("ix_customer_kyc_audit_logs_action", ["action"]),
        ],
    )


def downgrade() -> None:
    op.drop_index("ix_customer_kyc_audit_logs_action", table_name="customer_kyc_audit_logs")
    op.drop_index("ix_customer_kyc_audit_logs_actor_id", table_name="customer_kyc_audit_logs")
    op.drop_index("ix_customer_kyc_audit_logs_document_id", table_name="customer_kyc_audit_logs")
    op.drop_table("customer_kyc_audit_logs")
    op.drop_index("ix_customer_kyc_status_submitted", table_name="customer_kyc_documents")
    op.drop_index("ix_customer_kyc_user_status", table_name="customer_kyc_documents")
    op.drop_index("ix_customer_kyc_documents_user_id", table_name="customer_kyc_documents")
    op.drop_table("customer_kyc_documents")
