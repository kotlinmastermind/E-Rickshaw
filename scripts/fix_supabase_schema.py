import asyncio
import os
import sys
from dotenv import load_dotenv

sys.path.insert(0, r"c:\androidAi")
load_dotenv(r"c:\androidAi\.env")

from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy import text, select
from backend.domain.models import VehicleCategory, FareRule

with open(r"c:\androidAi\.env", "r") as f:
    for line in f:
        if "REMOTE_SUPABASE_DATABASE_URL=" in line:
            url = line.split("=", 1)[1].strip().lstrip("#").strip()
            break

async def fix_and_update():
    engine = create_async_engine(url)
    
    # 1. Add missing columns to fare_rules
    async with engine.begin() as conn:
        fare_cols = [
            ("workflow_status", "VARCHAR(32) DEFAULT 'APPROVED'"),
            ("approved_by", "VARCHAR(36)"),
            ("approved_at", "TIMESTAMPTZ"),
            ("approval_reason", "TEXT"),
            ("grace_waiting_minutes", "INTEGER DEFAULT 5"),
            ("per_minute_waiting_cents", "INTEGER DEFAULT 100"),
            ("shared_discount_percentage", "FLOAT DEFAULT 0.0"),
            ("fare_cap_cents", "INTEGER"),
            ("tax_percentage", "FLOAT DEFAULT 5.0"),
            ("amenities_fee_cents", "INTEGER DEFAULT 0"),
            ("effective_from", "TIMESTAMPTZ"),
            ("effective_until", "TIMESTAMPTZ"),
            ("created_by", "VARCHAR(36)")
        ]
        for col, typ in fare_cols:
            try:
                await conn.execute(text(f"ALTER TABLE fare_rules ADD COLUMN IF NOT EXISTS {col} {typ};"))
            except Exception as e:
                print(f"Error adding {col}: {e}")
        print("Fare rules columns verified and synced!")

    # 2. Update categories and fares
    sf = async_sessionmaker(engine, class_=AsyncSession)
    async with sf() as session:
        res = await session.execute(select(VehicleCategory).order_by(VehicleCategory.sort_order))
        cats = {c.code: c for c in res.scalars().all()}
        print("Existing category codes in Supabase:", list(cats.keys()))

        if "SEDAN" in cats:
            c = cats["SEDAN"]
            c.code = "E_RICKSHAW_STANDARD"
            c.name = "Standard E-Rickshaw"
            c.description = "Eco-friendly electric 3-wheeler for quick city commutes and local transit."
            c.capacity = 4
            c.sort_order = 1
            c.is_electric = True
        elif "E_RICKSHAW_STANDARD" not in cats:
            session.add(VehicleCategory(
                code="E_RICKSHAW_STANDARD", name="Standard E-Rickshaw",
                description="Eco-friendly electric 3-wheeler for quick city commutes and local transit.",
                capacity=4, sort_order=1, is_electric=True
            ))

        if "LUXURY_SUV" in cats:
            c = cats["LUXURY_SUV"]
            c.code = "E_RICKSHAW_SHARE"
            c.name = "Shared E-Rickshaw (Eco-Pool)"
            c.description = "Ultra-affordable per-seat shared electric rickshaw on metro and market feeder routes."
            c.capacity = 4
            c.sort_order = 2
            c.is_electric = True
        elif "E_RICKSHAW_SHARE" not in cats:
            session.add(VehicleCategory(
                code="E_RICKSHAW_SHARE", name="Shared E-Rickshaw (Eco-Pool)",
                description="Ultra-affordable per-seat shared electric rickshaw on metro and market feeder routes.",
                capacity=4, sort_order=2, is_electric=True
            ))

        if "EXECUTIVE_LIMO" in cats:
            c = cats["EXECUTIVE_LIMO"]
            c.code = "E_AUTO"
            c.name = "High-Speed Electric Auto"
            c.description = "Modern enclosed L5M electric auto rickshaw with weather-shielding doors and higher speed."
            c.capacity = 3
            c.sort_order = 3
            c.is_electric = True
        elif "E_AUTO" not in cats:
            session.add(VehicleCategory(
                code="E_AUTO", name="High-Speed Electric Auto",
                description="Modern enclosed L5M electric auto rickshaw with weather-shielding doors and higher speed.",
                capacity=3, sort_order=3, is_electric=True
            ))

        if "E_CARGO_LOADER" not in cats:
            session.add(VehicleCategory(
                code="E_CARGO_LOADER", name="E-Loader Cargo",
                description="Electric commercial cargo loader 3-wheeler for parcels, goods, and luggage.",
                capacity=1, sort_order=4, is_electric=True
            ))

        await session.flush()

        res = await session.execute(select(VehicleCategory))
        updated_cats = {c.code: c for c in res.scalars().all()}
        
        fares_res = await session.execute(select(FareRule))
        fares_by_cat = {f.category_id: f for f in fares_res.scalars().all()}

        # 1. Standard: Base ₹20, per km ₹10
        std = updated_cats.get("E_RICKSHAW_STANDARD")
        if std:
            rule = fares_by_cat.get(std.id)
            if rule:
                rule.base_fare_cents = 2000
                rule.per_km_cents = 1000
                rule.per_minute_cents = 100
                rule.minimum_fare_cents = 2000
                rule.cancellation_fee_cents = 1000
                rule.is_active = True
            else:
                session.add(FareRule(category_id=std.id, version=1, base_fare_cents=2000, per_km_cents=1000, per_minute_cents=100, minimum_fare_cents=2000, cancellation_fee_cents=1000, surge_multiplier=1.0, is_active=True))

        # 2. Shared: Base ₹10, per km ₹6
        sh = updated_cats.get("E_RICKSHAW_SHARE")
        if sh:
            rule = fares_by_cat.get(sh.id)
            if rule:
                rule.base_fare_cents = 1000
                rule.per_km_cents = 600
                rule.per_minute_cents = 50
                rule.minimum_fare_cents = 1500
                rule.cancellation_fee_cents = 500
                rule.is_active = True
            else:
                session.add(FareRule(category_id=sh.id, version=1, base_fare_cents=1000, per_km_cents=600, per_minute_cents=50, minimum_fare_cents=1500, cancellation_fee_cents=500, surge_multiplier=1.0, is_active=True))

        # 3. E-Auto: Base ₹30, per km ₹12
        ea = updated_cats.get("E_AUTO")
        if ea:
            rule = fares_by_cat.get(ea.id)
            if rule:
                rule.base_fare_cents = 3000
                rule.per_km_cents = 1200
                rule.per_minute_cents = 150
                rule.minimum_fare_cents = 3000
                rule.cancellation_fee_cents = 1500
                rule.is_active = True
            else:
                session.add(FareRule(category_id=ea.id, version=1, base_fare_cents=3000, per_km_cents=1200, per_minute_cents=150, minimum_fare_cents=3000, cancellation_fee_cents=1500, surge_multiplier=1.0, is_active=True))

        # 4. E-Cargo: Base ₹50, per km ₹15
        ec = updated_cats.get("E_CARGO_LOADER")
        if ec:
            rule = fares_by_cat.get(ec.id)
            if rule:
                rule.base_fare_cents = 5000
                rule.per_km_cents = 1500
                rule.per_minute_cents = 200
                rule.minimum_fare_cents = 6000
                rule.cancellation_fee_cents = 2000
                rule.is_active = True
            else:
                session.add(FareRule(category_id=ec.id, version=1, base_fare_cents=5000, per_km_cents=1500, per_minute_cents=200, minimum_fare_cents=6000, cancellation_fee_cents=2000, surge_multiplier=1.0, is_active=True))

        await session.commit()
        print("ALL 4 E-Rickshaw Categories and Affordable Fares (₹20-₹50) Successfully Configured on Supabase PostgreSQL!")

    await engine.dispose()

if __name__ == "__main__":
    asyncio.run(fix_and_update())
