"""Centralized, versioned Commission Engine.

Single authoritative source of truth for:
- Resolving active CommissionRule by category_id and effective timestamp
- Computing exact commission_cents and driver_net_cents using Decimal (ROUND_HALF_UP)
- Creating/activating versioned CommissionRule records with audit trail
- Preventing overlapping active rules and invalid percentages
"""
from __future__ import annotations

import json
import logging
import math
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from typing import Optional

from fastapi import HTTPException, status
from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from backend.domain.models.extended_models import (
    AdministrativeAuditLog,
    CommissionRule,
    RideCommissionSnapshot,
)

logger = logging.getLogger(__name__)

DEFAULT_FALLBACK_COMMISSION_PERCENT = Decimal("15.00")


@dataclass(frozen=True)
class CommissionSnapshot:
    rule_id: Optional[str]
    version: int
    percentage: float
    commission_cents: int
    gross_cents: int
    net_cents: int
    calculation_basis: str
    effective_at: datetime


class CommissionService:
    """Authoritative versioned commission rule resolution and calculation."""

    @staticmethod
    def validate_percentage(percentage: float) -> Decimal:
        if percentage is None or isinstance(percentage, bool):
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail="Commission percentage is required.",
            )
        try:
            pct_float = float(percentage)
        except (TypeError, ValueError):
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail="Commission percentage must be a valid number.",
            )
        if math.isnan(pct_float) or math.isinf(pct_float):
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail="Commission percentage must be finite.",
            )
        if pct_float < 0.0 or pct_float > 100.0:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail="Commission percentage must be between 0.0 and 100.0.",
            )
        return Decimal(str(pct_float)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)

    @staticmethod
    def compute_commission_cents(gross_cents: int, percentage: float | Decimal) -> int:
        if gross_cents <= 0:
            return 0
        pct = Decimal(str(percentage))
        raw = (Decimal(gross_cents) * pct) / Decimal("100")
        return int(raw.quantize(Decimal("1"), rounding=ROUND_HALF_UP))

    @classmethod
    async def resolve_rule(
        cls,
        session: AsyncSession,
        *,
        category_id: Optional[str] = None,
        effective_at: Optional[datetime] = None,
    ) -> Optional[CommissionRule]:
        """Resolve the single authoritative CommissionRule active at `effective_at`.

        Checks category-specific rule first, then falls back to global rule (category_id IS NULL).
        """
        ts = effective_at or datetime.now(timezone.utc)
        if ts.tzinfo is None:
            ts = ts.replace(tzinfo=timezone.utc)

        for cat in ([category_id, None] if category_id is not None else [None]):
            cat_filter = (
                CommissionRule.category_id == cat
                if cat is not None
                else CommissionRule.category_id.is_(None)
            )
            stmt = (
                select(CommissionRule)
                .where(
                    cat_filter,
                    CommissionRule.approval_status == "ACTIVE",
                    or_(CommissionRule.effective_from.is_(None), CommissionRule.effective_from <= ts),
                    or_(CommissionRule.effective_until.is_(None), CommissionRule.effective_until > ts),
                )
                .order_by(CommissionRule.version.desc(), CommissionRule.created_at.desc())
                .limit(1)
            )
            rule = (await session.execute(stmt)).scalar_one_or_none()
            if rule is not None:
                return rule
        return None

    @classmethod
    async def calculate_for_ride(
        cls,
        session: AsyncSession,
        *,
        gross_cents: int,
        category_id: Optional[str] = None,
        effective_at: Optional[datetime] = None,
        calculation_basis: str = "RIDE_COMPLETION",
    ) -> CommissionSnapshot:
        """Calculate commission using the authoritative rule at `effective_at`."""
        ts = effective_at or datetime.now(timezone.utc)
        if ts.tzinfo is None:
            ts = ts.replace(tzinfo=timezone.utc)

        rule = await cls.resolve_rule(session, category_id=category_id, effective_at=ts)
        if rule is not None and rule.platform_commission_percentage is not None:
            pct = Decimal(str(rule.platform_commission_percentage)).quantize(
                Decimal("0.01"), rounding=ROUND_HALF_UP
            )
            rule_id = rule.id
            version = rule.version or 1
        else:
            from backend.core.config import settings
            if getattr(settings, "ENVIRONMENT", "development").lower() == "production":
                raise HTTPException(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail="No active commission rule configured for this category or globally.",
                )
            pct = DEFAULT_FALLBACK_COMMISSION_PERCENT
            rule_id = None
            version = 0

        commission_cents = cls.compute_commission_cents(gross_cents, pct)
        net_cents = max(0, gross_cents - commission_cents)
        return CommissionSnapshot(
            rule_id=rule_id,
            version=version,
            percentage=float(pct),
            commission_cents=commission_cents,
            gross_cents=gross_cents,
            net_cents=net_cents,
            calculation_basis=calculation_basis,
            effective_at=ts,
        )

    @classmethod
    async def record_ride_snapshot(
        cls,
        session: AsyncSession,
        *,
        ride_id: str,
        snapshot: CommissionSnapshot,
        category_id: Optional[str] = None,
        commission_classification: str = "PAYABLE",
        currency: str = "INR",
    ) -> RideCommissionSnapshot:
        """Persist an immutable per-ride commission snapshot idempotently."""
        existing = (
            await session.execute(
                select(RideCommissionSnapshot).where(RideCommissionSnapshot.ride_id == ride_id)
            )
        ).scalar_one_or_none()
        if existing is not None:
            mismatches = []
            if existing.gross_fare_cents != snapshot.gross_cents:
                mismatches.append(f"gross_fare_cents({existing.gross_fare_cents}!={snapshot.gross_cents})")
            if existing.driver_gross_cents != snapshot.gross_cents:
                mismatches.append(f"driver_gross_cents({existing.driver_gross_cents}!={snapshot.gross_cents})")
            if existing.commission_cents != snapshot.commission_cents:
                mismatches.append(f"commission_cents({existing.commission_cents}!={snapshot.commission_cents})")
            if existing.commission_classification != commission_classification:
                mismatches.append(f"classification({existing.commission_classification}!={commission_classification})")
            if abs(float(existing.commission_percentage) - float(snapshot.percentage)) > 1e-6:
                mismatches.append(f"percentage({existing.commission_percentage}!={snapshot.percentage})")
            if existing.commission_rule_id != snapshot.rule_id:
                mismatches.append(f"rule_id({existing.commission_rule_id}!={snapshot.rule_id})")
            if existing.commission_version != snapshot.version:
                mismatches.append(f"version({existing.commission_version}!={snapshot.version})")
            if existing.category_id != category_id:
                mismatches.append(f"category_id({existing.category_id}!={category_id})")
            if existing.currency != currency:
                mismatches.append(f"currency({existing.currency}!={currency})")
            if existing.calculation_basis != snapshot.calculation_basis:
                mismatches.append(f"calculation_basis({existing.calculation_basis}!={snapshot.calculation_basis})")
            if mismatches:
                logger.error("Immutable commission snapshot mismatch for ride %s: %s", ride_id, ", ".join(mismatches))
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail=f"Immutable commission snapshot mismatch for ride {ride_id}: {', '.join(mismatches)}",
                )
            return existing

        row = RideCommissionSnapshot(
            ride_id=ride_id,
            commission_rule_id=snapshot.rule_id,
            commission_version=snapshot.version,
            commission_percentage=snapshot.percentage,
            category_id=category_id,
            gross_fare_cents=snapshot.gross_cents,
            commission_cents=snapshot.commission_cents,
            driver_gross_cents=snapshot.gross_cents,
            commission_classification=commission_classification,
            currency=currency,
            calculation_basis=snapshot.calculation_basis,
            effective_at=snapshot.effective_at,
        )
        session.add(row)
        await session.flush()
        return row

    @classmethod
    async def create_and_activate_rule(
        cls,
        session: AsyncSession,
        *,
        category_id: Optional[str],
        percentage: float,
        admin_user_id: Optional[str],
        reason: Optional[str] = None,
        effective_from: Optional[datetime] = None,
    ) -> CommissionRule:
        """Create a new immutable versioned CommissionRule, closing any overlapping active rule."""
        pct = cls.validate_percentage(percentage)
        now = datetime.now(timezone.utc)
        eff_from = effective_from or now
        if eff_from.tzinfo is None:
            eff_from = eff_from.replace(tzinfo=timezone.utc)

        cat_filter = (
            CommissionRule.category_id == category_id
            if category_id is not None
            else CommissionRule.category_id.is_(None)
        )
        latest_version = (
            await session.execute(select(func.max(CommissionRule.version)).where(cat_filter))
        ).scalar() or 0

        active_rules = (
            await session.execute(
                select(CommissionRule)
                .where(cat_filter, CommissionRule.is_active.is_(True))
                .with_for_update()
            )
        ).scalars().all()

        previous_pct = None
        for prev in active_rules:
            previous_pct = prev.platform_commission_percentage
            prev.is_active = False
            if prev.effective_until is None or prev.effective_until > eff_from:
                prev.effective_until = eff_from

        rule = CommissionRule(
            category_id=category_id,
            version=latest_version + 1,
            platform_commission_percentage=float(pct),
            driver_share_percentage=float(Decimal("100.00") - pct),
            effective_from=eff_from,
            effective_until=None,
            is_active=True,
            approval_status="ACTIVE",
            approval_reason=reason or "Admin commission update",
            created_by=admin_user_id,
            created_at=now,
        )
        session.add(rule)
        await session.flush()

        if admin_user_id:
            session.add(
                AdministrativeAuditLog(
                    admin_user_id=admin_user_id,
                    action="COMMISSION_RULE_ACTIVATED",
                    target_resource="commission_rules",
                    target_id=rule.id,
                    changes_json=json.dumps(
                        {
                            "version": rule.version,
                            "previous_commission_percent": previous_pct,
                            "new_commission_percent": float(pct),
                            "category_id": category_id,
                            "effective_from": eff_from.isoformat(),
                            "reason": reason or "Admin commission update",
                        }
                    ),
                )
            )
            await session.flush()
        return rule
