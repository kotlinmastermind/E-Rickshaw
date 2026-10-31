import React, { useEffect, useState, useRef } from 'react';
import {
  LayoutDashboard, Navigation, Bell,
  FileText, ShieldAlert, Activity,
  LifeBuoy, Radio, Zap, Map, Award, Sliders, Wallet, BarChart3,
  Search, Users, Car, DollarSign, Clock, ChevronRight, X
} from 'lucide-react';
import { Dashboard } from './components/Dashboard';
import { UserManagement } from './components/UserManagement';
import { DriverManagement } from './components/DriverManagement';
import { FareManagement } from './components/FareManagement';
import { SupportOperationsConsole } from './components/SupportOperationsConsole';
import { SupportTicketDesk } from './components/SupportTicketDesk';
import { CommunicationsConsole } from './components/CommunicationsConsole';
import { FinanceOperationsConsole } from './components/FinanceOperationsConsole';
import { SurgePricingConsole } from './components/SurgePricingConsole';
import { LocationOperationsConsole } from './components/LocationOperationsConsole';
import { AdminReportsConsole } from './components/AdminReportsConsole';
import { AdminAuditLogViewer } from './components/AdminAuditLogViewer';
import { AdminSystemHealth } from './components/AdminSystemHealth';
import { SafetyAuditConsole } from './components/SafetyAuditConsole';
import { ReferralAndRecoveryConsole } from './components/ReferralAndRecoveryConsole';
import { FleetAccessibilityConsole } from './components/FleetAccessibilityConsole';
import { DriverOnboardingTicketDesk } from './components/DriverOnboardingTicketDesk';
import { SharedRideOperationalAnalytics } from './components/SharedRideOperationalAnalytics';
import { AdminGovernanceConsole } from './components/AdminGovernanceConsole';
import { DriverFinancialsAndPayoutConsole } from './components/DriverFinancialsAndPayoutConsole';
import { NotificationOperationsConsole } from './components/NotificationOperationsConsole';
import { AnalyticsAndGovernanceConsole } from './components/AnalyticsAndGovernanceConsole';
import { AdminSignIn } from './components/AdminSignIn';
import { requestApi } from './services/api';

type NavTab =
  | 'dashboard'
  | 'users'
  | 'drivers'
  | 'rides'
  | 'fleet-specs'
  | 'fares'
  | 'surge'
  | 'heatmap'
  | 'driver-financials'
  | 'finance-ops'
  | 'support-ops'
  | 'support'
  | 'onboarding-support'
  | 'broadcasts'
  | 'notifications'
  | 'analytics-bi'
  | 'shared-ops'
  | 'reports'
  | 'safety'
  | 'audit'
  | 'health'
  | 'loyalty-recovery';

interface BreadcrumbInfo {
  group: string;
  screen: string;
}

const TAB_BREADCRUMBS: Record<NavTab, BreadcrumbInfo> = {
  'dashboard': { group: 'Operations & Fleet', screen: '05 Executive Dashboard' },
  'users': { group: 'Operations & Fleet', screen: '07 Customers Directory & 09 Suspension' },
  'drivers': { group: 'Operations & Fleet', screen: '10 Driver Queue & 11 Document Reviewer' },
  'rides': { group: 'Operations & Fleet', screen: '13 Live Ops Map, 14 Timeline & 16 Dispatch' },
  'fleet-specs': { group: 'Operations & Fleet', screen: '21 Vehicle Categories & WAV Accessibility' },
  'fares': { group: 'Pricing & Geofencing', screen: '18 Fare Versions & Rate Cards' },
  'surge': { group: 'Pricing & Geofencing', screen: '24 Calendar & 25 Surge Simulator' },
  'heatmap': { group: 'Pricing & Geofencing', screen: '22 Zone Polygon Editor & 23 Geofence' },
  'driver-financials': { group: 'Finance Controls', screen: '27 Payout Queue & Welfare' },
  'finance-ops': { group: 'Finance Controls', screen: '29 Recon, 30 Commission, 33 Dual Control & 34 GL' },
  'support-ops': { group: 'Support & Communications', screen: '36 Ticket Inbox & 38 SLA Desk' },
  'support': { group: 'Support & Communications', screen: '37 Concierge & Knowledge Repository' },
  'onboarding-support': { group: 'Support & Communications', screen: '10 Chauffeur Onboarding Requests' },
  'broadcasts': { group: 'Support & Communications', screen: '39 Segments, 40 Composer & 42 Emergency Broadcast' },
  'notifications': { group: 'Support & Communications', screen: '43 Push, SMS & WhatsApp Dispatch' },
  'analytics-bi': { group: 'Analytics & BI', screen: '44 Funnel, 45 Cancellation & 49 Retention BI' },
  'shared-ops': { group: 'Analytics & BI', screen: '48 Eco-Pool Efficiency & Carbon Ledger' },
  'reports': { group: 'Analytics & BI', screen: '54 Executive BI Report Builder & OTP Export' },
  'safety': { group: 'Governance & Compliance', screen: '53 Safety Trends & SOC Command Desk' },
  'audit': { group: 'Governance & Compliance', screen: '55 Security Audit Trail & Access Logs' },
  'health': { group: 'Governance & Compliance', screen: '57 System Health, DB Latency & Maps Quota' },
  'loyalty-recovery': { group: 'Growth & Promos', screen: '50 Promo Codes, Redemptions & Referrals' }
};

