import {renderHook,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {authenticatedFetch,restoreSession,signIn,signOut,switchActorContext,useSession,type ActorType} from './session';

function response(body:unknown,status=200){
 return {ok:status>=200&&status<300,status,json:vi.fn().mockResolvedValue(body)} as unknown as Response;
}
const account=(actorType:'PLATFORM'|'VENDOR'|'CUSTOMER'='VENDOR',permissions=['POS_VIEW'],tenantId:number|null=actorType==='VENDOR'?8:null)=>({
 username:'actor@example.test',tenantId,branchId:actorType==='VENDOR'?80:null,
 membershipId:actorType==='VENDOR'?`${tenantId}:actor@example.test`:null,
 permissions,actorType,
 availableActorTypes:[actorType]
});
function authMe(value=account()){
 return response(value);
}
function loginSequence(fetchMock:ReturnType<typeof vi.fn>,active=account()){
 fetchMock.mockResolvedValueOnce(response({token:'csrf-login'}))
  .mockResolvedValueOnce(response({accessToken:'access-token',account:active}))
  .mockResolvedValueOnce(authMe(active));
}

describe('canonical authenticated session snapshot',()=>{
 beforeEach(()=>{vi.stubGlobal('fetch',vi.fn());});
 afterEach(()=>{vi.unstubAllGlobals();vi.restoreAllMocks();});

 it('uses the exact backend Account record from /auth/me, not the login payload, and loads it once',async()=>{
  const fetchMock=vi.mocked(fetch);
  const loginAccount=account('VENDOR',['POS_VIEW']);
  const authoritativeAccount=account('VENDOR',['POS_VIEW','PRODUCT_VIEW']);
  fetchMock.mockResolvedValueOnce(response({token:'csrf-login'}))
   .mockResolvedValueOnce(response({accessToken:'access-token',account:loginAccount}))
   .mockResolvedValueOnce(authMe(authoritativeAccount));
  await signIn('actor@example.test','safe-test-password');
  const {result}=renderHook(()=>useSession());
  expect(result.current.status).toBe('authenticated');
  expect(result.current.account).toEqual(authoritativeAccount);
  expect(result.current.account?.permissions).toEqual(['POS_VIEW','PRODUCT_VIEW']);
  expect(fetchMock.mock.calls.filter(([url])=>url==='/api/v1/auth/me')).toHaveLength(1);
 });

 it('uses one refresh-derived snapshot for all permission consumers without another fetch',async()=>{
  const fetchMock=vi.mocked(fetch);loginSequence(fetchMock);
  await signIn('actor@example.test','safe-test-password');
  const {result}=renderHook(()=>useSession());
  expect(result.current.account?.permissions).toEqual(['POS_VIEW']);
  expect(fetchMock.mock.calls.filter(([url])=>url==='/api/v1/auth/me')).toHaveLength(1);
 });

 it('fails closed while initial restore is pending and on a failed refresh',async()=>{
  const fetchMock=vi.mocked(fetch);
  let release:(value:Response)=>void=()=>{};
  fetchMock.mockResolvedValueOnce(response({token:'csrf-refresh'}))
   .mockImplementationOnce(()=>new Promise<Response>(resolve=>{release=resolve;}));
  const {result}=renderHook(()=>useSession());
  const pending=restoreSession();
  expect(result.current.loading).toBe(true);
  expect(result.current.account).toBeNull();
  release(response({detail:'expired'},401));
  await pending;
  expect(result.current.status).toBe('anonymous');
  expect(result.current.account).toBeNull();
 });

 it('replaces all grants after a 403 without replaying the rejected mutation',async()=>{
  const fetchMock=vi.mocked(fetch);loginSequence(fetchMock,account('VENDOR',['POS_VIEW','POS_SALE_CREATE']));
  fetchMock.mockResolvedValueOnce(response({detail:'Forbidden'},403))
   .mockResolvedValueOnce(response({token:'csrf-refresh'}))
   .mockResolvedValueOnce(response({accessToken:'refreshed-token',account:account('VENDOR',['POS_VIEW'])}))
   .mockResolvedValueOnce(authMe(account('VENDOR',['POS_VIEW'])));
  await signIn('actor@example.test','safe-test-password');
  const {result}=renderHook(()=>useSession());
  const denied=await authenticatedFetch('/api/v1/vendor/pos/sales',{method:'POST',body:'{}'});
  expect(denied.status).toBe(403);
  await waitFor(()=>expect(result.current.account?.permissions).toEqual(['POS_VIEW']));
  expect(fetchMock.mock.calls.filter(([url])=>url==='/api/v1/vendor/pos/sales')).toHaveLength(1);
 });

 it('clears protected permissions when the 403-triggered refresh fails',async()=>{
  const fetchMock=vi.mocked(fetch);loginSequence(fetchMock);
  fetchMock.mockResolvedValueOnce(response({detail:'Forbidden'},403))
   .mockResolvedValueOnce(response({token:'csrf-refresh'}))
   .mockResolvedValueOnce(response({detail:'session expired'},401));
  await signIn('actor@example.test','safe-test-password');
  const {result}=renderHook(()=>useSession());
  await authenticatedFetch('/api/v1/vendor/pos/sales',{method:'POST',body:'{}'});
  await waitFor(()=>expect(result.current.account).toBeNull());
  expect(result.current.status).toBe('anonymous');
 });

 it('clears all grants when every role has been removed',async()=>{
  const fetchMock=vi.mocked(fetch);loginSequence(fetchMock);
  fetchMock.mockResolvedValueOnce(response({token:'csrf-refresh'}))
   .mockResolvedValueOnce(response({accessToken:'refreshed-token',account:account('VENDOR',[])}))
   .mockResolvedValueOnce(authMe(account('VENDOR',[])));
  await signIn('actor@example.test','safe-test-password');
  await restoreSession();
  const {result}=renderHook(()=>useSession());
  expect(result.current.account?.permissions).toEqual([]);
 });

 it('rejects a vendor snapshot after membership is disabled or tenant is suspended',async()=>{
  for(const status of [403,401]){
   const fetchMock=vi.mocked(fetch);loginSequence(fetchMock);
   fetchMock.mockResolvedValueOnce(response({token:'csrf-refresh'}))
    .mockResolvedValueOnce(response({detail:'membership inactive'},status));
   await signIn('actor@example.test','safe-test-password');
   await restoreSession();
   expect(useSessionSnapshot()).toBeNull();
   vi.unstubAllGlobals();vi.stubGlobal('fetch',vi.fn());
  }
 });

 it('replaces the complete Customer snapshot with Vendor context and never mixes grants',async()=>{
  const fetchMock=vi.mocked(fetch);
  const customer={...account('CUSTOMER',['CUSTOMER'],null),availableActorTypes:['CUSTOMER','VENDOR'] as ActorType[]};
  const vendor={...account('VENDOR',['PRODUCT_VIEW'],8),availableActorTypes:['CUSTOMER','VENDOR'] as ActorType[]};
  loginSequence(fetchMock,customer);
  fetchMock.mockResolvedValueOnce(response({token:'csrf-switch'}))
   .mockResolvedValueOnce(response({accessToken:'vendor-access-token',account:vendor}))
   .mockResolvedValueOnce(authMe(vendor));
  await signIn('actor@example.test','safe-test-password',false,undefined,'CUSTOMER');
  await switchActorContext('VENDOR');
  const {result}=renderHook(()=>useSession());
  expect(result.current.account?.actorType).toBe('VENDOR');
  expect(result.current.account?.tenantId).toBe(8);
  expect(result.current.account?.permissions).toEqual(['PRODUCT_VIEW']);
  expect(result.current.account?.permissions).not.toContain('CUSTOMER');
 });

 it('replaces Vendor context with Customer context and clears tenant scope',async()=>{
  const fetchMock=vi.mocked(fetch);
  const vendor={...account('VENDOR',['PRODUCT_VIEW'],8),availableActorTypes:['CUSTOMER','VENDOR'] as ActorType[]};
  const customer={...account('CUSTOMER',['CUSTOMER'],null),availableActorTypes:['CUSTOMER','VENDOR'] as ActorType[]};
  loginSequence(fetchMock,vendor);
  fetchMock.mockResolvedValueOnce(response({token:'csrf-switch'}))
   .mockResolvedValueOnce(response({accessToken:'customer-access-token',account:customer}))
   .mockResolvedValueOnce(authMe(customer));
  await signIn('actor@example.test','safe-test-password');
  await switchActorContext('CUSTOMER');
  const {result}=renderHook(()=>useSession());
  expect(result.current.account?.actorType).toBe('CUSTOMER');
  expect(result.current.account?.tenantId).toBeNull();
  expect(result.current.account?.branchId).toBeNull();
  expect(result.current.account?.permissions).toEqual(['CUSTOMER']);
 });

 it('rejects mixed Platform/Vendor/Customer grants from /auth/me',async()=>{
  const fetchMock=vi.mocked(fetch);
  fetchMock.mockResolvedValueOnce(response({token:'csrf-login'}))
   .mockResolvedValueOnce(response({accessToken:'access-token',account:account()}))
   .mockResolvedValueOnce(response({...account(),permissions:['POS_VIEW','PLATFORM_ANALYTICS_VIEW','CUSTOMER']}));
  await expect(signIn('actor@example.test','safe-test-password')).rejects.toThrow();
  expect(useSessionSnapshot()).toBeNull();
 });

 it('does not reuse an in-flight refresh from an earlier login generation',async()=>{
  const fetchMock=vi.mocked(fetch);loginSequence(fetchMock);
  await signIn('actor@example.test','safe-test-password');

  let releaseOldRefresh:(value:Response)=>void=()=>{};
  let signalOldRefreshStarted!:()=>void;
  const oldRefreshStarted=new Promise<void>(resolve=>{signalOldRefreshStarted=resolve;});
  fetchMock.mockResolvedValueOnce(response({token:'csrf-old-refresh'}))
   .mockImplementationOnce(()=>new Promise<Response>(resolve=>{signalOldRefreshStarted();releaseOldRefresh=resolve;}));
  const oldRefresh=restoreSession();
  await oldRefreshStarted;

  const secondAccount={...account(),username:'second@example.test'};
  fetchMock.mockResolvedValueOnce(response({token:'csrf-second-login'}))
   .mockResolvedValueOnce(response({accessToken:'second-access-token',account:secondAccount}))
   .mockResolvedValueOnce(authMe(secondAccount));
  await signIn('second@example.test','another-safe-password');

  fetchMock.mockResolvedValueOnce(response({token:'csrf-current-refresh'}))
   .mockResolvedValueOnce(response({accessToken:'current-refresh-token',account:secondAccount}))
   .mockResolvedValueOnce(authMe(secondAccount));
  const currentRefresh=restoreSession();
  expect(currentRefresh).not.toBe(oldRefresh);
  await expect(currentRefresh).resolves.toBe(true);

  releaseOldRefresh(response({accessToken:'stale-refresh-token',account:account()}));
  await expect(oldRefresh).resolves.toBe(false);
  expect(useSessionSnapshot()?.username).toBe('second@example.test');
  expect(fetchMock.mock.calls.filter(([url])=>url==='/api/v1/auth/me')).toHaveLength(3);
 });

 it('rejects incomplete /auth/me records instead of inferring missing actor scope',async()=>{
  const fetchMock=vi.mocked(fetch);
  const {membershipId:omittedMembership,...incomplete}=account('CUSTOMER',['CUSTOMER'],null);
  fetchMock.mockResolvedValueOnce(response({token:'csrf-login'}))
   .mockResolvedValueOnce(response({accessToken:'access-token',account:account('CUSTOMER',['CUSTOMER'],null)}))
   .mockResolvedValueOnce(response(incomplete));
  await expect(signIn('actor@example.test','safe-test-password',false,undefined,'CUSTOMER')).rejects.toThrow();
  expect(useSessionSnapshot()).toBeNull();
 });

 it('clears the canonical snapshot on logout before a different actor signs in',async()=>{
  const fetchMock=vi.mocked(fetch);loginSequence(fetchMock);
  fetchMock.mockResolvedValueOnce(response({token:'csrf-logout'}))
   .mockResolvedValueOnce(response({}));
  await signIn('actor@example.test','safe-test-password');
  await signOut();
  expect(useSessionSnapshot()).toBeNull();

  const nextActor={...account('CUSTOMER',['CUSTOMER'],null),username:'next-customer@example.test'};
  fetchMock.mockResolvedValueOnce(response({token:'csrf-next-login'}))
   .mockResolvedValueOnce(response({accessToken:'customer-access-token',account:nextActor}))
   .mockResolvedValueOnce(authMe(nextActor));
  await signIn('next-customer@example.test','another-safe-password',false,undefined,'CUSTOMER');
  const active=useSessionSnapshot();
  expect(active?.username).toBe('next-customer@example.test');
  expect(active?.actorType).toBe('CUSTOMER');
  expect(active?.permissions).toEqual(['CUSTOMER']);
 });

 it('does not store access or refresh tokens in localStorage',async()=>{
  const fetchMock=vi.mocked(fetch);loginSequence(fetchMock);
  await signIn('actor@example.test','safe-test-password');
  expect(localStorage.length).toBe(0);
 });
});

function useSessionSnapshot(){
 // The external store is exposed through the same hook used by all UI consumers.
 const {result,unmount}=renderHook(()=>useSession());
 const accountSnapshot=result.current.account;
 unmount();
 return accountSnapshot;
}
