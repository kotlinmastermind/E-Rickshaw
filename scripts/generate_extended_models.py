import json
import re

with open("scripts/extracted_schemas.json", "r", encoding="utf-8") as f:
    schemas = json.load(f)

def candidates(name):
    s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
    snake = re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).lower()
    cands = [snake]
    if snake.endswith("y"):
        cands.append(snake[:-1] + "ies")
    elif snake.endswith("s"):
        cands.append(snake + "es")
    else:
        cands.append(snake + "s")
    if name == "ApiErrorCatalogEntry":
        cands.append("api_error_catalog")
    if name == "DatabaseMigrationVerification":
        cands.append("db_migration_verifications")
    return cands

with open("backend/domain/models/models.py", "r", encoding="utf-8") as f:
    text = f.read()

stubs = re.findall(r"([A-Za-z0-9_]+)\s*=\s*DroppedModelStub", text)

out_lines = [
    'import uuid',
    'import secrets',
    'from datetime import datetime, timezone',
    'from typing import Optional, List',
    'from sqlalchemy import (',
    '    String, Integer, Float, Boolean, DateTime, ForeignKey, Text, Index, UniqueConstraint',
    ')',
    'from sqlalchemy.orm import Mapped, mapped_column, relationship',
    'from backend.infrastructure.database.session import Base',
    'from backend.domain.models.models import generate_uuid, utc_now',
    '',
    '# =====================================================================',
    '# ENTERPRISE PERSISTENT MODELS (REPLACED PLACEHOLDER STUBS)',
    '# All models are genuine SQLAlchemy Declarative entities.',
    '# =====================================================================',
    ''
]

def map_col(cname, stype, extra):
    if cname == "id":
        return "    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)"
    
    st = stype.lower()
    
    # Check FK
    fk_match = re.search(r"sa\.ForeignKey\(['\"]([^'\"]+)['\"]\)", extra)
    fk = f', ForeignKey("{fk_match.group(1)}")' if fk_match else ""
    idx = ", index=True" if "index=True" in extra or "index=True" in st else ""
    uniq = ", unique=True" if "unique=True" in extra or "unique=True" in st else ""
    
    if "string" in st or "varchar" in st:
        len_match = re.search(r"\d+", st)
        length = len_match.group(0) if len_match else "255"
        base_t = f"String({length})"
        py_t = "str"
    elif "text" in st:
        base_t = "Text"
        py_t = "str"
    elif "integer" in st:
        base_t = "Integer"
        py_t = "int"
    elif "float" in st or "numeric" in st:
        base_t = "Float"
        py_t = "float"
    elif "boolean" in st:
        base_t = "Boolean"
        py_t = "bool"
    elif "datetime" in st or "timestamp" in st:
        base_t = "DateTime(timezone=True)"
        py_t = "datetime"
    else:
        base_t = "Text"
        py_t = "str"

    default_str = ""
    if "default=" in extra or "server_default=" in extra:
        d_match = re.search(r"(?:server_)?default=([^\),]+)", extra)
        if d_match:
            d_val = d_match.group(1).strip()
            if d_val == "sa.false()":
                default_str = ", default=False"
            elif d_val == "sa.true()":
                default_str = ", default=True"
            elif d_val in ('"0"', "'0'"):
                default_str = ", default=0" if py_t == "int" else ", default=False"
            elif d_val in ('"1"', "'1'"):
                default_str = ", default=1" if py_t == "int" else ", default=True"
            elif d_val in ("sa.func.now()", "now()"):
                default_str = ", default=utc_now"
            else:
                default_str = f", default={d_val}"
    elif py_t == "datetime" and ("created_at" in cname or "recorded_at" in cname or "received_at" in cname):
        default_str = ", default=utc_now"
    
    is_nullable = "nullable=False" not in extra
    
    if is_nullable:
        return f"    {cname}: Mapped[Optional[{py_t}]] = mapped_column({base_t}{fk}{idx}{uniq}{default_str}, nullable=True)"
    else:
        return f"    {cname}: Mapped[{py_t}] = mapped_column({base_t}{fk}{idx}{uniq}{default_str}, nullable=False)"


