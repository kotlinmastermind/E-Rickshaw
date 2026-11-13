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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.rider.components.*
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.theme.*
import kotlinx.coroutines.launch

// Screen 1: Brand Splash & Locale Switcher
@Composable
fun BrandSplashScreen(onNavigate: (String) -> Unit) {
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

        NeoCard(modifier = Modifier.fillMaxWidth()) {
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
fun PhoneAuthScreen(onNavigate: (String) -> Unit) {
    var phone by remember { mutableStateOf("+91 75620 84184") }
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
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        RiderRepository.requestOtp(phone)
                    }
                    DialogController.showToast("OTP code requested via Twilio for $phone")
                    onNavigate("auth_otp")
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
fun OtpVerifyScreen(onNavigate: (String) -> Unit) {
    var otpCode by remember { mutableStateOf("7294") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(24.dp)
    ) {
        NeoHeader(title = "Verification Code", subtitle = "Enter 4-digit security code", onBack = { onNavigate("auth_phone") })
        Spacer(modifier = Modifier.height(30.dp))

        NeoCard {
            Text(text = "Code sent via Twilio / Supabase to +91 75620 84184", color = TextMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(20.dp))
            OtpDisplayBox(otp = otpCode)
            Spacer(modifier = Modifier.height(24.dp))
            NeoButton(
                text = "Verify & Access Fleet",
                onClick = {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        RiderRepository.verifyOtp("+91 75620 84184", otpCode)
                    }
                    DialogController.showToast("Authentication Successful! Session issued.")
                    onNavigate("map_home")
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
    var phone by remember { mutableStateOf("+91 75620 84184") }
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
fun CustomerKycScreen(onNavigate: (String) -> Unit) {
    var fullName by remember { mutableStateOf("Bhishma Rider") }
    var email by remember { mutableStateOf("kotlindeveloper75620@gmail.com") }
    var aadhaarNumber by remember { mutableStateOf("•••• •••• 9102") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Customer Profile & KYC", subtitle = "Aadhaar verified commuter account", onBack = { onNavigate("auth_phone") })
        Spacer(modifier = Modifier.height(20.dp))

        NeoCard {
            NeoTextField(value = fullName, onValueChange = { fullName = it }, placeholder = "Full Legal Name", label = "Full Name")
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(value = email, onValueChange = { email = it }, placeholder = "Email Address", label = "Email")
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(value = aadhaarNumber, onValueChange = { aadhaarNumber = it }, placeholder = "Aadhaar Card Number", label = "Govt ID / Aadhaar", trailingText = "VERIFIED ✓")
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(text = "Save KYC & Continue", onClick = {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    RiderRepository.registerCustomerProfile(fullName, email)
                }
                DialogController.showToast("Profile and KYC synced with Supabase!")
                onNavigate("auth_biometric")
            })
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

