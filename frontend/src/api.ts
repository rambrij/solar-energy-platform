let csrf = '';
export class ApiError extends Error { constructor(public status:number) {super(`Request failed (${status}).`);} }
export async function api(path: string, body?: unknown, key?: string) {
  if (body !== undefined && !csrf) csrf = (await api('/csrf')).token;
  const response = await fetch('/api/vendor/pos' + path, {
    method: body === undefined ? 'GET' : 'POST', credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...(csrf ? {'X-XSRF-TOKEN':csrf} : {}), ...(key ? {'Idempotency-Key':key} : {}) },
    ...(body === undefined ? {} : {body:JSON.stringify(body)})
  });
  if (!response.ok) throw new ApiError(response.status);
  return response.json();
}
