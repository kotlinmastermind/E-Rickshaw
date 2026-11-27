"""Operator-only Admin MFA authenticator replacement.

Run from the repository root:
  python -m backend.scripts.recover_admin_mfa --email admin@example.com --check
  python -m backend.scripts.recover_admin_mfa --email admin@example.com --execute --confirm-email admin@example.com --confirm-production

Execution requires the current Admin password entered through getpass. The utility does
not disable MFA: it atomically replaces the TOTP secret while keeping MFA enabled,
revokes sessions, and records an audit event. The new secret is displayed once.
No HTTP endpoint is exposed.
"""
from __future__ import annotations

import argparse
import asyncio
import getpass
import json
import sys
import uuid
from typing import Any
from urllib.parse import quote

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from backend.core.config import settings
from backend.domain.models import AuditLog, Session
from backend.infrastructure.cache.redis_client import redis_client
from backend.infrastructure.database.session import async_session_maker, engine
from backend.infrastructure.security.crypto import generate_mfa_secret, verify_password
from backend.scripts.recover_admin_password import (
    RecoverySafetyError,
    inspect_admin_target,
    normalize_email,
)


async def revoke_session_cache(session_ids: list[str]) -> None:
    """Revoke only existing sessions; do not block future logins for this user."""
    for session_id in session_ids:
        await redis_client.set(f"revoked_session:{session_id}", "true", ex=86400 * 7)


async def rotate_admin_mfa(
    db: AsyncSession,
    *,
    email: str,
    current_password: str,
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
            "Production MFA recovery requires the explicit --confirm-production flag."
        )
    if not current_password:
        raise RecoverySafetyError("Current Admin password is required.")

    try:
        user, _ = await inspect_admin_target(db, normalized_email, lock=True)
        if not user.password_hash or not verify_password(current_password, user.password_hash):
            raise RecoverySafetyError("Current Admin password is incorrect; no changes made.")

        # Keep MFA enabled throughout. No password-only login window is created.
        replacement_secret = generate_mfa_secret()
        user.mfa_secret = replacement_secret

        active_session_ids = list(
            (
                await db.execute(
                    select(Session.id).where(
                        Session.user_id == user.id,
                        Session.is_revoked.is_(False),
                    )
                )
            ).scalars().all()
        )

        # Revoke existing session IDs individually. A user-wide Redis deny-list
        # would also reject newly created sessions, so it is intentionally avoided.
        # If Redis is unavailable, fail closed and roll back the DB transaction.
        await revoke_session_cache(active_session_ids)

        revoked_result = await db.execute(
            update(Session)
            .where(Session.user_id == user.id, Session.is_revoked.is_(False))
            .values(is_revoked=True)
        )
        revoked_count = int(revoked_result.rowcount or 0)

        db.add(
            AuditLog(
                actor_id=None,
                actor_role="SYSTEM",
                action="ADMIN_MFA_RECOVERY",
                target_resource="USER",
                target_id=user.id,
                status="SUCCESS",
                correlation_id=str(uuid.uuid4()),
                metadata_json=json.dumps(
                    {
                        "method": "operator_cli",
                        "sessions_revoked": revoked_count,
                        "mfa_enabled_preserved": True,
                        "old_secret_replaced": True,
                        "admin_role_preserved": True,
                    },
                    sort_keys=True,
                ),
            )
        )
        await db.commit()

        label = quote(f"GrandChauffeur:{normalized_email}", safe="")
        issuer = quote("GrandChauffeur", safe="")
        totp_uri = (
            f"otpauth://totp/{label}?secret={replacement_secret}"
            f"&issuer={issuer}&algorithm=SHA1&digits=6&period=30"
        )
        return {
            "status": "MFA_SECRET_ROTATED",
            "email": normalized_email,
            "sessions_revoked": revoked_count,
            "mfa_enabled": True,
            "admin_role_preserved": True,
            "secret": replacement_secret,
            "totp_uri": totp_uri,
            "message": (
                "Add this new key to your authenticator now. The secret is displayed once; "
                "then sign in with your password and the current 6-digit authenticator code."
            ),
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
        description="Secure operator-only Admin MFA authenticator recovery (no HTTP endpoint)."
    )
    parser.add_argument("--email", required=True, help="Exact Admin account email.")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true", help="Read-only preflight.")
    mode.add_argument("--execute", action="store_true", help="Replace the Admin TOTP secret.")
    parser.add_argument(
        "--confirm-email",
        help="Required with --execute; must exactly match --email.",
    )
    parser.add_argument(
        "--confirm-production",
        action="store_true",
        help="Mandatory acknowledgement for every execution.",
    )
    return parser


async def async_main(args: argparse.Namespace) -> int:
    try:
        require_execute_confirmation(args)
        async with async_session_maker() as db:
            if args.check:
                user, active_sessions = await inspect_admin_target(db, args.email)
                print(
                    json.dumps(
                        {
                            "status": "PREFLIGHT_VALID",
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
            current_password = getpass.getpass("Current Admin password (hidden): ")
            result = await rotate_admin_mfa(
                db,
                email=args.email,
                current_password=current_password,
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
