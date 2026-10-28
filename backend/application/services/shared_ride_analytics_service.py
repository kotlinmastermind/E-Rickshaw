"""Admin-only operational analytics derived from persisted shared-trip records."""
from datetime import datetime, timedelta, timezone
from typing import Any, Dict

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from backend.domain.models import (
    PooledTrip, PooledBooking, PooledTripStop, PassengerOccupancyEvent,
    VehicleCategory,
)


class SharedRideAnalyticsService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_operational_analytics(self, days: int = 30) -> Dict[str, Any]:
        now = datetime.now(timezone.utc)
        since = now - timedelta(days=days)

        trip_rows = (await self.db.execute(
            select(PooledTrip.status, func.count(PooledTrip.id),
                   func.coalesce(func.sum(PooledTrip.max_seats), 0),
                   func.coalesce(func.sum(PooledTrip.reserved_seats), 0),
                   func.coalesce(func.sum(PooledTrip.current_occupancy), 0))
            .where(PooledTrip.created_at >= since)
            .group_by(PooledTrip.status)
        )).all()
        trip_status = {status: int(count or 0) for status, count, *_ in trip_rows}
        trip_total = sum(trip_status.values())

        booking_rows = (await self.db.execute(
            select(PooledBooking.booking_status, func.count(PooledBooking.id),
                   func.coalesce(func.sum(PooledBooking.seats_reserved), 0),
                   func.coalesce(func.sum(PooledBooking.discount_cents), 0),
                   func.coalesce(func.sum(PooledBooking.allocated_fare_cents), 0))
            .where(PooledBooking.created_at >= since)
            .group_by(PooledBooking.booking_status)
        )).all()
        booking_status = {status: int(count or 0) for status, count, *_ in booking_rows}
        booking_total = sum(booking_status.values())
        reserved_seats = sum(int(row[2] or 0) for row in booking_rows)
        passenger_savings_cents = sum(int(row[3] or 0) for row in booking_rows)
        allocated_fare_cents = sum(int(row[4] or 0) for row in booking_rows)

        event_rows = (await self.db.execute(
            select(PassengerOccupancyEvent.event_type, func.count(PassengerOccupancyEvent.id))
            .where(PassengerOccupancyEvent.recorded_at >= since)
            .group_by(PassengerOccupancyEvent.event_type)
        )).all()
        event_counts = {kind: int(count or 0) for kind, count in event_rows}

        stop_rows = (await self.db.execute(
            select(PooledTripStop.status, func.count(PooledTripStop.id))
            .join(PooledTrip, PooledTrip.id == PooledTripStop.pooled_trip_id)
            .where(PooledTrip.created_at >= since)
            .group_by(PooledTripStop.status)
        )).all()
        stop_counts = {state: int(count or 0) for state, count in stop_rows}

        daily_trip_rows = (await self.db.execute(
            select(func.date(PooledTrip.created_at), func.count(PooledTrip.id))
            .where(PooledTrip.created_at >= since)
            .group_by(func.date(PooledTrip.created_at))
        )).all()
        daily_booking_rows = (await self.db.execute(
            select(func.date(PooledBooking.created_at), func.count(PooledBooking.id))
            .where(PooledBooking.created_at >= since)
            .group_by(func.date(PooledBooking.created_at))
        )).all()
        daily_trip = {str(day): int(count or 0) for day, count in daily_trip_rows}
        daily_booking = {str(day): int(count or 0) for day, count in daily_booking_rows}
        activity_days = sorted(set(daily_trip) | set(daily_booking))

        active_statuses = {"OPEN", "ACTIVE", "FULL"}
        active_trip_count = sum(trip_status.get(key, 0) for key in active_statuses)
        active_capacity = sum(int(row[2] or 0) for row in trip_rows if row[0] in active_statuses)
        active_reserved = sum(int(row[3] or 0) for row in trip_rows if row[0] in active_statuses)
        active_occupancy = sum(int(row[4] or 0) for row in trip_rows if row[0] in active_statuses)
        open_seats = max(0, active_capacity - active_reserved)

        category_rows = (await self.db.execute(
            select(VehicleCategory.name, func.count(PooledTrip.id),
                   func.coalesce(func.sum(PooledTrip.max_seats), 0),
                   func.coalesce(func.sum(PooledTrip.reserved_seats), 0),
                   func.coalesce(func.sum(PooledTrip.current_occupancy), 0))
            .join(PooledTrip, PooledTrip.vehicle_category_id == VehicleCategory.id)
            .where(PooledTrip.created_at >= since)
            .group_by(VehicleCategory.name)
            .order_by(VehicleCategory.name.asc())
        )).all()

        recent = (await self.db.execute(
            select(PooledTrip).where(PooledTrip.created_at >= since)
            .order_by(PooledTrip.updated_at.desc(), PooledTrip.id.asc()).limit(12)
        )).scalars().all()

        return {
            "period": {"days": days, "start": since.isoformat(), "end": now.isoformat()},
            "summary": {
                "pooled_trips": trip_total,
                "shared_bookings": booking_total,
                "active_pools": active_trip_count,
                "open_seats": open_seats,
                "active_seat_capacity": active_capacity,
                "active_reserved_seats": active_reserved,
                "reserved_seat_utilization_percent": round(active_reserved / active_capacity * 100, 1) if active_capacity else 0.0,
                "active_passengers_onboard": active_occupancy,
                "onboard_capacity_utilization_percent": round(active_occupancy / active_capacity * 100, 1) if active_capacity else 0.0,
                "no_show_bookings": booking_status.get("NO_SHOW", 0),
                "no_show_rate_percent": round(booking_status.get("NO_SHOW", 0) / booking_total * 100, 1) if booking_total else 0.0,
                "completed_bookings": booking_status.get("COMPLETED", 0),
                "cancelled_bookings": booking_status.get("CANCELLED", 0),
                "passenger_savings_cents": passenger_savings_cents,
                "allocated_fare_cents": allocated_fare_cents,
            },
            "trip_status_counts": trip_status,
            "booking_status_counts": booking_status,
            "occupancy_event_counts": event_counts,
            "stop_status_counts": stop_counts,
            "category_capacity": [
                {"category": name, "trips": int(count or 0), "capacity": int(capacity or 0),
                 "reserved_seats": int(reserved or 0), "passengers_onboard": int(occupancy or 0),
                 "open_seats": max(0, int(capacity or 0) - int(reserved or 0))}
                for name, count, capacity, reserved, occupancy in category_rows
            ],
            "daily_activity": [
                {"date": day, "trips_opened": daily_trip.get(day, 0), "bookings_created": daily_booking.get(day, 0)}
                for day in activity_days
            ],
            "recent_pools": [
                {"reference": f"POOL-{trip.id[:8].upper()}", "status": trip.status,
                 "category_id": trip.vehicle_category_id, "driver_assigned": trip.driver_id is not None,
                 "capacity": trip.max_seats, "reserved_seats": trip.reserved_seats,
                 "passengers_onboard": trip.current_occupancy,
                 "open_seats": max(0, trip.max_seats - trip.reserved_seats),
                 "updated_at": trip.updated_at.isoformat() if trip.updated_at else None}
                for trip in recent
            ],
            "source": "pooled_trips, pooled_bookings, pooled_trip_stops, and passenger_occupancy_events",
        }
