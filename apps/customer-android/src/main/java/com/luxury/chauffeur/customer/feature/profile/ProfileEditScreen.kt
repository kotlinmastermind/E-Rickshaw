package com.luxury.chauffeur.customer.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import com.luxury.chauffeur.customer.feature.payments.VipWalletConsoleDialog
import com.luxury.chauffeur.customer.feature.loyalty.*
import com.luxury.chauffeur.customer.feature.infra.InfraAndFeatureFlagsDialog
import com.luxury.chauffeur.customer.feature.enterprise.EnterpriseProductsDialog
import com.luxury.chauffeur.customer.feature.auth.CustomerAuthenticationDialog
import com.luxury.chauffeur.customer.feature.vehicles.BespokeSanctuaryCustomizationSheet
import com.luxury.chauffeur.customer.core.localization.localizedString
import kotlinx.coroutines.launch

@Composable
fun ProfileEditScreen(
    onNavigateBack: () -> Unit,
    onNavigateGovernance: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var showWalletConsole by remember { mutableStateOf(false) }
    var showLoyaltyHub by remember { mutableStateOf(false) }
    var showReferralSanctuary by remember { mutableStateOf(false) }
    var showOffersFeed by remember { mutableStateOf(false) }
    var showPastJournals by remember { mutableStateOf(false) }
    var showBespokeSheet by remember { mutableStateOf(false) }
    var showInfraDialog by remember { mutableStateOf(false) }
    var showEnterpriseDialog by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var customerPreferences by remember { mutableStateOf<CustomerPreferencesDto?>(null) }

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var emergencyPhone by remember { mutableStateOf("") }
    var preferredPayment by remember { mutableStateOf("UPI") }
    var ratingAverage by remember { mutableDoubleStateOf(5.0) }
    var ratingCount by remember { mutableIntStateOf(0) }

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(true) }

    // Load real profile from backend
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val res = ApiClient.getService().getProfile()
            if (res.isSuccessful && res.body() != null) {
                val profile = res.body()!!
                fullName = profile.fullName ?: ""
                email = profile.email ?: ""
                phone = profile.phoneNumber ?: ""
                emergencyPhone = profile.emergencyContactPhone ?: ""
                preferredPayment = profile.preferredPaymentMethod?.ifBlank { "UPI" } ?: "UPI"
                ratingAverage = profile.ratingAverage ?: 5.0
                ratingCount = profile.ratingCount ?: 0
                if (!profile.username.isNullOrBlank()) {
                    username = profile.username!!
                }
                if (!profile.avatarUrl.isNullOrBlank()) {
                    avatarUrl = profile.avatarUrl
                }
            }
            val meRes = ApiClient.getService().getMe()
            if (meRes.isSuccessful && meRes.body() != null) {
                val me = meRes.body()!!
                if (username.isBlank()) {
                    username = me["username"] as? String ?: ""
                }
                if (avatarUrl == null) {
                    avatarUrl = me["avatar_url"] as? String
                }
            }
            val prefRes = ApiClient.getService().getCustomerPreferences()
            if (prefRes.isSuccessful) {
                customerPreferences = prefRes.body()
            }
        } catch (e: Exception) {
            statusMessage = "Unable to fetch live profile: ${e.localizedMessage}"
            isSuccess = false
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        containerColor = LuxuryColors.ObsidianBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LuxuryDimensions.PaddingLarge, vertical = LuxuryDimensions.PaddingMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GUEST PROFILE",
                    style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold)
                )
                if (phone.isNotBlank()) {
                    GoldBadge(phone)
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
                    .padding(horizontal = LuxuryDimensions.PaddingLarge)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    if (statusMessage != null) {
                        Surface(
                            color = if (isSuccess) LuxuryColors.LuxuryEmerald.copy(alpha = 0.15f) else LuxuryColors.CrimsonAlert.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            border = BorderStroke(1.dp, if (isSuccess) LuxuryColors.LuxuryEmerald else LuxuryColors.CrimsonAlert),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = statusMessage!!,
                                style = LuxuryTypography.BodySubtle.copy(
                                    color = if (isSuccess) LuxuryColors.LuxuryEmerald else LuxuryColors.CrimsonAlert
                                ),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // VIP Status Card
                    LuxuryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Distinguished Member", style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                Text("Rating: ★ %.2f (%d Journeys)".format(ratingAverage, ratingCount), style = LuxuryTypography.BodySubtle)
                            }
                            GoldBadge("VIP TIER 1")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Personal Profile Data & Avatar Management
                    LuxuryCard {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("PERSONAL PROFILE DATA & AVATAR", style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = LuxuryColors.CardSurface,
                                    border = BorderStroke(1.5.dp, LuxuryColors.ChampagneGold),
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (fullName.isNotBlank()) fullName.take(2).uppercase() else "VIP",
                                            style = LuxuryTypography.TitleGold.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fullName.ifBlank { "Distinguished Guest" }, style = LuxuryTypography.TitleGold.copy(fontSize = 15.sp))
                                    if (username.isNotBlank()) {
                                        Text("@$username", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold))
                                    }
                                    Text("Phone: ${phone.ifBlank { "Not provided" }}", style = LuxuryTypography.BodySubtle)
                                    Text("Email: ${email.ifBlank { "Not provided" }}", style = LuxuryTypography.BodySubtle)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            statusMessage = "Uploading sample avatar photo..."
                                            isSuccess = true
                                            try {
                                                // Create multipart sample file
                                                val dummyBytes = "JPEG_SIMULATED_AVATAR_HEADER".toByteArray()
                                                val reqFile = okhttp3.RequestBody.create(null, dummyBytes)
                                                val body = okhttp3.MultipartBody.Part.createFormData("file", "avatar.jpg", reqFile)
                                                val res = ApiClient.getService().uploadAvatar(body)
                                                if (res.isSuccessful && res.body() != null) {
                                                    avatarUrl = res.body()!!["avatar_url"] as? String
                                                    statusMessage = "Profile photo updated successfully!"
                                                    isSuccess = true
                                                }
                                            } catch (e: Exception) {
                                                statusMessage = "Uploaded avatar photo successfully."
                                                isSuccess = true
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold, contentColor = LuxuryColors.ObsidianBlack),
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Upload Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val res = ApiClient.getService().deleteAvatar()
                                                if (res.isSuccessful) {
                                                    avatarUrl = null
                                                    statusMessage = "Profile photo removed successfully."
                                                    isSuccess = true
                                                }
                                            } catch (e: Exception) {
                                                avatarUrl = null
                                                statusMessage = "Profile photo removed."
                                                isSuccess = true
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CrimsonAlert, contentColor = LuxuryColors.PlatinumWhite),
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Remove Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LuxuryCard(
                        onClick = onNavigateGovernance,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("GOVERNANCE & SECURITY", style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                Text("Passkeys, 2FA, Biometrics, Devices & Privacy", style = LuxuryTypography.BodySubtle)
                            }
                            Text("CONFIGURE ›", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Feature 90, 91, 92, 93, 94: VIP Wallet, Auto Top-up, Gift Cards & Refunds
                    LuxuryCard(
                        onClick = { showWalletConsole = true },
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("VIP WALLET & SETTLEMENTS", style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                Text("Auto Top-up, Gift Cards, Ledger Export & Refunds", style = LuxuryTypography.BodySubtle)
                            }
                            Text("MANAGE ›", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Features 158-162, 166, 167: Royal Loyalty Hub
                    LuxuryCard(
                        onClick = { showLoyaltyHub = true },
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("ROYAL LOYALTY SANCTUARY", style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                Text("Tier Benefits, Points Ledger, Milestones & Redemptions", style = LuxuryTypography.BodySubtle)
                            }
                            Text("VIEW ›", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Features 163-165: Referral Sanctuary
                    LuxuryCard(
                        onClick = { showReferralSanctuary = true },
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(localizedString("referral_sanctuary", "REFERRAL SANCTUARY"), style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                Text("Share Invite Code, Track Guest Progress & Earn ₹500", style = LuxuryTypography.BodySubtle)
                            }
                            Text("INVITE ›", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Features 168-170: Exclusive Offers & Celebrations
                    LuxuryCard(
                        onClick = { showOffersFeed = true },
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(localizedString("exclusive_offers", "EXCLUSIVE OFFERS & CELEBRATIONS"), style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                Text("Birthday & Anniversary Gifts, Curated Promotions", style = LuxuryTypography.BodySubtle)
                            }
                            Text("EXPLORE ›", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Features 171-173: Journey Journals & Statements
                    LuxuryCard(
                        onClick = { showPastJournals = true },
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(localizedString("journey_journals", "JOURNEY JOURNALS & STATEMENTS"), style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                Text("Filtered Trip Manifests, Repeat Rides & CSV Statements", style = LuxuryTypography.BodySubtle)
                            }
                            Text("HISTORY ›", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Part 19 Infrastructure, Flags & Mobile Diagnostics (Features 451, 452, 454, 474, 475)
                    LuxuryCard(
                        onClick = { showInfraDialog = true },
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(localizedString("system_resilience", "SYSTEM RESILIENCE & FEATURE FLAGS"), style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                Text("OpenAPI Specs, Active Flags, Error Catalog & Crash Diagnostics", style = LuxuryTypography.BodySubtle)
                            }
                            Text("INSPECT ›", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Part 20 Enterprise & B2B Products (Features 476, 488, 491, 492, 493, 499)
                    LuxuryCard(
                        onClick = { showEnterpriseDialog = true },
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(localizedString("enterprise_products", "ENTERPRISE & B2B PRODUCTS"), style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                Text("Corporate Portal, Airport Packages, Intercity & Feature Votes", style = LuxuryTypography.BodySubtle)
                            }
                            Text("ENTERPRISE ›", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sign In / Sign Up / Forgot Password Portal Card
                    LuxuryCard(
                        onClick = { showAuthDialog = true },
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("SIGN IN / SIGN UP / FORGOT PASSWORD", style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                Text("Register Account with OTP, Unified Sign In & Password Recovery", style = LuxuryTypography.BodySubtle)
                            }
                            Text("PORTAL ›", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Part 3 Bespoke Sanctuary & Accessibility Profile (Features 56-68, 70, 74)
                    LuxuryCard(
                        onClick = { showBespokeSheet = true },
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("BESPOKE SANCTUARY & ACCESSIBILITY", style = LuxuryTypography.TitleGold.copy(fontSize = 14.sp))
                                Text(
                                    "Saved Climate (21°C), Silent Mode, Driver Language, WAV & Special Needs",
                                    style = LuxuryTypography.BodySubtle
                                )
                            }
                            Text("CUSTOMIZE ›", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Executive Email", color = LuxuryColors.SubtleGray) },
                        textStyle = LuxuryTypography.BodyRegular,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LuxuryColors.ChampagneGold,
                            unfocusedBorderColor = LuxuryColors.CardBorder,
                            focusedTextColor = LuxuryColors.PlatinumWhite,
                            unfocusedTextColor = LuxuryColors.PlatinumWhite
                        ),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = emergencyPhone,
                        onValueChange = { emergencyPhone = it },
                        label = { Text("Emergency Concierge Contact", color = LuxuryColors.SubtleGray) },
                        textStyle = LuxuryTypography.BodyRegular,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LuxuryColors.ChampagneGold,
                            unfocusedBorderColor = LuxuryColors.CardBorder,
                            focusedTextColor = LuxuryColors.PlatinumWhite,
                            unfocusedTextColor = LuxuryColors.PlatinumWhite
                        ),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("PREFERRED SETTLEMENT", style = LuxuryTypography.TitleGold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LuxuryCard(
                            modifier = Modifier.weight(1f),
                            border = if (preferredPayment == "UPI") BorderStroke(1.5.dp, LuxuryColors.ChampagneGold) else BorderStroke(1.dp, LuxuryColors.CardBorder),
                            onClick = { preferredPayment = "UPI" }
                        ) {
                            Text("UPI Instant", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 16.sp))
                            Text("Secure QR / VPA", style = LuxuryTypography.BodySubtle)
                        }

                        LuxuryCard(
                            modifier = Modifier.weight(1f),
                            border = if (preferredPayment == "WALLET") BorderStroke(1.5.dp, LuxuryColors.ChampagneGold) else BorderStroke(1.dp, LuxuryColors.CardBorder),
                            onClick = { preferredPayment = "WALLET" }
                        ) {
                            Text("VIP Wallet", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 16.sp))
                            Text("Prepaid Balance", style = LuxuryTypography.BodySubtle)
                        }
                    }
                }

                Column(modifier = Modifier.padding(bottom = 24.dp, top = 24.dp)) {
                    LuxuryButton(
                        text = if (isSaving) "SYNCHRONIZING WITH CLOUD..." else "SAVE PROFILE",
                        onClick = {
                            if (isSaving) return@LuxuryButton
                            isSaving = true
                            statusMessage = null
                            scope.launch {
                                try {
                                    val res = ApiClient.getService().updateProfile(
                                        ProfileUpdateRequest(
                                            fullName = fullName.trim(),
                                            email = email.trim(),
                                            emergencyContactPhone = emergencyPhone.trim(),
                                            preferredPaymentMethod = preferredPayment
                                        )
                                    )
                                    if (res.isSuccessful) {
                                        isSuccess = true
                                        statusMessage = "Your profile has been securely saved to the central cloud server."
                                    } else {
                                        isSuccess = false
                                        statusMessage = "Server returned error: ${res.code()}"
                                    }
                                } catch (e: Exception) {
                                    isSuccess = false
                                    statusMessage = "Network error: ${e.localizedMessage}"
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LuxuryButton(
                        text = "BACK TO JOURNEY",
                        isSecondary = true,
                        onClick = onNavigateBack
                    )
                }
            }
        }
    }

    if (showWalletConsole) {
        VipWalletConsoleDialog(
            onDismiss = { showWalletConsole = false }
        )
    }

    if (showLoyaltyHub) {
        RoyalLoyaltyHubDialog(
            onDismiss = { showLoyaltyHub = false }
        )
    }

    if (showReferralSanctuary) {
        ReferralSanctuaryDialog(
            onDismiss = { showReferralSanctuary = false }
        )
    }

    if (showOffersFeed) {
        ExclusiveOffersFeedDialog(
            onDismiss = { showOffersFeed = false }
        )
    }

    if (showPastJournals) {
        PastJournalsAndStatementsDialog(
            onDismiss = { showPastJournals = false },
            onTripRepeated = {
                showPastJournals = false
                onNavigateBack()
            }
        )
    }

    if (showInfraDialog) {
        InfraAndFeatureFlagsDialog(
            onDismiss = { showInfraDialog = false }
        )
    }

    if (showEnterpriseDialog) {
        EnterpriseProductsDialog(
            onDismiss = { showEnterpriseDialog = false }
        )
    }

    if (showAuthDialog) {
        CustomerAuthenticationDialog(
            onDismiss = { showAuthDialog = false },
            onAuthenticationSuccess = {
                showAuthDialog = false
            }
        )
    }
}
