/**
 * Centralized, Standardized API Types & Contracts for Admin Web Console.
 * Directly mirrors backend Pydantic schemas adhering strictly to DRY rules.
 */

export interface ApiResponse<T = any> {
  success: boolean;
  message?: string;
  data?: T;
  error?: string;
}

export interface PaginationMeta {
  total_count: number;
  page: number;
  page_size: number;
  total_pages: number;
}

export interface PaginatedResponse<T> {
  items: T[];
  pagination: PaginationMeta;
}

export interface ApiErrorResponse {
  code: string;
  message: string;
  field?: string;
  correlation_id?: string;
  status_code?: number;
}

// User & Identity
export type UserRole = "customer" | "driver" | "admin" | "superadmin";

export interface User {
  id: string;
  phone_number: string;
  email?: string | null;
  full_name?: string | null;
  role: UserRole;
  is_active: boolean;
  tier?: string;
  created_at: string;
  updated_at?: string;
}

export interface CustomerProfile {
  id: string;
  user_id: string;
  tier: string;
  loyalty_points_balance: number;
  quiet_ride: boolean;
  preferred_music?: string;
  preferred_driver_language?: string;
  created_at: string;
}

export interface DriverProfile {
  id: string;
  user_id: string;
  license_number: string;
  license_verified: boolean;
  background_check_status: string;
  academy_certified: boolean;
  service_etiquette_level: string;
  rating_avg: number;
  total_trips: number;
  created_at: string;
}

// Vehicles & Fleet
export interface VehicleCategory {
  id: string;
  code: string;
  name: string;
  description?: string;
  icon_url?: string;
  capacity: number;
  sort_order: number;
  is_electric: boolean;
  emissions_g_per_km: number;
  legroom_inches: number;
  quietness_db: number;
  wheelchair_accessible: boolean;
  pet_friendly: boolean;
}

export interface Vehicle {
  id: string;
  category_id: string;
  driver_id?: string | null;
  make: string;
  model: string;
  license_plate: string;
  color: string;
  year: number;
  is_active: boolean;
  battery_percentage?: number;
  created_at: string;
}

// Rides & State Machine
export type RideStatus =
  | "REQUESTED"
  | "DISPATCHING"
  | "ASSIGNED"
  | "CHAUFFEUR_EN_ROUTE"
  | "ARRIVED_AT_PICKUP"
  | "CHAUFFEURING"
  | "COMPLETED"
  | "CANCELLED";

export interface RideStop {
  id: string;
  sequence_order: number;
  location_name: string;
  latitude: number;
  longitude: number;
  dwell_time_minutes: number;
}

export interface Ride {
  id: string;
  customer_id: string;
  driver_id?: string | null;
  category_id: string;
  status: RideStatus;
  pickup_address: string;
  pickup_latitude: number;
  pickup_longitude: number;
  destination_address: string;
  destination_latitude: number;
  destination_longitude: number;
  distance_km: number;
  fare_amount_rupees: number;
  fare_amount_cents: number;
  tip_cents?: number;
  sos_triggered: boolean;
  created_at: string;
  stops?: RideStop[];
}

// Fares & Pricing
export interface FareRule {
  id: string;
  category_id: string;
  city: string;
  base_fare_rupees: number;
  per_km_rate_rupees: number;
  per_minute_rate_rupees: number;
  minimum_fare_rupees: number;
  surge_multiplier: number;
  is_active: boolean;
}

export interface FareEstimate {
  estimate_id: string;
  category_id: string;
  distance_km: number;
  estimated_duration_minutes: number;
  estimated_fare_rupees: number;
  estimated_fare_cents: number;
  currency: string;
  route_polyline?: string;
  confidence_level: string;
}

// Support & Concierge
export type TicketStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";
export type TicketPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export interface SupportTicket {
  id: string;
  user_id: string;
  ride_id?: string | null;
  subject: string;
  description: string;
  category: string;
  priority: TicketPriority;
  status: TicketStatus;
  complaint_reference_number?: string;
  csat_score?: number;
  created_at: string;
  resolved_at?: string | null;
}

// Audit & System Health
export interface SystemHealthCheck {
  status: "READY" | "LIVE" | "UNHEALTHY";
  checks: {
    database: { status: string; latency_ms: number };
    redis_cache: { status: string; backend: string; latency_ms: number };
  };
  timestamp: string;
}

export interface AuditLogEntry {
  id: string;
  admin_id: string;
  action: string;
  target_resource: string;
  target_id?: string;
  ip_address?: string;
  details_json?: string;
  created_at: string;
}

