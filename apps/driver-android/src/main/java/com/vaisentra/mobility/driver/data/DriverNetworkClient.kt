package com.vaisentra.mobility.driver.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import com.vaisentra.mobility.driver.BuildConfig
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object DriverNetworkClient {

    private const val TAG = "DriverNetworkClient"
    private const val PREF_NAME = "drivergo_secure_prefs"
    private const val KEY_TOKEN = "driver_access_token"
    private const val KEY_REFRESH_TOKEN = "driver_refresh_token"
    private const val KEY_PROFILE_PHOTO = "driver_profile_photo_uri"
    private const val KEY_CUSTOM_BASE_URL = "driver_custom_base_url"

    const val DEFAULT_RENDER_URL = "https://e-rickshaw-z32z.onrender.com/"
    const val LOCAL_EMULATOR_URL = "http://10.0.2.2:8000/"

    private var sharedPrefs: SharedPreferences? = null
    var onAuthExpired: (() -> Unit)? = null

    fun init(context: Context) {
        sharedPrefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedBaseUrl = sharedPrefs?.getString(KEY_CUSTOM_BASE_URL, null)
        if (!savedBaseUrl.isNullOrBlank()) {
            baseUrl = savedBaseUrl
        }
        authToken = sharedPrefs?.getString(KEY_TOKEN, null)
        refreshToken = sharedPrefs?.getString(KEY_REFRESH_TOKEN, null)

        // Validate access token expiry on startup
        if (authToken != null && isTokenExpired(authToken)) {
            Log.w(TAG, "Stored access token is expired on startup.")
            val refresh = refreshToken
            if (refresh != null && !isTokenExpired(refresh)) {
                Log.i(TAG, "Valid refresh token found; will auto-rotate on next request.")
            } else {
                Log.w(TAG, "Refresh token is also missing or expired; clearing cached credentials.")
                clearAuthToken()
            }
        }
    }

    @Volatile
    var baseUrl: String = DEFAULT_RENDER_URL
        set(value) {
            val normalized = if (value.endsWith("/")) value else "$value/"
            if (field != normalized) {
                field = normalized
                sharedPrefs?.edit { putString(KEY_CUSTOM_BASE_URL, normalized) }
                synchronized(this) {
                    cachedBaseUrl = null
                    cachedApiService = null
                }
            }
        }

    fun switchServer(url: String) {
        baseUrl = url
        Log.i(TAG, "Switched backend server to: $baseUrl")
    }

    @Volatile
    var authToken: String? = null
        private set

    @Volatile
    var refreshToken: String? = null
        private set

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedApiService: DriverApiService? = null

    fun setAuthToken(token: String, refresh: String? = null) {
        require(token.isNotBlank()) {
            "Access token cannot be empty"
        }
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

    fun saveProfilePhotoUri(uriString: String) {
        sharedPrefs?.edit { putString(KEY_PROFILE_PHOTO, uriString) }
    }

    fun getProfilePhotoUri(): String? {
        return sharedPrefs?.getString(KEY_PROFILE_PHOTO, null)
    }

    fun clearProfilePhotoUri() {
        sharedPrefs?.edit { remove(KEY_PROFILE_PHOTO) }
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
            // Expired if current time exceeds expiry minus 30 second safety margin
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
            val ref = refreshToken
            return !ref.isNullOrBlank() && !isTokenExpired(ref)
        }
        return true
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("Accept", "application/json")
            .header("X-App-Platform", "Android-DriverGo")

        if (original.body != null && original.header("Content-Type") == null) {
            requestBuilder.header("Content-Type", "application/json")
        }

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
                Log.w(TAG, "401 Unauthorized encountered on $path. Checking refresh token...")
                val currentRefresh = refreshToken
                if (!currentRefresh.isNullOrBlank() && !isTokenExpired(currentRefresh)) {
                    val rotated = attemptTokenRefresh(currentRefresh)
                    if (rotated) {
                        response.close()
                        val newRequest = request.newBuilder()
                            .header("Authorization", "Bearer $authToken")
                            .build()
                        return@Interceptor chain.proceed(newRequest)
                    }
                }
                // Refresh failed or no refresh token: clear invalid session
                Log.w(TAG, "Session expired or invalid credentials; clearing token.")
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
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val res = rawClient.newCall(refreshRequest).execute()
            if (res.isSuccessful) {
                val bodyStr = res.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val newAccess = json.optString("access_token", "")
                val newRefresh = json.optString("refresh_token", refresh)
                if (newAccess.isNotBlank()) {
                    setAuthToken(newAccess, newRefresh)
                    Log.i(TAG, "Successfully auto-rotated access token via refresh token.")
                    return true
                }
            } else {
                Log.w(TAG, "Token refresh call failed with HTTP ${res.code}")
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Exception during token refresh: ${e.message}", e)
            false
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .addInterceptor(auth401Interceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val apiService: DriverApiService
        get() {
            val currentUrl = baseUrl
            val existing = cachedApiService
            if (existing != null && cachedBaseUrl == currentUrl) {
                return existing
            }
            return synchronized(this) {
                val recheck = cachedApiService
                if (recheck != null && cachedBaseUrl == currentUrl) {
                    recheck
                } else {
                    val created = Retrofit.Builder()
                        .baseUrl(currentUrl)
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(DriverApiService::class.java)
                    cachedBaseUrl = currentUrl
                    cachedApiService = created
                    created
                }
            }
        }
}