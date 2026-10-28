from typing import Optional, Dict, Any, List
from fastapi import APIRouter, Depends, Request, HTTPException, status, UploadFile, File, Response
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.infrastructure.database.session import get_db
from backend.application.services.user_governance_service import UserGovernanceService
from backend.api.deps import get_current_user
from backend.domain.models import User, DataExportArchive
from backend.infrastructure.security.crypto import decode_jwt_token

router = APIRouter(prefix="/user", tags=["User Governance & Security"])

class BiometricTogglePayload(BaseModel):
    enabled: bool

class AppLockPinPayload(BaseModel):
    pin: str = Field(..., min_length=4, max_length=4)

class PasskeyRegisterPayload(BaseModel):
    credential_id: str
    public_key: str

class MfaEnablePayload(BaseModel):
    code: str = Field(..., min_length=6, max_length=6)

class TrustedDevicePayload(BaseModel):
    device_id: str
    device_name: str
    device_os: str = "Android"

class SavedPhonePayload(BaseModel):
    phone_number: str
    label: str = "Personal"

class UserPreferencesPayload(BaseModel):
    preferred_language: Optional[str] = None
    currency_code: Optional[str] = None
    number_format: Optional[str] = None
    text_scale: Optional[str] = None
    high_contrast: Optional[bool] = None
    reduced_motion: Optional[bool] = None
    analytics_consent: Optional[bool] = None
    crash_reports_consent: Optional[bool] = None
    background_location_consent: Optional[bool] = None
    third_party_sharing_consent: Optional[bool] = None
    marketing_push_opt_in: Optional[bool] = None
    marketing_email_opt_in: Optional[bool] = None
    marketing_sms_opt_in: Optional[bool] = None

class MembershipPreferencesPayload(BaseModel):
    tier: Optional[str] = None
    temperature_celsius: Optional[float] = None
    quiet_ride: Optional[bool] = None
    preferred_music: Optional[str] = None
    preferred_water: Optional[str] = None
    luggage_assistance: Optional[bool] = None

class DeletionRequestPayload(BaseModel):
    reason: Optional[str] = None

# 5. Biometric App Unlock
@router.post("/biometric/toggle")
async def toggle_biometric(
    payload: BiometricTogglePayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.toggle_biometric(current_user.id, payload.enabled)

# 19. App Lock with PIN
@router.post("/app-lock/pin")
async def set_app_lock_pin(
    payload: AppLockPinPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.set_app_lock_pin(current_user.id, payload.pin)

@router.post("/app-lock/verify")
async def verify_app_lock_pin(
    payload: AppLockPinPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.verify_app_lock_pin(current_user.id, payload.pin)

# 4. Passkey Registration
@router.get("/passkey/register-challenge")
async def passkey_register_challenge(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.create_passkey_challenge(current_user.id)

@router.post("/passkey/register")
async def register_passkey(
    payload: PasskeyRegisterPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.register_passkey(current_user.id, payload.credential_id, payload.public_key)

# 6. MFA Configuration
@router.post("/mfa/setup")
async def setup_mfa(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.setup_mfa(current_user.id)

@router.post("/mfa/enable")
async def enable_mfa(
    payload: MfaEnablePayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.enable_mfa(current_user.id, payload.code)

@router.post("/mfa/disable")
async def disable_mfa(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.disable_mfa(current_user.id)

# 7. Trusted Device Management
@router.get("/trusted-devices")
async def list_trusted_devices(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.list_trusted_devices(current_user.id)

@router.post("/trusted-devices")
async def add_trusted_device(
    payload: TrustedDevicePayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.add_trusted_device(
        user_id=current_user.id,
        device_id=payload.device_id,
        device_name=payload.device_name,
        device_os=payload.device_os
    )

@router.delete("/trusted-devices/{device_id}")
async def revoke_trusted_device(
    device_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.revoke_trusted_device(current_user.id, device_id)

# 8 & 9. Session and Device List + Remote Sign-out
@router.get("/sessions")
async def list_sessions(
    request: Request,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    current_session_id = None
    auth_header = request.headers.get("authorization", "")
    if auth_header.startswith("Bearer "):
        try:
            token = auth_header.split(" ")[1]
            payload = decode_jwt_token(token)
            current_session_id = payload.get("session_id")
        except Exception:
            pass

    service = UserGovernanceService(db)
    return await service.list_sessions(current_user.id, current_session_id)

@router.post("/sessions/revoke-all-others")
async def revoke_all_other_sessions(
    request: Request,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    current_session_id = None
    auth_header = request.headers.get("authorization", "")
    if auth_header.startswith("Bearer "):
        try:
            token = auth_header.split(" ")[1]
            payload = decode_jwt_token(token)
            current_session_id = payload.get("session_id")
        except Exception:
            pass

    service = UserGovernanceService(db)
    return await service.revoke_other_sessions(current_user.id, current_session_id)

# 10. Profile Photo Upload and Removal
@router.post("/avatar")
async def upload_avatar(
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.upload_avatar(current_user.id, file)

@router.delete("/avatar")
async def delete_avatar(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.delete_avatar(current_user.id)

# 11. Multiple Saved Phone Numbers
@router.get("/phones")
async def list_saved_phones(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.list_saved_phones(current_user.id)

@router.post("/phones")
async def add_saved_phone(
    payload: SavedPhonePayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.add_saved_phone(current_user.id, payload.phone_number, payload.label)

@router.delete("/phones/{phone_id}")
async def delete_saved_phone(
    phone_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.delete_saved_phone(current_user.id, phone_id)

# 13, 14, 15, 17, 18, 22, 23. User Preferences (Language, Formatting, Accessibility, Privacy, Marketing)
@router.get("/preferences")
async def get_user_preferences(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.get_user_preferences(current_user.id)

@router.put("/preferences")
async def update_user_preferences(
    payload: UserPreferencesPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.update_user_preferences(current_user.id, payload.model_dump(exclude_unset=True))

# 20. Account Deletion Progress Tracking
@router.post("/deletion/request")
async def request_deletion(
    payload: DeletionRequestPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.request_account_deletion(current_user.id, payload.reason)

@router.get("/deletion/status")
async def get_deletion_status(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.get_deletion_status(current_user.id)

@router.post("/deletion/cancel")
async def cancel_deletion(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.cancel_deletion_request(current_user.id)

# 21. Download Personal Data Archive
@router.post("/data-export")
async def request_data_export(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.request_data_export(current_user.id)

@router.get("/data-export/latest")
async def get_latest_data_export(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.get_latest_data_archive(current_user.id)

@router.get("/data-export/download/{archive_id}")
async def download_archive(
    archive_id: str,
    db: AsyncSession = Depends(get_db)
):
    stmt = select(DataExportArchive).where(DataExportArchive.download_url.like(f"%{archive_id}%"))
    res = await db.execute(stmt)
    archive = res.scalars().first()
    if not archive or not archive.payload_json:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Data export archive not found or expired.")

    return Response(
        content=archive.payload_json,
        media_type="application/json",
        headers={"Content-Disposition": f"attachment; filename=grand_chauffeur_data_archive_{archive.id[:8]}.json"}
    )

# 25. Customer Membership Profile and Preferences
@router.get("/membership")
async def get_membership(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.get_membership_details(current_user.id)

@router.put("/membership")
async def update_membership(
    payload: MembershipPreferencesPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = UserGovernanceService(db)
    return await service.update_membership_preferences(current_user.id, payload.model_dump(exclude_unset=True))
