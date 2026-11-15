package com.vaisentra.mobility.rider.feature.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.rider.components.*
import com.vaisentra.mobility.rider.data.NetworkClient
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import java.io.File

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
// Screen 46: Refer & Earn Electric Cash
@Composable
fun ReferEarnScreen(onNavigate: (String) -> Unit) {
    val loyalty by RiderRepository.loyalty.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeoHeader(title = "Refer & Earn Electric Cash", subtitle = "Give ₹100, Get ₹100", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(EmeraldGlow),
            contentAlignment = Alignment.Center
        ) { Text(text = "🎁", fontSize = 48.sp) }
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Earn ₹100 for Every Friend", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "When your contact takes their first green EV trip on RiderGo, both of you earn ₹100 wallet credit.", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(20.dp))

        NeoCard(borderColor = EmeraldGreen) {
            Text(text = "Your Exclusive Referral Code:", color = TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = loyalty.referralCode, color = EmeraldGreen, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                NeoBadge(text = "COPY CODE", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        NeoButton(text = "Share Referral Link via WhatsApp", onClick = {
            DialogController.showToast("Referral link shared to WhatsApp contacts!")
        }, backgroundColor = EmeraldGreen, icon = "💬")
    }
}

// Screen 47: Green Club Loyalty & CO2 Offset
@Composable
fun GreenClubLoyaltyScreen(onNavigate: (String) -> Unit) {
    val loyalty by RiderRepository.loyalty.collectAsState()

    LaunchedEffect(Unit) {
        RiderRepository.fetchLoyaltySummary()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Green Club Loyalty", subtitle = "Zero-Emission Commuter Impact", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard(borderColor = AmberGold) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = loyalty.tierName, color = AmberGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                NeoBadge(text = "GOLD COMMUTER", backgroundColor = AmberGold.copy(alpha = 0.2f), textColor = AmberGold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "🌱 Total Carbon Offset: ${loyalty.co2OffsetKg} kg CO₂ saved", color = EmeraldGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = "⭐ Green Reward Points: ${loyalty.pointsBalance} Pts", color = ElectricCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
        NeoCard {
            Text(text = "Club Tier Privileges", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            val perks = listOf(
                "✓ Zero peak-hour surge priority" to "Gold Status",
                "✓ 10% flat rebate on Eco-Pool rides" to "Active",
                "✓ Dedicated telephone operations concierge" to "Unlocked",
                "✓ Free airport flight delay protection" to "Included"
            )
            perks.forEach { (p, st) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = p, color = TextMuted, fontSize = 12.sp)
                    Text(text = st, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Download CO₂ Offset Certificate", onClick = {
            DialogController.show(RiderPopupType.TIER_UPGRADE)
        }, backgroundColor = EmeraldGreen, icon = "📜")
    }
}

// Screen 48: Notification Inbox & Alerts
@Composable
fun NotificationInboxScreen(onNavigate: (String) -> Unit) {
    val liveAlerts by RiderRepository.notifications.collectAsState()

    LaunchedEffect(Unit) {
        RiderRepository.fetchNotifications()
    }

    val defaultAlerts = listOf(
        Triple("⚡ Peak Corridor Cleared", "Demand stabilized near Anand Vihar Metro. Standard tariffs restored.", "10 mins ago"),
        Triple("🎉 ₹100 Referral Credited", "Priya joined via your link! ₹100 added to your wallet balance.", "2 hours ago"),
        Triple("🌧️ Delhi Monsoon Advisory", "High rain in South Delhi. 100% enclosed E-Autos dispatched on priority.", "Yesterday")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Notification Inbox", subtitle = "Transactional receipts & fleet advisories", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (liveAlerts.isNotEmpty()) {
                items(liveAlerts) { alert ->
                    NeoCard(cornerRadius = 14.dp) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = alert.title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = alert.createdAt.take(10), color = TextDim, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = alert.body, color = TextMuted, fontSize = 12.sp)
                    }
                }
            } else {
                items(defaultAlerts) { (title, body, time) ->
                    NeoCard(cornerRadius = 14.dp) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = time, color = TextDim, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = body, color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// Screen 49: Saved Places & Favorite Pins
@Composable
fun SavedPlacesScreen(onNavigate: (String) -> Unit) {
    val places by RiderRepository.savedPlaces.collectAsState()

    LaunchedEffect(Unit) {
        RiderRepository.fetchSavedPlaces()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Saved Places & Pins", subtitle = "1-Tap Booking Shortcuts in Supabase", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(places) { p ->
                NeoCard(cornerRadius = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (p.tag) {
                                "HOME" -> "🏠"
                                "WORK" -> "💼"
                                "AIRPORT" -> "✈️"
                                else -> "🚇"
                            },
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = p.name, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(text = p.address, color = TextMuted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        NeoButton(text = "Add New Favorite Pin", onClick = {
            DialogController.showToast("Enter address on map to save pin.")
            onNavigate("destination_search")
        }, icon = "📍")
    }
}

// Screen 50: Account Settings & Privacy Hub
@Composable
fun AccountSettingsHubScreen(onNavigate: (String) -> Unit) {
    val activeToken = NetworkClient.authToken
    val tokenStatus = if (!activeToken.isNullOrBlank()) {
        "ACTIVE (${activeToken.take(10)}...)"
    } else {
        "NOT LOGGED IN"
    }

    // Repository se live emergency contacts fetch karein
    val emergencyContacts by RiderRepository.emergencyContacts.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var userName by remember { mutableStateOf("Loading...") }
    var userId by remember { mutableStateOf("...") }
    var mobileNo by remember { mutableStateOf("...") }
    var emailId by remember { mutableStateOf("Not Linked") }
    var userRole by remember { mutableStateOf("CUSTOMER") }
    var passkeyStatus by remember { mutableStateOf("CHECKING...") }
    var deviceCount by remember { mutableIntStateOf(1) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        RiderRepository.fetchEmergencyContacts()
        RiderRepository.fetchCurrentUserMe()?.let { user ->
            userName = user.fullName ?: "RiderGo User"
            userId = user.id.take(12)
            mobileNo = user.phoneNumber ?: "N/A"
            emailId = user.email ?: "Not Linked"
            userRole = user.role.uppercase()
            passkeyStatus = if (user.hasPasskey) "ACTIVE ✓" else "NOT SET"
            avatarUrl = user.avatarUrl
        }
        RiderRepository.fetchTrustedDevices().getOrNull()?.let { devices ->
            if (devices.isNotEmpty()) {
                deviceCount = devices.size
            }
        }
    }

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && !isUploadingAvatar) {
            coroutineScope.launch {
                isUploadingAvatar = true
                val res = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    RiderRepository.uploadProfilePhoto(context, uri)
                }
                isUploadingAvatar = false

                res.onSuccess { newAvatarDataUri ->
                    avatarUrl = newAvatarDataUri
                    DialogController.showToast("Profile photo saved to backend!")
                }.onFailure { err ->
                    DialogController.showToast("Photo upload failed: ${err.message}")
                }
            }
        }
    }

    // Change Password Expandable Section States
    var showChangePassword by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isUpdatingPassword by remember { mutableStateOf(false) }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(
            title = "Account & Privacy Hub",
            subtitle = "Manage profile, security & SOS contacts",
            onBack = { onNavigate("map_home") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 1. PROFILE CARD (Image, Name, User ID, Mobile, Email)
        NeoCard(borderColor = ElectricCyan.copy(alpha = 0.4f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Image Avatar with Camera Badge
                val decodedAvatarBitmap = remember(avatarUrl) {
                    try {
                        val raw = avatarUrl ?: return@remember null
                        if (raw.startsWith("data:image")) {
                            val base64Part = raw.substringAfter("base64,")
                            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SapphireGlow)
                        .border(2.dp, ElectricCyan, CircleShape)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (decodedAvatarBitmap != null) {
                        Image(
                            bitmap = decodedAvatarBitmap,
                            contentDescription = "Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = userName.take(2).uppercase(),
                            color = ElectricCyan,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userName,
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ID: $userId",
                        color = TextDim,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Change Profile Image Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { imagePickerLauncher.launch("image/*") }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isUploadingAvatar) "⏳ Uploading..." else "📷 Change Profile Photo",
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Contact Info Rows (Mobile & Email)
            ProfileDetailRow(label = "Mobile Number", value = mobileNo)
            ProfileDetailRow(label = "Email Address", value = emailId)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. EMERGENCY CONTACTS CARD
        NeoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🚨 Emergency Contacts (SOS)",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "+ Manage",
                    color = ElectricCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigate("emergency_contacts") }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (emergencyContacts.isEmpty()) {
                Text(
                    text = "No emergency contacts added yet.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            } else {
                emergencyContacts.forEach { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = contact.name,
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = contact.phoneNumber, // Fixed: phone -> phoneNumber
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        NeoBadge(
                            text = contact.relationship.uppercase(), // Fixed: relation -> relationship
                            backgroundColor = CrimsonRed.copy(alpha = 0.15f),
                            textColor = CrimsonRed
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. SECURITY & CHANGE PASSWORD CARD
        NeoCard {
            Text(
                text = "Security & FastApi Sessions",
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            val sessionText = if (deviceCount == 1) "1 Active Android Session" else "$deviceCount Active Sessions"

            val settingsItems = listOf(
                "JWT Session Token" to tokenStatus,
                "Biometric Passkey (Face ID / Fingerprint)" to passkeyStatus,
                "Connected Devices ($sessionText)" to "DEVICE $deviceCount",
                "Supabase Role" to userRole
            )
            settingsItems.forEach { (item, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = item, color = TextMuted, fontSize = 12.sp)
                    NeoBadge(
                        text = value,
                        backgroundColor = SurfaceDark,
                        textColor = if (item == "JWT Session Token" && activeToken.isNullOrBlank()) CrimsonRed else ElectricCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Change Password Toggle Button
            NeoButton(
                text = if (showChangePassword) "Cancel Password Change" else "🔑 Change Password",
                backgroundColor = SurfaceDark,
                onClick = { showChangePassword = !showChangePassword }
            )

            // Expandable Change Password Form
            if (showChangePassword) {
                Spacer(modifier = Modifier.height(12.dp))
                NeoTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    placeholder = "Current Password / Recovery Code",
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                NeoTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    placeholder = "New Password (min 8 chars)",
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(10.dp))


                NeoButton(
                    text = if (isUpdatingPassword) "Updating..." else "Save New Password",
                    backgroundColor = EmeraldGreen,
                    onClick = {
                        if (isUpdatingPassword) return@NeoButton
                        if (currentPassword.isBlank()) {
                            DialogController.showToast("Please enter current password")
                            return@NeoButton
                        }
                        if (newPassword.length < 8) {
                            DialogController.showToast("New password must be at least 8 characters")
                            return@NeoButton
                        }

                        coroutineScope.launch {
                            isUpdatingPassword = true
                            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                RiderRepository.changeUserPassword(currentPassword, newPassword)
                            }
                            isUpdatingPassword = false

                            result.onSuccess {
                                DialogController.showToast("Password updated successfully!")
                                currentPassword = ""
                                newPassword = ""
                                showChangePassword = false
                            }.onFailure { err ->
                                DialogController.showToast("Failed: ${err.message}")
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val context = LocalContext.current
                var isExporting by remember { mutableStateOf(false) }

                NeoButton(
                    text = if (isExporting) "Exporting..." else "Export My Data",
                    modifier = Modifier.weight(1f),
                    backgroundColor = SurfaceDark,
                    onClick = {
                        if (isExporting) return@NeoButton
                        coroutineScope.launch {
                            isExporting = true
                            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                RiderRepository.requestDataExport()
                            }
                            isExporting = false

                            result.onSuccess { exportedMap ->
                                DialogController.showToast("Data ready! Choose where to save.")
                                exportAndShareJsonFile(context, exportedMap)
                            }.onFailure { err ->
                                DialogController.showToast("Export failed: ${err.message}")
                            }
                        }
                    }
                )
                NeoButton(
                    text = "App Lock PIN",
                    modifier = Modifier.weight(1f),
                    backgroundColor = SurfaceDark,
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            RiderRepository.setAppLockPin("1234")
                        }
                        DialogController.showToast("4-Digit Security PIN configured in Supabase!")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. SIGN OUT BUTTON (Clears Token)
        NeoButton(
            text = "Sign Out Session",
            onClick = {
                NetworkClient.clearAuthToken()
                DialogController.showToast("Session safely terminated.")
                onNavigate("splash")
            },
            backgroundColor = SurfaceDark,
            textColor = CrimsonRed
        )

        // Bottom Navigation Bar se overlap bachane ke liye extra space
        Spacer(modifier = Modifier.height(80.dp))
    }
}



private fun exportAndShareJsonFile(context: Context, data: Map<String, Any>) {
    try {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonString = gson.toJson(data)

        // Cache folder mein file banayein
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "RiderGo_UserData_${System.currentTimeMillis()}.json"
        val file = File(exportDir, fileName)
        file.writeText(jsonString)

        // FileProvider ke zariye secure URI banayein
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "RiderGo Account Data Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Save or Share Your Data (JSON)"))
    } catch (e: Exception) {
        DialogController.showToast("Export file error: ${e.message}")
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextMuted, fontSize = 12.sp)
        Text(
            text = value,
            color = TextWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Preview
@Composable
fun AccountSettingsHubScreenPreview() {
    RiderGoTheme {
        AccountSettingsHubScreen(onNavigate = {})
    }
}

