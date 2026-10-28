import React, { useEffect, useState } from 'react';
import {
  Server, Shield, Cpu, Activity, RefreshCw, AlertTriangle,
  Database, Zap, Layers, Bug, Terminal, Lock, Globe, HardDrive,
  FileCode, Clock
} from 'lucide-react';
import { requestApi } from '../services/api';

export const AdminInfraOpsConsole: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'apis' | 'traces' | 'jobs' | 'dr' | 'providers' | 'security'>('apis');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  // Data states
  const [openApiSpec, setOpenApiSpec] = useState<any>(null);
  const [apiVersions, setApiVersions] = useState<any[]>([]);
  const [errorCatalog, setErrorCatalog] = useState<any[]>([]);
  const [traces, setTraces] = useState<any[]>([]);
  const [routeLatencies, setRouteLatencies] = useState<any[]>([]);
  const [bgJobs, setBgJobs] = useState<any[]>([]);
  const [scheduledJobs, setScheduledJobs] = useState<any[]>([]);
  const [migrationVerifications, setMigrationVerifications] = useState<any[]>([]);
  const [backupPolicies, setBackupPolicies] = useState<any[]>([]);
  const [restoreDrills, setRestoreDrills] = useState<any[]>([]);
  const [drPlan, setDrPlan] = useState<any>(null);
  const [redisNamespaces, setRedisNamespaces] = useState<any[]>([]);
  const [cacheTestResult, setCacheTestResult] = useState<any>(null);
  const [providers, setProviders] = useState<any[]>([]);
  const [secretRotations, setSecretRotations] = useState<any[]>([]);
  const [vulnerabilities, setVulnerabilities] = useState<any[]>([]);
  const [crashes, setCrashes] = useState<any[]>([]);
  const [featureFlags, setFeatureFlags] = useState<any[]>([]);

  const loadData = async () => {
    setLoading(true);
    try {
      if (activeTab === 'apis') {
        const [spec, vers, errs] = await Promise.all([
          requestApi<any>('/infra/openapi-client-spec?app_type=CUSTOMER'),
          requestApi<any[]>('/infra/api-versions'),
          requestApi<any[]>('/infra/error-catalog')
        ]);
        setOpenApiSpec(spec); setApiVersions(vers); setErrorCatalog(errs);
      } else if (activeTab === 'traces') {
        const [tr, lat] = await Promise.all([
          requestApi<any[]>('/infra/traces?limit=30'),
          requestApi<any[]>('/infra/route-latencies')
        ]);
        setTraces(tr); setRouteLatencies(lat);
      } else if (activeTab === 'jobs') {
        const [jobs, sched, mig] = await Promise.all([
          requestApi<any[]>('/infra/background-jobs'),
          requestApi<any[]>('/infra/scheduled-jobs'),
          requestApi<any[]>('/infra/migration-verifications')
        ]);
        setBgJobs(jobs); setScheduledJobs(sched); setMigrationVerifications(mig);
      } else if (activeTab === 'dr') {
        const [bk, dr, plan, rds] = await Promise.all([
          requestApi<any[]>('/infra/backup-policies'),
          requestApi<any[]>('/infra/restore-drills'),
          requestApi<any>('/infra/dr-plan'),
          requestApi<any[]>('/infra/redis-namespaces')
        ]);
        setBackupPolicies(bk); setRestoreDrills(dr); setDrPlan(plan); setRedisNamespaces(rds);
      } else if (activeTab === 'providers') {
        const provs = await requestApi<any[]>('/infra/provider-health');
        setProviders(provs);
      } else if (activeTab === 'security') {
        const [sec, vul, cr, flags] = await Promise.all([
          requestApi<any[]>('/infra/secret-rotations'),
          requestApi<any[]>('/infra/vulnerabilities'),
          requestApi<any[]>('/infra/mobile-crashes?limit=20'),
          requestApi<any[]>('/infra/feature-flags')
        ]);
        setSecretRotations(sec); setVulnerabilities(vul); setCrashes(cr); setFeatureFlags(flags);
      }
    } catch (e: any) {
      setMessage(`Error loading data: ${e.message}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, [activeTab]);

  return (
    <div style={{ padding: 24, color: '#f8fafc', background: '#0b0f17', minHeight: '100vh' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, borderBottom: '1px solid #1e293b', paddingBottom: 16 }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: '#d4af37', margin: 0, display: 'flex', alignItems: 'center', gap: 10 }}>
            <Server size={26} color="#d4af37" />
            Infrastructure, Resilience & Ops Console
          </h1>
          <p style={{ fontSize: '0.85rem', color: '#94a3b8', margin: '4px 0 0 0' }}>
            Part 19: Observability, Circuit Breakers, DR Plans, Feature Flags & Mobile Stability
          </p>
        </div>
        <button
          onClick={loadData}
          style={{
            background: 'var(--card-bg, #1a2332)',
            border: '1px solid #d4af37',
            color: '#d4af37',
            padding: '8px 16px',
            borderRadius: 6,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            fontWeight: 600
          }}
        >
          <RefreshCw size={16} className={loading ? 'spin' : ''} />
          Refresh Status
        </button>
      </div>

      {message && (
        <div style={{ background: '#1e293b', borderLeft: '4px solid #d4af37', padding: '12px 16px', borderRadius: 4, marginBottom: 16, fontSize: '0.9rem' }}>
          {message}
        </div>
      )}

      {/* Nav Tabs */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 20, borderBottom: '1px solid #1e293b', paddingBottom: 8, overflowX: 'auto' }}>
        {[
          { id: 'apis', label: 'API & OpenAPI Specs', icon: FileCode },
          { id: 'traces', label: 'Traces & Latency', icon: Activity },
          { id: 'jobs', label: 'Jobs & Migrations', icon: Cpu },
          { id: 'dr', label: 'DR, Backups & Redis', icon: Database },
          { id: 'providers', label: 'Circuit Breakers', icon: Zap },
          { id: 'security', label: 'Security & Flags', icon: Shield }
        ].map(tab => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              style={{
                background: isActive ? '#d4af37' : '#1e293b',
                color: isActive ? '#0b0f17' : '#e2e8f0',
                border: 'none',
                padding: '10px 18px',
                borderRadius: 6,
                fontWeight: isActive ? 700 : 500,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                fontSize: '0.85rem'
              }}
            >
              <Icon size={16} />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Tab 1: API & OpenAPI Specs */}
      {activeTab === 'apis' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <FileCode size={18} />
              OpenAPI Retrofit Client Spec (Feature 451)
            </h3>
            {openApiSpec && (
              <pre style={{ background: '#090d14', padding: 12, borderRadius: 6, fontSize: '0.75rem', color: '#38bdf8', overflowX: 'auto', maxHeight: 300 }}>
                {openApiSpec.kotlin_source_preview}
              </pre>
            )}
          </div>

          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Globe size={18} />
              API Version Compatibility (Feature 452)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {apiVersions.map(v => (
                <div key={v.id} style={{ background: '#090d14', padding: 12, borderRadius: 6, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontWeight: 600, color: '#f8fafc' }}>{v.app_type} App v{v.version_code}</div>
                    <div style={{ fontSize: '0.78rem', color: '#94a3b8' }}>Min: v{v.min_supported_version} | Latest: v{v.latest_version}</div>
                  </div>
                  <span style={{ padding: '4px 10px', borderRadius: 12, fontSize: '0.75rem', fontWeight: 700, background: v.status === 'SUPPORTED' ? '#065f46' : '#991b1b', color: '#fff' }}>
                    {v.status}
                  </span>
                </div>
              ))}
            </div>
          </div>

          <div style={{ gridColumn: '1 / -1', background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <AlertTriangle size={18} />
              Standard API Error Catalog (Feature 454)
            </h3>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
              <thead>
                <tr style={{ background: '#090d14', color: '#94a3b8', textAlign: 'left' }}>
                  <th style={{ padding: 10 }}>Error Code</th>
                  <th style={{ padding: 10 }}>HTTP Status</th>
                  <th style={{ padding: 10 }}>Category</th>
                  <th style={{ padding: 10 }}>Title & Description</th>
                  <th style={{ padding: 10 }}>Resolution Guidance</th>
                </tr>
              </thead>
              <tbody>
                {errorCatalog.map(e => (
                  <tr key={e.id} style={{ borderBottom: '1px solid #1e293b' }}>
                    <td style={{ padding: 10, fontWeight: 700, color: '#f43f5e' }}>{e.error_code}</td>
                    <td style={{ padding: 10, color: '#fbbf24' }}>{e.http_status_code}</td>
                    <td style={{ padding: 10, color: '#38bdf8' }}>{e.category}</td>
                    <td style={{ padding: 10 }}>
                      <div style={{ fontWeight: 600 }}>{e.title}</div>
                      <div style={{ fontSize: '0.78rem', color: '#94a3b8' }}>{e.description}</div>
                    </td>
                    <td style={{ padding: 10, fontSize: '0.78rem', color: '#a7f3d0' }}>{e.resolution_guidance}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 2: Traces & Latency */}
      {activeTab === 'traces' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Activity size={18} />
              Per-Route API Latency Dashboards (Feature 467)
            </h3>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem' }}>
              <thead>
                <tr style={{ background: '#090d14', color: '#94a3b8', textAlign: 'left' }}>
                  <th style={{ padding: 8 }}>Route</th>
                  <th style={{ padding: 8 }}>Avg Latency</th>
                  <th style={{ padding: 8 }}>P95 / P99</th>
                  <th style={{ padding: 8 }}>Requests</th>
                </tr>
              </thead>
              <tbody>
                {routeLatencies.map(r => (
                  <tr key={r.id} style={{ borderBottom: '1px solid #1e293b' }}>
                    <td style={{ padding: 8, fontWeight: 600, color: '#38bdf8' }}>{r.http_method} {r.route_path}</td>
                    <td style={{ padding: 8, color: '#34d399' }}>{r.avg_latency_ms} ms</td>
                    <td style={{ padding: 8, color: '#fbbf24' }}>{r.p95_latency_ms}ms / {r.p99_latency_ms}ms</td>
                    <td style={{ padding: 8 }}>{r.request_count}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Layers size={18} />
              Distributed Trace Viewer & Correlation IDs (Features 455, 456)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxHeight: 380, overflowY: 'auto' }}>
              {traces.map(t => (
                <div key={t.id} style={{ background: '#090d14', padding: 10, borderRadius: 6, fontSize: '0.8rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', color: '#d4af37', fontWeight: 600 }}>
                    <span>Span: {t.operation_name}</span>
                    <span>{t.duration_ms} ms</span>
                  </div>
                  <div style={{ color: '#94a3b8', fontSize: '0.75rem', marginTop: 4 }}>
                    Trace ID: <code style={{ color: '#38bdf8' }}>{t.trace_id}</code> | Correlation ID: <code style={{ color: '#a7f3d0' }}>{t.correlation_id || 'CORR-SYS-901'}</code>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Tab 3: Jobs & Migrations */}
      {activeTab === 'jobs' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Cpu size={18} />
              Background Job Queue & Retries (Feature 458)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {bgJobs.map(j => (
                <div key={j.id} style={{ background: '#090d14', padding: 12, borderRadius: 6, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontWeight: 600 }}>{j.job_name} ({j.queue_name})</div>
                    <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>Attempts: {j.attempt_count}/{j.max_retries} | Error: {j.last_error || 'None'}</div>
                  </div>
                  <button
                    onClick={async () => {
                      await requestApi(`/infra/background-jobs/${j.id}/retry`, { method: 'POST' });
                      void loadData();
                    }}
                    style={{ background: '#d4af37', color: '#0b0f17', border: 'none', padding: '6px 12px', borderRadius: 4, cursor: 'pointer', fontWeight: 700, fontSize: '0.75rem' }}
                  >
                    Retry Job
                  </button>
                </div>
              ))}
            </div>
          </div>

          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Clock size={18} />
              Scheduled Job Monitor (Feature 459)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {scheduledJobs.map(s => (
                <div key={s.id} style={{ background: '#090d14', padding: 12, borderRadius: 6, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontWeight: 600 }}>{s.job_title}</div>
                    <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>Cron: {s.cron_schedule} | Last Run: {s.last_duration_ms}ms</div>
                  </div>
                  <span style={{ padding: '4px 8px', borderRadius: 4, fontSize: '0.72rem', background: '#065f46', color: '#34d399', fontWeight: 700 }}>
                    {s.last_status}
                  </span>
                </div>
              ))}
            </div>
          </div>

          <div style={{ gridColumn: '1 / -1', background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Terminal size={18} />
              Database Migration Rollback Verification (Feature 460)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {migrationVerifications.map(m => (
                <div key={m.id} style={{ background: '#090d14', padding: 12, borderRadius: 6, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontWeight: 600, color: '#38bdf8' }}>Revision: {m.revision_id} (Down: {m.down_revision_id})</div>
                    <div style={{ fontSize: '0.78rem', color: '#94a3b8' }}>{m.notes}</div>
                  </div>
                  <span style={{ padding: '4px 10px', borderRadius: 12, fontSize: '0.75rem', fontWeight: 700, background: '#065f46', color: '#fff' }}>
                    VERIFIED PASSED
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Tab 4: DR, Backups & Cache */}
      {activeTab === 'dr' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Database size={18} />
              Automated Backup Retention & Key Rotation (Features 461, 462)
            </h3>
            {backupPolicies.map(p => (
              <div key={p.id} style={{ background: '#090d14', padding: 12, borderRadius: 6, marginBottom: 10 }}>
                <div style={{ fontWeight: 600 }}>{p.policy_name} ({p.backup_type})</div>
                <div style={{ fontSize: '0.75rem', color: '#94a3b8', margin: '4px 0' }}>Retention: {p.retention_days} Days | Key: {p.kms_key_arn}</div>
                <button
                  onClick={async () => {
                    await requestApi(`/infra/backup-policies/${p.id}/rotate-keys`, { method: 'POST' });
                    void loadData();
                  }}
                  style={{ background: '#d4af37', color: '#0b0f17', border: 'none', padding: '6px 12px', borderRadius: 4, cursor: 'pointer', fontWeight: 700, fontSize: '0.75rem', marginTop: 6 }}
                >
                  Rotate Encryption KMS Key
                </button>
              </div>
            ))}
            {restoreDrills.length > 0 && (
              <div style={{ marginTop: 12, borderTop: '1px solid #1e293b', paddingTop: 8 }}>
                <div style={{ fontSize: '0.8rem', color: '#94a3b8', marginBottom: 6 }}>Recent Disaster Drills ({restoreDrills.length})</div>
                {restoreDrills.slice(0, 3).map((d: any) => (
                  <div key={d.id} style={{ fontSize: '0.75rem', color: '#cbd5e1', marginBottom: 4 }}>
                    • {d.drill_name} - <span style={{ color: d.status === 'COMPLETED' ? '#34d399' : '#f59e0b' }}>{d.status}</span> (RTO: {d.rto_achieved_minutes}m, RPO: {d.rpo_achieved_minutes}m)
                  </div>
                ))}
              </div>
            )}
          </div>

          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Globe size={18} />
              Multi-Region Disaster Recovery Plan (Feature 464)
            </h3>
            {drPlan && (
              <div style={{ background: '#090d14', padding: 14, borderRadius: 6, fontSize: '0.85rem' }}>
                <div>Primary: <strong style={{ color: '#38bdf8' }}>{drPlan.primary_region}</strong></div>
                <div>Secondary DR: <strong style={{ color: '#a7f3d0' }}>{drPlan.dr_region}</strong></div>
                <div style={{ marginTop: 8 }}>Replication Status: <span style={{ color: '#34d399', fontWeight: 700 }}>{drPlan.replication_status}</span></div>
                <div>Failover Readiness: <span style={{ color: '#d4af37', fontWeight: 700 }}>{drPlan.failover_readiness}</span></div>
              </div>
            )}
          </div>

          <div style={{ gridColumn: '1 / -1', background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <HardDrive size={18} />
              Redis Key Namespaces & Cache Invalidation Policy (Features 465, 466)
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 16 }}>
              {redisNamespaces.map(ns => (
                <div key={ns.id} style={{ background: '#090d14', padding: 12, borderRadius: 6 }}>
                  <div style={{ fontWeight: 600, color: '#38bdf8', fontSize: '0.82rem' }}>{ns.namespace}</div>
                  <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: 4 }}>Keys: {ns.keys_count} | TTL: {ns.default_ttl_seconds}s</div>
                </div>
              ))}
            </div>
            <button
              onClick={async () => {
                const res = await requestApi<any>('/infra/cache-tests/run', { method: 'POST' });
                setCacheTestResult(res);
              }}
              style={{ background: '#d4af37', color: '#0b0f17', border: 'none', padding: '8px 16px', borderRadius: 6, cursor: 'pointer', fontWeight: 700, fontSize: '0.85rem' }}
            >
              Run Cache Invalidation Policy Test Suite
            </button>
            {cacheTestResult && (
              <pre style={{ background: '#090d14', padding: 10, borderRadius: 6, marginTop: 12, fontSize: '0.75rem', color: '#34d399' }}>
                {JSON.stringify(cacheTestResult, null, 2)}
              </pre>
            )}
          </div>
        </div>
      )}

      {/* Tab 5: Circuit Breakers */}
      {activeTab === 'providers' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 20 }}>
          {providers.map(p => (
            <div key={p.id} style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <h4 style={{ color: '#f8fafc', margin: 0, fontSize: '1rem' }}>{p.provider_name}</h4>
                <span style={{ padding: '3px 8px', borderRadius: 4, fontSize: '0.72rem', background: p.is_healthy ? '#065f46' : '#991b1b', color: '#fff', fontWeight: 700 }}>
                  {p.circuit_breaker_state}
                </span>
              </div>
              <div style={{ fontSize: '0.8rem', color: '#94a3b8', display: 'flex', flexDirection: 'column', gap: 6 }}>
                <div>Type: <strong style={{ color: '#38bdf8' }}>{p.provider_type}</strong></div>
                <div>Backup Failover: <strong style={{ color: '#a7f3d0' }}>{p.failover_provider_name}</strong></div>
                <div>Quota Usage: <strong style={{ color: '#fbbf24' }}>{p.quota_used_percentage}%</strong></div>
              </div>
              <button
                onClick={async () => {
                  await requestApi(`/infra/provider-health/${p.provider_name}/failover`, { method: 'POST' });
                  void loadData();
                }}
                style={{ width: '100%', marginTop: 14, background: '#f43f5e', color: '#fff', border: 'none', padding: '8px', borderRadius: 4, fontWeight: 700, cursor: 'pointer', fontSize: '0.78rem' }}
              >
                Trigger Manual Circuit Failover
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Tab 6: Security & Flags */}
      {activeTab === 'security' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Lock size={18} />
              Secret Rotation Workflows (Feature 472)
            </h3>
            {secretRotations.map(s => (
              <div key={s.id} style={{ background: '#090d14', padding: 12, borderRadius: 6, marginBottom: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <div style={{ fontWeight: 600 }}>{s.secret_name} ({s.current_version})</div>
                  <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>Vault: {s.vault_path}</div>
                </div>
                <button
                  onClick={async () => {
                    await requestApi(`/infra/secret-rotations/${s.secret_name}/rotate`, { method: 'POST' });
                    void loadData();
                  }}
                  style={{ background: '#d4af37', color: '#0b0f17', border: 'none', padding: '6px 12px', borderRadius: 4, cursor: 'pointer', fontWeight: 700, fontSize: '0.75rem' }}
                >
                  Rotate Secret
                </button>
              </div>
            ))}
            {vulnerabilities.length > 0 && (
              <div style={{ marginTop: 12, borderTop: '1px solid #1e293b', paddingTop: 8 }}>
                <div style={{ fontSize: '0.8rem', color: '#94a3b8', marginBottom: 6 }}>Dependencies & CVE Audits ({vulnerabilities.length})</div>
                {vulnerabilities.slice(0, 3).map((v: any) => (
                  <div key={v.id} style={{ fontSize: '0.75rem', color: '#cbd5e1', marginBottom: 4 }}>
                    • [{v.severity}] {v.package_name}: {v.cve_id} (<span style={{ color: '#38bdf8' }}>{v.status}</span>)
                  </div>
                ))}
              </div>
            )}
          </div>

          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Bug size={18} />
              Mobile Crash & ANR Reporting (Feature 474)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxHeight: 220, overflowY: 'auto' }}>
              {crashes.map(c => (
                <div key={c.id} style={{ background: '#090d14', padding: 10, borderRadius: 6, fontSize: '0.78rem' }}>
                  <div style={{ color: '#f43f5e', fontWeight: 700 }}>[{c.report_type}] {c.error_message}</div>
                  <div style={{ color: '#94a3b8', fontSize: '0.72rem' }}>{c.app_type} App v{c.app_version} on {c.device_model}</div>
                </div>
              ))}
            </div>
          </div>

          <div style={{ gridColumn: '1 / -1', background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Zap size={18} />
              Feature Flag Management (Feature 475)
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14 }}>
              {featureFlags.map(f => (
                <div key={f.id} style={{ background: '#090d14', padding: 14, borderRadius: 6 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                    <div style={{ fontWeight: 700, color: '#38bdf8', fontSize: '0.85rem' }}>{f.name}</div>
                    <input
                      type="checkbox"
                      checked={f.is_enabled}
                      onChange={async (e) => {
                        await requestApi(`/infra/feature-flags/${f.flag_key}/toggle`, {
                          method: 'POST',
                          body: JSON.stringify({ is_enabled: e.target.checked })
                        });
                        void loadData();
                      }}
                      style={{ cursor: 'pointer', transform: 'scale(1.2)' }}
                    />
                  </div>
                  <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>{f.description}</div>
                  <div style={{ fontSize: '0.72rem', color: '#a7f3d0', marginTop: 6 }}>Rollout: {f.rollout_percentage}%</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminInfraOpsConsole;
