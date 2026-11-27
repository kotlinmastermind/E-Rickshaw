"""Operator-run, fail-closed Admin password recovery.

Run from the repository root:
  python -m backend.scripts.recover_admin_password --email admin@example.com --check
  python -m backend.scripts.recover_admin_password --email admin@example.com --execute --confirm-email admin@example.com --confirm-production

The --confirm-production flag is mandatory for every execution, including when the local
ENVIRONMENT is set to development; this prevents a local configuration from weakening
the operator's explicit acknowledgement when DATABASE_URL points at production.
The password is read with getpass; never pass it as a CLI argument or environment variable.
This module intentionally exposes no HTTP endpoint.
"""
from __future__ import annotations

import argparse
import asyncio
import getpass
import json
import sys
import uuid
from datetime import datetime, timezone
from typing import Any

from sqlalchemy import func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from backend.core.config import settings
from backend.domain.models import (
    AdminPermission,
    AdminRole,
    AdminRolePermission,
    AdminUserRole,
    AuditLog,
    Session,
    User,
)
from backend.infrastructure.database.session import async_session_maker, engine
from backend.infrastructure.security.crypto import hash_password, validate_password_strength


ADMIN_ROLES = frozenset({"admin", "superadmin", "super administrator"})


class RecoverySafetyError(RuntimeError):
    """Raised when recovery preconditions are not satisfied."""


def normalize_email(value: str) -> str:
    return (value or "").strip().lower()


async def inspect_admin_target(
    db: AsyncSession, email: str, *, lock: bool = False
) -> tuple[User, int]:
    normalized_email = normalize_email(email)
    if not normalized_email or "@" not in normalized_email:
        raise RecoverySafetyError("A valid Admin email address is required.")

    stmt = select(User).where(func.lower(User.email) == normalized_email)
    if lock:
        stmt = stmt.with_for_update()
    result = await db.execute(stmt)
    user = result.scalar_one_or_none()
    if user is None:
        raise RecoverySafetyError("No account matches the supplied Admin email.")
    if (user.role or "").strip().lower() not in ADMIN_ROLES:
        raise RecoverySafetyError("Target account is not an Admin; no changes made.")
    if not user.is_active:
        raise RecoverySafetyError("Target Admin account is inactive; no changes made.")
    if not user.mfa_enabled or not user.mfa_secret:
        raise RecoverySafetyError(
            "Admin MFA is not fully configured. Recovery is blocked; MFA will not be bypassed."
        )

    role_result = await db.execute(
        select(AdminRole)
        .join(AdminUserRole, AdminUserRole.role_id == AdminRole.id)
        .where(AdminUserRole.user_id == user.id)
    )
    assigned_roles = role_result.scalars().all()
    if not assigned_roles:
        raise RecoverySafetyError(
            "Target Admin has no Admin role assignment; no changes made."
        )

    permission_result = await db.execute(
        select(AdminRolePermission.role_id)
        .join(
            AdminPermission,
            AdminPermission.id == AdminRolePermission.permission_id,
        )
        .where(
            AdminRolePermission.role_id.in_([role.id for role in assigned_roles]),
            AdminPermission.permission_key == "admin.access",
        )
    )
    permitted_role_ids = set(permission_result.scalars().all())
    has_admin_access = any(
        role.is_superuser or role.id in permitted_role_ids
        for role in assigned_roles
    )
    if not has_admin_access:
        raise RecoverySafetyError(
            "Target Admin role assignment lacks admin.access; no changes made."
        )

    count_result = await db.execute(
        select(func.count(Session.id)).where(
            Session.user_id == user.id,
            Session.is_revoked.is_(False),
        )
    )
    active_sessions = int(count_result.scalar_one() or 0)
    return user, active_sessions


