import os
import json
import re

def audit_root_artifacts():
    root_files = os.listdir('.')
    screenshots = []
    xml_dumps = []
    other_temp = []
    
    for f in root_files:
        if os.path.isfile(f):
            if f.endswith('.png'):
                screenshots.append((f, os.path.getsize(f)))
            elif f.endswith('.xml') and ('dump' in f or 'ui' in f or 'cust' in f):
                xml_dumps.append((f, os.path.getsize(f)))
            elif f.endswith(('.log', '.tmp', '.bak', '.orig')):
                other_temp.append((f, os.path.getsize(f)))
                
    scratch_files = []
    if os.path.exists('scratch'):
        for f in os.listdir('scratch'):
            fp = os.path.join('scratch', f)
            if os.path.isfile(fp):
                scratch_files.append((fp, os.path.getsize(fp)))

    uploads_files = []
    if os.path.exists('uploads'):
        for root, _, files in os.walk('uploads'):
            for f in files:
                fp = os.path.join(root, f)
                uploads_files.append((fp, os.path.getsize(fp)))

    return {
        "screenshots": screenshots,
        "xml_dumps": xml_dumps,
        "other_temp": other_temp,
        "scratch_files": scratch_files,
        "uploads_files": uploads_files
    }

def audit_admin_web_dependencies():
    pkg_path = 'apps/admin-web/package.json'
    if not os.path.exists(pkg_path):
        return {}
    with open(pkg_path, 'r', encoding='utf-8') as f:
        pkg = json.load(f)
    deps = list(pkg.get('dependencies', {}).keys())
    dev_deps = list(pkg.get('devDependencies', {}).keys())
    
    # scan imports in src/
    src_dir = 'apps/admin-web/src'
    imported_pkgs = set()
    import_regex = re.compile(r"""(?:from|import)\s+['"]([^'".]+)['"]""")
    
    for root, _, files in os.walk(src_dir):
        for f in files:
            if f.endswith(('.ts', '.tsx', '.js', '.jsx', '.css')):
                with open(os.path.join(root, f), 'r', encoding='utf-8', errors='ignore') as fp:
                    for line in fp:
                        m = import_regex.findall(line)
                        for imp in m:
                            # package root name
                            pkg_name = imp.split('/')[0] if not imp.startswith('@') else '/'.join(imp.split('/')[:2])
                            imported_pkgs.add(pkg_name)

    unused_deps = [d for d in deps if d not in imported_pkgs and not d.startswith('@types')]
    return {
        "declared_deps": deps,
        "imported_pkgs": list(imported_pkgs),
        "potentially_unused_deps": unused_deps,
        "dev_deps": dev_deps
    }

def audit_backend_dependencies():
    req_path = 'backend/requirements.txt'
    if not os.path.exists(req_path):
        return {}
    with open(req_path, 'r', encoding='utf-8') as f:
        lines = [line.strip().split('>=')[0].split('==')[0].split('[')[0].strip() for line in f if line.strip() and not line.startswith('#')]

    # mapping package to import names
    pkg_map = {
        'fastapi': ['fastapi'],
        'uvicorn': ['uvicorn'],
        'pydantic': ['pydantic'],
        'pydantic-settings': ['pydantic_settings'],
        'sqlalchemy': ['sqlalchemy'],
        'greenlet': ['greenlet'],
        'alembic': ['alembic'],
        'aiosqlite': ['aiosqlite'],
        'pyjwt': ['jwt'],
        'bcrypt': ['bcrypt'],
        'httpx': ['httpx'],
        'pytest': ['pytest'],
        'pytest-asyncio': ['pytest_asyncio'],
        'python-multipart': ['multipart'],
        'websockets': ['websockets'],
        'redis': ['redis'],
        'psycopg2-binary': ['psycopg2'],
        'asyncpg': ['asyncpg'],
        'cryptography': ['cryptography']
    }

    import_counts = {pkg: 0 for pkg in lines}
    for root, _, files in os.walk('backend'):
        for f in files:
            if f.endswith('.py'):
                with open(os.path.join(root, f), 'r', encoding='utf-8', errors='ignore') as fp:
                    content = fp.read()
                    for pkg in lines:
                        mod_names = pkg_map.get(pkg, [pkg.replace('-', '_')])
                        for mod in mod_names:
                            if re.search(r'\b(import ' + mod + r'|from ' + mod + r'\b)', content):
                                import_counts[pkg] += 1
                                break

    return {
        "declared": lines,
        "usage_counts": import_counts
    }

def audit_android_resources(app_dir):
    res_dir = os.path.join(app_dir, 'src/main/res')
    java_dir = os.path.join(app_dir, 'src/main/java')
    manifest_path = os.path.join(app_dir, 'src/main/AndroidManifest.xml')
    
    # gather all drawables and mipmaps
    drawables = []
    if os.path.exists(res_dir):
        for d in os.listdir(res_dir):
            if d.startswith(('drawable', 'mipmap')):
                dp = os.path.join(res_dir, d)
                if os.path.isdir(dp):
                    for rf in os.listdir(dp):
                        base = os.path.splitext(rf)[0]
                        drawables.append((base, os.path.join(d, rf)))

    # gather code text
    code_text = ""
    if os.path.exists(java_dir):
        for root, _, files in os.walk(java_dir):
            for f in files:
                if f.endswith('.kt') or f.endswith('.java'):
                    with open(os.path.join(root, f), 'r', encoding='utf-8', errors='ignore') as fp:
                        code_text += fp.read() + " "
    if os.path.exists(manifest_path):
        with open(manifest_path, 'r', encoding='utf-8', errors='ignore') as fp:
            code_text += fp.read() + " "

    # find drawables not mentioned in code
    unique_drawables = list(set([d[0] for d in drawables]))
    unused_drawables = []
    for d in unique_drawables:
        if d not in code_text and not d.startswith('ic_launcher'):
            unused_drawables.append(d)

    return {
        "total_drawable_files": len(drawables),
        "unique_drawables": len(unique_drawables),
        "potentially_unused_drawables": unused_drawables
    }

def main():
    report = {
        "root_artifacts": audit_root_artifacts(),
        "admin_web": audit_admin_web_dependencies(),
        "backend": audit_backend_dependencies(),
        "customer_android": audit_android_resources('apps/customer-android'),
        "driver_android": audit_android_resources('apps/driver-android')
    }
    with open('scratch/deep_audit_results.json', 'w', encoding='utf-8') as f:
        json.dump(report, f, indent=2)
    print("Audit completed successfully. Results saved to scratch/deep_audit_results.json")

if __name__ == '__main__':
    main()

