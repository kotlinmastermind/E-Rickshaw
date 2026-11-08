import json
import re

with open("scripts/extracted_schemas.json", "r", encoding="utf-8") as f:
    schemas = json.load(f)

with open("backend/domain/models/models.py", "r", encoding="utf-8") as f:
    text = f.read()

stubs = re.findall(r"([A-Za-z0-9_]+)\s*=\s*DroppedModelStub", text)

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

matched = {}
unmatched = []

for s in stubs:
    found = None
    for c in candidates(s):
        if c in schemas:
            found = c
            break
    if found:
        matched[s] = found
    else:
        unmatched.append(s)

print(f"Matched stubs to migration tables: {len(matched)}")
print(f"Unmatched stubs ({len(unmatched)}): {unmatched}")
