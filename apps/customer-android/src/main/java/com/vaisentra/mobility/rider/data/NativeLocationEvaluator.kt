package com.vaisentra.mobility.rider.data

data class RiderLocationSample(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    val accuracyMeters: Float,
    val timestampMs: Long,
    val isMock: Boolean = false
)

/**
 * Pure Kotlin location accuracy, freshness, and provider-priority evaluator
 * for RiderGo native LocationManager (GPS / Network / Passive).
 */
object NativeLocationEvaluator {

    const val DEFAULT_MAX_AGE_MS = 180_000L
    const val DEFAULT_CACHE_MAX_AGE_MS = 600_000L
    const val DEFAULT_MAX_ACCURACY_METERS = 200f
    const val HARD_MAX_ACCURACY_METERS = 600f

    fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        if (!lat.isFinite() || !lng.isFinite()) return false
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) return false
        if (lat == 0.0 && lng == 0.0) return false
        return true
    }

    fun selectBestLocation(
        current: RiderLocationSample?,
        candidate: RiderLocationSample,
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS
    ): RiderLocationSample? {
        if (!isValidCoordinate(candidate.latitude, candidate.longitude)) return current
        if (candidate.timestampMs <= 0L || (nowMs - candidate.timestampMs) > maxAgeMs) return current
        if (candidate.accuracyMeters < 0f || candidate.accuracyMeters > HARD_MAX_ACCURACY_METERS) return current
        if (current == null) return candidate

        val timeDeltaMs = candidate.timestampMs - current.timestampMs
        if (timeDeltaMs > 30_000L && candidate.accuracyMeters <= DEFAULT_MAX_ACCURACY_METERS) return candidate
        if (timeDeltaMs < -30_000L) return current
        if (candidate.accuracyMeters < current.accuracyMeters) return candidate
        if (timeDeltaMs >= 0L && candidate.provider.equals("gps", ignoreCase = true) && candidate.accuracyMeters <= 50f) return candidate
        return if (timeDeltaMs >= 0L && candidate.accuracyMeters <= DEFAULT_MAX_ACCURACY_METERS) candidate else current
    }

    fun selectBestLastKnown(
        samples: List<RiderLocationSample>,
        nowMs: Long = System.currentTimeMillis(),
        maxCacheAgeMs: Long = DEFAULT_CACHE_MAX_AGE_MS
    ): RiderLocationSample? {
        var best: RiderLocationSample? = null
        for (sample in samples) {
            best = selectBestLocation(best, sample, nowMs, maxCacheAgeMs)
        }
        return best
    }
}
