import React, { useEffect, useState } from 'react';
import { AdminLuxuryFlowConsole } from './components/AdminLuxuryFlowConsole';
import { AdminSignIn } from './components/AdminSignIn';
import { requestApi } from './services/api';

export const App: React.FC = () => {
  const [authenticated, setAuthenticated] = useState(false);
  const [adminUser, setAdminUser] = useState<any>(null);
  const [showSignInModal, setShowSignInModal] = useState(false);
  const [viewMode, setViewMode] = useState<'console' | 'showcase'>('console');

  useEffect(() => {
    if (!localStorage.getItem('admin_token')) return;
    requestApi<any>('/auth/me')
      .then(user => {
        if (user && user.role === 'admin' && user.is_active) {
          setAdminUser(user);
          setAuthenticated(true);
        } else {
          localStorage.removeItem('admin_token');
          localStorage.removeItem('admin_refresh_token');
        }
      })
      .catch(() => {
        localStorage.removeItem('admin_token');
        localStorage.removeItem('admin_refresh_token');
      });
  }, []);

  const handleSignOut = () => {
    void requestApi('/auth/logout', { method: 'POST' })
      .catch(() => undefined)
      .finally(() => {
        localStorage.removeItem('admin_token');
        localStorage.removeItem('admin_refresh_token');
        setAuthenticated(false);
        setAdminUser(null);
      });
  };

  return (
    <div style={{ minHeight: '100vh', background: '#07070a', color: '#f1f5f9', fontFamily: "'Plus Jakarta Sans', sans-serif" }}>
      {/* GLOBAL MASTER SYSTEM BAR: Direct Toggle Between Live Operations & Master 65 Screens Catalog */}
      <div style={{
        background: '#0e0e14',
        borderBottom: '1px solid #1e293b',
        padding: '6px 20px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: 10,
        fontSize: 12
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span style={{ fontWeight: 800, color: '#f59e0b', display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
            <span>🚖</span> E-RICKSHAW PLATFORM MASTER
          </span>
          <span style={{ color: '#334155' }}>|</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <button
              onClick={() => setViewMode('console')}
              style={{
                background: viewMode === 'console' ? 'linear-gradient(135deg, #f59e0b, #d97706)' : '#1e293b',
                color: viewMode === 'console' ? '#000000' : '#cbd5e1',
                border: 'none',
                borderRadius: 8,
                padding: '5px 12px',
                fontWeight: 800,
                fontSize: 11,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 5,
                transition: 'all 0.15s ease'
              }}
            >
              <span>🖥️</span> Live Operations (15 Desks)
            </button>
            <button
              onClick={() => setViewMode('showcase')}
              style={{
                background: viewMode === 'showcase' ? 'linear-gradient(135deg, #f59e0b, #d97706)' : '#1e293b',
                color: viewMode === 'showcase' ? '#000000' : '#cbd5e1',
                border: 'none',
                borderRadius: 8,
                padding: '5px 12px',
                fontWeight: 800,
                fontSize: 11,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 5,
                transition: 'all 0.15s ease'
              }}
            >
              <span>📱</span> 65 Screens Master Catalog (25 Cust + 25 Driver + 15 Admin)
            </button>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <span style={{ color: '#10b981', fontWeight: 700, display: 'flex', alignItems: 'center', gap: 6, fontSize: 11 }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#10b981', boxShadow: '0 0 8px #10b981', display: 'inline-block' }} />
            PostgreSQL Live Connected
          </span>
          <a
            href="/showcase.html"
            target="_blank"
            rel="noopener noreferrer"
            style={{
              color: '#38bdf8',
              textDecoration: 'none',
              fontSize: 11,
              fontWeight: 700,
              background: '#0369a122',
              border: '1px solid #0284c755',
              padding: '3px 10px',
              borderRadius: 6
            }}
          >
            Standalone Showcase ↗
          </a>
        </div>
      </div>

      {viewMode === 'console' ? (
        <AdminLuxuryFlowConsole
          adminUser={authenticated ? adminUser : null}
          onSignOut={handleSignOut}
          onOpenSignIn={() => setShowSignInModal(true)}
        />
      ) : (
        <iframe
          src="/showcase.html"
          title="E-Rickshaw 65 Screens Master Showcase"
          style={{
            width: '100%',
            height: 'calc(100vh - 46px)',
            border: 'none',
            background: '#07070a'
          }}
        />
      )}

      {showSignInModal && (
        <div style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.85)',
          backdropFilter: 'blur(8px)',
          zIndex: 10000,
          display: 'grid',
          placeItems: 'center',
          padding: 16
        }}>
          <div style={{ position: 'relative', width: '100%', maxWidth: 440 }}>
            <button
              onClick={() => setShowSignInModal(false)}
              style={{
                position: 'absolute',
                top: 14,
                right: 14,
                background: '#222',
                border: '1px solid #444',
                color: '#fff',
                borderRadius: '50%',
                width: 30,
                height: 30,
                cursor: 'pointer',
                zIndex: 10
              }}
            >
              ✕
            </button>
            <AdminSignIn
              onAuthenticated={user => {
                setAdminUser(user);
                setAuthenticated(true);
                setShowSignInModal(false);
              }}
            />
          </div>
        </div>
      )}
    </div>
  );
};

export default App;