for s in stubs:
    found_table = None
    for c in candidates(s):
        if c in schemas:
            found_table = c
            break
    
    if found_table:
        cols = schemas[found_table]["columns"]
        out_lines.append(f"class {s}(Base):")
        out_lines.append(f'    __tablename__ = "{found_table}"')
        out_lines.append('')
        has_id = False
        for cname, ctype, extra in cols:
            if cname == "id":
                has_id = True
            out_lines.append(map_col(cname, ctype, extra))
        if not has_id:
            out_lines.insert(-len(cols), "    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)")
        out_lines.append('')
        out_lines.append('')

custom_models = {
    "AccountDeletionRequest": """class AccountDeletionRequest(Base):
    __tablename__ = "account_deletion_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    status: Mapped[str] = mapped_column(String(32), default="PENDING_GRACE_PERIOD", index=True)
    cooldown_days_total: Mapped[int] = mapped_column(Integer, default=30)
    requested_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    scheduled_purge_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    cancellation_reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    cancelled_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
""",
    "AdminActionJustification": """class AdminActionJustification(Base):
    __tablename__ = "admin_action_justifications"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    admin_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    action_type: Mapped[str] = mapped_column(String(64), nullable=False)
    target_resource: Mapped[str] = mapped_column(String(64), nullable=False)
    target_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    justification_reason: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "CustomReportConfig": """class CustomReportConfig(Base):
    __tablename__ = "custom_report_configs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    domain: Mapped[str] = mapped_column(String(64), default="RIDES")
    selected_metrics_json: Mapped[str] = mapped_column(Text, default="[]")
    selected_dimensions_json: Mapped[str] = mapped_column(Text, default="[]")
    filter_conditions_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    time_grouping: Mapped[str] = mapped_column(String(32), default="DAILY")
    created_by_admin_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "CustomerMilestoneBadge": """class CustomerMilestoneBadge(Base):
    __tablename__ = "customer_milestone_badges"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    badge_code: Mapped[str] = mapped_column(String(64), nullable=False)
    badge_name: Mapped[str] = mapped_column(String(128), nullable=False)
    badge_icon: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    unlocked_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "CustomerSurvey": """class CustomerSurvey(Base):
    __tablename__ = "customer_surveys"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True)
    survey_type: Mapped[str] = mapped_column(String(64), default="POST_RIDE")
    score: Mapped[int] = mapped_column(Integer, default=5)
    dimensions_json: Mapped[str] = mapped_column(Text, default="{}")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "DataExportArchive": """class DataExportArchive(Base):
    __tablename__ = "data_export_archives"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    status: Mapped[str] = mapped_column(String(32), default="READY")
    archive_format: Mapped[str] = mapped_column(String(16), default="JSON")
    file_size_bytes: Mapped[int] = mapped_column(Integer, default=0)
    download_url: Mapped[str] = mapped_column(String(512), nullable=False)
    payload_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    storage_path: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "DataRetentionPolicy": """class DataRetentionPolicy(Base):
    __tablename__ = "data_retention_policies"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    data_category: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    retention_days: Mapped[int] = mapped_column(Integer, default=365)
    auto_purge_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    legal_basis: Mapped[str] = mapped_column(String(128), default="DPDP_ACT_2023")
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)
""",
    "DriverBatteryOptimizationSetting": """class DriverBatteryOptimizationSetting(Base):
    __tablename__ = "driver_battery_optimization_settings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), unique=True, nullable=False)
    mode: Mapped[str] = mapped_column(String(32), default="BALANCED")
    throttle_ping_background: Mapped[bool] = mapped_column(Boolean, default=True)
    high_accuracy_only_when_moving: Mapped[bool] = mapped_column(Boolean, default=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "DriverBlock": """class DriverBlock(Base):
    __tablename__ = "driver_blocks"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True)
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "DriverFinancialSupportRequest": """class DriverFinancialSupportRequest(Base):
    __tablename__ = "driver_financial_support_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    support_type: Mapped[str] = mapped_column(String(64), nullable=False)
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    status: Mapped[str] = mapped_column(String(32), default="PENDING")
    reason: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "DriverOperatingExpense": """class DriverOperatingExpense(Base):
    __tablename__ = "driver_operating_expenses"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    category: Mapped[str] = mapped_column(String(64), nullable=False)
    amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "DriverPayoutReconciliation": """class DriverPayoutReconciliation(Base):
    __tablename__ = "driver_payout_reconciliations"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), nullable=False, index=True)
    period_start: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    period_end: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    total_earned_cents: Mapped[int] = mapped_column(Integer, default=0)
    total_settled_cents: Mapped[int] = mapped_column(Integer, default=0)
    variance_cents: Mapped[int] = mapped_column(Integer, default=0)
    status: Mapped[str] = mapped_column(String(32), default="MATCHED")
    generated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "DriverPayoutSchedule": """class DriverPayoutSchedule(Base):
    __tablename__ = "driver_payout_schedules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    driver_id: Mapped[str] = mapped_column(String(36), ForeignKey("driver_profiles.id"), unique=True, nullable=False)
    schedule_type: Mapped[str] = mapped_column(String(32), default="DAILY")
    min_threshold_cents: Mapped[int] = mapped_column(Integer, default=50000)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "GiftCard": """class GiftCard(Base):
    __tablename__ = "gift_cards"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    code: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    initial_value_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    remaining_value_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    purchased_by_user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    recipient_email: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    recipient_phone: Mapped[Optional[str]] = mapped_column(String(20), nullable=True)
    sender_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    message: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "HelpArticleRating": """class HelpArticleRating(Base):
    __tablename__ = "help_article_ratings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    article_id: Mapped[str] = mapped_column(String(36), ForeignKey("help_articles.id"), nullable=False, index=True)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    was_helpful: Mapped[bool] = mapped_column(Boolean, nullable=False)
    feedback_text: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "HelpArticle": """class HelpArticle(Base):
    __tablename__ = "help_articles"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    slug: Mapped[str] = mapped_column(String(128), unique=True, index=True, nullable=False)
    category: Mapped[str] = mapped_column(String(64), nullable=False)
    content_markdown: Mapped[str] = mapped_column(Text, nullable=False)
    is_published: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)
