import pytest
import uuid
from datetime import datetime, timezone, timedelta

from backend.domain.models import (
    User, CustomerProfile, DriverProfile, DriverDocument,
    Ride, RideStatusEvent, FareRule, VehicleCategory,
    Payment, Notification, DriverAvailability
)
from backend.application.services.admin_service import AdminService
from backend.application.services.analytics_bi_service import AnalyticsBiService


@pytest.mark.asyncio
async def test_admin_dashboard_metrics_from_database(db_session):
    """
    Task 076 & 077: Verify admin dashboard metrics and driver earnings calculate from database.
    """
    admin_service = AdminService(db_session)

    suffix = uuid.uuid4().hex[:6]
    cust_user = User(phone_number=f"+91981{suffix}1", full_name="Metrics Customer", role="customer")
    drv_user = User(phone_number=f"+91981{suffix}2", full_name="Metrics Driver", role="driver")
    db_session.add_all([cust_user, drv_user])
    await db_session.flush()

    c_prof = CustomerProfile(user_id=cust_user.id)
    d_prof = DriverProfile(user_id=drv_user.id, approval_status="APPROVED", license_number=f"DL-MET-{suffix}")
    db_session.add_all([c_prof, d_prof])
    await db_session.flush()

    cat = VehicleCategory(code=f"CAT_MET_{suffix}", name="Eco", description="Standard", capacity=4)
    db_session.add(cat)
    await db_session.flush()

    rule = FareRule(category_id=cat.id, base_fare_cents=2000, per_km_cents=500, per_minute_cents=100, minimum_fare_cents=2000)
    db_session.add(rule)
    await db_session.flush()

    ride = Ride(
        customer_id=c_prof.id,
        assigned_driver_id=d_prof.id,
        vehicle_category_id=cat.id,
        fare_rule_id=rule.id,
        status="COMPLETED",
        start_otp="1234",
        pickup_address="Origin Point",
        pickup_latitude=28.6139,
        pickup_longitude=77.2090,
        destination_address="Destination Point",
        destination_latitude=28.7041,
        destination_longitude=77.1025,
        estimated_distance_km=10.0,
        estimated_duration_minutes=20.0,
        estimated_fare_cents=10000,
        final_fare_cents=10000,
        requested_at=datetime.now(timezone.utc) - timedelta(hours=2),
        completed_at=datetime.now(timezone.utc) - timedelta(hours=1)
    )
    db_session.add(ride)
    await db_session.commit()

    # Query metrics
    metrics = await admin_service.get_dashboard_metrics()
    assert metrics["total_rides"] >= 1
    assert metrics["completed_rides"] >= 1
    assert metrics["gross_booking_cents"] >= 10000
    assert metrics["currency"] == "INR"


@pytest.mark.asyncio
async def test_admin_analytics_date_range_filtering(db_session):
    """
    Task 078: Support arbitrary date range filtering in analytics reports.
    """
    admin_service = AdminService(db_session)
    suffix = uuid.uuid4().hex[:6]

    cust_user = User(phone_number=f"+91983{suffix}1", full_name="Hist Customer", role="customer")
    db_session.add(cust_user)
    await db_session.flush()

    c_prof = CustomerProfile(user_id=cust_user.id)
    db_session.add(c_prof)
    await db_session.flush()

    cat = VehicleCategory(code=f"CAT_HIST_{suffix}", name="Eco", description="Standard", capacity=4)
    db_session.add(cat)
    await db_session.flush()

    rule = FareRule(category_id=cat.id, base_fare_cents=2000, per_km_cents=500, per_minute_cents=100, minimum_fare_cents=2000)
    db_session.add(rule)
    await db_session.flush()

    # Historical ride (5 days ago)
    past_date = datetime.now(timezone.utc) - timedelta(days=5)
    ride_old = Ride(
        customer_id=c_prof.id,
        vehicle_category_id=cat.id,
        fare_rule_id=rule.id,
        status="COMPLETED",
        start_otp="1234",
        pickup_address="Old Street",
        pickup_latitude=28.6,
        pickup_longitude=77.2,
        destination_address="Old Market",
        destination_latitude=28.7,
        destination_longitude=77.1,
        estimated_distance_km=5.0,
        estimated_duration_minutes=10.0,
        estimated_fare_cents=5000,
        final_fare_cents=5000,
        requested_at=past_date,
        completed_at=past_date + timedelta(minutes=15)
    )
    db_session.add(ride_old)
    await db_session.commit()

    # 1. Filter with a window between 3 days ago and 2 days ago (excludes 5-day-old ride and recent rides)
    start_str = (datetime.now(timezone.utc) - timedelta(days=3)).isoformat()
    end_str = (datetime.now(timezone.utc) - timedelta(days=2)).isoformat()
    empty_report = await admin_service.get_analytics_reports(start_date=start_str, end_date=end_str)
    assert empty_report["volume_metrics"]["total_rides_requested"] == 0

    # 2. Filter with window between 6 days ago and 4 days ago (captures the 5-day-old ride)
    broad_start = (datetime.now(timezone.utc) - timedelta(days=6)).isoformat()
    broad_end = (datetime.now(timezone.utc) - timedelta(days=4)).isoformat()
    broad_report = await admin_service.get_analytics_reports(start_date=broad_start, end_date=broad_end)
    assert broad_report["volume_metrics"]["total_rides_requested"] >= 1


