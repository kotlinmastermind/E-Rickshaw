#!/usr/bin/env python3
"""
VAISENTRA MOBILITY — Secure Super Admin Bootstrap Implementation Utility
Repository: C:\\androidAi
Target Account: kotlindeveloper75620@gmail.com (ID: 1)
Target Role: Super Administrator (System Role: "admin")

PURPOSE:
Safely and transactionally prepare the personal owner account as the Super Administrator
without modifying or exposing existing passwords, and re-arm the standard MFA enrollment
lifecycle so the owner can securely enroll their authenticator upon next sign-in.

MODES:
  --check    : Read-only preflight validation of database, account, and roles.
               (Safe against any database target; performs ZERO mutations).
  --dry-run  : Simulates the bootstrap transaction and rolls back without writing.
  --execute  : Applies bootstrap changes. Blocked against non-local / production targets
               unless explicitly authorized with --allow-production-execution.
"""

import sys
import os
import re
import argparse
import asyncio
import logging
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Optional, List, Dict, Any, Tuple

# Ensure project root is in sys.path
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from sqlalchemy import select, update, func
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.engine import make_url

from backend.core.config import settings
from backend.domain.models import User, AdminRole, AdminUserRole, Session, AuditLog
from backend.infrastructure.database.session import async_session_maker

logger = logging.getLogger("admin_bootstrap")

# Target configuration constants
DEFAULT_TARGET_EMAIL = "kotlindeveloper75620@gmail.com"
DEFAULT_TARGET_USER_ID = "1"
SUPER_ROLE_NAME = "Super Administrator"
SYSTEM_ADMIN_ROLE = "admin"

# SEC-01: Positive local database allowlist
LOCAL_HOST_ALLOWLIST = {"localhost", "127.0.0.1", "::1", "[::1]"}

# Known cloud production provider indicators
PROD_HOST_KEYWORDS = [
    "supabase.com",
    "supabase.co",
    "pooler.supabase.com",
    "onrender.com",
    "render.com",
    "aws-0-ap-southeast-2"
]


class BootstrapSecurityError(Exception):
    """Raised when any security boundary or preflight check is violated."""
    pass


class ProductionBlockedError(BootstrapSecurityError):
    """Raised when an unauthorized attempt is made to execute against a production database."""
    pass


@dataclass
class PreflightReport:
    is_valid: bool
    user_id: str
    email: str
    full_name: Optional[str]
    current_role: str
    is_active: bool
    is_verified: bool
    has_password: bool
    current_mfa_enabled: bool
    has_mfa_secret: bool
    super_role_id: Optional[str]
    super_role_is_superuser: bool
    existing_mappings: List[str] = field(default_factory=list)
    active_session_count: int = 0
    errors: List[str] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)


def _normalize_db_url(db_url: str) -> str:
    """
    Normalizes database URLs for safe make_url parsing.
    Specifically handles unbracketed IPv6 loopback (::1) which standard URL
    parsers fail to split from port without RFC 3986 brackets.
    """
    if not db_url or not db_url.strip():
        return db_url
    url = re.sub(r'://::1(?=[:/])', '://[::1]', db_url)
    url = re.sub(r'@::1(?=[:/])', '@[::1]', url)
    return url


def sanitize_db_target(db_url: str) -> str:
    """
    Returns scheme and host/port without exposing usernames, passwords, or database paths.
    """
    if not db_url or not db_url.strip():
        return "[empty-url]"
    try:
        parsed = make_url(_normalize_db_url(db_url))
        host = parsed.host or ("in-memory" if parsed.database and ":memory:" in parsed.database else "local-file")
        port = f":{parsed.port}" if parsed.port else ""
        return f"{parsed.drivername}://{host}{port}"
    except Exception:
        return "[unparseable-url]"


def is_permitted_local_target(db_url: str) -> Tuple[bool, str]:
    """
    SEC-01: Enforces a positive allowlist for non-production targets:
      - SQLite (aiosqlite, in-memory, or local file)
      - localhost
      - 127.0.0.1
      - IPv6 loopback ::1 / [::1]
    Rejects remote IP addresses, unknown hostnames, and cloud endpoints.
    """
    if not db_url or not db_url.strip():
        return False, "Database URL is empty or missing"

    try:
        parsed = make_url(_normalize_db_url(db_url))
    except Exception as e:
        return False, f"Invalid database URL format: {type(e).__name__}"

    driver = (parsed.drivername or "").lower()
    if driver.startswith("sqlite"):
        return True, f"SQLite local database ({sanitize_db_target(db_url)})"

    host = (parsed.host or "").strip().lower()
    if not host:
        return False, "Non-SQLite database URL without hostname"

    host_clean = host.strip("[]")
    if host_clean in LOCAL_HOST_ALLOWLIST or host in LOCAL_HOST_ALLOWLIST:
        return True, f"Permitted local loopback host: '{host}'"

    return False, f"Remote/non-allowlisted host: '{host}'"