export const App: React.FC = () => {
  const [authenticated, setAuthenticated] = useState(false);
  const [checking, setChecking] = useState(() => Boolean(localStorage.getItem('admin_token')));
  const [adminUser, setAdminUser] = useState<any>(null);

  useEffect(() => {
    if (!localStorage.getItem('admin_token')) return;
    requestApi<any>('/auth/me').then(user => {
      if (user.role === 'admin' && user.mfa_enabled === true && user.is_active) {
        setAdminUser(user);
        setAuthenticated(true);
      } else {
        localStorage.removeItem('admin_token');
        localStorage.removeItem('admin_refresh_token');
      }
    }).catch(() => {
      localStorage.removeItem('admin_token');
      localStorage.removeItem('admin_refresh_token');
    }).finally(() => setChecking(false));
  }, []);

  if (checking) {
    return (
      <main role="status" style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#0D0D0F', color: '#C9A24B', fontFamily: 'Georgia, serif' }}>
        Verifying sovereign administrator session…
      </main>
    );
  }

  if (!authenticated) {
    return <AdminSignIn onAuthenticated={user => { setAdminUser(user); setAuthenticated(true); }} />;
  }

  return (
    <AdminConsole
      adminUser={adminUser}
      onSignOut={() => {
        void requestApi('/auth/logout', { method: 'POST' }).catch(() => undefined).finally(() => {
          localStorage.removeItem('admin_token');
          localStorage.removeItem('admin_refresh_token');
          setAuthenticated(false);
          setAdminUser(null);
        });
      }}
    />
  );
};

