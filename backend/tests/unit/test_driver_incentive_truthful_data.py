"""Regression tests for database-backed driver incentive eligibility and history."""

import uuid

import pytest
from sqlalchemy import func, select

from backend.application.services.driver_financial_service import DriverFinancialService
from backend.domain.models import (
    DriverIncentiveProgress,
    DriverProfile,
    User,
)


@pytest.mark.asyncio
async def test_incentive_overview_does_not_seed_fake_quests_or_bonus_history(db_session):
    suffix = uuid.uuid4().hex[:10]
    user = User(
        phone_number=f"+9198{suffix[:8]}",
        full_name="Incentive Test Driver",
        role="driver",
        is_active=True,
    )
    db_session.add(user)
    await db_session.flush()
    driver = DriverProfile(
        user_id=user.id,
        license_number=f"DL-INC-{suffix}",
        approval_status="APPROVED",
        rating_average=None,
        rating_count=0,
        total_trips=0,
    )
    db_session.add(driver)
    await db_session.commit()

    service = DriverFinancialService(db_session)
    result = await service.get_incentive_overview(driver.id)

    assert result["active_milestones"] == []
    assert result["bonus_history"] == []
    assert result["overall_incentive_eligibility"] is False

    checks = {item["criterion"]: item for item in result["eligibility_checklist"]}
    assert checks["Minimum Rating Average"]["actual"] == "Not rated"
    assert checks["Minimum Rating Average"]["passed"] is False
    assert checks["Offer Acceptance Rate"]["actual"] == "Not available"
    assert checks["Offer Acceptance Rate"]["passed"] is False
    assert checks["Driver Cancellation Rate"]["actual"] == "Not available"
    assert checks["Driver Cancellation Rate"]["passed"] is False
    assert checks["Active Safety Violations"]["actual"] == "0"
    assert checks["Vehicle Cleanliness Score"]["actual"] == "Not available"
    assert checks["Vehicle Cleanliness Score"]["passed"] is False

    seeded_count = int((await db_session.execute(
        select(func.count(DriverIncentiveProgress.id)).where(
            DriverIncentiveProgress.driver_id == driver.id
        )
    )).scalar_one() or 0)
    assert seeded_count == 0
