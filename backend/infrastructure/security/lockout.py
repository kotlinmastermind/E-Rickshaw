"""
Progressive Account Lockout & Distributed Brute-Force Defense Infrastructure
Provides bounded progressive lockout tiers, anonymized SHA-256 keys, accurate Retry-After headers,
and dual-layer synchronization across primary distributed Redis and authoritative PostgreSQL storage.
"""
import logging
from datetime import datetime, timezone, timedelta
from typing import Dict, Optional, Any
from fastapi import HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from backend.infrastructure.cache.redis_client import redis_client
from backend.infrastructure.security.crypto import hash_secret

logger = logging.getLogger("security.lockout")

BASE_LOCKOUT_SECONDS = 900   # Tier 1: 15 minutes
MAX_LOCKOUT_SECONDS = 86400  # Cap: 24 hours
THRESHOLD_ATTEMPTS = 5       # 5 consecutive failures

# In-memory fallback if both Redis and PostgreSQL are unavailable
_in_memory_lockout_state: Dict[str, Dict[str, Any]] = {}


def get_lockout_duration(tier: int) -> int:
    """Calculates progressive lockout duration bounded by MAX_LOCKOUT_SECONDS (24 hours)."""
    effective_tier = max(1, tier)
    return min(BASE_LOCKOUT_SECONDS * (2 ** (effective_tier - 1)), MAX_LOCKOUT_SECONDS)


def normalize_auth_identifier(ident: str) -> str:
    """Consistently normalizes email, username, or phone number for lockout tracking."""
    raw = ident.strip().lower()
    digits = "".join(filter(str.isdigit, raw))
    if len(digits) >= 10:
        return f"+91{digits[-10:]}"
    return raw


def get_lockout_keys(identifier: str) -> Dict[str, str]:
    """Generates anonymized SHA-256 key names to avoid storing raw credentials in Redis or DB."""
    norm = normalize_auth_identifier(identifier)
    key_hash = hash_secret(f"auth_lockout:{norm}")[:32]
    return {
        "key_hash": key_hash,
        "lockout_key": f"auth_lockout:{key_hash}",
        "attempts_key": f"auth_attempts:{key_hash}",
        "tier_key": f"auth_tier:{key_hash}"
    }


