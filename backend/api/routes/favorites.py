from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_customer
from backend.domain.models import User, FavoriteLocation

router = APIRouter(prefix="/favorites", tags=["Favorite Locations"])

class FavoriteCreateRequest(BaseModel):
    label: str = Field(..., json_schema_extra={"example": "Penthouse HQ"})
    address: str = Field(..., json_schema_extra={"example": "One BKC, G Block, Bandra Kurla Complex, Mumbai"})
    latitude: float = Field(..., json_schema_extra={"example": 19.0657})
    longitude: float = Field(..., json_schema_extra={"example": 72.8688})

class FavoriteUpdateRequest(BaseModel):
    label: Optional[str] = None
    address: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None

@router.get("")
async def list_favorites(
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    stmt = (
        select(FavoriteLocation)
        .where(FavoriteLocation.customer_id == user.id)
        .order_by(FavoriteLocation.created_at.desc())
    )
    res = await db.execute(stmt)
    favs = res.scalars().all()
    return [
        {
            "id": f.id,
            "label": f.label,
            "address": f.address,
            "latitude": f.latitude,
            "longitude": f.longitude,
            "created_at": f.created_at.isoformat()
        }
        for f in favs
    ]

@router.post("")
async def add_favorite(
    payload: FavoriteCreateRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    fav = FavoriteLocation(
        customer_id=user.id,
        label=payload.label,
        address=payload.address,
        latitude=payload.latitude,
        longitude=payload.longitude
    )
    db.add(fav)
    await db.commit()
    await db.refresh(fav)
    return {
        "status": "CREATED",
        "id": fav.id,
        "label": fav.label,
        "address": fav.address,
        "latitude": fav.latitude,
        "longitude": fav.longitude
    }

@router.put("/{fav_id}")
async def update_favorite(
    fav_id: str,
    payload: FavoriteUpdateRequest,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    fav = await db.get(FavoriteLocation, fav_id)
    if not fav or fav.customer_id != user.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Favorite location not found")

    if payload.label is not None:
        fav.label = payload.label
    if payload.address is not None:
        fav.address = payload.address
    if payload.latitude is not None:
        fav.latitude = payload.latitude
    if payload.longitude is not None:
        fav.longitude = payload.longitude

    await db.commit()
    await db.refresh(fav)
    return {
        "status": "UPDATED",
        "id": fav.id,
        "label": fav.label,
        "address": fav.address,
        "latitude": fav.latitude,
        "longitude": fav.longitude
    }

@router.delete("/{fav_id}")
async def delete_favorite(
    fav_id: str,
    user: User = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db)
):
    fav = await db.get(FavoriteLocation, fav_id)
    if not fav or fav.customer_id != user.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Favorite location not found")

    await db.delete(fav)
    await db.commit()
    return {"status": "DELETED", "id": fav_id}
