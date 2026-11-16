import {useEffect,useState} from 'react';
import {platformApi,ApiError} from './api';
import {money,subscriptionTransitions,type SubscriptionState} from './platformRules';
import type {Tenant} from './PlatformTenants';
const message=(e:unknown,fallback:string)=>e instanceof ApiError?(e.message||`${fallback} (${e.status}).`):fallback;
type Version={id:string;version:number;interval:string;price:number;currency:string;taxRate:number;taxInclusive:boolean;graceDays:number;features:string[]};
type Plan={code:string;name:string;active:boolean;versions:Version[]};
type Sub={id:string;planCode:string;planVersion:number;price:number;currency:string;interval:string;status:SubscriptionState;statusReason?:string|null;periodStart?:string|null;periodEnd?:string|null};
type Invoice={id:string;number:string;total:number;currency:string;status:string;dueAt:string;periodStart:string;payments:{id:string;status:string;method:string;merchantId:string}[]};

export function PlatformPlans({can}:{can:(p:string)=>boolean}){
 const [plans,setPlans]=useState<Plan[]>([]),[error,setError]=useState(''),[notice,setNotice]=useState(''),[tick,setTick]=useState(0);
 const [form,setForm]=useState({code:'',name:'',interval:'MONTHLY',price:'',currency:'INR',taxRate:'18',graceDays:'5'}),[versionFor,setVersionFor]=useState(''),[versionPrice,setVersionPrice]=useState('');
 const view=can('PLATFORM_PLAN_MANAGE')||can('PLATFORM_SUBSCRIPTION_VIEW'),manage=can('PLATFORM_PLAN_MANAGE');
 useEffect(()=>{if(!view)return;let live=true;platformApi<Plan[]>('/plans').then(rows=>{if(live)setPlans(rows);}).catch(e=>{if(live)setError(message(e,'Could not load plans'));});return()=>{live=false;};},[view,tick]);
 const terms=(price:string,source=form)=>({interval:source.interval,price,currency:source.currency,taxRate:source.taxRate,taxInclusive:false,graceDays:Number(source.graceDays),features:[]});
 async function run(action:()=>Promise<unknown>,done:string){setError('');setNotice('');try{await action();setNotice(done);setTick(value=>value+1);}catch(e){setError(message(e,'Plan change rejected'));}}
 return <section id="platform-plans" aria-label="Plans and versions"><h2>Plans &amp; versions</h2>
  <p className="bill-hint">Price and entitlement changes create a new immutable version. Existing subscriptions keep the terms they agreed to.</p>
  {!view&&<p role="status">Your role cannot view plans.</p>}
  {plans.map(plan=><div key={plan.code} className="platform-card"><h3>{plan.name} <small>({plan.code})</small> {!plan.active&&<span className="badge">Retired</span>}</h3>
   <ul>{plan.versions.map(v=><li key={v.id}>v{v.version} · {v.interval} · {money(v.price,v.currency)} + {v.taxRate}% tax · grace {v.graceDays}d</li>)}</ul>
   {manage&&<div className="platform-actions">
    <label>New version price <input aria-label={`New version price for ${plan.code}`} value={versionFor===plan.code?versionPrice:''} onFocus={()=>setVersionFor(plan.code)} onChange={e=>{setVersionFor(plan.code);setVersionPrice(e.target.value);}}/></label>
    <button type="button" className="secondary" disabled={versionFor!==plan.code||!/^\d+(\.\d{1,2})?$/.test(versionPrice)} onClick={()=>void run(()=>platformApi(`/plans/${plan.code}/versions`,{body:{...terms(versionPrice,{...form,interval:plan.versions[0].interval,currency:plan.versions[0].currency,taxRate:String(plan.versions[0].taxRate),graceDays:String(plan.versions[0].graceDays)})}}),`Version added to ${plan.code}.`)}>Add version</button>
    <button type="button" className="secondary" onClick={()=>void run(()=>platformApi(`/plans/${plan.code}/active`,{body:{active:!plan.active}}),plan.active?`${plan.code} retired.`:`${plan.code} reactivated.`)}>{plan.active?'Retire plan':'Reactivate plan'}</button></div>}
  </div>)}
  {manage&&<form className="platform-form" onSubmit={e=>{e.preventDefault();if(!/^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$/.test(form.code)){setError('Plan code must be 3-40 lower-case letters, digits or hyphens.');return;}if(!/^\d+(\.\d{1,2})?$/.test(form.price)){setError('Enter a price with up to two decimals.');return;}void run(()=>platformApi('/plans',{body:{code:form.code,name:form.name,terms:terms(form.price)}}),`Plan ${form.code} created.`);}}>
   <h3>New plan</h3>
   <label>Code <input value={form.code} onChange={e=>setForm({...form,code:e.target.value})}/></label><label>Name <input value={form.name} onChange={e=>setForm({...form,name:e.target.value})}/></label>
   <label>Interval <select value={form.interval} onChange={e=>setForm({...form,interval:e.target.value})}><option>MONTHLY</option><option>QUARTERLY</option><option>YEARLY</option></select></label>
   <label>Price <input value={form.price} onChange={e=>setForm({...form,price:e.target.value})}/></label><label>Currency <input value={form.currency} maxLength={3} onChange={e=>setForm({...form,currency:e.target.value.toUpperCase()})}/></label>
   <label>Tax % <input value={form.taxRate} onChange={e=>setForm({...form,taxRate:e.target.value})}/></label><label>Grace days <input value={form.graceDays} onChange={e=>setForm({...form,graceDays:e.target.value})}/></label>
   <button>Create plan</button></form>}
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
 </section>;
}

