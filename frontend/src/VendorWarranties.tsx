import {useCallback,useEffect,useState,type FormEvent} from 'react';
import {vendorApi} from './api';
import {useSession} from './session';

type Warranty={id:number;orderId:string;productId:number;productName:string;installationId:number|null;coverageStartDate:string;coverageEndDate:string;termsSummary:string;registeredBy:string;registeredAt:string};
type EligibleOrder={orderId:string;orderedAt:string};
type EligibleItem={productId:number;productName:string;quantity:number};

export default function VendorWarranties(){
 const {account}=useSession();
 const canView=account?.permissions.includes('SERVICE_VIEW')??false;
 const canCreate=account?.permissions.includes('SERVICE_CREATE')??false;
 const [rows,setRows]=useState<Warranty[]>([]),[orders,setOrders]=useState<EligibleOrder[]>([]),[items,setItems]=useState<EligibleItem[]>([]);
 const [orderId,setOrderId]=useState(''),[productId,setProductId]=useState(''),[start,setStart]=useState(''),[end,setEnd]=useState(''),[terms,setTerms]=useState(''),[reason,setReason]=useState('');
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const refresh=useCallback(async()=>{
  setBusy(true);setError('');
  try{const [w,o]=await Promise.all([canView?vendorApi('/warranties') as Promise<Warranty[]>:Promise.resolve([]),canCreate?vendorApi('/warranties/orders') as Promise<EligibleOrder[]>:Promise.resolve([])]);setRows(w);setOrders(o);}
  catch(e){setError((e as Error).message||'Warranty records could not be loaded.');}
  finally{setBusy(false);}
 },[canView,canCreate]);
 useEffect(()=>{void refresh();},[refresh]);
 useEffect(()=>{
  let active=true;setItems([]);setProductId('');
  if(!orderId)return;
  void vendorApi('/warranties/orders/'+encodeURIComponent(orderId)+'/items').then(value=>{if(active)setItems(value as EligibleItem[]);})
   .catch(e=>{if(active)setError((e as Error).message||'Order items could not be loaded.');});
  return()=>{active=false;};
 },[orderId]);
 async function register(event:FormEvent){
  event.preventDefault();if(!orderId||!productId||!start||!end||!terms.trim()||!reason.trim())return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi('/warranties','POST',{orderId,productId:Number(productId),coverageStartDate:start,coverageEndDate:end,termsSummary:terms.trim(),reason:reason.trim()});
   setNotice('Warranty coverage registered and recorded in the audit history.');
   setOrderId('');setProductId('');setStart('');setEnd('');setTerms('');setReason('');setItems([]);
   await refresh();
  }catch(e){setError((e as Error).message||'Warranty registration was not saved.');}
  finally{setBusy(false);}
 }
 return <section className="vendor-warranties" aria-labelledby="vendor-warranties-title" aria-busy={busy}>
  <header><span className="eyebrow">Fulfilment</span><h2 id="vendor-warranties-title">Warranty coverage</h2>
   <p>Register coverage for a paid online order item. Registered terms are immutable; corrections require a separately audited workflow.</p></header>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  {canCreate&&<section aria-label="Register warranty coverage"><h3>Register product warranty</h3>
   {orders.length===0?<p>No eligible paid online customer orders are available.</p>:<form onSubmit={register}>
    <label>Customer order<select required value={orderId} onChange={e=>setOrderId(e.target.value)}><option value="">Select paid order</option>{orders.map(o=><option key={o.orderId} value={o.orderId}>{o.orderId} · {new Date(o.orderedAt).toLocaleDateString()}</option>)}</select></label>
    <label>Purchased product<select required value={productId} onChange={e=>setProductId(e.target.value)}><option value="">Select order item</option>{items.map(item=><option key={item.productId} value={item.productId}>{item.productName} · Qty {item.quantity}</option>)}</select></label>
    <label>Coverage starts<input type="date" required value={start} onChange={e=>setStart(e.target.value)}/></label>
    <label>Coverage ends<input type="date" required min={start||undefined} value={end} onChange={e=>setEnd(e.target.value)}/></label>
    <label>Coverage terms<textarea required maxLength={2000} rows={3} value={terms} onChange={e=>setTerms(e.target.value)}/></label>
    <label>Registration reason<input required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>
    <button disabled={busy||!orderId||!productId||!start||!end||end<start||!terms.trim()||!reason.trim()}>Register warranty</button>
   </form>}
  </section>}
  {canView&&<section aria-label="Registered warranties"><h3>Registered warranties</h3>
   {rows.length===0?<p>No warranty coverage has been registered yet.</p>:<ul>{rows.map(row=><li key={row.id}>
    <h4>{row.productName} · Warranty #{row.id}</h4><p>Order {row.orderId}</p>
    <p>{row.coverageStartDate} to {row.coverageEndDate}</p><p>{row.termsSummary}</p>
    {row.installationId&&<p>Linked installation #{row.installationId}</p>}
    <small>Registered by {row.registeredBy} · {new Date(row.registeredAt).toLocaleString()}</small>
   </li>)}</ul>}
  </section>}
  <button type="button" className="secondary" disabled={busy} onClick={()=>void refresh()}>Refresh warranties</button>
 </section>;
}
