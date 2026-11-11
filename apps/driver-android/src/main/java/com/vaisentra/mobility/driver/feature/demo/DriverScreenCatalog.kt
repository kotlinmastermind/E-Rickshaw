package com.vaisentra.mobility.driver.feature.demo

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

data class DriverScreenMeta(
    val id: Int,
    val title: String,
    val category: String,
    val summary: String,
    val icon: String
)

object DriverScreenCatalog {
    val SCREENS = listOf(
        DriverScreenMeta(1, "Pilot Cockpit Login", "Auth & Onboarding", "Driver credentials sign-in with phone + encrypted password", "⚡"),
        DriverScreenMeta(2, "Pilot SMS OTP Sign-In", "Auth & Onboarding", "Quick authentication for pilots on the road via SMS", "📲"),
        DriverScreenMeta(3, "Pilot Password Recovery", "Auth & Onboarding", "SMS-verified pilot credential reset procedure", "🔑"),
        DriverScreenMeta(4, "Pilot Profile & Academy KYC", "Auth & Onboarding", "License number, badge ID, academy certifications", "📋"),
        DriverScreenMeta(5, "Document Vault & Verification", "Auth & Onboarding", "Aadhaar, Driving License, Police Verification clearance", "🗂️"),
        DriverScreenMeta(6, "Document Expiry Watchdog", "Auth & Onboarding", "Upcoming document expiry warnings and resubmissions", "⏳"),
        DriverScreenMeta(7, "Assigned Vehicle & Inspection", "Vehicle & Duty", "E-Rickshaw model, year, registration plate, battery status", "🛺"),
        DriverScreenMeta(8, "Vehicle RC, Fitness & PUC", "Vehicle & Duty", "Permit number, PUC emission validity, insurance policy", "📑"),
        DriverScreenMeta(9, "Shift Start & Odometer Log", "Vehicle & Duty", "Enter initial km reading and battery percentage to start shift", "🛣️"),
        DriverScreenMeta(10, "Duty Switch & Auto-Accept Radar", "Vehicle & Duty", "Go Online / Offline toggle with auto-dispatch radius", "⚡"),
        DriverScreenMeta(11, "High-Frequency GPS Ping", "Telemetry & Heatmap", "Real-time location, heading, speed & anti-spoof check", "📡"),
        DriverScreenMeta(12, "Driver Fatigue & Rest Check", "Vehicle & Duty", "Mandatory rest prompts after continuous driving limit", "☕"),
        DriverScreenMeta(13, "Heatmap & Surge Zones", "Telemetry & Heatmap", "Live city demand map showing surge multipliers", "🌐"),
        DriverScreenMeta(14, "Road Restrictions & Hazards", "Telemetry & Heatmap", "Live municipal road blocks, waterlogging, detours", "🚫"),
        DriverScreenMeta(15, "Incoming Ride Offer Card", "Turn Navigation", "Accept/Decline offer card with pickup dist, fare, time", "🚖"),
        DriverScreenMeta(16, "Auto-Dispatch Offer Queue", "Turn Navigation", "Continuous trip matching and queued requests", "📥"),
        DriverScreenMeta(17, "Navigation to Pickup Spot", "Turn Navigation", "Turn-by-turn routing to commuter boarding point", "📍"),
        DriverScreenMeta(18, "Arrived at Pickup Point", "Turn Navigation", "Notify customer with photo proof and entrance guidance", "🎯"),
        DriverScreenMeta(19, "Waiting Timer & Billable Meter", "Turn Navigation", "Grace waiting period tracker and per-minute wait fee", "⏱️"),
        DriverScreenMeta(20, "Boarding & Commuter OTP Verify", "Turn Navigation", "Enter customer's 4-digit code to unlock the trip", "🔢"),
        DriverScreenMeta(21, "In-Trip Turn Navigation", "Turn Navigation", "Live route guide with ETA, distance left, speed limit", "🧭"),
        DriverScreenMeta(22, "Multi-Stop Waypoints", "Turn Navigation", "Sequential stop navigation with passenger instruction notes", "🗺️"),
        DriverScreenMeta(23, "Route Deviation & Anomaly Alarm", "Safety & Anomaly", "Detects abnormal route detours and stationary delays", "⚠️"),
        DriverScreenMeta(24, "In-Cabin Sound & Safety Status", "Safety & Anomaly", "Cabin ambient sound level check and safety check-in", "🎙️"),
        DriverScreenMeta(25, "Driver Emergency SOS Panic", "Safety & Anomaly", "Instant assistance trigger with SOC agent connect", "🚨"),
        DriverScreenMeta(26, "Passenger In-Trip Messaging", "Turn Navigation", "Quick-canned phrases and live chat with passenger", "💬"),
        DriverScreenMeta(27, "Shared Pool Trip Manifest", "Carpool", "View co-passengers, seats occupied and total pool payout", "👥"),
        DriverScreenMeta(28, "Pooled Stops Queue", "Carpool", "Optimized pickup and drop-off sequence for shared rides", "📋"),
        DriverScreenMeta(29, "Trip Finished & Fare Meter", "Earnings & Wallet", "Actual distance, actual minutes, final earnings breakdown", "🧾"),
        DriverScreenMeta(30, "Cash Collection Confirmer", "Earnings & Wallet", "Collect exact cash, verify bill, mark cash collected", "💵"),
        DriverScreenMeta(31, "Dynamic Driver UPI QR Code", "Earnings & Wallet", "Display UPI code for instant passenger scan", "📱"),
        DriverScreenMeta(32, "Passenger Rating & Reviews", "Profile & Goals", "Driver rating breakdown and badge accolades", "⭐"),
        DriverScreenMeta(33, "Tips & Extra Earnings Ledger", "Earnings & Wallet", "View customer tips credited 100% to pilot balance", "🎁"),
        DriverScreenMeta(34, "Shift Summary & Metrics", "Profile & Goals", "Gross fares, platform cut, net earnings, trip count", "📊"),
        DriverScreenMeta(35, "Shift End & Closing Odometer", "Vehicle & Duty", "Enter ending odometer, vehicle handover check", "🏁"),
        DriverScreenMeta(36, "Daily Targets & Incentive Bonus", "Profile & Goals", "Target ride completions and bonus progress tracker", "🎯"),
        DriverScreenMeta(37, "Weekly Goals & Progress", "Profile & Goals", "Track weekly ride goals and milestone bonuses", "📈"),
        DriverScreenMeta(38, "Streak Missions & Badges", "Profile & Goals", "Consecutive peak-hour completions for tiered multipliers", "🔥"),
        DriverScreenMeta(39, "Pilot Earnings & Balance", "Earnings & Wallet", "Total wallet balance, today's pay, pending settlements", "💰"),
        DriverScreenMeta(40, "Payout Bank & UPI Accounts", "Earnings & Wallet", "Manage bank account, IFSC code, and primary UPI VPA", "🏦"),
        DriverScreenMeta(41, "Instant IMPS/UPI Payout", "Earnings & Wallet", "Withdraw earnings to bank account instantly", "⚡"),
        DriverScreenMeta(42, "Driver Account Ledger", "Earnings & Wallet", "Itemized statement of all ride credits and deductions", "📑"),
        DriverScreenMeta(43, "Performance & Safety Scorecard", "Profile & Goals", "Lifetime rating, cancellation rate, on-time percentage", "🏆"),
        DriverScreenMeta(44, "Electric Academy Training", "Support & Info", "E-rickshaw maintenance, road safety & customer etiquette", "🎓"),
        DriverScreenMeta(45, "Driver Incident Reporting", "Safety & Anomaly", "File road crash, passenger dispute or route incident", "🛡️"),
        DriverScreenMeta(46, "Found Lost Item in Cab", "Support & Info", "Report customer items found in the e-rickshaw rear seat", "🎒"),
        DriverScreenMeta(47, "Driver Helpdesk Tickets", "Support & Info", "Submit queries regarding vehicle maintenance, pay, KYC", "🎫"),
        DriverScreenMeta(48, "Driver Emergency Contacts", "Safety & Anomaly", "Set family contact for safety alerts and SOS escalation", "📞"),
        DriverScreenMeta(49, "Push Notifications Center", "Support & Info", "Platform alerts, weather warnings, surge announcements", "🔔"),
        DriverScreenMeta(50, "Driver Preferences & Zones", "Vehicle & Duty", "Operating zone filter, vehicle class preference, language", "⚙️")
    )
}

