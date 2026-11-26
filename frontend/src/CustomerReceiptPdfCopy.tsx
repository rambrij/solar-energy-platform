import {useEffect,useRef,useState} from 'react';
import {requestCustomerReceiptPdf} from './customerReceiptPdfApi';
import {downloadFile} from './fileDownload';

export default function CustomerReceiptPdfCopy({orderId,disabled}:{orderId:string;disabled:boolean}){
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const generation=useRef(0);
 useEffect(()=>{generation.current++;setBusy(false);setError('');setNotice('');return()=>{generation.current++;};},[orderId,disabled]);
 async function generate(){
  if(disabled||busy)return;
  const current=++generation.current;setBusy(true);setError('');setNotice('');
  try{
   const file=await requestCustomerReceiptPdf(orderId);
   if(current===generation.current){downloadFile(file);setNotice('PDF receipt copy generated. Reference '+file.reference+'. Browser saving is not verified.');}
  }catch{
   if(current===generation.current)setError('PDF copy was not confirmed. The saved receipt and browser print option remain available; retry when connected. An audit event may already have been recorded.');
  }finally{if(current===generation.current)setBusy(false);}
 }
 return <section className="customer-receipt-pdf-copy" aria-label="Download PDF receipt copy" aria-busy={busy}>
  <button disabled={disabled||busy} onClick={()=>void generate()}>{busy?'Generating PDF…':'Download PDF receipt copy'}</button>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
 </section>;
}
