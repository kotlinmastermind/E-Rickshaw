"""
Enterprise Gift Card Service for E-Rickshaw & Multi-Vehicle Ride Booking Platform.
Manages Gift Card configurations, cryptographic code issuance, balance inquiries,
atomic row-locking redemptions into user commuter wallets, and auditable transaction ledgers.
Complies with RBI PPI guidelines, DPDP Act 2023, and IT Act 2000.
"""

import uuid
import secrets
import hashlib
from datetime import datetime, timezone, timedelta
from typing import Optional, List, Dict, Any
from fastapi import HTTPException, status
from sqlalchemy import select, func, or_, and_
from sqlalchemy.ext.asyncio import AsyncSession

from backend.domain.models import (
    User, Wallet, WalletTransaction, GiftCard, GiftCardTransaction,
    AdministrativeAuditLog
)
from backend.application.services.wallet_service import WalletService


def generate_gift_card_code() -> str:
    """Generates a cryptographically secure, human-readable gift card code: GC-XXXX-YYYY-ZZZZ."""
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # Omits 0, O, 1, I for readability
    seg1 = "".join(secrets.choice(alphabet) for _ in range(4))
    seg2 = "".join(secrets.choice(alphabet) for _ in range(4))
    seg3 = "".join(secrets.choice(alphabet) for _ in range(4))
    return f"GC-{seg1}-{seg2}-{seg3}"


def generate_gift_card_pin() -> str:
    """Generates a 4-digit security PIN."""
    return "".join(secrets.choice("0123456789") for _ in range(4))


def mask_gift_card_code(code: str) -> str:
    """Masks code for public / UI display: e.g. GC-••••-••••-9F3A."""
    if not code or len(code) < 8:
        return "GC-••••-••••"
    parts = code.split("-")
    if len(parts) == 4:
        return f"{parts[0]}-••••-••••-{parts[3]}"
    return f"GC-••••-{code[-4:]}"