async def reset_admin_password(
    db: AsyncSession,
    *,
    email: str,
    new_password: str,
    confirm_email: str,
    environment: str,
    confirm_production: bool = False,
) -> dict[str, Any]:
    normalized_email = normalize_email(email)
    if not normalized_email or normalize_email(confirm_email) != normalized_email:
        raise RecoverySafetyError(
            "Confirmation email does not exactly match the target Admin email."
        )
    if (environment or "").strip().lower() == "production" and not confirm_production:
        raise RecoverySafetyError(
            "Production recovery requires the explicit --confirm-production flag."
        )

    try:
        validate_password_strength(new_password)
    except ValueError as exc:
        raise RecoverySafetyError(str(exc)) from exc
    if len(new_password.encode("utf-8")) > 72:
        raise RecoverySafetyError(
            "Password exceeds bcrypt's 72-byte input limit; choose a shorter password."
        )

    try:
        user, _ = await inspect_admin_target(db, normalized_email, lock=True)
        # Password, session revocation, and audit event commit atomically.
        user.password_hash = hash_password(new_password)
        revoked_result = await db.execute(
            update(Session)
            .where(
                Session.user_id == user.id,
                Session.is_revoked.is_(False),
            )
            .values(is_revoked=True)
        )
        revoked_count = int(revoked_result.rowcount or 0)

        db.add(
            AuditLog(
                actor_id=None,
                actor_role="SYSTEM",
                action="ADMIN_PASSWORD_RECOVERY",
                target_resource="USER",
                target_id=user.id,
                status="SUCCESS",
                correlation_id=str(uuid.uuid4()),
                metadata_json=json.dumps(
                    {
                        "method": "operator_cli",
                        "sessions_revoked": revoked_count,
                        "mfa_preserved": True,
                        "admin_role_preserved": True,
                    },
                    sort_keys=True,
                ),
            )
        )
        await db.commit()
        return {
            "status": "RECOVERY_SUCCESS",
            "user_id": user.id,
            "email": normalized_email,
            "sessions_revoked": revoked_count,
            "mfa_preserved": True,
            "admin_role_preserved": True,
        }
    except Exception:
        await db.rollback()
        raise


def require_execute_confirmation(args: argparse.Namespace) -> None:
    if args.execute and not args.confirm_production:
        raise RecoverySafetyError(
            "Every --execute run requires --confirm-production, regardless of local ENVIRONMENT."
        )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Secure operator-only Admin password recovery (no HTTP endpoint)."
    )
    parser.add_argument("--email", required=True, help="Exact Admin account email.")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true", help="Read-only preflight.")
    mode.add_argument("--execute", action="store_true", help="Perform password recovery.")
    parser.add_argument(
        "--confirm-email",
        help="Required with --execute; must exactly match --email.",
    )
    parser.add_argument(
        "--confirm-production",
        action="store_true",
        help="Required when ENVIRONMENT=production.",
    )
    return parser


async def async_main(args: argparse.Namespace) -> int:
    try:
        # Validate the operator acknowledgement before opening a database connection.
        require_execute_confirmation(args)
        async with async_session_maker() as db:
            if args.check:
                user, active_sessions = await inspect_admin_target(db, args.email)
                print(
                    json.dumps(
                        {
                            "status": "PREFLIGHT_VALID",
                            "user_id": user.id,
                            "email": normalize_email(user.email or ""),
                            "role": user.role,
                            "mfa_enabled": bool(user.mfa_enabled),
                            "active_sessions": active_sessions,
                            "mutations_committed": False,
                        },
                        sort_keys=True,
                    )
                )
                return 0

            if not args.confirm_email:
                raise RecoverySafetyError("--confirm-email is required with --execute.")
            first = getpass.getpass("New Admin password (hidden): ")
            second = getpass.getpass("Confirm new Admin password (hidden): ")
            if first != second:
                raise RecoverySafetyError("Password confirmation does not match.")
            result = await reset_admin_password(
                db,
                email=args.email,
                new_password=first,
                confirm_email=args.confirm_email,
                environment=settings.ENVIRONMENT,
                confirm_production=args.confirm_production,
            )
            print(json.dumps(result, sort_keys=True))
            return 0
    except RecoverySafetyError as exc:
        print(f"RECOVERY BLOCKED: {exc}", file=sys.stderr)
        return 2
    finally:
        await engine.dispose()


def main() -> int:
    args = build_parser().parse_args()
    return asyncio.run(async_main(args))


if __name__ == "__main__":
    raise SystemExit(main())
