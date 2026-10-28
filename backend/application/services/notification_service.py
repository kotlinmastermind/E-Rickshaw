"""Persisted notification inbox, consent, delivery receipts and retry operations."""
import json
import re
from datetime import datetime, time, timedelta, timezone
from typing import Any, Dict, Optional
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from fastapi import HTTPException
from sqlalchemy import and_, desc, func, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from backend.domain.models import (
    AdministrativeAuditLog, DeviceToken, Notification, NotificationConsentAudit,
    NotificationDeliveryAttempt, NotificationTemplate, User, UserNotificationSettings,
    UserPreferences,
)
from backend.integrations.notifications.provider import get_notification_provider, get_notification_provider_health

LANGUAGES = {"en", "hi", "mr", "gu"}
CHANNELS = {"PUSH", "EMAIL", "SMS", "IN_APP"}
CRITICAL_EVENTS = {"SAFETY_CASE_OUTCOME", "EMERGENCY_ALERT", "DRIVER_EMERGENCY", "RIDE_SECURITY_ALERT", "EMERGENCY_ANNOUNCEMENT"}
ACTION_PREFIXES = ("/rides/", "/shared-rides/", "/support/", "/safety/", "/payments/")
VARIABLE_PATTERN = re.compile(r"{{\s*([a-zA-Z][a-zA-Z0-9_]*)\s*}}")


