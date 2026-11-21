import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {signIn,switchActorContext} from './session';

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
});
