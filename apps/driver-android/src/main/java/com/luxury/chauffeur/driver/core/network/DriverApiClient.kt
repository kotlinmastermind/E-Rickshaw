package com.luxury.chauffeur.driver.core.network

import android.content.Context
import com.luxury.chauffeur.driver.core.security.DriverTokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object DriverApiClient {
    // Cloud Render URL (Production deployment)
    const val RENDER_BASE_URL: String = "https://e-rickshaw-z32z.onrender.com/"

    // Local host machine URL for Android Emulator
    const val LOCAL_EMULATOR_BASE_URL: String = "http://10.0.2.2:8000/"

    // Base URL currently used by Retrofit (default: Render Cloud for production release)
    var baseUrl: String = RENDER_BASE_URL

    private var tokenManager: DriverTokenManager? = null
    private var service: DriverApiService? = null

    fun initialize(context: Context) {
        tokenManager = DriverTokenManager.getInstance(context)
        service = createService()
    }

    fun updateBaseUrl(newUrl: String) {
        baseUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        service = createService()
    }

    fun useLocalEmulator() {
        updateBaseUrl(LOCAL_EMULATOR_BASE_URL)
    }

    fun useCloudProduction() {
        updateBaseUrl(RENDER_BASE_URL)
    }

    fun getService(): DriverApiService {
        if (service == null) {
            service = createService()
        }
        return service!!
    }

    private fun createService(): DriverApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
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
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(DriverApiService::class.java)
    }
}
