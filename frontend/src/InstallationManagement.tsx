import {useCallback,useEffect,useMemo,useState,type FormEvent} from 'react';
import {vendorApi} from './api';
import {useSession} from './session';

type Job={id:number;orderId:string;siteAddress:string;scheduledAt:string;status:string;assignedTo:string;createdAt:string;updatedAt:string;completedAt:string|null;version:number};
type Event={id:number;type:string;status:string;note:string;actorUsername:string;occurredAt:string};
type Detail={job:Job;events:Event[]};
type Eligible={orderId:string;orderedAt:string;total:number|string};

const localDateTime=(value:string)=>new Date(value).toLocaleString();

export default function InstallationManagement(){
 const {account}=useSession();
 const permissions=new Set(account?.permissions??[]);
 const canAssign=permissions.has('INSTALLATION_ASSIGN');
 const canUpdate=permissions.has('INSTALLATION_UPDATE');
 const canCommission=permissions.has('INSTALLATION_COMMISSION');
 const [orders,setOrders]=useState<Eligible[]>([]);
 const [engineers,setEngineers]=useState<string[]>([]);
 const [jobs,setJobs]=useState<Job[]>([]);
 const [details,setDetails]=useState<Record<number,Detail>>({});
 const [orderId,setOrderId]=useState('');
 const [siteAddress,setSiteAddress]=useState('');
 const [scheduledAt,setScheduledAt]=useState('');
 const [assignedTo,setAssignedTo]=useState('');
 const [reasons,setReasons]=useState('');
 const [assignments,setAssignments]=useState<Record<number,{assignedTo:string;scheduledAt:string;reason:string}>>({});
 const [notes,setNotes]=useState<Record<number,string>>({});
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState('');
 const [notice,setNotice]=useState('');

 const refresh=useCallback(async()=>{
  setBusy(true);setError('');
  try{
   const [jobRows,orderRows,engineerRows]=await Promise.all([
    vendorApi('/installations') as Promise<Job[]>,
    canAssign?vendorApi('/installations/orders') as Promise<Eligible[]>:Promise.resolve([]),
    canAssign?vendorApi('/installations/engineers') as Promise<string[]>:Promise.resolve([])
   ]);
   setJobs(jobRows);setOrders(orderRows);setEngineers(engineerRows);
   setAssignedTo(current=>current||engineerRows[0]||'');
  }catch(e){setError((e as Error).message||'Installation workspace could not be loaded.');}
  finally{setBusy(false);}
 },[canAssign]);

 useEffect(()=>{void refresh();},[refresh,account?.tenantId,account?.username]);

 const currentJobs=useMemo(()=>jobs.filter(job=>job.status!=='CANCELLED'),[jobs]);
 async function create(event:FormEvent){
  event.preventDefault();
  if(!orderId||!siteAddress.trim()||!scheduledAt||!assignedTo||!reasons.trim())return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi('/installations','POST',{
    orderId,siteAddress:siteAddress.trim(),scheduledAt:new Date(scheduledAt).toISOString(),
    assignedTo,reason:reasons.trim()
   });
   setOrderId('');setSiteAddress('');setScheduledAt('');setReasons('');
   setNotice('Installation job created and assigned.');
   await refresh();
  }catch(e){setError((e as Error).message||'Installation job was not created.');}
  finally{setBusy(false);}
 }

 async function showDetails(id:number){
  setError('');
  try{
   const result=await vendorApi('/installations/'+id) as Detail;
   setDetails(current=>({...current,[id]:result}));
  }catch(e){setError((e as Error).message||'Installation history could not be loaded.');}
 }

 async function changeAssignment(job:Job){
  const value=assignments[job.id];
  if(!value?.assignedTo||!value.scheduledAt||!value.reason.trim())return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi('/installations/'+job.id+'/assignment','PATCH',{
    expectedVersion:job.version,assignedTo:value.assignedTo,
    scheduledAt:new Date(value.scheduledAt).toISOString(),reason:value.reason.trim()
   });
   setNotice('Installation assignment updated.');
   setAssignments(current=>({...current,[job.id]:{...value,reason:''}}));
   await refresh();
  }catch(e){setError((e as Error).message||'Installation assignment was not updated.');}
  finally{setBusy(false);}
 }

 async function changeStatus(job:Job,status:string){
  const note=(notes[job.id]??'').trim();
  if(!note)return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi('/installations/'+job.id+'/status','PATCH',{
    expectedVersion:job.version,status,note
   });
   setNotice(status==='COMPLETED'?'Installation commissioned.':status==='CANCELLED'?'Installation cancelled.':'Installation progress updated.');
   setNotes(current=>({...current,[job.id]:''}));
   await refresh();
   await showDetails(job.id);
  }catch(e){setError((e as Error).message||'Installation status was not changed.');}
  finally{setBusy(false);}
 }

 return <section className="installation-management" aria-labelledby="installation-title" aria-busy={busy}>
  <header><span className="eyebrow">Fulfilment</span><h2 id="installation-title">Installation management</h2>
   <p>Create installation jobs from paid online orders, assign qualified tenant employees and record customer-visible progress.</p>
  </header>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  {canAssign&&<section aria-label="Create installation job">
   <h3>Paid orders awaiting installation</h3>
   {orders.length===0?<p>No paid online customer orders are awaiting installation.</p>:<form onSubmit={create}>
    <label>Paid order
     <select required value={orderId} onChange={e=>setOrderId(e.target.value)}>
      <option value="">Select a paid order</option>
      {orders.map(order=><option key={order.orderId} value={order.orderId}>{order.orderId} · ₹{Number(order.total).toFixed(2)} · {new Date(order.orderedAt).toLocaleDateString()}</option>)}
     </select>
    </label>
    <label>Installation site address
     <textarea required rows={2} maxLength={1000} value={siteAddress} onChange={e=>setSiteAddress(e.target.value)}
      placeholder="Enter and confirm the actual solar installation site address"/>
    </label>
    <label>Scheduled date and time
     <input required type="datetime-local" value={scheduledAt} onChange={e=>setScheduledAt(e.target.value)}/>
    </label>
    <label>Assigned engineer
     <select required value={assignedTo} onChange={e=>setAssignedTo(e.target.value)}>
      <option value="">Select an enabled installation engineer</option>
      {engineers.map(engineer=><option key={engineer} value={engineer}>{engineer}</option>)}
     </select>
    </label>
    <label>Reason for creating job
     <input required maxLength={500} value={reasons} onChange={e=>setReasons(e.target.value)}/>
    </label>
    <button disabled={busy||!orderId||!siteAddress.trim()||!scheduledAt||!assignedTo||!reasons.trim()}>Create installation job</button>
   </form>}
  </section>}
  <section aria-label="Installation job register">
   <h3>Installation jobs</h3>
   {currentJobs.length===0?<p>No installation jobs are assigned to your account.</p>:currentJobs.map(job=><article key={job.id} className="installation-job">
    <h4>Installation #{job.id} · Order {job.orderId}</h4>
    <p><strong>{job.status.replaceAll('_',' ')}</strong> · Assigned to {job.assignedTo}</p>
    <p>{job.siteAddress}</p><p>Scheduled: {localDateTime(job.scheduledAt)}</p>
    <button type="button" className="secondary" disabled={busy} onClick={()=>void showDetails(job.id)}>View progress history</button>
    {details[job.id]&&<ol>{details[job.id].events.map(event=><li key={event.id}>
      <strong>{event.type.replaceAll('_',' ')}</strong> · {event.status.replaceAll('_',' ')} · {event.note}
      <small> {localDateTime(event.occurredAt)}</small>
    </li>)}</ol>}
    {canAssign&&job.status!=='COMPLETED'&&job.status!=='CANCELLED'&&<form onSubmit={e=>{e.preventDefault();void changeAssignment(job);}}>
     <h5>Reassign or reschedule</h5>
     <label>Engineer
      <select required value={assignments[job.id]?.assignedTo??job.assignedTo}
       onChange={e=>setAssignments(current=>({...current,[job.id]:{assignedTo:e.target.value,scheduledAt:current[job.id]?.scheduledAt??job.scheduledAt.slice(0,16),reason:current[job.id]?.reason??''}}))}>
       {engineers.map(engineer=><option key={engineer} value={engineer}>{engineer}</option>)}
      </select>
     </label>
     <label>New schedule
      <input required type="datetime-local" value={assignments[job.id]?.scheduledAt??job.scheduledAt.slice(0,16)}
       onChange={e=>setAssignments(current=>({...current,[job.id]:{assignedTo:current[job.id]?.assignedTo??job.assignedTo,scheduledAt:e.target.value,reason:current[job.id]?.reason??''}}))}/>
     </label>
     <label>Reason
      <input required maxLength={500} value={assignments[job.id]?.reason??''}
       onChange={e=>setAssignments(current=>({...current,[job.id]:{assignedTo:current[job.id]?.assignedTo??job.assignedTo,scheduledAt:current[job.id]?.scheduledAt??job.scheduledAt.slice(0,16),reason:e.target.value}}))}/>
     </label>
     <button className="secondary" disabled={busy||!(assignments[job.id]?.reason??'').trim()}>Save assignment</button>
    </form>}
    {canUpdate&&job.status!=='COMPLETED'&&job.status!=='CANCELLED'&&<form onSubmit={e=>{e.preventDefault();void changeStatus(job,job.status==='ASSIGNED'?'IN_PROGRESS':'COMPLETED');}}>
     <h5>Record installation progress</h5>
     <label>Customer-visible progress note
      <textarea required rows={2} maxLength={1000} value={notes[job.id]??''}
       onChange={e=>setNotes(current=>({...current,[job.id]:e.target.value}))}/>
     </label>
     <button disabled={busy||!(notes[job.id]??'').trim()||job.status==='IN_PROGRESS'&&!canCommission}>
      {job.status==='ASSIGNED'?'Start installation':'Commission installation'}
     </button>
    </form>}
    {canAssign&&job.status!=='COMPLETED'&&job.status!=='CANCELLED'&&<button type="button" className="secondary" disabled={busy||!(notes[job.id]??'').trim()}
      onClick={()=>void changeStatus(job,'CANCELLED')}>Cancel installation</button>}
   </article>)}
  </section>
  <button type="button" className="secondary" disabled={busy} onClick={()=>void refresh()}>Refresh installations</button>
 </section>;
}
