package com.luxury.chauffeur.customer.feature.map

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.model.LatLng
import com.luxury.chauffeur.customer.core.network.NearbyVehicleDto
import org.json.JSONArray
import org.json.JSONObject

interface RealMapBridgeListener {
    fun onMapClick(lat: Double, lng: Double)
    fun onCameraMoved(lat: Double, lng: Double)
    fun onVehicleClick(vehicleId: String)
}

class AndroidMapBridge(private val listener: RealMapBridgeListener) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onMapClick(lat: Double, lng: Double) {
        mainHandler.post { listener.onMapClick(lat, lng) }
    }

    @JavascriptInterface
    fun onCameraMoved(lat: Double, lng: Double) {
        mainHandler.post { listener.onCameraMoved(lat, lng) }
    }

    @JavascriptInterface
    fun onVehicleClick(vehicleId: String) {
        mainHandler.post { listener.onVehicleClick(vehicleId) }
    }
}

/**
 * Enterprise Real-World Live Interactive Map powered by OpenStreetMap,
 * CartoDB Voyager/Dark Matter, and ESRI High-Resolution Satellite imagery.
 * Works seamlessly worldwide without Google Cloud billing barriers or DEVELOPER_ERROR.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealWorldLeafletMapView(
    modifier: Modifier = Modifier,
    pickupLatLng: LatLng,
    pickupTitle: String = "Pickup Point",
    destLatLng: LatLng? = null,
    destTitle: String? = null,
    driverLatLng: LatLng? = null,
    nearbyVehicles: List<NearbyVehicleDto> = emptyList(),
    routePoints: List<LatLng> = emptyList(),
    mapLayer: String = "STREETS", // "STREETS", "DARK", "SATELLITE", "OSM"
    recenterTrigger: Int = 0,
    zoomLevel: Float = 16f,
    onMapClick: ((LatLng) -> Unit)? = null,
    onCameraMoved: ((LatLng) -> Unit)? = null,
    onVehicleClick: ((NearbyVehicleDto) -> Unit)? = null
) {
    val bridgeListener = remember(onMapClick, onCameraMoved, onVehicleClick, nearbyVehicles) {
        object : RealMapBridgeListener {
            override fun onMapClick(lat: Double, lng: Double) {
                onMapClick?.invoke(LatLng(lat, lng))
            }
            override fun onCameraMoved(lat: Double, lng: Double) {
                onCameraMoved?.invoke(LatLng(lat, lng))
            }
            override fun onVehicleClick(vehicleId: String) {
                val veh = nearbyVehicles.find { it.id == vehicleId }
                if (veh != null) onVehicleClick?.invoke(veh)
            }
        }
    }

    var webViewRef: WebView? = remember { null }

    fun buildUpdateJson(): String {
        val root = JSONObject()

        // Pickup
        val pObj = JSONObject().apply {
            put("lat", pickupLatLng.latitude)
            put("lng", pickupLatLng.longitude)
            put("title", pickupTitle)
        }
        root.put("pickup", pObj)

        // Destination
        if (destLatLng != null) {
            val dObj = JSONObject().apply {
                put("lat", destLatLng.latitude)
                put("lng", destLatLng.longitude)
                put("title", destTitle ?: "Destination")
            }
            root.put("destination", dObj)
        }

        // Driver
        if (driverLatLng != null) {
            val drvObj = JSONObject().apply {
                put("lat", driverLatLng.latitude)
                put("lng", driverLatLng.longitude)
                put("bearing", 0.0)
            }
            root.put("driver", drvObj)
        }

        // Nearby Vehicles
        val vArr = JSONArray()
        nearbyVehicles.forEach { veh ->
            val vObj = JSONObject().apply {
                put("id", veh.id)
                put("lat", veh.latitude)
                put("lng", veh.longitude)
                put("bearing", veh.bearing)
                put("icon", veh.iconType)
                put("name", veh.vehicleTypeName)
            }
            vArr.put(vObj)
        }
        root.put("vehicles", vArr)

        // Route Points
        val rArr = JSONArray()
        val pts = if (routePoints.isNotEmpty()) routePoints else if (destLatLng != null) listOf(pickupLatLng, destLatLng) else emptyList()
        pts.forEach { pt ->
            val ptObj = JSONObject().apply {
                put("lat", pt.latitude)
                put("lng", pt.longitude)
            }
            rArr.put(ptObj)
        }
        root.put("routePoints", rArr)
        root.put("fitBounds", destLatLng != null)

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
                        evaluateJavascript("if(window.updateMap) { window.updateMap($updateJson); }", null)
                    }
                }
                addJavascriptInterface(AndroidMapBridge(bridgeListener), "AndroidBridge")
                loadDataWithBaseURL("https://app.luxurychauffeur.local/", getRealMapHtml(pickupLatLng.latitude, pickupLatLng.longitude, zoomLevel, mapLayer), "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webViewRef = webView
            val updateJson = buildUpdateJson()
            webView.evaluateJavascript("if(window.updateMap) { window.updateMap($updateJson); }", null)
        },
        modifier = modifier.fillMaxSize()
    )

    // Layer switch
    LaunchedEffect(mapLayer) {
        webViewRef?.evaluateJavascript("if(window.setMapLayer) { window.setMapLayer('$mapLayer'); }", null)
    }

    // Recenter camera
    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            webViewRef?.evaluateJavascript("if(window.recenterCamera) { window.recenterCamera(${pickupLatLng.latitude}, ${pickupLatLng.longitude}, $zoomLevel); }", null)
        }
    }
}

fun getRealMapHtml(initLat: Double, initLng: Double, zoom: Float, layer: String): String {
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
            background: #0b0b0d;
            overflow: hidden;
            -webkit-tap-highlight-color: transparent;
        }
        .leaflet-control-attribution { display: none !important; }
        
        /* Modern High-End Markers */
        .pickup-pulse-wrapper {
            position: relative;
            width: 28px;
            height: 28px;
        }
        .pulse-ring-anim {
            position: absolute;
            width: 44px;
            height: 44px;
            left: -8px;
            top: -8px;
            border-radius: 50%;
            background: rgba(16, 185, 129, 0.45);
            animation: ring-pulse 1.8s infinite ease-out;
        }
        @keyframes ring-pulse {
            0% { transform: scale(0.5); opacity: 1; }
            100% { transform: scale(1.6); opacity: 0; }
        }
        .pickup-core-dot {
            position: absolute;
            width: 20px;
            height: 20px;
            left: 4px;
            top: 4px;
            background: #10b981;
            border: 3px solid #ffffff;
            border-radius: 50%;
            box-shadow: 0 0 12px rgba(16, 185, 129, 0.9);
        }
        .marker-badge {
            position: absolute;
            bottom: 30px;
            left: 50%;
            transform: translateX(-50%);
            background: #0f172a;
            color: #ffffff;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            font-size: 11px;
            font-weight: 700;
            letter-spacing: 0.02em;
            padding: 4px 10px;
            border-radius: 12px;
            border: 1.5px solid #10b981;
            white-space: nowrap;
            box-shadow: 0 4px 12px rgba(0,0,0,0.5);
            pointer-events: none;
        }
        .dest-badge {
            background: #991b1b;
            border-color: #ef4444;
        }
        .vehicle-bubble {
            background: #0f172a;
            border: 2px solid #eab308;
            border-radius: 50%;
            width: 32px;
            height: 32px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.6);
            cursor: pointer;
            transition: transform 0.2s ease;
        }
    </style>
