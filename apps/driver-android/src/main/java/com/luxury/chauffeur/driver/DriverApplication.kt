package com.luxury.chauffeur.driver

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.mapbox.common.MapboxOptions
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DriverApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        setupUncaughtExceptionHandler()

        // Asynchronously warm up Mapbox and API client in background IO pool
        // This ensures Application.onCreate() finishes in <5ms, preventing any ANR
        CoroutineScope(Dispatchers.IO).launch {
            initializeMapboxSdk()
            try {
                DriverApiClient.initialize(this@DriverApplication)
            } catch (_: Throwable) {}
        }

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
            "com.google.android.gms.maps",
            "com.google.android.gms"
        )

        return nonFatalKeywords.any { fullTrace.contains(it, ignoreCase = true) }
    }

    private fun initializeMapboxSdk() {
        try {
            val token = getString(R.string.mapbox_access_token)
            MapboxOptions.accessToken = token
            Log.d("DriverApplication", "Mapbox SDK initialized with public access token")
        } catch (t: Throwable) {
            Log.e("DriverApplication", "Failed to initialize Mapbox token", t)
        }
    }
}
