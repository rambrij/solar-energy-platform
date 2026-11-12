import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import Marketplace from './Marketplace';
import { marketplaceApi, MarketplaceError } from './marketplaceApi';
vi.mock('./marketplaceApi', async original => ({ ...await original<typeof import('./marketplaceApi')>(), marketplaceApi: vi.fn() }));
const vendors = [{ id: 1, slug: 'vendor-1', name: 'Vendor A' }, { id: 2, slug: 'vendor-2', name: 'Vendor B' }];
const products = [{ id: 1, sku: 'P1', name: 'Solar Panel', price: 100, taxRate: 10, inStock: true }];
const gate = 'Online payment is not configured. No paid order was created.';
const cart = { id: 'cart-a', vendorSlug: 'vendor-1', vendorName: 'Vendor A', items: [{ productId: 1, name: 'Solar Panel', quantity: 1, unitPrice: 100, tax: 10, total: 110, available: true }], total: 110, checkoutAvailable: false, message: gate };
beforeEach(() => { window.history.replaceState({}, '', '/vendors/vendor-1'); });
afterEach(() => { cleanup(); vi.resetAllMocks(); window.history.replaceState({}, '', '/'); });
function publicResult(path: string) { return path === '/api/marketplace/vendors' ? vendors : products; }
it('browses publicly without requesting a customer account or cart', async () => {
  vi.mocked(marketplaceApi).mockImplementation(async path => publicResult(path));
  render(<Marketplace />); await screen.findByText('Solar Panel');
  expect(vi.mocked(marketplaceApi).mock.calls.every(call => call[0].startsWith('/api/marketplace/'))).toBe(true);
  expect(screen.getByText('Vendor B').getAttribute('href')).toBe('/vendors/vendor-2');
});
it('uses server cart totals and blocks payment completion at the provider gate', async () => {
  vi.mocked(marketplaceApi).mockImplementation(async path => path.startsWith('/api/customer/') ? cart : publicResult(path));
  render(<Marketplace />); fireEvent.click(await screen.findByText('Add or update Solar Panel in cart'));
  await screen.findByText(/Current estimate: 110.00/);
  fireEvent.click(screen.getByText('Preview checkout'));
  await waitFor(() => expect(marketplaceApi).toHaveBeenCalledWith('/api/customer/cart/quote', 'POST', {}));
  expect(screen.getAllByText(gate).length).toBeGreaterThan(0);
  expect(screen.queryByRole('button', { name: /^pay/i })).toBeNull();
});
it('requires explicit confirmation before switching vendor carts', async () => {
  const original = { ...cart, id: 'cart-b', vendorSlug: 'vendor-2', vendorName: 'Vendor B' };
  vi.mocked(marketplaceApi).mockImplementation(async path => {
    if (path === '/api/customer/cart/items') throw new MarketplaceError(409);
    if (path === '/api/customer/cart') return original;
    if (path === '/api/customer/cart/switch') return cart;
    return publicResult(path);
  });
  render(<Marketplace />); fireEvent.click(await screen.findByText('Add or update Solar Panel in cart'));
  await screen.findByRole('dialog');
  expect(vi.mocked(marketplaceApi).mock.calls.some(call => call[0].endsWith('/switch'))).toBe(false);
  fireEvent.click(screen.getByText('Keep current cart')); expect(screen.queryByRole('dialog')).toBeNull();
  fireEvent.click(screen.getByText('Add or update Solar Panel in cart')); await screen.findByRole('dialog');
  fireEvent.click(screen.getByText('Confirm vendor switch'));
  await waitFor(() => expect(marketplaceApi).toHaveBeenCalledWith('/api/customer/cart/switch', 'POST', { expectedCartId: 'cart-b', vendorSlug: 'vendor-1', productId: 1, quantity: 1 }));
});
it('explains customer authorization failures without clearing a cart', async () => {
  vi.mocked(marketplaceApi).mockImplementation(async path => { if (path.startsWith('/api/customer/')) throw new MarketplaceError(403); return publicResult(path); });
  render(<Marketplace />); fireEvent.click(screen.getByText('Sign in / reload my cart'));
  await screen.findByText(/Staff credentials are separate/);
  expect(screen.queryByRole('dialog')).toBeNull();
});
it('reports changed stock at preview rather than claiming an order was completed', async () => {
  vi.mocked(marketplaceApi).mockImplementation(async path => { if (path.endsWith('/quote')) throw new MarketplaceError(409); return path.startsWith('/api/customer/') ? cart : publicResult(path); });
  render(<Marketplace />); fireEvent.click(screen.getByText('Sign in / reload my cart')); await screen.findByText('Preview checkout');
  fireEvent.click(screen.getByText('Preview checkout')); await screen.findByText(/A cart item is unavailable/);
  expect(screen.getByText(/Current estimate: 110.00/)).toBeTruthy();
});
it('loads own archived carts and requires confirmation before restoring', async () => {
  const archived = { ...cart, id: 'saved-cart', vendorName: 'Saved Vendor' };
  vi.mocked(marketplaceApi).mockImplementation(async path => {
    if (path === '/api/customer/cart/history') return [archived];
    return path.startsWith('/api/customer/') ? cart : publicResult(path);
  });
  render(<Marketplace />); fireEvent.click(screen.getByText('Load archived carts'));
  fireEvent.click(await screen.findByText('Restore Saved Vendor cart'));
  await screen.findByRole('dialog');
  expect(vi.mocked(marketplaceApi).mock.calls.some(call => call[0].endsWith('/restore'))).toBe(false);
  fireEvent.click(screen.getByText('Keep active cart')); expect(screen.queryByRole('dialog')).toBeNull();
  fireEvent.click(screen.getByText('Restore Saved Vendor cart')); fireEvent.click(screen.getByText('Confirm cart restore'));
  await screen.findByText(/Archived cart restored with current prices/);
  expect(marketplaceApi).toHaveBeenCalledWith('/api/customer/cart/restore', 'POST', { archivedCartId: 'saved-cart', expectedCartId: 'cart-a' });
});
it('keeps active cart contents after a stale restore is rejected', async () => {
  vi.mocked(marketplaceApi).mockImplementation(async path => {
    if (path === '/api/customer/cart/history') return [{ ...cart, id: 'saved-cart', vendorName: 'Saved Vendor' }];
    if (path === '/api/customer/cart/restore') throw new MarketplaceError(409);
    return path.startsWith('/api/customer/') ? cart : publicResult(path);
  });
  render(<Marketplace />); fireEvent.click(screen.getByText('Load archived carts'));
  fireEvent.click(await screen.findByText('Restore Saved Vendor cart')); fireEvent.click(screen.getByText('Confirm cart restore'));
  await screen.findByText(/The cart changed or archived products are unavailable/);
  expect(screen.getByText(/Current estimate: 110.00/)).toBeTruthy();
  expect(screen.queryByText(/Archived cart restored with current prices/)).toBeNull();
});
