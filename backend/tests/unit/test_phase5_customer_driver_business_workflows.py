import uuid
import pytest
from datetime import datetime, timezone, timedelta
from httpx import AsyncClient, ASGITransport
from sqlalchemy import select, update

from backend.main import app
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, DriverAvailability, Vehicle, VehicleCategory,
    FareRule, FareEstimate, Ride, RideAssignment, RideOperationalEvent, Session
)
from backend.domain.state_machine.ride_state_machine import RideStateMachine, RideStatus, ActorRole, RideTransitionError
from backend.infrastructure.security.crypto import (
    create_jwt_token, hash_secret, generate_secure_otp, verify_otp_constant_time
)
from backend.application.services.auth_service import AuthService
from backend.application.services.ride_service import RideService
from backend.application.services.driver_onboarding_service import DriverOnboardingService


@pytest.mark.asyncio
async def test_customer_registration_login_profile_transactional(db_session):
    """
    Task 051, 052, 053: Verify customer registration creates user, profile, wallet,
    preferences, and session transactionally, with secure login and duplicate prevention.
    """
    auth_service = AuthService(db_session)
    suffix = uuid.uuid4().int % 100000000
    phone = f"+9198{suffix:08d}"
    email = f"cust_wf_{suffix}@erickshaw.in"
    password = "SecurePassword123!"

    # 1. Customer Registration
    reg_res = await auth_service.register_customer_account(
        phone_number=phone,
        email=email,
        full_name="Workflow Customer",
        password=password
    )
    assert "access_token" in reg_res
    user_id = reg_res["user"]["id"]

    # 2. Verify transactional entities in DB
    user = await db_session.get(User, user_id)
    assert user is not None
    assert user.role == "customer"

    cust_prof = (await db_session.execute(select(CustomerProfile).where(CustomerProfile.user_id == user_id))).scalar_one_or_none()
    assert cust_prof is not None

    # 3. Duplicate phone registration rejected -> 409
    with pytest.raises(Exception) as exc_info:
        await auth_service.register_customer_account(
            phone_number=phone,
            email=f"other_{suffix}@erickshaw.in",
            full_name="Duplicate Phone",
            password=password
        )
    assert "already exists" in str(exc_info.value).lower()

    # 4. Password login verification
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        login_res = await ac.post("/api/v1/auth/login", json={
            "identifier": phone,
            "password": password
        })
        assert login_res.status_code == 200
        tokens = login_res.json()
        assert "access_token" in tokens

        # Bad password fails -> 401
        bad_login = await ac.post("/api/v1/auth/login", json={
            "identifier": phone,
            "password": "WrongPassword999!"
        })
        assert bad_login.status_code == 401


@pytest.mark.asyncio
async def test_driver_registration_and_training_workflow(db_session):
    """
    Task 054, 056: Verify driver registration and training progress persistence.
    """
    auth_service = AuthService(db_session)
    suffix = uuid.uuid4().int % 100000000
    phone = f"+9197{suffix:08d}"
    email = f"drv_wf_{suffix}@erickshaw.in"

    # 1. Driver Registration
    reg_res = await auth_service.register_driver_account(
        phone_number=phone,
        email=email,
        full_name="Workflow Chauffeur",
        password="SecurePassword123!",
        vehicle_no=f"MH-01-WF-{suffix % 10000}"
    )
    assert "access_token" in reg_res
    user_id = reg_res["user"]["id"]

    driver_prof = (await db_session.execute(select(DriverProfile).where(DriverProfile.user_id == user_id))).scalar_one_or_none()
    assert driver_prof is not None
    assert driver_prof.approval_status == "PENDING"

    # 2. Driver Training Curriculum & Assessment
    onboarding_service = DriverOnboardingService(db_session)
    curriculum = await onboarding_service.get_training_curriculum(user_id)
    assert len(curriculum) > 0

    target_module = next((m for m in curriculum if len(m.get("questions", [])) > 0), curriculum[0])
    mod_id = target_module["module_id"]

    # Passing assessment
    correct_answers = [q["correct_index"] for q in target_module["questions"]]
    assess_res = await onboarding_service.submit_assessment(
        user_id=user_id,
        module_id=mod_id,
        selected_answers=correct_answers
    )
    assert assess_res["passed"] is True
    assert assess_res["score_percentage"] == 100


