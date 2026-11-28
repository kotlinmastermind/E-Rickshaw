"""
Driver Daily Vehicle Inspection, Odometer Verification, Photo Evidence & Admin Review Service.
Implements timezone-aware (Asia/Kolkata) daily inspection enforcement, secure Supabase storage integration,
odometer discrepancy detection, and driver online availability gating.
"""
import os
import json
import logging
from datetime import datetime, date, timezone, timedelta
from typing import Optional, Dict, Any, List, Tuple
from zoneinfo import ZoneInfo

from fastapi import HTTPException, status, UploadFile
from sqlalchemy import select, func, and_, or_, desc
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from backend.domain.models import (
    DriverVehicleInspection,
    DriverVehicleInspectionPhoto,
    DriverProfile,
    Vehicle,
    DriverVehicleAssignment,
    User
)
from backend.integrations.storage.provider import get_storage_provider

logger = logging.getLogger("backend.driver_inspection")

try:
    from zoneinfo import ZoneInfo
    IST_TZ = ZoneInfo("Asia/Kolkata")
except Exception:
    IST_TZ = timezone(timedelta(hours=5, minutes=30), name="IST")

CRITICAL_SAFETY_ITEMS = {
    "brakes": "Service Brake & Parking/Handbrake",
    "steering": "Steering & Handlebar Smoothness",
    "tyres": "Tyre Pressure & Tread Condition",
    "headlights_taillights": "Headlights, Tail Lights & Brake Lights",
    "battery_electrical": "EV Battery SOC & High-Voltage Electrical Isolation"
}

STANDARD_CHECKLIST_ITEMS = {
    "brakes": {"title": "Service Brake & Handbrake", "critical": True, "category": "MECHANICAL"},
    "steering": {"title": "Steering & Handlebar Alignment", "critical": True, "category": "MECHANICAL"},
    "tyres": {"title": "Tyre Pressure & Tread Condition", "critical": True, "category": "MECHANICAL"},
    "headlights_taillights": {"title": "Headlights, Tail Lights & Brake Lights", "critical": True, "category": "ELECTRICAL"},
    "indicators_horn": {"title": "Turn Indicators, Horn & Reverse Buzzer", "critical": False, "category": "ELECTRICAL"},
    "mirrors_windshield": {"title": "Rear-View Mirrors & Windshield Clarity", "critical": False, "category": "BODY"},
    "battery_electrical": {"title": "EV Battery State-of-Charge & Isolation", "critical": True, "category": "ELECTRICAL"},
    "cabin_cleanliness": {"title": "Cabin Cleanliness & Seating Sanitation", "critical": False, "category": "PASSENGER_COMFORT"},
    "emergency_safety": {"title": "First Aid Kit & Emergency Cutout Switch", "critical": False, "category": "SAFETY"}
}

MANDATORY_PHOTO_CATEGORIES = ["ODOMETER", "FRONT", "REAR", "LEFT_SIDE", "RIGHT_SIDE"]
OPTIONAL_PHOTO_CATEGORIES = ["INTERIOR", "DAMAGE", "OTHER"]
ALL_PHOTO_CATEGORIES = MANDATORY_PHOTO_CATEGORIES + OPTIONAL_PHOTO_CATEGORIES


def get_ist_today() -> date:
    """Returns today's calendar date in Asia/Kolkata timezone."""
    return datetime.now(IST_TZ).date()


def get_ist_now() -> datetime:
    """Returns current localized timestamp in Asia/Kolkata timezone."""
    return datetime.now(IST_TZ)


