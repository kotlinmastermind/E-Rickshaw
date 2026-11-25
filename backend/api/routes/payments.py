from typing import Optional, List
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, Request, Header, HTTPException, status
import logging
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user, get_current_customer, get_current_driver
from backend.domain.models import User, Payment, CustomerProfile, Ride
from backend.application.services.payment_service import PaymentService, transition_payment_status
from backend.application.services.settlement_advanced_service import SettlementAdvancedService

router = APIRouter(prefix="/payments", tags=["Payments & Settlements"])
logger = logging.getLogger("payments.api")
class RazorpayOrderRequest(BaseModel):
    amount_rupees: Optional[float] = None
    amount_cents: Optional[int] = None
    ride_id: Optional[str] = None
    receipt: Optional[str] = None
    notes: Optional[dict] = None

class RazorpayVerifyRequest(BaseModel):
    razorpay_order_id: str
    razorpay_payment_id: str
    razorpay_signature: str
    ride_id: Optional[str] = None

class PaymentIntentRequest(BaseModel):
    ride_id: str
    idempotency_key: str = Field(..., min_length=8)

class CashCollectRequest(BaseModel):
    ride_id: str

class TokenizeCardRequest(BaseModel):
    card_number: str
    exp_month: int
    exp_year: int
    cardholder_name: str
    issuer_bank: str = "HDFC Bank"
    is_default: bool = False

class SaveVpaRequest(BaseModel):
    vpa_address: str
    is_default: bool = False

class UpiIntentRequest(BaseModel):
    ride_id: str
    amount_cents: int
    custom_vpa: Optional[str] = None

class UpiRetryRequest(BaseModel):
    ride_id: str
    retry_attempt: int
    failure_code: Optional[str] = "U30_PAYMENT_FAILED"

class CorporateSettlementRequest(BaseModel):
    ride_id: str

class PartialRefundRequest(BaseModel):
    payment_id: str
    amount_cents: int
    reason: str
    is_partial: bool = True

