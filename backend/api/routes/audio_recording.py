import os
from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File, Form, Query
from fastapi.responses import Response
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user, require_role
from backend.domain.models import User
from backend.application.services.audio_recording_service import AudioRecordingService
from backend.integrations.storage.provider import verify_signed_url_token, get_storage_provider

router = APIRouter(tags=["In-Cabin Audio Safety Recording (DPDP / IT Act 2000)"])


# --- Schemas ---
class ConsentActionPayload(BaseModel):
    consented: bool = True
    role: Optional[str] = None  # CUSTOMER or DRIVER
    consent_purpose: str = "IN_CABIN_SAFETY_DPDP_COMPLIANT"
    consent_version: str = "v1.0"


class ConsentWithdrawPayload(BaseModel):
    reason: Optional[str] = None


class StopSessionPayload(BaseModel):
    reason: str = "USER_STOPPED"


# --- 1. Ride-Level Consent Endpoints ---

@router.post("/rides/{ride_id}/audio-consent")
async def record_audio_consent(
    ride_id: str,
    payload: ConsentActionPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = AudioRecordingService(db)
    return await service.record_consent(
        ride_id=ride_id,
        user=current_user,
        consented=payload.consented,
        role=payload.role,
        consent_purpose=payload.consent_purpose,
        consent_version=payload.consent_version
    )


@router.get("/rides/{ride_id}/audio-consent")
async def get_audio_consent_status(
    ride_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = AudioRecordingService(db)
    return await service.get_consent_status(ride_id=ride_id, user=current_user)


@router.post("/rides/{ride_id}/audio-consent/withdraw")
async def withdraw_audio_consent(
    ride_id: str,
    payload: ConsentWithdrawPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = AudioRecordingService(db)
    return await service.withdraw_consent(ride_id=ride_id, user=current_user, reason=payload.reason)


# --- 2. Recording Session Lifecycle Endpoints ---

@router.post("/rides/{ride_id}/audio-recording/authorize")
async def authorize_audio_recording(
    ride_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = AudioRecordingService(db)
    return await service.authorize_session(ride_id=ride_id, user=current_user)


@router.post("/audio-recordings/{session_id}/start")
@router.post("/rides/{ride_id}/audio-recording/start")
async def start_audio_recording(
    session_id: Optional[str] = None,
    ride_id: Optional[str] = None,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = AudioRecordingService(db)
    target_session_id = session_id
    if not target_session_id and ride_id:
        status_res = await service.get_consent_status(ride_id, current_user)
        target_session_id = status_res.get("session", {}).get("session_id")
        if not target_session_id:
            auth_res = await service.authorize_session(ride_id, current_user)
            target_session_id = auth_res["session_id"]

    if not target_session_id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Session ID required.")
    return await service.start_session(session_id=target_session_id, user=current_user)


@router.post("/audio-recordings/{session_id}/heartbeat")
@router.post("/rides/{ride_id}/audio-recording/heartbeat")
async def recording_heartbeat(
    session_id: Optional[str] = None,
    ride_id: Optional[str] = None,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = AudioRecordingService(db)
    target_session_id = session_id
    if not target_session_id and ride_id:
        status_res = await service.get_consent_status(ride_id, current_user)
        target_session_id = status_res.get("session", {}).get("session_id")

    if not target_session_id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Session ID required.")
    return await service.heartbeat(session_id=target_session_id, user=current_user)


@router.post("/audio-recordings/{session_id}/stop")
@router.post("/rides/{ride_id}/audio-recording/stop")
async def stop_audio_recording(
    payload: StopSessionPayload,
    session_id: Optional[str] = None,
    ride_id: Optional[str] = None,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = AudioRecordingService(db)
    target_session_id = session_id
    if not target_session_id and ride_id:
        status_res = await service.get_consent_status(ride_id, current_user)
        target_session_id = status_res.get("session", {}).get("session_id")

    if not target_session_id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Session ID required.")
    return await service.stop_session(session_id=target_session_id, user=current_user, reason=payload.reason)


# --- 3. Segment Upload & Retrieval ---

@router.post("/audio-recordings/{session_id}/segments")
async def upload_audio_segment(
    session_id: str,
    sequence: int = Form(...),
    duration_seconds: float = Form(...),
    encryption_nonce: str = Form(...),
    encryption_tag: str = Form(...),
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = AudioRecordingService(db)
    return await service.upload_encrypted_segment(
        session_id=session_id,
        user=current_user,
        segment_sequence=sequence,
        duration_seconds=duration_seconds,
        encryption_nonce=encryption_nonce,
        encryption_tag=encryption_tag,
        file=file
    )


@router.get("/audio-recordings/{session_id}/status")
async def get_audio_recording_session_status(
    session_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = AudioRecordingService(db)
    return await service.admin_get_session_details(session_id=session_id, admin_user=current_user)


@router.get("/safety/audio-recordings/download")
@router.get("/audio-recordings/download")
async def download_private_audio_file(
    file: str = Query(...),
    expires: int = Query(...),
    sig: str = Query(...)
):
    if not verify_signed_url_token(file, expires, sig):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Tampered, invalid, or expired cryptographic signature for private audio file."
        )

    storage = get_storage_provider()
    # Resolve local file path
    base_dir = getattr(storage, "base_dir", None) or getattr(getattr(storage, "fallback", None), "base_dir", "")
    full_path = os.path.abspath(os.path.join(base_dir, file.replace("/", os.sep)))
    if not full_path.startswith(base_dir):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied.")

    content = await storage.get_audio_segment_bytes(full_path)
    if not content:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Audio file not found on storage.")

    return Response(
        content=content,
        media_type="application/octet-stream",
        headers={
            "Content-Disposition": f'attachment; filename="{os.path.basename(full_path)}"',
            "X-Content-Type-Options": "nosniff"
        }
    )
