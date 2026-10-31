import os
import sys
sys.path.insert(0, r"c:\androidAi")
from dotenv import load_dotenv
from sqlalchemy import create_engine, text
import httpx

load_dotenv(r"c:\androidAi\.env")
sync_url = os.environ.get("DATABASE_URL_SYNC")
url = os.environ.get("SUPABASE_URL")
key = os.environ.get("SUPABASE_KEY")

def create_bucket():
    print("Creating driver-documents bucket on Supabase...")
    # 1. Via SQL
    engine = create_engine(sync_url)
    with engine.connect() as conn:
        try:
            conn.execute(text("""
                INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
                VALUES ('driver-documents', 'driver-documents', true, 10485760, ARRAY['image/png', 'image/jpeg', 'application/pdf', 'image/webp'])
                ON CONFLICT (id) DO NOTHING;
            """))
            conn.commit()
            print("SQL: driver-documents bucket created.")
        except Exception as e:
            print("SQL error:", e)

        res = conn.execute(text("SELECT id, name, public FROM storage.buckets;"))
        print("SQL Buckets list:", res.fetchall())

    # 2. Verify via REST API
    headers = {"apikey": key, "Authorization": f"Bearer {key}"}
    r = httpx.get(f"{url}/storage/v1/bucket", headers=headers)
    print("REST API Buckets:", r.status_code, r.text)

if __name__ == "__main__":
    create_bucket()
