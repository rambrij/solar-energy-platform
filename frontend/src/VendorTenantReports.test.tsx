import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import VendorTenantReports from './VendorTenantReports';
import {vendorApi} from './api';
import {usePermissions} from './usePermissions';

vi.mock('./api',()=>({vendorApi:vi.fn()}));
vi.mock('./usePermissions',()=>({usePermissions:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});

const permissionState=(hasPermission:(permission:string)=>boolean):ReturnType<typeof usePermissions>=>({
 permissions:null,
 loading:false,
 error:null,
 hasPermission,
 hasAnyPermission:(...permissions)=>permissions.some(hasPermission),
 hasAllPermissions:(...permissions)=>permissions.every(hasPermission),
 isPlatformUser:false,
 isCustomer:false,
 isVendorEmployee:true
});

const overview={
 from:'2026-11-28',to:'2026-11-28',generatedAt:'2026-11-28T01:00:00Z',
 totals:{paidOrders:3,sales:1500,tax:250,beforeTax:1250,cashReceived:300,changeGiven:0,cashApplied:300,units:5,invoices:3,distinctCustomers:2,unpaidOrders:0,unpaidAmount:0,cancelledOrders:0,expiredOrders:0,discounts:20},
 channels:[{channel:'ONLINE',totals:{paidOrders:2,sales:1200,tax:200,beforeTax:1000,cashReceived:0,changeGiven:0,cashApplied:0,units:4,invoices:2,distinctCustomers:0,unpaidOrders:0,unpaidAmount:0,cancelledOrders:0,expiredOrders:0,discounts:20}},{channel:'IN_STORE',totals:{paidOrders:1,sales:300,tax:50,beforeTax:250,cashReceived:300,changeGiven:0,cashApplied:300,units:1,invoices:1,distinctCustomers:0,unpaidOrders:0,unpaidAmount:0,cancelledOrders:0,expiredOrders:0,discounts:0}}],
 days:[{date:'2026-11-28',totals:{paidOrders:3,sales:1500,tax:250,beforeTax:1250,cashReceived:300,changeGiven:0,cashApplied:300,units:5,invoices:3,distinctCustomers:0,unpaidOrders:0,unpaidAmount:0,cancelledOrders:0,expiredOrders:0,discounts:20}}],
 outlets:[{outlet:'Outlet A',channel:'IN_STORE',orders:1,paidOrders:1,paidSales:300,unpaidOrders:0,unpaidAmount:0}],
 topProducts:[{productId:7,productName:'Solar Panel',units:5,sales:1500,discounts:20}],
 confirmedTenders:[{channel:'ONLINE',method:'UPI',origin:'ONLINE_MOCK',tenders:2,confirmedAmount:1200}]
};
const refunds={from:'2026-11-28',to:'2026-11-28',generatedAt:'2026-11-28T01:00:00Z',totals:{refunds:1,units:1,cash:50,beforeTax:45,tax:5},days:[{date:'2026-11-28',totals:{refunds:1,units:1,cash:50,beforeTax:45,tax:5}}]};
function setDates(){fireEvent.change(screen.getByLabelText('Activity from (UTC)'),{target:{value:'2026-11-28'}});fireEvent.change(screen.getByLabelText('Activity to (UTC)'),{target:{value:'2026-11-28'}});}
it('loads tenant-wide metrics and separate refund data for users with refund permission',async()=>{
 vi.mocked(usePermissions).mockReturnValue(permissionState(()=>true));
 vi.mocked(vendorApi).mockImplementation(async path=>path.startsWith('/reports/refunds')?refunds:overview);
 render(<VendorTenantReports/>);setDates();fireEvent.click(screen.getByText('Load vendor reports'));
 await waitFor(()=>expect(vendorApi).toHaveBeenCalledTimes(2));
 expect(vendorApi).toHaveBeenNthCalledWith(1,'/reports/overview?from=2026-11-28&to=2026-11-28');
 expect(vendorApi).toHaveBeenNthCalledWith(2,'/reports/refunds?from=2026-11-28&to=2026-11-28');
 expect(await screen.findByText('Solar Panel')).toBeTruthy();
 expect(screen.getByText('Outlet A')).toBeTruthy();
 expect(screen.getByText(/Paid POS cash refunds/)).toBeTruthy();
});
it('does not request refund data when the actor lacks POS_REFUND_VIEW',async()=>{
 vi.mocked(usePermissions).mockReturnValue(permissionState(p=>p!=='POS_REFUND_VIEW'));
 vi.mocked(vendorApi).mockResolvedValue(overview);
 render(<VendorTenantReports/>);setDates();fireEvent.click(screen.getByText('Load vendor reports'));
 await waitFor(()=>expect(vendorApi).toHaveBeenCalledTimes(1));
 expect(vendorApi).toHaveBeenCalledWith('/reports/overview?from=2026-11-28&to=2026-11-28');
 expect(screen.queryByText(/Paid POS cash refunds/)).toBeNull();
});
it('rejects reversed and overlong date ranges without an API call',()=>{
 vi.mocked(usePermissions).mockReturnValue(permissionState(()=>false));
 render(<VendorTenantReports/>);
 fireEvent.change(screen.getByLabelText('Activity from (UTC)'),{target:{value:'2026-11-29'}});
 fireEvent.change(screen.getByLabelText('Activity to (UTC)'),{target:{value:'2026-11-28'}});
 fireEvent.click(screen.getByText('Load vendor reports'));
 expect(screen.getByRole('alert').textContent).toContain('inclusive UTC date range');
 expect(vendorApi).not.toHaveBeenCalled();
});
