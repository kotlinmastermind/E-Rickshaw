package com.vaisentra.mobility.driver.feature.map

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vaisentra.mobility.core.map.LatLng
import com.vaisentra.mobility.core.map.AppMapDetailsCatalog
import com.vaisentra.mobility.core.map.MapboxSharedConfig
import com.vaisentra.mobility.core.map.DeviceLocationProvider
import com.vaisentra.mobility.core.map.RealDeviceLocation
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import java.util.Locale

/**
 * Enterprise Native Mapbox Driver Automotive Navigation Cockpit View.
 * Implements 3D automotive perspective tilt, turn-by-turn road polyline rendering,
 * smooth PointAnnotation driver tracking, spoken voice guidance via TextToSpeech,
 * and comprehensive driver HUD (Speedometer, Speed Limits, Maneuvers, Controls).
 */
@Composable
fun MapboxDriverMapView(
    modifier: Modifier = Modifier,
    driverLatLng: LatLng,
    bearing: Float = 0f,
    currentSpeedKmh: Int = 0,
    speedLimitKmh: Int = 25,
    pickupLatLng: LatLng? = null,
    pickupTitle: String = "Pickup Point",
    destLatLng: LatLng? = null,
    destTitle: String? = null,
    routePoints: List<LatLng> = emptyList(),
    currentManeuverInstruction: String? = null,
    maneuverIcon: String = "↑",
    distanceToManeuver: String? = null,
    showManeuverBanner: Boolean = false,
    showCockpitHud: Boolean = true,
    isArrivalDetected: Boolean = false,
    arrivalTitle: String = "Arrived at Destination",
    isVoiceMuted: Boolean = false,
    mapLayer: String = "DARK", // "LIGHT", "DARK", "SATELLITE", "TERRAIN"
    mapDetails: Map<String, Boolean> = AppMapDetailsCatalog.getDefaultMap(),
    is3DMode: Boolean = true,
    recenterTrigger: Int = 0,
    cameraTargetLatLng: LatLng? = null,
    cameraAnimTrigger: Int = 0,
    cameraAnimType: String = "flyTo", // "flyTo" or "easeTo"
    zoomLevel: Float = 17f,
    mapboxToken: String = MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN,
    onMapClick: ((LatLng) -> Unit)? = null,
    onVoiceMuteToggle: (() -> Unit)? = null,
    onToggle3D: (() -> Unit)? = null,
    onRecenter: (() -> Unit)? = null,
    onToggleLayer: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var liveDriverLoc by remember { mutableStateOf<RealDeviceLocation?>(null) }
    var hasLocationPermission by remember { mutableStateOf(DeviceLocationProvider.hasLocationPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            val last = DeviceLocationProvider.getLastKnownLocation(context)
            if (last != null) {
                liveDriverLoc = RealDeviceLocation(last.latitude, last.longitude)
            }
        }
    }

    // Auto-prompt on screen launch if permission not yet granted
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Continuous real-time GPS updates for pilot vehicle
    DisposableEffect(context, hasLocationPermission) {
        if (hasLocationPermission) {
            val stopUpdates = DeviceLocationProvider.startContinuousLocationUpdates(context, 1000L, 1.0f) { loc ->
                liveDriverLoc = loc
            }
            onDispose { stopUpdates() }
        } else {
            onDispose {}
        }
    }

    val activeDriverPos = liveDriverLoc?.toLatLng() ?: driverLatLng
    val activeBearing = if (liveDriverLoc != null && liveDriverLoc!!.bearing > 0f) liveDriverLoc!!.bearing else bearing
    val activeSpeedKmh = if (liveDriverLoc != null && liveDriverLoc!!.speedKmh > 0f) liveDriverLoc!!.speedKmh.toInt() else currentSpeedKmh

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var pointAnnotationManager by remember { mutableStateOf<PointAnnotationManager?>(null) }
    var polylineAnnotationManager by remember { mutableStateOf<PolylineAnnotationManager?>(null) }
    var activeLoadedStyleUri by remember { mutableStateOf<String?>(null) }

    val targetStyleUri = remember(mapLayer) {
        when (mapLayer.uppercase(Locale.US)) {
            "LIGHT" -> Style.STANDARD
            "SATELLITE" -> Style.SATELLITE_STREETS
            "TERRAIN" -> Style.OUTDOORS
            else -> Style.DARK
        }
    }

    // Voice Navigation Engine using Android TextToSpeech
    var ttsEngine: TextToSpeech? = remember { null }
    DisposableEffect(context) {
        val tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale.ENGLISH
            }
        }
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // Voice prompt trigger on maneuver instruction change
    LaunchedEffect(currentManeuverInstruction, isVoiceMuted) {
        if (!isVoiceMuted && !currentManeuverInstruction.isNullOrBlank()) {
            ttsEngine?.speak(
                currentManeuverInstruction,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "MANEUVER_AUDIO_PROMPT"
            )
        }
    }

    // Cached marker bitmaps
    val pickupBitmap = remember { createDriverPinBitmap(AndroidColor.parseColor("#D4AF37"), "P") }
    val destBitmap = remember { createDriverPinBitmap(AndroidColor.parseColor("#10B981"), "D") }
    val driverCarBitmap = remember { createDriverCarBitmap(AndroidColor.parseColor("#D4AF37")) }

    // Lifecycle binding for Mapbox Native MapView
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapViewRef?.onStart()
                Lifecycle.Event.ON_STOP -> mapViewRef?.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    mapViewRef?.onDestroy()
                    mapViewRef = null
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onDestroy()
            mapViewRef = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val resolvedToken = mapboxToken.ifBlank {
                    try {
                        val resToken = ctx.getString(com.vaisentra.mobility.driver.R.string.mapbox_access_token)
                        if (resToken.isNotBlank()) resToken else MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN
                    } catch (_: Throwable) {
                        MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN
                    }
                }.ifBlank { MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN }
                if (resolvedToken.isNotBlank()) {
                    try {
                        MapboxOptions.accessToken = resolvedToken
                    } catch (_: Throwable) {}
                }
                try {
                    MapView(ctx).apply {
                        mapViewRef = this

                        mapboxMap.loadStyle(targetStyleUri) {
                            activeLoadedStyleUri = targetStyleUri
                            val annotationPlugin = annotations
                            pointAnnotationManager = annotationPlugin.createPointAnnotationManager()
                            polylineAnnotationManager = annotationPlugin.createPolylineAnnotationManager()

                            // Setup initial 3D Cockpit Camera with worldwide perspective so it opens globally and animates smoothly into real driver location
                            mapboxMap.setCamera(
                                CameraOptions.Builder()
                                    .center(Point.fromLngLat(DeviceLocationProvider.WORLDWIDE_CENTER.longitude, DeviceLocationProvider.WORLDWIDE_CENTER.latitude))
                                    .bearing(0.0)
                                    .pitch(0.0)
                                    .zoom(DeviceLocationProvider.WORLDWIDE_ZOOM)
                                    .build()
                            )
                        }

                        // Map click listener
                        gestures.addOnMapClickListener { point ->
                            onMapClick?.invoke(LatLng(point.latitude(), point.longitude()))
                            true
                        }
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("MapboxDriverMapView", "Failed to inflate MapView: ${t.message}", t)
                    android.view.View(ctx)
                }
            },
            update = { view ->
                val mapView = view as? MapView ?: return@AndroidView
                // ONLY reload style if user explicitly switched the layer and it is NOT currently loaded
                if (activeLoadedStyleUri != null && activeLoadedStyleUri != targetStyleUri) {
                    activeLoadedStyleUri = targetStyleUri
                    mapView.mapboxMap.loadStyle(targetStyleUri) {
                        val annotationPlugin = mapView.annotations
                        pointAnnotationManager = annotationPlugin.createPointAnnotationManager()
                        polylineAnnotationManager = annotationPlugin.createPolylineAnnotationManager()
                    }
                }
            }
        )

        // -------------------------------------------------------------
        // COCKPIT OVERLAY UI: Top Maneuver Banner
        // -------------------------------------------------------------
        if (showManeuverBanner && !currentManeuverInstruction.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = maneuverIcon,
                                color = Color.Black,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentManeuverInstruction,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                        if (!distanceToManeuver.isNullOrBlank()) {
                            Text(
                                text = distanceToManeuver,
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // COCKPIT OVERLAY UI: Arrival Alert Banner
        // -------------------------------------------------------------
        if (isArrivalDetected) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF10B981),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (showManeuverBanner) 85.dp else 14.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🎯 $arrivalTitle",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // COCKPIT OVERLAY UI: Top-Right Control FABs
        // -------------------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = if (showManeuverBanner) 95.dp else 16.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 🎯 Current Location FAB
            Surface(
                shape = CircleShape,
                color = Color(0xFF0F172A).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color(0xFF10B981)),
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        val activePoint = liveDriverLoc?.toLatLng()?.takeIf { DeviceLocationProvider.isValidPhysicalLocation(it.latitude, it.longitude) }
                            ?: driverLatLng.takeIf { DeviceLocationProvider.isValidPhysicalLocation(it.latitude, it.longitude) }
                            ?: DeviceLocationProvider.getLastKnownLocation(context)?.takeIf { DeviceLocationProvider.isValidPhysicalLocation(it.latitude, it.longitude) }
                            ?: driverLatLng
                        val targetPoint = Point.fromLngLat(activePoint.longitude, activePoint.latitude)
                        val targetPitch = if (is3DMode) 55.0 else 0.0
                        val cameraOptions = CameraOptions.Builder()
                            .center(targetPoint)
                            .bearing(activeBearing.toDouble())
                            .pitch(targetPitch)
                            .zoom(zoomLevel.toDouble().coerceAtLeast(16.5))
                            .build()
                        val animOptions = MapAnimationOptions.mapAnimationOptions {
                            duration(1400)
                        }
                        mapViewRef?.camera?.flyTo(cameraOptions, animOptions)

                        DeviceLocationProvider.requestCurrentLocation(context) { freshLoc ->
                            if (DeviceLocationProvider.isValidPhysicalLocation(freshLoc.latitude, freshLoc.longitude)) {
                                liveDriverLoc = RealDeviceLocation(freshLoc.latitude, freshLoc.longitude)
                                val freshPoint = Point.fromLngLat(freshLoc.longitude, freshLoc.latitude)
                                val freshCamera = CameraOptions.Builder()
                                    .center(freshPoint)
                                    .bearing(activeBearing.toDouble())
                                    .pitch(targetPitch)
                                    .zoom(zoomLevel.toDouble().coerceAtLeast(16.5))
                                    .build()
                                mapViewRef?.camera?.flyTo(freshCamera, animOptions)
                            }
                        }
                        onRecenter?.invoke()
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🎯", fontSize = 18.sp)
                }
            }

            // 🌍 Worldwide Map View FAB
            Surface(
                shape = CircleShape,
                color = Color(0xFF0F172A).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        val cameraOptions = CameraOptions.Builder()
                            .center(Point.fromLngLat(20.0, 20.0))
                            .zoom(1.8)
                            .pitch(0.0)
                            .bearing(0.0)
                            .build()
                        val animOptions = MapAnimationOptions.mapAnimationOptions {
                            duration(2000)
                        }
                        mapViewRef?.camera?.flyTo(cameraOptions, animOptions)
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🌍", fontSize = 18.sp)
                }
            }

            // 3D / 2D Perspective Toggle
            Surface(
                shape = CircleShape,
                color = Color(0xFF0F172A).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onToggle3D?.invoke() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (is3DMode) "3D" else "2D",
                        color = if (is3DMode) Color(0xFF10B981) else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Voice Guidance Mute Toggle
            Surface(
                shape = CircleShape,
                color = Color(0xFF0F172A).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onVoiceMuteToggle?.invoke() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isVoiceMuted) "🔇" else "🔊",
                        fontSize = 17.sp
                    )
                }
            }

            // Map Style Switcher (Dark / Light / Satellite / Terrain)
            Surface(
                shape = CircleShape,
                color = Color(0xFF0F172A).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onToggleLayer?.invoke() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🗺️", fontSize = 17.sp)
                }
            }
        }

        // -------------------------------------------------------------
        // COCKPIT OVERLAY UI: Speedometer & EV Speed Limit HUD
        // -------------------------------------------------------------
        if (showCockpitHud) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.92f),
                border = BorderStroke(
                    1.dp,
                    if (activeSpeedKmh > speedLimitKmh) Color(0xFFEF4444) else Color(0xFF334155)
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Speed Value (Connected to real device hardware GPS velocity)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$activeSpeedKmh",
                            color = if (activeSpeedKmh > speedLimitKmh) Color(0xFFEF4444) else Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "km/h",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Circular Speed Limit Sign
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(3.dp, Color(0xFFEF4444)),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$speedLimitKmh",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // Overspeed Alert
                    if (activeSpeedKmh > speedLimitKmh) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x33EF4444),
                            border = BorderStroke(1.dp, Color(0xFFEF4444))
                        ) {
                            Text(
                                text = "SLOW DOWN",
                                color = Color(0xFFEF4444),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Real-Time Live Pilot GPS Status Pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, if (liveDriverLoc != null) Color(0xFF10B981) else Color(0xFFEAB308)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (showManeuverBanner && !currentManeuverInstruction.isNullOrBlank()) 86.dp else 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (liveDriverLoc != null) Color(0xFF10B981) else Color(0xFFEAB308),
                            CircleShape
                        )
                )
                Text(
                    text = if (liveDriverLoc != null) {
                        "PILOT LIVE GPS: %.5f, %.5f (%d km/h)".format(
                            liveDriverLoc!!.latitude,
                            liveDriverLoc!!.longitude,
                            activeSpeedKmh
                        )
                    } else if (hasLocationPermission) {
                        "LOCKING REAL PILOT GPS SATELLITES..."
                    } else {
                        "ENABLE PILOT GPS ACCESS"
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!hasLocationPermission) {
                    Text(
                        text = "ALLOW",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    // Update Annotations (Driver position, Pickup, Destination & Polyline)
    LaunchedEffect(
        pointAnnotationManager,
        polylineAnnotationManager,
        activeDriverPos,
        activeBearing,
        pickupLatLng,
        destLatLng,
        routePoints
    ) {
        val pManager = pointAnnotationManager ?: return@LaunchedEffect
        val lManager = polylineAnnotationManager ?: return@LaunchedEffect

        pManager.deleteAll()
        lManager.deleteAll()

        // 1. Driver Car Marker with Bearing
        val driverOption = PointAnnotationOptions()
            .withPoint(Point.fromLngLat(activeDriverPos.longitude, activeDriverPos.latitude))
            .withIconImage(driverCarBitmap)
            .withIconRotate(activeBearing.toDouble())
        pManager.create(driverOption)

        // 2. Pickup Point (if navigating to passenger)
        if (pickupLatLng != null) {
            val pickupOption = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(pickupLatLng.longitude, pickupLatLng.latitude))
                .withIconImage(pickupBitmap)
            pManager.create(pickupOption)
        }

        // 3. Destination Point (if trip in progress)
        if (destLatLng != null) {
            val destOption = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(destLatLng.longitude, destLatLng.latitude))
                .withIconImage(destBitmap)
            pManager.create(destOption)
        }

        // 4. Draw Turn-by-Turn Route Polyline
        if (routePoints.size >= 2) {
            val points = routePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
            val polylineOptions = PolylineAnnotationOptions()
                .withPoints(points)
                .withLineColor("#10B981") // High-visibility emerald green navigation route
                .withLineWidth(7.0)
                .withLineOpacity(0.92)
            lManager.create(polylineOptions)
        }
    }

    // Auto-animate & smooth zoom directly into real physical driver location on map open
    var hasAutoZoomedDriver by remember { mutableStateOf(false) }
    LaunchedEffect(activeLoadedStyleUri, liveDriverLoc, driverLatLng) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        if (activeLoadedStyleUri == null || hasAutoZoomedDriver) return@LaunchedEffect

        // Resolve real physical location (ignoring emulator Googleplex mock defaults)
        val physicalLoc = when {
            liveDriverLoc != null && DeviceLocationProvider.isValidPhysicalLocation(liveDriverLoc!!.latitude, liveDriverLoc!!.longitude) ->
                liveDriverLoc!!.toLatLng()
            driverLatLng != null && DeviceLocationProvider.isValidPhysicalLocation(driverLatLng.latitude, driverLatLng.longitude) ->
                driverLatLng
            else -> {
                val last = DeviceLocationProvider.getLastKnownLocation(context)
                if (last != null && DeviceLocationProvider.isValidPhysicalLocation(last.latitude, last.longitude)) last else null
            }
        }

        if (physicalLoc != null) {
            hasAutoZoomedDriver = true

            val targetPoint = Point.fromLngLat(physicalLoc.longitude, physicalLoc.latitude)
            val targetPitch = if (is3DMode) 55.0 else 0.0

            val cameraOptions = CameraOptions.Builder()
                .center(targetPoint)
                .bearing(activeBearing.toDouble())
                .pitch(targetPitch)
                .zoom(zoomLevel.toDouble().coerceAtLeast(16.5))
                .build()

            val animOptions = MapAnimationOptions.mapAnimationOptions {
                duration(2400)
            }
            mapView.camera.flyTo(cameraOptions, animOptions)
        }
    }

    // Camera follow driver in 3D navigation mode (ONLY after initial auto-zoom has landed)
    LaunchedEffect(activeDriverPos, activeBearing, is3DMode, hasAutoZoomedDriver) {
        if (!hasAutoZoomedDriver) return@LaunchedEffect
        if (!DeviceLocationProvider.isValidPhysicalLocation(activeDriverPos.latitude, activeDriverPos.longitude)) return@LaunchedEffect
        val mapView = mapViewRef ?: return@LaunchedEffect
        val targetPoint = Point.fromLngLat(activeDriverPos.longitude, activeDriverPos.latitude)
        val targetPitch = if (is3DMode) 55.0 else 0.0

        val cameraOptions = CameraOptions.Builder()
            .center(targetPoint)
            .bearing(activeBearing.toDouble())
            .pitch(targetPitch)
            .zoom(zoomLevel.toDouble().coerceAtLeast(16.5))
            .build()

        val animOptions = MapAnimationOptions.mapAnimationOptions {
            duration(500)
        }
        mapView.camera.easeTo(cameraOptions, animOptions)
    }

    // Manual Camera Recenter / Animation trigger
    LaunchedEffect(cameraAnimTrigger, cameraTargetLatLng, recenterTrigger) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        if (cameraAnimTrigger == 0 && recenterTrigger == 0) return@LaunchedEffect
        val target = cameraTargetLatLng
            ?: liveDriverLoc?.toLatLng()?.takeIf { DeviceLocationProvider.isValidPhysicalLocation(it.latitude, it.longitude) }
            ?: driverLatLng.takeIf { DeviceLocationProvider.isValidPhysicalLocation(it.latitude, it.longitude) }
            ?: DeviceLocationProvider.getLastKnownLocation(context)?.takeIf { DeviceLocationProvider.isValidPhysicalLocation(it.latitude, it.longitude) }
            ?: driverLatLng
        val targetPoint = Point.fromLngLat(target.longitude, target.latitude)
        val targetPitch = if (is3DMode) 55.0 else 0.0

        val cameraOptions = CameraOptions.Builder()
            .center(targetPoint)
            .bearing(bearing.toDouble())
            .pitch(targetPitch)
            .zoom(zoomLevel.toDouble().coerceAtLeast(16.5))
            .build()

        val animOptions = MapAnimationOptions.mapAnimationOptions {
            duration(1400)
        }

        if (cameraAnimType == "flyTo") {
            mapView.camera.flyTo(cameraOptions, animOptions)
        } else {
            mapView.camera.easeTo(cameraOptions, animOptions)
        }
    }
}

