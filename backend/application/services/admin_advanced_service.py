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
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException, status

from backend.domain.models import (
    GeofenceZone, SurgeRule, CommissionRule, DriverPayoutRequest,
    BroadcastNotification, Wallet, User, DriverProfile, AdministrativeAuditLog, WalletTransaction,
    DriverWallet, DriverLedgerEntry
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
        admin_user_id: Optional[str] = None,
        reason: Optional[str] = None,
        effective_from: Optional[datetime] = None,
    ) -> CommissionRule:
        from backend.application.services.commission_service import CommissionService
        return await CommissionService.create_and_activate_rule(
            session,
            category_id=category_id,
            percentage=percentage,
            admin_user_id=admin_user_id,
            reason=reason,
            effective_from=effective_from,
        )

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
        stmt = select(DriverPayoutRequest).where(DriverPayoutRequest.id == payout_id).with_for_update()
        payout = (await session.execute(stmt)).scalar_one_or_none()
        if not payout:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout request not found")

        if payout.status != "REQUESTED":
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Cannot approve payout in status {payout.status}")

        holds = (await session.execute(select(DriverLedgerEntry).where(
            DriverLedgerEntry.category == "PAYOUT_HOLD", DriverLedgerEntry.reference_id == payout.id))).scalars().all()

        if len(holds) != 1 or holds[0].amount_cents != payout.amount_cents:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Payout hold is missing or does not match ledger.")

        from backend.application.services.payout_execution_service import PayoutExecutionService
        now = datetime.now(timezone.utc)
        mode = PayoutExecutionService.get_execution_mode()
        payout = await PayoutExecutionService._transition_status_atomic(
            session,
            payout=payout,
            expected_status="REQUESTED",
            target_status="APPROVED",
            updates={
                "execution_mode": mode,
                "reviewed_by": admin_user_id,
                "reviewed_at": now,
                "updated_at": now,
            },
        )
        session.add(AdministrativeAuditLog(
            admin_user_id=admin_user_id, action="PAYOUT_APPROVED",
            target_resource="driver_payout_requests", target_id=payout.id,
            changes_json=json.dumps({"amount_cents": payout.amount_cents, "execution_mode": mode})
        ))
        await session.flush()
        return payout

    @staticmethod
    async def reject_payout(session: AsyncSession, payout_id: str, admin_user_id: str, rejection_reason: str) -> DriverPayoutRequest:
        from backend.application.services.payout_execution_service import PayoutExecutionService
        stmt = select(DriverPayoutRequest).where(DriverPayoutRequest.id == payout_id).with_for_update()
        payout = (await session.execute(stmt)).scalar_one_or_none()
        if not payout:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout request not found")

        if payout.status not in ["REQUESTED", "APPROVED"]:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Cannot reject payout in status {payout.status}")

        now = datetime.now(timezone.utc)
        payout = await PayoutExecutionService._transition_status_atomic(
            session,
            payout=payout,
            expected_status=payout.status,
            target_status="REJECTED",
            updates={
                "rejection_reason": rejection_reason,
                "reviewed_by": admin_user_id,
                "reviewed_at": now,
                "updated_at": now,
            },
        )
        await PayoutExecutionService._reverse_payout_hold_once(
            session, payout=payout, reason=rejection_reason, now=now
        )
        session.add(AdministrativeAuditLog(
            admin_user_id=admin_user_id, action="PAYOUT_REJECTED",
            target_resource="driver_payout_requests", target_id=payout.id,
            changes_json=json.dumps({"reason": rejection_reason, "amount_cents": payout.amount_cents})
        ))
        await session.flush()
        return payout

    @staticmethod
    async def mark_payout_paid(session: AsyncSession, payout_id: str, admin_user_id: str, settlement_ref: str) -> DriverPayoutRequest:
        from backend.application.services.payout_execution_service import PayoutExecutionService
        stmt = select(DriverPayoutRequest).where(DriverPayoutRequest.id == payout_id).with_for_update()
        payout = (await session.execute(stmt)).scalar_one_or_none()
        if not payout:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout request not found")
        if payout.status != "AWAITING_MANUAL_TRANSFER":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Manual transfer reference must be recorded first (status AWAITING_MANUAL_TRANSFER required before confirmation).",
            )
        clean_ref = (settlement_ref or "").strip()
        if clean_ref and payout.settlement_reference and clean_ref != payout.settlement_reference:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Confirmation reference does not match recorded manual transfer reference.",
            )
        return await PayoutExecutionService.confirm_manual_transfer(
            session, payout_id=payout.id, admin_user_id=admin_user_id
        )
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


    @staticmethod
    async def verify_payout_account(
        session: AsyncSession,
        account_id: str,
        admin_user_id: str,
        verification_reference: str,
        verification_notes: Optional[str] = None,
    ):
        from backend.domain.models import DriverPayoutAccount
        clean_ref = (verification_reference or "").strip()
        clean_notes = (verification_notes or "").strip() or None
        if len(clean_ref) < 6 or clean_ref.upper().startswith("PENNY-") or clean_ref.lower() in {"verified", "test", "manual", "approved"}:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="A valid external/manual verification evidence reference (min 6 chars, non-placeholder) is required.",
            )
        stmt = select(DriverPayoutAccount).where(DriverPayoutAccount.id == account_id).with_for_update()
        account = (await session.execute(stmt)).scalar_one_or_none()
        if not account:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout account not found.")
        if account.verification_status == "VERIFIED":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Payout account is already verified.",
            )

        now = datetime.now(timezone.utc)
        account.verification_status = "VERIFIED"
        account.verification_method = "MANUAL_ADMIN_REVIEW"
        account.verified_by = admin_user_id
        account.verified_at = now
        account.verification_notes = clean_notes
        account.rejection_reason = None
        account.penny_drop_reference = clean_ref
        account.updated_at = now

        existing_primary = (
            await session.execute(
                select(DriverPayoutAccount).where(
                    DriverPayoutAccount.driver_id == account.driver_id,
                    DriverPayoutAccount.is_primary.is_(True),
                    DriverPayoutAccount.verification_status == "VERIFIED",
                    DriverPayoutAccount.id != account.id,
                )
            )
        ).scalar_one_or_none()
        if not existing_primary:
            account.is_primary = True

        session.add(AdministrativeAuditLog(
            admin_user_id=admin_user_id,
            action="PAYOUT_ACCOUNT_MANUAL_VERIFIED",
            target_resource="driver_payout_accounts",
            target_id=account.id,
            changes_json=json.dumps({
                "driver_id": account.driver_id,
                "verification_method": "MANUAL_ADMIN_REVIEW",
                "verification_reference": clean_ref,
                "verification_notes": clean_notes,
                "verified_by": admin_user_id,
                "verified_at": now.isoformat(),
            }),
        ))
        await session.flush()
        return account

    @staticmethod
    async def reject_payout_account(
        session: AsyncSession,
        account_id: str,
        admin_user_id: str,
        rejection_reason: str,
    ):
        from backend.domain.models import DriverPayoutAccount
        clean_reason = (rejection_reason or "").strip()
        if len(clean_reason) < 6:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="A valid rejection_reason (at least 6 characters) is required.",
            )
        stmt = select(DriverPayoutAccount).where(DriverPayoutAccount.id == account_id).with_for_update()
        account = (await session.execute(stmt)).scalar_one_or_none()
        if not account:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payout account not found.")

        now = datetime.now(timezone.utc)
        account.verification_status = "REJECTED"
        account.verification_method = "MANUAL_ADMIN_REVIEW"
        account.verified_by = admin_user_id
        account.verified_at = now
        account.rejection_reason = clean_reason
        account.penny_drop_reference = None
        account.is_primary = False
        account.updated_at = now

        session.add(AdministrativeAuditLog(
            admin_user_id=admin_user_id,
            action="PAYOUT_ACCOUNT_MANUAL_REJECTED",
            target_resource="driver_payout_accounts",
            target_id=account.id,
            changes_json=json.dumps({
                "driver_id": account.driver_id,
                "verification_method": "MANUAL_ADMIN_REVIEW",
                "rejection_reason": clean_reason,
                "reviewed_by": admin_user_id,
                "reviewed_at": now.isoformat(),
            }),
        ))
        await session.flush()
        return account
