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
