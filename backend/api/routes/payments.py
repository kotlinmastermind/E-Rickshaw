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

