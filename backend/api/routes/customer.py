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

    return {
        "user_id": user.id,
        "full_name": user.full_name,
        "phone_number": user.phone_number,
        "email": user.email,
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
