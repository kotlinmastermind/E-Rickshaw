from typing import Optional, List
from pydantic import BaseModel, Field

class VehicleCategoryDto(BaseModel):
    """Standardized vehicle category schema across API & apps."""
    id: str
    code: str
    name: str
    description: Optional[str] = None
    icon_url: Optional[str] = None
    capacity: int = 4
    sort_order: int = 1
    is_electric: bool = True
    emissions_g_per_km: float = 0.0
    legroom_inches: float = 36.0
    quietness_db: float = 45.0
    luggage_large_capacity: int = 1
    luggage_small_capacity: int = 2
    wheelchair_accessible: bool = False
    pet_friendly: bool = True
    child_seat_available: bool = False

class VehicleDto(BaseModel):
    """Standardized vehicle representation."""
    id: str
    category_id: str
    driver_id: Optional[str] = None
    make: str
    model: str
    license_plate: str
    color: str
    year: int
    is_active: bool = True
    battery_percentage: Optional[int] = 100

