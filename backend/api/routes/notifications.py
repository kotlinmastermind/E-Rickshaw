"""User notification preferences/inbox and privileged delivery operations."""
from typing import Any, Dict, Optional

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from backend.api.deps import get_current_admin, get_current_user
from backend.application.services.notification_service import NotificationService
from backend.domain.models import User
from backend.infrastructure.database.session import get_db

router = APIRouter(prefix="/notifications", tags=["Notifications"])
admin_router = APIRouter(prefix="/admin/notification-ops", tags=["Admin Notification Operations"])


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


class TokenPayload(BaseModel):
    fcm_token: str = Field(min_length=20, max_length=512)


@router.get("/preferences")
async def get_notification_preferences(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    service = NotificationService(db)
    row = await service.get_or_create_settings(user.id)
    await db.commit()
    return service.settings_payload(row)


@router.put("/preferences")
async def update_notification_preferences(payload: PreferencesPayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await NotificationService(db).update_settings(user.id, payload.model_dump(exclude_unset=True), source="MOBILE_APP")


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


@router.delete("/device-token")
async def unregister_device_token(payload: TokenPayload, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    return await NotificationService(db).unregister_token(user.id, payload.fcm_token)


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
