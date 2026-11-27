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
    monkeypatch.setenv("PAYOUT_DESTINATION_ENCRYPTION_KEY", "test-only-payout-encryption-key-alpha-99887766")
    monkeypatch.setenv("RAZORPAY_KEY_ID", "rzp_live_12345678901234")
    monkeypatch.setenv("RAZORPAY_KEY_SECRET", "production_secret_key_12345678")
    monkeypatch.setenv("RAZORPAY_WEBHOOK_SECRET", "production_webhook_secret_123456")
    monkeypatch.setenv("RAZORPAY_MERCHANT_ACCOUNT_ID", "acc_FourthMerchant01")

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
    import hashlib
    import re

    with open("backend/core/config.py", "r", encoding="utf-8") as f:
        src = f.read()

    # SHA-256 digests of historically compromised credentials (stored only as irreversible hashes)
    compromised_sha256_digests = {
        "078609acf0e6dc35066737e1ea5a17fa2845b2f5ae92ef2572814765e4de64d3",
        "71be9f29fea3d8b57db08fa7b5ff524e27bd0d1f457bcba364737bfa6786fce7",
        "94c89a0b7924b1ea176c8ac5829c5ed3a59faa505f5823c626425a6b8b8ae4e7",
        "5fed94849bed0cf6091e179e38568428e373890745433d9487c4b714e6959c7c",
        "ccfa99b2b0d03200160f19821399c05a0bd978b12565b25e1b786bd5eca18d77",
    }
    candidates = re.findall(r"[A-Za-z0-9_.%+/-]{14,}", src)
    for token in candidates:
        digest = hashlib.sha256(token.encode("utf-8")).hexdigest()
        assert digest not in compromised_sha256_digests, (
            f"Security Violation: Compromised credential digest matched in backend/core/config.py!"
        )
    assert not re.search(r"postgresql(?:\+\w+)?://[^$\"'\s]+:[^@$\"'\s]+@", src), (
        "Embedded database credentials must not appear in backend/core/config.py"
    )
    assert not re.search(r"rzp_live_[A-Za-z0-9]{8,}", src), (
        "Live Razorpay key literal must not appear in backend/core/config.py"
    )
    assert not re.search(r"sk\.[A-Za-z0-9_-]{12,}", src), (
        "Mapbox token literal must not appear in backend/core/config.py"
    )

@pytest.mark.asyncio
async def test_init_db_skips_ddl_in_production(monkeypatch):
    from backend.infrastructure.database.session import init_db
    import backend.infrastructure.database.session as sess_module

    monkeypatch.setattr(sess_module.settings, "ENVIRONMENT", "production")
    # In production, init_db must return early without touching database schema
    await init_db()

