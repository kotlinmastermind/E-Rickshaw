package com.vaisentra.mobility.rider.core.network

import android.util.Log
import com.vaisentra.mobility.core.network.ResilientDns
import com.vaisentra.mobility.rider.core.security.TokenManager
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Enterprise Token Authenticator for Rider App.
 * Handles automatic JWT access token refresh via /api/v1/auth/token/refresh
 * without evicting the user session upon temporary 401 errors.
 */
class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val baseUrlProvider: () -> String
) : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
        private const val MAX_AUTH_RETRY_COUNT = 2
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_AUTH_RETRY_COUNT) {
            Log.w(TAG, "Max authentication retry count reached ($MAX_AUTH_RETRY_COUNT). Giving up.")
            return null
        }

        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken.isNullOrBlank()) {
            Log.w(TAG, "No refresh token available in TokenManager. Cannot refresh access token.")
            tokenManager.clear()
            return null
        }

        synchronized(this) {
            val currentAccessToken = tokenManager.getToken()
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()

            // If another thread already successfully refreshed the access token
            if (!currentAccessToken.isNullOrBlank() && currentAccessToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .build()
            }

            // Perform synchronous token rotation with the backend
            val cleanBaseUrl = baseUrlProvider().removeSuffix("/")
            val refreshUrl = "$cleanBaseUrl/api/v1/auth/token/refresh"

            val refreshHttpClient = OkHttpClient.Builder()
                .dns(ResilientDns)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val jsonPayload = JSONObject().apply {
                put("refresh_token", refreshToken)
            }.toString()

            val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val refreshRequest = Request.Builder()
                .url(refreshUrl)
                .post(requestBody)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build()

            try {
                val refreshResponse = refreshHttpClient.newCall(refreshRequest).execute()
                if (refreshResponse.isSuccessful) {
                    val responseBody = refreshResponse.body?.string().orEmpty()
                    val json = JSONObject(responseBody)
                    val newAccessToken = json.optString("access_token", "")
                    val newRefreshToken = json.optString("refresh_token", "")

                    if (newAccessToken.isNotBlank()) {
                        Log.i(TAG, "Access token successfully refreshed and rotated.")
                        tokenManager.saveTokens(newAccessToken, newRefreshToken.ifBlank { refreshToken })
                        return response.request.newBuilder()
                            .header("Authorization", "Bearer $newAccessToken")
                            .build()
                    }
                } else if (refreshResponse.code == 401 || refreshResponse.code == 403) {
                    Log.w(TAG, "Refresh token rejected by server (HTTP ${refreshResponse.code}). Session revoked.")
                    tokenManager.clear()
                    return null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Network failure during token refresh: ${e.message}")
                return null
            }

            return null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

