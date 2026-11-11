from contextlib import asynccontextmanager
import asyncio
import logging
import os
import time
import uuid
from typing import Optional
from fastapi import FastAPI, Request, status, WebSocket, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, HTMLResponse, FileResponse
from fastapi.staticfiles import StaticFiles
from backend.core.config import settings
from backend.infrastructure.database.session import init_db, async_session_maker
from backend.domain.models import VehicleCategory, FareRule, User, AdminRole, AdminUserRole
from backend.infrastructure.security.crypto import hash_password
from backend.domain.state_machine.ride_state_machine import RideTransitionError
from backend.api.routes import (
    auth, customer, driver, vehicles, location,
    fares, rides, payments, admin, ws, health,
    wallets, promos, support, chat, favorites, pools, user_governance,
    safety, loyalty_feedback, driver_onboarding, driver_safety, driver_financials,
    notifications, analytics_bi, dispatch
)
from sqlalchemy import select

logger = logging.getLogger(__name__)

async def _dispatch_scheduled_broadcasts():
    """Process due notifications while this API worker is running."""
    while True:
        try:
            async with async_session_maker() as session:
                from backend.application.services.notification_service import NotificationService
                await NotificationService(session).process_due()
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("Scheduled notification dispatch cycle failed")
        await asyncio.sleep(30)

async def _seed_baseline_data():
    """Resiliently initialize database tables and baseline data with retries."""
    max_retries = 3
    for attempt in range(1, max_retries + 1):
        try:
            # Initialize database tables
            await init_db()
            # Verify live Supabase database connectivity & check available vehicle categories
            async with async_session_maker() as session:
                cat_count_stmt = select(VehicleCategory)
                cat_res = await session.execute(cat_count_stmt)
                existing_cats = cat_res.scalars().all()
                logger.info("Connected to Supabase PostgreSQL: Loaded %d live vehicle categories directly from database.", len(existing_cats))

                # Seed / Sync Root Admin & Super Administrator role unconditionally
                # 1. Ensure Super Administrator Role exists
                stmt = select(AdminRole).where(AdminRole.name == "Super Administrator")
                res = await session.execute(stmt)
                super_role = res.scalar_one_or_none()
                if not super_role:
                    super_role = AdminRole(
                        id=str(uuid.uuid4()),
                        name="Super Administrator",
                        description="Unrestricted access across all operational, financial and governance desks.",
                        is_superuser=True
                    )
                    session.add(super_role)
                    await session.flush()

                # 2. Seed / Sync primary admin users
                admin_specs = [
                    {
                        "email": "kotlindeveloper75620@gmail.com",
                        "phone_number": "+917562084184",
                        "full_name": "BHISHMA"
                    },
                    {
                        "email": "admin@luxurychauffeur.com",
                        "phone_number": "+919999900000",
                        "full_name": "Super Administrator"
                    }
                ]

                default_admin_pwd = hash_password(os.getenv("ADMIN_INITIAL_PASSWORD", "Admin@1234"))

                for spec in admin_specs:
                    u_stmt = select(User).where(User.email == spec["email"])
                    u_res = await session.execute(u_stmt)
                    admin_user = u_res.scalar_one_or_none()

                    if not admin_user:
                        # Also check by phone number if email didn't match
                        p_stmt = select(User).where(User.phone_number == spec["phone_number"])
                        p_res = await session.execute(p_stmt)
                        admin_user = p_res.scalar_one_or_none()

                    if admin_user:
                        admin_user.role = "admin"
                        admin_user.is_active = True
                        admin_user.is_verified = True
                        admin_user.email_verified = True
                        admin_user.mfa_enabled = True
                        if not admin_user.email:
                            admin_user.email = spec["email"]
                        if not admin_user.password_hash or spec["email"] in ["kotlindeveloper75620@gmail.com", "admin@luxurychauffeur.com"]:
                            admin_user.password_hash = default_admin_pwd
                        if not admin_user.mfa_secret:
                            admin_user.mfa_secret = "JBSWY3DPEHPK3PXP"
                    else:
                        admin_user = User(
                            id=str(uuid.uuid4()),
                            phone_number=spec["phone_number"],
                            email=spec["email"],
                            full_name=spec["full_name"],
                            role="admin",
                            is_active=True,
                            is_verified=True,
                            email_verified=True,
                            mfa_enabled=True,
                            mfa_secret="JBSWY3DPEHPK3PXP",
                            password_hash=default_admin_pwd
                        )
                        session.add(admin_user)
                        await session.flush()

                    # Assign Super Administrator role
                    aur_stmt = select(AdminUserRole).where(
                        AdminUserRole.user_id == admin_user.id,
                        AdminUserRole.role_id == super_role.id
                    )
                    aur_res = await session.execute(aur_stmt)
                    if not aur_res.scalar_one_or_none():
                        session.add(AdminUserRole(user_id=admin_user.id, role_id=super_role.id))

                await session.commit()
            logger.info("Database initialization and baseline seeding completed successfully.")
            return True
        except Exception as exc:
            logger.warning("Database startup init/seed attempt %d/%d failed: %s", attempt, max_retries, exc)
            if attempt < max_retries:
                await asyncio.sleep(1.5 * attempt)
            else:
                logger.error("Database connection unavailable during startup lifespan. Application starting in degraded mode.", exc_info=True)
                return False

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize database tables with retry protection
    await _seed_baseline_data()
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
    description="Production-grade API for VAISENTRA MOBILITY.",
    lifespan=lifespan
)

