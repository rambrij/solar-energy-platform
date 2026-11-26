import {useEffect,useState} from 'react';
import {vendorApi} from './api';
type Eligible={orderId:string;orderedAt:string;total:number};
type Shipment={id:number;orderId:string;carrier:string;trackingReference:string;trackingUrl:string|null;createdAt:string;events:{id:number;eventCode:string;description:string;reason:string;actorUsername:string;occurredAt:string}[]};
export default function ShipmentManagement(){
 const [eligible,setEligible]=useState<Eligible[]>([]),[shipments,setShipments]=useState<Shipment[]>([]),[selected,setSelected]=useState(''),[carrier,setCarrier]=useState(''),[reference,setReference]=useState(''),[url,setUrl]=useState(''),[reason,setReason]=useState(''),[code,setCode]=useState('PICKED_UP'),[description,setDescription]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 async function load(){setBusy(true);setError('');try{const [orders,rows]=await Promise.all([vendorApi('/shipments/orders'),vendorApi('/shipments')]);setEligible(orders);setShipments(rows);}catch{setError('Shipment workspace could not be loaded. Check shipment permissions and retry.');}finally{setBusy(false);}}
 useEffect(()=>{void load();},[]);
 async function create(event:React.FormEvent){event.preventDefault();setBusy(true);setError('');setNotice('');try{await vendorApi('/orders/'+encodeURIComponent(selected)+'/shipments','POST',{carrier,trackingReference:reference,trackingUrl:url||null,reason});setCarrier('');setReference('');setUrl('');setReason('');setSelected('');setNotice('Shipment registered.');await load();}catch{setError('Shipment was not confirmed. Check the order and tracking details, then retry.');}finally{setBusy(false);}}
 async function addEvent(id:number){setBusy(true);setError('');setNotice('');try{await vendorApi('/shipments/'+id+'/events','POST',{eventCode:code,description,reason});setDescription('');setNotice('Tracking event added.');await load();}catch{setError('Tracking event was not confirmed. No existing event was changed.');}finally{setBusy(false);}}
 return <section className="shipment-management" aria-labelledby="shipment-management-title" aria-busy={busy}>
  <header><span className="eyebrow">Fulfilment</span><h2 id="shipment-management-title">Shipment tracking</h2><p>Register a carrier reference for a paid online order, then append delivery progress events. Events are retained as history.</p></header>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  <section><h3>Orders ready for shipment</h3>{eligible.length===0?<p>No paid online orders are awaiting shipment registration.</p>:<form onSubmit={create}>
   <label>Paid online order<select required value={selected} onChange={e=>setSelected(e.target.value)}><option value="">Select order</option>{eligible.map(o=><option key={o.orderId} value={o.orderId}>{o.orderId} · {Number(o.total).toFixed(2)}</option>)}</select></label>
   <label>Carrier<input required maxLength={120} value={carrier} onChange={e=>setCarrier(e.target.value)}/></label>
   <label>Tracking reference<input required maxLength={160} value={reference} onChange={e=>setReference(e.target.value)}/></label>
   <label>Tracking URL (HTTPS)<input type="url" value={url} onChange={e=>setUrl(e.target.value)} placeholder="https://carrier.example/track/..."/></label>
   <label>Audit reason<input required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>
   <button disabled={busy||!selected}>Register shipment</button>
  </form>}</section>
  <section><h3>Registered shipments</h3>{shipments.length===0?<p>No shipments registered yet.</p>:shipments.map(s=><article key={s.id}><h4>{s.carrier} · {s.orderId}</h4><p>Tracking reference: {s.trackingReference}</p>{s.trackingUrl&&<a href={s.trackingUrl} target="_blank" rel="noreferrer">Open carrier tracking</a>}
   <ol>{s.events.map(e=><li key={e.id}><strong>{e.eventCode}</strong> · {e.description} <small>{e.reason}</small></li>)}</ol>
   <form onSubmit={e=>{e.preventDefault();void addEvent(s.id);}}><label>Event code<input value={code} maxLength={40} onChange={e=>setCode(e.target.value)}/></label><label>Event description<input required value={description} maxLength={500} onChange={e=>setDescription(e.target.value)}/></label><label>Audit reason<input required value={reason} maxLength={500} onChange={e=>setReason(e.target.value)}/></label><button disabled={busy||!description.trim()}>Add tracking event</button></form>
  </article>)}</section>
  <button type="button" className="secondary" disabled={busy} onClick={()=>void load()}>Refresh shipments</button>
 </section>;
}
