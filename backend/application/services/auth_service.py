from datetime import datetime, timezone, timedelta
from typing import Dict, Any, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update
from fastapi import HTTPException, status
from backend.domain.models import User, CustomerProfile, DriverProfile, Session
from backend.infrastructure.security.crypto import (
    generate_secure_otp, hash_secret, create_jwt_token, verify_otp_constant_time
)
from backend.integrations.sms.provider import get_sms_provider
from backend.core.config import settings

class OtpTracker:
    def __init__(self):
        # phone_number -> {"otp": str, "expires_at": datetime, "attempts": int, "last_sent_at": datetime}
        self._cache: Dict[str, Dict[str, Any]] = {}

    def record_request(self, phone: str, otp: str):
        now = datetime.now(timezone.utc)
        record = self._cache.get(phone)
        if record:
            time_since_last = (now - record["last_sent_at"]).total_seconds()
            if time_since_last < settings.OTP_RESEND_COOLDOWN_SECONDS:
                raise HTTPException(
                    status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                    detail=f"Please wait {int(settings.OTP_RESEND_COOLDOWN_SECONDS - time_since_last)} seconds before requesting a new passcode."
                )
        self._cache[phone] = {
            "otp": otp,
            "expires_at": now + timedelta(seconds=settings.OTP_EXPIRE_SECONDS),
            "attempts": 0,
            "last_sent_at": now
        }

    def verify_attempt(self, phone: str, provided_otp: str) -> bool:
        now = datetime.now(timezone.utc)
        record = self._cache.get(phone)
        if not record:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No active passcode found for this phone number. Please request one.")
        if now > record["expires_at"]:
            del self._cache[phone]
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Passcode has expired. Please request a new one.")
        if record["attempts"] >= settings.OTP_MAX_ATTEMPTS:
            del self._cache[phone]
            raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="Too many invalid attempts. Passcode invalidated for security.")
        
        record["attempts"] += 1
        is_correct = verify_otp_constant_time(provided_otp, record["otp"])
        if is_correct:
            del self._cache[phone]
            return True
        return False

_otp_tracker = OtpTracker()

class AuthService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.sms_provider = get_sms_provider()

    async def request_otp(self, phone_number: str) -> Dict[str, Any]:
        phone = phone_number.strip()
        if len(phone) < 10:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid telephone format.")
        
        otp = generate_secure_otp(6)
        _otp_tracker.record_request(phone, otp)
        await self.sms_provider.send_otp(phone, otp)
        
        resp = {
            "message": "Passcode dispatched successfully.",
            "phone_masked": f"{phone[-4:].rjust(len(phone), '*')}",
            "expires_in_seconds": settings.OTP_EXPIRE_SECONDS
        }
        if settings.DEBUG and settings.SMS_PROVIDER == "mock":
            resp["debug_otp"] = otp
        return resp

    async def verify_otp(
        self, phone_number: str, otp_code: str, role: str = "customer",
        ip_address: Optional[str] = None, user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        phone = phone_number.strip()
        _otp_tracker.verify_attempt(phone, otp_code)

        # Lookup or create User
        stmt = select(User).where(User.phone_number == phone)
        res = await self.db.execute(stmt)
        user = res.scalar_one_or_none()

        if not user:
            user = User(
                phone_number=phone,
                role=role,
                is_active=True,
                is_verified=True
            )
            self.db.add(user)
            await self.db.flush()

            if role == "customer":
                profile = CustomerProfile(user_id=user.id)
                self.db.add(profile)
            elif role == "driver":
                profile = DriverProfile(
                    user_id=user.id,
                    license_number=f"DL-{user.id[:8].upper()}",
                    approval_status="PENDING"
                )
                self.db.add(profile)
            await self.db.flush()

        # Create Session record
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
                "phone_number": user.phone_number,
                "full_name": user.full_name,
                "role": user.role,
                "is_verified": user.is_verified
            }
        }

    async def refresh_token_rotation(self, refresh_token_str: str) -> Dict[str, Any]:
        """Validates refresh token, rotates tokens, and invalidates old credentials if compromised."""
        from backend.infrastructure.security.crypto import decode_jwt_token
        from backend.infrastructure.cache.redis_client import redis_client

        try:
            payload = decode_jwt_token(refresh_token_str)
        except Exception:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid, expired, or tampered refresh token."
            )

        if payload.get("token_type") != "refresh":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Provided token is not a refresh token."
            )

        user_id = payload.get("sub")
        session_id = payload.get("session_id")

        if await redis_client.exists(f"revoked_session:{session_id}") or await redis_client.exists(f"revoked_user:{user_id}"):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Session has been revoked.")

        stmt = select(Session).where(Session.id == session_id, Session.is_revoked == False)
        res = await self.db.execute(stmt)
        session = res.scalar_one_or_none()
        if not session:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Session is no longer valid.")

        user_stmt = select(User).where(User.id == user_id, User.is_active == True)
        user_res = await self.db.execute(user_stmt)
        user = user_res.scalar_one_or_none()
        if not user:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User account is deactivated.")

        # Rotate tokens: create fresh access & refresh tokens
        new_access_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="access")
        new_refresh_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="refresh")

        return {
            "access_token": new_access_token,
            "refresh_token": new_refresh_token,
            "token_type": "bearer",
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60
        }

    async def revoke_session(self, session_id: str):
        from backend.infrastructure.cache.redis_client import redis_client
        stmt = update(Session).where(Session.id == session_id).values(is_revoked=True)
        await self.db.execute(stmt)
        await self.db.commit()
        # Revoke immediately in distributed cache (7 day TTL matching max refresh lifetime)
        await redis_client.set(f"revoked_session:{session_id}", "true", ex=86400 * 7)

    async def revoke_all_user_sessions(self, user_id: str):
        from backend.infrastructure.cache.redis_client import redis_client
        stmt = update(Session).where(Session.user_id == user_id).values(is_revoked=True)
        await self.db.execute(stmt)
        await self.db.commit()
        # Blacklist user in distributed cache
        await redis_client.set(f"revoked_user:{user_id}", "true", ex=86400 * 7)
