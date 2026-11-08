import os
import shutil
import subprocess
import time
import psycopg2

INITDB_BIN = r"C:\Program Files\PostgreSQL\18\bin\initdb.exe"
PG_CTL_BIN = r"C:\Program Files\PostgreSQL\18\bin\pg_ctl.exe"
PG_RESTORE_BIN = r"C:\Program Files\PostgreSQL\18\bin\pg_restore.exe"

TEMP_DIR = os.path.abspath(r"C:\androidAi\.temp_pg_test\data")
PORT = 54334
DB_NAME = "test_alembic_dryrun"
DUMP_PATH = os.path.abspath(r"database\backups\production_full_backup_866dcc677e2a_20261108_131847.dump")

def test_dryrun_migration():
    if os.path.exists(TEMP_DIR):
        subprocess.run([PG_CTL_BIN, "-D", TEMP_DIR, "stop", "-m", "immediate"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(1)
        shutil.rmtree(TEMP_DIR, ignore_errors=True)

    os.makedirs(TEMP_DIR, exist_ok=True)
    subprocess.run([INITDB_BIN, "-U", "postgres", "-A", "trust", "-E", "UTF8", "-D", TEMP_DIR], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    
    start_cmd = [PG_CTL_BIN, "-D", TEMP_DIR, "-o", f"-p {PORT} -c listen_addresses=localhost", "-l", os.path.join(TEMP_DIR, "server.log"), "start"]
    subprocess.run(start_cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2)

    conn = psycopg2.connect(host="localhost", port=PORT, user="postgres", dbname="postgres")
    conn.autocommit = True
    cur = conn.cursor()
    cur.execute(f"CREATE DATABASE {DB_NAME};")
    conn.close()

    # Restore backup dump
    restore_cmd = [PG_RESTORE_BIN, "-h", "localhost", "-p", str(PORT), "-U", "postgres", "-d", DB_NAME, "--no-owner", "--no-privileges", DUMP_PATH]
    subprocess.run(restore_cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    # Now attempt to run alembic upgrade head against this restored database!
    test_db_url = f"postgresql+psycopg2://postgres@localhost:{PORT}/{DB_NAME}"
    env = os.environ.copy()
    env["DATABASE_URL_SYNC"] = test_db_url
    env["PYTHONPATH"] = "."

    print(f"Testing alembic upgrade head against isolated restored production database ({test_db_url})...", flush=True)
    alembic_res = subprocess.run([r"C:\androidAi\.venv\Scripts\alembic.exe", "upgrade", "head"], env=env, capture_output=True, text=True)
    print("Alembic exit code:", alembic_res.returncode, flush=True)
    if alembic_res.returncode != 0:
        print("MIGRATION FAILED AS PREDICTED!", flush=True)
        print("STDERR:")
        for line in alembic_res.stderr.splitlines()[-15:]:
            print("  ", line, flush=True)
    else:
        print("MIGRATION SUCCEEDED!", flush=True)

    # Stop cluster
    subprocess.run([PG_CTL_BIN, "-D", TEMP_DIR, "stop", "-m", "fast"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1)
    shutil.rmtree(TEMP_DIR, ignore_errors=True)

if __name__ == "__main__":
    test_dryrun_migration()

