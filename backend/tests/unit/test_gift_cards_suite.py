"""
Comprehensive Unit Tests for Feature 7: Gift Cards Suite.
Validates:
1. Gift card configuration & theme designs.
2. Gift card purchase & cryptographic code/PIN generation.
3. Denomination & limit validation (₹100 min, ₹10,000 max).
4. Balance inquiry & code masking.
5. Atomic row-locked redemption into commuter wallet.
6. Double-spend & race-condition prevention.
7. Expiration enforcement.
8. User portfolio filtering (purchased, received, active).
9. Admin governance overview & cancellation ledger.
"""

import uuid
import pytest
from datetime import datetime, timezone, timedelta
from fastapi import HTTPException
from sqlalchemy import select

from backend.domain.models import User, GiftCard, GiftCardTransaction, Wallet
from backend.application.services.gift_card_service import GiftCardService
from backend.application.services.wallet_service import WalletService


@pytest.mark.asyncio
async def test_get_gift_card_config(db_session):
    """Test 1: Verify authoritative gift card configuration and allowed design themes."""
    config = GiftCardService.get_gift_card_config()

    assert config["currency"] == "INR"
    assert config["min_amount_rupees"] == 100
    assert config["max_amount_rupees"] == 10000
    assert 250 in config["preset_denominations_rupees"]
    assert 500 in config["preset_denominations_rupees"]
    assert 1000 in config["preset_denominations_rupees"]
    assert len(config["available_designs"]) >= 4
    design_ids = [d["id"] for d in config["available_designs"]]
    assert "ROYAL_GOLD" in design_ids
    assert "SAPPHIRE_ELEGANCE" in design_ids


