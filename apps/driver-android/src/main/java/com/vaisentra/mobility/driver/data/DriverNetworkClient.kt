package com.vaisentra.mobility.driver.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object DriverNetworkClient {

    private const val PREF_NAME = "drivergo_secure_prefs"
    private const val KEY_TOKEN = "driver_access_token"
    private const val KEY_REFRESH_TOKEN = "driver_refresh_token"
    private const val KEY_PROFILE_PHOTO = "driver_profile_photo_uri"
    private var sharedPrefs: SharedPreferences? = null

    fun init(context: Context) {
        sharedPrefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        authToken = sharedPrefs?.getString(KEY_TOKEN, null)
        refreshToken = sharedPrefs?.getString(KEY_REFRESH_TOKEN, null)
    }

    @Volatile
    var baseUrl: String = "https://e-rickshaw-z32z.onrender.com/"
        set(value) {
            val normalized = if (value.endsWith("/")) value else "$value/"
            if (field != normalized) {
                field = normalized
                synchronized(this) {
                    cachedBaseUrl = null
                    cachedApiService = null
                }
            }
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

    fun isAuthenticated(): Boolean = !authToken.isNullOrBlank()

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

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
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