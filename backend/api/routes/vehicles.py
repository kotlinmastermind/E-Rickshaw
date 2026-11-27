import json
from typing import List, Optional, Dict, Any
from fastapi import APIRouter, Depends, Query, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_, or_

from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_admin
from backend.domain.models import (
    DriverProfile, DriverAvailability, DriverLocation, DriverVehicleAssignment,
    User, Vehicle, VehicleCategory,
)

router = APIRouter(prefix="/vehicles", tags=["Vehicles & Categories"])

# Standard luxury photo galleries per vehicle category (Feature 51)
DEFAULT_GALLERIES = {
    "SEDAN": [
        {
            "id": "gal_sedan_1",
            "tag": "EXTERIOR_HERO",
            "title": "Obsidian Black Executive Profile",
            "url": "https://images.unsplash.com/photo-1555215695-3004980ad54e?auto=format&fit=crop&w=1200&q=80",
            "caption": "Aerodynamic executive silhouette with privacy tinted acoustic double-glazed glass."
        },
        {
            "id": "gal_sedan_2",
            "tag": "COCKPIT",
            "title": "Chauffeur Command Cockpit",
            "url": "https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=1200&q=80",
            "caption": "MBUX digital cockpit with real-time route telemetry and hands-free communications."
        },
        {
            "id": "gal_sedan_3",
            "tag": "REAR_CABIN",
            "title": "Executive Nappa Leather Lounge",
            "url": "https://images.unsplash.com/photo-1542282088-72c9c27ed0cd?auto=format&fit=crop&w=1200&q=80",
            "caption": "Perforated ventilated seats with individual 3-zone climate control and ambient lighting."
        },
        {
            "id": "gal_sedan_4",
            "tag": "TRUNK",
            "title": "Luggage Trunk Compartment",
            "url": "https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=1200&q=80",
            "caption": "Generous 540-liter boot space accommodating 2 large suitcases and 2 carry-ons."
        }
    ],
    "LUXURY_SUV": [
        {
            "id": "gal_suv_1",
            "tag": "EXTERIOR_HERO",
            "title": "Prestige Long-Wheelbase Stance",
            "url": "https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=1200&q=80",
            "caption": "Imposing presence with electronically deployable side-steps and panoramic roof."
        },
        {
            "id": "gal_suv_2",
            "tag": "REAR_CABIN",
            "title": "First-Class Rear Executive Suite",
            "url": "https://images.unsplash.com/photo-1563720223185-11003d516935?auto=format&fit=crop&w=1200&q=80",
            "caption": "Deep-pile mohair carpeting, semi-aniline leather recliners with hot-stone massage."
        },
        {
            "id": "gal_suv_3",
            "tag": "AMENITIES",
            "title": "Rear Center Console Amenities",
            "url": "https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=1200&q=80",
            "caption": "Refrigerated console compartment for Evian mineral water and dual 11.4-inch HD touchscreens."
        },
        {
            "id": "gal_suv_4",
            "tag": "TRUNK",
            "title": "Full Luggage Cargo Capacity",
            "url": "https://images.unsplash.com/photo-1555215695-3004980ad54e?auto=format&fit=crop&w=1200&q=80",
            "caption": "Massive 850-liter trunk capacity ideal for 4 checked suitcases, golf bags, and baby strollers."
        }
    ],
    "EXECUTIVE_LIMO": [
        {
            "id": "gal_limo_1",
            "tag": "EXTERIOR_HERO",
            "title": "Sovereign Maybach Limousine",
            "url": "https://images.unsplash.com/photo-1563720223185-11003d516935?auto=format&fit=crop&w=1200&q=80",
            "caption": "Two-tone handcrafted finish with Maybach radiator grille and soft-close power doors."
        },
        {
            "id": "gal_limo_2",
            "tag": "REAR_CABIN",
            "title": "Reclining Ottoman First-Class Cabin",
            "url": "https://images.unsplash.com/photo-1542282088-72c9c27ed0cd?auto=format&fit=crop&w=1200&q=80",
            "caption": "43.5-degree recline angle with calf rests, neck warmers, and active road noise cancellation."
        },
        {
            "id": "gal_limo_3",
            "tag": "CHAMPAGNE_BAR",
            "title": "Chilled Champagne Console & Silver Flutes",
            "url": "https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=1200&q=80",
            "caption": "Integrated compressor refrigerator with bespoke silver-plated champagne flutes."
        }
    ],
    "ULTRA_LUXURY_EV": [
        {
            "id": "gal_ev_1",
            "tag": "EXTERIOR_HERO",
            "title": "Whisper-Quiet Electric Flagship",
            "url": "https://images.unsplash.com/photo-1555215695-3004980ad54e?auto=format&fit=crop&w=1200&q=80",
            "caption": "Zero-emissions luxury performance with illuminated kidney grille and crystal headlamps."
        },
        {
            "id": "gal_ev_2",
            "tag": "THEATRE_SCREEN",
            "title": "31.3-Inch 8K Theater Display",
            "url": "https://images.unsplash.com/photo-1542282088-72c9c27ed0cd?auto=format&fit=crop&w=1200&q=80",
            "caption": "Ceiling-mounted ultra-wide panoramic cinema screen with Bowers & Wilkins Diamond 4D audio."
        }
    ]
}

