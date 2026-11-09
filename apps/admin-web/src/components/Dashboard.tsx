import React, { useEffect, useState } from 'react';
import { Activity, Car, RefreshCw } from 'lucide-react';
import { fetchWithAuth } from '../services/api';

interface DashboardStats {
  total_rides: number;
  active_rides: number;
  online_drivers: number;
  gross_booking_cents: number;
  platform_status: string;
}

interface LiveDispatchItem {
  id: string;
  guest_name?: string;
  customer_id?: string;
  driver_name?: string;
  assigned_driver_id?: string;
  vehicle_category?: string;
  vehicle_model_override?: string;
  pickup_address?: string;
  destination_address?: string;
  status: string;
  final_fare_cents?: number;
  estimated_fare_cents?: number;
}

export const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<DashboardStats>({
    total_rides: 0,
    active_rides: 0,
    online_drivers: 0,
    gross_booking_cents: 0,
    platform_status: "OPERATIONAL"
  });
  const [recentRides, setRecentRides] = useState<LiveDispatchItem[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const loadDashboardData = async () => {
    setIsLoading(true);
    try {
      const [statsData, ridesData] = await Promise.all([
        fetchWithAuth<DashboardStats>('/admin/dashboard/stats'),
        fetchWithAuth<{ rides?: LiveDispatchItem[] }>('/admin/rides?limit=10')
      ]);

      if (statsData) {
        setStats(statsData);
      }
      if (ridesData && Array.isArray(ridesData.rides)) {
        setRecentRides(ridesData.rides);
      }
    } catch (err) {
      console.warn('Dashboard data fetch notification:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadDashboardData();
    const interval = setInterval(loadDashboardData, 30000);
    return () => clearInterval(interval);
  }, []);

  const formatRupees = (cents: number) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0
    }).format(cents / 100);
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'IN_PROGRESS':
      case 'STARTED':
        return <span className="badge badge-amber">{status}</span>;
      case 'COMPLETED':
        return <span className="badge badge-emerald">{status}</span>;
      case 'ASSIGNED':
      case 'ARRIVED':
        return <span className="badge badge-gold">{status}</span>;
      case 'CANCELLED':
        return <span className="badge" style={{ background: 'rgba(239, 68, 68, 0.2)', color: '#f87171' }}>{status}</span>;
      default:
        return <span className="badge">{status}</span>;
    }
  };

  return (
    <div>
      <div className="page-header flex justify-between items-center mb-6">
        <div>
          <h1 className="page-title text-xl font-bold text-white">Vaisentra Mobility Operations Control</h1>
          <p className="page-description text-xs text-slate-400">
            Real-time urban transit telemetry, active ride dispatches, and fleet indicators.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={loadDashboardData}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-900 hover:bg-slate-800 border border-slate-700 rounded-lg text-xs font-semibold text-slate-300 transition-all"
            title="Refresh Data"
          >
            <RefreshCw size={13} className={isLoading ? "animate-spin text-amber-400" : ""} />
            <span>Refresh</span>
          </button>
          <div className="badge badge-emerald flex items-center gap-1.5">
            <Activity size={14} />
            <span>Platform: {stats.platform_status}</span>
          </div>
        </div>
      </div>

      <div className="stat-grid grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
        <div className="stat-card p-4 rounded-xl bg-slate-900/60 border border-slate-800">
          <div className="stat-label text-xs font-semibold text-slate-400">Gross Booking Value</div>
          <div className="stat-value gold text-2xl font-black text-amber-400 mt-1">
            {formatRupees(stats.gross_booking_cents)}
          </div>
          <div className="text-[11px] text-emerald-400 mt-1 flex items-center gap-1">
            <span>●</span> Live Verified Revenue
          </div>
        </div>

        <div className="stat-card p-4 rounded-xl bg-slate-900/60 border border-slate-800">
          <div className="stat-label text-xs font-semibold text-slate-400">Active Dispatches</div>
          <div className="stat-value text-2xl font-black text-white mt-1">
            {stats.active_rides}
          </div>
          <div className="text-[11px] text-slate-400 mt-1">
            En route, arriving & in progress
          </div>
        </div>

        <div className="stat-card p-4 rounded-xl bg-slate-900/60 border border-slate-800">
          <div className="stat-label text-xs font-semibold text-slate-400">Pilots Online</div>
          <div className="stat-value text-2xl font-black text-emerald-400 mt-1">
            {stats.online_drivers}
          </div>
          <div className="text-[11px] text-emerald-400 mt-1 flex items-center gap-1">
            <span>●</span> Active H3 Hexagonal Grid
          </div>
        </div>

        <div className="stat-card p-4 rounded-xl bg-slate-900/60 border border-slate-800">
          <div className="stat-label text-xs font-semibold text-slate-400">Total Completed Trips</div>
          <div className="stat-value text-2xl font-black text-white mt-1">
            {stats.total_rides}
          </div>
          <div className="text-[11px] text-slate-400 mt-1">
            Zero-emission transit rides
          </div>
        </div>
      </div>

      <div className="table-card rounded-2xl bg-slate-900/80 border border-slate-800 p-5">
        <div className="table-toolbar flex justify-between items-center mb-4">
          <div className="flex items-center gap-2">
            <Car size={18} className="text-amber-400" />
            <h2 className="text-base font-bold text-white">Live Dispatch Feed</h2>
          </div>
          <span className="badge badge-gold text-[10px] uppercase tracking-wider font-bold">
            Authoritative Stream
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="luxury-table w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400">
                <th className="py-2.5 px-3">Ride ID</th>
                <th className="py-2.5 px-3">Commuter</th>
                <th className="py-2.5 px-3">Pilot</th>
                <th className="py-2.5 px-3">Category</th>
                <th className="py-2.5 px-3">Route (Origin → Destination)</th>
                <th className="py-2.5 px-3">Status</th>
                <th className="py-2.5 px-3 text-right">Fare</th>
              </tr>
            </thead>
            <tbody>
              {recentRides.length > 0 ? (
                recentRides.map((ride) => (
                  <tr key={ride.id} className="border-b border-slate-800/50 hover:bg-slate-800/40 transition-colors">
                    <td className="py-2.5 px-3 font-mono text-slate-300">
                      #{ride.id.substring(0, 8)}
                    </td>
                    <td className="py-2.5 px-3 text-white font-semibold">
                      {ride.guest_name || (ride.customer_id ? `Customer (${ride.customer_id.substring(0, 6)})` : 'Commuter')}
                    </td>
                    <td className="py-2.5 px-3 text-slate-300">
                      {ride.driver_name || (ride.assigned_driver_id ? `Pilot (${ride.assigned_driver_id.substring(0, 6)})` : 'Unassigned')}
                    </td>
                    <td className="py-2.5 px-3 text-slate-400">
                      {ride.vehicle_model_override || ride.vehicle_category || 'Standard E-Rickshaw'}
                    </td>
                    <td className="py-2.5 px-3 text-slate-300 max-w-[280px] truncate" title={`${ride.pickup_address || 'Origin'} → ${ride.destination_address || 'Destination'}`}>
                      {ride.pickup_address || 'Pickup'} → {ride.destination_address || 'Destination'}
                    </td>
                    <td className="py-2.5 px-3">
                      {getStatusBadge(ride.status)}
                    </td>
                    <td className="py-2.5 px-3 text-right font-bold text-amber-400">
                      {formatRupees(ride.final_fare_cents || ride.estimated_fare_cents || 0)}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="text-center py-8 text-slate-400">
                    <div className="flex flex-col items-center justify-center gap-2">
                      <span className="text-2xl">⚡</span>
                      <p className="text-xs font-semibold text-slate-300">No active ride dispatches in queue.</p>
                      <p className="text-[11px] text-slate-500">Dispatch engine is standing by for new commuter requests.</p>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
