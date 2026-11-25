from typing import Optional
from sqlalchemy import update
from sqlalchemy import select, or_
from backend.domain.models import User, CustomerProfile
from fastapi import APIRouter, Body, Depends, Request, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.application.services.auth_service import AuthService
from backend.application.services.user_governance_service import UserGovernanceService
from backend.api.deps import get_current_user
from backend.domain.models import User
from backend.infrastructure.security.rate_limiter import auth_rate_limiter, otp_rate_limiter
from backend.api.schemas.auth import (
    IdentityLookupPayload,
    OtpRequestPayload,
    OtpVerifyPayload,
    TokenRefreshPayload,
    PasswordRegisterPayload,
    PasswordLoginPayload,
    AdminMfaEnablePayload,
    OAuthPayload,
    PasskeyLoginPayload,
    MfaChallengeVerifyPayload,
    RecoveryRequestPayload,
    PasswordResetPayload,
    EmailVerifyPayload,
    CustomerSignupPayload,
    DriverSignupPayload,
    UnifiedLoginPayload,
    UserDto,
    AuthResponse
)
from backend.infrastructure.security.crypto import (
    decode_jwt_token,
    verify_password,
    hash_password,
    validate_password_strength,
)
router = APIRouter(prefix="/auth", tags=["Authentication"])

class ChangePasswordPayload(BaseModel):
    current_password: Optional[str] = None
    new_password: str = Field(..., min_length=8)



class AvatarUpdatePayload(BaseModel):
    avatar_url: str = Field(..., min_length=1)



class CustomerKycUpdatePayload(BaseModel):
    phone_number: Optional[str] = None
    full_name: str = Field(..., min_length=2)
    email: Optional[str] = None
    govt_id_number: Optional[str] = None

@router.post("/kyc/customer")
@router.put("/kyc/customer")
async def update_customer_kyc(
    payload: CustomerKycUpdatePayload,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    user: Optional[User] = None

    # 1. Pehle Bearer token se logged-in user ko dhundhein
    auth_header = request.headers.get("authorization", "")
    if auth_header.startswith("Bearer "):
        try:
            token = auth_header.split(" ")[1]
            decoded = decode_jwt_token(token)
            user_id = decoded.get("sub")
            if user_id:
                res = await db.execute(select(User).where(User.id == user_id))
                user = res.scalar_one_or_none()
        except Exception:
            pass

    # 2. Agar token nahi hai toh phone_number se user dhundhein
    if not user and payload.phone_number:
        digits = "".join(filter(str.isdigit, payload.phone_number))
        last_10 = digits[-10:] if len(digits) >= 10 else digits
        res = await db.execute(
            select(User).where(
                or_(
                    User.phone_number == payload.phone_number.strip(),
                    User.phone_number.endswith(last_10)
                )
            )
        )
        user = res.scalars().first()

    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Account nahi mila. Kripya pehle phone number se OTP verify karein."
        )

    # 3. User details update karein
    user.full_name = payload.full_name.strip()
    if payload.email and payload.email.strip():
        user.email = payload.email.strip().lower()
    user.is_verified = True

    # 4. CustomerProfile record ensure/update karein
    prof_res = await db.execute(select(CustomerProfile).where(CustomerProfile.user_id == user.id))
    profile = prof_res.scalar_one_or_none()
    if not profile:
        profile = CustomerProfile(user_id=user.id)
        db.add(profile)

    await db.commit()
    return {
        "message": "Customer profile and KYC saved successfully.",
        "user_id": user.id,
        "full_name": user.full_name,
        "email": user.email,
        "is_verified": user.is_verified
    }


@router.put("/avatar")
@router.post("/avatar")
async def update_user_avatar(
    payload: AvatarUpdatePayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    try:
        clean_url = payload.avatar_url.strip()
        await db.execute(
            update(User)
            .where(User.id == current_user.id)
            .values(avatar_url=clean_url)
        )
        await db.commit()
        return {
            "message": "Profile photo updated successfully.",
            "avatar_url": clean_url
        }
    except Exception as exc:
        await db.rollback()
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Avatar DB update failed: {str(exc)}"
        )


@router.post("/lookup", dependencies=[Depends(auth_rate_limiter)])
async def lookup_user_identity(payload: IdentityLookupPayload, db: AsyncSession = Depends(get_db)):
    service = AuthService(db)
    return await service.lookup_user_identity(payload.identifier, payload.role)

@router.post("/otp/request", dependencies=[Depends(otp_rate_limiter)])
@router.post("/otp/send", dependencies=[Depends(otp_rate_limiter)])
async def request_otp(payload: OtpRequestPayload, db: AsyncSession = Depends(get_db)):
    """Dispatches a 6-digit OTP verification passcode to the user's mobile via Twilio Verify Service."""
    service = AuthService(db)
    return await service.request_otp(payload.phone_number)

