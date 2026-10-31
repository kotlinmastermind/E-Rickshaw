from typing import Optional, Dict, Any, List
from pydantic import BaseModel, Field

class IdentityLookupPayload(BaseModel):
    """Payload to look up user credentials and auth options by phone or email."""
    identifier: str = Field(..., description="Mobile number (e.g. +919876543210) or Email address", json_schema_extra={"example": "+919876543210"})
    role: str = Field(default="customer", description="customer or driver", json_schema_extra={"example": "customer"})

class OtpRequestPayload(BaseModel):
    """Payload to request one-time passcode for phone auth."""
    phone_number: str = Field(..., description="Full international phone number with country code", json_schema_extra={"example": "+919876543210"})

class OtpVerifyPayload(BaseModel):
    """Payload to verify OTP and authenticate session."""
    phone_number: str = Field(..., json_schema_extra={"example": "+919876543210"})
    otp_code: str = Field(..., min_length=4, max_length=6, json_schema_extra={"example": "123456"})
    role: str = Field(default="customer", json_schema_extra={"example": "customer"})

class TokenRefreshPayload(BaseModel):
    """Payload to exchange active refresh token for a fresh access token."""
    refresh_token: str = Field(..., description="Cryptographically signed refresh token")

class PasswordRegisterPayload(BaseModel):
    """Payload to register a new user with email and secure password."""
    email: str = Field(..., json_schema_extra={"example": "guest@erickshaw.in"})
    password: str = Field(..., min_length=8)
    full_name: str = Field(..., min_length=2)
    phone_number: str = Field(..., min_length=10)
    role: str = Field(default="customer")

class PasswordLoginPayload(BaseModel):
    """Payload for password-based authentication."""
    email: str = Field(..., json_schema_extra={"example": "guest@erickshaw.in"})
    password: str = Field(...)

class AdminMfaEnablePayload(BaseModel):
    """Payload to verify MFA TOTP token for admin accounts."""
    setup_token: str = Field(..., min_length=20)
    code: str = Field(..., min_length=6, max_length=6, pattern="^[0-9]{6}$")

class OAuthPayload(BaseModel):
    """Payload for Google/Apple Single Sign-On."""
    provider: Optional[str] = "google"
    identity_token: Optional[str] = None
    id_token: Optional[str] = None
    apple_user_id: Optional[str] = None
    email: Optional[str] = "guest@erickshaw.in"
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
    email: str = Field(..., json_schema_extra={"example": "customer@erickshaw.in"})
    full_name: str = Field(..., min_length=2)
    password: str = Field(..., min_length=6)
    username: str = Field(..., min_length=3)
    otp_code: str = Field(..., min_length=4, max_length=6)

class DriverSignupPayload(BaseModel):
    phone_number: str = Field(..., min_length=10)
    email: str = Field(..., json_schema_extra={"example": "driver@erickshaw.in"})
    full_name: str = Field(..., min_length=2)
    password: str = Field(..., min_length=6)
    username: str = Field(..., min_length=3)
    otp_code: str = Field(..., min_length=4, max_length=6)
    vehicle_type: str = Field(default="SEDAN")
    vehicle_name: str = Field(..., min_length=2, json_schema_extra={"example": "Electric Auto L5M"})
    vehicle_no: str = Field(..., min_length=5, json_schema_extra={"example": "DL-01-ER-1001"})

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

class FirebaseVerifyPayload(BaseModel):
    phone_number: str = Field(..., json_schema_extra={"example": "+919876543210"})
    role: str = Field(default="customer")
    firebase_uid: Optional[str] = None
    id_token: Optional[str] = None

class UserDto(BaseModel):
    """Standardized user representation across all endpoints."""
    id: str
    phone_number: str
    email: Optional[str] = None
    full_name: Optional[str] = None
    role: str
    is_active: bool = True
    tier: Optional[str] = "STANDARD"

class AuthResponse(BaseModel):
    """Standardized authentication response envelope."""
    access_token: str
    token_type: str = "bearer"
    expires_in_minutes: int
    user: UserDto
    requires_profile_completion: bool = False
