package com.vaisentra.mobility.rider.feature.auth

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.rider.core.designsystem.MobilityColors
import com.vaisentra.mobility.core.designsystem.SharedDesignTokens
import com.vaisentra.mobility.rider.core.network.ApiClient
import com.vaisentra.mobility.rider.core.network.CustomerOtpRequestPayload
import com.vaisentra.mobility.rider.core.network.CustomerOtpVerifyPayload
import com.vaisentra.mobility.rider.core.network.CustomerSignupRequestPayload
import com.vaisentra.mobility.rider.core.network.UnifiedLoginRequestPayload
import com.vaisentra.mobility.core.security.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val M3Primary = SharedDesignTokens.M3PrimaryDark
private val M3OnPrimary = Color(0xFF041E49)
private val M3Surface = SharedDesignTokens.M3SurfaceDark
private val M3SurfaceContainer = SharedDesignTokens.M3SurfaceContainerDark
private val M3SurfaceContainerHigh = SharedDesignTokens.M3SurfaceContainerHighDark
private val M3Outline = SharedDesignTokens.M3OutlineDark

enum class AuthTab {
    OTP_SIGN_IN,      // Primary / Default OTP Authentication via Twilio
    PASSWORD_SIGN_IN, // Password Sign In
    SIGN_UP           // Standard Registration
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager.getInstance(context) }

    // Primary Default Tab: OTP Login
    var selectedTab by remember { mutableStateOf(AuthTab.OTP_SIGN_IN) }

    // OTP Login States
    var otpPhone by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpNewUserName by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var otpResendCountdown by remember { mutableIntStateOf(0) }

    // Password Sign In States
    var signInIdentifier by remember { mutableStateOf("") }
    var signInPassword by remember { mutableStateOf("") }

    // Sign Up States
    var signUpName by remember { mutableStateOf("") }
    var signUpPhone by remember { mutableStateOf("") }
    var signUpEmail by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var signUpConfirmPassword by remember { mutableStateOf("") }

    // UI state
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }

    fun normalizePhone(raw: String): String {
        val clean = raw.trim().replace(" ", "").replace("-", "")
        return if (clean.startsWith("+")) clean else if (clean.length == 10) "+91$clean" else clean
    }

    // Resend countdown timer
    LaunchedEffect(otpResendCountdown) {
        if (otpResendCountdown > 0) {
            delay(1000L)
            otpResendCountdown -= 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(M3Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Material 3 Expressive Header
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = M3SurfaceContainerHigh,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🚖", fontSize = 36.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "RideGo",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = SharedDesignTokens.M3EcoGreen.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, SharedDesignTokens.M3EcoGreen.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "⚡ 100% Clean EV Mobility",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6EE7B7),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Material 3 Expressive Pill Tabs
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = M3SurfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp)
                ) {
                    listOf(
                        AuthTab.OTP_SIGN_IN to "OTP Sign In",
                        AuthTab.PASSWORD_SIGN_IN to "Password",
                        AuthTab.SIGN_UP to "Sign Up"
                    ).forEach { (tab, label) ->
                        val isSelected = selectedTab == tab
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isSelected) M3Primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedTab = tab
                                    errorMessage = null
                                    successMessage = null
                                }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) M3OnPrimary else Color(0xFFC4C6D0),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 11.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error Display (Material 3 Error Card)
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SharedDesignTokens.Red.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SharedDesignTokens.Red.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Success Display (Material 3 Success Card)
            if (!successMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SharedDesignTokens.M3EcoGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, SharedDesignTokens.M3EcoGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        text = successMessage ?: "",
                        color = Color(0xFF6EE7B7),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // -------------------------------------------------------------
            // TAB 1: PRIMARY OTP SIGN IN (Twilio Telecom SMS Delivery)
            // -------------------------------------------------------------
            if (selectedTab == AuthTab.OTP_SIGN_IN) {
                if (!isOtpSent) {
                    // STEP 1: Enter Mobile Number
                    Text(
                        text = "Instant Sign In with Mobile OTP",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "We will send a 6-digit verification code to your phone via Twilio.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp)
                    )

                    OutlinedTextField(
                        value = otpPhone,
                        onValueChange = { otpPhone = it.filter { char -> char.isDigit() || char == '+' } },
                        label = { Text("Mobile Number (e.g. 9876543210)", color = Color(0xFF94A3B8)) },
                        prefix = { Text("+91 ", color = M3Primary, fontWeight = FontWeight.Bold) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = M3Primary,
                            unfocusedBorderColor = M3Outline,
                            focusedContainerColor = M3SurfaceContainer,
                            unfocusedContainerColor = M3SurfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val clean = otpPhone.trim()
                            if (clean.length < 10) {
                                errorMessage = "Please enter a valid 10-digit mobile phone number."
                                return@Button
                            }
                            val formatted = normalizePhone(clean)
                            isLoading = true
                            errorMessage = null
                            successMessage = null
                            scope.launch {
                                try {
                                    val res = ApiClient.getService().requestOtp(
                                        CustomerOtpRequestPayload(
                                            phoneNumber = formatted,
                                            role = "customer"
                                        )
                                    )
                                    if (res.isSuccessful) {
                                        isOtpSent = true
                                        otpResendCountdown = 60
                                        successMessage = "OTP sent successfully via Twilio!"
                                    } else {
                                        val err = res.errorBody()?.string() ?: "Failed to dispatch OTP."
                                        errorMessage = if (err.contains("detail")) err else "Could not send OTP. Please verify your mobile number."
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Network error: ${e.localizedMessage ?: "Could not reach server"}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = M3Primary, contentColor = M3OnPrimary),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = M3OnPrimary, strokeWidth = 2.5.dp)
                        } else {
                            Text("GET OTP", fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
                        }
                    }

                } else {
                    // STEP 2: Enter OTP Code & Optional Name for New Users
                    Text(
                        text = "Enter Verification Code",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Enter the 6-digit code sent to ${normalizePhone(otpPhone)}",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp)
                    )

                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6) otpCode = it.filter { c -> c.isDigit() } },
                        label = { Text("6-Digit OTP Code", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = M3Primary,
                            unfocusedBorderColor = M3Outline,
                            focusedContainerColor = M3SurfaceContainer,
                            unfocusedContainerColor = M3SurfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = otpNewUserName,
                        onValueChange = { otpNewUserName = it },
                        label = { Text("Full Name (Optional for new riders)", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = M3Primary,
                            unfocusedBorderColor = M3Outline,
                            focusedContainerColor = M3SurfaceContainer,
                            unfocusedContainerColor = M3SurfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (otpCode.trim().length < 4) {
                                errorMessage = "Please enter the 6-digit OTP passcode."
                                return@Button
                            }
                            val formatted = normalizePhone(otpPhone)
                            val code = otpCode.trim()
                            val name = otpNewUserName.trim().ifBlank { null }

                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val res = ApiClient.getService().verifyOtp(
                                        CustomerOtpVerifyPayload(
                                            phoneNumber = formatted,
                                            otpCode = code,
                                            role = "customer",
                                            fullName = name
                                        )
                                    )

                                    if (res.isSuccessful && res.body()?.accessToken != null) {
                                        val token = res.body()!!.accessToken
                                        val uid = res.body()?.user?.id ?: "usr_customer"
                                        val uName = res.body()?.user?.fullName ?: name ?: "Rider"
                                        tokenManager.saveAuth(token, uid, formatted, "customer")
                                        Toast.makeText(context, "Welcome, $uName!", Toast.LENGTH_SHORT).show()
                                        onAuthSuccess()
                                    } else {
                                        val err = res.errorBody()?.string() ?: "Invalid OTP."
                                        errorMessage = if (err.contains("detail")) err else "Invalid or expired OTP passcode. Please try again."
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Verification error: ${e.localizedMessage ?: "Could not contact server"}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = M3Primary, contentColor = M3OnPrimary),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = M3OnPrimary, strokeWidth = 2.5.dp)
                        } else {
                            Text("VERIFY & SIGN IN", fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                isOtpSent = false
                                otpCode = ""
                                errorMessage = null
                                successMessage = null
                            }
                        ) {
                            Text("Change Number", color = M3Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        TextButton(
                            onClick = {
                                if (otpResendCountdown == 0) {
                                    val formatted = normalizePhone(otpPhone)
                                    isLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        try {
                                            val res = ApiClient.getService().requestOtp(
                                                CustomerOtpRequestPayload(
                                                    phoneNumber = formatted,
                                                    role = "customer"
                                                )
                                            )
                                            if (res.isSuccessful) {
                                                otpResendCountdown = 60
                                                successMessage = "New OTP sent via Twilio!"
                                            } else {
                                                errorMessage = "Please wait before resending OTP."
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Error resending OTP: ${e.localizedMessage}"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            enabled = otpResendCountdown == 0 && !isLoading
                        ) {
                            Text(
                                text = if (otpResendCountdown > 0) "Resend in ${otpResendCountdown}s" else "Resend OTP",
                                color = if (otpResendCountdown > 0) Color(0xFF64748B) else M3Primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

            // -------------------------------------------------------------
            // TAB 2: PASSWORD SIGN IN
            // -------------------------------------------------------------
            } else if (selectedTab == AuthTab.PASSWORD_SIGN_IN) {
                OutlinedTextField(
                    value = signInIdentifier,
                    onValueChange = { signInIdentifier = it },
                    label = { Text("Email or Mobile Number", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = M3Primary,
                        unfocusedBorderColor = M3Outline,
                        focusedContainerColor = M3SurfaceContainer,
                        unfocusedContainerColor = M3SurfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = signInPassword,
                    onValueChange = { signInPassword = it },
                    label = { Text("Password", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Check else Icons.Default.Lock,
                                contentDescription = "Toggle password",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = M3Primary,
                        unfocusedBorderColor = M3Outline,
                        focusedContainerColor = M3SurfaceContainer,
                        unfocusedContainerColor = M3SurfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (signInIdentifier.isBlank() || signInPassword.isBlank()) {
                            errorMessage = "Please enter email/mobile number and password."
                            return@Button
                        }
                        val ident = if (signInIdentifier.contains("@")) {
                            signInIdentifier.trim().lowercase()
                        } else {
                            normalizePhone(signInIdentifier)
                        }
                        val pwd = signInPassword
                        signInIdentifier = ""
                        signInPassword = ""
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val res = ApiClient.getService().unifiedLogin(
                                    UnifiedLoginRequestPayload(
                                        loginType = "PASSWORD",
                                        identifier = ident,
                                        password = pwd
                                    )
                                )

                                if (res.isSuccessful && res.body()?.accessToken != null) {
                                    val token = res.body()!!.accessToken
                                    val uid = res.body()?.user?.id ?: "usr_customer"
                                    tokenManager.saveAuth(token, uid, ident, "customer")
                                    Toast.makeText(context, "Sign in successful!", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                } else {
                                    val err = res.errorBody()?.string() ?: "Invalid credentials. Please try again."
                                    errorMessage = if (err.contains("detail")) err else "Invalid email/mobile or password."
                                }
                            } catch (e: Exception) {
                                errorMessage = "Network error: ${e.localizedMessage ?: "Could not reach server"}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = M3Primary, contentColor = M3OnPrimary),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = M3OnPrimary, strokeWidth = 2.5.dp)
                    } else {
                        Text("SIGN IN", fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
                    }
                }

            // -------------------------------------------------------------
            // TAB 3: SIGN UP
            // -------------------------------------------------------------
            } else {
                OutlinedTextField(
                    value = signUpName,
                    onValueChange = { signUpName = it },
                    label = { Text("Full Name", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = M3Primary,
                        unfocusedBorderColor = M3Outline,
                        focusedContainerColor = M3SurfaceContainer,
                        unfocusedContainerColor = M3SurfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = signUpPhone,
                    onValueChange = { signUpPhone = it },
                    label = { Text("Mobile Number (e.g. 9876543210)", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = M3Primary,
                        unfocusedBorderColor = M3Outline,
                        focusedContainerColor = M3SurfaceContainer,
                        unfocusedContainerColor = M3SurfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = signUpEmail,
                    onValueChange = { signUpEmail = it },
                    label = { Text("Email Address (Optional)", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = M3Primary,
                        unfocusedBorderColor = M3Outline,
                        focusedContainerColor = M3SurfaceContainer,
                        unfocusedContainerColor = M3SurfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = signUpPassword,
                    onValueChange = { signUpPassword = it },
                    label = { Text("Password (min 12 characters)", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = M3Primary,
                        unfocusedBorderColor = M3Outline,
                        focusedContainerColor = M3SurfaceContainer,
                        unfocusedContainerColor = M3SurfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = signUpConfirmPassword,
                    onValueChange = { signUpConfirmPassword = it },
                    label = { Text("Confirm Password", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = M3Primary,
                        unfocusedBorderColor = M3Outline,
                        focusedContainerColor = M3SurfaceContainer,
                        unfocusedContainerColor = M3SurfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (signUpName.isBlank()) {
                            errorMessage = "Please enter your full name."
                            return@Button
                        }
                        if (signUpPhone.isBlank() && signUpEmail.isBlank()) {
                            errorMessage = "Please enter either a mobile number or an email."
                            return@Button
                        }
                        if (signUpPassword.length < 12) {
                            errorMessage = "Password must be at least 12 characters."
                            return@Button
                        }
                        if (signUpPassword != signUpConfirmPassword) {
                            errorMessage = "Passwords do not match."
                            return@Button
                        }

                        val name = signUpName.trim()
                        val phoneFormatted = if (signUpPhone.isNotBlank()) normalizePhone(signUpPhone) else null
                        val emailClean = if (signUpEmail.isNotBlank()) signUpEmail.trim().lowercase() else null
                        val pwd = signUpPassword

                        signUpName = ""
                        signUpPhone = ""
                        signUpEmail = ""
                        signUpPassword = ""
                        signUpConfirmPassword = ""

                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val res = ApiClient.getService().signupCustomer(
                                    CustomerSignupRequestPayload(
                                        fullName = name,
                                        phoneNumber = phoneFormatted,
                                        email = emailClean,
                                        username = emailClean?.substringBefore("@") ?: ("user_" + (phoneFormatted?.takeLast(4) ?: "guest")),
                                        password = pwd
                                    )
                                )

                                if (res.isSuccessful && res.body()?.accessToken != null) {
                                    val token = res.body()!!.accessToken
                                    val uid = res.body()?.user?.id ?: "usr_customer"
                                    tokenManager.saveAuth(token, uid, phoneFormatted ?: emailClean ?: "customer", "customer")
                                    Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                } else {
                                    val err = res.errorBody()?.string() ?: "Registration failed."
                                    errorMessage = if (err.contains("detail")) err else "Account with this mobile or email may already exist."
                                }
                            } catch (e: Exception) {
                                errorMessage = "Network error: ${e.localizedMessage ?: "Could not reach server"}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = M3Primary, contentColor = M3OnPrimary),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = M3OnPrimary, strokeWidth = 2.5.dp)
                    } else {
                        Text("CREATE ACCOUNT", fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = M3SurfaceContainer,
                modifier = Modifier.padding(4.dp)
            ) {
                Text(
                    text = "🔒 Twilio Verified Secure SMS Authentication Active",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
