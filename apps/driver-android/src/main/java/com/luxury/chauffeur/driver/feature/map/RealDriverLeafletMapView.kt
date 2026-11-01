package com.luxury.chauffeur.driver.feature.map

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject

interface DriverMapBridgeListener {
    fun onMapClick(lat: Double, lng: Double)
    fun onCameraMoved(lat: Double, lng: Double)
}

class AndroidDriverMapBridge(private val listener: DriverMapBridgeListener) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onMapClick(lat: Double, lng: Double) {
        mainHandler.post { listener.onMapClick(lat, lng) }
    }

    @JavascriptInterface
    fun onCameraMoved(lat: Double, lng: Double) {
        mainHandler.post { listener.onCameraMoved(lat, lng) }
    }
}

/**
 * Enterprise Driver Automotive Cockpit Real Map powered by Leaflet,
 * CartoDB Dark Matter, and High-Resolution Satellite imagery.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealDriverLeafletMapView(
    modifier: Modifier = Modifier,
    driverLatLng: LatLng,
    bearing: Float = 0f,
    currentSpeedKmh: Int = 0,
    pickupLatLng: LatLng? = null,
    pickupTitle: String = "Pickup Point",
    destLatLng: LatLng? = null,
    destTitle: String? = null,
    routePoints: List<LatLng> = emptyList(),
    mapLayer: String = "DARK", // "DARK", "SATELLITE", "STREETS"
    is3DMode: Boolean = true,
    recenterTrigger: Int = 0,
    zoomLevel: Float = 17f,
    onMapClick: ((LatLng) -> Unit)? = null
) {
    val bridgeListener = remember(onMapClick) {
        object : DriverMapBridgeListener {
            override fun onMapClick(lat: Double, lng: Double) {
                onMapClick?.invoke(LatLng(lat, lng))
            }
            override fun onCameraMoved(lat: Double, lng: Double) {}
        }
    }

    var webViewRef: WebView? = remember { null }

    fun buildUpdateJson(): String {
        val root = JSONObject()

        // Driver E-Rickshaw
        val drvObj = JSONObject().apply {
            put("lat", driverLatLng.latitude)
            put("lng", driverLatLng.longitude)
            put("bearing", bearing)
            put("speed", currentSpeedKmh)
        }
        root.put("driver", drvObj)

        // Pickup
        if (pickupLatLng != null) {
            val pObj = JSONObject().apply {
                put("lat", pickupLatLng.latitude)
                put("lng", pickupLatLng.longitude)
                put("title", pickupTitle)
            }
            root.put("pickup", pObj)
        }

        // Destination
        if (destLatLng != null) {
            val dObj = JSONObject().apply {
                put("lat", destLatLng.latitude)
                put("lng", destLatLng.longitude)
                put("title", destTitle ?: "Destination")
            }
            root.put("destination", dObj)
        }

        // Route Points
        val rArr = JSONArray()
        val origin = pickupLatLng ?: driverLatLng
        val pts = if (routePoints.isNotEmpty()) routePoints else if (destLatLng != null) listOf(origin, destLatLng) else emptyList()
        pts.forEach { pt ->
            val ptObj = JSONObject().apply {
                put("lat", pt.latitude)
                put("lng", pt.longitude)
            }
            rArr.put(ptObj)
        }
        root.put("routePoints", rArr)
        root.put("is3D", is3DMode)

        return root.toString()
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewRef = this
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    databaseEnabled = true
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val updateJson = buildUpdateJson()
                        val initJs = "if(!window.map) { initMap(${driverLatLng.latitude}, ${driverLatLng.longitude}, $zoomLevel, '$mapLayer'); } window.updateMap($updateJson);"
                        evaluateJavascript(initJs, null)
                    }
                }
                addJavascriptInterface(AndroidDriverMapBridge(bridgeListener), "AndroidBridge")
                loadUrl("file:///android_asset/leaflet/map.html")
            }
        },
        update = { webView ->
            webViewRef = webView
            val updateJson = buildUpdateJson()
            val js = "if(window.map && window.updateMap) { window.updateMap($updateJson); } else if(!window.map && window.initMap) { initMap(${driverLatLng.latitude}, ${driverLatLng.longitude}, $zoomLevel, '$mapLayer'); window.updateMap($updateJson); }"
            webView.evaluateJavascript(js, null)
        },
        modifier = modifier.fillMaxSize()
    )

    // Layer switch
    LaunchedEffect(mapLayer) {
        webViewRef?.evaluateJavascript("if(window.setMapLayer) { window.setMapLayer('$mapLayer'); }", null)
    }

    // Recenter camera
    LaunchedEffect(recenterTrigger, driverLatLng) {
        webViewRef?.evaluateJavascript("if(window.recenterCamera) { window.recenterCamera(${driverLatLng.latitude}, ${driverLatLng.longitude}, $zoomLevel); }", null)
    }
}

fun getDriverMapHtml(initLat: Double, initLng: Double, zoom: Float, layer: String): String {
    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <style>
        html, body, #map {
            width: 100%;
            height: 100%;
            margin: 0;
            padding: 0;
            background: #0f172a;
            overflow: hidden;
            -webkit-tap-highlight-color: transparent;
        }
        .leaflet-control-attribution { display: none !important; }

        /* Driver 3D Cockpit Vehicle Marker */
        .driver-vehicle-cockpit {
            position: relative;
            width: 48px;
            height: 48px;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: transform 0.25s linear;
        }
        .headlight-cone {
            position: absolute;
            top: -24px;
            left: 50%;
            transform: translateX(-50%);
            width: 0;
            height: 0;
            border-left: 18px solid transparent;
            border-right: 18px solid transparent;
            border-bottom: 28px solid rgba(16, 185, 129, 0.45);
            pointer-events: none;
            filter: blur(1px);
        }
        .vehicle-body {
            position: relative;
            width: 36px;
            height: 36px;
            background: #0f172a;
            border: 2.5px solid #eab308;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 0 16px rgba(234, 179, 8, 0.75);
            font-size: 20px;
        }
        .marker-badge {
            position: absolute;
            bottom: 34px;
            left: 50%;
            transform: translateX(-50%);
            background: #0f172a;
            color: #ffffff;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            font-size: 11px;
            font-weight: 700;
            padding: 3px 8px;
            border-radius: 10px;
            border: 1px solid #10b981;
            white-space: nowrap;
            box-shadow: 0 4px 10px rgba(0,0,0,0.5);
            pointer-events: none;
        }
    </style>
