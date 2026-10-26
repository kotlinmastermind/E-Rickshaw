import React, { useState, useEffect } from "react";
import { fetchWithAuth } from "../services/api";

interface Ticket {
  id: string;
  ticket_number?: string;
  user_id: string;
  ride_id?: string;
  subject: string;
  category: string;
  status: string;
  priority: string;
  created_at: string;
}

export const SupportTicketDesk: React.FC = () => {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [selectedTicket, setSelectedTicket] = useState<any | null>(null);
  const [internalNote, setInternalNote] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadTickets();
  }, []);

  const loadTickets = async () => {
    setLoading(true);
    const data = await fetchWithAuth("/admin/support/tickets");
    if (data && Array.isArray(data)) {
      setTickets(data);
    } else {
      setTickets([
        {
          id: "tck-001",
          ticket_number: "TCK-20261024-A8F1",
          user_id: "usr-guest-99",
          ride_id: "ride-maybach-01",
          subject: "Left Champagne Flute in rear console",
          category: "LOST_PROPERTY",
          status: "OPEN",
          priority: "HIGH",
          created_at: new Date().toISOString()
        },
        {
          id: "tck-002",
          ticket_number: "TCK-20261024-C3B2",
          user_id: "usr-guest-42",
          subject: "Corporate Billing VAT Receipt Request",
          category: "BILLING",
          status: "INVESTIGATING",
          priority: "MEDIUM",
          created_at: new Date(Date.now() - 3600000).toISOString()
        }
      ]);
    }
    setLoading(false);
  };

  const handleUpdateStatus = async (ticketId: string, newStatus: string) => {
    await fetchWithAuth(`/admin/support/tickets/${ticketId}/status?status_val=${newStatus}`, { method: "PUT" });
    setTickets(prev => prev.map(t => t.id === ticketId ? { ...t, status: newStatus } : t));
    if (selectedTicket && selectedTicket.id === ticketId) {
      setSelectedTicket({ ...selectedTicket, status: newStatus });
    }
  };

  const handleAddNote = async (ticketId: string) => {
    if (!internalNote.trim()) return;
    await fetchWithAuth(`/admin/support/tickets/${ticketId}/internal-note?note=${encodeURIComponent(internalNote)}`, { method: "POST" });
    alert("Internal staff note recorded to audit trail.");
    setInternalNote("");
  };

  return (
    <div style={{ padding: "1.5rem" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.5rem" }}>
        <div>
          <h2 style={{ fontSize: "1.5rem", fontWeight: 600, color: "var(--text-primary, #fff)" }}>
            Concierge Support Desk & Case Resolution
          </h2>
          <p style={{ color: "var(--text-secondary, #9ca3af)", fontSize: "0.875rem" }}>
            Priority passenger inquiries, incident escalations, and internal staff audit trails.
          </p>
        </div>
        <button
          onClick={loadTickets}
          style={{
            background: "var(--accent-gold, #d4af37)",
            color: "#000",
            padding: "0.5rem 1rem",
            borderRadius: "6px",
            fontWeight: 600,
            cursor: "pointer",
            border: "none"
          }}
        >
          Refresh Queue
        </button>
      </div>

      {loading ? (
        <p style={{ color: "#888" }}>Loading support queues...</p>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: selectedTicket ? "1fr 1fr" : "1fr", gap: "1.5rem" }}>
          <div style={{ background: "rgba(255,255,255,0.03)", borderRadius: "8px", border: "1px solid rgba(255,255,255,0.08)", overflow: "hidden" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "0.875rem" }}>
              <thead style={{ background: "rgba(255,255,255,0.05)", borderBottom: "1px solid rgba(255,255,255,0.1)" }}>
                <tr>
                  <th style={{ padding: "0.75rem 1rem" }}>Ticket Ref</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Category</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Subject</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Priority</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Status</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {tickets.map(t => (
                  <tr
                    key={t.id}
                    onClick={() => setSelectedTicket(t)}
                    style={{
                      borderBottom: "1px solid rgba(255,255,255,0.05)",
                      cursor: "pointer",
                      background: selectedTicket?.id === t.id ? "rgba(212,175,55,0.1)" : "transparent"
                    }}
                  >
                    <td style={{ padding: "0.75rem 1rem", fontWeight: 600, color: "#d4af37" }}>{t.ticket_number || t.id.slice(0, 8)}</td>
                    <td style={{ padding: "0.75rem 1rem" }}>{t.category}</td>
                    <td style={{ padding: "0.75rem 1rem" }}>{t.subject}</td>
                    <td style={{ padding: "0.75rem 1rem" }}>
                      <span style={{
                        padding: "0.2rem 0.5rem",
                        borderRadius: "4px",
                        fontSize: "0.75rem",
                        background: t.priority === "HIGH" ? "#7f1d1d" : "#1e293b",
                        color: t.priority === "HIGH" ? "#fca5a5" : "#94a3b8"
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
                          padding: "0.25rem 0.5rem"
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

          {selectedTicket && (
            <div style={{ background: "rgba(255,255,255,0.03)", borderRadius: "8px", border: "1px solid rgba(255,255,255,0.08)", padding: "1.25rem" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
                <h3 style={{ fontSize: "1.125rem", color: "#d4af37", fontWeight: 600 }}>
                  Case Inspector — {selectedTicket.ticket_number || selectedTicket.id}
                </h3>
                <button
                  onClick={() => setSelectedTicket(null)}
                  style={{ background: "transparent", color: "#888", border: "none", cursor: "pointer", fontSize: "1.2rem" }}
                >
                  ✕
                </button>
              </div>

              <div style={{ fontSize: "0.875rem", color: "#e5e7eb", marginBottom: "1rem" }}>
                <p><strong>Passenger User ID:</strong> {selectedTicket.user_id}</p>
                <p><strong>Associated Ride ID:</strong> {selectedTicket.ride_id || "None"}</p>
                <p><strong>Subject:</strong> {selectedTicket.subject}</p>
                <p><strong>Reported:</strong> {new Date(selectedTicket.created_at).toLocaleString()}</p>
              </div>

              <div style={{ marginTop: "1.5rem" }}>
                <h4 style={{ fontSize: "0.875rem", color: "#9ca3af", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: "0.5rem" }}>
                  Staff Internal Note (Confidential)
                </h4>
                <textarea
                  rows={3}
                  value={internalNote}
                  onChange={(e) => setInternalNote(e.target.value)}
                  placeholder="Record internal escalation notes or dispatch tracking ID..."
                  style={{
                    width: "100%",
                    background: "#111827",
                    border: "1px solid #374151",
                    borderRadius: "6px",
                    color: "#fff",
                    padding: "0.75rem",
                    boxSizing: "border-box",
                    fontSize: "0.875rem",
                    marginBottom: "0.5rem"
                  }}
                />
                <button
                  onClick={() => handleAddNote(selectedTicket.id)}
                  style={{
                    background: "#374151",
                    color: "#fff",
                    padding: "0.5rem 1rem",
                    borderRadius: "6px",
                    border: "none",
                    fontWeight: 600,
                    cursor: "pointer"
                  }}
                >
                  Save Internal Note
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
