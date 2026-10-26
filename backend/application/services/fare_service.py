from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from fastapi import HTTPException, status
from backend.domain.models import FareRule, FareEstimate, VehicleCategory
from backend.integrations.maps.provider import get_maps_provider

class FareService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.maps_provider = get_maps_provider()

    async def get_active_rule_for_category(self, category_id: str) -> FareRule:
        stmt = select(FareRule).where(
            FareRule.category_id == category_id,
            FareRule.is_active == True
        ).order_by(FareRule.version.desc())
        res = await self.db.execute(stmt)
        rule = res.scalars().first()
        if not rule:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="No active fare schedule found for this luxury vehicle category."
            )
        return rule

    async def calculate_estimate(
        self,
        category_id: str,
        pickup_lat: float, pickup_lng: float,
        dest_lat: float, dest_lng: float
    ) -> Dict[str, Any]:
        rule = await self.get_active_rule_for_category(category_id)
        route = await self.maps_provider.get_route(pickup_lat, pickup_lng, dest_lat, dest_lng)

        # Distance & Time charges in minor currency units (paise/cents)
        distance_charge = round(route.distance_km * rule.per_km_cents)
        time_charge = round(route.duration_minutes * rule.per_minute_cents)
        subtotal = rule.base_fare_cents + distance_charge + time_charge
        
        # Minimum fare guard & surge multiplier
        calculated_fare = max(subtotal, rule.minimum_fare_cents)
        final_estimate_cents = round(calculated_fare * rule.surge_multiplier)

        # Persist estimate snapshot for auditable validation when booking
        expires_at = datetime.now(timezone.utc) + timedelta(minutes=15)
        estimate = FareEstimate(
            category_id=category_id,
            fare_rule_id=rule.id,
            pickup_latitude=pickup_lat,
            pickup_longitude=pickup_lng,
            destination_latitude=dest_lat,
            destination_longitude=dest_lng,
            distance_km=route.distance_km,
            estimated_duration_minutes=route.duration_minutes,
            estimated_fare_cents=final_estimate_cents,
            surge_multiplier=rule.surge_multiplier,
            expires_at=expires_at
        )
        self.db.add(estimate)
        await self.db.commit()
        await self.db.refresh(estimate)

        return {
            "estimate_id": estimate.id,
            "fare_rule_id": rule.id,
            "fare_rule_version": rule.version,
            "category_id": category_id,
            "distance_km": round(route.distance_km, 2),
            "estimated_duration_minutes": round(route.duration_minutes, 1),
            "base_fare_cents": rule.base_fare_cents,
            "distance_charge_cents": distance_charge,
            "time_charge_cents": time_charge,
            "surge_multiplier": rule.surge_multiplier,
            "estimated_fare_cents": final_estimate_cents,
            "currency": "INR",
            "expires_at": expires_at.isoformat(),
            "route_polyline": route.polyline
        }
