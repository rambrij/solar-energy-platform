import {useCallback,useEffect,useState} from 'react';
import {marketplaceApi} from './marketplaceApi';

type Job={id:number;orderId:string;siteAddress:string;scheduledAt:string;status:string;createdAt:string;updatedAt:string;completedAt:string|null};
type Event={id:number;type:string;status:string;note:string;occurredAt:string};
type Detail={job:Job;events:Event[]};

export default function CustomerInstallations(){
 const [jobs,setJobs]=useState<Job[]>([]);
 const [selected,setSelected]=useState<number|null>(null);
 const [detail,setDetail]=useState<Detail|null>(null);
 const [loading,setLoading]=useState(true);
 const [error,setError]=useState('');

 const refresh=useCallback(async()=>{
  setLoading(true);setError('');
  try{
   const rows=await marketplaceApi('/api/v1/customer/installations') as Job[];
   setJobs(rows);
   setSelected(current=>current&&rows.some(row=>row.id===current)?current:rows[0]?.id??null);
  }catch(e){setError((e as Error).message||'Your installation updates could not be loaded.');}
  finally{setLoading(false);}
 },[]);
 useEffect(()=>{void refresh();},[refresh]);
 useEffect(()=>{
  let active=true;
  if(selected===null){setDetail(null);return;}
  void marketplaceApi('/api/v1/customer/installations/'+selected).then(value=>{
   if(active)setDetail(value as Detail);
  }).catch(e=>{if(active)setError((e as Error).message||'Installation history could not be loaded.');});
  return()=>{active=false;};
 },[selected]);

 return <section className="customer-installations" aria-labelledby="customer-installations-title" aria-busy={loading}>
  <header><span className="eyebrow">My purchases</span><h2 id="customer-installations-title">My solar installations</h2>
   <p>See the installation schedule and progress shared by your solar vendor.</p></header>
  {error&&<p role="alert">{error}</p>}
  {loading&&<p role="status">Loading installation updates…</p>}
  {!loading&&jobs.length===0&&!error&&<p>No installation jobs are currently registered for your paid online orders.</p>}
  {jobs.length>0&&<div className="customer-installation-layout">
   <section aria-label="Your installation jobs">
    <h3>Your installations</h3>
    {jobs.map(job=><article key={job.id}>
     <h4>Order {job.orderId}</h4><p>{job.status.replaceAll('_',' ')}</p>
     <p>{new Date(job.scheduledAt).toLocaleString()}</p>
     <button type="button" className="secondary" aria-pressed={selected===job.id}
      onClick={()=>setSelected(job.id)}>View updates</button>
    </article>)}
   </section>
   {detail&&<section aria-label="Selected installation progress">
    <h3>Installation #{detail.job.id}</h3>
    <p><strong>{detail.job.status.replaceAll('_',' ')}</strong></p>
    <p>Site: {detail.job.siteAddress}</p>
    <p>Scheduled: {new Date(detail.job.scheduledAt).toLocaleString()}</p>
    <h4>Progress history</h4>
    {detail.events.length===0?<p>No progress updates have been shared yet.</p>:<ol>{detail.events.map(event=><li key={event.id}>
     <strong>{event.status.replaceAll('_',' ')}</strong> · {event.note}
     <small> {new Date(event.occurredAt).toLocaleString()}</small>
    </li>)}</ol>}
   </section>}
  </div>}
  <button type="button" className="secondary" disabled={loading} onClick={()=>void refresh()}>Refresh installation status</button>
 </section>;
}
