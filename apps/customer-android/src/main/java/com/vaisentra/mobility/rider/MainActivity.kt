package com.vaisentra.mobility.rider

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.vaisentra.mobility.rider.data.NetworkClient
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.fcm.RiderFcmManager
import com.vaisentra.mobility.rider.navigation.RiderAppNavigation
import com.vaisentra.mobility.rider.theme.CanvasBlack
import com.vaisentra.mobility.rider.theme.RiderGoTheme

class MainActivity : FragmentActivity(), PaymentResultWithDataListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NetworkClient.init(applicationContext)
        RiderRepository.restoreCheckoutStates()
        RiderFcmManager.init(applicationContext)
        RiderFcmManager.handleNotificationIntent(intent)
        Checkout.preload(applicationContext)

        setContent {
            RiderGoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CanvasBlack
                ) {
                    RiderAppNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        RiderFcmManager.handleNotificationIntent(intent)
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        RiderRepository.handleRazorpaySuccess(
            orderId = paymentData?.orderId ?: "",
            paymentId = paymentData?.paymentId ?: razorpayPaymentId ?: "",
            signature = paymentData?.signature ?: ""
        )
    }

    override fun onPaymentError(errorCode: Int, errorDescription: String?, paymentData: PaymentData?) {
        RiderRepository.handleRazorpayFailure(
            code = errorCode,
            description = errorDescription ?: "Payment cancelled or failed",
            orderId = paymentData?.orderId
        )
    }
}