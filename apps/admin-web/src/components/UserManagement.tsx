import React, { useState, useEffect } from 'react';
import { fetchWithAuth } from '../services/api';

interface User {
  id: string;
  phone_number: string;
  email: string | null;
  full_name: string;
  role: string;
  is_active: boolean;
  email_verified?: boolean;
  mfa_enabled?: boolean;
  biometric_enabled?: boolean;
  has_pin?: boolean;
  has_passkey?: boolean;
  created_at: string;
}

export const UserManagement: React.FC = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("all");
  const [isLoading, setIsLoading] = useState(false);

  const loadUsers = async () => {
    setIsLoading(true);
    const data = await fetchWithAuth('/admin/users?limit=100');
    if (data && data.users) {
      setUsers(data.users);
    }
    setIsLoading(false);
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const toggleUserStatus = async (userId: string) => {
    const targetUser = users.find(u => u.id === userId);
    if (!targetUser) return;
    const newStatus = !targetUser.is_active;

    // Optimistic UI update
    setUsers(prev => prev.map(u => u.id === userId ? { ...u, is_active: newStatus } : u));

    const res = await fetchWithAuth(`/admin/users/${userId}/status`, {
      method: 'PUT',
      body: JSON.stringify({ is_active: newStatus })
    });
    if (!res) {
      // Revert if API failed
      setUsers(prev => prev.map(u => u.id === userId ? { ...u, is_active: !newStatus } : u));
    }
  };

  const filtered = users.filter(u => {
    const fullName = u.full_name || '';
    const phone = u.phone_number || '';
    const matchesSearch = fullName.toLowerCase().includes(search.toLowerCase()) || phone.includes(search);
    const matchesRole = roleFilter === "all" || u.role === roleFilter;
    return matchesSearch && matchesRole;
  });

  const mfaCount = users.filter(u => u.mfa_enabled).length;
  const verifiedEmailCount = users.filter(u => u.email_verified).length;
  const biometricCount = users.filter(u => u.biometric_enabled).length;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">User Account Governance & Security</h1>
          <p className="page-description">Oversee multi-factor authentication, verified identity records, biometric vault locks, and GDPR compliance.</p>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginBottom: '24px' }}>
        <div className="table-card" style={{ padding: '16px', textAlign: 'center' }}>
          <div style={{ color: 'var(--text-muted)', fontSize: '12px', textTransform: 'uppercase', letterSpacing: '1px' }}>2FA / MFA Adoption</div>
          <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--gold-primary)', marginTop: '4px' }}>{mfaCount} / {users.length}</div>
          <div style={{ fontSize: '12px', color: 'var(--emerald-primary)', marginTop: '2px' }}>Cryptographic TOTP</div>
        </div>
        <div className="table-card" style={{ padding: '16px', textAlign: 'center' }}>
          <div style={{ color: 'var(--text-muted)', fontSize: '12px', textTransform: 'uppercase', letterSpacing: '1px' }}>Verified Emails</div>
          <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--emerald-primary)', marginTop: '4px' }}>{verifiedEmailCount} Accounts</div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '2px' }}>Verified Delivery</div>
        </div>
        <div className="table-card" style={{ padding: '16px', textAlign: 'center' }}>
          <div style={{ color: 'var(--text-muted)', fontSize: '12px', textTransform: 'uppercase', letterSpacing: '1px' }}>Biometric Vaults</div>
          <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--gold-light)', marginTop: '4px' }}>{biometricCount} Armed</div>
          <div style={{ fontSize: '12px', color: 'var(--gold-primary)', marginTop: '2px' }}>Hardware Enclave</div>
        </div>
      </div>

      <div className="table-card">
        <div className="table-toolbar">
          <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
            <input
              type="text"
              placeholder="Search by name or phone..."
              className="search-input"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
            <select
              className="search-input"
              style={{ width: 140 }}
              value={roleFilter}
              onChange={e => setRoleFilter(e.target.value)}
            >
              <option value="all">All Roles</option>
              <option value="customer">Customers</option>
              <option value="driver">Chauffeurs</option>
              <option value="admin">Administrators</option>
            </select>
          </div>
          <span className="badge badge-gold">{filtered.length} Registered Accounts</span>
        </div>

        <table className="luxury-table">
          <thead>
            <tr>
              <th>Full Name</th>
              <th>Telephone</th>
              <th>Email & Status</th>
              <th>Security & 2FA</th>
              <th>Role</th>
              <th>Account Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={7} style={{ textAlign: 'center', padding: '32px', color: 'var(--gold-primary)' }}>
                  Loading accounts from executive ledger...
                </td>
              </tr>
            ) : filtered.length === 0 ? (
              <tr>
                <td colSpan={7} style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>
                  No registered accounts found matching filter.
                </td>
              </tr>
            ) : (
              filtered.map(user => (
                <tr key={user.id}>
                  <td style={{ fontWeight: 600 }}>{user.full_name}</td>
                  <td style={{ fontFamily: 'var(--font-mono)' }}>{user.phone_number}</td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <span>{user.email || '—'}</span>
                      {user.email && (
                        <span className={`badge ${user.email_verified ? 'badge-emerald' : 'badge-crimson'}`} style={{ fontSize: '10px', padding: '2px 6px' }}>
                          {user.email_verified ? 'VERIFIED' : 'UNVERIFIED'}
                        </span>
                      )}
                    </div>
                  </td>
                  <td>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                      {user.mfa_enabled && <span className="badge badge-gold" style={{ fontSize: '10px' }}>MFA TOTP</span>}
                      {user.biometric_enabled && <span className="badge badge-emerald" style={{ fontSize: '10px' }}>BIOMETRIC</span>}
                      {user.has_pin && <span className="badge badge-amber" style={{ fontSize: '10px' }}>PIN</span>}
                      {user.has_passkey && <span className="badge badge-gold" style={{ fontSize: '10px' }}>PASSKEY</span>}
                      {!user.mfa_enabled && !user.biometric_enabled && !user.has_pin && !user.has_passkey && (
                        <span style={{ color: 'var(--text-muted)', fontSize: '12px' }}>STANDARD</span>
                      )}
                    </div>
                  </td>
                  <td>
                    <span className={`badge ${user.role === 'admin' ? 'badge-gold' : (user.role === 'driver' ? 'badge-amber' : 'badge-emerald')}`}>
                      {user.role.toUpperCase()}
                    </span>
                  </td>
                  <td>
                    <span className={`badge ${user.is_active ? 'badge-emerald' : 'badge-crimson'}`}>
                      {user.is_active ? 'ACTIVE' : 'SUSPENDED'}
                    </span>
                  </td>
                  <td>
                    <button
                      className="btn-secondary"
                      onClick={() => toggleUserStatus(user.id)}
                    >
                      {user.is_active ? 'Suspend Access' : 'Restore Access'}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
