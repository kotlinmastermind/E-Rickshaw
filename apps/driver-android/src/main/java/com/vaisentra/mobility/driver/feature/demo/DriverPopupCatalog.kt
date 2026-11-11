package com.vaisentra.mobility.driver.feature.demo

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.core.designsystem.ElectricNeoTokens
import com.vaisentra.mobility.core.designsystem.NeoBatteryGauge
import com.vaisentra.mobility.core.designsystem.NeoCard
import com.vaisentra.mobility.core.designsystem.NeoGlowButton
import com.vaisentra.mobility.core.designsystem.NeoStatPill
import com.vaisentra.mobility.core.designsystem.NeoTextField

data class DriverPopupMeta(
    val id: Int,
    val title: String,
    val triggerContext: String,
    val description: String,
    val icon: String,
    val alertLevel: String // INFO, WARNING, CRITICAL, SUCCESS
)

object DriverPopupCatalog {
    val POPUPS = listOf(
        DriverPopupMeta(
            id = 1,
            title = "Incoming Dispatch Ride Offer",
            triggerContext = "Radar matches commuter trip",
            description = "15-second countdown sheet showing ₹145 fare, pickup 450m away, destination & instant Accept/Decline.",
            icon = "🚖",
            alertLevel = "WARNING"
        ),
        DriverPopupMeta(
            id = 2,
            title = "Rider Cancelled Trip",
            triggerContext = "Commuter aborts en-route",
            description = "Notification confirming ride cancellation by commuter with ₹25 compensation credited to pilot wallet.",
            icon = "🛑",
            alertLevel = "INFO"
        ),
        DriverPopupMeta(
            id = 3,
            title = "Low Battery Depot Reroute",
            triggerContext = "State of charge below 18%",
            description = "Urgent battery advisory routing pilot to nearest fast-swap charging station (Indirapuram Hub).",
            icon = "🪫",
            alertLevel = "WARNING"
        ),
        DriverPopupMeta(
            id = 4,
            title = "Mandatory Driver Fatigue Break",
            triggerContext = "4.5 continuous hours on duty",
            description = "Safety compliance lock enforcing 15-minute rest & hydration cooldown before next dispatch.",
            icon = "☕",
            alertLevel = "WARNING"
        ),
        DriverPopupMeta(
            id = 5,
            title = "Overspeeding Telemetry Alert",
            triggerContext = "Vehicle speed exceeds 45 km/h",
            description = "Audible alarm advising pilot to decelerate to prescribed 35 km/h e-rickshaw urban corridor limit.",
            icon = "⚠️",
            alertLevel = "CRITICAL"
        ),
        DriverPopupMeta(
            id = 6,
            title = "Cash Fare Collection Confirmer",
            triggerContext = "Trip completion on Cash Ride",
            description = "Interactive collection counter: Collect exact ₹160 cash, enter tender received, calculate change.",
            icon = "💵",
            alertLevel = "INFO"
        ),
        DriverPopupMeta(
            id = 7,
            title = "Commuter No-Show Expiry",
            triggerContext = "Waiting at pickup > 5 minutes",
            description = "Grace waiting period expired. Pilot can cancel without penalty and claim ₹30 no-show compensation.",
            icon = "⏱️",
            alertLevel = "INFO"
        ),
        DriverPopupMeta(
            id = 8,
            title = "Route Deviation Anomaly",
            triggerContext = "Vehicle deviates > 300m from GPS line",
            description = "Automated safety prompt inquiring if road is blocked or passenger requested alternate waypoint.",
            icon = "🧭",
            alertLevel = "WARNING"
        ),
        DriverPopupMeta(
            id = 9,
            title = "Document Expiry Urgent Notice",
            triggerContext = "Commercial Driving License expires in 3 days",
            description = "Notice to upload renewed transport badge/DL to avoid automated duty dispatch suspension.",
            icon = "📑",
            alertLevel = "WARNING"
        ),
        DriverPopupMeta(
            id = 10,
            title = "Pilot SOS Panic Dispatched",
            triggerContext = "Driver hits emergency SOS button",
            description = "Emergency confirmation: live cabin telemetry sent to 24/7 SOC desk, calling pilot phone.",
            icon = "🚨",
            alertLevel = "CRITICAL"
        ),
        DriverPopupMeta(
            id = 11,
            title = "Odometer Closing Discrepancy",
            triggerContext = "Shift closing reading mismatch",
            description = "Odometer reading entered is 4 km less than GPS tracked shift distance. Retake photo proof.",
            icon = "🛣️",
            alertLevel = "WARNING"
        ),
        DriverPopupMeta(
            id = 12,
            title = "Daily Peak Bonus Unlocked",
            triggerContext = "Completed 10 rides in morning peak",
            description = "Celebration banner crediting ₹300 incentive bonus directly to today's earnings balance.",
            icon = "🎉",
            alertLevel = "SUCCESS"
        ),
        DriverPopupMeta(
            id = 13,
            title = "CSAT Rating Advisory Alert",
            triggerContext = "Rating dips to 4.52★",
            description = "Quality guidelines review: Ensure vehicle cleanliness, greet polite namaste, check tire pressure.",
            icon = "⭐",
            alertLevel = "WARNING"
        ),
        DriverPopupMeta(
            id = 14,
            title = "Instant IMPS Payout Completed",
            triggerContext = "Pilot withdraws ₹800 to UPI/Bank",
            description = "Success modal displaying Bank IMPS RRN reference #4391028471 with instant account credit.",
            icon = "⚡",
            alertLevel = "SUCCESS"
        ),
        DriverPopupMeta(
            id = 15,
            title = "Toll & Municipal Surcharge Entry",
            triggerContext = "Crossing state or MCD toll gate",
            description = "Add verified MCD border entry tax (₹100) directly into the commuter's final digital invoice.",
            icon = "🛂",
            alertLevel = "INFO"
        )
    )
}

