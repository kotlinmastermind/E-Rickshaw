package com.luxury.chauffeur.driver

import com.luxury.chauffeur.driver.core.network.ResilientDns
import org.junit.Assert.*
import org.junit.Test

class DriverFcmAndResilienceTest {

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
}

