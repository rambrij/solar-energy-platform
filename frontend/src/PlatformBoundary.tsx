import './platform.css';
import './platform-overrides.css';
import LoginPanel from './LoginPanel';
import {useSession} from './session';
import PlatformApp from './PlatformApp';
import PlatformHealthWidget from './PlatformHealthWidget';
import PlatformSubscriptions from './PlatformSubscriptions';
import PlatformMfaEnrollment from './PlatformMfaEnrollment';
import {hasPlatformPagePermission} from './usePermissions';

export default function PlatformBoundary(){
 const session=useSession();
 if(!session.ready)return <main className="platform-login"><p role="status">Restoring your secure platform session…</p></main>;
 if(!session.account)return <main className="platform-login"><LoginPanel/><PlatformMfaEnrollment/></main>;
 if(session.account.actorType!=='PLATFORM'||!session.account.permissions.some(p=>p.startsWith('PLATFORM_'))||session.account.permissions.includes('CUSTOMER')||session.account.tenantId!==null){
  return <main className="platform-login"><section role="alert"><h1>Platform access denied</h1><p>This workspace is separate from vendor and customer accounts. Sign in with an authorized Platform Super Admin identity.</p></section></main>;
 }
 const isSubscriptions=window.location.pathname.startsWith('/platform/subscriptions');
 const canViewSubscriptions=hasPlatformPagePermission('platform-subscriptions',session.account.permissions);
 const canViewDashboard=hasPlatformPagePermission('dashboard',session.account.permissions);
 if(isSubscriptions&&!canViewSubscriptions)return <main className="platform-login"><section role="alert"><h1>Platform access denied</h1><p>Your platform identity does not have permission to view tenant subscriptions.</p></section></main>;
 return <div className="platform-workspace"><nav className="platform-global-nav" aria-label="Platform section shortcuts">{canViewDashboard&&<a href="/platform" aria-current={!isSubscriptions?'page':undefined}>Platform overview</a>}{canViewSubscriptions&&<a href="/platform/subscriptions" aria-current={isSubscriptions?'page':undefined}>Tenant subscriptions</a>}{canViewDashboard&&<PlatformHealthWidget/>}</nav>{isSubscriptions?<PlatformSubscriptions/>:<PlatformApp/>}</div>;
}
