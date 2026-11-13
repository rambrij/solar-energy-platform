import {useEffect,useRef,useState} from 'react';
import {api} from './api';
type Totals={paidOrders:number;sales:number;tax:number;beforeTax:number;cashReceived:number;changeGiven:number;cashApplied:number;units:number;invoices:number;discounts:number};
type Report={branchId:number;from:string;to:string;generatedAt:string;page:number;size:number;totalElements:number;totalPages:number;pageTotals:Totals;items:{cashier:string;totals:Totals}[]};
const money=(value:number)=>Number(value).toFixed(2);
export default function CashierSalesInsights(){
 const [filters,setFilters]=useState(()=>({from:new Date(Date.now()-6*86400000).toISOString().slice(0,10),to:new Date().toISOString().slice(0,10),size:'12'}));
 const [report,setReport]=useState<Report>(),[busy,setBusy]=useState(false),[error,setError]=useState('');const generation=useRef(0);
 useEffect(()=>()=>{generation.current++;},[]);
 function edit(next:Partial<typeof filters>){generation.current++;setFilters(value=>({...value,...next}));setReport(undefined);setError('');}
 async function load(page=0){const days=(Date.parse(filters.to)-Date.parse(filters.from))/86400000;if(!filters.from||!filters.to||!Number.isFinite(days)||days<0||days>365){setReport(undefined);setError('Choose an inclusive UTC date range of 1-366 days.');return;}
  const current=++generation.current;setBusy(true);setReport(undefined);setError('');
  try{const value:Report=await api(`/reports/cashier-sales?${new URLSearchParams({...filters,page:String(page)})}`);if(generation.current===current)setReport(value);}
  catch{if(generation.current===current)setError('Selling cashier report unavailable. POS_VIEW, POS_REPORT_VIEW and POS_CASHIER_REPORT_VIEW in an active assigned branch are required.');}
  finally{if(generation.current===current)setBusy(false);}
 }
 return <section id="cashier-sales-insights" className="sales-insights" aria-labelledby="cashier-sales-title" aria-busy={busy}><div className="section-heading"><div><span className="eyebrow">Your store team</span><h2 id="cashier-sales-title">Selling cashier insights</h2></div><span className="badge">Paid walk-in sales only</span></div>
 <p>Original selling cashier, including closed shifts. Values use saved sale prices, tax and discounts. Online orders, unpaid reservations and refund payouts are excluded. This report is not a drawer balance or net revenue report.</p>
 <form className="insight-filters" onSubmit={e=>{e.preventDefault();void load();}}><label>Cashier sales from (UTC)<input type="date" required value={filters.from} disabled={busy} onChange={e=>edit({from:e.target.value})}/></label><label>Cashier sales to (UTC)<input type="date" required value={filters.to} disabled={busy} onChange={e=>edit({to:e.target.value})}/></label><label>Cashiers per page<select value={filters.size} disabled={busy} onChange={e=>edit({size:e.target.value})}><option value="12">12</option><option value="24">24</option><option value="48">48</option></select></label><button disabled={busy}>{busy?'Loading cashier sales...':'Load selling cashier insights'}</button></form>
 {error&&<p role="alert">{error}</p>}
 {report&&<><p role="status" className="report-scope">Branch {report.branchId} | {report.from} to {report.to} UTC | Generated {new Date(report.generatedAt).toLocaleString()}</p>
 <div className="insight-kpis"><div><small>Shown-page gross sales</small><strong>{money(report.pageTotals.sales)}</strong><span>{report.pageTotals.paidOrders} paid sales on this page</span></div><div><small>Shown-page allocated tax</small><strong>{money(report.pageTotals.tax)}</strong><span>Before tax {money(report.pageTotals.beforeTax)}</span></div><div><small>Shown-page cash applied</small><strong>{money(report.pageTotals.cashApplied)}</strong><span>Received {money(report.pageTotals.cashReceived)} minus change {money(report.pageTotals.changeGiven)}</span></div><div><small>Shown-page discounts</small><strong>{money(report.pageTotals.discounts)}</strong><span>{report.pageTotals.units} units | {report.pageTotals.invoices} invoices</span></div></div>
 <p>Totals above cover only the cashiers shown on this page. Use Sales insights for whole-branch channel totals.</p>
 {!report.items.length&&<div className="empty-state"><h3>{report.totalElements?'No cashiers on this page':'No paid walk-in sales in this period'}</h3><p>Unpaid drafts and online reservations are not cashier revenue.</p></div>}
 <div className="refund-cashiers">{report.items.map(row=><article key={row.cashier} aria-label={`Sales by ${row.cashier}`}><h3>{row.cashier}</h3><dl><dt>Gross sales / paid orders</dt><dd>{money(row.totals.sales)} / {row.totals.paidOrders}</dd><dt>Before tax / tax</dt><dd>{money(row.totals.beforeTax)} / {money(row.totals.tax)}</dd><dt>Units / invoices</dt><dd>{row.totals.units} / {row.totals.invoices}</dd><dt>Cash received / change</dt><dd>{money(row.totals.cashReceived)} / {money(row.totals.changeGiven)}</dd><dt>Cash applied</dt><dd>{money(row.totals.cashApplied)}</dd><dt>Promotion discounts</dt><dd>{money(row.totals.discounts)}</dd></dl></article>)}</div>
 <nav className="catalogue-pagination" aria-label="Selling cashier report pages"><button className="secondary" disabled={busy||report.page===0} onClick={()=>void load(report.page-1)}>Previous cashiers</button><span>Page {report.page+1} of {Math.max(1,report.totalPages)} | {report.totalElements} cashiers</span><button disabled={busy||report.page>=report.totalPages-1||report.page>=10000} onClick={()=>void load(report.page+1)}>Next cashiers</button>{report.page>0&&<button className="secondary" onClick={()=>void load(0)}>First cashier page</button>}</nav>
 <p>The refund-paying employee can differ from the seller. <a href="#refund-insights">Review cash refund payouts</a>. Cashier identities are not added to the existing gross-sales CSV export by this view permission.</p>
 </>}
 </section>;
}
