package com.luxury.chauffeur.customer

import com.luxury.chauffeur.customer.core.network.ResilientDns
import com.luxury.chauffeur.customer.feature.auth.EnterpriseAuthViewModel
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CustomerFcmAndNetworkTest {

    @Test
    fun testResilientDnsResolvesRenderHost() {
        // ResilientDns should always resolve e-rickshaw-z32z.onrender.com even if system DNS fails
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
    fun testPhoneValidationAndNormalization() {
        val raw1 = "7562084184"
        val raw2 = "+91 75620-84184"
        val raw3 = "917562084184"
        val invalid = "12345"

        assertTrue(EnterpriseAuthViewModel.isValidPhone(raw1))
        assertTrue(EnterpriseAuthViewModel.isValidPhone(raw2))
        assertTrue(EnterpriseAuthViewModel.isValidPhone(raw3))
        assertFalse(EnterpriseAuthViewModel.isValidPhone(invalid))

        assertEquals("+917562084184", EnterpriseAuthViewModel.normalizePhone(raw1))
        assertEquals("+917562084184", EnterpriseAuthViewModel.normalizePhone(raw2))
        assertEquals("+917562084184", EnterpriseAuthViewModel.normalizePhone(raw3))
    }

    @Test
    fun testErrorMessageFormattingGracefulHandling() {
        val unknownHost = UnknownHostException("Unable to resolve host \"e-rickshaw-z32z.onrender.com\": No address associated with hostname")
        val connectEx = ConnectException("Failed to connect to /216.24.57.18:443")
        val timeoutEx = SocketTimeoutException("connect timed out")

        val msgUnknown = EnterpriseAuthViewModel.formatErrorMessage(unknownHost, "Default Error")
        assertTrue(msgUnknown.contains("check your internet", ignoreCase = true) || msgUnknown.contains("Wi-Fi", ignoreCase = true))

        val msgConnect = EnterpriseAuthViewModel.formatErrorMessage(connectEx, "Default Error")
        assertTrue(msgConnect.contains("network connection", ignoreCase = true))

        val msgTimeout = EnterpriseAuthViewModel.formatErrorMessage(timeoutEx, "Default Error")
        assertTrue(msgTimeout.contains("longer to respond", ignoreCase = true))
    }
}

