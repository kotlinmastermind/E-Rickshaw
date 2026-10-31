import React, { useState, useEffect } from 'react';
import { fetchWithAuth } from '../services/api';

interface AuditLogEntry {
  id: string;
  actor_id: string | null;
  actor_role: string;
  action: string;
  target_resource: string;
  target_id: string | null;
  status: string;
  correlation_id: string | null;
  ip_address: string | null;
  metadata: Record<string, any>;
  created_at: string;
}

export const AdminAuditLogViewer: React.FC = () => {
  const [logs, setLogs] = useState<AuditLogEntry[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [actorRole, setActorRole] = useState('');
  const [actionFilter, setActionFilter] = useState('');
  const [resourceFilter, setResourceFilter] = useState('');
  const [expandedLogId, setExpandedLogId] = useState<string | null>(null);

  useEffect(() => {
    fetchAuditLogs();
  }, [page, actorRole, actionFilter, resourceFilter]);

  const fetchAuditLogs = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        page: page.toString(),
        limit: '25'
      });
      if (actorRole) params.append('actor_role', actorRole);
      if (actionFilter) params.append('action', actionFilter);
      if (resourceFilter) params.append('target_resource', resourceFilter);

      const data = await fetchWithAuth(`/admin/audit-logs?${params.toString()}`);
      if (data) {
        setLogs(data.logs || []);
        setTotalCount(data.total_count || 0);
      }
    } catch (err) {
      console.error('Failed to load audit logs', err);
    } finally {
      setLoading(false);
    }
  };

  const getActionBadgeColor = (action: string) => {
    if (action.includes('AUTH') || action.includes('REVOKE')) return 'bg-amber-950/80 text-amber-300 border-amber-800/40';
    if (action.includes('POOL') || action.includes('SEAT')) return 'bg-cyan-950/80 text-cyan-300 border-cyan-800/40';
    if (action.includes('PAYOUT') || action.includes('FARE')) return 'bg-purple-950/80 text-purple-300 border-purple-800/40';
    if (action.includes('BACKUP')) return 'bg-emerald-950/80 text-emerald-300 border-emerald-800/40';
    return 'bg-slate-800 text-slate-300 border-slate-700';
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-amber-900/30 pb-6">
        <div>
          <h2 className="text-2xl font-serif text-amber-100 flex items-center gap-3">
            <span>Immutable Security & Operational Audit Log</span>
            <span className="text-xs font-mono uppercase bg-slate-800 text-slate-300 px-2.5 py-1 rounded-full border border-slate-700">
              {totalCount} Total Entries
            </span>
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Searchable, tamper-evident log capturing authentication, fare policies, pooling allocations, and administrative state changes.
          </p>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 bg-slate-900/70 border border-slate-800 rounded-xl p-4">
        <div>
          <label className="text-xs uppercase tracking-wider text-slate-400 block mb-1">Actor Role</label>
          <select
            value={actorRole}
            onChange={(e) => { setActorRole(e.target.value); setPage(1); }}
            className="w-full bg-slate-950 border border-slate-800 text-slate-200 text-sm rounded-lg p-2.5 outline-none focus:border-amber-500"
          >
            <option value="">All Roles</option>
            <option value="admin">Administrator</option>
            <option value="customer">Customer</option>
            <option value="driver">Driver</option>
            <option value="SYSTEM">System Process</option>
          </select>
        </div>

        <div>
          <label className="text-xs uppercase tracking-wider text-slate-400 block mb-1">Action Type</label>
          <select
            value={actionFilter}
            onChange={(e) => { setActionFilter(e.target.value); setPage(1); }}
            className="w-full bg-slate-950 border border-slate-800 text-slate-200 text-sm rounded-lg p-2.5 outline-none focus:border-amber-500"
          >
            <option value="">All Actions</option>
            <option value="POOL_SEAT_RESERVE">POOL_SEAT_RESERVE</option>
            <option value="POOL_PASSENGER_BOARDED">POOL_PASSENGER_BOARDED</option>
            <option value="POOL_PASSENGER_ALIGHTED">POOL_PASSENGER_ALIGHTED</option>
            <option value="UPDATE_FARE_RULE">UPDATE_FARE_RULE</option>
            <option value="BACKUP_TRIGGERED">BACKUP_TRIGGERED</option>
            <option value="PAYOUT_APPROVE">PAYOUT_APPROVE</option>
          </select>
        </div>

        <div>
          <label className="text-xs uppercase tracking-wider text-slate-400 block mb-1">Resource</label>
          <select
            value={resourceFilter}
            onChange={(e) => { setResourceFilter(e.target.value); setPage(1); }}
            className="w-full bg-slate-950 border border-slate-800 text-slate-200 text-sm rounded-lg p-2.5 outline-none focus:border-amber-500"
          >
            <option value="">All Resources</option>
            <option value="pooled_bookings">pooled_bookings</option>
            <option value="rides">rides</option>
            <option value="fare_rules">fare_rules</option>
            <option value="database_backups">database_backups</option>
          </select>
        </div>
      </div>

      {/* Logs Table */}
      <div className="bg-slate-900/70 border border-slate-800 rounded-xl overflow-hidden shadow-xl">
        {loading ? (
          <div className="p-12 text-center text-amber-300 font-serif">Querying security audit records...</div>
        ) : logs.length === 0 ? (
          <div className="p-12 text-center text-slate-500">No audit logs matching specified filters.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="bg-slate-950/80 text-xs uppercase tracking-wider text-slate-400 border-b border-slate-800">
                <tr>
                  <th className="py-3 px-4">Timestamp (UTC)</th>
                  <th className="py-3 px-4">Actor</th>
                  <th className="py-3 px-4">Action</th>
                  <th className="py-3 px-4">Resource</th>
                  <th className="py-3 px-4">Target ID</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {logs.map((log) => {
                  const isExpanded = expandedLogId === log.id;
                  return (
                    <React.Fragment key={log.id}>
                      <tr className="hover:bg-slate-800/40 transition">
                        <td className="py-3 px-4 font-mono text-xs text-slate-400 whitespace-nowrap">
                          {new Date(log.created_at).toLocaleString('en-US', { timeZone: 'UTC' })}
                        </td>
                        <td className="py-3 px-4">
                          <span className="font-mono text-xs uppercase bg-slate-800 text-amber-300 px-2 py-0.5 rounded border border-slate-700 mr-1.5">
                            {log.actor_role}
                          </span>
                          <span className="text-xs text-slate-400 font-mono">
                            {log.actor_id ? `${log.actor_id.slice(0, 8)}...` : 'System'}
                          </span>
                        </td>
                        <td className="py-3 px-4">
                          <span className={`text-xs font-mono px-2 py-0.5 rounded border ${getActionBadgeColor(log.action)}`}>
                            {log.action}
                          </span>
                        </td>
                        <td className="py-3 px-4 font-mono text-xs text-slate-300">{log.target_resource}</td>
                        <td className="py-3 px-4 font-mono text-xs text-slate-400">
                          {log.target_id ? `${log.target_id.slice(0, 8)}...` : '-'}
                        </td>
                        <td className="py-3 px-4">
                          <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                            log.status === 'SUCCESS' ? 'bg-emerald-950/80 text-emerald-400 border border-emerald-800/40' : 'bg-rose-950/80 text-rose-400 border border-rose-800/40'
                          }`}>
                            {log.status}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-right">
                          <button
                            onClick={() => setExpandedLogId(isExpanded ? null : log.id)}
                            className="text-xs text-amber-400 hover:text-amber-300 transition"
                          >
                            {isExpanded ? 'Hide' : 'Inspect'}
                          </button>
                        </td>
                      </tr>
                      {isExpanded && (
                        <tr className="bg-slate-950/90 border-b border-amber-900/20">
                          <td colSpan={7} className="p-4">
                            <div className="bg-slate-900 border border-slate-800 rounded-lg p-3">
                              <span className="text-xs uppercase tracking-wider text-slate-400 block mb-2 font-mono">
                                Redacted Metadata Snapshot & Network Context
                              </span>
                              <pre className="text-xs font-mono text-amber-200 overflow-x-auto p-2 bg-slate-950 rounded">
                                {JSON.stringify(
                                  {
                                    audit_id: log.id,
                                    ip_address: log.ip_address || 'Cloud Gateway',
                                    correlation_id: log.correlation_id,
                                    metadata: log.metadata
                                  },
                                  null,
                                  2
                                )}
                              </pre>
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Bar */}
        <div className="flex items-center justify-between p-4 bg-slate-950/80 border-t border-slate-800 text-xs text-slate-400">
          <span>Showing Page {page} (25 entries per page)</span>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage(Math.max(1, page - 1))}
              disabled={page <= 1}
              className="px-3 py-1 bg-slate-900 border border-slate-800 rounded hover:bg-slate-800 disabled:opacity-40 transition"
            >
              Previous
            </button>
            <button
              onClick={() => setPage(page + 1)}
              disabled={logs.length < 25}
              className="px-3 py-1 bg-slate-900 border border-slate-800 rounded hover:bg-slate-800 disabled:opacity-40 transition"
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
