from typing import Optional
from fastapi import APIRouter, Depends, Request, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.application.services.auth_service import AuthService
from backend.application.services.user_governance_service import UserGovernanceService
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

class PasswordRegisterPayload(BaseModel):
    email: str = Field(..., json_schema_extra={"example": "guest@luxury.com"})
    password: str = Field(..., min_length=8)
    full_name: str = Field(..., min_length=2)
    phone_number: str = Field(..., min_length=10)
    role: str = "customer"

class PasswordLoginPayload(BaseModel):
    email: str = Field(..., json_schema_extra={"example": "guest@luxury.com"})
    password: str

class AdminMfaEnablePayload(BaseModel):
    setup_token: str = Field(..., min_length=20)
    code: str = Field(..., min_length=6, max_length=6, pattern="^[0-9]{6}$")

class OAuthPayload(BaseModel):
    provider: Optional[str] = "google"
    identity_token: Optional[str] = None
    id_token: Optional[str] = None
    apple_user_id: Optional[str] = None
    email: Optional[str] = "guest@grandchauffeur.com"
    full_name: Optional[str] = None
    phone_number: Optional[str] = None
    role: str = "customer"

    @property
    def token(self) -> str:
        return self.identity_token or self.id_token or "simulated_token"

class PasskeyLoginPayload(BaseModel):
    credential_id: Optional[str] = None
    credentialId: Optional[str] = None
    signature: Optional[str] = None
    email: Optional[str] = None

class MfaChallengeVerifyPayload(BaseModel):
    mfa_token: Optional[str] = None
    challenge_token: Optional[str] = None
    code: Optional[str] = None
    totp_code: Optional[str] = None

class RecoveryRequestPayload(BaseModel):
    identifier: Optional[str] = None
    email: Optional[str] = None

class PasswordResetPayload(BaseModel):
    user_id: Optional[str] = None
    recovery_code: Optional[str] = None
    recovery_token: Optional[str] = None
    new_password: str = Field(..., min_length=6)

class EmailVerifyPayload(BaseModel):
    code: str = Field(..., min_length=6, max_length=6)

class CustomerSignupPayload(BaseModel):
    phone_number: str = Field(..., min_length=10)
    email: str = Field(..., json_schema_extra={"example": "customer@luxury.com"})
    full_name: str = Field(..., min_length=2)
    password: str = Field(..., min_length=6)
    username: str = Field(..., min_length=3)
    otp_code: str = Field(..., min_length=4, max_length=6)

class DriverSignupPayload(BaseModel):
    phone_number: str = Field(..., min_length=10)
    email: str = Field(..., json_schema_extra={"example": "driver@luxury.com"})
    full_name: str = Field(..., min_length=2)
    password: str = Field(..., min_length=6)
    username: str = Field(..., min_length=3)
    otp_code: str = Field(..., min_length=4, max_length=6)
    vehicle_type: str = Field(default="SEDAN")
    vehicle_name: str = Field(..., min_length=2, json_schema_extra={"example": "Mercedes-Benz E-Class"})
    vehicle_no: str = Field(..., min_length=5, json_schema_extra={"example": "MH01-LX-0001"})

class UnifiedLoginPayload(BaseModel):
    login_type: str = Field(default="PASSWORD")
    identifier: str = Field(..., min_length=3)
    password: Optional[str] = None
    otp_code: Optional[str] = None

class ForgotPasswordOtpPayload(BaseModel):
    identifier: str = Field(..., min_length=3)

class ForgotPasswordResetPayload(BaseModel):
    identifier: str = Field(..., min_length=3)
    otp_code: str = Field(..., min_length=4, max_length=6)
    new_password: str = Field(..., min_length=6)

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

# 2 & 3. Sign in with Google / Apple
@router.post("/oauth/google", dependencies=[Depends(auth_rate_limiter)])
async def oauth_google(payload: OAuthPayload, request: Request, db: AsyncSession = Depends(get_db)):
    service = UserGovernanceService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    return await service.oauth_login_or_register(
        provider="google",
        identity_token=payload.token,
        email=payload.email,
        full_name=payload.full_name,
        phone_number=payload.phone_number,
        role=payload.role,
        ip_address=ip,
        user_agent=ua
    )

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

# 4. Passkey Sign-In
@router.get("/passkey/login-challenge")
async def passkey_login_challenge(db: AsyncSession = Depends(get_db)):
    service = UserGovernanceService(db)
    return await service.create_passkey_challenge()

@router.post("/passkey/login-verify", dependencies=[Depends(auth_rate_limiter)])
async def passkey_login_verify(payload: PasskeyLoginPayload, request: Request, db: AsyncSession = Depends(get_db)):
    service = UserGovernanceService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    cid = payload.credential_id or payload.credentialId or "default_cred"
    sig = payload.signature or "default_sig"
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
        otp_code=payload.otp_code,
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
        otp_code=payload.otp_code,
        vehicle_type=payload.vehicle_type,
        vehicle_name=payload.vehicle_name,
        vehicle_no=payload.vehicle_no,
        ip_address=ip,
        user_agent=ua
    )

@router.post("/login/unified", dependencies=[Depends(auth_rate_limiter)])
async def unified_login(payload: UnifiedLoginPayload, request: Request, db: AsyncSession = Depends(get_db)):
    service = AuthService(db)
    ip = request.client.host if request.client else None
    ua = request.headers.get("user-agent")
    return await service.unified_login_authentication(
        login_type=payload.login_type,
        identifier=payload.identifier,
        password=payload.password,
        otp_code=payload.otp_code,
        ip_address=ip,
        user_agent=ua
    )

@router.post("/forgot-password/request-otp", dependencies=[Depends(auth_rate_limiter)])
async def request_forgot_password_otp(payload: ForgotPasswordOtpPayload, db: AsyncSession = Depends(get_db)):
    service = AuthService(db)
    return await service.request_forgot_password_passcode(payload.identifier)

@router.post("/forgot-password/reset", dependencies=[Depends(auth_rate_limiter)])
async def reset_forgot_password(payload: ForgotPasswordResetPayload, db: AsyncSession = Depends(get_db)):
    service = AuthService(db)
    return await service.reset_forgot_password_passcode(
        identifier=payload.identifier,
        otp_code=payload.otp_code,
        new_password=payload.new_password
    )

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
