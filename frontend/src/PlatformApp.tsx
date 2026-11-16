import {useCallback,useEffect,useState,type FormEvent} from 'react';
import {platformApi,ApiError} from './api';
import PlatformIdentityAdmin from './PlatformIdentityAdmin';
import {signOut,useSession} from './session';

type Page='dashboard'|'tenants'|'plans'|'audit'|'support'|'configuration'|'identities';
type Tenant={id:number;slug:string;name:string;legalName:string;lifecycleStatus:string;contactEmail?:string|null;contactPhone?:string|null;initialVendorAdminUsername?:string|null;createdAt:string;onlineBranchId?:number|null};
type TenantPage={items:Tenant[];totalElements:number;totalPages:number;page:number};
type PlanVersion={version:number;price:string;currency:string;billingInterval:string;maxUsers:number;maxStorageMb:number;maxBranches:number;maxWarehouses:number;entitlements:string;graceDays:number;taxMetadata:string;invoiceDescription:string;createdAt:string};
type Plan={id:number;planKey:string;displayName:string;active:boolean;currentVersion:number;versions:PlanVersion[]};
type AuditPage={items:{id:number;actor:string;action:string;resourceType:string;resourceId:string|null;tenantId:number|null;reason:string|null;occurredAt:string}[]};

const pages:{id:Page;label:string;description:string}[]=[
 {id:'dashboard',label:'Overview',description:'Platform-level tenant and operating context'},
 {id:'tenants',label:'Tenants',description:'Onboard vendors and manage tenant lifecycle'},
 {id:'plans',label:'Subscription plans',description:'Version SaaS prices and entitlements'},
 {id:'audit',label:'Platform audit',description:'Review platform administration activity'},
 {id:'support',label:'Support access',description:'Grant purpose-bound, expiring support windows'},
 {id:'configuration',label:'Configuration',description:'Manage global non-commercial configuration'},
 {id:'identities',label:'Platform identities',description:'Manage platform-only identities and audited MFA enrollment/reset'}
];

