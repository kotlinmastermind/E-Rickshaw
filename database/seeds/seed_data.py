import asyncio
from datetime import datetime, timezone
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.infrastructure.database.session import async_session_maker, init_db
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, VehicleCategory, Vehicle,
    DriverVehicleAssignment, DriverAvailability, FareRule
)

async def seed():
    await init_db()
    async with async_session_maker() as session:
        # Check if already seeded
        res = await session.execute(select(VehicleCategory))
        if res.scalars().first():
            print("Database already contains seed data.")
            return

        print("Seeding E-Rickshaw vehicle categories...")
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

        print("Seeding electric rickshaw fleet vehicles...")
        v1 = Vehicle(category_id=standard.id, make="Mahindra", model="Treo EV 4-Seater", year=2026, license_plate="MH01-ER-1001", color="Leaf Green")
        v2 = Vehicle(category_id=e_auto.id, make="Bajaj", model="RE E-Tec 9.0", year=2026, license_plate="MH01-ER-1002", color="Emerald Green / Yellow")
        v3 = Vehicle(category_id=e_cargo.id, make="Piaggio", model="Ape E-Extra Cargo", year=2026, license_plate="MH01-ER-1003", color="Eco White")
        session.add_all([v1, v2, v3])
        await session.flush()

        print("Seeding administrator account...")
        admin = User(
            phone_number="+919999900000",
            email="admin@erickshaw.in",
            full_name="E-Rickshaw Fleet Operations",
            role="admin",
            is_active=True,
            is_verified=True
        )
        session.add(admin)

        print("Seeding verified certified E-Rickshaw driver...")
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

        # Assign vehicle to driver
        assignment = DriverVehicleAssignment(driver_id=driver_profile.id, vehicle_id=v1.id, is_active=True)
        session.add(assignment)

        # Driver availability
        availability = DriverAvailability(driver_id=driver_profile.id, is_online=True, is_busy=False)
        session.add(availability)

        print("Seeding verified commuter passenger...")
        cust_user = User(
            phone_number="+919876543210",
            email="commuter@erickshaw.in",
            full_name="Sunil Sharma (Daily Commuter)",
            role="customer",
            is_active=True,
            is_verified=True
        )
        session.add(cust_user)
        await session.flush()

        cust_profile = CustomerProfile(
            user_id=cust_user.id,
            preferred_payment_method="UPI",
            emergency_contact_phone="+919811122233",
            rating_average=5.0,
            rating_count=18
        )
        session.add(cust_profile)

        await session.commit()
        print("Database successfully seeded with luxury platform baseline!")

if __name__ == "__main__":
    asyncio.run(seed())
