import secrets
import hashlib
import hmac
import base64
import struct
import time
from datetime import datetime, timedelta, timezone
from typing import Optional, Dict, Any
import jwt
import bcrypt
from backend.core.config import settings

def generate_secure_otp(length: int = 6) -> str:
    """Generate a cryptographically secure numeric OTP."""
    digits = "0123456789"
    return "".join(secrets.choice(digits) for _ in range(length))

def hash_secret(secret: str) -> str:
    """Hash a secret string (token or OTP) using SHA-256 for secure DB lookup."""
    return hashlib.sha256(secret.encode("utf-8")).hexdigest()

def create_jwt_token(
    user_id: str,
    role: str,
    session_id: str,
    token_type: str = "access",
    expires_delta: Optional[timedelta] = None
) -> str:
    now = datetime.now(timezone.utc)
    if expires_delta:
        expire = now + expires_delta
    else:
        if token_type == "refresh":
            expire = now + timedelta(days=7)
        else:
            expire = now + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    
    payload: Dict[str, Any] = {
        "sub": user_id,
        "role": role,
        "session_id": session_id,
        "token_type": token_type,
        "iat": int(now.timestamp()),
        "exp": int(expire.timestamp()),
        "iss": "luxury-ride-platform",
        "aud": "luxury-ride-clients"
    }
    encoded_jwt = jwt.encode(payload, settings.SECRET_KEY, algorithm=settings.ALGORITHM)
    return encoded_jwt

def decode_jwt_token(token: str) -> Dict[str, Any]:
    return jwt.decode(
        token,
        settings.SECRET_KEY,
        algorithms=[settings.ALGORITHM],
        issuer="luxury-ride-platform",
        audience="luxury-ride-clients"
    )

def verify_hmac_signature(payload_bytes: bytes, signature: str, secret: str) -> bool:
    """Verify HMAC SHA-256 webhook signature in constant time."""
    expected = hmac.new(secret.encode("utf-8"), payload_bytes, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature)

def verify_otp_constant_time(provided_otp: str, actual_otp: str) -> bool:
    """Compare two OTP strings in constant time to prevent timing attacks."""
    return hmac.compare_digest(provided_otp, actual_otp)

def hash_password(password: str) -> str:
    """Hash password using bcrypt."""
    salt = bcrypt.gensalt(rounds=12)
    return bcrypt.hashpw(password.encode("utf-8"), salt).decode("utf-8")

def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Verify password against bcrypt hash in constant time."""
    try:
        return bcrypt.checkpw(plain_password.encode("utf-8"), hashed_password.encode("utf-8"))
    except Exception:
        return False

def hash_pin(pin: str) -> str:
    """Hash 4-digit security PIN with platform salt."""
    return hashlib.sha256(f"pin_salt_{pin}_{settings.SECRET_KEY[:8]}".encode("utf-8")).hexdigest()

def verify_pin(pin: str, hashed_pin: str) -> bool:
    """Verify 4-digit PIN in constant time."""
    return hmac.compare_digest(hash_pin(pin), hashed_pin)

def generate_mfa_secret() -> str:
    """Generate a base32 MFA secret for TOTP authenticators."""
    import base64
    random_bytes = secrets.token_bytes(20)
    return base64.b32encode(random_bytes).decode("utf-8").replace("=", "")


def verify_totp(secret: str, code: str, at_time: Optional[float] = None, window: int = 1) -> bool:
    """Verify RFC 6238 six-digit TOTP with a small clock-skew window."""
    if not secret or len(code) != 6 or not code.isdigit():
        return False
    try:
        padded = secret.upper() + "=" * ((8 - len(secret) % 8) % 8)
        key = base64.b32decode(padded, casefold=True)
        counter = int((at_time if at_time is not None else time.time()) // 30)
        for offset in range(-window, window + 1):
            digest = hmac.new(key, struct.pack(">Q", counter + offset), hashlib.sha1).digest()
            index = digest[-1] & 0x0F
            value = struct.unpack(">I", digest[index:index + 4])[0] & 0x7FFFFFFF
            expected = f"{value % 1_000_000:06d}"
            if hmac.compare_digest(expected, code):
                return True
    except Exception:
        return False
    return False
