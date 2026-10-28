import React, { useState, useEffect } from "react";
import {
  LifeBuoy, AlertTriangle, RefreshCw, CheckCircle2,
  ShieldAlert
} from "lucide-react";
import { requestApi } from "../services/api";

interface Ticket {
  id: string;
  ticket_number?: string;
  complaint_reference_number?: string;
  user_id: string;
  ride_id?: string;
  subject: string;
  category: string;
  status: string;
  priority: string;
  assigned_squad?: string;
  language_preference?: string;
  is_escalated?: boolean;
  escalation_reason?: string;
  lost_item_category?: string;
  lost_item_status?: string;
  csat_score?: number;
  data_retention_days?: number;
  refund_amount_cents?: number;
  assigned_to?: string;
  created_at: string;
}

interface ServiceStatus {
  overall_status: string;
  checked_at: string;
  services: Array<{
    service: string;
    name: string;
    status: string;
    uptime_percentage: number;
    latency_ms: number;
  }>;
  active_incidents_count: number;
  incidents: Array<{
    id: string;
    title: string;
    severity: string;
    affected_service: string;
    description: string;
    created_at: string;
  }>;
}

interface HelpArticle {
  id: string;
  slug: string;
  title: string;
  category: string;
  summary: string;
  context_tags: string[];
  helpful_count: number;
  not_helpful_count: number;
}

