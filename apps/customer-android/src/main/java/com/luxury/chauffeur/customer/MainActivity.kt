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
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.initialize(this)

        // Preload Razorpay Checkout for instantaneous payment sheet display
        RazorpayCheckoutHelper.initialize(this)

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

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        RazorpayPaymentBridge.onPaymentSuccess(razorpayPaymentId, paymentData)
    }

    override fun onPaymentError(errorCode: Int, response: String?, paymentData: PaymentData?) {
        RazorpayPaymentBridge.onPaymentError(errorCode, response, paymentData)
    }
}
