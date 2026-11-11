from datetime import datetime, timezone
import json
from typing import Dict, Any, List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, func, desc
from fastapi import HTTPException, status, UploadFile
from backend.domain.models import (
    DriverProfile, DriverDocument, DriverAvailability, DriverLocation,
    Vehicle, DriverVehicleAssignment, Ride, Payment, RideLocationHistory
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
        else:
            avail_stmt = select(DriverAvailability).where(DriverAvailability.driver_id == driver.id)
            avail_res = await self.db.execute(avail_stmt)
            if not avail_res.scalar_one_or_none():
                avail = DriverAvailability(driver_id=driver.id, is_online=False, is_busy=False)
                self.db.add(avail)
                await self.db.flush()
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

    async def get_availability(self, user_id: str) -> Dict[str, Any]:
        driver = await self.get_or_create_profile(user_id)
        avail_stmt = select(DriverAvailability).where(DriverAvailability.driver_id == driver.id)
        avail_res = await self.db.execute(avail_stmt)
        avail_obj = avail_res.scalar_one_or_none()

        active_ride_stmt = (
            select(Ride.id)
            .where(
                Ride.assigned_driver_id == driver.id,
                Ride.status.in_(["ASSIGNED", "ARRIVED", "IN_PROGRESS", "PAUSED"])
            )
            .limit(1)
        )
        active_ride_res = await self.db.execute(active_ride_stmt)
        active_ride_id = active_ride_res.scalar_one_or_none()

        return {
            "driver_id": driver.id,
            "is_online": avail_obj.is_online if avail_obj else False,
            "is_busy": avail_obj.is_busy if avail_obj else False,
            "approval_status": driver.approval_status,
            "active_ride_id": active_ride_id,
            "last_heartbeat": avail_obj.last_heartbeat.isoformat() if avail_obj and avail_obj.last_heartbeat else None
        }

    async def toggle_availability(self, user_id: str, is_online: bool) -> Dict[str, Any]:
        driver = await self.get_or_create_profile(user_id)
        if is_online and driver.approval_status not in ["APPROVED", "ACTIVE"]:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Driver must be approved before going online. Mandatory document verification and admin approval required.",
                headers={
                    "X-Error-Code": "DRIVER_NOT_APPROVED",
                    "X-Approval-Status": str(driver.approval_status),
                    "X-Can-Go-Online": "false"
                }
            )

        # Section 9: Validate Ride Safety - Prevent going offline during active rides
        if not is_online:
            active_ride_stmt = (
                select(Ride.id)
                .where(
                    Ride.assigned_driver_id == driver.id,
                    Ride.status.in_(["ASSIGNED", "ARRIVED", "IN_PROGRESS", "PAUSED"])
                )
                .limit(1)
            )
            active_ride_res = await self.db.execute(active_ride_stmt)
            active_ride_id = active_ride_res.scalar_one_or_none()
            if active_ride_id:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail=f"Cannot go offline while on an active ride ({active_ride_id}). Please complete or cancel the active ride first."
                )

        now = datetime.now(timezone.utc)
        avail_stmt = select(DriverAvailability).where(DriverAvailability.driver_id == driver.id)
        avail_res = await self.db.execute(avail_stmt)
        avail_obj = avail_res.scalar_one_or_none()
        if not avail_obj:
            avail_obj = DriverAvailability(driver_id=driver.id, is_online=is_online, is_busy=False, last_heartbeat=now)
            self.db.add(avail_obj)
        else:
            avail_obj.is_online = is_online
            avail_obj.last_heartbeat = now
        await self.db.commit()

        if not is_online:
            try:
                from backend.application.services.live_location_service import LiveLocationService
                await LiveLocationService(self.db).remove_driver_from_h3_index(driver.id)
            except Exception:
                pass

        return {
            "driver_id": driver.id,
            "is_online": is_online,
            "is_busy": avail_obj.is_busy,
            "approval_status": driver.approval_status,
            "active_ride_id": None,
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

        # Record active ride breadcrumb if driver is on trip
        active_ride_stmt = (
            select(Ride)
            .where(
                Ride.assigned_driver_id == driver.id,
                Ride.status.in_(["ASSIGNED", "ARRIVED", "IN_PROGRESS"])
            )
            .order_by(desc(Ride.requested_at))
            .limit(1)
        )
        active_ride = (await self.db.execute(active_ride_stmt)).scalars().first()
        if active_ride:
            crumb = RideLocationHistory(
                ride_id=active_ride.id,
                driver_id=driver.id,
                latitude=lat,
                longitude=lng,
                heading=heading,
                speed_kmh=(speed_mps * 3.6 if speed_mps is not None else None),
                accuracy_meters=accuracy_m,
                recorded_at=now
            )
            self.db.add(crumb)

        availability = (await self.db.execute(select(DriverAvailability).where(DriverAvailability.driver_id == driver.id))).scalars().first()
        if availability:
            availability.last_heartbeat = now
        await self.db.commit()

        try:
            from backend.application.services.live_location_service import LiveLocationService
            is_online = availability.is_online if availability else True
            is_busy = availability.is_busy if availability else False
            await LiveLocationService(self.db).ingest_driver_location(
                driver_id=driver.id,
                latitude=lat,
                longitude=lng,
                heading=heading,
                speed_mps=speed_mps,
                accuracy_m=accuracy_m,
                is_mock_location=is_mock_location,
                location_source=location_source,
                is_online=is_online,
                is_busy=is_busy,
                active_ride_id=active_ride.id if active_ride else None
            )
        except Exception:
            pass

        return {"status": "RECORDED", "accuracy_m": accuracy_m, "quality_flags": flags,
                "risk_signal": bool(risk), "risk_action": "REVIEW_SIGNAL_ONLY"}

    async def update_location_batch(self, user_id: str, locations: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Atomically ingests a batch of offline buffered driver locations."""
        if not locations:
            return {"status": "SUCCESS", "ingested_count": 0}

        driver = await self.get_or_create_profile(user_id)
        now = datetime.now(timezone.utc)
        ingested = 0
        latest_loc = None

        active_ride_stmt = (
            select(Ride)
            .where(
                Ride.assigned_driver_id == driver.id,
                Ride.status.in_(["ASSIGNED", "ARRIVED", "IN_PROGRESS"])
            )
            .order_by(desc(Ride.requested_at))
            .limit(1)
        )
        active_ride = (await self.db.execute(active_ride_stmt)).scalars().first()

        for item in locations:
            lat = item.get("latitude")
            lng = item.get("longitude")
            if lat is None or lng is None:
                continue
            heading = item.get("heading")
            speed = item.get("speed_mps") or item.get("speed")
            acc = item.get("accuracy_m") or item.get("accuracy")
            is_mock = item.get("is_mock_location", False)
            source = item.get("location_source", "FUSED")

            rec_at = item.get("timestamp") or item.get("recorded_at")
            if isinstance(rec_at, (int, float)):
                ts = datetime.fromtimestamp(rec_at if rec_at < 1e11 else rec_at / 1000.0, tz=timezone.utc)
            elif isinstance(rec_at, str):
                try:
                    ts = datetime.fromisoformat(rec_at.replace("Z", "+00:00"))
                except Exception:
                    ts = now
            else:
                ts = now

            loc = DriverLocation(
                driver_id=driver.id,
                latitude=lat,
                longitude=lng,
                heading=heading,
                speed=speed,
                accuracy=acc,
                spoof_risk_score=0.9 if is_mock else 0.0,
                quality_flags_json=json.dumps(["BATCH_OFFLINE_UPLOAD"] + (["MOCK_LOCATION_REPORTED"] if is_mock else [])),
                source=source,
                recorded_at=ts
            )
            self.db.add(loc)
            if active_ride:
                crumb = RideLocationHistory(
                    ride_id=active_ride.id,
                    driver_id=driver.id,
                    latitude=lat,
                    longitude=lng,
                    heading=heading,
                    speed_kmh=(speed * 3.6 if speed is not None else None),
                    accuracy_meters=acc,
                    recorded_at=ts
                )
                self.db.add(crumb)
            ingested += 1
            latest_loc = (lat, lng, heading, speed, acc, is_mock, source)

        availability = (await self.db.execute(select(DriverAvailability).where(DriverAvailability.driver_id == driver.id))).scalars().first()
        if availability:
            availability.last_heartbeat = now
        await self.db.commit()

        if latest_loc:
            try:
                from backend.application.services.live_location_service import LiveLocationService
                is_online = availability.is_online if availability else True
                is_busy = availability.is_busy if availability else False
                await LiveLocationService(self.db).ingest_driver_location(
                    driver_id=driver.id,
                    latitude=latest_loc[0],
                    longitude=latest_loc[1],
                    heading=latest_loc[2],
                    speed_mps=latest_loc[3],
                    accuracy_m=latest_loc[4],
                    is_mock_location=latest_loc[5],
                    location_source=latest_loc[6],
                    is_online=is_online,
                    is_busy=is_busy,
                    active_ride_id=active_ride.id if active_ride else None
                )
            except Exception:
                pass

        return {"status": "SUCCESS", "ingested_count": ingested}

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

