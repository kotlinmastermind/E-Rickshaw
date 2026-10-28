from typing import Optional
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.infrastructure.database.session import get_db
from backend.domain.models import User, Session, AdminUserRole, AdminRole, AdminRolePermission, AdminPermission
from backend.infrastructure.security.crypto import decode_jwt_token

security_scheme = HTTPBearer(auto_error=True)

async def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security_scheme),
    db: AsyncSession = Depends(get_db)
) -> User:
    token = credentials.credentials
    try:
        payload = decode_jwt_token(token)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid, expired, or malformed authentication credentials.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    user_id = payload.get("sub")
    session_id = payload.get("session_id")
    token_type = payload.get("token_type", "access")
    if not user_id or not session_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token claims.")
    if token_type != "access":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token type for API access. Access token required.")

    # High-performance distributed revocation check via Redis
    from backend.infrastructure.cache.redis_client import redis_client
    if await redis_client.exists(f"revoked_session:{session_id}") or await redis_client.exists(f"revoked_user:{user_id}"):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Session has been revoked.")

    # Validate active unrevoked session in DB
    session_stmt = select(Session).where(Session.id == session_id, Session.is_revoked == False)
    session_res = await db.execute(session_stmt)
    active_session = session_res.scalar_one_or_none()
    if not active_session:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Session has expired or was revoked.")

    user_stmt = select(User).where(User.id == user_id, User.is_active == True)
    user_res = await db.execute(user_stmt)
    user = user_res.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User account is inactive or not found.")

    return user

def require_role(required_role: str):
    async def role_checker(user: User = Depends(get_current_user)) -> User:
        if user.role != required_role and user.role != "admin":
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Access denied. Requires '{required_role}' authorization."
            )
        return user
    return role_checker

async def get_current_customer(user: User = Depends(require_role("customer"))) -> User:
    return user

async def get_current_driver(user: User = Depends(require_role("driver"))) -> User:
    return user