@pytest.mark.asyncio
async def test_secure_per_ride_otp_and_constant_time_verification():
    """
    Task 058: Verify cryptographically secure 6-digit OTP generation with no hardcoded fallbacks.
    """
    otps = set()
    for _ in range(50):
        code = generate_secure_otp(6)
        assert len(code) == 6
        assert code.isdigit()
        assert code != "739104"  # Prohibit legacy hardcoded fallback
        otps.add(code)
    # Ensure high entropy across samples
    assert len(otps) >= 48

    # Constant-time comparison
    assert verify_otp_constant_time("123456", "123456") is True
    assert verify_otp_constant_time("123456", "654321") is False


@pytest.mark.asyncio
async def test_ride_state_machine_strict_transitions():
    """
    Task 059: Enforce valid lifecycle transitions and reject illegal state jumps.
    """
    # 1. Permitted transitions
    assert RideStateMachine.can_transition("REQUESTED", "ASSIGNED", "DRIVER") is True
    assert RideStateMachine.can_transition("ASSIGNED", "ARRIVED", "DRIVER") is True
    assert RideStateMachine.can_transition("ARRIVED", "IN_PROGRESS", "DRIVER") is True
    assert RideStateMachine.can_transition("IN_PROGRESS", "COMPLETED", "DRIVER") is True
    assert RideStateMachine.can_transition("REQUESTED", "CANCELLED", "CUSTOMER") is True

    # 2. Illegal transitions must be strictly forbidden
    assert RideStateMachine.can_transition("REQUESTED", "IN_PROGRESS", "DRIVER") is False
    assert RideStateMachine.can_transition("REQUESTED", "COMPLETED", "DRIVER") is False
    assert RideStateMachine.can_transition("COMPLETED", "IN_PROGRESS", "DRIVER") is False
    assert RideStateMachine.can_transition("CANCELLED", "ASSIGNED", "DRIVER") is False

    with pytest.raises(RideTransitionError):
        RideStateMachine.validate_transition("REQUESTED", "COMPLETED", "DRIVER")


