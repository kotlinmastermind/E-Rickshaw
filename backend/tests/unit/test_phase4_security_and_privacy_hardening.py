import os
import io
import uuid
import time
import hashlib
import hmac
import pytest
from datetime import datetime, timezone, timedelta
from httpx import AsyncClient, ASGITransport
from fastapi import UploadFile, HTTPException
from sqlalchemy import select, update

from backend.main import app
from backend.core.config import settings
from backend.domain.models import (
    User, DriverProfile, CustomerProfile, DriverDocument,
    Session, AccountDeletionRequest, DataExportArchive, AdminRole, AdminUserRole, AdminPermission, AdminRolePermission
)
from backend.infrastructure.security.crypto import (
    create_jwt_token, decode_jwt_token, hash_secret, generate_secure_otp
)
from backend.infrastructure.security.encryption import redact_sensitive_dict, mask_phone_number, mask_email
from backend.integrations.storage.provider import (
    LocalPrivateStorageProvider, SupabaseCloudStorageProvider,
    verify_signed_url_token, get_storage_provider
)
from backend.application.services.user_governance_service import UserGovernanceService


@pytest.mark.asyncio
async def test_private_kyc_storage_and_disallowed_public_exposure(tmp_path):
    """
    Task 041 & 042: Verify KYC storage is strictly private, never returning public Supabase URLs,
    and enforcing local directory containment.
    """
    local_provider = LocalPrivateStorageProvider(str(tmp_path))
    cloud_provider = SupabaseCloudStorageProvider(local_provider)
    cloud_provider.supabase_url = "https://mock-supabase.internal"
    cloud_provider.supabase_key = "mock_secret_key"

    pdf_content = b"%PDF-1.4 test document content for private kyc"
    upload_file = UploadFile(filename="license.pdf", file=io.BytesIO(pdf_content))

    path, mime, size, digest = await local_provider.save_driver_document(
        driver_id="drv_12345",
        document_type="DRIVING_LICENSE",
        file=upload_file
    )

    # 1. Local path is contained inside tmp_path
    assert os.path.exists(path)
    assert path.startswith(str(tmp_path))
    assert mime == "application/pdf"
    assert size == len(pdf_content)

    # 2. Signed URL generation works for local storage
    signed_url = await local_provider.generate_signed_url(path, expires_in_seconds=300)
    assert "/documents/download?file=" in signed_url
    assert "&sig=" in signed_url
    assert "&expires=" in signed_url

    # 3. Public bucket URL is NEVER returned
    assert "object/public" not in path


@pytest.mark.asyncio
async def test_secure_document_upload_validation_mime_and_magic_bytes(tmp_path):
    """
    Task 044: Verify MIME type, complete file signature (magic bytes), file size,
    and permitted extension enforcement.
    """
    provider = LocalPrivateStorageProvider(str(tmp_path))

    # 1. Dangerous extension rejected
    malicious_file = UploadFile(filename="exploit.exe", file=io.BytesIO(b"%PDF-1.4 fake exe"))
    with pytest.raises(HTTPException) as exc_info:
        await provider.save_driver_document("drv_test", "DRIVING_LICENSE", malicious_file)
    assert exc_info.value.status_code == 400
    assert "extension .exe is not permitted" in exc_info.value.detail.lower()

    # 2. Mismatched extension vs magic bytes rejected (.pdf extension with PNG magic bytes)
    mismatched_file = UploadFile(filename="document.pdf", file=io.BytesIO(b"\x89PNG\r\n\x1a\nfake png content"))
    with pytest.raises(HTTPException) as exc_info:
        await provider.save_driver_document("drv_test", "DRIVING_LICENSE", mismatched_file)
    assert exc_info.value.status_code == 400
    assert "does not match detected mime" in exc_info.value.detail.lower()

    # 3. Invalid magic bytes rejected
    invalid_header_file = UploadFile(filename="fake.jpg", file=io.BytesIO(b"random garbage bytes here"))
    with pytest.raises(HTTPException) as exc_info:
        await provider.save_driver_document("drv_test", "DRIVING_LICENSE", invalid_header_file)
    assert exc_info.value.status_code == 400
    assert "file signature does not match" in exc_info.value.detail.lower()

    # 4. Empty file rejected
    empty_file = UploadFile(filename="empty.png", file=io.BytesIO(b""))
    with pytest.raises(HTTPException) as exc_info:
        await provider.save_driver_document("drv_test", "DRIVING_LICENSE", empty_file)
    assert exc_info.value.status_code == 400
    assert "empty" in exc_info.value.detail.lower()

    # 5. Valid PDF accepted
    valid_pdf = UploadFile(filename="authentic.pdf", file=io.BytesIO(b"%PDF-1.7 valid test payload"))
    path, mime, size, sha256 = await provider.save_driver_document("drv_valid", "IDENTITY_PROOF", valid_pdf)
    assert mime == "application/pdf"
    assert os.path.exists(path)


