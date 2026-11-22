import {authenticatedFetch} from './session';
let csrf='';
export class ApiError extends Error {constructor(public status:number,public code?:string,detail?:string){super(detail??`Request failed (${status}).`);}}
async function request(url:string,method:string,body?:unknown,key?:string){
 if(body!==undefined&&!csrf){const response=await authenticatedFetch('/api/v1/auth/csrf',{credentials:'same-origin'});if(!response.ok)throw new ApiError(response.status);csrf=(await response.json()).token;}
 const response=await authenticatedFetch(url,{method,credentials:'same-origin',headers:{'Content-Type':'application/json',...(csrf?{'X-XSRF-TOKEN':csrf}:{}),...(key?{'Idempotency-Key':key}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
 if(!response.ok){const problem=await response.json().catch(()=>({}));throw new ApiError(response.status,problem.code,problem.detail);}return response.status===204?undefined:response.json();
}
export function authApi(path:string){if(path!=='/api/v1/auth/me')throw new Error('Unsupported auth read');return request(path,'GET');}
export function authPost(path:string,body:unknown){return request('/api/v1/auth'+path,'POST',body);}
export function api(path:string,body?:unknown,key?:string){return request('/api/v1/vendor/pos'+path,body===undefined?'GET':'POST',body,key);}
export function vendorApi(path:string,method='GET',body?:unknown,key?:string){return request('/api/v1/vendor'+path,method,body,key);}
export function platformApi(path:string,method='GET',body?:unknown){return request('/api/v1/platform'+path,method,body);}
