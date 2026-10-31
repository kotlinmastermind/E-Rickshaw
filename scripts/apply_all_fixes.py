import re
import os

def normalize_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    return content.replace('\r\n', '\n')

def save_file(filepath, content):
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Updated: {filepath}")

# 1. CustomerAppNavigation.kt
nav_path = r'C:\androidAi\apps\customer-android\src\main\java\com\luxury\chauffeur\customer\CustomerAppNavigation.kt'
c = normalize_file(nav_path)
c = c.replace(
    'var activeRideId by remember { mutableStateOf("ride_lux_001") }',
    'var activeRideId by remember { mutableStateOf("") }'
)
save_file(nav_path, c)

# 2. CustomerAuthenticationDialog.kt
cust_auth_path = r'C:\androidAi\apps\customer-android\src\main\java\com\luxury\chauffeur\customer\feature\auth\CustomerAuthenticationDialog.kt'
c = normalize_file(cust_auth_path)
norm_fn = """    var isSubmitting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun normalizePhoneNumber(input: String): String {
        val clean = input.trim().replace(" ", "").replace("-", "")
        if (clean.isBlank()) return ""
        val digitsOnly = clean.filter { it.isDigit() }
        return when {
            clean.startsWith("+") -> clean
            digitsOnly.length == 10 -> "+91$digitsOnly"
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> "+$digitsOnly"
            digitsOnly.length > 10 && digitsOnly.startsWith("91") -> "+$digitsOnly"
            digitsOnly.length > 10 -> "+91${digitsOnly.take(10)}"
            else -> "+91$digitsOnly"
        }
    }"""
c = re.sub(
    r'var isSubmitting by remember \{ mutableStateOf\(false\) \}\s*var statusMessage by remember \{ mutableStateOf<String\?>\(null\) \}\s*var errorMessage by remember \{ mutableStateOf<String\?>\(null\) \}',
    norm_fn,
    c
)
# update button click in CustomerAuthenticationDialog
old_otp_req = """val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
                                            if (clean.length < 10) {
                                                errorMessage = "Enter a valid 10-digit mobile phone number first."
                                                return@Button
                                            }
                                            val formatted = if (clean.startsWith("+")) clean else if (clean.length == 10) "+91$clean" else clean"""
new_otp_req = """val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
                                            val formatted = normalizePhoneNumber(clean)
                                            if (formatted.length < 10) {
                                                errorMessage = "Enter a valid 10-digit mobile phone number first."
                                                return@Button
                                            }"""
c = c.replace(old_otp_req, new_otp_req)

old_sign_in = """val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
                                    if (clean.isBlank()) {
                                        errorMessage = "Please enter your mobile phone number."
                                        return@LuxuryButton
                                    }
                                    val formatted = if (clean.startsWith("+")) clean else if (clean.length == 10) "+91$clean" else clean"""
new_sign_in = """val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
                                    if (clean.isBlank()) {
                                        errorMessage = "Please enter your mobile phone number."
                                        return@LuxuryButton
                                    }
                                    val formatted = normalizePhoneNumber(clean)"""
c = c.replace(old_sign_in, new_sign_in)
save_file(cust_auth_path, c)

# 3. DriverAuthenticationDialog.kt
drv_auth_path = r'C:\androidAi\apps\driver-android\src\main\java\com\luxury\chauffeur\driver\feature\auth\DriverAuthenticationDialog.kt'
c = normalize_file(drv_auth_path)
c = re.sub(
    r'var isSubmitting by remember \{ mutableStateOf\(false\) \}\s*var statusMessage by remember \{ mutableStateOf<String\?>\(null\) \}\s*var errorMessage by remember \{ mutableStateOf<String\?>\(null\) \}',
    norm_fn,
    c
)
old_drv_otp = """val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
                                        if (clean.length < 10) {
                                            errorMessage = "Enter a valid 10-digit mobile phone number first."
                                            return@Button
                                        }
                                        val formatted = if (clean.startsWith("+")) clean else if (clean.length == 10) "+91$clean" else clean"""
new_drv_otp = """val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
                                        val formatted = normalizePhoneNumber(clean)
                                        if (formatted.length < 10) {
                                            errorMessage = "Enter a valid 10-digit mobile phone number first."
                                            return@Button
                                        }"""
c = c.replace(old_drv_otp, new_drv_otp)

old_drv_signin = """val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
                                if (clean.isBlank()) {
                                    errorMessage = "Please enter your mobile phone number."
                                    return@LuxuryDriverButton
                                }
                                val formatted = if (clean.startsWith("+")) clean else if (clean.length == 10) "+91$clean" else clean"""
new_drv_signin = """val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
                                if (clean.isBlank()) {
                                    errorMessage = "Please enter your mobile phone number."
                                    return@LuxuryDriverButton
                                }
                                val formatted = normalizePhoneNumber(clean)"""
c = c.replace(old_drv_signin, new_drv_signin)
save_file(drv_auth_path, c)

