import { afterEach, expect, it, vi } from 'vitest';
import { marketplaceApi } from './marketplaceApi';
afterEach(() => vi.unstubAllGlobals());
it('fetches CSRF before a customer mutation and includes credentials/header', async () => {
  const fetcher = vi.fn().mockResolvedValueOnce({ ok: true, json: async () => ({ token: 'csrf-token' }) }).mockResolvedValueOnce({ ok: true, json: async () => ({ id: 'cart' }) });
  vi.stubGlobal('fetch', fetcher);
  await marketplaceApi('/api/v1/customer/cart/items', 'POST', { productId: 1 });
  expect(fetcher.mock.calls[0][0]).toBe('/api/v1/customer/csrf');
  expect(fetcher.mock.calls[1][1]).toMatchObject({ method: 'POST', credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': 'csrf-token' } });
});

it('uses the public marketplace CSRF endpoint for a public enquiry mutation', async () => {
  const fetcher = vi.fn()
    .mockResolvedValueOnce({ ok: true, json: async () => ({ token: 'public-csrf' }) })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ id: 44, status: 'NEW' }) });
  vi.stubGlobal('fetch', fetcher);
  await marketplaceApi('/api/v1/marketplace/vendors/vendor-a/enquiries', 'POST', { consent: true });
  expect(fetcher.mock.calls[0][0]).toBe('/api/v1/marketplace/csrf');
  expect(fetcher.mock.calls[1][1]).toMatchObject({
    method: 'POST', credentials: 'same-origin',
    headers: { 'X-XSRF-TOKEN': 'public-csrf' }
  });
});
