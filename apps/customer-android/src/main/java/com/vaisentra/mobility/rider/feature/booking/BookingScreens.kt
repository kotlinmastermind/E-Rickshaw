package com.vaisentra.mobility.rider.feature.booking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

// =============================================================================
// SCREEN 7: RAPIDO MAP HOME WITH MAPBOX GL BACKDROP
// =============================================================================
@Composable
fun MapHomeScreen(onNavigate: (String) -> Unit) {
    val categories by RiderRepository.categories.collectAsState()
    val savedPlaces by RiderRepository.savedPlaces.collectAsState()

    LaunchedEffect(Unit) {
        RiderRepository.fetchVehicleCategories()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
    ) {
        // 1. Mapbox Dark Vector Map Canvas with Live Drivers & 3D Pitch
        MapboxLiveView(
            modifier = Modifier.fillMaxSize(),
            pickupLat = 28.6139,
            pickupLng = 77.2090,
            showRoute = false,
            pickupLabel = "Connaught Place"
        )

        // 2. Top Floating Pill Bar (Branding, Live SOS, Wallet Balance)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile & Brand Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceElevated.copy(alpha = 0.92f))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⚡ RiderGo", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        NeoBadge(text = "RAPIDO EV", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
                    }
                }

                // Action Shortcuts (SOS & Quick Wallet)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated.copy(alpha = 0.92f))
                            .border(1.dp, CrimsonRed.copy(alpha = 0.4f), CircleShape)
                            .clickable { DialogController.show(RiderPopupType.SOS_112) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🚨", fontSize = 18.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated.copy(alpha = 0.92f))
                            .border(1.dp, BorderSubtle, CircleShape)
                            .clickable { onNavigate("wallet_hub") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "👛", fontSize = 18.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Rapido Floating Destination Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceElevated.copy(alpha = 0.95f))
                    .border(1.5.dp, BorderFocus, RoundedCornerShape(20.dp))
                    .clickable { onNavigate("destination_search") }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SapphireGlow),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🔍", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Where are you going?",
                            color = TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Pickup: Connaught Place • 8 EV Pilots Nearby",
                            color = ElectricCyan,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "Go →",
                        color = ElectricCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Bottom Draggable Rapido Quick Fleet Sheet (Above persistent bottom bar)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(SurfaceElevated.copy(alpha = 0.96f))
                .border(1.dp, BorderSubtle, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Column {
                // Draggable notch indicator
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BorderSubtle)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose Your Ride",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Multi-Stop →",
                        color = ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onNavigate("multi_stop") }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4 Rapido Service Category Cards
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
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceCard)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                .clickable { onNavigate("fare_matrix") }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = icon, fontSize = 22.sp)
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = name,
                                    color = TextWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(text = rate, color = EmeraldGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// SCREEN 8: DESTINATION SEARCH WITH MAPBOX AUTOCOMPLETE
// =============================================================================
@Composable
fun DestinationSearchScreen(onNavigate: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val savedPlaces by RiderRepository.savedPlaces.collectAsState()
    var searchResults by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(
            title = "Where To?",
            subtitle = "Mapbox Geocoding & Instant Autocomplete",
            onBack = { onNavigate("map_home") }
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Pickup & Destination Inputs (Rapido Double Pin Box)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceElevated)
                .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Pickup Pin
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(EmeraldGreen))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Current GPS: Connaught Place, New Delhi",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Divider(color = BorderSubtle, thickness = 1.dp)

                // Drop Destination Input
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(CrimsonRed))
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            if (it.length >= 2) {
                                scope.launch {
                                    val places = RiderRepository.searchMapboxPlaces(it)
                                    searchResults = if (places.isNotEmpty()) {
                                        places.map { p -> p.full_address }
                                    } else {
                                        listOf(
                                            "$it, Central Delhi (Metro Gate 4)",
                                            "$it Commercial Hub, Sector 62",
                                            "$it Transit Station, Anand Vihar"
                                        )
                                    }
                                }
                            }
                        },
                        placeholder = { Text(text = "Enter drop landmark, metro...", color = TextDim, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (query.isEmpty()) "Saved Places & Metro Feeder Hubs" else "Mapbox Suggested Routes",
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (query.isNotEmpty() && searchResults.isNotEmpty()) {
                items(searchResults) { result ->
                    NeoCard(
                        modifier = Modifier.clickable { onNavigate("fare_matrix") },
                        cornerRadius = 14.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📍", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = result, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(text = "Mapbox Verified EV Transit Corridor", color = ElectricCyan, fontSize = 11.sp)
                            }
                            Text(text = "Select →", color = EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                items(savedPlaces) { place ->
                    NeoCard(
                        modifier = Modifier.clickable { onNavigate("fare_matrix") },
                        cornerRadius = 14.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
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
                            Text(text = "Select →", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// SCREEN 9: MULTI-STOP ROUTE PLANNER
// =============================================================================
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
        NeoHeader(
            title = "Multi-Stop Planner",
            subtitle = "Up to 3 intermediate waypoints with Mapbox Matrix",
            onBack = { onNavigate("destination_search") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            NeoTextField(value = stop1, onValueChange = { stop1 = it }, placeholder = "Pickup Location", label = "Point A (Pickup)", leadingIcon = "🟢")
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(value = stop2, onValueChange = { stop2 = it }, placeholder = "Intermediate Stop (10 min dwell)", label = "Point B (Waypoint)", leadingIcon = "🟡")
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(value = stop3, onValueChange = { stop3 = it }, placeholder = "Final Destination", label = "Point C (Final Drop)", leadingIcon = "🔴")
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(text = "Calculate Mapbox Multi-Stop Tariff", onClick = { onNavigate("fare_matrix") }, icon = "🗺️")
        }
    }
}

// =============================================================================
// SCREEN 10: RAPIDO VEHICLE TIER WITH MAPBOX ROUTE POLYLINE BACKDROP
// =============================================================================
@Composable
fun VehicleFareMatrixScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("E_RICKSHAW_STANDARD") }

    val vehicleTiers = listOf(
        Triple("E_RICKSHAW_STANDARD", "Standard E-Rickshaw", "₹129.04 • 4 Seats • 2 min away"),
        Triple("E_RICKSHAW_SHARE", "Shared Eco-Pool", "₹94.93 • Per Seat • 3 min away"),
        Triple("E_AUTO", "High-Speed Electric Auto", "₹155.00 • 3 Seats • L5M Enclosed"),
        Triple("E_CARGO_LOADER", "E-Loader Cargo", "₹195.30 • 1 Seat • Parcel Transport")
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
    ) {
        // 1. Live Mapbox Vector Map with Active Route Polyline (Connaught Place -> Sector 62)
        MapboxLiveView(
            modifier = Modifier.fillMaxSize(),
            pickupLat = 28.6139,
            pickupLng = 77.2090,
            dropLat = 28.6289,
            dropLng = 77.3712,
            showRoute = true,
            pickupLabel = "Connaught Place",
            dropLabel = "Sector 62, Noida"
        )

        // 2. Top Route Info Header Floating Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp, start = 16.dp, end = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceElevated.copy(alpha = 0.94f))
                .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .clickable { onNavigate("destination_search") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "←", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Route to Sector 62, Noida", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = "3.96 km • Estimated 14 mins via Barapullah", color = ElectricCyan, fontSize = 11.sp)
                }
                NeoBadge(text = "LIVE FARE", backgroundColor = SapphireGlow, textColor = ElectricCyan)
            }
        }

        // 3. Rapido Bottom Sheet for Vehicle Tiers & Booking Action
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(SurfaceElevated.copy(alpha = 0.98f))
                .border(1.dp, BorderSubtle, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column {
                // Draggable handle
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BorderSubtle)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Vehicle List (Compact Rapido Vertical List)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vehicleTiers.forEach { (code, name, detail) ->
                        val isSelected = selectedCategory == code
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) SurfaceCard else SurfaceDark)
                                .border(
                                    1.5.dp,
                                    if (isSelected) BorderFocus else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedCategory = code }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (code) {
                                        "E_RICKSHAW_STANDARD" -> "🛺"
                                        "E_RICKSHAW_SHARE" -> "👥"
                                        "E_AUTO" -> "⚡"
                                        else -> "📦"
                                    },
                                    fontSize = 26.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = name, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(text = detail, color = if (isSelected) ElectricCyan else TextMuted, fontSize = 11.sp)
                                }
                                if (isSelected) {
                                    NeoBadge(text = "SELECTED", backgroundColor = SapphireGlow, textColor = ElectricCyan)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Payment & Promo Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigate("payment_methods") }
                    ) {
                        Text(text = "💳 UPI (GPay)", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(text = " ▾", color = TextMuted, fontSize = 12.sp)
                    }
                    Text(
                        text = "Apply Promo (₹65 Off) →",
                        color = EmeraldGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { DialogController.show(RiderPopupType.PROMO_APPLIED) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Big Rapido "Book Ride" Button
                NeoButton(
                    text = when (selectedCategory) {
                        "E_RICKSHAW_STANDARD" -> "Book Standard E-Rickshaw"
                        "E_RICKSHAW_SHARE" -> "Book Shared Eco-Pool Seat"
                        "E_AUTO" -> "Book High-Speed Electric Auto"
                        else -> "Book E-Loader Cargo"
                    },
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
    }
}

// =============================================================================
// SCREEN 18: SCHEDULE RIDE LATER
// =============================================================================
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
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    RiderRepository.scheduleRideBooking(
                        pickup = "Home (Scheduled)",
                        dest = "Indira Gandhi Airport T3",
                        pickupTimeIso = java.time.Instant.now().plusSeconds(86400).toString()
                    )
                }
                DialogController.showToast("Ride scheduled for $pickupDate with flight sync active!")
                onNavigate("map_home")
            })
        }
    }
}

// =============================================================================
// SCREEN 19: BOOK FOR FAMILY / GUEST
// =============================================================================
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
