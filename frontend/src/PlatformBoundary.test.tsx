import {cleanup,render,screen} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import PlatformBoundary from './PlatformBoundary';
import {useSession} from './session';

vi.mock('./session',()=>({useSession:vi.fn()}));
vi.mock('./LoginPanel',()=>({default:()=>null}));
vi.mock('./PlatformMfaEnrollment',()=>({default:()=>null}));
vi.mock('./PlatformApp',()=>({default:()=> <div>Platform overview screen</div>}));
vi.mock('./PlatformSubscriptions',()=>({default:()=> <div>Subscription screen</div>}));
vi.mock('./PlatformHealthWidget',()=>({default:()=> <span>Platform health widget</span>}));

const account=(permissions:string[],actorType:'PLATFORM'|'VENDOR'|'CUSTOMER'='PLATFORM')=>({
 username:'operator@platform.test',tenantId:actorType==='VENDOR'?42:null,branchId:actorType==='VENDOR'?7:null,permissions,actorType
});
beforeEach(()=>{vi.clearAllMocks();window.history.replaceState({},'','/platform');});
afterEach(()=>{cleanup();window.history.replaceState({},'','/');});

describe('PlatformBoundary page permission gates',()=>{
 it('shows subscription shortcut and page only with subscription view',()=>{
  vi.mocked(useSession).mockReturnValue({ready:true,account:account(['PLATFORM_SUBSCRIPTION_VIEW'])} as ReturnType<typeof useSession>);
  window.history.replaceState({},'','/platform/subscriptions');
  render(<PlatformBoundary/>);
  expect(screen.getByRole('link',{name:'Tenant subscriptions'}).getAttribute('aria-current')).toBe('page');
  expect(screen.getByText('Subscription screen')).toBeTruthy();
  expect(screen.queryByText('Platform health widget')).toBeNull();
 });
 it('denies direct subscription route without subscription view',()=>{
  vi.mocked(useSession).mockReturnValue({ready:true,account:account(['PLATFORM_SUPPORT_ACCESS'])} as ReturnType<typeof useSession>);
  window.history.replaceState({},'','/platform/subscriptions');
  render(<PlatformBoundary/>);
  expect(screen.getByText('Platform access denied')).toBeTruthy();
  expect(screen.queryByText('Subscription screen')).toBeNull();
  expect(screen.queryByRole('link',{name:'Tenant subscriptions'})).toBeNull();
 });
 it('shows platform health only with analytics view and omits subscription shortcut',()=>{
  vi.mocked(useSession).mockReturnValue({ready:true,account:account(['PLATFORM_ANALYTICS_VIEW'])} as ReturnType<typeof useSession>);
  render(<PlatformBoundary/>);
  expect(screen.getByText('Platform overview screen')).toBeTruthy();
  expect(screen.getByText('Platform health widget')).toBeTruthy();
  expect(screen.queryByRole('link',{name:'Tenant subscriptions'})).toBeNull();
 });

 it('rejects a vendor actor even when its snapshot contains a platform permission',()=>{
  vi.mocked(useSession).mockReturnValue({ready:true,account:account(['PLATFORM_ANALYTICS_VIEW'],'VENDOR')} as ReturnType<typeof useSession>);
  render(<PlatformBoundary/>);
  expect(screen.getByRole('alert').textContent).toContain('Platform access denied');
  expect(screen.queryByText('Platform overview screen')).toBeNull();
  expect(screen.queryByRole('navigation',{name:'Platform section shortcuts'})).toBeNull();
 });

});
