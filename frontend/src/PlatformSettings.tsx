import {useEffect,useState} from 'react';
import {platformApi,ApiError} from './api';
import {configProblem,supportProblem} from './platformRules';
import type {Tenant} from './PlatformTenants';
const message=(e:unknown,fallback:string)=>e instanceof ApiError?(e.message||`${fallback} (${e.status}).`):fallback;
type Entry={key:string;value:string|null;secret:boolean;version:number|null};

export function PlatformConfiguration({can}:{can:(p:string)=>boolean}){
 const [entries,setEntries]=useState<Entry[]>([]),[drafts,setDrafts]=useState<Record<string,string>>({}),[error,setError]=useState(''),[notice,setNotice]=useState(''),[tick,setTick]=useState(0);
 const view=can('PLATFORM_ANALYTICS_VIEW'),edit=can('PLATFORM_SUBSCRIPTION_ADJUST');
 useEffect(()=>{if(!view)return;let live=true;platformApi<Entry[]>('/configuration').then(rows=>{if(live)setEntries(rows);}).catch(e=>{if(live)setError(message(e,'Could not load configuration'));});return()=>{live=false;};},[view,tick]);
 async function save(entry:Entry){
  setError('');setNotice('');const value=drafts[entry.key]??'',problem=configProblem(entry.secret,value);if(problem){setError(problem);return;}
  try{await platformApi(`/configuration/${entry.key}`,{method:'PUT',body:{value:value.trim()}});setNotice(`${entry.key} saved.`);setDrafts(previous=>({...previous,[entry.key]:''}));setTick(v=>v+1);}catch(e){setError(message(e,'Configuration rejected'));}
 }
 return <section id="platform-config" aria-label="Configuration"><h2>Configuration</h2>
  <p className="bill-hint">Global platform and subscription-payment settings. Secrets are never stored or shown: secret keys take a reference (env:NAME or vault:path) and are always masked. Mock providers are refused in production.</p>
  {!view&&<p role="status">Your role cannot view configuration.</p>}
  <ul className="platform-config-list">{entries.map(entry=><li key={entry.key}><strong>{entry.key}</strong> {entry.secret&&<span className="badge">Secret reference</span>}<div>Current: <code>{entry.value??'not set'}</code>{entry.version?` · v${entry.version}`:''}</div>
   {edit&&<div className="platform-actions"><label>New value <input aria-label={`New value for ${entry.key}`} value={drafts[entry.key]??''} onChange={e=>setDrafts({...drafts,[entry.key]:e.target.value})} autoComplete="off"/></label><button type="button" className="secondary" aria-label={`Save ${entry.key}`} onClick={()=>void save(entry)}>Save</button></div>}</li>)}</ul>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
 </section>;
}

type Grant={id:string;tenantId:number;reason:string;ticket:string;expiresAt:string;revokedAt?:string|null;active:boolean};
export function SupportAccess({tenant,can}:{tenant?:Tenant;can:(p:string)=>boolean}){
 const [grants,setGrants]=useState<Grant[]>([]),[reason,setReason]=useState(''),[ticket,setTicket]=useState(''),[minutes,setMinutes]=useState('30'),[error,setError]=useState(''),[notice,setNotice]=useState(''),[tick,setTick]=useState(0),[entries,setEntries]=useState<{grantId:string;rows:{actor:string;action:string;resourceId:string;occurredAt:string}[]}>();
 const allowed=can('PLATFORM_SUPPORT_ACCESS');
 useEffect(()=>{if(!tenant||!allowed){setGrants([]);return;}let live=true;platformApi<Grant[]>(`/tenants/${tenant.id}/support-access`).then(rows=>{if(live)setGrants(rows);}).catch(e=>{if(live)setError(message(e,'Could not load grants'));});return()=>{live=false;};},[tenant?.id,allowed,tick]);
 async function run(action:()=>Promise<unknown>,done:string){setError('');setNotice('');try{await action();setNotice(done);setTick(v=>v+1);}catch(e){setError(message(e,'Support access request rejected'));}}
 return <section id="platform-support" aria-label="Support access"><h2>Support access</h2>
  <p className="bill-hint">Tenant-private data is not visible to the platform by default. A grant needs a purpose, a ticket and an expiry; it is read-only, limited to the tenant's operational audit trail, and every grant, read and denial is audited.</p>
  {!allowed?<p role="status">Your role does not include support access.</p>:!tenant?<p role="status">Select a tenant in the directory first.</p>:<>
   <h3>{tenant.name}</h3>
   <form className="platform-form" onSubmit={e=>{e.preventDefault();const m=Number(minutes),problem=supportProblem(reason,ticket,m);if(problem){setError(problem);return;}void run(()=>platformApi(`/tenants/${tenant.id}/support-access`,{body:{reason:reason.trim(),ticket:ticket.trim(),minutes:m}}),'Support access granted.').then(()=>{setReason('');setTicket('');});}}>
    <label>Purpose <input value={reason} onChange={e=>setReason(e.target.value)}/></label><label>Ticket / reference <input value={ticket} onChange={e=>setTicket(e.target.value)}/></label><label>Minutes (5-240) <input value={minutes} onChange={e=>setMinutes(e.target.value)} inputMode="numeric"/></label>
    <button>Grant time-limited access</button></form>
   <ul>{grants.map(grant=><li key={grant.id}><span className="badge">{grant.active?'ACTIVE':grant.revokedAt?'REVOKED':'EXPIRED'}</span> {grant.ticket} · {grant.reason} · until {new Date(grant.expiresAt).toLocaleString()}
    {grant.active&&<span className="platform-actions"><button type="button" className="secondary" onClick={()=>void run(async()=>{const read=await platformApi<{entries:{actor:string;action:string;resourceId:string;occurredAt:string}[]}>(`/support-access/${grant.id}/vendor-audit`);setEntries({grantId:grant.id,rows:read.entries});},'Tenant audit trail read; the access is recorded.')}>Read tenant audit trail</button><button type="button" className="danger" onClick={()=>void run(()=>platformApi(`/support-access/${grant.id}/revoke`,{body:{}}),'Access revoked.')}>Revoke</button></span>}</li>)}</ul>
   {entries&&<table className="data-table"><caption>Tenant audit trail (read-only)</caption><thead><tr><th scope="col">When</th><th scope="col">Actor</th><th scope="col">Action</th><th scope="col">Resource</th></tr></thead><tbody>{entries.rows.map((row,index)=><tr key={index}><td>{new Date(row.occurredAt).toLocaleString()}</td><td>{row.actor}</td><td>{row.action}</td><td>{row.resourceId}</td></tr>)}</tbody></table>}
  </>}
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
 </section>;
}

