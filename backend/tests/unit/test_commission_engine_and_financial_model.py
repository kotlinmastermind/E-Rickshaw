"""Tests for the centralized versioned Commission Engine and locked financial business model."""
from __future__ import annotations

import uuid
from datetime import datetime, timedelta, timezone

import pytest
from fastapi import HTTPException
from sqlalchemy import select

from backend.application.services.commission_service import CommissionService
from backend.application.services.payment_service import PaymentService
from backend.domain.models.extended_models import AdministrativeAuditLog, CommissionRule
from backend.domain.models.models import (
    CustomerProfile,
    DriverLedgerEntry,
    DriverProfile,
    DriverWallet,
    FareRule,
    Payment,
    Ride,
    User,
    VehicleCategory,
)


@pytest.mark.asyncio
async def test_commission_engine_versioning_validation_and_audit(db_session):
    admin = User(id=str(uuid.uuid4()), phone_number="+919999900001", full_name="Admin", role="ADMIN")
    db_session.add(admin)
    await db_session.flush()

    # Invalid percentages rejected
    with pytest.raises(HTTPException) as exc_neg:
        await CommissionService.create_and_activate_rule(
            db_session, category_id=None, percentage=-5.0, admin_user_id=admin.id, reason="invalid"
        )
    assert exc_neg.value.status_code == 422

    with pytest.raises(HTTPException) as exc_over:
        await CommissionService.create_and_activate_rule(
            db_session, category_id=None, percentage=105.0, admin_user_id=admin.id, reason="invalid"
        )
    assert exc_over.value.status_code == 422

    t0 = datetime.now(timezone.utc) - timedelta(hours=2)
    t1 = datetime.now(timezone.utc) - timedelta(hours=1)

    r1 = await CommissionService.create_and_activate_rule(
        db_session, category_id=None, percentage=15.0, admin_user_id=admin.id, reason="Initial 15%", effective_from=t0
    )
    assert r1.version == 1
    assert r1.is_active is True

    r2 = await CommissionService.create_and_activate_rule(
        db_session, category_id=None, percentage=20.0, admin_user_id=admin.id, reason="Increase to 20%", effective_from=t1
    )
    await db_session.refresh(r1)
    assert r1.is_active is False
    assert r1.effective_until.replace(tzinfo=timezone.utc) == t1
    assert r2.version == 2
    assert r2.is_active is True

    # Historical ride at t0 + 10m retains 15%
    snap_old = await CommissionService.calculate_for_ride(
        db_session, gross_cents=10000, effective_at=t0 + timedelta(minutes=10)
    )
    assert snap_old.percentage == 15.0
    assert snap_old.commission_cents == 1500
    assert snap_old.net_cents == 8500
    assert snap_old.version == 1

    # New ride after t1 uses 20%
    snap_new = await CommissionService.calculate_for_ride(
        db_session, gross_cents=10000, effective_at=t1 + timedelta(minutes=10)
    )
    assert snap_new.percentage == 20.0
    assert snap_new.commission_cents == 2000
    assert snap_new.net_cents == 8000
    assert snap_new.version == 2

    audits = (
        await db_session.execute(
            select(AdministrativeAuditLog).where(AdministrativeAuditLog.action == "COMMISSION_RULE_ACTIVATED")
        )
    ).scalars().all()
    assert len(audits) == 2


