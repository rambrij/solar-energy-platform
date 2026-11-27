import { expect, it } from 'vitest';
import { availableNavigation, navigationItems, navigationPath, resolveWorkspaceRoute, selectedNavigation } from './navigation';

it('groups unique workspace destinations without exposing unrelated modules', () => {
  for (const mode of ['pos', 'online'] as const) {
    const items = navigationItems(mode);
    expect(new Set(items.map(item => item.id)).size).toBe(items.length);
    expect(new Set(items.map(item => item.path)).size).toBe(items.length);
  }
  expect(navigationItems('online').some(item => item.id === 'cash-refunds')).toBe(false);
  expect(navigationItems('pos').some(item => item.id === 'vendor-customer-crm')).toBe(true);
  expect(navigationItems('pos').some(item => item.id === 'shipment-management')).toBe(true);
  expect(navigationItems('pos').some(item => item.id === 'vendor-profile')).toBe(true);
  expect(navigationItems('pos').some(item => item.id === 'vendor-site-surveys')).toBe(true);
  expect(navigationItems('pos').some(item => item.id === 'solar-package-management')).toBe(true);
  expect(navigationItems('pos').some(item => item.id === 'vendor-service-visits')).toBe(true);
  expect(navigationItems('online').some(item => item.id === 'customer-service-visits')).toBe(true);
  expect(availableNavigation('pos', new Set(['checkout', 'order-history'])).map(item => item.id)).toEqual(['checkout', 'order-history']);
});

it('resolves direct workspace paths and legacy section fragments', () => {
  expect(resolveWorkspaceRoute('pos', '/vendor/customers', '')).toBe('vendor-customer-crm');
  expect(resolveWorkspaceRoute('pos', '/vendor/shipments', '')).toBe('shipment-management');
  expect(resolveWorkspaceRoute('pos', '/vendor/profile', '')).toBe('vendor-profile');
  expect(resolveWorkspaceRoute('pos', '/vendor/pos/site-surveys', '')).toBe('vendor-site-surveys');
  expect(resolveWorkspaceRoute('pos', '/vendor/pos/products', '')).toBe('product-management');
  expect(resolveWorkspaceRoute('pos', '/vendor/pos/solar-packages', '')).toBe('solar-package-management');
  expect(resolveWorkspaceRoute('pos', '/vendor/pos', '#cash-refunds')).toBe('cash-refunds');
  expect(resolveWorkspaceRoute('online', '/marketplace/cart', '')).toBe('customer-cart');
  expect(resolveWorkspaceRoute('online', '/marketplace/purchases', '')).toBe('customer-purchases');
  expect(resolveWorkspaceRoute('online', '/marketplace/dashboard', '')).toBe('customer-dashboard');
  expect(resolveWorkspaceRoute('online', '/vendors/vendor-1', '')).toBe('storefront-products');
});

it('keeps navigation paths stable for the current vendor storefront', () => {
  expect(navigationPath('online', 'storefront-products', '/vendors/vendor-1')).toBe('/vendors/vendor-1');
  expect(navigationPath('online', 'storefront-products', '/marketplace/cart')).toBe('/vendors');
  expect(navigationPath('online', 'vendor-directory', '/marketplace/cart')).toBe('/marketplace/vendors');
  expect(resolveWorkspaceRoute('online', '/marketplace/vendors', '')).toBe('vendor-directory');
  expect(selectedNavigation(navigationItems('pos'), 'cash-refunds')?.label).toBe('Cash Refunds');
});


it('matches nested pages to their deepest registered navigation parent', () => {
  expect(resolveWorkspaceRoute('pos', '/vendor/pos/products/SKU-204/details', '')).toBe('product-management');
  expect(resolveWorkspaceRoute('pos', '/vendor/pos/held-bills/RES-9/', '')).toBe('reserved-pos-checkout');
  expect(resolveWorkspaceRoute('online', '/marketplace/cart/review/ORDER-9', '')).toBe('customer-cart');
  expect(resolveWorkspaceRoute('online', '/vendors/solar-partner/products/panel-1', '')).toBe('storefront-products');
  expect(resolveWorkspaceRoute('pos', '/vendor/pos/products-old', '')).toBe('not-found');
  expect(resolveWorkspaceRoute('online', '/marketplace/cartography', '')).toBe('not-found');
});

it('normalizes trailing slashes and never selects the first menu item for unknown routes', () => {
  expect(resolveWorkspaceRoute('pos', '/vendor/pos/products/', '')).toBe('product-management');
  expect(resolveWorkspaceRoute('pos', '/vendor/service-visits', '')).toBe('vendor-service-visits');
  expect(resolveWorkspaceRoute('online', '/marketplace/service-visits', '')).toBe('customer-service-visits');
  expect(resolveWorkspaceRoute('online', '/marketplace/cart/', '')).toBe('customer-cart');
  expect(resolveWorkspaceRoute('online', '/marketplace/dashboard/', '')).toBe('customer-dashboard');
  expect(selectedNavigation(navigationItems('pos'), 'not-found')).toBeUndefined();
});
