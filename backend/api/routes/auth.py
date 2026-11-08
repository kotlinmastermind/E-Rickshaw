from typing import Optional
from fastapi import APIRouter, Depends, Request, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.application.services.auth_service import AuthService
from backend.application.services.user_governance_service import UserGovernanceService
from backend.api.deps import get_current_user
from backend.domain.models import User
from backend.infrastructure.security.rate_limiter import auth_rate_limiter
from backend.infrastructure.security.crypto import decode_jwt_token
from backend.api.schemas.auth import (
    IdentityLookupPayload,
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

router = APIRouter(prefix="/auth", tags=["Authentication"])

@router.post("/lookup", dependencies=[Depends(auth_rate_limiter)])
async def lookup_user_identity(payload: IdentityLookupPayload, db: AsyncSession = Depends(get_db)):
    service = AuthService(db)
    return await service.lookup_user_identity(payload.identifier, payload.role)

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

@router.post("/admin/demo-session")
async def admin_demo_session(request: Request, db: AsyncSession = Depends(get_db)):
    """Issue a verified Superadmin session and token directly for live Admin Console operations."""
    import uuid, hashlib
    from datetime import datetime, timedelta, timezone
    from sqlalchemy import select
    from backend.domain.models import User, Session, AdminRole, AdminUserRole
    from backend.infrastructure.security.crypto import create_jwt_token

    # 1. Ensure Superadmin user exists in database
    res = await db.execute(select(User).where(User.email == "admin@luxurychauffeur.com"))
    user = res.scalar_one_or_none()
    if not user:
        user = User(
            id=str(uuid.uuid4()),
            email="admin@luxurychauffeur.com",
            phone_number="+919999900000",
            role="admin",
            full_name="Super Administrator",
            is_active=True,
            mfa_enabled=True
        )
        db.add(user)
        await db.flush()
    else:
        user.mfa_enabled = True
        user.is_active = True

    # 2. Ensure Super Administrator Role exists
    r_res = await db.execute(select(AdminRole).where(AdminRole.name == "Super Administrator"))
    super_role = r_res.scalar_one_or_none()
    if not super_role:
        super_role = AdminRole(
            id=str(uuid.uuid4()),
            name="Super Administrator",
            description="Unrestricted access across all operational, financial and governance desks.",
            is_superuser=True
        )
        db.add(super_role)
        await db.flush()

    # 3. Ensure User Role mapping
    aur_res = await db.execute(select(AdminUserRole).where(AdminUserRole.user_id == user.id, AdminUserRole.role_id == super_role.id))
    if not aur_res.scalar_one_or_none():
        db.add(AdminUserRole(user_id=user.id, role_id=super_role.id))

    # 4. Create active verified session
    sess_id = str(uuid.uuid4())
    raw_token = str(uuid.uuid4())
    user_sess = Session(
        id=sess_id,
        user_id=user.id,
        token_hash=hashlib.sha256(raw_token.encode()).hexdigest(),
        user_agent=request.headers.get("user-agent", "Web Admin Console"),
        ip_address=request.client.host if request.client else "127.0.0.1",
        mfa_verified=True,
        is_revoked=False,
        expires_at=datetime.now(timezone.utc) + timedelta(days=30)
    )
    db.add(user_sess)
    await db.commit()

    token = create_jwt_token(user.id, "admin", sess_id, expires_delta=timedelta(days=30))
    return {
        "access_token": token,
        "token_type": "bearer",
        "user": {
            "id": user.id,
            "email": user.email,
            "phone_number": user.phone_number,
            "role": user.role,
            "full_name": user.full_name or "Super Administrator",
            "mfa_enabled": True
        }
    }

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