@pytest.mark.asyncio
async def test_online_payment_100_rupees_at_15_percent_model(db_session):
    rider = User(id=str(uuid.uuid4()), phone_number="+919999900011", full_name="Rider", role="COMMUTER")
    driver_u = User(id=str(uuid.uuid4()), phone_number="+919999900012", full_name="Driver", role="DRIVER")
    db_session.add_all([rider, driver_u])
    await db_session.flush()

    driver_p = DriverProfile(id=str(uuid.uuid4()), user_id=driver_u.id, license_number="DL-100")
    db_session.add(driver_p)
    await db_session.flush()

    await CommissionService.create_and_activate_rule(
        db_session, category_id=None, percentage=15.0, admin_user_id=None, reason="15% rule"
    )

    cust_p = CustomerProfile(id=str(uuid.uuid4()), user_id=rider.id)
    cat = VehicleCategory(id=str(uuid.uuid4()), name="E-Rickshaw", code=f"ERICK_{uuid.uuid4().hex[:6]}")
    fare_rule = FareRule(id=str(uuid.uuid4()), category_id=cat.id, base_fare_cents=2000, per_km_cents=1000, per_minute_cents=200, minimum_fare_cents=2000)
    db_session.add_all([cust_p, cat, fare_rule])
    await db_session.flush()

    ride = Ride(
        id=str(uuid.uuid4()),
        customer_id=cust_p.id,
        assigned_driver_id=driver_p.id,
        vehicle_category_id=cat.id,
        fare_rule_id=fare_rule.id,
        status="IN_PROGRESS",
        payment_method="RAZORPAY",
        start_otp="1234",
        estimated_distance_km=5.0,
        estimated_duration_minutes=15,
        estimated_fare_cents=10000,
        final_fare_cents=10000,
        pickup_address="Point A",
        pickup_latitude=28.6,
        pickup_longitude=77.2,
        destination_address="Point B",
        destination_latitude=28.62,
        destination_longitude=77.22,
    )
    db_session.add(ride)
    await db_session.flush()

    payment = Payment(
        id=str(uuid.uuid4()),
        ride_id=ride.id,
        customer_id=cust_p.id,
        driver_id=driver_p.id,
        amount_cents=10000,
        currency="INR",
        purpose="RIDE_FARE",
        status="CREATED",
        provider_name="RAZORPAY",
        provider_order_id="order_test_100",
        idempotency_key=f"idem_{uuid.uuid4()}",
    )
    db_session.add(payment)
    await db_session.commit()

    svc = PaymentService(db_session)
    await svc.finalize_payment_capture(payment=payment, provider_payment_id="pay_test_100")

    wallet = (
        await db_session.execute(select(DriverWallet).where(DriverWallet.driver_id == driver_p.id))
    ).scalar_one()
    assert wallet.balance_cents == 10000

    entries = (
        await db_session.execute(select(DriverLedgerEntry).where(DriverLedgerEntry.driver_id == driver_p.id))
    ).scalars().all()
    by_cat = {e.category: e for e in entries}
    assert by_cat["TRIP_EARNING"].amount_cents == 10000
    assert by_cat["COMMISSION_PAYABLE"].amount_cents == -1500


@pytest.mark.asyncio
async def test_cash_payment_100_rupees_at_15_percent_model(db_session):
    rider = User(id=str(uuid.uuid4()), phone_number="+919999900021", full_name="Rider2", role="COMMUTER")
    driver_u = User(id=str(uuid.uuid4()), phone_number="+919999900022", full_name="Driver2", role="DRIVER")
    db_session.add_all([rider, driver_u])
    await db_session.flush()

    driver_p = DriverProfile(id=str(uuid.uuid4()), user_id=driver_u.id, license_number="DL-200")
    db_session.add(driver_p)
    await db_session.flush()

    await CommissionService.create_and_activate_rule(
        db_session, category_id=None, percentage=15.0, admin_user_id=None, reason="15% rule"
    )

    cust_p = CustomerProfile(id=str(uuid.uuid4()), user_id=rider.id)
    cat = VehicleCategory(id=str(uuid.uuid4()), name="E-Rickshaw", code=f"ERICK_{uuid.uuid4().hex[:6]}")
    fare_rule = FareRule(id=str(uuid.uuid4()), category_id=cat.id, base_fare_cents=2000, per_km_cents=1000, per_minute_cents=200, minimum_fare_cents=2000)
    db_session.add_all([cust_p, cat, fare_rule])
    await db_session.flush()

    ride = Ride(
        id=str(uuid.uuid4()),
        customer_id=cust_p.id,
        assigned_driver_id=driver_p.id,
        vehicle_category_id=cat.id,
        fare_rule_id=fare_rule.id,
        status="IN_PROGRESS",
        payment_method="CASH",
        start_otp="1234",
        estimated_distance_km=5.0,
        estimated_duration_minutes=15,
        estimated_fare_cents=10000,
        final_fare_cents=10000,
        pickup_address="Point A",
        pickup_latitude=28.6,
        pickup_longitude=77.2,
        destination_address="Point B",
        destination_latitude=28.62,
        destination_longitude=77.22,
    )
    db_session.add(ride)
    await db_session.commit()

    svc = PaymentService(db_session)
    res = await svc.confirm_cash_collection(driver_user_id=driver_u.id, ride_id=ride.id)

    assert res["cash_collected_cents"] == 10000
    assert res["driver_gross_earnings_cents"] == 10000
    assert res["commission_receivable_cents"] == 1500
    assert res["commission_deducted_cents"] == 0
    assert res["driver_wallet_balance_cents"] == 0

    entries = (
        await db_session.execute(select(DriverLedgerEntry).where(DriverLedgerEntry.driver_id == driver_p.id))
    ).scalars().all()
    by_cat = {e.category: e for e in entries}
    assert by_cat["CASH_TRIP_EARNING"].amount_cents == 10000
    assert by_cat["COMMISSION_RECEIVABLE"].amount_cents == -1500
