import pytest
from sqlalchemy import select
from fastapi import HTTPException

from backend.application.services.service_area_service import ServiceAreaService
from backend.domain.models import DriverProfile, DriverServiceAreaAssignment, User, GeofenceZone


@pytest.mark.asyncio
async def test_driver_must_have_explicit_active_service_area_assignment(db_session):
    driver_user = User(
        phone_number="+919900001234",
        full_name="Area Test Driver",
        role="driver",
        is_active=True,
    )
    db_session.add(driver_user)
    await db_session.flush()
    driver = DriverProfile(
        user_id=driver_user.id,
        license_number="AREA-TEST-001",
        approval_status="APPROVED",
    )
    db_session.add(driver)
    await db_session.flush()

    area = (await db_session.execute(
        select(GeofenceZone).where(
            GeofenceZone.zone_type == "SERVICE_AREA",
            GeofenceZone.city_code == "DELHI_NCR",
        )
    )).scalars().first()
    service = ServiceAreaService(db_session)

    with pytest.raises(HTTPException) as exc:
        await service.require_driver_assignment(driver.id, area.id)
    assert exc.value.status_code == 403

    assignment = DriverServiceAreaAssignment(
        driver_id=driver.id,
        service_area_id=area.id,
        is_active=True,
    )
    db_session.add(assignment)
    await db_session.commit()

    await service.require_driver_assignment(driver.id, area.id)
    assert await service.active_driver_ids(area.id, [driver.id, "not-assigned"]) == {driver.id}

    assignment.is_active = False
    await db_session.commit()
    with pytest.raises(HTTPException) as revoked:
        await service.require_driver_assignment(driver.id, area.id)
    assert revoked.value.status_code == 403


@pytest.mark.asyncio
async def test_service_area_route_rejects_cross_area_and_inactive_area(db_session):
    service = ServiceAreaService(db_session)

    with pytest.raises(HTTPException) as cross:
        await service.resolve_route(
            28.6139, 77.2090,
            25.5941, 85.1376,
        )
    assert cross.value.status_code == 400

    delhi = (await db_session.execute(
        __import__("sqlalchemy").select(GeofenceZone).where(
            GeofenceZone.city_code == "DELHI_NCR",
            GeofenceZone.zone_type == "SERVICE_AREA",
        )
    )).scalars().first()
    delhi.is_active = False
    await db_session.commit()

    with pytest.raises(HTTPException) as inactive:
        await service.resolve_route(
            28.6139, 77.2090,
            28.6300, 77.2200,
        )
    assert inactive.value.status_code == 400


from backend.api.routes.driver_onboarding import (
    DriverServiceAreaRequestPayload,
    submit_driver_service_area_request,
)
from backend.api.routes.admin import (
    DriverServiceAreaRequestApprovalPayload,
    approve_driver_service_area_request,
)


@pytest.mark.asyncio
async def test_driver_can_submit_one_pending_locality_request(db_session):
    driver_user = User(
        phone_number="+919900001235",
        full_name="Area Request Driver",
        role="driver",
        is_active=True,
    )
    db_session.add(driver_user)
    await db_session.flush()
    driver = DriverProfile(
        user_id=driver_user.id,
        license_number="AREA-REQUEST-001",
        approval_status="PENDING",
    )
    db_session.add(driver)
    await db_session.commit()

    payload = DriverServiceAreaRequestPayload(
        state_name="Bihar",
        district_name="Nalanda",
        locality_name="Bihar Sharif",
        postal_code="803101",
        latitude=25.1972,
        longitude=85.5239,
    )
    created = await submit_driver_service_area_request(payload, driver_user, db_session)
    assert created["status"] == "PENDING"
    assert created["request_id"]

    with pytest.raises(HTTPException) as duplicate:
        await submit_driver_service_area_request(payload, driver_user, db_session)
    assert duplicate.value.status_code == 409


