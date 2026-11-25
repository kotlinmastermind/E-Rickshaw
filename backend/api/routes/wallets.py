"""
Wallet & Ledger API Routes - Production Financial Security Architecture
Enforces cryptographic Razorpay order binding, server-side signature verification,
pessimistic row locking, idempotent DB-backed order persistence, and strict separation
between ride payments and wallet top-ups.
"""
import hashlib
from typing import Optional, List, Dict, Any
import uuid
from fastapi import APIRouter, Depends, HTTPException, status, Query
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user, get_current_admin
from backend.domain.models import User, Payment, PaymentEvent, PaymentOrderAttempt
from backend.application.services.wallet_service import WalletService
from backend.application.services.payment_service import PaymentService
from backend.integrations.payments.provider import get_payment_provider
from backend.core.config import settings

router = APIRouter(prefix="/wallets", tags=["Wallet & Ledger"])

MIN_TOPUP_CENTS = 10000     # ₹100.00
MAX_TOPUP_CENTS = 1000000   # ₹10,000.00

class TopupCreateOrderRequest(BaseModel):
    amount_cents: int = Field(..., ge=MIN_TOPUP_CENTS, le=MAX_TOPUP_CENTS, description="Amount in paise (1 INR = 100 paise)")
    currency: str = Field("INR", pattern="^INR$")
    idempotency_key: Optional[str] = Field(None, min_length=8, max_length=128)

class TopupVerifyRequest(BaseModel):
    razorpay_order_id: str = Field(..., min_length=10)
    razorpay_payment_id: str = Field(..., min_length=10)
    razorpay_signature: str = Field(..., min_length=20)

class CompensateRequest(BaseModel):
    original_tx_id: Optional[str] = None
    user_id: Optional[str] = None
    amount_cents: Optional[int] = Field(None, gt=0)
    reason: str = Field(..., min_length=10)
    idempotency_key: Optional[str] = None


