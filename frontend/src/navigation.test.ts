import { expect, it } from 'vitest';
import { availableNavigation, navigationItems, navigationPath, resolveWorkspaceRoute, selectedNavigation } from './navigation';

it('groups unique workspace destinations without exposing unrelated modules', () => {
  for (const mode of ['pos', 'online'] as const) {
    const items = navigationItems(mode);
    expect(new Set(items.map(item => item.id)).size).toBe(items.length);
    expect(new Set(items.map(item => item.path)).size).toBe(items.length);
  }
  expect(navigationItems('online').some(item => item.id === 'cash-refunds')).toBe(false);
  expect(availableNavigation('pos', new Set(['checkout', 'order-history'])).map(item => item.id)).toEqual(['checkout', 'order-history']);
});

it('resolves direct workspace paths and legacy section fragments', () => {
  expect(resolveWorkspaceRoute('pos', '/vendor/pos/products', '')).toBe('product-management');
  expect(resolveWorkspaceRoute('pos', '/vendor/pos', '#cash-refunds')).toBe('cash-refunds');
  expect(resolveWorkspaceRoute('online', '/marketplace/cart', '')).toBe('customer-cart');
  expect(resolveWorkspaceRoute('online', '/marketplace/purchases', '')).toBe('order-history');
  expect(resolveWorkspaceRoute('online', '/vendors/vendor-1', '')).toBe('storefront-products');
});

it('keeps navigation paths stable for the current vendor storefront', () => {
  expect(navigationPath('online', 'storefront-products', '/vendors/vendor-1')).toBe('/vendors/vendor-1');
  expect(navigationPath('online', 'storefront-products', '/marketplace/cart')).toBe('/vendors');
  expect(navigationPath('online', 'vendor-directory', '/marketplace/cart')).toBe('/marketplace/vendors');
  expect(resolveWorkspaceRoute('online', '/marketplace/vendors', '')).toBe('vendor-directory');
  expect(selectedNavigation(navigationItems('pos'), 'cash-refunds')?.label).toBe('Cash Refunds');
});
