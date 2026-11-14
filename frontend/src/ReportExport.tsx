import {downloadFile} from './fileDownload';
import {useEffect,useRef,useState} from 'react';
import {requestReportExport} from './reportExportApi';
export default function ReportExport({kind,from,to,channel,disabled}:{kind:'sales'|'cash-refunds';from:string;to:string;channel?:string;disabled:boolean}){
 const [reason,setReason]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[reference,setReference]=useState('');const generation=useRef(0);
 const label=kind==='sales'?'Sales':'Cash refund';
 const key=JSON.stringify([kind,from,to,channel]);
 useEffect(()=>{generation.current++;setBusy(false);setError('');setReference('');return()=>{generation.current++;};},[key,disabled]);
 async function exportCsv(event:React.FormEvent){event.preventDefault();if(disabled||busy||!reason.trim())return;const current=++generation.current;setBusy(true);setError('');setReference('');
  try{const value=await requestReportExport(kind,{from,to,reason:reason.trim(),...(kind==='sales'?{channel:channel??'ALL'}:{})});if(generation.current===current){downloadFile(value);setReference(value.reference);}}
  catch{if(generation.current===current)setError('CSV export unavailable. Check export/report permissions, dates and connectivity. No file was prepared.');}
  finally{if(generation.current===current)setBusy(false);}
 }
 return <form className="report-export" aria-label={`${label} CSV export`} onSubmit={exportCsv}><div><strong>Export this report period</strong><p>{from} to {to} UTC. The server regenerates current totals and audits the download request. TOTAL, DAY and breakdown sections are alternative views; do not sum them together.</p></div><label>{label} export reason<input maxLength={250} required disabled={disabled||busy} value={reason} placeholder="Business purpose; no buyer details" onChange={e=>setReason(e.target.value)}/></label><button disabled={disabled||busy||!reason.trim()}>{busy?'Preparing CSV...':`Download ${kind==='sales'?'sales':'cash refund'} CSV`}</button>{error&&<p role="alert">{error}</p>}{reference&&<p role="status">CSV prepared. Export reference {reference}</p>}</form>;
}
