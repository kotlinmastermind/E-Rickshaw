import os
import sys
sys.path.insert(0, r"c:\androidAi")
from dotenv import load_dotenv
from sqlalchemy import create_engine, text
from backend.infrastructure.database.session import Base
# Import all models so metadata knows about all tables and columns
import backend.domain.models

load_dotenv(r"c:\androidAi\.env")
sync_url = os.environ.get("DATABASE_URL_SYNC")

def get_pg_type_str(column):
    from sqlalchemy.dialects.postgresql import UUID, JSONB, TIMESTAMP
    import sqlalchemy.types as types

    col_type = column.type
    if isinstance(col_type, types.String) or isinstance(col_type, types.VARCHAR):
        if col_type.length:
            return f"VARCHAR({col_type.length})"
        return "TEXT"
    elif isinstance(col_type, types.Text):
        return "TEXT"
    elif isinstance(col_type, types.Integer):
        return "INTEGER"
    elif isinstance(col_type, types.BigInteger):
        return "BIGINT"
    elif isinstance(col_type, types.SmallInteger):
        return "SMALLINT"
    elif isinstance(col_type, types.Boolean):
        return "BOOLEAN"
    elif isinstance(col_type, types.Float) or isinstance(col_type, types.Numeric):
        return "NUMERIC(12, 4)"
    elif isinstance(col_type, types.DateTime):
        return "TIMESTAMP WITH TIME ZONE"
    elif isinstance(col_type, types.JSON):
        return "JSONB"
    else:
        # Generic fallback
        compiled = col_type.compile(dialect=None)
        return str(compiled)

def auto_migrate():
    engine = create_engine(sync_url)
    with engine.connect() as conn:
        # Get existing tables in public schema
        res = conn.execute(text("""
            SELECT table_name 
            FROM information_schema.tables 
            WHERE table_schema = 'public';
        """))
        existing_tables = set(r[0] for r in res.fetchall())
        print(f"Total existing tables in public schema: {len(existing_tables)}")

        # Get existing columns
        res = conn.execute(text("""
            SELECT table_name, column_name 
            FROM information_schema.columns 
            WHERE table_schema = 'public';
        """))
        existing_columns = set((r[0], r[1]) for r in res.fetchall())

        added_count = 0
        for table_name, table in Base.metadata.tables.items():
            if table_name in existing_tables:
                for col in table.columns:
                    if (table_name, col.name) not in existing_columns:
                        col_type_str = get_pg_type_str(col)
                        sql = f"ALTER TABLE \"{table_name}\" ADD COLUMN IF NOT EXISTS \"{col.name}\" {col_type_str};"
                        try:
                            conn.execute(text(sql))
                            conn.commit()
                            print(f"Added column: {table_name}.{col.name} ({col_type_str})")
                            added_count += 1
                        except Exception as e:
                            print(f"Failed to add {table_name}.{col.name}: {e}")
        
        print(f"Migration completed! Added {added_count} missing columns.")

if __name__ == "__main__":
    auto_migrate()
