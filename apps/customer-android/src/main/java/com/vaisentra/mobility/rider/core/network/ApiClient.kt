package com.vaisentra.mobility.rider.core.network

import android.content.Context
import com.vaisentra.mobility.core.network.CoreNetworkClient
import com.vaisentra.mobility.rider.core.security.TokenManager
import okhttp3.OkHttpClient

/**
 * Standardized ApiClient for Rider App leveraging CoreNetworkClient.
 */
object ApiClient {
    const val RENDER_BASE_URL: String = CoreNetworkClient.DEFAULT_PRODUCTION_URL
    const val LOCAL_EMULATOR_BASE_URL: String = CoreNetworkClient.LOCAL_EMULATOR_BASE_URL

    fun isEmulator(): Boolean = CoreNetworkClient.isEmulator()

    @Volatile
    var baseUrl: String = RENDER_BASE_URL

    @Volatile
    private var tokenManager: TokenManager? = null

    @Volatile
    private var service: CustomerApiService? = null

    @Volatile
    private var cachedOkHttpClient: OkHttpClient? = null

    fun initialize(context: Context) {
        synchronized(this) {
            tokenManager = TokenManager.getInstance(context)
            baseUrl = CoreNetworkClient.resolveDefaultBaseUrl(tokenManager?.getServerUrl())
            cachedOkHttpClient = null
            service = createService()
        }
    }

    fun updateBaseUrl(newUrl: String) {
        val formatted = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        synchronized(this) {
            baseUrl = formatted
            tokenManager?.setServerUrl(formatted)
            cachedOkHttpClient = null
            service = createService()
        }
    }

    fun useCloudProduction() {
        updateBaseUrl(RENDER_BASE_URL)
    }

    fun useLocalEmulator() {
        updateBaseUrl(LOCAL_EMULATOR_BASE_URL)
    }

    fun isLocalBackend(): Boolean = CoreNetworkClient.isLocalBackend(baseUrl)

    fun getService(): CustomerApiService {
        return service ?: synchronized(this) {
            service ?: createService().also { service = it }
        }
    }

    fun getToken(): String? = tokenManager?.getToken()

    fun getWebSocketUrl(path: String): String = CoreNetworkClient.getWebSocketUrl(baseUrl, path)

    fun getOkHttpClient(): OkHttpClient {
        return cachedOkHttpClient ?: synchronized(this) {
            cachedOkHttpClient ?: CoreNetworkClient.createConfiguredOkHttpClient(
                tokenManager = tokenManager,
                baseUrlProvider = { baseUrl },
                clientRole = "customer"
            ).also { cachedOkHttpClient = it }
        }
    }

    private fun createService(): CustomerApiService {
        val client = getOkHttpClient()
        val retrofit = CoreNetworkClient.createRetrofit(baseUrl, client)
        return retrofit.create(CustomerApiService::class.java)
    }
}
