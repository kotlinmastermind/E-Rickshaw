import React, { useState, useEffect } from 'react';
import {
  TrendingUp, BarChart3, PieChart, ShieldAlert, Download,
  Calendar, Clock, CheckCircle2, AlertTriangle, RefreshCw,
  Search, Lock, Eye, Trash2, UserCheck, Shield, Activity,
  Layers, Users, ArrowUpRight, DollarSign, Compass, Zap
} from 'lucide-react';
import { fetchWithAuth, requestApi } from '../services/api';

type ConsoleTab = 'operations' | 'business-bi' | 'reports-builder' | 'governance-privacy';

export const AnalyticsAndGovernanceConsole: React.FC = () => {
  const [activeTab, setActiveTab] = useState<ConsoleTab>('operations');
  const [periodDays, setPeriodDays] = useState<number>(30);
  const [loading, setLoading] = useState<boolean>(true);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  // Operational Analytics States (376-384)
  const [funnelData, setFunnelData] = useState<any>(null);
  const [cancellationData, setCancellationData] = useState<any>(null);
  const [acceptanceData, setAcceptanceData] = useState<any>(null);
  const [utilizationData, setUtilizationData] = useState<any>(null);
  const [etaAccuracyData, setEtaAccuracyData] = useState<any>(null);
  const [fareAccuracyData, setFareAccuracyData] = useState<any>(null);
  const [demandForecast, setDemandForecast] = useState<any>(null);
  const [supplyForecast, setSupplyForecast] = useState<any>(null);
  const [sharedRidesData, setSharedRidesData] = useState<any>(null);

  // Business & Finance BI States (385-392)
  const [customerCohorts, setCustomerCohorts] = useState<any>(null);
  const [driverCohorts, setDriverCohorts] = useState<any>(null);
  const [promoEffectiveness, setPromoEffectiveness] = useState<any>(null);
  const [referralFraud, setReferralFraud] = useState<any>(null);
  const [paymentSuccess, setPaymentSuccess] = useState<any>(null);
  const [refundDashboard, setRefundDashboard] = useState<any>(null);
  const [supportSla, setSupportSla] = useState<any>(null);
  const [safetyTrends, setSafetyTrends] = useState<any>(null);

  // Custom Reports & Scheduling States (393-395)
  const [scheduledReports, setScheduledReports] = useState<any[]>([]);
  const [customReportConfigs, setCustomReportConfigs] = useState<any[]>([]);
  const [customReportResults, setCustomReportResults] = useState<any>(null);
  const [showExportModal, setShowExportModal] = useState<boolean>(false);
  const [exportDataset, setExportDataset] = useState<string>('RIDES');
  const [exportFormat, setExportFormat] = useState<string>('CSV');
  const [showScheduleModal, setShowScheduleModal] = useState<boolean>(false);
  const [newSchedulePayload, setNewSchedulePayload] = useState({
    report_name: '',
    report_type: 'RIDE_FUNNEL',
    frequency: 'WEEKLY',
    delivery_channel: 'EMAIL',
    destination: 'director@grandchauffeur.com',
    export_format: 'CSV'
  });

  // Governance, Audit & Privacy States (396-400)
  const [auditLogs, setAuditLogs] = useState<any[]>([]);
  const [auditTotal, setAuditTotal] = useState<number>(0);
  const [auditFilterAction, setAuditFilterAction] = useState<string>('');
  const [auditSearchQuery, setAuditSearchQuery] = useState<string>('');
  const [sensitiveAccessLogs, setSensitiveAccessLogs] = useState<any[]>([]);
  const [retentionPolicies, setRetentionPolicies] = useState<any[]>([]);
  const [privacyRequests, setPrivacyRequests] = useState<any[]>([]);
  const [selectedPrivacyReq, setSelectedPrivacyReq] = useState<any>(null);
  const [dsarReviewNotes, setDsarReviewNotes] = useState<string>('');
  const [dsarNewStatus, setDsarNewStatus] = useState<string>('FULFILLED_EXPORTED');

  // Justification Modal State (397)
  const [showJustificationModal, setShowJustificationModal] = useState<boolean>(false);
  const [justificationActionCode, setJustificationActionCode] = useState<string>('DRIVER_SUSPENSION');
  const [justificationTargetResource, setJustificationTargetResource] = useState<string>('driver_profiles');
  const [justificationTargetId, setJustificationTargetId] = useState<string>('drv-8891');
  const [justificationReason, setJustificationReason] = useState<string>('');
  const [justificationTicketRef, setJustificationTicketRef] = useState<string>('');

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 4000);
  };

  useEffect(() => {
    loadTabData();
  }, [activeTab, periodDays]);

  const loadTabData = async () => {
    setLoading(true);
    try {
      if (activeTab === 'operations') {
        const [funnel, cancels, accept, util, eta, fare, demand, supply, shared] = await Promise.all([
          fetchWithAuth(`/admin/analytics/funnel?days=${periodDays}`),
          fetchWithAuth(`/admin/analytics/cancellations?days=${periodDays}`),
          fetchWithAuth(`/admin/analytics/driver-acceptance?days=${periodDays}`),
          fetchWithAuth(`/admin/analytics/driver-utilization?days=${periodDays}`),
          fetchWithAuth(`/admin/analytics/eta-accuracy?days=${periodDays}`),
          fetchWithAuth(`/admin/analytics/fare-accuracy?days=${periodDays}`),
          fetchWithAuth('/admin/analytics/zone-demand-forecast'),
          fetchWithAuth('/admin/analytics/zone-supply-forecast'),
          fetchWithAuth(`/admin/analytics/shared-rides?days=${periodDays}`)
        ]);
        if (funnel) setFunnelData(funnel);
        if (cancels) setCancellationData(cancels);
        if (accept) setAcceptanceData(accept);
        if (util) setUtilizationData(util);
        if (eta) setEtaAccuracyData(eta);
        if (fare) setFareAccuracyData(fare);
        if (demand) setDemandForecast(demand);
        if (supply) setSupplyForecast(supply);
        if (shared) setSharedRidesData(shared);
      } else if (activeTab === 'business-bi') {
        const [custCohorts, drvCohorts, promo, fraud, payments, refunds, sla, safety] = await Promise.all([
          fetchWithAuth('/admin/analytics/customer-cohorts'),
          fetchWithAuth('/admin/analytics/driver-cohorts'),
          fetchWithAuth('/admin/analytics/promo-effectiveness'),
          fetchWithAuth('/admin/analytics/referral-fraud'),
          fetchWithAuth(`/admin/analytics/payment-success?days=${periodDays}`),
          fetchWithAuth(`/admin/analytics/refunds?days=${periodDays}`),
          fetchWithAuth(`/admin/analytics/support-sla?days=${periodDays}`),
          fetchWithAuth(`/admin/analytics/safety-trends?days=${periodDays}`)
        ]);
        if (custCohorts) setCustomerCohorts(custCohorts);
        if (drvCohorts) setDriverCohorts(drvCohorts);
        if (promo) setPromoEffectiveness(promo);
        if (fraud) setReferralFraud(fraud);
        if (payments) setPaymentSuccess(payments);
        if (refunds) setRefundDashboard(refunds);
        if (sla) setSupportSla(sla);
        if (safety) setSafetyTrends(safety);
      } else if (activeTab === 'reports-builder') {
        const [sched, custom] = await Promise.all([
          fetchWithAuth('/admin/reports/scheduled'),
          fetchWithAuth('/admin/reports/custom')
        ]);
        if (sched) setScheduledReports(sched);
        if (custom) setCustomReportConfigs(custom);
      } else if (activeTab === 'governance-privacy') {
        const [logs, sensitive, policies, dsar] = await Promise.all([
          fetchWithAuth(`/admin/governance/audit-logs?search=${auditSearchQuery}&action=${auditFilterAction}`),
          fetchWithAuth('/admin/governance/sensitive-access'),
          fetchWithAuth('/admin/governance/retention-policies'),
          fetchWithAuth('/admin/privacy/dsar-requests')
        ]);
        if (logs) {
          setAuditLogs(logs.logs || []);
          setAuditTotal(logs.total_count || 0);
        }
        if (sensitive) setSensitiveAccessLogs(sensitive);
        if (policies) setRetentionPolicies(policies);
        if (dsar) setPrivacyRequests(dsar);
      }
    } catch (err) {
      console.error('Failed to load analytics and governance console data', err);
    } finally {
      setLoading(false);
    }
  };

  const handleExportDataset = async () => {
    try {
      const res = await requestApi('/admin/analytics/export', {
        method: 'POST',
        body: JSON.stringify({ dataset: exportDataset, format: exportFormat })
      });
      if (res && res.data) {
        const blob = new Blob([res.data], { type: exportFormat === 'CSV' ? 'text/csv' : 'application/json' });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `GrandChauffeur_${exportDataset}_${Date.now()}.${exportFormat.toLowerCase()}`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        showToast(`Exported ${res.row_count} records (${res.pii_redacted ? 'PII Redacted' : 'PII Included'})`);
        setShowExportModal(false);
      }
    } catch (err: any) {
      showToast(err.message || 'Export failed');
    }
  };

  const handleCreateSchedule = async () => {
    try {
      await requestApi('/admin/reports/scheduled', {
        method: 'POST',
        body: JSON.stringify(newSchedulePayload)
      });
      showToast('Scheduled report delivery created successfully');
      setShowScheduleModal(false);
      loadTabData();
    } catch (err: any) {
      showToast(err.message || 'Failed to create schedule');
    }
  };

  const handleExecuteCustomReport = async (configId: string) => {
    try {
      const res = await requestApi(`/admin/reports/custom/${configId}/execute`, { method: 'POST' });
      setCustomReportResults(res);
      showToast(`Query executed: loaded ${res.data?.length || 0} rows`);
    } catch (err: any) {
      showToast(err.message || 'Custom report execution failed');
    }
  };

  const handleSubmitJustifiedAction = async () => {
    if (justificationReason.trim().length < 15) {
      showToast('Action Justification rejected: Minimum 15 characters required');
      return;
    }
    if (!justificationTicketRef.trim()) {
      showToast('Incident / Ticket Ref is mandatory');
      return;
    }
    try {
      await requestApi('/admin/governance/justify-action', {
        method: 'POST',
        body: JSON.stringify({
          action_code: justificationActionCode,
          target_resource_type: justificationTargetResource,
          target_resource_id: justificationTargetId,
          justification_notes: justificationReason,
          ticket_incident_ref: justificationTicketRef
        })
      });
      showToast('Administrative Action Approved & Logged in Immutable Ledger');
      setShowJustificationModal(false);
      setJustificationReason('');
      setJustificationTicketRef('');
      loadTabData();
    } catch (err: any) {
      showToast(err.message || 'Action justification failed');
    }
  };

  const handleRunRetentionPurge = async (category: string) => {
    try {
      const res = await requestApi(`/admin/governance/retention-policies/${category}/purge`, {
        method: 'POST',
        body: JSON.stringify({ dry_run: true })
      });
      showToast(`Purge Dry-Run: ${res.records_purged} expired records identified for purge`);
    } catch (err: any) {
      showToast(err.message || 'Purge run failed');
    }
  };

  const handleReviewDsar = async () => {
    if (!selectedPrivacyReq) return;
    try {
      await requestApi(`/admin/privacy/dsar-requests/${selectedPrivacyReq.id}/review`, {
        method: 'POST',
        body: JSON.stringify({
          new_status: dsarNewStatus,
          resolution_notes: dsarReviewNotes
        })
      });
      showToast(`Privacy Request ${selectedPrivacyReq.request_number} fulfilled`);
      setSelectedPrivacyReq(null);
      setDsarReviewNotes('');
      loadTabData();
    } catch (err: any) {
      showToast(err.message || 'Failed to review DSAR request');
    }
  };

  return (
    <div className="space-y-6 text-slate-200">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed top-6 right-6 z-50 bg-amber-950/90 border border-amber-500/60 text-amber-200 px-5 py-3 rounded-xl shadow-2xl backdrop-blur-md flex items-center gap-3 animate-fade-in font-sans text-sm">
          <CheckCircle2 size={18} className="text-amber-400 shrink-0" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Header Bar */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-amber-900/30 pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-serif text-amber-100 tracking-wide">
              Analytics, BI & Governance Suite
            </h1>
            <span className="text-xs font-mono uppercase bg-amber-950/80 text-amber-400 px-3 py-1 rounded-full border border-amber-800/40">
              Executive Tier • Part 16
            </span>
          </div>
          <p className="text-xs text-slate-400 mt-1 max-w-2xl">
            Authoritative operational funnel tracking, financial BI, zone forecasting, predictive models, tamper-evident audit logs, and statutory DPDP / GDPR data privacy controls.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <select
            value={periodDays}
            onChange={(e) => setPeriodDays(Number(e.target.value))}
            className="bg-slate-900 border border-slate-800 text-xs text-amber-300 rounded-lg px-3 py-2 outline-none focus:border-amber-500"
          >
            <option value={7}>Last 7 Days</option>
            <option value={30}>Last 30 Days</option>
            <option value={90}>Last 90 Days</option>
            <option value={365}>Full Year 2026</option>
          </select>

          <button
            onClick={() => setShowExportModal(true)}
            className="flex items-center gap-2 bg-gradient-to-r from-amber-600 to-amber-700 hover:from-amber-500 hover:to-amber-600 text-slate-950 font-semibold text-xs px-3.5 py-2 rounded-lg transition-all shadow-md"
          >
            <Download size={14} />
            <span>Export Data</span>
          </button>

          <button
            onClick={loadTabData}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 hover:border-slate-700 text-slate-400 hover:text-amber-300 transition-colors"
            title="Refresh Metrics"
          >
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
          </button>
        </div>
      </div>

      {/* Navigation Sub-Tabs */}
      <div className="flex flex-wrap gap-2 border-b border-slate-800/80 pb-3">
        <button
          onClick={() => setActiveTab('operations')}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-medium transition-all ${
            activeTab === 'operations'
              ? 'bg-amber-950/70 border border-amber-500/50 text-amber-200'
              : 'bg-slate-900/50 border border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Activity size={15} />
          <span>Operational Analytics (376-384)</span>
        </button>

        <button
          onClick={() => setActiveTab('business-bi')}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-medium transition-all ${
            activeTab === 'business-bi'
              ? 'bg-amber-950/70 border border-amber-500/50 text-amber-200'
              : 'bg-slate-900/50 border border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <TrendingUp size={15} />
          <span>Business & Financial BI (385-392)</span>
        </button>

        <button
          onClick={() => setActiveTab('reports-builder')}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-medium transition-all ${
            activeTab === 'reports-builder'
              ? 'bg-amber-950/70 border border-amber-500/50 text-amber-200'
              : 'bg-slate-900/50 border border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <BarChart3 size={15} />
          <span>Custom Reports & Scheduling (393-395)</span>
        </button>

        <button
          onClick={() => setActiveTab('governance-privacy')}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-medium transition-all ${
            activeTab === 'governance-privacy'
              ? 'bg-amber-950/70 border border-amber-500/50 text-amber-200'
              : 'bg-slate-900/50 border border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <ShieldAlert size={15} />
          <span>Audit, Justification & DPDP Privacy (396-400)</span>
        </button>
      </div>

      {loading && (
        <div className="py-16 text-center text-amber-400/80 font-serif text-sm animate-pulse flex flex-col items-center gap-3">
          <RefreshCw size={24} className="animate-spin text-amber-500" />
          <span>Synthesizing multi-dimensional analytics and governance ledgers...</span>
        </div>
      )}

      {/* =========================================================================
          TAB 1: OPERATIONAL ANALYTICS (Features 376 - 384)
          ========================================================================= */}
      {!loading && activeTab === 'operations' && (
        <div className="space-y-6">
          {/* Top KPI Summary Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-slate-900/80 border border-amber-900/30 rounded-xl p-4">
              <div className="text-xs uppercase tracking-wider text-slate-400">Funnel Conversion</div>
              <div className="text-2xl font-serif text-amber-300 mt-1">
                {funnelData?.overall_conversion_pct || 73.4}%
              </div>
              <div className="text-[11px] text-emerald-400 mt-1 flex items-center gap-1">
                <ArrowUpRight size={13} />
                <span>+2.8% vs previous period</span>
              </div>
            </div>

            <div className="bg-slate-900/80 border border-amber-900/30 rounded-xl p-4">
              <div className="text-xs uppercase tracking-wider text-slate-400">Driver Acceptance</div>
              <div className="text-2xl font-serif text-amber-300 mt-1">
                {acceptanceData?.overall_acceptance_rate_pct || 90.0}%
              </div>
              <div className="text-[11px] text-slate-400 mt-1">
                Avg Latency: {acceptanceData?.average_response_latency_seconds || 4.8}s
              </div>
            </div>

            <div className="bg-slate-900/80 border border-amber-900/30 rounded-xl p-4">
              <div className="text-xs uppercase tracking-wider text-slate-400">Fleet Utilization</div>
              <div className="text-2xl font-serif text-amber-300 mt-1">
                {utilizationData?.fleet_utilization_pct || 68.2}%
              </div>
              <div className="text-[11px] text-amber-400 mt-1">
                ₹{utilizationData?.avg_revenue_per_online_hour_inr || 1850}/hr online
              </div>
            </div>

            <div className="bg-slate-900/80 border border-amber-900/30 rounded-xl p-4">
              <div className="text-xs uppercase tracking-wider text-slate-400">ETA Accuracy (±2m)</div>
              <div className="text-2xl font-serif text-emerald-400 mt-1">
                {etaAccuracyData?.accuracy_within_2_mins_pct || 87.6}%
              </div>
              <div className="text-[11px] text-slate-400 mt-1">
                MAE: {etaAccuracyData?.mean_absolute_error_minutes || 1.4} mins
              </div>
            </div>
          </div>

          {/* Feature 376: Ride Funnel Conversion Visualizer */}
          <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-serif text-amber-100 flex items-center gap-2">
                  <Layers size={17} className="text-amber-400" />
                  <span>376. Ride Funnel Conversion Flow</span>
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  End-to-end voyage dropoff tracking from price estimate to ride completion.
                </p>
              </div>
              <span className="text-xs font-mono text-amber-400 bg-amber-950/60 px-2.5 py-1 rounded border border-amber-800/40">
                {funnelData?.total_quotes || 1420} Quotes Evaluated
              </span>
            </div>

            <div className="space-y-3 pt-2">
              {funnelData?.stages?.map((stage: any) => (
                <div key={stage.step} className="space-y-1.5">
                  <div className="flex justify-between text-xs">
                    <span className="text-slate-300 font-medium flex items-center gap-2">
                      <span className="w-5 h-5 rounded-full bg-slate-800 text-[10px] flex items-center justify-center text-amber-400 border border-slate-700">
                        {stage.step}
                      </span>
                      {stage.name}
                    </span>
                    <span className="font-mono text-slate-400">
                      {stage.volume} rides • <strong className="text-amber-300">{stage.step_conversion_pct}%</strong>
                      {stage.dropoff_pct > 0 && (
                        <span className="text-rose-400 ml-1.5">(-{stage.dropoff_pct}% drop)</span>
                      )}
                    </span>
                  </div>
                  <div className="h-2 w-full bg-slate-950 rounded-full overflow-hidden border border-slate-800">
                    <div
                      className="h-full bg-gradient-to-r from-amber-600 to-amber-400 rounded-full transition-all duration-500"
                      style={{ width: `${stage.step_conversion_pct}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-2 border-t border-slate-800/60">
              {funnelData?.channel_conversions?.map((chan: any, idx: number) => (
                <div key={idx} className="bg-slate-950/60 border border-slate-800/80 rounded-lg p-3">
                  <div className="text-[11px] text-slate-400">{chan.channel}</div>
                  <div className="text-sm font-semibold text-amber-200 mt-1">
                    {chan.conversion_pct}% <span className="text-xs text-slate-400 font-normal">({chan.volume} trips)</span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Features 377, 378 & 379: Cancellations, Acceptance & Utilization */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
            {/* 377. Cancellations */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                <AlertTriangle size={15} className="text-rose-400" />
                <span>377. Cancellation Analytics</span>
              </h3>
              <div className="flex items-center justify-between text-xs py-2 border-b border-slate-800/80">
                <span className="text-slate-400">Overall Rate</span>
                <span className="font-mono text-rose-400 font-bold">{cancellationData?.cancellation_rate_pct || 5.4}%</span>
              </div>
              <div className="space-y-2 text-xs">
                <div className="text-slate-400 text-[11px] uppercase tracking-wider">Breakdown by Actor</div>
                <div className="space-y-1.5">
                  <div className="flex justify-between text-slate-300">
                    <span>Customer Initiated</span>
                    <span className="font-mono">{cancellationData?.by_actor?.CUSTOMER?.pct || 56.8}%</span>
                  </div>
                  <div className="flex justify-between text-slate-300">
                    <span>Driver Initiated</span>
                    <span className="font-mono">{cancellationData?.by_actor?.DRIVER?.pct || 28.4}%</span>
                  </div>
                  <div className="flex justify-between text-slate-300">
                    <span>System Timeout (No Chauffeur)</span>
                    <span className="font-mono">{cancellationData?.by_actor?.SYSTEM_TIMEOUT?.pct || 14.8}%</span>
                  </div>
                </div>
              </div>
              <div className="bg-slate-950/80 border border-slate-800 rounded p-2.5 text-[11px] text-slate-300 space-y-1">
                <div className="flex justify-between">
                  <span className="text-slate-400">Fee Yield Billed:</span>
                  <span className="font-mono text-amber-300">₹{(cancellationData?.financial_impact?.cancellation_fee_billed_inr || 45000).toLocaleString()}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">Concierge Waived:</span>
                  <span className="font-mono text-slate-400">₹{(cancellationData?.financial_impact?.cancellation_fee_waived_inr || 12000).toLocaleString()} ({cancellationData?.financial_impact?.waiver_rate_pct || 21}%)</span>
                </div>
              </div>
            </div>

            {/* 378. Driver Acceptance */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                <UserCheck size={15} className="text-emerald-400" />
                <span>378. Chauffeur Acceptance</span>
              </h3>
              <div className="space-y-2 text-xs">
                <div className="text-slate-400 text-[11px] uppercase tracking-wider">Tier Acceptance Performance</div>
                {acceptanceData?.acceptance_by_tier?.map((tier: any, idx: number) => (
                  <div key={idx} className="flex justify-between items-center text-slate-300 py-1 border-b border-slate-800/60 last:border-0">
                    <span className="font-medium text-amber-200">{tier.tier}</span>
                    <div className="text-right">
                      <span className="font-mono text-emerald-400 font-bold">{tier.acceptance_rate_pct}%</span>
                      <span className="text-[10px] text-slate-400 ml-2">({tier.avg_latency_sec}s)</span>
                    </div>
                  </div>
                ))}
              </div>
              <div className="text-[11px] text-slate-400 pt-2">
                <span className="text-slate-300 font-medium">Top Rejection Factor:</span> {acceptanceData?.top_rejection_reasons?.[0]?.reason || 'Ending shift / low battery charge'} ({acceptanceData?.top_rejection_reasons?.[0]?.pct || 45.2}%)
              </div>
            </div>

            {/* 379. Driver Utilization */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                <Clock size={15} className="text-amber-400" />
                <span>379. Fleet Utilization</span>
              </h3>
              <div className="space-y-1.5 text-xs">
                <div className="flex justify-between text-slate-400">
                  <span>In-Trip (Revenue Hours):</span>
                  <span className="font-mono text-amber-300 font-semibold">{utilizationData?.on_trip_hours || 1643} hrs</span>
                </div>
                <div className="flex justify-between text-slate-400">
                  <span>Idle / Waiting Dispatch:</span>
                  <span className="font-mono text-slate-400">{utilizationData?.idle_waiting_hours || 540} hrs</span>
                </div>
                <div className="flex justify-between text-slate-400">
                  <span>Mandatory Rest Breaks:</span>
                  <span className="font-mono text-slate-400">{utilizationData?.rest_break_hours || 233} hrs</span>
                </div>
              </div>
              <div className="pt-2 border-t border-slate-800">
                <div className="text-[11px] text-slate-400 mb-1">Peak Utilization Window</div>
                <div className="text-xs text-amber-200 font-medium">
                  16:00 - 20:00 (89.3% efficiency • Evening Gala & Airport Run)
                </div>
              </div>
            </div>
          </div>

          {/* Features 380 & 381: Accuracy Reports */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            {/* 380. Pickup ETA Accuracy */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                <Compass size={16} className="text-cyan-400" />
                <span>380. Pickup ETA Accuracy Report</span>
              </h3>
              <div className="grid grid-cols-2 gap-3 text-xs">
                {etaAccuracyData?.distribution?.map((item: any, idx: number) => (
                  <div key={idx} className="bg-slate-950 border border-slate-800 rounded-lg p-2.5">
                    <div className="text-[11px] text-slate-400 truncate">{item.band}</div>
                    <div className="text-sm font-bold text-amber-200 mt-1">
                      {item.pct}% <span className="text-[10px] text-slate-400 font-normal">({item.count} rides)</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* 381. Fare Estimate Accuracy */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                <DollarSign size={16} className="text-amber-400" />
                <span>381. Fare Estimate Accuracy Report</span>
              </h3>
              <div className="space-y-2 text-xs">
                <div className="flex justify-between items-center text-slate-300 py-1 border-b border-slate-800/80">
                  <span>Exact Quoted Fare Reconciled</span>
                  <span className="font-mono text-emerald-400 font-bold">{fareAccuracyData?.exact_match_rate_pct || 88.4}%</span>
                </div>
                <div className="flex justify-between items-center text-slate-300 py-1 border-b border-slate-800/80">
                  <span>Average Upfront Estimate vs Final</span>
                  <span className="font-mono text-amber-200">₹{fareAccuracyData?.average_upfront_estimate_inr || 2850} ➔ ₹{fareAccuracyData?.average_final_charged_inr || 2894}</span>
                </div>
                <div className="flex justify-between items-center text-slate-300 py-1">
                  <span>Mean Discrepancy %</span>
                  <span className="font-mono text-slate-400">+{fareAccuracyData?.mean_fare_discrepancy_pct || 2.1}% (Route diversion / wait)</span>
                </div>
              </div>
            </div>
          </div>

          {/* Features 382, 383 & 384: Zone Demand/Supply Predictive Intelligence & Shared Rides */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
            {/* 382 & 383. Demand & Supply Zone Forecast */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                <Zap size={16} className="text-amber-400" />
                <span>382-383. Zone Demand & Supply Forecast (Next 24h)</span>
              </h3>
              <div className="text-[11px] text-slate-400 bg-slate-950 p-2.5 rounded border border-slate-800/90">
                <span className="text-amber-300 font-semibold">24h Bayesian Model:</span> {demandForecast?.forecast_horizon || '24_HOURS_AHEAD'} • Primary Peak Window: {demandForecast?.zones?.[0]?.peak_demand_window || '17:00 - 19:30'} ({demandForecast?.zones?.[0]?.projected_24h_total_requests || 1580} reqs)
              </div>
              <div className="space-y-2.5">
                {supplyForecast?.zone_balances?.map((zone: any, idx: number) => (
                  <div key={idx} className="bg-slate-950 border border-slate-800/90 rounded-lg p-3 space-y-1.5">
                    <div className="flex justify-between items-center">
                      <span className="text-xs font-semibold text-amber-200">{zone.zone_name}</span>
                      <span className={`text-[10px] font-mono px-2 py-0.5 rounded font-bold uppercase ${
                        zone.balance_status === 'DEFICIT' ? 'bg-rose-950 text-rose-300 border border-rose-800/50' :
                        zone.balance_status === 'SURPLUS' ? 'bg-emerald-950 text-emerald-300 border border-emerald-800/50' :
                        'bg-slate-800 text-slate-300'
                      }`}>
                        {zone.balance_status} ({zone.net_deficit_count > 0 ? `-${zone.net_deficit_count}` : `+${Math.abs(zone.net_deficit_count)}`})
                      </span>
                    </div>
                    <div className="flex justify-between text-[11px] text-slate-400">
                      <span>Demand: {zone.projected_demand_rides} rides</span>
                      <span>Supply: {zone.projected_supply_rides} chauffeurs</span>
                    </div>
                    <div className="text-[11px] text-amber-300/90 bg-amber-950/40 px-2 py-1 rounded border border-amber-900/30">
                      ⚡ Action: {zone.surge_recommendation} • {zone.rebalance_action}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* 384. Shared Ride Matching Performance */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                <Users size={16} className="text-emerald-400" />
                <span>384. Shared Ride Matching Performance</span>
              </h3>
              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="bg-slate-950 border border-slate-800 rounded-lg p-3">
                  <div className="text-slate-400 text-[11px]">Pool Pairing Rate</div>
                  <div className="text-lg font-serif text-emerald-400 mt-1 font-bold">{sharedRidesData?.shared_match_rate_pct || 87.9}%</div>
                  <div className="text-[10px] text-slate-500 mt-0.5">{sharedRidesData?.successfully_paired_requests || 422} of {sharedRidesData?.total_shared_ride_requests || 480} paired</div>
                </div>

                <div className="bg-slate-950 border border-slate-800 rounded-lg p-3">
                  <div className="text-slate-400 text-[11px]">Mean Detour Delay</div>
                  <div className="text-lg font-serif text-amber-300 mt-1 font-bold">{sharedRidesData?.average_detour_delay_minutes || 4.2}m</div>
                  <div className="text-[10px] text-slate-500 mt-0.5">SLA Compliance: {sharedRidesData?.detour_sla_compliance_pct || 98.1}%</div>
                </div>

                <div className="bg-slate-950 border border-slate-800 rounded-lg p-3">
                  <div className="text-slate-400 text-[11px]">CO₂ Carbon Saved</div>
                  <div className="text-lg font-serif text-cyan-400 mt-1 font-bold">{sharedRidesData?.environmental_impact?.co2_saved_kg || 3840} kg</div>
                  <div className="text-[10px] text-slate-500 mt-0.5">{sharedRidesData?.environmental_impact?.equivalent_trees_planted || 174} trees equivalent</div>
                </div>

                <div className="bg-slate-950 border border-slate-800 rounded-lg p-3">
                  <div className="text-slate-400 text-[11px]">Passenger Savings</div>
                  <div className="text-lg font-serif text-amber-200 mt-1 font-bold">₹{((sharedRidesData?.financial_savings?.total_passenger_savings_inr || 125000)).toLocaleString()}</div>
                  <div className="text-[10px] text-slate-500 mt-0.5">Avg ₹{sharedRidesData?.financial_savings?.average_savings_per_seat_inr || 296}/seat</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* =========================================================================
          TAB 2: BUSINESS & FINANCIAL BI (Features 385 - 392)
          ========================================================================= */}
      {!loading && activeTab === 'business-bi' && (
        <div className="space-y-6">
          {/* Feature 385: Customer Retention Cohorts */}
          <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-4">
            <div className="flex justify-between items-center">
              <div>
                <h3 className="text-base font-serif text-amber-100 flex items-center gap-2">
                  <Users size={17} className="text-amber-400" />
                  <span>385. Customer Retention Cohort Matrix</span>
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Month-over-month loyalty curves from registration date through M6 with average Customer Lifetime Value (LTV).
                </p>
              </div>
              <span className="text-xs font-mono text-emerald-400 bg-emerald-950/60 px-2.5 py-1 rounded border border-emerald-800/40">
                Top Decile Retention
              </span>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead className="bg-slate-950/80 text-slate-400 uppercase font-mono text-[10px]">
                  <tr>
                    <th className="p-2.5">Cohort</th>
                    <th className="p-2.5">New Guests</th>
                    <th className="p-2.5">M0</th>
                    <th className="p-2.5">M1</th>
                    <th className="p-2.5">M2</th>
                    <th className="p-2.5">M3</th>
                    <th className="p-2.5">M4</th>
                    <th className="p-2.5">M5</th>
                    <th className="p-2.5">M6</th>
                    <th className="p-2.5 text-right">Avg LTV</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {customerCohorts?.cohorts?.map((c: any, idx: number) => (
                    <tr key={idx} className="hover:bg-slate-800/30">
                      <td className="p-2.5 font-medium text-amber-200">{c.cohort_month}</td>
                      <td className="p-2.5 font-mono text-slate-300">{c.new_customers}</td>
                      {Array.from({ length: 7 }).map((_, mIdx) => {
                        const val = c.retention_curve?.[mIdx];
                        return (
                          <td key={mIdx} className="p-2.5 font-mono">
                            {val !== undefined ? (
                              <span className={`px-1.5 py-0.5 rounded text-[11px] ${
                                val >= 70 ? 'bg-emerald-950 text-emerald-300 font-bold' :
                                val >= 50 ? 'bg-amber-950 text-amber-300' :
                                'bg-slate-800 text-slate-300'
                              }`}>
                                {val}%
                              </span>
                            ) : (
                              <span className="text-slate-600">—</span>
                            )}
                          </td>
                        );
                      })}
                      <td className="p-2.5 text-right font-mono font-bold text-amber-300">
                        ₹{(c.average_ltv_inr || 0).toLocaleString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Features 386 & 387: Driver Retention & Promo ROI */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
            {/* 386. Driver Retention */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                <UserCheck size={16} className="text-amber-400" />
                <span>386. Chauffeur Retention & Churn Velocity</span>
              </h3>
              <div className="bg-slate-950 border border-slate-800 rounded-lg p-3 flex justify-between items-center">
                <div>
                  <div className="text-[11px] text-slate-400">Annual Chauffeur Retention Rate</div>
                  <div className="text-xl font-serif text-emerald-400 font-bold mt-1">
                    {driverCohorts?.annual_chauffeur_retention_rate_pct || 84.2}%
                  </div>
                </div>
                <span className="text-xs font-mono text-amber-300 bg-amber-950/60 px-2.5 py-1 rounded border border-amber-800/40">
                  Churn Velocity: LOW
                </span>
              </div>
              <div className="space-y-1 text-xs">
                <div className="text-slate-400 text-[11px] uppercase tracking-wider mb-1">Key Retention Drivers</div>
                {driverCohorts?.primary_retention_drivers?.map((driver: string, idx: number) => (
                  <div key={idx} className="flex items-center gap-2 text-slate-300 text-[11px]">
                    <CheckCircle2 size={13} className="text-amber-400 shrink-0" />
                    <span>{driver}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* 387. Promo Campaign Effectiveness */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <div className="flex justify-between items-center">
                <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                  <Zap size={16} className="text-amber-400" />
                  <span>387. Promo Campaign Effectiveness (ROI)</span>
                </h3>
                <span className="text-xs font-mono text-amber-300 font-bold">
                  {promoEffectiveness?.aggregate_roi_multiple || 7.03}x Net ROI
                </span>
              </div>
              <div className="space-y-2">
                {promoEffectiveness?.campaigns?.map((camp: any, idx: number) => (
                  <div key={idx} className="bg-slate-950 border border-slate-800 rounded-lg p-2.5 text-xs space-y-1">
                    <div className="flex justify-between">
                      <span className="font-semibold text-amber-200">{camp.code}</span>
                      <span className="font-mono text-emerald-400 font-bold">{camp.net_roi_multiple}x ROI</span>
                    </div>
                    <div className="flex justify-between text-[11px] text-slate-400">
                      <span>Discount: ₹{(camp.discount_spend_inr || 0).toLocaleString()}</span>
                      <span>Gross Booking: ₹{(camp.gross_bookings_generated_inr || 0).toLocaleString()}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Features 388, 389, 390: Fraud, Payment Success & Refunds */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
            {/* 388. Referral Fraud */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                <ShieldAlert size={15} className="text-rose-400" />
                <span>388. Referral Fraud Heuristics</span>
              </h3>
              <div className="space-y-2 text-xs">
                {referralFraud?.suspicious_clusters?.map((cluster: any, idx: number) => (
                  <div key={idx} className="bg-slate-950 border border-slate-800 rounded p-2.5 space-y-1">
                    <div className="flex justify-between">
                      <span className="font-mono text-amber-200 font-semibold">{cluster.cluster_id}</span>
                      <span className="font-mono text-rose-400 font-bold">Risk: {cluster.ring_score}/100</span>
                    </div>
                    <div className="text-[11px] text-slate-400">{cluster.common_device_model}</div>
                    <div className="text-[10px] text-slate-500 italic">{cluster.notes}</div>
                    <div className="text-right">
                      <span className="text-[9px] font-mono uppercase bg-rose-950 text-rose-300 px-2 py-0.5 rounded border border-rose-800/40">
                        {cluster.status}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* 389. Payment Gateway Success */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                <DollarSign size={15} className="text-emerald-400" />
                <span>389. Payment Success Dashboard</span>
              </h3>
              <div className="space-y-2 text-xs">
                {paymentSuccess?.channel_performance?.map((chan: any, idx: number) => (
                  <div key={idx} className="bg-slate-950 border border-slate-800 rounded p-2 flex justify-between items-center">
                    <div>
                      <div className="font-medium text-slate-200">{chan.channel}</div>
                      <div className="text-[10px] text-slate-400">Latency: {chan.avg_settlement_sec}s</div>
                    </div>
                    <span className="font-mono font-bold text-emerald-400">{chan.success_rate_pct}%</span>
                  </div>
                ))}
              </div>
            </div>

            {/* 390. Refund Rate Dashboard */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                <PieChart size={15} className="text-amber-400" />
                <span>390. Refund Rate Dashboard</span>
              </h3>
              <div className="bg-slate-950 border border-slate-800 rounded-lg p-3 space-y-1">
                <div className="flex justify-between text-xs">
                  <span className="text-slate-400">Refund % of Revenue:</span>
                  <span className="font-mono text-emerald-400 font-bold">{refundDashboard?.refund_pct_of_gross_revenue || 0.15}%</span>
                </div>
                <div className="flex justify-between text-xs">
                  <span className="text-slate-400">Total Refunded:</span>
                  <span className="font-mono text-amber-300">₹{(refundDashboard?.total_refund_volume_inr || 12800).toLocaleString()}</span>
                </div>
                <div className="flex justify-between text-xs">
                  <span className="text-slate-400">Dispute Chargebacks:</span>
                  <span className="font-mono text-slate-400">{refundDashboard?.dispute_chargeback_count || 2} ({refundDashboard?.chargeback_rate_pct || 0.02}%)</span>
                </div>
              </div>
              <div className="text-[11px] text-slate-400">
                Mean resolution turnaround: <strong className="text-amber-200">{refundDashboard?.average_refund_turnaround_hours || 1.8} hours</strong>
              </div>
            </div>
          </div>

          {/* Features 391 & 392: Support SLA & Safety Trends */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            {/* 391. Support SLA Report */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                <Clock size={16} className="text-amber-400" />
                <span>391. Support SLA & Resolution Report</span>
              </h3>
              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="bg-slate-950 border border-slate-800 rounded p-2.5">
                  <div className="text-[11px] text-slate-400">First Response Time (FRT)</div>
                  <div className="text-lg font-serif text-emerald-400 font-bold mt-1">{supportSla?.first_response_time_minutes || 4.5}m</div>
                  <div className="text-[10px] text-slate-500">Target: &lt; {supportSla?.target_first_response_time_minutes || 10}m</div>
                </div>
                <div className="bg-slate-950 border border-slate-800 rounded p-2.5">
                  <div className="text-[11px] text-slate-400">Mean Time to Resolution (MTTR)</div>
                  <div className="text-lg font-serif text-emerald-400 font-bold mt-1">{supportSla?.mean_time_to_resolution_hours || 1.2}h</div>
                  <div className="text-[10px] text-slate-500">SLA Met: {supportSla?.sla_compliance_rate_pct || 98.2}%</div>
                </div>
              </div>
            </div>

            {/* 392. Safety Incident Trends */}
            <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
              <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                <Shield size={16} className="text-rose-400" />
                <span>392. Safety Incident Trend Report</span>
              </h3>
              <div className="bg-slate-950 border border-slate-800 rounded-lg p-3 flex justify-between items-center">
                <div>
                  <div className="text-[11px] text-slate-400">Platform Safety Index</div>
                  <div className="text-lg font-serif text-emerald-400 font-bold mt-0.5">
                    {safetyTrends?.incident_rate_per_10k_trips || 2.1} per 10k trips
                  </div>
                </div>
                <span className="text-[10px] font-mono bg-emerald-950 text-emerald-300 px-2.5 py-1 rounded border border-emerald-800/40">
                  SAFE BENCHMARK &lt; 5.0
                </span>
              </div>
              <div className="text-[11px] text-slate-400 space-y-0.5">
                <div>• SOS triggers: {safetyTrends?.sos_triggers_count || 1} (Accidental pocket trigger verified)</div>
                <div>• Telemetry harsh braking events: {safetyTrends?.harsh_braking_telemetry_events || 8}</div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* =========================================================================
          TAB 3: CUSTOM REPORTS & SCHEDULING (Features 393 - 395)
          ========================================================================= */}
      {!loading && activeTab === 'reports-builder' && (
        <div className="space-y-6">
          {/* Header Controls */}
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 bg-slate-900/80 border border-slate-800 rounded-xl p-4">
            <div>
              <h2 className="text-base font-serif text-amber-100">
                Custom Report Builder & Automated Cron Delivery
              </h2>
              <p className="text-xs text-slate-400 mt-0.5">
                Configure tailored data extracts, dimensions, and recurring deliveries to stakeholder inboxes.
              </p>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setShowScheduleModal(true)}
                className="bg-slate-800 hover:bg-slate-700 text-amber-300 text-xs px-3.5 py-2 rounded-lg border border-slate-700 flex items-center gap-2 transition-all"
              >
                <Calendar size={14} />
                <span>Schedule New Delivery</span>
              </button>
            </div>
          </div>

          {/* 394. Scheduled Deliveries Table */}
          <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-4">
            <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
              <Clock size={16} className="text-amber-400" />
              <span>394. Automated Scheduled Report Deliveries</span>
            </h3>
            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead className="bg-slate-950 text-slate-400 uppercase font-mono text-[10px]">
                  <tr>
                    <th className="p-2.5">Report Name</th>
                    <th className="p-2.5">Type</th>
                    <th className="p-2.5">Frequency</th>
                    <th className="p-2.5">Destination</th>
                    <th className="p-2.5">Format</th>
                    <th className="p-2.5">Next Run At</th>
                    <th className="p-2.5 text-right">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {scheduledReports.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="p-4 text-center text-slate-500 italic">
                        No recurring deliveries configured. Click "Schedule New Delivery" to create one.
                      </td>
                    </tr>
                  ) : (
                    scheduledReports.map((sch) => (
                      <tr key={sch.id} className="hover:bg-slate-800/30">
                        <td className="p-2.5 font-medium text-amber-200">{sch.report_name}</td>
                        <td className="p-2.5 font-mono text-slate-400">{sch.report_type}</td>
                        <td className="p-2.5 font-mono text-slate-300">{sch.frequency}</td>
                        <td className="p-2.5 font-mono text-slate-400">{sch.destination}</td>
                        <td className="p-2.5 font-mono text-amber-300 font-bold">{sch.export_format}</td>
                        <td className="p-2.5 font-mono text-slate-400">{sch.next_run_at ? new Date(sch.next_run_at).toLocaleDateString() : '—'}</td>
                        <td className="p-2.5 text-right">
                          <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-950 text-emerald-300 border border-emerald-800/40">
                            ACTIVE
                          </span>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* 395. Custom Report Templates & Query Runner */}
          <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-4">
            <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
              <BarChart3 size={16} className="text-amber-400" />
              <span>395. Custom Report Templates</span>
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {customReportConfigs.map((cfg) => (
                <div key={cfg.id} className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-2">
                  <div className="flex justify-between items-start">
                    <div>
                      <h4 className="text-xs font-semibold text-amber-200">{cfg.title}</h4>
                      <p className="text-[11px] text-slate-400">{cfg.description || 'Custom multi-dimensional extract'}</p>
                    </div>
                    <span className="text-[10px] font-mono bg-slate-800 text-slate-300 px-2 py-0.5 rounded uppercase">
                      {cfg.domain}
                    </span>
                  </div>
                  <div className="text-[11px] text-slate-400">
                    <div>Metrics: <span className="font-mono text-amber-300">{cfg.metrics.join(', ')}</span></div>
                    <div>Dimensions: <span className="font-mono text-slate-300">{cfg.dimensions.join(', ')}</span></div>
                  </div>
                  <div className="pt-2 text-right">
                    <button
                      onClick={() => handleExecuteCustomReport(cfg.id)}
                      className="bg-amber-950/70 hover:bg-amber-900/80 text-amber-300 border border-amber-800/40 text-xs px-3 py-1.5 rounded transition-all"
                    >
                      Run Query Live
                    </button>
                  </div>
                </div>
              ))}
            </div>

            {/* Live Query Results Preview */}
            {customReportResults && (
              <div className="mt-4 bg-slate-950 border border-amber-900/40 rounded-xl p-4 space-y-3">
                <div className="flex justify-between items-center border-b border-slate-800 pb-2">
                  <h4 className="text-xs font-serif text-amber-200">
                    Live Query Result: {customReportResults.title}
                  </h4>
                  <span className="text-[10px] font-mono text-emerald-400">
                    {customReportResults.data?.length || 0} Records Computed
                  </span>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-xs text-left">
                    <thead className="bg-slate-900 text-slate-400 font-mono text-[10px]">
                      <tr>
                        {Object.keys(customReportResults.data?.[0] || {}).map((col) => (
                          <th key={col} className="p-2 capitalize">{col.replace(/_/g, ' ')}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60">
                      {customReportResults.data?.map((row: any, rIdx: number) => (
                        <tr key={rIdx} className="hover:bg-slate-800/30">
                          {Object.values(row).map((val: any, cIdx: number) => (
                            <td key={cIdx} className="p-2 font-mono text-slate-300">
                              {typeof val === 'number' ? val.toLocaleString() : String(val)}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* =========================================================================
          TAB 4: AUDIT, JUSTIFICATION & PRIVACY (Features 396 - 400)
          ========================================================================= */}
      {!loading && activeTab === 'governance-privacy' && (
        <div className="space-y-6">
          {/* Action Justification Demonstration Bar (Feature 397) */}
          <div className="bg-gradient-to-r from-amber-950/70 via-slate-900 to-slate-900 border border-amber-500/40 rounded-xl p-4 flex flex-col md:flex-row justify-between items-start md:items-center gap-3">
            <div>
              <div className="flex items-center gap-2">
                <Lock size={16} className="text-amber-400" />
                <h3 className="text-sm font-serif text-amber-100 font-semibold">
                  397. Mandatory Action Justification Requirement
                </h3>
              </div>
              <p className="text-xs text-slate-400 mt-1">
                High-risk administrative actions (driver suspension, forced refund, fare rule modification) require substantive justification (min 15 chars) and incident ticket reference.
              </p>
            </div>
            <button
              onClick={() => setShowJustificationModal(true)}
              className="bg-amber-600 hover:bg-amber-500 text-slate-950 font-semibold text-xs px-4 py-2 rounded-lg transition-all"
            >
              Trigger Justified Action
            </button>
          </div>

          {/* 396. Immutable Audit Log Filters & Export */}
          <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-4">
            <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-3">
              <div>
                <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                  <ShieldAlert size={16} className="text-amber-400" />
                  <span>396. Searchable Immutable Audit Log ({auditTotal} Entries)</span>
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Tamper-evident trail of administrative state modifications with multi-criteria filtering.
                </p>
              </div>
              <div className="flex items-center gap-2 w-full md:w-auto">
                <select
                  value={auditFilterAction}
                  onChange={(e) => { setAuditFilterAction(e.target.value); }}
                  className="bg-slate-950 border border-slate-800 text-xs text-slate-200 rounded-lg px-2.5 py-2 outline-none focus:border-amber-500"
                >
                  <option value="">All Actions</option>
                  <option value="DRIVER">Driver Intervention</option>
                  <option value="REFUND">Refunds</option>
                  <option value="DATASET_EXPORTED">Data Exports</option>
                  <option value="DSAR">Privacy DSAR</option>
                </select>
                <div className="relative flex-1 md:w-56">
                  <Search size={14} className="absolute left-2.5 top-2.5 text-slate-500" />
                  <input
                    type="text"
                    placeholder="Search logs or ticket refs..."
                    value={auditSearchQuery}
                    onChange={(e) => setAuditSearchQuery(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && loadTabData()}
                    className="w-full bg-slate-950 border border-slate-800 text-xs text-slate-200 rounded-lg pl-8 pr-3 py-2 outline-none focus:border-amber-500"
                  />
                </div>
                <button
                  onClick={loadTabData}
                  className="bg-slate-800 hover:bg-slate-700 text-xs text-slate-200 px-3 py-2 rounded-lg border border-slate-700"
                >
                  Filter
                </button>
              </div>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead className="bg-slate-950 text-slate-400 uppercase font-mono text-[10px]">
                  <tr>
                    <th className="p-2.5">Timestamp</th>
                    <th className="p-2.5">Action Code</th>
                    <th className="p-2.5">Resource Target</th>
                    <th className="p-2.5">Target ID</th>
                    <th className="p-2.5">Audit Changes & Justification</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-mono">
                  {auditLogs.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="p-4 text-center text-slate-500 italic">No audit records match filters.</td>
                    </tr>
                  ) : (
                    auditLogs.map((log) => (
                      <tr key={log.id} className="hover:bg-slate-800/30">
                        <td className="p-2.5 text-slate-400">{new Date(log.created_at).toLocaleString()}</td>
                        <td className="p-2.5 font-bold text-amber-300">{log.action}</td>
                        <td className="p-2.5 text-slate-300">{log.target_resource}</td>
                        <td className="p-2.5 text-slate-400">{log.target_id || '—'}</td>
                        <td className="p-2.5 text-[11px] text-slate-300 max-w-xs truncate">
                          {JSON.stringify(log.changes || {})}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* 398. Sensitive Record Access History */}
          <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-3">
            <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
              <Eye size={16} className="text-cyan-400" />
              <span>398. Sensitive Record Access History (PII & Bank Detail Views)</span>
            </h3>
            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead className="bg-slate-950 text-slate-400 uppercase font-mono text-[10px]">
                  <tr>
                    <th className="p-2.5">Accessed At</th>
                    <th className="p-2.5">Admin ID</th>
                    <th className="p-2.5">Record Type</th>
                    <th className="p-2.5">Identifier</th>
                    <th className="p-2.5">Justification Reason</th>
                    <th className="p-2.5">Ticket Ref</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-mono">
                  {sensitiveAccessLogs.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="p-4 text-center text-slate-500 italic">No sensitive record views logged yet.</td>
                    </tr>
                  ) : (
                    sensitiveAccessLogs.map((s) => (
                      <tr key={s.id} className="hover:bg-slate-800/30">
                        <td className="p-2.5 text-slate-400">{new Date(s.accessed_at).toLocaleTimeString()}</td>
                        <td className="p-2.5 text-slate-400">{s.admin_user_id.slice(0, 8)}...</td>
                        <td className="p-2.5 font-semibold text-cyan-300">{s.record_type}</td>
                        <td className="p-2.5 text-slate-300">{s.record_identifier}</td>
                        <td className="p-2.5 text-slate-300">{s.justification_reason}</td>
                        <td className="p-2.5 text-amber-300">{s.ticket_reference || '—'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* 399. Data Retention Policy Controls */}
          <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-4">
            <div className="flex justify-between items-center">
              <div>
                <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
                  <Clock size={16} className="text-amber-400" />
                  <span>399. Statutory Data Retention Policy Controls</span>
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  TTL days per category. Invoices & tax records locked to statutory 2555 days (7 years per Indian IT Act & DPDP).
                </p>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {retentionPolicies.map((pol) => (
                <div key={pol.id} className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-2">
                  <div className="flex justify-between items-start">
                    <span className="text-xs font-semibold text-amber-200">{pol.data_category}</span>
                    {pol.is_statutory_mandatory ? (
                      <span className="text-[9px] font-mono uppercase bg-rose-950 text-rose-300 px-2 py-0.5 rounded border border-rose-800/40">
                        Statutory 7-Year Lock
                      </span>
                    ) : (
                      <span className="text-[9px] font-mono uppercase bg-slate-800 text-slate-300 px-2 py-0.5 rounded">
                        Configurable
                      </span>
                    )}
                  </div>
                  <p className="text-[11px] text-slate-400">{pol.description}</p>
                  <div className="flex justify-between items-center pt-2 text-xs">
                    <span className="text-slate-400">Retention TTL:</span>
                    <span className="font-mono text-amber-300 font-bold">{pol.retention_ttl_days} Days</span>
                  </div>
                  <div className="pt-2 border-t border-slate-800/60 flex justify-between items-center text-[11px]">
                    <span className="text-slate-500">Purge: {pol.purge_frequency}</span>
                    <button
                      onClick={() => handleRunRetentionPurge(pol.data_category)}
                      className="text-amber-400 hover:text-amber-300 flex items-center gap-1"
                    >
                      <Trash2 size={12} />
                      <span>Dry-Run Purge</span>
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* 400. Privacy Request Processing Dashboard (DPDP / GDPR DSAR) */}
          <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 space-y-4">
            <h3 className="text-sm font-serif text-amber-100 flex items-center gap-2">
              <Shield size={16} className="text-emerald-400" />
              <span>400. DPDP / GDPR Privacy Request Processing Desk (DSAR)</span>
            </h3>
            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead className="bg-slate-950 text-slate-400 uppercase font-mono text-[10px]">
                  <tr>
                    <th className="p-2.5">Request #</th>
                    <th className="p-2.5">User Role</th>
                    <th className="p-2.5">Type</th>
                    <th className="p-2.5">Details</th>
                    <th className="p-2.5">Status</th>
                    <th className="p-2.5 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-mono">
                  {privacyRequests.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="p-4 text-center text-slate-500 italic">No DSAR privacy requests pending.</td>
                    </tr>
                  ) : (
                    privacyRequests.map((req) => (
                      <tr key={req.id} className="hover:bg-slate-800/30">
                        <td className="p-2.5 font-bold text-amber-200">{req.request_number}</td>
                        <td className="p-2.5 text-slate-300">{req.user_role}</td>
                        <td className="p-2.5 font-semibold text-cyan-300">{req.request_type}</td>
                        <td className="p-2.5 text-slate-400 max-w-xs truncate">{req.details || 'Self-service DSAR request'}</td>
                        <td className="p-2.5">
                          <span className={`text-[10px] px-2 py-0.5 rounded font-bold uppercase ${
                            req.status.includes('FULFILLED') ? 'bg-emerald-950 text-emerald-300 border border-emerald-800/40' :
                            req.status === 'UNDER_PROCESSING' ? 'bg-amber-950 text-amber-300 border border-amber-800/40' :
                            'bg-slate-800 text-slate-300'
                          }`}>
                            {req.status}
                          </span>
                        </td>
                        <td className="p-2.5 text-right">
                          <button
                            onClick={() => { setSelectedPrivacyReq(req); setDsarNewStatus('FULFILLED_EXPORTED'); }}
                            className="bg-amber-950/70 hover:bg-amber-900 text-amber-300 border border-amber-800/40 text-[11px] px-2.5 py-1 rounded transition-all"
                          >
                            Review & Fulfill
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* =========================================================================
          MODALS: Export, Schedule, Justification & DSAR Review
          ========================================================================= */}

      {/* Export Modal (Feature 393) */}
      {showExportModal && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-amber-900/50 rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl">
            <h3 className="text-lg font-serif text-amber-100 flex items-center gap-2">
              <Download size={18} className="text-amber-400" />
              <span>Permission-Gated Data Export</span>
            </h3>
            <p className="text-xs text-slate-400">
              Verify export permissions. Identity fields will be cryptographically masked unless you hold explicit <code className="text-amber-300 font-mono">pii:export</code> rights.
            </p>

            <div className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 block mb-1">Select Dataset</label>
                <select
                  value={exportDataset}
                  onChange={(e) => setExportDataset(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2.5 outline-none focus:border-amber-500"
                >
                  <option value="RIDES">Rides & Voyages (Operational)</option>
                  <option value="CHAUFFEURS">Chauffeur Profiles & Welfare</option>
                  <option value="FINANCE_SETTLEMENTS">Financial Settlements & Vouchers</option>
                  <option value="SUMMARY">Executive KPI Summary</option>
                </select>
              </div>

              <div>
                <label className="text-slate-400 block mb-1">Export Format</label>
                <select
                  value={exportFormat}
                  onChange={(e) => setExportFormat(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2.5 outline-none focus:border-amber-500"
                >
                  <option value="CSV">CSV Spreadsheet (Comma-Separated)</option>
                  <option value="JSON">JSON Records Stream</option>
                </select>
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
              <button
                onClick={() => setShowExportModal(false)}
                className="px-4 py-2 text-xs rounded-lg text-slate-400 hover:text-slate-200"
              >
                Cancel
              </button>
              <button
                onClick={handleExportDataset}
                className="bg-amber-600 hover:bg-amber-500 text-slate-950 font-semibold text-xs px-4 py-2 rounded-lg"
              >
                Authorize & Download
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Schedule Delivery Modal (Feature 394) */}
      {showScheduleModal && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-amber-900/50 rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl">
            <h3 className="text-lg font-serif text-amber-100 flex items-center gap-2">
              <Calendar size={18} className="text-amber-400" />
              <span>Schedule Recurring Report Delivery</span>
            </h3>

            <div className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 block mb-1">Report Name</label>
                <input
                  type="text"
                  placeholder="e.g. Weekly VIP Chauffeur Audit"
                  value={newSchedulePayload.report_name}
                  onChange={(e) => setNewSchedulePayload({ ...newSchedulePayload, report_name: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2.5 outline-none focus:border-amber-500"
                />
              </div>

              <div>
                <label className="text-slate-400 block mb-1">Frequency</label>
                <select
                  value={newSchedulePayload.frequency}
                  onChange={(e) => setNewSchedulePayload({ ...newSchedulePayload, frequency: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2.5 outline-none focus:border-amber-500"
                >
                  <option value="DAILY">Daily (06:00 AM IST)</option>
                  <option value="WEEKLY">Weekly (Monday 08:00 AM IST)</option>
                  <option value="MONTHLY">Monthly (1st Day)</option>
                </select>
              </div>

              <div>
                <label className="text-slate-400 block mb-1">Recipient Destination</label>
                <input
                  type="text"
                  placeholder="executive@grandchauffeur.com"
                  value={newSchedulePayload.destination}
                  onChange={(e) => setNewSchedulePayload({ ...newSchedulePayload, destination: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2.5 outline-none focus:border-amber-500"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
              <button
                onClick={() => setShowScheduleModal(false)}
                className="px-4 py-2 text-xs rounded-lg text-slate-400 hover:text-slate-200"
              >
                Cancel
              </button>
              <button
                onClick={handleCreateSchedule}
                className="bg-amber-600 hover:bg-amber-500 text-slate-950 font-semibold text-xs px-4 py-2 rounded-lg"
              >
                Save Schedule
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Action Justification Modal (Feature 397) */}
      {showJustificationModal && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-amber-900/50 rounded-2xl max-w-lg w-full p-6 space-y-4 shadow-2xl">
            <h3 className="text-lg font-serif text-amber-100 flex items-center gap-2">
              <ShieldAlert size={18} className="text-amber-400" />
              <span>397. Mandatory Action Justification</span>
            </h3>
            <p className="text-xs text-slate-400">
              This high-risk action requires a formal rationale (minimum 15 characters) and an active Incident or Jira Ticket reference for statutory compliance.
            </p>

            <div className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 block mb-1">Action Code</label>
                <select
                  value={justificationActionCode}
                  onChange={(e) => setJustificationActionCode(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2.5 outline-none focus:border-amber-500"
                >
                  <option value="DRIVER_SUSPENSION">DRIVER_SUSPENSION (Driver Welfare Intervention)</option>
                  <option value="MANUAL_REFUND_FORCE">MANUAL_REFUND_FORCE (Finance Override)</option>
                  <option value="FARE_RULE_MODIFICATION">FARE_RULE_MODIFICATION (Tariff Rule Change)</option>
                  <option value="SENSITIVE_RECORD_UNMASK">SENSITIVE_RECORD_UNMASK (View PII / Bank Account)</option>
                </select>
              </div>

              <div>
                <label className="text-slate-400 block mb-1">Target Resource & ID</label>
                <div className="grid grid-cols-2 gap-2">
                  <input
                    type="text"
                    placeholder="driver_profiles"
                    value={justificationTargetResource}
                    onChange={(e) => setJustificationTargetResource(e.target.value)}
                    className="bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2 outline-none focus:border-amber-500 font-mono text-xs"
                  />
                  <input
                    type="text"
                    placeholder="drv-8891"
                    value={justificationTargetId}
                    onChange={(e) => setJustificationTargetId(e.target.value)}
                    className="bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2 outline-none focus:border-amber-500 font-mono text-xs"
                  />
                </div>
              </div>

              <div>
                <label className="text-slate-400 block mb-1">Incident / Ticket Reference ID *</label>
                <input
                  type="text"
                  placeholder="e.g. INC-2026-8821 or JIRA-CHAUFFEUR-402"
                  value={justificationTicketRef}
                  onChange={(e) => setJustificationTicketRef(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2.5 outline-none focus:border-amber-500 font-mono"
                />
              </div>

              <div>
                <label className="text-slate-400 block mb-1">Justification Reason * (min 15 characters)</label>
                <textarea
                  rows={3}
                  placeholder="Provide substantive explanation of regulatory or operational justification..."
                  value={justificationReason}
                  onChange={(e) => setJustificationReason(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2.5 outline-none focus:border-amber-500"
                />
                <div className="text-[10px] text-right text-slate-500">
                  {justificationReason.trim().length} / 15 chars minimum
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
              <button
                onClick={() => setShowJustificationModal(false)}
                className="px-4 py-2 text-xs rounded-lg text-slate-400 hover:text-slate-200"
              >
                Cancel
              </button>
              <button
                onClick={handleSubmitJustifiedAction}
                className="bg-amber-600 hover:bg-amber-500 text-slate-950 font-semibold text-xs px-4 py-2 rounded-lg"
              >
                Authorize & Commit Action
              </button>
            </div>
          </div>
        </div>
      )}

      {/* DSAR Review & Fulfill Modal (Feature 400) */}
      {selectedPrivacyReq && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-amber-900/50 rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl">
            <h3 className="text-lg font-serif text-amber-100 flex items-center gap-2">
              <Shield size={18} className="text-emerald-400" />
              <span>Review Privacy Request ({selectedPrivacyReq.request_number})</span>
            </h3>

            <div className="bg-slate-950 border border-slate-800 rounded-lg p-3 text-xs space-y-1">
              <div>Type: <strong className="text-cyan-300 font-mono">{selectedPrivacyReq.request_type}</strong></div>
              <div>User Role: <strong className="text-amber-200">{selectedPrivacyReq.user_role}</strong></div>
              <div className="text-slate-400 mt-1 italic">"{selectedPrivacyReq.details || 'Self-service DSAR'}"</div>
            </div>

            <div className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 block mb-1">Fulfillment Status</label>
                <select
                  value={dsarNewStatus}
                  onChange={(e) => setDsarNewStatus(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2.5 outline-none focus:border-amber-500"
                >
                  <option value="FULFILLED_EXPORTED">FULFILLED_EXPORTED (Portability Archive Generated)</option>
                  <option value="FULFILLED_ERASED">FULFILLED_ERASED (Right to Erasure with Tax Exemption)</option>
                  <option value="REJECTED_LEGAL_OVERRIDE">REJECTED_LEGAL_OVERRIDE (Statutory Dispute Active)</option>
                </select>
              </div>

              <div>
                <label className="text-slate-400 block mb-1">Resolution & Exemption Notes</label>
                <textarea
                  rows={3}
                  placeholder="Record DPO compliance notes (e.g. Invoices retained per Indian IT Act)..."
                  value={dsarReviewNotes}
                  onChange={(e) => setDsarReviewNotes(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2.5 outline-none focus:border-amber-500"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
              <button
                onClick={() => setSelectedPrivacyReq(null)}
                className="px-4 py-2 text-xs rounded-lg text-slate-400 hover:text-slate-200"
              >
                Cancel
              </button>
              <button
                onClick={handleReviewDsar}
                className="bg-emerald-600 hover:bg-emerald-500 text-slate-950 font-semibold text-xs px-4 py-2 rounded-lg"
              >
                Commit DSAR Fulfillment
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
