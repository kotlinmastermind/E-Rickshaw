package com.vaisentra.mobility.rider.feature.demo

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.core.designsystem.ElectricNeoTokens
import com.vaisentra.mobility.core.designsystem.NeoCard
import com.vaisentra.mobility.core.designsystem.NeoGlowButton
import com.vaisentra.mobility.core.designsystem.NeoStatPill
import com.vaisentra.mobility.core.designsystem.NeoTextField

data class CustomerPopupMeta(
    val id: Int,
    val title: String,
    val triggerContext: String,
    val description: String,
    val icon: String,
    val alertLevel: String // INFO, WARNING, CRITICAL, SUCCESS
)

object CustomerPopupCatalog {
    val POPUPS = listOf(
        CustomerPopupMeta(
            id = 1,
            title = "Ride Cancellation Confirmation",
            triggerContext = "Commuter cancels active dispatch",
            description = "BottomSheet verifying reason for cancellation and applicable fee disclaimer.",
            icon = "🛑",
            alertLevel = "WARNING"
        ),
        CustomerPopupMeta(
            id = 2,
            title = "SOS 112 Emergency Panic Alert",
            triggerContext = "Commuter triggers SOS in transit",
            description = "High-priority dialog confirming 5-second countdown to notify Delhi Police 112 & SOC.",
            icon = "🚨",
            alertLevel = "CRITICAL"
        ),
        CustomerPopupMeta(
            id = 3,
            title = "Promo Voucher Applied",
            triggerContext = "Commuter enters promo code",
            description = "Modal celebration showing ₹65 instant discount on current ride fare.",
            icon = "🎟️",
            alertLevel = "SUCCESS"
        ),
        CustomerPopupMeta(
            id = 4,
            title = "Pilot Arrived at Gate Buzzer",
            triggerContext = "Driver arrives at pickup point",
            description = "Interactive arrival alert with photo of meeting spot, vehicle plate & horn buzzer.",
            icon = "🎯",
            alertLevel = "INFO"
        ),
        CustomerPopupMeta(
            id = 5,
            title = "Low Wallet Balance Top-Up",
            triggerContext = "Wallet balance below ride fare",
            description = "BottomSheet allowing 1-tap UPI recharge of ₹100, ₹200, or ₹500 before trip start.",
            icon = "💰",
            alertLevel = "WARNING"
        ),
        CustomerPopupMeta(
            id = 6,
            title = "Split Fare Request & Contacts",
            triggerContext = "Commuter initiates bill split",
            description = "Modal to pick up to 3 co-passengers and send UPI collect links.",
            icon = "➗",
            alertLevel = "INFO"
        ),
        CustomerPopupMeta(
            id = 7,
            title = "In-Cabin Audio Recording Consent",
            triggerContext = "Commuter enables safety audio",
            description = "Privacy and compliance dialog detailing encrypted ambient audio storage.",
            icon = "🎙️",
            alertLevel = "INFO"
        ),
        CustomerPopupMeta(
            id = 8,
            title = "Lost Item Report Acknowledgment",
            triggerContext = "Commuter submits lost item report",
            description = "Modal confirmation with support ticket number and driver contact lockdown.",
            icon = "🎒",
            alertLevel = "SUCCESS"
        ),
        CustomerPopupMeta(
            id = 9,
            title = "Green Club Tier Upgrade",
            triggerContext = "Commuter crosses 20 green rides",
            description = "Celebratory badge modal promoting rider to Silver Green Tier with 10% discount.",
            icon = "🏆",
            alertLevel = "SUCCESS"
        ),
        CustomerPopupMeta(
            id = 10,
            title = "Driver Rating & Tip Sheet",
            triggerContext = "Trip completion post-drop",
            description = "Interactive 5-star rating, driver compliment tags, and ₹10/₹20/₹50 quick tips.",
            icon = "⭐",
            alertLevel = "INFO"
        ),
        CustomerPopupMeta(
            id = 11,
            title = "High Demand Surge Notice",
            triggerContext = "Booking during rain or rush hour",
            description = "Dynamic surge notice explaining 1.35x multiplier due to heavy passenger demand.",
            icon = "⚡",
            alertLevel = "WARNING"
        ),
        CustomerPopupMeta(
            id = 12,
            title = "Out of Geofence Warning",
            triggerContext = "Destination beyond EV operational zone",
            description = "Alert indicating drop location is outside 25 km service perimeter.",
            icon = "🗺️",
            alertLevel = "WARNING"
        ),
        CustomerPopupMeta(
            id = 13,
            title = "GPS Precision Calibration",
            triggerContext = "Weak satellite location accuracy",
            description = "Modal guidance to turn on High Accuracy Wi-Fi scanning for accurate boarding pin.",
            icon = "📡",
            alertLevel = "INFO"
        ),
        CustomerPopupMeta(
            id = 14,
            title = "Biometric Passkey Enrolment",
            triggerContext = "First sign-in on new device",
            description = "Prompt to activate Fingerprint or Face Unlock for fast, passwordless rides.",
            icon = "🔐",
            alertLevel = "INFO"
        ),
        CustomerPopupMeta(
            id = 15,
            title = "Airport Flight Delay Reschedule",
            triggerContext = "Flight AI-201 delayed by 45 min",
            description = "Automated proactive alert offering to postpone scheduled airport pickup at zero fee.",
            icon = "✈️",
            alertLevel = "INFO"
        )
    )
}