const AdminConsole: React.FC<{ adminUser: any; onSignOut: () => void }> = ({ adminUser, onSignOut }) => {
  const [activeTab, setActiveTab] = useState<NavTab>('dashboard');
  const [universalSearch, setUniversalSearch] = useState('');
  const [searchFilter, setSearchFilter] = useState<'all' | 'users' | 'drivers' | 'rides' | 'tickets'>('all');
  const [isSearchOpen, setIsSearchOpen] = useState(false);

  // NAV.a 15-minute idle countdown timer (900 seconds)
  const [sessionSecondsRemaining, setSessionSecondsRemaining] = useState(900);
  const lastActivityRef = useRef(Date.now());

  useEffect(() => {
    const handleUserActivity = () => {
      const now = Date.now();
      if (now - lastActivityRef.current > 5000) {
        lastActivityRef.current = now;
        setSessionSecondsRemaining(900);
      }
    };
    window.addEventListener('mousemove', handleUserActivity);
    window.addEventListener('keydown', handleUserActivity);
    window.addEventListener('click', handleUserActivity);

    const timer = setInterval(() => {
      setSessionSecondsRemaining(prev => {
        if (prev <= 1) {
          clearInterval(timer);
          onSignOut();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => {
      window.removeEventListener('mousemove', handleUserActivity);
      window.removeEventListener('keydown', handleUserActivity);
      window.removeEventListener('click', handleUserActivity);
      clearInterval(timer);
    };
  }, [onSignOut]);

  // NAV.a: Esc key closes search or active modal first
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        if (isSearchOpen) setIsSearchOpen(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isSearchOpen]);

  const formatTimer = (totalSecs: number) => {
    const mins = Math.floor(totalSecs / 60);
    const secs = totalSecs % 60;
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  };

  const breadcrumb = TAB_BREADCRUMBS[activeTab] || { group: 'Operations & Fleet', screen: 'Console' };

  return (
    <div className="luxury-app-container" style={{ display: 'flex', minHeight: '100vh', background: '#0D0D0F', color: '#F2EFE8' }}>
      {/* 7-Category Left Sidebar per NAV.a */}
      <aside className="luxury-sidebar" style={{ width: 260, minWidth: 260, background: '#17171A', borderRight: '1px solid #2C2C30', display: 'flex', flexDirection: 'column', height: '100vh', overflowY: 'auto' }}>
        <div className="brand-header" style={{ padding: '20px 16px', borderBottom: '1px solid #2C2C30' }}>
          <div style={{ fontFamily: 'Georgia, serif', color: '#C9A24B', fontSize: '18px', fontWeight: 'bold', letterSpacing: '0.05em' }}>
            RIDEGO LUXURY
          </div>
          <div style={{ fontSize: '10px', color: '#9A968C', letterSpacing: '0.08em', marginTop: 2 }}>
            EXECUTIVE CONTROL V3.0
          </div>
        </div>

        <nav style={{ padding: '12px 8px', flex: 1, display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Group 1: Operations & Fleet (Screens 07-17, 26) */}
          <div>
            <div style={{ padding: '0 10px', fontSize: '10px', letterSpacing: '0.08em', color: '#C9A24B', textTransform: 'uppercase', marginBottom: 4, fontWeight: 700 }}>
              1. Operations & Fleet
            </div>
            <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
              <SidebarItem icon={<LayoutDashboard size={15} />} label="Overview" active={activeTab === 'dashboard'} onClick={() => setActiveTab('dashboard')} />
              <SidebarItem icon={<Users size={15} />} label="Customers & Bans" active={activeTab === 'users'} onClick={() => setActiveTab('users')} />
              <SidebarItem icon={<Car size={15} />} label="Drivers & Documents" active={activeTab === 'drivers'} onClick={() => setActiveTab('drivers')} />
              <SidebarItem icon={<Navigation size={15} />} label="Live Map & Dispatch" active={activeTab === 'rides'} onClick={() => setActiveTab('rides')} />
              <SidebarItem icon={<Sliders size={15} />} label="Fleet Specs & WAV" active={activeTab === 'fleet-specs'} onClick={() => setActiveTab('fleet-specs')} />
            </ul>
          </div>

          {/* Group 2: Pricing & Geofencing (Screens 18-25) */}
          <div>
            <div style={{ padding: '0 10px', fontSize: '10px', letterSpacing: '0.08em', color: '#C9A24B', textTransform: 'uppercase', marginBottom: 4, fontWeight: 700 }}>
              2. Pricing & Geofencing
            </div>
            <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
              <SidebarItem icon={<DollarSign size={15} />} label="Fare Versions & Rules" active={activeTab === 'fares'} onClick={() => setActiveTab('fares')} />
              <SidebarItem icon={<Zap size={15} />} label="Surge Simulator" active={activeTab === 'surge'} onClick={() => setActiveTab('surge')} />
              <SidebarItem icon={<Map size={15} />} label="Zones & Geofencing" active={activeTab === 'heatmap'} onClick={() => setActiveTab('heatmap')} />
            </ul>
          </div>

          {/* Group 3: Finance Controls (Screens 27-35) */}
          <div>
            <div style={{ padding: '0 10px', fontSize: '10px', letterSpacing: '0.08em', color: '#C9A24B', textTransform: 'uppercase', marginBottom: 4, fontWeight: 700 }}>
              3. Finance Controls
            </div>
            <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
              <SidebarItem icon={<Wallet size={15} />} label="Chauffeur Payouts" active={activeTab === 'driver-financials'} onClick={() => setActiveTab('driver-financials')} />
              <SidebarItem icon={<DollarSign size={15} />} label="Finance & Recon" active={activeTab === 'finance-ops'} onClick={() => setActiveTab('finance-ops')} />
            </ul>
          </div>

          {/* Group 4: Support & Communications (Screens 36-43) */}
          <div>
            <div style={{ padding: '0 10px', fontSize: '10px', letterSpacing: '0.08em', color: '#C9A24B', textTransform: 'uppercase', marginBottom: 4, fontWeight: 700 }}>
              4. Support & Broadcast
            </div>
            <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
              <SidebarItem icon={<LifeBuoy size={15} />} label="Support SLA Desk" active={activeTab === 'support-ops'} onClick={() => setActiveTab('support-ops')} />
              <SidebarItem icon={<LifeBuoy size={15} />} label="Concierge Desk" active={activeTab === 'support'} onClick={() => setActiveTab('support')} />
              <SidebarItem icon={<Car size={15} />} label="Onboarding Queue" active={activeTab === 'onboarding-support'} onClick={() => setActiveTab('onboarding-support')} />
              <SidebarItem icon={<Radio size={15} />} label="Broadcast Composer" active={activeTab === 'broadcasts'} onClick={() => setActiveTab('broadcasts')} />
              <SidebarItem icon={<Bell size={15} />} label="Notification Delivery" active={activeTab === 'notifications'} onClick={() => setActiveTab('notifications')} />
            </ul>
          </div>

          {/* Group 5: Analytics & BI (Screens 44-54) */}
          <div>
            <div style={{ padding: '0 10px', fontSize: '10px', letterSpacing: '0.08em', color: '#C9A24B', textTransform: 'uppercase', marginBottom: 4, fontWeight: 700 }}>
              5. Analytics & BI
            </div>
            <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
              <SidebarItem icon={<BarChart3 size={15} />} label="Executive BI Suite" active={activeTab === 'analytics-bi'} onClick={() => setActiveTab('analytics-bi')} />
              <SidebarItem icon={<Activity size={15} />} label="Shared Ride Carbon" active={activeTab === 'shared-ops'} onClick={() => setActiveTab('shared-ops')} />
              <SidebarItem icon={<FileText size={15} />} label="Scheduled Reports" active={activeTab === 'reports'} onClick={() => setActiveTab('reports')} />
            </ul>
          </div>

          {/* Group 6: Governance & Compliance */}
          <div>
            <div style={{ padding: '0 10px', fontSize: '10px', letterSpacing: '0.08em', color: '#C9A24B', textTransform: 'uppercase', marginBottom: 4, fontWeight: 700 }}>
              6. Governance & Compliance
            </div>
            <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
              <SidebarItem icon={<ShieldAlert size={15} />} label="Safety & SOC Command" active={activeTab === 'safety'} onClick={() => setActiveTab('safety')} />
              <SidebarItem icon={<ShieldAlert size={15} />} label="Security Audit Trail" active={activeTab === 'audit'} onClick={() => setActiveTab('audit')} />
              <SidebarItem icon={<Activity size={15} />} label="System Health" active={activeTab === 'health'} onClick={() => setActiveTab('health')} />
            </ul>
          </div>

          {/* Group 7: Growth & Promos */}
          <div>
            <div style={{ padding: '0 10px', fontSize: '10px', letterSpacing: '0.08em', color: '#C9A24B', textTransform: 'uppercase', marginBottom: 4, fontWeight: 700 }}>
              7. Growth & Promos
            </div>
            <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
              <SidebarItem icon={<Award size={15} />} label="Promos & Referrals" active={activeTab === 'loyalty-recovery'} onClick={() => setActiveTab('loyalty-recovery')} />
            </ul>
          </div>
        </nav>

        {/* User Badge & Logout in Sidebar Footer */}
        <div style={{ padding: '14px 16px', borderTop: '1px solid #2C2C30', background: '#111114' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: '#C9A24B', color: '#111', fontWeight: 'bold', display: 'grid', placeItems: 'center', fontSize: '13px' }}>
              {(adminUser?.full_name || 'Admin').split(' ').map((n: string) => n[0]).join('').slice(0, 2).toUpperCase()}
            </div>
            <div style={{ overflow: 'hidden' }}>
              <div style={{ fontSize: '12px', fontWeight: 600, color: '#F2EFE8', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {adminUser?.full_name || 'Administrator'}
              </div>
              <div style={{ fontSize: '10px', color: '#C9A24B' }}>SUPER ADMIN</div>
            </div>
            <button
              onClick={onSignOut}
              style={{ marginLeft: 'auto', background: 'transparent', border: '1px solid #333', color: '#9A968C', borderRadius: 6, padding: '4px 8px', fontSize: '11px', cursor: 'pointer' }}
            >
              Sign out
            </button>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0, overflowY: 'auto' }}>
        {/* Top Bar with Universal Search, Timer, Profile & Breadcrumbs per NAV.a */}
        <header style={{ height: 60, minHeight: 60, background: '#17171A', borderBottom: '1px solid #2C2C30', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px', gap: 16 }}>
          {/* Breadcrumb Path */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '13px', color: '#9A968C' }}>
            <span style={{ color: '#C9A24B', fontWeight: 600 }}>{breadcrumb.group}</span>
            <ChevronRight size={14} />
            <span style={{ color: '#F2EFE8' }}>{breadcrumb.screen}</span>
          </div>

          {/* Universal Search (Screen 06) */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 1, maxWidth: 460 }}>
            <div style={{ position: 'relative', width: '100%' }}>
              <Search size={15} style={{ position: 'absolute', left: 10, top: 10, color: '#9A968C' }} />
              <input
                type="text"
                placeholder="Universal Search (Ride #, Driver, Phone, Ticket)..."
                value={universalSearch}
                onChange={e => {
                  setUniversalSearch(e.target.value);
                  setIsSearchOpen(Boolean(e.target.value.trim()));
                }}
                onFocus={() => { if (universalSearch.trim()) setIsSearchOpen(true); }}
                style={{
                  width: '100%',
                  background: '#0D0D0F',
                  border: '1px solid #2C2C30',
                  borderRadius: 8,
                  padding: '7px 10px 7px 32px',
                  color: '#F2EFE8',
                  fontSize: '12px'
                }}
              />
              {universalSearch && (
                <X
                  size={14}
                  onClick={() => { setUniversalSearch(''); setIsSearchOpen(false); }}
                  style={{ position: 'absolute', right: 10, top: 10, color: '#9A968C', cursor: 'pointer' }}
                />
              )}
            </div>
          </div>

          {/* Session Timer & Security Badge */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <div
              title="Automatic idle session timeout for administrative compliance"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '4px 10px',
                borderRadius: 20,
                background: sessionSecondsRemaining < 180 ? 'rgba(239, 68, 68, 0.2)' : 'rgba(201, 162, 75, 0.15)',
                border: `1px solid ${sessionSecondsRemaining < 180 ? '#EF4444' : '#C9A24B'}`,
                color: sessionSecondsRemaining < 180 ? '#EF4444' : '#C9A24B',
                fontSize: '12px',
                fontWeight: 600
              }}
            >
              <Clock size={13} />
              <span>Session: {formatTimer(sessionSecondsRemaining)}</span>
            </div>

            <div style={{ fontSize: '11px', color: '#10B981', display: 'flex', alignItems: 'center', gap: 4 }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#10B981' }} />
              Render API Live
            </div>
          </div>
        </header>

        {/* Universal Search Results Flyout */}
        {isSearchOpen && (
          <div
            style={{
              position: 'absolute',
              top: 60,
              left: 280,
              right: 280,
              zIndex: 9999,
              background: '#17171A',
              border: '1px solid #C9A24B',
              borderRadius: 8,
              padding: 16,
              boxShadow: '0 12px 36px rgba(0,0,0,0.8)'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <span style={{ fontSize: '12px', color: '#C9A24B', fontWeight: 'bold' }}>
                UNIVERSAL SEARCH RESULTS FOR "{universalSearch}"
              </span>
              <button
                onClick={() => setIsSearchOpen(false)}
                style={{ background: 'transparent', border: 'none', color: '#9A968C', cursor: 'pointer', fontSize: '11px' }}
              >
                Close (Esc)
              </button>
            </div>
            <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
              {(['all', 'users', 'drivers', 'rides', 'tickets'] as const).map(f => (
                <button
                  key={f}
                  onClick={() => setSearchFilter(f)}
                  style={{
                    padding: '4px 10px',
                    borderRadius: 14,
                    fontSize: '11px',
                    background: searchFilter === f ? '#C9A24B' : '#222226',
                    color: searchFilter === f ? '#111' : '#F2EFE8',
                    border: 'none',
                    cursor: 'pointer'
                  }}
                >
                  {f.toUpperCase()}
                </button>
              ))}
            </div>
            <div style={{ fontSize: '12px', color: '#9A968C', padding: '8px 0' }}>
              Press Enter or click quick shortcuts below to drill into specific database tables:
            </div>
            <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
              <button
                onClick={() => { setActiveTab('users'); setIsSearchOpen(false); }}
                style={{ padding: '6px 12px', borderRadius: 6, background: '#222226', color: '#C9A24B', border: '1px solid #333', cursor: 'pointer', fontSize: '11px' }}
              >
                Inspect Customers →
              </button>
              <button
                onClick={() => { setActiveTab('drivers'); setIsSearchOpen(false); }}
                style={{ padding: '6px 12px', borderRadius: 6, background: '#222226', color: '#C9A24B', border: '1px solid #333', cursor: 'pointer', fontSize: '11px' }}
              >
                Inspect Chauffeurs →
              </button>
              <button
                onClick={() => { setActiveTab('rides'); setIsSearchOpen(false); }}
                style={{ padding: '6px 12px', borderRadius: 6, background: '#222226', color: '#C9A24B', border: '1px solid #333', cursor: 'pointer', fontSize: '11px' }}
              >
                Inspect Live Ops & Dispatch →
              </button>
              <button
                onClick={() => { setActiveTab('support-ops'); setIsSearchOpen(false); }}
                style={{ padding: '6px 12px', borderRadius: 6, background: '#222226', color: '#C9A24B', border: '1px solid #333', cursor: 'pointer', fontSize: '11px' }}
              >
                Inspect Support Tickets →
              </button>
            </div>
          </div>
        )}

        {/* Tab Viewport */}
        <main style={{ flex: 1, padding: 24, overflowY: 'auto' }}>
          {activeTab === 'dashboard' && <Dashboard />}
          {activeTab === 'users' && <UserManagement />}
          {activeTab === 'drivers' && <DriverManagement />}
          {activeTab === 'rides' && <AdminGovernanceConsole />}
          {activeTab === 'fleet-specs' && <FleetAccessibilityConsole />}
          {activeTab === 'fares' && <FareManagement />}
          {activeTab === 'surge' && <SurgePricingConsole />}
          {activeTab === 'heatmap' && <LocationOperationsConsole />}
          {activeTab === 'driver-financials' && <DriverFinancialsAndPayoutConsole />}
          {activeTab === 'finance-ops' && <FinanceOperationsConsole />}
          {activeTab === 'support-ops' && <SupportOperationsConsole />}
          {activeTab === 'support' && <SupportTicketDesk />}
          {activeTab === 'onboarding-support' && <DriverOnboardingTicketDesk />}
          {activeTab === 'broadcasts' && <CommunicationsConsole />}
          {activeTab === 'notifications' && <NotificationOperationsConsole />}
          {activeTab === 'analytics-bi' && <AnalyticsAndGovernanceConsole />}
          {activeTab === 'shared-ops' && <SharedRideOperationalAnalytics />}
          {activeTab === 'reports' && <AdminReportsConsole />}
          {activeTab === 'safety' && <SafetyAuditConsole />}
          {activeTab === 'audit' && <AdminAuditLogViewer />}
          {activeTab === 'health' && <AdminSystemHealth />}
          {activeTab === 'loyalty-recovery' && <ReferralAndRecoveryConsole />}
        </main>
      </div>
    </div>
  );
};

const SidebarItem: React.FC<{
  icon: React.ReactNode;
  label: string;
  active: boolean;
  onClick: () => void;
}> = ({ icon, label, active, onClick }) => {
  return (
    <li>
      <button
        onClick={onClick}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '8px 12px',
          borderRadius: 8,
          fontSize: '12px',
          fontWeight: active ? 600 : 400,
          background: active ? 'rgba(201, 162, 75, 0.15)' : 'transparent',
          color: active ? '#C9A24B' : '#9A968C',
          border: active ? '1px solid #C9A24B' : '1px solid transparent',
          cursor: 'pointer',
          textAlign: 'left',
          transition: 'all 0.15s ease'
        }}
      >
        <span style={{ color: active ? '#C9A24B' : '#9A968C' }}>{icon}</span>
        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{label}</span>
      </button>
    </li>
  );
};

export default App;
