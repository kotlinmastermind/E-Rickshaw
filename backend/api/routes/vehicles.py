from typing import List
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.infrastructure.database.session import get_db
from backend.domain.models import VehicleCategory

router = APIRouter(prefix="/vehicles", tags=["Vehicles & Categories"])

@router.get("/categories")
async def list_vehicle_categories(db: AsyncSession = Depends(get_db)):
    stmt = select(VehicleCategory).where(VehicleCategory.is_active == True).order_by(VehicleCategory.sort_order.asc())
    res = await db.execute(stmt)
    categories = res.scalars().all()
    return [
        {
            "id": c.id,
            "code": c.code,
            "name": c.name,
            "description": c.description,
            "icon_url": c.icon_url,
            "capacity": c.capacity,
            "sort_order": c.sort_order
        }
        for c in categories
    ]
