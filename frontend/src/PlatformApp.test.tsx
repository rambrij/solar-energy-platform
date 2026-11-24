import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import PlatformApp from './PlatformApp';
import {useSession} from './session';
import {platformApi} from './api';

vi.mock('./api',()=>({platformApi:vi.fn()}));
vi.mock('./session',()=>({useSession:vi.fn(),signOut:vi.fn()}));

const account={username:'admin@platform.test',tenantId:null,branchId:null,membershipId:null,availableActorTypes:['PLATFORM'],actorType:'PLATFORM' as const,permissions:['PLATFORM_TENANT_VIEW','PLATFORM_TENANT_CREATE','PLATFORM_TENANT_UPDATE','PLATFORM_TENANT_SUSPEND','PLATFORM_TENANT_CLOSE','PLATFORM_PLAN_MANAGE','PLATFORM_SUBSCRIPTION_VIEW','PLATFORM_SUBSCRIPTION_ADJUST','PLATFORM_ANALYTICS_VIEW','PLATFORM_AUDIT_VIEW','PLATFORM_SUPPORT_ACCESS','PLATFORM_CONFIG_MANAGE']};
beforeEach(()=>{
 vi.clearAllMocks();
 vi.mocked(useSession).mockReturnValue({status:'authenticated',loading:false,refreshing:false,account,error:null} as ReturnType<typeof useSession>);
 vi.mocked(platformApi).mockImplementation(async(path)=>path==='/subscription-plans'?[]:path.startsWith('/audit')?{items:[],totalElements:0,totalPages:0,page:0}:path.startsWith('/tenants?')?{items:[],totalElements:0,totalPages:0,page:0}:path==='/support-access'?[]:path==='/configuration'?[]:{tenantStatuses:[]});
});
afterEach(()=>{cleanup();vi.restoreAllMocks();window.history.replaceState({},'','/platform');});

