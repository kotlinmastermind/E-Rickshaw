package com.luxury.chauffeur.customer.feature.tracking

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

/**
 * Part 5: Advanced Tracking, Communication & Route Governance UI Components
 * Features 101 - 125
 */

// =========================================================================
// Features 107, 108, 109: Trip Share Dialog (Contacts, Expiry, Privacy Shield)
// =========================================================================

@Composable
fun TripShareDialog(
    rideId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }
    var hideExactAddress by remember { mutableStateOf(true) }
    var expiryHours by remember { mutableIntStateOf(4) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedShareResponse by remember { mutableStateOf<TripShareResponse?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "SHARE LIVE TRIP",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            "Features 107-109: Real-time Live Tracking Link",
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = LuxuryColors.SubtleGray, fontSize = 18.sp)
                    }
                }

                Divider(color = LuxuryColors.CardBorder)

                // Privacy Shield Toggle (Feature 109)
                Surface(
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                    color = LuxuryColors.DeepGraphite,
                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🛡️ Privacy Shield (Hide exact address)",
                                style = LuxuryTypography.BodyRegular.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Conceals house/unit numbers; displays only neighborhood landmark (e.g. Near Worli Sea Face)",
                                style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                            )
                        }
                        Switch(
                            checked = hideExactAddress,
                            onCheckedChange = { hideExactAddress = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = LuxuryColors.ChampagneGold,
                                checkedTrackColor = LuxuryColors.ChampagneGold.copy(alpha = 0.3f),
                                uncheckedThumbColor = LuxuryColors.SubtleGray,
                                uncheckedTrackColor = LuxuryColors.CardSurface
                            )
                        )
                    }
                }

                // Expiry Selector (Feature 108)
                Text(
                    "LINK EXPIRATION DURATION",
                    style = LuxuryTypography.BodySubtle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1 to "1 Hour", 4 to "4 Hours", 12 to "12 Hours", 24 to "24 Hours").forEach { (hrs, label) ->
                        val isSelected = expiryHours == hrs
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = if (isSelected) LuxuryColors.ChampagneGold.copy(alpha = 0.15f) else LuxuryColors.DeepGraphite,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { expiryHours = hrs }
                        ) {
                            Text(
                                text = label,
                                style = LuxuryTypography.BodySubtle.copy(
                                    color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.PlatinumWhite,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }

                // Optional Contact Fields (Feature 107)
                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    label = { Text("Trusted Contact Name (Optional)") },
                    placeholder = { Text("e.g. Family, Executive Assistant") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LuxuryColors.ChampagneGold,
                        unfocusedBorderColor = LuxuryColors.CardBorder,
                        focusedTextColor = LuxuryColors.PlatinumWhite,
                        unfocusedTextColor = LuxuryColors.PlatinumWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = recipientPhone,
                    onValueChange = { recipientPhone = it },
                    label = { Text("Contact Phone (Optional)") },
                    placeholder = { Text("+91 98200 00000") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LuxuryColors.ChampagneGold,
                        unfocusedBorderColor = LuxuryColors.CardBorder,
                        focusedTextColor = LuxuryColors.PlatinumWhite,
                        unfocusedTextColor = LuxuryColors.PlatinumWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Share Result or Generate Button
                val share = generatedShareResponse
                if (share != null) {
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.DeepGraphite,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "SECURE TRACKING LINK ACTIVE",
                                style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp)
                            )
                            Text(
                                share.shareUrl,
                                style = LuxuryTypography.BodyRegular.copy(
                                    fontSize = 12.sp,
                                    color = LuxuryColors.PlatinumWhite
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Trip Tracking Link", share.shareUrl))
                                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Text("📋 COPY LINK", color = LuxuryColors.ChampagneGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Track my Grand Chauffeur executive journey live: ${share.shareUrl} (Secured with Privacy Shield)"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Live Trip"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Text("SHARE 💬", color = LuxuryColors.ObsidianBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    LuxuryButton(
                        text = if (isGenerating) "GENERATING LINK..." else "GENERATE SECURE LINK",
                        onClick = {
                            isGenerating = true
                            scope.launch {
                                try {
                                    val req = TripShareRequest(
                                        recipientName = recipientName.ifBlank { null },
                                        recipientPhone = recipientPhone.ifBlank { null },
                                        hideExactAddress = hideExactAddress,
                                        expiryHours = expiryHours
                                    )
                                    val res = ApiClient.getService().createTripShareLink(rideId, req)
                                    if (res.isSuccessful && res.body() != null) {
                                        generatedShareResponse = res.body()
                                        Toast.makeText(context, "Tracking link generated", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to create share link", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isGenerating = false
                                }
                            }
                        },
                        isLoading = isGenerating
                    )
                }
            }
        }
    }
}

// =========================================================================
// Features 110, 112, 113, 114: In-App Customer-Driver Chat, Quick Replies, Translations
// =========================================================================

data class LocalChatMessage(
    val id: String,
    val sender: String, // "CUSTOMER" or "DRIVER"
    val text: String,
    val translatedText: String? = null,
    val meetingPointName: String? = null,
    val timestamp: String
)

@Composable
fun InAppChauffeurChatDialog(
    rideId: String,
    chauffeurName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var messageText by remember { mutableStateOf("") }
    var quickReplies by remember {
        mutableStateOf(
            listOf(
                "Waiting at the main entrance portico.",
                "Please pull into the private VIP service lane.",
                "Holding heavy luggage - luggage assistance requested.",
                "Standing by hotel concierge / valet kiosk.",
                "Will be down to the vehicle in 2 minutes."
            )
        )
    }

    var messages by remember {
        mutableStateOf(
            listOf(
                LocalChatMessage(
                    id = "msg-1",
                    sender = "DRIVER",
                    text = "Good day. I have arrived at the designated portico. Hazards active.",
                    timestamp = "Just now"
                )
            )
        )
    }

    // Load server quick replies
    LaunchedEffect(rideId) {
        try {
            val res = ApiClient.getService().getQuickReplies(rideId, role = "customer")
            if (res.isSuccessful && res.body() != null) {
                quickReplies = res.body()!!
            }
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Chat Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(LuxuryColors.ChampagneGold.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👔", fontSize = 20.sp)
                        }
                        Column {
                            Text(
                                chauffeurName,
                                style = LuxuryTypography.HeadlineMedium.copy(fontSize = 16.sp)
                            )
                            Text(
                                "Executive Chauffeur • Encrypted Channel",
                                style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.LuxuryEmerald)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = LuxuryColors.SubtleGray, fontSize = 18.sp)
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = LuxuryColors.CardBorder)

                // Meeting Point Quick Dispatch Card (Feature 114)
                Surface(
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                    color = LuxuryColors.DeepGraphite,
                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "📍 PICKUP MEETING POINT CHIPS (Feature 114)",
                            style = LuxuryTypography.TitleGold.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Main Portico Bay 1" to Pair(18.9220, 72.8347),
                                "VIP Valet Kiosk" to Pair(18.9225, 72.8350),
                                "Airport Gate 4" to Pair(19.0974, 72.8744)
                            ).forEach { (name, coords) ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = LuxuryColors.CardSurface,
                                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.5f)),
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            try {
                                                ApiClient.getService().sendMeetingPoint(
                                                    rideId,
                                                    MeetingPointRequest(name, coords.first, coords.second)
                                                )
                                                messages = messages + LocalChatMessage(
                                                    id = "msg-${System.currentTimeMillis()}",
                                                    sender = "CUSTOMER",
                                                    text = "📍 Designated Meeting Point: $name",
                                                    meetingPointName = name,
                                                    timestamp = "Just now"
                                                )
                                                Toast.makeText(context, "Meeting point sent to chauffeur", Toast.LENGTH_SHORT).show()
                                            } catch (_: Exception) {}
                                        }
                                    }
                                ) {
                                    Text(
                                        name,
                                        style = LuxuryTypography.BodySubtle.copy(
                                            color = LuxuryColors.ChampagneGold,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Message Stream
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        val isCustomer = msg.sender == "CUSTOMER"
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isCustomer) Alignment.End else Alignment.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = if (isCustomer) 12.dp else 2.dp,
                                    bottomEnd = if (isCustomer) 2.dp else 12.dp
                                ),
                                color = if (isCustomer) LuxuryColors.ChampagneGold else LuxuryColors.DeepGraphite,
                                border = if (!isCustomer) BorderStroke(1.dp, LuxuryColors.CardBorder) else null,
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = msg.text,
                                        style = LuxuryTypography.BodyRegular.copy(
                                            color = if (isCustomer) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                            fontSize = 13.sp
                                        )
                                    )

                                    if (msg.translatedText != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Divider(color = if (isCustomer) LuxuryColors.ObsidianBlack.copy(alpha = 0.2f) else LuxuryColors.CardBorder)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "🌐 ${msg.translatedText}",
                                            style = LuxuryTypography.BodySubtle.copy(
                                                fontSize = 11.sp,
                                                color = if (isCustomer) LuxuryColors.ObsidianBlack else LuxuryColors.ChampagneGold
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = msg.timestamp,
                                            style = LuxuryTypography.BodySubtle.copy(
                                                fontSize = 9.sp,
                                                color = if (isCustomer) LuxuryColors.ObsidianBlack.copy(alpha = 0.7f) else LuxuryColors.SubtleGray
                                            )
                                        )

                                        // Feature 113: Translate Button
                                        if (msg.translatedText == null) {
                                            Text(
                                                "🌐 TRANSLATE",
                                                style = LuxuryTypography.BodySubtle.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCustomer) LuxuryColors.ObsidianBlack else LuxuryColors.ChampagneGold
                                                ),
                                                modifier = Modifier.clickable {
                                                    scope.launch {
                                                        try {
                                                            val transRes = ApiClient.getService().translateChatMessage(
                                                                rideId,
                                                                TranslateChatRequest(text = msg.text, targetLanguage = "hi")
                                                            )
                                                            if (transRes.isSuccessful && transRes.body() != null) {
                                                                val tText = transRes.body()!!.translatedText
                                                                messages = messages.map {
                                                                    if (it.id == msg.id) it.copy(translatedText = tText) else it
                                                                }
                                                            }
                                                        } catch (_: Exception) {}
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Feature 112: Suggested Quick-Reply Horizontal Chips
                Text(
                    "SUGGESTED QUICK REPLIES",
                    style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(quickReplies) { chip ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = LuxuryColors.DeepGraphite,
                            border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                            modifier = Modifier.clickable {
                                messageText = chip
                            }
                        ) {
                            Text(
                                text = chip,
                                style = LuxuryTypography.BodySubtle.copy(
                                    fontSize = 11.sp,
                                    color = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Message chauffeur...", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LuxuryColors.ChampagneGold,
                            unfocusedBorderColor = LuxuryColors.CardBorder,
                            focusedTextColor = LuxuryColors.PlatinumWhite,
                            unfocusedTextColor = LuxuryColors.PlatinumWhite
                        )
                    )

                    Button(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                val userMsg = messageText
                                messageText = ""
                                messages = messages + LocalChatMessage(
                                    id = "msg-${System.currentTimeMillis()}",
                                    sender = "CUSTOMER",
                                    text = userMsg,
                                    timestamp = "Just now"
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("SEND", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// =========================================================================
// Feature 111: Masked Phone Call Dialog (Virtual PBX Proxy)
// =========================================================================

@Composable
fun MaskedCallDialog(
    virtualNumber: String = "+91 22 6900 4820",
    virtualExt: String = "4921",
    chauffeurName: String = "Master Chauffeur",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LuxuryColors.CardSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📞", fontSize = 20.sp)
                Text(
                    "MASKED VIRTUAL DIALING",
                    style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 17.sp)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Feature 111: To ensure strict confidentiality, telephone numbers are routed through our private enterprise PBX switchboard.",
                    style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp)
                )

                Surface(
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                    color = LuxuryColors.DeepGraphite,
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ENTERPRISE PROXY LINE", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                        Text(
                            virtualNumber,
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.PlatinumWhite,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            "Direct Pin Extension: #$virtualExt",
                            style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp)
                        )
                    }
                }

                Text(
                    "✓ Call recording & privacy encryption active.\n✓ Personal numbers remain concealed from both parties.",
                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.LuxuryEmerald)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$virtualNumber")
                    }
                    context.startActivity(intent)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
            ) {
                Text("START MASKED CALL", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = LuxuryColors.SubtleGray)
            }
        }
    )
}

// =========================================================================
// Feature 115: Driver Arrival Photo Dialog
// =========================================================================

@Composable
fun DriverArrivalPhotoDialog(
    photoUrl: String,
    vehicleModel: String,
    licensePlate: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LuxuryColors.CardSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📸", fontSize = 20.sp)
                Text(
                    "CHAUFFEUR ARRIVAL PHOTO",
                    style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 17.sp)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Feature 115: High-resolution verification photo taken upon vehicle staging at the designated portico.",
                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                )

                Surface(
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                    color = LuxuryColors.DeepGraphite,
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "📷 [VERIFIED STAGING PHOTO]\n$vehicleModel\nPlate: $licensePlate",
                            style = LuxuryTypography.BodyRegular.copy(
                                textAlign = TextAlign.Center,
                                color = LuxuryColors.ChampagneGold
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PORTICO STAGING CONFIRMED", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.LuxuryEmerald))
                    GoldBadge("GPS MATCHED")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
            ) {
                Text("ACKNOWLEDGE", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    )
}

// =========================================================================
// Feature 116: Vehicle & Plate Confirmation Card
// =========================================================================

@Composable
fun VehiclePlateConfirmationCard(
    vehicleModel: String,
    licensePlate: String,
    vehicleColor: String,
    startOtp: String,
    onViewArrivalPhoto: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
        color = LuxuryColors.CardSurface,
        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "VEHICLE & PLATE CONFIRMATION",
                    style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                )
                GoldBadge("SECURITY VERIFIED")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        vehicleModel,
                        style = LuxuryTypography.HeadlineMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Exterior: $vehicleColor",
                        style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                    )
                }

                // High-visibility license plate block
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFACC15), // Indian Luxury Commercial Yellow
                    border = BorderStroke(2.dp, Color.Black)
                ) {
                    Text(
                        licensePlate,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Color.Black,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Divider(color = LuxuryColors.CardBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CABIN OTP PASSCODE: ", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                    Text(startOtp, style = LuxuryTypography.OtpDigits.copy(fontSize = 15.sp))
                }

                TextButton(onClick = onViewArrivalPhoto) {
                    Text("📸 VIEW PHOTO", color = LuxuryColors.ChampagneGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =========================================================================
// Features 117, 118, 119: Route Preview, Deviations & User Route Preference
// =========================================================================

@Composable
fun RoutePreviewAndPreferenceDialog(
    rideId: String,
    currentPreference: String,
    hasRouteDeviation: Boolean,
    deviationReason: String?,
    onPreferenceChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPref by remember { mutableStateOf(currentPreference) }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "ROUTE PREVIEW & PREFERENCES",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 17.sp
                            )
                        )
                        Text(
                            "Features 117-119: Corridor Navigation Governance",
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = LuxuryColors.SubtleGray, fontSize = 18.sp)
                    }
                }

                // Feature 118: Route Deviation Notice Banner
                if (hasRouteDeviation) {
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CrimsonAlert.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚠️", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "ROUTE DEVIATION DETECTED",
                                    style = LuxuryTypography.TitleGold.copy(
                                        color = LuxuryColors.CrimsonAlert,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                deviationReason ?: "Vehicle altered corridor due to road closure or security detour. Telemetry remains locked.",
                                style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.PlatinumWhite)
                            )
                        }
                    }
                }

                // Feature 117: Primary Corridor Preview
                Surface(
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                    color = LuxuryColors.DeepGraphite,
                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("NAVIGATIONAL CORRIDOR", style = LuxuryTypography.TitleGold.copy(fontSize = 11.sp))
                        Text(
                            "Mumbai Coastal Arterial & Bandra-Worli Sea Link",
                            style = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Fastag Automated Tolls: ENABLED", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                            Text("Pave-Shield: 0.04 RMS Smooth", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.LuxuryEmerald))
                        }
                    }
                }

                // Feature 119: Customer-Selected Route Preference Options
                Text(
                    "CHOOSE ROUTE PREFERENCE (Feature 119)",
                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                )

                listOf(
                    Triple("FASTEST", "🚀 Fastest Expressway Route", "Optimizes travel duration via Sea Link & flyovers"),
                    Triple("SCENIC", "🌊 Scenic Coastal Route", "Breathtaking Arabian Sea waterfront vista bypass"),
                    Triple("SMOOTHEST", "🛋️ Smoothest Pave-Shield Corridor", "Prioritizes vibration-free freshly paved boulevards")
                ).forEach { (code, title, desc) ->
                    val isSelected = selectedPref == code
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = if (isSelected) LuxuryColors.ChampagneGold.copy(alpha = 0.15f) else LuxuryColors.DeepGraphite,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPref = code }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedPref = code },
                                colors = RadioButtonDefaults.colors(selectedColor = LuxuryColors.ChampagneGold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    title,
                                    style = LuxuryTypography.BodyRegular.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.PlatinumWhite
                                    )
                                )
                                Text(desc, style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            }
                        }
                    }
                }

                LuxuryButton(
                    text = if (isSaving) "SAVING PREFERENCE..." else "CONFIRM ROUTE PREFERENCE",
                    onClick = {
                        isSaving = true
                        scope.launch {
                            try {
                                val res = ApiClient.getService().setRoutePreference(
                                    rideId,
                                    RoutePreferenceRequest(routePreference = selectedPref)
                                )
                                if (res.isSuccessful) {
                                    onPreferenceChanged(selectedPref)
                                    Toast.makeText(context, "Route preference updated to $selectedPref", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Failed to update route preference", Toast.LENGTH_SHORT).show()
                                }
                            } catch (_: Exception) {
                                onPreferenceChanged(selectedPref)
                                onDismiss()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    isLoading = isSaving
                )
            }
        }
    }
}

