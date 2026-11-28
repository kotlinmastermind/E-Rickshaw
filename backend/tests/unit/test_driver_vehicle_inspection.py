"""Comprehensive unit tests for Driver Vehicle Inspection, Camera Uploads, Supabase Storage, and Online Gating."""
import io
import uuid
import pytest
from datetime import datetime, timezone, timedelta
from fastapi import HTTPException, UploadFile
from sqlalchemy import select

from backend.domain.models import User, DriverProfile, Vehicle, VehicleCategory, DriverVehicleAssignment, DriverVehicleInspection, DriverVehicleInspectionPhoto
from backend.application.services.driver_inspection_service import DriverInspectionService, get_ist_today
from backend.application.services.driver_service import DriverService
from backend.integrations.storage.provider import get_storage_provider, generate_compliance_placeholder_png


@pytest.fixture
def sample_jpeg_file() -> UploadFile:
    """Creates a valid JPEG UploadFile."""
    jpeg_bytes = b"\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x01\x00`\x00`\x00\x00" + (b"\x00" * 200) + b"\xff\xd9"
    file_obj = io.BytesIO(jpeg_bytes)
    return UploadFile(filename="camera_photo.jpg", file=file_obj, headers={"content-type": "image/jpeg"})


@pytest.fixture
def sample_png_file() -> UploadFile:
    """Creates a valid standalone PNG UploadFile."""
    png_bytes = generate_compliance_placeholder_png(doc_type="ODOMETER", status="VERIFIED")
    file_obj = io.BytesIO(png_bytes)
    return UploadFile(filename="camera_odometer.png", file=file_obj, headers={"content-type": "image/png"})


@pytest.mark.asyncio
async def test_draft_inspection_creation_and_baseline_odometer(db_session):
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    uid = uuid.uuid4().hex[:8]
    user = User(phone_number=f"+9191{uid}", email=f"driver_{uid}@vaisentra.com", role="driver")
    db_session.add(user)
    await db_session.flush()

    driver = DriverProfile(user_id=user.id, license_number=f"DL-INSP-{uid}", approval_status="APPROVED")
    db_session.add(driver)
    vehicle = Vehicle(category_id=cat.id, license_plate=f"DL-01-ER-{uid[:4].upper()}", make="Mahindra", model="Treo EV", year=2025)
    db_session.add(vehicle)
    await db_session.flush()

    assign = DriverVehicleAssignment(driver_id=driver.id, vehicle_id=vehicle.id, is_active=True)
    db_session.add(assign)
    await db_session.commit()

    service = DriverInspectionService(db_session)
    draft = await service.get_or_create_draft_inspection(user.id, vehicle.id)

    assert draft["inspection_id"] is not None
    assert draft["inspection_status"] == "DRAFT"
    assert draft["vehicle_plate"] == vehicle.license_plate
    assert draft["photos_count"] == 0
    assert len(draft["standard_checklist_items"]) == 9


@pytest.mark.asyncio
async def test_photo_upload_storage_and_magic_bytes_validation(db_session, sample_jpeg_file):
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    uid = uuid.uuid4().hex[:8]
    user = User(phone_number=f"+9192{uid}", email=f"driver_{uid}@vaisentra.com", role="driver")
    db_session.add(user)
    await db_session.flush()

    driver = DriverProfile(user_id=user.id, license_number=f"DL-PHOTO-{uid}", approval_status="APPROVED")
    vehicle = Vehicle(category_id=cat.id, license_plate=f"DL-02-ER-{uid[:4].upper()}", make="Mahindra", model="Treo EV", year=2025)
    db_session.add_all([driver, vehicle])
    await db_session.flush()

    db_session.add(DriverVehicleAssignment(driver_id=driver.id, vehicle_id=vehicle.id, is_active=True))
    await db_session.commit()

    service = DriverInspectionService(db_session)
    draft = await service.get_or_create_draft_inspection(user.id, vehicle.id)

    # 1. Upload valid photo
    upload_res = await service.upload_inspection_photo(
        user_id=user.id,
        inspection_id=draft["inspection_id"],
        photo_category="ODOMETER",
        file=sample_jpeg_file
    )
    assert upload_res["photo_id"] is not None
    assert upload_res["category"] == "ODOMETER"
    assert upload_res["mime_type"] == "image/jpeg"
    assert "driver-vehicle-inspections" in upload_res["storage_path"]
    assert upload_res["file_size_bytes"] > 0

    # 2. Reject arbitrary or spoofed file
    fake_file = UploadFile(filename="malicious.exe", file=io.BytesIO(b"MZExecutableFakeHeader1234567890"), headers={"content-type": "image/jpeg"})
    with pytest.raises(HTTPException) as exc_info:
        await service.upload_inspection_photo(
            user_id=user.id,
            inspection_id=draft["inspection_id"],
            photo_category="FRONT",
            file=fake_file
        )
    assert exc_info.value.status_code == 400
    assert "Security validation failed" in exc_info.value.detail


