import {useCallback,useEffect,useState} from 'react';
import {platformApi,ApiError} from './api';

type Identity={username:string;mfaEnrolled:boolean;failedMfaAttempts:number;lockedUntil:string|null;permissions:string[]};
type Issued={username:string;token:string;expiresAt:string};

export default function PlatformIdentityAdmin(){
 const [items,setItems]=useState<Identity[]>([]),[reason,setReason]=useState(''),[issued,setIssued]=useState<Issued|null>(null);
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const load=useCallback(async()=>{try{setError('');setItems(await platformApi('/identities'));}catch(e){setError(e instanceof ApiError?e.message:(e as Error).message);}},[]);
 useEffect(()=>{void load();},[load]);
 async function issue(username:string){
  if(!reason.trim()){setError('A reason is required before issuing an enrollment token.');return;}
  setBusy(true);setError('');setNotice('');setIssued(null);
  try{const result=await platformApi('/identities/'+encodeURIComponent(username)+'/mfa-enrollment','POST',{reason:reason.trim()});
   setIssued(result);setNotice('One-time token issued. Copy it to the target operator through the approved secure channel.');setReason('');await load();}
  catch(e){setError(e instanceof ApiError?e.message:(e as Error).message);}
  finally{setBusy(false);}
 }
 return <section className="platform-panel">
  <div className="platform-panel-heading"><div><h2>Platform identities and MFA</h2><p>Only a separate authorized platform operator can reset another platform identity’s MFA. Tokens expire after 30 minutes and are shown once.</p></div><button className="secondary" onClick={()=>void load()} disabled={busy}>Refresh</button></div>
  {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  {issued&&<div className="platform-invitation"><strong>One-time MFA enrollment token for {issued.username}</strong><code>{issued.token}</code><p>Expires {new Date(issued.expiresAt).toLocaleString()}. Share once via the approved secure channel. The token is stored only as a SHA-256 hash.</p><button type="button" className="secondary" onClick={()=>void navigator.clipboard?.writeText(issued.token)}>Copy token</button></div>}
  <label>Reason for enrollment/reset<input maxLength={500} value={reason} onChange={e=>setReason(e.target.value)} placeholder="Ticket or recovery reason"/></label>
  <div className="platform-table-wrap"><table><thead><tr><th>Platform identity</th><th>MFA</th><th>Failed attempts</th><th>Lockout</th><th>Permissions</th><th>Action</th></tr></thead><tbody>{items.map(item=><tr key={item.username}>
   <td>{item.username}</td><td>{item.mfaEnrolled?'Enrolled':'Not enrolled'}</td><td>{item.failedMfaAttempts}</td><td>{item.lockedUntil??'—'}</td><td>{item.permissions.join(', ')}</td>
   <td><button type="button" className="secondary" disabled={busy||!reason.trim()} onClick={()=>void issue(item.username)}>Issue / reset MFA</button></td>
  </tr>)}</tbody></table></div>
 </section>;
}
