import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import PlatformSubscriptions from './PlatformSubscriptions';
import {platformApi} from './api';
import {useSession} from './session';

vi.mock('./api',()=>({platformApi:vi.fn()}));
vi.mock('./session',()=>({useSession:vi.fn()}));

const subscription={id:4,tenantId:12,tenantName:'Vendor Twelve',planId:2,planKey:'starter',planName:'Starter',planVersion:1,status:'ACTIVE',periodStart:'2026-11-01',periodEnd:'2026-12-01',graceUntil:null,createdAt:'2026-11-01',version:0};

beforeEach(()=>{
 vi.clearAllMocks();
 vi.mocked(useSession).mockReturnValue({status:'authenticated',loading:false,refreshing:false,account:{username:'viewer@platform.test',tenantId:null,branchId:null,membershipId:null,permissions:['PLATFORM_SUBSCRIPTION_VIEW'],actorType:'CUSTOMER',availableActorTypes:['CUSTOMER']},error:null} as ReturnType<typeof useSession>);
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
});
