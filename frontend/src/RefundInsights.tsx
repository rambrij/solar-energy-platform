import { useEffect, useRef, useState } from 'react';
import { api } from './api';
import ReportExport from './ReportExport';
type Totals = { refunds:number; units:number; cash:number; beforeTax:number; tax:number };
type Report = { branchId:number; from:string; to:string; generatedAt:string; totals:Totals; days:{date:string;totals:Totals}[]; cashiers:{cashier:string;totals:Totals}[] };
const money=(value:number)=>Number(value).toFixed(2);
export default function RefundInsights(){
 const [to,setTo]=useState(()=>new Date().toISOString().slice(0,10));
 const [from,setFrom]=useState(()=>new Date(Date.now()-6*86400000).toISOString().slice(0,10));
 const [report,setReport]=useState<Report>(),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const version=useRef(0);
 useEffect(()=>()=>{version.current++;},[]);
 function changeDate(value:string,end:boolean){setReport(undefined);setError('');if(end)setTo(value);else setFrom(value);}
 async function load(event:React.FormEvent){
  event.preventDefault();const days=(Date.parse(to)-Date.parse(from))/86400000;
  if(!from||!to||!Number.isFinite(days)||days<0||days>365){setReport(undefined);setError('Choose an inclusive UTC date range of 1-366 days.');return;}
  const current=++version.current;setBusy(true);setReport(undefined);setError('');
  try{const value:Report=await api(`/reports/cash-refunds?${new URLSearchParams({from,to})}`);if(version.current===current)setReport(value);}
  catch{if(version.current===current)setError('Cash refund report unavailable. POS_VIEW, POS_REPORT_VIEW and POS_REFUND_VIEW in an active assigned branch are required. Check your connection and retry.');}
  finally{if(version.current===current)setBusy(false);}
 }
 const maximum=Math.max(1,...(report?.days.map(day=>Number(day.totals.cash))??[]));
 return <section id="refund-insights" className="sales-insights refund-insights" aria-labelledby="refund-insights-title" aria-busy={busy}>
  <div className="section-heading"><div><span className="eyebrow">Cash return activity</span><h2 id="refund-insights-title">Cash refund insights</h2></div><span className="badge">Assigned branch only</span></div>
  <p>Paid cash outflows by UTC payout date, including refunds of older sales. These figures are separate from gross online/POS sales and stock-return intake. They are not a till balance or a statutory tax report.</p>
  <form className="insight-filters" onSubmit={load}><label>Refunds from (UTC)<input type="date" required disabled={busy} value={from} onChange={e=>changeDate(e.target.value,false)}/></label><label>Refunds to (UTC)<input type="date" required disabled={busy} value={to} onChange={e=>changeDate(e.target.value,true)}/></label><button disabled={busy}>{busy?'Loading cash refund insights...':'Load cash refund insights'}</button></form>
  {error&&<p role="alert">{error}</p>}
  {report&&<><p role="status" className="report-scope">Branch {report.branchId} | {report.from} to {report.to} UTC | Generated {new Date(report.generatedAt).toLocaleString()}</p>
   <div className="insight-kpis"><div><small>Cash refunded</small><strong>{money(report.totals.cash)}</strong><span>{report.totals.refunds} paid refunds</span></div><div><small>Refunded before tax</small><strong>{money(report.totals.beforeTax)}</strong><span>Saved sale allocation</span></div><div><small>Allocated refunded tax</small><strong>{money(report.totals.tax)}</strong><span>No tax credit note generated</span></div><div><small>Refunded units</small><strong>{report.totals.units}</strong><span>Stock disposition recorded separately</span></div></div>
   {!report.totals.refunds&&<div className="empty-state"><h3>No paid cash refunds in this period</h3><p>Pending stock returns and rejected/uncertain requests are not recorded payouts.</p></div>}
   <h3>Paying cashier breakdown</h3><div className="refund-cashiers">{report.cashiers.map(row=><article key={row.cashier} aria-label={`Refunds paid by ${row.cashier}`}><h4>{row.cashier}</h4><dl><dt>Cash paid</dt><dd>{money(row.totals.cash)}</dd><dt>Refunds / units</dt><dd>{row.totals.refunds} / {row.totals.units}</dd><dt>Before tax / allocated tax</dt><dd>{money(row.totals.beforeTax)} / {money(row.totals.tax)}</dd></dl></article>)}</div>
   <h3>Daily cash refunds</h3><ul className="sales-trend">{report.days.map(day=><li key={day.date}><time dateTime={day.date}>{day.date}</time><meter min="0" max={maximum} value={Number(day.totals.cash)} aria-label={`Cash refunded on ${day.date}`}/><span>{money(day.totals.cash)} | {day.totals.refunds} paid refunds</span></li>)}</ul><p>Use the refund journal for individual acknowledgements and the cash drawer for counted cash and reconciliation. The paying cashier can differ from the original seller.</p>
   <ReportExport kind="cash-refunds" from={report.from} to={report.to} disabled={busy}/>
  </>}
 </section>;
}
