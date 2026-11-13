import { useEffect, useRef, useState } from 'react';
import { api } from './api';
import { marketplaceApi } from './marketplaceApi';
import type { Receipt } from './types';

type Order = { id: string; vendorName: string; channel: string; status: string; createdAt: string; expiresAt: string | null; total: number; receiptNumber: number | null; taxInvoice: boolean };
type Page = { items: Order[]; page: number; size: number; totalElements: number; totalPages: number };
type Detail = { order: Order; items: { productId: number; name: string; quantity: number; unitPrice: number; tax: number; total: number; discount?: number }[]; cashReceived: number | null; changeGiven: number | null; cancellationReason: string | null; statusChangedAt: string | null };
type Filters = { status: string; reference: string; from: string; to: string };
const initial: Filters = { status: 'ALL', reference: '', from: '', to: '' };
const money = (value: number) => Number(value).toFixed(2);
const date = (value: string | null) => value ? new Date(value).toLocaleString() : 'Not recorded';
export default function OrderHistory({ mode, onReceipt, onReturn }: { mode: 'pos' | 'online'; onReceipt?: (value: Receipt) => void; onReturn?: (id: string) => void }) {
  const [filters, setFilters] = useState<Filters>(initial), [applied, setApplied] = useState<Filters>(initial);
  const [page, setPage] = useState<Page>(), [detail, setDetail] = useState<Detail>(), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const version = useRef(0);
  useEffect(() => () => { version.current += 1; }, []);
  const request = (suffix: string) => mode === 'pos' ? api(`/orders${suffix}`) : marketplaceApi(`/api/customer/orders${suffix}`);
  async function load(index: number, query: Filters) {
    if (query.from && query.to && query.from > query.to) { setError('From date must not follow to date.'); return; }
    const current = ++version.current;
    setBusy(true); setError(''); setDetail(undefined);
    const params = new URLSearchParams({ status: query.status, reference: query.reference.trim(), page: String(index), size: '10' });
    if (query.from) params.set('from', query.from);
    if (query.to) params.set('to', query.to);
    try {
      const value: Page = await request(`?${params}`);
      if (current === version.current) { setPage(value); setApplied(query); }
    } catch { if (current === version.current) setError('History unavailable. Check your login, permissions and filters, then retry. Existing sales are unchanged.'); }
    finally { if (current === version.current) setBusy(false); }
  }
  async function inspect(id: string) {
    const current = ++version.current; setBusy(true); setError(''); setDetail(undefined);
    try { const value: Detail = await request(`/${encodeURIComponent(id)}`); if (current === version.current) setDetail(value); }
    catch { if (current === version.current) setError('Order unavailable for your account. Reload history and try again.'); }
    finally { if (current === version.current) setBusy(false); }
  }
  async function receipt(id: string) {
    const current = ++version.current; setBusy(true); setError('');
    try { const value: Receipt = await api(`/receipts/${encodeURIComponent(id)}`); if (current === version.current) onReceipt?.(value); }
    catch { if (current === version.current) setError('Receipt unavailable. Receipt-view permission is required for a paid sale in your branch.'); }
    finally { if (current === version.current) setBusy(false); }
  }
  return <section id="order-history" className="order-history" aria-labelledby="history-title" aria-busy={busy}>
    <div className="section-heading"><div><span className="eyebrow">{mode === 'pos' ? 'Branch activity' : 'Your purchases'}</span><h2 id="history-title">{mode === 'pos' ? 'Sales history' : 'My online orders'}</h2></div><span className="badge">{mode === 'pos' ? 'Branch scoped' : 'Private to you'}</span></div>
    <p>{mode === 'pos' ? 'Find walk-in sales across your branch, including closed shifts. No customer account lookup is exposed.' : 'Track your online reservations across vendors. Unpaid drafts are not confirmed purchases.'}</p>
    <form className="history-filters" onSubmit={event => { event.preventDefault(); void load(0, filters); }}>
      <label>Order status<select value={filters.status} disabled={busy} onChange={event => setFilters({ ...filters, status: event.target.value })}>{['ALL', 'DRAFT', 'PAID', 'CANCELLED', 'EXPIRED'].map(status => <option key={status} value={status}>{status === 'ALL' ? 'All statuses' : status}</option>)}</select></label>
      <label>Order reference or receipt<input placeholder="Reference prefix or receipt number" maxLength={36} value={filters.reference} disabled={busy} onChange={event => setFilters({ ...filters, reference: event.target.value })}/></label>
      <label>From date (UTC)<input type="date" value={filters.from} disabled={busy} onChange={event => setFilters({ ...filters, from: event.target.value })}/></label>
      <label>To date (UTC)<input type="date" value={filters.to} disabled={busy} onChange={event => setFilters({ ...filters, to: event.target.value })}/></label>
      <button disabled={busy} type="submit">{busy ? 'Loading history…' : page ? 'Apply history filters' : 'Load order history'}</button>
    </form>
    {error && <p role="alert">{error}</p>}
    {page && <><p role="status">{page.totalElements} matching orders · Newest first</p>
      {!page.items.length ? <div className="empty-state"><h3>No orders found</h3><p>Try another status, reference or date range. Cart items appear here only after reserving a draft.</p></div> : <ul className="order-list">{page.items.map(order => <li key={order.id} className="order-card"><div><span className={`badge status-${order.status.toLowerCase()}`}>{order.status}</span><h3>{order.vendorName}</h3><p className="order-reference">{order.id}</p><p>{date(order.createdAt)}{order.receiptNumber !== null ? ` · Receipt ${order.receiptNumber}` : ''}</p></div><div className="order-actions"><strong>{money(order.total)}</strong><button className="secondary" disabled={busy} onClick={() => void inspect(order.id)}>View order {order.id}</button>{mode === 'pos' && order.status === 'PAID' && onReturn && <button className="secondary" disabled={busy} onClick={()=>onReturn(order.id)}>Start return {order.receiptNumber}</button>}{mode === 'pos' && order.status === 'PAID' && onReceipt && <button disabled={busy} onClick={() => void receipt(order.id)}>Open receipt {order.receiptNumber}</button>}</div></li>)}</ul>}
      <nav className="pagination" aria-label="Order history pages"><button className="secondary" disabled={busy || page.page === 0} onClick={() => void load(page.page - 1, applied)}>Previous orders</button><span>Page {page.page + 1} of {Math.max(1, page.totalPages)}</span><button className="secondary" disabled={busy || page.page + 1 >= page.totalPages} onClick={() => void load(page.page + 1, applied)}>Next orders</button></nav>
    </>}
    {detail && <section className="order-detail" aria-label="Order details"><div className="section-heading"><h3>Order details</h3><button className="secondary" onClick={() => setDetail(undefined)}>Close order details</button></div><p className="order-reference">{detail.order.id}</p><p>{detail.order.status} · {detail.order.channel === 'ONLINE' ? 'Online' : 'Walk-in'} · Total {money(detail.order.total)}</p><ul>{detail.items.map(line => <li key={line.productId}><strong>{line.name}</strong> × {line.quantity} · Unit {money(line.unitPrice)} · Tax {money(line.tax)} · Total {money(line.total)}{Number(line.discount??0)>0&&<> · Discount {money(line.discount??0)}</>}</li>)}</ul>
      {detail.cashReceived !== null && <p>Cash received {money(detail.cashReceived)} · Change {money(detail.changeGiven ?? 0)}</p>}
      {detail.cancellationReason && <p>Cancellation reason: {detail.cancellationReason}</p>}
      {detail.order.status === 'DRAFT' && <p>Unpaid reservation · Expires {date(detail.order.expiresAt)}. Manage it in reserved drafts above.</p>}
      <p>Status updated {date(detail.statusChangedAt)}</p><p>Line details are saved order snapshots. Catalogue changes do not change this order.</p>
    </section>}
  </section>;
}
