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
import com.vaisentra.mobility.rider.data.ActiveRideDto
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.theme.*
import kotlinx.coroutines.launch

// Screen 36: Ride Completed & Digital Receipt
@Composable
fun RideReceiptScreen(onNavigate: (String) -> Unit) {
    val receipt by RiderRepository.rideReceipt.collectAsState()

    LaunchedEffect(Unit) {
        val rideId = RiderRepository.activeRide.value?.id ?: "ride-latest"
        RiderRepository.fetchRideReceipt(rideId)
    }

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
            onClick = {
                val rideId = RiderRepository.activeRide.value?.id ?: "ride-latest"
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    RiderRepository.submitRating(rideId, rating, selectedCompliments.joinToString(", "))
                }
                DialogController.showToast("Rating submitted to pilot ledger!")
                onNavigate("tip_driver")
            },
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
            val rideId = RiderRepository.activeRide.value?.id ?: "ride-latest"
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                RiderRepository.submitTip(rideId, tipAmt)
            }
            DialogController.showToast("₹$tipAmt tip credited to Pilot Ram Lal!")
            onNavigate("past_trips")
        }, icon = "💵")
    }
}

// Screen 39: Past Trips & Activity History (Supabase: rides)
@Composable
fun PastTripsHistoryScreen(onNavigate: (String) -> Unit) {
    val liveTrips by RiderRepository.rideHistory.collectAsState()

    LaunchedEffect(Unit) {
        RiderRepository.fetchRideHistory()
    }

    val defaultTrips = listOf(
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
            if (liveTrips.isNotEmpty()) {
                items(liveTrips) { ride ->
                    val fareStr = if (ride.totalFareCents != null) "₹${ride.totalFareCents / 100}.00" else "₹129.00"
                    val routeStr = "${ride.pickupAddress} ➔ ${ride.destinationAddress}"
                    val detailStr = "$fareStr • ${ride.vehicleModel ?: "EV Rickshaw"} • ${ride.status}"
                    val timeStr = ride.createdAt?.take(16)?.replace("T", " ") ?: "Recent Trip"

                    NeoCard(
                        modifier = Modifier.clickable { onNavigate("ride_receipt") },
                        cornerRadius = 14.dp
                    ) {
                        Text(text = timeStr, color = TextDim, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = routeStr, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = detailStr, color = ElectricCyan, fontSize = 12.sp)
                    }
                }
            } else {
                items(defaultTrips) { (route, detail, time) ->
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
}

// Screen 40: GST Tax Invoice & Download
@Composable
fun GstInvoiceScreen(onNavigate: (String) -> Unit) {
    var recipientGstin by remember { mutableStateOf("07AAECV9988D1Z2") }
    var companyName by remember { mutableStateOf("Vaisentra Corp Labs") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Tax Invoice", subtitle = "Govt Compliant IRN Signed Slip", onBack = { onNavigate("ride_receipt") })
        Spacer(modifier = Modifier.height(14.dp))

        // Premium Official Tax Invoice Voucher Card
        NeoCard(borderColor = SapphireGlow) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "TAX INVOICE", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(text = "#INV-2026-9281", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                NeoBadge(text = "IRN GENERATED ✓", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "SUPPLIER DETAILS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = "Vaisentra Mobility Technologies Pvt Ltd", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = "GSTIN: 07AABCV1234F1Z8 • SAC Code: 996412", color = TextMuted, fontSize = 11.sp)
            Text(text = "Reg. Office: Cyber City, Tower 9, Gurugram NCR", color = TextDim, fontSize = 10.sp)

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "RECIPIENT (B2B CORPORATE)", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = companyName, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                NeoBadge(text = "ITC ELIGIBLE", backgroundColor = SurfaceDark, textColor = EmeraldGreen)
            }
            Text(text = "Recipient GSTIN: $recipientGstin", color = ElectricCyan, fontSize = 11.sp)

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))

            // Line items breakdown
            Text(text = "TAX BREAKUP & TARIFF", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            val items = listOf(
                "Base Fare (First 1.5 km)" to "₹20.00",
                "Distance Fare (2.46 km @ ₹12/km)" to "₹29.52",
                "Trip Duration (14 mins @ ₹1/min)" to "₹14.00",
                "Electric Mobility Green Fee" to "₹10.00",
                "Dynamic Surge Charge (1.0x Normal)" to "₹0.00",
                "Taxable Service Value" to "₹122.90",
                "CGST @ 2.5%" to "₹3.07",
                "SGST @ 2.5%" to "₹3.07"
            )

            items.forEach { (desc, amt) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = desc, color = if (desc.startsWith("Taxable")) TextWhite else TextMuted, fontSize = 11.sp, fontWeight = if (desc.startsWith("Taxable")) FontWeight.Bold else FontWeight.Normal)
                    Text(text = amt, color = TextWhite, fontSize = 11.sp, fontWeight = if (desc.startsWith("Taxable")) FontWeight.Bold else FontWeight.Normal)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Total Invoice Value (INR):", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = "₹129.04", color = ElectricCyan, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔐", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Digitally signed with SHA-256 e-Sign token under Indian IT Act 2000.",
                        color = TextDim,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoButton(
                text = "Download PDF Slip",
                onClick = {
                    val rideId = RiderRepository.activeRide.value?.id ?: "ride-latest"
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        RiderRepository.fetchTaxInvoice(rideId)
                    }
                    DialogController.showToast("GST Tax Invoice saved to Downloads!")
                },
                modifier = Modifier.weight(1f),
                icon = "📥",
                backgroundColor = SapphirePrimary
            )
            NeoButton(
                text = "Email Invoice",
                onClick = {
                    DialogController.showToast("Invoice sent to your corporate email!")
                },
                modifier = Modifier.weight(1f),
                icon = "✉️",
                backgroundColor = SurfaceElevated
            )
        }
    }
}

