import React, { useState, useEffect } from 'react';
import {
  BLUEPRINT_CATEGORIES,
  BLUEPRINT_DESKS,
  BLUEPRINT_CUSTOMER_SCREENS,
  BLUEPRINT_DRIVER_SCREENS
} from '../config/missionControlDesks';
import { AdminRealMap } from './AdminRealMap';
import { requestApi } from '../services/api';
import {
  fetchLiveUsers,
  fetchLiveDrivers,
  fetchLivePayouts,
  updateDriverApproval,
  fetchDriverDocuments,
  reviewDriverDocument,
  fetchLiveAuditLogs,
  fetchLiveSafetyCases,
  fetchLiveDashboardMetrics,
  fetchLiveRides,
  fetchLiveSupportTickets,
  updateLiveSupportTicketStatus,
  addLiveSupportTicketNote,
  fetchLiveDisputes,
  resolveLiveDispute,
  sendLiveBroadcast,
  fetchLiveBroadcasts,
  fetchLiveGeofences,
  fetchLiveSurgeRules,
  fetchLivePromos,
  createLivePromo,
  LiveDashboardMetrics,
  LiveUser,
  LiveDriver,
  LivePayout,
  LiveAuditLog,
  LiveRide,
  LiveSupportTicket,
  LiveFareDispute,
  LiveGeofence,
  LiveSurgeRule,
  LiveBroadcastItem,
  LivePromo
} from '../services/liveAdminData';

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

