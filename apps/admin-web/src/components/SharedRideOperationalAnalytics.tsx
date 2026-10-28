import React, { useCallback, useEffect, useState } from 'react';
import { Activity, RefreshCw, Users, Armchair, Route, CircleDollarSign } from 'lucide-react';
import { fetchWithAuth } from '../services/api';

type Analytics = {
  period: { days: number; start: string; end: string };
  summary: {
    pooled_trips: number; shared_bookings: number; active_pools: number; open_seats: number;
    active_seat_capacity: number; active_reserved_seats: number; reserved_seat_utilization_percent: number;
    active_passengers_onboard: number; onboard_capacity_utilization_percent: number;
    no_show_bookings: number; no_show_rate_percent: number; completed_bookings: number;
    cancelled_bookings: number; passenger_savings_cents: number; allocated_fare_cents: number;
  };
  trip_status_counts: Record<string, number>;
  booking_status_counts: Record<string, number>;
  occupancy_event_counts: Record<string, number>;
  stop_status_counts: Record<string, number>;
  category_capacity: Array<{ category: string; trips: number; capacity: number; reserved_seats: number; passengers_onboard: number; open_seats: number }>;
  daily_activity: Array<{ date: string; trips_opened: number; bookings_created: number }>;
  recent_pools: Array<{ reference: string; status: string; category_id: string; driver_assigned: boolean; capacity: number; reserved_seats: number; passengers_onboard: number; open_seats: number; updated_at: string | null }>;
  source: string;
};

