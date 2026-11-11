package com.vaisentra.mobility.core.error

import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ErrorHandlingAndResilienceTest {

    @Test
    fun testDnsFailureMapping() {
        val ex = UnknownHostException("Unable to resolve host \"e-rickshaw-z32z.onrender.com\": No address associated with hostname")
        val error = ErrorMapper.map(ex)

        assertTrue("Error must be typed DnsError", error is AppError.DnsError)
        assertEquals("ERR_NET_CONNECTION", error.code)
        assertTrue(error.userMessage.contains("DNS", ignoreCase = true) || error.userMessage.contains("server address", ignoreCase = true))
        assertTrue(error.isRecoverable)
        assertTrue(error.isRetryEligible)
    }

    @Test
    fun testTlsFailureMapping() {
        val ex = SSLHandshakeException("CertPathValidatorException: Trust anchor for certification path not found.")
        val error = ErrorMapper.map(ex)

        assertTrue("Error must be typed TlsError", error is AppError.TlsError)
        assertEquals("ERR_NET_TLS", error.code)
        assertTrue(error.userMessage.contains("TLS", ignoreCase = true) || error.userMessage.contains("Secure", ignoreCase = true))
        assertFalse("TLS errors must not be auto-retried", error.isRetryEligible)
    }

    @Test
    fun testTimeoutFailureMapping() {
        val ex = SocketTimeoutException("failed to connect to e-rickshaw-z32z.onrender.com after 20000ms")
        val error = ErrorMapper.map(ex)

        assertTrue("Error must be TimeoutError", error is AppError.TimeoutError)
        assertEquals("ERR_NET_TIMEOUT", error.code)
        assertTrue(error.userMessage.contains("too long", ignoreCase = true))
        assertTrue(error.isRecoverable)
    }

    @Test
    fun testConnectionRefusedMapping() {
        val ex = ConnectException("Failed to connect to e-rickshaw-z32z.onrender.com/216.24.57.18:443: Connection refused")
        val error = ErrorMapper.map(ex)

        assertTrue("Error must be ConnectionError", error is AppError.ConnectionError)
        assertEquals("ERR_NET_CONNECTION", error.code)
    }

    @Test
    fun testCoroutineCancellationMapping() {
        val ex = CancellationException("Job was cancelled normally")
        val error = ErrorMapper.map(ex)

        assertTrue("Error must be CoroutineCancellation", error is AppError.CoroutineCancellation)
        assertEquals("ERR_COROUTINE_CANCELLED", error.code)
        assertFalse(error.isRetryEligible)
    }

    @Test
    fun testRetrofitHttpErrorsMapping() {
        val errorJson = "{\"detail\":\"Session expired\"}".toResponseBody("application/json".toMediaType())

        // 401 Unauthorized
        val resp401: Response<String> = Response.error(401, errorJson)
        val result401 = resp401.toAppResult()
        assertTrue(result401.isFailure)
        assertTrue(result401.errorOrNull() is AppError.AuthenticationError)

        // 403 Forbidden
        val resp403: Response<String> = Response.error(403, errorJson)
        val result403 = resp403.toAppResult()
        assertTrue(result403.isFailure)
        assertTrue(result403.errorOrNull() is AppError.AuthorizationError)

        // 404 Not Found
        val resp404: Response<String> = Response.error(404, errorJson)
        val result404 = resp404.toAppResult()
        assertTrue(result404.isFailure)
        val err404 = result404.errorOrNull() as AppError.HttpError
        assertEquals(404, err404.statusCode)

        // 429 Too Many Requests
        val resp429: Response<String> = Response.error(429, errorJson)
        val result429 = resp429.toAppResult()
        assertTrue(result429.isFailure)
        val err429 = result429.errorOrNull() as AppError.HttpError
        assertEquals(429, err429.statusCode)
        assertTrue(err429.isRetryEligible)

        // 502 Bad Gateway
        val resp502: Response<String> = Response.error(502, errorJson)
        val result502 = resp502.toAppResult()
        assertTrue(result502.isFailure)
        val err502 = result502.errorOrNull() as AppError.HttpError
        assertEquals(502, err502.statusCode)
        assertTrue(err502.userMessage.contains("Bad Gateway", ignoreCase = true))

        // 503 Service Unavailable
        val resp503: Response<String> = Response.error(503, errorJson)
        val result503 = resp503.toAppResult()
        assertTrue(result503.isFailure)
        val err503 = result503.errorOrNull() as AppError.HttpError
        assertEquals(503, err503.statusCode)
        assertTrue(err503.userMessage.contains("temporarily unavailable", ignoreCase = true))
    }

    @Test
    fun testEmptyResponseBodyRejectionForDataResponses() {
        // When an API call returns 200 OK with null body where non-Unit data was expected,
        // it must NOT pretend success with Unit or null. It must return AppError.EmptyResponseError.
        val emptyDataResp: Response<String> = Response.success(null)
        val result = emptyDataResp.toAppResult()

        assertTrue("Empty body for non-Unit type must fail", result.isFailure)
        val error = result.errorOrNull()
        assertTrue("Error must be EmptyResponseError", error is AppError.EmptyResponseError)
        assertEquals("ERR_RESP_EMPTY", error?.code)
    }

    @Test
    fun testEmptyResponseBodyAllowedForUnitResponses() {
        val emptyUnitResp: Response<Unit> = Response.success(null)
        val result = emptyUnitResp.toAppResult()

        assertTrue("Empty body for Unit response should succeed", result.isSuccess)
    }

    @Test
    fun testPaymentErrorNeverRetryEligible() {
        val payError = AppError.PaymentError(orderId = "order_123", paymentId = "pay_456")
        assertFalse("Payment errors must NEVER be eligible for automatic retry", payError.isRetryEligible)
    }
}
