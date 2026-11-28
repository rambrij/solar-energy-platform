import {cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import VendorInvoiceRegister from './VendorInvoiceRegister';
import {vendorApi} from './api';
vi.mock('./api',()=>({vendorApi:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
const register={
 items:[{orderId:'order-1',invoiceNumber:'INV-1',sellerName:'Vendor A',outletName:'Online',total:110,
  issuedAt:'2026-11-28T00:00:00Z',channel:'ONLINE',paymentMethod:'MOCK_UPI',paymentReference:'pay-1',confirmedPayments:110}],
 totals:{invoiceCount:1,invoicedAmount:110,confirmedPayments:110,onlineInvoices:1,inStoreInvoices:0},
 from:'2026-11-01',to:'2026-11-28',channel:'ONLINE',page:0,size:50,totalElements:1,totalPages:1,generatedAt:'2026-11-28T01:00:00Z'
};
function dates(from='2026-11-01',to='2026-11-28'){
 fireEvent.change(screen.getByLabelText('Invoices from (UTC)'),{target:{value:from}});
 fireEvent.change(screen.getByLabelText('Invoices to (UTC)'),{target:{value:to}});
}
it('does not query finance data until the user loads the register',()=>{
 render(<VendorInvoiceRegister/>);expect(vendorApi).not.toHaveBeenCalled();
});
it('loads tenant invoice data with date and channel filters and displays confirmed payments',async()=>{
 vi.mocked(vendorApi).mockResolvedValue(register);
 render(<VendorInvoiceRegister/>);dates();
 fireEvent.change(screen.getByLabelText('Sales channel'),{target:{value:'ONLINE'}});
 fireEvent.click(screen.getByText('Load invoice register'));
 await waitFor(()=>expect(vendorApi).toHaveBeenCalledWith('/invoices?from=2026-11-01&to=2026-11-28&channel=ONLINE&page=0&size=50'));
 expect(await screen.findByText('INV-1')).toBeTruthy();
 expect(screen.getAllByText('110.00')).toHaveLength(4);
 expect(screen.getByText('MOCK_UPI')).toBeTruthy();
 expect(screen.getByText(/not a statutory tax filing/)).toBeTruthy();
});
it('rejects reversed and oversized ranges without a network request',()=>{
 render(<VendorInvoiceRegister/>);dates('2026-11-29','2026-11-28');
 fireEvent.click(screen.getByText('Load invoice register'));
 expect(screen.getByRole('alert').textContent).toContain('inclusive UTC date range');
 expect(vendorApi).not.toHaveBeenCalled();
});
it('reports denied/unavailable access without displaying stale data as current',async()=>{
 vi.mocked(vendorApi).mockRejectedValue(new Error('Forbidden'));
 render(<VendorInvoiceRegister/>);dates();fireEvent.click(screen.getByText('Load invoice register'));
 expect(await screen.findByRole('alert')).toBeTruthy();
 expect(screen.queryByText('INV-1')).toBeNull();
});
