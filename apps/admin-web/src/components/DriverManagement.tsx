import React, { useState, useEffect } from 'react';
import { fetchWithAuth } from '../services/api';

interface Chauffeur {
  id: string;
  name: string;
  license_number: string;
  vehicle: string;
  rating: number;
  total_trips: number;
  approval_status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
  documents: { type: string; filename: string; verified: boolean }[];
}

export const DriverManagement: React.FC = () => {
  const [drivers, setDrivers] = useState<Chauffeur[]>([
    {
      id: "drv-001",
      name: "Rajeshwar Varma",
      license_number: "DL-MH01-2020-009988",
      vehicle: "Mercedes-Benz E-Class (MH01-LX-0001)",
      rating: 4.98,
      total_trips: 340,
      approval_status: "APPROVED",
      documents: [
        { type: "Commercial License", filename: "license_mh01.pdf", verified: true },
        { type: "Vehicle RC", filename: "rc_eclass.pdf", verified: true },
        { type: "Fleet Insurance", filename: "insurance_policy.pdf", verified: true }
      ]
    },
    {
      id: "drv-002",
      name: "Devendra Patil",
      license_number: "DL-MH02-2022-004412",
      vehicle: "Range Rover Autobiography (MH01-LX-0002)",
      rating: 5.0,
      total_trips: 0,
      approval_status: "PENDING",
      documents: [
        { type: "Commercial License", filename: "dl_patil.pdf", verified: false },
        { type: "Vehicle RC", filename: "rc_rangerover.pdf", verified: false }
      ]
    }
  ]);

  const [selectedDriver, setSelectedDriver] = useState<Chauffeur | null>(null);
  const [rejectionReason, setRejectionReason] = useState("");

  useEffect(() => {
    fetchWithAuth('/admin/drivers').then(data => {
      if (data) {
        const list = Array.isArray(data) ? data : (data.items || data.drivers || []);
        if (list.length > 0) {
          setDrivers(list.map((i: any) => ({
            id: i.driver_id || i.id,
            name: i.full_name || i.name || "Chauffeur",
            license_number: i.license_number || "DL-PENDING-001",
            vehicle: i.vehicle_name || i.vehicle || "E-Rickshaw Fleet EV",
            rating: i.rating_average ?? i.rating ?? 5.0,
            total_trips: i.total_trips ?? 0,
            approval_status: i.approval_status || "PENDING",
            documents: [
              { type: "Commercial License", filename: "license.pdf", verified: i.approval_status === "APPROVED" },
              { type: "Vehicle Registration", filename: "rc_doc.pdf", verified: i.approval_status === "APPROVED" }
            ]
          })));
        }
      }
    });
  }, []);

  const updateApproval = async (driverId: string, status: 'APPROVED' | 'REJECTED') => {
    try {
      await fetchWithAuth(`/admin/drivers/${driverId}/approval`, {
        method: 'PUT',
        body: JSON.stringify({
          approval_status: status,
          rejection_reason: status === 'REJECTED' ? rejectionReason : null
        })
      });
    } catch (_e) {}

    setDrivers(prev => prev.map(d => {
      if (d.id === driverId) {
        return { ...d, approval_status: status };
      }
      return d;
    }));
    setSelectedDriver(null);
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Chauffeur Accreditation & Fleet Compliance</h1>
          <p className="page-description">Vetting, background verification, and operational credentials for luxury chauffeurs.</p>
        </div>
      </div>

      <div className="table-card">
        <div className="table-toolbar">
          <h2 style={{ fontSize: '1.05rem', fontWeight: 600 }}>Chauffeur Verification Roster</h2>
          <span className="badge badge-gold">Active Fleet Management</span>
        </div>

        <table className="luxury-table">
          <thead>
            <tr>
              <th>Chauffeur Name</th>
              <th>Commercial License</th>
              <th>Assigned Luxury Vehicle</th>
              <th>Rating & Trips</th>
              <th>Verification Status</th>
              <th>Review Actions</th>
            </tr>
          </thead>
          <tbody>
            {drivers.map(drv => (
              <tr key={drv.id}>
                <td style={{ fontWeight: 600 }}>{drv.name}</td>
                <td style={{ fontFamily: 'var(--font-mono)' }}>{drv.license_number}</td>
                <td>{drv.vehicle}</td>
                <td>★ {drv.rating} ({drv.total_trips} trips)</td>
                <td>
                  <span className={`badge ${drv.approval_status === 'APPROVED' ? 'badge-emerald' : (drv.approval_status === 'PENDING' ? 'badge-amber' : 'badge-crimson')}`}>
                    {drv.approval_status}
                  </span>
                </td>
                <td>
                  <button
                    className="btn-secondary"
                    onClick={() => setSelectedDriver(drv)}
                  >
                    Inspect Documents
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {selectedDriver && (
        <div className="modal-overlay" onClick={() => setSelectedDriver(null)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2 style={{ fontFamily: 'var(--font-display)', color: 'var(--gold-primary)', marginBottom: 8 }}>
              Chauffeur Compliance Review
            </h2>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem', marginBottom: 20 }}>
              Review credentials for <strong>{selectedDriver.name}</strong> ({selectedDriver.license_number})
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 24 }}>
              {selectedDriver.documents.map((doc, idx) => (
                <div
                  key={idx}
                  style={{
                    background: 'var(--bg-card)',
                    padding: '12px 16px',
                    borderRadius: 8,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center'
                  }}
                >
                  <div>
                    <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>{doc.type}</div>
                    <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{doc.filename}</div>
                  </div>
                  <span className={`badge ${doc.verified ? 'badge-emerald' : 'badge-amber'}`}>
                    {doc.verified ? 'VERIFIED' : 'PENDING'}
                  </span>
                </div>
              ))}
            </div>

            <div style={{ marginBottom: 16 }}>
              <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Compliance Notes / Rejection Reason</label>
              <input
                type="text"
                placeholder="e.g., Insurance policy renewal required..."
                className="search-input"
                style={{ width: '100%', marginTop: 4 }}
                value={rejectionReason}
                onChange={e => setRejectionReason(e.target.value)}
              />
            </div>

            <div style={{ display: 'flex', gap: 12 }}>
              <button
                className="btn-gold"
                style={{ flex: 1 }}
                onClick={() => updateApproval(selectedDriver.id, 'APPROVED')}
              >
                Approve & Grant Online Access
              </button>
              <button
                className="btn-secondary"
                style={{ color: 'var(--status-crimson)', borderColor: 'var(--status-crimson)' }}
                onClick={() => updateApproval(selectedDriver.id, 'REJECTED')}
              >
                Reject Application
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
