from typing import Optional
from datetime import datetime
from pydantic import BaseModel, Field

class FareEstimateRequest(BaseModel):
    """Standardized payload for ride fare estimation."""
    category_id: str = Field(..., description="Target vehicle category UUID")
    pickup_latitude: float = Field(..., ge=-90.0, le=90.0)
    pickup_longitude: float = Field(..., ge=-180.0, le=180.0)
    destination_latitude: float = Field(..., ge=-90.0, le=90.0)
    destination_longitude: float = Field(..., ge=-180.0, le=180.0)

class FareEstimateResponse(BaseModel):
    """Standardized estimate response returned to apps."""
    estimate_id: str
    fare_rule_id: Optional[str] = None
    category_id: str
    distance_km: float
    estimated_duration_minutes: float
    base_fare_rupees: float
    distance_charge_rupees: float
    time_charge_rupees: float
    tax_rupees: float
    amenities_fee_rupees: float
    estimated_fare_rupees: float
    estimated_fare_cents: int
    currency: str = "INR"
    route_polyline: Optional[str] = None
    confidence_level: str = "HIGH"
    confidence_percentage: int = 95
    seconds_remaining: int = 900

