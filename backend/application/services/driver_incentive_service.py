import uuid
import logging
from datetime import datetime, timezone, timedelta
from zoneinfo import ZoneInfo
from typing import Dict, Any, List
from sqlalchemy import select, and_, func, update
from sqlalchemy.ext.asyncio import AsyncSession
from backend.domain.models import (
    DriverProfile, DriverDailyTarget, DriverWeeklyGoal,
    DriverWallet, DriverLedgerEntry, Ride
)

logger = logging.getLogger(__name__)
try:
    from zoneinfo import ZoneInfo
    INDIA_TZ = ZoneInfo("Asia/Kolkata")
except Exception:
    INDIA_TZ = timezone(timedelta(hours=5, minutes=30), name="Asia/Kolkata")

class DriverIncentiveService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def settle_daily_targets(self, date_override: str = None) -> Dict[str, Any]:
        """
        Evaluates completed rides for India business day (Asia/Kolkata),
        credits bonus to driver wallets via atomic ledger entries, and rolls over to next day.
        """
        now_ist = datetime.now(INDIA_TZ)
        target_date_str = date_override or (now_ist - timedelta(days=1)).strftime("%Y-%m-%d")

        # 1. Fetch all daily targets for target date
        stmt = select(DriverDailyTarget).where(
            DriverDailyTarget.target_date == target_date_str,
            DriverDailyTarget.is_achieved == False
        )
        targets = (await self.db.execute(stmt)).scalars().all()
        settled_count = 0
        total_credited_cents = 0

        for target in targets:
            # Query eligible completed rides on target date in IST
            start_of_day = datetime.strptime(f"{target_date_str} 00:00:00", "%Y-%m-%d %H:%M:%S").replace(tzinfo=INDIA_TZ)
            end_of_day = start_of_day + timedelta(days=1)

            ride_count_stmt = select(func.count()).select_from(Ride).where(
                Ride.assigned_driver_id == target.driver_id,
                Ride.status == "COMPLETED",
                Ride.created_at >= start_of_day.astimezone(timezone.utc),
                Ride.created_at < end_of_day.astimezone(timezone.utc)
            )
            completed_trips = (await self.db.execute(ride_count_stmt)).scalar_one()

            if completed_trips >= target.target_trips:
                # Qualified! Credit bonus
                target.is_achieved = True
                bonus_cents = target.bonus_incentive_cents

                # 2. Update DriverWallet atomically
                wallet_stmt = select(DriverWallet).where(DriverWallet.driver_id == target.driver_id)
                wallet = (await self.db.execute(wallet_stmt)).scalar_one_or_none()
                if not wallet:
                    wallet = DriverWallet(driver_id=target.driver_id, balance_cents=0, currency="INR")
                    self.db.add(wallet)
                    await self.db.flush()

                new_balance = wallet.balance_cents + bonus_cents
                wallet.balance_cents = new_balance

                # 3. Create Immutable Ledger Entry with idempotency
                idempotency_ref = f"incentive:daily:{target.driver_id}:{target_date_str}"
                ledger = DriverLedgerEntry(
                    id=str(uuid.uuid4()),
                    driver_id=target.driver_id,
                    entry_type="CREDIT",
                    category="INCENTIVE_BONUS",
                    amount_cents=bonus_cents,
                    balance_after_cents=new_balance,
                    reference_id=idempotency_ref,
                    description=f"Achieved Daily Target ({completed_trips}/{target.target_trips} trips) on {target_date_str}"
                )
                self.db.add(ledger)
                settled_count += 1
                total_credited_cents += bonus_cents

            # 4. Ensure next day's target exists
            next_date_str = (datetime.strptime(target_date_str, "%Y-%m-%d") + timedelta(days=1)).strftime("%Y-%m-%d")
            existing_next = (await self.db.execute(
                select(DriverDailyTarget).where(
                    DriverDailyTarget.driver_id == target.driver_id,
                    DriverDailyTarget.target_date == next_date_str
                )
            )).scalar_one_or_none()
            if not existing_next:
                self.db.add(DriverDailyTarget(
                    id=str(uuid.uuid4()),
                    driver_id=target.driver_id,
                    target_date=next_date_str,
                    target_trips=target.target_trips,
                    target_earnings_cents=target.target_earnings_cents,
                    bonus_incentive_cents=target.bonus_incentive_cents,
                    is_achieved=False
                ))

        await self.db.commit()
        return {
            "target_date": target_date_str,
            "drivers_awarded": settled_count,
            "total_bonus_credited_cents": total_credited_cents
        }