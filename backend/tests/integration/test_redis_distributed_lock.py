"""Real Redis integration tests for distributed lock semantics.

Requires REDIS_TEST_URL to point to an isolated, disposable Redis instance.
Tests use unique namespaced keys and never FLUSHDB the shared service.
"""
import asyncio
import os
import uuid
from unittest.mock import AsyncMock

import pytest

from backend.core.config import settings
from backend.infrastructure.cache.redis_client import RedisManager


REDIS_TEST_URL = os.getenv("REDIS_TEST_URL", "").strip()


def _manager() -> RedisManager:
    manager = RedisManager()
    manager.redis_url = REDIS_TEST_URL
    manager._reconnect_backoff = 0
    return manager


async def _close(manager: RedisManager) -> None:
    client = manager._redis
    if client is not None:
        await client.aclose()
        manager._redis = None
        manager.is_connected = False


@pytest.mark.asyncio
@pytest.mark.skipif(not REDIS_TEST_URL, reason="REDIS_TEST_URL is not configured; real Redis integration test skipped.")
async def test_real_redis_lock_is_atomic_and_owner_restricted():
    manager = _manager()
    key = f"ci:distributed-lock:{uuid.uuid4().hex}"
    try:
        client = await manager._get_redis()
        assert client is not None, "Configured Redis test service must be reachable"

        tokens = [uuid.uuid4().hex for _ in range(20)]
        acquired = await asyncio.gather(
            *(manager.acquire_lock(key, token, timeout_seconds=10) for token in tokens)
        )
        assert sum(acquired) == 1, f"Redis SET NX must allow exactly one owner, got {sum(acquired)}"

        owner = tokens[acquired.index(True)]
        non_owner = uuid.uuid4().hex
        assert await manager.release_lock(key, non_owner) is False
        assert await client.get(key) == owner
        assert await manager.release_lock(key, owner) is True
        assert await client.get(key) is None
    finally:
        if manager._redis is not None:
            await manager._redis.delete(key)
        await _close(manager)


@pytest.mark.asyncio
@pytest.mark.skipif(not REDIS_TEST_URL, reason="REDIS_TEST_URL is not configured; real Redis integration test skipped.")
async def test_real_redis_lock_expiry_and_stale_owner_cannot_release_new_lock():
    manager = _manager()
    key = f"ci:distributed-lock-expiry:{uuid.uuid4().hex}"
    try:
        client = await manager._get_redis()
        assert client is not None, "Configured Redis test service must be reachable"

        old_token = uuid.uuid4().hex
        new_token = uuid.uuid4().hex
        assert await manager.acquire_lock(key, old_token, timeout_seconds=1) is True
        await asyncio.sleep(1.2)
        assert await manager.acquire_lock(key, new_token, timeout_seconds=10) is True
        assert await manager.release_lock(key, old_token) is False
        assert await client.get(key) == new_token
        assert await manager.release_lock(key, new_token) is True
    finally:
        if manager._redis is not None:
            await manager._redis.delete(key)
        await _close(manager)


@pytest.mark.asyncio
async def test_production_lock_operations_fail_closed_without_redis(monkeypatch):
    manager = RedisManager()
    manager.redis_url = None
    manager._get_redis = AsyncMock(return_value=None)
    monkeypatch.setattr(settings, "ENVIRONMENT", "production")

    key = f"ci:production-fail-closed:{uuid.uuid4().hex}"
    old_token = uuid.uuid4().hex
    new_token = uuid.uuid4().hex

    # Simulate a stale local fallback lock. Production must neither acquire nor
    # release distributed locks through process-local memory when Redis is down.
    assert await manager._fallback.acquire_lock(key, old_token, timeout=30) is True
    assert await manager.acquire_lock(key, new_token, timeout_seconds=10) is False
    assert await manager.release_lock(key, old_token) is False
    assert await manager._fallback.acquire_lock(key, new_token, timeout=30) is False
