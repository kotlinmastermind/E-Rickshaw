import pytest
import math
from backend.application.services.h3_geospatial_service import (
    H3GeospatialService, h3_service, DEFAULT_H3_RESOLUTION
)


def test_coordinate_validation():
    # Valid coordinates (Connaught Place, New Delhi)
    assert h3_service.validate_coordinates(28.6315, 77.2167) is True

    # Extreme boundary coordinates
    assert h3_service.validate_coordinates(90.0, 180.0) is True
    assert h3_service.validate_coordinates(-90.0, -180.0) is True
    assert h3_service.validate_coordinates(0.0, 0.0) is True

    # Invalid coordinates
    assert h3_service.validate_coordinates(91.0, 77.2167) is False
    assert h3_service.validate_coordinates(-91.0, 77.2167) is False
    assert h3_service.validate_coordinates(28.6315, 181.0) is False
    assert h3_service.validate_coordinates(28.6315, -181.0) is False

    # Non-numeric, None, NaN, Inf
    assert h3_service.validate_coordinates(None, 77.2) is False
    assert h3_service.validate_coordinates("28.6", 77.2) is False
    assert h3_service.validate_coordinates(float("nan"), 77.2) is False
    assert h3_service.validate_coordinates(28.6, float("inf")) is False


def test_latlng_to_h3_cell():
    lat, lng = 28.6139, 77.2090
    cell = h3_service.get_h3_cell(lat, lng)

    assert cell is not None
    assert isinstance(cell, str)
    assert len(cell) == 15  # Standard 15-char H3 hex string representation
    assert cell.startswith("88")  # Res 8 indices start with 88 in hex

    # Higher and lower resolution verification
    res7_cell = h3_service.get_h3_cell(lat, lng, resolution=7)
    res9_cell = h3_service.get_h3_cell(lat, lng, resolution=9)
    assert res7_cell is not None and res7_cell.startswith("87")
    assert res9_cell is not None and res9_cell.startswith("89")

    # Invalid coordinates return None
    assert h3_service.get_h3_cell(95.0, 77.2) is None


def test_cell_center_and_boundary():
    lat, lng = 28.6139, 77.2090
    cell = h3_service.get_h3_cell(lat, lng, resolution=8)

    center = h3_service.get_cell_center(cell)
    assert center is not None
    c_lat, c_lng = center
    # Center should be within ~500m of input coordinates
    dist = h3_service.haversine_distance_km(lat, lng, c_lat, c_lng)
    assert dist < 0.6

    boundary = h3_service.get_cell_boundary(cell)
    assert len(boundary) in (6, 7)  # Hexagon has 6 vertices (or 7 if closed loop)


def test_h3_grid_disk_and_rings():
    lat, lng = 28.6139, 77.2090
    origin_cell = h3_service.get_h3_cell(lat, lng, resolution=8)

    # Disk sizes: 1, 7, 19, 37
    disk_0 = h3_service.get_h3_disk(origin_cell, k=0)
    assert disk_0 == {origin_cell}
    assert len(disk_0) == 1

    disk_1 = h3_service.get_h3_disk(origin_cell, k=1)
    assert origin_cell in disk_1
    assert len(disk_1) == 7

    disk_2 = h3_service.get_h3_disk(origin_cell, k=2)
    assert len(disk_2) == 19

    disk_3 = h3_service.get_h3_disk(origin_cell, k=3)
    assert len(disk_3) == 37

    # Hollow Rings
    ring_1 = h3_service.get_h3_ring(origin_cell, k=1)
    assert len(ring_1) == 6
    assert origin_cell not in ring_1
    assert disk_1 == ring_1.union({origin_cell})

    ring_2 = h3_service.get_h3_ring(origin_cell, k=2)
    assert len(ring_2) == 12


def test_progressive_search_rings():
    lat, lng = 28.6139, 77.2090
    cell = h3_service.get_h3_cell(lat, lng, resolution=8)

    rings = h3_service.get_progressive_search_rings(cell, max_rings=3)
    assert len(rings) == 4  # [Ring 0, Ring 1, Ring 2, Ring 3]
    assert len(rings[0]) == 1
    assert len(rings[1]) == 6
    assert len(rings[2]) == 12
    assert len(rings[3]) == 18

    # Ensure rings are mutually disjoint
    all_cells = set()
    for r in rings:
        assert all_cells.isdisjoint(r)
        all_cells.update(r)
    assert len(all_cells) == 37


def test_haversine_distance():
    # New Delhi to Noida (~15.5 km)
    delhi_lat, delhi_lng = 28.6139, 77.2090
    noida_lat, noida_lng = 28.5355, 77.3910

    dist = h3_service.haversine_distance_km(delhi_lat, delhi_lng, noida_lat, noida_lng)
    assert 18.0 <= dist <= 22.0  # Approx 19.8 km

