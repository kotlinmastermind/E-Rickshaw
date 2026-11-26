import uuid
from datetime import datetime, timezone

import pytest
from sqlalchemy import select

from backend.api.deps import get_current_admin
from backend.domain.models import CustomerKycAuditLog, CustomerKycDocument, User
from backend.main import app


@pytest.mark.asyncio
async def test_customer_kyc_upload_is_private_owner_scoped_and_admin_review_is_audited(
    client, db_session, customer_user, customer_token, second_customer_user, second_customer_token, monkeypatch
):
    from backend.integrations.storage.customer_kyc import customer_kyc_storage

    customer_user.is_verified = False
    await db_session.commit()

    async def fake_upload(**kwargs):
        assert kwargs["user_id"] == customer_user.id
        assert kwargs["mime_type"] == "application/pdf"
        return f"private://customer-kyc-documents/{kwargs['user_id']}/{kwargs['document_id']}.pdf"

    async def fake_signed_url(storage_path, expires_in_seconds=60):
        assert storage_path.startswith("private://customer-kyc-documents/")
        assert expires_in_seconds == 60
        return "https://storage.example.test/signed-document"

    monkeypatch.setattr(customer_kyc_storage, "upload", fake_upload)
    monkeypatch.setattr(customer_kyc_storage, "create_signed_url", fake_signed_url)

    payload = b"%PDF-1.7\nprivate customer identity test document"
    response = await client.post(
        "/api/v1/customers/kyc/documents",
        data={"document_type": "PAN_CARD"},
        files={"file": ("identity.pdf", payload, "application/pdf")},
        headers={"Authorization": f"Bearer {customer_token}"},
    )
    assert response.status_code == 201, response.text
    body = response.json()
    assert body["status"] == "PENDING_REVIEW"
    assert "not been verified" in body["message"].lower()

    document = await db_session.get(CustomerKycDocument, body["id"])
    assert document is not None
    assert document.user_id == customer_user.id
    assert document.storage_path.startswith("private://")
    assert document.status == "PENDING_REVIEW"
    await db_session.refresh(customer_user)
    assert customer_user.is_verified is False  # document submission does not toggle account verification

    status_response = await client.get(
        "/api/v1/customers/kyc",
        headers={"Authorization": f"Bearer {customer_token}"},
    )
    assert status_response.status_code == 200
    assert status_response.json()["kyc_status"] == "PENDING_REVIEW"
    assert "storage_path" not in str(status_response.json())

    other_customer_status = await client.get(
        "/api/v1/customers/kyc",
        headers={"Authorization": f"Bearer {second_customer_token}"},
    )
    assert other_customer_status.status_code == 200
    assert other_customer_status.json()["documents"] == []

    admin = User(
        id=str(uuid.uuid4()),
        phone_number=f"+9199{uuid.uuid4().int % 100000000:08d}",
        email=f"kyc_admin_{uuid.uuid4().hex[:8]}@example.test",
        full_name="KYC Admin",
        role="admin",
        is_active=True,
        is_verified=True,
    )
    db_session.add(admin)
    await db_session.commit()
    app.dependency_overrides[get_current_admin] = lambda: admin
    try:
        listing = await client.get("/api/v1/admin/customer-kyc/documents?status_filter=PENDING_REVIEW")
        assert listing.status_code == 200, listing.text
        assert any(item["id"] == document.id for item in listing.json())

        download = await client.get(f"/api/v1/admin/customer-kyc/documents/{document.id}/download")
        assert download.status_code == 200, download.text
        assert download.json()["expires_in_seconds"] == 60

        reviewed = await client.post(
            f"/api/v1/admin/customer-kyc/documents/{document.id}/review",
            json={"approved": True, "reason": "Document reviewed manually."},
        )
        assert reviewed.status_code == 200, reviewed.text
        assert reviewed.json()["status"] == "APPROVED"

        await db_session.refresh(document)
        await db_session.refresh(customer_user)
        assert document.status == "APPROVED"
        assert document.reviewed_by_admin_id == admin.id
        assert customer_user.is_verified is False

        audit_rows = (
            await db_session.execute(
                select(CustomerKycAuditLog).where(CustomerKycAuditLog.document_id == document.id)
            )
        ).scalars().all()
        assert {entry.action for entry in audit_rows} >= {
            "DOCUMENT_SUBMITTED",
            "DOCUMENT_SIGNED_URL_ISSUED",
            "DOCUMENT_APPROVED",
        }
    finally:
        app.dependency_overrides.pop(get_current_admin, None)


@pytest.mark.asyncio
async def test_customer_kyc_rejects_aadhaar_and_invalid_file_content(client, customer_token):
    headers = {"Authorization": f"Bearer {customer_token}"}
    aadhaar = await client.post(
        "/api/v1/customers/kyc/documents",
        data={"document_type": "AADHAAR_CARD"},
        files={"file": ("aadhaar.pdf", b"%PDF-1.7 fake", "application/pdf")},
        headers=headers,
    )
    assert aadhaar.status_code == 422

    invalid = await client.post(
        "/api/v1/customers/kyc/documents",
        data={"document_type": "PAN_CARD"},
        files={"file": ("identity.pdf", b"not a pdf", "application/pdf")},
        headers=headers,
    )
    assert invalid.status_code == 400

@pytest.mark.asyncio
async def test_customer_kyc_storage_fails_closed_without_service_role_configuration(monkeypatch):
    from fastapi import HTTPException
    from backend.core.config import settings
    from backend.integrations.storage.customer_kyc import CustomerKycStorage

    monkeypatch.setattr(settings, "SUPABASE_URL", "")
    monkeypatch.setattr(settings, "SUPABASE_SERVICE_ROLE_KEY", "")
    storage = CustomerKycStorage()
    with pytest.raises(HTTPException) as error:
        await storage.upload(
            user_id=str(uuid.uuid4()),
            document_id=str(uuid.uuid4()),
            extension="pdf",
            mime_type="application/pdf",
            content=b"%PDF-1.7",
        )
    assert error.value.status_code == 503


@pytest.mark.asyncio
async def test_customer_kyc_upload_requires_authentication(client):
    response = await client.post(
        "/api/v1/customers/kyc/documents",
        data={"document_type": "PAN_CARD"},
        files={"file": ("identity.pdf", b"%PDF-1.7 content", "application/pdf")},
    )
    assert response.status_code == 401