def is_known_production_target(db_url: str) -> Tuple[bool, str]:
    """
    Detects whether the target database URL or environment corresponds to production.
    """
    env = os.getenv("ENVIRONMENT", settings.ENVIRONMENT or "").lower()
    if env in ("production", "prod"):
        return True, f"ENVIRONMENT is set to '{env}'"

    if not db_url or not db_url.strip():
        return False, "No database URL configured"

    try:
        parsed = make_url(_normalize_db_url(db_url))
        host = (parsed.host or "").lower()
    except Exception:
        host = db_url.lower()

    for keyword in PROD_HOST_KEYWORDS:
        if keyword in host or keyword in db_url.lower():
            return True, f"Target matches production keyword: '{keyword}'"

    return False, "Non-production target"


def validate_execution_safety(
    db_url: str,
    mode: str,
    allow_production_execution: bool = False
) -> Tuple[bool, Optional[str]]:
    """
    SEC-01 & SEC-02:
    - mode='check' is read-only and permitted against any valid target without extra flags.
    - mode='dry-run' or 'execute' against non-local or production targets strictly requires
      the explicit --allow-production-execution flag.
    """
    mode = mode.lower()
    if mode not in ("check", "dry-run", "execute"):
        return False, f"Invalid execution mode '{mode}'. Choose 'check', 'dry-run', or 'execute'."

    # Read-only check mode is always permitted safely
    if mode == "check":
        return True, None

    is_local, local_reason = is_permitted_local_target(db_url)
    is_prod, prod_reason = is_known_production_target(db_url)

    # Write operations against non-local or production targets require explicit authorization
    if not is_local or is_prod:
        if not allow_production_execution:
            target_str = sanitize_db_target(db_url)
            reason = prod_reason if is_prod else local_reason
            return False, (
                f"Production/Remote Safety Violation: Target database '{target_str}' is not an authorized local host ({reason}). "
                f"Execution in '{mode}' mode requires explicit flag '--allow-production-execution'."
            )

    return True, None


async def run_preflight_checks(
    session: AsyncSession,
    target_email: str = DEFAULT_TARGET_EMAIL,
    target_user_id: str = DEFAULT_TARGET_USER_ID
) -> PreflightReport:
    """
    SEC-02: Performs comprehensive read-only validation of target account, roles, and schema.
    ZERO mutations are performed during this inspection.
    """
    errors: List[str] = []
    warnings: List[str] = []
    normalized_target_email = target_email.strip().lower()

    # 1. Fetch user by primary key
    stmt = select(User).where(User.id == str(target_user_id))
    res = await session.execute(stmt)
    user = res.scalar_one_or_none()

    if not user:
        return PreflightReport(
            is_valid=False,
            user_id=target_user_id,
            email=normalized_target_email,
            full_name=None,
            current_role="",
            is_active=False,
            is_verified=False,
            has_password=False,
            current_mfa_enabled=False,
            has_mfa_secret=False,
            super_role_id=None,
            super_role_is_superuser=False,
            errors=[f"Target user with ID '{target_user_id}' does not exist in the database."]
        )

    # 2. Strict ID + Email correspondence
    user_email_norm = (user.email or "").strip().lower()
    if user_email_norm != normalized_target_email:
        errors.append(
            f"Security Mismatch: User ID '{target_user_id}' belongs to email '{user_email_norm}', "
            f"not target '{normalized_target_email}'. Refusing to proceed."
        )

    # 3. Account status checks
    if not user.is_active:
        errors.append("Target account is inactive (is_active=False).")

    if not user.is_verified:
        errors.append("Target account is not verified (is_verified=False).")

    has_password = bool(user.password_hash and user.password_hash.strip())
    if not has_password:
        errors.append("Target account has no password hash set (password_hash is NULL or empty).")

    has_mfa_secret = bool(user.mfa_secret and user.mfa_secret.strip())
    if user.mfa_enabled and not has_mfa_secret:
        warnings.append(
            "Account has mfa_enabled=True but mfa_secret is NULL. This represents an un-enrollable state."
        )

    # 4. Super Administrator Role verification
    r_stmt = select(AdminRole).where(AdminRole.name == SUPER_ROLE_NAME)
    r_res = await session.execute(r_stmt)
    super_role = r_res.scalar_one_or_none()

    super_role_id = None
    super_role_is_superuser = False
    if not super_role:
        errors.append(f"Required role '{SUPER_ROLE_NAME}' does not exist in 'admin_roles' table.")
    else:
        super_role_id = str(super_role.id)
        super_role_is_superuser = bool(super_role.is_superuser)
        if not super_role.is_superuser:
            errors.append(f"Role '{SUPER_ROLE_NAME}' exists but is_superuser is False.")

    # 5. Role mappings inspection
    aur_stmt = select(AdminUserRole).where(AdminUserRole.user_id == user.id)
    aur_res = await session.execute(aur_stmt)
    existing_mappings = [str(r.role_id) for r in aur_res.scalars().all()]

    # 6. Active sessions inspection
    sess_stmt = select(func.count(Session.id)).where(Session.user_id == user.id, Session.is_revoked == False)
    sess_res = await session.execute(sess_stmt)
    active_session_count = int(sess_res.scalar() or 0)

    is_valid = len(errors) == 0

    return PreflightReport(
        is_valid=is_valid,
        user_id=str(user.id),
        email=user_email_norm,
        full_name=user.full_name,
        current_role=str(user.role or ""),
        is_active=bool(user.is_active),
        is_verified=bool(user.is_verified),
        has_password=has_password,
        current_mfa_enabled=bool(user.mfa_enabled),
        has_mfa_secret=has_mfa_secret,
        super_role_id=super_role_id,
        super_role_is_superuser=super_role_is_superuser,
        existing_mappings=existing_mappings,
        active_session_count=active_session_count,
        errors=errors,
        warnings=warnings
    )


