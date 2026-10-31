import React, { useState } from 'react';
import {
  Play, Database, LogOut, Search, Smartphone, Table,
  Layers, MapPin
} from 'lucide-react';
import { ScreenItem, parseScreens, ADMIN_CRUD_MAPPING } from '../data/screensData';
import { AdminCrudModal } from './AdminCrudModal';
import { DeviceSimulatorModal } from './DeviceSimulatorModal';
import { InteractivePopupPlayground } from './InteractivePopupPlayground';
import { AdminLuxuryFlowConsole } from './AdminLuxuryFlowConsole';

interface LuxuryStudioProps {
  adminUser?: any;
  onSignOut: () => void;
  onOpenSignIn?: () => void;
  onSwitchMode?: () => void;
}

export const LuxuryStudio: React.FC<LuxuryStudioProps> = ({ adminUser, onSignOut, onOpenSignIn, onSwitchMode }) => {
  // Default to Customer (50) per uploaded HTML spec: show('cust')
  const [activeTab, setActiveTab] = useState<'cust' | 'drv' | 'adm' | 'pop' | 'nav'>('cust');
  const [searchTerm, setSearchTerm] = useState('');

  // Primary Interactive View Modes (Phone / CRUD / Grid)
  const [custViewMode, setCustViewMode] = useState<'phone' | 'grid'>('phone');
  const [drvViewMode, setDrvViewMode] = useState<'phone' | 'grid'>('phone');
  const [admViewMode, setAdmViewMode] = useState<'blueprint' | 'crud' | 'grid'>('blueprint');

  // Selected Screen IDs for inline interactive view
  const [inlineCustScreenId, setInlineCustScreenId] = useState<number>(8); // Home map
  const [inlineDrvScreenId, setInlineDrvScreenId] = useState<number>(10); // Driver home
  const [inlineAdmScreenId, setInlineAdmScreenId] = useState<number>(16); // Vehicle categories

  // Standalone modal states if user clicks "Open in Modal"
  const [selectedAdminModalScreen, setSelectedAdminModalScreen] = useState<ScreenItem | null>(null);
  const [simulatorModalState, setSimulatorModalState] = useState<{ open: boolean; category: 'cust' | 'drv'; initialId: number }>({
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

  // Render Wireframe Card HTML matching the uploaded HTML spec
  const renderWireframeContent = (s: ScreenItem) => {
    const isMap = s.type === 'm';
    const isList = s.type === 'l';
    const isForm = s.type === 'f';
    const isPrice = s.type === 'd';
    const isPopup = s.type === 'p';
    const isSplash = s.type === 's' || (!isMap && !isList && !isForm && !isPrice && !isPopup);

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

    if (isList) {
      return (
        <>
          {rows}
          {rows}
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

    if (isPopup) {
      return (
        <>
          <div className="big">{s.elements[0]}</div>
          {rows}
          <div className="b">{s.elements[s.elements.length - 1] || 'Proceed'}</div>
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

    return rows;
  };

  // Bottom Navigation tabs per spec:
  // const NAV={cust:['Home','Rides','Wallet','Account'],drv:['Home','Earnings','Goals','Profile'],adm:null};
  const getCardNav = (s: ScreenItem) => {
    if (activeTab === 'cust') {
      const showNav = s.type === 'm' || s.title.startsWith('Wallet') || s.title.startsWith('Ride history');
      if (showNav) return ['Home', 'Rides', 'Wallet', 'Account'];
    } else if (activeTab === 'drv') {
      const showNav = s.type === 'm' || s.title.startsWith('Driver home') || s.title.startsWith('Trip history');
      if (showNav) return ['Home', 'Earnings', 'Goals', 'Profile'];
    }
    return null;
  };

  const selectedAdmScreen = admScreens.find(s => s.id === inlineAdmScreenId) || admScreens[15];

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--t)', display: 'flex', flexDirection: 'column' }}>
      {/* Top Header matching uploaded HTML */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        paddingRight: 16,
        borderBottom: '1px solid #1a1a20'
      }}>
        <div>
          <h1>E-Rickshaw Platform – Luxury UI</h1>
          <p className="s">Customer 50 · Driver 50 · Admin 30 · 70 popups · gold-on-black wireframes, Rapido-style flow</p>
        </div>

        {/* Search, Live DB Badge, Admin Auth Pill */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 16px', flexWrap: 'wrap' }}>
          <div style={{
            fontSize: 11,
            color: '#10b981',
            background: 'rgba(16,185,129,0.1)',
            border: '1px solid rgba(16,185,129,0.3)',
            padding: '4px 10px',
            borderRadius: 14,
            display: 'flex',
            alignItems: 'center',
            gap: 5
          }}>
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: '#10b981' }} />
            68 Supabase Tables Live
          </div>

          <div style={{ position: 'relative' }}>
            <Search size={14} style={{ position: 'absolute', left: 10, top: 9, color: '#8d8672' }} />
            <input
              type="text"
              placeholder="Search screens, tables..."
              value={searchTerm}
              onChange={e => setSearchTerm(e.target.value)}
              style={{
                background: '#16161a',
                border: '1px solid #2a2a2e',
                borderRadius: 20,
                padding: '6px 12px 6px 30px',
                color: '#f2ead3',
                fontSize: 12,
                width: 180
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
                  width: 30,
                  height: 30,
                  display: 'grid',
                  placeItems: 'center',
                  cursor: 'pointer'
                }}
              >
                <LogOut size={13} />
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
      </div>

      {/* Main Tab Navigation matching uploaded HTML spec */}
      <nav id="tabs" className="luxury-tabs">
        <button
          className={activeTab === 'cust' ? 'on' : ''}
          onClick={() => setActiveTab('cust')}
        >
          Customer (50)
        </button>
        <button
          className={activeTab === 'drv' ? 'on' : ''}
          onClick={() => setActiveTab('drv')}
        >
          Driver (50)
        </button>
        <button
          className={activeTab === 'adm' ? 'on' : ''}
          onClick={() => setActiveTab('adm')}
        >
          Admin (30)
        </button>
        <button
          className={activeTab === 'pop' ? 'on' : ''}
          onClick={() => setActiveTab('pop')}
        >
          Popups (68)
        </button>
        <button
          className={activeTab === 'nav' ? 'on' : ''}
          onClick={() => setActiveTab('nav')}
        >
          Navigation rules
        </button>
      </nav>

      {/* Main View Area */}
      <main id="v" style={{ flex: 1, padding: '16px 20px', maxWidth: 1400, margin: '0 auto', width: '100%' }}>

        {/* VIEW 1: CUSTOMER (50 SCREENS) */}
        {activeTab === 'cust' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {/* View Controls Toolbar */}
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: 12,
              background: '#16161a',
              border: '1px solid #2a2a2e',
              padding: '10px 16px',
              borderRadius: 12
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 12, color: '#d4af37', fontWeight: 600 }}>Mode:</span>
                <button
                  onClick={() => setCustViewMode('phone')}
                  style={{
                    background: custViewMode === 'phone' ? '#d4af37' : '#0f0f12',
                    color: custViewMode === 'phone' ? '#000' : '#f2ead3',
                    border: '1px solid #333',
                    borderRadius: 16,
                    padding: '4px 12px',
                    fontSize: 11,
                    fontWeight: 600,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 5
                  }}
                >
                  <Smartphone size={13} /> Live Interactive Phone
                </button>
                <button
                  onClick={() => setCustViewMode('grid')}
                  style={{
                    background: custViewMode === 'grid' ? '#d4af37' : '#0f0f12',
                    color: custViewMode === 'grid' ? '#000' : '#f2ead3',
                    border: '1px solid #333',
                    borderRadius: 16,
                    padding: '4px 12px',
                    fontSize: 11,
                    fontWeight: 600,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 5
                  }}
                >
                  <Layers size={13} /> 50 Wireframe Cards Grid
                </button>
              </div>

              {/* Jump directly to screen */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 11, color: '#8d8672' }}>Jump to Screen:</span>
                <select
                  value={inlineCustScreenId}
                  onChange={e => setInlineCustScreenId(Number(e.target.value))}
                  style={{
                    background: '#0f0f12',
                    border: '1px solid #333',
                    color: '#f2ead3',
                    padding: '4px 10px',
                    borderRadius: 8,
                    fontSize: 11
                  }}
                >
                  {custScreens.map(s => (
                    <option key={s.id} value={s.id}>
                      {s.id.toString().padStart(2, '0')} - {s.title}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Content: Either Phone Simulator or Cards Grid */}
            {custViewMode === 'phone' ? (
              <DeviceSimulatorModal
                isInline={true}
                category="cust"
                screens={custScreens}
                initialScreenId={inlineCustScreenId}
              />
            ) : (
              <div className="grid">
                {filteredScreens.map(s => {
                  const cardNav = getCardNav(s);
                  return (
                    <div
                      key={s.id}
                      className="ph"
                      onClick={() => {
                        setInlineCustScreenId(s.id);
                        setCustViewMode('phone');
                      }}
                      style={{ cursor: 'pointer' }}
                    >
                      <div className="top">
                        <span>{s.title}</span>
                        <i>{s.id}</i>
                      </div>

                      <div className="body">
                        {renderWireframeContent(s)}
                      </div>

                      {cardNav && (
                        <div className="bn">
                          {cardNav.map((n, j) => (
                            j === 0 ? <b key={j}>{n}</b> : <span key={j}>{n}</span>
                          ))}
                        </div>
                      )}

                      <div className="card-overlay-actions">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setInlineCustScreenId(s.id);
                            setCustViewMode('phone');
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
                            gap: 5
                          }}
                        >
                          <Play size={12} fill="#0b0b0d" /> Open in Phone
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* VIEW 2: DRIVER (50 SCREENS) */}
        {activeTab === 'drv' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {/* View Controls Toolbar */}
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: 12,
              background: '#16161a',
              border: '1px solid #2a2a2e',
              padding: '10px 16px',
              borderRadius: 12
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 12, color: '#d4af37', fontWeight: 600 }}>Mode:</span>
                <button
                  onClick={() => setDrvViewMode('phone')}
                  style={{
                    background: drvViewMode === 'phone' ? '#d4af37' : '#0f0f12',
                    color: drvViewMode === 'phone' ? '#000' : '#f2ead3',
                    border: '1px solid #333',
                    borderRadius: 16,
                    padding: '4px 12px',
                    fontSize: 11,
                    fontWeight: 600,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 5
                  }}
                >
                  <Smartphone size={13} /> Live Driver Cockpit
                </button>
                <button
                  onClick={() => setDrvViewMode('grid')}
                  style={{
                    background: drvViewMode === 'grid' ? '#d4af37' : '#0f0f12',
                    color: drvViewMode === 'grid' ? '#000' : '#f2ead3',
                    border: '1px solid #333',
                    borderRadius: 16,
                    padding: '4px 12px',
                    fontSize: 11,
                    fontWeight: 600,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 5
                  }}
                >
                  <Layers size={13} /> 50 Wireframe Cards Grid
                </button>
              </div>

              {/* Jump directly to driver screen */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 11, color: '#8d8672' }}>Jump to Screen:</span>
                <select
                  value={inlineDrvScreenId}
                  onChange={e => setInlineDrvScreenId(Number(e.target.value))}
                  style={{
                    background: '#0f0f12',
                    border: '1px solid #333',
                    color: '#f2ead3',
                    padding: '4px 10px',
                    borderRadius: 8,
                    fontSize: 11
                  }}
                >
                  {drvScreens.map(s => (
                    <option key={s.id} value={s.id}>
                      {s.id.toString().padStart(2, '0')} - {s.title}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Content: Either Phone Cockpit or Cards Grid */}
            {drvViewMode === 'phone' ? (
              <DeviceSimulatorModal
                isInline={true}
                category="drv"
                screens={drvScreens}
                initialScreenId={inlineDrvScreenId}
              />
            ) : (
              <div className="grid">
                {filteredScreens.map(s => {
                  const cardNav = getCardNav(s);
                  return (
                    <div
                      key={s.id}
                      className="ph"
                      onClick={() => {
                        setInlineDrvScreenId(s.id);
                        setDrvViewMode('phone');
                      }}
                      style={{ cursor: 'pointer' }}
                    >
                      <div className="top">
                        <span>{s.title}</span>
                        <i>{s.id}</i>
                      </div>

                      <div className="body">
                        {renderWireframeContent(s)}
                      </div>

                      {cardNav && (
                        <div className="bn">
                          {cardNav.map((n, j) => (
                            j === 0 ? <b key={j}>{n}</b> : <span key={j}>{n}</span>
                          ))}
                        </div>
                      )}

                      <div className="card-overlay-actions">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setInlineDrvScreenId(s.id);
                            setDrvViewMode('phone');
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
                            gap: 5
                          }}
                        >
                          <Play size={12} fill="#0b0b0d" /> Open Cockpit
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* VIEW 3: ADMIN OPERATIONS & LIVE CRUD (30 Screens) */}
        {activeTab === 'adm' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {/* View Controls & Table Selector Toolbar */}
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: 12,
              background: '#16161a',
              border: '1px solid #2a2a2e',
              padding: '12px 18px',
              borderRadius: 12
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ fontSize: 12, color: '#d4af37', fontWeight: 600 }}>Console View:</span>
                <button
                  onClick={() => setAdmViewMode('blueprint')}
                  style={{
                    background: admViewMode === 'blueprint' ? '#d4af37' : '#0f0f12',
                    color: admViewMode === 'blueprint' ? '#000' : '#f2ead3',
                    border: '1px solid #333',
                    borderRadius: 16,
                    padding: '5px 14px',
                    fontSize: 11,
                    fontWeight: 700,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6
                  }}
                >
                  <MapPin size={13} /> 30-Screen Blueprint & Real Map Console
                </button>
                <button
                  onClick={() => setAdmViewMode('crud')}
                  style={{
                    background: admViewMode === 'crud' ? '#d4af37' : '#0f0f12',
                    color: admViewMode === 'crud' ? '#000' : '#f2ead3',
                    border: '1px solid #333',
                    borderRadius: 16,
                    padding: '5px 14px',
                    fontSize: 11,
                    fontWeight: 700,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6
                  }}
                >
                  <Table size={13} /> Full Live CRUD Console
                </button>
                <button
                  onClick={() => setAdmViewMode('grid')}
                  style={{
                    background: admViewMode === 'grid' ? '#d4af37' : '#0f0f12',
                    color: admViewMode === 'grid' ? '#000' : '#f2ead3',
                    border: '1px solid #333',
                    borderRadius: 16,
                    padding: '5px 14px',
                    fontSize: 11,
                    fontWeight: 700,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6
                  }}
                >
                  <Layers size={13} /> 30 Wireframe Cards Grid
                </button>
              </div>

              {/* Table Selection Dropdown */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 11, color: '#8d8672' }}>Select Domain Table:</span>
                <select
                  value={inlineAdmScreenId}
                  onChange={e => setInlineAdmScreenId(Number(e.target.value))}
                  style={{
                    background: '#0f0f12',
                    border: '1px solid #333',
                    color: '#d4af37',
                    fontWeight: 600,
                    padding: '6px 12px',
                    borderRadius: 8,
                    fontSize: 11,
                    cursor: 'pointer'
                  }}
                >
                  {admScreens.map(s => {
                    const map = ADMIN_CRUD_MAPPING[s.id];
                    return (
                      <option key={s.id} value={s.id}>
                        {s.id.toString().padStart(2, '0')} - {s.title} ({map?.table || 'SYSTEM'})
                      </option>
                    );
                  })}
                </select>
              </div>
            </div>

            {/* Content: Either 30-Screen Blueprint & Real Map Console, Live CRUD Workbench or 30 Cards Grid */}
            {admViewMode === 'blueprint' ? (
              <AdminLuxuryFlowConsole
                adminUser={adminUser}
                onSignOut={onSignOut}
              />
            ) : admViewMode === 'crud' ? (
              <AdminCrudModal
                isInline={true}
                screen={selectedAdmScreen}
              />
            ) : (
              <div className="grid ad-grid">
                {filteredScreens.map(s => {
                  const mapping = ADMIN_CRUD_MAPPING[s.id];
                  return (
                    <div
                      key={s.id}
                      className="ph ad"
                      onClick={() => {
                        setInlineAdmScreenId(s.id);
                        setAdmViewMode('crud');
                      }}
                      style={{ cursor: 'pointer' }}
                    >
                      <div className="top">
                        <span>{s.title}</span>
                        <i>{s.id}</i>
                      </div>

                      <div className="body">
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 2 }}>
                          <span style={{ fontSize: 9.5, color: '#d4af37', background: 'rgba(212,175,55,0.1)', padding: '2px 6px', borderRadius: 6, fontWeight: 700 }}>
                            TABLE: {mapping?.table?.toUpperCase() || s.tableTarget || 'SYSTEM'}
                          </span>
                          <span style={{ fontSize: 9.5, color: '#8d8672' }}>
                            {mapping?.actions?.map(a => a.toUpperCase()).join(' • ') || 'READ'}
                          </span>
                        </div>
                        {renderWireframeContent(s)}
                      </div>

                      <div className="card-overlay-actions">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setInlineAdmScreenId(s.id);
                            setAdmViewMode('crud');
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
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* VIEW 4: POPUPS MATRIX (68 Popups) */}
        {activeTab === 'pop' && (
          <div style={{ padding: '8px 0' }}>
            <InteractivePopupPlayground />
          </div>
        )}

        {/* VIEW 5: NAVIGATION RULES (Exact HTML R text) */}
        {activeTab === 'nav' && (
          <div className="sec">
            <h2>Navigation rules</h2>
            <h3>Customer app</h3>
            <ul>
              <li>Bottom nav (4 tabs): Home | Rides | Wallet | Account.</li>
              <li>Booking flow is linear: Home → Search → Choose ride → Confirm → Searching → Driver found → Live ride → Summary → Payment → Rating.</li>
              <li>During an active ride the bottom nav is hidden; a persistent ride card shows ETA and driver.</li>
              <li>SOS floating button on every live-ride screen.</li>
              <li>Back during a ride shows a confirm dialog and never cancels the ride.</li>
              <li>Driver not found in 3 retries → Offer fare bump or Try again.</li>
              <li>Out-of-zone / restricted road → alert before Confirm.</li>
            </ul>

            <h3>Driver app</h3>
            <ul>
              <li>Bottom nav (4 tabs): Home | Earnings | Goals | Profile.</li>
              <li>Unapproved driver sees only the Approval tracker, Home is locked.</li>
              <li>Ride request is a full-screen overlay with a 20s timer; timeout = auto-reject and ride_assignments.status updated.</li>
              <li>Trip state machine without skips: Accept → Navigate → Arrived → OTP → Start → Drop → Payment → Rate customer.</li>
              <li>Offline: Home map dims, requests stop. Fatigue limit forces the rest screen.</li>
              <li>Fake-GPS detection blocks going online until fixed.</li>
            </ul>

            <h3>Admin website</h3>
            <ul>
              <li>Left sidebar + top global search + role-based menu; items hidden without admin_permissions.</li>
              <li>Every destructive action (refund, ban, wallet adjust) needs a confirm modal and writes to audit_logs.</li>
              <li>Ride detail links to Driver, Customer, Payment and Chat; the SOS console takes priority with a red banner everywhere.</li>
            </ul>

            <h3>Popup rules (screens + popups combined)</h3>
            <ul>
              <li><b>4 popup types:</b> Dialog (centred, blocking, max 2 buttons), Bottom sheet (choices/forms, swipe-down or back to dismiss), Toast (3s auto-dismiss, never blocks), Full-screen overlay (critical, back and outside tap ignored).</li>
              <li><b>Priority:</b> Session expired / Force logout &gt; SOS &gt; Driver ride request &gt; Fatigue / Fake-GPS &gt; Payment and network errors &gt; Info dialogs &gt; Toasts. Only one blocking popup at a time; lower-priority ones queue, toasts never queue.</li>
              <li><b>Back press:</b> Dialog = same as secondary button; Sheet = dismiss; Full-screen = ignored. During an active ride, back never cancels the ride.</li>
              <li><b>Outside tap:</b> dismisses Sheets only. Destructive dialogs (Cancel ride, Logout, Ban, Delete) never close on outside tap.</li>
              <li><b>Route stack:</b> popups do not add to the back stack. Primary button either returns to the calling screen with a result or pushes the next screen (e.g. Driver arrived → Share OTP, Payment failed → Payment method).</li>
              <li><b>Flow triggers (Customer):</b> Pickup confirm sheet → Surge / Fare changed → Searching; No driver found dialog after 3 retries; Driver arrived overlay → Ride in progress; Ride ended → Payment → Tip sheet → Rate prompt.</li>
              <li><b>Flow triggers (Driver):</b> Go online dialog checks docs, GPS and fatigue; Ride request overlay (20s) → Navigate; Wrong OTP dialog blocks Start; Cash collect sheet → Rate customer; Fatigue overlay forces offline until the rest timer ends.</li>
              <li><b>Admin:</b> every destructive dialog asks for a reason and writes audit_logs; refunds above the set limit need MFA re-auth; Session timeout overlay clears the sidebar state.</li>
              <li><b>SOS:</b> Send-SOS dialog appears on Customer and Driver, a hold-to-confirm button prevents accidental triggers, and Admin receives a red banner plus the SOS console.</li>
              <li><b>Offline:</b> No-internet dialog only blocks network actions; the live ride and map keep working with the last known state.</li>
              <li><b>Every popup:</b> has a loading state on its primary button, an error toast on failure, and sends no analytics event until a button is tapped.</li>
            </ul>

            <h3>Common</h3>
            <ul>
              <li>Every screen has loading, empty and error states.</li>
              <li>Money is stored in cents and shown in ₹.</li>
              <li>Auth only via Firebase; Supabase is for data only.</li>
            </ul>
          </div>
        )}
      </main>

      {/* Standalone Modals if triggered */}
      {selectedAdminModalScreen && (
        <AdminCrudModal
          screen={selectedAdminModalScreen}
          onClose={() => setSelectedAdminModalScreen(null)}
        />
      )}

      {simulatorModalState.open && (
        <DeviceSimulatorModal
          category={simulatorModalState.category}
          screens={simulatorModalState.category === 'cust' ? custScreens : drvScreens}
          initialScreenId={simulatorModalState.initialId}
          onClose={() => setSimulatorModalState({ open: false, category: 'cust', initialId: 1 })}
        />
      )}
    </div>
  );
};