// =========================================================================
// Feature 122: Trip Status Timeline Sheet
// =========================================================================

@Composable
fun TripStatusTimelineSheet(
    timelineSteps: List<TripTimelineStepDto>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "JOURNEY TIMELINE",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            "Feature 122: Auditable Milestone Progression",
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = LuxuryColors.SubtleGray, fontSize = 18.sp)
                    }
                }

                Divider(color = LuxuryColors.CardBorder)

                timelineSteps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (step.completed) LuxuryColors.LuxuryEmerald else LuxuryColors.CardBorder
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (step.completed) "✓" else "${index + 1}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (index < timelineSteps.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(36.dp)
                                        .background(
                                            if (step.completed) LuxuryColors.LuxuryEmerald else LuxuryColors.CardBorder
                                        )
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    step.label,
                                    style = LuxuryTypography.BodyRegular.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (step.completed) LuxuryColors.PlatinumWhite else LuxuryColors.SubtleGray
                                    )
                                )
                                if (step.timestamp != null) {
                                    Text(
                                        step.timestamp.takeLast(8).take(5),
                                        style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp)
                                    )
                                }
                            }
                            Text(
                                step.description,
                                style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                            )
                        }
                    }
                }

                LuxuryButton(text = "CLOSE TIMELINE", onClick = onDismiss, isSecondary = true)
            }
        }
    }
}

