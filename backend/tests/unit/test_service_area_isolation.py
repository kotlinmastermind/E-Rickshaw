import pytest
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
        __import__("sqlalchemy").select(GeofenceZone).where(
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
