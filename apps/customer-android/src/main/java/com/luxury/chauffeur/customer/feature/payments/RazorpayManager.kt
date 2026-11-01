package com.luxury.chauffeur.customer.feature.payments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object RazorpayConfig {
    const val LIVE_KEY_ID = "rzp_live_TunGmzfpWlcatO"
    const val COMPANY_NAME = "Luxury Chauffeur Mobility"
    const val THEME_COLOR = "#D4AF37"
    const val DEFAULT_CURRENCY = "INR"
    const val DEFAULT_UPI_VPA = "luxurychauffeur@icici"
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
        var paymentId = razorpayPaymentId ?: ""
        var orderId = ""
        var signature = ""

        if (paymentData is com.razorpay.PaymentData) {
            if (paymentId.isBlank()) paymentId = paymentData.paymentId.orEmpty()
            orderId = paymentData.orderId.orEmpty()
            signature = paymentData.signature.orEmpty()
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
        forceUpiFirst: Boolean = true,
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
            options.put("send_sms_hash", true)

            // Prefill with customer info and prioritize UPI
            val prefill = JSONObject()
            if (!customerPhone.isNullOrBlank()) {
                prefill.put("contact", customerPhone)
            }
            if (!customerEmail.isNullOrBlank()) {
                prefill.put("email", customerEmail)
            }
            if (forceUpiFirst) {
                prefill.put("method", "upi")
            }
            options.put("prefill", prefill)

            // Styling Theme (Champagne Gold)
            val theme = JSONObject()
            theme.put("color", RazorpayConfig.THEME_COLOR)
            theme.put("hide_topbar", false)
            options.put("theme", theme)

            // Auto Retries
            val retryObj = JSONObject()
            retryObj.put("enabled", true)
            retryObj.put("max_count", 2)
            options.put("retry", retryObj)

            // Custom Display sequence that forces UPI to show at the top of Razorpay Checkout
            val config = JSONObject()
            val display = JSONObject()
            val blocks = JSONObject()

            // 1. UPI Block (Primary)
            val upiBlock = JSONObject()
            upiBlock.put("name", "Pay using UPI (GPay, PhonePe, Paytm, QR)")
            val upiInstruments = JSONArray()
            val upiInst = JSONObject()
            upiInst.put("method", "upi")
            upiInstruments.put(upiInst)
            upiBlock.put("instruments", upiInstruments)
            blocks.put("upi_block", upiBlock)

            // 2. Other Payment Methods (Cards, Netbanking, Wallets)
            val otherBlock = JSONObject()
            otherBlock.put("name", "Other Payment Modes (Cards, Netbanking, Wallets)")
            val otherInstruments = JSONArray()
            val cardInst = JSONObject()
            cardInst.put("method", "card")
            otherInstruments.put(cardInst)
            val nbInst = JSONObject()
            nbInst.put("method", "netbanking")
            otherInstruments.put(nbInst)
            val walletInst = JSONObject()
            walletInst.put("method", "wallet")
            otherInstruments.put(walletInst)
            otherBlock.put("instruments", otherInstruments)
            blocks.put("other_block", otherBlock)

            display.put("blocks", blocks)
            val sequence = JSONArray()
            sequence.put("block.upi_block")
            sequence.put("block.other_block")
            display.put("sequence", sequence)

            val preferences = JSONObject()
            preferences.put("show_default_blocks", true)
            display.put("preferences", preferences)

            config.put("display", display)
            options.put("config", config)
            options.put("display", display)

            Log.i(TAG, "Launching Razorpay Checkout with UPI Priority for Order: $orderId, ₹$amountRupees")
            checkout.open(activity, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Razorpay checkout: ${e.message}", e)
            callback.onPaymentError(-1, "Unable to launch Razorpay gateway: ${e.localizedMessage}")
        }
    }

    /**
     * Fallback Direct UPI Intent launcher to open Google Pay, PhonePe, Paytm directly
     */
    fun launchDirectUpiIntent(
        context: Context,
        amountRupees: Double,
        rideId: String,
        payeeVpa: String = RazorpayConfig.DEFAULT_UPI_VPA,
        payeeName: String = RazorpayConfig.COMPANY_NAME
    ): Boolean {
        return try {
            val formattedAmount = String.format(java.util.Locale.US, "%.2f", amountRupees)
            val uri = Uri.parse("upi://pay?pa=$payeeVpa&pn=${Uri.encode(payeeName)}&mc=5511&tr=$rideId&tn=RidePayment-${rideId.take(8)}&am=$formattedAmount&cu=INR")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            val chooser = Intent.createChooser(intent, "Pay via UPI App (Google Pay / PhonePe / Paytm)")
            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Direct UPI Intent launch failed: ${e.message}")
            false
        }
    }
}
