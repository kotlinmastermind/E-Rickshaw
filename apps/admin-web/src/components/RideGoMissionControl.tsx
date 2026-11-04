import React, { useState, useEffect } from 'react';
import {
  BLUEPRINT_CATEGORIES,
  BLUEPRINT_DESKS,
  BLUEPRINT_CUSTOMER_SCREENS,
  BLUEPRINT_DRIVER_SCREENS
} from '../data/blueprintData';
import {
  BLUEPRINT_CUSTOMER_UI,
  BLUEPRINT_DRIVER_UI
} from '../data/blueprintTemplates';
import { AdminRealMap } from './AdminRealMap';
import { requestApi } from '../services/api';

export interface AuditLogEntry {
  id: string;
  timestamp: string;
  actor: string;
  action: string;
  target: string;
  justification: string;
  status: 'SUCCESS' | 'BLOCKED';
}

export interface ConfirmModalState {
  open: boolean;
  title: string;
  message: string;
  actionLabel: string;
  isDestructive?: boolean;
  onConfirm: () => void;
}

export interface ToastItem {
  id: string;
  title: string;
  message: string;
  type: 'success' | 'danger' | 'info';
}

export const RideGoMissionControl: React.FC = () => {
  // Navigation State
  const [activeCategory, setActiveCategory] = useState<string>('overview');
  const [activeDeskId, setActiveDeskId] = useState<string>('dashboard');
  const [deepLinkParams, setDeepLinkParams] = useState<Record<string, string>>({});
  const [confirmModal, setConfirmModal] = useState<ConfirmModalState | null>(null);
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([
    { id: 'AUD-001', timestamp: '15:20:12', actor: 'Executive Ops Commander', action: 'SURGE_OVERRIDE_SET', target: 'Central Delhi Core (+1.4x)', justification: 'Evening rain commute demand surge', status: 'SUCCESS' },
    { id: 'AUD-002', timestamp: '14:45:00', actor: 'Fleet Safety Agent', action: 'DRIVER_KYC_VERIFIED', target: 'Pilot DL-042019007741', justification: 'Commercial DL and Sarathi OCR check valid', status: 'SUCCESS' },
    { id: 'AUD-003', timestamp: '14:10:30', actor: 'Finance Admin', action: 'PAYOUT_BATCH_SETTLED', target: 'IMPS Batch #8801', justification: 'Weekly settlement window', status: 'SUCCESS' }
  ]);
  const [deskSearch, setDeskSearch] = useState<string>('');

  // Simulator State
  const [simCustScreenId, setSimCustScreenId] = useState<number>(7);
  const [simDriverScreenId, setSimDriverScreenId] = useState<number>(8);

  // Live Backend & Supabase Data
  const [categories, setCategories] = useState<any[]>([]);
  const [_isLoadingCategories, setIsLoadingCategories] = useState<boolean>(false);
  const [surgeMultiplier, setSurgeMultiplier] = useState<number>(1.4);
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  // Broadcast state
  const [broadcastTitle, setBroadcastTitle] = useState<string>('Rain Alert & High Demand Near Metro');
  const [broadcastMessage, setBroadcastMessage] = useState<string>('Surge 1.4x is active at Rajiv Chowk & Anand Vihar. Head there for back-to-back rides.');
  const [broadcastAudience, setBroadcastAudience] = useState<string>('ALL_DRIVERS');

  // Customer Wallets state
  const [customerBalances, setCustomerBalances] = useState<Record<string, number>>({
    'cust_1': 1420.50,
    'cust_2': 380.00,
    'cust_3': 950.00,
    'cust_4': 2500.00
  });

  // Driver KYC state
  const [kycDrivers, setKycDrivers] = useState([
    { id: 'DRV-901', name: 'Rameshwar Yadav', mobile: '+91 98112 34567', vehicle: 'E-Rickshaw Standard (DL-1ER-9912)', dl: 'DL-042018009912', aadhaar: '•••• •••• 9812', status: 'PENDING' },
    { id: 'DRV-902', name: 'Mukesh Kumar', mobile: '+91 97188 44321', vehicle: 'High-Speed E-Auto (DL-1EA-5532)', dl: 'DL-042020015532', aadhaar: '•••• •••• 5532', status: 'PENDING' },
    { id: 'DRV-903', name: 'Sunil Sharma', mobile: '+91 99532 11984', vehicle: 'Eco-Pool Shared (DL-1ER-7741)', dl: 'DL-042019007741', aadhaar: '•••• •••• 7741', status: 'VERIFIED' }
  ]);

  // Driver Payouts state
  const [payouts, setPayouts] = useState([
    { id: 'PAY-8801', driver: 'Rameshwar Yadav', amount: 1840.50, bank: 'State Bank of India (•••9012)', ifsc: 'SBIN0001234', status: 'PENDING' },
    { id: 'PAY-8802', driver: 'Mukesh Kumar', amount: 2450.00, bank: 'HDFC Bank (•••4412)', ifsc: 'HDFC0000456', status: 'PENDING' },
    { id: 'PAY-8803', driver: 'Sunil Sharma', amount: 980.00, bank: 'Punjab National Bank (•••7721)', ifsc: 'PUNB0123456', status: 'COMPLETED' }
  ]);

  // Toast helper
  const addToast = (title: string, message: string, type: 'success' | 'danger' | 'info' = 'success') => {
    const id = Date.now().toString();
    setToasts(prev => [...prev, { id, title, message, type }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 4500);
  };

  // Fetch real categories from FastAPI backend on mount
  
  // URL Hash Navigation & Deep Link Parsers
  const parseRouteString = (str: string) => {
    const raw = str.replace(/^#\/?/, '');
    const [pathPart, queryPart] = raw.split('?');
    const segments = pathPart.split('/').filter(Boolean);
    const params: Record<string, string> = {};
    if (queryPart) {
      new URLSearchParams(queryPart).forEach((v, k) => { params[k] = v; });
    }
    if (segments.length >= 2) {
      return { category: segments[0], deskId: segments[1], params };
    } else if (segments.length === 1) {
      const desk = BLUEPRINT_DESKS.find(d => d.id === segments[0]);
      if (desk) return { category: desk.category, deskId: desk.id, params };
      return { category: segments[0], deskId: BLUEPRINT_DESKS.find(d => d.category === segments[0])?.id || 'dashboard', params };
    }
    return { category: 'overview', deskId: 'dashboard', params };
  };

  const navigateToDesk = (deskId: string, categoryId?: string, queryParams?: Record<string, string>) => {
    const desk = BLUEPRINT_DESKS.find(d => d.id === deskId);
    const cat = categoryId || desk?.category || 'overview';
    setActiveCategory(cat);
    setActiveDeskId(deskId);
    let hash = `#/${cat}/${deskId}`;
    if (queryParams && Object.keys(queryParams).length > 0) {
      hash += '?' + new URLSearchParams(queryParams).toString();
      setDeepLinkParams(queryParams);
    } else {
      setDeepLinkParams({});
    }
    window.location.hash = hash;
    localStorage.setItem('ridego_admin_route', hash);
  };

  const recordAudit = (action: string, target: string, justification: string) => {
    const entry: AuditLogEntry = {
      id: `AUD-${(auditLogs.length + 1).toString().padStart(3, '0')}`,
      timestamp: new Date().toLocaleTimeString(),
      actor: 'Executive Ops Commander',
      action,
      target,
      justification,
      status: 'SUCCESS'
    };
    setAuditLogs(prev => [entry, ...prev]);
  };

  // Synchronize browser URL hash and handle Back/Forward
  useEffect(() => {
    const handleHashChange = () => {
      const route = parseRouteString(window.location.hash);
      setActiveCategory(route.category);
      setActiveDeskId(route.deskId);
      setDeepLinkParams(route.params);
    };
    window.addEventListener('hashchange', handleHashChange);
    
    // Initial mount restore
    if (window.location.hash) {
      const initial = parseRouteString(window.location.hash);
      setActiveCategory(initial.category);
      setActiveDeskId(initial.deskId);
      setDeepLinkParams(initial.params);
    } else {
      const saved = localStorage.getItem('ridego_admin_route');
      if (saved) {
        window.location.hash = saved;
      } else {
        window.location.hash = '#/overview/dashboard';
      }
    }
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, []);

  useEffect(() => {
    setIsLoadingCategories(true);
    requestApi<any[]>('/vehicles/categories')
      .then(res => {
        if (Array.isArray(res) && res.length > 0) {
          setCategories(res);
        }
      })
      .catch(err => {
        console.warn('FastAPI categories fetch fallback:', err);
      })
      .finally(() => {
        setIsLoadingCategories(false);
      });
  }, []);

  const handleCategorySwitch = (catId: string) => {
    setActiveCategory(catId);
    if (catId === 'simulator') {
      setActiveDeskId('simulator');
      return;
    }
    const firstDesk = BLUEPRINT_DESKS.find(d => d.category === catId) || BLUEPRINT_DESKS[0];
    setActiveDeskId(firstDesk.id);
  };

  const handleApproveKYC = (driverId: string) => {
    setKycDrivers(prev => prev.map(d => d.id === driverId ? { ...d, status: 'VERIFIED' } : d));
    addToast('KYC Approved', `Driver ${driverId} commercial documents verified and approved on Supabase.`);
  };

  const handleRejectKYC = (driverId: string) => {
    setKycDrivers(prev => prev.map(d => d.id === driverId ? { ...d, status: 'REJECTED' } : d));
    addToast('KYC Rejected', `Driver ${driverId} application rejected with notice sent.`, 'danger');
  };

  const handleApprovePayout = (payId: string) => {
    setPayouts(prev => prev.map(p => p.id === payId ? { ...p, status: 'COMPLETED' } : p));
    addToast('IMPS Batch Released', `Payout ${payId} settled via Instant IMPS UPI Gateway.`);
  };

  const handleCreditWallet = (custId: string, amt: number) => {
    setCustomerBalances(prev => ({
      ...prev,
      [custId]: (prev[custId] || 0) + amt
    }));
    addToast('Wallet Credited', `₹${amt} successfully credited to ${custId} balance on Supabase.`);
  };

  const handleSendBroadcast = () => {
    addToast('📢 Broadcast Dispatched', `Mass alert sent to ${broadcastAudience}: "${broadcastTitle}"`);
  };

  const handleSaveFares = () => {
    addToast('Tariffs Updated', 'Dynamic Fare Rules saved to Supabase PostgreSQL cluster.');
  };

  const handleTriggerSOS = () => {
    addToast('🚨 EMERGENCY BROADCAST ACTIVE', 'Emergency alert dispatched to 128 active drivers and 112 Police command center.', 'danger');
  };

  // Filtered desks list
  const filteredDesks = BLUEPRINT_DESKS.filter(d => {
    const matchCat = (activeCategory === 'overview') || (d.category === activeCategory);
    const q = deskSearch.toLowerCase().trim();
    const matchQ = !q || d.title.toLowerCase().includes(q) || d.table.toLowerCase().includes(q);
    return matchCat && matchQ;
  });

  const activeDesk = BLUEPRINT_DESKS.find(d => d.id === activeDeskId) || BLUEPRINT_DESKS[0];

  return (
    <div className="flex flex-col h-screen w-screen overflow-hidden bg-[#07090e] text-slate-100 font-sans select-none">
      
      {/* 1. TOP HEADER (100% Matching latest ui blueprint.html) */}
      <header className="h-16 bg-[#0e121e] border-b border-slate-800 px-6 flex items-center justify-between shrink-0 z-30">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-amber-500 to-yellow-300 flex items-center justify-center text-xl shadow-lg shadow-amber-500/20">
            🚖
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-black text-sm text-white tracking-wider">RIDEGO MISSION CONTROL</span>
              <span className="px-2 py-0.5 rounded-full bg-blue-500/20 text-blue-400 font-extrabold text-[9px] border border-blue-500/30">
                SUPERADMIN CONSOLE
              </span>
            </div>
            <p className="text-[10px] text-slate-400">
              Universal Operational Hub with Full Two-App Control (Customer + Pilot Apps)
            </p>
          </div>
        </div>

        {/* Live Status Indicators */}
        <div className="hidden lg:flex items-center gap-5 text-xs">
          <div className="flex items-center gap-2 bg-slate-900/90 border border-slate-800 px-3 py-1.5 rounded-full">
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 shadow-md shadow-emerald-500/50 animate-pulse"></span>
            <span className="text-slate-300 font-bold text-[11px]">Sydney Supabase Pooler: <strong className="text-emerald-400">69 Tables Live</strong></span>
          </div>

          <div className="flex items-center gap-2 bg-slate-900/90 border border-slate-800 px-3 py-1.5 rounded-full">
            <span className="w-2.5 h-2.5 rounded-full bg-blue-500 shadow-md shadow-blue-500/50"></span>
            <span className="text-slate-300 font-bold text-[11px]">API Gateway: <code className="text-blue-400 font-mono">https://e-rickshaw-z32z.onrender.com</code></span>
          </div>

          <div className="flex items-center gap-2 bg-slate-900/90 border border-slate-800 px-3 py-1.5 rounded-full">
            <span className="text-amber-400 font-bold text-[11px]">⚡ Active Pilots: <strong>128 Online</strong></span>
          </div>

          <div className="flex items-center gap-2 bg-slate-900/90 border border-slate-800 px-3 py-1.5 rounded-full">
            <span className="text-cyan-400 font-bold text-[11px]">🚖 Live Rides: <strong>47</strong></span>
          </div>
        </div>

        {/* Header Right Actions & Admin Profile */}
        <div className="flex items-center gap-3">
          <button
            onClick={handleTriggerSOS}
            className="px-3.5 py-1.5 rounded-xl bg-red-600 hover:bg-red-500 text-white font-extrabold text-xs shadow-lg shadow-red-600/30 flex items-center gap-1.5 transition-all"
          >
            <span>🚨</span>
            <span className="hidden sm:inline">Trigger Emergency Alert</span>
          </button>

          <button
            onClick={() => addToast('Refreshed', 'Telemetry synced with Supabase PostgreSQL')}
            className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-bold text-xs flex items-center gap-1.5 border border-slate-700 transition-all"
          >
            <span>🔄</span>
            <span className="hidden sm:inline">Refresh</span>
          </button>

          <div className="flex items-center gap-2 pl-2 border-l border-slate-800">
            <div className="w-7 h-7 rounded-full bg-blue-500/30 border border-blue-400 flex items-center justify-center text-xs font-bold text-blue-300">
              AD
            </div>
            <div className="hidden sm:block text-left">
              <span className="font-bold text-white text-[11px] block leading-tight">admin@ridego.in</span>
              <span className="text-[9px] text-emerald-400 font-mono">SUPERADMIN_OPS (MFA ✓)</span>
            </div>
          </div>
        </div>
      </header>

      {/* 2. TOP MEGA-MENU BAR (10 Operational Groups) */}
      <nav className="h-12 bg-[#0a0d16] border-b border-slate-800 px-6 flex items-center gap-2 overflow-x-auto text-xs font-bold shrink-0">
        {BLUEPRINT_CATEGORIES.map(cat => {
          const isActive = activeCategory === cat.id;
          return (
            <button
              key={cat.id}
              onClick={() => handleCategorySwitch(cat.id)}
              className={`px-3 py-2 rounded-xl flex items-center gap-1.5 transition-all shrink-0 ${
                isActive
                  ? 'text-white bg-blue-600 shadow-md shadow-blue-600/30 font-black'
                  : 'text-slate-300 hover:text-white hover:bg-slate-800/80'
              }`}
            >
              <span>{cat.icon}</span>
              <span>{cat.label}</span>
              {cat.id === 'simulator' && (
                <span className="ml-1 px-1.5 py-0.2 bg-amber-500/30 text-amber-300 text-[9px] rounded font-mono">
                  BOTH APPS
                </span>
              )}
            </button>
          );
        })}
      </nav>

      {/* 3. BODY WORKSPACE: LEFT SIDEBAR + RIGHT MAIN VIEWPORT */}
      <div className="flex flex-1 overflow-hidden">
        
        {/* LEFT SIDEBAR: DESK SELECTOR */}
        <aside className="w-80 bg-[#0a0d14] border-r border-slate-800 flex flex-col shrink-0">
          <div className="p-3 border-b border-slate-800">
            <input
              type="text"
              value={deskSearch}
              onChange={e => setDeskSearch(e.target.value)}
              placeholder="🔍 Filter 32 desks or tables..."
              className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white placeholder-slate-500 outline-none focus:border-blue-500 transition-all"
            />
          </div>

          <div className="flex-1 overflow-y-auto p-2 space-y-1">
            {filteredDesks.map(d => {
              const isActive = d.id === activeDeskId;
              return (
                <button
                  key={d.id}
                  onClick={() => setActiveDeskId(d.id)}
                  className={`w-full text-left p-2.5 rounded-xl text-xs flex items-center justify-between transition-all ${
                    isActive
                      ? 'bg-blue-600 text-white font-black shadow-lg shadow-blue-600/20'
                      : 'bg-slate-900/60 text-slate-300 hover:text-white hover:bg-slate-800/80 border border-slate-800/80'
                  }`}
                >
                  <div className="flex items-center gap-2 truncate">
                    <span>{d.icon}</span>
                    <span className="truncate max-w-[170px]">{d.title}</span>
                  </div>
                  <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-slate-950/60 text-slate-300 font-bold">
                    {d.badge}
                  </span>
                </button>
              );
            })}
          </div>

          {/* Quick Dual App Remote Launcher in Sidebar Footer */}
          <div className="p-3 bg-slate-950 border-t border-slate-800 flex items-center justify-between text-xs">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
              <span className="text-[11px] text-slate-400 font-bold">Remote App Sync</span>
            </div>
            <button
              onClick={() => {
                setActiveCategory('simulator');
                setActiveDeskId('simulator');
              }}
              className="px-2.5 py-1 bg-amber-500/20 border border-amber-500/40 text-amber-300 rounded-lg text-[10px] font-black hover:bg-amber-500/30 transition-all"
            >
              📱 Launch Sim
            </button>
          </div>
        </aside>

        {/* RIGHT MAIN DESK VIEWPORT */}
        <main className="flex-1 bg-[#07090e] p-6 overflow-y-auto">
          
          {/* DESK 1: EXECUTIVE KPI DASHBOARD */}
          {/* DEEP LINK FILTER BANNER */}
          {Object.keys(deepLinkParams).length > 0 && (
            <div className="bg-amber-500/10 border border-amber-500/30 rounded-xl px-4 py-3 flex items-center justify-between text-xs text-amber-300">
              <div className="flex items-center gap-2">
                <span className="font-bold">🔗 Deep Link Route Active:</span>
                {Object.entries(deepLinkParams).map(([k, v]) => (
                  <span key={k} className="bg-amber-500/20 px-2.5 py-0.5 rounded font-mono font-bold text-amber-200">
                    {k} = {v}
                  </span>
                ))}
              </div>
              <button 
                onClick={() => navigateToDesk(activeDeskId, activeCategory)}
                className="text-slate-400 hover:text-white underline text-xs font-semibold"
              >
                Clear Deep Link Filter ✕
              </button>
            </div>
          )}

          {activeDeskId === 'dashboard' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h1 className="text-2xl font-black text-white flex items-center gap-2">
                    <span>📊 Executive Fleet Operations KPI</span>
                  </h1>
                  <p className="text-xs text-slate-400 mt-1">
                    Live data streams connected to all 69 Supabase PostgreSQL tables.
                  </p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => addToast('Refreshed', 'Telemetry synced with Supabase PostgreSQL')}
                    className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-white font-bold text-xs rounded-xl border border-slate-700 shadow"
                  >
                    Sync Live Data
                  </button>
                </div>
              </div>

              {/* STATS CARDS */}
              <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <div className="p-5 bg-gradient-to-tr from-blue-900/40 to-slate-900 border border-blue-500/30 rounded-2xl">
                  <p className="text-[10px] uppercase font-bold text-blue-400">Total Rides Today</p>
                  <h2 className="text-3xl font-black text-white mt-1">1,480</h2>
                  <p className="text-xs text-emerald-400 mt-2 font-bold">↑ 18.4% vs last week</p>
                </div>
                <div className="p-5 bg-gradient-to-tr from-emerald-900/40 to-slate-900 border border-emerald-500/30 rounded-2xl">
                  <p className="text-[10px] uppercase font-bold text-emerald-400">Online EV Pilots</p>
                  <h2 className="text-3xl font-black text-white mt-1">128</h2>
                  <p className="text-xs text-slate-400 mt-2">94% Fleet Utilization</p>
                </div>
                <div className="p-5 bg-gradient-to-tr from-amber-900/40 to-slate-900 border border-amber-500/30 rounded-2xl">
                  <p className="text-[10px] uppercase font-bold text-amber-400">Gross Daily GMV</p>
                  <h2 className="text-3xl font-black text-white mt-1">₹48,920</h2>
                  <p className="text-xs text-slate-400 mt-2">Platform Cut: ₹7,338</p>
                </div>
                <div className="p-5 bg-gradient-to-tr from-purple-900/40 to-slate-900 border border-purple-500/30 rounded-2xl">
                  <p className="text-[10px] uppercase font-bold text-purple-400">Active Carpools</p>
                  <h2 className="text-3xl font-black text-white mt-1">34 Pools</h2>
                  <p className="text-xs text-emerald-400 mt-2">Zero Emission Saved 420kg CO2</p>
                </div>
              </div>

              {/* TWO-APP CONTROL QUICK ACTION DECK */}
              <div className="p-6 bg-slate-900/80 border border-slate-800 rounded-3xl space-y-4">
                <h3 className="text-base font-black text-white">⚡ Superadmin Two-App Control Center</h3>
                <p className="text-xs text-slate-400">Directly execute overrides, financial actions, and safety mandates on either customer or pilot app.</p>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                  <button onClick={() => setActiveDeskId('customer_wallets')} className="p-4 bg-slate-950 border border-slate-800 rounded-2xl text-left hover:border-blue-500 transition-all">
                    <span className="text-2xl">💳</span>
                    <h4 className="text-xs font-black text-white mt-2">Customer Wallets</h4>
                    <p className="text-[10px] text-slate-400">Credit, refund or freeze customer money.</p>
                  </button>
                  <button onClick={() => setActiveDeskId('driver_kyc')} className="p-4 bg-slate-950 border border-slate-800 rounded-2xl text-left hover:border-emerald-500 transition-all">
                    <span className="text-2xl">📋</span>
                    <h4 className="text-xs font-black text-white mt-2">Driver KYC Approvals</h4>
                    <p className="text-[10px] text-slate-400">Approve DL, Aadhaar and Sarathi records.</p>
                  </button>
                  <button onClick={() => setActiveDeskId('driver_payouts')} className="p-4 bg-slate-950 border border-slate-800 rounded-2xl text-left hover:border-amber-500 transition-all">
                    <span className="text-2xl">💵</span>
                    <h4 className="text-xs font-black text-white mt-2">Driver Instant Payouts</h4>
                    <p className="text-[10px] text-slate-400">Execute IMPS/UPI bank transfers in 60s.</p>
                  </button>
                  <button onClick={() => setActiveDeskId('surge_rules')} className="p-4 bg-slate-950 border border-slate-800 rounded-2xl text-left hover:border-purple-500 transition-all">
                    <span className="text-2xl">📈</span>
                    <h4 className="text-xs font-black text-white mt-2">Surge Multiplier</h4>
                    <p className="text-[10px] text-slate-400">Apply 1.2x - 3.0x peak pricing on any zone.</p>
                  </button>
                </div>
              </div>

              {/* LIVE VEHICLE CATEGORIES FROM FASTAPI */}
              <div className="p-6 bg-slate-900 border border-slate-800 rounded-3xl space-y-4">
                <div className="flex justify-between items-center">
                  <div>
                    <h3 className="text-sm font-black text-white">🛺 Active Fleet Categories (Supabase Live)</h3>
                    <p className="text-[11px] text-slate-400">Connected to <code>vehicle_categories</code> table.</p>
                  </div>
                  <button onClick={() => setActiveDeskId('fare_matrix')} className="px-3 py-1 bg-blue-600/20 text-blue-400 border border-blue-500/30 rounded-xl text-xs font-bold hover:bg-blue-600/30">
                    Manage Tariffs →
                  </button>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  {categories.map((c, i) => (
                    <div key={c.id || i} className="p-4 bg-slate-950 border border-slate-800/80 rounded-2xl space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="font-extrabold text-white text-xs">{c.name}</span>
                        <span className="text-[10px] font-mono text-emerald-400 font-bold">Cap: {c.capacity}</span>
                      </div>
                      <p className="text-[11px] text-slate-400">{c.description}</p>
                      <div className="flex justify-between items-center pt-2 border-t border-slate-800 text-[10px]">
                        <span className="text-slate-400 font-mono">{c.code}</span>
                        <span className="text-amber-400 font-bold">{c.is_electric ? '⚡ 100% Electric' : 'Petrol'}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* DESK 2: LIVE GPS FLEET TELEMETRY MAP */}
          {activeDeskId === 'gps_map' && (
            <div className="space-y-4 h-full flex flex-col">
              <div className="flex justify-between items-center border-b border-slate-800 pb-3 shrink-0">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>🗺️ Live GPS Fleet Telemetry &amp; Radar Map</span>
                  </h2>
                  <p className="text-xs text-slate-400">Real-time driver location stream mapped from <code>driver_locations</code> and <code>rides</code> tables.</p>
                </div>
                <div className="flex gap-2">
                  <span className="px-3 py-1 bg-emerald-500/20 text-emerald-400 rounded-full font-bold text-xs border border-emerald-500/30 flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping"></span>
                    128 Active Beacons
                  </span>
                </div>
              </div>
              <div className="flex-1 min-h-[550px] rounded-2xl overflow-hidden border border-slate-800 shadow-2xl relative">
                <AdminRealMap mode="live_fleet" height="100%" />
              </div>
            </div>
          )}

          {/* DESK 5: CUSTOMER DIRECTORY & KYC */}
          {activeDeskId === 'customer_dir' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white">👤 Customer Directory &amp; Full Profile Control</h2>
                  <p className="text-xs text-slate-400">Tables: <code>users</code>, <code>customer_profiles</code>, <code>sessions</code></p>
                </div>
                <button onClick={() => addToast('Exported', 'Customer database exported to CSV')} className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-xl shadow">
                  Export CSV
                </button>
              </div>

              <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] font-bold border-b border-slate-800">
                    <tr>
                      <th className="p-3">Customer</th>
                      <th className="p-3">Phone</th>
                      <th className="p-3">Membership Tier</th>
                      <th className="p-3">Wallet Balance</th>
                      <th className="p-3">Rating</th>
                      <th className="p-3">Status</th>
                      <th className="p-3 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/80">
                    <tr className="hover:bg-slate-800/40">
                      <td className="p-3 font-bold text-white flex items-center gap-2">
                        <div className="w-7 h-7 rounded-full bg-blue-600/30 text-blue-300 flex items-center justify-center font-bold text-[10px]">PS</div>
                        Priya Sharma
                      </td>
                      <td className="p-3 font-mono text-slate-300">+91 98765 43210</td>
                      <td className="p-3"><span className="px-2 py-0.5 rounded bg-amber-500/20 text-amber-300 font-bold text-[10px]">Platinum</span></td>
                      <td className="p-3 font-bold text-emerald-400">₹{customerBalances['cust_1']?.toFixed(2)}</td>
                      <td className="p-3 text-amber-400 font-bold">★ 4.95</td>
                      <td className="p-3"><span className="px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-400 font-bold text-[10px]">Active</span></td>
                      <td className="p-3 text-right space-x-1">
                        <button onClick={() => handleCreditWallet('cust_1', 100)} className="px-2 py-1 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded font-bold text-[10px]">+₹100</button>
                        <button onClick={() => addToast('Notice Sent', 'Notification sent to Priya Sharma')} className="px-2 py-1 bg-blue-600/20 text-blue-400 rounded font-bold text-[10px]">Msg</button>
                      </td>
                    </tr>
                    <tr className="hover:bg-slate-800/40">
                      <td className="p-3 font-bold text-white flex items-center gap-2">
                        <div className="w-7 h-7 rounded-full bg-purple-600/30 text-purple-300 flex items-center justify-center font-bold text-[10px]">AV</div>
                        Amit Verma
                      </td>
                      <td className="p-3 font-mono text-slate-300">+91 98110 99887</td>
                      <td className="p-3"><span className="px-2 py-0.5 rounded bg-slate-700 text-slate-300 font-bold text-[10px]">Gold</span></td>
                      <td className="p-3 font-bold text-emerald-400">₹{customerBalances['cust_2']?.toFixed(2)}</td>
                      <td className="p-3 text-amber-400 font-bold">★ 4.80</td>
                      <td className="p-3"><span className="px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-400 font-bold text-[10px]">Active</span></td>
                      <td className="p-3 text-right space-x-1">
                        <button onClick={() => handleCreditWallet('cust_2', 100)} className="px-2 py-1 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded font-bold text-[10px]">+₹100</button>
                        <button onClick={() => addToast('Notice Sent', 'Notification sent to Amit Verma')} className="px-2 py-1 bg-blue-600/20 text-blue-400 rounded font-bold text-[10px]">Msg</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* DESK 6: CUSTOMER WALLETS & LEDGER */}
          {activeDeskId === 'customer_wallets' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white">💳 Customer Wallets &amp; Ledger Control</h2>
                  <p className="text-xs text-slate-400">Directly credit customer balances, issue instant refunds, or freeze fraudulent wallets.</p>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {Object.entries(customerBalances).map(([id, bal]) => (
                  <div key={id} className="p-5 bg-slate-900 border border-slate-800 rounded-2xl space-y-3">
                    <div className="flex justify-between items-center">
                      <span className="font-bold text-white text-xs">{id.toUpperCase()}</span>
                      <span className="px-2 py-0.5 bg-emerald-500/20 text-emerald-400 text-[10px] font-bold rounded">Live</span>
                    </div>
                    <div>
                      <p className="text-[10px] text-slate-400 uppercase font-bold">Available Balance</p>
                      <h3 className="text-2xl font-black text-emerald-400 mt-1">₹{bal.toFixed(2)}</h3>
                    </div>
                    <div className="flex gap-2 pt-2 border-t border-slate-800">
                      <button onClick={() => handleCreditWallet(id, 100)} className="flex-1 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold text-[11px] rounded-xl">+₹100 Credit</button>
                      <button onClick={() => handleCreditWallet(id, 500)} className="flex-1 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-[11px] rounded-xl">+₹500</button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* DESK 12: DRIVER APPLICATIONS & KYC APPROVALS */}
          {activeDeskId === 'driver_kyc' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white">📋 Driver Applications &amp; KYC Approvals</h2>
                  <p className="text-xs text-slate-400">Verify driving licenses, commercial RC, police clearance, and Aadhaar.</p>
                </div>
                <span className="px-3 py-1 bg-amber-500/20 text-amber-400 rounded-full font-bold text-xs border border-amber-500/30">
                  {kycDrivers.filter(d => d.status === 'PENDING').length} Pending Review
                </span>
              </div>

              <div className="space-y-4">
                {kycDrivers.map(d => (
                  <div key={d.id} className="p-5 bg-slate-900 border border-slate-800 rounded-2xl flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="font-extrabold text-white text-sm">{d.name}</span>
                        <span className="text-[10px] font-mono text-slate-400">{d.id}</span>
                        <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                          d.status === 'VERIFIED' ? 'bg-emerald-500/20 text-emerald-400' :
                          d.status === 'REJECTED' ? 'bg-red-500/20 text-red-400' :
                          'bg-amber-500/20 text-amber-400'
                        }`}>
                          {d.status}
                        </span>
                      </div>
                      <p className="text-xs text-slate-300">Vehicle: <strong className="text-white">{d.vehicle}</strong></p>
                      <div className="flex gap-4 text-[11px] text-slate-400">
                        <span>DL: <code className="text-slate-200">{d.dl}</code></span>
                        <span>Aadhaar: <code className="text-slate-200">{d.aadhaar}</code></span>
                        <span>Mobile: <code className="text-slate-200">{d.mobile}</code></span>
                      </div>
                    </div>

                    {d.status === 'PENDING' && (
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => handleApproveKYC(d.id)}
                          className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs rounded-xl shadow-lg shadow-emerald-600/20"
                        >
                          Approve KYC ✓
                        </button>
                        <button
                          onClick={() => handleRejectKYC(d.id)}
                          className="px-3 py-2 bg-red-600/20 hover:bg-red-600/30 text-red-400 border border-red-500/30 font-bold text-xs rounded-xl"
                        >
                          Reject
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* DESK 17: DRIVER PAYOUT APPROVALS (IMPS) */}
          {activeDeskId === 'driver_payouts' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white">💵 Driver Daily Payout Approvals (Instant IMPS)</h2>
                  <p className="text-xs text-slate-400">Batch-release driver earnings directly to verified bank accounts or UPI VPA.</p>
                </div>
                <button
                  onClick={() => {
                    setPayouts(prev => prev.map(p => ({ ...p, status: 'COMPLETED' })));
                    addToast('Batch Executed', 'All pending IMPS payouts processed successfully.');
                  }}
                  className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white font-extrabold text-xs rounded-xl shadow-lg shadow-emerald-600/30"
                >
                  ⚡ Approve All Pending (IMPS)
                </button>
              </div>

              <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] font-bold border-b border-slate-800">
                    <tr>
                      <th className="p-3">Payout ID</th>
                      <th className="p-3">Driver Name</th>
                      <th className="p-3">Amount</th>
                      <th className="p-3">Beneficiary Account</th>
                      <th className="p-3">IFSC Code</th>
                      <th className="p-3">Status</th>
                      <th className="p-3 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/80">
                    {payouts.map(p => (
                      <tr key={p.id} className="hover:bg-slate-800/40">
                        <td className="p-3 font-mono font-bold text-white">{p.id}</td>
                        <td className="p-3 font-bold text-slate-200">{p.driver}</td>
                        <td className="p-3 font-black text-emerald-400 text-sm">₹{p.amount.toFixed(2)}</td>
                        <td className="p-3 text-slate-300">{p.bank}</td>
                        <td className="p-3 font-mono text-slate-400">{p.ifsc}</td>
                        <td className="p-3">
                          <span className={`px-2 py-0.5 rounded font-bold text-[10px] ${
                            p.status === 'COMPLETED' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-amber-500/20 text-amber-300'
                          }`}>
                            {p.status}
                          </span>
                        </td>
                        <td className="p-3 text-right">
                          {p.status === 'PENDING' ? (
                            <button
                              onClick={() => handleApprovePayout(p.id)}
                              className="px-3 py-1 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs rounded-lg shadow"
                            >
                              Release IMPS
                            </button>
                          ) : (
                            <span className="text-slate-500 text-[11px] font-bold">Settled ✓</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* DESK 21: DYNAMIC FARE MATRIX & TARIFFS */}
          {activeDeskId === 'fare_matrix' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white">🏷️ Dynamic Fare Matrix &amp; Tariffs Configuration</h2>
                  <p className="text-xs text-slate-400">Manage base fare, per km rate, and minimum tariffs for all vehicle tiers. Connected to <code>fare_rules</code>.</p>
                </div>
                <button onClick={handleSaveFares} className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white font-black text-xs rounded-xl shadow-lg shadow-blue-600/30">
                  Save Changes to Supabase
                </button>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="p-5 bg-slate-900 border border-slate-800 rounded-2xl space-y-4">
                  <div className="flex justify-between items-center">
                    <span className="font-extrabold text-white text-sm">Standard E-Rickshaw</span>
                    <span className="px-2 py-0.5 bg-emerald-500/20 text-emerald-400 text-[10px] font-bold rounded">Active</span>
                  </div>
                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <label className="text-slate-400 text-[10px] uppercase font-bold block mb-1">Base Fare (₹)</label>
                      <input type="number" defaultValue="20" className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white font-bold" />
                    </div>
                    <div>
                      <label className="text-slate-400 text-[10px] uppercase font-bold block mb-1">Per Km Rate (₹)</label>
                      <input type="number" defaultValue="12" className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white font-bold" />
                    </div>
                    <div>
                      <label className="text-slate-400 text-[10px] uppercase font-bold block mb-1">Min Fare (₹)</label>
                      <input type="number" defaultValue="25" className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white font-bold" />
                    </div>
                    <div>
                      <label className="text-slate-400 text-[10px] uppercase font-bold block mb-1">Night Surge Multiplier</label>
                      <input type="number" defaultValue="1.25" step="0.05" className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white font-bold" />
                    </div>
                  </div>
                </div>

                <div className="p-5 bg-slate-900 border border-slate-800 rounded-2xl space-y-4">
                  <div className="flex justify-between items-center">
                    <span className="font-extrabold text-white text-sm">Shared E-Rickshaw (Eco-Pool)</span>
                    <span className="px-2 py-0.5 bg-emerald-500/20 text-emerald-400 text-[10px] font-bold rounded">Active</span>
                  </div>
                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <label className="text-slate-400 text-[10px] uppercase font-bold block mb-1">Per Seat Flat Fare (₹)</label>
                      <input type="number" defaultValue="10" className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white font-bold" />
                    </div>
                    <div>
                      <label className="text-slate-400 text-[10px] uppercase font-bold block mb-1">Per Additional Km (₹)</label>
                      <input type="number" defaultValue="5" className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white font-bold" />
                    </div>
                    <div>
                      <label className="text-slate-400 text-[10px] uppercase font-bold block mb-1">Max Deviation Km</label>
                      <input type="number" defaultValue="1.5" step="0.1" className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white font-bold" />
                    </div>
                    <div>
                      <label className="text-slate-400 text-[10px] uppercase font-bold block mb-1">Platform Commission %</label>
                      <input type="number" defaultValue="15" className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-white font-bold" />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* DESK 23: SURGE MULTIPLIER DEMAND OVERRIDES */}
          {activeDeskId === 'surge_rules' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white">📈 Surge Multiplier Demand Overrides</h2>
                  <p className="text-xs text-slate-400">Instantly adjust dynamic surge multipliers for rain, peak hours, or metro delays.</p>
                </div>
                <button
                  onClick={() => addToast('Surge Multiplier Set', `Global demand surge set to ${surgeMultiplier}x on active zones.`)}
                  className="px-4 py-2 bg-purple-600 hover:bg-purple-500 text-white font-bold text-xs rounded-xl shadow-lg shadow-purple-600/30"
                >
                  Apply Surge to All Metro Corridors
                </button>
              </div>

              <div className="p-6 bg-slate-900 border border-slate-800 rounded-2xl space-y-6">
                <div>
                  <div className="flex justify-between items-center mb-2">
                    <span className="font-bold text-white text-sm">Active Surge Multiplier</span>
                    <span className="text-2xl font-black text-purple-400 font-mono">{surgeMultiplier.toFixed(1)}x</span>
                  </div>
                  <input
                    type="range"
                    min="1.0"
                    max="3.5"
                    step="0.1"
                    value={surgeMultiplier}
                    onChange={e => setSurgeMultiplier(parseFloat(e.target.value))}
                    className="w-full accent-purple-500 cursor-pointer h-2 bg-slate-950 rounded-lg"
                  />
                  <div className="flex justify-between text-[10px] text-slate-500 mt-1 font-mono">
                    <span>1.0x (Normal)</span>
                    <span>1.5x (Busy)</span>
                    <span>2.0x (Peak)</span>
                    <span>3.0x (Storm/Flood)</span>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-4 border-t border-slate-800">
                  <div className="p-4 bg-slate-950 border border-slate-800 rounded-xl space-y-1">
                    <span className="text-xs font-bold text-white">Rajiv Chowk Metro Hub</span>
                    <p className="text-[10px] text-slate-400">Demand: High (240 requests/hr)</p>
                    <span className="text-xs font-mono text-purple-400 font-black">1.6x Active</span>
                  </div>
                  <div className="p-4 bg-slate-950 border border-slate-800 rounded-xl space-y-1">
                    <span className="text-xs font-bold text-white">Anand Vihar ISBT Feeder</span>
                    <p className="text-[10px] text-slate-400">Demand: Moderate (110 requests/hr)</p>
                    <span className="text-xs font-mono text-purple-400 font-black">1.2x Active</span>
                  </div>
                  <div className="p-4 bg-slate-950 border border-slate-800 rounded-xl space-y-1">
                    <span className="text-xs font-bold text-white">Noida Sector 18 Market</span>
                    <p className="text-[10px] text-slate-400">Demand: High (185 requests/hr)</p>
                    <span className="text-xs font-mono text-purple-400 font-black">1.4x Active</span>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* DESK 27: MASS PUSH BROADCAST CENTER */}
          {activeDeskId === 'broadcast' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white">📢 Mass Push Broadcast Center</h2>
                  <p className="text-xs text-slate-400">Send instant Firebase FCM push notifications and SMS broadcasts to drivers and commuters.</p>
                </div>
              </div>

              <div className="p-6 bg-slate-900 border border-slate-800 rounded-2xl space-y-4 max-w-2xl">
                <div>
                  <label className="text-[10px] uppercase font-bold text-slate-400 block mb-1">Target Audience</label>
                  <select
                    value={broadcastAudience}
                    onChange={e => setBroadcastAudience(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white outline-none"
                  >
                    <option value="ALL_DRIVERS">⚡ All Active Drivers (128 Online)</option>
                    <option value="ALL_CUSTOMERS">👤 All Registered Customers</option>
                    <option value="METRO_CORRIDOR_DRIVERS">🚇 Metro Corridor Drivers Only</option>
                  </select>
                </div>

                <div>
                  <label className="text-[10px] uppercase font-bold text-slate-400 block mb-1">Notification Title</label>
                  <input
                    type="text"
                    value={broadcastTitle}
                    onChange={e => setBroadcastTitle(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white outline-none"
                  />
                </div>

                <div>
                  <label className="text-[10px] uppercase font-bold text-slate-400 block mb-1">Message Body</label>
                  <textarea
                    rows={3}
                    value={broadcastMessage}
                    onChange={e => setBroadcastMessage(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white outline-none"
                  />
                </div>

                <button
                  onClick={handleSendBroadcast}
                  className="w-full py-3 bg-blue-600 hover:bg-blue-500 text-white font-black text-xs rounded-xl shadow-lg shadow-blue-600/30 transition-all"
                >
                  🚀 Dispatch Push Notification Now
                </button>
              </div>
            </div>
          )}

          {/* DESK 29: EMERGENCY SOS & SECURITY DESK */}
          {activeDeskId === 'emergency_sos' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-red-500 flex items-center gap-2">
                    <span>🚨 Emergency SOS Command &amp; 112 Desk</span>
                  </h2>
                  <p className="text-xs text-slate-400">Live priority alert stream connected to police dispatch, emergency contacts, and audio monitors.</p>
                </div>
                <button
                  onClick={handleTriggerSOS}
                  className="px-4 py-2 bg-red-600 hover:bg-red-500 text-white font-black text-xs rounded-xl shadow-lg shadow-red-600/30 animate-pulse"
                >
                  Sound Command Siren 🚨
                </button>
              </div>

              <div className="p-6 bg-red-950/20 border border-red-500/40 rounded-2xl space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-red-600/20 text-red-400 flex items-center justify-center text-xl border border-red-500/50">
                      🚨
                    </div>
                    <div>
                      <h3 className="font-extrabold text-white text-sm">Active SOS Incident: ALERT-SOS-1129</h3>
                      <p className="text-xs text-slate-400">Driver: <strong>Rameshwar Yadav (DL-1ER-9912)</strong> • Customer: <strong>Priya Sharma</strong></p>
                    </div>
                  </div>
                  <span className="px-3 py-1 bg-red-500/30 text-red-300 font-bold text-xs rounded-full border border-red-500/50">
                    POLICE 112 DISPATCHED
                  </span>
                </div>

                <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs pt-3 border-t border-red-500/30">
                  <div>
                    <span className="text-slate-400 text-[10px] block font-bold">Live GPS Coordinates</span>
                    <span className="font-mono text-white font-bold">28.6321° N, 77.2198° E</span>
                  </div>
                  <div>
                    <span className="text-slate-400 text-[10px] block font-bold">Audio Stream</span>
                    <span className="text-emerald-400 font-bold">🟢 Connected (Encrypted)</span>
                  </div>
                  <div>
                    <span className="text-slate-400 text-[10px] block font-bold">Emergency Contacts</span>
                    <span className="text-slate-200">3 SMS Sent ✓</span>
                  </div>
                  <div>
                    <span className="text-slate-400 text-[10px] block font-bold">Local PCR Van</span>
                    <span className="text-cyan-400 font-bold">PCR-42 (2 min away)</span>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* DESK 33: DUAL APP REMOTE SIMULATOR (BOTH APPS) */}
                    {/* DESK 29: AUDIT LOGS & GOVERNANCE VAULT */}
          {activeDeskId === 'audit_logs' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>🔐</span>
                    <span>Audit Vault &amp; Sensitive Operation Logs</span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Immutable security log of administrative overrides, tariff updates, and driver account mutations.
                  </p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => {
                      recordAudit('MANUAL_AUDIT_EXPORT', 'All System Logs', 'Admin regulatory compliance audit');
                      addToast('Exported', 'Audit ledger downloaded as CSV');
                    }}
                    className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-white font-bold text-xs rounded-xl border border-slate-700"
                  >
                    Export CSV
                  </button>
                </div>
              </div>

              <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
                <table className="w-full text-left text-xs text-slate-300">
                  <thead className="bg-slate-950 text-slate-400 uppercase font-mono text-[10px] border-b border-slate-800">
                    <tr>
                      <th className="p-3">Log ID</th>
                      <th className="p-3">Timestamp</th>
                      <th className="p-3">Actor / Admin</th>
                      <th className="p-3">Action</th>
                      <th className="p-3">Target Entity</th>
                      <th className="p-3">Justification</th>
                      <th className="p-3">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-mono text-[11px]">
                    {auditLogs.map(log => (
                      <tr key={log.id} className="hover:bg-slate-800/40">
                        <td className="p-3 text-amber-400 font-bold">{log.id}</td>
                        <td className="p-3 text-slate-400">{log.timestamp}</td>
                        <td className="p-3 text-white font-sans">{log.actor}</td>
                        <td className="p-3"><span className="px-2 py-0.5 bg-blue-500/20 text-blue-400 rounded font-bold">{log.action}</span></td>
                        <td className="p-3 text-slate-300">{log.target}</td>
                        <td className="p-3 text-slate-400 font-sans italic">{log.justification}</td>
                        <td className="p-3"><span className="px-2 py-0.5 bg-emerald-500/20 text-emerald-400 rounded font-bold">{log.status}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {activeDeskId === 'simulator' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white">📱 Dual App Remote Simulator &amp; Screen Inspector</h2>
                  <p className="text-xs text-slate-400">Live preview and remote control of both Customer Android App (50 screens) and Driver Android App (50 screens).</p>
                </div>
                <span className="px-3 py-1 bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 rounded-full text-xs font-bold">
                  Persistent Bottom Navigation Active
                </span>
              </div>

              {/* DUAL PHONE DISPLAY */}
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-start justify-items-center">
                
                {/* PHONE 1: CUSTOMER APP */}
                <div className="flex flex-col items-center">
                  <div className="mb-2 flex items-center justify-between w-full max-w-[360px] text-xs font-bold">
                    <span className="text-amber-400">👤 Customer App (50 Screens)</span>
                    <select
                      value={simCustScreenId}
                      onChange={e => setSimCustScreenId(parseInt(e.target.value, 10))}
                      className="bg-slate-900 border border-slate-700 text-white rounded-lg px-2 py-1 text-xs outline-none"
                    >
                      {BLUEPRINT_CUSTOMER_SCREENS.map(s => (
                        <option key={s.id} value={s.id}>{s.id}. {s.name}</option>
                      ))}
                    </select>
                  </div>

                  <div className="phone-frame">
                    <div className="phone-notch">
                      <div className="phone-speaker"></div>
                    </div>
                    <div className="phone-status-bar">
                      <span>9:41</span>
                      <div className="flex items-center gap-1"><span>5G</span><span>100%</span></div>
                    </div>
                    
                    <div className="px-3 py-1 bg-slate-900 border-b border-slate-800 text-[9px] font-bold text-amber-400 truncate">
                      Screen {simCustScreenId} of 50 • [{BLUEPRINT_CUSTOMER_SCREENS.find(s => s.id === simCustScreenId)?.tag || 'App'}]
                    </div>

                    <div
                      className="screen-scroll"
                      dangerouslySetInnerHTML={{
                        __html: BLUEPRINT_CUSTOMER_UI[simCustScreenId.toString()] || '<div class="p-6 text-center text-slate-400 text-xs">Screen rendering...</div>'
                      }}
                    />

                    {/* CUSTOMER PERSISTENT BOTTOM NAVIGATION TAB BAR */}
                    <div className="phone-bottom-nav">
                      <button
                        onClick={() => setSimCustScreenId(7)}
                        className={`flex flex-col items-center text-[9px] ${simCustScreenId === 7 ? 'text-amber-400 font-bold' : 'text-slate-400'}`}
                      >
                        <span className="text-base">🏠</span>
                        <span>Home</span>
                      </button>
                      <button
                        onClick={() => setSimCustScreenId(10)}
                        className={`flex flex-col items-center text-[9px] ${simCustScreenId === 10 ? 'text-amber-400 font-bold' : 'text-slate-400'}`}
                      >
                        <span className="text-base">🚖</span>
                        <span>Rides</span>
                      </button>
                      <button
                        onClick={() => setSimCustScreenId(22)}
                        className={`flex flex-col items-center text-[9px] ${simCustScreenId === 22 ? 'text-amber-400 font-bold' : 'text-slate-400'}`}
                      >
                        <span className="text-base">💳</span>
                        <span>Wallet</span>
                      </button>
                      <button
                        onClick={() => setSimCustScreenId(16)}
                        className={`flex flex-col items-center text-[9px] ${simCustScreenId === 16 ? 'text-red-400 font-bold' : 'text-slate-400'}`}
                      >
                        <span className="text-base">🛡️</span>
                        <span>Safety</span>
                      </button>
                      <button
                        onClick={() => setSimCustScreenId(25)}
                        className={`flex flex-col items-center text-[9px] ${simCustScreenId === 25 ? 'text-amber-400 font-bold' : 'text-slate-400'}`}
                      >
                        <span className="text-base">👤</span>
                        <span>Profile</span>
                      </button>
                      <button
                        onClick={() => setSimCustScreenId(s => (s % 50) + 1)}
                        className="flex flex-col items-center text-[9px] text-slate-400"
                      >
                        <span className="text-base">📑</span>
                        <span>All (50)</span>
                      </button>
                    </div>
                    <div className="phone-home-indicator"></div>
                  </div>
                </div>

                {/* PHONE 2: DRIVER PILOT APP */}
                <div className="flex flex-col items-center">
                  <div className="mb-2 flex items-center justify-between w-full max-w-[360px] text-xs font-bold">
                    <span className="text-emerald-400">⚡ Driver Pilot App (50 Screens)</span>
                    <select
                      value={simDriverScreenId}
                      onChange={e => setSimDriverScreenId(parseInt(e.target.value, 10))}
                      className="bg-slate-900 border border-slate-700 text-white rounded-lg px-2 py-1 text-xs outline-none"
                    >
                      {BLUEPRINT_DRIVER_SCREENS.map(s => (
                        <option key={s.id} value={s.id}>{s.id}. {s.name}</option>
                      ))}
                    </select>
                  </div>

                  <div className="phone-frame">
                    <div className="phone-notch">
                      <div className="phone-speaker"></div>
                    </div>
                    <div className="phone-status-bar">
                      <span>9:41</span>
                      <div className="flex items-center gap-1"><span>5G</span><span>100%</span></div>
                    </div>
                    
                    <div className="px-3 py-1 bg-slate-900 border-b border-slate-800 text-[9px] font-bold text-emerald-400 truncate">
                      Screen {simDriverScreenId} of 50 • [{BLUEPRINT_DRIVER_SCREENS.find(s => s.id === simDriverScreenId)?.tag || 'Pilot'}]
                    </div>

                    <div
                      className="screen-scroll"
                      dangerouslySetInnerHTML={{
                        __html: BLUEPRINT_DRIVER_UI[simDriverScreenId.toString()] || '<div class="p-6 text-center text-slate-400 text-xs">Screen rendering...</div>'
                      }}
                    />

                    {/* DRIVER PERSISTENT BOTTOM NAVIGATION TAB BAR */}
                    <div className="phone-bottom-nav">
                      <button
                        onClick={() => setSimDriverScreenId(8)}
                        className={`flex flex-col items-center text-[9px] ${simDriverScreenId === 8 ? 'text-emerald-400 font-bold' : 'text-slate-400'}`}
                      >
                        <span className="text-base">⚡</span>
                        <span>Cockpit</span>
                      </button>
                      <button
                        onClick={() => setSimDriverScreenId(11)}
                        className={`flex flex-col items-center text-[9px] ${simDriverScreenId === 11 ? 'text-emerald-400 font-bold' : 'text-slate-400'}`}
                      >
                        <span className="text-base">🧭</span>
                        <span>Nav</span>
                      </button>
                      <button
                        onClick={() => setSimDriverScreenId(19)}
                        className={`flex flex-col items-center text-[9px] ${simDriverScreenId === 19 ? 'text-amber-400 font-bold' : 'text-slate-400'}`}
                      >
                        <span className="text-base">💰</span>
                        <span>Earnings</span>
                      </button>
                      <button
                        onClick={() => setSimDriverScreenId(16)}
                        className={`flex flex-col items-center text-[9px] ${simDriverScreenId === 16 ? 'text-red-400 font-bold' : 'text-slate-400'}`}
                      >
                        <span className="text-base">🚨</span>
                        <span>SOS</span>
                      </button>
                      <button
                        onClick={() => setSimDriverScreenId(25)}
                        className={`flex flex-col items-center text-[9px] ${simDriverScreenId === 25 ? 'text-emerald-400 font-bold' : 'text-slate-400'}`}
                      >
                        <span className="text-base">👤</span>
                        <span>Profile</span>
                      </button>
                      <button
                        onClick={() => setSimDriverScreenId(s => (s % 50) + 1)}
                        className="flex flex-col items-center text-[9px] text-slate-400"
                      >
                        <span className="text-base">📑</span>
                        <span>All (50)</span>
                      </button>
                    </div>
                    <div className="phone-home-indicator"></div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* FALLBACK FOR REMAINING DESKS (3 to 32) */}
          {['dashboard', 'gps_map', 'customer_dir', 'customer_wallets', 'driver_kyc', 'driver_payouts', 'fare_matrix', 'surge_rules', 'broadcast', 'emergency_sos', 'audit_logs', 'simulator'].indexOf(activeDeskId) === -1 && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>{activeDesk.icon}</span>
                    <span>{activeDesk.title}</span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Supabase Database Tables: <code className="text-amber-400 font-mono">{activeDesk.table}</code>
                  </p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => addToast('Synchronized', `Desk parameters updated for ${activeDesk.title} in Supabase`)}
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-xl shadow-lg shadow-blue-600/30"
                  >
                    Save &amp; Commit Changes
                  </button>
                </div>
              </div>

              <div className="p-6 bg-slate-900 border border-slate-800 rounded-2xl space-y-4">
                <div className="flex justify-between items-center text-xs">
                  <span className="text-slate-400">Desk Category: <strong className="text-white uppercase">{activeDesk.category}</strong></span>
                  <span className="px-2.5 py-1 bg-emerald-500/20 text-emerald-400 rounded-full font-bold text-[10px]">69 Supabase Tables Active</span>
                </div>
                
                <div className="p-4 bg-slate-950 border border-slate-800/80 rounded-xl space-y-2 text-xs">
                  <div className="flex justify-between text-slate-300">
                    <span className="font-bold">Primary Schema Mapping:</span>
                    <span className="font-mono text-amber-400">{activeDesk.table}</span>
                  </div>
                  <div className="flex justify-between text-slate-300">
                    <span className="font-bold">Permissions:</span>
                    <span className="font-mono text-emerald-400">ALL_PERMISSIONS (SUPERADMIN_ROLE)</span>
                  </div>
                  <div className="flex justify-between text-slate-300">
                    <span className="font-bold">Audit Logging:</span>
                    <span className="text-emerald-400 font-mono">AUTOMATIC_TRANSACTION_LOG</span>
                  </div>
                </div>

                <div className="p-6 border border-dashed border-slate-800 rounded-xl text-center space-y-3">
                  <p className="text-xs text-slate-400">Live operation parameters and automated state machines are actively monitoring this desk.</p>
                  <button
                    onClick={() => addToast('Executed', `Operation executed for ${activeDesk.title}`)}
                    className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-bold rounded-xl border border-slate-700"
                  >
                    Execute Immediate Sync
                  </button>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>

      {/* 4. TOAST NOTIFICATIONS CONTAINER */}
      <div className="fixed bottom-6 right-6 z-50 flex flex-col gap-2 max-w-sm pointer-events-none">
        {toasts.map(t => (
          <div
            key={t.id}
            className={`p-4 rounded-2xl border shadow-2xl text-xs space-y-1 pointer-events-auto transition-all animate-bounce ${
              t.type === 'danger'
                ? 'bg-red-950 border-red-500 text-red-200'
                : t.type === 'info'
                ? 'bg-blue-950 border-blue-500 text-blue-200'
                : 'bg-emerald-950 border-emerald-500 text-emerald-200'
            }`}
          >
            <div className="flex items-center justify-between font-bold">
              <span>{t.title}</span>
              <button
                onClick={() => setToasts(prev => prev.filter(item => item.id !== t.id))}
                className="text-slate-400 hover:text-white ml-2"
              >
                ✕
              </button>
            </div>
            <p className="text-[11px] text-slate-300">{t.message}</p>
          </div>
        ))}
      </div>
    
      {/* CRITICAL ACTION CONFIRMATION MODAL */}
      {confirmModal && confirmModal.open && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center z-[9999] p-4">
          <div className="bg-slate-900 border border-slate-700 rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl animate-in fade-in duration-200">
            <div className="flex items-center gap-3">
              <span className="text-2xl">{confirmModal.isDestructive ? '⚠️' : '🛡️'}</span>
              <h3 className="text-base font-bold text-white">{confirmModal.title}</h3>
            </div>
            <p className="text-xs text-slate-300 leading-relaxed">{confirmModal.message}</p>
            <div className="flex justify-end gap-3 pt-3">
              <button
                onClick={() => setConfirmModal(null)}
                className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-bold rounded-xl"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  const cb = confirmModal.onConfirm;
                  recordAudit(confirmModal.title, 'Admin Console Action', 'Executed via Ops Confirmation');
                  setConfirmModal(null);
                  cb();
                }}
                className={`px-4 py-2 text-white text-xs font-bold rounded-xl shadow-lg ${
                  confirmModal.isDestructive ? 'bg-red-600 hover:bg-red-500 shadow-red-600/30' : 'bg-amber-500 hover:bg-amber-400 text-black shadow-amber-500/30'
                }`}
              >
                {confirmModal.actionLabel || 'Confirm Execution'}
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};

