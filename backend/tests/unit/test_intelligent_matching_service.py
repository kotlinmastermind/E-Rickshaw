"""
Unit tests for IntelligentMatchingService multi-dimensional candidate scoring and ranking.
"""
import pytest
from backend.application.services.driver_eligibility_service import CandidateDriver
from backend.application.services.intelligent_matching_service import IntelligentMatchingService, ScoredCandidate


def create_dummy_candidate(
    driver_id: str,
    lat: float,
    lng: float,
    haversine_km: float,
    road_eta_min: float,
    road_dist_km: float,
    rating: float = 4.8,
    heading: float = 0.0,
    speed_mps: float = 5.0,
    ring_idx: int = 0
) -> CandidateDriver:
    c = CandidateDriver(
        driver_id=driver_id,
        user_id=f"user_{driver_id}",
        latitude=lat,
        longitude=lng,
        heading=heading,
        speed_mps=speed_mps,
        accuracy_m=3.0,
        h3_cell="882d431053fffff",
        ring_index=ring_idx,
        haversine_distance_km=haversine_km,
        rating_average=rating,
        total_trips=100,
        wallet_balance_cents=2500
    )
    setattr(c, "road_eta_minutes", road_eta_min)
    setattr(c, "road_distance_km", road_dist_km)
    return c


def test_candidate_scoring_breakdown():
    service = IntelligentMatchingService()
    pickup_lat, pickup_lng = 28.6139, 77.2090

    c = create_dummy_candidate(
        driver_id="d1",
        lat=28.6150,
        lng=77.2100,
        haversine_km=0.2,
        road_eta_min=2.0,
        road_dist_km=0.3,
        rating=4.9
    )

    scored = service.score_candidate(c, pickup_lat, pickup_lng)
    assert 0.0 <= scored.total_score <= 1.0
    assert "eta_score" in scored.score_breakdown
    assert "distance_score" in scored.score_breakdown
    assert "freshness_score" in scored.score_breakdown
    assert "rating_score" in scored.score_breakdown
    assert "heading_score" in scored.score_breakdown
    assert len(scored.explanation) > 0


def test_eta_dominance_in_ranking():
    service = IntelligentMatchingService()
    pickup_lat, pickup_lng = 28.6139, 77.2090

    # Candidate Fast: 2 mins away, rating 4.6
    c_fast = create_dummy_candidate(
        driver_id="fast_driver",
        lat=28.6150,
        lng=77.2100,
        haversine_km=0.5,
        road_eta_min=2.0,
        road_dist_km=0.6,
        rating=4.6
    )

    # Candidate Slow: 14 mins away, rating 5.0
    c_slow = create_dummy_candidate(
        driver_id="slow_driver",
        lat=28.6500,
        lng=77.2500,
        haversine_km=5.0,
        road_eta_min=14.0,
        road_dist_km=6.5,
        rating=5.0
    )

    ranked = service.rank_candidates([c_slow, c_fast], pickup_lat, pickup_lng)
    assert len(ranked) == 2
    # The fast driver must be ranked #1
    assert ranked[0].candidate.driver_id == "fast_driver"
    assert ranked[0].total_score > ranked[1].total_score


def test_heading_alignment_scoring():
    service = IntelligentMatchingService()
    # Driver is at (28.60, 77.20) and pickup is to the North at (28.62, 77.20)
    driver_lat, driver_lng = 28.60, 77.20
    pickup_lat, pickup_lng = 28.62, 77.20

    # Moving North (heading 0 degrees) -> directly toward pickup
    score_north = service.calculate_heading_score(
        driver_lat, driver_lng, heading=0.0, speed_mps=6.0, pickup_lat=pickup_lat, pickup_lng=pickup_lng
    )

    # Moving South (heading 180 degrees) -> directly away from pickup
    score_south = service.calculate_heading_score(
        driver_lat, driver_lng, heading=180.0, speed_mps=6.0, pickup_lat=pickup_lat, pickup_lng=pickup_lng
    )

    assert score_north > score_south
    assert score_north >= 0.95
    assert score_south <= 0.45