@pytest.mark.asyncio
async def test_purchase_gift_card_success(db_session):
    """Test 2: Purchase gift card creates record, SHA-256 hash, PIN, and issuance transaction."""
    uid = uuid.uuid4().hex[:6]
    purchaser = User(
        phone_number=f"+91981{uid}1",
        full_name="Gift Buyer",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add(purchaser)
    await db_session.flush()

    service = GiftCardService(db_session)
    res = await service.purchase_gift_card(
        purchaser_user_id=purchaser.id,
        amount_cents=50000,  # ₹500
        card_design="ROYAL_GOLD",
        recipient_name="Amit Sharma",
        recipient_email="amit@example.com",
        recipient_phone="+919876543210",
        message="Happy Birthday Amit! Enjoy green EV rides.",
        delivery_method="SMS"
    )

    assert res["status"] == "ACTIVE"
    assert res["initial_value_cents"] == 50000
    assert res["initial_value_rupees"] == 500.0
    assert res["code"].startswith("GC-")
    assert len(res["pin"]) == 4
    assert res["recipient_name"] == "Amit Sharma"

    # Verify persistence in DB
    gc = await db_session.get(GiftCard, res["gift_card_id"])
    assert gc is not None
    assert gc.code_hash is not None
    assert gc.remaining_value_cents == 50000
    assert gc.status == "ACTIVE"

    # Verify transaction ledger entry
    stmt = select(GiftCardTransaction).where(GiftCardTransaction.gift_card_id == gc.id)
    txs = (await db_session.execute(stmt)).scalars().all()
    assert len(txs) == 1
    assert txs[0].transaction_type == "PURCHASE"
    assert txs[0].amount_cents == 50000
    assert txs[0].balance_after_cents == 50000


@pytest.mark.asyncio
async def test_wallet_gift_card_purchase_retry_is_idempotent(db_session):
    """A retry returns the same issued card and never debits the wallet twice."""
    uid = uuid.uuid4().hex[:6]
    purchaser = User(
        phone_number=f"+91982{uid}1",
        full_name="Wallet Gift Buyer",
        role="customer",
        is_active=True,
        is_verified=True,
    )
    db_session.add(purchaser)
    await db_session.flush()
    await WalletService.credit_wallet(
        session=db_session,
        user_id=purchaser.id,
        amount_cents=50000,
        source="TEST_SEED",
        idempotency_key=f"gift_seed_{uuid.uuid4().hex[:12]}",
    )
    await db_session.commit()

    service = GiftCardService(db_session)
    request = {
        "purchaser_user_id": purchaser.id,
        "amount_cents": 50000,
        "card_design": "ROYAL_GOLD",
        "recipient_name": "Retry Recipient",
        "payment_method": "WALLET",
        "idempotency_key": "giftcard_wallet_retry_001",
    }
    first = await service.purchase_gift_card(**request)
    replay = await service.purchase_gift_card(**request)

    assert replay["gift_card_id"] == first["gift_card_id"]
    assert replay["code"] == first["code"]
    assert replay["pin"] == first["pin"]

    wallet = (await db_session.execute(
        select(Wallet).where(Wallet.user_id == purchaser.id)
    )).scalar_one()
    assert wallet.balance_cents == 0

    cards = (await db_session.execute(
        select(GiftCard).where(GiftCard.purchased_by_user_id == purchaser.id)
    )).scalars().all()
    assert len(cards) == 1

    purchase_rows = (await db_session.execute(
        select(GiftCardTransaction).where(
            GiftCardTransaction.gift_card_id == first["gift_card_id"],
            GiftCardTransaction.transaction_type == "PURCHASE",
        )
    )).scalars().all()
    assert len(purchase_rows) == 1


@pytest.mark.asyncio
async def test_purchase_gift_card_limit_validation(db_session):
    """Test 3: Enforce min/max limits."""
    uid = uuid.uuid4().hex[:6]
    purchaser = User(
        phone_number=f"+91981{uid}2",
        full_name="Limit Tester",
        role="customer",
        is_active=True
    )
    db_session.add(purchaser)
    await db_session.flush()

    service = GiftCardService(db_session)

    # Below min (₹50)
    with pytest.raises(HTTPException) as exc_min:
        await service.purchase_gift_card(
            purchaser_user_id=purchaser.id,
            amount_cents=5000,
            card_design="ROYAL_GOLD"
        )
    assert exc_min.value.status_code == 400
    assert "between" in exc_min.value.detail.lower()

    # Above max (₹15,000)
    with pytest.raises(HTTPException) as exc_max:
        await service.purchase_gift_card(
            purchaser_user_id=purchaser.id,
            amount_cents=1500000,
            card_design="ROYAL_GOLD"
        )
    assert exc_max.value.status_code == 400
    assert "between" in exc_max.value.detail.lower()


@pytest.mark.asyncio
async def test_check_gift_card_balance(db_session):
    """Test 4: Balance inquiry returns masked code and requires correct PIN when checked."""
    uid = uuid.uuid4().hex[:6]
    purchaser = User(phone_number=f"+91981{uid}3", full_name="Sender", role="customer", is_active=True)
    db_session.add(purchaser)
    await db_session.flush()

    service = GiftCardService(db_session)
    bought = await service.purchase_gift_card(
        purchaser_user_id=purchaser.id,
        amount_cents=100000,  # ₹1000
        card_design="SAPPHIRE_ELEGANCE"
    )

    code = bought["code"]
    pin = bought["pin"]

    # Successful balance check
    bal = await service.check_gift_card_balance(code=code, pin=pin)
    assert bal["is_active"] is True
    assert bal["status"] == "ACTIVE"
    assert bal["remaining_value_rupees"] == 1000.0
    assert bal["masked_code"].startswith("GC-")
    assert "••••" in bal["masked_code"]

    # Incorrect PIN
    with pytest.raises(HTTPException) as exc_pin:
        await service.check_gift_card_balance(code=code, pin="9999")
    assert exc_pin.value.status_code == 400
    assert "PIN" in exc_pin.value.detail

    # Non-existent code
    with pytest.raises(HTTPException) as exc_none:
        await service.check_gift_card_balance(code="GC-0000-0000-0000", pin="1234")
    assert exc_none.value.status_code == 404


@pytest.mark.asyncio
async def test_redeem_gift_card_atomic(db_session):
    """Test 5: Atomic redemption credits user wallet, updates status, and logs transaction."""
    uid1 = uuid.uuid4().hex[:6]
    uid2 = uuid.uuid4().hex[:6]

    purchaser = User(phone_number=f"+91981{uid1}4", full_name="Gift Giver", role="customer", is_active=True)
    redeemer = User(phone_number=f"+91981{uid2}5", full_name="Gift Redeemer", role="customer", is_active=True)
    db_session.add_all([purchaser, redeemer])
    await db_session.flush()

    service = GiftCardService(db_session)
    bought = await service.purchase_gift_card(
        purchaser_user_id=purchaser.id,
        amount_cents=75000,  # ₹750
        card_design="EMERALD_GREEN"
    )

    # Redeem into redeemer's wallet
    redemption = await service.redeem_gift_card(
        user_id=redeemer.id,
        code=bought["code"],
        pin=bought["pin"]
    )

    assert redemption["status"] == "SUCCESS"
    assert redemption["redeemed_amount_rupees"] == 750.0
    assert redemption["new_wallet_balance_rupees"] >= 750.0

    # Verify DB state
    gc = await db_session.get(GiftCard, bought["gift_card_id"])
    assert gc.status == "REDEEMED"
    assert gc.remaining_value_cents == 0
    assert gc.redeemed_by_user_id == redeemer.id
    assert gc.redeemed_at is not None

    # Verify wallet record
    wallet = (await db_session.execute(select(Wallet).where(Wallet.user_id == redeemer.id))).scalars().first()
    assert wallet is not None
    assert wallet.balance_cents >= 75000

    # Verify transaction ledger has REDEMPTION entry
    stmt = select(GiftCardTransaction).where(
        GiftCardTransaction.gift_card_id == gc.id,
        GiftCardTransaction.transaction_type == "REDEMPTION"
    )
    tx = (await db_session.execute(stmt)).scalars().first()
    assert tx is not None
    assert tx.amount_cents == 75000
    assert tx.balance_before_cents == 75000
    assert tx.balance_after_cents == 0


@pytest.mark.asyncio
async def test_double_redemption_prevented(db_session):
    """Test 6: Second attempt to redeem an already redeemed gift card must fail with 400."""
    uid = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+91981{uid}6", full_name="Double Spender", role="customer", is_active=True)
    db_session.add(user)
    await db_session.flush()

    service = GiftCardService(db_session)
    bought = await service.purchase_gift_card(
        purchaser_user_id=user.id,
        amount_cents=25000,  # ₹250
        card_design="DIWALI_FESTIVE"
    )

    # First redemption succeeds
    await service.redeem_gift_card(user_id=user.id, code=bought["code"], pin=bought["pin"])

    # Second redemption attempt fails
    with pytest.raises(HTTPException) as exc:
        await service.redeem_gift_card(user_id=user.id, code=bought["code"], pin=bought["pin"])

    assert exc.value.status_code == 400
    assert "cannot be redeemed" in exc.value.detail or "already been fully redeemed" in exc.value.detail


@pytest.mark.asyncio
async def test_expired_card_rejected(db_session):
    """Test 7: Expired gift cards cannot be redeemed."""
    uid = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+91981{uid}7", full_name="Late Redeemer", role="customer", is_active=True)
    db_session.add(user)
    await db_session.flush()

    service = GiftCardService(db_session)
    bought = await service.purchase_gift_card(
        purchaser_user_id=user.id,
        amount_cents=50000,
        card_design="ROYAL_GOLD"
    )

    # Manually expire the card in DB
    gc = await db_session.get(GiftCard, bought["gift_card_id"])
    gc.expires_at = datetime.now(timezone.utc) - timedelta(days=2)
    await db_session.commit()

    # Redemption should fail
    with pytest.raises(HTTPException) as exc:
        await service.redeem_gift_card(user_id=user.id, code=bought["code"], pin=bought["pin"])

    assert exc.value.status_code == 400
    assert "expired" in exc.value.detail.lower()


@pytest.mark.asyncio
async def test_get_user_gift_cards(db_session):
    """Test 8: Portfolio listing respects ownership and filter modes."""
    uid = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+91981{uid}8", full_name="Portfolio User", role="customer", is_active=True)
    db_session.add(user)
    await db_session.flush()

    service = GiftCardService(db_session)
    # Buy 2 cards
    c1 = await service.purchase_gift_card(purchaser_user_id=user.id, amount_cents=25000, card_design="ROYAL_GOLD")
    c2 = await service.purchase_gift_card(purchaser_user_id=user.id, amount_cents=50000, card_design="SAPPHIRE_ELEGANCE")

    # Redeem card 1
    await service.redeem_gift_card(user_id=user.id, code=c1["code"], pin=c1["pin"])

    # Filter ALL
    all_cards = await service.get_user_gift_cards(user_id=user.id, filter_type="ALL")
    assert len(all_cards) >= 2

    # Filter ACTIVE
    active_cards = await service.get_user_gift_cards(user_id=user.id, filter_type="ACTIVE")
    active_ids = [c["gift_card_id"] for c in active_cards]
    assert c2["gift_card_id"] in active_ids
    assert c1["gift_card_id"] not in active_ids


@pytest.mark.asyncio
async def test_admin_overview_and_cancellation(db_session):
    """Test 9: Admin overview returns system-wide metrics and cancellation voids the card."""
    uid = uuid.uuid4().hex[:6]
    user = User(phone_number=f"+91981{uid}9", full_name="Admin Target", role="customer", is_active=True)
    db_session.add(user)
    await db_session.flush()

    service = GiftCardService(db_session)
    card = await service.purchase_gift_card(purchaser_user_id=user.id, amount_cents=100000, card_design="METRO_COMMUTE")

    # Admin list
    admin_data = await service.admin_list_gift_cards(limit=50)
    assert "summary" in admin_data
    assert "gift_cards" in admin_data
    assert any(c["id"] == card["gift_card_id"] for c in admin_data["gift_cards"])

    # Admin cancel
    cancelled = await service.admin_cancel_gift_card(
        admin_user_id="admin-super",
        gift_card_id=card["gift_card_id"],
        reason="Chargeback request"
    )
    assert cancelled["status"] == "CANCELLED"

    # Verify card remaining value is 0
    gc = await db_session.get(GiftCard, card["gift_card_id"])
    assert gc.remaining_value_cents == 0
    assert gc.status == "CANCELLED"

    # Verify adjustment transaction was logged
    stmt = select(GiftCardTransaction).where(
        GiftCardTransaction.gift_card_id == card["gift_card_id"],
        GiftCardTransaction.transaction_type == "ADMIN_ADJUSTMENT"
    )
    tx = (await db_session.execute(stmt)).scalars().first()
    assert tx is not None
    assert "Cancelled by Admin" in (tx.notes or "")
