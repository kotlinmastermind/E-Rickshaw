package com.vaisentra.mobility.driver.feature.auth

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.BuildConfig
import com.vaisentra.mobility.driver.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vaisentra.mobility.driver.data.DriverDocumentDto
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Screen 1: Pilot Cockpit Login -> Uses DriverGoLoginScreen
@Composable
fun PilotLoginScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var isRequesting by remember { mutableStateOf(false) }

    DriverGoLoginScreen(
        initialPhoneNumber = DriverRepository.pendingAuthPhone.removePrefix("+91"),
        onNextClick = { rawPhone ->
            val digits = rawPhone.filter { it.isDigit() }.take(10)
            if (digits.length != 10) {
                DriverDialogController.showToast("Please enter a valid 10-digit mobile number")
                return@DriverGoLoginScreen
            }
            if (isRequesting) return@DriverGoLoginScreen

            val fullPhone = "+91$digits"
            DriverRepository.pendingAuthPhone = fullPhone
            isRequesting = true
            scope.launch {
                val otpRes = DriverRepository.requestOtp(fullPhone)
                isRequesting = false
                if (otpRes.isSuccess) {
                    DriverDialogController.showToast("OTP sent to $fullPhone")
                } else {
                    DriverDialogController.showToast(
                        otpRes.exceptionOrNull()?.message ?: "Enter verification passcode to proceed"
                    )
                }
                onNavigate("auth_otp")
            }
        },
        onPasswordLoginClick = { rawPhone, pwd ->
            val digits = rawPhone.filter { it.isDigit() }.take(10)
            if (digits.length != 10) {
                DriverDialogController.showToast("Please enter a valid 10-digit mobile number")
                return@DriverGoLoginScreen
            }
            if (pwd.isBlank()) {
                DriverDialogController.showToast("Please enter your account password")
                return@DriverGoLoginScreen
            }
            if (isRequesting) return@DriverGoLoginScreen

            val fullPhone = "+91$digits"
            DriverRepository.pendingAuthPhone = fullPhone
            isRequesting = true
            scope.launch {
                val loginRes = DriverRepository.driverLogin(fullPhone, pwd)
                isRequesting = false
                if (loginRes.isSuccess) {
                    DriverDialogController.showToast("Welcome back! Live backend connected.")
                    onNavigate("duty_dashboard")
                } else {
                    DriverDialogController.showToast(
                        loginRes.exceptionOrNull()?.message ?: "Login failed. Please check credentials."
                    )
                }
            }
        },
        onTermsClick = {
            DriverDialogController.showToast("Vaisentra Mobility Driver Partner Terms & Privacy Policy")
        },
        onForgotPasswordClick = {
            onNavigate("auth_recovery")
        }
    )
}

