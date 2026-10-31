import React, { useEffect, useState } from 'react';
import { Activity } from 'lucide-react';
import { fetchWithAuth } from '../services/api';

export const Dashboard: React.FC = () => {
  const [stats, setStats] = useState({
    total_rides: 142,
    active_rides: 6,
    online_drivers: 18,
    gross_booking_cents: 28400000,
    platform_status: "OPERATIONAL"
  });

  useEffect(() => {
    fetchWithAuth('/admin/dashboard/stats').then(data => {
      if (data) setStats(data);
    });
  }, []);

  const formatRupees = (cents: number) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0
    }).format(cents / 100);
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Executive Operations Control</h1>
          <p className="page-description">Real-time luxury fleet telemetry and dispatch performance indicators.</p>
        </div>
        <div className="badge badge-emerald">
          <Activity size={14} style={{ marginRight: 6 }} /> System Nominal (100% SLA)
        </div>
      </div>

      <div className="stat-grid">
        <div className="stat-card">
          <div className="stat-label">Gross Booking Value</div>
          <div className="stat-value gold">{formatRupees(stats.gross_booking_cents)}</div>
          <div style={{ fontSize: '0.78rem', color: 'var(--status-emerald)' }}>
            +18.4% vs previous cycle
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-label">Active Rides</div>
          <div className="stat-value">{stats.active_rides}</div>
          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
            En route & in progress
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-label">Chauffeurs Online</div>
          <div className="stat-value">{stats.online_drivers}</div>
          <div style={{ fontSize: '0.78rem', color: 'var(--status-emerald)' }}>
            Ready for instant dispatch
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-label">Total Completed Journeys</div>
          <div className="stat-value">{stats.total_rides}</div>
          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
            Average Guest Rating: ★ 4.97
          </div>
        </div>
      </div>

      <div className="table-card">
        <div className="table-toolbar">
          <h2 style={{ fontSize: '1.05rem', fontWeight: 600 }}>Real-Time Dispatch Feed</h2>
          <span className="badge badge-gold">Live Telemetry</span>
        </div>
        <table className="luxury-table">
          <thead>
            <tr>
              <th>Ride Ref</th>
              <th>Guest Name</th>
              <th>Chauffeur</th>
              <th>Vehicle Tier</th>
              <th>Route</th>
              <th>Status</th>
              <th>Contract Value</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td style={{ fontFamily: 'var(--font-mono)' }}>#LX-9941</td>
              <td>Lord Sterling</td>
              <td>Rajeshwar Varma</td>
              <td>Executive Sedan</td>
              <td>Colaba → CSM T2 Terminal</td>
              <td><span className="badge badge-amber">IN PROGRESS</span></td>
              <td style={{ fontWeight: 600 }}>₹1,600.00</td>
            </tr>
            <tr>
              <td style={{ fontFamily: 'var(--font-mono)' }}>#LX-9940</td>
              <td>Lady Evelyn Vance</td>
              <td>Vikramaditya Rao</td>
              <td>Royal Limousine</td>
              <td>Bandra Kurla → Oberoi Pavilion</td>
              <td><span className="badge badge-emerald">COMPLETED</span></td>
              <td style={{ fontWeight: 600 }}>₹4,500.00</td>
            </tr>
            <tr>
              <td style={{ fontFamily: 'var(--font-mono)' }}>#LX-9939</td>
              <td>Ambassador Chen</td>
              <td>Siddharth Sen</td>
              <td>Prestige SUV</td>
              <td>Worli Sea Face → St. Regis</td>
              <td><span className="badge badge-emerald">COMPLETED</span></td>
              <td style={{ fontWeight: 600 }}>₹2,400.00</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
};