@pytest.mark.asyncio
async def test_admin_approval_assigns_driver_only_inside_configured_area(db_session):
    import json
    from backend.domain.models import DriverServiceAreaAssignment, VehicleCategory, FareRule, Vehicle, DriverVehicleAssignment

    driver_user = User(
        phone_number="+919900001236",
        full_name="Approved Area Driver",
        role="driver",
        is_active=True,
    )
    admin_user = User(
        phone_number="+919900001237",
        full_name="Area Admin",
        role="admin",
        is_active=True,
    )
    db_session.add_all([driver_user, admin_user])
    await db_session.flush()

    driver = DriverProfile(
        user_id=driver_user.id,
        license_number="AREA-REQUEST-002",
        approval_status="PENDING",
    )
    area = GeofenceZone(
        name="Test Locality Service Area",
        city_code="TEST_LOCALITY",
        zone_type="SERVICE_AREA",
        coordinates_json=json.dumps([
            {"lat": 9.0, "lng": 9.0},
            {"lat": 11.0, "lng": 9.0},
            {"lat": 11.0, "lng": 11.0},
            {"lat": 9.0, "lng": 11.0},
        ]),
        is_active=True,
    )
    category = VehicleCategory(code="AREA-TEST-CAT", name="Area Test Vehicle", is_active=True)
    db_session.add_all([driver, area, category])
    await db_session.flush()
    vehicle = Vehicle(
        category_id=category.id,
        make="Test",
        model="E-Rickshaw",
        year=2025,
        license_plate="AREA-TEST-002",
        is_active=True,
    )
    db_session.add(vehicle)
    await db_session.flush()
    db_session.add(DriverVehicleAssignment(driver_id=driver.id, vehicle_id=vehicle.id, is_active=True))
    db_session.add(FareRule(
        category_id=category.id,
        city_code="TEST_LOCALITY",
        base_fare_cents=1000,
        per_km_cents=500,
        per_minute_cents=100,
        minimum_fare_cents=1500,
        is_active=True,
        workflow_status="ACTIVE",
    ))
    await db_session.flush()

    created = await submit_driver_service_area_request(
        DriverServiceAreaRequestPayload(
            state_name="Test State",
            district_name="Test District",
            locality_name="Test Locality",
            latitude=10.0,
            longitude=10.0,
        ),
        driver_user,
        db_session,
    )
    driver.approval_status = "APPROVED"
    result = await approve_driver_service_area_request(
        created["request_id"],
        DriverServiceAreaRequestApprovalPayload(service_area_id=area.id),
        admin_user,
        db_session,
    )
    assert result["status"] == "APPROVED"

    assignment = (await db_session.execute(
        select(DriverServiceAreaAssignment).where(
            DriverServiceAreaAssignment.driver_id == driver.id,
            DriverServiceAreaAssignment.service_area_id == area.id,
            DriverServiceAreaAssignment.is_active.is_(True),
        )
    )).scalar_one_or_none()
    assert assignment is not None


@pytest.mark.asyncio
async def test_approved_driver_auto_joins_existing_area_when_tariff_is_ready(db_session):
    import json
    from backend.domain.models import DriverServiceAreaAssignment, VehicleCategory, FareRule, Vehicle, DriverVehicleAssignment

    driver_user = User(
        phone_number="+919900001238",
        full_name="Auto Area Driver",
        role="driver",
        is_active=True,
    )
    db_session.add(driver_user)
    await db_session.flush()
    driver = DriverProfile(
        user_id=driver_user.id,
        license_number="AREA-REQUEST-003",
        approval_status="APPROVED",
    )
    area = GeofenceZone(
        name="Auto Join Test Area",
        city_code="AUTO_JOIN_CITY",
        zone_type="SERVICE_AREA",
        coordinates_json=json.dumps([
            {"lat": 9.0, "lng": 9.0},
            {"lat": 11.0, "lng": 9.0},
            {"lat": 11.0, "lng": 11.0},
            {"lat": 9.0, "lng": 11.0},
        ]),
        is_active=True,
    )
    category = VehicleCategory(code="AUTO-JOIN-CAT", name="Auto Join Vehicle", is_active=True)
    db_session.add_all([driver, area, category])
    await db_session.flush()
    vehicle = Vehicle(
        category_id=category.id,
        make="Test",
        model="E-Rickshaw",
        year=2025,
        license_plate="AREA-TEST-003",
        is_active=True,
    )
    db_session.add(vehicle)
    await db_session.flush()
    db_session.add(DriverVehicleAssignment(driver_id=driver.id, vehicle_id=vehicle.id, is_active=True))
    db_session.add(FareRule(
        category_id=category.id,
        city_code="AUTO_JOIN_CITY",
        base_fare_cents=1000,
        per_km_cents=500,
        per_minute_cents=100,
        minimum_fare_cents=1500,
        is_active=True,
        workflow_status="ACTIVE",
    ))
    await db_session.flush()

    result = await submit_driver_service_area_request(
        DriverServiceAreaRequestPayload(
            state_name="Test State",
            district_name="Test District",
            locality_name="Auto Join Locality",
            latitude=10.0,
            longitude=10.0,
        ),
        driver_user,
        db_session,
    )
    assert result["status"] == "APPROVED"

    assignment = (await db_session.execute(
        select(DriverServiceAreaAssignment).where(
            DriverServiceAreaAssignment.driver_id == driver.id,
            DriverServiceAreaAssignment.service_area_id == area.id,
            DriverServiceAreaAssignment.is_active.is_(True),
        )
    )).scalar_one_or_none()
    assert assignment is not None


