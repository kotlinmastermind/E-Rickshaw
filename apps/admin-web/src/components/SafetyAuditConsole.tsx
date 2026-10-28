import React, { useState, useEffect } from 'react';
import { fetchWithAuth } from '../services/api';
import { ShieldAlert, AlertTriangle, Clock, UserX, RefreshCw } from 'lucide-react';

interface IncidentCase {
  id: string;
  ticket_number: string;
  ride_id: string | null;
  reporter_user_id: string;
  reporter_role: string;
  category: string;
  severity: string;
  description: string;
  status: string;
  resolution_notes: string | null;
  follow_up_action: string | null;
  created_at: string;
  resolved_at: string | null;
}

interface EmergencySosAlert {
  id: string;
  ride_id: string;
  triggered_by_user_id: string;
  alert_type: string;
  latitude: number;
  longitude: number;
  status: string;
  contacts_notified_count: number;
  soc_agent_notes: string | null;
  created_at: string;
}

interface DriverBlockEntry {
  id: string;
  customer_id: string;
  driver_id: string;
  ride_id: string | null;
  reason: string;
  is_active: boolean;
  created_at: string;
}

export const SafetyAuditConsole: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'incidents' | 'sos' | 'blocks'>('incidents');
  const [incidents, setIncidents] = useState<IncidentCase[]>([]);
  const [sosAlerts, setSosAlerts] = useState<EmergencySosAlert[]>([]);
  const [driverBlocks, setDriverBlocks] = useState<DriverBlockEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<string>('');

  // Resolution modal state
  const [resolvingTicket, setResolvingTicket] = useState<IncidentCase | null>(null);
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [followUpAction, setFollowUpAction] = useState('DRIVER_ACADEMY_REFRESHER');
  const [submittingResolution, setSubmittingResolution] = useState(false);

  useEffect(() => {
    fetchSafetyData();
  }, [statusFilter]);

  const fetchSafetyData = async () => {
    setLoading(true);
    try {
      const endpoint = statusFilter
        ? `/safety/admin/cases?status_filter=${statusFilter}`
        : `/safety/admin/cases`;
      const data = await fetchWithAuth(endpoint);
      if (data) {
        setIncidents(data.incident_cases || []);
        setSosAlerts(data.emergency_sos_alerts || []);
        setDriverBlocks(data.driver_blocks || []);
      }
    } catch (err) {
      console.error('Failed to load safety audit data', err);
    } finally {
      setLoading(false);
    }
  };

  const handleResolveCase = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!resolvingTicket) return;

    setSubmittingResolution(true);
    try {
      const res = await fetchWithAuth(`/safety/admin/cases/${resolvingTicket.ticket_number}/resolve`, {
        method: 'POST',
        body: JSON.stringify({
          resolution_notes: resolutionNotes,
          follow_up_action: followUpAction
        })
      });

      if (res && res.status === 'RESOLVED') {
        setResolvingTicket(null);
        setResolutionNotes('');
        fetchSafetyData();
      }
    } catch (err) {
      console.error('Resolution failed', err);
    } finally {
      setSubmittingResolution(false);
    }
  };

  const activeSosCount = sosAlerts.filter(a => a.status === 'ACTIVE').length;
  const criticalCount = incidents.filter(i => i.severity === 'CRITICAL' && i.status !== 'RESOLVED').length;
  const openIncidentsCount = incidents.filter(i => i.status === 'OPEN' || i.status === 'INVESTIGATING').length;

  return (
    <div className="luxury-page-container">
      {/* Page Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 24 }}>
        <div>
          <div style={{ fontSize: '0.75rem', letterSpacing: '0.12em', color: 'var(--gold-primary)', textTransform: 'uppercase', fontWeight: 600 }}>
            Security Operations & Assurance
          </div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 700, color: 'var(--text-main)', margin: '4px 0 0 0' }}>
            Safety Case Audit Trail & SOC Command
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.88rem', margin: '4px 0 0 0' }}>
            Live emergency SOS triggers, passenger incident reports, driver block enforcement, and regulatory compliance logs.
          </p>
        </div>

        <button
          onClick={fetchSafetyData}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '8px 14px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--border-gold)',
            color: 'var(--gold-primary)',
            borderRadius: 8,
            cursor: 'pointer',
            fontSize: '0.82rem',
            fontWeight: 600
          }}
        >
          <RefreshCw size={14} /> Refresh SOC Feed
        </button>
      </div>

      {/* Top Metrics Row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 24 }}>
        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-subtle)', borderRadius: 12, padding: 18 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Active SOS Alerts</span>
            <AlertTriangle size={18} color="#ef4444" />
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 700, color: activeSosCount > 0 ? '#ef4444' : 'var(--text-main)', marginTop: 8 }}>
            {activeSosCount}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: 4 }}>
            Requires immediate SOC dispatcher verification
          </div>
        </div>

        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-subtle)', borderRadius: 12, padding: 18 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Critical / Harassment</span>
            <ShieldAlert size={18} color="#f97316" />
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 700, color: criticalCount > 0 ? '#f97316' : 'var(--text-main)', marginTop: 8 }}>
            {criticalCount}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: 4 }}>
            Zero-tolerance escalations pending supervisor action
          </div>
        </div>

        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-subtle)', borderRadius: 12, padding: 18 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Open Incidents</span>
            <Clock size={18} color="#eab308" />
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 700, color: 'var(--text-main)', marginTop: 8 }}>
            {openIncidentsCount}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: 4 }}>
            Pending investigation & vehicle inspections
          </div>
        </div>

        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-subtle)', borderRadius: 12, padding: 18 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Driver Blocks</span>
            <UserX size={18} color="#a855f7" />
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 700, color: 'var(--text-main)', marginTop: 8 }}>
            {driverBlocks.length}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: 4 }}>
            Permanent dispatch exclusion pairings
          </div>
        </div>
      </div>

      {/* Tabs and Filters */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--border-subtle)', paddingBottom: 12, marginBottom: 18 }}>
        <div style={{ display: 'flex', gap: 12 }}>
          <button
            onClick={() => setActiveTab('incidents')}
            style={{
              padding: '8px 16px',
              borderRadius: 8,
              border: activeTab === 'incidents' ? '1px solid var(--gold-primary)' : '1px solid transparent',
              background: activeTab === 'incidents' ? 'rgba(212, 175, 55, 0.12)' : 'transparent',
              color: activeTab === 'incidents' ? 'var(--gold-primary)' : 'var(--text-muted)',
              cursor: 'pointer',
              fontWeight: 600,
              fontSize: '0.88rem'
            }}
          >
            Incident Reports ({incidents.length})
          </button>

          <button
            onClick={() => setActiveTab('sos')}
            style={{
              padding: '8px 16px',
              borderRadius: 8,
              border: activeTab === 'sos' ? '1px solid #ef4444' : '1px solid transparent',
              background: activeTab === 'sos' ? 'rgba(239, 68, 68, 0.12)' : 'transparent',
              color: activeTab === 'sos' ? '#ef4444' : 'var(--text-muted)',
              cursor: 'pointer',
              fontWeight: 600,
              fontSize: '0.88rem'
            }}
          >
            Emergency SOS Alerts ({sosAlerts.length})
          </button>

          <button
            onClick={() => setActiveTab('blocks')}
            style={{
              padding: '8px 16px',
              borderRadius: 8,
              border: activeTab === 'blocks' ? '1px solid #a855f7' : '1px solid transparent',
              background: activeTab === 'blocks' ? 'rgba(168, 85, 247, 0.12)' : 'transparent',
              color: activeTab === 'blocks' ? '#a855f7' : 'var(--text-muted)',
              cursor: 'pointer',
              fontWeight: 600,
              fontSize: '0.88rem'
            }}
          >
            Driver Block Registry ({driverBlocks.length})
          </button>
        </div>

        {activeTab === 'incidents' && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Status:</span>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              style={{
                background: 'var(--surface-elevated)',
                border: '1px solid var(--border-subtle)',
                color: 'var(--text-main)',
                padding: '6px 10px',
                borderRadius: 6,
                fontSize: '0.82rem'
              }}
            >
              <option value="">All Statuses</option>
              <option value="OPEN">OPEN</option>
              <option value="ESCALATED">ESCALATED</option>
              <option value="INVESTIGATING">INVESTIGATING</option>
              <option value="RESOLVED">RESOLVED</option>
            </select>
          </div>
        )}
      </div>

      {/* Main Content Sections */}
      {loading ? (
        <div style={{ padding: 48, textAlign: 'center', color: 'var(--gold-primary)' }}>
          Loading safety telemetry feed...
        </div>
      ) : activeTab === 'incidents' ? (
        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-subtle)', borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
            <thead>
              <tr style={{ background: 'var(--surface-elevated)', borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-muted)' }}>
                <th style={{ padding: '12px 16px' }}>Ticket #</th>
                <th style={{ padding: '12px 16px' }}>Category</th>
                <th style={{ padding: '12px 16px' }}>Severity</th>
                <th style={{ padding: '12px 16px' }}>Reporter</th>
                <th style={{ padding: '12px 16px' }}>Description</th>
                <th style={{ padding: '12px 16px' }}>Status</th>
                <th style={{ padding: '12px 16px' }}>Action</th>
              </tr>
            </thead>
            <tbody>
              {incidents.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ padding: 32, textAlign: 'center', color: 'var(--text-muted)' }}>
                    No incident reports found matching filter.
                  </td>
                </tr>
              ) : (
                incidents.map((inc) => (
                  <tr key={inc.id} style={{ borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-main)' }}>
                    <td style={{ padding: '12px 16px', fontFamily: 'monospace', color: 'var(--gold-primary)', fontWeight: 600 }}>
                      {inc.ticket_number}
                    </td>
                    <td style={{ padding: '12px 16px', fontWeight: 500 }}>
                      {inc.category.replace(/_/g, ' ')}
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <span
                        style={{
                          display: 'inline-block',
                          padding: '3px 8px',
                          borderRadius: 6,
                          fontSize: '0.72rem',
                          fontWeight: 700,
                          background:
                            inc.severity === 'CRITICAL'
                              ? 'rgba(239, 68, 68, 0.2)'
                              : inc.severity === 'HIGH'
                              ? 'rgba(249, 115, 22, 0.2)'
                              : 'rgba(100, 116, 139, 0.2)',
                          color:
                            inc.severity === 'CRITICAL'
                              ? '#ef4444'
                              : inc.severity === 'HIGH'
                              ? '#f97316'
                              : '#cbd5e1'
                        }}
                      >
                        {inc.severity}
                      </span>
                    </td>
                    <td style={{ padding: '12px 16px', color: 'var(--text-muted)' }}>
                      {inc.reporter_role}
                    </td>
                    <td style={{ padding: '12px 16px', maxWidth: 280, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {inc.description}
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <span
                        style={{
                          display: 'inline-block',
                          padding: '3px 8px',
                          borderRadius: 6,
                          fontSize: '0.72rem',
                          fontWeight: 600,
                          background:
                            inc.status === 'RESOLVED'
                              ? 'rgba(16, 185, 129, 0.15)'
                              : inc.status === 'ESCALATED'
                              ? 'rgba(239, 68, 68, 0.15)'
                              : 'rgba(212, 175, 55, 0.15)',
                          color:
                            inc.status === 'RESOLVED'
                              ? '#10b981'
                              : inc.status === 'ESCALATED'
                              ? '#ef4444'
                              : 'var(--gold-primary)'
                        }}
                      >
                        {inc.status}
                      </span>
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      {inc.status !== 'RESOLVED' ? (
                        <button
                          onClick={() => {
                            setResolvingTicket(inc);
                            setResolutionNotes('');
                          }}
                          style={{
                            padding: '4px 10px',
                            background: 'var(--surface-elevated)',
                            border: '1px solid var(--border-gold)',
                            color: 'var(--gold-primary)',
                            borderRadius: 6,
                            cursor: 'pointer',
                            fontSize: '0.75rem',
                            fontWeight: 600
                          }}
                        >
                          Resolve Case
                        </button>
                      ) : (
                        <span style={{ fontSize: '0.75rem', color: '#10b981' }}>
                          ✓ {inc.follow_up_action || 'Closed'}
                        </span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      ) : activeTab === 'sos' ? (
        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-subtle)', borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
            <thead>
              <tr style={{ background: 'var(--surface-elevated)', borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-muted)' }}>
                <th style={{ padding: '12px 16px' }}>Alert ID</th>
                <th style={{ padding: '12px 16px' }}>Ride ID</th>
                <th style={{ padding: '12px 16px' }}>Type</th>
                <th style={{ padding: '12px 16px' }}>GPS Location</th>
                <th style={{ padding: '12px 16px' }}>Contacts Notified</th>
                <th style={{ padding: '12px 16px' }}>Status</th>
                <th style={{ padding: '12px 16px' }}>SOC Notes</th>
              </tr>
            </thead>
            <tbody>
              {sosAlerts.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ padding: 32, textAlign: 'center', color: 'var(--text-muted)' }}>
                    No SOS emergency alerts triggered. Fleet operations nominal.
                  </td>
                </tr>
              ) : (
                sosAlerts.map((sos) => (
                  <tr key={sos.id} style={{ borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-main)' }}>
                    <td style={{ padding: '12px 16px', fontFamily: 'monospace', color: '#ef4444', fontWeight: 600 }}>
                      {sos.id.slice(0, 8)}...
                    </td>
                    <td style={{ padding: '12px 16px', fontFamily: 'monospace', color: 'var(--text-muted)' }}>
                      {sos.ride_id.slice(0, 8)}...
                    </td>
                    <td style={{ padding: '12px 16px', fontWeight: 600, color: '#ef4444' }}>
                      {sos.alert_type}
                    </td>
                    <td style={{ padding: '12px 16px', color: 'var(--text-muted)' }}>
                      {sos.latitude.toFixed(4)}, {sos.longitude.toFixed(4)}
                    </td>
                    <td style={{ padding: '12px 16px', color: 'var(--text-main)', fontWeight: 600 }}>
                      {sos.contacts_notified_count} contacts
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <span
                        style={{
                          display: 'inline-block',
                          padding: '3px 8px',
                          borderRadius: 6,
                          fontSize: '0.72rem',
                          fontWeight: 700,
                          background: sos.status === 'ACTIVE' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(16, 185, 129, 0.2)',
                          color: sos.status === 'ACTIVE' ? '#ef4444' : '#10b981'
                        }}
                      >
                        {sos.status}
                      </span>
                    </td>
                    <td style={{ padding: '12px 16px', color: 'var(--text-muted)' }}>
                      {sos.soc_agent_notes || 'Automatic priority alert dispatched'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      ) : (
        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-subtle)', borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
            <thead>
              <tr style={{ background: 'var(--surface-elevated)', borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-muted)' }}>
                <th style={{ padding: '12px 16px' }}>Block ID</th>
                <th style={{ padding: '12px 16px' }}>Customer ID</th>
                <th style={{ padding: '12px 16px' }}>Chauffeur ID</th>
                <th style={{ padding: '12px 16px' }}>Reason</th>
                <th style={{ padding: '12px 16px' }}>Status</th>
                <th style={{ padding: '12px 16px' }}>Date</th>
              </tr>
            </thead>
            <tbody>
              {driverBlocks.length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ padding: 32, textAlign: 'center', color: 'var(--text-muted)' }}>
                    No chauffeur block records active.
                  </td>
                </tr>
              ) : (
                driverBlocks.map((blk) => (
                  <tr key={blk.id} style={{ borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-main)' }}>
                    <td style={{ padding: '12px 16px', fontFamily: 'monospace', color: 'var(--gold-primary)' }}>
                      {blk.id.slice(0, 8)}...
                    </td>
                    <td style={{ padding: '12px 16px', fontFamily: 'monospace', color: 'var(--text-muted)' }}>
                      {blk.customer_id.slice(0, 8)}...
                    </td>
                    <td style={{ padding: '12px 16px', fontFamily: 'monospace', color: 'var(--text-muted)' }}>
                      {blk.driver_id.slice(0, 8)}...
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      {blk.reason}
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{ color: blk.is_active ? '#ef4444' : '#10b981', fontWeight: 600 }}>
                        {blk.is_active ? 'BLOCKED' : 'INACTIVE'}
                      </span>
                    </td>
                    <td style={{ padding: '12px 16px', color: 'var(--text-muted)' }}>
                      {new Date(blk.created_at).toLocaleDateString()}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Case Resolution Modal */}
      {resolvingTicket && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.7)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000
          }}
        >
          <div
            style={{
              background: 'var(--surface-card)',
              border: '1px solid var(--border-gold)',
              borderRadius: 16,
              padding: 24,
              width: 480,
              maxWidth: '90%'
            }}
          >
            <h3 style={{ margin: '0 0 12px 0', color: 'var(--gold-primary)', fontSize: '1.15rem' }}>
              Resolve Incident: {resolvingTicket.ticket_number}
            </h3>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: 16 }}>
              Category: {resolvingTicket.category} • Severity: {resolvingTicket.severity}
            </p>

            <form onSubmit={handleResolveCase}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: 6 }}>
                  Resolution Notes & Findings
                </label>
                <textarea
                  required
                  rows={4}
                  value={resolutionNotes}
                  onChange={(e) => setResolutionNotes(e.target.value)}
                  placeholder="Detail investigation findings, chauffeur statement, and corrective actions taken..."
                  style={{
                    width: '100%',
                    background: 'var(--surface-elevated)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 8,
                    padding: 10,
                    color: 'var(--text-main)',
                    fontSize: '0.85rem'
                  }}
                />
              </div>

              <div style={{ marginBottom: 20 }}>
                <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: 6 }}>
                  Follow-up Action Taken
                </label>
                <select
                  value={followUpAction}
                  onChange={(e) => setFollowUpAction(e.target.value)}
                  style={{
                    width: '100%',
                    background: 'var(--surface-elevated)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 8,
                    padding: '8px 10px',
                    color: 'var(--text-main)',
                    fontSize: '0.85rem'
                  }}
                >
                  <option value="DRIVER_ACADEMY_REFRESHER">Driver Academy Refresher Training</option>
                  <option value="VEHICLE_DEPOT_INSPECTION">Vehicle Depot Mechanical Inspection</option>
                  <option value="WARNING_ISSUED">Formal Warning Issued to Chauffeur</option>
                  <option value="DRIVER_SUSPENDED">Chauffeur Suspended Pending In-Depth Probe</option>
                  <option value="PASSENGER_COMPENSATION_CREDITED">Passenger Assured & Fare Refunded</option>
                  <option value="POLICE_LIAISON_ENGAGED">Police Liaison Officer Engaged</option>
                  <option value="EXONERATED_FALSE_ALARM">Exonerated (Telemetry Disproved Claim)</option>
                </select>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
                <button
                  type="button"
                  onClick={() => setResolvingTicket(null)}
                  style={{
                    padding: '8px 16px',
                    background: 'transparent',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 8,
                    color: 'var(--text-muted)',
                    cursor: 'pointer'
                  }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submittingResolution}
                  style={{
                    padding: '8px 16px',
                    background: 'var(--gold-primary)',
                    border: 'none',
                    borderRadius: 8,
                    color: '#0d0d11',
                    fontWeight: 700,
                    cursor: 'pointer'
                  }}
                >
                  {submittingResolution ? 'Saving...' : 'Confirm Resolution'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
