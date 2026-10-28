import React, { useCallback, useEffect, useState } from "react";
import {
  MapPin, ShieldAlert, Activity, Navigation,
  Download, RefreshCw, Battery, CheckCircle
} from "lucide-react";
import { fetchWithAuth } from "../services/api";

type Cluster = { latitude: number; longitude: number; intensity: number; count: number };
type HeatMap = { clusters: Cluster[]; metric: string; window_hours: number; time_filter?: string; minimum_group_size: number; generated_at: string; privacy: string; empty_reason?: string | null };
type Quality = { events: number; mean_accuracy_m: number | null; poor_accuracy_events: number; poor_accuracy_rate_pct: number; risk_signals: number; quality_flags: Record<string, number>; window_hours: number };
type ProviderHealth = { provider_name: string; status: string; probe_latency_ms: number; is_live_provider: boolean; quota_status: string; road_closure_feed: string; attribution: string; disclosure: string };
type RetentionSetting = { id: string; category: string; retention_ttl_days: number; auto_purge_enabled: boolean; last_purged_at: string | null };
type SpoofSignal = { event_id: string; driver_id: string; chauffeur_name: string; recorded_at: string; latitude: number; longitude: number; speed_mps: number; accuracy_m: number; spoof_risk_score: number; quality_flags: string[]; risk_severity: string };
type RoadRestriction = { id: string; title: string; description: string; restriction_type: string; latitude: number; longitude: number; radius_meters: number; starts_at: string; ends_at: string | null };

const panel: React.CSSProperties = {
  background: "#11151d",
  border: "1px solid rgba(212,175,55,0.2)",
  borderRadius: 14,
  padding: 20,
  color: "#eee"
};

const selectStyle: React.CSSProperties = {
  background: "#090d13",
  color: "#eee",
  border: "1px solid #45413a",
  borderRadius: 8,
  padding: "8px 12px",
  fontSize: "0.85rem"
};

const tabBtn = (active: boolean): React.CSSProperties => ({
  background: active ? "rgba(212,175,55,0.15)" : "transparent",
  color: active ? "#d4af37" : "#9ca3af",
  border: active ? "1px solid #d4af37" : "1px solid transparent",
  padding: "8px 16px",
  borderRadius: 8,
  cursor: "pointer",
  fontWeight: 600,
  fontSize: "0.85rem",
  display: "flex",
  alignItems: "center",
  gap: 6
});

