import {useCallback,useEffect,useState,type FormEvent} from 'react';
import {vendorApi} from './api';
import {useSession} from './session';

type RequestRow={id:number;orderId:string;installationId:number|null;subject:string;description:string;priority:string;status:string;assignedTo:string|null;createdAt:string;updatedAt:string;resolvedAt:string|null;version:number};
type EventRow={id:number;eventType:string;fromStatus:string|null;toStatus:string;note:string;customerVisible:boolean;actorType:string;actorId:string;occurredAt:string};
type Detail={serviceRequest:RequestRow;events:EventRow[]};
type EligibleOrder={orderId:string;sellerName:string;orderedAt:string|null};

export default function VendorServiceRequests(){
 const {account}=useSession();
 const permissions=new Set(account?.permissions??[]);
 const canCreate=permissions.has('SERVICE_CREATE'),canAssign=permissions.has('SERVICE_ASSIGN');
 const canUpdate=permissions.has('SERVICE_UPDATE'),canResolve=permissions.has('SERVICE_RESOLVE');
 const [rows,setRows]=useState<RequestRow[]>([]),[orders,setOrders]=useState<EligibleOrder[]>([]),[assignees,setAssignees]=useState<string[]>([]);
 const [details,setDetails]=useState<Record<number,Detail>>({});
 const [orderId,setOrderId]=useState(''),[subject,setSubject]=useState(''),[description,setDescription]=useState(''),[priority,setPriority]=useState('NORMAL');
 const [assignments,setAssignments]=useState<Record<number,{assignedTo:string;reason:string}>>({});
 const [notes,setNotes]=useState<Record<number,string>>({}),[nextStatuses,setNextStatuses]=useState<Record<number,string>>({});
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');

 const refresh=useCallback(async()=>{
  setBusy(true);setError('');
  try{
   const [requests,eligible,staff]=await Promise.all([
    vendorApi('/service-requests') as Promise<RequestRow[]>,
    canCreate?vendorApi('/service-requests/orders') as Promise<EligibleOrder[]>:Promise.resolve([]),
    canAssign?vendorApi('/service-requests/assignees') as Promise<string[]>:Promise.resolve([])
   ]);
   setRows(requests);setOrders(eligible);setAssignees(staff);
  }catch(e){setError((e as Error).message||'Service requests could not be loaded.');}
  finally{setBusy(false);}
 },[canCreate,canAssign]);
 useEffect(()=>{void refresh();},[refresh,account?.tenantId,account?.username]);

 async function create(event:FormEvent){
  event.preventDefault();if(!orderId||!subject.trim()||!description.trim())return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi('/service-requests','POST',{orderId,subject:subject.trim(),description:description.trim(),priority});
   setOrderId('');setSubject('');setDescription('');setPriority('NORMAL');setNotice('Service request created.');await refresh();
  }catch(e){setError((e as Error).message||'Service request was not created.');}
  finally{setBusy(false);}
 }
 async function loadDetail(id:number){
  setError('');
  try{const detail=await vendorApi('/service-requests/'+id) as Detail;setDetails(value=>({...value,[id]:detail}));}
  catch(e){setError((e as Error).message||'Service request history could not be loaded.');}
 }
 async function assign(row:RequestRow){
  const value=assignments[row.id];if(!value?.assignedTo||!value.reason.trim())return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi(`/service-requests/${row.id}/assignment`,'PATCH',{expectedVersion:row.version,assignedTo:value.assignedTo,reason:value.reason.trim()});
   setNotice('Service request assigned.');setAssignments(v=>({...v,[row.id]:{...value,reason:''}}));await refresh();await loadDetail(row.id);
  }catch(e){setError((e as Error).message||'Assignment was not saved.');}
  finally{setBusy(false);}
 }
 async function update(row:RequestRow){
  const note=(notes[row.id]??'').trim(),status=nextStatuses[row.id];if(!note||!status)return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi(`/service-requests/${row.id}/status`,'PATCH',{expectedVersion:row.version,status,note,customerVisible:true});
   setNotice('Service request updated.');setNotes(v=>({...v,[row.id]:''}));await refresh();await loadDetail(row.id);
  }catch(e){setError((e as Error).message||'Service request was not updated.');}
  finally{setBusy(false);}
 }
 const nextOptions=(status:string)=>{
  if(status==='OPEN')return ['TRIAGED'];
  if(status==='TRIAGED')return ['IN_PROGRESS'];
  if(status==='ASSIGNED')return ['IN_PROGRESS'];
  if(status==='IN_PROGRESS')return ['WAITING_CUSTOMER','WAITING_PARTS','RESOLVED'];
  if(status==='WAITING_CUSTOMER'||status==='WAITING_PARTS')return ['IN_PROGRESS','RESOLVED'];
  if(status==='RESOLVED')return ['CLOSED'];
  return [];
 };
 return <section className="vendor-service-requests" aria-labelledby="vendor-service-requests-title" aria-busy={busy}>
  <header><span className="eyebrow">Customer Management</span><h2 id="vendor-service-requests-title">Service requests</h2>
   <p>Manage customer support cases linked to paid orders and their installation history.</p></header>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  {canCreate&&<section aria-label="Create service request"><h3>Open a service case</h3>
   {orders.length===0?<p>No eligible paid customer orders are available.</p>:<form onSubmit={create}>
    <label>Customer order<select required value={orderId} onChange={e=>setOrderId(e.target.value)}><option value="">Select a paid order</option>{orders.map(o=><option key={o.orderId} value={o.orderId}>{o.orderId} · {o.sellerName}</option>)}</select></label>
    <label>Subject<input required maxLength={160} value={subject} onChange={e=>setSubject(e.target.value)}/></label>
    <label>Issue details<textarea required rows={3} maxLength={2000} value={description} onChange={e=>setDescription(e.target.value)}/></label>
    <label>Priority<select value={priority} onChange={e=>setPriority(e.target.value)}><option value="LOW">Low</option><option value="NORMAL">Normal</option><option value="HIGH">High</option><option value="URGENT">Urgent</option></select></label>
    <button disabled={busy||!orderId||!subject.trim()||!description.trim()}>Create service request</button>
   </form>}
  </section>}
  <section aria-label="Service request register"><h3>Service request register</h3>
   {rows.length===0?<p>No service requests are assigned to or managed by this account.</p>:rows.map(row=><article key={row.id} className="service-request">
    <h4>Case #{row.id} · {row.subject}</h4><p><strong>{row.status.replaceAll('_',' ')}</strong> · {row.priority} · Order {row.orderId}</p>
    <p>{row.description}</p>{row.installationId&&<p>Installation #{row.installationId}</p>}
    <button type="button" className="secondary" disabled={busy} onClick={()=>void loadDetail(row.id)}>View case history</button>
    {details[row.id]&&<ol>{details[row.id].events.map(ev=><li key={ev.id}><strong>{ev.toStatus.replaceAll('_',' ')}</strong> · {ev.note}<small> {new Date(ev.occurredAt).toLocaleString()}{ev.customerVisible?' · Customer visible':' · Internal'}</small></li>)}</ol>}
    {canAssign&&!['RESOLVED','CLOSED'].includes(row.status)&&<form onSubmit={e=>{e.preventDefault();void assign(row);}}>
     <h5>Assign service employee</h5><label>Assignee<select required value={assignments[row.id]?.assignedTo??row.assignedTo??''} onChange={e=>setAssignments(v=>({...v,[row.id]:{assignedTo:e.target.value,reason:v[row.id]?.reason??''}}))}><option value="">Select employee</option>{assignees.map(a=><option key={a} value={a}>{a}</option>)}</select></label>
     <label>Assignment reason<input required maxLength={500} value={assignments[row.id]?.reason??''} onChange={e=>setAssignments(v=>({...v,[row.id]:{assignedTo:v[row.id]?.assignedTo??row.assignedTo??'',reason:e.target.value}}))}/></label>
     <button className="secondary" disabled={busy||!(assignments[row.id]?.assignedTo??row.assignedTo)||!(assignments[row.id]?.reason??'').trim()}>Save assignment</button>
    </form>}
    {(canUpdate||canResolve)&&!['CLOSED'].includes(row.status)&&<form onSubmit={e=>{e.preventDefault();void update(row);}}>
     <h5>Update case</h5><label>Next status<select required value={nextStatuses[row.id]??''} onChange={e=>setNextStatuses(v=>({...v,[row.id]:e.target.value}))}><option value="">Select next status</option>{nextOptions(row.status).filter(s=>s!=='RESOLVED'||canResolve).map(s=><option key={s} value={s}>{s.replaceAll('_',' ')}</option>)}</select></label>
     <label>Customer-visible update<textarea required rows={2} maxLength={1000} value={notes[row.id]??''} onChange={e=>setNotes(v=>({...v,[row.id]:e.target.value}))}/></label>
     <button disabled={busy||!nextStatuses[row.id]||!(notes[row.id]??'').trim()}>Save case update</button>
    </form>}
   </article>)}
  </section>
  <button type="button" className="secondary" disabled={busy} onClick={()=>void refresh()}>Refresh service requests</button>
 </section>;
}
