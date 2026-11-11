package com.vaisentra.mobility.rider.feature.payments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object RazorpayConfig {
    val LIVE_KEY_ID: String get() = com.vaisentra.mobility.rider.BuildConfig.RAZORPAY_KEY_ID
    const val COMPANY_NAME = "VAISENTRA MOBILITY"
    const val THEME_COLOR = "#0D9488"
    const val DEFAULT_CURRENCY = "INR"
    const val DEFAULT_UPI_VPA = "vaisentramobility@icici"
}

interface RazorpayPaymentCallback {
    fun onPaymentSuccess(paymentId: String, orderId: String, signature: String)
    fun onPaymentError(code: Int, description: String)
}

object RazorpayPaymentBridge {
    private const val TAG = "RazorpayBridge"
    @Volatile
    private var callback: RazorpayPaymentCallback? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @Synchronized
    fun setCallback(cb: RazorpayPaymentCallback?) {
        this.callback = cb
    }

    @Synchronized
    fun clearCallback() {
        this.callback = null
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
        val cb = callback
        callback = null
        mainHandler.post {
            cb?.onPaymentSuccess(paymentId, orderId, signature)
        }
    }

    fun onPaymentError(code: Int, response: String?, paymentData: Any?) {
        val errorMsg = response ?: "Payment cancelled or failed (Code $code)"
        Log.w(TAG, "Payment Error ($code): $errorMsg")
        val cb = callback
        callback = null
        mainHandler.post {
            cb?.onPaymentError(code, errorMsg)
        }
    }
}

data class UpiAppInfo(
    val id: String,
    val name: String,
    val packageName: String,
    val isInstalled: Boolean
)

object RazorpayCheckoutHelper {
    private const val TAG = "RazorpayCheckout"

    const val PACKAGE_GPAY = "com.google.android.apps.nbu.paisa.user"
    const val PACKAGE_PHONEPE = "com.phonepe.app"
    const val PACKAGE_PAYTM = "net.one97.paytm"
    const val PACKAGE_BHIM = "in.org.npci.upiapp"
    const val PACKAGE_CRED = "com.dreamplug.androidapp"

    fun initialize(activity: Activity) {
        try {
            com.razorpay.Checkout.preload(activity.applicationContext)
            Log.d(TAG, "Razorpay Checkout preloaded successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Razorpay preload warning: ${e.message}")
        }
    }

    /**
     * Start Razorpay Checkout with reliable UPI priority.
     * Cleanly configured so Razorpay's API correctly reveals UPI (GPay, PhonePe, Paytm, QR, UPI ID)
     * alongside Cards, Netbanking, Wallets and Pay Later.
     */
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

        val activeKeyId = keyId.ifBlank { RazorpayConfig.LIVE_KEY_ID }.trim()
        if (activeKeyId.isBlank()) {
            Log.e(TAG, "Cannot launch Razorpay payment: Merchant Key ID is not configured.")
            callback.onPaymentError(-1, "Payment gateway configuration error. Please contact support.")
            return
        }

        try {
            val checkout = com.razorpay.Checkout()
            checkout.setKeyID(activeKeyId)

            val options = JSONObject()
            options.put("name", RazorpayConfig.COMPANY_NAME)
            options.put("description", "VAISENTRA MOBILITY Ride Payment #${rideId.take(10)}")
            options.put("currency", RazorpayConfig.DEFAULT_CURRENCY)
            val amountPaise = (amountRupees * 100).toLong()
            options.put("amount", amountPaise)

            // Only attach order_id if it's a real order created on Razorpay's servers
            if (orderId.isNotBlank() && orderId.startsWith("order_") &&
                !orderId.startsWith("order_live_") && !orderId.startsWith("order_sim_") && !orderId.startsWith("rcpt_")
            ) {
                options.put("order_id", orderId)
            }
            options.put("send_sms_hash", true)

            // Customer Prefill
            val prefill = JSONObject()
            if (!customerPhone.isNullOrBlank()) {
                prefill.put("contact", customerPhone)
            }
            if (!customerEmail.isNullOrBlank()) {
                prefill.put("email", customerEmail)
            }
            options.put("prefill", prefill)

            // Styling Theme (Luxury Gold)
            val theme = JSONObject()
            theme.put("color", RazorpayConfig.THEME_COLOR)
            theme.put("hide_topbar", false)
            options.put("theme", theme)

            // Auto Retries
            val retryObj = JSONObject()
            retryObj.put("enabled", true)
            retryObj.put("max_count", 3)
            options.put("retry", retryObj)

            // Display & Block Configuration: Enforces UPI Block (GPay, PhonePe, Paytm, QR, UPI ID) as #1
            val configObj = JSONObject()
            val displayObj = JSONObject()
            val blocksObj = JSONObject()

            // 1. Preferred UPI Block
            val upiBlock = JSONObject().apply {
                put("name", "Pay via UPI (GPay, PhonePe, Paytm, QR, UPI ID)")
                val instrumentsArray = JSONArray()
                val upiInstrument = JSONObject().apply {
                    put("method", "upi")
                    val flowsArray = JSONArray().apply {
                        put("intent")
                        put("qr")
                        put("collect")
                    }
                    put("flows", flowsArray)
                }
                instrumentsArray.put(upiInstrument)
                put("instruments", instrumentsArray)
            }
            blocksObj.put("preferred_upi", upiBlock)

            // 2. All Other Payment Options Block
            val otherBlock = JSONObject().apply {
                put("name", "Other Payment Options (Cards, Netbanking, Wallets)")
                val otherInstruments = JSONArray().apply {
                    put(JSONObject().put("method", "card"))
                    put(JSONObject().put("method", "netbanking"))
                    put(JSONObject().put("method", "wallet"))
                    put(JSONObject().put("method", "emi"))
                    put(JSONObject().put("method", "paylater"))
                }
                put("instruments", otherInstruments)
            }
            blocksObj.put("other_methods", otherBlock)

            displayObj.put("blocks", blocksObj)

            // Sequence: UPI block strictly placed FIRST
            val sequenceArray = JSONArray().apply {
                put("block.preferred_upi")
                put("block.other_methods")
            }
            displayObj.put("sequence", sequenceArray)

            val preferencesObj = JSONObject().apply {
                put("show_default_blocks", true)
            }
            displayObj.put("preferences", preferencesObj)

            configObj.put("display", displayObj)
            options.put("config", configObj)

            // Also directly attach display for SDK variants that read options.display
            options.put("display", displayObj)

            Log.i(TAG, "Opening Razorpay Checkout for ₹$amountRupees (orderId: $orderId, UPI first: $forceUpiFirst)")
            checkout.open(activity, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Razorpay checkout: ${e.message}", e)
            callback.onPaymentError(-1, "Unable to launch Razorpay gateway: ${e.localizedMessage}")
        }
    }

