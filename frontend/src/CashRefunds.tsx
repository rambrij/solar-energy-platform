import { useEffect, useState } from 'react';
import { api, ApiError } from './api';
export const refundStorageKey = 'pos-cash-refund-attempt-v1';
type Line = {productId:number;name:string;quantity:number;net:number;tax:number;amount:number};
type Quote = {returnId:string;orderId:string;originalReceipt:string;sessionId:string;amount:number;drawerCash:number;enoughCash:boolean;quoteHash:string;items:Line[];message:string};
export type CashRefundView = {id:string;returnId:string;orderId:string;originalReceipt:string;branchId:string;cashier:string;sessionId:string;amount:number;reason:string;paidAt:string;method:string;status:string;copy:boolean;items:Line[]};
type Attempt = {key:string;body:{returnId:string;sessionId:string;reason:string;quoteHash:string;confirmation:string};safeToDiscard:boolean};
type Props = {session:string;sessionState:string;disabled:boolean;onPendingChange:(value:boolean)=>void;onBusyChange:(value:boolean)=>void;onRecorded:(value:CashRefundView)=>void};
const money=(value:number)=>Number(value).toFixed(2);
export default function CashRefunds({session,sessionState,disabled,onPendingChange,onBusyChange,onRecorded}:Props){
 const [saved]=useState(()=>{try{const raw=sessionStorage.getItem(refundStorageKey);if(!raw)return {attempt:undefined,corrupt:false};const value=JSON.parse(raw) as Attempt;if(!value?.key||!value.body?.returnId||!value.body.sessionId||!value.body.reason||!value.body.quoteHash||value.body.confirmation!=='CASH_HANDED_TO_BUYER')return {attempt:undefined,corrupt:true};return {attempt:value,corrupt:false};}catch{return {attempt:undefined,corrupt:true};}});
 const [pending,setPending]=useState<Attempt|undefined>(saved.attempt),[corrupt,setCorrupt]=useState(saved.corrupt);
 const [returned,setReturned]=useState(pending?.body.returnId??''),[reason,setReason]=useState(''),[confirmed,setConfirmed]=useState(false),[noCash,setNoCash]=useState(false);
 const [journal,setJournal]=useState<CashRefundView[]>();
 const [quote,setQuote]=useState<Quote>(),[recovered,setRecovered]=useState<CashRefundView>(),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 useEffect(()=>{onPendingChange(!!pending||corrupt);},[pending,corrupt,onPendingChange]);
 const locked=disabled||busy||!!pending||corrupt;
 function save(attempt:Attempt){sessionStorage.setItem(refundStorageKey,JSON.stringify(attempt));setPending(attempt);onPendingChange(true);}
 function clear(){sessionStorage.removeItem(refundStorageKey);setPending(undefined);setCorrupt(false);onPendingChange(false);setQuote(undefined);setConfirmed(false);setNoCash(false);}
 async function load(recover:boolean){
  setBusy(true);onBusyChange(true);setError('');setQuote(undefined);setRecovered(undefined);setConfirmed(false);
  try{if(recover)setRecovered(await api(`/refunds/return/${encodeURIComponent(pending?.body.returnId??returned.trim())}`));else setQuote(await api(`/refunds/quote/${encodeURIComponent(returned.trim())}?sessionId=${encodeURIComponent(session)}`));}
  catch(e){setError((e as Error).message+' Check permissions, inspected return, branch policy and paying session. No cash was recorded by this lookup.');}
  finally{setBusy(false);onBusyChange(false);}
 }
 async function pay(){
  if(busy||(!pending&&(!quote||quote.sessionId!==session||sessionState!=='OPEN')))return;
  const attempt:Attempt=pending??{key:crypto.randomUUID(),body:{returnId:quote!.returnId,sessionId:quote!.sessionId,reason:reason.trim(),quoteHash:quote!.quoteHash,confirmation:'CASH_HANDED_TO_BUYER'},safeToDiscard:false};
  const wasSafe=!pending||pending.safeToDiscard;
  try{save({...attempt,safeToDiscard:false});}catch{setError('Cannot save retry details. No refund was submitted.');return;}
  setBusy(true);onBusyChange(true);setError('');setRecovered(undefined);
  try{const result:CashRefundView=await api('/refunds',attempt.body,attempt.key);clear();onRecorded(result);setReason('');}
  catch(e){const definite=e instanceof ApiError&&[400,403,404,409].includes(e.status);if(definite&&wasSafe){try{save({...attempt,safeToDiscard:true});}catch{/* Keep the persisted uncertain attempt. */}}
   setError((e as Error).message+' Reconcile the cash handover. Retry uses the exact saved command; do not hand cash over again.');}
  finally{setBusy(false);onBusyChange(false);}
 }
 return <section id="cash-refunds" aria-label="Cash refunds" className="cash-refunds"><div className="section-heading"><h2>Cash refunds</h2><span className="badge">Independent supervisor</span></div>
  <p>For inspected POS returns only. Branch policy must enable cash refunds. Tax-invoice refunds require credit notes and are blocked. Use your own funded open register.</p>
  {error&&<p role="alert">{error}</p>}{corrupt&&<p role="alert">Saved cash handover details cannot be read. Other cash operations stay locked. Enter the original return reference, recover its paid refund and reconcile cash before acknowledging. If no paid record exists, ask the supervisor to reconcile the journal before clearing browser storage.</p>}
  <label>Inspected return ID<input maxLength={36} value={returned} disabled={disabled||busy||!!pending} onChange={e=>{setReturned(e.target.value);setQuote(undefined);setRecovered(undefined);setConfirmed(false);}}/></label>
  <div className="refund-actions"><button disabled={locked||!returned.trim()||!session||sessionState!=='OPEN'} onClick={()=>void load(false)}>Calculate cash refund</button><button className="secondary" disabled={busy||(!pending&&disabled)||(!pending&&!returned.trim())} onClick={()=>void load(true)}>Recover paid refund</button></div>
  {quote&&!pending&&quote.sessionId===session&&sessionState==='OPEN'&&<div className="refund-quote"><h3>Review original receipt {quote.originalReceipt}</h3><ul>{quote.items.map(line=><li key={line.productId}>{line.name} × {line.quantity} · Net {money(line.net)} · Tax {money(line.tax)} · Refund {money(line.amount)}</li>)}</ul><p className="refund-total">Refund {money(quote.amount)} · Drawer {money(quote.drawerCash)}</p><p>{quote.message}</p>{!quote.enoughCash&&<p role="alert">Insufficient paying drawer cash.</p>}<label>Cash refund reason<input maxLength={250} value={reason} disabled={locked} onChange={e=>setReason(e.target.value)}/></label><label className="checkbox-label"><input type="checkbox" checked={confirmed} disabled={locked} onChange={e=>setConfirmed(e.target.checked)}/>I independently approve this amount and confirm cash was handed to the buyer once.</label><button disabled={locked||!quote.enoughCash||!reason.trim()||!confirmed} onClick={()=>void pay()}>Approve & record cash handover</button></div>}
  {pending&&<div className="refund-recovery"><h3>Resolve saved cash handover</h3><p>Return {pending.body.returnId} · Paying session {pending.body.sessionId}. Other cash operations stay locked. Do not hand cash over twice.</p><button disabled={busy} onClick={()=>void pay()}>Retry same cash refund</button>{pending.safeToDiscard&&<><label className="checkbox-label"><input type="checkbox" checked={noCash} disabled={busy} onChange={e=>setNoCash(e.target.checked)}/>I reconciled the rejection and no cash was handed over.</label><button className="secondary" disabled={busy||!noCash} onClick={()=>{clear();setError('');}}>Discard rejected refund attempt</button></>}</div>}
  <button className="secondary" disabled={busy||disabled} onClick={async()=>{setBusy(true);onBusyChange(true);setError('');try{setJournal(await api('/refunds'));}catch(e){setError((e as Error).message);}finally{setBusy(false);onBusyChange(false);}}}>Load recent cash refund journal</button>
  {journal&&<ul aria-label="Recent cash refund journal">{journal.map(row=><li key={row.id}>{row.id} · Return {row.returnId} · Cash {money(row.amount)} · {row.cashier} · {new Date(row.paidAt).toLocaleString()} <button className="secondary" disabled={busy||disabled||!!pending&&pending.body.returnId!==row.returnId} onClick={()=>{setRecovered(row);setQuote(undefined);if(!pending)setReturned(row.returnId);}}>View refund {row.id}</button></li>)}</ul>}
  {recovered&&<div className="refund-recovery"><p>Recorded PAID refund {recovered.id}: {money(recovered.amount)}. Reconcile this record with the physical cash handover; do not pay again.</p><button disabled={busy||(!pending&&disabled)} onClick={()=>{if(pending&&pending.body.returnId!==recovered.returnId)return;clear();onRecorded(recovered);setRecovered(undefined);}}>Acknowledge recorded refund & cash reconciliation</button></div>}
 </section>;
}
