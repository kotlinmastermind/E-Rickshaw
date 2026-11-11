"""
Automated Regression and Reproduction Test Suite for HAR and Production Defect Remediation.
Covers:
- Issue A: Fleet Map database live locations vs demonstration fallback and freshness tagging.
- Issue B: Ephemeral WebSocket ticket generation, role authorization, and live location broadcasting.
- Issue C: Ride completion fare finalization and revenue reconciliation.
- Issue D: Dashboard user and driver count consistency across roles and active statuses.
- Issue E: Driver approval status canonicalization and gating unapproved drivers.
"""
import uuid
from datetime import datetime, timezone, timedelta
import pytest
from httpx import AsyncClient, ASGITransport
from sqlalchemy import select

from backend.main import app
from backend.domain.models import (
    User, DriverProfile, DriverAvailability, DriverLocation, Ride,
    CustomerProfile, VehicleCategory, FareRule, Payment
)
from backend.domain.state_machine.ride_state_machine import RideStatus
from backend.infrastructure.security.crypto import create_jwt_token
from backend.application.services.admin_service import AdminService
from backend.application.services.admin_governance_service import AdminGovernanceService
from backend.application.services.driver_service import DriverService
from fastapi import HTTPException


@pytest.mark.asyncio
async def test_fleet_map_returns_real_database_records_when_present(db_session):
    """Verify that real driver locations in DB are returned without falling back to synthetic pilots."""
    uid = uuid.uuid4().hex[:8]
    user = User(
        phone_number=f"+9198{uid}",
        email=f"real_driver_{uid}@erickshaw.in",
        full_name="Chander Prakash",
        role="driver",
        is_active=True
    )
    db_session.add(user)
    await db_session.flush()

    prof = DriverProfile(
        user_id=user.id,
        license_number=f"DL-REAL-{uid}",
        security_badge_id="BADGE-8841",
        approval_status="APPROVED"
    )
    db_session.add(prof)
    await db_session.flush()

    now = datetime.now(timezone.utc)
    avail = DriverAvailability(
        driver_id=prof.id,
        is_online=True,
        is_busy=False,
        last_heartbeat=now
    )
    db_session.add(avail)

    loc = DriverLocation(
        driver_id=prof.id,
        latitude=28.6250,
        longitude=77.2180,
        heading=120.0,
        speed=15.5,
        accuracy=5.0,
        recorded_at=now
    )
    db_session.add(loc)
    await db_session.commit()

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/location/admin/fleet-map")
        assert resp.status_code == 200
        data = resp.json()
        assert data["is_live"] is True
        assert data["source"] == "database_live"
        assert data["count"] >= 1

        pilot_ids = [p["id"] for p in data["fleet"]]
        assert prof.id in pilot_ids

        driver_record = next(p for p in data["fleet"] if p["id"] == prof.id)
        assert driver_record["driver"] == "Chander Prakash"
        assert driver_record["plate"] == f"DL-REAL-{uid}"
        assert driver_record["lat"] == 28.6250
        assert driver_record["lng"] == 77.2180
        assert driver_record["is_synthetic"] is False
        assert driver_record["is_online"] is True
        assert driver_record["is_stale"] is False


@pytest.mark.asyncio
async def test_fleet_map_marks_stale_locations_correctly(db_session):
    """Verify that driver locations older than 15 minutes are tagged as stale."""
    uid = uuid.uuid4().hex[:8]
    user = User(
        phone_number=f"+9197{uid}",
        email=f"stale_driver_{uid}@erickshaw.in",
        full_name="Ramesh Kumar",
        role="driver",
        is_active=True
    )
    db_session.add(user)
    await db_session.flush()

    prof = DriverProfile(
        user_id=user.id,
        license_number=f"DL-STALE-{uid}",
        approval_status="APPROVED"
    )
    db_session.add(prof)
    await db_session.flush()

    old_time = datetime.now(timezone.utc) - timedelta(minutes=25)
    avail = DriverAvailability(
        driver_id=prof.id,
        is_online=True,
        is_busy=False,
        last_heartbeat=old_time
    )
    db_session.add(avail)

    loc = DriverLocation(
        driver_id=prof.id,
        latitude=28.6300,
        longitude=77.2200,
        heading=45.0,
        speed=0.0,
        recorded_at=old_time
    )
    db_session.add(loc)
    await db_session.commit()

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.get("/api/v1/location/admin/fleet-map")
        assert resp.status_code == 200
        data = resp.json()
        driver_record = next(p for p in data["fleet"] if p["id"] == prof.id)
        assert driver_record["is_stale"] is True
        assert driver_record["freshness_seconds"] >= 1500


@pytest.mark.asyncio
async def test_dashboard_reconciles_completed_ride_revenue(db_session):
    """Verify completed rides reconcile gross booking value even if final_fare was null."""
    uid = uuid.uuid4().hex[:8]
    cust_user = User(phone_number=f"+9193{uid}", email=f"cust_{uid}@erickshaw.in", role="customer")
    db_session.add(cust_user)
    await db_session.flush()

    cust_prof = CustomerProfile(user_id=cust_user.id)
    db_session.add(cust_prof)
    await db_session.flush()

    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    rule = (await db_session.execute(select(FareRule))).scalars().first()

    # Create completed ride with estimated_fare_cents=6000 and final_fare_cents=None
    ride = Ride(
        customer_id=cust_prof.id,
        vehicle_category_id=cat.id if cat else "cat_test",
        fare_rule_id=rule.id if rule else "rule_test",
        status="COMPLETED",
        pickup_latitude=28.6139,
        pickup_longitude=77.2090,
        pickup_address="Connaught Place",
        destination_latitude=28.6250,
        destination_longitude=77.2180,
        destination_address="New Delhi Railway Station",
        start_otp="123456",
        estimated_distance_km=5.0,
        estimated_duration_minutes=15.0,
        estimated_fare_cents=6000,
        final_fare_cents=None,  # Null final fare as in HAR
        requested_at=datetime.now(timezone.utc)
    )
    db_session.add(ride)
    await db_session.commit()

    service = AdminService(db_session)
    metrics = await service.get_dashboard_metrics()
    assert metrics["completed_rides"] >= 1
    # Gross booking value must coalesce and not return 0
    assert metrics["gross_booking_cents"] >= 6000
    assert metrics["gross_booking_inr"] >= 60.0


