import {useCallback,useEffect,useState,type FormEvent} from 'react';
import {vendorApi} from './api';
import {useSession} from './session';

type Lead={id:number;status:string;contactName:string;productName:string|null;assignedTo:string|null};
type Product={id:number;sku:string;name:string;price:number|string;taxRate:number|string;listPrice:number|string;discountPercent:number|string;inStock:boolean};
type QuoteLine={productId:number;name:string;quantity:number;listPrice:number|string;unitPrice:number|string;discountPercent:number|string;taxRate:number|string;tax:number|string;total:number|string};
type Revision={id:number;revisionNumber:number;status:string;currency:string;validUntil:string;subtotal:number|string;taxTotal:number|string;total:number|string;terms:string|null;sentAt:string|null;items:QuoteLine[]};
type Quote={id:number;enquiryId:number;status:string;acceptedRevisionNumber:number|null;convertedOrderId:string|null;version:number;revisions:Revision[]};
type DraftLine={productId:string;quantity:number};
const money=(value:number|string)=>Number(value).toFixed(2);
const newest=(quote:Quote)=>quote.revisions.slice().sort((a,b)=>b.revisionNumber-a.revisionNumber)[0];

export default function VendorQuotations(){
 const {account}=useSession();
 const permissions=account?.permissions??[];
 const canUpdate=permissions.includes('LEAD_UPDATE');
 const [leads,setLeads]=useState<Lead[]>([]);
 const [products,setProducts]=useState<Product[]>([]);
 const [quotes,setQuotes]=useState<Quote[]>([]);
 const [selectedQuoteId,setSelectedQuoteId]=useState<number|null>(null);
 const [leadId,setLeadId]=useState('');
 const [items,setItems]=useState<DraftLine[]>([{productId:'',quantity:1}]);
 const [validityDays,setValidityDays]=useState('30');
 const [terms,setTerms]=useState('');
 const [reason,setReason]=useState('');
 const [editing,setEditing]=useState(false);
 const [loading,setLoading]=useState(true);
 const [saving,setSaving]=useState(false);
 const [error,setError]=useState('');
 const [notice,setNotice]=useState('');

 const refresh=useCallback(async()=>{
  setLoading(true);setError('');setLeads([]);setProducts([]);setQuotes([]);setSelectedQuoteId(null);
  try{
   const [leadRows,productRows,quoteRows]=await Promise.all([
    vendorApi('/leads') as Promise<Lead[]>,
    vendorApi('/quotation-catalogue') as Promise<Product[]>,
    vendorApi('/quotations') as Promise<Quote[]>
   ]);
   setLeads(leadRows.filter(lead=>lead.status!=='LOST'));
   setProducts(productRows);
   setQuotes(quoteRows);
   setSelectedQuoteId(current=>current??(quoteRows.length?quoteRows[0].id:null));
  }catch(e){setError((e as Error).message||'Quotation workspace could not be loaded.');}
  finally{setLoading(false);}
 },[]);

 useEffect(()=>{void refresh();},[refresh,account?.tenantId,account?.username]);

 const selectedQuote=quotes.find(quote=>quote.id===selectedQuoteId)??null;
 const latest=selectedQuote?newest(selectedQuote):undefined;
 const updateLine=(index:number,patch:Partial<DraftLine>)=>setItems(current=>current.map((line,i)=>i===index?{...line,...patch}:line));
 function startRevision(quote:Quote){
  const current=newest(quote);
  if(!current||quote.status!=='OPEN')return;
  setSelectedQuoteId(quote.id);setLeadId(String(quote.enquiryId));setEditing(true);
  setItems(current.items.map(line=>({productId:String(line.productId),quantity:line.quantity})));
  setValidityDays('30');setTerms(current.terms??'');setReason('');setNotice('');setError('');
 }
 async function submit(event:FormEvent){
  event.preventDefault();
  if(!reason.trim()||!items.length||items.some(line=>!line.productId||!Number.isInteger(line.quantity)||line.quantity<1||line.quantity>10000))return;
  setSaving(true);setError('');setNotice('');
  try{
   const payload={items:items.map(line=>({productId:Number(line.productId),quantity:line.quantity})),validityDays:Number(validityDays),terms:terms.trim()||null,reason:reason.trim()};
   const revisionPath=selectedQuote?'/quotations/'+selectedQuote.id+'/revisions':'';
   const result=editing&&selectedQuote
    ?await vendorApi(revisionPath,'POST',payload) as Quote
    :await vendorApi('/quotations','POST',{enquiryId:Number(leadId),...payload}) as Quote;
   setQuotes(current=>[result,...current.filter(quote=>quote.id!==result.id)]);
   setSelectedQuoteId(result.id);setEditing(false);setReason('');
   setNotice(editing?'Quotation revision sent. Prices are saved in that revision.':'Quotation sent. The customer can now review it in My Quotations.');
  }catch(e){setError((e as Error).message||'Quotation could not be sent.');}
  finally{setSaving(false);}
 }

 return <section className="vendor-quotations" aria-labelledby="vendor-quotations-title">
  <header><h2 id="vendor-quotations-title">Vendor quotations</h2><p>Create customer offers from verified enquiries. Each sent revision stores the price, tax and discount snapshots used at that time.</p></header>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  {loading&&<p role="status">Loading leads, products and quotations…</p>}
  {!loading&&<section aria-label="Quotation register">
   <h3>Quotation register</h3>
   {!quotes.length?<p className="empty-state">No quotations have been sent for the leads visible to your role.</p>:<ul>{quotes.map(quote=>{
    const revision=newest(quote);const lead=leads.find(item=>item.id===quote.enquiryId);
    return <li key={quote.id} className="quotation-register-item">
     <h4>Quotation #{quote.id} · {lead?.contactName??`Lead #${quote.enquiryId}`}</h4>
     <p>Status: {quote.status}{revision? ` · Revision ${revision.revisionNumber} · ${revision.status}` : ''}</p>
     {revision&&<p>{revision.currency} {money(revision.total)} · Valid until {new Date(revision.validUntil).toLocaleDateString()}</p>}
     <button type="button" className="secondary" onClick={()=>setSelectedQuoteId(quote.id)}>View quotation</button>
     {canUpdate&&quote.status==='OPEN'&&<button type="button" className="secondary" disabled={saving} onClick={()=>startRevision(quote)}>Create next revision</button>}
    </li>;
   })}</ul>}
  </section>}
  {selectedQuote&&latest&&<section aria-label={`Quotation ${selectedQuote.id} details`}>
   <h3>Quotation #{selectedQuote.id} revision history</h3>
   {selectedQuote.revisions.slice().sort((a,b)=>a.revisionNumber-b.revisionNumber).map(revision=><article key={revision.id} className="quotation-revision">
    <h4>Revision {revision.revisionNumber} · {revision.status}</h4>
    <p>{revision.currency} {money(revision.total)} · Valid until {new Date(revision.validUntil).toLocaleString()}</p>
    {revision.terms&&<p>{revision.terms}</p>}
    <ul>{revision.items.map(line=><li key={line.productId}>{line.name} × {line.quantity} · {money(line.unitPrice)} each + {money(line.tax)} tax · {money(line.total)}</li>)}</ul>
   </article>)}
  </section>}
  {canUpdate&&<section aria-label={editing?'Create quotation revision':'Create quotation'}>
   <h3>{editing&&selectedQuote?'Create revision for quotation #'+selectedQuote.id:'Send a new quotation'}</h3>
   <form onSubmit={submit}>
    {!editing&&<label>Customer enquiry
     <select required value={leadId} onChange={event=>setLeadId(event.target.value)}>
      <option value="">Select an assigned lead</option>
      {leads.map(lead=><option key={lead.id} value={lead.id}>{lead.contactName}{lead.productName?' · '+lead.productName:''} · {lead.status}</option>)}
     </select>
    </label>}
    {items.map((line,index)=><fieldset key={index}>
     <legend>Quotation line {index+1}</legend>
     <label>Product
      <select required value={line.productId} onChange={event=>updateLine(index,{productId:event.target.value})}>
       <option value="">Select a published product</option>
       {products.map(product=><option key={product.id} value={product.id}>{product.name} · {money(product.price)} + {product.taxRate}% tax</option>)}
      </select>
     </label>
     <label>Quantity
      <input type="number" min="1" max="10000" step="1" required value={line.quantity} onChange={event=>updateLine(index,{quantity:Number(event.target.value)})}/>
     </label>
     {products.find(product=>String(product.id)===line.productId)&&<p>Current catalogue price: {money(products.find(product=>String(product.id)===line.productId)!.price)} each before tax. The server snapshots the approved price when the quotation is sent.</p>}
     {items.length>1&&<button type="button" className="secondary" onClick={()=>setItems(current=>current.filter((_,i)=>i!==index))}>Remove line {index+1}</button>}
    </fieldset>)}
    <button type="button" className="secondary" disabled={items.length>=100} onClick={()=>setItems(current=>[...current,{productId:'',quantity:1}])}>Add product line</button>
    <label>Validity (days)
     <input type="number" min="1" max="90" step="1" required value={validityDays} onChange={event=>setValidityDays(event.target.value)}/>
    </label>
    <label>Quotation terms
     <textarea maxLength={4000} rows={3} value={terms} onChange={event=>setTerms(event.target.value)}/>
    </label>
    <label>Reason for sending
     <input required maxLength={500} value={reason} onChange={event=>setReason(event.target.value)}/>
    </label>
    <button type="submit" disabled={saving||loading||!canUpdate||!reason.trim()||!items.length||items.some(line=>!line.productId||!Number.isInteger(line.quantity)||line.quantity<1||line.quantity>10000)||(!editing&&!leadId)||!Number.isInteger(Number(validityDays))||Number(validityDays)<1||Number(validityDays)>90}>
     {saving?'Sending…':editing?'Send quotation revision':'Send quotation'}
    </button>
    {editing&&<button type="button" className="secondary" disabled={saving} onClick={()=>{setEditing(false);setItems([{productId:'',quantity:1}]);setReason('');}}>Cancel revision</button>}
   </form>
  </section>}
  <button type="button" className="secondary" disabled={loading||saving} onClick={()=>void refresh()}>Refresh quotations</button>
 </section>;
}
