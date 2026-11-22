import {useEffect,useRef,useState} from 'react';
import {api} from './api';
type Filters={state:string;reference:string;from:string;to:string};
type Row={id:string;orderId:string;state:string;createdAt:string};
type Page={state:string;from:string;to:string;reference:string;page:number;size:number;totalElements:number;totalPages:number;items:Row[]};
const initial:Filters={state:'REQUESTED',reference:'',from:'',to:''};
export default function ReturnQueue({disabled,revision,onSelect}:{disabled:boolean;revision:string;onSelect:(id:string)=>void}){
 const [filters,setFilters]=useState(initial),[applied,setApplied]=useState(initial),[page,setPage]=useState<Page>(),[busy,setBusy]=useState(false),[error,setError]=useState('');const generation=useRef(0);
 useEffect(()=>{generation.current++;setPage(undefined);setError('');setBusy(false);return()=>{generation.current++;};},[disabled,revision]);
 function edit(key:keyof Filters,value:string){generation.current++;setFilters(previous=>({...previous,[key]:value}));setPage(undefined);setError('');setBusy(false);}
 async function load(index:number,query:Filters){
  if(disabled||busy)return;if(query.from&&query.to&&query.from>query.to){setError('From date must not follow to date.');return;}
  const current=++generation.current;setBusy(true);setPage(undefined);setError('');
  const params=new URLSearchParams({state:query.state,reference:query.reference.trim(),page:String(index),size:'12'});if(query.from)params.set('from',query.from);if(query.to)params.set('to',query.to);
  try{const value:Page=await api(`/returns/queue?${params}`);if(current===generation.current){setPage(value);setApplied({...query,from:value.from,to:value.to});}}
  catch{if(current===generation.current)setError('Return queue unavailable. Check your branch, POS_VIEW / POS_RETURN_VIEW permissions, and a UTC date range of at most 366 days.');}
  finally{if(current===generation.current)setBusy(false);}
 }
 return <section aria-label="Search return worklist" aria-busy={busy} className="return-worklist"><h3>2. Review queue</h3><p>Search return or original order reference prefixes. Oldest intake first; state changes do not change this order. Default date range is the last 30 UTC days. Adjust dates for older cases.</p>
  <form className="return-worklist-filters" onSubmit={e=>{e.preventDefault();void load(0,filters);}}>
   <label>Return queue state<select value={filters.state} disabled={disabled||busy} onChange={e=>edit('state',e.target.value)}>{['REQUESTED','APPROVED','REJECTED','INSPECTED','ALL'].map(state=><option key={state} value={state}>{state==='ALL'?'All return states':state}</option>)}</select></label>
   <label>Queue return or order reference<input maxLength={36} value={filters.reference} disabled={disabled||busy} onChange={e=>edit('reference',e.target.value)}/></label>
   <label>Queue from date (UTC)<input type="date" value={filters.from} disabled={disabled||busy} onChange={e=>edit('from',e.target.value)}/></label>
   <label>Queue to date (UTC)<input type="date" value={filters.to} disabled={disabled||busy} onChange={e=>edit('to',e.target.value)}/></label>
   <button disabled={disabled||busy}>{busy?'Loading return queue...':'Load return queue'}</button>
  </form>{error&&<p role="alert">{error}</p>}
  {page&&<><p role="status">{page.totalElements} matching returns · {page.from} through {page.to} UTC · Oldest first</p>{!page.items.length&&<p>No returns on this page. Change filters or reload the first page.</p>}<ul className="return-queue">{page.items.map(item=><li key={item.id}><span className="badge">{item.state}</span><p className="order-reference">{item.id}</p><small>Original order {item.orderId}</small><p><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString()} local time</time></p><button type="button" className="secondary" disabled={disabled||busy} onClick={()=>onSelect(item.id)}>Open return {item.id}</button></li>)}</ul><nav className="catalogue-pagination" aria-label="Return queue pages"><button type="button" className="secondary" disabled={disabled||busy||page.page===0} onClick={()=>void load(page.page-1,applied)}>Previous returns</button><span>Page {page.page+1} of {Math.max(1,page.totalPages)}</span><button type="button" disabled={disabled||busy||page.page>=page.totalPages-1||page.page>=10000} onClick={()=>void load(page.page+1,applied)}>Next returns</button></nav><p>Read-only summaries. Opening a case loads current details; it does not approve, inspect, refund or clear a saved command. Live queues can shift between pages.</p></>}
 </section>;
}
