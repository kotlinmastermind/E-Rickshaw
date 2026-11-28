package com.vaisentra.mobility.driver.feature.duty

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.rotate
import com.vaisentra.mobility.driver.components.DriverAvatar
import com.vaisentra.mobility.driver.components.DriverMapboxLiveView
import com.vaisentra.mobility.driver.components.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.R
import com.vaisentra.mobility.driver.components.DriverDialogController
import com.vaisentra.mobility.driver.data.ActiveRideDto
import com.vaisentra.mobility.driver.data.DailyIncentiveDto
import com.vaisentra.mobility.driver.data.DispatchOfferDto
import com.vaisentra.mobility.driver.data.DriverEarningsSummaryDto
import com.vaisentra.mobility.driver.data.DriverLiveLocation
import com.vaisentra.mobility.driver.data.DriverLocationManager
import com.vaisentra.mobility.driver.data.DriverRepository
import androidx.compose.runtime.collectAsState
import com.vaisentra.mobility.driver.theme.DriverGoTheme

@Composable
fun VasantraDriverHomeScreen(
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {}
) {
    val isOnline by DriverRepository.isOnline.collectAsState()
    val activeOffer by DriverRepository.activeOffer.collectAsState()
    val activeRide by DriverRepository.activeRide.collectAsState()
    val profile by DriverRepository.profile.collectAsState()
    val earnings by DriverRepository.earnings.collectAsState()
    val dailyTarget by DriverRepository.dailyTarget.collectAsState()

    val hasActiveRide = remember(activeRide?.id, activeRide?.status) {
        activeRide != null &&
        !activeRide?.id.isNullOrBlank() &&
        activeRide?.status?.uppercase() in listOf("ASSIGNED", "ARRIVED", "IN_PROGRESS", "STARTED")
    }
    val hasActiveOffer = remember(activeOffer?.rideId) {
        activeOffer != null && !activeOffer?.rideId.isNullOrBlank()
    }

    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        com.vaisentra.mobility.driver.fcm.DriverFcmManager.syncTokenIfAuthenticated(context)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                      (perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (granted) {
            DriverLocationManager.startTracking(context)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            try {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            DriverLocationManager.startTracking(context)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasNotifPerm = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotifPerm && (fineGranted || coarseGranted)) {
                try {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (_: Exception) { }
            }
        }
        com.vaisentra.mobility.driver.fcm.DriverFcmManager.syncTokenIfAuthenticated(context)

        DriverRepository.fetchDriverProfile()
        DriverRepository.fetchEarningsSummary()
        DriverRepository.fetchDailyTargets()
        DriverRepository.fetchActiveRide()
        DriverRepository.fetchUnreadNotificationsCount()
    }

    LaunchedEffect(isOnline) {
        if (isOnline) {
            DriverRepository.fetchActiveOffers()
            DriverRepository.fetchActiveRide()
            var loopTicks = 0
            while (true) {
                delay(5000)
                if (DriverRepository.isOnline.value) {
                    if (DriverRepository.activeRide.value == null) {
                        DriverRepository.fetchActiveOffers()
                    }
                    DriverRepository.fetchActiveRide()
                    loopTicks++
                    // Every 5 ticks (~17-20 seconds), refresh live earnings & target metrics from backend
                    if (loopTicks % 5 == 0) {
                        DriverRepository.fetchEarningsSummary()
                        DriverRepository.fetchDailyTargets()
                    }
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFFF5F6F8)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (hasActiveRide || hasActiveOffer) 140.dp else 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Dark Section for Status bar and Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF000000))
                        .padding(bottom = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(36.dp))

                    VasantraDriverTopHeader(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        driverName = profile.fullName.takeIf { it.isNotBlank() && !it.equals("Distinguished Guest", ignoreCase = true) } ?: "Driver",
                        driverRating = profile.rating?.takeIf { profile.ratingCount > 0 }
                            ?.let { rating -> "${"%.1f".format(rating)} (${profile.ratingCount})" } ?: "Not rated",
                        isOnline = isOnline,
                        onToggleDuty = { next ->
                            DriverRepository.setDutyStatus(next) { ok, err ->
                                if (!ok && next && (err?.contains("INSPECTION", ignoreCase = true) == true || err?.contains("inspection", ignoreCase = true) == true)) {
                                    DriverDialogController.show(DriverPopupType.ODOMETER_CHECK) {
                                        onNavigate("inspection")
                                    }
                                } else {
                                    DriverDialogController.showToast(
                                        if (ok) (if (next) "You are ONLINE" else "You are OFFLINE")
                                        else "Duty update failed: ${err ?: "Approval required"}"
                                    )
                                }
                            }
                        },
                        onProfileClick = {
                            onNavigate("profile")
                        }
                    )
                }

                // Main Content List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    VasantraMapSection(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(205.dp),
                        isOnline = isOnline,
                        onRecenterClick = {
                            DriverLocationManager.refreshCurrentLocation(context) { loc ->
                                if (loc.isLiveGps && com.vaisentra.mobility.driver.data.NativeLocationEvaluator.isValidCoordinate(loc.latitude, loc.longitude)) {
                                    DriverRepository.pingLocation(
                                        loc.latitude,
                                        loc.longitude,
                                        loc.heading,
                                        loc.speedKmh / 3.6,
                                        loc.accuracyMeters.toDouble()
                                    )
                                    DriverDialogController.showToast(
                                        "📍 Live GPS synced: ${String.format(java.util.Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)}"
                                    )
                                } else {
                                    val msg = when (DriverLocationManager.providerStatus.value) {
                                        com.vaisentra.mobility.driver.data.LocationProviderStatus.PERMISSION_REQUIRED ->
                                            "Location permission required for live GPS"
                                        com.vaisentra.mobility.driver.data.LocationProviderStatus.PROVIDERS_DISABLED ->
                                            "Turn ON device Location (GPS)"
                                        else -> "Acquiring live GPS fix..."
                                    }
                                    DriverDialogController.showToast(msg)
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    VasantraEarningsSummaryCard(
                        modifier = Modifier.fillMaxWidth(),
                        earnings = earnings,
                        onClick = { onNavigate("earnings") }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    VasantraQuickActionsGrid(
                        modifier = Modifier.fillMaxWidth(),
                        walletBalanceText = "₹${"%.0f".format(earnings.withdrawableBalanceRupees)}",
                        onActionClick = { route -> onNavigate(route) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    VasantraSosBannerCard(
                        modifier = Modifier.fillMaxWidth(),
                        onSosClick = { onNavigate("sos") }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    VasantraBonusCard(
                        modifier = Modifier.fillMaxWidth(),
                        dailyTarget = dailyTarget,
                        onBonusClick = { onNavigate("bonus") }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    VasantraMoreFeaturesGrid(
                        modifier = Modifier.fillMaxWidth(),
                        onViewAllClick = { onNavigate("catalog") },
                        onFeatureClick = { route -> onNavigate(route) }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Real-Time Floating Ride Request Card from FastAPI Backend
            AnimatedVisibility(
                visible = activeOffer != null && !activeOffer?.rideId.isNullOrBlank(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            ) {
                activeOffer?.let { offer ->
                    VasantraRideRequestCard(
                        modifier = Modifier.fillMaxWidth(),
                        offer = offer,
                        onAccept = {
                            val offerId = offer.rideId
                            if (offerId.isNotBlank()) {
                                DriverRepository.acceptOffer(offerId) { ok ->
                                    if (ok) onNavigate("accept_ride")
                                    else DriverDialogController.showToast("Could not accept ride")
                                }
                            } else {
                                onNavigate("accept_ride")
                            }
                        },
                        onReject = {
                            val offerId = offer.rideId
                            if (offerId.isNotBlank()) {
                                DriverRepository.rejectOffer(offerId)
                            }
                        }
                    )
                }
            }

            // Real-Time Floating Current Ride Card from FastAPI Backend (Only shown when active ride is running)
            AnimatedVisibility(
                visible = hasActiveRide && (activeOffer == null || activeOffer?.rideId.isNullOrBlank()),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            ) {
                activeRide?.let { ride ->
                    VasantraCurrentRideCard(
                        modifier = Modifier.fillMaxWidth(),
                        activeRide = ride,
                        onNavigateClick = { onNavigate("navigation") },
                        onEndRideClick = { onNavigate("end_ride") }
                    )
                }
            }
        }
    }
}

@Composable
fun VasantraDriverTopHeader(
    modifier: Modifier = Modifier,
    driverName: String = stringResource(id = R.string.driver_name_sample),
    driverRating: String = stringResource(id = R.string.driver_rating_sample),
    isOnline: Boolean = true,
    onToggleDuty: (Boolean) -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val profilePhotoUri by DriverRepository.profilePhotoUri.collectAsState()
    val context = LocalContext.current
    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(profilePhotoUri) {
        avatarBitmap = DriverRepository.loadAvatarBitmap(context, profilePhotoUri)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onProfileClick() }
        ) {
            // Profile Avatar
            DriverAvatar(
                bitmap = avatarBitmap,
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = driverName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(14.dp)) {
                        val starPath = Path().apply {
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val outerR = size.width / 2f
                            val innerR = outerR * 0.4f
                            for (i in 0 until 10) {
                                val r = if (i % 2 == 0) outerR else innerR
                                val angle = Math.toRadians((i * 36 - 90).toDouble())
                                val x = (centerX + r * kotlin.math.cos(angle)).toFloat()
                                val y = (centerY + r * kotlin.math.sin(angle)).toFloat()
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }
                        drawPath(path = starPath, color = Color(0xFFFACC15))
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = driverRating,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE5E7EB)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isOnline) stringResource(id = R.string.status_online) else stringResource(id = R.string.status_offline),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOnline) Color(0xFF10B981) else Color(0xFF9CA3AF)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = isOnline,
                onCheckedChange = { onToggleDuty(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF10B981),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF4B5563)
                )
            )
        }
    }
}

@Composable
fun VasantraMapSection(
    modifier: Modifier = Modifier,
    isOnline: Boolean = true,
    location: DriverLiveLocation? = null,
    onRecenterClick: () -> Unit = {}
) {
    val liveLoc = location ?: DriverRepository.driverLocation.collectAsState().value
    var showNativeMap by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE5EEF5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showNativeMap) {
                // Live Interactive Mapbox Map with Real Device Coordinates
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    DriverMapboxLiveView(
                        modifier = Modifier.fillMaxSize(),
                        driverLat = liveLoc.latitude,
                        driverLng = liveLoc.longitude,
                        headingDegrees = liveLoc.heading,
                        onRecenterClicked = onRecenterClick
                    )
                }
            } else {
                // Vasantra Stylized Live Radar Map isolated to its own composable to prevent full-screen recomposition
                VasantraRadarCanvas(location = liveLoc)
            }

            // Top Status & Locality Bar
            Row(
                modifier = Modifier
                    .padding(top = 10.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Map/Radar Toggle Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.clickable { showNativeMap = !showNativeMap }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (showNativeMap) "📡 Radar" else "🗺️ Live Map",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D61E7)
                        )
                    }
                }

                // Speech Bubble "You are online" / "You are offline"
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.98f),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) Color(0xFF10B981) else Color(0xFF94A3B8))
                        )
                        Text(
                            text = if (isOnline) stringResource(id = R.string.you_are_online) else stringResource(id = R.string.status_offline),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }

            // Live Address & GPS Coordinates Tag at Bottom-Left
            Surface(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.BottomStart),
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.94f),
                shadowElevation = 3.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = "📍 ${liveLoc.address.ifBlank { "Live Location" }}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.4f°N, %.4f°E", liveLoc.latitude, liveLoc.longitude)} • ${if (liveLoc.isLiveGps) "Live GPS" else "Default"}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (liveLoc.isLiveGps) Color(0xFF10B981) else Color(0xFF64748B)
                    )
                }
            }

            // Location crosshair button at bottom-right (Interactive & Live Backend Ping)
            Surface(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.BottomEnd)
                    .size(42.dp)
                    .clickable { onRecenterClick() },
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        drawCircle(color = Color(0xFF0F172A), style = Stroke(width = 2.dp.toPx()))
                        drawCircle(color = Color(0xFF2563EB), radius = 3.5.dp.toPx())
                        drawLine(color = Color(0xFF0F172A), start = Offset(size.width / 2f, 0f), end = Offset(size.width / 2f, 4.dp.toPx()), strokeWidth = 2.dp.toPx())
                        drawLine(color = Color(0xFF0F172A), start = Offset(size.width / 2f, size.height - 4.dp.toPx()), end = Offset(size.width / 2f, size.height), strokeWidth = 2.dp.toPx())
                        drawLine(color = Color(0xFF0F172A), start = Offset(0f, size.height / 2f), end = Offset(4.dp.toPx(), size.height / 2f), strokeWidth = 2.dp.toPx())
                        drawLine(color = Color(0xFF0F172A), start = Offset(size.width - 4.dp.toPx(), size.height / 2f), end = Offset(size.width, size.height / 2f), strokeWidth = 2.dp.toPx())
                    }
                }
            }
        }
    }
}