@router.post("/otp/verify", dependencies=[Depends(auth_rate_limiter)])
async def verify_otp(payload: OtpVerifyPayload, request: Request, db: AsyncSession = Depends(get_db)):
    """Verifies the 6-digit OTP via Twilio Verify Service and establishes authenticated JWT session."""
    service = AuthService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    return await service.verify_otp(
        phone_number=payload.phone_number,
        otp_code=payload.otp_code,
        role=payload.role or "customer",
        full_name=payload.full_name,
        ip_address=ip,
        user_agent=ua
    )

# 1. Sign in & Register with Email and Password
@router.post("/password/register", dependencies=[Depends(auth_rate_limiter)])
async def register_password(payload: PasswordRegisterPayload, request: Request, db: AsyncSession = Depends(get_db)):
    service = UserGovernanceService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    return await service.register_with_password(
        email=payload.email,
        password=payload.password,
        full_name=payload.full_name,
        phone_number=payload.phone_number,
        role=payload.role,
        ip_address=ip,
        user_agent=ua
    )

@router.post("/password/login", dependencies=[Depends(auth_rate_limiter)])
async def login_password(payload: PasswordLoginPayload, request: Request, db: AsyncSession = Depends(get_db)):
    service = UserGovernanceService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    return await service.login_with_password(
        email=payload.email,
        password=payload.password,
        ip_address=ip,
        user_agent=ua
    )

@router.post("/admin/password/login", dependencies=[Depends(auth_rate_limiter)])
async def admin_password_login(payload: PasswordLoginPayload, request: Request, db: AsyncSession = Depends(get_db)):
    return await UserGovernanceService(db).login_with_password(
        email=payload.email,
        password=payload.password,
        ip_address=request.client.host if request.client else None,
        user_agent=request.headers.get("user-agent"),
        require_admin=True,
    )

@router.post("/admin/mfa/setup", dependencies=[Depends(auth_rate_limiter)])
async def admin_mfa_setup(payload: PasswordLoginPayload, db: AsyncSession = Depends(get_db)):
    return await UserGovernanceService(db).setup_admin_mfa_by_password(payload.email, payload.password)

@router.post("/admin/mfa/enable", dependencies=[Depends(auth_rate_limiter)])
async def admin_mfa_enable(payload: AdminMfaEnablePayload, request: Request, db: AsyncSession = Depends(get_db)):
    return await UserGovernanceService(db).enable_admin_mfa_with_setup_token(
        setup_token=payload.setup_token,
        code=payload.code,
        ip_address=request.client.host if request.client else None,
        user_agent=request.headers.get("user-agent"),
    )

# Supported OAuth Sign-In (Apple SSO; Google Sign-In removed per FCM-only policy)
@router.post("/oauth/apple", dependencies=[Depends(auth_rate_limiter)])
async def oauth_apple(payload: OAuthPayload, request: Request, db: AsyncSession = Depends(get_db)):
    service = UserGovernanceService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    return await service.oauth_login_or_register(
        provider="apple",
        identity_token=payload.token,
        email=payload.email,
        full_name=payload.full_name,
        phone_number=payload.phone_number,
        role=payload.role,
        ip_address=ip,
        user_agent=ua
    )

# 4. Passkey Sign-In (SECURITY CONTAINMENT: Fail-Closed)
@router.get("/passkey/login-challenge")
async def passkey_login_challenge(db: AsyncSession = Depends(get_db)):
    service = UserGovernanceService(db)
    return await service.create_passkey_challenge()

@router.post("/passkey/login-verify", dependencies=[Depends(auth_rate_limiter)])
async def passkey_login_verify(payload: PasskeyLoginPayload, request: Request, db: AsyncSession = Depends(get_db)):
    service = UserGovernanceService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    cid = (payload.credential_id or payload.credentialId or "").strip()
    sig = (payload.signature or "").strip()
    if not cid:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Credential ID is required.")
    if not sig:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Assertion signature is required.")
    return await service.verify_passkey_login(
        credential_id=cid,
        signature=sig,
        ip_address=ip,
        user_agent=ua
    )

# 6. Multi-Factor Authentication Challenge Verify
@router.post("/mfa/challenge-verify", dependencies=[Depends(auth_rate_limiter)])
async def mfa_challenge_verify(payload: MfaChallengeVerifyPayload, request: Request, db: AsyncSession = Depends(get_db)):
    service = UserGovernanceService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    token = payload.mfa_token or payload.challenge_token or ""
    code = payload.code or payload.totp_code or ""
    return await service.verify_mfa_login_challenge(
        mfa_token=token,
        code=code,
        ip_address=ip,
        user_agent=ua
    )

