from datetime import datetime, timezone, timedelta
import json
from typing import Dict, Any, List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, or_
from fastapi import HTTPException, status
from backend.domain.models import FareRule, FareEstimate, VehicleCategory, SurgeRule, GeofenceZone
from backend.integrations.maps.provider import get_maps_provider

class FareService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.maps_provider = get_maps_provider()

    async def resolve_operating_city(
        self,
        city_code: Optional[str],
        lat: float,
        lng: float,
        dest_lat: Optional[float] = None,
        dest_lng: Optional[float] = None,
    ) -> str:
        """Resolve coverage from Admin-managed active polygons, never city-center radii."""
        from backend.application.services.service_area_service import ServiceAreaService
        service_areas = ServiceAreaService(self.db)
        if (dest_lat is None) != (dest_lng is None):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Both destination GPS coordinates are required for service-area validation.",
            )
        if dest_lat is not None and dest_lng is not None:
            zone = await service_areas.resolve_trip(lat, lng, dest_lat, dest_lng, city_code)
        else:
            zone = await service_areas.resolve_point(lat, lng, city_code)
        return zone.city_code.strip().upper()
    
    async def get_active_rule_for_category(self, category_id: str, city_code: Optional[str] = None) -> FareRule:
        now = datetime.now(timezone.utc)
        base_filters = [
            FareRule.category_id == category_id,
            FareRule.is_active == True,
            FareRule.workflow_status.in_(["ACTIVE", "APPROVED"]),
            FareRule.effective_from <= now,
            (FareRule.effective_until.is_(None) | (FareRule.effective_until > now)),
        ]
        if city_code:
            # 1. Exact city-scoped tariff rule has absolute priority
            stmt_city = select(FareRule).where(*base_filters, FareRule.city_code == city_code).order_by(FareRule.effective_from.desc(), FareRule.version.desc())
            rule = (await self.db.execute(stmt_city)).scalars().first()
            if not rule:
                # 2. Universal unpartitioned fallback rule (city_code IS NULL) if configured
                stmt_univ = select(FareRule).where(*base_filters, FareRule.city_code.is_(None)).order_by(FareRule.effective_from.desc(), FareRule.version.desc())
                rule = (await self.db.execute(stmt_univ)).scalars().first()
        else:
            stmt = select(FareRule).where(*base_filters).order_by(FareRule.effective_from.desc(), FareRule.version.desc())
            rule = (await self.db.execute(stmt)).scalars().first()

        if not rule:
            # Check if category_id was given by vehicle code or name
            cat_stmt = select(VehicleCategory).where(
                or_(
                    VehicleCategory.id == category_id,
                    VehicleCategory.code == category_id,
                    VehicleCategory.code == category_id.upper()
                )
            )
            cat_res = await self.db.execute(cat_stmt)
            cat = cat_res.scalars().first()
            if cat:
                cat_base_filters = [
                    FareRule.category_id == cat.id,
                    FareRule.is_active == True,
                    FareRule.workflow_status.in_(["ACTIVE", "APPROVED"]),
                    FareRule.effective_from <= now,
                    (FareRule.effective_until.is_(None) | (FareRule.effective_until > now)),
                ]
                if city_code:
                    stmt_cat_city = select(FareRule).where(*cat_base_filters, FareRule.city_code == city_code).order_by(FareRule.effective_from.desc(), FareRule.version.desc())
                    rule = (await self.db.execute(stmt_cat_city)).scalars().first()
                    if not rule:
                        stmt_cat_univ = select(FareRule).where(*cat_base_filters, FareRule.city_code.is_(None)).order_by(FareRule.effective_from.desc(), FareRule.version.desc())
                        rule = (await self.db.execute(stmt_cat_univ)).scalars().first()
                else:
                    cat_rule_stmt = select(FareRule).where(*cat_base_filters).order_by(FareRule.effective_from.desc(), FareRule.version.desc())
                    rule = (await self.db.execute(cat_rule_stmt)).scalars().first()

        if not rule:
            city_str = f" in city '{city_code}'" if city_code else ""
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"No active fare rule configured in database for vehicle category '{category_id}'{city_str}. Please configure via Admin Controls."
            )
        return rule

    async def get_surge_multiplier(self, category_id: str, latitude: float, longitude: float) -> float:
        now = datetime.now(timezone.utc)
        rules = (await self.db.execute(select(SurgeRule).where(
            SurgeRule.is_active.is_(True), SurgeRule.starts_at <= now, SurgeRule.ends_at > now,
            or_(SurgeRule.category_id.is_(None), SurgeRule.category_id == category_id),
        ))).scalars().all()
        multiplier = 1.0
        for rule in rules:
            if rule.zone_id:
                zone = await self.db.get(GeofenceZone, rule.zone_id)
                if not zone or not zone.is_active:
                    continue
                try:
                    coordinates = json.loads(zone.coordinates_json)
                    polygon = [(float(p['lat']), float(p['lng'])) for p in coordinates]
                except (ValueError, TypeError, KeyError, json.JSONDecodeError):
                    continue
                from backend.application.services.admin_advanced_service import AdminAdvancedService
                if not AdminAdvancedService.is_point_in_polygon(latitude, longitude, polygon):
                    continue
            multiplier = max(multiplier, min(float(rule.multiplier), float(rule.max_multiplier)))
        return multiplier

    async def calculate_estimate(
        self,
        category_id: str,
        pickup_lat: float, pickup_lng: float,
        dest_lat: float, dest_lng: float,
        city_code: Optional[str] = None
    ) -> Dict[str, Any]:
        resolved_city = await self.resolve_operating_city(city_code, pickup_lat, pickup_lng)
        rule = await self.get_active_rule_for_category(category_id, city_code=resolved_city)
        route = await self.maps_provider.get_route(pickup_lat, pickup_lng, dest_lat, dest_lng)

        # Distance & Time charges in minor currency units (paise/cents)
        distance_charge = round(route.distance_km * rule.per_km_cents)
        time_charge = round(route.duration_minutes * rule.per_minute_cents)
        amenities_fee = getattr(rule, "amenities_fee_cents", 0) or 0
        subtotal = rule.base_fare_cents + distance_charge + time_charge + amenities_fee
        
        # Minimum fare guard & surge multiplier
        calculated_fare = max(subtotal, rule.minimum_fare_cents)
        surge_mult = max(rule.surge_multiplier or 1.0, await self.get_surge_multiplier(category_id, pickup_lat, pickup_lng))
        tax_pct = getattr(rule, "tax_percentage", 5.0) or 5.0
        tax_charge = round(calculated_fare * (tax_pct / 100.0))
        total_fare_cents = round(calculated_fare * surge_mult) + tax_charge

        # Feature 81: Fare Cap protection
        fare_cap = getattr(rule, "fare_cap_cents", None)
        is_capped = False
        cap_discount_cents = 0
        if fare_cap and total_fare_cents > fare_cap:
            cap_discount_cents = total_fare_cents - fare_cap
            total_fare_cents = fare_cap
            is_capped = True

        # Feature 77: Confidence interval & estimate range
        confidence_level = "HIGH" if surge_mult == 1.0 else ("MEDIUM" if surge_mult < 1.5 else "LOW")
        confidence_percentage = 95 if confidence_level == "HIGH" else (85 if confidence_level == "MEDIUM" else 75)
        fare_min_cents = max(rule.minimum_fare_cents, round(total_fare_cents * 0.94))
        fare_max_cents = round(total_fare_cents * 1.08)

        # Resolve real category UUID to satisfy foreign key constraint on fare_estimates
        real_category_id = getattr(rule, "category_id", None)
        if not real_category_id or len(str(real_category_id)) != 36:
            cat_stmt = select(VehicleCategory).where(
                or_(
                    VehicleCategory.id == category_id,
                    VehicleCategory.code == category_id,
                    VehicleCategory.code == category_id.upper()
                )
            )
            cat_obj = (await self.db.execute(cat_stmt)).scalars().first()
            if cat_obj:
                real_category_id = cat_obj.id
            else:
                raise HTTPException(
                    status_code=status.HTTP_404_NOT_FOUND,
                    detail=f"Vehicle category '{category_id}' not found in database."
                )

        # Persist estimate snapshot for auditable validation when booking
        expires_at = datetime.now(timezone.utc) + timedelta(minutes=15)
        estimate = FareEstimate(
            category_id=real_category_id,
            fare_rule_id=rule.id,
            pickup_latitude=pickup_lat,
            pickup_longitude=pickup_lng,
            destination_latitude=dest_lat,
            destination_longitude=dest_lng,
            distance_km=route.distance_km,
            estimated_duration_minutes=route.duration_minutes,
            estimated_fare_cents=total_fare_cents,
            surge_multiplier=surge_mult,
            base_fare_cents=rule.base_fare_cents,
            distance_charge_cents=distance_charge,
            time_charge_cents=time_charge,
            tax_cents=tax_charge,
            amenities_fee_cents=amenities_fee,
            fare_min_cents=fare_min_cents,
            fare_max_cents=fare_max_cents,
            confidence_level=confidence_level,
            confidence_percentage=confidence_percentage,
            is_capped=is_capped,
            cap_discount_cents=cap_discount_cents,
            expires_at=expires_at
        )
        self.db.add(estimate)
        await self.db.commit()
        await self.db.refresh(estimate)

        now_utc = datetime.now(timezone.utc)
        seconds_remaining = max(0, int((expires_at - now_utc).total_seconds()))

        return {
            "estimate_id": estimate.id,
            "fare_rule_id": rule.id,
            "fare_rule_version": rule.version,
            "category_id": category_id,
            "city_code": resolved_city,
            "distance_km": round(route.distance_km, 2),
            "estimated_duration_minutes": round(route.duration_minutes, 1),
            "base_fare_cents": rule.base_fare_cents,
            "base_fare_rupees": round(rule.base_fare_cents / 100, 2),
            "distance_charge_cents": distance_charge,
            "distance_charge_rupees": round(distance_charge / 100, 2),
            "time_charge_cents": time_charge,
            "time_charge_rupees": round(time_charge / 100, 2),
            "tax_cents": tax_charge,
            "tax_rupees": round(tax_charge / 100, 2),
            "amenities_fee_cents": amenities_fee,
            "amenities_fee_rupees": round(amenities_fee / 100, 2),
            "surge_multiplier": surge_mult,
            "is_capped": is_capped,
            "cap_discount_cents": cap_discount_cents,
            "cap_discount_rupees": round(cap_discount_cents / 100, 2),
            "fare_min_cents": fare_min_cents,
            "fare_min_rupees": round(fare_min_cents / 100, 2),
            "fare_max_cents": fare_max_cents,
            "fare_max_rupees": round(fare_max_cents / 100, 2),
            "confidence_level": confidence_level,
            "confidence_percentage": confidence_percentage,
            "estimated_fare_cents": total_fare_cents,
            "estimated_fare_rupees": round(total_fare_cents / 100, 2),
            "currency": "INR",
            "expires_at": expires_at.isoformat(),
            "seconds_remaining": seconds_remaining,
            "route_polyline": route.polyline
        }

    async def compare_all_classes(
        self,
        pickup_lat: float, pickup_lng: float,
        dest_lat: float, dest_lng: float,
        city_code: Optional[str] = None
    ) -> List[Dict[str, Any]]:
        """Feature 78: Compare fare estimates across all luxury vehicle classes."""
        resolved_city = await self.resolve_operating_city(city_code, pickup_lat, pickup_lng)
        stmt = select(VehicleCategory).where(VehicleCategory.is_active == True).order_by(VehicleCategory.sort_order.asc())
        res = await self.db.execute(stmt)
        categories = res.scalars().all()

        comparisons: List[Dict[str, Any]] = []
        for cat in categories:
            try:
                estimate = await self.calculate_estimate(
                    category_id=cat.id,
                    pickup_lat=pickup_lat,
                    pickup_lng=pickup_lng,
                    dest_lat=dest_lat,
                    dest_lng=dest_lng,
                    city_code=resolved_city
                )
                comparisons.append({
                    "category_id": cat.id,
                    "code": cat.code,
                    "name": cat.name,
                    "description": cat.description,
                    "capacity": cat.capacity,
                    "icon_url": cat.icon_url,
                    "estimate": estimate
                })
            except Exception:
                continue
        return comparisons

