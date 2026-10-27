"""
Tourism & Historical Places API Routes.
Provides curated regional historical & tourism destinations with 7-day automatic rotation recommendations.
"""
import math
import time
from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Query, Depends
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, or_

from backend.infrastructure.database.session import get_db
from backend.domain.models import TourismPlace

router = APIRouter(prefix="/tourism", tags=["Tourism & Historical Places"])

def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    R = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2.0) ** 2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2.0) ** 2
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return R * c

class TourismPlaceOut(BaseModel):
    id: str
    title: str
    category: str
    short_description: str
    photo_url: str
    latitude: float
    longitude: float
    address: str
    city: str
    rating: float
    review_count: int
    entry_fee: str
    timings: str
    tag: str
    week_rotation_group: int
    is_featured: bool
    distance_km: Optional[float] = None
    approx_fare_rupees: Optional[int] = None

class TourismWeeklyResponse(BaseModel):
    current_week_rotation: int
    days_until_next_rotation: int
    rotation_interval_days: int = 7
    rotation_theme: str
    featured_places: List[TourismPlaceOut]
    all_places: List[TourismPlaceOut]

ROTATION_THEMES = {
    0: "Ancient Empires & Ashokan Heritage (Week 1)",
    1: "Ancient Universities & Sacred Shrines (Week 2)",
    2: "Mauryan Caves & Architectural Wonders (Week 3)",
    3: "Eco Lakes, Buddha Statues & Mughal Grandeur (Week 4)"
}

@router.get("/places", response_model=TourismWeeklyResponse)
async def get_tourism_places(
    category: Optional[str] = Query(None, description="HISTORICAL, TOURISM, SPIRITUAL_HERITAGE"),
    current_lat: Optional[float] = Query(None, ge=-90, le=90),
    current_lng: Optional[float] = Query(None, ge=-180, le=180),
    week_offset: int = Query(0, ge=0, le=10),
    db: AsyncSession = Depends(get_db)
):
    """
    Fetch curated famous historical and tourism places.
    Automatically rotates featured recommendations every 7 days (7-day epoch cycle).
    """
    now_epoch = time.time()
    epoch_days = int(now_epoch // 86400)
    current_week_rotation = ((epoch_days // 7) + week_offset) % 4
    days_until_next_rotation = 7 - (epoch_days % 7)

    stmt = select(TourismPlace)
    if category:
        stmt = stmt.where(TourismPlace.category == category.upper())
    
    result = await db.execute(stmt)
    places = result.scalars().all()

    def serialize_place(p: TourismPlace) -> TourismPlaceOut:
        dist_km = None
        approx_fare = None
        if current_lat is not None and current_lng is not None:
            raw_dist = haversine_km(current_lat, current_lng, p.latitude, p.longitude)
            dist_km = round(raw_dist, 1)
            # Standard E-Rickshaw formula: base ₹20 + ₹10/km
            approx_fare = max(25, int(20 + (dist_km * 10)))

        return TourismPlaceOut(
            id=p.id,
            title=p.title,
            category=p.category,
            short_description=p.short_description,
            photo_url=p.photo_url,
            latitude=p.latitude,
            longitude=p.longitude,
            address=p.address,
            city=p.city,
            rating=p.rating,
            review_count=p.review_count,
            entry_fee=p.entry_fee,
            timings=p.timings,
            tag=p.tag,
            week_rotation_group=p.week_rotation_group,
            is_featured=p.is_featured,
            distance_km=dist_km,
            approx_fare_rupees=approx_fare
        )

    all_out = [serialize_place(p) for p in places]
    featured_out = [p for p in all_out if p.week_rotation_group == current_week_rotation]
    if not featured_out and all_out:
        featured_out = all_out[:4]

    theme = ROTATION_THEMES.get(current_week_rotation, "Famous Regional Wonders")

    return TourismWeeklyResponse(
        current_week_rotation=current_week_rotation,
        days_until_next_rotation=days_until_next_rotation,
        rotation_interval_days=7,
        rotation_theme=theme,
        featured_places=featured_out,
        all_places=all_out
    )
