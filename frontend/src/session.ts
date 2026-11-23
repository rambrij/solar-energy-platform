import {useSyncExternalStore} from 'react';

export type ActorType='PLATFORM'|'VENDOR'|'CUSTOMER';
export type Account={
 username:string;
 tenantId:number|null;
 branchId:number|null;
 membershipId:string|null;
 permissions:string[];
 actorType:ActorType;
 availableActorTypes:ActorType[];
};
export type SessionStatus='loading'|'authenticated'|'anonymous'|'error';
export type SessionSnapshot={
 status:SessionStatus;
 loading:boolean;
 refreshing:boolean;
 account:Account|null;
 error:Error|null;
};

export class AuthError extends Error {
 constructor(public status:number,message:string){super(message);this.name='AuthError';}
}

let token:string|undefined;
let generation=0;
let snapshot:SessionSnapshot={
 status:'loading',loading:true,refreshing:false,account:null,error:null
};
const listeners=new Set<()=>void>();
let refreshTask:Promise<boolean>|undefined;
let refreshTaskGeneration:number|undefined;

function publish(status:SessionStatus,account:Account|null=null,error:Error|null=null,refreshing=false){
 snapshot=Object.freeze({
  status,loading:status==='loading',refreshing:status==='loading'&&refreshing,
  account,error
 });
 listeners.forEach(listener=>listener());
}
function subscribe(listener:()=>void){listeners.add(listener);return()=>{listeners.delete(listener);};}
export function useSession(){return useSyncExternalStore(subscribe,()=>snapshot,()=>snapshot);}

function asError(value:unknown,fallback='The authenticated session could not be verified.'){
 return value instanceof Error?value:new Error(fallback);
}
function isAuthenticationRejection(value:unknown){
 return value instanceof AuthError&&[401,403].includes(value.status);
}
function validActorType(value:unknown):value is ActorType{
 return value==='PLATFORM'||value==='VENDOR'||value==='CUSTOMER';
}

async function csrfToken(){
 const response=await fetch('/api/v1/auth/csrf',{credentials:'same-origin',cache:'no-store'});
 if(!response.ok)throw new AuthError(response.status,'Authentication service is unavailable.');
 const value=await response.json();
 if(typeof value.token!=='string'||!value.token)throw new Error('The CSRF token response was invalid.');
 return value.token as string;
}

async function authRequest(path:string,body:unknown,authenticated=false){
 const csrf=await csrfToken();
 const response=await fetch('/api/v1/auth'+path,{
  method:'POST',credentials:'same-origin',cache:'no-store',
  headers:{
   'Content-Type':'application/json','X-XSRF-TOKEN':csrf,
   ...(authenticated&&token?{Authorization:'Bearer '+token}:{})
  },
  body:JSON.stringify(body)
 });
 if(!response.ok){
  const problem=await response.json().catch(()=>({}));
  const message=response.status===409&&path==='/login'
   ?'This identity has both customer and vendor access. Choose one workspace context and sign in again.'
   :path==='/refresh'
    ?'Your session has expired or is no longer active.'
    :path==='/context'
     ?'The selected workspace context is no longer available.'
     :path==='/register'
      ?'Registration unavailable. Use a valid email and a password of at least 12 characters.'
      :'Sign-in unavailable. Check your credentials and connection.';
  throw new AuthError(response.status,typeof problem.detail==='string'?problem.detail:message);
 }
 return response.json();
}

function authorizedHeaders(source?:HeadersInit){
 const headers:Record<string,string>={};
 if(source instanceof Headers)source.forEach((value,key)=>{headers[key]=value;});
 else if(Array.isArray(source))source.forEach(([key,value])=>{headers[key]=value;});
 else if(source)Object.assign(headers,source);
 if(token)headers.Authorization='Bearer '+token;
 return headers;
}

/**
 * /api/v1/auth/me is the only source of active actor scope and effective grants.
 * Login/refresh/context responses contribute only a short-lived token and
 * non-authorizing context-switch display metadata.
 */
async function loadAuthoritativeAccount():Promise<Account>{
 const response=await fetch('/api/v1/auth/me',{
  method:'GET',credentials:'same-origin',cache:'no-store',
  headers:authorizedHeaders()
 });
 if(!response.ok)throw new AuthError(response.status,'The active actor context could not be verified.');
 const value=await response.json();
 const requiredFields=['username','tenantId','branchId','permissions','actorType','membershipId','availableActorTypes'];
 if(!requiredFields.every(field=>Object.prototype.hasOwnProperty.call(value,field))||
    typeof value.username!=='string'||!value.username.trim()||!validActorType(value.actorType)||
    !Array.isArray(value.permissions)||!value.permissions.every((permission:unknown)=>typeof permission==='string')||
    !(value.tenantId===null||(typeof value.tenantId==='number'&&Number.isSafeInteger(value.tenantId)))||
    !(value.branchId===null||(typeof value.branchId==='number'&&Number.isSafeInteger(value.branchId)))||
    !(value.membershipId===null||typeof value.membershipId==='string')){
  throw new AuthError(403,'The server returned an invalid authenticated-session snapshot.');
 }
 const permissions=[...new Set(value.permissions as string[])];
 const tenantId=value.tenantId as number|null;
 const branchId=value.branchId as number|null;
 const membershipId=value.membershipId as string|null;

 // Reject mixed actor grants and malformed scope instead of trying to infer or repair it.
 if(value.actorType==='PLATFORM'&&(tenantId!==null||branchId!==null||membershipId!==null||permissions.length===0||permissions.some(p=>!p.startsWith('PLATFORM_')))){
  throw new AuthError(403,'Platform permissions and scope must remain isolated.');
 }
 if(value.actorType==='VENDOR'&&(tenantId===null||membershipId===null||membershipId.length===0||permissions.some(p=>p==='CUSTOMER'||p.startsWith('PLATFORM_')))){
  throw new AuthError(403,'Vendor permissions require one active vendor tenant context.');
 }
 if(value.actorType==='CUSTOMER'&&(tenantId!==null||branchId!==null||membershipId!==null||permissions.length!==1||permissions[0]!=='CUSTOMER')){
  throw new AuthError(403,'Customer permissions and scope must remain isolated.');
 }

 if(!Array.isArray(value.availableActorTypes)||!value.availableActorTypes.every(validActorType)||!value.availableActorTypes.includes(value.actorType)||
    (value.actorType==='PLATFORM'&&(value.availableActorTypes.length!==1||value.availableActorTypes[0]!=='PLATFORM'))||
    (value.actorType!=='PLATFORM'&&value.availableActorTypes.includes('PLATFORM'))){
  throw new AuthError(403,'The server returned an invalid actor-context list.');
 }
 const validActors=[...new Set(value.availableActorTypes as ActorType[])];
 return {
  username:value.username,tenantId,branchId,membershipId,permissions,actorType:value.actorType,
  availableActorTypes:[...new Set(validActors)]
 };
}

