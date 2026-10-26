"""
Promo, Ride Pass & Referral Service
Enforces atomic redemption, anti-replay protection, financial limits, and anti-fraud referral tracking.
"""
from typing import Optional, List, Dict, Any
from datetime import datetime, timezone, timedelta
from sqlalchemy import select, func, and_
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException, status

from backend.domain.models import (
    PromoCode, PromoRedemption, RidePass, CustomerRidePass, RidePassRedemption,
    ReferralRecord, CustomerProfile, User
)
from backend.application.services.wallet_service import WalletService

class PromoPassService:
    # ---------------- PROMO CODES ----------------
    @staticmethod
    async def validate_promo_code(
        session: AsyncSession,
        code_str: str,
        customer_user_id: str,
        category_id: Optional[str] = None,
        fare_cents: int = 0
    ) -> Dict[str, Any]:
        stmt = select(PromoCode).where(
            func.upper(PromoCode.code) == code_str.strip().upper(),
            PromoCode.is_active == True
        )
        res = await session.execute(stmt)
        promo = res.scalars().first()
        if not promo:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Invalid or expired promo code")

        now = datetime.now(timezone.utc)
        start_tz = promo.starts_at if promo.starts_at.tzinfo else promo.starts_at.replace(tzinfo=timezone.utc)
        expires_tz = promo.expires_at if promo.expires_at.tzinfo else promo.expires_at.replace(tzinfo=timezone.utc)

        if now < start_tz or now > expires_tz:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Promo code is not currently valid")

        if promo.total_usage_limit and promo.times_used >= promo.total_usage_limit:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Promo code total redemption limit reached")

        if fare_cents < promo.min_fare_cents:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Minimum fare of ₹{promo.min_fare_cents / 100:.2f} required to apply this promo"
            )

        if promo.vehicle_category_id and category_id and promo.vehicle_category_id != category_id:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Promo code not eligible for this vehicle category")

        # Get customer profile id
        prof_res = await session.execute(select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id))
        cust_profile = prof_res.scalars().first()
        cust_prof_id = cust_profile.id if cust_profile else customer_user_id

        # Check customer usage limit
        cust_redemptions = await session.execute(
            select(func.count(PromoRedemption.id)).where(
                PromoRedemption.promo_id == promo.id,
                PromoRedemption.customer_id == cust_prof_id
            )
        )
        customer_uses = cust_redemptions.scalar() or 0
        if customer_uses >= promo.per_customer_limit:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="You have reached the redemption limit for this code")

        # Calculate discount
        if promo.discount_type == "PERCENTAGE":
            raw_discount = int((fare_cents * promo.discount_value) / 100)
            discount = min(raw_discount, promo.max_discount_cents)
        else:  # FLAT
            discount = min(int(promo.discount_value * 100), fare_cents)

        return {
            "promo_id": promo.id,
            "code": promo.code,
            "discount_cents": discount,
            "effective_fare_cents": max(0, fare_cents - discount),
            "discount_type": promo.discount_type,
            "discount_value": promo.discount_value,
            "terms": f"Maximum discount of ₹{promo.max_discount_cents / 100:.2f}"
        }

    @staticmethod
    async def redeem_promo_code(
        session: AsyncSession,
        code_str: str,
        customer_user_id: str,
        ride_id: str,
        fare_cents: int
    ) -> int:
        val = await PromoPassService.validate_promo_code(session, code_str, customer_user_id, fare_cents=fare_cents)
        promo_id = val["promo_id"]
        discount_cents = val["discount_cents"]

        # Prevent double redemption on the same ride
        existing = await session.execute(
            select(PromoRedemption).where(PromoRedemption.ride_id == ride_id)
        )
        if existing.scalars().first():
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="A promo has already been applied to this ride")

        promo = await session.get(PromoCode, promo_id)
        promo.times_used += 1

        prof_res = await session.execute(select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id))
        cust_profile = prof_res.scalars().first()
        cust_prof_id = cust_profile.id if cust_profile else customer_user_id

        redemption = PromoRedemption(
            promo_id=promo_id,
            customer_id=cust_prof_id,
            ride_id=ride_id,
            discount_applied_cents=discount_cents
        )
        session.add(redemption)
        await session.flush()
        return discount_cents

    # ---------------- RIDE PASSES ----------------
    @staticmethod
    async def list_pass_catalog(session: AsyncSession) -> List[RidePass]:
        stmt = select(RidePass).where(RidePass.is_active == True).order_by(RidePass.price_cents.asc())
        res = await session.execute(stmt)
        return list(res.scalars().all())

    @staticmethod
    async def purchase_ride_pass(
        session: AsyncSession,
        customer_user_id: str,
        pass_id: str,
        payment_method: str = "WALLET"
    ) -> CustomerRidePass:
        ride_pass = await session.get(RidePass, pass_id)
        if not ride_pass or not ride_pass.is_active:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride pass not found or inactive")

        prof_res = await session.execute(select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id))
        cust_profile = prof_res.scalars().first()
        if not cust_profile:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer profile not found")

        # If payment is WALLET, debit user wallet
        if payment_method == "WALLET":
            await WalletService.debit_wallet(
                session=session,
                user_id=customer_user_id,
                amount_cents=ride_pass.price_cents,
                transaction_type="DEBIT",
                source="RIDE_PASS_PURCHASE",
                description=f"Purchased Ride Pass: {ride_pass.name}"
            )

        now = datetime.now(timezone.utc)
        expires_at = now + timedelta(days=ride_pass.validity_days)

        user_pass = CustomerRidePass(
            customer_id=cust_profile.id,
            pass_id=ride_pass.id,
            status="ACTIVE",
            rides_remaining=ride_pass.total_rides,
            valid_from=now,
            valid_until=expires_at
        )
        session.add(user_pass)
        await session.flush()
        return user_pass

    @staticmethod
    async def get_customer_passes(session: AsyncSession, customer_user_id: str) -> List[CustomerRidePass]:
        prof_res = await session.execute(select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id))
        cust_profile = prof_res.scalars().first()
        if not cust_profile:
            return []

        stmt = (
            select(CustomerRidePass)
            .where(CustomerRidePass.customer_id == cust_profile.id)
            .order_by(CustomerRidePass.created_at.desc())
        )
        res = await session.execute(stmt)
        return list(res.scalars().all())

    @staticmethod
    async def apply_pass_to_ride(
        session: AsyncSession,
        customer_user_id: str,
        ride_id: str,
        fare_cents: int
    ) -> int:
        prof_res = await session.execute(select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id))
        cust_profile = prof_res.scalars().first()
        if not cust_profile:
            return 0

        now = datetime.now(timezone.utc)
        stmt = (
            select(CustomerRidePass)
            .where(
                CustomerRidePass.customer_id == cust_profile.id,
                CustomerRidePass.status == "ACTIVE",
                CustomerRidePass.rides_remaining > 0,
                CustomerRidePass.valid_until > now
            )
            .order_by(CustomerRidePass.valid_until.asc())
        )
        res = await session.execute(stmt)
        active_pass = res.scalars().first()
        if not active_pass:
            return 0

        ride_pass = await session.get(RidePass, active_pass.pass_id)
        if not ride_pass:
            return 0

        discount = min(int((fare_cents * ride_pass.discount_percentage) / 100), ride_pass.max_discount_per_ride_cents)
        active_pass.rides_remaining -= 1
        if active_pass.rides_remaining <= 0:
            active_pass.status = "EXHAUSTED"

        redemption = RidePassRedemption(
            customer_pass_id=active_pass.id,
            ride_id=ride_id,
            discount_applied_cents=discount
        )
        session.add(redemption)
        await session.flush()
        return discount

    # ---------------- REFERRALS ----------------
    @staticmethod
    async def claim_referral(session: AsyncSession, new_user_id: str, referral_code_str: str) -> ReferralRecord:
        ref_code = referral_code_str.strip().upper()
        # Find referrer profile
        stmt = select(CustomerProfile).where(func.upper(CustomerProfile.referral_code) == ref_code)
        res = await session.execute(stmt)
        referrer_profile = res.scalars().first()
        if not referrer_profile:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Invalid referral code")

        # Find new user customer profile
        new_prof_res = await session.execute(select(CustomerProfile).where(CustomerProfile.user_id == new_user_id))
        new_prof = new_prof_res.scalars().first()
        if not new_prof:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer profile not found")

        if referrer_profile.id == new_prof.id or referrer_profile.user_id == new_user_id:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Self-referrals are strictly prohibited")

        # Check if already referred
        existing = await session.execute(
            select(ReferralRecord).where(ReferralRecord.referred_id == new_prof.id)
        )
        if existing.scalars().first():
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="User already registered with a referral code")

        new_prof.referred_by_code = ref_code

        record = ReferralRecord(
            referrer_id=referrer_profile.id,
            referred_id=new_prof.id,
            reward_status="PENDING_FIRST_RIDE",
            referrer_reward_cents=50000,
            referred_reward_cents=50000
        )
        session.add(record)
        await session.flush()
        return record

    @staticmethod
    async def attribute_referral_reward(session: AsyncSession, completed_customer_user_id: str) -> Optional[ReferralRecord]:
        # Find customer profile
        prof_res = await session.execute(select(CustomerProfile).where(CustomerProfile.user_id == completed_customer_user_id))
        cust_prof = prof_res.scalars().first()
        if not cust_prof:
            return None

        stmt = select(ReferralRecord).where(
            ReferralRecord.referred_id == cust_prof.id,
            ReferralRecord.reward_status == "PENDING_FIRST_RIDE"
        )
        res = await session.execute(stmt)
        record = res.scalars().first()
        if not record:
            return None

        # Fetch referrer user_id
        ref_prof = await session.get(CustomerProfile, record.referrer_id)
        if ref_prof:
            await WalletService.credit_wallet(
                session=session,
                user_id=ref_prof.user_id,
                amount_cents=record.referrer_reward_cents,
                transaction_type="CREDIT",
                source="REFERRAL_REWARD",
                reference_id=record.id,
                description="Referral reward for verified passenger onboarding"
            )

        # Credit referred customer as well
        await WalletService.credit_wallet(
            session=session,
            user_id=cust_prof.user_id,
            amount_cents=record.referred_reward_cents,
            transaction_type="CREDIT",
            source="REFERRAL_REWARD",
            reference_id=record.id,
            description="Welcome bonus for first completed luxury ride"
        )

        record.reward_status = "CREDITED"
        record.credited_at = datetime.now(timezone.utc)
        await session.flush()
        return record
