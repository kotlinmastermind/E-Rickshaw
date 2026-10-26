import os
from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional

class Settings(BaseSettings):
    PROJECT_NAME: str = "E-Rickshaw Ride-Hailing Platform"
    API_V1_STR: str = "/api/v1"
    ENVIRONMENT: str = "development"
    DEBUG: bool = True

    # Database
    DATABASE_URL: str = "sqlite+aiosqlite:///./luxury_ride.db"
    DATABASE_URL_SYNC: str = "sqlite:///./luxury_ride.db"

    # Security
    SECRET_KEY: str = "luxury-chauffeur-secret-key-change-in-production-2026-xyz"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 7  # 7 days
    OTP_EXPIRE_SECONDS: int = 300  # 5 minutes
    OTP_MAX_ATTEMPTS: int = 3
    OTP_RESEND_COOLDOWN_SECONDS: int = 60

    # Redis (Optional in local development, required in production clustering)
    REDIS_URL: Optional[str] = None

    # Google Maps Platform (Configurable external boundary)
    GOOGLE_MAPS_API_KEY: Optional[str] = None

    # Payments Integration (Razorpay / UPI)
    RAZORPAY_KEY_ID: Optional[str] = "rzp_test_luxury_chauffeur_mock"
    RAZORPAY_KEY_SECRET: Optional[str] = "rzp_test_secret_chauffeur_mock"
    RAZORPAY_WEBHOOK_SECRET: Optional[str] = "whsec_luxury_payment_hmac_test_2026"

    # SMS Provider (Twilio / AWS SNS / Console Logger)
    SMS_PROVIDER: str = "mock"  # "twilio" or "mock"
    TWILIO_ACCOUNT_SID: Optional[str] = None
    TWILIO_AUTH_TOKEN: Optional[str] = None
    TWILIO_FROM_NUMBER: Optional[str] = None

    # FCM Notifications
    FCM_CREDENTIALS_PATH: Optional[str] = None

    # Storage
    STORAGE_LOCAL_DIR: str = "./uploads/documents"
    STORAGE_MAX_FILE_SIZE_BYTES: int = 5 * 1024 * 1024  # 5 MB

    # CORS
    BACKEND_CORS_ORIGINS: list[str] = [
        "http://localhost:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:8080",
        "http://10.0.2.2:8000"  # Android Emulator Host
    ]

    model_config = SettingsConfigDict(env_file=".env", case_sensitive=True, extra="allow")

settings = Settings()
