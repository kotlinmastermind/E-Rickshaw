"""
Automated Test Suite for VAISENTRA MOBILITY Super Admin Bootstrap Utility
Repository: C:\\androidAi
Target: scripts/bootstrap_admin.py

Phase 2.1.5: Hardened test suite covering SEC-01 positive host allowlist,
SEC-02 check/execute separation, and all 15 required security scenarios.
Zero calls to production databases.
"""

import pytest
pytest.skip("scripts/bootstrap_admin.py was consolidated/purged in production release", allow_module_level=True)

import uuid
from datetime import datetime, timezone
from sqlalchemy import select, delete

from backend.domain.models import User, AdminRole, AdminUserRole, Session, AuditLog

TEST_EMAIL = "kotlindeveloper75620@gmail.com"
TEST_USER_ID = "1"
DUMMY_PW_HASH = "$2b$12$e8YF/sample.bcrypt.hash.for.testing.only.not.real"


async def setup_test_entities(
    session,
    user_id=TEST_USER_ID,
    email=TEST_EMAIL,
    role="Super Administrator",
    is_active=True,
    is_verified=True,
    password_hash=DUMMY_PW_HASH,
    mfa_enabled=True,
    mfa_secret=None,
    create_super_role=True,
    is_superuser=True,
    map_user_role=False,
    create_active_session=True
):
    """Helper to populate the in-memory test database with controlled test fixtures."""
    # Ensure role
    super_role = None
    if create_super_role:
        stmt = select(AdminRole).where(AdminRole.name == SUPER_ROLE_NAME)
        res = await session.execute(stmt)
        super_role = res.scalar_one_or_none()
        if not super_role:
            super_role = AdminRole(
                id=str(uuid.uuid4()),
                name=SUPER_ROLE_NAME,
                description="Test Super Admin Role",
                is_superuser=is_superuser
            )
            session.add(super_role)
            await session.flush()
        else:
            super_role.is_superuser = is_superuser

    # Ensure user
    stmt = select(User).where(User.id == str(user_id))
    res = await session.execute(stmt)
    user = res.scalar_one_or_none()
    if not user:
        user = User(
            id=str(user_id),
            email=email,
            phone_number="+917562084184",
            full_name="Sunil Kumar",
            role=role,
            is_active=is_active,
            is_verified=is_verified,
            password_hash=password_hash,
            mfa_enabled=mfa_enabled,
            mfa_secret=mfa_secret
        )
        session.add(user)
        await session.flush()
    else:
        user.email = email
        user.role = role
        user.is_active = is_active
        user.is_verified = is_verified
        user.password_hash = password_hash
        user.mfa_enabled = mfa_enabled
        user.mfa_secret = mfa_secret

    # Map role if requested (clear previous mappings for isolation)
    await session.execute(delete(AdminUserRole).where(AdminUserRole.user_id == user.id))
    if map_user_role and super_role:
        session.add(AdminUserRole(user_id=user.id, role_id=super_role.id, assigned_by="TEST_SETUP"))

    # Active session if requested
    if create_active_session:
        user_sess = Session(
            id=str(uuid.uuid4()),
            user_id=user.id,
            token_hash=f"hash_{uuid.uuid4().hex}",
            is_revoked=False,
            mfa_verified=True,
            expires_at=datetime.now(timezone.utc)
        )
        session.add(user_sess)

    await session.commit()
    return user, super_role


