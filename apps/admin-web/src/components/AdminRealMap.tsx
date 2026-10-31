import React, { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { ZoomIn, ZoomOut, Maximize2, RefreshCw } from 'lucide-react';

export interface AdminRealMapProps {
  mode?: 'live_fleet' | 'geofence_editor' | 'road_restrictions' | 'mini';
  height?: string | number;
  selectedScreenTitle?: string;
  onPolygonCreated?: (coords: [number, number][], zoneType: string) => void;
  className?: string;
}

interface FleetVehicle {
  id: string;
  plate: string;
  driver: string;
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
    lat: 28.6200,
    lng: 77.2270,
    battery: 14,
    speed: 0,
    status: 'available'
  }
];

// Connaught Place & Central Delhi Geofence Polygons
const GEOFENCE_ZONES: { name: string; type: string; color: string; coords: [number, number][] }[] = [
  {
    name: 'Connaught Place Prime Inner Corridor',
    type: 'Operational Zone',
    color: '#d4af37',
    coords: [
      [28.6355, 77.2135],
      [28.6355, 77.2205],
      [28.6275, 77.2205],
      [28.6275, 77.2135]
    ]
  },
  {
    name: 'New Delhi Railway Station Gate 1 & 2',
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
    name: 'Heritage High-Density Pedestrian Zone',
    type: 'Commercial EV Restricted',
    color: '#ef4444',
    coords: [
      [28.6530, 77.2300],
      [28.6530, 77.2380],
      [28.6470, 77.2380],
      [28.6470, 77.2300]
    ]
  }
];

// Restricted Road Segments
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

