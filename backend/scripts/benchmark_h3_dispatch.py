"""
Performance and Load Benchmark for H3 Smart Dispatch Engine.
Measures real latency, throughput, memory, and algorithmic complexity across
100, 1,000, and 5,000 simulated drivers with concurrent requests and GPS updates.
"""
import os
os.environ["REDIS_URL"] = ""

import sys
import time
import math
import random
import asyncio
import tracemalloc
from typing import List, Dict, Any

# Ensure project root is in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))

from backend.application.services.h3_geospatial_service import h3_service
from backend.application.services.live_location_service import LiveLocationService
from backend.application.services.driver_eligibility_service import DriverEligibilityService, CandidateDriver
from backend.application.services.intelligent_matching_service import IntelligentMatchingService
from backend.application.services.batch_dispatch_optimizer import (
    HungarianMatcher, BatchDispatchOptimizer, RideRequestItem, DriverCandidateItem
)
from backend.infrastructure.cache.redis_client import redis_client


def generate_simulated_drivers(count: int) -> List[Dict[str, Any]]:
    """Generates synthetic driver telemetry clustered in urban Delhi NCR."""
    center_lat, center_lng = 28.6139, 77.2090
    drivers = []
    for i in range(count):
        # Gaussian distribution around urban center (~0.05 deg ~ 5.5 km)
        d_lat = center_lat + random.gauss(0, 0.035)
        d_lng = center_lng + random.gauss(0, 0.035)
        drivers.append({
            "driver_id": f"driver_bench_{i:05d}",
            "latitude": d_lat,
            "longitude": d_lng,
            "heading": random.uniform(0.0, 360.0),
            "speed_mps": random.uniform(0.0, 12.0),
            "accuracy_m": random.uniform(2.0, 8.0)
        })
    return drivers


