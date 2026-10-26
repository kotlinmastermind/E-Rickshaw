from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.application.services.fare_service import FareService

router = APIRouter(prefix="/fares", tags=["Fare Calculation"])

class FareEstimateRequest(BaseModel):
    category_id: str
    pickup_latitude: float = Field(..., ge=-90.0, le=90.0)
    pickup_longitude: float = Field(..., ge=-180.0, le=180.0)
    destination_latitude: float = Field(..., ge=-90.0, le=90.0)
    destination_longitude: float = Field(..., ge=-180.0, le=180.0)

@router.post("/estimate")
async def calculate_fare_estimate(payload: FareEstimateRequest, db: AsyncSession = Depends(get_db)):
    service = FareService(db)
    return await service.calculate_estimate(
        category_id=payload.category_id,
        pickup_lat=payload.pickup_latitude,
        pickup_lng=payload.pickup_longitude,
        dest_lat=payload.destination_latitude,
        dest_lng=payload.destination_longitude
    )
