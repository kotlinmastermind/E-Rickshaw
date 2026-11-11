package com.vaisentra.mobility.rider.feature.posttrip

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

// Screen 36: Ride Completed & Digital Receipt
@Composable
fun RideReceiptScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Trip Receipt", subtitle = "Itemized Settlement Slip", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard(borderColor = EmeraldGreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Amount Paid via UPI", color = TextMuted, fontSize = 12.sp)
                NeoBadge(text = "PAID IN FULL ✓", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "₹129.04", color = ElectricCyan, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))
            val items = listOf(
                "Base Fare" to "₹20.00",
                "Distance (3.96 km @ ₹10/km)" to "₹39.60",
                "Time (13.3 min @ ₹1/min)" to "₹13.30",
                "Fleet Cleanliness Fee" to "₹50.00",
                "GST (5%)" to "₹6.14",
                "Discounts & Promos" to "-₹0.00"
            )
            items.forEach { (k, v) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = k, color = TextMuted, fontSize = 12.sp)
                    Text(text = v, color = TextWhite, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoButton(
                text = "Rate Chauffeur",
                onClick = { onNavigate("rate_driver") },
                modifier = Modifier.weight(1f),
                backgroundColor = SapphirePrimary,
                icon = "⭐"
            )
            NeoButton(
                text = "GST Invoice",
                onClick = { onNavigate("gst_invoice") },
                modifier = Modifier.weight(1f),
                backgroundColor = SurfaceElevated,
                icon = "📄"
            )
        }
    }
}

// Screen 37: Driver Rating & Factor CSAT
@Composable
fun DriverRatingScreen(onNavigate: (String) -> Unit) {
    var rating by remember { mutableStateOf(5) }
    val compliments = listOf("⚡ Smooth Driving", "🧼 Spotless EV", "🧭 Expert Route", "🙏 Polite Chauffeur")
    val selectedCompliments = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Rate Pilot Experience", subtitle = "Quality Assurance & Compliments", onBack = { onNavigate("ride_receipt") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Text(text = "How was your journey with Ram Lal?", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (1..5).forEach { star ->
                    Text(
                        text = if (star <= rating) "★" else "☆",
                        color = AmberGold,
                        fontSize = 38.sp,
                        modifier = Modifier.clickable { rating = star }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Add Compliments:", color = TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            compliments.forEach { comp ->
                val isSelected = selectedCompliments.contains(comp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) SapphirePrimary.copy(alpha = 0.3f) else SurfaceCard)
                        .border(1.dp, if (isSelected) BorderFocus else BorderSubtle, RoundedCornerShape(10.dp))
                        .clickable {
                            if (isSelected) selectedCompliments.remove(comp) else selectedCompliments.add(comp)
                        }
                        .padding(10.dp)
                ) {
                    Text(text = comp, color = TextWhite, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(
            text = "Submit Rating & Tip Driver →",
            onClick = { onNavigate("tip_driver") },
            backgroundColor = SapphirePrimary
        )
    }
}

// Screen 38: Driver Tip & Gratuity
@Composable
fun DriverTipScreen(onNavigate: (String) -> Unit) {
    var tipAmt by remember { mutableStateOf(20) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Chauffeur Gratuity", subtitle = "100% credited to pilot wallet", onBack = { onNavigate("rate_driver") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Text(text = "Select Tip Amount", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(10, 20, 50, 100).forEach { t ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (tipAmt == t) SapphirePrimary else SurfaceCard)
                            .clickable { tipAmt = t }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "₹$t", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        NeoButton(text = "Pay ₹$tipAmt Tip via UPI", onClick = {
            DialogController.showToast("₹$tipAmt tip credited to Pilot Ram Lal!")
            onNavigate("past_trips")
        }, icon = "💵")
    }
}

// Screen 39: Past Trips & Activity History (Supabase: rides)
@Composable
fun PastTripsHistoryScreen(onNavigate: (String) -> Unit) {
    val trips = listOf(
        Triple("Sector 62, Noida ➔ Connaught Place", "₹129.04 • 14 Mins • Completed", "Today, 08:30 AM"),
        Triple("Anand Vihar Hub ➔ Rajiv Chowk Metro", "₹95.00 • Eco-Pool • Completed", "Yesterday, 06:15 PM"),
        Triple("Indira Gandhi Airport T3 ➔ Cyber Hub", "₹450.00 • E-Auto • Completed", "08-Nov, 10:20 PM")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Ride Activity Ledger", subtitle = "Completed trips synced from Supabase", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(trips) { (route, detail, time) ->
                NeoCard(
                    modifier = Modifier.clickable { onNavigate("ride_receipt") },
                    cornerRadius = 14.dp
                ) {
                    Text(text = time, color = TextDim, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = route, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = detail, color = ElectricCyan, fontSize = 12.sp)
                }
            }
        }
    }
}

// Screen 40: GST Tax Invoice & Download
@Composable
fun GstInvoiceScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Tax Invoice", subtitle = "Govt Compliant IRN Signed Slip", onBack = { onNavigate("ride_receipt") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "INVOICE #INV-2026-9281", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                NeoBadge(text = "IRN GENERATED", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "Supplier: Vaisentra Mobility Technologies Pvt Ltd", color = TextMuted, fontSize = 11.sp)
            Text(text = "GSTIN: 07AABCV1234F1Z8", color = TextMuted, fontSize = 11.sp)
            Text(text = "Tax Category: Transport of Passengers via EV (SAC 996412)", color = TextMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Taxable Value:", color = TextMuted, fontSize = 12.sp)
                Text(text = "₹122.90", color = TextWhite, fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "CGST (2.5%) + SGST (2.5%):", color = TextMuted, fontSize = 12.sp)
                Text(text = "₹6.14", color = TextWhite, fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Total Invoice Value:", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = "₹129.04", color = ElectricCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Download PDF Tax Invoice", onClick = {
            DialogController.showToast("Tax Invoice downloaded to device downloads folder!")
        }, icon = "📥")
    }
}

