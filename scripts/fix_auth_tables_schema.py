import os
import sys
sys.path.insert(0, r"c:\androidAi")
from dotenv import load_dotenv
from sqlalchemy import create_engine, text

load_dotenv(r"c:\androidAi\.env")
sync_url = os.environ.get("DATABASE_URL_SYNC")

def fix_auth_tables():
    engine = create_engine(sync_url)
    with engine.connect() as conn:
        print("Connected to Supabase PostgreSQL.")
        
        # 1. Sessions table
        conn.execute(text("""
            ALTER TABLE sessions ADD COLUMN IF NOT EXISTS mfa_verified BOOLEAN DEFAULT FALSE;
            ALTER TABLE sessions ADD COLUMN IF NOT EXISTS is_revoked BOOLEAN DEFAULT FALSE;
            ALTER TABLE sessions ADD COLUMN IF NOT EXISTS user_agent VARCHAR(255);
            ALTER TABLE sessions ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);
        """))
        conn.commit()
        print("Updated sessions table.")

        # 2. Users table
        conn.execute(text("""
            ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(64);
            ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255);
            ALTER TABLE users ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT FALSE;
            ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;
        """))
        conn.commit()
        print("Updated users table.")

        # 3. Customer profiles
        conn.execute(text("""
            ALTER TABLE customer_profiles ADD COLUMN IF NOT EXISTS default_category VARCHAR(64);
        """))
        conn.commit()
        print("Updated customer_profiles table.")

        # 4. Check sessions columns
        res = conn.execute(text("""
            SELECT column_name, data_type 
            FROM information_schema.columns 
            WHERE table_name = 'sessions';
        """))
        print("Current sessions columns:", [r[0] for r in res.fetchall()])

if __name__ == "__main__":
    fix_auth_tables()
