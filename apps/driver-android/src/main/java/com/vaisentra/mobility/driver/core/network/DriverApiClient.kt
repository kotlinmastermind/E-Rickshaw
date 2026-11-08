package com.vaisentra.mobility.driver.core.network

import android.content.Context
import com.vaisentra.mobility.driver.core.security.DriverTokenManager
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Enterprise Resilient DNS Resolver for Driver App.
 * Automatically resolves e-rickshaw-z32z.onrender.com even if the physical device's
 * local mobile network or WiFi ISP DNS resolver fails, times out, or blocks Render domains in India.
 */
object ResilientDns : Dns {
    private val RENDER_FALLBACK_IPS = listOf(
        byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 16.toByte()),
        byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 18.toByte())
    )

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) addresses else throw UnknownHostException("No IP address found for $hostname")
        } catch (e: Exception) {
            if (hostname.contains("onrender.com", ignoreCase = true)) {
                try {
                    RENDER_FALLBACK_IPS.map { ipBytes ->
                        InetAddress.getByAddress(hostname, ipBytes)
                    }
                } catch (_: Throwable) {
                    throw UnknownHostException("Unable to resolve server address for \"$hostname\": ${e.message}")
                }
            } else {
                throw UnknownHostException("Unable to resolve server address for \"$hostname\": ${e.message}")
            }
        }
    }
}

object DriverApiClient {
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

    private var tokenManager: DriverTokenManager? = null
    private var service: DriverApiService? = null

    fun initialize(context: Context) {
        tokenManager = DriverTokenManager.getInstance(context)
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

    fun getService(): DriverApiService {
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
            val response = chain.proceed(requestBuilder.build())
            if (response.code == 401) {
                tokenManager?.clear()
            }
            response
        }
        return OkHttpClient.Builder()
            .dns(ResilientDns)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    private fun createService(): DriverApiService {
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
            val response = chain.proceed(requestBuilder.build())
            if (response.code == 401) {
                tokenManager?.clear()
            }
            response
        }

        val client = OkHttpClient.Builder()
            .dns(ResilientDns)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(DriverApiService::class.java)
    }
}