_ADMIN_PERMISSION_PATHS = {
    ("GET", "/admin/users"): "admin.users.read",
    ("PUT", "/admin/users/{user_id}/status"): "admin.users.suspend",
    ("POST", "/admin/users/bulk-status"): "admin.bulk.execute",
    ("POST", "/admin/users/{user_id}/roles"): "admin.roles.manage",
    ("GET", "/admin/roles"): "admin.roles.manage",
    ("GET", "/admin/roles/permissions"): "admin.roles.manage",
    ("POST", "/admin/roles"): "admin.roles.manage",
    ("PUT", "/admin/roles/{role_id}"): "admin.roles.manage",
    ("GET", "/admin/sessions"): "admin.sessions.manage",
    ("DELETE", "/admin/sessions/{session_id}"): "admin.sessions.manage",
    ("GET", "/admin/drivers"): "admin.drivers.read",
    ("PUT", "/admin/drivers/{driver_id}/approval"): "admin.drivers.approve",
    ("GET", "/admin/drivers/{driver_id}/documents"): "admin.drivers.documents",
    ("GET", "/admin/driver-documents/{document_id}/content"): "admin.drivers.documents",
    ("PUT", "/admin/driver-documents/{document_id}/review"): "admin.drivers.documents",
    ("GET", "/admin/rejection-templates"): "admin.drivers.approve",
    ("POST", "/admin/rejection-templates"): "admin.drivers.approve",
    ("GET", "/admin/rides"): "admin.rides.read",
    ("GET", "/admin/rides/{ride_id}"): "admin.rides.read",
    ("POST", "/admin/rides/{ride_id}/assign"): "admin.rides.assign",
    ("POST", "/admin/rides/{ride_id}/cancel"): "admin.rides.cancel",
    ("GET", "/admin/fares"): "admin.fares.read",
    ("POST", "/admin/fares"): "admin.fares.draft",
    ("GET", "/admin/fares/versions"): "admin.fares.read",
    ("GET", "/admin/fare-drafts"): "admin.fares.read",
    ("POST", "/admin/fare-drafts"): "admin.fares.draft",
    ("POST", "/admin/fare-drafts/{rule_id}/approve"): "admin.fares.approve",
    ("POST", "/admin/fare-drafts/{rule_id}/activate"): "admin.fares.activate",
    ("GET", "/admin/vehicle-categories"): "admin.categories.manage",
    ("POST", "/admin/vehicle-categories"): "admin.categories.manage",
    ("PUT", "/admin/vehicle-categories/{category_id}"): "admin.categories.manage",
    ("GET", "/admin/geofences"): "admin.zones.manage",
    ("GET", "/admin/geofences/export"): "admin.geofences.manage",
    ("POST", "/admin/geofences/import"): "admin.geofences.manage",
    ("GET", "/admin/geofences/audit"): "admin.zones.manage",
    ("POST", "/admin/geofences"): "admin.geofences.manage",
    ("POST", "/admin/geofences/preview"): "admin.geofences.manage",
    ("PUT", "/admin/geofences/{zone_id}"): "admin.geofences.manage",
    ("DELETE", "/admin/geofences/{zone_id}"): "admin.geofences.manage",
    ("GET", "/admin/calendar"): "admin.calendar.manage",
    ("POST", "/admin/calendar"): "admin.calendar.manage",
    ("DELETE", "/admin/calendar/{event_id}"): "admin.calendar.manage",
    ("POST", "/admin/surge/preview"): "admin.surge.manage",
    ("POST", "/admin/surge/rules"): "admin.surge.manage",
    ("GET", "/admin/surge/rules"): "admin.surge.manage",
    ("GET", "/admin/records/{subject_type}/{subject_id}/notes"): "admin.notes.manage",
    ("POST", "/admin/records/{subject_type}/{subject_id}/notes"): "admin.notes.manage",
    ("GET", "/admin/financeops/payouts"): "admin.finance.payouts.read",
    ("GET", "/admin/financeops/payouts/reconciliation"): "admin.finance.payouts.read",
    ("POST", "/admin/financeops/payouts/{payout_id}/investigate"): "admin.finance.payouts.approve",
    ("POST", "/admin/financeops/payouts/{payout_id}/approve"): "admin.finance.payouts.approve",
    ("GET", "/admin/commissions"): "admin.finance.commissions.manage",
    ("POST", "/admin/commissions"): "admin.finance.commissions.manage",
    ("GET", "/admin/broadcasts"): "admin.broadcasts.manage",
    ("POST", "/admin/broadcasts"): "admin.broadcasts.manage",
    ("GET", "/admin/refunds"): "admin.finance.refunds.read",
    ("GET", "/admin/support/tickets"): "admin.support.manage",
    ("PUT", "/admin/support/tickets/{ticket_id}/status"): "admin.support.manage",
    ("POST", "/admin/support/tickets/{ticket_id}/internal-note"): "admin.support.collaborate",
    ("POST", "/admin/financeops/commission-impact"): "admin.finance.commissions.manage",
    ("GET", "/admin/financeops/commissions"): "admin.finance.commissions.manage",
    ("POST", "/admin/financeops/commissions"): "admin.finance.commissions.manage",
    ("POST", "/admin/financeops/commissions/{rule_id}/review"): "admin.finance.commissions.manage",
    ("GET", "/admin/payouts"): "admin.finance.payouts.read",
    ("POST", "/admin/payouts/{payout_id}/approve"): "admin.finance.payouts.approve",
    ("POST", "/admin/payouts/{payout_id}/reject"): "admin.finance.payouts.approve",
    ("POST", "/admin/payouts/{payout_id}/settle"): "admin.finance.payouts.approve",
    ("GET", "/admin/financeops/refunds"): "admin.finance.refunds.read",
    ("POST", "/admin/financeops/refunds"): "admin.finance.refunds.approve",
    ("POST", "/admin/financeops/refunds/{refund_id}/review"): "admin.finance.refunds.approve",
    ("GET", "/admin/financeops/payment-exceptions"): "admin.finance.refunds.read",
    ("GET", "/admin/financeops/webhooks"): "admin.finance.webhooks.replay",
    ("POST", "/admin/financeops/webhooks/{inbox_id}/replay"): "admin.finance.webhooks.replay",
    ("GET", "/admin/financeops/wallet-adjustments"): "admin.finance.wallet.read",
    ("POST", "/admin/financeops/wallet-adjustments"): "admin.finance.wallet.request",
    ("POST", "/admin/financeops/wallet-adjustments/{request_id}/approve"): "admin.finance.wallet.approve",
    ("GET", "/admin/financeops/ledger.csv"): "admin.finance.ledger.export",
    ("GET", "/admin/financeops/invoice-corrections"): "admin.finance.invoices.correct",
    ("POST", "/admin/financeops/invoice-corrections"): "admin.finance.invoices.correct",
    ("POST", "/admin/financeops/invoice-corrections/{request_id}/review"): "admin.finance.invoices.correct",
    ("GET", "/admin/supportops/tickets"): "admin.support.manage",
    ("GET", "/admin/supportops/workload"): "admin.support.manage",
    ("POST", "/admin/supportops/tickets/{ticket_id}/assign"): "admin.support.manage",
    ("GET", "/admin/supportops/tickets/{ticket_id}/internal-notes"): "admin.support.collaborate",
    ("POST", "/admin/supportops/tickets/{ticket_id}/internal-notes"): "admin.support.collaborate",
    ("POST", "/admin/supportops/tickets/{ticket_id}/templates/{template_id}/send"): "admin.support.manage",
    ("GET", "/admin/supportops/templates"): "admin.support.templates.manage",
    ("POST", "/admin/supportops/templates"): "admin.support.templates.manage",
    ("GET", "/admin/supportops/escalation-policies"): "admin.support.policies.manage",
    ("POST", "/admin/supportops/escalation-policies"): "admin.support.policies.manage",
    ("POST", "/admin/supportops/tickets/{ticket_id}/escalate"): "admin.support.manage",
    ("POST", "/admin/communications/preview"): "admin.broadcasts.manage",
    ("GET", "/admin/communications/broadcasts"): "admin.broadcasts.manage",
    ("POST", "/admin/communications/broadcasts"): "admin.broadcasts.manage",
    ("GET", "/admin/communications/opt-outs"): "admin.broadcasts.optout.read",
    ("GET", "/admin/notification-ops/dashboard"): "admin.notifications.read",
    ("GET", "/admin/notification-ops/consent-audit"): "admin.notifications.read",
    ("GET", "/admin/notification-ops/templates"): "admin.notifications.templates.manage",
    ("PUT", "/admin/notification-ops/templates"): "admin.notifications.templates.manage",
    ("POST", "/admin/notification-ops/templates/{template_key}/preview"): "admin.notifications.templates.manage",
    ("POST", "/admin/notification-ops/notifications/{notification_id}/replay"): "admin.notifications.replay",
    ("GET", "/admin/analytics/funnel"): "admin.analytics.read",
    ("GET", "/admin/analytics/cancellations"): "admin.analytics.read",
    ("GET", "/admin/analytics/driver-acceptance"): "admin.analytics.read",
    ("GET", "/admin/analytics/driver-utilization"): "admin.analytics.read",
    ("GET", "/admin/analytics/eta-accuracy"): "admin.analytics.read",
    ("GET", "/admin/analytics/fare-accuracy"): "admin.analytics.read",
    ("GET", "/admin/analytics/zone-demand-forecast"): "admin.analytics.read",
    ("GET", "/admin/analytics/zone-supply-forecast"): "admin.analytics.read",
    ("GET", "/admin/analytics/shared-rides"): "admin.analytics.read",
    ("GET", "/admin/location-ops/quality"): "admin.analytics.read",
    ("GET", "/admin/location-ops/provider-health"): "admin.analytics.read",
    ("GET", "/admin/location-ops/driver-density"): "admin.analytics.read",
    ("GET", "/admin/location-ops/spoof-signals"): "admin.analytics.read",
    ("GET", "/admin/location-ops/retention-controls"): "admin.governance.retention",
    ("PUT", "/admin/location-ops/retention-controls"): "admin.governance.retention",
    ("POST", "/admin/location-ops/retention-purge"): "admin.governance.retention",
    ("GET", "/admin/location-ops/road-restrictions"): "admin.analytics.read",
    ("POST", "/admin/location-ops/road-restrictions"): "admin.governance.manage",
    ("GET", "/admin/heat-map/monitor"): "admin.analytics.read",
    ("GET", "/admin/analytics/customer-cohorts"): "admin.analytics.read",
    ("GET", "/admin/analytics/driver-cohorts"): "admin.analytics.read",
    ("GET", "/admin/analytics/promo-effectiveness"): "admin.analytics.read",
    ("GET", "/admin/analytics/referral-fraud"): "admin.analytics.read",
    ("GET", "/admin/analytics/payment-success"): "admin.analytics.read",
    ("GET", "/admin/analytics/refunds"): "admin.analytics.read",
    ("GET", "/admin/analytics/support-sla"): "admin.analytics.read",
    ("GET", "/admin/analytics/safety-trends"): "admin.analytics.read",
    ("POST", "/admin/analytics/export"): "admin.reports.export",
    ("GET", "/admin/reports/scheduled"): "admin.reports.manage",
    ("POST", "/admin/reports/scheduled"): "admin.reports.manage",
    ("GET", "/admin/reports/custom"): "admin.reports.manage",
    ("POST", "/admin/reports/custom"): "admin.reports.manage",
    ("POST", "/admin/reports/custom/{config_id}/execute"): "admin.reports.manage",
    ("GET", "/admin/governance/audit-logs"): "admin.audit.read",
    ("POST", "/admin/governance/justify-action"): "admin.governance.justify",
    ("GET", "/admin/governance/sensitive-access"): "admin.governance.audit",
    ("POST", "/admin/governance/sensitive-access"): "admin.governance.audit",
    ("GET", "/admin/governance/retention-policies"): "admin.governance.retention",
    ("PUT", "/admin/governance/retention-policies/{category}"): "admin.governance.retention",
    ("POST", "/admin/governance/retention-policies/{category}/purge"): "admin.governance.retention",
    ("GET", "/admin/privacy/dsar-requests"): "admin.privacy.manage",
    ("POST", "/admin/privacy/dsar-requests/{request_id}/review"): "admin.privacy.manage",
}


