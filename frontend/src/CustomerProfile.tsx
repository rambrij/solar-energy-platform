import {useEffect,useState} from 'react';
import {marketplaceApi} from './marketplaceApi';

type Profile={id:number;displayName:string|null;email:string};
export default function CustomerProfile(){
 const [profile,setProfile]=useState<Profile>(),[name,setName]=useState(''),[busy,setBusy]=useState(true),[saving,setSaving]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 async function load(){
  setBusy(true);setError('');
  try{const value:Profile=await marketplaceApi('/api/v1/customer/profile');setProfile(value);setName(value.displayName??'');}
  catch{setError('Your customer profile is unavailable. Check your customer session and retry.');}
  finally{setBusy(false);}
 }
 useEffect(()=>{void load();},[]);
 async function save(event:React.FormEvent){
  event.preventDefault();setSaving(true);setError('');setNotice('');
  try{const value:Profile=await marketplaceApi('/api/v1/customer/profile','PUT',{displayName:name.trim()||null});setProfile(value);setName(value.displayName??'');setNotice('Profile updated.');}
  catch{setError('Profile update was not confirmed. Your previous profile values remain saved.');}
  finally{setSaving(false);}
 }
 return <section aria-labelledby="customer-profile-title" className="customer-profile">
  <h2 id="customer-profile-title">My customer profile</h2>
  {busy&&<p role="status">Loading your profile…</p>}
  {error&&<p role="alert">{error}</p>}
  {notice&&<p role="status">{notice}</p>}
  {profile&&<form onSubmit={save}>
   <label>Email address<input type="email" value={profile.email} readOnly/></label>
   <label>Display name<input maxLength={120} value={name} disabled={saving} onChange={event=>setName(event.target.value)}/></label>
   <p>Your email is the global sign-in identity and cannot be changed here.</p>
   <button disabled={busy||saving}>{saving?'Saving…':'Save profile'}</button>
   <button type="button" className="secondary" disabled={busy||saving} onClick={()=>{setName(profile.displayName??'');setNotice('Changes discarded.');}}>Discard changes</button>
  </form>}
  <CustomerSessionSecurity/>
 </section>;
}

function CustomerSessionSecurity(){
 const [confirm,setConfirm]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 async function revoke(){
  setBusy(true);setError('');setNotice('');
  try{await marketplaceApi('/api/v1/auth/sessions/revoke-others','POST',{});setConfirm(false);setNotice('Other active sessions were signed out. This device remains signed in.');}
  catch{setError('Other sessions could not be signed out. This device remains active; retry when connected.');}
  finally{setBusy(false);}
 }
 return <section aria-labelledby="customer-security-title" className="customer-security">
  <h3 id="customer-security-title">Account security</h3>
  <p>Sign out other active sessions for this global account. This device will remain signed in.</p>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  <button type="button" className="secondary" disabled={busy} onClick={()=>{setError('');setNotice('');setConfirm(true);}}>Sign out other devices</button>
  {confirm&&<section role="dialog" aria-modal="true" aria-labelledby="customer-revoke-title">
   <h3 id="customer-revoke-title">Sign out other sessions?</h3>
   <p>All other active sessions will be revoked. Your current device remains active.</p>
   <button type="button" className="secondary" disabled={busy} onClick={()=>setConfirm(false)}>Keep other sessions</button>
   <button type="button" disabled={busy} onClick={()=>void revoke()}>{busy?'Signing out…':'Confirm sign out'}</button>
  </section>}
 </section>;
}
