package com.vaisentra.mobility.rider.feature.map

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import com.vaisentra.mobility.rider.core.network.NearbyVehicleDto
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

/**
 * Enterprise Native Mapbox Customer MapView.
 * Powered by Mapbox Maps SDK v11 (Native Vector Tiles, High-Performance Rendering,
 * PointAnnotationManager for live vehicles and PolylineAnnotationManager for route geometry).
 */
@Composable
fun MapboxCustomerMapView(
    modifier: Modifier = Modifier,
    pickupLatLng: LatLng,
    pickupTitle: String = "Pickup Point",
    destLatLng: LatLng? = null,
    destTitle: String? = null,
    driverLatLng: LatLng? = null,
    driverBearing: Float = 0f,
    userLocationLatLng: LatLng? = null,
    nearbyVehicles: List<NearbyVehicleDto> = emptyList(),
    routePoints: List<LatLng> = emptyList(),
    mapLayer: String = "LIGHT", // "LIGHT", "DARK", "SATELLITE", "TERRAIN"
    mapDetails: Map<String, Boolean> = AppMapDetailsCatalog.getDefaultMap(),
    recenterTrigger: Int = 0,
    cameraTargetLatLng: LatLng? = null,
    cameraAnimTrigger: Int = 0,
    cameraAnimType: String = "flyTo", // "flyTo" or "easeTo"
    zoomLevel: Float = 16f,
    mapboxToken: String = MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN,
    isEmergencyMode: Boolean = false,
    routeColor: String? = null,
    showLocationControls: Boolean = true,
    onRecenter: (() -> Unit)? = null,
    onMapClick: ((LatLng) -> Unit)? = null,
    onCameraMoved: ((LatLng) -> Unit)? = null,
    onVehicleClick: ((NearbyVehicleDto) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var liveDeviceLoc by remember { mutableStateOf<RealDeviceLocation?>(null) }
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
                liveDeviceLoc = RealDeviceLocation(last.latitude, last.longitude)
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

    // Continuous real-time GPS updates from real hardware GPS
    DisposableEffect(context, hasLocationPermission) {
        if (hasLocationPermission) {
            val stopUpdates = DeviceLocationProvider.startContinuousLocationUpdates(context, 1000L, 1.0f) { loc ->
                liveDeviceLoc = loc
            }
            onDispose { stopUpdates() }
        } else {
            onDispose {}
        }
    }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var pointAnnotationManager by remember { mutableStateOf<PointAnnotationManager?>(null) }
    var polylineAnnotationManager by remember { mutableStateOf<PolylineAnnotationManager?>(null) }
    var activeLoadedStyleUri by remember { mutableStateOf<String?>(null) }

    val targetStyleUri = remember(mapLayer) {
        when (mapLayer.uppercase()) {
            "DARK" -> Style.DARK
            "SATELLITE" -> Style.SATELLITE_STREETS
            "TERRAIN" -> Style.OUTDOORS
            else -> Style.STANDARD
        }
    }

    // Cached marker bitmaps
    val pickupBitmap = remember { createPinBitmap(AndroidColor.parseColor("#D4AF37"), "P") }
    val destBitmap = remember(isEmergencyMode) {
        if (isEmergencyMode) {
            createPinBitmap(AndroidColor.parseColor("#EF4444"), "🚨")
        } else {
            createPinBitmap(AndroidColor.parseColor("#10B981"), "D")
        }
    }
    val driverBitmap = remember { createVehicleBitmap(AndroidColor.parseColor("#D4AF37")) }
    val nearbyVehicleBitmap = remember { createVehicleBitmap(AndroidColor.parseColor("#9E9E9E")) }
    val userLocationBitmap = remember { createUserLocationBitmap() }

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
                        val resToken = ctx.getString(com.vaisentra.mobility.rider.R.string.mapbox_access_token)
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
                MapView(ctx).apply {
                    mapViewRef = this

                    mapboxMap.loadStyle(targetStyleUri) {
                        activeLoadedStyleUri = targetStyleUri
                        val annotationPlugin = annotations
                        pointAnnotationManager = annotationPlugin.createPointAnnotationManager()
                        polylineAnnotationManager = annotationPlugin.createPolylineAnnotationManager()

                        // Setup initial camera at worldwide perspective so map zooms in smoothly to user location
                        val initialLoc = userLocationLatLng ?: DeviceLocationProvider.getLastKnownLocation(ctx) ?: pickupLatLng
                        mapboxMap.setCamera(
                            CameraOptions.Builder()
                                .center(Point.fromLngLat(initialLoc.longitude, initialLoc.latitude))
                                .zoom(2.0)
                                .build()
                        )
                    }

                    // Map click listener
                    gestures.addOnMapClickListener { point ->
                        onMapClick?.invoke(LatLng(point.latitude(), point.longitude()))
                        true
                    }
                }
            },
            update = { mapView ->
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

        // Built-in Floating Worldwide & Current Location Controls
        if (showLocationControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 24.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 🌍 Worldwide Map View FAB
                FloatingActionButton(
                    onClick = {
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
                    },
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    containerColor = Color(0xFF0F172A).copy(alpha = 0.92f),
                    contentColor = Color(0xFF60A5FA),
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Text("🌍", fontSize = 18.sp)
                }

                // 🎯 Current Location FAB
                FloatingActionButton(
                    onClick = {
                        val activePoint = liveDeviceLoc?.toLatLng() ?: userLocationLatLng ?: DeviceLocationProvider.getLastKnownLocation(context) ?: pickupLatLng
                        val targetPoint = Point.fromLngLat(activePoint.longitude, activePoint.latitude)
                        val cameraOptions = CameraOptions.Builder()
                            .center(targetPoint)
                            .zoom(zoomLevel.toDouble())
                            .build()
                        val animOptions = MapAnimationOptions.mapAnimationOptions {
                            duration(1400)
                        }
                        mapViewRef?.camera?.flyTo(cameraOptions, animOptions)

                        DeviceLocationProvider.requestCurrentLocation(context) { freshLoc ->
                            liveDeviceLoc = RealDeviceLocation(freshLoc.latitude, freshLoc.longitude)
                            val freshPoint = Point.fromLngLat(freshLoc.longitude, freshLoc.latitude)
                            val freshCamera = CameraOptions.Builder()
                                .center(freshPoint)
                                .zoom(zoomLevel.toDouble())
                                .build()
                            mapViewRef?.camera?.flyTo(freshCamera, animOptions)
                        }
                        onRecenter?.invoke()
                    },
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    containerColor = Color(0xFF0F172A).copy(alpha = 0.92f),
                    contentColor = Color(0xFF10B981),
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Text("🎯", fontSize = 18.sp)
                }
            }
        }

        // Real-Time Live Device GPS Telemetry Pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, if (liveDeviceLoc != null) Color(0xFF10B981) else Color(0xFFEAB308)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
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
                            if (liveDeviceLoc != null) Color(0xFF10B981) else Color(0xFFEAB308),
                            CircleShape
                        )
                )
                Text(
                    text = if (liveDeviceLoc != null) {
                        "LIVE GPS: %.5f, %.5f (±%.0fm)".format(
                            liveDeviceLoc!!.latitude,
                            liveDeviceLoc!!.longitude,
                            liveDeviceLoc!!.accuracyMeters
                        )
                    } else if (hasLocationPermission) {
                        "ACQUIRING REAL DEVICE GPS..."
                    } else {
                        "ENABLE GPS ACCESS"
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!hasLocationPermission) {
                    Text(
                        text = "ALLOW",
                        color = Color(0xFF60A5FA),
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

    // Update Annotations (Markers & Route Polyline) when state changes
    LaunchedEffect(
        pointAnnotationManager,
        polylineAnnotationManager,
        pickupLatLng,
        destLatLng,
        driverLatLng,
        driverBearing,
        userLocationLatLng,
        liveDeviceLoc,
        nearbyVehicles,
        routePoints
    ) {
        val pManager = pointAnnotationManager ?: return@LaunchedEffect
        val lManager = polylineAnnotationManager ?: return@LaunchedEffect

        pManager.deleteAll()
        lManager.deleteAll()

        val vehicleAnnotationsMap = mutableMapOf<PointAnnotation, NearbyVehicleDto>()

        // 0. User Current Real Device GPS Location Dot / Puck
        val activeUserLoc = liveDeviceLoc?.toLatLng() ?: userLocationLatLng ?: DeviceLocationProvider.getLastKnownLocation(context)
        if (activeUserLoc != null) {
            val userOption = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(activeUserLoc.longitude, activeUserLoc.latitude))
                .withIconImage(userLocationBitmap)
            pManager.create(userOption)
        }

        // 1. Pickup Marker
        val pickupOption = PointAnnotationOptions()
            .withPoint(Point.fromLngLat(pickupLatLng.longitude, pickupLatLng.latitude))
            .withIconImage(pickupBitmap)
        pManager.create(pickupOption)

        // 2. Destination Marker (if set)
        if (destLatLng != null) {
            val destOption = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(destLatLng.longitude, destLatLng.latitude))
                .withIconImage(destBitmap)
            pManager.create(destOption)
        }

        // 3. Assigned Driver Marker with Bearing Rotation (if en route)
        if (driverLatLng != null) {
            val driverOption = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(driverLatLng.longitude, driverLatLng.latitude))
                .withIconImage(driverBitmap)
                .withIconRotate(driverBearing.toDouble())
            pManager.create(driverOption)
        }

        // 4. Nearby Fleet Vehicles
        for (veh in nearbyVehicles) {
            val vehPoint = Point.fromLngLat(veh.longitude, veh.latitude)
            val vehOption = PointAnnotationOptions()
                .withPoint(vehPoint)
                .withIconImage(nearbyVehicleBitmap)
            val ann = pManager.create(vehOption)
            vehicleAnnotationsMap[ann] = veh
        }

        // Click on nearby vehicle
        pManager.addClickListener { clickedAnn ->
            val veh = vehicleAnnotationsMap[clickedAnn]
            if (veh != null) {
                onVehicleClick?.invoke(veh)
                true
            } else {
                false
            }
        }

        // 5. Draw Navigation Route Polyline
        if (routePoints.size >= 2) {
            val points = routePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
            val lineColor = routeColor ?: if (isEmergencyMode) "#EF4444" else "#D4AF37"
            val polylineOptions = PolylineAnnotationOptions()
                .withPoints(points)
                .withLineColor(lineColor)
                .withLineWidth(if (isEmergencyMode) 8.0 else 6.0)
                .withLineOpacity(0.95)
            lManager.create(polylineOptions)
        }
    }

    // Auto-animate & smooth zoom directly into real device current location on map open
    var hasAutoZoomedToLocation by remember { mutableStateOf(false) }
    LaunchedEffect(activeLoadedStyleUri, userLocationLatLng, liveDeviceLoc) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        if (activeLoadedStyleUri == null || hasAutoZoomedToLocation) return@LaunchedEffect

        val loc = liveDeviceLoc?.toLatLng() ?: userLocationLatLng ?: DeviceLocationProvider.getLastKnownLocation(context) ?: pickupLatLng
        hasAutoZoomedToLocation = true

        val targetPoint = Point.fromLngLat(loc.longitude, loc.latitude)
        val cameraOptions = CameraOptions.Builder()
            .center(targetPoint)
            .zoom(zoomLevel.toDouble())
            .build()

        val animOptions = MapAnimationOptions.mapAnimationOptions {
            duration(2000)
        }

        mapView.camera.flyTo(cameraOptions, animOptions)
    }

    // Camera Recenter / Animation Handling
    LaunchedEffect(cameraAnimTrigger, cameraTargetLatLng, recenterTrigger) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        if (cameraAnimTrigger == 0 && recenterTrigger == 0) return@LaunchedEffect
        val target = cameraTargetLatLng ?: userLocationLatLng ?: DeviceLocationProvider.getLastKnownLocation(context) ?: pickupLatLng
        val targetPoint = Point.fromLngLat(target.longitude, target.latitude)

        val cameraOptions = CameraOptions.Builder()
            .center(targetPoint)
            .zoom(zoomLevel.toDouble())
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
 * Generates a crisp vector pin Bitmap for Mapbox PointAnnotation.
 */
