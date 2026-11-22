import {useEffect,useRef,useState} from 'react';
import {api,ApiError} from './api';
import {useSession} from './session';
type Tender={attemptId:string;method:string;state:string;amount:number;reference?:string};
type Balance={orderId:string;orderStatus:string;total:number;confirmed:number;remaining:number;splitEnabled:boolean;tenders:Tender[]};
type Command={key:string;body:{method:string;appliedAmount:string;cashReceived?:string}};
type Props={orderId:string;disabled?:boolean;reservationExpired?:boolean;onComplete:()=>void;onBusyChange:(value:boolean)=>void};
export default function SplitTenderCheckout({orderId,disabled=false,reservationExpired=false,onComplete,onBusyChange}:Props){
 const {account}=useSession();
 const permissions=account?.permissions??[];
 const canRecordCash=permissions.includes('POS_TENDER_CASH_RECORD');
 const canRecordExternal=permissions.includes('POS_TENDER_EXTERNAL_RECORD');
 const canCreateSale=permissions.includes('POS_SALE_CREATE');
 const canRecordMethod=(value:string)=>value==='CASH'?canRecordCash:canRecordExternal;
 const storageKey=`pos-tender-command-v1:${orderId}`;
 const [balance,setBalance]=useState<Balance>(),[method,setMethod]=useState(()=>canRecordCash?'CASH':'UPI'),[amount,setAmount]=useState(''),[cash,setCash]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const [pending,setPending]=useState<Command|undefined>(()=>{try{return JSON.parse(sessionStorage.getItem(storageKey)??'null')??undefined;}catch{return undefined;}});
 const alive=useRef(true);useEffect(()=>{alive.current=true;return()=>{alive.current=false;};},[]);
  function accept(value:Balance){if(value.orderId!==orderId||!Array.isArray(value.tenders))throw Error('Tender response mismatch');setBalance(value);if(value.orderStatus==='PAID')onComplete();}
 async function load(){setBusy(true);onBusyChange(true);setError('');try{const value=await api(`/sales/${encodeURIComponent(orderId)}/tenders`);if(alive.current)accept(value);}catch{if(alive.current)setError('Tender status unavailable. Check connection and permission before taking payment.');}finally{if(alive.current){setBusy(false);onBusyChange(false);}}}
 async function post(command:Command){
  if(!canRecordMethod(command.body.method)){setError(command.body.method==='CASH'?'POS_TENDER_CASH_RECORD permission is required.':'POS_TENDER_EXTERNAL_RECORD permission is required.');return;}
  try{sessionStorage.setItem(storageKey,JSON.stringify(command));}catch{setError('Cannot save retry details. No payment request was submitted.');return;}
  const retry=!!pending;setPending(command);setBusy(true);onBusyChange(true);setError('');
  try{const value=await api(`/sales/${encodeURIComponent(orderId)}/tenders`,command.body,command.key);if(!alive.current)return;accept(value);sessionStorage.removeItem(storageKey);setPending(undefined);setAmount('');setCash('');}
  catch(e){if(alive.current){if(!retry&&e instanceof ApiError&&[400,403,404,409].includes(e.status)){sessionStorage.removeItem(storageKey);setPending(undefined);setError(e.message+' Tender rejected; check the saved balance before taking payment.');}else setError('Payment outcome is uncertain. Retry the same saved tender; do not collect or charge again.');}}
  finally{if(alive.current){setBusy(false);onBusyChange(false);}}
 }
 async function resolve(path:string){
  if(path.endsWith('/unknown')&&!canRecordExternal){setError('POS_TENDER_EXTERNAL_RECORD permission is required to mark an external outcome unknown.');return;}
  if(path==='/finalize'&&!canCreateSale){setError('POS_SALE_CREATE permission is required to finalize the reserved sale.');return;}
  setBusy(true);onBusyChange(true);setError('');try{if(path.endsWith('/unknown')){const attemptId=path.split('/')[2];await api(`/sales/${encodeURIComponent(orderId)}/tenders/${encodeURIComponent(attemptId)}/unknown`,{},`finalize-${orderId}`);}else{await api(`/sales/${encodeURIComponent(orderId)}/finalize`,{},`finalize-${orderId}`);}const value=await api(`/sales/${encodeURIComponent(orderId)}/tenders`);if(alive.current)accept(value);}catch{if(alive.current)setError('Outcome not confirmed. Reload the original tender balance before another action. Do not collect payment again.');}finally{if(alive.current){setBusy(false);onBusyChange(false);}}}
 const unresolved=balance?.tenders.some(t=>['PENDING_VERIFICATION','UNKNOWN_RECONCILIATION_REQUIRED'].includes(t.state));
 const validAmount=/^\d+(\.\d{1,2})?$/.test(amount)&&Number(amount)>=0&&Number(amount)<=Number(balance?.remaining??-1)&&(Number(amount)>0||Number(balance?.total)===0);
 const validCash=method!=='CASH'||(/^\d+(\.\d{1,2})?$/.test(cash)&&Number(cash)>=Number(amount));
 return <section className="split-tender-checkout" aria-label={`Tenders for ${orderId}`} aria-busy={busy}>
  <h3>Payments and split tender</h3><p>Cash and terminal payments belong directly to this vendor. Only confirmed applied amounts count toward the bill.</p>
  {error&&<p role="alert">{error}</p>}<button className="secondary" disabled={busy} onClick={()=>void load()}>Load tender balance</button>
  {pending&&<div><p>A saved tender is awaiting acknowledgement. Do not take funds again.</p>{canRecordMethod(pending.body.method)&&<button disabled={busy} onClick={()=>void post(pending)}>Retry same tender</button>}</div>}
  {balance&&<><p role="status">{balance.orderStatus} · Paid {Number(balance.confirmed).toFixed(2)} · Remaining {Number(balance.remaining).toFixed(2)}</p>
   <ul>{balance.tenders.map(t=><li key={t.attemptId}>{t.method} · {t.state} · {Number(t.amount).toFixed(2)} · Reference {t.attemptId}</li>)}</ul>
   {unresolved&&<p role="alert">External payment is awaiting independent verification. Do not charge again or collect cash. Ask an authorized supervisor to reconcile this tender.</p>}
   {canRecordExternal&&balance.tenders.filter(t=>t.state==='PENDING_VERIFICATION').map(t=><button key={t.attemptId} disabled={busy||!!pending} onClick={()=>void resolve(`/tenders/${encodeURIComponent(t.attemptId)}/unknown`)}>Mark {t.method} outcome unknown</button>)}
   {canCreateSale&&balance.orderStatus==='DRAFT'&&Number(balance.remaining)===0&&!unresolved&&<button disabled={busy||!!pending} onClick={()=>void resolve('/finalize')}>Retry saved sale finalization</button>}
   {reservationExpired&&Number(balance.confirmed)>0&&<p role="status">The unpaid hold time elapsed, but confirmed funds retain this bill and stock. Collect only the saved remaining balance after checking the tender status.</p>}
   {(canRecordCash||canRecordExternal)&&<fieldset disabled={busy||disabled||(reservationExpired&&Number(balance.confirmed)<=0)||!!pending||unresolved||balance.orderStatus!=='DRAFT'}>
    <label>Tender method<select value={method} onChange={e=>setMethod(e.target.value)}>{canRecordCash&&<option value="CASH">Cash</option>}{canRecordExternal&&<><option value="UPI">Vendor UPI</option><option value="CARD">Vendor card terminal</option></>}</select></label>
    <label>Amount applied<input inputMode="decimal" value={amount} onChange={e=>setAmount(e.target.value)}/></label>
    {method==='CASH'&&canRecordCash&&<><label>Tender cash received<input inputMode="decimal" value={cash} onChange={e=>setCash(e.target.value)}/></label><p>Change {validAmount&&validCash?(Number(cash)-Number(amount)).toFixed(2):'—'}</p></>}
    {!balance.splitEnabled&&<p>This outlet requires one tender for the full remaining amount.</p>}
    <button disabled={!canRecordMethod(method)||!validAmount||!validCash||(!balance.splitEnabled&&Number(amount)!==Number(balance.remaining))} onClick={()=>void post({key:crypto.randomUUID(),body:{method,appliedAmount:amount,...(method==='CASH'?{cashReceived:cash}:{})}})}>{method==='CASH'?'Record received cash':'Prepare vendor terminal tender'}</button>
   </fieldset>}
  </>}
 </section>;
}
