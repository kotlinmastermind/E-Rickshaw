import os
import json
import psycopg2
from datetime import datetime, timezone
from dotenv import load_dotenv

def create_backup():
    load_dotenv('.env')
    raw_url = os.environ.get('DATABASE_URL_SYNC', '')
    url = raw_url.replace('postgresql+psycopg2://', 'postgresql://')
    
    conn = psycopg2.connect(url, connect_timeout=15)
    cur = conn.cursor()
    
    cur.execute("SELECT version_num FROM alembic_version;")
    version_row = cur.fetchone()
    current_version = version_row[0] if version_row else None
    
    cur.execute("""
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
        ORDER BY table_name;
    """)
    tables = [r[0] for r in cur.fetchall()]
    
    snapshot = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "alembic_version": current_version,
        "table_count": len(tables),
        "tables": {}
    }
    
    print(f"Creating snapshot of database at Alembic version {current_version} with {len(tables)} tables...", flush=True)
    
    for table in tables:
        # Get column definitions
        cur.execute("""
            SELECT column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = %s
            ORDER BY ordinal_position;
        """, (table,))
        cols = cur.fetchall()
        
        # Get row count
        cur.execute(f'SELECT count(*) FROM "{table}";')
        count = cur.fetchone()[0]
        
        # Get rows
        cur.execute(f'SELECT * FROM "{table}";')
        col_names = [desc[0] for desc in cur.description]
        raw_rows = cur.fetchall()
        
        serialized_rows = []
        for row in raw_rows:
            serialized_row = {}
            for col_name, val in zip(col_names, row):
                if isinstance(val, (datetime,)):
                    serialized_row[col_name] = val.isoformat()
                elif isinstance(val, (bytes, memoryview)):
                    serialized_row[col_name] = val.hex()
                else:
                    serialized_row[col_name] = str(val) if val is not None else None
            serialized_rows.append(serialized_row)
            
        snapshot["tables"][table] = {
            "columns": [{"name": c[0], "type": c[1], "nullable": c[2]} for c in cols],
            "row_count": count,
            "rows": serialized_rows
        }
        if count > 0:
            print(f"  Captured table {table}: {count} rows", flush=True)
        else:
            print(f"  Captured schema for table {table}: 0 rows", flush=True)
            
    os.makedirs("database/backups", exist_ok=True)
    timestamp_str = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    backup_file = f"database/backups/production_backup_{current_version}_{timestamp_str}.json"
    
    with open(backup_file, "w", encoding="utf-8") as f:
        json.dump(snapshot, f, indent=2)
        
    print(f"\nSUCCESS: Production snapshot verified and saved to {backup_file}", flush=True)
    print(f"File size: {os.path.getsize(backup_file)} bytes", flush=True)
    conn.close()
    return backup_file

if __name__ == "__main__":
    create_backup()