export const AdminRealMap: React.FC<AdminRealMapProps> = ({
  mode = 'live_fleet',
  height = '420px',
  selectedScreenTitle = 'Live Fleet Map',
  onPolygonCreated,
  className = ''
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const markersLayerRef = useRef<L.LayerGroup | null>(null);
  const geofenceLayerRef = useRef<L.LayerGroup | null>(null);
  const editorLayerRef = useRef<L.LayerGroup | null>(null);

  const [editorPoints, setEditorPoints] = useState<[number, number][]>([]);
  const [zoneType, setZoneType] = useState<string>('Surge Zone');
  const [selectedVehicle, setSelectedVehicle] = useState<FleetVehicle | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

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

    // CartoDB Dark Matter Tiles (High performance luxury dark tiles)
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      maxZoom: 19,
      subdomains: 'abcd',
      opacity: 0.95
    }).addTo(map);

    // Layer Groups
    const geofenceGroup = L.layerGroup().addTo(map);
    const markersGroup = L.layerGroup().addTo(map);
    const editorGroup = L.layerGroup().addTo(map);

    geofenceLayerRef.current = geofenceGroup;
    markersLayerRef.current = markersGroup;
    editorLayerRef.current = editorGroup;

    // 1. Render Geofence Polygons
    GEOFENCE_ZONES.forEach(zone => {
      const polygon = L.polygon(zone.coords, {
        color: zone.color,
        fillColor: zone.color,
        fillOpacity: 0.18,
        weight: 2,
        dashArray: zone.type.includes('Restricted') ? '6, 6' : undefined
      });

      polygon.bindPopup(`
        <div style="font-family: system-ui; padding: 4px;">
          <b style="color: ${zone.color}; font-size: 13px;">${zone.name}</b>
          <div style="font-size: 11px; color: #8d8672; margin-top: 2px;">Zone Classification: ${zone.type}</div>
          <div style="font-size: 11px; color: #f2ead3; margin-top: 4px;">Status: Active Geofence Rule</div>
        </div>
      `);
      geofenceGroup.addLayer(polygon);
    });

    // 2. Render Road Restrictions
    RESTRICTED_ROADS.forEach(road => {
      const polyline = L.polyline(road.coords, {
        color: '#ef4444',
        weight: 5,
        opacity: 0.85,
        dashArray: '8, 8'
      });
      polyline.bindPopup(`
        <div style="font-family: system-ui; padding: 4px;">
          <b style="color: #ef4444; font-size: 12px;">🚫 RESTRICTED CORRIDOR</b>
          <div style="font-size: 11px; color: #f2ead3; margin-top: 2px;">${road.name}</div>
          <div style="font-size: 10px; color: #8d8672;">Type: ${road.type}</div>
        </div>
      `);
      geofenceGroup.addLayer(polyline);
    });

    // 3. Render Sample Fleet Vehicles with Custom Gold SVG Pins
    SAMPLE_VEHICLES.forEach(v => {
      const statusColor = v.status === 'on_trip' ? '#d4af37' : v.status === 'en_route' ? '#3b82f6' : '#10b981';
      const iconHtml = `
        <div style="
          width: 32px;
          height: 32px;
          border-radius: 50%;
          background: #16161a;
          border: 2px solid ${statusColor};
          box-shadow: 0 0 10px ${statusColor}66;
          display: flex;
          align-items: center;
          justify-content: center;
          color: ${statusColor};
          font-weight: 800;
          font-size: 10px;
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
        <div style="font-family: system-ui; min-width: 180px; padding: 6px;">
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <b style="color: #d4af37; font-size: 13px;">${v.plate}</b>
            <span style="font-size: 10px; color: ${statusColor}; font-weight: bold; text-transform: uppercase;">${v.status.replace('_', ' ')}</span>
          </div>
          <div style="font-size: 11px; color: #f2ead3; margin-top: 4px;">Chauffeur: ${v.driver}</div>
          <div style="font-size: 11px; color: #8d8672;">Battery: <b style="color: ${v.battery > 20 ? '#10b981' : '#ef4444'}">${v.battery}%</b> | Speed: ${v.speed} km/h</div>
          ${v.passenger ? `<div style="margin-top: 6px; padding: 4px 6px; background: #0f0f12; border-radius: 4px; font-size: 10px; color: #d4af37;">Rider: ${v.passenger} · Fare: ₹${v.fare}</div>` : ''}
        </div>
      `);

      marker.on('click', () => {
        setSelectedVehicle(v);
      });

      markersGroup.addLayer(marker);
    });

    // 4. Geofence Editor Interactive Click Handler
    if (mode === 'geofence_editor') {
      map.on('click', (e: L.LeafletMouseEvent) => {
        const newCoord: [number, number] = [e.latlng.lat, e.latlng.lng];
        setEditorPoints(prev => {
          const updated = [...prev, newCoord];
          // Re-draw in editor layer
          editorGroup.clearLayers();
          updated.forEach(p => {
            const dot = L.circleMarker(p, { radius: 5, color: '#d4af37', fillColor: '#d4af37', fillOpacity: 1 });
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

    // Invalidate size on mount to prevent grey tile bug
    setTimeout(() => {
      map.invalidateSize();
    }, 200);

    return () => {
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

  const handleResetCenter = () => {
    mapInstanceRef.current?.setView([28.6328, 77.2195], 14);
  };

  const handleClearEditor = () => {
    setEditorPoints([]);
    editorLayerRef.current?.clearLayers();
  };

  const handleSaveGeofence = () => {
    if (editorPoints.length < 3) {
      alert('Please click on the map to define at least 3 perimeter points for the geofence polygon.');
      return;
    }
    if (onPolygonCreated) {
      onPolygonCreated(editorPoints, zoneType);
    }
    alert(`Successfully registered new Geofence (${zoneType}) with ${editorPoints.length} boundary coordinates.`);
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
      {/* Real Map Leaflet DOM Container */}
      <div
        ref={mapContainerRef}
        style={{ width: '100%', height: '100%', background: '#0b0b0d' }}
      />

      {/* Top Map HUD Bar */}
      <div
        style={{
          position: 'absolute',
          top: 12,
          left: 12,
          right: 12,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          pointerEvents: 'none',
          zIndex: 1000
        }}
      >
        <div
          style={{
            background: 'rgba(22, 22, 26, 0.92)',
            backdropFilter: 'blur(8px)',
            border: '1px solid #2a2a2e',
            borderRadius: 10,
            padding: '6px 14px',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            pointerEvents: 'auto'
          }}
        >
          <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#10b981', boxShadow: '0 0 6px #10b981' }} />
          <span style={{ color: '#d4af37', fontWeight: 700, fontSize: 12 }}>
            REAL MAP ENGINE: {selectedScreenTitle.toUpperCase()}
          </span>
          <span style={{ color: '#8d8672', fontSize: 11 }}>
            · Central Delhi GPS Grid (28.63° N, 77.21° E)
          </span>
        </div>

        {/* Action Controls */}
        <div style={{ display: 'flex', gap: 6, pointerEvents: 'auto' }}>
          <button
            onClick={handleResetCenter}
            title="Recenter Map"
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

      {/* Floating Zoom & Layer Controls */}
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
            cursor: 'pointer'
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
            cursor: 'pointer'
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
            background: 'rgba(22, 22, 26, 0.94)',
            backdropFilter: 'blur(8px)',
            border: '1px solid #d4af37',
            borderRadius: 12,
            padding: '12px 16px',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            zIndex: 1000,
            maxWidth: 540
          }}
        >
          <div>
            <div style={{ color: '#d4af37', fontSize: 12, fontWeight: 700 }}>
              Draw Geofence: Click map to place perimeter vertices ({editorPoints.length} points)
            </div>
            <div style={{ color: '#8d8672', fontSize: 11 }}>
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
                <option value="Prohibited Road">Prohibited / Restricted</option>
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
                padding: '5px 10px',
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
                padding: '5px 12px',
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
            padding: '12px 16px',
            zIndex: 1000,
            minWidth: 260
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ color: '#d4af37', fontWeight: 800, fontSize: 13 }}>{selectedVehicle.plate}</span>
            <button
              onClick={() => setSelectedVehicle(null)}
              style={{ background: 'transparent', border: 'none', color: '#8d8672', cursor: 'pointer', fontSize: 13 }}
            >
              ✕
            </button>
          </div>
          <div style={{ color: '#f2ead3', fontSize: 12, marginTop: 4 }}>Chauffeur: {selectedVehicle.driver}</div>
          <div style={{ color: '#8d8672', fontSize: 11, marginTop: 2 }}>
            Battery: <b style={{ color: selectedVehicle.battery > 20 ? '#10b981' : '#ef4444' }}>{selectedVehicle.battery}%</b> | Speed: {selectedVehicle.speed} km/h
          </div>
          {selectedVehicle.passenger && (
            <div style={{ marginTop: 6, padding: '4px 8px', background: '#0f0f12', borderRadius: 6, fontSize: 11, color: '#d4af37' }}>
              Active Rider: {selectedVehicle.passenger} (Fare: ₹{selectedVehicle.fare})
            </div>
          )}
        </div>
      )}
    </div>
  );
};
