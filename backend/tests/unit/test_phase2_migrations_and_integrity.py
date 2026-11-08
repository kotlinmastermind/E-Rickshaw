import pytest
import os
import tempfile
from alembic.config import Config
from alembic import command
from sqlalchemy import create_engine, inspect
from backend.domain.models.models import Base
from scripts.run_migrations import run_migrations
from scripts.verify_database_integrity import verify_database_integrity

def test_metadata_tables_integrity():
    """Verify all declarative models have primary keys and defined tables."""
    assert len(Base.metadata.tables) > 50, "Expected at least 50 mapped tables in Base.metadata"
    for table_name, table in Base.metadata.tables.items():
        assert len(table.primary_key.columns) > 0, f"Table {table_name} missing primary key"

def test_alembic_upgrade_downgrade_cycle():
    """Test full Alembic migration cycle on an isolated temporary SQLite database."""
    fd, temp_path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    if os.path.exists(temp_path):
        os.remove(temp_path)

    test_url = f"sqlite:///{temp_path}"
    os.environ["DATABASE_URL_SYNC"] = test_url
    cfg = Config("alembic.ini")
    cfg.set_main_option("sqlalchemy.url", test_url)

    try:
        # Upgrade to head
        command.upgrade(cfg, "head")

        # Verify tables created
        engine = create_engine(test_url)
        inspector = inspect(engine)
        tables = inspector.get_table_names()
        assert "users" in tables
        assert "rides" in tables
        assert "payments" in tables
        assert "payment_webhook_inbox" in tables
        assert "idempotency_records" in tables
        assert "administrative_audit_logs" in tables
        engine.dispose()

        # Test database integrity check on fresh migrated schema
        integrity_res = verify_database_integrity(test_url)
        assert integrity_res["status"] == "PASSED"
        assert len(integrity_res["violations"]) == 0

        # Downgrade 1 revision and verify
        command.downgrade(cfg, "-1")

        # Upgrade back to head
        command.upgrade(cfg, "head")

    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)