@Composable
fun DriverScreenRenderer(
    screenId: Int,
    modifier: Modifier = Modifier
) {
    val meta = DriverScreenCatalog.SCREENS.find { it.id == screenId }
        ?: DriverScreenCatalog.SCREENS[0]

    var sampleInput1 by remember { mutableStateOf("") }
    var sampleInput2 by remember { mutableStateOf("") }
    var sliderSpeed by remember { mutableFloatStateOf(24f) }
    var isDutyOnline by remember { mutableStateOf(true) }
    var autoAcceptOn by remember { mutableStateOf(true) }
    var selectedTier by remember { mutableStateOf("All Corridors") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ElectricNeoTokens.CyberVoid)
            .padding(ElectricNeoTokens.SpacingMedium),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            NeoCard(borderColor = ElectricNeoTokens.NeonEmeraldBorder) {
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
                                .background(ElectricNeoTokens.NeonEmeraldGlow)
                                .border(1.5.dp, ElectricNeoTokens.NeonEmerald, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = meta.icon, fontSize = 20.sp)
                        }
                        Column {
                            Text(
                                text = "PILOT SCREEN #${meta.id}",
                                color = ElectricNeoTokens.NeonEmerald,
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
                    NeoStatPill(label = "CLUSTER", value = meta.category, accentColor = ElectricNeoTokens.NeonEmerald)
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

        // Custom Specialized Interactive UI per Driver Screen ID
        when (screenId) {
            1, 2, 3 -> item { // Cockpit Login & Auth
                NeoCard {
                    Text(if (screenId == 2) "SMS Quick Driver Sign-In" else "Pilot Cockpit Authentication", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = if (screenId == 2) "Enter 6-Digit SMS Code" else "Registered Pilot Mobile (+91)"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (screenId != 2) {
                        NeoTextField(
                            value = sampleInput2,
                            onValueChange = { sampleInput2 = it },
                            placeholder = "Cockpit Security PIN"
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    NeoGlowButton(
                        text = if (screenId == 2) "Verify OTP & Log In" else "Sign In to Cockpit",
                        onClick = { statusMessage = "Pilot authenticated. Active session token stored in Keystore." },
                        accentColor = ElectricNeoTokens.NeonEmerald,
                        textColor = Color.Black
                    )
                }
            }

            4, 5, 6 -> item { // KYC & Documents
                NeoCard {
                    Text("Transport Documents & Badge Vault", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(
                        Triple("Commercial Driving License (DL)", "DL-0420180092144", "Valid till Dec 2028"),
                        Triple("Electric Rickshaw Transport Badge", "VAI-PILOT-8821", "Active Verified"),
                        Triple("Delhi Police Character Verification", "POL-DEL-77412", "Clearance Approved")
                    ).forEach { doc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                .background(ElectricNeoTokens.CyberSurface)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(doc.first, color = ElectricNeoTokens.TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(doc.second, color = ElectricNeoTokens.NeonEmerald, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            Text(doc.third, color = ElectricNeoTokens.SolarAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    NeoGlowButton(
                        text = "Upload Renewed Document Certificate",
                        onClick = { statusMessage = "Camera scanner opened. Upload document image." },
                        accentColor = ElectricNeoTokens.NeonEmerald,
                        textColor = Color.Black
                    )
                }
            }

            7, 8, 9 -> item { // Vehicle, Inspection, Odometer
                NeoCard {
                    Text("Assigned Vehicle & Pre-Shift Odometer", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Vehicle Plate", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                            Text("DL 1ER 9241", color = ElectricNeoTokens.NeonEmerald, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                        NeoBatteryGauge(batteryPercent = 92, isCharging = false)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = "Opening Odometer KM (e.g. 14280)"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoGlowButton(
                        text = "Complete Inspection & Unlock Shift",
                        onClick = { statusMessage = "Shift opened with odometer ${sampleInput1.ifBlank { "14280" }} km." },
                        accentColor = ElectricNeoTokens.NeonEmerald,
                        textColor = Color.Black
                    )
                }
            }

            10, 11, 12 -> item { // Duty Switch & Fatigue
                NeoCard {
                    Text("Duty Cockpit & Dispatch Radar State", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Duty Status", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                            Text(
                                text = if (isDutyOnline) "ONLINE (ACCEPTING)" else "OFFLINE",
                                color = if (isDutyOnline) ElectricNeoTokens.NeonEmerald else ElectricNeoTokens.TextMuted,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        NeoStatPill(label = "PING RATE", value = "5 sec GPS", accentColor = ElectricNeoTokens.ElectricCyan)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-Accept Dispatch Offers", color = ElectricNeoTokens.TextWhite, fontSize = 12.sp)
                        Switch(
                            checked = autoAcceptOn,
                            onCheckedChange = { autoAcceptOn = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ElectricNeoTokens.NeonEmerald)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoGlowButton(
                        text = if (isDutyOnline) "⚡ GO OFFLINE" else "⚡ GO ONLINE (ACCEPT RIDES)",
                        onClick = {
                            isDutyOnline = !isDutyOnline
                            statusMessage = if (isDutyOnline) "Pilot is now ONLINE in Redis dispatch queue" else "Duty set OFFLINE"
                        },
                        accentColor = if (isDutyOnline) ElectricNeoTokens.SolarAmber else ElectricNeoTokens.NeonEmerald,
                        textColor = Color.Black
                    )
                }
            }

            13, 14 -> item { // Heatmap & Hazards
                NeoCard {
                    Text("Surge Heatmap & Traffic Advisory", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(
                        Triple("Connaught Place Outer", "1.6x Surge 🔥", "High commuter demand"),
                        Triple("Delhi Airport T3", "1.4x Surge 🔥", "Terminal arrivals buffer"),
                        Triple("Barakhamba Road", "Road Works ⚠️", "Single lane diversion")
                    ).forEach { zone ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                .background(ElectricNeoTokens.CyberSurface)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(zone.first, color = ElectricNeoTokens.TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(zone.third, color = ElectricNeoTokens.TextSecondary, fontSize = 10.sp)
                            }
                            Text(zone.second, color = ElectricNeoTokens.SolarAmber, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    NeoGlowButton(
                        text = "Navigate to 1.6x High Surge Zone ➔",
                        onClick = { statusMessage = "Routing set toward Connaught Place Outer." },
                        accentColor = ElectricNeoTokens.SolarAmber,
                        textColor = Color.Black
                    )
                }
            }

            15, 16, 17, 18, 19, 20 -> item { // Offer, Queue, Turn Nav, OTP Verify
                NeoCard(borderColor = ElectricNeoTokens.ElectricCyanBorder) {
                    Text("Active Ride Offer & Commuter Verification", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NeoStatPill(label = "EST FARE", value = "₹155", accentColor = ElectricNeoTokens.NeonEmerald)
                        NeoStatPill(label = "PICKUP", value = "380 M", accentColor = ElectricNeoTokens.ElectricCyan)
                        NeoStatPill(label = "DURATION", value = "14 MIN", accentColor = ElectricNeoTokens.SolarAmber)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = "Enter 4-Digit Commuter OTP (e.g. 7421)"
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                .background(ElectricNeoTokens.CyberCrimsonGlow)
                                .border(1.dp, ElectricNeoTokens.CyberCrimsonBorder, RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                                .clickable { statusMessage = "Ride declined. Returning to queue." },
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
                                .clickable { statusMessage = "OTP Verified! Starting meter & turn navigation." },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓ Accept & Start Ride", color = Color.Black, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            21, 22, 28 -> item { // In-Trip Navigation & Speedometer
                NeoCard {
                    Text("Digital Chauffeur Telemetry & Turn Guide", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeoSpeedometerGauge(speedKmh = sliderSpeed)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeoStatPill(label = "SPEED LIMIT", value = "35 KM/H")
                            NeoStatPill(label = "NEXT TURN", value = "RIGHT 180M", accentColor = ElectricNeoTokens.ElectricCyan)
                            NeoStatPill(label = "DESTINATION", value = "2.4 KM", accentColor = ElectricNeoTokens.NeonEmerald)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Adjust Vehicle Speed Simulator: ${sliderSpeed.toInt()} km/h", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                    Slider(
                        value = sliderSpeed,
                        onValueChange = { sliderSpeed = it },
                        valueRange = 0f..55f,
                        colors = SliderDefaults.colors(thumbColor = ElectricNeoTokens.NeonEmerald, activeTrackColor = ElectricNeoTokens.NeonEmerald)
                    )
                }
            }

            23, 24, 25, 45, 48 -> item { // Safety & SOS
                NeoCard(borderColor = ElectricNeoTokens.CyberCrimsonBorder) {
                    Text("Pilot Safety Command & Emergency Panic", color = ElectricNeoTokens.CyberCrimson, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Immediately transmits pilot telemetry and cabin ambient audio to Delhi Police 112 and the Vaisentra 24/7 Response Desk.",
                        color = ElectricNeoTokens.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoGlowButton(
                        text = "🚨 TRIGGER PILOT PANIC SOS",
                        onClick = { statusMessage = "Pilot SOS transmitted to SOC control desk." },
                        accentColor = ElectricNeoTokens.CyberCrimson,
                        textColor = Color.White
                    )
                }
            }

            26, 27 -> item { // Chat & Shared Pool Manifest
                NeoCard {
                    Text("Shared Pool Manifest & Passenger Comms", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(
                        Pair("Seat 1: Amit Sharma", "Boarded • Connaught Place"),
                        Pair("Seat 2: Neha Verma", "Pickup in 400m • Barakhamba"),
                        Pair("Seat 3: EMPTY", "Available on corridor")
                    ).forEach { seat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                .background(ElectricNeoTokens.CyberSurface)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(seat.first, color = ElectricNeoTokens.TextWhite, fontSize = 12.sp)
                            Text(seat.second, color = ElectricNeoTokens.NeonEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    NeoGlowButton(
                        text = "Broadcast Arrival Notice to Next Pickup",
                        onClick = { statusMessage = "Arrival announcement broadcasted." },
                        accentColor = ElectricNeoTokens.ElectricCyan,
                        textColor = Color.Black
                    )
                }
            }

            29, 30, 31 -> item { // Fare Meter, Cash Collection, UPI QR
                NeoCard {
                    Text("Trip Finished • Fare Meter & UPI Collection", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Ride Fare", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                            Text("₹ 165.00", color = ElectricNeoTokens.NeonEmerald, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        }
                        NeoStatPill(label = "MODE", value = "CASH / UPI QR", accentColor = ElectricNeoTokens.SolarAmber)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = "Cash Tendered by Rider (e.g. ₹200)"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoGlowButton(
                        text = "Confirm Payment & Close Trip",
                        onClick = { statusMessage = "Payment confirmed. ₹165 credited to pilot ledger." },
                        accentColor = ElectricNeoTokens.NeonEmerald,
                        textColor = Color.Black
                    )
                }
            }

            32, 33, 34, 35 -> item { // Shift Summary & Metrics
                NeoCard {
                    Text("Shift Metrics & Daily Financial Ledger", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NeoStatPill(label = "TODAY'S PAY", value = "₹1,840", accentColor = ElectricNeoTokens.NeonEmerald)
                        NeoStatPill(label = "TRIPS", value = "14 RIDES", accentColor = ElectricNeoTokens.ElectricCyan)
                        NeoStatPill(label = "TIPS", value = "₹120", accentColor = ElectricNeoTokens.SolarAmber)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = "Closing Odometer KM (e.g. 14352)"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoGlowButton(
                        text = "End Duty Shift & Submit Handover",
                        onClick = { statusMessage = "Shift closed. Daily payout submitted for settlement." },
                        accentColor = ElectricNeoTokens.SolarAmber,
                        textColor = Color.Black
                    )
                }
            }

            36, 37, 38 -> item { // Daily Targets & Badges
                NeoCard {
                    Text("Incentive Targets & Performance Streaks", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(
                        Triple("Complete 12 Peak Rides", "10 / 12 (83%)", "₹250 Bonus"),
                        Triple("Maintain 4.8★ CSAT Rating", "4.92★ (Active)", "₹150 Bonus"),
                        Triple("Zero Cancellation Streak", "5 Days in a Row", "₹500 Badge")
                    ).forEach { goal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                .background(ElectricNeoTokens.CyberSurface)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(goal.first, color = ElectricNeoTokens.TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(goal.second, color = ElectricNeoTokens.ElectricCyan, fontSize = 11.sp)
                            }
                            Text(goal.third, color = ElectricNeoTokens.NeonEmerald, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    NeoGlowButton(
                        text = "Claim Unlocked ₹250 Incentive",
                        onClick = { statusMessage = "Bonus added to wallet balance!" },
                        accentColor = ElectricNeoTokens.NeonEmerald,
                        textColor = Color.Black
                    )
                }
            }

            39, 40, 41, 42 -> item { // Earnings & IMPS Payout
                NeoCard {
                    Text("Instant IMPS & UPI Settlement Hub", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Available for Withdrawal", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                            Text("₹ 2,420.00", color = ElectricNeoTokens.NeonEmerald, fontSize = 28.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        }
                        NeoStatPill(label = "STATUS", value = "INSTANT IMPS", accentColor = ElectricNeoTokens.NeonEmerald)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = "Enter Withdrawal Amount (e.g. ₹1000)"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoGlowButton(
                        text = "Instant Payout to Bank Account",
                        onClick = { statusMessage = "₹${sampleInput1.ifBlank { "1000" }} payout triggered via IMPS. UTR generated." },
                        accentColor = ElectricNeoTokens.NeonEmerald,
                        textColor = Color.Black
                    )
                }
            }

            43, 44, 46, 47 -> item { // Academy, Training, Helpdesk, Lost item
                NeoCard {
                    Text("Electric Academy Training & Pilot Support", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(
                        "🔋 Module 1: EV Battery Longevity & Regen Braking (Completed ✓)",
                        "🤝 Module 2: Commuter Etiquette & De-escalation (Completed ✓)",
                        "🚨 Module 3: Emergency Road Protocol & 112 SOPs (In Progress ➔)"
                    ).forEach { mod ->
                        Text(mod, color = ElectricNeoTokens.TextWhite, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    NeoTextField(
                        value = sampleInput1,
                        onValueChange = { sampleInput1 = it },
                        placeholder = if (screenId == 46) "Found item details (e.g. Black umbrella in rear)" else "Support Query / Maintenance Issue"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoGlowButton(
                        text = if (screenId == 46) "Log Found Item to Concierge" else "Submit Pilot Support Ticket",
                        onClick = { statusMessage = "Ticket submitted to fleet operations desk." },
                        accentColor = ElectricNeoTokens.ElectricCyan,
                        textColor = Color.Black
                    )
                }
            }

            49, 50 -> item { // Push & Driver Preferences
                NeoCard {
                    Text("Operating Zones & Vehicle Settings", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Preferred Operating Zone:", color = ElectricNeoTokens.TextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val zones = listOf("All Corridors", "Central Delhi (CP)", "Noida Sector 18", "Gurgaon Cyber Hub")
                        items(zones) { zone ->
                            val isSel = selectedTier == zone
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                    .background(if (isSel) ElectricNeoTokens.NeonEmerald else ElectricNeoTokens.CyberSurface)
                                    .border(1.dp, if (isSel) ElectricNeoTokens.NeonEmerald else ElectricNeoTokens.NeonEmeraldBorder, RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                    .clickable { selectedTier = zone }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(zone, color = if (isSel) Color.Black else ElectricNeoTokens.TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    NeoGlowButton(
                        text = "Save Operating Preferences",
                        onClick = { statusMessage = "Zone filter applied: $selectedTier." },
                        accentColor = ElectricNeoTokens.NeonEmerald,
                        textColor = Color.Black
                    )
                }
            }

            else -> item {
                NeoCard {
                    Text("Pilot Screen #${meta.id} Workspace", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    NeoTextField(value = sampleInput1, onValueChange = { sampleInput1 = it }, placeholder = "Input telemetry parameter...")
                    Spacer(modifier = Modifier.height(12.dp))
                    NeoGlowButton(
                        text = "Execute Pilot Action",
                        onClick = { statusMessage = "Action completed for ${meta.title}" },
                        accentColor = ElectricNeoTokens.NeonEmerald,
                        textColor = Color.Black
                    )
                }
            }
        }

        // Status Toast
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
