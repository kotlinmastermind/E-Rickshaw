package com.vaisentra.mobility.driver.core.network

import android.content.Context
import com.vaisentra.mobility.core.network.CoreNetworkClient
import com.vaisentra.mobility.driver.core.security.DriverTokenManager
import okhttp3.OkHttpClient

/**
 * Standardized ApiClient for Driver App leveraging CoreNetworkClient.
 */
object DriverApiClient {
    const val RENDER_BASE_URL: String = CoreNetworkClient.DEFAULT_PRODUCTION_URL
    const val LOCAL_EMULATOR_BASE_URL: String = CoreNetworkClient.LOCAL_EMULATOR_BASE_URL

    fun isEmulator(): Boolean = CoreNetworkClient.isEmulator()

    var baseUrl: String = RENDER_BASE_URL

    private var tokenManager: DriverTokenManager? = null
    private var service: DriverApiService? = null

    fun initialize(context: Context) {
        tokenManager = DriverTokenManager.getInstance(context)
        baseUrl = CoreNetworkClient.resolveDefaultBaseUrl(tokenManager?.getServerUrl())
        service = createService()
    }

    fun updateBaseUrl(newUrl: String) {
        val formatted = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        baseUrl = formatted
        tokenManager?.setServerUrl(formatted)
        service = createService()
    }

    fun useCloudProduction() {
        updateBaseUrl(RENDER_BASE_URL)
    }

    fun useLocalEmulator() {
        updateBaseUrl(LOCAL_EMULATOR_BASE_URL)
    }

    fun isLocalBackend(): Boolean = CoreNetworkClient.isLocalBackend(baseUrl)

    fun getService(): DriverApiService {
        if (service == null) {
            service = createService()
        }
        return service!!
    }

    fun getToken(): String? = tokenManager?.getToken()

    fun getWebSocketUrl(path: String): String = CoreNetworkClient.getWebSocketUrl(baseUrl, path)

    fun getOkHttpClient(): OkHttpClient {
        return CoreNetworkClient.createConfiguredOkHttpClient(
            tokenManager = tokenManager,
            baseUrlProvider = { baseUrl },
            clientRole = "driver"
        )
    }

    private fun createService(): DriverApiService {
        val client = getOkHttpClient()
        val retrofit = CoreNetworkClient.createRetrofit(baseUrl, client)
        return retrofit.create(DriverApiService::class.java)
    }
}
