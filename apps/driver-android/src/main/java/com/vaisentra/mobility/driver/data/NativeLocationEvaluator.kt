package com.vaisentra.mobility.driver.data

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    val accuracyMeters: Float,
    val timestampMs: Long,
    val heading: Double = 0.0,
    val speedMps: Double = 0.0,
    val isMock: Boolean = false
)

/**
 * Pure Kotlin location accuracy, freshness, and provider-priority evaluator
 * for Android native LocationManager (GPS / Network / Passive).
 */
object NativeLocationEvaluator {

    const val DEFAULT_MAX_AGE_MS = 120_000L
    const val DEFAULT_CACHE_MAX_AGE_MS = 600_000L
    const val DEFAULT_MAX_ACCURACY_METERS = 150f
    const val HARD_MAX_ACCURACY_METERS = 500f

    fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        if (!lat.isFinite() || !lng.isFinite()) return false
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) return false
        if (lat == 0.0 && lng == 0.0) return false
        return true
    }

    fun normalizeLocationSource(provider: String?): String {
        return when (provider?.lowercase()) {
            "gps" -> "GPS"
            "network" -> "NETWORK"
            "passive" -> "PASSIVE"
            else -> "GPS"
        }
    }

    fun shouldAcceptLocationUpdate(
        current: LocationSample?,
        candidate: LocationSample,
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
        maxAcceptableAccuracyM: Float = DEFAULT_MAX_ACCURACY_METERS
    ): Boolean {
        if (!isValidCoordinate(candidate.latitude, candidate.longitude)) return false
        if (candidate.timestampMs <= 0L) return false
        val ageMs = nowMs - candidate.timestampMs
        if (ageMs > maxAgeMs) return false
        if (candidate.accuracyMeters < 0f || candidate.accuracyMeters > HARD_MAX_ACCURACY_METERS) return false
        if (current == null) {
            return candidate.accuracyMeters <= maxAcceptableAccuracyM || candidate.accuracyMeters <= HARD_MAX_ACCURACY_METERS
        }

        val timeDeltaMs = candidate.timestampMs - current.timestampMs
        val isSignificantlyNewer = timeDeltaMs > 30_000L
        val isSignificantlyOlder = timeDeltaMs < -30_000L

        if (isSignificantlyNewer) {
            return candidate.accuracyMeters <= maxAcceptableAccuracyM
        }
        if (isSignificantlyOlder) {
            return false
        }

        // Within same 30-second window: compare accuracy & provider preference
        val accuracyDelta = candidate.accuracyMeters - current.accuracyMeters
        val isMoreAccurate = accuracyDelta < 0f
        val isSignificantlyLessAccurate = accuracyDelta > 100f
        val sameProvider = candidate.provider.equals(current.provider, ignoreCase = true)

        if (isMoreAccurate) return true
        if (timeDeltaMs >= 0L && candidate.provider.equals("gps", ignoreCase = true) && candidate.accuracyMeters <= 50f) {
            return true
        }
        if (timeDeltaMs >= 0L && sameProvider && candidate.accuracyMeters <= maxAcceptableAccuracyM) {
            return true
        }
        return timeDeltaMs >= 0L && !isSignificantlyLessAccurate && candidate.accuracyMeters <= maxAcceptableAccuracyM
    }

    fun selectBestLastKnown(
        samples: List<LocationSample>,
        nowMs: Long = System.currentTimeMillis(),
        maxCacheAgeMs: Long = DEFAULT_CACHE_MAX_AGE_MS
    ): LocationSample? {
        var best: LocationSample? = null
        for (sample in samples) {
            if (!isValidCoordinate(sample.latitude, sample.longitude)) continue
            if (sample.timestampMs <= 0L || (nowMs - sample.timestampMs) > maxCacheAgeMs) continue
            if (sample.accuracyMeters < 0f || sample.accuracyMeters > HARD_MAX_ACCURACY_METERS) continue
            if (best == null || shouldAcceptLocationUpdate(best, sample, nowMs, maxCacheAgeMs, HARD_MAX_ACCURACY_METERS)) {
                best = sample
            }
        }
        return best
    }
}
