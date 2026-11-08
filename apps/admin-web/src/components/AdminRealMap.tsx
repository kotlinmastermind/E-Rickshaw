import React, { useEffect, useRef, useState, useCallback } from 'react';
import mapboxgl from 'mapbox-gl';
import 'mapbox-gl/dist/mapbox-gl.css';
import {
  ZoomIn,
  ZoomOut,
  Maximize2,
  RefreshCw,
  Search,
  Play,
  Pause,
  RotateCcw,
  Crosshair,
  Globe
} from 'lucide-react';
import { fetchWithAuth, requestApi, getWebSocketTicket, API_BASE } from '../services/api';

export interface AdminRealMapProps {
  mode?: 'live_fleet' | 'geofence_editor' | 'road_restrictions' | 'trip_playback' | 'ride_route' | 'sos_incident' | 'mini';
  height?: string | number;
  selectedScreenTitle?: string;
  onPolygonCreated?: (coords: [number, number][], zoneType: string) => void;
  className?: string;
  rideId?: string;
}

export interface FleetVehicle {
  id: string;
  plate: string;
  driver: string;
  model: string;
  lat: number;
  lng: number;
  battery: number;
  speed: number;
  heading?: number;
  status: 'available' | 'on_trip' | 'en_route';
  passenger?: string;
  fare?: number;
  last_seen?: string;
}

export interface GeofenceZone {
  id?: string;
  name: string;
  type: string;
  color: string;
  surge_multiplier?: number;
  coords: [number, number][]; // [lng, lat]
}

export interface RestrictedRoad {
  id?: string;
  name: string;
  type: string;
  reason: string;
  penalty: string;
  detour: string;
  coords: [number, number][]; // [lng, lat]
}

export interface TripBreadcrumb {
  latitude: number;
  longitude: number;
  speed_kmh: number;
  heading: number;
  distance_km?: number;
  recorded_at: string;
}

