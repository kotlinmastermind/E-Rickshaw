package com.vaisentra.mobility.rider.feature.booking

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
import kotlinx.coroutines.launch
import com.vaisentra.mobility.rider.components.*
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.data.VehicleCategoryDto
import com.vaisentra.mobility.rider.theme.*

// Screen 7: Map Home & Live Radar
@Composable
fun MapHomeScreen(onNavigate: (String) -> Unit) {
    val categories by RiderRepository.categories.collectAsState()

    LaunchedEffect(Unit) {
        RiderRepository.fetchVehicleCategories()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
    ) {
        // Simulated Interactive Map Background Canvas
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(SapphireGlow)
                    .border(1.dp, BorderFocus, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🛺", fontSize = 42.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    NeoBadge(text = "8 EV PILOTS NEARBY", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Anand Vihar Hub (1.2 km)", color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        // Top Floating Executive Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                        .clickable { onNavigate("catalog") }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⚡ RiderGo", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        NeoBadge(text = "50 SCREENS", backgroundColor = SapphireGlow, textColor = ElectricCyan)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .clickable { DialogController.show(RiderPopupType.SOS_112) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🚨", fontSize = 18.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .clickable { onNavigate("wallet_hub") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "👛", fontSize = 18.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Trigger Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderFocus, RoundedCornerShape(18.dp))
                    .clickable { onNavigate("destination_search") }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔍", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "Where to? Enter Destination", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Pickup: Current GPS Location (Connaught Place)", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        // Bottom Quick Category Sheet
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(SurfaceElevated)
                .border(1.dp, BorderSubtle, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Direct EV Mobility Fleet", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Multi-Stop →",
                        color = ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onNavigate("multi_stop") }
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val quickTiers = listOf(
                        Triple("E-Rickshaw", "₹20 Base", "🛺"),
                        Triple("Eco-Pool", "₹10 Seat", "👥"),
                        Triple("E-Auto L5M", "₹30 Base", "⚡"),
                        Triple("E-Cargo", "₹50 Base", "📦")
                    )
                    quickTiers.forEach { (name, rate, icon) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceCard)
                                .clickable { onNavigate("fare_matrix") }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = icon, fontSize = 20.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = name, color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(text = rate, color = EmeraldGreen, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Screen 8: Destination Search & Predictive Autocomplete
@Composable
fun DestinationSearchScreen(onNavigate: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val savedPlaces by RiderRepository.savedPlaces.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Select Route", subtitle = "Predictive Mapbox Geocoding", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(12.dp))

        NeoTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search metro stations, offices, landmarks...",
            leadingIcon = "📍",
            trailingText = if (query.isNotEmpty()) "CLEAR" else null
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "Saved Shortcuts & Favorites", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(savedPlaces) { place ->
                NeoCard(
                    modifier = Modifier.clickable { onNavigate("fare_matrix") },
                    cornerRadius = 14.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SapphireGlow),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (place.tag) {
                                    "HOME" -> "🏠"
                                    "WORK" -> "💼"
                                    "AIRPORT" -> "✈️"
                                    else -> "🚇"
                                },
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = place.name, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(text = place.address, color = TextMuted, fontSize = 12.sp, maxLines = 1)
                        }
                        Text(text = "Select →", color = ElectricCyan, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// Screen 9: Multi-Stop Route Planner
@Composable
fun MultiStopRouteScreen(onNavigate: (String) -> Unit) {
    var stop1 by remember { mutableStateOf("Connaught Place (Pickup)") }
    var stop2 by remember { mutableStateOf("Pragati Maidan Metro (Waypoint)") }
    var stop3 by remember { mutableStateOf("Sector 62, Noida (Destination)") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Multi-Stop Planner", subtitle = "Up to 3 intermediate waypoints", onBack = { onNavigate("destination_search") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            NeoTextField(value = stop1, onValueChange = { stop1 = it }, placeholder = "Pickup Location", label = "Point A (Pickup)", leadingIcon = "🟢")
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(value = stop2, onValueChange = { stop2 = it }, placeholder = "Intermediate Stop (10 min dwell)", label = "Point B (Waypoint)", leadingIcon = "🟡")
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(value = stop3, onValueChange = { stop3 = it }, placeholder = "Final Destination", label = "Point C (Final Drop)", leadingIcon = "🔴")
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(text = "Calculate Multi-Stop Tariff", onClick = { onNavigate("fare_matrix") }, icon = "🗺️")
        }
    }
}

// Screen 10: Vehicle Tier & Fare Matrix (Direct from Supabase)
@Composable
fun VehicleFareMatrixScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("E_RICKSHAW_STANDARD") }

    val vehicleTiers = listOf(
        Triple("E_RICKSHAW_STANDARD", "Standard E-Rickshaw", "₹129.04 • 4 Seats • 3.96 km"),
        Triple("E_RICKSHAW_SHARE", "Shared Eco-Pool", "₹94.93 • Per Seat • 3.96 km"),
        Triple("E_AUTO", "High-Speed Electric Auto", "₹155.00 • 3 Seats • L5M Enclosed"),
        Triple("E_CARGO_LOADER", "E-Loader Cargo", "₹195.30 • 1 Seat • Parcel Transport")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Select Vehicle Tier", subtitle = "Live Tariffs Synced with Supabase", onBack = { onNavigate("destination_search") })
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(vehicleTiers) { (code, name, detail) ->
                val isSelected = selectedCategory == code
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) SurfaceCard else SurfaceElevated)
                        .border(
                            1.5.dp,
                            if (isSelected) BorderFocus else BorderSubtle,
                            RoundedCornerShape(18.dp)
                        )
                        .clickable { selectedCategory = code }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (code) {
                                "E_RICKSHAW_STANDARD" -> "🛺"
                                "E_RICKSHAW_SHARE" -> "👥"
                                "E_AUTO" -> "⚡"
                                else -> "📦"
                            },
                            fontSize = 32.sp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = name, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = detail, color = if (isSelected) ElectricCyan else TextMuted, fontSize = 12.sp)
                        }
                        if (isSelected) {
                            NeoBadge(text = "SELECTED", backgroundColor = SapphireGlow, textColor = ElectricCyan)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        NeoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Payment: UPI (GPay)", color = TextWhite, fontSize = 13.sp)
                Text(
                    text = "Apply Promo →",
                    color = EmeraldGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { DialogController.show(RiderPopupType.PROMO_APPLIED) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        NeoButton(
            text = "Request EV Chauffeur Now",
            onClick = {
                scope.launch {
                    RiderRepository.createRideBooking("Connaught Place", "Sector 62, Noida", selectedCategory)
                }
                onNavigate("dispatch_matching")
            },
            icon = "⚡"
        )
    }
}

// Screen 18: Schedule Later & Airport Sync
@Composable
fun ScheduleRideScreen(onNavigate: (String) -> Unit) {
    var flightNo by remember { mutableStateOf("6E-204 (IndiGo)") }
    var pickupDate by remember { mutableStateOf("Tomorrow, 07:30 AM") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Schedule Later & Airport", subtitle = "Reserve rides with automatic flight sync", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            NeoTextField(value = pickupDate, onValueChange = { pickupDate = it }, placeholder = "Pickup Date & Time", label = "Scheduled Pickup")
            Spacer(modifier = Modifier.height(14.dp))
            NeoTextField(value = flightNo, onValueChange = { flightNo = it }, placeholder = "Flight Number (e.g. 6E-204)", label = "Airport Flight Number", trailingText = "AUTO-SYNC ✈️")
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(text = "Confirm Scheduled Chauffeur", onClick = {
                DialogController.showToast("Ride scheduled for $pickupDate with flight sync active!")
                onNavigate("map_home")
            })
        }
    }
}

// Screen 19: Book for Family / Guest
@Composable
fun BookForGuestScreen(onNavigate: (String) -> Unit) {
    var guestName by remember { mutableStateOf("Anita Roy") }
    var guestPhone by remember { mutableStateOf("+91 98112 99887") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Book for Guest / Family", subtitle = "SMS tracking link sent to passenger", onBack = { onNavigate("map_home") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            NeoTextField(value = guestName, onValueChange = { guestName = it }, placeholder = "Guest Full Name", label = "Guest Name")
            Spacer(modifier = Modifier.height(14.dp))
            NeoTextField(value = guestPhone, onValueChange = { guestPhone = it }, placeholder = "Guest Mobile Number", label = "Guest Phone")
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(text = "Dispatch for Guest", onClick = {
                DialogController.showToast("Booking created! Live SMS tracking dispatched to $guestPhone")
                onNavigate("dispatch_matching")
            })
        }
    }
}