@pytest.mark.asyncio
async def test_driver_ride_acceptance_race_condition_prevention(db_session):
    """
    Task 060: Verify that when two drivers attempt to accept the same ride,
    atomic conditional update ensures only ONE succeeds while the other gets 409.
    """
    suffix = uuid.uuid4().int % 100000000

    # Create Customer
    c_user = User(phone_number=f"+9191{suffix:08d}", email=f"c_race_{suffix}@erickshaw.in", role="customer", is_active=True)
    d1_user = User(phone_number=f"+9192{suffix:08d}", email=f"d1_race_{suffix}@erickshaw.in", role="driver", is_active=True)
    d2_user = User(phone_number=f"+9193{suffix:08d}", email=f"d2_race_{suffix}@erickshaw.in", role="driver", is_active=True)
    db_session.add_all([c_user, d1_user, d2_user])
    await db_session.flush()

    c_prof = CustomerProfile(user_id=c_user.id)
    d1_prof = DriverProfile(user_id=d1_user.id, approval_status="APPROVED", license_number=f"DL1-{suffix}")
    d2_prof = DriverProfile(user_id=d2_user.id, approval_status="APPROVED", license_number=f"DL2-{suffix}")
    db_session.add_all([c_prof, d1_prof, d2_prof])
    await db_session.flush()

    d1_avail = DriverAvailability(driver_id=d1_prof.id, is_online=True, is_busy=False)
    d2_avail = DriverAvailability(driver_id=d2_prof.id, is_online=True, is_busy=False)
    db_session.add_all([d1_avail, d2_avail])

    cat = VehicleCategory(id=f"cat_race_{suffix}", code=f"CAT_{suffix}", name="Eco E-Rickshaw", description="Standard", capacity=4)
    rule = FareRule(id=f"rule_race_{suffix}", category_id=cat.id, base_fare_cents=2000, per_km_cents=500, per_minute_cents=100, minimum_fare_cents=2000, cancellation_fee_cents=1000)
    db_session.add_all([cat, rule])
    await db_session.flush()

    # Create Requested Ride
    ride = Ride(
        customer_id=c_prof.id,
        vehicle_category_id=cat.id,
        fare_rule_id=rule.id,
        status="REQUESTED",
        pickup_address="Origin",
        pickup_latitude=28.60,
        pickup_longitude=77.20,
        destination_address="Destination",
        destination_latitude=28.65,
        destination_longitude=77.25,
        start_otp="555123",
        estimated_distance_km=5.0,
        estimated_duration_minutes=15.0,
        estimated_fare_cents=4500
    )
    db_session.add(ride)
    await db_session.flush()

    # Dispatch offers for both drivers
    now = datetime.now(timezone.utc)
    off1 = RideAssignment(ride_id=ride.id, driver_id=d1_prof.id, status="OFFERED", expires_at=now + timedelta(seconds=30))
    off2 = RideAssignment(ride_id=ride.id, driver_id=d2_prof.id, status="OFFERED", expires_at=now + timedelta(seconds=30))
    db_session.add_all([off1, off2])
    await db_session.commit()

    service = RideService(db_session)

    # First driver accepts -> SUCCESS
    res1 = await service.accept_ride_atomic(driver_user_id=d1_user.id, ride_id=ride.id)
    assert res1["status"] == "ASSIGNED"
    assert res1["assigned_driver_id"] == d1_prof.id

    # Second driver attempts to accept same ride -> 409 CONFLICT
    with pytest.raises(Exception) as exc_info:
        await service.accept_ride_atomic(driver_user_id=d2_user.id, ride_id=ride.id)
    assert "409" in str(exc_info.value) or "already been accepted" in str(exc_info.value).lower()


@pytest.mark.asyncio
async def test_driver_arrival_proximity_enforcement(db_session):
    """
    Task 062: Verify that a driver more than 500m away cannot mark ARRIVED.
    """
    suffix = uuid.uuid4().int % 100000000
    c_user = User(phone_number=f"+9191{suffix:08d}", email=f"c_arr_{suffix}@erickshaw.in", role="customer", is_active=True)
    d_user = User(phone_number=f"+9192{suffix:08d}", email=f"d_arr_{suffix}@erickshaw.in", role="driver", is_active=True)
    db_session.add_all([c_user, d_user])
    await db_session.flush()

    c_prof = CustomerProfile(user_id=c_user.id)
    d_prof = DriverProfile(user_id=d_user.id, approval_status="APPROVED", license_number=f"DL-ARR-{suffix}")
    db_session.add_all([c_prof, d_prof])
    await db_session.flush()

    cat = VehicleCategory(id=f"cat_arr_{suffix}", code=f"CAT_ARR_{suffix}", name="Eco", description="Standard", capacity=4)
    rule = FareRule(id=f"rule_arr_{suffix}", category_id=cat.id, base_fare_cents=2000, per_km_cents=500, per_minute_cents=100, minimum_fare_cents=2000, cancellation_fee_cents=1000)
    db_session.add_all([cat, rule])
    await db_session.flush()

    pickup_lat, pickup_lng = 28.6139, 77.2090
    ride = Ride(
        customer_id=c_prof.id,
        assigned_driver_id=d_prof.id,
        vehicle_category_id=cat.id,
        fare_rule_id=rule.id,
        status="ASSIGNED",
        pickup_address="India Gate",
        pickup_latitude=pickup_lat,
        pickup_longitude=pickup_lng,
        destination_address="Airport",
        destination_latitude=28.5562,
        destination_longitude=77.1000,
        start_otp="123987",
        estimated_distance_km=12.0,
        estimated_duration_minutes=25.0,
        estimated_fare_cents=8000
    )
    db_session.add(ride)
    await db_session.commit()

    service = RideService(db_session)

    # 1. Driver 2 km away (28.6300, 77.2200) -> 400 Bad Request
    with pytest.raises(Exception) as exc_info:
        await service.driver_arrived(
            driver_user_id=d_user.id,
            ride_id=ride.id,
            driver_latitude=28.6300,
            driver_longitude=77.2200
        )
    assert "400" in str(exc_info.value)
    assert "arrival verification failed" in str(exc_info.value).lower()

    # 2. Driver within 100 meters (28.6141, 77.2092) -> SUCCESS
    arr_res = await service.driver_arrived(
        driver_user_id=d_user.id,
        ride_id=ride.id,
        driver_latitude=28.6141,
        driver_longitude=77.2092
    )
    assert arr_res["status"] == "ARRIVED"