# 4. LocationScreen.kt
loc_path = r'C:\androidAi\apps\customer-android\src\main\java\com\luxury\chauffeur\customer\feature\location\LocationScreen.kt'
c = normalize_file(loc_path)
old_booking_block = """        scope.launch {
            try {
                val res = ApiClient.getService().requestAdvancedRide(payload)
                if (res.isSuccessful && res.body() != null) {
                    val b = res.body()!!
                    val rideId = b["ride_id"] as? String ?: "ride_adv_${System.currentTimeMillis().toString().takeLast(4)}"
                    val otp = b["start_otp"] as? String ?: "849201"
                    onRideRequested(rideId, otp)
                } else {
                    onRideRequested("ride_adv_${System.currentTimeMillis().toString().takeLast(4)}", "849201")
                }
            } catch (_: Exception) {
                onRideRequested("ride_adv_${System.currentTimeMillis().toString().takeLast(4)}", "849201")
            } finally {
                isBookingInProgress = false
            }
        }"""

new_booking_block = """        scope.launch {
            try {
                val res = ApiClient.getService().requestAdvancedRide(payload)
                if (res.isSuccessful && res.body() != null) {
                    val b = res.body()!!
                    val rideId = b["ride_id"] as? String
                    val otp = b["start_otp"] as? String ?: "849201"
                    if (!rideId.isNullOrBlank()) {
                        onRideRequested(rideId, otp)
                    } else {
                        val errMsg = (b["error"] ?: b["detail"] ?: "Unable to confirm ride reservation. Please retry.") as String
                        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                    }
                } else {
                    val rawErr = res.errorBody()?.string() ?: ""
                    val errMsg = if (rawErr.contains("detail")) {
                        try {
                            org.json.JSONObject(rawErr).optString("detail", rawErr)
                        } catch (_: Exception) { rawErr }
                    } else {
                        "Reservation failed (HTTP ${res.code()}). Please retry."
                    }
                    Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error: ${e.localizedMessage ?: "Unable to connect"}", Toast.LENGTH_LONG).show()
            } finally {
                isBookingInProgress = false
            }
        }"""
if old_booking_block in c:
    c = c.replace(old_booking_block, new_booking_block)
    save_file(loc_path, c)
else:
    print("WARNING: old_booking_block not matched in LocationScreen.kt")

# 5. LiveTrackingScreen.kt
trk_path = r'C:\androidAi\apps\customer-android\src\main\java\com\luxury\chauffeur\customer\feature\tracking\LiveTrackingScreen.kt'
c = normalize_file(trk_path)
# Add consecutive404Count state
c = c.replace(
    'var telemetryData by remember { mutableStateOf<AdvancedTelemetryResponse?>(null) }',
    'var telemetryData by remember { mutableStateOf<AdvancedTelemetryResponse?>(null) }\n    var consecutive404Count by remember { mutableIntStateOf(0) }'
)

# Update fetchAdvancedTelemetry
old_fetch = """    // Telemetry and HTTP Polling backup
    fun fetchAdvancedTelemetry() {
        scope.launch {
            try {
                val res = ApiClient.getService().getRideTelemetry(rideId)
                if (res.isSuccessful && res.body() != null) {
                    val tel = res.body()!!"""

new_fetch = """    // Telemetry and HTTP Polling backup
    fun fetchAdvancedTelemetry() {
        if (rideId.isBlank()) return
        scope.launch {
            try {
                val res = ApiClient.getService().getRideTelemetry(rideId)
                if (res.isSuccessful && res.body() != null) {
                    consecutive404Count = 0
                    val tel = res.body()!!"""
c = c.replace(old_fetch, new_fetch)

old_ride_res = """                // Also refresh main ride details
                val rideRes = ApiClient.getService().getRideDetails(rideId)
                if (rideRes.isSuccessful && rideRes.body() != null) {
                    rideDetails = rideRes.body()
                    if (rideRes.body()!!.status == "COMPLETED") {
                        val fare = (rideRes.body()!!.finalFareCents ?: rideRes.body()!!.estimatedFareCents) / 100
                        finalFareRupees = fare
                        onProceedPayment(rideId, fare)
                    }
                }
            } catch (_: Exception) {}"""

new_ride_res = """                // Also refresh main ride details
                val rideRes = ApiClient.getService().getRideDetails(rideId)
                if (rideRes.isSuccessful && rideRes.body() != null) {
                    consecutive404Count = 0
                    rideDetails = rideRes.body()
                    if (rideRes.body()!!.status == "COMPLETED") {
                        val fare = (rideRes.body()!!.finalFareCents ?: rideRes.body()!!.estimatedFareCents) / 100
                        finalFareRupees = fare
                        onProceedPayment(rideId, fare)
                    }
                } else if (rideRes.code() == 404 || res.code() == 404) {
                    consecutive404Count++
                    if (consecutive404Count >= 3) {
                        tripStatus = "CANCELLED"
                        Toast.makeText(context, "Ride session ended or not found on server.", Toast.LENGTH_LONG).show()
                        onRideCancelled()
                    }
                }
            } catch (_: Exception) {}"""
c = c.replace(old_ride_res, new_ride_res)

# Update polling LaunchedEffect
old_poll = """    // Polling cycle every 3 seconds
    LaunchedEffect(rideId) {
        while (tripStatus != "COMPLETED" && tripStatus != "CANCELLED") {
            fetchAdvancedTelemetry()
            delay(3000)
        }
    }"""
new_poll = """    // Polling cycle every 3 seconds
    LaunchedEffect(rideId) {
        if (rideId.isBlank()) {
            return@LaunchedEffect
        }
        while (tripStatus != "COMPLETED" && tripStatus != "CANCELLED") {
            fetchAdvancedTelemetry()
            delay(3000)
        }
    }"""
c = c.replace(old_poll, new_poll)
save_file(trk_path, c)
print("All client files updated successfully!")

