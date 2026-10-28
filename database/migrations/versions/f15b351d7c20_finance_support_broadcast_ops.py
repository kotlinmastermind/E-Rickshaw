"""Finance approvals, support operations, webhook replay and campaigns."""
from uuid import uuid4, uuid5, NAMESPACE_URL
from alembic import op
import sqlalchemy as sa

revision = "f15b351d7c20"
down_revision = "f14a326b9c2e"
branch_labels = None
depends_on = None

PERMISSIONS = [
    ("admin.finance.payouts.read", "Review payout requests and exceptions", "Finance"),
    ("admin.finance.payouts.approve", "Approve, reject and settle payouts", "Finance"),
    ("admin.finance.commissions.manage", "Version and preview commission rules", "Finance"),
    ("admin.finance.refunds.read", "Review refund requests and payment exceptions", "Finance"),
    ("admin.finance.refunds.approve", "Approve or reject refund requests", "Finance"),
    ("admin.finance.webhooks.replay", "Replay signature-verified webhook deliveries", "Finance"),
    ("admin.finance.wallet.request", "Request a wallet adjustment", "Finance"),
    ("admin.finance.wallet.read", "View wallet adjustment approvals", "Finance"),
    ("admin.finance.wallet.approve", "Provide a second approval for wallet adjustments", "Finance"),
    ("admin.finance.ledger.export", "Export financial ledger records", "Finance"),
    ("admin.finance.invoices.correct", "Review invoice correction requests", "Finance"),
    ("admin.support.manage", "Assign and update support tickets", "Support"),
    ("admin.support.collaborate", "Read and write internal ticket collaboration", "Support"),
    ("admin.support.policies.manage", "Manage support SLA and escalation policies", "Support"),
    ("admin.support.templates.manage", "Manage saved support responses", "Support"),
    ("admin.broadcasts.manage", "Preview, schedule and send audience broadcasts", "Communications"),
    ("admin.broadcasts.emergency", "Send a service-wide emergency announcement", "Communications"),
    ("admin.broadcasts.optout.read", "Review broadcast opt-out compliance", "Communications"),
]


