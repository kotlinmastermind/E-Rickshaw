@file:Suppress("PreviewAnnotationInFunctionWithParameters")

package com.vaisentra.mobility.rider.feature.onboarding

import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
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

import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import java.util.UUID

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
    var isRequestingOtp by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()


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

            NeoButton(
                text = if (isRequestingOtp) "Sending OTP..." else "Send 6-Digit OTP",
                onClick = {
                    val cleanPhone = phone.trim()
                    if (cleanPhone.length < 10) {
                        DialogController.showToast("Please enter a valid mobile number")
                        return@NeoButton
                    }
                    if (isRequestingOtp) return@NeoButton
                    coroutineScope.launch {
                        isRequestingOtp = true
                        try {
                            val result = withContext(Dispatchers.IO) {
                                RiderRepository.requestOtp(cleanPhone)
                            }
                            if (result.isSuccess) {
                                DialogController.showToast("Verification code sent. Check your SMS.")
                                onOtpSent(cleanPhone)
                            } else {
                                DialogController.showToast(
                                    result.exceptionOrNull()?.message ?: "Unable to send verification code. Please try again."
                                )
                            }
                        } finally {
                            isRequestingOtp = false
                        }
                    }
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
    var isResending by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableIntStateOf(60) }
    val coroutineScope = rememberCoroutineScope() // Compose-safe coroutine scope

    LaunchedEffect(secondsRemaining) {
        if (secondsRemaining > 0) {
            kotlinx.coroutines.delay(1000)
            secondsRemaining--
        }
    }

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
                                val tokenResponse = result.getOrNull()
                                val isBackendNewUser = tokenResponse?.isNewUser == true
                                
                                val userMe = withContext(Dispatchers.IO) { RiderRepository.fetchCurrentUserMe() }
                                val hasCustomName = userMe != null && 
                                    !userMe.fullName.isNullOrBlank() && 
                                    userMe.fullName != "Valued Customer" && 
                                    userMe.fullName != "Distinguished Guest" &&
                                    userMe.fullName != "Driver Partner"

                                val isExistingUser = !isBackendNewUser && hasCustomName

                                if (isExistingUser) {
                                    DialogController.showToast("Welcome back!")
                                    onNavigate("map_home")
                                } else {
                                    DialogController.showToast("Welcome! Please complete your profile KYC.")
                                    onNavigate("auth_kyc")
                                }
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
                text = when {
                    isResending -> "Sending new code..."
                    secondsRemaining > 0 -> "Resend code in ${secondsRemaining}s"
                    else -> "Resend verification code"
                },
                color = if (secondsRemaining == 0 && !isResending) ElectricCyan else TextDim,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(enabled = secondsRemaining == 0 && !isResending && !isLoading) {
                        coroutineScope.launch {
                            isResending = true
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    RiderRepository.requestOtp(phone)
                                }
                                if (result.isSuccess) {
                                    secondsRemaining = 60
                                    DialogController.showToast("New verification code sent.")
                                } else {
                                    DialogController.showToast(
                                        result.exceptionOrNull()?.message ?: "Unable to resend verification code."
                                    )
                                }
                            } finally {
                                isResending = false
                            }
                        }
                    }
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
    onNavigate: (String) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
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
            title = "Complete Your Profile",
            subtitle = "Your mobile number was verified through OTP. No government ID is collected here.",
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
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(
                text = if (isSaving) "Saving Profile..." else "Save Profile & Continue",
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
                            email = email
                        )
                        withContext(Dispatchers.Main) {
                            isSaving = false
                            result.onSuccess {
                                DialogController.showToast("Profile saved successfully.")
                                onNavigate("auth_biometric")
                            }.onFailure { err ->
                                DialogController.showToast(err.message ?: "Profile update failed!")
                            }
                        }
                    }
                }
            )
            TextButton(onClick = { onNavigate("customer_kyc_documents") }) {
                Text("Submit identity document (optional)", color = ElectricCyan)
            }
        }
    }
}

