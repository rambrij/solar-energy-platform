import {useSyncExternalStore} from 'react';
export type Account={username:string;tenantId:number|null;branchId:number|null;permissions:string[]};
type Snapshot={ready:boolean;account:Account|null};
let token:string|undefined;
let generation=0;
let snapshot:Snapshot={ready:false,account:null};
const listeners=new Set<()=>void>();
let refreshTask:Promise<boolean>|undefined;
function publish(account:Account|null){snapshot={ready:true,account};listeners.forEach(listener=>listener());}
function subscribe(listener:()=>void){listeners.add(listener);return()=>{listeners.delete(listener);};}
export function useSession(){return useSyncExternalStore(subscribe,()=>snapshot);}
async function authRequest(path:string,body:unknown){
 const csrfResponse=await fetch('/api/v1/auth/csrf',{credentials:'same-origin',cache:'no-store'});if(!csrfResponse.ok)throw Error('Authentication unavailable');
 const csrf=await csrfResponse.json();
 const response=await fetch('/api/v1/auth'+path,{method:'POST',credentials:'same-origin',cache:'no-store',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':csrf.token},body:JSON.stringify(body)});
 if(!response.ok)throw Error(path==='/register'?'Registration unavailable. Use a valid email and a password of at least 12 characters.':'Sign-in unavailable. Check your credentials and connection.');return response.json();
}
export function restoreSession(){
 if(refreshTask)return refreshTask;
 const current=generation;
 refreshTask=(async()=>{try{const value=await authRequest('/refresh',{});if(current!==generation)return false;token=value.accessToken;publish(value.account);return true;}catch{if(current===generation){token=undefined;publish(null);}return false;}finally{refreshTask=undefined;}})();return refreshTask;
}
export async function signIn(username:string,password:string,register=false){const current=++generation;const value=await authRequest(register?'/register':'/login',{username,password});if(current!==generation)throw Error('Sign-in superseded');token=value.accessToken;publish(value.account);}
export async function signOut(){const current=++generation;await authRequest('/logout',{});if(current===generation){token=undefined;publish(null);}}
function authorizedHeaders(source?:HeadersInit){const headers:Record<string,string>={};if(source instanceof Headers)source.forEach((value,key)=>{headers[key]=value;});else if(Array.isArray(source))source.forEach(([key,value])=>{headers[key]=value;});else Object.assign(headers,source);if(token)headers.Authorization='Bearer '+token;return headers;}
export async function authenticatedFetch(path:string,options:RequestInit={}){
 const current=generation;
 let response=await fetch(path,{...options,headers:authorizedHeaders(options.headers)});
 if(response.status===401&&token&&current===generation){if(await restoreSession()&&current===generation)response=await fetch(path,{...options,headers:authorizedHeaders(options.headers)});}
 return response;
}
