package com.vaisentra.mobility.core.network

import android.content.Context
import android.util.Log
import com.vaisentra.mobility.core.security.BaseTokenManager
import okhttp3.OkHttpClient

/**
 * Standardized, thread-safe, strictly PRODUCTION-ONLY ApiClient base class for all Vasantra Mobility applications.
 * Eliminates multi-environment switching, local development routing, and staging overrides.
 * All requests resolve authoritatively to the canonical Production endpoint.
 */
abstract class BaseApiClient<T : Any>(
    val clientRole: String,
    protected val serviceClass: Class<T>
) {
    val tag: String = "${clientRole.replaceFirstChar { it.uppercase() }}ApiClient"

    fun isEmulator(): Boolean = CoreNetworkClient.isEmulator()

    val currentEnvironment: String = "Production"

    @Volatile
    var baseUrl: String = NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL
        private set

    @Volatile
    protected var tokenManager: BaseTokenManager? = null

    @Volatile
    private var service: T? = null

    @Volatile
    private var cachedOkHttpClient: OkHttpClient? = null

    abstract fun resolveTokenManager(context: Context): BaseTokenManager

    fun initialize(context: Context, isRelease: Boolean = false, isDebug: Boolean = true) {
        synchronized(this) {
            tokenManager = resolveTokenManager(context)
            // Safely clean any legacy local/staging environment preferences
            tokenManager?.cleanLegacyEnvironmentSettings()

            baseUrl = NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL
            NetworkEnvironmentManager.validateProductionUrl(baseUrl)

            cachedOkHttpClient = null
            service = createService(isDebug)
            Log.i(tag, "Initialized $tag: env=Production, baseUrl=$baseUrl, isRelease=$isRelease")
        }
    }

    /**
     * Prevents arbitrary URL overrides. Rejects non-canonical URLs.
     */
    fun updateBaseUrl(newUrl: String, isRelease: Boolean = false, isDebug: Boolean = true) {
        val formatted = NetworkEnvironmentManager.sanitizeUrl(newUrl)
        NetworkEnvironmentManager.validateProductionUrl(formatted)
        synchronized(this) {
            baseUrl = formatted
            cachedOkHttpClient = null
            service = createService(isDebug)
            Log.i(tag, "Verified Base URL: $baseUrl")
        }
    }

    fun isLocalBackend(): Boolean = false

    fun getService(): T {
        return service ?: synchronized(this) {
            service ?: createService().also { service = it }
        }
    }

    fun getToken(): String? = tokenManager?.getToken()

    fun getWebSocketUrl(path: String): String = CoreNetworkClient.getWebSocketUrl(baseUrl, path)

    fun getOkHttpClient(isDebug: Boolean = true): OkHttpClient {
        return cachedOkHttpClient ?: synchronized(this) {
            cachedOkHttpClient ?: CoreNetworkClient.createConfiguredOkHttpClient(
                tokenManager = tokenManager,
                baseUrlProvider = { baseUrl },
                clientRole = clientRole,
                isDebug = isDebug
            ).also { cachedOkHttpClient = it }
        }
    }

    fun createService(isDebug: Boolean = true): T {
        val client = getOkHttpClient(isDebug)
        val retrofit = CoreNetworkClient.createRetrofit(baseUrl, client)
        return retrofit.create(serviceClass)
    }
}
