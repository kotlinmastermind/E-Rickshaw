import pytest
import uuid
from datetime import datetime, timezone, timedelta
from httpx import AsyncClient, ASGITransport
from sqlalchemy import select
from fastapi import HTTPException

from backend.main import app
from backend.domain.models import (
    User, DriverProfile, DriverDocument, DriverAvailability,
    Session, AuditLog, SupportTicket
)
from backend.infrastructure.security.crypto import (
    hash_password, create_jwt_token, hash_secret, generate_secure_otp
)
from backend.api.deps import get_current_admin


@pytest.mark.asyncio
async def test_admin_document_review_positive_and_audit(db_session):
    """Test that an authorized MFA admin can verify and reject driver documents, recording audit logs."""
    unique_suffix = uuid.uuid4().int % 100000000

    # 1. Setup driver and driver profile
    driver_user = User(
        phone_number=f"+9198{str(unique_suffix)[-8:]}",
        email=f"doc_driver_{unique_suffix}@erickshaw.in",
        role="driver",
        is_active=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    driver_profile = DriverProfile(
        user_id=driver_user.id,
        license_number=f"DL-{str(unique_suffix)[-6:]}",
        approval_status="PENDING"
    )
    db_session.add(driver_profile)
    await db_session.flush()

    # 2. Setup documents
    doc1 = DriverDocument(
        driver_id=driver_profile.id,
        document_type="DRIVING_LICENSE",
        storage_path="/docs/dl.pdf",
        file_name="dl.pdf",
        mime_type="application/pdf",
        file_size_bytes=2048,
        file_hash_sha256="hash123dl",
        verification_status="PENDING"
    )
    doc2 = DriverDocument(
        driver_id=driver_profile.id,
        document_type="AADHAAR_CARD",
        storage_path="/docs/aadhaar.pdf",
        file_name="aadhaar.pdf",
        mime_type="application/pdf",
        file_size_bytes=1024,
        file_hash_sha256="hash123aadhaar",
        verification_status="PENDING"
    )
    db_session.add_all([doc1, doc2])

    # 3. Setup Admin user and session
    admin_user = User(
        phone_number=f"+9199{str(unique_suffix)[-8:]}",
        email=f"doc_admin_{unique_suffix}@erickshaw.in",
        role="admin",
        is_active=True,
        mfa_enabled=True
    )
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
            # A. Verify doc1 via canonical endpoint
            res_verify = await ac.put(
                f"/api/v1/admin/drivers/{driver_profile.id}/documents/{doc1.id}",
                json={"verification_status": "VERIFIED"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_verify.status_code == 200, res_verify.text
            data_verify = res_verify.json()
            assert data_verify["verification_status"] == "VERIFIED"
            assert data_verify["document_id"] == doc1.id
            assert data_verify["driver_id"] == driver_profile.id

            # Verify in DB
            db_doc1 = await db_session.get(DriverDocument, doc1.id)
            await db_session.refresh(db_doc1)
            assert db_doc1.verification_status == "VERIFIED"
            assert db_doc1.reviewed_by == admin_user.id

            # Check audit log in DB
            audit_stmt = select(AuditLog).where(
                AuditLog.target_resource == "driver_documents",
                AuditLog.target_id == doc1.id
            )
            audit_res = await db_session.execute(audit_stmt)
            audit_entry = audit_res.scalar_one_or_none()
            assert audit_entry is not None
            assert audit_entry.actor_id == admin_user.id
            assert audit_entry.action == "DRIVER_DOCUMENT_REVIEWED"
            assert "VERIFIED" in audit_entry.metadata_json

            # B. Reject doc2 with valid reason
            res_reject = await ac.put(
                f"/api/v1/admin/drivers/{driver_profile.id}/documents/{doc2.id}",
                json={
                    "verification_status": "REJECTED",
                    "review_notes": "Aadhaar image is blurry and illegible"
                },
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_reject.status_code == 200, res_reject.text
            data_reject = res_reject.json()
            assert data_reject["verification_status"] == "REJECTED"
            assert "blurry" in data_reject["rejection_reason"]

            # Verify in DB
            db_doc2 = await db_session.get(DriverDocument, doc2.id)
            await db_session.refresh(db_doc2)
            assert db_doc2.verification_status == "REJECTED"
            assert db_doc2.rejection_reason == "Aadhaar image is blurry and illegible"

            # C. List driver documents
            res_list = await ac.get(
                f"/api/v1/admin/drivers/{driver_profile.id}/documents",
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_list.status_code == 200
            docs_list = res_list.json()
            assert len(docs_list) == 2
        finally:
            app.dependency_overrides.pop(get_current_admin, None)


@pytest.mark.asyncio
async def test_admin_document_review_security_boundaries_and_rejections(db_session):
    """Test all negative boundaries: unauth, cross-driver mismatch, invalid status, missing reason."""
    unique_suffix = uuid.uuid4().int % 100000000

    # Create two drivers
    driver_user1 = User(phone_number=f"+9191{str(unique_suffix)[-8:]}", email=f"d1_{unique_suffix}@erickshaw.in", role="driver", is_active=True)
    driver_user2 = User(phone_number=f"+9192{str(unique_suffix)[-8:]}", email=f"d2_{unique_suffix}@erickshaw.in", role="driver", is_active=True)
    customer_user = User(phone_number=f"+9193{str(unique_suffix)[-8:]}", email=f"c_{unique_suffix}@erickshaw.in", role="customer", is_active=True)
    db_session.add_all([driver_user1, driver_user2, customer_user])
    await db_session.flush()

    prof1 = DriverProfile(user_id=driver_user1.id, license_number=f"DL-1-{str(unique_suffix)[-4:]}", approval_status="PENDING")
    prof2 = DriverProfile(user_id=driver_user2.id, license_number=f"DL-2-{str(unique_suffix)[-4:]}", approval_status="PENDING")
    db_session.add_all([prof1, prof2])
    await db_session.flush()

    doc_d1 = DriverDocument(
        driver_id=prof1.id,
        document_type="DRIVING_LICENSE",
        storage_path="/docs/d1.pdf",
        file_name="d1.pdf",
        mime_type="application/pdf",
        file_size_bytes=1024,
        file_hash_sha256="hashd1",
        verification_status="PENDING"
    )
    db_session.add(doc_d1)

    admin_user = User(
        phone_number=f"+9194{str(unique_suffix)[-8:]}",
        email=f"admin_sec_{unique_suffix}@erickshaw.in",
        role="admin",
        is_active=True,
        mfa_enabled=True
    )
    db_session.add(admin_user)
    await db_session.flush()

    admin_sess = Session(
        user_id=admin_user.id,
        token_hash=hash_secret(f"{admin_user.id}:admin_token:{generate_secure_otp(12)}"),
        is_revoked=False,
        mfa_verified=True,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=60)
    )
    cust_sess = Session(
        user_id=customer_user.id,
        token_hash=hash_secret(f"{customer_user.id}:cust_token:{generate_secure_otp(12)}"),
        is_revoked=False,
        mfa_verified=False,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=60)
    )
    drv_sess = Session(
        user_id=driver_user1.id,
        token_hash=hash_secret(f"{driver_user1.id}:drv_token:{generate_secure_otp(12)}"),
        is_revoked=False,
        mfa_verified=False,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=60)
    )
    db_session.add_all([admin_sess, cust_sess, drv_sess])
    await db_session.commit()

    admin_token = create_jwt_token(user_id=admin_user.id, role="admin", session_id=admin_sess.id, token_type="access")
    cust_token = create_jwt_token(user_id=customer_user.id, role="customer", session_id=cust_sess.id, token_type="access")
    driver_token = create_jwt_token(user_id=driver_user1.id, role="driver", session_id=drv_sess.id, token_type="access")

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        # 1. Unauthenticated request rejected
        res_noauth = await ac.put(f"/api/v1/admin/drivers/{prof1.id}/documents/{doc_d1.id}", json={"verification_status": "VERIFIED"})
        assert res_noauth.status_code == 401

        # 2. Customer token rejected (403)
        res_cust = await ac.put(
            f"/api/v1/admin/drivers/{prof1.id}/documents/{doc_d1.id}",
            json={"verification_status": "VERIFIED"},
            headers={"Authorization": f"Bearer {cust_token}"}
        )
        assert res_cust.status_code == 403

        # 3. Driver token rejected (403)
        res_drv = await ac.put(
            f"/api/v1/admin/drivers/{prof1.id}/documents/{doc_d1.id}",
            json={"verification_status": "VERIFIED"},
            headers={"Authorization": f"Bearer {driver_token}"}
        )
        assert res_drv.status_code == 403

        # For remaining endpoint tests with admin override
        app.dependency_overrides[get_current_admin] = lambda: admin_user
        try:
            # 4. Nonexistent driver rejected (404)
            res_bad_drv = await ac.put(
                f"/api/v1/admin/drivers/nonexistent-driver-id/documents/{doc_d1.id}",
                json={"verification_status": "VERIFIED"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_bad_drv.status_code == 404

            # 5. Nonexistent document rejected (404)
            res_bad_doc = await ac.put(
                f"/api/v1/admin/drivers/{prof1.id}/documents/nonexistent-doc-id",
                json={"verification_status": "VERIFIED"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_bad_doc.status_code == 404

            # 6. Mismatched driver/document ID rejected (400) - doc belongs to prof1, reviewed under prof2
            res_mismatch = await ac.put(
                f"/api/v1/admin/drivers/{prof2.id}/documents/{doc_d1.id}",
                json={"verification_status": "VERIFIED"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_mismatch.status_code == 400
            assert "belong" in res_mismatch.text.lower()

            # 7. Invalid status rejected (422)
            res_bad_status = await ac.put(
                f"/api/v1/admin/drivers/{prof1.id}/documents/{doc_d1.id}",
                json={"verification_status": "SUSPENDED"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_bad_status.status_code == 422

            # 8. Rejection without reason (< 8 chars) rejected (422)
            res_short_reason = await ac.put(
                f"/api/v1/admin/drivers/{prof1.id}/documents/{doc_d1.id}",
                json={"verification_status": "REJECTED", "review_notes": "bad"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_short_reason.status_code == 422
        finally:
            app.dependency_overrides.pop(get_current_admin, None)


@pytest.mark.asyncio
async def test_driver_approval_workflow_gated_by_documents(db_session):
    """Test full onboarding workflow: doc review is required, but verification alone does NOT approve driver."""
    unique_suffix = uuid.uuid4().int % 100000000

    driver_user = User(
        phone_number=f"+9195{str(unique_suffix)[-8:]}",
        email=f"flow_driver_{unique_suffix}@erickshaw.in",
        role="driver",
        is_active=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    prof = DriverProfile(user_id=driver_user.id, license_number=f"DL-FLOW-{str(unique_suffix)[-4:]}", approval_status="PENDING")
    db_session.add(prof)
    await db_session.flush()

    doc = DriverDocument(
        driver_id=prof.id,
        document_type="COMMERCIAL_LICENSE",
        storage_path="/docs/com.pdf",
        file_name="com.pdf",
        mime_type="application/pdf",
        file_size_bytes=1024,
        file_hash_sha256="hashcom",
        verification_status="PENDING"
    )
    db_session.add(doc)

    admin_user = User(
        phone_number=f"+9196{str(unique_suffix)[-8:]}",
        email=f"flow_admin_{unique_suffix}@erickshaw.in",
        role="admin",
        is_active=True,
        mfa_enabled=True
    )
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
            # 1. Driver with PENDING document cannot be approved -> 409
            res_premature_approval = await ac.put(
                f"/api/v1/admin/drivers/{prof.id}/approval",
                json={"approval_status": "APPROVED"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_premature_approval.status_code == 409

            # 2. Verify the document
            res_verify_doc = await ac.put(
                f"/api/v1/admin/drivers/{prof.id}/documents/{doc.id}",
                json={"verification_status": "VERIFIED"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_verify_doc.status_code == 200

            # 3. Document verification alone MUST NOT approve the driver
            db_prof = await db_session.get(DriverProfile, prof.id)
            await db_session.refresh(db_prof)
            assert db_prof.approval_status == "PENDING", "Document verification must not automatically approve driver"

            # 4. Driver still cannot go online while PENDING
            from backend.application.services.driver_service import DriverService
            driver_service = DriverService(db_session)
            with pytest.raises(HTTPException) as exc_info:
                await driver_service.toggle_availability(driver_user.id, is_online=True)
            assert exc_info.value.status_code == 403

            # 5. Now explicitly approve driver via admin approval route
            res_approve = await ac.put(
                f"/api/v1/admin/drivers/{prof.id}/approval",
                json={"approval_status": "APPROVED"},
                headers={"Authorization": f"Bearer {admin_token}"}
            )
            assert res_approve.status_code == 200
            assert res_approve.json()["approval_status"] == "APPROVED"

            # 6. Driver is now APPROVED and can go online
            await db_session.refresh(db_prof)
            assert db_prof.approval_status == "APPROVED"
            avail = await driver_service.toggle_availability(driver_user.id, is_online=True)
            assert avail["is_online"] is True
        finally:
            app.dependency_overrides.pop(get_current_admin, None)


@pytest.mark.asyncio
async def test_support_recovery_endpoint_truthful_message(db_session):
    """Verify that support self-service recovery returns honest instructions without claiming SMS dispatch."""
    unique_suffix = uuid.uuid4().int % 100000000

    user = User(
        phone_number=f"+9190{str(unique_suffix)[-8:]}",
        email=f"support_user_{unique_suffix}@erickshaw.in",
        role="customer",
        is_active=True
    )
    db_session.add(user)
    await db_session.flush()

    sess = Session(
        user_id=user.id,
        token_hash=hash_secret(f"{user.id}:user_token:{generate_secure_otp(12)}"),
        is_revoked=False,
        mfa_verified=False,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=60)
    )
    db_session.add(sess)
    await db_session.commit()

    token = create_jwt_token(user_id=user.id, role="customer", session_id=sess.id, token_type="access")

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        res = await ac.post(
            "/api/v1/support/self-service/account-recovery",
            json={
                "recovery_type": "PIN_RESET",
                "details": "Forgot my 4-digit luxury chauffeur application PIN"
            },
            headers={"Authorization": f"Bearer {token}"}
        )
        assert res.status_code == 200, res.text
        data = res.json()
        assert data["status"] == "RECOVERY_RECORDED"
        assert "SMS" not in data["instructions"] or "Automated SMS recovery is currently unavailable" in data["instructions"]
        assert "support@vaisentra.com" in data["instructions"]
        assert "dispatched" not in data["instructions"].lower()

        # Verify a SupportTicket was actually created in the DB
        ticket_stmt = select(SupportTicket).where(
            SupportTicket.user_id == user.id,
            SupportTicket.category == "ACCOUNT_RECOVERY"
        )
        ticket_res = await db_session.execute(ticket_stmt)
        ticket = ticket_res.scalar_one_or_none()
        assert ticket is not None
        assert ticket.status == "OPEN"
        assert ticket.assigned_squad == "IDENTITY_SECURITY"