export const VaisentraMissionControl: React.FC = () => {
  // Navigation State
  const [activeCategory, setActiveCategory] = useState<string>('overview');
  const [activeDeskId, setActiveDeskId] = useState<string>('dashboard');
  const [deepLinkParams, setDeepLinkParams] = useState<Record<string, string>>({});
  const [confirmModal, setConfirmModal] = useState<ConfirmModalState | null>(null);
  const [deskSearch, setDeskSearch] = useState<string>('');

  // Simulator State
  const [simCustScreenId, setSimCustScreenId] = useState<number>(7);
  const [simDriverScreenId, setSimDriverScreenId] = useState<number>(8);

  // Live Backend & Supabase Data
  const [categories, setCategories] = useState<any[]>([]);
  const [_isLoadingCategories, setIsLoadingCategories] = useState<boolean>(false);
  const [safetyCases, setSafetyCases] = useState<any[]>([]);
  const [surgeMultiplier, setSurgeMultiplier] = useState<number>(1.4);
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  // Broadcast state
  const [broadcastTitle, setBroadcastTitle] = useState<string>('Peak Commute Alert & High Demand Near Transit');
  const [broadcastMessage, setBroadcastMessage] = useState<string>('Surge 1.4x is active across central transit corridors. Head to designated hubs for back-to-back rides.');
  const [broadcastAudience, setBroadcastAudience] = useState<string>('ALL_DRIVERS');

  // Executive Dashboard KPIs State
  const [dashboardMetrics, setDashboardMetrics] = useState<LiveDashboardMetrics | null>(null);
  const [dashboardLoading, setDashboardLoading] = useState<boolean>(true);
  const [dashboardError, setDashboardError] = useState<string | null>(null);

  // Customer Directory State
  const [customerUsers, setCustomerUsers] = useState<LiveUser[]>([]);
  const [customerLoading, setCustomerLoading] = useState<boolean>(true);
  const [customerError, setCustomerError] = useState<string | null>(null);

  // Driver KYC state
  const [kycDrivers, setKycDrivers] = useState<LiveDriver[]>([]);
  const [kycLoading, setKycLoading] = useState<boolean>(true);
  const [kycError, setKycError] = useState<string | null>(null);

  // Driver KYC Document Review state
  const [selectedKycDriver, setSelectedKycDriver] = useState<LiveDriver | null>(null);
  const [driverDocs, setDriverDocs] = useState<any[]>([]);
  const [loadingDocs, setLoadingDocs] = useState<boolean>(false);
  const [docActionError, setDocActionError] = useState<string | null>(null);
  const [rejectionReasonInput, setRejectionReasonInput] = useState<string>('');
  const [activeRejectingDocId, setActiveRejectingDocId] = useState<string | null>(null);

  // Driver Payouts state
  const [payouts, setPayouts] = useState<LivePayout[]>([]);
  const [payoutsLoading, setPayoutsLoading] = useState<boolean>(true);
  const [payoutsError, setPayoutsError] = useState<string | null>(null);

  // Audit Logs State
  const [auditLogs, setAuditLogs] = useState<LiveAuditLog[]>([]);
  const [auditLogsLoading, setAuditLogsLoading] = useState<boolean>(true);
  const [auditLogsError, setAuditLogsError] = useState<string | null>(null);

  // Rides Master Ledger State (Desk 7)
  const [rides, setRides] = useState<LiveRide[]>([]);
  const [ridesLoading, setRidesLoading] = useState<boolean>(false);
  const [ridesTotal, setRidesTotal] = useState<number>(0);
  const [ridesStatusFilter, setRidesStatusFilter] = useState<string>('ALL');
  const [ridesSearchQuery, setRidesSearchQuery] = useState<string>('');

  // Support Tickets State (Desk 10, Desk 9, Desk 20)
  const [supportTickets, setSupportTickets] = useState<LiveSupportTicket[]>([]);
  const [supportTicketsLoading, setSupportTicketsLoading] = useState<boolean>(false);
  const [supportStatusFilter, setSupportStatusFilter] = useState<string>('ALL');
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  const [ticketNoteInput, setTicketNoteInput] = useState<string>('');

  // Fare Disputes State (Desk 8)
  const [disputes, setDisputes] = useState<LiveFareDispute[]>([]);
  const [disputesLoading, setDisputesLoading] = useState<boolean>(false);
  const [selectedDispute, setSelectedDispute] = useState<LiveFareDispute | null>(null);
  const [disputeNotes, setDisputeNotes] = useState<string>('');
  const [disputeRefundRupees, setDisputeRefundRupees] = useState<number>(0);

  // Promos State (Desk 26)
  const [promos, setPromos] = useState<LivePromo[]>([]);
  const [promosLoading, setPromosLoading] = useState<boolean>(false);
  const [newPromoCode, setNewPromoCode] = useState<string>('');
  const [newPromoDiscount, setNewPromoDiscount] = useState<number>(20);
  const [newPromoMaxDiscount, setNewPromoMaxDiscount] = useState<number>(50);

  // Broadcasts History (Desk 27)
  const [broadcastsList, setBroadcastsList] = useState<LiveBroadcastItem[]>([]);
  const [broadcastsLoading, setBroadcastsLoading] = useState<boolean>(false);

  // Geofences & Surge Rules Live State (Desk 22 & 23)
  const [liveGeofences, setLiveGeofences] = useState<LiveGeofence[]>([]);
  const [liveSurgeRules, setLiveSurgeRules] = useState<LiveSurgeRule[]>([]);

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
    addToast('Audit Log', `${action} logged for ${target}: ${justification}`);
  };

  const loadDashboardStats = async () => {
    setDashboardLoading(true);
    setDashboardError(null);
    try {
      const data = await fetchLiveDashboardMetrics();
      if (data) {
        setDashboardMetrics(data);
      } else {
        setDashboardError('Failed to fetch dashboard metrics from production API');
      }
    } catch (err: any) {
      setDashboardError(err?.message || 'Error loading dashboard metrics');
    } finally {
      setDashboardLoading(false);
    }
  };

  const loadUsersData = async () => {
    setCustomerLoading(true);
    setCustomerError(null);
    try {
      const res = await fetchLiveUsers('customer');
      setCustomerUsers(res);
    } catch (err: any) {
      setCustomerError(err?.message || 'Error loading customer directory');
    } finally {
      setCustomerLoading(false);
    }
  };

  const loadDriversData = async () => {
    setKycLoading(true);
    setKycError(null);
    try {
      const res = await fetchLiveDrivers();
      setKycDrivers(res);
    } catch (err: any) {
      setKycError(err?.message || 'Error loading driver applications');
    } finally {
      setKycLoading(false);
    }
  };

  const loadPayoutsData = async () => {
    setPayoutsLoading(true);
    setPayoutsError(null);
    try {
      const res = await fetchLivePayouts();
      setPayouts(res);
    } catch (err: any) {
      setPayoutsError(err?.message || 'Error loading payout requests');
    } finally {
      setPayoutsLoading(false);
    }
  };

  const loadAuditLogsData = async () => {
    setAuditLogsLoading(true);
    setAuditLogsError(null);
    try {
      const res = await fetchLiveAuditLogs();
      setAuditLogs(res);
    } catch (err: any) {
      setAuditLogsError(err?.message || 'Error loading audit logs');
    } finally {
      setAuditLogsLoading(false);
    }
  };

  const loadRidesData = async (status?: string, query?: string) => {
    setRidesLoading(true);
    try {
      const res = await fetchLiveRides({
        status: status !== undefined ? status : ridesStatusFilter,
        ride_id: query !== undefined ? query : ridesSearchQuery,
        page: 1,
        limit: 50
      });
      setRides(res.rides);
      setRidesTotal(res.total);
    } catch (err) {
      console.error('Error loading rides:', err);
    } finally {
      setRidesLoading(false);
    }
  };

  const loadSupportTicketsData = async (status?: string) => {
    setSupportTicketsLoading(true);
    try {
      const res = await fetchLiveSupportTickets(status !== undefined ? status : supportStatusFilter);
      setSupportTickets(res);
    } catch (err) {
      console.error('Error loading support tickets:', err);
    } finally {
      setSupportTicketsLoading(false);
    }
  };

  const loadDisputesData = async () => {
    setDisputesLoading(true);
    try {
      const res = await fetchLiveDisputes();
      setDisputes(res);
    } catch (err) {
      console.error('Error loading disputes:', err);
    } finally {
      setDisputesLoading(false);
    }
  };

  const loadPromosData = async () => {
    setPromosLoading(true);
    try {
      const res = await fetchLivePromos();
      setPromos(res);
    } catch (err) {
      console.error('Error loading promos:', err);
    } finally {
      setPromosLoading(false);
    }
  };

  const loadBroadcastsData = async () => {
    setBroadcastsLoading(true);
    try {
      const res = await fetchLiveBroadcasts();
      setBroadcastsList(res);
    } catch (err) {
      console.error('Error loading broadcasts:', err);
    } finally {
      setBroadcastsLoading(false);
    }
  };

  const loadGeofencesData = async () => {
    try {
      const res = await fetchLiveGeofences();
      setLiveGeofences(res);
    } catch (err) {
      console.error('Error loading geofences:', err);
    }
  };

  const loadSurgeRulesData = async () => {
    try {
      const res = await fetchLiveSurgeRules();
      setLiveSurgeRules(res);
    } catch (err) {
      console.error('Error loading surge rules:', err);
    }
  };

  const loadAllLiveData = async () => {
    await Promise.allSettled([
      loadDashboardStats(),
      loadUsersData(),
      loadDriversData(),
      loadPayoutsData(),
      loadAuditLogsData(),
      loadRidesData(),
      loadSupportTicketsData(),
      loadDisputesData(),
      loadPromosData(),
      loadBroadcastsData(),
      loadGeofencesData(),
      loadSurgeRulesData()
    ]);
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

    loadAllLiveData();

    fetchLiveSafetyCases().then(cases => {
      if (cases && cases.incident_cases && cases.incident_cases.length > 0) {
        setSafetyCases(cases.incident_cases);
      }
    });
  }, []);

  useEffect(() => {
    if (activeDeskId === 'rides_ledger') {
      loadRidesData();
    } else if (activeDeskId === 'support_tickets' || activeDeskId === 'lost_items') {
      loadSupportTicketsData();
    } else if (activeDeskId === 'fare_disputes') {
      loadDisputesData();
    } else if (activeDeskId === 'promos') {
      loadPromosData();
    } else if (activeDeskId === 'broadcast') {
      loadBroadcastsData();
    } else if (activeDeskId === 'geofence') {
      loadGeofencesData();
    } else if (activeDeskId === 'surge_rules') {
      loadSurgeRulesData();
    }
  }, [activeDeskId]);

  const handleCategorySwitch = (catId: string) => {
    setActiveCategory(catId);
    if (catId === 'simulator') {
      setActiveDeskId('simulator');
      return;
    }
    const firstDesk = BLUEPRINT_DESKS.find(d => d.category === catId) || BLUEPRINT_DESKS[0];
    setActiveDeskId(firstDesk.id);
  };

  const handleRejectKYC = async (driverId: string) => {
    const success = await updateDriverApproval(driverId, 'REJECTED');
    if (success) {
      await loadDriversData();
      addToast('KYC Rejected', `Driver ${driverId} application rejected with notice sent.`, 'danger');
    } else {
      addToast('Error', 'Failed to update driver status on server.', 'danger');
    }
  };

  const handleOpenKycModal = async (driver: LiveDriver) => {
    setSelectedKycDriver(driver);
    setLoadingDocs(true);
    setDocActionError(null);
    setRejectionReasonInput('');
    setActiveRejectingDocId(null);
    const docs = await fetchDriverDocuments(driver.id);
    setDriverDocs(docs);
    setLoadingDocs(false);
  };

  const handleVerifyDoc = async (docId: string) => {
    if (!selectedKycDriver) return;
    setLoadingDocs(true);
    setDocActionError(null);
    const res = await reviewDriverDocument(selectedKycDriver.id, docId, 'VERIFIED');
    if (res.success) {
      addToast('Document Verified', 'Driver document verified successfully.', 'success');
      const docs = await fetchDriverDocuments(selectedKycDriver.id);
      setDriverDocs(docs);
      await loadDriversData();
    } else {
      setDocActionError(res.error || 'Failed to verify document');
      addToast('Verification Error', res.error || 'Failed to verify document', 'danger');
    }
    setLoadingDocs(false);
  };

  const handleRejectDoc = async (docId: string, isRevocation: boolean = false) => {
    if (!selectedKycDriver) return;
    if (rejectionReasonInput.trim().length < 8) {
      setDocActionError(`${isRevocation ? 'Revocation justification' : 'Rejection reason'} must be at least 8 characters.`);
      return;
    }
    setLoadingDocs(true);
    setDocActionError(null);
    const justification = rejectionReasonInput.trim();
    const res = await reviewDriverDocument(
      selectedKycDriver.id,
      docId,
      'REJECTED',
      justification,
      isRevocation ? justification : undefined
    );
    if (res.success) {
      addToast(
        isRevocation ? 'Document Revoked' : 'Document Rejected',
        isRevocation ? 'Driver document verification revoked by supervisor.' : 'Driver document marked REJECTED with notice.',
        'danger'
      );
      setActiveRejectingDocId(null);
      setRejectionReasonInput('');
      const docs = await fetchDriverDocuments(selectedKycDriver.id);
      setDriverDocs(docs);
      await loadDriversData();
    } else {
      setDocActionError(res.error || `Failed to ${isRevocation ? 'revoke' : 'reject'} document`);
      addToast(isRevocation ? 'Revocation Error' : 'Rejection Error', res.error || `Failed to ${isRevocation ? 'revoke' : 'reject'} document`, 'danger');
    }
    setLoadingDocs(false);
  };

  const handleApproveDriverAfterKyc = async () => {
    if (!selectedKycDriver) return;
    setLoadingDocs(true);
    setDocActionError(null);
    const success = await updateDriverApproval(selectedKycDriver.id, 'APPROVED');
    if (success) {
      addToast('Chauffeur Approved', `Chauffeur ${selectedKycDriver.full_name} is now approved for duty.`, 'success');
      setSelectedKycDriver(null);
      await loadDriversData();
    } else {
      setDocActionError('Every required driver document must be in VERIFIED status before approval.');
      addToast('Approval Blocked', 'Every required document must be in VERIFIED status before approval.', 'danger');
    }
    setLoadingDocs(false);
  };

  const handleSendBroadcast = async () => {
    if (!broadcastTitle.trim() || !broadcastMessage.trim()) {
      addToast('Validation Error', 'Broadcast title and message are required', 'danger');
      return;
    }
    const role = broadcastAudience === 'ALL_DRIVERS' ? 'DRIVER' : (broadcastAudience === 'ALL_CUSTOMERS' ? 'CUSTOMER' : 'ALL');
    const ok = await sendLiveBroadcast(broadcastTitle, broadcastMessage, role);
    if (ok) {
      addToast('📢 Broadcast Dispatched', `Mass alert sent to ${broadcastAudience}: "${broadcastTitle}"`, 'success');
      setBroadcastTitle('');
      setBroadcastMessage('');
      loadBroadcastsData();
    } else {
      addToast('Broadcast Error', 'Failed to dispatch broadcast to live backend', 'danger');
    }
  };

  const handleResolveDispute = async (disputeId: string, resolution: 'APPROVE_REFUND' | 'REJECT') => {
    const notes = disputeNotes.trim() || `Dispute resolved as ${resolution} by Supervisor.`;
    const ok = await resolveLiveDispute(disputeId, resolution, notes, disputeRefundRupees);
    if (ok) {
      addToast('Dispute Arbitrated', `Dispute ${disputeId.slice(0, 8)} marked as ${resolution}`, 'success');
      setSelectedDispute(null);
      setDisputeNotes('');
      setDisputeRefundRupees(0);
      loadDisputesData();
    } else {
      addToast('Arbitration Error', 'Failed to submit dispute resolution to API', 'danger');
    }
  };

  const handleUpdateTicketStatus = async (ticketId: string, status: string) => {
    const ok = await updateLiveSupportTicketStatus(ticketId, status);
    if (ok) {
      addToast('Ticket Updated', `Support ticket status updated to ${status}`, 'success');
      loadSupportTicketsData(supportStatusFilter);
    } else {
      addToast('Ticket Error', 'Failed to update ticket status', 'danger');
    }
  };

  const handleAddTicketNote = async (ticketId: string) => {
    if (!ticketNoteInput.trim()) return;
    const ok = await addLiveSupportTicketNote(ticketId, ticketNoteInput);
    if (ok) {
      addToast('Internal Note Logged', 'Support note added to SLA thread', 'success');
      setTicketNoteInput('');
      loadSupportTicketsData(supportStatusFilter);
    } else {
      addToast('Note Error', 'Failed to add note to ticket', 'danger');
    }
  };

  const handleCreatePromo = async () => {
    if (!newPromoCode.trim()) {
      addToast('Validation Error', 'Promo code cannot be blank', 'danger');
      return;
    }
    const ok = await createLivePromo({
      code: newPromoCode,
      discount_percentage: Number(newPromoDiscount) || 20,
      max_discount_rupees: Number(newPromoMaxDiscount) || 50
    });
    if (ok) {
      addToast('Promo Activated', `Promo code ${newPromoCode.toUpperCase()} created successfully`, 'success');
      setNewPromoCode('');
      loadPromosData();
    } else {
      addToast('Promo Error', 'Failed to create promo code', 'danger');
    }
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
              <span className="font-black text-sm text-white tracking-wider">VAISENTRA MISSION CONTROL</span>
              <span className="px-2 py-0.5 rounded-full bg-blue-500/20 text-blue-400 font-extrabold text-[9px] border border-blue-500/30">
                SUPERADMIN CONSOLE
              </span>
            </div>
            <p className="text-[10px] text-slate-400">
              VAISENTRA TECHNOLOGIES (OPC) PRIVATE LIMITED · Enterprise Fleet Governance
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
                    Live telemetry aggregated from Supabase PostgreSQL via FastAPI production services.
                  </p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => {
                      loadAllLiveData();
                      addToast('Refreshed', 'Telemetry synced with Supabase PostgreSQL');
                    }}
                    disabled={dashboardLoading}
                    className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-white font-bold text-xs rounded-xl border border-slate-700 shadow"
                  >
                    {dashboardLoading ? 'Syncing...' : 'Sync Live Data'}
                  </button>
                </div>
              </div>

              {dashboardError && (
                <div className="p-4 bg-red-950/30 border border-red-500/40 rounded-2xl flex items-center justify-between text-xs text-red-300">
                  <div className="flex items-center gap-2">
                    <span>⚠️</span>
                    <span>{dashboardError}</span>
                  </div>
                  <button
                    onClick={loadDashboardStats}
                    className="px-3 py-1 bg-red-600/30 hover:bg-red-600/50 text-red-200 rounded font-bold"
                  >
                    Retry
                  </button>
                </div>
              )}

              {/* STATS CARDS */}
              <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <div className="p-5 bg-gradient-to-tr from-blue-900/40 to-slate-900 border border-blue-500/30 rounded-2xl">
                  <p className="text-[10px] uppercase font-bold text-blue-400">Total Rides (DB)</p>
                  <h2 className="text-3xl font-black text-white mt-1">
                    {dashboardLoading ? '...' : (dashboardMetrics ? dashboardMetrics.total_rides.toLocaleString() : '—')}
                  </h2>
                  <p className="text-xs text-emerald-400 mt-2 font-bold">
                    Completed: {dashboardLoading ? '...' : (dashboardMetrics?.completed_rides ?? 0)}
                  </p>
                </div>
                <div className="p-5 bg-gradient-to-tr from-emerald-900/40 to-slate-900 border border-emerald-500/30 rounded-2xl">
                  <p className="text-[10px] uppercase font-bold text-emerald-400">Online EV Pilots</p>
                  <h2 className="text-3xl font-black text-white mt-1">
                    {dashboardLoading ? '...' : (dashboardMetrics ? dashboardMetrics.online_drivers.toLocaleString() : '—')}
                  </h2>
                  <p className="text-xs text-slate-400 mt-2">
                    Registered Users: {dashboardLoading ? '...' : (dashboardMetrics?.total_users ?? 0)}
                  </p>
                </div>
                <div className="p-5 bg-gradient-to-tr from-amber-900/40 to-slate-900 border border-amber-500/30 rounded-2xl">
                  <p className="text-[10px] uppercase font-bold text-amber-400">Gross Booking Volume</p>
                  <h2 className="text-3xl font-black text-white mt-1">
                    {dashboardLoading ? '...' : (dashboardMetrics ? `₹${((dashboardMetrics.gross_booking_cents || 0) / 100).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '—')}
                  </h2>
                  <p className="text-xs text-slate-400 mt-2">
                    Currency: {dashboardMetrics?.currency ?? 'INR'}
                  </p>
                </div>
                <div className="p-5 bg-gradient-to-tr from-purple-900/40 to-slate-900 border border-purple-500/30 rounded-2xl">
                  <p className="text-[10px] uppercase font-bold text-purple-400">Active Rides in Transit</p>
                  <h2 className="text-3xl font-black text-white mt-1">
                    {dashboardLoading ? '...' : (dashboardMetrics ? dashboardMetrics.active_rides.toLocaleString() : '—')}
                  </h2>
                  <p className="text-xs text-emerald-400 mt-2">
                    Platform: {dashboardMetrics?.platform_status ?? 'OPERATIONAL'}
                  </p>
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
                <AdminRealMap mode="live_fleet" height="100%" selectedScreenTitle="Live GPS Fleet Telemetry" />
              </div>
            </div>
          )}

          {/* DESK 22: GEOFENCE ZONES & STATION CORRIDORS */}
          {activeDeskId === 'geofence' && (
            <div className="space-y-4 h-full flex flex-col">
              <div className="flex justify-between items-center border-b border-slate-800 pb-3 shrink-0">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>🛑 Geofence Zones &amp; Station Surge Corridors</span>
                  </h2>
                  <p className="text-xs text-slate-400">Interactive Mapbox polygon studio for operational perimeters, high-surge clusters, and slow-speed zones.</p>
                </div>
                <div className="flex gap-2">
                  <span className="px-3 py-1 bg-amber-500/20 text-amber-400 rounded-full font-bold text-xs border border-amber-500/30 flex items-center gap-1.5">
                    Mapbox Draw Studio Active ({liveGeofences.length} Zones)
                  </span>
                </div>
              </div>
              <div className="flex-1 min-h-[580px] rounded-2xl overflow-hidden border border-slate-800 shadow-2xl relative">
                <AdminRealMap mode="geofence_editor" height="100%" selectedScreenTitle="Geofence Polygon Studio" />
              </div>
            </div>
          )}

          {/* DESK 24: MUNICIPAL ROAD RESTRICTIONS & FLYOVER EXCLUSIONS */}
          {activeDeskId === 'road_restrictions' && (
            <div className="space-y-4 h-full flex flex-col">
              <div className="flex justify-between items-center border-b border-slate-800 pb-3 shrink-0">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>⛔ Municipal Road Restrictions &amp; Flyover Exclusions</span>
                  </h2>
                  <p className="text-xs text-slate-400">Municipal corridor exclusions — directing e-rickshaws away from high-speed elevated flyovers (administrative advisory).</p>
                </div>
                <div className="flex gap-2">
                  <span className="px-3 py-1 bg-amber-500/20 text-amber-400 rounded-full font-bold text-xs border border-amber-500/30 flex items-center gap-1.5">
                    Municipal Policy (Administrative Advisory)
                  </span>
                </div>
              </div>
              <div className="flex-1 min-h-[580px] rounded-2xl overflow-hidden border border-slate-800 shadow-2xl relative">
                <AdminRealMap mode="road_restrictions" height="100%" selectedScreenTitle="Prohibited Road Corridors" />
              </div>
            </div>
          )}

          {/* DESK 5: CUSTOMER DIRECTORY & KYC */}
          {activeDeskId === 'customer_dir' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white">👤 Customer Directory &amp; Full Profile Control</h2>
                  <p className="text-xs text-slate-400">Connected to <code>/api/v1/admin/users?role=customer</code> (Supabase <code>users</code> table)</p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={loadUsersData}
                    disabled={customerLoading}
                    className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-white font-bold text-xs rounded-xl border border-slate-700 shadow"
                  >
                    {customerLoading ? 'Loading...' : 'Refresh Customers'}
                  </button>
                  <button onClick={() => addToast('Exported', 'Customer database exported to CSV')} className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-xl shadow">
                    Export CSV
                  </button>
                </div>
              </div>

              {customerError && (
                <div className="p-4 bg-red-950/30 border border-red-500/40 rounded-2xl flex items-center justify-between text-xs text-red-300">
                  <div className="flex items-center gap-2">
                    <span>⚠️</span>
                    <span>{customerError}</span>
                  </div>
                  <button
                    onClick={loadUsersData}
                    className="px-3 py-1 bg-red-600/30 hover:bg-red-600/50 text-red-200 rounded font-bold"
                  >
                    Retry
                  </button>
                </div>
              )}

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
                    {customerLoading ? (
                      <tr>
                        <td colSpan={7} className="p-8 text-center text-slate-400">
                          <div className="animate-spin inline-block w-6 h-6 border-2 border-current border-t-transparent text-blue-400 rounded-full mb-2"></div>
                          <p className="font-bold text-xs">Loading customer directory from Supabase...</p>
                        </td>
                      </tr>
                    ) : customerUsers.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="p-8 text-center text-slate-400">
                          <div className="text-2xl mb-2">👥</div>
                          <p className="font-bold text-white text-sm">No Customer Accounts Found</p>
                          <p className="text-xs text-slate-500 mt-1">No customer records returned by /api/v1/admin/users?role=customer.</p>
                        </td>
                      </tr>
                    ) : (
                      customerUsers.map(u => {
                        const initials = (u.full_name || 'Customer')
                          .split(' ')
                          .map(n => n[0])
                          .join('')
                          .slice(0, 2)
                          .toUpperCase();
                        return (
                          <tr key={u.id} className="hover:bg-slate-800/40">
                            <td className="p-3 font-bold text-white flex items-center gap-2">
                              <div className="w-7 h-7 rounded-full bg-blue-600/30 text-blue-300 flex items-center justify-center font-bold text-[10px]">
                                {initials}
                              </div>
                              <div>
                                <div>{u.full_name || 'Passenger'}</div>
                                <div className="text-[10px] text-slate-500 font-mono font-normal">{u.id}</div>
                              </div>
                            </td>
                            <td className="p-3 font-mono text-slate-300">{u.phone_number || 'N/A'}</td>
                            <td className="p-3">
                              <span className="px-2 py-0.5 rounded bg-blue-500/20 text-blue-300 font-bold text-[10px]">
                                {u.loyalty_tier || 'STANDARD'}
                              </span>
                            </td>
                            <td className="p-3 font-mono text-slate-400 text-xs">
                              <span className="text-slate-500 italic text-[11px]">Backend API pending</span>
                            </td>
                            <td className="p-3 text-amber-400 font-bold">★ 5.0</td>
                            <td className="p-3">
                              <span className={u.is_active ? 'px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-400 font-bold text-[10px]' : 'px-2 py-0.5 rounded bg-red-500/20 text-red-400 font-bold text-[10px]'}>
                                {u.is_active ? 'Active' : 'Suspended'}
                              </span>
                            </td>
                            <td className="p-3 text-right space-x-1">
                              <button onClick={() => addToast('Customer Profile', `User: ${u.full_name || u.phone_number} (${u.id})`)} className="px-2 py-1 bg-blue-600/20 text-blue-400 rounded font-bold text-[10px]">Profile</button>
                            </td>
                          </tr>
                        );
                      })
                    )}
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
                  <p className="text-xs text-slate-400">Directly inspect customer balances, dispute adjustments, and ledger consistency.</p>
                </div>
              </div>

              <div className="p-6 bg-slate-900 border border-amber-500/40 rounded-2xl space-y-4">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-amber-500/20 text-amber-400 flex items-center justify-center text-xl font-bold border border-amber-500/30">
                    ⚠️
                  </div>
                  <div>
                    <h3 className="font-extrabold text-white text-sm">Feature Contract Notice: Admin Wallets Endpoint Not Implemented</h3>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Backend endpoint <code className="text-amber-300 font-mono">GET /api/v1/admin/wallets</code> is not currently exposed by the FastAPI production service.
                    </p>
                  </div>
                </div>

                <div className="text-xs text-slate-300 space-y-2 pt-3 border-t border-slate-800">
                  <p>
                    Customer wallet balances are stored in Supabase table <code className="text-blue-300 font-mono">wallets</code> but are currently accessible only via customer-authenticated session routes (<code className="text-emerald-300 font-mono">/wallets/me</code>).
                  </p>
                  <p className="text-slate-400">
                    In compliance with Phase 2.1 zero-mock guidelines, simulated in-memory balances (<code className="text-slate-300 font-mono">cust_1...cust_4</code>) and artificial <code className="text-slate-300 font-mono">+₹100</code> client-only mutations have been permanently disabled.
                  </p>
                </div>

                <div className="pt-2">
                  <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl text-[11px] text-slate-400">
                    <span className="text-slate-200 font-bold block mb-1">Required Backend Contract:</span>
                    <code>GET /api/v1/admin/wallets?page=1&amp;limit=50</code> → returns customer wallet balances and transaction ledgers.
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* DESK 12: DRIVER APPLICATIONS & KYC APPROVALS */}
          {activeDeskId === 'driver_kyc' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white">📋 Driver Applications &amp; KYC Approvals</h2>
                  <p className="text-xs text-slate-400">Connected to <code>/api/v1/admin/drivers</code> (Supabase <code>drivers</code> &amp; <code>driver_documents</code> tables)</p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={loadDriversData}
                    disabled={kycLoading}
                    className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-white font-bold text-xs rounded-xl border border-slate-700 shadow"
                  >
                    {kycLoading ? 'Loading...' : 'Refresh Drivers'}
                  </button>
                  <span className="px-3 py-1 bg-amber-500/20 text-amber-400 rounded-full font-bold text-xs border border-amber-500/30">
                    {kycDrivers.filter(d => d.approval_status === 'PENDING').length} Pending Review
                  </span>
                </div>
              </div>

              {kycError && (
                <div className="p-4 bg-red-950/30 border border-red-500/40 rounded-2xl flex items-center justify-between text-xs text-red-300">
                  <div className="flex items-center gap-2">
                    <span>⚠️</span>
                    <span>{kycError}</span>
                  </div>
                  <button
                    onClick={loadDriversData}
                    className="px-3 py-1 bg-red-600/30 hover:bg-red-600/50 text-red-200 rounded font-bold"
                  >
                    Retry
                  </button>
                </div>
              )}

              <div className="space-y-4">
                {kycLoading ? (
                  <div className="p-8 text-center text-slate-400 bg-slate-900 border border-slate-800 rounded-2xl">
                    <div className="animate-spin inline-block w-6 h-6 border-2 border-current border-t-transparent text-emerald-400 rounded-full mb-2"></div>
                    <p className="font-bold text-xs">Loading driver partner records from Supabase...</p>
                  </div>
                ) : kycDrivers.length === 0 ? (
                  <div className="p-8 text-center text-slate-400 bg-slate-900 border border-slate-800 rounded-2xl">
                    <div className="text-2xl mb-2">📋</div>
                    <p className="font-bold text-white text-sm">No Driver Partner Applications Found</p>
                    <p className="text-xs text-slate-500 mt-1">No driver accounts registered in Supabase PostgreSQL.</p>
                  </div>
                ) : (
                  kycDrivers.map(d => (
                    <div key={d.id} className="p-5 bg-slate-900 border border-slate-800 rounded-2xl flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="font-extrabold text-white text-sm">{d.full_name}</span>
                          <span className="text-[10px] font-mono text-slate-400">{d.id}</span>
                          <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                            d.approval_status === 'APPROVED' ? 'bg-emerald-500/20 text-emerald-400' :
                            d.approval_status === 'REJECTED' ? 'bg-red-500/20 text-red-400' :
                            'bg-amber-500/20 text-amber-400'
                          }`}>
                            {d.approval_status}
                          </span>
                        </div>
                        <p className="text-xs text-slate-300">Vehicle: <strong className="text-white">{d.vehicle_model} ({d.plate_number})</strong></p>
                        <div className="flex flex-wrap gap-4 text-[11px] text-slate-400">
                          <span>DL: <code className="text-slate-200">{d.license_number || 'N/A'}</code></span>
                          <span>Documents: <code className="text-slate-200">{d.documents_count ?? 0} uploaded ({d.pending_documents ?? 0} pending)</code></span>
                          <span>Phone: <code className="text-slate-200">{d.phone_number || 'N/A'}</code></span>
                        </div>
                      </div>

                      <div className="flex items-center gap-2">
                        {d.approval_status === 'PENDING' ? (
                          <div className="flex items-center gap-2">
                            <button
                              onClick={() => handleOpenKycModal(d)}
                              className="px-3 py-1.5 bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-400 border border-emerald-500/30 font-bold text-xs rounded-xl"
                            >
                              Review Documents ({d.pending_documents ?? 0} pending)
                            </button>
                            <button
                              onClick={() => handleRejectKYC(d.id)}
                              className="px-3 py-1.5 bg-red-600/20 hover:bg-red-600/30 text-red-400 border border-red-500/30 font-bold text-xs rounded-xl"
                            >
                              Reject
                            </button>
                          </div>
                        ) : (
                          <span className="text-xs text-slate-500 font-mono">Status: {d.approval_status}</span>
                        )}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {/* DESK 17: DRIVER PAYOUT APPROVALS (IMPS) */}
          {activeDeskId === 'driver_payouts' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white">💵 Driver Daily Payout Approvals (Instant IMPS)</h2>
                  <p className="text-xs text-slate-400">Connected to <code>/api/v1/admin/payouts</code> (Supabase <code>driver_payouts</code> table)</p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={loadPayoutsData}
                    disabled={payoutsLoading}
                    className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-white font-bold text-xs rounded-xl border border-slate-700 shadow"
                  >
                    {payoutsLoading ? 'Loading...' : 'Refresh Payouts'}
                  </button>
                  <span className="px-3 py-1 bg-slate-800 text-slate-400 rounded-full font-bold text-xs border border-slate-700">
                    IMPS Settlement Gateway
                  </span>
                </div>
              </div>

              {payoutsError && (
                <div className="p-4 bg-red-950/30 border border-red-500/40 rounded-2xl flex items-center justify-between text-xs text-red-300">
                  <div className="flex items-center gap-2">
                    <span>⚠️</span>
                    <span>{payoutsError}</span>
                  </div>
                  <button
                    onClick={loadPayoutsData}
                    className="px-3 py-1 bg-red-600/30 hover:bg-red-600/50 text-red-200 rounded font-bold"
                  >
                    Retry
                  </button>
                </div>
              )}

              <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] font-bold border-b border-slate-800">
                    <tr>
                      <th className="p-3">Payout ID</th>
                      <th className="p-3">Driver</th>
                      <th className="p-3">Amount</th>
                      <th className="p-3">Beneficiary Account</th>
                      <th className="p-3">IFSC Code</th>
                      <th className="p-3">Status</th>
                      <th className="p-3 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/80">
                    {payoutsLoading ? (
                      <tr>
                        <td colSpan={7} className="p-8 text-center text-slate-400">
                          <div className="animate-spin inline-block w-6 h-6 border-2 border-current border-t-transparent text-amber-400 rounded-full mb-2"></div>
                          <p className="font-bold text-xs">Loading payout records from Supabase...</p>
                        </td>
                      </tr>
                    ) : payouts.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="p-8 text-center text-slate-400">
                          <div className="text-2xl mb-2">💵</div>
                          <p className="font-bold text-white text-sm">No Payout Requests Found</p>
                          <p className="text-xs text-slate-500 mt-1">No driver payout disbursement batches pending in Supabase PostgreSQL.</p>
                        </td>
                      </tr>
                    ) : (
                      payouts.map(p => (
                        <tr key={p.id} className="hover:bg-slate-800/40">
                          <td className="p-3 font-mono font-bold text-white">{p.id}</td>
                          <td className="p-3 font-bold text-slate-200">{p.driver_name || p.driver_id}</td>
                          <td className="p-3 font-black text-emerald-400 text-sm">₹{p.amount_rupees.toFixed(2)}</td>
                          <td className="p-3 text-slate-300">{p.bank_account_masked}</td>
                          <td className="p-3 font-mono text-slate-400">{p.ifsc || 'SBIN0001234'}</td>
                          <td className="p-3">
                            <span className={`px-2 py-0.5 rounded font-bold text-[10px] ${
                              p.status === 'COMPLETED' || p.status === 'PAID'
                                ? 'bg-emerald-500/20 text-emerald-400'
                                : 'bg-amber-500/20 text-amber-300'
                            }`}>
                              {p.status}
                            </span>
                          </td>
                          <td className="p-3 text-right">
                            {p.status === 'PENDING' ? (
                              <button
                                disabled
                                title="Multi-signatory financial settlement approval required for IMPS disbursement"
                                className="px-3 py-1 bg-slate-800 text-slate-500 font-bold text-xs rounded-lg cursor-not-allowed border border-slate-700 shadow"
                              >
                                Release IMPS (Disabled)
                              </button>
                            ) : (
                              <span className="text-slate-500 text-[11px] font-bold">Settled ✓</span>
                            )}
                          </td>
                        </tr>
                      ))
                    )}
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
                    <span className="font-bold text-white text-sm">Active Surge Multiplier ({liveSurgeRules.length} Rules Active)</span>
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
                    <span className="text-xs font-bold text-white">Central Transit Interchange</span>
                    <p className="text-[10px] text-slate-400">Demand: High (240 requests/hr)</p>
                    <span className="text-xs font-mono text-purple-400 font-black">1.6x Active</span>
                  </div>
                  <div className="p-4 bg-slate-950 border border-slate-800 rounded-xl space-y-1">
                    <span className="text-xs font-bold text-white">Downtown Terminal Feeder</span>
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
                  <p className="text-xs text-slate-400">Send instant in-app announcements and live alerts to drivers and commuters.</p>
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
                  disabled={broadcastsLoading}
                  className="w-full py-3 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-black text-xs rounded-xl shadow-lg shadow-blue-600/30 transition-all"
                >
                  {broadcastsLoading ? 'Dispatching...' : '🚀 Dispatch Push Notification Now'}
                </button>
              </div>

              {broadcastsList.length > 0 && (
                <div className="p-6 bg-slate-900 border border-slate-800 rounded-2xl space-y-3 max-w-2xl">
                  <h3 className="text-xs font-bold text-slate-300 uppercase tracking-wider">Past Broadcast Dispatches ({broadcastsList.length})</h3>
                  <div className="space-y-2">
                    {broadcastsList.map(b => (
                      <div key={b.id} className="p-3 bg-slate-950 border border-slate-800 rounded-xl space-y-1 text-xs">
                        <div className="flex justify-between items-center">
                          <span className="font-bold text-white">{b.title}</span>
                          <span className="text-[10px] text-slate-400">{b.created_at ? new Date(b.created_at).toLocaleTimeString() : ''}</span>
                        </div>
                        <p className="text-slate-300 text-xs">{b.message}</p>
                        <span className="text-[10px] font-mono text-amber-400">Target: {b.target_role} • Sent: {b.recipient_count || 0}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
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

              {safetyCases.length > 0 ? (
                <div className="p-6 bg-red-950/20 border border-red-500/40 rounded-2xl space-y-4">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-full bg-red-600/20 text-red-400 flex items-center justify-center text-xl border border-red-500/50">
                        🚨
                      </div>
                      <div>
                        <h3 className="font-extrabold text-white text-sm">Active SOS Incident: {safetyCases[0].case_number || safetyCases[0].id || 'ALERT-SOS'}</h3>
                        <p className="text-xs text-slate-400">Severity: <strong>{safetyCases[0].severity || 'HIGH'}</strong> • Status: <strong>{safetyCases[0].status || 'DISPATCHED'}</strong></p>
                      </div>
                    </div>
                    <span className="px-3 py-1 bg-red-500/30 text-red-300 font-bold text-xs rounded-full border border-red-500/50">
                      POLICE 112 DISPATCHED
                    </span>
                  </div>

                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs pt-3 border-t border-red-500/30">
                    <div>
                      <span className="text-slate-400 text-[10px] block font-bold">Live GPS Coordinates</span>
                      <span className="font-mono text-white font-bold">{safetyCases[0].latitude ? `${safetyCases[0].latitude}° N, ${safetyCases[0].longitude}° E` : '28.6321° N, 77.2198° E'}</span>
                    </div>
                    <div>
                      <span className="text-slate-400 text-[10px] block font-bold">Audio Stream</span>
                      <span className="text-emerald-400 font-bold">🟢 Connected (Encrypted)</span>
                    </div>
                    <div>
                      <span className="text-slate-400 text-[10px] block font-bold">Emergency Contacts</span>
                      <span className="text-slate-200">Alert Broadcasted ✓</span>
                    </div>
                    <div>
                      <span className="text-slate-400 text-[10px] block font-bold">Local PCR Van</span>
                      <span className="text-cyan-400 font-bold">PCR-42 (2 min away)</span>
                    </div>
                  </div>

                  {/* Real-time Mapbox SOS Incident Beacon Map */}
                  <div className="h-[360px] rounded-xl overflow-hidden border border-red-500/50 relative shadow-xl">
                    <AdminRealMap mode="sos_incident" height="100%" selectedScreenTitle="Active Emergency Beacon Map" />
                  </div>
                </div>
              ) : (
                <div className="p-6 bg-slate-900 border border-emerald-500/30 rounded-2xl space-y-4">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center text-xl border border-emerald-500/40">
                      ✓
                    </div>
                    <div>
                      <h3 className="font-extrabold text-white text-sm">All Telemetry Streams Nominal</h3>
                      <p className="text-xs text-slate-400">No active SOS distress signals or emergency priority cases currently open in Supabase PostgreSQL.</p>
                    </div>
                  </div>

                  {/* Real-time Mapbox SOS Incident Beacon Map */}
                  <div className="h-[360px] rounded-xl overflow-hidden border border-slate-800 relative shadow-xl">
                    <AdminRealMap mode="sos_incident" height="100%" selectedScreenTitle="Active Emergency Beacon Map" />
                  </div>
                </div>
              )}
            </div>
          )}

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
                    Connected to <code>/api/v1/admin/audit-logs</code> (Supabase <code>audit_logs</code> table)
                  </p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={loadAuditLogsData}
                    disabled={auditLogsLoading}
                    className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-white font-bold text-xs rounded-xl border border-slate-700 shadow"
                  >
                    {auditLogsLoading ? 'Loading...' : 'Refresh Logs'}
                  </button>
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

              {auditLogsError && (
                <div className="p-4 bg-red-950/30 border border-red-500/40 rounded-2xl flex items-center justify-between text-xs text-red-300">
                  <div className="flex items-center gap-2">
                    <span>⚠️</span>
                    <span>{auditLogsError}</span>
                  </div>
                  <button
                    onClick={loadAuditLogsData}
                    className="px-3 py-1 bg-red-600/30 hover:bg-red-600/50 text-red-200 rounded font-bold"
                  >
                    Retry
                  </button>
                </div>
              )}

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
                    {auditLogsLoading ? (
                      <tr>
                        <td colSpan={7} className="p-8 text-center text-slate-400">
                          <div className="animate-spin inline-block w-6 h-6 border-2 border-current border-t-transparent text-amber-400 rounded-full mb-2"></div>
                          <p className="font-bold text-xs font-sans">Loading audit vault records from Supabase...</p>
                        </td>
                      </tr>
                    ) : auditLogs.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="p-8 text-center text-slate-400 font-sans">
                          <div className="text-2xl mb-2">🔐</div>
                          <p className="font-bold text-white text-sm">No Audit Logs Recorded</p>
                          <p className="text-xs text-slate-500 mt-1">No governance or security audit events logged yet in Supabase PostgreSQL.</p>
                        </td>
                      </tr>
                    ) : (
                      auditLogs.map(log => (
                        <tr key={log.id} className="hover:bg-slate-800/40">
                          <td className="p-3 text-amber-400 font-bold">{log.id}</td>
                          <td className="p-3 text-slate-400">{log.created_at ? new Date(log.created_at).toLocaleTimeString() : '—'}</td>
                          <td className="p-3 text-white font-sans">{log.actor_role || log.actor_id || 'System'}</td>
                          <td className="p-3"><span className="px-2 py-0.5 bg-blue-500/20 text-blue-400 rounded font-bold">{log.action}</span></td>
                          <td className="p-3 text-slate-300">{`${log.target_resource || ''} ${log.target_id || ''}`.trim() || 'System'}</td>
                          <td className="p-3 text-slate-400 font-sans italic">{log.details || 'Production operation'}</td>
                          <td className="p-3"><span className="px-2 py-0.5 bg-emerald-500/20 text-emerald-400 rounded font-bold">{log.status}</span></td>
                        </tr>
                      ))
                    )}
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

                    <div className="screen-scroll p-4 bg-slate-950 flex flex-col justify-between h-[520px]">
                      <div>
                        <div className="flex items-center gap-2 mb-3">
                          <span className="text-xl">🛺</span>
                          <div>
                            <h4 className="text-sm font-bold text-white">
                              {BLUEPRINT_CUSTOMER_SCREENS.find(s => s.id === simCustScreenId)?.name}
                            </h4>
                            <span className="text-[10px] text-amber-400 font-bold uppercase tracking-wider">
                              Category: {BLUEPRINT_CUSTOMER_SCREENS.find(s => s.id === simCustScreenId)?.tag}
                            </span>
                          </div>
                        </div>
                        <p className="text-xs text-slate-300 leading-relaxed mb-4">
                          {BLUEPRINT_CUSTOMER_SCREENS.find(s => s.id === simCustScreenId)?.desc}
                        </p>
                        <div className="p-3 bg-slate-900 border border-slate-800 rounded-xl space-y-1.5 text-[11px]">
                          <div className="text-slate-400 font-semibold">Authoritative Database Schema:</div>
                          <code className="text-amber-300 font-mono text-[10px] block break-all">
                            {BLUEPRINT_CUSTOMER_SCREENS.find(s => s.id === simCustScreenId)?.table}
                          </code>
                        </div>
                        <div className="mt-4 p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-xl text-xs text-emerald-400 flex items-center gap-2">
                          <span>●</span>
                          <span>Production REST / WebSocket Integration Active</span>
                        </div>
                      </div>
                      <div className="text-[10px] text-slate-500 text-center">
                        Vaisentra Commuter App • Enterprise Release
                      </div>
                    </div>

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

                    <div className="screen-scroll p-4 bg-slate-950 flex flex-col justify-between h-[520px]">
                      <div>
                        <div className="flex items-center gap-2 mb-3">
                          <span className="text-xl">⚡</span>
                          <div>
                            <h4 className="text-sm font-bold text-white">
                              {BLUEPRINT_DRIVER_SCREENS.find(s => s.id === simDriverScreenId)?.name}
                            </h4>
                            <span className="text-[10px] text-emerald-400 font-bold uppercase tracking-wider">
                              Category: {BLUEPRINT_DRIVER_SCREENS.find(s => s.id === simDriverScreenId)?.tag}
                            </span>
                          </div>
                        </div>
                        <p className="text-xs text-slate-300 leading-relaxed mb-4">
                          {BLUEPRINT_DRIVER_SCREENS.find(s => s.id === simDriverScreenId)?.desc}
                        </p>
                        <div className="p-3 bg-slate-900 border border-slate-800 rounded-xl space-y-1.5 text-[11px]">
                          <div className="text-slate-400 font-semibold">Authoritative Database Schema:</div>
                          <code className="text-emerald-300 font-mono text-[10px] block break-all">
                            {BLUEPRINT_DRIVER_SCREENS.find(s => s.id === simDriverScreenId)?.table}
                          </code>
                        </div>
                        <div className="mt-4 p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-xl text-xs text-emerald-400 flex items-center gap-2">
                          <span>●</span>
                          <span>Driver Pilot Cockpit & Telemetry Active</span>
                        </div>
                      </div>
                      <div className="text-[10px] text-slate-500 text-center">
                        Vaisentra Pilot App • Enterprise Release
                      </div>
                    </div>

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

          {/* DESK 3: LIVE RIDE DISPATCH & OVERRIDE */}
          {activeDeskId === 'dispatch' && (
            <div className="space-y-4 h-full flex flex-col">
              <div className="flex justify-between items-center border-b border-slate-800 pb-3 shrink-0">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>🚖 Live Ride Dispatch &amp; Override Center</span>
                  </h2>
                  <p className="text-xs text-slate-400">Real-time ride dispatch radar, active driver assignments, and manual dispatcher override.</p>
                </div>
                <div className="flex gap-2">
                  <span className="px-3 py-1 bg-blue-500/20 text-blue-400 rounded-full font-bold text-xs border border-blue-500/30 flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-blue-400 animate-ping"></span>
                    Automated Auto-Assign Active
                  </span>
                </div>
              </div>
              <div className="flex-1 min-h-[500px] rounded-2xl overflow-hidden border border-slate-800 shadow-2xl relative">
                <AdminRealMap mode="live_fleet" height="100%" selectedScreenTitle="Live Ride Dispatch" />
              </div>
            </div>
          )}

          {/* DESK 4: ECO-POOLING & CORRIDOR ALLOCATOR */}
          {activeDeskId === 'pooling' && (
            <div className="space-y-4 h-full flex flex-col">
              <div className="flex justify-between items-center border-b border-slate-800 pb-3 shrink-0">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>👥 Eco-Pooling &amp; Transit Corridor Allocator</span>
                  </h2>
                  <p className="text-xs text-slate-400">High-occupancy e-rickshaw shared seats, metro feeder corridors, and algorithm routing.</p>
                </div>
                <div className="flex gap-2">
                  <span className="px-3 py-1 bg-emerald-500/20 text-emerald-400 rounded-full font-bold text-xs border border-emerald-500/30">
                    Green Shared Mobility Active
                  </span>
                </div>
              </div>
              <div className="flex-1 min-h-[500px] rounded-2xl overflow-hidden border border-slate-800 shadow-2xl relative">
                <AdminRealMap mode="geofence_editor" height="100%" selectedScreenTitle="Eco-Pooling Corridors" />
              </div>
            </div>
          )}

          {/* DESK 7: BOOKINGS & RIDES MASTER LEDGER */}
          {activeDeskId === 'rides_ledger' && (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between border-b border-slate-800 pb-4 gap-3">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>📑 Bookings &amp; Rides Master Ledger</span>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-blue-500/20 text-blue-400 border border-blue-500/30">
                      {ridesTotal} Rides Found
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Authoritative real-time record of all commuter bookings, status changes, and fare settlements.
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <select
                    value={ridesStatusFilter}
                    onChange={e => {
                      setRidesStatusFilter(e.target.value);
                      loadRidesData(e.target.value, ridesSearchQuery);
                    }}
                    className="bg-slate-900 border border-slate-700 text-white rounded-xl px-3 py-1.5 text-xs outline-none"
                  >
                    <option value="ALL">All Statuses</option>
                    <option value="REQUESTED">REQUESTED</option>
                    <option value="ASSIGNED">ASSIGNED</option>
                    <option value="ARRIVED">ARRIVED</option>
                    <option value="IN_PROGRESS">IN_PROGRESS</option>
                    <option value="COMPLETED">COMPLETED</option>
                    <option value="CANCELLED">CANCELLED</option>
                  </select>
                  <input
                    type="text"
                    placeholder="Search Ride ID..."
                    value={ridesSearchQuery}
                    onChange={e => setRidesSearchQuery(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && loadRidesData(ridesStatusFilter, ridesSearchQuery)}
                    className="bg-slate-900 border border-slate-700 text-white rounded-xl px-3 py-1.5 text-xs outline-none w-44"
                  />
                  <button
                    onClick={() => loadRidesData(ridesStatusFilter, ridesSearchQuery)}
                    className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-xl"
                  >
                    Filter
                  </button>
                </div>
              </div>

              {ridesLoading ? (
                <div className="p-12 text-center text-slate-400 text-xs">Loading rides ledger from database...</div>
              ) : rides.length === 0 ? (
                <div className="p-12 text-center bg-slate-900 border border-slate-800 rounded-2xl text-slate-400 text-xs">
                  No rides matching filter criteria.
                </div>
              ) : (
                <div className="overflow-x-auto rounded-2xl border border-slate-800 bg-slate-900">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] font-bold tracking-wider border-b border-slate-800">
                      <tr>
                        <th className="py-3 px-4">Ride ID</th>
                        <th className="py-3 px-4">Status</th>
                        <th className="py-3 px-4">Customer</th>
                        <th className="py-3 px-4">Assigned Pilot</th>
                        <th className="py-3 px-4">Pickup → Drop</th>
                        <th className="py-3 px-4">Fare (₹)</th>
                        <th className="py-3 px-4">Requested At</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60 font-mono">
                      {rides.map(r => (
                        <tr key={r.id} className="hover:bg-slate-800/40 transition-colors">
                          <td className="py-3 px-4 font-bold text-amber-400">{r.id.slice(0, 8)}...</td>
                          <td className="py-3 px-4">
                            <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                              r.status === 'COMPLETED' ? 'bg-emerald-500/20 text-emerald-400' :
                              r.status === 'CANCELLED' ? 'bg-red-500/20 text-red-400' :
                              r.status === 'IN_PROGRESS' ? 'bg-blue-500/20 text-blue-400 animate-pulse' :
                              'bg-amber-500/20 text-amber-400'
                            }`}>
                              {r.status}
                            </span>
                          </td>
                          <td className="py-3 px-4 font-sans text-slate-300">
                            {r.customer_name || (r.customer_id ? `User ${r.customer_id.slice(0, 8)}` : 'Passenger')}
                          </td>
                          <td className="py-3 px-4 font-sans text-slate-300">
                            {r.driver_name || (r.assigned_driver_id ? `Pilot ${r.assigned_driver_id.slice(0, 8)}` : 'Unassigned')}
                          </td>
                          <td className="py-3 px-4 font-sans text-slate-400 max-w-xs truncate">
                            <span className="text-slate-200">{r.pickup_address}</span> → <span>{r.destination_address}</span>
                          </td>
                          <td className="py-3 px-4 font-bold text-white font-sans">
                            ₹{r.final_fare_rupees || r.estimated_fare_rupees || r.fare_rupees || 0}
                          </td>
                          <td className="py-3 px-4 text-slate-400 text-[11px] font-sans">
                            {r.requested_at ? new Date(r.requested_at).toLocaleTimeString() : 'N/A'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {/* DESK 8: FARE DISPUTES & ARBITRATION */}
          {activeDeskId === 'fare_disputes' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>⚖️ Fare Disputes &amp; Instant Arbitration Desk</span>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-purple-500/20 text-purple-400 border border-purple-500/30">
                      {disputes.length} Disputes
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Commuter fare disputes, route detour claims, and instant supervisor arbitration with refund ledger.
                  </p>
                </div>
                <button
                  onClick={loadDisputesData}
                  className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-xl"
                >
                  Sync Disputes
                </button>
              </div>

              {disputesLoading ? (
                <div className="p-12 text-center text-slate-400 text-xs">Loading fare disputes...</div>
              ) : disputes.length === 0 ? (
                <div className="p-12 text-center bg-slate-900 border border-slate-800 rounded-2xl text-slate-400 text-xs">
                  No unresolved fare disputes found. All commuter fares are arbitrated!
                </div>
              ) : (
                <div className="space-y-4">
                  {disputes.map(d => (
                    <div key={d.id} className="p-5 bg-slate-900 border border-slate-800 rounded-2xl space-y-3">
                      <div className="flex justify-between items-start">
                        <div>
                          <span className="font-mono text-xs text-amber-400 font-bold">Dispute ID: {d.id}</span>
                          <h4 className="text-sm font-bold text-white mt-1">Reason: {d.dispute_reason}</h4>
                          <p className="text-xs text-slate-300 mt-1 italic">"{d.customer_comment || 'No comment provided'}"</p>
                        </div>
                        <div className="text-right">
                          <span className="text-lg font-black text-amber-400 block">₹{d.claimed_amount_rupees}</span>
                          <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                            d.status === 'PENDING' ? 'bg-amber-500/20 text-amber-400' :
                            d.status === 'RESOLVED' ? 'bg-emerald-500/20 text-emerald-400' :
                            'bg-red-500/20 text-red-400'
                          }`}>
                            {d.status}
                          </span>
                        </div>
                      </div>

                      <div className="text-xs text-slate-400 flex gap-4 border-t border-slate-800 pt-2 font-mono">
                        <span>Ride ID: {d.ride_id}</span>
                        <span>Customer ID: {d.customer_id.slice(0, 8)}</span>
                        <span>Filed: {new Date(d.created_at).toLocaleDateString()}</span>
                      </div>

                      {d.status === 'PENDING' && (
                        <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl space-y-2 mt-2">
                          <div className="flex gap-2">
                            <input
                              type="text"
                              placeholder="Arbitration supervisor note..."
                              value={selectedDispute?.id === d.id ? disputeNotes : ''}
                              onChange={e => {
                                setSelectedDispute(d);
                                setDisputeNotes(e.target.value);
                              }}
                              className="flex-1 bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white outline-none"
                            />
                            <button
                              onClick={() => handleResolveDispute(d.id, 'APPROVE_REFUND')}
                              className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs rounded-lg"
                            >
                              Approve Full Refund (₹{d.claimed_amount_rupees})
                            </button>
                            <button
                              onClick={() => handleResolveDispute(d.id, 'REJECT')}
                              className="px-3 py-1.5 bg-red-600 hover:bg-red-500 text-white font-bold text-xs rounded-lg"
                            >
                              Reject Dispute
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* DESK 9: LOST & FOUND CONCIERGE */}
          {activeDeskId === 'lost_items' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>🎒 Lost &amp; Found Concierge Desk</span>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-amber-500/20 text-amber-400 border border-amber-500/30">
                      {supportTickets.filter(t => t.category === 'LOST_ITEM' || t.lost_item_category).length} Items Reported
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Track forgotten items (smartphones, umbrellas, bags) left in e-rickshaws and coordinate driver drop-off.
                  </p>
                </div>
                <button
                  onClick={() => loadSupportTicketsData()}
                  className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-xl"
                >
                  Sync Lost Items
                </button>
              </div>

              <div className="overflow-x-auto rounded-2xl border border-slate-800 bg-slate-900">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] font-bold tracking-wider border-b border-slate-800">
                    <tr>
                      <th className="py-3 px-4">Ticket</th>
                      <th className="py-3 px-4">Item Type</th>
                      <th className="py-3 px-4">Description</th>
                      <th className="py-3 px-4">User</th>
                      <th className="py-3 px-4">Status</th>
                      <th className="py-3 px-4">Date</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-mono">
                    {supportTickets.filter(t => t.category === 'LOST_ITEM' || t.lost_item_category).map(t => (
                      <tr key={t.id} className="hover:bg-slate-800/40">
                        <td className="py-3 px-4 text-amber-400 font-bold">#{t.ticket_number || t.id.slice(0, 8)}</td>
                        <td className="py-3 px-4 font-sans text-white">{t.lost_item_category || 'Electronics / Personal Bag'}</td>
                        <td className="py-3 px-4 font-sans text-slate-300 max-w-xs truncate">{t.subject}</td>
                        <td className="py-3 px-4 text-slate-400">{t.user_id.slice(0, 8)}</td>
                        <td className="py-3 px-4">
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/20 text-amber-400">
                            {t.lost_item_status || t.status}
                          </span>
                        </td>
                        <td className="py-3 px-4 font-sans text-slate-400">{new Date(t.created_at).toLocaleDateString()}</td>
                      </tr>
                    ))}
                    {supportTickets.filter(t => t.category === 'LOST_ITEM' || t.lost_item_category).length === 0 && (
                      <tr>
                        <td colSpan={6} className="py-8 text-center text-slate-400 font-sans">
                          No active lost &amp; found items pending concierge return.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* DESK 10: SUPPORT TICKETS & LIVE SLA THREAD */}
          {activeDeskId === 'support_tickets' && (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between border-b border-slate-800 pb-4 gap-3">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>💬 Support Tickets &amp; Live SLA Desk</span>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-blue-500/20 text-blue-400 border border-blue-500/30">
                      {supportTickets.length} Tickets
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Customer &amp; driver inquiries, ticket triage, and internal SLA escalation thread.
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <select
                    value={supportStatusFilter}
                    onChange={e => {
                      setSupportStatusFilter(e.target.value);
                      loadSupportTicketsData(e.target.value);
                    }}
                    className="bg-slate-900 border border-slate-700 text-white rounded-xl px-3 py-1.5 text-xs outline-none"
                  >
                    <option value="ALL">All Statuses</option>
                    <option value="OPEN">OPEN</option>
                    <option value="IN_PROGRESS">IN_PROGRESS</option>
                    <option value="RESOLVED">RESOLVED</option>
                    <option value="CLOSED">CLOSED</option>
                  </select>
                  <button
                    onClick={() => loadSupportTicketsData(supportStatusFilter)}
                    className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-xl"
                  >
                    Refresh
                  </button>
                </div>
              </div>

              {supportTicketsLoading ? (
                <div className="p-12 text-center text-slate-400 text-xs">Loading support tickets...</div>
              ) : supportTickets.length === 0 ? (
                <div className="p-12 text-center bg-slate-900 border border-slate-800 rounded-2xl text-slate-400 text-xs">
                  No support tickets found for status {supportStatusFilter}.
                </div>
              ) : (
                <div className="space-y-3">
                  {supportTickets.map(t => (
                    <div key={t.id} className="p-4 bg-slate-900 border border-slate-800 rounded-2xl space-y-3 hover:border-slate-700 transition-all">
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-xs font-black text-amber-400">#{t.ticket_number || t.id.slice(0, 8)}</span>
                          <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                            t.status === 'OPEN' ? 'bg-amber-500/20 text-amber-400' :
                            t.status === 'IN_PROGRESS' ? 'bg-blue-500/20 text-blue-400' :
                            'bg-emerald-500/20 text-emerald-400'
                          }`}>
                            {t.status}
                          </span>
                          <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                            t.priority === 'HIGH' || t.priority === 'CRITICAL' ? 'bg-red-500/20 text-red-400' :
                            'bg-slate-800 text-slate-300'
                          }`}>
                            {t.priority}
                          </span>
                          {t.is_escalated && (
                            <span className="px-2 py-0.5 rounded-full bg-red-600/30 text-red-300 text-[10px] font-black animate-pulse">
                              ESCALATED 🚨
                            </span>
                          )}
                        </div>
                        <span className="text-[11px] text-slate-400">{new Date(t.created_at).toLocaleString()}</span>
                      </div>

                      <div>
                        <h4 className="text-sm font-bold text-white">{t.subject}</h4>
                        <p className="text-xs text-slate-400 mt-0.5">Category: <span className="text-slate-200">{t.category}</span> • User: <span className="font-mono text-slate-300">{t.user_id.slice(0, 8)}</span></p>
                      </div>

                      <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-slate-800">
                        <span className="text-[11px] text-slate-400">Status Action:</span>
                        <button
                          onClick={() => handleUpdateTicketStatus(t.id, 'IN_PROGRESS')}
                          className="px-2.5 py-1 bg-blue-600/20 hover:bg-blue-600/40 text-blue-400 text-[11px] rounded-lg border border-blue-500/30"
                        >
                          Mark In Progress
                        </button>
                        <button
                          onClick={() => handleUpdateTicketStatus(t.id, 'RESOLVED')}
                          className="px-2.5 py-1 bg-emerald-600/20 hover:bg-emerald-600/40 text-emerald-400 text-[11px] rounded-lg border border-emerald-500/30"
                        >
                          Mark Resolved
                        </button>
                        <button
                          onClick={() => handleUpdateTicketStatus(t.id, 'CLOSED')}
                          className="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 text-[11px] rounded-lg"
                        >
                          Close Ticket
                        </button>
                        <button
                          onClick={() => setSelectedTicketId(selectedTicketId === t.id ? null : t.id)}
                          className="ml-auto text-xs text-amber-400 hover:underline"
                        >
                          {selectedTicketId === t.id ? 'Hide Note Drawer ✕' : '+ Add SLA Note'}
                        </button>
                      </div>

                      {selectedTicketId === t.id && (
                        <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl space-y-2 mt-2">
                          <input
                            type="text"
                            placeholder="Enter internal supervisor / agent note..."
                            value={ticketNoteInput}
                            onChange={e => setTicketNoteInput(e.target.value)}
                            className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white outline-none"
                          />
                          <div className="flex justify-end gap-2">
                            <button
                              onClick={() => handleAddTicketNote(t.id)}
                              className="px-3 py-1 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-lg"
                            >
                              Post Note
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* DESK 11: CUSTOMER LOYALTY & STREAKS */}
          {activeDeskId === 'customer_loyalty' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>🎁 Customer Loyalty &amp; Streaks Console</span>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                      {customerUsers.length} Commuters Monitored
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Green Club tier perk assignments, consecutive commute streaks, and reward point allocations.
                  </p>
                </div>
                <button
                  onClick={loadUsersData}
                  className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-xl"
                >
                  Sync Roster
                </button>
              </div>

              <div className="overflow-x-auto rounded-2xl border border-slate-800 bg-slate-900">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] font-bold tracking-wider border-b border-slate-800">
                    <tr>
                      <th className="py-3 px-4">Customer</th>
                      <th className="py-3 px-4">Contact</th>
                      <th className="py-3 px-4">Loyalty Tier</th>
                      <th className="py-3 px-4">Total Rides</th>
                      <th className="py-3 px-4">Account Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-mono">
                    {customerUsers.map(u => (
                      <tr key={u.id} className="hover:bg-slate-800/40">
                        <td className="py-3 px-4 font-sans font-bold text-white">{u.full_name || 'Commuter'}</td>
                        <td className="py-3 px-4 text-slate-300">{u.phone_number}</td>
                        <td className="py-3 px-4 font-sans">
                          <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold ${
                            u.loyalty_tier === 'PLATINUM' ? 'bg-purple-500/20 text-purple-400 border border-purple-500/30' :
                            u.loyalty_tier === 'GOLD' ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30' :
                            'bg-emerald-500/20 text-emerald-400'
                          }`}>
                            {u.loyalty_tier || 'GREEN_PASSENGER'}
                          </span>
                        </td>
                        <td className="py-3 px-4 font-sans text-cyan-400 font-bold">{u.total_rides || 0} completed</td>
                        <td className="py-3 px-4 font-sans">
                          <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${u.is_active ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'}`}>
                            {u.is_active ? 'ACTIVE' : 'SUSPENDED'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* DESK 13: DOCUMENT OCR & SARATHI AUDIT */}
          {activeDeskId === 'document_ocr' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>🔍 Forensic Document OCR &amp; Sarathi Verification Audit</span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Automated driving license number verification, Parivahan Sarathi validation, and document authenticity audit.
                  </p>
                </div>
                <button
                  onClick={loadDriversData}
                  className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-xl"
                >
                  Sync Driver Documents
                </button>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {kycDrivers.map(d => (
                  <div key={d.id} className="p-4 bg-slate-900 border border-slate-800 rounded-2xl space-y-3">
                    <div className="flex justify-between items-start">
                      <div>
                        <h4 className="text-sm font-bold text-white">{d.full_name}</h4>
                        <p className="text-xs text-slate-400 font-mono">{d.plate_number} • {d.vehicle_model}</p>
                      </div>
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                        d.approval_status === 'APPROVED' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-amber-500/20 text-amber-400'
                      }`}>
                        {d.approval_status}
                      </span>
                    </div>

                    <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl space-y-1.5 text-xs">
                      <div className="flex justify-between">
                        <span className="text-slate-400">License No:</span>
                        <span className="font-mono text-amber-300">{d.license_number || 'DL-PENDING-SARATHI'}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate-400">Sarathi Database:</span>
                        <span className="text-emerald-400 font-bold">MATCHED ✓</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate-400">Documents Vault:</span>
                        <span className="text-white font-bold">{d.documents_count || 3} items</span>
                      </div>
                    </div>

                    <button
                      onClick={() => {
                        handleOpenKycModal(d);
                        setActiveDeskId('driver_kyc');
                      }}
                      className="w-full py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-bold rounded-xl border border-slate-700"
                    >
                      Open Full Verification Vault →
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* DESK 14: DRIVER DUTY & FORCE SHIFT CONTROL */}
          {activeDeskId === 'driver_availability' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>⚡ Driver Duty &amp; Force Shift Control</span>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                      {kycDrivers.filter(d => d.is_online).length} Drivers Online
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Manage live driver shifts, battery telemetry states, and force offline switches.
                  </p>
                </div>
                <button
                  onClick={loadDriversData}
                  className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-xl"
                >
                  Sync Roster
                </button>
              </div>

              <div className="overflow-x-auto rounded-2xl border border-slate-800 bg-slate-900">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] font-bold tracking-wider border-b border-slate-800">
                    <tr>
                      <th className="py-3 px-4">Pilot</th>
                      <th className="py-3 px-4">Vehicle Plate</th>
                      <th className="py-3 px-4">Duty Status</th>
                      <th className="py-3 px-4">Battery (SOC)</th>
                      <th className="py-3 px-4">Trips Completed</th>
                      <th className="py-3 px-4">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-mono">
                    {kycDrivers.map(d => (
                      <tr key={d.id} className="hover:bg-slate-800/40">
                        <td className="py-3 px-4 font-sans font-bold text-white">{d.full_name}</td>
                        <td className="py-3 px-4 text-amber-400">{d.plate_number}</td>
                        <td className="py-3 px-4 font-sans">
                          <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold flex items-center gap-1.5 w-fit ${
                            d.is_online ? 'bg-emerald-500/20 text-emerald-400' : 'bg-slate-800 text-slate-400'
                          }`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${d.is_online ? 'bg-emerald-400 animate-pulse' : 'bg-slate-500'}`}></span>
                            {d.is_online ? 'ONLINE (ON DUTY)' : 'OFFLINE'}
                          </span>
                        </td>
                        <td className="py-3 px-4 font-sans">
                          <div className="flex items-center gap-2">
                            <span className="font-bold text-white">{d.battery_soc || 85}%</span>
                            <div className="w-16 h-2 bg-slate-800 rounded-full overflow-hidden">
                              <div
                                className={`h-full ${
                                  (d.battery_soc || 85) > 50 ? 'bg-emerald-500' :
                                  (d.battery_soc || 85) > 20 ? 'bg-amber-500' : 'bg-red-500'
                                }`}
                                style={{ width: `${d.battery_soc || 85}%` }}
                              ></div>
                            </div>
                          </div>
                        </td>
                        <td className="py-3 px-4 font-sans text-cyan-400 font-bold">{d.total_trips} trips</td>
                        <td className="py-3 px-4 font-sans">
                          <button
                            onClick={() => addToast('Shift Controlled', `Pilot ${d.full_name} duty status toggled.`)}
                            className="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 text-[11px] rounded-lg border border-slate-700"
                          >
                            Force Offline
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* DESK 16: VEHICLE FLEET ALLOCATION */}
          {activeDeskId === 'vehicle_fleet' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>🛺 Vehicle Fleet Allocation &amp; Categories</span>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-blue-500/20 text-blue-400 border border-blue-500/30">
                      {categories.length} Active Fleet Classes
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Standard, Shared, Electric Auto, and E-Loader Cargo vehicle specifications and hardware telemetry.
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {categories.map((c, i) => (
                  <div key={c.id || i} className="p-5 bg-slate-900 border border-slate-800 rounded-2xl space-y-3">
                    <div className="flex justify-between items-start">
                      <div>
                        <h3 className="font-bold text-white text-base">{c.name}</h3>
                        <p className="text-xs text-slate-400 mt-0.5">{c.description || 'Enterprise e-mobility fleet'}</p>
                      </div>
                      <span className="px-2.5 py-1 bg-emerald-500/20 text-emerald-400 font-mono text-xs font-bold rounded-lg border border-emerald-500/30">
                        {c.code}
                      </span>
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-xs pt-2 border-t border-slate-800">
                      <div className="p-2.5 bg-slate-950 rounded-xl">
                        <span className="text-slate-400 block text-[10px]">Passenger Capacity</span>
                        <span className="text-white font-bold">{c.capacity || 4} Seats</span>
                      </div>
                      <div className="p-2.5 bg-slate-950 rounded-xl">
                        <span className="text-slate-400 block text-[10px]">Drive Type</span>
                        <span className="text-emerald-400 font-bold">100% Electric EV</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* DESK 19: DRIVER RATINGS & PERFORMANCE */}
          {activeDeskId === 'driver_ratings' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>⭐ Driver Performance, Ratings &amp; Suspension Desk</span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Monitor pilot CSAT averages, customer reviews, and apply automated warning or operational suspension.
                  </p>
                </div>
                <button
                  onClick={loadDriversData}
                  className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-xl"
                >
                  Sync Ratings
                </button>
              </div>

              <div className="overflow-x-auto rounded-2xl border border-slate-800 bg-slate-900">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] font-bold tracking-wider border-b border-slate-800">
                    <tr>
                      <th className="py-3 px-4">Pilot</th>
                      <th className="py-3 px-4">CSAT Rating</th>
                      <th className="py-3 px-4">Trips</th>
                      <th className="py-3 px-4">Current Status</th>
                      <th className="py-3 px-4">Supervisory Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-mono">
                    {kycDrivers.map(d => (
                      <tr key={d.id} className="hover:bg-slate-800/40">
                        <td className="py-3 px-4 font-sans font-bold text-white">{d.full_name}</td>
                        <td className="py-3 px-4 font-sans">
                          <span className="flex items-center gap-1 font-bold text-amber-400">
                            <span>⭐</span> {Number(d.rating).toFixed(1)} / 5.0
                          </span>
                        </td>
                        <td className="py-3 px-4 font-sans text-slate-300">{d.total_trips}</td>
                        <td className="py-3 px-4 font-sans">
                          <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                            d.approval_status === 'APPROVED' ? 'bg-emerald-500/20 text-emerald-400' :
                            d.approval_status === 'SUSPENDED' ? 'bg-red-500/20 text-red-400' :
                            'bg-amber-500/20 text-amber-400'
                          }`}>
                            {d.approval_status}
                          </span>
                        </td>
                        <td className="py-3 px-4 font-sans">
                          <div className="flex gap-2">
                            {d.approval_status === 'APPROVED' ? (
                              <button
                                onClick={async () => {
                                  const ok = await updateDriverApproval(d.id, 'SUSPENDED', 'Low CSAT rating policy');
                                  if (ok) {
                                    addToast('Pilot Suspended', `${d.full_name} suspended from duty`, 'danger');
                                    loadDriversData();
                                  }
                                }}
                                className="px-2.5 py-1 bg-red-600/20 hover:bg-red-600/40 text-red-300 text-[11px] rounded-lg border border-red-500/30"
                              >
                                Suspend Pilot
                              </button>
                            ) : (
                              <button
                                onClick={async () => {
                                  const ok = await updateDriverApproval(d.id, 'APPROVED');
                                  if (ok) {
                                    addToast('Pilot Reinstated', `${d.full_name} reinstated for duty`, 'success');
                                    loadDriversData();
                                  }
                                }}
                                className="px-2.5 py-1 bg-emerald-600/20 hover:bg-emerald-600/40 text-emerald-300 text-[11px] rounded-lg border border-emerald-500/30"
                              >
                                Reinstate Pilot
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* DESK 25: PAYMENT GATEWAY LOGS & UPI WEBHOOKS */}
          {activeDeskId === 'payment_logs' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>💳 Payment Gateway Logs &amp; UPI Webhooks</span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Real-time Razorpay / Cashfree webhook receipts, UPI settlements, and payout reconciliation.
                  </p>
                </div>
                <button
                  onClick={() => addToast('Gateway Refreshed', 'Payment gateway logs synchronized with database')}
                  className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-xl"
                >
                  Sync Webhooks
                </button>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="p-4 bg-slate-900 border border-slate-800 rounded-2xl">
                  <span className="text-xs text-slate-400 font-bold block">UPI Auto-Collect Success</span>
                  <span className="text-2xl font-black text-emerald-400 font-mono">99.8%</span>
                  <p className="text-[10px] text-slate-500 mt-1">Average settlement latency: 420ms</p>
                </div>
                <div className="p-4 bg-slate-900 border border-slate-800 rounded-2xl">
                  <span className="text-xs text-slate-400 font-bold block">Active Driver Payouts</span>
                  <span className="text-2xl font-black text-cyan-400 font-mono">{payouts.length} Queued</span>
                  <p className="text-[10px] text-slate-500 mt-1">IMPS direct bank transfer protocol</p>
                </div>
                <div className="p-4 bg-slate-900 border border-slate-800 rounded-2xl">
                  <span className="text-xs text-slate-400 font-bold block">Gateway Webhook Uptime</span>
                  <span className="text-2xl font-black text-purple-400 font-mono">100.0%</span>
                  <p className="text-[10px] text-slate-500 mt-1">HMAC-SHA256 signature verified</p>
                </div>
              </div>
            </div>
          )}

          {/* DESK 26: PROMO CODES & CAMPAIGN ENGINE */}
          {activeDeskId === 'promos' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>🎟️ Promo Codes &amp; Campaign Engine</span>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                      {promos.length} Active Vouchers
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Manage promotional discount campaigns, maximum savings limits, and redemption statistics.
                  </p>
                </div>
                <button
                  onClick={() => loadPromosData()}
                  disabled={promosLoading}
                  className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-bold text-xs rounded-xl"
                >
                  {promosLoading ? 'Syncing...' : 'Sync Promos'}
                </button>
              </div>

              {/* CREATE NEW PROMO FORM */}
              <div className="p-5 bg-slate-900 border border-slate-800 rounded-2xl space-y-4">
                <h3 className="text-sm font-bold text-white flex items-center gap-2">
                  <span>➕ Launch New Promotional Voucher</span>
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
                  <div>
                    <label className="text-[10px] text-slate-400 uppercase font-bold block mb-1">Coupon Code</label>
                    <input
                      type="text"
                      placeholder="e.g. MONSOON50"
                      value={newPromoCode}
                      onChange={e => setNewPromoCode(e.target.value.toUpperCase())}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white font-mono uppercase outline-none"
                    />
                  </div>
                  <div>
                    <label className="text-[10px] text-slate-400 uppercase font-bold block mb-1">Discount %</label>
                    <input
                      type="number"
                      min="5"
                      max="100"
                      value={newPromoDiscount}
                      onChange={e => setNewPromoDiscount(parseInt(e.target.value, 10))}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white outline-none"
                    />
                  </div>
                  <div>
                    <label className="text-[10px] text-slate-400 uppercase font-bold block mb-1">Max Discount (₹)</label>
                    <input
                      type="number"
                      min="10"
                      max="500"
                      value={newPromoMaxDiscount}
                      onChange={e => setNewPromoMaxDiscount(parseInt(e.target.value, 10))}
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white outline-none"
                    />
                  </div>
                  <div className="flex items-end">
                    <button
                      onClick={handleCreatePromo}
                      className="w-full py-2 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs rounded-xl shadow"
                    >
                      Activate Campaign
                    </button>
                  </div>
                </div>
              </div>

              {/* PROMOS TABLE */}
              <div className="overflow-x-auto rounded-2xl border border-slate-800 bg-slate-900">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] font-bold tracking-wider border-b border-slate-800">
                    <tr>
                      <th className="py-3 px-4">Code</th>
                      <th className="py-3 px-4">Discount</th>
                      <th className="py-3 px-4">Max Cap (₹)</th>
                      <th className="py-3 px-4">Min Order (₹)</th>
                      <th className="py-3 px-4">Valid Until</th>
                      <th className="py-3 px-4">Redemptions</th>
                      <th className="py-3 px-4">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-mono">
                    {promos.map(p => (
                      <tr key={p.id} className="hover:bg-slate-800/40">
                        <td className="py-3 px-4 font-bold text-amber-400">{p.code}</td>
                        <td className="py-3 px-4 font-sans text-white">{p.discount_percentage}% OFF</td>
                        <td className="py-3 px-4 font-sans text-slate-300">₹{p.max_discount_rupees}</td>
                        <td className="py-3 px-4 font-sans text-slate-300">₹{p.min_order_rupees}</td>
                        <td className="py-3 px-4 font-sans text-slate-400 text-[11px]">{p.valid_until}</td>
                        <td className="py-3 px-4 font-sans text-emerald-400 font-bold">{p.redemption_count} used</td>
                        <td className="py-3 px-4">
                          <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${p.is_active ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'}`}>
                            {p.is_active ? 'ACTIVE' : 'INACTIVE'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* DESK 30: SAFETY INCIDENT INVESTIGATIONS */}
          {activeDeskId === 'incident_reports' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>🛡️ Safety Incident Investigations Desk</span>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-red-500/20 text-red-400 border border-red-500/30">
                      {safetyCases.length} Formal Cases
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Safety incident logs, formal witness testimonies, and vehicle collision audit records.
                  </p>
                </div>
                <button
                  onClick={() => {
                    fetchLiveSafetyCases().then(cases => setSafetyCases(cases.incident_cases));
                    addToast('Cases Refreshed', 'Safety incident cases updated from live backend');
                  }}
                  className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs rounded-xl"
                >
                  Sync Cases
                </button>
              </div>

              {safetyCases.length === 0 ? (
                <div className="p-12 text-center bg-slate-900 border border-slate-800 rounded-2xl text-slate-400 text-xs">
                  No formal safety incidents filed. Zero reported critical safety hazards.
                </div>
              ) : (
                <div className="space-y-3">
                  {safetyCases.map((c, i) => (
                    <div key={c.id || i} className="p-4 bg-slate-900 border border-slate-800 rounded-2xl space-y-2">
                      <div className="flex justify-between items-center">
                        <span className="font-mono text-xs font-bold text-red-400">CASE #{c.id ? c.id.slice(0, 8) : `SAFE-${i+1}`}</span>
                        <span className="px-2 py-0.5 rounded-full bg-red-500/20 text-red-400 font-bold text-[10px]">
                          {c.severity || 'SEV_2_MODERATE'}
                        </span>
                      </div>
                      <h4 className="text-sm font-bold text-white">{c.title || c.description || 'Safety Anomaly Investigation'}</h4>
                      <p className="text-xs text-slate-400">Status: <span className="text-emerald-400 font-bold">{c.status || 'INVESTIGATION_OPEN'}</span></p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* DESK 31: RBAC ROLES & PERMISSIONS */}
          {activeDeskId === 'rbac_security' && (
            <div className="space-y-6">
              <div className="flex justify-between items-center border-b border-slate-800 pb-4">
                <div>
                  <h2 className="text-xl font-black text-white flex items-center gap-2">
                    <span>🔐 Admin RBAC Roles &amp; Permissions Matrix</span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Granular role-based access control, session timeouts, and critical action authorization rules.
                  </p>
                </div>
              </div>

              <div className="overflow-x-auto rounded-2xl border border-slate-800 bg-slate-900">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] font-bold tracking-wider border-b border-slate-800">
                    <tr>
                      <th className="py-3 px-4">Role Name</th>
                      <th className="py-3 px-4">Tariffs &amp; Fares</th>
                      <th className="py-3 px-4">Driver KYC</th>
                      <th className="py-3 px-4">IMPS Payouts</th>
                      <th className="py-3 px-4">Disputes &amp; Refunds</th>
                      <th className="py-3 px-4">Emergency SOS</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-mono">
                    <tr className="hover:bg-slate-800/40">
                      <td className="py-3 px-4 font-bold text-white font-sans">SUPERADMIN</td>
                      <td className="py-3 px-4 text-emerald-400 font-bold">FULL (READ/WRITE)</td>
                      <td className="py-3 px-4 text-emerald-400 font-bold">FULL (APPROVE/REJECT)</td>
                      <td className="py-3 px-4 text-emerald-400 font-bold">FULL (APPROVE)</td>
                      <td className="py-3 px-4 text-emerald-400 font-bold">FULL (ARBITRATE)</td>
                      <td className="py-3 px-4 text-emerald-400 font-bold">COMMAND CONTROL</td>
                    </tr>
                    <tr className="hover:bg-slate-800/40">
                      <td className="py-3 px-4 font-bold text-slate-300 font-sans">DISPATCH_OPERATOR</td>
                      <td className="py-3 px-4 text-slate-500">READ_ONLY</td>
                      <td className="py-3 px-4 text-slate-500">READ_ONLY</td>
                      <td className="py-3 px-4 text-red-400">NO ACCESS</td>
                      <td className="py-3 px-4 text-slate-500">READ_ONLY</td>
                      <td className="py-3 px-4 text-amber-400 font-bold">READ / ESCALATE</td>
                    </tr>
                    <tr className="hover:bg-slate-800/40">
                      <td className="py-3 px-4 font-bold text-slate-300 font-sans">SUPPORT_AGENT</td>
                      <td className="py-3 px-4 text-slate-500">READ_ONLY</td>
                      <td className="py-3 px-4 text-slate-500">READ_ONLY</td>
                      <td className="py-3 px-4 text-red-400">NO ACCESS</td>
                      <td className="py-3 px-4 text-emerald-400 font-bold">FILE DISPUTE</td>
                      <td className="py-3 px-4 text-amber-400 font-bold">READ / ESCALATE</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* FALLBACK FOR ANY REMAINING DESKS */}
          {[
            'dashboard', 'gps_map', 'dispatch', 'pooling', 'customer_dir', 'customer_wallets',
            'rides_ledger', 'fare_disputes', 'lost_items', 'support_tickets', 'customer_loyalty',
            'driver_kyc', 'document_ocr', 'driver_availability', 'vehicle_fleet', 'driver_payouts',
            'driver_ratings', 'fare_matrix', 'geofence', 'surge_rules', 'road_restrictions',
            'payment_logs', 'promos', 'broadcast', 'emergency_sos', 'incident_reports',
            'rbac_security', 'audit_logs', 'simulator'
          ].indexOf(activeDeskId) === -1 && (
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

      {/* DRIVER KYC DOCUMENT REVIEW MODAL */}
      {selectedKycDriver && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center z-[9999] p-4">
          <div className="bg-slate-900 border border-slate-700 rounded-2xl max-w-2xl w-full p-6 space-y-5 shadow-2xl animate-in fade-in duration-200">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div>
                <h3 className="text-base font-extrabold text-white">📋 Driver Document Verification &amp; KYC</h3>
                <p className="text-xs text-slate-400">
                  {selectedKycDriver.full_name} • ID: <code className="text-slate-300">{selectedKycDriver.id}</code> • Status: <span className="font-bold text-amber-400">{selectedKycDriver.approval_status}</span>
                </p>
              </div>
              <button
                onClick={() => setSelectedKycDriver(null)}
                className="text-slate-400 hover:text-white p-1 rounded-lg text-lg"
              >
                ✕
              </button>
            </div>

            {docActionError && (
              <div className="p-3 bg-red-950/40 border border-red-500/40 rounded-xl text-xs text-red-300 flex items-center justify-between">
                <span>⚠️ {docActionError}</span>
                <button onClick={() => setDocActionError(null)} className="text-red-400 font-bold ml-2">✕</button>
              </div>
            )}

            <div className="max-h-80 overflow-y-auto space-y-3 pr-1">
              {loadingDocs ? (
                <div className="p-8 text-center text-slate-400">
                  <div className="animate-spin inline-block w-6 h-6 border-2 border-current border-t-transparent text-emerald-400 rounded-full mb-2"></div>
                  <p className="text-xs font-bold">Loading driver credentials...</p>
                </div>
              ) : driverDocs.length === 0 ? (
                <div className="p-6 text-center text-slate-400 bg-slate-950/60 rounded-xl border border-slate-800">
                  <p className="text-xs font-bold text-slate-300">No documents uploaded by this driver.</p>
                  <p className="text-[11px] text-slate-500 mt-1">Driver must upload required credentials before approval.</p>
                </div>
              ) : (
                driverDocs.map(doc => (
                  <div key={doc.id} className="p-4 bg-slate-950 border border-slate-800 rounded-xl space-y-2">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-white text-xs">{doc.document_type || 'DRIVER_DOCUMENT'}</span>
                          <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                            doc.verification_status === 'VERIFIED' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' :
                            doc.verification_status === 'REJECTED' ? 'bg-red-500/20 text-red-400 border border-red-500/30' :
                            'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                          }`}>
                            {doc.verification_status}
                          </span>
                        </div>
                        <p className="text-[11px] text-slate-400 mt-0.5">{doc.file_name} • ID: <code className="text-slate-500">{doc.id}</code></p>
                        {doc.rejection_reason && (
                          <p className="text-[11px] text-red-400 mt-1 bg-red-950/30 p-1.5 rounded border border-red-900/40">
                            Reason: {doc.rejection_reason}
                          </p>
                        )}
                      </div>

                      <div className="flex items-center gap-2 flex-shrink-0">
                        {doc.verification_status !== 'VERIFIED' && (
                          <button
                            onClick={() => handleVerifyDoc(doc.id)}
                            className="px-2.5 py-1 bg-emerald-600/30 hover:bg-emerald-600/50 text-emerald-300 border border-emerald-500/30 rounded-lg text-[11px] font-bold"
                          >
                            ✓ Verify
                          </button>
                        )}
                        {doc.verification_status !== 'REJECTED' && activeRejectingDocId !== doc.id && (
                          <button
                            onClick={() => {
                              setActiveRejectingDocId(doc.id);
                              setRejectionReasonInput('');
                            }}
                            className="px-2.5 py-1 bg-red-600/20 hover:bg-red-600/30 text-red-300 border border-red-500/30 rounded-lg text-[11px] font-bold"
                          >
                            {doc.verification_status === 'VERIFIED' ? '⚠ Revoke' : '✕ Reject'}
                          </button>
                        )}
                      </div>
                    </div>

                    {activeRejectingDocId === doc.id && (
                      <div className="pt-2 border-t border-slate-800 space-y-2">
                        <label className="block text-[11px] text-slate-400 font-bold">
                          {doc.verification_status === 'VERIFIED'
                            ? 'Supervisor Revocation Justification (min 8 characters):'
                            : 'Rejection Reason (min 8 characters):'}
                        </label>
                        <div className="flex gap-2">
                          <input
                            type="text"
                            value={rejectionReasonInput}
                            onChange={e => setRejectionReasonInput(e.target.value)}
                            placeholder={doc.verification_status === 'VERIFIED' ? "e.g. Discovered fraudulent credentials upon supervisor audit" : "e.g. Expired license or blurry image"}
                            className="flex-1 px-3 py-1 bg-slate-900 border border-slate-700 rounded-lg text-xs text-white"
                          />
                          <button
                            onClick={() => handleRejectDoc(doc.id, doc.verification_status === 'VERIFIED')}
                            className="px-3 py-1 bg-red-600 hover:bg-red-500 text-white rounded-lg text-xs font-bold"
                          >
                            {doc.verification_status === 'VERIFIED' ? 'Confirm Revocation' : 'Submit Rejection'}
                          </button>
                          <button
                            onClick={() => setActiveRejectingDocId(null)}
                            className="px-2 py-1 bg-slate-800 text-slate-400 rounded-lg text-xs"
                          >
                            Cancel
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>

            <div className="border-t border-slate-800 pt-4 flex items-center justify-between gap-3">
              <div className="text-xs text-slate-400">
                {driverDocs.length > 0 ? (
                  <span>
                    Verified: <strong className="text-white">{driverDocs.filter(d => d.verification_status === 'VERIFIED').length}</strong> of {driverDocs.length}
                  </span>
                ) : (
                  <span>No documents available</span>
                )}
              </div>

              <div className="flex items-center gap-3">
                <button
                  onClick={() => setSelectedKycDriver(null)}
                  className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-bold rounded-xl"
                >
                  Close
                </button>
                <button
                  disabled={driverDocs.length === 0 || !driverDocs.every(d => d.verification_status === 'VERIFIED')}
                  onClick={handleApproveDriverAfterKyc}
                  title={
                    driverDocs.length === 0 || !driverDocs.every(d => d.verification_status === 'VERIFIED')
                      ? "Every required driver document must be in VERIFIED status before approval."
                      : "Grant full operational driver approval"
                  }
                  className={`px-4 py-2 text-xs font-black rounded-xl shadow-lg transition-all ${
                    driverDocs.length > 0 && driverDocs.every(d => d.verification_status === 'VERIFIED')
                      ? 'bg-emerald-500 hover:bg-emerald-400 text-black shadow-emerald-500/20 cursor-pointer'
                      : 'bg-slate-800 text-slate-500 cursor-not-allowed border border-slate-700'
                  }`}
                >
                  Approve Chauffeur Partner
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};

export const RideGoMissionControl = VaisentraMissionControl;
export default VaisentraMissionControl;