@pytest.mark.asyncio
async def test_submit_inspection_missing_photos_rejected(db_session, sample_jpeg_file):
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    uid = uuid.uuid4().hex[:8]
    user = User(phone_number=f"+9193{uid}", email=f"driver_{uid}@vaisentra.com", role="driver")
    db_session.add(user)
    await db_session.flush()

    driver = DriverProfile(user_id=user.id, license_number=f"DL-MISS-{uid}", approval_status="APPROVED")
    vehicle = Vehicle(category_id=cat.id, license_plate=f"DL-03-ER-{uid[:4].upper()}", make="Mahindra", model="Treo EV", year=2025)
    db_session.add_all([driver, vehicle])
    await db_session.flush()
    db_session.add(DriverVehicleAssignment(driver_id=driver.id, vehicle_id=vehicle.id, is_active=True))
    await db_session.commit()

    service = DriverInspectionService(db_session)
    draft = await service.get_or_create_draft_inspection(user.id, vehicle.id)

    # Only upload 1 photo out of 5 required
    await service.upload_inspection_photo(user.id, draft["inspection_id"], "ODOMETER", sample_jpeg_file)

    with pytest.raises(HTTPException) as exc_info:
        await service.submit_inspection(
            user_id=user.id,
            inspection_id=draft["inspection_id"],
            odometer_reading=12500.5,
            checklist_results={"brakes": "PASS", "tyres": "PASS"}
        )
    assert exc_info.value.status_code == 400
    assert "Missing: FRONT, REAR, LEFT_SIDE, RIGHT_SIDE" in exc_info.value.detail


@pytest.mark.asyncio
async def test_full_inspection_submission_approval_and_online_gating(db_session):
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    uid = uuid.uuid4().hex[:8]
    user = User(phone_number=f"+9194{uid}", email=f"driver_{uid}@vaisentra.com", role="driver")
    db_session.add(user)
    await db_session.flush()

    driver = DriverProfile(user_id=user.id, license_number=f"DL-GATE-{uid}", approval_status="APPROVED")
    vehicle = Vehicle(category_id=cat.id, license_plate=f"DL-04-ER-{uid[:4].upper()}", make="Piaggio", model="Ape E-City", year=2026)
    db_session.add_all([driver, vehicle])
    await db_session.flush()
    db_session.add(DriverVehicleAssignment(driver_id=driver.id, vehicle_id=vehicle.id, is_active=True))
    await db_session.commit()

    driver_service = DriverService(db_session)
    inspection_service = DriverInspectionService(db_session)

    # 1. Driver attempts to go online WITHOUT inspection -> strictly BLOCKED with 403 INSPECTION_REQUIRED
    with pytest.raises(HTTPException) as exc_info:
        await driver_service.toggle_availability(user.id, is_online=True)
    assert exc_info.value.status_code == 403
    assert exc_info.value.headers.get("X-Error-Code") == "INSPECTION_REQUIRED"

    # 2. Driver starts inspection and uploads all 5 required photos
    draft = await inspection_service.get_or_create_draft_inspection(user.id, vehicle.id)
    insp_id = draft["inspection_id"]

    for category in ["ODOMETER", "FRONT", "REAR", "LEFT_SIDE", "RIGHT_SIDE"]:
        png_bytes = generate_compliance_placeholder_png(doc_type=category, status="VERIFIED")
        file = UploadFile(filename=f"{category.lower()}.png", file=io.BytesIO(png_bytes), headers={"content-type": "image/png"})
        await inspection_service.upload_inspection_photo(user.id, insp_id, category, file)

    # 3. Submit valid passed inspection
    checklist = {
        "brakes": "PASS",
        "steering": "PASS",
        "tyres": "PASS",
        "headlights_taillights": "PASS",
        "indicators_horn": "PASS",
        "mirrors_windshield": "PASS",
        "battery_electrical": "PASS",
        "cabin_cleanliness": "PASS",
        "emergency_safety": "PASS"
    }
    sub_res = await inspection_service.submit_inspection(
        user_id=user.id,
        inspection_id=insp_id,
        odometer_reading=4520.0,
        checklist_results=checklist,
        driver_remarks="Vehicle cleaned, battery 100% charged, brakes sharp."
    )
    assert sub_res["status"] == "APPROVED"
    assert sub_res["overall_result"] == "PASS"
    assert sub_res["can_go_online"] is True

    # 4. Driver attempts to go online WITH approved inspection -> ALLOWED
    online_res = await driver_service.toggle_availability(user.id, is_online=True)
    assert online_res["is_online"] is True


