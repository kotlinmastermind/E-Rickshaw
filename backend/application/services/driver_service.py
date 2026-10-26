from datetime import datetime, timezone
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

    async def update_location(self, user_id: str, lat: float, lng: float, heading: Optional[float] = None) -> Dict[str, Any]:
        driver = await self.get_or_create_profile(user_id)
        loc = DriverLocation(
            driver_id=driver.id,
            latitude=lat,
            longitude=lng,
            heading=heading,
            recorded_at=datetime.now(timezone.utc)
        )
        self.db.add(loc)
        await self.db.commit()
        return {"status": "RECORDED", "lat": lat, "lng": lng}

    async def get_earnings_summary(self, user_id: str) -> Dict[str, Any]:
        driver = await self.get_or_create_profile(user_id)
        stmt = select(Ride).where(Ride.assigned_driver_id == driver.id, Ride.status == "COMPLETED")
        res = await self.db.execute(stmt)
        rides = res.scalars().all()

        total_earnings_cents = sum(round((r.final_fare_cents or 0) * 0.8) for r in rides)
        trips_count = len(rides)

        return {
            "total_earnings_cents": total_earnings_cents,
            "currency": "INR",
            "completed_trips": trips_count,
            "wallet_balance_cents": driver.wallet_balance_cents,
            "rating_average": driver.rating_average,
            "rating_count": driver.rating_count
        }
