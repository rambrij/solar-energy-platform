import {useEffect,useState,type FormEvent} from 'react';
import {marketplaceApi} from './marketplaceApi';
type Order={orderId:string;sellerName:string;deliveredAt:string|null;returnWindowDays:number;eligibleUntil:string|null;eligible:boolean;message:string;items:{productId:number;name:string;purchasedQuantity:number;requestedQuantity:number;availableQuantity:number}[]};
type Return={id:string;orderId:string;status:string;reason:string;customerMessage:string|null;createdAt:string;items:{productId:number;name:string;quantity:number}[];events:{id:number;eventType:string;message:string;occurredAt:string}[]};
type Workspace={eligibleOrders:Order[];returns:Return[]};
type Attempt={key:string;body:{orderId:string;items:{productId:number;quantity:number}[];reason:string}};
const keyName='customer-online-return-attempt-v1';
export default function CustomerOnlineReturns(){
 const [data,setData]=useState<Workspace>({eligibleOrders:[],returns:[]}),[order,setOrder]=useState('');
 const [qty,setQty]=useState<Record<number,number>>({}),[reason,setReason]=useState('');
 const [attempt,setAttempt]=useState<Attempt|undefined>(()=>{try{return JSON.parse(sessionStorage.getItem(keyName)??'null')??undefined;}catch{return undefined;}});
 const [busy,setBusy]=useState(false),[loading,setLoading]=useState(true),[error,setError]=useState(''),[notice,setNotice]=useState('');
 async function load(){setLoading(true);try{const v=await marketplaceApi('/api/v1/customer/online-returns') as Workspace;setData(v);if(!order&&v.eligibleOrders.length)setOrder(v.eligibleOrders[0].orderId);}catch(e){setError((e as Error).message);}finally{setLoading(false);}}
 useEffect(()=>{void load();},[]);
 const selected=data.eligibleOrders.find(o=>o.orderId===order);
 async function send(a:Attempt){setBusy(true);setError('');try{await marketplaceApi('/api/v1/customer/online-returns','POST',a.body,a.key);sessionStorage.removeItem(keyName);setAttempt(undefined);setReason('');setQty({});setNotice('Return request submitted for vendor review. No refund or return shipment has been initiated.');await load();}catch(e){setError('Request not confirmed. Retry the same request safely. '+(e as Error).message);}finally{setBusy(false);}}
 async function submit(e:FormEvent){e.preventDefault();if(!selected?.eligible||attempt||busy)return;const items=selected.items.filter(i=>(qty[i.productId]??0)>0).map(i=>({productId:i.productId,quantity:qty[i.productId]}));if(!items.length||!reason.trim()){setError('Choose at least one item and enter a return reason.');return;}const a={key:crypto.randomUUID(),body:{orderId:order,items,reason:reason.trim()}};setAttempt(a);sessionStorage.setItem(keyName,JSON.stringify(a));await send(a);}
 return <section className="workspace-panel"><div className="section-heading"><div><h2>Online order returns</h2><p>Request a return for an eligible delivered marketplace order and follow the vendor decision.</p></div><button className="secondary" disabled={busy||loading} onClick={()=>void load()}>Refresh</button></div>
 {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
 {attempt&&<p role="status">A previous submission is unconfirmed. <button disabled={busy} onClick={()=>void send(attempt)}>Retry same request</button></p>}
 {loading?<p role="status">Checking delivered orders…</p>:<section><h3>Request a return</h3>{!data.eligibleOrders.length?<p>No delivered paid online orders are available.</p>:<>
 <label>Delivered order<select value={order} disabled={busy||!!attempt} onChange={e=>{setOrder(e.target.value);setQty({});}}>{data.eligibleOrders.map(o=><option key={o.orderId} value={o.orderId}>{o.orderId} · {o.sellerName}</option>)}</select></label>
 {selected&&<><p>{selected.message}{selected.eligibleUntil?' Deadline: '+new Date(selected.eligibleUntil).toLocaleString():''}</p><form onSubmit={submit}><fieldset disabled={busy||!!attempt||!selected.eligible}><legend>Items to return</legend>
 {selected.items.map(i=><label key={i.productId}>{i.name} · purchased {i.purchasedQuantity} · requested {i.requestedQuantity} · available {i.availableQuantity}<input type="number" min="0" max={i.availableQuantity} value={qty[i.productId]??0} onChange={e=>setQty(v=>({...v,[i.productId]:Math.max(0,Math.min(i.availableQuantity,Number(e.target.value)||0))}))}/></label>)}
 <label>Reason for return<textarea maxLength={250} value={reason} onChange={e=>setReason(e.target.value)}/></label></fieldset><button disabled={busy||!!attempt||!selected.eligible}>Submit online return request</button></form></>}
 </>}</section>}
 <section><h3>Return request history</h3>{!data.returns.length?<p>No online return requests yet.</p>:<ul>{data.returns.map(r=><li key={r.id}><h4>{r.id} · Order {r.orderId}</h4><p>Status: <strong>{r.status}</strong> · {r.reason}</p><ul>{r.items.map(i=><li key={i.productId}>{i.name} × {i.quantity}</li>)}</ul>{r.customerMessage&&<p>{r.customerMessage}</p>}<ol>{r.events.map(ev=><li key={ev.id}>{ev.eventType}: {ev.message}</li>)}</ol></li>)}</ul>}</section>
 <p>Vendor approval records a decision only. Refund processing, carrier collection, reverse logistics and stock receipt are not initiated in this increment.</p></section>;
}
