import {cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import PlatformSubscriptions from './PlatformSubscriptions';
import {platformApi} from './api';
import {authenticatedFetch,useSession} from './session';
import {downloadFile} from './fileDownload';

vi.mock('./api',()=>({platformApi:vi.fn()}));
vi.mock('./session',()=>({authenticatedFetch:vi.fn(),useSession:vi.fn()}));
vi.mock('./fileDownload',()=>({downloadFile:vi.fn()}));

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
 it('downloads only mock invoices from the selected subscription',async()=>{
  const blob=new Blob(['%PDF-1.7'],{type:'application/pdf'});
  const response=new Response(null,{headers:{'Content-Type':'application/pdf'}});
  vi.spyOn(response,'blob').mockResolvedValue(blob);
  vi.mocked(authenticatedFetch).mockResolvedValue(response);
  vi.mocked(platformApi).mockImplementation(async(path:string)=>{
   if(path==='/subscriptions')return [subscription];
   if(path.endsWith('/billing-history'))return {invoices:[
    {id:9,invoiceNumber:'MOCK-SUB-4-2026-11-01',currency:'INR',total:'19.50',status:'OPEN'},
    {id:10,invoiceNumber:'SUB-4',currency:'INR',total:'19.50',status:'OPEN'},
   ],payments:[]};
   return [];
  });
  render(<PlatformSubscriptions/>);
  fireEvent.click(await screen.findByRole('button',{name:'Billing and events'}));
  fireEvent.click(await screen.findByRole('button',{name:'Download mock invoice PDF'}));
  await waitFor(()=>expect(downloadFile).toHaveBeenCalledWith({
   blob,filename:'mock-subscription-invoice-9.pdf',
  }));
  expect(authenticatedFetch).toHaveBeenCalledWith('/api/v1/platform/subscriptions/4/invoices/9/document');
 });
 it('matches payment attempts to invoices and filters without mixing retry histories',async()=>{
  const invoice=(id:number,invoiceNumber:string)=>({id,invoiceNumber,currency:'INR',total:'19.50',status:'OPEN'});
  const attempt={invoiceId:9,provider:'MOCK_SUBSCRIPTION',currency:'INR',amount:'19.50',
   createdAt:'2026-11-01T10:00:00Z',confirmedAt:null};
  vi.mocked(platformApi).mockImplementation(async(path:string)=>{
   if(path==='/subscriptions')return [subscription];
   if(path.endsWith('/billing-history'))return {
    invoices:[invoice(9,'MOCK-SUB-4-2026-11-01'),invoice(10,'MOCK-SUB-4-2026-12-01'),invoice(11,'MOCK-SUB-4-2027-01-01')],
    payments:[
     {...attempt,id:1,providerPaymentReference:'attempt-failed',status:'FAILED'},
     {...attempt,id:2,providerPaymentReference:'attempt-paid',status:'SUCCEEDED',confirmedAt:'2026-11-01T10:01:00Z'},
     {...attempt,id:3,invoiceId:10,providerPaymentReference:'other-invoice-attempt',status:'FAILED'},
    ],
   };
   return [];
  });

  render(<PlatformSubscriptions/>);
  fireEvent.click(await screen.findByRole('button',{name:'Billing and events'}));
  const table=await screen.findByRole('table',{name:'Subscription payment attempts'});
  expect(within(table).getAllByRole('row')).toHaveLength(4);
  expect(within(table).getByText('attempt-paid').closest('tr')?.textContent).toContain('MOCK-SUB-4-2026-11-01');
  const filter=screen.getByLabelText('Filter payment attempts by invoice');
  fireEvent.change(filter,{target:{value:'9'}});
  expect(within(table).getByText('attempt-failed')).toBeTruthy();
  expect(within(table).getByText('attempt-paid')).toBeTruthy();
  expect(within(table).queryByText('other-invoice-attempt')).toBeNull();
  fireEvent.change(filter,{target:{value:'11'}});
  expect(screen.getByText('No payment attempts for this invoice.')).toBeTruthy();
 });
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
