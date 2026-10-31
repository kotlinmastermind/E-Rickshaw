from contextlib import asynccontextmanager
import asyncio
import logging
import time
import uuid
from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, HTMLResponse
from backend.core.config import settings
from backend.infrastructure.database.session import init_db, async_session_maker
from backend.domain.models import VehicleCategory, FareRule, User
from backend.domain.state_machine.ride_state_machine import RideTransitionError
from backend.api.routes import (
    auth, customer, driver, vehicles, location,
    fares, rides, payments, admin, ws, health,
    wallets, promos, support, chat, favorites, pools, user_governance,
    safety, loyalty_feedback, driver_onboarding, driver_safety, driver_financials, admin_governance,
    admin_finance_support, notifications, analytics_bi, infra_ops, enterprise_growth, localization,
    tourism
)
from sqlalchemy import select

logger = logging.getLogger(__name__)

async def _dispatch_scheduled_broadcasts():
    """Process persisted in-app campaigns while this API worker is running."""
    from backend.application.services.admin_finance_support_service import AdminFinanceSupportService
    while True:
        try:
            async with async_session_maker() as session:
                operations = AdminFinanceSupportService(session)
                await operations.dispatch_due_broadcasts()
                await operations.activate_due_commission_rules()
                await operations.process_support_escalations()
                from backend.application.services.notification_service import NotificationService
                await NotificationService(session).process_due()
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("Scheduled broadcast dispatch cycle failed")
        await asyncio.sleep(30)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize database tables
    await init_db()
    # Seed baseline luxury categories and default fare schedules if not present
    async with async_session_maker() as session:
        stmt = select(VehicleCategory)
        res = await session.execute(stmt)
        if not res.scalars().first():
            standard = VehicleCategory(
                code="E_RICKSHAW_STANDARD",
                name="Standard E-Rickshaw",
                description="Eco-friendly electric 3-wheeler for quick city commutes, metro station feeder transit, and local errands.",
                icon_url="https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&w=400&q=80",
                capacity=4,
                sort_order=1,
                is_electric=True,
                emissions_g_per_km=0.0,
                legroom_inches=36.0,
                quietness_db=45.0,
                luggage_large_capacity=1,
                luggage_small_capacity=2,
                wheelchair_accessible=False,
                pet_friendly=True,
                child_seat_available=False
            )
            shared = VehicleCategory(
                code="E_RICKSHAW_SHARE",
                name="Shared E-Rickshaw (Eco-Pool)",
                description="Ultra-affordable per-seat shared electric rickshaw on high-frequency metro and market routes.",
                icon_url="https://images.unsplash.com/photo-1568772585407-9361f9bf3a87?auto=format&fit=crop&w=400&q=80",
                capacity=4,
                sort_order=2,
                is_electric=True,
                emissions_g_per_km=0.0,
                legroom_inches=35.0,
                quietness_db=45.0,
                luggage_large_capacity=0,
                luggage_small_capacity=1,
                wheelchair_accessible=False,
                pet_friendly=False,
                child_seat_available=False
            )
            e_auto = VehicleCategory(
                code="E_AUTO",
                name="High-Speed Electric Auto",
                description="Modern enclosed L5M electric auto rickshaw with weather-shielding doors and higher speed.",
                icon_url="https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=400&q=80",
                capacity=3,
                sort_order=3,
                is_electric=True,
                emissions_g_per_km=0.0,
                legroom_inches=38.0,
                quietness_db=44.0,
                luggage_large_capacity=2,
                luggage_small_capacity=2,
                wheelchair_accessible=True,
                pet_friendly=True,
                child_seat_available=False
            )
            e_cargo = VehicleCategory(
                code="E_CARGO_LOADER",
                name="E-Loader Cargo",
                description="Electric commercial cargo loader 3-wheeler for parcels, shop goods, and heavy luggage.",
                icon_url="https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=400&q=80",
                capacity=1,
                sort_order=4,
                is_electric=True,
                emissions_g_per_km=0.0,
                legroom_inches=34.0,
                quietness_db=46.0,
                luggage_large_capacity=4,
                luggage_small_capacity=4,
                wheelchair_accessible=False,
                pet_friendly=False,
                child_seat_available=False
            )
            session.add_all([standard, shared, e_auto, e_cargo])
            await session.flush()

            # Seed default affordable E-Rickshaw fare rules (in paise/cents: 100 paise = 1 INR)
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

            # Seed Root Admin
            admin_user = User(
                phone_number="+919999900000",
                email="admin@erickshaw.in",
                full_name="E-Rickshaw Fleet Operations",
                role="admin",
                is_active=True,
                is_verified=True
            )
            session.add(admin_user)
            await session.commit()
    dispatcher = asyncio.create_task(_dispatch_scheduled_broadcasts(), name="scheduled-in-app-broadcasts")
    try:
        yield
    finally:
        dispatcher.cancel()
        try:
            await dispatcher
        except asyncio.CancelledError:
            pass

