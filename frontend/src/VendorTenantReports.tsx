import {useState,type FormEvent} from 'react';
import {vendorApi} from './api';
import {usePermissions} from './usePermissions';

type Totals={paidOrders:number;sales:number;tax:number;beforeTax:number;cashReceived:number;changeGiven:number;cashApplied:number;units:number;invoices:number;distinctCustomers:number;unpaidOrders:number;unpaidAmount:number;cancelledOrders:number;expiredOrders:number;discounts:number};
type Overview={from:string;to:string;generatedAt:string;totals:Totals;channels:{channel:string;totals:Totals}[];days:{date:string;totals:Totals}[];outlets:{outlet:string;channel:string;orders:number;paidOrders:number;paidSales:number;unpaidOrders:number;unpaidAmount:number}[];topProducts:{productId:number;productName:string;units:number;sales:number;discounts:number}[];confirmedTenders:{channel:string;method:string;origin:string;tenders:number;confirmedAmount:number}[]};
type RefundTotals={refunds:number;units:number;cash:number;beforeTax:number;tax:number};
type Refunds={from:string;to:string;generatedAt:string;totals:RefundTotals;days:{date:string;totals:RefundTotals}[]};
const money=(value:number)=>Number(value??0).toFixed(2);
const channelName=(value:string)=>value==='IN_STORE'?'Store POS':'Online marketplace';
export default function VendorTenantReports(){
 const {hasPermission}=usePermissions();
 const [from,setFrom]=useState(()=>new Date(Date.now()-6*86400000).toISOString().slice(0,10));
 const [to,setTo]=useState(()=>new Date().toISOString().slice(0,10));
 const [overview,setOverview]=useState<Overview>();
 const [refunds,setRefunds]=useState<Refunds>();
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState('');
 const [refundError,setRefundError]=useState('');
 async function load(event:FormEvent<HTMLFormElement>){
  event.preventDefault();
  const days=(Date.parse(to)-Date.parse(from))/86400000;
  if(!from||!to||!Number.isFinite(days)||days<0||days>365){setError('Choose an inclusive UTC date range of 1 to 366 days.');return;}
  setBusy(true);setError('');setRefundError('');setOverview(undefined);setRefunds(undefined);
  const params=new URLSearchParams({from,to});
  try{
   const value=await vendorApi('/reports/overview?'+params.toString()) as Overview;
   setOverview(value);
  }catch{
   setError('Vendor report unavailable. POS_REPORT_VIEW in an active vendor membership is required. Check the date range and connectivity.');
   setBusy(false);return;
  }
  if(hasPermission('POS_REFUND_VIEW')){
   try{
    const refundValue=await vendorApi('/reports/refunds?'+params.toString()) as Refunds;
    setRefunds(refundValue);
   }catch{
    setRefundError('Cash refund summary unavailable. POS_REFUND_VIEW is required for refund details.');
   }
  }
  setBusy(false);
 }
 return <section className="sales-insights" aria-labelledby="vendor-report-title" aria-busy={busy}>
  <div className="section-heading"><div><span className="eyebrow">Tenant-wide analytics</span><h2 id="vendor-report-title">Vendor performance</h2></div><span className="badge">All assigned branches and online sales</span></div>
  <p>Aggregated activity for this vendor tenant only. Online and store sales, confirmed tenders and paid POS cash refunds are reported as separate measures; tender and item breakdowns are not added again to order totals.</p>
  <form className="insight-filters" onSubmit={load}>
   <label>Activity from (UTC)<input type="date" required disabled={busy} value={from} onChange={e=>setFrom(e.target.value)}/></label>
   <label>Activity to (UTC)<input type="date" required disabled={busy} value={to} onChange={e=>setTo(e.target.value)}/></label>
   <button disabled={busy}>{busy?'Loading vendor reports…':overview?'Refresh vendor reports':'Load vendor reports'}</button>
  </form>
  {error&&<p role="alert">{error}</p>}
  {overview&&<>
   <p className="report-scope" role="status">{overview.from} to {overview.to} UTC · Generated {new Date(overview.generatedAt).toLocaleString()}</p>
   <div className="insight-kpis">
    <div><small>Paid orders</small><strong>{overview.totals.paidOrders}</strong><span>{overview.totals.distinctCustomers} distinct customers</span></div>
    <div><small>Recorded sales incl. tax</small><strong>{money(overview.totals.sales)}</strong><span>{money(overview.totals.beforeTax)} before tax</span></div>
    <div><small>Tax recorded</small><strong>{money(overview.totals.tax)}</strong><span>{overview.totals.invoices} invoices issued</span></div>
    <div><small>Unpaid reservations</small><strong>{overview.totals.unpaidOrders}</strong><span>{money(overview.totals.unpaidAmount)} excluded from sales</span></div>
   </div>
   <h3>Sales by channel</h3>
   <div className="channel-grid">{overview.channels.map(row=><section key={row.channel} aria-label={channelName(row.channel)}><h4>{channelName(row.channel)}</h4><dl className="channel-facts"><div><dt>Recorded sales</dt><dd>{money(row.totals.sales)}</dd></div><div><dt>Paid orders / units</dt><dd>{row.totals.paidOrders} / {row.totals.units}</dd></div><div><dt>Discounts / tax</dt><dd>{money(row.totals.discounts)} / {money(row.totals.tax)}</dd></div><div><dt>Unpaid / cancelled / expired</dt><dd>{row.totals.unpaidOrders} / {row.totals.cancelledOrders} / {row.totals.expiredOrders}</dd></div></dl></section>)}</div>
   <h3>Daily sales across the tenant</h3>
   <ul className="sales-trend">{overview.days.map(day=><li key={day.date}><time dateTime={day.date}>{day.date}</time><meter min="0" max={Math.max(1,...overview.days.map(x=>Number(x.totals.sales)))} value={Number(day.totals.sales)} aria-label={'Recorded sales on '+day.date}/><span>{money(day.totals.sales)} · {day.totals.paidOrders} paid</span></li>)}</ul>
   <h3>Outlet / channel performance</h3>
   {!overview.outlets.length?<p className="empty-state">No outlet or online sales in this period.</p>:<div className="table-scroll"><table className="data-table"><thead><tr><th>Outlet / channel</th><th>Channel</th><th>Orders</th><th>Paid sales</th><th>Unpaid</th></tr></thead><tbody>{overview.outlets.map((row,i)=><tr key={row.outlet+'-'+row.channel+'-'+i}><td>{row.outlet}</td><td>{channelName(row.channel)}</td><td>{row.orders}</td><td>{money(row.paidSales)}</td><td>{row.unpaidOrders} · {money(row.unpaidAmount)}</td></tr>)}</tbody></table></div>}
   <h3>Top 10 products by recorded sales</h3>
   {!overview.topProducts.length?<p className="empty-state">No paid product lines in this period.</p>:<div className="table-scroll"><table className="data-table"><thead><tr><th>Product</th><th>Units</th><th>Sales incl. tax</th><th>Discounts</th></tr></thead><tbody>{overview.topProducts.map(row=><tr key={row.productId}><td>{row.productName}</td><td>{row.units}</td><td>{money(row.sales)}</td><td>{money(row.discounts)}</td></tr>)}</tbody></table></div>}
   <h3>Confirmed tenders</h3>
   <p>Only confirmed payment attempts are included; pending or unknown terminal attempts are not treated as paid.</p>
   {!overview.confirmedTenders.length?<p className="empty-state">No confirmed tenders in this period.</p>:<div className="table-scroll"><table className="data-table"><thead><tr><th>Channel</th><th>Method</th><th>Origin</th><th>Count</th><th>Confirmed amount</th></tr></thead><tbody>{overview.confirmedTenders.map((row,i)=><tr key={row.channel+'-'+row.method+'-'+row.origin+'-'+i}><td>{channelName(row.channel)}</td><td>{row.method}</td><td>{row.origin}</td><td>{row.tenders}</td><td>{money(row.confirmedAmount)}</td></tr>)}</tbody></table></div>}
   {hasPermission('POS_REFUND_VIEW')&&<>
    <h3>Paid POS cash refunds</h3>
    {refundError&&<p role="alert">{refundError}</p>}
    {refunds&&<><div className="insight-kpis"><div><small>Cash refunded</small><strong>{money(refunds.totals.cash)}</strong><span>{refunds.totals.refunds} paid refunds</span></div><div><small>Refunded units</small><strong>{refunds.totals.units}</strong><span>Stock disposition is separate</span></div><div><small>Refunded before tax</small><strong>{money(refunds.totals.beforeTax)}</strong><span>Saved allocation</span></div><div><small>Allocated tax</small><strong>{money(refunds.totals.tax)}</strong><span>No credit note implied</span></div></div>
     <ul className="sales-trend">{refunds.days.map(day=><li key={day.date}><time dateTime={day.date}>{day.date}</time><span>{money(day.totals.cash)} · {day.totals.refunds} paid refunds</span></li>)}</ul>
    </>}
   </>}
   <p>Scope note: this is operational vendor reporting, not a general ledger, bank statement, settlement reconciliation or statutory tax filing. POS cash refunds are separate paid outflows, and the invoice register remains the source for immutable invoice records.</p>
  </>}
 </section>;
}
