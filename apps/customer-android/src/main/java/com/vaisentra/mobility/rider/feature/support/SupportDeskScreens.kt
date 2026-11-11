package com.vaisentra.mobility.rider.feature.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.rider.components.*
import com.vaisentra.mobility.rider.theme.*

// Screen 41: Fare Dispute & Refund Claim
@Composable
fun FareDisputeScreen(onNavigate: (String) -> Unit) {
    var reason by remember { mutableStateOf("Driver took inefficient detour (+2.4 km)") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Dispute Trip Fare", subtitle = "Automated audit & instant wallet credit", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Text(text = "Trip: Sector 62 ➔ Connaught Place (₹129.04)", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(value = reason, onValueChange = { reason = it }, placeholder = "Describe route or fare discrepancy...", label = "Dispute Reason")
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(text = "Submit Dispute for Review", onClick = {
                DialogController.showToast("Dispute ticket logged in Supabase! Under review.")
                onNavigate("support_tickets")
            }, backgroundColor = AmberGold, textColor = CanvasBlack)
        }
    }
}

// Screen 42: Lost Item Concierge Desk
@Composable
fun LostItemScreen(onNavigate: (String) -> Unit) {
    var itemDescription by remember { mutableStateOf("Black leather wallet with metro smartcard") }
    var seatLocation by remember { mutableStateOf("Rear right seat") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Lost Item Concierge", subtitle = "Expedited pilot retrieval desk", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            NeoTextField(value = itemDescription, onValueChange = { itemDescription = it }, placeholder = "Item description", label = "Lost Article Details")
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(value = seatLocation, onValueChange = { seatLocation = it }, placeholder = "Where was it left?", label = "Vehicle Cabin Location")
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(text = "Report Lost Item to Concierge", onClick = {
                DialogController.show(RiderPopupType.LOST_ITEM)
            }, icon = "🎒")
        }
    }
}

// Screen 43: Support Ticket Helpdesk (Supabase: support_tickets)
@Composable
fun SupportTicketsScreen(onNavigate: (String) -> Unit) {
    val tickets = listOf(
        Triple("#TKT-8219 • Fare Recalculation", "STATUS: RESOLVED (₹35 CREDITED)", "Yesterday"),
        Triple("#TKT-7104 • Driver Cleanliness Report", "STATUS: INVESTIGATING", "05-Nov"),
        Triple("#TKT-6920 • UPI Double Debit Check", "STATUS: REFUNDED TO BANK", "28-Oct")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Support Helpdesk", subtitle = "Priority Tickets in Supabase", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tickets) { (title, status, date) ->
                NeoCard(cornerRadius = 14.dp) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = date, color = TextDim, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = status, color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Chat with Live Concierge", onClick = { onNavigate("support_chat") }, icon = "💬")
    }
}

// Screen 44: Live Support Executive Chat
@Composable
fun LiveSupportChatScreen(onNavigate: (String) -> Unit) {
    var msg by remember { mutableStateOf("") }
    val chats = remember {
        mutableStateListOf(
            Pair("Vaisentra Concierge", "Hello Bhishma! Welcome to RiderGo 24/7 Operations. How can I assist your commute today?"),
            Pair("You", "I wanted to check on the refund for my cancelled shared ride.")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Support Operations Chat", subtitle = "SLA < 2 Mins • Live Executive", onBack = { onNavigate("support_tickets") })
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chats) { (sender, text) ->
                val isMe = sender == "You"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                ) {
                    NeoCard(
                        backgroundColor = if (isMe) SapphirePrimary else SurfaceCard,
                        cornerRadius = 14.dp,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(text = sender, color = if (isMe) TextWhite else ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = text, color = TextWhite, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                NeoTextField(value = msg, onValueChange = { msg = it }, placeholder = "Type to concierge executive...")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SapphirePrimary)
                    .clickable {
                        if (msg.isNotBlank()) {
                            chats.add(Pair("You", msg))
                            msg = ""
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text(text = "➤", color = TextWhite, fontSize = 18.sp) }
        }
    }
}

// Screen 45: Safety Incident Formal Report
@Composable
fun SafetyIncidentReportScreen(onNavigate: (String) -> Unit) {
    var description by remember { mutableStateOf("Reckless overtakes near Akshardham flyover.") }
    var severity by remember { mutableStateOf("HIGH") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Formal Incident Report", subtitle = "Legal & Fleet Safety Audit", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard(borderColor = CrimsonRed) {
            Text(text = "Severity Classification: $severity", color = CrimsonRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(value = description, onValueChange = { description = it }, placeholder = "Describe safety incident...", label = "Incident Statement")
            Spacer(modifier = Modifier.height(12.dp))
            NeoBadge(text = "EVIDENCE PHOTO ATTACHED (1 FILE)", backgroundColor = SapphireGlow, textColor = ElectricCyan)
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(text = "Submit Formal Safety Complaint", onClick = {
                DialogController.showToast("Formal safety incident logged in Supabase! SOC alerted.")
                onNavigate("map_home")
            }, backgroundColor = CrimsonRed)
        }
    }
}