@pytest.mark.asyncio
async def test_prevent_arbitrary_file_uploads_and_path_traversal(tmp_path):
    """
    Task 045: Verify path traversal prevention and identifier format sanitization.
    """
    provider = LocalPrivateStorageProvider(str(tmp_path))

    valid_pdf_bytes = b"%PDF-1.4 safe pdf"

    # 1. Directory traversal in driver_id rejected
    file1 = UploadFile(filename="test.pdf", file=io.BytesIO(valid_pdf_bytes))
    with pytest.raises(HTTPException) as exc_info:
        await provider.save_driver_document("../../etc", "DRIVING_LICENSE", file1)
    assert exc_info.value.status_code == 400
    assert "invalid driver identifier format" in exc_info.value.detail.lower()

    # 2. Directory traversal in document_type rejected
    file2 = UploadFile(filename="test.pdf", file=io.BytesIO(valid_pdf_bytes))
    with pytest.raises(HTTPException) as exc_info:
        await provider.save_driver_document("drv_safe", "../malicious_type", file2)
    assert exc_info.value.status_code == 400
    assert "invalid document type format" in exc_info.value.detail.lower()


@pytest.mark.asyncio
async def test_signed_document_urls_ownership_and_tamper_verification(db_session):
    """
    Task 043: Verify signed document URLs enforce ownership, valid HMAC signatures,
    and prevent expired or tampered access.
    """
    uid_suffix = uuid.uuid4().int % 100000000
    driver1_user = User(
        phone_number=f"+9198{uid_suffix:08d}",
        email=f"doc_d1_{uid_suffix}@erickshaw.in",
        role="driver",
        is_active=True
    )
    driver2_user = User(
        phone_number=f"+9197{uid_suffix:08d}",
        email=f"doc_d2_{uid_suffix}@erickshaw.in",
        role="driver",
        is_active=True
    )
    db_session.add_all([driver1_user, driver2_user])
    await db_session.flush()

    prof1 = DriverProfile(user_id=driver1_user.id, license_number=f"DL1-{uid_suffix}")
    prof2 = DriverProfile(user_id=driver2_user.id, license_number=f"DL2-{uid_suffix}")
    db_session.add_all([prof1, prof2])
    await db_session.flush()

    # Create dummy file on storage
    base_storage = os.path.abspath(settings.STORAGE_LOCAL_DIR)
    drv_dir = os.path.join(base_storage, prof1.id)
    os.makedirs(drv_dir, exist_ok=True)
    file_path = os.path.join(drv_dir, "license_signed_test.pdf")
    with open(file_path, "wb") as f:
        f.write(b"%PDF-1.4 test driver license verification")

    doc1 = DriverDocument(
        driver_id=prof1.id,
        document_type="DRIVING_LICENSE",
        storage_path=file_path,
        file_name="license.pdf",
        mime_type="application/pdf",
        file_size_bytes=42,
        file_hash_sha256="testhash123",
        verification_status="PENDING"
    )
    db_session.add(doc1)

    sess1 = Session(
        user_id=driver1_user.id,
        token_hash=hash_secret(f"tok1_{uid_suffix}"),
        is_revoked=False,
        expires_at=datetime.now(timezone.utc) + timedelta(hours=1)
    )
    sess2 = Session(
        user_id=driver2_user.id,
        token_hash=hash_secret(f"tok2_{uid_suffix}"),
        is_revoked=False,
        expires_at=datetime.now(timezone.utc) + timedelta(hours=1)
    )
    db_session.add_all([sess1, sess2])
    await db_session.commit()

    token1 = create_jwt_token(driver1_user.id, "driver", sess1.id)
    token2 = create_jwt_token(driver2_user.id, "driver", sess2.id)

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        # A. Driver 2 attempts to generate signed URL for Driver 1's document -> 403 Forbidden
        res_unauth = await ac.get(
            f"/api/v1/driver-onboarding/documents/{doc1.id}/signed-url",
            headers={"Authorization": f"Bearer {token2}"}
        )
        assert res_unauth.status_code == 403
        assert "do not own" in res_unauth.text.lower()

        # B. Driver 1 generates signed URL -> 200 OK
        res_auth = await ac.get(
            f"/api/v1/driver-onboarding/documents/{doc1.id}/signed-url",
            headers={"Authorization": f"Bearer {token1}"}
        )
        assert res_auth.status_code == 200
        data = res_auth.json()
        signed_url = data["signed_url"]
        assert "/documents/download" in signed_url

        # C. Download using the valid signed URL
        res_dl = await ac.get(signed_url)
        assert res_dl.status_code == 200
        assert res_dl.content.startswith(b"%PDF-1.4")

        # D. Tampered signature rejected -> 403
        tampered_url = signed_url.replace("&sig=", "&sig=tampered_")
        res_tampered = await ac.get(tampered_url)
        assert res_tampered.status_code == 403

        # E. Expired token rejected -> 403
        import re
        expired_url = re.sub(r"expires=\d+", "expires=1000", signed_url)
        res_expired = await ac.get(expired_url)
        assert res_expired.status_code == 403


