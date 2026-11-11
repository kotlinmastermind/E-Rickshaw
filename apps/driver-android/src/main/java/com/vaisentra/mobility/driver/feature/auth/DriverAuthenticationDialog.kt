package com.vaisentra.mobility.driver.feature.auth

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vaisentra.mobility.driver.core.designsystem.*
import com.vaisentra.mobility.driver.core.network.*
import com.vaisentra.mobility.core.security.TokenManager
import com.vaisentra.mobility.core.ui.normalizePhoneNumber
import kotlinx.coroutines.launch

@Composable
fun DriverAuthenticationDialog(
    onDismiss: () -> Unit,
    onAuthenticationSuccess: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DriverAuthenticationContent(
            onAuthenticationSuccess = onAuthenticationSuccess,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun DriverAuthenticationContent(
    onAuthenticationSuccess: (String) -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var authTab by remember { mutableStateOf("OTP_SIGNIN") } // OTP_SIGNIN (Primary), SIGNIN, SIGNUP

    // OTP Form States
    var otpPhone by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }

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

    var isSubmitting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }



    Surface(
        modifier = modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.92f)
            .clip(RoundedCornerShape(28.dp)),
        color = DriverColors.MidnightBlack,
        border = BorderStroke(1.dp, DriverColors.SurfaceBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp)
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
                        text = "DriverGo Partner Portal",
                        style = DriverTypography.Title.copy(
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Text(
                        text = "⚡ 100% Clean Electric Fleet & Chauffeur Cockpit",
                        style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 12.sp)
                    )
                }
                if (onDismiss != null) {
                    Text(
                        text = "✕",
                        style = DriverTypography.Subtitle.copy(fontSize = 18.sp),
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Navigation Tabs (OTP Sign In is Primary)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = DriverColors.CockpitDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("OTP_SIGNIN" to "Pilot OTP Sign In", "SIGNIN" to "Password Login", "SIGNUP" to "Driver Sign Up").forEach { (key, label) ->
                        val isSelected = authTab == key
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isSelected) DriverColors.ChauffeurGold else Color.Transparent,
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
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1
                            )
                        }
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
                    "OTP_SIGNIN" -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DriverColors.SurfaceCard,
                            border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("PILOT TELECOM OTP AUTHENTICATION", style = DriverTypography.Subtitle.copy(fontSize = 13.sp, color = DriverColors.ChauffeurGold))
                                Spacer(modifier = Modifier.height(6.dp))

                                if (!isOtpSent) {
                                    Text("Enter your registered driver mobile number. An OTP will be dispatched via Twilio.", color = DriverColors.TextMuted, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = otpPhone,
                                        onValueChange = { otpPhone = it.filter { c -> c.isDigit() || c == '+' } },
                                        label = { Text("Driver Mobile (+91...)", color = DriverColors.TextMuted) },
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

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            val clean = otpPhone.trim()
                                            if (clean.length < 10) {
                                                errorMessage = "Please enter a valid 10-digit mobile phone number."
                                                return@Button
                                            }
                                            val formatted = if (clean.startsWith("+")) clean else if (clean.length == 10) "+91$clean" else clean
                                            isSubmitting = true
                                            errorMessage = null
                                            statusMessage = null
                                            scope.launch {
                                                try {
                                                    val res = DriverApiClient.getService().requestOtp(
                                                        DriverOtpRequestPayload(
                                                            phoneNumber = formatted,
                                                            role = "driver"
                                                        )
                                                    )
                                                    if (res.isSuccessful) {
                                                        isOtpSent = true
                                                        statusMessage = "OTP sent successfully via Twilio!"
                                                    } else {
                                                        errorMessage = "Could not dispatch OTP. Please check mobile number."
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = "Network Error: ${e.localizedMessage}"
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
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isSubmitting
                                    ) {
                                        Text(if (isSubmitting) "Dispatching OTP..." else "Send OTP via Twilio", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                } else {
                                    Text("Enter 6-digit OTP code sent to $otpPhone", color = DriverColors.TextMuted, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = otpCode,
                                        onValueChange = { if (it.length <= 6) otpCode = it.filter { c -> c.isDigit() } },
                                        label = { Text("6-Digit OTP Code", color = DriverColors.TextMuted) },
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

                                    OutlinedTextField(
                                        value = fullName,
                                        onValueChange = { fullName = it },
                                        label = { Text("Chauffeur Full Name (Optional for new pilots)", color = DriverColors.TextMuted) },
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

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            val cleanCode = otpCode.trim()
                                            if (cleanCode.length < 4) {
                                                errorMessage = "Please enter 6-digit OTP passcode."
                                                return@Button
                                            }
                                            val formatted = if (otpPhone.startsWith("+")) otpPhone else if (otpPhone.length == 10) "+91$otpPhone" else otpPhone
                                            val name = fullName.trim().ifBlank { null }

                                            isSubmitting = true
                                            errorMessage = null
                                            scope.launch {
                                                try {
                                                    val res = DriverApiClient.getService().verifyOtp(
                                                        DriverOtpVerifyPayload(
                                                            phoneNumber = formatted,
                                                            otpCode = cleanCode,
                                                            role = "driver",
                                                            fullName = name
                                                        )
                                                    )
                                                    if (res.isSuccessful && res.body() != null) {
                                                        val token = res.body()!!["access_token"] as? String ?: ""
                                                        Toast.makeText(context, "Welcome Chauffeur!", Toast.LENGTH_SHORT).show()
                                                        onAuthenticationSuccess(token)
                                                        onDismiss?.invoke()
                                                    } else {
                                                        errorMessage = "Invalid or expired OTP passcode. Please check and try again."
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = "Verification Error: ${e.localizedMessage}"
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
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isSubmitting
                                    ) {
                                        Text(if (isSubmitting) "Verifying..." else "Verify & Open Cockpit", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    TextButton(
                                        onClick = {
                                            isOtpSent = false
                                            otpCode = ""
                                            errorMessage = null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Change Phone Number", color = DriverColors.ChauffeurGold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

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

                                Text("Sign In with Email or Mobile Number & Password", color = DriverColors.TextMuted, fontSize = 11.sp)

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

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
                                        if (clean.isBlank() || password.isBlank()) {
                                            errorMessage = "Enter username, email or phone number and password."
                                            return@Button
                                        }
                                        val formatted = if (clean.startsWith("+")) clean else if (clean.length == 10) "+91$clean" else clean
                                        val pwd = password

                                        // Clear input fields immediately on submit
                                        phoneNumber = ""
                                        password = ""

                                        isSubmitting = true
                                        errorMessage = null
                                        scope.launch {
                                            try {
                                                val res = DriverApiClient.getService().unifiedLogin(
                                                    DriverUnifiedLoginRequestPayload(
                                                        loginType = "PASSWORD",
                                                        identifier = formatted,
                                                        password = pwd
                                                    )
                                                )
                                                if (res.isSuccessful && res.body() != null) {
                                                    val token = res.body()!!["access_token"] as? String ?: ""
                                                    Toast.makeText(context, "Welcome Chauffeur!", Toast.LENGTH_SHORT).show()
                                                    onAuthenticationSuccess(token)
                                                    onDismiss?.invoke()
                                                } else {
                                                    errorMessage = "Login Failed: Invalid credentials or account not found."
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
                                    label = { Text("Password (min 12 chars)", color = DriverColors.TextMuted) },
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

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (fullName.isBlank() || phoneNumber.isBlank() || email.isBlank() || username.isBlank() || password.isBlank() || vehicleName.isBlank() || vehicleNo.isBlank()) {
                                            errorMessage = "All chauffeur details & vehicle specs are required."
                                            return@Button
                                        }
                                        if (password.length < 12) {
                                            errorMessage = "Password must be at least 12 characters."
                                            return@Button
                                        }
                                        val reqPayload = DriverSignupRequestPayload(
                                            phoneNumber = phoneNumber,
                                            email = email,
                                            fullName = fullName,
                                            password = password,
                                            username = username,
                                            vehicleType = vehicleType,
                                            vehicleName = vehicleName,
                                            vehicleNo = vehicleNo
                                        )
                                        // Clear all inputs immediately on submit
                                        fullName = ""
                                        phoneNumber = ""
                                        email = ""
                                        username = ""
                                        password = ""
                                        vehicleName = ""
                                        vehicleNo = ""

                                        isSubmitting = true
                                        errorMessage = null
                                        scope.launch {
                                            try {
                                                val res = DriverApiClient.getService().signupDriver(reqPayload)
                                                if (res.isSuccessful && res.body() != null) {
                                                    val token = res.body()!!["access_token"] as? String ?: ""
                                                    Toast.makeText(context, "Driver & Vehicle Registered Successfully!", Toast.LENGTH_SHORT).show()
                                                    onAuthenticationSuccess(token)
                                                    onDismiss?.invoke()
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
                                Text("FORGOT PASSWORD ASSISTANCE", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "SMS recovery passcode service has been decommissioned. To reset your chauffeur pilot credentials, please contact Driver Operations Support at driver-support@vaisentra.com or visit your local station dispatch desk.",
                                    style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 12.sp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { authTab = "SIGNIN" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DriverColors.ChauffeurGold,
                                        contentColor = DriverColors.MidnightBlack
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Back to Sign In", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Production Network Active",
                    color = DriverColors.TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
