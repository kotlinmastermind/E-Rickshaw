"""Regression tests for database-backed nearby driver and vehicle profiles."""
from datetime import datetime, timezone
from types import SimpleNamespace

import pytest

from backend.api.routes.vehicles import get_nearby_vehicles


class FakeResult:
    def __init__(self, rows):
        self._rows = rows

    def all(self):
        return self._rows


class FakeDb:
    def __init__(self, rows):
        self.rows = rows

    async def execute(self, _statement):
        return FakeResult(self.rows)


@pytest.mark.asyncio
async def test_nearby_feed_uses_real_vehicle_fields_and_hides_unrated_placeholder_profile():
    now = datetime.now(timezone.utc)
    profile = SimpleNamespace(
        id="driver-1",
        rating_average=5.0,
        rating_count=0,
        license_number="PROFILE-LICENSE-SHOULD-NOT-BE-VEHICLE-PLATE",
        approval_status="APPROVED",
    )
    availability = SimpleNamespace(is_online=True, is_busy=False)
    location = SimpleNamespace(
        latitude=28.6139,
        longitude=77.2090,
        heading=None,
        recorded_at=now,
    )
    user = SimpleNamespace(
        full_name="Distinguished Guest",
        phone_number="+919999999999",
        is_active=True,
    )
    vehicle = SimpleNamespace(
        make="Mahindra",
        model="Treo",
        license_plate="BR01AB1234",
        is_active=True,
    )
    category = SimpleNamespace(
        code="E_RICKSHAW_STANDARD",
        name="Standard E-Rickshaw",
        capacity=3,
    )

    response = await get_nearby_vehicles(
        latitude=28.6139,
        longitude=77.2090,
        radius_km=5.0,
        category=None,
        db=FakeDb([(profile, availability, location, user, vehicle, category)]),
    )

    assert response["total_available"] == 1
    item = response["vehicles"][0]
    assert item["driver_name"] is None
    assert item["rating"] is None
    assert item["category_code"] == "E_RICKSHAW_STANDARD"
    assert item["vehicle_type_name"] == "Standard E-Rickshaw"
    assert item["model_name"] == "Mahindra Treo"
    assert item["license_plate"] == "BR01AB1234"
    assert item["capacity"] == 3
    assert item["battery_level"] is None
    assert item["bearing"] is None


@pytest.mark.asyncio
async def test_nearby_feed_returns_actual_rating_only_when_rating_count_is_positive():
    now = datetime.now(timezone.utc)
    profile = SimpleNamespace(
        id="driver-2",
        rating_average=4.7,
        rating_count=9,
        license_number="PROFILE-LICENSE",
        approval_status="APPROVED",
    )
    availability = SimpleNamespace(is_online=True, is_busy=False)
    location = SimpleNamespace(
        latitude=28.6139,
        longitude=77.2090,
        heading=90.0,
        recorded_at=now,
    )
    user = SimpleNamespace(full_name="Driver Two", phone_number=None, is_active=True)
    vehicle = SimpleNamespace(make="Piaggio", model="Ape", license_plate="BR02CD4567", is_active=True)
    category = SimpleNamespace(code="E_RICKSHAW_CARGO", name="Cargo E-Rickshaw", capacity=1)

    response = await get_nearby_vehicles(
        latitude=28.6139,
        longitude=77.2090,
        radius_km=5.0,
        category=None,
        db=FakeDb([(profile, availability, location, user, vehicle, category)]),
    )

    item = response["vehicles"][0]
    assert item["driver_name"] == "Driver Two"
    assert item["rating"] == 4.7
    assert item["model_name"] == "Piaggio Ape"
    assert item["license_plate"] == "BR02CD4567"
