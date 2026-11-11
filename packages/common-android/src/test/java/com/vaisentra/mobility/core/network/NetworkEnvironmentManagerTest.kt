package com.vaisentra.mobility.core.network

import org.junit.Assert.*
import org.junit.Test

class NetworkEnvironmentManagerTest {

    @Test
    fun testCanonicalProductionBaseUrl() {
        assertEquals("https://e-rickshaw-z32z.onrender.com/", NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL)
        assertEquals("e-rickshaw-z32z.onrender.com", NetworkEnvironmentManager.CANONICAL_PRODUCTION_HOSTNAME)
        assertTrue(NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL.startsWith("https://"))
        assertTrue(NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL.endsWith("/"))
    }

    @Test
    fun testResolveBaseUrlAlwaysReturnsCanonicalProduction() {
        // Must always return canonical production URL regardless of input
        val resolved = NetworkEnvironmentManager.resolveBaseUrl()
        assertEquals(NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL, resolved)

        val resolvedFromCustom = NetworkEnvironmentManager.resolveBaseUrl(
            savedUrl = "http://10.0.2.2:8000/",
            isRelease = false,
            deviceIsEmulator = true
        )
        assertEquals(NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL, resolvedFromCustom)

        val resolvedRelease = NetworkEnvironmentManager.resolveBaseUrl(
            savedUrl = "https://staging-api.vaisentra.com/",
            isRelease = true,
            deviceIsEmulator = false
        )
        assertEquals(NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL, resolvedRelease)
    }

    @Test
    fun testProductionValidationRejectsInsecureOrLocalUrls() {
        // Must reject HTTP
        assertThrows(IllegalStateException::class.java) {
            NetworkEnvironmentManager.validateProductionUrl("http://e-rickshaw-z32z.onrender.com/")
        }

        // Must reject localhost / 10.0.2.2 / 127.0.0.1 / LAN
        assertThrows(IllegalStateException::class.java) {
            NetworkEnvironmentManager.validateProductionUrl("https://10.0.2.2:8000/")
        }
        assertThrows(IllegalStateException::class.java) {
            NetworkEnvironmentManager.validateProductionUrl("https://localhost:8000/")
        }
        assertThrows(IllegalStateException::class.java) {
            NetworkEnvironmentManager.validateProductionUrl("https://127.0.0.1:8000/")
        }
        assertThrows(IllegalStateException::class.java) {
            NetworkEnvironmentManager.validateProductionUrl("https://192.168.1.5:8000/")
        }

        // Must reject staging domains
        assertThrows(IllegalStateException::class.java) {
            NetworkEnvironmentManager.validateProductionUrl("https://staging-api.vaisentra.com/")
        }

        // Must reject unverified foreign domains
        assertThrows(IllegalStateException::class.java) {
            NetworkEnvironmentManager.validateProductionUrl("https://unverified-domain.com/")
        }

        // Canonical production URL must pass
        NetworkEnvironmentManager.validateProductionUrl(NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL)
    }

    @Test
    fun testUrlSanitization() {
        assertEquals("https://api.example.com/", NetworkEnvironmentManager.sanitizeUrl("https://api.example.com"))
        assertEquals("https://api.example.com/", NetworkEnvironmentManager.sanitizeUrl("  https://api.example.com/  "))
    }
}
