package com.vaisentra.mobility.driver

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.mapbox.common.MapboxOptions
import com.vaisentra.mobility.core.map.MapboxSharedConfig
import com.vaisentra.mobility.driver.core.network.DriverApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DriverApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        setupUncaughtExceptionHandler()

        // Synchronously initialize Mapbox SDK before any UI/MapView inflation
        initializeMapboxSdk()

        try {
            DriverApiClient.initialize(this)
        } catch (_: Throwable) {}

        if (!isMainProcess()) {
            setupSecondaryProcessWebView()
        }
    }

    private fun isMainProcess(): Boolean {
        val processName = getAppProcessName()
        return processName == null || processName == packageName
    }

    private fun getAppProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName()
        }
        val pid = android.os.Process.myPid()
        val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        am?.runningAppProcesses?.forEach { proc ->
            if (proc.pid == pid) return proc.processName
        }
        return null
    }

    private fun setupSecondaryProcessWebView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = getAppProcessName()
            if (processName != null && processName != packageName) {
                try {
                    val suffix = processName.replace(':', '_')
                    WebView.setDataDirectorySuffix(suffix)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isGoogleMapsOrPlayServicesException(thread, throwable)) {
                Log.w("DriverApplication", "Caught non-fatal Play Services / Maps exception on thread [${thread.name}]: ${throwable.message}")
                return@setDefaultUncaughtExceptionHandler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun isGoogleMapsOrPlayServicesException(thread: Thread, throwable: Throwable?): Boolean {
        if (throwable == null) return false
        val fullTrace = buildString {
            append(thread.name)
            append(" ")
            append(throwable.javaClass.name)
            append(" ")
            append(throwable.message.orEmpty())
            append(" ")
            append(Log.getStackTraceString(throwable))
            var cause = throwable.cause
            var depth = 0
            while (cause != null && depth < 10) {
                append(" ")
                append(cause.javaClass.name)
                append(" ")
                append(cause.message.orEmpty())
                cause = cause.cause
                depth++
            }
        }

        val nonFatalKeywords = listOf(
            "FlagStore",
            "policy_maps_core_dynamite",
            "maps_dynamite",
            "DynamiteModule",
            "No acceptable module",
            "pre-Chimera",
            "com.google.android.gms.dynamite",
            "zzcc",
            "LoadingException",
            "m140.cpq",
            "m140.cps",
            "cpq.u",
            "cpq.f",
            "cpq.j",
            "cps.run",
            "NativeSqliteDiskCacheImpl",
            "Database lock unavailable",
            "mapcore",
            "CronetException",
            "NO_CONNECTIVITY",
            "REQUEST_TIMEOUT",
            "Unknown calling package name",
            "Failed to get service from broker",
            "GoogleApiManager",
            "IGmsServiceBroker",
            "SecurityException",
            "Parcel.createException",
            "Parcel.readException",
            "Phenotype",
            "Unable to update local snapshot",
            "com.google.android.gms"
        )

        return nonFatalKeywords.any { fullTrace.contains(it, ignoreCase = true) }
    }

    private fun initializeMapboxSdk() {
        try {
            var token = try { getString(R.string.mapbox_access_token) } catch (_: Throwable) { "" }
            if (token.isBlank()) {
                token = MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN
            }
            if (token.isNotBlank()) {
                MapboxOptions.accessToken = token
                Log.d("DriverApplication", "Mapbox SDK initialized with public access token: ${token.take(12)}...")
            }
        } catch (t: Throwable) {
            Log.e("DriverApplication", "Failed to initialize Mapbox token", t)
        }
    }
}
