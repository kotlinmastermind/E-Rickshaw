package com.vaisentra.mobility.rider.data

import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object NetworkClient {
    private const val TAG = "RiderNetworkClient"
    private const val PREF_NAME = "ridergo_secure_prefs"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private var sharedPrefs: android.content.SharedPreferences? = null
    var onAuthExpired: (() -> Unit)? = null

    fun init(context: android.content.Context) {
        sharedPrefs = context.applicationContext.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        authToken = sharedPrefs?.getString(KEY_TOKEN, null)
        refreshToken = sharedPrefs?.getString(KEY_REFRESH_TOKEN, null)

        if (authToken != null && isTokenExpired(authToken)) {
            val currentRefresh = refreshToken
            if (currentRefresh == null || isTokenExpired(currentRefresh)) {
                clearAuthToken()
            }
        }
    }
    // Default to Android Emulator host or local LAN server (configurable at runtime)
    @Volatile
    var baseUrl: String = "https://e-rickshaw-z32z.onrender.com/"

    @Volatile
    var authToken: String? = null
        private set

    @Volatile
    var refreshToken: String? = null
        private set

    fun setAuthToken(token: String, refresh: String? = null) {
        require(token.isNotBlank()) { "Access token cannot be empty" }
        authToken = token
        sharedPrefs?.edit { putString(KEY_TOKEN, token) }
        if (!refresh.isNullOrBlank()) {
            refreshToken = refresh
            sharedPrefs?.edit { putString(KEY_REFRESH_TOKEN, refresh) }
        }
    }

    fun clearAuthToken() {
        authToken = null
        refreshToken = null
        sharedPrefs?.edit {
            remove(KEY_TOKEN)
            remove(KEY_REFRESH_TOKEN)
        }
    }

    fun isTokenExpired(token: String?): Boolean {
        if (token.isNullOrBlank()) return true
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return true
            val decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val json = JSONObject(String(decodedBytes))
            val expSeconds = json.optLong("exp", 0L)
            if (expSeconds == 0L) return false
            val nowSeconds = System.currentTimeMillis() / 1000
            nowSeconds >= (expSeconds - 30)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking token expiration: ${e.message}")
            false
        }
    }

    fun isAuthenticated(): Boolean {
        val token = authToken
        if (token.isNullOrBlank()) return false
        if (isTokenExpired(token)) {
            val currentRefresh = refreshToken
            return !currentRefresh.isNullOrBlank() && !isTokenExpired(currentRefresh)
        }
        return true
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-App-Platform", "Android-RiderGo")

        authToken?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }

        chain.proceed(requestBuilder.build())
    }

    private val auth401Interceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 401) {
            val path = request.url.encodedPath
            if (!path.contains("/auth/login") && !path.contains("/auth/otp") && !path.contains("/auth/token/refresh")) {
                val requestToken = request.header("Authorization")?.removePrefix("Bearer ")?.trim()
                val rotated = synchronized(this) {
                    val latestToken = authToken
                    if (!latestToken.isNullOrBlank() && latestToken != requestToken && !isTokenExpired(latestToken)) {
                        true
                    } else {
                        val currentRefresh = refreshToken
                        if (!currentRefresh.isNullOrBlank() && !isTokenExpired(currentRefresh)) {
                            attemptTokenRefresh(currentRefresh)
                        } else {
                            false
                        }
                    }
                }
                if (rotated) {
                    response.close()
                    val newRequest = request.newBuilder()
                        .header("Authorization", "Bearer $authToken")
                        .build()
                    return@Interceptor chain.proceed(newRequest)
                }
                clearAuthToken()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onAuthExpired?.invoke()
                }
            }
        }
        response
    }

    private fun attemptTokenRefresh(refresh: String): Boolean {
        return try {
            val refreshUrl = "${baseUrl.trimEnd('/')}/api/v1/auth/token/refresh"
            val payload = JSONObject().apply {
                put("refresh_token", refresh)
            }.toString()

            val refreshRequest = Request.Builder()
                .url(refreshUrl)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()

            val rawClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            rawClient.newCall(refreshRequest).execute().use { res ->
                if (res.isSuccessful) {
                    val bodyStr = res.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val newAccess = json.optString("access_token", "")
                    val newRefresh = json.optString("refresh_token", refresh)
                    if (newAccess.isNotBlank()) {
                        setAuthToken(newAccess, newRefresh)
                        return true
                    }
                } else {
                    Log.w(TAG, "Token refresh call failed with HTTP ${res.code}")
                }
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during token refresh: ${e.message}", e)
            false
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .addInterceptor(auth401Interceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val apiService: RiderApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RiderApiService::class.java)
    }
}

