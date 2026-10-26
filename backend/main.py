from contextlib import asynccontextmanager
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
    wallets, promos, support, chat, favorites, pools
)
from sqlalchemy import select

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize database tables
    await init_db()
    # Seed baseline luxury categories and default fare schedules if not present
    async with async_session_maker() as session:
        stmt = select(VehicleCategory)
        res = await session.execute(stmt)
        if not res.scalars().first():
            sedan = VehicleCategory(
                code="SEDAN",
                name="Executive Sedan",
                description="Mercedes-Benz E-Class / BMW 5 Series with premium chauffeur amenities.",
                icon_url="https://images.unsplash.com/photo-1555215695-3004980ad54e?auto=format&fit=crop&w=400&q=80",
                capacity=3,
                sort_order=1
            )
            suv = VehicleCategory(
                code="LUXURY_SUV",
                name="Prestige SUV",
                description="Range Rover Autobiography / Lexus LX600 with spacious seating and privacy tint.",
                icon_url="https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=400&q=80",
                capacity=5,
                sort_order=2
            )
            limo = VehicleCategory(
                code="EXECUTIVE_LIMO",
                name="Royal Limousine",
                description="Mercedes-Maybach S680 with rear champagne bar, reclining massage seating, and dedicated escort.",
                icon_url="https://images.unsplash.com/photo-1563720223185-11003d516935?auto=format&fit=crop&w=400&q=80",
                capacity=4,
                sort_order=3
            )
            session.add_all([sedan, suv, limo])
            await session.flush()

            # Seed default fare rules
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

            # Seed Root Admin
            admin_user = User(
                phone_number="+919999900000",
                email="admin@luxurychauffeur.com",
                full_name="Fleet Director",
                role="admin",
                is_active=True,
                is_verified=True
            )
            session.add(admin_user)
            await session.commit()
    yield

app = FastAPI(
    title=settings.PROJECT_NAME,
    version="1.0.0",
    description="Production-grade API for the Premium Luxury Ride-Hailing Platform Phase 1 MVP.",
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
app.include_router(ws.router, prefix=settings.API_V1_STR)
app.include_router(wallets.router, prefix=settings.API_V1_STR)
app.include_router(promos.router, prefix=settings.API_V1_STR)
app.include_router(support.router, prefix=settings.API_V1_STR)
app.include_router(chat.router, prefix=settings.API_V1_STR)
app.include_router(favorites.router, prefix=settings.API_V1_STR)
app.include_router(pools.router, prefix=settings.API_V1_STR)

@app.get("/privacy-policy", response_class=HTMLResponse)
async def privacy_policy():
    """Google Play Store compliant public Privacy Policy."""
    return """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Privacy Policy - Grand Chauffeur & Grand Chauffeur Partner</title>
        <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #2d3748; max-width: 820px; margin: 0 auto; padding: 24px; background-color: #f7fafc; }
            .container { background: #ffffff; padding: 40px; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); }
            h1 { color: #1a202c; border-bottom: 2px solid #ecc94b; padding-bottom: 12px; }
            h2 { color: #2b6cb0; margin-top: 28px; }
            .highlight { background-color: #feebc8; padding: 12px; border-left: 4px solid #dd6b20; border-radius: 4px; margin: 16px 0; }
            ul { padding-left: 20px; }
            li { margin-bottom: 8px; }
            footer { margin-top: 40px; font-size: 0.9em; color: #718096; text-align: center; }
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Privacy Policy</h1>
            <p><strong>Effective Date:</strong> October 25, 2026</p>
            <p>This Privacy Policy applies to the <strong>Grand Chauffeur</strong> (Customer) and <strong>Grand Chauffeur Partner</strong> (Driver) mobile applications operated by Grand Chauffeur Luxury Travel Platform.</p>

            <h2>1. Information We Collect</h2>
            <ul>
                <li><strong>Personal Identifiers:</strong> Mobile telephone number, full name, email address (for booking confirmations and receipts).</li>
                <li><strong>Precise Location Data:</strong> Real-time GPS location (latitude, longitude, heading) collected to calculate pickup distance, navigation routes, and accurate fares.</li>
                <li><strong>Device Information:</strong> Device model, operating system version, and Firebase Cloud Messaging (FCM) tokens for trip arrival notifications.</li>
                <li><strong>Payment Transaction Data:</strong> Payment transaction IDs and settlement statuses processed securely via certified payment gateways (Razorpay/UPI). We do not store raw card numbers.</li>
            </ul>

            <div class="highlight">
                <strong>Location Data Disclosure (Google Play Compliance):</strong><br>
                • <strong>Customer App:</strong> We access precise location while the app is active to locate nearby luxury chauffeurs and display pickup ETAs.<br>
                • <strong>Driver Partner App:</strong> We collect precise location data continuously—including when the app is running in the background via a persistent Foreground Service (<code>FOREGROUND_SERVICE_LOCATION</code>)—to enable accurate ride dispatch, passenger live telemetry, route navigation, and safety verification throughout the active duty period.
            </div>

            <h2>2. How We Use Your Information</h2>
            <ul>
                <li>To allocate and dispatch executive chauffeur rides matching customer pickup requests.</li>
                <li>To calculate transparent tariffs based on actual distance and elapsed travel time.</li>
                <li>To process digital payments and chauffeur earnings settlements.</li>
                <li>To ensure passenger and chauffeur safety through real-time telemetry and state verification.</li>
            </ul>

            <h2>3. Data Storage, Security & Cloud Providers</h2>
            <p>Your data is encrypted in transit using Transport Layer Security (TLS 1.3 / HTTPS) and stored in enterprise-grade secured cloud infrastructure with access controls, cryptographic tokens, and row-level security.</p>

            <h2>4. User Rights & Account Deletion Policy</h2>
            <p>You have full ownership of your data. You may request account deactivation and personal data deletion at any time by:</p>
            <ul>
                <li>Using the in-app <strong>"Delete Account"</strong> feature within your Profile settings.</li>
                <li>Submitting a formal request to our privacy team at <code>privacy@grandchauffeur.com</code>.</li>
            </ul>
            <p>Upon request, your personal credentials will be permanently anonymized and purged from active systems within 30 days.</p>

            <h2>5. Contact Us</h2>
            <p>If you have any questions regarding this policy, please reach out to our privacy compliance officer at:</p>
            <p><strong>Email:</strong> support@grandchauffeur.com | director@luxurychauffeur.com<br>
            <strong>Operating Location:</strong> Mumbai, Maharashtra, India</p>

            <footer>
                &copy; 2026 Grand Chauffeur Platform. All rights reserved.
            </footer>
        </div>
    </body>
    </html>
    """

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("backend.main:app", host="0.0.0.0", port=8000, reload=True)
