import sys
import os
import argparse
import asyncio

# Ensure project root is in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))

from datetime import datetime, timezone
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.infrastructure.database.session import async_session_maker, init_db
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, VehicleCategory, Vehicle,
    DriverVehicleAssignment, DriverAvailability, FareRule
)

async def seed_production(session: AsyncSession) -> None:
    """
    Initial Production Bootstrap:
    Seeds ONLY baseline structural system data:
    - 4 Core Vehicle Categories
    - Default Fare Rules (Version 1)
    - Root System Administrator Account
    Strictly NO mock drivers, test rides, or fictitious passengers.
    """
    print("\n--- [PRODUCTION BOOTSTRAP] Initializing System Schema & Core Baseline ---")
    
    # 1. Vehicle Categories
    existing_cat = await session.execute(select(VehicleCategory))
    categories = existing_cat.scalars().all()
    if not categories:
        print("[1/3] Seeding Authoritative Vehicle Categories...")
        standard = VehicleCategory(
            code="E_RICKSHAW_STANDARD",
            name="Standard E-Rickshaw",
            description="Eco-friendly electric 3-wheeler for quick city commutes, metro feeder transit, and local errands.",
            icon_url="https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&w=400&q=80",
            capacity=4,
            sort_order=1,
            is_electric=True
        )
        shared = VehicleCategory(
            code="E_RICKSHAW_SHARE",
            name="Shared E-Rickshaw (Eco-Pool)",
            description="Ultra-affordable per-seat shared electric rickshaw on high-frequency metro and market routes.",
            icon_url="https://images.unsplash.com/photo-1568772585407-9361f9bf3a87?auto=format&fit=crop&w=400&q=80",
            capacity=4,
            sort_order=2,
            is_electric=True
        )
        e_auto = VehicleCategory(
            code="E_AUTO",
            name="High-Speed Electric Auto",
            description="Modern enclosed L5M electric auto rickshaw with weather-shielding doors and higher speed.",
            icon_url="https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=400&q=80",
            capacity=3,
            sort_order=3,
            is_electric=True
        )
        e_cargo = VehicleCategory(
            code="E_CARGO_LOADER",
            name="E-Loader Cargo",
            description="Electric commercial cargo loader 3-wheeler for parcels, shop goods, and heavy luggage.",
            icon_url="https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=400&q=80",
            capacity=1,
            sort_order=4,
            is_electric=True
        )
        session.add_all([standard, shared, e_auto, e_cargo])
        await session.flush()
        categories = [standard, shared, e_auto, e_cargo]
    else:
        print("[1/3] Vehicle categories already present in database.")

    # 2. Fare Rules
    cat_by_code = {c.code: c for c in categories}
    existing_rules = await session.execute(select(FareRule))
    if not existing_rules.scalars().first():
        print("[2/3] Seeding Authoritative Baseline Fare Rules (v1)...")
        rules = [
            FareRule(
                category_id=cat_by_code["E_RICKSHAW_STANDARD"].id, version=1, base_fare_cents=2000,
                per_km_cents=1000, per_minute_cents=100, minimum_fare_cents=2000,
                cancellation_fee_cents=1000, surge_multiplier=1.0, is_active=True
            ),
            FareRule(
                category_id=cat_by_code["E_RICKSHAW_SHARE"].id, version=1, base_fare_cents=1000,
                per_km_cents=600, per_minute_cents=50, minimum_fare_cents=1500,
                cancellation_fee_cents=500, surge_multiplier=1.0, is_active=True
            ),
            FareRule(
                category_id=cat_by_code["E_AUTO"].id, version=1, base_fare_cents=3000,
                per_km_cents=1200, per_minute_cents=150, minimum_fare_cents=3000,
                cancellation_fee_cents=1500, surge_multiplier=1.0, is_active=True
            ),
            FareRule(
                category_id=cat_by_code["E_CARGO_LOADER"].id, version=1, base_fare_cents=5000,
                per_km_cents=1500, per_minute_cents=200, minimum_fare_cents=6000,
                cancellation_fee_cents=2000, surge_multiplier=1.0, is_active=True
            )
        ]
        session.add_all(rules)
    else:
        print("[2/3] Fare rules already present in database.")

    # 3. Root Administrator Account
    from sqlalchemy import or_
    existing_admin = await session.execute(
        select(User).where(or_(User.email == "admin@erickshaw.in", User.phone_number == "+919999900000"))
    )
    if not existing_admin.scalar_one_or_none():
        print("[3/3] Seeding Root Administrator Account (admin@erickshaw.in)...")
        admin = User(
            phone_number="+919999900000",
            email="admin@erickshaw.in",
            full_name="Root Fleet Administrator",
            role="admin",
            is_active=True,
            is_verified=True
        )
        session.add(admin)
    else:
        print("[3/3] Root administrator account already present.")

    await session.commit()
    print("--- [PRODUCTION BOOTSTRAP] Completed Successfully with Zero Mock Data ---")

