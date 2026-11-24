import {useEffect,useRef,useState,type FormEvent} from 'react';
import {ApiError,platformApi} from './api';
import {authenticatedFetch,useSession} from './session';
import {downloadFile} from './fileDownload';

type Subscription={id:number;tenantId:number;tenantName:string;planId:number;planKey:string;planName:string;planVersion:number;termsSnapshot:string;status:string;periodStart:string;periodEnd:string;graceUntil:string|null;createdAt:string;version:number;allowedAdministrativeTransitions:string[]};
type Plan={id:number;planKey:string;displayName:string;active:boolean;currentVersion:number;versions:{version:number;billingInterval:string}[]};
type Invoice={id:number;invoiceNumber:string;currency:string;total:number|string;status:string;mockNextAttemptAt:string|null};
type Payment={id:number;invoiceId:number;provider:string;providerPaymentReference:string|null;amount:number|string;
 currency:string;status:string;createdAt:string;confirmedAt:string|null};
type BillingHistory={invoices:Invoice[];payments:Payment[]};
type SubscriptionEvent={id:number;oldStatus:string|null;newStatus:string;actor:string;reason:string;occurredAt:string};

export default function PlatformSubscriptions(){
 const {account}=useSession();
 const canAdjust=account?.permissions.includes('PLATFORM_SUBSCRIPTION_ADJUST')??false;
 const [rows,setRows]=useState<Subscription[]>([]),[plans,setPlans]=useState<Plan[]>([]);
 const [tenantId,setTenantId]=useState(''),[planId,setPlanId]=useState(''),[start,setStart]=useState(''),[end,setEnd]=useState(''),[reason,setReason]=useState('Initial plan assignment');
 const [usePlanInterval,setUsePlanInterval]=useState(true);
 const [selected,setSelected]=useState<number|null>(null),[billing,setBilling]=useState<BillingHistory|null>(null),[events,setEvents]=useState<SubscriptionEvent[]>([]);
 const [statusReason,setStatusReason]=useState(''),[target,setTarget]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
 const detailRequest=useRef(0);
 const current=rows.find(row=>row.id===selected);
 const selectedPlan=plans.find(plan=>plan.active&&plan.id===Number(planId));
 const selectedInterval=selectedPlan?.versions.find(version=>version.version===selectedPlan.currentVersion)?.billingInterval;
 const transitions=current?.allowedAdministrativeTransitions??[];
 const selectedTransition=transitions.includes(target)?target:'';
 async function load(){setError('');try{const [s,p]=await Promise.all([platformApi('/subscriptions'),platformApi('/subscription-plans')]);setRows(s);setPlans(p);}catch(e){setError((e as Error).message);}}
 useEffect(()=>{void load();},[]);
 async function action(work:()=>Promise<unknown>,success:string,refreshId?:number){
  setBusy(true);setError('');setMessage('');
  try{
   await work();
   setMessage(success);
   await load();
   if(refreshId!==undefined)await loadDetails(refreshId);
  }catch(e){setError(e instanceof ApiError?e.message:(e as Error).message);}
  finally{setBusy(false);}
 }
 async function create(e:FormEvent){
  e.preventDefault();
  if(!canAdjust||busy||!selectedPlan)return;
  await action(()=>platformApi(`/tenants/${Number(tenantId)}/subscription`,'POST',{
   planId:selectedPlan.id,expectedPlanVersion:selectedPlan.currentVersion,periodStart:start,
   usePlanInterval,...(usePlanInterval?{}:{periodEnd:end}),reason,
  }),'Pending subscription recorded. No payment is marked successful.');
 }
 async function loadDetails(id:number){
  const request=++detailRequest.current;
  try{
   const [nextBilling,nextEvents]=await Promise.all([
    platformApi(`/subscriptions/${id}/billing-history`),platformApi(`/subscriptions/${id}/events`),
   ]);
   if(request===detailRequest.current){setBilling(nextBilling);setEvents(nextEvents);}
  }catch(e){if(request===detailRequest.current)setError((e as Error).message);}
 }
 async function show(id:number){
  setSelected(id);
  setBilling(null);setEvents([]);setStatusReason('');setTarget('');
  await loadDetails(id);
 }
 async function downloadInvoice(subscriptionId:number,invoiceId:number){
  setError('');
  try{
   const response=await authenticatedFetch(`/api/v1/platform/subscriptions/${subscriptionId}/invoices/${invoiceId}/document`);
   if(!response.ok)throw new ApiError(response.status);
   if(response.headers.get('Content-Type')?.split(';')[0]!=='application/pdf')throw new Error('Invalid invoice PDF response.');
   const blob=await response.blob();
   downloadFile({blob,filename:`mock-subscription-invoice-${invoiceId}.pdf`});
  }catch(e){setError((e as Error).message);}
 }
 async function change(){
  if(!canAdjust||busy||!current||!selectedTransition||!statusReason.trim())return;
  const id=current.id;
  await action(async()=>{
   await platformApi(`/subscriptions/${id}/status`,'POST',{
    status:selectedTransition,reason:statusReason.trim(),expectedVersion:current.version,
   });
   setTarget('');setStatusReason('');
  },'Subscription status updated.',id);
 }
 return <div className="platform-content">
  <div className="platform-page-heading"><div><span className="platform-kicker">PLATFORM / SUBSCRIPTIONS</span><h1>Tenant subscriptions</h1><p>Platform billing records are separate from vendor customer-purchase payments.</p></div><button className="secondary" onClick={()=>void load()}>Refresh</button></div>
  {error&&<p className="platform-alert" role="alert">{error}</p>}{message&&<p className="platform-notice" role="status">{message}</p>}{busy&&<p role="status">Saving subscription administration change…</p>}
  {canAdjust&&<section className="platform-panel"><h2>Assign a plan to a tenant</h2><p>Creates a PENDING subscription with an immutable plan-version snapshot. Mock billing runs only when explicitly enabled in local or test environments.</p>
   <form className="platform-form-grid" onSubmit={create}>
    <label>Tenant ID<input required type="number" min="1" value={tenantId} onChange={e=>setTenantId(e.target.value)}/></label>
    <label>Plan<select required value={planId} onChange={e=>setPlanId(e.target.value)}><option value="">Choose plan</option>{plans.filter(p=>p.active).map(p=><option value={p.id} key={p.id}>{p.displayName} · v{p.currentVersion}</option>)}</select></label>
    <label>Billing period start<input required type="date" value={start} onChange={e=>setStart(e.target.value)}/></label>
    <label>Billing period<select value={usePlanInterval?'PLAN':'MANUAL'} onChange={e=>setUsePlanInterval(e.target.value==='PLAN')}>
     <option value="PLAN">Use plan interval{selectedInterval?` (${selectedInterval.toLowerCase()})`:''}</option>
     <option value="MANUAL">Enter end date</option>
    </select></label>
    {usePlanInterval?<p>The end date is calculated from the start and the selected plan version.</p>:
     <label>Billing period end<input required type="date" value={end} onChange={e=>setEnd(e.target.value)}/></label>}
    <label className="platform-wide">Reason<input required value={reason} onChange={e=>setReason(e.target.value)}/></label>
    <div className="platform-form-actions"><button disabled={busy||!selectedPlan}>Create pending subscription</button></div>
   </form>
  </section>}
  <section className="platform-panel"><h2>Subscription history</h2><div className="platform-table-wrap"><table><thead><tr><th>Tenant</th><th>Plan / version</th><th>Status</th><th>Period</th><th>History</th></tr></thead><tbody>{rows.map(s=><tr key={s.id}><td>{s.tenantName} · #{s.tenantId}</td><td>{s.planName} · v{s.planVersion}</td><td>{s.status}{s.graceUntil&&<div>Grace ends <time dateTime={s.graceUntil}>{new Date(s.graceUntil).toLocaleString()}</time></div>}</td><td>{s.periodStart} – {s.periodEnd}</td><td><button className="secondary" disabled={busy} onClick={()=>void show(s.id)}>Billing and events</button></td></tr>)}</tbody></table></div></section>
  {selected!==null&&<section className="platform-panel"><h2>Subscription #{selected}</h2>
   {current&&<SavedSubscriptionTerms version={current.planVersion} snapshot={current.termsSnapshot}/>}
   {canAdjust&&<><div className="platform-form-grid">
    {transitions.length>0?<><label>Permitted administrative transition<select value={selectedTransition} onChange={e=>setTarget(e.target.value)}><option value="">Choose a transition</option>{transitions.map(status=><option key={status} value={status}>{status.replaceAll('_',' ')}</option>)}</select></label>
    <label>Transition reason<input required maxLength={500} value={statusReason} onChange={e=>setStatusReason(e.target.value)}/></label>
    <div className="platform-form-actions"><button disabled={busy||!selectedTransition||!statusReason.trim()} onClick={()=>void change()}>Apply status transition</button></div></>:<p>No administrative transitions are available for this subscription.</p>}
   </div><p>Activation requires a verified payment outcome. The local mock billing job can reactivate eligible subscriptions.</p></>}
   <article><h3>Invoices</h3>{billing?.invoices?.length?billing.invoices.map(i=><p key={i.id}>{i.invoiceNumber} · {i.currency} {i.total} · {i.status}{i.mockNextAttemptAt&&<> · Test retry <time dateTime={i.mockNextAttemptAt}>{new Date(i.mockNextAttemptAt).toLocaleString()}</time></>}{i.invoiceNumber.startsWith('MOCK-SUB-')&&
    <button type="button" className="secondary" onClick={()=>void downloadInvoice(selected,i.id)}>Download mock invoice PDF</button>}</p>):<p>No subscription invoices recorded yet.</p>}</article>
   {billing&&<SubscriptionPayments key={selected} billing={billing}/>}
   <h3>Subscription events</h3><div className="platform-table-wrap"><table><thead><tr><th>Time</th><th>From</th><th>To</th><th>Actor</th><th>Reason</th></tr></thead><tbody>{events.map(e=><tr key={e.id}><td>{new Date(e.occurredAt).toLocaleString()}</td><td>{e.oldStatus??'—'}</td><td>{e.newStatus}</td><td>{e.actor}</td><td>{e.reason}</td></tr>)}</tbody></table></div>
  </section>}
 </div>;
}

function SubscriptionPayments({billing}:{billing:BillingHistory}){
 const [invoiceFilter,setInvoiceFilter]=useState('');
 const invoices=new Map(billing.invoices.map(invoice=>[invoice.id,invoice.invoiceNumber]));
 const selectedInvoice=billing.invoices.find(invoice=>String(invoice.id)===invoiceFilter);
 const payments=selectedInvoice?billing.payments.filter(payment=>payment.invoiceId===selectedInvoice.id):billing.payments;

 return <article>
  <h3>Subscription payment attempts</h3>
  {billing.invoices.length>0&&<label>Filter payment attempts by invoice
   <select value={selectedInvoice?invoiceFilter:''} onChange={event=>setInvoiceFilter(event.target.value)}>
    <option value="">All invoices</option>
    {billing.invoices.map(invoice=><option key={invoice.id} value={invoice.id}>{invoice.invoiceNumber}</option>)}
   </select>
  </label>}
  {payments.length===0?<p>{selectedInvoice?'No payment attempts for this invoice.':'No subscription payments recorded yet.'}</p>:
   <div className="platform-table-wrap">
    <table aria-label="Subscription payment attempts">
     <thead><tr><th>Invoice</th><th>Provider</th><th>Reference</th><th>Amount</th><th>Status</th><th>Attempted</th><th>Confirmed</th></tr></thead>
     <tbody>{payments.map(payment=><tr key={payment.id}>
      <td>{invoices.get(payment.invoiceId)??`Invoice #${payment.invoiceId}`}</td>
      <td>{payment.provider==='MOCK_SUBSCRIPTION'?'Mock subscription':payment.provider}</td>
      <td>{payment.providerPaymentReference??'—'}</td>
      <td>{payment.currency} {payment.amount}</td>
      <td>{payment.status}</td>
      <td><time dateTime={payment.createdAt}>{new Date(payment.createdAt).toLocaleString()}</time></td>
      <td>{payment.confirmedAt?<time dateTime={payment.confirmedAt}>{new Date(payment.confirmedAt).toLocaleString()}</time>:'—'}</td>
     </tr>)}</tbody>
    </table>
   </div>}
 </article>;
}

function readSavedTerms(snapshot:string):Record<string,unknown>|null{
 try{
  const value:unknown=JSON.parse(snapshot);
  return value&&typeof value==='object'&&!Array.isArray(value)?value as Record<string,unknown>:null;
 }catch{
  return null;
 }
}

function SavedSubscriptionTerms({version,snapshot}:{version:number;snapshot:string}){
 const terms=readSavedTerms(snapshot);
 const fields=[
  {label:'Price',value:typeof terms?.currency==='string'&&typeof terms.price==='number'
   ?`${terms.currency} ${terms.price}`:null},
  {label:'Billing interval',value:terms?.billingInterval},
  {label:'Users',value:terms?.maxUsers},
  {label:'Storage (MB)',value:terms?.maxStorageMb},
  {label:'Branches',value:terms?.maxBranches},
  {label:'Warehouses',value:terms?.maxWarehouses},
  {label:'Grace period (days)',value:terms?.graceDays},
  {label:'Trial eligible',value:typeof terms?.trialEligible==='boolean'?(terms.trialEligible?'Yes':'No'):null},
  {label:'Invoice description',value:terms?.invoiceDescription},
 ];
 const recordedFields=fields.filter(field=>typeof field.value==='string'||typeof field.value==='number');

 return <details className="platform-plan-version">
  <summary>Saved subscription terms · v{version}</summary>
  {recordedFields.length>0&&<dl>{recordedFields.map(field=><div key={field.label}>
   <dt>{field.label}</dt><dd>{String(field.value)}</dd>
  </div>)}</dl>}
  {!recordedFields.length&&<p>{terms?.local_demo===true?'This local demo subscription has a limited snapshot.':'No plan terms were recorded in this snapshot.'}</p>}
  {terms?.entitlements!=null&&<details><summary>Saved feature entitlements</summary><pre>{JSON.stringify(terms.entitlements,null,2)}</pre></details>}
  {terms?.taxMetadata!=null&&<details><summary>Saved tax metadata</summary><pre>{JSON.stringify(terms.taxMetadata,null,2)}</pre></details>}
 </details>;
}