export default function PlatformApp(){
 const {account}=useSession();
 const [page,setPage]=useState<Page>('dashboard');
 const visiblePages=pages.filter(item=>item.id!=='identities'||account?.permissions.includes('PLATFORM_IDENTITY_MANAGE'));
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const [tenantPage,setTenantPage]=useState<TenantPage>({items:[],totalElements:0,totalPages:0,page:0});
 const [tenantSearch,setTenantSearch]=useState(''),[tenantStatus,setTenantStatus]=useState('');
 const [dashboard,setDashboard]=useState<{tenantStatuses:{status:string;count:number}[]}|null>(null);
 const [plans,setPlans]=useState<Plan[]>([]);
 const [audit,setAudit]=useState<AuditPage>({items:[]});
 const [support,setSupport]=useState<any[]>([]);
 const [settings,setSettings]=useState<any[]>([]);
 const [reason,setReason]=useState(''),[invite,setInvite]=useState('');
 const [tenantForm,setTenantForm]=useState({slug:'',name:'',legalName:'',contactEmail:'',contactPhone:'',taxIdentifier:'',address:'',initialVendorAdminUsername:''});
 const [planForm,setPlanForm]=useState({planKey:'',displayName:'',price:'',currency:'INR',billingInterval:'MONTHLY',maxUsers:'5',maxStorageMb:'1024',maxBranches:'1',maxWarehouses:'0',entitlements:'{"features":{}}',graceDays:'7',taxMetadata:'{}',invoiceDescription:'SaaS subscription'});
 const [supportForm,setSupportForm]=useState({tenantId:'',purpose:'',ticketReference:'',expiresAt:''});
 const [configForm,setConfigForm]=useState({key:'',category:'INTEGRATION',value:'',sensitive:false,secretReference:''});

 const load=useCallback(async(target:Page)=>{
  setError('');
  try{
   if(target==='dashboard')setDashboard(await platformApi('/dashboard'));
   if(target==='tenants')setTenantPage(await platformApi(`/tenants?page=0&size=50${tenantStatus?'&status='+tenantStatus:''}${tenantSearch?'&q='+encodeURIComponent(tenantSearch):''}`));
   if(target==='plans')setPlans(await platformApi('/subscription-plans'));
   if(target==='audit')setAudit(await platformApi('/audit?page=0&size=50'));
   if(target==='support')setSupport(await platformApi('/support-access'));
   if(target==='configuration')setSettings(await platformApi('/configuration'));
  }catch(e){setError((e as Error).message);}
 },[tenantSearch,tenantStatus]);
 useEffect(()=>{void load(page);},[page,load]);

 async function run(action:()=>Promise<unknown>,success:string){
  setBusy(true);setError('');setNotice('');
  try{await action();setNotice(success);await load(page);}
  catch(e){setError(e instanceof ApiError?e.message:(e as Error).message);}
  finally{setBusy(false);}
 }
 async function createTenant(event:FormEvent){
  event.preventDefault();
  await run(async()=>{
   const result=await platformApi('/tenants','POST',tenantForm);
   if(result?.invitationToken)setInvite(result.invitationToken);
   else setInvite('The nominated identity already has an active account; no new invitation was issued.');
   setTenantForm({slug:'',name:'',legalName:'',contactEmail:'',contactPhone:'',taxIdentifier:'',address:'',initialVendorAdminUsername:''});
  },'Tenant created with an initial branch and Vendor Admin membership.');
 }
 async function changeLifecycle(tenant:Tenant,target:string){
  const why=reason.trim();if(!why){setError('Enter a reason before changing tenant lifecycle.');return;}
  await run(()=>platformApi(`/tenants/${tenant.id}/lifecycle`,'POST',{status:target,reason:why}),`Tenant ${tenant.slug} moved to ${target}.`);
  setReason('');
 }
 async function createPlan(event:FormEvent){
  event.preventDefault();
  await run(()=>platformApi('/subscription-plans','POST',{...planForm,price:Number(planForm.price),maxUsers:Number(planForm.maxUsers),maxStorageMb:Number(planForm.maxStorageMb),maxBranches:Number(planForm.maxBranches),maxWarehouses:Number(planForm.maxWarehouses),graceDays:Number(planForm.graceDays)}),'A new plan version has been saved.');
  setPlanForm(value=>({...value,planKey:'',displayName:'',price:''}));
 }
 async function createSupport(event:FormEvent){
  event.preventDefault();
  await run(()=>platformApi('/support-access','POST',{...supportForm,tenantId:Number(supportForm.tenantId),expiresAt:new Date(supportForm.expiresAt).toISOString()}),'A support-access grant was recorded. It does not create an automatic tenant-data bypass.');
 }
 async function saveConfig(event:FormEvent){
  event.preventDefault();
  await run(()=>platformApi('/configuration','PUT',configForm),'Global configuration reference saved.');
  setConfigForm({key:'',category:'INTEGRATION',value:'',sensitive:false,secretReference:''});
 }
 const lifecycleActions=(tenant:Tenant)=>{
  switch(tenant.lifecycleStatus){case 'PENDING':return ['UNDER_REVIEW','CLOSED'];case 'UNDER_REVIEW':return ['ACTIVE','CLOSED'];case 'ACTIVE':return ['SUSPENDED','CLOSED'];case 'SUSPENDED':return ['ACTIVE','CLOSED'];default:return [];}
 };

 return <div className="platform-app">
  <header className="platform-header">
   <a className="platform-brand" href="/platform"><span className="platform-brand-mark">SS</span><span>Saumya Solar<small>PLATFORM OPERATIONS</small></span></a>
   <div className="platform-identity"><span>{account?.username}</span><span className="platform-role">Platform Super Admin</span><button type="button" className="secondary" onClick={()=>void signOut()}>Sign out</button></div>
  </header>
  <div className="platform-frame">
   <aside className="platform-sidebar"><span className="platform-kicker">SAAS CONTROL PLANE</span>
    <nav aria-label="Platform navigation">{visiblePages.map(item=><button type="button" key={item.id} className={page===item.id?'active':''} onClick={()=>setPage(item.id)}><span>{item.label}</span><small>{item.description}</small></button>)}</nav>
    <p className="platform-sidebar-note">Commercial vendor operations are available only through a separate vendor tenant identity.</p>
   </aside>
   <main className="platform-content">
    <div className="platform-page-heading"><div><span className="platform-kicker">PLATFORM / {page.toUpperCase()}</span><h1>{visiblePages.find(p=>p.id===page)?.label}</h1><p>{visiblePages.find(p=>p.id===page)?.description}</p></div><span className="platform-scope">Platform scope only</span></div>
    {error&&<p className="platform-alert" role="alert">{error}</p>}{notice&&<p className="platform-notice" role="status">{notice}</p>}{busy&&<p role="status">Saving platform changes…</p>}
    {page==='dashboard'&&<section className="platform-panel"><div className="platform-panel-heading"><div><h2>Tenant lifecycle</h2><p>Aggregated platform-owned counts; no tenant-private CRM or transaction detail.</p></div><button className="secondary" onClick={()=>void load('dashboard')}>Refresh</button></div>
     <div className="platform-stat-grid">{(dashboard?.tenantStatuses??[]).map(item=><article key={item.status}><span>{item.status.replaceAll('_',' ')}</span><strong>{item.count}</strong></article>)}</div>
     <div className="platform-boundary-grid"><article><h3>Commercial catalogue</h3><strong>Vendor-owned only</strong><p>There is no platform seller or platform catalogue.</p></article><article><h3>Customer purchase funds</h3><strong>Direct to vendor</strong><p>Customer order payments must use the vendor merchant context.</p></article><article><h3>SaaS subscriptions</h3><strong>Platform merchant</strong><p>Subscription billing uses independent records and credentials.</p></article><article><h3>Support access</h3><strong>Purpose-bound and time-limited</strong><p>A grant alone does not provide unrestricted tenant access.</p></article></div>
    </section>}
    {page==='tenants'&&<>
     <section className="platform-panel"><h2>Onboard a vendor tenant</h2><p>Create the tenant, initial branch and initial Vendor Admin membership. The invitation token is displayed once after creation.</p>
      <form className="platform-form-grid" onSubmit={createTenant}>
       <label>Store slug<input required pattern="[a-z0-9]+(-[a-z0-9]+)*" value={tenantForm.slug} onChange={e=>setTenantForm({...tenantForm,slug:e.target.value})}/></label>
       <label>Display name<input required value={tenantForm.name} onChange={e=>setTenantForm({...tenantForm,name:e.target.value})}/></label>
       <label>Legal name<input required value={tenantForm.legalName} onChange={e=>setTenantForm({...tenantForm,legalName:e.target.value})}/></label>
       <label>Initial Vendor Admin email<input required type="email" value={tenantForm.initialVendorAdminUsername} onChange={e=>setTenantForm({...tenantForm,initialVendorAdminUsername:e.target.value})}/></label>
       <label>Contact email<input type="email" value={tenantForm.contactEmail} onChange={e=>setTenantForm({...tenantForm,contactEmail:e.target.value})}/></label>
       <label>Contact phone<input value={tenantForm.contactPhone} onChange={e=>setTenantForm({...tenantForm,contactPhone:e.target.value})}/></label>
       <label>Tax identifier<input value={tenantForm.taxIdentifier} onChange={e=>setTenantForm({...tenantForm,taxIdentifier:e.target.value})}/></label>
       <label>Address<input value={tenantForm.address} onChange={e=>setTenantForm({...tenantForm,address:e.target.value})}/></label>
       <div className="platform-form-actions"><button disabled={busy}>Create vendor tenant</button></div>
      </form>
      {invite&&<div className="platform-invitation"><strong>Vendor Admin invitation token</strong><code>{invite}</code><p>Share securely with the nominated Vendor Admin. The token is not stored in the platform audit record.</p></div>}
     </section>
     <section className="platform-panel"><div className="platform-panel-heading"><h2>Tenant directory</h2><button className="secondary" onClick={()=>void load('tenants')}>Refresh</button></div>
      <div className="platform-toolbar"><input aria-label="Search tenants" placeholder="Search name, legal name or slug" value={tenantSearch} onChange={e=>setTenantSearch(e.target.value)}/><select aria-label="Filter lifecycle status" value={tenantStatus} onChange={e=>setTenantStatus(e.target.value)}><option value="">All statuses</option>{['PENDING','UNDER_REVIEW','ACTIVE','SUSPENDED','CLOSED'].map(s=><option key={s}>{s}</option>)}</select><button className="secondary" onClick={()=>void load('tenants')}>Search</button></div>
      <label>Lifecycle reason for the next action<input value={reason} onChange={e=>setReason(e.target.value)} placeholder="Required for every lifecycle change"/></label>
      <div className="platform-table-wrap"><table><thead><tr><th>Vendor tenant</th><th>Status</th><th>Initial Vendor Admin</th><th>Storefront</th><th>Lifecycle actions</th></tr></thead><tbody>{tenantPage.items.map(t=><tr key={t.id}><td><strong>{t.name}</strong><small>{t.legalName} · #{t.id}</small></td><td><span className="platform-status">{t.lifecycleStatus}</span></td><td>{t.initialVendorAdminUsername??'—'}</td><td>{t.slug}</td><td><div className="platform-row-actions">{lifecycleActions(t).map(action=><button key={action} className="secondary" disabled={busy} onClick={()=>void changeLifecycle(t,action)}>{action.replaceAll('_',' ')}</button>)}</div></td></tr>)}</tbody></table></div>
      <p>{tenantPage.totalElements} tenants · page {tenantPage.page+1} of {tenantPage.totalPages}</p>
     </section>
    </>}
    {page==='plans'&&<>
     <section className="platform-panel"><h2>Create subscription plan</h2><p>Each saved change creates a new immutable plan version. Existing invoices and subscription snapshots are not rewritten.</p>
      <form className="platform-form-grid" onSubmit={createPlan}>
       <label>Plan key<input required value={planForm.planKey} onChange={e=>setPlanForm({...planForm,planKey:e.target.value})}/></label><label>Display name<input required value={planForm.displayName} onChange={e=>setPlanForm({...planForm,displayName:e.target.value})}/></label>
       <label>Price<input required type="number" min="0" step="0.0001" value={planForm.price} onChange={e=>setPlanForm({...planForm,price:e.target.value})}/></label><label>Currency<input required maxLength={3} value={planForm.currency} onChange={e=>setPlanForm({...planForm,currency:e.target.value.toUpperCase()})}/></label>
       <label>Billing interval<select value={planForm.billingInterval} onChange={e=>setPlanForm({...planForm,billingInterval:e.target.value})}><option>MONTHLY</option><option>QUARTERLY</option><option>YEARLY</option></select></label>
       <label>Included users<input type="number" min="1" value={planForm.maxUsers} onChange={e=>setPlanForm({...planForm,maxUsers:e.target.value})}/></label>
       <label>Storage (MB)<input type="number" min="0" value={planForm.maxStorageMb} onChange={e=>setPlanForm({...planForm,maxStorageMb:e.target.value})}/></label>
       <label>Branches<input type="number" min="1" value={planForm.maxBranches} onChange={e=>setPlanForm({...planForm,maxBranches:e.target.value})}/></label>
       <label>Warehouses<input type="number" min="0" value={planForm.maxWarehouses} onChange={e=>setPlanForm({...planForm,maxWarehouses:e.target.value})}/></label>
       <label>Grace days<input type="number" min="0" max="90" value={planForm.graceDays} onChange={e=>setPlanForm({...planForm,graceDays:e.target.value})}/></label>
       <label className="platform-wide">Entitlements JSON<textarea required value={planForm.entitlements} onChange={e=>setPlanForm({...planForm,entitlements:e.target.value})}/></label>
       <label className="platform-wide">Tax metadata JSON<textarea required value={planForm.taxMetadata} onChange={e=>setPlanForm({...planForm,taxMetadata:e.target.value})}/></label>
       <label className="platform-wide">Invoice description<input required value={planForm.invoiceDescription} onChange={e=>setPlanForm({...planForm,invoiceDescription:e.target.value})}/></label>
       <div className="platform-form-actions"><button disabled={busy}>Save plan version</button></div>
      </form>
     </section>
     <section className="platform-panel"><div className="platform-panel-heading"><h2>Plan catalogue</h2><button className="secondary" onClick={()=>void load('plans')}>Refresh</button></div>
      {plans.map(plan=><article className="platform-plan-card" key={plan.id}><div><h3>{plan.displayName} <small>({plan.planKey})</small></h3><p>{plan.active?'Available for new subscriptions':'Inactive'} · Current version {plan.currentVersion}</p></div><div className="platform-plan-versions">{plan.versions.map(v=><span key={v.version}>v{v.version} · {v.currency} {v.price} · {v.billingInterval}</span>)}</div><button className="secondary" onClick={()=>void run(()=>platformApi(`/subscription-plans/${plan.id}/active`,'PATCH',{active:!plan.active}),'Plan availability updated.')}>{plan.active?'Disable':'Enable'} plan</button></article>)}
     </section>
    </>}
    {page==='audit'&&<section className="platform-panel"><div className="platform-panel-heading"><h2>Immutable platform audit events</h2><button className="secondary" onClick={()=>void load('audit')}>Refresh</button></div><div className="platform-table-wrap"><table><thead><tr><th>Occurred</th><th>Actor</th><th>Action</th><th>Resource</th><th>Tenant</th><th>Reason</th></tr></thead><tbody>{audit.items.map(row=><tr key={row.id}><td>{new Date(row.occurredAt).toLocaleString()}</td><td>{row.actor}</td><td>{row.action}</td><td>{row.resourceType} {row.resourceId}</td><td>{row.tenantId??'Platform'}</td><td>{row.reason??'—'}</td></tr>)}</tbody></table></div></section>}
    {page==='support'&&<><section className="platform-panel"><h2>Grant controlled support window</h2><p>Maximum duration is four hours. Grants are recorded and audited; no general tenant-data bypass is provided.</p><form className="platform-form-grid" onSubmit={createSupport}>
      <label>Tenant ID<input required type="number" min="1" value={supportForm.tenantId} onChange={e=>setSupportForm({...supportForm,tenantId:e.target.value})}/></label>
      <label>Ticket / reference<input required value={supportForm.ticketReference} onChange={e=>setSupportForm({...supportForm,ticketReference:e.target.value})}/></label>
      <label>Expiry<input required type="datetime-local" value={supportForm.expiresAt} onChange={e=>setSupportForm({...supportForm,expiresAt:e.target.value})}/></label>
      <label className="platform-wide">Purpose / reason<textarea required value={supportForm.purpose} onChange={e=>setSupportForm({...supportForm,purpose:e.target.value})}/></label>
      <div className="platform-form-actions"><button disabled={busy}>Record support grant</button></div></form></section>
     <section className="platform-panel"><h2>Recent support grants</h2><div className="platform-table-wrap"><table><thead><tr><th>Tenant</th><th>Actor</th><th>Purpose</th><th>Ticket</th><th>Expiry</th><th>Status</th></tr></thead><tbody>{support.map(s=><tr key={s.id}><td>{s.tenantId}</td><td>{s.actor}</td><td>{s.purpose}</td><td>{s.ticketReference}</td><td>{new Date(s.expiresAt).toLocaleString()}</td><td>{s.active?'Active':s.revokedAt?'Revoked':'Expired'}</td></tr>)}</tbody></table></div></section></>}
    {page==='identities'&&<PlatformIdentityAdmin/>}
    {page==='configuration'&&<><section className="platform-panel"><h2>Global platform configuration</h2><p>Secret values are never accepted. Sensitive settings require a reference to an external secret manager.</p><form className="platform-form-grid" onSubmit={saveConfig}>
      <label>Configuration key<input required value={configForm.key} onChange={e=>setConfigForm({...configForm,key:e.target.value.toUpperCase()})}/></label>
      <label>Category<select value={configForm.category} onChange={e=>setConfigForm({...configForm,category:e.target.value})}><option>INTEGRATION</option><option>EMAIL</option><option>PAYMENT</option><option>PLATFORM</option></select></label>
      <label><input type="checkbox" checked={configForm.sensitive} onChange={e=>setConfigForm({...configForm,sensitive:e.target.checked,value:''})}/> Sensitive secret reference</label>
      {configForm.sensitive?<label>Secret manager reference<input required value={configForm.secretReference} onChange={e=>setConfigForm({...configForm,secretReference:e.target.value})} placeholder="vault://path/to/secret"/></label>:<label className="platform-wide">Non-sensitive value<textarea required value={configForm.value} onChange={e=>setConfigForm({...configForm,value:e.target.value})}/></label>}
      <div className="platform-form-actions"><button disabled={busy}>Save configuration</button></div></form></section>
     <section className="platform-panel"><h2>Saved configuration</h2><div className="platform-table-wrap"><table><thead><tr><th>Key</th><th>Category</th><th>Value</th><th>Updated by</th><th>Updated</th></tr></thead><tbody>{settings.map(s=><tr key={s.key}><td>{s.key}</td><td>{s.category}</td><td>{s.sensitive?(s.secretConfigured?'Configured secret reference':'Not configured'):(s.value??'—')}</td><td>{s.updatedBy}</td><td>{new Date(s.updatedAt).toLocaleString()}</td></tr>)}</tbody></table></div></section></>}
   </main>
  </div>
 </div>;
}
