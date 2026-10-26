import secrets
import hashlib
import hmac
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
