import re
import json
import uuid
import logging
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, or_
from fastapi import HTTPException, status
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, DriverAvailability, Session, Vehicle, VehicleCategory,
    DriverVehicleAssignment, Wallet, UserPreferences
)
from backend.infrastructure.security.crypto import (
    generate_secure_otp, hash_secret, create_jwt_token, verify_otp_constant_time,
    hash_password, verify_password, validate_password_strength, DUMMY_PASSWORD_HASH
)
from backend.infrastructure.cache.redis_client import redis_client
from backend.integrations.sms.provider import get_sms_provider, normalize_e164_phone
from backend.core.config import settings

logger = logging.getLogger("auth_service")

class AuthService:
    def __init__(self, db: AsyncSession):
        self.db = db

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
        """
        Dispatches telecom SMS verification passcode via Twilio Verify Service.
        Includes Redis cooldown to prevent SMS flooding.
        """
        norm_phone = normalize_e164_phone(phone_number)
        if not norm_phone or len(norm_phone) < 10:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="A valid mobile phone number is required (e.g., +919876543210)."
            )

        # Enforce resend cooldown to protect against SMS spamming
        cooldown_key = f"otp_cooldown:{norm_phone}"
        try:
            if await redis_client.exists(cooldown_key):
                raise HTTPException(
                    status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                    detail=f"Please wait {settings.OTP_RESEND_COOLDOWN_SECONDS} seconds before requesting a new passcode."
                )
        except HTTPException:
            raise
        except Exception:
            pass

        sms_provider = get_sms_provider()
        await sms_provider.send_verification_otp(norm_phone)

        try:
            await redis_client.set(cooldown_key, "1", ex=settings.OTP_RESEND_COOLDOWN_SECONDS)
        except Exception:
            pass

        phone_masked = f"{norm_phone[-4:].rjust(len(norm_phone), '*')}"
        return {
            "message": f"Verification passcode dispatched to {phone_masked} via Twilio.",
            "phone_masked": phone_masked,
            "expires_in_seconds": settings.OTP_EXPIRE_SECONDS
        }

    async def verify_otp(
        self,
        phone_number: str,
        otp_code: str,
        role: str = "customer",
        full_name: Optional[str] = None,
        ip_address: Optional[str] = None,
        user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Verifies SMS OTP via Twilio Verify Service and authenticates user session.
        Auto-registers new users seamlessly if account doesn't exist yet.
        STRICT FAIL-CLOSED: All previous bypasses (e.g. 123456) have been permanently deleted.
        """
        norm_phone = normalize_e164_phone(phone_number)
        clean_code = (otp_code or "").strip()

        if not norm_phone or not clean_code:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Mobile phone number and OTP code are required."
            )

        # 1. Twilio Verify API check (NO BYPASS)
        sms_provider = get_sms_provider()
        is_valid = await sms_provider.verify_otp(norm_phone, clean_code)
        if not is_valid:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid or expired OTP passcode. Please check the code and try again."
            )

        # 2. Database lookup
        phone_digits = "".join(filter(str.isdigit, norm_phone))
        conditions = [
            User.phone_number == norm_phone
        ]
        if len(phone_digits) >= 10:
            conditions.append(User.phone_number.endswith(phone_digits[-10:]))

        stmt = select(User).where(or_(*conditions))
        res = await self.db.execute(stmt)
        user = res.scalars().first()

        is_new_user = False
        target_role = (role or "customer").lower().strip()
        if target_role not in ("customer", "driver", "admin"):
            target_role = "customer"

        if not user:
            # Auto-create user account seamlessly
            is_new_user = True
            default_name = (full_name or "").strip()
            if not default_name:
                default_name = "Valued Customer" if target_role == "customer" else "Driver Partner"

            user = User(
                id=str(uuid.uuid4()),
                phone_number=norm_phone,
                email=None,
                full_name=default_name,
                role=target_role,
                is_active=True,
                is_verified=True,
                password_hash=hash_password(f"OtpUserPass_{uuid.uuid4().hex[:12]}")
            )
            self.db.add(user)
            await self.db.flush()
        else:
            if not user.is_active:
                raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Account is deactivated. Please contact support.")
            # If user provided their full name and previous full name was placeholder, update it
            if full_name and full_name.strip() and user.full_name in ("Valued Customer", "Driver Partner", None, ""):
                user.full_name = full_name.strip()

        # Clear lockout tier upon successful verification
        try:
            from backend.infrastructure.security.lockout import reset_account_lockout
            await reset_account_lockout(norm_phone, db=self.db)
        except Exception:
            pass

        # 3. Create active session and issue access + refresh tokens
        session_id = str(uuid.uuid4())
        access_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session_id, token_type="access")
        refresh_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session_id, token_type="refresh")
        token_hash = hash_secret(refresh_token)
        session = Session(
            id=session_id,
            user_id=user.id,
            token_hash=token_hash,
            ip_address=ip_address,
            user_agent=user_agent,
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        self.db.add(session)
        await self.db.commit()

        return {
            "message": f"Welcome back, {user.full_name}!" if not is_new_user else f"Welcome to Vaisentra Mobility, {user.full_name}!",
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            "is_new_user": is_new_user,
            "user": {
                "id": user.id,
                "phone_number": user.phone_number,
                "email": user.email,
                "username": user.username,
                "full_name": user.full_name,
                "role": user.role
            }
        }

    async def refresh_token_rotation(self, refresh_token_str: str) -> Dict[str, Any]:
        """Validates refresh token, rotates tokens atomically in PostgreSQL, and invalidates session family if compromised.

        Concurrency & Replay Safety (Finding C-P5-01 Remediation):
        - PostgreSQL is the sole authoritative state machine for token rotation.
        - Uses atomic Compare-And-Swap (CAS) conditional updates.
        - Supports a configurable grace period (default 10s) to distinguish benign concurrent races
          (e.g., dual API triggers or network retries) from malicious replay attacks.
        - Within the grace period, duplicate requests receive HTTP 409 Conflict without revoking
          the session family or invalidating the winner's new credentials.
        - Outside the grace period, token reuse revokes the complete session family in DB and Redis.
        """
        from backend.infrastructure.security.crypto import decode_jwt_token, hash_secret, create_jwt_token
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
        if not user_id or not session_id:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token claims.")

        # Redis fast-path revocation check (resilient to Redis errors)
        try:
            if await redis_client.exists(f"revoked_session:{session_id}") or await redis_client.exists(f"revoked_user:{user_id}"):
                raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Session has been revoked.")
        except HTTPException:
            raise
        except Exception as redis_err:
            logger.warning(f"Redis check failed during token rotation: {redis_err}")

        # Database is authoritative: query session
        stmt = select(Session).where(Session.id == session_id)
        res = await self.db.execute(stmt)
        session = res.scalar_one_or_none()
        if not session:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Session is no longer valid.")

        if session.is_revoked:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Session has been revoked.")

        now_utc = datetime.now(timezone.utc)
        sess_exp = session.expires_at
        if sess_exp and sess_exp.tzinfo is None:
            sess_exp = sess_exp.replace(tzinfo=timezone.utc)

        if sess_exp and sess_exp <= now_utc:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Session has expired.")

        user_stmt = select(User).where(User.id == user_id, User.is_active == True)
        user_res = await self.db.execute(user_stmt)
        user = user_res.scalar_one_or_none()
        if not user:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User account is deactivated.")

        incoming_token_hash = hash_secret(refresh_token_str)
        grace_period_seconds = getattr(settings, "REFRESH_TOKEN_GRACE_PERIOD_SECONDS", 10)
        grace_period = timedelta(seconds=grace_period_seconds)

        # Helper to check if a session rotation occurred within the concurrency grace period
        def is_within_grace_period(target_session: Session) -> bool:
            if not target_session or not target_session.rotated_at:
                return False
            rot_time = target_session.rotated_at
            if rot_time.tzinfo is None:
                rot_time = rot_time.replace(tzinfo=timezone.utc)
            return (now_utc - rot_time) <= grace_period

        # Check if the incoming token matches the current active token
        is_current_token = bool(
            session.token_hash
            and (
                session.token_hash == incoming_token_hash
                or session.token_hash.startswith("legacy:")
                or session.token_hash.startswith("hash_")
            )
        )

        if not is_current_token:
            # Token does not match current active token.
            # Determine if this is a benign concurrent duplicate within the grace window,
            # or a malicious replay of an old token.
            if session.previous_token_hash == incoming_token_hash and is_within_grace_period(session):
                # Scenario B: Duplicate request presenting the recently rotated token within the grace period.
                # Must NOT revoke the session. Must NOT invalidate newly issued credentials.
                logger.info(
                    f"Concurrency grace window hit for session {session_id}. "
                    f"Token recently rotated at {session.rotated_at}. Returning non-destructive 409."
                )
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Refresh token was recently rotated. Concurrency grace period active; use the credentials from the winning request."
                )

            # Scenario C: Stale-token replay after grace period, or invalid token.
            # Atomically revoke entire session family in DB and Redis.
            session.is_revoked = True
            await self.db.commit()
            try:
                await redis_client.set(f"revoked_session:{session_id}", "true", ex=86400 * 7)
            except Exception as redis_err:
                logger.warning(f"Redis session revocation recording failed: {redis_err}")
            logger.warning(
                f"Security Alert: Refresh token reuse/replay detected for session {session_id}, user {user_id}. "
                f"Previous token match: {session.previous_token_hash == incoming_token_hash}. "
                f"Rotated at: {session.rotated_at}. Session revoked."
            )
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Refresh token reuse detected. Session has been revoked for security."
            )

        # Incoming token is the current active token. Proceed with atomic conditional rotation.
        new_access_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="access")
        new_refresh_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session.id, token_type="refresh")
        new_token_hash = hash_secret(new_refresh_token)
        new_expires_at = now_utc + timedelta(days=7)

        # Atomic conditional update (Compare-And-Swap) in PostgreSQL
        # Guarantees that if two concurrent requests attempt to rotate with the same token,
        # exactly one can update the rowcount.
        update_stmt = (
            update(Session)
            .where(
                Session.id == session_id,
                Session.token_hash == session.token_hash,
                Session.is_revoked == False
            )
            .values(
                previous_token_hash=incoming_token_hash,
                token_hash=new_token_hash,
                rotated_at=now_utc,
                expires_at=new_expires_at
            )
        )
        update_res = await self.db.execute(update_stmt)
        if update_res.rowcount != 1:
            await self.db.rollback()
            # Concurrency race lost: inspect current DB state
            check_stmt = select(Session).where(Session.id == session_id)
            latest_session = (await self.db.execute(check_stmt)).scalar_one_or_none()

            if latest_session and latest_session.previous_token_hash == incoming_token_hash and is_within_grace_period(latest_session):
                # The losing request in a near-simultaneous race!
                logger.info(
                    f"Concurrent CAS race lost for session {session_id}. "
                    f"Winner rotated token at {latest_session.rotated_at}. Returning non-destructive 409."
                )
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Refresh token was recently rotated. Concurrency grace period active; use the credentials from the winning request."
                )

            # Not within grace period: confirmed replay or revoked session
            if latest_session and latest_session.token_hash != incoming_token_hash:
                latest_session.is_revoked = True
                await self.db.commit()
                try:
                    await redis_client.set(f"revoked_session:{session_id}", "true", ex=86400 * 7)
                except Exception:
                    pass
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="Refresh token reuse detected. Session has been revoked for security."
                )
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Session is no longer valid or has been revoked."
            )

        # Commit transaction to PostgreSQL authority
        try:
            await self.db.commit()
        except Exception as commit_err:
            await self.db.rollback()
            logger.error(f"Failed to commit rotated refresh token to database: {commit_err}")
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Failed to persist rotated session credentials."
            )

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
        self, phone_number: Optional[str] = None, email: Optional[str] = None, full_name: str = "Valued Customer",
        password: str = "", username: Optional[str] = None,
        ip_address: Optional[str] = None, user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        phone_raw = (phone_number or "").strip()
        email_raw = (email or "").strip().lower()
        
        if not phone_raw and not email_raw:
            raise HTTPException(status_code=400, detail="Either mobile phone number or email address is required.")
        try:
            validate_password_strength(password)
        except ValueError as err:
            raise HTTPException(status_code=400, detail=str(err))

        # Normalize phone
        phone_digits = "".join(filter(str.isdigit, phone_raw))
        if len(phone_digits) >= 10:
            phone = f"+91{phone_digits[-10:]}"
        elif phone_raw:
            phone = phone_raw
        else:
            phone = f"+91{phone_digits if phone_digits else '0000000000'}"

        # Normalize email
        if email_raw and "@" in email_raw:
            email_clean = email_raw
        else:
            clean_digits = phone_digits[-10:] if len(phone_digits) >= 10 else "guest"
            email_clean = f"{clean_digits}@erickshaw.in"

        # Normalize username
        username_clean = (username or "").strip().lower()
        if not username_clean or len(username_clean) < 3:
            username_clean = email_clean.split("@")[0] if "@" in email_clean else f"cust_{phone[-4:]}"

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
            is_verified=False,
            email_verified=False
        )
        self.db.add(user)
        await self.db.flush()

        profile = CustomerProfile(user_id=user.id)
        self.db.add(profile)

        wallet = Wallet(user_id=user.id, balance_cents=100000)
        self.db.add(wallet)

        pref = UserPreferences(user_id=user.id)
        self.db.add(pref)

        # Session token with single-use refresh token binding
        session_id = str(uuid.uuid4())
        access_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session_id, token_type="access")
        refresh_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session_id, token_type="refresh")
        token_hash = hash_secret(refresh_token)
        session = Session(
            id=session_id,
            user_id=user.id,
            token_hash=token_hash,
            ip_address=ip_address,
            user_agent=user_agent,
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        self.db.add(session)
        await self.db.commit()

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
        self, phone_number: Optional[str] = None, email: Optional[str] = None, full_name: str = "Driver Partner",
        password: str = "", username: Optional[str] = None,
        vehicle_type: str = "E_RICKSHAW_STANDARD", vehicle_name: str = "Electric Rickshaw", vehicle_no: str = "DL-01-ER-1001",
        ip_address: Optional[str] = None, user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        phone_raw = (phone_number or "").strip()
        email_raw = (email or "").strip().lower()
        plate_clean = vehicle_no.strip().upper() if vehicle_no else "DL-01-ER-1001"

        if not phone_raw and not email_raw:
            raise HTTPException(status_code=400, detail="Either mobile phone number or email address is required.")
        try:
            validate_password_strength(password)
        except ValueError as err:
            raise HTTPException(status_code=400, detail=str(err))

        # Normalize phone
        phone_digits = "".join(filter(str.isdigit, phone_raw))
        if len(phone_digits) >= 10:
            phone = f"+91{phone_digits[-10:]}"
        elif phone_raw:
            phone = phone_raw
        else:
            phone = f"+91{phone_digits if phone_digits else '0000000000'}"

        # Normalize email
        if email_raw and "@" in email_raw:
            email_clean = email_raw
        else:
            clean_digits = phone_digits[-10:] if len(phone_digits) >= 10 else "driver"
            email_clean = f"driver_{clean_digits}@erickshaw.in"

        username_clean = (username or "").strip().lower()
        if not username_clean or len(username_clean) < 3:
            username_clean = email_clean.split("@")[0] if "@" in email_clean else f"driver_{phone[-4:]}"

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
            is_verified=False,
            email_verified=False
        )
        self.db.add(user)
        await self.db.flush()

        driver_prof = DriverProfile(
            user_id=user.id,
            license_number=f"DL-{user.id[:8].upper()}",
            approval_status="PENDING",
            onboarding_step="DOCUMENTS_PENDING"
        )
        self.db.add(driver_prof)
        await self.db.flush()

        avail = DriverAvailability(driver_id=driver_prof.id, is_online=False, is_busy=False)
        self.db.add(avail)
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
            is_active=True
        )
        self.db.add(assignment)

        # Session token with single-use refresh token binding
        session_id = str(uuid.uuid4())
        access_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session_id, token_type="access")
        refresh_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session_id, token_type="refresh")
        token_hash = hash_secret(refresh_token)
        session = Session(
            id=session_id,
            user_id=user.id,
            token_hash=token_hash,
            ip_address=ip_address,
            user_agent=user_agent,
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        self.db.add(session)
        await self.db.commit()

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
        self, login_type: str, identifier: str, password: Optional[str] = None,
        ip_address: Optional[str] = None, user_agent: Optional[str] = None
    ) -> Dict[str, Any]:
        ident = identifier.strip()
        ident_lower = ident.lower()
        phone_digits = "".join(filter(str.isdigit, ident))
        phone_norm = f"+91{phone_digits[-10:]}" if len(phone_digits) >= 10 else ident

        conditions = [
            User.email == ident_lower,
            User.username == ident_lower,
            User.phone_number == ident,
            User.phone_number == phone_norm
        ]
        if len(phone_digits) >= 10:
            conditions.append(User.phone_number.endswith(phone_digits[-10:]))

        stmt = select(User).where(or_(*conditions))
        res = await self.db.execute(stmt)
        user = res.scalars().first()

        # Progressive Account Lockout Check
        from backend.infrastructure.security.lockout import (
            check_account_lockout,
            record_failed_login,
            reset_account_lockout
        )
        await check_account_lockout(identifier, db=self.db)

        if not user:
            # Constant-time dummy verification to defeat account enumeration timing attacks
            verify_password(password or "dummy", DUMMY_PASSWORD_HASH)
            await record_failed_login(identifier, db=self.db)
            raise HTTPException(status_code=401, detail="Invalid mobile number, email, or password.")

        if not user.is_active:
            raise HTTPException(status_code=403, detail="Account is deactivated. Please contact support.")

        mode = login_type.strip().upper()
        if mode == "OTP":
            if not password:
                raise HTTPException(status_code=400, detail="OTP passcode is required for OTP login.")
            return await self.verify_otp(
                phone_number=identifier,
                otp_code=password,
                role=user.role if user else "customer",
                ip_address=ip_address,
                user_agent=user_agent
            )
        elif mode != "PASSWORD":
            raise HTTPException(
                status_code=400,
                detail="Unsupported login type. Supported types: OTP, PASSWORD."
            )
        if not password:
            raise HTTPException(status_code=400, detail="Password is required for password login.")
        if not user.password_hash or not verify_password(password, user.password_hash):
            await record_failed_login(identifier, db=self.db)
            raise HTTPException(status_code=401, detail="Invalid password.")

        # Successful authentication: clear failure counters and lockout tier
        await reset_account_lockout(identifier, db=self.db)

        # Session token with single-use refresh token binding
        session_id = str(uuid.uuid4())
        access_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session_id, token_type="access")
        refresh_token = create_jwt_token(user_id=user.id, role=user.role, session_id=session_id, token_type="refresh")
        token_hash = hash_secret(refresh_token)
        session = Session(
            id=session_id,
            user_id=user.id,
            token_hash=token_hash,
            ip_address=ip_address,
            user_agent=user_agent,
            is_revoked=False,
            expires_at=datetime.now(timezone.utc) + timedelta(days=7)
        )
        self.db.add(session)
        await self.db.commit()

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

