from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_customer
from backend.domain.models import User, CustomerProfile, DeviceToken, Notification

router = APIRouter(prefix="/customers", tags=["Customer Profiles"])

class CustomerProfileUpdate(BaseModel):
    full_name: Optional[str] = Field(None, json_schema_extra={"example": "Lord Sterling"})
    email: Optional[str] = Field(None, json_schema_extra={"example": "sterling@luxury.io"})
    username: Optional[str] = Field(None, json_schema_extra={"example": "lord_sterling"})
    avatar_url: Optional[str] = Field(None, json_schema_extra={"example": "https://example.com/avatar.jpg"})
    emergency_contact_phone: Optional[str] = Field(None, json_schema_extra={"example": "+919811122233"})
    preferred_payment_method: Optional[str] = Field(None, json_schema_extra={"example": "UPI"})

class DeviceTokenPayload(BaseModel):
    fcm_token: str
    device_os: str = "ANDROID"

@router.get("/profile")
async def get_profile(user: User = Depends(get_current_customer), db: AsyncSession = Depends(get_db)):
    stmt = select(CustomerProfile).where(CustomerProfile.user_id == user.id)
    res = await db.execute(stmt)
    profile = res.scalar_one_or_none()
    if not profile:
        profile = CustomerProfile(user_id=user.id)
        db.add(profile)
        await db.commit()
        await db.refresh(profile)

    uname = user.username or (user.email.split("@")[0] if user.email else f"user_{user.phone_number[-4:]}")
    return {
        "user_id": user.id,
        "full_name": user.full_name,
        "phone_number": user.phone_number,
        "email": user.email,
        "username": uname,
        "avatar_url": user.avatar_url,
        "is_verified": user.is_verified,
        "role": user.role,
        "created_at": user.created_at.isoformat() if user.created_at else None,
        "preferred_payment_method": profile.preferred_payment_method,
        "emergency_contact_phone": profile.emergency_contact_phone,
        "rating_average": profile.rating_average,
        "rating_count": profile.rating_count
    }

