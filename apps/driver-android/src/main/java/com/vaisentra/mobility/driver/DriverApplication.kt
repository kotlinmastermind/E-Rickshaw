package com.vaisentra.mobility.driver

import android.app.Application
import com.vaisentra.mobility.driver.data.DriverNetworkClient

/**
 * DriverGo Application entry point.
 * Initializes base URLs, repositories, telemetry broadcasters and Supabase connection configs.
 */
class DriverApplication : Application() {

    companion object {
        lateinit var instance: DriverApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Configure default backend endpoint pointing to local FastAPI server
        // Android Emulator uses 10.0.2.2 for localhost:8000
        DriverNetworkClient.baseUrl = "https://e-rickshaw-z32z.onrender.com/"
    }
}