async def execute_bootstrap(
    session: AsyncSession,
    mode: str,
    target_email: str = DEFAULT_TARGET_EMAIL,
    target_user_id: str = DEFAULT_TARGET_USER_ID,
    allow_production_execution: bool = False,
    db_url: Optional[str] = None
) -> Dict[str, Any]:
    """
    Executes the bootstrap workflow in check, dry-run, or execute mode.
    Guarantees full transactional rollback on failure or in dry-run mode.
    """
    mode = mode.lower()
    effective_db_url = db_url or settings.DATABASE_URL

    # Validate execution safety according to SEC-01 and SEC-02
    allowed, safety_reason = validate_execution_safety(
        db_url=effective_db_url,
        mode=mode,
        allow_production_execution=allow_production_execution
    )
    if not allowed:
        raise ProductionBlockedError(safety_reason)

    # 1. Run Preflight
    report = await run_preflight_checks(session, target_email, target_user_id)
    if not report.is_valid:
        raise BootstrapSecurityError(
            f"Preflight validation failed with {len(report.errors)} error(s): {'; '.join(report.errors)}"
        )

    # 2. Prepare Transaction Plan
    plan = {
        "user_id": report.user_id,
        "email": report.email,
        "current_role": report.current_role,
        "target_role": SYSTEM_ADMIN_ROLE,
        "role_update_needed": report.current_role.lower() != SYSTEM_ADMIN_ROLE,
        "super_role_id": report.super_role_id,
        "already_mapped": bool(report.super_role_id and report.super_role_id in report.existing_mappings),
        "mfa_reset_planned": True,  # Disarms deadlock so user can clean-enroll
        "sessions_to_revoke": report.active_session_count
    }

    # SEC-02: Check mode strictly performs zero mutations and returns report
    if mode == "check":
        return {
            "mode": "check",
            "status": "VALIDATED",
            "report": report,
            "plan": plan
        }

    # 3. Apply mutations inside explicit transaction
    try:
        user = await session.get(User, report.user_id)
        if not user:
            raise BootstrapSecurityError(f"User {report.user_id} disappeared during transaction.")

        # Update role to system admin role ("admin")
        user.role = SYSTEM_ADMIN_ROLE

        # Reset MFA state for clean Authenticator setup
        user.mfa_enabled = False
        user.mfa_secret = None

        # SEC-03: Ensure Super Administrator mapping exists without deleting other valid role assignments
        if report.super_role_id and report.super_role_id not in report.existing_mappings:
            new_aur = AdminUserRole(
                user_id=user.id,
                role_id=report.super_role_id,
                assigned_by="SYSTEM_BOOTSTRAP"
            )
            session.add(new_aur)

        # Revoke existing sessions to enforce fresh MFA
        await session.execute(
            update(Session)
            .where(Session.user_id == user.id, Session.is_revoked == False)
            .values(is_revoked=True)
        )

        # Audit log creation (never storing secrets or hashes)
        audit_entry = AuditLog(
            actor_id=user.id,
            actor_role="SYSTEM",
            action="SUPER_ADMIN_BOOTSTRAP",
            target_resource="users",
            target_id=user.id,
            status="SUCCESS" if mode == "execute" else "SIMULATED",
            metadata_json=f'{{"mode": "{mode}", "role": "{SYSTEM_ADMIN_ROLE}", "super_role_id": "{report.super_role_id}"}}'
        )
        session.add(audit_entry)

        if mode == "dry-run":
            # Simulate and strictly rollback
            await session.rollback()
            return {
                "mode": "dry-run",
                "status": "SIMULATED_SUCCESS",
                "mutations_committed": False,
                "plan": plan
            }
        elif mode == "execute":
            # Commit the authorized changes
            await session.commit()
            return {
                "mode": "execute",
                "status": "EXECUTED_SUCCESS",
                "mutations_committed": True,
                "plan": plan
            }

    except Exception as e:
        await session.rollback()
        raise e


