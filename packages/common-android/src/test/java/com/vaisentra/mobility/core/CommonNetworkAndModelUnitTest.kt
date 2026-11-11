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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonNetworkAndModelUnitTest {

    private val gson = Gson()

    @Test
    fun testResolveDefaultBaseUrl() {
        val resolved = CoreNetworkClient.resolveDefaultBaseUrl()
        assertEquals(CoreNetworkClient.CANONICAL_PRODUCTION_URL, resolved)

        val resolvedFromSaved = CoreNetworkClient.resolveDefaultBaseUrl("https://unverified.com")
        assertEquals(CoreNetworkClient.CANONICAL_PRODUCTION_URL, resolvedFromSaved)
    }

    @Test
    fun testIsLocalBackendStrictlyFalse() {
        assertFalse(CoreNetworkClient.isLocalBackend("https://e-rickshaw-z32z.onrender.com/"))
    }

    @Test
    fun testWebSocketUrlConstruction() {
        val wsUrl = CoreNetworkClient.getWebSocketUrl(CoreNetworkClient.CANONICAL_PRODUCTION_URL, "rides/123/ws")
        assertEquals("wss://e-rickshaw-z32z.onrender.com/rides/123/ws", wsUrl)

        val wsUrlWithPath = CoreNetworkClient.getWebSocketUrl(CoreNetworkClient.CANONICAL_PRODUCTION_URL, "/api/v1/ws/telemetry")
        assertEquals("wss://e-rickshaw-z32z.onrender.com/api/v1/ws/telemetry", wsUrlWithPath)
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

    @Test
    fun testResilientDnsResolvesAnycastWithBoundedExecutor() {
        val renderAddresses = com.vaisentra.mobility.core.network.ResilientDns.lookup("e-rickshaw-z32z.onrender.com")
        assertNotNull(renderAddresses)
        assertTrue(renderAddresses.isNotEmpty())
        val ipStrings = renderAddresses.map { it.hostAddress }
        assertTrue(
            "Must resolve to Cloudflare Anycast Origin IPs (216.24.57.16 or 216.24.57.18)",
            ipStrings.contains("216.24.57.16") || ipStrings.contains("216.24.57.18")
        )
    }

    @Test
    fun testMultipartUploadInterceptorPreservesBoundary() {
        val client = CoreNetworkClient.createConfiguredOkHttpClient(
            tokenManager = null,
            baseUrlProvider = { "https://e-rickshaw-z32z.onrender.com/" },
            clientRole = "driver",
            isDebug = false
        )

        // 1. Construct a Multipart upload request (as Retrofit @Multipart produces)
        val imageBytes = "binary-image-data".toByteArray()
        val multipartBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("document_type", "DRIVING_LICENSE")
            .addFormDataPart(
                "file",
                "license.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            .build()

        val multipartRequest = okhttp3.Request.Builder()
            .url("https://e-rickshaw-z32z.onrender.com/api/v1/drivers/documents")
            .header("Authorization", "Bearer test_driver_token_xyz")
            .post(multipartBody)
            .build()

        // Execute interceptor chain
        val authInterceptor = client.interceptors.first()
        var interceptedRequest: okhttp3.Request? = null

        val fakeChain = object : okhttp3.Interceptor.Chain {
            override fun request(): okhttp3.Request = multipartRequest
            override fun proceed(request: okhttp3.Request): okhttp3.Response {
                interceptedRequest = request
                return okhttp3.Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{\"status\":\"ok\"}".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            override fun connection() = null
            override fun call(): okhttp3.Call = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 10000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 10000
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 10000
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        authInterceptor.intercept(fakeChain)

        assertNotNull(interceptedRequest)
        val req = interceptedRequest!!
        // Header assertions
        assertEquals("Android", req.header("X-Client-Platform"))
        assertEquals("driver", req.header("X-Client-Role"))
        assertEquals("application/json", req.header("Accept"))
        assertEquals("Bearer test_driver_token_xyz", req.header("Authorization"))

        // Critical: Multipart request body Content-Type must NOT be overwritten with application/json
        val reqContentType = req.header("Content-Type")
        assertTrue(
            "Content-Type header must either be null (to let OkHttp write multipart boundary) or start with multipart/form-data; boundary=",
            reqContentType == null || reqContentType.startsWith("multipart/form-data")
        )
        assertFalse(
            "Content-Type must NOT be forced to application/json for multipart uploads",
            reqContentType == "application/json"
        )
        assertTrue(req.body?.contentType().toString().startsWith("multipart/form-data; boundary="))
    }

    @Test
    fun testJsonRequestRetainsApplicationJson() {
        val client = CoreNetworkClient.createConfiguredOkHttpClient(
            tokenManager = null,
            baseUrlProvider = { "https://e-rickshaw-z32z.onrender.com/" },
            clientRole = "customer",
            isDebug = false
        )

        val jsonRequest = okhttp3.Request.Builder()
            .url("https://e-rickshaw-z32z.onrender.com/api/v1/auth/login")
            .post("{\"email\":\"a@b.com\"}".toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        val authInterceptor = client.interceptors.first()
        var interceptedRequest: okhttp3.Request? = null

        val fakeChain = object : okhttp3.Interceptor.Chain {
            override fun request(): okhttp3.Request = jsonRequest
            override fun proceed(request: okhttp3.Request): okhttp3.Response {
                interceptedRequest = request
                return okhttp3.Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            override fun connection() = null
            override fun call(): okhttp3.Call = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 10000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 10000
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 10000
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        authInterceptor.intercept(fakeChain)

        assertNotNull(interceptedRequest)
        val req = interceptedRequest!!
        assertEquals("Android", req.header("X-Client-Platform"))
        assertEquals("customer", req.header("X-Client-Role"))
        assertEquals("application/json", req.header("Content-Type"))
    }
}