@Composable
fun CustomerPopupInteractiveModal(
    popupId: Int,
    onDismiss: () -> Unit,
    onActionCompleted: (String) -> Unit
) {
    val meta = CustomerPopupCatalog.POPUPS.find { it.id == popupId }
        ?: CustomerPopupCatalog.POPUPS[0]

    var selectedReason by remember { mutableStateOf("Driver taking too long") }
    var selectedTip by remember { mutableStateOf("₹20") }
    var starRating by remember { mutableIntStateOf(5) }
    var inputAmount by remember { mutableStateOf("200") }
    var sosCountdown by remember { mutableIntStateOf(5) }

    val accentColor = when (meta.alertLevel) {
        "CRITICAL" -> ElectricNeoTokens.CyberCrimson
        "WARNING" -> ElectricNeoTokens.SolarAmber
        "SUCCESS" -> ElectricNeoTokens.NeonEmerald
        else -> ElectricNeoTokens.ElectricCyan
    }

    // Modal Scrim Container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clickable(enabled = false) {} // Prevent click through
                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusLarge))
                .border(1.5.dp, accentColor, RoundedCornerShape(ElectricNeoTokens.RadiusLarge)),
            color = ElectricNeoTokens.CyberSurfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with Badge & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.2f))
                                .border(1.5.dp, accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(meta.icon, fontSize = 20.sp)
                        }
                        Column {
                            Text(
                                text = "POPUP #${meta.id} • ${meta.alertLevel}",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = meta.title,
                                color = ElectricNeoTokens.TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ElectricNeoTokens.CyberSurfaceHighlight)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = ElectricNeoTokens.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = meta.description,
                    color = ElectricNeoTokens.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Interactive Controls per Popup ID
                when (meta.id) {
                    1 -> { // Ride Cancellation
                        Text("Select Cancellation Reason:", color = ElectricNeoTokens.TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf("Driver taking too long", "Changed my mind", "Booked wrong location", "Found alternate ride").forEach { reason ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                    .background(if (selectedReason == reason) ElectricNeoTokens.CyberSurfaceHighlight else ElectricNeoTokens.CyberSurface)
                                    .clickable { selectedReason = reason }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(reason, color = ElectricNeoTokens.TextWhite, fontSize = 12.sp)
                                if (selectedReason == reason) {
                                    Text("●", color = ElectricNeoTokens.SolarAmber, fontSize = 14.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        NeoGlowButton(
                            text = "Confirm Cancellation (₹0 Fee)",
                            onClick = {
                                onActionCompleted("Ride cancelled: $selectedReason")
                                onDismiss()
                            },
                            accentColor = ElectricNeoTokens.SolarAmber,
                            textColor = Color.Black
                        )
                    }

                    2 -> { // SOS Emergency Panic
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                .background(ElectricNeoTokens.CyberCrimsonGlow)
                                .border(1.dp, ElectricNeoTokens.CyberCrimsonBorder, RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TRANSMITTING TO 112 IN", color = ElectricNeoTokens.CyberCrimson, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                Text("$sosCountdown", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                Text("Vehicle GPS, Audio & Pilot ID sharing active", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                    .background(ElectricNeoTokens.CyberSurfaceHighlight)
                                    .clickable {
                                        onActionCompleted("SOS Cancelled by Rider")
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Cancel Alert", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                    .background(ElectricNeoTokens.CyberCrimson)
                                    .clickable {
                                        onActionCompleted("112 Emergency Dispatched Immediately!")
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("CALL 112 NOW", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        }
                    }

                    3 -> { // Promo Applied
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                .background(ElectricNeoTokens.NeonEmeraldGlow)
                                .border(1.dp, ElectricNeoTokens.NeonEmeraldBorder, RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("CODE: ELECTRIC50", color = ElectricNeoTokens.NeonEmerald, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                Text("Flat 50% off on your current EV trip", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                            }
                            Text("-₹65.00", color = ElectricNeoTokens.NeonEmerald, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        NeoGlowButton(
                            text = "Awesome, Apply to Fare",
                            onClick = {
                                onActionCompleted("Coupon ELECTRIC50 applied successfully! Saved ₹65")
                                onDismiss()
                            },
                            accentColor = ElectricNeoTokens.NeonEmerald,
                            textColor = Color.Black
                        )
                    }

                    4 -> { // Pilot Arrived at Gate Buzzer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NeoStatPill(label = "PLATE", value = "DL 1ER 8421", accentColor = ElectricNeoTokens.ElectricCyan)
                            NeoStatPill(label = "PILOT", value = "Rajesh K. (4.9★)", accentColor = ElectricNeoTokens.NeonEmerald)
                            NeoStatPill(label = "GATE", value = "Gate 2 (Main)", accentColor = ElectricNeoTokens.SolarAmber)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                    .background(ElectricNeoTokens.CyberSurfaceHighlight)
                                    .border(1.dp, ElectricNeoTokens.ElectricCyanBorder, RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                    .clickable { onActionCompleted("Buzzer sound sent to pilot's phone!") },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("📢 Sound Horn", color = ElectricNeoTokens.ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                    .background(ElectricNeoTokens.ElectricCyan)
                                    .clickable {
                                        onActionCompleted("Commuter walked to pilot. Showing 4-Digit OTP.")
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("I'm Coming Out ➔", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }

                    5 -> { // Low Wallet Balance Top-Up
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("₹100", "₹200", "₹500").forEach { amount ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                        .background(if (inputAmount == amount.removePrefix("₹")) ElectricNeoTokens.NeonEmerald else ElectricNeoTokens.CyberSurface)
                                        .clickable { inputAmount = amount.removePrefix("₹") }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = amount,
                                        color = if (inputAmount == amount.removePrefix("₹")) Color.Black else ElectricNeoTokens.TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        NeoGlowButton(
                            text = "Recharge ₹$inputAmount via UPI Instant",
                            onClick = {
                                onActionCompleted("Wallet topped up by ₹$inputAmount via UPI")
                                onDismiss()
                            },
                            accentColor = ElectricNeoTokens.NeonEmerald,
                            textColor = Color.Black
                        )
                    }

                    10 -> { // Driver Rating & Tip Sheet
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            (1..5).forEach { star ->
                                Text(
                                    text = if (star <= starRating) "⭐" else "☆",
                                    fontSize = 28.sp,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clickable { starRating = star }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Add Pilot Tip (100% goes to driver):", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("₹0", "₹10", "₹20", "₹50").forEach { tip ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                        .background(if (selectedTip == tip) ElectricNeoTokens.SolarAmber else ElectricNeoTokens.CyberSurface)
                                        .clickable { selectedTip = tip }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tip,
                                        color = if (selectedTip == tip) Color.Black else ElectricNeoTokens.TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        NeoGlowButton(
                            text = "Submit $starRating★ Rating & Tip",
                            onClick = {
                                onActionCompleted("Rated pilot $starRating★ with tip $selectedTip")
                                onDismiss()
                            },
                            accentColor = ElectricNeoTokens.SolarAmber,
                            textColor = Color.Black
                        )
                    }

                    else -> { // Generic Interactive Action Modal for other popups
                        NeoCard {
                            Text("Authoritative Event Parameter", color = ElectricNeoTokens.TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Context: ${meta.triggerContext}", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NeoStatPill(label = "POPUP ID", value = "#${meta.id}")
                                NeoStatPill(label = "STATUS", value = "READY", accentColor = accentColor)
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        NeoGlowButton(
                            text = "Confirm & Acknowledge",
                            onClick = {
                                onActionCompleted("Action acknowledged for Popup #${meta.id}: ${meta.title}")
                                onDismiss()
                            },
                            accentColor = accentColor,
                            textColor = if (accentColor == ElectricNeoTokens.CyberCrimson) Color.White else Color.Black
                        )
                    }
                }
            }
        }
    }
}
