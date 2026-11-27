import {useState} from 'react';
import {resendCustomerEmailVerification,signIn,type ActorType,type AuthError} from './session';

export default function LoginPanel({onSignedIn}:{onSignedIn?:()=>void}){
 const [register,setRegister]=useState(false),[username,setUsername]=useState(''),[password,setPassword]=useState(''),[totpCode,setTotpCode]=useState('');
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[contextRequired,setContextRequired]=useState(false);
 const [actorType,setActorType]=useState<Exclude<ActorType,'PLATFORM'>>('CUSTOMER');
 const [verificationPending,setVerificationPending]=useState(false),[resendPrompt,setResendPrompt]=useState(false),[resendMessage,setResendMessage]=useState('');
 async function submit(){
  setBusy(true);setError('');
  try{
   if(contextRequired)await signIn(username,password,register,totpCode||undefined,actorType);
   else if(totpCode)await signIn(username,password,register,totpCode);
   else await signIn(username,password,register);
   setPassword('');setTotpCode('');setContextRequired(false);
   if(register){setRegister(false);setVerificationPending(true);setResendPrompt(false);setResendMessage('');return;}
   setVerificationPending(false);setResendPrompt(false);onSignedIn?.();
  }catch(e){
   const authError=e as AuthError;
   if(!register&&authError.status===409){
    setContextRequired(true);
    setError(authError.message);
   }else{
    setPassword('');setTotpCode('');
    setResendPrompt(!register&&authError.status===401);
    setError((e as Error).message);
   }
  }finally{setBusy(false);}
 }
 async function resend(){
  setBusy(true);setResendMessage('');
  try{await resendCustomerEmailVerification(username);setResendMessage('If this account needs verification, an email will be sent.');}
  catch(e){setError((e as Error).message);}
  finally{setBusy(false);}
 }
 return <form className="login-panel" onSubmit={e=>{e.preventDefault();void submit();}} aria-label={register?'Create customer account':'Sign in'}>
  <h2>{register?'Create your customer account':'Sign in'}</h2>
  <p>{register?'One account for purchases across vendors. Staff memberships are provisioned by your vendor.':'Use your own customer or named staff account.'}</p>
  {error&&<p role="alert">{error}</p>}
  {verificationPending&&<section aria-label="Email verification"><p role="status">We sent a verification link to {username}. Verify your email before signing in.</p><button type="button" className="secondary" disabled={busy} onClick={()=>void resend()}>Resend verification email</button>{resendMessage&&<p role="status">{resendMessage}</p>}</section>}
  {resendPrompt&&!verificationPending&&<p>Having trouble signing in? You can request an email verification link. <button type="button" className="secondary" disabled={busy} onClick={()=>void resend()}>Resend verification email</button></p>}
  <fieldset disabled={busy}>
   <label>{register?'Email address':'Email or username'}<input autoComplete="username" type={register?'email':'text'} maxLength={100} required value={username} onChange={e=>setUsername(e.target.value)}/></label>
   <label>Password<input type="password" autoComplete={register?'new-password':'current-password'} minLength={register?12:undefined} maxLength={72} required value={password} onChange={e=>setPassword(e.target.value)}/></label>
   {!register&&<label>Authenticator code <small>(Platform accounts only)</small><input inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} value={totpCode} onChange={e=>setTotpCode(e.target.value.replace(/\D/g,'').slice(0,6))} placeholder="6-digit code"/></label>}
   {contextRequired&&<fieldset className="actor-context-choice" aria-label="Actor context">
    <legend>Choose one workspace context</legend>
    <label><input type="radio" name="actor-context" value="CUSTOMER" checked={actorType==='CUSTOMER'} onChange={()=>setActorType('CUSTOMER')}/> Customer marketplace</label>
    <label><input type="radio" name="actor-context" value="VENDOR" checked={actorType==='VENDOR'} onChange={()=>setActorType('VENDOR')}/> Vendor employee</label>
    <small>Only the selected context's permissions are included in this session.</small>
   </fieldset>}
   <button type="submit">{busy?'Signing in…':register?'Create account':contextRequired?'Continue in selected workspace':'Sign in securely'}</button>
   <button type="button" className="secondary" onClick={()=>{setRegister(value=>!value);setError('');setPassword('');setTotpCode('');setContextRequired(false);setVerificationPending(false);setResendPrompt(false);setResendMessage('');}}>{register?'Use existing account':'Create a customer account'}</button>
  </fieldset>
 </form>;
}
