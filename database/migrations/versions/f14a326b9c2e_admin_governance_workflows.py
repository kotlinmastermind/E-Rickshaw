"""Admin permissions, review workflows, notes and calendar."""
from uuid import uuid4, uuid5, NAMESPACE_URL
from datetime import datetime, timezone
from alembic import op
import sqlalchemy as sa

revision = "f14a326b9c2e"
down_revision = "e9c7a2b641d5"
branch_labels = None
depends_on = None

PERMISSIONS = [
    ("admin.access", "Open the administration console", "Security"),
    ("admin.roles.manage", "Create roles and grant permissions", "Security"),
    ("admin.sessions.manage", "Review and revoke admin sessions", "Security"),
    ("admin.users.read", "Search customer and driver accounts", "Accounts"),
    ("admin.users.suspend", "Suspend customer accounts", "Accounts"),
    ("admin.users.reinstate", "Reinstate customer accounts", "Accounts"),
    ("admin.drivers.read", "Review driver approval queue", "Drivers"),
    ("admin.drivers.approve", "Approve or reject driver applications", "Drivers"),
    ("admin.drivers.documents", "Review driver documents", "Drivers"),
    ("admin.rides.read", "Search rides and view timelines", "Rides"),
    ("admin.rides.assign", "Manually assign a chauffeur", "Rides"),
    ("admin.rides.cancel", "Cancel a ride", "Rides"),
    ("admin.fares.read", "View fare versions", "Pricing"),
    ("admin.fares.draft", "Draft fare changes", "Pricing"),
    ("admin.fares.approve", "Approve fare drafts", "Pricing"),
    ("admin.fares.activate", "Schedule or activate approved fares", "Pricing"),
    ("admin.categories.manage", "Manage vehicle categories", "Fleet"),
    ("admin.zones.manage", "Manage service zones and calendar", "Dispatch"),
    ("admin.calendar.manage", "Manage holiday and event calendar", "Dispatch"),
    ("admin.surge.manage", "Preview and create surge rules", "Dispatch"),
    ("admin.geofences.manage", "Edit service geofences", "Dispatch"),
    ("admin.notes.manage", "Read and write internal record notes", "Accounts"),
    ("admin.bulk.execute", "Execute confirmed customer bulk actions", "Accounts"),
]