async def get_current_admin(
    user: User = Depends(require_role("admin")),
    request: Request = None,
    db: AsyncSession = Depends(get_db),
) -> User:
    auth = request.headers.get("authorization", "") if request else ""
    token = auth.removeprefix("Bearer ")
    try:
        claims = decode_jwt_token(token)
        session_id = claims.get("session_id")
    except Exception:
        session_id = None
    verified_session = await db.get(Session, session_id) if session_id else None
    if not user.mfa_enabled or not verified_session or verified_session.user_id != user.id or not verified_session.mfa_verified:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Complete administrator MFA sign-in before using this console.")
    route = request.scope.get("route") if request else None
    route_path = getattr(route, "path", "")
    api_prefix = "/api/v1"
    normalized_path = route_path[len(api_prefix):] if route_path.startswith(api_prefix) else route_path
    needed = _ADMIN_PERMISSION_PATHS.get((request.method.upper(), normalized_path), "admin.access") if request else "admin.access"

    assigned = (await db.execute(select(AdminUserRole.role_id).where(AdminUserRole.user_id == user.id))).scalars().all()
    if not assigned:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Administrator role assignment is required before console access.")
    role_rows = (await db.execute(select(AdminRole).where(AdminRole.id.in_(assigned)))).scalars().all()
    if any(role.is_superuser for role in role_rows):
        return user
    permission = (await db.execute(
        select(AdminPermission.id)
        .join(AdminRolePermission, AdminRolePermission.permission_id == AdminPermission.id)
        .where(AdminRolePermission.role_id.in_(assigned), AdminPermission.permission_key == needed)
        .limit(1)
    )).first()
    if not permission:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=f"Administrator permission required: {needed}.")
    return user
