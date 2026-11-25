from typing import Optional, List
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, Request, Header, HTTPException, status
import hashlib
import logging
import uuid
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user, get_current_customer, get_current_driver
from backend.domain.models import User, Payment, CustomerProfile, Ride, PaymentOrderAttempt, PaymentEvent, DriverProfile
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
    idempotency_key: Optional[str] = Field(None, min_length=8, max_length=128)

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
    idempotency_key: Optional[str] = Field(None, min_length=8, max_length=128)

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
    x_razorpay_account: Optional[str] = Header(None, alias="X-Razorpay-Account"),
    db: AsyncSession = Depends(get_db)
):
    body = await request.body()
    service = PaymentService(db)
    return await service.process_webhook(
        raw_body=body,
        signature_header=x_razorpay_signature or "",
        account_header=x_razorpay_account,
    )

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
            "refund.created",
            "refund.failed"
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
        "is_live": (settings.RAZORPAY_KEY_ID or "").startswith("rzp_live_"),
        "environment": settings.ENVIRONMENT,
        "webhook_secret_configured": bool(settings.RAZORPAY_WEBHOOK_SECRET),
        "merchant_account_configured": bool(settings.RAZORPAY_MERCHANT_ACCOUNT_ID),
    }

@router.post("/razorpay/create-order")
@router.post("/create-order")
async def create_razorpay_order(
    payload: RazorpayOrderRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.core.config import settings
    from backend.integrations.payments.provider import get_payment_provider
    from backend.domain.models import Ride, Payment, CustomerProfile

    provider = get_payment_provider()

    if payload.ride_id:
        ride = (await db.execute(select(Ride).where(Ride.id == payload.ride_id))).scalar_one_or_none()
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"Ride {payload.ride_id} not found.")
        prof = (await db.execute(select(CustomerProfile).where(CustomerProfile.user_id == user.id))).scalar_one_or_none()
        if (not prof or ride.customer_id != prof.id) and user.role != "admin":
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You do not own this ride.")

        if user.role == "admin" and (not prof or ride.customer_id != prof.id):
            cust_prof = (await db.execute(select(CustomerProfile).where(CustomerProfile.id == ride.customer_id))).scalar_one_or_none()
            target_user_id = cust_prof.user_id if cust_prof else user.id
        else:
            target_user_id = user.id

        idem = (payload.idempotency_key or payload.receipt or f"ride_ord_{payload.ride_id}").strip()
        service = PaymentService(db)
        intent_res = await service.create_payment_intent(
            user_id=target_user_id,
            ride_id=payload.ride_id,
            idempotency_key=idem,
        )
        if intent_res.get("already_settled"):
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"Cannot create new order: ride payment is already in status {intent_res.get('status')}.",
            )
        amt = int(intent_res.get("amount_cents") or 0)
        return {
            "order_id": intent_res.get("order_id"),
            "payment_id": intent_res.get("payment_id"),
            "amount": amt,
            "amount_cents": amt,
            "amount_rupees": round(amt / 100.0, 2),
            "currency": intent_res.get("currency", "INR"),
            "key_id": settings.RAZORPAY_KEY_ID or "",
            "receipt": payload.receipt or f"ride_{payload.ride_id.replace('-', '')[:24]}",
            "status": "created",
            "reused_existing_order": bool(intent_res.get("reused_existing_order")),
        }

    # Generic non-ride Razorpay order creation with DB persistence and idempotency
    if payload.amount_cents is not None:
        amount_paise = int(payload.amount_cents)
    elif payload.amount_rupees is not None:
        amount_paise = int(round(payload.amount_rupees * 100))
    else:
        amount_paise = 10000

    if amount_paise <= 0:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Order amount must be positive.")

    clean_idem = (payload.idempotency_key or "").strip() or None
    receipt = (
        payload.receipt
        or (f"rcpt_{hashlib.sha256(f'{user.id}:{clean_idem}'.encode()).hexdigest()[:24]}" if clean_idem else f"rcpt_{uuid.uuid4().hex[:20]}")
    )
    scoped_idem = f"gen_ord_{user.id}_{clean_idem}" if clean_idem else f"gen_pend_{receipt}"

    if clean_idem:
        existing_gen = (
            await db.execute(select(Payment).where(Payment.idempotency_key == scoped_idem).with_for_update())
        ).scalar_one_or_none()
        if existing_gen:
            if existing_gen.user_id != user.id:
                raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Idempotency key belongs to another user.")
            if existing_gen.amount_cents != amount_paise:
                raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Idempotency key reused with a different amount.")
            if existing_gen.provider_order_id:
                return {
                    "order_id": existing_gen.provider_order_id,
                    "payment_id": existing_gen.id,
                    "amount": existing_gen.amount_cents,
                    "amount_cents": existing_gen.amount_cents,
                    "amount_rupees": round(existing_gen.amount_cents / 100.0, 2),
                    "currency": existing_gen.currency,
                    "key_id": settings.RAZORPAY_KEY_ID or "",
                    "receipt": receipt,
                    "status": "created",
                    "reused_existing_order": True,
                }

    notes = dict(payload.notes or {})
    notes["user_id"] = user.id
    payment = Payment(
        ride_id=None,
        user_id=user.id,
        customer_id=None,
        amount_cents=amount_paise,
        currency="INR",
        status="CREATED",
        purpose=str(notes.get("purpose") or "GENERAL_PAYMENT"),
        payment_method="RAZORPAY",
        provider_name="RAZORPAY",
        merchant_account_id=(settings.RAZORPAY_MERCHANT_ACCOUNT_ID or "").strip() or None,
        idempotency_key=scoped_idem,
    )
    db.add(payment)
    await db.commit()
    await db.refresh(payment)

    order = await provider.create_razorpay_order(
        amount_cents=amount_paise,
        currency="INR",
        receipt=receipt,
        notes=notes,
    )
    locked_pay = (await db.execute(select(Payment).where(Payment.id == payment.id).with_for_update())).scalar_one()
    locked_pay.provider_order_id = order.get("id")
    if order.get("id"):
        db.add(
            PaymentOrderAttempt(
                payment_id=locked_pay.id,
                provider_order_id=order.get("id"),
                receipt=receipt,
                idempotency_key=clean_idem,
                amount_cents=amount_paise,
                currency="INR",
                merchant_account_id=locked_pay.merchant_account_id,
                status="CREATED",
            )
        )
    await db.commit()

    return {
        "order_id": order.get("id"),
        "payment_id": locked_pay.id,
        "amount": order.get("amount", amount_paise),
        "amount_cents": order.get("amount", amount_paise),
        "amount_rupees": round(amount_paise / 100.0, 2),
        "currency": order.get("currency", "INR"),
        "key_id": settings.RAZORPAY_KEY_ID or "",
        "receipt": order.get("receipt", receipt),
        "status": order.get("status", "created"),
        "reused_existing_order": False,
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
        if not found:
            att = (
                await db.execute(
                    select(PaymentOrderAttempt).where(
                        PaymentOrderAttempt.provider_order_id == payload.razorpay_order_id
                    )
                )
            ).scalar_one_or_none()
            if att:
                q_att = select(Payment).where(Payment.id == att.payment_id)
                if lock:
                    q_att = q_att.with_for_update()
                found = (await db.execute(q_att)).scalar_one_or_none()
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

    matched_pre_attempt = None
    if pre_payment.provider_order_id and pre_payment.provider_order_id != payload.razorpay_order_id:
        matched_pre_attempt = (
            await db.execute(
                select(PaymentOrderAttempt).where(
                    PaymentOrderAttempt.payment_id == pre_payment.id,
                    PaymentOrderAttempt.provider_order_id == payload.razorpay_order_id,
                )
            )
        ).scalar_one_or_none()
        if not matched_pre_attempt:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Payment is bound to a different Razorpay order ID."
            )
        if pre_payment.status in ("CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED", "PAID", "SETTLED"):
            if pre_payment.provider_payment_id and pre_payment.provider_payment_id != payload.razorpay_payment_id:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Payment is already settled on another order."
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

    expected_verify_amount = (
        matched_pre_attempt.amount_cents if matched_pre_attempt else pre_payment.amount_cents
    )

    # 5. Authoritative Razorpay Server-Side Fetch (outside DB write lock)
    try:
        gateway_payment = await provider.fetch_payment(
            payment_id=payload.razorpay_payment_id,
            expected_amount_cents=expected_verify_amount,
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
    gw_account_id = gateway_payment.get("account_id")

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

    acct_ok, acct_reason = provider.validate_merchant_account(gw_account_id)
    if not acct_ok:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=acct_reason)

    if gw_currency != "INR":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid transaction currency: {gw_currency}. Must be INR."
        )

    # 7. Acquire Pessimistic Row Lock for Finalization
    payment = await _load_payment(lock=True)
    if not payment:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payment record no longer exists.")

    if gw_account_id and not payment.merchant_account_id:
        payment.merchant_account_id = gw_account_id

    expected_locked_amount = (
        matched_pre_attempt.amount_cents if matched_pre_attempt else payment.amount_cents
    )
    if gw_amount != expected_locked_amount:
        logger.critical(
            "SECURITY ANOMALY: Payment amount mismatch! DB expects %d paise, gateway reports %d paise",
            expected_locked_amount, gw_amount
        )
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Payment amount mismatch between client, order, and gateway."
        )

    if matched_pre_attempt and payment.provider_order_id != payload.razorpay_order_id and gw_status == "captured":
        payment.provider_order_id = payload.razorpay_order_id
        payment.amount_cents = matched_pre_attempt.amount_cents

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
        is_partial=payload.is_partial,
        idempotency_key=payload.idempotency_key,
        allow_debt_clawback=is_admin,
        actor_admin_id=user.id if is_admin else None,
    )