async def seed_development(session: AsyncSession) -> None:
    """
    Development & Sandbox Seed:
    Seeds mock vehicles, demo driver pilot, and commuter sandbox accounts.
    ONLY executed in local development / QA environments.
    """
    print("\n--- [DEVELOPMENT SEED] Populating Sandbox Test Accounts & Vehicles ---")
    
    # Ensure production baseline exists first
    await seed_production(session)

    # Check for existing dev driver
    existing_dev_user = await session.execute(select(User).where(User.email == "pilot.demo@erickshaw.in"))
    if existing_dev_user.scalar_one_or_none():
        print("Development sandbox data already seeded.")
        return

    res = await session.execute(select(VehicleCategory).where(VehicleCategory.code == "E_RICKSHAW_STANDARD"))
    standard = res.scalar_one_or_none()
    if not standard:
        return

    # Demo fleet vehicle
    v1 = Vehicle(category_id=standard.id, make="Mahindra", model="Treo EV 4-Seater", year=2026, license_plate="DL-01-EV-0001", color="Leaf Green")
    session.add(v1)
    await session.flush()

    # Demo driver
    driver_user = User(
        phone_number="+919876500001",
        email="pilot.demo@erickshaw.in",
        full_name="Demo Driver Partner",
        role="driver",
        is_active=True,
        is_verified=True
    )
    session.add(driver_user)
    await session.flush()

    driver_profile = DriverProfile(
        user_id=driver_user.id,
        license_number="DL-01-2026-DEMO",
        approval_status="APPROVED",
        rating_average=4.9,
        rating_count=50,
        total_trips=50,
        wallet_balance_cents=100000
    )
    session.add(driver_profile)
    await session.flush()

    assignment = DriverVehicleAssignment(driver_id=driver_profile.id, vehicle_id=v1.id, is_active=True)
    availability = DriverAvailability(driver_id=driver_profile.id, is_online=True, is_busy=False)
    session.add_all([assignment, availability])

    # Demo commuter
    cust_user = User(
        phone_number="+919876500002",
        email="commuter.demo@erickshaw.in",
        full_name="Demo Commuter Passenger",
        role="customer",
        is_active=True,
        is_verified=True
    )
    session.add(cust_user)
    await session.flush()

    cust_profile = CustomerProfile(
        user_id=cust_user.id,
        preferred_payment_method="UPI",
        rating_average=5.0,
        rating_count=10
    )
    session.add(cust_profile)

    await session.commit()
    print("--- [DEVELOPMENT SEED] Completed Successfully ---")

async def main():
    parser = argparse.ArgumentParser(description="E-Rickshaw Database Seeding Pipeline")
    parser.add_argument(
        "--env",
        choices=["production", "development"],
        default="production",
        help="Specify 'production' for clean baseline or 'development' for test mock accounts"
    )
    parser.add_argument(
        "--init-tables",
        action="store_true",
        help="Run schema creation and column synchronization before seeding"
    )
    args = parser.parse_args()

    if args.init_tables:
        print("Initializing tables and synchronizing columns...")
        await init_db()

    async with async_session_maker() as session:
        if args.env == "production":
            await seed_production(session)
        else:
            await seed_development(session)

if __name__ == "__main__":
    asyncio.run(main())
