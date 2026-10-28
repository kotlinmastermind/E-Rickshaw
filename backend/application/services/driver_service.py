from datetime import datetime, timezone
import json
from typing import Dict, Any, List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, func
from fastapi import HTTPException, status, UploadFile
from backend.domain.models import (
    DriverProfile, DriverDocument, DriverAvailability, DriverLocation,
    Vehicle, DriverVehicleAssignment, Ride, Payment
)
from backend.integrations.storage.provider import get_storage_provider

class DriverService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.storage = get_storage_provider()

    async def get_or_create_profile(self, user_id: str) -> DriverProfile:
        stmt = select(DriverProfile).where(DriverProfile.user_id == user_id)
        res = await self.db.execute(stmt)
        driver = res.scalar_one_or_none()
        if not driver:
            driver = DriverProfile(
                user_id=user_id,
                license_number=f"DL-{user_id[:8].upper()}",
                approval_status="PENDING"
            )
            self.db.add(driver)
            await self.db.flush()

            avail = DriverAvailability(driver_id=driver.id, is_online=False, is_busy=False)
            self.db.add(avail)
            await self.db.commit()
            await self.db.refresh(driver)
        return driver

    async def upload_document(self, user_id: str, document_type: str, file: UploadFile) -> Dict[str, Any]:
        driver = await self.get_or_create_profile(user_id)
        path, mime, size, sha256 = await self.storage.save_driver_document(
            driver_id=driver.id,
            document_type=document_type,
            file=file
        )

        doc = DriverDocument(
            driver_id=driver.id,
            document_type=document_type,
            storage_path=path,
            file_name=file.filename or f"{document_type}.pdf",
            mime_type=mime,
            file_size_bytes=size,
            file_hash_sha256=sha256,
            verification_status="PENDING"
        )
        self.db.add(doc)
        await self.db.commit()
        await self.db.refresh(doc)

        return {
            "document_id": doc.id,
            "document_type": doc.document_type,
            "verification_status": doc.verification_status,
            "file_name": doc.file_name,
            "file_size_bytes": doc.file_size_bytes,
            "uploaded_at": doc.created_at.isoformat()
        }

    async def get_documents(self, user_id: str) -> List[Dict[str, Any]]:
        driver = await self.get_or_create_profile(user_id)
        stmt = select(DriverDocument).where(DriverDocument.driver_id == driver.id).order_by(DriverDocument.created_at.desc())
        res = await self.db.execute(stmt)
        docs = res.scalars().all()
        return [
            {
                "id": d.id,
                "document_type": d.document_type,
                "file_name": d.file_name,
                "verification_status": d.verification_status,
                "rejection_reason": d.rejection_reason,
                "created_at": d.created_at.isoformat()
            }
            for d in docs
        ]

    async def toggle_availability(self, user_id: str, is_online: bool) -> Dict[str, Any]:
        driver = await self.get_or_create_profile(user_id)
        if is_online and driver.approval_status != "APPROVED":
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Verification pending. Your documents must be approved before going online."
            )

        now = datetime.now(timezone.utc)
        stmt = (
            update(DriverAvailability)
            .where(DriverAvailability.driver_id == driver.id)
            .values(is_online=is_online, last_heartbeat=now, updated_at=now)
        )
        await self.db.execute(stmt)
        await self.db.commit()

        return {
            "driver_id": driver.id,
            "is_online": is_online,
            "last_heartbeat": now.isoformat()
        }

    async def update_location(self, user_id: str, lat: float, lng: float, heading: Optional[float] = None,
                              speed_mps: Optional[float] = None, accuracy_m: Optional[float] = None,
                              is_mock_location: bool = False, location_source: str = "FUSED") -> Dict[str, Any]:
        driver = await self.get_or_create_profile(user_id)
        now = datetime.now(timezone.utc)
        flags = []
        risk = 0.0
        if is_mock_location:
            flags.append("MOCK_LOCATION_REPORTED")
            risk = max(risk, 0.9)
        if speed_mps is not None and speed_mps > 65:
            flags.append("IMPLAUSIBLE_SPEED")
            risk = max(risk, 0.9)
        if accuracy_m is not None and accuracy_m > 100:
            flags.append("LOW_ACCURACY")
        latest = (await self.db.execute(select(DriverLocation).where(DriverLocation.driver_id == driver.id)
                                        .order_by(DriverLocation.recorded_at.desc()).limit(1))).scalars().first()
        if latest and now > latest.recorded_at:
            elapsed = max(0.1, (now - latest.recorded_at).total_seconds())
            distance = self._location_distance_m(lat, lng, latest.latitude, latest.longitude)
            if distance / elapsed > 75:
                flags.append("IMPLAUSIBLE_DISPLACEMENT")
                risk = max(risk, 0.85)
        loc = DriverLocation(
            driver_id=driver.id,
            latitude=lat,
            longitude=lng,
            heading=heading,
            speed=speed_mps,
            accuracy=accuracy_m,
            spoof_risk_score=risk,
            quality_flags_json=json.dumps(flags),
            source=location_source,
            recorded_at=now
        )
        self.db.add(loc)
        availability = (await self.db.execute(select(DriverAvailability).where(DriverAvailability.driver_id == driver.id))).scalars().first()
        if availability:
            availability.last_heartbeat = now
        await self.db.commit()
        return {"status": "RECORDED", "accuracy_m": accuracy_m, "quality_flags": flags,
                "risk_signal": bool(risk), "risk_action": "REVIEW_SIGNAL_ONLY"}

    @staticmethod
    def _location_distance_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
        import math
        radius = 6371000
        dlat, dlng = math.radians(lat2-lat1), math.radians(lng2-lng1)
        a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1))*math.cos(math.radians(lat2))*math.sin(dlng/2)**2
        return radius * 2 * math.atan2(math.sqrt(a), math.sqrt(max(0, 1-a)))

    async def get_earnings_summary(self, user_id: str) -> Dict[str, Any]:
        driver = await self.get_or_create_profile(user_id)
        stmt = select(Ride).where(Ride.assigned_driver_id == driver.id, Ride.status == "COMPLETED").order_by(Ride.completed_at.desc())
        res = await self.db.execute(stmt)
        rides = res.scalars().all()

        total_earnings_cents = sum(round((r.final_fare_cents or 0) * 0.8) for r in rides)
        trips_count = len(rides)

        now = datetime.now(timezone.utc)
        today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
        today_earnings_cents = sum(
            round((r.final_fare_cents or 0) * 0.8)
            for r in rides
            if r.completed_at and r.completed_at.replace(tzinfo=timezone.utc if r.completed_at.tzinfo is None else None) >= today_start
        )
        weekly_earnings_cents = total_earnings_cents

        recent_trips = []
        for r in rides[:15]:
            recent_trips.append({
                "ride_id": r.id,
                "date": r.completed_at.strftime("%b %d, %I:%M %p") if r.completed_at else "Recently",
                "fare_rupees": round((r.final_fare_cents or 0) * 0.8 / 100),
                "pickup": r.pickup_address,
                "destination": r.destination_address
            })

        return {
            "total_earnings_cents": total_earnings_cents,
            "today_earnings_cents": today_earnings_cents,
            "weekly_earnings_cents": weekly_earnings_cents,
            "currency": "INR",
            "completed_trips": trips_count,
            "wallet_balance_cents": driver.wallet_balance_cents,
            "rating_average": driver.rating_average,
            "rating_count": driver.rating_count,
            "recent_trips": recent_trips
        }

