import {useEffect,useRef,useState} from 'react';
import {api} from './api';
import {marketplaceApi} from './marketplaceApi';
type Payment={orderId:string;attemptId:string|null;method:string|null;state:string;amount:number;merchantAccount:string|null;mockEnabled:boolean;message:string;terminalEnabled?:boolean;orderStatus?:string;cashier?:string;reference?:string};
type Props={mode:'online'|'pos';orderId:string;disabled?:boolean;onComplete:()=>void;onBusyChange:(busy:boolean)=>void};
export default function PaymentCheckout({mode,orderId,disabled=false,onComplete,onBusyChange}:Props){
 const [payment,setPayment]=useState<Payment>(),[busy,setBusy]=useState(false),[error,setError]=useState(''),[uncertain,setUncertain]=useState(false);
 const alive=useRef(true);
 useEffect(()=>{alive.current=true;return()=>{alive.current=false;};},[]);
 const base=`/drafts/${encodeURIComponent(orderId)}/payment`;
 async function request(action:'status'|'UPI'|'CARD'|'confirm'|'unknown'){
  setBusy(true);onBusyChange(true);setError('');
  try{
   const suffix=action==='confirm'?'/mock-confirm':action==='unknown'?'/unknown':'';
   const body=action==='status'?undefined:action==='confirm'||action==='unknown'?{attemptId:payment?.attemptId}:{method:action};
   const value:Payment=mode==='pos'?await api(base+suffix,body):await marketplaceApi('/api/customer/checkout'+base+suffix,body===undefined?'GET':'POST',body);
   if(!alive.current)return;
   if(value.orderId!==orderId)throw Error('Payment order mismatch');
   setPayment(value);setUncertain(false);
   if(value.state==='CONFIRMED')onComplete();
  }catch{if(alive.current){setUncertain(true);setError('Payment outcome was not confirmed. Check payment status before another action. Do not collect payment again.');}}
  finally{if(alive.current){setBusy(false);onBusyChange(false);}}
 }
 return <section className="payment-checkout" aria-label={`Payment for ${orderId}`} aria-busy={busy}>
  <h3>{mode==='online'?'Pay your vendor':'UPI / card checkout'}</h3><p>Payment belongs to this reserved order and vendor. A reservation alone is unpaid.</p>
  {error&&<p role="alert">{error}</p>}
  <button className="secondary" disabled={busy} onClick={()=>void request('status')}>Check payment options / status</button>
  {payment&&<><p role="status">{payment.state} · {Number(payment.amount).toFixed(2)}</p><p>{payment.message}</p>
   {mode==='online'&&payment.mockEnabled&&<p className="simulation-notice"><strong>TEST PAYMENT — no real money moves.</strong> These controls simulate an online vendor-direct provider in pre-production.</p>}
   {payment.merchantAccount&&<p>Vendor account: {payment.merchantAccount} · Payment reference: {payment.attemptId}</p>}
   {mode==='online'&&payment.mockEnabled&&payment.state==='NOT_STARTED'&&!uncertain&&<div className="payment-actions"><button disabled={busy||disabled} onClick={()=>void request('UPI')}>Start test UPI payment</button><button disabled={busy||disabled} onClick={()=>void request('CARD')}>Start test card payment</button></div>}
   {mode==='online'&&payment.mockEnabled&&payment.state==='PENDING'&&!uncertain&&<button disabled={busy||disabled} onClick={()=>void request('confirm')}>Simulate successful {payment.method} payment</button>}
   {mode==='pos'&&payment.terminalEnabled&&payment.state==='NOT_STARTED'&&!uncertain&&<div className="payment-actions"><button disabled={busy||disabled} onClick={()=>void request('UPI')}>Prepare vendor UPI tender</button><button disabled={busy||disabled} onClick={()=>void request('CARD')}>Prepare vendor card tender</button></div>}
   {mode==='pos'&&payment.state==='PENDING_VERIFICATION'&&<><p>Tender saved. Collect payment only through the vendor's separate terminal/account. Ask a different authorized supervisor to verify the external result. A typed reference alone cannot complete the sale.</p><button disabled={busy||uncertain} onClick={()=>void request('unknown')}>Mark outcome unknown — reconcile before retrying</button></>}
   {mode==='pos'&&payment.state==='UNKNOWN_RECONCILIATION_REQUIRED'&&<p role="alert">Do not charge again or collect cash. Stock remains held until independent reconciliation confirms success or failure.</p>}
   {payment.state==='CONFIRMED'&&<p>{mode==='online'?'Test order completed. No funds were collected.':'Terminal tender independently confirmed.'} View the saved order or receipt.</p>}
  </>}
 </section>;
}
