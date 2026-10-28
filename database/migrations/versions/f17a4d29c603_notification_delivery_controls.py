"""Notification preferences, consent, delivery queue, receipts, and templates."""
from uuid import uuid4, uuid5, NAMESPACE_URL

from alembic import op
import sqlalchemy as sa

revision = "f17a4d29c603"
down_revision = "f15b351d7c20"
branch_labels = None
depends_on = None

PERMISSIONS = [
    ("admin.notifications.read", "View notification delivery, failure and consent records", "Notifications"),
    ("admin.notifications.templates.manage", "Manage and preview notification templates", "Notifications"),
    ("admin.notifications.replay", "Replay eligible failed notification events", "Notifications"),
]


def upgrade() -> None:
    op.add_column("device_tokens", sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.true()))
    op.add_column("device_tokens", sa.Column("last_error_at", sa.DateTime(timezone=True), nullable=True))
    for column in [
        sa.Column("channel", sa.String(16), nullable=False, server_default="IN_APP"),
        sa.Column("critical", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("dedupe_key", sa.String(160), nullable=True),
        sa.Column("action_url", sa.String(512), nullable=True),
        sa.Column("scheduled_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("next_attempt_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("attempt_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("last_attempt_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("last_error", sa.String(500), nullable=True),
    ]:
        op.add_column("notifications", column)
    op.create_index("ix_notifications_next_attempt_at", "notifications", ["next_attempt_at"])
    op.create_unique_constraint("uq_notifications_dedupe_key", "notifications", ["dedupe_key"])

    op.create_table(
        "user_notification_settings",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False, unique=True),
        sa.Column("push_enabled", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("email_enabled", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("sms_enabled", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("quiet_hours_enabled", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("quiet_start", sa.String(5), nullable=False, server_default="22:00"),
        sa.Column("quiet_end", sa.String(5), nullable=False, server_default="07:00"),
        sa.Column("timezone_name", sa.String(64), nullable=False, server_default="Asia/Kolkata"),
        sa.Column("language_code", sa.String(10), nullable=False, server_default="en"),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_table(
        "notification_consent_audits",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("source", sa.String(32), nullable=False),
        sa.Column("changes_json", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_notification_consent_audits_user_id", "notification_consent_audits", ["user_id"])
    op.create_index("ix_notification_consent_audits_created_at", "notification_consent_audits", ["created_at"])
    op.create_table(
        "notification_delivery_attempts",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("notification_id", sa.String(36), sa.ForeignKey("notifications.id", ondelete="CASCADE"), nullable=False),
        sa.Column("channel", sa.String(16), nullable=False),
        sa.Column("attempt_number", sa.Integer(), nullable=False),
        sa.Column("status", sa.String(20), nullable=False),
        sa.Column("provider_message_id", sa.String(128), nullable=True),
        sa.Column("error_code", sa.String(64), nullable=True),
        sa.Column("error_message", sa.String(500), nullable=True),
        sa.Column("attempted_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index("ix_notification_delivery_attempts_notification_id", "notification_delivery_attempts", ["notification_id"])
    op.create_index("ix_notification_delivery_attempts_attempted_at", "notification_delivery_attempts", ["attempted_at"])
    op.create_table(
        "notification_templates",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("template_key", sa.String(100), nullable=False),
        sa.Column("locale", sa.String(10), nullable=False, server_default="en"),
        sa.Column("channel", sa.String(16), nullable=False, server_default="PUSH"),
        sa.Column("title_template", sa.String(128), nullable=False),
        sa.Column("body_template", sa.Text(), nullable=False),
        sa.Column("required_variables_json", sa.Text(), nullable=False, server_default="[]"),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("updated_by", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.UniqueConstraint("template_key", "locale", "channel", name="uq_notification_template_variant"),
    )
    op.create_index("ix_notification_templates_template_key", "notification_templates", ["template_key"])

    permissions = sa.table("admin_permissions", sa.column("id", sa.String), sa.column("permission_key", sa.String), sa.column("description", sa.String), sa.column("category", sa.String))
    op.bulk_insert(permissions, [{"id": str(uuid5(NAMESPACE_URL, f"grandchauffeur:permission:{key}")), "permission_key": key, "description": description, "category": category} for key, description, category in PERMISSIONS])
    links = sa.table("admin_role_permissions", sa.column("id", sa.String), sa.column("role_id", sa.String), sa.column("permission_id", sa.String))
    founding_role = "f14a326b-9c2e-4d00-8000-000000000001"
    op.bulk_insert(links, [{"id": str(uuid4()), "role_id": founding_role, "permission_id": str(uuid5(NAMESPACE_URL, f"grandchauffeur:permission:{key}"))} for key, _, _ in PERMISSIONS])


def downgrade() -> None:
    op.drop_index("ix_notification_templates_template_key", table_name="notification_templates")
    op.drop_table("notification_templates")
    op.drop_index("ix_notification_delivery_attempts_attempted_at", table_name="notification_delivery_attempts")
    op.drop_index("ix_notification_delivery_attempts_notification_id", table_name="notification_delivery_attempts")
    op.drop_table("notification_delivery_attempts")
    op.drop_index("ix_notification_consent_audits_created_at", table_name="notification_consent_audits")
    op.drop_index("ix_notification_consent_audits_user_id", table_name="notification_consent_audits")
    op.drop_table("notification_consent_audits")
    op.drop_table("user_notification_settings")
    op.drop_constraint("uq_notifications_dedupe_key", "notifications", type_="unique")
    op.drop_index("ix_notifications_next_attempt_at", table_name="notifications")
    for column in ["last_error", "last_attempt_at", "attempt_count", "next_attempt_at", "scheduled_at", "action_url", "dedupe_key", "critical", "channel"]:
        op.drop_column("notifications", column)
    op.drop_column("device_tokens", "last_error_at")
    op.drop_column("device_tokens", "is_active")
