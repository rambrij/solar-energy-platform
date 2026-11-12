import { afterEach, expect, it, vi } from 'vitest';
import { marketplaceApi } from './marketplaceApi';
afterEach(() => vi.unstubAllGlobals());
it('fetches CSRF before a customer mutation and includes credentials/header', async () => {
  const fetcher = vi.fn().mockResolvedValueOnce({ ok: true, json: async () => ({ token: 'csrf-token' }) }).mockResolvedValueOnce({ ok: true, json: async () => ({ id: 'cart' }) });
  vi.stubGlobal('fetch', fetcher);
  await marketplaceApi('/api/customer/cart/items', 'POST', { productId: 1 });
  expect(fetcher.mock.calls[0][0]).toBe('/api/customer/csrf');
  expect(fetcher.mock.calls[1][1]).toMatchObject({ method: 'POST', credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': 'csrf-token' } });
});
