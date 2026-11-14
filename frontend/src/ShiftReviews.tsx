import { useState } from 'react';
import { api, ApiError } from './api';
type Review = { sessionId: string; registerId: number; cashier: string; requestId: string; expectedCash: number; countedCash: number; variance: number; varianceReason: string };
type Attempt = { sessionId: string; action: string; requestId: string; reason: string };
export default function ShiftReviews() {
  const storageKey = 'pos-shift-review-attempt-v1';
  const [pending, setPending] = useState<Attempt | undefined>(() => { try { return JSON.parse(sessionStorage.getItem(storageKey) ?? 'null') ?? undefined; } catch { return undefined; } });
  const [rows, setRows] = useState<Review[]>([]), [reasons, setReasons] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  async function load() {
    setBusy(true); setError('');
    try { setRows(await api('/sessions/pending-variances')); } catch { setError('Could not load shift reviews. A supervisor grant in this branch is required.'); } finally { setBusy(false); }
  }
  async function decide(attempt: Attempt) {
    try { sessionStorage.setItem(storageKey, JSON.stringify(attempt)); } catch { setError('Cannot save review retry details; no decision was sent.'); return; }
    setPending(attempt); setBusy(true); setError(''); setNotice('');
    try {
      await api(`/sessions/${attempt.sessionId}/${attempt.action}`, { requestId: attempt.requestId, reason: attempt.reason });
      sessionStorage.removeItem(storageKey); setPending(undefined); setRows(rows => rows.filter(row => row.requestId !== attempt.requestId)); setNotice('Supervisor decision recorded. Refresh to view remaining requests.');
    } catch (e) {
      if (!pending && e instanceof ApiError && [400,403,404,409].includes(e.status)) {
        sessionStorage.removeItem(storageKey); setPending(undefined); setError('Review rejected. Reload requests and check your permission and review reason.');
      } else setError('Decision was not confirmed. Retry the same decision after checking access and connectivity; do not submit a different decision.');
    }
    finally { setBusy(false); }
  }
  return <details id="shift-reviews"><summary>Supervisor shift review</summary>
    <p>A different authorized supervisor in the same tenant and branch must review discrepancies.</p>
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    <button disabled={busy} onClick={load}>Load pending shift reviews</button>
    {pending && <button disabled={busy} onClick={() => decide(pending)}>Retry same review decision</button>}
    {rows.map(row => <section key={row.requestId} aria-label={`Review register ${row.registerId}`}>
      <h3>Register {row.registerId} · Cashier {row.cashier}</h3>
      <p>Expected {Number(row.expectedCash).toFixed(2)} · Counted {Number(row.countedCash).toFixed(2)} · Variance {Number(row.variance).toFixed(2)} · Cashier reason: {row.varianceReason}</p>
      <label>Supervisor reason for register {row.registerId} <input maxLength={250} disabled={busy || !!pending} value={reasons[row.requestId] ?? ''} onChange={e => setReasons({ ...reasons, [row.requestId]: e.target.value })} /></label>
      <button disabled={busy || !!pending || !reasons[row.requestId]?.trim()} onClick={() => decide({ sessionId: row.sessionId, requestId: row.requestId, action: 'approve-variance', reason: reasons[row.requestId].trim() })}>Approve variance for register {row.registerId}</button>
      <button disabled={busy || !!pending || !reasons[row.requestId]?.trim()} onClick={() => decide({ sessionId: row.sessionId, requestId: row.requestId, action: 'return-for-correction', reason: reasons[row.requestId].trim() })}>Return register {row.registerId} for correction</button>
    </section>)}
  </details>;
}
