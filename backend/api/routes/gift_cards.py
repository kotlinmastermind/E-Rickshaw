"""
Gift Cards API Endpoints for E-Rickshaw & Multi-Vehicle Ride Booking Platform.
Provides gift card configuration, purchase, balance checking, atomic redemption,
user gift card listing, and administrative governance.
"""

from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user, require_role
from backend.domain.models import User
from backend.application.services.gift_card_service import GiftCardService

router = APIRouter(prefix="/gift-cards", tags=["Gift Cards (Commuter Credits)"])


# --- Schemas ---

class GiftCardPurchasePayload(BaseModel):
    amount_cents: int = Field(..., gt=0, description="Amount in integer cents (e.g. 50000 for ₹500.00)")
    card_design: str = Field("ROYAL_GOLD", description="Visual theme ID: ROYAL_GOLD, SAPPHIRE_ELEGANCE, EMERALD_GREEN, DIWALI_FESTIVE, METRO_COMMUTE")
    recipient_name: Optional[str] = Field(None, max_length=128)
    recipient_email: Optional[str] = Field(None, max_length=255)
    recipient_phone: Optional[str] = Field(None, max_length=20)
    sender_name: Optional[str] = Field(None, max_length=128)
    message: Optional[str] = Field(None, max_length=500)
    delivery_method: str = Field("IN_APP", description="IN_APP, SMS, or EMAIL")
    payment_method: str = Field("UPI", description="UPI, WALLET, or CARD")
    idempotency_key: str = Field(..., min_length=8, max_length=128)


class GiftCardBalancePayload(BaseModel):
    code: str = Field(..., min_length=4, max_length=64, description="Gift Card code (e.g. GC-XXXX-YYYY-ZZZZ)")
    pin: Optional[str] = Field(None, max_length=8, description="Optional 4-digit security PIN")


class GiftCardRedeemPayload(BaseModel):
    code: str = Field(..., min_length=4, max_length=64, description="Gift Card code to redeem")
    pin: Optional[str] = Field(None, max_length=8, description="Optional 4-digit security PIN")
    idempotency_key: Optional[str] = None


class AdminCancelPayload(BaseModel):
    reason: str = Field(..., min_length=3, max_length=255)


# --- Endpoints ---

@router.get("/config")
async def get_gift_card_config():
    """Returns authoritative denominations, design themes, validity rules, and delivery options."""
    return GiftCardService.get_gift_card_config()


@router.post("/purchase")
async def purchase_gift_card(
    payload: GiftCardPurchasePayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Purchases a new gift card with payment validation and cryptographic code generation."""
    service = GiftCardService(db)
    return await service.purchase_gift_card(
        purchaser_user_id=current_user.id,
        amount_cents=payload.amount_cents,
        card_design=payload.card_design,
        recipient_name=payload.recipient_name,
        recipient_email=payload.recipient_email,
        recipient_phone=payload.recipient_phone,
        sender_name=payload.sender_name,
        message=payload.message,
        delivery_method=payload.delivery_method,
        payment_method=payload.payment_method,
        idempotency_key=payload.idempotency_key
    )


@router.post("/check-balance")
async def check_gift_card_balance(
    payload: GiftCardBalancePayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Checks the live remaining balance and validity of a gift card code."""
    service = GiftCardService(db)
    return await service.check_gift_card_balance(code=payload.code, pin=payload.pin)


@router.post("/redeem")
async def redeem_gift_card(
    payload: GiftCardRedeemPayload,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Atomically redeems a gift card into the commuter wallet with row-level locking."""
    service = GiftCardService(db)
    return await service.redeem_gift_card(
        user_id=current_user.id,
        code=payload.code,
        pin=payload.pin,
        idempotency_key=payload.idempotency_key
    )


@router.get("/my")
async def get_my_gift_cards(
    filter_type: str = Query("ALL", regex="^(ALL|ACTIVE|REDEEMED|PURCHASED|RECEIVED)$"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Lists gift cards purchased or received by the current user."""
    service = GiftCardService(db)
    return await service.get_user_gift_cards(user_id=current_user.id, filter_type=filter_type)


@router.get("/{gift_card_id}")
async def get_gift_card_details(
    gift_card_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Fetches details and audit transaction history for a gift card."""
    service = GiftCardService(db)
    is_admin = current_user.role.lower() in ("admin", "superadmin")
    return await service.get_gift_card_details(user_id=current_user.id, gift_card_id=gift_card_id, is_admin=is_admin)


# --- Admin Governance Endpoints ---

@router.get("/admin/overview")
async def admin_get_gift_cards(
    status_filter: Optional[str] = Query(None, regex="^(ACTIVE|REDEEMED|EXPIRED|CANCELLED)$"),
    search: Optional[str] = None,
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    current_user: User = Depends(require_role(["admin", "superadmin"])),
    db: AsyncSession = Depends(get_db)
):
    """Admin dashboard: summary liabilities, card inventory, and audit search."""
    service = GiftCardService(db)
    return await service.admin_list_gift_cards(
        status_filter=status_filter,
        search_query=search,
        limit=limit,
        offset=offset
    )


@router.post("/admin/{gift_card_id}/cancel")
async def admin_cancel_gift_card(
    gift_card_id: str,
    payload: AdminCancelPayload,
    current_user: User = Depends(require_role(["admin", "superadmin"])),
    db: AsyncSession = Depends(get_db)
):
    """Admin action: Cancels an unredeemed gift card with audit logging."""
    service = GiftCardService(db)
    return await service.admin_cancel_gift_card(
        admin_user_id=current_user.id,
        gift_card_id=gift_card_id,
        reason=payload.reason
    )