""",
    "LocationRetentionSetting": """class LocationRetentionSetting(Base):
    __tablename__ = "location_retention_settings"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    retention_days: Mapped[int] = mapped_column(Integer, default=30)
    anonymize_after_days: Mapped[int] = mapped_column(Integer, default=90)
    allow_historical_playback: Mapped[bool] = mapped_column(Boolean, default=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "LoyaltyPointsLedger": """class LoyaltyPointsLedger(Base):
    __tablename__ = "loyalty_points_ledger"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    transaction_type: Mapped[str] = mapped_column(String(50), nullable=False)
    points_change: Mapped[int] = mapped_column(Integer, nullable=False)
    balance_after: Mapped[int] = mapped_column(Integer, nullable=False)
    reference_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    description: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "PersonalizedOffer": """class PersonalizedOffer(Base):
    __tablename__ = "personalized_offers"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    tagline: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    offer_type: Mapped[str] = mapped_column(String(64), default="DISCOUNT")
    discount_percent: Mapped[float] = mapped_column(Float, default=10.0)
    max_discount_cents: Mapped[int] = mapped_column(Integer, default=10000)
    promo_code: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    badge_label: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    days: Mapped[int] = mapped_column(Integer, default=7)
    starts_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    is_claimed: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "PrivacyDataSubjectRequest": """class PrivacyDataSubjectRequest(Base):
    __tablename__ = "privacy_data_subject_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    request_number: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    user_role: Mapped[str] = mapped_column(String(20), default="CUSTOMER")
    request_type: Mapped[str] = mapped_column(String(64), nullable=False)
    details: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    status: Mapped[str] = mapped_column(String(32), default="UNDER_PROCESSING")
    resolution_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    assigned_dpo_admin_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("users.id"), nullable=True)
    fulfilled_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "RecurringRideSchedule": """class RecurringRideSchedule(Base):
    __tablename__ = "recurring_ride_schedules"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    frequency: Mapped[str] = mapped_column(String(32), default="WEEKLY")
    days_of_week: Mapped[str] = mapped_column(String(64), default="MON,TUE,WED,THU,FRI")
    pickup_time_str: Mapped[str] = mapped_column(String(10), nullable=False)
    pickup_address: Mapped[str] = mapped_column(String(255), nullable=False)
    pickup_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    pickup_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_address: Mapped[str] = mapped_column(String(255), nullable=False)
    destination_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    vehicle_category_id: Mapped[str] = mapped_column(String(36), ForeignKey("vehicle_categories.id"), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "ScheduledReportDelivery": """class ScheduledReportDelivery(Base):
    __tablename__ = "scheduled_report_deliveries"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    report_name: Mapped[str] = mapped_column(String(128), nullable=False)
    recipients_json: Mapped[str] = mapped_column(Text, default="[]")
    frequency: Mapped[str] = mapped_column(String(32), default="WEEKLY")
    created_by_admin_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    next_run_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "SensitiveRecordAccessLog": """class SensitiveRecordAccessLog(Base):
    __tablename__ = "sensitive_record_access_logs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    actor_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    record_type: Mapped[str] = mapped_column(String(64), nullable=False)
    record_id: Mapped[str] = mapped_column(String(64), nullable=False)
    purpose: Mapped[str] = mapped_column(String(255), nullable=False)
    accessed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "ServiceIncidentAdvisory": """class ServiceIncidentAdvisory(Base):
    __tablename__ = "service_incident_advisories"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False)
    affected_service: Mapped[str] = mapped_column(String(64), nullable=False)
    severity: Mapped[str] = mapped_column(String(32), default="MEDIUM")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "ServiceRecoveryCredit": """class ServiceRecoveryCredit(Base):
    __tablename__ = "service_recovery_credits"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    ride_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("rides.id"), nullable=True)
    credit_amount_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    reason_category: Mapped[str] = mapped_column(String(64), nullable=False)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    issued_by_admin_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    status: Mapped[str] = mapped_column(String(32), default="ISSUED")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "SupportCallbackRequest": """class SupportCallbackRequest(Base):
    __tablename__ = "support_callback_requests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    ticket_id: Mapped[Optional[str]] = mapped_column(String(36), ForeignKey("support_tickets.id"), nullable=True)
    phone_number: Mapped[str] = mapped_column(String(20), nullable=False)
    subject: Mapped[str] = mapped_column(String(128), nullable=False)
    preferred_time_window: Mapped[str] = mapped_column(String(64), default="IMMEDIATE")
    status: Mapped[str] = mapped_column(String(32), default="PENDING")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "TourismPlace": """class TourismPlace(Base):
    __tablename__ = "tourism_places"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    name: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    latitude: Mapped[float] = mapped_column(Float, nullable=False)
    longitude: Mapped[float] = mapped_column(Float, nullable=False)
    image_url: Mapped[Optional[str]] = mapped_column(String(512), nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "TripTemplate": """class TripTemplate(Base):
    __tablename__ = "trip_templates"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    pickup_address: Mapped[str] = mapped_column(String(255), nullable=False)
    pickup_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    pickup_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_address: Mapped[str] = mapped_column(String(255), nullable=False)
    destination_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    destination_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    intermediate_stops_json: Mapped[Optional[str]] = mapped_column(Text, default="[]")
    cabin_temperature: Mapped[Optional[float]] = mapped_column(Float, default=21.0)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "TrustedDevice": """class TrustedDevice(Base):
    __tablename__ = "trusted_devices"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    device_id: Mapped[str] = mapped_column(String(128), nullable=False)
    device_name: Mapped[str] = mapped_column(String(128), nullable=False)
    device_os: Mapped[str] = mapped_column(String(64), nullable=False)
    is_trusted: Mapped[bool] = mapped_column(Boolean, default=True)
    last_used_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "UserDependent": """class UserDependent(Base):
    __tablename__ = "user_dependents"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    customer_id: Mapped[str] = mapped_column(String(36), ForeignKey("customer_profiles.id"), nullable=False, index=True)
    full_name: Mapped[str] = mapped_column(String(128), nullable=False)
    phone_number: Mapped[str] = mapped_column(String(20), nullable=False)
    relationship: Mapped[str] = mapped_column(String(32), default="FAMILY")
    emergency_notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
""",
    "UserPreferences": """class UserPreferences(Base):
    __tablename__ = "user_preferences"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), unique=True, nullable=False)
    preferred_language: Mapped[str] = mapped_column(String(10), default="en")
    marketing_push_opt_in: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)
""",
    "UserSavedPhone": """class UserSavedPhone(Base):
    __tablename__ = "user_saved_phones"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    phone_number: Mapped[str] = mapped_column(String(20), nullable=False)
    label: Mapped[str] = mapped_column(String(32), default="MOBILE")
    is_primary: Mapped[bool] = mapped_column(Boolean, default=False)
    is_verified: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
"""
}

for name, code in custom_models.items():
    out_lines.append(code)
    out_lines.append('')

with open("backend/domain/models/extended_models.py", "w", encoding="utf-8") as fp:
    fp.write("\n".join(out_lines))

print("Successfully generated backend/domain/models/extended_models.py")