@router.get("/me")
async def get_my_wallet(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Retrieve authenticated commuter's authoritative balance from database."""
    return await WalletService.get_balance(db, user.id)


@router.post("/topup/create-order")
async def create_wallet_topup_order(
    payload: TopupCreateOrderRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Stage 1: Generates or idempotently reuses a cryptographically bound Razorpay Order
    for Wallet Top-up using an atomic DB lease to prevent concurrent duplicate orders.
    """
    actor_user_id = user.id
    clean_idem = (payload.idempotency_key or "").strip() or None
    receipt_id = (
        f"wtop_{hashlib.sha256(f'{actor_user_id}:{clean_idem}'.encode()).hexdigest()[:24]}"
        if clean_idem
        else f"wtop_{uuid.uuid4().hex[:20]}"
    )
    if clean_idem:
        idem_digest = hashlib.sha256(f"WALLET_TOPUP:{actor_user_id}:{clean_idem}".encode("utf-8")).hexdigest()[:48]
        scoped_idem = f"wtop_{idem_digest}"
    else:
        scoped_idem = f"wtop_pending_{receipt_id}"

    stmt = select(Payment).where(Payment.idempotency_key == scoped_idem)
    payment = (await db.execute(stmt)).scalar_one_or_none()
    if not payment:
        payment = Payment(
            ride_id=None,
            user_id=actor_user_id,
            customer_id=None,
            amount_cents=payload.amount_cents,
            currency=payload.currency,
            status="CREATED",
            purpose="WALLET_TOPUP",
            payment_method="RAZORPAY",
            provider_name="RAZORPAY",
            provider_order_id=None,
            merchant_account_id=(settings.RAZORPAY_MERCHANT_ACCOUNT_ID or "").strip() or None,
            idempotency_key=scoped_idem,
        )
        db.add(payment)
        try:
            await db.commit()
            await db.refresh(payment)
        except IntegrityError:
            await db.rollback()
            payment = (await db.execute(stmt)).scalar_one()

    if payment.user_id != actor_user_id or payment.purpose != "WALLET_TOPUP":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Idempotency key belongs to a different account or payment purpose.",
        )
    if payment.amount_cents != payload.amount_cents or payment.currency.upper() != payload.currency.upper():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Idempotency key conflict: top-up amount or currency differs from original request.",
        )

    payment_id_str = payment.id
    order_res = await PaymentService(db).execute_order_creation_with_lease(
        payment=payment,
        amount_cents=payload.amount_cents,
        currency=payload.currency,
        deterministic_receipt=receipt_id,
        idempotency_key=clean_idem,
        notes={
            "user_id": actor_user_id,
            "payment_id": payment_id_str,
            "purpose": "WALLET_TOPUP",
            "platform": "E-Rickshaw Mobility",
        },
        allow_supersede_on_amount_change=False,
    )
    if order_res.get("already_settled"):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Wallet top-up order for this idempotency key has already been settled.",
        )

    amt = int(order_res.get("amount_cents") or payload.amount_cents)
    return {
        "payment_id": order_res.get("payment_id"),
        "order_id": order_res.get("order_id"),
        "amount": amt,
        "amount_cents": amt,
        "amount_rupees": round(amt / 100.0, 2),
        "currency": order_res.get("currency", payload.currency),
        "key_id": settings.RAZORPAY_KEY_ID or "",
        "receipt": order_res.get("receipt", receipt_id),
        "status": "CREATED",
        "reused_existing_order": bool(order_res.get("reused_existing_order")),
    }


@router.post("/topup/verify")
async def verify_wallet_topup(
    payload: TopupVerifyRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Stage 2: Cryptographically verifies Razorpay payment signature, validates authoritative
    gateway payment fields fail-closed, and commits atomic credit to Wallet Ledger via
    the unified backend verification and capture engine.
    """
    actor_user_id = user.id
    actor_role = getattr(user, "role", None)
    verify_res = await PaymentService(db).verify_checkout_and_finalize_capture(
        actor_user_id=actor_user_id,
        actor_role=actor_role,
        razorpay_order_id=payload.razorpay_order_id,
        razorpay_payment_id=payload.razorpay_payment_id,
        razorpay_signature=payload.razorpay_signature,
        expected_purpose="WALLET_TOPUP",
    )

    balance_info = await WalletService.get_balance(db, actor_user_id)
    if verify_res.get("already_processed"):
        return {
            "status": "ALREADY_PROCESSED",
            "message": "Payment was already verified and credited.",
            "payment_id": verify_res.get("payment_id"),
            "order_id": verify_res.get("order_id"),
            "balance_cents": balance_info["balance_cents"],
            "balance_rupees": balance_info["balance_cents"] / 100.0,
        }

    return {
        "status": "SUCCESS",
        "message": "Payment verified and credited to commuter wallet.",
        "payment_id": verify_res.get("payment_id"),
        "order_id": verify_res.get("order_id"),
        "amount_rupees": round(int(verify_res.get("amount_cents") or 0) / 100.0, 2),
        "balance_cents": balance_info["balance_cents"],
        "balance_rupees": balance_info["balance_cents"] / 100.0,
    }


@router.post("/topup")
@router.post("/top-up")
async def legacy_topup_rejection():
    """Permanent removal of unverified client-side credit route."""
    raise HTTPException(
        status_code=status.HTTP_410_GONE,
        detail="Direct wallet top-up without gateway verification is permanently deprecated. Use /topup/create-order and /topup/verify."
    )


@router.get("/history")
async def get_wallet_history(
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Retrieve immutable double-entry ledger history."""
    txs = await WalletService.get_history(db, user.id, limit=limit, offset=offset)
    return [
        {
            "id": tx.id,
            "transaction_type": tx.transaction_type,
            "source": tx.source,
            "amount_cents": tx.amount_cents,
            "amount_rupees": round(tx.amount_cents / 100.0, 2),
            "balance_after_cents": tx.balance_after_cents,
            "balance_after_rupees": round(tx.balance_after_cents / 100.0, 2),
            "reference_id": tx.reference_id,
            "description": tx.description,
            "created_at": tx.created_at.isoformat()
        }
        for tx in txs
    ]


@router.post("/admin/compensate")
async def create_compensating_transaction(
    payload: CompensateRequest,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Administrative ledger adjustment with mandatory audit logging and justification."""
    if not (payload.original_tx_id or (payload.user_id and payload.amount_cents)):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Either original_tx_id or user_id and amount_cents must be provided."
        )

    if payload.original_tx_id:
        comp_tx = await WalletService.create_compensating_entry(
            session=db,
            original_tx_id=payload.original_tx_id,
            reason=payload.reason,
            admin_user_id=admin.id
        )
    else:
        if payload.amount_cents > 500000:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Administrative single compensation limit exceeded (maximum ₹5,000.00 / 500,000 paise)."
            )
        comp_digest = hashlib.sha256(
            f"{payload.user_id}:{payload.amount_cents}:{payload.reason.strip()}".encode("utf-8")
        ).hexdigest()[:48]
        scoped_comp_idem = (payload.idempotency_key or "").strip()[:64] or f"adm_comp_{comp_digest}"
        comp_tx = await WalletService.credit_wallet(
            session=db,
            user_id=payload.user_id,
            amount_cents=payload.amount_cents,
            transaction_type="ADMIN_COMPENSATION",
            source="ADMIN_CONCIERGE",
            reference_id=f"adm_{admin.id[:8]}",
            description=f"Admin compensation: {payload.reason}",
            idempotency_key=scoped_comp_idem,
        )

    await db.commit()
    return {
        "status": "COMPENSATED",
        "transaction_id": comp_tx.id,
        "amount_rupees": round(comp_tx.amount_cents / 100.0, 2),
        "balance_after_rupees": round(comp_tx.balance_after_cents / 100.0, 2),
        "authorized_by": admin.email
    }