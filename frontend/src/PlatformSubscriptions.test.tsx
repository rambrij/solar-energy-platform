import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import PlatformSubscriptions from './PlatformSubscriptions';
import {platformApi} from './api';
import {useSession} from './session';

vi.mock('./api',()=>({platformApi:vi.fn()}));
vi.mock('./session',()=>({useSession:vi.fn()}));

const subscription={id:4,tenantId:12,tenantName:'Vendor Twelve',planId:2,planKey:'starter',planName:'Starter',planVersion:1,status:'ACTIVE',periodStart:'2026-11-01',periodEnd:'2026-12-01',graceUntil:null,createdAt:'2026-11-01',version:0,allowedAdministrativeTransitions:['PAST_DUE','SUSPENDED']};

beforeEach(()=>{
 vi.clearAllMocks();
 vi.mocked(useSession).mockReturnValue({status:'authenticated',loading:false,refreshing:false,account:{username:'viewer@platform.test',tenantId:null,branchId:null,membershipId:null,permissions:['PLATFORM_SUBSCRIPTION_VIEW'],actorType:'PLATFORM',availableActorTypes:['PLATFORM']},error:null} as ReturnType<typeof useSession>);
 vi.mocked(platformApi).mockImplementation(async(path:string)=>{
  if(path==='/subscriptions')return [subscription];
  if(path==='/subscription-plans')return [];
  if(path.endsWith('/billing-history'))return {invoices:[],payments:[]};
  if(path.endsWith('/events'))return [];
  return {};
 });

});
afterEach(()=>{cleanup();vi.restoreAllMocks();});

describe('PlatformSubscriptions permission boundaries',()=>{
 it('allows subscription reads without exposing subscription mutations',async()=>{
  render(<PlatformSubscriptions/>);
  expect(await screen.findByText('Tenant subscriptions')).toBeTruthy();
  expect(screen.getByText('Vendor Twelve · #12')).toBeTruthy();
  expect(screen.queryByText('Assign a plan to a tenant')).toBeNull();
  fireEvent.click(screen.getByRole('button',{name:'Billing and events'}));
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/subscriptions/4/billing-history'));
  expect(screen.queryByRole('button',{name:'Apply status transition'})).toBeNull();
  expect(screen.queryByLabelText('Permitted administrative transition')).toBeNull();
 });
 it('uses server transitions and the current version, then reloads billing and events',async()=>{
  const session=vi.mocked(useSession)();
  vi.mocked(useSession).mockReturnValue({...session,account:{...session.account!,permissions:['PLATFORM_SUBSCRIPTION_VIEW','PLATFORM_SUBSCRIPTION_ADJUST']}});
  render(<PlatformSubscriptions/>);
  fireEvent.click(await screen.findByRole('button',{name:'Billing and events'}));
  const choices=screen.getByLabelText('Permitted administrative transition') as HTMLSelectElement;
  expect(Array.from(choices.options,option=>option.value)).toEqual(['','PAST_DUE','SUSPENDED']);
  fireEvent.change(choices,{target:{value:'SUSPENDED'}});
  fireEvent.change(screen.getByLabelText('Transition reason'),{target:{value:'  Account review  '}});
  fireEvent.click(screen.getByRole('button',{name:'Apply status transition'}));
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/subscriptions/4/status','POST',{
   status:'SUSPENDED',reason:'Account review',expectedVersion:0,
  }));
  await waitFor(()=>expect(vi.mocked(platformApi).mock.calls.filter(([path])=>path==='/subscriptions/4/events')).toHaveLength(2));
  expect(vi.mocked(platformApi).mock.calls.filter(([path])=>path==='/subscriptions/4/billing-history')).toHaveLength(2);
 });
 it('requests the selected plan version and lets the server calculate the end date',async()=>{
  const session=vi.mocked(useSession)();
  vi.mocked(useSession).mockReturnValue({...session,account:{...session.account!,permissions:['PLATFORM_SUBSCRIPTION_VIEW','PLATFORM_SUBSCRIPTION_ADJUST']}});
  vi.mocked(platformApi).mockImplementation(async(path)=>path==='/subscription-plans'?
   [{id:2,displayName:'Starter',active:true,currentVersion:3,versions:[{version:3,billingInterval:'QUARTERLY'}]}]:[]);
  render(<PlatformSubscriptions/>);
  await screen.findByRole('option',{name:'Starter · v3'});
  fireEvent.change(screen.getByLabelText('Tenant ID'),{target:{value:'12'}});
  fireEvent.change(screen.getByLabelText('Plan'),{target:{value:'2'}});
  fireEvent.change(screen.getByLabelText('Billing period start'),{target:{value:'2028-01-31'}});
  expect(screen.getByRole('option',{name:'Use plan interval (quarterly)'})).toBeTruthy();
  expect(screen.queryByLabelText('Billing period end')).toBeNull();
  fireEvent.click(screen.getByRole('button',{name:'Create pending subscription'}));
  await waitFor(()=>expect(platformApi).toHaveBeenCalledWith('/tenants/12/subscription','POST',{
   planId:2,expectedPlanVersion:3,periodStart:'2028-01-31',usePlanInterval:true,reason:'Initial plan assignment',
  }));
 });
});