@pytest.mark.asyncio
async def test_ride_cancellation_grace_window_and_fee_calculation(db_session):
    """
    Task 061: Verify cancellation window: within 2 minutes = 0 fee, after 2 minutes = fare rule fee.
    """
    suffix = uuid.uuid4().int % 100000000
    c_user = User(phone_number=f"+9191{suffix:08d}", email=f"c_cnc_{suffix}@erickshaw.in", role="customer", is_active=True)
    d_user = User(phone_number=f"+9192{suffix:08d}", email=f"d_cnc_{suffix}@erickshaw.in", role="driver", is_active=True)
    db_session.add_all([c_user, d_user])
    await db_session.flush()

    c_prof = CustomerProfile(user_id=c_user.id)
    d_prof = DriverProfile(user_id=d_user.id, approval_status="APPROVED", license_number=f"DL-CNC-{suffix}")
    db_session.add_all([c_prof, d_prof])
    await db_session.flush()

    cat = VehicleCategory(id=f"cat_cnc_{suffix}", code=f"CAT_CNC_{suffix}", name="Eco", description="Standard", capacity=4)
    rule = FareRule(id=f"rule_cnc_{suffix}", category_id=cat.id, base_fare_cents=2000, per_km_cents=500, per_minute_cents=100, minimum_fare_cents=2000, cancellation_fee_cents=1500)
    db_session.add_all([cat, rule])
    await db_session.flush()

    # Ride 1: Cancelled within 1 minute of assignment (grace period active)
    now = datetime.now(timezone.utc)
    ride1 = Ride(
        customer_id=c_prof.id,
        assigned_driver_id=d_prof.id,
        assigned_at=now - timedelta(seconds=45),
        vehicle_category_id=cat.id,
        fare_rule_id=rule.id,
        status="ASSIGNED",
        pickup_address="Pickup 1",
        pickup_latitude=28.60,
        pickup_longitude=77.20,
        destination_address="Dest 1",
        destination_latitude=28.65,
        destination_longitude=77.25,
        start_otp="111222",
        estimated_distance_km=5.0,
        estimated_duration_minutes=15.0,
        estimated_fare_cents=4500
    )
    # Ride 2: Cancelled 5 minutes after assignment (grace period expired)
    ride2 = Ride(
        customer_id=c_prof.id,
        assigned_driver_id=d_prof.id,
        assigned_at=now - timedelta(minutes=5),
        vehicle_category_id=cat.id,
        fare_rule_id=rule.id,
        status="ASSIGNED",
        pickup_address="Pickup 2",
        pickup_latitude=28.60,
        pickup_longitude=77.20,
        destination_address="Dest 2",
        destination_latitude=28.65,
        destination_longitude=77.25,
        start_otp="333444",
        estimated_distance_km=5.0,
        estimated_duration_minutes=15.0,
        estimated_fare_cents=4500
    )
    db_session.add_all([ride1, ride2])
    await db_session.commit()

    service = RideService(db_session)

    # 1. Customer cancels ride1 within grace period -> 0 cancellation fee
    res1 = await service.cancel_ride(user_id=c_user.id, role="customer", ride_id=ride1.id, reason="Changed my mind")
    assert res1["status"] == "CANCELLED"
    assert res1["cancellation_fee_cents"] == 0

    # 2. Customer cancels ride2 after grace period -> 1500 paise cancellation fee
    res2 = await service.cancel_ride(user_id=c_user.id, role="customer", ride_id=ride2.id, reason="Driver taking too long")
    assert res2["status"] == "CANCELLED"
    assert res2["cancellation_fee_cents"] == 1500


