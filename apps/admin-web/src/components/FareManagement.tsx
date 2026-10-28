import React, { useState, useEffect } from 'react';
import { fetchWithAuth } from '../services/api';

interface FareRuleItem {
  id: string;
  categoryId: string;
  categoryCode: string;
  categoryName: string;
  version: number;
  baseFare: number;
  perKm: number;
  perMin: number;
  minFare: number;
  fareCap: number;
  surge: number;
  isActive: boolean;
}

interface DisputeItem {
  id: string;
  ride_id: string;
  customer_id: string;
  dispute_reason: string;
  claimed_amount_rupees: number;
  customer_comment: string;
  status: string;
  admin_resolution_notes?: string;
  created_at: string;
}

interface RefundItem {
  id: string;
  payment_id: string;
  ride_id: string;
  customer_id: string;
  amount_rupees: number;
  is_partial: boolean;
  status: string;
  arn_reference?: string;
  reason: string;
  created_at: string;
}

interface CorporateBillingItem {
  ride_id: string;
  cost_center: string;
  project_code: string;
  tax_id: string;
  billing_ref: string;
  amount_rupees: number;
  passenger_name: string;
  requested_at: string;
}

export const FareManagement: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'tariffs' | 'disputes' | 'refunds' | 'corporate'>('tariffs');
  const [rules, setRules] = useState<FareRuleItem[]>([]);
  const [editingRule, setEditingRule] = useState<FareRuleItem | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  // Disputes state
  const [disputes, setDisputes] = useState<DisputeItem[]>([]);
  const [resolvingDispute, setResolvingDispute] = useState<DisputeItem | null>(null);
  const [resolutionAction, setResolutionAction] = useState<'APPROVE_REFUND' | 'REJECT'>('APPROVE_REFUND');
  const [resolutionNotes, setResolutionNotes] = useState('');

  // Refunds state
  const [refunds, setRefunds] = useState<RefundItem[]>([]);

  // Corporate state
  const [corporateBillings, setCorporateBillings] = useState<CorporateBillingItem[]>([]);

  const loadFareRules = async () => {
    const data = await fetchWithAuth('/admin/fares');
    if (data && Array.isArray(data) && data.length > 0) {
      const mapped = data.map((r: any) => ({
        id: r.id,
        categoryId: r.category_id,
        categoryCode: r.category_code,
        categoryName: r.category_name,
        version: r.version,
        baseFare: Math.round((r.base_fare_cents || 0) / 100),
        perKm: Math.round((r.per_km_cents || 0) / 100),
        perMin: Math.round((r.per_minute_cents || 0) / 100),
        minFare: Math.round((r.minimum_fare_cents || 0) / 100),
        fareCap: Math.round((r.fare_cap_cents || 150000) / 100),
        surge: r.surge_multiplier || 1.0,
        isActive: r.is_active ?? true
      }));
      setRules(mapped);
    }
  };

  const loadDisputes = async () => {
    const data = await fetchWithAuth('/admin/disputes');
    if (data && Array.isArray(data)) {
      setDisputes(data);
    }
  };

  const loadRefunds = async () => {
    const data = await fetchWithAuth('/admin/refunds');
    if (data && Array.isArray(data)) {
      setRefunds(data);
    }
  };

  const loadCorporateBillings = async () => {
    const data = await fetchWithAuth('/admin/corporate-billings');
    if (data && Array.isArray(data)) {
      setCorporateBillings(data);
    }
  };

  useEffect(() => {
    loadFareRules();
    loadDisputes();
    loadRefunds();
    loadCorporateBillings();
  }, []);

  const handleSaveRule = async () => {
    if (!editingRule) return;
    setIsSaving(true);

    const payload = {
      category_id: editingRule.categoryId,
      base_fare_cents: Math.round(editingRule.baseFare * 100),
      per_km_cents: Math.round(editingRule.perKm * 100),
      per_minute_cents: Math.round(editingRule.perMin * 100),
      minimum_fare_cents: Math.round(editingRule.minFare * 100),
      fare_cap_cents: Math.round(editingRule.fareCap * 100),
      surge_multiplier: editingRule.surge
    };

    const res = await fetchWithAuth('/admin/fares', {
      method: 'POST',
      body: JSON.stringify(payload)
    });

    if (res) {
      await loadFareRules();
      setEditingRule(null);
    }
    setIsSaving(false);
  };

  const handleResolveDispute = async () => {
    if (!resolvingDispute) return;
    setIsSaving(true);

    const res = await fetchWithAuth(`/admin/disputes/${resolvingDispute.id}/resolve`, {
      method: 'POST',
      body: JSON.stringify({
        resolution: resolutionAction,
        resolution_notes: resolutionNotes || 'Resolved through executive concierge protocol.',
        refund_amount_cents: resolutionAction === 'APPROVE_REFUND' ? Math.round(resolvingDispute.claimed_amount_rupees * 100) : undefined
      })
    });

    if (res) {
      await loadDisputes();
      await loadRefunds();
      setResolvingDispute(null);
      setResolutionNotes('');
    }
    setIsSaving(false);
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Tariff Governance & Financial Settlements</h1>
          <p className="page-description">Authoritative tariffs, fare caps, customer disputes, RBI-compliant refund tracking, and corporate accounts.</p>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 24, borderBottom: '1px solid var(--border-subtle)', paddingBottom: 12 }}>
        <button
          className={activeTab === 'tariffs' ? 'btn-gold' : 'btn-secondary'}
          onClick={() => setActiveTab('tariffs')}
        >
          Tariff Rules & Fare Caps
        </button>
        <button
          className={activeTab === 'disputes' ? 'btn-gold' : 'btn-secondary'}
          onClick={() => { setActiveTab('disputes'); loadDisputes(); }}
        >
          Fare Disputes ({disputes.filter(d => d.status === 'UNDER_REVIEW' || d.status === 'PENDING').length})
        </button>
        <button
          className={activeTab === 'refunds' ? 'btn-gold' : 'btn-secondary'}
          onClick={() => { setActiveTab('refunds'); loadRefunds(); }}
        >
          Refunds & ARN Ledger
        </button>
        <button
          className={activeTab === 'corporate' ? 'btn-gold' : 'btn-secondary'}
          onClick={() => { setActiveTab('corporate'); loadCorporateBillings(); }}
        >
          Corporate Accounts ({corporateBillings.length})
        </button>
      </div>

      {/* Tab 1: Tariffs & Fare Caps */}
      {activeTab === 'tariffs' && (
        <div className="stat-grid" style={{ gridTemplateColumns: 'repeat(3, 1fr)' }}>
          {rules.map(rule => (
            <div key={rule.id} className="stat-card" style={{ borderColor: 'var(--border-gold)' }}>
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span className="badge badge-gold">{rule.categoryName}</span>
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Version {rule.version}.0</span>
                </div>
                <div className="stat-value gold" style={{ fontSize: '1.5rem', marginTop: 12 }}>
                  ₹{rule.baseFare} <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Base Tariff</span>
                </div>
              </div>

              <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', display: 'flex', flexDirection: 'column', gap: 6, margin: '16px 0' }}>
                <div>Distance Rate: <strong>₹{rule.perKm}/km</strong></div>
                <div>Duration Rate: <strong>₹{rule.perMin}/min</strong></div>
                <div>Guaranteed Floor: <strong>₹{rule.minFare}</strong></div>
                <div>Maximum Fare Cap (F81): <strong>₹{rule.fareCap}</strong></div>
                <div>Active Surge: <strong>{rule.surge}x Multiplier</strong></div>
              </div>

              <button className="btn-gold" onClick={() => setEditingRule(rule)}>
                Adjust Tariff & Cap
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Tab 2: Disputes Review */}
      {activeTab === 'disputes' && (
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Dispute ID</th>
                <th>Ride ID</th>
                <th>Reason</th>
                <th>Claimed (₹)</th>
                <th>Customer Notes</th>
                <th>Status</th>
                <th>Lodged At</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {disputes.length === 0 ? (
                <tr>
                  <td colSpan={8} style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>
                    No customer disputes pending review.
                  </td>
                </tr>
              ) : (
                disputes.map(d => (
                  <tr key={d.id}>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{d.id.substring(0, 10)}...</td>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{d.ride_id.substring(0, 8)}...</td>
                    <td><span className="badge badge-gold">{d.dispute_reason}</span></td>
                    <td style={{ color: 'var(--gold-primary)', fontWeight: 'bold' }}>₹{d.claimed_amount_rupees.toFixed(2)}</td>
                    <td style={{ maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{d.customer_comment}</td>
                    <td>
                      <span className={`badge ${d.status === 'APPROVED_REFUND' ? 'badge-green' : d.status === 'REJECTED' ? 'badge-red' : 'badge-gold'}`}>
                        {d.status}
                      </span>
                    </td>
                    <td style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{d.created_at.substring(0, 10)}</td>
                    <td>
                      {d.status === 'UNDER_REVIEW' || d.status === 'PENDING' ? (
                        <button className="btn-gold" style={{ fontSize: '0.78rem', padding: '4px 10px' }} onClick={() => setResolvingDispute(d)}>
                          Review & Resolve
                        </button>
                      ) : (
                        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Resolved</span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Tab 3: Refunds & ARN Ledger */}
      {activeTab === 'refunds' && (
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Refund ID</th>
                <th>Ride ID</th>
                <th>Amount (₹)</th>
                <th>Type</th>
                <th>Bank ARN Reference</th>
                <th>Reason</th>
                <th>Status</th>
                <th>Processed At</th>
              </tr>
            </thead>
            <tbody>
              {refunds.length === 0 ? (
                <tr>
                  <td colSpan={8} style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>
                    No refund transactions processed yet.
                  </td>
                </tr>
              ) : (
                refunds.map(r => (
                  <tr key={r.id}>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{r.id.substring(0, 12)}</td>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{r.ride_id.substring(0, 8)}</td>
                    <td style={{ color: 'var(--gold-primary)', fontWeight: 'bold' }}>₹{r.amount_rupees.toFixed(2)}</td>
                    <td>
                      <span className="badge badge-gold">{r.is_partial ? 'PARTIAL' : 'FULL'}</span>
                    </td>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                      {r.arn_reference || 'PENDING_BATCH'}
                    </td>
                    <td>{r.reason}</td>
                    <td>
                      <span className={`badge ${r.status === 'SETTLED' ? 'badge-green' : 'badge-gold'}`}>
                        {r.status}
                      </span>
                    </td>
                    <td style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{r.created_at.substring(0, 16)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Tab 4: Corporate Cashless Accounts */}
      {activeTab === 'corporate' && (
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Billing Ref</th>
                <th>Ride ID</th>
                <th>Cost Center</th>
                <th>Project Code</th>
                <th>Company GSTIN</th>
                <th>Amount (₹)</th>
                <th>Passenger</th>
                <th>Date</th>
              </tr>
            </thead>
            <tbody>
              {corporateBillings.length === 0 ? (
                <tr>
                  <td colSpan={8} style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>
                    No corporate accounts billed in this cycle.
                  </td>
                </tr>
              ) : (
                corporateBillings.map(c => (
                  <tr key={c.ride_id}>
                    <td style={{ fontFamily: 'monospace', fontWeight: 'bold', color: 'var(--gold-primary)' }}>{c.billing_ref}</td>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{c.ride_id.substring(0, 8)}</td>
                    <td><span className="badge badge-gold">{c.cost_center}</span></td>
                    <td>{c.project_code}</td>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{c.tax_id}</td>
                    <td style={{ fontWeight: 'bold' }}>₹{c.amount_rupees.toFixed(2)}</td>
                    <td>{c.passenger_name}</td>
                    <td style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{c.requested_at?.substring(0, 10)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Modal: Adjust Tariff Schedule & Cap */}
      {editingRule && (
        <div className="modal-overlay" onClick={() => setEditingRule(null)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2 style={{ fontFamily: 'var(--font-display)', color: 'var(--gold-primary)', marginBottom: 8 }}>
              Version Tariff: {editingRule.categoryName}
            </h2>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem', marginBottom: 20 }}>
              Updating this schedule will create Version {editingRule.version + 1} and record an administrative audit entry.
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 14, marginBottom: 24 }}>
              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Base Fare (₹)</label>
                <input
                  type="number"
                  className="search-input"
                  style={{ width: '100%', marginTop: 4 }}
                  value={editingRule.baseFare}
                  onChange={e => setEditingRule({ ...editingRule, baseFare: Number(e.target.value) })}
                />
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Per-Kilometer Rate (₹)</label>
                <input
                  type="number"
                  className="search-input"
                  style={{ width: '100%', marginTop: 4 }}
                  value={editingRule.perKm}
                  onChange={e => setEditingRule({ ...editingRule, perKm: Number(e.target.value) })}
                />
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Duration Rate (₹/min)</label>
                <input
                  type="number"
                  className="search-input"
                  style={{ width: '100%', marginTop: 4 }}
                  value={editingRule.perMin}
                  onChange={e => setEditingRule({ ...editingRule, perMin: Number(e.target.value) })}
                />
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Minimum Floor Fare (₹)</label>
                <input
                  type="number"
                  className="search-input"
                  style={{ width: '100%', marginTop: 4 }}
                  value={editingRule.minFare}
                  onChange={e => setEditingRule({ ...editingRule, minFare: Number(e.target.value) })}
                />
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Eligible Trip Fare Cap (₹) (Feature 81)</label>
                <input
                  type="number"
                  className="search-input"
                  style={{ width: '100%', marginTop: 4 }}
                  value={editingRule.fareCap}
                  onChange={e => setEditingRule({ ...editingRule, fareCap: Number(e.target.value) })}
                />
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Surge Multiplier (1.0x - 3.0x)</label>
                <input
                  type="number"
                  step="0.1"
                  className="search-input"
                  style={{ width: '100%', marginTop: 4 }}
                  value={editingRule.surge}
                  onChange={e => setEditingRule({ ...editingRule, surge: Number(e.target.value) })}
                />
              </div>
            </div>

            <div style={{ display: 'flex', gap: 12 }}>
              <button className="btn-gold" style={{ flex: 1 }} onClick={handleSaveRule} disabled={isSaving}>
                {isSaving ? "Publishing Rule..." : "Publish Versioned Rule"}
              </button>
              <button className="btn-secondary" onClick={() => setEditingRule(null)}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Resolve Dispute */}
      {resolvingDispute && (
        <div className="modal-overlay" onClick={() => setResolvingDispute(null)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2 style={{ fontFamily: 'var(--font-display)', color: 'var(--gold-primary)', marginBottom: 8 }}>
              Resolve Fare Dispute #{resolvingDispute.id.substring(0, 10)}
            </h2>
            <div style={{ background: 'var(--bg-card)', padding: 14, borderRadius: 8, border: '1px solid var(--border-subtle)', marginBottom: 16 }}>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Reason: <strong style={{ color: 'var(--text-primary)' }}>{resolvingDispute.dispute_reason}</strong></div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: 4 }}>Claimed Refund: <strong style={{ color: 'var(--gold-primary)' }}>₹{resolvingDispute.claimed_amount_rupees.toFixed(2)}</strong></div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: 8 }}>"{resolvingDispute.customer_comment}"</div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 20 }}>
              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Adjudication Decision</label>
                <select
                  className="search-input"
                  style={{ width: '100%', marginTop: 4 }}
                  value={resolutionAction}
                  onChange={e => setResolutionAction(e.target.value as any)}
                >
                  <option value="APPROVE_REFUND">Approve & Credit to VIP Wallet</option>
                  <option value="REJECT">Reject Dispute with Explanation</option>
                </select>
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Executive Resolution Notes</label>
                <textarea
                  className="search-input"
                  style={{ width: '100%', marginTop: 4, height: 70 }}
                  value={resolutionNotes}
                  onChange={e => setResolutionNotes(e.target.value)}
                  placeholder="Reasoning for audit trail and customer notification..."
                />
              </div>
            </div>

            <div style={{ display: 'flex', gap: 12 }}>
              <button className="btn-gold" style={{ flex: 1 }} onClick={handleResolveDispute} disabled={isSaving}>
                {isSaving ? "Submitting Resolution..." : "Confirm Adjudication"}
              </button>
              <button className="btn-secondary" onClick={() => setResolvingDispute(null)}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
