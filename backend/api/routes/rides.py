from typing import Optional, List, Dict, Any
from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException, status, Response, Query
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user, get_current_customer, get_current_driver
from backend.domain.models import User, Ride, DriverProfile, DriverLocation, RideStop, CustomerProfile, DriverSafetyPreferences
from backend.application.services.ride_service import RideService
from backend.application.services.dispatch_advanced_service import DispatchAdvancedService
from backend.application.services.booking_advanced_service import BookingAdvancedService
from backend.application.services.driver_trip_operations_service import DriverTripOperationsService

router = APIRouter(prefix="/rides", tags=["Ride Lifecycle & Booking"])

class IntermediateStopInput(BaseModel):
    address: str
    latitude: float
    longitude: float
    dwell_time_minutes: int = 0
    passenger_instructions: Optional[str] = None
    entrance_landmark: Optional[str] = None

class RideRequestPayload(BaseModel):
    estimate_id: str
    pickup_address: str
    destination_address: str
    payment_method: str = "UPI"
    is_scheduled: bool = False
    scheduled_pickup_time: Optional[datetime] = None
    promo_code: Optional[str] = None
    stops: Optional[List[IntermediateStopInput]] = None

class AdvancedRideRequestPayload(BaseModel):
    estimate_id: str
    pickup_address: str
    destination_address: str
    payment_method: str = "UPI"
    is_scheduled: bool = False
    scheduled_pickup_time: Optional[datetime] = None
    promo_code: Optional[str] = None
    # Features 26 & 27: Booking for another passenger
    booking_for_self: bool = True
    passenger_name: Optional[str] = None
    passenger_phone: Optional[str] = None
    passenger_relationship: Optional[str] = None
    # Features 28 & 39: Return-trip & Round-trip
    is_return_trip: bool = False
    return_pickup_offset_hours: Optional[int] = None
    return_scheduled_time: Optional[datetime] = None
    is_round_trip: bool = False
    # Features 33, 34, 35: Chauffeur Notes & Guidance
    pickup_notes: Optional[str] = None
    destination_notes: Optional[str] = None
    entrance_guidance: Optional[str] = None
    landmark_guidance: Optional[str] = None
    # Features 40, 41: Airport Terminal & Flight Number
    airport_code: Optional[str] = None
    terminal_name: Optional[str] = None
    airline_code: Optional[str] = None
    flight_number: Optional[str] = None
    # Features 43, 44: Concierge & Business Profiles
    booking_mode: str = "STANDARD"  # STANDARD, HOTEL_CONCIERGE, BUSINESS_EXPENSE, DEPENDENT
    hotel_room_number: Optional[str] = None
    hotel_guest_name: Optional[str] = None
    business_cost_center: Optional[str] = None
    business_project_code: Optional[str] = None
    business_tax_id: Optional[str] = None
    # Feature 45: Dependent Link
    dependent_id: Optional[str] = None
    # Feature 49: Shared location link source
    shared_location_source: Optional[str] = None
    # Features 36, 37, 38: Stops with dwell & instructions
    stops: Optional[List[IntermediateStopInput]] = None
    # Part 3 Bespoke Ride Customizations (Features 53-75)
    child_seat_requested: bool = False
    child_seat_type: Optional[str] = None
    luggage_large_count: int = 0
    luggage_small_count: int = 0
    wheelchair_requested: bool = False
    step_free_pickup: bool = False
    assistance_animal: bool = False
    extra_legroom_requested: bool = False
    quiet_ride_requested: bool = True
    cabin_temp_requested: Optional[float] = None
    music_requested: Optional[str] = None
    no_phone_call: bool = False
    driver_language_requested: Optional[str] = None
    female_driver_requested: bool = False
    vehicle_color_requested: Optional[str] = None
    amenities_requested: Optional[str] = None
    ev_ride_requested: bool = False
    pet_friendly_requested: bool = False
    special_assistance_notes: Optional[str] = None

class ScheduleRidePayload(BaseModel):
    estimate_id: str
    pickup_address: str
    destination_address: str
    scheduled_pickup_time: datetime
    payment_method: str = "UPI"
    promo_code: Optional[str] = None
    stops: Optional[List[IntermediateStopInput]] = None

class AddStopPayload(BaseModel):
    address: str
    latitude: float
    longitude: float
    sequence_order: int = 1
    dwell_time_minutes: int = 0
    passenger_instructions: Optional[str] = None
    entrance_landmark: Optional[str] = None

class UpdateStopPayload(BaseModel):
    address: Optional[str] = None
    dwell_time_minutes: Optional[int] = None
    passenger_instructions: Optional[str] = None
    entrance_landmark: Optional[str] = None

class RecommendPickupTimePayload(BaseModel):
    pickup_latitude: float
    pickup_longitude: float
    destination_latitude: float
    destination_longitude: float
    target_arrival_time: str

