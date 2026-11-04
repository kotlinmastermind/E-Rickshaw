import React, { useEffect, useRef, useState, useCallback } from 'react';
import mapboxgl from 'mapbox-gl';
import 'mapbox-gl/dist/mapbox-gl.css';
import { ZoomIn, ZoomOut, Maximize2, RefreshCw, Search } from 'lucide-react';

export interface AdminRealMapProps {
  mode?: 'live_fleet' | 'geofence_editor' | 'road_restrictions' | 'ride_route' | 'sos_incident' | 'mini';
  height?: string | number;
  selectedScreenTitle?: string;
  onPolygonCreated?: (coords: [number, number][], zoneType: string) => void;
  className?: string;
}

interface FleetVehicle {
  id: string;
  plate: string;
  driver: string;
  model: string;
  lat: number;
  lng: number;
  battery: number;
  speed: number;
  status: 'available' | 'on_trip' | 'en_route';
  passenger?: string;
  fare?: number;
}

export const SAMPLE_VEHICLES: FleetVehicle[] = [];

// Mapbox GeoJSON Geofence Zones (coordinates in [lng, lat])
const GEOFENCE_ZONES_MAPBOX = [
  {
    name: 'Connaught Place Commercial Core (High Surge)',
    type: 'Surge Zone (+1.5x)',
    color: '#d4af37',
    coords: [
      [77.2150, 28.6360],
      [77.2240, 28.6360],
      [77.2240, 28.6280],
      [77.2150, 28.6280],
      [77.2150, 28.6360]
    ]
  },
  {
    name: 'NDLS Railway Station Transit Hub',
    type: 'High Demand (+2.0x)',
    color: '#3b82f6',
    coords: [
      [77.2170, 28.6450],
      [77.2260, 28.6450],
      [77.2260, 28.6390],
      [77.2170, 28.6390],
      [77.2170, 28.6450]
    ]
  },
  {
    name: 'Old Delhi Heritage Zone (Geo-Restricted)',
    type: 'Restricted Slow Speed',
    color: '#ef4444',
    coords: [
      [77.2250, 28.6560],
      [77.2380, 28.6560],
      [77.2380, 28.6460],
      [77.2250, 28.6460],
      [77.2250, 28.6560]
    ]
  }
];

// Restricted Corridors ([lng, lat])
const RESTRICTED_ROADS_MAPBOX = [
  {
    name: 'Barakhamba Flyover (E-Rickshaw High-Speed Restriction)',
    type: 'Motorized Expressway',
    coords: [
      [77.2240, 28.6300],
      [77.2265, 28.6260]
    ]
  }
];

// Landmarks ([lng, lat] for Mapbox)
const DELHI_LANDMARKS_MAPBOX = [
  { label: 'Connaught Place', coords: [77.2195, 28.6328] as [number, number] },
  { label: 'NDLS Railway', coords: [77.2210, 28.6425] as [number, number] },
  { label: 'Chandni Chowk', coords: [77.2300, 28.6505] as [number, number] },
  { label: 'India Gate', coords: [77.2295, 28.6129] as [number, number] },
  { label: 'Khan Market', coords: [77.2270, 28.6000] as [number, number] },
  { label: 'Karol Bagh', coords: [77.1905, 28.6515] as [number, number] }
];

const MAPBOX_PUBLIC_KEY =
  (import.meta as any).env?.VITE_MAPBOX_PUBLIC_TOKEN || '';

const isValidMapboxToken =
  typeof MAPBOX_PUBLIC_KEY === 'string' &&
  MAPBOX_PUBLIC_KEY.startsWith('pk.') &&
  !MAPBOX_PUBLIC_KEY.includes('MapboxPublicDemo') &&
  !MAPBOX_PUBLIC_KEY.includes('example_public_token');

const MAPBOX_STYLES: Record<string, string> = {
  dark: 'mapbox://styles/mapbox/dark-v11',
  streets: 'mapbox://styles/mapbox/streets-v12',
  satellite: 'mapbox://styles/mapbox/satellite-streets-v12'
};

