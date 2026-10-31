from abc import ABC, abstractmethod
import asyncio
from typing import Dict, Any, Optional
from datetime import datetime, timezone
import logging
from backend.core.config import settings

logger = logging.getLogger("notifications_provider")

class NotificationProvider(ABC):
    @abstractmethod
    async def send_push_notification(
        self, device_token: str, title: str, body: str, data: Optional[Dict[str, Any]] = None
    ) -> bool:
        pass

class MockNotificationProvider(NotificationProvider):
    async def send_push_notification(
        self, device_token: str, title: str, body: str, data: Optional[Dict[str, Any]] = None
    ) -> bool:
        logger.info("Push provider is not configured; message remains in the persisted inbox.")
        return False

class FirebaseFCMNotificationProvider(NotificationProvider):
    def __init__(self):
        self.fallback = MockNotificationProvider()
        self._initialized = False
        self.last_error: str = "Firebase credentials are not configured."
        self.last_error_code: str = "PROVIDER_NOT_CONFIGURED"
        self.last_message_id: Optional[str] = None
        self._init_firebase()

    def _init_firebase(self):
        try:
            import os
            import json
            import firebase_admin
            from firebase_admin import credentials
            if not firebase_admin._apps:
                cred = None
                # Priority 1: Direct JSON string in environment (ideal for Render / cloud dashboard)
                json_env = getattr(settings, "FIREBASE_SERVICE_ACCOUNT_JSON", None) or os.environ.get("FIREBASE_SERVICE_ACCOUNT_JSON")
                if json_env:
                    try:
                        cert_dict = json.loads(json_env) if isinstance(json_env, str) else json_env
                        cred = credentials.Certificate(cert_dict)
                    except Exception as e:
                        logger.warning(f"Could not parse FIREBASE_SERVICE_ACCOUNT_JSON: {e}")

                # Priority 2: Configured file path
                if not cred and settings.FCM_CREDENTIALS_PATH and os.path.exists(settings.FCM_CREDENTIALS_PATH):
                    cred = credentials.Certificate(settings.FCM_CREDENTIALS_PATH)

                # Priority 3: Default relative file paths
                if not cred:
                    for candidate in ["backend/firebase-service-account.json", "firebase-service-account.json"]:
                        if os.path.exists(candidate):
                            cred = credentials.Certificate(candidate)
                            break

                if cred:
                    firebase_admin.initialize_app(cred)
                else:
                    firebase_admin.initialize_app()
            self._initialized = True
            self.last_error = ""
            self.last_error_code = ""
            logger.info("Firebase Admin SDK successfully initialized for Cloud Messaging.")
        except Exception as e:
            logger.info(f"Firebase Admin not configured or local environment, using resilient fallback: {e}")
            self._initialized = False

    async def send_push_notification(
        self, device_token: str, title: str, body: str, data: Optional[Dict[str, Any]] = None
    ) -> bool:
        if not device_token:
            self.last_error = "Push token is empty."
            self.last_error_code = "INVALID_TOKEN"
            return False

        if self._initialized:
            try:
                from firebase_admin import messaging
                msg = messaging.Message(
                    token=device_token,
                    notification=messaging.Notification(
                        title=title,
                        body=body
                    ),
                    android=messaging.AndroidConfig(
                        priority="high",
                        notification=messaging.AndroidNotification(
                            sound="default",
                            channel_id="driver_ride_dispatch_channel",
                            priority="max"
                        )
                    ),
                    data={str(k): str(v) for k, v in (data or {}).items()}
                )
                # Firebase Admin's send call is blocking; keep the async API worker responsive.
                res = await asyncio.to_thread(messaging.send, msg)
                self.last_message_id = str(res)
                self.last_error = ""
                self.last_error_code = ""
                logger.info(f"[FCM_SUCCESS] High-priority push sent to {device_token[:10]}... Result: {res}")
                return True
            except Exception as e:
                logger.error(f"[FCM_ERROR] Failed sending FCM message: {e}")
                code = str(getattr(e, "code", "") or e.__class__.__name__).lower()
                if "unregistered" in code or "registration-token-not-registered" in str(e).lower():
                    self.last_error_code = "UNREGISTERED"
                elif "invalidargument" in code or "invalid-argument" in code:
                    self.last_error_code = "FCM_BAD_REQUEST"
                else:
                    self.last_error_code = "FCM_SEND_FAILED"
                self.last_error = str(e)[:500]
                return False

        self.last_error = "Firebase Cloud Messaging is unavailable; delivery was not simulated."
        self.last_error_code = "PROVIDER_NOT_CONFIGURED"
        return False

_notif_instance: Optional[NotificationProvider] = None

def get_notification_provider() -> NotificationProvider:
    global _notif_instance
    if _notif_instance is None:
        _notif_instance = FirebaseFCMNotificationProvider()
    return _notif_instance


async def get_notification_provider_health() -> Dict[str, Any]:
    provider = get_notification_provider()
    if isinstance(provider, FirebaseFCMNotificationProvider):
        return {
            "push": "CONFIGURED_UNVERIFIED" if provider._initialized else "NOT_CONFIGURED",
            "push_provider": "Firebase Cloud Messaging",
            "push_last_error_code": provider.last_error_code or None,
            "health_boundary": "Credential initialization state only; no live provider probe or device receipt is available.",
            "email": "NOT_CONFIGURED",
            "sms": "NOT_CONFIGURED",
            "checked_at": datetime.now(timezone.utc).isoformat(),
        }
    return {"push": "NOT_CONFIGURED", "push_provider": "Unavailable", "email": "NOT_CONFIGURED", "sms": "NOT_CONFIGURED"}