type AuditEvent={id:string;occurredAt:string;actor:string;action:string;targetType:string;targetId:string;tenantId?:number|null;reason?:string|null;details?:string|null;outcome:string};
export function PlatformAudit({can}:{can:(p:string)=>boolean}){
 const [events,setEvents]=useState<AuditEvent[]>([]),[total,setTotal]=useState(0),[action,setAction]=useState(''),[tenantId,setTenantId]=useState(''),[page,setPage]=useState(0),[error,setError]=useState('');
 const allowed=can('PLATFORM_AUDIT_VIEW'),size=15;
 useEffect(()=>{if(!allowed)return;let live=true;setError('');
  platformApi<{items:AuditEvent[];total:number}>(`/audit?page=${page}&size=${size}${action.trim()?`&action=${encodeURIComponent(action.trim())}`:''}${/^\d+$/.test(tenantId)?`&tenantId=${tenantId}`:''}`).then(r=>{if(live){setEvents(r.items);setTotal(r.total);}}).catch(e=>{if(live)setError(message(e,'Could not load the audit trail'));});
  return()=>{live=false;};},[allowed,action,tenantId,page]);
 return <section id="platform-audit" aria-label="Platform audit"><h2>Platform audit</h2>
  {!allowed?<p role="status">Your role does not include audit access.</p>:<>
   <div className="platform-filters"><label>Action <input value={action} onChange={e=>{setPage(0);setAction(e.target.value);}} placeholder="e.g. TENANT_SUSPENDED"/></label><label>Tenant id <input value={tenantId} onChange={e=>{setPage(0);setTenantId(e.target.value);}} inputMode="numeric"/></label></div>
   <table className="data-table"><caption className="visually-hidden">Platform audit events</caption><thead><tr><th scope="col">When</th><th scope="col">Actor</th><th scope="col">Action</th><th scope="col">Target</th><th scope="col">Outcome</th><th scope="col">Reason / details</th></tr></thead>
    <tbody>{events.map(event=><tr key={event.id}><td>{new Date(event.occurredAt).toLocaleString()}</td><td>{event.actor}</td><td>{event.action}</td><td>{event.targetType} {event.targetId}</td><td>{event.outcome}</td><td>{event.reason??event.details??''}</td></tr>)}{events.length===0&&<tr><td colSpan={6}>No events.</td></tr>}</tbody></table>
   <div className="platform-pager"><button type="button" className="secondary" disabled={page===0} onClick={()=>setPage(page-1)}>Previous</button><span>Page {page+1} of {Math.max(1,Math.ceil(total/size))} · {total} events</span><button type="button" className="secondary" disabled={(page+1)*size>=total} onClick={()=>setPage(page+1)}>Next</button></div></>}
  {error&&<p role="alert">{error}</p>}
 </section>;
}