@pytest.mark.asyncio
async def test_critical_safety_failure_blocks_online_availability(db_session):
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    uid = uuid.uuid4().hex[:8]
    user = User(phone_number=f"+9195{uid}", email=f"driver_{uid}@vaisentra.com", role="driver")
    db_session.add(user)
    await db_session.flush()

    driver = DriverProfile(user_id=user.id, license_number=f"DL-CRIT-{uid}", approval_status="APPROVED")
    vehicle = Vehicle(category_id=cat.id, license_plate=f"DL-05-ER-{uid[:4].upper()}", make="Mahindra", model="Treo EV", year=2025)
    db_session.add_all([driver, vehicle])
    await db_session.flush()
    db_session.add(DriverVehicleAssignment(driver_id=driver.id, vehicle_id=vehicle.id, is_active=True))
    await db_session.commit()

    service = DriverInspectionService(db_session)
    driver_service = DriverService(db_session)

    draft = await service.get_or_create_draft_inspection(user.id, vehicle.id)
    insp_id = draft["inspection_id"]

    for category in ["ODOMETER", "FRONT", "REAR", "LEFT_SIDE", "RIGHT_SIDE"]:
        png_bytes = generate_compliance_placeholder_png(doc_type=category, status="DEFECT")
        file = UploadFile(filename=f"{category.lower()}.png", file=io.BytesIO(png_bytes), headers={"content-type": "image/png"})
        await service.upload_inspection_photo(user.id, insp_id, category, file)

    # Driver reports critical brake failure
    checklist = {
        "brakes": "FAIL",
        "steering": "PASS",
        "tyres": "PASS",
        "headlights_taillights": "PASS",
        "indicators_horn": "PASS",
        "mirrors_windshield": "PASS",
        "battery_electrical": "PASS",
        "cabin_cleanliness": "PASS",
        "emergency_safety": "PASS"
    }
    sub_res = await service.submit_inspection(
        user_id=user.id,
        inspection_id=insp_id,
        odometer_reading=4530.0,
        checklist_results=checklist,
        driver_remarks="Brake pedal feels soft, stopping distance compromised."
    )
    assert sub_res["status"] == "FAILED"
    assert sub_res["overall_result"] == "FAIL"
    assert sub_res["can_go_online"] is False
    assert "brakes" in sub_res["critical_failures"]

    # Driver attempts to go online -> blocked with 403 INSPECTION_FAILED
    with pytest.raises(HTTPException) as exc_info:
        await driver_service.toggle_availability(user.id, is_online=True)
    assert exc_info.value.status_code == 403
    assert exc_info.value.headers.get("X-Error-Code") == "INSPECTION_FAILED"


@pytest.mark.asyncio
async def test_admin_inspection_management_and_approval(db_session):
    cat = (await db_session.execute(select(VehicleCategory))).scalars().first()
    uid = uuid.uuid4().hex[:8]
    user = User(phone_number=f"+9196{uid}", email=f"driver_{uid}@vaisentra.com", role="driver", full_name="Ramesh Kumar")
    admin_user = User(phone_number=f"+9197{uid}", email=f"admin_{uid}@vaisentra.com", role="admin")
    db_session.add_all([user, admin_user])
    await db_session.flush()

    driver = DriverProfile(user_id=user.id, license_number=f"DL-ADM-{uid}", approval_status="APPROVED")
    vehicle = Vehicle(category_id=cat.id, license_plate=f"DL-06-ER-{uid[:4].upper()}", make="Mahindra", model="Treo EV", year=2025)
    db_session.add_all([driver, vehicle])
    await db_session.flush()
    db_session.add(DriverVehicleAssignment(driver_id=driver.id, vehicle_id=vehicle.id, is_active=True))
    await db_session.commit()

    service = DriverInspectionService(db_session)
    draft = await service.get_or_create_draft_inspection(user.id, vehicle.id)
    insp_id = draft["inspection_id"]

    for category in ["ODOMETER", "FRONT", "REAR", "LEFT_SIDE", "RIGHT_SIDE"]:
        png_bytes = generate_compliance_placeholder_png(doc_type=category, status="VERIFIED")
        file = UploadFile(filename=f"{category.lower()}.png", file=io.BytesIO(png_bytes), headers={"content-type": "image/png"})
        await service.upload_inspection_photo(user.id, insp_id, category, file)

    # Submit the complete mandatory photo set and explicit checklist before Admin review.
    from backend.application.services.driver_inspection_service import STANDARD_CHECKLIST_ITEMS
    submit_result = await service.submit_inspection(
        user_id=user.id,
        inspection_id=insp_id,
        odometer_reading=125.0,
        checklist_results={key: "PASS" for key in STANDARD_CHECKLIST_ITEMS},
        driver_remarks="All required safety checks passed."
    )
    assert submit_result["status"] in {"APPROVED", "PENDING_VERIFICATION"}

    # 1. Admin gets inspection stats
    stats = await service.get_admin_inspection_stats()
    assert "today_inspections" in stats
    assert "pending_verification" in stats

    # 2. Admin retrieves inspection details
    details = await service.get_admin_inspection_details(insp_id)
    assert details["driver_name"] == "Ramesh Kumar"
    assert len(details["photos"]) == 5

    # 3. Admin reviews and approves inspection
    review_res = await service.review_inspection_by_admin(
        inspection_id=insp_id,
        admin_user_id=admin_user.id,
        action="APPROVE"
    )
    assert review_res["status"] == "APPROVED"
    assert review_res["verification_status"] == "VERIFIED"
