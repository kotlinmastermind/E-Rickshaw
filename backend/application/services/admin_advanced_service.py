"""
Admin Advanced Operations Service
Handles:
- Geo-Fencing Polygon validation & Boundary checking (Ray-casting)
- Dynamic Surge Pricing Rules & Active Multiplier calculation
- Version-controlled Commission Rules
- Driver Payout Approval, Rejection (with compensating entries), and Settlement
- Fleet & Audience Broadcast Notifications
"""
import json
import uuid
from typing import Optional, List, Dict, Any, Tuple
from datetime import datetime, timezone, timedelta
from sqlalchemy import select, func, and_
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException, status

from backend.domain.models import (
    GeofenceZone, SurgeRule, CommissionRule, DriverPayoutRequest,
    BroadcastNotification, Wallet, User, DriverProfile, AdministrativeAuditLog, WalletTransaction
)
from backend.application.services.wallet_service import WalletService

class AdminAdvancedService:
    # ---------------- GEO-FENCING ----------------
    @staticmethod
    def is_point_in_polygon(lat: float, lng: float, polygon: List[Tuple[float, float]]) -> bool:
        if len(polygon) < 3:
            return False

        inside = False
        n = len(polygon)
        p1_lat, p1_lng = polygon[0]

        for i in range(1, n + 1):
            p2_lat, p2_lng = polygon[i % n]
            if min(p1_lng, p2_lng) < lng <= max(p1_lng, p2_lng):
                if lat <= max(p1_lat, p2_lat):
                    if p1_lng != p2_lng:
                        lat_intersect = (lng - p1_lng) * (p2_lat - p1_lat) / (p2_lng - p1_lng) + p1_lat
                    if p1_lat == p2_lat or lat <= lat_intersect:
                        inside = not inside
            p1_lat, p1_lng = p2_lat, p2_lng

        return inside

    @staticmethod
    async def create_geofence(
        session: AsyncSession,
        name: str,
        zone_type: str,
        coordinates: List[Dict[str, float]],
        surge_multiplier: float = 1.0
    ) -> GeofenceZone:
        if len(coordinates) < 3:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Geofence must have at least 3 coordinate vertices")

        zone = GeofenceZone(
            name=name,
            zone_type=zone_type,
            coordinates_json=json.dumps(coordinates),
            surge_multiplier=surge_multiplier,
            is_active=True
        )
        session.add(zone)
        await session.flush()
        return zone

    @staticmethod
    async def list_geofences(session: AsyncSession) -> List[GeofenceZone]:
        stmt = select(GeofenceZone).order_by(GeofenceZone.created_at.desc())
        res = await session.execute(stmt)
        return list(res.scalars().all())

    @staticmethod
    async def check_location_geofence(session: AsyncSession, lat: float, lng: float) -> Dict[str, Any]:
        stmt = select(GeofenceZone).where(GeofenceZone.is_active == True)
        res = await session.execute(stmt)
        zones = res.scalars().all()

        matched_zones = []
        is_restricted = False
        highest_surge = 1.0

        for z in zones:
            coords = json.loads(z.coordinates_json)
            poly = [(pt["lat"], pt["lng"]) for pt in coords]
            if AdminAdvancedService.is_point_in_polygon(lat, lng, poly):
                matched_zones.append(z.name)
                if z.zone_type == "RESTRICTED":
                    is_restricted = True
                if z.surge_multiplier > highest_surge:
                    highest_surge = z.surge_multiplier

        return {
            "latitude": lat,
            "longitude": lng,
            "is_serviceable": not is_restricted,
            "matched_zones": matched_zones,
            "surge_multiplier": highest_surge
        }

    # ---------------- SURGE RULES ----------------
    @staticmethod
    async def create_surge_rule(
        session: AsyncSession,
        zone_id: Optional[str],
        category_id: Optional[str],
        multiplier: float,
        reason: str,
        admin_user_id: Optional[str] = None,
        starts_at: Optional[datetime] = None,
        ends_at: Optional[datetime] = None,
    ) -> SurgeRule:
        now = datetime.now(timezone.utc)
        starts_at = starts_at or now
        ends_at = ends_at or (starts_at + timedelta(days=7))
        if starts_at.tzinfo is None: starts_at = starts_at.replace(tzinfo=timezone.utc)
        if ends_at.tzinfo is None: ends_at = ends_at.replace(tzinfo=timezone.utc)
        if ends_at <= starts_at or not 1.0 <= multiplier <= 5.0 or len(reason.strip()) < 5:
            raise HTTPException(status_code=422, detail="Use a multiplier from 1 to 5, a clear reason, and an end after the start.")
        rule = SurgeRule(
            zone_id=zone_id,
            category_id=category_id,
            multiplier=multiplier,
            max_multiplier=multiplier * 1.5,
            reason=reason,
            starts_at=starts_at,
            ends_at=ends_at,
            is_active=True,
            created_by=admin_user_id
        )
        session.add(rule)
        await session.flush()
        return rule

    @staticmethod
    async def list_surge_rules(session: AsyncSession) -> List[SurgeRule]:
        stmt = select(SurgeRule).order_by(SurgeRule.created_at.desc())
        res = await session.execute(stmt)
        return list(res.scalars().all())

    # ---------------- COMMISSION SETTINGS ----------------
    @staticmethod
    async def create_commission_rule(
        session: AsyncSession,
        category_id: Optional[str],
        percentage: float,
        admin_user_id: Optional[str] = None
    ) -> CommissionRule:
        current_time = datetime.now(timezone.utc)
        latest_version = (await session.execute(select(func.max(CommissionRule.version)).where(
            CommissionRule.category_id == category_id))).scalar() or 0
        active_rules = (await session.execute(select(CommissionRule).where(
            CommissionRule.category_id == category_id, CommissionRule.is_active.is_(True)).with_for_update())).scalars().all()
        for previous in active_rules:
            previous.is_active = False
            previous.effective_until = current_time
        rule = CommissionRule(
            category_id=category_id,
            version=latest_version + 1,
            platform_commission_percentage=percentage,
            driver_share_percentage=max(0.0, 100.0 - percentage),
            effective_from=current_time,
            is_active=True,
            created_by=admin_user_id
        )
        session.add(rule)
        await session.flush()
        if admin_user_id:
            session.add(AdministrativeAuditLog(admin_user_id=admin_user_id, action="COMMISSION_RULE_ACTIVATED", target_resource="commission_rules", target_id=rule.id, changes_json=json.dumps({"version": rule.version, "commission_percent": percentage, "category_id": category_id})))
        return rule

    @staticmethod
    async def list_commission_rules(session: AsyncSession) -> List[CommissionRule]:
        stmt = select(CommissionRule).order_by(CommissionRule.created_at.desc())
        res = await session.execute(stmt)
        return list(res.scalars().all())

    # ---------------- DRIVER PAYOUTS WORKFLOW ----------------
    @staticmethod
    async def list_payout_requests(session: AsyncSession, status_filter: Optional[str] = None) -> List[DriverPayoutRequest]:
        stmt = select(DriverPayoutRequest).order_by(DriverPayoutRequest.created_at.desc())
        if status_filter:
            stmt = stmt.where(DriverPayoutRequest.status == status_filter)
        res = await session.execute(stmt)
        return list(res.scalars().all())

    @staticmethod
    async def approve_payout(session: AsyncSession, payout_id: str, admin_user_id: str) -> DriverPayoutRequest:
        payout = await session.get(DriverPayoutRequest, payout_id)
        if not payout:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout request not found")

        if payout.status != "REQUESTED":
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Cannot approve payout in status {payout.status}")

        holds = (await session.execute(select(WalletTransaction).where(
            WalletTransaction.source == "PAYOUT_HOLD", WalletTransaction.reference_id == payout.id))).scalars().all()
        if len(holds) != 1 or holds[0].amount_cents != payout.amount_cents:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Payout hold is missing or does not match the immutable wallet ledger; investigate before approval.")

        payout.status = "APPROVED"
        payout.reviewed_by = admin_user_id
        payout.reviewed_at = datetime.now(timezone.utc)
        session.add(AdministrativeAuditLog(admin_user_id=admin_user_id, action="PAYOUT_APPROVED", target_resource="driver_payout_requests", target_id=payout.id, changes_json=json.dumps({"amount_cents": payout.amount_cents})))
        await session.flush()
        return payout

    @staticmethod
    async def reject_payout(session: AsyncSession, payout_id: str, admin_user_id: str, rejection_reason: str) -> DriverPayoutRequest:
        payout = await session.get(DriverPayoutRequest, payout_id)
        if not payout:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout request not found")

        if payout.status not in ["REQUESTED", "APPROVED"]:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Cannot reject payout in status {payout.status}")

        holds = (await session.execute(select(WalletTransaction).where(
            WalletTransaction.source == "PAYOUT_HOLD", WalletTransaction.reference_id == payout.id))).scalars().all()
        if len(holds) != 1 or holds[0].amount_cents != payout.amount_cents:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Payout hold is missing or does not match the immutable wallet ledger; investigate before rejection.")

        drv_prof = await session.get(DriverProfile, payout.driver_id)
        driver_user_id = drv_prof.user_id if drv_prof else payout.driver_id

        await WalletService.credit_wallet(
            session=session,
            user_id=driver_user_id,
            amount_cents=payout.amount_cents,
            transaction_type="CREDIT",
            source="PAYOUT_REVERSAL",
            reference_id=payout.id,
            description=f"Refund for rejected payout: {rejection_reason}",
            idempotency_key=f"payout_reversal_{payout.id}"
        )

        payout.status = "REJECTED"
        payout.rejection_reason = rejection_reason
        payout.reviewed_by = admin_user_id
        payout.reviewed_at = datetime.now(timezone.utc)
        session.add(AdministrativeAuditLog(admin_user_id=admin_user_id, action="PAYOUT_REJECTED", target_resource="driver_payout_requests", target_id=payout.id, changes_json=json.dumps({"reason": rejection_reason})))
        await session.flush()
        return payout

    @staticmethod
    async def mark_payout_paid(session: AsyncSession, payout_id: str, admin_user_id: str, settlement_ref: str) -> DriverPayoutRequest:
        payout = await session.get(DriverPayoutRequest, payout_id)
        if not payout:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout request not found")

        if payout.status != "APPROVED":
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Only approved payouts can be marked as paid")

        payout.status = "PAID"
        payout.payout_reference = settlement_ref
        payout.settlement_reference = settlement_ref
        payout.reviewed_by = admin_user_id
        payout.updated_at = datetime.now(timezone.utc)
        payout.reviewed_at = payout.updated_at
        session.add(AdministrativeAuditLog(admin_user_id=admin_user_id, action="PAYOUT_SETTLED", target_resource="driver_payout_requests", target_id=payout.id, changes_json=json.dumps({"settlement_reference": settlement_ref})))
        await session.flush()
        return payout

    # ---------------- BROADCAST NOTIFICATIONS ----------------
    @staticmethod
    async def send_broadcast(
        session: AsyncSession,
        admin_user_id: str,
        title: str,
        message: str,
        target_role: str = "ALL",
        channel: str = "IN_APP"
    ) -> BroadcastNotification:
        target_aud = "ALL"
        if target_role.upper() in ["DRIVER", "DRIVERS"]:
            target_aud = "DRIVERS"
        elif target_role.upper() in ["CUSTOMER", "CUSTOMERS"]:
            target_aud = "CUSTOMERS"

        stmt = select(func.count(User.id)).where(User.is_active == True)
        if target_aud == "DRIVERS":
            stmt = stmt.where(User.role == "driver")
        elif target_aud == "CUSTOMERS":
            stmt = stmt.where(User.role == "customer")

        c_res = await session.execute(stmt)
        count = c_res.scalar() or 0

        broadcast = BroadcastNotification(
            title=title,
            message=message,
            target_audience=target_aud,
            status="SENT",
            dispatched_count=count,
            sent_at=datetime.now(timezone.utc),
            created_by=admin_user_id
        )
        session.add(broadcast)
        await session.flush()
        return broadcast

    @staticmethod
    async def list_broadcasts(session: AsyncSession) -> List[BroadcastNotification]:
        stmt = select(BroadcastNotification).order_by(BroadcastNotification.created_at.desc())
        res = await session.execute(stmt)
        return list(res.scalars().all())
