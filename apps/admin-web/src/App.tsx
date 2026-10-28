import React, { useEffect, useState } from 'react';
import {
  LayoutDashboard, Navigation, Bell,
  FileText, ShieldAlert, Activity, Database,
  LifeBuoy, Radio, Zap, Map, Award, Sliders, Wallet, BarChart3, Cpu
} from 'lucide-react';
import { Dashboard } from './components/Dashboard';
import { SupportOperationsConsole } from './components/SupportOperationsConsole';
import { SupportTicketDesk } from './components/SupportTicketDesk';
import { CommunicationsConsole } from './components/CommunicationsConsole';
import { FinanceOperationsConsole } from './components/FinanceOperationsConsole';
import { SurgePricingConsole } from './components/SurgePricingConsole';
import { LocationOperationsConsole } from './components/LocationOperationsConsole';
import { AdminReportsConsole } from './components/AdminReportsConsole';
import { AdminAuditLogViewer } from './components/AdminAuditLogViewer';
import { AdminSystemHealth } from './components/AdminSystemHealth';
import { AdminDatabaseBackup } from './components/AdminDatabaseBackup';
import { SafetyAuditConsole } from './components/SafetyAuditConsole';
import { ReferralAndRecoveryConsole } from './components/ReferralAndRecoveryConsole';
import { FleetAccessibilityConsole } from './components/FleetAccessibilityConsole';
import { DriverOnboardingTicketDesk } from './components/DriverOnboardingTicketDesk';
import { SharedRideOperationalAnalytics } from './components/SharedRideOperationalAnalytics';
import { AdminGovernanceConsole } from './components/AdminGovernanceConsole';
import { DriverFinancialsAndPayoutConsole } from './components/DriverFinancialsAndPayoutConsole';
import { NotificationOperationsConsole } from './components/NotificationOperationsConsole';
import { AnalyticsAndGovernanceConsole } from './components/AnalyticsAndGovernanceConsole';
import { AdminInfraOpsConsole } from './components/AdminInfraOpsConsole';
import { AdminEnterpriseGrowthConsole } from './components/AdminEnterpriseGrowthConsole';
import { AdminSignIn } from './components/AdminSignIn';
import { requestApi } from './services/api';

type NavTab =
  | 'dashboard'
  | 'users'
  | 'drivers'
  | 'fleet-specs'
  | 'rides'
  | 'driver-financials'
  | 'finance-ops'
  | 'shared-ops'
  | 'fares'
  | 'safety'
  | 'loyalty-recovery'
  | 'support'
  | 'support-ops'
  | 'onboarding-support'
  | 'broadcasts'
  | 'surge'
  | 'heatmap'
  | 'reports'
  | 'audit'
  | 'health'
  | 'backups'
  | 'infra-ops'
  | 'enterprise-growth'
  | 'notifications'
  | 'analytics-bi';

export const App: React.FC = () => {
  const [authenticated, setAuthenticated] = useState(false);
  const [checking, setChecking] = useState(() => Boolean(localStorage.getItem('admin_token')));
  const [adminUser, setAdminUser] = useState<any>(null);
  useEffect(() => {
    if (!localStorage.getItem('admin_token')) return;
    requestApi<any>('/auth/me').then(user => {
      if (user.role === 'admin' && user.mfa_enabled === true && user.is_active) {
        setAdminUser(user); setAuthenticated(true);
      } else { localStorage.removeItem('admin_token'); localStorage.removeItem('admin_refresh_token'); }
    }).catch(() => { localStorage.removeItem('admin_token'); localStorage.removeItem('admin_refresh_token'); })
      .finally(() => setChecking(false));
  }, []);
  if (checking) return <main role="status" style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#101014', color: '#d4af37' }}>Verifying administrator session…</main>;
  if (!authenticated) return <AdminSignIn onAuthenticated={user => { setAdminUser(user); setAuthenticated(true); }} />;
  return <AdminConsole adminUser={adminUser} onSignOut={() => { void requestApi('/auth/logout', { method: 'POST' }).catch(() => undefined).finally(() => { localStorage.removeItem('admin_token'); localStorage.removeItem('admin_refresh_token'); setAuthenticated(false); setAdminUser(null); }); }} />;
};

