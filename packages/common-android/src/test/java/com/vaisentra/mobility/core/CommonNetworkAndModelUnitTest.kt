package com.vaisentra.mobility.core

import com.google.gson.Gson
import com.vaisentra.mobility.core.model.AuthResponse
import com.vaisentra.mobility.core.model.ChatMessageDto
import com.vaisentra.mobility.core.model.FareEstimateResponse
import com.vaisentra.mobility.core.model.GenericApiResponse
import com.vaisentra.mobility.core.model.IdentityLookupRequest
import com.vaisentra.mobility.core.model.TokenResponse
import com.vaisentra.mobility.core.model.UserDto
import com.vaisentra.mobility.core.network.CoreNetworkClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonNetworkAndModelUnitTest {

    private val gson = Gson()

    @Test
    fun testResolveDefaultBaseUrl() {
        val saved = "https://custom.backend.api"
        val resolved = CoreNetworkClient.resolveDefaultBaseUrl(saved)
        assertEquals("https://custom.backend.api/", resolved)

        val emptyResolved = CoreNetworkClient.resolveDefaultBaseUrl("")
        assertTrue(emptyResolved == CoreNetworkClient.DEFAULT_PRODUCTION_URL || emptyResolved == CoreNetworkClient.LOCAL_EMULATOR_BASE_URL)
    }

    @Test
    fun testIsLocalBackend() {
        assertTrue(CoreNetworkClient.isLocalBackend("http://10.0.2.2:8000/"))
        assertTrue(CoreNetworkClient.isLocalBackend("http://localhost:8000/"))
        assertTrue(CoreNetworkClient.isLocalBackend("http://127.0.0.1:8000/"))
        assertFalse(CoreNetworkClient.isLocalBackend("https://e-rickshaw-z32z.onrender.com/"))
    }

    @Test
    fun testWebSocketUrlConstruction() {
        val wsUrl = CoreNetworkClient.getWebSocketUrl("https://api.example.com", "rides/123/ws")
        assertEquals("wss://api.example.com/rides/123/ws", wsUrl)

        val httpWsUrl = CoreNetworkClient.getWebSocketUrl("http://10.0.2.2:8000/", "/tracking/ws")
        assertEquals("ws://10.0.2.2:8000/tracking/ws", httpWsUrl)
    }

    @Test
    fun testConfiguredOkHttpClientCreation() {
        val client = CoreNetworkClient.createConfiguredOkHttpClient(
            tokenManager = null,
            baseUrlProvider = { "https://api.example.com/" },
            clientRole = "customer",
            isDebug = false,
            connectTimeoutSeconds = 45,
            readTimeoutSeconds = 60
        )
        assertNotNull(client)
        assertEquals(45000, client.connectTimeoutMillis)
        assertEquals(60000, client.readTimeoutMillis)
        assertTrue(client.retryOnConnectionFailure)
    }

    @Test
    fun testSharedModelsSerialization() {
        val user = UserDto(
            id = "usr_001",
            phoneNumber = "+919876543210",
            fullName = "Ananya Roy",
            email = "ananya@example.com",
            role = "customer",
            tier = "VIP"
        )
        val userJson = gson.toJson(user)
        assertTrue(userJson.contains("\"id\":\"usr_001\""))
        assertTrue(userJson.contains("\"phone_number\":\"+919876543210\""))

        val tokenResp = TokenResponse(
            accessToken = "jwt_test_token_123",
            tokenType = "bearer",
            refreshToken = "refresh_token_456",
            user = user
        )
        val tokenJson = gson.toJson(tokenResp)
        val deserializedToken = gson.fromJson(tokenJson, TokenResponse::class.java)
        assertEquals("jwt_test_token_123", deserializedToken.accessToken)
        assertEquals("refresh_token_456", deserializedToken.refreshToken)
        assertEquals("usr_001", deserializedToken.user.id)

        val chat = ChatMessageDto(
            id = "msg_001",
            senderRole = "driver",
            messageText = "I have arrived at the gate."
        )
        val chatJson = gson.toJson(chat)
        val deserializedChat = gson.fromJson(chatJson, ChatMessageDto::class.java)
        assertEquals("I have arrived at the gate.", deserializedChat.messageText)
    }
}

