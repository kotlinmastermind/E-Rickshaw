import hmac
import os
from typing import Optional, List
from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

VALID_ENVIRONMENTS = frozenset({"development", "test", "staging", "production"})
WEAK_SECRET_VALUES = frozenset({
    "dev-insecure-secret-key-change-in-production",
    "change-me",
    "changeme",
    "secret",
    "default",
    "placeholder",
    "your-secret-key",
    "your_secret_key",
})
PLACEHOLDER_SUBSTRINGS = (
    "placeholder",
    "change-me",
    "changeme",
    "your_",
    "your-",
    "<secret",
    "<password",
    "example.com",
)


def _contains_placeholder(value: Optional[str]) -> bool:
    if not value:
        return True
    lower = value.strip().lower()
    return any(tok in lower for tok in PLACEHOLDER_SUBSTRINGS)

class Settings(BaseSettings):
    PROJECT_NAME: str = "VAISENTRA MOBILITY"
    API_V1_STR: str = "/api/v1"
    ENVIRONMENT: str = os.getenv("ENVIRONMENT", "development")
    DEBUG: bool = os.getenv("DEBUG", "False").lower() in ("true", "1", "yes")

    # Database (Supabase PostgreSQL Production Pooler)
    DATABASE_URL: str = os.getenv("DATABASE_URL", "")
    DATABASE_URL_SYNC: str = os.getenv("DATABASE_URL_SYNC", "")

    # Supabase Credentials
    SUPABASE_URL: str = os.getenv("SUPABASE_URL", "")
    SUPABASE_KEY: str = os.getenv("SUPABASE_KEY", "")
    SUPABASE_PUBLISHABLE_KEY: str = os.getenv("SUPABASE_PUBLISHABLE_KEY", "")

    # Security (JWT Authentication)
    SECRET_KEY: str = os.getenv("SECRET_KEY", "")
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = int(os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES", "15"))  # 15 minutes short-lived access token
    REFRESH_TOKEN_GRACE_PERIOD_SECONDS: int = int(os.getenv("REFRESH_TOKEN_GRACE_PERIOD_SECONDS", "10"))
    WEBAUTHN_ENABLED: bool = os.getenv("WEBAUTHN_ENABLED", "false").lower() in ("true", "1", "t")
    OTP_EXPIRE_SECONDS: int = 300  # 5 minutes
    OTP_MAX_ATTEMPTS: int = 3
    OTP_RESEND_COOLDOWN_SECONDS: int = 60

    # Upstash Cloud Redis (In-Memory Cache & Pub/Sub)
    REDIS_URL: Optional[str] = os.getenv("REDIS_URL", None)
    UPSTASH_REDIS_REST_URL: Optional[str] = os.getenv("UPSTASH_REDIS_REST_URL", None)
    UPSTASH_REDIS_REST_TOKEN: Optional[str] = os.getenv("UPSTASH_REDIS_REST_TOKEN", None)

    # Mapbox Web Services Integration (Directions, Geocoding, Matrix & Isochrone)
    MAPBOX_SECRET_TOKEN: Optional[str] = os.getenv("MAPBOX_SECRET_TOKEN", None)
    MAPBOX_PUBLIC_TOKEN: Optional[str] = os.getenv("MAPBOX_PUBLIC_TOKEN", "")

    # Payments Integration (Razorpay / UPI)
    RAZORPAY_KEY_ID: Optional[str] = os.getenv("RAZORPAY_KEY_ID", None)
    RAZORPAY_KEY_SECRET: Optional[str] = os.getenv("RAZORPAY_KEY_SECRET", None)
    RAZORPAY_WEBHOOK_SECRET: Optional[str] = os.getenv("RAZORPAY_WEBHOOK_SECRET", None)
    RAZORPAY_MERCHANT_ACCOUNT_ID: Optional[str] = os.getenv("RAZORPAY_MERCHANT_ACCOUNT_ID", None)

    # Primary SMS Gateway (Twilio Verify Service)
    SMS_PROVIDER: str = os.getenv("SMS_PROVIDER", "twilio")
    TWILIO_ACCOUNT_SID: str = os.getenv("TWILIO_ACCOUNT_SID", "")
    TWILIO_AUTH_TOKEN: str = os.getenv("TWILIO_AUTH_TOKEN", "")
    TWILIO_VERIFY_SERVICE_SID: str = os.getenv("TWILIO_VERIFY_SERVICE_SID", "")
    TWILIO_API_KEY: Optional[str] = os.getenv("TWILIO_API_KEY", None)
    TWILIO_PHONE_NUMBER: Optional[str] = os.getenv("TWILIO_PHONE_NUMBER", None)
    FCM_CREDENTIALS_PATH: Optional[str] = os.getenv("FCM_CREDENTIALS_PATH", None)
    FIREBASE_CREDENTIALS_PATH: Optional[str] = os.getenv("FIREBASE_CREDENTIALS_PATH", None)

    # Push Notifications & Real-Time Dispatch Governance (Phase 3.3)
    PUSH_PROVIDER: str = os.getenv("PUSH_PROVIDER", "websocket")
    DISPATCH_WS_HEARTBEAT_SECONDS: int = int(os.getenv("DISPATCH_WS_HEARTBEAT_SECONDS", "30"))
    DISPATCH_AUTH_REVALIDATION_SECONDS: int = int(os.getenv("DISPATCH_AUTH_REVALIDATION_SECONDS", "30"))
    DISPATCH_OFFER_TIMEOUT_SECONDS: int = int(os.getenv("DISPATCH_OFFER_TIMEOUT_SECONDS", "20"))
    DISPATCH_SAFETY_FALLBACK_SECONDS: int = int(os.getenv("DISPATCH_SAFETY_FALLBACK_SECONDS", "22"))
    DISPATCH_COOLDOWN_SECONDS: int = int(os.getenv("DISPATCH_COOLDOWN_SECONDS", "60"))
    DISPATCH_RECONNECT_BACKOFF_BASE_SECONDS: float = float(os.getenv("DISPATCH_RECONNECT_BACKOFF_BASE_SECONDS", "1.0"))
    DISPATCH_RECONNECT_BACKOFF_MAX_SECONDS: float = float(os.getenv("DISPATCH_RECONNECT_BACKOFF_MAX_SECONDS", "30.0"))
    DISPATCH_SUBSCRIBER_RETRY_LIMIT: int = int(os.getenv("DISPATCH_SUBSCRIBER_RETRY_LIMIT", "5"))

    # Storage
    STORAGE_LOCAL_DIR: str = "./uploads/documents"
    STORAGE_MAX_FILE_SIZE_BYTES: int = 5 * 1024 * 1024  # 5 MB

    # CORS
    BACKEND_CORS_ORIGINS: List[str] = [
        "http://localhost:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:8080",
        "http://10.0.2.2:8000",  # Android Emulator Host
        "https://e-rickshaw-admin.onrender.com",
        "https://e-rickshaw-api.onrender.com",
        "https://e-rickshaw-z32z.onrender.com",
        "https://vaisentra.github.io"
    ]

    model_config = SettingsConfigDict(
        env_file=None if os.getenv("TESTING") == "1" else [".env", "backend/.env"],
        case_sensitive=True,
        extra="allow"
    )

    @model_validator(mode="after")
    def validate_production_security(self) -> "Settings":
        # Normalize and reject unknown environments rather than falling back silently
        self.ENVIRONMENT = (self.ENVIRONMENT or "development").strip().lower()
        if self.ENVIRONMENT not in VALID_ENVIRONMENTS:
            raise ValueError(
                f"CRITICAL CONFIGURATION ERROR: Unrecognized ENVIRONMENT '{self.ENVIRONMENT}'. "
                f"Expected one of: {sorted(VALID_ENVIRONMENTS)}."
            )

        # Testing mode overrides
        if os.getenv("TESTING") == "1":
            if not self.DATABASE_URL or not self.DATABASE_URL.startswith("sqlite"):
                self.DATABASE_URL = "sqlite+aiosqlite:///file:testdb?mode=memory&cache=shared&uri=true"
            if not self.DATABASE_URL_SYNC or not self.DATABASE_URL_SYNC.startswith("sqlite"):
                self.DATABASE_URL_SYNC = "sqlite:///file:testdb?mode=memory&cache=shared&uri=true"
            if not self.SECRET_KEY:
                self.SECRET_KEY = "test-secret-key-for-unit-testing-purposes"
            return self

        # Strict production validation
        if self.ENVIRONMENT == "production":
            if self.DEBUG:
                raise ValueError("CRITICAL CONFIGURATION ERROR: DEBUG must be False in production.")
            if self.ACCESS_TOKEN_EXPIRE_MINUTES < 1 or self.ACCESS_TOKEN_EXPIRE_MINUTES > 60:
                raise ValueError(
                    "CRITICAL CONFIGURATION ERROR: ACCESS_TOKEN_EXPIRE_MINUTES must be between 1 and 60 in production."
                )

            db_url = (self.DATABASE_URL or "").strip()
            db_sync_url = (self.DATABASE_URL_SYNC or "").strip()
            if (
                not db_url
                or not db_url.startswith(("postgresql://", "postgresql+asyncpg://"))
                or _contains_placeholder(db_url)
            ):
                raise ValueError(
                    "CRITICAL CONFIGURATION ERROR: Valid PostgreSQL DATABASE_URL must be provided via environment variables in production."
                )
            if (
                not db_sync_url
                or not db_sync_url.startswith(("postgresql://", "postgresql+psycopg2://", "postgres://"))
                or _contains_placeholder(db_sync_url)
            ):
                raise ValueError(
                    "CRITICAL CONFIGURATION ERROR: Valid PostgreSQL DATABASE_URL_SYNC must be provided via environment variables in production."
                )

            secret = (self.SECRET_KEY or "").strip()
            if len(secret) < 32 or secret.lower() in WEAK_SECRET_VALUES or _contains_placeholder(secret):
                raise ValueError(
                    "CRITICAL CONFIGURATION ERROR: A secure SECRET_KEY must be provided via environment variables in production (minimum 32 characters)."
                )

            rzp_key_id = (self.RAZORPAY_KEY_ID or "").strip()
            if not rzp_key_id.startswith("rzp_live_") or len(rzp_key_id) < 18 or _contains_placeholder(rzp_key_id):
                raise ValueError(
                    "CRITICAL CONFIGURATION ERROR: A valid Live RAZORPAY_KEY_ID (rzp_live_*) must be configured in production."
                )

            rzp_key_secret = (self.RAZORPAY_KEY_SECRET or "").strip()
            if len(rzp_key_secret) < 16 or _contains_placeholder(rzp_key_secret):
                raise ValueError(
                    "CRITICAL CONFIGURATION ERROR: A valid RAZORPAY_KEY_SECRET must be configured in production."
                )

            rzp_webhook_secret = (self.RAZORPAY_WEBHOOK_SECRET or "").strip()
            if len(rzp_webhook_secret) < 16 or _contains_placeholder(rzp_webhook_secret):
                raise ValueError(
                    "CRITICAL CONFIGURATION ERROR: A valid RAZORPAY_WEBHOOK_SECRET must be configured in production."
                )

            if hmac.compare_digest(rzp_key_secret.encode("utf-8"), rzp_webhook_secret.encode("utf-8")):
                raise ValueError(
                    "CRITICAL CONFIGURATION ERROR: RAZORPAY_WEBHOOK_SECRET must be distinct from RAZORPAY_KEY_SECRET in production."
                )

            merchant_acct = (self.RAZORPAY_MERCHANT_ACCOUNT_ID or "").strip()
            if merchant_acct and (not merchant_acct.startswith("acc_") or len(merchant_acct) < 14):
                raise ValueError(
                    "CRITICAL CONFIGURATION ERROR: RAZORPAY_MERCHANT_ACCOUNT_ID must be a valid Razorpay account ID (acc_*)."
                )
        else:
            # Safe development defaults when .env is absent
            if not self.DATABASE_URL:
                self.DATABASE_URL = "sqlite+aiosqlite:///./dev.db"
            if not self.DATABASE_URL_SYNC:
                self.DATABASE_URL_SYNC = "sqlite:///./dev.db"
            if not self.SECRET_KEY:
                self.SECRET_KEY = "dev-insecure-secret-key-change-in-production"

        return self

settings = Settings()
if os.getenv("TESTING") == "1":
    settings.DATABASE_URL = "sqlite+aiosqlite:///file:testdb?mode=memory&cache=shared&uri=true"
    settings.DATABASE_URL_SYNC = "sqlite:///file:testdb?mode=memory&cache=shared&uri=true"
    settings.REDIS_URL = None

