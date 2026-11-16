import {useState} from 'react';
import {authPost,ApiError} from './api';

/** First-time platform MFA is bootstrapped with a one-time, operator-issued enrollment token. */
export default function PlatformMfaEnrollment(){
 const [token,setToken]=useState(''),[code,setCode]=useState(''),[secret,setSecret]=useState(''),[uri,setUri]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 async function start(){
  setBusy(true);setError('');setNotice('');
  try{const result=await authPost('/platform-mfa/enrollment/start',{enrollmentToken:token.trim()});setSecret(result.secret);setUri(result.otpauthUri);setNotice('Scan or enter this secret in an authenticator app. The displayed secret is shown only for this enrollment.');}
  catch(e){setError(e instanceof ApiError?e.message:(e as Error).message);}
  finally{setBusy(false);}
 }
 async function complete(){
  setBusy(true);setError('');setNotice('');
  try{await authPost('/platform-mfa/enrollment/complete',{enrollmentToken:token.trim(),code});setSecret('');setUri('');setCode('');setToken('');setNotice('Platform MFA enrollment completed. You can now sign in with your password and authenticator code.');}
  catch(e){setError(e instanceof ApiError?e.message:(e as Error).message);}
  finally{setBusy(false);}
 }
 return <section className="platform-mfa-enrollment" aria-labelledby="platform-mfa-heading">
  <h2 id="platform-mfa-heading">Set up Platform MFA</h2>
  <p>Enrollment requires a one-time authorization token issued to the platform operator through the approved secure provisioning process. A password alone cannot activate production platform access.</p>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  <label>One-time enrollment token<input type="password" autoComplete="off" value={token} onChange={e=>setToken(e.target.value)} maxLength={43}/></label>
  <button type="button" disabled={busy||token.trim().length!==43} onClick={()=>void start()}>Start secure enrollment</button>
  {secret&&<div className="platform-mfa-secret">
   <p><strong>Authenticator secret</strong></p><code>{secret}</code>
   <p>Authenticator URI</p><code>{uri}</code>
   <label>Six-digit authenticator code<input inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} value={code} onChange={e=>setCode(e.target.value.replace(/\D/g,'').slice(0,6))}/></label>
   <button type="button" disabled={busy||code.length!==6} onClick={()=>void complete()}>Verify and enable MFA</button>
  </div>}
 </section>;
}
