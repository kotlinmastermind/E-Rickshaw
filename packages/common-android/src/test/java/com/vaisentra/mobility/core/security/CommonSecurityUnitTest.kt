package com.vaisentra.mobility.core.security

import com.vaisentra.mobility.core.network.CoreNetworkClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonSecurityUnitTest {

    @Test
    fun testRootDetectorExecution() {
        // Root detection must execute safely without throwing exceptions on any runtime
        val isRooted = RootDetector.isDeviceRooted()
        // In standard CI / developer JVM environments, isRooted is typically false
        assertNotNull(isRooted)
    }

    @Test
    fun testCoreNetworkClientConfiguration() {
        val client = CoreNetworkClient.createOkHttpClient(
            tokenProvider = { "mock-test-jwt-token" },
            clientRole = "customer",
            isDebug = true
        )
        assertNotNull(client)
        assertEquals(30000, client.connectTimeoutMillis)
        assertEquals(30000, client.readTimeoutMillis)
        assertEquals(30000, client.writeTimeoutMillis)
        assertTrue(client.retryOnConnectionFailure)
    }

    @Test
    fun testCertificatePinnerCreation() {
        val pinner = CoreNetworkClient.createProductionCertificatePinner(
            CoreNetworkClient.PRODUCTION_HOSTNAME,
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
        )
        assertNotNull(pinner)
        assertEquals(2, pinner.pins.size)
    }

    @Test
    fun testKeystoreTokenEncryptorGracefulFallback() {
        // Under standard JVM JUnit test, Android KeyStore is absent
        // Encryptor must gracefully return input without crashing
        val nullResult = KeystoreTokenEncryptor.encrypt(null)
        assertEquals(null, nullResult)

        val blankResult = KeystoreTokenEncryptor.encrypt("")
        assertEquals("", blankResult)

        val sampleText = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val output = KeystoreTokenEncryptor.encrypt(sampleText)
        assertNotNull(output)
    }
}
