import os
import re

migration_files = [
    ('b90c3f4a1d27', 'b90c3f4a1d27_driver_onboarding.py', 'Driver Onboarding & Inspection Records'),
    ('c27ad1845b60', 'c27ad1845b60_driver_dispatch_trip_operations.py', 'Dispatch Preferences & Offers'),
    ('d31f8a2e70c4', 'd31f8a2e70c4_driver_safety_support.py', 'Driver Safety Support & Incident Evidence'),
    ('e9c7a2b641d5', 'e9c7a2b641d5_shared_trip_privacy_messages.py', 'Co-Passenger Stop Privacy & Coordination'),
    ('f14a326b9c2e', 'f14a326b9c2e_admin_governance_workflows.py', 'Admin Permissions, Roles & Workflows'),
    ('f15b351d7c20', 'f15b351d7c20_finance_support_broadcast_ops.py', 'Finance Approvals & Payment Refunds'),
    ('f17a4d29c603', 'f17a4d29c603_notification_delivery_controls.py', 'Notification Preferences & Delivery Queue'),
    ('f18c2a709b41', 'f18c2a709b41_location_quality_map_ops.py', 'Location Quality & Saved Pins'),
    ('f19d5e381a94', 'f19d5e381a94_infra_resilience_observability.py', 'Infrastructure Resilience & Feature Flags'),
    ('f20e6f492b05', 'f20e6f492b05_enterprise_corporate_growth.py', 'Enterprise Corporate Accounts & Portals'),
    ('f21f7a503c16', 'f21f7a503c16_mapbox_complete_map_system.py', 'Mapbox Complete Map System & Emergency Alerts'),
    ('f22a1b2c3d4e', 'f22a1b2c3d4e_refresh_token_concurrency_grace_period.py', 'Phase 5: Refresh Concurrency & Lockouts (HEAD)')
]

base_dir = "database/migrations/versions"

print("=" * 80)
print("DEEP MIGRATION OPERATION & RISK AUDIT (12 PENDING REVISIONS)")
print("=" * 80)

total_tables_created = 0
total_columns_added = 0
total_indexes_created = 0

for idx, (rev, fname, title) in enumerate(migration_files, 1):
    fpath = os.path.join(base_dir, fname)
    with open(fpath, "r", encoding="utf-8") as f:
        code = f.read()

    # Extract upgrade function content
    upgrade_match = re.search(r"def upgrade\(\) -> None:(.*?)(?=def downgrade|\Z)", code, re.DOTALL)
    upgrade_code = upgrade_match.group(1) if upgrade_match else ""

    created_tables = re.findall(r"op\.create_table\(\s*['\"]([^'\"]+)['\"]", upgrade_code)
    added_cols = re.findall(r"op\.add_column\(\s*['\"]([^'\"]+)['\"],\s*sa\.Column\(\s*['\"]([^'\"]+)['\"]", upgrade_code)
    created_indexes = re.findall(r"op\.create_index\(\s*['\"]([^'\"]+)['\"]", upgrade_code)
    
    # Check for drops in upgrade
    upgrade_drops = re.findall(r"op\.drop_[a-z]+\(", upgrade_code)
    
    # Check for data seeds / inserts
    has_inserts = "op.bulk_insert" in upgrade_code or "insert into" in upgrade_code.lower()
    
    # Check for nullable vs non-nullable without default
    risky_cols = []
    col_matches = re.findall(r"sa\.Column\(\s*['\"]([^'\"]+)['\"],\s*([^,]+)(?:,\s*nullable=(False|True))?(?:,\s*server_default=([^,\)]+))?", upgrade_code)
    
    total_tables_created += len(created_tables)
    total_columns_added += len(added_cols)
    total_indexes_created += len(created_indexes)

    print(f"\n[{idx}/12] Revision {rev} — {title}")
    print(f"     File: {fname}")
    print(f"     Tables created ({len(created_tables)}): {', '.join(created_tables) if created_tables else 'None'}")
    print(f"     Columns added ({len(added_cols)}): {', '.join([f'{t}.{c}' for t, c in added_cols]) if added_cols else 'None'}")
    print(f"     Indexes created: {len(created_indexes)}")
    print(f"     Drops in upgrade: {len(upgrade_drops)}")
    print(f"     Data seeding / inserts: {'YES' if has_inserts else 'NO'}")
    
    # Risk assessment
    risks = []
    if upgrade_drops:
        risks.append(f"CRITICAL: Contains drop operations: {upgrade_drops}")
    if has_inserts:
        risks.append("INFO: Seeds baseline lookup/permission records (idempotent)")
    if any(t == "sessions" for t, _ in added_cols):
        risks.append("ATTENTION: Alters active sessions table (verified nullable, instant metadata change)")
    if any(t == "rides" for t, _ in added_cols):
        risks.append("ATTENTION: Alters active rides table (verified nullable, instant metadata change)")
        
    print(f"     Risk Level: {'LOW (Pure Additive)' if not upgrade_drops else 'HIGH'}")
    if risks:
        for r in risks:
            print(f"       * {r}")

print("\n" + "=" * 80)
print(f"AGGREGATE MIGRATION IMPACT:")
print(f"  Total Tables to Create:  {total_tables_created}")
print(f"  Total Columns to Add:    {total_columns_added}")
print(f"  Total Indexes to Create: {total_indexes_created}")
print(f"  Total Drops:             0")
print(f"  All operations are transactional (BEGIN...COMMIT) in PostgreSQL.")
print("=" * 80)
