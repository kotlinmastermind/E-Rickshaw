import os
from abc import ABC, abstractmethod
from typing import Dict, Any, Optional
import uuid
import hmac
import hashlib
import json
import logging
from fastapi import HTTPException
from backend.core.config import settings
from backend.infrastructure.security.crypto import verify_hmac_signature

logger = logging.getLogger(__name__)

class PaymentIntentResult:
    def __init__(self, order_id: str, amount_cents: int, currency: str, provider: str, client_token: str):
        self.order_id = order_id
        self.amount_cents = amount_cents
        self.currency = currency
        self.provider = provider
        self.client_token = client_token

    def to_dict(self) -> Dict[str, Any]:
        return {
            "order_id": self.order_id,
            "amount_cents": self.amount_cents,
            "currency": self.currency,
            "provider": self.provider,
            "client_token": self.client_token
        }

class PaymentVerificationResult:
    def __init__(self, is_valid: bool, status: str, payment_id: str, message: str = ""):
        self.is_valid = is_valid
        self.status = status  # CAPTURED, FAILED
        self.payment_id = payment_id
        self.message = message

class PaymentProvider(ABC):
    @abstractmethod
    async def create_payment_intent(self, amount_cents: int, currency: str, idempotency_key: str) -> PaymentIntentResult:
        pass

    @abstractmethod
    async def verify_webhook_payload(self, raw_body: bytes, signature_header: str) -> PaymentVerificationResult:
        pass

