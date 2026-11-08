# VAISENTRA MOBILITY — ADMIN BOOTSTRAP & MFA RECOVERY ARCHITECTURE

**Repository:** `C:\androidAi`  
**Target Identity:** `kotlindeveloper75620@gmail.com` (User ID `1`)  
**Target Role:** Super Administrator (`admin` system role with `Super Administrator` governance mapping)  
**Security Status:** Production Execution Strictly Blocked (Local Code & Isolated Automated Testing Only)  

---

## 1. ARCHITECTURE & CONTEXT

### 1.1 Background & Root Cause
In `ADMIN_ACCOUNT_DISCOVERY_AND_RECOVERY_REPORT.md`, an audit of the Supabase PostgreSQL database and FastAPI backend revealed why the personal owner account (`kotlindeveloper75620@gmail.com`, User ID `1`) could not log into the Admin Mission Control console:

1. **Role Guard Mismatch:**  
   The FastAPI route guard in `backend/application/services/user_governance_service.py` (`login_with_password`) enforces:
   ```python
   if require_admin and user.role.lower() != "admin":
       raise HTTPException(status_code=401, detail="Invalid executive credentials.")
   ```
   In the database, User `1` has `role = "Super Administrator"`. Because `"Super Administrator".lower() != "admin"`, password authentication fails with `401 Unauthorized`.

2. **Un-enrollable MFA State:**  
   In the database, User `1` has `mfa_enabled = True`, but `mfa_secret = NULL` (no base32 TOTP secret was ever generated or saved).
   - Calling `/auth/mfa/challenge-verify` fails with `401 Unauthorized` because `verify_totp(None, code)` evaluates to `False`.
   - Calling `/auth/admin/mfa/setup` fails with `409 Conflict` because the backend checks `if user.mfa_enabled: raise HTTPException(409, "MFA already enabled")`.

3. **Missing Role Mapping:**  
   `backend/api/deps.py` (`get_current_admin`) requires a database relation in `admin_user_roles` linking the user ID to an `AdminRole` record where `is_superuser = True`. User `1` currently lacks an entry in `admin_user_roles`.

---

## 2. THE BOOTSTRAP UTILITY (`scripts/bootstrap_admin.py`)

