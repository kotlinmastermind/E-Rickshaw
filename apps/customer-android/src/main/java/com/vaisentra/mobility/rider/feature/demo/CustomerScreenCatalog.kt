package com.vaisentra.mobility.rider.feature.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.core.designsystem.ElectricNeoTokens
import com.vaisentra.mobility.core.designsystem.NeoBatteryGauge
import com.vaisentra.mobility.core.designsystem.NeoCard
import com.vaisentra.mobility.core.designsystem.NeoGlowButton
import com.vaisentra.mobility.core.designsystem.NeoSpeedometerGauge
import com.vaisentra.mobility.core.designsystem.NeoStatPill
import com.vaisentra.mobility.core.designsystem.NeoTextField

data class CustomerScreenMeta(
    val id: Int,
    val title: String,
    val category: String,
    val summary: String,
    val icon: String
)

object CustomerScreenCatalog {
    val SCREENS = listOf(
        CustomerScreenMeta(1, "Brand Splash & Locale", "Onboarding", "Cyber Neo Splash with Hindi, English, Tamil, Bengali locale switch", "⚡"),
        CustomerScreenMeta(2, "Phone & Cyber Auth", "Onboarding", "Mobile number & encrypted password sign-in with JWT token", "🔐"),
        CustomerScreenMeta(3, "SMS OTP Quick Login", "Onboarding", "6-digit OTP auto-read with resend cooldown timer", "📲"),
        CustomerScreenMeta(4, "Password & Pin Reset", "Onboarding", "OTP verified biometric PIN & credential recovery", "🔑"),
        CustomerScreenMeta(5, "Customer Registration & KYC", "Onboarding", "Full profile setup with Aadhaar verification & avatar", "👤"),
        CustomerScreenMeta(6, "Passkey & Biometric Lock", "Onboarding", "Fingerprint / Face Unlock with 4-digit fallback PIN", "🛡️"),
        CustomerScreenMeta(7, "Map Home & Live Radar", "Radar & Booking", "Real-time GPS map with nearby e-rickshaws live radar", "🗺️"),
        CustomerScreenMeta(8, "Destination Search & Places", "Radar & Booking", "Autocomplete address search with frequent landmarks", "🔍"),
        CustomerScreenMeta(9, "Multi-Stop Route Planner", "Radar & Booking", "Add up to 3 intermediate stops with waypoint re-ordering", "📍"),
        CustomerScreenMeta(10, "Vehicle Tier & Fare Matrix", "Radar & Booking", "Compare Eco Share, Standard EV, Auto, and Cargo loaders", "🛺"),
        CustomerScreenMeta(11, "Ride Comfort & Cabin Preferences", "Preferences", "In-cabin AC temperature, quiet ride, music genre presets", "❄️"),
        CustomerScreenMeta(12, "Accessibility & EV-Only Filter", "Preferences", "Wheelchair access, assistance animal, 100% green filter", "♿"),
        CustomerScreenMeta(13, "Price Lock & Fare Breakdown", "Preferences", "Locked quote breakdown: base, distance, surge, and GST", "🏷️"),
        CustomerScreenMeta(14, "Promo Codes & Vouchers", "Preferences", "Apply promotional coupons with instant cashback calculation", "🎟️"),
        CustomerScreenMeta(15, "Payment Method & UPI Picker", "Wallet & Pay", "Pay via UPI (GPay/PhonePe), Credit Card, Cash, or Wallet", "💳"),
        CustomerScreenMeta(16, "Cashless Wallet Hub", "Wallet & Pay", "Check live wallet balance, add money, UPI QR scanner", "💰"),
        CustomerScreenMeta(17, "Wallet Auto-Topup Rules", "Wallet & Pay", "Set minimum threshold and automated reload amount", "🔄"),
        CustomerScreenMeta(18, "Schedule Later & Airport Sync", "Radar & Booking", "Book advance rides with automated flight delay tracking", "✈️"),
        CustomerScreenMeta(19, "Book for Family / Guest", "Radar & Booking", "Enter guest passenger name, phone & relation for trip sharing", "👨‍👩‍👧"),
        CustomerScreenMeta(20, "Corporate & Business Profile", "Preferences", "Cost center, project code & GST tax invoice claiming", "💼"),
        CustomerScreenMeta(21, "Eco-Pool Shared Seat Booking", "Carpool", "Reserve 1 or 2 seats in shared electric corridor", "👥"),
        CustomerScreenMeta(22, "Shared Pool Passenger Chat", "Carpool", "Group chat announcements for co-passengers on route", "💬"),
        CustomerScreenMeta(23, "Dispatch Radar & Pilot Matching", "In-Trip HUD", "Animated cascading dispatch radar scanning 1.5 km perimeter", "📡"),
        CustomerScreenMeta(24, "Pilot Assigned & ETA Tracker", "In-Trip HUD", "Pilot photo, vehicle plate, verified badge & live ETA", "👨‍✈️"),
        CustomerScreenMeta(25, "Driver En-Route & Vehicle RC", "In-Trip HUD", "Commercial permit, vehicle registration plate and PUC check", "📋"),
        CustomerScreenMeta(26, "Pilot Arrived & Meeting Point", "In-Trip HUD", "Arrival photo proof, gate landmark & buzzer notification", "🎯"),
        CustomerScreenMeta(27, "Boarding & 4-Digit Security OTP", "In-Trip HUD", "Encrypted start OTP display with auto-refresh mechanism", "🔢"),
        CustomerScreenMeta(28, "Live Telemetry & Speedometer HUD", "In-Trip HUD", "Real-time digital speedometer, heading & route corridor", "⚡"),
        CustomerScreenMeta(29, "Route Deviations & Unplanned Stops", "Safety & SOS", "Automated alert when vehicle departs mapped trajectory", "⚠️"),
        CustomerScreenMeta(30, "In-Cabin Audio Safety Recording", "Safety & SOS", "Encrypted emergency ambient audio recording for trip security", "🎙️"),
        CustomerScreenMeta(31, "Live Journey Link Sharing", "Safety & SOS", "Share live tracking link via WhatsApp/SMS to trusted contacts", "🔗"),
        CustomerScreenMeta(32, "Emergency SOS & Police 112", "Safety & SOS", "1-tap emergency panic dispatch notifying 112 & Security Center", "🚨"),
        CustomerScreenMeta(33, "Emergency Contacts Manager", "Safety & SOS", "Manage trusted family contacts for auto trip notifications", "📞"),
        CustomerScreenMeta(34, "In-Trip Pilot Chat & Translate", "In-Trip HUD", "Two-way instant messaging with Hindi-English translation", "🌐"),
        CustomerScreenMeta(35, "Split Fare with Co-Riders", "Wallet & Pay", "Split bill equally or by custom percentage with contacts", "➗"),
        CustomerScreenMeta(36, "Ride Completed & Receipt", "Post-Trip", "Final itemized digital receipt with distance & time breakdown", "🧾"),
        CustomerScreenMeta(37, "Driver Rating & Factor CSAT", "Post-Trip", "5-star rating with driving, cleanliness, and courtesy tags", "⭐"),
        CustomerScreenMeta(38, "Driver Tip & Compliments", "Post-Trip", "Tip pilot directly (₹10, ₹20, ₹50) with instant wallet credit", "💵"),
        CustomerScreenMeta(39, "Past Trips & Activity History", "Post-Trip", "Searchable history of completed and cancelled bookings", "📑"),
        CustomerScreenMeta(40, "GST Tax Invoice & PDF Download", "Post-Trip", "Official corporate invoice with company GSTIN and tax seal", "📄"),
        CustomerScreenMeta(41, "Fare Dispute & Refund Claim", "Support", "Dispute route extra charges with instant automatic arbitration", "⚖️"),
        CustomerScreenMeta(42, "Lost Item Concierge Desk", "Support", "Report lost phones, bags, or items left in the vehicle", "🎒"),
        CustomerScreenMeta(43, "Support Ticket Helpdesk", "Support", "Submit tickets for payment, safety, or vehicle complaints", "🎫"),
        CustomerScreenMeta(44, "Live Support Executive Chat", "Support", "Real-time chat with human operations concierge agent", "💬"),
        CustomerScreenMeta(45, "Safety Incident Formal Report", "Support", "Log formal incident report with photo evidence and severity", "🛡️"),
        CustomerScreenMeta(46, "Refer & Earn Electric Cash", "Loyalty", "Unique referral link with ₹100 wallet reward per friend", "🎁"),
        CustomerScreenMeta(47, "Green Club Loyalty & Streaks", "Loyalty", "Tier perks: Bronze, Silver, Gold, Platinum with ride discounts", "🏆"),
        CustomerScreenMeta(48, "Notification Inbox & Alerts", "Account", "System updates, weather warnings, and promotional offers", "🔔"),
        CustomerScreenMeta(49, "Saved Places & Favorite Pins", "Account", "Manage Home, Work, Gym, Metro shortcuts with 1-tap booking", "⭐"),
        CustomerScreenMeta(50, "Account Settings & Privacy Hub", "Account", "Data download, session management, biometric app lock, sign-out", "⚙️")
    )
}

