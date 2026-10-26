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
        import httpx
        url = f"https://api.twilio.com/2010-04-01/Accounts/{self.account_sid}/Messages.json"
        data = {
            "To": phone_number,
            "From": self.from_number,
            "Body": f"Your E-Rickshaw Chauffeur authorization code is: {otp_code}. Valid for 5 minutes. Do not share."
        }
        async with httpx.AsyncClient() as client:
            try:
                resp = await client.post(url, data=data, auth=(self.account_sid, self.auth_token), timeout=10.0)
                if resp.status_code in [200, 201]:
                    logger.info("Twilio OTP sent successfully")
                    return True
                logger.error(f"Twilio API error {resp.status_code}: {resp.text}")
                return False
            except Exception as e:
                logger.error(f"Twilio network dispatch failed: {e}")
                return False

class Fast2SMSSMSProvider(SMSProvider):
    """
    Fast2SMS Gateway integration (India domestic SMS).
    Supports:
      1. Modern Fast2SMS Smart OTP API (direct delivery via OTP ID template)
      2. Quick transactional route (/dev/bulkV2) fallback
    """
    def __init__(self, api_key: str, otp_id: Optional[str] = None):
        self.api_key = api_key
        self.otp_id = otp_id

    async def send_otp(self, phone_number: str, otp_code: str) -> bool:
        if not self.api_key:
            logger.error("Fast2SMS API key missing. Refusing to send.")
            return False
        clean_phone = phone_number.replace("+91", "").replace("+", "").strip()
        import httpx

        # Priority 1: Fast2SMS Smart OTP Endpoint
        if self.otp_id:
            url = "https://www.fast2sms.com/dev/otp/send"
            headers = {
                "authorization": self.api_key,
                "Content-Type": "application/json"
            }
            payload = {
                "otp_id": self.otp_id,
                "mobile": clean_phone,
                "otp": otp_code
            }
            async with httpx.AsyncClient() as client:
                try:
                    resp = await client.post(url, json=payload, headers=headers, timeout=10.0)
                    if resp.status_code == 200:
                        data = resp.json()
                        if data.get("return") is True:
                            logger.info(f"Fast2SMS Smart OTP successfully dispatched to {clean_phone}")
                            return True
                    logger.warning(f"Fast2SMS Smart OTP returned status {resp.status_code}: {resp.text}")
                except Exception as e:
                    logger.error(f"Fast2SMS Smart OTP request failed: {e}")

        # Priority 2: Standard bulkV2 route
        url = "https://www.fast2sms.com/dev/bulkV2"
        headers = {
            "authorization": self.api_key,
            "Content-Type": "application/json"
        }
        payload = {
            "route": "otp",
            "variables_values": otp_code,
            "numbers": clean_phone
        }
        async with httpx.AsyncClient() as client:
            try:
                resp = await client.post(url, json=payload, headers=headers, timeout=10.0)
                if resp.status_code == 200:
                    data = resp.json()
                    is_success = data.get("return", False) is True
                    if is_success:
                        logger.info("Fast2SMS OTP dispatched successfully via bulkV2")
                    else:
                        logger.error(f"Fast2SMS API rejected request: {data}")
                    return is_success
                logger.error(f"Fast2SMS HTTP {resp.status_code}: {resp.text}")
                return False
            except Exception as e:
                logger.error(f"Fast2SMS dispatch failed: {e}")
                return False

_mock_sms_instance = MockSMSProvider()

def get_sms_provider() -> SMSProvider:
    if settings.SMS_PROVIDER == "twilio" and settings.TWILIO_ACCOUNT_SID:
        return TwilioSMSProvider(
            account_sid=settings.TWILIO_ACCOUNT_SID,
            auth_token=settings.TWILIO_AUTH_TOKEN or "",
            from_number=settings.TWILIO_FROM_NUMBER or ""
        )
    elif settings.SMS_PROVIDER == "fast2sms" and getattr(settings, "FAST2SMS_API_KEY", None):
        return Fast2SMSSMSProvider(
            api_key=settings.FAST2SMS_API_KEY,
            otp_id=getattr(settings, "FAST2SMS_OTP_ID", None)
        )
    return _mock_sms_instance
