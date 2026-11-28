import {useEffect,useState,type FormEvent} from 'react';
import {vendorApi} from './api';
import {usePermissions} from './usePermissions';
type Policy={returnWindowDays:number;version:number};
type Row={id:string;orderId:string;status:string;reason:string;reviewReason:string|null;customerMessage:string|null;createdAt:string;version:number;items:{productId:number;name:string;quantity:number}[];events:{id:number;eventType:string;message:string}[]};
type Draft={decision:'APPROVED'|'REJECTED';reason:string;customerMessage:string};
export default function VendorOnlineReturns(){
 const {hasPermission}=usePermissions(),canApprove=hasPermission('RETURN_APPROVE');
 const [policy,setPolicy]=useState<Policy>({returnWindowDays:0,version:0}),[days,setDays]=useState('0'),[policyReason,setPolicyReason]=useState('');
 const [rows,setRows]=useState<Row[]>([]),[drafts,setDrafts]=useState<Record<string,Draft>>({});
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 async function load(){setBusy(true);try{const [p,r]=await Promise.all([vendorApi('/online-returns/policy'),vendorApi('/online-returns')]) as [Policy,Row[]];setPolicy(p);setDays(String(p.returnWindowDays));setRows(r);}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 useEffect(()=>{void load();},[]);
 async function save(e:FormEvent){e.preventDefault();if(!policyReason.trim())return;setBusy(true);try{const p=await vendorApi('/online-returns/policy','PUT',{expectedVersion:policy.version,returnWindowDays:Number(days),reason:policyReason.trim()}) as Policy;setPolicy(p);setPolicyReason('');setNotice('Return window updated.');}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 function draft(id:string):Draft{return drafts[id]??{decision:'APPROVED',reason:'',customerMessage:''};}
 async function decide(row:Row){const d=draft(row.id);if(!d.reason.trim()||!d.customerMessage.trim())return;setBusy(true);try{await vendorApi('/online-returns/'+encodeURIComponent(row.id)+'/decision','POST',{expectedVersion:row.version,...d});setNotice('Decision recorded and customer message published.');await load();}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 return <section className="workspace-panel"><div className="section-heading"><div><h2>Online return management</h2><p>Review customer requests for delivered online orders in this tenant.</p></div><button className="secondary" disabled={busy} onClick={()=>void load()}>Refresh</button></div>
 {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
 <section><h3>Tenant return policy</h3><p>Current window: {policy.returnWindowDays===0?'Disabled':policy.returnWindowDays+' days'} · Version {policy.version}</p>
 {canApprove?<form onSubmit={save}><label>Return window days (0 disables)<input type="number" min="0" max="365" value={days} onChange={e=>setDays(e.target.value)}/></label><label>Reason for policy change<textarea maxLength={500} value={policyReason} onChange={e=>setPolicyReason(e.target.value)}/></label><button disabled={busy}>Save return policy</button></form>:<p>Read-only access: you cannot change the return policy.</p>}</section>
 <section><h3>Return requests</h3>{!rows.length?<p>No online return requests.</p>:<ul>{rows.map(row=><li key={row.id}><h4>{row.id} · Order {row.orderId}</h4><p>Status: <strong>{row.status}</strong> · {row.reason}</p><ul>{row.items.map(i=><li key={i.productId}>{i.name} × {i.quantity}</li>)}</ul>
 {row.status==='REQUESTED'&&canApprove&&<form onSubmit={e=>{e.preventDefault();void decide(row);}}><label>Decision<select value={draft(row.id).decision} onChange={e=>setDrafts(v=>({...v,[row.id]:{...draft(row.id),decision:e.target.value as Draft['decision']}}))}><option value="APPROVED">Approve</option><option value="REJECTED">Reject</option></select></label><label>Internal review reason<textarea value={draft(row.id).reason} onChange={e=>setDrafts(v=>({...v,[row.id]:{...draft(row.id),reason:e.target.value}}))}/></label><label>Customer-visible message<textarea value={draft(row.id).customerMessage} onChange={e=>setDrafts(v=>({...v,[row.id]:{...draft(row.id),customerMessage:e.target.value}}))}/></label><button disabled={busy}>Record decision</button></form>}
 {row.reviewReason&&<p>Internal review: {row.reviewReason}</p>}{row.customerMessage&&<p>Customer update: {row.customerMessage}</p>}<ol>{row.events.map(ev=><li key={ev.id}>{ev.eventType}: {ev.message}</li>)}</ol>
 </li>)}</ul>}</section><p>Approval records the decision only; refunds, reverse shipping and inventory receipts remain separate workflows.</p></section>;
}
