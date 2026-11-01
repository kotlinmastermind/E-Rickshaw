package com.luxury.chauffeur.customer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.MapsInitializer
import com.luxury.chauffeur.customer.core.network.ApiClient
import com.luxury.chauffeur.customer.feature.payments.RazorpayCheckoutHelper
import com.luxury.chauffeur.customer.feature.payments.RazorpayPaymentBridge

class MainActivity : ComponentActivity(), com.razorpay.PaymentResultWithDataListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.initialize(this)

        try {
            RazorpayCheckoutHelper.initialize(this)
        } catch (_: Throwable) {}

        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            try {
                MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST) { renderer ->
                    Log.d("MapsInit", "Customer Maps initialized with renderer: $renderer")
                }
            } catch (t: Throwable) {
                Log.w("MapsInit", "LATEST maps renderer unavailable (${t.message}), falling back to LEGACY pre-Chimera renderer")
                try {
                    MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LEGACY) { renderer ->
                        Log.d("MapsInit", "Customer Maps initialized with LEGACY renderer: $renderer")
                    }
                } catch (_: Throwable) {
                    try {
                        MapsInitializer.initialize(applicationContext)
                    } catch (_: Throwable) {}
                }
            }
        }

        setContent {
            Surface(color = Color.White) {
                CustomerAppNavigation()
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: com.razorpay.PaymentData?) {
        RazorpayPaymentBridge.onPaymentSuccess(razorpayPaymentId, paymentData)
    }

    override fun onPaymentError(errorCode: Int, response: String?, paymentData: com.razorpay.PaymentData?) {
        RazorpayPaymentBridge.onPaymentError(errorCode, response, paymentData)
    }
}
