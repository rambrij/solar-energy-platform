import {useEffect,useRef,useState} from 'react';
import {api} from './api';
type Filters={from:string;to:string;reference:string};
type Shift={id:string;registerId:number;openingFloat:number;expectedCash:number|null;countedCash:number|null;variance:number|null;closedAt:string};
type History={branchId:number;from:string;to:string;reference:string;page:number;size:number;totalElements:number;totalPages:number;items:Shift[]};
const initial:Filters={from:'',to:'',reference:''};
const money=(value:number|null)=>value===null?'Not recorded':Number(value).toFixed(2);
export default function ShiftHistory({disabled=false,revision=''}:{disabled?:boolean;revision?:string}){
 const [filters,setFilters]=useState(initial),[applied,setApplied]=useState(initial),[history,setHistory]=useState<History>(),[busy,setBusy]=useState(false),[error,setError]=useState('');const generation=useRef(0);
 useEffect(()=>{generation.current++;setHistory(undefined);setError('');setBusy(false);return()=>{generation.current++;};},[disabled,revision]);
 function edit(key:keyof Filters,value:string){generation.current++;setFilters(previous=>({...previous,[key]:value}));setHistory(undefined);setError('');}
 async function load(index:number,query:Filters){
  if(busy||disabled)return;if(query.from&&query.to&&query.from>query.to){setError('From date must not follow to date.');return;}
  const current=++generation.current;setBusy(true);setHistory(undefined);setError('');const params=new URLSearchParams({reference:query.reference.trim(),page:String(index),size:'12'});if(query.from)params.set('from',query.from);if(query.to)params.set('to',query.to);
  try{const value:History=await api(`/sessions/history?${params}`);if(current===generation.current){setHistory(value);setApplied({...query,from:value.from,to:value.to});}}
  catch{if(current===generation.current)setError('Closed shifts unavailable. Check your login, POS_VIEW permission and a UTC range of at most 366 days.');}
  finally{if(current===generation.current)setBusy(false);}
 }
 return <section id="shift-history" className="shift-history" aria-label="My closed shifts" aria-busy={busy}><div className="section-heading"><div><span className="eyebrow">Drawer records</span><h2>My closed shifts</h2></div><span className="badge">Your assigned branch</span></div><p>Your completed drawer counts only. Recorded closing values are snapshots, not current drawer balances or a sales report. Loading history does not resume a shift or reconcile a saved command.</p>
 <form className="history-filters" onSubmit={e=>{e.preventDefault();void load(0,filters);}}>
 <label>Closed shift reference<input maxLength={36} value={filters.reference} disabled={busy||disabled} onChange={e=>edit('reference',e.target.value)}/></label>
 <label>Closed from date (UTC)<input type="date" value={filters.from} disabled={busy||disabled} onChange={e=>edit('from',e.target.value)}/></label>
 <label>Closed to date (UTC)<input type="date" value={filters.to} disabled={busy||disabled} onChange={e=>edit('to',e.target.value)}/></label>
 <button disabled={busy||disabled}>{busy?'Loading closed shifts...':'Load my closed shifts'}</button></form><p className="bill-hint">Default: last 30 UTC days by close time. Choose earlier dates for older shifts. Open and awaiting-review shifts are excluded.</p>{error&&<p role="alert">{error}</p>}
 {history&&<><p role="status">{history.totalElements} closed shifts · Branch {history.branchId} · {history.from} through {history.to} UTC</p>{!history.items.length&&<p>No closed shifts on this page. Try another date range or reload page one.</p>}<ul className="closed-shift-list">{history.items.map(shift=><li key={shift.id}><div className="section-heading"><h3>Register {shift.registerId}</h3><span className="badge">CLOSED</span></div><p className="order-reference">{shift.id}</p><time dateTime={shift.closedAt}>{new Date(shift.closedAt).toLocaleString()} local close time</time><dl><dt>Opening float</dt><dd>{money(shift.openingFloat)}</dd><dt>Recorded expected cash</dt><dd>{money(shift.expectedCash)}</dd><dt>Counted closing cash</dt><dd>{money(shift.countedCash)}</dd><dt>Closing variance</dt><dd className={shift.variance!==null&&Number(shift.variance)!==0?'shift-variance':''}>{money(shift.variance)}</dd></dl><p>{shift.variance===null?'Historical variance was not recorded.':Number(shift.variance)===0?'Count matched the recorded expectation.':'Closed with a recorded difference. This is not an instruction to adjust cash.'}</p></li>)}</ul><nav className="catalogue-pagination" aria-label="Closed shift pages"><button className="secondary" disabled={busy||disabled||history.page===0} onClick={()=>void load(history.page-1,applied)}>Previous closed shifts</button><span>Page {history.page+1} of {Math.max(1,history.totalPages)}</span><button disabled={busy||disabled||history.page>=history.totalPages-1||history.page>=10000} onClick={()=>void load(history.page+1,applied)}>Next closed shifts</button></nav><p>Newest close time first, with session ID breaking ties. Reload after closing another shift; pages can shift as new closures are recorded.</p></>}
 </section>;
}
