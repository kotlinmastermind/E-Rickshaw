import re

with open("backend/infrastructure/database/session.py", "r", encoding="utf-8") as f:
    code = f.read()

# Extract the body of sync_missing_columns
fn_match = re.search(r"def sync_missing_columns\(connection\):(.*?)(?=\ndef |\Z)", code, re.DOTALL)
if not fn_match:
    print("Could not find sync_missing_columns")
    exit(1)

body = fn_match.group(1)

# Find all dictionary definitions like pg_..._cols = { ... }
tables_dict = {}
dict_matches = re.finditer(r"pg_(\w+)_cols\s*=\s*\{(.*?)\}", body, re.DOTALL)
for dm in dict_matches:
    var_name = dm.group(1)
    dict_body = dm.group(2)
    entries = re.findall(r'["\'](\w+)["\']\s*:\s*["\']([^"\']+)["\']', dict_body)
    tables_dict[var_name] = entries

print(f"Extracted {len(tables_dict)} dictionary mappings from sync_missing_columns:")
for k, v in tables_dict.items():
    print(f"  {k}: {len(v)} columns")

import json
with open("scripts/sync_missing_columns_extracted.json", "w", encoding="utf-8") as out:
    json.dump(tables_dict, out, indent=2)
print("Saved to scripts/sync_missing_columns_extracted.json")

