import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import Shell from './Shell';

const { mockAuthApi } = vi.hoisted(() => ({ mockAuthApi: vi.fn() }));
vi.mock('./api', () => ({ authApi: mockAuthApi }));

const posPermissions = [
  'POS_VIEW', 'POS_SESSION_OPEN', 'POS_SALE_CREATE', 'POS_REPORT_VIEW',
  'POS_RECEIPT_VIEW', 'POS_RETURN_VIEW', 'POS_REFUND_VIEW',
  'PRODUCT_VIEW', 'INVENTORY_VIEW', 'INVENTORY_RECEIVE',
  'POS_SHIFT_VARIANCE_APPROVE', 'POS_TENDER_MANUAL_VERIFY',
  'TENANT_PROFILE_VIEW', 'POS_CASHIER_REPORT_VIEW',
];

beforeEach(() => { mockAuthApi.mockReset(); });
afterEach(() => { cleanup(); window.history.replaceState({}, '', '/'); });

function grantPermissions(permissions: string[]) {
  mockAuthApi.mockResolvedValue({
    username: 'test-user',
    tenantId: permissions.includes('CUSTOMER') ? null : 1,
    branchId: permissions.includes('CUSTOMER') ? null : 10,
    permissions,
  });
}

it('renders route-based links, active state and navigable breadcrumbs', async () => {
  window.history.replaceState({}, '', '/vendor/pos/products');
  grantPermissions(posPermissions);
  render(<Shell mode="pos"><section><h2>Manage product catalogue</h2></section></Shell>);
  await waitFor(() => expect(screen.getByRole('link', { name: 'Manage Products' })).toBeTruthy());
  expect(screen.getByRole('link', { name: 'Manage Products' }).getAttribute('href')).toBe('/vendor/pos/products');
  expect(screen.getByRole('link', { name: 'Manage Products' }).getAttribute('aria-current')).toBe('page');
  expect(within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByText('Catalogue & Inventory')).toBeTruthy();
  expect(screen.queryByRole('navigation', { name: 'Workspace tabs' })).toBeNull();
});

it('blocks direct navigation to a POS page without its exact permission', async () => {
  window.history.replaceState({}, '', '/vendor/pos/products');
  grantPermissions(['POS_VIEW']);
  render(<Shell mode="pos"><section><h2>Protected product catalogue</h2></section></Shell>);
  await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('Access denied'));
  expect(screen.queryByText('Protected product catalogue')).toBeNull();
});

it('keeps public vendor discovery available without a customer login', async () => {
  window.history.replaceState({}, '', '/marketplace/vendors');
  mockAuthApi.mockRejectedValue(new Error('Not signed in'));
  render(<Shell mode="online"><section><h2>Public vendor directory</h2></section></Shell>);
  expect(await screen.findByText('Public vendor directory')).toBeTruthy();
});

it('updates the URL and selected workspace using sidebar navigation', async () => {
  grantPermissions(['CUSTOMER']);
  render(<Shell mode="online"><section><h2>Marketplace page</h2></section></Shell>);
  await waitFor(() => expect(screen.getByRole('link', { name: 'My Single-Vendor Cart' })).toBeTruthy());
  fireEvent.click(screen.getByRole('link', { name: 'My Single-Vendor Cart' }));
  await waitFor(() => expect(screen.getByRole('link', { name: 'My Single-Vendor Cart' }).getAttribute('aria-current')).toBe('page'));
  expect(window.location.pathname).toBe('/marketplace/cart');
  expect(screen.getByRole('link', { name: 'My Single-Vendor Cart' }).getAttribute('aria-current')).toBe('page');
});


it('allows a vendor employee with a narrow page grant to enter without POS_VIEW', async () => {
  window.history.replaceState({}, '', '/vendor/pos/products');
  grantPermissions(['PRODUCT_VIEW']);
  render(<Shell mode="pos"><section><h2>Product catalogue access</h2></section></Shell>);
  await waitFor(() => expect(screen.getByText('Product catalogue access')).toBeTruthy());
  expect(screen.queryByRole('alert')).toBeNull();
});