class DriverInspectionService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.storage = get_storage_provider()

    async def _resolve_driver(self, user_id_or_driver_id: str) -> DriverProfile:
        """Resolves DriverProfile by user_id or direct driver_id."""
        stmt = select(DriverProfile).where(
            or_(
                DriverProfile.user_id == user_id_or_driver_id,
                DriverProfile.id == user_id_or_driver_id
            )
        )
        res = await self.db.execute(stmt)
        driver = res.scalar_one_or_none()
        if not driver:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Driver profile not found for identifier '{user_id_or_driver_id}'."
            )
        return driver

    async def _resolve_active_vehicle(self, driver_id: str, requested_vehicle_id: Optional[str] = None) -> Vehicle:
        """Validates vehicle assignment and ownership for driver."""
        if requested_vehicle_id:
            # Check explicit assignment
            stmt = select(Vehicle).where(Vehicle.id == requested_vehicle_id)
            res = await self.db.execute(stmt)
            v = res.scalar_one_or_none()
            if not v:
                raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Specified vehicle not found.")
            return v

        # Find active vehicle assignment
        stmt = (
            select(Vehicle)
            .join(DriverVehicleAssignment, DriverVehicleAssignment.vehicle_id == Vehicle.id)
            .where(
                DriverVehicleAssignment.driver_id == driver_id,
                DriverVehicleAssignment.is_active == True
            )
            .order_by(DriverVehicleAssignment.assigned_at.desc())
            .limit(1)
        )
        res = await self.db.execute(stmt)
        v = res.scalar_one_or_none()
        if not v:
            # Check any assigned vehicle
            fallback_stmt = (
                select(Vehicle)
                .join(DriverVehicleAssignment, DriverVehicleAssignment.vehicle_id == Vehicle.id)
                .where(DriverVehicleAssignment.driver_id == driver_id)
                .order_by(DriverVehicleAssignment.assigned_at.desc())
                .limit(1)
            )
            fallback_res = await self.db.execute(fallback_stmt)
            v = fallback_res.scalar_one_or_none()

        if not v:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="No vehicle assigned to this driver. Vehicle assignment required before inspection.",
                headers={"X-Error-Code": "NO_ASSIGNED_VEHICLE"}
            )
        return v

    async def get_or_create_draft_inspection(self, user_id: str, vehicle_id: Optional[str] = None) -> Dict[str, Any]:
        """Gets today's inspection for driver and vehicle, or initializes a new draft."""
        driver = await self._resolve_driver(user_id)
        vehicle = await self._resolve_active_vehicle(driver.id, vehicle_id)
        today = get_ist_today()

        # Check existing inspection for today
        stmt = (
            select(DriverVehicleInspection)
            .options(selectinload(DriverVehicleInspection.photos))
            .where(
                DriverVehicleInspection.driver_id == driver.id,
                DriverVehicleInspection.vehicle_id == vehicle.id,
                DriverVehicleInspection.inspection_date == today
            )
            .order_by(DriverVehicleInspection.created_at.desc())
            .limit(1)
        )
        res = await self.db.execute(stmt)
        insp = res.scalar_one_or_none()

        if not insp:
            # Query last verified odometer for baseline comparison
            prev_stmt = (
                select(DriverVehicleInspection.odometer_reading)
                .where(
                    DriverVehicleInspection.vehicle_id == vehicle.id,
                    DriverVehicleInspection.odometer_reading.is_not(None),
                    DriverVehicleInspection.inspection_status.in_(["APPROVED", "SUBMITTED", "PENDING_VERIFICATION"])
                )
                .order_by(DriverVehicleInspection.created_at.desc())
                .limit(1)
            )
            prev_res = await self.db.execute(prev_stmt)
            prev_odo = prev_res.scalar_one_or_none() or 0.0

            insp = DriverVehicleInspection(
                driver_id=driver.id,
                vehicle_id=vehicle.id,
                inspection_date=today,
                inspection_status="DRAFT",
                previous_odometer_reading=prev_odo,
                odometer_unit="KM",
                overall_result="PENDING",
                verification_status="PENDING",
                checklist_json=json.dumps({k: "PASS" for k in STANDARD_CHECKLIST_ITEMS.keys()})
            )
            self.db.add(insp)
            await self.db.commit()
            await self.db.refresh(insp)

        return await self._serialize_inspection(insp, vehicle, driver)

    async def get_current_inspection(self, user_id: str, vehicle_id: Optional[str] = None) -> Dict[str, Any]:
        """Gets today's latest inspection state for current driver."""
        driver = await self._resolve_driver(user_id)
        vehicle = await self._resolve_active_vehicle(driver.id, vehicle_id)
        today = get_ist_today()

        stmt = (
            select(DriverVehicleInspection)
            .options(selectinload(DriverVehicleInspection.photos))
            .where(
                DriverVehicleInspection.driver_id == driver.id,
                DriverVehicleInspection.vehicle_id == vehicle.id,
                DriverVehicleInspection.inspection_date == today
            )
            .order_by(DriverVehicleInspection.created_at.desc())
            .limit(1)
        )
        res = await self.db.execute(stmt)
        insp = res.scalar_one_or_none()

        if not insp:
            return {
                "has_inspection": False,
                "driver_id": driver.id,
                "vehicle_id": vehicle.id,
                "vehicle_plate": vehicle.license_plate,
                "inspection_date": today.isoformat(),
                "can_go_online": False,
                "message": "Daily inspection not yet started for today."
            }

        return await self._serialize_inspection(insp, vehicle, driver)

    async def upload_inspection_photo(
        self,
        user_id: str,
        inspection_id: str,
        photo_category: str,
        file: UploadFile
    ) -> Dict[str, Any]:
        """Uploads and persists a real vehicle inspection photograph into private Supabase/local storage."""
        driver = await self._resolve_driver(user_id)
        cat_clean = photo_category.upper().strip()

        if cat_clean not in ALL_PHOTO_CATEGORIES:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Invalid photo category '{photo_category}'. Allowed: {', '.join(ALL_PHOTO_CATEGORIES)}"
            )

        stmt = (
            select(DriverVehicleInspection)
            .options(selectinload(DriverVehicleInspection.photos))
            .where(
                DriverVehicleInspection.id == inspection_id,
                DriverVehicleInspection.driver_id == driver.id
            )
        )
        res = await self.db.execute(stmt)
        insp = res.scalar_one_or_none()
        if not insp:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Inspection record not found or not owned by driver.")

        if insp.inspection_status in ["APPROVED", "REJECTED"]:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Inspection is already {insp.inspection_status} and cannot be modified."
            )

        # Upload photo through secure private storage provider
        storage_path, detected_mime, file_size, sha256_hash = await self.storage.save_inspection_photo(
            driver_id=driver.id,
            vehicle_id=insp.vehicle_id,
            inspection_id=insp.id,
            photo_category=cat_clean,
            file=file
        )

        # Check existing photo in this category for this inspection
        existing_stmt = select(DriverVehicleInspectionPhoto).where(
            DriverVehicleInspectionPhoto.inspection_id == insp.id,
            DriverVehicleInspectionPhoto.photo_category == cat_clean
        )
        existing_res = await self.db.execute(existing_stmt)
        photo_record = existing_res.scalar_one_or_none()

        now = datetime.now(timezone.utc)
        if not photo_record:
            photo_record = DriverVehicleInspectionPhoto(
                inspection_id=insp.id,
                driver_id=driver.id,
                vehicle_id=insp.vehicle_id,
                photo_category=cat_clean,
                photo_type=cat_clean,
                storage_bucket="driver-vehicle-inspections",
                storage_path=storage_path,
                mime_type=detected_mime,
                file_size_bytes=file_size,
                file_hash_sha256=sha256_hash,
                captured_at=now,
                uploaded_at=now,
                upload_status="COMPLETED",
                verification_status="PENDING"
            )
            self.db.add(photo_record)
        else:
            photo_record.storage_path = storage_path
            photo_record.mime_type = detected_mime
            photo_record.file_size_bytes = file_size
            photo_record.file_hash_sha256 = sha256_hash
            photo_record.uploaded_at = now
            photo_record.upload_status = "COMPLETED"

        # Update denormalized photo URLs on inspection record
        if cat_clean == "ODOMETER":
            insp.odometer_photo_url = storage_path
        elif cat_clean == "FRONT":
            insp.photo_front_url = storage_path
        elif cat_clean == "REAR":
            insp.photo_rear_url = storage_path
        elif cat_clean == "LEFT_SIDE":
            insp.photo_left_url = storage_path
        elif cat_clean == "RIGHT_SIDE":
            insp.photo_right_url = storage_path
        elif cat_clean == "INTERIOR":
            insp.photo_interior_front_url = storage_path

        insp.updated_at = now
        await self.db.commit()
        await self.db.refresh(photo_record)

        signed_url = await self.storage.generate_signed_inspection_url(storage_path, expires_in_seconds=900)

        return {
            "photo_id": photo_record.id,
            "inspection_id": insp.id,
            "category": cat_clean,
            "storage_path": storage_path,
            "signed_url": signed_url,
            "mime_type": detected_mime,
            "file_size_bytes": file_size,
            "file_hash_sha256": sha256_hash,
            "uploaded_at": photo_record.uploaded_at.isoformat() if photo_record.uploaded_at else now.isoformat(),
            "message": f"Photograph for {cat_clean} uploaded and verified."
        }

    async def submit_inspection(
        self,
        user_id: str,
        inspection_id: str,
        odometer_reading: float,
        checklist_results: Dict[str, Any],
        driver_remarks: Optional[str] = None
    ) -> Dict[str, Any]:
        """Authoritative backend validation & submission of the daily vehicle inspection."""
        driver = await self._resolve_driver(user_id)
        stmt = (
            select(DriverVehicleInspection)
            .options(selectinload(DriverVehicleInspection.photos))
            .where(
                DriverVehicleInspection.id == inspection_id,
                DriverVehicleInspection.driver_id == driver.id
            )
        )
        res = await self.db.execute(stmt)
        insp = res.scalar_one_or_none()
        if not insp:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Inspection record not found.")

        # 1. Validate Odometer Reading
        if odometer_reading < 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Odometer reading cannot be negative."
            )
        if odometer_reading > 999999.0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Odometer reading exceeds realistic vehicle threshold (999,999 km)."
            )

        prev_odo = insp.previous_odometer_reading or 0.0
        odometer_discrepancy = False
        discrepancy_note = None

        if prev_odo > 0 and odometer_reading < prev_odo:
            odometer_discrepancy = True
            discrepancy_note = f"Reported odometer ({odometer_reading} km) is lower than previous verified reading ({prev_odo} km)."
            logger.warning(f"Inspection {insp.id}: {discrepancy_note}")

        # 2. Validate Required Photo Evidence
        uploaded_photos = {p.photo_category.upper(): p for p in insp.photos if p.upload_status == "COMPLETED"}
        missing_photos = [cat for cat in MANDATORY_PHOTO_CATEGORIES if cat not in uploaded_photos]

        if missing_photos:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Inspection cannot be submitted without all mandatory photographs. Missing: {', '.join(missing_photos)}.",
                headers={"X-Missing-Photos": ",".join(missing_photos)}
            )

        # 3. Validate every checklist item explicitly; missing input is never a PASS.
        if not isinstance(checklist_results, dict):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Every required inspection checklist item must be submitted explicitly."
            )

        failed_items = []
        critical_failures = []
        normalized_checklist = {}
        missing_checklist_items = []

        for key, config in STANDARD_CHECKLIST_ITEMS.items():
            if key not in checklist_results:
                missing_checklist_items.append(key)
                continue
            val = checklist_results[key]
            if isinstance(val, bool):
                status_str = "PASS" if val else "FAIL"
            elif isinstance(val, str):
                status_str = val.upper().strip()
                if status_str not in ["PASS", "FAIL", "NOT_APPLICABLE"]:
                    raise HTTPException(
                        status_code=status.HTTP_400_BAD_REQUEST,
                        detail=f"Invalid inspection result for checklist item {key}."
                    )
            else:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail=f"Invalid inspection result for checklist item {key}."
                )

            normalized_checklist[key] = status_str

            if status_str == "FAIL":
                failed_items.append(key)
                if config["critical"]:
                    critical_failures.append(key)

        if missing_checklist_items:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Inspection checklist is incomplete. Missing: " + ", ".join(missing_checklist_items) + "."
            )

        now = datetime.now(timezone.utc)
        insp.odometer_reading = odometer_reading
        insp.checklist_json = json.dumps(normalized_checklist)
        insp.failed_items_json = json.dumps(failed_items) if failed_items else None
        insp.driver_remarks = driver_remarks
        insp.submitted_at = now
        insp.inspected_at = now
        insp.updated_at = now

        # Determine Final Inspection Status
        if critical_failures:
            insp.overall_result = "FAIL"
            insp.inspection_status = "FAILED"
            insp.verification_status = "REJECTED"
            insp.rejection_reason = f"Critical safety inspection failed on: {', '.join([CRITICAL_SAFETY_ITEMS.get(k, k) for k in critical_failures])}. Vehicle cannot be operated."
            can_go_online = False
            msg = "Inspection FAILED on critical safety items. Vehicle is barred from duty until repairs are verified."
        elif odometer_discrepancy or failed_items:
            insp.overall_result = "FLAGGED"
            insp.inspection_status = "PENDING_VERIFICATION"
            insp.verification_status = "FLAGGED"
            notes = []
            if odometer_discrepancy:
                notes.append(discrepancy_note)
            if failed_items:
                notes.append(f"Non-critical items flagged: {', '.join(failed_items)}")
            insp.rejection_reason = "; ".join(notes)
            can_go_online = True  # Allowed with supervisor alert for minor non-critical
            msg = "Inspection submitted with non-critical warnings. Supervisor review queued."
        else:
            insp.overall_result = "PASS"
            insp.inspection_status = "APPROVED"
            insp.verification_status = "AUTO_APPROVED"
            insp.rejection_reason = None
            can_go_online = True
            msg = "Daily vehicle inspection verified and APPROVED. Driver is eligible to go online."

        await self.db.commit()
        await self.db.refresh(insp)

        # Retrieve Vehicle and Driver for complete payload
        v_stmt = select(Vehicle).where(Vehicle.id == insp.vehicle_id)
        v_res = await self.db.execute(v_stmt)
        vehicle = v_res.scalar_one_or_none()

        return {
            "inspection_id": insp.id,
            "status": insp.inspection_status,
            "overall_result": insp.overall_result,
            "verification_status": insp.verification_status,
            "can_go_online": can_go_online,
            "odometer_reading": insp.odometer_reading,
            "odometer_unit": insp.odometer_unit,
            "photos_count": len(insp.photos),
            "failed_items": failed_items,
            "critical_failures": critical_failures,
            "rejection_reason": insp.rejection_reason,
            "submitted_at": insp.submitted_at.isoformat(),
            "message": msg
        }

    async def verify_inspection_eligibility_for_online(
        self,
        driver_id: str,
        vehicle_id: str
    ) -> Tuple[bool, str, str]:
        """
        Authoritative server-side gate checking if driver has a valid passed inspection for today.
        Returns: (can_go_online: bool, error_code: str, message: str)
        """
        today = get_ist_today()
        stmt = (
            select(DriverVehicleInspection)
            .where(
                DriverVehicleInspection.driver_id == driver_id,
                DriverVehicleInspection.vehicle_id == vehicle_id,
            )
            .order_by(DriverVehicleInspection.created_at.desc())
            .limit(1)
        )
        res = await self.db.execute(stmt)
        insp = res.scalar_one_or_none()

        # Legacy inspection rows may have a null inspection_date while retaining
        # an authoritative inspected_at timestamp. Accept only when that timestamp
        # falls on the current India-local day; an explicitly stale date still fails.
        inspection_day = insp.inspection_date if insp else None
        if insp and inspection_day is None and insp.inspected_at:
            inspected_at = insp.inspected_at
            if inspected_at.tzinfo is None:
                inspected_at = inspected_at.replace(tzinfo=timezone.utc)
            inspection_day = inspected_at.astimezone(ZoneInfo("Asia/Kolkata")).date()
        if not insp or inspection_day != today:
            return (
                False,
                "INSPECTION_REQUIRED",
                "Daily vehicle inspection required before going online. Please capture vehicle photos and log odometer."
            )

        if insp.inspection_status == "DRAFT":
            return (
                False,
                "INSPECTION_INCOMPLETE",
                "Today's vehicle inspection is in DRAFT state. Complete photo uploads and sign off inspection before going online."
            )

        if insp.inspection_status == "FAILED":
            return (
                False,
                "INSPECTION_FAILED",
                f"Vehicle inspection FAILED due to critical safety defects ({insp.failed_items_json}). Vehicle is grounded."
            )

        if insp.inspection_status == "REJECTED":
            return (
                False,
                "INSPECTION_REJECTED",
                f"Inspection was rejected by Admin: {insp.rejection_reason or 'Failed compliance criteria'}."
            )

        if insp.inspection_status in ["APPROVED", "PENDING_VERIFICATION", "SUBMITTED"]:
            return (True, "OK", "Valid daily inspection verified.")

        return (
            False,
            "INSPECTION_INVALID",
            f"Invalid inspection status: {insp.inspection_status}."
        )

    # --- ADMIN REVIEW ENDPOINTS ---
    async def get_admin_inspections(
        self,
        status_filter: Optional[str] = None,
        date_filter: Optional[str] = None,
        driver_id: Optional[str] = None,
        vehicle_id: Optional[str] = None,
        page: int = 1,
        limit: int = 50
    ) -> Dict[str, Any]:
        """Lists vehicle inspections for the Admin Portal."""
        stmt = (
            select(DriverVehicleInspection, DriverProfile, Vehicle, User)
            .join(DriverProfile, DriverProfile.id == DriverVehicleInspection.driver_id)
            .join(Vehicle, Vehicle.id == DriverVehicleInspection.vehicle_id)
            .join(User, User.id == DriverProfile.user_id)
            .options(selectinload(DriverVehicleInspection.photos))
        )

        filters = []
        if status_filter and status_filter.upper() != "ALL":
            filters.append(DriverVehicleInspection.inspection_status == status_filter.upper())
        if date_filter:
            try:
                parsed_date = datetime.strptime(date_filter, "%Y-%m-%d").date()
                filters.append(DriverVehicleInspection.inspection_date == parsed_date)
            except Exception:
                pass
        if driver_id:
            filters.append(DriverVehicleInspection.driver_id == driver_id)
        if vehicle_id:
            filters.append(DriverVehicleInspection.vehicle_id == vehicle_id)

        if filters:
            stmt = stmt.where(and_(*filters))

        count_stmt = select(func.count(DriverVehicleInspection.id))
        if filters:
            count_stmt = count_stmt.where(and_(*filters))
        total_res = await self.db.execute(count_stmt)
        total_count = total_res.scalar() or 0

        offset = (page - 1) * limit
        stmt = stmt.order_by(desc(DriverVehicleInspection.created_at)).offset(offset).limit(limit)
        res = await self.db.execute(stmt)
        rows = res.all()

        items = []
        for insp, driver, vehicle, user in rows:
            items.append({
                "id": insp.id,
                "driver_id": driver.id,
                "driver_name": user.full_name or f"Driver {driver.license_number}",
                "driver_phone": user.phone_number,
                "vehicle_id": vehicle.id,
                "vehicle_plate": vehicle.license_plate,
                "vehicle_model": f"{vehicle.make or ''} {vehicle.model or ''}".strip(),
                "inspection_date": insp.inspection_date.isoformat() if insp.inspection_date else None,
                "inspection_status": insp.inspection_status,
                "overall_result": insp.overall_result,
                "verification_status": insp.verification_status,
                "odometer_reading": insp.odometer_reading,
                "previous_odometer_reading": insp.previous_odometer_reading,
                "odometer_unit": insp.odometer_unit,
                "photos_count": len(insp.photos),
                "failed_items": json.loads(insp.failed_items_json) if insp.failed_items_json else [],
                "submitted_at": insp.submitted_at.isoformat() if insp.submitted_at else None,
                "verified_by": insp.verified_by,
                "rejection_reason": insp.rejection_reason
            })

        return {
            "total": total_count,
            "page": page,
            "limit": limit,
            "inspections": items
        }

    async def get_admin_inspection_stats(self) -> Dict[str, Any]:
        """Provides high-level dashboard metrics for daily inspections."""
        today = get_ist_today()

        total_stmt = select(func.count(DriverVehicleInspection.id))
        today_stmt = select(func.count(DriverVehicleInspection.id)).where(DriverVehicleInspection.inspection_date == today)
        pending_stmt = select(func.count(DriverVehicleInspection.id)).where(DriverVehicleInspection.inspection_status == "PENDING_VERIFICATION")
        approved_stmt = select(func.count(DriverVehicleInspection.id)).where(DriverVehicleInspection.inspection_status == "APPROVED")
        rejected_stmt = select(func.count(DriverVehicleInspection.id)).where(DriverVehicleInspection.inspection_status == "REJECTED")
        failed_stmt = select(func.count(DriverVehicleInspection.id)).where(DriverVehicleInspection.inspection_status == "FAILED")

        total = (await self.db.execute(total_stmt)).scalar() or 0
        today_total = (await self.db.execute(today_stmt)).scalar() or 0
        pending = (await self.db.execute(pending_stmt)).scalar() or 0
        approved = (await self.db.execute(approved_stmt)).scalar() or 0
        rejected = (await self.db.execute(rejected_stmt)).scalar() or 0
        failed = (await self.db.execute(failed_stmt)).scalar() or 0

        return {
            "total_inspections": total,
            "today_inspections": today_total,
            "pending_verification": pending,
            "approved": approved,
            "rejected": rejected,
            "critical_failed": failed,
            "drivers_blocked": failed + rejected
        }

    async def get_admin_inspection_details(self, inspection_id: str) -> Dict[str, Any]:
        """Detailed inspection viewer with photos and checklist items."""
        stmt = (
            select(DriverVehicleInspection, DriverProfile, Vehicle, User)
            .join(DriverProfile, DriverProfile.id == DriverVehicleInspection.driver_id)
            .join(Vehicle, Vehicle.id == DriverVehicleInspection.vehicle_id)
            .join(User, User.id == DriverProfile.user_id)
            .options(selectinload(DriverVehicleInspection.photos))
            .where(DriverVehicleInspection.id == inspection_id)
        )
        res = await self.db.execute(stmt)
        row = res.first()
        if not row:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Inspection record not found.")

        insp, driver, vehicle, user = row
        serialized = await self._serialize_inspection(insp, vehicle, driver, user)
        return serialized

    async def review_inspection_by_admin(
        self,
        inspection_id: str,
        admin_user_id: str,
        action: str,
        reason: Optional[str] = None
    ) -> Dict[str, Any]:
        """Admin approves or rejects an inspection."""
        action_clean = action.upper().strip()
        if action_clean not in ["APPROVE", "REJECT"]:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Action must be 'APPROVE' or 'REJECT'.")

        stmt = (
            select(DriverVehicleInspection)
            .options(selectinload(DriverVehicleInspection.photos))
            .where(DriverVehicleInspection.id == inspection_id)
        )
        res = await self.db.execute(stmt)
        insp = res.scalar_one_or_none()
        if not insp:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Inspection not found.")
        current_inspection_status = (insp.inspection_status or "").upper()
        current_verification_status = (insp.verification_status or "").upper()
        reviewable = (
            current_inspection_status in {"SUBMITTED", "PENDING_VERIFICATION"}
            or (
                current_inspection_status == "APPROVED"
                and current_verification_status in {"AUTO_APPROVED", "PENDING"}
            )
        )
        if not reviewable:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Only a submitted, pending-verification, or system-approved inspection can be reviewed by Admin."
            )

        if action_clean == "APPROVE":
            mandatory_categories = set(MANDATORY_PHOTO_CATEGORIES)
            uploaded_categories = {
                (photo.photo_category or "").upper()
                for photo in insp.photos
                if (photo.upload_status or "").upper() == "COMPLETED"
            }
            daily_inspection_evidence = mandatory_categories.issubset(uploaded_categories)
            onboarding_inspection_evidence = all([
                insp.photo_front_url,
                insp.photo_rear_url,
                insp.photo_left_url,
                insp.photo_right_url,
                insp.photo_interior_front_url,
                insp.photo_interior_rear_url,
            ])
            daily_photo_url_evidence = all([
                insp.odometer_photo_url,
                insp.photo_front_url,
                insp.photo_rear_url,
                insp.photo_left_url,
                insp.photo_right_url,
            ])
            if not insp.checklist_json or not (
                daily_inspection_evidence or onboarding_inspection_evidence or daily_photo_url_evidence
            ):
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Inspection evidence is incomplete; submit the full checklist and all mandatory inspection photographs."
                )

        now = datetime.now(timezone.utc)
        insp.verified_by = admin_user_id
        insp.verified_at = now
        insp.updated_at = now

        if action_clean == "APPROVE":
            insp.inspection_status = "APPROVED"
            insp.verification_status = "VERIFIED"
            insp.rejection_reason = None
            msg = "Inspection approved by administrator."
        else:
            if not reason:
                raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Rejection reason is required.")
            insp.inspection_status = "REJECTED"
            insp.verification_status = "REJECTED"
            insp.rejection_reason = reason
            msg = f"Inspection rejected: {reason}"

        await self.db.commit()
        await self.db.refresh(insp)

        return {
            "inspection_id": insp.id,
            "status": insp.inspection_status,
            "verification_status": insp.verification_status,
            "verified_by": admin_user_id,
            "verified_at": insp.verified_at.isoformat(),
            "message": msg
        }

    async def get_photo_signed_url(self, photo_id: str, current_user_id: str, is_admin: bool = False) -> str:
        """Returns signed URL for an inspection photograph."""
        stmt = select(DriverVehicleInspectionPhoto).where(DriverVehicleInspectionPhoto.id == photo_id)
        res = await self.db.execute(stmt)
        photo = res.scalar_one_or_none()
        if not photo:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Photograph record not found.")

        if not is_admin:
            driver = await self._resolve_driver(current_user_id)
            if photo.driver_id != driver.id:
                raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied: Not your photo.")

        return await self.storage.generate_signed_inspection_url(photo.storage_path, expires_in_seconds=900)

    async def get_photo_bytes(self, photo_id: str, current_user_id: str, is_admin: bool = False) -> Tuple[bytes, str]:
        """Streams binary bytes and MIME type for inspection photo."""
        stmt = select(DriverVehicleInspectionPhoto).where(DriverVehicleInspectionPhoto.id == photo_id)
        res = await self.db.execute(stmt)
        photo = res.scalar_one_or_none()
        if not photo:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Photograph record not found.")

        if not is_admin:
            driver = await self._resolve_driver(current_user_id)
            if photo.driver_id != driver.id:
                raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied: Not your photo.")

        content = await self.storage.get_inspection_photo_bytes(photo.storage_path)
        if not content:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Photograph file content not found in storage.")

        return (content, photo.mime_type or "image/jpeg")

    async def _serialize_inspection(
        self,
        insp: DriverVehicleInspection,
        vehicle: Vehicle,
        driver: DriverProfile,
        user: Optional[User] = None
    ) -> Dict[str, Any]:
        """Helper to build comprehensive inspection response payload."""
        checklist = {}
        if insp.checklist_json:
            try:
                checklist = json.loads(insp.checklist_json)
            except Exception:
                pass

        failed_items = []
        if insp.failed_items_json:
            try:
                failed_items = json.loads(insp.failed_items_json)
            except Exception:
                pass

        photos_list = []
        for p in (insp.photos or []):
            try:
                signed_url = await self.storage.generate_signed_inspection_url(p.storage_path, expires_in_seconds=900)
            except Exception:
                signed_url = None

            photos_list.append({
                "photo_id": p.id,
                "category": p.photo_category,
                "storage_path": p.storage_path,
                "signed_url": signed_url,
                "mime_type": p.mime_type,
                "file_size_bytes": p.file_size_bytes,
                "file_hash_sha256": p.file_hash_sha256,
                "captured_at": p.captured_at.isoformat() if p.captured_at else None,
                "uploaded_at": p.uploaded_at.isoformat() if p.uploaded_at else None,
                "upload_status": p.upload_status
            })

        can_go_online = insp.inspection_status in ["APPROVED", "PENDING_VERIFICATION"]

        return {
            "has_inspection": True,
            "id": insp.id,
            "inspection_id": insp.id,
            "driver_id": driver.id,
            "driver_name": user.full_name if user else None,
            "driver_phone": user.phone_number if user else None,
            "vehicle_id": vehicle.id,
            "vehicle_plate": vehicle.license_plate,
            "vehicle_model": f"{vehicle.make or ''} {vehicle.model or ''}".strip(),
            "inspection_date": insp.inspection_date.isoformat() if insp.inspection_date else None,
            "inspection_status": insp.inspection_status,
            "overall_result": insp.overall_result,
            "verification_status": insp.verification_status,
            "can_go_online": can_go_online,
            "odometer_reading": insp.odometer_reading,
            "previous_odometer_reading": insp.previous_odometer_reading,
            "odometer_unit": insp.odometer_unit,
            "checklist": checklist,
            "standard_checklist_items": STANDARD_CHECKLIST_ITEMS,
            "mandatory_photo_categories": MANDATORY_PHOTO_CATEGORIES,
            "failed_items": failed_items,
            "driver_remarks": insp.driver_remarks,
            "inspector_notes": insp.inspector_notes,
            "rejection_reason": insp.rejection_reason,
            "photos": photos_list,
            "photos_count": len(photos_list),
            "submitted_at": insp.submitted_at.isoformat() if insp.submitted_at else None,
            "inspected_at": insp.inspected_at.isoformat() if insp.inspected_at else None,
            "verified_by": insp.verified_by,
            "verified_at": insp.verified_at.isoformat() if insp.verified_at else None
        }
