import {useEffect,useState} from 'react';
import {platformApi,ApiError} from './api';
import {permissionForTarget,slugProblem,tenantTransitions,transitionLabel,usernameProblem,type TenantStatus} from './platformRules';
export type Tenant={id:number;slug:string;name:string;legalName:string;contactEmail:string;status:TenantStatus;statusReason?:string|null};
const message=(e:unknown,fallback:string)=>e instanceof ApiError?(e.message||`${fallback} (${e.status}).`):fallback;
const statuses:TenantStatus[]=['PENDING','UNDER_REVIEW','ACTIVE','SUSPENDED','CLOSED'];

export function TenantOnboarding({can,onCreated}:{can:(p:string)=>boolean;onCreated:(tenant:Tenant)=>void}){
 const [form,setForm]=useState({slug:'',name:'',legalName:'',contactEmail:'',contactPhone:'',address:'',taxId:''}),[error,setError]=useState(''),[notice,setNotice]=useState(''),[busy,setBusy]=useState(false);
 const set=(key:keyof typeof form,value:string)=>setForm(previous=>({...previous,[key]:value}));
 async function submit(event:React.FormEvent){
  event.preventDefault();setError('');setNotice('');
  const problem=slugProblem(form.slug);if(problem){setError(problem);return;}
  if(!form.name.trim()||!form.legalName.trim()||!form.contactEmail.trim()){setError('Display name, legal name and contact email are required.');return;}
  setBusy(true);
  try{const result=await platformApi<{tenant:Tenant;created:boolean}>('/tenants',{body:form});setNotice(result.created?`Tenant ${result.tenant.slug} created as PENDING.`:`Tenant ${result.tenant.slug} already exists with this profile; nothing changed.`);onCreated(result.tenant);if(result.created)setForm({slug:'',name:'',legalName:'',contactEmail:'',contactPhone:'',address:'',taxId:''});}
  catch(e){setError(message(e,'Could not create the tenant'));}finally{setBusy(false);}
 }
 const allowed=can('PLATFORM_TENANT_CREATE');
 return <section id="platform-onboarding" aria-label="Onboard a vendor"><h2>Onboard a vendor</h2>
  <p className="bill-hint">Creates an ordinary vendor tenant in PENDING. Saumya Solar, if it sells, is onboarded the same way. The platform owns no catalogue, stock or orders.</p>
  {!allowed&&<p role="status">Your role cannot create tenants.</p>}
  <form onSubmit={submit} className="platform-form">
   <label>Slug <input value={form.slug} onChange={e=>set('slug',e.target.value)} disabled={!allowed} autoComplete="off"/></label>
   <label>Display name <input value={form.name} onChange={e=>set('name',e.target.value)} disabled={!allowed}/></label>
   <label>Legal name <input value={form.legalName} onChange={e=>set('legalName',e.target.value)} disabled={!allowed}/></label>
   <label>Contact email <input type="email" value={form.contactEmail} onChange={e=>set('contactEmail',e.target.value)} disabled={!allowed}/></label>
   <label>Contact phone <input value={form.contactPhone} onChange={e=>set('contactPhone',e.target.value)} disabled={!allowed}/></label>
   <label>Tax identifier <input value={form.taxId} onChange={e=>set('taxId',e.target.value)} disabled={!allowed}/></label>
   <label>Address <input value={form.address} onChange={e=>set('address',e.target.value)} disabled={!allowed}/></label>
   <button disabled={!allowed||busy}>Create tenant</button>
  </form>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
 </section>;
}