function publishFailure(error:unknown,current:number){
 if(current!==generation)return;
 token=undefined;
 if(isAuthenticationRejection(error))publish('anonymous');
 else publish('error',null,asError(error));
}

export function restoreSession(){
 const current=generation;
 // Coalesce only refreshes that belong to the same login/context generation.
 // A new login or context switch must never await an obsolete actor refresh.
 if(refreshTask&&refreshTaskGeneration===current)return refreshTask;
 // Remove the old grants before any refresh request starts. Protected UI can
 // never continue rendering an actor snapshot while it is being revalidated.
 publish('loading',null,null,true);
 let task!:Promise<boolean>;
 task=(async()=>{
  try{
   const value=await authRequest('/refresh',{});
   if(current!==generation)return false;
   if(typeof value.accessToken!=='string'||!value.accessToken)throw new Error('The refresh response did not contain an access token.');
   token=value.accessToken;
   const account=await loadAuthoritativeAccount();
   if(current!==generation)return false;
   publish('authenticated',account);
   return true;
  }catch(error){
   publishFailure(error,current);
   return false;
  }finally{
   if(refreshTask===task){
    refreshTask=undefined;
    refreshTaskGeneration=undefined;
   }
  }
 })();
 refreshTask=task;
 refreshTaskGeneration=current;
 return task;
}

export async function signIn(
 username:string,password:string,register=false,totpCode?:string,
 actorType?:Exclude<ActorType,'PLATFORM'>
){
 const current=++generation;
 const pendingRefresh=refreshTask;
 token=undefined;
 publish('loading');
 try{
  // Let any earlier refresh finish cookie rotation before issuing a new login.
  if(pendingRefresh)await pendingRefresh;
  if(current!==generation)throw Error('Sign-in superseded');
  const value=await authRequest(register?'/register':'/login',{
   username,password,
   ...(!register&&totpCode?{totpCode}:{}),
   ...(!register&&actorType?{actorType}:{})
  });
  if(current!==generation)throw Error('Sign-in superseded');
  if(typeof value.accessToken!=='string'||!value.accessToken)throw new Error('The sign-in response did not contain an access token.');
  token=value.accessToken;
  const account=await loadAuthoritativeAccount();
  if(current!==generation)throw Error('Sign-in superseded');
  publish('authenticated',account);
 }catch(error){
  publishFailure(error,current);
  throw error;
 }
}

export async function switchActorContext(actorType:Exclude<ActorType,'PLATFORM'>){
 const current=++generation;
 if(!token)throw new AuthError(401,'Sign in before switching workspace context.');
 const pendingRefresh=refreshTask;
 // Keep the bearer only for the context-switch request; publish no old grants.
 publish('loading',null,null,true);
 try{
  // Wait for a refresh-cookie rotation already in flight before context exchange.
  if(pendingRefresh)await pendingRefresh;
  if(current!==generation)throw Error('Context switch superseded');
  const value=await authRequest('/context',{actorType},true);
  if(current!==generation)throw Error('Context switch superseded');
  if(typeof value.accessToken!=='string'||!value.accessToken)throw new Error('The context-switch response did not contain an access token.');
  token=value.accessToken;
  const account=await loadAuthoritativeAccount();
  if(current!==generation)throw Error('Context switch superseded');
  if(account.actorType!==actorType)throw new AuthError(403,'The server did not activate the requested actor context.');
  publish('authenticated',account);
 }catch(error){
  publishFailure(error,current);
  throw error;
 }
}

export async function signOut(){
 const current=++generation;
 const pendingRefresh=refreshTask;
 publish('loading',null,null,true);
 let failure:unknown;
 try{
  // Logout using the current refresh cookie, after a pending rotation is settled.
  if(pendingRefresh)await pendingRefresh;
  if(current===generation)await authRequest('/logout',{},true);
 }
 catch(error){failure=error;}
 finally{
  if(current===generation){
   token=undefined;
   publish('anonymous');
  }
 }
 if(failure)throw failure;
}

export async function authenticatedFetch(path:string,options:RequestInit={}){
 const current=generation;
 let response=await fetch(path,{...options,headers:authorizedHeaders(options.headers)});
 if(response.status===401&&token&&current===generation){
  if(await restoreSession()&&current===generation){
   response=await fetch(path,{...options,headers:authorizedHeaders(options.headers)});
  }
 }
 if(response.status===403&&token&&current===generation){
  // A 403 reconciles the canonical server snapshot, but the rejected operation
  // is never replayed, including mutations with idempotency keys.
  await restoreSession();
 }
 return response;
}
