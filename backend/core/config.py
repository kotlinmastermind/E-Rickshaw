import os
from typing import Optional, List
from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    PROJECT_NAME: str = "E-Rickshaw Ride-Hailing Platform"
    API_V1_STR: str = "/api/v1"
    ENVIRONMENT: str = os.getenv("ENVIRONMENT", "development")
    DEBUG: bool = os.getenv("DEBUG", "True").lower() in ("true", "1", "yes")

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
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 7  # 7 days
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
    GOOGLE_MAPS_API_KEY: Optional[str] = None

    # Payments Integration (Razorpay / UPI)
    RAZORPAY_KEY_ID: Optional[str] = os.getenv("RAZORPAY_KEY_ID", None)
    RAZORPAY_KEY_SECRET: Optional[str] = os.getenv("RAZORPAY_KEY_SECRET", None)
    RAZORPAY_WEBHOOK_SECRET: Optional[str] = os.getenv("RAZORPAY_WEBHOOK_SECRET", None)

    # Primary SMS Gateway
    SMS_PROVIDER: str = os.getenv("SMS_PROVIDER", "console")

    # Push Notifications
    PUSH_PROVIDER: str = os.getenv("PUSH_PROVIDER", "websocket")

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
            if not self.DATABASE_URL:
                raise ValueError("CRITICAL CONFIGURATION ERROR: DATABASE_URL must be provided via environment variables in production.")
            if not self.DATABASE_URL_SYNC:
                raise ValueError("CRITICAL CONFIGURATION ERROR: DATABASE_URL_SYNC must be provided via environment variables in production.")
            if not self.SECRET_KEY or self.SECRET_KEY in (
                "dev-insecure-secret-key-change-in-production",
                "change-me",
                "secret",
                "default"
            ):
                raise ValueError("CRITICAL CONFIGURATION ERROR: A secure SECRET_KEY must be provided via environment variables in production.")
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