# CORS Configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.BACKEND_CORS_ORIGINS,
    allow_origin_regex=r"https?://(localhost|127\.0\.0\.1)(:\d+)?|https://.*(\.onrender\.com|\.github\.io)",
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
app.include_router(admin.router, prefix="")
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
app.include_router(dispatch.router, prefix=settings.API_V1_STR)

# --- Dynamic Admin Web Panel Hosting on Render ---
STATIC_ADMIN_DIR = os.path.join(os.path.dirname(__file__), "static", "admin")
if os.path.exists(STATIC_ADMIN_DIR):
    assets_dir = os.path.join(STATIC_ADMIN_DIR, "assets")
    if os.path.exists(assets_dir):
        app.mount("/assets", StaticFiles(directory=assets_dir), name="admin_assets")
        app.mount("/admin/assets", StaticFiles(directory=assets_dir), name="admin_nested_assets")

SPA_NO_CACHE_HEADERS = {
    "Cache-Control": "no-cache, no-store, must-revalidate",
    "Pragma": "no-cache",
    "Expires": "0",
}

STATIC_ASSET_EXTENSIONS = (
    ".js", ".css", ".png", ".jpg", ".jpeg", ".svg", ".webp", ".gif",
    ".ico", ".woff", ".woff2", ".ttf", ".eot", ".map", ".json", ".txt"
)

@app.get("/admin", include_in_schema=False)
@app.get("/admin/{full_path:path}", include_in_schema=False)
async def serve_admin_panel(full_path: str = ""):
    """Dynamically serve the Luxury Admin Panel SPA from Render without route collisions."""
    norm = full_path.strip("/")

    # 1. Unknown or sub-path API requests must return JSON 404
    if norm.startswith(("api/", "api", "v1/")) or norm.startswith("admin/api"):
        return JSONResponse(status_code=404, content={"detail": "Not Found"})

    # 2. Missing static assets must return 404 rather than index.html
    if norm.startswith("assets/") or norm.endswith(STATIC_ASSET_EXTENSIONS):
        asset_file = os.path.join(STATIC_ADMIN_DIR, norm)
        if os.path.isfile(asset_file):
            return FileResponse(asset_file)
        return JSONResponse(status_code=404, content={"detail": "Asset not found"})

    # 3. Serve SPA entry point for valid frontend routing
    index_file = os.path.join(STATIC_ADMIN_DIR, "index.html")
    if os.path.exists(index_file):
        return FileResponse(index_file, headers=SPA_NO_CACHE_HEADERS)
    return HTMLResponse("<h1>Luxury Admin Panel initializing on Render...</h1>", status_code=200)

@app.get("/", tags=["Root"])
async def root(request: Request):
    """Welcome and API discovery root endpoint or Admin Panel SPA for web browsers."""
    accept = request.headers.get("accept", "")
    index_file = os.path.join(STATIC_ADMIN_DIR, "index.html")
    # If a browser requests the root URL, dynamically serve the Admin Panel SPA
    if "text/html" in accept and os.path.exists(index_file):
        return FileResponse(index_file, headers=SPA_NO_CACHE_HEADERS)
    return {
        "service": settings.PROJECT_NAME,
        "version": "1.0.0",
        "status": "OPERATIONAL",
        "admin_panel_url": "/admin",
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
                <li><strong>Device Information:</strong> Device model and operating system version for real-time ride tracking and in-app communications.</li>
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

@app.get("/{full_path:path}", include_in_schema=False)
async def catch_all_spa(full_path: str):
    """Fallback handler to route SPA client-side routes (e.g. /dashboard, /rides, /fleet) to index.html."""
    norm = full_path.strip("/")

    # Reject API and system routes with JSON 404
    if norm.startswith(("api/", "api", "health", "docs", "openapi.json", "redoc", "privacy-policy", "ws")):
        return JSONResponse(status_code=404, content={"detail": "Not Found"})

    # Reject missing static asset files with 404
    if norm.startswith("assets/") or norm.endswith(STATIC_ASSET_EXTENSIONS):
        asset_file = os.path.join(STATIC_ADMIN_DIR, norm)
        if os.path.isfile(asset_file):
            return FileResponse(asset_file)
        return JSONResponse(status_code=404, content={"detail": "Asset not found"})

    file_path = os.path.join(STATIC_ADMIN_DIR, norm)
    if os.path.isfile(file_path):
        return FileResponse(file_path)

    index_file = os.path.join(STATIC_ADMIN_DIR, "index.html")
    if os.path.exists(index_file):
        return FileResponse(index_file, headers=SPA_NO_CACHE_HEADERS)
    return JSONResponse(status_code=404, content={"detail": "Not Found"})

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("backend.main:app", host="127.0.0.1", port=8000, reload=True)