export function TenantDirectory({can,selected,onSelect,refresh}:{can:(p:string)=>boolean;selected?:Tenant;onSelect:(tenant:Tenant)=>void;refresh:number}){
 const [rows,setRows]=useState<Tenant[]>([]),[total,setTotal]=useState(0),[status,setStatus]=useState(''),[q,setQ]=useState(''),[page,setPage]=useState(0),[error,setError]=useState('');
 const size=10;
 useEffect(()=>{let live=true;setError('');
  platformApi<{items:Tenant[];total:number}>(`/tenants?page=${page}&size=${size}${status?`&status=${status}`:''}${q.trim()?`&q=${encodeURIComponent(q.trim())}`:''}`).then(result=>{if(live){setRows(result.items);setTotal(result.total);}}).catch(e=>{if(live)setError(message(e,'Could not load tenants'));});
  return()=>{live=false;};
 },[page,status,q,refresh]);
 return <section id="platform-tenants" aria-label="Tenant directory"><h2>Tenant directory</h2>
  <div className="platform-filters"><label>Status <select value={status} onChange={e=>{setPage(0);setStatus(e.target.value);}}><option value="">All</option>{statuses.map(s=><option key={s} value={s}>{s}</option>)}</select></label>
   <label>Search <input value={q} onChange={e=>{setPage(0);setQ(e.target.value);}} placeholder="Name or slug"/></label></div>
  {error&&<p role="alert">{error}</p>}
  <table className="data-table"><caption className="visually-hidden">Tenants</caption><thead><tr><th scope="col">Name</th><th scope="col">Slug</th><th scope="col">Status</th><th scope="col"><span className="visually-hidden">Actions</span></th></tr></thead>
   <tbody>{rows.map(row=><tr key={row.id} aria-selected={selected?.id===row.id}><th scope="row">{row.name}</th><td>{row.slug}</td><td><span className={`badge tenant-${row.status.toLowerCase()}`}>{row.status}</span></td><td><button type="button" className="secondary" aria-label={`Manage ${row.name}`} onClick={()=>onSelect(row)}>Manage</button></td></tr>)}
    {rows.length===0&&<tr><td colSpan={4}>No tenants match.</td></tr>}</tbody></table>
  <div className="platform-pager"><button type="button" className="secondary" disabled={page===0} onClick={()=>setPage(page-1)}>Previous</button><span>Page {page+1} of {Math.max(1,Math.ceil(total/size))} · {total} tenants</span><button type="button" className="secondary" disabled={(page+1)*size>=total} onClick={()=>setPage(page+1)}>Next</button></div>
  {selected&&<TenantDetail key={selected.id} tenant={selected} can={can} onChanged={onSelect}/>}
 </section>;
}

export function TenantDetail({tenant,can,onChanged}:{tenant:Tenant;can:(p:string)=>boolean;onChanged:(tenant:Tenant)=>void}){
 const [reason,setReason]=useState(''),[error,setError]=useState(''),[notice,setNotice]=useState(''),[username,setUsername]=useState(''),[password,setPassword]=useState<string>(),[busy,setBusy]=useState(false);
 const next=tenantTransitions[tenant.status];
 async function move(target:TenantStatus){
  setError('');setNotice('');
  if(!reason.trim()){setError('A reason is required for every lifecycle change.');return;}
  setBusy(true);
  try{const updated=await platformApi<Tenant>(`/tenants/${tenant.id}/status`,{body:{status:target,reason:reason.trim()}});setNotice(`${updated.name} is now ${updated.status}.`);setReason('');onChanged(updated);}
  catch(e){setError(message(e,'Lifecycle change rejected'));}finally{setBusy(false);}
 }
 async function provision(){
  setError('');setNotice('');setPassword(undefined);
  const problem=usernameProblem(username);if(problem){setError(problem);return;}
  setBusy(true);
  try{const result=await platformApi<{username:string;created:boolean;temporaryPassword?:string|null}>(`/tenants/${tenant.id}/vendor-admin`,{body:{username:username.trim()}});
   if(result.created){setPassword(result.temporaryPassword??undefined);setNotice(`Vendor Admin ${result.username} created.`);}else setNotice(`Vendor Admin ${result.username} already exists for this tenant; no new password was issued.`);}
  catch(e){setError(message(e,'Could not provision the Vendor Admin'));}finally{setBusy(false);}
 }
 return <div className="platform-detail" aria-label={`Manage ${tenant.name}`} role="group"><h3>{tenant.name} <span className={`badge tenant-${tenant.status.toLowerCase()}`}>{tenant.status}</span></h3>
  <p className="bill-hint">{tenant.legalName} · {tenant.contactEmail}{tenant.statusReason?` · Last reason: ${tenant.statusReason}`:''}</p>
  <h4>Lifecycle</h4>
  {next.length===0?<p role="status">This tenant is closed. Closed tenants are terminal and their records are preserved.</p>:<>
   <label>Reason (recorded in the audit trail) <input value={reason} onChange={e=>setReason(e.target.value)} maxLength={500}/></label>
   <div className="platform-actions">{next.map(target=>{const permitted=can(permissionForTarget(target));return <button key={target} type="button" className={target==='CLOSED'||target==='SUSPENDED'?'danger':undefined} disabled={busy||!permitted} title={permitted?undefined:`Requires ${permissionForTarget(target)}`} onClick={()=>void move(target)}>{transitionLabel[target]}</button>;})}</div></>}
  <h4>Initial Vendor Admin</h4>
  {tenant.status==='CLOSED'?<p className="bill-hint">Closed tenants cannot receive a Vendor Admin.</p>:<div className="platform-actions"><label>Username <input value={username} onChange={e=>setUsername(e.target.value)} autoComplete="off"/></label><button type="button" disabled={busy||!can('PLATFORM_TENANT_CREATE')} onClick={()=>void provision()}>Provision Vendor Admin</button></div>}
  {password&&<div role="alert" className="platform-secret"><strong>Temporary password (shown once):</strong> <code>{password}</code><p className="bill-hint">Share it securely. It is not stored and cannot be shown again; the admin has vendor permissions only.</p></div>}
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
 </div>;
}