</head>
<body>
    <div id="map"></div>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <script>
        var map;
        var currentLayer;
        var pickupMarker = null;
        var destMarker = null;
        var driverMarker = null;
        var vehicleMarkers = [];
        var routePolyline = null;
        var routePolylineBorder = null;

        var layers = {
            'STREETS': L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', { maxZoom: 19 }),
            'DARK': L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', { maxZoom: 19 }),
            'OSM': L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }),
            'SATELLITE': L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', { maxZoom: 19 })
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

            map.on('moveend', function() {
                var c = map.getCenter();
                if (window.AndroidBridge && window.AndroidBridge.onCameraMoved) {
                    window.AndroidBridge.onCameraMoved(c.lat, c.lng);
                }
            });
        }

        function setMapLayer(type) {
            if (currentLayer) map.removeLayer(currentLayer);
            currentLayer = layers[type] || layers['STREETS'];
            currentLayer.addTo(map);
        }

        function recenterCamera(lat, lng, zoom) {
            if (map) {
                map.flyTo([lat, lng], zoom || 16, { duration: 0.8 });
            }
        }

        function updateMap(dataJson) {
            if (!map) return;
            var data = typeof dataJson === 'string' ? JSON.parse(dataJson) : dataJson;

            // 1. Pickup Marker
            if (data.pickup) {
                var pickupHtml = '<div class="pickup-pulse-wrapper">' +
                    '<div class="pulse-ring-anim"></div>' +
                    '<div class="pickup-core-dot"></div>' +
                    '<div class="marker-badge">' + (data.pickup.title || 'PICKUP POINT') + '</div>' +
                    '</div>';
                var pIcon = L.divIcon({ className: '', html: pickupHtml, iconSize: [28, 28], iconAnchor: [14, 14] });
                if (!pickupMarker) {
                    pickupMarker = L.marker([data.pickup.lat, data.pickup.lng], { icon: pIcon }).addTo(map);
                } else {
                    pickupMarker.setLatLng([data.pickup.lat, data.pickup.lng]);
                    pickupMarker.setIcon(pIcon);
                }
            }

            // 2. Destination Marker
            if (data.destination) {
                var destHtml = '<div style="position:relative;">' +
                    '<div style="font-size:26px; transform:translate(-8px,-24px);">🏁</div>' +
                    '<div class="marker-badge dest-badge">' + (data.destination.title || 'DESTINATION') + '</div>' +
                    '</div>';
                var dIcon = L.divIcon({ className: '', html: destHtml, iconSize: [30, 30], iconAnchor: [15, 30] });
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

            // 3. Driver Marker
            if (data.driver) {
                var dHtml = '<div class="vehicle-bubble" style="border-color:#10b981;">🛺</div>';
                var drvIcon = L.divIcon({ className: '', html: dHtml, iconSize: [32, 32], iconAnchor: [16, 16] });
                if (!driverMarker) {
                    driverMarker = L.marker([data.driver.lat, data.driver.lng], { icon: drvIcon }).addTo(map);
                } else {
                    driverMarker.setLatLng([data.driver.lat, data.driver.lng]);
                    driverMarker.setIcon(drvIcon);
                }
            } else if (driverMarker) {
                map.removeLayer(driverMarker);
                driverMarker = null;
            }

            // 4. Nearby Vehicles
            vehicleMarkers.forEach(function(m) { map.removeLayer(m); });
            vehicleMarkers = [];
            if (data.vehicles && data.vehicles.length) {
                data.vehicles.forEach(function(v) {
                    var vHtml = '<div class="vehicle-bubble" style="transform:rotate(' + (v.bearing||0) + 'deg);">🛺</div>';
                    var vIcon = L.divIcon({ className: '', html: vHtml, iconSize: [32, 32], iconAnchor: [16, 16] });
                    var vm = L.marker([v.lat, v.lng], { icon: vIcon }).addTo(map);
                    vm.on('click', function() {
                        if (window.AndroidBridge && window.AndroidBridge.onVehicleClick) {
                            window.AndroidBridge.onVehicleClick(v.id || '');
                        }
                    });
                    vehicleMarkers.push(vm);
                });
            }

            // 5. Shortest Route Polyline
            if (data.routePoints && data.routePoints.length >= 2) {
                var latlngs = data.routePoints.map(function(pt) { return [pt.lat, pt.lng]; });
                if (routePolyline) map.removeLayer(routePolyline);
                if (routePolylineBorder) map.removeLayer(routePolylineBorder);

                routePolylineBorder = L.polyline(latlngs, {
                    color: '#0f172a',
                    weight: 8,
                    opacity: 0.9,
                    lineJoin: 'round',
                    lineCap: 'round'
                }).addTo(map);

                routePolyline = L.polyline(latlngs, {
                    color: '#10b981',
                    weight: 5,
                    opacity: 1.0,
                    lineJoin: 'round',
                    lineCap: 'round'
                }).addTo(map);

                if (data.fitBounds !== false) {
                    map.fitBounds(routePolyline.getBounds(), { padding: [60, 60] });
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
