import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api';
import { marketplaceApi, MarketplaceError } from './marketplaceApi';
import type {Draft} from './draftTypes';
import type {Receipt} from './types';
import CashTender from './CashTender';
import {assessCash} from './cashTenderMath';
import {canTenderReservation} from './reservedTenderPolicy';
import DraftReferenceRecovery from './DraftReferenceRecovery';

type Attempt = { action: 'create' | 'cancel' | 'cash'; id?: string; body: unknown; key?: string };
type Props = { mode: 'online' | 'pos'; session?: string; cartId?: string | null; items?: { productId: number; quantity: number }[]; invoiceRequested?: boolean; disabled?: boolean; createDisabled?: boolean; onBlocked?: (value: boolean) => void; onReceipt?: (value: Receipt, finalized: boolean) => void; onStockChange?: () => void };
export default function ReservedDrafts({ mode, session, cartId, items = [], invoiceRequested = false, disabled = false, createDisabled = false, onBlocked, onReceipt, onStockChange }: Props) {
  const storageKey = mode === 'pos' ? `pos-reserved-attempt-v1:${session}` : 'online-reserved-attempt-v1';
  const [pending, setPending] = useState<Attempt | undefined>(() => { try { return JSON.parse(sessionStorage.getItem(storageKey) ?? 'null') ?? undefined; } catch { return undefined; } });
  const [drafts, setDrafts] = useState<Draft[]>([]), [busy, setBusy] = useState(false), [error, setError] = useState(''),[statusFailed,setStatusFailed]=useState(false);
  const [recovered,setRecovered]=useState<Draft>(),[now,setNow]=useState(Date.now);
  const visibleDrafts=recovered?[recovered,...drafts.filter(draft=>draft.id!==recovered.id)]:drafts;
  const [cashByDraft,setCashByDraft]=useState<Record<string,string>>({}),[reasonByDraft,setReasonByDraft]=useState<Record<string,string>>({});
  const generation = useRef(0);
  const held = visibleDrafts.some(draft => draft.status === 'DRAFT');
  const resolvedPending = pending?.id && pending.action !== 'create' ? visibleDrafts.find(draft => draft.id === pending.id && ['PAID','CANCELLED','EXPIRED'].includes(draft.status) && !(pending.action === 'cash' && draft.status === 'PAID')) : undefined;
  useEffect(()=>{if(!held)return;const timer=setInterval(()=>setNow(Date.now()),1000);return()=>clearInterval(timer);},[held]);
  useEffect(() => { onBlocked?.(!!pending || held || statusFailed); }, [pending, held, statusFailed, onBlocked]);
  function request(path: string, body?: unknown, key?: string) {
    return mode === 'pos' ? api(`/drafts${path}`, body, key) : marketplaceApi(`/api/customer/checkout/drafts${path}`, body === undefined ? 'GET' : 'POST', body, key);
  }
  async function reload() {
    const version = ++generation.current;
    try {
      const value = await request(mode === 'pos' ? `?sessionId=${encodeURIComponent(session ?? '')}` : '');
      if (version === generation.current) setDrafts(Array.isArray(value) ? value : []);
      if(recovered){const current=await request(`/${encodeURIComponent(recovered.id)}`);if(version===generation.current)setRecovered(current);}
      if(version===generation.current)setStatusFailed(false);
    } catch { if (version === generation.current) {setStatusFailed(true);setError('Draft status unavailable. Displayed records are last known; reload or recover the reference before a new command. Saved retries were kept.');} }
  }
  useEffect(() => { if (mode === 'pos') void reload(); return () => { generation.current += 1; }; }, [mode, session]);
  async function run(attempt: Attempt) {
    try { sessionStorage.setItem(storageKey, JSON.stringify(attempt)); } catch { setError('Cannot save retry details. No request was submitted.'); return; }
    const wasPending = !!pending;
    generation.current += 1; setPending(attempt); onBlocked?.(true); setBusy(true); setError('');
    try {
      const suffix = attempt.action === 'create' ? '' : `/${attempt.id}/${attempt.action === 'cash' ? 'finalize-cash' : 'cancel'}`;
      const value = await request(suffix, attempt.body, attempt.key);
      if (attempt.action === 'cash') { setRecovered(previous=>previous&&previous.id===attempt.id?{...previous,status:'PAID',expired:false,message:'Cash sale completed. Do not collect cash again.'}:previous);setDrafts(rows => rows.map(row => row.id === attempt.id ? { ...row, status: 'PAID', expired:false, message:'Cash sale completed. Do not collect cash again.' } : row)); onReceipt?.(value,true); setCashByDraft(values=>{const next={...values};delete next[attempt.id!];return next;}); }
      else {setRecovered(previous=>previous&&previous.id===value.id?value:previous);setDrafts(rows => [value, ...rows.filter(row => row.id !== value.id)]);}
      sessionStorage.removeItem(storageKey); setPending(undefined); setReasonByDraft(values=>{const next={...values};if(attempt.id)delete next[attempt.id];return next;}); onStockChange?.();
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
    <button disabled={busy || disabled || statusFailed || createDisabled || !!pending || held || (mode === 'pos' ? !items.length : !cartId)} onClick={create}>Reserve {mode === 'pos' ? 'walk-in sale' : 'cart'} without payment</button>
    <button disabled={busy} onClick={() => { setError(''); void reload(); }}>Reload reserved drafts</button>
    <DraftReferenceRecovery mode={mode} requiredId={pending?.id} disabled={busy} onRecovered={value=>{generation.current++;setRecovered(value);setStatusFailed(false);setError('');setNow(Date.now());}}/>
    {pending && <button disabled={busy} onClick={() => void run(pending)}>Retry same draft operation</button>}
    {resolvedPending && <div><p>Server status is {resolvedPending.status}. Reconcile any cash already received before starting another sale. Paid sales require the return/refund workflow.</p><button disabled={busy} onClick={() => { sessionStorage.removeItem(storageKey); setPending(undefined); setError(''); }}>Acknowledge resolved draft status</button></div>}
    {visibleDrafts.map(draft => <section key={draft.id} aria-label={`Draft ${draft.id}`}>
      <h3>{draft.vendorName} · {draft.status}</h3><p>Reference {draft.id} · Expires {new Date(draft.expiresAt).toLocaleString()} · Total {Number(draft.total).toFixed(2)}</p>
      <ul>{draft.items.map(line => <li key={line.productId}>{line.name} × {line.quantity}: {Number(line.total).toFixed(2)} (tax {Number(line.tax).toFixed(2)}){Number(line.discount??0)>0&&<> · Discount {Number(line.discount).toFixed(2)}</>}</li>)}</ul><p>{draft.message}</p>
      {draft.status==='DRAFT'&&(draft.expired||Date.parse(draft.expiresAt)<=now)&&<p role="status">Reservation time elapsed. Do not collect cash; cancel or reload to release/check the hold.</p>}
      {mode==='pos'&&draft.status==='DRAFT'&&draft.sessionId!==session&&<p>Reserved under another cashier session. It cannot be paid from the current session; cancel it if appropriate.</p>}
      {draft.status === 'DRAFT' && <>
        <label>Cancellation reason <input maxLength={250} disabled={busy||disabled||!!pending} value={reasonByDraft[draft.id]??''} onChange={event=>setReasonByDraft(values=>({...values,[draft.id]:event.target.value}))}/></label>
        {mode==='pos'&&<CashTender amountDue={draft.total} cash={cashByDraft[draft.id]??''} onCash={value=>setCashByDraft(values=>({...values,[draft.id]:value}))} eligible={canTenderReservation(draft,session,now)} locked={busy||disabled||statusFailed||!!pending||!canTenderReservation(draft,session,now)} label="Cash received for reserved sale"/>}
        <button disabled={busy || disabled || statusFailed || !!pending || !(reasonByDraft[draft.id]??'').trim()} onClick={() => void run({ action: 'cancel', id: draft.id, body: { reason: (reasonByDraft[draft.id]??'').trim() } })}>Cancel draft {draft.id}</button>
        {mode === 'pos' && <button disabled={busy || disabled || statusFailed || !!pending || !canTenderReservation(draft,session,now)||assessCash(cashByDraft[draft.id]??'',draft.total).status!=='ready'} onClick={() => void run({ action: 'cash', id: draft.id, body: { cashReceived: cashByDraft[draft.id]??'' } })}>Complete reserved cash sale</button>}
      </>}
      {mode === 'pos' && draft.status === 'PAID' && <button disabled={busy||disabled} onClick={async () => { try { onReceipt?.(await api(`/receipts/${draft.id}`),false); } catch { setError('Receipt unavailable. Check receipt-view permission and retry.'); } }}>Recover cash receipt</button>}
    </section>)}
  </section>;
}
