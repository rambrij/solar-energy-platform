import {afterEach,expect,it,vi} from 'vitest';
import {requestReceiptPdf} from './receiptDocumentApi';
import {api} from './api';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}));
afterEach(()=>{vi.resetAllMocks();vi.unstubAllGlobals();});
const reference='11111111-1111-4111-8111-111111111111';
function response(type='application/pdf',ref=reference,size=100){return {ok:true,headers:new Headers({'Content-Type':type,'X-Document-Reference':ref,'Content-Disposition':'attachment; filename="receipt-copy.pdf"'}),blob:async()=>new Blob(['x'.repeat(size)])};}
it('uses CSRF and posts only the generation reason to the scoped receipt route',async()=>{vi.mocked(api).mockResolvedValue({token:'csrf'});const fetch=vi.fn().mockResolvedValue(response());vi.stubGlobal('fetch',fetch);const result=await requestReceiptPdf('order-1','Buyer copy');expect(fetch).toHaveBeenCalledWith('/api/vendor/pos/receipts/order-1/document',expect.objectContaining({method:'POST',headers:expect.objectContaining({'X-XSRF-TOKEN':'csrf'}),body:JSON.stringify({reason:'Buyer copy'})}));expect(result.filename).toBe('receipt-copy.pdf');expect(result.reference).toBe(reference);});
it('rejects denial, unexpected content/identity and oversized or empty bodies',async()=>{vi.mocked(api).mockResolvedValue({token:'csrf'});for(const value of [{ok:false,status:403},response('application/json'),response('application/pdf','bad'),response('application/pdf',reference,0),response('application/pdf',reference,2_000_001)]){vi.stubGlobal('fetch',vi.fn().mockResolvedValue(value));await expect(requestReceiptPdf('order','Copy')).rejects.toThrow();}});