export function TenantSubscription({tenant,can}:{tenant?:Tenant;can:(p:string)=>boolean}){
 const [data,setData]=useState<{current:Sub|null;history:Sub[];invoices:Invoice[]}>(),[plans,setPlans]=useState<Plan[]>([]),[error,setError]=useState(''),[notice,setNotice]=useState(''),[tick,setTick]=useState(0),[plan,setPlan]=useState(''),[reason,setReason]=useState('');
 const [reference,setReference]=useState(''),[method,setMethod]=useState('OFFLINE');
 const view=can('PLATFORM_SUBSCRIPTION_VIEW'),adjust=can('PLATFORM_SUBSCRIPTION_ADJUST');
 useEffect(()=>{if(!tenant||!view){setData(undefined);return;}let live=true;setError('');
  Promise.all([platformApi<any>(`/tenants/${tenant.id}/subscriptions`),platformApi<Plan[]>('/plans').catch(()=>[] as Plan[])]).then(([subscriptions,rows])=>{if(live){setData(subscriptions);setPlans(rows.filter(row=>row.active));}}).catch(e=>{if(live)setError(message(e,'Could not load the subscription'));});
  return()=>{live=false;};},[tenant?.id,view,tick]);
 async function run(action:()=>Promise<unknown>,done:string){setError('');setNotice('');try{await action();setNotice(done);setTick(value=>value+1);}catch(e){setError(message(e,'Subscription change rejected'));}}
 const current=data?.current;
 return <section id="platform-subscriptions" aria-label="Tenant subscriptions"><h2>Tenant subscriptions</h2>
  <p className="bill-hint">SaaS subscription payments use the platform merchant account and separate records. They never mix with customer purchase payments.</p>
  {!tenant?<p role="status">Select a tenant in the directory to manage its subscription.</p>:!view?<p role="status">Your role cannot view subscriptions.</p>:<>
   <h3>{tenant.name}</h3>
   {current?<div className="platform-card"><p><span className="badge">{current.status}</span> {current.planCode} v{current.planVersion} · {money(current.price,current.currency)} / {current.interval.toLowerCase()}</p>
    {current.periodEnd&&<p className="bill-hint">Current period ends {new Date(current.periodEnd).toLocaleDateString()}</p>}
    {adjust&&subscriptionTransitions[current.status].length>0&&<div className="platform-actions"><label>Reason <input value={reason} onChange={e=>setReason(e.target.value)}/></label>
     {subscriptionTransitions[current.status].map(target=><button key={target} type="button" className="secondary" disabled={!reason.trim()} onClick={()=>void run(()=>platformApi(`/tenants/${tenant.id}/subscriptions/status`,{body:{status:target,reason:reason.trim()}}),`Subscription is now ${target}.`)}>{target.replaceAll('_',' ')}</button>)}</div>}
    {adjust&&<button type="button" onClick={()=>void run(()=>platformApi(`/tenants/${tenant.id}/subscriptions/invoices`,{method:'POST',body:{}}),'Invoice issued for the current period.')}>Issue invoice for current period</button>}</div>
    :<p role="status">No effective subscription.</p>}
   {adjust&&<div className="platform-actions"><label>Assign plan <select value={plan} onChange={e=>setPlan(e.target.value)}><option value="">Choose a plan</option>{plans.map(p=><option key={p.code} value={p.code}>{p.name} (v{p.versions[0]?.version})</option>)}</select></label>
    <button type="button" disabled={!plan} onClick={()=>void run(()=>platformApi(`/tenants/${tenant.id}/subscriptions`,{body:{planCode:plan}}),`Plan ${plan} assigned.`)}>Assign latest version</button></div>}
   <h4>Invoices</h4>
   {(data?.invoices.length??0)===0?<p className="bill-hint">No invoices.</p>:<table className="data-table"><caption className="visually-hidden">Subscription invoices</caption><thead><tr><th scope="col">Number</th><th scope="col">Total</th><th scope="col">Status</th><th scope="col">Payments</th><th scope="col"><span className="visually-hidden">Actions</span></th></tr></thead>
    <tbody>{data!.invoices.map(invoice=><tr key={invoice.id}><th scope="row">{invoice.number}</th><td>{money(invoice.total,invoice.currency)}</td><td>{invoice.status}</td><td>{invoice.payments.map(p=>`${p.method} ${p.status.toLowerCase()} → ${p.merchantId}`).join('; ')||'—'}</td>
     <td>{adjust&&invoice.status==='OPEN'&&<span className="platform-actions"><select aria-label={`Method for ${invoice.number}`} value={method} onChange={e=>setMethod(e.target.value)}><option>OFFLINE</option><option>GATEWAY</option></select><input aria-label={`Reference for ${invoice.number}`} placeholder="Provider reference" value={reference} onChange={e=>setReference(e.target.value)}/>
      <button type="button" aria-label={`Record payment for ${invoice.number}`} onClick={()=>void run(()=>platformApi(`/subscription-invoices/${invoice.id}/payments`,{body:{amount:invoice.total,method,reference:reference||undefined,succeeded:true}}),`Payment recorded for ${invoice.number}.`)}>Record payment</button></span>}</td></tr>)}</tbody></table>}
  </>}
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
 </section>;
}
