import time
import os
from datetime import datetime, timezone
from typing import Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from backend.infrastructure.database.session import get_db
from backend.infrastructure.cache.redis_client import redis_client
from backend.infrastructure.telemetry.crash_reporter import crash_reporter
from backend.api.routes.ws import ws_manager

router = APIRouter(tags=["Health & Observability"])

_START_TIME = time.time()

@router.get("/health/live")
@router.get("/healthz")
@router.get("/health")
async def liveness():
    """
    Load balancer liveness probe. Returns immediately if the FastAPI worker process is active.
    """
    return {
        "status": "LIVE",
        "service": "e-rickshaw-api",
        "timestamp": datetime.now(timezone.utc).isoformat()
    }

@router.get("/health/ready")
async def readiness(db: AsyncSession = Depends(get_db)):
    """
    Load balancer readiness probe. Verifies database read/write readiness and Redis availability.
    """
    db_start = time.time()
    db_healthy = False
    db_latency_ms = 0.0
    try:
        res = await db.execute(text("SELECT 1"))
        res.scalar_one()
        db_latency_ms = round((time.time() - db_start) * 1000, 2)
        db_healthy = True
    except Exception as e:
        db_healthy = False

    redis_health = await redis_client.health_check()
    is_ready = db_healthy

    status_code = status.HTTP_200_OK if is_ready else status.HTTP_503_SERVICE_UNAVAILABLE
    response_data = {
        "status": "READY" if is_ready else "UNHEALTHY",
        "checks": {
            "database": {
                "status": "HEALTHY" if db_healthy else "DOWN",
                "latency_ms": db_latency_ms
            },
            "redis_cache": {
                "status": redis_health["status"],
                "backend": redis_health["backend"],
                "latency_ms": redis_health["latency_ms"]
            }
        },
        "timestamp": datetime.now(timezone.utc).isoformat()
    }
    if not is_ready:
        raise HTTPException(status_code=status_code, detail=response_data)
    return response_data

@router.get("/health/metrics")
async def operational_metrics(db: AsyncSession = Depends(get_db)) -> Dict[str, Any]:
    """
    Detailed operational diagnostics endpoint for internal SRE monitoring and Admin System Health.
    """
    uptime_seconds = round(time.time() - _START_TIME, 1)

    # Measure DB latency
    db_start = time.time()
    db_ok = True
    try:
        await db.execute(text("SELECT 1"))
        db_latency_ms = round((time.time() - db_start) * 1000, 2)
    except Exception:
        db_ok = False
        db_latency_ms = -1.0

    # Redis metrics
    redis_health = await redis_client.health_check()

    # Active WebSockets
    active_ride_sockets = sum(len(conns) for conns in ws_manager.ride_channels.values())
    active_driver_sockets = len(ws_manager.driver_channels)

    # Recent crashes
    recent_crashes = crash_reporter.get_recent_crashes(limit=5)

    return {
        "service": "luxury-ride-backend",
        "environment": getattr(crash_reporter, "environment", "development"),
        "release": getattr(crash_reporter, "release_version", "v3.0.0"),
        "uptime_seconds": uptime_seconds,
        "database": {
            "status": "HEALTHY" if db_ok else "UNHEALTHY",
            "latency_ms": db_latency_ms
        },
        "redis_cache": redis_health,
        "websockets": {
            "active_ride_subscribers": active_ride_sockets,
            "active_driver_subscribers": active_driver_sockets,
            "total_connected": active_ride_sockets + active_driver_sockets
        },
        "telemetry": {
            "recent_crashes_count": len(recent_crashes),
            "recent_crashes": recent_crashes
        },
        "system": {
            "pid": os.getpid(),
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
    }

@router.post("/health/test-crash")
async def trigger_test_crash():
    """
    Synthetic crash endpoint to verify crash reporting and exception telemetry pipeline.
    """
    event_id = crash_reporter.trigger_test_crash("Diagnostic validation of Phase 3 crash telemetry")
    return {
        "status": "CRASH_CAPTURED",
        "event_id": event_id,
        "message": "Synthetic exception successfully captured by crash reporting pipeline."
    }
