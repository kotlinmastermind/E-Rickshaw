import os
import pytest
from backend.core.config import Settings

def test_production_fails_when_database_url_missing(monkeypatch):
    monkeypatch.setenv("TESTING", "0")
    monkeypatch.setenv("ENVIRONMENT", "production")
    monkeypatch.setenv("DATABASE_URL", "")
    monkeypatch.setenv("DATABASE_URL_SYNC", "postgresql+psycopg2://user:pass@host:5432/db")
    monkeypatch.setenv("SECRET_KEY", "super-secure-production-secret-key-1234567890")

    with pytest.raises(ValueError, match="DATABASE_URL must be provided via environment variables in production"):
        Settings(_env_file=None)

def test_production_fails_when_database_url_sync_missing(monkeypatch):
    monkeypatch.setenv("TESTING", "0")
    monkeypatch.setenv("ENVIRONMENT", "production")
    monkeypatch.setenv("DATABASE_URL", "postgresql+asyncpg://user:pass@host:5432/db")
    monkeypatch.setenv("DATABASE_URL_SYNC", "")
    monkeypatch.setenv("SECRET_KEY", "super-secure-production-secret-key-1234567890")

    with pytest.raises(ValueError, match="DATABASE_URL_SYNC must be provided via environment variables in production"):
        Settings(_env_file=None)

def test_production_fails_when_secret_key_insecure(monkeypatch):
    monkeypatch.setenv("TESTING", "0")
    monkeypatch.setenv("ENVIRONMENT", "production")
    monkeypatch.setenv("DATABASE_URL", "postgresql+asyncpg://user:pass@host:5432/db")
    monkeypatch.setenv("DATABASE_URL_SYNC", "postgresql+psycopg2://user:pass@host:5432/db")
    monkeypatch.setenv("SECRET_KEY", "dev-insecure-secret-key-change-in-production")

    with pytest.raises(ValueError, match="A secure SECRET_KEY must be provided via environment variables in production"):
        Settings(_env_file=None)

def test_production_succeeds_with_valid_configuration(monkeypatch):
    monkeypatch.setenv("TESTING", "0")
    monkeypatch.setenv("ENVIRONMENT", "production")
    monkeypatch.setenv("DATABASE_URL", "postgresql+asyncpg://user:pass@host:5432/db")
    monkeypatch.setenv("DATABASE_URL_SYNC", "postgresql+psycopg2://user:pass@host:5432/db")
    monkeypatch.setenv("SECRET_KEY", "production-cryptographically-secure-key-99887766")

    cfg = Settings(_env_file=None)
    assert cfg.ENVIRONMENT == "production"
    assert cfg.DATABASE_URL == "postgresql+asyncpg://user:pass@host:5432/db"
    assert cfg.SECRET_KEY == "production-cryptographically-secure-key-99887766"

def test_development_fallback_defaults(monkeypatch):
    monkeypatch.setenv("TESTING", "0")
    monkeypatch.setenv("ENVIRONMENT", "development")
    monkeypatch.delenv("DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL_SYNC", raising=False)
    monkeypatch.delenv("SECRET_KEY", raising=False)

    cfg = Settings(_env_file=None)
    assert cfg.ENVIRONMENT == "development"
    assert "sqlite" in cfg.DATABASE_URL
    assert cfg.SECRET_KEY == "dev-insecure-secret-key-change-in-production"

def test_no_hardcoded_passwords_or_tokens_in_config_source():
    with open("backend/core/config.py", "r", encoding="utf-8") as f:
        src = f.read()

    forbidden_patterns = [
        "sunil%40%2375620",
        "sk.eyJ1Ijoia290bGlubWFzdGVybWluZC",
        "gQAAAAAAA6FxAAIgcDFlMDE2ZjlkMDZlZTQ0ODIwYjg5NzdiODFkMmU1YzM4OQ",
        "rzp_live_TunGmzfpWlcatO",
        "CSIhchRsNTdHx270VZthHSUE"
    ]
    for pattern in forbidden_patterns:
        assert pattern not in src, f"Security Violation: Pattern '{pattern}' found in backend/core/config.py source code!"

@pytest.mark.asyncio
async def test_init_db_skips_ddl_in_production(monkeypatch):
    from backend.infrastructure.database.session import init_db
    import backend.infrastructure.database.session as sess_module

    monkeypatch.setattr(sess_module.settings, "ENVIRONMENT", "production")
    # In production, init_db must return early without touching database schema
    await init_db()

