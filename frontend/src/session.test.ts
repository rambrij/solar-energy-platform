import {renderHook} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {authenticatedFetch,signIn,switchActorContext,useSession} from './session';

function response(body:unknown,status=200){
 return {ok:status>=200&&status<300,status,json:vi.fn().mockResolvedValue(body)} as unknown as Response;
}

describe('actor context session switching',()=>{
 beforeEach(()=>{vi.stubGlobal('fetch',vi.fn());});
 afterEach(()=>{vi.unstubAllGlobals();vi.restoreAllMocks();});

 it('sends the active bearer token and same-origin refresh cookie with the context switch',async()=>{
  const fetchMock=vi.mocked(fetch);
  fetchMock
   .mockResolvedValueOnce(response({token:'csrf-login'}))
   .mockResolvedValueOnce(response({accessToken:'customer-access-token',expiresIn:300,account:{
    username:'dual@example.test',tenantId:null,branchId:null,permissions:['CUSTOMER'],actorType:'CUSTOMER',
    availableActorTypes:['CUSTOMER','VENDOR']
   }}))
   .mockResolvedValueOnce(response({token:'csrf-switch'}))
   .mockResolvedValueOnce(response({accessToken:'vendor-access-token',expiresIn:300,account:{
    username:'dual@example.test',tenantId:8,branchId:80,permissions:['PRODUCT_VIEW'],actorType:'VENDOR',
    availableActorTypes:['CUSTOMER','VENDOR']
   }}));

  await signIn('dual@example.test','safe-test-password',false,undefined,'CUSTOMER');
  await switchActorContext('VENDOR');

  expect(fetchMock).toHaveBeenCalledTimes(4);
  const [switchUrl,switchOptions]=fetchMock.mock.calls[3];
  expect(switchUrl).toBe('/api/v1/auth/context');
  const headers=new Headers((switchOptions as RequestInit).headers);
  expect(headers.get('Authorization')).toBe('Bearer customer-access-token');
  expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-switch');
  expect((switchOptions as RequestInit).credentials).toBe('same-origin');
  expect(JSON.parse(String((switchOptions as RequestInit).body))).toEqual({actorType:'VENDOR'});
 });
 it('refreshes permissions after 403 but never replays the denied request',async()=>{
  const fetchMock=vi.mocked(fetch);
  const full={username:'vendor@example.test',tenantId:8,branchId:80,permissions:['POS_VIEW','POS_SALE_CREATE'],actorType:'VENDOR'};
  const narrow={...full,permissions:['POS_VIEW']};
  fetchMock
   .mockResolvedValueOnce(response({token:'csrf-login'}))
   .mockResolvedValueOnce(response({accessToken:'vendor-token',account:full}))
   .mockResolvedValueOnce(response({detail:'Forbidden'},403))
   .mockResolvedValueOnce(response({token:'csrf-refresh'}))
   .mockResolvedValueOnce(response({accessToken:'refreshed-token',account:narrow}));
  await signIn('vendor@example.test','safe-test-password');
  const {result}=renderHook(()=>useSession());
  const denied=await authenticatedFetch('/api/v1/vendor/pos/sales',{method:'POST',body:'{}'});
  expect(denied.status).toBe(403);
  expect(fetchMock.mock.calls.filter(([url])=>url==='/api/v1/vendor/pos/sales')).toHaveLength(1);
  expect(result.current.account?.permissions).toEqual(['POS_VIEW']);
  expect(fetchMock).toHaveBeenCalledTimes(5);
 });

});
