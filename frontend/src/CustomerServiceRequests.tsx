import {useCallback,useEffect,useState,type FormEvent} from 'react';
import {marketplaceApi} from './marketplaceApi';

type RequestRow={id:number;orderId:string;installationId:number|null;subject:string;description:string;priority:string;status:string;createdAt:string;updatedAt:string;resolvedAt:string|null};
type EventRow={id:number;eventType:string;toStatus:string;note:string;occurredAt:string};
type Detail={serviceRequest:RequestRow;events:EventRow[]};
type EligibleOrder={orderId:string;sellerName:string;orderedAt:string|null};

export default function CustomerServiceRequests(){
 const [rows,setRows]=useState<RequestRow[]>([]),[orders,setOrders]=useState<EligibleOrder[]>([]);
 const [details,setDetails]=useState<Record<number,Detail>>({});
 const [orderId,setOrderId]=useState(''),[subject,setSubject]=useState(''),[description,setDescription]=useState(''),[priority,setPriority]=useState('NORMAL');
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const refresh=useCallback(async()=>{
  setBusy(true);setError('');
  try{
   const [requests,eligible]=await Promise.all([
    marketplaceApi('/api/v1/customer/service-requests') as Promise<RequestRow[]>,
    marketplaceApi('/api/v1/customer/service-requests/orders') as Promise<EligibleOrder[]>
   ]);
   setRows(requests);setOrders(eligible);
  }catch(e){setError((e as Error).message||'Your service requests could not be loaded.');}
  finally{setBusy(false);}
 },[]);
 useEffect(()=>{void refresh();},[refresh]);
 async function create(event:FormEvent){
  event.preventDefault();if(!orderId||!subject.trim()||!description.trim())return;
  setBusy(true);setError('');setNotice('');
  try{
   await marketplaceApi('/api/v1/customer/service-requests','POST',{orderId,subject:subject.trim(),description:description.trim(),priority});
   setOrderId('');setSubject('');setDescription('');setPriority('NORMAL');setNotice('Your service request has been submitted.');await refresh();
  }catch(e){setError((e as Error).message||'Your service request was not submitted.');}
  finally{setBusy(false);}
 }
 async function detail(id:number){
  setError('');
  try{const result=await marketplaceApi('/api/v1/customer/service-requests/'+id) as Detail;setDetails(v=>({...v,[id]:result}));}
  catch(e){setError((e as Error).message||'Service request history could not be loaded.');}
 }
 return <section className="customer-service-requests" aria-labelledby="customer-service-requests-title" aria-busy={busy}>
  <header><span className="eyebrow">My purchases</span><h2 id="customer-service-requests-title">Service & support</h2>
   <p>Request help with an eligible paid order and follow customer-visible updates from your vendor.</p></header>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  <section aria-label="Create customer service request"><h3>Request support</h3>
   {orders.length===0?<p>No eligible paid online orders are available for a new service request.</p>:<form onSubmit={create}>
    <label>Order<select required value={orderId} onChange={e=>setOrderId(e.target.value)}><option value="">Select your order</option>{orders.map(o=><option key={o.orderId} value={o.orderId}>{o.orderId} · {o.sellerName}</option>)}</select></label>
    <label>Subject<input required maxLength={160} value={subject} onChange={e=>setSubject(e.target.value)}/></label>
    <label>Describe the issue<textarea required rows={3} maxLength={2000} value={description} onChange={e=>setDescription(e.target.value)}/></label>
    <label>Priority<select value={priority} onChange={e=>setPriority(e.target.value)}><option value="LOW">Low</option><option value="NORMAL">Normal</option><option value="HIGH">High</option><option value="URGENT">Urgent</option></select></label>
    <button disabled={busy||!orderId||!subject.trim()||!description.trim()}>Submit support request</button>
   </form>}
  </section>
  <section aria-label="My service requests"><h3>Your service requests</h3>
   {rows.length===0?<p>You have not submitted any service requests for your eligible paid orders.</p>:rows.map(row=><article key={row.id}>
    <h4>Case #{row.id} · {row.subject}</h4><p><strong>{row.status.replaceAll('_',' ')}</strong> · {row.priority} · Order {row.orderId}</p>
    <p>{row.description}</p>{row.installationId&&<p>Installation #{row.installationId}</p>}
    <button type="button" className="secondary" disabled={busy} onClick={()=>void detail(row.id)}>View updates</button>
    {details[row.id]&&<ol>{details[row.id].events.map(event=><li key={event.id}><strong>{event.toStatus.replaceAll('_',' ')}</strong> · {event.note}<small> {new Date(event.occurredAt).toLocaleString()}</small></li>)}</ol>}
   </article>)}
  </section>
  <button type="button" className="secondary" disabled={busy} onClick={()=>void refresh()}>Refresh service requests</button>
 </section>;
}
