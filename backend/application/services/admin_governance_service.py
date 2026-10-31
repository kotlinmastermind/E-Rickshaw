"""Audited, permission-scoped administrator workflows."""
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

from fastapi import HTTPException
from sqlalchemy import delete, desc, func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from backend.core.config import settings
from backend.domain.models import (
    AdminCalendarEvent, AdminPermission, AdminRecordNote, AdminReviewTemplate,
    AdminRole, AdminRolePermission, AdminUserRole, AdministrativeAuditLog,
    DriverDocument, DriverProfile, FareRule, GeofenceZone, Ride, RideAssignment,
    RideStatusEvent, Session, User, VehicleCategory, GeofenceZoneAudit,
)


class AdminGovernanceService:
    def __init__(self, db: AsyncSession):
        self.db = db

    def audit(self, admin_id: str, action: str, resource: str, target_id: Optional[str], data: Dict[str, Any]) -> None:
        self.db.add(AdministrativeAuditLog(admin_user_id=admin_id, action=action, target_resource=resource,
                                           target_id=target_id, changes_json=json.dumps(data, ensure_ascii=False)))

    async def list_users(self, search: str = "", role: str = "", active: Optional[bool] = None, page: int = 1, limit: int = 50) -> Dict[str, Any]:
        stmt = select(User)
        if role in {"customer", "driver", "admin"}:
            stmt = stmt.where(User.role == role)
        if active is not None:
            stmt = stmt.where(User.is_active == active)
        query = search.strip()
        if query:
            term = f"%{query}%"
            stmt = stmt.where(or_(User.full_name.ilike(term), User.email.ilike(term), User.phone_number.ilike(term), User.id.ilike(term)))
        count = int((await self.db.execute(select(func.count()).select_from(stmt.subquery()))).scalar_one())
        users = (await self.db.execute(stmt.order_by(desc(User.created_at)).offset((page - 1) * limit).limit(limit))).scalars().all()
        return {"users": [{"id": u.id, "full_name": u.full_name, "email": u.email, "phone_number": u.phone_number,
                            "role": u.role, "is_active": u.is_active, "mfa_enabled": u.mfa_enabled,
                            "email_verified": u.email_verified, "created_at": u.created_at.isoformat()} for u in users],
                "page": page, "limit": limit, "total": count}

    async def set_customer_status(self, admin_id: str, user_id: str, is_active: bool, reason: str) -> Dict[str, Any]:
        user = await self.db.get(User, user_id)
        if not user or user.role.lower() != "customer":
            raise HTTPException(404, "Customer account not found.")
        if len(reason.strip()) < 10:
            raise HTTPException(422, "Provide an audit reason of at least 10 characters.")
        user.is_active = is_active
        self.audit(admin_id, "CUSTOMER_REINSTATED" if is_active else "CUSTOMER_SUSPENDED", "users", user.id,
                   {"is_active": is_active, "reason": reason.strip()})
        await self.db.commit()
        return {"user_id": user.id, "is_active": user.is_active, "status": "ACTIVE" if is_active else "SUSPENDED"}

    async def bulk_customer_status(self, admin_id: str, user_ids: List[str], is_active: bool, reason: str) -> Dict[str, Any]:
        if not 1 <= len(set(user_ids)) <= 50 or len(set(user_ids)) != len(user_ids):
            raise HTTPException(422, "Choose 1 to 50 distinct customer records.")
        if len(reason.strip()) < 10:
            raise HTTPException(422, "Provide an audit reason of at least 10 characters.")
        users = (await self.db.execute(select(User).where(User.id.in_(user_ids)).with_for_update())).scalars().all()
        by_id = {u.id: u for u in users}
        changed, skipped = [], []
        for user_id in user_ids:
            user = by_id.get(user_id)
            if not user or user.role.lower() != "customer":
                skipped.append({"user_id": user_id, "reason": "Not a customer account."})
                continue
            user.is_active = is_active
            self.audit(admin_id, "CUSTOMER_BULK_REINSTATED" if is_active else "CUSTOMER_BULK_SUSPENDED", "users", user.id,
                       {"is_active": is_active, "reason": reason.strip(), "bulk": True})
            changed.append(user.id)
        await self.db.commit()
        return {"changed_ids": changed, "skipped": skipped, "changed_count": len(changed), "skipped_count": len(skipped)}

    async def list_drivers(self, approval_status: str = "") -> List[Dict[str, Any]]:
        stmt = select(DriverProfile, User).join(User, User.id == DriverProfile.user_id)
        if approval_status:
            stmt = stmt.where(DriverProfile.approval_status == approval_status.upper())
        rows = (await self.db.execute(stmt.order_by(DriverProfile.created_at.asc()))).all()
        result = []
        for driver, user in rows:
            count = int((await self.db.execute(select(func.count(DriverDocument.id)).where(DriverDocument.driver_id == driver.id))).scalar_one())
            pending = int((await self.db.execute(select(func.count(DriverDocument.id)).where(DriverDocument.driver_id == driver.id, DriverDocument.verification_status == "PENDING"))).scalar_one())
            result.append({"id": driver.id, "user_id": user.id, "full_name": user.full_name, "email": user.email,
                           "license_number": driver.license_number, "approval_status": driver.approval_status,
                           "rating_average": driver.rating_average, "total_trips": driver.total_trips,
                           "documents_count": count, "pending_documents": pending})
        return result

    async def list_driver_documents(self, driver_id: str) -> List[Dict[str, Any]]:
        if not await self.db.get(DriverProfile, driver_id):
            raise HTTPException(404, "Driver record not found.")
        docs = (await self.db.execute(select(DriverDocument).where(DriverDocument.driver_id == driver_id).order_by(desc(DriverDocument.created_at)))).scalars().all()
        return [{"id": d.id, "document_type": d.document_type, "file_name": d.file_name, "mime_type": d.mime_type,
                 "file_size_bytes": d.file_size_bytes, "verification_status": d.verification_status,
                 "rejection_reason": d.rejection_reason, "expires_at": d.expires_at.isoformat() if d.expires_at else None,
                 "uploaded_at": d.created_at.isoformat()} for d in docs]

    async def driver_document_path(self, document_id: str) -> tuple[Path, str, str]:
        document = await self.db.get(DriverDocument, document_id)
        if not document:
            raise HTTPException(404, "Driver document not found.")
        root = Path(settings.STORAGE_LOCAL_DIR).resolve()
        path = Path(document.storage_path).resolve()
        if not path.is_relative_to(root) or not path.is_file():
            raise HTTPException(404, "Private document content is not available from this storage provider.")
        return path, document.mime_type, Path(document.file_name).name

    async def review_document(self, admin_id: str, document_id: str, status: str, reason: str = "") -> Dict[str, Any]:
        doc = await self.db.get(DriverDocument, document_id)
        if not doc:
            raise HTTPException(404, "Driver document not found.")
        status = status.upper()
        if status not in {"VERIFIED", "REJECTED"}:
            raise HTTPException(422, "Document status must be VERIFIED or REJECTED.")
        if status == "REJECTED" and len(reason.strip()) < 8:
            raise HTTPException(422, "A clear rejection reason of at least 8 characters is required.")
        doc.verification_status = status
        doc.rejection_reason = reason.strip() if status == "REJECTED" else None
        doc.reviewed_by = admin_id
        doc.reviewed_at = datetime.now(timezone.utc)
        self.audit(admin_id, "DRIVER_DOCUMENT_REVIEWED", "driver_documents", doc.id,
                   {"status": status, "reason": doc.rejection_reason, "driver_id": doc.driver_id})
        await self.db.commit()
        return {"document_id": doc.id, "verification_status": doc.verification_status, "rejection_reason": doc.rejection_reason}

    async def list_rejection_templates(self) -> List[Dict[str, Any]]:
        rows = (await self.db.execute(select(AdminReviewTemplate).where(AdminReviewTemplate.is_active.is_(True)).order_by(AdminReviewTemplate.title.asc()))).scalars().all()
        return [{"id": r.id, "title": r.title, "reason": r.reason} for r in rows]

    async def create_rejection_template(self, admin_id: str, title: str, reason: str) -> Dict[str, Any]:
        row = AdminReviewTemplate(title=title.strip(), reason=reason.strip(), created_by=admin_id)
        self.db.add(row)
        self.audit(admin_id, "DRIVER_REJECTION_TEMPLATE_CREATED", "admin_review_templates", None, {"title": row.title})
        await self.db.commit()
        await self.db.refresh(row)
        return {"id": row.id, "title": row.title, "reason": row.reason}

    async def list_rides(self, ride_id: str = "", status: str = "", page: int = 1, limit: int = 50) -> Dict[str, Any]:
        stmt = select(Ride)
        if ride_id.strip():
            stmt = stmt.where(Ride.id.ilike(f"%{ride_id.strip()}%"))
        if status:
            stmt = stmt.where(Ride.status == status.upper())
        total = int((await self.db.execute(select(func.count()).select_from(stmt.subquery()))).scalar_one())
        rides = (await self.db.execute(stmt.order_by(desc(Ride.requested_at)).offset((page - 1) * limit).limit(limit))).scalars().all()
        return {"rides": [{"id": r.id, "status": r.status, "customer_id": r.customer_id,
                            "assigned_driver_id": r.assigned_driver_id, "pickup_address": r.pickup_address,
                            "destination_address": r.destination_address, "estimated_fare_cents": r.estimated_fare_cents,
                            "final_fare_cents": r.final_fare_cents, "requested_at": r.requested_at.isoformat()} for r in rides],
                "page": page, "limit": limit, "total": total}

    async def ride_timeline(self, ride_id: str) -> Dict[str, Any]:
        ride = await self.db.get(Ride, ride_id)
        if not ride:
            raise HTTPException(404, "Ride not found.")
        events = (await self.db.execute(select(RideStatusEvent).where(RideStatusEvent.ride_id == ride_id).order_by(RideStatusEvent.created_at.asc()))).scalars().all()
        return {"ride_id": ride.id, "status": ride.status, "pickup_address": ride.pickup_address,
                "destination_address": ride.destination_address, "timeline": [{"id": e.id, "previous_status": e.previous_status,
                    "new_status": e.new_status, "trigger_actor": e.trigger_actor, "notes": e.notes,
                    "metadata": e.metadata_json, "created_at": e.created_at.isoformat()} for e in events]}

    async def assign_ride(self, admin_id: str, ride_id: str, driver_id: str, reason: str) -> Dict[str, Any]:
        if len(reason.strip()) < 10:
            raise HTTPException(422, "Provide an assignment reason of at least 10 characters.")
        ride = (await self.db.execute(select(Ride).where(Ride.id == ride_id).with_for_update())).scalar_one_or_none()
        driver = await self.db.get(DriverProfile, driver_id)
        if not ride:
            raise HTTPException(404, "Ride not found.")
        if ride.status not in {"REQUESTED", "ASSIGNED"}:
            raise HTTPException(409, "Only requested or assigned rides can be manually assigned.")
        if not driver or driver.approval_status != "APPROVED":
            raise HTTPException(409, "The selected driver is not approved for assignment.")
        user = await self.db.get(User, driver.user_id)
        if not user or not user.is_active:
            raise HTTPException(409, "The selected driver account is inactive.")
        previous = ride.status
        ride.assigned_driver_id = driver.id
        ride.status = "ASSIGNED"
        ride.assigned_at = datetime.now(timezone.utc)
        self.db.add(RideAssignment(ride_id=ride.id, driver_id=driver.id, status="ASSIGNED", responded_at=datetime.now(timezone.utc)))
        self.db.add(RideStatusEvent(ride_id=ride.id, previous_status=previous, new_status="ASSIGNED", triggered_by_user_id=admin_id,
                                    trigger_actor="ADMIN", notes=reason.strip(), metadata_json=json.dumps({"driver_id": driver.id, "manual": True})))
        self.audit(admin_id, "RIDE_MANUALLY_ASSIGNED", "rides", ride.id, {"driver_id": driver.id, "reason": reason.strip()})
        await self.db.commit()
        return {"ride_id": ride.id, "assigned_driver_id": driver.id, "status": ride.status}

    async def cancel_ride(self, admin_id: str, ride_id: str, reason: str) -> Dict[str, Any]:
        if len(reason.strip()) < 10:
            raise HTTPException(422, "Provide a cancellation reason of at least 10 characters.")
        ride = (await self.db.execute(select(Ride).where(Ride.id == ride_id).with_for_update())).scalar_one_or_none()
        if not ride:
            raise HTTPException(404, "Ride not found.")
        if ride.status in {"COMPLETED", "CANCELLED", "EXPIRED"}:
            raise HTTPException(409, f"A ride in {ride.status} status cannot be cancelled.")
        previous = ride.status
        ride.status = "CANCELLED"
        ride.cancelled_at = datetime.now(timezone.utc)
        ride.cancellation_reason = reason.strip()
        ride.cancelled_by_role = "admin"
        self.db.add(RideStatusEvent(ride_id=ride.id, previous_status=previous, new_status="CANCELLED", triggered_by_user_id=admin_id,
                                    trigger_actor="ADMIN", notes=reason.strip(), metadata_json=json.dumps({"authorized_admin": True})))
        self.audit(admin_id, "RIDE_CANCELLED_BY_ADMIN", "rides", ride.id, {"previous_status": previous, "reason": reason.strip()})
        await self.db.commit()
        return {"ride_id": ride.id, "status": ride.status}

    async def list_fare_rules(self, include_drafts: bool = False) -> List[Dict[str, Any]]:
        stmt = select(FareRule, VehicleCategory).join(VehicleCategory, FareRule.category_id == VehicleCategory.id)
        if not include_drafts:
            stmt = stmt.where(FareRule.workflow_status != "DRAFT")
        rows = (await self.db.execute(stmt.order_by(VehicleCategory.sort_order.asc(), FareRule.version.desc()))).all()
        return [{"id": f.id, "category_id": f.category_id, "category_name": c.name, "category_code": c.code,
                 "version": f.version, "base_fare_cents": f.base_fare_cents, "per_km_cents": f.per_km_cents,
                 "per_minute_cents": f.per_minute_cents, "minimum_fare_cents": f.minimum_fare_cents,
                 "surge_multiplier": f.surge_multiplier, "workflow_status": f.workflow_status,
                 "is_active": f.is_active, "effective_from": f.effective_from.isoformat() if f.effective_from else None,
                 "approved_by": f.approved_by, "approval_reason": f.approval_reason} for f, c in rows]

    async def create_fare_draft(self, admin_id: str, values: Dict[str, Any]) -> Dict[str, Any]:
        category = await self.db.get(VehicleCategory, values["category_id"])
        if not category:
            raise HTTPException(404, "Vehicle category not found.")
        current = (await self.db.execute(select(func.max(FareRule.version)).where(FareRule.category_id == category.id))).scalar_one() or 0
        rule = FareRule(category_id=category.id, version=current + 1, base_fare_cents=values["base_fare_cents"],
                        per_km_cents=values["per_km_cents"], per_minute_cents=values["per_minute_cents"],
                        minimum_fare_cents=values["minimum_fare_cents"], surge_multiplier=values["surge_multiplier"],
                        fare_cap_cents=values.get("fare_cap_cents"), is_active=False, workflow_status="DRAFT",
                        effective_from=values["effective_from"], created_by=admin_id)
        self.db.add(rule)
        await self.db.flush()
        self.audit(admin_id, "FARE_RULE_DRAFT_CREATED", "fare_rules", rule.id, {"version": rule.version, "category_id": category.id})
        await self.db.commit()
        return {"id": rule.id, "version": rule.version, "workflow_status": rule.workflow_status, "effective_from": rule.effective_from.isoformat()}

    async def approve_fare_draft(self, admin_id: str, rule_id: str, reason: str) -> Dict[str, Any]:
        rule = await self.db.get(FareRule, rule_id)
        if not rule or rule.workflow_status != "DRAFT":
            raise HTTPException(404, "Draft fare rule not found.")
        if len(reason.strip()) < 10:
            raise HTTPException(422, "Provide an approval reason of at least 10 characters.")
        rule.workflow_status, rule.approved_by, rule.approved_at, rule.approval_reason = "APPROVED", admin_id, datetime.now(timezone.utc), reason.strip()
        self.audit(admin_id, "FARE_RULE_APPROVED", "fare_rules", rule.id, {"version": rule.version, "reason": reason.strip()})
        await self.db.commit()
        return {"id": rule.id, "workflow_status": rule.workflow_status, "approved_at": rule.approved_at.isoformat()}

    async def activate_fare_draft(self, admin_id: str, rule_id: str, effective_from: datetime) -> Dict[str, Any]:
        rule = await self.db.get(FareRule, rule_id)
        if not rule or rule.workflow_status != "APPROVED":
            raise HTTPException(409, "Only an approved fare draft can be scheduled.")
        if effective_from.tzinfo is None:
            effective_from = effective_from.replace(tzinfo=timezone.utc)
        now = datetime.now(timezone.utc)
        if effective_from < now:
            raise HTTPException(422, "Activation time must be now or in the future.")
        current = (await self.db.execute(select(FareRule).where(FareRule.category_id == rule.category_id, FareRule.is_active.is_(True), FareRule.workflow_status == "ACTIVE"))).scalars().all()
        for old in current:
            old.effective_until = effective_from
        rule.effective_from = effective_from
        rule.effective_until = None
        rule.is_active = True
        rule.workflow_status = "ACTIVE"
        self.audit(admin_id, "FARE_RULE_ACTIVATION_SCHEDULED", "fare_rules", rule.id,
                   {"effective_from": effective_from.isoformat(), "version": rule.version})
        await self.db.commit()
        return {"id": rule.id, "workflow_status": rule.workflow_status, "is_active": True, "effective_from": effective_from.isoformat(), "scheduled": effective_from > now}

    async def list_categories(self) -> List[Dict[str, Any]]:
        rows = (await self.db.execute(select(VehicleCategory).order_by(VehicleCategory.sort_order, VehicleCategory.name))).scalars().all()
        return [{"id": c.id, "code": c.code, "name": c.name, "description": c.description, "capacity": c.capacity, "is_active": c.is_active, "sort_order": c.sort_order} for c in rows]

    async def save_category(self, admin_id: str, category_id: Optional[str], values: Dict[str, Any]) -> Dict[str, Any]:
        category = await self.db.get(VehicleCategory, category_id) if category_id else None
        if category_id and not category:
            raise HTTPException(404, "Vehicle category not found.")
        if category is None:
            category = VehicleCategory(code=values["code"].strip().upper(), name=values["name"].strip(),
                                       capacity=values["capacity"], description=values["description"].strip(),
                                       sort_order=values["sort_order"], is_active=values["is_active"])
            self.db.add(category)
        else:
            for key in ("name", "capacity", "description", "sort_order", "is_active"):
                setattr(category, key, values[key])
        await self.db.flush()
        self.audit(admin_id, "VEHICLE_CATEGORY_SAVED", "vehicle_categories", category.id, values)
        await self.db.commit()
        return {"id": category.id, "code": category.code, "name": category.name, "capacity": category.capacity, "is_active": category.is_active}

    async def list_notes(self, subject_type: str, subject_id: str) -> List[Dict[str, Any]]:
        kind = subject_type.upper()
        if kind not in {"CUSTOMER", "DRIVER"}:
            raise HTTPException(422, "Notes are only supported for customer and driver records.")
        if kind == "CUSTOMER":
            from backend.domain.models import CustomerProfile
            profile = (await self.db.execute(select(CustomerProfile).where(CustomerProfile.user_id == subject_id))).scalar_one_or_none()
            if not profile: raise HTTPException(404, "Customer record not found.")
            subject_id = profile.id
        rows = (await self.db.execute(select(AdminRecordNote, User).join(User, User.id == AdminRecordNote.author_user_id)
                                     .where(AdminRecordNote.subject_type == subject_type.upper(), AdminRecordNote.subject_id == subject_id)
                                     .order_by(desc(AdminRecordNote.created_at)))).all()
        return [{"id": n.id, "note": n.note, "author": u.full_name, "created_at": n.created_at.isoformat()} for n, u in rows]

    async def add_note(self, admin_id: str, subject_type: str, subject_id: str, note: str) -> Dict[str, Any]:
        kind = subject_type.upper()
        if kind not in {"CUSTOMER", "DRIVER"} or len(note.strip()) < 3 or len(note) > 4000:
            raise HTTPException(422, "Select a customer/driver record and enter a 3 to 4,000 character note.")
        exists = False
        if kind == "CUSTOMER":
            from backend.domain.models import CustomerProfile
            profile = (await self.db.execute(select(CustomerProfile).where(CustomerProfile.user_id == subject_id))).scalar_one_or_none()
            exists = profile is not None
            if profile: subject_id = profile.id
        else:
            exists = await self.db.get(DriverProfile, subject_id) is not None
        if not exists:
            raise HTTPException(404, "Account record not found.")
        row = AdminRecordNote(subject_type=kind, subject_id=subject_id, author_user_id=admin_id, note=note.strip())
        self.db.add(row)
        self.audit(admin_id, "ADMIN_RECORD_NOTE_ADDED", f"{kind.lower()}_records", subject_id, {"note_id": row.id})
        await self.db.commit()
        await self.db.refresh(row)
        return {"id": row.id, "note": row.note, "created_at": row.created_at.isoformat()}

    async def list_calendar(self, start: Optional[datetime], end: Optional[datetime]) -> List[Dict[str, Any]]:
        stmt = select(AdminCalendarEvent).order_by(AdminCalendarEvent.event_date.asc())
        if start: stmt = stmt.where(AdminCalendarEvent.event_date >= start)
        if end: stmt = stmt.where(AdminCalendarEvent.event_date <= end)
        rows = (await self.db.execute(stmt.limit(250))).scalars().all()
        return [{"id": e.id, "title": e.title, "event_date": e.event_date.isoformat(), "event_type": e.event_type,
                 "zone_id": e.zone_id, "notes": e.notes} for e in rows]

    async def create_calendar_event(self, admin_id: str, title: str, event_date: datetime, event_type: str, zone_id: Optional[str], notes: Optional[str]) -> Dict[str, Any]:
        if event_date.tzinfo is None: event_date = event_date.replace(tzinfo=timezone.utc)
        if zone_id and not await self.db.get(GeofenceZone, zone_id):
            raise HTTPException(404, "Service zone not found.")
        row = AdminCalendarEvent(title=title.strip(), event_date=event_date, event_type=event_type, zone_id=zone_id, notes=notes, created_by=admin_id)
        self.db.add(row)
        self.audit(admin_id, "ADMIN_CALENDAR_EVENT_CREATED", "admin_calendar_events", None, {"title": title, "event_date": event_date.isoformat(), "event_type": event_type})
        await self.db.commit()
        await self.db.refresh(row)
        return {"id": row.id, "title": row.title, "event_date": row.event_date.isoformat(), "event_type": row.event_type}

    async def remove_calendar_event(self, admin_id: str, event_id: str) -> Dict[str, Any]:
        row = await self.db.get(AdminCalendarEvent, event_id)
        if not row: raise HTTPException(404, "Calendar entry not found.")
        self.audit(admin_id, "ADMIN_CALENDAR_EVENT_REMOVED", "admin_calendar_events", row.id, {"title": row.title})
        await self.db.delete(row)
        await self.db.commit()
        return {"id": event_id, "removed": True}

    async def list_sessions(self, user_id: str, current_session_id: str) -> List[Dict[str, Any]]:
        rows = (await self.db.execute(select(Session).where(Session.user_id == user_id, Session.is_revoked.is_(False)).order_by(desc(Session.created_at)))).scalars().all()
        return [{"id": s.id, "ip_address": s.ip_address, "user_agent": s.user_agent, "created_at": s.created_at.isoformat(),
                 "expires_at": s.expires_at.isoformat(), "is_current": s.id == current_session_id} for s in rows]

    async def revoke_session(self, admin_id: str, user_id: str, current_session_id: str, session_id: str) -> Dict[str, Any]:
        if session_id == current_session_id:
            raise HTTPException(409, "The current session cannot revoke itself from this action. Use Sign out.")
        row = (await self.db.execute(select(Session).where(Session.id == session_id, Session.user_id == user_id, Session.is_revoked.is_(False)))).scalar_one_or_none()
        if not row: raise HTTPException(404, "Active administrator session not found.")
        row.is_revoked = True
        from backend.infrastructure.cache.redis_client import redis_client
        await redis_client.set(f"revoked_session:{row.id}", "true", ex=86400 * 7)
        self.audit(admin_id, "ADMIN_SESSION_REVOKED", "sessions", row.id, {"target_user_id": user_id})
        await self.db.commit()
        return {"session_id": row.id, "is_revoked": True}

    async def role_permissions(self) -> List[Dict[str, str]]:
        rows = (await self.db.execute(select(AdminPermission).order_by(AdminPermission.category, AdminPermission.permission_key))).scalars().all()
        return [{"key": r.permission_key, "description": r.description, "category": r.category} for r in rows]

    async def list_roles(self) -> List[Dict[str, Any]]:
        roles = (await self.db.execute(select(AdminRole).order_by(AdminRole.name))).scalars().all()
        result = []
        for role in roles:
            keys = (await self.db.execute(select(AdminPermission.permission_key).join(AdminRolePermission, AdminRolePermission.permission_id == AdminPermission.id).where(AdminRolePermission.role_id == role.id))).scalars().all()
            count = int((await self.db.execute(select(func.count(AdminUserRole.id)).where(AdminUserRole.role_id == role.id))).scalar_one())
            result.append({"id": role.id, "name": role.name, "description": role.description, "permissions": list(keys), "assigned_admins": count})
        return result

    async def save_role(self, admin_id: str, role_id: Optional[str], name: str, description: str, permission_keys: List[str]) -> Dict[str, Any]:
        if len(name.strip()) < 3 or len(permission_keys) == 0 or len(set(permission_keys)) != len(permission_keys):
            raise HTTPException(422, "Role name and at least one distinct permission are required.")
        permissions = (await self.db.execute(select(AdminPermission).where(AdminPermission.permission_key.in_(permission_keys)))).scalars().all()
        if len(permissions) != len(permission_keys):
            raise HTTPException(422, "One or more permission keys are invalid.")
        role = await self.db.get(AdminRole, role_id) if role_id else None
        if role_id and (not role or role.is_superuser):
            raise HTTPException(404, "Editable role not found.")
        if not role:
            role = AdminRole(name=name.strip(), description=description.strip(), created_by=admin_id, is_superuser=False)
            self.db.add(role)
            await self.db.flush()
        else:
            role.name, role.description = name.strip(), description.strip()
            await self.db.execute(delete(AdminRolePermission).where(AdminRolePermission.role_id == role.id))
        self.db.add_all([AdminRolePermission(role_id=role.id, permission_id=p.id) for p in permissions])
        self.audit(admin_id, "ADMIN_ROLE_SAVED", "admin_roles", role.id, {"name": role.name, "permissions": permission_keys})
        await self.db.commit()
        return {"id": role.id, "name": role.name, "permissions": permission_keys}

    async def assign_role(self, admin_id: str, user_id: str, role_id: str) -> Dict[str, Any]:
        if user_id == admin_id:
            raise HTTPException(409, "Administrators cannot change their own role assignment.")
        user, role = await self.db.get(User, user_id), await self.db.get(AdminRole, role_id)
        if not user or user.role.lower() != "admin" or not role or role.is_superuser:
            raise HTTPException(404, "Administrator or assignable role not found.")
        existing = (await self.db.execute(select(AdminUserRole).where(AdminUserRole.user_id == user_id))).scalars().all()
        for item in existing: await self.db.delete(item)
        self.db.add(AdminUserRole(user_id=user_id, role_id=role_id, assigned_by=admin_id))
        self.audit(admin_id, "ADMIN_ROLE_ASSIGNED", "users", user_id, {"role_id": role_id, "role_name": role.name})
        await self.db.commit()
        return {"user_id": user_id, "role_id": role_id, "role_name": role.name}

    async def list_geofences(self) -> List[Dict[str, Any]]:
        rows = (await self.db.execute(select(GeofenceZone).order_by(GeofenceZone.name))).scalars().all()
        return [{"id": z.id, "name": z.name, "zone_type": z.zone_type, "coordinates": json.loads(z.coordinates_json),
                 "is_active": z.is_active, "surge_multiplier": z.surge_multiplier} for z in rows]

    @staticmethod
    def _orientation(a: Dict[str, float], b: Dict[str, float], c: Dict[str, float]) -> float:
        return (b["lng"] - a["lng"]) * (c["lat"] - a["lat"]) - (b["lat"] - a["lat"]) * (c["lng"] - a["lng"])

    @classmethod
    def polygons_overlap(cls, a: List[Dict[str, float]], b: List[Dict[str, float]]) -> bool:
        def inside(point: Dict[str, float], polygon: List[Dict[str, float]]) -> bool:
            x, y, hit = point["lng"], point["lat"], False
            for i, p1 in enumerate(polygon):
                p2 = polygon[(i + 1) % len(polygon)]
                if (p1["lat"] > y) != (p2["lat"] > y) and x < (p2["lng"] - p1["lng"]) * (y - p1["lat"]) / (p2["lat"] - p1["lat"] or 1e-12) + p1["lng"]:
                    hit = not hit
            return hit
        if not a or not b: return False
        if any(inside(p, b) for p in a) or any(inside(p, a) for p in b): return True
        for i, a1 in enumerate(a):
            a2 = a[(i + 1) % len(a)]
            for j, b1 in enumerate(b):
                b2 = b[(j + 1) % len(b)]
                o1, o2 = cls._orientation(a1, a2, b1), cls._orientation(a1, a2, b2)
                o3, o4 = cls._orientation(b1, b2, a1), cls._orientation(b1, b2, a2)
                def on_segment(p: Dict[str, float], q: Dict[str, float], r: Dict[str, float]) -> bool:
                    return (min(p["lng"], r["lng"]) - 1e-10 <= q["lng"] <= max(p["lng"], r["lng"]) + 1e-10 and
                            min(p["lat"], r["lat"]) - 1e-10 <= q["lat"] <= max(p["lat"], r["lat"]) + 1e-10)
                intersects = (o1 * o2 < 0 and o3 * o4 < 0) or (
                    abs(o1) <= 1e-10 and on_segment(a1, b1, a2)) or (
                    abs(o2) <= 1e-10 and on_segment(a1, b2, a2)) or (
                    abs(o3) <= 1e-10 and on_segment(b1, a1, b2)) or (
                    abs(o4) <= 1e-10 and on_segment(b1, a2, b2))
                if intersects: return True
        return False

    async def geofence_preview(self, coordinates: List[Dict[str, float]], zone_id: Optional[str] = None) -> List[Dict[str, str]]:
        zones = (await self.db.execute(select(GeofenceZone).where(GeofenceZone.is_active.is_(True)))).scalars().all()
        return [{"id": z.id, "name": z.name, "zone_type": z.zone_type} for z in zones
                if z.id != zone_id and self.polygons_overlap(coordinates, json.loads(z.coordinates_json))]

    async def save_geofence(self, admin_id: str, zone_id: Optional[str], values: Dict[str, Any], commit: bool = True) -> Dict[str, Any]:
        points = values["coordinates"]
        if len(points) < 3 or len(points) > 200:
            raise HTTPException(422, "A service boundary requires 3 to 200 vertices.")
        try:
            points = [{"lat": float(p["lat"]), "lng": float(p["lng"])} for p in points]
        except (KeyError, TypeError, ValueError):
            raise HTTPException(422, "Every boundary vertex must contain numeric latitude and longitude values.")
        if any(not -90 <= p["lat"] <= 90 or not -180 <= p["lng"] <= 180 for p in points) or len({(p["lat"], p["lng"]) for p in points}) < 3:
            raise HTTPException(422, "Boundary vertices must be valid geographic coordinates with at least three distinct points.")
        overlaps = await self.geofence_preview(points, zone_id)
        zone = await self.db.get(GeofenceZone, zone_id) if zone_id else None
        if zone_id and not zone: raise HTTPException(404, "Service zone not found.")
        previous = None if not zone else {"name": zone.name, "zone_type": zone.zone_type, "coordinates": json.loads(zone.coordinates_json), "is_active": zone.is_active}
        if not zone:
            zone = GeofenceZone(name=values["name"].strip(), zone_type=values["zone_type"], coordinates_json=json.dumps(points), surge_multiplier=values["surge_multiplier"], is_active=values["is_active"])
            self.db.add(zone)
        else:
            zone.name, zone.zone_type, zone.coordinates_json = values["name"].strip(), values["zone_type"], json.dumps(points)
            zone.surge_multiplier, zone.is_active = values["surge_multiplier"], values["is_active"]
        await self.db.flush()
        current = {"name": zone.name, "zone_type": zone.zone_type, "coordinates": points, "is_active": zone.is_active}
        action = "CREATED" if previous is None else ("ACTIVATED" if not previous["is_active"] and zone.is_active else "DEACTIVATED" if previous["is_active"] and not zone.is_active else "UPDATED")
        self.db.add(GeofenceZoneAudit(zone_id=zone.id, admin_user_id=admin_id, action=action,
            previous_state_json=json.dumps(previous) if previous else None, new_state_json=json.dumps(current)))
        self.audit(admin_id, "SERVICE_ZONE_SAVED", "geofence_zones", zone.id, {"name": zone.name, "zone_type": zone.zone_type, "overlap_warning_count": len(overlaps)})
        if commit:
            await self.db.commit()
        return {"id": zone.id, "name": zone.name, "zone_type": zone.zone_type, "is_active": zone.is_active, "overlap_warnings": overlaps}

    async def delete_geofence(self, admin_id: str, zone_id: str) -> Dict[str, Any]:
        zone = await self.db.get(GeofenceZone, zone_id)
        if not zone: raise HTTPException(404, "Service zone not found.")
        previous = {"name": zone.name, "zone_type": zone.zone_type, "coordinates": json.loads(zone.coordinates_json), "is_active": zone.is_active}
        zone.is_active = False
        self.db.add(GeofenceZoneAudit(zone_id=zone.id, admin_user_id=admin_id, action="DEACTIVATED",
            previous_state_json=json.dumps(previous), new_state_json=json.dumps({**previous, "is_active": False})))
        self.audit(admin_id, "SERVICE_ZONE_DEACTIVATED", "geofence_zones", zone.id, {"name": zone.name})
        await self.db.commit()
        return {"id": zone_id, "is_active": False}

    async def list_geofence_audits(self, zone_id: Optional[str] = None, limit: int = 100) -> List[Dict[str, Any]]:
        stmt = select(GeofenceZoneAudit, User.full_name, GeofenceZone.name).join(User, User.id == GeofenceZoneAudit.admin_user_id).join(GeofenceZone, GeofenceZone.id == GeofenceZoneAudit.zone_id).order_by(desc(GeofenceZoneAudit.created_at)).limit(min(500, max(1, limit)))
        if zone_id:
            stmt = stmt.where(GeofenceZoneAudit.zone_id == zone_id)
        return [{"id": audit.id, "zone_id": audit.zone_id, "zone_name": name, "admin_name": admin_name,
                 "action": audit.action, "previous_state": json.loads(audit.previous_state_json) if audit.previous_state_json else None,
                 "new_state": json.loads(audit.new_state_json) if audit.new_state_json else None,
                 "created_at": audit.created_at.isoformat()} for audit, admin_name, name in (await self.db.execute(stmt)).all()]
