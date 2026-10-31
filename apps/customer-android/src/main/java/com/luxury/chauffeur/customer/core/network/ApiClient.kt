package com.luxury.chauffeur.customer.core.network

import android.content.Context
import com.luxury.chauffeur.customer.core.security.TokenManager
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
 * Enterprise Resilient DNS Resolver.
 * Automatically resolves e-rickshaw-z32z.onrender.com even if the physical device's
 * local mobile network or WiFi ISP DNS resolver fails, times out, or blocks Render domains in India.
 */
object ResilientDns : Dns {
    private val RENDER_FALLBACK_IPS = listOf(
        byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 16.toByte()),
        byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 18.toByte())
    )

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.contains("onrender.com", ignoreCase = true)) {
            // First attempt: try system DNS with a quick 2.5-second timeout
            try {
                val future = java.util.concurrent.CompletableFuture.supplyAsync {
                    Dns.SYSTEM.lookup(hostname)
                }
                val addresses = future.get(2500, TimeUnit.MILLISECONDS)
                if (!addresses.isNullOrEmpty()) return addresses
            } catch (_: Throwable) {
                // System DNS failed, hung, or timed out
            }

            // Guaranteed Anycast edge fallback: direct Cloudflare Anycast Origin IPs
            return try {
                RENDER_FALLBACK_IPS.map { ipBytes ->
                    InetAddress.getByAddress(hostname, ipBytes)
                }
            } catch (_: Throwable) {
                listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 16.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 18.toByte()))
                )
            }
        }
        return try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) addresses else throw UnknownHostException("No IP address found for $hostname")
        } catch (e: UnknownHostException) {
            throw e
        } catch (t: Throwable) {
            throw UnknownHostException("Unable to resolve server address for \"$hostname\": ${t.message}")
        }
    }
}

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
            .dns(ResilientDns)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
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

        return retrofit.create(CustomerApiService::class.java)
    }
}

