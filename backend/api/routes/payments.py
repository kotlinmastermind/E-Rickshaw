from typing import Optional
from fastapi import APIRouter, Depends, Request, Header, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user, get_current_customer, get_current_driver
from backend.domain.models import User
from backend.application.services.payment_service import PaymentService
from backend.application.services.settlement_advanced_service import SettlementAdvancedService

router = APIRouter(prefix="/payments", tags=["Payments & Settlements"])

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
async def payment_webhook(
    request: Request,
    x_razorpay_signature: str = Header(None, alias="X-Razorpay-Signature"),
    db: AsyncSession = Depends(get_db)
):
    body = await request.body()
    service = PaymentService(db)
    return await service.process_webhook(raw_body=body, signature_header=x_razorpay_signature or "")

@router.get("/razorpay/config")
async def get_razorpay_config():
    from backend.core.config import settings
    return {
        "key_id": settings.RAZORPAY_KEY_ID or "rzp_live_TunGmzfpWlcatO",
        "currency": "INR",
        "company_name": "Luxury Chauffeur Mobility",
        "theme_color": "#D4AF37",
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
    from backend.domain.models import Ride, Payment
    from sqlalchemy import select

    provider = get_payment_provider()
    
    # Calculate amount in paise (1 INR = 100 paise)
    if payload.amount_cents:
        amount_paise = int(payload.amount_cents)
    elif payload.amount_rupees:
        amount_paise = int(round(payload.amount_rupees * 100))
    elif payload.ride_id:
        ride_res = await db.execute(select(Ride).where(Ride.id == payload.ride_id))
        ride = ride_res.scalar_one_or_none()
        if ride:
            amount_paise = int(ride.final_fare_cents or ride.estimated_fare_cents or 10000)
        else:
            amount_paise = 10000
    else:
        amount_paise = 10000 # default ₹100

    receipt = payload.receipt or f"rcpt_{payload.ride_id or user.id[:8]}_{int(time.time())}"
    notes = payload.notes or {}
    notes["user_id"] = user.id
    if payload.ride_id:
        notes["ride_id"] = payload.ride_id

    order = await provider.create_razorpay_order(
        amount_cents=amount_paise,
        currency="INR",
        receipt=receipt,
        notes=notes
    )

    # If ride_id is given, synchronize Payment in database
    if payload.ride_id:
        stmt = select(Payment).where(Payment.ride_id == payload.ride_id)
        res = await db.execute(stmt)
        payment = res.scalar_one_or_none()
        if not payment:
            payment = Payment(
                ride_id=payload.ride_id,
                customer_id=user.id,
                amount_cents=amount_paise,
                currency="INR",
                status="CREATED",
                payment_method="RAZORPAY",
                provider_name="RAZORPAY",
                provider_order_id=order.get("id")
            )
            db.add(payment)
        else:
            payment.provider_order_id = order.get("id")
            payment.amount_cents = amount_paise
            payment.provider_name = "RAZORPAY"
        await db.commit()

    return {
        "order_id": order.get("id"),
        "amount": order.get("amount", amount_paise),
        "amount_rupees": round(amount_paise / 100.0, 2),
        "currency": order.get("currency", "INR"),
        "key_id": settings.RAZORPAY_KEY_ID or "rzp_live_TunGmzfpWlcatO",
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
    from backend.domain.models import Payment, PaymentEvent, Ride
    from sqlalchemy import select

    provider = get_payment_provider()
    is_valid = provider.verify_payment_signature(
        razorpay_order_id=payload.razorpay_order_id,
        razorpay_payment_id=payload.razorpay_payment_id,
        razorpay_signature=payload.razorpay_signature
    )

    if not is_valid:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Razorpay payment verification failed: Invalid HMAC-SHA256 signature."
        )

    # Find and update Payment in DB
    payment = None
    if payload.ride_id:
        p_res = await db.execute(select(Payment).where(Payment.ride_id == payload.ride_id))
        payment = p_res.scalar_one_or_none()
    
    if not payment and payload.razorpay_order_id:
        p_res = await db.execute(select(Payment).where(Payment.provider_order_id == payload.razorpay_order_id))
        payment = p_res.scalar_one_or_none()

    if payment:
        payment.status = "CAPTURED"
        payment.provider_payment_id = payload.razorpay_payment_id
        payment.provider_signature = payload.razorpay_signature
        event = PaymentEvent(
            payment_id=payment.id,
            event_type="PAYMENT_CAPTURED",
            payload_json=f'{{"order_id": "{payload.razorpay_order_id}", "payment_id": "{payload.razorpay_payment_id}", "user_id": "{user.id}"}}'
        )
        db.add(event)

    if payload.ride_id:
        r_res = await db.execute(select(Ride).where(Ride.id == payload.ride_id))
        ride = r_res.scalar_one_or_none()
        if ride:
            ride.status = "COMPLETED"

    await db.commit()

    return {
        "success": True,
        "message": "Payment verified and recorded successfully.",
        "payment_id": payload.razorpay_payment_id,
        "order_id": payload.razorpay_order_id,
        "ride_id": payload.ride_id,
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
    service = SettlementAdvancedService(db)
    return await service.tokenize_and_save_card(
        user_id=user.id,
        card_number=payload.card_number,
        exp_month=payload.exp_month,
        exp_year=payload.exp_year,
        cardholder_name=payload.cardholder_name,
        issuer_bank=payload.issuer_bank,
        is_default=payload.is_default
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

