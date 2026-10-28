import json
import re
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, func
from fastapi import HTTPException, status, UploadFile

from backend.domain.models import (
    User, DriverProfile, DriverDocument, Vehicle, VehicleCategory, DriverVehicleAssignment,
    DriverTrainingModule, DriverTrainingProgress, DriverVehicleInspection,
    DriverVehicleInspectionPhoto, DriverScheduleAvailability, DriverOnboardingTicket
)
from backend.domain.models.models import utc_now
from backend.integrations.storage.provider import get_storage_provider

DEFAULT_TRAINING_MODULES = [
    {
        "module_code": "VIP_ETIQUETTE_101",
        "title": "Royal Chauffeur Protocol & VIP Etiquette",
        "description": "Master the fine art of executive greeting, discreet cabin posture, and luggage handling.",
        "duration": 15,
        "content": """# Royal Chauffeur Protocol & VIP Etiquette
1. **The Greeting**: Always address passengers by their formal title unless instructed otherwise. Maintain a 3-meter respectful distance upon arrival.
2. **Door Etiquette**: Open and hold passenger doors with right hand, protecting passenger head clearance with left hand.
3. **Cabin Atmosphere**: Verify cabin temperature is calibrated at 21°C. Offer chilled or room-temperature Evian water.
4. **Discretion**: The chauffeur speaks only when addressed, with warmth, brevity, and refined articulation.
5. **Luggage**: Inspect luggage handles, place upright into trunk with suede protective liners.""",
        "questions": [
            {
                "question": "What is the standard cabin ambient temperature for luxury pick-up?",
                "options": ["18°C", "21°C", "25°C", "Air conditioning turned off"],
                "correct_index": 1
            },
            {
                "question": "When should the chauffeur speak to an executive passenger?",
                "options": ["Constantly to entertain them", "Only when addressed by the passenger", "Never under any circumstance", "At every traffic signal"],
                "correct_index": 1
            }
        ]
    },
    {
        "module_code": "DEFENSIVE_SMOOTH_RIDE",
        "title": "Smooth Braking, Cornering & Defensive Navigation",
        "description": "Techniques for zero-inertia braking, progressive throttle control, and smooth cornering.",
        "duration": 20,
        "content": """# Smooth Braking & Defensive Navigation
1. **Zero-Inertia Braking**: Ease off brake pedal pressure just before full stop to eliminate vehicle jerk.
2. **Champagne Glass Rule**: Drive as if an open glass of champagne sits on the rear center console; avoid abrupt steering maneuvers.
3. **Interstate Distance**: Maintain at least 4 seconds follow distance from preceding vehicles.
4. **Pothole Avoidance**: When unavoidable, slow down beforehand, then release brakes while rolling through to let air suspension absorb shock.""",
        "questions": [
            {
                "question": "What is the 'Champagne Glass' driving standard?",
                "options": ["Drinking champagne while waiting", "Driving with smoothness so an open glass would not spill", "Carrying alcoholic beverages for VIPs", "Driving at top track speed"],
                "correct_index": 1
            },
            {
                "question": "How do you achieve Zero-Inertia braking at stops?",
                "options": ["Slamming brake quickly", "Easing brake pedal pressure right before coming to rest", "Using the handbrake exclusively", "Shifting into Neutral immediately"],
                "correct_index": 1
            }
        ]
    },
    {
        "module_code": "PRIVACY_DISCRETION",
        "title": "VIP Non-Disclosure & In-Cabin Privacy",
        "description": "Confidentiality rules, phone call etiquette, and camera/recording policies.",
        "duration": 15,
        "content": """# Confidentiality & VIP Privacy
1. **Non-Disclosure**: Any conversation overheard inside the cabin is strictly privileged and legally confidential under your Chauffeur NDA.
2. **Rearview Mirror Etiquette**: Position rearview mirror to monitor traffic; never make direct prolonged eye contact with passengers through mirror.
3. **Personal Devices**: Driver personal phone must remain on silent mode in the cockpit dock. No personal calls while passenger is onboard.
4. **Luggage & Items**: Never photograph or inspect passenger property.""",
        "questions": [
            {
                "question": "What should the chauffeur do if a passenger conducts a sensitive corporate phone call?",
                "options": ["Listen and take notes", "Maintain absolute discretion and never repeat or disclose overheard details", "Turn music volume to maximum", "Ask the passenger to hang up"],
                "correct_index": 1
            }
        ]
    },
    {
        "module_code": "VEHICLE_AMENITIES_HYGIENE",
        "title": "White-Glove Sanitization & Interior Prep",
        "description": "Perfection in Nappa leather care, fragrance calibration, and amenity replenishment.",
        "duration": 10,
        "content": """# White-Glove Sanitization & Luxury Amenities
1. **Pre-Trip Checklist**: Clean all windows inside and out. Ensure no fingerprints on gloss wood or chrome.
2. **Amenities**: Replenish sealed Evian glass bottles, clean microfiber tissue box, and certified USB-C / Lightning luxury braided cables.
3. **Fragrance**: Use only subtle natural amber or cedarwood organic diffusers; never use pungent chemical aerosol fresheners.
4. **Boot**: Trunk must be 100% empty of personal items, vacuumed and ready for high-end designer luggage.""",
        "questions": [
            {
                "question": "Which items must be available in the rear passenger console?",
                "options": ["Only business cards", "Sealed mineral water, tissue box, and braided multi-device charging cables", "Fast food snacks", "Driver personal items"],
                "correct_index": 1
            }
        ]
    },
    {
        "module_code": "DEFENSIVE_DRIVING_SAFETY",
        "title": "Defensive Driving & Roadside Safety",
        "description": "Practical hazard anticipation, safe following distance, fatigue response, and incident scene safety.",
        "duration": 12,
        "content": """# Defensive Driving & Roadside Safety
1. Scan well ahead and keep a safe following distance; increase it in rain, low visibility, and dense traffic.
2. Slow before turns and hazards. Never use a phone while the vehicle is moving.
3. If fatigue affects focus or reaction time, stop at a safe, legal place and rest before continuing.
4. At a breakdown or collision, protect people first, move clear of traffic if safe, and call 112 for immediate danger.
5. Never enter a flooded road or pass a closure barrier. Follow official traffic and weather directions.""",
        "questions": [
            {"question": "What should you do when fatigue affects your attention?", "options": ["Continue slowly", "Stop safely and rest before driving", "Open a window and keep going", "Accept no passengers"], "correct_index": 1},
            {"question": "When should you call emergency services?", "options": ["For any navigation delay", "For immediate danger or urgent injury", "Only after the trip", "Never"], "correct_index": 1}
        ]
    }
]