@router.post("/intent")
async def create_payment_intent(
    payload: PaymentIntentRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = PaymentService(db)
    return await service.create_payment_intent(
        user_id=user.id,
        ride_id=payload.ride_id,
        idempotency_key=payload.idempotency_key
    )

@router.post("/cash-collect")
async def confirm_cash_collected(
    payload: CashCollectRequest,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = PaymentService(db)
    return await service.confirm_cash_collection(
        driver_user_id=user.id,
        ride_id=payload.ride_id
    )

@router.post("/webhook")
@router.post("/razorpay/webhook")
async def payment_webhook(
    request: Request,
    x_razorpay_signature: str = Header(None, alias="X-Razorpay-Signature"),
    db: AsyncSession = Depends(get_db)
):
    body = await request.body()
    service = PaymentService(db)
    return await service.process_webhook(raw_body=body, signature_header=x_razorpay_signature or "")

@router.get("/razorpay/webhook")
@router.get("/webhook")
async def payment_webhook_status():
    """Diagnostic health check for the Razorpay Webhook endpoint."""
    return {
        "status": "ready",
        "endpoints": [
            "/api/v1/payments/razorpay/webhook",
            "/api/v1/payments/webhook"
        ],
        "method": "POST",
        "signature_header": "X-Razorpay-Signature",
        "description": "Cryptographically verifies HMAC-SHA256 signature and captures payment states.",
        "active_provider": "RAZORPAY",
        "supported_events": [
            "payment.captured",
            "payment.authorized",
            "order.paid",
            "payment.failed",
            "refund.processed",
            "refund.created"
        ]
    }

@router.get("/razorpay/config")
async def get_razorpay_config():
    from backend.core.config import settings
    return {
        "key_id": settings.RAZORPAY_KEY_ID or "",
        "currency": "INR",
        "company_name": "VAISENTRA MOBILITY",
        "theme_color": "#0D9488",
        "is_live": not (settings.RAZORPAY_KEY_ID or "").startswith("rzp_test")
    }

@router.post("/razorpay/create-order")
@router.post("/create-order")
async def create_razorpay_order(
    payload: RazorpayOrderRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    import time
    from backend.core.config import settings
    from backend.integrations.payments.provider import get_payment_provider
    from backend.domain.models import Ride, Payment, CustomerProfile
    from sqlalchemy import select

    provider = get_payment_provider()

    # Task 027: Resolve customer profile without hardcoded GUIDs
    prof_stmt = select(CustomerProfile).where(CustomerProfile.user_id == user.id)
    prof = (await db.execute(prof_stmt)).scalar_one_or_none()
    if not prof:
        prof = CustomerProfile(
            user_id=user.id,
            preferred_payment_method="RAZORPAY",
            rating_average=5.0,
            rating_count=0
        )
        db.add(prof)
        await db.flush()
    customer_profile_id = prof.id

    # Task 028 & 029: Server-side fare calculation and ride ownership verification
    if payload.ride_id:
        ride_res = await db.execute(select(Ride).where(Ride.id == payload.ride_id))
        ride = ride_res.scalar_one_or_none()
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"Ride {payload.ride_id} not found.")

        if ride.customer_id != customer_profile_id and user.role != "admin":
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You do not own this ride.")

        amount_paise = int(ride.final_fare_cents or ride.estimated_fare_cents or 0)
        if amount_paise <= 0:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Journey fare calculation has not finalized.")
    elif payload.amount_cents:
        amount_paise = int(payload.amount_cents)
    elif payload.amount_rupees:
        amount_paise = int(round(payload.amount_rupees * 100))
    else:
        amount_paise = 10000  # Default ₹100 for topups

    receipt = payload.receipt or f"rcpt_{payload.ride_id or user.id[:8]}_{int(time.time())}"
    notes = payload.notes or {}
    notes["user_id"] = user.id
    notes["customer_id"] = customer_profile_id
    if payload.ride_id:
        notes["ride_id"] = payload.ride_id

    # Prevent overwriting an order or amount on an already settled/authorized payment
    if payload.ride_id:
        existing_pay = (
            await db.execute(select(Payment).where(Payment.ride_id == payload.ride_id))
        ).scalar_one_or_none()
        if existing_pay and existing_pay.status in ("CAPTURED", "AUTHORIZED", "PARTIALLY_REFUNDED", "REFUNDED", "PAID", "SETTLED"):
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"Cannot create new order: ride payment is already in status {existing_pay.status}.",
            )

    order = await provider.create_razorpay_order(
        amount_cents=amount_paise,
        currency="INR",
        receipt=receipt,
        notes=notes
    )

    # Task 026: Synchronize Payment in database with valid idempotency_key and customer_id
    if payload.ride_id:
        stmt = select(Payment).where(Payment.ride_id == payload.ride_id).with_for_update()
        res = await db.execute(stmt)
        payment = res.scalar_one_or_none()
        if not payment:
            payment = Payment(
                ride_id=payload.ride_id,
                customer_id=customer_profile_id,
                amount_cents=amount_paise,
                currency="INR",
                status="CREATED",
                payment_method="RAZORPAY",
                provider_name="RAZORPAY",
                provider_order_id=order.get("id"),
                idempotency_key=f"pay_{payload.ride_id}_{order.get('id')}"
            )
            db.add(payment)
        else:
            if payment.status in ("CAPTURED", "AUTHORIZED", "PARTIALLY_REFUNDED", "REFUNDED", "PAID", "SETTLED"):
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail=f"Cannot create new order: ride payment is already in status {payment.status}.",
                )
            if payment.status == "FAILED":
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Cannot reuse failed payment record; initialize a fresh payment intent.",
                )
            payment.provider_order_id = order.get("id")
            payment.amount_cents = amount_paise
            payment.provider_name = "RAZORPAY"
            if not payment.idempotency_key:
                payment.idempotency_key = f"pay_{payload.ride_id}_{order.get('id')}"
        await db.commit()

    return {
        "order_id": order.get("id"),
        "amount": order.get("amount", amount_paise),
        "amount_rupees": round(amount_paise / 100.0, 2),
        "currency": order.get("currency", "INR"),
        "key_id": settings.RAZORPAY_KEY_ID or "",
        "receipt": order.get("receipt", receipt),
        "status": order.get("status", "created")
    }