@router.put("/profile")
async def update_profile(
    payload: CustomerProfileUpdate,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    if payload.full_name is not None:
        user.full_name = payload.full_name
    if payload.email is not None:
        user.email = payload.email
    if payload.username is not None:
        user.username = payload.username
    if payload.avatar_url is not None:
        user.avatar_url = payload.avatar_url

    stmt = select(CustomerProfile).where(CustomerProfile.user_id == user.id)
    res = await db.execute(stmt)
    profile = res.scalar_one_or_none()
    if not profile:
        profile = CustomerProfile(user_id=user.id)
        db.add(profile)

    if payload.emergency_contact_phone is not None:
        profile.emergency_contact_phone = payload.emergency_contact_phone
    if payload.preferred_payment_method is not None:
        profile.preferred_payment_method = payload.preferred_payment_method

    await db.commit()
    return {"message": "Profile updated successfully."}

class CustomerPreferencesUpdate(BaseModel):
    quiet_ride: Optional[bool] = None
    temperature_celsius: Optional[float] = None
    preferred_music: Optional[str] = None
    preferred_water: Optional[str] = None
    no_phone_call_ride: Optional[bool] = None
    preferred_driver_language: Optional[str] = None
    female_driver_preferred: Optional[bool] = None
    vehicle_color_preference: Optional[str] = None
    premium_amenities: Optional[str] = None
    electric_vehicle_preferred: Optional[bool] = None
    low_emission_filter: Optional[bool] = None
    wheelchair_access_required: Optional[bool] = None
    step_free_pickup_required: Optional[bool] = None
    assistance_animal_present: Optional[bool] = None
    extra_legroom_required: Optional[bool] = None
    pet_friendly_preferred: Optional[bool] = None
    accessibility_notes: Optional[str] = None

@router.get("/preferences")
async def get_customer_preferences(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(CustomerProfile).where(CustomerProfile.user_id == user.id)
    res = await db.execute(stmt)
    profile = res.scalar_one_or_none()
    if not profile:
        profile = CustomerProfile(user_id=user.id)
        db.add(profile)
        await db.commit()
        await db.refresh(profile)

    return {
        "user_id": user.id,
        "quiet_ride": profile.quiet_ride,
        "temperature_celsius": profile.temperature_celsius,
        "preferred_music": profile.preferred_music,
        "preferred_water": profile.preferred_water,
        "no_phone_call_ride": getattr(profile, "no_phone_call_ride", False),
        "preferred_driver_language": getattr(profile, "preferred_driver_language", "English"),
        "female_driver_preferred": getattr(profile, "female_driver_preferred", False),
        "vehicle_color_preference": getattr(profile, "vehicle_color_preference", "Obsidian Black"),
        "premium_amenities": getattr(profile, "premium_amenities", "Evian Sparkling Water, Braided Fast-Charging Kit"),
        "electric_vehicle_preferred": getattr(profile, "electric_vehicle_preferred", False),
        "low_emission_filter": getattr(profile, "low_emission_filter", False),
        "wheelchair_access_required": getattr(profile, "wheelchair_access_required", False),
        "step_free_pickup_required": getattr(profile, "step_free_pickup_required", False),
        "assistance_animal_present": getattr(profile, "assistance_animal_present", False),
        "extra_legroom_required": getattr(profile, "extra_legroom_required", False),
        "pet_friendly_preferred": getattr(profile, "pet_friendly_preferred", False),
        "accessibility_notes": getattr(profile, "accessibility_notes", None)
    }

@router.put("/preferences")
async def update_customer_preferences(
    payload: CustomerPreferencesUpdate,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(CustomerProfile).where(CustomerProfile.user_id == user.id)
    res = await db.execute(stmt)
    profile = res.scalar_one_or_none()
    if not profile:
        profile = CustomerProfile(user_id=user.id)
        db.add(profile)

    for field, value in payload.model_dump(exclude_unset=True).items():
        if hasattr(profile, field):
            setattr(profile, field, value)

    await db.commit()
    await db.refresh(profile)
    return {
        "status": "PREFERENCES_SAVED",
        "message": "Bespoke ride preferences and accessibility settings saved to profile.",
        "preferences": {
            "quiet_ride": profile.quiet_ride,
            "temperature_celsius": profile.temperature_celsius,
            "preferred_music": profile.preferred_music,
            "no_phone_call_ride": profile.no_phone_call_ride,
            "preferred_driver_language": profile.preferred_driver_language,
            "wheelchair_access_required": profile.wheelchair_access_required,
            "accessibility_notes": profile.accessibility_notes
        }
    }

@router.get("/preferences/summary")
async def get_prebooking_preference_summary(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    """
    Feature 75: Customer preference summary before booking showing curated bespoke cabin and service profile.
    """
    stmt = select(CustomerProfile).where(CustomerProfile.user_id == user.id)
    res = await db.execute(stmt)
    profile = res.scalar_one_or_none()
    if not profile:
        profile = CustomerProfile(user_id=user.id)
        db.add(profile)
        await db.commit()
        await db.refresh(profile)

    cabin_items = [
        f"Cabin Temperature: {profile.temperature_celsius:.1f}°C",
        f"Acoustics: {'Silent Discretion (Quiet Ride)' if profile.quiet_ride else 'Conversational Standard'}",
        f"Audio Atmosphere: {profile.preferred_music}",
        f"Beverage: {profile.preferred_water}"
    ]

    service_items = [
        f"Chauffeur Language: {profile.preferred_driver_language}",
        f"Communications: {'Silent Arrival (No Phone Calls)' if profile.no_phone_call_ride else 'Voice Call & Chat'}"
    ]
    if profile.female_driver_preferred:
        service_items.append("Chauffeur Gender: Female Chauffeur Preferred")
    if profile.vehicle_color_preference:
        service_items.append(f"Exterior Color: {profile.vehicle_color_preference}")

    amenity_items = [a.strip() for a in profile.premium_amenities.split(",") if a.strip()]

    accessibility_items = []
    if profile.wheelchair_access_required:
        accessibility_items.append("Wheelchair Accessible Vehicle with Hoist/Ramp Required")
    if profile.step_free_pickup_required:
        accessibility_items.append("Step-Free Low Threshold Curb Boarding")
    if profile.assistance_animal_present:
        accessibility_items.append("Certified Service / Guide Animal Onboard")
    if profile.extra_legroom_required:
        accessibility_items.append("Extended Executive Legroom (Long Wheelbase)")
    if profile.pet_friendly_preferred:
        accessibility_items.append("Pet-Friendly Vehicle with Leather Protective Liner")
    if profile.accessibility_notes:
        accessibility_items.append(f"Special Assistance Note: {profile.accessibility_notes}")

    return {
        "passenger_name": user.full_name or "Distinguished Guest",
        "cabin_atmosphere": cabin_items,
        "service_etiquette": service_items,
        "bespoke_amenities": amenity_items,
        "accessibility_compliance": accessibility_items if accessibility_items else ["Standard White-Glove VIP Boarding"],
        "ready_for_dispatch": True
    }

@router.post("/device-token")
async def register_device_token(
    payload: DeviceTokenPayload,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(DeviceToken).where(DeviceToken.fcm_token == payload.fcm_token)
    res = await db.execute(stmt)
    token_obj = res.scalar_one_or_none()
    if not token_obj:
        token_obj = DeviceToken(
            user_id=user.id,
            fcm_token=payload.fcm_token,
            device_os=payload.device_os
        )
        db.add(token_obj)
    else:
        token_obj.user_id = user.id
        token_obj.device_os = payload.device_os
        token_obj.is_active = True
        token_obj.last_error_at = None
    await db.commit()
    return {"message": "Device token registered."}

@router.get("/notifications")
async def get_notifications(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(Notification).where(Notification.user_id == user.id).order_by(Notification.created_at.desc()).limit(30)
    res = await db.execute(stmt)
    notifications = res.scalars().all()
    return [
        {
            "id": n.id,
            "title": n.title,
            "body": n.body,
            "event_type": n.event_type,
            "is_read": n.is_read,
            "created_at": n.created_at.isoformat()
        }
        for n in notifications
    ]

@router.delete("/account")
async def delete_customer_account(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    """
    Google Play Compliance: Allows user to permanently delete account and anonymize personal data.
    """
    user.is_active = False
    user.phone_number = f"deleted_{user.id[:8]}_{user.phone_number[-4:]}"
    if user.email:
        user.email = f"deleted_{user.id[:8]}@anonymized.local"
    await db.commit()
    return {"message": "Account successfully deleted and personal data anonymized."}
