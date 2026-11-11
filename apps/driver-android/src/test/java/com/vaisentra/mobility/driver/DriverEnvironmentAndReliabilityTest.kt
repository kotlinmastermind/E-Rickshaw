package com.vaisentra.mobility.driver

import com.vaisentra.mobility.core.network.CoreNetworkClient
import com.vaisentra.mobility.core.network.NetworkEnvironmentManager
import com.vaisentra.mobility.driver.core.network.DriverApiClient
import org.junit.Assert.*
import org.junit.Test

class DriverEnvironmentAndReliabilityTest {

    @Test
    fun testDriverApiClientStrictProductionLockdown() {
        assertEquals("Production", DriverApiClient.currentEnvironment)
        assertEquals(NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL, DriverApiClient.baseUrl)
        assertFalse("Driver app cannot use local backend", DriverApiClient.isLocalBackend())
    }

    @Test
    fun testDriverAndCustomerUrlAlignment() {
        assertEquals(
            "Driver app production base URL must match verified Render endpoint",
            NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL,
            DriverApiClient.RENDER_BASE_URL
        )
        assertEquals(
            "Driver canonical URL constant must match authoritative backend",
            "https://e-rickshaw-z32z.onrender.com/",
            DriverApiClient.CANONICAL_PRODUCTION_URL
        )
        assertTrue(DriverApiClient.RENDER_BASE_URL.startsWith("https://"))
        assertTrue(DriverApiClient.RENDER_BASE_URL.endsWith("/"))
    }

    @Test
    fun testDriverUpdateBaseUrlRejectsNonProduction() {
        assertThrows(IllegalStateException::class.java) {
            DriverApiClient.updateBaseUrl("http://10.0.2.2:8000/")
        }
        assertThrows(IllegalStateException::class.java) {
            DriverApiClient.updateBaseUrl("https://staging-api.vaisentra.com/")
        }
        assertThrows(IllegalStateException::class.java) {
            DriverApiClient.updateBaseUrl("http://localhost:8000/")
        }
    }

    @Test
    fun testDriverWebSocketUrlDerivation() {
        val wsUrl = DriverApiClient.getWebSocketUrl("api/v1/ws/driver/telemetry")
        assertEquals("wss://e-rickshaw-z32z.onrender.com/api/v1/ws/driver/telemetry", wsUrl)
    }
}
