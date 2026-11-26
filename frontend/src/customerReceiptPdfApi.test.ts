import {afterEach,expect,it,vi} from 'vitest';
import {requestCustomerReceiptPdf} from './customerReceiptPdfApi';
import {marketplaceApi,MarketplaceError} from './marketplaceApi';
import {authenticatedFetch} from './session';

vi.mock('./marketplaceApi',()=>({
 marketplaceApi:vi.fn(),
 MarketplaceError:class extends Error{constructor(status:number){super('Request failed ('+status+')');}}
}));
vi.mock('./session',()=>({authenticatedFetch:vi.fn()}));
afterEach(()=>{vi.resetAllMocks();});

const reference='11111111-1111-4111-8111-111111111111';
function response(type='application/pdf',ref=reference,size=100):Response{
 return {
  ok:true,status:200,
  headers:new Headers({'Content-Type':type,'X-Document-Reference':ref,'Content-Disposition':'attachment; filename="receipt-copy.pdf"'}),
  blob:async()=>new Blob(['x'.repeat(size)])
 } as Response;
}

it('fetches the customer CSRF token and validates the owned-order PDF response',async()=>{
 vi.mocked(marketplaceApi).mockResolvedValue({token:'customer-csrf'});
 vi.mocked(authenticatedFetch).mockResolvedValue(response());
 const result=await requestCustomerReceiptPdf('order-1');
 expect(marketplaceApi).toHaveBeenCalledWith('/api/v1/customer/csrf');
 expect(authenticatedFetch).toHaveBeenCalledWith('/api/v1/customer/orders/order-1/receipt-document',
  expect.objectContaining({method:'POST',headers:expect.objectContaining({'X-XSRF-TOKEN':'customer-csrf'}),body:'{}'}));
 expect(result.filename).toBe('receipt-copy.pdf');
 expect(result.reference).toBe(reference);
});

it('rejects denied, non-PDF, malformed-reference and oversized responses',async()=>{
 vi.mocked(marketplaceApi).mockResolvedValue({token:'customer-csrf'});
 const denied={ok:false,status:404,headers:new Headers(),blob:async()=>new Blob()} as Response;
 const values=[denied,response('application/json'),response('application/pdf','bad'),response('application/pdf',reference,2_000_001)];
 for(const value of values){
  vi.mocked(authenticatedFetch).mockResolvedValue(value);
  await expect(requestCustomerReceiptPdf('order-1')).rejects.toThrow();
 }
});
