import json

with open("scripts/sync_missing_columns_extracted.json", "r", encoding="utf-8") as f:
    table_cols = json.load(f)

var_to_table = {
    "user": "users",
    "profile": "customer_profiles",
    "customer_profile": "customer_profiles",
    "driver_profile": "driver_profiles",
    "ride": "rides",
    "category": "vehicle_categories",
    "driver_doc": "driver_documents",
    "vehicle": "vehicles",
    "chat": "ride_chat_messages",
    "stop": "ride_stops",
    "fare_rule": "fare_rules",
    "estimate": "fare_estimates",
    "wallet": "wallets",
    "payment": "payments",
    "pooled_booking": "pooled_bookings",
    "driver_loc": "driver_locations",
    "saved_pin": "saved_location_pins",
    "support": "support_tickets",
    "support_msg": "support_messages",
    "payout": "driver_payout_requests",
    "broadcast": "broadcast_notifications",
    "ride_assignment": "ride_assignments",
    "driver_rating": "driver_ratings",
    "device_token": "device_tokens",
    "notification": "notifications",
    "payment_refund": "payment_refunds"
}

aggregated = {}
for var, cols in table_cols.items():
    tname = var_to_table.get(var, var)
    aggregated.setdefault(tname, {})
    for cname, ctype in cols:
        aggregated[tname][cname] = ctype

out_lines = [
    '"""Comprehensive schema reconciliation and persistent entity migration.',
    '',
    'Revision ID: f23a1b2c3d4e',
    'Revises: f22a1b2c3d4e',
    'Create Date: 2026-11-08 21:30:00',
    '"""',
    'from alembic import op',
    'import sqlalchemy as sa',
    '',
    'revision = "f23a1b2c3d4e"',
    'down_revision = "f22a1b2c3d4e"',
    'branch_labels = None',
    'depends_on = None',
    '',
    '',
    'def upgrade() -> None:',
    '    bind = op.get_bind()',
    '    insp = sa.inspect(bind) if not getattr(op.get_context(), "as_sql", False) else None',
    '    tables = set(insp.get_table_names()) if insp else set()',
    ''
]

def to_sa_col(cname, sql_t):
    st = sql_t.upper()
    nullable = "NOT NULL" in st
    is_null_str = "nullable=False" if nullable else "nullable=True"
    
    server_default = None
    if "DEFAULT" in st:
        parts = st.split("DEFAULT")
        server_default = parts[1].strip()
        st = parts[0].strip()
    
    if "VARCHAR" in st:
        import re
        m = re.search(r"\d+", st)
        length = m.group(0) if m else "255"
        base_t = f"sa.String(length={length})"
    elif "TEXT" in st:
        base_t = "sa.Text()"
    elif "INTEGER" in st:
        base_t = "sa.Integer()"
    elif "FLOAT" in st or "DOUBLE" in st:
        base_t = "sa.Float()"
    elif "BOOLEAN" in st:
        base_t = "sa.Boolean()"
    elif "TIMESTAMP" in st or "DATETIME" in st:
        base_t = "sa.DateTime(timezone=True)"
    else:
        base_t = "sa.Text()"
    
    s_def_str = f', server_default={repr(server_default)}' if server_default else ""
    return f"sa.Column('{cname}', {base_t}, {is_null_str}{s_def_str})"

