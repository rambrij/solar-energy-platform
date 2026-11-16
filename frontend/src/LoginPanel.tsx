import {useState} from 'react';
import {signIn} from './session';

export default function LoginPanel({onSignedIn}:{onSignedIn?:()=>void}){
 const [register,setRegister]=useState(false),[username,setUsername]=useState(''),[password,setPassword]=useState(''),[totpCode,setTotpCode]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
 async function submit(){
  setBusy(true);setError('');
  try{await signIn(username,password,register,totpCode);setPassword('');setTotpCode('');onSignedIn?.();}
  catch(e){setPassword('');setTotpCode('');setError((e as Error).message);}
  finally{setBusy(false);}
 }
 return <form className="login-panel" onSubmit={e=>{e.preventDefault();void submit();}} aria-label={register?'Create customer account':'Sign in'}>
  <h2>{register?'Create your customer account':'Sign in'}</h2>
  <p>{register?'One account for purchases across vendors. Staff memberships are provisioned by your vendor.':'Use your own customer or named staff account.'}</p>
  {error&&<p role="alert">{error}</p>}
  <fieldset disabled={busy}>
   <label>{register?'Email address':'Email or username'}<input autoComplete="username" type={register?'email':'text'} maxLength={100} required value={username} onChange={e=>setUsername(e.target.value)}/></label>
   <label>Password<input type="password" autoComplete={register?'new-password':'current-password'} minLength={register?12:undefined} maxLength={72} required value={password} onChange={e=>setPassword(e.target.value)}/></label>
   {!register&&<label>Authenticator code <small>(Platform accounts only)</small><input inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} value={totpCode} onChange={e=>setTotpCode(e.target.value.replace(/\D/g,'').slice(0,6))} placeholder="6-digit code"/></label>}
   <button type="submit">{busy?'Signing in…':register?'Create account':'Sign in securely'}</button>
   <button type="button" className="secondary" onClick={()=>{setRegister(value=>!value);setError('');setPassword('');setTotpCode('');}}>{register?'Use existing account':'Create a customer account'}</button>
  </fieldset>
 </form>;
}
