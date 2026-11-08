import pytest
from datetime import datetime, timezone, timedelta
from httpx import AsyncClient, ASGITransport
from sqlalchemy import select
from unittest.mock import patch

from backend.main import app
from backend.domain.models import (
    User, DriverProfile, DriverDocument, Session, AuditLog,
    AdminRole, AdminPermission, AdminRolePermission, AdminUserRole
)
from backend.infrastructure.security.crypto import (
    create_jwt_token, hash_secret, generate_secure_otp
)
from backend.api.deps import get_current_admin


async def _get_or_create_permission(db_session, key: str, description: str = "Test permission", category: str = "Drivers"):
    perm = (await db_session.execute(select(AdminPermission).where(AdminPermission.permission_key == key))).scalar_one_or_none()
    if not perm:
        perm = AdminPermission(permission_key=key, description=description, category=category)
        db_session.add(perm)
        await db_session.flush()
    return perm


@pytest.mark.asyncio
async def test_ordinary_admin_cannot_revoke_verified_document(db_session):
    """An authorized admin with 'admin.drivers.documents' but without supervisor permissions cannot revoke a VERIFIED document."""
    unique_suffix = id(object())

    # 1. Driver and verified document
    driver_user = User(phone_number=f"+9191{str(unique_suffix)[-8:]}", email=f"ord_drv_{unique_suffix}@erickshaw.in", role="driver", is_active=True)
    db_session.add(driver_user)
    await db_session.flush()

    prof = DriverProfile(user_id=driver_user.id, license_number=f"DL-ORD-{str(unique_suffix)[-4:]}", approval_status="PENDING")
    db_session.add(prof)
    await db_session.flush()

    doc = DriverDocument(
        driver_id=prof.id,
        document_type="DRIVING_LICENSE",
        storage_path="/docs/dl.pdf",
        file_name="dl.pdf",
        mime_type="application/pdf",
        file_size_bytes=1024,
        file_hash_sha256="hash123",
        verification_status="VERIFIED"
    )
    db_session.add(doc)

    # 2. Ordinary Admin (has role with only 'admin.drivers.documents')
    admin_user = User(phone_number=f"+9192{str(unique_suffix)[-8:]}", email=f"ord_admin_{unique_suffix}@erickshaw.in", role="admin", is_active=True, mfa_enabled=True)
    db_session.add(admin_user)
    await db_session.flush()

    admin_role = AdminRole(name=f"Document Reviewer {unique_suffix}", description="Reviewer", is_superuser=False)
    db_session.add(admin_role)
    await db_session.flush()

    perm_doc = await _get_or_create_permission(db_session, "admin.drivers.documents", "Review docs", "Drivers")
    db_session.add(AdminRolePermission(role_id=admin_role.id, permission_id=perm_doc.id))
    db_session.add(AdminUserRole(user_id=admin_user.id, role_id=admin_role.id))

    admin_sess = Session(
        user_id=admin_user.id,
        token_hash=hash_secret(f"{admin_user.id}:admin_token:{generate_secure_otp(12)}"),
        is_revoked=False,
        mfa_verified=True,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=60)
    )
    db_session.add(admin_sess)
    await db_session.commit()

    admin_token = create_jwt_token(user_id=admin_user.id, role="admin", session_id=admin_sess.id, token_type="access")

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        app.dependency_overrides[get_current_admin] = lambda: admin_user
        try:
            # Case A: Ordinary review payload without revocation justification -> 422
            res_no_just = await ac.put(
                f"/api/v1/admin/drivers/{prof.id}/documents/{doc.id}",
                json={"verification_status": "REJECTED", "reason": "Found issue upon second look"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_no_just.status_code == 422
            assert "explicit revocation justification" in res_no_just.text

            # Case B: Supplies revocation justification but lacks supervisor authority -> 403
            res_unauth = await ac.put(
                f"/api/v1/admin/drivers/{prof.id}/documents/{doc.id}",
                json={
                    "verification_status": "REJECTED",
                    "reason": "Revoking license due to suspicion",
                    "revocation_justification": "Detailed operational revocation notes exceeding minimum length"
                },
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_unauth.status_code == 403
            assert "Supervisor authorization" in res_unauth.text

            # Verify document in DB remained VERIFIED
            db_doc = await db_session.get(DriverDocument, doc.id)
            await db_session.refresh(db_doc)
            assert db_doc.verification_status == "VERIFIED"
        finally:
            app.dependency_overrides.pop(get_current_admin, None)


@pytest.mark.asyncio
async def test_supervisor_revocation_success_and_audit(db_session):
    """An authorized supervisor (with 'admin.drivers.approve' or superuser) can revoke a VERIFIED document with valid justification."""
    unique_suffix = id(object())

    driver_user = User(phone_number=f"+9193{str(unique_suffix)[-8:]}", email=f"sup_drv_{unique_suffix}@erickshaw.in", role="driver", is_active=True)
    db_session.add(driver_user)
    await db_session.flush()

    prof = DriverProfile(user_id=driver_user.id, license_number=f"DL-SUP-{str(unique_suffix)[-4:]}", approval_status="PENDING")
    db_session.add(prof)
    await db_session.flush()

    doc = DriverDocument(
        driver_id=prof.id,
        document_type="COMMERCIAL_INSURANCE",
        storage_path="/docs/ins.pdf",
        file_name="ins.pdf",
        mime_type="application/pdf",
        file_size_bytes=2048,
        file_hash_sha256="hashins123",
        verification_status="VERIFIED"
    )
    db_session.add(doc)

    # Supervisor admin user
    sup_user = User(phone_number=f"+9194{str(unique_suffix)[-8:]}", email=f"sup_admin_{unique_suffix}@erickshaw.in", role="admin", is_active=True, mfa_enabled=True)
    db_session.add(sup_user)
    await db_session.flush()

    sup_role = AdminRole(name=f"Partner Manager {unique_suffix}", description="Approver", is_superuser=False)
    db_session.add(sup_role)
    await db_session.flush()

    perm_doc = await _get_or_create_permission(db_session, "admin.drivers.documents", "Review docs", "Drivers")
    perm_appr = await _get_or_create_permission(db_session, "admin.drivers.approve", "Approve drivers", "Drivers")

    db_session.add_all([
        AdminRolePermission(role_id=sup_role.id, permission_id=perm_doc.id),
        AdminRolePermission(role_id=sup_role.id, permission_id=perm_appr.id),
        AdminUserRole(user_id=sup_user.id, role_id=sup_role.id),
    ])

    sup_sess = Session(
        user_id=sup_user.id,
        token_hash=hash_secret(f"{sup_user.id}:sup_token:{generate_secure_otp(12)}"),
        is_revoked=False,
        mfa_verified=True,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=60)
    )
    db_session.add(sup_sess)
    await db_session.commit()

    sup_token = create_jwt_token(user_id=sup_user.id, role="admin", session_id=sup_sess.id, token_type="access")

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        app.dependency_overrides[get_current_admin] = lambda: sup_user
        try:
            revocation_note = "Insurance policy was audited and found to be expired on registry lookup"
            res = await ac.put(
                f"/api/v1/admin/drivers/{prof.id}/documents/{doc.id}",
                json={
                    "verification_status": "REJECTED",
                    "reason": revocation_note,
                    "revocation_justification": revocation_note
                },
                headers={"Authorization": f"Bearer {sup_token}"}
            )
            assert res.status_code == 200, res.text
            data = res.json()
            assert data["verification_status"] == "REJECTED"
            assert data["rejection_reason"] == revocation_note

            # Verify in DB
            db_doc = await db_session.get(DriverDocument, doc.id)
            await db_session.refresh(db_doc)
            assert db_doc.verification_status == "REJECTED"
            assert db_doc.rejection_reason == revocation_note

            # Check audit log in DB
            audit_stmt = select(AuditLog).where(
                AuditLog.target_resource == "driver_documents",
                AuditLog.target_id == doc.id,
                AuditLog.action == "DRIVER_DOCUMENT_REVOKED"
            )
            audit_res = await db_session.execute(audit_stmt)
            audit_entry = audit_res.scalar_one_or_none()
            assert audit_entry is not None
            assert audit_entry.actor_id == sup_user.id
            assert "admin.drivers.approve" in audit_entry.metadata_json
            assert revocation_note in audit_entry.metadata_json
        finally:
            app.dependency_overrides.pop(get_current_admin, None)


@pytest.mark.asyncio
async def test_revocation_validation_and_ownership_boundaries(db_session):
    """Test short justification (< 8 chars), cross-driver ownership mismatch, and commit failure rollback."""
    unique_suffix = id(object())

    # Two drivers
    d1 = User(phone_number=f"+9195{str(unique_suffix)[-8:]}", email=f"d1_{unique_suffix}@erickshaw.in", role="driver", is_active=True)
    d2 = User(phone_number=f"+9196{str(unique_suffix)[-8:]}", email=f"d2_{unique_suffix}@erickshaw.in", role="driver", is_active=True)
    db_session.add_all([d1, d2])
    await db_session.flush()

    p1 = DriverProfile(user_id=d1.id, license_number=f"DL-P1-{str(unique_suffix)[-4:]}", approval_status="PENDING")
    p2 = DriverProfile(user_id=d2.id, license_number=f"DL-P2-{str(unique_suffix)[-4:]}", approval_status="PENDING")
    db_session.add_all([p1, p2])
    await db_session.flush()

    doc1 = DriverDocument(
        driver_id=p1.id,
        document_type="COMMERCIAL_INSURANCE",
        storage_path="/docs/ins1.pdf",
        file_name="ins1.pdf",
        mime_type="application/pdf",
        file_size_bytes=2048,
        file_hash_sha256="hashp1",
        verification_status="VERIFIED"
    )
    db_session.add(doc1)

    # Superuser admin
    super_user = User(phone_number=f"+9197{str(unique_suffix)[-8:]}", email=f"super_{unique_suffix}@erickshaw.in", role="admin", is_active=True, mfa_enabled=True)
    db_session.add(super_user)
    await db_session.flush()

    super_role = AdminRole(name=f"Super Admin {unique_suffix}", description="Full access", is_superuser=True)
    db_session.add(super_role)
    await db_session.flush()

    db_session.add(AdminUserRole(user_id=super_user.id, role_id=super_role.id))

    super_sess = Session(
        user_id=super_user.id,
        token_hash=hash_secret(f"{super_user.id}:super_token:{generate_secure_otp(12)}"),
        is_revoked=False,
        mfa_verified=True,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=60)
    )
    db_session.add(super_sess)
    await db_session.commit()

    super_token = create_jwt_token(user_id=super_user.id, role="admin", session_id=super_sess.id, token_type="access")

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        app.dependency_overrides[get_current_admin] = lambda: super_user
        try:
            # 1. Short revocation justification (< 8 chars) -> 422
            res_short = await ac.put(
                f"/api/v1/admin/drivers/{p1.id}/documents/{doc1.id}",
                json={"verification_status": "REJECTED", "revocation_justification": "bad"},
                headers={"Authorization": f"Bearer {super_token}"}
            )
            assert res_short.status_code == 422

            # 2. Cross-driver document mismatch (doc1 belongs to p1, requested under p2) -> 400
            res_mismatch = await ac.put(
                f"/api/v1/admin/drivers/{p2.id}/documents/{doc1.id}",
                json={
                    "verification_status": "REJECTED",
                    "revocation_justification": "Valid justification for revocation"
                },
                headers={"Authorization": f"Bearer {super_token}"}
            )
            assert res_mismatch.status_code == 400
            assert "belong" in res_mismatch.text.lower()

            # 3. Superuser revocation with valid justification -> 200
            valid_just = "Revocation due to verified fraud notice from government transport portal"
            res_valid = await ac.put(
                f"/api/v1/admin/drivers/{p1.id}/documents/{doc1.id}",
                json={
                    "verification_status": "REJECTED",
                    "revocation_justification": valid_just
                },
                headers={"Authorization": f"Bearer {super_token}"}
            )
            assert res_valid.status_code == 200
            assert res_valid.json()["verification_status"] == "REJECTED"

            # 4. Existing REJECTED to VERIFIED re-review flow works
            res_reverify = await ac.put(
                f"/api/v1/admin/drivers/{p1.id}/documents/{doc1.id}",
                json={"verification_status": "VERIFIED"},
                headers={"Authorization": f"Bearer {super_token}"}
            )
            assert res_reverify.status_code == 200
            assert res_reverify.json()["verification_status"] == "VERIFIED"
        finally:
            app.dependency_overrides.pop(get_current_admin, None)


@pytest.mark.asyncio
async def test_driver_approval_payload_hardening(db_session):
    """Test approval payload validation: legacy status, modern approval_status, conflicts (422), extra fields (422)."""
    unique_suffix = id(object())

    driver_user = User(phone_number=f"+9198{str(unique_suffix)[-8:]}", email=f"appr_drv_{unique_suffix}@erickshaw.in", role="driver", is_active=True)
    db_session.add(driver_user)
    await db_session.flush()

    prof = DriverProfile(user_id=driver_user.id, license_number=f"DL-APPR-{str(unique_suffix)[-4:]}", approval_status="PENDING")
    db_session.add(prof)
    await db_session.flush()

    # Verified document so APPROVED state is reachable
    doc = DriverDocument(
        driver_id=prof.id,
        document_type="COMMERCIAL_LICENSE",
        storage_path="/docs/com.pdf",
        file_name="com.pdf",
        mime_type="application/pdf",
        file_size_bytes=1024,
        file_hash_sha256="hashcom123",
        verification_status="VERIFIED"
    )
    db_session.add(doc)

    admin_user = User(phone_number=f"+9199{str(unique_suffix)[-8:]}", email=f"appr_admin_{unique_suffix}@erickshaw.in", role="admin", is_active=True, mfa_enabled=True)
    db_session.add(admin_user)
    await db_session.flush()

    admin_sess = Session(
        user_id=admin_user.id,
        token_hash=hash_secret(f"{admin_user.id}:admin_token:{generate_secure_otp(12)}"),
        is_revoked=False,
        mfa_verified=True,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=60)
    )
    db_session.add(admin_sess)
    await db_session.commit()

    admin_token = create_jwt_token(user_id=admin_user.id, role="admin", session_id=admin_sess.id, token_type="access")

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        app.dependency_overrides[get_current_admin] = lambda: admin_user
        try:
            # 1. Conflicting values -> 422
            res_conflict = await ac.put(
                f"/api/v1/admin/drivers/{prof.id}/approval",
                json={"approval_status": "APPROVED", "status": "REJECTED"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_conflict.status_code == 422
            assert "Conflicting approval status" in res_conflict.text

            # 2. Extra protected field -> 422 (forbidden extra field)
            res_extra = await ac.put(
                f"/api/v1/admin/drivers/{prof.id}/approval",
                json={"approval_status": "APPROVED", "is_verified": True},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_extra.status_code == 422
            assert "extra" in res_extra.text.lower()

            # 3. Empty payload -> 422
            res_empty = await ac.put(
                f"/api/v1/admin/drivers/{prof.id}/approval",
                json={},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_empty.status_code == 422

            # 4. Invalid status -> 422
            res_invalid = await ac.put(
                f"/api/v1/admin/drivers/{prof.id}/approval",
                json={"approval_status": "NOT_A_REAL_STATUS"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_invalid.status_code == 422

            # 5. Dual identical fields with different casing -> 200
            res_dual_same = await ac.put(
                f"/api/v1/admin/drivers/{prof.id}/approval",
                json={"approval_status": "APPROVED", "status": "approved"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_dual_same.status_code == 200
            assert res_dual_same.json()["approval_status"] == "APPROVED"

            # 6. Legacy format: only 'status' -> 200 (SUSPENDED)
            res_legacy = await ac.put(
                f"/api/v1/admin/drivers/{prof.id}/approval",
                json={"status": "SUSPENDED"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_legacy.status_code == 200
            assert res_legacy.json()["approval_status"] == "SUSPENDED"

            # 7. Modern format: only 'approval_status' -> 200 (APPROVED)
            res_modern = await ac.put(
                f"/api/v1/admin/drivers/{prof.id}/approval",
                json={"approval_status": "APPROVED"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_modern.status_code == 200
            assert res_modern.json()["approval_status"] == "APPROVED"
        finally:
            app.dependency_overrides.pop(get_current_admin, None)


@pytest.mark.asyncio
async def test_audit_insertion_failure_rolls_back_document_status(db_session):
    """If audit log generation or commit fails during revocation, document status changes must be rolled back."""
    unique_suffix = id(object())

    driver_user = User(phone_number=f"+9188{str(unique_suffix)[-8:]}", email=f"rb_drv_{unique_suffix}@erickshaw.in", role="driver", is_active=True)
    db_session.add(driver_user)
    await db_session.flush()

    prof = DriverProfile(user_id=driver_user.id, license_number=f"DL-RB-{str(unique_suffix)[-4:]}", approval_status="PENDING")
    db_session.add(prof)
    await db_session.flush()

    doc = DriverDocument(
        driver_id=prof.id,
        document_type="COMMERCIAL_INSURANCE",
        storage_path="/docs/ins_rb.pdf",
        file_name="ins_rb.pdf",
        mime_type="application/pdf",
        file_size_bytes=2048,
        file_hash_sha256="hashrb123",
        verification_status="VERIFIED"
    )
    db_session.add(doc)

    super_user = User(phone_number=f"+9189{str(unique_suffix)[-8:]}", email=f"rb_super_{unique_suffix}@erickshaw.in", role="admin", is_active=True, mfa_enabled=True)
    db_session.add(super_user)
    await db_session.flush()

    super_role = AdminRole(name=f"Super Admin RB {unique_suffix}", description="Full access", is_superuser=True)
    db_session.add(super_role)
    await db_session.flush()

    db_session.add(AdminUserRole(user_id=super_user.id, role_id=super_role.id))

    super_sess = Session(
        user_id=super_user.id,
        token_hash=hash_secret(f"{super_user.id}:super_token:{generate_secure_otp(12)}"),
        is_revoked=False,
        mfa_verified=True,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=60)
    )
    db_session.add(super_sess)
    await db_session.commit()

    super_token = create_jwt_token(user_id=super_user.id, role="admin", session_id=super_sess.id, token_type="access")

    transport = ASGITransport(app=app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        app.dependency_overrides[get_current_admin] = lambda: super_user
        try:
            from backend.application.services.admin_governance_service import AdminGovernanceService
            with patch.object(AdminGovernanceService, "audit", side_effect=RuntimeError("Simulated audit write failure")):
                res_fail = await ac.put(
                    f"/api/v1/admin/drivers/{prof.id}/documents/{doc.id}",
                    json={
                        "verification_status": "REJECTED",
                        "reason": "Revocation note that meets the required length",
                        "revocation_justification": "Revocation note that meets the required length",
                    },
                    headers={"Authorization": f"Bearer {super_token}"}
                )
                assert res_fail.status_code == 500

            # Confirm rollback via GET /admin/drivers/{driver_id}/documents
            res_list = await ac.get(
                f"/api/v1/admin/drivers/{prof.id}/documents",
                headers={"Authorization": f"Bearer {super_token}"}
            )
            assert res_list.status_code == 200
            docs = res_list.json()
            target_doc = next(d for d in docs if d["id"] == doc.id)
            assert target_doc["verification_status"] == "VERIFIED"
        finally:
            app.dependency_overrides.pop(get_current_admin, None)
