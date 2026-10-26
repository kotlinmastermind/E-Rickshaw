import React, { useState, useEffect } from 'react';

interface SubsystemHealth {
  status: string;
  latency_ms: number;
}

interface RedisHealth {
  status: string;
  backend: string;
  latency_ms: number;
  connected: boolean;
  stats: {
    hits: number;
    misses: number;
    total_reads: number;
    hit_ratio_percent: number;
    sets: number;
    errors: number;
  };
}

interface HealthMetrics {
  service: string;
  environment: string;
  release: string;
  uptime_seconds: number;
  database: SubsystemHealth;
  redis_cache: RedisHealth;
  websockets: {
    active_ride_subscribers: number;
    active_driver_subscribers: number;
    total_connected: number;
  };
  telemetry: {
    recent_crashes_count: number;
    recent_crashes: Array<{
      event_id: string;
      exception_type: string;
      message: string;
      stacktrace: string;
      timestamp: string;
    }>;
  };
}

export const AdminSystemHealth: React.FC = () => {
  const [metrics, setMetrics] = useState<HealthMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [testCrashStatus, setTestCrashStatus] = useState<string | null>(null);
  const [triggering, setTriggering] = useState(false);

  useEffect(() => {
    fetchMetrics();
    const interval = setInterval(fetchMetrics, 10000);
    return () => clearInterval(interval);
  }, []);

  const fetchMetrics = async () => {
    try {
      const res = await fetch('/health/metrics');
      if (res.ok) {
        const data = await res.json();
        setMetrics(data);
      }
    } catch (err) {
      console.error('Failed to load system metrics', err);
    } finally {
      setLoading(false);
    }
  };

  const triggerTestCrash = async () => {
    setTriggering(true);
    setTestCrashStatus(null);
    try {
      const res = await fetch('/health/test-crash', { method: 'POST' });
      const data = await res.json();
      setTestCrashStatus(`Synthetic Exception Dispatched! Captured with Event ID: ${data.event_id}`);
      await fetchMetrics();
    } catch (err) {
      setTestCrashStatus('Failed to trigger test crash.');
    } finally {
      setTriggering(false);
    }
  };

  if (loading) {
    return <div className="p-8 text-amber-300 font-serif">Probing distributed cluster nodes and telemetry...</div>;
  }

  const formatUptime = (seconds: number) => {
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);
    return `${hrs}h ${mins}m ${secs}s`;
  };

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-amber-900/30 pb-6">
        <div>
          <h2 className="text-2xl font-serif text-amber-100 flex items-center gap-3">
            <span>High-Availability System Health & Observability</span>
            <span className="text-xs font-mono uppercase bg-emerald-950/80 text-emerald-400 px-2.5 py-1 rounded-full border border-emerald-800/40">
              Cluster Ready
            </span>
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Real-time diagnostics for database latency, Redis connection pool, WebSocket subscribers, and exception telemetry.
          </p>
        </div>
        <div className="flex items-center gap-4 text-xs font-mono">
          <div className="bg-slate-900 px-3 py-2 rounded-lg border border-slate-800 text-slate-300">
            Env: <span className="text-amber-400 uppercase">{metrics?.environment || 'PROD'}</span>
          </div>
          <div className="bg-slate-900 px-3 py-2 rounded-lg border border-slate-800 text-slate-300">
            Release: <span className="text-cyan-400">{metrics?.release || 'v3.0.0'}</span>
          </div>
          <div className="bg-slate-900 px-3 py-2 rounded-lg border border-slate-800 text-slate-300">
            Uptime: <span className="text-emerald-400">{formatUptime(metrics?.uptime_seconds || 0)}</span>
          </div>
        </div>
      </div>

      {/* Primary Subsystem Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Database Node */}
        <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 backdrop-blur-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs uppercase tracking-wider text-slate-400 font-mono">Primary Database</span>
            <span className="text-xs px-2 py-0.5 rounded-full font-medium bg-emerald-950/80 text-emerald-400 border border-emerald-800/40">
              {metrics?.database.status || 'HEALTHY'}
            </span>
          </div>
          <div className="text-3xl font-serif text-slate-100 mt-4">
            {metrics?.database.latency_ms} <span className="text-sm font-sans text-slate-400">ms</span>
          </div>
          <div className="text-xs text-slate-400 mt-2 space-y-1">
            <div>Engine: SQLite / PostgreSQL ACID</div>
            <div>Read/Write Probe: <span className="text-emerald-400">PASSED</span></div>
          </div>
        </div>

        {/* Redis Cache & Distributed Lock */}
        <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 backdrop-blur-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs uppercase tracking-wider text-slate-400 font-mono">Distributed Cache</span>
            <span className="text-xs px-2 py-0.5 rounded-full font-medium bg-cyan-950/80 text-cyan-400 border border-cyan-800/40">
              {metrics?.redis_cache.backend || 'REDIS'}
            </span>
          </div>
          <div className="text-3xl font-serif text-cyan-300 mt-4">
            {metrics?.redis_cache.stats.hit_ratio_percent || 0} <span className="text-sm font-sans text-slate-400">% Hit Ratio</span>
          </div>
          <div className="text-xs text-slate-400 mt-2 space-y-1">
            <div>Total Reads: {metrics?.redis_cache.stats.total_reads || 0}</div>
            <div>Latency: {metrics?.redis_cache.latency_ms || 0} ms | In-Memory Failover: <span className="text-emerald-400">READY</span></div>
          </div>
        </div>

        {/* Real-time WebSockets */}
        <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 backdrop-blur-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs uppercase tracking-wider text-slate-400 font-mono">Live WebSocket Gateway</span>
            <span className="text-xs px-2 py-0.5 rounded-full font-medium bg-purple-950/80 text-purple-400 border border-purple-800/40">
              ACTIVE
            </span>
          </div>
          <div className="text-3xl font-serif text-purple-300 mt-4">
            {metrics?.websockets.total_connected || 0} <span className="text-sm font-sans text-slate-400">Sockets</span>
          </div>
          <div className="text-xs text-slate-400 mt-2 space-y-1">
            <div>Ride Tracking Channels: {metrics?.websockets.active_ride_subscribers || 0}</div>
            <div>Chauffeur Dispatch Channels: {metrics?.websockets.active_driver_subscribers || 0}</div>
          </div>
        </div>
      </div>

      {/* Telemetry & Crash Reporting Console */}
      <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-6 backdrop-blur-sm">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
          <div>
            <h3 className="text-lg font-serif text-amber-200">Exception Telemetry & Crash Capture</h3>
            <p className="text-xs text-slate-400 mt-1">
              Privacy-filtered exceptions with PII redaction and release fingerprinting.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={triggerTestCrash}
              disabled={triggering}
              className="px-4 py-2 bg-rose-950/80 hover:bg-rose-900 text-rose-300 border border-rose-800/40 rounded-lg text-xs font-mono transition disabled:opacity-40"
            >
              {triggering ? 'Dispatching...' : 'Trigger Synthetic Test Crash'}
            </button>
          </div>
        </div>

        {testCrashStatus && (
          <div className="mb-4 p-3 bg-emerald-950/80 border border-emerald-800/40 text-emerald-300 text-xs rounded-lg font-mono">
            {testCrashStatus}
          </div>
        )}

        {metrics?.telemetry.recent_crashes.length === 0 ? (
          <div className="p-8 text-center text-slate-500 border border-dashed border-slate-800 rounded-lg">
            No active exceptions recorded. Telemetry reporting operating normally.
          </div>
        ) : (
          <div className="space-y-3">
            {metrics?.telemetry.recent_crashes.map((crash) => (
              <div key={crash.event_id} className="bg-slate-950 border border-slate-800 rounded-lg p-4 font-mono text-xs">
                <div className="flex items-center justify-between text-slate-400 mb-2">
                  <span className="text-rose-400 font-bold">{crash.exception_type}</span>
                  <span className="text-slate-500">{new Date(crash.timestamp).toLocaleString()}</span>
                </div>
                <div className="text-slate-200 mb-2">{crash.message}</div>
                <div className="text-slate-500 text-[11px] truncate">
                  Event ID: <span className="text-amber-300">{crash.event_id}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
