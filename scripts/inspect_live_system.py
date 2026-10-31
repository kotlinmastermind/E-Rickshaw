import urllib.request
import json
import os

def check_openapi():
    url = "https://e-rickshaw-z32z.onrender.com/openapi.json"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode('utf-8'))
    
    paths = data.get("paths", {})
    print(f"Total Live API Endpoints: {len(paths)}")
    
    tags = {}
    for path, methods in paths.items():
        for m, details in methods.items():
            tag = details.get("tags", ["General"])[0]
            if tag not in tags:
                tags[tag] = []
            tags[tag].append((m.upper(), path, details.get("summary", "")))
            
    print(f"Total API Groups / Modules: {len(tags)}")
    for tag, endpoints in sorted(tags.items()):
        print(f"\n--- {tag} ({len(endpoints)} endpoints) ---")
        for m, p, s in endpoints[:5]:
            print(f"  [{m}] {p} - {s}")
        if len(endpoints) > 5:
            print(f"  ... and {len(endpoints) - 5} more")

if __name__ == '__main__':
    check_openapi()

