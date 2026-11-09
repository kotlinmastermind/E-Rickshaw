import { requestApi } from './api';

export interface LiveCategory {
  id: string;
  code: string;
  name: string;
  description: string;
  capacity: number;
  quietness_db?: number;
  emissions_g_per_km?: number;
  legroom_inches?: number;
  is_electric: boolean;
  wheelchair_accessible: boolean;
  pet_friendly: boolean;
  is_active?: boolean;
}

export interface LiveFareRule {
  id: string;
  category_id: string;
  category_name?: string;
  version: number;
  base_fare_rupees: number;
  per_km_rupees: number;
  per_minute_rupees: number;
  minimum_fare_rupees: number;
  cancellation_fee_rupees: number;
  surge_multiplier: number;
  shared_discount_percentage?: number;
  tax_percentage?: number;
  is_active: boolean;
}

export interface LiveUser {
  id: string;
  phone_number: string;
  email?: string;
  full_name?: string;
  role: string;
  is_active: boolean;
  created_at?: string;
  loyalty_tier?: string;
  total_rides?: number;
}

export interface LiveDriver {
  id: string;
  user_id?: string;
  full_name: string;
  phone_number: string;
  plate_number: string;
  vehicle_model: string;
  license_number?: string;
  rating: number;
  total_trips: number;
  wallet_balance_rupees: number;
  approval_status: 'APPROVED' | 'PENDING' | 'REJECTED' | 'SUSPENDED';
  documents_count?: number;
  pending_documents?: number;
  is_online: boolean;
  battery_soc?: number;
}

export interface LivePayout {
  id: string;
  driver_id: string;
  driver_name?: string;
  amount_cents: number;
  amount_rupees: number;
  bank_account_masked?: string;
  ifsc?: string;
  status: string;
  requested_at?: string;
  processed_at?: string;
}

export interface LiveAuditLog {
  id: string;
  actor_id?: string | null;
  actor_role?: string;
  action: string;
  target_resource?: string;
  target_id?: string | null;
  status: string;
  created_at?: string;
  details?: string;
  ip_address?: string | null;
}

export interface LiveDashboardMetrics {
  total_rides: number;
  active_rides: number;
  completed_rides: number;
  gross_booking_cents: number;
  currency: string;
  online_drivers: number;
  total_users: number;
  platform_status: string;
}

export interface LiveRide {
  id: string;
  customer_id?: string;
  customer_name?: string;
  customer_phone?: string;
  assigned_driver_id?: string;
  driver_name?: string;
  driver_plate?: string;
  pickup_address: string;
  destination_address: string;
  estimated_fare_rupees?: number;
  final_fare_rupees?: number;
  fare_rupees?: number;
  status: string;
  created_at?: string;
  requested_at?: string;
  surge_applied?: number;
}

export interface LiveSupportTicket {
  id: string;
  ticket_number?: string;
  complaint_reference_number?: string;
  user_id: string;
  ride_id?: string;
  subject: string;
  category: string;
  status: string;
  priority: string;
  assigned_squad?: string;
  language_preference?: string;
  is_escalated: boolean;
  escalation_reason?: string;
  lost_item_category?: string;
  lost_item_status?: string;
  refund_amount_rupees?: number;
  assigned_to?: string;
  created_at: string;
}

export interface LiveFareDispute {
  id: string;
  ride_id: string;
  customer_id: string;
  dispute_reason: string;
  claimed_amount_rupees: number;
  customer_comment?: string;
  status: string;
  admin_resolution_notes?: string;
  created_at: string;
}

export interface LiveGeofence {
  id: string;
  name: string;
  zone_type: string;
  coordinates?: any;
  surge_multiplier: number;
  is_active: boolean;
}

export interface LiveSurgeRule {
  id: string;
  zone_id?: string;
  zone_name?: string;
  multiplier: number;
  reason: string;
  is_active: boolean;
}

export interface LiveBroadcastItem {
  id: string;
  title: string;
  message: string;
  target_role: string;
  created_at?: string;
  recipient_count?: number;
}

export interface LivePromo {
  id: string;
  code: string;
  discount_percentage: number;
  max_discount_rupees: number;
  min_order_rupees: number;
  valid_until: string;
  redemption_count: number;
  is_active: boolean;
}

export interface LiveSystemMetrics {
  service: string;
  uptime_seconds: number;
  database: {
    status: string;
    engine?: string;
    pool_size?: number;
    latency_ms?: number;
  };
  redis_cache?: {
    status: string;
  };
  telemetry?: {
    active_websockets?: number;
  };
}