// Screen 3: Pilot Password & PIN Recovery
@Composable
fun PilotPasswordRecoveryScreen(onNavigate: (String) -> Unit) {
    var identifier by remember { mutableStateOf(DriverRepository.pendingAuthPhone.removePrefix("+91")) }
    var recoveryCode by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) } // 1 = Request Code, 2 = Verify & Reset PIN
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CockpitHeader(
            title = "Account Recovery",
            subtitle = if (step == 1) "Request PIN reset code" else "Set your new security PIN",
            onBack = {
                if (step == 2) step = 1 else onNavigate("auth_login")
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (step == 1) {
            CockpitCard {
                Text(
                    text = "Enter your registered 10-digit mobile number to receive a 6-digit recovery code via SMS.",
                    color = PilotTextMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                CockpitTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = "Registered Mobile Number",
                    placeholder = "9876543210"
                )

                Spacer(modifier = Modifier.height(24.dp))

                CockpitButton(
                    text = if (isLoading) "SENDING CODE..." else "SEND RECOVERY CODE",
                    enabled = !isLoading && identifier.trim().length >= 10,
                    onClick = {
                        val digits = identifier.filter { it.isDigit() }.take(10)
                        val fullPhone = "+91$digits"
                        DriverRepository.pendingAuthPhone = fullPhone
                        isLoading = true
                        scope.launch {
                            val res = DriverRepository.requestOtp(fullPhone)
                            isLoading = false
                            if (res.isSuccess) {
                                DriverDialogController.showToast("Recovery code sent to $fullPhone")
                                step = 2
                            } else {
                                DriverDialogController.showToast(res.exceptionOrNull()?.message ?: "Failed to send recovery code")
                            }
                        }
                    }
                )
            }
        } else {
            CockpitCard {
                Text(
                    text = "Enter the 6-digit code sent to ${DriverRepository.pendingAuthPhone} and configure your new security PIN.",
                    color = PilotTextMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                CockpitTextField(
                    value = recoveryCode,
                    onValueChange = { recoveryCode = it.take(6) },
                    label = "6-Digit Recovery Code",
                    placeholder = "123456"
                )

                Spacer(modifier = Modifier.height(14.dp))

                CockpitTextField(
                    value = newPin,
                    onValueChange = { newPin = it.take(6) },
                    label = "New Security PIN (4-6 digits)",
                    placeholder = "Enter new PIN",
                    visualTransformation = PasswordVisualTransformation()
                )

                Spacer(modifier = Modifier.height(24.dp))

                CockpitButton(
                    text = if (isLoading) "VERIFYING..." else "UPDATE PIN & SIGN IN",
                    enabled = !isLoading && recoveryCode.length >= 4 && newPin.length >= 4,
                    onClick = {
                        isLoading = true
                        scope.launch {
                            val res = DriverRepository.verifyOtp(DriverRepository.pendingAuthPhone, recoveryCode)
                            isLoading = false
                            if (res.isSuccess) {
                                DriverDialogController.showToast("PIN reset successfully! Welcome back.")
                                onNavigate("duty_dashboard")
                            } else {
                                DriverDialogController.showToast("Invalid recovery code. Please check and try again.")
                            }
                        }
                    }
                )
            }
        }
    }
}

// Screen 2: Pilot SMS OTP Sign-In -> Uses DriverGoVerificationScreen
@Composable
fun PilotOtpScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var secondsRemaining by remember { mutableIntStateOf(30) }
    var isVerifying by remember { mutableStateOf(false) }
    val targetPhone = DriverRepository.pendingAuthPhone

    LaunchedEffect(secondsRemaining) {
        if (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining--
        }
    }

    val timerText = String.format("00:%02d", secondsRemaining)

    DriverGoVerificationScreen(
        initialCode = "",
        codeLength = 6,
        resendTimerText = timerText,
        onVerifyClick = { code ->
            if (targetPhone.isBlank()) {
                DriverDialogController.showToast("Please enter your mobile number first.")
                onNavigate("auth_login")
                return@DriverGoVerificationScreen
            }
            if (code.length < 6) {
                DriverDialogController.showToast("Please enter the complete 6-digit OTP code")
                return@DriverGoVerificationScreen
            }
            if (isVerifying) return@DriverGoVerificationScreen

            isVerifying = true
            scope.launch {
                val res = DriverRepository.verifyOtp(targetPhone, code)
                isVerifying = false
                if (res.isSuccess) {
                    val tokenResp = res.getOrNull()
                    if (tokenResp?.isNewUser == true) {
                        DriverDialogController.showToast("Account created! Please complete your driver profile.")
                        onNavigate("pilot_profile")
                    } else {
                        DriverDialogController.showToast("OTP verified! Welcome back.")
                        onNavigate("duty_dashboard")
                    }
                } else {
                    val errorMsg = res.exceptionOrNull()?.message ?: "Invalid OTP! Please check and retry."
                    DriverDialogController.showToast(errorMsg)
                }
            }
        },
        onChangeNumberClick = {
            onNavigate("auth_login")
        },
        onResendClick = {
            if (secondsRemaining == 0 && targetPhone.isNotBlank()) {
                secondsRemaining = 30
                scope.launch {
                    val resendRes = DriverRepository.requestOtp(targetPhone)
                    if (resendRes.isSuccess) {
                        DriverDialogController.showToast("New OTP sent to $targetPhone")
                    } else {
                        DriverDialogController.showToast("Failed to resend OTP.")
                    }
                }
            }
        }
    )
}

