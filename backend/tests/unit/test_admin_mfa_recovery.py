import json

import pytest
from sqlalchemy import select

from backend.domain.models import AuditLog, Session
from backend.infrastructure.cache.redis_client import RedisManager
from backend.infrastructure.security.crypto import hash_password
from backend.scripts.recover_admin_mfa import (
    RecoverySafetyError,
    rotate_admin_mfa,
)
from backend.tests.unit.test_admin_password_recovery import make_admin


@pytest.mark.asyncio
async def test_admin_mfa_recovery_rotates_secret_keeps_mfa_enabled_and_revokes_sessions(
    db_session, monkeypatch
):
    user, role, session = await make_admin(db_session)
    user.password_hash = hash_password("A-current-strong-admin-password-2026")
    await db_session.commit()
    old_secret = user.mfa_secret

    async def fake_revoke_session_cache(session_ids):
        assert session.id in session_ids

    monkeypatch.setattr(
        "backend.scripts.recover_admin_mfa.revoke_session_cache",
        fake_revoke_session_cache,
    )

    result = await rotate_admin_mfa(
        db_session,
        email=user.email,
        current_password="A-current-strong-admin-password-2026",
        confirm_email=user.email,
        environment="test",
    )

    await db_session.refresh(user)
    await db_session.refresh(session)
    assert result["status"] == "MFA_SECRET_ROTATED"
    assert result["secret"] != old_secret
    assert result["totp_uri"].startswith("otpauth://totp/")
    assert user.mfa_enabled is True
    assert user.mfa_secret == result["secret"]
    assert user.role == "admin"
    assert session.is_revoked is True
    assert await db_session.get(type(role), role.id) is not None

    audit = (
        await db_session.execute(
            select(AuditLog).where(
                AuditLog.action == "ADMIN_MFA_RECOVERY",
                AuditLog.target_id == user.id,
            )
        )
    ).scalar_one()
    metadata = json.loads(audit.metadata_json)
    assert metadata["mfa_enabled_preserved"] is True
    assert metadata["old_secret_replaced"] is True
    assert metadata["admin_role_preserved"] is True
    assert result["secret"] not in audit.metadata_json
    assert "totp_uri" not in audit.metadata_json


@pytest.mark.asyncio
async def test_admin_mfa_recovery_rejects_wrong_password_without_mutation(
    db_session, monkeypatch
):
    user, _, session = await make_admin(db_session)
    user.password_hash = hash_password("A-current-strong-admin-password-2026")
    await db_session.commit()
    old_secret = user.mfa_secret

    async def unexpected_cache_revoke(session_ids):
        pytest.fail("Cache revocation must not run for invalid credentials.")

    monkeypatch.setattr(
        "backend.scripts.recover_admin_mfa.revoke_session_cache",
        unexpected_cache_revoke,
    )

    with pytest.raises(RecoverySafetyError, match="password is incorrect"):
        await rotate_admin_mfa(
            db_session,
            email=user.email,
            current_password="wrong-password",
            confirm_email=user.email,
            environment="test",
        )

    await db_session.refresh(user)
    await db_session.refresh(session)
    assert user.mfa_secret == old_secret
    assert user.mfa_enabled is True
    assert session.is_revoked is False


@pytest.mark.asyncio
async def test_admin_mfa_recovery_rejects_confirmation_and_production_without_mutation(
    db_session, monkeypatch
):
    user, _, session = await make_admin(db_session)
    user.password_hash = hash_password("A-current-strong-admin-password-2026")
    await db_session.commit()
    old_secret = user.mfa_secret

    async def unexpected_cache_revoke(session_ids):
        pytest.fail("Cache revocation must not run before confirmation checks.")

    monkeypatch.setattr(
        "backend.scripts.recover_admin_mfa.revoke_session_cache",
        unexpected_cache_revoke,
    )

    with pytest.raises(RecoverySafetyError, match="does not exactly match"):
        await rotate_admin_mfa(
            db_session,
            email=user.email,
            current_password="A-current-strong-admin-password-2026",
            confirm_email="attacker@example.test",
            environment="test",
        )

    with pytest.raises(RecoverySafetyError, match="confirm-production"):
        await rotate_admin_mfa(
            db_session,
            email=user.email,
            current_password="A-current-strong-admin-password-2026",
            confirm_email=user.email,
            environment="production",
            confirm_production=False,
        )

    await db_session.refresh(user)
    await db_session.refresh(session)
    assert user.mfa_secret == old_secret
    assert user.mfa_enabled is True
    assert session.is_revoked is False



@pytest.mark.asyncio
async def test_admin_mfa_recovery_rolls_back_when_redis_revocation_is_unavailable(
    db_session, monkeypatch
):
    user, _, session = await make_admin(db_session)
    user.password_hash = hash_password("A-current-strong-admin-password-2026")
    await db_session.commit()
    old_secret = user.mfa_secret

    async def fail_closed_revoke(session_ids):
        assert session.id in session_ids
        raise ConnectionError("Redis is required for this security-sensitive operation.")

    monkeypatch.setattr(
        "backend.scripts.recover_admin_mfa.revoke_session_cache",
        fail_closed_revoke,
    )

    with pytest.raises(ConnectionError, match="Redis is required"):
        await rotate_admin_mfa(
            db_session,
            email=user.email,
            current_password="A-current-strong-admin-password-2026",
            confirm_email=user.email,
            environment="test",
        )

    await db_session.refresh(user)
    await db_session.refresh(session)
    assert user.mfa_secret == old_secret
    assert user.mfa_enabled is True
    assert session.is_revoked is False



@pytest.mark.asyncio
async def test_redis_set_strict_fails_without_redis_and_never_uses_memory_fallback(
    monkeypatch,
):
    manager = RedisManager()

    async def no_redis():
        return None

    async def forbidden_fallback(*args, **kwargs):
        pytest.fail("Security-sensitive writes must never use the in-memory fallback.")

    monkeypatch.setattr(manager, "_get_redis", no_redis)
    monkeypatch.setattr(manager._fallback, "set", forbidden_fallback)

    with pytest.raises(ConnectionError, match="Redis is required"):
        await manager.set_strict("revoked_session:test", "true", ex=60)


@pytest.mark.asyncio
async def test_redis_set_strict_requires_server_acknowledgement(monkeypatch):
    manager = RedisManager()

    class RedisWithoutWriteAcknowledgement:
        async def set(self, *args, **kwargs):
            return False

    async def connected_redis():
        return RedisWithoutWriteAcknowledgement()

    monkeypatch.setattr(manager, "_get_redis", connected_redis)

    with pytest.raises(RuntimeError, match="did not confirm"):
        await manager.set_strict("revoked_session:test", "true", ex=60)
