import React, { useState, useEffect } from 'react';
import { fetchWithAuth } from '../services/api';

interface RideTimelineEvent {
  time: string;
  actor: string;
  event: string;
  notes: string;
}

interface RideStopItem {
  id: string;
  sequence_order: number;
  address: string;
  dwell_time_minutes: number;
  passenger_instructions?: string;
  entrance_landmark?: string;
}

interface AdminRide {
  id: string;
  guest: string;
  driver: string;
  vehicle: string;
  pickup: string;
  destination: string;
  status: string;
  fare: string;
  booking_for_self?: boolean;
  passenger_name?: string;
  passenger_phone?: string;
  passenger_relationship?: string;
  booking_mode?: string;
  hotel_room_number?: string;
  hotel_guest_name?: string;
  business_cost_center?: string;
  business_project_code?: string;
  business_tax_id?: string;
  flight_number?: string;
  terminal_name?: string;
  airline_code?: string;
  flight_delay_minutes?: number;
  pickup_notes?: string;
  destination_notes?: string;
  entrance_guidance?: string;
  landmark_guidance?: string;
  is_return_trip?: boolean;
  is_round_trip?: boolean;
  stops?: RideStopItem[];
  route_preference?: string;
  route_deviation_detected?: boolean;
  route_deviation_reason?: string;
  driver_arrival_photo_url?: string;
  vehicle_model_override?: string;
  vehicle_plate_override?: string;
  masked_virtual_number?: string;
  masked_virtual_ext?: string;
  timeline: RideTimelineEvent[];
}