@router.get("/ride/{ride_id}/status")
async def get_ride_payment_status(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Authoritative backend ride payment verification status for Customer, Assigned Driver, and Admin."""
    ride = (await db.execute(select(Ride).where(Ride.id == ride_id))).scalar_one_or_none()
    if not ride:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found.")

    cust = (await db.execute(select(CustomerProfile).where(CustomerProfile.user_id == user.id))).scalar_one_or_none()
    drv = (await db.execute(select(DriverProfile).where(DriverProfile.user_id == user.id))).scalar_one_or_none()
    is_admin = getattr(user, "role", None) in ("admin", "superadmin", "super administrator")
    is_customer = bool(cust and ride.customer_id == cust.id)
    is_driver = bool(drv and ride.assigned_driver_id == drv.id)
    if not (is_admin or is_customer or is_driver):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Unauthorized to view payment status for this ride.")

    payment = (await db.execute(select(Payment).where(Payment.ride_id == ride_id))).scalar_one_or_none()
    if not payment:
        return {
            "ride_id": ride_id,
            "payment_id": None,
            "status": "UNPAID",
            "payment_method": ride.payment_method,
            "provider_name": None,
            "amount_cents": int(ride.final_fare_cents or ride.estimated_fare_cents or 0),
            "refunded_amount_cents": 0,
            "provider_order_id": None,
            "provider_payment_id": None,
            "gateway_verified": False,
        }

    settled_states = {"CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED", "PAID", "SETTLED", "SUCCESS"}
    return {
        "ride_id": ride_id,
        "payment_id": payment.id,
        "status": payment.status,
        "payment_method": payment.payment_method,
        "provider_name": payment.provider_name,
        "amount_cents": payment.amount_cents,
        "refunded_amount_cents": int(payment.refunded_amount_cents or 0),
        "provider_order_id": payment.provider_order_id,
        "provider_payment_id": payment.provider_payment_id,
        "gateway_verified": payment.status in settled_states,
    }

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