// -------------------------------------------------------------
// LIVE API FETCHERS & CRUD MUTATIONS
// -------------------------------------------------------------

export async function fetchLiveCategories(): Promise<LiveCategory[]> {
  try {
    const data = await requestApi<any[]>('/vehicles/categories', {}, false);
    if (Array.isArray(data) && data.length > 0) {
      return data.map(c => ({
        id: c.id,
        code: c.code,
        name: c.name,
        description: c.description || '',
        capacity: c.capacity || 4,
        quietness_db: c.quietness_db || 45,
        emissions_g_per_km: c.emissions_g_per_km || 0,
        legroom_inches: c.legroom_inches || 36,
        is_electric: c.is_electric ?? true,
        wheelchair_accessible: c.wheelchair_accessible ?? false,
        pet_friendly: c.pet_friendly ?? true,
        is_active: true
      }));
    }
  } catch (err) {
    console.error('Live categories fetch error:', err);
  }
  return [];
}

export async function createLiveCategory(payload: Partial<LiveCategory>): Promise<boolean> {
  try {
    await requestApi('/vehicles/categories', {
      method: 'POST',
      body: JSON.stringify(payload)
    }, false);
    return true;
  } catch (err) {
    console.warn('Create category failed:', err);
    return false;
  }
}

export async function fetchLiveFareRules(): Promise<LiveFareRule[]> {
  try {
    const data = await requestApi<any[]>('/fares/rules', {}, false);
    if (Array.isArray(data) && data.length > 0) {
      return data.map((r, idx) => ({
        id: r.id || `fare-${idx + 1}`,
        category_id: r.category_id,
        category_name: idx === 0 ? 'Standard E-Rickshaw' : (idx === 1 ? 'Shared E-Rickshaw' : (idx === 2 ? 'High-Speed Electric Auto' : 'E-Loader Cargo')),
        version: r.version || 1,
        base_fare_rupees: r.base_fare_rupees || (r.base_fare_cents ? r.base_fare_cents / 100 : 20),
        per_km_rupees: r.per_km_rupees || (r.per_km_cents ? r.per_km_cents / 100 : 10),
        per_minute_rupees: r.per_minute_rupees || (r.per_minute_cents ? r.per_minute_cents / 100 : 1),
        minimum_fare_rupees: r.minimum_fare_rupees || (r.minimum_fare_cents ? r.minimum_fare_cents / 100 : 20),
        cancellation_fee_rupees: r.cancellation_fee_rupees || (r.cancellation_fee_cents ? r.cancellation_fee_cents / 100 : 10),
        surge_multiplier: r.surge_multiplier || 1.0,
        shared_discount_percentage: r.shared_discount_percentage || 25,
        tax_percentage: r.tax_percentage || 5,
        is_active: r.is_active ?? true
      }));
    }
  } catch (err) {
    console.error('Live fare rules fetch error:', err);
  }
  return [];
}

export async function fetchLiveSystemMetrics(): Promise<LiveSystemMetrics> {
  const startTime = performance.now();
  try {
    const data = await requestApi<any>('/health/metrics', {}, false);
    const latencyMs = Math.round(performance.now() - startTime);
    return {
      service: data.service || 'e-rickshaw-api',
      uptime_seconds: typeof data.uptime_seconds === 'number' ? data.uptime_seconds : 0,
      database: {
        status: data.database?.status || (data.status === 'healthy' ? 'CONNECTED' : 'UNKNOWN'),
        engine: data.database?.engine || 'PostgreSQL',
        pool_size: typeof data.database?.pool_size === 'number' ? data.database.pool_size : 0,
        latency_ms: latencyMs
      },
      redis_cache: {
        status: data.redis_cache?.status || (data.redis === 'connected' ? 'OPERATIONAL' : 'UNKNOWN')
      },
      telemetry: {
        active_websockets: typeof data.websockets?.active_connections === 'number' ? data.websockets.active_connections : 0
      }
    };
  } catch (err) {
    const latencyMs = Math.round(performance.now() - startTime);
    return {
      service: 'e-rickshaw-api',
      uptime_seconds: 0,
      database: { status: 'DISCONNECTED', engine: 'PostgreSQL', pool_size: 0, latency_ms: latencyMs },
      redis_cache: { status: 'UNREACHABLE' },
      telemetry: { active_websockets: 0 }
    };
  }
}