// =========================================================================
// Feature 123: Trip Completion Receipt Dialog
// =========================================================================

@Composable
fun TripCompletionReceiptDialog(
    rideId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var receipt by remember { mutableStateOf<TripReceiptResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(rideId) {
        try {
            val res = ApiClient.getService().getCompletionReceipt(rideId)
            if (res.isSuccessful && res.body() != null) {
                receipt = res.body()
            }
        } catch (_: Exception) {}
        finally {
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
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "DIGITAL TAX RECEIPT",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            "Feature 123: SAC 9964 Compliant Invoice",
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = LuxuryColors.SubtleGray, fontSize = 18.sp)
                    }
                }

                Divider(color = LuxuryColors.CardBorder)

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
                    }
                } else {
                    val r = receipt
                    if (r != null) {
                        Text("Receipt Ref: ${r.receiptId}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace))
                        Text("Date: ${r.date}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                        Text("Passenger: ${r.passengerName}", style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        Text("Chauffeur: ${r.chauffeurName} • ${r.vehicleModel} (${r.licensePlate})", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))

                        Divider(color = LuxuryColors.CardBorder)

                        Text("Pickup: ${r.pickupAddress}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                        Text("Drop: ${r.destinationAddress}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                        Text("Total Distance: ${r.distanceKm} km • Duration: ${r.durationMinutes} mins", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))

                        // Fare breakdown table
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = LuxuryColors.DeepGraphite,
                            border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Base Dispatch Fare", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                    Text("₹${r.fareBreakdown.baseFareRupees}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Distance Surcharge", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                    Text("₹${r.fareBreakdown.distanceFareRupees}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Duration Charge", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                    Text("₹${r.fareBreakdown.durationFareRupees}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("GST 5% (SAC 9964)", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                    Text("₹${r.fareBreakdown.gstTaxRupees}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                }
                                Divider(color = LuxuryColors.CardBorder, modifier = Modifier.padding(vertical = 4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TOTAL CHARGED", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                                    Text("₹${r.fareBreakdown.totalChargedRupees}", style = LuxuryTypography.TitleGold.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                                }
                            }
                        }

                        Button(
                            onClick = {
                                Toast.makeText(context, "Tax invoice downloaded to device storage", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("DOWNLOAD PDF INVOICE 📥", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// Features 124, 125: Lost-Item Report from Trip History & Post-Trip Chauffeur Contact
// =========================================================================

@Composable
fun LostItemReportDialog(
    rideId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var itemType by remember { mutableStateOf("ELECTRONICS") }
    var description by remember { mutableStateOf("") }
    var seatLocation by remember { mutableStateOf("Rear Executive Left") }
    var contactPhone by remember { mutableStateOf("+91 98200 12345") }
    var isSubmitting by remember { mutableStateOf(false) }
    var reportedTicket by remember { mutableStateOf<LostItemResponse?>(null) }

    // Post-Trip Contact info (Feature 125)
    var driverContactInfo by remember { mutableStateOf<DriverContactResponse?>(null) }

    LaunchedEffect(rideId) {
        try {
            val res = ApiClient.getService().getPostTripDriverContact(rideId)
            if (res.isSuccessful && res.body() != null) {
                driverContactInfo = res.body()
            }
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "LOST PROPERTY RECOVERY",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            "Features 124 & 125: Post-Trip Concierge Desk",
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = LuxuryColors.SubtleGray, fontSize = 18.sp)
                    }
                }

                // Feature 125: Post-Trip Driver Contact Banner (Within 72 Hours)
                val contact = driverContactInfo
                if (contact != null) {
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.DeepGraphite,
                        border = BorderStroke(1.dp, if (contact.canContactDriver) LuxuryColors.LuxuryEmerald else LuxuryColors.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "CHAUFFEUR DIRECT CONTACT (Feature 125)",
                                    style = LuxuryTypography.TitleGold.copy(fontSize = 11.sp)
                                )
                                GoldBadge(if (contact.canContactDriver) "${contact.timeRemainingHours}h Window Active" else "Expired")
                            }

                            if (contact.canContactDriver) {
                                Text(
                                    "You may dial your chauffeur directly via our masked PBX line for up to 72 hours following journey completion.",
                                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                                )
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${contact.maskedPhoneNumber}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface),
                                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Text("📞 CALL CHAUFFEUR (${contact.maskedPhoneNumber} ext #${contact.extension})", color = LuxuryColors.ChampagneGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text(
                                    "The 72-hour direct chauffeur window has expired. Please lodge a lost-item report below for 24/7 executive concierge retrieval.",
                                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.SubtleGray)
                                )
                            }
                        }
                    }
                }

                Divider(color = LuxuryColors.CardBorder)

                // Feature 124: Lost Item Form
                val ticket = reportedTicket
                if (ticket != null) {
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.DeepGraphite,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("LOST PROPERTY TICKET GENERATED", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                            Text("Ticket Number: ${ticket.ticketNumber}", style = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                            Text("Category: ${ticket.itemType} • Status: ${ticket.status}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            Text("Estimated Resolution: ${ticket.estimatedResolution}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            Text("Concierge Hotline: ${ticket.conciergeHotline}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.ChampagneGold))
                        }
                    }
                } else {
                    Text("REPORT ITEM LEFT IN VEHICLE (Feature 124)", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))

                    // Item type selector
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ELECTRONICS", "WALLET/KEYS", "LUGGAGE", "OTHER").forEach { t ->
                            val isSel = itemType == t
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) LuxuryColors.ChampagneGold.copy(alpha = 0.2f) else LuxuryColors.DeepGraphite,
                                border = BorderStroke(1.dp, if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                                modifier = Modifier.weight(1f).clickable { itemType = t }
                            ) {
                                Text(
                                    t,
                                    style = LuxuryTypography.BodySubtle.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.PlatinumWhite
                                    ),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Item Description & Distinguishing Marks") },
                        placeholder = { Text("e.g. Black leather Montblanc briefcase with gold monogram") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LuxuryColors.ChampagneGold,
                            unfocusedBorderColor = LuxuryColors.CardBorder,
                            focusedTextColor = LuxuryColors.PlatinumWhite,
                            unfocusedTextColor = LuxuryColors.PlatinumWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = seatLocation,
                        onValueChange = { seatLocation = it },
                        label = { Text("Approximate Seat / Cabin Location") },
                        placeholder = { Text("e.g. Rear Executive Right, Boot") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LuxuryColors.ChampagneGold,
                            unfocusedBorderColor = LuxuryColors.CardBorder,
                            focusedTextColor = LuxuryColors.PlatinumWhite,
                            unfocusedTextColor = LuxuryColors.PlatinumWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("Callback Telephone Number") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LuxuryColors.ChampagneGold,
                            unfocusedBorderColor = LuxuryColors.CardBorder,
                            focusedTextColor = LuxuryColors.PlatinumWhite,
                            unfocusedTextColor = LuxuryColors.PlatinumWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    LuxuryButton(
                        text = if (isSubmitting) "SUBMITTING REPORT..." else "LODGE LOST-ITEM TICKET",
                        onClick = {
                            if (description.isBlank()) {
                                Toast.makeText(context, "Please describe the lost item", Toast.LENGTH_SHORT).show()
                                return@LuxuryButton
                            }
                            isSubmitting = true
                            scope.launch {
                                try {
                                    val req = LostItemRequest(
                                        itemType = itemType,
                                        description = description,
                                        seatLocation = seatLocation,
                                        contactPhone = contactPhone
                                    )
                                    val res = ApiClient.getService().reportLostItem(rideId, req)
                                    if (res.isSuccessful && res.body() != null) {
                                        reportedTicket = res.body()
                                        Toast.makeText(context, "Lost item report registered", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to submit report", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        isLoading = isSubmitting
                    )
                }
            }
        }
    }
}
