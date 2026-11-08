"""
Production Database Sanitization & Dummy Data Cleanup Script.
Clears all transactional, dummy mock, and authentication session data while
preserving structural data (Vehicle Categories, Fare Rules, Geofence Zones,
Surge Rules, Notification Templates, Admin Roles, Permissions, and Root Admin).
"""

import asyncio
import logging
from sqlalchemy import text, select, func, or_
from backend.infrastructure.database.session import async_session_maker
from backend.infrastructure.cache.redis_client import redis_client
from backend.domain.models import VehicleCategory, FareRule, User, Base

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("cleanup")

TRANSACTIONAL_TABLES = [
    # Auth & Tokens
    "sessions",
    "device_tokens",
    
    # Telemetry & Metrics
    "route_latency_metrics",
    "audit_logs",
    "trip_share_links",
    
    # Safety, Incidents & Support
    "lost_item_reports",
    "incident_evidence",
    "incident_reports",
    "emergency_alerts",
    "driver_onboarding_tickets",
    "driver_fatigue_checks",
    "support_messages",
    "support_tickets",
    
    # Driver Operational & Financial History
    "driver_shifts",
    "driver_weekly_goals",
    "driver_payout_accounts",
    "driver_ledger_entries",
    "driver_incentive_progress",
    "driver_daily_targets",
    "driver_payout_requests",
    "driver_locations",
    "driver_availability",
    "driver_vehicle_assignments",
    "driver_documents",
    
    # Pooled & Standard Rides
    "pooled_trip_messages",
    "pooled_trip_stops",
    "pooled_bookings",
    "pooled_trips",
    "shared_ride_preferences",
    "ride_chat_messages",
    "ride_chats",
    "driver_ratings",
    "fare_disputes",
    "payment_refunds",
    "ride_fare_splits",
    "payments",
    "ride_status_events",
    "ride_assignments",
    "ride_stops",
    "rides",
    "fare_estimates",
    
    # User-level transactionals
    "saved_payment_methods",
    "wallet_transactions",
    "wallets",
    "emergency_contacts",
    "favorite_locations",
    "saved_location_pins",
    "referral_records",
    "promo_redemptions",
    "broadcast_deliveries",
    "broadcast_notifications",
    "notifications",
    "customer_profiles",
    "driver_profiles",
    "admin_user_roles",
    "vehicles",
]

async def clear_backend_data():
    logger.info("Starting production database sanitization...")

    async with async_session_maker() as session:
        # 1. Truncate / delete all transactional dummy records
        for tbl in TRANSACTIONAL_TABLES:
            try:
                res = await session.execute(text(f"DELETE FROM {tbl}"))  # nosec B608
                logger.info(f"Cleared table: {tbl} (rows removed)")
            except Exception as e:
                logger.warning(f"Note clearing {tbl}: {e}")

        # 2. Clear non-admin users, keep only structural Root Admin
        try:
            await session.execute(text("DELETE FROM users WHERE role != 'admin' OR email IS NULL OR email NOT IN ('admin@erickshaw.in', 'admin@luxurychauffeur.in')"))
            logger.info("Cleared all non-admin dummy users.")
        except Exception as e:
            logger.warning(f"Note clearing dummy users: {e}")

        # 3. Verify / Ensure Root Admin is intact
        res = await session.execute(
            select(User).where(
                or_(User.email == "admin@erickshaw.in", User.phone_number == "+919999900000")
            )
        )
        admin = res.scalars().first()
        if not admin:
            admin = User(
                phone_number="+919999900000",
                email="admin@erickshaw.in",
                full_name="E-Rickshaw Fleet Operations",
                role="admin",
                is_active=True,
                is_verified=True
            )
            session.add(admin)
            logger.info("Created pristine Root Admin account.")
        else:
            admin.role = "admin"
            admin.email = "admin@erickshaw.in"
            admin.is_active = True
            admin.is_verified = True
            logger.info(f"Verified & updated Root Admin: {admin.email}")

        # 4. Verify Structural Vehicle Categories
        cat_count = (await session.execute(select(func.count(VehicleCategory.id)))).scalar()
        logger.info(f"Structural Vehicle Categories verified: {cat_count}")

        # 5. Verify Fare Rules
        fare_count = (await session.execute(select(func.count(FareRule.id)))).scalar()
        logger.info(f"Structural Fare Rules verified: {fare_count}")

        await session.commit()
        logger.info("Database sanitization committed successfully!")

    # 6. Clear Upstash Redis Cache (OTPs, sessions, cooldowns)
    try:
        keys_to_clear = ["otp_val:*", "otp_cooldown:*", "session:*", "rate_limit:*", "driver_loc:*"]
        cleared_count = 0
        for pattern in keys_to_clear:
            try:
                keys = await redis_client.keys(pattern)
                if keys:
                    for k in keys:
                        await redis_client.delete(k)
                        cleared_count += 1
            except Exception:
                pass
        logger.info(f"Cleared {cleared_count} volatile cached items from Upstash Redis.")
    except Exception as e:
        logger.warning(f"Redis cache flush note: {e}")

    logger.info("=== BACKEND SANITIZATION COMPLETE. ONLY STRUCTURAL DATA REMAINS. ===")

if __name__ == "__main__":
    asyncio.run(clear_backend_data())

