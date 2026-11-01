import React, { useState, useEffect } from 'react';
import { Search, Filter, Download, Menu, Link2, ChevronRight, Copy, Check } from 'lucide-react';
import { AdminRealMap } from './AdminRealMap';

export interface AdminLuxuryFlowConsoleProps {
  onSignOut?: () => void;
  onOpenSignIn?: () => void;
  adminUser?: any;
}

export const ADMIN_SCREENS = [
  { id: 1, slug: 'login', code: 'f', title: 'Login + MFA', desc: 'Email, Password, OTP', group: 'Authentication' },
  { id: 2, slug: 'dashboard', code: 'd', title: 'Live Dashboard', desc: 'KPIs, Rides chart, Alerts', group: 'Operations' },
  { id: 3, slug: 'search', code: 'l', title: 'Global Search', desc: 'Rides, Users, Drivers', group: 'Operations' },
  { id: 4, slug: 'audit-logs', code: 'l', title: 'Audit Logs', desc: 'Actor, Action, Filters', group: 'Governance' },
  { id: 5, slug: 'live-map', code: 'm', title: 'Live Map', desc: 'Rides, Drivers, Zones (Real Map)', group: 'Operations' },
  { id: 6, slug: 'dispatch', code: 'l', title: 'Dispatch Monitor', desc: 'Offers, Timeouts, Retries', group: 'Operations' },
  { id: 7, slug: 'rides', code: 'l', title: 'Ride List', desc: 'Filters, Status, Export', group: 'Rides' },
  { id: 8, slug: 'ride-detail', code: 'd', title: 'Ride Detail', desc: 'Timeline, Fare, Chat', group: 'Rides' },
  { id: 9, slug: 'drivers', code: 'l', title: 'Driver List', desc: 'Status, Rating, Filters', group: 'Chauffeurs' },
  { id: 10, slug: 'onboarding', code: 'l', title: 'Onboarding Queue', desc: 'Tickets, Assign, SLA', group: 'Chauffeurs' },
  { id: 11, slug: 'doc-review', code: 'd', title: 'Document Review', desc: 'Preview, Approve, Reject', group: 'Chauffeurs' },
  { id: 12, slug: 'driver-profile', code: 'd', title: 'Driver Profile', desc: 'Docs, Wallet, Trips', group: 'Chauffeurs' },
  { id: 13, slug: 'fatigue-spoof', code: 'l', title: 'Fatigue / Spoof Alerts', desc: 'Risk score, Action', group: 'Chauffeurs' },
  { id: 14, slug: 'customers', code: 'l', title: 'Customers', desc: 'Search, Profile, Tier', group: 'Customers' },
  { id: 15, slug: 'loyalty', code: 'd', title: 'Loyalty Mgmt', desc: 'Tiers, Points rules', group: 'Customers' },
  { id: 16, slug: 'categories', code: 'l', title: 'Vehicle Categories', desc: 'Amenities, Capacity, Active', group: 'Fleet' },
  { id: 17, slug: 'vehicles', code: 'l', title: 'Vehicles', desc: 'Plate, PUC, Fitness', group: 'Fleet' },
  { id: 18, slug: 'fare-rules', code: 'f', title: 'Fare Rules', desc: 'Base, Per km, Commission', group: 'Pricing' },
  { id: 19, slug: 'surge-rules', code: 'f', title: 'Surge Rules', desc: 'Zone, Multiplier, Schedule', group: 'Pricing' },
  { id: 20, slug: 'geofence-editor', code: 'm', title: 'Geofence Editor', desc: 'Polygon, Zone type (Real Map)', group: 'Pricing' },
  { id: 21, slug: 'road-restrictions', code: 'm', title: 'Road Restrictions', desc: 'Segment, Type, Active (Real Map)', group: 'Pricing' },
  { id: 22, slug: 'payments', code: 'l', title: 'Payments', desc: 'Gateway, Status, Filters', group: 'Finance' },
  { id: 23, slug: 'refunds', code: 'l', title: 'Refunds & Disputes', desc: 'Amount, Resolve', group: 'Finance' },
  { id: 24, slug: 'payouts', code: 'l', title: 'Driver Payouts', desc: 'Requests, Approve', group: 'Finance' },
  { id: 25, slug: 'sos-console', code: 'l', title: 'SOS Console', desc: 'Live alerts, Contacts, Notes', group: 'Safety' },
  { id: 26, slug: 'incidents', code: 'l', title: 'Incidents', desc: 'Ticket, Severity, Evidence', group: 'Safety' },
  { id: 27, slug: 'support-tickets', code: 'l', title: 'Support Tickets', desc: 'SLA, Priority, Assign', group: 'Support' },
  { id: 28, slug: 'promos', code: 'f', title: 'Promos & Referrals', desc: 'Code, Limits, Validity', group: 'Growth' },
  { id: 29, slug: 'broadcasts', code: 'f', title: 'Broadcasts', desc: 'Audience, Message, Schedule', group: 'Growth' },
  { id: 30, slug: 'roles-governance', code: 'd', title: 'Roles, Analytics & Latency', desc: 'RBAC matrix, Route latency, Reports', group: 'Governance' }
];

