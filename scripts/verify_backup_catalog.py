import os
import re
import subprocess
import psycopg2
from dotenv import load_dotenv

def verify():
    load_dotenv('.env')
    raw_url = os.environ.get('DATABASE_URL_SYNC', '')
    url = raw_url.replace('postgresql+psycopg2://', 'postgresql://')
    conn = psycopg2.connect(url, connect_timeout=10)
    cur = conn.cursor()
    cur.execute("""
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE' 
        ORDER BY table_name;
    """)
    db_tables = set(r[0] for r in cur.fetchall())
    conn.close()

    pg_restore = r"C:\Program Files\PostgreSQL\18\bin\pg_restore.exe"
    dump_path = "database/backups/production_full_backup_866dcc677e2a_20261108_131847.dump"
    res = subprocess.run([pg_restore, "--list", dump_path], capture_output=True, text=True)

    dump_tables = set()
    for l in res.stdout.splitlines():
        m = re.match(r"^\d+;\s+\d+\s+\d+\s+TABLE\s+public\s+([^\s]+)\s+", l)
        if m:
            dump_tables.add(m.group(1))

    print(f"DB public tables count:   {len(db_tables)}")
    print(f"Dump public tables count: {len(dump_tables)}")
    missing_in_dump = db_tables - dump_tables
    extra_in_dump = dump_tables - db_tables
    print("Missing in dump:", missing_in_dump if missing_in_dump else "None (0)")
    print("Extra in dump:  ", extra_in_dump if extra_in_dump else "None (0)")
    assert db_tables == dump_tables, "Mismatch between DB tables and dump tables!"
    print("VERIFICATION RESULT: 100% exact match between production DB tables and dump TOC tables!")

if __name__ == "__main__":
    verify()

