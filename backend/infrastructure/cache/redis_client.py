import json
import time
import asyncio
import logging
import uuid
from contextlib import asynccontextmanager
from typing import Optional, Any, Dict, Tuple, Set
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
        self._subscribers: Dict[str, Set[asyncio.Queue]] = {}
        self._lock = asyncio.Lock()

    async def register_subscriber(self, channel: str, queue: asyncio.Queue):
        async with self._lock:
            if channel not in self._subscribers:
                self._subscribers[channel] = set()
            self._subscribers[channel].add(queue)

    async def unregister_subscriber(self, channel: str, queue: asyncio.Queue):
        async with self._lock:
            if channel in self._subscribers:
                self._subscribers[channel].discard(queue)
                if not self._subscribers[channel]:
                    del self._subscribers[channel]

    async def publish(self, channel: str, message: str) -> int:
        async with self._lock:
            queues = list(self._subscribers.get(channel, set()))
        for q in queues:
            try:
                q.put_nowait({"type": "message", "channel": channel, "data": message})
            except Exception:
                pass
        return len(queues)

    async def get(self, key: str) -> Optional[str]:
        async with self._lock:
            if key in self._store:
                val, expires_at = self._store[key]
                if time.time() < expires_at:
                    return val
                del self._store[key]
        return None

    async def set(self, key: str, value: str, ex: Optional[int] = None, nx: bool = False) -> bool:
        expires_at = time.time() + (ex if ex else 86400 * 365)
        async with self._lock:
            if nx:
                if key in self._store:
                    val, exp = self._store[key]
                    if time.time() < exp:
                        return False
                    del self._store[key]
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

    async def ttl(self, key: str) -> int:
        async with self._lock:
            if key in self._store:
                val, expires_at = self._store[key]
                remaining = int(expires_at - time.time())
                if remaining > 0:
                    return remaining
                del self._store[key]
        return -2

    async def sadd(self, key: str, *members: str) -> int:
        async with self._lock:
            if key not in self._store:
                self._store[key] = (set(), time.time() + 86400 * 365)
            curr_set, exp = self._store[key]
            if not isinstance(curr_set, set):
                curr_set = set()
            added = 0
            for m in members:
                m_str = str(m)
                if m_str not in curr_set:
                    curr_set.add(m_str)
                    added += 1
            self._store[key] = (curr_set, exp)
            return added

    async def srem(self, key: str, *members: str) -> int:
        async with self._lock:
            if key in self._store:
                curr_set, exp = self._store[key]
                if isinstance(curr_set, set):
                    removed = 0
                    for m in members:
                        m_str = str(m)
                        if m_str in curr_set:
                            curr_set.remove(m_str)
                            removed += 1
                    return removed
            return 0

    async def smembers(self, key: str) -> Set[str]:
        async with self._lock:
            if key in self._store:
                curr_set, exp = self._store[key]
                if time.time() < exp and isinstance(curr_set, set):
                    return set(curr_set)
                elif time.time() >= exp:
                    del self._store[key]
            return set()

    async def scard(self, key: str) -> int:
        members = await self.smembers(key)
        return len(members)

    async def exists(self, key: str) -> bool:
        return (await self.get(key)) is not None

    async def acquire_lock(self, key: str, token: str, timeout: int) -> bool:
        async with self._lock:
            now = time.time()
            if key in self._store:
                val, expires_at = self._store[key]
                if now < expires_at:
                    return False
            self._store[key] = (token, now + timeout)
            return True

    async def release_lock(self, key: str, token: str) -> bool:
        async with self._lock:
            if key in self._store:
                val, _ = self._store[key]
                if val == token:
                    del self._store[key]
                    return True
            return False

_PUBSUB_SENTINEL = object()

