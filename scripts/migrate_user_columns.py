import os
import sys
sys.path.insert(0, r"c:\androidAi")
from dotenv import load_dotenv
from sqlalchemy import create_engine, text

load_dotenv()
sync_url = os.environ.get("DATABASE_URL_SYNC")

def run_migration():
    engine = create_engine(sync_url)
    with engine.connect() as conn:
        print("Connected to Supabase PostgreSQL.")
        # Ensure username column exists
        conn.execute(text("ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(64);"))
        conn.commit()
        print("Added username column if not exists.")
        
        # Check all columns in users table
        result = conn.execute(text("""
            SELECT column_name, data_type, is_nullable
            FROM information_schema.columns 
            WHERE table_name = 'users'
            ORDER BY ordinal_position;
        """))
        cols = result.fetchall()
        print("Users table columns:")
        for col in cols:
            print(f"  - {col[0]} ({col[1]}, nullable={col[2]})")

if __name__ == "__main__":
    run_migration()