const getMapboxStyle = (styleKey: 'dark' | 'streets' | 'satellite'): any => {
  if (isValidMapboxToken) {
    return MAPBOX_STYLES[styleKey];
  }
  if (styleKey === 'streets') {
    return {
      version: 8,
      sources: {
        'carto-voyager': {
          type: 'raster',
          tiles: [
            'https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}@2x.png',
            'https://b.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}@2x.png'
          ],
          tileSize: 256
        }
      },
      layers: [{ id: 'carto-voyager-layer', type: 'raster', source: 'carto-voyager', minzoom: 0, maxzoom: 22 }]
    };
  } else if (styleKey === 'satellite') {
    return {
      version: 8,
      sources: {
        'esri-satellite': {
          type: 'raster',
          tiles: ['https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'],
          tileSize: 256
        }
      },
      layers: [{ id: 'esri-satellite-layer', type: 'raster', source: 'esri-satellite', minzoom: 0, maxzoom: 22 }]
    };
  } else {
    return {
      version: 8,
      sources: {
        'carto-dark': {
          type: 'raster',
          tiles: [
            'https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png',
            'https://b.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png'
          ],
          tileSize: 256
        }
      },
      layers: [{ id: 'carto-dark-layer', type: 'raster', source: 'carto-dark', minzoom: 0, maxzoom: 22 }]
    };
  }
};