private fun createPinBitmap(color: Int, label: String): Bitmap {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.argb(80, 0, 0, 0)
    }
    canvas.drawCircle(size / 2f, size / 2f + 4, size / 2.6f, shadowPaint)

    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2.6f, pinPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2.6f, strokePaint)

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
 * Generates an executive electric vehicle / rickshaw vector Bitmap for Mapbox.
 */
private fun createVehicleBitmap(accentColor: Int): Bitmap {
    val size = 90
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Outer glow / background circle
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#16161A")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 38f, bgPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(size / 2f, size / 2f, 38f, borderPaint)

    // Inner EV Rickshaw Icon Body
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    val rect = RectF(size / 2f - 18f, size / 2f - 24f, size / 2f + 18f, size / 2f + 24f)
    canvas.drawRoundRect(rect, 8f, 8f, bodyPaint)

    // Windshield
    val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#0F0F12")
        style = Paint.Style.FILL
    }
    val windshield = RectF(size / 2f - 14f, size / 2f - 20f, size / 2f + 14f, size / 2f - 6f)
    canvas.drawRoundRect(windshield, 4f, 4f, glassPaint)

    return bitmap
}

/**
 * Generates a glowing GPS blue user location dot for Mapbox PointAnnotation.
 */
private fun createUserLocationBitmap(): Bitmap {
    val size = 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Outer soft pulse / halo
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(80, 59, 130, 246)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, haloPaint)

    // White border ring
    val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 3.4f, whiteBorderPaint)

    // Inner bright blue dot
    val blueDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#2563EB")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 4.5f, blueDotPaint)

    return bitmap
}
