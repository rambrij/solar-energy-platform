import {useSession} from './session';
import { useEffect, useRef, useState } from 'react';
import { api } from './api';
import { marketplaceApi } from './marketplaceApi';
import type { Receipt } from './types';
import OnlineReceipt from './OnlineReceipt';

type Order = { id: string; vendorName: string; channel: string; status: string; paymentState: string; createdAt: string; expiresAt: string | null; total: number; receiptNumber: number | null; taxInvoice: boolean };
type Page = { items: Order[]; page: number; size: number; totalElements: number; totalPages: number };
type Tracking = {id:number;carrier:string;trackingReference:string;trackingUrl:string|null;createdAt:string;events:{id:number;eventCode:string;description:string;occurredAt:string}[]};
type CustomerSerialAsset={productId:number;productName:string;serialNumber:string;recordedAt:string};
type CustomerReturnStatus = {id:string;state:string;requestedAt:string;reviewedAt:string|null;inspectedAt:string|null;cashRefundStatus:string;cashRefundAmount:number|null;cashRefundedAt:string|null;items:{name:string;quantity:number}[]};
type CustomerReturnHistory = {orderId:string;returns:CustomerReturnStatus[]};
type Detail = { order: Order; items: { productId: number; name: string; quantity: number; unitPrice: number; tax: number; total: number; discount?: number }[]; cashReceived: number | null; changeGiven: number | null; cancellationReason: string | null; statusChangedAt: string | null };
type Filters = { status: string; reference: string; from: string; to: string };
const initial: Filters = { status: 'ALL', reference: '', from: '', to: '' };
const money = (value: number) => Number(value).toFixed(2);
const date = (value: string | null) => value ? new Date(value).toLocaleString() : 'Not recorded';
export default function OrderHistory({ mode, onReceipt, onReturn }: { mode: 'pos' | 'online'; onReceipt?: (value: Receipt) => void; onReturn?: (id: string) => void }) {
  const {account}=useSession();
  const canStartReturn=mode==='online'||!!account?.permissions.includes('POS_RETURN_CREATE');
  const canOpenPosReceipt=mode==='online'||!!account?.permissions.includes('POS_RECEIPT_VIEW');
  const [onlineReceipt,setOnlineReceipt]=useState<{orderId:string;channel:string}>();
  const [tracking,setTracking]=useState<Tracking[]>([]);
  const [serialAssets,setSerialAssets]=useState<CustomerSerialAsset[]>([]);
  const [customerReturns,setCustomerReturns]=useState<CustomerReturnStatus[]>([]);
  const [customerReturnError,setCustomerReturnError]=useState('');
  const [customerReturnLoading,setCustomerReturnLoading]=useState(false);
  const [filters, setFilters] = useState<Filters>(initial), [applied, setApplied] = useState<Filters>(initial);
  const [page, setPage] = useState<Page>(), [detail, setDetail] = useState<Detail>(), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const version = useRef(0);
  useEffect(() => () => { version.current += 1; }, []);
  const request = (suffix: string) => mode === 'pos' ? api(`/orders${suffix}`) : marketplaceApi(`/api/v1/customer/orders${suffix}`);
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
  async function loadCustomerReturnStatus(id: string) {
    const current = version.current;
    setCustomerReturnError('');
    setCustomerReturns([]);
    setCustomerReturnLoading(true);
    try {
      const history: CustomerReturnHistory = await marketplaceApi(`/api/v1/customer/orders/${encodeURIComponent(id)}/returns`);
      if (current === version.current && history.orderId === id) setCustomerReturns(history.returns);
    } catch {
      if (current === version.current) setCustomerReturnError('Return status is currently unavailable. Retry to check again.');
    } finally {
      if (current === version.current) setCustomerReturnLoading(false);
    }
  }
  async function inspect(id: string) {
    const current = ++version.current; setBusy(true); setError(''); setDetail(undefined);
    setTracking([]); setSerialAssets([]); setCustomerReturns([]); setCustomerReturnError(''); setCustomerReturnLoading(false);
    try {
      const value: Detail = await request(`/${encodeURIComponent(id)}`);
      if (current === version.current) {
        setDetail(value);
        if (mode === 'online' && value.order.channel === 'ONLINE') {
          try {
            const [trackingRows,serialRows] = await Promise.all([
              marketplaceApi(`/api/v1/customer/orders/${encodeURIComponent(id)}/shipments`) as Promise<Tracking[]>,
              marketplaceApi(`/api/v1/customer/orders/${encodeURIComponent(id)}/serial-assets`) as Promise<CustomerSerialAsset[]>
            ]);
            if (current === version.current) { setTracking(trackingRows); setSerialAssets(serialRows); }
          } catch {
            if (current === version.current) setTracking([]);
          }
        } else if (mode === 'online' && value.order.channel === 'IN_STORE' && value.order.status === 'PAID') {
          await loadCustomerReturnStatus(id);
        }
      }
    }
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
    <div className="section-heading"><div><span className="eyebrow">{mode === 'pos' ? 'Branch activity' : 'Your purchases'}</span><h2 id="history-title">{mode === 'pos' ? 'Sales history' : 'My orders'}</h2></div><span className="badge">{mode === 'pos' ? 'Branch scoped' : 'Private to you'}</span></div>
    <p>{mode === 'pos' ? 'Find walk-in sales across your branch, including closed shifts. No customer account lookup is exposed.' : 'Track your online purchases and verified in-store purchases across vendors. Unpaid drafts are not confirmed purchases.'}</p>
    <form className="history-filters" onSubmit={event => { event.preventDefault(); void load(0, filters); }}>
      <label>Order status<select value={filters.status} disabled={busy} onChange={event => setFilters({ ...filters, status: event.target.value })}>{['ALL', 'DRAFT', 'PAID', 'CANCELLED', 'EXPIRED'].map(status => <option key={status} value={status}>{status === 'ALL' ? 'All statuses' : status}</option>)}</select></label>
      <label>Order reference or receipt<input placeholder="Reference prefix or receipt number" maxLength={36} value={filters.reference} disabled={busy} onChange={event => setFilters({ ...filters, reference: event.target.value })}/></label>
      <label>From date (UTC)<input type="date" value={filters.from} disabled={busy} onChange={event => setFilters({ ...filters, from: event.target.value })}/></label>
      <label>To date (UTC)<input type="date" value={filters.to} disabled={busy} onChange={event => setFilters({ ...filters, to: event.target.value })}/></label>
      <button disabled={busy} type="submit">{busy ? 'Loading history…' : page ? 'Apply history filters' : 'Load order history'}</button>
    </form>
    {error && <p role="alert">{error}</p>}
    {page && <><p role="status">{page.totalElements} matching orders · Newest first</p>
      {!page.items.length ? <div className="empty-state"><h3>No orders found</h3><p>Try another status, reference or date range. Cart items appear here only after reserving a draft.</p></div> : <ul className="order-list">{page.items.map(order => <li key={order.id} className="order-card"><div><span className={`badge status-${order.status.toLowerCase()}`}>{order.status}</span><h3>{order.vendorName}</h3><p className="order-reference">{order.id}</p><p>{date(order.createdAt)}{order.receiptNumber !== null ? ` · Receipt ${order.receiptNumber}` : ''}</p><p>Payment: {order.paymentState}</p></div><div className="order-actions"><strong>{money(order.total)}</strong><button className="secondary" disabled={busy} onClick={() => void inspect(order.id)}>View order {order.id}</button>{mode === 'pos' && canStartReturn && order.status === 'PAID' && onReturn && <button className="secondary" disabled={busy} onClick={()=>onReturn(order.id)}>Start return {order.receiptNumber}</button>}{mode === 'pos' && canOpenPosReceipt && order.status === 'PAID' && onReceipt && <button disabled={busy} onClick={() => void receipt(order.id)}>Open receipt {order.receiptNumber}</button>}{mode === 'online' && order.status === 'PAID' && <button className="secondary" disabled={busy} onClick={() => setOnlineReceipt({orderId:order.id,channel:order.channel})}>Open receipt</button>}</div></li>)}</ul>}
      <nav className="pagination" aria-label="Order history pages"><button className="secondary" disabled={busy || page.page === 0} onClick={() => void load(page.page - 1, applied)}>Previous orders</button><span>Page {page.page + 1} of {Math.max(1, page.totalPages)}</span><button className="secondary" disabled={busy || page.page + 1 >= page.totalPages} onClick={() => void load(page.page + 1, applied)}>Next orders</button></nav>
    </>}
    {detail && <section className="order-detail" aria-label="Order details"><div className="section-heading"><h3>Order details</h3><button className="secondary" onClick={() => setDetail(undefined)}>Close order details</button></div><p className="order-reference">{detail.order.id}</p><p>{detail.order.status} · {detail.order.channel === 'ONLINE' ? 'Online' : 'Walk-in'} · Total {money(detail.order.total)}</p><p>Payment state: {detail.order.paymentState}</p><ul>{detail.items.map(line => <li key={line.productId}><strong>{line.name}</strong> × {line.quantity} · Unit {money(line.unitPrice)} · Tax {money(line.tax)} · Total {money(line.total)}{Number(line.discount??0)>0&&<> · Discount {money(line.discount??0)}</>}</li>)}</ul>
      {detail.cashReceived !== null && <p>Cash received {money(detail.cashReceived)} · Change {money(detail.changeGiven ?? 0)}</p>}
      {detail.cancellationReason && <p>Cancellation reason: {detail.cancellationReason}</p>}
      {detail.order.status === 'DRAFT' && <p>Unpaid reservation · Expires {date(detail.order.expiresAt)}. Manage it in reserved drafts above.</p>}
      {mode==='online'&&detail.order.channel==='ONLINE'&&<section aria-label="Serialized product traceability"><h4>Serialized product units</h4>{serialAssets.length===0?<p>No serial numbers have been recorded for this order yet.</p>:<ul>{serialAssets.map((asset,index)=><li key={asset.productId+'-'+asset.serialNumber+'-'+index}>{asset.productName} — Serial number {asset.serialNumber}</li>)}</ul>}</section>}
    {mode==='online'&&detail.order.channel==='ONLINE'&&<section aria-label="Shipment tracking"><h4>Shipment tracking</h4>{tracking.length===0?<p>No shipment has been registered for this order yet.</p>:tracking.map(shipment=><article key={shipment.id}><h5>{shipment.carrier}</h5><p>Tracking reference: {shipment.trackingReference}</p>{shipment.trackingUrl&&<a href={shipment.trackingUrl} target="_blank" rel="noreferrer">Open carrier tracking</a>}<ol>{shipment.events.map(event=><li key={event.id}><strong>{event.eventCode.replaceAll('_',' ')}</strong> · {event.description} · {date(event.occurredAt)}</li>)}</ol></article>)}</section>}
      {mode==='online'&&detail.order.channel==='IN_STORE'&&detail.order.status==='PAID'&&<section aria-label="Return and refund status">
        <div className="section-heading"><h4>Return and refund status</h4></div>
        {customerReturnError
          ? <p role="alert">{customerReturnError} <button className="secondary" disabled={customerReturnLoading} onClick={()=>void loadCustomerReturnStatus(detail.order.id)}>{customerReturnLoading?'Checking…':'Retry return status'}</button></p>
          : customerReturnLoading && customerReturns.length===0
            ? <p role="status">Checking return status…</p>
            : customerReturns.length===0
              ? <p>No return has been recorded for this verified store purchase.</p>
              : customerReturns.map(returned=><article key={returned.id}>
                  <p><strong>Return status:</strong> {returned.state.replaceAll('_',' ')}</p>
                  <p>Requested {date(returned.requestedAt)}</p>
                  {returned.reviewedAt&&<p>Reviewed {date(returned.reviewedAt)}</p>}
                  {returned.inspectedAt&&<p>Inspection recorded {date(returned.inspectedAt)}</p>}
                  {returned.items.length>0&&<ul>{returned.items.map((item,index)=><li key={index}>{item.name} × {item.quantity}</li>)}</ul>}
                  <p><strong>Cash refund:</strong> {returned.cashRefundStatus==='PAID'
                    ? 'Paid ' + money(returned.cashRefundAmount??0) + ' on ' + date(returned.cashRefundedAt)
                    : 'Not issued'}</p>
                </article>)}
        <p className="receipt-note">This is the recorded status of the existing in-store return workflow. Viewing it does not start a return or change a refund.</p>
      </section>}
      <p>Status updated {date(detail.statusChangedAt)}</p><p>Line details are saved order snapshots. Catalogue changes do not change this order.</p>
    </section>}
    
    {mode==='online'&&onlineReceipt&&<OnlineReceipt key={onlineReceipt.orderId} orderId={onlineReceipt.orderId} channel={onlineReceipt.channel} onClose={()=>setOnlineReceipt(undefined)}/>}
  </section>;
}
