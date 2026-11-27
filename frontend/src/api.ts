import {authenticatedFetch, sessionGeneration} from './session';
import {jsonRequestOptions, readJsonResponse} from './jsonRequest';
export class ApiError extends Error {constructor(public status:number,public code?:string,detail?:string){super(detail??`Request failed (${status}).`);}}

async function requestCsrfToken(): Promise<string> {
 const response=await authenticatedFetch('/api/v1/auth/csrf',{credentials:'same-origin',cache:'no-store'});
 if(!response.ok)throw new ApiError(response.status);
 const value=await response.json();
 if(typeof value?.token!=='string'||!value.token)throw new Error('The CSRF token response was invalid.');
 return value.token;
}

async function request(url:string,method:string,body?:unknown,key?:string){
 const current=sessionGeneration();
 const csrf=/^(GET|HEAD|OPTIONS|TRACE)$/i.test(method)?undefined:await requestCsrfToken();
 if(current!==sessionGeneration())throw new Error('The session changed before the request could be sent.');
 const response=await authenticatedFetch(url,{method,...jsonRequestOptions(body,csrf,key)});
 return readJsonResponse(response,ApiError,true);
}
export function authPost(path:string,body:unknown){return request('/api/v1/auth'+path,'POST',body);}
export function api(path:string,body?:unknown,key?:string){return request('/api/v1/vendor/pos'+path,body===undefined?'GET':'POST',body,key);}
export function vendorApi(path:string,method='GET',body?:unknown,key?:string){return request('/api/v1/vendor'+path,method,body,key);}
export function platformApi(path:string,method='GET',body?:unknown){return request('/api/v1/platform'+path,method,body);}

export async function vendorUpload(path:string,file:File,altText:string,reason:string,customerVisible=false){
 const current=sessionGeneration();
 const csrf=await requestCsrfToken();
 if(current!==sessionGeneration())throw new Error('The session changed before the upload could be sent.');
 const form=new FormData();form.append('file',file);form.append('altText',altText);form.append('reason',reason);form.append('customerVisible',String(customerVisible));
 const response=await authenticatedFetch('/api/v1/vendor'+path,{method:'POST',credentials:'same-origin',headers:{'X-XSRF-TOKEN':csrf},body:form});
 return readJsonResponse(response,ApiError,true);
}