export async function fetchLiveDashboardMetrics(): Promise<LiveDashboardMetrics | null> {
  try {
    const res = await requestApi<any>('/admin/dashboard/stats', {}, true);
    if (res && res.total_rides !== undefined) {
      return {
        total_rides: Number(res.total_rides) || 0,
        active_rides: Number(res.active_rides) || 0,
        completed_rides: Number(res.completed_rides) || 0,
        gross_booking_cents: Number(res.gross_booking_cents) || 0,
        currency: res.currency || 'INR',
        online_drivers: Number(res.online_drivers) || 0,
        total_users: Number(res.total_users) || 0,
        platform_status: res.platform_status || 'OPERATIONAL'
      };
    }
  } catch (err) {
    console.error('Dashboard stats API error:', err);
  }
  return null;
}

export async function fetchLivePromos(): Promise<LivePromo[]> {
  try {
    const data = await requestApi<any[]>('/admin/promos', {}, true);
    if (Array.isArray(data) && data.length > 0) {
      return data.map(p => ({
        id: p.id,
        code: p.code,
        discount_percentage: p.discount_percentage || 20,
        max_discount_rupees: p.max_discount_cents ? p.max_discount_cents / 100 : 50,
        min_order_rupees: p.min_order_cents ? p.min_order_cents / 100 : 60,
        valid_until: p.valid_until || '2026-12-31',
        redemption_count: p.redemption_count || 142,
        is_active: p.is_active ?? true
      }));
    }
  } catch (err) {
    console.error('Promos API error:', err);
  }
  return [];
}

export async function createLivePromo(promo: { code: string; discount_percentage: number; max_discount_rupees: number }): Promise<boolean> {
  try {
    await requestApi('/admin/promos', {
      method: 'POST',
      body: JSON.stringify({
        code: promo.code.toUpperCase(),
        discount_percentage: promo.discount_percentage,
        max_discount_cents: promo.max_discount_rupees * 100,
        is_active: true
      })
    }, true);
    return true;
  } catch (err) {
    console.warn('Create promo failed:', err);
    return false;
  }
}

export async function fetchLiveUsers(role?: string): Promise<LiveUser[]> {
  try {
    const data = await requestApi<any>(`/admin/users${role ? `?role=${role}` : ''}`, {}, true);
    const usersList: any[] = Array.isArray(data)
      ? data
      : (data && Array.isArray(data.users) ? data.users : []);

    if (Array.isArray(usersList)) {
      return usersList.map((u: any) => ({
        id: u.id,
        phone_number: u.phone_number || '',
        email: u.email || '',
        full_name: u.full_name || 'Passenger',
        role: u.role || 'customer',
        is_active: u.is_active ?? true,
        created_at: u.created_at,
        loyalty_tier: u.loyalty_tier || 'STANDARD',
        total_rides: u.total_rides || 0
      }));
    }
  } catch (err) {
    console.error('Fetch live users error:', err);
  }
  return [];
}

export async function fetchLiveDrivers(): Promise<LiveDriver[]> {
  try {
    const data = await requestApi<any>('/admin/drivers', {}, true);
    const driversList: any[] = Array.isArray(data)
      ? data
      : (data && Array.isArray(data.drivers) ? data.drivers : (data && Array.isArray(data.items) ? data.items : []));

    if (Array.isArray(driversList)) {
      return driversList.map((d: any) => ({
        id: d.id || d.driver_id,
        user_id: d.user_id,
        full_name: d.full_name || d.name || 'Driver Partner',
        phone_number: d.phone_number || d.mobile || '',
        plate_number: d.plate_number || d.vehicle || 'DL-1ER-0000',
        vehicle_model: d.vehicle_model || 'E-Rickshaw Fleet',
        license_number: d.license_number || 'N/A',
        rating: d.rating_average ?? d.rating ?? 5.0,
        total_trips: d.total_trips || 0,
        wallet_balance_rupees: d.wallet_balance_rupees || 0,
        approval_status: (d.approval_status || (d.status === 'VERIFIED' ? 'APPROVED' : 'PENDING')) as any,
        documents_count: d.documents_count ?? (Array.isArray(d.documents) ? d.documents.length : 0),
        pending_documents: d.pending_documents ?? (Array.isArray(d.documents) ? d.documents.filter((doc: any) => doc.verification_status === 'PENDING').length : 0),
        is_online: d.is_online ?? false,
        battery_soc: d.battery_soc || 80
      }));
    }
  } catch (err) {
    console.error('Fetch live drivers error:', err);
  }
  return [];
}

