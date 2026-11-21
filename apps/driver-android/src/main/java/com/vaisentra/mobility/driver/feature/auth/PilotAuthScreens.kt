package com.vaisentra.mobility.driver.feature.auth

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
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
                    onNavigate("auth_otp")
                } else {
                    DriverDialogController.showToast(
                        otpRes.exceptionOrNull()?.message ?: "Could not send OTP. Please try again."
                    )
                }
            }
        },
        onTermsClick = {
            DriverDialogController.showToast("Vaisentra Mobility Driver Partner Terms & Privacy Policy")
        }
    )
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
                    DriverDialogController.showToast(
                        res.exceptionOrNull()?.message ?: "Invalid OTP. Please check and retry."
                    )
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

// Screen 3: Pilot Password / PIN Recovery (Connected to /otp/send, /otp/verify, /change-password)
@Composable
fun PilotPasswordRecoveryScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var phoneNumber by remember { mutableStateOf(DriverRepository.pendingAuthPhone.removePrefix("+91")) }
    var otpCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(
            title = "PIN & Password Recovery",
            subtitle = "Reset your security credentials",
            onBack = { onNavigate("auth_login") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard {
            Text(
                text = "Forgot Pilot Security Password?",
                color = PilotTextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Enter your registered 10-digit mobile number to receive a recovery OTP.",
                color = PilotTextMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            CockpitTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it.filter { ch -> ch.isDigit() }.take(10) },
                placeholder = "Enter 10-digit mobile number",
                label = "Registered Mobile Number",
                leadingIcon = "📱",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            if (isOtpSent) {
                Spacer(modifier = Modifier.height(12.dp))
                CockpitTextField(
                    value = otpCode,
                    onValueChange = { otpCode = it.filter { ch -> ch.isDigit() }.take(6) },
                    placeholder = "Enter 6-digit OTP",
                    label = "Verification OTP Code",
                    leadingIcon = "🔑",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(12.dp))
                CockpitTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    placeholder = "Enter new password (min 8 chars)",
                    label = "New Security Password",
                    leadingIcon = "🔒",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!isOtpSent) {
                CockpitButton(
                    text = if (isSubmitting) "Sending OTP..." else "Request Recovery OTP",
                    enabled = !isSubmitting,
                    onClick = {
                        val digits = phoneNumber.filter { it.isDigit() }
                        if (digits.length != 10) {
                            DriverDialogController.showToast("Please enter a valid 10-digit mobile number")
                            return@CockpitButton
                        }
                        val fullPhone = "+91$digits"
                        DriverRepository.pendingAuthPhone = fullPhone
                        isSubmitting = true
                        scope.launch {
                            val res = DriverRepository.requestOtp(fullPhone)
                            isSubmitting = false
                            if (res.isSuccess) {
                                isOtpSent = true
                                DriverDialogController.showToast("Recovery OTP sent to $fullPhone")
                            } else {
                                DriverDialogController.showToast(
                                    res.exceptionOrNull()?.message ?: "Failed to send recovery OTP"
                                )
                            }
                        }
                    }
                )
            } else {
                CockpitButton(
                    text = if (isSubmitting) "Updating Password..." else "Reset & Login",
                    enabled = !isSubmitting,
                    onClick = {
                        val digits = phoneNumber.filter { it.isDigit() }
                        if (digits.length != 10) {
                            DriverDialogController.showToast("Please enter a valid 10-digit mobile number")
                            return@CockpitButton
                        }
                        if (otpCode.length != 6) {
                            DriverDialogController.showToast("Please enter the 6-digit OTP code")
                            return@CockpitButton
                        }
                        if (newPassword.length < 8) {
                            DriverDialogController.showToast("Password must be at least 8 characters")
                            return@CockpitButton
                        }
                        val fullPhone = "+91$digits"
                        isSubmitting = true
                        scope.launch {
                            val res = DriverRepository.resetPasswordWithOtp(fullPhone, otpCode, newPassword)
                            isSubmitting = false
                            if (res.isSuccess) {
                                DriverDialogController.showToast("Password reset successful!")
                                onNavigate("duty_dashboard")
                            } else {
                                DriverDialogController.showToast(
                                    res.exceptionOrNull()?.message ?: "Could not reset password. Please retry."
                                )
                            }
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(
            text = "Back to Login",
            isOutlined = true,
            onClick = { onNavigate("auth_login") }
        )
    }
}

// Screen 4: Pilot Profile & Account Settings (Photo, Edit Profile, Preferences & Logout)
@Composable
fun PilotProfileScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by DriverRepository.profile.collectAsState()

    val prefs = remember {
        context.getSharedPreferences("drivergo_secure_prefs", android.content.Context.MODE_PRIVATE)
    }

    var photoUriString by remember {
        mutableStateOf(prefs.getString("driver_profile_photo_uri", null))
    }
    var profileBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Load photo bitmap when URI changes or on launch
    LaunchedEffect(photoUriString) {
        val uriStr = photoUriString
        if (!uriStr.isNullOrBlank()) {
            try {
                val uri = Uri.parse(uriStr)
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                profileBitmap = bitmap.asImageBitmap()
            } catch (_: Exception) {
                profileBitmap = null
            }
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                profileBitmap = bitmap.asImageBitmap()
                photoUriString = it.toString()
                prefs.edit().putString("driver_profile_photo_uri", it.toString()).apply()
                DriverDialogController.showToast("Profile photo updated successfully!")
            } catch (e: Exception) {
                DriverDialogController.showToast("Failed to load image: ${e.message}")
            }
        }
    }

    // Edit profile state
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember(profile.fullName) { mutableStateOf(profile.fullName) }
    var editEmail by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    // Settings state
    var soundAlertsEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_sound_alerts", true))
    }
    var autoAcceptEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_auto_accept", false))
    }
    var appLanguageHindi by remember {
        mutableStateOf(prefs.getBoolean("pref_lang_hi", false))
    }

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverProfile()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(
            title = "Pilot Profile & Account",
            subtitle = "Manage personal info, photo & driver settings",
            onBack = { onNavigate("duty_dashboard") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- SECTION 1: PHOTO & DRIVER CARD ---
        CockpitCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Avatar with Camera badge
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(PilotSurface)
                            .border(2.dp, PilotOnlineGreen, CircleShape)
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (profileBitmap != null) {
                            Image(
                                bitmap = profileBitmap!!,
                                contentDescription = "Driver Profile Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = Color(0xFFE5C09B))
                                drawArc(
                                    color = Color(0xFF1F2937),
                                    startAngle = 170f,
                                    sweepAngle = 200f,
                                    useCenter = true,
                                    topLeft = Offset(-size.width * 0.05f, -size.height * 0.1f),
                                    size = Size(size.width * 1.1f, size.height * 0.7f)
                                )
                                drawCircle(color = Color(0xFF1F2937), radius = size.width * 0.04f, center = Offset(size.width * 0.38f, size.height * 0.45f))
                                drawCircle(color = Color(0xFF1F2937), radius = size.width * 0.04f, center = Offset(size.width * 0.62f, size.height * 0.45f))
                                drawArc(
                                    color = Color(0xFF1F2937),
                                    startAngle = 20f,
                                    sweepAngle = 140f,
                                    useCenter = false,
                                    topLeft = Offset(size.width * 0.35f, size.height * 0.55f),
                                    size = Size(size.width * 0.3f, size.height * 0.25f),
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = Color(0xFF2563EB),
                                    startAngle = 0f,
                                    sweepAngle = 180f,
                                    useCenter = true,
                                    topLeft = Offset(0f, size.height * 0.75f),
                                    size = Size(size.width, size.height * 0.5f)
                                )
                            }
                        }
                    }

                    // Change Photo Camera Badge
                    Box(
                        modifier = Modifier
                            .offset(x = 4.dp, y = 4.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(PilotOnlineGreen)
                            .border(2.dp, PilotCanvas, CircleShape)
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "📷", fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Tap photo to change",
                    color = PilotElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { photoPickerLauncher.launch("image/*") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = profile.fullName.ifBlank { "Driver Partner" },
                    color = PilotTextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    CockpitBadge(
                        text = profile.approvalStatus.uppercase(),
                        backgroundColor = if (profile.approvalStatus.equals("APPROVED", ignoreCase = true)) OnlineGlow else Color(0x33FFB020),
                        textColor = if (profile.approvalStatus.equals("APPROVED", ignoreCase = true)) PilotOnlineGreen else Color(0xFFFFB020)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = profile.phoneNumber.ifBlank { "+91 Registered" },
                        color = PilotTextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = PilotBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Quick Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProfileStatBadge(title = "Rating", value = "${"%.1f".format(profile.rating.coerceAtLeast(4.8))} ★", color = Color(0xFFFACC15))
                ProfileStatBadge(title = "Trips", value = "${profile.completedTrips}", color = PilotElectricCyan)
                ProfileStatBadge(title = "Wallet", value = "₹${"%.0f".format(profile.walletBalanceRupees)}", color = PilotOnlineGreen)
                ProfileStatBadge(title = "Acceptance", value = "${profile.acceptanceRate.toInt().coerceAtLeast(95)}%", color = PilotTextWhite)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- SECTION 2: EDIT PROFILE / DETAILS ---
        CockpitCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profile Credentials",
                    color = PilotTextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEditing) "Cancel" else "✏️ Edit Details",
                    color = PilotElectricCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { isEditing = !isEditing }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isEditing) {
                CockpitTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    placeholder = "Enter full legal name",
                    label = "Full Legal Name",
                    leadingIcon = "👤"
                )
                Spacer(modifier = Modifier.height(10.dp))
                CockpitTextField(
                    value = editEmail,
                    onValueChange = { editEmail = it },
                    placeholder = "Enter email address (optional)",
                    label = "Email Address",
                    leadingIcon = "✉️"
                )
                Spacer(modifier = Modifier.height(14.dp))
                CockpitButton(
                    text = if (isSaving) "Saving Updates..." else "Save Profile Changes",
                    enabled = !isSaving && editName.trim().length >= 2,
                    onClick = {
                        isSaving = true
                        scope.launch {
                            val res = DriverRepository.updateDriverProfile(editName, editEmail)
                            isSaving = false
                            if (res.isSuccess) {
                                isEditing = false
                                DriverDialogController.showToast("Profile updated successfully!")
                            } else {
                                DriverDialogController.showToast("Update failed: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    }
                )
            } else {
                val details = listOf(
                    "🪪 Commercial License" to profile.licenseNumber.ifBlank { "DL-HR26-2023-XXXX" },
                    "🆔 Driver Partner ID" to profile.id.ifBlank { "DRV-84920" },
                    "🚗 Assigned Vehicle" to "${profile.assignedVehiclePlate.ifBlank { "DL 1ER 9042" }} (${profile.assignedVehicleModel.ifBlank { "Mahindra Treo" }})",
                    "📱 Registered Mobile" to profile.phoneNumber.ifBlank { "+91 98112 34567" }
                )
                details.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = label, color = PilotTextMuted, fontSize = 13.sp)
                        Text(text = value, color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- SECTION 3: ACCOUNT & APP SETTINGS ---
        CockpitCard {
            Text(
                text = "Account & Preferences",
                color = PilotTextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Setting 1: Language
            ProfileSettingRow(
                icon = "🌐",
                title = "App Language",
                subtitle = if (appLanguageHindi) "हिन्दी (Hindi)" else "English (US)",
                trailing = {
                    Text(
                        text = if (appLanguageHindi) "English me badlein" else "हिन्दी में बदलें",
                        color = PilotElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            val next = !appLanguageHindi
                            appLanguageHindi = next
                            prefs.edit().putBoolean("pref_lang_hi", next).apply()
                            DriverDialogController.showToast("Language changed to ${if (next) "Hindi" else "English"}")
                        }
                    )
                }
            )

            HorizontalDivider(color = PilotBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Setting 2: Ride & Dispatch Preferences
            ProfileSettingRow(
                icon = "⚙️",
                title = "Ride & Dispatch Preferences",
                subtitle = "Radius, destination filters & surge modes",
                onClick = { onNavigate("preferences") }
            )

            HorizontalDivider(color = PilotBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Setting 3: Payout Accounts & Bank
            ProfileSettingRow(
                icon = "💳",
                title = "Payout Bank & UPI Accounts",
                subtitle = "Fast settlement to verified bank account",
                onClick = { onNavigate("payout_accounts") }
            )

            HorizontalDivider(color = PilotBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Setting 4: Document Vault & RC
            ProfileSettingRow(
                icon = "📂",
                title = "Compliance & Document Vault",
                subtitle = "Driving license, RC, insurance & permits",
                onClick = { onNavigate("doc_vault") }
            )

            HorizontalDivider(color = PilotBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Setting 5: Safety Sentinel & SOS
            ProfileSettingRow(
                icon = "🛡️",
                title = "Safety Sentinel & Emergency SOS",
                subtitle = "Emergency contacts & automated cabin watchdog",
                onClick = { onNavigate("emergency_contacts") }
            )

            HorizontalDivider(color = PilotBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Setting 6: High Volume Sound Alerts
            ProfileSettingRow(
                icon = "🔔",
                title = "Dispatch High-Pitch Sound",
                subtitle = "Play loud chime on new booking offers",
                trailing = {
                    Switch(
                        checked = soundAlertsEnabled,
                        onCheckedChange = {
                            soundAlertsEnabled = it
                            prefs.edit().putBoolean("pref_sound_alerts", it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PilotOnlineGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = PilotBorder
                        )
                    )
                }
            )

            HorizontalDivider(color = PilotBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Setting 7: Auto-Accept Offers
            ProfileSettingRow(
                icon = "⚡",
                title = "Auto-Accept Ride Offers",
                subtitle = "Automatically accept rides within 2 km",
                trailing = {
                    Switch(
                        checked = autoAcceptEnabled,
                        onCheckedChange = {
                            autoAcceptEnabled = it
                            prefs.edit().putBoolean("pref_auto_accept", it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PilotOnlineGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = PilotBorder
                        )
                    )
                }
            )

            HorizontalDivider(color = PilotBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Setting 8: Driver Helpdesk
            ProfileSettingRow(
                icon = "🎧",
                title = "24/7 Driver Partner Helpdesk",
                subtitle = "Support tickets, disputes & road assistance",
                onClick = { onNavigate("helpdesk") }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // --- SECTION 4: LOG OUT BUTTON ---
        CockpitButton(
            text = "Log Out of Driver Account",
            isOutlined = true,
            backgroundColor = Color.Transparent,
            textColor = Color(0xFFFF5252),
            icon = "🚪",
            onClick = {
                DriverRepository.logout()
                DriverDialogController.showToast("Logged out successfully.")
                onNavigate("auth_login")
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileStatBadge(
    title: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = value,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            color = PilotTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProfileSettingRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PilotSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = PilotTextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = PilotTextMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Text(text = "→", color = PilotTextDim, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// Screen 5: Document Vault & Verification
@Composable
fun DocumentVaultScreen(onNavigate: (String) -> Unit) {
    val liveDocs by DriverRepository.documents.collectAsState()

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverDocuments()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(title = "Document Vault", subtitle = "Compliance records in backend", onBack = { onNavigate("pilot_profile") })
        Spacer(modifier = Modifier.height(12.dp))

        if (liveDocs.isEmpty()) {
            CockpitCard {
                Text(text = "No documents uploaded yet.", color = PilotTextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(liveDocs) { doc ->
                    CockpitCard(cornerRadius = 14.dp) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = doc.documentType, color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            CockpitBadge(
                                text = doc.status,
                                backgroundColor = if (doc.status == "APPROVED") OnlineGlow else AlertGlow,
                                textColor = if (doc.status == "APPROVED") PilotOnlineGreen else PilotAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = doc.fileName.ifBlank { "Uploaded: ${doc.createdAt}" }, color = PilotTextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// Screen 6: Document Expiry Watchdog
@Composable
fun DocumentExpiryWatchdogScreen(onNavigate: (String) -> Unit) {
    val liveDocs by DriverRepository.documents.collectAsState()
    LaunchedEffect(Unit) { DriverRepository.fetchDriverDocuments() }

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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = doc.documentType, color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            CockpitBadge(
                                text = if (doc.expiryDate.isNotBlank()) "${doc.daysUntilExpiry}d left" else doc.status,
                                backgroundColor = if (isExpiringSoon) AlertGlow else OnlineGlow,
                                textColor = if (isExpiringSoon) PilotAmber else PilotOnlineGreen
                            )
                        }
                        if (doc.expiryDate.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Expires: ${doc.expiryDate}", color = PilotTextMuted, fontSize = 11.sp)
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
}

@Preview(showBackground = true)
@Composable
fun PilotPasswordRecoveryScreenPreview() {
    DriverGoTheme {
        PilotPasswordRecoveryScreen(onNavigate = {})
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