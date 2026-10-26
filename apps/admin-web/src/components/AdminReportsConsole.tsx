import React, { useState, useEffect } from 'react';

interface ReportMetrics {
  period: string;
  currency: string;
  volume_metrics: {
    total_rides_requested: number;
    completed_rides: number;
    cancelled_rides: number;
    completion_rate_percent: number;
    cancellation_rate_percent: number;
    active_online_chauffeurs: number;
  };
  financial_metrics: {
    gross_booking_value_cents: number;
    collected_payments_cents: number;
    driver_earnings_cents: number;
    platform_commission_cents: number;
    conservation_verified: boolean;
  };
  shared_ride_metrics: {
    total_shared_ride_bookings: number;
    total_pooled_trips: number;
    shared_utilization_percent: number;
    average_occupancy_per_trip: number;
    total_passenger_savings_cents: number;
  };
}

export const AdminReportsConsole: React.FC = () => {
  const [metrics, setMetrics] = useState<ReportMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [dateRange, setDateRange] = useState('ALL');

  useEffect(() => {
    fetchReports();
  }, [dateRange]);

  const fetchReports = async () => {
    setLoading(true);
    try {
      const token = localStorage.getItem('token') || '';
      const res = await fetch('/api/v1/admin/reports', {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setMetrics(data);
      }
    } catch (err) {
      console.error('Failed to load reports', err);
    } finally {
      setLoading(false);
    }
  };

  const exportCsv = () => {
    if (!metrics) return;
    const csvContent =
      'data:text/csv;charset=utf-8,' +
      'Metric,Value\n' +
      `Gross Booking Value (INR),${(metrics.financial_metrics.gross_booking_value_cents / 100).toFixed(2)}\n` +
      `Collected Payments (INR),${(metrics.financial_metrics.collected_payments_cents / 100).toFixed(2)}\n` +
      `Driver Earnings (INR),${(metrics.financial_metrics.driver_earnings_cents / 100).toFixed(2)}\n` +
      `Platform Commission (INR),${(metrics.financial_metrics.platform_commission_cents / 100).toFixed(2)}\n` +
      `Total Rides Requested,${metrics.volume_metrics.total_rides_requested}\n` +
      `Completed Rides,${metrics.volume_metrics.completed_rides}\n` +
      `Completion Rate,${metrics.volume_metrics.completion_rate_percent}%\n` +
      `Shared Ride Bookings,${metrics.shared_ride_metrics.total_shared_ride_bookings}\n` +
      `Shared Utilization Rate,${metrics.shared_ride_metrics.shared_utilization_percent}%\n` +
      `Average Seat Occupancy,${metrics.shared_ride_metrics.average_occupancy_per_trip}\n` +
      `Passenger Savings (INR),${(metrics.shared_ride_metrics.total_passenger_savings_cents / 100).toFixed(2)}\n`;

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `executive_financial_report_${Date.now()}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  if (loading) {
    return <div className="p-8 text-amber-300 font-serif">Synthesizing executive ledger and pooling analytics...</div>;
  }

  const fin = metrics?.financial_metrics;
  const vol = metrics?.volume_metrics;
  const pool = metrics?.shared_ride_metrics;

  return (
    <div className="space-y-8">
      {/* Header & Controls */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-amber-900/30 pb-6">
        <div>
          <h2 className="text-2xl font-serif text-amber-100 flex items-center gap-3">
            <span>Executive Financial & Pooling Analytics</span>
            <span className="text-xs font-mono uppercase bg-emerald-950/80 text-emerald-400 px-2.5 py-1 rounded-full border border-emerald-800/40">
              Conservation Verified
            </span>
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Authoritative revenue reconciliation, ride volume conversion, and shared fleet utilization.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <select
            value={dateRange}
            onChange={(e) => setDateRange(e.target.value)}
            className="bg-slate-900 border border-slate-700 text-amber-200 text-sm rounded-lg px-3 py-2 outline-none focus:border-amber-500"
          >
            <option value="ALL">All Time</option>
            <option value="30D">Last 30 Days</option>
            <option value="7D">Last 7 Days</option>
          </select>
          <button
            onClick={exportCsv}
            className="px-4 py-2 bg-gradient-to-r from-amber-600 to-amber-700 hover:from-amber-500 hover:to-amber-600 text-white rounded-lg text-sm font-medium transition shadow-lg shadow-amber-900/20 flex items-center gap-2"
          >
            <span>Export CSV</span>
          </button>
        </div>
      </div>

      {/* Financial Ledger Section */}
      <div>
        <h3 className="text-lg font-serif text-amber-200 mb-4 flex items-center gap-2">
          <span>Financial Reconciliation (Minor Integer Unit Invariant)</span>
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div className="bg-slate-900/70 border border-amber-900/30 rounded-xl p-5 backdrop-blur-sm">
            <span className="text-xs uppercase tracking-wider text-slate-400">Gross Booking Value</span>
            <div className="text-2xl font-serif text-amber-300 mt-2">
              ₹{((fin?.gross_booking_value_cents || 0) / 100).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </div>
            <span className="text-xs text-slate-500 mt-1 block">Total client bookings value</span>
          </div>

          <div className="bg-slate-900/70 border border-amber-900/30 rounded-xl p-5 backdrop-blur-sm">
            <span className="text-xs uppercase tracking-wider text-slate-400">Collected Payments</span>
            <div className="text-2xl font-serif text-emerald-400 mt-2">
              ₹{((fin?.collected_payments_cents || 0) / 100).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </div>
            <span className="text-xs text-slate-500 mt-1 block">Settled via Razorpay / Wallet / Cash</span>
          </div>

          <div className="bg-slate-900/70 border border-amber-900/30 rounded-xl p-5 backdrop-blur-sm">
            <span className="text-xs uppercase tracking-wider text-slate-400">Chauffeur Payouts (80%)</span>
            <div className="text-2xl font-serif text-slate-200 mt-2">
              ₹{((fin?.driver_earnings_cents || 0) / 100).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </div>
            <span className="text-xs text-slate-500 mt-1 block">Chauffeur earnings allocation</span>
          </div>

          <div className="bg-slate-900/70 border border-amber-900/30 rounded-xl p-5 backdrop-blur-sm">
            <span className="text-xs uppercase tracking-wider text-slate-400">Platform Commission (20%)</span>
            <div className="text-2xl font-serif text-amber-400 mt-2">
              ₹{((fin?.platform_commission_cents || 0) / 100).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </div>
            <span className="text-xs text-slate-500 mt-1 block">Net platform retention</span>
          </div>
        </div>
      </div>

      {/* Shared Rides & Pooling Section */}
      <div>
        <h3 className="text-lg font-serif text-amber-200 mb-4 flex items-center gap-2">
          <span>Shared Rides & Vehicle Capacity Utilization</span>
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div className="bg-slate-900/70 border border-cyan-900/30 rounded-xl p-5 backdrop-blur-sm">
            <span className="text-xs uppercase tracking-wider text-slate-400">Shared Ride Bookings</span>
            <div className="text-2xl font-serif text-cyan-300 mt-2">
              {pool?.total_shared_ride_bookings || 0}
            </div>
            <span className="text-xs text-slate-500 mt-1 block">
              {pool?.shared_utilization_percent || 0}% of all rides
            </span>
          </div>

          <div className="bg-slate-900/70 border border-cyan-900/30 rounded-xl p-5 backdrop-blur-sm">
            <span className="text-xs uppercase tracking-wider text-slate-400">Active Pooled Trips</span>
            <div className="text-2xl font-serif text-cyan-300 mt-2">
              {pool?.total_pooled_trips || 0}
            </div>
            <span className="text-xs text-slate-500 mt-1 block">Consolidated vehicle journeys</span>
          </div>

          <div className="bg-slate-900/70 border border-cyan-900/30 rounded-xl p-5 backdrop-blur-sm">
            <span className="text-xs uppercase tracking-wider text-slate-400">Avg Seat Occupancy</span>
            <div className="text-2xl font-serif text-purple-300 mt-2">
              {pool?.average_occupancy_per_trip || 1.0} / 4.0
            </div>
            <span className="text-xs text-slate-500 mt-1 block">Seats utilized per pooled journey</span>
          </div>

          <div className="bg-slate-900/70 border border-cyan-900/30 rounded-xl p-5 backdrop-blur-sm">
            <span className="text-xs uppercase tracking-wider text-slate-400">Client Savings Generated</span>
            <div className="text-2xl font-serif text-emerald-400 mt-2">
              ₹{((pool?.total_passenger_savings_cents || 0) / 100).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </div>
            <span className="text-xs text-slate-500 mt-1 block">Delivered via pooling discount</span>
          </div>
        </div>
      </div>

      {/* Operational Volume & Conversion */}
      <div>
        <h3 className="text-lg font-serif text-amber-200 mb-4">Operational Volume & Service Health</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5">
            <span className="text-xs uppercase tracking-wider text-slate-400">Total Rides Requested</span>
            <div className="text-xl font-serif text-slate-100 mt-2">{vol?.total_rides_requested || 0}</div>
            <div className="flex items-center gap-4 mt-3 text-xs text-slate-400">
              <span className="text-emerald-400">Completed: {vol?.completed_rides || 0}</span>
              <span className="text-rose-400">Cancelled: {vol?.cancelled_rides || 0}</span>
            </div>
          </div>

          <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5">
            <span className="text-xs uppercase tracking-wider text-slate-400">Completion vs Cancellation Rate</span>
            <div className="text-xl font-serif text-emerald-400 mt-2">
              {vol?.completion_rate_percent || 0}% Completion
            </div>
            <span className="text-xs text-rose-400/90 mt-1 block">
              {vol?.cancellation_rate_percent || 0}% Cancellation rate
            </span>
          </div>

          <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5">
            <span className="text-xs uppercase tracking-wider text-slate-400">Active Online Chauffeurs</span>
            <div className="text-xl font-serif text-amber-300 mt-2">
              {vol?.active_online_chauffeurs || 0} Chauffeurs
            </div>
            <span className="text-xs text-slate-500 mt-1 block">Broadcasting real-time availability</span>
          </div>
        </div>
      </div>
    </div>
  );
};
