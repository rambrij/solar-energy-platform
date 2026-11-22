import {useState,type FormEvent} from 'react';
import {authPost,ApiError} from './api';

export default function VendorInvitationAcceptance(){
 const token=new URLSearchParams(window.location.search).get('token')??'';
 const [password,setPassword]=useState(''),[confirmation,setConfirmation]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[complete,setComplete]=useState(false);
 async function submit(event:FormEvent){
  event.preventDefault();setError('');
  if(!token){setError('This invitation link is missing its token. Ask your platform administrator to issue a new invitation.');return;}
  if(password!==confirmation){setError('The passwords do not match.');return;}
  setBusy(true);
  try{await authPost('/invitations/accept',{token,password});setComplete(true);setPassword('');setConfirmation('');}
  catch(e){setError(e instanceof ApiError?e.message:(e as Error).message);}
  finally{setBusy(false);}
 }
 return <main className="platform-login"><section className="login-panel">
  <span className="platform-kicker">SAUMYA SOLAR MARKETPLACE</span><h1>Activate Vendor Admin account</h1>
  {complete?<p role="status">Your account is activated. You can now sign in to your vendor workspace.</p>:<>
   <p>Set a password for the Vendor Admin identity that was nominated during tenant onboarding.</p>
   {error&&<p role="alert">{error}</p>}
   <form onSubmit={submit}><label>New password<input type="password" autoComplete="new-password" minLength={12} maxLength={72} required value={password} onChange={e=>setPassword(e.target.value)}/></label>
    <label>Confirm password<input type="password" autoComplete="new-password" minLength={12} maxLength={72} required value={confirmation} onChange={e=>setConfirmation(e.target.value)}/></label>
    <button disabled={busy||!token}>Activate account</button>
   </form>
  </>}
 </section></main>;
}
