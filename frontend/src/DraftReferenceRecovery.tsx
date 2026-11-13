import {useEffect,useRef,useState} from 'react';
import {api} from './api';
import {marketplaceApi} from './marketplaceApi';
import type {Draft} from './draftTypes';
export default function DraftReferenceRecovery({mode,requiredId,disabled,onRecovered}:{mode:'online'|'pos';requiredId?:string;disabled:boolean;onRecovered:(value:Draft)=>void}){
 const [reference,setReference]=useState(requiredId??''),[busy,setBusy]=useState(false),[error,setError]=useState('');const generation=useRef(0);
 useEffect(()=>{if(requiredId)setReference(requiredId);generation.current++;setError('');setBusy(false);return()=>{generation.current++;};},[mode,requiredId,disabled]);
 async function recover(event:React.FormEvent){event.preventDefault();if(disabled||busy)return;const id=(requiredId??reference).trim();if(!id||id.length>36){setError('Enter a valid reservation reference.');return;}const current=++generation.current;setBusy(true);setError('');
  try{const value:Draft=mode==='pos'?await api(`/drafts/${encodeURIComponent(id)}`):await marketplaceApi(`/api/customer/checkout/drafts/${encodeURIComponent(id)}`);if(current===generation.current)onRecovered(value);}
  catch{if(current===generation.current)setError('Reservation unavailable. Check the reference and your account, assigned branch or permissions. Saved retry details have been kept.');}
  finally{if(current===generation.current)setBusy(false);}
 }
 return <form className="draft-reference-recovery" aria-label="Recover reservation by reference" onSubmit={recover}><label>Reservation reference<input maxLength={36} value={requiredId??reference} readOnly={!!requiredId} disabled={disabled||busy} onChange={e=>{setReference(e.target.value);setError('');}}/></label><button disabled={disabled||busy||!(requiredId??reference).trim()}>{busy?'Checking reservation...':'Recover reservation by reference'}</button>{error&&<p role="alert">{error}</p>}<small>Lookup checks ownership and does not take payment or change stock. A saved uncertain command still requires its exact retry or explicit reconciliation.</small></form>;
}
