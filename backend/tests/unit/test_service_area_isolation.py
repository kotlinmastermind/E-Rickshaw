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
    from backend.domain.models import DriverServiceAreaAssignment

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
        approval_status="APPROVED",
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
    db_session.add_all([driver, area])
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
