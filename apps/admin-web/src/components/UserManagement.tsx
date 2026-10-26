import React, { useState } from 'react';


interface User {
  id: string;
  phone_number: string;
  email: string | null;
  full_name: string;
  role: string;
  is_active: boolean;
  created_at: string;
}

export const UserManagement: React.FC = () => {
  const [users, setUsers] = useState<User[]>([
    {
      id: "u-001",
      phone_number: "+91 98765 43210",
      email: "sterling@luxury.io",
      full_name: "Lord Sterling",
      role: "customer",
      is_active: true,
      created_at: "2026-10-20"
    },
    {
      id: "u-002",
      phone_number: "+91 98765 00001",
      email: "chauffeur.sterling@luxury.io",
      full_name: "Rajeshwar Varma",
      role: "driver",
      is_active: true,
      created_at: "2026-10-18"
    },
    {
      id: "u-003",
      phone_number: "+91 98111 99999",
      email: "suspect@unverified.net",
      full_name: "Unverified Entity",
      role: "customer",
      is_active: false,
      created_at: "2026-10-22"
    }
  ]);

  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("all");

  const toggleUserStatus = (userId: string) => {
    setUsers(prev => prev.map(u => {
      if (u.id === userId) {
        return { ...u, is_active: !u.is_active };
      }
      return u;
    }));
  };

  const filtered = users.filter(u => {
    const matchesSearch = u.full_name.toLowerCase().includes(search.toLowerCase()) || u.phone_number.includes(search);
    const matchesRole = roleFilter === "all" || u.role === roleFilter;
    return matchesSearch && matchesRole;
  });

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">User Account Governance</h1>
          <p className="page-description">Manage distinguished guests, verified chauffeurs, and administrative personnel.</p>
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
              <th>Email</th>
              <th>Role</th>
              <th>Account Status</th>
              <th>Registration</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map(user => (
              <tr key={user.id}>
                <td style={{ fontWeight: 600 }}>{user.full_name}</td>
                <td style={{ fontFamily: 'var(--font-mono)' }}>{user.phone_number}</td>
                <td>{user.email || '—'}</td>
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
                <td>{user.created_at}</td>
                <td>
                  <button
                    className="btn-secondary"
                    onClick={() => toggleUserStatus(user.id)}
                  >
                    {user.is_active ? 'Suspend Access' : 'Restore Access'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
