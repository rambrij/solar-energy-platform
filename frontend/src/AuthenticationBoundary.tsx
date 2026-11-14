import {useEffect,type ReactNode} from 'react';
import {useSession,restoreSession} from './session';
import LoginPanel from './LoginPanel';
import Shell from './Shell';
export function SessionBootstrap({children}:{children:ReactNode}){useEffect(()=>{void restoreSession();},[]);return children;}
export default function AuthenticationBoundary({permission,mode,children}:{permission:string;mode:'online'|'pos';children:ReactNode}){const session=useSession();if(!session.ready)return <Shell mode={mode}><p role="status">Restoring your secure session…</p></Shell>;if(!session.account)return <Shell mode={mode}><LoginPanel/></Shell>;if(!session.account.permissions.includes(permission))return <Shell mode={mode}><p role="alert">Your account does not have access to this workspace. Choose an authorized account using the sign-in menu.</p></Shell>;return children;}
