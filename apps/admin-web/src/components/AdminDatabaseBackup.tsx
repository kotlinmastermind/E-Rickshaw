import React, { useState, useEffect } from 'react';
import { fetchWithAuth } from '../services/api';

interface BackupSnapshot {
  filename: string;
  size_bytes: number;
  created_at: string;
  status: string;
  retention_status: string;
}

export const AdminDatabaseBackup: React.FC = () => {
  const [backups, setBackups] = useState<BackupSnapshot[]>([]);
  const [loading, setLoading] = useState(true);
  const [triggering, setTriggering] = useState(false);
  const [lastSnapshotResult, setLastSnapshotResult] = useState<any | null>(null);

  useEffect(() => {
    fetchBackups();
  }, []);

  const fetchBackups = async () => {
    setLoading(true);
    try {
      const data = await fetchWithAuth('/admin/backups');
      if (data) {
        setBackups(data || []);
      }
    } catch (err) {
      console.error('Failed to load database backups', err);
    } finally {
      setLoading(false);
    }
  };

  const handleTriggerSnapshot = async () => {
    setTriggering(true);
    setLastSnapshotResult(null);
    try {
      const data = await fetchWithAuth('/admin/backups/trigger', {
        method: 'POST'
      });
      if (data) {
        setLastSnapshotResult(data);
        await fetchBackups();
      }
    } catch (err) {
      console.error('Failed to trigger snapshot', err);
    } finally {
      setTriggering(false);
    }
  };

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-amber-900/30 pb-6">
        <div>
          <h2 className="text-2xl font-serif text-amber-100 flex items-center gap-3">
            <span>Database Backup, Integrity & Disaster Recovery</span>
            <span className="text-xs font-mono uppercase bg-emerald-950/80 text-emerald-400 px-2.5 py-1 rounded-full border border-emerald-800/40">
              RPO: 1h | RTO: 15m
            </span>
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Deterministic snapshot generation, SHA-256 cryptographic verification, and sandbox test restoration.
          </p>
        </div>
        <button
          onClick={handleTriggerSnapshot}
          disabled={triggering}
          className="px-5 py-2.5 bg-gradient-to-r from-amber-600 to-amber-700 hover:from-amber-500 hover:to-amber-600 text-white rounded-lg text-sm font-medium transition shadow-lg shadow-amber-900/20 disabled:opacity-50 flex items-center gap-2"
        >
          <span>{triggering ? 'Executing Sandbox Restore Probe...' : 'Generate Verified Snapshot'}</span>
        </button>
      </div>

      {/* SLA Policy & Target Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5">
          <span className="text-xs uppercase tracking-wider text-slate-400 font-mono">Recovery Point Objective (RPO)</span>
          <div className="text-2xl font-serif text-amber-300 mt-2">1 Hour</div>
          <span className="text-xs text-slate-500 mt-1 block">Maximum permissible ledger data variance</span>
        </div>

        <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5">
          <span className="text-xs uppercase tracking-wider text-slate-400 font-mono">Recovery Time Objective (RTO)</span>
          <div className="text-2xl font-serif text-cyan-300 mt-2">15 Minutes</div>
          <span className="text-xs text-slate-500 mt-1 block">Cold standby instance restoration SLA</span>
        </div>

        <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5">
          <span className="text-xs uppercase tracking-wider text-slate-400 font-mono">Retention & Integrity Check</span>
          <div className="text-2xl font-serif text-emerald-400 mt-2">SHA-256 + Sandbox</div>
          <span className="text-xs text-slate-500 mt-1 block">Every snapshot verified in an isolated schema</span>
        </div>
      </div>

      {/* Success Banner */}
      {lastSnapshotResult && (
        <div className="p-4 bg-emerald-950/80 border border-emerald-800/40 rounded-xl text-emerald-200 text-xs font-mono space-y-2">
          <div className="font-bold text-emerald-300 flex items-center justify-between">
            <span>Snapshot Verified and Stored Successfully</span>
            <span className="bg-emerald-900/60 px-2 py-0.5 rounded">PASSED</span>
          </div>
          <div>File: {lastSnapshotResult.filename} ({formatBytes(lastSnapshotResult.size_bytes)})</div>
          <div className="truncate">SHA-256: {lastSnapshotResult.sha256_checksum}</div>
          <div>Sandbox Tables Verified: {lastSnapshotResult.validation_report?.verified_tables_count || 0} tables intact</div>
        </div>
      )}

      {/* Snapshots Table */}
      <div className="bg-slate-900/70 border border-slate-800 rounded-xl overflow-hidden shadow-xl">
        <div className="p-4 bg-slate-950/80 border-b border-slate-800 flex items-center justify-between">
          <h3 className="text-sm font-serif text-amber-200 uppercase tracking-wider">Archived Database Snapshots</h3>
          <span className="text-xs text-slate-400 font-mono">{backups.length} Verified Copies</span>
        </div>

        {loading ? (
          <div className="p-12 text-center text-amber-300 font-serif">Querying backup storage volume...</div>
        ) : backups.length === 0 ? (
          <div className="p-12 text-center text-slate-500">No backup snapshots found. Generate your first verified snapshot above.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="bg-slate-950/80 text-xs uppercase tracking-wider text-slate-400 border-b border-slate-800">
                <tr>
                  <th className="py-3 px-4">Snapshot Filename</th>
                  <th className="py-3 px-4">Size</th>
                  <th className="py-3 px-4">Timestamp (UTC)</th>
                  <th className="py-3 px-4">Integrity Status</th>
                  <th className="py-3 px-4">Retention</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {backups.map((b) => (
                  <tr key={b.filename} className="hover:bg-slate-800/40 transition">
                    <td className="py-3 px-4 font-mono text-xs text-amber-300">{b.filename}</td>
                    <td className="py-3 px-4 font-mono text-xs text-slate-300">{formatBytes(b.size_bytes)}</td>
                    <td className="py-3 px-4 font-mono text-xs text-slate-400 whitespace-nowrap">
                      {new Date(b.created_at).toLocaleString('en-US', { timeZone: 'UTC' })}
                    </td>
                    <td className="py-3 px-4">
                      <span className="text-xs px-2.5 py-0.5 rounded-full font-medium bg-emerald-950/80 text-emerald-400 border border-emerald-800/40">
                        {b.status}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-mono">
                        {b.retention_status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
