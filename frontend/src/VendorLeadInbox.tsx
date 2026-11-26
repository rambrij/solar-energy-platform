import {useEffect,useState} from 'react';
import {vendorApi} from './api';
type Lead={id:number;productId:number|null;productName:string|null;contactName:string;contactEmail:string;contactPhone:string|null;message:string;createdAt:string;status:string};
export default function VendorLeadInbox(){
 const [leads,setLeads]=useState<Lead[]>([]),[busy,setBusy]=useState(true),[error,setError]=useState('');
 useEffect(()=>{let active=true;setBusy(true);setError('');
  vendorApi('/leads').then((items:Lead[])=>{if(active)setLeads(items);}).catch(e=>{if(active)setError((e as Error).message||'Enquiries could not be loaded.');}).finally(()=>{if(active)setBusy(false);});
  return()=>{active=false;};
 },[]);
 return <section aria-label="Vendor enquiry inbox"><h2>Marketplace enquiries</h2><p>Only enquiries submitted to this vendor are shown here.</p>
  {error&&<p role="alert">{error}</p>}{busy&&!leads.length&&<p role="status">Loading enquiries…</p>}
  {!busy&&!leads.length&&!error&&<p className="empty-state">No marketplace enquiries have been received.</p>}
  <ul>{leads.map(lead=><li key={lead.id}><h3>{lead.contactName}{lead.productName?' · '+lead.productName:''}</h3>
   <p><a href={'mailto:'+encodeURIComponent(lead.contactEmail)}>{lead.contactEmail}</a>{lead.contactPhone?' · '+lead.contactPhone:''}</p>
   <p>{lead.message}</p><small>{new Date(lead.createdAt).toLocaleString()} · {lead.status}</small></li>)}</ul>
 </section>;
}
