import {useCallback,useEffect,useState,type FormEvent} from 'react';
import {vendorApi} from './api';
import {useSession} from './session';
type CaseRow={id:number;subject:string;status:string};
type Visit={id:number;caseType:'WARRANTY_CLAIM'|'SERVICE_REQUEST';caseId:number;orderId:string;caseSubject:string;siteAddress:string;scheduledAt:string;status:string;assignedTo:string;version:number};
type Event={id:number;eventType:string;toStatus:string;note:string;reason:string;customerVisible:boolean;actorUsername:string;occurredAt:string};
type Detail={visit:Visit;events:Event[]};
const stamp=(v:string)=>new Date(v).toLocaleString();
const transitions=(s:string)=>s==='SCHEDULED'?['IN_PROGRESS','CANCELLED']:s==='IN_PROGRESS'?['COMPLETED','CANCELLED']:[];
export default function VendorServiceVisits(){
 const {account}=useSession(),grants=new Set(account?.permissions??[]);
 const canAssign=grants.has('SERVICE_ASSIGN'),canUpdate=grants.has('SERVICE_UPDATE'),canResolve=grants.has('SERVICE_RESOLVE');
 const [visits,setVisits]=useState<Visit[]>([]),[claims,setClaims]=useState<CaseRow[]>([]),[requests,setRequests]=useState<CaseRow[]>([]);
 const [assignees,setAssignees]=useState<string[]>([]),[details,setDetails]=useState<Record<number,Detail>>({});
 const [caseType,setCaseType]=useState<'WARRANTY_CLAIM'|'SERVICE_REQUEST'>('SERVICE_REQUEST'),[caseId,setCaseId]=useState('');
 const [site,setSite]=useState(''),[schedule,setSchedule]=useState(''),[assignee,setAssignee]=useState(''),[reason,setReason]=useState('');
 const [reassign,setReassign]=useState<Record<number,{assignee:string;schedule:string;reason:string}>>({});
 const [next,setNext]=useState<Record<number,string>>({}),[progress,setProgress]=useState<Record<number,string>>({}),[report,setReport]=useState<Record<number,string>>({});
 const [visible,setVisible]=useState<Record<number,boolean>>({}),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const cases=caseType==='WARRANTY_CLAIM'?claims:requests;
 const refresh=useCallback(async()=>{
  setBusy(true);setError('');
  try{
   const [v,c,r,a]=await Promise.all([
    vendorApi('/service-visits') as Promise<Visit[]>,vendorApi('/warranty-claims') as Promise<CaseRow[]>,
    vendorApi('/service-requests') as Promise<CaseRow[]>,canAssign?vendorApi('/service-visits/assignees') as Promise<string[]>:Promise.resolve([])
   ]);
   setVisits(v);setClaims(c);setRequests(r);setAssignees(a);setAssignee(x=>x||a[0]||'');
  }catch(e){setError((e as Error).message||'Engineer visits could not be loaded.');}
  finally{setBusy(false);}
 },[canAssign]);
 useEffect(()=>{void refresh();},[refresh,account?.tenantId,account?.username]);
 async function detail(id:number){
  try{setDetails(v=>({...v,[id]:await vendorApi('/service-visits/'+id) as Detail}));}
  catch(e){setError((e as Error).message||'Visit report could not be loaded.');}
 }
 async function create(e:FormEvent){
  e.preventDefault();if(!caseId||!site.trim()||!schedule||!assignee||!reason.trim())return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi('/service-visits','POST',{caseType,caseId:Number(caseId),siteAddress:site.trim(),scheduledAt:new Date(schedule).toISOString(),assignedTo:assignee,reason:reason.trim()});
   setCaseId('');setSite('');setSchedule('');setReason('');setNotice('Engineer visit scheduled.');await refresh();
  }catch(x){setError((x as Error).message||'Visit could not be scheduled.');}
  finally{setBusy(false);}
 }
 async function saveAssignment(v:Visit){
  const a=reassign[v.id];if(!a?.assignee||!a.schedule||!a.reason.trim())return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi('/service-visits/'+v.id+'/assignment','PATCH',{expectedVersion:v.version,assignedTo:a.assignee,scheduledAt:new Date(a.schedule).toISOString(),reason:a.reason.trim()});
   setNotice('Visit assignment and schedule saved.');await refresh();await detail(v.id);
  }catch(e){setError((e as Error).message||'Assignment could not be saved.');}
  finally{setBusy(false);}
 }
 async function saveStatus(v:Visit){
  const status=next[v.id],note=(progress[v.id]??'').trim();if(!status||!note)return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi('/service-visits/'+v.id+'/status','PATCH',{expectedVersion:v.version,status,note});
   setProgress(x=>({...x,[v.id]:''}));setNotice('Visit status updated.');await refresh();await detail(v.id);
  }catch(e){setError((e as Error).message||'Visit status could not be updated.');}
  finally{setBusy(false);}
 }
 async function saveNote(v:Visit){
  const note=(report[v.id]??'').trim();if(!note)return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi('/service-visits/'+v.id+'/notes','POST',{expectedVersion:v.version,note,customerVisible:visible[v.id]??true});
   setReport(x=>({...x,[v.id]:''}));setNotice('Visit report note saved.');await refresh();await detail(v.id);
  }catch(e){setError((e as Error).message||'Visit note could not be saved.');}
  finally{setBusy(false);}
 }
 return <section className="service-visits" aria-labelledby="service-visits-title" aria-busy={busy}>
  <header><span className="eyebrow">Customer Management</span><h2 id="service-visits-title">Engineer visits</h2><p>One visit workflow for warranty claims and general service requests.</p></header>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  {canAssign&&<section aria-label="Schedule engineer visit"><h3>Schedule a visit</h3><form onSubmit={create}>
   <label>Case type<select value={caseType} onChange={e=>{setCaseType(e.target.value as typeof caseType);setCaseId('');}}><option value="SERVICE_REQUEST">General service request</option><option value="WARRANTY_CLAIM">Warranty claim</option></select></label>
   <label>Case<select required value={caseId} onChange={e=>setCaseId(e.target.value)}><option value="">Select case</option>{cases.map(c=><option key={c.id} value={c.id}>#{c.id} · {c.subject} · {c.status.replaceAll('_',' ')}</option>)}</select></label>
   <label>Site address<textarea required rows={2} maxLength={1000} value={site} onChange={e=>setSite(e.target.value)}/></label>
   <label>Schedule<input required type="datetime-local" value={schedule} onChange={e=>setSchedule(e.target.value)}/></label>
   <label>Engineer<select required value={assignee} onChange={e=>setAssignee(e.target.value)}><option value="">Select engineer</option>{assignees.map(a=><option key={a}>{a}</option>)}</select></label>
   <label>Reason<input required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>
   <button disabled={busy||!caseId||!site.trim()||!schedule||!assignee||!reason.trim()}>Schedule visit</button>
  </form></section>}
  <section aria-label="Engineer visit register"><h3>Visit register</h3>
   {visits.length===0?<p>No engineer visits are available to this account.</p>:visits.map(v=><article key={v.id}>
    <h4>Visit #{v.id} · {v.caseType==='WARRANTY_CLAIM'?'Warranty claim':'Service request'} #{v.caseId}</h4>
    <p><strong>{v.status.replaceAll('_',' ')}</strong> · Order {v.orderId} · {v.caseSubject}</p><p>{v.siteAddress} · {stamp(v.scheduledAt)} · {v.assignedTo}</p>
    <button type="button" className="secondary" disabled={busy} onClick={()=>void detail(v.id)}>View visit report</button>
    {details[v.id]&&<ol>{details[v.id].events.map(ev=><li key={ev.id}><strong>{ev.eventType.replaceAll('_',' ')}</strong> · {ev.note}<small> {stamp(ev.occurredAt)} · {ev.customerVisible?'Customer visible':'Internal'}{ev.reason?' · '+ev.reason:''}</small></li>)}</ol>}
    {canAssign&&!['COMPLETED','CANCELLED'].includes(v.status)&&<form onSubmit={e=>{e.preventDefault();void saveAssignment(v);}}>
     <h5>Reassign or reschedule</h5>
     <label>Engineer<select value={reassign[v.id]?.assignee??v.assignedTo} onChange={e=>setReassign(x=>({...x,[v.id]:{assignee:e.target.value,schedule:x[v.id]?.schedule??v.scheduledAt.slice(0,16),reason:x[v.id]?.reason??''}}))}>{assignees.map(a=><option key={a}>{a}</option>)}</select></label>
     <label>New schedule<input type="datetime-local" required value={reassign[v.id]?.schedule??v.scheduledAt.slice(0,16)} onChange={e=>setReassign(x=>({...x,[v.id]:{assignee:x[v.id]?.assignee??v.assignedTo,schedule:e.target.value,reason:x[v.id]?.reason??''}}))}/></label>
     <label>Reason<input required maxLength={500} value={reassign[v.id]?.reason??''} onChange={e=>setReassign(x=>({...x,[v.id]:{assignee:x[v.id]?.assignee??v.assignedTo,schedule:x[v.id]?.schedule??v.scheduledAt.slice(0,16),reason:e.target.value}}))}/></label>
     <button className="secondary" disabled={busy||!(reassign[v.id]?.reason??'').trim()}>Save assignment</button>
    </form>}
    {(canUpdate||canResolve||canAssign)&&!['COMPLETED','CANCELLED'].includes(v.status)&&<form onSubmit={e=>{e.preventDefault();void saveStatus(v);}}>
     <h5>Visit status</h5><label>Next status<select required value={next[v.id]??''} onChange={e=>setNext(x=>({...x,[v.id]:e.target.value}))}><option value="">Select status</option>{transitions(v.status).filter(s=>s==='COMPLETED'?canResolve:s==='CANCELLED'?canAssign:canUpdate).map(s=><option key={s}>{s}</option>)}</select></label>
     <label>Progress note<textarea required rows={2} maxLength={1000} value={progress[v.id]??''} onChange={e=>setProgress(x=>({...x,[v.id]:e.target.value}))}/></label>
     <button disabled={busy||!next[v.id]||!(progress[v.id]??'').trim()}>Update visit status</button>
    </form>}
    {canUpdate&&!['COMPLETED','CANCELLED'].includes(v.status)&&<form onSubmit={e=>{e.preventDefault();void saveNote(v);}}>
     <h5>Visit report note</h5><label>Report note<textarea required rows={2} maxLength={1000} value={report[v.id]??''} onChange={e=>setReport(x=>({...x,[v.id]:e.target.value}))}/></label>
     <label><input type="checkbox" checked={visible[v.id]??true} onChange={e=>setVisible(x=>({...x,[v.id]:e.target.checked}))}/> Share this note with the customer</label>
     <button className="secondary" disabled={busy||!(report[v.id]??'').trim()}>Save report note</button>
    </form>}
   </article>)}
  </section>
  <button type="button" className="secondary" disabled={busy} onClick={()=>void refresh()}>Refresh engineer visits</button>
 </section>;
}
