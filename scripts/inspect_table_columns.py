import glob
import re

files = glob.glob("database/migrations/versions/*.py")
tables_info = {}

for f in files:
    with open(f, "r", encoding="utf-8") as fp:
        content = fp.read()
    
    # find op.create_table blocks
    table_blocks = re.findall(r"op\.create_table\(\s*['\"]([^'\"]+)['\"],(.*?)(?=\n    \w|\n    op\.|\Z)", content, re.DOTALL)
    for tname, block in table_blocks:
        cols = re.findall(r"sa\.Column\(\s*['\"]([^'\"]+)['\"],\s*([^,\)]+)", block)
        tables_info[tname] = [(c[0], c[1].strip()) for c in cols]

for t in [
    "administrative_audit_logs", "payment_webhook_inbox", "idempotency_records",
    "feature_flags", "driver_vehicle_inspections", "driver_vehicle_inspection_photos",
    "driver_training_modules", "driver_training_progress", "corporate_accounts",
    "corporate_invoices", "corporate_travel_policies", "employee_profiles",
    "business_approval_requests", "ride_operational_events", "passenger_occupancy_events",
    "payment_events", "commission_rules", "customer_ride_passes", "ride_passes",
    "ride_pass_redemptions", "driver_dispatch_preferences", "ride_destination_change_requests",
    "driver_safety_preferences", "driver_rating_appeals", "driver_schedule_availabilities",
    "admin_wallet_adjustment_requests", "support_response_templates", "invoice_correction_requests",
    "user_notification_settings", "notification_consent_audits", "notification_delivery_attempts",
    "address_quality_feedback", "geofence_zone_audits"
]:
    print(f"TABLE: {t}")
    for c, typ in tables_info.get(t, []):
        print(f"  - {c}: {typ}")
    print()

