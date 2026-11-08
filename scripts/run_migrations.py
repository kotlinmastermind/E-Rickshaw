"""
Safe database migration runner for enterprise deployment.
Performs connectivity check, records pre-migration revision, executes upgrade,
and verifies target head.
"""
import sys
import os
import logging
import argparse

# Ensure project root is in sys.path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from alembic.config import Config
from alembic import command
from alembic.script import ScriptDirectory
from alembic.runtime.migration import MigrationContext
from sqlalchemy import create_engine
from backend.core.config import settings

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("migration_runner")

def get_current_revision(connection) -> str:
    context = MigrationContext.configure(connection)
    return context.get_current_revision()

def run_migrations(target_revision: str = "head", check_only: bool = False) -> bool:
    logger.info("Initializing migration execution...")
    sync_url = settings.DATABASE_URL_SYNC
    if not sync_url:
        logger.error("DATABASE_URL_SYNC is not configured.")
        return False

    alembic_cfg = Config("alembic.ini")
    alembic_cfg.set_main_option("sqlalchemy.url", sync_url.replace("%", "%%"))

    script = ScriptDirectory.from_config(alembic_cfg)
    expected_head = script.get_current_head()
    logger.info(f"Target migration head in repository: {expected_head}")

    engine = create_engine(sync_url)
    try:
        with engine.connect() as conn:
            current_rev = get_current_revision(conn)
            logger.info(f"Current database revision: {current_rev}")

            if check_only:
                if current_rev == expected_head:
                    logger.info("Database schema is fully up-to-date.")
                    return True
                else:
                    logger.warning(f"Database schema revision ({current_rev}) does not match head ({expected_head}).")
                    return False

            if current_rev == expected_head and target_revision == "head":
                logger.info("Database is already at current head revision. No migrations needed.")
                return True

            logger.info(f"Executing migration to '{target_revision}'...")
            command.upgrade(alembic_cfg, target_revision)

        with engine.connect() as conn:
            post_rev = get_current_revision(conn)
            logger.info(f"Post-migration database revision: {post_rev}")
            if target_revision == "head" and post_rev != expected_head:
                logger.error(f"Migration verification failed: Expected {expected_head}, got {post_rev}")
                return False

        logger.info("Database migration completed successfully and verified.")
        return True

    except Exception as e:
        logger.exception(f"Migration execution failed: {e}")
        return False
    finally:
        engine.dispose()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run Alembic database migrations safely.")
    parser.add_argument("--revision", default="head", help="Target revision (default: head)")
    parser.add_argument("--check", action="store_true", help="Check status without applying migrations")
    args = parser.parse_args()

    success = run_migrations(target_revision=args.revision, check_only=args.check)
    sys.exit(0 if success else 1)

