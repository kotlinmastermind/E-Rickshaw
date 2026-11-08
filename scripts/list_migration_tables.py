import re
import glob
import os

tables = {}
for py_file in glob.glob("database/migrations/versions/*.py"):
    with open(py_file, "r", encoding="utf-8") as f:
        content = f.read()
    matches = re.findall(r"op\.create_table\(\s*['\"]([^'\"]+)['\"]", content)
    for m in matches:
        tables[m] = os.path.basename(py_file)

print(f"Total tables defined across all migrations: {len(tables)}")
for t in sorted(tables.keys()):
    print(f"  {t} -> {tables[t]}")

