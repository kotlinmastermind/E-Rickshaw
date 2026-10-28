from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List, Optional
import os
import json
import secrets
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, delete
from fastapi import HTTPException, status, UploadFile
from backend.domain.models import (
    User, CustomerProfile, Session, TrustedDevice, UserSavedPhone,
    UserPreferences, AccountDeletionRequest, DataExportArchive,
    Ride, Payment, AdministrativeAuditLog
)
from backend.infrastructure.security.crypto import (
    hash_password, verify_password, hash_pin, verify_pin,
    create_jwt_token, decode_jwt_token, hash_secret,
    generate_secure_otp, verify_otp_constant_time, generate_mfa_secret, verify_totp
)
from backend.core.config import settings

class UserGovernanceService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # 1. Sign In / Register with Email and Password
    async def register_with_password(
        self, email: str, password: str, full_name: str, phone_number: str, role: str = "customer",
        ip_address: Optional[str] = None, user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        email = email.strip().lower()
        phone = phone_number.strip()
        if len(password) < 8:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Password must contain at least 8 characters.")

        # Check existing email
        stmt_email = select(User).where(User.email == email)
        res_email = await self.db.execute(stmt_email)
        if res_email.scalar_one_or_none():
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="An account with this email address already exists.")

        # Check existing phone
        stmt_phone = select(User).where(User.phone_number == phone)
        res_phone = await self.db.execute(stmt_phone)
        user = res_phone.scalar_one_or_none()

        if user:
            # Associate email and password to existing user
            user.email = email
            user.password_hash = hash_password(password)
            user.full_name = full_name
        else:
            user = User(
                email=email,
                phone_number=phone,
                password_hash=hash_password(password),
                full_name=full_name,
                role=role,
                is_active=True,
                is_verified=True,
                email_verified=False
            )
            self.db.add(user)
            await self.db.flush()

            if role == "customer":
                profile = CustomerProfile(user_id=user.id)
                self.db.add(profile)
            await self.db.flush()

        # Create preferences record
        await self._ensure_user_preferences(user.id)

        # Create session
        token_hash = hash_secret(f"{user.id}:{datetime.now(timezone.utc).isoformat()}:{generate_secure_otp(12)}")
        session = Session(
            user_id=user.id,
            token_hash=token_hash,
            ip_address=ip_address,
            user_agent=user_agent,
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
        )
        self.db.add(session)
        await self.db.commit()
        await self.db.refresh(user)

        access_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="access")
        refresh_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="refresh")

        return {
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            "user": {
                "id": user.id,
                "email": user.email,
                "phone_number": user.phone_number,
                "full_name": user.full_name,
                "role": user.role,
                "email_verified": user.email_verified,
                "mfa_enabled": user.mfa_enabled
            }
        }

    async def login_with_password(
        self, email: str, password: str,
        ip_address: Optional[str] = None, user_agent: Optional[str] = None,
        require_admin: bool = False,
    ) -> Dict[str, Any]:
        email = email.strip().lower()
        stmt = select(User).where(User.email == email)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()

        if not user or not user.password_hash or not verify_password(password, user.password_hash):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid executive credentials.")

        if require_admin and user.role.lower() != "admin":
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid executive credentials.")

        if not user.is_active:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="This account has been deactivated.")

        if require_admin and not user.mfa_enabled:
            return {"mfa_setup_required": True, "message": "Administrator MFA enrollment is required before console access."}

        # Check MFA requirement
        if user.mfa_enabled:
            # Issue temporary 5-min MFA token
            mfa_token = create_jwt_token(
                user_id=user.id, role=user.role, session_id="pending_mfa",
                token_type="mfa_challenge", expires_delta=timedelta(minutes=5)
            )
            return {
                "mfa_required": True,
                "mfa_token": mfa_token,
                "message": "Multi-factor authentication required. Please provide your 6-digit TOTP code."
            }

        # Create session
        token_hash = hash_secret(f"{user.id}:{datetime.now(timezone.utc).isoformat()}:{generate_secure_otp(12)}")
        session = Session(
            user_id=user.id,
            token_hash=token_hash,
            ip_address=ip_address,
            user_agent=user_agent,
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
        )
        self.db.add(session)
        await self.db.commit()

        access_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="access")
        refresh_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="refresh")

        return {
            "mfa_required": False,
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            "user": {
                "id": user.id,
                "email": user.email,
                "phone_number": user.phone_number,
                "full_name": user.full_name,
                "role": user.role,
                "email_verified": user.email_verified,
                "mfa_enabled": user.mfa_enabled
            }
        }

    async def setup_admin_mfa_by_password(self, email: str, password: str) -> Dict[str, Any]:
        normalized = email.strip().lower()
        user = (await self.db.execute(select(User).where(User.email == normalized))).scalar_one_or_none()
        if not user or user.role.lower() != "admin" or not user.password_hash or not verify_password(password, user.password_hash):
            raise HTTPException(status_code=401, detail="Invalid administrator credentials.")
        if not user.is_active:
            raise HTTPException(status_code=403, detail="This administrator account is inactive.")
        if user.mfa_enabled:
            raise HTTPException(status_code=409, detail="Administrator MFA is already enabled. Sign in with your authenticator code.")
        if not user.mfa_secret:
            user.mfa_secret = generate_mfa_secret()
            await self.db.commit()
        setup_token = create_jwt_token(user_id=user.id, role="admin", session_id="admin_mfa_setup", token_type="admin_mfa_setup", expires_delta=timedelta(minutes=10))
        uri = f"otpauth://totp/GrandChauffeur:{user.email}?secret={user.mfa_secret}&issuer=GrandChauffeur&algorithm=SHA1&digits=6&period=30"
        return {"setup_token": setup_token, "totp_uri": uri, "secret": user.mfa_secret, "expires_in": 600, "message": "Add this account to an authenticator, then verify a current six-digit code. The secret is shown once in this response."}

    async def enable_admin_mfa_with_setup_token(self, setup_token: str, code: str, ip_address: Optional[str], user_agent: Optional[str]) -> Dict[str, Any]:
        try:
            payload = decode_jwt_token(setup_token)
        except Exception:
            raise HTTPException(status_code=401, detail="Administrator MFA setup link has expired. Start again.")
        if payload.get("token_type") != "admin_mfa_setup" or payload.get("role") != "admin":
            raise HTTPException(status_code=401, detail="Invalid administrator MFA setup token.")
        user = await self.db.get(User, payload.get("sub"))
        if not user or user.role.lower() != "admin" or not user.is_active or user.mfa_enabled or not user.mfa_secret:
            raise HTTPException(status_code=409, detail="Administrator MFA setup is no longer valid.")
        if not verify_totp(user.mfa_secret, code):
            raise HTTPException(status_code=401, detail="Authenticator code was not valid. Check device time and try again.")
        user.mfa_enabled = True
        return await self._issue_admin_session(user, ip_address, user_agent)

    async def _issue_admin_session(self, user: User, ip_address: Optional[str], user_agent: Optional[str]) -> Dict[str, Any]:
        token_hash = hash_secret(f"{user.id}:{datetime.now(timezone.utc).isoformat()}:{generate_secure_otp(12)}")
        session = Session(user_id=user.id, token_hash=token_hash, ip_address=ip_address, user_agent=user_agent,
                          is_revoked=False, mfa_verified=True,
                          expires_at=datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES))
        self.db.add(session)
        await self.db.commit()
        access_token = create_jwt_token(user_id=user.id, role="admin", session_id=session.id, token_type="access")
        refresh_token = create_jwt_token(user_id=user.id, role="admin", session_id=session.id, token_type="refresh")
        return {"access_token": access_token, "refresh_token": refresh_token, "token_type": "bearer",
                "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
                "user": {"id": user.id, "email": user.email, "full_name": user.full_name, "role": user.role, "mfa_enabled": True}}

    # 2 & 3. Sign In with Google & Apple OAuth
    async def oauth_login_or_register(
        self, provider: str, identity_token: str, email: str, full_name: Optional[str] = None,
        phone_number: Optional[str] = None, role: str = "customer",
        ip_address: Optional[str] = None, user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        email = email.strip().lower()
        stmt = select(User).where(User.email == email)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()

        if not user:
            phone = phone_number.strip() if phone_number else f"+9199{secrets.token_hex(4)[:8]}"
            user = User(
                email=email,
                phone_number=phone,
                full_name=full_name or f"{provider.capitalize()} Guest",
                role=role,
                is_active=True,
                is_verified=True,
                email_verified=True
            )
            self.db.add(user)
            await self.db.flush()

            if role == "customer":
                profile = CustomerProfile(user_id=user.id)
                self.db.add(profile)
            await self.db.flush()
        else:
            user.email_verified = True
            if full_name and not user.full_name:
                user.full_name = full_name

        await self._ensure_user_preferences(user.id)

        token_hash = hash_secret(f"{user.id}:{datetime.now(timezone.utc).isoformat()}:{generate_secure_otp(12)}")
        session = Session(
            user_id=user.id,
            token_hash=token_hash,
            ip_address=ip_address,
            user_agent=user_agent,
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
        )
        self.db.add(session)
        await self.db.commit()

        access_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="access")
        refresh_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="refresh")

        return {
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            "provider": provider,
            "user": {
                "id": user.id,
                "email": user.email,
                "phone_number": user.phone_number,
                "full_name": user.full_name,
                "role": user.role,
                "email_verified": user.email_verified
            }
        }

    # 4. Passkey sign-in & registration
    async def create_passkey_challenge(self, user_id: Optional[str] = None) -> Dict[str, Any]:
        challenge = secrets.token_urlsafe(32)
        return {
            "challenge": challenge,
            "rp": {"name": "Grand Chauffeur Luxury Escort", "id": "grandchauffeur.luxury"},
            "user": {"id": user_id or "anonymous", "name": "Distinguished Member"},
            "timeout": 60000
        }

    async def register_passkey(self, user_id: str, credential_id: str, public_key: str) -> Dict[str, Any]:
        stmt = select(User).where(User.id == user_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if not user:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")

        user.passkey_credential_id = credential_id
        user.passkey_public_key = public_key
        await self.db.commit()
        return {"message": "FIDO2 / WebAuthn Passkey registered successfully."}

    async def verify_passkey_login(
        self, credential_id: str, signature: str,
        ip_address: Optional[str] = None, user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        stmt = select(User).where(User.passkey_credential_id == credential_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()

        if not user or not user.is_active:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Passkey credential verification failed.")

        token_hash = hash_secret(f"{user.id}:{datetime.now(timezone.utc).isoformat()}:{generate_secure_otp(12)}")
        session = Session(
            user_id=user.id,
            token_hash=token_hash,
            ip_address=ip_address,
            user_agent=user_agent,
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
        )
        self.db.add(session)
        await self.db.commit()

        access_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="access")
        refresh_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="refresh")

        return {
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            "user": {
                "id": user.id,
                "email": user.email,
                "phone_number": user.phone_number,
                "full_name": user.full_name,
                "role": user.role,
                "mfa_enabled": user.mfa_enabled
            }
        }

    # 5. Biometric app unlock & PIN Lock
    async def toggle_biometric(self, user_id: str, enabled: bool) -> Dict[str, Any]:
        stmt = select(User).where(User.id == user_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if not user:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")

        user.biometric_enabled = enabled
        await self.db.commit()
        return {"biometric_enabled": user.biometric_enabled}

    async def set_app_lock_pin(self, user_id: str, pin: str) -> Dict[str, Any]:
        if len(pin) != 4 or not pin.isdigit():
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="PIN must be exactly 4 digits.")
        stmt = select(User).where(User.id == user_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if not user:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")

        user.app_lock_pin_hash = hash_pin(pin)
        await self.db.commit()
        return {"message": "Security App Lock PIN configured successfully."}

    async def verify_app_lock_pin(self, user_id: str, pin: str) -> Dict[str, Any]:
        stmt = select(User).where(User.id == user_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if not user or not user.app_lock_pin_hash:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="App lock PIN is not configured.")

        if not verify_pin(pin, user.app_lock_pin_hash):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid security PIN.")

        return {"verified": True, "message": "Cockpit unlocked."}

    # 6. Multi-factor Authentication (MFA)
    async def setup_mfa(self, user_id: str) -> Dict[str, Any]:
        secret = generate_mfa_secret()
        stmt = select(User).where(User.id == user_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if not user:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")

        user.mfa_secret = secret
        await self.db.commit()

        totp_uri = f"otpauth://totp/GrandChauffeur:{user.email or user.phone_number}?secret={secret}&issuer=GrandChauffeur"
        return {
            "mfa_secret": secret,
            "totp_uri": totp_uri,
            "instructions": "Enter this secret into Google Authenticator or scan the TOTP QR."
        }

    async def enable_mfa(self, user_id: str, code: str) -> Dict[str, Any]:
        stmt = select(User).where(User.id == user_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if not user or not user.mfa_secret:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="MFA setup was not initiated.")

        # Verify against the enrolled RFC 6238 secret; a six-digit shape alone is insufficient.
        if not verify_totp(user.mfa_secret or "", code):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authenticator code was not valid. Check device time and try again.")

        user.mfa_enabled = True
        await self.db.commit()
        return {"mfa_enabled": True, "message": "Multi-factor authentication active."}

    async def disable_mfa(self, user_id: str) -> Dict[str, Any]:
        stmt = select(User).where(User.id == user_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if user:
            user.mfa_enabled = False
            user.mfa_secret = None
            await self.db.commit()
        return {"mfa_enabled": False, "message": "MFA disabled."}

    async def verify_mfa_login_challenge(
        self, mfa_token: str, code: str,
        ip_address: Optional[str] = None, user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        try:
            payload = decode_jwt_token(mfa_token)
            if payload.get("token_type") != "mfa_challenge":
                raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid MFA token.")
            user_id = payload.get("sub")
        except Exception:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Expired or invalid MFA token.")

        stmt = select(User).where(User.id == user_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if not user or not user.is_active or not user.mfa_enabled:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="MFA is not enabled for this user.")

        if not verify_totp(user.mfa_secret or "", code):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authenticator code was not valid. Check device time and try again.")

        token_hash = hash_secret(f"{user.id}:{datetime.now(timezone.utc).isoformat()}:{generate_secure_otp(12)}")
        session = Session(
            user_id=user.id,
            token_hash=token_hash,
            ip_address=ip_address,
            user_agent=user_agent,
            is_revoked=False,
            mfa_verified=True,
            expires_at=datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
        )
        self.db.add(session)
        await self.db.commit()

        access_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="access")
        refresh_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="refresh")

        return {
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            "user": {
                "id": user.id,
                "email": user.email,
                "phone_number": user.phone_number,
                "full_name": user.full_name,
                "role": user.role
            }
        }

    # 7. Trusted Device Management
    async def list_trusted_devices(self, user_id: str) -> List[Dict[str, Any]]:
        stmt = select(TrustedDevice).where(TrustedDevice.user_id == user_id, TrustedDevice.is_trusted == True)
        res = await self.db.execute(stmt)
        devices = res.scalars().all()
        return [
            {
                "id": d.id,
                "device_id": d.device_id,
                "device_name": d.device_name,
                "device_os": d.device_os,
                "is_trusted": d.is_trusted,
                "last_used_at": d.last_used_at.isoformat(),
                "created_at": d.created_at.isoformat()
            }
            for d in devices
        ]

    async def add_trusted_device(self, user_id: str, device_id: str, device_name: str, device_os: str = "Android") -> Dict[str, Any]:
        stmt = select(TrustedDevice).where(TrustedDevice.user_id == user_id, TrustedDevice.device_id == device_id)
        res = await self.db.execute(stmt)
        dev = res.scalar_one_or_none()

        now = datetime.now(timezone.utc)
        if dev:
            dev.is_trusted = True
            dev.device_name = device_name
            dev.last_used_at = now
        else:
            dev = TrustedDevice(
                user_id=user_id,
                device_id=device_id,
                device_name=device_name,
                device_os=device_os,
                is_trusted=True,
                last_used_at=now
            )
            self.db.add(dev)
        await self.db.commit()
        return {"message": f"Device '{device_name}' is now registered as trusted."}

    async def revoke_trusted_device(self, user_id: str, device_id: str) -> Dict[str, Any]:
        stmt = select(TrustedDevice).where(TrustedDevice.user_id == user_id, TrustedDevice.device_id == device_id)
        res = await self.db.execute(stmt)
        dev = res.scalar_one_or_none()
        if dev:
            dev.is_trusted = False
            await self.db.commit()
        return {"message": "Trusted device revoked."}

    # 8 & 9. Sessions and Remote Sign-out
    async def list_sessions(self, user_id: str, current_session_id: Optional[str] = None) -> List[Dict[str, Any]]:
        stmt = select(Session).where(Session.user_id == user_id, Session.is_revoked == False).order_by(Session.created_at.desc())
        res = await self.db.execute(stmt)
        sessions = res.scalars().all()
        return [
            {
                "id": s.id,
                "ip_address": s.ip_address or "127.0.0.1",
                "user_agent": s.user_agent or "Executive Client App",
                "created_at": s.created_at.isoformat(),
                "expires_at": s.expires_at.isoformat(),
                "is_current": s.id == current_session_id
            }
            for s in sessions
        ]

    async def revoke_other_sessions(self, user_id: str, current_session_id: Optional[str]) -> Dict[str, Any]:
        from backend.infrastructure.cache.redis_client import redis_client
        stmt = select(Session).where(Session.user_id == user_id, Session.is_revoked == False)
        if current_session_id:
            stmt = stmt.where(Session.id != current_session_id)
        res = await self.db.execute(stmt)
        sessions = res.scalars().all()

        for s in sessions:
            s.is_revoked = True
            await redis_client.set(f"revoked_session:{s.id}", "true", ex=86400 * 7)

        await self.db.commit()
        return {"revoked_count": len(sessions), "message": "All other sessions signed out remotely."}

    # 10. Profile Photo Upload
    async def upload_avatar(self, user_id: str, file: UploadFile) -> Dict[str, Any]:
        content = await file.read()
        if len(content) > 5 * 1024 * 1024:
            raise HTTPException(status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, detail="Profile photo exceeds 5MB limit.")

        # Magic byte check for JPEG, PNG, WEBP
        is_jpeg = content.startswith(b"\xff\xd8\xff")
        is_png = content.startswith(b"\x89PNG\r\n\x1a\n")
        is_webp = content.startswith(b"RIFF") and b"WEBP" in content[:16]
        if not (is_jpeg or is_png or is_webp):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Only JPG, PNG, or WEBP images are supported.")

        ext = "jpg" if is_jpeg else ("png" if is_png else "webp")
        filename = f"avatar_{user_id}_{int(datetime.now().timestamp())}.{ext}"

        avatar_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "static", "avatars")
        os.makedirs(avatar_dir, exist_ok=True)
        filepath = os.path.join(avatar_dir, filename)
        with open(filepath, "wb") as f:
            f.write(content)

        avatar_url = f"/static/avatars/{filename}"
        stmt = select(User).where(User.id == user_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if user:
            user.avatar_url = avatar_url
            await self.db.commit()

        return {"avatar_url": avatar_url, "message": "Executive portrait updated successfully."}

    async def delete_avatar(self, user_id: str) -> Dict[str, Any]:
        stmt = select(User).where(User.id == user_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if user:
            user.avatar_url = None
            await self.db.commit()
        return {"message": "Profile photo removed successfully.", "avatar_url": None}

    # 11. Multiple Saved Phone Numbers
    async def list_saved_phones(self, user_id: str) -> List[Dict[str, Any]]:
        stmt = select(UserSavedPhone).where(UserSavedPhone.user_id == user_id).order_by(UserSavedPhone.created_at.asc())
        res = await self.db.execute(stmt)
        phones = res.scalars().all()
        return [
            {
                "id": p.id,
                "phone_number": p.phone_number,
                "label": p.label,
                "is_primary": p.is_primary,
                "is_verified": p.is_verified,
                "created_at": p.created_at.isoformat()
            }
            for p in phones
        ]

    async def add_saved_phone(self, user_id: str, phone_number: str, label: str = "Personal") -> Dict[str, Any]:
        phone_number = phone_number.strip()
        if len(phone_number) < 10:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid telephone format.")

        saved_phone = UserSavedPhone(
            user_id=user_id,
            phone_number=phone_number,
            label=label,
            is_primary=False,
            is_verified=True
        )
        self.db.add(saved_phone)
        await self.db.commit()
        return {"id": saved_phone.id, "phone_number": phone_number, "label": label}

    async def delete_saved_phone(self, user_id: str, phone_id: str) -> Dict[str, Any]:
        stmt = select(UserSavedPhone).where(UserSavedPhone.id == phone_id, UserSavedPhone.user_id == user_id)
        res = await self.db.execute(stmt)
        p = res.scalar_one_or_none()
        if p:
            await self.db.delete(p)
            await self.db.commit()
        return {"message": "Saved phone number removed."}

    # 12. Verified Email Address
    async def send_email_verification(self, user_id: str) -> Dict[str, Any]:
        stmt = select(User).where(User.id == user_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if not user or not user.email:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No email address associated with your account.")

        from backend.infrastructure.cache.redis_client import redis_client
        code = generate_secure_otp(6)
        await redis_client.set(f"email_verify:{user.id}", code, ex=900)  # 15 minutes

        return {
            "email_masked": f"{user.email[:3]}***@{user.email.split('@')[-1]}",
            "message": "Verification passcode dispatched to your email address.",
            "debug_code": code if settings.DEBUG else None
        }

    async def verify_email_code(self, user_id: str, code: str) -> Dict[str, Any]:
        from backend.infrastructure.cache.redis_client import redis_client
        stored_code = await redis_client.get(f"email_verify:{user_id}")
        if not stored_code or not verify_otp_constant_time(code.strip(), stored_code):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired email verification passcode.")

        stmt = select(User).where(User.id == user_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if user:
            user.email_verified = True
            await self.db.commit()
        await redis_client.delete(f"email_verify:{user_id}")
        return {"email_verified": True, "message": "Email address verified successfully."}

    # 13, 14, 15, 17, 18, 22, 23. User Preferences (Language, Formatting, Accessibility, Privacy, Marketing)
    async def get_user_preferences(self, user_id: str) -> Dict[str, Any]:
        pref = await self._ensure_user_preferences(user_id)
        return {
            "preferred_language": pref.preferred_language,
            "currency_code": pref.currency_code,
            "number_format": pref.number_format,
            "text_scale": pref.text_scale,
            "high_contrast": pref.high_contrast,
            "reduced_motion": pref.reduced_motion,
            "analytics_consent": pref.analytics_consent,
            "crash_reports_consent": pref.crash_reports_consent,
            "background_location_consent": pref.background_location_consent,
            "third_party_sharing_consent": pref.third_party_sharing_consent,
            "marketing_push_opt_in": pref.marketing_push_opt_in,
            "marketing_email_opt_in": pref.marketing_email_opt_in,
            "marketing_sms_opt_in": pref.marketing_sms_opt_in
        }

    async def update_user_preferences(self, user_id: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        pref = await self._ensure_user_preferences(user_id)
        for key, val in payload.items():
            if hasattr(pref, key) and val is not None:
                setattr(pref, key, val)
        await self.db.commit()
        return await self.get_user_preferences(user_id)

    async def _ensure_user_preferences(self, user_id: str) -> UserPreferences:
        stmt = select(UserPreferences).where(UserPreferences.user_id == user_id)
        res = await self.db.execute(stmt)
        pref = res.scalar_one_or_none()
        if not pref:
            pref = UserPreferences(user_id=user_id)
            self.db.add(pref)
            await self.db.commit()
            await self.db.refresh(pref)
        return pref

    # 20. Account Deletion Progress Tracking
    async def request_account_deletion(self, user_id: str, reason: Optional[str] = None) -> Dict[str, Any]:
        now = datetime.now(timezone.utc)
        scheduled_purge = now + timedelta(days=30)
        req = AccountDeletionRequest(
            user_id=user_id,
            status="PENDING_GRACE_PERIOD",
            cooldown_days_total=30,
            requested_at=now,
            scheduled_purge_at=scheduled_purge,
            cancellation_reason=reason
        )
        self.db.add(req)
        await self.db.commit()
        return {
            "status": "PENDING_GRACE_PERIOD",
            "requested_at": now.isoformat(),
            "scheduled_purge_at": scheduled_purge.isoformat(),
            "days_remaining": 30,
            "message": "Account deletion initiated. Your profile is in a 30-day grace period."
        }

    async def get_deletion_status(self, user_id: str) -> Dict[str, Any]:
        stmt = select(AccountDeletionRequest).where(
            AccountDeletionRequest.user_id == user_id
        ).order_by(AccountDeletionRequest.requested_at.desc())
        res = await self.db.execute(stmt)
        req = res.scalars().first()
        if not req or req.status != "PENDING_GRACE_PERIOD":
            return {"has_active_request": False, "status": "ACTIVE"}

        now = datetime.now(timezone.utc)
        purge_dt = req.scheduled_purge_at
        if purge_dt.tzinfo is None:
            purge_dt = purge_dt.replace(tzinfo=timezone.utc)
        diff = purge_dt - now
        days_rem = max(0, diff.days)

        return {
            "has_active_request": True,
            "request_id": req.id,
            "status": req.status,
            "requested_at": req.requested_at.isoformat(),
            "scheduled_purge_at": req.scheduled_purge_at.isoformat(),
            "days_remaining": days_rem,
            "cooldown_days_total": req.cooldown_days_total
        }

    async def cancel_deletion_request(self, user_id: str) -> Dict[str, Any]:
        stmt = select(AccountDeletionRequest).where(
            AccountDeletionRequest.user_id == user_id,
            AccountDeletionRequest.status == "PENDING_GRACE_PERIOD"
        )
        res = await self.db.execute(stmt)
        req = res.scalars().first()
        if not req:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No active deletion request to cancel.")

        req.status = "CANCELLED"
        req.cancelled_at = datetime.now(timezone.utc)
        await self.db.commit()
        return {"status": "CANCELLED", "message": "Account deletion request successfully cancelled. Your VIP membership remains active."}

    # 21. Download Personal Data Archive
    async def request_data_export(self, user_id: str) -> Dict[str, Any]:
        stmt_u = select(User).where(User.id == user_id)
        res_u = await self.db.execute(stmt_u)
        user = res_u.scalar_one_or_none()
        if not user:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")

        # Fetch profile, rides, payments, sessions
        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == user_id)
        cust_profile = (await self.db.execute(stmt_cust)).scalar_one_or_none()

        stmt_rides = select(Ride).where(Ride.customer_id == (cust_profile.id if cust_profile else ""))
        rides = (await self.db.execute(stmt_rides)).scalars().all()

        stmt_sessions = select(Session).where(Session.user_id == user_id)
        sessions = (await self.db.execute(stmt_sessions)).scalars().all()

        export_dict = {
            "export_metadata": {
                "generated_at": datetime.now(timezone.utc).isoformat(),
                "compliance_standard": "GDPR_DPDP_RIGHT_TO_PORTABILITY",
                "platform": "Grand Chauffeur Luxury Escort"
            },
            "user_identity": {
                "id": user.id,
                "phone_number": user.phone_number,
                "email": user.email,
                "full_name": user.full_name,
                "created_at": user.created_at.isoformat()
            },
            "membership_profile": {
                "tier": cust_profile.tier if cust_profile else "ROYAL_SOVEREIGN",
                "rating": cust_profile.rating_average if cust_profile else 5.0,
                "temperature_preference": cust_profile.temperature_celsius if cust_profile else 21.0,
                "quiet_ride": cust_profile.quiet_ride if cust_profile else True,
                "preferred_music": cust_profile.preferred_music if cust_profile else "Classical Symphony",
                "preferred_water": cust_profile.preferred_water if cust_profile else "Sparkling Mineral"
            },
            "journey_history": [
                {
                    "ride_id": r.id,
                    "status": r.status,
                    "pickup": r.pickup_address,
                    "destination": r.destination_address,
                    "fare_cents": r.final_fare_cents or r.estimated_fare_cents,
                    "requested_at": r.requested_at.isoformat()
                }
                for r in rides
            ],
            "security_sessions_log": [
                {
                    "session_id": s.id,
                    "ip_address": s.ip_address,
                    "user_agent": s.user_agent,
                    "created_at": s.created_at.isoformat()
                }
                for s in sessions
            ]
        }

        export_json = json.dumps(export_dict, indent=2)
        archive_id = secrets.token_hex(8)
        archive = DataExportArchive(
            user_id=user_id,
            status="READY",
            archive_format="JSON",
            file_size_bytes=len(export_json.encode("utf-8")),
            download_url=f"/api/v1/user/data-export/download/{archive_id}",
            payload_json=export_json,
            expires_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        self.db.add(archive)
        await self.db.commit()

        return {
            "status": "READY",
            "archive_id": archive.id,
            "file_size_bytes": archive.file_size_bytes,
            "download_url": archive.download_url,
            "expires_at": archive.expires_at.isoformat(),
            "message": "Personal data archive generated in compliance with GDPR & DPDP."
        }

    async def get_latest_data_archive(self, user_id: str) -> Dict[str, Any]:
        stmt = select(DataExportArchive).where(
            DataExportArchive.user_id == user_id
        ).order_by(DataExportArchive.created_at.desc())
        res = await self.db.execute(stmt)
        archive = res.scalars().first()
        if not archive:
            return {"has_archive": False}
        return {
            "has_archive": True,
            "id": archive.id,
            "status": archive.status,
            "file_size_bytes": archive.file_size_bytes,
            "download_url": archive.download_url,
            "expires_at": archive.expires_at.isoformat()
        }

    # 24. Account Recovery Workflow
    async def request_account_recovery(self, identifier: str) -> Dict[str, Any]:
        ident = identifier.strip().lower()
        stmt = select(User).where((User.email == ident) | (User.phone_number == ident))
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if not user:
            # Safe boundary - don't leak user enumeration
            return {"message": "If an account exists with this credential, a recovery token has been dispatched."}

        recovery_code = generate_secure_otp(6)
        from backend.infrastructure.cache.redis_client import redis_client
        await redis_client.set(f"pwd_reset:{user.id}", recovery_code, ex=900)  # 15 minutes

        return {
            "message": "If an account exists with this credential, a recovery token has been dispatched.",
            "user_id": user.id,
            "debug_token": recovery_code if settings.DEBUG else None
        }

    async def reset_password_with_token(self, user_id: str, recovery_code: str, new_password: str) -> Dict[str, Any]:
        if len(new_password) < 8:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="New password must be at least 8 characters.")

        from backend.infrastructure.cache.redis_client import redis_client
        stored_code = await redis_client.get(f"pwd_reset:{user_id}")
        if not stored_code or not verify_otp_constant_time(recovery_code.strip(), stored_code):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired recovery code.")

        stmt = select(User).where(User.id == user_id)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()
        if not user:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")

        user.password_hash = hash_password(new_password)
        await self.db.commit()
        await redis_client.delete(f"pwd_reset:{user_id}")
        return {"message": "Password reset successfully. You may now sign in with your new password."}

    # 25. Customer Membership Profile and Preferences
    async def get_membership_details(self, user_id: str) -> Dict[str, Any]:
        stmt = select(CustomerProfile).where(CustomerProfile.user_id == user_id)
        res = await self.db.execute(stmt)
        profile = res.scalar_one_or_none()
        if not profile:
            profile = CustomerProfile(user_id=user_id)
            self.db.add(profile)
            await self.db.commit()
            await self.db.refresh(profile)

        return {
            "tier": profile.tier,
            "temperature_celsius": profile.temperature_celsius,
            "quiet_ride": profile.quiet_ride,
            "preferred_music": profile.preferred_music,
            "preferred_water": profile.preferred_water,
            "luggage_assistance": profile.luggage_assistance,
            "rating_average": profile.rating_average,
            "rating_count": profile.rating_count,
            "referral_code": profile.referral_code
        }

    async def update_membership_preferences(self, user_id: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        stmt = select(CustomerProfile).where(CustomerProfile.user_id == user_id)
        res = await self.db.execute(stmt)
        profile = res.scalar_one_or_none()
        if not profile:
            profile = CustomerProfile(user_id=user_id)
            self.db.add(profile)

        if "temperature_celsius" in payload and payload["temperature_celsius"] is not None:
            profile.temperature_celsius = float(payload["temperature_celsius"])
        if "quiet_ride" in payload and payload["quiet_ride"] is not None:
            profile.quiet_ride = bool(payload["quiet_ride"])
        if "preferred_music" in payload and payload["preferred_music"] is not None:
            profile.preferred_music = str(payload["preferred_music"])
        if "preferred_water" in payload and payload["preferred_water"] is not None:
            profile.preferred_water = str(payload["preferred_water"])
        if "luggage_assistance" in payload and payload["luggage_assistance"] is not None:
            profile.luggage_assistance = bool(payload["luggage_assistance"])
        if "tier" in payload and payload["tier"] is not None:
            profile.tier = str(payload["tier"])

        await self.db.commit()
        return await self.get_membership_details(user_id)
