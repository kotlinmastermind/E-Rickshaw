package com.luxury.chauffeur.driver.feature.auth

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxury.chauffeur.driver.core.designsystem.*
import com.luxury.chauffeur.driver.core.network.*
import com.luxury.chauffeur.driver.core.security.DriverTokenManager
import kotlinx.coroutines.launch

@Composable
fun DriverAuthenticationDialog(
    onDismiss: () -> Unit,
    onAuthenticationSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var authTab by remember { mutableStateOf("SIGNIN") } // SIGNIN, SIGNUP, FORGOT_PASSWORD
    var loginMode by remember { mutableStateOf("OTP") } // PASSWORD, OTP

    // Driver Form States
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }

    // Vehicle Form States
    var vehicleType by remember { mutableStateOf("E_RICKSHAW_STANDARD") } // E_RICKSHAW_STANDARD, E_RICKSHAW_SHARE, E_AUTO, E_CARGO_LOADER
    var vehicleName by remember { mutableStateOf("") }
    var vehicleNo by remember { mutableStateOf("") }

    // Forgot Password States
    var forgotIdentifier by remember { mutableStateOf("") }
    var forgotOtpCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

        var isSubmitting by remember { mutableStateOf(false) }
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
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = DriverColors.MidnightBlack,
            border = BorderStroke(1.5.dp, DriverColors.ChauffeurGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CHAUFFEUR COCKPIT AUTHENTICATION",
                            style = DriverTypography.Title.copy(
                                color = DriverColors.ChauffeurGold,
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = "Chauffeur & Vehicle Registration Portal",
                            style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 11.sp)
                        )
                    }
                    Text(
                        text = "✕",
                        style = DriverTypography.Subtitle.copy(fontSize = 18.sp),
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("SIGNIN" to "Sign In", "SIGNUP" to "Driver & Vehicle Sign Up", "FORGOT_PASSWORD" to "Forgot Pass").forEach { (key, label) ->
                        val isSelected = authTab == key
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) DriverColors.ChauffeurGold else DriverColors.SurfaceCard,
                            border = BorderStroke(1.dp, if (isSelected) DriverColors.ChauffeurGold else DriverColors.SurfaceBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    authTab = key
                                    errorMessage = null
                                    statusMessage = null
                                }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) DriverColors.MidnightBlack else DriverColors.TextPure,
                                style = DriverTypography.Subtitle.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (errorMessage != null) {
                    Surface(
                        color = DriverColors.AlertDanger.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DriverColors.AlertDanger),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = DriverColors.AlertDanger,
                            style = DriverTypography.Body.copy(fontSize = 11.sp),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (statusMessage != null) {
                    Surface(
                        color = DriverColors.AlertSuccess.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DriverColors.AlertSuccess),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = statusMessage!!,
                            color = DriverColors.AlertSuccess,
                            style = DriverTypography.Body.copy(fontSize = 11.sp),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                when (authTab) {
                    "SIGNIN" -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DriverColors.SurfaceCard,
                            border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("SIGN IN TO CHAUFFEUR COCKPIT", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
                                Spacer(modifier = Modifier.height(8.dp))

                                // Login Mode Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("PASSWORD" to "Password Login", "OTP" to "OTP Login").forEach { (mKey, mLabel) ->
                                        val isSelected = loginMode == mKey
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) DriverColors.ChauffeurGold.copy(alpha = 0.2f) else DriverColors.CockpitDark,
                                            border = BorderStroke(1.dp, if (isSelected) DriverColors.ChauffeurGold else DriverColors.SurfaceBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { loginMode = mKey }
                                        ) {
                                            Text(
                                                text = mLabel,
                                                style = DriverTypography.Body.copy(
                                                    color = if (isSelected) DriverColors.ChauffeurGold else DriverColors.TextPure,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = phoneNumber,
                                    onValueChange = { phoneNumber = it },
                                    label = { Text("Username, Email ID or Mobile Phone", color = DriverColors.TextMuted) },
                                    textStyle = DriverTypography.Body,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DriverColors.ChauffeurGold,
                                        unfocusedBorderColor = DriverColors.SurfaceBorder,
                                        focusedTextColor = DriverColors.TextPure,
                                        unfocusedTextColor = DriverColors.TextPure
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                if (loginMode == "PASSWORD") {
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text("Password", color = DriverColors.TextMuted) },
                                        visualTransformation = PasswordVisualTransformation(),
                                        textStyle = DriverTypography.Body,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = DriverColors.ChauffeurGold,
                                            unfocusedBorderColor = DriverColors.SurfaceBorder,
                                            focusedTextColor = DriverColors.TextPure,
                                            unfocusedTextColor = DriverColors.TextPure
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = otpCode,
                                            onValueChange = { otpCode = it },
                                            label = { Text("6-Digit OTP Code", color = DriverColors.TextMuted) },
                                            textStyle = DriverTypography.Body,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = DriverColors.ChauffeurGold,
                                                unfocusedBorderColor = DriverColors.SurfaceBorder,
                                                focusedTextColor = DriverColors.TextPure,
                                                unfocusedTextColor = DriverColors.TextPure
                                            ),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )

                                        Button(
                                            onClick = {
                                                val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
                                                if (clean.length < 10) {
                                                    errorMessage = "Enter valid 10-digit mobile phone number first."
                                                    return@Button
                                                }
                                                val formatted = if (clean.startsWith("+")) clean else if (clean.length == 10) "+91$clean" else clean
                                                errorMessage = null
                                                statusMessage = "Requesting OTP..."
                                                scope.launch {
                                                    try {
                                                        val res = DriverApiClient.getService().requestOtp(DriverOtpRequestPayload(formatted))
                                                        if (res.isSuccessful && res.body() != null) {
                                                            val body = res.body()!!
                                                            val code = (body["debug_otp"] ?: body["otp_code"] ?: body["otp"]) as? String
                                                            if (!code.isNullOrBlank()) {
                                                                otpCode = code
                                                                statusMessage = "OTP Code: $code"
                                                            } else {
                                                                statusMessage = "OTP dispatched to $formatted!"
                                                            }
                                                        } else {
                                                            val err = res.errorBody()?.string() ?: ""
                                                            errorMessage = if (err.contains("detail")) err else "Failed to send OTP (HTTP " + res.code() + ")"
                                                        }
                                                    } catch (e: Exception) {
                                                        errorMessage = "OTP Error: ${e.localizedMessage}"
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = DriverColors.ChauffeurGold,
                                                contentColor = DriverColors.MidnightBlack
                                            ),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Send OTP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
                                        if (clean.isBlank()) {
                                            errorMessage = "Enter username, email or phone number."
                                            return@Button
                                        }
                                        val formatted = if (clean.startsWith("+")) clean else if (clean.length == 10) "+91$clean" else clean
                                        isSubmitting = true
                                        errorMessage = null
                                        scope.launch {
                                            try {
                                                if (loginMode == "OTP") {
                                                    if (otpCode.trim().length < 4) {
                                                        errorMessage = "Please enter valid 6-digit OTP."
                                                        isSubmitting = false
                                                        return@launch
                                                    }
                                                    val res = DriverApiClient.getService().verifyOtp(
                                                        DriverOtpVerifyPayload(
                                                            phoneNumber = formatted,
                                                            otpCode = otpCode.trim(),
                                                            role = "driver"
                                                        )
                                                    )
                                                    if (res.isSuccessful && res.body() != null) {
                                                        val tokenResp = res.body()!!
                                                        DriverTokenManager.getInstance(context).saveAuth(
                                                            token = tokenResp.accessToken,
                                                            userId = tokenResp.user.id,
                                                            phone = tokenResp.user.phoneNumber,
                                                            role = tokenResp.user.role
                                                        )
                                                        Toast.makeText(context, "Welcome Chauffeur!", Toast.LENGTH_SHORT).show()
                                                        onAuthenticationSuccess(tokenResp.accessToken)
                                                        onDismiss()
                                                    } else {
                                                        val err = res.errorBody()?.string() ?: ""
                                                        errorMessage = if (err.contains("detail")) err else "Login Failed: Invalid credentials or expired OTP."
                                                    }
                                                } else {
                                                    val res = DriverApiClient.getService().unifiedLogin(
                                                        DriverUnifiedLoginRequestPayload(
                                                            loginType = loginMode,
                                                            identifier = formatted,
                                                            password = password,
                                                            otpCode = null
                                                        )
                                                    )
                                                    if (res.isSuccessful && res.body() != null) {
                                                        val token = res.body()!!["access_token"] as? String ?: ""
                                                        Toast.makeText(context, "Welcome Chauffeur!", Toast.LENGTH_SHORT).show()
                                                        onAuthenticationSuccess(token)
                                                        onDismiss()
                                                    } else {
                                                        errorMessage = "Login Failed: Invalid credentials or account not found."
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "Login Error: ${e.localizedMessage}"
                                            } finally {
                                                isSubmitting = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DriverColors.ChauffeurGold,
                                        contentColor = DriverColors.MidnightBlack
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isSubmitting) "Authenticating..." else "Sign In to Cockpit", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    "SIGNUP" -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DriverColors.SurfaceCard,
                            border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("REGISTER CHAUFFEUR & VEHICLE", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
                                Spacer(modifier = Modifier.height(8.dp))

                                Text("1. CHAUFFEUR PERSONAL DETAILS", style = DriverTypography.Subtitle.copy(color = DriverColors.ChauffeurGold, fontSize = 11.sp))
                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = fullName,
                                    onValueChange = { fullName = it },
                                    label = { Text("Chauffeur Full Name", color = DriverColors.TextMuted) },
                                    textStyle = DriverTypography.Body,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DriverColors.ChauffeurGold,
                                        unfocusedBorderColor = DriverColors.SurfaceBorder,
                                        focusedTextColor = DriverColors.TextPure,
                                        unfocusedTextColor = DriverColors.TextPure
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = phoneNumber,
                                    onValueChange = { phoneNumber = it },
                                    label = { Text("Mobile Phone Number (+91...)", color = DriverColors.TextMuted) },
                                    textStyle = DriverTypography.Body,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DriverColors.ChauffeurGold,
                                        unfocusedBorderColor = DriverColors.SurfaceBorder,
                                        focusedTextColor = DriverColors.TextPure,
                                        unfocusedTextColor = DriverColors.TextPure
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Email Address", color = DriverColors.TextMuted) },
                                    textStyle = DriverTypography.Body,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DriverColors.ChauffeurGold,
                                        unfocusedBorderColor = DriverColors.SurfaceBorder,
                                        focusedTextColor = DriverColors.TextPure,
                                        unfocusedTextColor = DriverColors.TextPure
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Unique Username", color = DriverColors.TextMuted) },
                                    textStyle = DriverTypography.Body,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DriverColors.ChauffeurGold,
                                        unfocusedBorderColor = DriverColors.SurfaceBorder,
                                        focusedTextColor = DriverColors.TextPure,
                                        unfocusedTextColor = DriverColors.TextPure
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password (min 6 chars)", color = DriverColors.TextMuted) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    textStyle = DriverTypography.Body,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DriverColors.ChauffeurGold,
                                        unfocusedBorderColor = DriverColors.SurfaceBorder,
                                        focusedTextColor = DriverColors.TextPure,
                                        unfocusedTextColor = DriverColors.TextPure
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text("2. E-RICKSHAW VEHICLE REGISTRATION", style = DriverTypography.Subtitle.copy(color = DriverColors.ChauffeurGold, fontSize = 11.sp))
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("E_RICKSHAW_STANDARD" to "Standard", "E_RICKSHAW_SHARE" to "Shared", "E_AUTO" to "E-Auto", "E_CARGO_LOADER" to "Cargo").forEach { (typeKey, typeLabel) ->
                                        val isSelected = vehicleType == typeKey
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) DriverColors.ChauffeurGold else DriverColors.CockpitDark,
                                            border = BorderStroke(1.dp, if (isSelected) DriverColors.ChauffeurGold else DriverColors.SurfaceBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { vehicleType = typeKey }
                                        ) {
                                            Text(
                                                text = typeLabel,
                                                style = DriverTypography.Body.copy(
                                                    color = if (isSelected) DriverColors.MidnightBlack else DriverColors.TextPure,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                OutlinedTextField(
                                    value = vehicleName,
                                    onValueChange = { vehicleName = it },
                                    label = { Text("Vehicle Make & Model (e.g. Mercedes E-Class)", color = DriverColors.TextMuted) },
                                    textStyle = DriverTypography.Body,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DriverColors.ChauffeurGold,
                                        unfocusedBorderColor = DriverColors.SurfaceBorder,
                                        focusedTextColor = DriverColors.TextPure,
                                        unfocusedTextColor = DriverColors.TextPure
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = vehicleNo,
                                    onValueChange = { vehicleNo = it },
                                    label = { Text("Vehicle License Plate No. (e.g. MH01-LX-0001)", color = DriverColors.TextMuted) },
                                    textStyle = DriverTypography.Body,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DriverColors.ChauffeurGold,
                                        unfocusedBorderColor = DriverColors.SurfaceBorder,
                                        focusedTextColor = DriverColors.TextPure,
                                        unfocusedTextColor = DriverColors.TextPure
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text("3. OTP VERIFICATION", style = DriverTypography.Subtitle.copy(color = DriverColors.ChauffeurGold, fontSize = 11.sp))
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = otpCode,
                                        onValueChange = { otpCode = it },
                                        label = { Text("6-Digit OTP Code", color = DriverColors.TextMuted) },
                                        textStyle = DriverTypography.Body,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = DriverColors.ChauffeurGold,
                                            unfocusedBorderColor = DriverColors.SurfaceBorder,
                                            focusedTextColor = DriverColors.TextPure,
                                            unfocusedTextColor = DriverColors.TextPure
                                        ),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )

                                    Button(
                                        onClick = {
                                            if (phoneNumber.length < 10) {
                                                errorMessage = "Enter valid 10-digit phone number first."
                                                return@Button
                                            }
                                            scope.launch {
                                                try {
                                                    val res = DriverApiClient.getService().requestOtp(DriverOtpRequestPayload(phoneNumber))
                                                    if (res.isSuccessful && res.body() != null) {
                                                        val body = res.body()!!
                                                        val code = (body["debug_otp"] ?: body["otp_code"]) as? String
                                                        if (!code.isNullOrBlank()) {
                                                            otpCode = code
                                                            statusMessage = "OTP sent to $phoneNumber! Code: $code"
                                                        } else {
                                                            statusMessage = "OTP sent to $phoneNumber!"
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = "OTP Error: ${e.localizedMessage}"
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DriverColors.ChauffeurGold,
                                            contentColor = DriverColors.MidnightBlack
                                        ),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Send OTP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (fullName.isBlank() || phoneNumber.isBlank() || email.isBlank() || username.isBlank() || password.isBlank() || vehicleName.isBlank() || vehicleNo.isBlank() || otpCode.isBlank()) {
                                            errorMessage = "All chauffeur details, vehicle specs & OTP code are required."
                                            return@Button
                                        }
                                        isSubmitting = true
                                        errorMessage = null
                                        scope.launch {
                                            try {
                                                val res = DriverApiClient.getService().signupDriver(
                                                    DriverSignupRequestPayload(
                                                        phoneNumber = phoneNumber,
                                                        email = email,
                                                        fullName = fullName,
                                                        password = password,
                                                        username = username,
                                                        otpCode = otpCode,
                                                        vehicleType = vehicleType,
                                                        vehicleName = vehicleName,
                                                        vehicleNo = vehicleNo
                                                    )
                                                )
                                                if (res.isSuccessful && res.body() != null) {
                                                    val token = res.body()!!["access_token"] as? String ?: ""
                                                    Toast.makeText(context, "Driver & Vehicle Registered Successfully!", Toast.LENGTH_SHORT).show()
                                                    onAuthenticationSuccess(token)
                                                    onDismiss()
                                                } else {
                                                    errorMessage = "Registration Failed. Check if username or vehicle plate already exists."
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "Registration Error: ${e.localizedMessage}"
                                            } finally {
                                                isSubmitting = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DriverColors.ChauffeurGold,
                                        contentColor = DriverColors.MidnightBlack
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isSubmitting) "Registering Driver..." else "Submit Driver & Vehicle Sign Up", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    "FORGOT_PASSWORD" -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DriverColors.SurfaceCard,
                            border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("FORGOT PASSWORD RECOVERY", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = forgotIdentifier,
                                    onValueChange = { forgotIdentifier = it },
                                    label = { Text("Registered Mobile Number or Email ID", color = DriverColors.TextMuted) },
                                    textStyle = DriverTypography.Body,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DriverColors.ChauffeurGold,
                                        unfocusedBorderColor = DriverColors.SurfaceBorder,
                                        focusedTextColor = DriverColors.TextPure,
                                        unfocusedTextColor = DriverColors.TextPure
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = forgotOtpCode,
                                        onValueChange = { forgotOtpCode = it },
                                        label = { Text("6-Digit Recovery OTP", color = DriverColors.TextMuted) },
                                        textStyle = DriverTypography.Body,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = DriverColors.ChauffeurGold,
                                            unfocusedBorderColor = DriverColors.SurfaceBorder,
                                            focusedTextColor = DriverColors.TextPure,
                                            unfocusedTextColor = DriverColors.TextPure
                                        ),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )

                                    Button(
                                        onClick = {
                                            if (forgotIdentifier.isBlank()) {
                                                errorMessage = "Enter registered mobile number or email ID."
                                                return@Button
                                            }
                                            scope.launch {
                                                try {
                                                    val res = DriverApiClient.getService().requestForgotPasswordOtp(
                                                        DriverForgotPasswordOtpRequestPayload(forgotIdentifier)
                                                    )
                                                    if (res.isSuccessful && res.body() != null) {
                                                        val body = res.body()!!
                                                        val code = (body["debug_otp"] ?: body["otp_code"]) as? String
                                                        if (!code.isNullOrBlank()) {
                                                            forgotOtpCode = code
                                                            statusMessage = "Recovery passcode sent! Code: $code"
                                                        } else {
                                                            statusMessage = "Recovery passcode sent to mobile/email."
                                                        }
                                                    } else {
                                                        errorMessage = "Account not found for provided identifier."
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = "Recovery Error: ${e.localizedMessage}"
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DriverColors.ChauffeurGold,
                                            contentColor = DriverColors.MidnightBlack
                                        ),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Send OTP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = newPassword,
                                    onValueChange = { newPassword = it },
                                    label = { Text("New Password (min 6 chars)", color = DriverColors.TextMuted) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    textStyle = DriverTypography.Body,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DriverColors.ChauffeurGold,
                                        unfocusedBorderColor = DriverColors.SurfaceBorder,
                                        focusedTextColor = DriverColors.TextPure,
                                        unfocusedTextColor = DriverColors.TextPure
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (forgotIdentifier.isBlank() || forgotOtpCode.isBlank() || newPassword.isBlank()) {
                                            errorMessage = "Identifier, OTP code and new password are required."
                                            return@Button
                                        }
                                        isSubmitting = true
                                        errorMessage = null
                                        scope.launch {
                                            try {
                                                val res = DriverApiClient.getService().resetForgotPassword(
                                                    DriverForgotPasswordResetRequestPayload(
                                                        identifier = forgotIdentifier,
                                                        otpCode = forgotOtpCode,
                                                        newPassword = newPassword
                                                    )
                                                )
                                                if (res.isSuccessful && res.body() != null) {
                                                    val token = res.body()!!["access_token"] as? String ?: ""
                                                    Toast.makeText(context, "Password Reset Successful!", Toast.LENGTH_SHORT).show()
                                                    onAuthenticationSuccess(token)
                                                    onDismiss()
                                                } else {
                                                    errorMessage = "Password Reset Failed. Invalid OTP code or expired."
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "Reset Error: ${e.localizedMessage}"
                                            } finally {
                                                isSubmitting = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DriverColors.ChauffeurGold,
                                        contentColor = DriverColors.MidnightBlack
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isSubmitting) "Resetting Password..." else "Change Password & Sign In", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
