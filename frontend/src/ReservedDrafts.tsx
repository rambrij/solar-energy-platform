import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api';
import { marketplaceApi, MarketplaceError } from './marketplaceApi';
type Draft = { id: string; vendorName: string; channel: string; status: string; expiresAt: string; total: number; message: string; items: { productId: number; name: string; quantity: number; unitPrice: number; tax: number; total: number; discount?: number }[] };
type Attempt = { action: 'create' | 'cancel' | 'cash'; id?: string; body: unknown; key?: string };
type Props = { mode: 'online' | 'pos'; session?: string; cartId?: string | null; items?: { productId: number; quantity: number }[]; invoiceRequested?: boolean; disabled?: boolean; createDisabled?: boolean; onBlocked?: (value: boolean) => void; onReceipt?: (value: any, finalized: boolean) => void; onStockChange?: () => void };
export default function ReservedDrafts({ mode, session, cartId, items = [], invoiceRequested = false, disabled = false, createDisabled = false, onBlocked, onReceipt, onStockChange }: Props) {
  const storageKey = mode === 'pos' ? `pos-reserved-attempt-v1:${session}` : 'online-reserved-attempt-v1';
  const [pending, setPending] = useState<Attempt | undefined>(() => { try { return JSON.parse(sessionStorage.getItem(storageKey) ?? 'null') ?? undefined; } catch { return undefined; } });
  const [drafts, setDrafts] = useState<Draft[]>([]), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [cash, setCash] = useState(''), [reason, setReason] = useState('');
  const generation = useRef(0);
  const held = drafts.some(draft => draft.status === 'DRAFT');
  const resolvedPending = pending?.id && pending.action !== 'create' ? drafts.find(draft => draft.id === pending.id && ['PAID','CANCELLED','EXPIRED'].includes(draft.status) && !(pending.action === 'cash' && draft.status === 'PAID')) : undefined;
  useEffect(() => { onBlocked?.(!!pending || held); }, [pending, held, onBlocked]);
  function request(path: string, body?: unknown, key?: string) {
    return mode === 'pos' ? api(`/drafts${path}`, body, key) : marketplaceApi(`/api/customer/checkout/drafts${path}`, body === undefined ? 'GET' : 'POST', body, key);
  }
  async function reload() {
    const version = ++generation.current;
    try {
      const value = await request(mode === 'pos' ? `?sessionId=${encodeURIComponent(session ?? '')}` : '');
      if (version === generation.current) setDrafts(Array.isArray(value) ? value : []);
    } catch { if (version === generation.current) setError('Draft status unavailable. Reload after checking login and connectivity.'); }
  }
  useEffect(() => { if (mode === 'pos') void reload(); return () => { generation.current += 1; }; }, [mode, session]);
  async function run(attempt: Attempt) {
    try { sessionStorage.setItem(storageKey, JSON.stringify(attempt)); } catch { setError('Cannot save retry details. No request was submitted.'); return; }
    const wasPending = !!pending;
    generation.current += 1; setPending(attempt); onBlocked?.(true); setBusy(true); setError('');
    try {
      const suffix = attempt.action === 'create' ? '' : `/${attempt.id}/${attempt.action === 'cash' ? 'finalize-cash' : 'cancel'}`;
      const value = await request(suffix, attempt.body, attempt.key);
      if (attempt.action === 'cash') { setDrafts(rows => rows.map(row => row.id === attempt.id ? { ...row, status: 'PAID' } : row)); onReceipt?.(value,true); setCash(''); }
      else setDrafts(rows => [value, ...rows.filter(row => row.id !== value.id)]);
      sessionStorage.removeItem(storageKey); setPending(undefined); setReason(''); onStockChange?.();
      await reload();
    } catch (e) {
      const status = e instanceof ApiError || e instanceof MarketplaceError ? e.status : undefined;
      if (!wasPending && status && [400,403,404,409].includes(status)) {
        sessionStorage.removeItem(storageKey); setPending(undefined);
        setError('Request rejected. Check the open session, stock, prices, vendor stock location and permissions; resolve any existing draft first.');
        await reload();
      } else setError('Draft outcome is uncertain. Retry the same operation; do not collect cash again or create another draft.');
    } finally { setBusy(false); }
  }
  function create() { void run({ action: 'create', key: crypto.randomUUID(), body: mode === 'pos' ? { sessionId: session, items, invoiceRequested } : { expectedCartId: cartId } }); }
  return <section id={mode === 'online' ? 'online-checkout' : 'reserved-pos-checkout'} aria-label={mode === 'pos' ? 'Reserved POS sales' : 'Reserved online drafts'}>
    <h2>{mode === 'pos' ? 'Reserve and complete a walk-in sale' : 'Reserve your vendor cart'}</h2>
    <p>Stock is held until the displayed expiry. A draft is unpaid. Online/card/UPI payments are not configured.</p>
    {error && <p role="alert">{error}</p>}
    <button disabled={busy || disabled || createDisabled || !!pending || held || (mode === 'pos' ? !items.length : !cartId)} onClick={create}>Reserve {mode === 'pos' ? 'walk-in sale' : 'cart'} without payment</button>
    <button disabled={busy} onClick={() => { setError(''); void reload(); }}>Reload reserved drafts</button>
    {pending && <button disabled={busy} onClick={() => void run(pending)}>Retry same draft operation</button>}
    {resolvedPending && <div><p>Server status is {resolvedPending.status}. Reconcile any cash already received before starting another sale. Paid sales require the return/refund workflow.</p><button disabled={busy} onClick={() => { sessionStorage.removeItem(storageKey); setPending(undefined); setError(''); }}>Acknowledge resolved draft status</button></div>}
    <label>Cancellation reason <input maxLength={250} disabled={busy || !!pending} value={reason} onChange={e => setReason(e.target.value)} /></label>
    {mode === 'pos' && <label>Cash received for reserved sale <input type="number" min="0" step="0.01" disabled={busy || !!pending} value={cash} onChange={e => setCash(e.target.value)} /></label>}
    {drafts.map(draft => <section key={draft.id} aria-label={`Draft ${draft.id}`}>
      <h3>{draft.vendorName} · {draft.status}</h3><p>Reference {draft.id} · Expires {new Date(draft.expiresAt).toLocaleString()} · Total {Number(draft.total).toFixed(2)}</p>
      <ul>{draft.items.map(line => <li key={line.productId}>{line.name} × {line.quantity}: {Number(line.total).toFixed(2)} (tax {Number(line.tax).toFixed(2)}){Number(line.discount??0)>0&&<> · Discount {Number(line.discount).toFixed(2)}</>}</li>)}</ul><p>{draft.message}</p>
      {draft.status === 'DRAFT' && <>
        <button disabled={busy || !!pending || !reason.trim()} onClick={() => void run({ action: 'cancel', id: draft.id, body: { reason: reason.trim() } })}>Cancel draft {draft.id}</button>
        {mode === 'pos' && <button disabled={busy || disabled || !!pending || !cash} onClick={() => void run({ action: 'cash', id: draft.id, body: { cashReceived: cash } })}>Complete reserved cash sale</button>}
      </>}
      {mode === 'pos' && draft.status === 'PAID' && <button disabled={busy||disabled} onClick={async () => { try { onReceipt?.(await api(`/receipts/${draft.id}`),false); } catch { setError('Receipt unavailable. Check receipt-view permission and retry.'); } }}>Recover cash receipt</button>}
    </section>)}
  </section>;
}
