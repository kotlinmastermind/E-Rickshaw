import React, { useCallback, useEffect, useState } from 'react';
import { fetchWithAuth } from '../services/api';
import './DriverOnboardingTicketDesk.css';

type OnboardingTicket = {
  ticket_id: string;
  driver_name: string;
  phone_masked: string;
  subject: string;
  message: string;
  status: 'OPEN' | 'IN_REVIEW' | 'RESOLVED';
  admin_response?: string | null;
  created_at: string;
};

export const DriverOnboardingTicketDesk: React.FC = () => {
  const [tickets, setTickets] = useState<OnboardingTicket[]>([]);
  const [responses, setResponses] = useState<Record<string, string>>({});
  const [statuses, setStatuses] = useState<Record<string, OnboardingTicket['status']>>({});
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchWithAuth('/driver-onboarding/admin/tickets');
      if (!Array.isArray(result)) throw new Error('Support service is unavailable or this admin session is not authorized.');
      setTickets(result);
      setStatuses(Object.fromEntries(result.map((ticket: OnboardingTicket) => [ticket.ticket_id, ticket.status])));
      setResponses(Object.fromEntries(result.map((ticket: OnboardingTicket) => [ticket.ticket_id, ticket.admin_response || ''])));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load driver onboarding tickets.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const save = async (ticket: OnboardingTicket) => {
    const response = responses[ticket.ticket_id]?.trim() || '';
    if (response.length < 5) { setError('Write a response of at least five characters before updating a ticket.'); return; }
    setBusyId(ticket.ticket_id);
    setError(null);
    setNotice(null);
    try {
      const result = await fetchWithAuth(`/driver-onboarding/admin/tickets/${ticket.ticket_id}`, {
        method: 'PATCH',
        body: JSON.stringify({ status: statuses[ticket.ticket_id], admin_response: response }),
      });
      if (!result?.ticket_id) throw new Error('Could not update this ticket. Check admin authorization and retry.');
      setNotice(`Ticket ${ticket.ticket_id.slice(0, 8)} updated.`);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not update this ticket.');
    } finally {
      setBusyId(null);
    }
  };

  return <section className="onboarding-ticket-desk">
    <header className="onboarding-ticket-header"><div><h1>Driver Onboarding Support</h1><p>Review driver requests and send a response through the authenticated operations console.</p></div><button className="btn-secondary" onClick={() => void load()} disabled={loading}>Refresh</button></header>
    {error && <div role="alert" className="onboarding-ticket-feedback">{error}</div>}
    {notice && <div role="status" className="onboarding-ticket-feedback">{notice}</div>}
    {loading ? <div className="onboarding-ticket-empty" role="status">Loading onboarding tickets…</div> : tickets.length === 0 ? <div className="onboarding-ticket-empty">No driver onboarding support tickets yet.</div> : <div className="onboarding-ticket-grid">
      {tickets.map(ticket => <article key={ticket.ticket_id} className="onboarding-ticket-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' }}>
          <div><h2 style={{ marginTop: 0 }}>{ticket.subject}</h2><p>{ticket.driver_name} · {ticket.phone_masked}</p><small>{new Date(ticket.created_at).toLocaleString()}</small></div>
          <strong className="onboarding-ticket-status">{ticket.status}</strong>
        </div>
        <p style={{ whiteSpace: 'pre-wrap' }}>{ticket.message}</p>
        <label htmlFor={`status-${ticket.ticket_id}`}>Status</label>
        <select id={`status-${ticket.ticket_id}`} value={statuses[ticket.ticket_id] || ticket.status} onChange={e => setStatuses(current => ({ ...current, [ticket.ticket_id]: e.target.value as OnboardingTicket['status'] }))}>
          <option value="OPEN">Open</option><option value="IN_REVIEW">In review</option><option value="RESOLVED">Resolved</option>
        </select>
        <label htmlFor={`response-${ticket.ticket_id}`}>Driver response</label>
        <textarea id={`response-${ticket.ticket_id}`} rows={3} maxLength={2000} value={responses[ticket.ticket_id] || ''} onChange={e => setResponses(current => ({ ...current, [ticket.ticket_id]: e.target.value }))} placeholder="Write an update for the driver" />
        <button className="btn-primary" disabled={busyId === ticket.ticket_id} onClick={() => void save(ticket)}>{busyId === ticket.ticket_id ? 'Saving…' : 'Save response'}</button>
      </article>)}
    </div>}
  </section>;
};
