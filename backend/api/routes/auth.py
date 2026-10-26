from typing import Optional
from fastapi import APIRouter, Depends, Request, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.application.services.auth_service import AuthService
from backend.api.deps import get_current_user
from backend.domain.models import User
from backend.infrastructure.security.rate_limiter import otp_rate_limiter, auth_rate_limiter
from backend.infrastructure.security.crypto import decode_jwt_token

router = APIRouter(prefix="/auth", tags=["Authentication"])

class OtpRequestPayload(BaseModel):
    phone_number: str = Field(..., json_schema_extra={"example": "+919876543210"})

class OtpVerifyPayload(BaseModel):
    phone_number: str = Field(..., json_schema_extra={"example": "+919876543210"})
    otp_code: str = Field(..., min_length=4, max_length=6, json_schema_extra={"example": "123456"})
    role: str = Field(default="customer", json_schema_extra={"example": "customer"})  # customer, driver, admin

class TokenRefreshPayload(BaseModel):
    refresh_token: str

@router.post("/otp/request", dependencies=[Depends(otp_rate_limiter)])
async def request_otp(payload: OtpRequestPayload, db: AsyncSession = Depends(get_db)):
    service = AuthService(db)
    return await service.request_otp(payload.phone_number)

@router.post("/otp/verify", dependencies=[Depends(auth_rate_limiter)])
async def verify_otp(payload: OtpVerifyPayload, request: Request, db: AsyncSession = Depends(get_db)):
    service = AuthService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    return await service.verify_otp(
        phone_number=payload.phone_number,
        otp_code=payload.otp_code,
        role=payload.role,
        ip_address=ip,
        user_agent=ua
    )

class FirebaseVerifyPayload(BaseModel):
    phone_number: str = Field(..., json_schema_extra={"example": "+919876543210"})
    role: str = Field(default="customer")
    firebase_uid: Optional[str] = None
    id_token: Optional[str] = None

@router.post("/firebase/verify", dependencies=[Depends(auth_rate_limiter)])
async def verify_firebase(payload: FirebaseVerifyPayload, request: Request, db: AsyncSession = Depends(get_db)):
    service = AuthService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    return await service.verify_firebase_auth(
        phone_number=payload.phone_number,
        role=payload.role,
        firebase_uid=payload.firebase_uid,
        id_token=payload.id_token,
        ip_address=ip,
        user_agent=ua
    )

@router.post("/token/refresh", dependencies=[Depends(auth_rate_limiter)])
async def refresh_token(payload: TokenRefreshPayload, db: AsyncSession = Depends(get_db)):
    """Rotates refresh tokens and provides a newly signed short-lived access token."""
    service = AuthService(db)
    return await service.refresh_token_rotation(payload.refresh_token)

@router.get("/me")
async def get_me(current_user: User = Depends(get_current_user)):
    return {
        "id": current_user.id,
        "phone_number": current_user.phone_number,
        "email": current_user.email,
        "full_name": current_user.full_name,
        "role": current_user.role,
        "is_active": current_user.is_active,
        "is_verified": current_user.is_verified
    }

@router.post("/logout")
async def logout(
    request: Request,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Terminates active session and blacklists it across distributed instances."""
    auth_header = request.headers.get("authorization", "")
    if auth_header.startswith("Bearer "):
        token = auth_header.split(" ")[1]
        try:
            payload = decode_jwt_token(token)
            session_id = payload.get("session_id")
            if session_id:
                service = AuthService(db)
                await service.revoke_session(session_id)
        except Exception:
            pass
    return {"message": "Session terminated successfully."}

@router.post("/logout-all")
async def logout_all(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Terminates all active sessions across all devices for the current user."""
    service = AuthService(db)
    await service.revoke_all_user_sessions(current_user.id)
    return {"message": "All active sessions revoked across all devices."}