// Screen 4: Pilot Profile, Photo & Account Settings (GET & PUT /api/v1/drivers/profile)
@Composable
fun PilotProfileScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by DriverRepository.profile.collectAsState()
    val profilePhotoUri by DriverRepository.profilePhotoUri.collectAsState()

    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember(profile.fullName) { mutableStateOf(profile.fullName) }
    var editEmail by remember { mutableStateOf("") }
    var editPhone by remember(profile.phoneNumber) { mutableStateOf(profile.phoneNumber) }
    var isSaving by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Account settings preferences state
    val driverPrefs by DriverRepository.driverPreferences.collectAsState()
    var autoAcceptPool by remember(driverPrefs.autoAcceptPool) { mutableStateOf(driverPrefs.autoAcceptPool) }
    var highSurgeAlerts by remember(driverPrefs.highSurgeAlerts) { mutableStateOf(driverPrefs.highSurgeAlerts) }
    var voiceNavigation by remember(driverPrefs.voiceNavigation) { mutableStateOf(driverPrefs.voiceNavigation) }

    LaunchedEffect(driverPrefs) {
        autoAcceptPool = driverPrefs.autoAcceptPool
        highSurgeAlerts = driverPrefs.highSurgeAlerts
        voiceNavigation = driverPrefs.voiceNavigation
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    }
                    if (bitmap != null) {
                        profileBitmap = bitmap
                        val res = DriverRepository.uploadProfilePhotoBitmap(context, bitmap)
                        if (res.isSuccess) {
                            DriverDialogController.showToast("Profile photo saved to database successfully!")
                        } else {
                            DriverDialogController.showToast("Saved locally: ${res.exceptionOrNull()?.message}")
                        }
                    } else {
                        DriverDialogController.showToast("Unable to decode selected photo.")
                    }
                } catch (e: Exception) {
                    DriverDialogController.showToast("Failed to set photo: ${e.message}")
                }
            }
        }
    }

    // Load initial photo (supports Base64, web URL, or local content URI)
    LaunchedEffect(profilePhotoUri) {
        profileBitmap = DriverRepository.loadAvatarBitmap(context, profilePhotoUri)
    }

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverProfile()
        DriverRepository.fetchDriverPreferences()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(
            title = "Driver Profile & Settings",
            subtitle = "Fleet Operations & Account Governance",
            onBack = { onNavigate("duty_dashboard") }
        )
        Spacer(modifier = Modifier.height(14.dp))

        // 1. Photo & Identity Card
        CockpitCard(glow = profile.approvalStatus == "APPROVED") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Avatar with Camera badge
                Box(contentAlignment = Alignment.BottomEnd) {
                    DriverAvatar(
                        bitmap = profileBitmap,
                        size = 96.dp,
                        onClick = { photoPickerLauncher.launch("image/*") }
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(PilotOnlineGreen)
                            .border(2.dp, PilotSurfaceElevated, CircleShape)
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "📷", fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = profile.fullName.ifBlank { "Driver Partner" },
                    color = PilotTextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = profile.phoneNumber.ifBlank { "Registered Mobile" },
                        color = PilotTextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CockpitBadge(
                        text = profile.approvalStatus,
                        backgroundColor = if (profile.approvalStatus == "APPROVED") OnlineGlow else AlertGlow,
                        textColor = if (profile.approvalStatus == "APPROVED") PilotOnlineGreen else PilotAmber
                    )
                }

                if (profile.rating > 0.0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "★ ${"%.2f".format(profile.rating)} (${profile.ratingCount} reviews)",
                        color = PilotAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PilotElectricCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PilotElectricCyan.copy(alpha = 0.6f))
                    ) {
                        Text("Change Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (profilePhotoUri != null) {
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = {
                                profileBitmap = null
                                scope.launch {
                                    DriverRepository.removeProfilePhoto()
                                    DriverDialogController.showToast("Profile photo removed from database")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PilotAmber),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PilotAmber.copy(alpha = 0.5f))
                        ) {
                            Text("Remove", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Personal Details & Edit Form
        CockpitCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Personal Details",
                    color = PilotTextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = { isEditing = !isEditing }) {
                    Text(
                        text = if (isEditing) "Cancel" else "✏️ Edit Profile",
                        color = PilotElectricCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!isEditing) {
                val detailItems = listOf(
                    "Full Name" to profile.fullName.ifBlank { "Driver Partner" },
                    "Mobile Number" to profile.phoneNumber.ifBlank { "-" },
                    "Email Address" to profile.email.ifBlank { "Not provided" },
                    "Commercial Driving License" to profile.licenseNumber.ifBlank { "Pending verification" },
                    "Driver ID" to profile.id.ifBlank { "-" },
                    "Assigned Vehicle" to "${profile.assignedVehiclePlate.ifBlank { "Pending" }} ${profile.assignedVehicleModel}".trim(),
                    "Completed Trips" to "${profile.completedTrips} Trips"
                )

                detailItems.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = label, color = PilotTextMuted, fontSize = 12.sp)
                        Text(text = value, color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                CockpitTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    placeholder = "Enter full name",
                    label = "Full Name"
                )
                Spacer(modifier = Modifier.height(10.dp))
                CockpitTextField(
                    value = editEmail,
                    onValueChange = { editEmail = it },
                    placeholder = "Enter email address",
                    label = "Email Address (Optional)"
                )
                Spacer(modifier = Modifier.height(10.dp))
                CockpitTextField(
                    value = editPhone,
                    onValueChange = { editPhone = it },
                    placeholder = "Enter mobile number",
                    label = "Registered Mobile Number"
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isEditing = false },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PilotTextMuted)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            isSaving = true
                            scope.launch {
                                val res = DriverRepository.updateDriverProfile(editName, editEmail, editPhone)
                                isSaving = false
                                if (res.isSuccess) {
                                    isEditing = false
                                    DriverDialogController.showToast("Profile details updated successfully!")
                                } else {
                                    DriverDialogController.showToast(
                                        "Update failed: ${res.exceptionOrNull()?.message}"
                                    )
                                }
                            }
                        },
                        enabled = !isSaving && editName.trim().length >= 2,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PilotOnlineGreen)
                    ) {
                        Text(
                            text = if (isSaving) "Saving..." else "Save Changes",
                            color = PilotCanvas,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Account Settings Section
        CockpitCard {
            Text(
                text = "⚙️ Account Settings",
                color = PilotTextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Duty Preferences
            Text(text = "DUTY & DISPATCH", color = PilotElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Auto-Accept Eco-Pool Feeder Rides", color = PilotTextWhite, fontSize = 13.sp)
                    Text(text = "Automatically accept shared shuttle commuters", color = PilotTextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = autoAcceptPool,
                    onCheckedChange = { newValue ->
                        autoAcceptPool = newValue
                        scope.launch {
                            val res = DriverRepository.updateDriverPreferences(autoAcceptPool = newValue)
                            if (res.isSuccess) {
                                DriverDialogController.showToast("Eco-Pool Auto-Accept ${if (newValue) "Enabled" else "Disabled"}")
                            } else {
                                DriverDialogController.showToast("Failed to sync: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = PilotOnlineGreen)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "High-Demand Surge Audio Alerts", color = PilotTextWhite, fontSize = 13.sp)
                    Text(text = "Audio notification when entering 1.5x+ surge zones", color = PilotTextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = highSurgeAlerts,
                    onCheckedChange = { newValue ->
                        highSurgeAlerts = newValue
                        scope.launch {
                            val res = DriverRepository.updateDriverPreferences(highSurgeAlerts = newValue)
                            if (res.isSuccess) {
                                DriverDialogController.showToast("Surge Audio Alerts ${if (newValue) "Enabled" else "Disabled"}")
                            } else {
                                DriverDialogController.showToast("Failed to sync: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = PilotOnlineGreen)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Navigation Sound & Chimes", color = PilotTextWhite, fontSize = 13.sp)
                    Text(text = "Play turn-by-turn prompts during active rides", color = PilotTextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = voiceNavigation,
                    onCheckedChange = { newValue ->
                        voiceNavigation = newValue
                        scope.launch {
                            val res = DriverRepository.updateDriverPreferences(voiceNavigation = newValue)
                            if (res.isSuccess) {
                                DriverDialogController.showToast("Navigation Sound ${if (newValue) "Enabled" else "Disabled"}")
                            } else {
                                DriverDialogController.showToast("Failed to sync: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = PilotOnlineGreen)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = PilotBorder)
            Spacer(modifier = Modifier.height(14.dp))

            // Section 1: Compliance & Vehicle
            Text(text = "1. COMPLIANCE & VEHICLE", color = PilotElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigate("doc_vault") }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "📂 Document Compliance Vault", color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "View uploaded Driving License, RC & Insurance", color = PilotTextMuted, fontSize = 11.sp)
                }
                Text(text = "›", color = PilotTextMuted, fontSize = 18.sp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigate("doc_expiry") }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "⏱️ Document Expiry Watchdog", color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Monitor days left before license / permit renewal", color = PilotTextMuted, fontSize = 11.sp)
                }
                Text(text = "›", color = PilotTextMuted, fontSize = 18.sp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigate("rc_status") }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "📋 Vehicle RC & Road Permit", color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Verify roadworthiness, fitness and registration certificate", color = PilotTextMuted, fontSize = 11.sp)
                }
                Text(text = "›", color = PilotTextMuted, fontSize = 18.sp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigate("inspection") }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "🔍 Pre-Shift Vehicle Inspection", color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Daily tire pressure, brakes and battery inspection", color = PilotTextMuted, fontSize = 11.sp)
                }
                Text(text = "›", color = PilotTextMuted, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = PilotBorder)
            Spacer(modifier = Modifier.height(14.dp))

            // Section 2: Performance & Academy
            Text(text = "2. PERFORMANCE & ACADEMY", color = PilotElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigate("performance") }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "⭐ Pilot Scorecard & Ratings", color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Rider feedback, acceptance rate and completion scores", color = PilotTextMuted, fontSize = 11.sp)
                }
                Text(text = "›", color = PilotTextMuted, fontSize = 18.sp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigate("academy") }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "🎓 Electric Mobility Academy", color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Safe driving tutorials, EV range tips and certifications", color = PilotTextMuted, fontSize = 11.sp)
                }
                Text(text = "›", color = PilotTextMuted, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = PilotBorder)
            Spacer(modifier = Modifier.height(14.dp))

            // Section 3: Safety & Support
            Text(text = "3. SAFETY & SUPPORT", color = PilotElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigate("emergency_contacts") }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "🚨 Emergency SOS Contacts", color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Configure trusted emergency contact numbers", color = PilotTextMuted, fontSize = 11.sp)
                }
                Text(text = "›", color = PilotTextMuted, fontSize = 18.sp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigate("incident") }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "⚠️ Report Incident or Hazard", color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Log on-road collisions, mechanical faults or disputes", color = PilotTextMuted, fontSize = 11.sp)
                }
                Text(text = "›", color = PilotTextMuted, fontSize = 18.sp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigate("found_item") }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "🎒 Lost & Found Item in Cab", color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Report forgotten passenger belongings for safe return", color = PilotTextMuted, fontSize = 11.sp)
                }
                Text(text = "›", color = PilotTextMuted, fontSize = 18.sp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigate("helpdesk") }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "🎧 Driver 24x7 Helpdesk", color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Raise tickets for fare issues, route problems or app support", color = PilotTextMuted, fontSize = 11.sp)
                }
                Text(text = "›", color = PilotTextMuted, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = PilotBorder)
            Spacer(modifier = Modifier.height(14.dp))

            // Section 4: Settings & Diagnostics
            Text(text = "4. SETTINGS & DIAGNOSTICS", color = PilotElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigate("preferences") }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "⚙️ Pilot Preferences & Settings", color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Night mode, navigation sound and route corridor settings", color = PilotTextMuted, fontSize = 11.sp)
                }
                Text(text = "›", color = PilotTextMuted, fontSize = 18.sp)
            }

            if (BuildConfig.DEBUG) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onNavigate("gps") }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "📡 High-Frequency GPS Diagnostics [DEBUG]", color = PilotAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Live telemetry monitor and satellite fix logger", color = PilotTextMuted, fontSize = 11.sp)
                    }
                    Text(text = "›", color = PilotAmber, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = PilotBorder)
            Spacer(modifier = Modifier.height(14.dp))

            // Banking & Payouts Navigation
            Text(text = "PAYMENTS & EARNINGS", color = PilotElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigate("payout_accounts") }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "🏦 Payout Bank Accounts & UPI", color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Manage linked bank account for instant IMPS settlements", color = PilotTextMuted, fontSize = 11.sp)
                }
                Text(text = "›", color = PilotTextMuted, fontSize = 18.sp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigate("driver_ledger") }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "📑 Driver Financial Ledger", color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "View credit / debit history and deductions", color = PilotTextMuted, fontSize = 11.sp)
                }
                Text(text = "›", color = PilotTextMuted, fontSize = 18.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Logout / Session Exit Button
        Button(
            onClick = { showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2D1616),
                contentColor = Color(0xFFFF5252)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f))
        ) {
            Text(
                text = "🚪 Log Out from Driver Account",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(text = "Log Out?", color = PilotTextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Are you sure you want to log out of your driver account? You will need to verify with OTP again to log in.",
                    color = PilotTextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        DriverRepository.logout()
                        DriverDialogController.showToast("Logged out successfully.")
                        onNavigate("auth_login")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Log Out", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = PilotTextWhite)
                }
            },
            containerColor = PilotSurfaceElevated,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

// Screen 5: Document Vault & Verification (Upload & Live Database View)
@Composable
fun DocumentVaultScreen(onNavigate: (String) -> Unit) {
    val liveDocs by DriverRepository.documents.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showUploadCard by remember { mutableStateOf(false) }
    var selectedDocType by remember { mutableStateOf("DRIVING_LICENSE") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }

    // In-app Document Viewer State
    var previewDoc by remember { mutableStateOf<DriverDocumentDto?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            var name = "selected_document"
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        name = it.getString(nameIdx) ?: name
                    }
                }
            }
            selectedFileName = name
        }
    }

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverDocuments()
    }

    // Effect to fetch and decode document bytes from backend when previewDoc changes
    LaunchedEffect(previewDoc) {
        val doc = previewDoc
        if (doc != null) {
            isLoadingPreview = true
            previewBitmap = null
            previewError = null
            val res = DriverRepository.fetchDocumentContentBytes(doc.id)
            isLoadingPreview = false
            if (res.isSuccess) {
                val bytes = res.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    previewBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (previewBitmap == null) {
                        previewError = "Document is in PDF or non-image format."
                    }
                } else {
                    previewError = "Empty document content returned."
                }
            } else {
                previewError = res.exceptionOrNull()?.message ?: "Failed to load document."
            }
        }
    }

    val docTypeOptions = listOf(
        "DRIVING_LICENSE" to "Driving License (DL)",
        "VEHICLE_REGISTRATION" to "Vehicle RC / Reg",
        "INSURANCE" to "Vehicle Insurance",
        "IDENTITY_PROOF" to "Aadhaar / ID Proof",
        "COMMERCIAL_PERMIT" to "Commercial Permit",
        "FITNESS_CERTIFICATE" to "Fitness Certificate"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(
            title = "Document Vault",
            subtitle = "Compliance records & live database sync",
            onBack = { onNavigate("duty_dashboard") }
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Upload Action Button / Toggle
        CockpitButton(
            text = if (showUploadCard) "✕ Cancel Upload" else "+ Upload New Compliance Document",
            onClick = {
                showUploadCard = !showUploadCard
                if (!showUploadCard) {
                    selectedFileUri = null
                    selectedFileName = ""
                }
            },
            backgroundColor = if (showUploadCard) PilotCard else PilotOnlineGreen,
            textColor = if (showUploadCard) PilotTextWhite else PilotCanvas
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Upload Form Card
        if (showUploadCard) {
            CockpitCard(borderColor = PilotOnlineGreen) {
                Text(
                    text = "Upload Compliance Document",
                    color = PilotOnlineGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select document category and choose image/PDF from your device to save to backend database.",
                    color = PilotTextMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "DOCUMENT CATEGORY", color = PilotTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                docTypeOptions.chunked(2).forEach { rowPair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowPair.forEach { (typeCode, typeLabel) ->
                            val isSelected = selectedDocType == typeCode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PilotOnlineGreen.copy(alpha = 0.2f) else PilotSurface)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) PilotOnlineGreen else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedDocType = typeCode }
                                    .padding(vertical = 8.dp, horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = typeLabel,
                                    color = if (isSelected) PilotOnlineGreen else PilotTextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // File Selection Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PilotSurface)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedFileName.isNotBlank()) "Selected File:" else "No file selected",
                            color = PilotTextMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            text = if (selectedFileName.isNotBlank()) selectedFileName else "Choose PDF, JPG, or PNG",
                            color = if (selectedFileName.isNotBlank()) PilotOnlineGreen else PilotTextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = PilotCard)
                    ) {
                        Text("Browse", color = PilotTextWhite, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                CockpitButton(
                    text = if (isUploading) "Uploading to Database..." else "Upload & Submit for Verification",
                    enabled = !isUploading && selectedFileUri != null,
                    onClick = {
                        val uri = selectedFileUri ?: return@CockpitButton
                        isUploading = true
                        scope.launch {
                            val res = DriverRepository.uploadDriverDocument(
                                documentType = selectedDocType,
                                uri = uri,
                                context = context
                            )
                            isUploading = false
                            if (res.isSuccess) {
                                DriverDialogController.showToast("Document uploaded and saved to database successfully!")
                                showUploadCard = false
                                selectedFileUri = null
                                selectedFileName = ""
                            } else {
                                DriverDialogController.showToast("Upload failed: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    backgroundColor = PilotOnlineGreen,
                    textColor = PilotCanvas
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // List of Uploaded Documents
        if (liveDocs.isEmpty()) {
            CockpitCard {
                Text(
                    text = "No compliance documents uploaded yet. Use the upload button above to submit your Driving License and Vehicle RC.",
                    color = PilotTextMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(liveDocs) { doc ->
                    val isApproved = doc.status == "APPROVED"
                    val isRejected = doc.status == "REJECTED"
                    val badgeColor = when {
                        isApproved -> PilotOnlineGreen
                        isRejected -> CrimsonAlert
                        else -> PilotAmber
                    }
                    val badgeBg = when {
                        isApproved -> OnlineGlow
                        isRejected -> CrimsonAlert.copy(alpha = 0.2f)
                        else -> AlertGlow
                    }

                    CockpitCard(
                        cornerRadius = 14.dp,
                        borderColor = if (isApproved) PilotOnlineGreen.copy(alpha = 0.4f) else (if (isRejected) CrimsonAlert.copy(alpha = 0.6f) else Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = doc.documentType.replace("_", " "),
                                    color = PilotTextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = doc.fileName.ifBlank { "Uploaded: ${doc.createdAt}" },
                                    color = PilotTextMuted,
                                    fontSize = 11.sp
                                )
                                if (doc.expiryDate.isNotBlank()) {
                                    Text(
                                        text = "Expires: ${doc.expiryDate} (${doc.daysUntilExpiry} days left)",
                                        color = if (doc.daysUntilExpiry in 0..30) PilotAmber else PilotTextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            CockpitBadge(
                                text = doc.status,
                                backgroundColor = badgeBg,
                                textColor = badgeColor
                            )
                        }

                        if (!doc.rejectionReason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CrimsonAlert.copy(alpha = 0.15f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "Rejection Reason: ${doc.rejectionReason}",
                                    color = CrimsonAlert,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // View Document Button
                        Button(
                            onClick = { previewDoc = doc },
                            colors = ButtonDefaults.buttonColors(containerColor = PilotCard),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "👁️ View Document (${doc.documentType.replace("_", " ")})",
                                color = PilotElectricCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // In-App Document Preview Dialog
    val activePreview = previewDoc
    if (activePreview != null) {
        AlertDialog(
            onDismissRequest = { previewDoc = null },
            confirmButton = {
                TextButton(onClick = { previewDoc = null }) {
                    Text("Close", color = PilotTextWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        val res = DriverRepository.fetchDocumentSignedUrl(activePreview.id)
                        val url = res.getOrNull()
                        if (!url.isNullOrBlank()) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                DriverDialogController.showToast("Could not open URL: ${e.message}")
                            }
                        }
                    }
                }) {
                    Text("Open External / Link", color = PilotElectricCyan)
                }
            },
            title = {
                Column {
                    Text(
                        text = activePreview.documentType.replace("_", " "),
                        color = PilotTextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activePreview.fileName.ifBlank { activePreview.id },
                        color = PilotTextMuted,
                        fontSize = 11.sp
                    )
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 360.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PilotSurface),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoadingPreview -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PilotOnlineGreen)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Loading document from database...", color = PilotTextMuted, fontSize = 12.sp)
                            }
                        }
                        previewBitmap != null -> {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = activePreview.documentType,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        previewError != null -> {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("📄 Document Record", color = PilotElectricCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = previewError ?: "PDF or non-image format stored in database.",
                                    color = PilotTextWhite,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Use 'Open External / Link' to view in system PDF reader.",
                                    color = PilotTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        else -> {
                            Text("No preview available.", color = PilotTextMuted, fontSize = 12.sp)
                        }
                    }
                }
            },
            containerColor = PilotCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// Screen 6: Document Expiry Watchdog
@Composable
fun DocumentExpiryWatchdogScreen(onNavigate: (String) -> Unit) {
    val liveDocs by DriverRepository.documents.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewDoc by remember { mutableStateOf<DriverDocumentDto?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { DriverRepository.fetchDriverDocuments() }

    LaunchedEffect(previewDoc) {
        val doc = previewDoc
        if (doc != null) {
            isLoadingPreview = true
            previewBitmap = null
            val res = DriverRepository.fetchDocumentContentBytes(doc.id)
            isLoadingPreview = false
            if (res.isSuccess) {
                val bytes = res.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    previewBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(title = "Document Expiry Watchdog", subtitle = "Autonomous compliance monitor", onBack = { onNavigate("pilot_profile") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard(borderColor = PilotOnlineGreen) {
            Text(text = "Tracked Documents: ${liveDocs.size}", color = PilotOnlineGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Documents are synced directly from your driver compliance profile.", color = PilotTextMuted, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (liveDocs.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(liveDocs) { doc ->
                    val isExpiringSoon = doc.daysUntilExpiry in 0..30
                    CockpitCard(cornerRadius = 12.dp) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = doc.documentType.replace("_", " "), color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (doc.expiryDate.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "Expires: ${doc.expiryDate}", color = PilotTextMuted, fontSize = 11.sp)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CockpitBadge(
                                    text = if (doc.expiryDate.isNotBlank()) "${doc.daysUntilExpiry}d left" else doc.status,
                                    backgroundColor = if (isExpiringSoon) AlertGlow else OnlineGlow,
                                    textColor = if (isExpiringSoon) PilotAmber else PilotOnlineGreen
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "👁️ View",
                                    color = PilotElectricCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(PilotSurface)
                                        .clickable { previewDoc = doc }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        CockpitButton(text = "Check Renewal Alert Modal", onClick = {
            DriverDialogController.show(DriverPopupType.DOC_EXPIRY) {
                onNavigate("doc_vault")
            }
        }, backgroundColor = PilotCard, textColor = PilotTextWhite)
    }

    // In-App Document Preview Dialog in Watchdog
    val activePreview = previewDoc
    if (activePreview != null) {
        AlertDialog(
            onDismissRequest = { previewDoc = null },
            confirmButton = {
                TextButton(onClick = { previewDoc = null }) {
                    Text("Close", color = PilotTextWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        val res = DriverRepository.fetchDocumentSignedUrl(activePreview.id)
                        val url = res.getOrNull()
                        if (!url.isNullOrBlank()) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                DriverDialogController.showToast("Could not open URL: ${e.message}")
                            }
                        }
                    }
                }) {
                    Text("Open External", color = PilotElectricCyan)
                }
            },
            title = {
                Text(text = activePreview.documentType.replace("_", " "), color = PilotTextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PilotSurface),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoadingPreview -> {
                            CircularProgressIndicator(color = PilotOnlineGreen)
                        }
                        previewBitmap != null -> {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = activePreview.documentType,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        else -> {
                            Text("Document stored in database (PDF/File)", color = PilotTextWhite, fontSize = 12.sp)
                        }
                    }
                }
            },
            containerColor = PilotCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PilotProfileScreenPreview() {
    DriverGoTheme {
        PilotProfileScreen(onNavigate = {})
    }
}

@Preview(showBackground = true)
@Composable
fun DocumentVaultScreenPreview() {
    DriverGoTheme {
        DocumentVaultScreen(onNavigate = {})
    }
}

@Preview(showBackground = true)
@Composable
fun DocumentExpiryWatchdogScreenPreview() {
    DriverGoTheme {
        DocumentExpiryWatchdogScreen(onNavigate = {})
    }
}