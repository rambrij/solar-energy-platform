import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api';

type Attempt = { countedCash: string; reason: string; requestId: string };
type Status = { state: string; expectedCash: number; countedCash: number; variance: number; varianceReason: string; reviewedBy?: string; reviewReason?: string };
type Props = { session: string; disabled: boolean; onStateChange: (state: string) => void; onPendingChange: (value: boolean) => void; onBusyChange: (value: boolean) => void };
export default function ShiftClose({ session, disabled, onStateChange, onPendingChange, onBusyChange }: Props) {
  const key = `pos-close-attempt-v1:${session}`;
  const [pending, setPending] = useState<Attempt | undefined>(() => { try { return JSON.parse(sessionStorage.getItem(key) ?? 'null') ?? undefined; } catch { return undefined; } });
  const [count, setCount] = useState(''), [reason, setReason] = useState('');
  const [status, setStatus] = useState<Status>(), [error, setError] = useState(''), [revision, setRevision] = useState(0);
  const requestVersion = useRef(0);
  useEffect(() => { onPendingChange(!!pending); }, [pending, onPendingChange]);
  useEffect(() => {
    let active = true;
    const version = requestVersion.current;
    api(`/sessions/${session}/close-status`).then(value => { if (active && version === requestVersion.current) { setStatus(value); onStateChange(value.state); } })
      .catch(() => { if (active && version === requestVersion.current) setError('Close status unavailable. Refresh after restoring connectivity.'); });
    return () => { active = false; };
  }, [session, revision, onStateChange]);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const attempt = pending ?? { countedCash: count, reason: reason.trim(), requestId: crypto.randomUUID() };
    try { sessionStorage.setItem(key, JSON.stringify(attempt)); } catch { setError('Cannot save retry details; no close request was sent.'); return; }
    requestVersion.current += 1;
    setPending(attempt); onPendingChange(true); onBusyChange(true); setError('');
    try {
      const value = await api(`/sessions/${session}/close`, attempt);
      setStatus(value); onStateChange(value.state); sessionStorage.removeItem(key); setPending(undefined); onPendingChange(false);
      setCount(''); setReason('');
    } catch (e) {
      if (!pending && e instanceof ApiError && [400, 403, 404, 409].includes(e.status)) {
        sessionStorage.removeItem(key); setPending(undefined); onPendingChange(false);
        setError('Close request rejected. A discrepancy requires a reason; check permissions and session status.');
      } else setError('Close outcome is uncertain. Retry the same count; do not start a new shift until resolved.');
    } finally { onBusyChange(false); }
  }
  return <section aria-label="Shift closing">
    <h2>Shift closing</h2>{error && <p role="alert">{error}</p>}
    {status?.state === 'CLOSING' && <p role="status">Awaiting a different supervisor's review. Expected: {Number(status.expectedCash).toFixed(2)} · Counted: {Number(status.countedCash).toFixed(2)} · Variance: {Number(status.variance).toFixed(2)} · Reason: {status.varianceReason}. The drawer is frozen.</p>}
    {status?.state === 'CLOSED' && <p role="status">Session closed.{status.reviewedBy ? ` Approved by ${status.reviewedBy}: ${status.reviewReason}` : ''}</p>}
    {status?.state === 'OPEN' && status.reviewedBy && <p role="status">Supervisor requested correction: {status.reviewReason}. Count the drawer again.</p>}
    {(status?.state === 'OPEN' || pending || !status) && <form onSubmit={submit}>
      <fieldset disabled={disabled || !!pending}>
        <label>Counted drawer cash <input type="number" min="0" step="0.01" required value={count} onChange={e => setCount(e.target.value)} /></label>
        <label>Variance reason (required for a discrepancy) <input maxLength={250} value={reason} onChange={e => setReason(e.target.value)} /></label>
      </fieldset>
      <button disabled={disabled}>{pending ? 'Retry same close request' : 'Reconcile and close session'}</button>
    </form>}
    <button disabled={disabled} onClick={() => { setError(''); setRevision(value => value + 1); }}>Refresh close status</button>
  </section>;
}
