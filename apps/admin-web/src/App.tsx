import React, { useState, useEffect, useRef, useCallback } from 'react';
// Lazy-load Mission Control desks so initial login shell loads immediately
const VaisentraMissionControl = React.lazy(() => import('./components/VaisentraMissionControl').then(m => ({ default: m.VaisentraMissionControl })));
import { AdminSignIn } from './components/AdminSignIn';
import { requestApi } from './services/api';

const INACTIVITY_TIMEOUT_MS = 15 * 60 * 1000; // 15 minutes session inactivity timeout

export const App: React.FC = () => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(() => {
    return !!localStorage.getItem('admin_token');
  });
  const [isValidating, setIsValidating] = useState<boolean>(true);
  const [sessionTimeoutMessage, setSessionTimeoutMessage] = useState<string | null>(null);
  const lastActivityRef = useRef<number>(Date.now());

  // Task 084: Inactivity monitor & automatic session sign-out
  const handleUserActivity = useCallback(() => {
    lastActivityRef.current = Date.now();
  }, []);

  useEffect(() => {
    if (!isAuthenticated) return;

    const activityEvents = ['mousedown', 'mousemove', 'keydown', 'scroll', 'touchstart', 'click'];
    activityEvents.forEach(evt => window.addEventListener(evt, handleUserActivity, { passive: true }));

    const intervalId = setInterval(() => {
      if (Date.now() - lastActivityRef.current > INACTIVITY_TIMEOUT_MS) {
        localStorage.removeItem('admin_token');
        setIsAuthenticated(false);
        setSessionTimeoutMessage('Your administrative session has timed out due to 15 minutes of inactivity.');
      }
    }, 10000);

    return () => {
      activityEvents.forEach(evt => window.removeEventListener(evt, handleUserActivity));
      clearInterval(intervalId);
    };
  }, [isAuthenticated, handleUserActivity]);

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
      {sessionTimeoutMessage && !isAuthenticated && (
        <div style={{ background: '#7f1d1d', color: '#fecaca', padding: '12px 24px', textAlign: 'center', fontSize: 13, borderBottom: '1px solid #991b1b' }}>
          ⚠️ {sessionTimeoutMessage}
        </div>
      )}
      {isAuthenticated ? (
        <React.Suspense fallback={
          <div style={{ minHeight: '100vh', background: '#07070a', color: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ width: 40, height: 40, border: '3px solid #1e293b', borderTopColor: '#d4af37', borderRadius: '50%', animation: 'spin 1s linear infinite', margin: '0 auto 16px' }} />
              <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
              <div style={{ color: '#94a3b8', fontSize: 14 }}>Initializing Mission Control Desks...</div>
            </div>
          </div>
        }>
          <VaisentraMissionControl />
        </React.Suspense>
      ) : (
        <AdminSignIn onAuthenticated={() => {
          setSessionTimeoutMessage(null);
          lastActivityRef.current = Date.now();
          setIsAuthenticated(true);
        }} />
      )}
    </div>
  );
};

export default App;