const gold = '#d4af37';
const panel: React.CSSProperties = { background: 'linear-gradient(145deg, #191817, #11100f)', border: '1px solid rgba(212,175,55,.2)', borderRadius: 16, padding: 20 };
const money = (cents: number) => new Intl.NumberFormat(undefined, { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(cents / 100);

export const SharedRideOperationalAnalytics: React.FC = () => {
  const [days, setDays] = useState(30);
  const [data, setData] = useState<Analytics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    const result = await fetchWithAuth(`/admin/shared-rides/analytics?days=${days}`) as Analytics | null;
    if (!result) { setData(null); setError('Shared-ride analytics could not be loaded. Confirm the admin session and API connection, then retry.'); }
    else setData(result);
    setLoading(false);
  }, [days]);

  useEffect(() => { void load(); }, [load]);

  const s = data?.summary;
  const metrics = s ? [
    { title: 'Shared bookings', value: s.shared_bookings.toLocaleString(), detail: `${s.pooled_trips.toLocaleString()} pools opened`, icon: Users },
    { title: 'Seat utilization', value: `${s.reserved_seat_utilization_percent}%`, detail: `${s.active_reserved_seats} of ${s.active_seat_capacity} active seats reserved`, icon: Armchair },
    { title: 'Active pools', value: s.active_pools.toLocaleString(), detail: `${s.open_seats} seats currently available`, icon: Route },
    { title: 'Passenger savings', value: money(s.passenger_savings_cents), detail: `${money(s.allocated_fare_cents)} allocated fares`, icon: CircleDollarSign },
  ] : [];

  return <section style={{ color: '#f3efe7', maxWidth: 1440, margin: '0 auto', padding: '28px 24px 48px' }} aria-busy={loading}>
    <header style={{ display: 'flex', gap: 16, justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', marginBottom: 24 }}>
      <div><div style={{ color: gold, textTransform: 'uppercase', letterSpacing: '.16em', fontSize: 11, marginBottom: 8 }}>Operations intelligence</div><h1 style={{ margin: 0, fontFamily: 'Georgia, serif', fontWeight: 500 }}>Shared ride performance</h1><p style={{ color: '#aaa49a', margin: '8px 0 0' }}>Capacity, matching outcomes and fare allocation from recorded pooled trips.</p></div>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}><label htmlFor="shared-period" style={{ color: '#aaa49a', fontSize: 13 }}>Reporting window</label><select id="shared-period" value={days} onChange={event => setDays(Number(event.target.value))} style={{ background: '#211f1c', color: '#f3efe7', border: `1px solid ${gold}66`, borderRadius: 9, padding: '10px 12px' }}><option value={7}>7 days</option><option value={30}>30 days</option><option value={90}>90 days</option><option value={365}>12 months</option></select><button onClick={() => void load()} disabled={loading} aria-label="Refresh shared ride analytics" style={{ ...buttonStyle, opacity: loading ? .6 : 1 }}><RefreshCw size={16} /> Refresh</button></div>
    </header>
    {loading && <div role="status" style={{ ...panel, color: '#bdb6aa' }}>Loading recorded shared-ride activity…</div>}
    {!loading && error && <div role="alert" style={{ ...panel, borderColor: '#a45c53' }}><strong>Analytics unavailable</strong><p style={{ color: '#c7bfb2' }}>{error}</p><button onClick={() => void load()} style={buttonStyle}><RefreshCw size={16} /> Retry</button></div>}
    {!loading && data && !s && <div role="status" style={panel}>No summary metrics were returned for this reporting window.</div>}
    {!loading && data && s && <>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(220px,1fr))', gap: 14, marginBottom: 16 }}>
        {metrics.map(({ title, value, detail, icon: Icon }) => <article key={title} style={panel}><div style={{ display: 'flex', justifyContent: 'space-between', color: '#aaa49a', fontSize: 13 }}>{title}<Icon size={18} color={gold} /></div><div style={{ fontFamily: 'Georgia,serif', fontSize: 30, margin: '14px 0 6px' }}>{value}</div><div style={{ color: '#aaa49a', fontSize: 12 }}>{detail}</div></article>)}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(320px,1fr))', gap: 16, marginBottom: 16 }}>
        <article style={panel}><h2 style={heading}>Pool and booking outcomes</h2><div style={rowStyle}><span>Pool states</span><strong>{Object.entries(data.trip_status_counts).map(([k,v]) => `${k}: ${v}`).join(' · ') || 'No pools'}</strong></div><div style={rowStyle}><span>Booking states</span><strong>{Object.entries(data.booking_status_counts).map(([k,v]) => `${k}: ${v}`).join(' · ') || 'No bookings'}</strong></div><div style={rowStyle}><span>No-show rate</span><strong>{s.no_show_rate_percent}% ({s.no_show_bookings})</strong></div><div style={rowStyle}><span>Completed / cancelled</span><strong>{s.completed_bookings} / {s.cancelled_bookings}</strong></div><div style={rowStyle}><span>Passengers currently onboard</span><strong>{s.active_passengers_onboard} · {s.onboard_capacity_utilization_percent}% capacity</strong></div></article>
        <article style={panel}><h2 style={heading}>Capacity planning by vehicle</h2>{data.category_capacity.length === 0 ? <p style={{ color: '#aaa49a' }}>No pooled trips were recorded in this period.</p> : <div style={{ overflowX: 'auto' }}><table style={tableStyle}><thead><tr><th>Category</th><th>Pools</th><th>Reserved</th><th>Onboard</th><th>Open</th></tr></thead><tbody>{data.category_capacity.map(item => <tr key={item.category}><td>{item.category}</td><td>{item.trips}</td><td>{item.reserved_seats}/{item.capacity}</td><td>{item.passengers_onboard}</td><td>{item.open_seats}</td></tr>)}</tbody></table></div>}</article>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(320px,1fr))', gap: 16 }}>
        <article style={panel}><h2 style={heading}>Daily activity</h2>{data.daily_activity.length === 0 ? <p style={{ color: '#aaa49a' }}>No trips or bookings were recorded in this period.</p> : <div style={{ display: 'grid', gap: 10 }}>{data.daily_activity.slice(-14).map(day => { const max = Math.max(1, ...data.daily_activity.map(d => d.trips_opened + d.bookings_created)); return <div key={day.date} style={{ display: 'grid', gridTemplateColumns: '90px 1fr 52px', gap: 10, alignItems: 'center', fontSize: 12 }}><span style={{ color: '#aaa49a' }}>{day.date.slice(5)}</span><div aria-label={`${day.trips_opened} pools, ${day.bookings_created} bookings`} style={{ display: 'flex', height: 8, background: '#332f27', borderRadius: 8, overflow: 'hidden' }}><div style={{ width: `${day.trips_opened / max * 100}%`, background: gold }} /><div style={{ width: `${day.bookings_created / max * 100}%`, background: '#8f7950' }} /></div><span>{day.trips_opened}+{day.bookings_created}</span></div>; })}<small style={{ color: '#aaa49a' }}>Gold: pools opened · bronze: bookings created</small></div>}</article>
        <article style={panel}><h2 style={heading}>Recently updated pools</h2>{data.recent_pools.length === 0 ? <p style={{ color: '#aaa49a' }}>No pool records are available for this period.</p> : <div style={{ overflowX: 'auto' }}><table style={tableStyle}><thead><tr><th>Reference</th><th>State</th><th>Seats</th><th>Driver</th></tr></thead><tbody>{data.recent_pools.map(pool => <tr key={pool.reference}><td>{pool.reference}</td><td>{pool.status}</td><td>{pool.reserved_seats}/{pool.capacity} · {pool.open_seats} open</td><td>{pool.driver_assigned ? 'Assigned' : 'Pending'}</td></tr>)}</tbody></table></div>}<div style={{ color: '#aaa49a', fontSize: 11, marginTop: 14 }}>Source: recorded trip, booking, stop and occupancy events. No passenger identifiers are included.</div></article>
      </div>
      <div style={{ color: '#8f887d', fontSize: 11, marginTop: 14, display: 'flex', gap: 8, alignItems: 'center' }}><Activity size={14} /> Data window: last {data.period.days} days · derived from {data.source}</div>
    </>}
  </section>;
};

const heading: React.CSSProperties = { fontFamily: 'Georgia,serif', fontWeight: 500, fontSize: 19, margin: '0 0 16px' };
const buttonStyle: React.CSSProperties = { border: `1px solid ${gold}66`, background: '#24211c', color: '#f3efe7', borderRadius: 9, padding: '10px 13px', display: 'inline-flex', gap: 8, alignItems: 'center', cursor: 'pointer' };
const rowStyle: React.CSSProperties = { borderTop: '1px solid #332f29', padding: '12px 0', display: 'flex', justifyContent: 'space-between', gap: 12, color: '#aaa49a', fontSize: 13 };
const tableStyle: React.CSSProperties = { width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: 12 };