@router.post("/razorpay/verify")
@router.post("/verify")
async def verify_razorpay_payment(
    payload: RazorpayVerifyRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.integrations.payments.provider import get_payment_provider
    from backend.domain.models import Payment, Ride, CustomerProfile, PaymentEvent
    from sqlalchemy import select
    import json

    provider = get_payment_provider()

    # 1. Cryptographic HMAC-SHA256 signature verification
    is_valid = provider.verify_payment_signature(
        razorpay_order_id=payload.razorpay_order_id,
        razorpay_payment_id=payload.razorpay_payment_id,
        razorpay_signature=payload.razorpay_signature
    )
    if not is_valid:
        logger.error(
            "Razorpay signature mismatch: order_id=%s, payment_id=%s, user_id=%s",
            payload.razorpay_order_id, payload.razorpay_payment_id, user.id
        )
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Razorpay payment verification failed: Invalid HMAC-SHA256 signature."
        )

    # 2. Pre-fetch internal payment metadata without holding row locks across external HTTP
    async def _load_payment(*, lock: bool = False) -> Optional[Payment]:
        q = select(Payment).where(Payment.provider_order_id == payload.razorpay_order_id)
        if lock:
            q = q.with_for_update()
        found = (await db.execute(q)).scalar_one_or_none()
        if not found and payload.ride_id:
            q_ride = select(Payment).where(Payment.ride_id == payload.ride_id)
            if lock:
                q_ride = q_ride.with_for_update()
            found = (await db.execute(q_ride)).scalar_one_or_none()
        return found

    pre_payment = await _load_payment(lock=False)
    if not pre_payment:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"No payment record matches order {payload.razorpay_order_id}."
        )

    if payload.ride_id and pre_payment.ride_id and pre_payment.ride_id != payload.ride_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Specified ride_id does not match the payment order association."
        )

    if pre_payment.provider_order_id and pre_payment.provider_order_id != payload.razorpay_order_id:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Payment is bound to a different Razorpay order ID."
        )

    # 3. Ownership Verification
    prof = (
        await db.execute(select(CustomerProfile).where(CustomerProfile.user_id == user.id))
    ).scalar_one_or_none()
    if prof and pre_payment.customer_id and pre_payment.customer_id != prof.id and user.role != "admin":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You do not have permission to verify payment for this journey."
        )

    # 4. Early Idempotency Check
    if pre_payment.status in ("CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED", "PAID", "SETTLED"):
        if pre_payment.provider_payment_id and pre_payment.provider_payment_id != payload.razorpay_payment_id:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Payment is already settled with a different gateway payment ID."
            )
        return {
            "success": True,
            "message": "Payment was already verified and captured.",
            "payment_id": pre_payment.provider_payment_id or payload.razorpay_payment_id,
            "order_id": pre_payment.provider_order_id or payload.razorpay_order_id,
            "ride_id": pre_payment.ride_id or payload.ride_id,
            "status": pre_payment.status
        }

    # 5. Authoritative Razorpay Server-Side Fetch (outside DB write lock)
    try:
        gateway_payment = await provider.fetch_payment(
            payment_id=payload.razorpay_payment_id,
            expected_amount_cents=pre_payment.amount_cents,
            expected_order_id=payload.razorpay_order_id,
        )
    except Exception as exc:
        logger.error(
            "Failed fetching payment %s from Razorpay gateway: %s",
            payload.razorpay_payment_id, exc
        )
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Failed to verify payment status with gateway provider."
        )

    # 6. Strict Attribute & Security Validation
    gw_payment_id = gateway_payment.get("id")
    gw_order_id = gateway_payment.get("order_id")
    gw_amount = gateway_payment.get("amount")
    gw_currency = str(gateway_payment.get("currency") or "").upper()
    gw_status = str(gateway_payment.get("status") or "").lower()

    if gw_payment_id and gw_payment_id != payload.razorpay_payment_id:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Authoritative payment ID mismatch with gateway."
        )

    if gw_order_id != payload.razorpay_order_id:
        logger.critical(
            "SECURITY ANOMALY: Gateway order mismatch! Expected %s, got %s",
            payload.razorpay_order_id, gw_order_id
        )
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Authoritative payment does not correspond to expected Razorpay order."
        )

    if gw_currency != "INR":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid transaction currency: {gw_currency}. Must be INR."
        )

    # 7. Acquire Pessimistic Row Lock for Finalization
    payment = await _load_payment(lock=True)
    if not payment:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payment record no longer exists.")

    if gw_amount != payment.amount_cents:
        logger.critical(
            "SECURITY ANOMALY: Payment amount mismatch! DB expects %d paise, gateway reports %d paise",
            payment.amount_cents, gw_amount
        )
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Payment amount mismatch between client, order, and gateway."
        )

    if payment.status in ("CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED", "PAID", "SETTLED"):
        if payment.provider_payment_id and payment.provider_payment_id != payload.razorpay_payment_id:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Payment is already settled with a different gateway payment ID."
            )
        return {
            "success": True,
            "message": "Payment was already verified and captured.",
            "payment_id": payment.provider_payment_id or payload.razorpay_payment_id,
            "order_id": payment.provider_order_id or payload.razorpay_order_id,
            "ride_id": payment.ride_id or payload.ride_id,
            "status": payment.status
        }

    # Prevent gateway payment_id reuse across unrelated payments
    reused = (
        await db.execute(
            select(Payment).where(
                Payment.provider_payment_id == payload.razorpay_payment_id,
                Payment.id != payment.id,
            )
        )
    ).scalar_one_or_none()
    if reused:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Gateway payment ID is already bound to another transaction."
        )

    # 8. Gateway State Transition Handling via Centralized State Machine
    if gw_status == "authorized":
        try:
            transition_payment_status(payment, "AUTHORIZED", context="verify_razorpay_payment")
        except ValueError as err:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(err))
        payment.provider_payment_id = payload.razorpay_payment_id
        await db.commit()
        return {
            "success": True,
            "message": "Payment authorized by gateway; capture pending.",
            "payment_id": payload.razorpay_payment_id,
            "order_id": payload.razorpay_order_id,
            "ride_id": payment.ride_id or payload.ride_id,
            "status": "AUTHORIZED"
        }

    if gw_status != "captured":
        if gw_status == "failed":
            try:
                transition_payment_status(payment, "FAILED", context="verify_razorpay_payment")
            except ValueError as err:
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(err))
            payment.provider_payment_id = payload.razorpay_payment_id
            await db.commit()
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Authoritative gateway payment is not captured. Current status: {gw_status}"
        )

    # 9. Single-Source Capture Finalization + Atomic Commit
    payment_service = PaymentService(db)
    await payment_service.finalize_payment_capture(
        payment=payment,
        provider_payment_id=payload.razorpay_payment_id,
        provider_signature=payload.razorpay_signature,
        auto_commit=False,
    )

    target_ride_id = payment.ride_id or payload.ride_id
    if target_ride_id:
        r_res = await db.execute(select(Ride).where(Ride.id == target_ride_id).with_for_update())
        ride = r_res.scalar_one_or_none()
        if ride and ride.status in ["IN_PROGRESS", "ACCEPTED", "ARRIVED", "STARTED"]:
            now_comp = datetime.now(timezone.utc)
            ride.status = "COMPLETED"
            ride.completed_at = ride.completed_at or now_comp
            if ride.final_fare_cents is None:
                ride.final_fare_cents = payment.amount_cents or ride.estimated_fare_cents or 0
            if not ride.assigned_driver_id and payment.driver_id:
                ride.assigned_driver_id = payment.driver_id

    db.add(
        PaymentEvent(
            payment_id=payment.id,
            event_type="VERIFY_CAPTURED",
            payload_json=json.dumps({"order_id": payload.razorpay_order_id, "payment_id": payload.razorpay_payment_id}),
        )
    )

    try:
        await db.commit()
    except Exception as exc:
        await db.rollback()
        logger.error("Failed committing verified capture %s: %s", payment.id, exc)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Database commit failure during payment verification."
        )

    return {
        "success": True,
        "message": "Payment verified and recorded successfully.",
        "payment_id": payload.razorpay_payment_id,
        "order_id": payload.razorpay_order_id,
        "ride_id": target_ride_id,
        "status": "CAPTURED"
    }