@pytest.mark.asyncio
async def test_driver_approval_normalization(db_session):
    """Verify approval statuses (e.g. 'yes', 'approved', 'REJECTED') normalize to canonical enums."""
    uid = uuid.uuid4().hex[:8]
    user1 = User(phone_number=f"+9192{uid}", email=f"drv1_{uid}@erickshaw.in", full_name="Driver One", role="driver")
    user2 = User(phone_number=f"+9191{uid}", email=f"drv2_{uid}@erickshaw.in", full_name="Driver Two", role="driver")
    db_session.add_all([user1, user2])
    await db_session.flush()

    # Legacy values stored in DB
    prof1 = DriverProfile(user_id=user1.id, license_number=f"DL-LEG1-{uid}", approval_status="yes")
    prof2 = DriverProfile(user_id=user2.id, license_number=f"DL-LEG2-{uid}", approval_status="approved")
    db_session.add_all([prof1, prof2])
    await db_session.commit()

    gov_service = AdminGovernanceService(db_session)
    drivers = await gov_service.list_drivers()
    d1 = next(d for d in drivers if d["id"] == prof1.id)
    d2 = next(d for d in drivers if d["id"] == prof2.id)

    assert d1["approval_status"] == "APPROVED"
    assert d2["approval_status"] == "APPROVED"


@pytest.mark.asyncio
async def test_unapproved_driver_cannot_go_online(db_session):
    """Verify that an unapproved driver cannot bypass approval by toggling online."""
    uid = uuid.uuid4().hex[:8]
    user = User(phone_number=f"+9190{uid}", email=f"pend_{uid}@erickshaw.in", role="driver")
    db_session.add(user)
    await db_session.flush()

    prof = DriverProfile(user_id=user.id, license_number=f"DL-PEND-{uid}", approval_status="PENDING")
    db_session.add(prof)
    await db_session.commit()

    driver_service = DriverService(db_session)
    with pytest.raises(HTTPException) as exc_info:
        await driver_service.toggle_availability(user.id, is_online=True)
    assert exc_info.value.status_code == 403


@pytest.mark.asyncio
async def test_dashboard_user_counts_reconciliation(db_session):
    """Verify that dashboard metrics reconcile total users across customers, drivers, and admins."""
    uid = uuid.uuid4().hex[:6]
    # Add 2 customers, 1 driver, 1 admin
    c1 = User(phone_number=f"+9188{uid}1", role="customer", is_active=True)
    c2 = User(phone_number=f"+9188{uid}2", role="customer", is_active=True)
    d1 = User(phone_number=f"+9188{uid}3", role="driver", is_active=True)
    a1 = User(phone_number=f"+9188{uid}4", role="admin", is_active=True)
    db_session.add_all([c1, c2, d1, a1])
    await db_session.commit()

    service = AdminService(db_session)
    metrics = await service.get_dashboard_metrics()
    assert metrics["total_users"] >= 4
    assert metrics["total_customers"] >= 2
    assert metrics["total_drivers"] >= 1
    assert metrics["total_admins"] >= 1
    assert metrics["total_users"] == (metrics["total_customers"] + metrics["total_drivers"] + metrics["total_admins"])

    # Verify that list_users filtered by role='customer' exactly matches total_customers
    gov_service = AdminGovernanceService(db_session)
    cust_res = await gov_service.list_users(role="customer", limit=100)
    assert cust_res["total"] == metrics["total_customers"]


@pytest.mark.asyncio
async def test_websocket_ticket_lifecycle_and_single_use(db_session):
    """Verify ephemeral WebSocket ticket creation and single-use redemption."""
    from backend.domain.models import Session
    uid = uuid.uuid4().hex[:8]
    admin_user = User(phone_number=f"+9187{uid}", role="admin", is_active=True)
    db_session.add(admin_user)
    await db_session.flush()

    sess = Session(
        user_id=admin_user.id,
        token_hash=f"hash_{uuid.uuid4().hex}",
        expires_at=datetime.now(timezone.utc) + timedelta(days=1),
        is_revoked=False
    )
    db_session.add(sess)
    await db_session.commit()

    admin_token = create_jwt_token(user_id=admin_user.id, role="admin", session_id=sess.id)

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        resp = await client.post(
            "/api/v1/ws/ticket",
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert resp.status_code == 200
        ticket_data = resp.json()
        assert "ticket" in ticket_data
        assert ticket_data["expires_in_seconds"] == 30

        from backend.api.routes.ws import validate_ws_credentials
        # 1. First redemption succeeds
        cred = await validate_ws_credentials(f"TICKET:{ticket_data['ticket']}")
        assert cred is not None
        assert cred["user_id"] == admin_user.id
        assert cred["role"].lower() == "admin"

        # 2. Second redemption fails (single-use)
        cred2 = await validate_ws_credentials(f"TICKET:{ticket_data['ticket']}")
        assert cred2 is None

