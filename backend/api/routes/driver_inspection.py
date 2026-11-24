"""API Routes for Driver Daily Vehicle Inspection, Odometer Verification, Photo Uploads & Evidence."""
import os
import urllib.parse
from typing import Optional, Dict, Any, List
from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File, Form, Response, Query
from fastapi.responses import Response as FastApiResponse
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.api.deps import get_current_driver, get_current_user
from backend.domain.models import User
from backend.infrastructure.database.session import get_db
from backend.application.services.driver_inspection_service import DriverInspectionService
from backend.integrations.storage.provider import verify_signed_url_token, get_storage_provider

router = APIRouter(prefix="/drivers/vehicle-inspections", tags=["Driver Vehicle Inspection"])


class StartInspectionPayload(BaseModel):
    vehicle_id: Optional[str] = None


class SubmitInspectionPayload(BaseModel):
    odometer_reading: float = Field(..., ge=0, le=999999.0, description="Odometer reading in kilometers")
    checklist_results: Dict[str, Any] = Field(..., description="Checklist item statuses: PASS, FAIL, NOT_APPLICABLE")
    driver_remarks: Optional[str] = Field(None, max_length=1000)


@router.post("/start")
async def start_or_get_draft_inspection(
    payload: StartInspectionPayload = StartInspectionPayload(),
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Initializes or retrieves today's draft inspection for the driver's vehicle."""
    service = DriverInspectionService(db)
    return await service.get_or_create_draft_inspection(user.id, payload.vehicle_id)


@router.get("/current")
async def get_current_inspection(
    vehicle_id: Optional[str] = Query(None),
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Retrieves today's current vehicle inspection status and photo evidence."""
    service = DriverInspectionService(db)
    return await service.get_current_inspection(user.id, vehicle_id)


@router.get("/{inspection_id}")
async def get_inspection_details(
    inspection_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Retrieves full inspection details by inspection ID."""
    service = DriverInspectionService(db)
    return await service.get_admin_inspection_details(inspection_id)


@router.post("/{inspection_id}/photos")
async def upload_inspection_photo(
    inspection_id: str,
    category: str = Form(..., description="Photo category: ODOMETER, FRONT, REAR, LEFT_SIDE, RIGHT_SIDE, INTERIOR, DAMAGE"),
    file: UploadFile = File(...),
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Uploads a real photograph captured via device camera into Supabase/local private storage."""
    service = DriverInspectionService(db)
    return await service.upload_inspection_photo(
        user_id=user.id,
        inspection_id=inspection_id,
        photo_category=category,
        file=file
    )


@router.post("/{inspection_id}/submit")
async def submit_inspection(
    inspection_id: str,
    payload: SubmitInspectionPayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Authoritative submission of vehicle inspection with odometer reading, checklist, and photo evidence."""
    service = DriverInspectionService(db)
    return await service.submit_inspection(
        user_id=user.id,
        inspection_id=inspection_id,
        odometer_reading=payload.odometer_reading,
        checklist_results=payload.checklist_results,
        driver_remarks=payload.driver_remarks
    )


@router.get("/{inspection_id}/photos/{photo_id}/signed-url")
async def get_photo_signed_url(
    inspection_id: str,
    photo_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Retrieves a secure, time-limited signed URL to view an uploaded inspection photograph."""
    service = DriverInspectionService(db)
    signed_url = await service.get_photo_signed_url(photo_id, user.id, is_admin=False)
    return {"photo_id": photo_id, "signed_url": signed_url}


@router.get("/{inspection_id}/photos/{photo_id}/content")
async def get_photo_content(
    inspection_id: str,
    photo_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    """Streams the binary image bytes for the requested inspection photo."""
    service = DriverInspectionService(db)
    content, mime_type = await service.get_photo_bytes(photo_id, user.id, is_admin=False)
    return FastApiResponse(content=content, media_type=mime_type)


@router.get("/photos/download")
async def download_signed_inspection_photo(
    file: str = Query(...),
    expires: int = Query(...),
    sig: str = Query(...)
):
    """Publicly inaccessible, HMAC-authenticated endpoint for signed local private storage downloads."""
    rel_path = urllib.parse.unquote(file)
    if not verify_signed_url_token(rel_path, expires, sig):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Forbidden: Inspection photo download link is invalid or has expired."
        )

    storage = get_storage_provider()
    # Read via storage provider
    bytes_data = await storage.get_inspection_photo_bytes(rel_path)
    if not bytes_data:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Inspection file not found.")

    mime = "image/jpeg"
    if rel_path.endswith(".png"):
        mime = "image/png"
    elif rel_path.endswith(".webp"):
        mime = "image/webp"

    return FastApiResponse(content=bytes_data, media_type=mime)
