export function jsonRequestOptions(body?: unknown, csrf?: string, key?: string): RequestInit {
  return {
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
      ...(key ? { 'Idempotency-Key': key } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  };
}

type RequestError = new (status: number, code?: string, detail?: string) => Error;

export async function readJsonResponse(
  response: Response,
  ErrorType: RequestError,
  allowNoContent = false,
) {
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new ErrorType(response.status, problem?.code, problem?.detail);
  }
  return allowNoContent && response.status === 204 ? undefined : response.json();
}
