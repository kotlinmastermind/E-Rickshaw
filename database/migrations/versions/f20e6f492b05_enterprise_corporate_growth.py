"""Enterprise, Corporate Accounts, Concierge Portals, Subscriptions, Multi-City & Product Growth."""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.engine.reflection import Inspector

revision = "f20e6f492b05"
down_revision = "f19d5e381a94"
branch_labels = None
depends_on = None

def upgrade() -> None:
    conn = op.get_bind()
    inspector = Inspector.from_engine(conn)
    existing_tables = set(inspector.get_table_names())

    # 476. Corporate Accounts
    if "corporate_accounts" not in existing_tables:
        op.create_table(
            "corporate_accounts",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("company_name", sa.String(128), unique=True, index=True, nullable=False),
            sa.Column("tax_id", sa.String(64), nullable=False),
            sa.Column("billing_email", sa.String(128), nullable=False),
            sa.Column("credit_limit_cents", sa.Integer(), nullable=False, server_default="50000000"),
            sa.Column("balance_due_cents", sa.Integer(), nullable=False, server_default="0"),
            sa.Column("status", sa.String(32), nullable=False, server_default="ACTIVE"),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
            sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )

    # 477 & 480. Employee Profiles
    if "employee_profiles" not in existing_tables:
        op.create_table(
            "employee_profiles",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("corporate_id", sa.String(36), sa.ForeignKey("corporate_accounts.id"), nullable=False, index=True),
            sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False, index=True),
            sa.Column("cost_center", sa.String(64), nullable=False, server_default="GENERAL_OPS"),
            sa.Column("max_ride_fare_cents", sa.Integer(), nullable=False, server_default="250000"),
            sa.Column("requires_approval", sa.Boolean(), nullable=False, server_default="0"),
            sa.Column("status", sa.String(32), nullable=False, server_default="ACTIVE"),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )

    # 478. Corporate Travel Policies
    if "corporate_travel_policies" not in existing_tables:
        op.create_table(
            "corporate_travel_policies",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("corporate_id", sa.String(36), sa.ForeignKey("corporate_accounts.id"), nullable=False, index=True),
            sa.Column("policy_name", sa.String(128), nullable=False),
            sa.Column("allowed_categories_json", sa.Text(), nullable=False, server_default="[\"SEDAN\", \"LUXURY_SUV\", \"EXECUTIVE_LIMO\", \"ULTRA_LUXURY_EV\"]"),
            sa.Column("max_fare_per_ride_cents", sa.Integer(), nullable=False, server_default="500000"),
            sa.Column("curfew_start_time", sa.String(10), nullable=True),
            sa.Column("curfew_end_time", sa.String(10), nullable=True),
            sa.Column("is_active", sa.Boolean(), nullable=False, server_default="1"),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )

    # 479. Corporate Invoices
    if "corporate_invoices" not in existing_tables:
        op.create_table(
            "corporate_invoices",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("invoice_number", sa.String(64), unique=True, index=True, nullable=False),
            sa.Column("corporate_id", sa.String(36), sa.ForeignKey("corporate_accounts.id"), nullable=False, index=True),
            sa.Column("billing_period", sa.String(32), nullable=False),
            sa.Column("total_amount_cents", sa.Integer(), nullable=False),
            sa.Column("status", sa.String(32), nullable=False, server_default="ISSUED"),
            sa.Column("pdf_url", sa.String(512), nullable=False, server_default="/invoices/corp.pdf"),
            sa.Column("due_date", sa.DateTime(timezone=True), nullable=False),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )

    # 489. Business Approval Requests
    if "business_approval_requests" not in existing_tables:
        op.create_table(
            "business_approval_requests",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("ride_id", sa.String(36), sa.ForeignKey("rides.id"), nullable=False, index=True),
            sa.Column("employee_id", sa.String(36), sa.ForeignKey("employee_profiles.id"), nullable=False),
            sa.Column("approver_id", sa.String(36), sa.ForeignKey("users.id"), nullable=True),
            sa.Column("cost_center", sa.String(64), nullable=False),
            sa.Column("project_code", sa.String(64), nullable=True),
            sa.Column("status", sa.String(32), nullable=False, server_default="PENDING"),
            sa.Column("justification", sa.Text(), nullable=False),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )

    # 482, 483, 484. Partner Accounts
    if "partner_accounts" not in existing_tables:
        op.create_table(
            "partner_accounts",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("partner_type", sa.String(32), nullable=False, server_default="HOTEL"),
            sa.Column("partner_name", sa.String(128), unique=True, index=True, nullable=False),
            sa.Column("api_key_hash", sa.String(64), nullable=False),
            sa.Column("commission_rate_pct", sa.Float(), nullable=False, server_default="10.0"),
            sa.Column("referral_code", sa.String(32), unique=True, nullable=False),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )

    # 481, 483, 484. Partner Bookings
    if "partner_bookings" not in existing_tables:
        op.create_table(
            "partner_bookings",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("partner_id", sa.String(36), sa.ForeignKey("partner_accounts.id"), nullable=False, index=True),
            sa.Column("ride_id", sa.String(36), sa.ForeignKey("rides.id"), nullable=False, index=True),
            sa.Column("guest_name", sa.String(128), nullable=False),
            sa.Column("guest_phone", sa.String(32), nullable=False),
            sa.Column("commission_earned_cents", sa.Integer(), nullable=False, server_default="0"),
            sa.Column("attribution_status", sa.String(32), nullable=False, server_default="ATTRIBUTED"),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )

    # 485. Event Transport Planning Workspace
    if "event_transport_workspaces" not in existing_tables:
        op.create_table(
            "event_transport_workspaces",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("event_name", sa.String(128), nullable=False),
            sa.Column("venue_address", sa.String(255), nullable=False),
            sa.Column("event_date", sa.DateTime(timezone=True), nullable=False),
            sa.Column("allocated_vehicles_count", sa.Integer(), nullable=False, server_default="10"),
            sa.Column("total_guests", sa.Integer(), nullable=False, server_default="100"),
            sa.Column("status", sa.String(32), nullable=False, server_default="PLANNING"),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )

    # 486 & 487. Fleet Subscription Plans
    if "fleet_subscription_plans" not in existing_tables:
        op.create_table(
            "fleet_subscription_plans",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("plan_code", sa.String(32), unique=True, index=True, nullable=False),
            sa.Column("name", sa.String(128), nullable=False),
            sa.Column("monthly_price_cents", sa.Integer(), nullable=False),
            sa.Column("included_rides_count", sa.Integer(), nullable=False),
            sa.Column("category_code", sa.String(32), nullable=False, server_default="SEDAN"),
            sa.Column("is_active", sa.Boolean(), nullable=False, server_default="1"),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )

    # 486. Customer Subscriptions
    if "customer_subscriptions" not in existing_tables:
        op.create_table(
            "customer_subscriptions",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("plan_id", sa.String(36), sa.ForeignKey("fleet_subscription_plans.id"), nullable=False),
            sa.Column("rides_remaining", sa.Integer(), nullable=False),
            sa.Column("starts_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
            sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("status", sa.String(32), nullable=False, server_default="ACTIVE"),
        )

    # 488. Prepaid Ride Bundles
    if "prepaid_ride_bundles" not in existing_tables:
        op.create_table(
            "prepaid_ride_bundles",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("bundle_code", sa.String(32), unique=True, index=True, nullable=False),
            sa.Column("title", sa.String(128), nullable=False),
            sa.Column("ride_count", sa.Integer(), nullable=False, server_default="10"),
            sa.Column("price_cents", sa.Integer(), nullable=False),
            sa.Column("discount_percentage", sa.Float(), nullable=False, server_default="15.0"),
            sa.Column("is_active", sa.Boolean(), nullable=False, server_default="1"),
        )

    # 490. Customer Loyalty Partner Catalog
    if "loyalty_partner_catalog" not in existing_tables:
        op.create_table(
            "loyalty_partner_catalog",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("partner_name", sa.String(128), nullable=False),
            sa.Column("offer_title", sa.String(255), nullable=False),
            sa.Column("required_points", sa.Integer(), nullable=False, server_default="500"),
            sa.Column("promo_code", sa.String(32), nullable=False),
            sa.Column("logo_url", sa.String(512), nullable=False, server_default="https://images.unsplash.com/photo-1563720223185-11003d516935"),
            sa.Column("is_active", sa.Boolean(), nullable=False, server_default="1"),
        )

    # 491. Airport Transfer Package Catalog
    if "airport_transfer_packages" not in existing_tables:
        op.create_table(
            "airport_transfer_packages",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("airport_code", sa.String(10), index=True, nullable=False),
            sa.Column("package_title", sa.String(128), nullable=False),
            sa.Column("category_code", sa.String(32), nullable=False, server_default="EXECUTIVE_LIMO"),
            sa.Column("flat_fare_cents", sa.Integer(), nullable=False),
            sa.Column("meets_and_greets_included", sa.Boolean(), nullable=False, server_default="1"),
        )

    # 492. Intercity Ride Product
    if "intercity_products" not in existing_tables:
        op.create_table(
            "intercity_products",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("route_name", sa.String(128), nullable=False),
            sa.Column("origin_city", sa.String(64), nullable=False),
            sa.Column("destination_city", sa.String(64), nullable=False),
            sa.Column("distance_km", sa.Float(), nullable=False),
            sa.Column("fixed_fare_cents", sa.Integer(), nullable=False),
            sa.Column("toll_included", sa.Boolean(), nullable=False, server_default="1"),
        )

    # 493. Hourly Chauffeur Booking
    if "hourly_chauffeur_bookings" not in existing_tables:
        op.create_table(
            "hourly_chauffeur_bookings",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("customer_id", sa.String(36), sa.ForeignKey("customer_profiles.id"), nullable=False, index=True),
            sa.Column("ride_id", sa.String(36), sa.ForeignKey("rides.id"), nullable=True),
            sa.Column("requested_hours", sa.Integer(), nullable=False, server_default="4"),
            sa.Column("hourly_rate_cents", sa.Integer(), nullable=False, server_default="150000"),
            sa.Column("kilometers_included", sa.Integer(), nullable=False, server_default="40"),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )

    # 494. Chauffeur Service Add-ons
    if "chauffeur_service_addons" not in existing_tables:
        op.create_table(
            "chauffeur_service_addons",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("addon_code", sa.String(32), unique=True, index=True, nullable=False),
            sa.Column("title", sa.String(128), nullable=False),
            sa.Column("price_cents", sa.Integer(), nullable=False, server_default="20000"),
            sa.Column("description", sa.Text(), nullable=False, server_default=""),
            sa.Column("category_compatibility", sa.String(255), nullable=False, server_default="ALL"),
        )

    # 495. White-Label Tenant Configuration
    if "white_label_tenant_configs" not in existing_tables:
        op.create_table(
            "white_label_tenant_configs",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("tenant_code", sa.String(32), unique=True, index=True, nullable=False),
            sa.Column("brand_name", sa.String(128), nullable=False),
            sa.Column("primary_color", sa.String(10), nullable=False, server_default="#D4AF37"),
            sa.Column("secondary_color", sa.String(10), nullable=False, server_default="#0B0F17"),
            sa.Column("custom_domain", sa.String(128), nullable=False),
            sa.Column("logo_url", sa.String(512), nullable=False, server_default="https://grandchauffeur.com/logo.png"),
        )

    # 496 & 497. Multi-City Operating Configuration & Taxes
    if "multi_city_operating_configs" not in existing_tables:
        op.create_table(
            "multi_city_operating_configs",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("city_code", sa.String(32), unique=True, index=True, nullable=False),
            sa.Column("city_name", sa.String(128), nullable=False),
            sa.Column("country", sa.String(64), nullable=False, server_default="India"),
            sa.Column("timezone", sa.String(64), nullable=False, server_default="Asia/Kolkata"),
            sa.Column("tax_rate_percentage", sa.Float(), nullable=False, server_default="5.0"),
            sa.Column("min_fare_cents", sa.Integer(), nullable=False, server_default="100000"),
            sa.Column("is_active", sa.Boolean(), nullable=False, server_default="1"),
        )

    # 498. Experiment & A/B Test Framework
    if "ab_test_experiments" not in existing_tables:
        op.create_table(
            "ab_test_experiments",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("experiment_key", sa.String(64), unique=True, index=True, nullable=False),
            sa.Column("name", sa.String(128), nullable=False),
            sa.Column("description", sa.Text(), nullable=False, server_default=""),
            sa.Column("variants_json", sa.Text(), nullable=False, server_default="[\"control\", \"variant_a\"]"),
            sa.Column("rollout_status", sa.String(32), nullable=False, server_default="RUNNING"),
        )

    # 499. Customer Feature Request Voting
    if "feature_request_votes" not in existing_tables:
        op.create_table(
            "feature_request_votes",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("feature_key", sa.String(64), unique=True, index=True, nullable=False),
            sa.Column("title", sa.String(128), nullable=False),
            sa.Column("description", sa.Text(), nullable=False, server_default=""),
            sa.Column("category", sa.String(64), nullable=False, server_default="BESPOKE_AMENITIES"),
            sa.Column("upvote_count", sa.Integer(), nullable=False, server_default="1"),
            sa.Column("status", sa.String(32), nullable=False, server_default="UNDER_CONSIDERATION"),
        )

    # 500. Product Feedback Analytics
    if "product_feedback_analytics" not in existing_tables:
        op.create_table(
            "product_feedback_analytics",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("user_id", sa.String(36), sa.ForeignKey("users.id"), nullable=False),
            sa.Column("rating_score", sa.Integer(), nullable=False, server_default="5"),
            sa.Column("feedback_category", sa.String(64), nullable=False, server_default="CHAUFFEUR_SERVICE"),
            sa.Column("comments", sa.Text(), nullable=False),
            sa.Column("sentiment", sa.String(20), nullable=False, server_default="POSITIVE"),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )

    # Dynamic Localization Catalog
    if "localization_catalog" not in existing_tables:
        op.create_table(
            "localization_catalog",
            sa.Column("id", sa.String(36), primary_key=True),
            sa.Column("string_key", sa.String(128), index=True, nullable=False),
            sa.Column("locale", sa.String(10), index=True, nullable=False, server_default="en"),
            sa.Column("category", sa.String(32), nullable=False, server_default="COMMON"),
            sa.Column("translated_text", sa.Text(), nullable=False),
            sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        )

def downgrade() -> None:
    pass
