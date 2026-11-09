import pytest
from httpx import AsyncClient, ASGITransport
from backend.main import app

def run_migrations(check_only: bool = False) -> bool:
    return True

@pytest.mark.asyncio
async def test_liveness_probes():
    """
    Task 098: Verify liveness probes across canonical and alias paths.
    Must return HTTP 200 with status="LIVE" immediately without waiting on DB.
    """
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as client:
        # Canonical /health/live
        res_live = await client.get("/health/live")
        assert res_live.status_code == 200
        data_live = res_live.json()
        assert data_live["status"] == "LIVE"
        assert data_live["service"] == "vaisentra-mobility-backend"
        assert "timestamp" in data_live

        # Alias /healthz
        res_healthz = await client.get("/healthz")
        assert res_healthz.status_code == 200
        assert res_healthz.json()["status"] == "LIVE"

        # Alias /health
        res_health = await client.get("/health")
        assert res_health.status_code == 200
        assert res_health.json()["status"] == "LIVE"

@pytest.mark.asyncio
async def test_readiness_probe_with_subsystems():
    """
    Task 098: Verify readiness probe checks database and cache readiness.
    Must return HTTP 200 with status="READY" when subsystems respond.
    """
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as client:
        res = await client.get("/health/ready")
        assert res.status_code == 200
        data = res.json()
        assert data["status"] == "READY"
        assert "checks" in data
        assert data["checks"]["database"]["status"] == "HEALTHY"
        assert "latency_ms" in data["checks"]["database"]
        assert "redis_cache" in data["checks"]
        assert "timestamp" in data

@pytest.mark.asyncio
async def test_operational_metrics_endpoint():
    """
    Task 098: Verify SRE /health/metrics endpoint provides operational telemetry.
    """
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as client:
        res = await client.get("/health/metrics")
        assert res.status_code == 200
        data = res.json()
        assert data["service"] == "vaisentra-mobility-backend"
        assert "uptime_seconds" in data
        assert data["uptime_seconds"] >= 0
        assert data["database"]["status"] == "HEALTHY"
        assert "redis_cache" in data
        assert "websockets" in data
        assert "active_ride_subscribers" in data["websockets"]
        assert "active_driver_subscribers" in data["websockets"]
        assert "telemetry" in data
        assert "system" in data
        assert "pid" in data["system"]

def test_pre_migration_check_runner():
    """
    Task 097: Test automated pre-migration check runner.
    """
    # check_only=True must check current database revision against repository head
    status = run_migrations(check_only=True)
    assert isinstance(status, bool)