def upgrade() -> None:
    op.add_column("sessions", sa.Column("mfa_verified", sa.Boolean(), nullable=False, server_default=sa.false()))
    op.add_column("fare_rules", sa.Column("workflow_status", sa.String(20), nullable=False, server_default="ACTIVE"))
    op.add_column("fare_rules", sa.Column("approved_by", sa.String(36), sa.ForeignKey("users.id"), nullable=True))
    op.add_column("fare_rules", sa.Column("approved_at", sa.DateTime(timezone=True), nullable=True))
    op.add_column("fare_rules", sa.Column("approval_reason", sa.Text(), nullable=True))

    op.create_table(
        "admin_roles",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("name", sa.String(80), nullable=False, unique=True),
        sa.Column("description", sa.String(255), nullable=False, server_default=""),
        sa.Column("is_superuser", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("created_by", sa.String(36), sa.ForeignKey("users.id"), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "admin_permissions",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("permission_key", sa.String(80), nullable=False, unique=True),
        sa.Column("description", sa.String(255), nullable=False),
        sa.Column("category", sa.String(40), nullable=False),
    )
    op.create_table(
        "admin_role_permissions",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("role_id", sa.String(36), sa.ForeignKey("admin_roles.id", ondelete="CASCADE"), nullable=False),
        sa.Column("permission_id", sa.String(36), sa.ForeignKey("admin_permissions.id", ondelete="CASCADE"), nullable=False),
        sa.UniqueConstraint("role_id", "permission_id", name="uq_admin_role_permission"),
    )
    op.create_table(
        "admin_user_roles",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("role_id", sa.String(36), sa.ForeignKey("admin_roles.id", ondelete="CASCADE"), nullable=False),
        sa.Column("assigned_by", sa.String(36), sa.ForeignKey("users.id"), nullable=True),
        sa.Column("assigned_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("user_id", "role_id", name="uq_admin_user_role"),
    )
    op.create_index("ix_admin_user_roles_user_id", "admin_user_roles", ["user_id"])
    op.create_table(
        "admin_record_notes",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("subject_type", sa.String(16), nullable=False),
        sa.Column("subject_id", sa.String(36), nullable=False),
        sa.Column("author_user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("note", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_admin_record_notes_subject_id", "admin_record_notes", ["subject_id"])
    op.create_table(
        "admin_calendar_events",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("title", sa.String(120), nullable=False),
        sa.Column("event_date", sa.DateTime(timezone=True), nullable=False),
        sa.Column("event_type", sa.String(24), nullable=False, server_default="HOLIDAY"),
        sa.Column("zone_id", sa.String(36), sa.ForeignKey("geofence_zones.id"), nullable=True),
        sa.Column("notes", sa.String(500), nullable=True),
        sa.Column("created_by", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_admin_calendar_events_event_date", "admin_calendar_events", ["event_date"])
    op.create_table(
        "admin_review_templates",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("title", sa.String(80), nullable=False),
        sa.Column("reason", sa.String(500), nullable=False),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("created_by", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )

    permissions_table = sa.table("admin_permissions",
        sa.column("id", sa.String), sa.column("permission_key", sa.String),
        sa.column("description", sa.String), sa.column("category", sa.String))
    op.bulk_insert(permissions_table, [
        {"id": str(uuid5(NAMESPACE_URL, f"grandchauffeur:permission:{key}")), "permission_key": key, "description": description, "category": category}
        for key, description, category in PERMISSIONS
    ])

    # Preserve existing admin access while moving every existing administrator
    # onto an explicit permission role. New admins must be assigned separately.
    roles_table = sa.table("admin_roles", sa.column("id", sa.String), sa.column("name", sa.String),
                           sa.column("description", sa.String), sa.column("is_superuser", sa.Boolean),
                           sa.column("created_by", sa.String), sa.column("created_at", sa.DateTime(timezone=True)))
    role_id = "f14a326b-9c2e-4d00-8000-000000000001"
    op.bulk_insert(roles_table, [{"id": role_id, "name": "Founding Administrator",
                                  "description": "Migrated administrator access; reduce permissions with scoped roles.",
                                  "is_superuser": False, "created_by": None,
                                  "created_at": datetime.now(timezone.utc)}])
    role_permissions_table = sa.table("admin_role_permissions", sa.column("id", sa.String),
                                      sa.column("role_id", sa.String), sa.column("permission_id", sa.String))
    op.bulk_insert(role_permissions_table, [{"id": str(uuid4()), "role_id": role_id,
                                             "permission_id": str(uuid5(NAMESPACE_URL, f"grandchauffeur:permission:{key}"))}
                                            for key, _, _ in PERMISSIONS])
    op.execute(sa.text(
        "INSERT INTO admin_user_roles (id, user_id, role_id, assigned_by, assigned_at) "
        f"SELECT id, id, '{role_id}', NULL, CURRENT_TIMESTAMP FROM users WHERE lower(role) = 'admin'"
    ))


def downgrade() -> None:
    op.drop_table("admin_review_templates")
    op.drop_index("ix_admin_calendar_events_event_date", table_name="admin_calendar_events")
    op.drop_table("admin_calendar_events")
    op.drop_index("ix_admin_record_notes_subject_id", table_name="admin_record_notes")
    op.drop_table("admin_record_notes")
    op.drop_index("ix_admin_user_roles_user_id", table_name="admin_user_roles")
    op.drop_table("admin_user_roles")
    op.drop_table("admin_role_permissions")
    op.drop_table("admin_permissions")
    op.drop_table("admin_roles")
    op.drop_column("fare_rules", "approval_reason")
    op.drop_column("fare_rules", "approved_at")
    op.drop_column("fare_rules", "approved_by")
    op.drop_column("fare_rules", "workflow_status")
    op.drop_column("sessions", "mfa_verified")
