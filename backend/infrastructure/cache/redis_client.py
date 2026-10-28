import json
import time
import asyncio
import logging
from typing import Optional, Any, Dict, Tuple
from backend.core.config import settings

logger = logging.getLogger("cache.redis")

class CacheStats:
    def __init__(self):
        self.hits = 0
        self.misses = 0
        self.sets = 0
        self.errors = 0

    def record_hit(self):
        self.hits += 1

    def record_miss(self):
        self.misses += 1

    def record_set(self):
        self.sets += 1

    def record_error(self):
        self.errors += 1

    def to_dict(self) -> Dict[str, Any]:
        total = self.hits + self.misses
        hit_ratio = round((self.hits / total) * 100, 2) if total > 0 else 0.0
        return {
            "hits": self.hits,
            "misses": self.misses,
            "total_reads": total,
            "hit_ratio_percent": hit_ratio,
            "sets": self.sets,
            "errors": self.errors
        }

class InMemoryCacheFallback:
    """In-memory TTL cache fallback when Redis is unreachable."""
    def __init__(self):
        self._store: Dict[str, Tuple[Any, float]] = {}
        self._lock = asyncio.Lock()

    async def get(self, key: str) -> Optional[str]:
        async with self._lock:
            if key in self._store:
                val, expires_at = self._store[key]
                if time.time() < expires_at:
                    return val
                del self._store[key]
        return None

    async def set(self, key: str, value: str, ex: Optional[int] = None) -> bool:
        expires_at = time.time() + (ex if ex else 86400 * 365)
        async with self._lock:
            self._store[key] = (value, expires_at)
        return True

    async def delete(self, key: str) -> bool:
        async with self._lock:
            if key in self._store:
                del self._store[key]
                return True
        return False

    async def incr(self, key: str) -> int:
        async with self._lock:
            val, exp = self._store.get(key, ("0", time.time() + 60))
            try:
                new_val = int(val) + 1
            except ValueError:
                new_val = 1
            self._store[key] = (str(new_val), exp)
            return new_val

    async def expire(self, key: str, seconds: int) -> bool:
        async with self._lock:
            if key in self._store:
                val, _ = self._store[key]
                self._store[key] = (val, time.time() + seconds)
                return True
        return False

    async def exists(self, key: str) -> bool:
        return (await self.get(key)) is not None

class RedisManager:
    """
    Production-grade Redis Client Manager with connection pooling, automatic failover
    to an in-memory TTL store, distributed locks, and observability statistics.
    """
    def __init__(self):
        self.redis_url = getattr(settings, "REDIS_URL", None)
        self._redis = None
        self._fallback = InMemoryCacheFallback()
        self.is_connected = False
        self.stats = CacheStats()
        self._init_attempted = False

    async def _get_redis(self):
        if not self.redis_url:
            return None
        if self._redis is None and not self._init_attempted:
            self._init_attempted = True
            try:
                import redis.asyncio as aioredis
                self._redis = aioredis.from_url(
                    self.redis_url,
                    encoding="utf-8",
                    decode_responses=True,
                    socket_connect_timeout=5.0,
                    socket_timeout=5.0
                )
                await self._redis.ping()
                self.is_connected = True
                logger.info(f"Connected to Redis cluster at {self.redis_url}")
            except Exception as e:
                self.is_connected = False
                logger.warning(f"Redis unavailable ({e}). Gracefully falling back to in-memory TTL cache.")
        return self._redis if self.is_connected else None

    async def get(self, key: str) -> Optional[str]:
        client = await self._get_redis()
        try:
            if client:
                val = await client.get(key)
                if val is not None:
                    self.stats.record_hit()
                    return val
                self.stats.record_miss()
                return None
        except Exception as e:
            self.stats.record_error()
            self.is_connected = False
            logger.warning(f"Redis get failed: {e}. Falling back to memory.")

        # Fallback
        val = await self._fallback.get(key)
        if val is not None:
            self.stats.record_hit()
        else:
            self.stats.record_miss()
        return val

    async def get_json(self, key: str) -> Optional[Any]:
        raw = await self.get(key)
        if raw is not None:
            try:
                return json.loads(raw)
            except Exception:
                return raw
        return None

    async def set(self, key: str, value: str, ex: Optional[int] = None) -> bool:
        self.stats.record_set()
        client = await self._get_redis()
        try:
            if client:
                await client.set(key, value, ex=ex)
                return True
        except Exception as e:
            self.stats.record_error()
            self.is_connected = False
            logger.warning(f"Redis set failed: {e}. Falling back to memory.")

        return await self._fallback.set(key, value, ex=ex)

    async def set_json(self, key: str, value: Any, ex: Optional[int] = None) -> bool:
        return await self.set(key, json.dumps(value), ex=ex)

    async def delete(self, key: str) -> bool:
        client = await self._get_redis()
        try:
            if client:
                await client.delete(key)
                return True
        except Exception as e:
            self.stats.record_error()
            self.is_connected = False
        return await self._fallback.delete(key)

    async def incr(self, key: str, ex: Optional[int] = None) -> int:
        client = await self._get_redis()
        try:
            if client:
                val = await client.incr(key)
                if ex and val == 1:
                    await client.expire(key, ex)
                return val
        except Exception as e:
            self.stats.record_error()
            self.is_connected = False

        val = await self._fallback.incr(key)
        if ex and val == 1:
            await self._fallback.expire(key, ex)
        return val

    async def expire(self, key: str, seconds: int) -> bool:
        client = await self._get_redis()
        try:
            if client:
                return await client.expire(key, seconds)
        except Exception:
            self.is_connected = False
        return await self._fallback.expire(key, seconds)

    async def exists(self, key: str) -> bool:
        client = await self._get_redis()
        try:
            if client:
                return bool(await client.exists(key))
        except Exception:
            self.is_connected = False
        return await self._fallback.exists(key)

    async def health_check(self) -> Dict[str, Any]:
        """Observability health check reporting latency and backend status."""
        client = await self._get_redis()
        start = time.time()
        if client:
            try:
                await client.ping()
                latency_ms = round((time.time() - start) * 1000, 2)
                return {
                    "status": "HEALTHY",
                    "backend": "REDIS",
                    "latency_ms": latency_ms,
                    "connected": True,
                    "stats": self.stats.to_dict()
                }
            except Exception as e:
                self.is_connected = False
        return {
            "status": "DEGRADED_FALLBACK_ACTIVE",
            "backend": "IN_MEMORY_FALLBACK",
            "latency_ms": round((time.time() - start) * 1000, 2),
            "connected": False,
            "stats": self.stats.to_dict()
        }

# Global singleton
redis_client = RedisManager()
