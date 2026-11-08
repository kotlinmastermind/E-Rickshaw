import os
import json
import psycopg2
from dotenv import load_dotenv

load_dotenv('.env')
raw_url = os.environ.get('DATABASE_URL_SYNC', '')
url = raw_url.replace('postgresql+psycopg2://', 'postgresql://')

conn = psycopg2.connect(url, connect_timeout=10)
cur = conn.cursor()

# 1. Columns
cur.execute("""
    SELECT table_name, column_name, data_type, is_nullable, column_default 
    FROM information_schema.columns 
    WHERE table_schema = 'public' 
    ORDER BY table_name, ordinal_position;
""")
rows = cur.fetchall()
print(f"Fetched {len(rows)} columns across tables!")

schema = {}
for t, col, dt, nullb, defv in rows:
    if t not in schema:
        schema[t] = {
            'columns': [],
            'primary_key': [],
            'foreign_keys': []
        }
    schema[t]['columns'].append({
        'name': col,
        'type': dt,
        'nullable': nullb == 'YES',
        'default': defv
    })

# 2. Primary keys via pg_catalog (very fast)
cur.execute("""
    SELECT tc.table_name, ccu.column_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.constraint_column_usage ccu 
      ON tc.constraint_name = ccu.constraint_name AND tc.table_schema = ccu.table_schema
    WHERE tc.constraint_type = 'PRIMARY KEY' AND tc.table_schema = 'public';
""")
for t, col in cur.fetchall():
    if t in schema:
        schema[t]['primary_key'].append(col)

# 3. Foreign keys
cur.execute("""
    SELECT
        kcu.table_name,
        kcu.column_name,
        ccu.table_name AS foreign_table_name,
        ccu.column_name AS foreign_column_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
    JOIN information_schema.constraint_column_usage ccu
      ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema
    WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public';
""")
for t, col, ft, fcol in cur.fetchall():
    if t in schema:
        schema[t]['foreign_keys'].append({
            'column': col,
            'foreign_table': ft,
            'foreign_column': fcol
        })

# 4. Row counts
counts = {}
for t in schema:
    try:
        cur.execute(f'SELECT count(*) FROM "{t}";')
        cnt = cur.fetchone()[0]
        counts[t] = cnt
        schema[t]['row_count'] = cnt
    except Exception as e:
        counts[t] = -1
        schema[t]['row_count'] = -1
        conn.rollback()

cur.close()
conn.close()

os.makedirs('docs/database-integration', exist_ok=True)
with open('docs/database-integration/supabase_live_schema.json', 'w') as f:
    json.dump(schema, f, indent=2)

print(f"Total tables in Supabase: {len(schema)}")
for t in sorted(schema.keys()):
    cols = [c['name'] for c in schema[t]['columns']]
    pk = schema[t]['primary_key']
    cnt = schema[t]['row_count']
    print(f"  {t} ({cnt} rows, PK: {pk}): {cols}")

