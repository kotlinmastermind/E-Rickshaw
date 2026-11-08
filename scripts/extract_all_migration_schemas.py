import glob
import re

files = glob.glob("database/migrations/versions/*.py")
tables_schema = {}

for f in sorted(files):
    with open(f, "r", encoding="utf-8") as fp:
        content = fp.read()
    
    # Match create_table
    matches = re.finditer(r"op\.create_table\(\s*['\"]([^'\"]+)['\"],(.*?)(?=\n\s*(?:op\.|\Z|def ))", content, re.DOTALL)
    for m in matches:
        tname = m.group(1)
        body = m.group(2)
        cols = []
        for col_match in re.finditer(r"sa\.Column\(\s*['\"]([^'\"]+)['\"],\s*([^,\)]+)(?:,\s*(.*?))?\)", body, re.DOTALL):
            cname = col_match.group(1)
            ctype = col_match.group(2).strip()
            extra = col_match.group(3) or ""
            cols.append((cname, ctype, extra.strip()))
        tables_schema[tname] = (f, cols)

print(f"Total tables with schemas: {len(tables_schema)}")
import json
with open("scripts/extracted_schemas.json", "w", encoding="utf-8") as out:
    json.dump({k: {"file": v[0], "columns": v[1]} for k, v in tables_schema.items()}, out, indent=2)
print("Saved schemas to scripts/extracted_schemas.json")

