from abc import ABC, abstractmethod
from typing import Optional, Dict
import logging

logger = logging.getLogger("sms_provider")

class SMSProvider(ABC):
    @abstractmethod
    async def send_otp(self, phone_number: str, otp_code: str) -> bool:
        pass

class MockSMSProvider(SMSProvider):
    def __init__(self):
        self.sent_otps: Dict[str, str] = {}

    async def send_otp(self, phone_number: str, otp_code: str) -> bool:
        self.sent_otps[phone_number] = otp_code
        logger.info(f"[SMS_LOG] Code sent to {phone_number}")
        return True

    def get_latest_otp(self, phone_number: str) -> Optional[str]:
        return self.sent_otps.get(phone_number)

_mock_sms_instance = MockSMSProvider()

def get_sms_provider() -> SMSProvider:
    return _mock_sms_instance
