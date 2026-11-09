import pytest
import os
import tempfile
from alembic.config import Config
from alembic import command
from sqlalchemy import create_engine, inspect
from backend.domain.models.models import Base

def verify_database_integrity(db_url: str):
    eng = create_engine(db_url)
    insp = inspect(eng)
    tbls = set(insp.get_table_names())
    violations = []
    for expected in ["users", "rides", "payments", "payment_webhook_inbox", "idempotency_records", "administrative_audit_logs"]:
        if expected not in tbls:
            violations.append(f"Missing table: {expected}")
    eng.dispose()
    return {"status": "PASSED" if not violations else "FAILED", "violations": violations}

def test_metadata_tables_integrity():
    """Verify all declarative models have primary keys and defined tables."""
    assert len(Base.metadata.tables) > 50, "Expected at least 50 mapped tables in Base.metadata"
    for table_name, table in Base.metadata.tables.items():
        assert len(table.primary_key.columns) > 0, f"Table {table_name} missing primary key"

def test_alembic_upgrade_downgrade_cycle():
    """Test full Alembic migration cycle on an isolated temporary SQLite database."""
    if not os.path.exists("database/migrations/env.py"):
        pytest.skip("database/migrations/env.py is not present in repository")

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

