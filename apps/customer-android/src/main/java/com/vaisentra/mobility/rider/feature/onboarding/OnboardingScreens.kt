@file:Suppress("PreviewAnnotationInFunctionWithParameters")

package com.vaisentra.mobility.rider.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.rider.components.*
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Screen 1: Brand Splash & Locale Switcher

@Composable
fun BrandSplashScreen(onNavigate: (String) -> Unit = {}) {
    var selectedLocale by remember { mutableStateOf("en") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(SapphireGlow),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⚡", fontSize = 48.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "RiderGo",
                color = TextWhite,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Eco-Transit & Electric Micro-Mobility OS",
                color = TextMuted,
                fontSize = 14.sp
            )
        }

       /* NeoCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Select Preferred Language:", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            val locales = listOf(
                "en" to "English (Default)",
                "hi" to "हिंदी (Hindi)",
                "mr" to "मराठी (Marathi)",
                "gu" to "ગુજરાતી (Gujarati)"
            )
            locales.forEach { (code, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedLocale == code) SapphirePrimary.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { selectedLocale = code }
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = name, color = if (selectedLocale == code) ElectricCyan else TextWhite, fontSize = 14.sp)
                    if (selectedLocale == code) {
                        Text(text = "✓", color = ElectricCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
*/
        Column(modifier = Modifier.fillMaxWidth()) {
            NeoButton(
                text = "Get Started",
                onClick = { onNavigate("auth_phone") },
                icon = "🚀"
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Screen 2: Phone & Cyber Auth

@Composable
fun PhoneAuthScreen(onNavigate: (String) -> Unit,onOtpSent: (String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Sign In / Sign Up", subtitle = "Secure FastApi & Supabase Auth", onBack = { onNavigate("splash") })
        Spacer(modifier = Modifier.height(24.dp))

        NeoCard {
            Text(text = "Mobile Phone Number", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            NeoTextField(
                value = phone,
                onValueChange = { phone = it },
                placeholder = "+91 XXXXX XXXXX",
                leadingIcon = "📱",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Password (Optional for OTP)", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            NeoTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "••••••••",
                leadingIcon = "🔒",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(
                text = "Send 6-Digit OTP",
                onClick = {
                    val cleanPhone = phone.trim()
                    if (cleanPhone.length < 10) {
                        DialogController.showToast("Please enter a valid 10-digit mobile number")
                        return@NeoButton
                    }
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        RiderRepository.requestOtp(phone)
                    }
                    DialogController.showToast("OTP code requested via Twilio for $cleanPhone")
                    // Phone number ke saath OtpVerifyScreen par bhejein
                    onOtpSent(cleanPhone)

                },
                icon = "📩"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Reset Password / PIN?",
                color = ElectricCyan,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onNavigate("auth_reset") }
            )
            Text(
                text = "New User KYC?",
                color = ElectricCyan,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onNavigate("auth_kyc") }
            )
        }
    }
}

// Screen 3: SMS OTP Quick Login

@Composable
fun OtpVerifyScreen(
    phone: String ,
    onNavigate: (String) -> Unit
) {
    var otpCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope() // Compose-safe coroutine scope

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(24.dp)
    ) {
        NeoHeader(
            title = "Verification Code",
            subtitle = "Enter 6-digit security code",
            onBack = { onNavigate("auth_phone") }
        )
        Spacer(modifier = Modifier.height(30.dp))

        NeoCard {
            Text(
                text = "Code sent via Twilio / Supabase to +91 $phone",
                color = TextMuted,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(20.dp))

            // 6-digit OTP ke liye otpLength = 6 set kiya hai
            OtpInputBox(
                otp = otpCode,
                onOtpChange = { otpCode = it },
                otpLength = 6
            )

            Spacer(modifier = Modifier.height(24.dp))

            NeoButton(
                text = if (isLoading) "Verifying..." else "Verify & Access Fleet",
                onClick = {
                    // 1. Pehle check karein ki 6 digits poore type hue hain ya nahi
                    if (otpCode.length < 6) {
                        DialogController.showToast("Please enter 6-digit OTP")
                        return@NeoButton
                    }

                    if (isLoading) return@NeoButton // Double click rokne ke liye

                    coroutineScope.launch {
                        isLoading = true
                        try {
                            // 2. IO thread par OTP verify karein aur result ka wait karein
                            val result = withContext(Dispatchers.IO) {
                                RiderRepository.verifyOtp(phone, otpCode)
                            }

                            // 3. Agar OTP sahi hai tabhi navigate karein
                            if (result.isSuccess) {
                                DialogController.showToast("Authentication Successful! Session issued.")
                                onNavigate("map_home")
                            } else {
                                val errorMsg = result.exceptionOrNull()?.message ?: "Invalid OTP! Please try again."
                                DialogController.showToast(errorMsg)
                                otpCode = "" // Galat hone par boxes khali kar dein
                            }
                        } catch (e: Exception) {
                            DialogController.showToast("Error: ${e.message ?: "Verification failed"}")
                        } finally {
                            isLoading = false
                        }
                    }
                },
                backgroundColor = EmeraldGreen,
                icon = "✓"
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Resend Code in 28s",
                color = TextDim,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

// Screen 4: Password & PIN Reset
@Composable
fun PasswordResetScreen(onNavigate: (String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(24.dp)
    ) {
        NeoHeader(title = "Reset Credentials", subtitle = "PIN recovery & passkey reconfiguration", onBack = { onNavigate("auth_phone") })
        Spacer(modifier = Modifier.height(20.dp))

        NeoCard {
            NeoTextField(value = phone, onValueChange = { phone = it }, placeholder = "Registered Phone", label = "Registered Phone")
            Spacer(modifier = Modifier.height(14.dp))
            NeoTextField(value = newPin, onValueChange = { newPin = it }, placeholder = "New 4-Digit Security PIN", label = "New PIN", visualTransformation = PasswordVisualTransformation())
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(text = "Update Security Password", onClick = {
                val resetVal = if (newPin.isNotBlank()) newPin else "1234"
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    RiderRepository.resetForgotPassword(resetVal, resetVal)
                }
                DialogController.showToast("Password successfully reset via backend recovery!")
                onNavigate("auth_phone")
            })
        }
    }
}

// Screen 5: Customer Registration & KYC Profile

@Composable
fun CustomerKycScreen(
    prefilledPhone: String = "",
    onNavigate: (String) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(prefilledPhone) }
    var govtIdNumber by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Agar user pehle se logged in hai toh uska current data fetch karein
    LaunchedEffect(Unit) {
        val me = withContext(Dispatchers.IO) {
            RiderRepository.fetchCurrentUserMe()
        }
        if (me != null) {
            if (!me.fullName.isNullOrBlank() && me.fullName != "Valued Customer") {
                fullName = me.fullName
            }
            if (!me.email.isNullOrBlank()) {
                email = me.email
            }
            if (!me.phoneNumber.isNullOrBlank() && phone.isBlank()) {
                phone = me.phoneNumber
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(
            title = "Customer Profile & KYC",
            subtitle = "Verified commuter account",
            onBack = { onNavigate("auth_phone") }
        )
        Spacer(modifier = Modifier.height(20.dp))

        NeoCard {
            NeoTextField(
                value = fullName,
                onValueChange = { fullName = it },
                placeholder = "Enter Full Legal Name",
                label = "Full Name"
            )
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = "Enter Email Address",
                label = "Email"
            )
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(
                value = phone,
                onValueChange = { phone = it },
                placeholder = "+91 XXXXX XXXXX",
                label = "Registered Mobile Number"
            )
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(
                value = govtIdNumber,
                onValueChange = { govtIdNumber = it },
                placeholder = "Enter 12-Digit Govt ID",
                label = "Govt ID / Aadhaar"
            )
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(
                text = if (isSaving) "Syncing KYC..." else "Save KYC & Continue",
                onClick = {
                    if (fullName.trim().length < 2) {
                        DialogController.showToast("Please enter your full name")
                        return@NeoButton
                    }
                    if (isSaving) return@NeoButton
                    isSaving = true

                    coroutineScope.launch(Dispatchers.IO) {
                        val result = RiderRepository.registerCustomerProfile(
                            name = fullName,
                            email = email,
                            phone = phone,
                            govtId = govtIdNumber
                        )
                        withContext(Dispatchers.Main) {
                            isSaving = false
                            result.onSuccess {
                                DialogController.showToast("Profile and KYC synced with Supabase!")
                                onNavigate("auth_biometric")
                            }.onFailure { err ->
                                DialogController.showToast(err.message ?: "KYC update failed!")
                            }
                        }
                    }
                }
            )
        }
    }
}
// Screen 6: Passkey & Hardware Biometric Enrolment
@Composable
fun BiometricEnrolScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        NeoHeader(title = "Hardware Passkey", subtitle = "Biometric 1-Tap Authorization", onBack = { onNavigate("auth_kyc") })

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(SapphireGlow),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🛡️", fontSize = 54.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Biometric Face ID / Fingerprint", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Encrypt ride tokens in Android Keystore with zero SMS delays.",
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            NeoButton(text = "Enrol Biometric Passkey", onClick = {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    RiderRepository.registerPasskey("passkey-hw-cred-01", "secp256r1-public-key-blob")
                }
                DialogController.showToast("Biometric Passkey registered with Supabase & Android Keystore!")
                onNavigate("map_home")
            })
            Spacer(modifier = Modifier.height(10.dp))
            NeoButton(text = "Skip to Home", backgroundColor = SurfaceDark, onClick = { onNavigate("map_home") })
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


// Screen 7: App Lock PIN Verification Screen
@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit,
    onForgotPinLogout: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(SapphireGlow),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🔐", fontSize = 40.sp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "RiderGo Security Lock",
            color = TextWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Enter your 4-digit App Lock PIN to continue",
            color = TextMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(28.dp))

        NeoCard {
            NeoTextField(
                value = pinInput,
                onValueChange = { input ->
                    if (input.length <= 4) {
                        pinInput = input.filter { it.isDigit() }
                    }
                },
                placeholder = "••••",
                label = "4-Digit Security PIN",
                leadingIcon = "🔢",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(
                text = if (isVerifying) "Verifying PIN..." else "Unlock App",
                backgroundColor = EmeraldGreen,
                icon = "🔓",
                onClick = {
                    if (pinInput.length != 4) {
                        DialogController.showToast("Please enter your 4-digit PIN")
                        return@NeoButton
                    }
                    if (isVerifying) return@NeoButton
                    isVerifying = true

                    coroutineScope.launch(Dispatchers.IO) {
                        val result = RiderRepository.verifyAppLockPin(pinInput)
                        withContext(Dispatchers.Main) {
                            isVerifying = false
                            result.onSuccess {
                                DialogController.showToast("App Unlocked!")
                                onUnlocked()
                            }.onFailure { err ->
                                pinInput = ""
                                DialogController.showToast(err.message ?: "Invalid Security PIN!")
                            }
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Forgot PIN? Sign in again via OTP",
            color = ElectricCyan,
            fontSize = 13.sp,
            modifier = Modifier.clickable { onForgotPinLogout() }
        )
    }
}
@Preview
@Composable
fun PhoneAuthScreenPreview() {
    RiderGoTheme {
        PhoneAuthScreen(onNavigate = {},{})
        BiometricEnrolScreen {  }
        CustomerKycScreen {  }
        PasswordResetScreen {  }
        OtpVerifyScreen("7562084184") {  }
    }
}


@Preview
@Composable
fun PasswordResetScreenPreview() {
    RiderGoTheme {
        PasswordResetScreen(onNavigate = {})
    }
}

@Preview
@Composable
fun AppLockScreenPreview() {
    RiderGoTheme {
        AppLockScreen(
            onUnlocked = {},
            onForgotPinLogout = {}
        )
    }
}

@Preview
@Composable
fun CustomerKycScreenPreview() {
    RiderGoTheme {
        CustomerKycScreen("",onNavigate = {})
    }
}



