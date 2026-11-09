"""
H3 Hexagonal Geospatial Foundation Service for Vasantra Mobility.
Provides coordinate-to-hex conversion, resolution configuration,
k-ring disk expansion, boundary coordinates, and geographic distance utilities.
"""
import math
import logging
from typing import List, Set, Optional, Tuple
import h3

logger = logging.getLogger("backend.geospatial.h3")

# Default H3 resolution for urban E-rickshaw micro-mobility
# Resolution 8: Edge length ~461m, area ~0.737 km², hex diameter ~1.0 km.
# Resolution 7: Edge length ~1.22km, area ~5.16 km² (semi-rural / broad boundary).
# Resolution 9: Edge length ~174m, area ~0.105 km² (dense city core / high resolution).
DEFAULT_H3_RESOLUTION: int = 8
MIN_H3_RESOLUTION: int = 5
MAX_H3_RESOLUTION: int = 12

MAX_EXPANSION_RINGS: int = 5


class H3GeospatialService:
    """
    Authoritative geospatial indexing service based on Uber H3 discrete global grid system.
    """

    def __init__(self, resolution: int = DEFAULT_H3_RESOLUTION):
        self.resolution = self.validate_resolution(resolution)

    @staticmethod
    def validate_resolution(resolution: int) -> int:
        if not (MIN_H3_RESOLUTION <= resolution <= MAX_H3_RESOLUTION):
            logger.warning(
                f"Requested resolution {resolution} out of safe bounds [{MIN_H3_RESOLUTION}, {MAX_H3_RESOLUTION}]. "
                f"Falling back to default {DEFAULT_H3_RESOLUTION}."
            )
            return DEFAULT_H3_RESOLUTION
        return resolution

    @staticmethod
    def validate_coordinates(latitude: float, longitude: float) -> bool:
        """Validates that latitude is within [-90, 90] and longitude is within [-180, 180]."""
        if latitude is None or longitude is None:
            return False
        if not isinstance(latitude, (int, float)) or not isinstance(longitude, (int, float)):
            return False
        if math.isnan(latitude) or math.isnan(longitude) or math.isinf(latitude) or math.isinf(longitude):
            return False
        return -90.0 <= latitude <= 90.0 and -180.0 <= longitude <= 180.0

    def get_h3_cell(self, latitude: float, longitude: float, resolution: Optional[int] = None) -> Optional[str]:
        """
        Converts (latitude, longitude) coordinates to an H3 hexagonal cell identifier.
        Returns None if coordinates are invalid.
        """
        if not self.validate_coordinates(latitude, longitude):
            logger.warning(f"Invalid GPS coordinates provided for H3 conversion: lat={latitude}, lng={longitude}")
            return None

        res = self.validate_resolution(resolution) if resolution is not None else self.resolution
        try:
            return h3.latlng_to_cell(latitude, longitude, res)
        except Exception as e:
            logger.error(f"H3 cell conversion failed for lat={latitude}, lng={longitude}, res={res}: {e}")
            return None

    def get_cell_center(self, cell_id: str) -> Optional[Tuple[float, float]]:
        """
        Returns the (latitude, longitude) center of an H3 cell.
        """
        if not cell_id or not h3.is_valid_cell(cell_id):
            return None
        try:
            lat, lng = h3.cell_to_latlng(cell_id)
            return round(lat, 6), round(lng, 6)
        except Exception as e:
            logger.error(f"Error computing center for H3 cell {cell_id}: {e}")
            return None

    def get_cell_boundary(self, cell_id: str) -> List[Tuple[float, float]]:
        """
        Returns the list of (lat, lng) vertices defining the hexagon perimeter.
        """
        if not cell_id or not h3.is_valid_cell(cell_id):
            return []
        try:
            boundary = h3.cell_to_boundary(cell_id)
            return [(round(lat, 6), round(lng, 6)) for lat, lng in boundary]
        except Exception as e:
            logger.error(f"Error computing boundary for H3 cell {cell_id}: {e}")
            return []

    def get_h3_disk(self, cell_id: str, k: int = 1) -> Set[str]:
        """
        Returns all H3 cells within a grid distance <= k of the origin cell (inclusive).
        k=0: {origin} (1 cell)
        k=1: origin + 6 immediate neighbors (7 cells)
        k=2: origin + 6 + 12 (19 cells)
        k=3: origin + 6 + 12 + 18 (37 cells)
        """
        if not cell_id or not h3.is_valid_cell(cell_id):
            return set()
        k = max(0, min(k, MAX_EXPANSION_RINGS))
        try:
            return set(h3.grid_disk(cell_id, k))
        except Exception as e:
            logger.error(f"Error generating H3 grid disk for cell={cell_id}, k={k}: {e}")
            return {cell_id}

    def get_h3_ring(self, cell_id: str, k: int = 1) -> Set[str]:
        """
        Returns only the hollow ring of H3 cells at exact grid distance == k from the origin.
        k=0: {origin}
        k=1: 6 immediate neighbors
        k=2: 12 outer neighbors
        """
        if not cell_id or not h3.is_valid_cell(cell_id):
            return set()
        if k == 0:
            return {cell_id}
        k = max(1, min(k, MAX_EXPANSION_RINGS))
        try:
            return set(h3.grid_ring(cell_id, k))
        except Exception as e:
            logger.error(f"Error generating H3 grid ring for cell={cell_id}, k={k}: {e}")
            return set()

    def get_progressive_search_rings(self, origin_cell: str, max_rings: int = 3) -> List[Set[str]]:
        """
        Returns a list of consecutive concentric rings: [Ring 0, Ring 1, Ring 2, ...]
        Permits progressive, tiered nearby driver search without redundant re-evaluation.
        """
        if not origin_cell or not h3.is_valid_cell(origin_cell):
            return []

        max_rings = max(0, min(max_rings, MAX_EXPANSION_RINGS))
        rings: List[Set[str]] = [{origin_cell}]
        for r in range(1, max_rings + 1):
            ring_cells = self.get_h3_ring(origin_cell, r)
            if ring_cells:
                rings.append(ring_cells)
        return rings

    @staticmethod
    def haversine_distance_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
        """
        Computes great-circle distance in kilometers between two GPS coordinates.
        Used as early-stage heuristic filter before invoking road-network routing.
        """
        r = 6371.0
        dlat = math.radians(lat2 - lat1)
        dlng = math.radians(lng2 - lng1)
        a = (math.sin(dlat / 2) ** 2 +
             math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) *
             math.sin(dlng / 2) ** 2)
        c = 2 * math.asin(math.sqrt(max(0.0, min(1.0, a))))
        return round(r * c, 3)

    def get_approximate_hex_radius_km(self, resolution: Optional[int] = None) -> float:
        """
        Returns the approximate circumradius in kilometers for the configured resolution.
        """
        res = resolution if resolution is not None else self.resolution
        # H3 average hexagon edge length in km by resolution
        edge_lengths_km = {
            5: 8.54,
            6: 3.23,
            7: 1.22,
            8: 0.461,
            9: 0.174,
            10: 0.0659,
            11: 0.0249,
            12: 0.0094
        }
        return edge_lengths_km.get(res, 0.461)


# Singleton default instance
h3_service = H3GeospatialService(DEFAULT_H3_RESOLUTION)
