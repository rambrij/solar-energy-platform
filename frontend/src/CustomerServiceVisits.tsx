import {useCallback,useEffect,useState} from 'react';
import {marketplaceApi} from './marketplaceApi';

type Visit={id:number;caseType:'WARRANTY_CLAIM'|'SERVICE_REQUEST';caseId:number;orderId:string;caseSubject:string;siteAddress:string;scheduledAt:string;status:string;createdAt:string;updatedAt:string;completedAt:string|null};
type Event={id:number;eventType:string;toStatus:string;note:string;occurredAt:string};
type Detail={visit:Visit;events:Event[]};
const dateTime=(value:string)=>new Date(value).toLocaleString();
export default function CustomerServiceVisits(){
 const [visits,setVisits]=useState<Visit[]>([]),[selected,setSelected]=useState<number|null>(null),[detail,setDetail]=useState<Detail|null>(null);
 const [loading,setLoading]=useState(true),[error,setError]=useState('');
 const refresh=useCallback(async()=>{
  setLoading(true);setError('');
  try{
   const rows=await marketplaceApi('/api/v1/customer/service-visits') as Visit[];
   setVisits(rows);setSelected(current=>current&&rows.some(v=>v.id===current)?current:rows[0]?.id??null);
  }catch(e){setError((e as Error).message||'Engineer visits could not be loaded.');}
  finally{setLoading(false);}
 },[]);
 useEffect(()=>{void refresh();},[refresh]);
 useEffect(()=>{
  let active=true;
  if(selected===null){setDetail(null);return;}
  void marketplaceApi('/api/v1/customer/service-visits/'+selected).then(value=>{if(active)setDetail(value as Detail);})
   .catch(e=>{if(active)setError((e as Error).message||'Visit report could not be loaded.');});
  return()=>{active=false;};
 },[selected]);
 return <section className="customer-service-visits" aria-labelledby="customer-service-visits-title" aria-busy={loading}>
  <header><span className="eyebrow">My purchases</span><h2 id="customer-service-visits-title">My engineer visits</h2>
   <p>Follow scheduled engineer visits and customer-shared reports for your warranty claims and service requests.</p></header>
  {error&&<p role="alert">{error}</p>}{loading&&<p role="status">Loading engineer visits…</p>}
  {!loading&&visits.length===0&&!error&&<p>No engineer visits have been scheduled for your warranty claims or service requests.</p>}
  {visits.length>0&&<div className="customer-installation-layout">
   <section aria-label="Your engineer visits"><h3>Your visits</h3>{visits.map(visit=><article key={visit.id}>
    <h4>{visit.caseType==='WARRANTY_CLAIM'?'Warranty claim':'Service request'} #{visit.caseId}</h4>
    <p><strong>{visit.status.replaceAll('_',' ')}</strong> · {visit.caseSubject}</p>
    <p>Order {visit.orderId} · {dateTime(visit.scheduledAt)}</p>
    <button type="button" className="secondary" aria-pressed={selected===visit.id} onClick={()=>setSelected(visit.id)}>View visit report</button>
   </article>)}</section>
   {detail&&<section aria-label="Selected engineer visit report">
    <h3>Visit #{detail.visit.id}</h3><p><strong>{detail.visit.status.replaceAll('_',' ')}</strong> · {detail.visit.siteAddress}</p>
    <p>Scheduled: {dateTime(detail.visit.scheduledAt)}</p><h4>Customer-shared report</h4>
    {detail.events.length===0?<p>No customer-visible report notes have been shared yet.</p>:<ol>{detail.events.map(ev=><li key={ev.id}><strong>{ev.eventType.replaceAll('_',' ')}</strong> · {ev.note}<small> {dateTime(ev.occurredAt)}</small></li>)}</ol>}
   </section>}
  </div>}
  <button type="button" className="secondary" disabled={loading} onClick={()=>void refresh()}>Refresh engineer visits</button>
 </section>;
}
