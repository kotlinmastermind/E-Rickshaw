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

def setup_storage_rls():
    engine = create_engine(sync_url)
    with engine.connect() as conn:
        print("Setting up Storage RLS policies for driver-documents...")
        conn.execute(text("""
            -- Allow public bucket access
            DROP POLICY IF EXISTS "Public Bucket Access" ON storage.buckets;
            CREATE POLICY "Public Bucket Access" ON storage.buckets FOR SELECT USING (true);

            -- Allow authenticated and anon to upload documents
            DROP POLICY IF EXISTS "Allow All Uploads to driver-documents" ON storage.objects;
            CREATE POLICY "Allow All Uploads to driver-documents" ON storage.objects
                FOR INSERT WITH CHECK (bucket_id = 'driver-documents');

            -- Allow reading from driver-documents
            DROP POLICY IF EXISTS "Allow All Reads from driver-documents" ON storage.objects;
            CREATE POLICY "Allow All Reads from driver-documents" ON storage.objects
                FOR SELECT USING (bucket_id = 'driver-documents');
        """))
        conn.commit()
        print("Storage RLS policies successfully applied.")

    headers = {"apikey": key, "Authorization": f"Bearer {key}"}
    r = httpx.get(f"{url}/storage/v1/bucket", headers=headers)
    print("REST API Buckets after RLS policy:", r.status_code, r.text)

if __name__ == "__main__":
    setup_storage_rls()
