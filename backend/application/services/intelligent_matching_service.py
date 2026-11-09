"""
Intelligent Candidate Scoring & Driver Matching Engine for Vasantra Mobility.
Applies multi-dimensional scoring incorporating road ETA, deadheading distance,
GPS telemetry freshness, driver rating, and heading alignment to rank optimal candidates.
"""
import math
import time
import logging
from typing import List, Dict, Optional
from dataclasses import dataclass, field

from backend.application.services.driver_eligibility_service import CandidateDriver

logger = logging.getLogger("backend.dispatch.matching")

# Scoring weight configuration (must sum to 1.0)
WEIGHT_ETA: float = 0.45
WEIGHT_DISTANCE: float = 0.20
WEIGHT_FRESHNESS: float = 0.15
WEIGHT_RATING: float = 0.10
WEIGHT_HEADING: float = 0.10

MAX_REASONABLE_ETA_MINUTES: float = 20.0
MAX_REASONABLE_DISTANCE_KM: float = 10.0
MAX_TELEMETRY_AGE_SECONDS: float = 60.0


@dataclass
class ScoredCandidate:
    candidate: CandidateDriver
    total_score: float
    score_breakdown: Dict[str, float] = field(default_factory=dict)
    explanation: str = ""


class IntelligentMatchingService:
    def __init__(
        self,
        weight_eta: float = WEIGHT_ETA,
        weight_distance: float = WEIGHT_DISTANCE,
        weight_freshness: float = WEIGHT_FRESHNESS,
        weight_rating: float = WEIGHT_RATING,
        weight_heading: float = WEIGHT_HEADING
    ):
        self.w_eta = weight_eta
        self.w_dist = weight_distance
        self.w_fresh = weight_freshness
        self.w_rating = weight_rating
        self.w_heading = weight_heading

    @staticmethod
    def _compute_bearing(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
        """Calculates initial bearing (degrees from North: 0..360) from point 1 to point 2."""
        d_lng = math.radians(lng2 - lng1)
        lat1_r, lat2_r = math.radians(lat1), math.radians(lat2)
        y = math.sin(d_lng) * math.cos(lat2_r)
        x = math.cos(lat1_r) * math.sin(lat2_r) - math.sin(lat1_r) * math.cos(lat2_r) * math.cos(d_lng)
        bearing = math.degrees(math.atan2(y, x))
        return (bearing + 360.0) % 360.0

    def calculate_heading_score(
        self,
        driver_lat: float,
        driver_lng: float,
        heading: Optional[float],
        speed_mps: Optional[float],
        pickup_lat: float,
        pickup_lng: float
    ) -> float:
        """
        Rewards drivers currently traveling towards the pickup location.
        Neutral (0.75) if stationary or heading unknown; up to 1.0 if directly heading to pickup;
        down to 0.4 if moving in the exact opposite direction (requiring a U-turn).
        """
        if heading is None or speed_mps is None or speed_mps < 1.0:
            # Stationary or unknown heading - neutral score
            return 0.75

        target_bearing = self._compute_bearing(driver_lat, driver_lng, pickup_lat, pickup_lng)
        diff = abs(heading - target_bearing) % 360.0
        if diff > 180.0:
            diff = 360.0 - diff

        # diff ranges from 0 (heading directly toward) to 180 (heading away)
        # Scale to [0.4, 1.0]
        alignment = 1.0 - (diff / 180.0) * 0.6
        return round(alignment, 3)

    def score_candidate(
        self,
        candidate: CandidateDriver,
        pickup_lat: float,
        pickup_lng: float,
        current_time_epoch: Optional[float] = None
    ) -> ScoredCandidate:
        """
        Calculates a multi-factorial score in range [0.0, 1.0] for a single candidate.
        """
        epoch_now = current_time_epoch or time.time()

        # 1. Road ETA score (or fallback to haversine estimate if road_eta not computed)
        road_eta_min = getattr(candidate, "road_eta_minutes", None)
        if road_eta_min is None:
            # Fallback estimation: 20 km/h average urban speed
            road_eta_min = (candidate.haversine_distance_km / 20.0) * 60.0

        eta_score = max(0.0, min(1.0, 1.0 - (road_eta_min / MAX_REASONABLE_ETA_MINUTES)))

        # 2. Road Distance score
        road_dist_km = getattr(candidate, "road_distance_km", None)
        if road_dist_km is None:
            road_dist_km = candidate.haversine_distance_km * 1.25

        dist_score = max(0.0, min(1.0, 1.0 - (road_dist_km / MAX_REASONABLE_DISTANCE_KM)))

        # 3. Telemetry Freshness score
        last_seen = getattr(candidate, "epoch_timestamp", None)
        if last_seen is None:
            age_s = 5.0  # Default fresh if ingested
        else:
            age_s = max(0.0, epoch_now - float(last_seen))
        freshness_score = max(0.0, min(1.0, 1.0 - (age_s / MAX_TELEMETRY_AGE_SECONDS)))

        # 4. Rating score (scale 3.0 to 5.0 -> 0.0 to 1.0)
        rating = getattr(candidate, "rating_average", 5.0) or 5.0
        rating_score = max(0.0, min(1.0, (rating - 3.0) / 2.0))

        # 5. Heading alignment score
        heading_score = self.calculate_heading_score(
            driver_lat=candidate.latitude,
            driver_lng=candidate.longitude,
            heading=candidate.heading,
            speed_mps=candidate.speed_mps,
            pickup_lat=pickup_lat,
            pickup_lng=pickup_lng
        )

        total = (
            self.w_eta * eta_score +
            self.w_dist * dist_score +
            self.w_fresh * freshness_score +
            self.w_rating * rating_score +
            self.w_heading * heading_score
        )
        total_score = round(max(0.0, min(1.0, total)), 4)

        breakdown = {
            "eta_score": round(eta_score, 4),
            "distance_score": round(dist_score, 4),
            "freshness_score": round(freshness_score, 4),
            "rating_score": round(rating_score, 4),
            "heading_score": round(heading_score, 4)
        }

        explanation = (
            f"Score: {total_score:.3f} | ETA: {road_eta_min:.1f}m | "
            f"Dist: {road_dist_km:.2f}km | Rating: {rating:.1f}★ | Ring: {candidate.ring_index}"
        )

        return ScoredCandidate(
            candidate=candidate,
            total_score=total_score,
            score_breakdown=breakdown,
            explanation=explanation
        )

    def rank_candidates(
        self,
        candidates: List[CandidateDriver],
        pickup_lat: float,
        pickup_lng: float
    ) -> List[ScoredCandidate]:
        """
        Scores and ranks all candidates in descending order of match quality.
        """
        if not candidates:
            return []

        scored = [
            self.score_candidate(c, pickup_lat=pickup_lat, pickup_lng=pickup_lng)
            for c in candidates
        ]
        # Sort descending by score
        scored.sort(key=lambda s: s.total_score, reverse=True)
        return scored


matching_service = IntelligentMatchingService()
