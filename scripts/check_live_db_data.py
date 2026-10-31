import sys, os
sys.path.insert(0, os.path.abspath('.'))
import asyncio
from backend.core.config import settings
from backend.infrastructure.database.session import engine
from sqlalchemy import text

async def inspect_db():
    safe_url = settings.DATABASE_URL.split('@')[-1] if '@' in settings.DATABASE_URL else settings.DATABASE_URL
    print(f"DATABASE HOST: {safe_url}")
    print(f"DIALECT: {engine.dialect.name}")
    async with engine.connect() as conn:
        dialect = engine.dialect.name
        if dialect == 'postgresql':
            res = await conn.execute(text("SELECT table_name FROM information_schema.tables WHERE table_schema='public'"))
            tables = [r[0] for r in res.fetchall()]
        else:
            res = await conn.execute(text("SELECT name FROM sqlite_master WHERE type='table'"))
            tables = [r[0] for r in res.fetchall()]
        
        print(f"TOTAL TABLES IN DB: {len(tables)}")
        populated = {}
        empty = []
        for t in sorted(tables):
            try:
                cnt_res = await conn.execute(text(f'SELECT count(*) FROM "{t}"'))
                cnt = cnt_res.scalar()
                if cnt > 0:
                    populated[t] = cnt
                else:
                    empty.append(t)
            except Exception as e:
                pass
        
        print(f"\n--- POPULATED TABLES ({len(populated)}) ---")
        for t, c in populated.items():
            print(f"  {t}: {c} rows")
        
        print(f"\n--- TABLES READY WITH SCHEMA (Currently 0 rows): {len(empty)} ---")

if __name__ == '__main__':
    asyncio.run(inspect_db())
