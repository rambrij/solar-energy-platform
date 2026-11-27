import {authenticatedFetch} from './session';
import {jsonRequestOptions, readJsonResponse} from './jsonRequest';
export class MarketplaceError extends Error { constructor(public status:number, public code?:string, detail?:string) {super(detail ?? `Request failed (${status}).`);} }
export async function marketplaceApi(path: string, method = 'GET', body?: unknown, key?: string) {
  let token: string | undefined;
  if (method !== 'GET') {
    token = path.startsWith('/api/v1/marketplace/')
      ? (await marketplaceApi('/api/v1/marketplace/csrf')).token
      : (await marketplaceApi('/api/v1/customer/csrf')).token;
  }
  const response = await authenticatedFetch(path, { method, ...jsonRequestOptions(body, token, key) });
  return readJsonResponse(response, MarketplaceError);
}

/** Download a protected PDF through the same authenticated transport used by marketplace APIs. */
export async function downloadProtectedPdf(path:string, filename:string) {
  const response=await authenticatedFetch(path,{method:'GET',cache:'no-store'});
  if(!response.ok) return readJsonResponse(response,MarketplaceError);
  const contentType=response.headers.get('content-type')??'';
  if(!contentType.toLowerCase().includes('application/pdf'))
    throw new Error('The document response was not a PDF.');
  const blob=await response.blob();
  if(blob.size<5||blob.size>2_000_000)
    throw new Error('The quotation PDF is empty or exceeds the 2 MB document limit.');
  const objectUrl=URL.createObjectURL(blob);
  try {
    const link=document.createElement('a');
    link.href=objectUrl;
    link.download=filename;
    link.rel='noopener';
    document.body.appendChild(link);
    link.click();
    link.remove();
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}