ACCESSIBILITY_SPECS = {
    "SEDAN": {
        "step_free_height_cm": 38.0,
        "door_opening_angle_degrees": 78,
        "wheelchair_hoist": False,
        "swivel_seat_available": False,
        "ramp_equipped": False,
        "guide_dog_clearance": True,
        "tactile_controls": True,
        "low_floor_entry": True,
        "summary": "Accessible sedan with wide door aperture and low step-in threshold suitable for passengers with limited mobility."
    },
    "LUXURY_SUV": {
        "step_free_height_cm": 32.0,  # with air suspension kneel
        "door_opening_angle_degrees": 85,
        "wheelchair_hoist": True,
        "swivel_seat_available": True,
        "ramp_equipped": False,
        "guide_dog_clearance": True,
        "tactile_controls": True,
        "low_floor_entry": False,
        "summary": "Electronically lowers chassis by 50mm via air suspension for effortless boarding. Spacious cabin for service animals."
    },
    "EXECUTIVE_LIMO": {
        "step_free_height_cm": 36.0,
        "door_opening_angle_degrees": 82,
        "wheelchair_hoist": False,
        "swivel_seat_available": False,
        "ramp_equipped": False,
        "guide_dog_clearance": True,
        "tactile_controls": True,
        "low_floor_entry": True,
        "summary": "Ultra-wide door opening with automated chauffeur curb assistance, soft-close doors, and extended rear legroom."
    },
    "ULTRA_LUXURY_EV": {
        "step_free_height_cm": 35.0,
        "door_opening_angle_degrees": 84,
        "wheelchair_hoist": False,
        "swivel_seat_available": True,
        "ramp_equipped": True,
        "guide_dog_clearance": True,
        "tactile_controls": True,
        "low_floor_entry": True,
        "summary": "Automated electric open/close doors with obstacle detection and dedicated flat floor architecture."
    }
}


