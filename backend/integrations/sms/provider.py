from abc import ABC, abstractmethod
from typing import Optional, Dict
import logging
from backend.core.config import settings

logger = logging.getLogger("sms_provider")

class SMSProvider(ABC):
    @abstractmethod
    async def send_otp(self, phone_number: str, otp_code: str) -> bool:
        """Send OTP to target phone number."""
        pass

class MockSMSProvider(SMSProvider):
    """
    In-memory SMS provider for automated testing and local staging.
    Captures dispatched OTPs without making billable telecom network calls.
    """
    def __init__(self):
        self.sent_otps: Dict[str, str] = {}

    async def send_otp(self, phone_number: str, otp_code: str) -> bool:
        self.sent_otps[phone_number] = otp_code
        logger.info(f"[SECURE_MOCK_SMS] Verification code dispatched to {phone_number[-4:].rjust(len(phone_number), '*')}")
        return True

    def get_latest_otp(self, phone_number: str) -> Optional[str]:
        return self.sent_otps.get(phone_number)

class TwilioSMSProvider(SMSProvider):
    def __init__(self, account_sid: str, auth_token: str, from_number: str):
        self.account_sid = account_sid
        self.auth_token = auth_token
        self.from_number = from_number

    async def send_otp(self, phone_number: str, otp_code: str) -> bool:
        if not self.account_sid or not self.auth_token:
            logger.error("Twilio credentials missing. Refusing to send.")
            return False
        # External Twilio boundary implementation
        import httpx
        url = f"https://api.twilio.com/2010-04-01/Accounts/{self.account_sid}/Messages.json"
        data = {
            "To": phone_number,
            "From": self.from_number,
            "Body": f"Your Luxury Chauffeur authorization passcode is: {otp_code}. Valid for 5 minutes. Do not share."
        }
        async with httpx.AsyncClient() as client:
            try:
                resp = await client.post(url, data=data, auth=(self.account_sid, self.auth_token), timeout=10.0)
                return resp.status_code in [200, 201]
            except Exception as e:
                logger.error(f"Twilio network dispatch failed: {e}")
                return False

_mock_sms_instance = MockSMSProvider()

def get_sms_provider() -> SMSProvider:
    if settings.SMS_PROVIDER == "twilio" and settings.TWILIO_ACCOUNT_SID:
        return TwilioSMSProvider(
            account_sid=settings.TWILIO_ACCOUNT_SID,
            auth_token=settings.TWILIO_AUTH_TOKEN or "",
            from_number=settings.TWILIO_FROM_NUMBER or ""
        )
    return _mock_sms_instance