// Major Global Metropolitan Hubs for Quick Operations Jump
const GLOBAL_METROPOLITAN_HUBS = [
  { label: 'New York (Manhattan)', coords: [-73.9855, 40.7484] as [number, number] },
  { label: 'London (Westminster)', coords: [-0.1278, 51.5074] as [number, number] },
  { label: 'Tokyo (Marunouchi)', coords: [139.7671, 35.6812] as [number, number] },
  { label: 'Paris (Champs-Élysées)', coords: [2.3522, 48.8566] as [number, number] },
  { label: 'Dubai (Downtown)', coords: [55.2708, 25.2048] as [number, number] },
  { label: 'Singapore (Marina Bay)', coords: [103.8519, 1.2863] as [number, number] },
  { label: 'Sydney (Central Hub)', coords: [151.2093, -33.8688] as [number, number] },
  { label: 'Mumbai (BKC / Colaba)', coords: [72.8777, 19.0760] as [number, number] },
  { label: 'New Delhi (Central)', coords: [77.2195, 28.6328] as [number, number] },
  { label: 'San Francisco (Market St)', coords: [-122.4194, 37.7749] as [number, number] }
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

// Calculate approximate polygon area in square kilometers using Shoelace Formula
function calculatePolygonAreaSqKm(coords: [number, number][]): number {
  if (coords.length < 3) return 0;
  let area = 0;
  const numPoints = coords.length;
  for (let i = 0; i < numPoints; i++) {
    const j = (i + 1) % numPoints;
    const xi = coords[i][0] * 111.32 * Math.cos((coords[i][1] * Math.PI) / 180);
    const yi = coords[i][1] * 110.574;
    const xj = coords[j][0] * 111.32 * Math.cos((coords[j][1] * Math.PI) / 180);
    const yj = coords[j][1] * 110.574;
    area += xi * yj - xj * yi;
  }
  return Math.abs(area / 2);
}

export interface LocationFreshness {
  label: string;
  color: string;
  status: 'LIVE' | 'DELAYED' | 'STALE' | 'UNAVAILABLE';
}

export function getLocationFreshness(lastSeen?: string): LocationFreshness {
  if (!lastSeen) {
    return { label: 'Unavailable', color: '#94a3b8', status: 'UNAVAILABLE' };
  }
  const date = new Date(lastSeen);
  if (isNaN(date.getTime())) {
    return { label: 'Unavailable', color: '#94a3b8', status: 'UNAVAILABLE' };
  }
  const diffSec = Math.max(0, Math.floor((Date.now() - date.getTime()) / 1000));
  if (diffSec <= 30) {
    return { label: `LIVE (${diffSec}s ago)`, color: '#10b981', status: 'LIVE' };
  }
  if (diffSec <= 120) {
    return { label: `DELAYED (${diffSec}s ago)`, color: '#f59e0b', status: 'DELAYED' };
  }
  const diffMins = Math.floor(diffSec / 60);
  return { label: `STALE (${diffMins}m ago)`, color: '#ef4444', status: 'STALE' };
}

export const AdminRealMap: React.FC<AdminRealMapProps> = ({
  mode = 'live_fleet',
  height = '520px',
  selectedScreenTitle = 'Mission Control Map',
  onPolygonCreated,
  className = '',
  rideId = 'RIDE-DEL-8801'
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<mapboxgl.Map | null>(null);
  const markersRef = useRef<mapboxgl.Marker[]>([]);
  const playbackMarkerRef = useRef<mapboxgl.Marker | null>(null);
  const userLocationMarkerRef = useRef<mapboxgl.Marker | null>(null);
  const userLocationCoordsRef = useRef<[number, number] | null>(null);
  const [liveAdminGps, setLiveAdminGps] = useState<{ lat: number; lng: number; accuracy: number } | null>(null);

  // Real-Time continuous GPS Watcher for Admin browser/device
  useEffect(() => {
    if (typeof navigator !== 'undefined' && 'geolocation' in navigator) {
      const watchId = navigator.geolocation.watchPosition(
        (pos) => {
          const coords: [number, number] = [pos.coords.longitude, pos.coords.latitude];
          setLiveAdminGps({
            lat: pos.coords.latitude,
            lng: pos.coords.longitude,
            accuracy: Math.round(pos.coords.accuracy)
          });
          userLocationCoordsRef.current = coords;
          if (userLocationMarkerRef.current) {
            userLocationMarkerRef.current.setLngLat(coords);
          }
        },
        (err) => {
          console.warn('Geolocation watch error:', err.message);
        },
        { enableHighAccuracy: true, timeout: 10000, maximumAge: 4000 }
      );
      return () => {
        navigator.geolocation.clearWatch(watchId);
      };
    }
  }, []);

  const handleUserLocationAcquired = useCallback((coords: [number, number], isInitial: boolean = false) => {
    userLocationCoordsRef.current = coords;
    setLiveAdminGps({ lat: coords[1], lng: coords[0], accuracy: 5 });
    const map = mapInstanceRef.current;
    if (!map) return;

    if (userLocationMarkerRef.current) {
      userLocationMarkerRef.current.setLngLat(coords);
    } else {
      const el = document.createElement('div');
      el.className = 'admin-user-location-puck';
      el.title = 'Your Current GPS Location';
      el.innerHTML = `
        <div style="position: relative; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; cursor: pointer;">
          <div style="position: absolute; width: 32px; height: 32px; border-radius: 50%; background: rgba(59, 130, 246, 0.35); animation: pulse 2s infinite;"></div>
          <div style="width: 16px; height: 16px; border-radius: 50%; background: #2563eb; border: 3px solid #ffffff; box-shadow: 0 0 12px rgba(37,99,235,0.9);"></div>
        </div>
      `;
      const marker = new mapboxgl.Marker({ element: el }).setLngLat(coords).addTo(map);
      userLocationMarkerRef.current = marker;
    }

    map.flyTo({
      center: coords,
      zoom: isInitial ? 15.5 : 16.5,
      pitch: 35,
      duration: isInitial ? 2200 : 1600
    });
  }, []);

  const handleLocateCurrentLocation = useCallback(() => {
    if (typeof navigator !== 'undefined' && 'geolocation' in navigator) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          handleUserLocationAcquired([pos.coords.longitude, pos.coords.latitude], false);
        },
        (err) => {
          console.warn('Admin Web Geolocation error:', err.message);
          alert('Could not acquire GPS position. Please check location permissions.');
        },
        { enableHighAccuracy: true, timeout: 8000, maximumAge: 10000 }
      );
    } else {
      alert('Geolocation is not supported by your browser.');
    }
  }, [handleUserLocationAcquired]);

  const handleFlyToWorldView = useCallback(() => {
    const map = mapInstanceRef.current;
    if (!map) return;
    map.flyTo({
      center: [0, 20],
      zoom: 1.8,
      pitch: 0,
      bearing: 0,
      duration: 1800
    });
  }, []);

  // Active Map Mode (can be switched dynamically in HUD)
  const [activeMode, setActiveMode] = useState<string>(mode);
  useEffect(() => {
    setActiveMode(mode);
  }, [mode]);

  const [tileStyle, setTileStyle] = useState<'dark' | 'streets' | 'satellite'>('dark');
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Fleet Radar State
  const [vehicles, setVehicles] = useState<FleetVehicle[]>([]);
  const [vehicleFilter, setVehicleFilter] = useState<'all' | 'available' | 'on_trip' | 'en_route'>('all');
  const [selectedVehicle, setSelectedVehicle] = useState<FleetVehicle | null>(null);

  // Geofence Studio State
  const [geofences, setGeofences] = useState<GeofenceZone[]>([]);
  const [editorPoints, setEditorPoints] = useState<[number, number][]>([]);
  const [zoneType, setZoneType] = useState<string>('Surge Zone (+1.5x)');
  const [zoneName, setZoneName] = useState<string>('New High Surge Sector');

  // Road Restrictions State
  const [restrictedRoads, setRestrictedRoads] = useState<RestrictedRoad[]>([]);
  const [newCorridorPoints, setNewCorridorPoints] = useState<[number, number][]>([]);
  const [newCorridorName, setNewCorridorName] = useState<string>('Elevated Highway Exclusion Zone');
  const [newCorridorType, setNewCorridorType] = useState<string>('Motorized Expressway / Flyover');

  // Trip Playback State
  const [selectedRideId, setSelectedRideId] = useState<string>(rideId);
  const [breadcrumbs, setBreadcrumbs] = useState<TripBreadcrumb[]>([]);
  const [playbackIndex, setPlaybackIndex] = useState<number>(0);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playbackSpeed, setPlaybackSpeed] = useState<number>(1);
  const [isLooping, setIsLooping] = useState<boolean>(true);
  const [tripSummary, setTripSummary] = useState<any>(null);
  const [tripSource, setTripSource] = useState<{ isSynthetic: boolean; source: string } | null>(null);

  // Prevent overlapping background polling fetches
  const isFetchingFleetRef = useRef(false);

  // Fetch Live Fleet Telemetry
  // Fetch Live Fleet Telemetry from FastAPI
  const fetchLiveFleet = useCallback(async () => {
    if (isFetchingFleetRef.current) return;
    isFetchingFleetRef.current = true;
    try {
      const data = await fetchWithAuth<any>('/api/v1/location/admin/fleet-map');
      if (data) {
        const list = data?.fleet || data?.drivers || [];
        if (Array.isArray(list)) {
          const mapped: FleetVehicle[] = list
            .filter((d: any) => (d.lat ?? d.latitude) !== undefined && (d.lng ?? d.longitude) !== undefined)
            .map((d: any, idx: number) => ({
              id: d.id || `pilot-${idx}`,
              plate: d.plate || d.license_plate || `EV-${1000 + idx}`,
              driver: d.driver || d.driver_name || 'Pilot',
              model: d.model || d.vehicle_model || 'RideGo E-Rickshaw EV',
              lat: Number(d.lat ?? d.latitude),
              lng: Number(d.lng ?? d.longitude),
              battery: Number(d.battery ?? d.battery_level ?? 85),
              speed: Number(d.speed ?? d.speed_kmh ?? 0),
              heading: Number(d.heading ?? 0),
              status: d.status === 'on_trip' || d.status === 'en_route' ? d.status : 'available',
              passenger: d.passenger || (d.ride_id ? `Trip: ${d.ride_id.slice(0, 8)}` : undefined),
              fare: d.fare || 128,
              last_seen: d.last_seen
            }));
          setVehicles(mapped);
          return;
        }
      }
    } catch {
      // Offline or network error
    } finally {
      isFetchingFleetRef.current = false;
    }
  }, []);

  useEffect(() => {
    fetchLiveFleet();
    const interval = setInterval(fetchLiveFleet, 10000);
    return () => clearInterval(interval);
  }, [fetchLiveFleet]);

  // Real-time WebSocket Telemetry stream for live fleet markers
  useEffect(() => {
    let ws: WebSocket | null = null;
    let isCancelled = false;

    const connectWs = async () => {
      try {
        const token = localStorage.getItem('admin_token') || '';
        if (!token) return;

        const ticket = await getWebSocketTicket();
        if (isCancelled || !ticket) return;

        const wsBase = API_BASE.startsWith('http')
          ? API_BASE.replace(/^http/, 'ws')
          : `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/api/v1`;
        const wsUrl = `${wsBase.replace(/\/$/, '')}/ws/admin/telemetry?ticket=${encodeURIComponent(ticket)}`;
        ws = new WebSocket(wsUrl);
        ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            if (data.event_type === 'DRIVER_LOCATION_UPDATE' && data.payload) {
              const p = data.payload;
              const driverId = p.driver_id || p.id;
              const lat = Number(p.latitude ?? p.lat);
              const lng = Number(p.longitude ?? p.lng);
              const heading = Number(p.heading ?? 0);
              const speed = Number(p.speed ?? p.speed_mps ?? 0);

              if (driverId && !isNaN(lat) && !isNaN(lng) && lat !== 0 && lng !== 0) {
                setVehicles((prev) => {
                  const existingIdx = prev.findIndex((v) => v.id === driverId);
                  if (existingIdx >= 0) {
                    const updated = [...prev];
                    updated[existingIdx] = {
                      ...updated[existingIdx],
                      lat,
                      lng,
                      heading,
                      speed,
                      last_seen: p.last_seen || new Date().toISOString()
                    };
                    return updated;
                  } else {
                    return [
                      ...prev,
                      {
                        id: driverId,
                        plate: p.plate || `EV-${driverId.slice(-4)}`,
                        driver: p.driver || 'Pilot',
                        model: p.model || 'RideGo E-Rickshaw EV',
                        lat,
                        lng,
                        battery: 88,
                        speed,
                        heading,
                        status: 'available',
                        last_seen: p.last_seen || new Date().toISOString()
                      }
                    ];
                  }
                });
              }
            }
          } catch {}
        };
      } catch {}
    };

    connectWs();

    return () => {
      isCancelled = true;
      try {
        ws?.close();
      } catch {}
    };
  }, []);

  // Fetch Geofences from Backend
  useEffect(() => {
    const fetchGeofences = async () => {
      try {
        const data = await fetchWithAuth<any>('/api/v1/location/geofences');
        if (data && Array.isArray(data?.geofences) && data.geofences.length > 0) {
          const mapped: GeofenceZone[] = data.geofences.map((g: any) => ({
            id: g.id,
            name: g.name,
            type: g.zone_type || g.type,
            color: g.color || '#d4af37',
            surge_multiplier: g.surge_multiplier || 1.0,
            coords: g.coordinates || g.coords
          }));
          setGeofences(mapped);
        }
      } catch {
        // Fallback to default
      }
    };
    fetchGeofences();
  }, []);

  // Fetch Road Restrictions from Backend
  useEffect(() => {
    const fetchRestrictions = async () => {
      try {
        const data = await fetchWithAuth<any>('/api/v1/location/road-restrictions');
        if (data && Array.isArray(data) && data.length > 0) {
          const mapped = data.map((r: any) => ({
            id: r.id,
            name: r.title || r.name,
            type: r.restriction_type || 'Elevated Motorway',
            reason: r.description || 'Restricted corridor for low-speed urban shuttles under municipal policy (administrative advisory).',
            penalty: 'Traffic diversion advisory and local enforcement.',
            detour: 'Surface bypass recommended.',
            coords: [
              [r.longitude - 0.002, r.latitude - 0.002],
              [r.longitude + 0.002, r.latitude + 0.002]
            ] as [number, number][]
          }));
          setRestrictedRoads(mapped);
        }
      } catch {
        // Fallback to defaults
      }
    };
    fetchRestrictions();
  }, []);

  // Fetch Trip Breadcrumbs when in Playback mode or ride changes
  const fetchTripBreadcrumbs = useCallback(async (targetRideId: string) => {
    try {
      const data = await fetchWithAuth<any>(`/api/v1/location/trip-history/${targetRideId}`);
      if (data) {
        setTripSource({
          isSynthetic: Boolean(data?.is_synthetic),
          source: data?.source || 'sample_demonstration_trajectory'
        });
        if (Array.isArray(data?.breadcrumbs) && data.breadcrumbs.length > 0) {
          setBreadcrumbs(data.breadcrumbs);
          setPlaybackIndex(0);
          setTripSummary(data.summary || null);
          return;
        }
      }
    } catch {
      // Offline fallback
    }
  }, []);

  useEffect(() => {
    if (activeMode === 'trip_playback') {
      fetchTripBreadcrumbs(selectedRideId);
    }
  }, [activeMode, selectedRideId, fetchTripBreadcrumbs]);

  // Clear existing DOM markers
  const clearMarkers = useCallback(() => {
    markersRef.current.forEach(m => m.remove());
    markersRef.current = [];
    if (playbackMarkerRef.current) {
      playbackMarkerRef.current.remove();
      playbackMarkerRef.current = null;
    }
  }, []);

  // Render Overlays onto Mapbox GL JS Vector Canvas
  const renderOverlays = useCallback(
    (map: mapboxgl.Map) => {
      clearMarkers();

      // 1. GEOFENCES (Fleet, Editor, Mini)
      if (activeMode === 'live_fleet' || activeMode === 'geofence_editor' || activeMode === 'mini') {
        const features = geofences.map((z, idx) => ({
          type: 'Feature' as const,
          properties: {
            id: z.id || `zone-${idx}`,
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

      // 2. RESTRICTED FLYOVER / ROAD CORRIDORS
      if (activeMode === 'live_fleet' || activeMode === 'road_restrictions') {
        const roadFeatures = restrictedRoads.map((r, idx) => ({
          type: 'Feature' as const,
          properties: {
            id: r.id || `road-${idx}`,
            name: r.name,
            type: r.type,
            reason: r.reason,
            penalty: r.penalty,
            detour: r.detour
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
            id: 'restricted-roads-casing',
            type: 'line',
            source: 'restricted-roads-src',
            paint: {
              'line-color': '#000000',
              'line-width': 7,
              'line-opacity': 0.6
            }
          });

          map.addLayer({
            id: 'restricted-roads-line',
            type: 'line',
            source: 'restricted-roads-src',
            paint: {
              'line-color': '#ef4444',
              'line-width': 4,
              'line-dasharray': [3, 2]
            }
          });
        }

        // Place warning badge markers on restricted flyovers
        restrictedRoads.forEach(r => {
          if (r.coords.length >= 2) {
            const midCoord: [number, number] = [
              (r.coords[0][0] + r.coords[1][0]) / 2,
              (r.coords[0][1] + r.coords[1][1]) / 2
            ];

            const el = document.createElement('div');
            el.className = 'restricted-road-badge';
            el.style.background = '#ef4444';
            el.style.color = '#fff';
            el.style.fontWeight = '900';
            el.style.fontSize = '9px';
            el.style.padding = '3px 6px';
            el.style.borderRadius = '6px';
            el.style.border = '1px solid #fff';
            el.style.boxShadow = '0 0 10px rgba(239, 68, 68, 0.8)';
            el.style.cursor = 'pointer';
            el.innerText = '⛔ NO E-RICKSHAW';

            const popup = new mapboxgl.Popup({ offset: 12 }).setHTML(`
              <div style="font-family: system-ui; background: #16161a; color: #f2ead3; padding: 10px; border-radius: 8px; min-width: 240px; border: 1px solid #ef4444;">
                <div style="color: #ef4444; font-weight: 800; font-size: 13px;">⛔ ${r.name}</div>
                <div style="font-size: 11px; color: #f87171; margin-top: 4px;">Classification: ${r.type}</div>
                <div style="font-size: 11px; color: #f2ead3; margin-top: 6px; line-height: 1.4;">${r.reason}</div>
                <div style="font-size: 10px; color: #fbbf24; margin-top: 6px; padding: 4px 6px; background: #231d10; border-radius: 4px;">⚖️ ${r.penalty}</div>
                <div style="font-size: 10px; color: #10b981; margin-top: 4px;">🛣️ Detour: ${r.detour}</div>
              </div>
            `);

            const marker = new mapboxgl.Marker({ element: el })
              .setLngLat(midCoord)
              .setPopup(popup)
              .addTo(map);

            markersRef.current.push(marker);
          }
        });
      }

      // 3. FLEET RADAR PILOTS
      if (activeMode === 'live_fleet' || activeMode === 'mini') {
        const filteredVehicles = vehicles.filter(v => {
          if (vehicleFilter !== 'all' && v.status !== vehicleFilter) return false;
          if (searchQuery.trim()) {
            const q = searchQuery.toLowerCase();
            return (
              v.plate.toLowerCase().includes(q) ||
              v.driver.toLowerCase().includes(q) ||
              v.model.toLowerCase().includes(q)
            );
          }
          return true;
        });

        filteredVehicles.forEach(v => {
          const statusColor =
            v.status === 'on_trip'
              ? '#d4af37'
              : v.status === 'en_route'
              ? '#3b82f6'
              : '#10b981';

          const el = document.createElement('div');
          el.className = 'fleet-pilot-marker';
          el.style.width = '36px';
          el.style.height = '36px';
          el.style.borderRadius = '50%';
          el.style.background = '#16161a';
          el.style.border = `2.5px solid ${statusColor}`;
          el.style.boxShadow = `0 0 14px ${statusColor}aa`;
          el.style.display = 'flex';
          el.style.flexDirection = 'column';
          el.style.alignItems = 'center';
          el.style.justifyContent = 'center';
          el.style.color = statusColor;
          el.style.cursor = 'pointer';
          el.style.transition = 'transform 0.2s';

          el.innerHTML = `
            <div style="font-size: 13px; line-height: 1; transform: rotate(${v.heading || 0}deg);">⚡</div>
            <div style="font-size: 8px; font-weight: 800; color: ${v.battery > 20 ? '#10b981' : '#ef4444'}; margin-top: 1px;">${v.battery}%</div>
          `;

          const fresh = getLocationFreshness(v.last_seen);
          const popupContent = `
            <div style="font-family: system-ui; min-width: 220px; padding: 8px; background: #16161a; color: #f2ead3; border-radius: 8px; border: 1px solid ${statusColor};">
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <b style="color: #d4af37; font-size: 13px;">${v.plate}</b>
                <span style="font-size: 10px; color: ${statusColor}; font-weight: bold; text-transform: uppercase;">● ${v.status.replace('_', ' ')}</span>
              </div>
              <div style="font-size: 11px; color: #f2ead3; margin-top: 4px;">Pilot: <b>${v.driver}</b></div>
              <div style="font-size: 11px; color: #8d8672; margin-top: 2px;">Model: ${v.model}</div>
              <div style="font-size: 11px; color: #8d8672; margin-top: 2px;">
                Battery: <b style="color: ${v.battery > 20 ? '#10b981' : '#ef4444'}">${v.battery}%</b> | Speed: <b style="color: #f2ead3;">${v.speed} km/h</b>
              </div>
              <div style="font-size: 10px; color: ${fresh.color}; margin-top: 3px;">
                GPS: <b>${fresh.label}</b>
              </div>
              ${v.passenger ? `<div style="margin-top: 6px; padding: 4px 6px; background: #0f0f12; border-radius: 4px; font-size: 10px; color: #d4af37;">${v.passenger} · Fare: ₹${v.fare}</div>` : ''}
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

      // 4. HISTORICAL TRIP PLAYBACK
      if (activeMode === 'trip_playback' && breadcrumbs.length > 0) {
        const routeCoords: [number, number][] = breadcrumbs.map(b => [b.longitude, b.latitude]);

        if (map.getSource('trip-playback-src')) {
          (map.getSource('trip-playback-src') as mapboxgl.GeoJSONSource).setData({
            type: 'Feature',
            properties: {},
            geometry: {
              type: 'LineString',
              coordinates: routeCoords
            }
          });
        } else {
          map.addSource('trip-playback-src', {
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
            id: 'trip-playback-casing',
            type: 'line',
            source: 'trip-playback-src',
            paint: {
              'line-color': '#0f172a',
              'line-width': 8
            }
          });

          map.addLayer({
            id: 'trip-playback-line',
            type: 'line',
            source: 'trip-playback-src',
            paint: {
              'line-color': '#d4af37',
              'line-width': 4
            }
          });
        }

        // Origin Marker (Green)
        const originEl = document.createElement('div');
        originEl.style.background = '#10b981';
        originEl.style.color = '#000';
        originEl.style.fontWeight = '900';
        originEl.style.fontSize = '9px';
        originEl.style.padding = '3px 6px';
        originEl.style.borderRadius = '5px';
        originEl.style.boxShadow = '0 0 10px #10b981';
        originEl.style.border = '1px solid #fff';
        originEl.innerText = 'TRIP START';
        const startMarker = new mapboxgl.Marker({ element: originEl })
          .setLngLat(routeCoords[0])
          .addTo(map);
        markersRef.current.push(startMarker);

        // Destination Marker (Gold)
        const destEl = document.createElement('div');
        destEl.style.background = '#d4af37';
        destEl.style.color = '#000';
        destEl.style.fontWeight = '900';
        destEl.style.fontSize = '9px';
        destEl.style.padding = '3px 6px';
        destEl.style.borderRadius = '5px';
        destEl.style.boxShadow = '0 0 10px #d4af37';
        destEl.style.border = '1px solid #fff';
        destEl.innerText = 'TRIP END';
        const endMarker = new mapboxgl.Marker({ element: destEl })
          .setLngLat(routeCoords[routeCoords.length - 1])
          .addTo(map);
        markersRef.current.push(endMarker);

        // Current Animated Pilot Marker along Playback
        const currentCrumb = breadcrumbs[playbackIndex] || breadcrumbs[0];
        const pilotEl = document.createElement('div');
        pilotEl.className = 'playback-pilot-marker';
        pilotEl.style.width = '38px';
        pilotEl.style.height = '38px';
        pilotEl.style.borderRadius = '50%';
        pilotEl.style.background = '#16161a';
        pilotEl.style.border = '2.5px solid #d4af37';
        pilotEl.style.boxShadow = '0 0 16px rgba(212, 175, 55, 0.9)';
        pilotEl.style.display = 'flex';
        pilotEl.style.alignItems = 'center';
        pilotEl.style.justifyContent = 'center';
        pilotEl.style.color = '#d4af37';
        pilotEl.style.fontWeight = '900';
        pilotEl.style.fontSize = '14px';
        pilotEl.style.transform = `rotate(${currentCrumb.heading || 0}deg)`;
        pilotEl.style.transition = 'transform 0.1s linear';
        pilotEl.innerHTML = '🚖';

        const pMarker = new mapboxgl.Marker({ element: pilotEl })
          .setLngLat([currentCrumb.longitude, currentCrumb.latitude])
          .addTo(map);
        playbackMarkerRef.current = pMarker;

        // Auto fit route bounds on load
        if (playbackIndex === 0) {
          const bounds = new mapboxgl.LngLatBounds();
          routeCoords.forEach(c => bounds.extend(c));
          map.fitBounds(bounds, { padding: 80, duration: 800 });
        }
      }

      // 5. RIDE ROUTE INSPECTOR
      if (activeMode === 'ride_route') {
        const topVehicle = vehicles.length > 0 ? vehicles[0] : null;
        const pickup: [number, number] = topVehicle
          ? [topVehicle.lng, topVehicle.lat]
          : liveAdminGps
          ? [liveAdminGps.lng, liveAdminGps.lat]
          : [77.2195, 28.6328];
        const drop: [number, number] = [pickup[0] + 0.015, pickup[1] + 0.012];
        const routeCoords: [number, number][] = [
          pickup,
          [pickup[0] + 0.005, pickup[1] + 0.004],
          [pickup[0] + 0.009, pickup[1] + 0.008],
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

        const pEl = document.createElement('div');
        pEl.style.background = '#10b981';
        pEl.style.color = '#000';
        pEl.style.fontWeight = '900';
        pEl.style.fontSize = '9px';
        pEl.style.padding = '3px 8px';
        pEl.style.borderRadius = '5px';
        pEl.innerText = 'PICKUP';
        const pM = new mapboxgl.Marker({ element: pEl }).setLngLat(pickup).addTo(map);
        markersRef.current.push(pM);

        const dEl = document.createElement('div');
        dEl.style.background = '#d4af37';
        dEl.style.color = '#000';
        dEl.style.fontWeight = '900';
        dEl.style.fontSize = '9px';
        dEl.style.padding = '3px 8px';
        dEl.style.borderRadius = '5px';
        dEl.innerText = 'DROPOFF';
        const dM = new mapboxgl.Marker({ element: dEl }).setLngLat(drop).addTo(map);
        markersRef.current.push(dM);

        const bounds = new mapboxgl.LngLatBounds();
        routeCoords.forEach(c => bounds.extend(c));
        map.fitBounds(bounds, { padding: 80, duration: 1000 });
      }

      // 6. SOS INCIDENT BEACON
      if (activeMode === 'sos_incident') {
        const topVehicle = vehicles.length > 0 ? vehicles[0] : null;
        const incidentCoords: [number, number] = topVehicle
          ? [topVehicle.lng, topVehicle.lat]
          : liveAdminGps
          ? [liveAdminGps.lng + 0.005, liveAdminGps.lat + 0.004]
          : [77.2230, 28.6385];
        const sosEl = document.createElement('div');
        sosEl.style.position = 'relative';
        sosEl.style.width = '48px';
        sosEl.style.height = '48px';
        sosEl.style.display = 'flex';
        sosEl.style.alignItems = 'center';
        sosEl.style.justifyContent = 'center';
        sosEl.innerHTML = `
          <div style="position:absolute; width:48px; height:48px; border-radius:50%; background:rgba(239,68,68,0.4); animation:ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite;"></div>
          <div style="width:30px; height:30px; border-radius:50%; background:#ef4444; color:#fff; font-weight:900; font-size:12px; display:flex; align-items:center; justify-content:center; box-shadow:0 0 16px #ef4444; border:2px solid #fff;">
            SOS
          </div>
        `;

        const driverInfo = topVehicle
          ? `Chauffeur: ${topVehicle.driver} (${topVehicle.plate})`
          : 'Chauffeur: Active Partner Unit';

        const sosPopup = new mapboxgl.Popup({ offset: 16 }).setHTML(`
          <div style="font-family:system-ui; background:#16161a; color:#fff; padding:10px; border-radius:8px; border: 1px solid #ef4444;">
            <b style="color:#ef4444; font-size:13px;">🚨 CRITICAL SOS BEACON ACTIVE</b>
            <div style="font-size:11px; margin-top:4px;">${driverInfo}</div>
            <div style="font-size:11px; color:#f59e0b; margin-top:2px;">Location: ${incidentCoords[1].toFixed(4)}° N, ${incidentCoords[0].toFixed(4)}° E</div>
            <div style="font-size:10px; color:#10b981; margin-top:4px;">● Emergency Services Connected · Local Police Dispatch Engaged</div>
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
    [
      activeMode,
      clearMarkers,
      vehicles,
      vehicleFilter,
      searchQuery,
      geofences,
      restrictedRoads,
      breadcrumbs,
      playbackIndex
    ]
  );

  // Re-render overlays when vehicle or breadcrumb state updates
  useEffect(() => {
    if (mapInstanceRef.current && mapInstanceRef.current.loaded()) {
      renderOverlays(mapInstanceRef.current);
    }
  }, [vehicles, vehicleFilter, searchQuery, activeMode, geofences, restrictedRoads, renderOverlays]);

  // Update animated pilot marker position smoothly during playback
  useEffect(() => {
    if (activeMode === 'trip_playback' && playbackMarkerRef.current && breadcrumbs[playbackIndex]) {
      const crumb = breadcrumbs[playbackIndex];
      playbackMarkerRef.current.setLngLat([crumb.longitude, crumb.latitude]);
      const el = playbackMarkerRef.current.getElement();
      if (el) {
        el.style.transform = `rotate(${crumb.heading || 0}deg)`;
      }
    }
  }, [playbackIndex, activeMode, breadcrumbs]);

  // Playback Animation Timer
  useEffect(() => {
    let timer: any = null;
    if (isPlaying && activeMode === 'trip_playback' && breadcrumbs.length > 0) {
      const intervalMs = Math.max(100, Math.floor(600 / playbackSpeed));
      timer = setInterval(() => {
        setPlaybackIndex(prev => {
          if (prev >= breadcrumbs.length - 1) {
            if (isLooping) return 0;
            setIsPlaying(false);
            return prev;
          }
          return prev + 1;
        });
      }, intervalMs);
    }
    return () => {
      if (timer) clearInterval(timer);
    };
  }, [isPlaying, activeMode, breadcrumbs.length, playbackSpeed, isLooping]);

  // Initialize Mapbox GL JS Map
  useEffect(() => {
    if (!mapContainerRef.current) return;

    mapboxgl.accessToken = MAPBOX_PUBLIC_KEY;

    const map = new mapboxgl.Map({
      container: mapContainerRef.current,
      style: getMapboxStyle(tileStyle),
      center: [0, 20],
      zoom: 1.8,
      pitch: 0,
      bearing: 0,
      projection: 'globe' as any,
      attributionControl: false
    });

    mapInstanceRef.current = map;

    map.on('load', () => {
      try {
        (map as any).setFog({
          color: 'rgb(186, 210, 240)',
          'high-color': 'rgb(36, 92, 223)',
          'horizon-blend': 0.02,
          'space-color': 'rgb(11, 11, 25)',
          'star-intensity': 0.6
        });
      } catch {
        // Fallback for styles without fog support
      }

      renderOverlays(map);
      // Auto-detect real physical device/browser current location on map open and smoothly zoom in from worldwide globe!
      if (typeof navigator !== 'undefined' && 'geolocation' in navigator) {
        navigator.geolocation.getCurrentPosition(
          (pos) => {
            handleUserLocationAcquired([pos.coords.longitude, pos.coords.latitude], true);
          },
          (err) => {
            console.warn('Initial geolocation notice:', err.message);
            // If GPS is disabled or waiting, retain worldwide globe overview or fit active vehicles
            if (vehicles.length > 0) {
              const bounds = new mapboxgl.LngLatBounds();
              vehicles.forEach(v => bounds.extend([v.lng, v.lat]));
              map.fitBounds(bounds, { padding: 80, duration: 1800 });
            }
          },
          { enableHighAccuracy: true, timeout: 6000, maximumAge: 30000 }
        );
      } else if (vehicles.length > 0) {
        const bounds = new mapboxgl.LngLatBounds();
        vehicles.forEach(v => bounds.extend([v.lng, v.lat]));
        map.fitBounds(bounds, { padding: 80, duration: 1800 });
      }
    });

    // Map Click Handler for Geofence & Road Restriction Drawing
    map.on('click', (e: mapboxgl.MapMouseEvent) => {
      const pt: [number, number] = [e.lngLat.lng, e.lngLat.lat];

      // Geofence Editor
      if (activeMode === 'geofence_editor') {
        setEditorPoints(prev => {
          const updated = [...prev, pt];

          // Add vertex dot marker
          const dot = document.createElement('div');
          dot.style.width = '10px';
          dot.style.height = '10px';
          dot.style.borderRadius = '50%';
          dot.style.background = '#d4af37';
          dot.style.boxShadow = '0 0 8px #d4af37';
          dot.style.border = '1px solid #fff';
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
      }

      // Road Restrictions Corridor Drafting
      if (activeMode === 'road_restrictions') {
        setNewCorridorPoints(prev => {
          if (prev.length >= 2) {
            return [pt];
          }
          const updated = [...prev, pt];
          if (updated.length === 2) {
            if (map.getSource('corridor-draft-src')) {
              (map.getSource('corridor-draft-src') as mapboxgl.GeoJSONSource).setData({
                type: 'Feature',
                properties: {},
                geometry: {
                  type: 'LineString',
                  coordinates: updated
                }
              });
            } else {
              map.addSource('corridor-draft-src', {
                type: 'geojson',
                data: {
                  type: 'Feature',
                  properties: {},
                  geometry: {
                    type: 'LineString',
                    coordinates: updated
                  }
                }
              });

              map.addLayer({
                id: 'corridor-draft-line',
                type: 'line',
                source: 'corridor-draft-src',
                paint: {
                  'line-color': '#f87171',
                  'line-width': 4,
                  'line-dasharray': [2, 2]
                }
              });
            }
          }
          return updated;
        });
      }
    });

    return () => {
      clearMarkers();
      map.remove();
      mapInstanceRef.current = null;
    };
  }, [activeMode, renderOverlays, clearMarkers]);

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

  const handleZoomIn = () => mapInstanceRef.current?.zoomIn();
  const handleZoomOut = () => mapInstanceRef.current?.zoomOut();

  const handleFlyTo = (coords: [number, number], zoom = 15) => {
    mapInstanceRef.current?.flyTo({ center: coords, zoom, duration: 1200, pitch: 35 });
  };

  const handleSearchLocation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    const query = searchQuery.trim();
    const lower = query.toLowerCase();
    const match = GLOBAL_METROPOLITAN_HUBS.find(l => l.label.toLowerCase().includes(lower));
    if (match) {
      handleFlyTo(match.coords, 14);
      return;
    }
    // Find vehicle by plate
    const vMatch = vehicles.find(v => v.plate.toLowerCase().includes(lower) || v.driver.toLowerCase().includes(lower));
    if (vMatch) {
      handleFlyTo([vMatch.lng, vMatch.lat], 17);
      setSelectedVehicle(vMatch);
      return;
    }
    // Worldwide Geocoding search fallback (Mapbox or OpenStreetMap Nominatim)
    try {
      if (isValidMapboxToken) {
        const geoResp = await fetch(
          `https://api.mapbox.com/geocoding/v5/mapbox.places/${encodeURIComponent(query)}.json?access_token=${MAPBOX_PUBLIC_KEY}&limit=1`
        );
        if (geoResp.ok) {
          const geoData = await geoResp.json();
          if (geoData.features && geoData.features.length > 0) {
            const [lng, lat] = geoData.features[0].center;
            handleFlyTo([lng, lat], 14);
            return;
          }
        }
      } else {
        const nominatimResp = await fetch(
          `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}&limit=1`
        );
        if (nominatimResp.ok) {
          const nomData = await nominatimResp.json();
          if (Array.isArray(nomData) && nomData.length > 0) {
            const lat = parseFloat(nomData[0].lat);
            const lng = parseFloat(nomData[0].lon);
            handleFlyTo([lng, lat], 14);
            return;
          }
        }
      }
    } catch {
      // Ignore network error in fallback
    }
  };

  // Reset Geofence Editor
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
    if (map) renderOverlays(map);
  };

  // Save Drawn Geofence Polygon to Backend
  const handleSaveGeofence = async () => {
    if (editorPoints.length < 3) {
      alert('Please click on the Mapbox vector map to place at least 3 boundary vertices.');
      return;
    }

    const closedCoords = [...editorPoints, editorPoints[0]];
    const color = zoneType.includes('Surge') ? '#d4af37' : zoneType.includes('Demand') ? '#3b82f6' : '#ef4444';
    const surge_multiplier = zoneType.includes('2.0x') ? 2.0 : zoneType.includes('1.5x') ? 1.5 : 1.0;

    const newZone: GeofenceZone = {
      id: `zone-${Date.now()}`,
      name: zoneName || `Geofence Zone (${zoneType})`,
      type: zoneType,
      color,
      surge_multiplier,
      coords: closedCoords
    };

    try {
      await requestApi('/api/v1/location/geofences', {
        method: 'POST',
        body: JSON.stringify({
          name: newZone.name,
          zone_type: newZone.type,
          color: newZone.color,
          surge_multiplier: newZone.surge_multiplier,
          coordinates: newZone.coords
        })
      });
    } catch {
      // Offline fallback
    }

    setGeofences(prev => [...prev, newZone]);

    if (onPolygonCreated) {
      const latLngPoints: [number, number][] = editorPoints.map(([lng, lat]) => [lat, lng]);
      onPolygonCreated(latLngPoints, zoneType);
    }

    alert(`Successfully registered new Geofence (${zoneType}) with ${editorPoints.length} vertices! Area: ${calculatePolygonAreaSqKm(closedCoords).toFixed(2)} sq km.`);
    handleClearEditor();
  };

  // Save Restricted Road Corridor to Backend
  const handleSaveCorridor = async () => {
    if (newCorridorPoints.length < 2) {
      alert('Please click on the map to define the Start and End points of the restricted flyover corridor.');
      return;
    }

    const newRoad: RestrictedRoad = {
      id: `road-${Date.now()}`,
      name: newCorridorName || 'Restricted Motorized Corridor',
      type: newCorridorType,
      reason: 'Restricted corridor for low-speed urban shuttles under municipal policy (administrative advisory).',
      penalty: 'Traffic diversion advisory and local enforcement.',
      detour: 'Use ground-level service roundabout.',
      coords: newCorridorPoints
    };

    try {
      await requestApi('/api/v1/location/road-restrictions', {
        method: 'POST',
        body: JSON.stringify({
          title: newRoad.name,
          description: newRoad.reason,
          restriction_type: newRoad.type,
          latitude: newCorridorPoints[0][1],
          longitude: newCorridorPoints[0][0],
          radius_meters: 600.0
        })
      });
    } catch {
      // Offline fallback
    }

    setRestrictedRoads(prev => [...prev, newRoad]);
    setNewCorridorPoints([]);
    const map = mapInstanceRef.current;
    if (map && map.getSource('corridor-draft-src')) {
      (map.getSource('corridor-draft-src') as mapboxgl.GeoJSONSource).setData({
        type: 'FeatureCollection',
        features: []
      });
    }
    alert(`Successfully saved prohibited road corridor: ${newRoad.name}!`);
  };

  const currentCrumb = breadcrumbs[playbackIndex] || null;

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

      {/* Top Map HUD Bar: Engine Status & Mode Switcher */}
      <div
        style={{
          position: 'absolute',
          top: 10,
          left: 10,
          right: 10,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 8,
          pointerEvents: 'none',
          zIndex: 10
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, pointerEvents: 'auto', flexWrap: 'wrap' }}>
          {/* Status Badge */}
          <div
            style={{
              background: 'rgba(22, 22, 26, 0.94)',
              backdropFilter: 'blur(8px)',
              border: '1px solid #d4af3744',
              borderRadius: 8,
              padding: '5px 10px',
              display: 'flex',
              alignItems: 'center',
              gap: 6
            }}
          >
            <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#10b981', boxShadow: '0 0 8px #10b981' }} />
            <span style={{ color: '#d4af37', fontWeight: 800, fontSize: 11, letterSpacing: '0.04em' }}>
              MAPBOX GL JS · {selectedScreenTitle.toUpperCase()}
            </span>
          </div>

          {/* Real Live Device GPS Telemetry Badge */}
          {liveAdminGps && (
            <div
              style={{
                background: 'rgba(22, 22, 26, 0.94)',
                backdropFilter: 'blur(8px)',
                border: '1px solid #10b981',
                borderRadius: 8,
                padding: '5px 10px',
                display: 'flex',
                alignItems: 'center',
                gap: 6
              }}
            >
              <div style={{ width: 7, height: 7, borderRadius: '50%', background: '#10b981', boxShadow: '0 0 8px #10b981' }} />
              <span style={{ color: '#10b981', fontWeight: 800, fontSize: 10 }}>
                LIVE GPS: {liveAdminGps.lat.toFixed(5)}°N, {liveAdminGps.lng.toFixed(5)}°E (±{liveAdminGps.accuracy}m)
              </span>
            </div>
          )}

          {/* Mode Switcher Tabs */}
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
            {[
              { id: 'live_fleet', label: '⚡ Fleet Radar' },
              { id: 'geofence_editor', label: '🛑 Geofences' },
              { id: 'road_restrictions', label: '⛔ Flyover Exclusions' },
              { id: 'trip_playback', label: '⏱️ Playback' },
              { id: 'sos_incident', label: '🚨 SOS Beacon' }
            ].map(tab => (
              <button
                key={tab.id}
                onClick={() => setActiveMode(tab.id as any)}
                style={{
                  background: activeMode === tab.id ? '#d4af37' : 'transparent',
                  color: activeMode === tab.id ? '#000' : '#8d8672',
                  border: 'none',
                  borderRadius: 6,
                  padding: '4px 8px',
                  fontSize: 10,
                  fontWeight: 700,
                  cursor: 'pointer',
                  transition: 'background 0.2s'
                }}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Quick Global Metropolitan Hub Jump Pills */}
          <div style={{ display: 'flex', gap: 4, overflowX: 'auto', maxWidth: 460 }}>
            {GLOBAL_METROPOLITAN_HUBS.map((lm, idx) => (
              <button
                key={idx}
                onClick={() => handleFlyTo(lm.coords, 14)}
                style={{
                  background: 'rgba(22, 22, 26, 0.85)',
                  border: '1px solid #333',
                  color: '#f2ead3',
                  borderRadius: 6,
                  padding: '3px 7px',
                  fontSize: 9,
                  fontWeight: 600,
                  cursor: 'pointer',
                  whiteSpace: 'nowrap'
                }}
              >
                🌐 {lm.label.split(' ')[0]}
              </button>
            ))}
          </div>
        </div>

        {/* Style Switcher & Fullscreen Controls */}
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
            onClick={() => {
              if (liveAdminGps) {
                handleFlyTo([liveAdminGps.lng, liveAdminGps.lat], 15);
              } else if (vehicles.length > 0) {
                const bounds = new mapboxgl.LngLatBounds();
                vehicles.forEach(v => bounds.extend([v.lng, v.lat]));
                mapInstanceRef.current?.fitBounds(bounds, { padding: 80, duration: 1200 });
              } else {
                handleFlyTo([0, 20], 1.8);
              }
            }}
            title="Recenter to Live Position or Worldwide Fleet"
            style={{
              background: '#16161a',
              border: '1px solid #333',
              borderRadius: 8,
              color: '#d4af37',
              padding: '5px 8px',
              fontSize: 10,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 4
            }}
          >
            <RefreshCw size={11} /> Recenter
          </button>

          <button
            onClick={() => setIsFullscreen(!isFullscreen)}
            title="Toggle Fullscreen"
            style={{
              background: '#16161a',
              border: '1px solid #333',
              borderRadius: 8,
              color: '#d4af37',
              padding: '5px 8px',
              fontSize: 10,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 4
            }}
          >
            <Maximize2 size={11} /> {isFullscreen ? 'Exit' : 'Expand'}
          </button>
        </div>
      </div>

      {/* Sub-HUD: Search & Status Filters for Fleet Radar */}
      {activeMode === 'live_fleet' && (
        <div
          style={{
            position: 'absolute',
            top: 50,
            left: 10,
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            zIndex: 10
          }}
        >
          <form onSubmit={handleSearchLocation} style={{ display: 'flex', gap: 4 }}>
            <input
              type="text"
              placeholder="Search vehicle plate or driver..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              style={{
                background: 'rgba(22, 22, 26, 0.95)',
                border: '1px solid #333',
                color: '#f2ead3',
                borderRadius: 8,
                padding: '5px 10px',
                fontSize: 11,
                width: 220,
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
                padding: '5px 8px',
                cursor: 'pointer',
                fontWeight: 700
              }}
            >
              <Search size={11} />
            </button>
          </form>

          {/* Status Filter Pills */}
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
            {(['all', 'available', 'on_trip', 'en_route'] as const).map(f => (
              <button
                key={f}
                onClick={() => setVehicleFilter(f)}
                style={{
                  background: vehicleFilter === f ? '#d4af37' : 'transparent',
                  color: vehicleFilter === f ? '#000' : '#8d8672',
                  border: 'none',
                  borderRadius: 6,
                  padding: '3px 7px',
                  fontSize: 10,
                  fontWeight: 700,
                  cursor: 'pointer',
                  textTransform: 'capitalize'
                }}
              >
                {f.replace('_', ' ')}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Floating Zoom & Current Location Controls */}
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
          onClick={handleLocateCurrentLocation}
          title="Current Location (GPS Crosshair)"
          style={{
            background: '#16161a',
            border: '1px solid #10b981',
            color: '#10b981',
            width: 32,
            height: 32,
            borderRadius: 8,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            boxShadow: '0 4px 12px rgba(0,0,0,0.5)'
          }}
        >
          <Crosshair size={15} />
        </button>
        <button
          onClick={handleFlyToWorldView}
          title="Worldwide Map View (3D Globe)"
          style={{
            background: '#16161a',
            border: '1px solid #3b82f6',
            color: '#3b82f6',
            width: 32,
            height: 32,
            borderRadius: 8,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            boxShadow: '0 4px 12px rgba(0,0,0,0.5)'
          }}
        >
          <Globe size={15} />
        </button>
        <button
          onClick={handleZoomIn}
          title="Zoom In"
          style={{
            background: '#16161a',
            border: '1px solid #333',
            color: '#f2ead3',
            width: 32,
            height: 32,
            borderRadius: 8,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            boxShadow: '0 4px 12px rgba(0,0,0,0.5)'
          }}
        >
          <ZoomIn size={14} />
        </button>
        <button
          onClick={handleZoomOut}
          title="Zoom Out"
          style={{
            background: '#16161a',
            border: '1px solid #333',
            color: '#f2ead3',
            width: 32,
            height: 32,
            borderRadius: 8,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            boxShadow: '0 4px 12px rgba(0,0,0,0.5)'
          }}
        >
          <ZoomOut size={14} />
        </button>
      </div>

      {/* GEOFENCE EDITOR TOOLBAR */}
      {activeMode === 'geofence_editor' && (
        <div
          style={{
            position: 'absolute',
            bottom: 14,
            left: 12,
            background: 'rgba(22, 22, 26, 0.95)',
            backdropFilter: 'blur(10px)',
            border: '1px solid #d4af37',
            borderRadius: 12,
            padding: '10px 14px',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            zIndex: 10,
            maxWidth: 620,
            boxShadow: '0 6px 20px rgba(0,0,0,0.8)'
          }}
        >
          <div>
            <div style={{ color: '#d4af37', fontSize: 11, fontWeight: 800 }}>
              🛑 GEOFENCE STUDIO: Click map to place vertices ({editorPoints.length} points)
              {editorPoints.length >= 3 && (
                <span style={{ color: '#10b981', marginLeft: 8 }}>
                  ● Est. Area: {calculatePolygonAreaSqKm([...editorPoints, editorPoints[0]]).toFixed(2)} km²
                </span>
              )}
            </div>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginTop: 4 }}>
              <input
                type="text"
                placeholder="Zone Title..."
                value={zoneName}
                onChange={e => setZoneName(e.target.value)}
                style={{
                  background: '#0f0f12',
                  border: '1px solid #333',
                  color: '#f2ead3',
                  borderRadius: 6,
                  padding: '2px 8px',
                  fontSize: 10,
                  width: 150
                }}
              />
              <select
                value={zoneType}
                onChange={e => setZoneType(e.target.value)}
                style={{
                  background: '#0f0f12',
                  border: '1px solid #333',
                  color: '#f2ead3',
                  borderRadius: 6,
                  padding: '2px 6px',
                  fontSize: 10
                }}
              >
                <option value="Surge Zone (+1.5x)">Surge Zone (+1.5x)</option>
                <option value="High Demand (+2.0x)">High Demand (+2.0x)</option>
                <option value="Operational Zone (1.0x)">Operational Zone (1.0x)</option>
                <option value="Restricted Slow Speed">Restricted Slow Speed</option>
                <option value="Prohibited Road">Prohibited Road</option>
              </select>
            </div>
          </div>

          <div style={{ display: 'flex', gap: 6, marginLeft: 'auto' }}>
            <button
              onClick={handleClearEditor}
              style={{
                background: '#222',
                color: '#8d8672',
                border: '1px solid #333',
                borderRadius: 6,
                padding: '5px 10px',
                fontSize: 10,
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
                padding: '5px 12px',
                fontWeight: 700,
                fontSize: 10,
                cursor: 'pointer'
              }}
            >
              Save Polygon
            </button>
          </div>
        </div>
      )}

      {/* RESTRICTED CORRIDORS TOOLBAR */}
      {activeMode === 'road_restrictions' && (
        <div
          style={{
            position: 'absolute',
            bottom: 14,
            left: 12,
            background: 'rgba(22, 22, 26, 0.95)',
            backdropFilter: 'blur(10px)',
            border: '1px solid #ef4444',
            borderRadius: 12,
            padding: '10px 14px',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            zIndex: 10,
            maxWidth: 620,
            boxShadow: '0 6px 20px rgba(0,0,0,0.8)'
          }}
        >
          <div>
            <div style={{ color: '#ef4444', fontSize: 11, fontWeight: 800 }}>
              ⛔ FLYOVER & EXPRESSWAY EXCLUSION STUDIO
            </div>
            <div style={{ color: '#8d8672', fontSize: 10, marginTop: 2 }}>
              Click Start and End points on map to exclude motorized flyovers ({newCorridorPoints.length}/2 placed)
            </div>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginTop: 4 }}>
              <input
                type="text"
                placeholder="Flyover Corridor Name..."
                value={newCorridorName}
                onChange={e => setNewCorridorName(e.target.value)}
                style={{
                  background: '#0f0f12',
                  border: '1px solid #333',
                  color: '#f2ead3',
                  borderRadius: 6,
                  padding: '2px 8px',
                  fontSize: 10,
                  width: 170
                }}
              />
              <select
                value={newCorridorType}
                onChange={e => setNewCorridorType(e.target.value)}
                style={{
                  background: '#0f0f12',
                  border: '1px solid #333',
                  color: '#f2ead3',
                  borderRadius: 6,
                  padding: '2px 6px',
                  fontSize: 10
                }}
              >
                <option value="Motorized Expressway / Flyover">Motorized Flyover (Min Speed 50)</option>
                <option value="Elevated Motorway">Elevated Motorway</option>
                <option value="Tunnel">Tunnel Corridor</option>
                <option value="VIP Security Corridor">VIP Security Corridor</option>
              </select>
            </div>
          </div>

          <div style={{ display: 'flex', gap: 6, marginLeft: 'auto' }}>
            <button
              onClick={() => setNewCorridorPoints([])}
              style={{
                background: '#222',
                color: '#8d8672',
                border: '1px solid #333',
                borderRadius: 6,
                padding: '5px 10px',
                fontSize: 10,
                cursor: 'pointer'
              }}
            >
              Clear
            </button>
            <button
              onClick={handleSaveCorridor}
              style={{
                background: '#ef4444',
                color: '#fff',
                border: 'none',
                borderRadius: 6,
                padding: '5px 12px',
                fontWeight: 700,
                fontSize: 10,
                cursor: 'pointer'
              }}
            >
              Save Corridor
            </button>
          </div>
        </div>
      )}

      {/* HISTORICAL TRIP SCRUBBER & PLAYBACK TOOLBAR */}
      {activeMode === 'trip_playback' && (
        <div
          style={{
            position: 'absolute',
            bottom: 14,
            left: 12,
            right: 54,
            background: 'rgba(22, 22, 26, 0.96)',
            backdropFilter: 'blur(12px)',
            border: '1px solid #d4af37',
            borderRadius: 12,
            padding: '10px 16px',
            zIndex: 10,
            boxShadow: '0 8px 24px rgba(0,0,0,0.85)'
          }}
        >
          {/* Top Scrubber Row: Trip Selector & Telemetry Metrics */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ color: '#d4af37', fontWeight: 800, fontSize: 11 }}>⏱️ TRIP PLAYBACK:</span>
              <select
                value={selectedRideId}
                onChange={e => {
                  setSelectedRideId(e.target.value);
                  fetchTripBreadcrumbs(e.target.value);
                }}
                style={{
                  background: '#0f0f12',
                  border: '1px solid #d4af3766',
                  color: '#d4af37',
                  borderRadius: 6,
                  padding: '2px 8px',
                  fontSize: 10,
                  fontWeight: 700
                }}
              >
                <option value="RIDE-DEL-8801">RIDE-DEL-8801 (CP → India Gate) [Sample Fixture]</option>
                <option value="RIDE-DEL-9923">RIDE-DEL-9923 (NDLS → Chandni Chowk) [Sample Fixture]</option>
                <option value="RIDE-DEL-7714">RIDE-DEL-7714 (Karol Bagh → Janpath) [Sample Fixture]</option>
              </select>
              {tripSource && (
                <span
                  style={{
                    fontSize: 9,
                    fontWeight: 800,
                    padding: '2px 6px',
                    borderRadius: 4,
                    background: tripSource.isSynthetic ? 'rgba(245, 158, 11, 0.15)' : 'rgba(16, 185, 129, 0.15)',
                    color: tripSource.isSynthetic ? '#f59e0b' : '#10b981',
                    border: `1px solid ${tripSource.isSynthetic ? '#f59e0b' : '#10b981'}`
                  }}
                >
                  {tripSource.isSynthetic ? '● DEMONSTRATION SAMPLE TRAJECTORY' : '● VERIFIED PERSISTED GPS BREADCRUMBS'}
                </span>
              )}
              {tripSummary && (
                <span style={{ fontSize: 10, color: '#8d8672' }}>
                  {tripSummary.origin} → {tripSummary.destination} ({tripSummary.distance_km} km)
                </span>
              )}
            </div>

            {/* Current Step Telemetry Gauges */}
            {currentCrumb && (
              <div style={{ display: 'flex', gap: 12, fontSize: 10, color: '#f2ead3' }}>
                <span>Speed: <b style={{ color: '#10b981' }}>{currentCrumb.speed_kmh} km/h</b></span>
                <span>Heading: <b style={{ color: '#d4af37' }}>{currentCrumb.heading}°</b></span>
                <span>Distance: <b style={{ color: '#3b82f6' }}>{currentCrumb.distance_km || (playbackIndex * 0.15).toFixed(2)} km</b></span>
                <span style={{ color: '#8d8672' }}>
                  Step: {playbackIndex + 1}/{breadcrumbs.length}
                </span>
              </div>
            )}
          </div>

          {/* Scrubber Slider */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 8 }}>
            <button
              onClick={() => setIsPlaying(!isPlaying)}
              style={{
                background: isPlaying ? '#ef4444' : '#d4af37',
                color: isPlaying ? '#fff' : '#000',
                border: 'none',
                borderRadius: 6,
                padding: '4px 10px',
                cursor: 'pointer',
                fontWeight: 800,
                fontSize: 11,
                display: 'flex',
                alignItems: 'center',
                gap: 4
              }}
            >
              {isPlaying ? <Pause size={12} /> : <Play size={12} />}
              {isPlaying ? 'Pause' : 'Play'}
            </button>

            <button
              onClick={() => {
                setPlaybackIndex(0);
                setIsPlaying(false);
              }}
              title="Reset to Start"
              style={{
                background: '#222',
                border: '1px solid #444',
                color: '#8d8672',
                borderRadius: 6,
                padding: '4px 8px',
                fontSize: 10,
                cursor: 'pointer'
              }}
            >
              <RotateCcw size={11} />
            </button>

            {/* Range Slider */}
            <input
              type="range"
              min={0}
              max={Math.max(0, breadcrumbs.length - 1)}
              value={playbackIndex}
              onChange={e => {
                setPlaybackIndex(parseInt(e.target.value, 10));
              }}
              style={{
                flex: 1,
                accentColor: '#d4af37',
                cursor: 'pointer'
              }}
            />

            {/* Playback Speed Multipliers */}
            <div
              style={{
                background: '#16161a',
                border: '1px solid #333',
                borderRadius: 6,
                padding: '1px',
                display: 'flex',
                gap: 2
              }}
            >
              {[1, 2, 4].map(s => (
                <button
                  key={s}
                  onClick={() => setPlaybackSpeed(s)}
                  style={{
                    background: playbackSpeed === s ? '#d4af37' : 'transparent',
                    color: playbackSpeed === s ? '#000' : '#8d8672',
                    border: 'none',
                    borderRadius: 4,
                    padding: '2px 6px',
                    fontSize: 9,
                    fontWeight: 700,
                    cursor: 'pointer'
                  }}
                >
                  {s}x
                </button>
              ))}
            </div>

            <button
              onClick={() => setIsLooping(!isLooping)}
              style={{
                background: isLooping ? '#d4af3733' : 'transparent',
                border: '1px solid #333',
                color: isLooping ? '#d4af37' : '#8d8672',
                borderRadius: 6,
                padding: '3px 7px',
                fontSize: 9,
                fontWeight: 600,
                cursor: 'pointer'
              }}
            >
              Loop: {isLooping ? 'ON' : 'OFF'}
            </button>
          </div>
        </div>
      )}

      {/* Selected Vehicle Telemetry Card */}
      {selectedVehicle && (
        <div
          style={{
            position: 'absolute',
            bottom: 14,
            left: 12,
            background: 'rgba(22, 22, 26, 0.96)',
            backdropFilter: 'blur(10px)',
            border: '1px solid #d4af37',
            borderRadius: 12,
            padding: '12px 16px',
            zIndex: 10,
            minWidth: 260,
            boxShadow: '0 8px 24px rgba(0,0,0,0.85)'
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ color: '#d4af37', fontWeight: 800, fontSize: 13 }}>{selectedVehicle.plate}</span>
            <button
              onClick={() => setSelectedVehicle(null)}
              style={{ background: 'transparent', border: 'none', color: '#8d8672', cursor: 'pointer', fontSize: 14, marginLeft: 'auto' }}
            >
              ✕
            </button>
          </div>
          <div style={{ color: '#f2ead3', fontSize: 11, marginTop: 4 }}>Chauffeur: <b>{selectedVehicle.driver}</b></div>
          <div style={{ color: '#8d8672', fontSize: 10, marginTop: 2 }}>Model: {selectedVehicle.model}</div>
          <div style={{ color: '#8d8672', fontSize: 10, marginTop: 2 }}>
            Battery: <b style={{ color: selectedVehicle.battery > 20 ? '#10b981' : '#ef4444' }}>{selectedVehicle.battery}%</b> | Speed: <b style={{ color: '#f2ead3' }}>{selectedVehicle.speed} km/h</b>
          </div>
          {(() => {
            const freshness = getLocationFreshness(selectedVehicle.last_seen);
            return (
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 4 }}>
                <span style={{ fontSize: 9, color: '#8d8672' }}>GPS Telemetry:</span>
                <span
                  style={{
                    fontSize: 9,
                    fontWeight: 800,
                    padding: '2px 6px',
                    borderRadius: 4,
                    background: 'rgba(0,0,0,0.5)',
                    color: freshness.color,
                    border: `1px solid ${freshness.color}`
                  }}
                >
                  ● {freshness.label}
                </span>
              </div>
            );
          })()}
          {selectedVehicle.passenger && (
            <div style={{ marginTop: 6, padding: '4px 6px', background: '#0f0f12', borderRadius: 6, fontSize: 10, color: '#d4af37' }}>
              Active Rider: {selectedVehicle.passenger} (Fare: ₹{selectedVehicle.fare})
            </div>
          )}
          <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
            <button
              onClick={() => handleFlyTo([selectedVehicle.lng, selectedVehicle.lat], 17)}
              style={{
                background: '#d4af37',
                color: '#000',
                border: 'none',
                borderRadius: 6,
                padding: '4px 10px',
                fontSize: 10,
                fontWeight: 700,
                cursor: 'pointer'
              }}
            >
              Center on Vehicle
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
