import React, { useEffect, useState } from 'react';
import { AdminLuxuryFlowConsole } from './components/AdminLuxuryFlowConsole';
import { AdminSignIn } from './components/AdminSignIn';
import { requestApi } from './services/api';

export const App: React.FC = () => {
  const [authenticated, setAuthenticated] = useState(false);
  const [adminUser, setAdminUser] = useState<any>(null);
  const [showSignInModal, setShowSignInModal] = useState(false);

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
    <div style={{ minHeight: '100vh', background: '#0b0b0d', color: '#f2ead3' }}>
      <AdminLuxuryFlowConsole
        adminUser={authenticated ? adminUser : null}
        onSignOut={handleSignOut}
        onOpenSignIn={() => setShowSignInModal(true)}
      />

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
