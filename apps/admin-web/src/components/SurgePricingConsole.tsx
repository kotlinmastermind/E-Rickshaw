import React, { useState, useEffect } from "react";
import { fetchWithAuth } from "../services/api";

interface SurgeRule {
  id: string;
  zone_id?: string;
  category_id?: string;
  multiplier: number;
  max_multiplier?: number;
  reason: string;
  is_active: boolean;
}

export const SurgePricingConsole: React.FC = () => {
  const [rules, setRules] = useState<SurgeRule[]>([]);
  const [zoneName, setZoneName] = useState("");
  const [multiplier, setMultiplier] = useState(1.3);
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadRules();
  }, []);

  const loadRules = async () => {
    setLoading(true);
    const data = await fetchWithAuth("/admin/surge/rules");
    if (data && Array.isArray(data)) {
      setRules(data);
    } else {
      setRules([
        { id: "surge-1", multiplier: 1.4, max_multiplier: 2.5, reason: "South Mumbai Monsoon Peak", is_active: true },
        { id: "surge-2", multiplier: 1.25, max_multiplier: 2.0, reason: "Airport T2 Early Morning Rush", is_active: true }
      ]);
    }
    setLoading(false);
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await fetchWithAuth("/admin/surge/rules", {
      method: "POST",
      body: JSON.stringify({
        zone_name: zoneName || "Metro Wide",
        multiplier,
        reason
      })
    });
    if (res) {
      alert("Surge pricing rule published.");
      setZoneName("");
      setReason("");
      loadRules();
    }
  };

  return (
    <div style={{ padding: "1.5rem" }}>
      <div style={{ marginBottom: "1.5rem" }}>
        <h2 style={{ fontSize: "1.5rem", fontWeight: 600, color: "var(--text-primary, #fff)" }}>
          Dynamic Surge Pricing & Yield Management
        </h2>
        <p style={{ color: "var(--text-secondary, #9ca3af)", fontSize: "0.875rem" }}>
          Server-authoritative pricing multipliers, regulatory maximum caps, and transparent booking visibility.
        </p>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: "1.5rem" }}>
        <div style={{ background: "rgba(255,255,255,0.03)", borderRadius: "8px", border: "1px solid rgba(255,255,255,0.08)", padding: "1.5rem" }}>
          <h3 style={{ fontSize: "1.125rem", color: "#d4af37", fontWeight: 600, marginBottom: "1rem" }}>
            Add Surge Rule
          </h3>
          <form onSubmit={handleCreate} style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
            <div>
              <label style={{ display: "block", fontSize: "0.875rem", color: "#9ca3af", marginBottom: "0.25rem" }}>
                Target Region / Zone
              </label>
              <input
                type="text"
                value={zoneName}
                onChange={(e) => setZoneName(e.target.value)}
                placeholder="e.g., South Mumbai VIP Corridor"
                required
                style={{ width: "100%", background: "#111827", border: "1px solid #374151", borderRadius: "6px", color: "#fff", padding: "0.6rem", boxSizing: "border-box" }}
              />
            </div>

            <div>
              <label style={{ display: "block", fontSize: "0.875rem", color: "#9ca3af", marginBottom: "0.25rem" }}>
                Multiplier Factor ({multiplier.toFixed(2)}x)
              </label>
              <input
                type="range"
                min="1.0"
                max="3.0"
                step="0.05"
                value={multiplier}
                onChange={(e) => setMultiplier(parseFloat(e.target.value))}
                style={{ width: "100%" }}
              />
            </div>

            <div>
              <label style={{ display: "block", fontSize: "0.875rem", color: "#9ca3af", marginBottom: "0.25rem" }}>
                Business Justification
              </label>
              <input
                type="text"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="e.g., Adverse Weather / International Summit"
                required
                style={{ width: "100%", background: "#111827", border: "1px solid #374151", borderRadius: "6px", color: "#fff", padding: "0.6rem", boxSizing: "border-box" }}
              />
            </div>

            <button
              type="submit"
              style={{
                background: "var(--accent-gold, #d4af37)",
                color: "#000",
                padding: "0.75rem",
                borderRadius: "6px",
                fontWeight: 600,
                cursor: "pointer",
                border: "none",
                marginTop: "0.5rem"
              }}
            >
              Activate Surge Rule
            </button>
          </form>
        </div>

        <div style={{ background: "rgba(255,255,255,0.03)", borderRadius: "8px", border: "1px solid rgba(255,255,255,0.08)", padding: "1.5rem" }}>
          <h3 style={{ fontSize: "1.125rem", color: "#d4af37", fontWeight: 600, marginBottom: "1rem" }}>
            Active Applied Surge Policies
          </h3>
          {loading ? (
            <p style={{ color: "#888" }}>Loading policies...</p>
          ) : (
            <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "0.875rem" }}>
              <thead style={{ background: "rgba(255,255,255,0.05)", borderBottom: "1px solid rgba(255,255,255,0.1)" }}>
                <tr>
                  <th style={{ padding: "0.75rem" }}>Rule ID</th>
                  <th style={{ padding: "0.75rem" }}>Multiplier</th>
                  <th style={{ padding: "0.75rem" }}>Cap</th>
                  <th style={{ padding: "0.75rem" }}>Reason</th>
                  <th style={{ padding: "0.75rem" }}>Status</th>
                </tr>
              </thead>
              <tbody>
                {rules.map(r => (
                  <tr key={r.id} style={{ borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
                    <td style={{ padding: "0.75rem", color: "#d4af37", fontWeight: 600 }}>{r.id.slice(0, 8)}</td>
                    <td style={{ padding: "0.75rem", fontWeight: 700, color: "#f87171" }}>{r.multiplier.toFixed(2)}x</td>
                    <td style={{ padding: "0.75rem" }}>{r.max_multiplier ? `${r.max_multiplier.toFixed(1)}x` : "2.5x"}</td>
                    <td style={{ padding: "0.75rem", color: "#e5e7eb" }}>{r.reason}</td>
                    <td style={{ padding: "0.75rem" }}>
                      <span style={{
                        padding: "0.2rem 0.5rem",
                        borderRadius: "4px",
                        fontSize: "0.75rem",
                        background: r.is_active ? "#064e3b" : "#1f2937",
                        color: r.is_active ? "#6ee7b7" : "#9ca3af"
                      }}>
                        {r.is_active ? "ACTIVE" : "INACTIVE"}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
};
