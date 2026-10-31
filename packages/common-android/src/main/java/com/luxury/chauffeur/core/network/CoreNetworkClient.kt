package com.luxury.chauffeur.core.network

import com.luxury.chauffeur.core.security.BaseTokenManager
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
 * JWT Authorization header injection, and client telemetry headers.
 */
object CoreNetworkClient {
    const val DEFAULT_PRODUCTION_URL: String = "https://e-rickshaw-z32z.onrender.com/"

    fun createOkHttpClient(
        tokenProvider: () -> String?,
        clientRole: String = "mobile",
        isDebug: Boolean = true
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

        return OkHttpClient.Builder()
            .dns(ResilientDns)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
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