class RazorpayPaymentProvider(PaymentProvider):
    """
    Razorpay UPI / Card payment gateway boundary.
    Creates authoritative live orders and enforces cryptographically signed verification.
    """
    def __init__(
        self,
        key_id: str,
        key_secret: str,
        webhook_secret: str,
        previous_webhook_secret: str = "",
        merchant_account_id: str = "",
    ):
        self.key_id = key_id
        self.key_secret = key_secret
        self.webhook_secret = webhook_secret
        self.previous_webhook_secret = previous_webhook_secret
        self.merchant_account_id = merchant_account_id
        self._client = None
        try:
            import razorpay
            if self.key_id and self.key_secret:
                self._client = razorpay.Client(auth=(self.key_id, self.key_secret))
        except ImportError:
            pass

    async def create_razorpay_order(
        self,
        amount_cents: int,
        currency: str = "INR",
        receipt: Optional[str] = None,
        notes: Optional[Dict[str, str]] = None
    ) -> Dict[str, Any]:
        """
        Create a live Razorpay order directly with amount in paise (1 INR = 100 paise).
        """
        receipt_str = (receipt or f"rcpt_{uuid.uuid4().hex[:12]}")[:40]
        merged_notes = dict(notes or {
            "platform": "Vaisentra Mobility",
            "app": "E-Rickshaw Ride Hailing"
        })
        expected_merchant = (
            getattr(self, "merchant_account_id", None) or settings.RAZORPAY_MERCHANT_ACCOUNT_ID or ""
        ).strip()
        if expected_merchant:
            merged_notes["merchant_account_id"] = expected_merchant

        order_payload = {
            "amount": int(amount_cents),
            "currency": currency or "INR",
            "receipt": receipt_str,
            "notes": merged_notes,
        }

        # Avoid outbound network calls only when explicit synthetic test credentials are active
        if (
            os.getenv("TESTING") == "1"
            and (settings.ENVIRONMENT or "").strip().lower() != "production"
            and (not self.key_id or not self.key_secret or self.key_id.startswith("rzp_test_mock"))
        ):
            fallback_order_id = f"order_test_{uuid.uuid4().hex[:14]}"
            return {
                "id": fallback_order_id,
                "entity": "order",
                "amount": amount_cents,
                "amount_paid": 0,
                "amount_due": amount_cents,
                "currency": currency or "INR",
                "receipt": receipt_str,
                "status": "created",
                "attempts": 0,
                "notes": merged_notes,
                "created_at": 1730440000
            }

        # 1. Try Razorpay official SDK client
        if self._client:
            try:
                order = self._client.order.create(data=order_payload)
                logger.info(f"Razorpay live order created via SDK: {order.get('id')}")
                return order
            except Exception as e:
                logger.warning(f"Razorpay SDK order creation failed, trying HTTPX: {e}")

        # 2. Try HTTPX direct REST API with distinct handling of 4xx vs timeout/5xx
        import httpx
        url = "https://api.razorpay.com/v1/orders"
        try:
            async with httpx.AsyncClient() as client:
                resp = await client.post(
                    url,
                    json=order_payload,
                    auth=(self.key_id, self.key_secret),
                    timeout=10.0
                )
                if resp.status_code in [200, 201]:
                    data = resp.json()
                    logger.info(f"Razorpay live order created via HTTPX: {data.get('id')}")
                    return data
                logger.error(f"Razorpay HTTP order error {resp.status_code}: {resp.text}")
                if 400 <= resp.status_code < 500:
                    raise HTTPException(
                        status_code=status.HTTP_400_BAD_REQUEST if resp.status_code == 400 else resp.status_code,
                        detail=f"Razorpay order creation rejected by gateway ({resp.status_code}).",
                    )
                raise HTTPException(
                    status_code=502,
                    detail=f"Razorpay gateway upstream error ({resp.status_code}).",
                )
        except HTTPException:
            raise
        except httpx.TimeoutException as exc:
            logger.error(f"Razorpay order creation timeout: {exc}")
            raise HTTPException(
                status_code=504,
                detail="Razorpay order creation timed out; retry safely to reconcile before recreating.",
            )
        except Exception as e:
            logger.error(f"Razorpay HTTP request exception: {e}")
            raise HTTPException(
                status_code=502,
                detail="Razorpay payment gateway order creation failed. Verify credentials and connectivity."
            )

    async def create_payment_intent(self, amount_cents: int, currency: str, idempotency_key: str) -> PaymentIntentResult:
        order = await self.create_razorpay_order(
            amount_cents=amount_cents,
            currency=currency or "INR",
            receipt=idempotency_key[:40],
            notes={"idempotency_key": idempotency_key[:40]}
        )
        order_id = order.get("id") or f"order_{uuid.uuid4().hex[:14]}"
        return PaymentIntentResult(
            order_id=order_id,
            amount_cents=amount_cents,
            currency=currency or "INR",
            provider="RAZORPAY",
            client_token=order_id
        )

    def verify_payment_signature(
        self,
        razorpay_order_id: str,
        razorpay_payment_id: str,
        razorpay_signature: str
    ) -> bool:
        if not (razorpay_order_id and razorpay_payment_id and razorpay_signature and self.key_secret):
            return False

        try:
            msg = f"{razorpay_order_id}|{razorpay_payment_id}".encode("utf-8")
            expected_sig = hmac.new(self.key_secret.encode("utf-8"), msg, hashlib.sha256).hexdigest()
            return hmac.compare_digest(expected_sig.lower(), razorpay_signature.lower())
        except Exception as e:
            logger.error(f"Signature calculation failure: {e}")
            return False

    async def verify_webhook_payload_detailed(
        self, raw_body: bytes, signature_header: str
    ) -> tuple[PaymentVerificationResult, Optional[str]]:
        if not signature_header:
            return PaymentVerificationResult(False, "FAILED", "", "Missing webhook signature header."), None

        current_secret = (self.webhook_secret or "").strip()
        prev_secret = (
            getattr(self, "previous_webhook_secret", None)
            or settings.RAZORPAY_WEBHOOK_SECRET_PREVIOUS
            or ""
        ).strip()

        if not current_secret and not prev_secret:
            return PaymentVerificationResult(False, "FAILED", "", "Webhook secret is not configured."), None

        matched_slot: Optional[str] = None
        if current_secret and verify_hmac_signature(raw_body, signature_header, current_secret):
            matched_slot = "current"
        elif prev_secret and verify_hmac_signature(raw_body, signature_header, prev_secret):
            matched_slot = "previous"
            logger.warning(
                "Webhook verified via RAZORPAY_WEBHOOK_SECRET_PREVIOUS; retire previous secret once Razorpay retry window completes."
            )

        if not matched_slot:
            return PaymentVerificationResult(False, "FAILED", "", "Invalid HMAC-SHA256 signature."), None

        payment_id = ""
        try:
            data = json.loads(raw_body.decode("utf-8"))
            payment_id = data.get("payload", {}).get("payment", {}).get("entity", {}).get("id", "")
        except Exception:
            pass

        return PaymentVerificationResult(True, "CAPTURED", payment_id, f"Webhook verified ({matched_slot})"), matched_slot

    async def verify_webhook_payload(self, raw_body: bytes, signature_header: str) -> PaymentVerificationResult:
        result, _ = await self.verify_webhook_payload_detailed(raw_body, signature_header)
        return result

    async def fetch_payment(
        self,
        payment_id: str,
        expected_amount_cents: Optional[int] = None,
        expected_order_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Fetch authoritative live payment status directly from Razorpay API."""
        # Fallback for synthetic automated test suites without live gateway connectivity
        if (
            os.getenv("TESTING") == "1"
            and settings.ENVIRONMENT != "production"
            and (payment_id.startswith("pay_test_") or not self.key_id or not self.key_secret)
        ):
            result: Dict[str, Any] = {
                "id": payment_id,
                "entity": "payment",
                "amount": expected_amount_cents if expected_amount_cents is not None else 500000,
                "currency": "INR",
                "status": "captured",
            }
            if expected_order_id is not None:
                result["order_id"] = expected_order_id
            configured_acct = (getattr(self, "merchant_account_id", None) or settings.RAZORPAY_MERCHANT_ACCOUNT_ID or "").strip()
            if configured_acct:
                result["account_id"] = configured_acct
            return result

        if self._client:
            try:
                return self._client.payment.fetch(payment_id)
            except Exception as e:
                logger.warning(f"Razorpay SDK fetch failed, falling back to HTTPX: {e}")

        import httpx
        url = f"https://api.razorpay.com/v1/payments/{payment_id}"
        async with httpx.AsyncClient() as client:
            resp = await client.get(url, auth=(self.key_id, self.key_secret), timeout=10.0)
            if resp.status_code == 200:
                return resp.json()
            raise HTTPException(status_code=resp.status_code, detail=f"Razorpay API error: {resp.text}")

    async def create_refund(self, payment_id: str, amount_cents: int, notes: Optional[Dict[str, str]] = None) -> Dict[str, Any]:
        """Execute real gateway refund via Razorpay Refund API."""
        if (
            os.getenv("TESTING") == "1"
            and settings.ENVIRONMENT != "production"
            and (payment_id.startswith("pay_test_") or not self.key_id or not self.key_secret)
        ):
            return {
                "id": f"rfd_test_{uuid.uuid4().hex[:14]}",
                "entity": "refund",
                "amount": amount_cents,
                "currency": "INR",
                "payment_id": payment_id,
                "status": "processed",
                "notes": notes or {}
            }

        import httpx
        url = f"https://api.razorpay.com/v1/payments/{payment_id}/refund"
        payload = {"amount": amount_cents, "notes": notes or {}}
        async with httpx.AsyncClient() as client:
            resp = await client.post(url, json=payload, auth=(self.key_id, self.key_secret), timeout=10.0)
            if resp.status_code in [200, 201]:
                return resp.json()
            raise HTTPException(status_code=resp.status_code, detail=f"Razorpay Refund API error: {resp.text}")

    def validate_merchant_account(self, incoming_account_id: Optional[str]) -> tuple[bool, str]:
        """Fail-closed enforcement of configured Production Razorpay merchant account isolation."""
        expected = (getattr(self, "merchant_account_id", None) or settings.RAZORPAY_MERCHANT_ACCOUNT_ID or "").strip()
        is_prod = (settings.ENVIRONMENT or "").strip().lower() == "production"

        if not expected:
            if is_prod:
                return False, "CRITICAL SECURITY ERROR: Production RAZORPAY_MERCHANT_ACCOUNT_ID is not configured."
            return True, ""

        if not expected.startswith("acc_") or len(expected) < 14:
            return False, "Configured RAZORPAY_MERCHANT_ACCOUNT_ID is malformed."

        candidate = (incoming_account_id or "").strip()
        if not candidate:
            return False, (
                f"Webhook missing merchant account_id; expected configured production merchant account '{expected}'."
            )
        if not candidate.startswith("acc_") or len(candidate) < 14:
            return False, f"Webhook malformed merchant account_id '{candidate}'."

        if not hmac.compare_digest(candidate.encode("utf-8"), expected.encode("utf-8")):
            return False, (
                f"Webhook merchant account '{candidate}' does not match configured production merchant '{expected}'."
            )
        return True, ""

    async def fetch_order_payments(self, order_id: str) -> Dict[str, Any]:
        """Query Razorpay Payments linked to an Order ID for server-side reconciliation."""
        if not self.key_id or not self.key_secret:
            raise HTTPException(status_code=503, detail="Razorpay API credentials not configured.")
        import httpx
        url = f"https://api.razorpay.com/v1/orders/{order_id}/payments"
        async with httpx.AsyncClient() as client:
            resp = await client.get(url, auth=(self.key_id, self.key_secret), timeout=10.0)
            if resp.status_code == 200:
                return resp.json()
            raise HTTPException(status_code=resp.status_code, detail=f"Razorpay Order Payments API error: {resp.text}")

    async def fetch_orders_by_receipt(self, receipt: str) -> Dict[str, Any]:
        """Recover lost order creation responses by querying Razorpay orders matching our unique receipt."""
        if not self.key_id or not self.key_secret:
            return {"items": []}
        import httpx
        url = "https://api.razorpay.com/v1/orders"
        async with httpx.AsyncClient() as client:
            resp = await client.get(url, params={"receipt": receipt[:40]}, auth=(self.key_id, self.key_secret), timeout=10.0)
            if resp.status_code == 200:
                return resp.json()
            return {"items": []}

    async def fetch_refund(self, refund_id: str) -> Dict[str, Any]:
        """Fetch authoritative gateway refund status from Razorpay Refund API."""
        if (
            os.getenv("TESTING") == "1"
            and (settings.ENVIRONMENT or "").strip().lower() != "production"
            and refund_id.startswith("rfd_test_")
        ):
            return {"id": refund_id, "entity": "refund", "status": "processed"}
        if not self.key_id or not self.key_secret:
            raise HTTPException(status_code=503, detail="Razorpay API credentials not configured.")
        import httpx
        url = f"https://api.razorpay.com/v1/refunds/{refund_id}"
        async with httpx.AsyncClient() as client:
            resp = await client.get(url, auth=(self.key_id, self.key_secret), timeout=10.0)
            if resp.status_code == 200:
                return resp.json()
            raise HTTPException(status_code=resp.status_code, detail=f"Razorpay Refund Fetch API error: {resp.text}")

class CashPaymentProvider:
    """Cash on delivery / chauffeur direct collection handler."""
    @staticmethod
    def process_cash_confirmation(amount_cents: int, driver_confirmed: bool) -> PaymentVerificationResult:
        if driver_confirmed:
            return PaymentVerificationResult(True, "CAPTURED", f"cash_{uuid.uuid4().hex[:10]}", "Cash received and verified by chauffeur")
        return PaymentVerificationResult(False, "PENDING", "", "Awaiting chauffeur physical collection confirmation")

_payment_provider_instance: Optional[RazorpayPaymentProvider] = None
_last_provider_settings: Optional[tuple[str, str, str, str, str]] = None


def _rebuild_razorpay_sdk_client(provider: RazorpayPaymentProvider) -> None:
    provider._client = None
    try:
        import razorpay
        if provider.key_id and provider.key_secret:
            provider._client = razorpay.Client(auth=(provider.key_id, provider.key_secret))
    except ImportError:
        pass


def get_payment_provider() -> RazorpayPaymentProvider:
    global _payment_provider_instance, _last_provider_settings
    current_creds = (
        settings.RAZORPAY_KEY_ID or "",
        settings.RAZORPAY_KEY_SECRET or "",
        settings.RAZORPAY_WEBHOOK_SECRET or "",
        getattr(settings, "RAZORPAY_WEBHOOK_SECRET_PREVIOUS", None) or "",
        settings.RAZORPAY_MERCHANT_ACCOUNT_ID or "",
    )
    if _payment_provider_instance is None:
        _payment_provider_instance = RazorpayPaymentProvider(
            key_id=current_creds[0],
            key_secret=current_creds[1],
            webhook_secret=current_creds[2],
            previous_webhook_secret=current_creds[3],
            merchant_account_id=current_creds[4],
        )
        _last_provider_settings = current_creds
    elif current_creds != _last_provider_settings:
        _payment_provider_instance.key_id = current_creds[0]
        _payment_provider_instance.key_secret = current_creds[1]
        _payment_provider_instance.webhook_secret = current_creds[2]
        _payment_provider_instance.previous_webhook_secret = current_creds[3]
        _payment_provider_instance.merchant_account_id = current_creds[4]
        _rebuild_razorpay_sdk_client(_payment_provider_instance)
        _last_provider_settings = current_creds
    return _payment_provider_instance
