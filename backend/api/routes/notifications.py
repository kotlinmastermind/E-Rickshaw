"""User notification preferences/inbox and privileged delivery operations."""
from typing import Any, Dict, Optional

from fastapi import APIRouter, Body, Depends, Query, Request
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.api.deps import get_current_admin, get_current_user
from backend.application.services.notification_service import NotificationService
from backend.domain.models import User
from backend.infrastructure.database.session import get_db
from backend.infrastructure.security.rate_limiter import device_token_rate_limiter

router = APIRouter(prefix="/notifications", tags=["Notifications"])
admin_router = APIRouter(prefix="/admin/notification-ops", tags=["Admin Notification Operations"])


class DeviceTokenPayload(BaseModel):
    fcm_token: str = Field(..., min_length=8, max_length=512)
    device_os: str = Field("ANDROID", max_length=20)
    platform: Optional[str] = Field(None, max_length=20)
    user_id: Optional[str] = Field(None, max_length=64)
    device_label: Optional[str] = Field(None, max_length=64)


class DeviceTokenDeactivatePayload(BaseModel):
    fcm_token: Optional[str] = Field(None, min_length=8, max_length=512)


class PreferencesPayload(BaseModel):
    push_enabled: Optional[bool] = None
    email_enabled: Optional[bool] = None
    sms_enabled: Optional[bool] = None
    quiet_hours_enabled: Optional[bool] = None
    quiet_start: Optional[str] = Field(None, min_length=5, max_length=5)
    quiet_end: Optional[str] = Field(None, min_length=5, max_length=5)
    timezone: Optional[str] = Field(None, min_length=1, max_length=64)
    language: Optional[str] = None


class TemplatePayload(BaseModel):
    template_key: str = Field(min_length=3, max_length=100)
    locale: str = "en"
    channel: str = "PUSH"
    title_template: str = Field(min_length=1, max_length=128)
    body_template: str = Field(min_length=1, max_length=5000)
    required_variables: list[str] = Field(default_factory=list, max_length=30)
    is_active: bool = True


class TemplatePreviewPayload(BaseModel):
    locale: str = "en"
    channel: str = "PUSH"
    variables: Dict[str, Any] = Field(default_factory=dict)


@router.get("/preferences")
async def get_notification_preferences(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    service = NotificationService(db)
    row = await service.get_or_create_settings(user.id)
    await db.commit()
    return service.settings_payload(row)


@router.put("/preferences")
async def update_notification_preferences(payload: PreferencesPayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await NotificationService(db).update_settings(user.id, payload.model_dump(exclude_unset=True), source="MOBILE_APP")


@router.get("")
@router.get("/")
@router.get("/inbox")
async def get_notification_inbox(
    unread_only: bool = False,
    event_type: Optional[str] = Query(None, max_length=64),
    page: int = Query(1, ge=1),
    limit: int = Query(30, ge=1, le=100),
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db),
):
    return await NotificationService(db).inbox(user.id, unread_only, event_type, page, limit)


@router.patch("/inbox/{notification_id}/read")
async def mark_notification_read(notification_id: str, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await NotificationService(db).mark_read(user.id, notification_id)


@router.patch("/inbox/{notification_id}/unread")
async def mark_notification_unread(notification_id: str, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await NotificationService(db).mark_read(user.id, notification_id, False)


@router.post("/inbox/read-all")
async def mark_all_notifications_read(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await NotificationService(db).mark_all_read(user.id)


@router.get("/inbox/{notification_id}/receipts")
async def get_notification_receipts(notification_id: str, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await NotificationService(db).receipts(user.id, notification_id)


@router.post("/device-token")
async def register_device_token(
    request: Request,
    payload: DeviceTokenPayload,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    request.state.user = user
    await device_token_rate_limiter(request)
    return await NotificationService(db).register_token(
        user_id=user.id,
        fcm_token=payload.fcm_token,
        device_os=payload.platform or payload.device_os,
        target_user_id=payload.user_id,
        actor_role=user.role,
        device_label=payload.device_label,
    )


@router.get("/device-tokens")
async def list_device_tokens(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    return await NotificationService(db).list_device_registrations(user.id)


@router.delete("/device-token")
async def deactivate_device_token(
    request: Request,
    fcm_token: Optional[str] = Query(None, max_length=512),
    payload: Optional[DeviceTokenDeactivatePayload] = Body(default=None),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    request.state.user = user
    await device_token_rate_limiter(request)
    token = (payload.fcm_token if payload and payload.fcm_token else fcm_token)
    return await NotificationService(db).deactivate_token(
        user_id=user.id,
        fcm_token=token,
        actor_role=user.role,
    )


@admin_router.get("/dashboard")
async def notification_dashboard(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await NotificationService(db).admin_dashboard()


@admin_router.get("/consent-audit")
async def notification_consent_audit(limit: int = Query(200, ge=1, le=500), admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await NotificationService(db).consent_audit(limit)


@admin_router.get("/templates")
async def notification_templates(admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await NotificationService(db).list_templates()


@admin_router.put("/templates")
async def save_notification_template(payload: TemplatePayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await NotificationService(db).save_template(admin.id, payload.model_dump())


@admin_router.post("/templates/{template_key}/preview")
async def preview_notification_template(template_key: str, payload: TemplatePreviewPayload, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await NotificationService(db).preview_template(template_key, payload.locale, payload.channel, payload.variables)


@admin_router.post("/notifications/{notification_id}/replay")
async def replay_notification(notification_id: str, admin: User = Depends(get_current_admin), db: AsyncSession = Depends(get_db)):
    return await NotificationService(db).replay(admin.id, notification_id)
