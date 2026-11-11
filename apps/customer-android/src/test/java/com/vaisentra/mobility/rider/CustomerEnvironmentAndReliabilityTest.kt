package com.vaisentra.mobility.rider

import com.vaisentra.mobility.core.network.CoreNetworkClient
import com.vaisentra.mobility.core.network.NetworkEnvironmentManager
import com.vaisentra.mobility.rider.core.network.ApiClient
import org.junit.Assert.*
import org.junit.Test

class CustomerEnvironmentAndReliabilityTest {

    @Test
    fun testCustomerApiClientStrictProductionLockdown() {
        assertEquals("Production", ApiClient.currentEnvironment)
        assertEquals(NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL, ApiClient.baseUrl)
        assertFalse("Customer app cannot use local backend", ApiClient.isLocalBackend())
    }

    @Test
    fun testCustomerApiProductionUrlMatchesAuthoritativeBackend() {
        assertEquals(
            "Customer app production base URL must match verified Render endpoint",
            "https://e-rickshaw-z32z.onrender.com/",
            ApiClient.RENDER_BASE_URL
        )
        assertEquals(
            "Customer canonical URL constant must match authoritative backend",
            "https://e-rickshaw-z32z.onrender.com/",
            ApiClient.CANONICAL_PRODUCTION_URL
        )
        assertTrue(ApiClient.RENDER_BASE_URL.startsWith("https://"))
        assertTrue(ApiClient.RENDER_BASE_URL.endsWith("/"))
    }

    @Test
    fun testCustomerUpdateBaseUrlRejectsNonProduction() {
        assertThrows(IllegalStateException::class.java) {
            ApiClient.updateBaseUrl("http://10.0.2.2:8000/")
        }
        assertThrows(IllegalStateException::class.java) {
            ApiClient.updateBaseUrl("https://staging-api.vaisentra.com/")
        }
        assertThrows(IllegalStateException::class.java) {
            ApiClient.updateBaseUrl("http://localhost:8000/")
        }
    }

    @Test
    fun testWebSocketUrlDerivation() {
        val wsUrl = ApiClient.getWebSocketUrl("api/v1/ws/rides/123")
        assertEquals("wss://e-rickshaw-z32z.onrender.com/api/v1/ws/rides/123", wsUrl)
    }
}
