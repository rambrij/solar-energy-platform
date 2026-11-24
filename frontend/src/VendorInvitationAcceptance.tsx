import {useState,type FormEvent} from 'react';
import {authPost,ApiError} from './api';

export default function VendorInvitationAcceptance(){
 const [token,setToken]=useState(()=>new URLSearchParams(window.location.search).get('token')??'');
 const [password,setPassword]=useState(''),[confirmation,setConfirmation]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[complete,setComplete]=useState(false);
 async function submit(event:FormEvent){
  event.preventDefault();setError('');
  const invitationToken=token.trim();
  if(!invitationToken){setError('Enter the invitation token you received from your administrator.');return;}
  if(password!==confirmation){setError('The passwords do not match.');return;}
  setBusy(true);
  try{await authPost('/invitations/accept',{token:invitationToken,password});setComplete(true);setToken('');setPassword('');setConfirmation('');}
  catch(e){setError(e instanceof ApiError?e.message:(e as Error).message);}
  finally{setBusy(false);}
 }
 return <main className="platform-login"><section className="login-panel">
  <span className="platform-kicker">SAUMYA SOLAR MARKETPLACE</span><h1>Activate vendor account</h1>
  {complete?<p role="status">Your account is activated. You can now sign in to your vendor workspace.</p>:<>
   <p>Enter the one-time token you received from your administrator and set a password for your vendor account.</p>
   {error&&<p role="alert">{error}</p>}
   <form onSubmit={submit}>
    <label>Invitation token<input required type="password" autoComplete="off" value={token}
     onChange={event=>setToken(event.target.value)}/></label>
    <label>New password<input type="password" autoComplete="new-password" minLength={12} maxLength={72} required value={password} onChange={e=>setPassword(e.target.value)}/></label>
    <label>Confirm password<input type="password" autoComplete="new-password" minLength={12} maxLength={72} required value={confirmation} onChange={e=>setConfirmation(e.target.value)}/></label>
    <button disabled={busy||!token.trim()}>Activate account</button>
   </form>
  </>}
 </section></main>;
}
