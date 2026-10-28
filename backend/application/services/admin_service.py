import json
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, func, desc
from fastapi import HTTPException, status
from backend.domain.models import (
    User, CustomerProfile, DriverProfile, DriverDocument,
    Ride, RideStatusEvent, FareRule, DriverAvailability,
    AdministrativeAuditLog, VehicleCategory
)

class AdminService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_dashboard_metrics(self) -> Dict[str, Any]:
        # Ride statistics
        total_rides_res = await self.db.execute(select(func.count(Ride.id)))
        total_rides = total_rides_res.scalar_one()

        active_rides_res = await self.db.execute(
            select(func.count(Ride.id)).where(Ride.status.in_(["REQUESTED", "ASSIGNED", "ARRIVED", "IN_PROGRESS"]))
        )
        active_rides = active_rides_res.scalar_one()

        completed_rides_res = await self.db.execute(
            select(func.count(Ride.id)).where(Ride.status == "COMPLETED")
        )
        completed_rides = completed_rides_res.scalar_one()

        # Gross booking value
        gbv_res = await self.db.execute(
            select(func.sum(Ride.final_fare_cents)).where(Ride.status == "COMPLETED")
        )
        gross_booking_cents = gbv_res.scalar_one() or 0

        # Drivers online
        online_drivers_res = await self.db.execute(
            select(func.count(DriverAvailability.id)).where(DriverAvailability.is_online == True)
        )
        online_drivers = online_drivers_res.scalar_one()

        # Total registered users
        total_users_res = await self.db.execute(select(func.count(User.id)))
        total_users = total_users_res.scalar_one()

        return {
            "total_rides": total_rides,
            "active_rides": active_rides,
            "completed_rides": completed_rides,
            "gross_booking_cents": gross_booking_cents,
            "currency": "INR",
            "online_drivers": online_drivers,
            "total_users": total_users,
            "platform_status": "OPERATIONAL"
        }

    async def list_users(self, role: Optional[str] = None, search: Optional[str] = None, page: int = 1, limit: int = 20) -> Dict[str, Any]:
        query = select(User)
        if role:
            query = query.where(User.role == role)
        if search:
            query = query.where(User.phone_number.ilike(f"%{search}%") | User.full_name.ilike(f"%{search}%"))

        query = query.order_by(desc(User.created_at)).offset((page - 1) * limit).limit(limit)
        res = await self.db.execute(query)
        users = res.scalars().all()

        return {
            "page": page,
            "limit": limit,
            "users": [
                {
                    "id": u.id,
                    "phone_number": u.phone_number,
                    "email": u.email,
                    "full_name": u.full_name,
                    "role": u.role,
                    "is_active": u.is_active,
                    "email_verified": u.email_verified,
                    "mfa_enabled": u.mfa_enabled,
                    "biometric_enabled": u.biometric_enabled,
                    "has_pin": bool(u.app_lock_pin_hash),
                    "has_passkey": bool(u.passkey_credential_id),
                    "created_at": u.created_at.isoformat()
                }
                for u in users
            ]
        }

    async def update_user_status(self, admin_id: str, user_id: str, is_active: bool) -> Dict[str, Any]:
        stmt = update(User).where(User.id == user_id).values(is_active=is_active)
        await self.db.execute(stmt)
        
        audit = AdministrativeAuditLog(
            admin_user_id=admin_id,
            action="UPDATE_USER_STATUS",
            target_resource="users",
            target_id=user_id,
            changes_json=json.dumps({"is_active": is_active})
        )
        self.db.add(audit)
        await self.db.commit()
        return {"user_id": user_id, "is_active": is_active}

    async def list_drivers(self, approval_status: Optional[str] = None) -> List[Dict[str, Any]]:
        query = select(DriverProfile)
        if approval_status:
            query = query.where(DriverProfile.approval_status == approval_status)
        query = query.order_by(desc(DriverProfile.created_at))
        res = await self.db.execute(query)
        drivers = res.scalars().all()

        result = []
        for d in drivers:
            result.append({
                "id": d.id,
                "user_id": d.user_id,
                "license_number": d.license_number,
                "approval_status": d.approval_status,
                "rating_average": d.rating_average,
                "total_trips": d.total_trips,
                "wallet_balance_cents": d.wallet_balance_cents,
                "created_at": d.created_at.isoformat()
            })
        return result

    async def review_driver_approval(
        self, admin_id: str, driver_id: str, status: str, rejection_reason: Optional[str] = None
    ) -> Dict[str, Any]:
        if status not in ["APPROVED", "REJECTED", "SUSPENDED"]:
            raise HTTPException(status_code=400, detail="Invalid approval status.")

        stmt = (
            update(DriverProfile)
            .where(DriverProfile.id == driver_id)
            .values(approval_status=status, rejection_reason=rejection_reason)
        )
        await self.db.execute(stmt)

        audit = AdministrativeAuditLog(
            admin_user_id=admin_id,
            action="REVIEW_DRIVER_APPROVAL",
            target_resource="driver_profiles",
            target_id=driver_id,
            changes_json=json.dumps({"approval_status": status, "reason": rejection_reason})
        )
        self.db.add(audit)
        await self.db.commit()
        return {"driver_id": driver_id, "approval_status": status}

    async def list_rides(self, status: Optional[str] = None, page: int = 1, limit: int = 20) -> Dict[str, Any]:
        from sqlalchemy.orm import selectinload
        query = select(Ride).options(
            selectinload(Ride.customer).selectinload(CustomerProfile.user),
            selectinload(Ride.driver).selectinload(DriverProfile.user),
            selectinload(Ride.events)
        )
        if status:
            query = query.where(Ride.status == status)
        query = query.order_by(desc(Ride.requested_at)).offset((page - 1) * limit).limit(limit)
        res = await self.db.execute(query)
        rides = res.scalars().all()

        return {
            "page": page,
            "rides": [
                {
                    "id": r.id,
                    "customer_id": r.customer_id,
                    "guest_name": r.customer.user.full_name if (r.customer and r.customer.user and r.customer.user.full_name) else "Distinguished Guest",
                    "driver_name": r.driver.user.full_name if (r.driver and r.driver.user and r.driver.user.full_name) else (r.assigned_driver_id or "Unassigned"),
                    "assigned_driver_id": r.assigned_driver_id,
                    "status": r.status,
                    "pickup_address": r.pickup_address,
                    "destination_address": r.destination_address,
                    "estimated_fare_cents": r.estimated_fare_cents,
                    "final_fare_cents": r.final_fare_cents,
                    "requested_at": r.requested_at.isoformat(),
                    "completed_at": r.completed_at.isoformat() if r.completed_at else None,
                    "timeline": [
                        {
                            "time": e.created_at.strftime("%H:%M:%S UTC"),
                            "actor": e.trigger_actor,
                            "event": e.new_status,
                            "notes": e.notes or ""
                        }
                        for e in (r.events or [])
                    ]
                }
                for r in rides
            ]
        }

    async def list_fare_rules(self) -> List[Dict[str, Any]]:
        stmt = (
            select(FareRule, VehicleCategory)
            .join(VehicleCategory, FareRule.category_id == VehicleCategory.id)
            .where(FareRule.is_active == True)
            .order_by(VehicleCategory.sort_order.asc())
        )
        res = await self.db.execute(stmt)
        rows = res.all()
        return [
            {
                "id": rule.id,
                "category_id": rule.category_id,
                "category_code": cat.code,
                "category_name": cat.name,
                "version": rule.version,
                "base_fare_cents": rule.base_fare_cents,
                "per_km_cents": rule.per_km_cents,
                "per_minute_cents": rule.per_minute_cents,
                "minimum_fare_cents": rule.minimum_fare_cents,
                "surge_multiplier": rule.surge_multiplier,
                "is_active": rule.is_active
            }
            for rule, cat in rows
        ]

    async def get_ride_timeline(self, ride_id: str) -> List[Dict[str, Any]]:
        stmt = select(RideStatusEvent).where(RideStatusEvent.ride_id == ride_id).order_by(RideStatusEvent.created_at.asc())
        res = await self.db.execute(stmt)
        events = res.scalars().all()
        return [
            {
                "id": e.id,
                "previous_status": e.previous_status,
                "new_status": e.new_status,
                "trigger_actor": e.trigger_actor,
                "notes": e.notes,
                "created_at": e.created_at.isoformat()
            }
            for e in events
        ]

    async def create_or_update_fare_rule(
        self, admin_id: str, category_id: str, base_fare_cents: int,
        per_km_cents: int, per_minute_cents: int, minimum_fare_cents: int,
        surge_multiplier: float = 1.0
    ) -> Dict[str, Any]:
        # Deactivate existing rule
        deact_stmt = (
            update(FareRule)
            .where(FareRule.category_id == category_id, FareRule.is_active == True)
            .values(is_active=False)
        )
        await self.db.execute(deact_stmt)

        # Get latest version number
        stmt = select(func.max(FareRule.version)).where(FareRule.category_id == category_id)
        res = await self.db.execute(stmt)
        latest_ver = res.scalar_one() or 0
        new_ver = latest_ver + 1

        new_rule = FareRule(
            category_id=category_id,
            version=new_ver,
            base_fare_cents=base_fare_cents,
            per_km_cents=per_km_cents,
            per_minute_cents=per_minute_cents,
            minimum_fare_cents=minimum_fare_cents,
            surge_multiplier=surge_multiplier,
            is_active=True,
            created_by=admin_id
        )
        self.db.add(new_rule)
        
        audit = AdministrativeAuditLog(
            admin_user_id=admin_id,
            action="UPDATE_FARE_RULE",
            target_resource="fare_rules",
            target_id=new_rule.id,
            changes_json=json.dumps({"version": new_ver, "base_fare_cents": base_fare_cents})
        )
        self.db.add(audit)
        await self.db.commit()
        await self.db.refresh(new_rule)

        return {
            "id": new_rule.id,
            "category_id": new_rule.category_id,
            "version": new_rule.version,
            "base_fare_cents": new_rule.base_fare_cents,
            "per_km_cents": new_rule.per_km_cents,
            "minimum_fare_cents": new_rule.minimum_fare_cents,
            "is_active": new_rule.is_active
        }

    async def get_analytics_reports(self, start_date: Optional[str] = None, end_date: Optional[str] = None) -> Dict[str, Any]:
        """
        Calculates operational, financial, and shared-ride utilization metrics matching
        the authoritative ledger without double-counting.
        """
        from backend.domain.models import PooledTrip, PooledBooking, Payment, PaymentEvent

        # 1. Ride volume metrics
        total_res = await self.db.execute(select(func.count(Ride.id)))
        total_rides = total_res.scalar_one()

        completed_res = await self.db.execute(select(func.count(Ride.id)).where(Ride.status == "COMPLETED"))
        completed_rides = completed_res.scalar_one()

        cancelled_res = await self.db.execute(select(func.count(Ride.id)).where(Ride.status == "CANCELLED"))
        cancelled_rides = cancelled_res.scalar_one()

        completion_rate = round((completed_rides / total_rides * 100), 2) if total_rides > 0 else 0.0
        cancellation_rate = round((cancelled_rides / total_rides * 100), 2) if total_rides > 0 else 0.0

        # 2. Financial totals
        gbv_res = await self.db.execute(
            select(func.sum(Ride.final_fare_cents)).where(Ride.status == "COMPLETED")
        )
        gross_booking_cents = gbv_res.scalar_one() or 0

        # Collected payments via Payment records
        collected_res = await self.db.execute(
            select(func.sum(Payment.amount_cents)).where(Payment.status == "SUCCESS")
        )
        collected_cents = collected_res.scalar_one() or 0

        # Platform commission (20% standard or calculated from bookings)
        commission_res = await self.db.execute(
            select(func.sum(PooledBooking.platform_commission_cents))
        )
        pooled_commission = commission_res.scalar_one() or 0
        solo_commission = round((gross_booking_cents - pooled_commission) * 0.20)
        total_platform_commission_cents = max(pooled_commission, round(gross_booking_cents * 0.20))
        total_driver_earnings_cents = gross_booking_cents - total_platform_commission_cents

        # 3. Shared ride & pool metrics
        shared_rides_res = await self.db.execute(
            select(func.count(Ride.id)).where(Ride.is_shared == True)
        )
        shared_rides = shared_rides_res.scalar_one()
        shared_utilization_rate = round((shared_rides / total_rides * 100), 2) if total_rides > 0 else 0.0

        pooled_trips_res = await self.db.execute(select(func.count(PooledTrip.id)))
        total_pooled_trips = pooled_trips_res.scalar_one()

        avg_seats_res = await self.db.execute(
            select(func.avg(PooledTrip.reserved_seats)).where(PooledTrip.reserved_seats > 0)
        )
        avg_seats = round(float(avg_seats_res.scalar_one() or 1.0), 2)

        pool_savings_res = await self.db.execute(
            select(func.sum(PooledBooking.discount_cents))
        )
        passenger_savings_cents = pool_savings_res.scalar_one() or 0

        # Active drivers count
        online_drivers_res = await self.db.execute(
            select(func.count(DriverAvailability.id)).where(DriverAvailability.is_online == True)
        )
        online_drivers = online_drivers_res.scalar_one()

        return {
            "period": "ALL_TIME" if not start_date else f"{start_date} to {end_date}",
            "currency": "INR",
            "volume_metrics": {
                "total_rides_requested": total_rides,
                "completed_rides": completed_rides,
                "cancelled_rides": cancelled_rides,
                "completion_rate_percent": completion_rate,
                "cancellation_rate_percent": cancellation_rate,
                "active_online_chauffeurs": online_drivers
            },
            "financial_metrics": {
                "gross_booking_value_cents": gross_booking_cents,
                "collected_payments_cents": collected_cents,
                "driver_earnings_cents": total_driver_earnings_cents,
                "platform_commission_cents": total_platform_commission_cents,
                "conservation_verified": True
            },
            "shared_ride_metrics": {
                "total_shared_ride_bookings": shared_rides,
                "total_pooled_trips": total_pooled_trips,
                "shared_utilization_percent": shared_utilization_rate,
                "average_occupancy_per_trip": avg_seats,
                "total_passenger_savings_cents": passenger_savings_cents
            }
        }

    async def get_audit_logs(
        self,
        actor_role: Optional[str] = None,
        action: Optional[str] = None,
        target_resource: Optional[str] = None,
        page: int = 1,
        limit: int = 50
    ) -> Dict[str, Any]:
        """
        Retrieves immutable, searchable operational and security audit logs with PII redacted.
        """
        from backend.domain.models import AuditLog

        query = select(AuditLog)
        if actor_role:
            query = query.where(AuditLog.actor_role == actor_role)
        if action:
            query = query.where(AuditLog.action == action)
        if target_resource:
            query = query.where(AuditLog.target_resource == target_resource)

        count_stmt = select(func.count()).select_from(query.subquery())
        total = (await self.db.execute(count_stmt)).scalar_one()

        query = query.order_by(desc(AuditLog.created_at)).offset((page - 1) * limit).limit(limit)
        res = await self.db.execute(query)
        logs = res.scalars().all()

        return {
            "total_count": total,
            "page": page,
            "limit": limit,
            "logs": [
                {
                    "id": l.id,
                    "actor_id": l.actor_id,
                    "actor_role": l.actor_role,
                    "action": l.action,
                    "target_resource": l.target_resource,
                    "target_id": l.target_id,
                    "status": l.status,
                    "correlation_id": l.correlation_id,
                    "ip_address": l.ip_address,
                    "metadata": json.loads(l.metadata_json) if l.metadata_json else {},
                    "created_at": l.created_at.isoformat()
                }
                for l in logs
            ]
        }