    /**
     * Check if a specific UPI package is installed on the user's Android device
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Get list of popular UPI apps and whether they are installed
     */
    fun getPopularUpiApps(context: Context): List<UpiAppInfo> {
        return listOf(
            UpiAppInfo("gpay", "Google Pay", PACKAGE_GPAY, isAppInstalled(context, PACKAGE_GPAY)),
            UpiAppInfo("phonepe", "PhonePe", PACKAGE_PHONEPE, isAppInstalled(context, PACKAGE_PHONEPE)),
            UpiAppInfo("paytm", "Paytm UPI", PACKAGE_PAYTM, isAppInstalled(context, PACKAGE_PAYTM)),
            UpiAppInfo("bhim", "BHIM UPI", PACKAGE_BHIM, isAppInstalled(context, PACKAGE_BHIM)),
            UpiAppInfo("cred", "CRED UPI", PACKAGE_CRED, isAppInstalled(context, PACKAGE_CRED))
        )
    }

    /**
     * Build the standard NPCI compliant UPI Intent URI
     */
    fun buildUpiUri(
        amountRupees: Double,
        rideId: String,
        payeeVpa: String = RazorpayConfig.DEFAULT_UPI_VPA,
        payeeName: String = RazorpayConfig.COMPANY_NAME,
        transactionNote: String = "Luxury Ride Settlement"
    ): Uri {
        val formattedAmount = String.format(java.util.Locale.US, "%.2f", amountRupees)
        val cleanRideId = rideId.filter { it.isLetterOrDigit() }.take(16).ifBlank { "RIDE${System.currentTimeMillis()}" }
        val encodedName = Uri.encode(payeeName)
        val encodedNote = Uri.encode("$transactionNote $cleanRideId")
        return Uri.parse(
            "upi://pay?pa=$payeeVpa&pn=$encodedName&mc=4121&tr=$cleanRideId&tn=$encodedNote&am=$formattedAmount&cu=INR"
        )
    }

    /**
     * Fallback Direct UPI Intent launcher to open Google Pay, PhonePe, Paytm directly via Android system chooser
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
            val uri = buildUpiUri(amountRupees, rideId, payeeVpa, payeeName)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            val chooser = Intent.createChooser(intent, "Pay ₹$formattedAmount via UPI (GPay / PhonePe / Paytm / BHIM)")
            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Direct UPI Intent launch failed: ${e.message}")
            false
        }
    }

    /**
     * Launch a specific UPI App directly by package name (e.g. GPay, PhonePe, Paytm, BHIM)
     */
    fun launchSpecificUpiApp(
        context: Context,
        packageName: String,
        amountRupees: Double,
        rideId: String,
        payeeVpa: String = RazorpayConfig.DEFAULT_UPI_VPA,
        payeeName: String = RazorpayConfig.COMPANY_NAME
    ): Boolean {
        return try {
            val uri = buildUpiUri(amountRupees, rideId, payeeVpa, payeeName)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Targeted launch for $packageName failed, falling back to general chooser: ${e.message}")
            launchDirectUpiIntent(context, amountRupees, rideId, payeeVpa, payeeName)
        }
    }
}
