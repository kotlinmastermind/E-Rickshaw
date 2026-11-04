package com.luxury.chauffeur.customer.feature.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.model.LatLng
import com.luxury.chauffeur.core.map.AppMapDetailsCatalog
import com.luxury.chauffeur.core.map.MapboxSharedConfig
import com.luxury.chauffeur.customer.core.network.NearbyVehicleDto
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
    onMapClick: ((LatLng) -> Unit)? = null,
    onCameraMoved: ((LatLng) -> Unit)? = null,
    onVehicleClick: ((NearbyVehicleDto) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
    val destBitmap = remember { createPinBitmap(AndroidColor.parseColor("#10B981"), "D") }
    val driverBitmap = remember { createVehicleBitmap(AndroidColor.parseColor("#D4AF37")) }
    val nearbyVehicleBitmap = remember { createVehicleBitmap(AndroidColor.parseColor("#9E9E9E")) }

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

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val resolvedToken = mapboxToken.ifBlank {
                try {
                    ctx.getString(com.luxury.chauffeur.customer.R.string.mapbox_access_token)
                } catch (_: Throwable) {
                    ""
                }
            }
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

                    // Setup initial camera
                    mapboxMap.setCamera(
                        CameraOptions.Builder()
                            .center(Point.fromLngLat(pickupLatLng.longitude, pickupLatLng.latitude))
                            .zoom(zoomLevel.toDouble())
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

    // Update Annotations (Markers & Route Polyline) when state changes
    LaunchedEffect(
        pointAnnotationManager,
        polylineAnnotationManager,
        pickupLatLng,
        destLatLng,
        driverLatLng,
        nearbyVehicles,
        routePoints
    ) {
        val pManager = pointAnnotationManager ?: return@LaunchedEffect
        val lManager = polylineAnnotationManager ?: return@LaunchedEffect

        pManager.deleteAll()
        lManager.deleteAll()

        val vehicleAnnotationsMap = mutableMapOf<PointAnnotation, NearbyVehicleDto>()

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

        // 3. Assigned Driver Marker (if en route)
        if (driverLatLng != null) {
            val driverOption = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(driverLatLng.longitude, driverLatLng.latitude))
                .withIconImage(driverBitmap)
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
            val polylineOptions = PolylineAnnotationOptions()
                .withPoints(points)
                .withLineColor("#D4AF37") // Gold primary luxury route
                .withLineWidth(6.0)
                .withLineOpacity(0.9)
            lManager.create(polylineOptions)
        }
    }

    // Camera Recenter / Animation Handling
    LaunchedEffect(cameraAnimTrigger, cameraTargetLatLng, recenterTrigger) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        val target = cameraTargetLatLng ?: pickupLatLng
        val targetPoint = Point.fromLngLat(target.longitude, target.latitude)

        val cameraOptions = CameraOptions.Builder()
            .center(targetPoint)
            .zoom(zoomLevel.toDouble())
            .build()

        val animOptions = MapAnimationOptions.mapAnimationOptions {
            duration(1200)
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
