import {useState,type FormEvent} from 'react';
import {vendorApi} from './api';

type Invoice={
 orderId:string;invoiceNumber:string;sellerName:string;outletName:string;total:number;
 issuedAt:string;channel:string;paymentMethod:string;paymentReference:string|null;confirmedPayments:number;
};
type Register={
 items:Invoice[];totals:{invoiceCount:number;invoicedAmount:number;confirmedPayments:number;onlineInvoices:number;inStoreInvoices:number};
 from:string;to:string;channel:string;page:number;size:number;totalElements:number;totalPages:number;generatedAt:string;
};
const amount=(value:number)=>Number(value??0).toFixed(2);
const channelLabel=(value:string)=>value==='ONLINE'?'Online marketplace':'Store POS';
export default function VendorInvoiceRegister(){
 const [from,setFrom]=useState(()=>new Date(Date.now()-29*86400000).toISOString().slice(0,10));
 const [to,setTo]=useState(()=>new Date().toISOString().slice(0,10));
 const [channel,setChannel]=useState('ALL');
 const [register,setRegister]=useState<Register>();
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState('');
 async function load(event:FormEvent<HTMLFormElement>){
  event.preventDefault();
  const days=(Date.parse(to)-Date.parse(from))/86400000;
  if(!from||!to||!Number.isFinite(days)||days<0||days>365){setError('Choose an inclusive UTC date range of 1 to 366 days.');return;}
  setBusy(true);setError('');
  try{
   const params=new URLSearchParams({from,to,channel,page:'0',size:'50'});
   const value=await vendorApi('/invoices?'+params.toString()) as Register;
   setRegister(value);
  }catch{setError('Invoice register unavailable. INVOICE_VIEW permission and an active vendor membership are required. Check your connection and retry.');}
  finally{setBusy(false);}
 }
 return <section className="sales-insights" aria-labelledby="invoice-register-title" aria-busy={busy}>
  <div className="section-heading"><div><span className="eyebrow">Vendor finance</span><h2 id="invoice-register-title">Invoice register</h2></div><span className="badge">Tenant-wide view</span></div>
  <p>Invoices and confirmed payment attempts issued for this vendor tenant. The register combines online and store sales without exposing another vendor's orders.</p>
  <form className="insight-filters" onSubmit={load}>
   <label>Invoices from (UTC)<input type="date" required disabled={busy} value={from} onChange={e=>setFrom(e.target.value)}/></label>
   <label>Invoices to (UTC)<input type="date" required disabled={busy} value={to} onChange={e=>setTo(e.target.value)}/></label>
   <label>Sales channel<select disabled={busy} value={channel} onChange={e=>setChannel(e.target.value)}><option value="ALL">All channels</option><option value="ONLINE">Online marketplace</option><option value="IN_STORE">Store POS</option></select></label>
   <button disabled={busy}>{busy?'Loading invoice register…':register?'Refresh invoice register':'Load invoice register'}</button>
  </form>
  {error&&<p role="alert">{error}</p>}
  {register&&<>
   <p className="report-scope" role="status">{register.from} to {register.to} UTC · {register.channel==='ALL'?'All channels':channelLabel(register.channel)} · Generated {new Date(register.generatedAt).toLocaleString()}</p>
   <div className="insight-kpis">
    <div><small>Invoices issued</small><strong>{register.totals.invoiceCount}</strong><span>{register.totals.onlineInvoices} online · {register.totals.inStoreInvoices} store</span></div>
    <div><small>Gross invoiced</small><strong>{amount(register.totals.invoicedAmount)}</strong><span>Stored invoice totals</span></div>
    <div><small>Confirmed payments</small><strong>{amount(register.totals.confirmedPayments)}</strong><span>Confirmed checkout tenders only</span></div>
    <div><small>Invoice records</small><strong>{register.totalElements}</strong><span>Page {register.totalPages?register.page+1:0} of {register.totalPages}</span></div>
   </div>
   {!register.items.length?<div className="empty-state"><h3>No issued invoices in this period</h3><p>Only immutable invoices issued for paid vendor-owned orders appear here.</p></div>:
    <div className="table-scroll"><table className="data-table"><caption>Vendor invoice register</caption><thead><tr><th>Issued (UTC)</th><th>Invoice</th><th>Order</th><th>Channel</th><th>Outlet</th><th>Payment</th><th>Invoice total</th><th>Confirmed</th></tr></thead>
     <tbody>{register.items.map(row=><tr key={row.orderId}><td>{new Date(row.issuedAt).toLocaleString('en-IN',{timeZone:'UTC'})}</td><td>{row.invoiceNumber}</td><td><code>{row.orderId}</code></td><td>{channelLabel(row.channel)}</td><td>{row.outletName}</td><td>{row.paymentMethod}{row.paymentReference&&<small>{row.paymentReference}</small>}</td><td>{amount(row.total)}</td><td>{amount(row.confirmedPayments)}</td></tr>)}</tbody>
    </table></div>}
   <p>Scope note: this is an invoice and confirmed-tender register, not a statutory tax filing, general ledger, bank statement, payout reconciliation or credit-note register. Invoice delivery, correction and void actions remain unavailable until their audited lifecycle is implemented.</p>
  </>}
 </section>;
}
