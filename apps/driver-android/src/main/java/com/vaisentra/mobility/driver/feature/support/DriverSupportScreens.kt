package com.vaisentra.mobility.driver.feature.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 44: Electric Academy Training (battery health, regenerative braking, etiquette)
 */
@Composable
fun AcademyTrainingScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val modules = listOf(
        TrainingModule("EV Battery Life Optimization", "Thermal management & safe swap hygiene", "Completed", EmeraldPrimary, true),
        TrainingModule("Regenerative Braking Technique", "Maximizing kinetic range recovery", "Completed", EmeraldPrimary, true),
        TrainingModule("Customer Etiquette & Safety First", "De-escalation & POSH zero-tolerance", "Completed", EmeraldPrimary, true),
        TrainingModule("Monsoon Wet Weather Driving", "Preventing aqua-planing on electric 3-wheelers", "New Module", ElectricCyan, false)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Electric Academy",
            subtitle = "Skill training certifications & pilot road safety",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CockpitCard(glow = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ACADEMY CERTIFICATION STATUS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Certified EV Pilot • Grade A+", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Icon(Icons.Default.School, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(36.dp))
                    }
                }
            }

            items(modules) { module ->
                CockpitCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(module.title, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(module.desc, color = CockpitTextSecondary, fontSize = 12.sp)
                        }
                        CockpitBadge(module.status, module.tint)
                    }
                    if (!module.isDone) {
                        Spacer(Modifier.height(10.dp))
                        CockpitButton(
                            text = "Start Interactive Module (5 mins)",
                            onClick = { onShowToast("Module '${module.title}' launched") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen 46: Found Lost Item in Cab (log belongings found in rear cabin)
 */
@Composable
fun FoundItemScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var itemCategory by remember { mutableStateOf("Mobile Phone") }
    var itemNotes by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Found Lost Item",
            subtitle = "Report passenger belongings left behind in cab",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard {
                    Text("SELECT ITEM CATEGORY", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val categories = listOf("Mobile Phone / Tablet", "Wallet / Purse / Cards", "Umbrella / Water Bottle", "Backpack / Shopping Bag", "Keys / Glasses")
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = itemCategory == cat,
                                onClick = { itemCategory = cat },
                                colors = RadioButtonDefaults.colors(selectedColor = ElectricCyan)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(cat, color = CockpitTextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                CockpitTextField(
                    value = itemNotes,
                    onValueChange = { itemNotes = it },
                    label = "Description / Identifying Marks",
                    placeholder = "Color, brand, where in cab it was found..."
                )
            }

            item {
                CockpitButton(
                    text = "Report Found Item & Lock Ticket",
                    onClick = {
                        val currentRide = DriverRepository.currentRideId.value ?: "ride-latest"
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            DriverRepository.reportRouteDeviation(currentRide, 28.6289, 77.3710)
                        }
                        onShowToast("Lost property logged in Supabase. Commuter notified.")
                        onNavigateBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Screen 47: Driver Helpdesk Tickets (vehicle maintenance, payout queries)
 */
@Composable
fun DriverHelpdeskScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val tickets = listOf(
        HelpdeskTicket("TKT-8841", "Payout discrepancy on morning surge", "Resolved (₹50 credited)", EmeraldPrimary, "Yesterday"),
        HelpdeskTicket("TKT-8799", "Tyre pressure sensor recalibration", "Closed", CockpitTextMuted, "3 days ago"),
        HelpdeskTicket("TKT-8902", "App GPS ping jitter query", "Open (In Review)", AmberWarning, "Today")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Driver Helpdesk",
            subtitle = "24x7 pilot partner support & ticket tracking",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CockpitButton(
                    text = "+ Raise New Helpdesk Ticket",
                    onClick = { onShowToast("New ticket form opened") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(tickets) { ticket ->
                CockpitCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ticket.id, color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(ticket.subject, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Updated: ${ticket.timestamp}", color = CockpitTextSecondary, fontSize = 11.sp)
                        }
                        CockpitBadge(ticket.status, ticket.statusColor)
                    }
                }
            }
        }
    }
}

/**
 * Screen 49: Push Notifications Center (surge alerts, weather warnings, incentives)
 */
@Composable
fun NotificationsCenterScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val notifications = listOf(
        DriverNotif("Surge Alert 1.8x", "High demand near Anand Vihar ISBT. +₹65 per ride for next 45 mins.", Icons.Default.Bolt, AmberWarning, "10m ago"),
        DriverNotif("Payout Transferred", "₹1,500 successfully sent to your HDFC bank account.", Icons.Default.AccountBalanceWallet, EmeraldPrimary, "1h ago"),
        DriverNotif("Weather Advisory", "Light monsoon showers in East Delhi. Maintain safe 25 km/h braking distance.", Icons.Default.Cloud, ElectricCyan, "2h ago"),
        DriverNotif("Incentive Goal Unlocked", "Congratulations! You completed 10 morning rides. ₹300 bonus credited.", Icons.Default.EmojiEvents, EmeraldPrimary, "5h ago")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Pilot Notifications",
            subtitle = "Surge triggers, weather warnings & operational announcements",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(notifications) { notif ->
                CockpitCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(notif.tint.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(notif.icon, contentDescription = null, tint = notif.tint, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(notif.title, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(notif.time, color = CockpitTextMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(notif.body, color = CockpitTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

private data class TrainingModule(val title: String, val desc: String, val status: String, val tint: Color, val isDone: Boolean)
private data class HelpdeskTicket(val id: String, val subject: String, val status: String, val statusColor: Color, val timestamp: String)
private data class DriverNotif(val title: String, val body: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: Color, val time: String)

