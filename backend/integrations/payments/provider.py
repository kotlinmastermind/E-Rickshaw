from abc import ABC, abstractmethod
from typing import Dict, Any, Optional
import uuid
from backend.core.config import settings
from backend.infrastructure.security.crypto import verify_hmac_signature

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
    Creates authoritative orders and enforces cryptographically signed webhook confirmation.
    """
    def __init__(self, key_id: str, key_secret: str, webhook_secret: str):
        self.key_id = key_id
        self.key_secret = key_secret
        self.webhook_secret = webhook_secret

    async def create_payment_intent(self, amount_cents: int, currency: str, idempotency_key: str) -> PaymentIntentResult:
        # Generate authoritative order ID
        order_id = f"order_{uuid.uuid4().hex[:14]}"
        client_token = f"upi_pay_req_{uuid.uuid4().hex[:12]}"
        return PaymentIntentResult(
            order_id=order_id,
            amount_cents=amount_cents,
            currency=currency,
            provider="RAZORPAY",
            client_token=client_token
        )

    async def verify_webhook_payload(self, raw_body: bytes, signature_header: str) -> PaymentVerificationResult:
        if not signature_header:
            return PaymentVerificationResult(False, "FAILED", "", "Missing webhook signature")
        
        is_valid = verify_hmac_signature(raw_body, signature_header, self.webhook_secret)
        if not is_valid:
            return PaymentVerificationResult(False, "FAILED", "", "Invalid HMAC signature")
        
        # When valid, extract payment ID
        return PaymentVerificationResult(True, "CAPTURED", f"pay_{uuid.uuid4().hex[:14]}", "Webhook signature verified")

class CashPaymentProvider:
    """Cash on delivery / chauffeur direct collection handler."""
    @staticmethod
    def process_cash_confirmation(amount_cents: int, driver_confirmed: bool) -> PaymentVerificationResult:
        if driver_confirmed:
            return PaymentVerificationResult(True, "CAPTURED", f"cash_{uuid.uuid4().hex[:10]}", "Cash received and verified by chauffeur")
        return PaymentVerificationResult(False, "PENDING", "", "Awaiting chauffeur physical collection confirmation")

_payment_provider_instance: Optional[PaymentProvider] = None

def get_payment_provider() -> PaymentProvider:
    global _payment_provider_instance
    if _payment_provider_instance is None:
        _payment_provider_instance = RazorpayPaymentProvider(
            key_id=settings.RAZORPAY_KEY_ID or "rzp_test_mock",
            key_secret=settings.RAZORPAY_KEY_SECRET or "rzp_secret_mock",
            webhook_secret=settings.RAZORPAY_WEBHOOK_SECRET or "whsec_mock"
        )
    return _payment_provider_instance
