import {useEffect,useState} from 'react';
import {verifyCustomerEmail} from './session';
type State='checking'|'verified'|'failed';
export default function CustomerEmailVerification(){
 const token=new URLSearchParams(window.location.search).get('token')??'';
 const [state,setState]=useState<State>('checking');
 useEffect(()=>{let active=true;if(!token){setState('failed');return ()=>{active=false;};}
  void verifyCustomerEmail(token).then(()=>{if(active)setState('verified');}).catch(()=>{if(active)setState('failed');});
  return ()=>{active=false;};
 },[token]);
 return <main className="login-panel" aria-labelledby="email-verification-title">
  <h1 id="email-verification-title">Email verification</h1>
  {state==='checking'&&<p role="status">Checking your verification link…</p>}
  {state==='verified'&&<><h2>Email verified</h2><p role="status">Your customer email is verified. You can now sign in.</p></>}
  {state==='failed'&&<p role="alert">This verification link is invalid or has expired. Request a new link from the sign-in page.</p>}
  <p><a href="/">Return to sign in</a></p>
 </main>;
}
