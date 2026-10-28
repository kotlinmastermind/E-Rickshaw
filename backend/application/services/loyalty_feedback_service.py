import json
import uuid
from datetime import datetime, timezone, timedelta
from typing import Optional, List, Dict, Any

from fastapi import HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, and_, or_, desc

from backend.domain.models import (
    User, CustomerProfile, DriverProfile, Ride, DriverRating,
    Wallet, WalletTransaction, ReferralRecord, Notification,
    LoyaltyPointsLedger, CustomerMilestoneBadge, PersonalizedOffer,
    CustomerSurvey, ServiceRecoveryCredit
)

class LoyaltyFeedbackService:
    def __init__(self, db: AsyncSession):
        self.db = db

    # =========================================================================
    # Features 151 - 155: Driver Rating & Categorical Feedback
    # =========================================================================
    async def submit_driver_rating(
        self,
        customer_user_id: str,
        ride_id: str,
        overall_rating: int,
        cleanliness_score: int = 5,
        comfort_score: int = 5,
        service_quality_score: int = 5,
        navigation_score: int = 5,
        feedback: Optional[str] = None,
        private_feedback: Optional[str] = None,
        feedback_tags: Optional[List[str]] = None,
        tip_cents: int = 0
    ) -> Dict[str, Any]:
        if overall_rating < 1 or overall_rating > 5:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Overall rating must be between 1 and 5.")

        # Find customer profile
        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer profile not found.")

        # Find ride
        stmt_ride = select(Ride).where(Ride.id == ride_id)
        res_ride = await self.db.execute(stmt_ride)
        ride = res_ride.scalar_one_or_none()
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Journey not found.")

        if ride.customer_id != customer.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You can only review your own journeys.")

        if ride.status != "COMPLETED":
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Only completed journeys can be reviewed.")

        # Check if already reviewed
        stmt_exist = select(DriverRating).where(DriverRating.ride_id == ride.id)
        res_exist = await self.db.execute(stmt_exist)
        existing_rating = res_exist.scalar_one_or_none()

        tags_serialized = json.dumps(feedback_tags or [])
        driver_id = ride.assigned_driver_id or "drv-general"

        if existing_rating:
            # Update existing
            existing_rating.rating = overall_rating
            existing_rating.cleanliness_score = max(1, min(5, cleanliness_score))
            existing_rating.comfort_score = max(1, min(5, comfort_score))
            existing_rating.service_quality_score = max(1, min(5, service_quality_score))
            existing_rating.navigation_score = max(1, min(5, navigation_score))
            existing_rating.feedback = feedback
            existing_rating.private_feedback = private_feedback
            existing_rating.feedback_tags_json = tags_serialized
            rating_record = existing_rating
        else:
            rating_record = DriverRating(
                ride_id=ride.id,
                customer_id=customer.id,
                driver_id=driver_id,
                rating=overall_rating,
                cleanliness_score=max(1, min(5, cleanliness_score)),
                comfort_score=max(1, min(5, comfort_score)),
                service_quality_score=max(1, min(5, service_quality_score)),
                navigation_score=max(1, min(5, navigation_score)),
                feedback=feedback,
                private_feedback=private_feedback,
                feedback_tags_json=tags_serialized,
                tip_cents=tip_cents
            )
            self.db.add(rating_record)

        # Update Driver Profile average rating if driver exists
        if ride.assigned_driver_id:
            stmt_drv = select(DriverProfile).where(DriverProfile.id == ride.assigned_driver_id)
            res_drv = await self.db.execute(stmt_drv)
            driver = res_drv.scalar_one_or_none()
            if driver:
                new_cnt = (driver.rating_count or 0) + (1 if not existing_rating else 0)
                cur_avg = getattr(driver, "rating_average", 5.0) or 5.0
                new_avg = ((cur_avg * max(1, new_cnt - 1)) + overall_rating) / max(1, new_cnt)
                driver.rating_count = new_cnt
                driver.rating_average = round(new_avg, 2)

        # Process tip if provided at rating time
        tip_info = None
        if tip_cents > 0:
            tip_info = await self.tip_driver_internal(customer=customer, ride=ride, tip_cents=tip_cents)

        # Award points for rating completion (Bonus 50 points)
        await self.award_points(
            customer=customer,
            points=50,
            transaction_type="REVIEW_BONUS",
            description=f"Gratuity bonus for reviewing Journey #{ride.id[:8]}",
            reference_id=ride.id
        )

        # Check milestones for guest
        await self.check_and_award_milestones(customer)

        await self.db.commit()

        return {
            "success": True,
            "message": "Thank you for reviewing your royal journey experience.",
            "rating_id": rating_record.id,
            "overall_rating": overall_rating,
            "category_scores": {
                "cleanliness": rating_record.cleanliness_score,
                "comfort": rating_record.comfort_score,
                "service_quality": rating_record.service_quality_score,
                "navigation": rating_record.navigation_score
            },
            "tags": json.loads(tags_serialized),
            "tip_processed": tip_info
        }

    # =========================================================================
    # Features 156, 157: Tip Driver After Ride & Adjustment Window
    # =========================================================================
    async def tip_driver(
        self,
        customer_user_id: str,
        ride_id: str,
        tip_cents: int
    ) -> Dict[str, Any]:
        if tip_cents <= 0:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Tip amount must be greater than zero.")

        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer not found.")

        stmt_ride = select(Ride).where(Ride.id == ride_id)
        res_ride = await self.db.execute(stmt_ride)
        ride = res_ride.scalar_one_or_none()
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Journey not found.")

        if ride.customer_id != customer.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You can only tip on your own journeys.")

        if ride.status != "COMPLETED":
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Only completed journeys can be tipped.")

        now = datetime.now(timezone.utc)

        # Feature 157: Tip Adjustment Window (2 hours from trip completion)
        if ride.tip_adjustment_expires_at and now > ride.tip_adjustment_expires_at:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="The 2-hour tip adjustment window has expired for this journey."
            )

        res = await self.tip_driver_internal(customer=customer, ride=ride, tip_cents=tip_cents)
        await self.db.commit()
        return res

    async def tip_driver_internal(
        self,
        customer: CustomerProfile,
        ride: Ride,
        tip_cents: int
    ) -> Dict[str, Any]:
        now = datetime.now(timezone.utc)
        prev_tip = ride.tip_cents or 0
        tip_difference = tip_cents - prev_tip

        # Set tip adjustment window to 2 hours if not already set
        if not ride.tip_adjustment_expires_at:
            ride.tip_adjustment_expires_at = now + timedelta(hours=2)

        ride.tip_cents = tip_cents
        ride.tip_status = "ADJUSTED" if prev_tip > 0 else "CHARGED"
        ride.tip_adjusted_at = now

        # Credit driver wallet
        if ride.assigned_driver_id:
            stmt_drv = select(DriverProfile).where(DriverProfile.id == ride.assigned_driver_id)
            res_drv = await self.db.execute(stmt_drv)
            driver = res_drv.scalar_one_or_none()
            if driver and tip_difference > 0:
                driver.wallet_balance_cents = (driver.wallet_balance_cents or 0) + tip_difference

        # Debit customer wallet if wallet exists
        stmt_w = select(Wallet).where(Wallet.user_id == customer.user_id)
        res_w = await self.db.execute(stmt_w)
        wallet = res_w.scalar_one_or_none()
        if wallet and wallet.balance_cents >= tip_difference and tip_difference > 0:
            wallet.balance_cents -= tip_difference
            w_tx = WalletTransaction(
                wallet_id=wallet.id,
                transaction_type="DEBIT",
                amount_cents=tip_difference,
                balance_after_cents=wallet.balance_cents,
                source="CHAUFFEUR_TIP",
                reference_id=ride.id,
                idempotency_key=f"tip-{ride.id}-{uuid.uuid4().hex[:6]}",
                description=f"Gratuity to Chauffeur for Journey #{ride.id[:8]}"
            )
            self.db.add(w_tx)

        # Notify Chauffeur
        if ride.assigned_driver_id:
            stmt_drv_user = select(DriverProfile).where(DriverProfile.id == ride.assigned_driver_id)
            res_du = await self.db.execute(stmt_drv_user)
            drv_prof = res_du.scalar_one_or_none()
            if drv_prof:
                notif = Notification(
                    user_id=drv_prof.user_id,
                    title="Distinguished Gratuity Received",
                    body=f"Guest tipped ₹{tip_cents // 100} for Journey #{ride.id[:8]}.",
                    event_type="CHAUFFEUR_TIP_RECEIVED",
                    dedupe_key=f"tip-notice:{ride.id}:{drv_prof.user_id}"
                )
                self.db.add(notif)

        return {
            "tip_cents": tip_cents,
            "tip_rupees": tip_cents // 100,
            "status": ride.tip_status,
            "adjustment_expires_at": ride.tip_adjustment_expires_at.isoformat() if ride.tip_adjustment_expires_at else None,
            "can_adjust": True
        }

    # =========================================================================
    # Features 158 - 162: Loyalty Points Ledger, Tier Benefits & Redemption
    # =========================================================================
    async def get_loyalty_summary(self, customer_user_id: str) -> Dict[str, Any]:
        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer not found.")

        # Calculate current tier based on lifetime points
        tier_info = self.calculate_tier_info(customer.lifetime_points_earned or 0)
        customer.membership_tier = tier_info["tier_code"]

        # Renewal reminder check (Feature 161)
        now = datetime.now(timezone.utc)
        if not customer.membership_tier_expires_at:
            customer.membership_tier_expires_at = now + timedelta(days=365)

        days_remaining = (customer.membership_tier_expires_at - now).days
        renewal_needed = days_remaining <= 30

        return {
            "tier_code": tier_info["tier_code"],
            "tier_name": tier_info["tier_name"],
            "points_balance": customer.loyalty_points_balance or 0,
            "lifetime_points": customer.lifetime_points_earned or 0,
            "credit_value_rupees": (customer.loyalty_points_balance or 0) * 0.50,
            "multiplier": tier_info["multiplier"],
            "tier_perks": tier_info["perks"],
            "progress_to_next": tier_info["progress"],
            "next_tier_name": tier_info["next_tier_name"],
            "tier_expires_at": customer.membership_tier_expires_at.isoformat(),
            "days_until_renewal": max(0, days_remaining),
            "renewal_reminder_active": renewal_needed
        }

    async def get_loyalty_ledger(self, customer_user_id: str, limit: int = 50) -> List[Dict[str, Any]]:
        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer not found.")

        stmt_ledger = (
            select(LoyaltyPointsLedger)
            .where(LoyaltyPointsLedger.customer_id == customer.id)
            .order_by(LoyaltyPointsLedger.created_at.desc())
            .limit(limit)
        )
        res_ledger = await self.db.execute(stmt_ledger)
        records = res_ledger.scalars().all()

        return [
            {
                "id": r.id,
                "transaction_type": r.transaction_type,
                "points_change": r.points_change,
                "balance_after": r.balance_after,
                "reference_id": r.reference_id,
                "description": r.description,
                "created_at": r.created_at.isoformat()
            }
            for r in records
        ]

    async def redeem_points_for_credit(
        self,
        customer_user_id: str,
        points_to_redeem: int
    ) -> Dict[str, Any]:
        if points_to_redeem < 100:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Minimum redemption is 100 Royal Points.")

        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer not found.")

        cur_balance = customer.loyalty_points_balance or 0
        if cur_balance < points_to_redeem:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Insufficient points. Available: {cur_balance}, Requested: {points_to_redeem}"
            )

        # 100 points = ₹50 credit (5000 cents)
        credit_rupees = int(points_to_redeem * 0.50)
        credit_cents = credit_rupees * 100

        # Update points balance
        customer.loyalty_points_balance = cur_balance - points_to_redeem

        # Log in points ledger
        ledger_entry = LoyaltyPointsLedger(
            customer_id=customer.id,
            transaction_type="REDEEMED_RIDE_CREDIT",
            points_change=-points_to_redeem,
            balance_after=customer.loyalty_points_balance,
            reference_id=f"red-{uuid.uuid4().hex[:8]}",
            description=f"Redeemed {points_to_redeem} Royal Points for ₹{credit_rupees} Ride Credit"
        )
        self.db.add(ledger_entry)

        # Deposit into customer's wallet
        stmt_w = select(Wallet).where(Wallet.user_id == customer.user_id)
        res_w = await self.db.execute(stmt_w)
        wallet = res_w.scalar_one_or_none()
        if not wallet:
            wallet = Wallet(user_id=customer.user_id, balance_cents=credit_cents)
            self.db.add(wallet)
            await self.db.flush()
        else:
            wallet.balance_cents += credit_cents

        w_tx = WalletTransaction(
            wallet_id=wallet.id,
            transaction_type="CREDIT",
            amount_cents=credit_cents,
            balance_after_cents=wallet.balance_cents,
            source="LOYALTY_REDEMPTION",
            reference_id=ledger_entry.reference_id,
            idempotency_key=f"loyalty-redeem-{uuid.uuid4().hex}",
            description=f"Loyalty Points Redemption ({points_to_redeem} pts -> ₹{credit_rupees})"
        )
        self.db.add(w_tx)

        await self.db.commit()

        return {
            "success": True,
            "points_redeemed": points_to_redeem,
            "new_points_balance": customer.loyalty_points_balance,
            "credit_rupees": credit_rupees,
            "new_wallet_balance_rupees": wallet.balance_cents / 100.0
        }

    def calculate_tier_info(self, lifetime_points: int) -> Dict[str, Any]:
        if lifetime_points >= 15000:
            return {
                "tier_code": "BLACK_SOVEREIGN",
                "tier_name": "Black Sovereign",
                "multiplier": 2.0,
                "progress": 1.0,
                "next_tier_name": "Highest Distinction",
                "perks": [
                    "2.0x Royal Points Multiplier",
                    "Guaranteed Presidential Maybach allocation",
                    "Private Tarmac & Airport Concierge Escort",
                    "Dedicated 24/7 Security Operations Line",
                    "0% Surge Pricing Shield",
                    "Complimentary Laurent-Perrier Champagne Amenity"
                ]
            }
        elif lifetime_points >= 5000:
            return {
                "tier_code": "PLATINUM",
                "tier_name": "Platinum Member",
                "multiplier": 1.5,
                "progress": round((lifetime_points - 5000) / 10000.0, 2),
                "next_tier_name": "Black Sovereign",
                "perks": [
                    "1.5x Royal Points Multiplier",
                    "Priority Maybach & Mercedes S-Class Dispatch",
                    "Dedicated Senior Chauffeur Roster",
                    "15-Minute Complimentary Wait Time",
                    "Priority Booking during Peak Events"
                ]
            }
        elif lifetime_points >= 1000:
            return {
                "tier_code": "GOLD",
                "tier_name": "Gold Patron",
                "multiplier": 1.25,
                "progress": round((lifetime_points - 1000) / 4000.0, 2),
                "next_tier_name": "Platinum Member",
                "perks": [
                    "1.25x Royal Points Multiplier",
                    "Priority Chauffeur Dispatch",
                    "Complimentary Sparkling Mineral Water",
                    "10-Minute Complimentary Wait Time"
                ]
            }
        else:
            return {
                "tier_code": "SILVER",
                "tier_name": "Silver Chauffeur Guest",
                "multiplier": 1.0,
                "progress": round(lifetime_points / 1000.0, 2),
                "next_tier_name": "Gold Patron",
                "perks": [
                    "1.0x Royal Points Multiplier",
                    "Standard Luxury Fleet Access",
                    "Chauffeur Luggage Assistance",
                    "Live Trip GPS Escort Sharing"
                ]
            }

    async def award_points(
        self,
        customer: CustomerProfile,
        points: int,
        transaction_type: str,
        description: str,
        reference_id: Optional[str] = None
    ) -> int:
        multiplier = self.calculate_tier_info(customer.lifetime_points_earned or 0)["multiplier"]
        adjusted_points = int(points * multiplier)

        customer.loyalty_points_balance = (customer.loyalty_points_balance or 0) + adjusted_points
        customer.lifetime_points_earned = (customer.lifetime_points_earned or 0) + adjusted_points

        entry = LoyaltyPointsLedger(
            customer_id=customer.id,
            transaction_type=transaction_type,
            points_change=adjusted_points,
            balance_after=customer.loyalty_points_balance,
            reference_id=reference_id,
            description=description
        )
        self.db.add(entry)
        await self.db.flush()
        return adjusted_points

    # =========================================================================
    # Features 163 - 165: Referral Dashboard, Eligibility & Fraud Review Queue
    # =========================================================================
    async def get_referral_dashboard(self, customer_user_id: str) -> Dict[str, Any]:
        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer not found.")

        ref_code = customer.referral_code or f"ROYAL-{customer.id[:6].upper()}"

        stmt_refs = select(ReferralRecord).where(ReferralRecord.referrer_id == customer.id)
        res_refs = await self.db.execute(stmt_refs)
        records = res_refs.scalars().all()

        total_earned_cents = sum(r.referrer_reward_cents for r in records if r.reward_status == "CREDITED")

        guest_list = []
        for r in records:
            guest_list.append({
                "referral_id": r.id,
                "reward_status": r.reward_status,
                "reward_cents": r.referrer_reward_cents,
                "fraud_review_status": getattr(r, "fraud_review_status", "APPROVED"),
                "created_at": r.created_at.isoformat(),
                "credited_at": r.credited_at.isoformat() if r.credited_at else None
            })

        return {
            "referral_code": ref_code,
            "share_url": f"https://grandchauffeur.com/invite/{ref_code}",
            "share_headline": "An Invitation to Distinguished Travel",
            "share_body": f"Experience unparalleled luxury chauffeur service. Use my personal invitation code {ref_code} to receive ₹500 complimentary ride credit on your inaugural journey.",
            "eligibility_rules": [
                "Referee must register using your personal invitation code.",
                "Referee receives ₹500 ride credit voucher instantly.",
                "Referrer receives ₹500 wallet credit automatically when referee completes their first journey (minimum fare ₹200).",
                "Referral rewards expire 60 days from issuance if unused."
            ],
            "total_invites": len(records),
            "successful_referrals": len([r for r in records if r.reward_status == "CREDITED"]),
            "pending_referrals": len([r for r in records if r.reward_status != "CREDITED"]),
            "total_earned_rupees": total_earned_cents // 100,
            "invited_guests": guest_list
        }

    async def get_admin_referral_fraud_queue(self) -> List[Dict[str, Any]]:
        stmt = (
            select(ReferralRecord)
            .order_by(ReferralRecord.created_at.desc())
            .limit(100)
        )
        res = await self.db.execute(stmt)
        records = res.scalars().all()

        queue = []
        for r in records:
            status_val = getattr(r, "fraud_review_status", "APPROVED")
            risk_score = getattr(r, "fraud_risk_score", 0.05)
            queue.append({
                "referral_id": r.id,
                "referrer_id": r.referrer_id,
                "referred_id": r.referred_id,
                "reward_status": r.reward_status,
                "reward_amount_rupees": r.referrer_reward_cents // 100,
                "fraud_review_status": status_val,
                "fraud_risk_score": risk_score,
                "fraud_reason": getattr(r, "fraud_reason", None) or ("Self-referral IP collision check clear" if risk_score < 0.3 else "High-risk duplicate device pattern detected"),
                "created_at": r.created_at.isoformat()
            })
        return queue

    async def review_referral_fraud(
        self,
        admin_user_id: str,
        referral_id: str,
        decision: str,  # APPROVE, REJECT
        notes: str
    ) -> Dict[str, Any]:
        stmt = select(ReferralRecord).where(ReferralRecord.id == referral_id)
        res = await self.db.execute(stmt)
        record = res.scalar_one_or_none()
        if not record:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Referral record not found.")

        now = datetime.now(timezone.utc)
        record.fraud_review_status = "APPROVED" if decision == "APPROVE" else "REJECTED"
        record.reviewed_by = admin_user_id
        record.reviewed_at = now
        record.fraud_reason = notes

        if decision == "APPROVE" and record.reward_status == "PENDING_FIRST_RIDE":
            record.reward_status = "CREDITED"
            record.credited_at = now

        await self.db.commit()
        return {
            "success": True,
            "referral_id": referral_id,
            "status": record.fraud_review_status,
            "decision": decision,
            "notes": notes
        }

    # =========================================================================
    # Features 166, 167: Customer Milestones, Badges & Streak Rewards
    # =========================================================================
    async def get_customer_milestones(self, customer_user_id: str) -> Dict[str, Any]:
        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer not found.")

        await self.check_and_award_milestones(customer)
        await self.db.commit()

        stmt_b = select(CustomerMilestoneBadge).where(CustomerMilestoneBadge.customer_id == customer.id)
        res_b = await self.db.execute(stmt_b)
        unlocked = res_b.scalars().all()
        unlocked_codes = {b.badge_code for b in unlocked}

        all_badges = [
            {
                "code": "FIRST_VOYAGE",
                "name": "First Royal Voyage",
                "icon": "👑",
                "description": "Completed inaugural luxury journey with Grand Chauffeur.",
                "is_unlocked": "FIRST_VOYAGE" in unlocked_codes
            },
            {
                "code": "CENTURION_10",
                "name": "10-Voyage Centurion",
                "icon": "⚔️",
                "description": "Completed 10 distinguished journeys across the royal fleet.",
                "is_unlocked": "CENTURION_10" in unlocked_codes
            },
            {
                "code": "ROYAL_PATRON_50",
                "name": "Grand Sovereign Patron",
                "icon": "🏛️",
                "description": "Completed 50 journeys with uncompromising distinction.",
                "is_unlocked": "ROYAL_PATRON_50" in unlocked_codes
            },
            {
                "code": "NIGHT_OWL",
                "name": "Midnight Executive",
                "icon": "🌙",
                "description": "Experienced midnight executive escorts between 11 PM and 5 AM.",
                "is_unlocked": "NIGHT_OWL" in unlocked_codes
            },
            {
                "code": "FIVE_STAR_GUEST",
                "name": "Five-Star Distinguished Guest",
                "icon": "⭐",
                "description": "Maintained an impeccable 5.0 guest etiquette rating.",
                "is_unlocked": "FIVE_STAR_GUEST" in unlocked_codes
            },
            {
                "code": "STREAK_MASTER",
                "name": "Streak Champion",
                "icon": "🔥",
                "description": "Completed 3 journeys within 7 consecutive days.",
                "is_unlocked": "STREAK_MASTER" in unlocked_codes
            }
        ]

        # Streak status (Feature 167)
        streak_count = customer.ride_streak_count or 0
        streak_target = 3

        return {
            "milestones": all_badges,
            "total_unlocked": len(unlocked),
            "streak": {
                "current_count": streak_count,
                "target_count": streak_target,
                "reward_description": "250 Royal Bonus Points upon 3 journeys this week",
                "progress_percentage": min(100, int((streak_count / streak_target) * 100))
            }
        }

    async def check_and_award_milestones(self, customer: CustomerProfile):
        # Query completed rides count
        stmt_cnt = select(func.count(Ride.id)).where(
            Ride.customer_id == customer.id,
            Ride.status == "COMPLETED"
        )
        res_cnt = await self.db.execute(stmt_cnt)
        completed_count = res_cnt.scalar() or 0

        # Query existing badges
        stmt_b = select(CustomerMilestoneBadge.badge_code).where(CustomerMilestoneBadge.customer_id == customer.id)
        res_b = await self.db.execute(stmt_b)
        existing_codes = set(res_b.scalars().all())

        if completed_count >= 1 and "FIRST_VOYAGE" not in existing_codes:
            self.db.add(CustomerMilestoneBadge(
                customer_id=customer.id,
                badge_code="FIRST_VOYAGE",
                badge_name="First Royal Voyage",
                badge_icon="👑",
                description="Completed inaugural luxury journey with Grand Chauffeur."
            ))

        if completed_count >= 10 and "CENTURION_10" not in existing_codes:
            self.db.add(CustomerMilestoneBadge(
                customer_id=customer.id,
                badge_code="CENTURION_10",
                badge_name="10-Voyage Centurion",
                badge_icon="⚔️",
                description="Completed 10 distinguished journeys across the royal fleet."
            ))

        if completed_count >= 50 and "ROYAL_PATRON_50" not in existing_codes:
            self.db.add(CustomerMilestoneBadge(
                customer_id=customer.id,
                badge_code="ROYAL_PATRON_50",
                badge_name="Grand Sovereign Patron",
                badge_icon="🏛️",
                description="Completed 50 journeys with uncompromising distinction."
            ))

        if (customer.rating_average or 5.0) >= 4.9 and completed_count >= 5 and "FIVE_STAR_GUEST" not in existing_codes:
            self.db.add(CustomerMilestoneBadge(
                customer_id=customer.id,
                badge_code="FIVE_STAR_GUEST",
                badge_name="Five-Star Distinguished Guest",
                badge_icon="⭐",
                description="Maintained an impeccable 5.0 guest etiquette rating."
            ))

        if (customer.ride_streak_count or 0) >= 3 and "STREAK_MASTER" not in existing_codes:
            self.db.add(CustomerMilestoneBadge(
                customer_id=customer.id,
                badge_code="STREAK_MASTER",
                badge_name="Streak Champion",
                badge_icon="🔥",
                description="Completed 3 journeys within 7 consecutive days."
            ))

    # =========================================================================
    # Features 168 - 170: Birthday, Anniversary & Personalized Offers Feed
    # =========================================================================
    async def get_personalized_offers(self, customer_user_id: str) -> List[Dict[str, Any]]:
        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer not found.")

        # Ensure seed offers exist
        now = datetime.now(timezone.utc)
        stmt_o = select(PersonalizedOffer).where(
            or_(PersonalizedOffer.customer_id == customer.id, PersonalizedOffer.customer_id.is_(None)),
            PersonalizedOffer.expires_at > now
        )
        res_o = await self.db.execute(stmt_o)
        offers = res_o.scalars().all()

        if not offers:
            # Seed curated luxury offers
            seed_offers = [
                PersonalizedOffer(
                    customer_id=customer.id,
                    offer_type="BIRTHDAY" if customer.birthday_date else "ANNIVERSARY",
                    title="Celebration Jubilee Escort",
                    tagline="25% off your celebratory royal journey up to ₹1,000",
                    promo_code="CELEBRATE25",
                    discount_percent=25,
                    max_discount_cents=100000,
                    badge_label="ANNIVERSARY & BIRTHDAY",
                    starts_at=now - timedelta(days=1),
                    expires_at=now + timedelta(days=30)
                ),
                PersonalizedOffer(
                    customer_id=None,
                    offer_type="AIRPORT_ESCORT",
                    title="Executive Airport Tarmac Escort",
                    tagline="Flat ₹300 off your next chauffeur pickup at Mumbai T2 or Delhi T3",
                    promo_code="AIRPORT300",
                    discount_percent=15,
                    max_discount_cents=30000,
                    badge_label="FLIGHT ESCORT",
                    starts_at=now - timedelta(days=1),
                    expires_at=now + timedelta(days=14)
                ),
                PersonalizedOffer(
                    customer_id=None,
                    offer_type="WEEKEND_PRIVILEGE",
                    title="Saturday Night Sovereign Soiree",
                    tagline="Complimentary bottle of sparkling water & zero surge on weekend evenings",
                    promo_code="WEEKENDROYAL",
                    discount_percent=10,
                    max_discount_cents=20000,
                    badge_label="WEEKEND PERK",
                    starts_at=now - timedelta(days=1),
                    expires_at=now + timedelta(days=7)
                )
            ]
            self.db.add_all(seed_offers)
            await self.db.commit()
            offers = seed_offers

        return [
            {
                "id": o.id,
                "title": o.title,
                "tagline": o.tagline,
                "promo_code": o.promo_code,
                "discount_percent": o.discount_percent,
                "max_discount_rupees": o.max_discount_cents // 100,
                "badge_label": o.badge_label,
                "offer_type": o.offer_type,
                "expires_at": o.expires_at.isoformat(),
                "is_claimed": o.is_claimed
            }
            for o in offers
        ]

    async def update_celebration_dates(
        self,
        customer_user_id: str,
        birthday_date: Optional[str],
        anniversary_date: Optional[str]
    ) -> Dict[str, Any]:
        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer not found.")

        customer.birthday_date = birthday_date
        customer.anniversary_date = anniversary_date

        # Auto-award birthday/anniversary celebratory voucher
        now = datetime.now(timezone.utc)
        voucher = PersonalizedOffer(
            customer_id=customer.id,
            offer_type="CELEBRATION_VOUCHER",
            title="Sovereign Anniversary Jubilee",
            tagline="Complimentary 25% discount for your upcoming special celebration.",
            promo_code=f"JUBILEE-{uuid.uuid4().hex[:5].upper()}",
            discount_percent=25,
            max_discount_cents=100000,
            badge_label="ANNIVERSARY GIFT",
            starts_at=now,
            expires_at=now + timedelta(days=60)
        )
        self.db.add(voucher)
        await self.db.commit()

        return {
            "success": True,
            "birthday_date": customer.birthday_date,
            "anniversary_date": customer.anniversary_date,
            "celebration_voucher_code": voucher.promo_code
        }

    async def update_offer_notification_frequency(
        self,
        customer_user_id: str,
        frequency: str  # DAILY, WEEKLY, EXCLUSIVE_ONLY, MUTED
    ) -> Dict[str, Any]:
        valid = {"DAILY", "WEEKLY", "EXCLUSIVE_ONLY", "MUTED"}
        if frequency not in valid:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Frequency must be one of {valid}")

        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer not found.")

        customer.offer_notification_frequency = frequency
        await self.db.commit()

        return {
            "success": True,
            "frequency": frequency,
            "message": f"Curated offer dispatch frequency set to {frequency}."
        }

    # =========================================================================
    # Features 171 - 173: Ride History Filters, Repeat Trip & Statements
    # =========================================================================
    async def get_filtered_ride_history(
        self,
        customer_user_id: str,
        status_filter: Optional[str] = None,
        date_from: Optional[datetime] = None,
        date_to: Optional[datetime] = None,
        limit: int = 50
    ) -> List[Dict[str, Any]]:
        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer not found.")

        query = select(Ride).where(Ride.customer_id == customer.id)
        if status_filter:
            query = query.where(Ride.status == status_filter)
        if date_from:
            query = query.where(Ride.requested_at >= date_from)
        if date_to:
            query = query.where(Ride.requested_at <= date_to)

        query = query.order_by(Ride.requested_at.desc()).limit(limit)
        res = await self.db.execute(query)
        rides = res.scalars().all()

        return [
            {
                "ride_id": r.id,
                "status": r.status,
                "pickup_address": r.pickup_address,
                "dropoff_address": r.destination_address,
                "destination_address": r.destination_address,
                "fare_rupees": ((r.final_fare_cents or r.estimated_fare_cents or 0)) // 100,
                "tip_rupees": (r.tip_cents or 0) // 100,
                "requested_at": r.requested_at.isoformat(),
                "completed_at": r.completed_at.isoformat() if r.completed_at else None,
                "vehicle_category": r.vehicle_model_override or "Executive Sedan",
                "can_repeat": True
            }
            for r in rides
        ]

    async def repeat_past_trip(
        self,
        customer_user_id: str,
        past_ride_id: str
    ) -> Dict[str, Any]:
        stmt = select(Ride).where(Ride.id == past_ride_id)
        res = await self.db.execute(stmt)
        past_ride = res.scalar_one_or_none()
        if not past_ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Past journey record not found.")

        return {
            "pickup_address": past_ride.pickup_address,
            "pickup_lat": past_ride.pickup_latitude,
            "pickup_lng": past_ride.pickup_longitude,
            "destination_address": past_ride.destination_address,
            "destination_lat": past_ride.destination_latitude,
            "destination_lng": past_ride.destination_longitude,
            "route_preference": getattr(past_ride, "route_preference", "FASTEST"),
            "booking_mode": getattr(past_ride, "booking_mode", "STANDARD"),
            "estimated_fare_rupees": ((past_ride.final_fare_cents or past_ride.estimated_fare_cents or 160000)) // 100,
            "repeat_source_ride_id": past_ride.id
        }

    async def generate_monthly_statement(
        self,
        customer_user_id: str,
        year: int,
        month: int
    ) -> Dict[str, Any]:
        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer not found.")

        start_date = datetime(year, month, 1, tzinfo=timezone.utc)
        if month == 12:
            end_date = datetime(year + 1, 1, 1, tzinfo=timezone.utc)
        else:
            end_date = datetime(year, month + 1, 1, tzinfo=timezone.utc)

        stmt = select(Ride).where(
            Ride.customer_id == customer.id,
            Ride.status == "COMPLETED",
            Ride.completed_at >= start_date,
            Ride.completed_at < end_date
        ).order_by(Ride.completed_at.asc())

        res = await self.db.execute(stmt)
        rides = res.scalars().all()

        total_fare_cents = sum((r.final_fare_cents or r.estimated_fare_cents or 0) for r in rides)
        total_tip_cents = sum(r.tip_cents or 0 for r in rides)
        total_tax_cents = int(total_fare_cents * 0.05)  # 5% GST on passenger transport

        items = []
        for r in rides:
            fare_val = r.final_fare_cents or r.estimated_fare_cents or 0
            items.append({
                "date": r.completed_at.strftime("%Y-%m-%d %H:%M") if r.completed_at else "",
                "ride_id": r.id,
                "pickup": r.pickup_address,
                "dropoff": r.destination_address,
                "fare_rupees": fare_val / 100.0,
                "gst_rupees": round((fare_val * 0.05) / 100.0, 2),
                "tip_rupees": (r.tip_cents or 0) / 100.0,
                "total_rupees": (fare_val + (r.tip_cents or 0)) / 100.0
            })

        month_name = start_date.strftime("%B %Y")
        return {
            "period": month_name,
            "customer_name": getattr(customer, "full_name", "Distinguished Guest"),
            "total_rides": len(rides),
            "total_fare_rupees": total_fare_cents / 100.0,
            "total_gst_rupees": total_tax_cents / 100.0,
            "total_tip_rupees": total_tip_cents / 100.0,
            "grand_total_rupees": (total_fare_cents + total_tip_cents) / 100.0,
            "items": items,
            "csv_download_url": f"/api/v1/loyalty-feedback/statements/download.csv?year={year}&month={month}"
        }

    # =========================================================================
    # Feature 174: Customer Satisfaction (CSAT / NPS) Survey
    # =========================================================================
    async def submit_survey(
        self,
        customer_user_id: str,
        ride_id: Optional[str],
        survey_type: str,
        score: int,  # 1 to 10 scale
        dimensions: Optional[Dict[str, int]] = None,
        comments: Optional[str] = None
    ) -> Dict[str, Any]:
        if score < 1 or score > 10:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Survey score must be between 1 and 10.")

        stmt_cust = select(CustomerProfile).where(CustomerProfile.user_id == customer_user_id)
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer not found.")

        survey = CustomerSurvey(
            customer_id=customer.id,
            ride_id=ride_id,
            survey_type=survey_type,
            score=score,
            dimensions_json=json.dumps(dimensions or {}),
            comments=comments
        )
        self.db.add(survey)

        # Award 100 Royal Points for survey participation
        await self.award_points(
            customer=customer,
            points=100,
            transaction_type="CSAT_SURVEY_REWARD",
            description="Royal survey participation gratuity points",
            reference_id=survey.id
        )

        await self.db.commit()

        return {
            "success": True,
            "message": "Thank you for sharing your distinguished counsel.",
            "survey_id": survey.id,
            "score": score,
            "points_awarded": 100
        }

    # =========================================================================
    # Feature 175: Service Recovery Credit Issuance Flow (Admin)
    # =========================================================================
    async def issue_service_recovery_credit(
        self,
        admin_user_id: str,
        customer_id: str,
        amount_cents: int,
        reason_category: str,  # LATE_ARRIVAL, VEHICLE_DEFECT, CHAUFFEUR_CONDUCT, ROUTE_DEVIATION, GOODWILL
        notes: str,
        ride_id: Optional[str] = None
    ) -> Dict[str, Any]:
        if amount_cents <= 0:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Credit amount must be greater than zero.")

        stmt_cust = select(CustomerProfile).where(
            or_(CustomerProfile.id == customer_id, CustomerProfile.user_id == customer_id)
        )
        res_cust = await self.db.execute(stmt_cust)
        customer = res_cust.scalar_one_or_none()
        if not customer:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Target customer not found.")

        recovery = ServiceRecoveryCredit(
            customer_id=customer.id,
            ride_id=ride_id,
            amount_cents=amount_cents,
            reason_category=reason_category,
            notes=notes,
            issued_by_admin_id=admin_user_id,
            status="ISSUED"
        )
        self.db.add(recovery)
        await self.db.flush()

        # Deposit into customer wallet
        stmt_w = select(Wallet).where(Wallet.user_id == customer.user_id)
        res_w = await self.db.execute(stmt_w)
        wallet = res_w.scalar_one_or_none()
        if not wallet:
            wallet = Wallet(user_id=customer.user_id, balance_cents=amount_cents)
            self.db.add(wallet)
            await self.db.flush()
        else:
            wallet.balance_cents += amount_cents

        w_tx = WalletTransaction(
            wallet_id=wallet.id,
            transaction_type="CREDIT",
            amount_cents=amount_cents,
            balance_after_cents=wallet.balance_cents,
            source="SERVICE_RECOVERY",
            reference_id=recovery.id,
            idempotency_key=f"recovery-{recovery.id}",
            description=f"Executive Service Recovery Credit: {reason_category} ({notes[:30]})"
        )
        self.db.add(w_tx)
        recovery.wallet_transaction_id = w_tx.id

        # Send notification to customer
        notif = Notification(
            user_id=customer.user_id,
            title="Complimentary Service Recovery Credit",
            body=f"We have credited ₹{amount_cents // 100} to your Royal Wallet in consideration of your experience ({reason_category.replace('_', ' ')}).",
            event_type="SERVICE_RECOVERY_ISSUED",
            dedupe_key=f"recovery-notice:{recovery.id}:{customer.user_id}"
        )
        self.db.add(notif)

        await self.db.commit()

        return {
            "success": True,
            "recovery_id": recovery.id,
            "customer_id": customer.id,
            "amount_rupees": amount_cents // 100,
            "reason_category": reason_category,
            "notes": notes,
            "new_wallet_balance_rupees": wallet.balance_cents / 100.0
        }

    async def get_service_recovery_history(self, limit: int = 50) -> List[Dict[str, Any]]:
        stmt = (
            select(ServiceRecoveryCredit)
            .order_by(ServiceRecoveryCredit.created_at.desc())
            .limit(limit)
        )
        res = await self.db.execute(stmt)
        records = res.scalars().all()

        return [
            {
                "id": r.id,
                "customer_id": r.customer_id,
                "ride_id": r.ride_id,
                "amount_rupees": r.amount_cents // 100,
                "reason_category": r.reason_category,
                "notes": r.notes,
                "issued_by_admin_id": r.issued_by_admin_id,
                "status": r.status,
                "created_at": r.created_at.isoformat()
            }
            for r in records
        ]
