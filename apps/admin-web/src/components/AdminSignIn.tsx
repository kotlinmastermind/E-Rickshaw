import React, { FormEvent, useState } from 'react';
import { KeyRound, ShieldCheck } from 'lucide-react';
import { requestApi } from '../services/api';

type AuthResult = { access_token?: string; refresh_token?: string; mfa_required?: boolean; mfa_setup_required?: boolean; mfa_token?: string; setup_token?: string; secret?: string; totp_uri?: string; user?: { role?: string; mfa_enabled?: boolean } };

export const AdminSignIn: React.FC<{ onAuthenticated: (user: any) => void }> = ({ onAuthenticated }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [challenge, setChallenge] = useState('');
  const [setup, setSetup] = useState<AuthResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  const accept = (result: AuthResult) => {
    if (!result.access_token || result.user?.role !== 'admin' || result.user?.mfa_enabled !== true) {
      throw new Error('An MFA-verified administrator session was not returned.');
    }
    localStorage.setItem('admin_token', result.access_token);
    if (result.refresh_token) localStorage.setItem('admin_refresh_token', result.refresh_token);
    onAuthenticated({ role: 'admin', mfa_enabled: true, full_name: 'Administrator', email });
  };

  const signIn = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(''); setInfo('');
    try {
      const result = await requestApi<AuthResult>('/auth/admin/password/login', { method: 'POST', body: JSON.stringify({ email: email.trim(), password }) }, false);
      if (result.mfa_required && result.mfa_token) { setChallenge(result.mfa_token); setInfo('Enter the current code from your authenticator app.'); }
      else if (result.mfa_setup_required) setInfo('Administrator MFA enrollment is required. Set it up below before using the console.');
      else accept(result);
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Sign-in failed.'); }
    finally { setBusy(false); }
  };

  const beginSetup = async () => {
    setBusy(true); setError('');
    try { setSetup(await requestApi<AuthResult>('/auth/admin/mfa/setup', { method: 'POST', body: JSON.stringify({ email: email.trim(), password }) }, false)); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'MFA setup could not begin.'); }
    finally { setBusy(false); }
  };

  const verifyCode = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const result = setup
        ? await requestApi<AuthResult>('/auth/admin/mfa/enable', { method: 'POST', body: JSON.stringify({ setup_token: setup.setup_token, code }) }, false)
        : await requestApi<AuthResult>('/auth/mfa/challenge-verify', { method: 'POST', body: JSON.stringify({ mfa_token: challenge, code }) }, false);
      accept(result);
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Authenticator verification failed.'); }
    finally { setBusy(false); }
  };

  return <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24, background: 'radial-gradient(circle at 50% 0%, #28251e, #0a0a0e 62%)', color: '#f8f5ee' }}>
    <section style={{ width: 'min(100%, 460px)', padding: 34, borderRadius: 22, background: '#15141a', border: '1px solid rgba(212,175,55,.32)', boxShadow: '0 24px 90px #0008' }}>
      <div style={{ color: '#d4af37', fontFamily: 'Georgia,serif', letterSpacing: '.18em', fontSize: 12 }}>GRAND CHAUFFEUR</div>
      <h1 style={{ fontFamily: 'Georgia,serif', fontWeight: 500, fontSize: 30, margin: '12px 0 6px' }}>Executive access</h1>
      <p style={{ color: '#aca69c', lineHeight: 1.6, marginBottom: 24 }}>Sign in with an administrator account and verify with your authenticator. Customer and driver accounts cannot enter this console.</p>
      {!challenge && !setup && <form onSubmit={signIn} style={{ display: 'grid', gap: 14 }}>
        <label style={labelStyle}>Administrator email<input autoComplete="username" type="email" required value={email} onChange={e => setEmail(e.target.value)} style={inputStyle} /></label>
        <label style={labelStyle}>Password<input autoComplete="current-password" type="password" required value={password} onChange={e => setPassword(e.target.value)} style={inputStyle} /></label>
        <button disabled={busy} style={goldButton}>{busy ? 'Verifying credentials…' : 'Continue securely'}</button>
      </form>}
      {info && <div role="status" style={noticeStyle}>{info}</div>}
      {info.includes('enrollment') && !setup && <button disabled={busy || !email || !password} onClick={() => void beginSetup()} style={goldButton}><KeyRound size={16} /> Begin authenticator setup</button>}
      {setup && <div style={{ ...noticeStyle, marginTop: 16 }}><strong>Configure your authenticator</strong><p style={{ margin: '8px 0' }}>Add the account using this setup URI or enter the secret manually. The console does not send this secret to a QR provider.</p><code style={{ display: 'block', overflowWrap: 'anywhere', fontSize: 11, color: '#e5d4a1' }}>{setup.secret}</code><details style={{ marginTop: 10 }}><summary>Manual provisioning URI</summary><code style={{ display: 'block', overflowWrap: 'anywhere', marginTop: 8, fontSize: 10 }}>{setup.totp_uri}</code></details></div>}
      {(challenge || setup) && <form onSubmit={verifyCode} style={{ display: 'grid', gap: 14, marginTop: 18 }}><label style={labelStyle}>6-digit authenticator code<input autoComplete="one-time-code" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} required value={code} onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))} style={inputStyle} /></label><button disabled={busy || code.length !== 6} style={goldButton}><ShieldCheck size={16} /> {busy ? 'Verifying…' : setup ? 'Enable MFA and enter console' : 'Verify and sign in'}</button></form>}
      {error && <div role="alert" style={{ ...noticeStyle, borderColor: '#a65252', color: '#ffb7ae' }}>{error}</div>}
      <p style={{ color: '#77736c', fontSize: 11, lineHeight: 1.5, marginTop: 22 }}>If the API or MFA configuration is unavailable, access stays closed. Ask an authorized administrator to provision your account.</p>
    </section>
  </main>;
};

const labelStyle: React.CSSProperties = { display: 'grid', gap: 7, color: '#c8c1b6', fontSize: 13 };
const inputStyle: React.CSSProperties = { width: '100%', padding: '12px 13px', borderRadius: 9, border: '1px solid #3a352c', background: '#0e0d11', color: '#f8f5ee', outlineColor: '#d4af37' };
const goldButton: React.CSSProperties = { display: 'inline-flex', justifyContent: 'center', alignItems: 'center', gap: 8, width: '100%', padding: '13px 16px', borderRadius: 10, border: 0, background: '#d4af37', color: '#15130e', fontWeight: 700, cursor: 'pointer', marginTop: 6 };
const noticeStyle: React.CSSProperties = { background: '#211e18', border: '1px solid rgba(212,175,55,.28)', borderRadius: 10, padding: 13, color: '#cfc8bc', fontSize: 13, lineHeight: 1.5, marginTop: 14 };