@router.get("/categories")
async def list_vehicle_categories(
    passengers: Optional[int] = Query(None, description="Minimum passenger capacity required (Feature 72)"),
    luggage_large: Optional[int] = Query(None, description="Minimum large checked bags capacity required (Feature 53, 73)"),
    luggage_small: Optional[int] = Query(None, description="Minimum small carry-on bags capacity required (Feature 53, 73)"),
    wheelchair_accessible: Optional[bool] = Query(None, description="Filter for wheelchair accessible vehicles (Feature 55)"),
    step_free: Optional[bool] = Query(None, description="Filter for low step-in height <= 38cm (Feature 56)"),
    electric_only: Optional[bool] = Query(None, description="Filter for 100% electric vehicles (Feature 67)"),
    low_emissions_only: Optional[bool] = Query(None, description="Filter for low emissions <= 95g/km (Feature 68)"),
    pet_friendly: Optional[bool] = Query(None, description="Filter for pet-friendly certified fleet (Feature 74)"),
    extra_legroom: Optional[bool] = Query(None, description="Filter for vehicles with >= 42-inch legroom (Feature 58)"),
    child_seat: Optional[bool] = Query(None, description="Filter for vehicles with child seat installation (Feature 54)"),
    db: AsyncSession = Depends(get_db)
):
    """
    Lists luxury vehicle classes with rich specs, capacity filters, and accessibility compliance.
    """
    stmt = select(VehicleCategory).where(VehicleCategory.is_active == True).order_by(VehicleCategory.sort_order.asc())
    res = await db.execute(stmt)
    categories = res.scalars().all()

    filtered = []
    for c in categories:
        # Dynamic filtering rules
        if isinstance(passengers, int) and c.capacity < passengers:
            continue
        if isinstance(luggage_large, int) and getattr(c, "luggage_large_capacity", 2) < luggage_large:
            continue
        if isinstance(luggage_small, int) and getattr(c, "luggage_small_capacity", 2) < luggage_small:
            continue
        if wheelchair_accessible is True and not getattr(c, "wheelchair_accessible", False):
            continue
        if step_free is True and getattr(c, "step_free_height_cm", 40.0) > 38.0:
            continue
        if electric_only is True and not getattr(c, "is_electric", False):
            continue
        if low_emissions_only is True and getattr(c, "emissions_g_per_km", 120.0) > 95.0:
            continue
        if pet_friendly is True and not getattr(c, "pet_friendly", True):
            continue
        if extra_legroom is True and getattr(c, "legroom_inches", 40.0) < 42.0:
            continue
        if child_seat is True and not getattr(c, "child_seat_available", True):
            continue

        gallery = DEFAULT_GALLERIES.get(c.code, DEFAULT_GALLERIES.get("SEDAN", []))
        acc_spec = ACCESSIBILITY_SPECS.get(c.code, ACCESSIBILITY_SPECS["SEDAN"])

        colors = ["Obsidian Black", "Pearl White", "Royal Navy Blue", "Grigio Gray"]
        amenities = [
            "Chilled Evian Sparkling Water",
            "Braided Multi-Device Charging Kit",
            "The Financial Times Daily Print",
            "Cashmere Lap Blanket",
            "Organic Amber Wood Fragrance Diffuser"
        ]

        filtered.append({
            "id": c.id,
            "code": c.code,
            "name": c.name,
            "description": c.description,
            "icon_url": c.icon_url,
            "capacity": c.capacity,
            "sort_order": c.sort_order,
            # Part 3 Specs (Features 51-74)
            "legroom_inches": getattr(c, "legroom_inches", 40.0),
            "quietness_db": getattr(c, "quietness_db", 54.0),
            "luggage_large_capacity": getattr(c, "luggage_large_capacity", 2),
            "luggage_small_capacity": getattr(c, "luggage_small_capacity", 2),
            "has_wifi": getattr(c, "has_wifi", True),
            "has_massage_seats": getattr(c, "has_massage_seats", c.code in ["EXECUTIVE_LIMO", "LUXURY_SUV"]),
            "has_champagne_cooler": getattr(c, "has_champagne_cooler", c.code == "EXECUTIVE_LIMO"),
            "has_reclining_seats": getattr(c, "has_reclining_seats", True),
            "is_electric": getattr(c, "is_electric", c.code == "ULTRA_LUXURY_EV"),
            "emissions_g_per_km": getattr(c, "emissions_g_per_km", 0.0 if c.code == "ULTRA_LUXURY_EV" else 115.0),
            "wheelchair_accessible": getattr(c, "wheelchair_accessible", c.code in ["LUXURY_SUV", "ULTRA_LUXURY_EV"]),
            "step_free_height_cm": getattr(c, "step_free_height_cm", acc_spec["step_free_height_cm"]),
            "pet_friendly": getattr(c, "pet_friendly", True),
            "child_seat_available": getattr(c, "child_seat_available", True),
            "available_colors": colors,
            "available_amenities": amenities,
            "gallery_preview": gallery[:3]
        })

    return filtered


