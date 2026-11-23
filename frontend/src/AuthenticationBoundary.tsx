import {Fragment,useEffect,type ReactNode} from 'react';
import {useSession,restoreSession} from './session';
import LoginPanel from './LoginPanel';
import Shell from './Shell';

export function SessionBootstrap({children}:{children:ReactNode}){
 useEffect(()=>{void restoreSession();},[]);
 return children;
}

export default function AuthenticationBoundary({permission,mode,children}:{permission?:string;mode:'online'|'pos';children:ReactNode}){
 const session=useSession();
 if(session.loading)return <Shell mode={mode}><p role="status">Restoring your secure session…</p></Shell>;
 if(session.status==='error')return <Shell mode={mode}><p role="alert">Your secure session could not be verified. Protected workspace access is unavailable. Reload or sign in again.</p></Shell>;
 if(!session.account)return <Shell mode={mode}><LoginPanel/></Shell>;
 const wrongActor=mode==='pos'
  ?session.account.actorType!=='VENDOR'||session.account.tenantId===null
  :permission==='CUSTOMER'&&session.account.actorType!=='CUSTOMER';
 if(wrongActor||(permission&&!session.account.permissions.includes(permission)))return <Shell mode={mode}><p role="alert">Your account does not have access to this workspace. Choose an authorized account using the sign-in menu.</p></Shell>;
 const actorKey=[session.account.username,session.account.actorType,session.account.tenantId??'none',session.account.branchId??'none',session.account.membershipId??'none'].join(':');
 return <Fragment key={actorKey}>{children}</Fragment>;
}