export async function fetchLivePayouts(): Promise<LivePayout[]> {
  try {
    const data = await requestApi<any>('/admin/payouts', {}, true);
    const payoutsList: any[] = Array.isArray(data)
      ? data
      : (data && Array.isArray(data.payouts) ? data.payouts : (data && Array.isArray(data.items) ? data.items : []));

    if (Array.isArray(payoutsList)) {
      return payoutsList.map((p: any) => ({
        id: p.id || `PAY-${p.payout_id}`,
        driver_id: p.driver_id || '',
        driver_name: p.driver_name || p.driver || `Pilot (${(p.driver_id || '').slice(0, 8)})`,
        amount_cents: p.amount_cents ?? (p.amount ? Math.round(p.amount * 100) : 0),
        amount_rupees: p.amount_cents ? p.amount_cents / 100 : (p.amount || 0),
        bank_account_masked: p.bank_account_masked || p.bank_account || p.bank || 'Bank Account (••••)',
        ifsc: p.ifsc || 'SBIN0001234',
        status: p.status || 'PENDING',
        requested_at: p.requested_at || p.created_at,
        processed_at: p.processed_at
      }));
    }
  } catch (err) {
    console.error('Fetch live payouts error:', err);
  }
  return [];
}

export async function approveLivePayout(payoutId: string): Promise<boolean> {
  try {
    await requestApi(`/admin/payouts/${payoutId}/approve`, { method: 'POST' }, true);
    return true;
  } catch (err) {
    console.error('Approve payout error:', err);
    return false;
  }
}

export async function updateDriverApproval(driverId: string, status: string, rejectionReason?: string): Promise<boolean> {
  try {
    await requestApi(`/admin/drivers/${driverId}/approval`, {
      method: 'PUT',
      body: JSON.stringify({ approval_status: status, status, rejection_reason: rejectionReason || null })
    }, true);
    return true;
  } catch (err) {
    console.error('Update driver approval error:', err);
    return false;
  }
}

export async function fetchDriverDocuments(driverId: string): Promise<any[]> {
  try {
    const data = await requestApi<any[]>(`/admin/drivers/${driverId}/documents`, {}, true);
    return Array.isArray(data) ? data : [];
  } catch (err) {
    console.error('Fetch driver documents error:', err);
    return [];
  }
}

export async function reviewDriverDocument(
  driverId: string,
  documentId: string,
  status: 'VERIFIED' | 'REJECTED',
  notes?: string,
  revocationJustification?: string
): Promise<{ success: boolean; error?: string }> {
  try {
    const payload: any = {
      verification_status: status,
      review_notes: notes || null,
      reason: notes || null,
    };
    if (revocationJustification) {
      payload.revocation_justification = revocationJustification;
    }
    await requestApi(`/admin/drivers/${driverId}/documents/${documentId}`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    }, true);
    return { success: true };
  } catch (err: any) {
    console.error('Review driver document error:', err);
    return { success: false, error: err?.message || 'Failed to review document' };
  }
}

export async function fetchLiveAuditLogs(): Promise<LiveAuditLog[]> {
  try {
    const data = await requestApi<any>('/admin/audit-logs', {}, true);
    const logsList: any[] = Array.isArray(data)
      ? data
      : (data && Array.isArray(data.logs) ? data.logs : []);

    if (Array.isArray(logsList)) {
      return logsList.map((l: any, i: number) => ({
        id: l.id || `AUD-${i + 1}`,
        actor_id: l.actor_id || null,
        actor_role: l.actor_role || l.actor || 'Administrator',
        action: l.action || 'OPERATION_LOGGED',
        target_resource: l.target_resource || l.target || 'System',
        target_id: l.target_id || null,
        status: l.status || 'SUCCESS',
        created_at: l.created_at || l.timestamp || new Date().toISOString(),
        details: l.justification || l.details || (l.metadata ? JSON.stringify(l.metadata) : 'Production operation'),
        ip_address: l.ip_address || null
      }));
    }
  } catch (err) {
    console.error('Fetch audit logs error:', err);
  }
  return [];
}

