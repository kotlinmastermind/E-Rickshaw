import React, { useState } from 'react';


interface RideTimelineEvent {
  time: string;
  actor: string;
  event: string;
  notes: string;
}

interface AdminRide {
  id: string;
  guest: string;
  driver: string;
  vehicle: string;
  pickup: string;
  destination: string;
  status: string;
  fare: string;
  timeline: RideTimelineEvent[];
}

export const RideManagement: React.FC = () => {
  const [rides] = useState<AdminRide[]>([
    {
      id: "ride_lux_9941",
      guest: "Lord Sterling",
      driver: "Rajeshwar Varma",
      vehicle: "Mercedes-Benz E-Class",
      pickup: "The Taj Mahal Palace, Colaba",
      destination: "CSM T2 Private Terminal",
      status: "IN_PROGRESS",
      fare: "₹1,600.00",
      timeline: [
        { time: "14:02:10 UTC", actor: "CUSTOMER", event: "REQUESTED", notes: "Ride requested via Android Guest App" },
        { time: "14:02:18 UTC", actor: "DRIVER", event: "ASSIGNED", notes: "Chauffeur accepted atomic dispatch offer" },
        { time: "14:06:45 UTC", actor: "DRIVER", event: "ARRIVED", notes: "Vehicle arrived at hotel portico" },
        { time: "14:08:12 UTC", actor: "DRIVER", event: "IN_PROGRESS", notes: "Passcode 849201 verified successfully" }
      ]
    },
    {
      id: "ride_lux_9940",
      guest: "Lady Evelyn Vance",
      driver: "Vikramaditya Rao",
      vehicle: "Mercedes-Maybach S680",
      pickup: "Bandra Kurla Financial Enclave",
      destination: "The Oberoi Grand Pavilion",
      status: "COMPLETED",
      fare: "₹4,500.00",
      timeline: [
        { time: "12:15:00 UTC", actor: "CUSTOMER", event: "REQUESTED", notes: "Royal Limousine reservation" },
        { time: "12:15:22 UTC", actor: "DRIVER", event: "ASSIGNED", notes: "Master Chauffeur assigned" },
        { time: "12:20:05 UTC", actor: "DRIVER", event: "ARRIVED", notes: "Arrival at BKC North Entrance" },
        { time: "12:22:40 UTC", actor: "DRIVER", event: "IN_PROGRESS", notes: "Trip started" },
        { time: "13:05:18 UTC", actor: "DRIVER", event: "COMPLETED", notes: "Guest disembarked safely" }
      ]
    }
  ]);

  const [selectedRide, setSelectedRide] = useState<AdminRide | null>(null);
  const [statusFilter, setStatusFilter] = useState("all");

  const filtered = rides.filter(r => statusFilter === "all" || r.status === statusFilter);

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Ride Lifecycle Audit & Exception Inspection</h1>
          <p className="page-description">Examine journey trajectories, lifecycle transitions, and immutable audit logs.</p>
        </div>
      </div>

      <div className="table-card">
        <div className="table-toolbar">
          <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
            <select
              className="search-input"
              style={{ width: 160 }}
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value)}
            >
              <option value="all">All States</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </div>
          <span className="badge badge-gold">{filtered.length} Recorded Journeys</span>
        </div>

        <table className="luxury-table">
          <thead>
            <tr>
              <th>Ride ID</th>
              <th>Distinguished Guest</th>
              <th>Assigned Chauffeur</th>
              <th>Route</th>
              <th>Lifecycle State</th>
              <th>Contract Fare</th>
              <th>Audit Trail</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map(ride => (
              <tr key={ride.id}>
                <td style={{ fontFamily: 'var(--font-mono)' }}>{ride.id}</td>
                <td style={{ fontWeight: 600 }}>{ride.guest}</td>
                <td>{ride.driver} ({ride.vehicle})</td>
                <td>{ride.pickup} → {ride.destination}</td>
                <td>
                  <span className={`badge ${ride.status === 'COMPLETED' ? 'badge-emerald' : (ride.status === 'IN_PROGRESS' ? 'badge-amber' : 'badge-crimson')}`}>
                    {ride.status}
                  </span>
                </td>
                <td style={{ fontWeight: 600 }}>{ride.fare}</td>
                <td>
                  <button className="btn-secondary" onClick={() => setSelectedRide(ride)}>
                    Inspect Timeline
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {selectedRide && (
        <div className="modal-overlay" onClick={() => setSelectedRide(null)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2 style={{ fontFamily: 'var(--font-display)', color: 'var(--gold-primary)', marginBottom: 8 }}>
              Lifecycle Event Log: {selectedRide.id}
            </h2>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem', marginBottom: 24 }}>
              Immutable state transitions recorded in PostgreSQL <code>ride_status_events</code>
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginBottom: 24 }}>
              {selectedRide.timeline.map((item, idx) => (
                <div
                  key={idx}
                  style={{
                    borderLeft: '2px solid var(--gold-primary)',
                    paddingLeft: 16,
                    position: 'relative'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span className="badge badge-gold">{item.event}</span>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                      {item.time}
                    </span>
                  </div>
                  <div style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', marginTop: 4 }}>
                    Actor: <strong>{item.actor}</strong> • {item.notes}
                  </div>
                </div>
              ))}
            </div>

            <button className="btn-gold" style={{ width: '100%' }} onClick={() => setSelectedRide(null)}>
              Close Audit Inspection
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
