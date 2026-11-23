package com.vaisentra.mobility.rider.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import androidx.core.content.edit


object NetworkClient {
    private const val PREF_NAME = "ridergo_secure_prefs"
    private const val KEY_TOKEN = "access_token"
    private var sharedPrefs: android.content.SharedPreferences? = null

    fun init(context: android.content.Context) {
        sharedPrefs = context.applicationContext.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
        authToken = sharedPrefs?.getString(KEY_TOKEN, null)
    }
    // Default to Android Emulator host or local LAN server (configurable at runtime)
    @Volatile
    var baseUrl: String = "https://e-rickshaw-z32z.onrender.com/"

    @Volatile
    var authToken: String? = null
        private set

    // setAuthToken ke andar:
    fun setAuthToken(token: String) {
        require(token.isNotBlank()) { "Access token cannot be empty" }
        authToken = token
        sharedPrefs?.edit { putString(KEY_TOKEN, token) }
    }

    // clearAuthToken ke andar:
    fun clearAuthToken() {
        authToken = null
        sharedPrefs?.edit { remove(KEY_TOKEN) }
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

