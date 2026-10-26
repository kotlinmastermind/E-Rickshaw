import React, { useState } from 'react';
import {
  LayoutDashboard, Users, Car, Navigation, DollarSign,
  FileText, ShieldAlert, Activity, Database,
  LifeBuoy, Radio, Zap, Map
} from 'lucide-react';
import { Dashboard } from './components/Dashboard';
import { UserManagement } from './components/UserManagement';
import { DriverManagement } from './components/DriverManagement';
import { RideManagement } from './components/RideManagement';
import { FareManagement } from './components/FareManagement';
import { SupportTicketDesk } from './components/SupportTicketDesk';
import { BroadcastConsole } from './components/BroadcastConsole';
import { SurgePricingConsole } from './components/SurgePricingConsole';
import { HeatMapMonitor } from './components/HeatMapMonitor';
import { AdminReportsConsole } from './components/AdminReportsConsole';
import { AdminAuditLogViewer } from './components/AdminAuditLogViewer';
import { AdminSystemHealth } from './components/AdminSystemHealth';
import { AdminDatabaseBackup } from './components/AdminDatabaseBackup';

type NavTab =
  | 'dashboard'
  | 'users'
  | 'drivers'
  | 'rides'
  | 'fares'
  | 'support'
  | 'broadcasts'
  | 'surge'
  | 'heatmap'
  | 'reports'
  | 'audit'
  | 'health'
  | 'backups';

export const App: React.FC = () => {
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
            <span>Rides & Pools</span>
          </li>
          <li className={`nav-item ${activeTab === 'drivers' ? 'active' : ''}`} onClick={() => setActiveTab('drivers')}>
            <Car size={17} />
            <span>Chauffeur Fleet</span>
          </li>
          <li className={`nav-item ${activeTab === 'users' ? 'active' : ''}`} onClick={() => setActiveTab('users')}>
            <Users size={17} />
            <span>User Accounts</span>
          </li>
          <li className={`nav-item ${activeTab === 'fares' ? 'active' : ''}`} onClick={() => setActiveTab('fares')}>
            <DollarSign size={17} />
            <span>Tariff Schedules</span>
          </li>
        </ul>

        {/* Phase 2 Advanced Dispatch & Customer */}
        <div style={{ padding: '0 12px', fontSize: '0.68rem', letterSpacing: '0.08em', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: 6 }}>
          Dispatch & Support
        </div>
        <ul className="nav-menu" style={{ marginBottom: 16 }}>
          <li className={`nav-item ${activeTab === 'support' ? 'active' : ''}`} onClick={() => setActiveTab('support')}>
            <LifeBuoy size={17} />
            <span>VIP Support Desk</span>
          </li>
          <li className={`nav-item ${activeTab === 'broadcasts' ? 'active' : ''}`} onClick={() => setActiveTab('broadcasts')}>
            <Radio size={17} />
            <span>Broadcasts</span>
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
              FD
            </div>
            <div>
              <div style={{ fontSize: '0.88rem', fontWeight: 600 }}>Fleet Director</div>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>director@luxurychauffeur.com</div>
            </div>
          </div>
        </div>
      </aside>

      {/* Main Content View */}
      <main className="main-content">
        {activeTab === 'dashboard' && <Dashboard />}
        {activeTab === 'users' && <UserManagement />}
        {activeTab === 'drivers' && <DriverManagement />}
        {activeTab === 'rides' && <RideManagement />}
        {activeTab === 'fares' && <FareManagement />}
        {activeTab === 'support' && <SupportTicketDesk />}
        {activeTab === 'broadcasts' && <BroadcastConsole />}
        {activeTab === 'surge' && <SurgePricingConsole />}
        {activeTab === 'heatmap' && <HeatMapMonitor />}
        {activeTab === 'reports' && <AdminReportsConsole />}
        {activeTab === 'audit' && <AdminAuditLogViewer />}
        {activeTab === 'health' && <AdminSystemHealth />}
        {activeTab === 'backups' && <AdminDatabaseBackup />}
      </main>
    </div>
  );
};

export default App;
