import {useEffect,useState} from 'react';
import {marketplaceApi} from './marketplaceApi';

type QuoteLine={productId:number;name:string;quantity:number;listPrice:number|string;unitPrice:number|string;discountPercent:number|string;taxRate:number|string;tax:number|string;total:number|string};
type Revision={id:number;revisionNumber:number;status:string;currency:string;validUntil:string;subtotal:number|string;taxTotal:number|string;total:number|string;terms:string|null;sentAt:string|null;items:QuoteLine[]};
type Quote={id:number;enquiryId:number;status:string;acceptedRevisionNumber:number|null;convertedOrderId:string|null;version:number;revisions:Revision[]};
type Order={id:string;status:string;total:number;expiresAt:string;items:Array<{productId:number;name:string;quantity:number;total:number}>};
type DecisionResult={quotation:Quote;order:Order|null};
const money=(value:number|string)=>Number(value).toFixed(2);
const newest=(quote:Quote)=>quote.revisions.slice().sort((a,b)=>b.revisionNumber-a.revisionNumber)[0];

export default function CustomerQuotations(){
 const [quotes,setQuotes]=useState<Quote[]>([]);
 const [reasons,setReasons]=useState<Record<number,string>>({});
 const [loading,setLoading]=useState(true);
 const [savingId,setSavingId]=useState<number|null>(null);
 const [error,setError]=useState('');
 const [notice,setNotice]=useState('');
 const [orders,setOrders]=useState<Record<number,string>>({});

 async function refresh(){
  setLoading(true);setError('');
  try{setQuotes(await marketplaceApi('/api/v1/customer/quotations') as Quote[]);}
  catch(e){setError((e as Error).message||'Your quotations could not be loaded.');}
  finally{setLoading(false);}
 }
 useEffect(()=>{void refresh();},[]);

 async function decide(quote:Quote,accept:boolean){
  const revision=newest(quote);
  const reason=(reasons[quote.id]??'').trim();
  if(!revision||!accept&&!reason)return;
  setSavingId(quote.id);setError('');setNotice('');
  try{
   const result=await marketplaceApi(
    '/api/v1/customer/quotations/'+quote.id+'/revisions/'+revision.revisionNumber+'/decision',
    'POST',{accept,reason:reason||null}
   ) as DecisionResult;
   setQuotes(current=>current.map(item=>item.id===quote.id?result.quotation:item));
   if(result.order)setOrders(current=>({...current,[quote.id]:result.order!.id}));
   setNotice(accept?'Quotation accepted. Your unpaid order reservation has been created.':'Quotation revision rejected.');
   setReasons(current=>({...current,[quote.id]:''}));
  }catch(e){setError((e as Error).message||'Your decision was not confirmed. Reload quotations before retrying.');}
  finally{setSavingId(null);}
 }

 return <section className="customer-quotations" aria-labelledby="customer-quotations-title">
  <header><h2 id="customer-quotations-title">My solar quotations</h2><p>Review offers sent by vendors. Each revision shows the prices and taxes that were saved when it was sent.</p></header>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  {loading&&<p role="status">Loading your quotations…</p>}
  {!loading&&!quotes.length&&!error&&<p className="empty-state">No quotations have been sent to your verified customer account yet.</p>}
  <div className="customer-quotation-list">{quotes.map(quote=>{
   const current=newest(quote);
   const canDecide=quote.status==='OPEN'&&current?.status==='SENT'&&new Date(current.validUntil).getTime()>Date.now();
   return <article className="customer-quotation-card" key={quote.id}>
    <h3>Quotation #{quote.id}</h3>
    <p>Status: {quote.status}{quote.convertedOrderId? ` · Order ${quote.convertedOrderId}`:''}</p>
    {quote.revisions.slice().sort((a,b)=>a.revisionNumber-b.revisionNumber).map(revision=><section key={revision.id} aria-label={`Quotation revision ${revision.revisionNumber}`}>
     <h4>Revision {revision.revisionNumber} · {revision.status}</h4>
     <p>{revision.currency} {money(revision.total)} · Valid until {new Date(revision.validUntil).toLocaleString()}</p>
     <dl><dt>Subtotal</dt><dd>{money(revision.subtotal)}</dd><dt>Tax</dt><dd>{money(revision.taxTotal)}</dd><dt>Total</dt><dd>{money(revision.total)}</dd></dl>
     {revision.terms&&<p>Terms: {revision.terms}</p>}
     <ul>{revision.items.map(line=><li key={line.productId}>{line.name} × {line.quantity} · {money(line.unitPrice)} each · Tax {money(line.tax)} · Total {money(line.total)}</li>)}</ul>
    </section>)}
    {canDecide&&current&&<div>
     <label>Reason (required to reject)
      <textarea maxLength={500} rows={2} value={reasons[quote.id]??''} onChange={event=>setReasons(value=>({...value,[quote.id]:event.target.value}))}/>
     </label>
     <div className="quotation-decision-actions">
      <button type="button" disabled={savingId!==null} onClick={()=>void decide(quote,true)}>Accept quotation</button>
      <button type="button" className="secondary" disabled={savingId!==null||!(reasons[quote.id]??'').trim()} onClick={()=>void decide(quote,false)}>Reject quotation</button>
     </div>
    </div>}
    {orders[quote.id]&&<p role="status">Order reservation created: {orders[quote.id]}. This is unpaid; no payment has been taken. <a href="/marketplace/purchases">View purchases</a>.</p>}
    {!canDecide&&quote.status==='OPEN'&&<p>This revision is no longer available for a decision. Ask the vendor for a new revision.</p>}
   </article>;
  })}</div>
  <button type="button" className="secondary" disabled={loading||savingId!==null} onClick={()=>void refresh()}>Refresh quotations</button>
 </section>;
}
