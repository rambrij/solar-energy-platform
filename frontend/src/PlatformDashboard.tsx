import {useEffect,useState} from 'react';
import {platformApi,ApiError} from './api';
import {money} from './platformRules';
type Overview={tenantsByStatus:Record<string,number>;subscriptionsByStatus:Record<string,number>;subscriptionsByPlan:Record<string,number>;openInvoices:{currency:string;count:number;total:number}[];generatedAt:string};
type Health={status:string;environment:string;databaseReachable:boolean;paymentProviderConfigured:boolean;merchantConfigured:boolean;mockVendorPaymentsEnabled:boolean;mockSubscriptionProvider:boolean;issues:string[];checkedAt:string};
const Counts=({title,values}:{title:string;values:Record<string,number>})=><div className="platform-card"><h3>{title}</h3>{Object.keys(values).length===0?<p className="bill-hint">None yet.</p>:<dl className="platform-counts">{Object.entries(values).map(([key,value])=><div key={key}><dt>{key.replaceAll('_',' ')}</dt><dd>{value}</dd></div>)}</dl>}</div>;
export default function PlatformDashboard({can}:{can:(permission:string)=>boolean}){
 const [overview,setOverview]=useState<Overview>(),[health,setHealth]=useState<Health>(),[error,setError]=useState('');
 const allowed=can('PLATFORM_ANALYTICS_VIEW');
 const load=()=>{setError('');Promise.all([platformApi<Overview>('/overview'),platformApi<Health>('/health')]).then(([o,h])=>{setOverview(o);setHealth(h);}).catch(e=>setError(e instanceof ApiError?`Could not load the dashboard (${e.status}).`:'Could not load the dashboard.'));};
 useEffect(()=>{if(allowed)load();},[allowed]);
 if(!allowed)return <><section id="platform-dashboard" aria-label="Dashboard"><h2>Dashboard</h2><p role="status">Your role does not include platform analytics.</p></section><section id="platform-health" aria-label="System health"><h2>System health</h2><p role="status">Your role does not include platform analytics.</p></section></>;
 return <>
  <section id="platform-dashboard" aria-label="Dashboard"><div className="section-heading"><h2>Dashboard</h2><button type="button" className="secondary" onClick={load}>Refresh</button></div>
   <p className="bill-hint">Aggregated platform data only. Tenant-private commerce data is never shown here.</p>
   {error&&<p role="alert">{error}</p>}
   {overview&&<div className="platform-grid"><Counts title="Tenants by status" values={overview.tenantsByStatus}/><Counts title="Subscriptions by status" values={overview.subscriptionsByStatus}/><Counts title="Subscriptions by plan" values={overview.subscriptionsByPlan}/>
    <div className="platform-card"><h3>Open subscription invoices</h3>{overview.openInvoices.length===0?<p className="bill-hint">Nothing outstanding.</p>:<ul>{overview.openInvoices.map(row=><li key={row.currency}>{row.count} open · {money(row.total,row.currency)}</li>)}</ul>}</div></div>}
  </section>
  <section id="platform-health" aria-label="System health"><h2>System health</h2>
   {health?<><p><span className={`badge platform-health-${health.status.toLowerCase()}`}>{health.status}</span> {health.environment}</p>
    <ul className="platform-checks"><li>Database {health.databaseReachable?'reachable':'unreachable'}</li><li>Subscription payment provider {health.paymentProviderConfigured?'configured':'not configured'}</li><li>Platform merchant account {health.merchantConfigured?'configured':'not configured'}</li></ul>
    {health.issues.length>0&&<ul aria-label="Health issues">{health.issues.map(issue=><li key={issue} className={issue.startsWith('CRITICAL')?'platform-critical':undefined}>{issue}</li>)}</ul>}</>:<p className="bill-hint">Loading health…</p>}
  </section></>;
}
