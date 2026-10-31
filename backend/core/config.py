import os
from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional

class Settings(BaseSettings):
    PROJECT_NAME: str = "E-Rickshaw Ride-Hailing Platform"
    API_V1_STR: str = "/api/v1"
    ENVIRONMENT: str = "development"
    DEBUG: bool = True

    # Database (Supabase PostgreSQL Production Pooler)
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        "postgresql+asyncpg://postgres.lmrvubzinkziexaqozlw:sunil%40%2375620@aws-0-ap-southeast-2.pooler.supabase.com:5432/postgres?ssl=require"
    )
    DATABASE_URL_SYNC: str = os.getenv(
        "DATABASE_URL_SYNC",
        "postgresql+psycopg2://postgres.lmrvubzinkziexaqozlw:sunil%40%2375620@aws-0-ap-southeast-2.pooler.supabase.com:5432/postgres?sslmode=require"
    )

    # Security
    SECRET_KEY: str = "e-rickshaw-mobility-secret-key-prod-2026-auth-token-secured"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 7  # 7 days
    OTP_EXPIRE_SECONDS: int = 300  # 5 minutes
    OTP_MAX_ATTEMPTS: int = 3
    OTP_RESEND_COOLDOWN_SECONDS: int = 60

    # Upstash Cloud Redis (In-Memory Cache & Pub/Sub)
    REDIS_URL: Optional[str] = os.getenv(
        "REDIS_URL",
        "rediss://default:gQAAAAAAA6FxAAIgcDFlMDE2ZjlkMDZlZTQ0ODIwYjg5NzdiODFkMmU1YzM4OQ@optimum-aphid-237937.upstash.io:6379"
    )

    # Google Maps Platform (Configurable external boundary)
    GOOGLE_MAPS_API_KEY: Optional[str] = "AIzaSyBCd1pDJ_1KeajQKHUQG-8QqlB5OpwmmOo"

    # Payments Integration (Razorpay / UPI)
    RAZORPAY_KEY_ID: Optional[str] = "rzp_test_luxury_chauffeur_mock"
    RAZORPAY_KEY_SECRET: Optional[str] = "rzp_test_secret_chauffeur_mock"
    RAZORPAY_WEBHOOK_SECRET: Optional[str] = "whsec_luxury_payment_hmac_test_2026"

    # Primary SMS Gateway: Google Firebase Authentication (Free 10,000 Telecom SMS/month)
    SMS_PROVIDER: str = "firebase"

    # FCM Notifications
    FCM_CREDENTIALS_PATH: Optional[str] = "backend/firebase-service-account.json"
    FIREBASE_SERVICE_ACCOUNT_JSON: Optional[str] = None

    # Storage
    STORAGE_LOCAL_DIR: str = "./uploads/documents"
    STORAGE_MAX_FILE_SIZE_BYTES: int = 5 * 1024 * 1024  # 5 MB

    # CORS
    BACKEND_CORS_ORIGINS: list[str] = [
        "http://localhost:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:8080",
        "http://10.0.2.2:8000",  # Android Emulator Host
        "https://e-rickshaw-admin.onrender.com",
        "https://e-rickshaw-api.onrender.com",
        "https://e-rickshaw-z32z.onrender.com"
    ]

    model_config = SettingsConfigDict(
        env_file=None if os.getenv("TESTING") == "1" else ".env",
        case_sensitive=True,
        extra="allow"
    )

settings = Settings()
if os.getenv("TESTING") == "1":
    settings.DATABASE_URL = "sqlite+aiosqlite:///./luxury_ride.db"
    settings.DATABASE_URL_SYNC = "sqlite:///./luxury_ride.db"
    settings.REDIS_URL = None
