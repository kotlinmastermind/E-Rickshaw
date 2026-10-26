package com.luxury.chauffeur.customer.feature.auth

import android.app.Activity
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
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.ApiClient
import com.luxury.chauffeur.customer.core.network.FirebaseVerifyPayload
import com.luxury.chauffeur.customer.core.network.OtpRequestPayload
import com.luxury.chauffeur.customer.core.network.OtpVerifyPayload
import com.luxury.chauffeur.customer.core.security.TokenManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun OtpAuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var phoneNumber by remember { mutableStateOf("+91") }
    var otpCode by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successHint by remember { mutableStateOf<String?>(null) }
    var resendTimer by remember { mutableIntStateOf(60) }

    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }

    LaunchedEffect(isOtpSent) {
        if (isOtpSent) {
            resendTimer = 60
            while (resendTimer > 0) {
                kotlinx.coroutines.delay(1000)
                resendTimer--
            }
        }
    }

    fun verifyWithBackendApi(cleanPhone: String) {
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

    fun signInWithCredential(credential: PhoneAuthCredential, formattedPhone: String) {
        isLoading = true
        try {
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val fbUser = task.result?.user
                        scope.launch {
                            try {
                                val response = ApiClient.getService().verifyFirebaseAuth(
                                    FirebaseVerifyPayload(
                                        phoneNumber = formattedPhone,
                                        role = "customer",
                                        firebaseUid = fbUser?.uid
                                    )
                                )
                                isLoading = false
                                if (response.isSuccessful && response.body() != null) {
                                    val tokenResp = response.body()!!
                                    TokenManager.getInstance(context).saveAuth(
                                        token = tokenResp.accessToken,
                                        userId = tokenResp.user.id,
                                        phone = tokenResp.user.phoneNumber,
                                        role = tokenResp.user.role
                                    )
                                    onAuthSuccess()
                                } else {
                                    verifyWithBackendApi(formattedPhone)
                                }
                            } catch (e: Exception) {
                                verifyWithBackendApi(formattedPhone)
                            }
                        }
                    } else {
                        isLoading = false
                        errorMessage = task.exception?.localizedMessage ?: "Invalid passcode."
                    }
                }
        } catch (e: Exception) {
            verifyWithBackendApi(formattedPhone)
        }
    }

    fun requestOtpFromApiFallback(cleanPhone: String, formattedPhone: String, note: String?) {
        scope.launch {
            try {
                val response = ApiClient.getService().requestOtp(OtpRequestPayload(formattedPhone))
                isLoading = false
                if (response.isSuccessful) {
                    val body = response.body()
                    isOtpSent = true
                    val debugOtp = body?.get("debug_otp") as? String
                    if (!debugOtp.isNullOrBlank()) {
                        otpCode = debugOtp
                        successHint = "Dev Passcode auto-filled: $debugOtp"
                    } else {
                        successHint = "Passcode dispatched to $formattedPhone"
                    }
                } else {
                    val err = response.errorBody()?.string() ?: "Request failed"
                    errorMessage = if (err.contains("detail")) err else (note ?: "Failed to send passcode. Check backend connection.")
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Network error: ${e.localizedMessage ?: "Could not reach FastAPI server"}"
            }
        }
    }

    fun requestOtpFromApi() {
        val cleanPhone = phoneNumber.replace(" ", "").trim()
        val formattedPhone = if (cleanPhone.startsWith("+")) cleanPhone else "+91$cleanPhone"
        if (cleanPhone.length < 10) {
            errorMessage = "Please enter a valid telephone number."
            return
        }

        isLoading = true
        errorMessage = null
        successHint = null

        if (activity != null) {
            try {
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        isLoading = false
                        val code = credential.smsCode
                        if (!code.isNullOrBlank()) {
                            otpCode = code
                        }
                        signInWithCredential(credential, formattedPhone)
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        // Fallback to FastAPI backend OTP if Firebase throws
                        requestOtpFromApiFallback(cleanPhone, formattedPhone, e.localizedMessage)
                    }

                    override fun onCodeSent(
                        verId: String,
                        token: PhoneAuthProvider.ForceResendingToken
                    ) {
                        isLoading = false
                        isOtpSent = true
                        verificationId = verId
                        resendToken = token
                        successHint = "Google SMS passcode dispatched to $formattedPhone"
                    }
                }

                val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                    .setPhoneNumber(formattedPhone)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(callbacks)
                    .build()
                PhoneAuthProvider.verifyPhoneNumber(options)
                return
            } catch (e: Exception) {
                requestOtpFromApiFallback(cleanPhone, formattedPhone, e.localizedMessage)
                return
            }
        }
        requestOtpFromApiFallback(cleanPhone, formattedPhone, null)
    }

    fun verifyOtpFromApi() {
        val cleanPhone = phoneNumber.replace(" ", "").trim()
        val formattedPhone = if (cleanPhone.startsWith("+")) cleanPhone else "+91$cleanPhone"
        if (otpCode.length < 4) {
            errorMessage = "Please enter the complete passcode."
            return
        }

        isLoading = true
        errorMessage = null

        val vId = verificationId
        if (vId != null) {
            try {
                val credential = PhoneAuthProvider.getCredential(vId, otpCode.trim())
                signInWithCredential(credential, formattedPhone)
                return
            } catch (e: Exception) {
                // fallback to backend
            }
        }
        verifyWithBackendApi(formattedPhone)
    }

    Scaffold(
        containerColor = LuxuryColors.ObsidianBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = if (!isOtpSent) "ENTER YOUR PHONE" else "ENTER PASSCODE",
                    style = LuxuryTypography.HeadlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (!isOtpSent)
                        "Experience bespoke private chauffeur travel. Enter your mobile number to receive a secure passcode."
                    else
                        "A 6-digit confidential authorization code was dispatched to $phoneNumber.",
                    style = LuxuryTypography.BodySubtle
                )
                Spacer(modifier = Modifier.height(32.dp))

                errorMessage?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LuxuryColors.CrimsonAlert.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = err,
                            color = LuxuryColors.CrimsonAlert,
                            style = LuxuryTypography.BodySubtle,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                successHint?.let { hint ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LuxuryColors.LuxuryEmerald.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = hint,
                            color = LuxuryColors.LuxuryEmerald,
                            style = LuxuryTypography.BodySubtle,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (!isOtpSent) {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Mobile Number", color = LuxuryColors.SubtleGray) },
                        textStyle = LuxuryTypography.BodyRegular.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp
                        ),
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