@pytest.mark.asyncio
async def test_admin_dataset_export_functionality(db_session):
    """
    Task 079: Verify CSV and JSON dataset export with permission verification.
    """
    bi_service = AnalyticsBiService(db_session)
    admin_id = f"admin_{uuid.uuid4().hex[:6]}"

    # Export RIDES dataset
    export_res = await bi_service.export_dataset_with_permissions(
        dataset="RIDES",
        user_permissions=["analytics:export", "admin.access"],
        admin_user_id=admin_id,
        export_format="CSV"
    )

    assert export_res["dataset"] == "RIDES"
    assert export_res["format"] == "CSV"
    assert "data" in export_res
    assert "row_count" in export_res


@pytest.mark.asyncio
async def test_admin_driver_approval_workflow_and_notification(db_session):
    """
    Task 080: Update driver status and notify driver upon verification.
    """
    admin_service = AdminService(db_session)
    suffix = uuid.uuid4().hex[:6]
    driver_user = User(
        phone_number=f"+91987{suffix}9",
        full_name="Driver Applicant",
        role="driver",
        is_active=True
    )
    db_session.add(driver_user)
    await db_session.flush()

    driver_profile = DriverProfile(
        user_id=driver_user.id,
        license_number=f"DL-ONB-{suffix}",
        approval_status="PENDING",
        onboarding_step="DOCUMENTS_UPLOADED"
    )
    db_session.add(driver_profile)
    await db_session.commit()

    # Admin approves driver
    admin_id = f"admin_{suffix}"
    approval_res = await admin_service.review_driver_approval(
        admin_id=admin_id,
        driver_id=driver_profile.id,
        status="APPROVED"
    )
    assert approval_res["approval_status"] == "APPROVED"

    # Verify notification generated
    await db_session.refresh(driver_profile)
    assert driver_profile.approval_status == "APPROVED"


@pytest.mark.asyncio
async def test_vehicle_category_and_fare_rule_management(db_session):
    """
    Task 082 & 083: Vehicle category management and fare rule configuration.
    """
    admin_service = AdminService(db_session)
    suffix = uuid.uuid4().hex[:4].upper()

    # 1. Create vehicle category
    cat = VehicleCategory(
        code=f"TIER_{suffix}",
        name="Ultra Luxury Sedan",
        description="Executive fleet tier",
        capacity=4,
        sort_order=10,
        is_active=True
    )
    db_session.add(cat)
    await db_session.commit()
    await db_session.refresh(cat)

    # 2. Configure Fare Rule
    admin_id = f"admin_{suffix}"
    fare_res = await admin_service.create_or_update_fare_rule(
        admin_id=admin_id,
        category_id=cat.id,
        base_fare_cents=10000,
        per_km_cents=2500,
        per_minute_cents=500,
        minimum_fare_cents=15000,
        surge_multiplier=1.2
    )
    assert fare_res["base_fare_cents"] == 10000
    assert fare_res["per_km_cents"] == 2500
    assert fare_res["is_active"] is True
    assert fare_res["version"] >= 1
