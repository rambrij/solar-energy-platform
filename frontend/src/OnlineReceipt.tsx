import {useEffect,useRef,useState} from 'react';
import {marketplaceApi} from './marketplaceApi';
import {printDocument} from './printDocument';
export type OnlineReceiptData={orderId:string;seller:string|null;outlet:string|null;issuedAt:string;total:number;paymentMethod:string;copy:boolean;items:{productId:number;name:string;quantity:number;unitPrice:number;taxRate:number;discount:number;tax:number;total:number}[];invoices:{number:string;seller:string;outlet:string;total:number}[]};
const money=(amount:number)=>Number(amount).toFixed(2);
export default function OnlineReceipt({orderId,onClose}:{orderId:string;onClose:()=>void}){
 const [receipt,setReceipt]=useState<OnlineReceiptData>(),[busy,setBusy]=useState(false),[error,setError]=useState(''),[revision,setRevision]=useState(0),[printRequested,setPrintRequested]=useState(false);
 const alive=useRef(true);
 useEffect(()=>{alive.current=true;return()=>{alive.current=false;};},[]);
 useEffect(()=>{let active=true;setReceipt(undefined);setError('');setBusy(true);
  marketplaceApi(`/api/customer/orders/${encodeURIComponent(orderId)}/receipt`).then((value:OnlineReceiptData)=>{if(active){if(value.orderId!==orderId)throw Error('Receipt mismatch');setReceipt(value);}}).catch(()=>{if(active)setError('Receipt unavailable. Check your account and connection, then retry.');}).finally(()=>{if(active)setBusy(false);});
  return()=>{active=false;};
 },[orderId,revision]);
 useEffect(()=>{if(printRequested&&receipt?.copy){printDocument('online');setPrintRequested(false);}},[printRequested,receipt]);
 async function print(){setBusy(true);setError('');try{const value:OnlineReceiptData=await marketplaceApi(`/api/customer/orders/${encodeURIComponent(orderId)}/receipt-copy`,'POST',{});if(!alive.current)return;if(value.orderId!==orderId||!value.copy)throw Error('Copy not confirmed');setReceipt(value);setPrintRequested(true);}catch{if(alive.current)setError('Receipt copy was not confirmed. Printing was not started. Retry to generate another audited copy.');}finally{if(alive.current)setBusy(false);}}
 return <section className="online-receipt sale-receipt structured-receipt" aria-label="Online order receipt" aria-busy={busy}>
  <div className="receipt-actions"><button className="secondary" onClick={onClose}>Close online receipt</button></div>
  {error&&<p role="alert" className="receipt-error">{error}</p>}{!receipt&&<button disabled={busy} onClick={()=>setRevision(value=>value+1)}>{busy?'Loading receipt…':'Retry online receipt'}</button>}
  {receipt&&<><header className="receipt-heading"><div><span className="eyebrow">Online vendor order</span><h2>{receipt.copy?'COPY · ':''}Order receipt</h2><strong>{receipt.seller??'Seller not recorded'}</strong><p>{receipt.outlet??'Outlet not recorded'}</p></div><span className="badge">{receipt.copy?'Audited copy':'Saved receipt'}</span></header>
   <dl className="receipt-meta"><div><dt>Order reference</dt><dd>{receipt.orderId}</dd></div><div><dt>Issued</dt><dd><time dateTime={receipt.issuedAt}>{new Date(receipt.issuedAt).toLocaleString()}</time></dd></div><div><dt>Payment method</dt><dd>{receipt.paymentMethod.startsWith('MOCK_')?`TEST ${receipt.paymentMethod.slice(5)} · No funds collected`:receipt.paymentMethod}</dd></div></dl>
   <div className="receipt-table-scroll" role="region" aria-label="Online receipt item table" tabIndex={0}><table className="receipt-table"><caption>Saved purchase amounts</caption><thead><tr><th scope="col">Item</th><th scope="col">Qty</th><th scope="col">Unit price</th><th scope="col">Discount</th><th scope="col">Tax</th><th scope="col">Total</th></tr></thead><tbody>{receipt.items.map(item=><tr key={item.productId}><th scope="row">{item.name}</th><td>{item.quantity}</td><td>{money(item.unitPrice)}</td><td>{money(item.discount)}</td><td>{money(item.tax)} <small>({item.taxRate}%)</small></td><td>{money(item.total)}</td></tr>)}</tbody></table></div>
   <dl className="receipt-tender"><div className="receipt-total"><dt>Order total</dt><dd>{money(receipt.total)}</dd></div></dl>
   {receipt.invoices.length?<section className="receipt-invoices" aria-label="Online tax invoice"><h3>Linked tax invoice</h3>{receipt.invoices.map(invoice=><p key={invoice.number}>{invoice.number} · {invoice.seller} · {invoice.outlet} · {money(invoice.total)}</p>)}</section>:<p className="receipt-note">No tax invoice was issued for this order.</p>}
   <p className="receipt-note">These are saved transaction facts. Printing creates an audited copy of this receipt and its linked invoice details. You can save the print output as PDF. A copy does not create a payment or a new tax invoice.</p>
   <div className="receipt-actions"><button disabled={busy} onClick={()=>void print()}>{busy?'Preparing copy…':'Print / save receipt copy'}</button></div>
  </>}
 </section>;
}
