import {cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {PlatformPlans,TenantSubscription} from './PlatformBilling';
import {platformApi,ApiError} from './api';
import type {Tenant} from './PlatformTenants';
vi.mock('./api',async importOriginal=>({...await importOriginal<typeof import('./api')>(),platformApi:vi.fn()}));
afterEach(cleanup);beforeEach(()=>vi.mocked(platformApi).mockReset());
const tenant:Tenant={id:7,slug:'pa',name:'Pa Store',legalName:'L',contactEmail:'o@example.com',status:'ACTIVE'};
const plan={code:'basic',name:'Basic',active:true,versions:[{id:'v2',version:2,interval:'MONTHLY',price:2000,currency:'INR',taxRate:18,taxInclusive:false,graceDays:5,features:[]},{id:'v1',version:1,interval:'MONTHLY',price:1000,currency:'INR',taxRate:18,taxInclusive:false,graceDays:5,features:[]}]};
const fill=(label:RegExp|string,value:string)=>fireEvent.change(screen.getByLabelText(label),{target:{value}});
it('lists versions newest first and adds a version or retires a plan',async()=>{
 vi.mocked(platformApi).mockImplementation(async(path,options)=>path==='/plans'&&!options?[plan]:{});
 render(<PlatformPlans can={()=>true}/>);await screen.findByText(/v2 · MONTHLY · INR 2000.00 \+ 18% tax/);expect(screen.getByText(/v1 · MONTHLY · INR 1000.00/)).toBeTruthy();
 fill('New version price for basic','2500');fireEvent.click(screen.getByText('Add version'));await screen.findByText('Version added to basic.');
 const call=vi.mocked(platformApi).mock.calls.find(c=>c[0]==='/plans/basic/versions')!;expect((call[1]!.body as {price:string}).price).toBe('2500');
 fireEvent.click(screen.getByText('Retire plan'));await screen.findByText('basic retired.');expect(vi.mocked(platformApi)).toHaveBeenCalledWith('/plans/basic/active',{body:{active:false}});
});
it('validates a new plan before calling the server and hides management without permission',async()=>{
 vi.mocked(platformApi).mockResolvedValue([]);const view=render(<PlatformPlans can={()=>true}/>);
 fill('Code','Bad Code');fill('Price','10');fireEvent.click(screen.getByText('Create plan'));expect(screen.getByRole('alert').textContent).toContain('Plan code');
 fill('Code','pro-plan');fill('Price','abc');fireEvent.click(screen.getByText('Create plan'));expect(screen.getByRole('alert').textContent).toContain('price');
 fill('Name','Pro');fill('Price','1500.50');fireEvent.click(screen.getByText('Create plan'));await screen.findByText('Plan pro-plan created.');
 const body=vi.mocked(platformApi).mock.calls.find(c=>c[0]==='/plans'&&c[1])![1]!.body as {code:string;terms:{price:string;currency:string}};expect(body.code).toBe('pro-plan');expect(body.terms.price).toBe('1500.50');expect(body.terms.currency).toBe('INR');
 view.unmount();vi.mocked(platformApi).mockClear();render(<PlatformPlans can={p=>p==='PLATFORM_SUBSCRIPTION_VIEW'}/>);expect(screen.queryByText('Create plan')).toBeNull();
});
it('shows a plan-service error',async()=>{
 vi.mocked(platformApi).mockImplementation(async(path,options)=>{if(options)throw new ApiError(409,undefined,'Plan code already exists');return [];});
 render(<PlatformPlans can={()=>true}/>);fill('Code','dup-plan');fill('Name','D');fill('Price','1');fireEvent.click(screen.getByText('Create plan'));await screen.findByText('Plan code already exists');
});
const subscription=(over={})=>({id:'s1',planCode:'basic',planVersion:1,price:1000,currency:'INR',interval:'MONTHLY',status:'PENDING',periodStart:null,periodEnd:null,...over});
const invoice={id:'i1',number:'SUB-ABC',total:1180,currency:'INR',status:'OPEN',dueAt:'2026-12-01T00:00:00Z',periodStart:'2026-11-15T00:00:00Z',payments:[]};
it('shows the snapshotted terms, only valid state moves, and requires a reason',async()=>{
 vi.mocked(platformApi).mockImplementation(async(path,options)=>{if(options)return {};if(path==='/plans')return [plan];return {current:subscription(),history:[],invoices:[invoice]};});
 render(<TenantSubscription tenant={tenant} can={()=>true}/>);await screen.findByText(/basic v1 · INR 1000.00 \/ monthly/);
 expect(screen.getByRole('button',{name:'ACTIVE'})).toBeTruthy();expect(screen.getByRole('button',{name:'PAST DUE'})).toBeTruthy();expect(screen.queryByRole('button',{name:'SUSPENDED'})).toBeNull();expect((screen.getByRole('button',{name:'ACTIVE'}) as HTMLButtonElement).disabled).toBe(true);
 fill('Reason','Approved start');fireEvent.click(screen.getByRole('button',{name:'ACTIVE'}));await screen.findByText('Subscription is now ACTIVE.');expect(vi.mocked(platformApi)).toHaveBeenCalledWith('/tenants/7/subscriptions/status',{body:{status:'ACTIVE',reason:'Approved start'}});
});
it('assigns a plan, issues an invoice and records a payment against the platform merchant',async()=>{
 vi.mocked(platformApi).mockImplementation(async(path,options)=>{if(options)return {};if(path==='/plans')return [plan];return {current:subscription({status:'ACTIVE'}),history:[],invoices:[{...invoice,payments:[{id:'p0',status:'FAILED',method:'GATEWAY',merchantId:'acct_platform_1'}]}]};});
 render(<TenantSubscription tenant={tenant} can={()=>true}/>);await screen.findByText('SUB-ABC');expect(screen.getByText('GATEWAY failed → acct_platform_1')).toBeTruthy();
 fill('Assign plan','basic');fireEvent.click(screen.getByText('Assign latest version'));await screen.findByText('Plan basic assigned.');
 fireEvent.click(screen.getByText('Issue invoice for current period'));await screen.findByText('Invoice issued for the current period.');
 fill('Reference for SUB-ABC','UTR-1');fireEvent.click(screen.getByLabelText('Record payment for SUB-ABC'));await screen.findByText('Payment recorded for SUB-ABC.');
 expect(vi.mocked(platformApi)).toHaveBeenCalledWith('/subscription-invoices/i1/payments',{body:{amount:1180,method:'OFFLINE',reference:'UTR-1',succeeded:true}});
});
it('is read-only without adjust permission and prompts for a tenant',async()=>{
 const view=render(<TenantSubscription can={()=>true}/>);expect(screen.getByText(/Select a tenant/)).toBeTruthy();view.unmount();
 vi.mocked(platformApi).mockImplementation(async path=>path==='/plans'?[plan]:{current:subscription({status:'ACTIVE'}),history:[],invoices:[invoice]});
 render(<TenantSubscription tenant={tenant} can={p=>p==='PLATFORM_SUBSCRIPTION_VIEW'}/>);await screen.findByText('SUB-ABC');expect(screen.queryByText('Issue invoice for current period')).toBeNull();expect(screen.queryByLabelText('Record payment for SUB-ABC')).toBeNull();expect(screen.queryByText('Assign latest version')).toBeNull();
});
it('shows a server rejection on state change',async()=>{
 vi.mocked(platformApi).mockImplementation(async(path,options)=>{if(options)throw new ApiError(409,undefined,'Cannot move a subscription from PENDING to ACTIVE');return path==='/plans'?[]:{current:subscription(),history:[],invoices:[]};});
 render(<TenantSubscription tenant={tenant} can={()=>true}/>);await screen.findByText(/basic v1/);fill('Reason','x');fireEvent.click(screen.getByRole('button',{name:'ACTIVE'}));await screen.findByText(/Cannot move a subscription/);
});