class ParseSharedLocationPayload(BaseModel):
    raw_link: str

class RecurringSchedulePayload(BaseModel):
    frequency: str = "WEEKDAYS"  # DAILY, WEEKDAYS, WEEKLY
    days_of_week: str = "MON,TUE,WED,THU,FRI"
    pickup_time_str: str = "08:30"
    pickup_address: str
    pickup_latitude: float
    pickup_longitude: float
    destination_address: str
    destination_latitude: float
    destination_longitude: float
    vehicle_category_id: str

class TripTemplatePayload(BaseModel):
    title: str
    pickup_address: str
    pickup_latitude: float
    pickup_longitude: float
    destination_address: str
    destination_latitude: float
    destination_longitude: float
    intermediate_stops_json: Optional[str] = None
    notes: Optional[str] = None
    cabin_temperature: Optional[float] = 21.0

class DependentPayload(BaseModel):
    full_name: str
    phone_number: str
    relationship: str = "Family"
    emergency_notes: Optional[str] = None

class StartRidePayload(BaseModel):
    otp_code: str = Field(..., min_length=6, max_length=6)

class FinishRidePayload(BaseModel):
    actual_distance_km: Optional[float] = None
    actual_duration_minutes: Optional[float] = None

class CancelRidePayload(BaseModel):
    reason: str = Field(..., min_length=3)

class RateDriverPayload(BaseModel):
    rating: int = Field(..., ge=1, le=5)
    feedback: Optional[str] = None

class OperationChecklistPayload(BaseModel):
    checklist: Dict[str, bool]

class TripIssuePayload(BaseModel):
    category: str = Field(..., min_length=2, max_length=40)
    description: str = Field(..., min_length=5, max_length=2000)

class NoShowPayload(BaseModel):
    evidence_note: str = Field(..., min_length=10, max_length=1000)

class PauseTripPayload(BaseModel):
    reason: str = Field(..., min_length=3, max_length=500)

class PinCorrectionPayload(BaseModel):
    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)
    address: str = Field(..., min_length=3, max_length=255)

class DestinationChangePayload(PinCorrectionPayload):
    pass

class DestinationResponsePayload(BaseModel):
    accept: bool
    reason: str = Field("", max_length=500)

