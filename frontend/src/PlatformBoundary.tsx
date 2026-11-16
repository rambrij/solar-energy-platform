import './platform.css';
import './platform-overrides.css';
import LoginPanel from './LoginPanel';
import {useSession} from './session';
import PlatformApp from './PlatformApp';
import PlatformHealthWidget from './PlatformHealthWidget';
import PlatformSubscriptions from './PlatformSubscriptions';

export default function PlatformBoundary(){
 const session=useSession();
 if(!session.ready)return <main className="platform-login"><p role="status">Restoring your secure platform session…</p></main>;
 if(!session.account)return <main className="platform-login"><LoginPanel/></main>;
 if(!session.account.permissions.some(p=>p.startsWith('PLATFORM_'))||session.account.permissions.includes('CUSTOMER')||session.account.tenantId!==null){
  return <main className="platform-login"><section role="alert"><h1>Platform access denied</h1><p>This workspace is separate from vendor and customer accounts. Sign in with an authorized Platform Super Admin identity.</p></section></main>;
 }
 return <div className="platform-workspace"><nav className="platform-global-nav" aria-label="Platform section shortcuts"><a href="/platform">Platform overview</a><a href="/platform/subscriptions">Tenant subscriptions</a><PlatformHealthWidget/></nav>{window.location.pathname.startsWith('/platform/subscriptions')?<PlatformSubscriptions/>:<PlatformApp/>}</div>;
}
