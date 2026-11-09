package com.vaisentra.mobility.rider.core.network

import android.content.Context
import com.vaisentra.mobility.core.network.ResilientDns
import com.vaisentra.mobility.rider.core.security.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Cloud Render URL (Production online server)
    const val RENDER_BASE_URL: String = "https://e-rickshaw-z32z.onrender.com/"
    const val LOCAL_EMULATOR_BASE_URL: String = "http://10.0.2.2:8000/"

    fun isEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.startsWith("generic")
            || android.os.Build.FINGERPRINT.startsWith("unknown")
            || android.os.Build.MODEL.contains("google_sdk")
            || android.os.Build.MODEL.contains("Emulator")
            || android.os.Build.MODEL.contains("Android SDK built for x86")
            || android.os.Build.HARDWARE.contains("goldfish")
            || android.os.Build.HARDWARE.contains("ranchu")
            || android.os.Build.PRODUCT.contains("sdk_gphone")
            || android.os.Build.PRODUCT.contains("google_sdk")
    }

    // Base URL used by Retrofit (Automatically selects local emulator 10.0.2.2 or online Render)
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
            requestBuilder.header("Accept", "application/json")
            requestBuilder.header("Content-Type", "application/json")
            chain.proceed(requestBuilder.build())
        }
        val builder = OkHttpClient.Builder()
            .dns(ResilientDns)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)

        tokenManager?.let { tm ->
            builder.authenticator(TokenAuthenticator(tm) { baseUrl })
        }

        return builder.build()
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

        val builder = OkHttpClient.Builder()
            .dns(ResilientDns)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)

        tokenManager?.let { tm ->
            builder.authenticator(TokenAuthenticator(tm) { baseUrl })
        }

        val client = builder.build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(CustomerApiService::class.java)
    }
}

