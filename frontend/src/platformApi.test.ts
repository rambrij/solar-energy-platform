import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {ApiError,platformApi} from './api';
import {authenticatedFetch} from './session';
vi.mock('./session',()=>({authenticatedFetch:vi.fn()}));
const ok=(body:unknown)=>({ok:true,status:200,json:async()=>body}) as Response;
beforeEach(()=>vi.mocked(authenticatedFetch).mockReset());afterEach(()=>vi.clearAllMocks());
it('GETs under /api/v1/platform and POSTs with the shared CSRF token and JSON body',async()=>{
 const fetchMock=vi.mocked(authenticatedFetch);fetchMock.mockResolvedValueOnce(ok({a:1}));
 expect(await platformApi('/overview')).toEqual({a:1});expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/platform/overview');expect(fetchMock.mock.calls[0][1]!.method).toBe('GET');
 fetchMock.mockResolvedValueOnce(ok({token:'tok'})).mockResolvedValueOnce(ok({id:1}));
 await platformApi('/tenants',{body:{slug:'x'}});
 const post=fetchMock.mock.calls.at(-1)!;expect(post[0]).toBe('/api/v1/platform/tenants');expect(post[1]!.method).toBe('POST');expect((post[1]!.headers as Record<string,string>)['X-XSRF-TOKEN']).toBeTruthy();expect(post[1]!.body).toBe(JSON.stringify({slug:'x'}));
});
it('uses PUT when asked and surfaces the RFC 7807 detail and code',async()=>{
 const fetchMock=vi.mocked(authenticatedFetch);fetchMock.mockResolvedValueOnce({ok:false,status:400,json:async()=>({detail:'Secrets are never stored here',code:'REQUEST_REJECTED'})} as Response);
 const error=await platformApi('/configuration/k',{method:'PUT',body:{value:'x'}}).catch(e=>e);
 expect(error).toBeInstanceOf(ApiError);expect(error.status).toBe(400);expect(error.message).toBe('Secrets are never stored here');expect(error.code).toBe('REQUEST_REJECTED');expect(fetchMock.mock.calls.at(-1)![1]!.method).toBe('PUT');
});
it('falls back to a generic message when the error body is not JSON',async()=>{
 vi.mocked(authenticatedFetch).mockResolvedValueOnce({ok:false,status:403,json:async()=>{throw new Error('no body');}} as unknown as Response);
 const error=await platformApi('/audit').catch(e=>e);expect(error.status).toBe(403);expect(error.message).toBe('Request failed (403).');
});
