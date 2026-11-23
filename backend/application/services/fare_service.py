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

    @staticmethod
    def resolve_city_code_from_coords(lat: float, lng: float) -> str:
        """
        Authoritative GPS resolution with rigorous boundary checks:
        1. Validates coordinate existence, numeric types, and coordinate ranges.
        2. Applies city-specific metropolitan operational radii:
           - DELHI_NCR: 75 km (Delhi, Gurgaon, Noida, Ghaziabad, Faridabad)
           - PATNA: 35 km (Patna Municipal Corp, Danapur, Phulwari Sharif, Hajipur)
           - MUMBAI: 60 km (Mumbai Island, Suburban, Thane, Navi Mumbai, Mira-Bhayandar)
           - LUCKNOW: 40 km (Lucknow Urban Agglomeration, Gomti Nagar, Alambagh)
           - BENGALURU: 50 km (BBMP limits, Electronic City, Whitefield, Yelahanka)
        3. At border/overlap coordinates, resolves strictly to the nearest city center.
        4. Rejects out-of-bounds coordinates with HTTP 400.
        """
        if lat is None or lng is None:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Pickup GPS coordinates (latitude and longitude) are strictly required for city resolution."
            )
        try:
            lat = float(lat)
            lng = float(lng)
        except (ValueError, TypeError):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid GPS coordinates: latitude and longitude must be valid floating point numbers."
            )

        if not (-90.0 <= lat <= 90.0) or not (-180.0 <= lng <= 180.0):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"GPS coordinates out of valid range: lat={lat}, lng={lng}. Must be within [-90, 90] and [-180, 180]."
            )

        import math
        city_configs = {
            "DELHI_NCR": {"center": (28.6139, 77.2090), "max_radius_km": 75.0},
            "PATNA": {"center": (25.5941, 85.1376), "max_radius_km": 35.0},
            "MUMBAI": {"center": (19.0760, 72.8777), "max_radius_km": 60.0},
            "LUCKNOW": {"center": (26.8467, 80.9462), "max_radius_km": 40.0},
            "BENGALURU": {"center": (12.9716, 77.5946), "max_radius_km": 50.0},
        }
        closest_city = None
        min_dist_km = float("inf")
        candidate_matches = []

        for city, cfg in city_configs.items():
            c_lat, c_lng = cfg["center"]
            d_lat = math.radians(lat - c_lat)
            d_lng = math.radians(lng - c_lng)
            a = math.sin(d_lat / 2)**2 + math.cos(math.radians(c_lat)) * math.cos(math.radians(lat)) * math.sin(d_lng / 2)**2
            c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
            dist_km = 6371.0 * c

            if dist_km < min_dist_km:
                min_dist_km = dist_km
                closest_city = city

            if dist_km <= cfg["max_radius_km"]:
                candidate_matches.append((dist_km, city))

        # If location falls within one or multiple city radii, pick the closest matching one
        if candidate_matches:
            candidate_matches.sort(key=lambda x: x[0])
            return candidate_matches[0][1]

        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Pickup location ({lat:.4f}, {lng:.4f}) is outside our operational service area (nearest active city '{closest_city}' is {min_dist_km:.1f}km away). Operating coverage is currently available in Delhi NCR, Patna, Mumbai, Lucknow, and Bengaluru."
        )

    async def resolve_operating_city(self, city_code: Optional[str], lat: float, lng: float) -> str:
        # 1. Authoritative resolution from physical GPS coordinates
        coords_city = self.resolve_city_code_from_coords(lat, lng)

        # 2. If client supplied a city_code, verify it does not contradict physical coordinates
        if city_code and city_code.strip():
            clean_city = city_code.strip().upper()
            if clean_city != coords_city:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"City mismatch: Specified city '{clean_city}' does not match coordinates-resolved city '{coords_city}'. Cross-city fare spoofing is prohibited."
                )

        from backend.domain.models import MultiCityOperatingConfig
        cfg = (await self.db.execute(
            select(MultiCityOperatingConfig).where(
                MultiCityOperatingConfig.city_code == coords_city
            )
        )).scalars().first()
        if cfg and cfg.is_active is False:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Operating city '{coords_city}' is currently paused or inactive."
            )
        return coords_city

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

