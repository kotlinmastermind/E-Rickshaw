import uuid

import pytest

from backend.api.routes.admin import get_live_mobility_feed
from backend.domain.models import CustomerProfile, DriverProfile, User


@pytest.mark.asyncio
async def test_admin_live_feed_does_not_invent_drivers_riders_revenue_or_tariffs(db_session):
    suffix = uuid.uuid4().hex[:10]
    admin = User(
        phone_number=f"+9198{uuid.uuid4().int % 10**10:010d}",
        full_name="Operations Admin",
        role="admin",
        is_active=True,
    )
    driver_user = User(
        phone_number=f"+9197{uuid.uuid4().int % 10**10:010d}",
        full_name="Real Driver Without GPS",
        role="driver",
        is_active=True,
    )
    customer_user = User(
        phone_number=f"+9196{uuid.uuid4().int % 10**10:010d}",
        full_name="Real Customer Without Rides",
        role="customer",
        is_active=True,
    )
    db_session.add_all([admin, driver_user, customer_user])
    await db_session.flush()
    driver = DriverProfile(user_id=driver_user.id, license_number=f"DL-LIVE-{suffix}", approval_status="APPROVED")
    customer = CustomerProfile(user_id=customer_user.id)
    db_session.add_all([driver, customer])
    await db_session.commit()

    feed = await get_live_mobility_feed(admin=admin, db=db_session)

    assert feed["stats"]["online_drivers"] == 0
    assert feed["stats"]["active_rides"] == 0
    assert feed["stats"]["waiting_riders"] == 0
    assert feed["stats"]["gross_booking_inr"] == 0.0
    assert feed["drivers"] == [{
        "id": driver.id,
        "user_id": driver_user.id,
        "name": driver_user.full_name,
        "phone": driver_user.phone_number,
        "email": driver_user.email,
        "plate": None,
        "model": None,
        "category": None,
        "status": "OFFLINE",
        "approval_status": "APPROVED",
        "lat": None,
        "lng": None,
        "battery": None,
        "speed": None,
        "heading": None,
        "location_recorded_at": None,
        "rating": None,
        "trips": int(driver.total_trips or 0),
        "dl": driver.license_number,
    }]
    assert feed["riders"] == []
    assert feed["active_rides"] == []
    assert feed["tariffs"] == []
    assert feed["service_areas"] == []
    assert len(feed["customers"]) == 1
    assert feed["customers"][0]["name"] == customer_user.full_name
    assert feed["customers"][0]["rides"] == 0
    assert feed["customers"][0]["rating"] is None