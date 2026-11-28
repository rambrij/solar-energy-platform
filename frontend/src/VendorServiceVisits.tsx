import {useCallback,useEffect,useState,type FormEvent} from 'react';
import {vendorApi} from './api';
import {useSession} from './session';
type CaseRow={id:number;subject:string;status:string};
type Visit={id:number;caseType:'WARRANTY_CLAIM'|'SERVICE_REQUEST';caseId:number;orderId:string;caseSubject:string;siteAddress:string;scheduledAt:string;status:string;assignedTo:string;version:number};
type Event={id:number;eventType:string;toStatus:string;note:string;reason:string;customerVisible:boolean;actorUsername:string;occurredAt:string};
type Detail={visit:Visit;events:Event[]};
type SlaPolicy={caseType:'SERVICE_REQUEST'|'WARRANTY_CLAIM';priority:string;responseTargetMinutes:number|null;resolutionTargetMinutes:number|null;configured:boolean;version:number};
type SlaCase={caseType:'SERVICE_REQUEST'|'WARRANTY_CLAIM';caseId:number;orderId:string;subject:string;priority:string;caseStatus:string;assignedTo:string|null;openedAt:string;responseTargetMinutes:number|null;resolutionTargetMinutes:number|null;responseDueAt:string|null;resolutionDueAt:string|null;firstResponseAt:string|null;closedAt:string|null;responseBreachedAt:string|null;resolutionBreachedAt:string|null;responseState:string;resolutionState:string};
type EscalationPolicy={id:number;caseType:'SERVICE_REQUEST'|'WARRANTY_CLAIM';priority:string;breachType:'RESPONSE'|'RESOLUTION';escalationLevel:number;afterMinutes:number;recipientRoleKey:string;enabled:boolean;version:number};
type Escalation={id:number;slaId:number;caseType:'SERVICE_REQUEST'|'WARRANTY_CLAIM';caseId:number;breachType:'RESPONSE'|'RESOLUTION';escalationLevel:number;recipientRoleKey:string;status:'OPEN'|'ACKNOWLEDGED';escalatedAt:string;acknowledgedBy:string|null;acknowledgedAt:string|null;acknowledgementNote:string|null};
const stamp=(v:string)=>new Date(v).toLocaleString();
const transitions=(s:string)=>s==='SCHEDULED'?['IN_PROGRESS','CANCELLED']:s==='IN_PROGRESS'?['COMPLETED','CANCELLED']:[];
export default function VendorServiceVisits(){
 const {account}=useSession(),grants=new Set(account?.permissions??[]);
 const canAssign=grants.has('SERVICE_ASSIGN'),canUpdate=grants.has('SERVICE_UPDATE'),canResolve=grants.has('SERVICE_RESOLVE');
 const [visits,setVisits]=useState<Visit[]>([]),[claims,setClaims]=useState<CaseRow[]>([]),[requests,setRequests]=useState<CaseRow[]>([]);
 const [slaCases,setSlaCases]=useState<SlaCase[]>([]),[slaPolicies,setSlaPolicies]=useState<SlaPolicy[]>([]);
 const [escalationPolicies,setEscalationPolicies]=useState<EscalationPolicy[]>([]),[escalations,setEscalations]=useState<Escalation[]>([]);
 const [escalationDraft,setEscalationDraft]=useState({caseType:'SERVICE_REQUEST',priority:'NORMAL',breachType:'RESPONSE',escalationLevel:'1',afterMinutes:'0',recipientRoleKey:'SERVICE_MANAGER',enabled:true,reason:''});
 const [ackNotes,setAckNotes]=useState<Record<number,string>>({});
 const [slaDrafts,setSlaDrafts]=useState<Record<string,{response:string;resolution:string;reason:string}>>({});
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
   const [v,c,r,a,sla,policies,escalationRules,escalationQueue]=await Promise.all([
    vendorApi('/service-visits') as Promise<Visit[]>,vendorApi('/warranty-claims') as Promise<CaseRow[]>,
    vendorApi('/service-requests') as Promise<CaseRow[]>,canAssign?vendorApi('/service-visits/assignees') as Promise<string[]>:Promise.resolve([]),
    vendorApi('/service-sla') as Promise<SlaCase[]>,vendorApi('/service-sla/policies') as Promise<SlaPolicy[]>,
    vendorApi('/service-sla/escalation-policies') as Promise<EscalationPolicy[]>,vendorApi('/service-sla/escalations') as Promise<Escalation[]>
   ]);
   setVisits(v);setClaims(c);setRequests(r);setAssignees(a);setAssignee(x=>x||a[0]||'');
   setSlaCases(sla);setSlaPolicies(policies);setEscalationPolicies(escalationRules);setEscalations(escalationQueue);
  }catch(e){setError((e as Error).message||'Engineer visits could not be loaded.');}
  finally{setBusy(false);}
 },[canAssign]);
 useEffect(()=>{void refresh();},[refresh,account?.tenantId,account?.username]);
 async function detail(id:number){
  try{
   const result=await vendorApi(`/service-visits/${encodeURIComponent(id)}`) as Detail;
   setDetails(v=>({...v,[id]:result}));
  }catch(e){setError((e as Error).message||'Visit report could not be loaded.');}
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
   await vendorApi(`/service-visits/${encodeURIComponent(v.id)}/assignment`,'PATCH',{expectedVersion:v.version,assignedTo:a.assignee,scheduledAt:new Date(a.schedule).toISOString(),reason:a.reason.trim()});
   setNotice('Visit assignment and schedule saved.');await refresh();await detail(v.id);
  }catch(e){setError((e as Error).message||'Assignment could not be saved.');}
  finally{setBusy(false);}
 }
 async function saveStatus(v:Visit){
  const status=next[v.id],note=(progress[v.id]??'').trim();if(!status||!note)return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi(`/service-visits/${encodeURIComponent(v.id)}/status`,'PATCH',{expectedVersion:v.version,status,note});
   setProgress(x=>({...x,[v.id]:''}));setNotice('Visit status updated.');await refresh();await detail(v.id);
  }catch(e){setError((e as Error).message||'Visit status could not be updated.');}
  finally{setBusy(false);}
 }
 async function savePolicy(p:SlaPolicy){
  const key=`${p.caseType}:${p.priority}`,draft=slaDrafts[key];
  if(!draft?.response||!draft.resolution||!draft.reason.trim())return;
  const responseTargetMinutes=Number(draft.response),resolutionTargetMinutes=Number(draft.resolution);
  if(!Number.isInteger(responseTargetMinutes)||!Number.isInteger(resolutionTargetMinutes)
      ||responseTargetMinutes<1||resolutionTargetMinutes<responseTargetMinutes||resolutionTargetMinutes>525600)return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi('/service-sla/policies','PUT',{caseType:p.caseType,priority:p.priority,responseTargetMinutes,resolutionTargetMinutes,reason:draft.reason.trim()});
   setSlaDrafts(x=>({...x,[key]:{response:'',resolution:'',reason:''}}));
   setNotice('SLA policy saved. New and previously untracked open cases now have a target snapshot.');await refresh();
  }catch(e){setError((e as Error).message||'SLA policy could not be saved.');}
  finally{setBusy(false);}
 }

 async function saveEscalationPolicy(e:FormEvent){
  e.preventDefault();setBusy(true);setError('');setNotice('');
  try{
   await vendorApi('/service-sla/escalation-policies','PUT',{
    caseType:escalationDraft.caseType,priority:escalationDraft.caseType==='WARRANTY_CLAIM'?'NORMAL':escalationDraft.priority,
    breachType:escalationDraft.breachType,escalationLevel:Number(escalationDraft.escalationLevel),
    afterMinutes:Number(escalationDraft.afterMinutes),recipientRoleKey:escalationDraft.recipientRoleKey,enabled:escalationDraft.enabled,reason:escalationDraft.reason.trim()
   });
   setNotice('SLA escalation policy saved.');await refresh();
  }catch(x){setError((x as Error).message||'Escalation policy could not be saved.');}
  finally{setBusy(false);}
 }
 async function acknowledgeEscalation(id:number){
  const note=(ackNotes[id]??'').trim();if(!note)return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi(`/service-sla/escalations/${encodeURIComponent(id)}/acknowledge`,'POST',{note});
   setAckNotes(x=>({...x,[id]:''}));setNotice('Escalation acknowledged.');await refresh();
  }catch(x){setError((x as Error).message||'Escalation could not be acknowledged.');}
  finally{setBusy(false);}
 }
 async function saveNote(v:Visit){
  const note=(report[v.id]??'').trim();if(!note)return;
  setBusy(true);setError('');setNotice('');
  try{
   await vendorApi(`/service-visits/${encodeURIComponent(v.id)}/notes`,'POST',{expectedVersion:v.version,note,customerVisible:visible[v.id]??true});
   setReport(x=>({...x,[v.id]:''}));setNotice('Visit report note saved.');await refresh();await detail(v.id);
  }catch(e){setError((e as Error).message||'Visit note could not be saved.');}
  finally{setBusy(false);}
 }
 return <section className="service-visits" aria-labelledby="service-visits-title" aria-busy={busy}>
  <header><span className="eyebrow">Customer Management</span><h2 id="service-visits-title">Engineer visits</h2><p>One visit workflow for warranty claims and general service requests.</p></header>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  <section aria-label="Service level agreement dashboard">
   <h3>Service level agreements</h3>
   <p>Response and resolution clocks use elapsed calendar time. Targets are configured by your service manager; unconfigured cases are not assigned invented deadlines.</p>
   {slaCases.length===0?<p>No active service cases require SLA monitoring.</p>:<div className="service-sla-register">
    {slaCases.map(s=><article key={`${s.caseType}:${s.caseId}`}>
     <h4>{s.caseType==='WARRANTY_CLAIM'?'Warranty claim':'Service request'} #{s.caseId} · {s.subject}</h4>
     <p>Order {s.orderId} · {s.priority} · {s.caseStatus.replaceAll('_',' ')}{s.assignedTo?' · '+s.assignedTo:''}</p>
     <p>First response: <strong data-state={s.responseState}>{s.responseState.replaceAll('_',' ')}</strong>{s.responseDueAt?' · Due '+stamp(s.responseDueAt):' · No target configured'}</p>
     <p>Resolution: <strong data-state={s.resolutionState}>{s.resolutionState.replaceAll('_',' ')}</strong>{s.resolutionDueAt?' · Due '+stamp(s.resolutionDueAt):' · No target configured'}</p>
    </article>)}
   </div>}
   <h4>SLA policies</h4>
   {slaPolicies.map(p=>{
    const key=`${p.caseType}:${p.priority}`,draft=slaDrafts[key]??{response:p.responseTargetMinutes?.toString()??'',resolution:p.resolutionTargetMinutes?.toString()??'',reason:''};
    return <article key={key}>
     <h5>{p.caseType==='WARRANTY_CLAIM'?'Warranty claims':'Service requests'} · {p.priority}</h5>
     <p>{p.configured?'Configured · changes apply to new clocks; active case snapshots are preserved.':'Not configured · cases remain untracked until a policy is saved.'}</p>
     {canAssign?<form onSubmit={e=>{e.preventDefault();void savePolicy(p);}}>
      <label>Response target minutes<input required type="number" min="1" max="525600" value={draft.response} onChange={e=>setSlaDrafts(x=>({...x,[key]:{...draft,response:e.target.value}}))}/></label>
      <label>Resolution target minutes<input required type="number" min="1" max="525600" value={draft.resolution} onChange={e=>setSlaDrafts(x=>({...x,[key]:{...draft,resolution:e.target.value}}))}/></label>
      <label>Reason for policy change<input required maxLength={500} value={draft.reason} onChange={e=>setSlaDrafts(x=>({...x,[key]:{...draft,reason:e.target.value}}))}/></label>
      <button disabled={busy||!draft.response||!draft.resolution||Number(draft.resolution)<Number(draft.response)||!draft.reason.trim()}>Save SLA policy</button>
     </form>:<p>{p.configured?`Response ${p.responseTargetMinutes} min · Resolution ${p.resolutionTargetMinutes} min`:'Awaiting service manager configuration'}</p>}
    </article>;
   })}

   <h4>Escalation policies</h4>
   <p>Rules create durable, idempotent escalation records after a response or resolution breach. Outbox events are emitted for configured recipient roles.</p>
   {escalationPolicies.map(p=><article key={p.id}>
    <strong>{p.caseType.replaceAll('_',' ')} · {p.priority} · {p.breachType} · Level {p.escalationLevel}</strong>
    <p>{p.enabled?'Enabled':'Disabled'} · After {p.afterMinutes} minutes · Recipient role: {p.recipientRoleKey}</p>
   </article>)}
   {canAssign&&<form onSubmit={saveEscalationPolicy}>
    <h5>Add or update escalation level</h5>
    <label>Case type<select value={escalationDraft.caseType} onChange={e=>setEscalationDraft(x=>({...x,caseType:e.target.value,priority:e.target.value==='WARRANTY_CLAIM'?'NORMAL':x.priority}))}><option value="SERVICE_REQUEST">Service request</option><option value="WARRANTY_CLAIM">Warranty claim</option></select></label>
    <label>Priority<select disabled={escalationDraft.caseType==='WARRANTY_CLAIM'} value={escalationDraft.priority} onChange={e=>setEscalationDraft(x=>({...x,priority:e.target.value}))}>{['LOW','NORMAL','HIGH','URGENT'].map(v=><option key={v}>{v}</option>)}</select></label>
    <label>Breach type<select value={escalationDraft.breachType} onChange={e=>setEscalationDraft(x=>({...x,breachType:e.target.value}))}><option value="RESPONSE">Response</option><option value="RESOLUTION">Resolution</option></select></label>
    <label>Escalation level<input type="number" min="1" max="5" required value={escalationDraft.escalationLevel} onChange={e=>setEscalationDraft(x=>({...x,escalationLevel:e.target.value}))}/></label>
    <label>Minutes after breach<input type="number" min="0" max="525600" required value={escalationDraft.afterMinutes} onChange={e=>setEscalationDraft(x=>({...x,afterMinutes:e.target.value}))}/></label>
    <label>Recipient role<select value={escalationDraft.recipientRoleKey} onChange={e=>setEscalationDraft(x=>({...x,recipientRoleKey:e.target.value}))}><option value="VENDOR_ADMIN">Vendor Admin</option><option value="SERVICE_MANAGER">Service Manager</option><option value="SERVICE_LEAD">Service Lead</option></select></label>
    <label>Reason for policy change<input required maxLength={500} value={escalationDraft.reason} onChange={e=>setEscalationDraft(x=>({...x,reason:e.target.value}))}/></label>
    <label><input type="checkbox" checked={escalationDraft.enabled} onChange={e=>setEscalationDraft(x=>({...x,enabled:e.target.checked}))}/> Enabled</label>
    <button disabled={busy||!escalationDraft.reason.trim()}>Save escalation policy</button>
   </form>}
   <h4>Escalation queue</h4>
   {escalations.length===0?<p>No SLA escalations have been generated.</p>:escalations.map(e=><article key={e.id}>
    <strong>{e.status} · {e.caseType.replaceAll('_',' ')} #{e.caseId} · {e.breachType} · Level {e.escalationLevel}</strong>
    <p>Recipient role: {e.recipientRoleKey} · Escalated {stamp(e.escalatedAt)}</p>
    {e.status==='OPEN'&&canUpdate&&<form onSubmit={event=>{event.preventDefault();void acknowledgeEscalation(e.id);}}>
     <label>Acknowledgement note<textarea required maxLength={500} rows={2} value={ackNotes[e.id]??''} onChange={event=>setAckNotes(x=>({...x,[e.id]:event.target.value}))}/></label>
     <button disabled={busy||!(ackNotes[e.id]??'').trim()}>Acknowledge escalation</button>
    </form>}
    {e.status==='ACKNOWLEDGED'&&<p>Acknowledged by {e.acknowledgedBy} · {e.acknowledgementNote}</p>}
   </article>)}
  </section>
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
