"""
Tests for Admin Runtime Fare Configuration:
Verifies that administrators can dynamically adjust base fares, per-km rates, minimum fares,
and surge multipliers at runtime directly from backend without requiring a new client APK.
"""
import uuid
import pytest
from datetime import datetime, timezone
from httpx import AsyncClient, ASGITransport
from sqlalchemy import select

from backend.main import app
from backend.api.deps import get_current_admin, get_current_user, get_current_customer
from backend.domain.models import (
    User, VehicleCategory, FareRule, AuditLog, CustomerProfile
)

@pytest.mark.asyncio
async def test_admin_runtime_fare_update_reflects_immediately_in_estimates(db_session):
    """
    Verifies that when Admin modifies fare rates for a category via PUT /admin/fares/category/{id},
    the new rates are saved to the database and immediately reflected in customer fare estimates
    (POST /fares/estimate and POST /fares/compare) without any client-side app rebuild.
    """
    suffix = uuid.uuid4().hex[:6]

    # 1. Create Vehicle Category
    cat = VehicleCategory(
        id=f"cat_dynamic_{suffix}",
        code=f"E_AUTO_{suffix.upper()}",
        name="Dynamic Electric Auto",
        sort_order=1,
        is_active=True
    )
    db_session.add(cat)
    await db_session.flush()

    # 2. Initial Fare Rule: Base = 3000 cents (₹30), per_km = 1000 cents (₹10/km)
    initial_rule = FareRule(
        id=f"rule_init_{suffix}",
        category_id=cat.id,
        version=1,
        base_fare_cents=3000,
        per_km_cents=1000,
        per_minute_cents=100,
        minimum_fare_cents=3000,
        surge_multiplier=1.0,
        is_active=True,
        workflow_status="ACTIVE",
        effective_from=datetime.now(timezone.utc)
    )
    db_session.add(initial_rule)
    await db_session.commit()

    # 3. Create Admin & Customer Users
    admin_user = User(
        id=str(uuid.uuid4()),
        phone_number=f"+91981{suffix}01",
        full_name="Super Administrator",
        role="admin",
        is_active=True,
        is_verified=True
    )
    customer_user = User(
        id=str(uuid.uuid4()),
        phone_number=f"+91981{suffix}02",
        full_name="Mobile Rider",
        role="customer",
        is_active=True,
        is_verified=True
    )
    db_session.add_all([admin_user, customer_user])
    await db_session.flush()
    db_session.add(CustomerProfile(user_id=customer_user.id))
    await db_session.commit()

    app.dependency_overrides[get_current_admin] = lambda: admin_user
    app.dependency_overrides[get_current_user] = lambda: admin_user
    app.dependency_overrides[get_current_customer] = lambda: customer_user

    try:
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            # Step A: Customer requests fare estimate BEFORE admin update
            # ~2.0 km journey
            est_before = await client.post(
                "/api/v1/fares/estimate",
                json={
                    "category_id": cat.id,
                    "pickup_latitude": 28.6140,
                    "pickup_longitude": 77.2090,
                    "destination_latitude": 28.6300,
                    "destination_longitude": 77.2200
                }
            )
            assert est_before.status_code == 200, est_before.text
            data_before = est_before.json()
            assert data_before["base_fare_rupees"] == 30.0
            fare_before = data_before["estimated_fare_rupees"]

            # Step B: Admin updates tariff rates at runtime via PUT /api/v1/admin/fares/category/{id}
            # New Base = ₹50, Per Km = ₹15, Min Fare = ₹50, Surge = 1.2
            update_resp = await client.put(
                f"/api/v1/admin/fares/category/{cat.id}",
                json={
                    "base_fare_rupees": 50.0,
                    "per_km_rupees": 15.0,
                    "per_minute_rupees": 2.0,
                    "minimum_fare_rupees": 50.0,
                    "surge_multiplier": 1.2,
                    "reason": "Fuel tariff inflation revision"
                }
            )
            assert update_resp.status_code == 200, update_resp.text
            update_data = update_resp.json()
            assert update_data["base_fare_rupees"] == 50.0
            assert update_data["per_km_rupees"] == 15.0
            assert update_data["surge_multiplier"] == 1.2
            assert update_data["version"] == 2

            # Step C: Customer requests fare estimate AFTER admin update
            # Exact same coordinates - WITHOUT rebuilding or reinstalling APK
            est_after = await client.post(
                "/api/v1/fares/estimate",
                json={
                    "category_id": cat.id,
                    "pickup_latitude": 28.6140,
                    "pickup_longitude": 77.2090,
                    "destination_latitude": 28.6300,
                    "destination_longitude": 77.2200
                }
            )
            assert est_after.status_code == 200, est_after.text
            data_after = est_after.json()
            assert data_after["base_fare_rupees"] == 50.0
            assert data_after["surge_multiplier"] == 1.2
            assert data_after["estimated_fare_rupees"] > fare_before

            # Step D: Fare Compare endpoint also immediately reflects new rates
            compare_resp = await client.post(
                "/api/v1/fares/compare",
                json={
                    "pickup_latitude": 28.6140,
                    "pickup_longitude": 77.2090,
                    "destination_latitude": 28.6300,
                    "destination_longitude": 77.2200
                }
            )
            assert compare_resp.status_code == 200
            comp_list = compare_resp.json()
            cat_comp = next((c for c in comp_list if c["category_id"] == cat.id), None)
            assert cat_comp is not None
            assert cat_comp["estimate"]["base_fare_rupees"] == 50.0

            # Step E: Verify audit log recorded in database
            stmt_audit = select(AuditLog).where(
                AuditLog.action == "FARE_RULE_RUNTIME_UPDATED",
                AuditLog.actor_id == admin_user.id
            )
            audit_record = (await db_session.execute(stmt_audit)).scalars().first()
            assert audit_record is not None
            assert cat.id in audit_record.metadata_json
    finally:
        app.dependency_overrides.pop(get_current_admin, None)
        app.dependency_overrides.pop(get_current_user, None)
        app.dependency_overrides.pop(get_current_customer, None)


