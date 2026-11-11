import os
import re
import json
import logging
from abc import ABC, abstractmethod
from typing import Dict, Any, Optional
import httpx
from fastapi import HTTPException, status
from backend.core.config import settings

logger = logging.getLogger("sms_provider")


def normalize_e164_phone(phone: str) -> str:
    """
    Normalizes any input phone number into standard international E.164 format.
    Defaults 10-digit Indian numbers to +91 country code prefix.
    """
    if not phone:
        return ""
    clean = re.sub(r"[^\d+]", "", phone.strip())
    if not clean:
        return ""
    if clean.startswith("+"):
        return clean
    if len(clean) == 10:
        return f"+91{clean}"
    if len(clean) == 12 and clean.startswith("91"):
        return f"+{clean}"
    return f"+{clean}"


class SMSProvider(ABC):
    @abstractmethod
    async def send_verification_otp(self, phone_number: str) -> Dict[str, Any]:
        """Dispatches an OTP verification code via SMS."""
        pass

    @abstractmethod
    async def verify_otp(self, phone_number: str, otp_code: str) -> bool:
        """Verifies the OTP against the provider. Returns True if approved, False otherwise."""
        pass


class TwilioVerifyProvider(SMSProvider):
    """
    Twilio Verify v2 Integration Service.
    Uses official Twilio Verify Service API to dispatch telecom SMS passcodes
    and verify user-entered codes without any hardcoded bypasses.
    """
    def __init__(
        self,
        account_sid: Optional[str] = None,
        auth_token: Optional[str] = None,
        verify_service_sid: Optional[str] = None,
    ):
        self.account_sid = account_sid or settings.TWILIO_ACCOUNT_SID
        self.auth_token = auth_token or settings.TWILIO_AUTH_TOKEN
        self.verify_service_sid = verify_service_sid or settings.TWILIO_VERIFY_SERVICE_SID
        self.base_url = "https://verify.twilio.com/v2/Services"

    def _ensure_configured(self):
        if not self.account_sid or not self.auth_token or not self.verify_service_sid:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Twilio Verify Service credentials are not fully configured."
            )

    async def send_verification_otp(self, phone_number: str) -> Dict[str, Any]:
        """
        Calls Twilio Verify v2 Verifications endpoint to dispatch SMS OTP.
        """
        self._ensure_configured()
        e164_phone = normalize_e164_phone(phone_number)
        if len(e164_phone) < 10:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid mobile phone number format."
            )

        url = f"{self.base_url}/{self.verify_service_sid}/Verifications"
        data = {
            "To": e164_phone,
            "Channel": "sms"
        }

        auth = (self.account_sid, self.auth_token)
        async with httpx.AsyncClient(timeout=15.0) as client:
            try:
                response = await client.post(url, data=data, auth=auth)
            except httpx.RequestError as exc:
                logger.error(f"[TWILIO_VERIFY] Network error communicating with Twilio: {exc}")
                raise HTTPException(
                    status_code=status.HTTP_502_BAD_GATEWAY,
                    detail="Failed to connect to Twilio SMS gateway. Please check network connectivity."
                )

            if response.status_code in (200, 201):
                resp_json = response.json()
                logger.info(f"[TWILIO_VERIFY] Verification dispatched to {e164_phone[-4:].rjust(len(e164_phone), '*')}: {resp_json.get('sid')}")
                return {
                    "success": True,
                    "status": resp_json.get("status", "pending"),
                    "to": e164_phone,
                    "sid": resp_json.get("sid")
                }

            # Handle Twilio Errors
            resp_data = {}
            try:
                resp_data = response.json()
            except Exception:
                pass

            err_code = resp_data.get("code")
            err_msg = resp_data.get("message", response.text)
            logger.warning(f"[TWILIO_VERIFY_ERROR] HTTP {response.status_code} (Twilio Code {err_code}): {err_msg}")

            if response.status_code == 429 or err_code == 60203:
                raise HTTPException(
                    status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                    detail="Max send attempts reached for this phone number. Please wait before trying again."
                )
            if response.status_code == 400 and (err_code == 60200 or "Invalid parameter `To`" in err_msg):
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"The phone number {e164_phone} is not a valid mobile phone number."
                )

            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Twilio error: {err_msg}"
            )

    async def verify_otp(self, phone_number: str, otp_code: str) -> bool:
        """
        Calls Twilio Verify v2 VerificationCheck endpoint to validate user-entered code.
        STRICT FAIL-CLOSED: No test bypasses or simulated passes.
        """
        self._ensure_configured()
        e164_phone = normalize_e164_phone(phone_number)
        clean_code = otp_code.strip()

        if not clean_code:
            return False

        url = f"{self.base_url}/{self.verify_service_sid}/VerificationCheck"
        data = {
            "To": e164_phone,
            "Code": clean_code
        }

        auth = (self.account_sid, self.auth_token)
        async with httpx.AsyncClient(timeout=15.0) as client:
            try:
                response = await client.post(url, data=data, auth=auth)
            except httpx.RequestError as exc:
                logger.error(f"[TWILIO_VERIFY_CHECK] Network error contacting Twilio: {exc}")
                raise HTTPException(
                    status_code=status.HTTP_502_BAD_GATEWAY,
                    detail="Failed to connect to Twilio verification service."
                )

            if response.status_code == 200:
                resp_json = response.json()
                is_approved = resp_json.get("status") == "approved" and resp_json.get("valid") is True
                if is_approved:
                    logger.info(f"[TWILIO_VERIFY_APPROVED] Successfully verified {e164_phone[-4:].rjust(len(e164_phone), '*')}")
                    return True
                logger.warning(f"[TWILIO_VERIFY_REJECTED] Code check failed for {e164_phone[-4:].rjust(len(e164_phone), '*')}: status={resp_json.get('status')}")
                return False

            if response.status_code == 404:
                # Expired or no pending verification found
                logger.warning(f"[TWILIO_VERIFY_EXPIRED] No pending verification for {e164_phone}")
                return False

            resp_data = {}
            try:
                resp_data = response.json()
            except Exception:
                pass
            err_code = resp_data.get("code")
            logger.warning(f"[TWILIO_VERIFY_CHECK_ERROR] HTTP {response.status_code} Code {err_code}: {resp_data.get('message')}")
            return False


_sms_provider_instance: Optional[SMSProvider] = None

def get_sms_provider() -> SMSProvider:
    global _sms_provider_instance
    if _sms_provider_instance is None:
        _sms_provider_instance = TwilioVerifyProvider()
    return _sms_provider_instance

