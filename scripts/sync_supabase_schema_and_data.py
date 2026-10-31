import asyncio
import os
import sys
from dotenv import load_dotenv

# Ensure backend can be imported
sys.path.insert(0, r"c:\androidAi")
load_dotenv(r"c:\androidAi\.env")

from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy import text, select

# Import Base and all domain models so they are registered in metadata
from backend.infrastructure.database.session import Base
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, VehicleCategory, Vehicle,
    DriverVehicleAssignment, DriverAvailability, FareRule
)

# Read Supabase URL from .env
with open(r"c:\androidAi\.env", "r") as f:
    lines = f.readlines()

pg_async_url = None
for line in lines:
    if "REMOTE_SUPABASE_DATABASE_URL=" in line:
        pg_async_url = line.split("=", 1)[1].strip().lstrip("#").strip()
        break

if not pg_async_url:
    pg_async_url = os.getenv("REMOTE_SUPABASE_DATABASE_URL")

print("Initializing Supabase schema on:", pg_async_url[:50] + "...")

async def run_supabase_init():
    engine = create_async_engine(
        pg_async_url,
        echo=False,
        future=True,
        connect_args={"timeout": 15, "command_timeout": 15}
    )
    
    # 1. Create all tables
    print("Creating all tables in Supabase PostgreSQL...")
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    print("All tables created successfully!")

    # 2. Check and seed E-Rickshaw categories and fares
    session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    async with session_factory() as session:
        res = await session.execute(select(VehicleCategory))
        categories = res.scalars().all()
        print(f"Existing VehicleCategories in Supabase: {len(categories)}")
        
        if not categories:
            print("Seeding authentic E-Rickshaw categories...")
            standard = VehicleCategory(
                code="E_RICKSHAW_STANDARD",
                name="Standard E-Rickshaw",
                description="Eco-friendly electric 3-wheeler for quick city commutes, metro station feeder transit, and local errands.",
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

            print("Seeding affordable E-Rickshaw fare rules (version 1)...")
            rules = [
                FareRule(
                    category_id=standard.id, version=1, base_fare_cents=2000,
                    per_km_cents=1000, per_minute_cents=100, minimum_fare_cents=2000,
                    cancellation_fee_cents=1000, surge_multiplier=1.0, is_active=True
                ),
                FareRule(
                    category_id=shared.id, version=1, base_fare_cents=1000,
                    per_km_cents=600, per_minute_cents=50, minimum_fare_cents=1500,
                    cancellation_fee_cents=500, surge_multiplier=1.0, is_active=True
                ),
                FareRule(
                    category_id=e_auto.id, version=1, base_fare_cents=3000,
                    per_km_cents=1200, per_minute_cents=150, minimum_fare_cents=3000,
                    cancellation_fee_cents=1500, surge_multiplier=1.0, is_active=True
                ),
                FareRule(
                    category_id=e_cargo.id, version=1, base_fare_cents=5000,
                    per_km_cents=1500, per_minute_cents=200, minimum_fare_cents=6000,
                    cancellation_fee_cents=2000, surge_multiplier=1.0, is_active=True
                )
            ]
            session.add_all(rules)

            # Seed Fleet Vehicles
            v1 = Vehicle(category_id=standard.id, make="Mahindra", model="Treo EV 4-Seater", year=2026, license_plate="MH01-ER-1001", color="Leaf Green")
            v2 = Vehicle(category_id=e_auto.id, make="Bajaj", model="RE E-Tec 9.0", year=2026, license_plate="MH01-ER-1002", color="Emerald Green / Yellow")
            v3 = Vehicle(category_id=e_cargo.id, make="Piaggio", model="Ape E-Extra Cargo", year=2026, license_plate="MH01-ER-1003", color="Eco White")
            session.add_all([v1, v2, v3])
            await session.flush()

            # Seed Operations Admin
            admin = User(
                phone_number="+919999900000",
                email="admin@erickshaw.in",
                full_name="E-Rickshaw Fleet Operations",
                role="admin",
                is_active=True,
                is_verified=True
            )
            session.add(admin)

            # Seed E-Rickshaw Pilot
            driver_user = User(
                phone_number="+919876500001",
                email="pilot.ramesh@erickshaw.in",
                full_name="Ramesh Kumar (Certified Pilot)",
                role="driver",
                is_active=True,
                is_verified=True
            )
            session.add(driver_user)
            await session.flush()

            driver_profile = DriverProfile(
                user_id=driver_user.id,
                license_number="DL-MH01-2022-008811",
                approval_status="APPROVED",
                rating_average=4.95,
                rating_count=340,
                total_trips=340,
                wallet_balance_cents=254000
            )
            session.add(driver_profile)
            await session.flush()

            assignment = DriverVehicleAssignment(driver_id=driver_profile.id, vehicle_id=v1.id, is_active=True)
            session.add(assignment)

            availability = DriverAvailability(driver_id=driver_profile.id, is_online=True, is_busy=False)
            session.add(availability)

            # Seed Commuter
            cust_user = User(
                phone_number="+919876543210",
                email="commuter@erickshaw.in",
                full_name="Sunil Sharma (Daily Commuter)",
                role="customer",
                is_active=True,
                is_verified=True
            )
            session.add(cust_user)
            await session.commit()
            print("E-Rickshaw baseline seed successfully inserted into Supabase PostgreSQL!")
        else:
            print("Supabase already contains VehicleCategories. Updating categories if needed...")

    await engine.dispose()
    print("Supabase PostgreSQL setup completed!")

if __name__ == "__main__":
    asyncio.run(run_supabase_init())