@pytest.mark.asyncio
async def test_admin_corridor_surge_update(db_session):
    """
    Verifies that admin can dynamically adjust corridor surge multiplier across
    active fare rules via POST /api/v1/admin/fares/surge.
    """
    suffix = uuid.uuid4().hex[:6]
    cat = VehicleCategory(id=f"cat_surge_{suffix}", code=f"SURGE_{suffix.upper()}", name="Surge Category", is_active=True)
    rule = FareRule(
        id=f"rule_s_{suffix}", category_id=cat.id, version=1,
        base_fare_cents=2000, per_km_cents=1000, per_minute_cents=100,
        minimum_fare_cents=2000, surge_multiplier=1.0, is_active=True,
        workflow_status="ACTIVE", effective_from=datetime.now(timezone.utc)
    )
    admin_user = User(id=str(uuid.uuid4()), phone_number=f"+91981{suffix}99", full_name="Admin Surge", role="admin", is_active=True, is_verified=True)
    db_session.add_all([cat, rule, admin_user])
    await db_session.commit()

    app.dependency_overrides[get_current_admin] = lambda: admin_user
    app.dependency_overrides[get_current_user] = lambda: admin_user

    try:
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            resp = await client.post(
                "/api/v1/admin/fares/surge",
                json={
                    "surge_multiplier": 1.8,
                    "category_id": cat.id,
                    "reason": "Rainstorm dynamic surge"
                }
            )
            assert resp.status_code == 200, resp.text
            data = resp.json()
            assert data["surge_multiplier"] == 1.8
            assert data["status"] == "APPLIED_LIVE"

            # Check database persistence
            await db_session.refresh(rule)
            assert rule.surge_multiplier == 1.8
    finally:
        app.dependency_overrides.pop(get_current_admin, None)
        app.dependency_overrides.pop(get_current_user, None)


@pytest.mark.asyncio
async def test_runtime_fare_update_rejects_invalid_values(db_session):
    """
    Verifies that invalid values (e.g. negative base fare, surge multiplier < 1.0 or > 5.0)
    are strictly rejected with HTTP 422 Unprocessable Entity.
    """
    suffix = uuid.uuid4().hex[:6]
    admin_user = User(id=str(uuid.uuid4()), phone_number=f"+91981{suffix}55", full_name="Admin Validation", role="admin", is_active=True, is_verified=True)
    db_session.add(admin_user)
    await db_session.commit()

    app.dependency_overrides[get_current_admin] = lambda: admin_user

    try:
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            # 1. Negative base fare
            resp_neg = await client.put(
                "/api/v1/admin/fares/category/any-category",
                json={"base_fare_rupees": -10.0}
            )
            assert resp_neg.status_code == 422

            # 2. Surge multiplier below 1.0
            resp_surge_low = await client.put(
                "/api/v1/admin/fares/category/any-category",
                json={"surge_multiplier": 0.5}
            )
            assert resp_surge_low.status_code == 422

            # 3. Surge multiplier above 5.0
            resp_surge_high = await client.put(
                "/api/v1/admin/fares/category/any-category",
                json={"surge_multiplier": 7.0}
            )
            assert resp_surge_high.status_code == 422
    finally:
        app.dependency_overrides.pop(get_current_admin, None)
