import { useEffect, useState } from 'react';
import { api, ApiError } from './api';

type MovementType = 'CASH_IN' | 'CASH_OUT' | 'SAFE_DROP';
type Attempt = { key: string; body: { type: MovementType; amount: string; reason: string } };
type Reconciliation = {
  state: string;
  openingFloat: number;
  cashReceived: number;
  changeGiven: number;
  cashIn: number;
  cashOut: number;
  safeDrops: number;
  expectedCash: number;
  movements: { id: string; movement_type: MovementType; amount: number; reason: string; cashier: string; created_at: string }[];
};
type Props = {
  session: string;
  revision: number;
  disabled: boolean;
  readOnly?: boolean;
  onPendingChange: (pending: boolean) => void;
  onBusyChange: (busy: boolean) => void;
};
const money = (value: number) => Number(value).toFixed(2);
const labels = { CASH_IN: 'Cash-in', CASH_OUT: 'Cash-out', SAFE_DROP: 'Safe drop' };

export default function CashDrawer({ session, revision, disabled, readOnly = false, onPendingChange, onBusyChange }: Props) {
  const storageKey = `pos-drawer-attempt-v1:${session}`;
  const [pending, setPending] = useState<Attempt | undefined>(() => {
    try { return JSON.parse(sessionStorage.getItem(storageKey) ?? 'null') ?? undefined; }
    catch { return undefined; }
  });
  const [type, setType] = useState<MovementType>('CASH_IN');
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [summary, setSummary] = useState<Reconciliation>();
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);

  useEffect(() => { onPendingChange(!!pending); }, [pending, onPendingChange]);
  useEffect(() => {
    let active = true;
    setSummary(undefined);
    api(`/sessions/${session}/reconciliation`).then(value => {
      if (active) setSummary(value);
    }).catch(() => {
      if (active) setError('Drawer totals unavailable. Refresh before reconciling the session.');
    });
    return () => { active = false; };
  }, [session, revision, refresh]);

  async function record(event: React.FormEvent) {
    event.preventDefault();
    const attempt = pending ?? { key: crypto.randomUUID(), body: { type, amount, reason: reason.trim() } };
    // Save before posting so a lost response or reload can only retry this movement.
    try { sessionStorage.setItem(storageKey, JSON.stringify(attempt)); }
    catch { setError('Cannot save retry details. No cash movement was submitted.'); return; }
    setPending(attempt);
    onPendingChange(true);
    onBusyChange(true);
    setError('');
    setNotice('');
    try {
      await api(`/sessions/${session}/cash-movements`, attempt.body, attempt.key);
      sessionStorage.removeItem(storageKey);
      setPending(undefined);
      onPendingChange(false);
      setAmount('');
      setReason('');
      setNotice('Cash movement recorded.');
      setRefresh(value => value + 1);
    } catch (e) {
      // A denied retry cannot tell us whether the earlier request committed.
      const deniedRetry = !!pending && e instanceof ApiError && [403, 404].includes(e.status);
      if (e instanceof ApiError && [400, 403, 404, 409].includes(e.status) && !deniedRetry) {
        sessionStorage.removeItem(storageKey);
        setPending(undefined);
        onPendingChange(false);
        setError('Movement rejected. Check your permission, open session and drawer balance.');
      } else {
        setError('Cash movement outcome is uncertain. Retry the same movement; do not move the cash again.');
      }
    } finally { onBusyChange(false); }
  }

  return <section aria-label="Cash drawer">
    <h2>Cash drawer</h2>
    {error && <p role="alert">{error}</p>}
    {notice && <p role="status">{notice}</p>}
    {summary && <>
      <dl>
        <dt>Opening float</dt><dd>{money(summary.openingFloat)}</dd>
        <dt>Cash received from sales</dt><dd>{money(summary.cashReceived)}</dd>
        <dt>Change given</dt><dd>{money(summary.changeGiven)}</dd>
        <dt>Cash-in</dt><dd>{money(summary.cashIn)}</dd>
        <dt>Cash-out</dt><dd>{money(summary.cashOut)}</dd>
        <dt>Safe drops</dt><dd>{money(summary.safeDrops)}</dd>
        <dt>Expected drawer cash</dt><dd>{money(summary.expectedCash)}</dd>
      </dl>
      <p>Non-sale cash movements are separate from customer payments.</p>
      <ul aria-label="Cash movement history">{summary.movements.map(m =>
        <li key={m.id}>{labels[m.movement_type]}: {money(m.amount)} · {m.reason} · {m.cashier} · {m.created_at}</li>
      )}</ul>
    </>}
    <button type="button" disabled={disabled} onClick={() => { setError(''); setRefresh(value => value + 1); }}>Refresh drawer totals</button>
    <form onSubmit={record}>
      <fieldset disabled={disabled || !!pending || readOnly || (!!summary && summary.state !== 'OPEN')}>
        <legend>Record a cash movement</legend>
        <label>Movement type <select value={type} onChange={e => setType(e.target.value as MovementType)}>
          <option value="CASH_IN">Cash-in</option><option value="CASH_OUT">Cash-out</option><option value="SAFE_DROP">Safe drop</option>
        </select></label>
        <label>Movement amount <input type="number" min="0.01" max="999999999999.99" step="0.01" required value={amount} onChange={e => setAmount(e.target.value)} /></label>
        <label>Movement reason <input maxLength={250} required pattern=".*\S.*" value={reason} onChange={e => setReason(e.target.value)} /></label>
      </fieldset>
      <button type="submit" disabled={disabled || (!pending && (!amount || !reason.trim() || readOnly || (!!summary && summary.state !== 'OPEN')))}>
        {pending ? 'Retry same cash movement' : 'Record cash movement'}
      </button>
    </form>
  </section>;
}
