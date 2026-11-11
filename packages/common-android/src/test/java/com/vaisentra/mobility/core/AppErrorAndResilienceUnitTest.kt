package com.vaisentra.mobility.core

import com.google.gson.JsonSyntaxException
import com.vaisentra.mobility.core.error.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppErrorAndResilienceUnitTest {

    @Test
    fun testErrorMapperNetworkClassifications() {
        // Socket timeout
        val timeoutErr = ErrorMapper.map(SocketTimeoutException("Read timed out"))
        assertTrue(timeoutErr is AppError.TimeoutError)
        assertEquals("ERR_NET_TIMEOUT", timeoutErr.code)
        assertTrue(timeoutErr.isRecoverable)
        assertTrue(timeoutErr.isRetryEligible)

        // DNS resolution failure
        val dnsErr = ErrorMapper.map(UnknownHostException("backend.api"))
        assertTrue(dnsErr is AppError.ConnectionError)
        assertEquals("ERR_NET_CONNECTION", dnsErr.code)
        assertTrue(dnsErr.isRetryEligible)

        // TCP Connect exception
        val connErr = ErrorMapper.map(ConnectException("Connection refused"))
        assertTrue(connErr is AppError.ConnectionError)

        // Generic IO
        val ioErr = ErrorMapper.map(IOException("Broken pipe"))
        assertTrue(ioErr is AppError.NetworkError)
        assertEquals("ERR_NET_GENERAL", ioErr.code)
    }

    @Test
    fun testErrorMapperHttpClassifications() {
        val errorBody = "{}".toResponseBody("application/json".toMediaType())

        // 401 Unauthorized
        val http401 = HttpException(Response.error<String>(401, errorBody))
        val authErr = ErrorMapper.map(http401)
        assertTrue(authErr is AppError.AuthenticationError)
        assertFalse(authErr.isRetryEligible)
        assertEquals("ERR_AUTH_UNAUTHORIZED", authErr.code)

        // 403 Forbidden
        val http403 = HttpException(Response.error<String>(403, errorBody))
        val authzErr = ErrorMapper.map(http403)
        assertTrue(authzErr is AppError.AuthorizationError)
        assertFalse(authzErr.isRetryEligible)

        // 422 Unprocessable Entity
        val http422 = HttpException(Response.error<String>(422, errorBody))
        val valErr = ErrorMapper.map(http422)
        assertTrue(valErr is AppError.ValidationError)
        assertFalse(valErr.isRetryEligible)

        // 429 Too Many Requests with Retry-After header
        val headers = Headers.Builder().add("Retry-After", "15").build()
        val http429 = HttpException(Response.error<String>(errorBody, okhttp3.Response.Builder()
            .code(429)
            .message("Too Many Requests")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("https://example.com").build())
            .headers(headers)
            .build()
        ))
        val rateLimitErr = ErrorMapper.map(http429)
        assertTrue(rateLimitErr is AppError.HttpError)
        assertEquals(429, (rateLimitErr as AppError.HttpError).statusCode)
        assertEquals(15L, rateLimitErr.retryAfterSeconds)
        assertTrue(rateLimitErr.isRetryEligible)

        // 503 Service Unavailable
        val http503 = HttpException(Response.error<String>(503, errorBody))
        val serverErr = ErrorMapper.map(http503)
        assertTrue(serverErr is AppError.HttpError)
        assertEquals(503, (serverErr as AppError.HttpError).statusCode)
        assertTrue(serverErr.isRecoverable)
        assertTrue(serverErr.isRetryEligible)
    }

    @Test
    fun testErrorMapperDataAndRuntimeClassifications() {
        // Malformed JSON
        val jsonErr = ErrorMapper.map(JsonSyntaxException("Unterminated array at line 1"))
        assertTrue(jsonErr is AppError.SerializationError)
        assertFalse(jsonErr.isRetryEligible)

        // SQLite failure
        val dbErr = ErrorMapper.map(android.database.sqlite.SQLiteException("database disk image is malformed"))
        assertTrue(dbErr is AppError.DatabaseError)
        assertEquals("ERR_DB_OPERATION", dbErr.code)

        // Security / Keystore rejection
        val secErr = ErrorMapper.map(SecurityException("KeyStore unauthorized operation"))
        assertTrue(secErr is AppError.SecurityError)
        assertFalse(secErr.isRetryEligible)

        // Coroutine cancellation
        val cancelEx = CancellationException("Job was cancelled")
        val coroutineErr = ErrorMapper.map(cancelEx)
        assertTrue(coroutineErr is AppError.CoroutineCancellation)
    }

    @Test
    fun testAppResultFunctionalOperations() {
        val success = AppResult.Success("Vasantra")
        var successHandled = false
        success.onSuccess {
            assertEquals("Vasantra", it)
            successHandled = true
        }.onFailure {
            fail("Failure block should not run on Success")
        }
        assertTrue(successHandled)
        assertEquals("Vasantra", success.getOrNull())
        assertEquals("Vasantra", success.getOrDefault("Default"))

        val mapped = success.map { it.length }
        assertEquals(8, mapped.getOrNull())

        val failure: AppResult<String> = AppResult.Failure(AppError.NetworkError())
        var failureHandled = false
        failure.onFailure {
            assertEquals("ERR_NET_GENERAL", it.code)
            failureHandled = true
        }.onSuccess {
            fail("Success block should not run on Failure")
        }
        assertTrue(failureHandled)
        assertNull(failure.getOrNull())
        assertEquals("Fallback", failure.getOrDefault("Fallback"))
    }

    @Test
    fun testResilientCallPreservesCancellationException() = runBlocking {
        try {
            ResilientCall.safeCall<String> {
                throw CancellationException("Structured cancellation")
            }
            fail("CancellationException should have been rethrown!")
        } catch (ce: CancellationException) {
            assertEquals("Structured cancellation", ce.message)
        }
    }

    @Test
    fun testResilientCallTrapsNetworkException() = runBlocking {
        val result = ResilientCall.safeCall<String> {
            throw UnknownHostException("api.vasantra.com")
        }
        assertTrue(result.isFailure)
        val err = result.errorOrNull()
        assertNotNull(err)
        assertTrue(err is AppError.ConnectionError)
    }

    @Test
    fun testResilientCallRetryWithTransientRecovery() = runBlocking {
        var attempts = 0
        val result = ResilientCall.safeCallWithRetry(maxRetries = 3, initialDelayMs = 10L, maxDelayMs = 50L) {
            attempts++
            if (attempts < 3) {
                throw SocketTimeoutException("Transient glitch")
            }
            "SuccessAfterRetries"
        }
        assertTrue(result.isSuccess)
        assertEquals("SuccessAfterRetries", result.getOrNull())
        assertEquals(3, attempts)
    }

    @Test
    fun testResilientCallRetryAbortsOnNonRetryableError() = runBlocking {
        var attempts = 0
        val result = ResilientCall.safeCallWithRetry(maxRetries = 3, initialDelayMs = 10L) {
            attempts++
            val errorBody = "{}".toResponseBody("application/json".toMediaType())
            throw HttpException(Response.error<String>(401, errorBody))
        }
        assertTrue(result.isFailure)
        assertEquals(1, attempts) // Must NOT retry 401 Unauthorized!
        assertTrue(result.errorOrNull() is AppError.AuthenticationError)
    }

    @Test
    fun testDataValidatorRules() {
        // Phone numbers
        assertTrue(DataValidator.validatePhoneNumber("+919876543210").isSuccess)
        assertTrue(DataValidator.validatePhoneNumber("9876543210").isSuccess)
        assertTrue(DataValidator.validatePhoneNumber("6123456789").isSuccess)
        assertFalse(DataValidator.validatePhoneNumber("12345").isSuccess)
        assertFalse(DataValidator.validatePhoneNumber("abc").isSuccess)
        assertFalse(DataValidator.validatePhoneNumber("").isSuccess)

        // Coordinates
        assertTrue(DataValidator.validateCoordinates(28.6315, 77.2167).isSuccess)
        assertTrue(DataValidator.validateCoordinates(0.0, 0.0).isSuccess)
        assertTrue(DataValidator.validateCoordinates(-90.0, -180.0).isSuccess)
        assertTrue(DataValidator.validateCoordinates(90.0, 180.0).isSuccess)
        assertFalse(DataValidator.validateCoordinates(91.0, 77.0).isSuccess)
        assertFalse(DataValidator.validateCoordinates(28.0, 181.0).isSuccess)
        assertFalse(DataValidator.validateCoordinates(Double.NaN, 77.0).isSuccess)
        assertFalse(DataValidator.validateCoordinates(28.0, Double.POSITIVE_INFINITY).isSuccess)

        // Vehicle Plate
        assertTrue(DataValidator.validateVehicleRegistration("DL-1ER-9912").isSuccess)
        assertTrue(DataValidator.validateVehicleRegistration("DL-01-AB-1234").isSuccess)
        assertFalse(DataValidator.validateVehicleRegistration("").isSuccess)

        // Fare Amount
        assertTrue(DataValidator.validateFareAmount(250.0).isSuccess)
        assertFalse(DataValidator.validateFareAmount(0.0).isSuccess)
        assertFalse(DataValidator.validateFareAmount(-50.0).isSuccess)
        assertFalse(DataValidator.validateFareAmount(Double.NaN).isSuccess)
    }

    @Test
    fun testStructuredLoggerSanitizer() {
        val rawAuthLog = "Sending request with Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token"
        val sanitizedAuth = StructuredLogger.sanitize(rawAuthLog)
        assertFalse(sanitizedAuth.contains("eyJhbGci"))
        assertTrue(sanitizedAuth.contains("Bearer [REDACTED_TOKEN]"))

        val rawPassLog = "Payload: password=\"SuperSecretP@ss!\", email=\"pilot@vasantra.com\""
        val sanitizedPass = StructuredLogger.sanitize(rawPassLog)
        assertFalse(sanitizedPass.contains("SuperSecretP@ss!"))
        assertTrue(sanitizedPass.contains("password=[REDACTED]"))

        val rawCardLog = "Processing card number 4111 2222 3333 4444 for payment"
        val sanitizedCard = StructuredLogger.sanitize(rawCardLog)
        assertFalse(sanitizedCard.contains("4111 2222 3333 4444"))
        assertTrue(sanitizedCard.contains("[REDACTED_PAN]"))
    }

    @Test
    fun testUiStateModelProperties() {
        val loading = UiState.Loading("Fetching rides...")
        assertEquals("Fetching rides...", loading.message)

        val success = UiState.Success(listOf("RIDE-1", "RIDE-2"))
        assertEquals(2, success.data.size)

        val empty = UiState.Empty("No drivers available")
        assertEquals("No drivers available", empty.message)

        val offlineError = UiState.Error(AppError.NetworkError())
        assertTrue(offlineError.isOffline)
        assertTrue(offlineError.canRetry)

        val authError = UiState.Error(AppError.AuthenticationError())
        assertTrue(authError.isAuthRequired)
        assertFalse(authError.canRetry)

        val serverError = UiState.Error(AppError.HttpError(statusCode = 502))
        assertTrue(serverError.isServiceUnavailable)
        assertTrue(serverError.canRetry)
    }
}