const AdminConsole: React.FC<{ adminUser: any; onSignOut: () => void }> = ({ adminUser, onSignOut }) => {
  const [activeTab, setActiveTab] = useState<NavTab>('dashboard');

  return (
    <div className="luxury-app-container">
      {/* Sidebar Navigation */}
      <aside className="luxury-sidebar">
        <div className="brand-header">
          <div>
            <div className="brand-title">GRAND CHAUFFEUR</div>
            <div className="brand-subtitle">EXECUTIVE CONSOLE • V3.0</div>
          </div>
        </div>

        {/* Core Operations Section */}
        <div style={{ padding: '0 12px', fontSize: '0.68rem', letterSpacing: '0.08em', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: 6 }}>
          Operations & Fleet
        </div>
        <ul className="nav-menu" style={{ marginBottom: 16 }}>
          <li className={`nav-item ${activeTab === 'dashboard' ? 'active' : ''}`} onClick={() => setActiveTab('dashboard')}>
            <LayoutDashboard size={17} />
            <span>Overview</span>
          </li>
          <li className={`nav-item ${activeTab === 'rides' ? 'active' : ''}`} onClick={() => setActiveTab('rides')}>
            <Navigation size={17} />
            <span>Governance & Dispatch</span>
          </li>
          <li className={`nav-item ${activeTab === 'driver-financials' ? 'active' : ''}`} onClick={() => setActiveTab('driver-financials')}>
            <Wallet size={17} color={activeTab === 'driver-financials' ? '#d4af37' : undefined} />
            <span>Chauffeur Payouts & Welfare</span>
          </li>
          <li className={`nav-item ${activeTab === 'finance-ops' ? 'active' : ''}`} onClick={() => setActiveTab('finance-ops')}>
            <Wallet size={17} color={activeTab === 'finance-ops' ? '#d4af37' : undefined} />
            <span>Finance Controls</span>
          </li>
          <li className={`nav-item ${activeTab === 'shared-ops' ? 'active' : ''}`} onClick={() => setActiveTab('shared-ops')}>
            <Activity size={17} color={activeTab === 'shared-ops' ? '#d4af37' : undefined} />
            <span>Shared Ride Analytics</span>
          </li>
          <li className={`nav-item ${activeTab === 'fleet-specs' ? 'active' : ''}`} onClick={() => setActiveTab('fleet-specs')}>
            <Sliders size={17} color={activeTab === 'fleet-specs' ? '#d4af37' : undefined} />
            <span>Fleet Specs & WAV</span>
          </li>
        </ul>

        {/* Phase 2 Advanced Dispatch & Customer */}
        <div style={{ padding: '0 12px', fontSize: '0.68rem', letterSpacing: '0.08em', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: 6 }}>
          Dispatch & Support
        </div>
        <ul className="nav-menu" style={{ marginBottom: 16 }}>
          <li className={`nav-item ${activeTab === 'safety' ? 'active' : ''}`} onClick={() => setActiveTab('safety')}>
            <ShieldAlert size={17} color={activeTab === 'safety' ? '#ef4444' : undefined} />
            <span>Safety & SOC Command</span>
          </li>
          <li className={`nav-item ${activeTab === 'loyalty-recovery' ? 'active' : ''}`} onClick={() => setActiveTab('loyalty-recovery')}>
            <Award size={17} color={activeTab === 'loyalty-recovery' ? '#d4af37' : undefined} />
            <span>Loyalty & Recovery</span>
          </li>
          <li className={`nav-item ${activeTab === 'support-ops' ? 'active' : ''}`} onClick={() => setActiveTab('support-ops')}>
            <LifeBuoy size={17} />
            <span>Support & SLA Desk</span>
          </li>
          <li className={`nav-item ${activeTab === 'support' ? 'active' : ''}`} onClick={() => setActiveTab('support')}>
            <LifeBuoy size={17} />
            <span>Concierge & Knowledge</span>
          </li>
          <li className={`nav-item ${activeTab === 'onboarding-support' ? 'active' : ''}`} onClick={() => setActiveTab('onboarding-support')}>
            <LifeBuoy size={17} />
            <span>Driver Onboarding Requests</span>
          </li>
          <li className={`nav-item ${activeTab === 'broadcasts' ? 'active' : ''}`} onClick={() => setActiveTab('broadcasts')}>
            <Radio size={17} />
            <span>Broadcasts</span>
          </li>
          <li className={`nav-item ${activeTab === 'notifications' ? 'active' : ''}`} onClick={() => setActiveTab('notifications')}>
            <Bell size={17} color={activeTab === 'notifications' ? '#d4af37' : undefined} />
            <span>Notification Delivery</span>
          </li>
          <li className={`nav-item ${activeTab === 'surge' ? 'active' : ''}`} onClick={() => setActiveTab('surge')}>
            <Zap size={17} />
            <span>Surge & Geofence</span>
          </li>
          <li className={`nav-item ${activeTab === 'heatmap' ? 'active' : ''}`} onClick={() => setActiveTab('heatmap')}>
            <Map size={17} />
            <span>Demand Heatmap</span>
          </li>
        </ul>

        {/* Phase 3 Reliability, Security & Reports */}
        <div style={{ padding: '0 12px', fontSize: '0.68rem', letterSpacing: '0.08em', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: 6 }}>
          Reliability & Reports
        </div>
        <ul className="nav-menu" style={{ marginBottom: 16 }}>
          <li className={`nav-item ${activeTab === 'analytics-bi' ? 'active' : ''}`} onClick={() => setActiveTab('analytics-bi')}>
            <BarChart3 size={17} color={activeTab === 'analytics-bi' ? '#d4af37' : undefined} />
            <span>Analytics & Governance Suite</span>
          </li>
          <li className={`nav-item ${activeTab === 'reports' ? 'active' : ''}`} onClick={() => setActiveTab('reports')}>
            <FileText size={17} />
            <span>Executive Reports</span>
          </li>
          <li className={`nav-item ${activeTab === 'audit' ? 'active' : ''}`} onClick={() => setActiveTab('audit')}>
            <ShieldAlert size={17} />
            <span>Security Audit Logs</span>
          </li>
          <li className={`nav-item ${activeTab === 'health' ? 'active' : ''}`} onClick={() => setActiveTab('health')}>
            <Activity size={17} />
            <span>System Health</span>
          </li>
          <li className={`nav-item ${activeTab === 'backups' ? 'active' : ''}`} onClick={() => setActiveTab('backups')}>
            <Database size={17} />
            <span>Database Backups</span>
          </li>
          <li className={`nav-item ${activeTab === 'infra-ops' ? 'active' : ''}`} onClick={() => setActiveTab('infra-ops')}>
            <Cpu size={17} color={activeTab === 'infra-ops' ? '#d4af37' : undefined} />
            <span>Infra, Resilience & Ops</span>
          </li>
          <li className={`nav-item ${activeTab === 'enterprise-growth' ? 'active' : ''}`} onClick={() => setActiveTab('enterprise-growth')}>
            <Zap size={17} color={activeTab === 'enterprise-growth' ? '#d4af37' : undefined} />
            <span>Enterprise, B2B & Growth</span>
          </li>
        </ul>

        <div style={{ marginTop: 'auto', paddingTop: 20, borderTop: '1px solid var(--border-subtle)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: '50%',
                background: 'var(--gold-glow)',
                border: '1px solid var(--border-gold)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'var(--gold-primary)',
                fontWeight: 700
              }}
            >
              {(adminUser?.full_name || 'Admin').split(/\s+/).map((part: string) => part[0]).join('').slice(0, 2).toUpperCase()}
            </div>
            <div>
              <div style={{ fontSize: '0.88rem', fontWeight: 600 }}>{adminUser?.full_name || 'Administrator'}</div>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>{adminUser?.email || 'Admin account'}</div>
            </div>
            <button className="nav-item" style={{ marginLeft: 'auto', width: 'auto' }} onClick={onSignOut}>Sign out</button>
          </div>
        </div>
      </aside>

      {/* Main Content View */}
      <main className="main-content">
        {activeTab === 'dashboard' && <Dashboard />}
        {activeTab === 'fleet-specs' && <FleetAccessibilityConsole />}
        {activeTab === 'rides' && <AdminGovernanceConsole />}
        {activeTab === 'driver-financials' && <DriverFinancialsAndPayoutConsole />}
        {activeTab === 'finance-ops' && <FinanceOperationsConsole />}
        {activeTab === 'shared-ops' && <SharedRideOperationalAnalytics />}
        {activeTab === 'safety' && <SafetyAuditConsole />}
        {activeTab === 'loyalty-recovery' && <ReferralAndRecoveryConsole />}
        {activeTab === 'support-ops' && <SupportOperationsConsole />}
        {activeTab === 'support' && <SupportTicketDesk />}
        {activeTab === 'onboarding-support' && <DriverOnboardingTicketDesk />}
        {activeTab === 'broadcasts' && <CommunicationsConsole />}
        {activeTab === 'notifications' && <NotificationOperationsConsole />}
        {activeTab === 'surge' && <SurgePricingConsole />}
        {activeTab === 'heatmap' && <LocationOperationsConsole />}
        {activeTab === 'analytics-bi' && <AnalyticsAndGovernanceConsole />}
        {activeTab === 'reports' && <AdminReportsConsole />}
        {activeTab === 'audit' && <AdminAuditLogViewer />}
        {activeTab === 'health' && <AdminSystemHealth />}
        {activeTab === 'backups' && <AdminDatabaseBackup />}
        {activeTab === 'infra-ops' && <AdminInfraOpsConsole />}
        {activeTab === 'enterprise-growth' && <AdminEnterpriseGrowthConsole />}
      </main>
    </div>
  );
};

export default App;
