import React, { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
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

const SAMPLE_VEHICLES: FleetVehicle[] = [
  {
    id: 'V1',
    plate: 'DL-1ER-4821',
    driver: 'Suresh Kumar',
    model: 'Saarthi E-Rickshaw Pro',
    lat: 28.6315,
    lng: 77.2167,
    battery: 84,
    speed: 24,
    status: 'on_trip',
    passenger: 'Priya Sharma',
    fare: 145
  },
  {
    id: 'V2',
    plate: 'DL-1ER-9012',
    driver: 'Rajesh Verma',
    model: 'Mayuri Eco Deluxe',
    lat: 28.6412,
    lng: 77.2215,
    battery: 92,
    speed: 18,
    status: 'en_route',
    passenger: 'Amit Verma',
    fare: 110
  },
  {
    id: 'V3',
    plate: 'DL-1ER-3391',
    driver: 'Manoj Singh',
    model: 'Mahindra Treo Auto',
    lat: 28.6250,
    lng: 77.2100,
    battery: 76,
    speed: 0,
    status: 'available'
  },
  {
    id: 'V4',
    plate: 'DL-1ER-7714',
    driver: 'Sunil Batra',
    model: 'Euler HiLoad EV',
    lat: 28.6358,
    lng: 77.2285,
    battery: 41,
    speed: 22,
    status: 'on_trip',
    passenger: 'Rohan Kapoor',
    fare: 160
  },
  {
    id: 'V5',
    plate: 'DL-1ER-1092',
    driver: 'Dinesh Yadav',
    model: 'Saarthi Star EV',
    lat: 28.6200,
    lng: 77.2270,
    battery: 89,
    speed: 12,
    status: 'available'
  },
  {
    id: 'V6',
    plate: 'DL-1ER-5502',
    driver: 'Anil Gupta',
    model: 'Saarthi E-Rickshaw Pro',
    lat: 28.6480,
    lng: 77.2260,
    battery: 68,
    speed: 19,
    status: 'on_trip',
    passenger: 'Deepak Roy',
    fare: 85
  }
];

const GEOFENCE_ZONES: { name: string; type: string; color: string; coords: [number, number][] }[] = [
  {
    name: 'Connaught Place Inner & Outer Circle',
    type: 'Operational Zone (High Demand)',
    color: '#d4af37',
    coords: [
      [28.6360, 77.2135],
      [28.6360, 77.2225],
      [28.6270, 77.2225],
      [28.6270, 77.2135]
    ]
  },
  {
    name: 'New Delhi Railway Station (NDLS) Feeder',
    type: 'Surge Zone (+1.8x)',
    color: '#f59e0b',
    coords: [
      [28.6445, 77.2185],
      [28.6445, 77.2260],
      [28.6390, 77.2260],
      [28.6390, 77.2185]
    ]
  },
  {
    name: 'Chandni Chowk Heritage Pedestrian Zone',
    type: 'Commercial EV Restricted',
    color: '#ef4444',
    coords: [
      [28.6540, 77.2280],
      [28.6540, 77.2370],
      [28.6480, 77.2370],
      [28.6480, 77.2280]
    ]
  }
];

const RESTRICTED_ROADS: { name: string; type: string; coords: [number, number][] }[] = [
  {
    name: 'Minto Road Underpass (Heavy Waterlogging Caution)',
    type: 'Hazard Prohibited',
    coords: [
      [28.6385, 77.2230],
      [28.6405, 77.2245]
    ]
  },
  {
    name: 'Barakhamba Flyover (E-Rickshaw High-Speed Restriction)',
    type: 'Motorized Expressway',
    coords: [
      [28.6300, 77.2240],
      [28.6260, 77.2265]
    ]
  }
];

// Quick Jump Landmark Coordinates across Delhi NCR
const DELHI_LANDMARKS = [
  { label: 'Connaught Place', coords: [28.6328, 77.2195] as [number, number] },
  { label: 'NDLS Railway', coords: [28.6425, 77.2210] as [number, number] },
  { label: 'Chandni Chowk', coords: [28.6505, 77.2300] as [number, number] },
  { label: 'India Gate', coords: [28.6129, 77.2295] as [number, number] },
  { label: 'Khan Market', coords: [28.6000, 77.2270] as [number, number] },
  { label: 'Karol Bagh', coords: [28.6515, 77.1905] as [number, number] }
];

export const AdminRealMap: React.FC<AdminRealMapProps> = ({
  mode = 'live_fleet',
  height = '480px',
  selectedScreenTitle = 'Live Fleet Map',
  onPolygonCreated,
  className = ''
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const tileLayerRef = useRef<L.TileLayer | null>(null);
  const markersLayerRef = useRef<L.LayerGroup | null>(null);
  const geofenceLayerRef = useRef<L.LayerGroup | null>(null);
  const editorLayerRef = useRef<L.LayerGroup | null>(null);

  const [tileStyle, setTileStyle] = useState<'dark' | 'streets' | 'satellite'>('dark');
  const [editorPoints, setEditorPoints] = useState<[number, number][]>([]);
  const [zoneType, setZoneType] = useState<string>('Surge Zone (+1.5x)');
  const [selectedVehicle, setSelectedVehicle] = useState<FleetVehicle | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Tile layer URLs
  const TILE_URLS = {
    dark: 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
    streets: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    satellite: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'
  };

  // Change tile style dynamically
  const switchTileStyle = (style: 'dark' | 'streets' | 'satellite') => {
    setTileStyle(style);
    if (mapInstanceRef.current && tileLayerRef.current) {
      mapInstanceRef.current.removeLayer(tileLayerRef.current);
      const newLayer = L.tileLayer(TILE_URLS[style], {
        maxZoom: 19,
        subdomains: style === 'streets' ? 'abc' : 'abcd',
        opacity: style === 'satellite' ? 0.9 : 0.95
      }).addTo(mapInstanceRef.current);
      tileLayerRef.current = newLayer;
    }
  };

  useEffect(() => {
    if (!mapContainerRef.current) return;

    // Prevent duplicate map initialization
    if (mapInstanceRef.current) {
      mapInstanceRef.current.remove();
      mapInstanceRef.current = null;
    }

    // Initialize Leaflet Map centered on Central Delhi (Connaught Place)
    const map = L.map(mapContainerRef.current, {
      center: [28.6328, 77.2195],
      zoom: 14,
      zoomControl: false,
      attributionControl: false
    });
    mapInstanceRef.current = map;

    // Default Tile Layer
    const tileLayer = L.tileLayer(TILE_URLS[tileStyle], {
      maxZoom: 19,
      subdomains: 'abcd',
      opacity: 0.95
    }).addTo(map);
    tileLayerRef.current = tileLayer;

    // Layer Groups
    const geofenceGroup = L.layerGroup().addTo(map);
    const markersGroup = L.layerGroup().addTo(map);
    const editorGroup = L.layerGroup().addTo(map);

    geofenceLayerRef.current = geofenceGroup;
    markersLayerRef.current = markersGroup;
    editorLayerRef.current = editorGroup;

    // 1. Render Geofence Polygons (if in live_fleet or geofence mode)
    if (mode === 'live_fleet' || mode === 'geofence_editor' || mode === 'mini') {
      GEOFENCE_ZONES.forEach(zone => {
        const polygon = L.polygon(zone.coords, {
          color: zone.color,
          fillColor: zone.color,
          fillOpacity: 0.18,
          weight: 2,
          dashArray: zone.type.includes('Restricted') ? '6, 6' : undefined
        });

        polygon.bindPopup(`
          <div style="font-family: system-ui; padding: 6px; background: #16161a; color: #f2ead3; border-radius: 8px;">
            <b style="color: ${zone.color}; font-size: 13px;">${zone.name}</b>
            <div style="font-size: 11px; color: #8d8672; margin-top: 3px;">Zone Classification: ${zone.type}</div>
            <div style="font-size: 11px; color: #10b981; margin-top: 4px;">● Status: Active Operational Geofence</div>
          </div>
        `);
        geofenceGroup.addLayer(polygon);
      });
    }

    // 2. Render Road Restrictions (if live_fleet or road_restrictions mode)
    if (mode === 'live_fleet' || mode === 'road_restrictions') {
      RESTRICTED_ROADS.forEach(road => {
        const polyline = L.polyline(road.coords, {
          color: '#ef4444',
          weight: 6,
          opacity: 0.9,
          dashArray: '8, 8'
        });
        polyline.bindPopup(`
          <div style="font-family: system-ui; padding: 6px; background: #16161a; color: #f2ead3; border-radius: 8px;">
            <b style="color: #ef4444; font-size: 12px;">🚫 RESTRICTED CORRIDOR</b>
            <div style="font-size: 11px; color: #f2ead3; margin-top: 2px;">${road.name}</div>
            <div style="font-size: 10px; color: #8d8672;">Type: ${road.type}</div>
          </div>
        `);
        geofenceGroup.addLayer(polyline);
      });
    }

    // 3. Render Fleet Vehicles with Custom Gold SVG Pins (for live_fleet & mini)
    if (mode === 'live_fleet' || mode === 'mini') {
      SAMPLE_VEHICLES.forEach(v => {
        const statusColor = v.status === 'on_trip' ? '#d4af37' : v.status === 'en_route' ? '#3b82f6' : '#10b981';
        const iconHtml = `
          <div style="
            width: 32px;
            height: 32px;
            border-radius: 50%;
            background: #16161a;
            border: 2px solid ${statusColor};
            box-shadow: 0 0 12px ${statusColor}88;
            display: flex;
            align-items: center;
            justify-content: center;
            color: ${statusColor};
            font-weight: 800;
            font-size: 12px;
            cursor: pointer;
          ">
            ⚡
          </div>
        `;

        const customIcon = L.divIcon({
          html: iconHtml,
          className: 'luxury-rickshaw-icon',
          iconSize: [32, 32],
          iconAnchor: [16, 16]
        });

        const marker = L.marker([v.lat, v.lng], { icon: customIcon });
        marker.bindPopup(`
          <div style="font-family: system-ui; min-width: 200px; padding: 6px; background: #16161a; color: #f2ead3; border-radius: 8px;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <b style="color: #d4af37; font-size: 13px;">${v.plate}</b>
              <span style="font-size: 10px; color: ${statusColor}; font-weight: bold; text-transform: uppercase;">${v.status.replace('_', ' ')}</span>
            </div>
            <div style="font-size: 11px; color: #f2ead3; margin-top: 4px;">Chauffeur: ${v.driver} (${v.model})</div>
            <div style="font-size: 11px; color: #8d8672; margin-top: 2px;">Battery: <b style="color: ${v.battery > 20 ? '#10b981' : '#ef4444'}">${v.battery}%</b> | Speed: ${v.speed} km/h</div>
            ${v.passenger ? `<div style="margin-top: 6px; padding: 4px 6px; background: #0f0f12; border-radius: 4px; font-size: 10px; color: #d4af37;">Rider: ${v.passenger} · Fare: ₹${v.fare}</div>` : ''}
          </div>
        `);

        marker.on('click', () => {
          setSelectedVehicle(v);
        });

        markersGroup.addLayer(marker);
      });
    }

    // 4. Specialized: Ride Route Mode (Screen 08)
    if (mode === 'ride_route') {
      const pickupCoords: [number, number] = [28.6328, 77.2195]; // CP Metro Gate 2
      const dropCoords: [number, number] = [28.6000, 77.2270];   // Khan Market
      const waypoints: [number, number][] = [
        pickupCoords,
        [28.6250, 77.2230],
        [28.6150, 77.2250],
        [28.6070, 77.2260],
        dropCoords
      ];

      // Route Polyline
      const routeLine = L.polyline(waypoints, {
        color: '#d4af37',
        weight: 5,
        opacity: 0.9,
        lineJoin: 'round'
      }).addTo(markersGroup);

      // Pickup Marker (Green)
      const pickupIcon = L.divIcon({
        html: `<div style="background:#10b981; color:#000; font-weight:900; font-size:10px; padding:3px 6px; border-radius:6px; box-shadow:0 0 8px #10b981; border:1px solid #fff;">PICKUP</div>`,
        className: 'route-pin-pickup',
        iconAnchor: [24, 12]
      });
      L.marker(pickupCoords, { icon: pickupIcon }).bindPopup('<b>Pickup Location</b><br/>CP Metro Gate 2').addTo(markersGroup);

      // Dropoff Marker (Gold)
      const dropIcon = L.divIcon({
        html: `<div style="background:#d4af37; color:#000; font-weight:900; font-size:10px; padding:3px 6px; border-radius:6px; box-shadow:0 0 8px #d4af37; border:1px solid #fff;">DROPOFF</div>`,
        className: 'route-pin-drop',
        iconAnchor: [28, 12]
      });
      L.marker(dropCoords, { icon: dropIcon }).bindPopup('<b>Destination</b><br/>Khan Market Entrance').addTo(markersGroup);

      // Fit route bounds
      map.fitBounds(routeLine.getBounds(), { padding: [50, 50] });
    }

    // 5. Specialized: SOS Incident Mode (Screen 25)
    if (mode === 'sos_incident') {
      const incidentCoords: [number, number] = [28.6385, 77.2230];
      const sosIcon = L.divIcon({
        html: `
          <div style="position:relative; width:44px; height:44px; display:flex; align-items:center; justify-content:center;">
            <div style="position:absolute; width:44px; height:44px; border-radius:50%; background:rgba(239,68,68,0.4); animation:ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite;"></div>
            <div style="width:28px; height:28px; border-radius:50%; background:#ef4444; color:#fff; font-weight:900; font-size:13px; display:flex; align-items:center; justify-content:center; box-shadow:0 0 14px #ef4444; border:2px solid #fff;">
              SOS
            </div>
          </div>
        `,
        className: 'sos-incident-pin',
        iconAnchor: [22, 22]
      });

      L.marker(incidentCoords, { icon: sosIcon }).bindPopup(`
        <div style="font-family:system-ui; background:#16161a; color:#fff; padding:8px; border-radius:8px;">
          <b style="color:#ef4444; font-size:13px;">🚨 CRITICAL SOS BEACON</b>
          <div style="font-size:11px; margin-top:4px;">Chauffeur: Suresh Kumar (DL-1ER-4821)</div>
          <div style="font-size:11px; color:#f59e0b; margin-top:2px;">Location: Connaught Place Radial 4 Underpass</div>
          <div style="font-size:10px; color:#10b981; margin-top:4px;">● Govt 112 Dispatch Contacted</div>
        </div>
      `).addTo(markersGroup).openPopup();

      map.setView(incidentCoords, 16);
    }

    // 6. Interactive Geofence Editor Click Handler
    if (mode === 'geofence_editor') {
      map.on('click', (e: L.LeafletMouseEvent) => {
        const newCoord: [number, number] = [e.latlng.lat, e.latlng.lng];
        setEditorPoints(prev => {
          const updated = [...prev, newCoord];
          editorGroup.clearLayers();
          updated.forEach(p => {
            const dot = L.circleMarker(p, { radius: 6, color: '#d4af37', fillColor: '#d4af37', fillOpacity: 1 });
            editorGroup.addLayer(dot);
          });
          if (updated.length >= 2) {
            const line = L.polyline(updated, { color: '#d4af37', weight: 2, dashArray: '4, 4' });
            editorGroup.addLayer(line);
          }
          if (updated.length >= 3) {
            const poly = L.polygon(updated, { color: '#d4af37', fillColor: '#d4af37', fillOpacity: 0.25 });
            editorGroup.addLayer(poly);
          }
          return updated;
        });
      });
    }

    // Invalidate size on mount to eliminate gray tile glitches
    const tId = setTimeout(() => {
      map.invalidateSize();
    }, 200);

    return () => {
      clearTimeout(tId);
      map.remove();
      mapInstanceRef.current = null;
    };
  }, [mode]);

  const handleZoomIn = () => {
    mapInstanceRef.current?.zoomIn();
  };

  const handleZoomOut = () => {
    mapInstanceRef.current?.zoomOut();
  };

  const handleFlyTo = (coords: [number, number], zoom = 15) => {
    mapInstanceRef.current?.flyTo(coords, zoom, { duration: 1.2 });
  };

  const handleSearchLocation = (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    const lower = searchQuery.trim().toLowerCase();
    const match = DELHI_LANDMARKS.find(l => l.label.toLowerCase().includes(lower));
    if (match) {
      handleFlyTo(match.coords, 16);
    } else {
      // Default to central Delhi
      handleFlyTo([28.6328, 77.2195], 15);
    }
  };

  const handleClearEditor = () => {
    setEditorPoints([]);
    editorLayerRef.current?.clearLayers();
  };

  const handleSaveGeofence = () => {
    if (editorPoints.length < 3) {
      alert('Please click on the real map to place at least 3 boundary vertices.');
      return;
    }
    if (onPolygonCreated) {
      onPolygonCreated(editorPoints, zoneType);
    }
    alert(`Successfully registered new Geofence (${zoneType}) with ${editorPoints.length} GPS boundary points!`);
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
      {/* Real Map Leaflet DOM Canvas */}
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
          zIndex: 1000
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
              REAL GPS ENGINE · {selectedScreenTitle.toUpperCase()}
            </span>
          </div>

          {/* Quick Landmark Jump Pills */}
          <div style={{ display: 'flex', gap: 4, overflowX: 'auto', maxWidth: 440 }}>
            {DELHI_LANDMARKS.slice(0, 4).map((lm, idx) => (
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

        {/* Map Actions: Tile Switcher & Fullscreen */}
        <div style={{ display: 'flex', gap: 6, pointerEvents: 'auto' }}>
          {/* Tile Layer Selector */}
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
            onClick={() => handleFlyTo([28.6328, 77.2195], 14)}
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

      {/* Search Input Bar (Bottom-Left) */}
      <div
        style={{
          position: 'absolute',
          top: 56,
          left: 12,
          zIndex: 1000
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
          zIndex: 1000
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
            zIndex: 1000,
            maxWidth: 560,
            boxShadow: '0 6px 20px rgba(0,0,0,0.8)'
          }}
        >
          <div>
            <div style={{ color: '#d4af37', fontSize: 12, fontWeight: 700 }}>
              Interactive Geofence Editor: Click map to place perimeter vertices ({editorPoints.length} points)
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
            zIndex: 1000,
            minWidth: 280,
            boxShadow: '0 8px 24px rgba(0,0,0,0.8)'
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ color: '#d4af37', fontWeight: 800, fontSize: 13 }}>{selectedVehicle.plate}</span>
            <button
              onClick={() => setSelectedVehicle(null)}
              style={{ background: 'transparent', border: 'none', color: '#8d8672', cursor: 'pointer', fontSize: 14 }}
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
