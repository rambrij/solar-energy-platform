import { useEffect, useState, type FormEvent } from 'react';
import { vendorApi } from './api';
import { useSession } from './session';
import VendorLeadInbox from './VendorLeadInbox';

type CustomerRelationship={relationshipId:string;customerId:number;displayName:string;consentAt:string;provenance:string};
type CrmNote={id:number;relationshipId:string;note:string;createdBy:string;updatedBy:string;createdAt:string;updatedAt:string;version:number};

export default function VendorCustomerCrm(){
 const {account}=useSession(); const permissions=account?.permissions??[];
 const canView=permissions.includes('CRM_NOTE_VIEW'),canCreate=permissions.includes('CRM_NOTE_CREATE'),canUpdate=permissions.includes('CRM_NOTE_UPDATE');
 const [customers,setCustomers]=useState<CustomerRelationship[]>([]),[selected,setSelected]=useState('');
 const [notes,setNotes]=useState<CrmNote[]>([]),[draft,setDraft]=useState(''),[reason,setReason]=useState('');
 const [editing,setEditing]=useState<CrmNote>(),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');

 useEffect(()=>{if(!permissions.includes('CUSTOMER_RELATION_VIEW')){setCustomers([]);return;}let active=true;setBusy(true);setError('');
  vendorApi('/customers').then((items:CustomerRelationship[])=>{if(active){setCustomers(items);setSelected(current=>current||(items.length?items[0].relationshipId:''));}})
   .catch(e=>{if(active)setError((e as Error).message||'Customer relationships could not be loaded.');})
   .finally(()=>{if(active)setBusy(false);});
  return()=>{active=false;};
 },[account?.tenantId,account?.username,permissions.includes('CUSTOMER_RELATION_VIEW')]);

 useEffect(()=>{if(!selected||!canView){setNotes([]);return;}let active=true;setBusy(true);setError('');
  vendorApi('/customers/'+encodeURIComponent(selected)+'/crm-notes').then((items:CrmNote[])=>{if(active)setNotes(items);})
   .catch(e=>{if(active)setError((e as Error).message||'Private CRM notes could not be loaded.');})
   .finally(()=>{if(active)setBusy(false);});
  return()=>{active=false;};
 },[selected,canView,account?.tenantId,account?.username]);

 async function saveNote(event:FormEvent){event.preventDefault();if(!selected||!draft.trim()||!reason.trim())return;
  setBusy(true);setError('');setNotice('');
  try{const path='/customers/'+encodeURIComponent(selected)+'/crm-notes';
   if(editing){await vendorApi(path+'/'+editing.id,'PUT',{expectedVersion:editing.version,note:draft,reason});setNotice('Private note updated.');}
   else{await vendorApi(path,'POST',{note:draft,reason});setNotice('Private note added.');}
   setNotes(await vendorApi(path));setDraft('');setReason('');setEditing(undefined);
  }catch(e){setError((e as Error).message||'The CRM note could not be saved.');}finally{setBusy(false);}
 }
 return <section className="customer-crm" aria-labelledby="customer-crm-title">
  <h1 id="customer-crm-title">Customer relationships &amp; private CRM</h1>
  <p>Relationships and notes belong only to the active vendor tenant. Customers and other vendors cannot view this private workspace data.</p>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  {permissions.includes("LEAD_VIEW")&&<VendorLeadInbox/>}
  {permissions.includes("CUSTOMER_RELATION_VIEW")&&<section aria-label="Vendor customer relationships"><h2>Linked customers</h2>
   {busy&&!customers.length&&<p role="status">Loading customer relationships…</p>}
   {!busy&&!customers.length&&<p className="empty-state">No verified customer relationships are available for this vendor.</p>}
   {!!customers.length&&<label>Select customer<select value={selected} onChange={e=>{setSelected(e.target.value);setEditing(undefined);setDraft('');setReason('');}}>
    {customers.map(c=><option key={c.relationshipId} value={c.relationshipId}>{c.displayName}</option>)}
   </select></label>}
  </section>}
  {selected&&<section aria-label="Private customer notes"><h2>Private CRM notes</h2>
   {!canView&&<p>Your role does not include CRM_NOTE_VIEW.</p>}
   {canView&&!notes.length&&<p className="empty-state">No private notes have been recorded for this relationship.</p>}
   {canView&&<ul>{notes.map(n=><li key={n.id}><p>{n.note}</p><small>Updated by {n.updatedBy} · {new Date(n.updatedAt).toLocaleString()}</small>
    {canUpdate&&<button type="button" className="secondary" disabled={busy} onClick={()=>{setEditing(n);setDraft(n.note);setReason('');}}>Edit note</button>}
   </li>)}</ul>}
   {(canCreate||(Boolean(editing)&&canUpdate))&&<form onSubmit={saveNote}><h3>{editing?'Update private note':'Add private note'}</h3>
    <label>Note<textarea required maxLength={2000} rows={4} value={draft} onChange={e=>setDraft(e.target.value)}/></label>
    <label>Reason for change<input required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label>
    <button type="submit" disabled={busy||!draft.trim()||!reason.trim()}>{editing?'Save note changes':'Add private note'}</button>
    {editing&&<button type="button" className="secondary" disabled={busy} onClick={()=>{setEditing(undefined);setDraft('');setReason('');}}>Cancel edit</button>}
   </form>}
  </section>}
 </section>;
}
