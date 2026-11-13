package com.vaisentra.mobility.rider

import android.app.Application
import android.util.Log
import com.vaisentra.mobility.rider.data.NetworkClient

class RiderApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("RiderGo", "RiderGo Application initialized with Material 3 & FastAPI network client.")
        
        // Dynamic backend base URL configuration (can point to local FastAPI or Cloud Render deployment)
        NetworkClient.baseUrl = "https://e-rickshaw-z32z.onrender.com/"
    }
}

