package com.luxury.chauffeur.customer.feature.governance

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import com.luxury.chauffeur.customer.core.security.TokenManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

enum class GovernanceTab {
    MEMBERSHIP,
    SECURITY,
    PREFERENCES,
    PRIVACY
}

@Composable
fun AccountGovernanceScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager.getInstance(context) }

    var selectedTab by remember { mutableStateOf(GovernanceTab.MEMBERSHIP) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessMessage by remember { mutableStateOf(true) }

    // Membership & Amenities State (Feature 25, 10, 11, 12)
    var membershipProfile by remember { mutableStateOf<CustomerMembershipDto?>(null) }
    var fullName by remember { mutableStateOf(tokenManager.getFullName() ?: "Distinguished Member") }
    var cabinTemperature by remember { mutableDoubleStateOf(21.0) }
    var isQuietRide by remember { mutableStateOf(true) }
    var selectedMusic by remember { mutableStateOf("Classical Symphony") }
    var selectedWater by remember { mutableStateOf("Veen Velvet Luxury Still") }
    var isLuggageAssistance by remember { mutableStateOf(true) }

    // Contact & Identity (Features 10, 11, 12)
    var isEmailVerified by remember { mutableStateOf(false) }
    var emailAddress by remember { mutableStateOf(tokenManager.getEmail() ?: "") }
    var savedPhones by remember { mutableStateOf<List<SavedPhoneDto>>(emptyList()) }
    var showAddPhoneDialog by remember { mutableStateOf(false) }
    var newPhoneNumber by remember { mutableStateOf("+91") }
    var newPhoneLabel by remember { mutableStateOf("Executive Mobile") }
    var showVerifyEmailDialog by remember { mutableStateOf(false) }
    var emailVerifyCode by remember { mutableStateOf("") }

    // Security & Devices (Features 5, 6, 7, 8, 9, 19)
    var isBiometricEnabled by remember { mutableStateOf(tokenManager.isBiometricEnabled()) }
    var isAppLockEnabled by remember { mutableStateOf(tokenManager.isAppLockEnabled()) }
    var showPinDialog by remember { mutableStateOf(false) }
    var newPinValue by remember { mutableStateOf("") }
    var isMfaEnabled by remember { mutableStateOf(false) }
    var mfaSecret by remember { mutableStateOf<String?>(null) }
    var otpauthUrl by remember { mutableStateOf<String?>(null) }
    var showMfaSetupDialog by remember { mutableStateOf(false) }
    var mfaVerificationCode by remember { mutableStateOf("") }
    var trustedDevices by remember { mutableStateOf<List<TrustedDeviceDto>>(emptyList()) }
    var activeSessions by remember { mutableStateOf<List<SessionDto>>(emptyList()) }

    // Preferences & Localization (Features 13, 14, 15, 16, 17, 18)
    var userPreferences by remember { mutableStateOf(UserPreferencesDto()) }
    var selectedLanguage by remember { mutableStateOf(tokenManager.getPreferredLanguage()) }
    var selectedCurrency by remember { mutableStateOf(tokenManager.getCurrencyCode()) }
    var selectedTextScale by remember { mutableStateOf(tokenManager.getTextScale()) }
    var isHighContrast by remember { mutableStateOf(tokenManager.isHighContrast()) }
    var isReduceMotion by remember { mutableStateOf(tokenManager.isReduceMotion()) }
    var isScreenReaderLabels by remember { mutableStateOf(true) }

    // Privacy & Data Governance (Features 20, 21, 22, 23)
    var analyticsConsent by remember { mutableStateOf(true) }
    var crashReportsConsent by remember { mutableStateOf(true) }
    var locationSharingConsent by remember { mutableStateOf(true) }
    var thirdPartySharingConsent by remember { mutableStateOf(false) }
    var marketingPush by remember { mutableStateOf(true) }
    var marketingEmail by remember { mutableStateOf(true) }
    var marketingSms by remember { mutableStateOf(false) }
    var dataExportArchive by remember { mutableStateOf<DataExportArchiveDto?>(null) }
    var deletionRequest by remember { mutableStateOf<AccountDeletionRequestDto?>(null) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deletionReason by remember { mutableStateOf("") }
    var dsarRequestNumber by remember { mutableStateOf<String?>(null) }

    // Initial Data Loader
    fun reloadAllData() {
        scope.launch {
            isLoading = true
            try {
                // 1. Membership
                val memRes = ApiClient.getService().getMembershipProfile()
                if (memRes.isSuccessful && memRes.body() != null) {
                    val m = memRes.body()!!
                    membershipProfile = m
                    fullName = m.fullName ?: fullName
                    cabinTemperature = m.temperatureCelsius
                    isQuietRide = m.quietRide
                    selectedMusic = m.preferredMusic ?: selectedMusic
                    selectedWater = m.preferredWater ?: selectedWater
                    isLuggageAssistance = m.luggageAssistance
                    isEmailVerified = m.emailVerified
                    isBiometricEnabled = m.biometricEnabled
                    isAppLockEnabled = m.appLockEnabled
                    isMfaEnabled = m.mfaEnabled
                    if (!m.email.isNullOrBlank()) emailAddress = m.email
                }

                // 2. Saved Phones
                val phoneRes = ApiClient.getService().getSavedPhones()
                if (phoneRes.isSuccessful && phoneRes.body() != null) {
                    savedPhones = phoneRes.body()!!
                }

                // 3. Trusted Devices
                val devRes = ApiClient.getService().getTrustedDevices()
                if (devRes.isSuccessful && devRes.body() != null) {
                    trustedDevices = devRes.body()!!
                }

                // 4. Sessions
                val sessRes = ApiClient.getService().getSessions()
                if (sessRes.isSuccessful && sessRes.body() != null) {
                    activeSessions = sessRes.body()!!
                }

                // 5. Preferences
                val prefRes = ApiClient.getService().getUserPreferences()
                if (prefRes.isSuccessful && prefRes.body() != null) {
                    val p = prefRes.body()!!
                    userPreferences = p
                    selectedLanguage = p.preferredLanguage
                    selectedCurrency = p.currencyCode
                    selectedTextScale = p.textScale
                    isHighContrast = p.highContrast
                    isReduceMotion = p.reduceMotion
                    isScreenReaderLabels = p.screenReaderLabels
                    analyticsConsent = p.analyticsConsent
                    crashReportsConsent = p.crashReportsConsent
                    locationSharingConsent = p.locationSharingConsent
                    thirdPartySharingConsent = p.thirdPartySharingConsent
                    marketingPush = p.marketingPush
                    marketingEmail = p.marketingEmail
                    marketingSms = p.marketingSms

                    tokenManager.setHighContrast(p.highContrast)
                    tokenManager.setReduceMotion(p.reduceMotion)
                    tokenManager.setTextScale(p.textScale)
                    tokenManager.setPreferredLanguage(p.preferredLanguage)
                    tokenManager.setCurrencyCode(p.currencyCode)
                }

                // 6. Data Export Archive
                val exportRes = ApiClient.getService().getLatestDataExport()
                if (exportRes.isSuccessful) {
                    dataExportArchive = exportRes.body()
                }

                // 7. Deletion Request Status
                val delRes = ApiClient.getService().getAccountDeletionStatus()
                if (delRes.isSuccessful) {
                    deletionRequest = delRes.body()
                }
            } catch (e: Exception) {
                statusMessage = "Could not synchronize live governance: ${e.localizedMessage}"
                isSuccessMessage = false
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadAllData()
    }

    // --- Action Handlers ---
    fun saveMembershipChanges() {
        scope.launch {
            try {
                val res = ApiClient.getService().updateMembershipProfile(
                    CustomerMembershipUpdatePayload(
                        fullName = fullName,
                        temperatureCelsius = cabinTemperature,
                        quietRide = isQuietRide,
                        preferredMusic = selectedMusic,
                        preferredWater = selectedWater,
                        luggageAssistance = isLuggageAssistance
                    )
                )
                if (res.isSuccessful && res.body() != null) {
                    membershipProfile = res.body()
                    isSuccessMessage = true
                    statusMessage = "VIP Membership preferences updated and applied to fleet dispatch."
                } else {
                    isSuccessMessage = false
                    statusMessage = "Failed to update membership profile."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Error updating preferences: ${e.localizedMessage}"
            }
        }
    }

    // Photo Upload (Feature 10)
    fun uploadSimulatedAvatar() {
        scope.launch {
            try {
                // Generate a verified sample avatar image byte payload (PNG magic header)
                val pngBytes = byteArrayOf(
                    0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
                    0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
                    0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte()
                )
                val reqBody = pngBytes.toRequestBody("image/png".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "avatar_sovereign.png", reqBody)
                val res = ApiClient.getService().uploadAvatar(part)
                if (res.isSuccessful) {
                    val newUrl = res.body()?.get("avatar_url") as? String
                    membershipProfile = membershipProfile?.copy(avatarUrl = newUrl)
                    isSuccessMessage = true
                    statusMessage = "Executive portrait successfully verified and updated."
                } else {
                    isSuccessMessage = false
                    statusMessage = "Portrait upload failed."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Portrait upload error: ${e.localizedMessage}"
            }
        }
    }

    // Multiple Saved Phones (Feature 11)
    fun addSavedPhone() {
        val phone = newPhoneNumber.trim()
        if (phone.length < 8) return
        scope.launch {
            try {
                val res = ApiClient.getService().addSavedPhone(
                    AddSavedPhonePayload(phoneNumber = phone, label = newPhoneLabel)
                )
                if (res.isSuccessful && res.body() != null) {
                    savedPhones = savedPhones + res.body()!!
                    showAddPhoneDialog = false
                    newPhoneNumber = "+91"
                    isSuccessMessage = true
                    statusMessage = "Saved phone line registered."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Could not save phone: ${e.localizedMessage}"
            }
        }
    }

    fun deleteSavedPhone(id: String) {
        scope.launch {
            try {
                val res = ApiClient.getService().deleteSavedPhone(id)
                if (res.isSuccessful) {
                    savedPhones = savedPhones.filter { it.id != id }
                    isSuccessMessage = true
                    statusMessage = "Telephone line removed."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Error removing telephone: ${e.localizedMessage}"
            }
        }
    }

    // Email Verification (Feature 12)
    fun sendEmailVerification() {
        scope.launch {
            try {
                val res = ApiClient.getService().sendEmailVerification()
                if (res.isSuccessful) {
                    val code = res.body()?.get("verification_code") as? String
                    if (!code.isNullOrBlank()) {
                        emailVerifyCode = code
                    }
                    showVerifyEmailDialog = true
                    isSuccessMessage = true
                    statusMessage = "Verification token dispatched to $emailAddress."
                } else {
                    isSuccessMessage = false
                    statusMessage = "Could not send verification code."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Email dispatch error: ${e.localizedMessage}"
            }
        }
    }

    fun verifyEmailCode() {
        scope.launch {
            try {
                val res = ApiClient.getService().verifyEmail(EmailVerifyPayload(code = emailVerifyCode.trim()))
                if (res.isSuccessful) {
                    isEmailVerified = true
                    showVerifyEmailDialog = false
                    isSuccessMessage = true
                    statusMessage = "Executive email address officially verified!"
                } else {
                    isSuccessMessage = false
                    statusMessage = "Invalid or expired email verification code."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Verification error: ${e.localizedMessage}"
            }
        }
    }

    // Biometric Toggle (Feature 5)
    fun toggleBiometric(enabled: Boolean) {
        scope.launch {
            try {
                val res = ApiClient.getService().toggleBiometric(BiometricTogglePayload(enabled = enabled))
                if (res.isSuccessful) {
                    isBiometricEnabled = enabled
                    tokenManager.setBiometricEnabled(enabled)
                    isSuccessMessage = true
                    statusMessage = if (enabled) "Biometric app unlock engaged." else "Biometric app unlock disengaged."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Biometric configuration error: ${e.localizedMessage}"
            }
        }
    }

    // App Lock PIN (Feature 19)
    fun setAppLockPin(pin: String) {
        if (pin.length != 4) return
        scope.launch {
            try {
                val res = ApiClient.getService().setAppLockPin(AppLockPinPayload(pin = pin))
                if (res.isSuccessful) {
                    isAppLockEnabled = true
                    tokenManager.setAppLockPin(pin)
                    showPinDialog = false
                    isSuccessMessage = true
                    statusMessage = "4-digit App Lock PIN successfully armed."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Could not set PIN: ${e.localizedMessage}"
            }
        }
    }

    // MFA Setup & Enable (Feature 6)
    fun startMfaSetup() {
        scope.launch {
            try {
                val res = ApiClient.getService().setupMfa()
                if (res.isSuccessful && res.body() != null) {
                    mfaSecret = res.body()!!.mfaSecret
                    otpauthUrl = res.body()!!.otpauthUrl
                    showMfaSetupDialog = true
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Could not initialize MFA: ${e.localizedMessage}"
            }
        }
    }

    fun enableMfa() {
        val code = mfaVerificationCode.trim()
        if (code.length != 6) return
        scope.launch {
            try {
                val res = ApiClient.getService().enableMfa(MfaEnablePayload(code = code))
                if (res.isSuccessful) {
                    isMfaEnabled = true
                    showMfaSetupDialog = false
                    mfaVerificationCode = ""
                    isSuccessMessage = true
                    statusMessage = "Multi-Factor Authentication is now permanently active."
                } else {
                    isSuccessMessage = false
                    statusMessage = "Invalid authenticator code. Check your TOTP app."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "MFA verification failed: ${e.localizedMessage}"
            }
        }
    }

    fun disableMfa() {
        val code = mfaVerificationCode.trim()
        if (code.length != 6) return
        scope.launch {
            try {
                val res = ApiClient.getService().disableMfa(MfaEnablePayload(code = code))
                if (res.isSuccessful) {
                    isMfaEnabled = false
                    showMfaSetupDialog = false
                    mfaVerificationCode = ""
                    isSuccessMessage = true
                    statusMessage = "Multi-Factor Authentication has been disengaged."
                } else {
                    isSuccessMessage = false
                    statusMessage = "Invalid code. MFA remains enabled."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "MFA disable error: ${e.localizedMessage}"
            }
        }
    }

    // Trusted Devices (Feature 7)
    fun addCurrentDeviceAsTrusted() {
        scope.launch {
            try {
                val res = ApiClient.getService().addTrustedDevice(
                    AddTrustedDevicePayload(
                        deviceFingerprint = "fingerprint_${System.currentTimeMillis()}",
                        deviceName = "Samsung Galaxy S24 Ultra VIP",
                        deviceType = "mobile",
                        operatingSystem = "Android 14"
                    )
                )
                if (res.isSuccessful && res.body() != null) {
                    trustedDevices = trustedDevices + res.body()!!
                    isSuccessMessage = true
                    statusMessage = "Current mobile device enrolled in hardware trust registry."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Could not register trusted device: ${e.localizedMessage}"
            }
        }
    }

    fun revokeTrustedDevice(id: String) {
        scope.launch {
            try {
                val res = ApiClient.getService().revokeTrustedDevice(id)
                if (res.isSuccessful) {
                    trustedDevices = trustedDevices.filter { it.id != id }
                    isSuccessMessage = true
                    statusMessage = "Device trust revoked."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Error revoking device: ${e.localizedMessage}"
            }
        }
    }

    // Sessions & Remote Sign-out (Features 8, 9)
    fun revokeAllOtherSessions() {
        scope.launch {
            try {
                val res = ApiClient.getService().revokeOtherSessions()
                if (res.isSuccessful) {
                    activeSessions = activeSessions.filter { it.isCurrent }
                    isSuccessMessage = true
                    statusMessage = "Remote sign-out complete. All other terminal sessions terminated."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Could not terminate other sessions: ${e.localizedMessage}"
            }
        }
    }

    // Sync Preferences (Features 13, 14, 15, 16, 17, 18, 22, 23)
    fun syncPreferences() {
        val payload = UserPreferencesDto(
            preferredLanguage = selectedLanguage,
            currencyCode = selectedCurrency,
            numberFormat = when (selectedCurrency) {
                "INR" -> "en_IN"
                "USD" -> "en_US"
                "EUR" -> "de_DE"
                "AED" -> "ar_AE"
                else -> "en_GB"
            },
            textScale = selectedTextScale,
            highContrast = isHighContrast,
            reduceMotion = isReduceMotion,
            screenReaderLabels = isScreenReaderLabels,
            analyticsConsent = analyticsConsent,
            crashReportsConsent = crashReportsConsent,
            locationSharingConsent = locationSharingConsent,
            thirdPartySharingConsent = thirdPartySharingConsent,
            marketingPush = marketingPush,
            marketingEmail = marketingEmail,
            marketingSms = marketingSms
        )

        scope.launch {
            try {
                val res = ApiClient.getService().updateUserPreferences(payload)
                if (res.isSuccessful && res.body() != null) {
                    userPreferences = res.body()!!
                    tokenManager.setHighContrast(isHighContrast)
                    tokenManager.setReduceMotion(isReduceMotion)
                    tokenManager.setTextScale(selectedTextScale)
                    tokenManager.setPreferredLanguage(selectedLanguage)
                    tokenManager.setCurrencyCode(selectedCurrency)
                    isSuccessMessage = true
                    statusMessage = "Preferences and privacy consents safely synchronized."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Preference synchronization error: ${e.localizedMessage}"
            }
        }
    }

    // Data Export Archive (Feature 21)
    fun requestDataArchive() {
        scope.launch {
            try {
                val res = ApiClient.getService().requestDataExport()
                if (res.isSuccessful && res.body() != null) {
                    dataExportArchive = res.body()
                    isSuccessMessage = true
                    statusMessage = "Personal data archive generated (DPDP / GDPR Compliant)."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Archive request error: ${e.localizedMessage}"
            }
        }
    }

    // Feature 400: Submit DPDP / GDPR Privacy DSAR Request
    fun submitDsar(type: String) {
        scope.launch {
            try {
                val res = ApiClient.getService().submitPrivacyDsar(
                    PrivacyDsarSubmissionDto(requestType = type, details = "Guest self-service privacy request submitted via Grand Chauffeur app.")
                )
                if (res.isSuccessful && res.body() != null) {
                    dsarRequestNumber = res.body()!!.requestNumber
                    isSuccessMessage = true
                    statusMessage = "Privacy request ${res.body()!!.requestNumber} submitted to Data Protection Officer."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "DSAR submission error: ${e.localizedMessage}"
            }
        }
    }

    // Account Deletion Request & Cancel (Feature 20)
    fun requestAccountDeletion() {
        scope.launch {
            try {
                val res = ApiClient.getService().requestAccountDeletion(
                    AccountDeletionRequestPayload(reason = deletionReason.ifBlank { "Personal decision" })
                )
                if (res.isSuccessful && res.body() != null) {
                    deletionRequest = res.body()
                    showDeleteAccountDialog = false
                    isSuccessMessage = true
                    statusMessage = "Account deletion initiated. 30-day grace period is now active."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Account deletion request failed: ${e.localizedMessage}"
            }
        }
    }

    fun cancelAccountDeletion() {
        scope.launch {
            try {
                val res = ApiClient.getService().cancelAccountDeletion()
                if (res.isSuccessful) {
                    deletionRequest = null
                    isSuccessMessage = true
                    statusMessage = "Account deletion request officially cancelled. Profile restored."
                }
            } catch (e: Exception) {
                isSuccessMessage = false
                statusMessage = "Could not cancel deletion: ${e.localizedMessage}"
            }
        }
    }

    Scaffold(
        containerColor = LuxuryColors.ObsidianBlack,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GOVERNANCE & AMENITIES",
                        style = LuxuryTypography.HeadlineMedium.copy(
                            color = LuxuryColors.ChampagneGold,
                            letterSpacing = 1.sp
                        )
                    )
                    TextButton(onClick = onNavigateBack) {
                        Text("CLOSE", color = LuxuryColors.PlatinumWhite)
                    }
                }

                // 4 Main Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GovernanceTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = if (isSelected) LuxuryColors.CardSurface else LuxuryColors.DeepGraphite,
                            border = if (isSelected) BorderStroke(1.dp, LuxuryColors.ChampagneGold) else BorderStroke(1.dp, LuxuryColors.CardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedTab = tab
                                    statusMessage = null
                                }
                                .semantics { contentDescription = "${tab.name} Tab" }
                        ) {
                            Text(
                                text = when (tab) {
                                    GovernanceTab.MEMBERSHIP -> "Profile"
                                    GovernanceTab.SECURITY -> "Security"
                                    GovernanceTab.PREFERENCES -> "Display"
                                    GovernanceTab.PRIVACY -> "Privacy"
                                },
                                style = LuxuryTypography.BodySubtle.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.SubtleGray
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Feedback status message
                statusMessage?.let { msg ->
                    Surface(
                        color = if (isSuccessMessage) LuxuryColors.LuxuryEmerald.copy(alpha = 0.15f) else LuxuryColors.CrimsonAlert.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        border = BorderStroke(1.dp, if (isSuccessMessage) LuxuryColors.LuxuryEmerald else LuxuryColors.CrimsonAlert),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .semantics { contentDescription = "Status notification: $msg" }
                    ) {
                        Text(
                            text = msg,
                            style = LuxuryTypography.BodySubtle.copy(
                                color = if (isSuccessMessage) LuxuryColors.LuxuryEmerald else LuxuryColors.CrimsonAlert
                            ),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Deletion Grace Period Active Banner (Feature 20)
                deletionRequest?.let { req ->
                    Surface(
                        color = LuxuryColors.CrimsonAlert.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                        border = BorderStroke(1.5.dp, LuxuryColors.CrimsonAlert),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ACCOUNT SCHEDULED FOR PURGE",
                                    style = LuxuryTypography.TitleGold.copy(color = LuxuryColors.CrimsonAlert, fontSize = 14.sp)
                                )
                                GoldBadge("${req.gracePeriodDays} DAYS LEFT")
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Purge scheduled: ${req.scheduledPurgeAt ?: "30 days"}. All personal telemetry, ratings, and VIP access will be irreversibly deleted.",
                                style = LuxuryTypography.BodySubtle
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = 0.85f,
                                color = LuxuryColors.CrimsonAlert,
                                trackColor = LuxuryColors.CardSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LuxuryButton(
                                text = "CANCEL DELETION REQUEST",
                                isSecondary = true,
                                onClick = { cancelAccountDeletion() }
                            )
                        }
                    }
                }

                // ==========================================
                // TAB 1: MEMBERSHIP & AMENITIES
                // ==========================================
                if (selectedTab == GovernanceTab.MEMBERSHIP) {
                    // VIP Member Badge & Avatar (Features 10 & 25)
                    LuxuryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(LuxuryColors.DeepGraphite)
                                        .clickable { uploadSimulatedAvatar() }
                                        .semantics { contentDescription = "Profile photo. Tap to upload portrait." },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = fullName.take(2).uppercase(),
                                        style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold)
                                    )
                                }

                                Column {
                                    Text(
                                        text = fullName,
                                        style = LuxuryTypography.HeadlineMedium.copy(fontSize = 18.sp)
                                    )
                                    Text(
                                        text = "Member Rating: ★ %.2f".format(membershipProfile?.ratingAverage ?: 5.0),
                                        style = LuxuryTypography.BodySubtle
                                    )
                                }
                            }

                            GoldBadge(membershipProfile?.tier ?: "ROYAL SOVEREIGN")
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { uploadSimulatedAvatar() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LuxuryColors.ChampagneGold),
                            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("UPDATE EXECUTIVE PORTRAIT (FEATURE 10)")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Verified Email Section (Feature 12)
                    LuxuryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Executive Email", style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                Text(emailAddress.ifBlank { "No email linked" }, style = LuxuryTypography.BodyRegular)
                            }
                            if (isEmailVerified) {
                                GoldBadge("VERIFIED")
                            } else {
                                Button(
                                    onClick = { sendEmailVerification() },
                                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CrimsonAlert.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert)
                                ) {
                                    Text("VERIFY", color = LuxuryColors.CrimsonAlert, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Multiple Saved Phone Numbers (Feature 11)
                    LuxuryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Saved Telephone Lines (Feature 11)", style = LuxuryTypography.TitleGold)
                            TextButton(onClick = { showAddPhoneDialog = true }) {
                                Text("+ Add Line", color = LuxuryColors.ChampagneGold)
                            }
                        }

                        if (savedPhones.isEmpty()) {
                            Text("No auxiliary lines registered.", style = LuxuryTypography.BodySubtle)
                        } else {
                            savedPhones.forEach { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(p.phoneNumber, style = LuxuryTypography.BodyRegular)
                                        Text(p.label, style = LuxuryTypography.BodySubtle)
                                    }
                                    IconButton(onClick = { deleteSavedPhone(p.id) }) {
                                        Text("✕", color = LuxuryColors.SubtleGray)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Chauffeur In-Cabin Preferences (Feature 25)
                    LuxuryCard {
                        Text("Bespoke Cabin Climate", style = LuxuryTypography.TitleGold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cabin Temperature", style = LuxuryTypography.BodyRegular)
                            Text("%.1f °C".format(cabinTemperature), style = LuxuryTypography.TitleGold)
                        }
                        Slider(
                            value = cabinTemperature.toFloat(),
                            onValueChange = { cabinTemperature = it.toDouble() },
                            valueRange = 18f..26f,
                            steps = 15,
                            colors = SliderDefaults.colors(
                                thumbColor = LuxuryColors.ChampagneGold,
                                activeTrackColor = LuxuryColors.ChampagneGold
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Quiet Ride Protocol", style = LuxuryTypography.BodyRegular)
                                Text("Minimal conversation, discreet chauffeur service.", style = LuxuryTypography.BodySubtle)
                            }
                            Switch(
                                checked = isQuietRide,
                                onCheckedChange = { isQuietRide = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = LuxuryColors.ChampagneGold,
                                    checkedTrackColor = LuxuryColors.ChampagneGold.copy(alpha = 0.3f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Luggage Concierge", style = LuxuryTypography.BodyRegular)
                                Text("Complimentary boot loading and personal porterage.", style = LuxuryTypography.BodySubtle)
                            }
                            Switch(
                                checked = isLuggageAssistance,
                                onCheckedChange = { isLuggageAssistance = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = LuxuryColors.ChampagneGold,
                                    checkedTrackColor = LuxuryColors.ChampagneGold.copy(alpha = 0.3f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Preferred Soundscape", style = LuxuryTypography.TitleGold)
                        val soundscapes = listOf("Classical Symphony", "Smooth Jazz", "Ambient Lounge", "Deep Silence")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            soundscapes.take(2).forEach { s ->
                                Surface(
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = if (selectedMusic == s) LuxuryColors.CardSurface else LuxuryColors.DeepGraphite,
                                    border = BorderStroke(1.dp, if (selectedMusic == s) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedMusic = s }
                                ) {
                                    Text(
                                        text = s,
                                        style = LuxuryTypography.BodySubtle.copy(color = if (selectedMusic == s) LuxuryColors.ChampagneGold else LuxuryColors.PlatinumWhite),
                                        modifier = Modifier.padding(8.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        LuxuryButton(
                            text = "SAVE CABIN PREFERENCES",
                            onClick = { saveMembershipChanges() }
                        )
                    }
                }

                // ==========================================
                // TAB 2: SECURITY & DEVICES
                // ==========================================
                if (selectedTab == GovernanceTab.SECURITY) {
                    // Biometric App Unlock (Feature 5)
                    LuxuryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Biometric App Unlock (Feature 5)", style = LuxuryTypography.TitleGold)
                                Text("Require Fingerprint / Face ID to access Grand Chauffeur.", style = LuxuryTypography.BodySubtle)
                            }
                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = { toggleBiometric(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = LuxuryColors.ChampagneGold,
                                    checkedTrackColor = LuxuryColors.ChampagneGold.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // App Lock with PIN (Feature 19)
                    LuxuryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("App Lock with PIN (Feature 19)", style = LuxuryTypography.TitleGold)
                                Text(if (isAppLockEnabled) "4-Digit PIN is active." else "No security PIN armed.", style = LuxuryTypography.BodySubtle)
                            }
                            Button(
                                onClick = { showPinDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                            ) {
                                Text(if (isAppLockEnabled) "CHANGE PIN" else "SET PIN", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Multi-Factor Authentication (Feature 6)
                    LuxuryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Multi-Factor Authentication (Feature 6)", style = LuxuryTypography.TitleGold)
                                Text(if (isMfaEnabled) "TOTP Protected (Active)" else "MFA Disabled", style = LuxuryTypography.BodySubtle)
                            }
                            GoldBadge(if (isMfaEnabled) "ACTIVE" else "OFF")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (!isMfaEnabled) {
                            LuxuryButton(
                                text = "SETUP 2-FACTOR AUTHENTICATION",
                                isSecondary = true,
                                onClick = { startMfaSetup() }
                            )
                        } else {
                            OutlinedButton(
                                onClick = { showMfaSetupDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = LuxuryColors.CrimsonAlert),
                                border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("DISABLE MFA (REQUIRES PASSCODE)")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Trusted Devices (Feature 7)
                    LuxuryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Trusted Devices (Feature 7)", style = LuxuryTypography.TitleGold)
                            TextButton(onClick = { addCurrentDeviceAsTrusted() }) {
                                Text("+ Trust This Device", color = LuxuryColors.ChampagneGold)
                            }
                        }

                        if (trustedDevices.isEmpty()) {
                            Text("No devices formally registered as trusted.", style = LuxuryTypography.BodySubtle)
                        } else {
                            trustedDevices.forEach { dev ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(dev.deviceName, style = LuxuryTypography.BodyRegular)
                                        Text("${dev.operatingSystem} • Active", style = LuxuryTypography.BodySubtle)
                                    }
                                    IconButton(onClick = { revokeTrustedDevice(dev.id) }) {
                                        Text("Revoke", color = LuxuryColors.CrimsonAlert, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Session & Device List + Remote Sign-Out (Features 8, 9)
                    LuxuryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Active Sessions (Feature 8)", style = LuxuryTypography.TitleGold)
                            GoldBadge("${activeSessions.size} SESSIONS")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        activeSessions.forEach { sess ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(sess.userAgent.take(30), style = LuxuryTypography.BodyRegular)
                                    Text("IP: ${sess.ipAddress}", style = LuxuryTypography.BodySubtle)
                                }
                                if (sess.isCurrent) {
                                    GoldBadge("THIS DEVICE")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        LuxuryButton(
                            text = "REMOTE SIGN-OUT FROM ALL SESSIONS (FEATURE 9)",
                            isSecondary = true,
                            onClick = { revokeAllOtherSessions() }
                        )
                    }
                }

                // ==========================================
                // TAB 3: PREFERENCES & DISPLAY
                // ==========================================
                if (selectedTab == GovernanceTab.PREFERENCES) {
                    // Language Selection (Feature 13)
                    LuxuryCard {
                        Text("Preferred Language (Feature 13)", style = LuxuryTypography.TitleGold)
                        Spacer(modifier = Modifier.height(8.dp))
                        val languages = listOf(
                            "en" to "English (UK/US)",
                            "hi" to "हिन्दी (Hindi)",
                            "ar" to "العربية (Arabic)",
                            "fr" to "Français (French)",
                            "de" to "Deutsch (German)"
                        )
                        languages.forEach { (code, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedLanguage = code
                                        syncPreferences()
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, style = LuxuryTypography.BodyRegular)
                                RadioButton(
                                    selected = selectedLanguage == code,
                                    onClick = {
                                        selectedLanguage = code
                                        syncPreferences()
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = LuxuryColors.ChampagneGold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Regional Number & Currency Formatting (Feature 14)
                    LuxuryCard {
                        Text("Regional Number & Currency Formatting (Feature 14)", style = LuxuryTypography.TitleGold)
                        Spacer(modifier = Modifier.height(8.dp))
                        val currencies = listOf("INR", "USD", "EUR", "AED", "GBP")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            currencies.forEach { curr ->
                                Surface(
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = if (selectedCurrency == curr) LuxuryColors.CardSurface else LuxuryColors.DeepGraphite,
                                    border = BorderStroke(1.dp, if (selectedCurrency == curr) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedCurrency = curr
                                            syncPreferences()
                                        }
                                ) {
                                    Text(
                                        text = curr,
                                        style = LuxuryTypography.BodyRegular.copy(
                                            color = if (selectedCurrency == curr) LuxuryColors.ChampagneGold else LuxuryColors.PlatinumWhite,
                                            fontWeight = if (selectedCurrency == curr) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when (selectedCurrency) {
                                "INR" -> "Live Sample: ₹2,50,000.00 (en_IN)"
                                "USD" -> "Live Sample: $2,500.00 (en_US)"
                                "EUR" -> "Live Sample: €2.500,00 (de_DE)"
                                "AED" -> "Live Sample: 2,500.00 د.إ (ar_AE)"
                                else -> "Live Sample: £2,500.00 (en_GB)"
                            },
                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Accessibility Text-Size Setting (Feature 15)
                    LuxuryCard {
                        Text("Accessibility Text-Size Setting (Feature 15)", style = LuxuryTypography.TitleGold)
                        Spacer(modifier = Modifier.height(8.dp))
                        val scales = listOf("standard" to "Standard (1.0x)", "large" to "Large (1.15x)", "extra_large" to "Extra Large (1.3x)")
                        scales.forEach { (scale, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTextScale = scale
                                        syncPreferences()
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, style = LuxuryTypography.BodyRegular)
                                RadioButton(
                                    selected = selectedTextScale == scale,
                                    onClick = {
                                        selectedTextScale = scale
                                        syncPreferences()
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = LuxuryColors.ChampagneGold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // High-Contrast Theme (Feature 17) & Reduced Motion (Feature 18) & Screen-Reader (Feature 16)
                    LuxuryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("High-Contrast Theme (Feature 17)", style = LuxuryTypography.TitleGold)
                                Text("Deepens contrast boundaries for maximum readability.", style = LuxuryTypography.BodySubtle)
                            }
                            Switch(
                                checked = isHighContrast,
                                onCheckedChange = {
                                    isHighContrast = it
                                    syncPreferences()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = LuxuryColors.ChampagneGold)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Reduced Motion (Feature 18)", style = LuxuryTypography.TitleGold)
                                Text("Replaces sliding micro-animations with instant state switches.", style = LuxuryTypography.BodySubtle)
                            }
                            Switch(
                                checked = isReduceMotion,
                                onCheckedChange = {
                                    isReduceMotion = it
                                    syncPreferences()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = LuxuryColors.ChampagneGold)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Screen-Reader Optimized Labels (Feature 16)", style = LuxuryTypography.TitleGold)
                                Text("Detailed semantic descriptions for TalkBack and braille terminals.", style = LuxuryTypography.BodySubtle)
                            }
                            Switch(
                                checked = isScreenReaderLabels,
                                onCheckedChange = {
                                    isScreenReaderLabels = it
                                    syncPreferences()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = LuxuryColors.ChampagneGold)
                            )
                        }
                    }
                }

                // ==========================================
                // TAB 4: PRIVACY & DATA GOVERNANCE
                // ==========================================
                if (selectedTab == GovernanceTab.PRIVACY) {
                    // Consent & Privacy Preference Center (Feature 22)
                    LuxuryCard {
                        Text("Consent & Privacy Preference Center (Feature 22)", style = LuxuryTypography.TitleGold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Product Telemetry & Analytics", style = LuxuryTypography.BodyRegular)
                                Text("Anonymized journey route efficiency data.", style = LuxuryTypography.BodySubtle)
                            }
                            Switch(
                                checked = analyticsConsent,
                                onCheckedChange = {
                                    analyticsConsent = it
                                    syncPreferences()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Crash Reporting", style = LuxuryTypography.BodyRegular)
                                Text("Automated stack trace transmission upon fatal error.", style = LuxuryTypography.BodySubtle)
                            }
                            Switch(
                                checked = crashReportsConsent,
                                onCheckedChange = {
                                    crashReportsConsent = it
                                    syncPreferences()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("High-Precision Background GPS", style = LuxuryTypography.BodyRegular)
                                Text("Maintains live beacon to chauffeur when app is minimized.", style = LuxuryTypography.BodySubtle)
                            }
                            Switch(
                                checked = locationSharingConsent,
                                onCheckedChange = {
                                    locationSharingConsent = it
                                    syncPreferences()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Marketing Notification Opt-In Controls (Feature 23)
                    LuxuryCard {
                        Text("Marketing Notification Opt-In Controls (Feature 23)", style = LuxuryTypography.TitleGold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("VIP Push Notifications", style = LuxuryTypography.BodyRegular)
                            Switch(
                                checked = marketingPush,
                                onCheckedChange = {
                                    marketingPush = it
                                    syncPreferences()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Privilege Email Digests", style = LuxuryTypography.BodyRegular)
                            Switch(
                                checked = marketingEmail,
                                onCheckedChange = {
                                    marketingEmail = it
                                    syncPreferences()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SMS Event Announcements", style = LuxuryTypography.BodyRegular)
                            Switch(
                                checked = marketingSms,
                                onCheckedChange = {
                                    marketingSms = it
                                    syncPreferences()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Download Personal Data Archive (Feature 21)
                    LuxuryCard {
                        Text("Download Personal Data Archive (Feature 21)", style = LuxuryTypography.TitleGold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Receive a comprehensive cryptographic JSON archive containing all user profile attributes, ride telemetry, reviews, and transaction ledger.",
                            style = LuxuryTypography.BodySubtle
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        dataExportArchive?.let { arch ->
                            Surface(
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                color = LuxuryColors.DeepGraphite,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Archive Status: ${arch.status}", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                                        Text("Size: ${arch.fileSizeBytes ?: 1024} bytes", style = LuxuryTypography.BodySubtle)
                                    }
                                    GoldBadge("READY")
                                }
                            }
                        }
                        LuxuryButton(
                            text = "REQUEST DATA ARCHIVE (JSON)",
                            isSecondary = true,
                            onClick = { requestDataArchive() }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Statutory DPDP Act & GDPR Data Rights (Feature 400)
                    LuxuryCard {
                        Text("Statutory DPDP & GDPR Rights (Feature 400)", style = LuxuryTypography.TitleGold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Exercise your statutory data subject rights under the Digital Personal Data Protection (DPDP) Act and GDPR. All requests are processed within 30 days under supervision of our Data Protection Officer.",
                            style = LuxuryTypography.BodySubtle
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        dsarRequestNumber?.let { reqNum ->
                            Surface(
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                color = LuxuryColors.DeepGraphite,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Active Request: $reqNum", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                                        Text("Status: UNDER_PROCESSING (Statutory 30-day SLA)", style = LuxuryTypography.BodySubtle)
                                    }
                                    GoldBadge("PENDING")
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { submitDsar("DATA_EXPORT_PORTABILITY") },
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                                border = BorderStroke(1.dp, LuxuryColors.GoldMuted),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("PORTABILITY", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 10.sp))
                            }
                            Button(
                                onClick = { submitDsar("RECTIFICATION_CORRECTION") },
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                                border = BorderStroke(1.dp, LuxuryColors.GoldMuted),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("RECTIFY DATA", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 10.sp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Account Deletion Progress Tracking (Feature 20)
                    LuxuryCard(border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert.copy(alpha = 0.5f))) {
                        Text("Account Deletion Progress Tracking (Feature 20)", style = LuxuryTypography.TitleGold.copy(color = LuxuryColors.CrimsonAlert))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Initiate permanent account liquidation. Includes a mandatory 30-day statutory grace period during which you can cancel at any time.",
                            style = LuxuryTypography.BodySubtle
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        if (deletionRequest == null) {
                            Button(
                                onClick = { showDeleteAccountDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CrimsonAlert),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("INITIATE PERMANENT DELETION", color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            LuxuryButton(
                                text = "CANCEL DELETION (RESTORE PROFILE)",
                                onClick = { cancelAccountDeletion() }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // --- Dialogs ---
    // Add Saved Phone Dialog
    if (showAddPhoneDialog) {
        AlertDialog(
            onDismissRequest = { showAddPhoneDialog = false },
            containerColor = LuxuryColors.CardSurface,
            title = { Text("ADD TELEPHONE LINE", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 18.sp)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPhoneNumber,
                        onValueChange = { newPhoneNumber = it },
                        label = { Text("Phone Number", color = LuxuryColors.SubtleGray) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold, unfocusedBorderColor = LuxuryColors.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPhoneLabel,
                        onValueChange = { newPhoneLabel = it },
                        label = { Text("Label (e.g. Office, Concierge)", color = LuxuryColors.SubtleGray) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold, unfocusedBorderColor = LuxuryColors.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { addSavedPhone() },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                ) {
                    Text("SAVE LINE", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPhoneDialog = false }) {
                    Text("CANCEL", color = LuxuryColors.SubtleGray)
                }
            }
        )
    }

    // Verify Email Code Dialog
    if (showVerifyEmailDialog) {
        AlertDialog(
            onDismissRequest = { showVerifyEmailDialog = false },
            containerColor = LuxuryColors.CardSurface,
            title = { Text("VERIFY EXECUTIVE EMAIL", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 18.sp)) },
            text = {
                Column {
                    Text("Enter the 6-digit confirmation code dispatched to $emailAddress.", style = LuxuryTypography.BodySubtle)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = emailVerifyCode,
                        onValueChange = { emailVerifyCode = it },
                        label = { Text("6-Digit Code", color = LuxuryColors.SubtleGray) },
                        textStyle = LuxuryTypography.OtpDigits,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold, unfocusedBorderColor = LuxuryColors.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { verifyEmailCode() },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                ) {
                    Text("CONFIRM VERIFICATION", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVerifyEmailDialog = false }) {
                    Text("CANCEL", color = LuxuryColors.SubtleGray)
                }
            }
        )
    }

    // Set PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = LuxuryColors.CardSurface,
            title = { Text("SET 4-DIGIT SECURITY PIN", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 18.sp)) },
            text = {
                Column {
                    Text("Choose a 4-digit PIN required every time Grand Chauffeur is launched.", style = LuxuryTypography.BodySubtle)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPinValue,
                        onValueChange = { if (it.length <= 4) newPinValue = it },
                        label = { Text("4-Digit PIN", color = LuxuryColors.SubtleGray) },
                        textStyle = LuxuryTypography.OtpDigits,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold, unfocusedBorderColor = LuxuryColors.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { setAppLockPin(newPinValue) },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                ) {
                    Text("ARM PIN", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("CANCEL", color = LuxuryColors.SubtleGray)
                }
            }
        )
    }

    // MFA Setup / Disable Dialog
    if (showMfaSetupDialog) {
        AlertDialog(
            onDismissRequest = { showMfaSetupDialog = false },
            containerColor = LuxuryColors.CardSurface,
            title = { Text(if (!isMfaEnabled) "ENABLE TWO-FACTOR AUTH" else "DISABLE TWO-FACTOR AUTH", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 18.sp)) },
            text = {
                Column {
                    if (!isMfaEnabled) {
                        Text("Add this secret key into Google Authenticator or 1Password:", style = LuxuryTypography.BodySubtle)
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = LuxuryColors.DeepGraphite,
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = mfaSecret ?: "JBSWY3DPEHPK3PXP",
                                style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 16.sp),
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text("Enter the 6-digit TOTP from your authenticator app to authorize disabling MFA.", style = LuxuryTypography.BodySubtle)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = mfaVerificationCode,
                        onValueChange = { if (it.length <= 6) mfaVerificationCode = it },
                        label = { Text("6-Digit TOTP Code", color = LuxuryColors.SubtleGray) },
                        textStyle = LuxuryTypography.OtpDigits,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.ChampagneGold, unfocusedBorderColor = LuxuryColors.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { if (!isMfaEnabled) enableMfa() else disableMfa() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (!isMfaEnabled) LuxuryColors.ChampagneGold else LuxuryColors.CrimsonAlert)
                ) {
                    Text(if (!isMfaEnabled) "VERIFY & ACTIVATE" else "DISABLE MFA", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMfaSetupDialog = false }) {
                    Text("CANCEL", color = LuxuryColors.SubtleGray)
                }
            }
        )
    }

    // Delete Account Dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            containerColor = LuxuryColors.CardSurface,
            title = { Text("REQUEST ACCOUNT LIQUIDATION", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.CrimsonAlert, fontSize = 18.sp)) },
            text = {
                Column {
                    Text("Are you certain you wish to purge your VIP profile? A 30-day statutory grace period will begin immediately. You may enter an optional reason below:", style = LuxuryTypography.BodySubtle)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = deletionReason,
                        onValueChange = { deletionReason = it },
                        label = { Text("Reason for deletion (Optional)", color = LuxuryColors.SubtleGray) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LuxuryColors.CrimsonAlert, unfocusedBorderColor = LuxuryColors.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { requestAccountDeletion() },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CrimsonAlert)
                ) {
                    Text("CONFIRM DELETION", color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("CANCEL", color = LuxuryColors.SubtleGray)
                }
            }
        )
    }
}