VEHICLE_20_POINT_ITEMS = [
    {"code": "EXT_PAINT", "label": "Exterior Paint & Bodywork (Dent/Scratch Free)", "category": "EXTERIOR"},
    {"code": "TIRES_RIMS", "label": "Tires Condition & Alloy Rim Gloss (>4mm Tread)", "category": "EXTERIOR"},
    {"code": "WINDSHIELD_WIPERS", "label": "Windshield, Windows & Wipers (Crystal Clear)", "category": "EXTERIOR"},
    {"code": "LIGHTS_INDICATORS", "label": "LED Headlights, Taillights & Indicator Arrays", "category": "EXTERIOR"},
    {"code": "DOORS_HANDLES", "label": "Door Latches, Soft-Close Mechanisms & Child Locks", "category": "EXTERIOR"},
    {"code": "SUSPENSION_AIR", "label": "Air Suspension Balance & Smooth Damping", "category": "MECHANICAL"},
    {"code": "BRAKES_RESPONSIVE", "label": "Braking System Responsiveness & ABS Self-Check", "category": "MECHANICAL"},
    {"code": "ENGINE_MOTOR", "label": "Powertrain / Electric Motor Diagnostics (No Warning Lights)", "category": "MECHANICAL"},
    {"code": "BATTERY_HEALTH", "label": "Auxiliary Battery & High-Voltage EV Pack (>80% Capacity)", "category": "MECHANICAL"},
    {"code": "NAPPA_LEATHER", "label": "Nappa Leather Seats Condition (Tear/Stain Free)", "category": "INTERIOR"},
    {"code": "CABIN_HYGIENE", "label": "Cabin Sanitization & Odor-Free Ambience", "category": "INTERIOR"},
    {"code": "CLIMATE_DUAL_ZONE", "label": "Dual/Tri-Zone Automatic Climate Control & HEPA Filter", "category": "INTERIOR"},
    {"code": "REAR_CONSOLE", "label": "Rear Passenger Seat Recline & Console Controls", "category": "INTERIOR"},
    {"code": "CHARGING_PORTS", "label": "Fast Charging USB-C / Wireless Qi Charging Pads", "category": "INTERIOR"},
    {"code": "WIFI_HOTSPOT", "label": "Onboard Wi-Fi 6 Router Connectivity", "category": "AMENITIES"},
    {"code": "WATER_AMENITY", "label": "Sealed Bottled Mineral Water & Tissue Dispenser", "category": "AMENITIES"},
    {"code": "TRUNK_SPACE", "label": "Luggage Trunk Empty, Lined & Vacuumed", "category": "AMENITIES"},
    {"code": "FIRST_AID_KIT", "label": "Certified Medical First-Aid Kit with Trauma Bandages", "category": "SAFETY"},
    {"code": "FIRE_EXTINGUISHER", "label": "ABC Fire Extinguisher (Pressure Gauge in Green Zone)", "category": "SAFETY"},
    {"code": "SPARE_TIRE_JACK", "label": "Spare Tire / Run-Flat Mobility Sealant & Hazard Triangle", "category": "SAFETY"}
]


