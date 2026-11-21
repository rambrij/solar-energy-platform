import {useSyncExternalStore} from 'react';

export type ActorType='PLATFORM'|'VENDOR'|'CUSTOMER';
export type Account={username:string;tenantId:number|null;branchId:number|null;permissions:string[];actorType:ActorType;availableActorTypes?:ActorType[]};
type Snapshot={ready:boolean;account:Account|null};
export class AuthError extends Error {constructor(public status:number,message:string){super(message);}}
let token:string|undefined;
let generation=0;
let snapshot:Snapshot={ready:false,account:null};
const listeners=new Set<()=>void>();
let refreshTask:Promise<boolean>|undefined;

function publish(account:Account|null){snapshot={ready:true,account};listeners.forEach(listener=>listener());}
function subscribe(listener:()=>void){listeners.add(listener);return()=>{listeners.delete(listener);};}
export function useSession(){return useSyncExternalStore(subscribe,()=>snapshot);}

async function authRequest(path:string,body:unknown,authenticated=false){
 const csrfResponse=await fetch('/api/v1/auth/csrf',{credentials:'same-origin',cache:'no-store'});
 if(!csrfResponse.ok)throw new AuthError(csrfResponse.status,'Authentication unavailable');
 const csrf=await csrfResponse.json();
 const response=await fetch('/api/v1/auth'+path,{method:'POST',credentials:'same-origin',cache:'no-store',
  headers:{'Content-Type':'application/json','X-XSRF-TOKEN':csrf.token,...(authenticated&&token?{Authorization:'Bearer '+token}:{})},body:JSON.stringify(body)});
 if(!response.ok){
  const problem=await response.json().catch(()=>({}));
  const message=response.status===409&&path==='/login'
   ?'This identity has both customer and vendor access. Choose one workspace context and sign in again.'
   :path==='/register'
    ?'Registration unavailable. Use a valid email and a password of at least 12 characters.'
    :'Sign-in unavailable. Check your credentials and connection.';
  throw new AuthError(response.status,message);
 }
 return response.json();
}

export function restoreSession(){
 if(refreshTask)return refreshTask;
 const current=generation;
 refreshTask=(async()=>{
  try{const value=await authRequest('/refresh',{});if(current!==generation)return false;token=value.accessToken;publish(value.account);return true;}
  catch{if(current===generation){token=undefined;publish(null);}return false;}
  finally{refreshTask=undefined;}
 })();
 return refreshTask;
}

export async function signIn(username:string,password:string,register=false,totpCode?:string,actorType?:Exclude<ActorType,'PLATFORM'>){
 const current=++generation;
 const value=await authRequest(register?'/register':'/login',{username,password,...(!register&&totpCode?{totpCode}:{}),...(!register&&actorType?{actorType}:{})});
 if(current!==generation)throw Error('Sign-in superseded');
 token=value.accessToken;publish(value.account);
}

export async function switchActorContext(actorType:Exclude<ActorType,'PLATFORM'>){
 const current=++generation;
 const value=await authRequest('/context',{actorType},true);
 if(current!==generation)throw Error('Context switch superseded');
 token=value.accessToken;publish(value.account);
}

export async function signOut(){
 const current=++generation;
 await authRequest('/logout',{});
 if(current===generation){token=undefined;publish(null);}
}

function authorizedHeaders(source?:HeadersInit){
 const headers:Record<string,string>={};
 if(source instanceof Headers)source.forEach((value,key)=>{headers[key]=value;});
 else if(Array.isArray(source))source.forEach(([key,value])=>{headers[key]=value;});
 else Object.assign(headers,source);
 if(token)headers.Authorization='Bearer '+token;
 return headers;
}

export async function authenticatedFetch(path:string,options:RequestInit={}){
 const current=generation;
 let response=await fetch(path,{...options,headers:authorizedHeaders(options.headers)});
 if(response.status===401&&token&&current===generation){
  if(await restoreSession()&&current===generation)
   response=await fetch(path,{...options,headers:authorizedHeaders(options.headers)});
 }
 return response;
}
