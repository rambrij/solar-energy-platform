import {afterEach,expect,it,vi} from 'vitest';
import {requestReportExport,downloadReportCsv} from './reportExportApi';
import {api,ApiError} from './api';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}));
const reference='11111111-1111-4111-8111-111111111111';
const createDescriptor=Object.getOwnPropertyDescriptor(URL,'createObjectURL'),revokeDescriptor=Object.getOwnPropertyDescriptor(URL,'revokeObjectURL');
afterEach(()=>{vi.useRealTimers();vi.restoreAllMocks();vi.unstubAllGlobals();vi.resetAllMocks();if(createDescriptor)Object.defineProperty(URL,'createObjectURL',createDescriptor);else Reflect.deleteProperty(URL,'createObjectURL');if(revokeDescriptor)Object.defineProperty(URL,'revokeObjectURL',revokeDescriptor);else Reflect.deleteProperty(URL,'revokeObjectURL');});
it('posts a CSRF-protected request and accepts only an audited CSV response',async()=>{
 vi.mocked(api).mockResolvedValue({token:'csrf-token'});const fetcher=vi.fn().mockResolvedValue(new Response('csv',{headers:{'Content-Type':'text/csv;charset=UTF-8','Content-Disposition':'attachment; filename="sales.csv"','X-Export-Reference':reference}}));vi.stubGlobal('fetch',fetcher);
 const body={from:'2026-11-12',to:'2026-11-13',channel:'ALL',reason:'Review'};const result=await requestReportExport('sales',body);expect(api).toHaveBeenCalledWith('/csrf');expect(fetcher).toHaveBeenCalledWith('/api/vendor/pos/reports/sales/export',expect.objectContaining({method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':'csrf-token'},body:JSON.stringify(body)}));expect(result.filename).toBe('sales.csv');expect(result.reference).toBe(reference);
});
it('rejects denied downloads',async()=>{
 vi.mocked(api).mockResolvedValue({token:'csrf'});vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response('',{status:403})));await expect(requestReportExport('cash-refunds',{from:'2026-11-12',to:'2026-11-13',reason:'Review'})).rejects.toBeInstanceOf(ApiError);
});
it('does not download an HTML login response as a report',async()=>{
 vi.mocked(api).mockResolvedValue({token:'csrf'});vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response('<html/>',{headers:{'Content-Type':'text/html','X-Export-Reference':reference}})));await expect(requestReportExport('sales',{from:'2026-11-12',to:'2026-11-13',reason:'Review'})).rejects.toThrow('Invalid export response');
});
it('cleans the temporary link and revokes the blob URL',()=>{
 vi.useFakeTimers();const create=vi.fn(()=> 'blob:report'),revoke=vi.fn();Object.defineProperty(URL,'createObjectURL',{configurable:true,value:create});Object.defineProperty(URL,'revokeObjectURL',{configurable:true,value:revoke});const click=vi.spyOn(HTMLAnchorElement.prototype,'click').mockImplementation(()=>{});downloadReportCsv({blob:new Blob(['csv']),filename:'sales.csv',reference});expect(click).toHaveBeenCalledOnce();expect(document.querySelector('a[download]')).toBeNull();vi.advanceTimersByTime(1000);expect(revoke).toHaveBeenCalledWith('blob:report');
});
