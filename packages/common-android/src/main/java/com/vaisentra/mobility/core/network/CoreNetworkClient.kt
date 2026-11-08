package com.vaisentra.mobility.core.network

import com.vaisentra.mobility.core.security.BaseTokenManager
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Standardized Core Network Client Factory for all mobile apps.
 * Implements resilient Anycast DNS, standard timeout policies,
 * JWT Authorization header injection, client telemetry headers,
 * and TLS certificate pinning for production backend domains.
 */
object CoreNetworkClient {
    const val DEFAULT_PRODUCTION_URL: String = "https://e-rickshaw-z32z.onrender.com/"
    const val PRODUCTION_HOSTNAME: String = "e-rickshaw-z32z.onrender.com"

    /**
     * Builds an OkHttpClient with certificate pinning support.
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

