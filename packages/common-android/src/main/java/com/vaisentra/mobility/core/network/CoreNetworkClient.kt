package com.vaisentra.mobility.core.network

import android.os.Build
import com.vaisentra.mobility.core.security.BaseTokenManager
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Standardized Core Network Client Factory for all mobile apps.
 * Implements resilient Anycast DNS, standard timeout policies,
 * JWT Authorization header injection, automatic token refresh rotation,
 * client telemetry headers, and TLS certificate pinning for production backend domains.
 */
object CoreNetworkClient {
    const val DEFAULT_PRODUCTION_URL: String = "https://e-rickshaw-z32z.onrender.com/"
    const val LOCAL_EMULATOR_BASE_URL: String = "http://10.0.2.2:8000/"
    const val PRODUCTION_HOSTNAME: String = "e-rickshaw-z32z.onrender.com"

    fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty()
        val model = Build.MODEL.orEmpty()
        val hardware = Build.HARDWARE.orEmpty()
        val product = Build.PRODUCT.orEmpty()

        return fingerprint.startsWith("generic")
            || fingerprint.startsWith("unknown")
            || model.contains("google_sdk")
            || model.contains("Emulator")
            || model.contains("Android SDK built for x86")
            || hardware.contains("goldfish")
            || hardware.contains("ranchu")
            || product.contains("sdk_gphone")
            || product.contains("google_sdk")
    }

    fun resolveDefaultBaseUrl(savedUrl: String? = null): String {
        return when {
            !savedUrl.isNullOrBlank() -> if (savedUrl.endsWith("/")) savedUrl else "$savedUrl/"
            isEmulator() -> LOCAL_EMULATOR_BASE_URL
            else -> DEFAULT_PRODUCTION_URL
        }
    }

    fun isLocalBackend(baseUrl: String): Boolean {
        return baseUrl.contains("10.0.2.2") || baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")
    }

    fun getWebSocketUrl(baseUrl: String, path: String): String {
        val cleanBase = baseUrl.removeSuffix("/")
        val wsBase = if (cleanBase.startsWith("https://")) {
            cleanBase.replaceFirst("https://", "wss://")
        } else {
            cleanBase.replaceFirst("http://", "ws://")
        }
        val cleanPath = path.removePrefix("/")
        return "$wsBase/$cleanPath"
    }

    /**
     * Builds a production-grade OkHttpClient configured with ResilientDns,
     * JWT authorization interceptor, logging, timeouts, and automatic TokenAuthenticator.
     */
    fun createConfiguredOkHttpClient(
        tokenManager: BaseTokenManager? = null,
        baseUrlProvider: () -> String = { DEFAULT_PRODUCTION_URL },
        clientRole: String = "mobile",
        isDebug: Boolean = true,
        connectTimeoutSeconds: Long = 60,
        readTimeoutSeconds: Long = 90,
        writeTimeoutSeconds: Long = 60,
        callTimeoutSeconds: Long = 90,
        certificatePinner: CertificatePinner? = null
    ): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
                .header("X-Client-Platform", "Android")
                .header("X-Client-Role", clientRole)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")

            val token = tokenManager?.getToken()
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (isDebug) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }

        val builder = OkHttpClient.Builder()
            .dns(ResilientDns)
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)

        if (tokenManager != null) {
            builder.authenticator(CoreTokenAuthenticator(tokenManager, baseUrlProvider))
        }

        if (certificatePinner != null) {
            builder.certificatePinner(certificatePinner)
        }

        return builder.build()
    }

    /**
     * Backwards-compatible factory taking a tokenProvider closure.
     */
    fun createOkHttpClient(
        tokenProvider: () -> String?,
        clientRole: String = "mobile",
        isDebug: Boolean = true,
        certificatePinner: CertificatePinner? = null
    ): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
                .header("X-Client-Platform", "Android")
                .header("X-Client-Role", clientRole)
                .header("Accept", "application/json")

            val token = tokenProvider()
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (isDebug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val builder = OkHttpClient.Builder()
            .dns(ResilientDns)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)

        if (certificatePinner != null) {
            builder.certificatePinner(certificatePinner)
        }

        return builder.build()
    }

    /**
     * Helper to construct a CertificatePinner with primary and backup pins for the production domain.
     */
    fun createProductionCertificatePinner(
        hostname: String = PRODUCTION_HOSTNAME,
        vararg sha256Pins: String
    ): CertificatePinner {
        val builder = CertificatePinner.Builder()
        for (pin in sha256Pins) {
            builder.add(hostname, "sha256/$pin")
        }
        return builder.build()
    }

    fun createRetrofit(
        baseUrl: String = DEFAULT_PRODUCTION_URL,
        okHttpClient: OkHttpClient
    ): Retrofit {
        val sanitizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(sanitizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
