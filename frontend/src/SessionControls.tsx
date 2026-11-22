import {useState} from 'react';
import {useSession,signOut,switchActorContext,type ActorType} from './session';
import LoginPanel from './LoginPanel';

export default function SessionControls(){
 const {account}=useSession();
 const [open,setOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const availableActors=account?.availableActorTypes??[];
 const hasDualContext=availableActors.includes('CUSTOMER')&&availableActors.includes('VENDOR');
 const nextActor:Exclude<ActorType,'PLATFORM'>|null=hasDualContext?(account?.actorType==='CUSTOMER'?'VENDOR':account?.actorType==='VENDOR'?'CUSTOMER':null):null;
 const nextLabel=nextActor==='CUSTOMER'?'Customer workspace':'Vendor workspace';
 return <div className="session-controls">
  {account?<>
   <span aria-label="Active actor context">{account.username} · {account.actorType==='CUSTOMER'?'Customer':account.actorType==='VENDOR'?'Vendor':'Platform'}</span>
   {nextActor&&<button disabled={busy} onClick={async()=>{
    setBusy(true);setError('');
    try{await switchActorContext(nextActor);}catch(e){setError((e as Error).message||'Context switch was not completed.');}
    finally{setBusy(false);}
   }}>{busy?'Switching…':`Switch to ${nextLabel}`}</button>}
   <button disabled={busy} onClick={async()=>{setBusy(true);setError('');try{await signOut();}catch{setError('Sign-out was not confirmed. Retry before leaving this device.');}finally{setBusy(false);}}}>Sign out</button>
  </>:<>
   <button aria-expanded={open} onClick={()=>setOpen(value=>!value)}>Sign in / Register</button>
   {open&&<LoginPanel onSignedIn={()=>setOpen(false)}/>}
  </>}
  {error&&<p role="alert">{error}</p>}
 </div>;
}
