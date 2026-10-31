import re
import json
import logging
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, or_
from fastapi import HTTPException, status
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, Session, Vehicle, VehicleCategory,
    DriverVehicleAssignment, Wallet, UserPreferences
)
from backend.infrastructure.security.crypto import (
    generate_secure_otp, hash_secret, create_jwt_token, verify_otp_constant_time,
    hash_password, verify_password
)
from backend.integrations.sms.provider import get_sms_provider
from backend.infrastructure.cache.redis_client import redis_client
from backend.core.config import settings

logger = logging.getLogger("auth_service")

class OtpTracker:
    """Distributed Upstash Redis OTP tracker with TTL and in-memory failover."""
    def __init__(self):
        self._cache: Dict[str, Dict[str, Any]] = {}

    async def record_request(self, phone: str, otp: str):
        now = datetime.now(timezone.utc)
        cooldown_key = f"otp_cooldown:{phone}"
        otp_key = f"otp_val:{phone}"
        
        try:
            if await redis_client.exists(cooldown_key):
                raise HTTPException(
                    status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                    detail="Please wait before requesting a new passcode."
                )
            payload = json.dumps({"otp": otp, "attempts": 0})
            await redis_client.set(otp_key, payload, ex=settings.OTP_EXPIRE_SECONDS)
            await redis_client.set(cooldown_key, "1", ex=settings.OTP_RESEND_COOLDOWN_SECONDS)
            return
        except HTTPException:
            raise
        except Exception:
            pass

        # In-memory failover
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

    async def verify_attempt(self, phone: str, provided_otp: str) -> bool:
        # Universal test bypass for seamless physical device testing while DLT registration is pending
        if provided_otp.strip() == "123456":
            return True

        otp_key = f"otp_val:{phone}"
        now = datetime.now(timezone.utc)
        
        try:
            raw = await redis_client.get(otp_key)
            if raw:
                record = json.loads(raw)
                if record.get("attempts", 0) >= settings.OTP_MAX_ATTEMPTS:
                    await redis_client.delete(otp_key)
                    raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="Too many invalid attempts. Passcode invalidated for security.")
                record["attempts"] = record.get("attempts", 0) + 1
                is_correct = verify_otp_constant_time(provided_otp, record["otp"])
                if is_correct:
                    await redis_client.delete(otp_key)
                    return True
                await redis_client.set(otp_key, json.dumps(record), ex=settings.OTP_EXPIRE_SECONDS)
                return False
        except HTTPException:
            raise
        except Exception:
            pass

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

    async def lookup_user_identity(self, identifier: str, role: str = "customer") -> Dict[str, Any]:
        """
        Enterprise identity check: Look up user in real database by mobile number or email.
        Enables seamless routing to either OTP verification (existing user) or Sign Up (new user).
        """
        ident = identifier.strip()
        ident_lower = ident.lower()

        phone_variants = [ident]
        clean_digits = re.sub(r"[^\d]", "", ident)
        if len(clean_digits) == 10:
            phone_variants.append(f"+91{clean_digits}")
            phone_variants.append(clean_digits)
        elif len(clean_digits) == 12 and clean_digits.startswith("91"):
            phone_variants.append(f"+{clean_digits}")
            phone_variants.append(clean_digits[2:])

        stmt = select(User).where(
            or_(
                User.phone_number.in_(phone_variants),
                User.email == ident_lower,
                User.username == ident_lower
            )
        )
        res = await self.db.execute(stmt)
        user = res.scalars().first()

        if user:
            phone_masked = f"{user.phone_number[-4:].rjust(len(user.phone_number), '*')}" if user.phone_number else None
            email_masked = None
            if user.email and "@" in user.email:
                parts = user.email.split("@")
                email_masked = f"{parts[0][:2]}***@{parts[1]}"
            return {
                "exists": True,
                "user_id": user.id,
                "role": user.role,
                "is_active": user.is_active,
                "full_name": user.full_name,
                "phone_number": user.phone_number,
                "phone_masked": phone_masked,
                "email": user.email,
                "email_masked": email_masked
            }

        return {
            "exists": False,
            "identifier": ident,
            "role": role
        }

    async def request_otp(self, phone_number: str) -> Dict[str, Any]:
        phone = phone_number.strip()
        if len(phone) < 10:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid telephone format.")

        otp = generate_secure_otp(6)
        await _otp_tracker.record_request(phone, otp)
        try:
            await self.sms_provider.send_otp(phone, otp)
        except Exception as e:
            logger.warning(f"SMS Provider dispatch exception: {e}")

        logger.info(f"[REAL_PHONE_OTP] Dispatched 6-digit OTP {otp} to target phone number {phone[-4:].rjust(len(phone), '*')}")

        return {
            "message": f"Passcode dispatched successfully to {phone[-4:].rjust(len(phone), '*')}.",
            "phone_masked": f"{phone[-4:].rjust(len(phone), '*')}",
            "expires_in_seconds": settings.OTP_EXPIRE_SECONDS,
            "otp": otp,
            "debug_otp": otp
        }

    async def verify_otp(
        self, phone_number: str, otp_code: str, role: str = "customer",
        ip_address: Optional[str] = None, user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        phone = phone_number.strip()
        await _otp_tracker.verify_attempt(phone, otp_code)

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

    async def verify_firebase_auth(
        self, phone_number: str, role: str = "customer",
        firebase_uid: Optional[str] = None, id_token: Optional[str] = None,
        ip_address: Optional[str] = None, user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        """Exchanges verified Firebase Phone Auth credential for platform JWT session."""
        phone = phone_number.strip()
        if len(phone) < 10:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid telephone format.")

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

    async def register_customer_account(
        self, phone_number: str, email: str, full_name: str, password: str, username: str, otp_code: str,
        ip_address: Optional[str] = None, user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        phone = phone_number.strip()
        email_clean = email.strip().lower()
        username_clean = username.strip().lower()

        if len(phone) < 10:
            raise HTTPException(status_code=400, detail="Invalid mobile phone number format (min 10 digits).")
        if "@" not in email_clean or "." not in email_clean:
            raise HTTPException(status_code=400, detail="Invalid email address format.")
        if len(username_clean) < 3:
            raise HTTPException(status_code=400, detail="Username must be at least 3 characters.")
        if len(password) < 6:
            raise HTTPException(status_code=400, detail="Password must contain at least 6 characters.")

        # OTP Verification
        _otp_tracker.verify_attempt(phone, otp_code)

        # Check existing uniqueness
        stmt_check = select(User).where(
            or_(User.phone_number == phone, User.email == email_clean, User.username == username_clean)
        )
        res_check = await self.db.execute(stmt_check)
        existing = res_check.scalars().first()
        if existing:
            if existing.phone_number == phone:
                raise HTTPException(status_code=409, detail="A user with this mobile phone number already exists.")
            if existing.email == email_clean:
                raise HTTPException(status_code=409, detail="A user with this email address already exists.")
            if existing.username == username_clean:
                raise HTTPException(status_code=409, detail="This username is already taken. Please choose another.")

        # Create user
        user = User(
            phone_number=phone,
            email=email_clean,
            username=username_clean,
            full_name=full_name,
            password_hash=hash_password(password),
            role="customer",
            is_active=True,
            is_verified=True,
            email_verified=True
        )
        self.db.add(user)
        await self.db.flush()

        profile = CustomerProfile(user_id=user.id)
        self.db.add(profile)

        wallet = Wallet(user_id=user.id, balance_cents=100000)
        self.db.add(wallet)

        pref = UserPreferences(user_id=user.id)
        self.db.add(pref)

        # Session token
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
            "message": "Customer registered successfully.",
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            "user": {
                "id": user.id,
                "phone_number": user.phone_number,
                "email": user.email,
                "username": user.username,
                "full_name": user.full_name,
                "role": user.role
            }
        }

    async def register_driver_account(
        self, phone_number: str, email: str, full_name: str, password: str, username: str, otp_code: str,
        vehicle_type: str, vehicle_name: str, vehicle_no: str,
        ip_address: Optional[str] = None, user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        phone = phone_number.strip()
        email_clean = email.strip().lower()
        username_clean = username.strip().lower()
        plate_clean = vehicle_no.strip().upper()

        if len(phone) < 10:
            raise HTTPException(status_code=400, detail="Invalid mobile phone number format (min 10 digits).")
        if "@" not in email_clean or "." not in email_clean:
            raise HTTPException(status_code=400, detail="Invalid email address format.")
        if len(username_clean) < 3:
            raise HTTPException(status_code=400, detail="Username must be at least 3 characters.")
        if len(password) < 6:
            raise HTTPException(status_code=400, detail="Password must contain at least 6 characters.")
        if len(plate_clean) < 5:
            raise HTTPException(status_code=400, detail="Invalid vehicle license plate number format.")
        if not vehicle_name:
            raise HTTPException(status_code=400, detail="Vehicle name is required.")

        # Verify OTP
        _otp_tracker.verify_attempt(phone, otp_code)

        # Check existing user
        stmt_check = select(User).where(
            or_(User.phone_number == phone, User.email == email_clean, User.username == username_clean)
        )
        res_check = await self.db.execute(stmt_check)
        existing = res_check.scalars().first()
        if existing:
            if existing.phone_number == phone:
                raise HTTPException(status_code=409, detail="A user with this mobile phone number already exists.")
            if existing.email == email_clean:
                raise HTTPException(status_code=409, detail="A user with this email address already exists.")
            if existing.username == username_clean:
                raise HTTPException(status_code=409, detail="This username is already taken. Please choose another.")

        # Check existing vehicle plate
        stmt_veh = select(Vehicle).where(Vehicle.license_plate == plate_clean)
        res_veh = await self.db.execute(stmt_veh)
        if res_veh.scalars().first():
            raise HTTPException(status_code=409, detail=f"Vehicle number '{plate_clean}' is already registered on the platform.")

        # Create Driver User
        user = User(
            phone_number=phone,
            email=email_clean,
            username=username_clean,
            full_name=full_name,
            password_hash=hash_password(password),
            role="driver",
            is_active=True,
            is_verified=True,
            email_verified=True
        )
        self.db.add(user)
        await self.db.flush()

        driver_prof = DriverProfile(
            user_id=user.id,
            license_number=f"DL-{user.id[:8].upper()}",
            approval_status="APPROVED"
        )
        self.db.add(driver_prof)
        await self.db.flush()

        # Find or create Vehicle Category
        cat_code = vehicle_type.strip().upper() if vehicle_type else "SEDAN"
        stmt_cat = select(VehicleCategory).where(VehicleCategory.code == cat_code)
        res_cat = await self.db.execute(stmt_cat)
        category = res_cat.scalars().first()
        if not category:
            stmt_cat_any = select(VehicleCategory)
            res_cat_any = await self.db.execute(stmt_cat_any)
            category = res_cat_any.scalars().first()
            if not category:
                category = VehicleCategory(code="SEDAN", name="Executive Sedan", description="Default Sedan Category", capacity=4)
                self.db.add(category)
                await self.db.flush()

        # Create Vehicle
        vehicle = Vehicle(
            category_id=category.id,
            make="Grand Chauffeur",
            model=vehicle_name.strip(),
            year=2025,
            license_plate=plate_clean,
            color="Obsidian Black",
            is_active=True
        )
        self.db.add(vehicle)
        await self.db.flush()

        # Create Driver-Vehicle Assignment
        assignment = DriverVehicleAssignment(
            driver_id=driver_prof.id,
            vehicle_id=vehicle.id,
            is_primary=True,
            is_active=True
        )
        self.db.add(assignment)

        # Session token
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
            "message": "Driver and vehicle registered successfully.",
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            "user": {
                "id": user.id,
                "phone_number": user.phone_number,
                "email": user.email,
                "username": user.username,
                "full_name": user.full_name,
                "role": user.role
            },
            "vehicle": {
                "id": vehicle.id,
                "vehicle_name": vehicle.model,
                "vehicle_no": vehicle.license_plate,
                "vehicle_type": category.code
            }
        }

    async def unified_login_authentication(
        self, login_type: str, identifier: str, password: Optional[str] = None, otp_code: Optional[str] = None,
        ip_address: Optional[str] = None, user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        ident = identifier.strip()
        ident_lower = ident.lower()

        stmt = select(User).where(
            or_(User.phone_number == ident, User.email == ident_lower, User.username == ident_lower)
        )
        res = await self.db.execute(stmt)
        user = res.scalars().first()

        if not user:
            raise HTTPException(status_code=401, detail="Account not found with provided mobile number, email, or username.")

        if not user.is_active:
            raise HTTPException(status_code=403, detail="Account is deactivated. Please contact support.")

        mode = login_type.strip().upper()
        if mode == "PASSWORD":
            if not password:
                raise HTTPException(status_code=400, detail="Password is required for password login.")
            if not user.password_hash or not verify_password(password, user.password_hash):
                raise HTTPException(status_code=401, detail="Invalid password.")
        elif mode == "OTP":
            if not otp_code:
                raise HTTPException(status_code=400, detail="OTP code is required for OTP login.")
            _otp_tracker.verify_attempt(user.phone_number, otp_code)
        else:
            raise HTTPException(status_code=400, detail="Invalid login_type. Must be 'PASSWORD' or 'OTP'.")

        # Session token
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
            "message": f"Welcome back, {user.full_name}!",
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            "user": {
                "id": user.id,
                "phone_number": user.phone_number,
                "email": user.email,
                "username": user.username,
                "full_name": user.full_name,
                "role": user.role
            }
        }

    async def request_forgot_password_passcode(self, identifier: str) -> Dict[str, Any]:
        ident = identifier.strip()
        ident_lower = ident.lower()

        stmt = select(User).where(
            or_(User.phone_number == ident, User.email == ident_lower, User.username == ident_lower)
        )
        res = await self.db.execute(stmt)
        user = res.scalars().first()

        if not user:
            raise HTTPException(status_code=404, detail="No registered account found with provided mobile number or email ID.")

        otp = generate_secure_otp(6)
        await _otp_tracker.record_request(user.phone_number, otp)
        await self.sms_provider.send_otp(user.phone_number, otp)

        logger.info(f"[FORGOT_PASSWORD_OTP] Dispatched 6-digit OTP to target phone number {user.phone_number[-4:].rjust(len(user.phone_number), '*')}")

        return {
            "message": f"Password reset passcode sent to registered phone {user.phone_number[-4:].rjust(len(user.phone_number), '*')}.",
            "phone_masked": f"{user.phone_number[-4:].rjust(len(user.phone_number), '*')}",
            "expires_in_seconds": settings.OTP_EXPIRE_SECONDS
        }

    async def reset_forgot_password_passcode(self, identifier: str, otp_code: str, new_password: str) -> Dict[str, Any]:
        ident = identifier.strip()
        ident_lower = ident.lower()

        if len(new_password) < 6:
            raise HTTPException(status_code=400, detail="New password must contain at least 6 characters.")

        stmt = select(User).where(
            or_(User.phone_number == ident, User.email == ident_lower, User.username == ident_lower)
        )
        res = await self.db.execute(stmt)
        user = res.scalars().first()

        if not user:
            raise HTTPException(status_code=404, detail="Account not found.")

        # Verify OTP
        await _otp_tracker.verify_attempt(user.phone_number, otp_code)

        # Update Password
        user.password_hash = hash_password(new_password)
        await self.db.commit()

        # Session token for login
        token_hash = hash_secret(f"{user.id}:{datetime.now(timezone.utc).isoformat()}:{generate_secure_otp(12)}")
        session = Session(
            user_id=user.id,
            token_hash=token_hash,
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
        )
        self.db.add(session)
        await self.db.commit()

        access_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="access")
        refresh_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="refresh")

        return {
            "message": "Password changed successfully! You are now logged in.",
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            "user": {
                "id": user.id,
                "phone_number": user.phone_number,
                "email": user.email,
                "username": user.username,
                "full_name": user.full_name,
                "role": user.role
            }
        }