class NotificationService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_or_create_settings(self, user_id: str) -> UserNotificationSettings:
        row = (await self.db.execute(select(UserNotificationSettings).where(UserNotificationSettings.user_id == user_id))).scalar_one_or_none()
        if row: return row
        preferences = (await self.db.execute(select(UserPreferences).where(UserPreferences.user_id == user_id))).scalar_one_or_none()
        row = UserNotificationSettings(user_id=user_id, language_code=(preferences.preferred_language if preferences else "en") or "en")
        self.db.add(row)
        await self.db.flush()
        return row

    @staticmethod
    def settings_payload(row: UserNotificationSettings) -> Dict[str, Any]:
        return {"push_enabled": row.push_enabled, "email_enabled": row.email_enabled, "sms_enabled": row.sms_enabled,
                "quiet_hours_enabled": row.quiet_hours_enabled, "quiet_start": row.quiet_start,
                "quiet_end": row.quiet_end, "timezone": row.timezone_name, "language": row.language_code,
                "updated_at": row.updated_at.isoformat() if row.updated_at else None,
                "provider_boundary": "In-app inbox is always retained. Push requires configured Firebase credentials; email and SMS providers are not configured."}

    async def update_settings(self, user_id: str, updates: Dict[str, Any], source: str = "MOBILE_APP") -> Dict[str, Any]:
        allowed = {"push_enabled", "email_enabled", "sms_enabled", "quiet_hours_enabled", "quiet_start", "quiet_end", "timezone", "language"}
        if set(updates) - allowed: raise HTTPException(422, "Unsupported notification preference.")
        for key in ("quiet_start", "quiet_end"):
            if key in updates and not re.fullmatch(r"(?:[01]\d|2[0-3]):[0-5]\d", str(updates[key])):
                raise HTTPException(422, f"{key} must use 24-hour HH:MM time.")
        if "language" in updates and updates["language"] not in LANGUAGES:
            raise HTTPException(422, "Choose a supported notification language: en, hi, mr, or gu.")
        if "timezone" in updates:
            try: ZoneInfo(str(updates["timezone"]))
            except ZoneInfoNotFoundError: raise HTTPException(422, "Choose a valid IANA time zone.")
            if len(str(updates["timezone"])) > 64: raise HTTPException(422, "Time zone name is too long.")

        row = await self.get_or_create_settings(user_id)
        changes: Dict[str, Dict[str, Any]] = {}
        for key, value in updates.items():
            attr = "timezone_name" if key == "timezone" else "language_code" if key == "language" else key
            old = getattr(row, attr)
            if old != value:
                changes[key] = {"before": old, "after": value}
                setattr(row, attr, value)
        if "language" in updates:
            prefs = (await self.db.execute(select(UserPreferences).where(UserPreferences.user_id == user_id))).scalar_one_or_none()
            if prefs and prefs.preferred_language != updates["language"]:
                changes.setdefault("language", {"before": prefs.preferred_language, "after": updates["language"]})
                prefs.preferred_language = updates["language"]
        if changes:
            self.db.add(NotificationConsentAudit(user_id=user_id, source=source, changes_json=json.dumps(changes, ensure_ascii=False)))
        await self.db.commit()
        await self.db.refresh(row)
        return self.settings_payload(row)

    async def inbox(self, user_id: str, unread_only: bool = False, event_type: Optional[str] = None,
                    page: int = 1, limit: int = 30) -> Dict[str, Any]:
        stmt = select(Notification).where(Notification.user_id == user_id)
        if unread_only: stmt = stmt.where(Notification.is_read.is_(False))
        if event_type: stmt = stmt.where(Notification.event_type == event_type)
        rows = (await self.db.execute(stmt.order_by(desc(Notification.created_at)).offset((page - 1) * limit).limit(limit))).scalars().all()
        total = int(await self.db.scalar(select(func.count(Notification.id)).where(
            Notification.user_id == user_id, *([Notification.is_read.is_(False)] if unread_only else []),
            *([Notification.event_type == event_type] if event_type else []))) or 0)
        unread = int(await self.db.scalar(select(func.count(Notification.id)).where(Notification.user_id == user_id, Notification.is_read.is_(False)) ) or 0)
        return {"items": [self.notification_payload(n) for n in rows], "page": page, "limit": limit, "total": total, "unread_count": unread}

    @staticmethod
    def notification_payload(row: Notification) -> Dict[str, Any]:
        return {"id": row.id, "title": row.title, "body": row.body, "event_type": row.event_type,
                "is_read": row.is_read, "channel": row.channel, "delivery_status": row.delivery_status,
                "critical": row.critical, "action_url": row.action_url,
                "created_at": row.created_at.isoformat() if row.created_at else None,
                "sent_at": row.sent_at.isoformat() if row.sent_at else None}

    async def mark_read(self, user_id: str, notification_id: str, read: bool = True) -> Dict[str, Any]:
        row = (await self.db.execute(select(Notification).where(Notification.id == notification_id, Notification.user_id == user_id))).scalar_one_or_none()
        if not row: raise HTTPException(404, "Notification not found.")
        row.is_read = read
        await self.db.commit()
        return {"id": row.id, "is_read": row.is_read}

    async def mark_all_read(self, user_id: str) -> Dict[str, int]:
        rows = (await self.db.execute(select(Notification).where(Notification.user_id == user_id, Notification.is_read.is_(False)))).scalars().all()
        for row in rows: row.is_read = True
        await self.db.commit()
        return {"updated_count": len(rows)}

    async def receipts(self, user_id: str, notification_id: str) -> list[Dict[str, Any]]:
        notification = (await self.db.execute(select(Notification.id).where(Notification.id == notification_id, Notification.user_id == user_id))).scalar_one_or_none()
        if not notification: raise HTTPException(404, "Notification not found.")
        rows = (await self.db.execute(select(NotificationDeliveryAttempt).where(NotificationDeliveryAttempt.notification_id == notification_id)
            .order_by(NotificationDeliveryAttempt.attempted_at))).scalars().all()
        return [{"channel": r.channel, "attempt_number": r.attempt_number, "status": r.status,
                 "provider_message_id": r.provider_message_id, "error_code": r.error_code,
                 "attempted_at": r.attempted_at.isoformat(), "completed_at": r.completed_at.isoformat() if r.completed_at else None} for r in rows]

    async def unregister_token(self, user_id: str, token: str) -> Dict[str, Any]:
        row = (await self.db.execute(select(DeviceToken).where(DeviceToken.user_id == user_id, DeviceToken.fcm_token == token))).scalar_one_or_none()
        if row:
            await self.db.delete(row)
            await self.db.commit()
        return {"removed": bool(row)}

    async def create_notification(self, user_id: str, event_type: str, title: str, body: str,
                                  data: Optional[Dict[str, Any]] = None, critical: bool = False,
                                  dedupe_key: Optional[str] = None, action_url: Optional[str] = None,
                                  scheduled_at: Optional[datetime] = None) -> Notification:
        if action_url and (not action_url.startswith(ACTION_PREFIXES) or action_url.startswith("//") or "\\" in action_url):
            raise HTTPException(422, "Notification action must be an approved relative app path.")
        if dedupe_key:
            prior = (await self.db.execute(select(Notification).where(Notification.dedupe_key == dedupe_key))).scalar_one_or_none()
            if prior: return prior
        critical = critical or event_type in CRITICAL_EVENTS
        row = Notification(user_id=user_id, event_type=event_type, title=title[:128], body=body,
                           data_json=json.dumps(data or {}, ensure_ascii=False), critical=critical,
                           dedupe_key=dedupe_key, action_url=action_url, scheduled_at=scheduled_at,
                           next_attempt_at=scheduled_at, delivery_status="QUEUED", channel="IN_APP")
        self.db.add(row)
        try:
            await self.db.flush()
        except IntegrityError:
            await self.db.rollback()
            if dedupe_key:
                prior = (await self.db.execute(select(Notification).where(Notification.dedupe_key == dedupe_key))).scalar_one_or_none()
                if prior: return prior
            raise
        return row

    @staticmethod
    def _quiet_end(row: UserNotificationSettings, now: datetime) -> Optional[datetime]:
        if not row.quiet_hours_enabled: return None
        zone = ZoneInfo(row.timezone_name)
        local_now = now.astimezone(zone)
        start = time.fromisoformat(row.quiet_start)
        end = time.fromisoformat(row.quiet_end)
        current = local_now.timetz().replace(tzinfo=None)
        in_quiet = (current >= start or current < end) if start > end else start <= current < end
        if not in_quiet: return None
        end_date = local_now.date() + (timedelta(days=1) if current >= start and start > end else timedelta(days=0))
        target = datetime.combine(end_date, end, tzinfo=zone)
        if target <= local_now: target += timedelta(days=1)
        return target.astimezone(timezone.utc)

    async def process_due(self, limit: int = 100) -> int:
        now = datetime.now(timezone.utc)
        rows = (await self.db.execute(select(Notification).where(
            Notification.delivery_status.in_(["QUEUED", "RETRY"]),
            or_(Notification.next_attempt_at.is_(None), Notification.next_attempt_at <= now),
            or_(Notification.scheduled_at.is_(None), Notification.scheduled_at <= now),
        ).order_by(Notification.created_at).limit(limit).with_for_update(skip_locked=True))).scalars().all()
        processed = 0
        for row in rows:
            try:
                await self._deliver(row, now)
                processed += 1
            except Exception as exc:
                row.last_error = str(exc)[:500]
                row.attempt_count += 1
                row.last_attempt_at = now
                row.delivery_status = "FAILED" if row.attempt_count >= 5 else "RETRY"
                row.next_attempt_at = now + timedelta(minutes=min(60, 2 ** row.attempt_count))
                self.db.add(NotificationDeliveryAttempt(notification_id=row.id, channel="PUSH", attempt_number=row.attempt_count,
                    status="FAILED", error_code="WORKER_ERROR", error_message=str(exc)[:500], attempted_at=now, completed_at=now))
        await self.db.commit()
        return processed

    async def _deliver(self, row: Notification, now: datetime) -> None:
        settings = await self.get_or_create_settings(row.user_id)
        critical = row.critical or row.event_type in CRITICAL_EVENTS
        if not critical:
            deferred = self._quiet_end(settings, now)
            if deferred:
                row.next_attempt_at = deferred
                return
        push_allowed = settings.push_enabled or critical
        email_allowed = settings.email_enabled
        sms_allowed = settings.sms_enabled
        if not any((push_allowed, email_allowed, sms_allowed)):
            row.delivery_status = "IN_APP_ONLY"
            row.sent_at = now
            row.last_error = None
            return

        attempted_count = int(await self.db.scalar(select(func.count(NotificationDeliveryAttempt.id)).where(
            NotificationDeliveryAttempt.notification_id == row.id)) or 0)
        row.attempt_count = max(row.attempt_count, attempted_count) + 1
        row.last_attempt_at = now
        if not critical:
            rate_count = int(await self.db.scalar(select(func.count(NotificationDeliveryAttempt.id)).where(
                NotificationDeliveryAttempt.notification_id.in_(select(Notification.id).where(Notification.user_id == row.user_id)),
                NotificationDeliveryAttempt.channel == "PUSH",
                NotificationDeliveryAttempt.status.in_(["SENT", "FAILED", "THROTTLED"]),
                NotificationDeliveryAttempt.attempted_at >= now - timedelta(hours=1))) or 0)
            if push_allowed and rate_count >= 20:
                self.db.add(NotificationDeliveryAttempt(notification_id=row.id, channel="PUSH", attempt_number=row.attempt_count,
                    status="THROTTLED", error_code="USER_RATE_CAP", error_message="Push rate cap reached for the rolling hour.", attempted_at=now))
                row.delivery_status = "RETRY"
                row.next_attempt_at = now + timedelta(minutes=15)
                return

        errors = []
        delivered = False
        if push_allowed:
            tokens = (await self.db.execute(select(DeviceToken).where(DeviceToken.user_id == row.user_id, DeviceToken.is_active.is_(True)))).scalars().all()
            if not tokens:
                self.db.add(NotificationDeliveryAttempt(notification_id=row.id, channel="PUSH", attempt_number=row.attempt_count,
                    status="UNAVAILABLE", error_code="NO_ACTIVE_TOKEN", error_message="No active push token is registered.", attempted_at=now, completed_at=now))
                errors.append("No active push token is registered.")
                row.delivery_status, row.sent_at, row.last_error, row.next_attempt_at = "IN_APP_ONLY", now, None, None
                return
            else:
                provider = get_notification_provider()
                try: data = json.loads(row.data_json or "{}")
                except (ValueError, TypeError): data = {}
                if row.action_url: data["action_url"] = row.action_url
                for token in tokens:
                    sent = await provider.send_push_notification(token.fcm_token, row.title, row.body, data)
                    code = getattr(provider, "last_error_code", None)
                    self.db.add(NotificationDeliveryAttempt(notification_id=row.id, channel="PUSH", attempt_number=row.attempt_count,
                        status="SENT" if sent else "FAILED", provider_message_id=getattr(provider, "last_message_id", None) if sent else None,
                        error_code=code if not sent else None, error_message=None if sent else getattr(provider, "last_error", "Push provider unavailable")[:500], attempted_at=now, completed_at=now))
                    if sent: delivered = True
                    else:
                        errors.append(getattr(provider, "last_error", "Push provider unavailable"))
                        if code == "UNREGISTERED":
                            token.is_active = False
                            token.last_error_at = now
        # Email and SMS are deliberately recorded as unavailable until a real provider is configured.
        for channel, enabled in (("EMAIL", email_allowed), ("SMS", sms_allowed)):
            if enabled:
                previously_recorded = await self.db.scalar(select(NotificationDeliveryAttempt.id).where(
                    NotificationDeliveryAttempt.notification_id == row.id,
                    NotificationDeliveryAttempt.channel == channel,
                    NotificationDeliveryAttempt.error_code == "PROVIDER_NOT_CONFIGURED").limit(1))
                if not previously_recorded:
                    self.db.add(NotificationDeliveryAttempt(notification_id=row.id, channel=channel, attempt_number=row.attempt_count,
                        status="UNAVAILABLE", error_code="PROVIDER_NOT_CONFIGURED",
                        error_message=f"{channel.title()} delivery provider is not configured.", attempted_at=now, completed_at=now))
        if not push_allowed and (email_allowed or sms_allowed):
            row.delivery_status, row.sent_at = "IN_APP_ONLY", now
            row.last_error = "Email/SMS delivery providers are not configured; the inbox copy remains available."
            row.next_attempt_at = None
            return
        if delivered:
            row.delivery_status, row.sent_at, row.last_error, row.next_attempt_at = "DELIVERED", now, None, None
        elif row.attempt_count >= 5:
            row.delivery_status, row.last_error, row.next_attempt_at = "FAILED", "; ".join(errors)[:500], None
        else:
            row.delivery_status = "RETRY"
            row.last_error = "; ".join(errors)[:500] or "No configured delivery channel succeeded."
            row.next_attempt_at = now + timedelta(minutes=min(60, 2 ** row.attempt_count))

    @staticmethod
    def _render(title: str, body: str, required: list[str], variables: Dict[str, Any]) -> Dict[str, str]:
        detected = set(VARIABLE_PATTERN.findall(title + "\n" + body))
        missing = detected - set(variables)
        unknown = set(variables) - detected
        if set(required) - detected:
            raise HTTPException(422, "Required variable list contains names not used by the template.")
        if missing or unknown:
            raise HTTPException(422, {"message": "Template variables are incomplete.", "missing": sorted(missing), "unresolved": sorted(unknown)})
        if any(value is None or len(str(value)) > 500 for value in variables.values()):
            raise HTTPException(422, "Template values must be present and at most 500 characters.")
        def replace(match): return str(variables[match.group(1)])
        return {"title": VARIABLE_PATTERN.sub(replace, title), "body": VARIABLE_PATTERN.sub(replace, body)}

    async def list_templates(self) -> list[Dict[str, Any]]:
        rows = (await self.db.execute(select(NotificationTemplate).order_by(NotificationTemplate.template_key, NotificationTemplate.locale, NotificationTemplate.channel))).scalars().all()
        return [{"id": r.id, "template_key": r.template_key, "locale": r.locale, "channel": r.channel,
                 "title_template": r.title_template, "body_template": r.body_template,
                 "required_variables": json.loads(r.required_variables_json), "is_active": r.is_active} for r in rows]

    async def save_template(self, admin_id: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        key, locale, channel = payload["template_key"].strip(), payload["locale"].lower(), payload["channel"].upper()
        title, body = payload["title_template"].strip(), payload["body_template"].strip()
        required = payload.get("required_variables", [])
        if not re.fullmatch(r"[a-z][a-z0-9_.-]{2,99}", key): raise HTTPException(422, "Template key must use lowercase letters, digits, dots, dashes or underscores.")
        if locale not in LANGUAGES or channel not in CHANNELS: raise HTTPException(422, "Choose a supported locale and channel.")
        if not title or len(title) > 128 or not body or len(body) > 5000: raise HTTPException(422, "Template title/body exceeds its allowed length.")
        if not isinstance(required, list) or any(not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_]*", str(v)) for v in required): raise HTTPException(422, "Required variables must be valid variable names.")
        if set(VARIABLE_PATTERN.findall(title + "\n" + body)) != set(required): raise HTTPException(422, "Required variables must exactly match the template placeholders.")
        self._render(title, body, required, {name: "preview" for name in required})
        row = (await self.db.execute(select(NotificationTemplate).where(NotificationTemplate.template_key == key,
            NotificationTemplate.locale == locale, NotificationTemplate.channel == channel))).scalar_one_or_none()
        if not row:
            row = NotificationTemplate(template_key=key, locale=locale, channel=channel, updated_by=admin_id)
            self.db.add(row)
        row.title_template, row.body_template = title, body
        row.required_variables_json, row.is_active, row.updated_by = json.dumps(required), bool(payload.get("is_active", True)), admin_id
        await self.db.flush()
        self.db.add(AdministrativeAuditLog(admin_user_id=admin_id, action="NOTIFICATION_TEMPLATE_SAVED", target_resource="notification_templates", target_id=row.id,
            changes_json=json.dumps({"template_key": key, "locale": locale, "channel": channel})))
        await self.db.commit()
        return {"id": row.id, "template_key": key, "locale": locale, "channel": channel, "is_active": row.is_active}

    async def preview_template(self, key: str, locale: str, channel: str, variables: Dict[str, Any]) -> Dict[str, Any]:
        channel = channel.upper()
        row = (await self.db.execute(select(NotificationTemplate).where(NotificationTemplate.template_key == key,
            NotificationTemplate.locale == locale.lower(), NotificationTemplate.channel == channel,
            NotificationTemplate.is_active.is_(True)))).scalar_one_or_none()
        if not row and locale.lower() != "en":
            row = (await self.db.execute(select(NotificationTemplate).where(NotificationTemplate.template_key == key,
                NotificationTemplate.locale == "en", NotificationTemplate.channel == channel,
                NotificationTemplate.is_active.is_(True)))).scalar_one_or_none()
        if not row: raise HTTPException(404, "No active template exists for this locale/channel or English fallback.")
        rendered = self._render(row.title_template, row.body_template, json.loads(row.required_variables_json), variables)
        return {**rendered, "template_key": key, "requested_locale": locale, "resolved_locale": row.locale,
                "channel": channel, "provider_health": await get_notification_provider_health(),
                "delivery_boundary": "Preview only; no message was sent."}

    async def admin_dashboard(self) -> Dict[str, Any]:
        since = datetime.now(timezone.utc) - timedelta(hours=24)
        counts = (await self.db.execute(select(Notification.delivery_status, func.count(Notification.id))
            .where(Notification.created_at >= since).group_by(Notification.delivery_status))).all()
        failed = (await self.db.execute(select(Notification).where(Notification.delivery_status == "FAILED")
            .order_by(desc(Notification.last_attempt_at)).limit(100))).scalars().all()
        attempts = int(await self.db.scalar(select(func.count(NotificationDeliveryAttempt.id)).where(NotificationDeliveryAttempt.attempted_at >= since)) or 0)
        return {"window_hours": 24, "status_counts": {str(k): int(v) for k, v in counts}, "attempts": attempts,
                "failed_notifications": [self.notification_payload(n) | {"user_id": n.user_id, "attempt_count": n.attempt_count, "last_error": n.last_error} for n in failed],
                "providers": await get_notification_provider_health(),
                "message": "In-app receipts reflect persisted inbox records. Email/SMS are unavailable until providers are configured."}

    async def consent_audit(self, limit: int = 200) -> list[Dict[str, Any]]:
        rows = (await self.db.execute(select(NotificationConsentAudit).order_by(desc(NotificationConsentAudit.created_at)).limit(limit))).scalars().all()
        return [{"id": r.id, "user_id": r.user_id, "source": r.source, "changes": json.loads(r.changes_json), "created_at": r.created_at.isoformat()} for r in rows]

    async def replay(self, admin_id: str, notification_id: str) -> Dict[str, Any]:
        row = await self.db.get(Notification, notification_id)
        if not row: raise HTTPException(404, "Notification not found.")
        if row.delivery_status != "FAILED": raise HTTPException(409, "Only failed notification events can be replayed.")
        if row.attempt_count >= 5: raise HTTPException(429, "Retry limit reached; investigate provider health before resetting this event.")
        row.delivery_status, row.next_attempt_at, row.last_error = "QUEUED", datetime.now(timezone.utc), None
        self.db.add(AdministrativeAuditLog(admin_user_id=admin_id, action="NOTIFICATION_EVENT_REPLAYED", target_resource="notifications", target_id=row.id,
            changes_json=json.dumps({"user_id": row.user_id, "previous_attempts": row.attempt_count})))
        await self.db.commit()
        return {"id": row.id, "delivery_status": row.delivery_status, "message": "Replay queued for the delivery worker."}
