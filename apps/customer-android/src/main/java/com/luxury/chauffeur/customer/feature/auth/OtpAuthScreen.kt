package com.luxury.chauffeur.customer.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.ApiClient
import com.luxury.chauffeur.customer.core.network.OtpRequestPayload
import com.luxury.chauffeur.customer.core.network.OtpVerifyPayload
import com.luxury.chauffeur.customer.core.security.TokenManager
import kotlinx.coroutines.launch

@Composable
fun OtpAuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phoneNumber by remember { mutableStateOf("+919876543210") }
    var otpCode by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successHint by remember { mutableStateOf<String?>(null) }
    var resendTimer by remember { mutableIntStateOf(60) }

    LaunchedEffect(isOtpSent) {
        if (isOtpSent) {
            resendTimer = 60
            while (resendTimer > 0) {
                kotlinx.coroutines.delay(1000)
                resendTimer--
            }
        }
    }

    fun requestOtpFromApi() {
        val cleanPhone = phoneNumber.replace(" ", "").trim()
        if (cleanPhone.length < 10) {
            errorMessage = "Please enter a valid telephone number."
            return
        }

        isLoading = true
        errorMessage = null
        successHint = null

        scope.launch {
            try {
                val response = ApiClient.getService().requestOtp(OtpRequestPayload(cleanPhone))
                isLoading = false
                if (response.isSuccessful) {
                    val body = response.body()
                    isOtpSent = true
                    val debugOtp = body?.get("debug_otp") as? String
                    if (!debugOtp.isNullOrBlank()) {
                        otpCode = debugOtp
                        successHint = "Dev Passcode auto-filled: $debugOtp"
                    }
                } else {
                    val err = response.errorBody()?.string() ?: "Request failed"
                    errorMessage = if (err.contains("detail")) err else "Failed to send passcode. Check backend connection."
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Network error: ${e.localizedMessage ?: "Could not reach FastAPI server"}"
            }
        }
    }

    fun verifyOtpFromApi() {
        val cleanPhone = phoneNumber.replace(" ", "").trim()
        if (otpCode.length < 4) {
            errorMessage = "Please enter the complete passcode."
            return
        }

        isLoading = true
        errorMessage = null

        scope.launch {
            try {
                val response = ApiClient.getService().verifyOtp(
                    OtpVerifyPayload(
                        phoneNumber = cleanPhone,
                        otpCode = otpCode.trim(),
                        role = "customer"
                    )
                )
                isLoading = false
                if (response.isSuccessful) {
                    val tokenResp = response.body()
                    if (tokenResp != null) {
                        TokenManager.getInstance(context).saveAuth(
                            token = tokenResp.accessToken,
                            userId = tokenResp.user.id,
                            phone = tokenResp.user.phoneNumber,
                            role = tokenResp.user.role
                        )
                        onAuthSuccess()
                    } else {
                        errorMessage = "Invalid response from server."
                    }
                } else {
                    val err = response.errorBody()?.string() ?: "Verification failed"
                    errorMessage = if (err.contains("detail")) err else "Invalid or expired passcode."
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Network error: ${e.localizedMessage ?: "Could not verify"}"
            }
        }
    }

    Scaffold(
        containerColor = LuxuryColors.ObsidianBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LuxuryDimensions.PaddingLarge),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = if (!isOtpSent) "ENTER YOUR PHONE" else "ENTER SECURITY PASSCODE",
                    style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (!isOtpSent)
                        "Experience bespoke private chauffeur travel. Enter your mobile number to receive a secure passcode."
                    else
                        "A 6-digit authorization passcode has been dispatched to $phoneNumber.",
                    style = LuxuryTypography.BodySubtle
                )
                Spacer(modifier = Modifier.height(32.dp))

                if (errorMessage != null) {
                    Surface(
                        color = LuxuryColors.CrimsonAlert.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.CrimsonAlert),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (successHint != null) {
                    Surface(
                        color = LuxuryColors.ChampagneGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = successHint ?: "",
                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (!isOtpSent) {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Mobile Number", color = LuxuryColors.SubtleGray) },
                        textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 18.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LuxuryColors.ChampagneGold,
                            unfocusedBorderColor = LuxuryColors.CardBorder,
                            focusedTextColor = LuxuryColors.PlatinumWhite,
                            unfocusedTextColor = LuxuryColors.PlatinumWhite
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6) otpCode = it },
                        label = { Text("6-Digit Passcode", color = LuxuryColors.SubtleGray) },
                        textStyle = LuxuryTypography.OtpDigits,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LuxuryColors.ChampagneGold,
                            unfocusedBorderColor = LuxuryColors.CardBorder,
                            focusedTextColor = LuxuryColors.ChampagneGold,
                            unfocusedTextColor = LuxuryColors.ChampagneGold
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (resendTimer > 0) "Resend code in ${resendTimer}s" else "Didn't receive passcode?",
                            style = LuxuryTypography.BodySubtle
                        )
                        if (resendTimer == 0) {
                            TextButton(onClick = {
                                requestOtpFromApi()
                            }) {
                                Text("Resend Now", color = LuxuryColors.ChampagneGold)
                            }
                        }
                    }
                }
            }

            Column {
                LuxuryButton(
                    text = if (!isOtpSent) "CONTINUE" else "VERIFY & ENTER",
                    isLoading = isLoading,
                    onClick = {
                        if (!isOtpSent) {
                            requestOtpFromApi()
                        } else {
                            verifyOtpFromApi()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