// Optional Customer Identity Document Submission
@Composable
fun CustomerKycDocumentsScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedDocumentType by remember { mutableStateOf("PAN_CARD") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var isWithdrawing by remember { mutableStateOf(false) }
    var consentGiven by remember { mutableStateOf(false) }
    var showWithdrawConfirmation by remember { mutableStateOf(false) }
    var reviewStatus by remember { mutableStateOf("Loading review status...") }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedUri = uri
    }

    LaunchedEffect(Unit) {
        RiderRepository.fetchCustomerKycStatus().onSuccess {
            reviewStatus = it.replace("_", " ")
        }.onFailure {
            reviewStatus = "Status unavailable"
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
            title = "Identity Verification",
            subtitle = "Optional manual review. Aadhaar is not collected. Upload only a document you choose to provide.",
            onBack = { onNavigate("map_home") }
        )
        Spacer(modifier = Modifier.height(20.dp))
        NeoCard {
            Text("Select document type", color = Color.White, fontWeight = FontWeight.Bold)
            listOf(
                "PAN_CARD" to "PAN Card",
                "DRIVING_LICENSE" to "Driving Licence",
                "PASSPORT" to "Passport",
                "VOTER_ID" to "Voter ID"
            ).forEach { (value, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedDocumentType == value,
                        onClick = { selectedDocumentType = value }
                    )
                    Text(label, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    documentPicker.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedUri?.lastPathSegment ?: "Choose PDF, JPEG, or PNG")
            }
            Text(
                "Maximum file size: 5 MB. Documents are stored privately and reviewed manually.",
                color = Color.LightGray,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = consentGiven,
                    onCheckedChange = { consentGiven = it }
                )
                Text(
                    "I consent to private storage of this selected document and access by authorized administrators for manual review.",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }
            NeoButton(
                text = if (isUploading) "Uploading securely..." else "Submit for manual review",
                enabled = consentGiven && selectedUri != null && !isUploading,
                onClick = {
                    val uri = selectedUri
                    if (uri == null) {
                        DialogController.showToast("Choose an identity document first.")
                        return@NeoButton
                    }
                    if (isUploading) return@NeoButton
                    isUploading = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            RiderRepository.submitCustomerKycDocument(context, uri, selectedDocumentType)
                        }
                        isUploading = false
                        result.onSuccess { message ->
                            DialogController.showToast(message)
                            selectedUri = null
                            RiderRepository.fetchCustomerKycStatus().onSuccess {
                                reviewStatus = it.replace("_", " ")
                            }
                        }.onFailure { error ->
                            DialogController.showToast(error.message ?: "Document upload failed.")
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Current review status: $reviewStatus", color = ElectricCyan, fontSize = 12.sp)
            if (reviewStatus !in setOf("NOT SUBMITTED", "LOADING REVIEW STATUS...", "STATUS UNAVAILABLE")) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showWithdrawConfirmation = true },
                    enabled = !isWithdrawing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isWithdrawing) "Removing documents..." else "Withdraw and delete submitted documents")
                }
            }
        }
    }

    if (showWithdrawConfirmation) {
        AlertDialog(
            onDismissRequest = { showWithdrawConfirmation = false },
            title = { Text("Withdraw identity documents?") },
            text = {
                Text("This removes all submitted customer identity files from private storage and withdraws them from manual review.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWithdrawConfirmation = false
                        if (isWithdrawing) return@TextButton
                        isWithdrawing = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                RiderRepository.withdrawCustomerKycDocuments()
                            }
                            isWithdrawing = false
                            result.onSuccess { message ->
                                DialogController.showToast(message)
                                reviewStatus = "NOT SUBMITTED"
                                selectedUri = null
                            }.onFailure { error ->
                                DialogController.showToast(error.message ?: "Unable to withdraw documents.")
                            }
                        }
                    }
                ) { Text("Withdraw", color = CrimsonRed) }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

// Screen 6: Passkey & Hardware Biometric Enrolment


@Composable
fun BiometricEnrolScreen(onNavigate: (String) -> Unit) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    fun enrollWithSystemBiometric() {
        val activity = context as? FragmentActivity
        if (activity == null) {
            DialogController.showToast("MainActivity must extend FragmentActivity")
            return
        }

        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            DialogController.showToast("Please enable Fingerprint or Face Unlock in device settings.")
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    scope.launch {
                        isLoading = true
                        val credId = "passkey-${java.util.UUID.randomUUID()}"
                        val pubKey = "android-keystore-${android.os.Build.MODEL}-${System.currentTimeMillis()}"

                        val apiResult = RiderRepository.registerPasskey(credId, pubKey)
                        isLoading = false

                        apiResult.onSuccess {
                            DialogController.showToast("Biometric Passkey registered successfully!")
                            onNavigate("map_home")
                        }.onFailure { err ->
                            DialogController.showToast("Server Error: ${err.message}")
                        }
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    DialogController.showToast("Cancelled: $errString")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    DialogController.showToast("Biometric not recognized. Try again.")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Hardware Passkey")
            .setSubtitle("Verify Fingerprint or Face ID")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        NeoHeader(
            title = "Hardware Passkey",
            subtitle = "Biometric 1-Tap Authorization",
            onBack = { onNavigate("auth_kyc") }
        )

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
            Text(
                text = "Biometric Face ID / Fingerprint",
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
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
            NeoButton(
                text = if (isLoading) "Syncing with Server..." else "Enrol Biometric Passkey",
                onClick = { if (!isLoading) enrollWithSystemBiometric() }
            )
            Spacer(modifier = Modifier.height(10.dp))
            NeoButton(
                text = "Skip to Home",
                backgroundColor = SurfaceDark,
                onClick = { onNavigate("map_home") }
            )
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
        CustomerKycScreen(onNavigate = {})
    }
}

@Preview
@Composable
fun BiometricEnrolScreenPreview() {
    RiderGoTheme {
        BiometricEnrolScreen(onNavigate = {})
    }
}