async def benchmark_tier(driver_count: int) -> Dict[str, Any]:
    print(f"\n==================================================================")
    print(f"  BENCHMARKING TIER: {driver_count:,} SIMULATED ACTIVE DRIVERS")
    print(f"==================================================================")

    tracemalloc.start()
    start_wall_time = time.perf_counter()

    live_loc = LiveLocationService(db=None)
    eligibility_svc = DriverEligibilityService(db=None, live_loc_service=live_loc)
    matcher = IntelligentMatchingService()
    optimizer = BatchDispatchOptimizer()

    drivers = generate_simulated_drivers(driver_count)

    # 1. GPS Ingestion & H3 Cell Indexing Benchmark
    t0 = time.perf_counter()
    for d in drivers:
        await live_loc.ingest_driver_location(
            driver_id=d["driver_id"],
            latitude=d["latitude"],
            longitude=d["longitude"],
            heading=d["heading"],
            speed_mps=d["speed_mps"],
            accuracy_m=d["accuracy_m"],
            is_online=True,
            is_busy=False
        )
    ingest_duration_s = time.perf_counter() - t0
    ingest_throughput = driver_count / max(0.0001, ingest_duration_s)
    ingest_avg_ms = (ingest_duration_s / driver_count) * 1000.0

    print(f" [1] GPS Ingestion & H3 Indexing:")
    print(f"     Total Ingest Time:  {ingest_duration_s * 1000.0:.2f} ms")
    print(f"     Throughput:         {ingest_throughput:,.1f} pings/sec")
    print(f"     Avg Ping Latency:   {ingest_avg_ms:.4f} ms")

    # 2. Concentric H3 Ring Candidate Discovery Benchmark
    pickup_lat, pickup_lng = 28.6139, 77.2090
    pickup_cell = h3_service.get_h3_cell(pickup_lat, pickup_lng)

    retrieval_latencies = []
    candidate_counts = []
    for _ in range(50):
        t_start = time.perf_counter()
        candidates = await eligibility_svc.discover_eligible_candidates(
            pickup_lat=pickup_lat + random.uniform(-0.01, 0.01),
            pickup_lng=pickup_lng + random.uniform(-0.01, 0.01),
            max_rings=3,
            max_candidates=20
        )
        t_end = time.perf_counter()
        retrieval_latencies.append((t_end - t_start) * 1000.0)
        candidate_counts.append(len(candidates))

    avg_retrieval_ms = sum(retrieval_latencies) / len(retrieval_latencies)
    p95_retrieval_ms = sorted(retrieval_latencies)[int(len(retrieval_latencies) * 0.95)]
    avg_candidates_found = sum(candidate_counts) / len(candidate_counts)

    print(f"\n [2] Concentric H3 Rings Candidate Discovery (Ring 0..3):")
    print(f"     Avg Retrieval Latency:  {avg_retrieval_ms:.3f} ms")
    print(f"     P95 Retrieval Latency:  {p95_retrieval_ms:.3f} ms")
    print(f"     Avg Candidates Found:   {avg_candidates_found:.1f} drivers in range")

    # 3. Multi-Factor Intelligent Scoring Benchmark
    sample_candidates = await eligibility_svc.discover_eligible_candidates(
        pickup_lat=pickup_lat,
        pickup_lng=pickup_lng,
        max_rings=3,
        max_candidates=25
    )

    t_score_start = time.perf_counter()
    ranked = matcher.rank_candidates(sample_candidates, pickup_lat, pickup_lng)
    scoring_ms = (time.perf_counter() - t_score_start) * 1000.0

    print(f"\n [3] Multi-Factor Candidate Scoring:")
    print(f"     Scored {len(sample_candidates)} Candidates in: {scoring_ms:.3f} ms")
    print(f"     Avg Time Per Candidate:  {(scoring_ms / max(1, len(sample_candidates))):.4f} ms")

    # 4. Hungarian Bipartite Matching Batch Optimization Benchmark
    # Simulate a concurrent burst of 5 rides and 20 candidates
    m_rides = [
        RideRequestItem(ride_id=f"ride_{k}", pickup_lat=pickup_lat + k*0.005, pickup_lng=pickup_lng + k*0.005)
        for k in range(5)
    ]
    n_drivers = [
        DriverCandidateItem(
            driver_id=f"d_cand_{k}", user_id=f"u_{k}", latitude=pickup_lat, longitude=pickup_lng,
            road_eta_minutes=random.uniform(1.5, 12.0), total_score=random.uniform(0.7, 0.95), ring_index=0
        )
        for k in range(20)
    ]
    eta_matrix = [[random.uniform(1.5, 15.0) for _ in range(20)] for _ in range(5)]

    t_hungarian = time.perf_counter()
    matches = await optimizer.optimize_and_assign(m_rides, n_drivers, eta_matrix)
    hungarian_ms = (time.perf_counter() - t_hungarian) * 1000.0

    print(f"\n [4] Hungarian Bipartite Batch Optimization (5 Rides x 20 Drivers):")
    print(f"     Matching & Lock Time:   {hungarian_ms:.3f} ms")
    print(f"     Optimal Matches Solved: {len(matches)}")

    # Clean locks
    for m in matches:
        await optimizer.release_driver_lock(m.driver_id, m.ride_id)

    # 5. Memory Footprint
    current_mem, peak_mem = tracemalloc.get_traced_memory()
    tracemalloc.stop()

    total_tier_duration = time.perf_counter() - start_wall_time
    print(f"\n [5] Resource Utilization:")
    print(f"     Peak Memory:        {peak_mem / (1024 * 1024):.2f} MB")
    print(f"     Total Tier Runtime: {total_tier_duration:.2f} seconds")

    return {
        "driver_count": driver_count,
        "ingest_throughput_pings_sec": round(ingest_throughput, 1),
        "ingest_avg_ms": round(ingest_avg_ms, 4),
        "h3_retrieval_avg_ms": round(avg_retrieval_ms, 3),
        "h3_retrieval_p95_ms": round(p95_retrieval_ms, 3),
        "scoring_ms": round(scoring_ms, 3),
        "hungarian_batch_ms": round(hungarian_ms, 3),
        "peak_memory_mb": round(peak_mem / (1024 * 1024), 2)
    }


async def main():
    print("==================================================================")
    print("  VASANTRA MOBILITY — H3 SMART DISPATCH LOAD & PERFORMANCE BENCHMARK")
    print("==================================================================")

    results = []
    for count in [100, 1000, 5000]:
        res = await benchmark_tier(count)
        results.append(res)

    print("\n\n" + "=" * 90)
    print(f"{'DRIVER COUNT':<15} | {'INGEST THRUPUT':<16} | {'H3 RETRIEVAL (P95)':<20} | {'BATCH OPT (5x20)':<18} | {'PEAK MEMORY':<12}")
    print("=" * 90)
    for r in results:
        print(
            f"{r['driver_count']:<15,d} | "
            f"{r['ingest_throughput_pings_sec']:<11,.1f} p/s | "
            f"{r['h3_retrieval_p95_ms']:<17.3f} ms | "
            f"{r['hungarian_batch_ms']:<15.3f} ms | "
            f"{r['peak_memory_mb']:<9.2f} MB"
        )
    print("=" * 90)


if __name__ == "__main__":
    asyncio.run(main())