async def check_account_lockout(identifier: str, db: Optional[AsyncSession] = None) -> None:
    """
    Checks if an account identifier is currently locked out.
    Dual-layer check: Queries Redis first (fast path), falls back to PostgreSQL (durable authority).
    """
    keys = get_lockout_keys(identifier)
    lockout_key = keys["lockout_key"]
    key_hash = keys["key_hash"]
    now = datetime.now(timezone.utc)

    # 1. Primary check: Redis
    try:
        if await redis_client.exists(lockout_key):
            ttl = await redis_client.ttl(lockout_key)
            if ttl <= 0:
                ttl = BASE_LOCKOUT_SECONDS
            minutes = max(1, (ttl + 59) // 60)
            logger.warning(
                f"Login blocked by progressive account lockout (Redis) for identifier hash {key_hash[:8]} (TTL: {ttl}s)"
            )
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail=f"Account is temporarily locked due to multiple failed login attempts. Please retry after {minutes} minutes.",
                headers={"Retry-After": str(max(1, ttl))}
            )
    except HTTPException:
        raise
    except Exception as e:
        logger.warning(f"Redis lockout check failed: {e}. Checking PostgreSQL authority.")

    # 2. Authoritative check: PostgreSQL (when db session provided)
    if db is not None:
        try:
            from backend.domain.models.models import AccountLockout
            stmt = select(AccountLockout).where(AccountLockout.key_hash == key_hash)
            res = await db.execute(stmt)
            record = res.scalar_one_or_none()
            if record and record.locked_until:
                l_until = record.locked_until if record.locked_until.tzinfo else record.locked_until.replace(tzinfo=timezone.utc)
                if l_until > now:
                    ttl = max(1, int((l_until - now).total_seconds()))
                    minutes = max(1, (ttl + 59) // 60)
                    # Backfill Redis cache if reachable
                    try:
                        await redis_client.set(lockout_key, str(record.lockout_tier), ex=ttl)
                    except Exception:
                        pass
                    logger.warning(
                        f"Login blocked by progressive account lockout (PostgreSQL) for identifier hash {key_hash[:8]} (TTL: {ttl}s)"
                    )
                    raise HTTPException(
                        status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                        detail=f"Account is temporarily locked due to multiple failed login attempts. Please retry after {minutes} minutes.",
                        headers={"Retry-After": str(ttl)}
                    )
                else:
                    # Expired lockout in DB
                    record.locked_until = None
                    record.failed_attempts = 0
                    await db.commit()
        except HTTPException:
            raise
        except Exception as db_err:
            logger.warning(f"Database lockout check failed: {db_err}")

    # 3. In-memory check (if Redis failed and no db provided)
    mem_entry = _in_memory_lockout_state.get(key_hash)
    if mem_entry and mem_entry.get("locked_until"):
        l_until = mem_entry["locked_until"]
        if l_until > now:
            ttl = max(1, int((l_until - now).total_seconds()))
            minutes = max(1, (ttl + 59) // 60)
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail=f"Account is temporarily locked due to multiple failed login attempts. Please retry after {minutes} minutes.",
                headers={"Retry-After": str(ttl)}
            )


async def record_failed_login(identifier: str, db: Optional[AsyncSession] = None) -> None:
    """
    Increments failed attempt count and triggers progressive lockout when threshold is reached.
    Synchronizes state across both Redis (cache) and PostgreSQL (durable shared storage).
    """
    keys = get_lockout_keys(identifier)
    key_hash = keys["key_hash"]
    attempts_key = keys["attempts_key"]
    lockout_key = keys["lockout_key"]
    tier_key = keys["tier_key"]
    now = datetime.now(timezone.utc)

    redis_duration = 0
    redis_locked = False
    redis_succeeded = False

    # 1. Increment in Redis
    try:
        attempts = await redis_client.incr(attempts_key, ex=BASE_LOCKOUT_SECONDS)
        if attempts >= THRESHOLD_ATTEMPTS:
            tier = await redis_client.incr(tier_key, ex=MAX_LOCKOUT_SECONDS * 2)
            redis_duration = get_lockout_duration(tier)
            await redis_client.set(lockout_key, str(tier), ex=redis_duration)
            await redis_client.delete(attempts_key)
            redis_locked = True
            logger.warning(
                f"Progressive account lockout triggered (Redis) for identifier hash {key_hash[:8]}: "
                f"Tier {tier}, duration {redis_duration}s"
            )
        redis_succeeded = True
    except Exception as e:
        logger.warning(f"Redis error during failed login tracking: {e}. Relying on PostgreSQL authority.")

    # 2. Synchronize to PostgreSQL (if db session provided)
    db_duration = 0
    db_locked = False
    if db is not None:
        try:
            from backend.domain.models.models import AccountLockout
            stmt = select(AccountLockout).where(AccountLockout.key_hash == key_hash)
            res = await db.execute(stmt)
            record = res.scalar_one_or_none()
            if not record:
                record = AccountLockout(
                    key_hash=key_hash,
                    failed_attempts=1,
                    lockout_tier=0,
                    locked_until=None,
                    last_failed_at=now
                )
                db.add(record)
            else:
                if record.locked_until:
                    l_until = record.locked_until if record.locked_until.tzinfo else record.locked_until.replace(tzinfo=timezone.utc)
                    if l_until <= now:
                        record.locked_until = None
                        record.failed_attempts = 0
                record.failed_attempts += 1
                record.last_failed_at = now
                if record.failed_attempts >= THRESHOLD_ATTEMPTS:
                    record.lockout_tier += 1
                    db_duration = get_lockout_duration(record.lockout_tier)
                    record.locked_until = now + timedelta(seconds=db_duration)
                    record.failed_attempts = 0
                    db_locked = True
                    logger.warning(
                        f"Progressive account lockout triggered (PostgreSQL) for identifier hash {key_hash[:8]}: "
                        f"Tier {record.lockout_tier}, duration {db_duration}s"
                    )
            await db.commit()
        except Exception as db_err:
            await db.rollback()
            logger.warning(f"Database error during failed login tracking: {db_err}")

    # 3. In-memory tracking fallback if Redis failed and no db provided
    mem_locked = False
    mem_duration = 0
    if not redis_succeeded and db is None:
        entry = _in_memory_lockout_state.setdefault(key_hash, {"attempts": 0, "tier": 0, "locked_until": None})
        if entry["locked_until"] and entry["locked_until"] <= now:
            entry["locked_until"] = None
            entry["attempts"] = 0
        entry["attempts"] += 1
        if entry["attempts"] >= THRESHOLD_ATTEMPTS:
            entry["tier"] += 1
            mem_duration = get_lockout_duration(entry["tier"])
            entry["locked_until"] = now + timedelta(seconds=mem_duration)
            entry["attempts"] = 0
            mem_locked = True

    duration = redis_duration or db_duration or mem_duration
    if redis_locked or db_locked or mem_locked:
        minutes = max(1, duration // 60)
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"Account is temporarily locked due to multiple failed login attempts. Please retry after {minutes} minutes.",
            headers={"Retry-After": str(duration)}
        )


async def reset_account_lockout(identifier: str, db: Optional[AsyncSession] = None) -> None:
    """Clears failure counter, lockout, and tier upon successful authentication across all layers."""
    keys = get_lockout_keys(identifier)
    key_hash = keys["key_hash"]

    # 1. Clear Redis
    try:
        await redis_client.delete(keys["attempts_key"])
        await redis_client.delete(keys["lockout_key"])
        await redis_client.delete(keys["tier_key"])
    except Exception as e:
        logger.warning(f"Redis cleanup failed on successful login: {e}")

    # 2. Clear PostgreSQL
    if db is not None:
        try:
            from backend.domain.models.models import AccountLockout
            stmt = select(AccountLockout).where(AccountLockout.key_hash == key_hash)
            res = await db.execute(stmt)
            record = res.scalar_one_or_none()
            if record:
                record.failed_attempts = 0
                record.locked_until = None
                record.lockout_tier = 0
                await db.commit()
        except Exception as db_err:
            await db.rollback()
            logger.warning(f"Database cleanup failed on successful login: {db_err}")

    # 3. Clear In-memory fallback
    _in_memory_lockout_state.pop(key_hash, None)
