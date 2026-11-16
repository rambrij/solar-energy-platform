import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import PlatformDashboard from './PlatformDashboard';
import {platformApi,ApiError} from './api';
vi.mock('./api',async importOriginal=>({...await importOriginal<typeof import('./api')>(),platformApi:vi.fn()}));
afterEach(cleanup);beforeEach(()=>vi.mocked(platformApi).mockReset());
const overview={tenantsByStatus:{PENDING:2,ACTIVE:5},subscriptionsByStatus:{ACTIVE:4},subscriptionsByPlan:{basic:4},openInvoices:[{currency:'INR',count:2,total:2360}],generatedAt:'2026-11-15T00:00:00Z'};
const health=(over={})=>({status:'DEGRADED',environment:'non-production',databaseReachable:true,paymentProviderConfigured:false,merchantConfigured:false,mockVendorPaymentsEnabled:true,mockSubscriptionProvider:false,issues:['Platform merchant account is not configured'],checkedAt:'2026-11-15T00:00:00Z',...over});
it('shows aggregate counts, open invoices and health issues',async()=>{
 vi.mocked(platformApi).mockImplementation(async path=>path==='/overview'?overview:health({issues:['CRITICAL: mock vendor payments are enabled in production'],status:'DOWN'}));
 render(<PlatformDashboard can={()=>true}/>);await screen.findByText('Tenants by status');
 expect(screen.getByText('PENDING').nextSibling?.textContent).toBe('2');expect(screen.getByText(/2 open · INR 2360.00/)).toBeTruthy();
 expect((await screen.findByText('DOWN')).className).toContain('platform-health-down');expect(screen.getByText(/CRITICAL: mock vendor payments/).className).toBe('platform-critical');expect(screen.getByText('Database reachable')).toBeTruthy();
});
it('does not call the API without analytics permission',()=>{
 render(<PlatformDashboard can={()=>false}/>);expect(screen.getAllByText('Your role does not include platform analytics.')).toHaveLength(2);expect(vi.mocked(platformApi)).not.toHaveBeenCalled();
});
it('reports a failed load and can refresh',async()=>{
 vi.mocked(platformApi).mockRejectedValueOnce(new ApiError(500));render(<PlatformDashboard can={()=>true}/>);await screen.findByText('Could not load the dashboard (500).');
 vi.mocked(platformApi).mockImplementation(async path=>path==='/overview'?overview:health());fireEvent.click(screen.getByText('Refresh'));await waitFor(()=>expect(screen.queryByText('Could not load the dashboard (500).')).toBeNull());await screen.findByText('Subscriptions by plan');
});
