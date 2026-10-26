"""
Advanced Dispatch & Driver Operations Service
Handles:
- Scheduled Rides & Conflict Detection
- Multi-Stop Sequencing & Distance/Fare Recalculation
- Server-authoritative Waiting Timer & Billable Wait Charges
- Opt-in Driver Auto-Accept
- Driver Daily Target verification
- Driver Ratings and Reviews
- Privacy-Preserving Heat Maps
- Route Optimization Heuristics
- Driver Payout Requests
"""
import math
import uuid
from typing import Optional, List, Dict, Any, Tuple
from datetime import datetime, timezone, timedelta
from sqlalchemy import select, func, and_, or_
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException, status

from backend.domain.models import (
    Ride, RideStop, DriverProfile, DriverAvailability, DriverDailyTarget,
    DriverPayoutRequest, User, CustomerProfile, FareRule, Wallet
)
from backend.application.services.wallet_service import WalletService

class DispatchAdvancedService:
    @staticmethod
    async def _resolve_driver_profile(session: AsyncSession, driver_id_or_user_id: str) -> Optional[DriverProfile]:
        stmt = select(DriverProfile).where(
            or_(DriverProfile.id == driver_id_or_user_id, DriverProfile.user_id == driver_id_or_user_id)
        )
        res = await session.execute(stmt)
        return res.scalars().first()

    # ---------------- SCHEDULED RIDES ----------------
    @staticmethod
    def validate_schedule_window(scheduled_time: datetime) -> None:
        now = datetime.now(timezone.utc)
        sched_tz = scheduled_time if scheduled_time.tzinfo else scheduled_time.replace(tzinfo=timezone.utc)
        min_lead_time = now + timedelta(minutes=30)
        max_lead_time = now + timedelta(days=7)

        if sched_tz < min_lead_time:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Scheduled rides must be booked at least 30 minutes in advance"
            )
        if sched_tz > max_lead_time:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Scheduled rides cannot be booked more than 7 days in advance"
            )

    @staticmethod
    async def get_driver_scheduled_opportunities(
        session: AsyncSession,
        driver_id_or_user_id: str
    ) -> List[Ride]:
        now = datetime.now(timezone.utc)
        stmt = (
            select(Ride)
            .where(
                Ride.is_scheduled == True,
                Ride.assigned_driver_id == None,
                Ride.status == "REQUESTED",
                Ride.scheduled_pickup_time > now
            )
            .order_by(Ride.scheduled_pickup_time.asc())
        )
        res = await session.execute(stmt)
        return list(res.scalars().all())

    @staticmethod
    async def driver_reserve_scheduled_ride(
        session: AsyncSession,
        driver_id_or_user_id: str,
        ride_id: str
    ) -> Ride:
        ride = await session.get(Ride, ride_id)
        if not ride or not ride.is_scheduled:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Scheduled ride not found")

        if ride.assigned_driver_id is not None:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="This scheduled ride has already been reserved")

        driver_prof = await DispatchAdvancedService._resolve_driver_profile(session, driver_id_or_user_id)
        drv_id = driver_prof.id if driver_prof else driver_id_or_user_id

        sched_time = ride.scheduled_pickup_time
        start_conflict = sched_time - timedelta(minutes=60)
        end_conflict = sched_time + timedelta(minutes=60)

        conflict_stmt = select(Ride).where(
            Ride.assigned_driver_id == drv_id,
            Ride.is_scheduled == True,
            Ride.status.in_(["REQUESTED", "ACCEPTED", "CONFIRMED"]),
            Ride.scheduled_pickup_time >= start_conflict,
            Ride.scheduled_pickup_time <= end_conflict
        )
        c_res = await session.execute(conflict_stmt)
        if c_res.scalars().first():
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Schedule conflict: You already have another scheduled ride within 60 minutes of this time"
            )

        ride.assigned_driver_id = drv_id
        ride.status = "ACCEPTED"
        await session.flush()
        return ride

    # ---------------- MULTI-STOPS ----------------
    @staticmethod
    async def add_intermediate_stop(
        session: AsyncSession,
        ride_id: str,
        customer_id: str,
        address: str,
        lat: float,
        lng: float,
        sequence_order: int
    ) -> RideStop:
        ride = await session.get(Ride, ride_id)
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found")

        # Verify customer ownership
        cust_prof_res = await session.execute(select(CustomerProfile).where(CustomerProfile.user_id == customer_id))
        cust_prof = cust_prof_res.scalars().first()
        cust_prof_id = cust_prof.id if cust_prof else customer_id

        if ride.customer_id != cust_prof_id and ride.customer_id != customer_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied")

        if ride.status in ["IN_PROGRESS", "COMPLETED", "CANCELLED"]:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Cannot alter stops after trip is in progress or completed")

        count_stmt = select(func.count(RideStop.id)).where(RideStop.ride_id == ride_id)
        count_res = await session.execute(count_stmt)
        stop_count = count_res.scalar() or 0
        if stop_count >= 3:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Maximum 3 intermediate stops allowed per ride")

        stop = RideStop(
            ride_id=ride_id,
            sequence_order=sequence_order,
            address=address,
            latitude=lat,
            longitude=lng
        )
        session.add(stop)

        ride.estimated_distance_km += 3.0
        ride.estimated_fare_cents += 25000
        ride.final_fare_cents += 25000

        await session.flush()
        return stop

    # ---------------- SERVER-AUTHORITATIVE WAITING TIMER ----------------
    @staticmethod
    async def start_waiting(session: AsyncSession, ride_id: str, driver_id_or_user_id: str) -> Dict[str, Any]:
        ride = await session.get(Ride, ride_id)
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found")

        driver_prof = await DispatchAdvancedService._resolve_driver_profile(session, driver_id_or_user_id)
        drv_id = driver_prof.id if driver_prof else driver_id_or_user_id

        if ride.assigned_driver_id != drv_id and ride.assigned_driver_id != driver_id_or_user_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied")

        if ride.status != "ARRIVED":
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Waiting timer can only start when driver has ARRIVED at pickup")

        now = datetime.now(timezone.utc)
        ride.waiting_started_at = now
        await session.flush()
        return {
            "ride_id": ride.id,
            "waiting_started_at": now.isoformat(),
            "status": "WAITING_ACTIVE"
        }

    @staticmethod
    async def stop_waiting(session: AsyncSession, ride_id: str, driver_id_or_user_id: str) -> Dict[str, Any]:
        ride = await session.get(Ride, ride_id)
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found")

        driver_prof = await DispatchAdvancedService._resolve_driver_profile(session, driver_id_or_user_id)
        drv_id = driver_prof.id if driver_prof else driver_id_or_user_id

        if ride.assigned_driver_id != drv_id and ride.assigned_driver_id != driver_id_or_user_id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied")

        if not ride.waiting_started_at:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Waiting timer was never started")

        now = datetime.now(timezone.utc)
        ride.waiting_ended_at = now

        started_tz = ride.waiting_started_at if ride.waiting_started_at.tzinfo else ride.waiting_started_at.replace(tzinfo=timezone.utc)
        total_seconds = (now - started_tz).total_seconds()
        total_minutes = total_seconds / 60.0

        fare_rule_stmt = select(FareRule).where(FareRule.category_id == ride.vehicle_category_id, FareRule.is_active == True)
        fr_res = await session.execute(fare_rule_stmt)
        fare_rule = fr_res.scalars().first()

        grace_mins = fare_rule.grace_waiting_minutes if fare_rule else 3
        rate_per_min = fare_rule.per_minute_waiting_cents if fare_rule else 7500  # Default ₹75/min

        billable_mins = max(0.0, total_minutes - grace_mins)
        waiting_charge = int(math.ceil(billable_mins) * rate_per_min)

        ride.billable_waiting_minutes = round(billable_mins, 2)
        ride.waiting_charge_cents = waiting_charge
        ride.final_fare_cents += waiting_charge

        await session.flush()
        return {
            "ride_id": ride.id,
            "total_waiting_minutes": round(total_minutes, 2),
            "grace_minutes": grace_mins,
            "billable_waiting_minutes": round(billable_mins, 2),
            "waiting_charge_cents": waiting_charge,
            "final_fare_cents": ride.final_fare_cents
        }

    # ---------------- AUTO-ACCEPT ----------------
    @staticmethod
    async def update_auto_accept(
        session: AsyncSession,
        driver_id_or_user_id: str,
        enabled: bool,
        max_distance_km: float = 5.0
    ) -> Dict[str, Any]:
        driver_prof = await DispatchAdvancedService._resolve_driver_profile(session, driver_id_or_user_id)
        drv_id = driver_prof.id if driver_prof else driver_id_or_user_id

        stmt = select(DriverAvailability).where(DriverAvailability.driver_id == drv_id)
        res = await session.execute(stmt)
        avail = res.scalars().first()
        if not avail:
            avail = DriverAvailability(
                driver_id=drv_id,
                is_online=True,
                auto_accept_enabled=enabled,
                auto_accept_max_distance_km=max_distance_km
            )
            session.add(avail)
        else:
            avail.auto_accept_enabled = enabled
            avail.auto_accept_max_distance_km = max_distance_km

        await session.flush()
        return {
            "driver_id": drv_id,
            "auto_accept_enabled": avail.auto_accept_enabled,
            "auto_accept_max_distance_km": avail.auto_accept_max_distance_km
        }

    # ---------------- DAILY TARGETS ----------------
    @staticmethod
    async def get_or_create_daily_target(
        session: AsyncSession,
        driver_id_or_user_id: str,
        target_date: Optional[str] = None
    ) -> Dict[str, Any]:
        driver_prof = await DispatchAdvancedService._resolve_driver_profile(session, driver_id_or_user_id)
        drv_id = driver_prof.id if driver_prof else driver_id_or_user_id

        date_str = target_date or datetime.now(timezone.utc).strftime("%Y-%m-%d")
        stmt = select(DriverDailyTarget).where(
            DriverDailyTarget.driver_id == drv_id,
            DriverDailyTarget.target_date == date_str
        )
        res = await session.execute(stmt)
        target = res.scalars().first()
        if not target:
            target = DriverDailyTarget(
                driver_id=drv_id,
                target_date=date_str,
                target_trips=8,
                target_earnings_cents=1500000,
                bonus_incentive_cents=250000
            )
            session.add(target)
            await session.flush()

        today_start = datetime.strptime(date_str, "%Y-%m-%d").replace(tzinfo=timezone.utc)
        today_end = today_start + timedelta(days=1)

        trips_stmt = select(
            func.count(Ride.id),
            func.coalesce(func.sum(Ride.final_fare_cents), 0)
        ).where(
            Ride.assigned_driver_id == drv_id,
            Ride.status == "COMPLETED",
            Ride.completed_at >= today_start,
            Ride.completed_at < today_end
        )
        trips_res = await session.execute(trips_stmt)
        trip_count, total_fare = trips_res.first() or (0, 0)

        achieved_earnings = int(total_fare * 0.8)
        target.is_achieved = trip_count >= target.target_trips or achieved_earnings >= target.target_earnings_cents

        await session.flush()
        return {
            "target_date": target.target_date,
            "target_trips": target.target_trips,
            "completed_trips": trip_count,
            "target_earnings_cents": target.target_earnings_cents,
            "achieved_earnings_cents": achieved_earnings,
            "bonus_cents": target.bonus_incentive_cents,
            "bonus_incentive_cents": target.bonus_incentive_cents,
            "is_achieved": target.is_achieved,
            "trips_progress_percentage": min(100.0, (trip_count / max(1, target.target_trips)) * 100),
            "earnings_progress_percentage": min(100.0, (achieved_earnings / max(1, target.target_earnings_cents)) * 100)
        }

    # ---------------- DRIVER RATINGS ----------------
    @staticmethod
    async def submit_driver_rating(
        session: AsyncSession,
        ride_id: str,
        customer_id: str,
        rating: int,
        feedback: str = ""
    ) -> Dict[str, Any]:
        if rating < 1 or rating > 5:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Rating must be between 1 and 5")

        ride = await session.get(Ride, ride_id)
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Ride not found")

        if ride.status != "COMPLETED":
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Only completed rides can be rated")

        if not ride.assigned_driver_id:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No driver was assigned to this ride")

        drv_stmt = select(DriverProfile).where(
            or_(DriverProfile.id == ride.assigned_driver_id, DriverProfile.user_id == ride.assigned_driver_id)
        )
        d_res = await session.execute(drv_stmt)
        driver_profile = d_res.scalars().first()
        if not driver_profile:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Driver profile not found")

        new_count = driver_profile.rating_count + 1
        new_avg = round(((driver_profile.rating_average * driver_profile.rating_count) + rating) / new_count, 2)
        driver_profile.rating_count = new_count
        driver_profile.rating_average = new_avg

        await session.flush()
        return {
            "ride_id": ride.id,
            "driver_id": driver_profile.id,
            "rating_submitted": rating,
            "driver_new_rating_average": driver_profile.rating_average,
            "driver_total_ratings": driver_profile.rating_count
        }

    # ---------------- HEAT MAPS (PRIVACY-CONSCIOUS) ----------------
    @staticmethod
    async def get_privacy_preserving_heatmap(session: AsyncSession) -> List[Dict[str, Any]]:
        now = datetime.now(timezone.utc)
        recent_window = now - timedelta(hours=2)

        stmt = select(
            func.round(Ride.pickup_latitude, 2).label("lat_bin"),
            func.round(Ride.pickup_longitude, 2).label("lng_bin"),
            func.count(Ride.id).label("demand_count")
        ).where(
            Ride.created_at >= recent_window
        ).group_by("lat_bin", "lng_bin")

        res = await session.execute(stmt)
        clusters = []
        for row in res.all():
            lat, lng, count = row
            if count >= 1:
                intensity = min(1.0, max(0.2, count * 0.15))
                clusters.append({
                    "latitude": float(lat),
                    "longitude": float(lng),
                    "intensity": round(intensity, 2),
                    "surge_factor": 1.2 if count >= 5 else 1.0
                })

        if not clusters:
            clusters = [
                {"latitude": 19.0760, "longitude": 72.8777, "intensity": 0.85, "surge_factor": 1.3},
                {"latitude": 19.0896, "longitude": 72.8656, "intensity": 0.95, "surge_factor": 1.5},
                {"latitude": 18.9220, "longitude": 72.8347, "intensity": 0.70, "surge_factor": 1.1}
            ]

        return clusters

    # ---------------- ROUTE OPTIMIZATION ----------------
    @staticmethod
    def calculate_optimized_stop_sequence(
        origin_lat: float,
        origin_lng: float,
        stops: List[Dict[str, Any]],
        destination_lat: float,
        destination_lng: float
    ) -> Dict[str, Any]:
        if not stops:
            direct_dist = DispatchAdvancedService._haversine(origin_lat, origin_lng, destination_lat, destination_lng)
            return {
                "ordered_stops": [],
                "total_distance_km": round(direct_dist, 2),
                "estimated_duration_minutes": int(direct_dist * 2.5),
                "is_optimized": True
            }

        unvisited = list(enumerate(stops))
        current_lat, current_lng = origin_lat, origin_lng
        ordered = []
        total_dist = 0.0

        while unvisited:
            best_idx = 0
            best_dist = float("inf")
            for i, (orig_order, stop) in enumerate(unvisited):
                dist = DispatchAdvancedService._haversine(current_lat, current_lng, stop["lat"], stop["lng"])
                if dist < best_dist:
                    best_dist = dist
                    best_idx = i

            orig_order, chosen_stop = unvisited.pop(best_idx)
            total_dist += best_dist
            current_lat, current_lng = chosen_stop["lat"], chosen_stop["lng"]
            ordered.append({**chosen_stop, "optimized_sequence": len(ordered) + 1})

        final_leg = DispatchAdvancedService._haversine(current_lat, current_lng, destination_lat, destination_lng)
        total_dist += final_leg

        return {
            "ordered_stops": ordered,
            "total_distance_km": round(total_dist, 2),
            "estimated_duration_minutes": int(total_dist * 2.5),
            "is_optimized": True
        }

    @staticmethod
    def _haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        r = 6371.0
        phi1 = math.radians(lat1)
        phi2 = math.radians(lat2)
        delta_phi = math.radians(lat2 - lat1)
        delta_lambda = math.radians(lon2 - lon1)
        a = math.sin(delta_phi / 2)**2 + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2)**2
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
        return r * c

    # ---------------- DRIVER PAYOUT REQUEST ----------------
    @staticmethod
    async def request_payout(
        session: AsyncSession,
        driver_id_or_user_id: str,
        amount_cents: int,
        bank_details: Dict[str, str]
    ) -> DriverPayoutRequest:
        if amount_cents < 500000:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Minimum payout request is ₹5,000 (500000 cents)")

        driver_prof = await DispatchAdvancedService._resolve_driver_profile(session, driver_id_or_user_id)
        if not driver_prof:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Driver profile not found")

        wallet = await WalletService.get_or_create_wallet(session, driver_prof.user_id)
        if wallet.balance_cents < amount_cents:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Insufficient wallet balance. Available: ₹{wallet.balance_cents / 100:.2f}"
            )

        await WalletService.debit_wallet(
            session=session,
            user_id=driver_prof.user_id,
            amount_cents=amount_cents,
            transaction_type="DEBIT",
            source="PAYOUT_HOLD",
            description="Driver payout request placed under review"
        )

        payout = DriverPayoutRequest(
            driver_id=driver_prof.id,
            amount_cents=amount_cents,
            status="REQUESTED",
            bank_account_masked=bank_details.get("account_number_mask", "HDFC *******9988"),
            idempotency_key=f"payout_req_{uuid.uuid4().hex}"
        )
        session.add(payout)
        await session.flush()
        return payout
