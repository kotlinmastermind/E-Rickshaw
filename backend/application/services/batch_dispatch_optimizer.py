"""
Batch Dispatch Optimization Engine for Vasantra Mobility.
Solves the Maximum Utility / Minimum Global Waiting Time Bipartite Assignment Problem
between concurrent Ride Requests and Available Drivers using the Hungarian (Kuhn-Munkres) Algorithm.
"""
import logging
from typing import List, Optional, Tuple
from dataclasses import dataclass

from backend.infrastructure.cache.redis_client import redis_client

logger = logging.getLogger("backend.dispatch.batch")

DISPATCH_LOCK_TTL_SECONDS: int = 25  # Lock driver while dispatch offer is presented


@dataclass
class RideRequestItem:
    ride_id: str
    pickup_lat: float
    pickup_lng: float
    passenger_user_id: Optional[str] = None
    required_category_id: Optional[str] = None


@dataclass
class DriverCandidateItem:
    driver_id: str
    user_id: Optional[str]
    latitude: float
    longitude: float
    road_eta_minutes: float
    total_score: float
    ring_index: int


@dataclass
class OptimizedAssignment:
    ride_id: str
    driver_id: str
    driver_user_id: Optional[str]
    road_eta_minutes: float
    match_score: float
    lock_acquired: bool


class HungarianMatcher:
    """
    Exact Kuhn-Munkres / Hungarian Algorithm for Minimum Weight Bipartite Matching.
    Solves min-cost assignment in O(V^3) time for rectangular cost matrices.
    """

    @classmethod
    def solve_min_cost_assignment(cls, cost_matrix: List[List[float]]) -> List[Tuple[int, int]]:
        """
        Given an M x N cost matrix (M requests, N drivers), returns a list of (row, col)
        assignments that minimizes total cost.
        If M > N, some rows remain unassigned.
        If a cell has cost >= 1e8 (infinity), it is treated as an invalid edge.
        """
        if not cost_matrix or not cost_matrix[0]:
            return []

        n_rows = len(cost_matrix)
        n_cols = len(cost_matrix[0])

        # If only 1 request, find min cost directly in O(N)
        if n_rows == 1:
            min_val = float("inf")
            min_col = -1
            for c in range(n_cols):
                if cost_matrix[0][c] < min_val and cost_matrix[0][c] < 1e8:
                    min_val = cost_matrix[0][c]
                    min_col = c
            return [(0, min_col)] if min_col != -1 else []

        # Pad matrix to square N_dim x N_dim where N_dim = max(n_rows, n_cols)
        dim = max(n_rows, n_cols)
        INF = 1e9

        matrix = [[INF] * dim for _ in range(dim)]
        for r in range(n_rows):
            for c in range(n_cols):
                matrix[r][c] = cost_matrix[r][c]
        # Pad with 0 for dummy rows/cols to allow free assignment
        for r in range(n_rows, dim):
            for c in range(dim):
                matrix[r][c] = 0.0
        for r in range(dim):
            for c in range(n_cols, dim):
                matrix[r][c] = 0.0

        # Jonker-Volgenant / Hungarian dual variables
        u = [0.0] * (dim + 1)
        v = [0.0] * (dim + 1)
        p = [0] * (dim + 1)
        way = [0] * (dim + 1)

        for i in range(1, dim + 1):
            p[0] = i
            j0 = 0
            minv = [INF] * (dim + 1)
            used = [False] * (dim + 1)

            while True:
                used[j0] = True
                i0 = p[j0]
                delta = INF
                j1 = 0

                for j in range(1, dim + 1):
                    if not used[j]:
                        cur = matrix[i0 - 1][j - 1] - u[i0] - v[j]
                        if cur < minv[j]:
                            minv[j] = cur
                            way[j] = j0
                        if minv[j] < delta:
                            delta = minv[j]
                            j1 = j

                for j in range(dim + 1):
                    if used[j]:
                        u[p[j]] += delta
                        v[j] -= delta
                    else:
                        minv[j] -= delta

                j0 = j1
                if p[j0] == 0:
                    break

            while True:
                j1 = way[j0]
                p[j0] = p[j1]
                j0 = j1
                if j0 == 0:
                    break

        assignments = []
        for j in range(1, dim + 1):
            if p[j] != 0:
                row = p[j] - 1
                col = j - 1
                if row < n_rows and col < n_cols:
                    if cost_matrix[row][col] < 1e8:
                        assignments.append((row, col))

        # Sort assignments by row index
        assignments.sort(key=lambda x: x[0])
        return assignments


