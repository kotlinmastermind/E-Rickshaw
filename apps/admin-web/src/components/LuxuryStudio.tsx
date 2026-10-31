import React, { useState } from 'react';
import {
  Smartphone, Car, Layers, BookOpen, Search,
  Play, Database, LogOut
} from 'lucide-react';
import { ScreenItem, parseScreens } from '../data/screensData';
import { AdminCrudModal } from './AdminCrudModal';
import { DeviceSimulatorModal } from './DeviceSimulatorModal';
import { InteractivePopupPlayground } from './InteractivePopupPlayground';

interface LuxuryStudioProps {
  adminUser?: any;
  onSignOut: () => void;
  onOpenSignIn?: () => void;
  onSwitchMode?: () => void;
}

export const LuxuryStudio: React.FC<LuxuryStudioProps> = ({ adminUser, onSignOut, onOpenSignIn, onSwitchMode }) => {
  const [activeTab, setActiveTab] = useState<'cust' | 'drv' | 'adm' | 'pop' | 'nav'>('adm');
  const [searchTerm, setSearchTerm] = useState('');

  // Active Modals
  const [selectedAdminScreen, setSelectedAdminScreen] = useState<ScreenItem | null>(null);
  const [simulatorState, setSimulatorState] = useState<{ open: boolean; category: 'cust' | 'drv'; initialId: number }>({
    open: false,
    category: 'cust',
    initialId: 8
  });

  const custScreens = parseScreens('cust');
  const drvScreens = parseScreens('drv');
  const admScreens = parseScreens('adm');

  // Active screens for current tab
  const getCurrentScreens = (): ScreenItem[] => {
    if (activeTab === 'cust') return custScreens;
    if (activeTab === 'drv') return drvScreens;
    if (activeTab === 'adm') return admScreens;
    return [];
  };

  const filteredScreens = getCurrentScreens().filter(s => {
    if (!searchTerm) return true;
    const term = searchTerm.toLowerCase();
    const matchTitle = s.title.toLowerCase().includes(term);
    const matchElem = s.elements.some(e => e.toLowerCase().includes(term));
    const matchTable = s.tableTarget?.toLowerCase().includes(term);
    return matchTitle || matchElem || matchTable;
  });

  // Render Wireframe Card HTML matching the uploaded spec
  const renderWireframeContent = (s: ScreenItem) => {
    const isMap = s.type === 'm';
    const isList = s.type === 'l';
    const isForm = s.type === 'f';
    const isPrice = s.type === 'd';
    const isSplash = s.type === 's' || (!isMap && !isList && !isForm && !isPrice);

    const rows = s.elements.map((el, i) => (
      <div key={i} className="r">{el}</div>
    ));

    if (isMap) {
      return (
        <>
          <div className="map" />
          <div className="sheet">{rows}</div>
        </>
      );
    }

    if (isForm) {
      return (
        <>
          {s.elements.map((el, i) => (
            <div key={i} className="f">{el}</div>
          ))}
          <div className="b">Continue</div>
        </>
      );
    }

    if (isPrice) {
      return (
        <>
          <div className="big">₹ 128</div>
          {rows}
          <div className="b">{s.elements[s.elements.length - 1] || 'Confirm'}</div>
        </>
      );
    }

    if (isSplash) {
      return (
        <>
          <div className="c">{s.title.toUpperCase()}</div>
          {s.elements.map((el, i) => (
            <div key={i} className="ch" style={{ alignSelf: 'center' }}>{el}</div>
          ))}
        </>
      );
    }

    // Default list
    return rows;
  };

  const navItems = activeTab === 'cust'
    ? ['Home', 'Rides', 'Wallet', 'Account']
    : activeTab === 'drv'
    ? ['Home', 'Earnings', 'Goals', 'Profile']
    : null;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--t)', display: 'flex', flexDirection: 'column' }}>
      {/* Top Sovereign Luxury Header */}
      <header style={{
        background: '#121216',
        borderBottom: '1px solid #222226',
        padding: '12px 20px',
        display: 'flex',
        flexWrap: 'wrap',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: 12
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{
            background: 'linear-gradient(135deg, #b8962e, #d4af37)',
            color: '#0b0b0d',
            fontWeight: 800,
            fontSize: 16,
            width: 36,
            height: 36,
            borderRadius: 10,
            display: 'grid',
            placeItems: 'center',
            letterSpacing: '0.05em'
          }}>
            ER
          </div>
          <div>
            <div style={{ color: '#d4af37', fontWeight: 700, fontSize: 16, letterSpacing: '0.03em' }}>
              E-RICKSHAW PLATFORM • LUXURY OS
            </div>
            <div style={{ color: '#8d8672', fontSize: 11 }}>
              130 Luxury Screens • 68 Active Tables • Full Responsive CRUD
            </div>
          </div>
        </div>

        {/* Status Indicators & Search */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            background: 'rgba(16,185,129,0.1)',
            border: '1px solid rgba(16,185,129,0.3)',
            borderRadius: 20,
            padding: '4px 12px',
            fontSize: 11,
            color: '#10b981'
          }}>
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: '#10b981' }} />
            <span>Supabase 68 Tables • Online</span>
          </div>

          <div style={{ position: 'relative' }}>
            <Search size={14} style={{ position: 'absolute', left: 10, top: 10, color: '#8d8672' }} />
            <input
              type="text"
              placeholder="Search 130 screens or tables..."
              value={searchTerm}
              onChange={e => setSearchTerm(e.target.value)}
              style={{
                background: '#16161a',
                border: '1px solid #2a2a2e',
                borderRadius: 20,
                padding: '6px 12px 6px 30px',
                color: '#f2ead3',
                fontSize: 12,
                width: 220
              }}
            />
          </div>

          {adminUser ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {onSwitchMode && (
                <button
                  onClick={onSwitchMode}
                  style={{
                    background: '#16161a',
                    border: '1px solid #333',
                    color: '#8d8672',
                    padding: '5px 10px',
                    borderRadius: 14,
                    fontSize: 11,
                    cursor: 'pointer'
                  }}
                >
                  Classic Console
                </button>
              )}
              <span style={{ fontSize: 12, color: '#d4af37', fontWeight: 600 }}>
                {adminUser.full_name || 'Admin'}
              </span>
              <button
                onClick={onSignOut}
                title="Sign out of Admin Session"
                style={{
                  background: '#1c1c22',
                  border: '1px solid #333',
                  color: '#8d8672',
                  borderRadius: '50%',
                  width: 32,
                  height: 32,
                  display: 'grid',
                  placeItems: 'center',
                  cursor: 'pointer'
                }}
              >
                <LogOut size={14} />
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {onOpenSignIn && (
                <button
                  onClick={onOpenSignIn}
                  style={{
                    background: 'linear-gradient(90deg, #b8962e, #d4af37)',
                    border: 'none',
                    color: '#0b0b0d',
                    padding: '6px 14px',
                    borderRadius: 20,
                    fontSize: 11,
                    fontWeight: 700,
                    cursor: 'pointer'
                  }}
                >
                  🔐 Admin Sign In
                </button>
              )}
            </div>
          )}
        </div>
      </header>

      {/* Main Tab Bar matching spec */}
      <nav className="luxury-tabs">
        <button
          className={activeTab === 'adm' ? 'on' : ''}
          onClick={() => setActiveTab('adm')}
        >
          <Database size={14} /> Admin Operations & Live CRUD (30)
        </button>
        <button
          className={activeTab === 'cust' ? 'on' : ''}
          onClick={() => setActiveTab('cust')}
        >
          <Smartphone size={14} /> Customer (50)
        </button>
        <button
          className={activeTab === 'drv' ? 'on' : ''}
          onClick={() => setActiveTab('drv')}
        >
          <Car size={14} /> Driver Cockpit (50)
        </button>
        <button
          className={activeTab === 'pop' ? 'on' : ''}
          onClick={() => setActiveTab('pop')}
        >
          <Layers size={14} /> Popups Matrix (68)
        </button>
        <button
          className={activeTab === 'nav' ? 'on' : ''}
          onClick={() => setActiveTab('nav')}
        >
          <BookOpen size={14} /> Navigation Rules & Priority
        </button>
      </nav>

      {/* Tab Context Subtitle */}
      <div style={{ padding: '8px 20px 0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p className="s" style={{ padding: 0 }}>
          {activeTab === 'adm' && '30 Production Admin Screens mapped to live PostgreSQL tables with full CRUD actions.'}
          {activeTab === 'cust' && '50 Customer Screens with Rapido-style linear booking lifecycle and floating SOS.'}
          {activeTab === 'drv' && '50 Driver Screens featuring authoritative 20s dispatch overlay and safety locks.'}
          {activeTab === 'pop' && '68 Popups Matrix categorized by Dialogs, Bottom Sheets, Fullscreen, and Toasts.'}
          {activeTab === 'nav' && 'Complete architectural navigation rules, priority queues, and state machine flows.'}
        </p>

        {(activeTab === 'cust' || activeTab === 'drv') && (
          <button
            onClick={() => setSimulatorState({
              open: true,
              category: activeTab,
              initialId: activeTab === 'cust' ? 8 : 10
            })}
            style={{
              background: 'linear-gradient(90deg, #b8962e, #d4af37)',
              color: '#0b0b0d',
              border: 'none',
              padding: '6px 14px',
              borderRadius: 20,
              fontSize: 12,
              fontWeight: 700,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 6
            }}
          >
            <Play size={12} fill="#0b0b0d" /> Launch Interactive Phone Simulator
          </button>
        )}
      </div>

      {/* Main View Area */}
      <main style={{ flex: 1, paddingBottom: 40 }}>
        {/* VIEW 1: ADMIN OPERATIONS & LIVE CRUD (30 Screens) */}
        {activeTab === 'adm' && (
          <div className="grid ad-grid">
            {filteredScreens.map(s => (
              <div
                key={s.id}
                className="ph ad"
                onClick={() => setSelectedAdminScreen(s)}
                style={{ cursor: 'pointer' }}
              >
                <div className="top">
                  <span>{s.title}</span>
                  <i>Screen {String(s.id).padStart(2, '0')}</i>
                </div>

                <div className="body">
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 2 }}>
                    <span style={{ fontSize: 10, color: '#d4af37', background: 'rgba(212,175,55,0.1)', padding: '2px 6px', borderRadius: 6, fontWeight: 700 }}>
                      TABLE: {s.tableTarget || 'SYSTEM'}
                    </span>
                    <span style={{ fontSize: 9.5, color: '#8d8672' }}>
                      {s.crudActions?.map(a => a.toUpperCase()).join(' • ')}
                    </span>
                  </div>
                  {renderWireframeContent(s)}
                </div>

                {/* Card Overlay Actions on Hover */}
                <div className="card-overlay-actions">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setSelectedAdminScreen(s);
                    }}
                    style={{
                      background: 'linear-gradient(90deg, #b8962e, #d4af37)',
                      color: '#0b0b0d',
                      border: 'none',
                      borderRadius: 8,
                      padding: '8px 16px',
                      fontWeight: 700,
                      fontSize: 12,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6
                    }}
                  >
                    <Database size={13} /> Open Live CRUD Console
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* VIEW 2 & 3: CUSTOMER (50) & DRIVER (50) SCREENS */}
        {(activeTab === 'cust' || activeTab === 'drv') && (
          <div className="grid">
            {filteredScreens.map(s => (
              <div
                key={s.id}
                className="ph"
                onClick={() => setSimulatorState({
                  open: true,
                  category: activeTab,
                  initialId: s.id
                })}
                style={{ cursor: 'pointer' }}
              >
                <div className="top">
                  <span>{s.title}</span>
                  <i>{s.id}</i>
                </div>

                <div className="body">
                  {renderWireframeContent(s)}
                </div>

                {/* Bottom Nav Simulation */}
                {navItems && (s.type === 'm' || s.title.toLowerCase().includes('wallet') || s.title.toLowerCase().includes('history') || s.title.toLowerCase().includes('home')) && (
                  <div className="bn">
                    {navItems.map((n, j) => (
                      <span key={j} style={{ color: j === 0 ? '#d4af37' : '#8d8672', fontWeight: j === 0 ? 700 : 400 }}>
                        {n}
                      </span>
                    ))}
                  </div>
                )}

                {/* Hover Play Button */}
                <div className="card-overlay-actions">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setSimulatorState({
                        open: true,
                        category: activeTab,
                        initialId: s.id
                      });
                    }}
                    style={{
                      background: 'linear-gradient(90deg, #b8962e, #d4af37)',
                      color: '#0b0b0d',
                      border: 'none',
                      borderRadius: 8,
                      padding: '8px 14px',
                      fontWeight: 700,
                      fontSize: 11,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6
                    }}
                  >
                    <Play size={12} fill="#0b0b0d" /> Test in Phone Shell
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* VIEW 4: POPUPS MATRIX (68 Popups) */}
        {activeTab === 'pop' && (
          <div style={{ padding: '16px' }}>
            <InteractivePopupPlayground />
          </div>
        )}

        {/* VIEW 5: NAVIGATION RULES & PRIORITY */}
        {activeTab === 'nav' && (
          <div className="sec">
            <h2>Navigation Rules & Operational Architecture</h2>

            <div style={{
              background: '#16161a',
              border: '1px solid #d4af37',
              borderRadius: 12,
              padding: 16,
              marginBottom: 20
            }}>
              <h3 style={{ margin: '0 0 10px', color: '#d4af37' }}>Priority Queue Hierarchy</h3>
              <div style={{
                background: '#0b0b0d',
                padding: 12,
                borderRadius: 8,
                fontSize: 12,
                color: '#f2ead3',
                fontFamily: 'monospace',
                border: '1px solid #2a2a2e'
              }}>
                Session Timeout &gt; Emergency SOS &gt; Driver 20s Dispatch &gt; Fatigue / Fake-GPS &gt; Payment Errors &gt; Info Dialogs &gt; Toasts
              </div>
              <p style={{ margin: '8px 0 0', fontSize: 12, color: '#8d8672' }}>
                Only one blocking popup is shown at any moment. Higher-priority system alerts immediately preempt lower-priority dialogs, while non-blocking toasts auto-dismiss after 3 seconds.
              </p>
            </div>

            <h3>Customer App Lifecycle (50 Screens)</h3>
            <ul>
              <li><strong>4 Persistent Bottom Tabs:</strong> <code>Home</code> | <code>Rides</code> | <code>Wallet</code> | <code>Account</code>.</li>
              <li><strong>Linear Booking Flow:</strong> Home (08) → Search (09) → Choose Ride (12) → Confirm (16) → Searching (18) → Driver Found (19) → Live Ride (23) → Trip Summary (28) → Payment (29) → Rating (30).</li>
              <li><strong>Active Ride Isolation:</strong> During active rides (Screens 18-25), the bottom navigation is strictly hidden. A persistent bottom card displays driver ETA, vehicle plate, and live location.</li>
              <li><strong>Floating SOS Panic Button:</strong> Omnipresent on every live-ride screen with 1-tap police (112) and emergency contacts broadcast.</li>
              <li><strong>Back Button Protection:</strong> Tapping Back during an active ride displays an exit confirmation dialog and <strong>never cancels the ongoing trip</strong>.</li>
              <li><strong>Out-of-Zone / Restricted Roads:</strong> Verified prior to booking confirmation (Screens 17 & 21).</li>
            </ul>

            <h3>Driver Cockpit Lifecycle (50 Screens)</h3>
            <ul>
              <li><strong>4 Persistent Bottom Tabs:</strong> <code>Home</code> | <code>Earnings</code> | <code>Goals</code> | <code>Profile</code>.</li>
              <li><strong>Onboarding Gatekeeper:</strong> Unapproved drivers only see Approval Tracker (07) and Document Upload (04); Home cockpit remains locked until KYC verification.</li>
              <li><strong>20-Second Incoming Ride Offer:</strong> Full-screen overlay with circular countdown ring; timeout automatically registers <code>is_accepted=false</code> in <code>ride_assignments</code>.</li>
              <li><strong>Authoritative Ride State Machine:</strong> Accept → Navigate → Arrived → 4-Digit OTP Verified → Start → Drop → Payment Collection → Rate Customer. Skips are strictly prohibited.</li>
              <li><strong>Safety Limits:</strong> Continuous online time &gt; 8 hours enforces a mandatory 20-minute fatigue rest countdown lock. Fake-GPS spoofing locks online mode immediately.</li>
            </ul>

            <h3>Admin Operations Website (30 Screens)</h3>
            <ul>
              <li><strong>Direct Table Mapping:</strong> All 30 admin screens map directly to active Supabase PostgreSQL tables.</li>
              <li><strong>Destructive Action Safeguards:</strong> Every deletion, refund, or suspension requires a mandatory justification note and writes permanently to <code>audit_logs</code>.</li>
              <li><strong>SOS Command Desk:</strong> Active emergency SOS alerts display a flashing red banner across all screens with 1-click access to the dispatch console.</li>
            </ul>
          </div>
        )}
      </main>

      {/* LIVE ADMIN CRUD MODAL */}
      {selectedAdminScreen && (
        <AdminCrudModal
          screen={selectedAdminScreen}
          onClose={() => setSelectedAdminScreen(null)}
          onAuditAction={(action, entity, reason) => {
            console.log(`Audit: ${action} on ${entity} - Reason: ${reason}`);
          }}
        />
      )}

      {/* INTERACTIVE DEVICE SIMULATOR MODAL */}
      {simulatorState.open && (
        <DeviceSimulatorModal
          screens={simulatorState.category === 'cust' ? custScreens : drvScreens}
          initialScreenId={simulatorState.initialId}
          category={simulatorState.category}
          onClose={() => setSimulatorState({ ...simulatorState, open: false })}
        />
      )}
    </div>
  );
};
