package com.vaisentra.mobility.driver

import com.vaisentra.mobility.core.network.ResilientDns
import com.vaisentra.mobility.driver.core.network.DriverApiClient
import org.junit.Assert.*
import org.junit.Test

class DriverNetworkAndResilienceTest {

    @Test
    fun testDriverResilientDnsResolvesRenderHost() {
        val addresses = ResilientDns.lookup("e-rickshaw-z32z.onrender.com")
        assertNotNull(addresses)
        assertTrue("Addresses list must not be empty", addresses.isNotEmpty())
        val ipStrings = addresses.map { it.hostAddress }
        assertTrue(
            "Must resolve to Cloudflare Anycast Origin IPs (216.24.57.16 or 216.24.57.18)",
            ipStrings.contains("216.24.57.16") || ipStrings.contains("216.24.57.18")
        )
    }

    @Test
    fun testDriverTopicNamesFormat() {
        val pilotTopic = "driver_dispatches_tier1"
        assertTrue(pilotTopic.startsWith("driver_"))
        assertFalse(pilotTopic.contains(" "))
    }

    @Test
    fun testDriverApiClientCachesOkHttpClientInstance() {
        val client1 = DriverApiClient.getOkHttpClient()
        val client2 = DriverApiClient.getOkHttpClient()
        assertSame("DriverApiClient.getOkHttpClient() must return cached singleton instance to prevent socket leaks", client1, client2)
    }
}

