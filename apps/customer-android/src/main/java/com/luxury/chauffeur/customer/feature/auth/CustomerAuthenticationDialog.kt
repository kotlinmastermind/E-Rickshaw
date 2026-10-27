package com.luxury.chauffeur.customer.feature.auth

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
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import kotlinx.coroutines.launch

@Composable
fun CustomerAuthenticationDialog(
    onDismiss: () -> Unit,
    onAuthenticationSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var authTab by remember { mutableStateOf("SIGNIN") } // SIGNUP, SIGNIN, FORGOT_PASSWORD
    var loginMode by remember { mutableStateOf("PASSWORD") } // PASSWORD, OTP

    // Form states
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }

    // Forgot password states
    var forgotIdentifier by remember { mutableStateOf("") }
    var forgotOtpCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(LuxuryDimensions.RadiusLarge)),
            color = LuxuryColors.ObsidianBlack,
            border = BorderStroke(1.5.dp, LuxuryColors.ChampagneGold)
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
                            text = "AUTHENTICATION SANCTUARY",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Customer Account Portal",
                            style = LuxuryTypography.BodySubtle
                        )
                    }
                    Text(
                        text = "✕",
                        style = LuxuryTypography.TitleGold.copy(fontSize = 18.sp),
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("SIGNIN" to "Sign In", "SIGNUP" to "Sign Up", "FORGOT_PASSWORD" to "Forgot Pass").forEach { (key, label) ->
                        val isSelected = authTab == key
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusPill),
                            color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardSurface,
                            border = BorderStroke(1.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
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
                                color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                style = LuxuryTypography.TitleGold.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (errorMessage != null) {
                    Surface(
                        color = LuxuryColors.CrimsonAlert.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = LuxuryColors.CrimsonAlert,
                            style = LuxuryTypography.BodySubtle,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (statusMessage != null) {
                    Surface(
                        color = LuxuryColors.LuxuryEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        border = BorderStroke(1.dp, LuxuryColors.LuxuryEmerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = statusMessage!!,
                            color = LuxuryColors.LuxuryEmerald,
                            style = LuxuryTypography.BodySubtle,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                when (authTab) {
                    "SIGNIN" -> {
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("SIGN IN TO YOUR ACCOUNT", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(8.dp))

                            // Mode Switcher
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("PASSWORD" to "Password Login", "OTP" to "OTP Login").forEach { (mKey, mLabel) ->
                                    val isSelected = loginMode == mKey
                                    Surface(
                                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                        color = if (isSelected) LuxuryColors.ChampagneGold.copy(alpha = 0.2f) else LuxuryColors.CardSurface,
                                        border = BorderStroke(1.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { loginMode = mKey }
                                    ) {
                                        Text(
                                            text = mLabel,
                                            style = LuxuryTypography.BodySubtle.copy(
                                                color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.PlatinumWhite,
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
                                label = { Text("Username, Email ID, or Mobile Phone", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (loginMode == "PASSWORD") {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password", color = LuxuryColors.SubtleGray) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    textStyle = LuxuryTypography.BodyRegular,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = LuxuryColors.ChampagneGold,
                                        unfocusedBorderColor = LuxuryColors.CardBorder,
                                        focusedTextColor = LuxuryColors.PlatinumWhite,
                                        unfocusedTextColor = LuxuryColors.PlatinumWhite
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
                                        label = { Text("6-Digit OTP Code", color = LuxuryColors.SubtleGray) },
                                        textStyle = LuxuryTypography.BodyRegular,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = LuxuryColors.ChampagneGold,
                                            unfocusedBorderColor = LuxuryColors.CardBorder,
                                            focusedTextColor = LuxuryColors.PlatinumWhite,
                                            unfocusedTextColor = LuxuryColors.PlatinumWhite
                                        ),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )

                                    Button(
                                        onClick = {
                                            if (phoneNumber.length < 10) {
                                                errorMessage = "Enter a valid 10-digit mobile phone number first."
                                                return@Button
                                            }
                                            scope.launch {
                                                try {
                                                    val res = ApiClient.getService().requestOtp(OtpRequestPayload(phoneNumber))
                                                    if (res.isSuccessful) {
                                                        statusMessage = "OTP dispatched! Check phone or console."
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = "Error requesting OTP: ${e.localizedMessage}"
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LuxuryColors.ChampagneGold,
                                            contentColor = LuxuryColors.ObsidianBlack
                                        ),
                                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                                    ) {
                                        Text("Send OTP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            LuxuryButton(
                                text = if (isSubmitting) "Authenticating..." else "Sign In",
                                onClick = {
                                    if (phoneNumber.isBlank()) {
                                        errorMessage = "Please enter your username, email or phone number."
                                        return@LuxuryButton
                                    }
                                    isSubmitting = true
                                    errorMessage = null
                                    scope.launch {
                                        try {
                                            val res = ApiClient.getService().unifiedLogin(
                                                UnifiedLoginRequestPayload(
                                                    loginType = loginMode,
                                                    identifier = phoneNumber,
                                                    password = if (loginMode == "PASSWORD") password else null,
                                                    otpCode = if (loginMode == "OTP") otpCode else null
                                                )
                                            )
                                            if (res.isSuccessful && res.body() != null) {
                                                Toast.makeText(context, "Sign In Successful!", Toast.LENGTH_SHORT).show()
                                                onAuthenticationSuccess(res.body()!!.accessToken)
                                                onDismiss()
                                            } else {
                                                errorMessage = "Sign In failed: Invalid credentials or expired OTP."
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Sign In Error: ${e.localizedMessage}"
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                },
                                isLoading = isSubmitting
                            )
                        }
                    }

                    "SIGNUP" -> {
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("CREATE NEW CUSTOMER ACCOUNT", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Full Name", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text("Mobile Phone Number (+91...)", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email Address", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Unique Username", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password (min 6 chars)", color = LuxuryColors.SubtleGray) },
                                visualTransformation = PasswordVisualTransformation(),
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = otpCode,
                                    onValueChange = { otpCode = it },
                                    label = { Text("6-Digit OTP Code", color = LuxuryColors.SubtleGray) },
                                    textStyle = LuxuryTypography.BodyRegular,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = LuxuryColors.ChampagneGold,
                                        unfocusedBorderColor = LuxuryColors.CardBorder,
                                        focusedTextColor = LuxuryColors.PlatinumWhite,
                                        unfocusedTextColor = LuxuryColors.PlatinumWhite
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
                                                val res = ApiClient.getService().requestOtp(OtpRequestPayload(phoneNumber))
                                                if (res.isSuccessful) {
                                                    statusMessage = "OTP sent to $phoneNumber!"
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "OTP Error: ${e.localizedMessage}"
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LuxuryColors.ChampagneGold,
                                        contentColor = LuxuryColors.ObsidianBlack
                                    ),
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                                ) {
                                    Text("Send OTP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            LuxuryButton(
                                text = if (isSubmitting) "Creating Account..." else "Submit Sign Up",
                                onClick = {
                                    if (fullName.isBlank() || phoneNumber.isBlank() || email.isBlank() || username.isBlank() || password.isBlank() || otpCode.isBlank()) {
                                        errorMessage = "All fields and OTP verification code are required."
                                        return@LuxuryButton
                                    }
                                    isSubmitting = true
                                    errorMessage = null
                                    scope.launch {
                                        try {
                                            val res = ApiClient.getService().signupCustomer(
                                                CustomerSignupRequestPayload(
                                                    phoneNumber = phoneNumber,
                                                    email = email,
                                                    fullName = fullName,
                                                    password = password,
                                                    username = username,
                                                    otpCode = otpCode
                                                )
                                            )
                                            if (res.isSuccessful && res.body() != null) {
                                                Toast.makeText(context, "Account Registered Successfully!", Toast.LENGTH_SHORT).show()
                                                onAuthenticationSuccess(res.body()!!.accessToken)
                                                onDismiss()
                                            } else {
                                                errorMessage = "Registration Failed. Check if email/username already taken or OTP invalid."
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Registration Error: ${e.localizedMessage}"
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                },
                                isLoading = isSubmitting
                            )
                        }
                    }

                    "FORGOT_PASSWORD" -> {
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("FORGOT PASSWORD RECOVERY", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = forgotIdentifier,
                                onValueChange = { forgotIdentifier = it },
                                label = { Text("Registered Mobile Number or Email ID", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
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
                                    label = { Text("6-Digit Recovery OTP", color = LuxuryColors.SubtleGray) },
                                    textStyle = LuxuryTypography.BodyRegular,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = LuxuryColors.ChampagneGold,
                                        unfocusedBorderColor = LuxuryColors.CardBorder,
                                        focusedTextColor = LuxuryColors.PlatinumWhite,
                                        unfocusedTextColor = LuxuryColors.PlatinumWhite
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
                                                val res = ApiClient.getService().requestForgotPasswordOtp(
                                                    ForgotPasswordOtpRequestPayload(forgotIdentifier)
                                                )
                                                if (res.isSuccessful) {
                                                    statusMessage = "Recovery passcode sent to registered mobile/email."
                                                } else {
                                                    errorMessage = "Account not found for provided identifier."
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "Recovery Error: ${e.localizedMessage}"
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LuxuryColors.ChampagneGold,
                                        contentColor = LuxuryColors.ObsidianBlack
                                    ),
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                                ) {
                                    Text("Send OTP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = { Text("New Password (min 6 chars)", color = LuxuryColors.SubtleGray) },
                                visualTransformation = PasswordVisualTransformation(),
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            LuxuryButton(
                                text = if (isSubmitting) "Resetting Password..." else "Change Password & Sign In",
                                onClick = {
                                    if (forgotIdentifier.isBlank() || forgotOtpCode.isBlank() || newPassword.isBlank()) {
                                        errorMessage = "Identifier, OTP code, and new password are required."
                                        return@LuxuryButton
                                    }
                                    isSubmitting = true
                                    errorMessage = null
                                    scope.launch {
                                        try {
                                            val res = ApiClient.getService().resetForgotPassword(
                                                ForgotPasswordResetRequestPayload(
                                                    identifier = forgotIdentifier,
                                                    otpCode = forgotOtpCode,
                                                    newPassword = newPassword
                                                )
                                            )
                                            if (res.isSuccessful && res.body() != null) {
                                                Toast.makeText(context, "Password Changed & Sign In Successful!", Toast.LENGTH_SHORT).show()
                                                onAuthenticationSuccess(res.body()!!.accessToken)
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
                                isLoading = isSubmitting
                            )
                        }
                    }
                }
            }
        }
    }
}