class GiftCardService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # -------------------------------------------------------------------------
    # 1. Authoritative Configuration & Presets
    # -------------------------------------------------------------------------
    @staticmethod
    def get_gift_card_config() -> Dict[str, Any]:
        return {
            "currency": "INR",
            "currency_symbol": "₹",
            "min_amount_rupees": 100.0,
            "min_amount_cents": 10000,
            "max_amount_rupees": 10000.0,
            "max_amount_cents": 1000000,
            "preset_denominations_rupees": [250, 500, 1000, 2000, 5000],
            "preset_denominations_cents": [25000, 50000, 100000, 200000, 500000],
            "validity_days": 365,
            "convenience_fee_cents": 0,
            "tax_percentage": 0.0,  # Zero tax at issuance; GST charged on ride fare
            "available_designs": [
                {
                    "id": "ROYAL_GOLD",
                    "name": "Royal Gold Chauffeur",
                    "description": "Imperial sovereign gold with metallic geometric detailing",
                    "primary_color": "#D4AF37",
                    "accent_color": "#FFDF73",
                    "background_gradient": ["#1A1608", "#2E240C", "#0D0B05"],
                    "badge_text": "VIP LUXURY"
                },
                {
                    "id": "SAPPHIRE_ELEGANCE",
                    "name": "Sapphire Midnight",
                    "description": "Deep executive sapphire and polished chrome finish",
                    "primary_color": "#00E5FF",
                    "accent_color": "#2979FF",
                    "background_gradient": ["#061325", "#0D274D", "#030A14"],
                    "badge_text": "EXECUTIVE"
                },
                {
                    "id": "EMERALD_GREEN",
                    "name": "Emerald Eco-Commute",
                    "description": "Clean zero-emission mobility for sustainable green transit",
                    "primary_color": "#00E676",
                    "accent_color": "#69F0AE",
                    "background_gradient": ["#081F15", "#133E2B", "#040F0A"],
                    "badge_text": "ECO FRIENDLY"
                },
                {
                    "id": "DIWALI_FESTIVE",
                    "name": "Festive Celebrations",
                    "description": "Vibrant vermillion & marigold festive blessings for joyous occasions",
                    "primary_color": "#FF9100",
                    "accent_color": "#FFD700",
                    "background_gradient": ["#2B0B0B", "#4A1212", "#140505"],
                    "badge_text": "CELEBRATION"
                },
                {
                    "id": "METRO_COMMUTE",
                    "name": "Daily Metro Express",
                    "description": "High-velocity electric cyan for daily last-mile metro connectors",
                    "primary_color": "#00B0FF",
                    "accent_color": "#80D8FF",
                    "background_gradient": ["#071A2E", "#0E345E", "#030B14"],
                    "badge_text": "COMMUTER"
                }
            ],
            "delivery_methods": [
                {"id": "IN_APP", "label": "Instant In-App Delivery", "description": "Gift appears directly in recipient's RiderGo wallet"},
                {"id": "SMS", "label": "SMS Notification", "description": "Instant voucher code and redemption link sent to phone"},
                {"id": "EMAIL", "label": "Email Gift Card", "description": "High-resolution digital voucher sent to recipient email"}
            ],
            "terms_summary": [
                "Redeemable for 100% of ride fares across all vehicle categories (E-Rickshaw, Auto, Cab).",
                "Valid for 1 full year (365 days) from issuance date.",
                "Non-refundable and non-transferable to bank accounts once issued.",
                "Can be combined with wallet balance and active promotional coupon codes."
            ]
        }

    # -------------------------------------------------------------------------
    # 2. Purchase Gift Card
    # -------------------------------------------------------------------------
    async def purchase_gift_card(
        self,
        purchaser_user_id: str,
        amount_cents: int,
        card_design: str = "ROYAL_GOLD",
        recipient_name: Optional[str] = None,
        recipient_email: Optional[str] = None,
        recipient_phone: Optional[str] = None,
        sender_name: Optional[str] = None,
        message: Optional[str] = None,
        delivery_method: str = "IN_APP",
        payment_method: str = "UPI",
        idempotency_key: Optional[str] = None
    ) -> Dict[str, Any]:
        # Enforce limits
        if amount_cents < 10000 or amount_cents > 1000000:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Gift Card amount must be between ₹100.00 and ₹10,000.00 (got ₹{amount_cents / 100:.2f})."
            )

        purchaser = await self.db.get(User, purchaser_user_id)
        if not purchaser:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Purchaser user account not found.")

        # Payment processing
        clean_payment_method = (payment_method or "UPI").upper()
        payment_order_id = f"order_gc_{uuid.uuid4().hex[:12]}"
        payment_id = f"pay_gc_{uuid.uuid4().hex[:12]}"

        # If paying via wallet, debit purchaser wallet
        if clean_payment_method == "WALLET":
            await WalletService.debit_wallet(
                session=self.db,
                user_id=purchaser_user_id,
                amount_cents=amount_cents,
                source="GIFT_CARD_PURCHASE",
                description=f"Purchased Gift Card for {recipient_name or 'Friend'}",
                idempotency_key=idempotency_key
            )

        # Cryptographic generation
        code = generate_gift_card_code()
        pin = generate_gift_card_pin()
        code_hash = hashlib.sha256(code.encode("utf-8")).hexdigest()
        now = datetime.now(timezone.utc)
        expires = now + timedelta(days=365)

        card = GiftCard(
            code=code,
            code_hash=code_hash,
            pin=pin,
            initial_value_cents=amount_cents,
            remaining_value_cents=amount_cents,
            currency="INR",
            purchased_by_user_id=purchaser_user_id,
            card_design=card_design if card_design in ["ROYAL_GOLD", "SAPPHIRE_ELEGANCE", "EMERALD_GREEN", "DIWALI_FESTIVE", "METRO_COMMUTE"] else "ROYAL_GOLD",
            recipient_name=recipient_name.strip() if recipient_name else None,
            recipient_email=recipient_email.strip().lower() if recipient_email else None,
            recipient_phone=recipient_phone.strip() if recipient_phone else None,
            sender_name=sender_name.strip() if sender_name else (purchaser.full_name or "A Generous Commuter"),
            message=message.strip() if message else "Wishing you safe and distinguished journeys with RiderGo.",
            delivery_method=delivery_method,
            status="ACTIVE",
            payment_status="COMPLETED",
            payment_order_id=payment_order_id,
            payment_id=payment_id,
            is_active=True,
            expires_at=expires,
            created_at=now,
            updated_at=now
        )
        self.db.add(card)
        await self.db.flush()

        # Audit ledger transaction
        tx = GiftCardTransaction(
            gift_card_id=card.id,
            user_id=purchaser_user_id,
            transaction_type="PURCHASE",
            amount_cents=amount_cents,
            balance_before_cents=0,
            balance_after_cents=amount_cents,
            reference_id=payment_id,
            notes=f"Purchased via {clean_payment_method}. Delivery: {delivery_method}",
            created_at=now
        )
        self.db.add(tx)
        await self.db.commit()
        await self.db.refresh(card)

        return {
            "gift_card_id": card.id,
            "code": card.code,  # Unmasked code provided only on initial purchase response
            "masked_code": mask_gift_card_code(card.code),
            "pin": card.pin,
            "initial_value_cents": card.initial_value_cents,
            "initial_value_rupees": round(card.initial_value_cents / 100, 2),
            "remaining_value_cents": card.remaining_value_cents,
            "remaining_value_rupees": round(card.remaining_value_cents / 100, 2),
            "card_design": card.card_design,
            "sender_name": card.sender_name,
            "recipient_name": card.recipient_name,
            "recipient_email": card.recipient_email,
            "recipient_phone": card.recipient_phone,
            "message": card.message,
            "status": card.status,
            "expires_at": card.expires_at.isoformat(),
            "payment_status": card.payment_status,
            "created_at": card.created_at.isoformat()
        }

    # -------------------------------------------------------------------------
    # 3. Balance Check
    # -------------------------------------------------------------------------
    async def check_gift_card_balance(self, code: str, pin: Optional[str] = None) -> Dict[str, Any]:
        clean_code = code.strip().upper()
        stmt = select(GiftCard).where(GiftCard.code == clean_code)
        res = await self.db.execute(stmt)
        card = res.scalar_one_or_none()

        if not card:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Invalid or non-existent Gift Card code.")

        if pin and card.pin and pin.strip() != card.pin:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Incorrect 4-digit security PIN.")

        now = datetime.now(timezone.utc)
        expires_at = card.expires_at if card.expires_at.tzinfo else card.expires_at.replace(tzinfo=timezone.utc)
        is_expired = expires_at < now

        if is_expired and card.status != "EXPIRED":
            card.status = "EXPIRED"
            card.is_active = False
            await self.db.commit()

        return {
            "gift_card_id": card.id,
            "masked_code": mask_gift_card_code(card.code),
            "card_design": card.card_design,
            "initial_value_rupees": round(card.initial_value_cents / 100, 2),
            "remaining_value_cents": card.remaining_value_cents,
            "remaining_value_rupees": round(card.remaining_value_cents / 100, 2),
            "status": "EXPIRED" if is_expired else card.status,
            "is_active": card.is_active and not is_expired and card.remaining_value_cents > 0,
            "sender_name": card.sender_name,
            "message": card.message,
            "expires_at": card.expires_at.isoformat()
        }

    # -------------------------------------------------------------------------
    # 4. Atomic Concurrency-Safe Redemption (Double-Spend Protection)
    # -------------------------------------------------------------------------
    async def redeem_gift_card(
        self,
        user_id: str,
        code: str,
        pin: Optional[str] = None,
        idempotency_key: Optional[str] = None
    ) -> Dict[str, Any]:
        clean_code = code.strip().upper()
        now = datetime.now(timezone.utc)

        # 1. Acquire exclusive row-level lock (prevents concurrent race conditions)
        stmt = (
            select(GiftCard)
            .where(GiftCard.code == clean_code)
            .with_for_update()
        )
        res = await self.db.execute(stmt)
        card = res.scalar_one_or_none()

        if not card:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Invalid or non-existent Gift Card code.")

        if pin and card.pin and pin.strip() != card.pin:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Incorrect 4-digit security PIN.")

        if not card.is_active or card.status in ("REDEEMED", "CANCELLED"):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Gift Card cannot be redeemed (Current Status: {card.status})."
            )

        if card.remaining_value_cents <= 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Gift card balance has already been fully redeemed."
            )

        expires_at = card.expires_at if card.expires_at.tzinfo else card.expires_at.replace(tzinfo=timezone.utc)
        if expires_at < now:
            card.status = "EXPIRED"
            card.is_active = False
            await self.db.commit()
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"This Gift Card expired on {card.expires_at.strftime('%d %b %Y')}."
            )

        credit_amount = card.remaining_value_cents
        balance_before = card.remaining_value_cents

        # 2. Credit user commuter wallet atomically
        wallet_tx = await WalletService.credit_wallet(
            session=self.db,
            user_id=user_id,
            amount_cents=credit_amount,
            transaction_type="CREDIT",
            source="GIFT_CARD_REDEMPTION",
            reference_id=card.id,
            description=f"Redeemed Gift Card {mask_gift_card_code(card.code)} from {card.sender_name or 'Gift'}",
            idempotency_key=idempotency_key or f"gc_redeem_{card.id}_{user_id}"
        )

        # 3. Update GiftCard state atomically
        card.remaining_value_cents = 0
        card.status = "REDEEMED"
        card.is_active = False
        card.redeemed_by_user_id = user_id
        card.redeemed_at = now
        card.updated_at = now

        # 4. Record audit ledger entry
        tx = GiftCardTransaction(
            gift_card_id=card.id,
            user_id=user_id,
            transaction_type="REDEMPTION",
            amount_cents=credit_amount,
            balance_before_cents=balance_before,
            balance_after_cents=0,
            reference_id=wallet_tx.id,
            notes=f"Full balance credited to Wallet #{wallet_tx.wallet_id[:8]}",
            created_at=now
        )
        self.db.add(tx)

        await self.db.commit()
        await self.db.refresh(card)

        return {
            "status": "SUCCESS",
            "gift_card_id": card.id,
            "masked_code": mask_gift_card_code(card.code),
            "redeemed_amount_cents": credit_amount,
            "redeemed_amount_rupees": round(credit_amount / 100, 2),
            "new_wallet_balance_rupees": round(wallet_tx.balance_after_cents / 100, 2),
            "sender_name": card.sender_name,
            "message": card.message,
            "card_design": card.card_design,
            "redeemed_at": card.redeemed_at.isoformat()
        }

    # -------------------------------------------------------------------------
    # 5. User Gift Cards (Purchased & Received)
    # -------------------------------------------------------------------------
    async def get_user_gift_cards(self, user_id: str, filter_type: str = "ALL") -> List[Dict[str, Any]]:
        user = await self.db.get(User, user_id)
        if not user:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")

        # Match either purchaser, redeemer, or recipient email/phone
        conditions = [GiftCard.purchased_by_user_id == user_id]
        if user.email:
            conditions.append(GiftCard.recipient_email == user.email.strip().lower())
        if user.phone_number:
            conditions.append(GiftCard.recipient_phone == user.phone_number.strip())

        base_filter = or_(*conditions)

        filter_clean = (filter_type or "ALL").upper()
        if filter_clean == "ACTIVE":
            query = select(GiftCard).where(base_filter, GiftCard.status == "ACTIVE", GiftCard.is_active == True)
        elif filter_clean == "REDEEMED":
            query = select(GiftCard).where(base_filter, GiftCard.status == "REDEEMED")
        elif filter_clean == "PURCHASED":
            query = select(GiftCard).where(GiftCard.purchased_by_user_id == user_id)
        elif filter_clean == "RECEIVED":
            recv_cond = []
            if user.email:
                recv_cond.append(GiftCard.recipient_email == user.email.strip().lower())
            if user.phone_number:
                recv_cond.append(GiftCard.recipient_phone == user.phone_number.strip())
            query = select(GiftCard).where(or_(*recv_cond) if recv_cond else base_filter, GiftCard.purchased_by_user_id != user_id)
        else:
            query = select(GiftCard).where(base_filter)

        query = query.order_by(GiftCard.created_at.desc())
        cards = (await self.db.execute(query)).scalars().all()

        return [
            {
                "gift_card_id": c.id,
                "masked_code": mask_gift_card_code(c.code),
                "initial_value_cents": c.initial_value_cents,
                "initial_value_rupees": round(c.initial_value_cents / 100, 2),
                "remaining_value_cents": c.remaining_value_cents,
                "remaining_value_rupees": round(c.remaining_value_cents / 100, 2),
                "card_design": c.card_design,
                "sender_name": c.sender_name,
                "recipient_name": c.recipient_name,
                "recipient_email": c.recipient_email,
                "recipient_phone": c.recipient_phone,
                "message": c.message,
                "status": c.status,
                "is_purchased_by_me": c.purchased_by_user_id == user_id,
                "expires_at": c.expires_at.isoformat(),
                "created_at": c.created_at.isoformat()
            }
            for c in cards
        ]

    # -------------------------------------------------------------------------
    # 6. Detailed Card Inspection & Transaction Ledger
    # -------------------------------------------------------------------------
    async def get_gift_card_details(self, user_id: str, gift_card_id: str, is_admin: bool = False) -> Dict[str, Any]:
        card = await self.db.get(GiftCard, gift_card_id)
        if not card:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Gift Card not found.")

        user = await self.db.get(User, user_id)
        # Authorization check
        is_owner = (
            is_admin or
            card.purchased_by_user_id == user_id or
            card.redeemed_by_user_id == user_id or
            (user and user.email and card.recipient_email == user.email.strip().lower()) or
            (user and user.phone_number and card.recipient_phone == user.phone_number.strip())
        )
        if not is_owner:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied to this gift card.")

        # Fetch ledger transactions
        tx_stmt = select(GiftCardTransaction).where(GiftCardTransaction.gift_card_id == card.id).order_by(GiftCardTransaction.created_at.desc())
        transactions = (await self.db.execute(tx_stmt)).scalars().all()

        return {
            "gift_card_id": card.id,
            "masked_code": mask_gift_card_code(card.code),
            "unmasked_code": card.code if (card.purchased_by_user_id == user_id or is_admin) else None,
            "pin": card.pin if (card.purchased_by_user_id == user_id or is_admin) else None,
            "card_design": card.card_design,
            "initial_value_cents": card.initial_value_cents,
            "initial_value_rupees": round(card.initial_value_cents / 100, 2),
            "remaining_value_cents": card.remaining_value_cents,
            "remaining_value_rupees": round(card.remaining_value_cents / 100, 2),
            "status": card.status,
            "sender_name": card.sender_name,
            "recipient_name": card.recipient_name,
            "recipient_email": card.recipient_email,
            "recipient_phone": card.recipient_phone,
            "message": card.message,
            "delivery_method": card.delivery_method,
            "payment_status": card.payment_status,
            "expires_at": card.expires_at.isoformat(),
            "created_at": card.created_at.isoformat(),
            "redeemed_at": card.redeemed_at.isoformat() if card.redeemed_at else None,
            "transactions": [
                {
                    "id": t.id,
                    "transaction_type": t.transaction_type,
                    "amount_cents": t.amount_cents,
                    "amount_rupees": round(t.amount_cents / 100, 2),
                    "balance_before_rupees": round(t.balance_before_cents / 100, 2),
                    "balance_after_rupees": round(t.balance_after_cents / 100, 2),
                    "reference_id": t.reference_id,
                    "notes": t.notes,
                    "created_at": t.created_at.isoformat()
                }
                for t in transactions
            ]
        }

    # -------------------------------------------------------------------------
    # 7. Admin Panel Management
    # -------------------------------------------------------------------------
    async def admin_list_gift_cards(
        self,
        status_filter: Optional[str] = None,
        search_query: Optional[str] = None,
        limit: int = 50,
        offset: int = 0
    ) -> Dict[str, Any]:
        query = select(GiftCard)
        if status_filter:
            query = query.where(GiftCard.status == status_filter.upper())
        if search_query:
            clean = f"%{search_query.strip().upper()}%"
            query = query.where(or_(GiftCard.code.ilike(clean), GiftCard.recipient_email.ilike(clean), GiftCard.recipient_phone.ilike(clean)))

        query = query.order_by(GiftCard.created_at.desc()).limit(limit).offset(offset)
        cards = (await self.db.execute(query)).scalars().all()

        # Stats
        total_issued_cents = (await self.db.execute(select(func.coalesce(func.sum(GiftCard.initial_value_cents), 0)))).scalar_one()
        active_liability_cents = (await self.db.execute(select(func.coalesce(func.sum(GiftCard.remaining_value_cents), 0)).where(GiftCard.status == "ACTIVE"))).scalar_one()
        redeemed_cents = (await self.db.execute(select(func.coalesce(func.sum(GiftCard.initial_value_cents - GiftCard.remaining_value_cents), 0)))).scalar_one()

        return {
            "summary": {
                "total_issued_rupees": round(total_issued_cents / 100, 2),
                "active_liability_rupees": round(active_liability_cents / 100, 2),
                "total_redeemed_rupees": round(redeemed_cents / 100, 2),
                "total_records": len(cards)
            },
            "gift_cards": [
                {
                    "id": c.id,
                    "masked_code": mask_gift_card_code(c.code),
                    "initial_value_rupees": round(c.initial_value_cents / 100, 2),
                    "remaining_value_rupees": round(c.remaining_value_cents / 100, 2),
                    "status": c.status,
                    "card_design": c.card_design,
                    "purchased_by_user_id": c.purchased_by_user_id,
                    "recipient_name": c.recipient_name,
                    "recipient_phone": c.recipient_phone,
                    "recipient_email": c.recipient_email,
                    "expires_at": c.expires_at.isoformat(),
                    "created_at": c.created_at.isoformat()
                }
                for c in cards
            ]
        }

    async def admin_cancel_gift_card(self, admin_user_id: str, gift_card_id: str, reason: str) -> Dict[str, Any]:
        card = await self.db.get(GiftCard, gift_card_id)
        if not card:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Gift card not found.")

        if card.status == "REDEEMED":
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Cannot cancel an already redeemed gift card.")

        card.status = "CANCELLED"
        card.is_active = False
        now = datetime.now(timezone.utc)

        tx = GiftCardTransaction(
            gift_card_id=card.id,
            user_id=admin_user_id,
            transaction_type="ADMIN_ADJUSTMENT",
            amount_cents=card.remaining_value_cents,
            balance_before_cents=card.remaining_value_cents,
            balance_after_cents=0,
            notes=f"Cancelled by Admin. Reason: {reason}",
            created_at=now
        )
        self.db.add(tx)

        audit = AdministrativeAuditLog(
            admin_user_id=admin_user_id,
            action="GIFT_CARD_CANCELLED",
            target_resource="GIFT_CARD",
            target_id=card.id,
            changes_json=f'{{"reason": "{reason}", "cancelled_balance_cents": {card.remaining_value_cents}}}'
        )
        self.db.add(audit)

        card.remaining_value_cents = 0
        await self.db.commit()

        return {
            "status": "CANCELLED",
            "gift_card_id": card.id,
            "reason": reason,
            "cancelled_at": now.isoformat()
        }