def parse_arguments(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="VAISENTRA MOBILITY Super Admin Bootstrap Utility",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--check", action="store_true", help="Perform read-only preflight inspection (safe against any database).")
    group.add_argument("--dry-run", action="store_true", help="Simulate bootstrap transaction with automatic rollback.")
    group.add_argument("--execute", action="store_true", help="Apply bootstrap changes.")

    parser.add_argument("--email", default=DEFAULT_TARGET_EMAIL, help=f"Target administrator email (default: {DEFAULT_TARGET_EMAIL})")
    parser.add_argument("--user-id", default=DEFAULT_TARGET_USER_ID, help=f"Target user ID (default: {DEFAULT_TARGET_USER_ID})")
    parser.add_argument("--confirm-owner-authorization", type=str, help="Required email confirmation string when --execute is used.")
    parser.add_argument("--allow-production-execution", action="store_true", help="Explicit authorization flag required for non-local / production database execution.")

    return parser.parse_args(argv)


async def main_async(argv: Optional[List[str]] = None) -> int:
    args = parse_arguments(argv)

    mode = "check" if args.check else "dry-run" if args.dry_run else "execute"

    print("================================================================================")
    print("       VAISENTRA MOBILITY — SUPER ADMIN BOOTSTRAP UTILITY")
    print(f"       Mode: {mode.upper()} | Target: {args.email} (ID: {args.user_id})")
    print("================================================================================")

    # Validate execution confirmation
    if mode == "execute":
        if args.confirm_owner_authorization != args.email:
            print("ERROR: --execute requires --confirm-owner-authorization matching the target email.")
            return 1

    # SEC-01 & SEC-02: Check connection target safety upfront
    allowed, safety_reason = validate_execution_safety(
        db_url=settings.DATABASE_URL,
        mode=mode,
        allow_production_execution=args.allow_production_execution
    )
    sanitized_url = sanitize_db_target(settings.DATABASE_URL)
    print(f"[*] Connection Target: {sanitized_url}")

    if not allowed:
        print(f"FATAL: {safety_reason}")
        return 2

    async with async_session_maker() as session:
        try:
            result = await execute_bootstrap(
                session=session,
                mode=mode,
                target_email=args.email,
                target_user_id=args.user_id,
                allow_production_execution=args.allow_production_execution
            )

            print(f"\n[+] Status: {result['status']}")
            plan = result.get("plan", {})
            print(f"[*] Target User ID:      {plan.get('user_id')}")
            print(f"[*] Target Email:        {plan.get('email')}")
            print(f"[*] Role Update:         {plan.get('current_role')} -> {plan.get('target_role')}")
            print(f"[*] Super Role Mapped:   {plan.get('already_mapped')}")
            print(f"[*] Sessions Revoked:    {plan.get('sessions_to_revoke')}")

            if mode == "check":
                rep = result.get("report")
                if rep and rep.warnings:
                    print("\n[!] Preflight Warnings:")
                    for w in rep.warnings:
                        print(f"    - {w}")
                print("\n[✓] Preflight check PASSED cleanly (READ-ONLY). Account is eligible for bootstrap.")

            elif mode == "dry-run":
                print("\n[✓] Dry-run SIMULATION SUCCESSFUL. No changes were committed to database.")

            elif mode == "execute":
                print("\n[✓] Bootstrap EXECUTED successfully. Changes committed to database.")

            return 0

        except BootstrapSecurityError as bse:
            print(f"\n[!] Bootstrap Security Abort: {bse}")
            return 1
        except Exception as e:
            print(f"\n[!] Unexpected Error: {e}")
            return 3


def main(argv: Optional[List[str]] = None) -> int:
    return asyncio.run(main_async(argv))


if __name__ == "__main__":
    sys.exit(main())