@Composable
private fun VasantraRadarCanvas(location: DriverLiveLocation) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 18f,
        targetValue = 46f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseRadius"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = Color(0xFFE5EEF5))

        val roadColor = Color.White
        val greenAreaColor = Color(0xFFDCFCE7)

        // Map grid streets
        drawLine(color = roadColor, start = Offset(0f, size.height * 0.25f), end = Offset(size.width, size.height * 0.4f), strokeWidth = 14.dp.toPx())
        drawLine(color = roadColor, start = Offset(0f, size.height * 0.65f), end = Offset(size.width, size.height * 0.5f), strokeWidth = 12.dp.toPx())
        drawLine(color = roadColor, start = Offset(size.width * 0.3f, 0f), end = Offset(size.width * 0.45f, size.height), strokeWidth = 14.dp.toPx())
        drawLine(color = roadColor, start = Offset(size.width * 0.75f, 0f), end = Offset(size.width * 0.85f, size.height), strokeWidth = 10.dp.toPx())

        // Green area (Park)
        val parkPath = Path().apply {
            moveTo(size.width * 0.05f, size.height * 0.05f)
            lineTo(size.width * 0.25f, size.height * 0.08f)
            lineTo(size.width * 0.2f, size.height * 0.3f)
            lineTo(size.width * 0.02f, size.height * 0.25f)
            close()
        }
        drawPath(path = parkPath, color = greenAreaColor)

        val parkPath2 = Path().apply {
            moveTo(size.width * 0.38f, size.height * 0.2f)
            lineTo(size.width * 0.45f, size.height * 0.35f)
            lineTo(size.width * 0.35f, size.height * 0.55f)
            close()
        }
        drawPath(path = parkPath2, color = greenAreaColor)

        // Demand Surge / Hotspot Marker Dot
        drawCircle(color = Color(0xFFF97316), radius = 6.dp.toPx(), center = Offset(size.width * 0.2f, size.height * 0.55f))

        // Real Live Driver Center Location Marker with Animated Radar Pulse
        val center = Offset(size.width * 0.5f, size.height * 0.52f)

        // Outer animated pulse ring
        drawCircle(
            color = Color(0xFF2563EB).copy(alpha = pulseAlpha),
            radius = pulseRadius.dp.toPx(),
            center = center
        )
        drawCircle(color = Color(0x333B82F6), radius = 24.dp.toPx(), center = center)
        drawCircle(color = Color(0x553B82F6), radius = 16.dp.toPx(), center = center)

        // Directional Navigation Arrow rotating with device heading
        rotate(degrees = location.heading.toFloat(), pivot = center) {
            val navArrow = Path().apply {
                moveTo(center.x, center.y - 14.dp.toPx())
                lineTo(center.x + 10.dp.toPx(), center.y + 10.dp.toPx())
                lineTo(center.x, center.y + 4.dp.toPx())
                lineTo(center.x - 10.dp.toPx(), center.y + 10.dp.toPx())
                close()
            }
            drawPath(path = navArrow, color = Color(0xFF2563EB))
        }
    }
}

