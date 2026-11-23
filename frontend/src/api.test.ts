import {afterEach, expect, it, vi} from 'vitest';
import {api, platformApi, vendorApi} from './api';
import {authenticatedFetch, sessionGeneration} from './session';

vi.mock('./session', () => ({authenticatedFetch: vi.fn(), sessionGeneration: vi.fn(() => 0)}));
const fetchRequest = vi.mocked(authenticatedFetch);
afterEach(() => fetchRequest.mockReset());

it('uses the current CSRF token for each mutation and preserves the idempotency key', async () => {
  fetchRequest
    .mockResolvedValueOnce(Response.json({token: 'first-token'}))
    .mockResolvedValueOnce(new Response(null, {status: 204}))
    .mockResolvedValueOnce(Response.json({token: 'rotated-token'}))
    .mockResolvedValueOnce(new Response(null, {status: 204}));

  await api('/sales', {quantity: 1}, 'retry-key');
  await platformApi('/support', 'DELETE');

  expect(fetchRequest).toHaveBeenNthCalledWith(1, '/api/v1/auth/csrf', {
    credentials: 'same-origin', cache: 'no-store',
  });
  expect(fetchRequest.mock.calls[1][1]).toMatchObject({
    headers: {'X-XSRF-TOKEN': 'first-token', 'Idempotency-Key': 'retry-key'},
  });
  expect(fetchRequest.mock.calls[2][0]).toBe('/api/v1/auth/csrf');
  expect(fetchRequest.mock.calls[3][1]).toMatchObject({
    method: 'DELETE', headers: {'X-XSRF-TOKEN': 'rotated-token'},
  });
});

it('does not request a CSRF token for a read', async () => {
  fetchRequest.mockResolvedValueOnce(Response.json([]));

  await vendorApi('/products');

  expect(fetchRequest).toHaveBeenCalledExactlyOnceWith('/api/v1/vendor/products', {
    method: 'GET', credentials: 'same-origin', headers: {'Content-Type': 'application/json'},
  });
});

it('does not send a mutation if the CSRF response does not contain a token', async () => {
  fetchRequest.mockResolvedValueOnce(Response.json({token: ''}));

  await expect(api('/sales', {quantity: 1})).rejects.toThrow('The CSRF token response was invalid.');
  expect(fetchRequest).toHaveBeenCalledTimes(1);
});

it('does not send a mutation when the actor changes during CSRF acquisition', async () => {
  vi.mocked(sessionGeneration).mockReturnValueOnce(0).mockReturnValueOnce(1);
  fetchRequest.mockResolvedValueOnce(Response.json({token: 'current-token'}));

  await expect(api('/sales', {quantity: 1})).rejects.toThrow('The session changed');
  expect(fetchRequest).toHaveBeenCalledTimes(1);
});