export const RideManagement: React.FC = () => {
  const [rides, setRides] = useState<AdminRide[]>([]);
  const [selectedRide, setSelectedRide] = useState<AdminRide | null>(null);
  const [statusFilter, setStatusFilter] = useState("all");
  const [isLoading, setIsLoading] = useState(false);

  const loadRides = async () => {
    setIsLoading(true);
    const query = statusFilter !== "all" ? `/admin/rides?status=${statusFilter}&limit=50` : `/admin/rides?limit=50`;
    const data = await fetchWithAuth(query);
    if (data && data.rides) {
      const mapped: AdminRide[] = data.rides.map((r: any) => ({
        id: r.id,
        guest: r.guest_name || `Guest (${r.customer_id?.substring(0, 8)})`,
        driver: r.driver_name || (r.assigned_driver_id ? `Chauffeur (${r.assigned_driver_id?.substring(0, 8)})` : 'Unassigned'),
        vehicle: r.vehicle_model_override || 'Executive Sedan',
        pickup: r.pickup_address,
        destination: r.destination_address,
        status: r.status,
        fare: `₹${(((r.final_fare_cents || r.estimated_fare_cents || 0) / 100)).toFixed(2)}`,
        booking_for_self: r.booking_for_self,
        passenger_name: r.passenger_name,
        passenger_phone: r.passenger_phone,
        passenger_relationship: r.passenger_relationship,
        booking_mode: r.booking_mode,
        hotel_room_number: r.hotel_room_number,
        hotel_guest_name: r.hotel_guest_name,
        business_cost_center: r.business_cost_center,
        business_project_code: r.business_project_code,
        business_tax_id: r.business_tax_id,
        flight_number: r.flight_number,
        terminal_name: r.terminal_name,
        airline_code: r.airline_code,
        flight_delay_minutes: r.flight_delay_minutes,
        pickup_notes: r.pickup_notes,
        destination_notes: r.destination_notes,
        entrance_guidance: r.entrance_guidance,
        landmark_guidance: r.landmark_guidance,
        is_return_trip: r.is_return_trip,
        is_round_trip: r.is_round_trip,
        stops: r.stops || [],
        route_preference: r.route_preference || 'FASTEST',
        route_deviation_detected: r.route_deviation_detected || false,
        route_deviation_reason: r.route_deviation_reason,
        driver_arrival_photo_url: r.driver_arrival_photo_url,
        vehicle_model_override: r.vehicle_model_override,
        vehicle_plate_override: r.vehicle_plate_override,
        masked_virtual_number: r.masked_virtual_number,
        masked_virtual_ext: r.masked_virtual_ext,
        timeline: r.timeline || []
      }));
      setRides(mapped);
    }
    setIsLoading(false);
  };

  useEffect(() => {
    loadRides();
  }, [statusFilter]);

  const handleInspectTimeline = async (ride: AdminRide) => {
    setSelectedRide(ride);
    try {
      const data = await fetchWithAuth(`/api/v1/rides/${ride.id}`);
      if (data) {
        const events = (data.timeline || []).map((e: any) => ({
          time: new Date(e.created_at).toLocaleTimeString('en-US', { hour12: false }) + ' UTC',
          actor: e.trigger_actor,
          event: e.new_status,
          notes: e.notes || ''
        }));
        setSelectedRide(prev => prev ? {
          ...prev,
          booking_for_self: data.booking_for_self,
          passenger_name: data.passenger_name,
          passenger_phone: data.passenger_phone,
          passenger_relationship: data.passenger_relationship,
          booking_mode: data.booking_mode,
          hotel_room_number: data.hotel_room_number,
          hotel_guest_name: data.hotel_guest_name,
          business_cost_center: data.business_cost_center,
          business_project_code: data.business_project_code,
          business_tax_id: data.business_tax_id,
          flight_number: data.flight_number,
          terminal_name: data.terminal_name,
          airline_code: data.airline_code,
          flight_delay_minutes: data.flight_delay_minutes,
          pickup_notes: data.pickup_notes,
          destination_notes: data.destination_notes,
          entrance_guidance: data.entrance_guidance,
          landmark_guidance: data.landmark_guidance,
          is_return_trip: data.is_return_trip,
          is_round_trip: data.is_round_trip,
          stops: data.stops || [],
          timeline: events.length > 0 ? events : prev.timeline
        } : null);
      }
    } catch (_e) {
      // Keep existing timeline
    }
  };

  const filtered = rides;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Ride Lifecycle Audit & Exception Inspection</h1>
          <p className="page-description">Examine journey trajectories, lifecycle transitions, multi-stop waypoints, and luxury profiles.</p>
        </div>
      </div>

      <div className="table-card">
        <div className="table-toolbar">
          <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
            <select
              className="search-input"
              style={{ width: 160 }}
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value)}
            >
              <option value="all">All States</option>
              <option value="REQUESTED">Requested</option>
              <option value="DRIVER_ASSIGNED">Assigned</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </div>
          <span className="badge badge-gold">{filtered.length} Recorded Journeys</span>
        </div>

        <table className="luxury-table">
          <thead>
            <tr>
              <th>Ride ID</th>
              <th>Distinguished Guest</th>
              <th>Assigned Chauffeur</th>
              <th>Route & Stops</th>
              <th>Lifecycle State</th>
              <th>Contract Fare</th>
              <th>Audit Trail</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={7} style={{ textAlign: 'center', padding: '32px', color: 'var(--gold-primary)' }}>
                  Retrieving journeys from telemetry database...
                </td>
              </tr>
            ) : filtered.length === 0 ? (
              <tr>
                <td colSpan={7} style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>
                  No recorded journeys for selected state.
                </td>
              </tr>
            ) : (
              filtered.map(ride => (
                <tr key={ride.id}>
                  <td style={{ fontFamily: 'var(--font-mono)' }}>{ride.id.slice(0, 8)}...</td>
                  <td style={{ fontWeight: 600 }}>
                    {ride.guest}
                    {ride.passenger_name && (
                      <span style={{ display: 'block', fontSize: '0.75rem', color: 'var(--gold-primary)' }}>
                        Passenger: {ride.passenger_name}
                      </span>
                    )}
                  </td>
                  <td>{ride.driver} ({ride.vehicle})</td>
                  <td style={{ maxWidth: 260 }}>
                    <div style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {ride.pickup} → {ride.destination}
                    </div>
                    {ride.stops && ride.stops.length > 0 && (
                      <span className="badge badge-amber" style={{ fontSize: '0.7rem', marginTop: 2 }}>
                        +{ride.stops.length} Waypoints
                      </span>
                    )}
                    {ride.route_deviation_detected && (
                      <span className="badge badge-crimson" style={{ fontSize: '0.7rem', marginLeft: 4 }}>
                        ⚠️ DEVIATION
                      </span>
                    )}
                    {ride.flight_number && (
                      <span className="badge badge-gold" style={{ fontSize: '0.7rem', marginLeft: 4 }}>
                        ✈ {ride.flight_number}
                      </span>
                    )}
                  </td>
                  <td>
                    <span className={`badge ${ride.status === 'COMPLETED' ? 'badge-emerald' : (ride.status === 'IN_PROGRESS' ? 'badge-amber' : 'badge-crimson')}`}>
                      {ride.status}
                    </span>
                  </td>
                  <td style={{ fontWeight: 600 }}>{ride.fare}</td>
                  <td>
                    <button className="btn-secondary" onClick={() => handleInspectTimeline(ride)}>
                      Inspect Details
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {selectedRide && (
        <div className="modal-overlay" onClick={() => setSelectedRide(null)}>
          <div className="modal-content" style={{ maxWidth: 650, maxHeight: '85vh', overflowY: 'auto' }} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h2 style={{ fontFamily: 'var(--font-display)', color: 'var(--gold-primary)', margin: 0, fontSize: '1.25rem' }}>
                Journey Inspection: {selectedRide.id}
              </h2>
              <span className={`badge ${selectedRide.status === 'COMPLETED' ? 'badge-emerald' : 'badge-amber'}`}>
                {selectedRide.status}
              </span>
            </div>

            {/* Part 2 Attributes Overview */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
              {/* Passenger Designation */}
              <div style={{ background: 'var(--bg-input, #16161E)', padding: 12, borderRadius: 8, border: '1px solid var(--border-color, #272732)' }}>
                <div style={{ fontSize: '0.75rem', color: 'var(--gold-primary)', fontWeight: 600 }}>PASSENGER DESIGNATION</div>
                <div style={{ fontSize: '0.9rem', fontWeight: 600, marginTop: 4 }}>
                  {selectedRide.booking_for_self === false && selectedRide.passenger_name
                    ? `${selectedRide.passenger_name} (${selectedRide.passenger_relationship || 'Guest'})`
                    : 'Distinguished Account Holder'}
                </div>
                {selectedRide.passenger_phone && (
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                    Phone: {selectedRide.passenger_phone}
                  </div>
                )}
              </div>

              {/* Booking Mode */}
              <div style={{ background: 'var(--bg-input, #16161E)', padding: 12, borderRadius: 8, border: '1px solid var(--border-color, #272732)' }}>
                <div style={{ fontSize: '0.75rem', color: 'var(--gold-primary)', fontWeight: 600 }}>BOOKING MODE & PROFILE</div>
                <div style={{ fontSize: '0.9rem', fontWeight: 600, marginTop: 4 }}>
                  {selectedRide.booking_mode === 'HOTEL_CONCIERGE'
                    ? `Hotel Concierge (Suite ${selectedRide.hotel_room_number || 'N/A'})`
                    : (selectedRide.booking_mode === 'BUSINESS' ? `Corporate (${selectedRide.business_cost_center || 'CC-STD'})` : 'Standard Luxury Ride')}
                </div>
                {selectedRide.hotel_guest_name && (
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                    VIP Guest: {selectedRide.hotel_guest_name}
                  </div>
                )}
                {selectedRide.business_tax_id && (
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                    GSTIN: {selectedRide.business_tax_id}
                  </div>
                )}
              </div>
            </div>

            {/* Flight Telemetry & Guidance */}
            {(selectedRide.flight_number || selectedRide.pickup_notes || selectedRide.destination_notes) && (
              <div style={{ background: 'var(--bg-input, #16161E)', padding: 12, borderRadius: 8, border: '1px solid var(--border-color, #272732)', marginBottom: 16 }}>
                <div style={{ fontSize: '0.75rem', color: 'var(--gold-primary)', fontWeight: 600 }}>CHAUFFEUR GUIDANCE & FLIGHT TELEMETRY</div>
                {selectedRide.flight_number && (
                  <div style={{ fontSize: '0.85rem', marginTop: 4 }}>
                    ✈ Flight: <strong>{selectedRide.flight_number}</strong> • Terminal: {selectedRide.terminal_name || 'T2 VIP'}
                    {selectedRide.flight_delay_minutes ? (
                      <span style={{ color: 'var(--gold-primary)', marginLeft: 8 }}>
                        (+{selectedRide.flight_delay_minutes}m delay sync)
                      </span>
                    ) : null}
                  </div>
                )}
                {selectedRide.pickup_notes && (
                  <div style={{ fontSize: '0.82rem', marginTop: 4, color: 'var(--text-secondary)' }}>
                    Pickup Guidance: {selectedRide.pickup_notes}
                  </div>
                )}
                {selectedRide.destination_notes && (
                  <div style={{ fontSize: '0.82rem', marginTop: 2, color: 'var(--text-secondary)' }}>
                    Drop Guidance: {selectedRide.destination_notes}
                  </div>
                )}
              </div>
            )}

            {/* Intermediate Stops */}
            {selectedRide.stops && selectedRide.stops.length > 0 && (
              <div style={{ background: 'var(--bg-input, #16161E)', padding: 12, borderRadius: 8, border: '1px solid var(--border-color, #272732)', marginBottom: 16 }}>
                <div style={{ fontSize: '0.75rem', color: 'var(--gold-primary)', fontWeight: 600, marginBottom: 8 }}>
                  INTERMEDIATE WAYPOINTS & DWELL PERIODS ({selectedRide.stops.length})
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {selectedRide.stops.map((stp, idx) => (
                    <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.82rem' }}>
                      <div>
                        <span className="badge badge-gold" style={{ marginRight: 6, fontSize: '0.7rem' }}>Stop {stp.sequence_order || idx + 1}</span>
                        {stp.address}
                        {stp.passenger_instructions && (
                          <span style={{ color: 'var(--text-muted)', display: 'block', fontSize: '0.75rem' }}>
                            Instructions: {stp.passenger_instructions}
                          </span>
                        )}
                      </div>
                      <span style={{ color: 'var(--gold-primary)', fontWeight: 600 }}>
                        {stp.dwell_time_minutes}m dwell
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Part 5: Route Governance, Telemetry & Masked Dialing (Features 111, 115, 116, 118, 119) */}
            <div style={{ background: 'var(--bg-input, #16161E)', padding: 12, borderRadius: 8, border: '1px solid var(--border-color, #272732)', marginBottom: 16 }}>
              <div style={{ fontSize: '0.75rem', color: 'var(--gold-primary)', fontWeight: 600, marginBottom: 6 }}>
                PART 5: CORRIDOR GOVERNANCE, TELEMETRY & MASKED PROXY
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, fontSize: '0.82rem' }}>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Route Preference: </span>
                  <span className="badge badge-gold">{selectedRide.route_preference || 'FASTEST'}</span>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Corridor Integrity: </span>
                  {selectedRide.route_deviation_detected ? (
                    <span className="badge badge-crimson">⚠️ DEVIATION: {selectedRide.route_deviation_reason || 'Detour detected'}</span>
                  ) : (
                    <span className="badge badge-emerald">✓ Locked Corridor</span>
                  )}
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Vehicle & Plate: </span>
                  <strong>{selectedRide.vehicle_model_override || 'Mercedes-Benz E-Class'} ({selectedRide.vehicle_plate_override || 'MH 01 DX 4921'})</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)' }}>Masked Switchboard: </span>
                  <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--gold-primary)' }}>
                    {selectedRide.masked_virtual_number || '+91 22 6900 4820'} ext #{selectedRide.masked_virtual_ext || '4921'}
                  </span>
                </div>
              </div>

              {selectedRide.driver_arrival_photo_url && (
                <div style={{ marginTop: 10, paddingTop: 8, borderTop: '1px solid var(--border-color, #272732)' }}>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: 4 }}>
                    CHAUFFEUR PORTICO STAGING VERIFICATION PHOTO (Feature 115)
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <img
                      src={selectedRide.driver_arrival_photo_url}
                      alt="Arrival Staging"
                      style={{ width: 120, height: 70, objectFit: 'cover', borderRadius: 4, border: '1px solid var(--gold-primary)' }}
                    />
                    <span className="badge badge-emerald">Verified Portico Arrival</span>
                  </div>
                </div>
              )}
            </div>

            {/* Lifecycle Timeline */}
            <h3 style={{ fontSize: '0.85rem', color: 'var(--gold-primary)', marginBottom: 8 }}>
              LIFECYCLE STATE TRANSITIONS (POSTGRESQL AUDIT)
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 20 }}>
              {selectedRide.timeline.length === 0 ? (
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>No state events recorded yet.</div>
              ) : (
                selectedRide.timeline.map((item, idx) => (
                  <div
                    key={idx}
                    style={{
                      borderLeft: '2px solid var(--gold-primary)',
                      paddingLeft: 12,
                      position: 'relative'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span className="badge badge-gold">{item.event}</span>
                      <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                        {item.time}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: 2 }}>
                      Actor: <strong>{item.actor}</strong> • {item.notes}
                    </div>
                  </div>
                ))
              )}
            </div>

            <button className="btn-gold" style={{ width: '100%' }} onClick={() => setSelectedRide(null)}>
              Close Audit Inspection
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