@Composable
fun VasantraRideRequestCard(
    modifier: Modifier = Modifier,
    offer: DispatchOfferDto? = null,
    onAccept: () -> Unit = {},
    onReject: () -> Unit = {}
) {
    val pickupSummary = if (offer?.pickupDistanceKm != null && offer.pickupDurationMinutes != null) {
        "%.1f".format(offer.pickupDistanceKm) + " km • " + offer.pickupDurationMinutes + " min"
    } else "Unavailable"
    val tripSummary = if (offer != null && offer.distanceKm > 0.0 && offer.tripDurationMinutes != null) {
        "%.1f".format(offer.distanceKm) + " km • " + offer.tripDurationMinutes + " min"
    } else "Unavailable"
    val validEarnings = offer?.estimatedDriverEarningsRupees?.takeIf { it > 0.0 }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Category, Tag & Earnings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Green lightning bolt icon
                    Canvas(modifier = Modifier.size(22.dp)) {
                        val boltPath = Path().apply {
                            moveTo(size.width * 0.6f, 0f)
                            lineTo(size.width * 0.15f, size.height * 0.55f)
                            lineTo(size.width * 0.5f, size.height * 0.55f)
                            lineTo(size.width * 0.4f, size.height)
                            lineTo(size.width * 0.85f, size.height * 0.45f)
                            lineTo(size.width * 0.5f, size.height * 0.45f)
                            close()
                        }
                        drawPath(path = boltPath, color = Color(0xFF10B981))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = stringResource(id = R.string.ride_request_tag),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = offer?.vehicleCategory?.ifBlank { "Category unavailable" } ?: "No active offer",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (validEarnings != null) "₹${"%.0f".format(validEarnings)}" else "--",
                        fontSize = if (validEarnings != null) 24.sp else 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (validEarnings != null) Color(0xFF0F172A) else Color(0xFF9CA3AF)
                    )
                    Text(
                        text = if (validEarnings != null) "Estimated Gross" else "Gross Pending",
                        fontSize = 11.sp,
                        color = if (validEarnings != null) Color(0xFF10B981) else Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = offer?.estimatedCommissionCents?.let { amount -> "Commission: ₹${"%.2f".format(amount / 100.0)}" } ?: "Commission: unavailable",
                        fontSize = 10.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Two Summary Badges for Live Distance & Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Badge 1: Driver Live Location -> Pickup
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFECFDF5),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TO PICKUP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pickupSummary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                        Text(
                            text = "from live location",
                            fontSize = 10.sp,
                            color = Color(0xFF059669)
                        )
                    }
                }

                // Badge 2: Pickup -> Destination
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEFF6FF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3B82F6))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TRIP DISTANCE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tripSummary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E40AF)
                        )
                        Text(
                            text = "pickup to drop",
                            fontSize = 10.sp,
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stepper / Address Route List
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Pickup Address Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pickup Location (from your live location)",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = offer?.pickupAddress?.takeIf { it.isNotBlank() } ?: "Pickup address unavailable",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "📍 " + pickupSummary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669)
                            )
                        }
                    }

                    // Vertical Connector Line
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                            .width(2.dp)
                            .height(16.dp)
                            .background(Color(0xFFCBD5E1))
                    )

                    // Destination Address Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Drop / Destination Location",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = offer?.destinationAddress?.takeIf { it.isNotBlank() } ?: "Destination address unavailable",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "🏁 " + tripSummary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onReject() },
                    color = Color(0xFFF1F5F9)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.action_reject),
                            color = Color(0xFF334155),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAccept() },
                    color = Color(0xFF10B981)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.action_accept),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VasantraCurrentRideCard(
    modifier: Modifier = Modifier,
    activeRide: ActiveRideDto? = null,
    onNavigateClick: () -> Unit = {},
    onEndRideClick: () -> Unit = {}
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1320)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // White Car icon
                    Canvas(modifier = Modifier.size(24.dp)) {
                        val carPath = Path().apply {
                            moveTo(size.width * 0.15f, size.height * 0.4f)
                            cubicTo(size.width * 0.25f, size.height * 0.15f, size.width * 0.35f, size.height * 0.1f, size.width * 0.5f, size.height * 0.1f)
                            cubicTo(size.width * 0.65f, size.height * 0.1f, size.width * 0.75f, size.height * 0.15f, size.width * 0.85f, size.height * 0.4f)
                            lineTo(size.width * 0.98f, size.height * 0.45f)
                            cubicTo(size.width, size.height * 0.45f, size.width, size.height * 0.75f, size.width * 0.95f, size.height * 0.75f)
                            lineTo(size.width * 0.05f, size.height * 0.75f)
                            cubicTo(0f, size.height * 0.75f, 0f, size.height * 0.45f, size.width * 0.02f, size.height * 0.45f)
                            close()
                        }
                        drawPath(path = carPath, color = Color.White)
                        drawCircle(color = Color.White, radius = size.width * 0.08f, center = Offset(size.width * 0.25f, size.height * 0.82f))
                        drawCircle(color = Color.White, radius = size.width * 0.08f, center = Offset(size.width * 0.75f, size.height * 0.82f))
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = stringResource(id = R.string.current_ride_tag),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // En Route Tag Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFDCFCE7))
                        .clickable { onEndRideClick() }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = activeRide?.status?.replace("_", " ") ?: stringResource(id = R.string.tag_en_route),
                        color = Color(0xFF059669),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = activeRide?.pickupAddress?.takeIf { it.isNotBlank() }?.let { "Pickup: $it" } ?: stringResource(id = R.string.sample_current_pickup),
                fontSize = 13.sp,
                color = Color(0xFFE2E8F0),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = activeRide?.destinationAddress?.takeIf { it.isNotBlank() }?.let { "Drop: $it" } ?: stringResource(id = R.string.sample_current_drop),
                fontSize = 13.sp,
                color = Color(0xFFE2E8F0),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Progress Line with 3 dots
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth()) {
                        val strokeW = 4.dp.toPx()
                        val progressFraction = when (activeRide?.status) {
                            "ASSIGNED" -> 0.25f
                            "ARRIVED" -> 0.5f
                            "IN_PROGRESS" -> 0.75f
                            else -> 0.6f
                        }
                        val activeWidth = size.width * progressFraction

                        // Background track
                        drawLine(
                            color = Color(0xFF374151),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )

                        // Active green track
                        drawLine(
                            color = Color(0xFF10B981),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(activeWidth, size.height / 2f),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )

                        // Node dots
                        drawCircle(color = Color(0xFF10B981), radius = 5.dp.toPx(), center = Offset(0f, size.height / 2f))
                        drawCircle(color = Color(0xFF10B981), radius = 6.dp.toPx(), center = Offset(activeWidth, size.height / 2f))
                        drawCircle(color = Color(0xFF6B7280), radius = 5.dp.toPx(), center = Offset(size.width, size.height / 2f))
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Navigate Green Button
                Surface(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateClick() },
                    color = Color(0xFF10B981)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val navPath = Path().apply {
                                moveTo(size.width * 0.85f, 0f)
                                lineTo(size.width * 0.1f, size.height * 0.4f)
                                lineTo(size.width * 0.45f, size.height * 0.55f)
                                lineTo(size.width * 0.6f, size.height * 0.9f)
                                close()
                            }
                            drawPath(path = navPath, color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = stringResource(id = R.string.action_navigate),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VasantraEarningsSummaryCard(
    modifier: Modifier = Modifier,
    earnings: DriverEarningsSummaryDto = DriverEarningsSummaryDto(),
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1.3f)
            ) {
                // Wallet icon box
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF111827)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(0f, size.height * 0.25f),
                            size = Size(size.width, size.height * 0.65f),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawArc(
                            color = Color.White,
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(size.width * 0.3f, 0f),
                            size = Size(size.width * 0.4f, size.height * 0.35f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(size.width * 0.5f, size.height * 0.55f))
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = stringResource(id = R.string.todays_earnings_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF374151)
                    )
                    Text(
                        text = "₹${"%.0f".format(earnings.todayGrossRupees)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF111827)
                    )
                    Text(
                        text = "Commission tracked: ₹${"%.2f".format(earnings.todayCommissionRupees)}",
                        fontSize = 10.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Color(0xFFF3F4F6))
            )

            Column(
                modifier = Modifier.weight(0.7f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.label_rides),
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = earnings.todayCompletedTrips.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Color(0xFFF3F4F6))
            )

            Column(
                modifier = Modifier.weight(0.9f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.label_distance),
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${"%.1f".format(earnings.todayDistanceKm)} km",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
            }

            // Upward trend arrow icon
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Top),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    drawLine(color = Color(0xFF374151), start = Offset(0f, size.height), end = Offset(size.width, 0f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(color = Color(0xFF374151), start = Offset(size.width * 0.4f, 0f), end = Offset(size.width, 0f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(color = Color(0xFF374151), start = Offset(size.width, 0f), end = Offset(size.width, size.height * 0.6f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
fun VasantraQuickActionsGrid(
    modifier: Modifier = Modifier,
    walletBalanceText: String = stringResource(id = R.string.sample_wallet_balance),
    onActionClick: (String) -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VasantraQuickActionItem(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.label_wallet),
            subtitle = walletBalanceText,
            iconType = 0,
            onClick = { onActionClick("wallet") }
        )

        VasantraQuickActionItem(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.label_ride_history),
            subtitle = null,
            iconType = 1,
            onClick = { onActionClick("history") }
        )

        VasantraQuickActionItem(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.label_support),
            subtitle = null,
            iconType = 2,
            onClick = { onActionClick("support") }
        )

        VasantraQuickActionItem(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.label_documents),
            subtitle = null,
            iconType = 3,
            onClick = { onActionClick("documents") }
        )
    }
}

@Composable
fun VasantraQuickActionItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    iconType: Int,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    when (iconType) {
                        0 -> { // Wallet
                            drawRoundRect(
                                color = Color(0xFF111827),
                                topLeft = Offset(0f, size.height * 0.2f),
                                size = Size(size.width, size.height * 0.65f),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(color = Color(0xFF111827), radius = 2.dp.toPx(), center = Offset(size.width * 0.75f, size.height * 0.52f))
                        }
                        1 -> { // History (Clock)
                            drawCircle(color = Color(0xFF111827), style = Stroke(width = 2.dp.toPx()))
                            drawLine(color = Color(0xFF111827), start = Offset(size.width / 2f, size.height / 2f), end = Offset(size.width / 2f, size.height * 0.25f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(color = Color(0xFF111827), start = Offset(size.width / 2f, size.height / 2f), end = Offset(size.width * 0.75f, size.height / 2f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        }
                        2 -> { // Support (Headphones)
                            drawArc(
                                color = Color(0xFF111827),
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawRoundRect(color = Color(0xFF111827), topLeft = Offset(0f, size.height * 0.45f), size = Size(4.dp.toPx(), 8.dp.toPx()), cornerRadius = CornerRadius(1.dp.toPx()))
                            drawRoundRect(color = Color(0xFF111827), topLeft = Offset(size.width - 4.dp.toPx(), size.height * 0.45f), size = Size(4.dp.toPx(), 8.dp.toPx()), cornerRadius = CornerRadius(1.dp.toPx()))
                        }
                        else -> { // Documents
                            drawRoundRect(
                                color = Color(0xFF111827),
                                topLeft = Offset(size.width * 0.15f, 0f),
                                size = Size(size.width * 0.7f, size.height),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawLine(color = Color(0xFF111827), start = Offset(size.width * 0.35f, size.height * 0.3f), end = Offset(size.width * 0.65f, size.height * 0.3f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(color = Color(0xFF111827), start = Offset(size.width * 0.35f, size.height * 0.5f), end = Offset(size.width * 0.65f, size.height * 0.5f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(color = Color(0xFF111827), start = Offset(size.width * 0.35f, size.height * 0.7f), end = Offset(size.width * 0.55f, size.height * 0.7f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun VasantraSosBannerCard(
    modifier: Modifier = Modifier,
    onSosClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable { onSosClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Red shield icon with exclamation mark
                Canvas(modifier = Modifier.size(24.dp)) {
                    val shieldPath = Path().apply {
                        moveTo(size.width * 0.5f, 0f)
                        lineTo(size.width, size.height * 0.25f)
                        lineTo(size.width, size.height * 0.65f)
                        cubicTo(size.width, size.height * 0.85f, size.width * 0.5f, size.height, size.width * 0.5f, size.height)
                        cubicTo(size.width * 0.5f, size.height, 0f, size.height * 0.85f, 0f, size.height * 0.65f)
                        lineTo(0f, size.height * 0.25f)
                        close()
                    }
                    drawPath(path = shieldPath, color = Color(0xFFEF4444))
                    drawLine(color = Color.White, start = Offset(size.width / 2f, size.height * 0.3f), end = Offset(size.width / 2f, size.height * 0.6f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(size.width / 2f, size.height * 0.75f))
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = stringResource(id = R.string.sos_tag),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFEF4444)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = stringResource(id = R.string.emergency_assistance),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF991B1B)
                )
            }

            // Chevron right icon
            Canvas(modifier = Modifier.size(14.dp)) {
                val arrowPath = Path().apply {
                    moveTo(size.width * 0.3f, 0f)
                    lineTo(size.width * 0.8f, size.height / 2f)
                    lineTo(size.width * 0.3f, size.height)
                }
                drawPath(
                    path = arrowPath,
                    color = Color(0xFFEF4444),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

@Composable
fun VasantraBonusCard(
    modifier: Modifier = Modifier,
    dailyTarget: DailyIncentiveDto = DailyIncentiveDto(),
    onBonusClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable { onBonusClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1320)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Trophy icon
                    Canvas(modifier = Modifier.size(24.dp)) {
                        val cupPath = Path().apply {
                            moveTo(size.width * 0.2f, 0f)
                            lineTo(size.width * 0.8f, 0f)
                            lineTo(size.width * 0.75f, size.height * 0.45f)
                            cubicTo(size.width * 0.7f, size.height * 0.6f, size.width * 0.3f, size.height * 0.6f, size.width * 0.25f, size.height * 0.45f)
                            close()
                        }
                        drawPath(path = cupPath, color = Color(0xFFFACC15))
                        drawRect(color = Color(0xFFFACC15), topLeft = Offset(size.width * 0.42f, size.height * 0.55f), size = Size(size.width * 0.16f, size.height * 0.25f))
                        drawRoundRect(color = Color(0xFFFACC15), topLeft = Offset(size.width * 0.2f, size.height * 0.8f), size = Size(size.width * 0.6f, size.height * 0.2f), cornerRadius = CornerRadius(2.dp.toPx()))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = stringResource(id = R.string.bonus_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFACC15)
                        )
                        Text(
                            text = if (dailyTarget.targetRides > 0) "Complete ${dailyTarget.targetRides} rides for ₹${"%.0f".format(dailyTarget.rewardRupees)}" else stringResource(id = R.string.bonus_subtitle),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (dailyTarget.targetRides > 0) "${dailyTarget.completedRides}/${dailyTarget.targetRides}" else stringResource(id = R.string.bonus_progress),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Canvas(modifier = Modifier.size(12.dp)) {
                        val arrowPath = Path().apply {
                            moveTo(size.width * 0.3f, 0f)
                            lineTo(size.width * 0.8f, size.height / 2f)
                            lineTo(size.width * 0.3f, size.height)
                        }
                        drawPath(
                            path = arrowPath,
                            color = Color.White,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Yellow Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF374151))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (dailyTarget.targetRides > 0) (dailyTarget.completedRides.toFloat() / dailyTarget.targetRides.toFloat()).coerceIn(0f, 1f) else 0.6f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Color(0xFFFACC15))
                )
            }
        }
    }
}

@Composable
fun VasantraMoreFeaturesGrid(
    modifier: Modifier = Modifier,
    onViewAllClick: () -> Unit = {},
    onFeatureClick: (String) -> Unit = {}
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.more_features_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Text(
                text = stringResource(id = R.string.action_view_all),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D61E7),
                modifier = Modifier.clickable { onViewAllClick() }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VasantraFeatureItem(
                modifier = Modifier.weight(1f),
                title = stringResource(id = R.string.label_demand_heatmap),
                iconType = 0,
                onClick = { onFeatureClick("heatmap") }
            )

            VasantraFeatureItem(
                modifier = Modifier.weight(1f),
                title = stringResource(id = R.string.label_incentives),
                iconType = 1,
                onClick = { onFeatureClick("incentives") }
            )

            VasantraFeatureItem(
                modifier = Modifier.weight(1f),
                title = stringResource(id = R.string.label_daily_targets),
                iconType = 2,
                onClick = { onFeatureClick("targets") }
            )

            VasantraFeatureItem(
                modifier = Modifier.weight(1f),
                title = stringResource(id = R.string.label_performance),
                iconType = 3,
                onClick = { onFeatureClick("performance") }
            )
        }
    }
}

@Composable
fun VasantraFeatureItem(
    modifier: Modifier = Modifier,
    title: String,
    iconType: Int,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF3F4F6)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    when (iconType) {
                        0 -> {
                            val flamePath = Path().apply {
                                moveTo(size.width * 0.5f, 0f)
                                cubicTo(size.width * 0.8f, size.height * 0.4f, size.width, size.height * 0.6f, size.width * 0.8f, size.height)
                                cubicTo(size.width * 0.6f, size.height, size.width * 0.4f, size.height, size.width * 0.2f, size.height)
                                cubicTo(0f, size.height * 0.6f, size.width * 0.2f, size.height * 0.4f, size.width * 0.5f, 0f)
                                close()
                            }
                            drawPath(path = flamePath, color = Color(0xFFF97316))
                        }
                        1 -> {
                            val trophyPath = Path().apply {
                                moveTo(size.width * 0.2f, 0f)
                                lineTo(size.width * 0.8f, 0f)
                                lineTo(size.width * 0.7f, size.height * 0.5f)
                                lineTo(size.width * 0.3f, size.height * 0.5f)
                                close()
                            }
                            drawPath(path = trophyPath, color = Color(0xFFEAB308))
                            drawLine(color = Color(0xFFEAB308), start = Offset(size.width / 2f, size.height * 0.5f), end = Offset(size.width / 2f, size.height * 0.8f), strokeWidth = 3.dp.toPx())
                            drawRect(color = Color(0xFFEAB308), topLeft = Offset(size.width * 0.3f, size.height * 0.8f), size = Size(size.width * 0.4f, size.height * 0.2f))
                        }
                        2 -> {
                            drawCircle(color = Color(0xFFF97316), style = Stroke(width = 2.dp.toPx()))
                            drawCircle(color = Color(0xFFF97316), radius = 4.dp.toPx())
                        }
                        else -> {
                            drawLine(color = Color(0xFF06B6D4), start = Offset(size.width * 0.1f, size.height * 0.9f), end = Offset(size.width * 0.35f, size.height * 0.6f), strokeWidth = 2.dp.toPx())
                            drawLine(color = Color(0xFF06B6D4), start = Offset(size.width * 0.35f, size.height * 0.6f), end = Offset(size.width * 0.65f, size.height * 0.75f), strokeWidth = 2.dp.toPx())
                            drawLine(color = Color(0xFF06B6D4), start = Offset(size.width * 0.65f, size.height * 0.75f), end = Offset(size.width * 0.9f, size.height * 0.2f), strokeWidth = 2.dp.toPx())
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VasantraBottomNavigationBar(
    modifier: Modifier = Modifier,
    selectedTab: Int = 0,
    unreadCount: Int = 0,
    onTabSelected: (Int) -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = Color.White,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(66.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                stringResource(id = R.string.nav_home),
                stringResource(id = R.string.nav_trips),
                stringResource(id = R.string.nav_earnings),
                stringResource(id = R.string.nav_notifications),
                stringResource(id = R.string.nav_profile)
            )

            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index

                // Smooth animations for tab interaction
                val pillScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.0f else 0.94f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "pillScale"
                )
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color(0xFF64748B),
                    animationSpec = tween(220),
                    label = "iconColor"
                )
                val pillBgColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF0F172A) else Color.Transparent,
                    animationSpec = tween(220),
                    label = "pillBgColor"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8),
                    animationSpec = tween(220),
                    label = "textColor"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Elevated icon container pill
                    Box(
                        modifier = Modifier
                            .width(if (isSelected) 52.dp else 44.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(pillBgColor)
                            .scale(pillScale),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(19.dp)) {
                            val w = size.width
                            val h = size.height

                            when (index) {
                                0 -> { // Modern Architectural Home
                                    val roof = Path().apply {
                                        moveTo(w * 0.5f, h * 0.08f)
                                        lineTo(w * 0.96f, h * 0.44f)
                                        lineTo(w * 0.82f, h * 0.44f)
                                        lineTo(w * 0.82f, h * 0.92f)
                                        lineTo(w * 0.18f, h * 0.92f)
                                        lineTo(w * 0.18f, h * 0.44f)
                                        lineTo(w * 0.04f, h * 0.44f)
                                        close()
                                    }
                                    drawPath(path = roof, color = iconColor)

                                    // Doorway aperture
                                    drawRoundRect(
                                        color = if (isSelected) Color(0xFF0F172A) else Color.White,
                                        topLeft = Offset(w * 0.38f, h * 0.58f),
                                        size = Size(w * 0.24f, h * 0.34f),
                                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                    )
                                }
                                1 -> { // Aerodynamic Electric Mobility / Cab
                                    val body = Path().apply {
                                        moveTo(w * 0.14f, h * 0.48f)
                                        cubicTo(w * 0.22f, h * 0.20f, w * 0.32f, h * 0.14f, w * 0.50f, h * 0.14f)
                                        cubicTo(w * 0.68f, h * 0.14f, w * 0.78f, h * 0.20f, w * 0.86f, h * 0.48f)
                                        lineTo(w * 0.96f, h * 0.54f)
                                        cubicTo(w * 1.0f, h * 0.58f, w * 1.0f, h * 0.74f, w * 0.94f, h * 0.74f)
                                        lineTo(w * 0.06f, h * 0.74f)
                                        cubicTo(0f, h * 0.74f, 0f, h * 0.58f, w * 0.04f, h * 0.54f)
                                        close()
                                    }
                                    drawPath(path = body, color = iconColor)

                                    // Windshield glass
                                    val glass = Path().apply {
                                        moveTo(w * 0.24f, h * 0.44f)
                                        cubicTo(w * 0.28f, h * 0.26f, w * 0.36f, h * 0.22f, w * 0.50f, h * 0.22f)
                                        cubicTo(w * 0.64f, h * 0.22f, w * 0.72f, h * 0.26f, w * 0.76f, h * 0.44f)
                                        close()
                                    }
                                    drawPath(path = glass, color = if (isSelected) Color(0xFF0F172A) else Color.White)

                                    // Wheels with hubs
                                    drawCircle(color = iconColor, radius = w * 0.09f, center = Offset(w * 0.26f, h * 0.78f))
                                    drawCircle(color = if (isSelected) Color(0xFF0F172A) else Color.White, radius = w * 0.035f, center = Offset(w * 0.26f, h * 0.78f))
                                    drawCircle(color = iconColor, radius = w * 0.09f, center = Offset(w * 0.74f, h * 0.78f))
                                    drawCircle(color = if (isSelected) Color(0xFF0F172A) else Color.White, radius = w * 0.035f, center = Offset(w * 0.74f, h * 0.78f))
                                }
                                2 -> { // Graduated Financial Growth Analytics
                                    val barW = w * 0.22f
                                    val cr = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
                                    // Pillar 1 (Left - starter)
                                    drawRoundRect(
                                        color = iconColor.copy(alpha = if (isSelected) 0.65f else 0.5f),
                                        topLeft = Offset(w * 0.08f, h * 0.52f),
                                        size = Size(barW, h * 0.40f),
                                        cornerRadius = cr
                                    )
                                    // Pillar 2 (Center - peak)
                                    drawRoundRect(
                                        color = iconColor,
                                        topLeft = Offset(w * 0.39f, h * 0.16f),
                                        size = Size(barW, h * 0.76f),
                                        cornerRadius = cr
                                    )
                                    // Pillar 3 (Right - growth)
                                    drawRoundRect(
                                        color = iconColor.copy(alpha = if (isSelected) 0.85f else 0.75f),
                                        topLeft = Offset(w * 0.70f, h * 0.34f),
                                        size = Size(barW, h * 0.58f),
                                        cornerRadius = cr
                                    )
                                }
                                3 -> { // Precision Bell with Suspension Loop
                                    // Suspension loop
                                    drawCircle(
                                        color = iconColor,
                                        radius = w * 0.075f,
                                        center = Offset(w * 0.5f, h * 0.12f),
                                        style = Stroke(width = 1.8.dp.toPx())
                                    )
                                    // Bell flare
                                    val bell = Path().apply {
                                        moveTo(w * 0.5f, h * 0.16f)
                                        cubicTo(w * 0.32f, h * 0.16f, w * 0.22f, h * 0.32f, w * 0.22f, h * 0.56f)
                                        lineTo(w * 0.14f, h * 0.74f)
                                        lineTo(w * 0.86f, h * 0.74f)
                                        lineTo(w * 0.78f, h * 0.56f)
                                        cubicTo(w * 0.78f, h * 0.32f, w * 0.68f, h * 0.16f, w * 0.5f, h * 0.16f)
                                        close()
                                    }
                                    drawPath(path = bell, color = iconColor)

                                    // Clapper striker
                                    drawArc(
                                        color = iconColor,
                                        startAngle = 0f,
                                        sweepAngle = 180f,
                                        useCenter = false,
                                        topLeft = Offset(w * 0.38f, h * 0.76f),
                                        size = Size(w * 0.24f, h * 0.15f),
                                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                else -> { // Executive Captain Profile
                                    // Head circle
                                    drawCircle(
                                        color = iconColor,
                                        radius = w * 0.22f,
                                        center = Offset(w * 0.5f, h * 0.28f)
                                    )
                                    // Contoured shoulder mantle
                                    val mantle = Path().apply {
                                        moveTo(w * 0.12f, h * 0.88f)
                                        cubicTo(w * 0.12f, h * 0.60f, w * 0.30f, h * 0.54f, w * 0.50f, h * 0.54f)
                                        cubicTo(w * 0.70f, h * 0.54f, w * 0.88f, h * 0.60f, w * 0.88f, h * 0.88f)
                                        close()
                                    }
                                    drawPath(path = mantle, color = iconColor)
                                }
                            }
                        }

                        // High-contrast notification badge
                        if (index == 3 && unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 3.dp, y = (-2).dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    color = Color.White,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 8.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = title,
                        fontSize = if (isSelected) 11.sp else 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Active dot indicator
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun VasantraDriverHomeScreenPreview() {
    DriverGoTheme {
        VasantraDriverHomeScreen()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000L)
@Composable
private fun VasantraDriverTopHeaderPreview() {
    DriverGoTheme {
        VasantraDriverTopHeader(
            modifier = Modifier.padding(16.dp),
            driverName = "Rajesh Kumar",
            driverRating = "4.9 (128)",
            isOnline = true
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VasantraCurrentRideCardPreview() {
    DriverGoTheme {
        VasantraCurrentRideCard(
            modifier = Modifier.padding(16.dp),
            activeRide = ActiveRideDto(
                id = "ride_101",
                status = "EN_ROUTE",
                pickupAddress = "MG Road Metro Station, Bengaluru",
                destinationAddress = "Indiranagar 100ft Road, Bengaluru"
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VasantraMoreFeaturesGridPreview() {
    DriverGoTheme {
        VasantraMoreFeaturesGrid(
            modifier = Modifier.padding(16.dp)
        )
    }
}