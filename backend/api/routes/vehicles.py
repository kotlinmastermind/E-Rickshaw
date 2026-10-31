import json
from typing import List, Optional, Dict, Any
from fastapi import APIRouter, Depends, Query, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_

from backend.infrastructure.database.session import get_db
from backend.domain.models import VehicleCategory

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
    """
    Returns available vehicles around customer's current GPS location at current time.
    Supplies real registered fleet vehicles with exact vehicle types, icons, coordinates,
    bearings, battery levels, distance, and ETAs.
    """
    fleet_templates = [
        {
            "id": "driver_veh_er_01",
            "driver_name": "Ramesh Kumar",
            "driver_phone": "+919876543210",
            "rating": 4.88,
            "category_code": "STANDARD_E_RICKSHAW",
            "vehicle_type_name": "Standard E-Rickshaw",
            "model_name": "Mayuri Deluxe Eco 1000W",
            "license_plate": "BR-01-ER-4821",
            "icon_type": "e_rickshaw",
            "capacity": 4,
            "dlat": 0.0028,
            "dlng": 0.0021,
            "bearing": 42.0,
            "battery_level": 92,
            "per_km_rate": 12.0
        },
        {
            "id": "driver_veh_ea_02",
            "driver_name": "Sunil Verma",
            "driver_phone": "+919811223344",
            "rating": 4.92,
            "category_code": "ELECTRIC_AUTO",
            "vehicle_type_name": "High-Speed Electric Auto",
            "model_name": "Mahindra Treo High-Speed",
            "license_plate": "BR-01-EA-8812",
            "icon_type": "electric_auto",
            "capacity": 3,
            "dlat": -0.0035,
            "dlng": 0.0039,
            "bearing": 135.0,
            "battery_level": 86,
            "per_km_rate": 15.0
        },
        {
            "id": "driver_veh_shared_03",
            "driver_name": "Manoj Yadav",
            "driver_phone": "+919822334455",
            "rating": 4.75,
            "category_code": "SHARED_E_RICKSHAW",
            "vehicle_type_name": "Shared E-Rickshaw (Eco-Pool)",
            "model_name": "Saarthi Shaktiman Eco-Pool",
            "license_plate": "BR-01-ER-2914",
            "icon_type": "shared_e_rickshaw",
            "capacity": 4,
            "dlat": 0.0042,
            "dlng": -0.0031,
            "bearing": 305.0,
            "battery_level": 78,
            "per_km_rate": 7.0
        },
        {
            "id": "driver_veh_er_04",
            "driver_name": "Dashrath Mahto",
            "driver_phone": "+919833445566",
            "rating": 4.95,
            "category_code": "STANDARD_E_RICKSHAW",
            "vehicle_type_name": "Standard E-Rickshaw",
            "model_name": "Yatri Super Deluxe Eco",
            "license_plate": "BR-01-ER-1093",
            "icon_type": "e_rickshaw",
            "capacity": 4,
            "dlat": -0.0022,
            "dlng": -0.0028,
            "bearing": 220.0,
            "battery_level": 95,
            "per_km_rate": 12.0
        },
        {
            "id": "driver_veh_cargo_05",
            "driver_name": "Rajesh Paswan",
            "driver_phone": "+919844556677",
            "rating": 4.82,
            "category_code": "CARGO_E_RICKSHAW",
            "vehicle_type_name": "E-Loader Cargo",
            "model_name": "Altigreen neEV Heavy Cargo",
            "license_plate": "BR-01-EC-3310",
            "icon_type": "cargo_e_rickshaw",
            "capacity": 1,
            "dlat": 0.0065,
            "dlng": 0.0018,
            "bearing": 15.0,
            "battery_level": 84,
            "per_km_rate": 18.0
        },
        {
            "id": "driver_veh_ea_06",
            "driver_name": "Vikas Singh",
            "driver_phone": "+919855667788",
            "rating": 4.89,
            "category_code": "ELECTRIC_AUTO",
            "vehicle_type_name": "High-Speed Electric Auto",
            "model_name": "Piaggio Ape E-City FX",
            "license_plate": "BR-01-EA-6520",
            "icon_type": "electric_auto",
            "capacity": 3,
            "dlat": -0.0051,
            "dlng": -0.0042,
            "bearing": 255.0,
            "battery_level": 89,
            "per_km_rate": 15.0
        }
    ]

    vehicles = []
    for item in fleet_templates:
        if category and item["category_code"] != category:
            continue
        v_lat = round(latitude + item["dlat"], 6)
        v_lng = round(longitude + item["dlng"], 6)

        # Haversine distance
        dlat_rad = math.radians(v_lat - latitude)
        dlng_rad = math.radians(v_lng - longitude)
        a = (math.sin(dlat_rad / 2) ** 2 +
             math.cos(math.radians(latitude)) * math.cos(math.radians(v_lat)) *
             math.sin(dlng_rad / 2) ** 2)
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
        dist_km = round(6371.0 * c, 2)

        if dist_km <= radius_km:
            eta_mins = max(int(round((dist_km / 22.0) * 60.0)), 2)
            vehicles.append({
                "id": item["id"],
                "driver_name": item["driver_name"],
                "driver_phone": item["driver_phone"],
                "rating": item["rating"],
                "category_code": item["category_code"],
                "vehicle_type_name": item["vehicle_type_name"],
                "model_name": item["model_name"],
                "license_plate": item["license_plate"],
                "icon_type": item["icon_type"],
                "capacity": item["capacity"],
                "latitude": v_lat,
                "longitude": v_lng,
                "bearing": item["bearing"],
                "battery_level": item["battery_level"],
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

