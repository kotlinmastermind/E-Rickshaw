import React, { useCallback, useEffect, useState } from "react";
import { fetchWithAuth } from "../services/api";

type Cluster = { latitude: number; longitude: number; intensity: number; count: number };
type HeatMap = { clusters: Cluster[]; metric: string; window_hours: number; minimum_group_size: number; generated_at: string; privacy: string; empty_reason?: string | null };
type Quality = { events: number; mean_accuracy_m: number | null; poor_accuracy_events: number; risk_signals: number; quality_flags: Record<string, number>; window_hours: number };

const panel: React.CSSProperties = { background: "#11151d", border: "1px solid rgba(212,175,55,.2)", borderRadius: 14, padding: 20, color: "#eee" };
const selectStyle: React.CSSProperties = { background: "#090d13", color: "#eee", border: "1px solid #45413a", borderRadius: 8, padding: "9px 12px" };

export const HeatMapMonitor: React.FC = () => {
  const [metric, setMetric] = useState<"demand" | "supply">("demand");
  const [windowHours, setWindowHours] = useState(2);
  const [data, setData] = useState<HeatMap | null>(null);
  const [quality, setQuality] = useState<Quality | null>(null);
  const [health, setHealth] = useState<Record<string, any> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const [heat, q, h] = await Promise.all([
        fetchWithAuth(`/admin/heat-map/monitor?metric=${metric}&window_hours=${windowHours}&minimum_count=5`),
        fetchWithAuth("/admin/location-ops/quality?window_hours=24"),
        fetchWithAuth("/admin/location-ops/provider-health")
      ]);
      if (!heat || !Array.isArray((heat as HeatMap).clusters)) throw new Error("Operational location data is unavailable.");
      setData(heat as HeatMap); setQuality(q as Quality | null); setHealth(h as Record<string, any> | null);
    } catch (e) { setError(e instanceof Error ? e.message : "Could not load location operations."); }
    finally { setLoading(false); }
  }, [metric, windowHours]);

  useEffect(() => { void load(); const timer = window.setInterval(() => void load(), 60000); return () => window.clearInterval(timer); }, [load]);
  const counts = data?.clusters.map(c => c.count) ?? [];
  const maxCount = Math.max(1, ...counts);
  const latitudes = data?.clusters.map(c => c.latitude) ?? [];
  const longitudes = data?.clusters.map(c => c.longitude) ?? [];
  const minLat = Math.min(...latitudes), maxLat = Math.max(...latitudes), minLng = Math.min(...longitudes), maxLng = Math.max(...longitudes);

  return <main style={{ padding: "clamp(16px,3vw,32px)", color: "#f4f0e7", maxWidth: 1440, margin: "0 auto" }}>
    <header style={{ display: "flex", flexWrap: "wrap", justifyContent: "space-between", alignItems: "center", gap: 16, marginBottom: 20 }}>
      <div><p style={{ color: "#d4af37", textTransform: "uppercase", letterSpacing: 2, fontSize: 11, margin: 0 }}>Operations / Location</p><h1 style={{ margin: "8px 0", fontFamily: "Georgia,serif", fontWeight: 500 }}>Location quality & coverage</h1><p style={{ color: "#a8adb6", margin: 0 }}>Privacy-rounded activity cells and location signal diagnostics.</p></div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}><select aria-label="Map metric" style={selectStyle} value={metric} onChange={e => setMetric(e.target.value as "demand" | "supply")}><option value="demand">Ride demand</option><option value="supply">Online driver supply</option></select><select aria-label="Time range" style={selectStyle} value={windowHours} onChange={e => setWindowHours(Number(e.target.value))}><option value={1}>Last hour</option><option value={2}>Last 2 hours</option><option value={6}>Last 6 hours</option><option value={24}>Last 24 hours</option><option value={72}>Last 72 hours</option></select><button onClick={() => void load()} style={{ ...selectStyle, borderColor: "#d4af37", cursor: "pointer" }}>Refresh</button></div>
    </header>
    {error && <div role="alert" style={{ ...panel, borderColor: "#b65f59", marginBottom: 16 }}>{error} <button onClick={() => void load()} style={{ ...selectStyle, marginLeft: 12 }}>Retry</button></div>}
    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(220px,1fr))", gap: 12, marginBottom: 16 }}>
      <section style={panel}><small style={{ color: "#aaadb3" }}>MAP PROVIDER</small><h3 style={{ marginBottom: 4 }}>{health?.provider ?? "Checking…"}</h3><p style={{ color: "#b9bdc3", margin: 0 }}>{health?.status ?? "Provider health not available"}</p><small style={{ color: "#858b94" }}>{health?.message ?? ""}</small></section>
      <section style={panel}><small style={{ color: "#aaadb3" }}>LOCATION EVENTS · 24H</small><h3 style={{ marginBottom: 4 }}>{quality?.events ?? "—"}</h3><p style={{ color: "#b9bdc3", margin: 0 }}>Mean accuracy {quality?.mean_accuracy_m == null ? "not reported" : `±${quality.mean_accuracy_m} m`}</p></section>
      <section style={panel}><small style={{ color: "#aaadb3" }}>QUALITY SIGNALS · 24H</small><h3 style={{ marginBottom: 4 }}>{quality?.poor_accuracy_events ?? "—"} low accuracy</h3><p style={{ color: "#b9bdc3", margin: 0 }}>{quality?.risk_signals ?? "—"} spoof risk signals · review only</p></section>
    </div>
    <div style={{ display: "grid", gridTemplateColumns: "minmax(0,2fr) minmax(280px,1fr)", gap: 16 }}>
      <section style={{ ...panel, minHeight: 390 }} aria-label="Aggregated activity map">
        <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}><strong>{metric === "demand" ? "Aggregated ride demand" : "Aggregated online supply"}</strong><small style={{ color: "#a8adb6" }}>{data ? `${data.window_hours}h window · at least ${data.minimum_group_size} records per cell` : ""}</small></div>
        {loading && <p role="status" style={{ color: "#b9bdc3", marginTop: 28 }}>Loading aggregated location activity…</p>}
        {!loading && data?.clusters.length === 0 && <div style={{ marginTop: 80, textAlign: "center", color: "#b9bdc3" }}><h3>Nothing meets the privacy threshold</h3><p>{data.empty_reason} Try a longer time window.</p></div>}
        {!loading && !!data?.clusters.length && <div style={{ position: "relative", height: 300, marginTop: 18, background: "radial-gradient(ellipse at center,#1b2431,#0a0e14)", borderRadius: 10, overflow: "hidden", border: "1px solid #282d36" }}>
          {data.clusters.map((c, i) => { const x = maxLng === minLng ? 50 : 8 + ((c.longitude - minLng) / (maxLng - minLng)) * 84; const y = maxLat === minLat ? 50 : 92 - ((c.latitude - minLat) / (maxLat - minLat)) * 84; const size = 28 + (c.count / maxCount) * 46; return <div key={`${c.latitude}-${c.longitude}-${i}`} title={`${c.count} aggregated ${metric} records`} style={{ position: "absolute", left: `${x}%`, top: `${y}%`, width: size, height: size, transform: "translate(-50%,-50%)", borderRadius: "50%", background: "radial-gradient(circle,rgba(212,175,55,.8),rgba(212,175,55,.08) 70%,transparent)", display: "grid", placeItems: "center", color: "#fff", fontSize: 11, fontWeight: 700 }}>{c.count}</div>; })}
          <small style={{ position: "absolute", bottom: 8, left: 10, color: "#9198a3" }}>Rounded cell centers · relative extent only · no street-level map</small>
        </div>}
        <p style={{ color: "#9ba1aa", fontSize: 12, marginTop: 12 }}>{data?.privacy ?? "Location records are never shown individually."}</p>
      </section>
      <section style={panel}><strong>Coverage boundaries & provider limits</strong><p style={{ color: "#b9bdc3", lineHeight: 1.55 }}>Map health reports configuration status only. Live closures, legal road restrictions, and independent traffic verification are not configured here.</p><div style={{ padding: 12, borderRadius: 9, background: "#090d13", color: "#d4d0c6", fontSize: 13 }}>{health?.road_closure_feed ?? "Road closure feed: NOT_CONFIGURED"}</div><p style={{ color: "#9096a0", fontSize: 12 }}>Last refreshed: {data?.generated_at ? new Date(data.generated_at).toLocaleString() : "—"}</p></section>
    </div>
  </main>;
};