# 1. New Tables Creation
new_tables_code = [
    """    # 1. Account Deletion Requests
    if "account_deletion_requests" not in tables:
        op.create_table(
            "account_deletion_requests",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False, index=True),
            sa.Column("status", sa.String(32), nullable=False, server_default="PENDING_GRACE_PERIOD", index=True),
            sa.Column("cooldown_days_total", sa.Integer(), nullable=False, server_default="30"),
            sa.Column("requested_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("scheduled_purge_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("cancellation_reason", sa.Text(), nullable=True),
            sa.Column("cancelled_at", sa.DateTime(timezone=True), nullable=True),
        )""",

    """    # 2. Data Export Archives
    if "data_export_archives" not in tables:
        op.create_table(
            "data_export_archives",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False, index=True),
            sa.Column("status", sa.String(32), nullable=False, server_default="READY"),
            sa.Column("archive_format", sa.String(16), nullable=False, server_default="JSON"),
            sa.Column("file_size_bytes", sa.Integer(), nullable=False, server_default="0"),
            sa.Column("download_url", sa.String(512), nullable=False),
            sa.Column("payload_json", sa.Text(), nullable=True),
            sa.Column("storage_path", sa.String(512), nullable=True),
            sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )""",

    """    # 3. Recurring Ride Schedules
    if "recurring_ride_schedules" not in tables:
        op.create_table(
            "recurring_ride_schedules",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("frequency", sa.String(32), nullable=False, server_default="WEEKLY"),
            sa.Column("days_of_week", sa.String(64), nullable=False, server_default="MON,TUE,WED,THU,FRI"),
            sa.Column("pickup_time_str", sa.String(10), nullable=False),
            sa.Column("pickup_address", sa.String(255), nullable=False),
            sa.Column("pickup_latitude", sa.Float(), nullable=False),
            sa.Column("pickup_longitude", sa.Float(), nullable=False),
            sa.Column("destination_address", sa.String(255), nullable=False),
            sa.Column("destination_latitude", sa.Float(), nullable=False),
            sa.Column("destination_longitude", sa.Float(), nullable=False),
            sa.Column("vehicle_category_id", sa.String(36), sa.ForeignKey("vehicle_categories.id"), nullable=False),
            sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.true()),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )""",

    """    # 4. Loyalty Points Ledger
    if "loyalty_points_ledger" not in tables:
        op.create_table(
            "loyalty_points_ledger",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("transaction_type", sa.String(50), nullable=False),
            sa.Column("points_change", sa.Integer(), nullable=False),
            sa.Column("balance_after", sa.Integer(), nullable=False),
            sa.Column("reference_id", sa.String(64), nullable=True),
            sa.Column("description", sa.String(255), nullable=True),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )""",

    """    # 5. Customer Milestone Badges
    if "customer_milestone_badges" not in tables:
        op.create_table(
            "customer_milestone_badges",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("badge_code", sa.String(64), nullable=False),
            sa.Column("badge_name", sa.String(128), nullable=False),
            sa.Column("badge_icon", sa.String(512), nullable=True),
            sa.Column("description", sa.Text(), nullable=True),
            sa.Column("unlocked_at", sa.DateTime(timezone=True), nullable=False),
        )""",

    """    # 6. Personalized Offers
    if "personalized_offers" not in tables:
        op.create_table(
            "personalized_offers",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("title", sa.String(128), nullable=False),
            sa.Column("tagline", sa.String(255), nullable=True),
            sa.Column("offer_type", sa.String(64), nullable=False, server_default="DISCOUNT"),
            sa.Column("discount_percent", sa.Float(), nullable=False, server_default="10.0"),
            sa.Column("max_discount_cents", sa.Integer(), nullable=False, server_default="10000"),
            sa.Column("promo_code", sa.String(64), nullable=True),
            sa.Column("badge_label", sa.String(64), nullable=True),
            sa.Column("days", sa.Integer(), nullable=False, server_default="7"),
            sa.Column("starts_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("is_claimed", sa.Boolean(), nullable=False, server_default=sa.false()),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )""",

    """    # 7. Service Recovery Credits
    if "service_recovery_credits" not in tables:
        op.create_table(
            "service_recovery_credits",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("ride_id", sa.String(36), sa.ForeignKey("rides.id"), nullable=True),
            sa.Column("credit_amount_cents", sa.Integer(), nullable=False),
            sa.Column("reason_category", sa.String(64), nullable=False),
            sa.Column("notes", sa.Text(), nullable=True),
            sa.Column("issued_by_admin_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
            sa.Column("status", sa.String(32), nullable=False, server_default="ISSUED"),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )""",

    """    # 8. User Preferences
    if "user_preferences" not in tables:
        op.create_table(
            "user_preferences",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), unique=True, nullable=False),
            sa.Column("preferred_language", sa.String(10), nullable=False, server_default="en"),
            sa.Column("marketing_push_opt_in", sa.Boolean(), nullable=False, server_default=sa.true()),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        )""",

    """    # 9. User Saved Phones
    if "user_saved_phones" not in tables:
        op.create_table(
            "user_saved_phones",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False, index=True),
            sa.Column("phone_number", sa.String(20), nullable=False),
            sa.Column("label", sa.String(32), nullable=False, server_default="MOBILE"),
            sa.Column("is_primary", sa.Boolean(), nullable=False, server_default=sa.false()),
            sa.Column("is_verified", sa.Boolean(), nullable=False, server_default=sa.false()),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )""",

    """    # 10. Privacy Data Subject Requests
    if "privacy_data_subject_requests" not in tables:
        op.create_table(
            "privacy_data_subject_requests",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("request_number", sa.String(64), unique=True, index=True, nullable=False),
            sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False, index=True),
            sa.Column("user_role", sa.String(20), nullable=False, server_default="CUSTOMER"),
            sa.Column("request_type", sa.String(64), nullable=False),
            sa.Column("details", sa.Text(), nullable=True),
            sa.Column("status", sa.String(32), nullable=False, server_default="UNDER_PROCESSING"),
            sa.Column("resolution_notes", sa.Text(), nullable=True),
            sa.Column("assigned_dpo_admin_id", sa.String(36), sa.ForeignKey("users.id"), nullable=True),
            sa.Column("fulfilled_at", sa.DateTime(timezone=True), nullable=True),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )""",

    """    # 11. Customer Surveys
    if "customer_surveys" not in tables:
        op.create_table(
            "customer_surveys",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("ride_id", sa.String(36), sa.ForeignKey("rides.id"), nullable=True),
            sa.Column("survey_type", sa.String(64), nullable=False, server_default="POST_RIDE"),
            sa.Column("score", sa.Integer(), nullable=False, server_default="5"),
            sa.Column("dimensions_json", sa.Text(), nullable=False, server_default="{}"),
            sa.Column("comments", sa.Text(), nullable=True),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        )"""
]

out_lines.extend(new_tables_code)
out_lines.append('')
out_lines.append('    # 2. Add Missing Columns to Existing Tables')

for tname, cols in sorted(aggregated.items()):
    out_lines.append(f'    # --- Table: {tname} ---')
    out_lines.append(f'    if "{tname}" in tables:')
    out_lines.append(f'        existing_cols = {{c["name"] for c in insp.get_columns("{tname}")}} if insp else set()')
    for cname, sql_t in sorted(cols.items()):
        col_def = to_sa_col(cname, sql_t)
        out_lines.append(f'        if "{cname}" not in existing_cols:')
        out_lines.append(f'            op.add_column("{tname}", {col_def})')
    out_lines.append('')

out_lines.append('def downgrade() -> None:')
out_lines.append('    pass')
out_lines.append('')

with open("database/migrations/versions/f23a1b2c3d4e_comprehensive_schema_synchronization.py", "w", encoding="utf-8") as fp:
    fp.write("\n".join(out_lines))

print("Regenerated database/migrations/versions/f23a1b2c3d4e_comprehensive_schema_synchronization.py")