app = FastAPI(
    title=settings.PROJECT_NAME,
    version="1.0.0",
    description="Production-grade API for the E-Rickshaw Ride-Hailing Platform.",
    lifespan=lifespan
)

# CORS Configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.BACKEND_CORS_ORIGINS,
    allow_origin_regex=r"https://.*\.onrender\.com",
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Feature 455 & 467: Request correlation ID & route latency tracking middleware
@app.middleware("http")
async def correlation_and_telemetry_middleware(request: Request, call_next):
    correlation_id = request.headers.get("X-Correlation-ID") or request.headers.get("X-Request-ID") or f"corr-{uuid.uuid4().hex[:12]}"
    start_time = time.perf_counter()
    try:
        response = await call_next(request)
    except Exception as exc:
        duration_ms = (time.perf_counter() - start_time) * 1000.0
        logger.exception("Unhandled error on %s %s (corr=%s)", request.method, request.url.path, correlation_id)
        return JSONResponse(
            status_code=500,
            content={
                "error": {
                    "code": "ERR_INTERNAL_SERVER_ERROR",
                    "message": "An internal error occurred. Please contact E-Rickshaw Support Concierge.",
                    "correlation_id": correlation_id,
                    "path": request.url.path,
                    "status_code": 500
                }
            },
            headers={
                "X-Correlation-ID": correlation_id,
                "X-Response-Time-Ms": f"{duration_ms:.2f}"
            }
        )
    duration_ms = (time.perf_counter() - start_time) * 1000.0
    response.headers["X-Correlation-ID"] = correlation_id
    response.headers["X-Response-Time-Ms"] = f"{duration_ms:.2f}"
    return response

# Custom exception handler for Ride State Machine violations
@app.exception_handler(RideTransitionError)
async def ride_transition_exception_handler(request: Request, exc: RideTransitionError):
    return JSONResponse(
        status_code=status.HTTP_409_CONFLICT,
        content={
            "error": "StateTransitionProhibited",
            "message": exc.message,
            "current_status": exc.current_status,
            "target_status": exc.target_status,
            "actor": exc.actor
        }
    )

# Register API v1 Routers
app.include_router(health.router)
app.include_router(auth.router, prefix=settings.API_V1_STR)
app.include_router(customer.router, prefix=settings.API_V1_STR)
app.include_router(driver.router, prefix=settings.API_V1_STR)
app.include_router(vehicles.router, prefix=settings.API_V1_STR)
app.include_router(location.router, prefix=settings.API_V1_STR)
app.include_router(fares.router, prefix=settings.API_V1_STR)
app.include_router(rides.router, prefix=settings.API_V1_STR)
app.include_router(payments.router, prefix=settings.API_V1_STR)
app.include_router(admin.router, prefix=settings.API_V1_STR)
app.include_router(admin_governance.router, prefix=settings.API_V1_STR)
app.include_router(admin_finance_support.router, prefix=settings.API_V1_STR)
app.include_router(notifications.router, prefix=settings.API_V1_STR)
app.include_router(notifications.admin_router, prefix=settings.API_V1_STR)
app.include_router(ws.router, prefix=settings.API_V1_STR)
app.include_router(wallets.router, prefix=settings.API_V1_STR)
app.include_router(promos.router, prefix=settings.API_V1_STR)
app.include_router(support.router, prefix=settings.API_V1_STR)
app.include_router(chat.router, prefix=settings.API_V1_STR)
app.include_router(favorites.router, prefix=settings.API_V1_STR)
app.include_router(pools.router, prefix=settings.API_V1_STR)
app.include_router(user_governance.router, prefix=settings.API_V1_STR)
app.include_router(safety.router, prefix=settings.API_V1_STR)
app.include_router(loyalty_feedback.router, prefix=settings.API_V1_STR)
app.include_router(driver_onboarding.router, prefix=settings.API_V1_STR)
app.include_router(driver_safety.router, prefix=settings.API_V1_STR)
app.include_router(driver_financials.router, prefix=settings.API_V1_STR)
app.include_router(analytics_bi.router, prefix=settings.API_V1_STR)
app.include_router(infra_ops.router, prefix=settings.API_V1_STR)
app.include_router(enterprise_growth.router, prefix=settings.API_V1_STR)
app.include_router(localization.router, prefix=settings.API_V1_STR)
app.include_router(tourism.router, prefix=settings.API_V1_STR)