describe('PlatformApp',()=>{
 it('renders a platform-only control plane',async()=>{
  render(<PlatformApp/>);
  expect(screen.getByText('Platform Super Admin')).toBeTruthy();
  expect(screen.queryByText('Store POS')).toBeNull();
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/dashboard'));
 });
 it('shows aggregated subscription statuses on the overview',async()=>{
  vi.mocked(platformApi).mockResolvedValue({tenantStatuses:[],subscriptionStatuses:[{status:'ACTIVE',count:3},{status:'GRACE_PERIOD',count:2}]});
  render(<PlatformApp/>);
  const metrics=await screen.findByRole('region',{name:'Subscriptions by status'});
  expect(metrics.textContent).toContain('ACTIVE3');
  expect(metrics.textContent).toContain('GRACE PERIOD2');
 });
 it('filters platform audit events and browses older pages with request metadata',async()=>{
  const entry={id:7,actor:account.username,action:'TENANT_UPDATED',resourceType:'TENANT',
   resourceId:'12',tenantId:12,reason:'Profile reviewed',outcome:'SUCCESS',
   correlationId:'request-123',occurredAt:'2026-11-01T12:00:00Z'};
  vi.mocked(platformApi).mockImplementation(async(path)=>{
   const params=new URLSearchParams(path.split('?')[1]);
   const page=Number(params.get('page'));
   return {items:params.has('tenantId')?[entry]:[],page,totalPages:2,totalElements:51};
  });
  window.history.replaceState({},'','/platform#audit');
  render(<PlatformApp/>);
  await screen.findByText('No audit events on this page.');
  fireEvent.click(screen.getByRole('button',{name:'Next page'}));
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/audit?page=1&size=50'));
  fireEvent.change(screen.getByLabelText('Tenant ID'),{target:{value:'12'}});
  fireEvent.click(screen.getByRole('button',{name:'Filter audit events'}));
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/audit?page=0&size=50&tenantId=12'));
  expect(await screen.findByText('request-123')).toBeTruthy();
  expect(screen.getByText('SUCCESS')).toBeTruthy();
  fireEvent.click(screen.getByRole('button',{name:'Next page'}));
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/audit?page=1&size=50&tenantId=12'));
  await screen.findByText('Page 2 of 2');
  fireEvent.click(screen.getByRole('button',{name:'Clear filter'}));
  await screen.findByText('No audit events on this page.');
  expect(platformApi).toHaveBeenLastCalledWith('/audit?page=0&size=50');
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
  fireEvent.click(screen.getByLabelText('Record confirmed vendor terms acceptance'));
  fireEvent.click(screen.getByRole('button',{name:'Create vendor tenant'}));
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/tenants','POST',expect.objectContaining({slug:'demo-vendor',initialVendorAdminUsername:'admin@demo.test',termsAccepted:true})));
  expect(await screen.findByText('one-time-invitation-token')).toBeTruthy();
 });
 it('opens an existing vendor profile and records terms via the authorized update endpoint',async()=>{
  vi.mocked(platformApi).mockImplementation(async(path)=>path.startsWith('/tenants?')?
   {items:[{id:12,slug:'solar',name:'Solar',legalName:'Solar Ltd',lifecycleStatus:'UNDER_REVIEW'}],totalElements:1,totalPages:1,page:0}:
   {tenantStatuses:[]});
  render(<PlatformApp/>);
  fireEvent.click(screen.getByRole('button',{name:/^Tenants/}));
  fireEvent.click(await screen.findByRole('button',{name:'Edit profile'}));
  expect(screen.getByRole('heading',{name:'Edit vendor profile'})).toBeTruthy();
  fireEvent.change(screen.getByLabelText('Contact phone'),{target:{value:'5550100'}});
  fireEvent.click(screen.getByLabelText('Record confirmed vendor terms acceptance'));
  fireEvent.click(screen.getByRole('button',{name:'Save vendor profile'}));
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/tenants/12','PATCH',
   expect.objectContaining({contactPhone:'5550100',termsAccepted:true})));
 });
 it('renders historic plan versions as separate records',async()=>{
  vi.mocked(platformApi).mockImplementation(async(path)=>path==='/subscription-plans'?[{id:1,planKey:'starter',displayName:'Starter',active:true,currentVersion:2,versions:[{version:2,currency:'INR',price:'999',billingInterval:'MONTHLY'},{version:1,currency:'INR',price:'799',billingInterval:'MONTHLY'}]}]:{tenantStatuses:[]});
  render(<PlatformApp/>);
  fireEvent.click(screen.getAllByRole('button',{name:/subscription plans/i})[0]);
  expect(await screen.findByText('v2 · INR 999 · MONTHLY')).toBeTruthy();
  expect(screen.getByText('v1 · INR 799 · MONTHLY')).toBeTruthy();
 });
 it('prefills the current plan and submits a new version to the revision endpoint',async()=>{
  const current={version:2,price:'999',currency:'INR',billingInterval:'MONTHLY',maxUsers:5,maxStorageMb:1024,maxBranches:1,maxWarehouses:0,entitlements:'{"features":{"POS":true}}',trialEligible:true,graceDays:7,taxMetadata:'{}',invoiceDescription:'Starter subscription'};
  vi.mocked(platformApi).mockImplementation(async(path)=>path==='/subscription-plans'?
   [{id:1,planKey:'starter',displayName:'Starter',active:true,currentVersion:2,versions:[current]}]:{tenantStatuses:[]});
  render(<PlatformApp/>);
  fireEvent.click(screen.getByRole('button',{name:/subscription plans/i}));
  fireEvent.click(await screen.findByRole('button',{name:'Revise plan'}));
  expect(screen.getByRole('heading',{name:'Revise subscription plan'})).toBeTruthy();
  const trial=screen.getByRole('checkbox',{name:'Eligible for trial'}) as HTMLInputElement;
  expect(trial.checked).toBe(true);
  fireEvent.click(trial);
  fireEvent.change(screen.getByLabelText('Price'),{target:{value:'1099'}});
  fireEvent.click(screen.getByRole('button',{name:'Save plan version'}));
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/subscription-plans/1/versions','PUT',
   expect.objectContaining({planKey:'starter',price:1099,trialEligible:false,entitlements:current.entitlements})));
 });
 it('requires a reason and revokes the selected active support grant',async()=>{
  vi.mocked(platformApi).mockImplementation(async(path)=>path==='/support-access'?
   [{id:7,tenantId:1,actor:account.username,purpose:'Investigation',ticketReference:'T-7',expiresAt:'2026-12-01T12:00:00Z',active:true}]:{tenantStatuses:[]});
  render(<PlatformApp/>);
  fireEvent.click(screen.getByRole('button',{name:/support access/i}));
  fireEvent.click(await screen.findByRole('button',{name:'Revoke'}));
  fireEvent.change(screen.getByLabelText('Revocation reason'),{target:{value:'  Investigation complete  '}});
  fireEvent.click(screen.getByRole('button',{name:'Revoke support grant'}));
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/support-access/7/revoke','POST',{reason:'Investigation complete'}));
  expect(await screen.findByText('Support grant revoked.')).toBeTruthy();
 });
 it('hides tenant creation and plan mutation forms from view-only platform roles',async()=>{
  vi.mocked(useSession).mockReturnValue({status:'authenticated',loading:false,refreshing:false,account:{...account,permissions:['PLATFORM_TENANT_VIEW','PLATFORM_SUBSCRIPTION_VIEW']},error:null} as ReturnType<typeof useSession>);
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
 vi.mocked(useSession).mockReturnValue({status:'authenticated',loading:false,refreshing:false,account:{...account,permissions:['PLATFORM_ANALYTICS_VIEW']},error:null} as ReturnType<typeof useSession>);
 window.history.replaceState({},'','/platform#identities');
 render(<PlatformApp/>);
 const overview=screen.getByRole('button',{name:/overview/i});
 await waitFor(()=>expect(overview.getAttribute('aria-current')).toBe('page'));
 expect(window.location.hash).toBe('#dashboard');
 expect(screen.queryByRole('button',{name:/platform identities/i})).toBeNull();
});
