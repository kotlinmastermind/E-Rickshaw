package com.luxury.chauffeur.driver.feature.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.*

object NavigationHelper {
    fun launchNavigation(context: Context, provider: String, destLat: Double, destLng: Double, destinationLabel: String = "Destination") {
        when (provider.uppercase()) {
            "WAZE" -> {
                val uri = Uri.parse("https://waze.com/ul?ll=$destLat,$destLng&navigate=yes")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
            "SYSTEM" -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$destLat,$destLng?q=$destLat,$destLng(${Uri.encode(destinationLabel)})")))
            else -> launchGoogleMapsNavigation(context, destLat, destLng, destinationLabel)
        }
    }

    fun launchGoogleMapsNavigation(context: Context, destLat: Double, destLng: Double, destinationLabel: String = "Destination") {
        val navUri = Uri.parse("google.navigation:q=$destLat,$destLng&mode=d")
        val mapIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            val fallbackUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destLat,$destLng&travelmode=driving")
            val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)
            context.startActivity(fallbackIntent)
        }
    }
}

@Composable
fun rememberDriverMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
            onStart()
            onResume()
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }
    return mapView
}

@SuppressLint("MissingPermission")
@Composable
fun DriverMapView(
    modifier: Modifier = Modifier,
    driverLatLng: LatLng = LatLng(25.6858, 85.2146), // Hajipur/Patna region
    pickupLatLng: LatLng? = null,
    pickupTitle: String = "Pickup Location",
    destLatLng: LatLng? = null,
    destTitle: String? = null,
    recenterTrigger: Int = 0,
    zoomLevel: Float = 16f
) {
    val context = LocalContext.current
    val mapView = rememberDriverMapViewWithLifecycle()
    var googleMapInstance by remember { mutableStateOf<GoogleMap?>(null) }

    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            googleMapInstance = map
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            map.isBuildingsEnabled = true
            map.isTrafficEnabled = true
            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
            map.uiSettings.isMapToolbarEnabled = true
            if (hasLocationPermission) {
                try {
                    map.isMyLocationEnabled = true
                } catch (_: SecurityException) {}
            }
        }
    }

    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            googleMapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(driverLatLng, 17f))
        }
    }

    LaunchedEffect(googleMapInstance, driverLatLng, pickupLatLng, destLatLng) {
        val map = googleMapInstance ?: return@LaunchedEffect
        map.clear()

        // 1. Driver's own E-Rickshaw marker
        map.addMarker(
            MarkerOptions()
                .position(driverLatLng)
                .title("My E-Rickshaw")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )

        // 2. Pickup Location marker if active
        if (pickupLatLng != null) {
            map.addMarker(
                MarkerOptions()
                    .position(pickupLatLng)
                    .title(pickupTitle)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
        }

        // 3. Destination marker & Route Polyline
        if (destLatLng != null) {
            map.addMarker(
                MarkerOptions()
                    .position(destLatLng)
                    .title(destTitle ?: "Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )

            val origin = pickupLatLng ?: driverLatLng
            map.addPolyline(
                PolylineOptions()
                    .add(origin, destLatLng)
                    .color(android.graphics.Color.parseColor("#1E2022"))
                    .width(10f)
                    .geodesic(true)
            )

            val bounds = LatLngBounds.builder()
                .include(driverLatLng)
                .include(destLatLng)
            if (pickupLatLng != null) bounds.include(pickupLatLng)

            try {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120))
            } catch (_: Exception) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(driverLatLng, zoomLevel))
            }
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(driverLatLng, zoomLevel))
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}
