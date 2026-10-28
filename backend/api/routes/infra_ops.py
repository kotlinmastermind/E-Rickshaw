import json
from fastapi import APIRouter, Depends, HTTPException, status, Header
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Dict, Any, List, Optional
from backend.api.deps import get_db, get_current_user
from backend.domain.models.models import User
from backend.application.services.infra_ops_service import InfraOpsService

router = APIRouter(prefix="/infra", tags=["Infrastructure, Observability, Resilience & Ops"])

# Feature 451: OpenAPI client generation
@router.get("/openapi-client-spec")
async def get_openapi_client_spec(app_type: str = "CUSTOMER", db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    return await service.generate_openapi_client_spec(app_type)

# Feature 452: API version compatibility
@router.get("/api-versions")
async def get_api_versions(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    records = await service.get_api_versions()
    return [
        {
            "id": r.id,
            "version_code": r.version_code,
            "app_type": r.app_type,
            "min_supported_version": r.min_supported_version,
            "latest_version": r.latest_version,
            "status": r.status,
            "deprecation_message": r.deprecation_message,
            "created_at": r.created_at.isoformat() if r.created_at else None
        }
        for r in records
    ]

@router.post("/api-versions")
async def update_api_version(payload: Dict[str, Any], db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    service = InfraOpsService(db)
    rec = await service.update_api_version(payload)
    return {"message": "API version record updated", "version_code": rec.version_code, "status": rec.status}

# Feature 453: Idempotency key support
@router.post("/idempotency/check")
async def check_idempotency(
    payload: Dict[str, Any],
    x_idempotency_key: Optional[str] = Header(None, alias="X-Idempotency-Key"),
    db: AsyncSession = Depends(get_db)
):
    if not x_idempotency_key:
        x_idempotency_key = f"IDEM-{payload.get('key', 'default')}"
    service = InfraOpsService(db)
    is_cached, data = await service.check_or_record_idempotency(
        key=x_idempotency_key,
        route_path="/infra/idempotency/check",
        request_data=payload,
        response_code=200,
        response_data={"status": "PROCESSED", "key": x_idempotency_key}
    )
    return {"is_replay": is_cached, "data": data}

# Feature 454: Standard API error catalog
@router.get("/error-catalog")
async def get_error_catalog(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    entries = await service.get_error_catalog()
    return [
        {
            "id": e.id,
            "error_code": e.error_code,
            "http_status_code": e.http_status_code,
            "title": e.title,
            "description": e.description,
            "resolution_guidance": e.resolution_guidance,
            "category": e.category,
            "is_active": e.is_active
        }
        for e in entries
    ]

# Features 455 & 456: Request correlation IDs & Distributed trace viewer
@router.get("/traces")
async def get_distributed_traces(limit: int = 50, db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    spans = await service.get_distributed_traces(limit)
    return [
        {
            "id": s.id,
            "trace_id": s.trace_id,
            "span_id": s.span_id,
            "operation_name": s.operation_name,
            "service_name": s.service_name,
            "duration_ms": s.duration_ms,
            "http_method": s.http_method,
            "http_status": s.http_status,
            "route_path": s.route_path,
            "correlation_id": s.correlation_id,
            "error_message": s.error_message,
            "timestamp": s.timestamp.isoformat() if s.timestamp else None
        }
        for s in spans
    ]

# Feature 457: Structured audit event schema
@router.get("/audit-events")
async def get_structured_audit_events(limit: int = 50, db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    events = await service.get_structured_audit_events(limit)
    return [
        {
            "id": ev.id,
            "actor_id": ev.actor_id,
            "actor_role": ev.actor_role,
            "action": ev.action,
            "target_resource": ev.target_resource,
            "target_id": ev.target_id,
            "status": ev.status,
            "correlation_id": ev.correlation_id,
            "created_at": ev.created_at.isoformat() if ev.created_at else None
        }
        for ev in events
    ]

# Feature 458: Background job queue and retry policy
@router.get("/background-jobs")
async def get_background_jobs(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    jobs = await service.get_background_jobs()
    return [
        {
            "id": j.id,
            "job_name": j.job_name,
            "queue_name": j.queue_name,
            "status": j.status,
            "attempt_count": j.attempt_count,
            "max_retries": j.max_retries,
            "last_error": j.last_error,
            "scheduled_at": j.scheduled_at.isoformat() if j.scheduled_at else None
        }
        for j in jobs
    ]

@router.post("/background-jobs/{job_id}/retry")
async def retry_background_job(job_id: str, db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    job = await service.retry_background_job(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Background job not found")
    return {"message": "Job queued for retry", "job_id": job.id, "status": job.status}

# Feature 459: Scheduled job monitoring
@router.get("/scheduled-jobs")
async def get_scheduled_jobs(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    monitors = await service.get_scheduled_job_monitors()
    return [
        {
            "id": m.id,
            "job_key": m.job_key,
            "job_title": m.job_title,
            "cron_schedule": m.cron_schedule,
            "last_run_at": m.last_run_at.isoformat() if m.last_run_at else None,
            "next_run_at": m.next_run_at.isoformat() if m.next_run_at else None,
            "last_status": m.last_status,
            "last_duration_ms": m.last_duration_ms,
            "is_enabled": m.is_enabled
        }
        for m in monitors
    ]

# Feature 460: Database migration rollback verification
@router.get("/migration-verifications")
async def get_migration_verifications(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    verifications = await service.get_migration_verifications()
    return [
        {
            "id": v.id,
            "revision_id": v.revision_id,
            "down_revision_id": v.down_revision_id,
            "rollback_verified": v.rollback_verified,
            "verification_status": v.verification_status,
            "notes": v.notes,
            "verified_at": v.verified_at.isoformat() if v.verified_at else None
        }
        for v in verifications
    ]

@router.post("/migration-verifications/verify")
async def verify_migration(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    rec = await service.verify_migration_rollback(payload.get("revision_id", "20261025_head"))
    return {"message": "Migration rollback verified", "revision_id": rec.revision_id, "status": rec.verification_status}

# Features 461 & 462: Automated backup retention & Key rotation
@router.get("/backup-policies")
async def get_backup_policies(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    policies = await service.get_backup_policies()
    return [
        {
            "id": p.id,
            "policy_name": p.policy_name,
            "backup_type": p.backup_type,
            "retention_days": p.retention_days,
            "encryption_algorithm": p.encryption_algorithm,
            "key_rotation_status": p.key_rotation_status,
            "kms_key_arn": p.kms_key_arn,
            "last_backup_at": p.last_backup_at.isoformat() if p.last_backup_at else None,
            "is_active": p.is_active
        }
        for p in policies
    ]

@router.post("/backup-policies/{policy_id}/rotate-keys")
async def rotate_backup_keys(policy_id: str, db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    policy = await service.rotate_backup_encryption_keys(policy_id)
    if not policy:
        raise HTTPException(status_code=404, detail="Backup policy not found")
    return {"message": "Backup encryption key rotated", "policy_id": policy.id, "key_arn": policy.kms_key_arn}

# Feature 463: Restore drill scheduler
@router.get("/restore-drills")
async def get_restore_drills(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    drills = await service.get_restore_drills()
    return [
        {
            "id": d.id,
            "drill_name": d.drill_name,
            "target_region": d.target_region,
            "scheduled_date": d.scheduled_date.isoformat() if d.scheduled_date else None,
            "status": d.status,
            "rto_minutes_achieved": d.rto_minutes_achieved,
            "rpo_minutes_achieved": d.rpo_minutes_achieved,
            "conducted_by": d.conducted_by
        }
        for d in drills
    ]

# Feature 464: Multi-region disaster recovery plan
@router.get("/dr-plan")
async def get_dr_plan(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    plan = await service.get_dr_plan()
    return {
        "id": plan.id,
        "plan_code": plan.plan_code,
        "primary_region": plan.primary_region,
        "dr_region": plan.dr_region,
        "replication_status": plan.replication_status,
        "failover_readiness": plan.failover_readiness,
        "last_failover_test_at": plan.last_failover_test_at.isoformat() if plan.last_failover_test_at else None
    }

# Features 465 & 466: Redis namespaces & Cache tests
@router.get("/redis-namespaces")
async def get_redis_namespaces(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    namespaces = await service.get_redis_namespaces()
    return [
        {
            "id": ns.id,
            "namespace": ns.namespace,
            "default_ttl_seconds": ns.default_ttl_seconds,
            "keys_count": ns.keys_count,
            "memory_bytes": ns.memory_bytes,
            "eviction_policy": ns.eviction_policy
        }
        for ns in namespaces
    ]

@router.post("/cache-tests/run")
async def run_cache_tests(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    return await service.run_cache_invalidation_tests()

# Feature 467: Per-route API latency
@router.get("/route-latencies")
async def get_route_latencies(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    metrics = await service.get_route_latencies()
    return [
        {
            "id": m.id,
            "route_path": m.route_path,
            "http_method": m.http_method,
            "avg_latency_ms": m.avg_latency_ms,
            "p95_latency_ms": m.p95_latency_ms,
            "p99_latency_ms": m.p99_latency_ms,
            "request_count": m.request_count,
            "error_count": m.error_count
        }
        for m in metrics
    ]

# Features 468, 469, 470, 471: Provider circuit breakers & failovers
@router.get("/provider-health")
async def get_provider_health(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    configs = await service.get_provider_health()
    return [
        {
            "id": p.id,
            "provider_name": p.provider_name,
            "provider_type": p.provider_type,
            "is_healthy": p.is_healthy,
            "circuit_breaker_state": p.circuit_breaker_state,
            "consecutive_failures": p.consecutive_failures,
            "failover_provider_name": p.failover_provider_name,
            "quota_used_percentage": p.quota_used_percentage
        }
        for p in configs
    ]

@router.post("/provider-health/{provider_name}/failover")
async def trigger_provider_failover(provider_name: str, db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    p = await service.trigger_provider_failover(provider_name)
    if not p:
        raise HTTPException(status_code=404, detail="Provider configuration not found")
    return {"message": f"Circuit breaker opened for {provider_name}. Failover active.", "failover": p.failover_provider_name}

# Feature 472: Secret rotation workflow
@router.get("/secret-rotations")
async def get_secret_rotations(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    secrets = await service.get_secret_rotations()
    return [
        {
            "id": s.id,
            "secret_name": s.secret_name,
            "vault_path": s.vault_path,
            "current_version": s.current_version,
            "status": s.status,
            "auto_rotate_days": s.auto_rotate_days,
            "last_rotated_at": s.last_rotated_at.isoformat() if s.last_rotated_at else None,
            "next_rotation_due": s.next_rotation_due.isoformat() if s.next_rotation_due else None
        }
        for s in secrets
    ]

@router.post("/secret-rotations/{secret_name}/rotate")
async def rotate_secret(secret_name: str, db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    s = await service.rotate_secret(secret_name)
    if not s:
        raise HTTPException(status_code=404, detail="Secret workflow not found")
    return {"message": f"Secret {secret_name} rotated successfully", "new_version": s.current_version}

# Feature 473: Dependency vulnerability reporting
@router.get("/vulnerabilities")
async def get_vulnerabilities(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    vulns = await service.get_vulnerabilities()
    return [
        {
            "id": v.id,
            "package_name": v.package_name,
            "installed_version": v.installed_version,
            "fixed_version": v.fixed_version,
            "cve_id": v.cve_id,
            "severity": v.severity,
            "status": v.status,
            "advisory_url": v.advisory_url
        }
        for v in vulns
    ]

# Feature 474: Mobile crash and ANR reporting
@router.get("/mobile-crashes")
async def get_mobile_crashes(limit: int = 50, db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    crashes = await service.get_mobile_crashes(limit)
    return [
        {
            "id": c.id,
            "app_type": c.app_type,
            "app_version": c.app_version,
            "device_model": c.device_model,
            "os_version": c.os_version,
            "report_type": c.report_type,
            "stack_trace": c.stack_trace,
            "error_message": c.error_message,
            "created_at": c.created_at.isoformat() if c.created_at else None
        }
        for c in crashes
    ]

@router.post("/mobile-crashes")
async def report_mobile_crash(payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    report = await service.report_mobile_crash(payload)
    return {"message": "Mobile crash report recorded", "report_id": report.id}

# Feature 475: Feature flag management
@router.get("/feature-flags")
async def get_feature_flags(db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    flags = await service.get_feature_flags()
    return [
        {
            "id": f.id,
            "flag_key": f.flag_key,
            "name": f.name,
            "description": f.description,
            "is_enabled": f.is_enabled,
            "rollout_percentage": f.rollout_percentage,
            "target_roles": json.loads(f.target_roles_json) if f.target_roles_json else [],
            "updated_at": f.updated_at.isoformat() if f.updated_at else None
        }
        for f in flags
    ]

@router.post("/feature-flags/{flag_key}/toggle")
async def toggle_feature_flag(flag_key: str, payload: Dict[str, Any], db: AsyncSession = Depends(get_db)):
    service = InfraOpsService(db)
    flag = await service.update_feature_flag(flag_key, payload)
    if not flag:
        raise HTTPException(status_code=404, detail="Feature flag not found")
    return {"message": f"Feature flag {flag_key} updated", "is_enabled": flag.is_enabled, "rollout_percentage": flag.rollout_percentage}