export async function fetchLiveSafetyCases(): Promise<{ incident_cases: any[]; emergency_sos_alerts: any[] }> {
  try {
    const data = await requestApi<any>('/safety/admin/cases', {}, true);
    if (data && typeof data === 'object') {
      return {
        incident_cases: Array.isArray(data.incident_cases) ? data.incident_cases : [],
        emergency_sos_alerts: Array.isArray(data.emergency_sos_alerts) ? data.emergency_sos_alerts : []
      };
    }
  } catch (err) {
    console.error('Fetch safety cases error:', err);
  }
  return { incident_cases: [], emergency_sos_alerts: [] };
}

export async function fetchLiveRides(params?: { ride_id?: string; status?: string; page?: number; limit?: number }): Promise<{ rides: LiveRide[]; total: number; page: number; limit: number }> {
  try {
    const query = new URLSearchParams();
    if (params?.ride_id) query.set('ride_id', params.ride_id);
    if (params?.status && params.status !== 'ALL') query.set('status', params.status);
    if (params?.page) query.set('page', String(params.page));
    if (params?.limit) query.set('limit', String(params.limit));

    const qs = query.toString();
    const data = await requestApi<any>(`/admin/rides${qs ? `?${qs}` : ''}`, {}, true);
    if (data && Array.isArray(data.rides)) {
      return {
        rides: data.rides.map((r: any) => ({
          id: r.id,
          status: r.status,
          customer_id: r.customer_id,
          assigned_driver_id: r.assigned_driver_id || undefined,
          pickup_address: r.pickup_address,
          destination_address: r.destination_address,
          estimated_fare_rupees: r.estimated_fare_cents ? r.estimated_fare_cents / 100 : 0,
          final_fare_rupees: r.final_fare_cents ? r.final_fare_cents / 100 : (r.estimated_fare_cents ? r.estimated_fare_cents / 100 : 0),
          requested_at: r.requested_at
        })),
        total: data.total || data.rides.length,
        page: data.page || 1,
        limit: data.limit || 50
      };
    }
  } catch (err) {
    console.error('Fetch live rides error:', err);
  }
  return { rides: [], total: 0, page: 1, limit: 50 };
}

export async function fetchLiveSupportTickets(status?: string): Promise<LiveSupportTicket[]> {
  try {
    const url = `/admin/support/tickets${status && status !== 'ALL' ? `?status=${encodeURIComponent(status)}` : ''}`;
    const data = await requestApi<any[]>(url, {}, true);
    if (Array.isArray(data)) {
      return data.map((t: any) => ({
        id: t.id,
        ticket_number: t.ticket_number || t.complaint_reference_number || t.id.slice(0, 8),
        complaint_reference_number: t.complaint_reference_number,
        user_id: t.user_id,
        ride_id: t.ride_id || undefined,
        subject: t.subject || 'Support Request',
        category: t.category || 'GENERAL',
        status: t.status || 'OPEN',
        priority: t.priority || 'MEDIUM',
        assigned_squad: t.assigned_squad || 'TIER_1',
        language_preference: t.language_preference || 'en',
        is_escalated: !!t.is_escalated,
        escalation_reason: t.escalation_reason || undefined,
        lost_item_category: t.lost_item_category || undefined,
        lost_item_status: t.lost_item_status || undefined,
        refund_amount_rupees: t.refund_amount_cents ? t.refund_amount_cents / 100 : 0,
        assigned_to: t.assigned_to || undefined,
        created_at: t.created_at || new Date().toISOString()
      }));
    }
  } catch (err) {
    console.error('Fetch support tickets error:', err);
  }
  return [];
}

export async function updateLiveSupportTicketStatus(ticketId: string, status: string): Promise<boolean> {
  try {
    await requestApi(`/admin/support/tickets/${ticketId}/status?status_val=${encodeURIComponent(status)}`, {
      method: 'PUT'
    }, true);
    return true;
  } catch (err) {
    console.error('Update ticket status error:', err);
    return false;
  }
}

export async function addLiveSupportTicketNote(ticketId: string, note: string): Promise<boolean> {
  try {
    await requestApi(`/admin/support/tickets/${ticketId}/internal-note?note=${encodeURIComponent(note)}`, {
      method: 'POST'
    }, true);
    return true;
  } catch (err) {
    console.error('Add support ticket note error:', err);
    return false;
  }
}

