from typing import Optional
from fastapi import APIRouter, Query, HTTPException, status
from pydantic import BaseModel
from backend.integrations.maps.provider import get_maps_provider

router = APIRouter(prefix="/location", tags=["Location & Maps"])

class ReverseGeocodePayload(BaseModel):
    latitude: float
    longitude: float

@router.get("/autocomplete")
async def autocomplete(q: str = Query(..., min_length=1, description="Search landmark or address query")):
    provider = get_maps_provider()
    results = await provider.autocomplete_places(q)
    return {"query": q, "results": results}

@router.post("/reverse-geocode")
async def reverse_geocode(payload: ReverseGeocodePayload):
    provider = get_maps_provider()
    address = await provider.reverse_geocode(payload.latitude, payload.longitude)
    return {
        "latitude": payload.latitude,
        "longitude": payload.longitude,
        "formatted_address": address
    }

@router.get("/route")
async def get_route(
    origin_lat: float = Query(...),
    origin_lng: float = Query(...),
    dest_lat: float = Query(...),
    dest_lng: float = Query(...)
):
    provider = get_maps_provider()
    route = await provider.get_route(origin_lat, origin_lng, dest_lat, dest_lng)
    return route.to_dict()
