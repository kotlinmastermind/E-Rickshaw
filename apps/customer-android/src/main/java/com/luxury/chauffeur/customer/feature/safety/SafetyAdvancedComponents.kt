package com.luxury.chauffeur.customer.feature.safety

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import kotlinx.coroutines.launch

val SosRed = Color(0xFFEF4444)
val SosRedDark = Color(0xFF991B1B)
val SafetyEmerald = Color(0xFF10B981)
val SafetyGold = Color(0xFFD4AF37)
val SafetySurfaceDark = Color(0xFF16161E)
val SafetySurfaceCard = Color(0xFF1F1F2C)

val LuxuryWhite = LuxuryColors.PlatinumWhite
val LuxuryIvory = LuxuryColors.SubtleGray
val LuxuryGold = LuxuryColors.ChampagneGold
val LuxuryBlack = LuxuryColors.ObsidianBlack

/**
 * Feature 126 & 129: In-Trip Emergency SOS Button & Live Response Dialog
 */
@Composable
fun EmergencySosDialog(
    rideId: String,
    currentLat: Double,
    currentLng: Double,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var sosTriggered by remember { mutableStateOf(false) }
    var sosResult by remember { mutableStateOf<SosTriggerResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun dialNumber(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        context.startActivity(intent)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = SafetySurfaceDark,
            border = BorderStroke(1.5.dp, if (sosTriggered) SosRed else SosRed.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SosRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SOS",
                        color = SosRed,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (sosTriggered) "EMERGENCY PROTOCOL ACTIVE" else "EMERGENCY DISPATCH (SOS)",
                    color = if (sosTriggered) SosRed else LuxuryWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (sosTriggered)
                        "24/7 Security Operations Command & your emergency contacts have received your live GPS coordinates."
                    else
                        "Pressing Confirm will instantly alert our 24/7 VIP Security Operations Command and broadcast live journey telemetry to your emergency contacts.",
                    color = LuxuryIvory.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = SosRed,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!sosTriggered) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    val resp = ApiClient.getService().triggerEmergencySos(
                                        SosTriggerRequest(
                                            rideId = rideId,
                                            latitude = currentLat,
                                            longitude = currentLng,
                                            alertType = "SOS_BUTTON"
                                        )
                                    )
                                    if (resp.isSuccessful && resp.body() != null) {
                                        sosResult = resp.body()
                                        sosTriggered = true
                                    } else {
                                        errorMessage = "Failed to dispatch SOS alert. Please dial 112 directly."
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Network anomaly: ${e.localizedMessage}. Dialing helpline."
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SosRed),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = LuxuryWhite, modifier = Modifier.size(22.dp))
                        } else {
                            Text(
                                text = "ACTIVATE EMERGENCY SOS",
                                color = LuxuryWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    sosResult?.let { result ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SafetySurfaceCard, RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Alert Status: ${result.status}",
                                color = SafetyEmerald,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Contacts Notified: ${result.contactsNotifiedCount}",
                                color = LuxuryWhite,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = result.message,
                                color = LuxuryIvory.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Fast Direct Dial Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { dialNumber("112") },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SosRed)
                    ) {
                        Text(
                            text = "CALL 112",
                            color = SosRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { dialNumber("+912269004800") },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SafetyGold)
                    ) {
                        Text(
                            text = "CALL SOC VIP",
                            color = SafetyGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onDismiss) {
                    Text(
                        text = if (sosTriggered) "Close & Return to Live Tracking" else "Dismiss",
                        color = LuxuryIvory.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Feature 127, 144, 145: Emergency Contact Setup Dialog
 */
@Composable
fun EmergencyContactSetupDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<EmergencyContactDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddForm by remember { mutableStateOf(false) }

    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newRelationship by remember { mutableStateOf("Family") }
    var notifyTripStart by remember { mutableStateOf(true) }
    var notifyArrival by remember { mutableStateOf(true) }

    fun refreshContacts() {
        coroutineScope.launch {
            isLoading = true
            try {
                val resp = ApiClient.getService().getEmergencyContacts()
                if (resp.isSuccessful) {
                    contacts = resp.body() ?: emptyList()
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshContacts()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = SafetySurfaceDark,
            border = BorderStroke(1.dp, SafetyGold.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TRUSTED CONTACTS",
                            color = SafetyGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Executive Safety Circle",
                            color = LuxuryWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close", color = LuxuryIvory.copy(alpha = 0.7f))
                    }
                }

                Text(
                    text = "Trusted contacts are automatically alerted when your trip commences or reaches its destination, and receive instant emergency coordinates if SOS is activated.",
                    color = LuxuryIvory.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SafetyGold)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(contacts) { contact ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = SafetySurfaceCard),
                                border = BorderStroke(1.dp, LuxuryGold.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.name,
                                            color = LuxuryWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "${contact.relationship} • ${contact.phoneNumber}",
                                            color = LuxuryIvory.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (contact.notifyOnTripStart) {
                                                Text(
                                                    text = "✓ Trip Start",
                                                    color = SafetyEmerald,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            if (contact.notifyOnArrival) {
                                                Text(
                                                    text = "✓ Arrival",
                                                    color = SafetyEmerald,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    IconButton(onClick = {
                                        coroutineScope.launch {
                                            ApiClient.getService().deleteEmergencyContact(contact.id)
                                            refreshContacts()
                                        }
                                    }) {
                                        Text("✕", color = SosRed, fontSize = 16.sp)
                                    }
                                }
                            }
                        }

                        if (contacts.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No emergency contacts configured yet.\nAdd up to 5 trusted persons.",
                                        color = LuxuryIvory.copy(alpha = 0.5f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    if (showAddForm) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SafetySurfaceCard, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "Add Trusted Contact",
                                color = SafetyGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("Full Name", color = LuxuryIvory.copy(alpha = 0.7f)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SafetyGold,
                                    unfocusedBorderColor = LuxuryIvory.copy(alpha = 0.3f),
                                    focusedTextColor = LuxuryWhite,
                                    unfocusedTextColor = LuxuryWhite
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newPhone,
                                onValueChange = { newPhone = it },
                                label = { Text("Phone Number (+91...)", color = LuxuryIvory.copy(alpha = 0.7f)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SafetyGold,
                                    unfocusedBorderColor = LuxuryIvory.copy(alpha = 0.3f),
                                    focusedTextColor = LuxuryWhite,
                                    unfocusedTextColor = LuxuryWhite
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newRelationship,
                                onValueChange = { newRelationship = it },
                                label = { Text("Relationship (Spouse, EA, Security, Family)", color = LuxuryIvory.copy(alpha = 0.7f)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SafetyGold,
                                    unfocusedBorderColor = LuxuryIvory.copy(alpha = 0.3f),
                                    focusedTextColor = LuxuryWhite,
                                    unfocusedTextColor = LuxuryWhite
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = notifyTripStart,
                                    onCheckedChange = { notifyTripStart = it },
                                    colors = CheckboxDefaults.colors(checkedColor = SafetyGold)
                                )
                                Text("Notify when journey begins", color = LuxuryWhite, fontSize = 12.sp)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = notifyArrival,
                                    onCheckedChange = { notifyArrival = it },
                                    colors = CheckboxDefaults.colors(checkedColor = SafetyGold)
                                )
                                Text("Notify upon safe arrival", color = LuxuryWhite, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (newName.isBlank() || newPhone.isBlank()) {
                                            Toast.makeText(context, "Please enter name and phone", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        coroutineScope.launch {
                                            try {
                                                ApiClient.getService().addEmergencyContact(
                                                    AddEmergencyContactRequest(
                                                        name = newName.trim(),
                                                        relationship = newRelationship.trim(),
                                                        phoneNumber = newPhone.trim(),
                                                        notifyOnTripStart = notifyTripStart,
                                                        notifyOnArrival = notifyArrival
                                                    )
                                                )
                                                showAddForm = false
                                                newName = ""
                                                newPhone = ""
                                                refreshContacts()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = SafetyGold)
                                ) {
                                    Text("Save Contact", color = LuxuryBlack, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { showAddForm = false },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, LuxuryIvory.copy(alpha = 0.3f))
                                ) {
                                    Text("Cancel", color = LuxuryIvory)
                                }
                            }
                        }
                    } else if (contacts.size < 5) {
                        Button(
                            onClick = { showAddForm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyGold)
                        ) {
                            Text("+ Add Trusted Contact", color = LuxuryBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Feature 128: Safety Check-In Timer Dialog
 */
@Composable
fun SafetyCheckInDialog(
    rideId: String,
    onDismiss: () -> Unit,
    onNeedHelp: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var responded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = SafetySurfaceDark,
            border = BorderStroke(1.5.dp, SafetyGold)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AUTOMATED SAFETY CHECK-IN",
                    color = SafetyGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Are you comfortable and safe?",
                    color = LuxuryWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "As part of our VIP late-night voyage assurance, we periodically confirm your comfort. Please confirm your status below.",
                    color = LuxuryIvory.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // 1. All Good
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                ApiClient.getService().respondSafetyCheckin(
                                    RespondCheckinRequest(rideId = rideId, responseType = "ALL_GOOD")
                                )
                                Toast.makeText(context, "Safety confirmed. Pleasant voyage.", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } catch (_: Exception) {
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyEmerald)
                ) {
                    Text("✓ I'm Safe & Comfortable", color = LuxuryWhite, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Snooze 10 min
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                ApiClient.getService().respondSafetyCheckin(
                                    RespondCheckinRequest(rideId = rideId, responseType = "SNOOZE_10_MIN")
                                )
                                Toast.makeText(context, "Check-in snoozed for 10 minutes", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } catch (_: Exception) {
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, LuxuryIvory.copy(alpha = 0.4f))
                ) {
                    Text("Snooze for 10 Minutes", color = LuxuryWhite)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Need Help (Auto SOS)
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                ApiClient.getService().respondSafetyCheckin(
                                    RespondCheckinRequest(rideId = rideId, responseType = "NEED_HELP")
                                )
                            } catch (_: Exception) {}
                            onDismiss()
                            onNeedHelp()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SosRed)
                ) {
                    Text("⚠️ I Need Assistance / SOS", color = LuxuryWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Feature 130, 131, 132: Driver & Vehicle Verification Credentials Dialog
 */
@Composable
fun ChauffeurIdentityCredentialsDialog(
    rideId: String,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var driverCreds by remember { mutableStateOf<DriverCredentialsResponse?>(null) }
    var vehicleCreds by remember { mutableStateOf<VehicleCredentialsResponse?>(null) }
    var pickupCode by remember { mutableStateOf<PickupVerificationCodeResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(rideId) {
        isLoading = true
        try {
            val dResp = ApiClient.getService().getDriverCredentials(rideId)
            if (dResp.isSuccessful) driverCreds = dResp.body()

            val vResp = ApiClient.getService().getVehicleCredentials(rideId)
            if (vResp.isSuccessful) vehicleCreds = vResp.body()

            val pResp = ApiClient.getService().getPickupVerificationCode(rideId)
            if (pResp.isSuccessful) pickupCode = pResp.body()
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = SafetySurfaceDark,
            border = BorderStroke(1.dp, SafetyGold.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SECURITY ASSURANCE",
                            color = SafetyGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Chauffeur & Fleet Credentials",
                            color = LuxuryWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close", color = LuxuryIvory.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Feature 130: 4-digit pickup verification code
                pickupCode?.let { pin ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SafetySurfaceCard),
                        border = BorderStroke(1.dp, SafetyGold)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "PICKUP VERIFICATION PIN",
                                color = SafetyGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = pin.verificationCode,
                                color = LuxuryWhite,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 8.sp
                            )
                            Text(
                                text = pin.instructions,
                                color = LuxuryIvory.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SafetyGold)
                    }
                } else {
                    // Feature 131: Chauffeur Identity Verification Card
                    driverCreds?.let { d ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SafetySurfaceCard),
                            border = BorderStroke(1.dp, LuxuryGold.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CHAUFFEUR IDENTITY",
                                        color = SafetyGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SafetyEmerald.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "✓ ${d.backgroundCheckStatus}",
                                            color = SafetyEmerald,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(SafetyGold.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = d.fullName.take(2).uppercase(),
                                            color = SafetyGold,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Text(
                                            text = d.fullName,
                                            color = LuxuryWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "Security Badge: ${d.securityBadgeId}",
                                            color = SafetyGold,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Level: ${d.academyLevel} • ★ ${d.rating} (${d.totalTrips} trips)",
                                            color = LuxuryIvory.copy(alpha = 0.7f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = LuxuryGold.copy(alpha = 0.15f))
                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Vetting Authority: ${d.backgroundCheckAuthority}",
                                    color = LuxuryIvory.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Clearance: ${d.confidentialityClearance}",
                                    color = LuxuryIvory.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Feature 132: Vehicle Registration Verification Card
                    vehicleCreds?.let { v ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SafetySurfaceCard),
                            border = BorderStroke(1.dp, LuxuryGold.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "CHARIOT REGISTRATION & PERMIT",
                                    color = SafetyGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "${v.model} (${v.color})",
                                    color = LuxuryWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Plate: ${v.licensePlate}",
                                    color = SafetyGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = LuxuryGold.copy(alpha = 0.15f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "RC Number: ${v.registrationCertificateRc}",
                                    color = LuxuryIvory.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Commercial Permit: ${v.commercialPermitNumber} (${v.permitType})",
                                    color = LuxuryIvory.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "PUC Status: ${v.pucCertificateStatus} (Exp: ${v.pucExpiryDate})",
                                    color = LuxuryIvory.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Fleet Insurance: ${v.insuranceProvider} (#${v.insurancePolicyNumber})",
                                    color = LuxuryIvory.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "EV Battery Health: ${v.evBatteryHealth}",
                                    color = SafetyEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Feature 133 & 149: Safety Center & Education Hub Dialog
 */
@Composable
fun SafetyCenterHubDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var safetyCenter by remember { mutableStateOf<SafetyCenterResponse?>(null) }
    var educationCards by remember { mutableStateOf<List<SafetyEducationCardDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun dial(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        context.startActivity(intent)
    }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val scResp = ApiClient.getService().getSafetyCenterInfo()
            if (scResp.isSuccessful) safetyCenter = scResp.body()

            val edResp = ApiClient.getService().getSafetyEducationCards()
            if (edResp.isSuccessful) educationCards = edResp.body() ?: emptyList()
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(24.dp),
            color = SafetySurfaceDark,
            border = BorderStroke(1.dp, SafetyGold.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SECURITY & CRISIS COMMAND",
                            color = SafetyGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Grand Chauffeur Safety Center",
                            color = LuxuryWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close", color = LuxuryIvory.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SOC 24/7 Command Card
                safetyCenter?.socCommandCenter?.let { soc ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SafetySurfaceCard),
                        border = BorderStroke(1.dp, SafetyGold)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "24/7 VIP SOC COMMAND",
                                    color = SafetyGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "SLA: ${soc.responseSla}",
                                    color = SafetyEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = soc.title, color = LuxuryWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(text = soc.description, color = LuxuryIvory.copy(alpha = 0.7f), fontSize = 12.sp)

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { dial(soc.phoneNumber) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyGold),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Direct Call: ${soc.phoneNumber}",
                                    color = LuxuryBlack,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // National Helplines
                Text(
                    text = "NATIONAL EMERGENCY HELPLINES",
                    color = SafetyGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                safetyCenter?.emergencyHelplines?.forEach { helpline ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { dial(helpline.number) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SafetySurfaceCard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = helpline.name, color = LuxuryWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(text = helpline.description, color = LuxuryIvory.copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                            Text(
                                text = "Dial ${helpline.number}",
                                color = SafetyGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Feature 149: Safety Education Cards
                Text(
                    text = "EXECUTIVE SAFETY PROTOCOLS",
                    color = SafetyGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                educationCards.forEach { card ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SafetySurfaceCard),
                        border = BorderStroke(1.dp, LuxuryGold.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = card.title, color = LuxuryWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SafetyGold.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = card.badge, color = SafetyGold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Text(text = card.subtitle, color = SafetyGold.copy(alpha = 0.8f), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = card.content, color = LuxuryIvory.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Feature 136 & 137: In-Cabin Consensual Audio Recording Dialog
 */
@Composable
fun RideRecordingConsentDialog(
    rideId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var passengerConsent by remember { mutableStateOf(false) }
    var driverConsent by remember { mutableStateOf(false) }
    var isRecordingActive by remember { mutableStateOf(false) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(rideId) {
        isLoading = true
        try {
            val resp = ApiClient.getService().getRecordingStatus(rideId)
            if (resp.isSuccessful && resp.body() != null) {
                passengerConsent = resp.body()!!.passengerConsent
                driverConsent = resp.body()!!.driverConsent
                isRecordingActive = resp.body()!!.isRecordingActive
                sessionId = resp.body()!!.sessionId
            }
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = SafetySurfaceDark,
            border = BorderStroke(1.dp, SafetyGold.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "CABIN AUDIO SAFEGUARD",
                    color = SafetyGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Dual-Party Consensual Recording",
                    color = LuxuryWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                Text(
                    text = "In compliance with the Digital Personal Data Protection Act (DPDP) and IT Act Section 26, audio in the chariot cabin can be recorded with mutual consent. Audio is encrypted client-side with AES-256 and stored on secure cold storage for 7 days, accessible only under formal security escalation.",
                    color = LuxuryIvory.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SafetySurfaceCard),
                    border = BorderStroke(1.dp, if (isRecordingActive) SafetyEmerald else LuxuryIvory.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Your Consent:", color = LuxuryWhite, fontSize = 13.sp)
                            Text(if (passengerConsent) "✓ Consented" else "Declined", color = if (passengerConsent) SafetyEmerald else SosRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Chauffeur Consent:", color = LuxuryWhite, fontSize = 13.sp)
                            Text(if (driverConsent) "✓ Consented" else "Awaiting Chauffeur", color = if (driverConsent) SafetyEmerald else SafetyGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Recording Session:", color = LuxuryWhite, fontSize = 13.sp)
                            Text(if (isRecordingActive) "ACTIVE (AES-256)" else "STANDBY", color = if (isRecordingActive) SafetyEmerald else LuxuryIvory.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val newConsent = !passengerConsent
                        coroutineScope.launch {
                            try {
                                val resp = ApiClient.getService().updateRecordingConsent(
                                    RecordingConsentRequest(rideId = rideId, role = "CUSTOMER", consented = newConsent)
                                )
                                if (resp.isSuccessful && resp.body() != null) {
                                    passengerConsent = resp.body()!!.passengerConsent
                                    isRecordingActive = resp.body()!!.isRecordingActive
                                    Toast.makeText(context, resp.body()!!.message, Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (passengerConsent) SosRed else SafetyGold
                    )
                ) {
                    Text(
                        text = if (passengerConsent) "Revoke My Consent" else "Give My Consent for Recording",
                        color = if (passengerConsent) LuxuryWhite else LuxuryBlack,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Close", color = LuxuryIvory.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/**
 * Feature 138, 140, 142, 143: Incident Report & Tracking Dialog
 */
@Composable
fun IncidentReportDialog(
    rideId: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) } // 0: Lodge Report, 1: Track Ticket

    // Lodge Report State
    var category by remember { mutableStateOf("AGGRESSIVE_DRIVING") }
    var description by remember { mutableStateOf("") }
    var isHarassment by remember { mutableStateOf(false) }
    var requestSupervisorCall by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var generatedTicket by remember { mutableStateOf<String?>(null) }

    // Track State
    var searchTicket by remember { mutableStateOf("") }
    var trackedIncident by remember { mutableStateOf<IncidentTrackingResponse?>(null) }
    var isTrackingLoading by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = SafetySurfaceDark,
            border = BorderStroke(1.dp, SafetyGold.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SECURITY OPERATIONS & INCIDENTS",
                            color = SafetyGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Fleet Assurance & Review",
                            color = LuxuryWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close", color = LuxuryIvory.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SafetySurfaceCard, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 0) SafetyGold else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Lodge Report", color = if (selectedTab == 0) LuxuryBlack else LuxuryWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 1) SafetyGold else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Track Ticket", color = if (selectedTab == 1) LuxuryBlack else LuxuryWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (generatedTicket != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = SafetySurfaceCard),
                                border = BorderStroke(1.dp, SafetyEmerald)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("✓ INCIDENT REPORTED", color = SafetyEmerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Ticket: $generatedTicket", color = LuxuryWhite, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                    Text("Security Operations will review this within 30 minutes.", color = LuxuryIvory.copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = {
                                        searchTicket = generatedTicket ?: ""
                                        selectedTab = 1
                                    }, colors = ButtonDefaults.buttonColors(containerColor = SafetyGold)) {
                                        Text("Track This Ticket", color = LuxuryBlack, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Text("Select Incident Nature:", color = LuxuryWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))

                            val categories = listOf(
                                "AGGRESSIVE_DRIVING" to "Aggressive Driving / Speeding",
                                "VEHICLE_DEFECT" to "Unsafe Vehicle Condition (AC / Seatbelt)",
                                "HARASSMENT_MISCONDUCT" to "Harassment or Misconduct (Zero-Tolerance)",
                                "ROUTE_ALTERATION" to "Unauthorized Detour / Route Alteration",
                                "OTHER" to "Other Safety Concern"
                            )

                            categories.forEach { (key, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            category = key
                                            isHarassment = (key == "HARASSMENT_MISCONDUCT")
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (category == key),
                                        onClick = {
                                            category = key
                                            isHarassment = (key == "HARASSMENT_MISCONDUCT")
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = if (key == "HARASSMENT_MISCONDUCT") SosRed else SafetyGold)
                                    )
                                    Text(
                                        text = label,
                                        color = if (key == "HARASSMENT_MISCONDUCT") SosRed else LuxuryWhite,
                                        fontSize = 12.sp,
                                        fontWeight = if (category == key) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Describe the incident in detail...", color = LuxuryIvory.copy(alpha = 0.6f)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                maxLines = 5,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isHarassment) SosRed else SafetyGold,
                                    unfocusedBorderColor = LuxuryIvory.copy(alpha = 0.3f),
                                    focusedTextColor = LuxuryWhite,
                                    unfocusedTextColor = LuxuryWhite
                                )
                            )

                            if (isHarassment) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                    Checkbox(
                                        checked = requestSupervisorCall,
                                        onCheckedChange = { requestSupervisorCall = it },
                                        colors = CheckboxDefaults.colors(checkedColor = SosRed)
                                    )
                                    Text("Request immediate supervisor telephone call", color = SosRed, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    if (description.isBlank()) {
                                        Toast.makeText(context, "Please provide description", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    coroutineScope.launch {
                                        isSubmitting = true
                                        try {
                                            if (isHarassment) {
                                                val resp = ApiClient.getService().reportHarassment(
                                                    HarassmentReportRequest(
                                                        rideId = rideId ?: "active-ride",
                                                        perpetratorRole = "DRIVER",
                                                        description = description.trim(),
                                                        requestSupervisorCall = requestSupervisorCall
                                                    )
                                                )
                                                if (resp.isSuccessful && resp.body() != null) {
                                                    generatedTicket = resp.body()!!.ticketNumber
                                                }
                                            } else {
                                                val resp = ApiClient.getService().lodgeIncidentReport(
                                                    IncidentReportRequest(
                                                        rideId = rideId,
                                                        category = category,
                                                        description = description.trim()
                                                    )
                                                )
                                                if (resp.isSuccessful && resp.body() != null) {
                                                    generatedTicket = resp.body()!!.ticketNumber
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Submission failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isHarassment) SosRed else SafetyGold
                                ),
                                enabled = !isSubmitting
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(color = LuxuryWhite, modifier = Modifier.size(20.dp))
                                } else {
                                    Text(
                                        text = if (isHarassment) "SUBMIT ZERO-TOLERANCE REPORT" else "SUBMIT INCIDENT REPORT",
                                        color = if (isHarassment) LuxuryWhite else LuxuryBlack,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Track Ticket Tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = searchTicket,
                                onValueChange = { searchTicket = it },
                                label = { Text("Ticket # (e.g. INC-20261027-A1B2)", color = LuxuryIvory.copy(alpha = 0.6f)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SafetyGold,
                                    unfocusedBorderColor = LuxuryIvory.copy(alpha = 0.3f),
                                    focusedTextColor = LuxuryWhite,
                                    unfocusedTextColor = LuxuryWhite
                                )
                            )

                            Button(
                                onClick = {
                                    if (searchTicket.isBlank()) return@Button
                                    coroutineScope.launch {
                                        isTrackingLoading = true
                                        try {
                                            val resp = ApiClient.getService().getIncidentTracking(searchTicket.trim())
                                            if (resp.isSuccessful) {
                                                trackedIncident = resp.body()
                                            } else {
                                                Toast.makeText(context, "Ticket not found", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isTrackingLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier.height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyGold)
                            ) {
                                Text("Track", color = LuxuryBlack, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (isTrackingLoading) {
                            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = SafetyGold)
                            }
                        } else trackedIncident?.let { inc ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SafetySurfaceCard),
                                border = BorderStroke(1.dp, SafetyGold.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = inc.ticketNumber, color = SafetyGold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (inc.status == "RESOLVED") SafetyEmerald.copy(alpha = 0.15f)
                                                    else SafetyGold.copy(alpha = 0.15f)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = inc.status,
                                                color = if (inc.status == "RESOLVED") SafetyEmerald else SafetyGold,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "Category: ${inc.category} • Severity: ${inc.severity}", color = LuxuryIvory.copy(alpha = 0.7f), fontSize = 12.sp)
                                    Text(text = inc.description, color = LuxuryWhite, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))

                                    inc.resolutionNotes?.let { notes ->
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(text = "Resolution Notes: $notes", color = SafetyEmerald, fontSize = 12.sp)
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(text = "INVESTIGATION TIMELINE", color = SafetyGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    inc.timeline.forEach { step ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (step.completed) "● " else "○ ",
                                                color = if (step.completed) SafetyEmerald else LuxuryIvory.copy(alpha = 0.4f),
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = step.step,
                                                color = if (step.completed) LuxuryWhite else LuxuryIvory.copy(alpha = 0.5f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Feature 141: Block Driver Confirmation Dialog
 */
@Composable
fun DriverBlockConfirmationDialog(
    driverId: String,
    driverName: String,
    rideId: String?,
    onDismiss: () -> Unit,
    onBlocked: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var reportIncident by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = SafetySurfaceDark,
            border = BorderStroke(1.5.dp, SosRed.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "BLOCK CHAUFFEUR",
                    color = SosRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Block $driverName?",
                    color = LuxuryWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                Text(
                    text = "You will never be paired with this chauffeur in future bookings. Our allocation engine permanently excludes this chauffeur from your dispatch pool.",
                    color = LuxuryIvory.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for block...", color = LuxuryIvory.copy(alpha = 0.6f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SosRed,
                        unfocusedBorderColor = LuxuryIvory.copy(alpha = 0.3f),
                        focusedTextColor = LuxuryWhite,
                        unfocusedTextColor = LuxuryWhite
                    )
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Checkbox(
                        checked = reportIncident,
                        onCheckedChange = { reportIncident = it },
                        colors = CheckboxDefaults.colors(checkedColor = SosRed)
                    )
                    Text("Also file an official fleet safety incident", color = LuxuryWhite, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSubmitting = true
                                try {
                                    val resp = ApiClient.getService().blockDriver(
                                        BlockDriverRequest(
                                            driverId = driverId,
                                            rideId = rideId,
                                            reason = reason.ifBlank { "Guest requested chauffeur block" },
                                            reportIncident = reportIncident,
                                            incidentDescription = if (reportIncident) reason else null
                                        )
                                    )
                                    if (resp.isSuccessful) {
                                        Toast.makeText(context, "Chauffeur successfully blocked.", Toast.LENGTH_SHORT).show()
                                        onBlocked()
                                        onDismiss()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SosRed),
                        enabled = !isSubmitting
                    ) {
                        Text("Confirm Block", color = LuxuryWhite, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, LuxuryIvory.copy(alpha = 0.4f))
                    ) {
                        Text("Cancel", color = LuxuryIvory)
                    }
                }
            }
        }
    }
}

/**
 * Feature 147 & 148: Safe Pickup Zone Suggestions & Restricted Warning Dialog
 */
@Composable
fun SafePickupZonesDialog(
    latitude: Double,
    longitude: Double,
    onSelectZone: (SafePickupZoneDto) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var safeZones by remember { mutableStateOf<List<SafePickupZoneDto>>(emptyList()) }
    var restrictedWarning by remember { mutableStateOf<RestrictedZoneWarningResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(latitude, longitude) {
        isLoading = true
        try {
            val szResp = ApiClient.getService().getSafePickupZones(latitude, longitude)
            if (szResp.isSuccessful) safeZones = szResp.body() ?: emptyList()

            val rzResp = ApiClient.getService().checkRestrictedPickupWarning(latitude, longitude)
            if (rzResp.isSuccessful) restrictedWarning = rzResp.body()
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = SafetySurfaceDark,
            border = BorderStroke(1.dp, SafetyGold.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LOCATION ASSURANCE",
                            color = SafetyGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Safe Pickup Zones",
                            color = LuxuryWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close", color = LuxuryIvory.copy(alpha = 0.7f))
                    }
                }

                // Feature 148: Geofenced restricted warning banner
                restrictedWarning?.let { w ->
                    if (w.isRestricted) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SosRed.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, SosRed)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⚠️ ${w.warningTitle ?: "Restricted Staging Zone"}",
                                    color = SosRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = w.warningMessage ?: "",
                                    color = LuxuryWhite,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Nearby 5-star hotel porticos, embassy gates, and luxury valet bays with 24/7 security and dedicated chauffeur staging.",
                    color = LuxuryIvory.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SafetyGold)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(safeZones) { zone ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectZone(zone)
                                        onDismiss()
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = SafetySurfaceCard),
                                border = BorderStroke(1.dp, LuxuryGold.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = zone.name, color = LuxuryWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(text = "${zone.distanceKm} km", color = SafetyGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    Text(text = zone.address, color = LuxuryIvory.copy(alpha = 0.7f), fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        zone.amenities.take(3).forEach { amenity ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(LuxuryBlack.copy(alpha = 0.5f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(text = amenity, color = LuxuryGold, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
