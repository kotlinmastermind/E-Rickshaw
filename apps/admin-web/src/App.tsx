import React, { useState, useEffect } from 'react';
import { VaisentraMissionControl } from './components/VaisentraMissionControl';
import { AdminSignIn } from './components/AdminSignIn';
import { requestApi } from './services/api';

export const App: React.FC = () => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(() => {
    return !!localStorage.getItem('admin_token');
  });
  const [isValidating, setIsValidating] = useState<boolean>(true);

  useEffect(() => {
    const token = localStorage.getItem('admin_token');
    if (!token) {
      setIsAuthenticated(false);
      setIsValidating(false);
      return;
    }
    requestApi('/auth/me')
      .then((user) => {
        if (user && (user.role === 'admin' || user.role === 'superadmin')) {
          setIsAuthenticated(true);
        } else {
          localStorage.removeItem('admin_token');
          setIsAuthenticated(false);
        }
      })
      .catch(() => {
        localStorage.removeItem('admin_token');
        setIsAuthenticated(false);
      })
      .finally(() => {
        setIsValidating(false);
      });
  }, []);

  if (isValidating) {
    return (
      <div style={{ minHeight: '100vh', background: '#07070a', color: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ width: 40, height: 40, border: '3px solid #1e293b', borderTopColor: '#d4af37', borderRadius: '50%', animation: 'spin 1s linear infinite', margin: '0 auto 16px' }} />
          <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
          <div style={{ color: '#94a3b8', fontSize: 14 }}>Authenticating Mission Control...</div>
        </div>
      </div>
    );
  }

  return (
    <div style={{ minHeight: '100vh', background: '#07070a', color: '#f1f5f9', fontFamily: "'Plus Jakarta Sans', sans-serif" }}>
      {isAuthenticated ? (
        <VaisentraMissionControl />
      ) : (
        <AdminSignIn onAuthenticated={() => setIsAuthenticated(true)} />
      )}
    </div>
  );
};

export default App;
