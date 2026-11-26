import {useState,type FormEvent} from 'react';
import {marketplaceApi} from './marketplaceApi';
import {useSession} from './session';

export default function MarketplaceEnquiryForm({slug,productId,productName}:{slug:string;productId:number;productName:string}){
 const {account}=useSession();
 const customer=account?.actorType==='CUSTOMER';
 const [open,setOpen]=useState(false),[name,setName]=useState(''),[email,setEmail]=useState(''),[phone,setPhone]=useState(''),[message,setMessage]=useState('');
 const [consent,setConsent]=useState(false),[link,setLink]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 async function submit(event:FormEvent){
  event.preventDefault();if(!consent)return;
  setBusy(true);setError('');setNotice('');
  try{
   await marketplaceApi(`/api/v1/marketplace/vendors/${encodeURIComponent(slug)}/enquiries`,'POST',{contactName:name,contactEmail:email,contactPhone:phone,message,productId,consent,linkToAccount:link});
   setNotice('Your enquiry has been sent to this vendor.');setOpen(false);
   setName('');setEmail('');setPhone('');setMessage('');setConsent(false);setLink(false);
  }catch(e){setError((e as Error).message||'The enquiry could not be sent.');}finally{setBusy(false);}
 }
 return <section aria-label="Ask this vendor about the product"><h3>Ask this vendor about {productName}</h3>
  <p>Share your question directly with this vendor. Your details are shared only after you give consent.</p>
  {notice&&<p role="status">{notice}</p>}{error&&<p role="alert">{error}</p>}
  {!open?<button type="button" className="secondary" onClick={()=>setOpen(true)}>Send a product enquiry</button>:
  <form onSubmit={submit}>
   <label>Your name<input required maxLength={120} value={name} onChange={e=>setName(e.target.value)}/></label>
   <label>Email address<input required type="email" maxLength={254} value={email} onChange={e=>setEmail(e.target.value)}/></label>
   <label>Phone (optional)<input maxLength={30} value={phone} onChange={e=>setPhone(e.target.value)}/></label>
   <label>Your question<textarea required maxLength={2000} rows={4} value={message} onChange={e=>setMessage(e.target.value)}/></label>
   <label><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/> I agree to share my contact details and message with this vendor so they can respond.</label>
   {customer&&<label><input type="checkbox" checked={link} onChange={e=>setLink(e.target.checked)}/> Link this enquiry to my customer account (email must match the signed-in account).</label>}
   <button type="submit" disabled={busy||!consent||!name.trim()||!email.trim()||!message.trim()}>{busy?'Sending enquiry…':'Send enquiry'}</button>
   <button type="button" className="secondary" disabled={busy} onClick={()=>setOpen(false)}>Cancel enquiry</button>
  </form>}
 </section>;
}
