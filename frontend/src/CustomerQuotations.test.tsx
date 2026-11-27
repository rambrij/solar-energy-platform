import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import CustomerQuotations from './CustomerQuotations';
const {apiMock}=vi.hoisted(()=>({apiMock:vi.fn()}));
vi.mock('./marketplaceApi',()=>({marketplaceApi:apiMock}));
const quote={id:12,enquiryId:8,status:'OPEN',acceptedRevisionNumber:null,convertedOrderId:null,version:0,revisions:[{id:19,revisionNumber:1,status:'SENT',currency:'INR',validUntil:'2099-01-01T00:00:00Z',subtotal:10000,taxTotal:1800,total:11800,terms:'Roof inspection before installation',sentAt:'2026-11-27T10:00:00Z',items:[{productId:3,name:'Solar inverter',quantity:1,listPrice:10000,unitPrice:10000,discountPercent:0,taxRate:18,tax:1800,total:11800}]}]};
beforeEach(()=>{apiMock.mockImplementation((path:string,method='GET',body?:unknown)=>path==='/api/v1/customer/quotations'?Promise.resolve([quote]):Promise.resolve({quotation:{...quote,status:(body as {accept:boolean}).accept?'CONVERTED':'OPEN',acceptedRevisionNumber:1,convertedOrderId:(body as {accept:boolean}).accept?'order-12':null},order:(body as {accept:boolean}).accept?{id:'order-12',status:'DRAFT',total:11800,expiresAt:'2099-01-01T00:00:00Z',items:[]}:null}));});
afterEach(()=>{cleanup();vi.clearAllMocks();});
it('shows the saved revision price, tax and validity to the global customer',async()=>{
 render(<CustomerQuotations/>);
 expect(await screen.findByText(/INR 11800\.00 · Valid until/)).toBeTruthy();
 expect(screen.getByText(/Roof inspection before installation/)).toBeTruthy();
});
it('accepts a current revision and displays the unpaid converted order',async()=>{
 render(<CustomerQuotations/>);
 fireEvent.click(await screen.findByRole('button',{name:'Accept quotation'}));
 await waitFor(()=>expect(apiMock).toHaveBeenCalledWith(
  '/api/v1/customer/quotations/12/revisions/1/decision','POST',{accept:true,reason:null}
 ));
 expect(await screen.findByText(/Order reservation created: order-12/)).toBeTruthy();
 expect(screen.getByText(/no payment has been taken/i)).toBeTruthy();
});
it('requires a rejection reason and posts a rejection decision',async()=>{
 render(<CustomerQuotations/>);
 const reject=await screen.findByRole('button',{name:'Reject quotation'});
 expect((reject as HTMLButtonElement).disabled).toBe(true);
 fireEvent.change(screen.getByLabelText('Reason (required to reject)'),{target:{value:'Please revise the installation scope'}});
 fireEvent.click(reject);
 await waitFor(()=>expect(apiMock).toHaveBeenCalledWith(
  '/api/v1/customer/quotations/12/revisions/1/decision','POST',
  {accept:false,reason:'Please revise the installation scope'}
 ));
 expect(await screen.findByText('Quotation revision rejected.')).toBeTruthy();
});