/**
 * Generates a high-contrast vector pin Bitmap for Driver Mapbox PointAnnotation.
 */
private fun createDriverPinBitmap(color: Int, label: String): Bitmap {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.argb(90, 0, 0, 0)
    }
    canvas.drawCircle(size / 2f, size / 2f + 4, size / 2.6f, shadowPaint)

    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f + 2.6f, size / 2.6f, pinPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawCircle(size / 2f, size / 2f + 2.6f, size / 2.6f, strokePaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        textSize = 34f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    val yPos = (size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(label, size / 2f, yPos, textPaint)

    return bitmap
}

/**
 * Generates an executive directional navigation arrow / car bitmap for the driver cockpit.
 */
private fun createDriverCarBitmap(accentColor: Int): Bitmap {
    val size = 110
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Outer radar pulse circle
    val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(60, 212, 175, 55)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 48f, pulsePaint)

    // Dark chassis background
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#121216")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 36f, bgPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(size / 2f, size / 2f, 36f, borderPaint)

    // Directional Navigation Triangle / EV Arrow (pointing upwards north)
    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    val path = android.graphics.Path().apply {
        moveTo(size / 2f, size / 2f - 24f) // Top tip
        lineTo(size / 2f + 16f, size / 2f + 18f) // Right bottom
        lineTo(size / 2f, size / 2f + 10f) // Center indent
        lineTo(size / 2f - 16f, size / 2f + 18f) // Left bottom
        close()
    }
    canvas.drawPath(path, arrowPaint)

    return bitmap
}
