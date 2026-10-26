package com.luxury.chauffeur.driver.feature.map

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.*

object NavigationHelper {
    fun launchGoogleMapsNavigation(context: Context, destLat: Double, destLng: Double, destinationLabel: String = "Destination") {
        // Native Google Maps Turn-by-Turn Navigation Intent
        val navUri = Uri.parse("google.navigation:q=$destLat,$destLng&mode=d")
        val mapIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            // Fallback to Google Maps directions web / app link
            val fallbackUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destLat,$destLng&travelmode=driving")
            val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)
            context.startActivity(fallbackIntent)
        }
    }
}

@Composable
fun rememberDriverMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

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
        }
    }
    return mapView
}

@Composable
fun DriverMapView(
    modifier: Modifier = Modifier,
    pickupLatLng: LatLng = LatLng(18.9217, 72.8332),
    pickupTitle: String = "Pickup Location",
    destLatLng: LatLng? = null,
    destTitle: String? = null,
    zoomLevel: Float = 14f
) {
    val mapView = rememberDriverMapViewWithLifecycle()
    var googleMapInstance by remember { mutableStateOf<GoogleMap?>(null) }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            googleMapInstance = map
            try {
                // Sleek Cockpit Dark Theme vector map style
                val darkStyleJson = """
                    [
                      {"elementType": "geometry", "stylers": [{"color": "#18181b"}]},
                      {"elementType": "labels.text.stroke", "stylers": [{"color": "#18181b"}]},
                      {"elementType": "labels.text.fill", "stylers": [{"color": "#a1a1aa"}]},
                      {"featureType": "administrative.locality", "elementType": "labels.text.fill", "stylers": [{"color": "#d4af37"}]},
                      {"featureType": "poi", "elementType": "labels.text.fill", "stylers": [{"color": "#d4af37"}]},
                      {"featureType": "road", "elementType": "geometry", "stylers": [{"color": "#27272a"}]},
                      {"featureType": "road.highway", "elementType": "geometry", "stylers": [{"color": "#3f3f46"}]},
                      {"featureType": "water", "elementType": "geometry", "stylers": [{"color": "#09090b"}]}
                    ]
                """.trimIndent()
                map.setMapStyle(MapStyleOptions(darkStyleJson))
            } catch (_: Exception) {}

            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
        }
    }

    LaunchedEffect(googleMapInstance, pickupLatLng, destLatLng) {
        val map = googleMapInstance ?: return@LaunchedEffect
        map.clear()

        map.addMarker(
            MarkerOptions()
                .position(pickupLatLng)
                .title(pickupTitle)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )

        if (destLatLng != null) {
            map.addMarker(
                MarkerOptions()
                    .position(destLatLng)
                    .title(destTitle ?: "Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )

            map.addPolyline(
                PolylineOptions()
                    .add(pickupLatLng, destLatLng)
                    .color(android.graphics.Color.parseColor("#D4AF37"))
                    .width(8f)
                    .geodesic(true)
            )

            val bounds = LatLngBounds.builder()
                .include(pickupLatLng)
                .include(destLatLng)
                .build()
            try {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
            } catch (_: Exception) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, zoomLevel))
            }
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, zoomLevel))
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}