@pytest.mark.asyncio
async def test_account_deletion_workflow_and_grace_period_purge(db_session):
    """
    Task 046: Verify account deletion workflow: persistence, status tracking,
    cancellation, and automated purge of expired grace period accounts.
    """
    uid_suffix = uuid.uuid4().int % 100000000
    user = User(
        phone_number=f"+9196{uid_suffix:08d}",
        email=f"del_user_{uid_suffix}@erickshaw.in",
        full_name="VIP Grace Period User",
        role="customer",
        is_active=True
    )
    db_session.add(user)
    await db_session.flush()

    sess = Session(
        user_id=user.id,
        token_hash=hash_secret(f"sess_del_{uid_suffix}"),
        is_revoked=False,
        expires_at=datetime.now(timezone.utc) + timedelta(hours=1)
    )
    db_session.add(sess)
    await db_session.commit()

    service = UserGovernanceService(db_session)

    # 1. Request deletion
    req_res = await service.request_account_deletion(user.id, reason="Relocating to Singapore")
    assert req_res["status"] == "PENDING_GRACE_PERIOD"
    assert req_res["days_remaining"] == 30

    # 2. Check deletion status
    status_res = await service.get_deletion_status(user.id)
    assert status_res["has_active_request"] is True
    assert status_res["status"] == "PENDING_GRACE_PERIOD"

    # 3. Cancel deletion request
    cancel_res = await service.cancel_deletion_request(user.id)
    assert cancel_res["status"] == "CANCELLED"

    status_after_cancel = await service.get_deletion_status(user.id)
    assert status_after_cancel["has_active_request"] is False

    # 4. Request deletion again and simulate elapsed grace period
    await service.request_account_deletion(user.id, reason="Permanent account closure")
    
    # Backdate scheduled_purge_at beyond cooldown
    await db_session.execute(
        update(AccountDeletionRequest)
        .where(AccountDeletionRequest.user_id == user.id, AccountDeletionRequest.status == "PENDING_GRACE_PERIOD")
        .values(scheduled_purge_at=datetime.now(timezone.utc) - timedelta(hours=1))
    )
    await db_session.commit()

    # 5. Execute scheduled purge job
    purge_summary = await service.execute_scheduled_account_purges()
    assert purge_summary["status"] == "SUCCESS"
    assert purge_summary["purged_accounts_count"] >= 1

    # 6. Verify user is anonymized and sessions revoked
    refreshed_user = await db_session.get(User, user.id)
    await db_session.refresh(refreshed_user)
    assert refreshed_user.is_active is False
    assert "purged" in refreshed_user.email
    assert "Deactivated" in refreshed_user.full_name

    refreshed_sess = await db_session.get(Session, sess.id)
    assert refreshed_sess.is_revoked is True