def upgrade() -> None:
    op.add_column("driver_payout_requests", sa.Column("exception_notes", sa.Text(), nullable=True))
    op.add_column("driver_payout_requests", sa.Column("settlement_reference", sa.String(128), nullable=True))
    op.add_column("commission_rules", sa.Column("approval_status", sa.String(20), nullable=False, server_default="ACTIVE"))
    op.add_column("commission_rules", sa.Column("approval_reason", sa.Text(), nullable=True))
    op.add_column("payment_refunds", sa.Column("approval_status", sa.String(24), nullable=False, server_default="APPROVED"))
    op.add_column("payment_refunds", sa.Column("requested_by_admin_id", sa.String(36), sa.ForeignKey("users.id"), nullable=True))
    op.add_column("payment_refunds", sa.Column("reviewed_by_admin_id", sa.String(36), sa.ForeignKey("users.id"), nullable=True))
    op.add_column("payment_refunds", sa.Column("reviewed_at", sa.DateTime(timezone=True), nullable=True))
    op.add_column("payment_refunds", sa.Column("review_reason", sa.Text(), nullable=True))
    op.add_column("support_tickets", sa.Column("sla_due_at", sa.DateTime(timezone=True), nullable=True))
    op.add_column("support_tickets", sa.Column("last_staff_update_at", sa.DateTime(timezone=True), nullable=True))
    op.add_column("broadcast_notifications", sa.Column("channel", sa.String(20), nullable=False, server_default="IN_APP"))
    op.add_column("broadcast_notifications", sa.Column("audience_filter_json", sa.Text(), nullable=False, server_default="{}"))
    op.add_column("broadcast_notifications", sa.Column("localized_content_json", sa.Text(), nullable=False, server_default="{}"))
    op.add_column("broadcast_notifications", sa.Column("delivery_success_count", sa.Integer(), nullable=False, server_default="0"))
    op.add_column("broadcast_notifications", sa.Column("delivery_failure_count", sa.Integer(), nullable=False, server_default="0"))
    op.add_column("broadcast_notifications", sa.Column("opt_out_count", sa.Integer(), nullable=False, server_default="0"))
    op.add_column("broadcast_notifications", sa.Column("is_emergency", sa.Boolean(), nullable=False, server_default=sa.false()))
    op.add_column("broadcast_notifications", sa.Column("preview_json", sa.Text(), nullable=True))

    op.create_table(
        "admin_wallet_adjustment_requests",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("amount_cents", sa.Integer(), nullable=False),
        sa.Column("adjustment_type", sa.String(12), nullable=False),
        sa.Column("reason", sa.Text(), nullable=False),
        sa.Column("status", sa.String(20), nullable=False, server_default="PENDING"),
        sa.Column("requested_by", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("first_approved_by", sa.String(36), sa.ForeignKey("users.id"), nullable=True),
        sa.Column("second_approved_by", sa.String(36), sa.ForeignKey("users.id"), nullable=True),
        sa.Column("reviewed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("applied_transaction_id", sa.String(36), sa.ForeignKey("wallet_transactions.id"), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_admin_wallet_adjustment_requests_user_id", "admin_wallet_adjustment_requests", ["user_id"])
    op.create_index("ix_admin_wallet_adjustment_requests_status", "admin_wallet_adjustment_requests", ["status"])

    op.create_table(
        "payment_webhook_inbox",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("provider_name", sa.String(32), nullable=False),
        sa.Column("event_digest", sa.String(64), nullable=False, unique=True),
        sa.Column("payload_json", sa.Text(), nullable=False),
        sa.Column("signature_verified", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("status", sa.String(20), nullable=False, server_default="FAILED"),
        sa.Column("attempts", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("last_error", sa.String(500), nullable=True),
        sa.Column("received_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("last_attempt_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index("ix_payment_webhook_inbox_event_digest", "payment_webhook_inbox", ["event_digest"])
    op.create_index("ix_payment_webhook_inbox_status", "payment_webhook_inbox", ["status"])

    op.create_table(
        "support_response_templates",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("title", sa.String(100), nullable=False),
        sa.Column("category", sa.String(50), nullable=False, server_default="GENERAL"),
        sa.Column("language_code", sa.String(10), nullable=False, server_default="en"),
        sa.Column("body", sa.Text(), nullable=False),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("created_by", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_table(
        "support_escalation_policies",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("name", sa.String(100), nullable=False, unique=True),
        sa.Column("priority", sa.String(16), nullable=False),
        sa.Column("sla_minutes", sa.Integer(), nullable=False),
        sa.Column("escalation_minutes", sa.Integer(), nullable=False),
        sa.Column("target_squad", sa.String(50), nullable=False),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("updated_by", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_table(
        "invoice_correction_requests",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("ride_id", sa.String(36), sa.ForeignKey("rides.id"), nullable=False),
        sa.Column("requested_changes_json", sa.Text(), nullable=False),
        sa.Column("original_values_json", sa.Text(), nullable=False),
        sa.Column("reason", sa.Text(), nullable=False),
        sa.Column("status", sa.String(20), nullable=False, server_default="PENDING"),
        sa.Column("requested_by", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("reviewed_by", sa.String(36), sa.ForeignKey("users.id"), nullable=True),
        sa.Column("reviewed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("review_reason", sa.Text(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_invoice_correction_requests_ride_id", "invoice_correction_requests", ["ride_id"])
    op.create_index("ix_invoice_correction_requests_status", "invoice_correction_requests", ["status"])
    op.create_table(
        "broadcast_deliveries",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("broadcast_id", sa.String(36), sa.ForeignKey("broadcast_notifications.id", ondelete="CASCADE"), nullable=False),
        sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("notification_id", sa.String(36), sa.ForeignKey("notifications.id"), nullable=True),
        sa.Column("status", sa.String(20), nullable=False),
        sa.Column("language_code", sa.String(10), nullable=False, server_default="en"),
        sa.Column("failure_reason", sa.String(255), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.UniqueConstraint("broadcast_id", "user_id", name="uq_broadcast_delivery_user"),
    )
    op.create_index("ix_broadcast_deliveries_broadcast_id", "broadcast_deliveries", ["broadcast_id"])
    op.create_index("ix_broadcast_deliveries_user_id", "broadcast_deliveries", ["user_id"])

    permissions = sa.table("admin_permissions", sa.column("id", sa.String), sa.column("permission_key", sa.String), sa.column("description", sa.String), sa.column("category", sa.String))
    op.bulk_insert(permissions, [{"id": str(uuid5(NAMESPACE_URL, f"grandchauffeur:permission:{key}")), "permission_key": key, "description": description, "category": category} for key, description, category in PERMISSIONS])
    links = sa.table("admin_role_permissions", sa.column("id", sa.String), sa.column("role_id", sa.String), sa.column("permission_id", sa.String))
    founding_role = "f14a326b-9c2e-4d00-8000-000000000001"
    op.bulk_insert(links, [{"id": str(uuid4()), "role_id": founding_role, "permission_id": str(uuid5(NAMESPACE_URL, f"grandchauffeur:permission:{key}"))} for key, _, _ in PERMISSIONS])


def downgrade() -> None:
    op.drop_index("ix_broadcast_deliveries_user_id", table_name="broadcast_deliveries")
    op.drop_index("ix_broadcast_deliveries_broadcast_id", table_name="broadcast_deliveries")
    op.drop_table("broadcast_deliveries")
    op.drop_index("ix_invoice_correction_requests_status", table_name="invoice_correction_requests")
    op.drop_index("ix_invoice_correction_requests_ride_id", table_name="invoice_correction_requests")
    op.drop_table("invoice_correction_requests")
    op.drop_table("support_escalation_policies")
    op.drop_table("support_response_templates")
    op.drop_index("ix_payment_webhook_inbox_status", table_name="payment_webhook_inbox")
    op.drop_index("ix_payment_webhook_inbox_event_digest", table_name="payment_webhook_inbox")
    op.drop_table("payment_webhook_inbox")
    op.drop_index("ix_admin_wallet_adjustment_requests_status", table_name="admin_wallet_adjustment_requests")
    op.drop_index("ix_admin_wallet_adjustment_requests_user_id", table_name="admin_wallet_adjustment_requests")
    op.drop_table("admin_wallet_adjustment_requests")
    for table, column in [
        ("broadcast_notifications", "preview_json"), ("broadcast_notifications", "is_emergency"),
        ("broadcast_notifications", "opt_out_count"), ("broadcast_notifications", "delivery_failure_count"),
        ("broadcast_notifications", "delivery_success_count"), ("broadcast_notifications", "localized_content_json"),
        ("broadcast_notifications", "audience_filter_json"), ("broadcast_notifications", "channel"),
        ("support_tickets", "last_staff_update_at"), ("support_tickets", "sla_due_at"),
        ("payment_refunds", "review_reason"), ("payment_refunds", "reviewed_at"),
        ("payment_refunds", "reviewed_by_admin_id"), ("payment_refunds", "requested_by_admin_id"),
        ("payment_refunds", "approval_status"), ("commission_rules", "approval_reason"),
        ("commission_rules", "approval_status"), ("driver_payout_requests", "settlement_reference"),
        ("driver_payout_requests", "exception_notes"),
    ]:
        op.drop_column(table, column)
    # Permission records/links are intentionally retained if administrators created roles from them.