@router.post("/request")
async def create_ride_request(
    payload: RideRequestPayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    if payload.is_scheduled and payload.scheduled_pickup_time:
        DispatchAdvancedService.validate_schedule_window(payload.scheduled_pickup_time)

    service = RideService(db)
    stops_data = [s.model_dump() for s in payload.stops] if payload.stops else None
    return await service.create_ride_request(
        customer_user_id=user.id,
        estimate_id=payload.estimate_id,
        pickup_address=payload.pickup_address,
        destination_address=payload.destination_address,
        payment_method=payload.payment_method,
        is_scheduled=payload.is_scheduled,
        scheduled_pickup_time=payload.scheduled_pickup_time,
        promo_code=payload.promo_code,
        stops=stops_data
    )

@router.post("/schedule")
async def schedule_ride(
    payload: ScheduleRidePayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    DispatchAdvancedService.validate_schedule_window(payload.scheduled_pickup_time)
    service = RideService(db)
    stops_data = [s.model_dump() for s in payload.stops] if payload.stops else None
    return await service.create_ride_request(
        customer_user_id=user.id,
        estimate_id=payload.estimate_id,
        pickup_address=payload.pickup_address,
        destination_address=payload.destination_address,
        payment_method=payload.payment_method,
        is_scheduled=True,
        scheduled_pickup_time=payload.scheduled_pickup_time,
        promo_code=payload.promo_code,
        stops=stops_data
    )

@router.post("/advanced-request")
async def create_advanced_ride_request(
    payload: AdvancedRideRequestPayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    stops_data = [s.model_dump() for s in payload.stops] if payload.stops else None
    return await service.create_advanced_booking(
        customer_user_id=user.id,
        estimate_id=payload.estimate_id,
        pickup_address=payload.pickup_address,
        destination_address=payload.destination_address,
        payment_method=payload.payment_method,
        is_scheduled=payload.is_scheduled,
        scheduled_pickup_time=payload.scheduled_pickup_time,
        promo_code=payload.promo_code,
        booking_for_self=payload.booking_for_self,
        passenger_name=payload.passenger_name,
        passenger_phone=payload.passenger_phone,
        passenger_relationship=payload.passenger_relationship,
        is_return_trip=payload.is_return_trip,
        return_pickup_offset_hours=payload.return_pickup_offset_hours,
        return_scheduled_time=payload.return_scheduled_time,
        is_round_trip=payload.is_round_trip,
        pickup_notes=payload.pickup_notes,
        destination_notes=payload.destination_notes,
        entrance_guidance=payload.entrance_guidance,
        landmark_guidance=payload.landmark_guidance,
        airport_code=payload.airport_code,
        terminal_name=payload.terminal_name,
        airline_code=payload.airline_code,
        flight_number=payload.flight_number,
        booking_mode=payload.booking_mode,
        hotel_room_number=payload.hotel_room_number,
        hotel_guest_name=payload.hotel_guest_name,
        business_cost_center=payload.business_cost_center,
        business_project_code=payload.business_project_code,
        business_tax_id=payload.business_tax_id,
        dependent_id=payload.dependent_id,
        shared_location_source=payload.shared_location_source,
        stops=stops_data,
        child_seat_requested=payload.child_seat_requested,
        child_seat_type=payload.child_seat_type,
        luggage_large_count=payload.luggage_large_count,
        luggage_small_count=payload.luggage_small_count,
        wheelchair_requested=payload.wheelchair_requested,
        step_free_pickup=payload.step_free_pickup,
        assistance_animal=payload.assistance_animal,
        extra_legroom_requested=payload.extra_legroom_requested,
        quiet_ride_requested=payload.quiet_ride_requested,
        cabin_temp_requested=payload.cabin_temp_requested,
        music_requested=payload.music_requested,
        no_phone_call=payload.no_phone_call,
        driver_language_requested=payload.driver_language_requested,
        female_driver_requested=payload.female_driver_requested,
        vehicle_color_requested=payload.vehicle_color_requested,
        amenities_requested=payload.amenities_requested,
        ev_ride_requested=payload.ev_ride_requested,
        pet_friendly_requested=payload.pet_friendly_requested,
        special_assistance_notes=payload.special_assistance_notes
    )

@router.post("/recommend-pickup-time")
async def recommend_pickup_time(
    payload: RecommendPickupTimePayload
):
    return BookingAdvancedService.recommend_pickup_time(
        pickup_lat=payload.pickup_latitude,
        pickup_lng=payload.pickup_longitude,
        dest_lat=payload.destination_latitude,
        dest_lng=payload.destination_longitude,
        target_arrival_time=payload.target_arrival_time
    )

@router.get("/flight-status")
async def get_flight_status(
    flight_number: str = Query(..., description="Flight code, e.g. EK504 or AI101"),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.get_flight_status_data(flight_number)

@router.post("/{ride_id}/sync-flight")
async def sync_flight_to_ride(
    ride_id: str,
    flight_number: str = Query(..., description="Flight code"),
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.sync_flight_delay_to_ride(ride_id, flight_number)

@router.get("/{ride_id}/calendar-invite")
async def download_calendar_invite(
    ride_id: str,
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    ics_str = await service.generate_calendar_invite_ics(ride_id)
    return Response(
        content=ics_str,
        media_type="text/calendar",
        headers={"Content-Disposition": f"attachment; filename=grand_chauffeur_{ride_id[:8]}.ics"}
    )

@router.post("/{ride_id}/stops")
async def add_stop_to_ride(
    ride_id: str,
    payload: AddStopPayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.add_stop_during_active_ride(
        ride_id=ride_id,
        customer_id=user.id,
        address=payload.address,
        latitude=payload.latitude,
        longitude=payload.longitude,
        sequence_order=payload.sequence_order,
        dwell_time_minutes=payload.dwell_time_minutes,
        passenger_instructions=payload.passenger_instructions,
        entrance_landmark=payload.entrance_landmark
    )

@router.patch("/{ride_id}/stops/{stop_id}")
async def update_stop_in_ride(
    ride_id: str,
    stop_id: str,
    payload: UpdateStopPayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.update_stop_during_active_ride(
        ride_id=ride_id,
        stop_id=stop_id,
        customer_id=user.id,
        address=payload.address,
        dwell_time_minutes=payload.dwell_time_minutes,
        passenger_instructions=payload.passenger_instructions,
        entrance_landmark=payload.entrance_landmark
    )

@router.delete("/{ride_id}/stops/{stop_id}")
async def delete_stop_from_ride(
    ride_id: str,
    stop_id: str,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.delete_stop_during_active_ride(
        ride_id=ride_id,
        stop_id=stop_id,
        customer_id=user.id
    )

@router.get("/{ride_id}/stops")
async def get_ride_stops(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    ride = await RideService(db).get_ride_by_id(ride_id)
    if user.role != "admin":
        if user.role == "driver":
            driver_result = await db.execute(select(DriverProfile).where(DriverProfile.user_id == user.id))
            driver = driver_result.scalar_one_or_none()
            if driver is None or ride.assigned_driver_id != driver.id:
                raise HTTPException(status_code=403, detail="Only trip participants can view stops.")
        else:
            customer_result = await db.execute(select(CustomerProfile).where(CustomerProfile.user_id == user.id))
            customer = customer_result.scalar_one_or_none()
            if customer is None or ride.customer_id != customer.id:
                raise HTTPException(status_code=403, detail="Only trip participants can view stops.")
    stmt = select(RideStop).where(RideStop.ride_id == ride_id).order_by(RideStop.sequence_order.asc())
    res = await db.execute(stmt)
    stops = res.scalars().all()
    return [
        {
            "id": s.id,
            "sequence_order": s.sequence_order,
            "address": s.address,
            "latitude": s.latitude,
            "longitude": s.longitude,
            "dwell_time_minutes": s.dwell_time_minutes,
            "passenger_instructions": s.passenger_instructions,
            "entrance_landmark": s.entrance_landmark,
            "status": s.status
        }
        for s in stops
    ]

@router.post("/{ride_id}/waiting/start")
async def start_waiting(
    ride_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    result = await DispatchAdvancedService.start_waiting(db, ride_id, user.id)
    await db.commit()
    return result

@router.post("/{ride_id}/waiting/stop")
async def stop_waiting(
    ride_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    result = await DispatchAdvancedService.stop_waiting(db, ride_id, user.id)
    await db.commit()
    return result

@router.post("/{ride_id}/accept")
async def accept_ride(
    ride_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    return await service.accept_ride_atomic(driver_user_id=user.id, ride_id=ride_id)

@router.post("/{ride_id}/arrive")
async def driver_arrived(
    ride_id: str,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    return await service.driver_arrived(driver_user_id=user.id, ride_id=ride_id)

@router.post("/{ride_id}/start")
async def start_trip(
    ride_id: str,
    payload: StartRidePayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    return await service.start_trip_with_otp(
        driver_user_id=user.id,
        ride_id=ride_id,
        otp_code=payload.otp_code
    )

@router.post("/{ride_id}/finish")
async def finish_trip(
    ride_id: str,
    payload: FinishRidePayload,
    user: User = Depends(get_current_driver),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    return await service.finish_trip(
        driver_user_id=user.id,
        ride_id=ride_id,
        actual_distance_km=payload.actual_distance_km,
        actual_duration_minutes=payload.actual_duration_minutes
    )

@router.post("/{ride_id}/actions/start-checklist")
async def record_start_checklist(ride_id: str, payload: OperationChecklistPayload, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).prepare_trip(ride_id, user.id, payload.checklist)

@router.post("/{ride_id}/actions/pickup-pin-correction")
async def correct_pickup_pin(ride_id: str, payload: PinCorrectionPayload, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    svc = DriverTripOperationsService(db)
    ride = await svc.record_event(ride_id, user.id, "PICKUP_PIN_CORRECTED", payload.model_dump())
    if ride.status not in {"ASSIGNED", "ARRIVED"}:
        raise HTTPException(status_code=409, detail="Pickup pin can only be corrected before the trip starts.")
    ride.pickup_latitude, ride.pickup_longitude, ride.pickup_address = payload.latitude, payload.longitude, payload.address
    await db.commit()
    return {"ride_id": ride.id, "pickup_address": ride.pickup_address, "latitude": ride.pickup_latitude, "longitude": ride.pickup_longitude}

@router.post("/{ride_id}/actions/reroute")
async def record_reroute(ride_id: str, payload: TripIssuePayload, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    ride = await DriverTripOperationsService(db).record_event(ride_id, user.id, "ROAD_CLOSURE_REROUTE", {"category": payload.category, "description": payload.description})
    if ride.status not in {"ASSIGNED", "ARRIVED", "IN_PROGRESS", "PAUSED"}:
        raise HTTPException(status_code=409, detail="A road closure can only be recorded during an active trip.")
    await db.commit()
    return {"status": "REROUTE_RECORDED", "message": "Closure noted. Select an alternate route in your navigation app; route calculation is provided by that app."}

@router.post("/{ride_id}/actions/pause")
async def pause_trip(ride_id: str, payload: PauseTripPayload, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).pause_or_resume(ride_id, user.id, True, payload.reason)

@router.post("/{ride_id}/actions/resume")
async def resume_trip(ride_id: str, payload: PauseTripPayload, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).pause_or_resume(ride_id, user.id, False, payload.reason)

@router.post("/{ride_id}/actions/no-show")
async def report_passenger_no_show(ride_id: str, payload: NoShowPayload, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).report_no_show(ride_id, user.id, payload.evidence_note)

@router.post("/{ride_id}/actions/completion-checklist")
async def completion_checklist(ride_id: str, payload: OperationChecklistPayload, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).submit_completion_checklist(ride_id, user.id, payload.checklist)

@router.post("/{ride_id}/actions/issues")
async def report_trip_issue(ride_id: str, payload: TripIssuePayload, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).report_issue(ride_id, user.id, payload.category, payload.description)

@router.post("/{ride_id}/stops/{stop_id}/arrive")
async def arrive_at_stop(ride_id: str, stop_id: str, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).mark_stop(ride_id, stop_id, user.id, "ARRIVED")

@router.post("/{ride_id}/stops/{stop_id}/depart")
async def depart_stop(ride_id: str, stop_id: str, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).mark_stop(ride_id, stop_id, user.id, "DEPARTED")

@router.post("/{ride_id}/destination-change")
async def request_destination_change(ride_id: str, payload: DestinationChangePayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).submit_destination_change(ride_id, user, payload.address, payload.latitude, payload.longitude)

@router.post("/destination-change/{request_id}/customer-response")
async def respond_destination_quote(request_id: str, payload: DestinationResponsePayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).customer_confirm_change(request_id, user, payload.accept)

@router.post("/{ride_id}/destination-change/{request_id}/driver-response")
async def respond_destination_driver(ride_id: str, request_id: str, payload: DestinationResponsePayload, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).driver_resolve_change(ride_id, request_id, user.id, payload.accept, payload.reason)

@router.get("/{ride_id}/destination-change/pending")
async def get_pending_destination_change(ride_id: str, user: User = Depends(get_current_driver), db: AsyncSession = Depends(get_db)):
    return await DriverTripOperationsService(db).pending_driver_destination_change(ride_id, user.id)

@router.post("/{ride_id}/cancel")
async def cancel_ride(
    ride_id: str,
    payload: CancelRidePayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    return await service.cancel_ride(
        user_id=user.id,
        role=user.role,
        ride_id=ride_id,
        reason=payload.reason
    )

@router.post("/{ride_id}/rate")
async def rate_driver(
    ride_id: str,
    payload: RateDriverPayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    return await service.rate_driver(
        customer_user_id=user.id,
        ride_id=ride_id,
        rating=payload.rating,
        feedback=payload.feedback
    )

@router.get("/{ride_id}")
async def get_ride_details(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    ride = await service.get_ride_by_id(ride_id)
    if user.role != "admin":
        if user.role == "driver":
            driver_result = await db.execute(select(DriverProfile).where(DriverProfile.user_id == user.id))
            driver = driver_result.scalar_one_or_none()
            if driver is None or ride.assigned_driver_id != driver.id:
                raise HTTPException(status_code=403, detail="Only trip participants can view ride details.")
        else:
            customer_result = await db.execute(select(CustomerProfile).where(CustomerProfile.user_id == user.id))
            customer = customer_result.scalar_one_or_none()
            if customer is None or ride.customer_id != customer.id:
                raise HTTPException(status_code=403, detail="Only trip participants can view ride details.")
    
    driver_info = None
    if ride.assigned_driver_id:
        stmt = select(DriverProfile).where(DriverProfile.id == ride.assigned_driver_id)
        res = await db.execute(stmt)
        drv = res.scalar_one_or_none()
        if drv:
            stmt_u = select(User).where(User.id == drv.user_id)
            res_u = await db.execute(stmt_u)
            u = res_u.scalar_one_or_none()
            driver_info = {
                "driver_id": drv.id,
                "name": u.full_name if u else "Executive Chauffeur",
                "phone": u.phone_number if u else "",
                "license_number": drv.license_number,
                "rating": drv.rating_average,
                "total_trips": drv.total_trips
            }

    return {
        "id": ride.id,
        "status": ride.status,
        "pickup_address": ride.pickup_address,
        "pickup_latitude": ride.pickup_latitude,
        "pickup_longitude": ride.pickup_longitude,
        "destination_address": ride.destination_address,
        "destination_latitude": ride.destination_latitude,
        "destination_longitude": ride.destination_longitude,
        "estimated_fare_cents": ride.estimated_fare_cents,
        "final_fare_cents": ride.final_fare_cents,
        "payment_method": ride.payment_method,
        "start_otp": ride.start_otp if (user.role == "customer" or user.role == "admin") else None,
        "driver": driver_info,
        "requested_at": ride.requested_at.isoformat(),
        "assigned_at": ride.assigned_at.isoformat() if ride.assigned_at else None,
        "arrived_at": ride.arrived_at.isoformat() if ride.arrived_at else None,
        "started_at": ride.started_at.isoformat() if ride.started_at else None,
        "completed_at": ride.completed_at.isoformat() if ride.completed_at else None,
        "cancelled_at": ride.cancelled_at.isoformat() if ride.cancelled_at else None,
        # Part 2 attributes
        "booking_for_self": ride.booking_for_self,
        "passenger_name": ride.passenger_name,
        "passenger_phone": ride.passenger_phone,
        "passenger_relationship": ride.passenger_relationship,
        "pickup_notes": ride.pickup_notes,
        "destination_notes": ride.destination_notes,
        "entrance_guidance": ride.entrance_guidance,
        "landmark_guidance": ride.landmark_guidance,
        "airport_code": ride.airport_code,
        "terminal_name": ride.terminal_name,
        "airline_code": ride.airline_code,
        "flight_number": ride.flight_number,
        "flight_delay_minutes": ride.flight_delay_minutes,
        "flight_status": ride.flight_status,
        "booking_mode": ride.booking_mode,
        "hotel_room_number": ride.hotel_room_number,
        "hotel_guest_name": ride.hotel_guest_name,
        "business_cost_center": ride.business_cost_center,
        "business_project_code": ride.business_project_code,
        "business_tax_id": ride.business_tax_id,
        "is_return_trip": ride.is_return_trip,
        "paired_return_ride_id": ride.paired_return_ride_id,
        "is_round_trip": ride.is_round_trip
    }

# ----------------- RECENT DESTINATIONS & LINK PARSER -----------------
@router.get("/recent-destinations")
async def get_recent_destinations(
    limit: int = Query(6, ge=1, le=20),
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.get_recent_destinations(customer_user_id=user.id, limit=limit)

@router.post("/parse-shared-location")
async def parse_shared_location(
    payload: ParseSharedLocationPayload
):
    return BookingAdvancedService.parse_shared_location_link(payload.raw_link)

# ----------------- RECURRING SCHEDULES (Feature 29) -----------------
@router.get("/recurring")
async def list_recurring_schedules(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.list_recurring_schedules(user.id)

@router.post("/recurring")
async def create_recurring_schedule(
    payload: RecurringSchedulePayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.create_recurring_schedule(
        customer_user_id=user.id,
        frequency=payload.frequency,
        days_of_week=payload.days_of_week,
        pickup_time_str=payload.pickup_time_str,
        pickup_address=payload.pickup_address,
        pickup_latitude=payload.pickup_latitude,
        pickup_longitude=payload.pickup_longitude,
        destination_address=payload.destination_address,
        destination_latitude=payload.destination_latitude,
        destination_longitude=payload.destination_longitude,
        vehicle_category_id=payload.vehicle_category_id
    )

@router.patch("/recurring/{schedule_id}/toggle")
async def toggle_recurring_schedule(
    schedule_id: str,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.toggle_recurring_schedule(schedule_id, user.id)

@router.delete("/recurring/{schedule_id}")
async def delete_recurring_schedule(
    schedule_id: str,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.delete_recurring_schedule(schedule_id, user.id)

# ----------------- TRIP TEMPLATES (Feature 46) -----------------
@router.get("/templates")
async def list_trip_templates(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.list_trip_templates(user.id)

@router.post("/templates")
async def create_trip_template(
    payload: TripTemplatePayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.create_trip_template(
        customer_user_id=user.id,
        title=payload.title,
        pickup_address=payload.pickup_address,
        pickup_latitude=payload.pickup_latitude,
        pickup_longitude=payload.pickup_longitude,
        destination_address=payload.destination_address,
        destination_latitude=payload.destination_latitude,
        destination_longitude=payload.destination_longitude,
        intermediate_stops_json=payload.intermediate_stops_json,
        notes=payload.notes,
        cabin_temperature=payload.cabin_temperature
    )

@router.delete("/templates/{template_id}")
async def delete_trip_template(
    template_id: str,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.delete_trip_template(template_id, user.id)

# ----------------- DEPENDENTS (Feature 45) -----------------
@router.get("/dependents")
async def list_dependents(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.list_dependents(user.id)

@router.post("/dependents")
async def add_dependent(
    payload: DependentPayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.add_dependent(
        customer_user_id=user.id,
        full_name=payload.full_name,
        phone_number=payload.phone_number,
        relationship=payload.relationship,
        emergency_notes=payload.emergency_notes
    )

@router.delete("/dependents/{dependent_id}")
async def delete_dependent(
    dependent_id: str,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    service = BookingAdvancedService(db)
    return await service.delete_dependent(dependent_id, user.id)

@router.get("/{ride_id}/track")
async def track_ride_location(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    ride = await service.get_ride_by_id(ride_id)

    if user.role.lower() == "customer":
        profile = (await db.execute(select(CustomerProfile).where(CustomerProfile.user_id == user.id))).scalar_one_or_none()
        if not profile or ride.customer_id != profile.id:
            raise HTTPException(status_code=403, detail="Ride participant access required")
    elif user.role.lower() == "driver":
        profile = (await db.execute(select(DriverProfile).where(DriverProfile.user_id == user.id))).scalar_one_or_none()
        if not profile or ride.assigned_driver_id != profile.id:
            raise HTTPException(status_code=403, detail="Ride participant access required")
    elif user.role.lower() != "admin":
        raise HTTPException(status_code=403, detail="Ride participant access required")

    # Never substitute pickup coordinates for a live driver location. Customers
    # receive telemetry only during an active ride and after driver opt-in.
    lat, lng, heading = None, None, None
    if ride.assigned_driver_id and ride.status in ("ASSIGNED", "ARRIVED", "IN_PROGRESS"):
        preference = (await db.execute(select(DriverSafetyPreferences).where(DriverSafetyPreferences.driver_id == ride.assigned_driver_id))).scalar_one_or_none()
        may_share = user.role.lower() != "customer" or bool(preference and preference.share_trip_location_with_customer)
    else:
        may_share = False
    if ride.assigned_driver_id and may_share:
        stmt = select(DriverLocation).where(DriverLocation.driver_id == ride.assigned_driver_id).order_by(DriverLocation.recorded_at.desc())
        res = await db.execute(stmt)
        loc = res.scalars().first()
        if loc:
            lat, lng, heading = loc.latitude, loc.longitude, (loc.heading or 0.0)

    return {
        "ride_id": ride.id,
        "status": ride.status,
        "driver_latitude": lat,
        "driver_longitude": lng,
        "heading": heading,
        "location_sharing_enabled": lat is not None
    }

# ----------------- Part 4 Fares, Splits, Invoices & Disputes (Features 76, 80, 83, 96, 97, 100) -----------------
class SplitFareInitiateRequest(BaseModel):
    splits: List[Dict[str, Any]]

class SplitFareRespondRequest(BaseModel):
    action: str  # ACCEPT or DECLINE

class SplitFarePayRequest(BaseModel):
    payment_method: str = "WALLET"  # WALLET or UPI

class SendInvoiceEmailRequest(BaseModel):
    recipient_email: str

class SubmitDisputeRequest(BaseModel):
    dispute_reason: str
    claimed_amount_cents: int
    customer_comment: str

@router.get("/{ride_id}/fare-breakdown")
async def get_ride_fare_breakdown(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    ride = await service.get_ride_by_id(ride_id)
    fare = ride.final_fare_cents or ride.estimated_fare_cents
    return {
        "ride_id": ride.id,
        "currency": "INR",
        "total_fare_cents": fare,
        "total_fare_rupees": round(fare / 100, 2),
        "distance_km": round(ride.actual_distance_km or ride.estimated_distance_km, 2),
        "duration_minutes": round(ride.actual_duration_minutes or ride.estimated_duration_minutes, 1),
        "base_fare_rupees": 500.0,
        "distance_charge_rupees": round(max(0, (fare / 100) - 600), 2),
        "time_charge_rupees": 50.0,
        "tax_rupees": round((fare * 0.05) / 100, 2),
        "amenities_fee_rupees": 50.0,
        "price_locked": ride.price_locked,
        "fare_cap_applied": ride.fare_cap_applied
    }

@router.get("/{ride_id}/route-change-explanation")
async def get_route_change_explanation(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    service = RideService(db)
    ride = await service.get_ride_by_id(ride_id)
    return {
        "ride_id": ride.id,
        "original_fare_rupees": round((ride.original_fare_cents or ride.estimated_fare_cents) / 100, 2),
        "final_fare_rupees": round((ride.final_fare_cents or ride.estimated_fare_cents) / 100, 2),
        "fare_difference_rupees": round(ride.fare_difference_cents / 100, 2),
        "fare_change_reason": ride.fare_change_reason or "Route and dwell duration executed according to original booking itinerary.",
        "has_changed": (ride.fare_difference_cents != 0)
    }

@router.post("/{ride_id}/split-fare")
async def initiate_split_fare(
    ride_id: str,
    payload: SplitFareInitiateRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    service = SettlementAdvancedService(db)
    return await service.initiate_split_fare(
        ride_id=ride_id,
        inviter_user_id=user.id,
        splits=payload.splits
    )

@router.post("/split-fare/{split_id}/respond")
async def respond_to_split(
    split_id: str,
    payload: SplitFareRespondRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    service = SettlementAdvancedService(db)
    return await service.respond_to_split_fare(
        split_id=split_id,
        action=payload.action,
        payer_user_id=user.id
    )

@router.post("/split-fare/{split_id}/pay")
async def pay_split_share(
    split_id: str,
    payload: SplitFarePayRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    service = SettlementAdvancedService(db)
    return await service.pay_split_fare_share(
        split_id=split_id,
        payment_method=payload.payment_method,
        user_id=user.id
    )

@router.get("/{ride_id}/tax-invoice")
async def get_tax_invoice(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    service = SettlementAdvancedService(db)
    return await service.generate_tax_invoice(ride_id)

@router.post("/{ride_id}/send-invoice-email")
async def send_invoice_email(
    ride_id: str,
    payload: SendInvoiceEmailRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    service = SettlementAdvancedService(db)
    return await service.send_invoice_email(
        ride_id=ride_id,
        target_email=payload.recipient_email
    )

@router.post("/{ride_id}/disputes")
async def submit_dispute(
    ride_id: str,
    payload: SubmitDisputeRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    stmt = select(CustomerProfile).where(CustomerProfile.user_id == user.id)
    res = await db.execute(stmt)
    prof = res.scalars().first()
    customer_id = prof.id if prof else user.id

    service = SettlementAdvancedService(db)
    return await service.submit_fare_dispute(
        ride_id=ride_id,
        customer_id=customer_id,
        dispute_reason=payload.dispute_reason,
        claimed_amount_cents=payload.claimed_amount_cents,
        customer_comment=payload.customer_comment
    )

@router.get("/disputes/my")
async def list_my_disputes(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    stmt = select(CustomerProfile).where(CustomerProfile.user_id == user.id)
    res = await db.execute(stmt)
    prof = res.scalars().first()
    customer_id = prof.id if prof else user.id

    service = SettlementAdvancedService(db)
    return await service.list_customer_disputes(customer_id)

# -----------------------------------------------------------------------------
# Part 5: Tracking Telemetry, Trip Sharing, Route & Lost Items (Features 101-125)
# -----------------------------------------------------------------------------
class TripSharePayload(BaseModel):
    recipient_name: Optional[str] = None
    recipient_phone: Optional[str] = None
    hide_exact_address: bool = False
    expiry_hours: int = 4

class ArrivalPhotoPayload(BaseModel):
    photo_url: str

class RoutePreferencePayload(BaseModel):
    route_preference: str  # FASTEST, SCENIC, SMOOTHEST

class LostItemPayload(BaseModel):
    item_type: str
    description: str
    seat_location: str = "BACK_SEAT"
    contact_phone: str

@router.get("/{ride_id}/telemetry")
async def get_advanced_ride_telemetry(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.tracking_advanced_service import TrackingAdvancedService
    service = TrackingAdvancedService(db)
    return await service.get_advanced_tracking_telemetry(ride_id, user)

@router.post("/{ride_id}/share")
async def create_trip_share(
    ride_id: str,
    payload: TripSharePayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.tracking_advanced_service import TrackingAdvancedService
    service = TrackingAdvancedService(db)
    return await service.create_trip_share_link(
        ride_id=ride_id,
        user_id=user.id,
        recipient_name=payload.recipient_name,
        recipient_phone=payload.recipient_phone,
        hide_exact_address=payload.hide_exact_address,
        expiry_hours=payload.expiry_hours
    )

@router.get("/share/{token}")
async def resolve_shared_trip_link(
    token: str,
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.tracking_advanced_service import TrackingAdvancedService
    service = TrackingAdvancedService(db)
    return await service.resolve_shared_trip(token)

@router.post("/{ride_id}/arrival-photo")
async def upload_chauffeur_arrival_photo(
    ride_id: str,
    payload: ArrivalPhotoPayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.tracking_advanced_service import TrackingAdvancedService
    service = TrackingAdvancedService(db)
    return await service.update_driver_arrival_photo(ride_id, payload.photo_url)

@router.get("/{ride_id}/route-preview")
async def get_ride_route_preview(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.tracking_advanced_service import TrackingAdvancedService
    service = TrackingAdvancedService(db)
    return await service.get_route_preview(ride_id)

@router.post("/{ride_id}/route-preference")
async def set_ride_route_preference(
    ride_id: str,
    payload: RoutePreferencePayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.tracking_advanced_service import TrackingAdvancedService
    service = TrackingAdvancedService(db)
    return await service.update_route_preference(ride_id, payload.route_preference)

@router.get("/{ride_id}/receipt")
async def get_ride_completion_receipt(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.tracking_advanced_service import TrackingAdvancedService
    service = TrackingAdvancedService(db)
    return await service.get_completion_receipt(ride_id)

@router.post("/{ride_id}/lost-item")
async def report_lost_property(
    ride_id: str,
    payload: LostItemPayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.tracking_advanced_service import TrackingAdvancedService
    service = TrackingAdvancedService(db)
    return await service.lodge_lost_item_report(
        ride_id=ride_id,
        customer_user_id=user.id,
        item_type=payload.item_type,
        description=payload.description,
        seat_location=payload.seat_location,
        contact_phone=payload.contact_phone
    )

@router.get("/{ride_id}/driver-contact")
async def get_post_trip_driver_contact(
    ride_id: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.tracking_advanced_service import TrackingAdvancedService
    service = TrackingAdvancedService(db)
    return await service.get_driver_post_trip_contact(ride_id, user.id)


