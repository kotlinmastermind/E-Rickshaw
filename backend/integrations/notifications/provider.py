from abc import ABC, abstractmethod
from typing import Dict, Any, List, Optional
import logging

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

_notif_instance = MockNotificationProvider()

def get_notification_provider() -> NotificationProvider:
    return _notif_instance
