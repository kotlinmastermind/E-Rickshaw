package com.vaisentra.mobility.driver.core.network

import android.content.Context
import com.vaisentra.mobility.core.network.BaseApiClient
import com.vaisentra.mobility.core.network.NetworkEnvironmentManager
import com.vaisentra.mobility.core.security.BaseTokenManager
import com.vaisentra.mobility.core.security.TokenManager
import com.vaisentra.mobility.driver.BuildConfig

/**
 * Standardized ApiClient for Driver App extending BaseApiClient.
 * Strictly locks to the canonical Production API: https://e-rickshaw-z32z.onrender.com/
 */
object DriverApiClient : BaseApiClient<DriverApiService>("driver", DriverApiService::class.java) {
    const val CANONICAL_PRODUCTION_URL: String = NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL
    const val RENDER_BASE_URL: String = NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL

    override fun resolveTokenManager(context: Context): BaseTokenManager {
        return TokenManager.getDriverInstance(context)
    }

    fun initialize(context: Context) {
        initialize(context, isRelease = !BuildConfig.DEBUG, isDebug = BuildConfig.DEBUG)
    }
}
