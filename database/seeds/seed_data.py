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

        print("Seeding luxury vehicle categories...")
        sedan = VehicleCategory(
            code="SEDAN",
            name="Executive Sedan",
            description="Mercedes-Benz E-Class & BMW 5 Series. Uncompromising executive travel.",
            icon_url="https://images.unsplash.com/photo-1555215695-3004980ad54e?auto=format&fit=crop&w=400&q=80",
            capacity=3,
            sort_order=1
        )
        suv = VehicleCategory(
            code="LUXURY_SUV",
            name="Prestige SUV",
            description="Range Rover Autobiography & Lexus LX600. Spacious, commanding luxury.",
            icon_url="https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=400&q=80",
            capacity=5,
            sort_order=2
        )
        limo = VehicleCategory(
            code="EXECUTIVE_LIMO",
            name="Royal Limousine",
            description="Mercedes-Maybach S680. Handcrafted luxury, champagne cooler, reclining suites.",
            icon_url="https://images.unsplash.com/photo-1563720223185-11003d516935?auto=format&fit=crop&w=400&q=80",
            capacity=4,
            sort_order=3
        )
        session.add_all([sedan, suv, limo])
        await session.flush()

        print("Seeding fare rules (version 1)...")
        rules = [
            FareRule(
                category_id=sedan.id, version=1, base_fare_cents=50000,
                per_km_cents=3500, per_minute_cents=500, minimum_fare_cents=100000,
                cancellation_fee_cents=30000, surge_multiplier=1.0, is_active=True
            ),
            FareRule(
                category_id=suv.id, version=1, base_fare_cents=75000,
                per_km_cents=5000, per_minute_cents=750, minimum_fare_cents=150000,
                cancellation_fee_cents=45000, surge_multiplier=1.0, is_active=True
            ),
            FareRule(
                category_id=limo.id, version=1, base_fare_cents=150000,
                per_km_cents=9000, per_minute_cents=1500, minimum_fare_cents=300000,
                cancellation_fee_cents=100000, surge_multiplier=1.0, is_active=True
            )
        ]
        session.add_all(rules)

        print("Seeding fleet vehicles...")
        v1 = Vehicle(category_id=sedan.id, make="Mercedes-Benz", model="E-Class E350d", year=2025, license_plate="MH01-LX-0001", color="Obsidian Black")
        v2 = Vehicle(category_id=suv.id, make="Range Rover", model="Autobiography LWB", year=2026, license_plate="MH01-LX-0002", color="Santorini Black")
        v3 = Vehicle(category_id=limo.id, make="Mercedes-Maybach", model="S680 V12", year=2026, license_plate="MH01-LX-0003", color="Kalahari Gold / Obsidian")
        session.add_all([v1, v2, v3])
        await session.flush()

        print("Seeding administrator account...")
        admin = User(
            phone_number="+919999900000",
            email="director@luxurychauffeur.com",
            full_name="Fleet Director",
            role="admin",
            is_active=True,
            is_verified=True
        )
        session.add(admin)

        print("Seeding verified master chauffeur...")
        driver_user = User(
            phone_number="+919876500001",
            email="chauffeur.sterling@luxury.io",
            full_name="Rajeshwar Varma (Executive Chauffeur)",
            role="driver",
            is_active=True,
            is_verified=True
        )
        session.add(driver_user)
        await session.flush()

        driver_profile = DriverProfile(
            user_id=driver_user.id,
            license_number="DL-MH01-2020-009988",
            approval_status="APPROVED",
            rating_average=4.98,
            rating_count=342,
            total_trips=340,
            wallet_balance_cents=2540000
        )
        session.add(driver_profile)
        await session.flush()

        # Assign vehicle to driver
        assignment = DriverVehicleAssignment(driver_id=driver_profile.id, vehicle_id=v1.id, is_active=True)
        session.add(assignment)

        # Driver availability
        availability = DriverAvailability(driver_id=driver_profile.id, is_online=True, is_busy=False)
        session.add(availability)

        print("Seeding distinguished passenger...")
        cust_user = User(
            phone_number="+919876543210",
            email="guest.vip@royal.io",
            full_name="Lord Sterling",
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
