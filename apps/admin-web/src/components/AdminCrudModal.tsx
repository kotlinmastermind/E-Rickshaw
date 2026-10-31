import React, { useState, useEffect } from 'react';
import {
  X, RefreshCw, Plus, Edit2, Trash2, Search, CheckCircle2,
  AlertTriangle, Database, ShieldAlert
} from 'lucide-react';
import { ScreenItem, ADMIN_CRUD_MAPPING } from '../data/screensData';
import { requestApi } from '../services/api';

interface AdminCrudModalProps {
  screen: ScreenItem;
  onClose: () => void;
  onAuditAction?: (action: string, entity: string, reason: string) => void;
}

export const AdminCrudModal: React.FC<AdminCrudModalProps> = ({ screen, onClose, onAuditAction }) => {
  const mapping = ADMIN_CRUD_MAPPING[screen.id] || {
    table: 'admin_operations',
    endpoint: '/admin/stats/overview',
    actions: ['read'],
    description: screen.title
  };

  const [activeTab, setActiveTab] = useState<'read' | 'create' | 'update' | 'delete'>('read');
  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedRecord, setSelectedRecord] = useState<any | null>(null);

  // Form State for CREATE
  const [formData, setFormData] = useState<Record<string, any>>({});
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Reason for destructive action
  const [auditReason, setAuditReason] = useState('');

  // Fetch records
  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      let endpoint = mapping.endpoint;
      // Adjust endpoint if parameterized
      if (endpoint.includes('{id}')) {
        endpoint = endpoint.replace('/{id}', '');
      }

      const res = await requestApi<any>(endpoint);
      if (Array.isArray(res)) {
        setData(res);
      } else if (res && typeof res === 'object') {
        const arrayKey = Object.keys(res).find(k => Array.isArray(res[k]));
        if (arrayKey) {
          setData(res[arrayKey]);
        } else {
          setData([res]);
        }
      } else {
        setData([]);
      }
    } catch (err: any) {
      console.warn('CRUD Fetch warning:', err);
      setError(err.message || 'Failed to fetch live data from backend.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [screen.id]);

  // Handle Create
  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setFeedback(null);
    try {
      let endpoint = mapping.endpoint;
      if (screen.id === 16) endpoint = '/vehicles/categories';
      else if (screen.id === 18) endpoint = '/admin/fares';
      else if (screen.id === 19) endpoint = '/admin/surge/rules';
      else if (screen.id === 20) endpoint = '/admin/geofences';
      else if (screen.id === 21) endpoint = '/location/road-restrictions';
      else if (screen.id === 28) endpoint = '/admin/promos';
      else if (screen.id === 29) endpoint = '/admin/broadcasts';

      await requestApi(endpoint, {
        method: 'POST',
        body: JSON.stringify(formData)
      });

      setFeedback({ type: 'success', message: `Record successfully created in ${mapping.table}!` });
      setFormData({});
      fetchData();
      if (onAuditAction) {
        onAuditAction('CREATE', mapping.table, `Created record in ${mapping.table}`);
      }
    } catch (err: any) {
      setFeedback({ type: 'error', message: err.message || 'Creation failed' });
    } finally {
      setSubmitting(false);
    }
  };

  // Handle Delete with Audit Reason
  const handleDelete = async (recordId: string) => {
    if (!auditReason || auditReason.trim().length < 5) {
      alert('Mandatory DPDP requirement: Please enter an audit reason (at least 5 chars) before deleting.');
      return;
    }
    setSubmitting(true);
    try {
      let endpoint = `${mapping.endpoint}/${recordId}`;
      if (screen.id === 16) endpoint = `/vehicles/categories/${recordId}`;
      else if (screen.id === 21) endpoint = `/location/road-restrictions/${recordId}`;
      else if (screen.id === 28) endpoint = `/admin/promos/${recordId}`;

      await requestApi(endpoint, { method: 'DELETE' });
      setFeedback({ type: 'success', message: `Record deleted from ${mapping.table}. Audit logged.` });
      setAuditReason('');
      fetchData();
      if (onAuditAction) {
        onAuditAction('DELETE', mapping.table, auditReason);
      }
    } catch (err: any) {
      setFeedback({ type: 'error', message: err.message || 'Delete operation failed' });
    } finally {
      setSubmitting(false);
    }
  };

  // Filtered records
  const filteredData = data.filter(item => {
    if (!searchTerm) return true;
    const str = JSON.stringify(item).toLowerCase();
    return str.includes(searchTerm.toLowerCase());
  });

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(0,0,0,0.85)',
      backdropFilter: 'blur(8px)',
      zIndex: 100,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '16px'
    }}>
      <div style={{
        background: '#121216',
        border: '1px solid #d4af37',
        borderRadius: 16,
        width: '100%',
        maxWidth: 960,
        maxHeight: '90vh',
        display: 'flex',
        flexDirection: 'column',
        boxShadow: '0 20px 60px rgba(0,0,0,0.9), 0 0 25px rgba(212,175,55,0.2)',
        overflow: 'hidden'
      }}>
        {/* Header */}
        <div style={{
          padding: '16px 20px',
          borderBottom: '1px solid #2a2a2e',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          background: '#16161a'
        }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span style={{
                background: 'rgba(212,175,55,0.15)',
                color: '#d4af37',
                border: '1px solid #d4af37',
                padding: '2px 8px',
                borderRadius: 12,
                fontSize: 11,
                fontWeight: 700
              }}>
                SCREEN {String(screen.id).padStart(2, '0')}
              </span>
              <h2 style={{ margin: 0, color: '#f2ead3', fontSize: 18, fontWeight: 600 }}>
                {screen.title}
              </h2>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 4, fontSize: 12, color: '#8d8672' }}>
              <span>Target Table: <b style={{ color: '#d4af37' }}>{mapping.table}</b></span>
              <span>•</span>
              <span>Endpoint: <code style={{ color: '#aaa', background: '#0b0b0d', padding: '1px 6px', borderRadius: 4 }}>{mapping.endpoint}</code></span>
            </div>
          </div>
          <button
            onClick={onClose}
            style={{
              background: '#222226',
              border: '1px solid #333',
              color: '#f2ead3',
              borderRadius: '50%',
              width: 32,
              height: 32,
              display: 'grid',
              placeItems: 'center',
              cursor: 'pointer'
            }}
          >
            <X size={16} />
          </button>
        </div>

        {/* Tab Controls */}
        <div style={{
          display: 'flex',
          borderBottom: '1px solid #2a2a2e',
          background: '#0b0b0d',
          padding: '0 16px',
          gap: 8,
          overflowX: 'auto'
        }}>
          <button
            onClick={() => setActiveTab('read')}
            style={{
              padding: '12px 18px',
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'read' ? '2px solid #d4af37' : '2px solid transparent',
              color: activeTab === 'read' ? '#d4af37' : '#8d8672',
              fontWeight: activeTab === 'read' ? 700 : 500,
              fontSize: 13,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 6
            }}
          >
            <Database size={14} /> READ & INSPECT ({data.length})
          </button>

          {mapping.actions.includes('create') && (
            <button
              onClick={() => setActiveTab('create')}
              style={{
                padding: '12px 18px',
                background: 'transparent',
                border: 'none',
                borderBottom: activeTab === 'create' ? '2px solid #d4af37' : '2px solid transparent',
                color: activeTab === 'create' ? '#d4af37' : '#8d8672',
                fontWeight: activeTab === 'create' ? 700 : 500,
                fontSize: 13,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 6
              }}
            >
              <Plus size={14} /> CREATE NEW
            </button>
          )}

          {mapping.actions.includes('update') && (
            <button
              onClick={() => setActiveTab('update')}
              style={{
                padding: '12px 18px',
                background: 'transparent',
                border: 'none',
                borderBottom: activeTab === 'update' ? '2px solid #d4af37' : '2px solid transparent',
                color: activeTab === 'update' ? '#d4af37' : '#8d8672',
                fontWeight: activeTab === 'update' ? 700 : 500,
                fontSize: 13,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 6
              }}
            >
              <Edit2 size={14} /> UPDATE STATUS
            </button>
          )}

          {mapping.actions.includes('delete') && (
            <button
              onClick={() => setActiveTab('delete')}
              style={{
                padding: '12px 18px',
                background: 'transparent',
                border: 'none',
                borderBottom: activeTab === 'delete' ? '2px solid #ef4444' : '2px solid transparent',
                color: activeTab === 'delete' ? '#ef4444' : '#8d8672',
                fontWeight: activeTab === 'delete' ? 700 : 500,
                fontSize: 13,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 6
              }}
            >
              <Trash2 size={14} /> DELETE / ARCHIVE
            </button>
          )}
        </div>

        {/* Feedback message banner */}
        {feedback && (
          <div style={{
            padding: '10px 16px',
            background: feedback.type === 'success' ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)',
            borderBottom: `1px solid ${feedback.type === 'success' ? '#10b981' : '#ef4444'}`,
            color: feedback.type === 'success' ? '#10b981' : '#ef4444',
            fontSize: 12,
            display: 'flex',
            alignItems: 'center',
            gap: 8
          }}>
            {feedback.type === 'success' ? <CheckCircle2 size={15} /> : <AlertTriangle size={15} />}
            {feedback.message}
          </div>
        )}

        {/* Content Body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px', background: '#0b0b0d' }}>
          {/* TAB 1: READ / INSPECT */}
          {activeTab === 'read' && (
            <div>
              <div style={{ display: 'flex', gap: 12, marginBottom: 14 }}>
                <div style={{ position: 'relative', flex: 1 }}>
                  <Search size={14} style={{ position: 'absolute', left: 10, top: 11, color: '#8d8672' }} />
                  <input
                    type="text"
                    placeholder={`Search within ${mapping.table}...`}
                    value={searchTerm}
                    onChange={e => setSearchTerm(e.target.value)}
                    style={{
                      width: '100%',
                      padding: '8px 12px 8px 32px',
                      background: '#16161a',
                      border: '1px solid #2a2a2e',
                      borderRadius: 8,
                      color: '#f2ead3',
                      fontSize: 12
                    }}
                  />
                </div>
                <button
                  onClick={fetchData}
                  disabled={loading}
                  style={{
                    background: '#16161a',
                    border: '1px solid #333',
                    color: '#d4af37',
                    padding: '8px 14px',
                    borderRadius: 8,
                    fontSize: 12,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6
                  }}
                >
                  <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
                  Refresh
                </button>
              </div>

              {loading ? (
                <div style={{ textAlign: 'center', padding: 40, color: '#d4af37' }}>
                  <RefreshCw size={24} style={{ animation: 'spin 1s linear infinite' }} />
                  <p style={{ marginTop: 10, fontSize: 13 }}>Querying live {mapping.table} from backend...</p>
                </div>
              ) : error ? (
                <div style={{ padding: 20, background: '#1c1515', border: '1px solid #4a2020', borderRadius: 8, color: '#ff8888', fontSize: 12 }}>
                  <AlertTriangle size={18} style={{ marginBottom: 6 }} />
                  <p><strong>Database/API Notice:</strong> {error}</p>
                </div>
              ) : filteredData.length === 0 ? (
                <div style={{ textAlign: 'center', padding: 40, color: '#8d8672', fontSize: 13 }}>
                  No active records found in <code>{mapping.table}</code>.
                  {mapping.actions.includes('create') && (
                    <div style={{ marginTop: 12 }}>
                      <button
                        onClick={() => setActiveTab('create')}
                        style={{
                          background: 'linear-gradient(90deg, #b8962e, #d4af37)',
                          border: 'none',
                          color: '#0b0b0d',
                          padding: '8px 16px',
                          borderRadius: 8,
                          fontWeight: 700,
                          fontSize: 12,
                          cursor: 'pointer'
                        }}
                      >
                        Create First Record
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {filteredData.map((item, idx) => (
                    <div
                      key={item.id || idx}
                      onClick={() => setSelectedRecord(selectedRecord?.id === item.id ? null : item)}
                      style={{
                        background: '#16161a',
                        border: selectedRecord?.id === item.id ? '1px solid #d4af37' : '1px solid #2a2a2e',
                        borderRadius: 10,
                        padding: '12px 14px',
                        cursor: 'pointer',
                        transition: 'border-color 0.2s ease'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span style={{ color: '#d4af37', fontWeight: 700, fontSize: 13 }}>
                            {item.title || item.name || item.code || item.phone_number || item.email || item.ticket_number || `Record #${idx + 1}`}
                          </span>
                          {item.id && (
                            <span style={{ fontSize: 10, color: '#8d8672', background: '#0b0b0d', padding: '1px 6px', borderRadius: 4 }}>
                              ID: {String(item.id).slice(0, 8)}...
                            </span>
                          )}
                        </div>
                        <span style={{
                          fontSize: 11,
                          padding: '2px 8px',
                          borderRadius: 10,
                          background: item.is_active !== false && item.status !== 'CANCELLED' ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)',
                          color: item.is_active !== false && item.status !== 'CANCELLED' ? '#10b981' : '#ef4444'
                        }}>
                          {item.status || (item.is_active !== false ? 'ACTIVE' : 'INACTIVE')}
                        </span>
                      </div>

                      {/* Summary elements */}
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 6, fontSize: 11, color: '#aaa' }}>
                        {Object.entries(item).slice(0, 4).map(([k, v]) => (
                          <div key={k}>
                            <span style={{ color: '#8d8672' }}>{k}: </span>
                            <span style={{ color: '#f2ead3' }}>{typeof v === 'object' ? JSON.stringify(v) : String(v)}</span>
                          </div>
                        ))}
                      </div>

                      {/* Expandable JSON detail */}
                      {selectedRecord?.id === item.id && (
                        <div style={{ marginTop: 12, paddingTop: 10, borderTop: '1px solid #2a2a2e' }}>
                          <div style={{ fontSize: 11, color: '#d4af37', fontWeight: 600, marginBottom: 4 }}>FULL RECORD PAYLOAD:</div>
                          <pre style={{
                            background: '#09090b',
                            padding: 10,
                            borderRadius: 6,
                            color: '#e2d7ba',
                            fontSize: 11,
                            overflowX: 'auto',
                            maxHeight: 200
                          }}>
                            {JSON.stringify(item, null, 2)}
                          </pre>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 2: CREATE RECORD */}
          {activeTab === 'create' && (
            <form onSubmit={handleCreateSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div style={{ padding: '8px 12px', background: '#16161a', borderRadius: 8, border: '1px solid #2a2a2e', fontSize: 12, color: '#8d8672' }}>
                Fill in the elements below to insert a validated row into <b style={{ color: '#d4af37' }}>{mapping.table}</b>.
              </div>

              {screen.elements.map(el => {
                const key = el.toLowerCase().replace(/[^a-z0-9]/g, '_');
                return (
                  <div key={el} style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <label style={{ fontSize: 12, color: '#d4af37', fontWeight: 500 }}>{el}</label>
                    <input
                      type="text"
                      placeholder={`Enter ${el}...`}
                      value={formData[key] || ''}
                      onChange={e => setFormData({ ...formData, [key]: e.target.value })}
                      style={{
                        padding: '9px 12px',
                        background: '#16161a',
                        border: '1px solid #333',
                        borderRadius: 8,
                        color: '#f2ead3',
                        fontSize: 12
                      }}
                      required={el.includes('Code') || el.includes('Name') || el.includes('Base') || el.includes('Title')}
                    />
                  </div>
                );
              })}

              <button
                type="submit"
                disabled={submitting}
                style={{
                  background: 'linear-gradient(90deg, #b8962e, #d4af37)',
                  color: '#0b0b0d',
                  border: 'none',
                  padding: '12px',
                  borderRadius: 8,
                  fontWeight: 700,
                  fontSize: 13,
                  cursor: 'pointer',
                  marginTop: 10
                }}
              >
                {submitting ? 'Submitting to Backend...' : `Save to ${mapping.table}`}
              </button>
            </form>
          )}

          {/* TAB 3: UPDATE STATUS */}
          {activeTab === 'update' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div style={{ padding: '8px 12px', background: '#16161a', borderRadius: 8, border: '1px solid #2a2a2e', fontSize: 12, color: '#8d8672' }}>
                Select a live record below to toggle status, resolve tickets, or approve onboarding documents.
              </div>

              {filteredData.slice(0, 10).map(item => (
                <div key={item.id} style={{
                  background: '#16161a',
                  border: '1px solid #2a2a2e',
                  borderRadius: 10,
                  padding: '12px 14px',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}>
                  <div>
                    <div style={{ color: '#d4af37', fontWeight: 600, fontSize: 13 }}>
                      {item.title || item.name || item.code || item.email || item.ticket_number || `Record #${item.id?.slice(0, 8)}`}
                    </div>
                    <div style={{ fontSize: 11, color: '#8d8672' }}>
                      Current Status: <b style={{ color: '#fff' }}>{item.status || (item.is_active ? 'ACTIVE' : 'INACTIVE')}</b>
                    </div>
                  </div>
                  <button
                    onClick={async () => {
                      setSubmitting(true);
                      try {
                        const newStatus = item.is_active === false ? true : false;
                        await requestApi(`${mapping.endpoint}/${item.id}`, {
                          method: 'PUT',
                          body: JSON.stringify({ is_active: newStatus, status: 'UPDATED' })
                        });
                        setFeedback({ type: 'success', message: 'Record status updated!' });
                        fetchData();
                      } catch (err: any) {
                        setFeedback({ type: 'error', message: err.message || 'Status update failed' });
                      } finally {
                        setSubmitting(false);
                      }
                    }}
                    style={{
                      background: '#222226',
                      border: '1px solid #d4af37',
                      color: '#d4af37',
                      padding: '6px 14px',
                      borderRadius: 6,
                      fontSize: 11,
                      cursor: 'pointer',
                      fontWeight: 600
                    }}
                  >
                    Toggle Active
                  </button>
                </div>
              ))}
            </div>
          )}

          {/* TAB 4: DELETE / ARCHIVE */}
          {activeTab === 'delete' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div style={{
                padding: '12px 14px',
                background: 'rgba(239,68,68,0.1)',
                border: '1px solid rgba(239,68,68,0.3)',
                borderRadius: 8,
                color: '#ffaaaa',
                fontSize: 12
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700, marginBottom: 4 }}>
                  <ShieldAlert size={16} /> Strict Navigation Rule 3: Destructive Operation Safeguard
                </div>
                Every deletion or deactivation requires an explicit audit justification note. It is permanently written to <code>audit_logs</code>.
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <label style={{ fontSize: 12, color: '#ef4444', fontWeight: 600 }}>Mandatory Audit Reason:</label>
                <input
                  type="text"
                  placeholder="e.g. Regulatory compliance purge, incorrect boundary coordinate..."
                  value={auditReason}
                  onChange={e => setAuditReason(e.target.value)}
                  style={{
                    padding: '9px 12px',
                    background: '#16161a',
                    border: '1px solid #ef4444',
                    borderRadius: 8,
                    color: '#f2ead3',
                    fontSize: 12
                  }}
                />
              </div>

              <div style={{ marginTop: 10, display: 'flex', flexDirection: 'column', gap: 8 }}>
                {filteredData.slice(0, 8).map(item => (
                  <div key={item.id} style={{
                    background: '#16161a',
                    border: '1px solid #2a2a2e',
                    borderRadius: 8,
                    padding: '10px 14px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center'
                  }}>
                    <div>
                      <div style={{ color: '#f2ead3', fontSize: 12, fontWeight: 600 }}>
                        {item.title || item.name || item.code || item.id}
                      </div>
                      <div style={{ fontSize: 10, color: '#8d8672' }}>ID: {item.id}</div>
                    </div>
                    <button
                      onClick={() => handleDelete(item.id)}
                      disabled={submitting}
                      style={{
                        background: 'rgba(239,68,68,0.2)',
                        border: '1px solid #ef4444',
                        color: '#ef4444',
                        padding: '6px 12px',
                        borderRadius: 6,
                        fontSize: 11,
                        cursor: 'pointer',
                        fontWeight: 700
                      }}
                    >
                      Delete Row
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div style={{
          padding: '12px 20px',
          borderTop: '1px solid #2a2a2e',
          background: '#16161a',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          fontSize: 11,
          color: '#8d8672'
        }}>
          <div>Connected to Supabase Live Database • 68 Core Active Tables</div>
          <button
            onClick={onClose}
            style={{
              background: '#222226',
              border: '1px solid #444',
              color: '#f2ead3',
              padding: '6px 16px',
              borderRadius: 6,
              fontSize: 12,
              cursor: 'pointer'
            }}
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
