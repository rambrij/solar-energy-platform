import {useEffect,useState} from 'react';
import {vendorApi} from './api';
type Eligible={orderId:string;orderedAt:string;total:number};
type SerialAsset={id:number;productId:number;productName:string;serialNumber:string;orderId:string;shipmentId:number;installationId:number|null;recordedAt:string};
type Shipment={id:number;orderId:string;carrier:string;trackingReference:string;trackingUrl:string|null;createdAt:string;events:{id:number;eventCode:string;description:string;reason:string;actorUsername:string;occurredAt:string}[];serialAssets:SerialAsset[]};
export default function ShipmentManagement(){
 const [eligible,setEligible]=useState<Eligible[]>([]),[shipments,setShipments]=useState<Shipment[]>([]),[selected,setSelected]=useState(''),[carrier,setCarrier]=useState(''),[reference,setReference]=useState(''),[url,setUrl]=useState(''),[reason,setReason]=useState(''),[code,setCode]=useState('PICKED_UP'),[description,setDescription]=useState(''),[serialProductId,setSerialProductId]=useState(''),[serialNumbers,setSerialNumbers]=useState(''),[serialReason,setSerialReason]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 async function load(){setBusy(true);setError('');try{const [orders,rows]=await Promise.all([vendorApi('/shipments/orders'),vendorApi('/shipments')]);const withSerials=await Promise.all((rows as Shipment[]).map(async row=>({...row,serialAssets:await vendorApi(`/shipments/${row.id}/serial-assets`).catch(()=>[])})));setEligible(orders);setShipments(withSerials);}catch{setError('Shipment workspace could not be loaded. Check shipment permissions and retry.');}finally{setBusy(false);}}
 useEffect(()=>{void load();},[]);
 async function create(event:React.FormEvent){event.preventDefault();setBusy(true);setError('');setNotice('');try{await vendorApi(`/orders/${encodeURIComponent(selected)}/shipments`,'POST',{carrier,trackingReference:reference,trackingUrl:url||null,reason});setCarrier('');setReference('');setUrl('');setReason('');setSelected('');setNotice('Shipment registered.');await load();}catch{setError('Shipment was not confirmed. Check the order and tracking details, then retry.');}finally{setBusy(false);}}
 async function captureSerials(id:number){
  const serials=serialNumbers.split(/[\\n,;]+/).map(value=>value.trim()).filter(Boolean);
  if(!serialProductId||serials.length===0||!serialReason.trim()){setError('Enter a product ID, at least one serial number and an audit reason.');return;}
  setBusy(true);setError('');setNotice('');
  try{await vendorApi(`/shipments/${id}/serial-assets`,'POST',{productId:Number(serialProductId),serialNumbers:serials,reason:serialReason.trim()});setSerialNumbers('');setSerialReason('');setNotice('Serial numbers recorded and linked to the order.');await load();}
  catch(e){setError(e instanceof Error?e.message:'Serial numbers were not recorded. Check product quantity and duplicate serials.');}
  finally{setBusy(false);}
 }
 async function addEvent(id:number){setBusy(true);setError('');setNotice('');try{await vendorApi(`/shipments/${id}/events`,'POST',{eventCode:code,description,reason});setDescription('');setNotice('Tracking event added.');await load();}catch{setError('Tracking event was not confirmed. No existing event was changed.');}finally{setBusy(false);}}
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
   <section aria-label={`Serialized assets for shipment ${s.id}`}><h5>Serialized product units</h5>
    {s.serialAssets.length===0?<p>No serial numbers recorded yet.</p>:<ul>{s.serialAssets.map(asset=><li key={asset.id}>{asset.productName} — {asset.serialNumber}{asset.installationId? ` · Installation #${asset.installationId}`:''}</li>)}</ul>}
    <form onSubmit={e=>{e.preventDefault();void captureSerials(s.id);}}>
     <label>Product ID<input required type="number" min="1" value={serialProductId} onChange={e=>setSerialProductId(e.target.value)}/></label>
     <label>Serial numbers (one per line or comma-separated)<textarea required value={serialNumbers} onChange={e=>setSerialNumbers(e.target.value)} rows={3}/></label>
     <label>Audit reason<input required maxLength={500} value={serialReason} onChange={e=>setSerialReason(e.target.value)}/></label>
     <button disabled={busy||!serialProductId||!serialNumbers.trim()||!serialReason.trim()}>Record serial numbers</button>
    </form>
   </section>
   <form onSubmit={e=>{e.preventDefault();void addEvent(s.id);}}><label>Event code<input value={code} maxLength={40} onChange={e=>setCode(e.target.value)}/></label><label>Event description<input required value={description} maxLength={500} onChange={e=>setDescription(e.target.value)}/></label><label>Audit reason<input required value={reason} maxLength={500} onChange={e=>setReason(e.target.value)}/></label><button disabled={busy||!description.trim()}>Add tracking event</button></form>
  </article>)}</section>
  <button type="button" className="secondary" disabled={busy} onClick={()=>void load()}>Refresh shipments</button>
 </section>;
}
