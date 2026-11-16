import {useState} from 'react';
import {useSession,signOut} from './session';
import LoginPanel from './LoginPanel';
export default function SessionControls(){const {account}=useSession();const [open,setOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');return <div className="session-controls">{account?<><span>{account.username}</span><button disabled={busy} onClick={async()=>{setBusy(true);setError('');try{await signOut();}catch{setError('Sign-out was not confirmed. Retry before leaving this device.');}finally{setBusy(false);}}}>Sign out</button></>:<><button aria-expanded={open} onClick={()=>setOpen(value=>!value)}>Sign in / Register</button>{open&&<LoginPanel onSignedIn={()=>setOpen(false)}/>}</>}{error&&<p role="alert">{error}</p>}</div>;}
