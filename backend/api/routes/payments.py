from fastapi import APIRouter, Depends, Request, Header, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_customer, get_current_driver
from backend.domain.models import User
from backend.application.services.payment_service import PaymentService

router = APIRouter(prefix="/payments", tags=["Payments & Settlements"])

class PaymentIntentRequest(BaseModel):
    ride_id: str
    idempotency_key: str = Field(..., min_length=8)

class CashCollectRequest(BaseModel):
    ride_id: str

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
