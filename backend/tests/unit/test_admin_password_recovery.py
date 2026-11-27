import json
from argparse import Namespace
import uuid
from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import select

from backend.domain.models import AdminRole, AdminUserRole, AuditLog, Session, User
from backend.infrastructure.security.crypto import verify_password
from backend.scripts.recover_admin_password import (
    RecoverySafetyError,
    reset_admin_password,
    require_execute_confirmation,
)


async def make_admin(db_session, *, mfa_enabled=True, mfa_secret="JBSWY3DPEHPK3PXP"):
    user_id = str(uuid.uuid4())
    role_id = str(uuid.uuid4())
    user = User(
        id=user_id,
        phone_number=f"+9199{uuid.uuid4().int % 100000000:08d}",
        email=f"admin-{user_id[:8]}@example.test",
        full_name="Test Admin",
        role="admin",
        is_active=True,
        is_verified=True,
        password_hash="old-hash",
        mfa_enabled=mfa_enabled,
        mfa_secret=mfa_secret,
    )
    role = AdminRole(
        id=role_id,
        name=f"Test Superuser {user_id[:8]}",
        description="Test-only superuser role",
        is_superuser=True,
    )
    db_session.add_all([user, role])
    await db_session.flush()
    db_session.add(AdminUserRole(user_id=user_id, role_id=role_id))
    session = Session(
        id=str(uuid.uuid4()),
        user_id=user_id,
        token_hash=f"token-hash-{uuid.uuid4().hex}",
        is_revoked=False,
        mfa_verified=True,
        expires_at=datetime.now(timezone.utc) + timedelta(hours=1),
    )
    db_session.add(session)
    await db_session.commit()
    return user, role, session


@pytest.mark.asyncio
async def test_admin_recovery_changes_password_revokes_sessions_and_preserves_mfa_and_role(db_session):
    user, role, session = await make_admin(db_session)
    old_mfa_secret = user.mfa_secret
    old_role = user.role
    new_password = "A-new-strong-admin-password-2026"

    result = await reset_admin_password(
        db_session,
        email=user.email,
        new_password=new_password,
        confirm_email=user.email,
        environment="test",
    )

    await db_session.refresh(user)
    await db_session.refresh(session)
    assert result["status"] == "RECOVERY_SUCCESS"
    assert verify_password(new_password, user.password_hash)
    assert user.role == old_role
    assert user.mfa_enabled is True
    assert user.mfa_secret == old_mfa_secret
    assert session.is_revoked is True
    assert await db_session.get(AdminRole, role.id) is not None
    mapping = (
        await db_session.execute(
            select(AdminUserRole).where(
                AdminUserRole.user_id == user.id,
                AdminUserRole.role_id == role.id,
            )
        )
    ).scalar_one()
    assert mapping is not None

    audit = (
        await db_session.execute(
            select(AuditLog).where(
                AuditLog.action == "ADMIN_PASSWORD_RECOVERY",
                AuditLog.target_id == user.id,
            )
        )
    ).scalar_one()
    metadata = json.loads(audit.metadata_json)
    assert metadata["mfa_preserved"] is True
    assert metadata["admin_role_preserved"] is True
    assert new_password not in audit.metadata_json
    assert user.password_hash not in audit.metadata_json


@pytest.mark.asyncio
async def test_admin_recovery_rejects_email_mismatch_and_production_without_confirmation(db_session):
    user, _, session = await make_admin(db_session)

    with pytest.raises(RecoverySafetyError, match="does not exactly match"):
        await reset_admin_password(
            db_session,
            email=user.email,
            new_password="A-new-strong-admin-password-2026",
            confirm_email="attacker@example.test",
            environment="test",
        )

    with pytest.raises(RecoverySafetyError, match="confirm-production"):
        await reset_admin_password(
            db_session,
            email=user.email,
            new_password="A-new-strong-admin-password-2026",
            confirm_email=user.email,
            environment="production",
            confirm_production=False,
        )

    await db_session.refresh(user)
    await db_session.refresh(session)
    assert user.password_hash == "old-hash"
    assert session.is_revoked is False


@pytest.mark.asyncio
async def test_admin_recovery_fails_closed_when_mfa_is_not_configured(db_session):
    user, _, session = await make_admin(
        db_session,
        mfa_enabled=False,
        mfa_secret=None,
    )

    with pytest.raises(RecoverySafetyError, match="MFA is not fully configured"):
        await reset_admin_password(
            db_session,
            email=user.email,
            new_password="A-new-strong-admin-password-2026",
            confirm_email=user.email,
            environment="test",
        )

    await db_session.refresh(user)
    await db_session.refresh(session)
    assert user.password_hash == "old-hash"
    assert user.mfa_enabled is False
    assert session.is_revoked is False


def test_cli_requires_production_confirmation_even_when_local_environment_is_development():
    args = Namespace(execute=True, confirm_production=False)
    with pytest.raises(RecoverySafetyError, match="regardless of local ENVIRONMENT"):
        require_execute_confirmation(args)

    args.confirm_production = True
    require_execute_confirmation(args)