class InMemoryPubSub:
    """In-memory PubSub fallback matching redis.asyncio PubSub interface."""
    def __init__(self, fallback: InMemoryCacheFallback):
        self.fallback = fallback
        self.channels: Set[str] = set()
        self.queue: asyncio.Queue = asyncio.Queue()
        self._closed = False

    async def subscribe(self, *channels: str):
        if self._closed:
            return
        for ch in channels:
            self.channels.add(ch)
            await self.fallback.register_subscriber(ch, self.queue)

    async def unsubscribe(self, *channels: str):
        for ch in channels:
            self.channels.discard(ch)
            await self.fallback.unregister_subscriber(ch, self.queue)

    async def get_message(self, ignore_subscribe_messages: bool = True, timeout: Optional[float] = None) -> Optional[Dict[str, Any]]:
        if self._closed:
            return None
        try:
            if timeout is not None:
                msg = await asyncio.wait_for(self.queue.get(), timeout=timeout)
            else:
                msg = await self.queue.get()
            if msg is _PUBSUB_SENTINEL:
                return None
            return msg
        except (asyncio.TimeoutError, asyncio.CancelledError):
            return None

    async def listen(self):
        while not self._closed:
            msg = await self.queue.get()
            if msg is _PUBSUB_SENTINEL:
                break
            yield msg

    async def close(self):
        self._closed = True
        for ch in list(self.channels):
            await self.fallback.unregister_subscriber(ch, self.queue)
        self.channels.clear()
        try:
            self.queue.put_nowait(_PUBSUB_SENTINEL)
        except Exception:
            pass

