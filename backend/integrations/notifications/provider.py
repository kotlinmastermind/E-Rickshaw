from abc import ABC, abstractmethod
from typing import Dict, Any, List, Optional
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
    def __init__(self):
        self.dispatched_notifications: List[Dict[str, Any]] = []

    async def send_push_notification(
        self, device_token: str, title: str, body: str, data: Optional[Dict[str, Any]] = None
    ) -> bool:
        record = {
            "token": device_token,
            "title": title,
            "body": body,
            "data": data or {}
        }
        self.dispatched_notifications.append(record)
        logger.info(f"[PUSH_DISPATCH] To: {device_token[:10]}... | Title: {title} | Body: {body}")
        return True

class FirebaseFCMNotificationProvider(NotificationProvider):
    def __init__(self):
        self.fallback = MockNotificationProvider()
        self._initialized = False
        self._init_firebase()

    def _init_firebase(self):
        try:
            import firebase_admin
            from firebase_admin import credentials
            if not firebase_admin._apps:
                if settings.FCM_CREDENTIALS_PATH:
                    cred = credentials.Certificate(settings.FCM_CREDENTIALS_PATH)
                    firebase_admin.initialize_app(cred)
                else:
                    firebase_admin.initialize_app()
            self._initialized = True
            logger.info("Firebase Admin SDK successfully initialized for Cloud Messaging.")
        except Exception as e:
            logger.info(f"Firebase Admin not configured or local environment, using resilient fallback: {e}")
            self._initialized = False

    async def send_push_notification(
        self, device_token: str, title: str, body: str, data: Optional[Dict[str, Any]] = None
    ) -> bool:
        if not device_token:
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
                res = messaging.send(msg)
                logger.info(f"[FCM_SUCCESS] High-priority push sent to {device_token[:10]}... Result: {res}")
                return True
            except Exception as e:
                logger.error(f"[FCM_ERROR] Failed sending FCM message: {e}")

        return await self.fallback.send_push_notification(device_token, title, body, data)

_notif_instance: Optional[NotificationProvider] = None

def get_notification_provider() -> NotificationProvider:
    global _notif_instance
    if _notif_instance is None:
        _notif_instance = FirebaseFCMNotificationProvider()
    return _notif_instance
