export class MarketplaceError extends Error { constructor(public status: number) { super(`Request failed (${status}).`); } }
export async function marketplaceApi(path: string, method = 'GET', body?: unknown) {
  let token: string | undefined;
  if (method !== 'GET') token = (await marketplaceApi('/api/customer/csrf')).token;
  const response = await fetch(path, { method, credentials: 'same-origin', headers: {
    'Content-Type': 'application/json', ...(token ? { 'X-XSRF-TOKEN': token } : {})
  }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  if (!response.ok) throw new MarketplaceError(response.status);
  return response.json();
}
