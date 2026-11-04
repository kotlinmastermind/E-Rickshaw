package com.luxury.chauffeur.customer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.luxury.chauffeur.customer.core.network.ApiClient
import com.luxury.chauffeur.customer.feature.payments.RazorpayCheckoutHelper
import com.luxury.chauffeur.customer.feature.payments.RazorpayPaymentBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), com.razorpay.PaymentResultWithDataListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Render Compose UI immediately to satisfy Android OS startup watchdog in <50ms
        setContent {
            Surface(color = Color(0xFF0F172A)) {
                CustomerAppNavigation()
            }
        }

        // Post-launch initialization: API client & payment SDK initialize after UI frame is drawn
        window.decorView.post {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ApiClient.initialize(this@MainActivity)
                } catch (_: Throwable) {}
                try {
                    RazorpayCheckoutHelper.initialize(this@MainActivity)
                } catch (_: Throwable) {}
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
