package com.luxury.chauffeur.customer.feature.payments

import android.app.Activity
import android.util.Log
import org.json.JSONObject

object RazorpayConfig {
    const val LIVE_KEY_ID = "rzp_live_TunGmzfpWlcatO"
    const val COMPANY_NAME = "Luxury Chauffeur Mobility"
    const val THEME_COLOR = "#D4AF37"
    const val DEFAULT_CURRENCY = "INR"
}

interface RazorpayPaymentCallback {
    fun onPaymentSuccess(paymentId: String, orderId: String, signature: String)
    fun onPaymentError(code: Int, description: String)
}

object RazorpayPaymentBridge {
    private const val TAG = "RazorpayBridge"
    private var callback: RazorpayPaymentCallback? = null

    fun setCallback(cb: RazorpayPaymentCallback?) {
        this.callback = cb
    }

    fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: Any?) {
        var paymentId = razorpayPaymentId.orEmpty()
        var orderId = ""
        var signature = ""

        if (paymentData != null) {
            try {
                if (paymentData is com.razorpay.PaymentData) {
                    paymentId = razorpayPaymentId ?: paymentData.paymentId.orEmpty()
                    orderId = paymentData.orderId.orEmpty()
                    signature = paymentData.signature.orEmpty()
                } else {
                    val pId = paymentData.javaClass.getMethod("getPaymentId").invoke(paymentData)?.toString()
                    val oId = paymentData.javaClass.getMethod("getOrderId").invoke(paymentData)?.toString()
                    val sig = paymentData.javaClass.getMethod("getSignature").invoke(paymentData)?.toString()
                    if (paymentId.isBlank()) paymentId = pId.orEmpty()
                    orderId = oId.orEmpty()
                    signature = sig.orEmpty()
                }
            } catch (_: Exception) {}
        }
        Log.i(TAG, "Payment Success: payId=$paymentId, orderId=$orderId, signature=$signature")
        callback?.onPaymentSuccess(paymentId, orderId, signature)
    }

    fun onPaymentError(code: Int, response: String?, paymentData: Any?) {
        val errorMsg = response ?: "Payment cancelled or failed (Code $code)"
        Log.w(TAG, "Payment Error ($code): $errorMsg")
        callback?.onPaymentError(code, errorMsg)
    }
}

object RazorpayCheckoutHelper {
    private const val TAG = "RazorpayCheckout"

    fun initialize(activity: Activity) {
        try {
            com.razorpay.Checkout.preload(activity.applicationContext)
            Log.d(TAG, "Razorpay Checkout preloaded successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Razorpay preload warning: ${e.message}")
        }
    }

    fun startPayment(
        activity: Activity,
        orderId: String,
        amountRupees: Double,
        rideId: String,
        customerPhone: String? = null,
        customerEmail: String? = null,
        keyId: String = RazorpayConfig.LIVE_KEY_ID,
        callback: RazorpayPaymentCallback
    ) {
        RazorpayPaymentBridge.setCallback(callback)

        try {
            val checkout = com.razorpay.Checkout()
            checkout.setKeyID(keyId)

            val options = JSONObject()
            options.put("name", RazorpayConfig.COMPANY_NAME)
            options.put("description", "Luxury Chauffeur Ride Payment #${rideId.take(10)}")
            options.put("currency", RazorpayConfig.DEFAULT_CURRENCY)
            val amountPaise = (amountRupees * 100).toLong()
            options.put("amount", amountPaise)
            options.put("order_id", orderId)

            val prefill = JSONObject()
            if (!customerPhone.isNullOrBlank()) {
                prefill.put("contact", customerPhone)
            }
            if (!customerEmail.isNullOrBlank()) {
                prefill.put("email", customerEmail)
            }
            options.put("prefill", prefill)

            val theme = JSONObject()
            theme.put("color", RazorpayConfig.THEME_COLOR)
            options.put("theme", theme)

            val retryObj = JSONObject()
            retryObj.put("enabled", true)
            retryObj.put("max_count", 2)
            options.put("retry", retryObj)

            Log.i(TAG, "Launching Razorpay Checkout for Order: $orderId, ₹$amountRupees")
            checkout.open(activity, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Razorpay checkout: ${e.message}", e)
            callback.onPaymentError(-1, "Unable to launch Razorpay gateway: ${e.localizedMessage}")
        }
    }
}