export const AdminRealMap: React.FC<AdminRealMapProps> = ({
  mode = 'live_fleet',
  height = '480px',
  selectedScreenTitle = 'Live Fleet Map',
  onPolygonCreated,
  className = ''
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<mapboxgl.Map | null>(null);
  const markersRef = useRef<mapboxgl.Marker[]>([]);

  const [tileStyle, setTileStyle] = useState<'dark' | 'streets' | 'satellite'>('dark');
  const [editorPoints, setEditorPoints] = useState<[number, number][]>([]); // [lng, lat]
  const [zoneType, setZoneType] = useState<string>('Surge Zone (+1.5x)');
  const [selectedVehicle, setSelectedVehicle] = useState<FleetVehicle | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [vehicles, setVehicles] = useState<FleetVehicle[]>([]);

  // Dynamic Fleet Telemetry Hook from FastAPI Backend
  useEffect(() => {
    let isMounted = true;
    const fetchLiveVehicles = async () => {
      try {
        // 1. Try dedicated FastAPI location fleet-map endpoint first
        let resp = await fetch('/api/v1/location/admin/fleet-map');
        if (resp.ok) {
          const fleetData = await resp.json();
          if (fleetData?.drivers && Array.isArray(fleetData.drivers) && isMounted) {
            const mapped: FleetVehicle[] = fleetData.drivers.map((d: any, i: number) => ({
              id: d.driver_id || `driver-${i}`,
              plate: d.license_plate || `DL-1ER-${1000 + i}`,
              driver: d.driver_name || 'Pilot',
              model: d.vehicle_model || 'E-Rickshaw Fleet',
              lat: Number(d.latitude || 28.6315 + (i * 0.003)),
              lng: Number(d.longitude || 77.2167 + (i * 0.003)),
              battery: Number(d.battery_level || 85),
              speed: Number(d.speed_kmh || 20),
              status: (d.status === 'on_trip' || d.status === 'en_route') ? d.status : 'available',
              passenger: d.ride_id ? `Trip: ${d.ride_id.slice(0, 8)}` : undefined,
              fare: d.fare || 128
            }));
            setVehicles(mapped);
            return;
          }
        }

        // 2. Fallback to vehicles nearby
        resp = await fetch('/api/v1/vehicles/nearby?lat=28.63&lng=77.21&radius_km=25');
        if (resp.ok) {
          const data = await resp.json();
          if (Array.isArray(data) && isMounted) {
            const mapped: FleetVehicle[] = data.map((v: any, i: number) => ({
              id: v.id || `live-${i}`,
              plate: v.registration_number || v.plate || `DL-1ER-${1000 + i}`,
              driver: v.driver_name || v.driver || 'Live Chauffeur',
              model: v.model_name || v.model || 'E-Rickshaw Pro',
              lat: Number(v.current_latitude || v.lat || 28.6315 + (i * 0.004)),
              lng: Number(v.current_longitude || v.lng || 77.2167 + (i * 0.004)),
              battery: Number(v.battery_percentage || v.battery || 85),
              speed: Number(v.current_speed || v.speed || 22),
              status: v.status || 'available',
              passenger: v.passenger_name || v.passenger,
              fare: v.fare || 128
            }));
            setVehicles(mapped);
          }
        }
      } catch {
        // Network offline or connecting
      }
    };
    fetchLiveVehicles();
    const interval = setInterval(fetchLiveVehicles, 10000);
    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, []);

  // Clear existing DOM markers
  const clearMarkers = useCallback(() => {
    markersRef.current.forEach(m => m.remove());
    markersRef.current = [];
  }, []);

  // Update Vector Overlays (Geofences, Routes, Restricted Roads, Vehicle Markers)
  const renderOverlays = useCallback(
    (map: mapboxgl.Map) => {
      clearMarkers();

      // 1. Geofences
      if (mode === 'live_fleet' || mode === 'geofence_editor' || mode === 'mini') {
        const features = GEOFENCE_ZONES_MAPBOX.map((z, idx) => ({
          type: 'Feature' as const,
          properties: {
            id: `zone-${idx}`,
            name: z.name,
            type: z.type,
            color: z.color
          },
          geometry: {
            type: 'Polygon' as const,
            coordinates: [z.coords]
          }
        }));

        if (map.getSource('geofences-src')) {
          (map.getSource('geofences-src') as mapboxgl.GeoJSONSource).setData({
            type: 'FeatureCollection',
            features
          });
        } else {
          map.addSource('geofences-src', {
            type: 'geojson',
            data: { type: 'FeatureCollection', features }
          });

          map.addLayer({
            id: 'geofences-fill',
            type: 'fill',
            source: 'geofences-src',
            paint: {
              'fill-color': ['get', 'color'],
              'fill-opacity': 0.18
            }
          });

          map.addLayer({
            id: 'geofences-outline',
            type: 'line',
            source: 'geofences-src',
            paint: {
              'line-color': ['get', 'color'],
              'line-width': 2
            }
          });
        }
      }

      // 2. Restricted Roads
      if (mode === 'live_fleet' || mode === 'road_restrictions') {
        const roadFeatures = RESTRICTED_ROADS_MAPBOX.map((r, idx) => ({
          type: 'Feature' as const,
          properties: {
            id: `road-${idx}`,
            name: r.name,
            type: r.type
          },
          geometry: {
            type: 'LineString' as const,
            coordinates: r.coords
          }
        }));

        if (map.getSource('restricted-roads-src')) {
          (map.getSource('restricted-roads-src') as mapboxgl.GeoJSONSource).setData({
            type: 'FeatureCollection',
            features: roadFeatures
          });
        } else {
          map.addSource('restricted-roads-src', {
            type: 'geojson',
            data: { type: 'FeatureCollection', features: roadFeatures }
          });

          map.addLayer({
            id: 'restricted-roads-line',
            type: 'line',
            source: 'restricted-roads-src',
            paint: {
              'line-color': '#ef4444',
              'line-width': 5,
              'line-dasharray': [3, 2]
            }
          });
        }
      }

      // 3. Fleet Vehicles Markers
      if (mode === 'live_fleet' || mode === 'mini') {
        vehicles.forEach(v => {
          const statusColor = v.status === 'on_trip' ? '#d4af37' : v.status === 'en_route' ? '#3b82f6' : '#10b981';

          const el = document.createElement('div');
          el.className = 'luxury-mapbox-marker';
          el.style.width = '34px';
          el.style.height = '34px';
          el.style.borderRadius = '50%';
          el.style.background = '#16161a';
          el.style.border = `2px solid ${statusColor}`;
          el.style.boxShadow = `0 0 14px ${statusColor}99`;
          el.style.display = 'flex';
          el.style.alignItems = 'center';
          el.style.justifyContent = 'center';
          el.style.color = statusColor;
          el.style.fontWeight = '900';
          el.style.fontSize = '13px';
          el.style.cursor = 'pointer';
          el.innerHTML = '⚡';

          const popupContent = `
            <div style="font-family: system-ui; min-width: 200px; padding: 6px; background: #16161a; color: #f2ead3; border-radius: 8px;">
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <b style="color: #d4af37; font-size: 13px;">${v.plate}</b>
                <span style="font-size: 10px; color: ${statusColor}; font-weight: bold; text-transform: uppercase;">${v.status.replace('_', ' ')}</span>
              </div>
              <div style="font-size: 11px; color: #f2ead3; margin-top: 4px;">Chauffeur: ${v.driver} (${v.model})</div>
              <div style="font-size: 11px; color: #8d8672; margin-top: 2px;">Battery: <b style="color: ${v.battery > 20 ? '#10b981' : '#ef4444'}">${v.battery}%</b> | Speed: ${v.speed} km/h</div>
              ${v.passenger ? `<div style="margin-top: 6px; padding: 4px 6px; background: #0f0f12; border-radius: 4px; font-size: 10px; color: #d4af37;">Rider: ${v.passenger} · Fare: ₹${v.fare}</div>` : ''}
            </div>
          `;

          const popup = new mapboxgl.Popup({ offset: 16 }).setHTML(popupContent);

          el.addEventListener('click', () => {
            setSelectedVehicle(v);
          });

          const marker = new mapboxgl.Marker({ element: el })
            .setLngLat([v.lng, v.lat])
            .setPopup(popup)
            .addTo(map);

          markersRef.current.push(marker);
        });
      }

      // 4. Ride Route Mode
      if (mode === 'ride_route') {
        const pickup: [number, number] = [77.2195, 28.6328]; // [lng, lat]
        const drop: [number, number] = [77.2270, 28.6000];
        const routeCoords: [number, number][] = [
          pickup,
          [77.2230, 28.6250],
          [77.2250, 28.6150],
          [77.2260, 28.6070],
          drop
        ];

        if (map.getSource('ride-route-src')) {
          (map.getSource('ride-route-src') as mapboxgl.GeoJSONSource).setData({
            type: 'Feature',
            properties: {},
            geometry: {
              type: 'LineString',
              coordinates: routeCoords
            }
          });
        } else {
          map.addSource('ride-route-src', {
            type: 'geojson',
            data: {
              type: 'Feature',
              properties: {},
              geometry: {
                type: 'LineString',
                coordinates: routeCoords
              }
            }
          });

          map.addLayer({
            id: 'ride-route-casing',
            type: 'line',
            source: 'ride-route-src',
            paint: {
              'line-color': '#0f172a',
              'line-width': 10
            }
          });

          map.addLayer({
            id: 'ride-route-line',
            type: 'line',
            source: 'ride-route-src',
            paint: {
              'line-color': '#d4af37',
              'line-width': 5
            }
          });
        }

        // Pickup Marker (Green)
        const pickupEl = document.createElement('div');
        pickupEl.style.background = '#10b981';
        pickupEl.style.color = '#000';
        pickupEl.style.fontWeight = '900';
        pickupEl.style.fontSize = '10px';
        pickupEl.style.padding = '3px 8px';
        pickupEl.style.borderRadius = '6px';
        pickupEl.style.boxShadow = '0 0 10px #10b981';
        pickupEl.style.border = '1px solid #fff';
        pickupEl.innerText = 'PICKUP';
        const pMarker = new mapboxgl.Marker({ element: pickupEl }).setLngLat(pickup).addTo(map);
        markersRef.current.push(pMarker);

        // Dropoff Marker (Gold)
        const dropEl = document.createElement('div');
        dropEl.style.background = '#d4af37';
        dropEl.style.color = '#000';
        dropEl.style.fontWeight = '900';
        dropEl.style.fontSize = '10px';
        dropEl.style.padding = '3px 8px';
        dropEl.style.borderRadius = '6px';
        dropEl.style.boxShadow = '0 0 10px #d4af37';
        dropEl.style.border = '1px solid #fff';
        dropEl.innerText = 'DROPOFF';
        const dMarker = new mapboxgl.Marker({ element: dropEl }).setLngLat(drop).addTo(map);
        markersRef.current.push(dMarker);

        // Fit Bounds
        const bounds = new mapboxgl.LngLatBounds();
        routeCoords.forEach(c => bounds.extend(c));
        map.fitBounds(bounds, { padding: 80, duration: 1000 });
      }

      // 5. SOS Incident Mode
      if (mode === 'sos_incident') {
        const incidentCoords: [number, number] = [77.2230, 28.6385];
        const sosEl = document.createElement('div');
        sosEl.style.position = 'relative';
        sosEl.style.width = '44px';
        sosEl.style.height = '44px';
        sosEl.style.display = 'flex';
        sosEl.style.alignItems = 'center';
        sosEl.style.justifyContent = 'center';
        sosEl.innerHTML = `
          <div style="position:absolute; width:44px; height:44px; border-radius:50%; background:rgba(239,68,68,0.4); animation:ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite;"></div>
          <div style="width:28px; height:28px; border-radius:50%; background:#ef4444; color:#fff; font-weight:900; font-size:12px; display:flex; align-items:center; justify-content:center; box-shadow:0 0 14px #ef4444; border:2px solid #fff;">
            SOS
          </div>
        `;

        const topVehicle = vehicles && vehicles.length > 0 ? vehicles[0] : null;
        const driverInfo = topVehicle ? `Chauffeur: ${topVehicle.driver || 'Partner'} (${topVehicle.plate || 'Fleet EV'})` : 'Chauffeur: Active Partner Unit';
        const sosPopup = new mapboxgl.Popup({ offset: 16 }).setHTML(`
          <div style="font-family:system-ui; background:#16161a; color:#fff; padding:8px; border-radius:8px;">
            <b style="color:#ef4444; font-size:13px;">🚨 CRITICAL SOS BEACON</b>
            <div style="font-size:11px; margin-top:4px;">${driverInfo}</div>
            <div style="font-size:11px; color:#f59e0b; margin-top:2px;">Location: 28.6385° N, 77.2230° E</div>
            <div style="font-size:10px; color:#10b981; margin-top:4px;">● Govt 112 Dispatch Connected</div>
          </div>
        `);

        const sMarker = new mapboxgl.Marker({ element: sosEl })
          .setLngLat(incidentCoords)
          .setPopup(sosPopup)
          .addTo(map);
        markersRef.current.push(sMarker);

        map.flyTo({ center: incidentCoords, zoom: 16, duration: 1200 });
      }
    },
    [mode, clearMarkers, vehicles]
  );

  // Re-render overlays when dynamic vehicle coordinates update
  useEffect(() => {
    if (mapInstanceRef.current && mapInstanceRef.current.loaded()) {
      renderOverlays(mapInstanceRef.current);
    }
  }, [vehicles, renderOverlays]);

  // Initialize Mapbox GL JS Map
  useEffect(() => {
    if (!mapContainerRef.current) return;

    mapboxgl.accessToken = MAPBOX_PUBLIC_KEY;

    const map = new mapboxgl.Map({
      container: mapContainerRef.current,
      style: getMapboxStyle(tileStyle),
      center: [77.2195, 28.6328], // Connaught Place Central Delhi
      zoom: 13.5,
      pitch: 35,
      bearing: 0,
      attributionControl: false
    });

    mapInstanceRef.current = map;

    map.on('load', () => {
      renderOverlays(map);
    });

    // Geofence Editor Click Handler
    if (mode === 'geofence_editor') {
      map.on('click', (e: mapboxgl.MapMouseEvent) => {
        const pt: [number, number] = [e.lngLat.lng, e.lngLat.lat];
        setEditorPoints(prev => {
          const updated = [...prev, pt];

          // Add point marker
          const dot = document.createElement('div');
          dot.style.width = '10px';
          dot.style.height = '10px';
          dot.style.borderRadius = '50%';
          dot.style.background = '#d4af37';
          dot.style.boxShadow = '0 0 8px #d4af37';
          const dotMarker = new mapboxgl.Marker({ element: dot }).setLngLat(pt).addTo(map);
          markersRef.current.push(dotMarker);

          // Update editor preview polygon
          if (updated.length >= 3) {
            const closedCoords = [...updated, updated[0]];
            if (map.getSource('editor-polygon-src')) {
              (map.getSource('editor-polygon-src') as mapboxgl.GeoJSONSource).setData({
                type: 'Feature',
                properties: {},
                geometry: {
                  type: 'Polygon',
                  coordinates: [closedCoords]
                }
              });
            } else {
              map.addSource('editor-polygon-src', {
                type: 'geojson',
                data: {
                  type: 'Feature',
                  properties: {},
                  geometry: {
                    type: 'Polygon',
                    coordinates: [closedCoords]
                  }
                }
              });

              map.addLayer({
                id: 'editor-polygon-fill',
                type: 'fill',
                source: 'editor-polygon-src',
                paint: {
                  'fill-color': '#d4af37',
                  'fill-opacity': 0.25
                }
              });

              map.addLayer({
                id: 'editor-polygon-line',
                type: 'line',
                source: 'editor-polygon-src',
                paint: {
                  'line-color': '#d4af37',
                  'line-width': 2,
                  'line-dasharray': [2, 2]
                }
              });
            }
          }

          return updated;
        });
      });
    }

    return () => {
      clearMarkers();
      map.remove();
      mapInstanceRef.current = null;
    };
  }, [mode, renderOverlays, clearMarkers]);

  // Switch Tile Style
  const switchTileStyle = (styleKey: 'dark' | 'streets' | 'satellite') => {
    setTileStyle(styleKey);
    const map = mapInstanceRef.current;
    if (map) {
      map.setStyle(getMapboxStyle(styleKey));
      map.once('style.load', () => {
        renderOverlays(map);
      });
    }
  };

  const handleZoomIn = () => {
    mapInstanceRef.current?.zoomIn();
  };

  const handleZoomOut = () => {
    mapInstanceRef.current?.zoomOut();
  };

  const handleFlyTo = (coords: [number, number], zoom = 15) => {
    mapInstanceRef.current?.flyTo({ center: coords, zoom, duration: 1200, pitch: 35 });
  };

  const handleSearchLocation = (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    const lower = searchQuery.trim().toLowerCase();
    const match = DELHI_LANDMARKS_MAPBOX.find(l => l.label.toLowerCase().includes(lower));
    if (match) {
      handleFlyTo(match.coords, 16);
    } else {
      handleFlyTo([77.2195, 28.6328], 15);
    }
  };

  const handleClearEditor = () => {
    setEditorPoints([]);
    clearMarkers();
    const map = mapInstanceRef.current;
    if (map && map.getSource('editor-polygon-src')) {
      (map.getSource('editor-polygon-src') as mapboxgl.GeoJSONSource).setData({
        type: 'FeatureCollection',
        features: []
      });
    }
  };

  const handleSaveGeofence = () => {
    if (editorPoints.length < 3) {
      alert('Please click on the Mapbox vector map to place at least 3 boundary vertices.');
      return;
    }
    if (onPolygonCreated) {
      // Pass back [lat, lng] pairs for backend format compatibility
      const latLngPoints: [number, number][] = editorPoints.map(([lng, lat]) => [lat, lng]);
      onPolygonCreated(latLngPoints, zoneType);
    }
    alert(`Successfully registered new Geofence (${zoneType}) with ${editorPoints.length} Mapbox vector coordinates!`);
    handleClearEditor();
  };

  return (
    <div
      className={`admin-real-map-wrapper ${className}`}
      style={{
        position: 'relative',
        width: '100%',
        height: isFullscreen ? '100vh' : height,
        borderRadius: isFullscreen ? 0 : 14,
        overflow: 'hidden',
        border: '1px solid #2a2a2e',
        background: '#0b0b0d',
        zIndex: isFullscreen ? 99999 : 1
      }}
    >
      {/* Mapbox Vector Canvas */}
      <div
        ref={mapContainerRef}
        style={{ width: '100%', height: '100%', background: '#0b0b0d' }}
      />

      {/* Top Map HUD Bar: Engine Status & Location Search */}
      <div
        style={{
          position: 'absolute',
          top: 12,
          left: 12,
          right: 12,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 10,
          pointerEvents: 'none',
          zIndex: 10
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, pointerEvents: 'auto', flexWrap: 'wrap' }}>
          <div
            style={{
              background: 'rgba(22, 22, 26, 0.94)',
              backdropFilter: 'blur(8px)',
              border: '1px solid #d4af3744',
              borderRadius: 8,
              padding: '6px 12px',
              display: 'flex',
              alignItems: 'center',
              gap: 8
            }}
          >
            <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#10b981', boxShadow: '0 0 8px #10b981' }} />
            <span style={{ color: '#d4af37', fontWeight: 800, fontSize: 11, letterSpacing: '0.04em' }}>
              MAPBOX GL JS VECTOR ENGINE · {selectedScreenTitle.toUpperCase()}
            </span>
          </div>

          {/* Quick Landmark Jump Pills */}
          <div style={{ display: 'flex', gap: 4, overflowX: 'auto', maxWidth: 440 }}>
            {DELHI_LANDMARKS_MAPBOX.slice(0, 4).map((lm, idx) => (
              <button
                key={idx}
                onClick={() => handleFlyTo(lm.coords, 15)}
                style={{
                  background: 'rgba(22, 22, 26, 0.9)',
                  border: '1px solid #333',
                  color: '#f2ead3',
                  borderRadius: 6,
                  padding: '4px 8px',
                  fontSize: 10,
                  fontWeight: 600,
                  cursor: 'pointer',
                  whiteSpace: 'nowrap'
                }}
              >
                📍 {lm.label}
              </button>
            ))}
          </div>
        </div>

        {/* Map Actions: Mapbox Style Switcher & Fullscreen */}
        <div style={{ display: 'flex', gap: 6, pointerEvents: 'auto' }}>
          <div
            style={{
              background: 'rgba(22, 22, 26, 0.94)',
              border: '1px solid #333',
              borderRadius: 8,
              padding: '2px',
              display: 'flex',
              gap: 2
            }}
          >
            <button
              onClick={() => switchTileStyle('dark')}
              style={{
                background: tileStyle === 'dark' ? '#d4af37' : 'transparent',
                color: tileStyle === 'dark' ? '#000' : '#8d8672',
                border: 'none',
                borderRadius: 6,
                padding: '4px 8px',
                fontSize: 10,
                fontWeight: 700,
                cursor: 'pointer'
              }}
            >
              🌙 Dark
            </button>
            <button
              onClick={() => switchTileStyle('streets')}
              style={{
                background: tileStyle === 'streets' ? '#d4af37' : 'transparent',
                color: tileStyle === 'streets' ? '#000' : '#8d8672',
                border: 'none',
                borderRadius: 6,
                padding: '4px 8px',
                fontSize: 10,
                fontWeight: 700,
                cursor: 'pointer'
              }}
            >
              🗺️ Streets
            </button>
            <button
              onClick={() => switchTileStyle('satellite')}
              style={{
                background: tileStyle === 'satellite' ? '#d4af37' : 'transparent',
                color: tileStyle === 'satellite' ? '#000' : '#8d8672',
                border: 'none',
                borderRadius: 6,
                padding: '4px 8px',
                fontSize: 10,
                fontWeight: 700,
                cursor: 'pointer'
              }}
            >
              🛰️ Satellite
            </button>
          </div>

          <button
            onClick={() => handleFlyTo([77.2195, 28.6328], 14)}
            title="Recenter Central Delhi"
            style={{
              background: '#16161a',
              border: '1px solid #333',
              borderRadius: 8,
              color: '#d4af37',
              padding: '6px 10px',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 4
            }}
          >
            <RefreshCw size={12} /> Recenter
          </button>

          <button
            onClick={() => setIsFullscreen(!isFullscreen)}
            title="Toggle Fullscreen"
            style={{
              background: '#16161a',
              border: '1px solid #333',
              borderRadius: 8,
              color: '#d4af37',
              padding: '6px 10px',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 4
            }}
          >
            <Maximize2 size={12} /> {isFullscreen ? 'Exit' : 'Expand'}
          </button>
        </div>
      </div>

      {/* Search Input Bar (Top-Left) */}
      <div
        style={{
          position: 'absolute',
          top: 56,
          left: 12,
          zIndex: 10
        }}
      >
        <form onSubmit={handleSearchLocation} style={{ display: 'flex', gap: 4 }}>
          <input
            type="text"
            placeholder="Search landmark (e.g. Connaught Place)..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            style={{
              background: 'rgba(22, 22, 26, 0.95)',
              border: '1px solid #333',
              color: '#f2ead3',
              borderRadius: 8,
              padding: '6px 12px',
              fontSize: 11,
              width: 240,
              boxShadow: '0 4px 12px rgba(0,0,0,0.5)'
            }}
          />
          <button
            type="submit"
            style={{
              background: '#d4af37',
              color: '#000',
              border: 'none',
              borderRadius: 8,
              padding: '6px 10px',
              cursor: 'pointer',
              fontWeight: 700
            }}
          >
            <Search size={12} />
          </button>
        </form>
      </div>

      {/* Floating Zoom Controls */}
      <div
        style={{
          position: 'absolute',
          bottom: 16,
          right: 14,
          display: 'flex',
          flexDirection: 'column',
          gap: 6,
          zIndex: 10
        }}
      >
        <button
          onClick={handleZoomIn}
          style={{
            background: '#16161a',
            border: '1px solid #333',
            color: '#f2ead3',
            width: 34,
            height: 34,
            borderRadius: 8,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            boxShadow: '0 4px 12px rgba(0,0,0,0.5)'
          }}
        >
          <ZoomIn size={16} />
        </button>
        <button
          onClick={handleZoomOut}
          style={{
            background: '#16161a',
            border: '1px solid #333',
            color: '#f2ead3',
            width: 34,
            height: 34,
            borderRadius: 8,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            boxShadow: '0 4px 12px rgba(0,0,0,0.5)'
          }}
        >
          <ZoomOut size={16} />
        </button>
      </div>

      {/* Geofence Editor Floating Toolbar if in Geofence mode */}
      {mode === 'geofence_editor' && (
        <div
          style={{
            position: 'absolute',
            bottom: 16,
            left: 14,
            background: 'rgba(22, 22, 26, 0.95)',
            backdropFilter: 'blur(8px)',
            border: '1px solid #d4af37',
            borderRadius: 12,
            padding: '12px 16px',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            zIndex: 10,
            maxWidth: 560,
            boxShadow: '0 6px 20px rgba(0,0,0,0.8)'
          }}
        >
          <div>
            <div style={{ color: '#d4af37', fontSize: 12, fontWeight: 700 }}>
              Mapbox Geofence Editor: Click map to place perimeter vertices ({editorPoints.length} points)
            </div>
            <div style={{ color: '#8d8672', fontSize: 11, marginTop: 4 }}>
              Zone Type:
              <select
                value={zoneType}
                onChange={e => setZoneType(e.target.value)}
                style={{
                  marginLeft: 6,
                  background: '#0f0f12',
                  border: '1px solid #333',
                  color: '#f2ead3',
                  borderRadius: 6,
                  padding: '2px 6px',
                  fontSize: 11
                }}
              >
                <option value="Operational Zone">Operational Zone</option>
                <option value="Surge Zone (+1.5x)">Surge Zone (+1.5x)</option>
                <option value="High Demand (+2.0x)">High Demand (+2.0x)</option>
                <option value="Prohibited Road">Prohibited / Restricted Road</option>
              </select>
            </div>
          </div>

          <div style={{ display: 'flex', gap: 6 }}>
            <button
              onClick={handleClearEditor}
              style={{
                background: '#222',
                color: '#8d8672',
                border: '1px solid #333',
                borderRadius: 6,
                padding: '6px 12px',
                fontSize: 11,
                cursor: 'pointer'
              }}
            >
              Reset
            </button>
            <button
              onClick={handleSaveGeofence}
              style={{
                background: 'linear-gradient(90deg, #b8962e, #d4af37)',
                color: '#000',
                border: 'none',
                borderRadius: 6,
                padding: '6px 14px',
                fontWeight: 700,
                fontSize: 11,
                cursor: 'pointer'
              }}
            >
              Save Polygon
            </button>
          </div>
        </div>
      )}

      {/* Selected Vehicle Telemetry Card if clicked */}
      {selectedVehicle && (
        <div
          style={{
            position: 'absolute',
            bottom: 16,
            left: 14,
            background: 'rgba(22, 22, 26, 0.95)',
            backdropFilter: 'blur(10px)',
            border: '1px solid #d4af37',
            borderRadius: 12,
            padding: '14px 18px',
            zIndex: 10,
            minWidth: 280,
            boxShadow: '0 8px 24px rgba(0,0,0,0.8)'
          }}
        >
          <div style={{ display: 'flex', justifySelf: 'space-between', alignItems: 'center' }}>
            <span style={{ color: '#d4af37', fontWeight: 800, fontSize: 13 }}>{selectedVehicle.plate}</span>
            <button
              onClick={() => setSelectedVehicle(null)}
              style={{ background: 'transparent', border: 'none', color: '#8d8672', cursor: 'pointer', fontSize: 14, marginLeft: 'auto' }}
            >
              ✕
            </button>
          </div>
          <div style={{ color: '#f2ead3', fontSize: 12, marginTop: 4 }}>Chauffeur: {selectedVehicle.driver}</div>
          <div style={{ color: '#8d8672', fontSize: 11, marginTop: 2 }}>Model: {selectedVehicle.model}</div>
          <div style={{ color: '#8d8672', fontSize: 11, marginTop: 2 }}>
            Battery: <b style={{ color: selectedVehicle.battery > 20 ? '#10b981' : '#ef4444' }}>{selectedVehicle.battery}%</b> | Speed: {selectedVehicle.speed} km/h
          </div>
          {selectedVehicle.passenger && (
            <div style={{ marginTop: 8, padding: '5px 8px', background: '#0f0f12', borderRadius: 6, fontSize: 11, color: '#d4af37' }}>
              Active Rider: {selectedVehicle.passenger} (Fare: ₹{selectedVehicle.fare})
            </div>
          )}
        </div>
      )}
    </div>
  );
};
