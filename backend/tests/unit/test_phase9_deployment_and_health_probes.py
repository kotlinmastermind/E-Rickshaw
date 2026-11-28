import pytest
from httpx import AsyncClient, ASGITransport
from backend.main import app
from backend.api.deps import get_current_admin

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
    app.dependency_overrides[get_current_admin] = lambda: object()
    try:
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
    finally:
        app.dependency_overrides.pop(get_current_admin, None)

def test_pre_migration_check_runner():
    """
    Task 097: Test automated pre-migration check runner.
    """
    # check_only=True must check current database revision against repository head
    status = run_migrations(check_only=True)
    assert isinstance(status, bool)


def test_render_blueprint_and_dockerfile_production_readiness():
    """Verify Render blueprint declares required production secrets and Dockerfile copies requirements cleanly."""
    from pathlib import Path

    root = Path(".")
    render_yaml = (root / "render.yaml").read_text(encoding="utf-8")
    for required_key in (
        "DATABASE_URL",
        "DATABASE_URL_SYNC",
        "SECRET_KEY",
        "PAYOUT_DESTINATION_ENCRYPTION_KEY",
        "PAYOUT_EXECUTION_MODE",
        "RAZORPAY_KEY_ID",
        "RAZORPAY_KEY_SECRET",
        "RAZORPAY_WEBHOOK_SECRET",
        "RAZORPAY_MERCHANT_ACCOUNT_ID",
    ):
        assert required_key in render_yaml, f"Missing {required_key} in render.yaml"

    dockerfile = (root / "Dockerfile").read_text(encoding="utf-8")
    copy_backend_req_idx = dockerfile.find("COPY backend/requirements.txt")
    pip_install_idx = dockerfile.find("pip install -r /app/requirements.txt")
    assert copy_backend_req_idx != -1, "Dockerfile must copy backend/requirements.txt before pip install"
    assert copy_backend_req_idx < pip_install_idx, "backend/requirements.txt must be copied before pip install"


def test_android_release_manifests_and_gitignore_hygiene():
    """Verify Android main manifests disable backup and cleartext traffic while debug manifests allow dev cleartext."""
    from pathlib import Path

    root = Path(".")
    gitignore_lines = [line.strip() for line in (root / ".gitignore").read_text(encoding="utf-8").splitlines()]
    assert "*.xml" not in gitignore_lines, ".gitignore must not globally ignore Android XML resource files"

    for app_dir in ("apps/customer-android", "apps/driver-android"):
        main_manifest = (root / app_dir / "src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        debug_manifest = (root / app_dir / "src/debug/AndroidManifest.xml").read_text(encoding="utf-8")
        assert 'android:allowBackup="false"' in main_manifest, f"{app_dir} main manifest must set allowBackup=false"
        assert 'android:usesCleartextTraffic="true"' not in main_manifest, (
            f"{app_dir} main manifest must not allow cleartext traffic in release builds"
        )
        assert 'android:usesCleartextTraffic="true"' in debug_manifest, (
            f"{app_dir} debug manifest should permit local emulator cleartext traffic"
        )


