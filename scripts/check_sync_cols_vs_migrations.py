import re
import glob

with open("backend/infrastructure/database/session.py", "r", encoding="utf-8") as f:
    session_code = f.read()

# Find dicts like pg_user_cols = { "col": "type" }
dict_matches = re.finditer(r"pg_(\w+)_cols\s*=\s*\{(.*?)\}", session_code, re.DOTALL)
sync_cols = []
for dm in dict_matches:
    tname = dm.group(1) # e.g. user -> users, profile -> customer_profiles
    table = tname
    if tname == "user": table = "users"
    elif tname == "profile": table = "customer_profiles"
    elif tname == "driver": table = "driver_profiles"
    elif tname == "ride": table = "rides"
    elif tname == "session": table = "sessions"
    elif tname == "vehicle": table = "vehicles"
    elif tname == "cat": table = "vehicle_categories"
    
    body = dm.group(2)
    keys = re.findall(r'["\'](\w+)["\']\s*:', body)
    for k in keys:
        sync_cols.append((table, k))

print(f"Total columns extracted from sync_missing_columns dicts: {len(sync_cols)}")

migration_cols = set()
for f in glob.glob("database/migrations/versions/*.py"):
    with open(f, "r", encoding="utf-8") as fp:
        c = fp.read()
    for m in re.finditer(r"op\.add_column\(\s*['\"](\w+)['\"],\s*sa\.Column\(\s*['\"](\w+)['\"]", c):
        migration_cols.add((m.group(1).lower(), m.group(2).lower()))
    table_blocks = re.findall(r"op\.create_table\(\s*['\"](\w+)['\"],(.*?)(?=\n\s*(?:op\.|\Z|def ))", c, re.DOTALL)
    for tname, block in table_blocks:
        for cm in re.finditer(r"sa\.Column\(\s*['\"](\w+)['\"]", block):
            migration_cols.add((tname.lower(), cm.group(1).lower()))

missing_in_migrations = []
for t, col in sync_cols:
    if (t.lower(), col.lower()) not in migration_cols:
        missing_in_migrations.append((t, col))

print(f"Columns in sync_missing_columns missing in Alembic migrations: {len(missing_in_migrations)}")
for t, col in sorted(set(missing_in_migrations)):
    print(f"  Missing: {t}.{col}")