class DriverOnboardingService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.storage = get_storage_provider()

    async def get_or_create_driver(self, user_id: str) -> DriverProfile:
        stmt = select(DriverProfile).where(DriverProfile.user_id == user_id)
        res = await self.db.execute(stmt)
        driver = res.scalar_one_or_none()
        if not driver:
            driver = DriverProfile(
                user_id=user_id,
                license_number=f"DL-{user_id[:8].upper()}",
                approval_status="PENDING",
                onboarding_step="IDENTITY_SUBMITTED",
                profile_completeness_percentage=10,
                preferred_operating_zones="AIRPORT_CORRIDOR,SOUTH_MUMBAI,BKC_FINANCIAL",
                preferred_vehicle_categories="EXECUTIVE_SEDAN,PRESTIGE_SUV",
                languages_spoken="English, Hindi, Marathi",
                service_etiquette_level="Black Sovereign Executive",
                cabin_music_preference="Classical & Soft Jazz",
                cabin_temp_celsius=21
            )
            self.db.add(driver)
            await self.db.flush()
        return driver

    # =========================================================================
    # 201 & 216 & 224: Checklist, Completeness, Status Tracker
    # =========================================================================
    async def get_onboarding_overview(self, user_id: str) -> Dict[str, Any]:
        driver = await self.get_or_create_driver(user_id)
        user_res = await self.db.execute(select(User).where(User.id == user_id))
        user = user_res.scalar_one_or_none()

        # Documents check
        docs_res = await self.db.execute(select(DriverDocument).where(DriverDocument.driver_id == driver.id))
        docs = docs_res.scalars().all()
        doc_types = {d.document_type: d for d in docs}

        has_license = "DRIVING_LICENSE" in doc_types and doc_types["DRIVING_LICENSE"].verification_status == "APPROVED"
        has_rc = "VEHICLE_REGISTRATION" in doc_types and doc_types["VEHICLE_REGISTRATION"].verification_status == "APPROVED"
        has_insurance = "INSURANCE" in doc_types and doc_types["INSURANCE"].verification_status == "APPROVED"
        has_id = "IDENTITY_PROOF" in doc_types and doc_types["IDENTITY_PROOF"].verification_status == "APPROVED"
        all_docs_verified = has_license and has_rc and has_insurance and has_id

        # Training progress
        t_res = await self.db.execute(
            select(func.count()).select_from(DriverTrainingProgress)
            .where(DriverTrainingProgress.driver_id == driver.id, DriverTrainingProgress.assessment_passed == True)
        )
        passed_modules = t_res.scalar() or 0
        total_modules_needed = len(DEFAULT_TRAINING_MODULES)
        training_done = passed_modules >= total_modules_needed

        # Vehicle inspection
        i_res = await self.db.execute(
            select(DriverVehicleInspection)
            .where(DriverVehicleInspection.driver_id == driver.id)
            .order_by(DriverVehicleInspection.inspected_at.desc())
        )
        latest_inspection = i_res.scalars().first()
        inspection_done = latest_inspection is not None and latest_inspection.inspection_status == "PASSED"

        # Emergency contact & preferences
        has_emergency_contact = bool(driver.emergency_contact_name and driver.emergency_contact_phone)
        policy_acknowledged = driver.policy_acknowledged_at is not None

        # Calculate completeness percentage
        completeness = 10  # Base profile
        if driver.identity_verified or has_id:
            completeness += 20
        if all_docs_verified:
            completeness += 20
        elif len(docs) > 0:
            completeness += min(20, len(docs) * 7)
        # Background verification is provider-dependent and is never credited
        # from legacy/default status labels alone.
        if training_done:
            completeness += 15
        elif passed_modules > 0:
            completeness += min(15, round((passed_modules / max(1, total_modules_needed)) * 15))
        if inspection_done:
            completeness += 10
        if has_emergency_contact:
            completeness += 5
        if policy_acknowledged:
            completeness += 5

        driver.profile_completeness_percentage = min(100, completeness)

        # Onboarding stage state machine
        if driver.approval_status == "APPROVED":
            current_step = "ACTIVE"
        elif inspection_done and training_done and all_docs_verified and policy_acknowledged:
            current_step = "APPROVED"
        elif inspection_done:
            current_step = "FINAL_REVIEW"
        elif training_done:
            current_step = "INSPECTION_PENDING"
        elif all_docs_verified:
            current_step = "TRAINING_PENDING"
        elif len(docs) > 0:
            current_step = "DOCUMENTS_UNDER_AUDIT"
        else:
            current_step = "IDENTITY_SUBMITTED"

        driver.onboarding_step = current_step
        await self.db.commit()

        stages = [
            {
                "id": "STAGE_IDENTITY",
                "title": "Government Identity & Aadhaar",
                "description": "Biometric verification & photo ID confirmation",
                "status": "COMPLETED" if driver.identity_verified else "IN_PROGRESS",
                "is_mandatory": True
            },
            {
                "id": "STAGE_DOCUMENTS",
                "title": "Commercial Fleet Documents",
                "description": "Driving license, RC badge, commercial taxi insurance",
                "status": "COMPLETED" if all_docs_verified else ("IN_REVIEW" if len(docs) > 0 else "PENDING"),
                "is_mandatory": True
            },
            {
                "id": "STAGE_BACKGROUND",
                "title": "Security & Police Background Clearance",
                "description": "Criminal record clearance and Master Chauffeur accreditation",
                "status": "BLOCKED" if driver.background_check_status in ["VERIFIED", "VERIFIED_POLICE_CLEARED"] else "IN_PROGRESS",
                "is_mandatory": True
            },
            {
                "id": "STAGE_TRAINING",
                "title": "Royal Chauffeur Academy",
                "description": f"VIP etiquette & smooth navigation curriculum ({passed_modules}/{total_modules_needed} completed)",
                "status": "COMPLETED" if training_done else ("IN_PROGRESS" if passed_modules > 0 else "PENDING"),
                "is_mandatory": True
            },
            {
                "id": "STAGE_INSPECTION",
                "title": "20-Point Vehicle Inspection & 6 Photos",
                "description": "Mechanical audit, Nappa interior, hygiene & exterior photos",
                "status": "COMPLETED" if inspection_done else "PENDING",
                "is_mandatory": True
            },
            {
                "id": "STAGE_PREFERENCES",
                "title": "Operating Preferences & Emergency Contacts",
                "description": "Zones, vehicle classes, emergency contact & policy acknowledgement",
                "status": "COMPLETED" if (has_emergency_contact and policy_acknowledged) else "IN_PROGRESS",
                "is_mandatory": True
            }
        ]

        return {
            "driver_id": driver.id,
            "full_name": user.full_name if user else "Executive Chauffeur",
            "phone_number": user.phone_number if user else "",
            "preferred_operating_zones": [value for value in driver.preferred_operating_zones.split(",") if value],
            "preferred_vehicle_categories": [value for value in driver.preferred_vehicle_categories.split(",") if value],
            "languages_spoken": driver.languages_spoken,
            "service_etiquette_level": driver.service_etiquette_level,
            "cabin_music_preference": driver.cabin_music_preference,
            "cabin_temp_celsius": driver.cabin_temp_celsius,
            "emergency_contact_name": driver.emergency_contact_name,
            "emergency_contact_phone": driver.emergency_contact_phone,
            "emergency_contact_relationship": driver.emergency_contact_relationship,
            "approval_status": driver.approval_status,
            "onboarding_step": driver.onboarding_step,
            "profile_completeness_percentage": driver.profile_completeness_percentage,
            "security_badge_id": driver.security_badge_id,
            "background_check_status": driver.background_check_status,
            "identity_verified": driver.identity_verified,
            "identity_reverification_due": driver.identity_reverification_due,
            "last_identity_verified_at": driver.last_identity_verified_at.isoformat() if driver.last_identity_verified_at else None,
            "policy_acknowledged_at": driver.policy_acknowledged_at.isoformat() if driver.policy_acknowledged_at else None,
            "account_deletion_requested": driver.account_deletion_requested,
            "checklist_stages": stages
        }

    # =========================================================================
    # 202 & 223: Identity Verification & Re-verification
    # =========================================================================
    async def get_identity_status(self, user_id: str) -> Dict[str, Any]:
        driver = await self.get_or_create_driver(user_id)
        return {
            "driver_id": driver.id,
            "identity_verified": driver.identity_verified,
            "identity_reverification_due": driver.identity_reverification_due,
            "last_verified_at": driver.last_identity_verified_at.isoformat() if driver.last_identity_verified_at else None,
            "security_badge_id": driver.security_badge_id,
            "license_number": driver.license_number,
            "status": "VERIFIED" if driver.identity_verified else ("REVERIFICATION_REQUIRED" if driver.identity_reverification_due else "PENDING")
        }

    async def submit_identity_verification(
        self, user_id: str, id_type: str, id_number: str, selfie_notes: Optional[str] = None
    ) -> Dict[str, Any]:
        if id_type not in {"AADHAAR", "PASSPORT", "DRIVING_LICENSE", "VOTER_ID"}:
            raise HTTPException(status_code=400, detail="Unsupported identity document type.")
        driver = await self.get_or_create_driver(user_id)
        now = utc_now()
        # Identity checks require an approved verification provider. Never mark a
        # submission verified merely because the driver supplied an ID number.
        await self.db.commit()

        return {
            "status": "PENDING_PROVIDER",
            "id_type": id_type,
            "id_number_masked": f"***{id_number[-4:]}" if len(id_number) >= 4 else "***",
            "verified_at": None,
            "provider_configured": False,
            "message": "Identity details received. Verification is pending an approved identity provider review.",
            "badge_id": driver.security_badge_id
        }

    # =========================================================================
    # 203: Document Expiry Reminders
    # =========================================================================
    async def get_onboarding_documents(self, user_id: str) -> List[Dict[str, Any]]:
        driver = await self.get_or_create_driver(user_id)
        result = await self.db.execute(select(DriverDocument).where(DriverDocument.driver_id == driver.id).order_by(DriverDocument.created_at.desc()))
        return [{"id": doc.id, "document_type": doc.document_type, "file_name": doc.file_name,
                 "verification_status": doc.verification_status, "rejection_reason": doc.rejection_reason,
                 "image_quality_score": doc.image_quality_score, "quality_assessment_notes": doc.quality_assessment_notes,
                 "resubmission_count": doc.resubmission_count, "expires_at": doc.expires_at.isoformat() if doc.expires_at else None}
                for doc in result.scalars().all()]

    async def get_document_expiry_reminders(self, user_id: str) -> List[Dict[str, Any]]:
        driver = await self.get_or_create_driver(user_id)
        res = await self.db.execute(select(DriverDocument).where(DriverDocument.driver_id == driver.id))
        docs = res.scalars().all()

        now = utc_now()
        results = []
        for doc in docs:
            if doc.expires_at is None:
                results.append({"document_id": doc.id, "document_type": doc.document_type, "file_name": doc.file_name, "expires_at": None, "days_remaining": None, "status": "EXPIRY_DATE_NOT_RECORDED", "reminder_sent": False, "requires_renewal": False})
                continue
            expires_at = doc.expires_at
            if expires_at.tzinfo is None:
                expires_at = expires_at.replace(tzinfo=timezone.utc)
            days_left = (expires_at - now).days

            status_str = "VALID"
            if days_left < 0:
                status_str = "EXPIRED"
            elif days_left <= 30:
                status_str = "EXPIRING_SOON"

            results.append({
                "document_id": doc.id,
                "document_type": doc.document_type,
                "file_name": doc.file_name,
                "expires_at": expires_at.isoformat(),
                "days_remaining": max(0, days_left),
                "status": status_str,
                "reminder_sent": False,
                "reminder_due": status_str in ["EXPIRED", "EXPIRING_SOON"],
                "delivery_channel": "IN_APP_STATUS",
                "requires_renewal": status_str in ["EXPIRED", "EXPIRING_SOON"]
            })

        await self.db.commit()
        return results

    # =========================================================================
    # 204, 205, 206: Document Resubmission & Image Quality Checks
    # =========================================================================
    async def resubmit_document(
        self, user_id: str, document_type: str, file: UploadFile,
        guidance_confirmed: bool = True
    ) -> Dict[str, Any]:
        if document_type not in {"DRIVING_LICENSE", "VEHICLE_REGISTRATION", "INSURANCE", "IDENTITY_PROOF"}:
            raise HTTPException(status_code=400, detail="Unsupported driver document type.")
        driver = await self.get_or_create_driver(user_id)

        # Automated Quality Checks (Feature 206)
        content_bytes = await file.read()
        file_size = len(content_bytes)
        await file.seek(0)

        if not guidance_confirmed:
            raise HTTPException(status_code=400, detail="Confirm the document capture guidance before upload.")
        if file_size < 16:
            raise HTTPException(status_code=400, detail="The selected file is empty or unreadable.")
        # No OCR/blur analysis provider is configured. Report that honestly.
        quality_score = 0.0
        quality_notes = "Automated image quality analysis is unavailable; a reviewer will assess this document."

        # Save to storage
        path, mime, size, sha256 = await self.storage.save_driver_document(
            driver_id=driver.id,
            document_type=document_type,
            file=file
        )

        # Check existing document for resubmission count
        stmt = select(DriverDocument).where(
            DriverDocument.driver_id == driver.id,
            DriverDocument.document_type == document_type
        )
        res = await self.db.execute(stmt)
        existing = res.scalar_one_or_none()

        if existing:
            existing.storage_path = path
            existing.file_name = file.filename or f"{document_type}.jpg"
            existing.mime_type = mime
            existing.file_size_bytes = size
            existing.file_hash_sha256 = sha256
            existing.verification_status = "PENDING"
            existing.rejection_reason = None
            existing.image_quality_score = quality_score
            existing.quality_assessment_notes = quality_notes
            existing.resubmission_count += 1
            existing.updated_at = utc_now()
            doc_obj = existing
        else:
            doc_obj = DriverDocument(
                driver_id=driver.id,
                document_type=document_type,
                storage_path=path,
                file_name=file.filename or f"{document_type}.jpg",
                mime_type=mime,
                file_size_bytes=size,
                file_hash_sha256=sha256,
                verification_status="PENDING",
                image_quality_score=quality_score,
                quality_assessment_notes=quality_notes,
                resubmission_count=1
            )
            self.db.add(doc_obj)

        await self.db.commit()
        await self.db.refresh(doc_obj)

        return {
            "document_id": doc_obj.id,
            "document_type": doc_obj.document_type,
            "file_name": doc_obj.file_name,
            "verification_status": doc_obj.verification_status,
            "image_quality_score": doc_obj.image_quality_score,
            "quality_assessment_notes": doc_obj.quality_assessment_notes,
            "resubmission_count": doc_obj.resubmission_count,
            "guidance_applied": guidance_confirmed,
            "message": "Document received and queued for manual review. Automated image quality analysis is unavailable."
        }

    # =========================================================================
    # 207: Background Verification Status
    # =========================================================================
    async def get_background_check_details(self, user_id: str) -> Dict[str, Any]:
        driver = await self.get_or_create_driver(user_id)
        return {
            "driver_id": driver.id,
            "security_badge_id": driver.security_badge_id,
            "background_check_status": "PROVIDER_NOT_CONFIGURED" if driver.background_check_status in ["VERIFIED", "VERIFIED_POLICE_CLEARED"] else driver.background_check_status,
            "background_check_date": driver.background_check_date.isoformat() if driver.background_check_date else None,
            "provider_configured": False,
            "academy_certified": False,
            "academy_level": driver.academy_level,
            "police_clearance_authority": None,
            "clearance_reference_number": None,
            "message": "Background verification is not connected to an external screening provider. No clearance is claimed."
        }

    # =========================================================================
    # 208 & 209: Driver Training Lessons & Knowledge Assessment
    # =========================================================================
    async def seed_training_modules_if_needed(self):
        existing = (await self.db.execute(select(DriverTrainingModule.module_code))).scalars().all()
        existing_codes = set(existing)
        added = False
        for idx, item in enumerate(DEFAULT_TRAINING_MODULES):
            if item["module_code"] not in existing_codes:
                mod = DriverTrainingModule(
                    module_code=item["module_code"],
                    title=item["title"],
                    description=item["description"],
                    content_markdown=item["content"],
                    estimated_duration_minutes=item["duration"],
                    sort_order=idx + 1,
                    is_mandatory=True
                )
                self.db.add(mod)
                added = True
        if added:
            await self.db.commit()

    async def get_training_curriculum(self, user_id: str) -> List[Dict[str, Any]]:
        await self.seed_training_modules_if_needed()
        driver = await self.get_or_create_driver(user_id)

        modules_res = await self.db.execute(select(DriverTrainingModule).order_by(DriverTrainingModule.sort_order.asc()))
        modules = modules_res.scalars().all()

        prog_res = await self.db.execute(
            select(DriverTrainingProgress).where(DriverTrainingProgress.driver_id == driver.id)
        )
        progress_map = {p.module_id: p for p in prog_res.scalars().all()}

        curriculum = []
        for mod in modules:
            p = progress_map.get(mod.id)
            # Find quiz questions
            matched_spec = next((m for m in DEFAULT_TRAINING_MODULES if m["module_code"] == mod.module_code), None)
            questions = matched_spec["questions"] if matched_spec else []

            curriculum.append({
                "module_id": mod.id,
                "module_code": mod.module_code,
                "title": mod.title,
                "description": mod.description,
                "content_markdown": mod.content_markdown,
                "duration_minutes": mod.estimated_duration_minutes,
                "is_completed": p.is_completed if p else False,
                "assessment_score": p.assessment_score_percentage if p else None,
                "assessment_passed": p.assessment_passed if p else False,
                "questions": questions
            })
        return curriculum

    async def submit_assessment(
        self, user_id: str, module_id: str, selected_answers: List[int]
    ) -> Dict[str, Any]:
        driver = await self.get_or_create_driver(user_id)
        mod_res = await self.db.execute(select(DriverTrainingModule).where(DriverTrainingModule.id == module_id))
        mod = mod_res.scalar_one_or_none()
        if not mod:
            raise HTTPException(status_code=404, detail="Training module not found.")

        matched_spec = next((m for m in DEFAULT_TRAINING_MODULES if m["module_code"] == mod.module_code), None)
        questions = matched_spec["questions"] if matched_spec else []

        correct_count = 0
        total_questions = len(questions)
        for i, q in enumerate(questions):
            if i < len(selected_answers) and selected_answers[i] == q["correct_index"]:
                correct_count += 1

        score_percent = round((correct_count / max(1, total_questions)) * 100)
        passed = score_percent >= 75

        # Record progress
        prog_res = await self.db.execute(
            select(DriverTrainingProgress).where(
                DriverTrainingProgress.driver_id == driver.id,
                DriverTrainingProgress.module_id == module_id
            )
        )
        progress = prog_res.scalar_one_or_none()
        now = utc_now()
        if not progress:
            progress = DriverTrainingProgress(
                driver_id=driver.id,
                module_id=module_id,
                is_completed=True,
                assessment_score_percentage=score_percent,
                assessment_passed=passed,
                completed_at=now if passed else None
            )
            self.db.add(progress)
        else:
            progress.is_completed = True
            progress.assessment_score_percentage = score_percent
            progress.assessment_passed = passed
            if passed:
                progress.completed_at = now

        await self.db.commit()

        return {
            "module_id": module_id,
            "module_code": mod.module_code,
            "score_percentage": score_percent,
            "passed": passed,
            "passing_threshold": 75,
            "message": "Assessment passed! Academy certified in module." if passed else "Score below threshold. Please review the material and retry."
        }

    # =========================================================================
    # 210 & 211: Vehicle Inspection Checklist & 6-Angle Photo Submission
    # =========================================================================
    async def get_inspection_template(self) -> Dict[str, Any]:
        return {
            "total_items": len(VEHICLE_20_POINT_ITEMS),
            "checklist_items": VEHICLE_20_POINT_ITEMS,
            "required_photos": [
                {"key": "photo_front_url", "label": "Vehicle Front Exterior"},
                {"key": "photo_rear_url", "label": "Vehicle Rear Exterior with License Plate"},
                {"key": "photo_left_url", "label": "Driver Side Exterior Profile"},
                {"key": "photo_right_url", "label": "Passenger Side Exterior Profile"},
                {"key": "photo_interior_front_url", "label": "Cockpit & Instrument Cluster"},
                {"key": "photo_interior_rear_url", "label": "Rear VIP Cabin & Nappa Leather Seating"}
            ]
        }

    async def upload_vehicle_inspection_photo(self, user_id: str, vehicle_id: str, photo_type: str, file: UploadFile) -> Dict[str, Any]:
        driver = await self.get_or_create_driver(user_id)
        allowed = {"photo_front_url", "photo_rear_url", "photo_left_url", "photo_right_url", "photo_interior_front_url", "photo_interior_rear_url"}
        if photo_type not in allowed:
            raise HTTPException(status_code=400, detail="Unsupported inspection photo angle.")
        assigned = await self.db.execute(select(DriverVehicleAssignment).where(DriverVehicleAssignment.driver_id == driver.id, DriverVehicleAssignment.vehicle_id == vehicle_id))
        if assigned.scalar_one_or_none() is None:
            raise HTTPException(status_code=404, detail="Vehicle is not assigned to this driver.")
        vehicle = await self.db.get(Vehicle, vehicle_id)
        if vehicle is None:
            raise HTTPException(status_code=404, detail="Vehicle was not found.")
        header = await file.read(12)
        if not (header.startswith(b"\xff\xd8\xff") or header.startswith(b"\x89PNG\r\n\x1a\n") or (header.startswith(b"RIFF") and header[8:12] == b"WEBP")):
            raise HTTPException(status_code=400, detail="Vehicle inspection photos must be JPEG, PNG, or WebP images.")
        await file.seek(0)
        path, mime, size, digest = await self.storage.save_driver_document(driver.id, f"vehicle-{vehicle_id[:8]}-{photo_type}", file)
        if mime == "application/pdf":
            raise HTTPException(status_code=400, detail="Vehicle inspection photos must be images.")
        photo = DriverVehicleInspectionPhoto(driver_id=driver.id, vehicle_id=vehicle_id, photo_type=photo_type, storage_path=path, mime_type=mime, file_size_bytes=size, file_hash_sha256=digest)
        self.db.add(photo)
        await self.db.commit()
        await self.db.refresh(photo)
        return {"photo_id": photo.id, "photo_type": photo.photo_type, "vehicle_id": vehicle_id, "status": "UPLOADED_PRIVATE", "message": "Photo stored privately and linked to your assigned vehicle."}

    async def submit_vehicle_inspection(
        self, user_id: str, vehicle_id: str, checklist_results: Dict[str, str], photos: Dict[str, str], notes: Optional[str] = None
    ) -> Dict[str, Any]:
        driver = await self.get_or_create_driver(user_id)

        # Only inspect vehicles assigned to this authenticated driver.
        assignment_res = await self.db.execute(select(DriverVehicleAssignment).where(
            DriverVehicleAssignment.driver_id == driver.id,
            DriverVehicleAssignment.vehicle_id == vehicle_id
        ))
        if assignment_res.scalar_one_or_none() is None:
            raise HTTPException(status_code=404, detail="Vehicle is not assigned to this driver.")
        veh_res = await self.db.execute(select(Vehicle).where(Vehicle.id == vehicle_id))
        vehicle = veh_res.scalar_one_or_none()
        if not vehicle:
            raise HTTPException(status_code=404, detail="Assigned vehicle was not found.")

        required_codes = {item["code"] for item in VEHICLE_20_POINT_ITEMS}
        required_photos = {"photo_front_url", "photo_rear_url", "photo_left_url", "photo_right_url", "photo_interior_front_url", "photo_interior_rear_url"}
        if set(checklist_results) != required_codes or any(value not in {"PASS", "FAIL", "NOT_APPLICABLE"} for value in checklist_results.values()):
            raise HTTPException(status_code=400, detail="Submit a result for each inspection checklist item.")
        photo_result = await self.db.execute(select(DriverVehicleInspectionPhoto).where(DriverVehicleInspectionPhoto.driver_id == driver.id, DriverVehicleInspectionPhoto.vehicle_id == vehicle_id, DriverVehicleInspectionPhoto.id.in_([photos.get(key, "") for key in required_photos])))
        photo_records = {photo.id: photo for photo in photo_result.scalars().all()}
        if len(photo_records) != len(required_photos) or any(not photos.get(key) or photos[key] not in photo_records for key in required_photos):
            raise HTTPException(status_code=400, detail="Upload all six inspection photos for this vehicle before submitting.")
        photo_paths = {photo.photo_type: f"private://{photo.id}" for photo in photo_records.values()}

        all_passed = all(val == "PASS" for val in checklist_results.values())
        # Driver-entered inspection data is a submission, never an approval.
        inspection_status = "SUBMITTED"

        inspection = DriverVehicleInspection(
            driver_id=driver.id,
            vehicle_id=vehicle.id,
            inspection_status=inspection_status,
            checklist_json=json.dumps(checklist_results),
            photo_front_url=photo_paths["photo_front_url"],
            photo_rear_url=photo_paths["photo_rear_url"],
            photo_left_url=photo_paths["photo_left_url"],
            photo_right_url=photo_paths["photo_right_url"],
            photo_interior_front_url=photo_paths["photo_interior_front_url"],
            photo_interior_rear_url=photo_paths["photo_interior_rear_url"],
            inspector_notes=((notes + "\n") if notes else "") + ("Driver-submitted inspection; fleet review required." if all_passed else "Driver-submitted findings include items needing fleet review."),
            inspected_at=utc_now()
        )
        self.db.add(inspection)
        await self.db.commit()
        await self.db.refresh(inspection)

        return {
            "inspection_id": inspection.id,
            "vehicle_id": vehicle.id,
            "vehicle_model": vehicle.model,
            "inspection_status": inspection.inspection_status,
            "checklist_items_verified": len(checklist_results),
            "photos_submitted": len(photos),
            "inspected_at": inspection.inspected_at.isoformat()
        }

    # =========================================================================
    # 212: Multiple Vehicle Management
    # =========================================================================
    async def get_driver_vehicles(self, user_id: str) -> List[Dict[str, Any]]:
        driver = await self.get_or_create_driver(user_id)
        stmt = (
            select(Vehicle, DriverVehicleAssignment)
            .join(DriverVehicleAssignment, DriverVehicleAssignment.vehicle_id == Vehicle.id)
            .where(DriverVehicleAssignment.driver_id == driver.id)
        )
        res = await self.db.execute(stmt)
        rows = res.all()

        results = []
        for v, a in rows:
            results.append({
                "vehicle_id": v.id,
                "model": f"{v.make} {v.model}",
                "license_plate": v.license_plate,
                "color": v.color,
                "year": v.year,
                "rc_number": v.rc_number,
                "permit_number": v.permit_number,
                "is_active_vehicle": a.is_active,
                "is_active": v.is_active
            })
        return results

    async def switch_active_vehicle(self, user_id: str, vehicle_id: str) -> Dict[str, Any]:
        driver = await self.get_or_create_driver(user_id)
        owned = await self.db.execute(select(DriverVehicleAssignment).where(
            DriverVehicleAssignment.driver_id == driver.id,
            DriverVehicleAssignment.vehicle_id == vehicle_id
        ))
        if owned.scalar_one_or_none() is None:
            raise HTTPException(status_code=404, detail="Vehicle is not assigned to this driver.")
        # Set all inactive
        await self.db.execute(
            update(DriverVehicleAssignment)
            .where(DriverVehicleAssignment.driver_id == driver.id)
            .values(is_active=False)
        )
        # Set chosen vehicle active
        await self.db.execute(
            update(DriverVehicleAssignment)
            .where(DriverVehicleAssignment.driver_id == driver.id, DriverVehicleAssignment.vehicle_id == vehicle_id)
            .values(is_active=True)
        )
        await self.db.commit()
        return {"status": "SWITCHED", "active_vehicle_id": vehicle_id}

    # =========================================================================
    # 213: Driver Availability Calendar
    # =========================================================================
    async def get_availability_calendar(self, user_id: str) -> List[Dict[str, Any]]:
        driver = await self.get_or_create_driver(user_id)
        stmt = (
            select(DriverScheduleAvailability)
            .where(DriverScheduleAvailability.driver_id == driver.id)
            .order_by(DriverScheduleAvailability.day_of_week.asc())
        )
        res = await self.db.execute(stmt)
        schedules = res.scalars().all()

        days_name = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
        if not schedules:
            return []

        return [
            {
                "id": s.id,
                "day_of_week": s.day_of_week,
                "day_name": days_name[s.day_of_week],
                "start_time": s.start_time,
                "end_time": s.end_time,
                "is_active": s.is_active,
                "is_rest_day": s.is_rest_day
            }
            for s in schedules
        ]

    async def update_availability_calendar(
        self, user_id: str, schedules_data: List[Dict[str, Any]]
    ) -> Dict[str, Any]:
        driver = await self.get_or_create_driver(user_id)
        if len(schedules_data) != 7 or {item.get("day_of_week") for item in schedules_data} != set(range(7)):
            raise HTTPException(status_code=400, detail="Provide exactly one schedule for each day of the week.")
        for item in schedules_data:
            day = item.get("day_of_week")
            start = item.get("start_time", "08:00")
            end = item.get("end_time", "20:00")
            is_active = bool(item.get("is_active", True))
            is_rest = bool(item.get("is_rest_day", not is_active))
            if not isinstance(start, str) or not isinstance(end, str) or not all(re.fullmatch(r"(?:[01]\d|2[0-3]):[0-5]\d", t) for t in (start, end)):
                raise HTTPException(status_code=400, detail="Schedule times must use 24-hour HH:MM format.")
            stmt = select(DriverScheduleAvailability).where(
                DriverScheduleAvailability.driver_id == driver.id,
                DriverScheduleAvailability.day_of_week == day
            )
            res = await self.db.execute(stmt)
            s = res.scalar_one_or_none()
            if s:
                s.start_time = start
                s.end_time = end
                s.is_active = is_active and not is_rest
                s.is_rest_day = is_rest
            else:
                self.db.add(DriverScheduleAvailability(driver_id=driver.id, day_of_week=day, start_time=start, end_time=end, is_active=is_active and not is_rest, is_rest_day=is_rest))
        await self.db.commit()
        return {"status": "CALENDAR_UPDATED", "message": "Weekly availability schedule saved."}

    # =========================================================================
    # 214, 215, 218: Zones, Categories & Service Preferences
    # =========================================================================
    async def update_zones_and_categories(
        self, user_id: str, zones: List[str], categories: List[str]
    ) -> Dict[str, Any]:
        if not (1 <= len(zones) <= 10 and 1 <= len(categories) <= 10) or any(not re.fullmatch(r"[A-Z0-9_]{2,40}", item) for item in zones + categories):
            raise HTTPException(status_code=400, detail="Zones and vehicle categories must be short uppercase codes.")
        driver = await self.get_or_create_driver(user_id)
        driver.preferred_operating_zones = ",".join(zones)
        driver.preferred_vehicle_categories = ",".join(categories)
        await self.db.commit()
        return {
            "preferred_operating_zones": zones,
            "preferred_vehicle_categories": categories
        }

    async def update_service_preferences(
        self, user_id: str, languages: str, etiquette_level: str, music: str, temp_celsius: int
    ) -> Dict[str, Any]:
        if not languages.strip() or not etiquette_level.strip() or not (16 <= temp_celsius <= 28):
            raise HTTPException(status_code=400, detail="Provide languages, a service style, and a temperature from 16 to 28°C.")
        driver = await self.get_or_create_driver(user_id)
        driver.languages_spoken = languages
        driver.service_etiquette_level = etiquette_level
        driver.cabin_music_preference = music
        driver.cabin_temp_celsius = temp_celsius
        await self.db.commit()
        return {
            "languages_spoken": driver.languages_spoken,
            "service_etiquette_level": driver.service_etiquette_level,
            "cabin_music_preference": driver.cabin_music_preference,
            "cabin_temp_celsius": driver.cabin_temp_celsius
        }

    # =========================================================================
    # 217: Driver Public Profile Preview
    # =========================================================================
    async def get_public_profile_preview(self, user_id: str) -> Dict[str, Any]:
        driver = await self.get_or_create_driver(user_id)
        user_res = await self.db.execute(select(User).where(User.id == user_id))
        user = user_res.scalar_one_or_none()

        # Active vehicle
        veh_res = await self.db.execute(
            select(Vehicle)
            .join(DriverVehicleAssignment, DriverVehicleAssignment.vehicle_id == Vehicle.id)
            .where(DriverVehicleAssignment.driver_id == driver.id, DriverVehicleAssignment.is_active == True)
        )
        active_veh = veh_res.scalars().first()

        return {
            "chauffeur_name": user.full_name if user else "",
            "security_badge_id": driver.security_badge_id,
            "academy_level": driver.academy_level,
            "rating_average": driver.rating_average,
            "total_trips": driver.total_trips,
            "years_experience": None,
            "languages_spoken": driver.languages_spoken.split(", ") if driver.languages_spoken else ["English", "Hindi"],
            "service_etiquette_level": driver.service_etiquette_level,
            "cabin_music_preference": driver.cabin_music_preference,
            "cabin_temp_celsius": driver.cabin_temp_celsius,
            "active_vehicle": {
                "model": f"{active_veh.make} {active_veh.model}" if active_veh else None,
                "license_plate": active_veh.license_plate if active_veh else None,
                "color": active_veh.color if active_veh else None
            },
            "compliments_count": {}
        }

    # =========================================================================
    # 219: Emergency Contact Management
    # =========================================================================
    async def update_emergency_contact(
        self, user_id: str, name: str, phone: str, relationship: str
    ) -> Dict[str, Any]:
        phone = re.sub(r"[\s-]", "", phone)
        if not re.fullmatch(r"\+?[1-9]\d{7,14}", phone):
            raise HTTPException(status_code=400, detail="Enter a valid international phone number.")
        driver = await self.get_or_create_driver(user_id)
        driver.emergency_contact_name = name
        driver.emergency_contact_phone = phone
        driver.emergency_contact_relationship = relationship
        await self.db.commit()
        return {
            "emergency_contact_name": name,
            "emergency_contact_phone": phone,
            "emergency_contact_relationship": relationship,
            "message": "Emergency contact saved for SOS dispatch."
        }

    # =========================================================================
    # 220: Driver Account Recovery
    # =========================================================================
    async def initiate_account_recovery(self, phone_number: str) -> Dict[str, Any]:
        return {"status": "RECOVERY_UNAVAILABLE", "message": "Use the sign-in OTP recovery flow. No recovery code was sent by this endpoint."}

    async def verify_account_recovery(self, phone_number: str, recovery_code: str) -> Dict[str, Any]:
        # Recovery must use the configured authentication/OTP provider. This
        # endpoint intentionally does not accept guessed or fixed development codes.
        raise HTTPException(status_code=503, detail="Account recovery verification is unavailable until the OTP provider is configured.")

    # =========================================================================
    # 221: Consent & Policy Acknowledgments
    # =========================================================================
    async def acknowledge_policies(self, user_id: str) -> Dict[str, Any]:
        driver = await self.get_or_create_driver(user_id)
        now = utc_now()
        driver.policy_acknowledged_at = now
        await self.db.commit()
        return {
            "policy_acknowledged_at": now.isoformat(),
            "policies_covered": [
                "Zero-Tolerance Alcohol & Substance Policy",
                "VIP Non-Disclosure & Privacy Agreement",
                "High-Value Fleet Care & Safe Driving Protocol",
                "Customer Harassment Zero-Tolerance Standard"
            ],
            "message": "Legal acknowledgments securely logged with digital audit trail."
        }

    # =========================================================================
    # 222: Driver Account Deletion Request
    # =========================================================================
    async def request_account_deletion(self, user_id: str) -> Dict[str, Any]:
        driver = await self.get_or_create_driver(user_id)
        now = utc_now()
        driver.account_deletion_requested = True
        driver.account_deletion_requested_at = now
        await self.db.commit()
        return {
            "status": "DELETION_REQUEST_RECEIVED",
            "account_deletion_requested_at": now.isoformat(),
            "grace_period_days": 30,
            "warning": "This request is subject to payout reconciliation and applicable data retention requirements."
        }

    # =========================================================================
    # 225: Driver Onboarding Support Ticket
    # =========================================================================
    async def create_onboarding_ticket(self, user_id: str, subject: str, message: str) -> Dict[str, Any]:
        driver = await self.get_or_create_driver(user_id)
        ticket = DriverOnboardingTicket(
            driver_id=driver.id,
            subject=subject,
            message=message,
            status="OPEN"
        )
        self.db.add(ticket)
        await self.db.commit()
        await self.db.refresh(ticket)
        return {
            "ticket_id": ticket.id,
            "subject": ticket.subject,
            "message": ticket.message,
            "status": ticket.status,
            "created_at": ticket.created_at.isoformat()
        }

    async def get_onboarding_tickets(self, user_id: str) -> List[Dict[str, Any]]:
        driver = await self.get_or_create_driver(user_id)
        stmt = (
            select(DriverOnboardingTicket)
            .where(DriverOnboardingTicket.driver_id == driver.id)
            .order_by(DriverOnboardingTicket.created_at.desc())
        )
        res = await self.db.execute(stmt)
        tickets = res.scalars().all()
        return [
            {
                "ticket_id": t.id,
                "subject": t.subject,
                "message": t.message,
                "status": t.status,
                "admin_response": t.admin_response,
                "created_at": t.created_at.isoformat(),
                "resolved_at": t.resolved_at.isoformat() if t.resolved_at else None
            }
            for t in tickets
        ]

    async def get_admin_onboarding_tickets(self) -> List[Dict[str, Any]]:
        result = await self.db.execute(
            select(DriverOnboardingTicket, DriverProfile, User)
            .join(DriverProfile, DriverOnboardingTicket.driver_id == DriverProfile.id)
            .join(User, DriverProfile.user_id == User.id)
            .order_by(DriverOnboardingTicket.created_at.desc())
            .limit(100)
        )
        return [{"ticket_id": ticket.id, "driver_id": driver.id, "driver_name": user.full_name,
                 "phone_masked": f"••••{user.phone_number[-4:]}", "subject": ticket.subject,
                 "message": ticket.message, "status": ticket.status, "admin_response": ticket.admin_response,
                 "created_at": ticket.created_at.isoformat(), "resolved_at": ticket.resolved_at.isoformat() if ticket.resolved_at else None}
                for ticket, driver, user in result.all()]

    async def review_onboarding_ticket(self, ticket_id: str, ticket_status: str, admin_response: str) -> Dict[str, Any]:
        ticket = await self.db.get(DriverOnboardingTicket, ticket_id)
        if ticket is None:
            raise HTTPException(status_code=404, detail="Onboarding support ticket not found.")
        ticket.status = ticket_status
        ticket.admin_response = admin_response.strip()
        ticket.resolved_at = utc_now() if ticket_status == "RESOLVED" else None
        await self.db.commit()
        return {"ticket_id": ticket.id, "status": ticket.status, "admin_response": ticket.admin_response,
                "resolved_at": ticket.resolved_at.isoformat() if ticket.resolved_at else None}