The utility is located at [`scripts/bootstrap_admin.py`](file:///C:/androidAi/scripts/bootstrap_admin.py). It provides an automated, auditable, and idempotent mechanism to resolve the deadlock while preserving existing password hashes and user identity.

### 2.1 Preflight Validation Checks
Before attempting any database modification, `run_preflight_checks` executes the following read-only verifications:
1. **Target User Exists:** Verifies that User ID `1` exists in the database.
2. **Identity & Email Correspondence:** Asserts that the normalized email on User ID `1` exactly matches `kotlindeveloper75620@gmail.com`. If any mismatch is found, it immediately aborts.
3. **Account Integrity:**
   - Verifies `is_active == True`.
   - Verifies `is_verified == True`.
   - Verifies an existing password hash is present (`password_hash is not NULL`).
4. **Governance Role Integrity:**
   - Queries `admin_roles` for `name == "Super Administrator"`.
   - Asserts that the role exists and has `is_superuser == True`.
5. **Mapping Detection:**
   - Inspects `admin_user_roles` to determine if a mapping already exists.
6. **MFA State Inspection:**
   - Detects if `mfa_enabled == True` while `mfa_secret == NULL`, issuing an explicit audit warning.

---

## 3. SAFE EXECUTION MODES & SECURITY BOUNDARIES

The utility supports three mutually exclusive operational modes designed with strict fail-closed security controls (SEC-01, SEC-02, SEC-03):

### 3.1 SEC-01: Positive Non-Production Host Allowlist
To prevent accidental mutation of remote staging or cloud databases, `scripts/bootstrap_admin.py` enforces a strict positive allowlist via `is_permitted_local_target(db_url)` parsed through `sqlalchemy.engine.make_url`:
- **Permitted Non-Production Targets:**
  - SQLite (in-memory `:memory:` or local file `sqlite+aiosqlite:///...`)
  - IPv4 loopback `127.0.0.1` and hostname `localhost`
  - IPv6 loopback `::1` and RFC 3986 bracketed `[::1]`
- **Rejected Targets (Fail-Closed):**
  - Any remote IPv4 or IPv6 address (e.g. `192.168.x.x`, `10.x.x.x`, `172.16.x.x`)
  - Any un-allowlisted or corporate intranet hostname
  - Cloud database endpoints (e.g. `*.supabase.com`, `*.render.com`, `aws-0-ap-southeast-2`)
- **Target Sanitization (`sanitize_db_target`):**
  - All logs, console messages, and audit traces print sanitized target representations (`{driver}://{host}:{port}`) and strictly strip usernames, passwords, access tokens, query parameters, and database paths.
  - Automatically handles unbracketed IPv6 loopbacks to ensure standards-compliant URL parsing.

### 3.2 SEC-02: Mode Separation & Production Authorization Flags

The modes are partitioned to enable safe live auditing while preventing unauthorized execution:

#### Mode A: `--check` (Safe Read-Only Preflight)
```powershell
python scripts/bootstrap_admin.py --check
```
- **Permission Boundary:** Permitted against **ANY** database target (including local, staging, and live production).
- **Flag Requirements:** Does **not** require `--allow-production-execution` or confirmation flags.
- **Side Effects:** **ZERO** database writes, **ZERO** row locks, **ZERO** session revocations.
- **Output:** Emits a structured diagnostic report detailing target account state, role mapping status, session count, and un-enrollable MFA deadlock warnings.
- **Exit Codes:** `0` (Valid & Ready), `1` (Preflight Failure / Abort).

#### Mode B: `--dry-run` (Transactional Simulation)
```powershell
python scripts/bootstrap_admin.py --dry-run
```
- **Permission Boundary:** Permitted automatically on allowlisted local targets. If targeting a remote or production database, strictly requires `--allow-production-execution`.
- **Side Effects:** Opens a transaction, simulates role update, MFA disarm, mapping preparation, session revocation count, and audit log generation, followed by an unconditional **`ROLLBACK`**.
- **Side Effects Guarantee:** **ZERO** committed changes.

#### Mode C: `--execute` (Authorized Mutation Mode)
```powershell
python scripts/bootstrap_admin.py --execute --confirm-owner-authorization kotlindeveloper75620@gmail.com
```
- **Local Targets:** Requires `--confirm-owner-authorization kotlindeveloper75620@gmail.com`.
- **Production / Remote Targets:** Requires **BOTH**:
  1. `--confirm-owner-authorization kotlindeveloper75620@gmail.com`
  2. `--allow-production-execution`
- If either flag is missing when targeting a remote host or production endpoint, the script immediately aborts with exit code `2` (`ProductionBlockedError`) before touching any data.

### 3.3 SEC-03: Governance Role Mapping Preservation Policy
In `backend/api/deps.py` line 250, administrator authorization checks:
```python
if any(role.is_superuser for role in role_rows):
    return user
```
The authorization engine explicitly accommodates users possessing multiple administrative and governance roles. The bootstrap utility:
1. Queries `admin_user_roles` for existing mappings.
2. If a mapping to `Super Administrator` is already present, it logs that no change is necessary.
3. If absent, it inserts the mapping to `Super Administrator`.
4. **Preservation Guarantee:** It does **NOT** prune, overwrite, or delete any other legitimate roles assigned to the user.

---

## 4. MFA RE-ARMING & RECOVERY LIFECYCLE

Rather than attempting to bypass multi-factor authentication or generate an ephemeral token out-of-band, the bootstrap utility re-arms the existing, production-hardened MFA lifecycle:

```
[Bootstrap Script Executed]
           │
           ▼
[Database Record Updated]
  - user.role = "admin"
  - user.mfa_enabled = False
  - user.mfa_secret = NULL
  - admin_user_roles mapped to Super Administrator
  - existing sessions revoked
           │
           ▼
[Owner Signs In on http://localhost:5173/]
  - Enters kotlindeveloper75620@gmail.com & existing password
  - POST /auth/admin/password/login succeeds
  - Backend detects mfa_enabled == False
  - Returns: {"mfa_setup_required": true}
           │
           ▼
[AdminSignIn.tsx Displays Authenticator Enrollment]
  - Prompts owner to click "Begin authenticator setup"
  - Calls POST /auth/admin/mfa/setup
  - Backend generates fresh base32 secret & OTP URI
  - UI displays secret key & TOTP URI
           │
           ▼
[Owner Enrolls TOTP App]
  - Owner enters 6-digit code from authenticator app
  - Calls POST /auth/admin/mfa/enable
  - Backend validates TOTP code, sets mfa_enabled = True
  - Verified Session issued; RideGoMissionControl mounts
```

### Security Benefits:
1. **Zero Exposure:** Existing and new TOTP secrets are never transmitted across CLI logs or shared chats.
2. **No Bypass:** Console access remains blocked until the owner proves possession of their physical TOTP authenticator device.
3. **Session Invalidation:** All prior sessions are revoked during bootstrap, forcing fresh authentication.

---

## 5. LOCAL TESTING & VERIFICATION

Automated tests for the bootstrap utility are located at [`backend/tests/test_admin_bootstrap.py`](file:///C:/androidAi/backend/tests/test_admin_bootstrap.py).

To execute the 30 automated tests locally:
```powershell
backend\.venv\Scripts\pytest backend\tests\test_admin_bootstrap.py -v
```

### Verified Test Cases (30 Tests Covering SEC-01, SEC-02, SEC-03):
1. **test_01_correct_owner_account_identified:** Validates exact identity lookup for `kotlindeveloper75620@gmail.com` and ID `1`.
2. **test_02_incorrect_email_causes_abort:** Rejects any mismatched email address.
3. **test_03_incorrect_user_id_causes_abort:** Rejects non-existent or mismatched user IDs.
4. **test_04_missing_password_hash_causes_abort:** Fails preflight if target account lacks a password hash.
5. **test_05_inactive_account_causes_abort:** Fails preflight if `is_active` is false.
6. **test_06_unverified_account_causes_abort:** Fails preflight if `is_verified` is false.
7. **test_07_missing_super_admin_role_causes_abort:** Fails if `Super Administrator` role does not exist in `admin_roles`.
8. **test_08_non_superuser_role_causes_abort:** Fails if `Super Administrator` role has `is_superuser = False`.
9. **test_09_existing_correct_role_mapping_handled_idempotently:** Idempotently handles existing valid mapping.
10. **test_10_duplicate_or_conflicting_mappings_detected:** Detects and reports existing duplicate or conflicting role mappings.
11. **test_11_existing_password_hash_remains_unchanged:** Asserts password hash is completely untouched during execution.
12. **test_12_existing_totp_secret_never_printed:** Ensures TOTP secrets and passwords are never logged or exposed.
13. **test_13_unexpected_mfa_state_detected:** Safe detection and warning generation for unexpected MFA states (`mfa_enabled=True`, `mfa_secret=None`).
14. **test_14_dry_run_produces_no_database_mutations:** Validates transactional simulation and unconditional rollback in `--dry-run`.
15. **test_15_sqlite_accepted_for_non_production:** Confirms SQLite (`:memory:` and local paths) is accepted by SEC-01 allowlist.
16. **test_16_localhost_and_127_accepted:** Confirms `localhost` and `127.0.0.1` are accepted by SEC-01 allowlist.
17. **test_17_ipv6_loopback_handled_correctly:** Confirms `[::1]` and unbracketed `::1` loopbacks are accepted.
18. **test_18_remote_ipv4_and_ipv6_rejected:** Asserts remote IPs (`192.168.1.50`, `10.0.0.1`, `[2001:db8::1]`) are rejected.
19. **test_19_unknown_hostnames_rejected:** Asserts unknown or internal hostnames (`internal-staging.db.corp`) are rejected.
20. **test_20_supabase_and_render_rejected_without_authorization:** Rejects Supabase and Render hosts in execute/dry-run unless authorized.
21. **test_21_production_check_allowed_as_readonly:** Asserts `--check` is permitted against production without authorization flags.
22. **test_22_production_check_makes_zero_database_mutations:** Asserts `--check` against production executes ZERO database writes.
23. **test_23_production_execute_rejected_without_authorization:** Rejects production `--execute` when `--allow-production-execution` is missing.
24. **test_24_production_execute_rejected_if_owner_confirmation_missing:** Rejects production `--execute` when `--confirm-owner-authorization` is missing or mismatched.
25. **test_25_database_transaction_rolls_back_on_failure:** Asserts clean rollback when any database operation fails.
26. **test_26_existing_sessions_revoked_only_during_actual_authorized_execution:** Confirms active sessions are preserved during `--check` and `--dry-run`, revoked only on authorized `--execute`.
27. **test_27_audit_logging_does_not_expose_secrets:** Verifies `AuditLog` metadata contains no credentials or secret data.
28. **test_28_script_does_not_silently_select_different_user:** Verifies script strictly validates single owner identity.
29. **test_29_successful_isolated_bootstrap_produces_expected_role_and_mapping:** End-to-end isolated execution test verifying role change, MFA reset, Super Administrator mapping, and audit log.
30. **test_30_cli_argument_validation:** Verifies argument parser validation, mutual exclusivity, and target sanitization.

---

## 6. AUTHORIZED PRODUCTION RECOVERY PROCEDURE

> [!CAUTION]
> **Strict Safety Notice:**
> No database mutations have been executed against the production Supabase database.
> The steps below are documented exclusively for when the repository owner provides explicit authorization.

When authorized, the operator will execute the recovery workflow in exact sequence:

### Step 1: Read-Only Production Preflight Inspection
```powershell
python scripts/bootstrap_admin.py --check
```
* **Safety:** Connects read-only to verify database accessibility, account status, and roles.
* **Mutations:** Zero.
* **Authorization Flags:** None required.

### Step 2: Transactional Simulation (Dry Run)
```powershell
python scripts/bootstrap_admin.py --dry-run --allow-production-execution
```
* **Safety:** Opens a transaction, simulates role update, MFA disarm, mapping preparation, and session revocation, then issues an unconditional `ROLLBACK`.
* **Mutations:** Zero committed changes.

### Step 3: Authorized Execution
```powershell
python scripts/bootstrap_admin.py --execute --confirm-owner-authorization kotlindeveloper75620@gmail.com --allow-production-execution
```
* **Safety:** Applies changes, commits transaction, and logs audit record.
* **Mutations:** Target user `role` set to `admin`, `mfa_enabled` set to `False`, `mfa_secret` set to `None`, `Super Administrator` role mapped, active sessions revoked.

### Step 4: Administrator Sign-In & TOTP Enrollment
1. Open the Admin Portal at `http://localhost:5173/`.
2. Sign in using `kotlindeveloper75620@gmail.com` and existing password.
3. The backend detects `mfa_enabled == False` and responds with `{"mfa_setup_required": true}`.
4. Click **Begin authenticator setup**.
5. Scan QR code or copy base32 key into Google Authenticator / 1Password.
6. Enter 6-digit TOTP code to finalize enrollment.
7. Admin session begins and `RideGoMissionControl` mounts with full Super Administrator permissions.