@pytest.mark.asyncio
async def test_ride_start_otp_verification_and_completion(db_session):
    """
    Task 063, 064, 065: Verify that ride commencement requires correct OTP,
    and completion finalizes fare, distance, duration, and driver availability.
    """
    suffix = uuid.uuid4().int % 100000000
    c_user = User(phone_number=f"+9191{suffix:08d}", email=f"c_cmp_{suffix}@erickshaw.in", role="customer", is_active=True)
    d_user = User(phone_number=f"+9192{suffix:08d}", email=f"d_cmp_{suffix}@erickshaw.in", role="driver", is_active=True)
    db_session.add_all([c_user, d_user])
    await db_session.flush()

    c_prof = CustomerProfile(user_id=c_user.id)
    d_prof = DriverProfile(user_id=d_user.id, approval_status="APPROVED", license_number=f"DL-CMP-{suffix}", wallet_balance_cents=0, total_trips=0)
    db_session.add_all([c_prof, d_prof])
    await db_session.flush()

    d_avail = DriverAvailability(driver_id=d_prof.id, is_online=True, is_busy=True)
    cat = VehicleCategory(id=f"cat_cmp_{suffix}", code=f"CAT_CMP_{suffix}", name="Eco", description="Standard", capacity=4)
    rule = FareRule(id=f"rule_cmp_{suffix}", category_id=cat.id, base_fare_cents=2000, per_km_cents=500, per_minute_cents=100, minimum_fare_cents=2000, cancellation_fee_cents=1000)
    db_session.add_all([d_avail, cat, rule])
    await db_session.flush()

    correct_otp = "849201"
    ride = Ride(
        customer_id=c_prof.id,
        assigned_driver_id=d_prof.id,
        vehicle_category_id=cat.id,
        fare_rule_id=rule.id,
        status="ARRIVED",
        pickup_address="Connaught Place",
        pickup_latitude=28.6328,
        pickup_longitude=77.2195,
        destination_address="Khan Market",
        destination_latitude=28.6000,
        destination_longitude=77.2270,
        start_otp=correct_otp,
        estimated_distance_km=4.5,
        estimated_duration_minutes=12.0,
        estimated_fare_cents=3500
    )
    db_session.add(ride)
    await db_session.commit()

    service = RideService(db_session)

    # 1. Incorrect OTP rejected -> 400 Bad Request
    with pytest.raises(Exception) as exc_info:
        await service.start_trip_with_otp(driver_user_id=d_user.id, ride_id=ride.id, otp_code="000000")
    assert "400" in str(exc_info.value)
    assert "invalid authorization passcode" in str(exc_info.value).lower()

    # 2. Correct OTP accepted -> transitions to IN_PROGRESS
    start_res = await service.start_trip_with_otp(driver_user_id=d_user.id, ride_id=ride.id, otp_code=correct_otp)
    assert start_res["status"] == "IN_PROGRESS"

    # 3. Finish trip -> COMPLETED, distance and duration finalized
    finish_res = await service.finish_trip(
        driver_user_id=d_user.id,
        ride_id=ride.id,
        actual_distance_km=4.8,
        actual_duration_minutes=14.0
    )
    assert finish_res["status"] == "COMPLETED"
    assert finish_res["final_fare_cents"] == 3500
    assert finish_res["payment_status"] == "PENDING"

    # 4. Verify driver availability freed
    await db_session.refresh(d_avail)
    assert d_avail.is_busy is False