class CategoryCreatePayload(BaseModel):
    code: str = Field(..., min_length=2, max_length=32)
    name: str = Field(..., min_length=2, max_length=64)
    description: str = Field("", max_length=512)
    capacity: int = Field(4, ge=1, le=10)
    sort_order: int = Field(1, ge=1)
    is_electric: bool = True
    icon_url: Optional[str] = "https://images.unsplash.com/photo-1558981806-ec527fa84c39"


class CategoryUpdatePayload(BaseModel):
    name: Optional[str] = Field(None, min_length=2, max_length=64)
    description: Optional[str] = Field(None, max_length=512)
    capacity: Optional[int] = Field(None, ge=1, le=10)
    sort_order: Optional[int] = Field(None, ge=1)
    is_active: Optional[bool] = None
    is_electric: Optional[bool] = None
    icon_url: Optional[str] = None


@router.post("/categories")
async def create_vehicle_category(
    payload: CategoryCreatePayload,
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    cat = VehicleCategory(
        code=payload.code.strip().upper(),
        name=payload.name.strip(),
        description=payload.description.strip(),
        capacity=payload.capacity,
        sort_order=payload.sort_order,
        is_electric=payload.is_electric,
        icon_url=payload.icon_url,
        is_active=True
    )
    db.add(cat)
    await db.commit()
    await db.refresh(cat)
    return {"status": "CREATED", "id": cat.id, "name": cat.name, "code": cat.code}


@router.put("/categories/{category_id}")
async def update_vehicle_category(
    category_id: str,
    payload: CategoryUpdatePayload,
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(VehicleCategory).where(VehicleCategory.id == category_id)
    cat = (await db.execute(stmt)).scalar_one_or_none()
    if not cat:
        raise HTTPException(404, "Vehicle category not found")

    if payload.name is not None:
        cat.name = payload.name.strip()
    if payload.description is not None:
        cat.description = payload.description.strip()
    if payload.capacity is not None:
        cat.capacity = payload.capacity
    if payload.sort_order is not None:
        cat.sort_order = payload.sort_order
    if payload.is_active is not None:
        cat.is_active = payload.is_active
    if payload.is_electric is not None:
        cat.is_electric = payload.is_electric
    if payload.icon_url is not None:
        cat.icon_url = payload.icon_url

    await db.commit()
    await db.refresh(cat)
    return {
        "status": "UPDATED",
        "id": cat.id,
        "name": cat.name,
        "code": cat.code,
        "is_active": cat.is_active
    }


@router.delete("/categories/{category_id}")
async def delete_vehicle_category(
    category_id: str,
    admin=Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    stmt = select(VehicleCategory).where(VehicleCategory.id == category_id)
    cat = (await db.execute(stmt)).scalar_one_or_none()
    if not cat:
        raise HTTPException(404, "Vehicle category not found")
    await db.delete(cat)
    await db.commit()
    return {"status": "DELETED", "id": category_id}


@router.get("/categories/{code}/gallery")
@router.get("/{code}/gallery")
async def get_category_gallery(code: str, db: AsyncSession = Depends(get_db)):
    """
    Feature 51: Vehicle photos and interior gallery with multi-angle high-resolution captures.
    """
    code_upper = code.upper()
    gallery = DEFAULT_GALLERIES.get(code_upper)
    if not gallery:
        gallery = DEFAULT_GALLERIES.get("SEDAN", [])

    return {
        "category_code": code_upper,
        "total_photos": len(gallery),
        "photos": gallery,
        "gallery_images": [
            {"angle": p.get("tag", "EXTERIOR"), "label": p.get("title", ""), "url": p.get("url", "")}
            for p in gallery
        ]
    }


@router.get("/categories/compare")
@router.get("/compare")
async def compare_vehicle_categories(db: AsyncSession = Depends(get_db)):
    """
    Feature 52: Vehicle feature comparison matrix across luxury classes.
    """
    stmt = select(VehicleCategory).where(VehicleCategory.is_active == True).order_by(VehicleCategory.sort_order.asc())
    res = await db.execute(stmt)
    categories = res.scalars().all()

    comparison_matrix = []
    for c in categories:
        acc = ACCESSIBILITY_SPECS.get(c.code, ACCESSIBILITY_SPECS["SEDAN"])
        comparison_matrix.append({
            "id": c.id,
            "code": c.code,
            "name": c.name,
            "description": c.description,
            "icon_url": c.icon_url,
            "capacity": c.capacity,
            "legroom_inches": getattr(c, "legroom_inches", 40.0),
            "quietness_db": getattr(c, "quietness_db", 54.0),
            "luggage_large_capacity": getattr(c, "luggage_large_capacity", 2),
            "luggage_small_capacity": getattr(c, "luggage_small_capacity", 2),
            "has_wifi": getattr(c, "has_wifi", True),
            "has_massage_seats": getattr(c, "has_massage_seats", c.code in ["EXECUTIVE_LIMO", "LUXURY_SUV"]),
            "has_champagne_cooler": getattr(c, "has_champagne_cooler", c.code == "EXECUTIVE_LIMO"),
            "is_electric": getattr(c, "is_electric", c.code == "ULTRA_LUXURY_EV"),
            "emissions_g_per_km": getattr(c, "emissions_g_per_km", 0.0 if c.code == "ULTRA_LUXURY_EV" else 115.0),
            "wheelchair_accessible": getattr(c, "wheelchair_accessible", c.code in ["LUXURY_SUV", "ULTRA_LUXURY_EV"]),
            "step_free_height_cm": acc["step_free_height_cm"],
            "pet_friendly": getattr(c, "pet_friendly", True),
            "child_seat_available": getattr(c, "child_seat_available", True),
            "signature_amenities": [
                "Chilled Mineral Water",
                "Braided High-Speed Chargers",
                "Dedicated Acoustic Partition"
            ],
            "ideal_for": "Executive airport transfers & city transit" if c.code == "SEDAN" else (
                "VIP delegations & family travel with luggage" if c.code == "LUXURY_SUV" else (
                    "State visits & ceremonial luxury galas" if c.code == "EXECUTIVE_LIMO" else "Eco-conscious high-tech private ride"
                )
            )
        })

    return {
        "comparison_title": "Grand Chauffeur Luxury Fleet Specification Matrix",
        "categories_compared": len(comparison_matrix),
        "matrix": comparison_matrix,
        "categories": comparison_matrix
    }


@router.get("/categories/{code}/accessibility")
async def get_category_accessibility_details(code: str, db: AsyncSession = Depends(get_db)):
    """
    Feature 69: Vehicle accessibility details for passengers with physical, sensory, or mobility needs.
    """
    code_upper = code.upper()
    acc = ACCESSIBILITY_SPECS.get(code_upper, ACCESSIBILITY_SPECS["SEDAN"])

    return {
        "category_code": code_upper,
        "accessibility_specs": acc,
        "certified_compliance": "Bureau of Indian Standards VIP Transport Accessibility Guidelines 2024",
        "driver_training_verified": True,
        "service_animals_welcome": True,
        "luggage_assistance_guaranteed": True
    }


import math

@router.get("/nearby")
async def get_nearby_vehicles(
    latitude: float = Query(..., ge=-90, le=90, description="Customer latitude"),
    longitude: float = Query(..., ge=-180, le=180, description="Customer longitude"),
    radius_km: float = Query(5.0, ge=0.5, le=50.0),
    category: Optional[str] = Query(None),
    db: AsyncSession = Depends(get_db)
):
    from datetime import datetime, timezone
    from sqlalchemy import desc

    now = datetime.now(timezone.utc)
    stmt = (
        select(DriverProfile, DriverAvailability, DriverLocation, User, Vehicle, VehicleCategory)
        .join(DriverAvailability, DriverAvailability.driver_id == DriverProfile.id)
        .join(DriverLocation, DriverLocation.driver_id == DriverProfile.id)
        .join(User, User.id == DriverProfile.user_id)
        .join(
            DriverVehicleAssignment,
            and_(
                DriverVehicleAssignment.driver_id == DriverProfile.id,
                DriverVehicleAssignment.is_active.is_(True),
            ),
        )
        .join(
            Vehicle,
            and_(
                Vehicle.id == DriverVehicleAssignment.vehicle_id,
                Vehicle.is_active.is_(True),
            ),
        )
        .join(VehicleCategory, VehicleCategory.id == Vehicle.category_id)
        .where(
            DriverProfile.approval_status == "APPROVED",
            User.is_active.is_(True),
            DriverAvailability.is_online.is_(True),
            DriverAvailability.is_busy.is_(False),
        )
        .order_by(desc(DriverLocation.recorded_at))
    )
    if category:
        stmt = stmt.where(
            or_(VehicleCategory.code == category, VehicleCategory.id == category)
        )
    res = await db.execute(stmt)
    rows = res.all()

    seen_drivers = set()
    vehicles = []

    for prof, avail, loc, user, vehicle, vehicle_category in rows:
        if prof.id in seen_drivers:
            continue
        seen_drivers.add(prof.id)

        # 15 minutes se purani GPS ping ko ignore karein
        rec_at = loc.recorded_at if loc.recorded_at.tzinfo else loc.recorded_at.replace(tzinfo=timezone.utc)
        if (now - rec_at).total_seconds() > 900:
            continue

        # Haversine distance
        dlat_rad = math.radians(loc.latitude - latitude)
        dlng_rad = math.radians(loc.longitude - longitude)
        a = (
            math.sin(dlat_rad / 2) ** 2
            + math.cos(math.radians(latitude))
            * math.cos(math.radians(loc.latitude))
            * math.sin(dlng_rad / 2) ** 2
        )
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
        dist_km = round(6371.0 * c, 2)

        if dist_km <= radius_km:
            eta_mins = max(int(round((dist_km / 22.0) * 60.0)), 2)
            vehicles.append({
                "id": prof.id,
                "driver_name": user.full_name.strip() if user.full_name and user.full_name.strip() else None,
                "driver_phone": user.phone_number,
                "rating": (
                    round(float(prof.rating_average), 2)
                    if int(prof.rating_count or 0) > 0 and prof.rating_average is not None
                    else None
                ),
                "category_code": vehicle_category.code,
                "vehicle_type_name": vehicle_category.name,
                "model_name": " ".join(part for part in (vehicle.make, vehicle.model) if part) or None,
                "license_plate": vehicle.license_plate,
                "icon_type": "e_rickshaw" if "RICKSHAW" in vehicle_category.code.upper() else "vehicle",
                "capacity": vehicle_category.capacity,
                "latitude": loc.latitude,
                "longitude": loc.longitude,
                "bearing": loc.heading,
                "battery_level": None,
                "distance_km": dist_km,
                "eta_minutes": eta_mins,
                "status": "AVAILABLE"
            })

    return {
        "customer_latitude": latitude,
        "customer_longitude": longitude,
        "radius_km": radius_km,
        "total_available": len(vehicles),
        "vehicles": vehicles
    }