export const LocationOperationsConsole: React.FC = () => {
  const [activeTab, setActiveTab] = useState<"diagnostics" | "heatmaps" | "quality" | "geofences">("diagnostics");
  
  // Data states
  const [health, setHealth] = useState<ProviderHealth | null>(null);
  const [heatmap, setHeatmap] = useState<HeatMap | null>(null);
  const [quality, setQuality] = useState<Quality | null>(null);
  const [densityCells, setDensityCells] = useState<any[]>([]);
  const [retentionSettings, setRetentionSettings] = useState<RetentionSetting[]>([]);
  const [spoofSignals, setSpoofSignals] = useState<SpoofSignal[]>([]);
  const [restrictions, setRestrictions] = useState<RoadRestriction[]>([]);
  const [geofenceAudits, setGeofenceAudits] = useState<any[]>([]);
  const [batteryConfig, setBatteryConfig] = useState<any>(null);

  // Filter states
  const [metric, setMetric] = useState<"demand" | "supply">("demand");
  const [windowHours, setWindowHours] = useState(24);
  const [timeFilter, setTimeFilter] = useState("ALL_DAY");
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Diagnostic Test States
  const [testLat, setTestLat] = useState("28.6139");
  const [testLng, setTestLng] = useState("77.2090");
  const [snappedResult, setSnappedResult] = useState<any>(null);
  const [routeAlternatives, setRouteAlternatives] = useState<any[]>([]);
  const [newRestrictionTitle, setNewRestrictionTitle] = useState("");
  const [newRestrictionDesc, setNewRestrictionDesc] = useState("");
  const [newRestrictionType, setNewRestrictionType] = useState("VIP_CORRIDOR");

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [h, q, hm, dens, ret, spoofs, restr, audits, batt] = await Promise.all([
        fetchWithAuth("/admin/location-ops/provider-health"),
        fetchWithAuth(`/admin/location-ops/quality?window_hours=${windowHours}`),
        fetchWithAuth(`/admin/heat-map/monitor?metric=${metric}&window_hours=${windowHours}&time_filter=${timeFilter}&minimum_count=5`),
        fetchWithAuth("/admin/location-ops/driver-density?resolution_km=2.0"),
        fetchWithAuth("/admin/location-ops/retention-controls"),
        fetchWithAuth("/admin/location-ops/spoof-signals?limit=25"),
        fetchWithAuth("/admin/location-ops/road-restrictions"),
        fetchWithAuth("/admin/governance/geofences/audit?limit=20"),
        fetchWithAuth("/location/battery-optimization-config")
      ]);

      if (h) setHealth(h as ProviderHealth);
      if (q) setQuality(q as Quality);
      if (hm) setHeatmap(hm as HeatMap);
      if (dens && dens.grid_cells) setDensityCells(dens.grid_cells);
      if (Array.isArray(ret)) setRetentionSettings(ret);
      if (Array.isArray(spoofs)) setSpoofSignals(spoofs);
      if (Array.isArray(restr)) setRestrictions(restr);
      if (Array.isArray(audits)) setGeofenceAudits(audits);
      if (batt) setBatteryConfig(batt);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load location operations intelligence.");
    } finally {
      setLoading(false);
    }
  }, [metric, windowHours, timeFilter]);

  useEffect(() => {
    void loadData();
    const interval = window.setInterval(() => void loadData(), 60000);
    return () => window.clearInterval(interval);
  }, [loadData]);

  // Test snap action
  const handleSnapTest = async () => {
    try {
      const res = await fetchWithAuth("/location/snap-to-road", {
        method: "POST",
        body: JSON.stringify({ latitude: parseFloat(testLat), longitude: parseFloat(testLng) })
      });
      setSnappedResult(res);
      setMessage("Coordinate curbside alignment evaluated successfully.");
    } catch (err: any) {
      setError(err.message || "Failed to snap coordinates.");
    }
  };

  // Test route alternatives
  const handleRouteTest = async () => {
    try {
      const res = await fetchWithAuth(`/location/route-alternatives?origin_lat=${testLat}&origin_lng=${testLng}&dest_lat=28.5562&dest_lng=77.0855`);
      if (res && res.routes) {
        setRouteAlternatives(res.routes);
        setMessage("Generated 3 multi-criteria route alternatives (Express, Scenic, Low-Emission).");
      }
    } catch (err: any) {
      setError(err.message || "Failed to calculate route alternatives.");
    }
  };

  // Create road restriction
  const handleCreateRestriction = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newRestrictionTitle.trim()) return;
    try {
      await fetchWithAuth("/admin/location-ops/road-restrictions", {
        method: "POST",
        body: JSON.stringify({
          title: newRestrictionTitle,
          description: newRestrictionDesc || "Municipal and traffic authority restriction active.",
          restriction_type: newRestrictionType,
          latitude: parseFloat(testLat),
          longitude: parseFloat(testLng),
          radius_meters: 600.0
        })
      });
      setMessage("Road restriction broadcast saved and published.");
      setNewRestrictionTitle("");
      setNewRestrictionDesc("");
      void loadData();
    } catch (err: any) {
      setError(err.message || "Could not publish road restriction.");
    }
  };

  // Update retention TTL
  const handleUpdateRetention = async (category: string, newDays: number) => {
    try {
      await fetchWithAuth("/admin/location-ops/retention-controls", {
        method: "PUT",
        body: JSON.stringify({ category, retention_ttl_days: newDays })
      });
      setMessage(`Updated retention TTL for ${category} to ${newDays} days.`);
      void loadData();
    } catch (err: any) {
      setError(err.message || "Could not update retention policy.");
    }
  };

  // Run dry run or purge
  const handleRunPurge = async (category: string, dryRun: boolean) => {
    try {
      const res = await fetchWithAuth("/admin/location-ops/retention-purge", {
        method: "POST",
        body: JSON.stringify({ category, dry_run: dryRun })
      });
      setMessage(
        dryRun
          ? `Purge dry-run complete for ${category}: ${res.records_eligible_for_purge} expired records identified.`
          : `Purge executed for ${category}: ${res.records_eligible_for_purge} records removed.`
      );
      void loadData();
    } catch (err: any) {
      setError(err.message || "Failed to execute retention purge.");
    }
  };

  // Export GeoJSON
  const handleExportGeoJson = async () => {
    try {
      const geo = await fetchWithAuth("/admin/governance/geofences/export");
      const blob = new Blob([JSON.stringify(geo, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `grand_chauffeur_geofences_${new Date().toISOString().slice(0, 10)}.geojson`;
      a.click();
      URL.revokeObjectURL(url);
      setMessage("Geofence FeatureCollection exported successfully.");
    } catch (err: any) {
      setError(err.message || "Export failed.");
    }
  };

  return (
    <main style={{ padding: "clamp(16px,3vw,32px)", color: "#f4f0e7", maxWidth: 1440, margin: "0 auto" }}>
      {/* Header */}
      <header style={{ display: "flex", flexWrap: "wrap", justifyContent: "space-between", alignItems: "center", gap: 16, marginBottom: 20 }}>
        <div>
          <p style={{ color: "#d4af37", textTransform: "uppercase", letterSpacing: 2, fontSize: 11, margin: 0 }}>
            Geospatial Intelligence • Part 18
          </p>
          <h1 style={{ margin: "8px 0", fontFamily: "Georgia,serif", fontWeight: 500, fontSize: "1.8rem" }}>
            Location Operations & Geofence Intelligence
          </h1>
          <p style={{ color: "#a8adb6", margin: 0, fontSize: "0.88rem" }}>
            Real-time provider health, privacy-rounded heatmaps, road closures, telemetry quality & geofence lifecycle audit.
          </p>
        </div>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <button onClick={() => void loadData()} style={{ ...selectStyle, borderColor: "#d4af37", cursor: "pointer", display: "flex", alignItems: "center", gap: 6 }}>
            <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
            <span>Refresh</span>
          </button>
        </div>
      </header>

      {/* Status Banners */}
      {error && (
        <div role="alert" style={{ ...panel, borderColor: "#ef4444", background: "rgba(239,68,68,0.1)", marginBottom: 16, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <span>{error}</span>
          <button onClick={() => setError(null)} style={{ background: "transparent", border: "none", color: "#ef4444", cursor: "pointer" }}>✕</button>
        </div>
      )}
      {message && (
        <div role="status" style={{ ...panel, borderColor: "#10b981", background: "rgba(16,185,129,0.1)", marginBottom: 16, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <span>{message}</span>
          <button onClick={() => setMessage(null)} style={{ background: "transparent", border: "none", color: "#10b981", cursor: "pointer" }}>✕</button>
        </div>
      )}

      {/* High Level KPI Ribbons */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(210px,1fr))", gap: 12, marginBottom: 20 }}>
        <section style={panel}>
          <small style={{ color: "#9ca3af", textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>PROVIDER STATUS</small>
          <div style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 4 }}>
            <span style={{ width: 10, height: 10, borderRadius: "50%", background: health?.is_live_provider ? "#10b981" : "#f59e0b" }} />
            <h3 style={{ margin: 0, fontSize: "1.2rem" }}>{health?.provider_name ?? "Testing…"}</h3>
          </div>
          <p style={{ color: "#d4af37", margin: "4px 0 0", fontSize: "0.8rem" }}>Latency: {health?.probe_latency_ms ?? 0} ms</p>
        </section>

        <section style={panel}>
          <small style={{ color: "#9ca3af", textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>TELEMETRY EVENTS (24H)</small>
          <h3 style={{ margin: "4px 0 0", fontSize: "1.3rem" }}>{quality?.events ?? 0}</h3>
          <p style={{ color: "#9ca3af", margin: "4px 0 0", fontSize: "0.8rem" }}>
            Mean accuracy: ±{quality?.mean_accuracy_m ?? 8.5} m
          </p>
        </section>

        <section style={panel}>
          <small style={{ color: "#9ca3af", textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>SPOOF SIGNALS (24H)</small>
          <h3 style={{ margin: "4px 0 0", fontSize: "1.3rem", color: (quality?.risk_signals ?? 0) > 0 ? "#ef4444" : "#10b981" }}>
            {quality?.risk_signals ?? 0} alerts
          </h3>
          <p style={{ color: "#9ca3af", margin: "4px 0 0", fontSize: "0.8rem" }}>
            Poor accuracy rate: {quality?.poor_accuracy_rate_pct ?? 0}%
          </p>
        </section>

        <section style={panel}>
          <small style={{ color: "#9ca3af", textTransform: "uppercase", fontSize: "0.75rem", letterSpacing: "0.05em" }}>ACTIVE RESTRICTIONS</small>
          <h3 style={{ margin: "4px 0 0", fontSize: "1.3rem", color: restrictions.length > 0 ? "#f59e0b" : "#d4af37" }}>
            {restrictions.length} active
          </h3>
          <p style={{ color: "#9ca3af", margin: "4px 0 0", fontSize: "0.8rem" }}>VIP corridors & closures</p>
        </section>
      </div>

      {/* Tab Navigation */}
      <div style={{ display: "flex", gap: 10, borderBottom: "1px solid rgba(212,175,55,0.2)", paddingBottom: 12, marginBottom: 20, overflowX: "auto" }}>
        <button style={tabBtn(activeTab === "diagnostics")} onClick={() => setActiveTab("diagnostics")}>
          <Navigation size={15} />
          <span>Provider & Diagnostics</span>
        </button>
        <button style={tabBtn(activeTab === "heatmaps")} onClick={() => setActiveTab("heatmaps")}>
          <MapPin size={15} />
          <span>Demand & Density Maps</span>
        </button>
        <button style={tabBtn(activeTab === "quality")} onClick={() => setActiveTab("quality")}>
          <Activity size={15} />
          <span>Quality, Spoofing & Retention</span>
        </button>
        <button style={tabBtn(activeTab === "geofences")} onClick={() => setActiveTab("geofences")}>
          <ShieldAlert size={15} />
          <span>Geofences & Restrictions</span>
        </button>
      </div>

      {/* TAB 1: DIAGNOSTICS & PROVIDER */}
      {activeTab === "diagnostics" && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
          {/* Live Provider Card */}
          <section style={panel}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
              <h3 style={{ margin: 0, color: "#d4af37" }}>Live Map Provider Status (Feature 426 & 433)</h3>
              <span style={{ fontSize: "0.75rem", background: "rgba(16,185,129,0.2)", color: "#10b981", padding: "4px 8px", borderRadius: 4 }}>
                {health?.status || "OPERATIONAL"}
              </span>
            </div>
            <p style={{ color: "#9ca3af", fontSize: "0.85rem" }}>
              Comprehensive provider disclosure, tile licensing, and live API responsiveness monitoring.
            </p>
            <div style={{ background: "#090d13", padding: 12, borderRadius: 8, fontSize: "0.82rem", lineHeight: 1.6, marginBottom: 14 }}>
              <div><strong>Active Provider:</strong> {health?.provider_name}</div>
              <div><strong>Live Probe Latency:</strong> {health?.probe_latency_ms} ms</div>
              <div><strong>API Quota Status:</strong> {health?.quota_status}</div>
              <div><strong>Tile Server:</strong> {health?.attribution}</div>
              <div><strong>Road Closure Feed:</strong> {health?.road_closure_feed}</div>
            </div>
            <small style={{ color: "#a8adb6" }}>{health?.disclosure}</small>
          </section>

          {/* Coordinate Snapping & Verification Tester */}
          <section style={panel}>
            <h3 style={{ margin: "0 0 12px 0", color: "#d4af37" }}>Road Snapping & Route Multi-Choice (Features 428, 432, 434)</h3>
            <p style={{ color: "#9ca3af", fontSize: "0.85rem" }}>
              Test dropped pin curbside alignment and compute 3 multi-criteria route alternatives (Fastest, Scenic, Low-Emission).
            </p>
            <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
              <input
                type="text"
                value={testLat}
                onChange={e => setTestLat(e.target.value)}
                placeholder="Latitude"
                style={{ ...selectStyle, flex: 1 }}
              />
              <input
                type="text"
                value={testLng}
                onChange={e => setTestLng(e.target.value)}
                placeholder="Longitude"
                style={{ ...selectStyle, flex: 1 }}
              />
              <button onClick={() => void handleSnapTest()} style={{ ...selectStyle, borderColor: "#d4af37", cursor: "pointer" }}>
                Test Snap
              </button>
              <button onClick={() => void handleRouteTest()} style={{ ...selectStyle, background: "#d4af37", color: "#000", fontWeight: 700, cursor: "pointer" }}>
                Compare Routes
              </button>
            </div>

            {snappedResult && (
              <div style={{ background: "#090d13", padding: 12, borderRadius: 8, fontSize: "0.82rem", marginBottom: 12 }}>
                <div style={{ color: "#10b981", fontWeight: 600 }}>✓ Curbside Alignment: {snappedResult.offset_distance_meters}m offset</div>
                <div>Snapped Coords: ({snappedResult.snapped_latitude}, {snappedResult.snapped_longitude})</div>
                <div style={{ color: "#9ca3af" }}>{snappedResult.safety_note}</div>
              </div>
            )}

            {routeAlternatives.length > 0 && (
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {routeAlternatives.map((r, i) => (
                  <div key={i} style={{ background: "#090d13", border: r.tag === "RECOMMENDED" ? "1px solid #d4af37" : "1px solid #282d36", padding: 10, borderRadius: 8, fontSize: "0.8rem" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", fontWeight: 600 }}>
                      <span style={{ color: r.tag === "RECOMMENDED" ? "#d4af37" : "#fff" }}>{r.route_label} [{r.tag}]</span>
                      <span>{r.duration_minutes} min ({r.distance_km} km)</span>
                    </div>
                    <div style={{ color: "#9ca3af", fontSize: "0.75rem", marginTop: 2 }}>{r.description}</div>
                    <div style={{ color: "#a8adb6", fontSize: "0.72rem", marginTop: 2 }}>Traffic delay: +{r.traffic_delay_minutes}m • Toll: ₹{r.est_toll_inr} • CO₂: {r.co2_grams_per_km}g/km</div>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      )}

      {/* TAB 2: DEMAND & DENSITY HEATMAPS */}
      {activeTab === "heatmaps" && (
        <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 16 }}>
          <section style={panel}>
            <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "space-between", alignItems: "center", gap: 8, marginBottom: 14 }}>
              <div>
                <h3 style={{ margin: 0, color: "#d4af37" }}>
                  {metric === "demand" ? "Executive Demand Activity" : "Chauffeur Supply Density"} (Feature 440 & 441)
                </h3>
                <small style={{ color: "#9ca3af" }}>Privacy-aggregated k-anonymous cells (min 5 events per 1.1km bin)</small>
              </div>
              <div style={{ display: "flex", gap: 8 }}>
                <select style={selectStyle} value={timeFilter} onChange={e => setTimeFilter(e.target.value)}>
                  <option value="ALL_DAY">All Day</option>
                  <option value="MORNING_RUSH">Morning Rush (07:00–11:00)</option>
                  <option value="AFTERNOON_LULL">Afternoon Lull (12:00–16:00)</option>
                  <option value="EVENING_GALA">Evening Gala (17:00–22:00)</option>
                  <option value="LATE_NIGHT">Late Night (23:00–06:00)</option>
                </select>
                <select style={selectStyle} value={windowHours} onChange={e => setWindowHours(Number(e.target.value))}>
                  <option value={12}>12 Hours</option>
                  <option value={24}>24 Hours</option>
                  <option value={48}>48 Hours</option>
                  <option value={168}>7 Days</option>
                </select>
                <select style={selectStyle} value={metric} onChange={e => setMetric(e.target.value as "demand" | "supply")}>
                  <option value="demand">Ride Demand</option>
                  <option value="supply">Driver Supply</option>
                </select>
              </div>
            </div>

            {/* Heatmap Visual Matrix */}
            <div style={{ position: "relative", height: 320, background: "radial-gradient(ellipse at center, #1b2431, #0a0e14)", borderRadius: 10, border: "1px solid #282d36", overflow: "hidden", display: "grid", placeItems: "center" }}>
              {heatmap && heatmap.clusters.length > 0 ? (
                heatmap.clusters.map((c, i) => (
                  <div
                    key={i}
                    title={`${c.count} records (${c.latitude}, ${c.longitude})`}
                    style={{
                      position: "absolute",
                      left: `${30 + ((c.longitude - 77.0) * 120) % 70}%`,
                      top: `${30 + ((c.latitude - 28.5) * 120) % 70}%`,
                      width: 24 + c.intensity * 36,
                      height: 24 + c.intensity * 36,
                      transform: "translate(-50%, -50%)",
                      borderRadius: "50%",
                      background: "radial-gradient(circle, rgba(212,175,55,0.85), rgba(212,175,55,0.1) 70%, transparent)",
                      display: "grid",
                      placeItems: "center",
                      color: "#fff",
                      fontSize: 10,
                      fontWeight: 700
                    }}
                  >
                    {c.count}
                  </div>
                ))
              ) : (
                <div style={{ textAlign: "center", color: "#9ca3af" }}>
                  <MapPin size={32} style={{ margin: "0 auto 8px", opacity: 0.5 }} />
                  <div>No clusters met the privacy threshold for this window.</div>
                  <small>Try selecting a different time segment or expanding hours.</small>
                </div>
              )}
              <small style={{ position: "absolute", bottom: 8, left: 12, color: "#9ca3af", fontSize: "0.72rem" }}>
                {heatmap?.privacy}
              </small>
            </div>
          </section>

          {/* Chauffeur Density Aggregation Table */}
          <section style={panel}>
            <h3 style={{ margin: "0 0 10px 0", color: "#d4af37" }}>Chauffeur Density (Feature 441)</h3>
            <p style={{ color: "#9ca3af", fontSize: "0.82rem", lineHeight: 1.4 }}>
              Regional density clusters without exposing driver home addresses or raw GPS tracks.
            </p>
            <div style={{ display: "flex", flexDirection: "column", gap: 8, maxHeight: 310, overflowY: "auto" }}>
              {densityCells.length > 0 ? (
                densityCells.map((cell, idx) => (
                  <div key={idx} style={{ background: "#090d13", padding: 10, borderRadius: 8, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: "0.85rem" }}>
                        Cell {cell.latitude}, {cell.longitude}
                      </div>
                      <small style={{ color: "#9ca3af" }}>{cell.active_chauffeurs} online chauffeurs</small>
                    </div>
                    <span style={{
                      fontSize: "0.72rem",
                      fontWeight: 700,
                      padding: "3px 8px",
                      borderRadius: 4,
                      background: cell.density_tier === "SURGE_READY" ? "rgba(16,185,129,0.2)" : "rgba(212,175,55,0.2)",
                      color: cell.density_tier === "SURGE_READY" ? "#10b981" : "#d4af37"
                    }}>
                      {cell.density_tier}
                    </span>
                  </div>
                ))
              ) : (
                <div style={{ color: "#9ca3af", fontSize: "0.82rem", textAlign: "center", marginTop: 40 }}>
                  Active chauffeur density aggregates will display as online telemetry updates.
                </div>
              )}
            </div>
          </section>
        </div>
      )}

      {/* TAB 3: QUALITY, SPOOFING & RETENTION */}
      {activeTab === "quality" && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
          {/* Spoofing Signals Monitor */}
          <section style={panel}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
              <h3 style={{ margin: 0, color: "#d4af37" }}>GPS Spoofing & Teleportation Signals (Feature 444)</h3>
              <span style={{ fontSize: "0.75rem", background: "rgba(239,68,68,0.2)", color: "#ef4444", padding: "4px 8px", borderRadius: 4 }}>
                {spoofSignals.length} Detected
              </span>
            </div>
            <p style={{ color: "#9ca3af", fontSize: "0.82rem" }}>
              Mock location flags, kinematic jumps (&gt;160 km/h), and synthetic coordinates logged for SOC review.
            </p>
            <div style={{ display: "flex", flexDirection: "column", gap: 8, maxHeight: 300, overflowY: "auto" }}>
              {spoofSignals.length > 0 ? (
                spoofSignals.map((sig, i) => (
                  <div key={i} style={{ background: "#090d13", padding: 10, borderRadius: 8, fontSize: "0.8rem", borderLeft: "3px solid #ef4444" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", fontWeight: 600 }}>
                      <span>{sig.chauffeur_name}</span>
                      <span style={{ color: "#ef4444" }}>Risk Score: {(sig.spoof_risk_score * 100).toFixed(0)}%</span>
                    </div>
                    <div style={{ color: "#9ca3af", fontSize: "0.75rem", marginTop: 2 }}>
                      Flags: {sig.quality_flags.join(", ") || "UNUSUAL_KINEMATICS"}
                    </div>
                    <div style={{ color: "#6b7280", fontSize: "0.72rem", marginTop: 2 }}>
                      Recorded: {new Date(sig.recorded_at).toLocaleTimeString()} • Speed: {sig.speed_mps} m/s • Accuracy: ±{sig.accuracy_m}m
                    </div>
                  </div>
                ))
              ) : (
                <div style={{ textAlign: "center", color: "#10b981", padding: 30 }}>
                  <CheckCircle size={28} style={{ margin: "0 auto 8px" }} />
                  <div>No active GPS spoofing anomalies recorded in current window.</div>
                </div>
              )}
            </div>
          </section>

          {/* Retention Controls & Purge */}
          <section style={panel}>
            <h3 style={{ margin: "0 0 12px 0", color: "#d4af37" }}>Location Data Retention & Purge (Feature 447 & 445)</h3>
            <p style={{ color: "#9ca3af", fontSize: "0.82rem" }}>
              Statutory and privacy-first retention TTLs. Purge raw GPS tracks while safeguarding accident defense periods.
            </p>
            <div style={{ display: "flex", flexDirection: "column", gap: 10, marginBottom: 14 }}>
              {retentionSettings.map((r, i) => (
                <div key={i} style={{ background: "#090d13", padding: 12, borderRadius: 8, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: "0.85rem" }}>{r.category}</div>
                    <small style={{ color: "#9ca3af" }}>TTL: {r.retention_ttl_days} Days • Last purged: {r.last_purged_at ? new Date(r.last_purged_at).toLocaleDateString() : "Never"}</small>
                  </div>
                  <div style={{ display: "flex", gap: 6 }}>
                    <button onClick={() => void handleUpdateRetention(r.category, r.retention_ttl_days === 90 ? 60 : 90)} style={{ ...selectStyle, cursor: "pointer", fontSize: "0.75rem" }}>
                      Toggle TTL
                    </button>
                    <button onClick={() => void handleRunPurge(r.category, true)} style={{ ...selectStyle, cursor: "pointer", fontSize: "0.75rem" }}>
                      Dry-Run
                    </button>
                    <button onClick={() => void handleRunPurge(r.category, false)} style={{ ...selectStyle, borderColor: "#ef4444", color: "#ef4444", cursor: "pointer", fontSize: "0.75rem" }}>
                      Purge
                    </button>
                  </div>
                </div>
              ))}
            </div>

            {batteryConfig && (
              <div style={{ background: "#090d13", padding: 10, borderRadius: 8, fontSize: "0.8rem" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 6, color: "#d4af37", fontWeight: 600 }}>
                  <Battery size={14} />
                  <span>Adaptive Chauffeur Battery Optimization (Feature 445)</span>
                </div>
                <div style={{ color: "#9ca3af", marginTop: 4 }}>
                  Stationary: {batteryConfig.stationary_interval_sec}s • City: {batteryConfig.city_interval_sec}s • Highway: {batteryConfig.highway_interval_sec}s • Displacement: {batteryConfig.displacement_threshold_m}m
                </div>
              </div>
            )}
          </section>
        </div>
      )}

      {/* TAB 4: GEOFENCES & RESTRICTIONS */}
      {activeTab === "geofences" && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
          {/* Road Restrictions Desk */}
          <section style={panel}>
            <h3 style={{ margin: "0 0 12px 0", color: "#d4af37" }}>Road Restrictions & VIP Corridors (Feature 436)</h3>
            <p style={{ color: "#9ca3af", fontSize: "0.82rem" }}>
              Publish active road closures, VIP convoy holds, and zero-emission vehicle restrictions to all client apps.
            </p>
            <form onSubmit={handleCreateRestriction} style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 14 }}>
              <input
                type="text"
                placeholder="Title (e.g. Diplomatic VIP Corridor Hold)"
                value={newRestrictionTitle}
                onChange={e => setNewRestrictionTitle(e.target.value)}
                required
                style={selectStyle}
              />
              <input
                type="text"
                placeholder="Details & detour guidance"
                value={newRestrictionDesc}
                onChange={e => setNewRestrictionDesc(e.target.value)}
                style={selectStyle}
              />
              <div style={{ display: "flex", gap: 8 }}>
                <select style={{ ...selectStyle, flex: 1 }} value={newRestrictionType} onChange={e => setNewRestrictionType(e.target.value)}>
                  <option value="VIP_CORRIDOR">VIP Corridor Hold</option>
                  <option value="ROAD_CLOSURE">Complete Road Closure</option>
                  <option value="MONSOON_WATERLOGGING">Monsoon Waterlogging</option>
                  <option value="ZERO_EMISSION_ONLY">Zero-Emission Zone</option>
                </select>
                <button type="submit" style={{ ...selectStyle, background: "#d4af37", color: "#000", fontWeight: 700, cursor: "pointer" }}>
                  Publish Notice
                </button>
              </div>
            </form>

            <div style={{ display: "flex", flexDirection: "column", gap: 8, maxHeight: 220, overflowY: "auto" }}>
              {restrictions.map((r, i) => (
                <div key={i} style={{ background: "#090d13", padding: 10, borderRadius: 8, fontSize: "0.8rem", borderLeft: "3px solid #f59e0b" }}>
                  <div style={{ fontWeight: 600 }}>{r.title}</div>
                  <div style={{ color: "#9ca3af", fontSize: "0.75rem", marginTop: 2 }}>{r.description}</div>
                  <small style={{ color: "#6b7280" }}>Type: {r.restriction_type} • Radius: {r.radius_meters}m</small>
                </div>
              ))}
            </div>
          </section>

          {/* Geofence Import/Export & Audit History */}
          <section style={panel}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
              <h3 style={{ margin: 0, color: "#d4af37" }}>Geofence GeoJSON & Audit History (Features 449 & 450)</h3>
              <button onClick={() => void handleExportGeoJson()} style={{ ...selectStyle, borderColor: "#d4af37", cursor: "pointer", display: "flex", alignItems: "center", gap: 6 }}>
                <Download size={13} />
                <span>Export GeoJSON</span>
              </button>
            </div>
            <p style={{ color: "#9ca3af", fontSize: "0.82rem" }}>
              Immutable audit trail recording all polygon boundary changes, activations, and surge updates.
            </p>
            <div style={{ display: "flex", flexDirection: "column", gap: 8, maxHeight: 310, overflowY: "auto" }}>
              {geofenceAudits.length > 0 ? (
                geofenceAudits.map((a, i) => (
                  <div key={i} style={{ background: "#090d13", padding: 10, borderRadius: 8, fontSize: "0.8rem" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", fontWeight: 600 }}>
                      <span style={{ color: "#d4af37" }}>{a.zone_name || "Geofence Zone"}</span>
                      <span style={{ fontSize: "0.72rem", color: a.action === "ACTIVATED" ? "#10b981" : "#f59e0b" }}>{a.action}</span>
                    </div>
                    <div style={{ color: "#9ca3af", fontSize: "0.75rem", marginTop: 2 }}>
                      Admin: {a.admin_name || "System"} • {new Date(a.created_at).toLocaleString()}
                    </div>
                  </div>
                ))
              ) : (
                <div style={{ color: "#9ca3af", textAlign: "center", marginTop: 40, fontSize: "0.85rem" }}>
                  Geofence activation audits will record upon boundary edits or toggles.
                </div>
              )}
            </div>
          </section>
        </div>
      )}
    </main>
  );
};
