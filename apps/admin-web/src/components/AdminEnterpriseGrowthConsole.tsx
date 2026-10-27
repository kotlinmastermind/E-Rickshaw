import React, { useEffect, useState } from 'react';
import {
  Building2, Users, FileText, Award, Calendar, Package, Globe, Sliders,
  CheckCircle, RefreshCw, ThumbsUp, BarChart, Layers, DollarSign, Clock, MapPin
} from 'lucide-react';
import { requestApi } from '../services/api';

export const AdminEnterpriseGrowthConsole: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'corporate' | 'partners' | 'products' | 'multicity' | 'growth'>('corporate');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  // Data states
  const [corporateAccounts, setCorporateAccounts] = useState<any[]>([]);
  const [approvals, setApprovals] = useState<any[]>([]);
  const [partnerAccounts, setPartnerAccounts] = useState<any[]>([]);
  const [events, setEvents] = useState<any[]>([]);
  const [subscriptionPlans, setSubscriptionPlans] = useState<any[]>([]);
  const [bundles, setBundles] = useState<any[]>([]);
  const [airportPackages, setAirportPackages] = useState<any[]>([]);
  const [intercityProducts, setIntercityProducts] = useState<any[]>([]);
  const [addons, setAddons] = useState<any[]>([]);
  const [tenantConfigs, setTenantConfigs] = useState<any[]>([]);
  const [cityConfigs, setCityConfigs] = useState<any[]>([]);
  const [abExperiments, setAbExperiments] = useState<any[]>([]);
  const [featureRequests, setFeatureRequests] = useState<any[]>([]);

  const loadData = async () => {
    setLoading(true);
    try {
      if (activeTab === 'corporate') {
        const [accs, apprs] = await Promise.all([
          requestApi<any[]>('/enterprise/corporate/accounts'),
          requestApi<any[]>('/enterprise/corporate/approvals')
        ]);
        setCorporateAccounts(accs); setApprovals(apprs);
      } else if (activeTab === 'partners') {
        const [parts, evts] = await Promise.all([
          requestApi<any[]>('/enterprise/partners/accounts'),
          requestApi<any[]>('/enterprise/partners/events')
        ]);
        setPartnerAccounts(parts); setEvents(evts);
      } else if (activeTab === 'products') {
        const [plans, bndls, air, inter, add] = await Promise.all([
          requestApi<any[]>('/enterprise/subscriptions/plans'),
          requestApi<any[]>('/enterprise/subscriptions/bundles'),
          requestApi<any[]>('/enterprise/products/airport-transfers'),
          requestApi<any[]>('/enterprise/products/intercity'),
          requestApi<any[]>('/enterprise/products/add-ons')
        ]);
        setSubscriptionPlans(plans); setBundles(bndls); setAirportPackages(air);
        setIntercityProducts(inter); setAddons(add);
      } else if (activeTab === 'multicity') {
        const [tenants, cities] = await Promise.all([
          requestApi<any[]>('/enterprise/tenant/configs'),
          requestApi<any[]>('/enterprise/cities/configs')
        ]);
        setTenantConfigs(tenants); setCityConfigs(cities);
      } else if (activeTab === 'growth') {
        const [exps, reqs] = await Promise.all([
          requestApi<any[]>('/enterprise/growth/ab-experiments'),
          requestApi<any[]>('/enterprise/growth/feature-requests')
        ]);
        setAbExperiments(exps); setFeatureRequests(reqs);
      }
    } catch (e: any) {
      setMessage(`Error loading enterprise data: ${e.message}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, [activeTab]);

  return (
    <div style={{ padding: 24, color: '#f8fafc', background: '#0b0f17', minHeight: '100vh' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, borderBottom: '1px solid #1e293b', paddingBottom: 16 }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: '#d4af37', margin: 0, display: 'flex', alignItems: 'center', gap: 10 }}>
            <Building2 size={26} color="#d4af37" />
            Enterprise, Partnerships & Growth Suite
          </h1>
          <p style={{ fontSize: '0.85rem', color: '#94a3b8', margin: '4px 0 0 0' }}>
            Part 20: B2B Accounts, Concierge Portals, Subscriptions, Multi-City & Feedback Analytics
          </p>
        </div>
        <button
          onClick={loadData}
          style={{
            background: 'var(--card-bg, #1a2332)',
            border: '1px solid #d4af37',
            color: '#d4af37',
            padding: '8px 16px',
            borderRadius: 6,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            fontWeight: 600
          }}
        >
          <RefreshCw size={16} className={loading ? 'spin' : ''} />
          Refresh Enterprise Data
        </button>
      </div>

      {message && (
        <div style={{ background: '#1e293b', borderLeft: '4px solid #d4af37', padding: '12px 16px', borderRadius: 4, marginBottom: 16, fontSize: '0.9rem' }}>
          {message}
        </div>
      )}

      {/* Nav Tabs */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 20, borderBottom: '1px solid #1e293b', paddingBottom: 8, overflowX: 'auto' }}>
        {[
          { id: 'corporate', label: 'Corporate & Invoicing', icon: Building2 },
          { id: 'partners', label: 'Partners & Concierge', icon: Users },
          { id: 'products', label: 'Subscriptions & Products', icon: Package },
          { id: 'multicity', label: 'Multi-City & Tenant', icon: Globe },
          { id: 'growth', label: 'A/B & Feature Voting', icon: BarChart }
        ].map(tab => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              style={{
                background: isActive ? '#d4af37' : '#1e293b',
                color: isActive ? '#0b0f17' : '#e2e8f0',
                border: 'none',
                padding: '10px 18px',
                borderRadius: 6,
                fontWeight: isActive ? 700 : 500,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                fontSize: '0.85rem'
              }}
            >
              <Icon size={16} />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Tab 1: Corporate & Invoicing */}
      {activeTab === 'corporate' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Building2 size={18} />
              Corporate Accounts (Features 476, 479, 480)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {corporateAccounts.map(c => (
                <div key={c.id} style={{ background: '#090d14', padding: 12, borderRadius: 6 }}>
                  <div style={{ fontWeight: 700, color: '#f8fafc', fontSize: '0.95rem' }}>{c.company_name}</div>
                  <div style={{ fontSize: '0.78rem', color: '#94a3b8', marginTop: 4 }}>
                    Tax ID: {c.tax_id} | Billing: {c.billing_email}
                  </div>
                  <div style={{ fontSize: '0.82rem', color: '#34d399', fontWeight: 600, marginTop: 6 }}>
                    Credit Limit: ₹{c.credit_limit_rupees.toLocaleString()} | Due: ₹{c.balance_due_rupees.toLocaleString()}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <CheckCircle size={18} />
              Business Travel Approval Requests (Feature 489)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {approvals.map(a => (
                <div key={a.id} style={{ background: '#090d14', padding: 12, borderRadius: 6, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontWeight: 600, color: '#38bdf8' }}>Cost Center: {a.cost_center} (Ride #{a.ride_id})</div>
                    <div style={{ fontSize: '0.78rem', color: '#94a3b8' }}>{a.justification}</div>
                  </div>
                  <span style={{ padding: '4px 8px', borderRadius: 4, fontSize: '0.72rem', background: '#d4af37', color: '#0b0f17', fontWeight: 700 }}>
                    {a.status}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Tab 2: Partners & Concierge */}
      {activeTab === 'partners' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Users size={18} />
              Hotel & Venue Partners (Features 481, 482, 483, 484)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {partnerAccounts.map(p => (
                <div key={p.id} style={{ background: '#090d14', padding: 12, borderRadius: 6 }}>
                  <div style={{ fontWeight: 700, color: '#f8fafc' }}>{p.partner_name} [{p.partner_type}]</div>
                  <div style={{ fontSize: '0.78rem', color: '#94a3b8', marginTop: 4 }}>
                    Code: <code style={{ color: '#38bdf8' }}>{p.referral_code}</code> | Commission: {p.commission_rate_pct}%
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Calendar size={18} />
              Event Transport Planning Workspaces (Feature 485)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {events.map(e => (
                <div key={e.id} style={{ background: '#090d14', padding: 12, borderRadius: 6 }}>
                  <div style={{ fontWeight: 700, color: '#a7f3d0' }}>{e.event_name}</div>
                  <div style={{ fontSize: '0.78rem', color: '#94a3b8', marginTop: 4 }}>
                    Venue: {e.venue_address} | Vehicles: {e.allocated_vehicles_count} | Guests: {e.total_guests}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Tab 3: Subscriptions & Products */}
      {activeTab === 'products' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16 }}>
          <div style={{ background: '#131c2a', padding: 16, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h4 style={{ color: '#d4af37', margin: '0 0 10px 0' }}>Subscriptions (486, 487)</h4>
            {subscriptionPlans.map(sp => (
              <div key={sp.id} style={{ background: '#090d14', padding: 10, borderRadius: 6, marginBottom: 8, fontSize: '0.8rem' }}>
                <div style={{ fontWeight: 700 }}>{sp.name}</div>
                <div style={{ color: '#34d399' }}>₹{sp.monthly_price_rupees.toLocaleString()} / mo ({sp.included_rides_count} rides)</div>
              </div>
            ))}
          </div>

          <div style={{ background: '#131c2a', padding: 16, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h4 style={{ color: '#d4af37', margin: '0 0 10px 0' }}>Prepaid Bundles (488)</h4>
            {bundles.map(b => (
              <div key={b.id} style={{ background: '#090d14', padding: 10, borderRadius: 6, marginBottom: 8, fontSize: '0.8rem' }}>
                <div style={{ fontWeight: 700 }}>{b.title}</div>
                <div style={{ color: '#fbbf24' }}>₹{b.price_rupees.toLocaleString()} ({b.discount_percentage}% OFF)</div>
              </div>
            ))}
          </div>

          <div style={{ background: '#131c2a', padding: 16, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h4 style={{ color: '#d4af37', margin: '0 0 10px 0' }}>Airport & Intercity (491, 492)</h4>
            {airportPackages.map(ap => (
              <div key={ap.id} style={{ background: '#090d14', padding: 10, borderRadius: 6, marginBottom: 8, fontSize: '0.8rem' }}>
                <div style={{ fontWeight: 700 }}>[{ap.airport_code}] {ap.package_title}</div>
                <div style={{ color: '#38bdf8' }}>Flat Fare: ₹{ap.flat_fare_rupees.toLocaleString()}</div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Tab 4: Multi-City & Tenant */}
      {activeTab === 'multicity' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Globe size={18} />
              Multi-City Operating Settings & Tax (Features 496, 497)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {cityConfigs.map(c => (
                <div key={c.id} style={{ background: '#090d14', padding: 12, borderRadius: 6 }}>
                  <div style={{ fontWeight: 700, color: '#f8fafc' }}>{c.city_name} [{c.city_code}]</div>
                  <div style={{ fontSize: '0.78rem', color: '#94a3b8', marginTop: 4 }}>
                    Country: {c.country} | Timezone: {c.timezone} | Tax Rate: {c.tax_rate_percentage}% | Min Fare: ₹{c.min_fare_rupees}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Layers size={18} />
              White-Label Tenant Config (Feature 495)
            </h3>
            {tenantConfigs.map(t => (
              <div key={t.id} style={{ background: '#090d14', padding: 12, borderRadius: 6 }}>
                <div style={{ fontWeight: 700, color: '#d4af37' }}>{t.brand_name} ({t.tenant_code})</div>
                <div style={{ fontSize: '0.78rem', color: '#94a3b8', marginTop: 4 }}>
                  Domain: {t.custom_domain} | Palette: {t.primary_color} / {t.secondary_color}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Tab 5: Growth, A/B & Feedback */}
      {activeTab === 'growth' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <BarChart size={18} />
              A/B Testing Experiments (Feature 498)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {abExperiments.map(e => (
                <div key={e.id} style={{ background: '#090d14', padding: 12, borderRadius: 6 }}>
                  <div style={{ fontWeight: 700, color: '#38bdf8' }}>{e.name}</div>
                  <div style={{ fontSize: '0.78rem', color: '#94a3b8', marginTop: 4 }}>
                    Variants: {e.variants.join(', ')} | Status: <strong style={{ color: '#34d399' }}>{e.rollout_status}</strong>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div style={{ background: '#131c2a', padding: 20, borderRadius: 8, border: '1px solid #1e293b' }}>
            <h3 style={{ color: '#d4af37', fontSize: '1.1rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
              <ThumbsUp size={18} />
              Customer Feature Request Voting (Feature 499)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {featureRequests.map(r => (
                <div key={r.id} style={{ background: '#090d14', padding: 12, borderRadius: 6, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontWeight: 600 }}>{r.title}</div>
                    <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>{r.description}</div>
                  </div>
                  <button
                    onClick={async () => {
                      await requestApi(`/enterprise/growth/feature-requests/${r.feature_key}/vote`, { method: 'POST' });
                      void loadData();
                    }}
                    style={{ background: '#d4af37', color: '#0b0f17', border: 'none', padding: '6px 12px', borderRadius: 4, cursor: 'pointer', fontWeight: 700, fontSize: '0.8rem' }}
                  >
                    👍 {r.upvote_count} Upvotes
                  </button>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminEnterpriseGrowthConsole;
