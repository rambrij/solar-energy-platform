import { useCallback, useEffect, useRef, useState } from 'react';
import { marketplaceApi } from './marketplaceApi';

type Profile = { id: number; displayName: string | null; email: string };
type Order = { id: string; vendorName: string; channel: string; status: string; paymentState: string; createdAt: string; total: number; receiptNumber: number | null };
type OrdersPage = { items: Order[]; totalElements: number; totalPages: number; page: number; size: number };
type Cart = { id: string | null; vendorSlug: string | null; vendorName: string | null; items: { productId: number; name: string; quantity: number; available: boolean }[]; total: number; checkoutAvailable: boolean; message: string };
type ShipmentEvent={id:number;eventCode:string;description:string;occurredAt:string};
type Shipment={orderId:string;vendorName:string;id:number;carrier:string;trackingReference:string;trackingUrl:string|null;createdAt:string;events:ShipmentEvent[]};
type DashboardData = { profile: Profile; orders: OrdersPage; cart: Cart };

const money = (value: number) => Number(value).toFixed(2);
const date = (value: string) => new Date(value).toLocaleDateString();

/** Global customer landing view assembled from the existing customer-owned APIs. */
export default function CustomerDashboard() {
  const [data, setData] = useState<DashboardData>();
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  const [retryCount, setRetryCount] = useState(0);
  const [shipments,setShipments]=useState<Shipment[]>([]),[shipmentBusy,setShipmentBusy]=useState(true),[shipmentError,setShipmentError]=useState(''),[shipmentRetry,setShipmentRetry]=useState(0);
  const requestVersion = useRef(0);

  const load = useCallback(async () => {
    const current = ++requestVersion.current;
    setBusy(true);
    setError('');
    try {
      const [profile, orders, cart] = await Promise.all([
        marketplaceApi('/api/v1/customer/profile') as Promise<Profile>,
        marketplaceApi('/api/v1/customer/orders?page=0&size=5&status=ALL&reference=') as Promise<OrdersPage>,
        marketplaceApi('/api/v1/customer/cart') as Promise<Cart>,
      ]);
      if (current === requestVersion.current) setData({ profile, orders, cart });
    } catch {
      if (current === requestVersion.current) setError('Your dashboard could not be loaded. Check your customer session and connection, then retry.');
    } finally {
      if (current === requestVersion.current) setBusy(false);
    }
  }, []);

  useEffect(() => {
    void load();
    return () => { requestVersion.current += 1; };
  }, [load, retryCount]);

  useEffect(()=>{
    let active=true;setShipmentBusy(true);setShipmentError('');
    marketplaceApi('/api/v1/customer/shipments').then((rows:Shipment[])=>{if(active)setShipments(rows);}).catch(()=>{if(active){setShipments([]);setShipmentError('Shipment updates are temporarily unavailable. Your order history is still available.');}}).finally(()=>{if(active)setShipmentBusy(false);});
    return()=>{active=false;};
  },[shipmentRetry]);
  const greeting = data?.profile.displayName?.trim() || data?.profile.email || 'Customer';
  const cartCount = data?.cart.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0;

  return <section className="customer-dashboard" aria-labelledby="customer-dashboard-title" aria-busy={busy}>
    {busy && !data && <p role="status">Loading your global customer dashboard…</p>}
    {error && <div role="alert"><p>{error}</p><button type="button" onClick={() => setRetryCount(count => count + 1)} disabled={busy}>Retry dashboard</button></div>}
    {data && <>
      <header className="customer-dashboard-intro">
        <span className="eyebrow">Your global customer account</span>
        <h2 id="customer-dashboard-title">Welcome back, {greeting}</h2>
        <p>Your purchases from different vendors stay together here, while each vendor’s private business records remain separate.</p>
      </header>
      <div className="customer-dashboard-summary" aria-label="Account summary">
        <article className="customer-dashboard-stat"><span>Orders across vendors</span><strong>{data.orders.totalElements}</strong></article>
        <article className="customer-dashboard-stat"><span>Recent orders shown</span><strong>{data.orders.items.length}</strong></article>
        <article className="customer-dashboard-stat"><span>Items in current cart</span><strong>{cartCount}</strong></article>
      </div>
      <section className="customer-dashboard-card customer-dashboard-shipments" aria-labelledby="customer-dashboard-shipments-title">
        <div className="customer-dashboard-card-heading"><h3 id="customer-dashboard-shipments-title">Shipment updates</h3><a className="customer-dashboard-link" href="/marketplace/purchases">View purchases</a></div>
        {shipmentBusy?<p role="status">Loading shipment updates…</p>:shipmentError?<div role="alert"><p>{shipmentError}</p><button type="button" className="secondary" onClick={()=>setShipmentRetry(v=>v+1)}>Retry shipment updates</button></div>:shipments.length===0?<p>No shipments have been registered for your paid online orders yet.</p>:<ul className="customer-dashboard-orders">{shipments.map(s=><li className="customer-dashboard-order" key={s.id}><div><h4>{s.vendorName}</h4><p>Order {s.orderId} · {s.carrier}</p><p>Tracking reference: {s.trackingReference}</p>{s.events.length?<ol>{s.events.slice(-3).reverse().map(e=><li key={e.id}><strong>{e.eventCode.replaceAll('_',' ')}</strong> · {e.description} · {date(e.occurredAt)}</li>)}</ol>:<p>Shipment registered; no tracking events have been added yet.</p>}{s.trackingUrl&&<a href={s.trackingUrl} target="_blank" rel="noreferrer">Open carrier tracking</a>}</div></li>)}</ul>}
        <p className="receipt-note">Only tracking updates for your own paid online orders are shown. Vendor audit reasons and employee identities are not shared.</p>
      </section>
      <div className="customer-dashboard-grid">
        <section className="customer-dashboard-card" aria-labelledby="customer-dashboard-orders-title">
          <div className="customer-dashboard-card-heading"><h3 id="customer-dashboard-orders-title">Recent orders</h3><a className="customer-dashboard-link" href="/marketplace/purchases">View all orders</a></div>
          {data.orders.items.length ? <ul className="customer-dashboard-orders">{data.orders.items.map(order => <li className="customer-dashboard-order" key={order.id}>
            <div><span className={`badge status-${order.status.toLowerCase()}`}>{order.status}</span><h4>{order.vendorName}</h4><p>Order {order.id} · {date(order.createdAt)} · {order.channel === 'IN_STORE' ? 'Verified store purchase' : 'Online order'}</p><p>Payment: {order.paymentState}{order.receiptNumber !== null ? ` · Receipt ${order.receiptNumber}` : ''}</p></div>
            <strong className="customer-dashboard-order-total">{money(order.total)}</strong>
          </li>)}</ul> : <div className="empty-state"><h4>No orders yet</h4><p>Your online purchases and verified in-store purchases will appear here.</p><a className="customer-dashboard-link" href="/marketplace/vendors">Explore vendors</a></div>}
        </section>
        <aside className="customer-dashboard-aside">
          <section className="customer-dashboard-card" aria-labelledby="customer-dashboard-cart-title">
            <div className="customer-dashboard-card-heading"><h3 id="customer-dashboard-cart-title">Your current cart</h3><a className="customer-dashboard-link" href="/marketplace/cart">Open cart</a></div>
            {data.cart.items.length ? <><strong>{data.cart.vendorName ?? 'One vendor'}</strong><p>{cartCount} item{cartCount === 1 ? '' : 's'} in your single-vendor cart</p><p className="customer-dashboard-cart-total">Current estimate: {money(data.cart.total)}</p><p>Prices and stock are rechecked during checkout. No stock is reserved by this cart.</p></> : <p>Your cart is empty. Choose a vendor to start a new cart.</p>}
          </section>
          <section className="customer-dashboard-card" aria-labelledby="customer-dashboard-account-title">
            <div className="customer-dashboard-card-heading"><h3 id="customer-dashboard-account-title">Account details</h3><a className="customer-dashboard-link" href="/marketplace/profile">Manage profile</a></div>
            <p>{data.profile.displayName?.trim() || 'Display name not set'}</p><p className="customer-dashboard-account-email">{data.profile.email}</p>
            <p>Your sign-in email is read-only in the current profile settings.</p>
          </section>
        </aside>
      </div>
    </>}
  </section>;
}