@Composable
fun DriverPopupInteractiveModal(
    popupId: Int,
    onDismiss: () -> Unit,
    onActionCompleted: (String) -> Unit
) {
    val meta = DriverPopupCatalog.POPUPS.find { it.id == popupId }
        ?: DriverPopupCatalog.POPUPS[0]

    var tenderReceived by remember { mutableStateOf("200") }
    var tollAmount by remember { mutableStateOf("100") }
    var countdownSec by remember { mutableIntStateOf(15) }

    val accentColor = when (meta.alertLevel) {
        "CRITICAL" -> ElectricNeoTokens.CyberCrimson
        "WARNING" -> ElectricNeoTokens.SolarAmber
        "SUCCESS" -> ElectricNeoTokens.NeonEmerald
        else -> ElectricNeoTokens.NeonEmerald
    }

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
                .clickable(enabled = false) {}
                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusLarge))
                .border(1.5.dp, accentColor, RoundedCornerShape(ElectricNeoTokens.RadiusLarge)),
            color = ElectricNeoTokens.CyberSurfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
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
                                text = "PILOT POPUP #${meta.id} • ${meta.alertLevel}",
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

                // Custom Content per Popup ID
                when (meta.id) {
                    1 -> { // Incoming Ride Offer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            NeoStatPill(label = "NET EARNING", value = "₹145.00", accentColor = ElectricNeoTokens.NeonEmerald)
                            NeoStatPill(label = "PICKUP", value = "450 M", accentColor = ElectricNeoTokens.ElectricCyan)
                            NeoStatPill(label = "TRIP DIST", value = "3.8 KM", accentColor = ElectricNeoTokens.SolarAmber)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                .background(ElectricNeoTokens.CyberSurface)
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("📍 Pickup: Connaught Place Radial 3", color = ElectricNeoTokens.TextWhite, fontSize = 11.sp)
                                Text("🎯 Drop: Pragati Maidan Gate 4", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                    .background(ElectricNeoTokens.CyberCrimsonGlow)
                                    .border(1.dp, ElectricNeoTokens.CyberCrimsonBorder, RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                    .clickable {
                                        onActionCompleted("Ride offer declined. Radar active.")
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Decline", color = ElectricNeoTokens.CyberCrimson, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(2f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                    .background(ElectricNeoTokens.NeonEmerald)
                                    .clickable {
                                        onActionCompleted("RIDE ACCEPTED! Starting navigation to pickup.")
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✓ ACCEPT (12s)", color = Color.Black, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    3 -> { // Low Battery Warning
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Remaining SoC", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                                Text("16% Critical", color = ElectricNeoTokens.SolarAmber, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            }
                            NeoBatteryGauge(batteryPercent = 16, isCharging = false)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Nearest Swap Hub: Anand Vihar Metro EV Depot (1.2 km)", color = ElectricNeoTokens.TextWhite, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        NeoGlowButton(
                            text = "Route to Nearest Swap Station ➔",
                            onClick = {
                                onActionCompleted("Navigation directed to Indirapuram EV Battery Station")
                                onDismiss()
                            },
                            accentColor = ElectricNeoTokens.SolarAmber,
                            textColor = Color.Black
                        )
                    }

                    6 -> { // Cash Collection Confirmer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Bill Amount", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                                Text("₹ 160.00", color = ElectricNeoTokens.NeonEmerald, fontSize = 26.sp, fontWeight = FontWeight.Black)
                            }
                            NeoStatPill(label = "PAYMENT", value = "CASH ON DELIVERY")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        NeoTextField(
                            value = tenderReceived,
                            onValueChange = { tenderReceived = it },
                            placeholder = "Cash Received from Rider (e.g. ₹200)"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val tenderVal = tenderReceived.toIntOrNull() ?: 160
                        val changeVal = (tenderVal - 160).coerceAtLeast(0)
                        Text("Change to Return to Commuter: ₹$changeVal", color = ElectricNeoTokens.ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(14.dp))
                        NeoGlowButton(
                            text = "Confirm ₹160 Cash Collected",
                            onClick = {
                                onActionCompleted("₹160 Cash collected. Trip closed in ledger.")
                                onDismiss()
                            },
                            accentColor = ElectricNeoTokens.NeonEmerald,
                            textColor = Color.Black
                        )
                    }

                    12 -> { // Daily Bonus Unlocked
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                .background(ElectricNeoTokens.NeonEmeraldGlow)
                                .border(1.dp, ElectricNeoTokens.NeonEmeraldBorder, RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CONGRATULATIONS PILOT!", color = ElectricNeoTokens.NeonEmerald, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                Text("+₹ 300.00", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                Text("10 Morning Peak Rides Completed (100% Acceptance)", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        NeoGlowButton(
                            text = "Claim ₹300 into Wallet ➔",
                            onClick = {
                                onActionCompleted("₹300 peak bonus credited to pilot balance!")
                                onDismiss()
                            },
                            accentColor = ElectricNeoTokens.NeonEmerald,
                            textColor = Color.Black
                        )
                    }

                    14 -> { // IMPS Instant Payout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Settlement Amount", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                                Text("₹ 800.00", color = ElectricNeoTokens.NeonEmerald, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            }
                            NeoStatPill(label = "MODE", value = "IMPS INSTANT", accentColor = ElectricNeoTokens.NeonEmerald)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Bank Account: State Bank of India •••• 9214", color = ElectricNeoTokens.TextWhite, fontSize = 12.sp)
                        Text("UTR Ref: IMPS-VAI-20261111-9941", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        NeoGlowButton(
                            text = "Done & View Wallet Statement",
                            onClick = {
                                onActionCompleted("Instant settlement receipt acknowledged.")
                                onDismiss()
                            },
                            accentColor = ElectricNeoTokens.NeonEmerald,
                            textColor = Color.Black
                        )
                    }

                    15 -> { // Toll & Surcharge Entry
                        Text("Enter Municipal Toll / MCD Gate Slip:", color = ElectricNeoTokens.TextWhite, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        NeoTextField(
                            value = tollAmount,
                            onValueChange = { tollAmount = it },
                            placeholder = "Toll Amount (₹)"
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        NeoGlowButton(
                            text = "Add ₹$tollAmount to Commuter Bill",
                            onClick = {
                                onActionCompleted("₹$tollAmount added as verified toll to passenger receipt.")
                                onDismiss()
                            },
                            accentColor = ElectricNeoTokens.ElectricCyan,
                            textColor = Color.Black
                        )
                    }

                    else -> { // Generic Action Modal
                        NeoCard {
                            Text("Authoritative Telemetry Event", color = ElectricNeoTokens.TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Context: ${meta.triggerContext}", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NeoStatPill(label = "POPUP ID", value = "#${meta.id}")
                                NeoStatPill(label = "SEVERITY", value = meta.alertLevel, accentColor = accentColor)
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        NeoGlowButton(
                            text = "Confirm & Acknowledge Action",
                            onClick = {
                                onActionCompleted("Pilot acknowledged #${meta.id}: ${meta.title}")
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
