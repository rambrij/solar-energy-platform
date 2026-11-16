import {fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import PlatformApp from './PlatformApp';
import {useSession} from './session';
import {platformApi} from './api';

vi.mock('./api',()=>({platformApi:vi.fn()}));
vi.mock('./session',()=>({useSession:vi.fn(),signOut:vi.fn()}));

const account={username:'admin@platform.test',tenantId:null,branchId:null,permissions:['PLATFORM_TENANT_CREATE','PLATFORM_TENANT_UPDATE','PLATFORM_TENANT_SUSPEND','PLATFORM_TENANT_CLOSE','PLATFORM_PLAN_MANAGE','PLATFORM_SUBSCRIPTION_VIEW','PLATFORM_SUBSCRIPTION_ADJUST','PLATFORM_ANALYTICS_VIEW','PLATFORM_AUDIT_VIEW','PLATFORM_SUPPORT_ACCESS']};
beforeEach(()=>{vi.mocked(useSession).mockReturnValue({ready:true,account} as ReturnType<typeof useSession>);vi.clearAllMocks();vi.mocked(platformApi).mockResolvedValue({tenantStatuses:[]});});
afterEach(()=>vi.restoreAllMocks());

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
  fireEvent.click(screen.getByRole('button',{name:/tenants/i}));
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
  render(<PlatformApp/>);fireEvent.click(screen.getByRole('button',{name:/subscription plans/i}));
  expect(await screen.findByText('v2 · INR 999 · MONTHLY')).toBeTruthy();
  expect(screen.getByText('v1 · INR 799 · MONTHLY')).toBeTruthy();
 });
});
