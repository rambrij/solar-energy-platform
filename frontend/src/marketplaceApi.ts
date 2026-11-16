import {authenticatedFetch} from './session';
export class MarketplaceError extends Error { constructor(public status:number, public code?:string, detail?:string) {super(detail ?? `Request failed (${status}).`);} }
export async function marketplaceApi(path: string, method = 'GET', body?: unknown, key?: string) {
  let token: string | undefined;
  if (method !== 'GET') token = (await marketplaceApi('/api/v1/customer/csrf')).token;
  const response = await authenticatedFetch(path, { method, credentials: 'same-origin', headers: {
    'Content-Type': 'application/json', ...(token ? { 'X-XSRF-TOKEN': token } : {}), ...(key ? {'Idempotency-Key':key} : {})
  }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new MarketplaceError(response.status, problem.code, problem.detail);
  }
  return response.json();
}
