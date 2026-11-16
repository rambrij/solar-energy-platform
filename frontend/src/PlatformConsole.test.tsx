import {cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import PlatformConsole from './PlatformConsole';
import {platformApi} from './api';
import {useSession} from './session';
vi.mock('./api',async importOriginal=>({...await importOriginal<typeof import('./api')>(),platformApi:vi.fn()}));
vi.mock('./session',async importOriginal=>({...await importOriginal<typeof import('./session')>(),useSession:vi.fn()}));
afterEach(cleanup);beforeEach(()=>{vi.mocked(platformApi).mockReset();window.history.replaceState({},'','/platform');});
const signedIn=(permissions:string[])=>{vi.mocked(useSession).mockReturnValue({ready:true,account:{username:'pa-root',tenantId:null,branchId:null,permissions}});
 vi.mocked(platformApi).mockImplementation(async path=>{
  if(path.startsWith('/tenants?'))return {items:[{id:7,slug:'pa',name:'Pa Store',legalName:'L',contactEmail:'o@example.com',status:'PENDING'}],total:1};
  if(path.startsWith('/audit'))return {items:[],total:0};
  if(path==='/overview')return {tenantsByStatus:{PENDING:1},subscriptionsByStatus:{},subscriptionsByPlan:{},openInvoices:[],generatedAt:'2026-11-15T00:00:00Z'};
  if(path==='/health')return {status:'UP',environment:'non-production',databaseReachable:true,paymentProviderConfigured:true,merchantConfigured:true,mockVendorPaymentsEnabled:false,mockSubscriptionProvider:false,issues:[],checkedAt:'2026-11-15T00:00:00Z'};
  if(path==='/plans'||path==='/configuration'||path.endsWith('/support-access'))return [];
  return {current:null,history:[],invoices:[]};});};
it('shows platform navigation only, with no vendor or POS entry points',async()=>{
 signedIn(['PLATFORM_TENANT_CREATE','PLATFORM_ANALYTICS_VIEW']);render(<PlatformConsole/>);await screen.findByText('Tenants by status');
 const main=screen.getByRole('navigation',{name:'Main navigation'});expect(within(main).getByText('Platform console')).toBeTruthy();expect(within(main).queryByText('Store POS')).toBeNull();expect(within(main).queryByText('Marketplace')).toBeNull();
 const side=screen.getByRole('navigation',{name:'Workspace sections'});expect(within(side).getByText('Tenant directory')).toBeTruthy();expect(within(side).queryByText('Current bill')).toBeNull();expect(within(side).queryByText('Product catalogue')).toBeNull();
 expect(screen.getByText('Platform operations')).toBeTruthy();expect(screen.getByRole('main').className).toContain('platform-workspace');
});
it('lets a selected tenant flow into its lifecycle, subscription and support panels',async()=>{
 signedIn(['PLATFORM_TENANT_UPDATE','PLATFORM_SUBSCRIPTION_VIEW','PLATFORM_SUPPORT_ACCESS']);render(<PlatformConsole/>);await screen.findByText('Pa Store');
 expect(screen.getByText('Select a tenant in the directory to manage its subscription.')).toBeTruthy();expect(screen.getByText('Select a tenant in the directory first.')).toBeTruthy();
 fireEvent.click(screen.getByLabelText('Manage Pa Store'));await screen.findByText('Initial Vendor Admin');
 await waitFor(()=>expect(vi.mocked(platformApi)).toHaveBeenCalledWith('/tenants/7/subscriptions'));await waitFor(()=>expect(vi.mocked(platformApi)).toHaveBeenCalledWith('/tenants/7/support-access'));
 expect((screen.getByText('Provision Vendor Admin') as HTMLButtonElement).disabled).toBe(true);
});
it('reflects permissions in the UI while the server stays authoritative',async()=>{
 signedIn(['PLATFORM_AUDIT_VIEW']);render(<PlatformConsole/>);await screen.findByText('Your role cannot create tenants.');
 expect(screen.getAllByText('Your role does not include platform analytics.').length).toBeGreaterThan(0);expect(screen.getByText('Your role does not include support access.')).toBeTruthy();expect(screen.queryByText('Create plan')).toBeNull();
 await waitFor(()=>expect(vi.mocked(platformApi).mock.calls.some(c=>String(c[0]).startsWith('/audit'))).toBe(true));expect(vi.mocked(platformApi).mock.calls.some(c=>c[0]==='/overview')).toBe(false);
});
