package com.luxury.chauffeur.customer.core.network

import android.content.Context
import com.luxury.chauffeur.customer.core.security.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Cloud Render URL (Production online server)
    const val RENDER_BASE_URL: String = "https://e-rickshaw-z32z.onrender.com/"

    // Base URL used by Retrofit (Strictly points to online Render cloud server)
    var baseUrl: String = RENDER_BASE_URL

    private var tokenManager: TokenManager? = null
    private var service: CustomerApiService? = null

    fun initialize(context: Context) {
        tokenManager = TokenManager.getInstance(context)
        baseUrl = RENDER_BASE_URL
        service = createService()
    }

    fun updateBaseUrl(newUrl: String) {
        baseUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        service = createService()
    }

    fun useCloudProduction() {
        updateBaseUrl(RENDER_BASE_URL)
    }

    fun getService(): CustomerApiService {
        if (service == null) {
            service = createService()
        }
        return service!!
    }

    fun getToken(): String? = tokenManager?.getToken()

    fun getWebSocketUrl(path: String): String {
        val cleanBase = baseUrl.removeSuffix("/")
        val wsBase = if (cleanBase.startsWith("https://")) {
            cleanBase.replaceFirst("https://", "wss://")
        } else {
            cleanBase.replaceFirst("http://", "ws://")
        }
        val cleanPath = path.removePrefix("/")
        return "$wsBase/$cleanPath"
    }

    fun getOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            tokenManager?.getToken()?.let { token ->
                if (token.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }
            chain.proceed(requestBuilder.build())
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    private fun createService(): CustomerApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()

            tokenManager?.getToken()?.let { token ->
                if (token.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }

            requestBuilder.header("Accept", "application/json")
            requestBuilder.header("Content-Type", "application/json")
            chain.proceed(requestBuilder.build())
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(CustomerApiService::class.java)
    }
}