# 12. Verified Email Address
@router.post("/email/send-verification")
async def send_email_verification(current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    service = UserGovernanceService(db)
    return await service.send_email_verification(current_user.id)

@router.post("/email/verify")
async def verify_email(payload: EmailVerifyPayload, current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    service = UserGovernanceService(db)
    return await service.verify_email_code(current_user.id, payload.code)

# 24. Account Recovery Workflow
@router.post("/recovery/request", dependencies=[Depends(auth_rate_limiter)])
async def recovery_request(payload: RecoveryRequestPayload, db: AsyncSession = Depends(get_db)):
    service = UserGovernanceService(db)
    identifier = payload.identifier or payload.email or ""
    return await service.request_account_recovery(identifier)

@router.post("/recovery/reset-password", dependencies=[Depends(auth_rate_limiter)])
async def recovery_reset_password(payload: PasswordResetPayload, db: AsyncSession = Depends(get_db)):
    service = UserGovernanceService(db)
    recovery_code = payload.recovery_code or payload.recovery_token or ""
    user_id = payload.user_id or "uid"
    return await service.reset_password_with_token(
        user_id=user_id,
        recovery_code=recovery_code,
        new_password=payload.new_password
    )


# --- YAHAN PASTE KAREIN ---
@router.post("/change-password", dependencies=[Depends(auth_rate_limiter)])
@router.put("/change-password", dependencies=[Depends(auth_rate_limiter)])
async def change_password_authenticated(
    payload: ChangePasswordPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # Agar user ne pehle se password set kar rakha hai aur current_password bheja hai toh verify karein
    if payload.current_password and current_user.password_hash:
        # OTP-only users ke default hash ko chhod kar baaki sabka purana password verify hoga
        if not current_user.password_hash.startswith("OtpUserPass_"):
            if not verify_password(payload.current_password, current_user.password_hash):
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Current password is incorrect."
                )

    try:
        validate_password_strength(payload.new_password)
    except ValueError as err:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(err)
        )

    current_user.password_hash = hash_password(payload.new_password)
    await db.commit()
    return {"message": "Password updated successfully."}

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
        "username": current_user.username,
        "full_name": current_user.full_name,
        "role": current_user.role,
        "avatar_url": current_user.avatar_url,
        "email_verified": current_user.email_verified,
        "mfa_enabled": current_user.mfa_enabled,
        "biometric_enabled": current_user.biometric_enabled,
        "has_pin": current_user.app_lock_pin_hash is not None,
        "has_passkey": current_user.passkey_credential_id is not None,
        "is_active": current_user.is_active,
        "is_verified": current_user.is_verified
    }

@router.post("/signup/customer", dependencies=[Depends(auth_rate_limiter)])
async def customer_signup(payload: CustomerSignupPayload, request: Request, db: AsyncSession = Depends(get_db)):
    service = AuthService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    return await service.register_customer_account(
        phone_number=payload.phone_number,
        email=payload.email,
        full_name=payload.full_name,
        password=payload.password,
        username=payload.username,
        ip_address=ip,
        user_agent=ua
    )

@router.post("/signup/driver", dependencies=[Depends(auth_rate_limiter)])
async def driver_signup(payload: DriverSignupPayload, request: Request, db: AsyncSession = Depends(get_db)):
    service = AuthService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    return await service.register_driver_account(
        phone_number=payload.phone_number,
        email=payload.email,
        full_name=payload.full_name,
        password=payload.password,
        username=payload.username,
        vehicle_type=payload.vehicle_type,
        vehicle_name=payload.vehicle_name,
        vehicle_no=payload.vehicle_no,
        ip_address=ip,
        user_agent=ua
    )

@router.post("/login", dependencies=[Depends(auth_rate_limiter)])
@router.post("/login/unified", dependencies=[Depends(auth_rate_limiter)])
async def unified_login(payload: UnifiedLoginPayload, request: Request, db: AsyncSession = Depends(get_db)):
    service = AuthService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    return await service.unified_login_authentication(
        login_type=payload.login_type,
        identifier=payload.identifier,
        password=payload.password,
        ip_address=ip,
        user_agent=ua
    )

class LogoutPayload(BaseModel):
    fcm_token: Optional[str] = Field(None, max_length=512)


@router.post("/logout")
async def logout(
    request: Request,
    payload: Optional[LogoutPayload] = Body(default=None),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Terminates active session, blacklists it across distributed instances, and deactivates optional device push token."""
    auth_header = request.headers.get("authorization", "")
    if auth_header.startswith("Bearer "):
        token = auth_header.split(" ")[1]
        try:
            decoded = decode_jwt_token(token)
            session_id = decoded.get("session_id")
            if session_id:
                service = AuthService(db)
                await service.revoke_session(session_id)
        except Exception:
            pass
    if payload and payload.fcm_token:
        from backend.application.services.notification_service import NotificationService
        await NotificationService(db).deactivate_token(current_user.id, payload.fcm_token)
    return {"message": "Session terminated successfully."}

@router.post("/logout-all")
async def logout_all(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Terminates all active sessions and deactivates push device tokens across all devices for the current user."""
    service = AuthService(db)
    await service.revoke_all_user_sessions(current_user.id)
    from backend.application.services.notification_service import NotificationService
    await NotificationService(db).deactivate_user_tokens(current_user.id)
    return {"message": "All active sessions revoked across all devices."}
