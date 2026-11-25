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
    idempotency_key: str = Field(..., min_length=8, max_length=128)

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
    """Diagnostic health check reporting exact live Razorpay webhook event compatibility."""
    from backend.application.services.payment_service import (
        INFORMATIONAL_WEBHOOK_EVENTS,
        NOTIFICATION_WEBHOOK_EVENTS,
        SUPPORTED_DISPUTE_WEBHOOK_EVENTS,
        SUPPORTED_FINANCIAL_WEBHOOK_EVENTS,
        SUPPORTED_WEBHOOK_EVENTS,
    )

    return {
        "status": "ready",
        "endpoints": [
            "/api/v1/payments/razorpay/webhook",
            "/api/v1/payments/webhook"
        ],
        "method": "POST",
        "signature_header": "X-Razorpay-Signature",
        "description": "Cryptographically verifies raw-body HMAC-SHA256 signature (supporting secret overlap rotation) and processes financial, dispute, notification, and informational events.",
        "active_provider": "RAZORPAY",
        "supported_events": sorted(SUPPORTED_WEBHOOK_EVENTS),
        "event_categories": {
            "financial": sorted(SUPPORTED_FINANCIAL_WEBHOOK_EVENTS),
            "dispute": sorted(SUPPORTED_DISPUTE_WEBHOOK_EVENTS),
            "informational": sorted(INFORMATIONAL_WEBHOOK_EVENTS),
            "notification": sorted(NOTIFICATION_WEBHOOK_EVENTS),
        },
        "unknown_event_policy": "Persist in webhook inbox as FAILED and reject with HTTP 400 (no silent 200 on unknown financial events).",
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

    actor_user_id = user.id
    clean_idem = (payload.idempotency_key or "").strip() or None
    receipt = (
        payload.receipt
        or (f"rcpt_{hashlib.sha256(f'{actor_user_id}:{clean_idem}'.encode()).hexdigest()[:24]}" if clean_idem else f"rcpt_{uuid.uuid4().hex[:20]}")
    )
    scoped_digest = hashlib.sha256(f"GEN_ORD:{actor_user_id}:{clean_idem}".encode("utf-8")).hexdigest()[:48] if clean_idem else f"pend_{receipt}"[:48]
    scoped_idem = f"gen_{scoped_digest}"

    stmt_gen = select(Payment).where(Payment.idempotency_key == scoped_idem)
    payment = (await db.execute(stmt_gen)).scalar_one_or_none()
    if not payment:
        notes = dict(payload.notes or {})
        notes["user_id"] = actor_user_id
        payment = Payment(
            ride_id=None,
            user_id=actor_user_id,
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
        try:
            await db.commit()
            await db.refresh(payment)
        except IntegrityError:
            await db.rollback()
            payment = (await db.execute(stmt_gen)).scalar_one()

    if payment.user_id != actor_user_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Idempotency key belongs to another user.")
    requested_purpose = str((payload.notes or {}).get("purpose") or payment.purpose or "GENERAL_PAYMENT")
    if payment.purpose != requested_purpose:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Idempotency key reused with a different payment purpose.")
    if payment.amount_cents != amount_paise:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Idempotency key reused with a different amount.")

    notes = dict(payload.notes or {})
    notes["user_id"] = actor_user_id
    notes["payment_id"] = payment.id
    order_res = await PaymentService(db).execute_order_creation_with_lease(
        payment=payment,
        amount_cents=amount_paise,
        currency="INR",
        deterministic_receipt=receipt,
        idempotency_key=clean_idem,
        notes=notes,
        allow_supersede_on_amount_change=False,
    )
    if order_res.get("already_settled"):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Order for this idempotency key is already settled ({order_res.get('status')}).",
        )
    amt = int(order_res.get("amount_cents") or amount_paise)
    return {
        "order_id": order_res.get("order_id"),
        "payment_id": order_res.get("payment_id"),
        "amount": amt,
        "amount_cents": amt,
        "amount_rupees": round(amt / 100.0, 2),
        "currency": order_res.get("currency", "INR"),
        "key_id": settings.RAZORPAY_KEY_ID or "",
        "receipt": order_res.get("receipt", receipt),
        "status": "created",
        "reused_existing_order": bool(order_res.get("reused_existing_order")),
    }

@router.post("/razorpay/verify")
@router.post("/verify")
async def verify_razorpay_payment(
    payload: RazorpayVerifyRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    actor_user_id = user.id
    actor_role = getattr(user, "role", None)
    verify_res = await PaymentService(db).verify_checkout_and_finalize_capture(
        actor_user_id=actor_user_id,
        actor_role=actor_role,
        razorpay_order_id=payload.razorpay_order_id,
        razorpay_payment_id=payload.razorpay_payment_id,
        razorpay_signature=payload.razorpay_signature,
        ride_id=payload.ride_id,
    )

    if verify_res.get("already_processed"):
        return {
            "success": True,
            "message": "Payment was already verified and captured.",
            "payment_id": verify_res.get("payment_id"),
            "order_id": verify_res.get("order_id"),
            "ride_id": verify_res.get("ride_id"),
            "status": verify_res.get("payment_status"),
        }

    if verify_res.get("status") == "AUTHORIZED":
        return {
            "success": True,
            "message": "Payment authorized by gateway; capture pending.",
            "payment_id": verify_res.get("payment_id"),
            "order_id": verify_res.get("order_id"),
            "ride_id": verify_res.get("ride_id"),
            "status": "AUTHORIZED",
        }

    return {
        "success": True,
        "message": "Payment verified and recorded successfully.",
        "payment_id": verify_res.get("payment_id"),
        "order_id": verify_res.get("order_id"),
        "ride_id": verify_res.get("ride_id"),
        "status": "CAPTURED",
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