@app.get("/", tags=["Root"])
async def root():
    """Welcome and API discovery root endpoint."""
    return {
        "service": settings.PROJECT_NAME,
        "version": "1.0.0",
        "status": "OPERATIONAL",
        "docs_url": "/docs",
        "health_url": "/health/ready",
        "privacy_policy": "/privacy-policy",
        "api_v1_prefix": settings.API_V1_STR
    }

@app.get("/privacy-policy", response_class=HTMLResponse)
async def privacy_policy():
    """Google Play Store compliant public Privacy Policy."""
    return """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Privacy Policy - E-Rickshaw & E-Rickshaw Partner</title>
        <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #2d3748; max-width: 820px; margin: 0 auto; padding: 24px; background-color: #f7fafc; }
            .container { background: #ffffff; padding: 40px; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); }
            h1 { color: #1a202c; border-bottom: 2px solid #22c55e; padding-bottom: 12px; }
            h2 { color: #15803d; margin-top: 28px; }
            .highlight { background-color: #dcfce7; padding: 12px; border-left: 4px solid #16a34a; border-radius: 4px; margin: 16px 0; }
            ul { padding-left: 20px; }
            li { margin-bottom: 8px; }
            footer { margin-top: 40px; font-size: 0.9em; color: #718096; text-align: center; }
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Privacy Policy</h1>
            <p><strong>Effective Date:</strong> October 27, 2026</p>
            <p>This Privacy Policy applies to the <strong>E-Rickshaw</strong> (Customer) and <strong>E-Rickshaw Partner</strong> (Driver) mobile applications operated by E-Rickshaw Mobility Platform.</p>

            <h2>1. Information We Collect</h2>
            <ul>
                <li><strong>Personal Identifiers:</strong> Mobile telephone number, full name, email address (for booking confirmations and receipts).</li>
                <li><strong>Precise Location Data:</strong> Real-time GPS location (latitude, longitude, heading) collected to calculate pickup distance, navigation routes, and accurate affordable fares.</li>
                <li><strong>Device Information:</strong> Device model, operating system version, and Firebase Cloud Messaging (FCM) tokens for ride arrival notifications.</li>
                <li><strong>Payment Transaction Data:</strong> Payment transaction IDs and settlement statuses processed securely via certified payment gateways (Razorpay/UPI). We do not store raw card numbers.</li>
            </ul>

            <div class="highlight">
                <strong>Location Data Disclosure (Google Play Compliance):</strong><br>
                • <strong>Customer App:</strong> We access precise location while the app is active to locate nearby electric rickshaws and display pickup ETAs.<br>
                • <strong>Driver Partner App:</strong> We collect precise location data continuously—including when the app is running in the background via a persistent Foreground Service (<code>FOREGROUND_SERVICE_LOCATION</code>)—to enable accurate ride dispatch, passenger live telemetry, route navigation, and safety verification throughout the active duty period.
            </div>

            <h2>2. How We Use Your Information</h2>
            <ul>
                <li>To allocate and dispatch electric rickshaw rides matching customer pickup requests.</li>
                <li>To calculate transparent, affordable tariffs based on actual distance and elapsed travel time.</li>
                <li>To process digital payments and driver earnings settlements.</li>
                <li>To ensure passenger and driver safety through real-time telemetry and state verification.</li>
            </ul>

            <h2>3. Data Storage, Security & Cloud Providers</h2>
            <p>Your data is encrypted in transit using Transport Layer Security (TLS 1.3 / HTTPS) and stored in enterprise-grade secured cloud infrastructure with access controls, cryptographic tokens, and row-level security.</p>

            <h2>4. User Rights & Account Deletion Policy</h2>
            <p>You have full ownership of your data. You may request account deactivation and personal data deletion at any time by:</p>
            <ul>
                <li>Using the in-app <strong>"Delete Account"</strong> feature within your Profile settings.</li>
                <li>Submitting a formal request to our privacy team at <code>privacy@erickshaw.in</code>.</li>
            </ul>
            <p>Upon request, your personal credentials will be permanently anonymized and purged from active systems within 30 days.</p>

            <h2>5. Contact Us</h2>
            <p>If you have any questions regarding this policy, please reach out to our privacy compliance officer at:</p>
            <p><strong>Email:</strong> support@erickshaw.in | grievance@erickshaw.in<br>
            <strong>Operating Location:</strong> Mumbai, Maharashtra, India</p>

            <footer>
                &copy; 2026 E-Rickshaw Mobility Platform. All rights reserved.
            </footer>
        </div>
    </body>
    </html>
    """

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("backend.main:app", host="0.0.0.0", port=8000, reload=True)