@Composable
fun CustomerScreenRenderer(
    screenId: Int,
    modifier: Modifier = Modifier
) {
    val meta = CustomerScreenCatalog.SCREENS.find { it.id == screenId }
        ?: CustomerScreenCatalog.SCREENS[0]

    var sampleInput1 by remember { mutableStateOf("") }
    var sampleInput2 by remember { mutableStateOf("") }
    var sampleInput3 by remember { mutableStateOf("") }
    var toggleState by remember { mutableStateOf(true) }
    var toggleState2 by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(24f) }
    var selectedChip by remember { mutableStateOf("Standard") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ElectricNeoTokens.CyberVoid)
            .padding(ElectricNeoTokens.SpacingMedium),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Screen Header Badge
        item {
            NeoCard(borderColor = ElectricNeoTokens.ElectricCyanBorder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ElectricNeoTokens.ElectricCyanGlow)
                                .border(1.5.dp, ElectricNeoTokens.ElectricCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = meta.icon, fontSize = 20.sp)
                        }
                        Column {
                            Text(
                                text = "RIDER SCREEN #${meta.id}",
                                color = ElectricNeoTokens.ElectricCyan,
                                fontSize = 11.sp,
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
                    NeoStatPill(label = "CAT", value = meta.category)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = meta.summary,
                    color = ElectricNeoTokens.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        // Custom Specialized Interactive UI per Screen
        when (screenId) {
            1 -> item { // Splash & Locale
                NeoCard {
                    Text("Select Interface Language", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf("English", "हिंदी (Hindi)", "বাংলা (Bengali)", "தமிழ் (Tamil)", "मराठी (Marathi)").forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                .background(if (sampleInput1 == lang) ElectricNeoTokens.CyberSurfaceHighlight else ElectricNeoTokens.CyberSurface)
                                .clickable { sampleInput1 = lang }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(lang, color = ElectricNeoTokens.TextWhite, fontSize = 13.sp)
                            if (sampleInput1 == lang) {
                                Text("✓ SELECTED", color = ElectricNeoTokens.ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    NeoGlowButton(text = "Confirm & Proceed ➔", onClick = { statusMessage = "Locale saved: ${sampleInput1.ifBlank { "English" }}" })
                }
            }

            2, 3 -> item { // Auth & OTP
                NeoCard {
                    Text(if (screenId == 3) "Enter SMS OTP Verification" else "Mobile & JWT Authentication", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = if (screenId == 3) "Enter 6-Digit SMS Code" else "+91 Phone Number (e.g. 9876543210)"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (screenId == 2) {
                        NeoTextField(
                            value = sampleInput2,
                            onValueChange = { sampleInput2 = it },
                            placeholder = "Password / Passcode"
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    NeoGlowButton(
                        text = if (screenId == 3) "Verify OTP Code" else "Send Login OTP",
                        onClick = { statusMessage = if (screenId == 3) "OTP code verified. Auth token generated." else "SMS OTP sent to +91 ${sampleInput1.ifBlank { "9876543210" }}" }
                    )
                }
            }

            4, 5, 6 -> item { // Profile, Reset, Biometric
                NeoCard {
                    Text("Identity & Passkey Security", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = if (screenId == 5) "Full Legal Name (as on Aadhaar)" else "Registered Mobile or Email"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    NeoTextField(
                        value = sampleInput2,
                        onValueChange = { sampleInput2 = it },
                        placeholder = if (screenId == 5) "Aadhaar / Virtual ID (12-Digits)" else "4-Digit Master PIN"
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Biometric Fingerprint / FaceID", color = ElectricNeoTokens.TextWhite, fontSize = 12.sp)
                        Switch(
                            checked = toggleState,
                            onCheckedChange = { toggleState = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ElectricNeoTokens.ElectricCyan, checkedTrackColor = ElectricNeoTokens.CyberSurfaceHighlight)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoGlowButton(
                        text = if (screenId == 6) "Register Passkey" else "Save Identity KYC",
                        onClick = { statusMessage = "Biometric credentials synced with Android Keystore." }
                    )
                }
            }

            7, 8, 9, 10 -> item { // Map Radar, Autocomplete, Waypoints, Vehicle Tiers
                NeoCard {
                    Text("Radar Route Planner & Vehicle Fleet", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NeoStatPill(label = "Nearby EV", value = "9 Active", accentColor = ElectricNeoTokens.NeonEmerald)
                        NeoStatPill(label = "Pickup ETA", value = "2.8 MIN", accentColor = ElectricNeoTokens.ElectricCyan)
                        NeoStatPill(label = "Surge", value = "1.0x Flat", accentColor = ElectricNeoTokens.SolarAmber)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = "Pickup: Connaught Place Inner Circle Gate 3"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NeoTextField(
                        value = sampleInput2,
                        onValueChange = { sampleInput2 = it },
                        placeholder = "Drop: Cyber City DLF Phase 2"
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Select Fleet Tier:", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tiers = listOf("Eco Rickshaw (₹35)", "Auto Green (₹45)", "Cab EV (₹90)", "Cargo Loader (₹110)")
                        items(tiers) { tier ->
                            val isSel = selectedChip == tier
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                    .background(if (isSel) ElectricNeoTokens.ElectricCyan else ElectricNeoTokens.CyberSurface)
                                    .border(1.dp, if (isSel) ElectricNeoTokens.ElectricCyan else ElectricNeoTokens.ElectricCyanBorder, RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                    .clickable { selectedChip = tier }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(tier, color = if (isSel) Color.Black else ElectricNeoTokens.TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoGlowButton(
                        text = "Confirm Booking with $selectedChip",
                        onClick = { statusMessage = "Booking confirmed for $selectedChip! Searching nearby pilots." }
                    )
                }
            }

            11, 12, 13, 14 -> item { // Comfort, Filters, Price Lock, Promo
                NeoCard {
                    Text("Ride Comfort & Price Lock Breakdown", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Cabin Climate Control: ${sliderValue.toInt()}°C", color = ElectricNeoTokens.TextWhite, fontSize = 12.sp)
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 18f..28f,
                        colors = SliderDefaults.colors(thumbColor = ElectricNeoTokens.ElectricCyan, activeTrackColor = ElectricNeoTokens.ElectricCyan)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Quiet Ride (No conversation / low music)", color = ElectricNeoTokens.TextWhite, fontSize = 12.sp)
                        Switch(checked = toggleState, onCheckedChange = { toggleState = it })
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("100% Zero-Emission Electric Only", color = ElectricNeoTokens.NeonEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Switch(checked = toggleState2, onCheckedChange = { toggleState2 = it })
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = "Promo Code (Try: VAISENTRA50)"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoGlowButton(
                        text = "Apply Promo & Lock Fare (₹128)",
                        onClick = { statusMessage = "Fare locked at ₹128 with promo discount applied." }
                    )
                }
            }

            15, 16, 17, 35 -> item { // Wallet, UPI, Auto-Topup, Split
                NeoCard {
                    Text("Neo Wallet & UPI Instant Payment Hub", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Vaisentra Electric Balance", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                            Text("₹ 840.00", color = ElectricNeoTokens.NeonEmerald, fontSize = 28.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        }
                        NeoBatteryGauge(batteryPercent = 95, isCharging = true)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("+₹100", "+₹250", "+₹500", "+₹1000").forEach { chip ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                    .background(ElectricNeoTokens.CyberSurfaceHighlight)
                                    .border(1.dp, ElectricNeoTokens.ElectricCyanBorder, RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                    .clickable { sampleInput1 = chip }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(chip, color = ElectricNeoTokens.ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoTextField(
                        value = sampleInput2,
                        onValueChange = { sampleInput2 = it },
                        placeholder = "UPI VPA (e.g. user@okhdfcbank / PhonePe)"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoGlowButton(
                        text = "Top-Up Wallet via UPI",
                        onClick = { statusMessage = "₹${sampleInput1.ifBlank { "250" }} added to wallet balance via UPI." }
                    )
                }
            }

            18, 19, 20 -> item { // Airport, Family, Corporate
                NeoCard {
                    Text("Advanced Scheduling & Corporate Profiles", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = if (screenId == 18) "Flight Number (e.g. AI 804 / 6E 2132)" else "Passenger Full Name"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    NeoTextField(
                        value = sampleInput2,
                        onValueChange = { sampleInput2 = it },
                        placeholder = if (screenId == 20) "Corporate GSTIN / Cost Center Code" else "Passenger Mobile (+91)"
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoGlowButton(
                        text = if (screenId == 18) "Sync Flight & Reserve Pilot" else "Save Guest Booking",
                        onClick = { statusMessage = "Scheduled ride saved with flight delay watchdog enabled." }
                    )
                }
            }

            21, 22 -> item { // Carpool & Passenger Chat
                NeoCard {
                    Text("Electric Corridor Carpool & Co-Rider Chat", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NeoStatPill(label = "POOL ROUTE", value = "Metro Green Line")
                        NeoStatPill(label = "SEATS", value = "2 Available", accentColor = ElectricNeoTokens.NeonEmerald)
                        NeoStatPill(label = "FARE/SEAT", value = "₹25 Flat", accentColor = ElectricNeoTokens.SolarAmber)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = "Message co-passengers in pool..."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoGlowButton(
                        text = "Send Message to Shared Corridor",
                        onClick = { statusMessage = "Chat broadcasted to 2 active co-riders." }
                    )
                }
            }

            23, 24, 25, 26, 27, 28 -> item { // In-Trip Telemetry, ETA, Speedometer
                NeoCard {
                    Text("Active Pilot HUD & Live Telemetry Corridor", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeoSpeedometerGauge(speedKmh = sliderValue)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeoStatPill(label = "START OTP", value = "6289", accentColor = ElectricNeoTokens.NeonEmerald)
                            NeoStatPill(label = "VEHICLE", value = "DL 1ER 7741")
                            NeoStatPill(label = "PILOT ETA", value = "3.2 MIN", accentColor = ElectricNeoTokens.SolarAmber)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Simulate Vehicle Speed: ${sliderValue.toInt()} km/h", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..60f,
                        colors = SliderDefaults.colors(thumbColor = ElectricNeoTokens.ElectricCyan, activeTrackColor = ElectricNeoTokens.ElectricCyan)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                .background(ElectricNeoTokens.CyberSurfaceHighlight)
                                .clickable { statusMessage = "Driver buzzer sounded!" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔔 Sound Buzzer", color = ElectricNeoTokens.ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                .background(ElectricNeoTokens.ElectricCyan)
                                .clickable { statusMessage = "Voice call initiated with pilot." },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📞 Call Pilot", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            29, 30, 31, 32, 33 -> item { // Safety, SOS, Contacts, Audio Recording
                NeoCard(borderColor = ElectricNeoTokens.CyberCrimsonBorder) {
                    Text("Emergency SOS & Safety Operations Center", color = ElectricNeoTokens.CyberCrimson, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "1-Tap trigger dispatches real-time telemetry, vehicle plate number, and cabin audio to Delhi Police 112 and Vaisentra 24/7 Response Command.",
                        color = ElectricNeoTokens.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = "Emergency Contact Mobile (+91)"
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoGlowButton(
                        text = "🚨 TRIGGER 112 EMERGENCY SOS",
                        onClick = { statusMessage = "112 Emergency alert broadcasted. SOC Operator assigned." },
                        accentColor = ElectricNeoTokens.CyberCrimson,
                        textColor = Color.White
                    )
                }
            }

            34 -> item { // Pilot Chat & Translate
                NeoCard {
                    Text("In-Trip Pilot Chat & Auto-Translation", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val canned = listOf("I am at Gate 2", "Turn on AC", "Please wait 2 mins", "Follow GPS route")
                        items(canned) { phrase ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusPill))
                                    .background(ElectricNeoTokens.CyberSurfaceHighlight)
                                    .clickable { sampleInput1 = phrase }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(phrase, color = ElectricNeoTokens.TextWhite, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = "Type message (translates automatically to Hindi)..."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoGlowButton(
                        text = "Send Message to Pilot",
                        onClick = { statusMessage = "Message delivered with Hindi audio playback." }
                    )
                }
            }

            36, 37, 38 -> item { // Receipt, Rating, Tips
                NeoCard {
                    Text("Ride Completed • Receipt & Pilot Rating", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Trip Fare", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                            Text("₹ 142.50", color = ElectricNeoTokens.NeonEmerald, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        }
                        NeoStatPill(label = "PAID VIA", value = "Neo Wallet", accentColor = ElectricNeoTokens.ElectricCyan)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Rate Your Electric Pilot Experience:", color = ElectricNeoTokens.TextWhite, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1★", "2★", "3★", "4★", "5★").forEach { star ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                    .background(if (selectedChip == star) ElectricNeoTokens.SolarAmber else ElectricNeoTokens.CyberSurfaceHighlight)
                                    .clickable { selectedChip = star }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(star, color = if (selectedChip == star) Color.Black else ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoGlowButton(
                        text = "Submit Rating & Download Receipt",
                        onClick = { statusMessage = "Rating $selectedChip submitted! Digital receipt PDF generated." }
                    )
                }
            }

            39, 40 -> item { // Past Trips & GST Invoices
                NeoCard {
                    Text("Trip Activity Ledger & Official GST Invoices", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(
                        Triple("Connaught Place ➔ Noida Sec 62", "₹185.00 • Standard EV", "11 Nov, 10:15 AM"),
                        Triple("Saket Metro ➔ Cyber Hub DLF", "₹142.50 • Eco Rickshaw", "10 Nov, 06:40 PM"),
                        Triple("Delhi Airport T3 ➔ Indirapuram", "₹420.00 • Cab EV", "08 Nov, 09:30 PM")
                    ).forEach { trip ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                .background(ElectricNeoTokens.CyberSurface)
                                .clickable { sampleInput1 = trip.first }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(trip.first, color = ElectricNeoTokens.TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(trip.third, color = ElectricNeoTokens.TextSecondary, fontSize = 10.sp)
                            }
                            Text(trip.second, color = ElectricNeoTokens.ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    NeoGlowButton(
                        text = "Download Consolidated GST Statement",
                        onClick = { statusMessage = "Consolidated GST invoice sent to registered email." }
                    )
                }
            }

            41, 42, 43, 44, 45 -> item { // Dispute, Lost Item, Helpdesk, Incident
                NeoCard {
                    Text("Customer Concierge & Issue Resolution", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = if (screenId == 42) "Lost item description (e.g. Black iPhone 15 Pro)" else "Issue Category (Fare, Driver Behaviour, Route)"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    NeoTextField(
                        value = sampleInput2,
                        onValueChange = { sampleInput2 = it },
                        placeholder = "Detailed description / Trip ID..."
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoGlowButton(
                        text = if (screenId == 44) "Connect to Live Operations Specialist" else "Submit Formal Ticket",
                        onClick = { statusMessage = "Support ticket #VAI-8832 registered. SLA response: < 15 mins." }
                    )
                }
            }

            46, 47 -> item { // Referral & Green Club Loyalty
                NeoCard {
                    Text("Green Club Loyalty & Referral Rewards", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NeoStatPill(label = "TIER", value = "Gold Green", accentColor = ElectricNeoTokens.SolarAmber)
                        NeoStatPill(label = "CO2 SAVED", value = "42.8 KG", accentColor = ElectricNeoTokens.NeonEmerald)
                        NeoStatPill(label = "REWARDS", value = "₹350 Free", accentColor = ElectricNeoTokens.ElectricCyan)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoTextField(
                        value = "VAISENTRA-GREEN-772",
                        onValueChange = {},
                        placeholder = "Referral Code"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoGlowButton(
                        text = "Share Referral Link (Earn ₹100 per friend)",
                        onClick = { statusMessage = "Referral invite link copied to clipboard!" }
                    )
                }
            }

            48, 49, 50 -> item { // Notifications, Saved Places, Settings
                NeoCard {
                    Text("Account Preferences & Saved Locations Hub", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(
                        "🏠 Home: Tower 4, Cyber City Gurgaon",
                        "💼 Work: DLF Cyber Park Phase 3",
                        "🏋️ Gym: Gold's Fitness Sector 29",
                        "🚇 Metro: Sikanderpur Interchange Station"
                    ).forEach { place ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                .background(ElectricNeoTokens.CyberSurface)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(place, color = ElectricNeoTokens.TextWhite, fontSize = 11.sp)
                            Text("1-Tap Book ➔", color = ElectricNeoTokens.ElectricCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("App Lock via Biometrics", color = ElectricNeoTokens.TextWhite, fontSize = 12.sp)
                        Switch(checked = toggleState, onCheckedChange = { toggleState = it })
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoGlowButton(
                        text = "Update Account Preferences",
                        onClick = { statusMessage = "Account preferences synchronized." }
                    )
                }
            }

            else -> item {
                NeoCard {
                    Text("Screen #${meta.id} Dynamic Workspace", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    NeoTextField(value = sampleInput1, onValueChange = { sampleInput1 = it }, placeholder = "Input parameter...")
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoGlowButton(text = "Execute Action", onClick = { statusMessage = "Action executed for ${meta.title}" })
                }
            }
        }

        // Live Status Feedback Toast
        if (statusMessage != null) {
            item {
                NeoCard(
                    borderColor = ElectricNeoTokens.NeonEmeraldBorder,
                    backgroundColor = ElectricNeoTokens.NeonEmeraldGlow
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "✓ ${statusMessage}", color = ElectricNeoTokens.NeonEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "DISMISS",
                            color = ElectricNeoTokens.TextWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.clickable { statusMessage = null }
                        )
                    }
                }
            }
        }
    }
}
