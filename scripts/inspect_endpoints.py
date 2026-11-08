import json

with open('docs/database-integration/api_endpoints.json') as f:
    endpoints = json.load(f)

keywords = ['customer', 'profile', 'shift', 'incident', 'share', 'preference', 'pool', 'safety', 'fare']
for kw in keywords:
    matches = [e for e in endpoints if kw in e['path'].lower()]
    print(f"\n=== Keyword: {kw} ({len(matches)} endpoints) ===")
    for m in matches:
        print(f"  {m['methods']:10} {m['path']}")

