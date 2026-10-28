import React, { useEffect, useState } from 'react';
import {
  Building2, Users, Calendar, Package, Globe, Languages,
  CheckCircle, RefreshCw, ThumbsUp, BarChart, Layers, Plus,
  FileText, Shield, Tag, X
} from 'lucide-react';
import { requestApi } from '../services/api';

export const AdminEnterpriseGrowthConsole: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'corporate' | 'partners' | 'products' | 'multicity' | 'growth' | 'localization'>('corporate');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Tab 1: Corporate & Invoicing
  const [corporateAccounts, setCorporateAccounts] = useState<any[]>([]);
  const [selectedCorpId, setSelectedCorpId] = useState<string>('');
  const [corporatePolicies, setCorporatePolicies] = useState<any[]>([]);
  const [corporateInvoices, setCorporateInvoices] = useState<any[]>([]);
  const [costCenters, setCostCenters] = useState<any[]>([]);
  const [approvals, setApprovals] = useState<any[]>([]);
  const [showAddCorpModal, setShowAddCorpModal] = useState(false);
  const [newCorp, setNewCorp] = useState({ company_name: '', tax_id: '', billing_email: '', credit_limit_rupees: 500000 });

  // Tab 2: Partners & Concierge
  const [partnerAccounts, setPartnerAccounts] = useState<any[]>([]);
  const [conciergeDashboard, setConciergeDashboard] = useState<any>(null);
  const [partnerAttributions, setPartnerAttributions] = useState<any[]>([]);
  const [events, setEvents] = useState<any[]>([]);
  const [showAddPartnerModal, setShowAddPartnerModal] = useState(false);
  const [newPartner, setNewPartner] = useState({ partner_name: '', partner_type: 'HOTEL', commission_rate_pct: 12.5, referral_code: '' });
  const [showAddEventModal, setShowAddEventModal] = useState(false);
  const [newEvent, setNewEvent] = useState({ event_name: '', venue_address: '', allocated_vehicles_count: 15, total_guests: 150 });

  // Tab 3: Subscriptions & Products
  const [subscriptionPlans, setSubscriptionPlans] = useState<any[]>([]);
  const [bundles, setBundles] = useState<any[]>([]);
  const [loyaltyItems, setLoyaltyItems] = useState<any[]>([]);
  const [airportPackages, setAirportPackages] = useState<any[]>([]);
  const [intercityProducts, setIntercityProducts] = useState<any[]>([]);
  const [hourlyBookings, setHourlyBookings] = useState<any[]>([]);
  const [addons, setAddons] = useState<any[]>([]);
  const [showAddPlanModal, setShowAddPlanModal] = useState(false);
  const [newPlan, setNewPlan] = useState({ plan_code: '', name: '', monthly_price_rupees: 45000, included_rides_count: 10, category_code: 'SEDAN' });
  const [showAddAddonModal, setShowAddAddonModal] = useState(false);
  const [newAddon, setNewAddon] = useState({ addon_code: '', title: '', price_rupees: 2500, description: '' });

  // Tab 4: Multi-City & Tenant
  const [tenantConfigs, setTenantConfigs] = useState<any[]>([]);
  const [cityConfigs, setCityConfigs] = useState<any[]>([]);
  const [editingCityTax, setEditingCityTax] = useState<{ [cityCode: string]: { tax: number; minFare: number } }>({});

  // Tab 5: Growth & Feedback
  const [abExperiments, setAbExperiments] = useState<any[]>([]);
  const [featureRequests, setFeatureRequests] = useState<any[]>([]);
  const [feedbackSummary, setFeedbackSummary] = useState<any>(null);
  const [feedbackList, setFeedbackList] = useState<any[]>([]);
  const [showAddFeatureModal, setShowAddFeatureModal] = useState(false);
  const [newFeature, setNewFeature] = useState({ title: '', description: '', category: 'BESPOKE_AMENITIES' });

  // Tab 6: Localization Catalog
  const [localizationEntries, setLocalizationEntries] = useState<any[]>([]);
  const [selectedLocaleFilter, setSelectedLocaleFilter] = useState<string>('en');

  const loadData = async () => {
    setLoading(true);
    try {
      if (activeTab === 'corporate') {
        const [accs, apprs] = await Promise.all([
          requestApi<any[]>('/enterprise/corporate/accounts'),
          requestApi<any[]>('/enterprise/corporate/approvals')
        ]);
        setCorporateAccounts(accs);
        setApprovals(apprs);

        const currentCorpId = selectedCorpId || (accs.length > 0 ? accs[0].id : '');
        if (currentCorpId) {
          setSelectedCorpId(currentCorpId);
          const [pols, invs, centers] = await Promise.all([
            requestApi<any[]>(`/enterprise/corporate/policies/${currentCorpId}`),
            requestApi<any[]>(`/enterprise/corporate/invoices/${currentCorpId}`),
            requestApi<any[]>(`/enterprise/corporate/cost-centers/${currentCorpId}`)
          ]);
          setCorporatePolicies(pols);
          setCorporateInvoices(invs);
          setCostCenters(centers);
        }
      } else if (activeTab === 'partners') {
        const [parts, dash, attrs, evts] = await Promise.all([
          requestApi<any[]>('/enterprise/partners/accounts'),
          requestApi<any>('/enterprise/partners/concierge/dashboard'),
          requestApi<any[]>('/enterprise/partners/attributions'),
          requestApi<any[]>('/enterprise/partners/events')
        ]);
        setPartnerAccounts(parts);
        setConciergeDashboard(dash);
        setPartnerAttributions(attrs);
        setEvents(evts);
      } else if (activeTab === 'products') {
        const [plans, bndls, loy, air, inter, hourly, add] = await Promise.all([
          requestApi<any[]>('/enterprise/subscriptions/plans'),
          requestApi<any[]>('/enterprise/subscriptions/bundles'),
          requestApi<any[]>('/enterprise/loyalty-catalog'),
          requestApi<any[]>('/enterprise/products/airport-transfers'),
          requestApi<any[]>('/enterprise/products/intercity'),
          requestApi<any[]>('/enterprise/products/hourly-bookings'),
          requestApi<any[]>('/enterprise/products/add-ons')
        ]);
        setSubscriptionPlans(plans);
        setBundles(bndls);
        setLoyaltyItems(loy);
        setAirportPackages(air);
        setIntercityProducts(inter);
        setHourlyBookings(hourly);
        setAddons(add);
      } else if (activeTab === 'multicity') {
        const [tenants, cities] = await Promise.all([
          requestApi<any[]>('/enterprise/tenant/configs'),
          requestApi<any[]>('/enterprise/cities/configs')
        ]);
        setTenantConfigs(tenants);
        setCityConfigs(cities);
        const taxMap: any = {};
        cities.forEach(c => {
          taxMap[c.city_code] = { tax: c.tax_rate_percentage, minFare: c.min_fare_rupees };
        });
        setEditingCityTax(taxMap);
      } else if (activeTab === 'growth') {
        const [exps, reqs, summary, feedList] = await Promise.all([
          requestApi<any[]>('/enterprise/growth/ab-experiments'),
          requestApi<any[]>('/enterprise/growth/feature-requests'),
          requestApi<any>('/enterprise/growth/feedback-analytics/summary'),
          requestApi<any[]>('/enterprise/growth/feedback-analytics')
        ]);
        setAbExperiments(exps);
        setFeatureRequests(reqs);
        setFeedbackSummary(summary);
        setFeedbackList(feedList);
      } else if (activeTab === 'localization') {
        const entries = await requestApi<any[]>('/localization/catalog');
        setLocalizationEntries(entries);
      }
    } catch (e: any) {
      setMessage({ text: `Failed to load data: ${e.message}`, type: 'error' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, [activeTab, selectedCorpId]);

  // Action handlers
  const handleCreateCorporate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCorp.company_name || !newCorp.tax_id || !newCorp.billing_email) {
      setMessage({ text: 'Please complete all required fields.', type: 'error' });
      return;
    }
    try {
      await requestApi('/enterprise/corporate/accounts', {
        method: 'POST',
        body: JSON.stringify({
          company_name: newCorp.company_name,
          tax_id: newCorp.tax_id,
          billing_email: newCorp.billing_email,
          credit_limit_cents: newCorp.credit_limit_rupees * 100
        })
      });
      setMessage({ text: `Onboarded corporate account "${newCorp.company_name}"`, type: 'success' });
      setShowAddCorpModal(false);
      setNewCorp({ company_name: '', tax_id: '', billing_email: '', credit_limit_rupees: 500000 });
      void loadData();
    } catch (err: any) {
      setMessage({ text: `Error: ${err.message}`, type: 'error' });
    }
  };

  const handleActionApproval = async (approvalId: string, action: 'APPROVE' | 'REJECT') => {
    try {
      await requestApi(`/enterprise/corporate/approvals/${approvalId}/action`, {
        method: 'POST',
        body: JSON.stringify({ action, approver_id: 'admin_exec' })
      });
      setMessage({ text: `Approval request ${action.toLowerCase()}d successfully.`, type: 'success' });
      void loadData();
    } catch (err: any) {
      setMessage({ text: `Action error: ${err.message}`, type: 'error' });
    }
  };

  const handleGenerateInvoice = async () => {
    if (!selectedCorpId) return;
    try {
      const res = await requestApi<any>('/enterprise/corporate/invoices/generate', {
        method: 'POST',
        body: JSON.stringify({ corporate_id: selectedCorpId, billing_period: '2026-M11' })
      });
      setMessage({ text: `Generated ${res.invoice_number} (₹${res.total_rupees.toLocaleString()})`, type: 'success' });
      void loadData();
    } catch (err: any) {
      setMessage({ text: `Invoice generation error: ${err.message}`, type: 'error' });
    }
  };

  const handleMarkInvoicePaid = async (invId: string) => {
    try {
      await requestApi(`/enterprise/corporate/invoices/${invId}/pay`, { method: 'PATCH' });
      setMessage({ text: 'Invoice marked as PAID', type: 'success' });
      void loadData();
    } catch (err: any) {
      setMessage({ text: err.message, type: 'error' });
    }
  };

  const handleCreatePartner = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await requestApi('/enterprise/partners/accounts', {
        method: 'POST',
        body: JSON.stringify(newPartner)
      });
      setMessage({ text: `Onboarded partner "${newPartner.partner_name}"`, type: 'success' });
      setShowAddPartnerModal(false);
      void loadData();
    } catch (err: any) {
      setMessage({ text: err.message, type: 'error' });
    }
  };

  const handleCreateEvent = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await requestApi('/enterprise/partners/events', {
        method: 'POST',
        body: JSON.stringify(newEvent)
      });
      setMessage({ text: `Event workspace "${newEvent.event_name}" initiated`, type: 'success' });
      setShowAddEventModal(false);
      void loadData();
    } catch (err: any) {
      setMessage({ text: err.message, type: 'error' });
    }
  };

  const handleCreatePlan = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await requestApi('/enterprise/subscriptions/plans', {
        method: 'POST',
        body: JSON.stringify({
          plan_code: newPlan.plan_code,
          name: newPlan.name,
          monthly_price_cents: newPlan.monthly_price_rupees * 100,
          included_rides_count: newPlan.included_rides_count,
          category_code: newPlan.category_code
        })
      });
      setMessage({ text: `Created subscription plan "${newPlan.name}"`, type: 'success' });
      setShowAddPlanModal(false);
      void loadData();
    } catch (err: any) {
      setMessage({ text: err.message, type: 'error' });
    }
  };

  const handleCreateAddon = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await requestApi('/enterprise/products/add-ons', {
        method: 'POST',
        body: JSON.stringify({
          addon_code: newAddon.addon_code,
          title: newAddon.title,
          price_cents: newAddon.price_rupees * 100,
          description: newAddon.description
        })
      });
      setMessage({ text: `Created chauffeur add-on "${newAddon.title}"`, type: 'success' });
      setShowAddAddonModal(false);
      void loadData();
    } catch (err: any) {
      setMessage({ text: err.message, type: 'error' });
    }
  };

  const handleCreateFeature = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await requestApi('/enterprise/growth/feature-requests', {
        method: 'POST',
        body: JSON.stringify(newFeature)
      });
      setMessage({ text: `Submitted feature request "${newFeature.title}"`, type: 'success' });
      setShowAddFeatureModal(false);
      void loadData();
    } catch (err: any) {
      setMessage({ text: err.message, type: 'error' });
    }
  };

  const handleUpdateCityTaxFare = async (cityCode: string) => {
    const edit = editingCityTax[cityCode];
    if (!edit) return;
    try {
      await requestApi(`/enterprise/cities/${cityCode}/tax-fare`, {
        method: 'PATCH',
        body: JSON.stringify({
          tax_rate_percentage: edit.tax,
          min_fare_cents: edit.minFare * 100
        })
      });
      setMessage({ text: `Updated tax and fare settings for ${cityCode}`, type: 'success' });
      void loadData();
    } catch (err: any) {
      setMessage({ text: err.message, type: 'error' });
    }
  };

  return (
    <div style={{ padding: '24px 32px', color: '#F8F9FA', background: '#0A0A0E', minHeight: '100vh', fontFamily: 'var(--font-sans)' }}>
      {/* Top Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24, borderBottom: '1px solid #2A2A3A', paddingBottom: 18 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Building2 size={28} color="#D4AF37" />
            <h1 style={{ fontSize: '1.6rem', fontWeight: 700, color: '#D4AF37', margin: 0, letterSpacing: '0.5px', fontFamily: 'var(--font-display)' }}>
              ENTERPRISE, PARTNERSHIPS & GROWTH
            </h1>
          </div>
          <p style={{ fontSize: '0.82rem', color: '#9CA3AF', margin: '4px 0 0 0' }}>
            Part 20 of 20: Corporate Accounts, Concierge Portals, Subscriptions, Multi-City, A/B Testing & Feedback Analytics
          </p>
        </div>

        <button
          onClick={loadData}
          style={{
            background: '#14141C',
            border: '1px solid #D4AF37',
            color: '#D4AF37',
            padding: '8px 18px',
            borderRadius: 6,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            fontWeight: 600,
            fontSize: '0.85rem'
          }}
        >
          <RefreshCw size={15} className={loading ? 'spin' : ''} />
          {loading ? 'Refreshing...' : 'Refresh Console'}
        </button>
      </div>

      {message && (
        <div style={{
          background: message.type === 'success' ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
          borderLeft: `4px solid ${message.type === 'success' ? '#10B981' : '#EF4444'}`,
          color: message.type === 'success' ? '#34D399' : '#FCA5A5',
          padding: '12px 18px',
          borderRadius: 6,
          marginBottom: 20,
          fontSize: '0.88rem',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <span>{message.text}</span>
          <X size={16} style={{ cursor: 'pointer' }} onClick={() => setMessage(null)} />
        </div>
      )}

      {/* Luxury Tabs Bar */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 24, borderBottom: '1px solid #2A2A3A', paddingBottom: 10, overflowX: 'auto' }}>
        {[
          { id: 'corporate', label: 'Corporate & Invoicing (476-480, 489)', icon: Building2 },
          { id: 'partners', label: 'Partners & Concierge (481-485)', icon: Users },
          { id: 'products', label: 'Subscriptions & Products (486-488, 490-494)', icon: Package },
          { id: 'multicity', label: 'Multi-City & Tenant (495-497)', icon: Globe },
          { id: 'growth', label: 'A/B & Product Feedback (498-500)', icon: BarChart },
          { id: 'localization', label: 'Dynamic Localization Catalog', icon: Languages }
        ].map(tab => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              style={{
                background: isActive ? '#D4AF37' : '#14141C',
                color: isActive ? '#0A0A0E' : '#D1D5DB',
                border: '1px solid',
                borderColor: isActive ? '#D4AF37' : '#2A2A3A',
                padding: '10px 16px',
                borderRadius: 8,
                fontWeight: isActive ? 700 : 500,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                fontSize: '0.82rem',
                whiteSpace: 'nowrap',
                transition: 'all 0.2s ease'
              }}
            >
              <Icon size={16} />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* =====================================================================
          TAB 1: CORPORATE & INVOICING (Features 476, 477, 478, 479, 480, 489)
      ===================================================================== */}
      {activeTab === 'corporate' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* Top Row: Corporate Accounts & Action Toolbar */}
          <div style={{ background: '#14141C', borderRadius: 8, padding: 20, border: '1px solid #2A2A3A' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <div>
                <h3 style={{ margin: 0, color: '#D4AF37', fontSize: '1.1rem', display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Building2 size={18} />
                  Corporate Customer Accounts (Feature 476)
                </h3>
                <span style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Centralized executive B2B accounts, credit limits and balances</span>
              </div>
              <button
                onClick={() => setShowAddCorpModal(true)}
                style={{
                  background: '#D4AF37',
                  color: '#0A0A0E',
                  border: 'none',
                  padding: '7px 14px',
                  borderRadius: 6,
                  fontWeight: 700,
                  fontSize: '0.8rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6
                }}
              >
                <Plus size={15} /> Onboard Corporate Account
              </button>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 14 }}>
              {corporateAccounts.map(c => {
                const isSelected = selectedCorpId === c.id;
                return (
                  <div
                    key={c.id}
                    onClick={() => setSelectedCorpId(c.id)}
                    style={{
                      background: isSelected ? '#1A1A24' : '#0E0E14',
                      border: `1.5px solid ${isSelected ? '#D4AF37' : '#2A2A3A'}`,
                      borderRadius: 8,
                      padding: 16,
                      cursor: 'pointer',
                      transition: 'border-color 0.2s'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <div style={{ fontWeight: 700, fontSize: '1rem', color: '#F8F9FA' }}>{c.company_name}</div>
                      <span style={{
                        padding: '2px 8px',
                        borderRadius: 4,
                        fontSize: '0.7rem',
                        fontWeight: 700,
                        background: c.status === 'ACTIVE' ? 'rgba(16, 185, 129, 0.2)' : 'rgba(239, 68, 68, 0.2)',
                        color: c.status === 'ACTIVE' ? '#10B981' : '#EF4444'
                      }}>
                        {c.status}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.76rem', color: '#9CA3AF', marginTop: 6 }}>
                      Tax ID: <span style={{ color: '#E2E8F0' }}>{c.tax_id}</span> • Email: <span style={{ color: '#E2E8F0' }}>{c.billing_email}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 12, paddingTop: 10, borderTop: '1px solid #222230', fontSize: '0.82rem' }}>
                      <div>Credit Cap: <strong style={{ color: '#D4AF37' }}>₹{c.credit_limit_rupees?.toLocaleString()}</strong></div>
                      <div>Balance Due: <strong style={{ color: c.balance_due_rupees > 0 ? '#F59E0B' : '#10B981' }}>₹{c.balance_due_rupees?.toLocaleString()}</strong></div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Second Row: Policies, Invoicing & Cost Center Tagging */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 20 }}>
            {/* Travel Policies */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 18, border: '1px solid #2A2A3A' }}>
              <h4 style={{ margin: '0 0 12px 0', color: '#D4AF37', fontSize: '0.95rem', display: 'flex', alignItems: 'center', gap: 6 }}>
                <Shield size={16} /> Travel Policy Rules (Feature 478)
              </h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {corporatePolicies.map(p => (
                  <div key={p.id} style={{ background: '#0E0E14', padding: 12, borderRadius: 6, border: '1px solid #222230' }}>
                    <div style={{ fontWeight: 600, fontSize: '0.85rem', color: '#F8F9FA' }}>{p.policy_name}</div>
                    <div style={{ fontSize: '0.74rem', color: '#9CA3AF', marginTop: 4 }}>
                      Max Fare / Ride: <strong style={{ color: '#D4AF37' }}>₹{p.max_fare_per_ride_rupees?.toLocaleString()}</strong>
                    </div>
                    <div style={{ fontSize: '0.74rem', color: '#9CA3AF', marginTop: 2 }}>
                      Curfew Hours: {p.curfew_start_time ? `${p.curfew_start_time} - ${p.curfew_end_time}` : '24/7 Unrestricted'}
                    </div>
                    <div style={{ marginTop: 6, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                      {p.allowed_categories?.map((cat: string) => (
                        <span key={cat} style={{ background: '#1A1A24', border: '1px solid #333344', padding: '2px 6px', borderRadius: 3, fontSize: '0.68rem', color: '#E2E8F0' }}>
                          {cat}
                        </span>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Invoices */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 18, border: '1px solid #2A2A3A' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <h4 style={{ margin: 0, color: '#D4AF37', fontSize: '0.95rem', display: 'flex', alignItems: 'center', gap: 6 }}>
                  <FileText size={16} /> Central Invoicing (Feature 479)
                </h4>
                <button
                  onClick={handleGenerateInvoice}
                  style={{ background: '#1A1A24', border: '1px solid #D4AF37', color: '#D4AF37', padding: '4px 8px', borderRadius: 4, fontSize: '0.72rem', cursor: 'pointer', fontWeight: 600 }}
                >
                  Generate Invoice
                </button>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {corporateInvoices.map(inv => (
                  <div key={inv.id} style={{ background: '#0E0E14', padding: 12, borderRadius: 6, border: '1px solid #222230', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: '0.84rem', color: '#60A5FA' }}>{inv.invoice_number}</div>
                      <div style={{ fontSize: '0.74rem', color: '#9CA3AF', marginTop: 2 }}>
                        Period: {inv.billing_period} • ₹{inv.total_amount_rupees?.toLocaleString()}
                      </div>
                    </div>
                    {inv.status === 'PAID' ? (
                      <span style={{ background: 'rgba(16, 185, 129, 0.2)', color: '#10B981', padding: '3px 8px', borderRadius: 4, fontSize: '0.7rem', fontWeight: 700 }}>PAID</span>
                    ) : (
                      <button
                        onClick={() => handleMarkInvoicePaid(inv.id)}
                        style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '4px 8px', borderRadius: 4, fontSize: '0.72rem', fontWeight: 700, cursor: 'pointer' }}
                      >
                        Mark Paid
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>

            {/* Cost Center Tagging */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 18, border: '1px solid #2A2A3A' }}>
              <h4 style={{ margin: '0 0 12px 0', color: '#D4AF37', fontSize: '0.95rem', display: 'flex', alignItems: 'center', gap: 6 }}>
                <Tag size={16} /> Cost-Center Tagging (Feature 480)
              </h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {costCenters.map(cc => (
                  <div key={cc.cost_center} style={{ background: '#0E0E14', padding: 10, borderRadius: 6, border: '1px solid #222230' }}>
                    <div style={{ fontWeight: 600, fontSize: '0.82rem', color: '#E2E8F0' }}>{cc.cost_center}</div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.72rem', color: '#9CA3AF', marginTop: 4 }}>
                      <span>Employees: <strong style={{ color: '#F8F9FA' }}>{cc.employee_count}</strong></span>
                      <span>Cap: <strong style={{ color: '#D4AF37' }}>₹{cc.allocated_cap_rupees?.toLocaleString()}</strong></span>
                      <span>Active: <strong style={{ color: '#34D399' }}>{cc.active_rides_count} rides</strong></span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Third Row: Business Travel Approvals Workflow (Feature 489) */}
          <div style={{ background: '#14141C', borderRadius: 8, padding: 20, border: '1px solid #2A2A3A' }}>
            <h3 style={{ margin: '0 0 14px 0', color: '#D4AF37', fontSize: '1.05rem', display: 'flex', alignItems: 'center', gap: 8 }}>
              <CheckCircle size={18} />
              Business Travel Approval Workflow (Feature 489)
            </h3>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.84rem' }}>
                <thead>
                  <tr style={{ background: '#0E0E14', color: '#D4AF37', textAlign: 'left', borderBottom: '1px solid #2A2A3A' }}>
                    <th style={{ padding: '10px 12px' }}>Ride ID</th>
                    <th style={{ padding: '10px 12px' }}>Cost Center</th>
                    <th style={{ padding: '10px 12px' }}>Project Code</th>
                    <th style={{ padding: '10px 12px' }}>Executive Justification</th>
                    <th style={{ padding: '10px 12px' }}>Status</th>
                    <th style={{ padding: '10px 12px', textAlign: 'right' }}>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {approvals.map(a => (
                    <tr key={a.id} style={{ borderBottom: '1px solid #222230' }}>
                      <td style={{ padding: '10px 12px', fontFamily: 'var(--font-mono)', color: '#60A5FA' }}>{a.ride_id}</td>
                      <td style={{ padding: '10px 12px', fontWeight: 600 }}>{a.cost_center}</td>
                      <td style={{ padding: '10px 12px', color: '#9CA3AF' }}>{a.project_code || 'N/A'}</td>
                      <td style={{ padding: '10px 12px', color: '#E2E8F0', maxWidth: 300 }}>{a.justification}</td>
                      <td style={{ padding: '10px 12px' }}>
                        <span style={{
                          padding: '3px 8px',
                          borderRadius: 4,
                          fontSize: '0.72rem',
                          fontWeight: 700,
                          background: a.status === 'APPROVED' ? 'rgba(16, 185, 129, 0.2)' : (a.status === 'REJECTED' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(245, 158, 11, 0.2)'),
                          color: a.status === 'APPROVED' ? '#10B981' : (a.status === 'REJECTED' ? '#EF4444' : '#F59E0B')
                        }}>
                          {a.status}
                        </span>
                      </td>
                      <td style={{ padding: '10px 12px', textAlign: 'right' }}>
                        {a.status === 'PENDING' ? (
                          <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                            <button
                              onClick={() => handleActionApproval(a.id, 'APPROVE')}
                              style={{ background: '#10B981', color: '#0A0A0E', border: 'none', padding: '4px 8px', borderRadius: 4, fontWeight: 700, cursor: 'pointer', fontSize: '0.75rem' }}
                            >
                              Approve
                            </button>
                            <button
                              onClick={() => handleActionApproval(a.id, 'REJECT')}
                              style={{ background: '#EF4444', color: '#FFFFFF', border: 'none', padding: '4px 8px', borderRadius: 4, fontWeight: 700, cursor: 'pointer', fontSize: '0.75rem' }}
                            >
                              Reject
                            </button>
                          </div>
                        ) : (
                          <span style={{ fontSize: '0.75rem', color: '#9CA3AF' }}>Concluded</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* =====================================================================
          TAB 2: PARTNERS & CONCIERGE (Features 481, 482, 483, 484, 485)
      ===================================================================== */}
      {activeTab === 'partners' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* Concierge Dashboard Metric Cards (Feature 481) */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16 }}>
            <div style={{ background: '#14141C', borderRadius: 8, padding: 18, border: '1px solid #2A2A3A' }}>
              <div style={{ fontSize: '0.75rem', color: '#9CA3AF', textTransform: 'uppercase' }}>Active Hotel & Venue Partners</div>
              <div style={{ fontSize: '1.6rem', fontWeight: 700, color: '#D4AF37', marginTop: 4 }}>
                {conciergeDashboard?.active_partners_count || partnerAccounts.length}
              </div>
              <div style={{ fontSize: '0.72rem', color: '#10B981', marginTop: 4 }}>Taj, Jio, Four Seasons, Oberoi</div>
            </div>
            <div style={{ background: '#14141C', borderRadius: 8, padding: 18, border: '1px solid #2A2A3A' }}>
              <div style={{ fontSize: '0.75rem', color: '#9CA3AF', textTransform: 'uppercase' }}>Monthly Concierge Bookings</div>
              <div style={{ fontSize: '1.6rem', fontWeight: 700, color: '#60A5FA', marginTop: 4 }}>
                {conciergeDashboard?.concierge_bookings_count || 14}
              </div>
              <div style={{ fontSize: '0.72rem', color: '#9CA3AF', marginTop: 4 }}>Live Partner API & Direct Desk</div>
            </div>
            <div style={{ background: '#14141C', borderRadius: 8, padding: 18, border: '1px solid #2A2A3A' }}>
              <div style={{ fontSize: '0.75rem', color: '#9CA3AF', textTransform: 'uppercase' }}>Attributed Partner Commission</div>
              <div style={{ fontSize: '1.6rem', fontWeight: 700, color: '#34D399', marginTop: 4 }}>
                ₹{(conciergeDashboard?.total_commission_rupees || 18500).toLocaleString()}
              </div>
              <div style={{ fontSize: '0.72rem', color: '#9CA3AF', marginTop: 4 }}>Calculated by Tier Rates</div>
            </div>
            <div style={{ background: '#14141C', borderRadius: 8, padding: 18, border: '1px solid #2A2A3A' }}>
              <div style={{ fontSize: '0.75rem', color: '#9CA3AF', textTransform: 'uppercase' }}>VIP Guest Satisfaction</div>
              <div style={{ fontSize: '1.6rem', fontWeight: 700, color: '#F59E0B', marginTop: 4 }}>
                ★ {conciergeDashboard?.guest_satisfaction_score || 4.96}
              </div>
              <div style={{ fontSize: '0.72rem', color: '#10B981', marginTop: 4 }}>Verified Hotel Guest Rating</div>
            </div>
          </div>

          {/* Row 2: Hotel & Venue Partners & Event Transport Planning Workspace */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
            {/* Partners List (482, 483, 484) */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 20, border: '1px solid #2A2A3A' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
                <h3 style={{ margin: 0, color: '#D4AF37', fontSize: '1.05rem', display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Users size={18} /> Hotel & Venue Partners (482-484)
                </h3>
                <button
                  onClick={() => setShowAddPartnerModal(true)}
                  style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '6px 12px', borderRadius: 4, fontWeight: 700, fontSize: '0.75rem', cursor: 'pointer' }}
                >
                  <Plus size={14} style={{ display: 'inline', verticalAlign: 'middle' }} /> Onboard Partner
                </button>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {partnerAccounts.map(p => (
                  <div key={p.id} style={{ background: '#0E0E14', padding: 12, borderRadius: 6, border: '1px solid #222230' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ fontWeight: 700, color: '#F8F9FA' }}>{p.partner_name}</div>
                      <span style={{ background: '#1A1A24', border: '1px solid #D4AF37', color: '#D4AF37', padding: '2px 8px', borderRadius: 4, fontSize: '0.7rem', fontWeight: 700 }}>
                        {p.partner_type}
                      </span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.76rem', color: '#9CA3AF', marginTop: 6 }}>
                      <span>Referral Code: <code style={{ color: '#60A5FA' }}>{p.referral_code}</code></span>
                      <span>Commission: <strong style={{ color: '#34D399' }}>{p.commission_rate_pct}%</strong></span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Event Transport Planning Workspace (Feature 485) */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 20, border: '1px solid #2A2A3A' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
                <h3 style={{ margin: 0, color: '#D4AF37', fontSize: '1.05rem', display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Calendar size={18} /> Event Transport Workspaces (Feature 485)
                </h3>
                <button
                  onClick={() => setShowAddEventModal(true)}
                  style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '6px 12px', borderRadius: 4, fontWeight: 700, fontSize: '0.75rem', cursor: 'pointer' }}
                >
                  <Plus size={14} style={{ display: 'inline', verticalAlign: 'middle' }} /> Plan Event Fleet
                </button>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {events.map(e => (
                  <div key={e.id} style={{ background: '#0E0E14', padding: 12, borderRadius: 6, border: '1px solid #222230' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ fontWeight: 700, color: '#A7F3D0' }}>{e.event_name}</div>
                      <span style={{ background: 'rgba(16, 185, 129, 0.2)', color: '#10B981', padding: '2px 8px', borderRadius: 4, fontSize: '0.7rem', fontWeight: 700 }}>
                        {e.status}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.76rem', color: '#9CA3AF', marginTop: 4 }}>
                      Venue: <span style={{ color: '#E2E8F0' }}>{e.venue_address}</span>
                    </div>
                    <div style={{ display: 'flex', gap: 16, marginTop: 8, fontSize: '0.74rem', color: '#9CA3AF' }}>
                      <span>Vehicles Allocated: <strong style={{ color: '#D4AF37' }}>{e.allocated_vehicles_count}</strong></span>
                      <span>Total VIP Guests: <strong style={{ color: '#60A5FA' }}>{e.total_guests}</strong></span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Row 3: Partner Referral Attribution Ledger (Feature 484) */}
          <div style={{ background: '#14141C', borderRadius: 8, padding: 20, border: '1px solid #2A2A3A' }}>
            <h3 style={{ margin: '0 0 14px 0', color: '#D4AF37', fontSize: '1.05rem', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Tag size={18} /> Partner Referral Attribution & Commission Ledger (Feature 484)
            </h3>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.84rem' }}>
                <thead>
                  <tr style={{ background: '#0E0E14', color: '#D4AF37', textAlign: 'left', borderBottom: '1px solid #2A2A3A' }}>
                    <th style={{ padding: '8px 10px' }}>Attribution ID</th>
                    <th style={{ padding: '8px 10px' }}>VIP Guest Name</th>
                    <th style={{ padding: '8px 10px' }}>Guest Phone</th>
                    <th style={{ padding: '8px 10px' }}>Commission Earned</th>
                    <th style={{ padding: '8px 10px' }}>Status</th>
                    <th style={{ padding: '8px 10px' }}>Recorded At</th>
                  </tr>
                </thead>
                <tbody>
                  {partnerAttributions.map(attr => (
                    <tr key={attr.id} style={{ borderBottom: '1px solid #222230' }}>
                      <td style={{ padding: '8px 10px', fontFamily: 'var(--font-mono)', color: '#60A5FA' }}>{attr.id}</td>
                      <td style={{ padding: '8px 10px', fontWeight: 600 }}>{attr.guest_name}</td>
                      <td style={{ padding: '8px 10px', color: '#9CA3AF' }}>{attr.guest_phone}</td>
                      <td style={{ padding: '8px 10px', color: '#34D399', fontWeight: 700 }}>₹{attr.commission_earned_rupees?.toLocaleString()}</td>
                      <td style={{ padding: '8px 10px' }}>
                        <span style={{ background: 'rgba(16, 185, 129, 0.2)', color: '#10B981', padding: '2px 6px', borderRadius: 3, fontSize: '0.7rem', fontWeight: 700 }}>
                          {attr.attribution_status}
                        </span>
                      </td>
                      <td style={{ padding: '8px 10px', color: '#9CA3AF', fontSize: '0.75rem' }}>{attr.created_at?.slice(0, 16).replace('T', ' ')}</td>
                    </tr>
                  ))}
                  {partnerAttributions.length === 0 && (
                    <tr>
                      <td colSpan={6} style={{ padding: 12, textAlign: 'center', color: '#9CA3AF' }}>No partner bookings recorded yet.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* =====================================================================
          TAB 3: SUBSCRIPTIONS & PRODUCTS (486, 487, 488, 490, 491, 492, 493, 494)
      ===================================================================== */}
      {activeTab === 'products' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* Subscriptions & Prepaid Bundles */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16 }}>
            {/* Subscriptions (486, 487) */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 16, border: '1px solid #2A2A3A' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <h4 style={{ margin: 0, color: '#D4AF37', fontSize: '0.92rem' }}>Fleet Subscriptions (486, 487)</h4>
                <button
                  onClick={() => setShowAddPlanModal(true)}
                  style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '4px 8px', borderRadius: 4, fontSize: '0.7rem', fontWeight: 700, cursor: 'pointer' }}
                >
                  + Plan
                </button>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {subscriptionPlans.map(sp => (
                  <div key={sp.id} style={{ background: '#0E0E14', padding: 10, borderRadius: 6, border: '1px solid #222230' }}>
                    <div style={{ fontWeight: 700, fontSize: '0.85rem' }}>{sp.name}</div>
                    <div style={{ color: '#34D399', fontSize: '0.78rem', marginTop: 2 }}>
                      ₹{sp.monthly_price_rupees?.toLocaleString()} / mo ({sp.included_rides_count} rides • {sp.category_code})
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Prepaid Bundles (488) */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 16, border: '1px solid #2A2A3A' }}>
              <h4 style={{ margin: '0 0 12px 0', color: '#D4AF37', fontSize: '0.92rem' }}>Prepaid Ride Bundles (488)</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {bundles.map(b => (
                  <div key={b.id} style={{ background: '#0E0E14', padding: 10, borderRadius: 6, border: '1px solid #222230' }}>
                    <div style={{ fontWeight: 700, fontSize: '0.85rem' }}>{b.title}</div>
                    <div style={{ color: '#F59E0B', fontSize: '0.78rem', marginTop: 2 }}>
                      ₹{b.price_rupees?.toLocaleString()} ({b.ride_count} rides • {b.discount_percentage}% OFF)
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Loyalty Catalog (490) */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 16, border: '1px solid #2A2A3A' }}>
              <h4 style={{ margin: '0 0 12px 0', color: '#D4AF37', fontSize: '0.92rem' }}>Loyalty Partner Catalog (490)</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {loyaltyItems.map(it => (
                  <div key={it.id} style={{ background: '#0E0E14', padding: 10, borderRadius: 6, border: '1px solid #222230' }}>
                    <div style={{ fontWeight: 700, fontSize: '0.82rem', color: '#D4AF37' }}>{it.partner_name}</div>
                    <div style={{ fontSize: '0.75rem', color: '#E2E8F0', marginTop: 2 }}>{it.offer_title}</div>
                    <div style={{ fontSize: '0.7rem', color: '#9CA3AF', marginTop: 4 }}>
                      Required: <strong style={{ color: '#F59E0B' }}>{it.required_points} pts</strong> • Voucher: <code style={{ color: '#60A5FA' }}>{it.promo_code}</code>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Airport, Intercity, Hourly & Chauffeur Add-ons */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16 }}>
            {/* Airport Transfer Packages (491) */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 16, border: '1px solid #2A2A3A' }}>
              <h4 style={{ margin: '0 0 10px 0', color: '#D4AF37', fontSize: '0.88rem' }}>Airport Packages (491)</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {airportPackages.map(ap => (
                  <div key={ap.id} style={{ background: '#0E0E14', padding: 10, borderRadius: 6 }}>
                    <div style={{ fontWeight: 700, fontSize: '0.8rem', color: '#60A5FA' }}>[{ap.airport_code}] {ap.package_title}</div>
                    <div style={{ fontSize: '0.74rem', color: '#34D399', marginTop: 2 }}>
                      ₹{ap.flat_fare_rupees?.toLocaleString()} flat • Meet & Greet
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Intercity Ride Products (492) */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 16, border: '1px solid #2A2A3A' }}>
              <h4 style={{ margin: '0 0 10px 0', color: '#D4AF37', fontSize: '0.88rem' }}>Intercity Corridors (492)</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {intercityProducts.map(ip => (
                  <div key={ip.id} style={{ background: '#0E0E14', padding: 10, borderRadius: 6 }}>
                    <div style={{ fontWeight: 700, fontSize: '0.8rem', color: '#A7F3D0' }}>{ip.route_name}</div>
                    <div style={{ fontSize: '0.74rem', color: '#E2E8F0', marginTop: 2 }}>
                      ₹{ip.fixed_fare_rupees?.toLocaleString()} fixed • {ip.distance_km}km (Tolls Included)
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Hourly Chauffeur Booking (493) */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 16, border: '1px solid #2A2A3A' }}>
              <h4 style={{ margin: '0 0 10px 0', color: '#D4AF37', fontSize: '0.88rem' }}>Hourly Bookings (493)</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {hourlyBookings.map(hb => (
                  <div key={hb.id} style={{ background: '#0E0E14', padding: 10, borderRadius: 6 }}>
                    <div style={{ fontWeight: 700, fontSize: '0.8rem' }}>{hb.requested_hours} Hours Dedicated Standby</div>
                    <div style={{ fontSize: '0.74rem', color: '#9CA3AF', marginTop: 2 }}>
                      Rate: ₹{hb.hourly_rate_rupees?.toLocaleString()}/hr • {hb.kilometers_included}km included
                    </div>
                  </div>
                ))}
                {hourlyBookings.length === 0 && (
                  <div style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Tier default: ₹1,500/hr (10km/hr included)</div>
                )}
              </div>
            </div>

            {/* Chauffeur Service Add-ons (494) */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 16, border: '1px solid #2A2A3A' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                <h4 style={{ margin: 0, color: '#D4AF37', fontSize: '0.88rem' }}>Service Add-ons (494)</h4>
                <button
                  onClick={() => setShowAddAddonModal(true)}
                  style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '3px 6px', borderRadius: 4, fontSize: '0.68rem', fontWeight: 700, cursor: 'pointer' }}
                >
                  + Add-on
                </button>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {addons.map(ad => (
                  <div key={ad.id} style={{ background: '#0E0E14', padding: 10, borderRadius: 6 }}>
                    <div style={{ fontWeight: 700, fontSize: '0.78rem', color: '#FBCFE8' }}>{ad.title}</div>
                    <div style={{ fontSize: '0.72rem', color: '#D4AF37', marginTop: 2 }}>₹{ad.price_rupees?.toLocaleString()}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* =====================================================================
          TAB 4: MULTI-CITY & TENANT (Features 495, 496, 497)
      ===================================================================== */}
      {activeTab === 'multicity' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: 20 }}>
          {/* Multi-City Settings (496, 497) */}
          <div style={{ background: '#14141C', borderRadius: 8, padding: 20, border: '1px solid #2A2A3A' }}>
            <h3 style={{ margin: '0 0 14px 0', color: '#D4AF37', fontSize: '1.05rem', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Globe size={18} /> Multi-City Operating Matrix & Taxes (Features 496, 497)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {cityConfigs.map(c => {
                const edit = editingCityTax[c.city_code] || { tax: c.tax_rate_percentage, minFare: c.min_fare_rupees };
                return (
                  <div key={c.id} style={{ background: '#0E0E14', padding: 14, borderRadius: 6, border: '1px solid #222230' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ fontWeight: 700, color: '#F8F9FA' }}>{c.city_name} <span style={{ color: '#60A5FA', fontFamily: 'var(--font-mono)' }}>[{c.city_code}]</span></div>
                      <span style={{
                        padding: '2px 8px',
                        borderRadius: 4,
                        fontSize: '0.7rem',
                        fontWeight: 700,
                        background: c.is_active ? 'rgba(16, 185, 129, 0.2)' : 'rgba(239, 68, 68, 0.2)',
                        color: c.is_active ? '#10B981' : '#EF4444'
                      }}>
                        {c.is_active ? 'OPERATIONAL' : 'INACTIVE'}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.76rem', color: '#9CA3AF', marginTop: 4 }}>
                      Country: {c.country} • Timezone: {c.timezone}
                    </div>

                    <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginTop: 10, paddingTop: 10, borderTop: '1px solid #222230' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span style={{ fontSize: '0.75rem', color: '#9CA3AF' }}>Tax Rate (%):</span>
                        <input
                          type="number"
                          step="0.5"
                          value={edit.tax}
                          onChange={e => setEditingCityTax({
                            ...editingCityTax,
                            [c.city_code]: { ...edit, tax: parseFloat(e.target.value) || 0 }
                          })}
                          style={{ width: 65, background: '#1A1A24', border: '1px solid #333344', color: '#F8F9FA', padding: '4px 6px', borderRadius: 4, fontSize: '0.75rem' }}
                        />
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span style={{ fontSize: '0.75rem', color: '#9CA3AF' }}>Min Fare (₹):</span>
                        <input
                          type="number"
                          step="100"
                          value={edit.minFare}
                          onChange={e => setEditingCityTax({
                            ...editingCityTax,
                            [c.city_code]: { ...edit, minFare: parseInt(e.target.value) || 0 }
                          })}
                          style={{ width: 85, background: '#1A1A24', border: '1px solid #333344', color: '#F8F9FA', padding: '4px 6px', borderRadius: 4, fontSize: '0.75rem' }}
                        />
                      </div>

                      <button
                        onClick={() => handleUpdateCityTaxFare(c.city_code)}
                        style={{ marginLeft: 'auto', background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '5px 12px', borderRadius: 4, fontWeight: 700, fontSize: '0.72rem', cursor: 'pointer' }}
                      >
                        Save Settings
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* White-Label Tenant Config (Feature 495) */}
          <div style={{ background: '#14141C', borderRadius: 8, padding: 20, border: '1px solid #2A2A3A' }}>
            <h3 style={{ margin: '0 0 14px 0', color: '#D4AF37', fontSize: '1.05rem', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Layers size={18} /> White-Label Tenant Branding (Feature 495)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {tenantConfigs.map(t => (
                <div key={t.id} style={{ background: '#0E0E14', padding: 14, borderRadius: 6, border: '1px solid #222230' }}>
                  <div style={{ fontWeight: 700, fontSize: '0.95rem', color: '#D4AF37' }}>{t.brand_name}</div>
                  <div style={{ fontSize: '0.76rem', color: '#9CA3AF', marginTop: 4 }}>
                    Code: <code style={{ color: '#60A5FA' }}>{t.tenant_code}</code> • Domain: <span style={{ color: '#E2E8F0' }}>{t.custom_domain}</span>
                  </div>
                  <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginTop: 10 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.75rem' }}>
                      <span>Primary:</span>
                      <div style={{ width: 16, height: 16, borderRadius: 3, background: t.primary_color, border: '1px solid #444' }} />
                      <span style={{ fontFamily: 'var(--font-mono)' }}>{t.primary_color}</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.75rem' }}>
                      <span>Secondary:</span>
                      <div style={{ width: 16, height: 16, borderRadius: 3, background: t.secondary_color, border: '1px solid #444' }} />
                      <span style={{ fontFamily: 'var(--font-mono)' }}>{t.secondary_color}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* =====================================================================
          TAB 5: GROWTH, A/B & FEEDBACK ANALYTICS (Features 498, 499, 500)
      ===================================================================== */}
      {activeTab === 'growth' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* Top Row: Feedback Analytics Dashboard Summary (Feature 500) */}
          <div style={{ background: '#14141C', borderRadius: 8, padding: 20, border: '1px solid #2A2A3A' }}>
            <h3 style={{ margin: '0 0 16px 0', color: '#D4AF37', fontSize: '1.05rem', display: 'flex', alignItems: 'center', gap: 8 }}>
              <BarChart size={18} /> Product Feedback & CSAT Analytics Dashboard (Feature 500)
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 16 }}>
              <div style={{ background: '#0E0E14', padding: 14, borderRadius: 6, border: '1px solid #222230' }}>
                <div style={{ fontSize: '0.74rem', color: '#9CA3AF' }}>Average CSAT Score</div>
                <div style={{ fontSize: '1.6rem', fontWeight: 700, color: '#F59E0B', marginTop: 4 }}>
                  ★ {feedbackSummary?.average_rating || 4.9} / 5.0
                </div>
              </div>
              <div style={{ background: '#0E0E14', padding: 14, borderRadius: 6, border: '1px solid #222230' }}>
                <div style={{ fontSize: '0.74rem', color: '#9CA3AF' }}>Net Promoter Score (NPS)</div>
                <div style={{ fontSize: '1.6rem', fontWeight: 700, color: '#10B981', marginTop: 4 }}>
                  +{feedbackSummary?.nps_estimate || 94}
                </div>
              </div>
              <div style={{ background: '#0E0E14', padding: 14, borderRadius: 6, border: '1px solid #222230' }}>
                <div style={{ fontSize: '0.74rem', color: '#9CA3AF' }}>Positive Sentiment</div>
                <div style={{ fontSize: '1.6rem', fontWeight: 700, color: '#34D399', marginTop: 4 }}>
                  {feedbackSummary?.sentiment_breakdown?.positive_pct || 92}%
                </div>
              </div>
              <div style={{ background: '#0E0E14', padding: 14, borderRadius: 6, border: '1px solid #222230' }}>
                <div style={{ fontSize: '0.74rem', color: '#9CA3AF' }}>Total Guest Feedbacks</div>
                <div style={{ fontSize: '1.6rem', fontWeight: 700, color: '#60A5FA', marginTop: 4 }}>
                  {feedbackSummary?.total_feedbacks || feedbackList.length}
                </div>
              </div>
            </div>

            {/* Live Guest Feedback Feed */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 12 }}>
              {feedbackList.map(f => (
                <div key={f.id} style={{ background: '#0E0E14', padding: 12, borderRadius: 6, border: '1px solid #222230' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ color: '#F59E0B', fontWeight: 700, fontSize: '0.85rem' }}>{'★'.repeat(f.rating_score)}</div>
                    <span style={{ fontSize: '0.7rem', color: '#10B981', background: 'rgba(16, 185, 129, 0.15)', padding: '2px 6px', borderRadius: 3 }}>
                      {f.sentiment}
                    </span>
                  </div>
                  <div style={{ fontSize: '0.8rem', color: '#E2E8F0', marginTop: 6, fontStyle: 'italic' }}>
                    "{f.comments}"
                  </div>
                  <div style={{ fontSize: '0.7rem', color: '#6B7280', marginTop: 6 }}>
                    Category: {f.feedback_category}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Bottom Row: A/B Experiments (498) & Feature Request Voting (499) */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
            {/* A/B Experiments (Feature 498) */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 20, border: '1px solid #2A2A3A' }}>
              <h3 style={{ margin: '0 0 14px 0', color: '#D4AF37', fontSize: '1.05rem', display: 'flex', alignItems: 'center', gap: 8 }}>
                <BarChart size={18} /> A/B Testing Experiments Framework (Feature 498)
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {abExperiments.map(e => (
                  <div key={e.id} style={{ background: '#0E0E14', padding: 12, borderRadius: 6, border: '1px solid #222230' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ fontWeight: 700, color: '#60A5FA', fontSize: '0.88rem' }}>{e.name}</div>
                      <span style={{ background: 'rgba(16, 185, 129, 0.2)', color: '#10B981', padding: '2px 8px', borderRadius: 4, fontSize: '0.7rem', fontWeight: 700 }}>
                        {e.rollout_status}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.76rem', color: '#9CA3AF', marginTop: 4 }}>{e.description}</div>
                    <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
                      {e.variants?.map((v: string) => (
                        <span key={v} style={{ background: '#1A1A24', border: '1px solid #333344', padding: '2px 6px', borderRadius: 3, fontSize: '0.7rem', color: '#D4AF37' }}>
                          {v}
                        </span>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Feature Request Voting (Feature 499) */}
            <div style={{ background: '#14141C', borderRadius: 8, padding: 20, border: '1px solid #2A2A3A' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
                <h3 style={{ margin: 0, color: '#D4AF37', fontSize: '1.05rem', display: 'flex', alignItems: 'center', gap: 8 }}>
                  <ThumbsUp size={18} /> Customer Feature Request Voting (Feature 499)
                </h3>
                <button
                  onClick={() => setShowAddFeatureModal(true)}
                  style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '6px 12px', borderRadius: 4, fontWeight: 700, fontSize: '0.75rem', cursor: 'pointer' }}
                >
                  <Plus size={14} style={{ display: 'inline', verticalAlign: 'middle' }} /> Submit Request
                </button>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {featureRequests.map(r => (
                  <div key={r.id} style={{ background: '#0E0E14', padding: 12, borderRadius: 6, border: '1px solid #222230', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ maxWidth: '75%' }}>
                      <div style={{ fontWeight: 600, fontSize: '0.86rem', color: '#F8F9FA' }}>{r.title}</div>
                      <div style={{ fontSize: '0.75rem', color: '#9CA3AF', marginTop: 2 }}>{r.description}</div>
                      <span style={{ display: 'inline-block', marginTop: 4, background: '#1A1A24', padding: '2px 6px', borderRadius: 3, fontSize: '0.68rem', color: '#D4AF37' }}>
                        {r.category}
                      </span>
                    </div>
                    <button
                      onClick={async () => {
                        await requestApi(`/enterprise/growth/feature-requests/${r.feature_key}/vote`, { method: 'POST' });
                        void loadData();
                      }}
                      style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer', fontWeight: 700, fontSize: '0.8rem', whiteSpace: 'nowrap' }}
                    >
                      👍 {r.upvote_count} Upvotes
                    </button>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* =====================================================================
          TAB 6: DYNAMIC LOCALIZATION CATALOG
      ===================================================================== */}
      {activeTab === 'localization' && (
        <div style={{ background: '#14141C', borderRadius: 8, padding: 20, border: '1px solid #2A2A3A' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <h3 style={{ color: '#D4AF37', fontSize: '1.1rem', margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Languages size={20} />
              Live Backend API Dynamic Localization Catalog
            </h3>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <span style={{ fontSize: '0.8rem', color: '#9CA3AF' }}>Filter Locale:</span>
              {['en', 'hi', 'mr', 'gu'].map(loc => (
                <button
                  key={loc}
                  onClick={() => setSelectedLocaleFilter(loc)}
                  style={{
                    background: selectedLocaleFilter === loc ? '#D4AF37' : '#1E293B',
                    color: selectedLocaleFilter === loc ? '#0A0A0E' : '#F8F9FA',
                    border: 'none',
                    padding: '4px 12px',
                    borderRadius: 4,
                    fontWeight: 700,
                    cursor: 'pointer',
                    fontSize: '0.8rem'
                  }}
                >
                  {loc.toUpperCase()}
                </button>
              ))}
            </div>
          </div>

          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
              <thead>
                <tr style={{ background: '#0E0E14', color: '#D4AF37', textAlign: 'left', borderBottom: '1px solid #2A2A3A' }}>
                  <th style={{ padding: '10px 12px' }}>String Key</th>
                  <th style={{ padding: '10px 12px' }}>Category</th>
                  <th style={{ padding: '10px 12px' }}>Locale</th>
                  <th style={{ padding: '10px 12px' }}>Live Translated String Text</th>
                  <th style={{ padding: '10px 12px' }}>Action</th>
                </tr>
              </thead>
              <tbody>
                {localizationEntries
                  .filter(e => !selectedLocaleFilter || e.locale === selectedLocaleFilter)
                  .map(entry => (
                    <tr key={entry.id} style={{ borderBottom: '1px solid #222230' }}>
                      <td style={{ padding: '10px 12px', fontFamily: 'var(--font-mono)', color: '#60A5FA' }}>{entry.string_key}</td>
                      <td style={{ padding: '10px 12px', color: '#9CA3AF' }}>{entry.category}</td>
                      <td style={{ padding: '10px 12px', fontWeight: 700, color: '#34D399' }}>{entry.locale.toUpperCase()}</td>
                      <td style={{ padding: '10px 12px', color: '#F8F9FA' }}>
                        <input
                          type="text"
                          defaultValue={entry.translated_text}
                          id={`input-${entry.id}`}
                          style={{
                            width: '100%',
                            background: '#0E0E14',
                            border: '1px solid #334155',
                            color: '#F8F9FA',
                            padding: '6px 10px',
                            borderRadius: 4,
                            fontSize: '0.85rem'
                          }}
                        />
                      </td>
                      <td style={{ padding: '10px 12px' }}>
                        <button
                          onClick={async () => {
                            const inputEl = document.getElementById(`input-${entry.id}`) as HTMLInputElement;
                            if (inputEl) {
                              await requestApi('/localization/catalog', {
                                method: 'POST',
                                body: JSON.stringify({
                                  string_key: entry.string_key,
                                  locale: entry.locale,
                                  category: entry.category,
                                  translated_text: inputEl.value
                                })
                              });
                              setMessage({ text: `Updated '${entry.string_key}' [${entry.locale}] in live DB`, type: 'success' });
                              void loadData();
                            }
                          }}
                          style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '6px 12px', borderRadius: 4, cursor: 'pointer', fontWeight: 700, fontSize: '0.75rem' }}
                        >
                          Save Live
                        </button>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Modal: Add Corporate Account */}
      {showAddCorpModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.8)', display: 'grid', placeItems: 'center', zIndex: 1000 }}>
          <div style={{ background: '#14141C', border: '1.5px solid #D4AF37', borderRadius: 8, padding: 24, width: 440, maxWidth: '90vw' }}>
            <h3 style={{ margin: '0 0 16px 0', color: '#D4AF37' }}>Onboard Corporate Customer Account</h3>
            <form onSubmit={handleCreateCorporate} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Company Name *</label>
                <input
                  type="text"
                  required
                  value={newCorp.company_name}
                  onChange={e => setNewCorp({ ...newCorp, company_name: e.target.value })}
                  placeholder="e.g. Reliance Strategic Advisory"
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Corporate Tax ID / GSTIN *</label>
                <input
                  type="text"
                  required
                  value={newCorp.tax_id}
                  onChange={e => setNewCorp({ ...newCorp, tax_id: e.target.value })}
                  placeholder="27AAACR1234F1Z1"
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Central Billing Email *</label>
                <input
                  type="email"
                  required
                  value={newCorp.billing_email}
                  onChange={e => setNewCorp({ ...newCorp, billing_email: e.target.value })}
                  placeholder="invoicing@company.com"
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Monthly Credit Limit (₹)</label>
                <input
                  type="number"
                  value={newCorp.credit_limit_rupees}
                  onChange={e => setNewCorp({ ...newCorp, credit_limit_rupees: parseInt(e.target.value) || 0 })}
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 12 }}>
                <button
                  type="button"
                  onClick={() => setShowAddCorpModal(false)}
                  style={{ background: '#1A1A24', color: '#F8F9FA', border: '1px solid #333', padding: '8px 16px', borderRadius: 4, cursor: 'pointer' }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '8px 18px', borderRadius: 4, fontWeight: 700, cursor: 'pointer' }}
                >
                  Confirm Onboarding
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Add Partner */}
      {showAddPartnerModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.8)', display: 'grid', placeItems: 'center', zIndex: 1000 }}>
          <div style={{ background: '#14141C', border: '1.5px solid #D4AF37', borderRadius: 8, padding: 24, width: 420, maxWidth: '90vw' }}>
            <h3 style={{ margin: '0 0 16px 0', color: '#D4AF37' }}>Onboard Partner Account</h3>
            <form onSubmit={handleCreatePartner} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Partner Name *</label>
                <input
                  type="text"
                  required
                  value={newPartner.partner_name}
                  onChange={e => setNewPartner({ ...newPartner, partner_name: e.target.value })}
                  placeholder="e.g. The St. Regis Mumbai"
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Partner Type</label>
                <select
                  value={newPartner.partner_type}
                  onChange={e => setNewPartner({ ...newPartner, partner_type: e.target.value })}
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                >
                  <option value="HOTEL">Hotel</option>
                  <option value="VENUE">Venue / Convention</option>
                  <option value="CONCIERGE">Concierge Agency</option>
                </select>
              </div>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Commission Rate (%)</label>
                <input
                  type="number"
                  step="0.5"
                  value={newPartner.commission_rate_pct}
                  onChange={e => setNewPartner({ ...newPartner, commission_rate_pct: parseFloat(e.target.value) || 0 })}
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 12 }}>
                <button type="button" onClick={() => setShowAddPartnerModal(false)} style={{ background: '#1A1A24', color: '#F8F9FA', border: '1px solid #333', padding: '8px 16px', borderRadius: 4, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '8px 18px', borderRadius: 4, fontWeight: 700, cursor: 'pointer' }}>Save Partner</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Add Event */}
      {showAddEventModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.8)', display: 'grid', placeItems: 'center', zIndex: 1000 }}>
          <div style={{ background: '#14141C', border: '1.5px solid #D4AF37', borderRadius: 8, padding: 24, width: 440, maxWidth: '90vw' }}>
            <h3 style={{ margin: '0 0 16px 0', color: '#D4AF37' }}>Plan Event Transport Fleet</h3>
            <form onSubmit={handleCreateEvent} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Event Title *</label>
                <input
                  type="text"
                  required
                  value={newEvent.event_name}
                  onChange={e => setNewEvent({ ...newEvent, event_name: e.target.value })}
                  placeholder="e.g. World Diamond Congress 2026"
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Venue Address *</label>
                <input
                  type="text"
                  required
                  value={newEvent.venue_address}
                  onChange={e => setNewEvent({ ...newEvent, venue_address: e.target.value })}
                  placeholder="e.g. Grand Hyatt Mumbai Hotel & Residences"
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div style={{ display: 'flex', gap: 10 }}>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Vehicles Required</label>
                  <input
                    type="number"
                    value={newEvent.allocated_vehicles_count}
                    onChange={e => setNewEvent({ ...newEvent, allocated_vehicles_count: parseInt(e.target.value) || 0 })}
                    style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>VIP Guests</label>
                  <input
                    type="number"
                    value={newEvent.total_guests}
                    onChange={e => setNewEvent({ ...newEvent, total_guests: parseInt(e.target.value) || 0 })}
                    style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                  />
                </div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 12 }}>
                <button type="button" onClick={() => setShowAddEventModal(false)} style={{ background: '#1A1A24', color: '#F8F9FA', border: '1px solid #333', padding: '8px 16px', borderRadius: 4, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '8px 18px', borderRadius: 4, fontWeight: 700, cursor: 'pointer' }}>Initiate Workspace</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Add Subscription Plan (Feature 487) */}
      {showAddPlanModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.8)', display: 'grid', placeItems: 'center', zIndex: 1000 }}>
          <div style={{ background: '#14141C', border: '1.5px solid #D4AF37', borderRadius: 8, padding: 24, width: 440, maxWidth: '90vw' }}>
            <h3 style={{ margin: '0 0 16px 0', color: '#D4AF37' }}>Create Fleet Subscription Plan</h3>
            <form onSubmit={handleCreatePlan} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Plan Code *</label>
                <input
                  type="text"
                  required
                  value={newPlan.plan_code}
                  onChange={e => setNewPlan({ ...newPlan, plan_code: e.target.value })}
                  placeholder="e.g. PLAN_ROYAL_20"
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Plan Name *</label>
                <input
                  type="text"
                  required
                  value={newPlan.name}
                  onChange={e => setNewPlan({ ...newPlan, name: e.target.value })}
                  placeholder="e.g. Maybach Royal 20 Pass"
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div style={{ display: 'flex', gap: 10 }}>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Monthly Price (₹)</label>
                  <input
                    type="number"
                    value={newPlan.monthly_price_rupees}
                    onChange={e => setNewPlan({ ...newPlan, monthly_price_rupees: parseInt(e.target.value) || 0 })}
                    style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Included Rides</label>
                  <input
                    type="number"
                    value={newPlan.included_rides_count}
                    onChange={e => setNewPlan({ ...newPlan, included_rides_count: parseInt(e.target.value) || 0 })}
                    style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                  />
                </div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 12 }}>
                <button type="button" onClick={() => setShowAddPlanModal(false)} style={{ background: '#1A1A24', color: '#F8F9FA', border: '1px solid #333', padding: '8px 16px', borderRadius: 4, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '8px 18px', borderRadius: 4, fontWeight: 700, cursor: 'pointer' }}>Save Plan</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Add Service Addon (Feature 494) */}
      {showAddAddonModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.8)', display: 'grid', placeItems: 'center', zIndex: 1000 }}>
          <div style={{ background: '#14141C', border: '1.5px solid #D4AF37', borderRadius: 8, padding: 24, width: 440, maxWidth: '90vw' }}>
            <h3 style={{ margin: '0 0 16px 0', color: '#D4AF37' }}>Register Chauffeur Service Add-on</h3>
            <form onSubmit={handleCreateAddon} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Add-on Code *</label>
                <input
                  type="text"
                  required
                  value={newAddon.addon_code}
                  onChange={e => setNewAddon({ ...newAddon, addon_code: e.target.value })}
                  placeholder="e.g. ADDON_STARLINK_WIFI"
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Title *</label>
                <input
                  type="text"
                  required
                  value={newAddon.title}
                  onChange={e => setNewAddon({ ...newAddon, title: e.target.value })}
                  placeholder="e.g. High-Speed Starlink Satellite Wi-Fi"
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Price per Voyage (₹)</label>
                <input
                  type="number"
                  value={newAddon.price_rupees}
                  onChange={e => setNewAddon({ ...newAddon, price_rupees: parseInt(e.target.value) || 0 })}
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 12 }}>
                <button type="button" onClick={() => setShowAddAddonModal(false)} style={{ background: '#1A1A24', color: '#F8F9FA', border: '1px solid #333', padding: '8px 16px', borderRadius: 4, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '8px 18px', borderRadius: 4, fontWeight: 700, cursor: 'pointer' }}>Save Add-on</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Add Feature Request (Feature 499) */}
      {showAddFeatureModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.8)', display: 'grid', placeItems: 'center', zIndex: 1000 }}>
          <div style={{ background: '#14141C', border: '1.5px solid #D4AF37', borderRadius: 8, padding: 24, width: 440, maxWidth: '90vw' }}>
            <h3 style={{ margin: '0 0 16px 0', color: '#D4AF37' }}>Submit Feature Request</h3>
            <form onSubmit={handleCreateFeature} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Feature Title *</label>
                <input
                  type="text"
                  required
                  value={newFeature.title}
                  onChange={e => setNewFeature({ ...newFeature, title: e.target.value })}
                  placeholder="e.g. In-Cabin Wireless Noise Cancellation"
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.78rem', color: '#9CA3AF' }}>Description</label>
                <textarea
                  value={newFeature.description}
                  onChange={e => setNewFeature({ ...newFeature, description: e.target.value })}
                  placeholder="Detailed executive suggestion..."
                  rows={3}
                  style={{ width: '100%', background: '#0E0E14', border: '1px solid #333344', color: '#F8F9FA', padding: '8px 10px', borderRadius: 4, marginTop: 4 }}
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 12 }}>
                <button type="button" onClick={() => setShowAddFeatureModal(false)} style={{ background: '#1A1A24', color: '#F8F9FA', border: '1px solid #333', padding: '8px 16px', borderRadius: 4, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" style={{ background: '#D4AF37', color: '#0A0A0E', border: 'none', padding: '8px 18px', borderRadius: 4, fontWeight: 700, cursor: 'pointer' }}>Post Request</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminEnterpriseGrowthConsole;
