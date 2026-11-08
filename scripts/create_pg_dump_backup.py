import os
import subprocess
import urllib.parse
from datetime import datetime, timezone
from dotenv import load_dotenv

PG_DUMP_PATH = r"C:\Program Files\PostgreSQL\18\bin\pg_dump.exe"
PG_RESTORE_PATH = r"C:\Program Files\PostgreSQL\18\bin\pg_restore.exe"

def run_backup():
    load_dotenv('.env')
    raw_url = os.environ.get('DATABASE_URL_SYNC', '')
    if not raw_url:
        print("ERROR: DATABASE_URL_SYNC is not set.")
        return False
        
    clean_url = raw_url.replace('postgresql+psycopg2://', 'postgresql://')
    parsed = urllib.parse.urlparse(clean_url)
    
    os.makedirs("database/backups", exist_ok=True)
    timestamp_str = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    output_dump = f"database/backups/production_full_backup_866dcc677e2a_{timestamp_str}.dump"
    
    env = os.environ.copy()
    env["PGHOST"] = parsed.hostname
    env["PGPORT"] = str(parsed.port or 5432)
    env["PGUSER"] = urllib.parse.unquote(parsed.username)
    env["PGPASSWORD"] = urllib.parse.unquote(parsed.password)
    env["PGDATABASE"] = parsed.path.lstrip('/')
    env["PGSSLMODE"] = "require"
    
    cmd = [
        PG_DUMP_PATH,
        "-Fc",
        "-v",
        "--schema=public",
        "-f", output_dump
    ]
    
    print(f"Executing pg_dump to {output_dump} (SSL mode: require)...", flush=True)
    res = subprocess.run(cmd, env=env, capture_output=True, text=True)
    
    if res.returncode != 0:
        print("ERROR: pg_dump failed with code", res.returncode, flush=True)
        # Sanitize any stderr that might have credentials
        stderr_safe = res.stderr.replace(parsed.password, "[REDACTED]") if parsed.password else res.stderr
        print("STDERR:", stderr_safe, flush=True)
        return False
        
    print(f"pg_dump completed successfully. Output file: {output_dump}", flush=True)
    file_size = os.path.getsize(output_dump)
    print(f"Backup file size: {file_size} bytes ({file_size/1024:.2f} KB)", flush=True)
    
    # Verify with pg_restore --list
    print("Verifying backup archive using pg_restore --list...", flush=True)
    restore_cmd = [PG_RESTORE_PATH, "--list", output_dump]
    restore_res = subprocess.run(restore_cmd, capture_output=True, text=True)
    
    if restore_res.returncode != 0:
        print("ERROR: pg_restore verification failed!", flush=True)
        print("STDERR:", restore_res.stderr, flush=True)
        return False
        
    lines = restore_res.stdout.splitlines()
    print(f"pg_restore verification PASSED: Archive TOC contains {len(lines)} entries.", flush=True)
    
    # Count object types in TOC
    object_counts = {}
    for line in lines:
        parts = line.split()
        if len(parts) >= 4:
            desc = parts[3]
            object_counts[desc] = object_counts.get(desc, 0) + 1
            
    print("Archive Content Breakdown by Object Type:", flush=True)
    for obj_type, count in sorted(object_counts.items(), key=lambda x: -x[1]):
        print(f"  {obj_type}: {count}", flush=True)
        
    # Write TOC summary to text file alongside dump
    toc_file = output_dump.replace(".dump", "_toc.txt")
    with open(toc_file, "w", encoding="utf-8") as f:
        f.write(restore_res.stdout)
    print(f"TOC saved to {toc_file}", flush=True)
    
    return output_dump

if __name__ == "__main__":
    run_backup()