export const SupportTicketDesk: React.FC = () => {
  const [activeSubTab, setActiveSubTab] = useState<"tickets" | "service_status" | "knowledge_base" | "callbacks">("tickets");
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [squadFilter, setSquadFilter] = useState<string>("ALL");
  const [selectedTicket, setSelectedTicket] = useState<any | null>(null);
  const [ticketTimeline, setTicketTimeline] = useState<any | null>(null);
  const [internalNote, setInternalNote] = useState("");
  const [serviceStatus, setServiceStatus] = useState<ServiceStatus | null>(null);
  const [articles, setArticles] = useState<HelpArticle[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [actionNotice, setActionNotice] = useState("");

  useEffect(() => {
    loadAll();
  }, []);

  const loadAll = async () => {
    setLoading(true);
    setLoadError("");
    await Promise.all([loadTickets(), loadServiceStatus(), loadArticles()]);
    setLoading(false);
  };

  const loadTickets = async () => {
    try { setTickets(await requestApi<Ticket[]>("/admin/support/tickets")); }
    catch (error) { setTickets([]); setLoadError(error instanceof Error ? error.message : "Support tickets could not be loaded."); }
  };

  const loadServiceStatus = async () => {
    try {
      const data = await requestApi("/support/service-status");
      if (data && data.services) {
        setServiceStatus(data);
      }
    } catch { setServiceStatus(null); }
  };

  const loadArticles = async () => {
    try {
      const data = await requestApi("/support/articles");
      if (data && Array.isArray(data)) {
        setArticles(data);
      }
    } catch { setArticles([]); }
  };

  const handleUpdateStatus = async (ticketId: string, newStatus: string) => {
    try {
      await requestApi(`/admin/support/tickets/${ticketId}/status?status_val=${newStatus}`, { method: "PUT" });
      setTickets(prev => prev.map(t => t.id === ticketId ? { ...t, status: newStatus } : t));
      if (selectedTicket?.id === ticketId) setSelectedTicket({ ...selectedTicket, status: newStatus });
      setActionNotice("Ticket status updated."); setLoadError("");
    } catch (error) { setLoadError(error instanceof Error ? error.message : "Ticket status could not be updated."); }
  };

  const handleSelectTicket = async (t: Ticket) => {
    setSelectedTicket(t);
    try {
      const tl = await requestApi(`/support/tickets/${t.id}/timeline`);
      setTicketTimeline(tl);
    } catch {
      setTicketTimeline(null);
      setLoadError("Ticket timeline could not be loaded.");
    }
  };

  const handleAddNote = async (ticketId: string) => {
    if (!internalNote.trim()) return;
    try {
      await requestApi(`/admin/support/tickets/${ticketId}/internal-note?note=${encodeURIComponent(internalNote)}`, { method: "POST" });
      setActionNotice("Internal staff note recorded."); setLoadError(""); setInternalNote("");
    } catch (error) { setLoadError(error instanceof Error ? error.message : "Internal note could not be saved."); }
  };

  const filteredTickets = tickets.filter(t => {
    if (squadFilter === "ALL") return true;
    return (t.assigned_squad || "VIP_CONCIERGE") === squadFilter;
  });

  return (
    <div style={{ padding: "1.5rem", maxWidth: "1600px", margin: "0 auto" }}>
      {/* Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.5rem" }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
            <LifeBuoy style={{ width: "24px", height: "24px", color: "var(--accent-gold, #d4af37)" }} />
            <h2 style={{ fontSize: "1.5rem", fontWeight: 600, color: "var(--text-primary, #fff)", margin: 0 }}>
              Concierge Operations & Support Desk
            </h2>
          </div>
          <p style={{ color: "var(--text-secondary, #9ca3af)", fontSize: "0.875rem", marginTop: "0.25rem", marginBottom: 0 }}>
            Features 176–200: Case routing, incident advisories, guided cancellation audit, dispute resolutions & service recovery.
          </p>
        </div>
        <button
          onClick={loadAll}
          style={{
            background: "var(--accent-gold, #d4af37)",
            color: "#000",
            padding: "0.5rem 1.25rem",
            borderRadius: "6px",
            fontWeight: 600,
            cursor: "pointer",
            border: "none",
            display: "flex",
            alignItems: "center",
            gap: "0.5rem"
          }}
        >
          <RefreshCw style={{ width: "16px", height: "16px" }} />
          Refresh Consoles
        </button>
      </div>

      {/* Sub-Tabs */}
      <div style={{ display: "flex", gap: "0.5rem", marginBottom: "1.5rem", borderBottom: "1px solid rgba(255,255,255,0.1)", paddingBottom: "0.5rem" }}>
        <button
          onClick={() => setActiveSubTab("tickets")}
          style={{
            background: activeSubTab === "tickets" ? "rgba(212,175,55,0.15)" : "transparent",
            color: activeSubTab === "tickets" ? "var(--accent-gold, #d4af37)" : "#9ca3af",
            border: activeSubTab === "tickets" ? "1px solid var(--accent-gold, #d4af37)" : "1px solid transparent",
            padding: "0.5rem 1rem",
            borderRadius: "6px",
            cursor: "pointer",
            fontWeight: 600,
            fontSize: "0.875rem"
          }}
        >
          Priority Case Queue ({tickets.length})
        </button>
        <button
          onClick={() => setActiveSubTab("service_status")}
          style={{
            background: activeSubTab === "service_status" ? "rgba(212,175,55,0.15)" : "transparent",
            color: activeSubTab === "service_status" ? "var(--accent-gold, #d4af37)" : "#9ca3af",
            border: activeSubTab === "service_status" ? "1px solid var(--accent-gold, #d4af37)" : "1px solid transparent",
            padding: "0.5rem 1rem",
            borderRadius: "6px",
            cursor: "pointer",
            fontWeight: 600,
            fontSize: "0.875rem"
          }}
        >
          Platform Status & Advisories ({serviceStatus?.active_incidents_count || 0} Advisories)
        </button>
        <button
          onClick={() => setActiveSubTab("knowledge_base")}
          style={{
            background: activeSubTab === "knowledge_base" ? "rgba(212,175,55,0.15)" : "transparent",
            color: activeSubTab === "knowledge_base" ? "var(--accent-gold, #d4af37)" : "#9ca3af",
            border: activeSubTab === "knowledge_base" ? "1px solid var(--accent-gold, #d4af37)" : "1px solid transparent",
            padding: "0.5rem 1rem",
            borderRadius: "6px",
            cursor: "pointer",
            fontWeight: 600,
            fontSize: "0.875rem"
          }}
        >
          Knowledge Base & Help Articles ({articles.length})
        </button>
      </div>

      {loadError && <p role="alert" style={{ color: "#fca5a5", marginBottom: 12 }}>{loadError}</p>}
      {actionNotice && <p role="status" style={{ color: "#86efac", marginBottom: 12 }}>{actionNotice}</p>}
      {loading ? (
        <p style={{ color: "#888" }}>Loading support queues and incident telemetry...</p>
      ) : activeSubTab === "tickets" ? (
        <div>
          {/* Squad Filter Chips */}
          <div style={{ display: "flex", gap: "0.5rem", marginBottom: "1rem", alignItems: "center" }}>
            <span style={{ fontSize: "0.8rem", color: "#9ca3af", textTransform: "uppercase", letterSpacing: "0.05em" }}>Squad Routing:</span>
            {["ALL", "VIP_CONCIERGE", "BILLING_REFUNDS", "SAFETY_INCIDENTS", "FLEET_OPERATIONS"].map(sq => (
              <button
                key={sq}
                onClick={() => setSquadFilter(sq)}
                style={{
                  background: squadFilter === sq ? "#374151" : "rgba(255,255,255,0.05)",
                  color: squadFilter === sq ? "#fff" : "#9ca3af",
                  border: squadFilter === sq ? "1px solid #6b7280" : "1px solid rgba(255,255,255,0.1)",
                  padding: "0.25rem 0.75rem",
                  borderRadius: "16px",
                  fontSize: "0.75rem",
                  fontWeight: 600,
                  cursor: "pointer"
                }}
              >
                {sq.replace("_", " ")}
              </button>
            ))}
          </div>

          <div style={{ display: "grid", gridTemplateColumns: selectedTicket ? "1.2fr 1fr" : "1fr", gap: "1.5rem" }}>
            {/* Tickets Table */}
            <div style={{ background: "rgba(255,255,255,0.03)", borderRadius: "8px", border: "1px solid rgba(255,255,255,0.08)", overflowX: "auto" }}>
              {!filteredTickets.length && <p style={{ color: "#9ca3af", padding: 16 }}>No live support tickets are available.</p>}
              <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "0.85rem" }}>
                <thead style={{ background: "rgba(255,255,255,0.05)", borderBottom: "1px solid rgba(255,255,255,0.1)" }}>
                  <tr>
                    <th style={{ padding: "0.75rem 1rem" }}>Ticket & Ref #</th>
                    <th style={{ padding: "0.75rem 1rem" }}>Squad & Cat</th>
                    <th style={{ padding: "0.75rem 1rem" }}>Subject</th>
                    <th style={{ padding: "0.75rem 1rem" }}>Priority</th>
                    <th style={{ padding: "0.75rem 1rem" }}>Status</th>
                    <th style={{ padding: "0.75rem 1rem" }}>Special State</th>
                    <th style={{ padding: "0.75rem 1rem" }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredTickets.map(t => (
                    <tr
                      key={t.id}
                      onClick={() => handleSelectTicket(t)}
                      style={{
                        borderBottom: "1px solid rgba(255,255,255,0.05)",
                        cursor: "pointer",
                        background: selectedTicket?.id === t.id ? "rgba(212,175,55,0.1)" : "transparent"
                      }}
                    >
                      <td style={{ padding: "0.75rem 1rem" }}>
                        <div style={{ fontWeight: 600, color: "#d4af37" }}>{t.ticket_number || t.id.slice(0, 8)}</div>
                        {t.complaint_reference_number && (
                          <div style={{ fontSize: "0.75rem", color: "#9ca3af" }}>{t.complaint_reference_number}</div>
                        )}
                      </td>
                      <td style={{ padding: "0.75rem 1rem" }}>
                        <span style={{ padding: "0.15rem 0.4rem", borderRadius: "4px", fontSize: "0.7rem", background: "#1e293b", color: "#93c5fd" }}>
                          {t.assigned_squad || "VIP_CONCIERGE"}
                        </span>
                        <div style={{ fontSize: "0.75rem", color: "#6b7280", marginTop: "2px" }}>{t.category}</div>
                      </td>
                      <td style={{ padding: "0.75rem 1rem", maxWidth: "240px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                        {t.subject}
                      </td>
                      <td style={{ padding: "0.75rem 1rem" }}>
                        <span style={{
                          padding: "0.2rem 0.5rem",
                          borderRadius: "4px",
                          fontSize: "0.75rem",
                          fontWeight: 600,
                          background: t.priority === "URGENT" ? "#7f1d1d" : t.priority === "HIGH" ? "#9a3412" : "#1e293b",
                          color: t.priority === "URGENT" ? "#fca5a5" : t.priority === "HIGH" ? "#fdba74" : "#94a3b8"
                        }}>
                          {t.priority}
                        </span>
                      </td>
                      <td style={{ padding: "0.75rem 1rem" }}>
                        <span style={{
                          padding: "0.2rem 0.5rem",
                          borderRadius: "4px",
                          fontSize: "0.75rem",
                          background: t.status === "RESOLVED" ? "#064e3b" : "#451a03",
                          color: t.status === "RESOLVED" ? "#6ee7b7" : "#fdba74"
                        }}>
                          {t.status}
                        </span>
                      </td>
                      <td style={{ padding: "0.75rem 1rem" }}>
                        {t.is_escalated && (
                          <span style={{ display: "inline-flex", alignItems: "center", gap: "3px", color: "#ef4444", fontSize: "0.75rem", fontWeight: 600 }}>
                            <ShieldAlert style={{ width: "12px", height: "12px" }} /> Escalated
                          </span>
                        )}
                        {t.lost_item_status && (
                          <div style={{ fontSize: "0.75rem", color: "#38bdf8" }}>Lost: {t.lost_item_status}</div>
                        )}
                        {t.refund_amount_cents ? (
                          <div style={{ fontSize: "0.75rem", color: "#10b981" }}>Claim: ₹{t.refund_amount_cents / 100}</div>
                        ) : null}
                      </td>
                      <td style={{ padding: "0.75rem 1rem" }}>
                        <select
                          value={t.status}
                          onChange={(e) => {
                            e.stopPropagation();
                            handleUpdateStatus(t.id, e.target.value);
                          }}
                          style={{
                            background: "#1f2937",
                            color: "#fff",
                            border: "1px solid #374151",
                            borderRadius: "4px",
                            padding: "0.25rem 0.5rem",
                            fontSize: "0.8rem"
                          }}
                        >
                          <option value="OPEN">OPEN</option>
                          <option value="INVESTIGATING">INVESTIGATING</option>
                          <option value="RESOLVED">RESOLVED</option>
                          <option value="CLOSED">CLOSED</option>
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Selected Ticket Inspector */}
            {selectedTicket && (
              <div style={{ background: "rgba(255,255,255,0.03)", borderRadius: "8px", border: "1px solid rgba(255,255,255,0.08)", padding: "1.25rem" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
                  <div>
                    <h3 style={{ fontSize: "1.125rem", color: "#d4af37", fontWeight: 600, margin: 0 }}>
                      Case Inspector — {selectedTicket.ticket_number || selectedTicket.id.slice(0, 8)}
                    </h3>
                    <div style={{ fontSize: "0.75rem", color: "#9ca3af", marginTop: "2px" }}>
                      Complaint Ref: {selectedTicket.complaint_reference_number || "Not assigned"}
                    </div>
                  </div>
                  <button
                    onClick={() => setSelectedTicket(null)}
                    style={{ background: "transparent", color: "#888", border: "none", cursor: "pointer", fontSize: "1.2rem" }}
                  >
                    ✕
                  </button>
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0.5rem", fontSize: "0.825rem", color: "#e5e7eb", marginBottom: "1rem", background: "rgba(0,0,0,0.2)", padding: "0.75rem", borderRadius: "6px" }}>
                  <div><strong>User ID:</strong> {selectedTicket.user_id.slice(0, 12)}...</div>
                  <div><strong>Assigned Squad:</strong> {selectedTicket.assigned_squad || "VIP_CONCIERGE"}</div>
                  <div><strong>Language:</strong> {selectedTicket.language_preference?.toUpperCase() || "EN"}</div>
                  <div><strong>Retention Window:</strong> {selectedTicket.data_retention_days || 365} Days</div>
                  <div><strong>Associated Ride:</strong> {selectedTicket.ride_id || "None"}</div>
                  <div><strong>Reported:</strong> {new Date(selectedTicket.created_at).toLocaleString()}</div>
                </div>

                {selectedTicket.is_escalated && (
                  <div style={{ background: "rgba(239,68,68,0.15)", border: "1px solid rgba(239,68,68,0.4)", padding: "0.75rem", borderRadius: "6px", marginBottom: "1rem" }}>
                    <div style={{ color: "#fca5a5", fontWeight: 600, fontSize: "0.825rem", display: "flex", alignItems: "center", gap: "0.5rem" }}>
                      <AlertTriangle style={{ width: "16px", height: "16px" }} />
                      SUPERVISORY ESCALATION ACTIVE
                    </div>
                    <div style={{ fontSize: "0.8rem", color: "#fee2e2", marginTop: "0.25rem" }}>
                      {selectedTicket.escalation_reason || "Customer requested expedited director escalation."}
                    </div>
                  </div>
                )}

                {/* Timeline display */}
                {ticketTimeline && ticketTimeline.events && (
                  <div style={{ marginBottom: "1rem" }}>
                    <h4 style={{ fontSize: "0.75rem", color: "#9ca3af", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: "0.5rem" }}>
                      Response SLA & Status Timeline
                    </h4>
                    <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
                      {ticketTimeline.events.map((ev: any, idx: number) => (
                        <div key={idx} style={{ display: "flex", gap: "0.75rem", fontSize: "0.8rem" }}>
                          <CheckCircle2 style={{ width: "14px", height: "14px", color: "#10b981", flexShrink: 0, marginTop: "2px" }} />
                          <div>
                            <div style={{ color: "#fff", fontWeight: 600 }}>{ev.label}</div>
                            <div style={{ color: "#9ca3af", fontSize: "0.75rem" }}>{ev.details} • {new Date(ev.timestamp).toLocaleTimeString()}</div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Internal Note */}
                <div style={{ marginTop: "1rem" }}>
                  <h4 style={{ fontSize: "0.75rem", color: "#9ca3af", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: "0.5rem" }}>
                    Internal Staff Audit Note (Confidential)
                  </h4>
                  <textarea
                    rows={2}
                    value={internalNote}
                    onChange={(e) => setInternalNote(e.target.value)}
                    placeholder="Log internal telemetry findings, chauffeur contact notes, or reimbursement IDs..."
                    style={{
                      width: "100%",
                      background: "#111827",
                      border: "1px solid #374151",
                      borderRadius: "6px",
                      color: "#fff",
                      padding: "0.5rem",
                      boxSizing: "border-box",
                      fontSize: "0.825rem",
                      marginBottom: "0.5rem"
                    }}
                  />
                  <button
                    onClick={() => handleAddNote(selectedTicket.id)}
                    style={{
                      background: "#374151",
                      color: "#fff",
                      padding: "0.4rem 0.8rem",
                      borderRadius: "6px",
                      border: "none",
                      fontWeight: 600,
                      fontSize: "0.8rem",
                      cursor: "pointer"
                    }}
                  >
                    Save Internal Note
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      ) : activeSubTab === "service_status" ? (
        <div>
          {/* Service Status Dashboard */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "1rem", marginBottom: "2rem" }}>
            {serviceStatus?.services.map(s => (
              <div key={s.service} style={{ background: "rgba(255,255,255,0.03)", borderRadius: "8px", border: "1px solid rgba(255,255,255,0.08)", padding: "1.25rem" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.5rem" }}>
                  <span style={{ fontWeight: 600, color: "#fff", fontSize: "0.95rem" }}>{s.name}</span>
                  <span style={{
                    padding: "0.2rem 0.5rem",
                    borderRadius: "4px",
                    fontSize: "0.75rem",
                    fontWeight: 600,
                    background: s.status === "OPERATIONAL" ? "rgba(16,185,129,0.15)" : "rgba(234,179,8,0.15)",
                    color: s.status === "OPERATIONAL" ? "#34d399" : "#fbbf24"
                  }}>
                    {s.status}
                  </span>
                </div>
                <div style={{ fontSize: "0.825rem", color: "#9ca3af", display: "flex", justifyContent: "space-between", marginTop: "0.75rem" }}>
                  <span>Uptime: <strong>{s.uptime_percentage}%</strong></span>
                  <span>Response: <strong>{s.latency_ms} ms</strong></span>
                </div>
              </div>
            ))}
          </div>

          {/* Active Advisories */}
          <h3 style={{ fontSize: "1.125rem", color: "#d4af37", fontWeight: 600, marginBottom: "1rem" }}>
            Active Known Incidents & Customer Advisories ({serviceStatus?.incidents.length || 0})
          </h3>
          <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
            {serviceStatus?.incidents.map(inc => (
              <div key={inc.id} style={{ background: "rgba(234,179,8,0.08)", border: "1px solid rgba(234,179,8,0.25)", borderRadius: "8px", padding: "1.25rem" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.5rem" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                    <AlertTriangle style={{ width: "18px", height: "18px", color: "#f59e0b" }} />
                    <span style={{ fontWeight: 600, color: "#fef3c7", fontSize: "1rem" }}>{inc.title}</span>
                  </div>
                  <span style={{ fontSize: "0.75rem", color: "#9ca3af" }}>
                    Service: {inc.affected_service} • {new Date(inc.created_at).toLocaleDateString()}
                  </span>
                </div>
                <p style={{ color: "#fef3c7", fontSize: "0.875rem", margin: 0, opacity: 0.9 }}>
                  {inc.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div>
          {/* Knowledge Base Review */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: "1.25rem" }}>
            {articles.map(art => (
              <div key={art.id} style={{ background: "rgba(255,255,255,0.03)", borderRadius: "8px", border: "1px solid rgba(255,255,255,0.08)", padding: "1.25rem" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "0.5rem" }}>
                  <span style={{ padding: "0.15rem 0.4rem", borderRadius: "4px", fontSize: "0.7rem", background: "rgba(212,175,55,0.15)", color: "#d4af37", fontWeight: 600 }}>
                    {art.category}
                  </span>
                  <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", fontSize: "0.75rem", color: "#9ca3af" }}>
                    <span>👍 {art.helpful_count}</span>
                    <span>👎 {art.not_helpful_count}</span>
                  </div>
                </div>
                <h4 style={{ color: "#fff", fontSize: "1rem", fontWeight: 600, margin: "0.5rem 0" }}>{art.title}</h4>
                <p style={{ color: "#9ca3af", fontSize: "0.825rem", lineHeight: 1.5 }}>{art.summary}</p>
                <div style={{ display: "flex", flexWrap: "wrap", gap: "0.25rem", marginTop: "0.75rem" }}>
                  {art.context_tags.map(tag => (
                    <span key={tag} style={{ background: "#1f2937", color: "#94a3b8", fontSize: "0.7rem", padding: "0.1rem 0.4rem", borderRadius: "4px" }}>
                      #{tag}
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