</head>
<body>
    <div id="map"></div>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <script>
        var map;
        var currentLayer;
        var driverMarker = null;
        var pickupMarker = null;
        var destMarker = null;
        var routePolyline = null;
        var routePolylineBorder = null;

        var layers = {
            'DARK': L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', { maxZoom: 19 }),
            'SATELLITE': L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', { maxZoom: 19 }),
            'STREETS': L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', { maxZoom: 19 })
        };

        function init() {
            map = L.map('map', {
                center: [$initLat, $initLng],
                zoom: $zoom,
                zoomControl: false,
                attributionControl: false
            });

            setMapLayer('$layer');

            map.on('click', function(e) {
                if (window.AndroidBridge && window.AndroidBridge.onMapClick) {
                    window.AndroidBridge.onMapClick(e.latlng.lat, e.latlng.lng);
                }
            });
        }

        function setMapLayer(type) {
            if (currentLayer) map.removeLayer(currentLayer);
            currentLayer = layers[type] || layers['DARK'];
            currentLayer.addTo(map);
        }

        function recenterCamera(lat, lng, zoom) {
            if (map) {
                map.flyTo([lat, lng], zoom || 17, { duration: 0.6 });
            }
        }

        function updateMap(dataJson) {
            if (!map) return;
            var data = typeof dataJson === 'string' ? JSON.parse(dataJson) : dataJson;

            // 1. Driver E-Rickshaw with Headlight Cone
            if (data.driver) {
                var bearing = data.driver.bearing || 0;
                var drvHtml = '<div class="driver-vehicle-cockpit" style="transform: rotate(' + bearing + 'deg);">' +
                    '<div class="headlight-cone"></div>' +
                    '<div class="vehicle-body">🛺</div>' +
                    '</div>';
                var drvIcon = L.divIcon({ className: '', html: drvHtml, iconSize: [48, 48], iconAnchor: [24, 24] });
                if (!driverMarker) {
                    driverMarker = L.marker([data.driver.lat, data.driver.lng], { icon: drvIcon }).addTo(map);
                } else {
                    driverMarker.setLatLng([data.driver.lat, data.driver.lng]);
                    driverMarker.setIcon(drvIcon);
                }
            }

            // 2. Pickup Marker
            if (data.pickup) {
                var pHtml = '<div style="position:relative;">' +
                    '<div style="font-size:24px; transform:translate(-6px,-20px);">📍</div>' +
                    '<div class="marker-badge" style="border-color:#10b981;">' + (data.pickup.title || 'PICKUP') + '</div>' +
                    '</div>';
                var pIcon = L.divIcon({ className: '', html: pHtml, iconSize: [28, 28], iconAnchor: [14, 28] });
                if (!pickupMarker) {
                    pickupMarker = L.marker([data.pickup.lat, data.pickup.lng], { icon: pIcon }).addTo(map);
                } else {
                    pickupMarker.setLatLng([data.pickup.lat, data.pickup.lng]);
                    pickupMarker.setIcon(pIcon);
                }
            } else if (pickupMarker) {
                map.removeLayer(pickupMarker);
                pickupMarker = null;
            }

            // 3. Destination Marker
            if (data.destination) {
                var dHtml = '<div style="position:relative;">' +
                    '<div style="font-size:26px; transform:translate(-8px,-24px);">🏁</div>' +
                    '<div class="marker-badge" style="border-color:#ef4444; background:#991b1b;">' + (data.destination.title || 'DESTINATION') + '</div>' +
                    '</div>';
                var dIcon = L.divIcon({ className: '', html: dHtml, iconSize: [30, 30], iconAnchor: [15, 30] });
                if (!destMarker) {
                    destMarker = L.marker([data.destination.lat, data.destination.lng], { icon: dIcon }).addTo(map);
                } else {
                    destMarker.setLatLng([data.destination.lat, data.destination.lng]);
                    destMarker.setIcon(dIcon);
                }
            } else if (destMarker) {
                map.removeLayer(destMarker);
                destMarker = null;
            }

            // 4. Navigation Shortest Route
            if (data.routePoints && data.routePoints.length >= 2) {
                var latlngs = data.routePoints.map(function(pt) { return [pt.lat, pt.lng]; });
                if (routePolyline) map.removeLayer(routePolyline);
                if (routePolylineBorder) map.removeLayer(routePolylineBorder);

                routePolylineBorder = L.polyline(latlngs, {
                    color: '#0f172a',
                    weight: 10,
                    opacity: 0.95,
                    lineJoin: 'round',
                    lineCap: 'round'
                }).addTo(map);

                routePolyline = L.polyline(latlngs, {
                    color: '#10b981',
                    weight: 6,
                    opacity: 1.0,
                    lineJoin: 'round',
                    lineCap: 'round'
                }).addTo(map);

                if (data.destination && !data.is3D) {
                    map.fitBounds(routePolyline.getBounds(), { padding: [50, 50] });
                }
            } else {
                if (routePolyline) { map.removeLayer(routePolyline); routePolyline = null; }
                if (routePolylineBorder) { map.removeLayer(routePolylineBorder); routePolylineBorder = null; }
            }
        }

        window.onload = init;
    </script>
</body>
</html>
""".trimIndent()
}

