package com.luxury.chauffeur.driver.feature.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.speech.tts.TextToSpeech
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
import com.luxury.chauffeur.core.map.LatLng
import com.luxury.chauffeur.core.map.AppMapDetailsCatalog
import com.luxury.chauffeur.core.map.MapboxSharedConfig
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
 * smooth PointAnnotation driver tracking, and spoken voice guidance via TextToSpeech.
 */
@Composable
fun MapboxDriverMapView(
    modifier: Modifier = Modifier,
    driverLatLng: LatLng,
    bearing: Float = 0f,
    currentSpeedKmh: Int = 0,
    pickupLatLng: LatLng? = null,
    pickupTitle: String = "Pickup Point",
    destLatLng: LatLng? = null,
    destTitle: String? = null,
    routePoints: List<LatLng> = emptyList(),
    currentManeuverInstruction: String? = null,
    isVoiceMuted: Boolean = false,
    mapLayer: String = "LIGHT", // "LIGHT", "DARK", "SATELLITE", "TERRAIN"
    mapDetails: Map<String, Boolean> = AppMapDetailsCatalog.getDefaultMap(),
    is3DMode: Boolean = true,
    recenterTrigger: Int = 0,
    cameraTargetLatLng: LatLng? = null,
    cameraAnimTrigger: Int = 0,
    cameraAnimType: String = "flyTo", // "flyTo" or "easeTo"
    zoomLevel: Float = 17f,
    mapboxToken: String = MapboxSharedConfig.DEFAULT_PUBLIC_TOKEN,
    onMapClick: ((LatLng) -> Unit)? = null
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

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val resolvedToken = mapboxToken.ifBlank {
                try {
                    ctx.getString(com.luxury.chauffeur.driver.R.string.mapbox_access_token)
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

                    // Setup initial 3D Cockpit Camera
                    val initialPitch = if (is3DMode) 55.0 else 0.0
                    mapboxMap.setCamera(
                        CameraOptions.Builder()
                            .center(Point.fromLngLat(driverLatLng.longitude, driverLatLng.latitude))
                            .bearing(bearing.toDouble())
                            .pitch(initialPitch)
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

    // Update Annotations (Driver position, Pickup, Destination & Polyline)
    LaunchedEffect(
        pointAnnotationManager,
        polylineAnnotationManager,
        driverLatLng,
        bearing,
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
            .withPoint(Point.fromLngLat(driverLatLng.longitude, driverLatLng.latitude))
            .withIconImage(driverCarBitmap)
            .withIconRotate(bearing.toDouble())
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

    // Camera follow driver in 3D navigation mode
    LaunchedEffect(driverLatLng, bearing, is3DMode) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        val targetPoint = Point.fromLngLat(driverLatLng.longitude, driverLatLng.latitude)
        val targetPitch = if (is3DMode) 55.0 else 0.0

        val cameraOptions = CameraOptions.Builder()
            .center(targetPoint)
            .bearing(bearing.toDouble())
            .pitch(targetPitch)
            .zoom(zoomLevel.toDouble())
            .build()

        val animOptions = MapAnimationOptions.mapAnimationOptions {
            duration(500)
        }
        mapView.camera.easeTo(cameraOptions, animOptions)
    }

    // Manual Camera Recenter / Animation trigger
    LaunchedEffect(cameraAnimTrigger, cameraTargetLatLng, recenterTrigger) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        val target = cameraTargetLatLng ?: driverLatLng
        val targetPoint = Point.fromLngLat(target.longitude, target.latitude)
        val targetPitch = if (is3DMode) 55.0 else 0.0

        val cameraOptions = CameraOptions.Builder()
            .center(targetPoint)
            .bearing(bearing.toDouble())
            .pitch(targetPitch)
            .zoom(zoomLevel.toDouble())
            .build()

        val animOptions = MapAnimationOptions.mapAnimationOptions {
            duration(1000)
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
