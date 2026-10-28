import React, { useState, useEffect } from 'react';
import { fetchWithAuth } from '../services/api';
import { Award, AlertOctagon, HeartHandshake, RefreshCw } from 'lucide-react';

interface ReferralQueueItem {
  referral_id: string;
  referrer_id: string;
  referred_id: string;
  reward_status: string;
  reward_amount_rupees: number;
  fraud_review_status: string;
  fraud_risk_score: number;
  fraud_reason: string;
  created_at: string;
}

interface ServiceRecoveryItem {
  id: string;
  customer_id: string;
  ride_id: string | null;
  amount_rupees: number;
  reason_category: string;
  notes: string;
  issued_by_admin_id: string;
  status: string;
  created_at: string;
}

export const ReferralAndRecoveryConsole: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'referrals' | 'recovery'>('referrals');
  const [referrals, setReferrals] = useState<ReferralQueueItem[]>([]);
  const [recoveryHistory, setRecoveryHistory] = useState<ServiceRecoveryItem[]>([]);
  const [loading, setLoading] = useState(true);

  // Referral review modal state
  const [reviewingReferral, setReviewingReferral] = useState<ReferralQueueItem | null>(null);
  const [reviewDecision, setReviewDecision] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [reviewNotes, setReviewNotes] = useState('');
  const [isSubmittingReview, setIsSubmittingReview] = useState(false);

  // Recovery issuance modal state
  const [showIssueModal, setShowIssueModal] = useState(false);
  const [targetCustomerId, setTargetCustomerId] = useState('');
  const [recoveryAmountRupees, setRecoveryAmountRupees] = useState(500);
  const [recoveryCategory, setRecoveryCategory] = useState('LATE_ARRIVAL');
  const [recoveryNotes, setRecoveryNotes] = useState('');
  const [recoveryRideId, setRecoveryRideId] = useState('');
  const [isSubmittingRecovery, setIsSubmittingRecovery] = useState(false);
  const [actionSuccessMsg, setActionSuccessMsg] = useState<string | null>(null);

  useEffect(() => {
    fetchData();
  }, [activeTab]);

  const fetchData = async () => {
    setLoading(true);
    setActionSuccessMsg(null);
    try {
      if (activeTab === 'referrals') {
        const res = await fetchWithAuth('/api/v1/loyalty-feedback/admin/referrals/fraud-queue');
        if (Array.isArray(res)) {
          setReferrals(res);
        } else {
          setReferrals([]);
        }
      } else {
        const res = await fetchWithAuth('/api/v1/loyalty-feedback/admin/service-recovery/history');
        if (Array.isArray(res)) {
          setRecoveryHistory(res);
        } else {
          setRecoveryHistory([]);
        }
      }
    } catch (e) {
      console.error("Failed to load loyalty console data", e);
    } finally {
      setLoading(false);
    }
  };

  const handleReviewReferral = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reviewingReferral) return;

    setIsSubmittingReview(true);
    try {
      await fetchWithAuth(`/api/v1/loyalty-feedback/admin/referrals/${reviewingReferral.referral_id}/review`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          decision: reviewDecision,
          notes: reviewNotes.trim() || 'Reviewed by Concierge Fraud Desk'
        })
      });

      setActionSuccessMsg(`Referral marked as ${reviewDecision}.`);
      setReviewingReferral(null);
      setReviewNotes('');
      fetchData();
    } catch (err: any) {
      alert(err.message || 'Error processing referral review');
    } finally {
      setIsSubmittingReview(false);
    }
  };

  const handleIssueRecovery = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!targetCustomerId.trim()) {
      alert("Please enter customer user ID or profile ID");
      return;
    }

    setIsSubmittingRecovery(true);
    try {
      await fetchWithAuth('/api/v1/loyalty-feedback/admin/service-recovery', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          customer_id: targetCustomerId.trim(),
          amount_cents: recoveryAmountRupees * 100,
          reason_category: recoveryCategory,
          notes: recoveryNotes.trim() || 'Executive Concierge Service Recovery Credit',
          ride_id: recoveryRideId.trim() || null
        })
      });

      setActionSuccessMsg(`Successfully credited ₹${recoveryAmountRupees} to customer wallet.`);
      setShowIssueModal(false);
      setTargetCustomerId('');
      setRecoveryNotes('');
      setRecoveryRideId('');
      fetchData();
    } catch (err: any) {
      alert(err.message || 'Error issuing service recovery credit');
    } finally {
      setIsSubmittingRecovery(false);
    }
  };

  return (
    <div className="space-y-6" style={{ padding: '4px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2 style={{ fontSize: '1.45rem', fontWeight: 600, color: 'var(--text-primary)', margin: 0, display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Award className="w-6 h-6" style={{ color: '#d4af37' }} />
            Loyalty, Referrals & Service Recovery
          </h2>
          <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)', margin: '4px 0 0 0' }}>
            Features 165 & 175: Referral Fraud Prevention & Goodwill Credit Issuance Console
          </p>
        </div>

        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            onClick={() => fetchData()}
            className="btn btn-secondary"
            style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.8rem' }}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            onClick={() => setShowIssueModal(true)}
            className="btn btn-primary"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              fontSize: '0.82rem',
              backgroundColor: '#d4af37',
              color: '#000',
              fontWeight: 600
            }}
          >
            <HeartHandshake className="w-4 h-4" />
            Issue Recovery Credit
          </button>
        </div>
      </div>

      {actionSuccessMsg && (
        <div style={{ padding: '10px 14px', borderRadius: '8px', backgroundColor: 'rgba(16, 185, 129, 0.15)', border: '1px solid #10b981', color: '#10b981', fontSize: '0.85rem' }}>
          ✓ {actionSuccessMsg}
        </div>
      )}

      {/* KPI Stats Strip */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px' }}>
        <div className="card" style={{ padding: '16px', borderLeft: '4px solid #d4af37' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Active Referrals</div>
          <div style={{ fontSize: '1.6rem', fontWeight: 700, color: 'var(--text-primary)', marginTop: '4px' }}>
            {referrals.length}
          </div>
          <div style={{ fontSize: '0.72rem', color: '#d4af37', marginTop: '2px' }}>Audited guest invitations</div>
        </div>

        <div className="card" style={{ padding: '16px', borderLeft: '4px solid #f59e0b' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Flagged for Review</div>
          <div style={{ fontSize: '1.6rem', fontWeight: 700, color: '#f59e0b', marginTop: '4px' }}>
            {referrals.filter(r => r.fraud_review_status === 'FLAGGED').length}
          </div>
          <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '2px' }}>Device or IP collisions</div>
        </div>

        <div className="card" style={{ padding: '16px', borderLeft: '4px solid #10b981' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Approved Dispatches</div>
          <div style={{ fontSize: '1.6rem', fontWeight: 700, color: '#10b981', marginTop: '4px' }}>
            {referrals.filter(r => r.fraud_review_status === 'APPROVED').length}
          </div>
          <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '2px' }}>Credited to Royal Wallets</div>
        </div>

        <div className="card" style={{ padding: '16px', borderLeft: '4px solid #6366f1' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Recovery Credits Issued</div>
          <div style={{ fontSize: '1.6rem', fontWeight: 700, color: '#6366f1', marginTop: '4px' }}>
            {recoveryHistory.length}
          </div>
          <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '2px' }}>Concierge goodwill grants</div>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '8px', borderBottom: '1px solid var(--border-color)', paddingBottom: '8px' }}>
        <button
          onClick={() => setActiveTab('referrals')}
          className={`btn ${activeTab === 'referrals' ? 'btn-primary' : 'btn-secondary'}`}
          style={{ fontSize: '0.82rem', display: 'flex', alignItems: 'center', gap: '6px' }}
        >
          <AlertOctagon className="w-4 h-4" />
          Referral Fraud Review Queue ({referrals.length})
        </button>
        <button
          onClick={() => setActiveTab('recovery')}
          className={`btn ${activeTab === 'recovery' ? 'btn-primary' : 'btn-secondary'}`}
          style={{ fontSize: '0.82rem', display: 'flex', alignItems: 'center', gap: '6px' }}
        >
          <HeartHandshake className="w-4 h-4" />
          Service Recovery Audit Trail ({recoveryHistory.length})
        </button>
      </div>

      {/* Tab 1: Referral Fraud Review Queue */}
      {activeTab === 'referrals' && (
        <div className="card" style={{ overflow: 'hidden' }}>
          {loading ? (
            <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
              Loading referral fraud queue...
            </div>
          ) : referrals.length === 0 ? (
            <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
              No referral queue entries recorded.
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem', textAlign: 'left' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--border-color)', backgroundColor: 'rgba(255, 255, 255, 0.02)' }}>
                    <th style={{ padding: '12px 16px' }}>Referral ID</th>
                    <th style={{ padding: '12px 16px' }}>Referrer</th>
                    <th style={{ padding: '12px 16px' }}>Referred Guest</th>
                    <th style={{ padding: '12px 16px' }}>Reward</th>
                    <th style={{ padding: '12px 16px' }}>Risk Assessment</th>
                    <th style={{ padding: '12px 16px' }}>Review Status</th>
                    <th style={{ padding: '12px 16px', textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {referrals.map((item) => {
                    const isFlagged = item.fraud_review_status === 'FLAGGED' || item.fraud_risk_score > 0.3;
                    return (
                      <tr key={item.referral_id} style={{ borderBottom: '1px solid var(--border-color)' }}>
                        <td style={{ padding: '12px 16px', fontFamily: 'monospace' }}>
                          {item.referral_id.slice(0, 10)}...
                        </td>
                        <td style={{ padding: '12px 16px', fontFamily: 'monospace' }}>
                          {item.referrer_id.slice(0, 10)}...
                        </td>
                        <td style={{ padding: '12px 16px', fontFamily: 'monospace' }}>
                          {item.referred_id.slice(0, 10)}...
                        </td>
                        <td style={{ padding: '12px 16px', fontWeight: 600, color: '#d4af37' }}>
                          ₹{item.reward_amount_rupees}
                        </td>
                        <td style={{ padding: '12px 16px' }}>
                          <span
                            style={{
                              display: 'inline-block',
                              padding: '2px 8px',
                              borderRadius: '4px',
                              fontSize: '0.72rem',
                              fontWeight: 600,
                              backgroundColor: isFlagged ? 'rgba(239, 68, 68, 0.15)' : 'rgba(16, 185, 129, 0.15)',
                              color: isFlagged ? '#ef4444' : '#10b981',
                              marginRight: '6px'
                            }}
                          >
                            Score: {(item.fraud_risk_score * 100).toFixed(0)}%
                          </span>
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                            {item.fraud_reason}
                          </span>
                        </td>
                        <td style={{ padding: '12px 16px' }}>
                          <span
                            style={{
                              display: 'inline-block',
                              padding: '2px 8px',
                              borderRadius: '4px',
                              fontSize: '0.72rem',
                              fontWeight: 600,
                              backgroundColor:
                                item.fraud_review_status === 'APPROVED'
                                  ? 'rgba(16, 185, 129, 0.15)'
                                  : item.fraud_review_status === 'REJECTED'
                                  ? 'rgba(239, 68, 68, 0.15)'
                                  : 'rgba(245, 158, 11, 0.15)',
                              color:
                                item.fraud_review_status === 'APPROVED'
                                  ? '#10b981'
                                  : item.fraud_review_status === 'REJECTED'
                                  ? '#ef4444'
                                  : '#f59e0b'
                            }}
                          >
                            {item.fraud_review_status}
                          </span>
                        </td>
                        <td style={{ padding: '12px 16px', textAlign: 'right' }}>
                          <button
                            onClick={() => {
                              setReviewingReferral(item);
                              setReviewDecision('APPROVE');
                              setReviewNotes('');
                            }}
                            className="btn btn-secondary"
                            style={{ fontSize: '0.75rem', padding: '4px 8px' }}
                          >
                            Review
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Service Recovery History */}
      {activeTab === 'recovery' && (
        <div className="card" style={{ overflow: 'hidden' }}>
          {loading ? (
            <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
              Loading service recovery history...
            </div>
          ) : recoveryHistory.length === 0 ? (
            <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
              No service recovery credits issued yet.
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem', textAlign: 'left' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--border-color)', backgroundColor: 'rgba(255, 255, 255, 0.02)' }}>
                    <th style={{ padding: '12px 16px' }}>Date</th>
                    <th style={{ padding: '12px 16px' }}>Customer ID</th>
                    <th style={{ padding: '12px 16px' }}>Category</th>
                    <th style={{ padding: '12px 16px' }}>Amount</th>
                    <th style={{ padding: '12px 16px' }}>Investigation Summary</th>
                    <th style={{ padding: '12px 16px' }}>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {recoveryHistory.map((item) => (
                    <tr key={item.id} style={{ borderBottom: '1px solid var(--border-color)' }}>
                      <td style={{ padding: '12px 16px', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                        {new Date(item.created_at).toLocaleString()}
                      </td>
                      <td style={{ padding: '12px 16px', fontFamily: 'monospace' }}>
                        {item.customer_id.slice(0, 10)}...
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <span
                          style={{
                            display: 'inline-block',
                            padding: '2px 8px',
                            borderRadius: '4px',
                            fontSize: '0.72rem',
                            fontWeight: 600,
                            backgroundColor: 'rgba(212, 175, 55, 0.15)',
                            color: '#d4af37'
                          }}
                        >
                          {item.reason_category.replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td style={{ padding: '12px 16px', fontWeight: 700, color: '#10b981' }}>
                        +₹{item.amount_rupees}
                      </td>
                      <td style={{ padding: '12px 16px', color: 'var(--text-primary)' }}>
                        {item.notes}
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <span
                          style={{
                            display: 'inline-block',
                            padding: '2px 8px',
                            borderRadius: '4px',
                            fontSize: '0.72rem',
                            fontWeight: 600,
                            backgroundColor: 'rgba(16, 185, 129, 0.15)',
                            color: '#10b981'
                          }}
                        >
                          {item.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Modal: Review Referral Decision */}
      {reviewingReferral && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.75)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: '16px'
          }}
        >
          <div
            className="card"
            style={{
              maxWidth: '480px',
              width: '100%',
              padding: '24px',
              backgroundColor: 'var(--bg-card)',
              border: '1px solid var(--border-color)',
              borderRadius: '12px'
            }}
          >
            <h3 style={{ fontSize: '1.2rem', fontWeight: 600, color: 'var(--text-primary)', margin: 0 }}>
              Review Referral Fraud Status
            </h3>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              Referral #{reviewingReferral.referral_id.slice(0, 12)}
            </p>

            <form onSubmit={handleReviewReferral} style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)', display: 'block', marginBottom: '6px' }}>
                  Tribunal Decision
                </label>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                  <button
                    type="button"
                    onClick={() => setReviewDecision('APPROVE')}
                    style={{
                      padding: '8px',
                      borderRadius: '6px',
                      border: '1px solid',
                      borderColor: reviewDecision === 'APPROVE' ? '#10b981' : 'var(--border-color)',
                      backgroundColor: reviewDecision === 'APPROVE' ? 'rgba(16, 185, 129, 0.2)' : 'transparent',
                      color: reviewDecision === 'APPROVE' ? '#10b981' : 'var(--text-muted)',
                      fontWeight: 600,
                      cursor: 'pointer'
                    }}
                  >
                    ✓ Approve Reward
                  </button>
                  <button
                    type="button"
                    onClick={() => setReviewDecision('REJECT')}
                    style={{
                      padding: '8px',
                      borderRadius: '6px',
                      border: '1px solid',
                      borderColor: reviewDecision === 'REJECT' ? '#ef4444' : 'var(--border-color)',
                      backgroundColor: reviewDecision === 'REJECT' ? 'rgba(239, 68, 68, 0.2)' : 'transparent',
                      color: reviewDecision === 'REJECT' ? '#ef4444' : 'var(--text-muted)',
                      fontWeight: 600,
                      cursor: 'pointer'
                    }}
                  >
                    ✕ Flag as Fraud
                  </button>
                </div>
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)', display: 'block', marginBottom: '6px' }}>
                  Audit Review Justification
                </label>
                <textarea
                  value={reviewNotes}
                  onChange={(e) => setReviewNotes(e.target.value)}
                  placeholder="State rationale for approval or fraud rejection..."
                  rows={3}
                  className="input"
                  style={{ width: '100%', resize: 'none' }}
                  required
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '8px' }}>
                <button
                  type="button"
                  onClick={() => setReviewingReferral(null)}
                  className="btn btn-secondary"
                  disabled={isSubmittingReview}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={isSubmittingReview}
                  style={{
                    backgroundColor: reviewDecision === 'APPROVE' ? '#10b981' : '#ef4444',
                    color: '#fff',
                    border: 'none'
                  }}
                >
                  {isSubmittingReview ? 'Submitting...' : 'Confirm Decision'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Issue Service Recovery Credit */}
      {showIssueModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.75)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: '16px'
          }}
        >
          <div
            className="card"
            style={{
              maxWidth: '520px',
              width: '100%',
              padding: '24px',
              backgroundColor: 'var(--bg-card)',
              border: '1px solid var(--border-color)',
              borderRadius: '12px'
            }}
          >
            <h3 style={{ fontSize: '1.25rem', fontWeight: 600, color: 'var(--text-primary)', margin: 0, display: 'flex', alignItems: 'center', gap: '8px' }}>
              <HeartHandshake className="w-5 h-5" style={{ color: '#d4af37' }} />
              Issue Service Recovery Credit
            </h3>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              Direct wallet credit dispatch to resolve executive guest service anomalies.
            </p>

            <form onSubmit={handleIssueRecovery} style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)', display: 'block', marginBottom: '6px' }}>
                  Target Customer ID or Phone
                </label>
                <input
                  type="text"
                  value={targetCustomerId}
                  onChange={(e) => setTargetCustomerId(e.target.value)}
                  placeholder="e.g. cust-uuid or user UUID"
                  className="input"
                  style={{ width: '100%' }}
                  required
                />
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)', display: 'block', marginBottom: '6px' }}>
                  Credit Amount (₹ INR)
                </label>
                <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
                  {[200, 500, 1000, 2000].map((preset) => (
                    <button
                      key={preset}
                      type="button"
                      onClick={() => setRecoveryAmountRupees(preset)}
                      style={{
                        flex: 1,
                        padding: '6px',
                        borderRadius: '6px',
                        border: '1px solid',
                        borderColor: recoveryAmountRupees === preset ? '#d4af37' : 'var(--border-color)',
                        backgroundColor: recoveryAmountRupees === preset ? 'rgba(212, 175, 55, 0.15)' : 'transparent',
                        color: recoveryAmountRupees === preset ? '#d4af37' : 'var(--text-muted)',
                        fontSize: '0.82rem',
                        fontWeight: 600
                      }}
                    >
                      ₹{preset}
                    </button>
                  ))}
                </div>
                <input
                  type="number"
                  min="50"
                  max="10000"
                  value={recoveryAmountRupees}
                  onChange={(e) => setRecoveryAmountRupees(Number(e.target.value))}
                  className="input"
                  style={{ width: '100%' }}
                  required
                />
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)', display: 'block', marginBottom: '6px' }}>
                  Reason Category
                </label>
                <select
                  value={recoveryCategory}
                  onChange={(e) => setRecoveryCategory(e.target.value)}
                  className="input"
                  style={{ width: '100%' }}
                >
                  <option value="LATE_ARRIVAL">Chauffeur Late Arrival (&gt; 5 mins)</option>
                  <option value="VEHICLE_DEFECT">Vehicle AC / Amenity Failure</option>
                  <option value="CHAUFFEUR_CONDUCT">Chauffeur Etiquette Anomaly</option>
                  <option value="ROUTE_DEVIATION">Unapproved Detour / Delay</option>
                  <option value="BILLING_DISPUTE">Billing Correction / Toll Adjustment</option>
                  <option value="GOODWILL">Executive Concierge Goodwill</option>
                </select>
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)', display: 'block', marginBottom: '6px' }}>
                  Related Journey ID (Optional)
                </label>
                <input
                  type="text"
                  value={recoveryRideId}
                  onChange={(e) => setRecoveryRideId(e.target.value)}
                  placeholder="e.g. ride_lux_..."
                  className="input"
                  style={{ width: '100%' }}
                />
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)', display: 'block', marginBottom: '6px' }}>
                  Investigation Details & Notes
                </label>
                <textarea
                  value={recoveryNotes}
                  onChange={(e) => setRecoveryNotes(e.target.value)}
                  placeholder="Record factual summary of anomaly and justification for credit..."
                  rows={2}
                  className="input"
                  style={{ width: '100%', resize: 'none' }}
                  required
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '8px' }}>
                <button
                  type="button"
                  onClick={() => setShowIssueModal(false)}
                  className="btn btn-secondary"
                  disabled={isSubmittingRecovery}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={isSubmittingRecovery}
                  style={{
                    backgroundColor: '#d4af37',
                    color: '#000',
                    fontWeight: 600,
                    border: 'none'
                  }}
                >
                  {isSubmittingRecovery ? 'Transmitting Credit...' : `Authorize ₹${recoveryAmountRupees} Credit`}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
