import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import VendorQuotations from './VendorQuotations';
const {apiMock,sessionMock,downloadMock}=vi.hoisted(()=>({apiMock:vi.fn(),sessionMock:vi.fn(),downloadMock:vi.fn()}));
vi.mock('./api',()=>({vendorApi:apiMock}));
vi.mock('./session',()=>({useSession:sessionMock}));
vi.mock('./marketplaceApi',()=>({downloadProtectedPdf:downloadMock}));
const lead={id:41,status:'QUALIFIED',contactName:'Asha Rao',productName:'Solar package',assignedTo:'sales@vendor.test'};
const product={id:9,sku:'PANEL-9',name:'Solar Panel 550W',price:12000,taxRate:18,listPrice:13000,discountPercent:7.69,inStock:true};
const quote={id:71,enquiryId:41,status:'OPEN',acceptedRevisionNumber:null,convertedOrderId:null,version:1,revisions:[{id:81,revisionNumber:1,status:'SENT',currency:'INR',validUntil:'2099-01-01T00:00:00Z',subtotal:12000,taxTotal:2160,total:14160,terms:'Standard installation terms',sentAt:'2026-11-27T10:00:00Z',items:[{productId:9,name:'Solar Panel 550W',quantity:1,listPrice:13000,unitPrice:12000,discountPercent:7.69,taxRate:18,tax:2160,total:14160}]}]};
beforeEach(()=>{
 sessionMock.mockReturnValue({account:{username:'sales@vendor.test',tenantId:7,permissions:['LEAD_VIEW','LEAD_UPDATE']}});
 apiMock.mockImplementation((path:string,method='GET')=>{
  if(path==='/leads')return Promise.resolve([lead]);
  if(path==='/quotation-catalogue')return Promise.resolve([product]);
  if(path==='/quotations'&&method==='GET')return Promise.resolve([quote]);
  if(path==='/quotations'&&method==='POST')return Promise.resolve(quote);
  if(path==='/quotations/71/revisions'&&method==='POST')return Promise.resolve(quote);
  return Promise.resolve([]);
 });
});
afterEach(()=>{cleanup();vi.clearAllMocks();});
it('loads assigned leads, the quotation catalogue and quotation revisions',async()=>{
 render(<VendorQuotations/>);
 expect(await screen.findByText('Quotation #71 · Asha Rao')).toBeTruthy();
 expect(await screen.findByText(/Standard installation terms/)).toBeTruthy();
 expect(apiMock).toHaveBeenCalledWith('/leads');
 expect(apiMock).toHaveBeenCalledWith('/quotation-catalogue');
 expect(apiMock).toHaveBeenCalledWith('/quotations');
});
it('creates a quotation with line quantities, validity and an audit reason',async()=>{
 apiMock.mockImplementation((path:string,method='GET')=>{
  if(path==='/leads')return Promise.resolve([lead]);
  if(path==='/quotation-catalogue')return Promise.resolve([product]);
  if(path==='/quotations'&&method==='GET')return Promise.resolve([]);
  if(path==='/quotations'&&method==='POST')return Promise.resolve(quote);
  return Promise.resolve([]);
 });
 render(<VendorQuotations/>);
 await screen.findByText('Send a new quotation');
 fireEvent.change(screen.getByLabelText('Customer enquiry'),{target:{value:'41'}});
 fireEvent.change(screen.getByLabelText('Product'),{target:{value:'9'}});
 fireEvent.change(screen.getByLabelText('Reason for sending'),{target:{value:'Customer requested a formal offer'}});
 fireEvent.click(screen.getByRole('button',{name:'Send quotation'}));
 await waitFor(()=>expect(apiMock).toHaveBeenCalledWith('/quotations','POST',expect.objectContaining({
  enquiryId:41,items:[{productId:9,quantity:1}],validityDays:30,reason:'Customer requested a formal offer'
 })));
 expect(await screen.findByText(/Quotation sent/)).toBeTruthy();
});
it('allows a lead editor to prepare a new immutable revision',async()=>{
 render(<VendorQuotations/>);
 await screen.findByText('Quotation #71 · Asha Rao');
 fireEvent.click(screen.getByRole('button',{name:'Create next revision'}));
 fireEvent.change(screen.getByLabelText('Reason for sending'),{target:{value:'Updated installation scope'}});
 fireEvent.click(screen.getByRole('button',{name:'Send quotation revision'}));
 await waitFor(()=>expect(apiMock).toHaveBeenCalledWith('/quotations/71/revisions','POST',expect.objectContaining({
  items:[{productId:9,quantity:1}],reason:'Updated installation scope'
 })));
});

it('downloads the selected immutable quotation revision PDF through the authenticated document helper',async()=>{
 render(<VendorQuotations/>);
 await screen.findByText('Quotation #71 · Asha Rao');
 fireEvent.click(screen.getAllByRole('button',{name:'Download PDF'})[0]);
 await waitFor(()=>expect(downloadMock).toHaveBeenCalledWith(
  '/api/v1/vendor/quotations/71/revisions/1/document',
  'quotation-71-revision-1.pdf'
 ));
});