const getScreenFromHash = (): number => {
  if (typeof window === 'undefined') return 2;
  const raw = window.location.hash.replace(/^#\/?/, '').trim().toLowerCase();
  if (!raw) return 2;
  const match = ADMIN_SCREENS.find(s => s.slug === raw || s.id.toString() === raw);
  return match ? match.id : 2;
};

export const AdminLuxuryFlowConsole: React.FC<AdminLuxuryFlowConsoleProps> = ({
  onSignOut,
  onOpenSignIn,
  adminUser
}) => {
  // Navigation State with Clean URL Hash Routing for every screen
  const [selectedScreenId, setSelectedScreenId] = useState<number>(getScreenFromHash);
  const [globalSearch, setGlobalSearch] = useState<string>('');
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(false);
  const [copiedLinkToast, setCopiedLinkToast] = useState<boolean>(false);

  // Navigate to screen and update URL hash so every page has its own link
  const navigateTo = (screenId: number) => {
    const target = ADMIN_SCREENS.find(s => s.id === screenId);
    if (target) {
      setSelectedScreenId(target.id);
      window.location.hash = `#/${target.slug}`;
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  // URL Hash Synchronizer
  useEffect(() => {
    const handleHashChange = () => {
      const screenId = getScreenFromHash();
      setSelectedScreenId(screenId);
    };
    window.addEventListener('hashchange', handleHashChange);
    window.addEventListener('popstate', handleHashChange);

    if (!window.location.hash || window.location.hash === '#') {
      window.location.hash = '#/dashboard';
    }

    return () => {
      window.removeEventListener('hashchange', handleHashChange);
      window.removeEventListener('popstate', handleHashChange);
    };
  }, []);

  // Active SOS Banner State (High Priority Per ui.html Spec)
  const [activeSosAlert, setActiveSosAlert] = useState<{ id: string; driver: string; plate: string; loc: string; time: string } | null>({
    id: 'SOS-9912',
    driver: 'Suresh Kumar',
    plate: 'DL-1ER-4821',
    loc: 'Outer Circle Pillar 14, Connaught Place',
    time: '2 mins ago'
  });

  // 20 Admin Popups State (P.adm)
  const [popupConfirmRefund, setPopupConfirmRefund] = useState<boolean>(false);       // 01: d|Confirm refund
  const [popupBanUser, setPopupBanUser] = useState<boolean>(false);                   // 02: d|Ban user
  const [popupWalletAdjust, setPopupWalletAdjust] = useState<boolean>(false);         // 03: d|Wallet adjust
  const [popupApproveDriver, setPopupApproveDriver] = useState<boolean>(false);       // 04: d|Approve driver
  const [popupRejectDocs, setPopupRejectDocs] = useState<boolean>(false);             // 05: d|Reject documents
  const [popupAssignTicket, setPopupAssignTicket] = useState<boolean>(false);         // 06: b|Assign ticket
  const [popupDeleteGeofence, setPopupDeleteGeofence] = useState<boolean>(false);     // 07: d|Delete geofence
  const [popupPublishSurge, setPopupPublishSurge] = useState<boolean>(false);         // 08: d|Publish surge
  const [popupSendBroadcast, setPopupSendBroadcast] = useState<boolean>(false);       // 09: b|Send broadcast
  const [popupForceCancelRide, setPopupForceCancelRide] = useState<boolean>(false);   // 10: d|Force cancel ride
  const [popupResolveSos, setPopupResolveSos] = useState<boolean>(false);             // 11: d|Resolve SOS
  const [popupExportData, setPopupExportData] = useState<boolean>(false);             // 12: b|Export data
  const [popupRoleChange, setPopupRoleChange] = useState<boolean>(false);             // 13: d|Role change
  const [popupMfaRequired, setPopupMfaRequired] = useState<boolean>(false);           // 14: d|MFA required
  const [popupSessionTimeout, setPopupSessionTimeout] = useState<boolean>(false);     // 15: x|Session timeout
  const [toastSaved, setToastSaved] = useState<boolean>(false);                       // 16: t|Saved
  const [toastPermissionDenied, setToastPermissionDenied] = useState<boolean>(false); // 17: t|Permission denied
  const [popupFilters, setPopupFilters] = useState<boolean>(false);                   // 18: b|Filters
  const [popupDisablePromo, setPopupDisablePromo] = useState<boolean>(false);         // 19: d|Disable promo
  const [popupApprovePayouts, setPopupApprovePayouts] = useState<boolean>(false);     // 20: b|Approve payouts

  // Auto-dismiss toasts (3s)
  useEffect(() => {
    if (toastSaved) {
      const timer = setTimeout(() => setToastSaved(false), 3000);
      return () => clearTimeout(timer);
    }
  }, [toastSaved]);

  useEffect(() => {
    if (toastPermissionDenied) {
      const timer = setTimeout(() => setToastPermissionDenied(false), 3000);
      return () => clearTimeout(timer);
    }
  }, [toastPermissionDenied]);

  const currentScreen = ADMIN_SCREENS.find(s => s.id === selectedScreenId) || ADMIN_SCREENS[1];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', background: '#0b0b0d', color: '#f2ead3' }}>
      
      {/* 1. PRIORITY SOS RED BANNER (Mandated by ui.html spec) */}
      {activeSosAlert && (
        <div style={{
          background: 'linear-gradient(90deg, #b91c1c, #ef4444)',
          color: '#ffffff',
          padding: '10px 16px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 10,
          boxShadow: '0 2px 14px rgba(239, 68, 68, 0.4)',
          zIndex: 999
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{
              background: '#ffffff',
              color: '#b91c1c',
              fontWeight: 900,
              fontSize: 11,
              padding: '2px 8px',
              borderRadius: 4
            }}>
              ACTIVE CRITICAL SOS
            </span>
            <span style={{ fontWeight: 700, fontSize: 13 }}>
              {activeSosAlert.id}: Chauffeur {activeSosAlert.driver} ({activeSosAlert.plate}) triggered Emergency 112
            </span>
            <span style={{ fontSize: 12, opacity: 0.9 }}>
              · {activeSosAlert.loc} ({activeSosAlert.time})
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <button
              onClick={() => navigateTo(25)}
              style={{
                background: '#ffffff',
                color: '#000000',
                border: 'none',
                borderRadius: 6,
                padding: '5px 12px',
                fontWeight: 800,
                fontSize: 11,
                cursor: 'pointer'
              }}
            >
              Open SOS Console (25) →
            </button>
            <button
              onClick={() => setPopupResolveSos(true)}
              style={{
                background: 'rgba(0, 0, 0, 0.35)',
                color: '#ffffff',
                border: '1px solid #ffffff44',
                borderRadius: 6,
                padding: '5px 10px',
                fontSize: 11,
                cursor: 'pointer'
              }}
            >
              Resolve Alert
            </button>
          </div>
        </div>
      )}

      {/* 2. TOP GLOBAL HEADER */}
      <header style={{
        background: '#16161a',
        borderBottom: '1px solid #2a2a2e',
        padding: '12px 20px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: 16,
        position: 'sticky',
        top: 0,
        zIndex: 50
      }}>
        {/* Brand & Sidebar Toggle */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              background: '#0f0f12',
              border: '1px solid #333',
              color: '#d4af37',
              borderRadius: 8,
              padding: 6,
              cursor: 'pointer'
            }}
          >
            <Menu size={18} />
          </button>

          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ color: '#d4af37', fontWeight: 800, fontSize: 16, letterSpacing: '0.04em' }}>
                LUXURY CHAUFFEUR ADMIN
              </span>
              <span style={{
                background: 'rgba(212, 175, 55, 0.15)',
                color: '#d4af37',
                border: '1px solid #d4af37',
                borderRadius: 12,
                padding: '2px 8px',
                fontSize: 10,
                fontWeight: 700
              }}>
                {adminUser?.email ? `SUPERADMIN · ${adminUser.email}` : 'SUPERADMIN RBAC'}
              </span>
            </div>
            <div style={{ fontSize: 11, color: '#8d8672' }}>
              Screen {currentScreen.id.toString().padStart(2, '0')}: {currentScreen.title} ({currentScreen.desc})
            </div>
          </div>
        </div>

        {/* Global Search (Screen 03 Shortcut) */}
        <div style={{ flex: 1, maxWidth: 440, position: 'relative' }}>
          <Search size={15} style={{ position: 'absolute', left: 12, top: 10, color: '#8d8672' }} />
          <input
            type="text"
            placeholder="Global search rides, users, drivers, invoices (Screen 03)…"
            value={globalSearch}
            onChange={e => {
              setGlobalSearch(e.target.value);
              if (selectedScreenId !== 3 && e.target.value.length > 0) {
                navigateTo(3);
              }
            }}
            style={{
              width: '100%',
              background: '#0f0f12',
              border: '1px solid #333',
              color: '#f2ead3',
              borderRadius: 10,
              padding: '8px 12px 8px 36px',
              fontSize: 12,
              outline: 'none'
            }}
          />
        </div>

        {/* Action Controls & Direct URL Tools */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button
            onClick={() => {
              if (navigator.clipboard) {
                navigator.clipboard.writeText(window.location.href);
                setCopiedLinkToast(true);
                setTimeout(() => setCopiedLinkToast(false), 2500);
              }
            }}
            title="Copy direct shareable link for this specific screen"
            style={{
              background: '#16161a',
              border: '1px solid #d4af3755',
              color: '#d4af37',
              borderRadius: 8,
              padding: '6px 12px',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 5
            }}
          >
            {copiedLinkToast ? <Check size={13} color="#22c55e" /> : <Copy size={13} />}
            {copiedLinkToast ? 'Link Copied!' : 'Copy Page Link'}
          </button>

          <button
            onClick={() => setPopupFilters(true)}
            style={{
              background: '#16161a',
              border: '1px solid #333',
              color: '#f2ead3',
              borderRadius: 8,
              padding: '6px 12px',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 5
            }}
          >
            <Filter size={13} /> Filters
          </button>

          <button
            onClick={() => setPopupExportData(true)}
            style={{
              background: '#16161a',
              border: '1px solid #333',
              color: '#f2ead3',
              borderRadius: 8,
              padding: '6px 12px',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 5
            }}
          >
            <Download size={13} /> Export
          </button>

          {adminUser ? (
            <button
              onClick={() => {
                if (onSignOut) onSignOut();
                else setPopupSessionTimeout(true);
              }}
              style={{
                background: '#221111',
                border: '1px solid #ef444455',
                color: '#ef4444',
                borderRadius: 8,
                padding: '6px 12px',
                fontSize: 11,
                fontWeight: 700,
                cursor: 'pointer'
              }}
            >
              Logout
            </button>
          ) : (
            <button
              onClick={() => {
                if (onOpenSignIn) onOpenSignIn();
                else navigateTo(1);
              }}
              style={{
                background: 'linear-gradient(135deg, #d4af37, #aa820a)',
                border: 'none',
                color: '#000000',
                borderRadius: 8,
                padding: '6px 14px',
                fontSize: 11,
                fontWeight: 800,
                cursor: 'pointer'
              }}
            >
              Sign In
            </button>
          )}
        </div>
      </header>

      {/* 2.1 BREADCRUMBS & DIRECT QUICK LINKS BAR */}
      <div style={{
        background: '#0e0e11',
        borderBottom: '1px solid #1f1f24',
        padding: '8px 20px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: 10,
        fontSize: 11
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <a
            href="#/dashboard"
            onClick={(e) => { e.preventDefault(); navigateTo(2); }}
            style={{ color: '#d4af37', textDecoration: 'none', fontWeight: 700 }}
          >
            Admin Panel
          </a>
          <ChevronRight size={12} color="#8d8672" />
          <span style={{ color: '#8d8672' }}>{currentScreen.group}</span>
          <ChevronRight size={12} color="#8d8672" />
          <span style={{ color: '#ffffff', fontWeight: 600 }}>
            Screen {currentScreen.id.toString().padStart(2, '0')}: {currentScreen.title}
          </span>
          <span style={{
            background: '#1a1a20',
            color: '#8d8672',
            padding: '1px 6px',
            borderRadius: 4,
            marginLeft: 6,
            fontSize: 10,
            fontFamily: 'monospace'
          }}>
            #{currentScreen.slug}
          </span>
        </div>

        {/* Quick Screen Category Links */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
          <span style={{ color: '#8d8672', fontSize: 10 }}>QUICK JUMP:</span>
          {[
            { id: 2, label: 'Dashboard', slug: 'dashboard' },
            { id: 5, label: 'Live Map', slug: 'live-map' },
            { id: 6, label: 'Dispatch', slug: 'dispatch' },
            { id: 7, label: 'Rides', slug: 'rides' },
            { id: 9, label: 'Drivers', slug: 'drivers' },
            { id: 14, label: 'Customers', slug: 'customers' },
            { id: 16, label: 'Fleet', slug: 'categories' },
            { id: 18, label: 'Fares', slug: 'fare-rules' },
            { id: 20, label: 'Geofences', slug: 'geofence-editor' },
            { id: 22, label: 'Payments', slug: 'payments' },
            { id: 24, label: 'Payouts', slug: 'payouts' },
            { id: 25, label: 'SOS', slug: 'sos-console' }
          ].map(q => (
            <a
              key={q.id}
              href={`#/${q.slug}`}
              onClick={(e) => { e.preventDefault(); navigateTo(q.id); }}
              style={{
                color: selectedScreenId === q.id ? '#000000' : '#d4af37',
                background: selectedScreenId === q.id ? '#d4af37' : '#16161a',
                border: '1px solid #d4af3744',
                padding: '2px 8px',
                borderRadius: 4,
                textDecoration: 'none',
                fontWeight: 600,
                fontSize: 10
              }}
            >
              {q.label}
            </a>
          ))}
        </div>
      </div>

      {/* 3. MAIN WORKBENCH: SIDEBAR + SCREEN VIEWPORT */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        
        {/* LEFT SIDEBAR: 30 SCREENS HIERARCHY */}
        <aside style={{
          width: sidebarCollapsed ? 64 : 260,
          background: '#121215',
          borderRight: '1px solid #2a2a2e',
          display: 'flex',
          flexDirection: 'column',
          transition: 'width 0.2s ease',
          overflowY: 'auto'
        }}>
          <div style={{ padding: '12px 14px', borderBottom: '1px solid #222' }}>
            <span style={{ fontSize: 11, color: '#8d8672', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              {sidebarCollapsed ? '30' : '30 Blueprint Screens'}
            </span>
          </div>

          <div style={{ padding: '8px 6px', display: 'flex', flexDirection: 'column', gap: 2 }}>
            {ADMIN_SCREENS.map((s, idx) => {
              const isSelected = selectedScreenId === s.id;
              const prevGroup = idx > 0 ? ADMIN_SCREENS[idx - 1].group : null;
              const isNewGroup = s.group !== prevGroup;

              return (
                <React.Fragment key={s.id}>
                  {isNewGroup && !sidebarCollapsed && (
                    <div style={{
                      padding: '10px 8px 4px',
                      fontSize: 9,
                      fontWeight: 800,
                      color: '#8d8672',
                      textTransform: 'uppercase',
                      letterSpacing: '0.08em'
                    }}>
                      {s.group}
                    </div>
                  )}

                  <a
                    href={`#/${s.slug}`}
                    onClick={(e) => {
                      e.preventDefault();
                      navigateTo(s.id);
                    }}
                    title={`${s.title} · Direct link: #/${s.slug}`}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      padding: '8px 10px',
                      borderRadius: 8,
                      background: isSelected ? 'linear-gradient(90deg, rgba(212, 175, 55, 0.2), rgba(212, 175, 55, 0.05))' : 'transparent',
                      border: isSelected ? '1px solid #d4af37' : '1px solid transparent',
                      color: isSelected ? '#d4af37' : '#f2ead3',
                      textAlign: 'left',
                      textDecoration: 'none',
                      cursor: 'pointer',
                      fontSize: 12,
                      transition: 'all 0.15s ease'
                    }}
                  >
                    <span style={{
                      width: 22,
                      height: 22,
                      borderRadius: 4,
                      background: isSelected ? '#d4af37' : '#1e1e24',
                      color: isSelected ? '#000' : '#8d8672',
                      fontWeight: 800,
                      fontSize: 10,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0
                    }}>
                      {s.id.toString().padStart(2, '0')}
                    </span>

                    {!sidebarCollapsed && (
                      <div style={{ overflow: 'hidden', flex: 1 }}>
                        <div style={{
                          fontWeight: isSelected ? 700 : 500,
                          whiteSpace: 'nowrap',
                          textOverflow: 'ellipsis',
                          overflow: 'hidden',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between'
                        }}>
                          <span>{s.title}</span>
                          {isSelected && <Link2 size={11} color="#d4af37" />}
                        </div>
                        <div style={{ fontSize: 10, color: '#8d8672', whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>
                          /{s.slug}
                        </div>
                      </div>
                    )}
                  </a>
                </React.Fragment>
              );
            })}
          </div>
        </aside>

        {/* RIGHT VIEWPORT: ACTIVE SCREEN CONTENT (01 to 30) */}
        <main style={{ flex: 1, padding: 20, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 20 }}>
          
          {/* SCREEN 01: Login + MFA */}
          {selectedScreenId === 1 && (
            <div style={{ maxWidth: 480, margin: '40px auto', background: '#16161a', padding: 24, borderRadius: 16, border: '1px solid #d4af37' }}>
              <h3 style={{ color: '#d4af37', marginBottom: 6 }}>01 · Login + MFA Security Gate</h3>
              <p style={{ color: '#8d8672', fontSize: 12, marginBottom: 20 }}>Superadmin session credentials with hardware/TOTP Authenticator.</p>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <div>
                  <label style={{ fontSize: 11, color: '#8d8672' }}>Admin Email Address</label>
                  <input readOnly value="admin@luxurychauffeur.in" style={{ width: '100%', background: '#0f0f12', border: '1px solid #333', color: '#f2ead3', padding: '8px 12px', borderRadius: 8, marginTop: 4 }} />
                </div>
                <div>
                  <label style={{ fontSize: 11, color: '#8d8672' }}>Master Password</label>
                  <input readOnly type="password" value="••••••••••••" style={{ width: '100%', background: '#0f0f12', border: '1px solid #333', color: '#f2ead3', padding: '8px 12px', borderRadius: 8, marginTop: 4 }} />
                </div>
                <div>
                  <label style={{ fontSize: 11, color: '#d4af37', fontWeight: 700 }}>6-Digit TOTP Authenticator OTP</label>
                  <input readOnly value="849 201" style={{ width: '100%', background: '#0f0f12', border: '1px solid #d4af37', color: '#d4af37', fontWeight: 800, fontSize: 16, textAlign: 'center', padding: '8px 12px', borderRadius: 8, marginTop: 4, letterSpacing: 4 }} />
                </div>

                <button
                  onClick={() => {
                    setToastSaved(true);
                    navigateTo(2);
                  }}
                  style={{ background: 'linear-gradient(90deg, #b8962e, #d4af37)', color: '#000', border: 'none', borderRadius: 8, padding: 12, fontWeight: 800, marginTop: 12, cursor: 'pointer' }}
                >
                  Verify MFA & Unlock Console
                </button>
              </div>
            </div>
          )}

          {/* SCREEN 02: Live Dashboard */}
          {selectedScreenId === 2 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <h2 style={{ color: '#d4af37', margin: 0 }}>02 · Live Executive Dashboard</h2>
                  <p style={{ color: '#8d8672', fontSize: 12, margin: '4px 0 0' }}>Real-time telemetry KPIs, revenue metrics, and system alert feed.</p>
                </div>
                <button
                  onClick={() => navigateTo(5)}
                  style={{ background: '#d4af37', color: '#000', border: 'none', borderRadius: 8, padding: '8px 16px', fontWeight: 800, fontSize: 12, cursor: 'pointer' }}
                >
                  View Real Fleet Map (05) →
                </button>
              </div>

              {/* 4 KPI Cards */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 14 }}>
                <div style={{ background: '#16161a', border: '1px solid #2a2a2e', padding: 16, borderRadius: 12 }}>
                  <div style={{ fontSize: 11, color: '#8d8672' }}>Active Chauffeur Rides</div>
                  <div style={{ fontSize: 28, color: '#d4af37', fontWeight: 900 }}>84 Trips</div>
                  <div style={{ fontSize: 11, color: '#10b981', marginTop: 4 }}>+14% vs yesterday peak</div>
                </div>
                <div style={{ background: '#16161a', border: '1px solid #2a2a2e', padding: 16, borderRadius: 12 }}>
                  <div style={{ fontSize: 11, color: '#8d8672' }}>Online Chauffeurs</div>
                  <div style={{ fontSize: 28, color: '#f2ead3', fontWeight: 900 }}>142 EVs</div>
                  <div style={{ fontSize: 11, color: '#8d8672', marginTop: 4 }}>Saarthi & Mayuri Fleet</div>
                </div>
                <div style={{ background: '#16161a', border: '1px solid #2a2a2e', padding: 16, borderRadius: 12 }}>
                  <div style={{ fontSize: 11, color: '#8d8672' }}>Today's Gross GMV</div>
                  <div style={{ fontSize: 28, color: '#d4af37', fontWeight: 900 }}>₹ 1,48,920</div>
                  <div style={{ fontSize: 11, color: '#10b981', marginTop: 4 }}>Net Commission: ₹14,892</div>
                </div>
                <div style={{ background: '#16161a', border: '1px solid #2a2a2e', padding: 16, borderRadius: 12 }}>
                  <div style={{ fontSize: 11, color: '#8d8672' }}>Emergency SOS Queue</div>
                  <div style={{ fontSize: 28, color: '#ef4444', fontWeight: 900 }}>0 Active</div>
                  <div style={{ fontSize: 11, color: '#10b981', marginTop: 4 }}>All cleared with Fleet SOC</div>
                </div>
              </div>

              {/* Real Map Integration on Dashboard */}
              <div style={{ background: '#16161a', border: '1px solid #2a2a2e', padding: 16, borderRadius: 14 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                  <span style={{ color: '#d4af37', fontWeight: 700, fontSize: 13 }}>Live Operational Overview (Delhi NCR)</span>
                  <button onClick={() => navigateTo(5)} style={{ background: 'transparent', border: '1px solid #333', color: '#8d8672', borderRadius: 6, padding: '4px 8px', fontSize: 11, cursor: 'pointer' }}>
                    Full Map →
                  </button>
                </div>
                <AdminRealMap height={320} mode="live_fleet" selectedScreenTitle="Dashboard Live Map" />
              </div>
            </div>
          )}

          {/* SCREEN 03: Global Search */}
          {selectedScreenId === 3 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <h2 style={{ color: '#d4af37', margin: 0 }}>03 · Universal Global Search</h2>
              <div style={{ background: '#16161a', border: '1px solid #2a2a2e', borderRadius: 12, padding: 16 }}>
                <div style={{ color: '#8d8672', fontSize: 12, marginBottom: 12 }}>
                  Search Results for "{globalSearch || 'Recent Queries'}":
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ padding: 12, background: '#0f0f12', borderRadius: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <b style={{ color: '#d4af37' }}>Trip #8491</b> · Priya Sharma → Connaught Place
                      <div style={{ fontSize: 11, color: '#8d8672' }}>Completed · Fare: ₹145 · Chauffeur: Suresh Kumar</div>
                    </div>
                    <button onClick={() => navigateTo(8)} style={{ background: '#222', color: '#d4af37', border: '1px solid #333', padding: '4px 10px', borderRadius: 6, fontSize: 11, cursor: 'pointer' }}>
                      Inspect Ride →
                    </button>
                  </div>
                  <div style={{ padding: 12, background: '#0f0f12', borderRadius: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <b style={{ color: '#d4af37' }}>Chauffeur Suresh Kumar</b> · DL-1ER-4821
                      <div style={{ fontSize: 11, color: '#8d8672' }}>Rating 4.88 ★ · Gold Tier · Saarthi EV</div>
                    </div>
                    <button onClick={() => navigateTo(12)} style={{ background: '#222', color: '#d4af37', border: '1px solid #333', padding: '4px 10px', borderRadius: 6, fontSize: 11, cursor: 'pointer' }}>
                      View Profile →
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* SCREEN 04: Audit Logs */}
          {selectedScreenId === 4 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h2 style={{ color: '#d4af37', margin: 0 }}>04 · Compliance Audit Logs</h2>
                <button onClick={() => setPopupExportData(true)} style={{ background: '#16161a', border: '1px solid #333', color: '#d4af37', padding: '6px 12px', borderRadius: 8, fontSize: 11, cursor: 'pointer' }}>
                  Export CSV
                </button>
              </div>

              <div style={{ background: '#16161a', border: '1px solid #2a2a2e', borderRadius: 12, overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                  <thead>
                    <tr style={{ background: '#0f0f12', color: '#8d8672', borderBottom: '1px solid #2a2a2e', textAlign: 'left' }}>
                      <th style={{ padding: '10px 14px' }}>Actor</th>
                      <th style={{ padding: '10px 14px' }}>Action</th>
                      <th style={{ padding: '10px 14px' }}>Target Table</th>
                      <th style={{ padding: '10px 14px' }}>Details & Reason</th>
                      <th style={{ padding: '10px 14px' }}>Timestamp</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr style={{ borderBottom: '1px solid #1f1f24' }}>
                      <td style={{ padding: '10px 14px', color: '#d4af37' }}>admin@luxury.in</td>
                      <td style={{ padding: '10px 14px' }}><span style={{ color: '#10b981' }}>APPROVE_DRIVER</span></td>
                      <td style={{ padding: '10px 14px', color: '#8d8672' }}>drivers</td>
                      <td style={{ padding: '10px 14px' }}>Approved Chauffeur Suresh Kumar (DL-1ER-4821)</td>
                      <td style={{ padding: '10px 14px', color: '#8d8672' }}>Today 10:14 AM</td>
                    </tr>
                    <tr style={{ borderBottom: '1px solid #1f1f24' }}>
                      <td style={{ padding: '10px 14px', color: '#d4af37' }}>admin@luxury.in</td>
                      <td style={{ padding: '10px 14px' }}><span style={{ color: '#f59e0b' }}>WALLET_ADJUST</span></td>
                      <td style={{ padding: '10px 14px', color: '#8d8672' }}>driver_wallets</td>
                      <td style={{ padding: '10px 14px' }}>Adjusted +₹250 Weekend Quest Bonus</td>
                      <td style={{ padding: '10px 14px', color: '#8d8672' }}>Today 09:40 AM</td>
                    </tr>
                    <tr style={{ borderBottom: '1px solid #1f1f24' }}>
                      <td style={{ padding: '10px 14px', color: '#d4af37' }}>finance_lead@luxury.in</td>
                      <td style={{ padding: '10px 14px' }}><span style={{ color: '#3b82f6' }}>CONFIRM_REFUND</span></td>
                      <td style={{ padding: '10px 14px', color: '#8d8672' }}>refunds</td>
                      <td style={{ padding: '10px 14px' }}>Approved ₹145 refund for Trip #8410 (Route issue)</td>
                      <td style={{ padding: '10px 14px', color: '#8d8672' }}>Yesterday 06:12 PM</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* SCREEN 05: Live Map (Real Leaflet Map) */}
          {selectedScreenId === 5 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <h2 style={{ color: '#d4af37', margin: 0 }}>05 · Live Dispatch & Fleet Map</h2>
                  <p style={{ color: '#8d8672', fontSize: 12, margin: '4px 0 0' }}>Real Leaflet GPS integration with live E-Rickshaw telemetry and surge zone overlays.</p>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button onClick={() => setPopupPublishSurge(true)} style={{ background: '#d4af37', color: '#000', border: 'none', borderRadius: 8, padding: '6px 14px', fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                    Publish Surge (08)
                  </button>
                  <button onClick={() => navigateTo(20)} style={{ background: '#16161a', border: '1px solid #333', color: '#d4af37', borderRadius: 8, padding: '6px 14px', fontSize: 11, fontWeight: 600, cursor: 'pointer' }}>
                    Geofence Editor (20) →
                  </button>
                </div>
              </div>

              <AdminRealMap height="540px" mode="live_fleet" selectedScreenTitle="Live Fleet & Zones" />
            </div>
          )}

          {/* SCREEN 06: Dispatch Monitor */}
          {selectedScreenId === 6 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <h2 style={{ color: '#d4af37', margin: 0 }}>06 · Dispatch Algorithm Monitor</h2>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
                <div style={{ background: '#16161a', padding: 16, borderRadius: 10, border: '1px solid #2a2a2e' }}>
                  <div style={{ color: '#8d8672', fontSize: 11 }}>20-Second Offer Timeouts</div>
                  <div style={{ fontSize: 24, color: '#f59e0b', fontWeight: 800 }}>3.2%</div>
                  <div style={{ color: '#10b981', fontSize: 11 }}>Under 5% threshold</div>
                </div>
                <div style={{ background: '#16161a', padding: 16, borderRadius: 10, border: '1px solid #2a2a2e' }}>
                  <div style={{ color: '#8d8672', fontSize: 11 }}>Auto-Accept Ratio</div>
                  <div style={{ fontSize: 24, color: '#10b981', fontWeight: 800 }}>88.4%</div>
                  <div style={{ color: '#8d8672', fontSize: 11 }}>High chauffeur responsiveness</div>
                </div>
                <div style={{ background: '#16161a', padding: 16, borderRadius: 10, border: '1px solid #2a2a2e' }}>
                  <div style={{ color: '#8d8672', fontSize: 11 }}>Average Dispatch Latency</div>
                  <div style={{ fontSize: 24, color: '#d4af37', fontWeight: 800 }}>1.4 seconds</div>
                  <div style={{ color: '#10b981', fontSize: 11 }}>Optimized proximity index</div>
                </div>
              </div>
            </div>
          )}

          {/* SCREEN 07: Ride List */}
          {selectedScreenId === 7 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h2 style={{ color: '#d4af37', margin: 0 }}>07 · Passenger Ride Management</h2>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button onClick={() => setPopupFilters(true)} style={{ background: '#16161a', border: '1px solid #333', color: '#d4af37', padding: '6px 12px', borderRadius: 8, fontSize: 11, cursor: 'pointer' }}>
                    Filter Status
                  </button>
                  <button onClick={() => setPopupExportData(true)} style={{ background: '#16161a', border: '1px solid #333', color: '#f2ead3', padding: '6px 12px', borderRadius: 8, fontSize: 11, cursor: 'pointer' }}>
                    Export
                  </button>
                </div>
              </div>

              <div style={{ background: '#16161a', border: '1px solid #2a2a2e', borderRadius: 12, overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                  <thead>
                    <tr style={{ background: '#0f0f12', color: '#8d8672', borderBottom: '1px solid #2a2a2e', textAlign: 'left' }}>
                      <th style={{ padding: '10px 14px' }}>Ride ID</th>
                      <th style={{ padding: '10px 14px' }}>Rider</th>
                      <th style={{ padding: '10px 14px' }}>Chauffeur</th>
                      <th style={{ padding: '10px 14px' }}>Route</th>
                      <th style={{ padding: '10px 14px' }}>Fare</th>
                      <th style={{ padding: '10px 14px' }}>Status</th>
                      <th style={{ padding: '10px 14px' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr style={{ borderBottom: '1px solid #1f1f24' }}>
                      <td style={{ padding: '10px 14px', color: '#d4af37', fontWeight: 700 }}>#8491</td>
                      <td style={{ padding: '10px 14px' }}>Priya Sharma</td>
                      <td style={{ padding: '10px 14px' }}>Suresh Kumar</td>
                      <td style={{ padding: '10px 14px', color: '#8d8672' }}>CP Gate 2 → Khan Market</td>
                      <td style={{ padding: '10px 14px', color: '#d4af37', fontWeight: 700 }}>₹ 145</td>
                      <td style={{ padding: '10px 14px' }}><span style={{ color: '#10b981', background: '#10b98115', padding: '2px 8px', borderRadius: 4, fontWeight: 700 }}>Completed</span></td>
                      <td style={{ padding: '10px 14px' }}>
                        <button onClick={() => navigateTo(8)} style={{ background: '#222', color: '#d4af37', border: '1px solid #333', padding: '4px 8px', borderRadius: 4, cursor: 'pointer', fontSize: 11 }}>
                          Detail (08)
                        </button>
                      </td>
                    </tr>
                    <tr style={{ borderBottom: '1px solid #1f1f24' }}>
                      <td style={{ padding: '10px 14px', color: '#d4af37', fontWeight: 700 }}>#8492</td>
                      <td style={{ padding: '10px 14px' }}>Amit Verma</td>
                      <td style={{ padding: '10px 14px' }}>Rajesh Verma</td>
                      <td style={{ padding: '10px 14px', color: '#8d8672' }}>NDLS Gate 1 → Karol Bagh</td>
                      <td style={{ padding: '10px 14px', color: '#d4af37', fontWeight: 700 }}>₹ 110</td>
                      <td style={{ padding: '10px 14px' }}><span style={{ color: '#3b82f6', background: '#3b82f615', padding: '2px 8px', borderRadius: 4, fontWeight: 700 }}>In Progress</span></td>
                      <td style={{ padding: '10px 14px' }}>
                        <button onClick={() => setPopupForceCancelRide(true)} style={{ background: '#331111', color: '#ef4444', border: '1px solid #ef444433', padding: '4px 8px', borderRadius: 4, cursor: 'pointer', fontSize: 11 }}>
                          Force Cancel (10)
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* SCREEN 08: Ride Detail */}
          {selectedScreenId === 8 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h2 style={{ color: '#d4af37', margin: 0 }}>08 · Ride Detail Inspection (#8491)</h2>
                <button onClick={() => navigateTo(7)} style={{ background: '#16161a', border: '1px solid #333', color: '#d4af37', padding: '6px 12px', borderRadius: 8, fontSize: 11, cursor: 'pointer' }}>
                  ← Back to Ride List
                </button>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 16 }}>
                <div style={{ background: '#16161a', border: '1px solid #2a2a2e', padding: 18, borderRadius: 12 }}>
                  <h4 style={{ color: '#d4af37', marginTop: 0 }}>Trip Timeline</h4>
                  <div style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <div><b>10:15 AM</b>: Booking requested by Priya Sharma</div>
                    <div><b>10:15 AM</b>: Dispatched to Chauffeur Suresh Kumar (accepted in 4s)</div>
                    <div><b>10:19 AM</b>: Driver Arrived at CP Metro Gate 2</div>
                    <div><b>10:20 AM</b>: Start OTP 4821 verified</div>
                    <div><b>10:34 AM</b>: Drop completed at Khan Market · ₹145 Cash collected</div>
                  </div>
                </div>

                <div style={{ background: '#16161a', border: '1px solid #2a2a2e', padding: 18, borderRadius: 12 }}>
                  <h4 style={{ color: '#d4af37', marginTop: 0 }}>In-Ride Chat Transcript</h4>
                  <div style={{ fontSize: 12, background: '#0f0f12', padding: 10, borderRadius: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
                    <div style={{ color: '#f2ead3' }}><b>Priya:</b> Waiting near pillar 14 gate 2.</div>
                    <div style={{ color: '#d4af37' }}><b>Suresh (Driver):</b> Yes, approaching in Saarthi EV now.</div>
                  </div>
                  <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
                    <button onClick={() => setPopupConfirmRefund(true)} style={{ background: '#d4af37', color: '#000', border: 'none', borderRadius: 6, padding: '6px 12px', fontWeight: 700, fontSize: 11, cursor: 'pointer' }}>
                      Initiate Refund (01)
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* SCREEN 09: Driver List */}
          {selectedScreenId === 9 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h2 style={{ color: '#d4af37', margin: 0 }}>09 · Chauffeur Partner Directory</h2>
                <button onClick={() => navigateTo(10)} style={{ background: '#d4af37', color: '#000', border: 'none', borderRadius: 8, padding: '6px 14px', fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                  Onboarding Queue (10) →
                </button>
              </div>

              <div style={{ background: '#16161a', border: '1px solid #2a2a2e', borderRadius: 12, overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                  <thead>
                    <tr style={{ background: '#0f0f12', color: '#8d8672', borderBottom: '1px solid #2a2a2e', textAlign: 'left' }}>
                      <th style={{ padding: '10px 14px' }}>Chauffeur</th>
                      <th style={{ padding: '10px 14px' }}>Plate & Model</th>
                      <th style={{ padding: '10px 14px' }}>Rating</th>
                      <th style={{ padding: '10px 14px' }}>Wallet Balance</th>
                      <th style={{ padding: '10px 14px' }}>Status</th>
                      <th style={{ padding: '10px 14px' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr style={{ borderBottom: '1px solid #1f1f24' }}>
                      <td style={{ padding: '10px 14px', color: '#f2ead3', fontWeight: 700 }}>Suresh Kumar</td>
                      <td style={{ padding: '10px 14px', color: '#d4af37' }}>DL-1ER-4821 (Saarthi EV)</td>
                      <td style={{ padding: '10px 14px' }}>4.88 ★ (320 trips)</td>
                      <td style={{ padding: '10px 14px', color: '#d4af37', fontWeight: 700 }}>₹ 2,450</td>
                      <td style={{ padding: '10px 14px' }}><span style={{ color: '#10b981', background: '#10b98115', padding: '2px 8px', borderRadius: 4, fontWeight: 700 }}>Active Online</span></td>
                      <td style={{ padding: '10px 14px', display: 'flex', gap: 6 }}>
                        <button onClick={() => navigateTo(12)} style={{ background: '#222', color: '#d4af37', border: '1px solid #333', padding: '4px 8px', borderRadius: 4, cursor: 'pointer', fontSize: 11 }}>
                          Profile (12)
                        </button>
                        <button onClick={() => setPopupWalletAdjust(true)} style={{ background: '#222', color: '#f2ead3', border: '1px solid #333', padding: '4px 8px', borderRadius: 4, cursor: 'pointer', fontSize: 11 }}>
                          Adjust Wallet (03)
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* SCREEN 11: Document Review */}
          {selectedScreenId === 11 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <h2 style={{ color: '#d4af37', margin: 0 }}>11 · Chauffeur Document Audit & Verification</h2>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 14 }}>
                <div style={{ background: '#16161a', border: '1px solid #2a2a2e', padding: 16, borderRadius: 12 }}>
                  <div style={{ color: '#d4af37', fontWeight: 700, marginBottom: 8 }}>Commercial Driving License</div>
                  <div style={{ height: 140, background: '#0f0f12', borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#8d8672', border: '1px dashed #444' }}>
                    [DL-0420188921 Preview]
                  </div>
                  <div style={{ fontSize: 11, color: '#10b981', marginTop: 8 }}>Verified with Sarathi Parivahan API</div>
                </div>

                <div style={{ background: '#16161a', border: '1px solid #2a2a2e', padding: 16, borderRadius: 12 }}>
                  <div style={{ color: '#d4af37', fontWeight: 700, marginBottom: 8 }}>Vehicle Registration (RC)</div>
                  <div style={{ height: 140, background: '#0f0f12', borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#8d8672', border: '1px dashed #444' }}>
                    [Commercial EV RC Preview]
                  </div>
                  <div style={{ fontSize: 11, color: '#10b981', marginTop: 8 }}>Permit Active until 2029</div>
                </div>
              </div>

              <div style={{ display: 'flex', gap: 10, marginTop: 10 }}>
                <button
                  onClick={() => setPopupApproveDriver(true)}
                  style={{ background: 'linear-gradient(90deg, #10b981, #059669)', color: '#fff', border: 'none', borderRadius: 8, padding: '10px 20px', fontWeight: 800, cursor: 'pointer' }}
                >
                  Approve Chauffeur (04)
                </button>
                <button
                  onClick={() => setPopupRejectDocs(true)}
                  style={{ background: '#221111', color: '#ef4444', border: '1px solid #ef444455', borderRadius: 8, padding: '10px 20px', fontWeight: 700, cursor: 'pointer' }}
                >
                  Reject with Reason (05)
                </button>
              </div>
            </div>
          )}

          {/* SCREEN 20: Geofence Editor (Real Map) */}
          {selectedScreenId === 20 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <h2 style={{ color: '#d4af37', margin: 0 }}>20 · Interactive Geofence Editor</h2>
                  <p style={{ color: '#8d8672', fontSize: 12, margin: '4px 0 0' }}>Click map directly to plot multi-point polygon boundaries for operating zones.</p>
                </div>
                <button
                  onClick={() => setPopupDeleteGeofence(true)}
                  style={{ background: '#221111', color: '#ef4444', border: '1px solid #ef444455', borderRadius: 8, padding: '6px 12px', fontSize: 11, cursor: 'pointer' }}
                >
                  Delete Selected Geofence (07)
                </button>
              </div>

              <AdminRealMap height="540px" mode="geofence_editor" selectedScreenTitle="Geofence Polygon Studio" />
            </div>
          )}

          {/* SCREEN 21: Road Restrictions (Real Map) */}
          {selectedScreenId === 21 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <h2 style={{ color: '#d4af37', margin: 0 }}>21 · Road Restrictions & Safety Corridors</h2>
                  <p style={{ color: '#8d8672', fontSize: 12, margin: '4px 0 0' }}>Enforce restricted expressways, one-way zones, and waterlogging hazards for commercial E-Rickshaws.</p>
                </div>
              </div>

              <AdminRealMap height="540px" mode="road_restrictions" selectedScreenTitle="Restricted Road Vectors" />
            </div>
          )}

          {/* SCREEN 25: SOS Console */}
          {selectedScreenId === 25 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <h2 style={{ color: '#ef4444', margin: 0 }}>25 · Emergency SOS Incident Command Center</h2>
                  <p style={{ color: '#8d8672', fontSize: 12, margin: '4px 0 0' }}>Priority Fleet SOC desk directly connected with Government 112 Dispatch.</p>
                </div>
                <button
                  onClick={() => setPopupResolveSos(true)}
                  style={{ background: '#10b981', color: '#000', border: 'none', borderRadius: 8, padding: '8px 16px', fontWeight: 800, fontSize: 12, cursor: 'pointer' }}
                >
                  Resolve Alert (11)
                </button>
              </div>

              <div style={{ background: '#1c1111', border: '1px solid #ef4444', padding: 18, borderRadius: 12 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <b style={{ color: '#ef4444', fontSize: 14 }}>🚨 ALERT SOS-9912 ACTIVE</b>
                  <span style={{ fontSize: 12, color: '#f2ead3' }}>Dispatched: 2 mins ago</span>
                </div>
                <div style={{ fontSize: 12, color: '#f2ead3', marginTop: 8 }}>
                  <b>Driver:</b> Suresh Kumar (DL-1ER-4821) · Phone: +91 98765 43210
                </div>
                <div style={{ fontSize: 12, color: '#8d8672', marginTop: 2 }}>
                  <b>GPS Location:</b> 28.6315° N, 77.2167° E (Outer Circle Pillar 14, Connaught Place)
                </div>
                <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
                  <button onClick={() => window.open('tel:112')} style={{ background: '#ef4444', color: '#fff', border: 'none', borderRadius: 6, padding: '6px 14px', fontWeight: 700, fontSize: 11, cursor: 'pointer' }}>
                    Call Delhi Police 112
                  </button>
                  <button onClick={() => navigateTo(5)} style={{ background: '#222', color: '#d4af37', border: '1px solid #333', borderRadius: 6, padding: '6px 14px', fontSize: 11, cursor: 'pointer' }}>
                    Track on Real Map
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* FALLBACK FOR OTHER SCREENS */}
          {![1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 20, 21, 25].includes(selectedScreenId) && (
            <div style={{ background: '#16161a', border: '1px solid #2a2a2e', padding: 24, borderRadius: 14 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <div>
                  <h2 style={{ color: '#d4af37', margin: 0 }}>
                    {currentScreen.id.toString().padStart(2, '0')} · {currentScreen.title}
                  </h2>
                  <p style={{ color: '#8d8672', fontSize: 12, margin: '4px 0 0' }}>
                    Blueprint Specification: {currentScreen.desc}
                  </p>
                </div>
                <span style={{ background: 'rgba(212, 175, 55, 0.1)', color: '#d4af37', padding: '4px 10px', borderRadius: 6, fontSize: 11, fontWeight: 700 }}>
                  GROUP: {currentScreen.group.toUpperCase()}
                </span>
              </div>

              <div style={{ background: '#0f0f12', border: '1px solid #333', padding: 18, borderRadius: 10, fontSize: 13, color: '#f2ead3' }}>
                <p>Domain operations for <b>{currentScreen.title}</b> connected directly with Supabase live database tables.</p>
                <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
                  <button onClick={() => setToastSaved(true)} style={{ background: '#d4af37', color: '#000', border: 'none', borderRadius: 6, padding: '6px 14px', fontWeight: 700, fontSize: 11, cursor: 'pointer' }}>
                    Save Changes (16)
                  </button>
                  <button onClick={() => setPopupFilters(true)} style={{ background: '#16161a', border: '1px solid #333', color: '#8d8672', borderRadius: 6, padding: '6px 14px', fontSize: 11, cursor: 'pointer' }}>
                    Open Filters (18)
                  </button>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>

      {/* ========================================================= */}
      {/* ALL 20 ADMIN POPUPS IMPLEMENTATION (P.adm) */}
      {/* ========================================================= */}

      {/* 01: d|Confirm refund|Amount + reason|Approve/Cancel */}
      {popupConfirmRefund && (
        <AdminModal
          title="Confirm Fare Refund"
          onClose={() => setPopupConfirmRefund(false)}
        >
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <p>Refund Amount: <b style={{ color: '#d4af37' }}>₹ 145.00</b> to Priya Sharma (Rider)</p>
            <div style={{ marginTop: 10 }}>
              <label style={{ fontSize: 11, color: '#8d8672' }}>Mandatory Refund Reason (Logged to Audit Logs)</label>
              <textarea placeholder="e.g. Chauffeur route dispute or mechanical breakdown..." style={{ width: '100%', background: '#0f0f12', border: '1px solid #333', color: '#f2ead3', borderRadius: 8, padding: 8, marginTop: 4, height: 70 }} />
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupConfirmRefund(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupConfirmRefund(false); setToastSaved(true); }} style={{ background: '#d4af37', color: '#000', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Approve Refund</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 04: d|Approve driver|Docs verified|Approve/Reject */}
      {popupApproveDriver && (
        <AdminModal
          title="Approve Chauffeur Partner"
          onClose={() => setPopupApproveDriver(false)}
        >
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <p>Confirm that Aadhaar, Driving License, Commercial Permit and Vehicle RC have been manually verified against official Parivahan records.</p>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupApproveDriver(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupApproveDriver(false); setToastSaved(true); }} style={{ background: '#10b981', color: '#fff', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Approve & Unlock Cockpit</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 05: d|Reject documents|Rejection reason|Submit */}
      {popupRejectDocs && (
        <AdminModal
          title="Reject Chauffeur Documents"
          onClose={() => setPopupRejectDocs(false)}
        >
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <label style={{ fontSize: 11, color: '#8d8672' }}>Select Rejection Reason</label>
            <select style={{ width: '100%', background: '#0f0f12', border: '1px solid #333', color: '#f2ead3', padding: 8, borderRadius: 6, marginTop: 4 }}>
              <option>Vehicle Insurance Expired</option>
              <option>Blurry Commercial DL Image</option>
              <option>Aadhaar Address Mismatch</option>
              <option>Expired PUC Certificate</option>
            </select>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupRejectDocs(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Back</button>
              <button onClick={() => { setPopupRejectDocs(false); setToastSaved(true); }} style={{ background: '#ef4444', color: '#fff', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Submit Rejection</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 07: d|Delete geofence|Irreversible|Delete/Cancel */}
      {popupDeleteGeofence && (
        <AdminModal
          title="Delete Geofence Zone?"
          onClose={() => setPopupDeleteGeofence(false)}
        >
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <p style={{ color: '#ef4444', fontWeight: 700 }}>Warning: This action is permanent and cannot be undone.</p>
            <p>Deleting this geofence will immediately remove fare multipliers and dispatch routing restrictions from all live customer applications.</p>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupDeleteGeofence(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupDeleteGeofence(false); setToastSaved(true); }} style={{ background: '#ef4444', color: '#fff', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Confirm Delete</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 08: d|Publish surge|Zone + multiplier|Publish/Cancel */}
      {popupPublishSurge && (
        <AdminModal
          title="Publish Live Surge Multiplier"
          onClose={() => setPopupPublishSurge(false)}
        >
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <p>Target Zone: <b style={{ color: '#d4af37' }}>New Delhi Railway Station Corridor</b></p>
            <div style={{ marginTop: 8 }}>
              <label style={{ fontSize: 11, color: '#8d8672' }}>Multiplier Value</label>
              <input readOnly value="1.8x Surge Boost" style={{ width: '100%', background: '#0f0f12', border: '1px solid #d4af37', color: '#d4af37', padding: 8, borderRadius: 6, fontWeight: 700, marginTop: 4 }} />
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupPublishSurge(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupPublishSurge(false); setToastSaved(true); }} style={{ background: '#d4af37', color: '#000', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Publish Surge Now</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 10: d|Force cancel ride|Reason required|Confirm/Cancel */}
      {popupForceCancelRide && (
        <AdminModal
          title="Force Cancel Active Ride (#8492)"
          onClose={() => setPopupForceCancelRide(false)}
        >
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <p style={{ color: '#ef4444', fontWeight: 700 }}>Mandatory Admin Override</p>
            <textarea placeholder="Specify operational reason (e.g. breakdown, safety lock)..." style={{ width: '100%', background: '#0f0f12', border: '1px solid #333', color: '#f2ead3', borderRadius: 8, padding: 8, marginTop: 4, height: 70 }} />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupForceCancelRide(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupForceCancelRide(false); setToastSaved(true); }} style={{ background: '#ef4444', color: '#fff', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Confirm Force Cancel</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 11: d|Resolve SOS|Add notes|Resolve */}
      {popupResolveSos && (
        <AdminModal
          title="Resolve Emergency SOS (SOS-9912)"
          onClose={() => setPopupResolveSos(false)}
        >
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <p>Verification with Delhi Police 112 and Chauffeur Suresh Kumar completed.</p>
            <textarea placeholder="Enter Fleet SOC incident resolution notes..." style={{ width: '100%', background: '#0f0f12', border: '1px solid #333', color: '#f2ead3', borderRadius: 8, padding: 8, marginTop: 4, height: 70 }} />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupResolveSos(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupResolveSos(false); setActiveSosAlert(null); setToastSaved(true); }} style={{ background: '#10b981', color: '#fff', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Close & Resolve SOS</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 12: b|Export data|CSV / PDF|Export */}
      {popupExportData && (
        <AdminModal
          title="Export System Data"
          onClose={() => setPopupExportData(false)}
        >
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <p>Select export format for current table ({currentScreen.title}):</p>
            <div style={{ display: 'flex', gap: 10, marginTop: 10 }}>
              <button onClick={() => { setPopupExportData(false); setToastSaved(true); }} style={{ flex: 1, background: '#16161a', border: '1px solid #d4af37', color: '#d4af37', padding: '12px', borderRadius: 8, fontWeight: 700, cursor: 'pointer' }}>
                Download CSV Spreadsheet
              </button>
              <button onClick={() => { setPopupExportData(false); setToastSaved(true); }} style={{ flex: 1, background: '#16161a', border: '1px solid #d4af37', color: '#d4af37', padding: '12px', borderRadius: 8, fontWeight: 700, cursor: 'pointer' }}>
                Download PDF Report
              </button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 15: x|Session timeout|Login again|Login */}
      {popupSessionTimeout && (
        <div style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(0, 0, 0, 0.95)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 99999
        }}>
          <div style={{ background: '#16161a', border: '1px solid #d4af37', borderRadius: 16, padding: 30, maxWidth: 400, textAlign: 'center' }}>
            <div style={{ width: 60, height: 60, borderRadius: '50%', background: '#221111', color: '#ef4444', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px', fontSize: 24, fontWeight: 900 }}>
              !
            </div>
            <h3 style={{ color: '#d4af37', margin: 0 }}>Session Expired</h3>
            <p style={{ color: '#8d8672', fontSize: 12, marginTop: 8 }}>Due to RBAC security inactivity timeout, your privileged session has expired.</p>
            <button
              onClick={() => {
                setPopupSessionTimeout(false);
                navigateTo(1);
              }}
              style={{ background: 'linear-gradient(90deg, #b8962e, #d4af37)', color: '#000', border: 'none', borderRadius: 8, padding: '10px 24px', fontWeight: 800, marginTop: 16, cursor: 'pointer', width: '100%' }}
            >
              Login Again (01)
            </button>
          </div>
        </div>
      )}

      {/* 16: t|Saved|Changes saved|— (Toast) */}
      {toastSaved && (
        <div style={{
          position: 'fixed',
          bottom: 24,
          right: 24,
          background: '#16161a',
          border: '1px solid #10b981',
          color: '#10b981',
          borderRadius: 8,
          padding: '10px 18px',
          fontWeight: 700,
          fontSize: 12,
          boxShadow: '0 4px 16px rgba(16, 185, 129, 0.3)',
          zIndex: 9999
        }}>
          ✓ Changes Successfully Saved to Supabase
        </div>
      )}

      {/* 17: t|Permission denied|Contact superadmin|— (Toast) */}
      {toastPermissionDenied && (
        <div style={{
          position: 'fixed',
          bottom: 24,
          right: 24,
          background: '#16161a',
          border: '1px solid #ef4444',
          color: '#ef4444',
          borderRadius: 8,
          padding: '10px 18px',
          fontWeight: 700,
          fontSize: 12,
          boxShadow: '0 4px 16px rgba(239, 68, 68, 0.3)',
          zIndex: 9999
        }}>
          ⚠ Permission Denied: Action restricted to Superadmin Role
        </div>
      )}

      {/* 18: b|Filters|Date, status, city|Apply/Reset */}
      {popupFilters && (
        <AdminModal
          title="Data Range & Domain Filters"
          onClose={() => setPopupFilters(false)}
        >
          <div style={{ fontSize: 12, color: '#f2ead3', display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div>
              <label style={{ color: '#8d8672' }}>Date Range</label>
              <select style={{ width: '100%', background: '#0f0f12', border: '1px solid #333', color: '#f2ead3', padding: 6, borderRadius: 6, marginTop: 4 }}>
                <option>Today (Live Operations)</option>
                <option>Yesterday</option>
                <option>Last 7 Days</option>
                <option>October 2026</option>
              </select>
            </div>
            <div>
              <label style={{ color: '#8d8672' }}>Operation City</label>
              <select style={{ width: '100%', background: '#0f0f12', border: '1px solid #333', color: '#f2ead3', padding: 6, borderRadius: 6, marginTop: 4 }}>
                <option>Delhi NCR (Central Zone)</option>
                <option>Noida Metro Feeder Zone</option>
                <option>Gurugram Cyber Corridor</option>
              </select>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}>
              <button onClick={() => setPopupFilters(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Reset</button>
              <button onClick={() => { setPopupFilters(false); setToastSaved(true); }} style={{ background: '#d4af37', color: '#000', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Apply Filters</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 02: d|Ban user|Reason required|Ban/Cancel */}
      {popupBanUser && (
        <AdminModal title="Suspend/Ban User Account" onClose={() => setPopupBanUser(false)}>
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <p style={{ color: '#ef4444', fontWeight: 700 }}>Mandatory Security & Conduct Action</p>
            <textarea placeholder="Enter official suspension reason (logged to audit_logs)..." style={{ width: '100%', background: '#0f0f12', border: '1px solid #333', color: '#f2ead3', borderRadius: 8, padding: 8, marginTop: 4, height: 70 }} />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupBanUser(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupBanUser(false); setToastSaved(true); }} style={{ background: '#ef4444', color: '#fff', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Confirm Ban</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 03: d|Wallet adjust|Amount + note|Apply/Cancel */}
      {popupWalletAdjust && (
        <AdminModal title="Manual Wallet Balance Adjustment" onClose={() => setPopupWalletAdjust(false)}>
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <label style={{ fontSize: 11, color: '#8d8672' }}>Adjustment Amount (₹)</label>
            <input defaultValue="+250" style={{ width: '100%', background: '#0f0f12', border: '1px solid #d4af37', color: '#d4af37', padding: 8, borderRadius: 6, fontWeight: 700, marginTop: 4 }} />
            <label style={{ fontSize: 11, color: '#8d8672', marginTop: 10, display: 'block' }}>Audit Justification Note</label>
            <input defaultValue="Weekend Quest Campaign Bonus" style={{ width: '100%', background: '#0f0f12', border: '1px solid #333', color: '#f2ead3', padding: 8, borderRadius: 6, marginTop: 4 }} />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupWalletAdjust(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupWalletAdjust(false); setToastSaved(true); }} style={{ background: '#d4af37', color: '#000', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Apply Credit</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 06: b|Assign ticket|Pick agent|Assign */}
      {popupAssignTicket && (
        <AdminModal title="Assign Ticket to Support Agent" onClose={() => setPopupAssignTicket(false)}>
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <label style={{ fontSize: 11, color: '#8d8672' }}>Select Tier-2 Chauffeur Operations Agent</label>
            <select style={{ width: '100%', background: '#0f0f12', border: '1px solid #333', color: '#f2ead3', padding: 8, borderRadius: 6, marginTop: 4 }}>
              <option>Agent Priya (Fleet Support Tier-2)</option>
              <option>Agent Rahul (Disputes Specialist)</option>
              <option>Agent Neha (Onboarding Lead)</option>
            </select>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupAssignTicket(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupAssignTicket(false); setToastSaved(true); }} style={{ background: '#d4af37', color: '#000', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Assign Ticket</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 09: b|Send broadcast|Audience count|Send/Schedule */}
      {popupSendBroadcast && (
        <AdminModal title="Dispatch Fleet Broadcast Announcement" onClose={() => setPopupSendBroadcast(false)}>
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <p>Target Audience: <b style={{ color: '#d4af37' }}>142 Online Chauffeurs (Central Delhi)</b></p>
            <textarea defaultValue="High demand surge active around NDLS & CP! Move to hotspot for +1.8x fare boost." style={{ width: '100%', background: '#0f0f12', border: '1px solid #333', color: '#f2ead3', borderRadius: 8, padding: 8, marginTop: 4, height: 70 }} />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupSendBroadcast(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupSendBroadcast(false); setToastSaved(true); }} style={{ background: '#d4af37', color: '#000', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Dispatch Broadcast</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 13: d|Role change|Permission diff|Save/Cancel */}
      {popupRoleChange && (
        <AdminModal title="Modify Admin RBAC Role Permissions" onClose={() => setPopupRoleChange(false)}>
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <p>Target Administrator: <b style={{ color: '#d4af37' }}>operations_lead@luxury.in</b></p>
            <div style={{ background: '#0f0f12', padding: 10, borderRadius: 6, fontSize: 11, color: '#8d8672' }}>
              <div>+ Added: FARE_RULE_WRITE, SURGE_OVERRIDE</div>
              <div>- Removed: DB_TRUNCATE_PRIVILEGE</div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupRoleChange(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupRoleChange(false); setToastSaved(true); }} style={{ background: '#d4af37', color: '#000', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Save Permission Diff</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 14: d|MFA required|Enter OTP|Verify */}
      {popupMfaRequired && (
        <AdminModal title="Privileged Action: MFA Verification" onClose={() => setPopupMfaRequired(false)}>
          <div style={{ fontSize: 13, color: '#f2ead3', textAlign: 'center' }}>
            <p>Enter 6-digit TOTP code from your Google Authenticator or YubiKey.</p>
            <input defaultValue="849201" style={{ width: 180, textAlign: 'center', letterSpacing: 4, background: '#0f0f12', border: '1px solid #d4af37', color: '#d4af37', fontWeight: 800, fontSize: 18, padding: 8, borderRadius: 6, margin: '10px auto', display: 'block' }} />
            <div style={{ display: 'flex', justifyContent: 'center', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupMfaRequired(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupMfaRequired(false); setToastSaved(true); }} style={{ background: '#d4af37', color: '#000', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Verify & Proceed</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 19: d|Disable promo|Active users affected|Disable/Cancel */}
      {popupDisablePromo && (
        <AdminModal title="Deactivate Promo Code (LUXURY2026)" onClose={() => setPopupDisablePromo(false)}>
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <p style={{ color: '#f59e0b', fontWeight: 700 }}>420 active passengers currently hold this discount voucher in wallet.</p>
            <p>Deactivating will prevent new redemptions while preserving ongoing in-flight bookings.</p>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupDisablePromo(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupDisablePromo(false); setToastSaved(true); }} style={{ background: '#ef4444', color: '#fff', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Confirm Deactivation</button>
            </div>
          </div>
        </AdminModal>
      )}

      {/* 20: b|Approve payouts|Batch total|Approve */}
      {popupApprovePayouts && (
        <AdminModal title="Batch Instant Payout Approval" onClose={() => setPopupApprovePayouts(false)}>
          <div style={{ fontSize: 13, color: '#f2ead3' }}>
            <p>Batch Total: <b style={{ color: '#d4af37' }}>₹ 18,450.00</b> (12 Chauffeurs)</p>
            <p style={{ fontSize: 12, color: '#8d8672' }}>Will trigger automated IMPS banking webhook to RBI switch for instant settlement.</p>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setPopupApprovePayouts(false)} style={{ background: '#222', color: '#8d8672', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer' }}>Cancel</button>
              <button onClick={() => { setPopupApprovePayouts(false); setToastSaved(true); }} style={{ background: 'linear-gradient(90deg, #10b981, #059669)', color: '#fff', border: 'none', padding: '6px 16px', borderRadius: 6, fontWeight: 700, cursor: 'pointer' }}>Approve Batch (₹18,450)</button>
            </div>
          </div>
        </AdminModal>
      )}

    </div>
  );
};

// Reusable Modal Component
const AdminModal: React.FC<{ title: string; onClose: () => void; children: React.ReactNode }> = ({
  title,
  onClose,
  children
}) => {
  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(0, 0, 0, 0.75)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: 16,
      zIndex: 9999
    }}>
      <div style={{
        background: '#16161a',
        border: '1px solid #d4af37',
        borderRadius: 14,
        padding: 22,
        maxWidth: 460,
        width: '100%',
        boxShadow: '0 8px 32px rgba(0, 0, 0, 0.8)'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
          <b style={{ color: '#d4af37', fontSize: 15 }}>{title}</b>
          <button onClick={onClose} style={{ background: 'transparent', border: 'none', color: '#8d8672', fontSize: 16, cursor: 'pointer' }}>
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
};
