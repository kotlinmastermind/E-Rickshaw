package com.vaisentra.mobility.driver

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Looper
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

        if (isDebuggable()) {
            setupStrictMode()
        }

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

    private fun isDebuggable(): Boolean {
        return (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun setupStrictMode() {
        android.os.StrictMode.setThreadPolicy(
            android.os.StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        android.os.StrictMode.setVmPolicy(
            android.os.StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
        Log.i("DriverApplication", "StrictMode diagnostics enabled for debug build (penaltyLog).")
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
        val crashObserver = com.vaisentra.mobility.core.error.GlobalCrashHandler(this, "driver-android", defaultHandler)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isPlayServicesOrNativeMapException(thread, throwable)) {
                Log.w("DriverApplication", "Caught non-fatal background Map/Play Services exception on thread [${thread.name}]: ${throwable.message}")
                return@setDefaultUncaughtExceptionHandler
            }
            crashObserver.uncaughtException(thread, throwable)
        }
    }

    companion object {
        fun isPlayServicesOrNativeMapException(thread: Thread, throwable: Throwable?): Boolean {
            if (throwable == null) return false

            // NEVER swallow exceptions on the main UI thread
            val mainThread = try { Looper.getMainLooper()?.thread } catch (_: Throwable) { null }
            if (mainThread != null && thread == mainThread) {
                return false
            }

            // Inspect exception chain for typed fatal exceptions that must NEVER be swallowed
            var curr: Throwable? = throwable
            while (curr != null) {
                if (curr is SecurityException) return false
                if (curr is java.io.IOException) return false
                if (curr is NullPointerException || curr is IllegalArgumentException) return false

                val className = curr.javaClass.name
                if (className.contains("SecurityException", ignoreCase = true) ||
                    className.contains("Parcel", ignoreCase = true) ||
                    className.contains("RemoteException", ignoreCase = true)
                ) {
                    return false
                }
                curr = curr.cause
            }

            // Only swallow specific, known non-fatal background Google Play Services Dynamite module
            // loader failures or native Mapbox sqlite disk cache lock contention
            val fullTrace = buildString {
                append(thread.name).append(" ")
                append(throwable.javaClass.name).append(" ")
                append(throwable.message.orEmpty()).append(" ")
                append(throwable.stackTraceToString())
            }

            val isDynamiteModuleFailure = (fullTrace.contains("com.google.android.gms.dynamite", ignoreCase = true) ||
                    fullTrace.contains("policy_maps_core_dynamite", ignoreCase = true) ||
                    fullTrace.contains("maps_dynamite", ignoreCase = true)) &&
                    fullTrace.contains("No acceptable module", ignoreCase = true)

            val isNativeDiskCacheLock = fullTrace.contains("NativeSqliteDiskCacheImpl", ignoreCase = true) &&
                    fullTrace.contains("Database lock unavailable", ignoreCase = true)

            return isDynamiteModuleFailure || isNativeDiskCacheLock
        }
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
