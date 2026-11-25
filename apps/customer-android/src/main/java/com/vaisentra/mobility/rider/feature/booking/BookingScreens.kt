package com.vaisentra.mobility.rider.feature.booking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vaisentra.mobility.rider.components.*
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// =============================================================================
// SCREEN 7: RAPIDO MAP HOME WITH MAPBOX GL BACKDROP
// =============================================================================

@Composable
fun MapHomeScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val categories by RiderRepository.categories.collectAsState()
    val fareRules by RiderRepository.fareRules.collectAsState()
    val nearbyVehicles by RiderRepository.nearbyVehicles.collectAsState()
    val bookingDraft by RiderRepository.bookingDraft.collectAsState()
    val wallet by RiderRepository.wallet.collectAsState()

    var currentLat by remember { mutableDoubleStateOf(bookingDraft.pickupLat) }
    var currentLng by remember { mutableDoubleStateOf(bookingDraft.pickupLng) }
    var lastAcceptedSample by remember { mutableStateOf<com.vaisentra.mobility.rider.data.RiderLocationSample?>(null) }
    val activeCancelSignal = remember { mutableStateOf<CancellationSignal?>(null) }

    var fetchDeviceGpsAndSyncRef by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        com.vaisentra.mobility.rider.fcm.RiderFcmManager.syncTokenIfAuthenticated(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        fetchDeviceGpsAndSyncRef?.invoke(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            try {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (_: Exception) { }
        }
    }

    fun Location.toRiderSample(fallbackProvider: String): com.vaisentra.mobility.rider.data.RiderLocationSample {
        val mockFix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else {
            @Suppress("DEPRECATION")
            isFromMockProvider
        }
        return com.vaisentra.mobility.rider.data.RiderLocationSample(
            latitude = latitude,
            longitude = longitude,
            provider = provider ?: fallbackProvider,
            accuracyMeters = if (hasAccuracy()) accuracy else 25f,
            timestampMs = if (time > 0L) time else System.currentTimeMillis(),
            isMock = mockFix
        )
    }

    fun fetchDeviceGpsAndSync(showToast: Boolean = false) {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            if (!com.vaisentra.mobility.rider.data.NativeLocationEvaluator.isValidCoordinate(currentLat, currentLng)) {
                RiderRepository.updatePickupLocation(0.0, 0.0, "Allow Location permission for GPS pickup")
            }
            try {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } catch (_: Exception) {
                // Protected against un-registered launcher state
            }
            return
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            if (!com.vaisentra.mobility.rider.data.NativeLocationEvaluator.isValidCoordinate(currentLat, currentLng)) {
                RiderRepository.updatePickupLocation(0.0, 0.0, "Location service unavailable")
            }
            if (showToast) DialogController.showToast("Location service unavailable")
            return
        }

        val enabledProviders = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).filter { p ->
            try {
                lm.allProviders.contains(p) && lm.isProviderEnabled(p)
            } catch (_: Exception) {
                false
            }
        }

        if (enabledProviders.isEmpty()) {
            if (!com.vaisentra.mobility.rider.data.NativeLocationEvaluator.isValidCoordinate(currentLat, currentLng)) {
                RiderRepository.updatePickupLocation(0.0, 0.0, "Turn ON GPS Location & tap recenter")
            }
            if (showToast) {
                DialogController.showToast("Please turn ON device Location (GPS)")
            }
            return
        }

        if (showToast) {
            DialogController.showToast("Fetching live GPS location...")
        }

        try {
            val cachedCandidates = enabledProviders.mapNotNull { prov ->
                try {
                    lm.getLastKnownLocation(prov)?.toRiderSample(prov)
                } catch (_: SecurityException) {
                    null
                }
            }
            val bestLast = com.vaisentra.mobility.rider.data.NativeLocationEvaluator.selectBestLastKnown(cachedCandidates)

            if (bestLast != null) {
                lastAcceptedSample = bestLast
                currentLat = bestLast.latitude
                currentLng = bestLast.longitude
                scope.launch {
                    RiderRepository.syncLiveLocationAndVehicles(currentLat, currentLng)
                }
            }

            val provider = when {
                enabledProviders.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                enabledProviders.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> enabledProviders.first()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activeCancelSignal.value?.cancel()
                val signal = CancellationSignal()
                activeCancelSignal.value = signal
                lm.getCurrentLocation(
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context)
                ) { freshLoc ->
                    if (freshLoc != null) {
                        val candidate = freshLoc.toRiderSample(provider)
                        val selected = com.vaisentra.mobility.rider.data.NativeLocationEvaluator.selectBestLocation(
                            lastAcceptedSample, candidate
                        ) ?: candidate
                        lastAcceptedSample = selected
                        currentLat = selected.latitude
                        currentLng = selected.longitude
                        scope.launch {
                            RiderRepository.syncLiveLocationAndVehicles(currentLat, currentLng)
                            if (showToast) {
                                DialogController.showToast("Live location updated!")
                            }
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, { freshLoc ->
                    val candidate = freshLoc.toRiderSample(provider)
                    val selected = com.vaisentra.mobility.rider.data.NativeLocationEvaluator.selectBestLocation(
                        lastAcceptedSample, candidate
                    ) ?: candidate
                    lastAcceptedSample = selected
                    currentLat = selected.latitude
                    currentLng = selected.longitude
                    scope.launch {
                        RiderRepository.syncLiveLocationAndVehicles(currentLat, currentLng)
                        if (showToast) {
                            DialogController.showToast("Live location updated!")
                        }
                    }
                }, android.os.Looper.getMainLooper())
            }
        } catch (e: Exception) {
            if (showToast) {
                DialogController.showToast("GPS Error: ${e.message}")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeCancelSignal.value?.cancel()
            activeCancelSignal.value = null
        }
    }

    LaunchedEffect(Unit) {
        fetchDeviceGpsAndSyncRef = { fetchDeviceGpsAndSync(it) }
        launch(Dispatchers.IO) {
            launch { RiderRepository.fetchVehicleCategories() }
            launch { RiderRepository.fetchFareRules() }
            launch { RiderRepository.fetchWalletBalance() }
            launch { RiderRepository.fetchSavedPlaces() }
        }
        com.vaisentra.mobility.rider.fcm.RiderFcmManager.syncTokenIfAuthenticated(context)

        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            fetchDeviceGpsAndSync(showToast = false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (_: Exception) { }
            }
        } else {
            try {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } catch (_: Exception) {
                // Safeguard against launch before registry attachment
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10_000L)
            if (com.vaisentra.mobility.rider.data.NativeLocationEvaluator.isValidCoordinate(currentLat, currentLng)) {
                RiderRepository.syncLiveLocationAndVehicles(currentLat, currentLng)
            }
        }
    }

    // ==================== YEH UI HISSA MISSING THA ====================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
    ) {
        // 1. Live Mapbox Map with Real GPS & Backend Nearby Drivers
        MapboxLiveView(
            modifier = Modifier.fillMaxSize(),
            pickupLat = currentLat,
            pickupLng = currentLng,
            showRoute = false,
            pickupLabel = bookingDraft.pickupAddress,
            nearbyVehicles = nearbyVehicles,
            onRecenterClicked = {
                fetchDeviceGpsAndSync(showToast = true)
            }
        )

        // 2. Top Floating Pill Bar (Branding, Live SOS, Live Wallet Balance)
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
                        NeoBadge(text = "LIVE EV", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceElevated.copy(alpha = 0.92f))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                            .clickable { onNavigate("wallet_hub") }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "👛 ₹${wallet.displayBalanceRupees.toInt()}",
                            color = EmeraldGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Live Pickup & Destination Search Bar
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
                        val nearbyText = if (nearbyVehicles.isNotEmpty()) "${nearbyVehicles.size} EV Pilots Nearby" else "No EV Pilots Nearby"
                        Text(
                            text = "Pickup: ${bookingDraft.pickupAddress} • $nearbyText",
                            color = if (nearbyVehicles.isNotEmpty()) ElectricCyan else TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1
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

        // 4. Bottom Sheet Connected to Live Vehicle Categories & Fare Rules
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

                if (categories.isEmpty()) {
                    Text(
                        text = "Loading fleet from server...",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        categories.take(4).forEach { cat ->
                            val rule = fareRules.firstOrNull { it.categoryId == cat.id }
                            val subLabel = if (rule != null) "₹${rule.baseFareRupees} Base" else "${cat.capacity} Seats"
                            val displayIcon = if (!cat.iconUrl.isNullOrBlank() && !cat.iconUrl.startsWith("http")) {
                                cat.iconUrl
                            } else {
                                "🛺"
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SurfaceCard)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                    .clickable {
                                        RiderRepository.selectVehicleCategory(cat.code)
                                        onNavigate("destination_search")
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = displayIcon, fontSize = 22.sp)
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Text(
                                        text = cat.name,
                                        color = TextWhite,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = subLabel,
                                        color = EmeraldGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
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
    val bookingDraft by RiderRepository.bookingDraft.collectAsState()
    var searchResults by remember { mutableStateOf(listOf<com.vaisentra.mobility.rider.data.MapSearchResult>()) }
    var isSearching by remember { mutableStateOf(false) }

    // Debounced live search jab user type kare
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            isSearching = true
            kotlinx.coroutines.delay(300L)
            searchResults = kotlinx.coroutines.withContext(Dispatchers.IO) {
                RiderRepository.searchMapboxPlaces(trimmed)
            }
            isSearching = false
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceElevated)
                .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(EmeraldGreen))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Pickup: ${bookingDraft.pickupAddress}",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(CrimsonRed))
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(text = "Enter drop landmark, station, chowk...", color = TextDim, fontSize = 13.sp) },
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
                    if (query.isNotEmpty()) {
                        Text(
                            text = "✕",
                            color = TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { query = "" }
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (query.trim().length < 2) "Saved Places" else "Mapbox Suggested Places",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (isSearching) {
                Text(text = "Searching...", color = ElectricCyan, fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (query.trim().length >= 2) {
                if (searchResults.isNotEmpty()) {
                    items(searchResults) { place ->
                        NeoCard(
                            modifier = Modifier.clickable {
                                RiderRepository.updateDestination(place.full_address, place.latitude, place.longitude)
                                onNavigate("fare_matrix")
                            },
                            cornerRadius = 14.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "📍", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = place.name, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(text = place.full_address, color = ElectricCyan, fontSize = 11.sp, maxLines = 2)
                                }
                                Text(text = "Select →", color = EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                // In BookingScreens.kt (DestinationSearchScreen)
                } else if (!isSearching) {
                    item {
                        NeoCard(
                            modifier = Modifier.clickable {
                                isSearching = true
                                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                                    val resolved = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                        RiderRepository.resolveDestinationCoordinates(
                                            query.trim(),
                                            bookingDraft.pickupLat,
                                            bookingDraft.pickupLng
                                        )
                                    }
                                    isSearching = false
                                    RiderRepository.updateDestination(
                                        resolved.full_address,
                                        resolved.latitude,
                                        resolved.longitude
                                    )
                                    onNavigate("fare_matrix")
                                }
                            },
                            cornerRadius = 14.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🧭", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Search landmark: \"${query.trim()}\"",
                                        color = TextWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Resolve real GPS location & calculate fare",
                                        color = ElectricCyan,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(text = "Go →", color = EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // When query < 2 chars, render real saved places from database
                if (savedPlaces.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No saved places found in database.\nSearch destination above to plan your ride.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(savedPlaces) { place ->
                        NeoCard(
                            modifier = Modifier.clickable {
                                RiderRepository.updateDestination(place.address, place.latitude, place.longitude)
                                onNavigate("fare_matrix")
                            },
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
                                        text = when (place.tag.uppercase()) {
                                            "HOME" -> "🏠"
                                            "WORK" -> "💼"
                                            "AIRPORT" -> "✈️"
                                            else -> "📍"
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
}

// =============================================================================
// SCREEN 9: MULTI-STOP ROUTE PLANNER
// =============================================================================
// SCREEN 9: MULTI-STOP ROUTE ENGINE WITH DYNAMIC REORDERING & WAITING POLICY
// =============================================================================
@Composable
fun MultiStopRouteScreen(onNavigate: (String) -> Unit) {
    val bookingDraft by RiderRepository.bookingDraft.collectAsState()
    var pickupAddress by remember { mutableStateOf(bookingDraft.pickupAddress.ifBlank { "Current Pickup Location" }) }
    var destinationAddress by remember { mutableStateOf(bookingDraft.destinationAddress.ifBlank { "Final Destination" }) }
    val waypoints = remember {
        mutableStateListOf(
            "Metro Station Gate 2",
            "Apollo Pharmacy Sector 62"
        )
    }

    // Dynamic distance and fare estimation based on number of stops
    val baseDistanceKm = 4.2
    val extraPerStopKm = 1.4
    val totalDistanceKm = baseDistanceKm + (waypoints.size * extraPerStopKm)
    val estimatedMinutes = (totalDistanceKm * 2.8).toInt() + (waypoints.size * 5)
    val estimatedFare = 25.0 + (totalDistanceKm * 12.0) + (waypoints.size * 10.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(
            title = "Multi-Stop Planner",
            subtitle = "Up to 4 intermediate waypoints with waiting billing",
            onBack = { onNavigate("destination_search") }
        )
        Spacer(modifier = Modifier.height(14.dp))

        NeoCard {
            // Point A (Pickup)
            NeoTextField(
                value = pickupAddress,
                onValueChange = { pickupAddress = it },
                placeholder = "Pickup Location",
                label = "Point A (Pickup)",
                leadingIcon = "🟢"
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Intermediate Waypoints
            Text(
                text = "INTERMEDIATE WAYPOINTS (${waypoints.size}/4)",
                color = ElectricCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            waypoints.forEachIndexed { index, stopText ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        NeoTextField(
                            value = stopText,
                            onValueChange = { waypoints[index] = it },
                            placeholder = "Stop ${index + 1} address",
                            label = "Stop ${index + 1}",
                            leadingIcon = "🟡"
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    // Reorder Controls (Move Up / Move Down)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceDark)
                                    .clickable {
                                        val temp = waypoints[index]
                                        waypoints[index] = waypoints[index - 1]
                                        waypoints[index - 1] = temp
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(text = "▲", color = ElectricCyan, fontSize = 10.sp) }
                        }
                        if (index < waypoints.size - 1) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceDark)
                                    .clickable {
                                        val temp = waypoints[index]
                                        waypoints[index] = waypoints[index + 1]
                                        waypoints[index + 1] = temp
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(text = "▼", color = ElectricCyan, fontSize = 10.sp) }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    // Remove button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B151E))
                            .clickable { waypoints.removeAt(index) },
                        contentAlignment = Alignment.Center
                    ) { Text(text = "✕", color = CrimsonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }

            if (waypoints.size < 4) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                        .clickable { waypoints.add("Waypoint ${waypoints.size + 1}") }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "+ Add Intermediate Stop (${4 - waypoints.size} remaining)", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            // Point C (Final Drop)
            NeoTextField(
                value = destinationAddress,
                onValueChange = { destinationAddress = it },
                placeholder = "Final Destination",
                label = "Point ${('A'.code + waypoints.size + 1).toChar()} (Final Drop)",
                leadingIcon = "🔴"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Waiting Time Policy Card
        NeoCard(borderColor = SapphireGlow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "WAITING TIME POLICY", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                NeoBadge(text = "3 MINS FREE DWELL", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Each intermediate stop includes 3 mins complimentary dwell. Thereafter, live waiting charge of ₹2.00/min is billed to trip meter.",
                color = TextMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Total Distance:", color = TextDim, fontSize = 11.sp)
                    Text(text = String.format("%.1f km", totalDistanceKm), color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(text = "Est. Journey Time:", color = TextDim, fontSize = 11.sp)
                    Text(text = "$estimatedMinutes mins", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(text = "Estimated Fare:", color = TextDim, fontSize = 11.sp)
                    Text(text = "₹${estimatedFare.toInt()}.00", color = ElectricCyan, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(
            text = "Confirm Multi-Stop Route & Compare Fares →",
            onClick = {
                RiderRepository.updateDestination(destinationAddress, 28.6328, 77.2197)
                onNavigate("fare_matrix")
            },
            icon = "🗺️",
            backgroundColor = SapphirePrimary
        )
    }
}

// =============================================================================
// SCREEN 10: RAPIDO VEHICLE TIER WITH MAPBOX ROUTE POLYLINE BACKDROP
// =============================================================================
@Composable
fun VehicleFareMatrixScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val bookingDraft by RiderRepository.bookingDraft.collectAsState()
    val corpAccount by RiderRepository.corporateAccount.collectAsState()
    var isCorporateBilling by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(bookingDraft.selectedCategoryCode) }
    var fareComparisons by remember { mutableStateOf<List<com.vaisentra.mobility.rider.data.VehicleFareComparisonDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isNetworkError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        RiderRepository.fetchCorporateAccount()
    }

    val loadFares: () -> Unit = {
        scope.launch {
            isLoading = true
            errorMessage = null
            isNetworkError = false
            val res = RiderRepository.compareFares(
                bookingDraft.pickupLat,
                bookingDraft.pickupLng,
                bookingDraft.destinationLat,
                bookingDraft.destinationLng
            )
            isLoading = false
            res.onSuccess { list ->
                fareComparisons = list
                errorMessage = null
                if (list.isNotEmpty() && list.none { it.code == selectedCategory }) {
                    selectedCategory = list.first().code
                }
            }.onFailure { err ->
                fareComparisons = emptyList()
                errorMessage = err.message ?: "Failed to calculate live fare"
                isNetworkError = err is RiderRepository.FareCompareException.NetworkError ||
                    (err.message?.contains("Network", ignoreCase = true) == true)
            }
        }
    }

    LaunchedEffect(bookingDraft) {
        loadFares()
    }

    val firstEstimate = fareComparisons.firstOrNull()?.estimate
    val destTitle = bookingDraft.destinationAddress.ifBlank { "Selected Destination" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
    ) {
        MapboxLiveView(
            modifier = Modifier.fillMaxSize(),
            pickupLat = bookingDraft.pickupLat,
            pickupLng = bookingDraft.pickupLng,
            dropLat = bookingDraft.destinationLat,
            dropLng = bookingDraft.destinationLng,
            showRoute = true,
            pickupLabel = bookingDraft.pickupAddress,
            dropLabel = destTitle
        )

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
                    Text(text = "Route to $destTitle", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        text = when {
                            isLoading -> "Calculating live route & fare..."
                            errorMessage != null -> "Fare unavailable: $errorMessage"
                            fareComparisons.isEmpty() -> "No active tariffs configured for route"
                            firstEstimate != null -> "${firstEstimate.distanceKm} km • Estimated ${firstEstimate.estimatedDurationMinutes.toInt()} mins"
                            else -> "Route ready"
                        },
                        color = when {
                            errorMessage != null -> CrimsonRed
                            fareComparisons.isEmpty() -> AmberGold
                            else -> ElectricCyan
                        },
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                if (!isLoading && errorMessage == null && fareComparisons.isNotEmpty()) {
                    NeoBadge(text = "LIVE FARE", backgroundColor = SapphireGlow, textColor = ElectricCyan)
                } else if (errorMessage != null) {
                    NeoBadge(text = if (isNetworkError) "OFFLINE" else "ERROR", backgroundColor = CrimsonGlow, textColor = CrimsonRed)
                } else if (!isLoading && fareComparisons.isEmpty()) {
                    NeoBadge(text = "UNCONFIGURED", backgroundColor = AmberGold.copy(alpha = 0.2f), textColor = AmberGold)
                }
            }
        }

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
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BorderSubtle)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = ElectricCyan,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Calculating live vehicle tariffs...",
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Contacting routing engine & active tariff ledger",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceDark)
                            .border(1.dp, CrimsonRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (isNetworkError) "📡 Network Connection Error" else "⚠️ Fare Calculation Error",
                                color = CrimsonRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = errorMessage ?: "Unable to fetch live fare rates.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            NeoButton(
                                text = "Retry Fare Calculation",
                                onClick = { loadFares() },
                                backgroundColor = SapphirePrimary,
                                modifier = Modifier.height(44.dp)
                            )
                        }
                    }
                } else if (fareComparisons.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceDark)
                            .border(1.dp, AmberGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "🚫 No Active Tariffs Configured",
                                color = AmberGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No active fare rules were found in the database for the available vehicle categories on this route. Please contact administration or try another destination.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            NeoButton(
                                text = "Refresh Fares",
                                onClick = { loadFares() },
                                backgroundColor = SapphirePrimary,
                                modifier = Modifier.height(44.dp)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        fareComparisons.forEach { tier ->
                            val isSelected = selectedCategory == tier.code
                            val fareRupees = tier.estimate?.estimatedFareRupees ?: 0.0
                            val detailText = "₹${String.format(Locale.US, "%.2f", fareRupees)} • ${tier.capacity} Seats • ${tier.description ?: ""}"

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
                                    .clickable { selectedCategory = tier.code }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "🛺", fontSize = 26.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = tier.name, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(text = detailText, color = if (isSelected) ElectricCyan else TextMuted, fontSize = 11.sp, maxLines = 1)
                                    }
                                    if (isSelected) {
                                        NeoBadge(text = "SELECTED", backgroundColor = SapphireGlow, textColor = ElectricCyan)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                        text = "Apply Promo →",
                        color = EmeraldGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { DialogController.show(RiderPopupType.PROMO_APPLIED) }
                    )
                }

                if (corpAccount?.status in listOf("APPROVED", "ACTIVE")) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isCorporateBilling) SapphirePrimary else Color.Transparent)
                                .clickable { isCorporateBilling = false }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👤 Personal Ride", color = if (!isCorporateBilling) TextWhite else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCorporateBilling) ElectricCyan else Color.Transparent)
                                .clickable { isCorporateBilling = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🏢 Corporate Ride", color = if (isCorporateBilling) CanvasBlack else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                var isBooking by remember { mutableStateOf(false) }

                val selectedTier = fareComparisons.firstOrNull { it.code == selectedCategory }
                val selectedTierName = selectedTier?.name ?: "Ride"
                val canBook = !isLoading && errorMessage == null && selectedTier != null && (selectedTier.estimate?.estimatedFareRupees ?: 0.0) > 0.0

                val buttonText = when {
                    isBooking -> "Requesting Live Pilot..."
                    isLoading -> "Calculating Live Fare..."
                    errorMessage != null -> "Fare Unavailable — Retry Above"
                    fareComparisons.isEmpty() -> "Tariffs Not Configured"
                    isCorporateBilling -> "Book Corporate $selectedTierName"
                    else -> "Book $selectedTierName"
                }

                NeoButton(
                    text = buttonText,
                    enabled = canBook && !isBooking,
                    backgroundColor = if (canBook) (if (isCorporateBilling) ElectricCyan else SapphirePrimary) else SurfaceCard,
                    textColor = if (canBook) (if (isCorporateBilling) CanvasBlack else TextWhite) else TextDim,
                    onClick = {
                        if (!canBook || isBooking) return@NeoButton
                        scope.launch {
                            isBooking = true
                            val result = RiderRepository.createRideBooking(
                                pickupAddress = bookingDraft.pickupAddress,
                                destinationAddress = destTitle,
                                categoryId = selectedTier?.categoryId ?: selectedCategory,
                                estimateId = selectedTier?.estimate?.estimateId,
                                paymentMethod = if (isCorporateBilling) "CORPORATE_BILLING" else bookingDraft.paymentMethod,
                                bookingMode = if (isCorporateBilling) "CORPORATE_BILLING" else "STANDARD",
                                businessCostCenter = if (isCorporateBilling) corpAccount?.membership?.costCenter ?: corpAccount?.defaultCostCenter else null,
                                pickupLat = bookingDraft.pickupLat,
                                pickupLng = bookingDraft.pickupLng,
                                destLat = bookingDraft.destinationLat,
                                destLng = bookingDraft.destinationLng
                            )
                            isBooking = false
                            result.onSuccess {
                                onNavigate("dispatch_matching")
                            }.onFailure { err ->
                                DialogController.showToast("Booking Error: ${err.message}")
                            }
                        }
                    },
                    icon = if (canBook) "⚡" else null
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
    val bookingDraft by RiderRepository.bookingDraft.collectAsState()
    var flightNo by remember { mutableStateOf("") }
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
            NeoTextField(value = flightNo, onValueChange = { flightNo = it }, placeholder = "Flight Number (Optional)", label = "Airport Flight Number", trailingText = "AUTO-SYNC ✈️")
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(text = "Confirm Scheduled Ride", onClick = {
                val tomorrowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(System.currentTimeMillis() + 86_400_000L))

                CoroutineScope(Dispatchers.IO).launch {
                    val res = RiderRepository.scheduleRideBooking(
                        pickup = bookingDraft.pickupAddress,
                        dest = bookingDraft.destinationAddress,
                        pickupTimeIso = tomorrowIso
                    )
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        if (res.isSuccess) {
                            DialogController.showToast("Ride scheduled for $pickupDate!")
                            onNavigate("map_home")
                        } else {
                            DialogController.showToast(
                                res.exceptionOrNull()?.message
                                    ?: "Select valid pickup & destination before scheduling."
                            )
                        }
                    }
                }
            })
        }
    }
}

// =============================================================================
// SCREEN 19: BOOK FOR FAMILY / GUEST
// =============================================================================
@Composable
fun BookForGuestScreen(onNavigate: (String) -> Unit) {
    var guestName by remember { mutableStateOf("") }
    var guestPhone by remember { mutableStateOf("") }

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

@Preview
@Composable
fun MapHomeScreenPreview() {
    RiderGoTheme {
        MapHomeScreen(onNavigate = {})
    }
}

@Preview
@Composable
fun DestinationSearchScreenPreview() {
    RiderGoTheme {
        DestinationSearchScreen(onNavigate = {})
    }
}

@Preview
@Composable
fun MultiStopRouteScreenPreview() {
    RiderGoTheme {
        MultiStopRouteScreen(onNavigate = {})
    }
}

@Preview
@Composable
fun VehicleFareMatrixScreenPreview() {
    RiderGoTheme {
        VehicleFareMatrixScreen(onNavigate = {})
    }
}

@Preview
@Composable
fun ScheduleRideScreenPreview() {
    RiderGoTheme {
        ScheduleRideScreen(onNavigate = {})
    }
}

@Preview
@Composable
fun BookForGuestScreenPreview() {
    RiderGoTheme {
        BookForGuestScreen(onNavigate = {})
    }
}