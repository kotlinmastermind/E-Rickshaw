package com.vaisentra.mobility.rider.core.network

import android.content.Context
import com.vaisentra.mobility.core.network.BaseApiClient
import com.vaisentra.mobility.core.network.NetworkEnvironmentManager
import com.vaisentra.mobility.core.security.BaseTokenManager
import com.vaisentra.mobility.core.security.TokenManager
import com.vaisentra.mobility.rider.BuildConfig

/**
 * Standardized ApiClient for Rider App extending BaseApiClient.
 * Strictly locks to the canonical Production API: https://e-rickshaw-z32z.onrender.com/
 */
object ApiClient : BaseApiClient<CustomerApiService>("customer", CustomerApiService::class.java) {
    const val CANONICAL_PRODUCTION_URL: String = NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL
    const val RENDER_BASE_URL: String = NetworkEnvironmentManager.CANONICAL_PRODUCTION_URL

    override fun resolveTokenManager(context: Context): BaseTokenManager {
        return TokenManager.getCustomerInstance(context)
    }

    fun initialize(context: Context) {
        initialize(context, isRelease = !BuildConfig.DEBUG, isDebug = BuildConfig.DEBUG)
    }
}