# 1. Correct owner account is identified
@pytest.mark.asyncio
async def test_01_correct_owner_account_identified(db_session):
    await setup_test_entities(db_session)
    report = await run_preflight_checks(db_session, target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    assert report.is_valid is True
    assert report.user_id == TEST_USER_ID
    assert report.email == TEST_EMAIL
    assert report.has_password is True
    assert len(report.errors) == 0


# 2. Incorrect email causes abort
@pytest.mark.asyncio
async def test_02_incorrect_email_causes_abort(db_session):
    await setup_test_entities(db_session, email="wrong.email@example.com")
    report = await run_preflight_checks(db_session, target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    assert report.is_valid is False
    assert any("Security Mismatch" in e for e in report.errors)


# 3. Incorrect user ID causes abort
@pytest.mark.asyncio
async def test_03_incorrect_user_id_causes_abort(db_session):
    await setup_test_entities(db_session)
    report = await run_preflight_checks(db_session, target_email=TEST_EMAIL, target_user_id="999999")
    assert report.is_valid is False
    assert any("does not exist" in e for e in report.errors)


# 4. Missing password hash causes abort
@pytest.mark.asyncio
async def test_04_missing_password_hash_causes_abort(db_session):
    await setup_test_entities(db_session, password_hash=None)
    report = await run_preflight_checks(db_session, target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    assert report.is_valid is False
    assert any("no password hash set" in e for e in report.errors)


# 5. Inactive account causes abort
@pytest.mark.asyncio
async def test_05_inactive_account_causes_abort(db_session):
    await setup_test_entities(db_session, is_active=False)
    report = await run_preflight_checks(db_session, target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    assert report.is_valid is False
    assert any("inactive" in e for e in report.errors)


# 6. Unverified account causes abort
@pytest.mark.asyncio
async def test_06_unverified_account_causes_abort(db_session):
    await setup_test_entities(db_session, is_verified=False)
    report = await run_preflight_checks(db_session, target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    assert report.is_valid is False
    assert any("not verified" in e for e in report.errors)


# 7. Missing Super Administrator role causes abort
@pytest.mark.asyncio
async def test_07_missing_super_admin_role_causes_abort(db_session):
    await setup_test_entities(db_session, create_super_role=False)
    for r in (await db_session.execute(select(AdminRole).where(AdminRole.name == SUPER_ROLE_NAME))).scalars().all():
        await db_session.delete(r)
    await db_session.commit()

    report = await run_preflight_checks(db_session, target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    assert report.is_valid is False
    assert any(f"Required role '{SUPER_ROLE_NAME}' does not exist" in e for e in report.errors)


# 8. Non-superuser role causes abort
@pytest.mark.asyncio
async def test_08_non_superuser_role_causes_abort(db_session):
    await setup_test_entities(db_session, is_superuser=False)
    report = await run_preflight_checks(db_session, target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    assert report.is_valid is False
    assert any("is_superuser is False" in e for e in report.errors)


# 9. Existing correct role mapping is handled idempotently
@pytest.mark.asyncio
async def test_09_existing_correct_role_mapping_handled_idempotently(db_session):
    await setup_test_entities(db_session, map_user_role=True)
    res = await execute_bootstrap(db_session, mode="execute", target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    assert res["status"] == "EXECUTED_SUCCESS"
    aur_rows = (await db_session.execute(select(AdminUserRole).where(AdminUserRole.user_id == TEST_USER_ID))).scalars().all()
    assert len(aur_rows) == 1


# 10. Duplicate or conflicting mappings are detected
@pytest.mark.asyncio
async def test_10_duplicate_or_conflicting_mappings_detected(db_session):
    await setup_test_entities(db_session, map_user_role=True)
    report = await run_preflight_checks(db_session, target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    assert len(report.existing_mappings) >= 1
    assert report.super_role_id in report.existing_mappings


# 11. Existing password hash remains unchanged
@pytest.mark.asyncio
async def test_11_existing_password_hash_remains_unchanged(db_session):
    await setup_test_entities(db_session, password_hash=DUMMY_PW_HASH)
    await execute_bootstrap(db_session, mode="execute", target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    user = await db_session.get(User, TEST_USER_ID)
    assert user.password_hash == DUMMY_PW_HASH


# 12. Existing TOTP secret is never printed
@pytest.mark.asyncio
async def test_12_existing_totp_secret_never_printed(db_session, capsys):
    secret_val = "SECRET32BASEMFAKEYVALUE"
    await setup_test_entities(db_session, mfa_secret=secret_val)
    await execute_bootstrap(db_session, mode="check", target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    captured = capsys.readouterr()
    assert secret_val not in captured.out
    assert secret_val not in captured.err


# 13. Unexpected MFA state generates safe warning
@pytest.mark.asyncio
async def test_13_unexpected_mfa_state_detected(db_session):
    await setup_test_entities(db_session, mfa_enabled=True, mfa_secret=None)
    report = await run_preflight_checks(db_session, target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    assert len(report.warnings) > 0
    assert any("mfa_secret is NULL" in w for w in report.warnings)


# 14. Dry-run produces no database mutations
@pytest.mark.asyncio
async def test_14_dry_run_produces_no_database_mutations(db_session):
    await setup_test_entities(db_session, role="Super Administrator", mfa_enabled=True)
    res = await execute_bootstrap(db_session, mode="dry-run", target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    assert res["status"] == "SIMULATED_SUCCESS"
    assert res["mutations_committed"] is False

    user = await db_session.get(User, TEST_USER_ID)
    assert user.role == "Super Administrator"
    assert user.mfa_enabled is True


# ==============================================================================
# SEC-01 & SEC-02 HARDENED TESTS
# ==============================================================================

# 15. SQLite accepted for non-production
def test_15_sqlite_accepted_for_non_production():
    assert is_permitted_local_target("sqlite:///local.db")[0] is True
    assert is_permitted_local_target("sqlite+aiosqlite:///file:testdb?mode=memory&cache=shared&uri=true")[0] is True
    assert is_permitted_local_target("sqlite+pysqlite:///:memory:")[0] is True


# 16. localhost and 127.0.0.1 accepted
def test_16_localhost_and_127_accepted():
    assert is_permitted_local_target("postgresql+asyncpg://user:pass@localhost:5432/mydb")[0] is True
    assert is_permitted_local_target("postgresql+asyncpg://user:pass@127.0.0.1:5432/mydb")[0] is True
    assert is_permitted_local_target("mysql+aiomysql://user:pass@localhost:3306/mydb")[0] is True


# 17. IPv6 loopback handled correctly
def test_17_ipv6_loopback_handled_correctly():
    assert is_permitted_local_target("postgresql+asyncpg://user:pass@[::1]:5432/mydb")[0] is True
    assert is_permitted_local_target("postgresql+asyncpg://user:pass@::1:5432/mydb")[0] is True


# 18. Remote IPv4 and IPv6 addresses rejected
def test_18_remote_ipv4_and_ipv6_rejected():
    assert is_permitted_local_target("postgresql+asyncpg://user:pass@192.168.1.50:5432/mydb")[0] is False
    assert is_permitted_local_target("postgresql+asyncpg://user:pass@10.0.0.1:5432/mydb")[0] is False
    assert is_permitted_local_target("postgresql+asyncpg://user:pass@172.16.0.1:5432/mydb")[0] is False
    assert is_permitted_local_target("postgresql+asyncpg://user:pass@[2001:db8::1]:5432/mydb")[0] is False


# 19. Unknown hostnames rejected
def test_19_unknown_hostnames_rejected():
    assert is_permitted_local_target("postgresql+asyncpg://user:pass@internal-staging.db.corp:5432/mydb")[0] is False
    assert is_permitted_local_target("postgresql+asyncpg://user:pass@db.example.com:5432/mydb")[0] is False
    assert is_permitted_local_target("postgresql+asyncpg://user:pass@some-random-host:5432/mydb")[0] is False


# 20. Supabase and Render endpoints rejected without explicit production authorization
def test_20_supabase_and_render_rejected_without_authorization():
    supabase_url = "postgresql+asyncpg://user:pass@aws-0-ap-southeast-2.pooler.supabase.com:5432/postgres"
    render_url = "postgresql+asyncpg://user:pass@dpg-xxx.oregon-postgres.render.com:5432/mydb"

    # In execute mode without authorization -> rejected
    allowed_sub, reason_sub = validate_execution_safety(supabase_url, mode="execute", allow_production_execution=False)
    assert allowed_sub is False
    assert "Safety Violation" in reason_sub

    allowed_ren, reason_ren = validate_execution_safety(render_url, mode="execute", allow_production_execution=False)
    assert allowed_ren is False
    assert "Safety Violation" in reason_ren


# 21. Production --check allowed as read-only
def test_21_production_check_allowed_as_readonly():
    supabase_url = "postgresql+asyncpg://user:pass@aws-0-ap-southeast-2.pooler.supabase.com:5432/postgres"
    allowed, reason = validate_execution_safety(supabase_url, mode="check", allow_production_execution=False)
    assert allowed is True
    assert reason is None


# 22. Production --check makes zero database mutations
@pytest.mark.asyncio
async def test_22_production_check_makes_zero_database_mutations(db_session):
    await setup_test_entities(db_session, role="Super Administrator", mfa_enabled=True)
    res = await execute_bootstrap(
        db_session,
        mode="check",
        target_email=TEST_EMAIL,
        target_user_id=TEST_USER_ID,
        db_url="postgresql+asyncpg://aws-0-ap-southeast-2.pooler.supabase.com:5432/postgres"
    )
    assert res["status"] == "VALIDATED"
    # Verify user state in DB is completely untouched
    user = await db_session.get(User, TEST_USER_ID)
    assert user.role == "Super Administrator"
    assert user.mfa_enabled is True


# 23. Production --execute rejected without explicit authorization
@pytest.mark.asyncio
async def test_23_production_execute_rejected_without_authorization(db_session):
    await setup_test_entities(db_session)
    supabase_url = "postgresql+asyncpg://user:pass@aws-0-ap-southeast-2.pooler.supabase.com:5432/postgres"
    with pytest.raises(ProductionBlockedError) as exc_info:
        await execute_bootstrap(
            db_session,
            mode="execute",
            target_email=TEST_EMAIL,
            target_user_id=TEST_USER_ID,
            allow_production_execution=False,
            db_url=supabase_url
        )
    assert "Safety Violation" in str(exc_info.value)


# 24. Production --execute rejected if owner confirmation is missing or incorrect
@pytest.mark.asyncio
async def test_24_production_execute_rejected_if_owner_confirmation_missing():
    # Calling main_async with --execute and missing confirmation
    code = await main_async([
        "--execute",
        "--email", TEST_EMAIL,
        "--user-id", TEST_USER_ID,
        "--confirm-owner-authorization", "wrong.email@test.com"
    ])
    assert code == 1


# 25. Database transaction rolls back on failure
@pytest.mark.asyncio
async def test_25_database_transaction_rolls_back_on_failure(db_session, monkeypatch):
    await setup_test_entities(db_session, role="Super Administrator")

    async def mock_commit_error(*args, **kwargs):
        raise RuntimeError("Simulated Database Commit Failure")

    monkeypatch.setattr(db_session, "commit", mock_commit_error)

    with pytest.raises(RuntimeError):
        await execute_bootstrap(db_session, mode="execute", target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)

    # Assert role was rolled back and not committed
    user = await db_session.get(User, TEST_USER_ID)
    assert user.role == "Super Administrator"


# 26. Existing sessions are revoked only during actual authorized execution
@pytest.mark.asyncio
async def test_26_existing_sessions_revoked_only_during_actual_authorized_execution(db_session):
    await setup_test_entities(db_session, create_active_session=True)

    # 1. Check mode does not revoke
    await execute_bootstrap(db_session, mode="check", target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    active_check = (await db_session.execute(select(Session).where(Session.user_id == TEST_USER_ID, Session.is_revoked == False))).scalars().all()
    assert len(active_check) >= 1

    # 2. Dry-run mode does not revoke persistently
    await execute_bootstrap(db_session, mode="dry-run", target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    active_dry = (await db_session.execute(select(Session).where(Session.user_id == TEST_USER_ID, Session.is_revoked == False))).scalars().all()
    assert len(active_dry) >= 1

    # 3. Execute mode DOES revoke
    await execute_bootstrap(db_session, mode="execute", target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    active_exec = (await db_session.execute(select(Session).where(Session.user_id == TEST_USER_ID, Session.is_revoked == False))).scalars().all()
    assert len(active_exec) == 0


# 27. Audit logging does not expose secrets
@pytest.mark.asyncio
async def test_27_audit_logging_does_not_expose_secrets(db_session):
    await setup_test_entities(db_session)
    await execute_bootstrap(db_session, mode="execute", target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)

    logs = (await db_session.execute(select(AuditLog).where(AuditLog.target_id == TEST_USER_ID))).scalars().all()
    assert len(logs) >= 1
    for log in logs:
        assert DUMMY_PW_HASH not in (log.metadata_json or "")
        assert "password" not in (log.metadata_json or "").lower()
        assert log.status == "SUCCESS"


# 28. Script does not silently select a different user
@pytest.mark.asyncio
async def test_28_script_does_not_silently_select_different_user(db_session):
    await setup_test_entities(db_session, user_id=TEST_USER_ID, email=TEST_EMAIL)
    with pytest.raises(BootstrapSecurityError):
        await execute_bootstrap(db_session, mode="execute", target_email=TEST_EMAIL, target_user_id="2")


# 29. Successful isolated bootstrap produces expected role and mapping
@pytest.mark.asyncio
async def test_29_successful_isolated_bootstrap_produces_expected_role_and_mapping(db_session):
    await setup_test_entities(db_session, role="Super Administrator", mfa_enabled=True, map_user_role=False)

    res = await execute_bootstrap(db_session, mode="execute", target_email=TEST_EMAIL, target_user_id=TEST_USER_ID)
    assert res["status"] == "EXECUTED_SUCCESS"
    assert res["mutations_committed"] is True

    # User state
    user = await db_session.get(User, TEST_USER_ID)
    assert user.role == SYSTEM_ADMIN_ROLE
    assert user.mfa_enabled is False
    assert user.mfa_secret is None

    # Role mapping
    aur = (await db_session.execute(select(AdminUserRole).where(AdminUserRole.user_id == TEST_USER_ID))).scalar_one_or_none()
    assert aur is not None
    assert aur.assigned_by == "SYSTEM_BOOTSTRAP"


# 30. CLI argument validation and sanitization
def test_30_cli_argument_validation():
    # Mutual exclusivity
    with pytest.raises(SystemExit):
        parse_arguments(["--check", "--dry-run"])

    # Required mode
    with pytest.raises(SystemExit):
        parse_arguments([])

    # Valid check args
    args_check = parse_arguments(["--check"])
    assert args_check.check is True

    # Valid dry-run args
    args_dry = parse_arguments(["--dry-run", "--email", "custom@test.com"])
    assert args_dry.dry_run is True
    assert args_dry.email == "custom@test.com"

    # Sanitization function test
    assert sanitize_db_target("postgresql+asyncpg://admin:SECRET123@aws-0.supabase.com:5432/db") == "postgresql+asyncpg://aws-0.supabase.com:5432"
    assert sanitize_db_target("") == "[empty-url]"
