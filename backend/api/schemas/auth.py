from typing import Optional, Dict, Any, List
from pydantic import BaseModel, Field

class IdentityLookupPayload(BaseModel):
    """Payload to look up user credentials and auth options by phone or email."""
    identifier: str = Field(..., description="Mobile number (e.g. +919876543210) or Email address", json_schema_extra={"example": "+919876543210"})
    role: str = Field(default="customer", description="customer or driver", json_schema_extra={"example": "customer"})

class TokenRefreshPayload(BaseModel):
    """Payload to exchange active refresh token for a fresh access token."""
    refresh_token: str = Field(..., description="Cryptographically signed refresh token")

class PasswordRegisterPayload(BaseModel):
    """Payload to register a new user with email and secure password."""
    email: str = Field(..., json_schema_extra={"example": "guest@erickshaw.in"})
    password: str = Field(..., min_length=12, description="Password must contain at least 12 characters.")
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
    new_password: str = Field(..., min_length=12, description="New password must contain at least 12 characters.")

class EmailVerifyPayload(BaseModel):
    code: str = Field(..., min_length=6, max_length=6)

class CustomerSignupPayload(BaseModel):
    phone_number: Optional[str] = Field(default=None)
    email: Optional[str] = Field(default=None)
    full_name: str = Field(default="Valued Customer")
    password: str = Field(..., min_length=12, description="Password must contain at least 12 characters.")
    username: Optional[str] = Field(default=None)

class DriverSignupPayload(BaseModel):
    phone_number: Optional[str] = Field(default=None)
    email: Optional[str] = Field(default=None)
    full_name: str = Field(default="Driver Partner")
    password: str = Field(..., min_length=12, description="Password must contain at least 12 characters.")
    username: Optional[str] = Field(default=None)
    vehicle_type: str = Field(default="E_RICKSHAW_STANDARD")
    vehicle_name: Optional[str] = Field(default="Electric Rickshaw")
    vehicle_no: Optional[str] = Field(default="DL-01-ER-1001")

class UnifiedLoginPayload(BaseModel):
    login_type: str = Field(default="PASSWORD")
    identifier: str = Field(..., min_length=3)
    password: Optional[str] = None

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
