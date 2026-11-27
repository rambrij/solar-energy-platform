import {useCallback,useEffect,useState,type FormEvent} from 'react';
import {vendorApi} from './api';
import {useSession} from './session';

type Account={id:number;providerKey:string;maskedMerchantAccountReference:string;status:string;submittedAt:string;updatedAt:string;verifiedAt:string|null;rejectionReason:string|null;version:number};
const statusLabel=(status:string)=>status.replaceAll('_',' ').toLowerCase().replace(/\b\w/g,c=>c.toUpperCase());

export default function VendorPaymentAccounts(){
 const {account}=useSession();
 const canView=account?.permissions.includes('TENANT_PROFILE_VIEW')??false;
 const canUpdate=account?.permissions.includes('TENANT_PROFILE_UPDATE')??false;
 const [accounts,setAccounts]=useState<Account[]>([]);
 const [providerKey,setProviderKey]=useState('');
 const [merchantReference,setMerchantReference]=useState('');
 const [reason,setReason]=useState('');
 const [loading,setLoading]=useState(false);
 const [saving,setSaving]=useState(false);
 const [error,setError]=useState('');
 const [notice,setNotice]=useState('');

 const refresh=useCallback(async()=>{
  if(!canView)return;
  setLoading(true);setError('');
  try{setAccounts(await vendorApi('/payment-accounts') as Account[]);}
  catch(e){setError((e as Error).message||'Merchant accounts could not be loaded.');}
  finally{setLoading(false);}
 },[canView]);
 useEffect(()=>{void refresh();},[refresh]);

 async function submit(event:FormEvent){
  event.preventDefault();
  const provider=providerKey.trim().toUpperCase(),reference=merchantReference.trim(),auditReason=reason.trim();
  if(!provider||!reference||!auditReason)return;
  const existing=accounts.find(item=>item.providerKey===provider);
  setSaving(true);setError('');setNotice('');
  try{
   const saved=await vendorApi('/payment-accounts','PUT',{
    providerKey:provider,merchantAccountReference:reference,
    expectedVersion:existing?.version??0,reason:auditReason
   }) as Account;
   setAccounts(current=>[saved,...current.filter(item=>item.providerKey!==saved.providerKey)]);
   setMerchantReference('');setReason('');
   setNotice('Merchant account reference submitted. It remains pending until the selected provider verifies the merchant identity.');
  }catch(e){setError((e as Error).message||'Merchant account could not be submitted.');}
  finally{setSaving(false);}
 }

 if(!canView)return <section aria-label="Vendor payment account"><p role="alert">Access denied. Vendor profile view permission is required.</p></section>;
 return <section className="vendor-payment-accounts" aria-labelledby="vendor-payment-accounts-title" aria-busy={loading||saving}>
  <h3 id="vendor-payment-accounts-title">Vendor payment account</h3>
  <p>Submit the merchant-account reference issued by your selected payment provider. Do not enter API keys, passwords, bank login details or other provider secrets here.</p>
  <p>Online payments remain unavailable until a real provider verifies the merchant identity and direct vendor settlement is approved for the target product and jurisdiction.</p>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  {loading&&<p role="status">Loading merchant account status…</p>}
  {!loading&&!accounts.length&&<p className="empty-state">No merchant account reference has been submitted.</p>}
  {!!accounts.length&&<ul aria-label="Submitted merchant accounts">{accounts.map(item=><li key={item.id}>
   <h4>{item.providerKey}</h4>
   <p>Account reference: {item.maskedMerchantAccountReference}</p>
   <p>Status: {statusLabel(item.status)}</p>
   {item.submittedAt&&<p>Submitted: {new Date(item.submittedAt).toLocaleString()}</p>}
   {item.verifiedAt&&<p>Provider verified: {new Date(item.verifiedAt).toLocaleString()}</p>}
   {item.rejectionReason&&<p>Provider review: {item.rejectionReason}</p>}
  </li>)}</ul>}
  {canUpdate&&<form aria-label="Submit merchant account reference" onSubmit={submit}>
   <label>Payment provider key
    <input required minLength={2} maxLength={40} pattern="[A-Za-z][A-Za-z0-9_-]{1,39}" value={providerKey} onChange={e=>setProviderKey(e.target.value)} placeholder="Provider key from your onboarding instructions"/>
   </label>
   <label>Merchant account reference
    <input required minLength={3} maxLength={160} pattern="[A-Za-z0-9][A-Za-z0-9._:/-]{2,159}" value={merchantReference} onChange={e=>setMerchantReference(e.target.value)} autoComplete="off"/>
   </label>
   <label>Reason for submission
    <input required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/>
   </label>
   <button type="submit" disabled={saving||loading||!providerKey.trim()||!merchantReference.trim()||!reason.trim()}>
    {saving?'Submitting…':'Submit merchant account'}
   </button>
  </form>}
  <button type="button" className="secondary" disabled={loading||saving} onClick={()=>void refresh()}>Refresh account status</button>
 </section>;
}
