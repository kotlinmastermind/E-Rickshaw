import pytest

def calculate_fare(base_fare: int, per_km: int, per_min: int, min_fare: int, distance_km: float, duration_min: float, surge: float = 1.0) -> int:
    distance_charge = round(distance_km * per_km)
    time_charge = round(duration_min * per_min)
    subtotal = base_fare + distance_charge + time_charge
    effective = max(subtotal, min_fare)
    return round(effective * surge)

def test_fare_calculation_standard_sedan():
    # Base: Rs 500 (50,000 cents), Rs 35/km (3,500 cents), Rs 5/min (500 cents), min: Rs 1000 (100,000 cents)
    # Trip: 10 km, 20 mins -> 50,000 + (10 * 3500) + (20 * 500) = 50,000 + 35,000 + 10,000 = 95,000
    # Minimum fare is 100,000, so effective fare should be 100,000
    fare = calculate_fare(
        base_fare=50000,
        per_km=3500,
        per_min=500,
        min_fare=100000,
        distance_km=10.0,
        duration_min=20.0,
        surge=1.0
    )
    assert fare == 100000

def test_fare_calculation_long_distance_exceeds_minimum():
    # Trip: 25 km, 45 mins
    # 50,000 + (25 * 3500 = 87,500) + (45 * 500 = 22,500) = 160,000 cents (Rs 1,600)
    fare = calculate_fare(
        base_fare=50000,
        per_km=3500,
        per_min=500,
        min_fare=100000,
        distance_km=25.0,
        duration_min=45.0,
        surge=1.0
    )
    assert fare == 160000

def test_fare_calculation_with_surge_multiplier():
    # 1.5x surge on 160,000 -> 240,000 cents
    fare = calculate_fare(
        base_fare=50000,
        per_km=3500,
        per_min=500,
        min_fare=100000,
        distance_km=25.0,
        duration_min=45.0,
        surge=1.5
    )
    assert fare == 240000
    assert isinstance(fare, int)