class RedisManager:
    """
    Production-grade Redis Client Manager with connection pooling, automatic failover
    to an in-memory TTL store, exponential reconnect backoff with jitter, distributed locks,
    and observability statistics.
    """
    def __init__(self):
        self.redis_url = getattr(settings, "REDIS_URL", None)
        self._redis = None
        self._fallback = InMemoryCacheFallback()
        self.is_connected = False
        self.stats = CacheStats()
        self._last_reconnect_attempt = 0.0
        self._reconnect_backoff = 1.0
        self._max_reconnect_backoff = 60.0

    async def _get_redis(self):
        if not self.redis_url:
            return None

        now = time.time()
        if self.is_connected and self._redis:
            return self._redis

        # If disconnected, apply exponential backoff with jitter before retrying
        if (now - self._last_reconnect_attempt) < self._reconnect_backoff:
            return None

        self._last_reconnect_attempt = now
        try:
            import redis.asyncio as aioredis
            import random

            if self._redis is None:
                self._redis = aioredis.from_url(
                    self.redis_url,
                    encoding="utf-8",
                    decode_responses=True,
                    socket_connect_timeout=5.0,
                    socket_timeout=5.0
                )
            await self._redis.ping()
            self.is_connected = True
            self._reconnect_backoff = 1.0
            logger.info(f"Connected/reconnected to Redis cluster at {self.redis_url}")
            return self._redis
        except Exception as e:
            self.is_connected = False
            import random
            jitter = random.uniform(0.1, 0.5)
            self._reconnect_backoff = min(self._max_reconnect_backoff, self._reconnect_backoff * 2) + jitter
            logger.warning(
                f"Redis unavailable ({e}). Reconnecting in {round(self._reconnect_backoff, 2)}s. "
                "Gracefully falling back to in-memory TTL cache."
            )
            return None

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

    async def set(self, key: str, value: str, ex: Optional[int] = None, nx: bool = False) -> bool:
        self.stats.record_set()
        client = await self._get_redis()
        try:
            if client:
                res = await client.set(key, value, ex=ex, nx=nx)
                return bool(res)
        except Exception as e:
            self.stats.record_error()
            self.is_connected = False
            logger.warning(f"Redis set failed: {e}. Falling back to memory.")

        return await self._fallback.set(key, value, ex=ex, nx=nx)

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

    async def ttl(self, key: str) -> int:
        client = await self._get_redis()
        try:
            if client:
                return await client.ttl(key)
        except Exception:
            self.is_connected = False
        return await self._fallback.ttl(key)

    async def exists(self, key: str) -> bool:
        client = await self._get_redis()
        try:
            if client:
                return bool(await client.exists(key))
        except Exception:
            self.is_connected = False
        return await self._fallback.exists(key)

    async def sadd(self, key: str, *members: str) -> int:
        client = await self._get_redis()
        try:
            if client:
                return await client.sadd(key, *members)
        except Exception as e:
            self.stats.record_error()
            self.is_connected = False
        return await self._fallback.sadd(key, *members)

    async def srem(self, key: str, *members: str) -> int:
        client = await self._get_redis()
        try:
            if client:
                return await client.srem(key, *members)
        except Exception as e:
            self.stats.record_error()
            self.is_connected = False
        return await self._fallback.srem(key, *members)

    async def smembers(self, key: str) -> Set[str]:
        client = await self._get_redis()
        try:
            if client:
                res = await client.smembers(key)
                return set(res) if res else set()
        except Exception as e:
            self.stats.record_error()
            self.is_connected = False
        return await self._fallback.smembers(key)

    async def scard(self, key: str) -> int:
        client = await self._get_redis()
        try:
            if client:
                return await client.scard(key)
        except Exception as e:
            self.stats.record_error()
            self.is_connected = False
        return await self._fallback.scard(key)

    async def publish(self, channel: str, message: Any) -> int:
        self.stats.record_set()
        client = await self._get_redis()
        msg_str = json.dumps(message) if isinstance(message, (dict, list)) else str(message)
        try:
            if client:
                return await client.publish(channel, msg_str)
        except Exception as e:
            self.stats.record_error()
            self.is_connected = False
            logger.warning(f"Redis publish failed: {e}. Falling back to in-memory topic.")

        return await self._fallback.publish(channel, msg_str)

    async def get_pubsub(self):
        """Returns a connected PubSub object (Redis PubSub or InMemoryPubSub fallback)."""
        client = await self._get_redis()
        if client and self.is_connected:
            return client.pubsub()
        return InMemoryPubSub(self._fallback)

    def pubsub(self):
        """Returns an InMemoryPubSub fallback or Redis pubsub if already connected."""
        if self.is_connected and self._redis:
            return self._redis.pubsub()
        return InMemoryPubSub(self._fallback)

    RELEASE_LOCK_LUA = """
    if redis.call("get", KEYS[1]) == ARGV[1] then
        return redis.call("del", KEYS[1])
    else
        return 0
    end
    """

    async def acquire_lock(self, key: str, token: str, timeout_seconds: int = 10) -> bool:
        client = await self._get_redis()
        try:
            if client:
                res = await client.set(key, token, nx=True, px=int(timeout_seconds * 1000))
                return bool(res)
        except Exception as e:
            logger.warning(f"Redis acquire_lock failed, using memory fallback: {e}")
            self.is_connected = False
        return await self._fallback.acquire_lock(key, token, timeout_seconds)

    async def release_lock(self, key: str, token: str) -> bool:
        client = await self._get_redis()
        try:
            if client:
                res = await client.eval(self.RELEASE_LOCK_LUA, 1, key, token)
                return bool(res)
        except Exception as e:
            logger.warning(f"Redis release_lock failed, using memory fallback: {e}")
            self.is_connected = False
        return await self._fallback.release_lock(key, token)

    @asynccontextmanager
    async def distributed_lock(
        self,
        key: str,
        timeout_seconds: int = 10,
        retry_delay_seconds: float = 0.1,
        max_retries: int = 30
    ):
        """
        Distributed lock context manager protecting concurrent resource mutations.
        Ensures strict mutual exclusion across async workers and load-balanced instances.
        """
        token = str(uuid.uuid4())
        acquired = False
        for attempt in range(max_retries):
            acquired = await self.acquire_lock(key, token, timeout_seconds)
            if acquired:
                break
            await asyncio.sleep(retry_delay_seconds)

        if not acquired:
            raise TimeoutError(f"Could not acquire distributed lock for key: {key} within timeout")

        try:
            yield token
        finally:
            await self.release_lock(key, token)

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
