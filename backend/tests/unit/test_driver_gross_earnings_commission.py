"""Driver earnings must report finalized gross fares and commission snapshots separately."""
import uuid
from datetime import datetime, timedelta, timezone

import pytest

from backend.application.services.commission_service import CommissionService
from backend.application.services.driver_service import DriverService
from backend.application.services.driver_financial_service import DriverFinancialService
from backend.domain.models import (
    CustomerProfile, DriverProfile, DriverWallet, FareRule, Ride, User, VehicleCategory,
)


@pytest.mark.asyncio
async def test_driver_earnings_summary_uses_gross_fares_and_per_ride_commission_snapshots(db_session):
    suffix = uuid.uuid4().hex[:10]
    driver_user = User(id=str(uuid.uuid4()), phone_number=f"+91{uuid.uuid4().int % 10**10:010d}", full_name="Gross Earnings Driver", role="DRIVER")
    rider_user = User(id=str(uuid.uuid4()), phone_number=f"+91{uuid.uuid4().int % 10**10:010d}", full_name="Earnings Rider", role="CUSTOMER")
    db_session.add_all([driver_user, rider_user])
    await db_session.flush()
    driver = DriverProfile(id=str(uuid.uuid4()), user_id=driver_user.id, license_number=f"DL-{suffix}", rating_count=0)
    customer = CustomerProfile(id=str(uuid.uuid4()), user_id=rider_user.id)
    category = VehicleCategory(id=str(uuid.uuid4()), name="Test E-Rickshaw", code=f"ER_{suffix}")
    db_session.add_all([driver, customer, category])
    await db_session.flush()
    fare_rule = FareRule(id=str(uuid.uuid4()), category_id=category.id, base_fare_cents=2000, per_km_cents=1000, per_minute_cents=100, minimum_fare_cents=2000)
    db_session.add(fare_rule)
    db_session.add(DriverWallet(driver_id=driver.id, balance_cents=10000, currency="INR"))
    await db_session.flush()

    await CommissionService.create_and_activate_rule(
        db_session, category_id=category.id, percentage=15.0, admin_user_id=None, reason="Driver earnings snapshot test"
    )
    now = datetime.now(timezone.utc)

    def make_ride(ride_id, payment_method, gross_cents, completed_at):
        return Ride(
            id=ride_id, customer_id=customer.id, assigned_driver_id=driver.id,
            vehicle_category_id=category.id, fare_rule_id=fare_rule.id,
            status="COMPLETED", payment_method=payment_method,
            start_otp="1234", estimated_distance_km=3.0, estimated_duration_minutes=12.0,
            estimated_fare_cents=gross_cents, final_fare_cents=gross_cents,
            completed_at=completed_at, pickup_address="Pickup", pickup_latitude=25.6, pickup_longitude=85.1,
            destination_address="Destination", destination_latitude=25.61, destination_longitude=85.11,
        )

    digital_ride = make_ride(f"digital-{suffix}", "RAZORPAY", 10000, now)
    cash_ride = make_ride(f"cash-{suffix}", "CASH", 5000, now - timedelta(minutes=1))
    legacy_ride = make_ride(f"legacy-{suffix}", "RAZORPAY", 2000, now - timedelta(minutes=2))
    db_session.add_all([digital_ride, cash_ride, legacy_ride])
    await db_session.flush()

    for ride, classification in ((digital_ride, "PAYABLE"), (cash_ride, "RECEIVABLE")):
        snapshot = await CommissionService.calculate_for_ride(
            db_session, gross_cents=ride.final_fare_cents, category_id=category.id,
            effective_at=ride.completed_at, calculation_basis="RIDE_COMPLETION"
        )
        await CommissionService.record_ride_snapshot(
            db_session, ride_id=ride.id, snapshot=snapshot, category_id=category.id,
            commission_classification=classification, currency="INR"
        )
    await db_session.commit()

    summary = await DriverService(db_session).get_earnings_summary(driver_user.id)

    assert summary["total_earnings_cents"] == 17000
    assert summary["today_earnings_cents"] == 17000
    assert summary["weekly_earnings_cents"] == 17000
    assert summary["commission_accrued_cents"] == 2250
    assert summary["today_commission_cents"] == 2250
    assert summary["commission_payable_cents"] == 1500
    assert summary["commission_receivable_cents"] == 750
    assert summary["commission_snapshot_missing_count"] == 1
    assert summary["wallet_balance_cents"] == 10000
    assert "today_net_cents" not in summary
    legacy_trip = next(item for item in summary["recent_trips"] if item["ride_id"] == legacy_ride.id)
    assert legacy_trip["gross_fare_cents"] == 2000
    assert legacy_trip["commission_cents"] is None
    assert legacy_trip["commission_snapshot_available"] is False

    # A later rate change must not rewrite the rate attached to an earlier ride.
    await CommissionService.create_and_activate_rule(
        db_session, category_id=category.id, percentage=20.0, admin_user_id=None, reason="Later rate change test"
    )
    estimate = await CommissionService.estimate_for_ride(db_session, digital_ride)
    assert estimate["estimated_driver_earnings_cents"] == 10000
    assert estimate["estimated_commission_cents"] == 2000
    assert estimate["estimated_commission_percentage"] == 20.0

    breakdown = await DriverFinancialService(db_session).get_ride_earnings_breakdown(driver_user.id, digital_ride.id)
    assert breakdown["driver_gross_earnings_cents"] == 10000
    assert breakdown["deductions_and_supplements"]["platform_commission_cents"] == 1500
    assert breakdown["deductions_and_supplements"]["platform_commission_percentage"] == 15.0
    assert breakdown["fare_breakdown"]["components_available"] is False

    legacy_breakdown = await DriverFinancialService(db_session).get_ride_earnings_breakdown(driver_user.id, legacy_ride.id)
    assert legacy_breakdown["deductions_and_supplements"]["platform_commission_cents"] is None
    assert legacy_breakdown["settlement_status"] == "COMMISSION_SNAPSHOT_UNAVAILABLE"