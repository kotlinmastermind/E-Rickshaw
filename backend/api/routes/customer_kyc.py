"""Customer KYC document submission endpoints.

Only document metadata is stored in PostgreSQL. Binary identity documents are sent
exclusively to a verified private Supabase Storage bucket.
"""
import hashlib
import json
import logging
import os
import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile, status
from sqlalchemy import desc, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from backend.api.deps import get_current_customer
from backend.domain.models import CustomerKycAuditLog, CustomerKycDocument, User
from backend.infrastructure.database.session import get_db
from backend.integrations.storage.customer_kyc import customer_kyc_storage

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/customers/kyc", tags=["Customer KYC"])

MAX_KYC_FILE_BYTES = 5 * 1024 * 1024
ALLOWED_DOCUMENT_TYPES = {"PAN_CARD", "DRIVING_LICENSE", "PASSPORT", "VOTER_ID"}
ALLOWED_FILE_SIGNATURES = {
    "application/pdf": (b"%PDF", {"pdf"}),
    "image/jpeg": (b"\xff\xd8\xff", {"jpg", "jpeg"}),
    "image/png": (b"\x89PNG\r\n\x1a\n", {"png"}),
}


def _detect_document_mime(content: bytes, filename: str) -> tuple[str, str]:
    extension = os.path.splitext(filename or "")[1].lower().lstrip(".")
    for mime_type, (signature, extensions) in ALLOWED_FILE_SIGNATURES.items():
        if content.startswith(signature):
            if extension and extension not in extensions:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="The selected file extension does not match its actual content.",
                )
            return mime_type, next(iter(extensions)) if extension not in extensions else extension
    raise HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST,
        detail="Unsupported identity document. Upload a valid PDF, JPEG, or PNG file.",
    )


def _audit(
    *,
    document_id: str,
    actor: User,
    action: str,
    request: Request,
    metadata: dict | None = None,
) -> CustomerKycAuditLog:
    return CustomerKycAuditLog(
        document_id=document_id,
        actor_id=actor.id,
        actor_role=(actor.role or "customer").upper(),
        action=action,
        metadata_json=json.dumps(metadata or {}, separators=(",", ":")),
        ip_address=request.client.host if request.client else None,
        user_agent=(request.headers.get("user-agent") or "")[:255] or None,
    )


@router.get("")
async def get_customer_kyc_status(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(
        select(CustomerKycDocument)
        .where(CustomerKycDocument.user_id == user.id)
        .order_by(desc(CustomerKycDocument.submitted_at))
    )
    documents = result.scalars().all()
    return {
        "kyc_status": (
            "PENDING_REVIEW" if any(d.status == "PENDING_REVIEW" for d in documents)
            else "REJECTED" if any(d.status == "REJECTED" for d in documents)
            else "APPROVED" if any(d.status == "APPROVED" for d in documents)
            else "NOT_SUBMITTED"
        ),
        "documents": [
            {
                "id": d.id,
                "document_type": d.document_type,
                "status": d.status,
                "submitted_at": d.submitted_at.isoformat() if d.submitted_at else None,
                "reviewed_at": d.reviewed_at.isoformat() if d.reviewed_at else None,
                "review_notes": d.review_notes,
            }
            for d in documents
        ],
        "identity_verified": False,
        "message": "Document review is manual. Account verification is not changed by document submission.",
    }


@router.delete("/documents")
async def withdraw_customer_kyc_documents(
    request: Request,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(
        select(CustomerKycDocument).where(
            CustomerKycDocument.user_id == user.id,
            CustomerKycDocument.status != "WITHDRAWN",
        )
    )
    documents = result.scalars().all()
    withdrawn_count = 0
    for document in documents:
        await customer_kyc_storage.delete(document.storage_path)
        document.status = "WITHDRAWN"
        document.storage_path = f"purged://{document.id}"
        document.mime_type = "application/octet-stream"
        document.file_size_bytes = 0
        document.sha256_hash = "0" * 64
        document.review_notes = None
        document.updated_at = datetime.now(timezone.utc)
        db.add(
            _audit(
                document_id=document.id,
                actor=user,
                action="DOCUMENT_WITHDRAWN",
                request=request,
                metadata={"reason": "CUSTOMER_REQUEST", "document_type": document.document_type},
            )
        )
        withdrawn_count += 1
    await db.commit()
    return {
        "withdrawn_count": withdrawn_count,
        "message": "Submitted identity documents were removed from private storage and withdrawn from review.",
    }


@router.post("/documents", status_code=status.HTTP_201_CREATED)
async def submit_customer_kyc_document(
    request: Request,
    document_type: str = Form(...),
    consent_to_processing: bool = Form(False),
    file: UploadFile = File(...),
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db),
):
    if not consent_to_processing:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Consent to private document storage and manual review is required.",
        )

    normalized_type = (document_type or "").strip().upper()
    if normalized_type not in ALLOWED_DOCUMENT_TYPES:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Unsupported document type. Supported: {', '.join(sorted(ALLOWED_DOCUMENT_TYPES))}.",
        )

    pending = await db.execute(
        select(CustomerKycDocument.id).where(
            CustomerKycDocument.user_id == user.id,
            CustomerKycDocument.document_type == normalized_type,
            CustomerKycDocument.status == "PENDING_REVIEW",
        ).limit(1)
    )
    if pending.scalar_one_or_none():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A document of this type is already awaiting review.",
        )

    content = await file.read(MAX_KYC_FILE_BYTES + 1)
    if not content:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="The selected document is empty.")
    if len(content) > MAX_KYC_FILE_BYTES:
        raise HTTPException(status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, detail="KYC documents must be 5 MB or smaller.")

    mime_type, extension = _detect_document_mime(content, file.filename or "")
    document_id = str(uuid.uuid4())
    digest = hashlib.sha256(content).hexdigest()
    storage_path = await customer_kyc_storage.upload(
        user_id=user.id,
        document_id=document_id,
        extension=extension,
        mime_type=mime_type,
        content=content,
    )

    now = datetime.now(timezone.utc)
    document = CustomerKycDocument(
        id=document_id,
        user_id=user.id,
        document_type=normalized_type,
        storage_path=storage_path,
        mime_type=mime_type,
        file_size_bytes=len(content),
        sha256_hash=digest,
        status="PENDING_REVIEW",
        submitted_at=now,
        created_at=now,
        updated_at=now,
    )
    db.add(document)
    db.add(
        _audit(
            document_id=document_id,
            actor=user,
            action="DOCUMENT_SUBMITTED",
            request=request,
            metadata={
                "document_type": normalized_type,
                "mime_type": mime_type,
                "file_size_bytes": len(content),
                "consent_to_processing": True,
                "consented_at": now.isoformat(),
            },
        )
    )
    try:
        await db.commit()
    except IntegrityError as exc:
        await db.rollback()
        try:
            await customer_kyc_storage.delete(storage_path)
        except Exception:
            logger.exception("Failed to clean up orphaned customer KYC object after database integrity failure.")
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A conflicting customer KYC document submission already exists.",
        ) from exc
    except Exception:
        await db.rollback()
        try:
            await customer_kyc_storage.delete(storage_path)
        except Exception:
            logger.exception("Failed to clean up orphaned customer KYC object after database commit failure.")
        raise

    return {
        "id": document.id,
        "document_type": document.document_type,
        "status": document.status,
        "submitted_at": document.submitted_at.isoformat(),
        "message": "Document securely submitted for manual review. It has not been verified yet.",
    }
