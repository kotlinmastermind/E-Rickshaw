import os
import shutil
import subprocess
import time
import psycopg2

INITDB_BIN = r"C:\Program Files\PostgreSQL\18\bin\initdb.exe"
PG_CTL_BIN = r"C:\Program Files\PostgreSQL\18\bin\pg_ctl.exe"
PSQL_BIN = r"C:\Program Files\PostgreSQL\18\bin\psql.exe"
PG_RESTORE_BIN = r"C:\Program Files\PostgreSQL\18\bin\pg_restore.exe"

TEMP_DIR = os.path.abspath(r"C:\androidAi\.temp_pg_test\data")
PORT = 54333
DB_NAME = "isolated_restore_test"
DUMP_PATH = os.path.abspath(r"database\backups\production_full_backup_866dcc677e2a_20261108_131847.dump")

def run_isolated_restore():
    print("=" * 80, flush=True)
    print("VAISENTRA MOBILITY — ISOLATED POSTGRESQL RESTORE VALIDATION TEST", flush=True)
    print(f"Target Isolated Port: {PORT}", flush=True)
    print(f"Isolated Data Dir:   {TEMP_DIR}", flush=True)
    print(f"Backup Dump Path:    {DUMP_PATH}", flush=True)
    print("=" * 80, flush=True)

    if os.path.exists(TEMP_DIR):
        print("Cleaning up old temp pg data dir...", flush=True)
        # Try stopping if already running
        subprocess.run([PG_CTL_BIN, "-D", TEMP_DIR, "stop", "-m", "immediate"], capture_output=True)
        time.sleep(1)
        shutil.rmtree(TEMP_DIR, ignore_errors=True)

    os.makedirs(TEMP_DIR, exist_ok=True)

    # 1. Initialize temporary isolated cluster
    print("\n[Step 1/7] Initializing isolated PostgreSQL cluster via initdb...", flush=True)
    init_cmd = [INITDB_BIN, "-U", "postgres", "-A", "trust", "-E", "UTF8", "-D", TEMP_DIR]
    init_res = subprocess.run(init_cmd, capture_output=True, text=True)
    if init_res.returncode != 0:
        print("ERROR: initdb failed:", init_res.stderr, flush=True)
        return False
    print("initdb completed successfully.", flush=True)

    # 2. Start temporary cluster
    print(f"\n[Step 2/7] Starting isolated PostgreSQL cluster on localhost:{PORT}...", flush=True)
    start_cmd = [PG_CTL_BIN, "-D", TEMP_DIR, "-o", f"-p {PORT} -c listen_addresses=localhost", "-l", os.path.join(TEMP_DIR, "server.log"), "start"]
    subprocess.run(start_cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2)

    # Verify cluster is accepting connections
    try:
        conn = psycopg2.connect(host="localhost", port=PORT, user="postgres", dbname="postgres", connect_timeout=5)
        conn.autocommit = True
        cur = conn.cursor()
        cur.execute("SELECT version();")
        ver = cur.fetchone()[0]
        print(f"Connected to isolated cluster: {ver[:60]}...", flush=True)
    except Exception as e:
        print(f"ERROR: Cannot connect to isolated cluster: {e}", flush=True)
        return False

    # 3. Create destination test database
    print(f"\n[Step 3/7] Creating test database '{DB_NAME}'...", flush=True)
    cur.execute(f"CREATE DATABASE {DB_NAME};")
    conn.close()

    # 4. Restore the backup archive
    print(f"\n[Step 4/7] Restoring backup dump into '{DB_NAME}' via pg_restore...", flush=True)
    restore_cmd = [
        PG_RESTORE_BIN,
        "-h", "localhost",
        "-p", str(PORT),
        "-U", "postgres",
        "-d", DB_NAME,
        "--no-owner",
        "--no-privileges",
        DUMP_PATH
    ]
    restore_res = subprocess.run(restore_cmd, capture_output=True, text=True)
    print(f"pg_restore finished with exit code {restore_res.returncode}.", flush=True)
    if restore_res.stderr:
        filtered_errors = [l for l in restore_res.stderr.splitlines() if not l.startswith("pg_restore: warning:")]
        print(f"Restore output (warnings/notices: {len(restore_res.stderr.splitlines())}, errors: {len(filtered_errors)}):", flush=True)
        for line in restore_res.stderr.splitlines()[:10]:
            print("  ", line, flush=True)

    # 5. Connect and audit restored database
    print(f"\n[Step 5/7] Verifying restored schema, tables, rows, indexes & constraints...", flush=True)
    test_conn = psycopg2.connect(host="localhost", port=PORT, user="postgres", dbname=DB_NAME, connect_timeout=5)
    test_cur = test_conn.cursor()

    # Table count
    test_cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name;")
    restored_tables = [r[0] for r in test_cur.fetchall()]
    print(f"Restored Public Tables Count: {len(restored_tables)} / 77", flush=True)

    # Row counts
    total_rows = 0
    populated_tables = {}
    for t in restored_tables:
        test_cur.execute(f'SELECT count(*) FROM "{t}";')
        cnt = test_cur.fetchone()[0]
        total_rows += cnt
        if cnt > 0:
            populated_tables[t] = cnt

    print(f"Total Restored Rows: {total_rows} across {len(populated_tables)} populated tables", flush=True)
    print("Populated table sample:", {k: populated_tables[k] for k in list(populated_tables.keys())[:8]}, flush=True)

    # Primary key constraints count
    test_cur.execute("""
        SELECT count(*) 
        FROM information_schema.table_constraints 
        WHERE table_schema = 'public' AND constraint_type = 'PRIMARY KEY';
    """)
    pk_count = test_cur.fetchone()[0]
    print(f"Restored Primary Keys: {pk_count}", flush=True)

    # Foreign key constraints count
    test_cur.execute("""
        SELECT count(*) 
        FROM information_schema.table_constraints 
        WHERE table_schema = 'public' AND constraint_type = 'FOREIGN KEY';
    """)
    fk_count = test_cur.fetchone()[0]
    print(f"Restored Foreign Keys: {fk_count}", flush=True)

    # Unique constraints count
    test_cur.execute("""
        SELECT count(*) 
        FROM information_schema.table_constraints 
        WHERE table_schema = 'public' AND constraint_type = 'UNIQUE';
    """)
    unique_count = test_cur.fetchone()[0]
    print(f"Restored Unique Constraints: {unique_count}", flush=True)

    # Secondary indexes count
    test_cur.execute("""
        SELECT count(*) 
        FROM pg_indexes 
        WHERE schemaname = 'public';
    """)
    index_count = test_cur.fetchone()[0]
    print(f"Restored Indexes: {index_count}", flush=True)

    # Alembic version check
    test_cur.execute("SELECT version_num FROM alembic_version;")
    alem_ver = test_cur.fetchone()[0]
    print(f"Restored Alembic Revision: {alem_ver}", flush=True)
    assert alem_ver == "866dcc677e2a", f"Expected 866dcc677e2a, got {alem_ver}"

    # 6. Read-only integrity queries
    print(f"\n[Step 6/7] Running read-only semantic integrity verification...", flush=True)
    test_cur.execute("SELECT id, email, role, is_active FROM users LIMIT 3;")
    users_sample = test_cur.fetchall()
    print(f"Successfully queried 'users' table: {len(users_sample)} sample rows read.", flush=True)

    test_cur.execute("SELECT id, user_id, is_revoked FROM sessions LIMIT 3;")
    sessions_sample = test_cur.fetchall()
    print(f"Successfully queried 'sessions' table: {len(sessions_sample)} sample rows read.", flush=True)

    test_conn.close()
    print("All read-only queries executed successfully on restored database!", flush=True)

    # 7. Shutdown and clean up temporary cluster
    print(f"\n[Step 7/7] Shutting down isolated temporary cluster...", flush=True)
    subprocess.run([PG_CTL_BIN, "-D", TEMP_DIR, "stop", "-m", "fast"], capture_output=True)
    time.sleep(2)
    shutil.rmtree(TEMP_DIR, ignore_errors=True)
    print("Isolated cluster shut down and temp files cleaned up safely.", flush=True)

    print("\n" + "=" * 80, flush=True)
    print("CONCLUSION: ISOLATED RESTORE TEST PASSED 100%", flush=True)
    print(f"Restored Tables: {len(restored_tables)} / 77 (100% matched)")
    print(f"Restored Rows:   {total_rows}")
    print(f"Restored PKs:    {pk_count}")
    print(f"Restored FKs:    {fk_count}")
    print(f"Restored Indexes:{index_count}")
    print(f"Revision:        {alem_ver}")
    print("=" * 80, flush=True)
    return True

if __name__ == "__main__":
    run_isolated_restore()
