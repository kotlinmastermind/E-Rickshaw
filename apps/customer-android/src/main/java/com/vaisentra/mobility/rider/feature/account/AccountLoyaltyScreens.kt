package com.vaisentra.mobility.rider.feature.account

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
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.theme.*

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
    val alerts = listOf(
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
            items(alerts) { (title, body, time) ->
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

// Screen 49: Saved Places & Favorite Pins
@Composable
fun SavedPlacesScreen(onNavigate: (String) -> Unit) {
    val places by RiderRepository.savedPlaces.collectAsState()

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Account & Privacy Hub", subtitle = "User ID: bhishma-rider-75620", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Text(text = "Security & FastApi Sessions", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            val settingsItems = listOf(
                "Biometric Passkey (Face ID / Fingerprint)" to "ACTIVE ✓",
                "Data Privacy & Telemetry Export" to "REQUEST JSON",
                "Connected Devices (1 Active Android Session)" to "DEVICE 1",
                "Supabase Role" to "CUSTOMER"
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
                    NeoBadge(text = value, backgroundColor = SurfaceDark, textColor = ElectricCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        NeoButton(
            text = "Open 50-Screen Inspector Catalog →",
            onClick = { onNavigate("catalog") },
            backgroundColor = SapphirePrimary,
            icon = "📱"
        )
        Spacer(modifier = Modifier.height(10.dp))
        NeoButton(
            text = "Sign Out Session",
            onClick = {
                DialogController.showToast("Session safely terminated.")
                onNavigate("splash")
            },
            backgroundColor = SurfaceDark,
            textColor = CrimsonRed
        )
    }
}

