import pytest
import uuid
from datetime import datetime, timezone, timedelta
from fastapi import UploadFile
import io

from backend.application.services.driver_onboarding_service import DriverOnboardingService
from backend.domain.models import (
    User, DriverProfile, DriverDocument, Vehicle, VehicleCategory, DriverVehicleAssignment,
    DriverTrainingModule, DriverTrainingProgress, DriverVehicleInspection,
    DriverScheduleAvailability, DriverOnboardingTicket, DriverVehicleInspectionPhoto
)

@pytest.mark.asyncio
async def test_part9_driver_onboarding_end_to_end(db_session):
    u_suffix = uuid.uuid4().hex[:6]
    user = User(
        phone_number=f"+91987{u_suffix}0",
        full_name="Chauffeur Rajeshwar Varma",
        role="driver",
        is_active=True,
        is_verified=True
    )
    db_session.add(user)
    await db_session.flush()

    service = DriverOnboardingService(db_session)

    # 1. Feature 201, 216, 224: Checklist, Completeness Meter & Application Status Tracker
    overview = await service.get_onboarding_overview(user.id)
    assert overview["approval_status"] == "PENDING"
    assert "checklist_stages" in overview
    assert len(overview["checklist_stages"]) >= 6
    assert overview["profile_completeness_percentage"] >= 10
    assert overview["onboarding_step"] == "IDENTITY_SUBMITTED"

    # 2. Feature 202 & 223: Identity verification and re-verification
    identity_res = await service.get_identity_status(user.id)
    assert identity_res["driver_id"] is not None
    assert identity_res["status"] == "PENDING"

    verify_res = await service.submit_identity_verification(
        user_id=user.id,
        id_type="AADHAAR",
        id_number="548912349081",
        selfie_notes="Biometric facial match verified"
    )
    assert verify_res["status"] == "PENDING_PROVIDER"
    assert verify_res["id_number_masked"] == "***9081"

    # Re-check identity status
    identity_res2 = await service.get_identity_status(user.id)
    assert identity_res2["status"] == "PENDING"

    # 3. Feature 204, 205, 206: Guided Photo Capture & Document Quality Checks
    file_bytes = b"%PDF-1.5 DOCUMENT-CONTENT-TEST-LUXURY-CHAUFFEUR-BADGE-2026-HIGH-RES"
    upload_file = UploadFile(
        file=io.BytesIO(file_bytes),
        filename="chauffeur_badge_front.pdf",
        headers={"content-type": "application/pdf"}
    )
    doc_res = await service.resubmit_document(
        user_id=user.id,
        document_type="DRIVING_LICENSE",
        file=upload_file,
        guidance_confirmed=True
    )
    assert doc_res["verification_status"] == "PENDING"
    assert doc_res["image_quality_score"] == 0.0
    assert doc_res["resubmission_count"] >= 1
    assert doc_res["guidance_applied"] is True

    # 4. Feature 203: Document Expiry Reminders
    expiries = await service.get_document_expiry_reminders(user.id)
    assert len(expiries) >= 1
    assert expiries[0]["document_type"] == "DRIVING_LICENSE"
    assert expiries[0]["days_remaining"] is None or expiries[0]["days_remaining"] > 0

    # 5. Feature 207: Background verification status
    bg_res = await service.get_background_check_details(user.id)
    assert not bg_res["security_badge_id"]
    assert bg_res["provider_configured"] is False
    assert bg_res["police_clearance_authority"] is None

    # 6. Feature 208: Driver Training Lessons
    curriculum = await service.get_training_curriculum(user.id)
    assert len(curriculum) >= 4
    first_module = curriculum[0]
    assert first_module["module_code"] == "VIP_ETIQUETTE_101"
    assert len(first_module["questions"]) >= 1

    # 7. Feature 209: Driver Knowledge Assessment
    assessment_res = await service.submit_assessment(
        user_id=user.id,
        module_id=first_module["module_id"],
        selected_answers=[1, 1]
    )
    assert assessment_res["passed"] is True
    assert assessment_res["score_percentage"] == 100

    # 8. Feature 210 & 211: Vehicle Inspection Checklist (20-point) & 6-Angle Photo Submission
    template = await service.get_inspection_template()
    assert template["total_items"] == 20
    assert len(template["required_photos"]) == 6

    checklist_dict = {item["code"]: "PASS" for item in template["checklist_items"]}

    category = VehicleCategory(code=f"TEST_{u_suffix}", name="Test vehicle category")
    db_session.add(category)
    await db_session.flush()
    vehicle = Vehicle(category_id=category.id, make="Test", model="Fleet vehicle", license_plate=f"TEST-{u_suffix}", year=2025)
    db_session.add(vehicle)
    await db_session.flush()
    driver_prof = await service.get_or_create_driver(user.id)
    assignment = DriverVehicleAssignment(
        driver_id=driver_prof.id,
        vehicle_id=vehicle.id,
        is_active=True
    )
    db_session.add(assignment)
    await db_session.flush()

    photos_dict = {}
    for p in template["required_photos"]:
        photo_row = DriverVehicleInspectionPhoto(
            driver_id=driver_prof.id,
            vehicle_id=vehicle.id,
            photo_type=p["key"],
            storage_path=f"storage/{p['key']}.jpg",
            mime_type="image/jpeg",
            file_size_bytes=2048,
            file_hash_sha256="abc"
        )
        db_session.add(photo_row)
        await db_session.flush()
        photos_dict[p["key"]] = photo_row.id

    inspection_res = await service.submit_vehicle_inspection(
        user_id=user.id,
        vehicle_id=vehicle.id,
        checklist_results=checklist_dict,
        photos=photos_dict,
        notes="All 20 luxury criteria cleared."
    )
    assert inspection_res["inspection_status"] == "SUBMITTED"
    assert inspection_res["checklist_items_verified"] == 20

    # 9. Feature 212: Multiple Vehicle Management
    vehicles = await service.get_driver_vehicles(user.id)
    assert len(vehicles) >= 1
    v_id = vehicles[0]["vehicle_id"]

    switch_res = await service.switch_active_vehicle(user.id, v_id)
    assert switch_res["status"] == "SWITCHED"
    assert switch_res["active_vehicle_id"] == v_id

    # 10. Feature 213: Driver Availability Calendar
    calendar = await service.get_availability_calendar(user.id)
    assert calendar == []

    calendar_update = [
        {"day_of_week": i, "start_time": "07:30" if i == 0 else "08:00", "end_time": "21:30", "is_active": True, "is_rest_day": i == 6}
        for i in range(7)
    ]
    cal_res = await service.update_availability_calendar(user.id, calendar_update)
    assert cal_res["status"] == "CALENDAR_UPDATED"

    # 11. Feature 214 & 215: Preferred Operating Zones & Vehicle Categories
    zones_res = await service.update_zones_and_categories(
        user_id=user.id,
        zones=["AIRPORT_CORRIDOR", "BKC_FINANCIAL", "SOUTH_MUMBAI"],
        categories=["EXECUTIVE_SEDAN", "ROYAL_SOVEREIGN_LIMOUSINE"]
    )
    assert "AIRPORT_CORRIDOR" in zones_res["preferred_operating_zones"]
    assert "EXECUTIVE_SEDAN" in zones_res["preferred_vehicle_categories"]

    # 12. Feature 218: Language and Service Preferences
    pref_res = await service.update_service_preferences(
        user_id=user.id,
        languages="English, Hindi, Marathi, French",
        etiquette_level="Diplomatic Discreet",
        music="Classical & Soft Jazz",
        temp_celsius=20
    )
    assert pref_res["languages_spoken"] == "English, Hindi, Marathi, French"
    assert pref_res["cabin_temp_celsius"] == 20

    # 13. Feature 219: Emergency Contact Management
    em_res = await service.update_emergency_contact(
        user_id=user.id,
        name="Ananya Varma",
        phone="+91 98200 99881",
        relationship="Spouse"
    )
    assert em_res["emergency_contact_name"] == "Ananya Varma"

    # 14. Feature 217: Driver Public Profile Preview
    public_card = await service.get_public_profile_preview(user.id)
    assert public_card["chauffeur_name"] == "Chauffeur Rajeshwar Varma"
    assert public_card["service_etiquette_level"] == "Diplomatic Discreet"
    assert public_card["compliments_count"] == {}

    # 15. Feature 220: Account Recovery
    rec_init = await service.initiate_account_recovery(user.phone_number)
    assert rec_init["status"] == "RECOVERY_UNAVAILABLE"

    from fastapi import HTTPException
    with pytest.raises(HTTPException) as recovery_error:
        await service.verify_account_recovery(user.phone_number, "123456")
    assert recovery_error.value.status_code == 503

    # 16. Feature 221: Consent & Policy Acknowledgments
    ack_res = await service.acknowledge_policies(user.id)
    assert "policy_acknowledged_at" in ack_res
    assert len(ack_res["policies_covered"]) >= 4

    # 17. Feature 222: Driver Account Deletion Request
    del_res = await service.request_account_deletion(user.id)
    assert del_res["status"] == "DELETION_REQUEST_RECEIVED"
    assert del_res["grace_period_days"] == 30

    # 18. Feature 225: Driver Onboarding Support Ticket
    ticket_res = await service.create_onboarding_ticket(
        user_id=user.id,
        subject="Document Verification Query",
        message="License document submitted with 4-corner guided alignment."
    )
    assert ticket_res["status"] == "OPEN"
    assert ticket_res["ticket_id"] is not None

    tickets_list = await service.get_onboarding_tickets(user.id)
    assert len(tickets_list) >= 1
    assert tickets_list[0]["subject"] == "Document Verification Query"

    # 19. Final Check: Profile completeness updated
    overview_final = await service.get_onboarding_overview(user.id)
    assert overview_final["profile_completeness_percentage"] >= 30
    assert overview_final["identity_verified"] is False
