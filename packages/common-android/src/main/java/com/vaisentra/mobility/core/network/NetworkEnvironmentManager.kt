package com.vaisentra.mobility.core.network

/**
 * Enterprise Production-Only Network Environment Manager.
 *
 * Guarantees:
 * 1. Strict Production Locking: The entire platform is locked to the canonical backend endpoint.
 * 2. Complete Local/Staging Removal: No multi-environment switching, no local fallback, no staging rewriting.
 * 3. Release & Debug Parity: Both release and debug mobile builds communicate exclusively with Production.
 * 4. HTTPS & Domain Validation: Enforces HTTPS and authentic production domain verification.
 */
object NetworkEnvironmentManager {
    const val CANONICAL_PRODUCTION_URL: String = "https://e-rickshaw-z32z.onrender.com/"
    const val CANONICAL_PRODUCTION_HOSTNAME: String = "e-rickshaw-z32z.onrender.com"

    // Backwards-compatible aliases for verified production endpoints
    const val VERIFIED_PRODUCTION_URL: String = CANONICAL_PRODUCTION_URL
    const val VERIFIED_PRODUCTION_HOSTNAME: String = CANONICAL_PRODUCTION_HOSTNAME

    fun sanitizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    /**
     * Strict validation preventing invalid or non-production URLs.
     * Enforces HTTPS and authentic production domain pinning.
     */
    fun validateProductionUrl(url: String) {
        val sanitized = sanitizeUrl(url)
        if (!sanitized.startsWith("https://")) {
            throw IllegalStateException("CRITICAL PRODUCTION SECURITY ERROR: Production URL must use HTTPS. Provided: $url")
        }
        if (sanitized.contains("10.0.2.2") ||
            sanitized.contains("localhost") ||
            sanitized.contains("127.0.0.1") ||
            sanitized.contains("192.168.")
        ) {
            throw IllegalStateException("CRITICAL PRODUCTION SECURITY ERROR: Local development endpoints cannot be used in production. Provided: $url")
        }
        if (sanitized.contains("staging-api.vaisentra.com") || sanitized.contains("staging")) {
            throw IllegalStateException("CRITICAL PRODUCTION SECURITY ERROR: Staging endpoints cannot be used in production. Provided: $url")
        }
        if (!sanitized.contains(CANONICAL_PRODUCTION_HOSTNAME)) {
            throw IllegalStateException("CRITICAL PRODUCTION SECURITY ERROR: Unverified domain detected. Expected $CANONICAL_PRODUCTION_HOSTNAME, got: $url")
        }
    }

    /**
     * Resolves the authoritative Base URL.
     * In the production-only architecture, this strictly returns the canonical production endpoint.
     */
    fun resolveBaseUrl(
        savedUrl: String? = null,
        isRelease: Boolean = false,
        deviceIsEmulator: Boolean = false
    ): String {
        validateProductionUrl(CANONICAL_PRODUCTION_URL)
        return CANONICAL_PRODUCTION_URL
    }
}
