import {useEffect,useRef,useState} from 'react';
import {api} from './api';
import {marketplaceApi} from './marketplaceApi';
type Payment={orderId:string;attemptId:string|null;method:string|null;state:string;amount:number;merchantAccount:string|null;mockEnabled:boolean;message:string};
type Props={mode:'online'|'pos';orderId:string;disabled?:boolean;onComplete:()=>void;onBusyChange:(busy:boolean)=>void};
export default function PaymentCheckout({mode,orderId,disabled=false,onComplete,onBusyChange}:Props){
 const [payment,setPayment]=useState<Payment>(),[busy,setBusy]=useState(false),[error,setError]=useState(''),[uncertain,setUncertain]=useState(false);
 const alive=useRef(true);
 useEffect(()=>{alive.current=true;return()=>{alive.current=false;};},[]);
 const base=`/drafts/${encodeURIComponent(orderId)}/payment`;
 async function request(action:'status'|'UPI'|'CARD'|'confirm'){
  setBusy(true);onBusyChange(true);setError('');
  try{
   const suffix=action==='confirm'?'/mock-confirm':'';
   const body=action==='status'?undefined:action==='confirm'?{attemptId:payment?.attemptId}:{method:action};
   const value:Payment=mode==='pos'?await api(base+suffix,body):await marketplaceApi('/api/customer/checkout'+base+suffix,body===undefined?'GET':'POST',body);
   if(!alive.current)return;
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
   {payment.mockEnabled&&<p className="simulation-notice"><strong>TEST PAYMENT — no real money moves.</strong> These controls simulate a vendor-direct provider in pre-production.</p>}
   {payment.merchantAccount&&<p>Vendor account: {payment.merchantAccount} · Payment reference: {payment.attemptId}</p>}
   {payment.mockEnabled&&payment.state==='NOT_STARTED'&&!uncertain&&<div className="payment-actions"><button disabled={busy||disabled} onClick={()=>void request('UPI')}>Start test UPI payment</button><button disabled={busy||disabled} onClick={()=>void request('CARD')}>Start test card payment</button></div>}
   {payment.mockEnabled&&payment.state==='PENDING'&&!uncertain&&<button disabled={busy||disabled} onClick={()=>void request('confirm')}>Simulate successful {payment.method} payment</button>}
   {payment.state==='CONFIRMED'&&<p>Test order completed. No funds were collected. View the saved order or receipt.</p>}
  </>}
 </section>;
}