@pytest.mark.asyncio
async def test_data_export_archive_ownership_and_expiration(db_session):
    """
    Task 047: Verify personal data export generation, ownership enforcement,
    and expiration handling on download.
    """
    uid_suffix = uuid.uuid4().int % 100000000
    user1 = User(
        phone_number=f"+9195{uid_suffix:08d}",
        email=f"exp_u1_{uid_suffix}@erickshaw.in",
        full_name="VIP Export User 1",
        role="customer",
        is_active=True
    )
    user2 = User(
        phone_number=f"+9194{uid_suffix:08d}",
        email=f"exp_u2_{uid_suffix}@erickshaw.in",
        full_name="VIP Export User 2",
        role="customer",
        is_active=True
    )
    db_session.add_all([user1, user2])
    await db_session.flush()

    sess1 = Session(user_id=user1.id, token_hash=hash_secret(f"exp_s1_{uid_suffix}"), is_revoked=False, expires_at=datetime.now(timezone.utc) + timedelta(hours=1))
    sess2 = Session(user_id=user2.id, token_hash=hash_secret(f"exp_s2_{uid_suffix}"), is_revoked=False, expires_at=datetime.now(timezone.utc) + timedelta(hours=1))
    db_session.add_all([sess1, sess2])
    await db_session.commit()

    token1 = create_jwt_token(user1.id, "customer", sess1.id)
    token2 = create_jwt_token(user2.id, "customer", sess2.id)

    service = UserGovernanceService(db_session)
    export_res = await service.request_data_export(user1.id)
    assert export_res["status"] == "READY"
    archive_id = export_res["archive_id"]
    download_url = export_res["download_url"]

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        # A. Unauthenticated download rejected -> 401
        res_unauth = await ac.get(download_url)
        assert res_unauth.status_code == 401

        # B. User 2 attempts to download User 1's archive -> 403 Forbidden
        res_forbidden = await ac.get(download_url, headers={"Authorization": f"Bearer {token2}"})
        assert res_forbidden.status_code == 403
        assert "do not own" in res_forbidden.text.lower()

        # C. User 1 downloads their own archive -> 200 OK with valid JSON payload
        res_ok = await ac.get(download_url, headers={"Authorization": f"Bearer {token1}"})
        assert res_ok.status_code == 200
        data = res_ok.json()
        assert "export_metadata" in data
        assert data["user_identity"]["email"] == user1.email

        # D. Expired archive returns 410 Gone
        await db_session.execute(
            update(DataExportArchive)
            .where(DataExportArchive.id == archive_id)
            .values(expires_at=datetime.now(timezone.utc) - timedelta(hours=1))
        )
        await db_session.commit()

        res_expired = await ac.get(download_url, headers={"Authorization": f"Bearer {token1}"})
        assert res_expired.status_code == 410
        assert "expired" in res_expired.text.lower()


@pytest.mark.asyncio
async def test_short_lived_jwt_access_tokens():
    """
    Task 048: Verify JWT access tokens are short-lived (15 minutes),
    while refresh tokens have appropriate long-term lifespan.
    """
    assert settings.ACCESS_TOKEN_EXPIRE_MINUTES <= 30
    assert settings.ACCESS_TOKEN_EXPIRE_MINUTES >= 5

    access_tok = create_jwt_token("u123", "customer", "s123", token_type="access")
    refresh_tok = create_jwt_token("u123", "customer", "s123", token_type="refresh")

    claims_access = decode_jwt_token(access_tok)
    claims_refresh = decode_jwt_token(refresh_tok)

    access_lifespan = claims_access["exp"] - claims_access["iat"]
    refresh_lifespan = claims_refresh["exp"] - claims_refresh["iat"]

    # Access token lifespan matches settings (15 mins = 900 seconds)
    assert access_lifespan == settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60
    # Refresh token lifespan is 7 days (604800 seconds)
    assert refresh_lifespan == 7 * 24 * 3600


@pytest.mark.asyncio
async def test_secrets_and_pii_redaction_in_logging():
    """
    Task 050: Verify passwords, secrets, tokens, and PII are redacted or masked in logs.
    """
    raw_payload = {
        "user_id": "u_999",
        "email": "executive_guest@luxurychauffeur.com",
        "phone_number": "+919876543210",
        "password": "SuperSecretPassword123!",
        "access_token": "eyJhbGciOi...",
        "start_otp": "4921",
        "cvv": "999",
        "metadata": {
            "refresh_token": "refr_secret_123",
            "api_key": "live_sec_key"
        }
    }

    sanitized = redact_sensitive_dict(raw_payload)

    assert sanitized["password"] == "[REDACTED]"
    assert sanitized["access_token"] == "[REDACTED]"
    assert sanitized["start_otp"] == "[REDACTED]"
    assert sanitized["cvv"] == "[REDACTED]"
    assert sanitized["metadata"]["refresh_token"] == "[REDACTED]"
    assert sanitized["email"] != "executive_guest@luxurychauffeur.com"
    assert "@luxurychauffeur.com" in sanitized["email"]
    assert sanitized["phone_number"].startswith("+91")
    assert "3210" in sanitized["phone_number"]
    assert "******" in sanitized["phone_number"]

