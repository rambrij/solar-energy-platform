import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import PlatformApp from './PlatformApp';
import {useSession} from './session';
import {platformApi} from './api';

vi.mock('./api',()=>({platformApi:vi.fn()}));
vi.mock('./session',()=>({useSession:vi.fn(),signOut:vi.fn()}));

const account={username:'admin@platform.test',tenantId:null,branchId:null,permissions:['PLATFORM_TENANT_VIEW','PLATFORM_TENANT_CREATE','PLATFORM_TENANT_UPDATE','PLATFORM_TENANT_SUSPEND','PLATFORM_TENANT_CLOSE','PLATFORM_PLAN_MANAGE','PLATFORM_SUBSCRIPTION_VIEW','PLATFORM_SUBSCRIPTION_ADJUST','PLATFORM_ANALYTICS_VIEW','PLATFORM_AUDIT_VIEW','PLATFORM_SUPPORT_ACCESS','PLATFORM_CONFIG_MANAGE']};
beforeEach(()=>{
 vi.clearAllMocks();
 vi.mocked(useSession).mockReturnValue({ready:true,account} as ReturnType<typeof useSession>);
 vi.mocked(platformApi).mockResolvedValue({tenantStatuses:[]});
});
afterEach(()=>{cleanup();vi.restoreAllMocks();window.history.replaceState({},'','/platform');});

describe('PlatformApp',()=>{
 it('renders a platform-only control plane',async()=>{
  render(<PlatformApp/>);
  expect(screen.getByText('Platform Super Admin')).toBeTruthy();
  expect(screen.queryByText('Store POS')).toBeNull();
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/dashboard'));
 });
 it('shows tenant onboarding and submits the initial Vendor Admin details',async()=>{
  vi.mocked(platformApi).mockImplementation(async(path)=>{
   if(path.startsWith('/tenants?'))return {items:[],totalElements:0,totalPages:0,page:0};
   return {tenant:{id:11,slug:'demo-vendor',name:'Demo Vendor',legalName:'Demo Vendor Ltd',lifecycleStatus:'PENDING'},invitationToken:'one-time-invitation-token'};
  });
  render(<PlatformApp/>);
  fireEvent.click(screen.getAllByRole('button',{name:/tenants/i})[0]);
  fireEvent.change(screen.getByLabelText('Store slug'),{target:{value:'demo-vendor'}});
  fireEvent.change(screen.getByLabelText('Display name'),{target:{value:'Demo Vendor'}});
  fireEvent.change(screen.getByLabelText('Legal name'),{target:{value:'Demo Vendor Ltd'}});
  fireEvent.change(screen.getByLabelText('Initial Vendor Admin email'),{target:{value:'admin@demo.test'}});
  fireEvent.click(screen.getByRole('button',{name:'Create vendor tenant'}));
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/tenants','POST',expect.objectContaining({slug:'demo-vendor',initialVendorAdminUsername:'admin@demo.test'})));
  expect(await screen.findByText('one-time-invitation-token')).toBeTruthy();
 });
 it('renders historic plan versions as separate records',async()=>{
  vi.mocked(platformApi).mockImplementation(async(path)=>path==='/subscription-plans'?[{id:1,planKey:'starter',displayName:'Starter',active:true,currentVersion:2,versions:[{version:2,currency:'INR',price:'999',billingInterval:'MONTHLY'},{version:1,currency:'INR',price:'799',billingInterval:'MONTHLY'}]}]:{tenantStatuses:[]});
  render(<PlatformApp/>);
  fireEvent.click(screen.getAllByRole('button',{name:/subscription plans/i})[0]);
  expect(await screen.findByText('v2 · INR 999 · MONTHLY')).toBeTruthy();
  expect(screen.getByText('v1 · INR 799 · MONTHLY')).toBeTruthy();
 });
 it('hides tenant creation and plan mutation forms from view-only platform roles',async()=>{
  vi.mocked(useSession).mockReturnValue({ready:true,account:{...account,permissions:['PLATFORM_TENANT_VIEW','PLATFORM_SUBSCRIPTION_VIEW']}} as ReturnType<typeof useSession>);
  vi.mocked(platformApi).mockImplementation(async(path)=>path.includes('/tenants?')?{items:[],totalElements:0,totalPages:0,page:0}:path.endsWith('/subscription-plans')?[]:{tenantStatuses:[]});
  render(<PlatformApp/>);
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith(expect.stringContaining('/tenants?')));
  expect(screen.queryByText('Create vendor tenant')).toBeNull();
  fireEvent.click(screen.getAllByRole('button',{name:/subscription plans/i})[0]);
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/subscription-plans'));
  expect(screen.queryByText('Save plan version')).toBeNull();
 });

});


it('restores direct platform page fragments and exposes the active page accessibly', async()=>{
 window.history.replaceState({},'','/platform#audit');
 render(<PlatformApp/>);
 const audit=screen.getByRole('button',{name:/platform audit/i});
 expect(audit.getAttribute('aria-current')).toBe('page');
 await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/audit?page=0&size=50'));
 expect(screen.getByRole('navigation',{name:'Breadcrumb'}).querySelector('[aria-current="page"]')?.textContent).toBe('Platform audit');
});

it('updates the platform location and follows browser back navigation without exposing forbidden pages', async()=>{
 window.history.replaceState({},'','/platform');
 render(<PlatformApp/>);
 const overview=screen.getByRole('button',{name:/overview/i});
 await waitFor(()=>expect(overview.getAttribute('aria-current')).toBe('page'));
 fireEvent.click(screen.getAllByRole('button',{name:/tenants/i})[0]);
 expect(window.location.hash).toBe('#tenants');
 expect(screen.getAllByRole('button',{name:/tenants/i})[0].getAttribute('aria-current')).toBe('page');
 fireEvent.click(screen.getAllByRole('button',{name:/subscription plans/i})[0]);
 expect(window.location.hash).toBe('#plans');
 window.history.back();
 await waitFor(()=>expect(window.location.hash).toBe('#tenants'));
 await waitFor(()=>expect(screen.getAllByRole('button',{name:/tenants/i})[0].getAttribute('aria-current')).toBe('page'));
});

it('repairs a direct URL to an inaccessible platform page using only authorized navigation', async()=>{
 vi.mocked(useSession).mockReturnValue({ready:true,account:{...account,permissions:['PLATFORM_ANALYTICS_VIEW']}} as ReturnType<typeof useSession>);
 window.history.replaceState({},'','/platform#identities');
 render(<PlatformApp/>);
 const overview=screen.getByRole('button',{name:/overview/i});
 await waitFor(()=>expect(overview.getAttribute('aria-current')).toBe('page'));
 expect(window.location.hash).toBe('#dashboard');
 expect(screen.queryByRole('button',{name:/platform identities/i})).toBeNull();
});