# ----------------- Part 4 Saved Methods & Tokenization (Features 85 & 86) -----------------
@router.get("/saved-methods")
async def list_saved_payment_methods(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SettlementAdvancedService(db)
    return await service.list_saved_payment_methods(user.id)

@router.post("/tokenize-card")
async def tokenize_card(
    payload: TokenizeCardRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    # Under PCI DSS Req 3/4 & RBI Card-on-File regulations, raw card PAN must never traverse the backend application server.
    raise HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST,
        detail="Direct ingestion of raw card PAN on application servers is prohibited under PCI DSS. Tokenization must be performed client-side using Razorpay Checkout SDK."
    )

@router.post("/save-vpa")
async def save_upi_vpa(
    payload: SaveVpaRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SettlementAdvancedService(db)
    return await service.save_upi_vpa(
        user_id=user.id,
        vpa=payload.vpa_address,
        is_default=payload.is_default
    )

@router.delete("/saved-methods/{method_id}")
async def delete_saved_payment_method(
    method_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SettlementAdvancedService(db)
    return await service.delete_saved_method(method_id, user.id)

# ----------------- Part 4 UPI Deep-Link & Retries (Features 87 & 88) -----------------
@router.post("/upi-checkout")
@router.post("/upi-intent")
async def generate_upi_checkout(
    payload: UpiIntentRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SettlementAdvancedService(db)
    return await service.generate_upi_deep_link(
        ride_id=payload.ride_id,
        amount_cents=payload.amount_cents,
        custom_vpa=payload.custom_vpa
    )

@router.post("/upi-retry")
async def retry_upi_checkout(
    payload: UpiRetryRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SettlementAdvancedService(db)
    return await service.handle_upi_intent_retry(
        ride_id=payload.ride_id,
        retry_attempt=payload.retry_attempt,
        failure_code=payload.failure_code
    )

# ----------------- Part 4 Cashless Corporate Settlement (Feature 89) -----------------
@router.post("/corporate-settlement")
async def settle_corporate_ride(
    payload: CorporateSettlementRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = SettlementAdvancedService(db)
    return await service.process_corporate_billing(
        ride_id=payload.ride_id,
        user_id=user.id
    )

# ----------------- Part 4 Refunds (Features 94 & 95) -----------------
@router.post("/refunds/partial")
async def process_partial_refund(
    payload: PartialRefundRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(Payment).where(Payment.id == payload.payment_id)
    payment = (await db.execute(stmt)).scalar_one_or_none()
    if not payment:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payment record not found.")

    is_admin = getattr(user, "role", None) == "admin"
    is_owner = (payment.user_id == user.id)
    if not is_owner and not is_admin:
        prof_stmt = select(CustomerProfile).where(CustomerProfile.user_id == user.id)
        prof = (await db.execute(prof_stmt)).scalar_one_or_none()
        if prof and payment.customer_id == prof.id:
            is_owner = True

    if not is_admin and not is_owner:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Unauthorized: Only an administrator or the transaction owner can initiate refunds.")

    service = SettlementAdvancedService(db)
    return await service.create_refund(
        payment_id=payload.payment_id,
        amount_cents=payload.amount_cents,
        reason=payload.reason,
        is_partial=payload.is_partial
    )

@router.get("/refunds/{refund_id}/status")
async def get_refund_status(
    refund_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = SettlementAdvancedService(db)
    return await service.get_refund_status(refund_id)

@router.get("/refunds/my")
async def list_my_refunds(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = SettlementAdvancedService(db)
    return await service.list_customer_refunds(user.id)

class SplitPaymentRequest(BaseModel):
    ride_id: str
    co_passenger_phones: List[str]
    split_type: str = "EQUAL"

@router.post("/split")
async def split_payment(
    payload: SplitPaymentRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    service = SettlementAdvancedService(db)
    splits = await service.initiate_split_fare(
        ride_id=payload.ride_id,
        inviter_user_id=user.id,
        splits=payload.co_passenger_phones
    )
    return {
        "status": "SUCCESS",
        "message": f"Split fare initiated with {len(splits)} co-passengers.",
        "splits": splits
    }


