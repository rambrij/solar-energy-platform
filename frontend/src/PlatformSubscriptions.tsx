import {useEffect,useState,type FormEvent} from 'react';
import {ApiError,platformApi} from './api';
import {useSession} from './session';

type Subscription={id:number;tenantId:number;tenantName:string;planId:number;planKey:string;planName:string;planVersion:number;status:string;periodStart:string;periodEnd:string;graceUntil:string|null;createdAt:string;version:number};
type Plan={id:number;planKey:string;displayName:string;active:boolean;currentVersion:number};
export default function PlatformSubscriptions(){
 const {account}=useSession();
 const canAdjust=account?.permissions.includes('PLATFORM_SUBSCRIPTION_ADJUST')??false;
 const [rows,setRows]=useState<Subscription[]>([]),[plans,setPlans]=useState<Plan[]>([]);
 const [tenantId,setTenantId]=useState(''),[planId,setPlanId]=useState(''),[start,setStart]=useState(''),[end,setEnd]=useState(''),[reason,setReason]=useState('Initial plan assignment');
 const [selected,setSelected]=useState<number|null>(null),[billing,setBilling]=useState<any>(null),[events,setEvents]=useState<any[]>([]);
 const [statusReason,setStatusReason]=useState(''),[target,setTarget]=useState('PAST_DUE'),[busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
 async function load(){setError('');try{const [s,p]=await Promise.all([platformApi('/subscriptions'),platformApi('/subscription-plans')]);setRows(s);setPlans(p);}catch(e){setError((e as Error).message);}}
 useEffect(()=>{void load();},[]);
 async function action(work:()=>Promise<any>,success:string){setBusy(true);setError('');setMessage('');try{await work();setMessage(success);await load();}catch(e){setError(e instanceof ApiError?e.message:(e as Error).message);}finally{setBusy(false);}}
 async function create(e:FormEvent){e.preventDefault();await action(()=>platformApi(`/tenants/${Number(tenantId)}/subscription`,'POST',{planId:Number(planId),periodStart:start,periodEnd:end,reason}),'Pending subscription recorded. No payment is marked successful.');}
 async function show(id:number){setSelected(id);try{const [b,h]=await Promise.all([platformApi(`/subscriptions/${id}/billing-history`),platformApi(`/subscriptions/${id}/events`)]);setBilling(b);setEvents(h);}catch(e){setError((e as Error).message);}}
 async function change(){if(selected===null)return;await action(()=>platformApi(`/subscriptions/${selected}/status`,'POST',{status:target,reason:statusReason}),'Subscription status updated.');setStatusReason('');}
 return <div className="platform-content">
  <div className="platform-page-heading"><div><span className="platform-kicker">PLATFORM / SUBSCRIPTIONS</span><h1>Tenant subscriptions</h1><p>Platform billing records are separate from vendor customer-purchase payments.</p></div><button className="secondary" onClick={()=>void load()}>Refresh</button></div>
  {error&&<p className="platform-alert" role="alert">{error}</p>}{message&&<p className="platform-notice" role="status">{message}</p>}{busy&&<p role="status">Saving subscription administration change…</p>}
  {canAdjust&&<section className="platform-panel"><h2>Assign a plan to a tenant</h2><p>Creates a PENDING subscription with an immutable plan-version snapshot. Invoice/tax issuance and provider collection are not simulated.</p>
   <form className="platform-form-grid" onSubmit={create}>
    <label>Tenant ID<input required type="number" min="1" value={tenantId} onChange={e=>setTenantId(e.target.value)}/></label>
    <label>Plan<select required value={planId} onChange={e=>setPlanId(e.target.value)}><option value="">Choose plan</option>{plans.filter(p=>p.active).map(p=><option value={p.id} key={p.id}>{p.displayName} · v{p.currentVersion}</option>)}</select></label>
    <label>Billing period start<input required type="date" value={start} onChange={e=>setStart(e.target.value)}/></label>
    <label>Billing period end<input required type="date" value={end} onChange={e=>setEnd(e.target.value)}/></label>
    <label className="platform-wide">Reason<input required value={reason} onChange={e=>setReason(e.target.value)}/></label>
    <div className="platform-form-actions"><button disabled={busy||!plans.length}>Create pending subscription</button></div>
   </form>
  </section>}
  <section className="platform-panel"><h2>Subscription history</h2><div className="platform-table-wrap"><table><thead><tr><th>Tenant</th><th>Plan / version</th><th>Status</th><th>Period</th><th>History</th></tr></thead><tbody>{rows.map(s=><tr key={s.id}><td>{s.tenantName} · #{s.tenantId}</td><td>{s.planName} · v{s.planVersion}</td><td>{s.status}</td><td>{s.periodStart} – {s.periodEnd}</td><td><button className="secondary" onClick={()=>void show(s.id)}>Billing and events</button></td></tr>)}</tbody></table></div></section>
  {selected!==null&&<section className="platform-panel"><h2>Subscription #{selected}</h2>{canAdjust&&<><div className="platform-form-grid">
    <label>Permitted administrative transition<select value={target} onChange={e=>setTarget(e.target.value)}><option>PAST_DUE</option><option>GRACE_PERIOD</option><option>SUSPENDED</option><option>CANCEL_AT_PERIOD_END</option><option>CANCELLED</option><option>EXPIRED</option></select></label>
    <label>Reason<input required value={statusReason} onChange={e=>setStatusReason(e.target.value)}/></label>
    <div className="platform-form-actions"><button disabled={busy} onClick={()=>void change()}>Apply status transition</button></div>
   </div><p>Manual status changes cannot set ACTIVE. Subscription reactivation requires a verified platform payment integration event.</p></>}
   <div className="platform-boundary-grid"><article><h3>Invoices</h3>{billing?.invoices?.length?billing.invoices.map((i:any)=><p key={i.id}>{i.invoiceNumber} · {i.currency} {i.total} · {i.status}</p>):<p>No subscription invoices recorded yet.</p>}</article><article><h3>Subscription payments</h3>{billing?.payments?.length?billing.payments.map((p:any)=><p key={p.id}>{p.provider} · {p.amount} {p.currency} · {p.status}</p>):<p>No subscription payments recorded yet. Provider collection is not connected.</p>}</article></div>
   <h3>Subscription events</h3><div className="platform-table-wrap"><table><thead><tr><th>Time</th><th>From</th><th>To</th><th>Actor</th><th>Reason</th></tr></thead><tbody>{events.map(e=><tr key={e.id}><td>{new Date(e.occurredAt).toLocaleString()}</td><td>{e.oldStatus??'—'}</td><td>{e.newStatus}</td><td>{e.actor}</td><td>{e.reason}</td></tr>)}</tbody></table></div>
  </section>}
 </div>;
}
