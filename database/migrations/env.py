import os
import sys
from logging.config import fileConfig

from sqlalchemy import engine_from_config, pool, text, inspect
from alembic import context

# Add project root to sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))

from backend.core.config import settings
from backend.domain.models import Base

config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata

def get_database_url() -> str:
    is_testing = os.getenv("TESTING") == "1"
    # 1. Check if caller explicitly passed sqlalchemy.url via Config (e.g. in tests)
    ini_url = config.get_main_option("sqlalchemy.url")
    if ini_url and (is_testing or not ini_url.startswith("sqlite")):
        return ini_url

    # 2. Check environment variable override
    env_sync = os.getenv("DATABASE_URL_SYNC")
    if env_sync and (is_testing or not env_sync.startswith("sqlite")):
        return env_sync

    # 3. Prefer DATABASE_URL_SYNC from settings
    db_sync = getattr(settings, "DATABASE_URL_SYNC", "")
    if db_sync and not db_sync.startswith("sqlite"):
        return db_sync

    # 4. Derive sync url from DATABASE_URL
    db_url = getattr(settings, "DATABASE_URL", "")
    if db_url and not db_url.startswith("sqlite"):
        if db_url.startswith("postgresql+asyncpg"):
            return db_url.replace("postgresql+asyncpg", "postgresql+psycopg2")
        return db_url

    raise ValueError(
        "Production PostgreSQL configuration (DATABASE_URL or DATABASE_URL_SYNC) must be configured for Alembic migrations. SQLite fallback is strictly prohibited."
    )

def run_migrations_offline() -> None:
    """Run migrations in 'offline' mode."""
    url = get_database_url()
    context.configure(
        url=url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )

    with context.begin_transaction():
        context.run_migrations()

def run_migrations_online() -> None:
    """Run migrations in 'online' mode."""
    configuration = config.get_section(config.config_ini_section) or {}
    configuration["sqlalchemy.url"] = get_database_url()

    connectable = engine_from_config(
        configuration,
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )

    with connectable.connect() as connection:
        if connection.dialect.name == "postgresql":
            insp = inspect(connection)
            if "alembic_version" not in set(insp.get_table_names()):
                connection.execute(
                    text(
                        "CREATE TABLE alembic_version ("
                        "version_num VARCHAR(128) NOT NULL, "
                        "CONSTRAINT alembic_version_pkc PRIMARY KEY (version_num)"
                        ")"
                    )
                )
            else:
                cols = {c["name"]: c for c in insp.get_columns("alembic_version")}
                vcol = cols.get("version_num")
                vlen = getattr(vcol["type"], "length", None) if vcol else None
                if vlen is not None and vlen < 128:
                    connection.execute(text("ALTER TABLE alembic_version ALTER COLUMN version_num TYPE VARCHAR(128)"))
            connection.commit()

        context.configure(
            connection=connection,
            target_metadata=target_metadata
        )

        with context.begin_transaction():
            context.run_migrations()

if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