@pytest.mark.asyncio
async def test_admin_geofence_lifecycle_active_ride_protection_malformed_fail_closed_and_auth(
    db_session, client, customer_user, customer_token
):
    import json
    import uuid
    from httpx import ASGITransport, AsyncClient
    from backend.main import app
    from backend.api.deps import get_current_admin
    from backend.domain.models import (
        CustomerProfile, FareRule, GeofenceZoneAudit, Ride, VehicleCategory,
    )

    unauth_del = await client.delete("/api/v1/admin/geofences/nonexistent-id")
    assert unauth_del.status_code == 401

    cust_del = await client.delete(
        "/api/v1/admin/geofences/nonexistent-id",
        headers={"Authorization": f"Bearer {customer_token}"},
    )
    assert cust_del.status_code == 403

    admin_user = User(
        id=str(uuid.uuid4()),
        phone_number="+919900001299",
        email="area_admin_lifecycle@erickshaw.in",
        full_name="Area Lifecycle Admin",
        role="admin",
        is_active=True,
        is_verified=True,
    )
    db_session.add(admin_user)
    await db_session.flush()

    delhi_area = (await db_session.execute(
        select(GeofenceZone).where(
            GeofenceZone.city_code == "DELHI_NCR",
            GeofenceZone.zone_type == "SERVICE_AREA",
        )
    )).scalars().first()
    cust_profile = (await db_session.execute(
        select(CustomerProfile).where(CustomerProfile.user_id == customer_user.id)
    )).scalar_one()
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    rule = (await db_session.execute(select(FareRule).where(FareRule.city_code == "DELHI_NCR"))).scalars().first()

    active_ride = Ride(
        id=str(uuid.uuid4()),
        customer_id=cust_profile.id,
        vehicle_category_id=cat.id,
        fare_rule_id=rule.id,
        service_area_id=delhi_area.id,
        status="REQUESTED",
        pickup_address="Connaught Place",
        pickup_latitude=28.6315,
        pickup_longitude=77.2167,
        destination_address="India Gate",
        destination_latitude=28.6129,
        destination_longitude=77.2295,
        start_otp="1234",
        estimated_distance_km=3.0,
        estimated_duration_minutes=10.0,
        estimated_fare_cents=5000,
    )
    db_session.add(active_ride)
    await db_session.commit()

    app.dependency_overrides[get_current_admin] = lambda: admin_user
    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
            # 1. Both DELETE and PUT (is_active=False) must block deactivation while an active ride exists
            del_blocked = await ac.delete(f"/api/v1/admin/geofences/{delhi_area.id}")
            assert del_blocked.status_code == 409

            put_blocked = await ac.put(
                f"/api/v1/admin/geofences/{delhi_area.id}",
                json={
                    "name": delhi_area.name,
                    "zone_type": "SERVICE_AREA",
                    "city_code": "DELHI_NCR",
                    "coordinates": json.loads(delhi_area.coordinates_json),
                    "surge_multiplier": 1.0,
                    "is_active": False,
                },
            )
            assert put_blocked.status_code == 409

            # 2. Complete the ride -> deactivation succeeds via DELETE and records audit
            active_ride.status = "COMPLETED"
            await db_session.commit()

            del_ok = await ac.delete(f"/api/v1/admin/geofences/{delhi_area.id}")
            assert del_ok.status_code == 200
            assert del_ok.json()["is_active"] is False
            assert del_ok.json()["status"] == "DEACTIVATED"

            # 3. Reactivate via PUT and confirm consistent UPDATED state + audit entries
            put_reactivate = await ac.put(
                f"/api/v1/admin/geofences/{delhi_area.id}",
                json={
                    "name": delhi_area.name,
                    "zone_type": "SERVICE_AREA",
                    "city_code": "DELHI_NCR",
                    "coordinates": json.loads(delhi_area.coordinates_json),
                    "surge_multiplier": 1.0,
                    "is_active": True,
                },
            )
            assert put_reactivate.status_code == 200
            assert put_reactivate.json()["is_active"] is True
            assert put_reactivate.json()["status"] == "UPDATED"

            audits = (await db_session.execute(
                select(GeofenceZoneAudit).where(GeofenceZoneAudit.zone_id == delhi_area.id)
            )).scalars().all()
            actions = [a.action for a in audits]
            assert "DEACTIVATED" in actions
            assert "ACTIVATED" in actions

            # 4. Malformed active polygon must fail closed (503) in listing and preview
            corrupt = GeofenceZone(
                name="Broken Active Zone",
                zone_type="RESTRICTED",
                coordinates_json="not-valid-json",
                is_active=True,
            )
            db_session.add(corrupt)
            await db_session.commit()

            list_res = await ac.get("/api/v1/admin/geofences")
            assert list_res.status_code == 503

            preview_res = await ac.post(
                "/api/v1/admin/geofences/preview",
                json={
                    "coordinates": [
                        {"lat": 20.0, "lng": 75.0},
                        {"lat": 20.1, "lng": 75.0},
                        {"lat": 20.1, "lng": 75.1},
                    ]
                },
            )
            assert preview_res.status_code == 503

            await db_session.delete(corrupt)
            await db_session.commit()
    finally:
        app.dependency_overrides.pop(get_current_admin, None)
