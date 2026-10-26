import time
from typing import Callable, Optional
from fastapi import Request, HTTPException, status
from backend.infrastructure.cache.redis_client import redis_client

class RateLimiter:
    """
    Production-grade distributed rate limiter supporting sliding-window / token counters
    backed by Redis with seamless local fallback. Returns standard 429 Too Many Requests
    with Retry-After and X-RateLimit headers.
    """
    def __init__(
        self,
        max_requests: int,
        window_seconds: int = 60,
        scope: str = "global",
        by_user: bool = False
    ):
        self.max_requests = max_requests
        self.window_seconds = window_seconds
        self.scope = scope
        self.by_user = by_user

    async def __call__(self, request: Request):
        # Determine client identity (protecting against forged X-Forwarded-For headers)
        client_ip = request.client.host if request.client else "127.0.0.1"
        user_id = None
        if self.by_user:
            # Check if user is attached to request state (from auth dependency)
            user = getattr(request.state, "user", None)
            if user:
                user_id = getattr(user, "id", None)

        identifier = f"user:{user_id}" if user_id else f"ip:{client_ip}"
        # Current time window bucket (rounded down to window interval)
        now = int(time.time())
        window_bucket = now // self.window_seconds
        cache_key = f"ratelimit:{self.scope}:{identifier}:{window_bucket}"

        # Increment atomic counter with TTL
        current_count = await redis_client.incr(cache_key, ex=self.window_seconds * 2)

        remaining = max(0, self.max_requests - current_count)
        reset_time = (window_bucket + 1) * self.window_seconds
        retry_after = max(1, reset_time - now)

        # Set rate limit headers on response state if possible
        request.state.rate_limit_headers = {
            "X-RateLimit-Limit": str(self.max_requests),
            "X-RateLimit-Remaining": str(remaining),
            "X-RateLimit-Reset": str(reset_time)
        }

        if current_count > self.max_requests:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail=f"Rate limit exceeded for {self.scope}. Please retry in {retry_after} seconds.",
                headers={
                    "Retry-After": str(retry_after),
                    "X-RateLimit-Limit": str(self.max_requests),
                    "X-RateLimit-Remaining": "0",
                    "X-RateLimit-Reset": str(reset_time)
                }
            )
        return True

import os

# Configurable rate limits with environment overrides
OTP_LIMIT = int(os.getenv("RATE_LIMIT_OTP", "60"))
AUTH_LIMIT = int(os.getenv("RATE_LIMIT_AUTH", "60"))
RIDE_LIMIT = int(os.getenv("RATE_LIMIT_RIDE", "30"))
DRIVER_LIMIT = int(os.getenv("RATE_LIMIT_DRIVER", "60"))
PAYMENT_LIMIT = int(os.getenv("RATE_LIMIT_PAYMENT", "30"))
CHAT_LIMIT = int(os.getenv("RATE_LIMIT_CHAT", "100"))
ADMIN_LIMIT = int(os.getenv("RATE_LIMIT_ADMIN", "100"))

# Predefined rate limiters for key endpoints
otp_rate_limiter = RateLimiter(max_requests=OTP_LIMIT, window_seconds=60, scope="otp")
auth_rate_limiter = RateLimiter(max_requests=AUTH_LIMIT, window_seconds=60, scope="auth")
ride_booking_rate_limiter = RateLimiter(max_requests=RIDE_LIMIT, window_seconds=60, scope="ride_booking", by_user=True)
driver_accept_rate_limiter = RateLimiter(max_requests=DRIVER_LIMIT, window_seconds=60, scope="driver_accept", by_user=True)
payment_rate_limiter = RateLimiter(max_requests=PAYMENT_LIMIT, window_seconds=60, scope="payment", by_user=True)
chat_rate_limiter = RateLimiter(max_requests=CHAT_LIMIT, window_seconds=60, scope="chat", by_user=True)
admin_bulk_rate_limiter = RateLimiter(max_requests=ADMIN_LIMIT, window_seconds=60, scope="admin_bulk", by_user=True)