class BatchDispatchOptimizer:
    def __init__(self, lock_ttl_seconds: int = DISPATCH_LOCK_TTL_SECONDS):
        self.lock_ttl = lock_ttl_seconds

    @staticmethod
    def build_cost_matrix(
        rides: List[RideRequestItem],
        drivers: List[DriverCandidateItem],
        eta_matrix: List[List[float]]
    ) -> List[List[float]]:
        """
        Builds a combined cost matrix where Cost = (ETA_minutes) - (weight * match_score).
        Minimizing this cost minimizes customer wait time while favoring higher driver ratings.
        """
        INF = 1e8
        cost_matrix: List[List[float]] = []

        for r_idx in range(len(rides)):
            row: List[float] = []
            for d_idx in range(len(drivers)):
                eta_min = eta_matrix[r_idx][d_idx]
                if eta_min >= 30.0:  # Excessive distance cutoff
                    row.append(INF)
                else:
                    score = drivers[d_idx].total_score
                    # Base cost = ETA in minutes; minus incentive bonus for top match score
                    cost = eta_min - (score * 2.0)
                    row.append(round(cost, 4))
            cost_matrix.append(row)

        return cost_matrix

    async def acquire_atomic_driver_lock(self, driver_id: str, ride_id: str) -> bool:
        """
        Acquires an atomic distributed Redis lock on the driver for DISPATCH_LOCK_TTL_SECONDS.
        Ensures a driver cannot receive two simultaneous dispatch offers from different rides.
        """
        lock_key = f"dispatch:lock:{driver_id}"
        # Set if not exists (NX) with expiration (EX)
        acquired = await redis_client.set(lock_key, ride_id, ex=self.lock_ttl, nx=True)
        return bool(acquired)

    async def release_driver_lock(self, driver_id: str, ride_id: str) -> None:
        """Releases the dispatch lock if held by this ride."""
        lock_key = f"dispatch:lock:{driver_id}"
        val = await redis_client.get(lock_key)
        if val == ride_id:
            await redis_client.delete(lock_key)

    async def optimize_and_assign(
        self,
        rides: List[RideRequestItem],
        drivers: List[DriverCandidateItem],
        eta_matrix: List[List[float]]
    ) -> List[OptimizedAssignment]:
        """
        Solves the optimal bipartite assignment and secures atomic locks for matched pairs.
        """
        if not rides or not drivers:
            return []

        cost_matrix = self.build_cost_matrix(rides, drivers, eta_matrix)
        matches = HungarianMatcher.solve_min_cost_assignment(cost_matrix)

        assignments: List[OptimizedAssignment] = []
        for r_idx, d_idx in matches:
            ride = rides[r_idx]
            driver = drivers[d_idx]
            eta = eta_matrix[r_idx][d_idx]

            # Try to acquire atomic lock
            locked = await self.acquire_atomic_driver_lock(driver.driver_id, ride.ride_id)

            assignments.append(
                OptimizedAssignment(
                    ride_id=ride.ride_id,
                    driver_id=driver.driver_id,
                    driver_user_id=driver.user_id,
                    road_eta_minutes=eta,
                    match_score=driver.total_score,
                    lock_acquired=locked
                )
            )

        return assignments


batch_optimizer = BatchDispatchOptimizer()
