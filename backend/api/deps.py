from typing import Optional
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.infrastructure.database.session import get_db
from backend.domain.models import User, Session
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

async def get_current_admin(user: User = Depends(require_role("admin"))) -> User:
    return user