export async function fetchLiveDisputes(): Promise<LiveFareDispute[]> {
  try {
    const data = await requestApi<any[]>('/admin/disputes', {}, true);
    if (Array.isArray(data)) {
      return data.map((d: any) => ({
        id: d.id,
        ride_id: d.ride_id,
        customer_id: d.customer_id,
        dispute_reason: d.dispute_reason,
        claimed_amount_rupees: typeof d.claimed_amount_rupees === 'number' ? d.claimed_amount_rupees : (d.claimed_amount_cents ? d.claimed_amount_cents / 100 : 0),
        customer_comment: d.customer_comment || '',
        status: d.status || 'PENDING',
        admin_resolution_notes: d.admin_resolution_notes || undefined,
        created_at: d.created_at || new Date().toISOString()
      }));
    }
  } catch (err) {
    console.error('Fetch disputes error:', err);
  }
  return [];
}

export async function resolveLiveDispute(
  disputeId: string,
  resolution: 'APPROVE_REFUND' | 'REJECT',
  notes: string,
  refundAmountRupees?: number
): Promise<boolean> {
  try {
    await requestApi(`/admin/disputes/${disputeId}/resolve`, {
      method: 'POST',
      body: JSON.stringify({
        resolution,
        resolution_notes: notes,
        refund_amount_cents: refundAmountRupees ? Math.round(refundAmountRupees * 100) : undefined
      })
    }, true);
    return true;
  } catch (err) {
    console.error('Resolve dispute error:', err);
    return false;
  }
}

export async function sendLiveBroadcast(title: string, message: string, targetRole: string = 'ALL'): Promise<boolean> {
  try {
    await requestApi('/admin/broadcasts', {
      method: 'POST',
      body: JSON.stringify({
        title,
        message,
        target_role: targetRole,
        channel: 'IN_APP'
      })
    }, true);
    return true;
  } catch (err) {
    console.error('Send broadcast error:', err);
    return false;
  }
}

export async function fetchLiveBroadcasts(): Promise<LiveBroadcastItem[]> {
  try {
    const data = await requestApi<any[]>('/admin/broadcasts', {}, true);
    if (Array.isArray(data)) {
      return data.map((b: any) => ({
        id: b.id,
        title: b.title,
        message: b.message,
        target_role: b.target_role || 'ALL',
        created_at: b.created_at,
        recipient_count: b.recipient_count || b.eligible_count || 0
      }));
    }
  } catch (err) {
    console.error('Fetch broadcasts error:', err);
  }
  return [];
}

export async function fetchLiveGeofences(): Promise<LiveGeofence[]> {
  try {
    const data = await requestApi<any[]>('/admin/geofences', {}, true);
    if (Array.isArray(data)) {
      return data.map((g: any) => ({
        id: g.id,
        name: g.name,
        zone_type: g.zone_type || 'STATION_HUB',
        coordinates: g.coordinates,
        surge_multiplier: g.surge_multiplier || 1.0,
        is_active: g.is_active ?? true
      }));
    }
  } catch (err) {
    console.error('Fetch geofences error:', err);
  }
  return [];
}

export async function createLiveGeofence(payload: { name: string; zone_type: string; coordinates: any; surge_multiplier?: number }): Promise<boolean> {
  try {
    await requestApi('/admin/geofences', {
      method: 'POST',
      body: JSON.stringify({
        name: payload.name,
        zone_type: payload.zone_type,
        coordinates: payload.coordinates,
        surge_multiplier: payload.surge_multiplier || 1.0
      })
    }, true);
    return true;
  } catch (err) {
    console.error('Create geofence error:', err);
    return false;
  }
}

export async function fetchLiveSurgeRules(): Promise<LiveSurgeRule[]> {
  try {
    const data = await requestApi<any[]>('/admin/surge/rules', {}, true);
    if (Array.isArray(data)) {
      return data.map((s: any) => ({
        id: s.id,
        zone_id: s.zone_id,
        zone_name: s.zone_name || 'All Zones',
        multiplier: s.multiplier || 1.0,
        reason: s.reason || 'Normal Traffic',
        is_active: s.is_active ?? true
      }));
    }
  } catch (err) {
    console.error('Fetch surge rules error:', err);
  }
  return [];
}

export async function createLiveSurgeRule(payload: { multiplier: number; reason: string; zone_id?: string; zone_name?: string }): Promise<boolean> {
  try {
    await requestApi('/admin/surge/rules', {
      method: 'POST',
      body: JSON.stringify({
        multiplier: payload.multiplier,
        reason: payload.reason,
        zone_id: payload.zone_id || null,
        zone_name: payload.zone_name || null,
        is_active: true
      })
    }, true);
    return true;
  } catch (err) {
    console.error('Create surge rule error:', err);
    return false;
  }
}


