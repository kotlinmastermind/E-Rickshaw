package com.luxury.chauffeur.driver

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.google.android.gms.maps.MapsInitializer
import com.luxury.chauffeur.driver.core.network.DriverApiClient

class DriverApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            DriverApiClient.initialize(this)
        } catch (_: Throwable) {}

        setupUncaughtExceptionHandler()

        if (isMainProcess()) {
            java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                initializeMapsSdk()
            }
        } else {
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

    private fun initializeMapsSdk() {
        try {
            MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST) { renderer ->
                Log.d("DriverApplication", "Google Maps initialized with renderer: $renderer")
            }
        } catch (_: Throwable) {
            try {
                MapsInitializer.initialize(applicationContext)
            } catch (t: Throwable) {
                Log.e("DriverApplication", "Failed to initialize Google Maps SDK", t)
            }
        }
    }
}
