import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import Shell from './Shell';

const { mockUseSession } = vi.hoisted(() => ({ mockUseSession: vi.fn() }));
vi.mock('./session', () => ({ useSession: mockUseSession, signOut: vi.fn() }));

const posPermissions = [
  'POS_VIEW', 'POS_SESSION_OPEN', 'POS_SALE_CREATE', 'POS_REPORT_VIEW',
  'POS_RECEIPT_VIEW', 'POS_RETURN_VIEW', 'POS_REFUND_VIEW',
  'PRODUCT_VIEW', 'INVENTORY_VIEW', 'INVENTORY_RECEIVE',
  'POS_SHIFT_VARIANCE_APPROVE', 'POS_TENDER_MANUAL_VERIFY',
  'TENANT_PROFILE_VIEW', 'POS_CASHIER_REPORT_VIEW',
];

beforeEach(() => {
  mockUseSession.mockReturnValue({status:'anonymous',loading:false,refreshing:false,account:null,error:null});
});
afterEach(() => { cleanup(); window.history.replaceState({}, '', '/'); });

function grantPermissions(permissions: string[]) {
  const actorType = permissions.includes('CUSTOMER') ? 'CUSTOMER' : permissions.some(p=>p.startsWith('PLATFORM_')) ? 'PLATFORM' : 'VENDOR';
  const tenantId = actorType === 'VENDOR' ? 1 : null;
  const branchId = actorType === 'VENDOR' ? 10 : null;
  const membershipId=actorType==='VENDOR'?String(tenantId)+':test-user':null;
  mockUseSession.mockReturnValue({status:'authenticated',loading:false,refreshing:false,
    account:{username:'test-user',tenantId,branchId,membershipId,permissions,actorType,availableActorTypes:[actorType]},error:null});
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
  mockUseSession.mockReturnValue({status:'anonymous',loading:false,refreshing:false,account:null,error:null});
  render(<Shell mode="online"><section><h2>Public vendor directory</h2></section></Shell>);
  expect(await screen.findByText('Public vendor directory')).toBeTruthy();
});

it('keeps public discovery breadcrumbs aligned with the route without exposing customer navigation', async () => {
  window.history.replaceState({}, '', '/vendors/solar-partner');
  mockUseSession.mockReturnValue({status:'anonymous',loading:false,refreshing:false,account:null,error:null});
  render(<Shell mode="online"><section><h2>Public storefront content</h2></section></Shell>);
  expect(await screen.findByText('Public storefront content')).toBeTruthy();
  const breadcrumbs = within(screen.getByRole('navigation', { name: 'Breadcrumb' }));
  expect(breadcrumbs.getByRole('link', { name: 'Marketplace' })).toBeTruthy();
  expect(breadcrumbs.getByRole('link', { name: 'Shop' }).getAttribute('href')).toBe('/marketplace/vendors');
  expect(breadcrumbs.getByText('Vendor Catalogue')).toBeTruthy();
  expect(screen.queryByRole('link', { name: 'My Single-Vendor Cart' })).toBeNull();
  expect(screen.getByRole('heading', { name: 'Vendor Catalogue', level: 1 })).toBeTruthy();
});

it('fails closed while permissions are loading and after an unresolved permission error', async () => {
  window.history.replaceState({}, '', '/vendor/pos/products');
  mockUseSession.mockReturnValue({status:'loading',loading:true,refreshing:true,account:null,error:null});
  const view = render(<Shell mode="pos"><section><h2>Protected product content</h2></section></Shell>);
  expect(screen.getByRole('status').textContent).toContain('Checking your workspace access');
  expect(screen.queryByText('Protected product content')).toBeNull();
  expect(screen.queryByRole('link', { name: 'Manage Products' })).toBeNull();
  mockUseSession.mockReturnValue({status:'error',loading:false,refreshing:false,account:null,error:new Error('Session endpoint unavailable')});
  view.rerender(<Shell mode="pos"><section><h2>Protected product content</h2></section></Shell>);
  expect(screen.getByRole('alert').textContent).toContain('Access denied');
  expect(screen.queryByText('Protected product content')).toBeNull();
  expect(screen.queryByRole('link', { name: 'Manage Products' })).toBeNull();
});

it('keeps public marketplace discovery visible while session restoration is pending',()=>{
  window.history.replaceState({}, '', '/marketplace/vendors');
  mockUseSession.mockReturnValue({status:'loading',loading:true,refreshing:true,account:null,error:null});
  render(<Shell mode="online"><section><h2>Public discovery while restoring session</h2></section></Shell>);
  expect(screen.getByText('Public discovery while restoring session')).toBeTruthy();
  expect(screen.queryByRole('link',{name:'My Single-Vendor Cart'})).toBeNull();
});

it('preserves mounted page state when the mobile navigation is opened and closed', () => {
  grantPermissions(posPermissions);
  render(<Shell mode="pos"><label>Draft note<input aria-label="Draft note" defaultValue="unsaved value" /></label></Shell>);
  const field = screen.getByRole('textbox', { name: 'Draft note' });
  fireEvent.change(field, { target: { value: 'changed but not saved' } });
  const toggle = screen.getByRole('button', { name: 'Open workspace menu' });
  fireEvent.click(toggle);
  fireEvent.click(screen.getByRole('button', { name: 'Close workspace navigation' }));
  expect(screen.getByRole('textbox', { name: 'Draft note' })).toBe(field);
  expect((screen.getByRole('textbox', { name: 'Draft note' }) as HTMLInputElement).value).toBe('changed but not saved');
});

it('removes vendor navigation immediately when the active actor changes to CUSTOMER', async () => {
  window.history.replaceState({}, '', '/vendor/pos/products');
  grantPermissions(posPermissions);
  const view = render(<Shell mode="pos"><section><h2>Vendor-only product area</h2></section></Shell>);
  expect(await screen.findByRole('link', { name: 'Manage Products' })).toBeTruthy();
  grantPermissions(['CUSTOMER']);
  view.rerender(<Shell mode="pos"><section><h2>Vendor-only product area</h2></section></Shell>);
  expect(screen.getByRole('alert').textContent).toContain('Access denied');
  expect(screen.queryByRole('link', { name: 'Manage Products' })).toBeNull();
  expect(screen.queryByText('Vendor-only product area')).toBeNull();
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


it('keeps the parent navigation item active for nested direct routes and refresh URLs', async () => {
  window.history.replaceState({}, '', '/vendor/pos/products/SKU-204/details?tab=stock');
  grantPermissions(posPermissions);
  render(<Shell mode="pos"><section><h2>Nested product details</h2></section></Shell>);
  const current = await screen.findByRole('link', { name: 'Manage Products' });
  expect(current.getAttribute('aria-current')).toBe('page');
  expect(screen.getByText('Current section')).toBeTruthy();
  expect(screen.getByText('Current page')).toBeTruthy();
  const breadcrumbs = within(screen.getByRole('navigation', { name: 'Breadcrumb' }));
  expect(breadcrumbs.getByText('Store POS')).toBeTruthy();
  expect(breadcrumbs.getByRole('link', { name: 'Catalogue & Inventory' }).getAttribute('href')).toBe('/vendor/pos/categories');
  expect(breadcrumbs.getByText('Manage Products').closest('li')?.getAttribute('aria-current')).toBe('page');
});

it('moves focus into the mobile drawer, traps tab navigation and restores focus on Escape', async () => {
  window.history.replaceState({}, '', '/marketplace/vendors');
  grantPermissions(['CUSTOMER']);
  render(<Shell mode="online"><section><h2>Vendor directory content</h2></section></Shell>);
  const toggle = screen.getByRole('button', { name: 'Open workspace menu' });
  fireEvent.click(toggle);
  const close = screen.getByRole('button', { name: 'Close workspace navigation' });
  expect(screen.getByRole('dialog', { name: 'Workspace menu' }).getAttribute('aria-modal')).toBe('true');
  expect(close.getAttribute('aria-expanded')).toBeNull();
  expect(toggle.getAttribute('aria-expanded')).toBe('true');
  expect(document.activeElement).toBe(close);

  fireEvent.keyDown(document, { key: 'Tab', shiftKey: true });
  expect(document.activeElement).toBe(screen.getByRole('link', { name: 'Your Purchases' }));
  fireEvent.keyDown(document, { key: 'Tab' });
  expect(document.activeElement).toBe(close);

  fireEvent.keyDown(document, { key: 'Escape' });
  expect(toggle.getAttribute('aria-expanded')).toBe('false');
  expect(document.activeElement).toBe(toggle);
  expect(screen.getByText('Vendor directory content')).toBeTruthy();
});

it('closes the mobile drawer after navigation and focuses the destination content', async () => {
  grantPermissions(['CUSTOMER']);
  render(<Shell mode="online"><section><h2>Marketplace content remains mounted</h2></section></Shell>);
  const toggle = screen.getByRole('button', { name: 'Open workspace menu' });
  fireEvent.click(toggle);
  fireEvent.click(screen.getByRole('link', { name: 'My Single-Vendor Cart' }));
  await waitFor(() => expect(window.location.pathname).toBe('/marketplace/cart'));
  expect(screen.getByRole('button', { name: 'Open workspace menu' }).getAttribute('aria-expanded')).toBe('false');
  expect(document.activeElement).toBe(document.getElementById('main-content'));
  expect(screen.getByText('Marketplace content remains mounted')).toBeTruthy();
});

it('keeps platform and vendor/customer navigation actor contexts separate', async () => {
  window.history.replaceState({}, '', '/vendor/pos');
  grantPermissions(['PLATFORM_ANALYTICS_VIEW']);
  render(<Shell mode="pos"><section><h2>Platform must not enter POS</h2></section></Shell>);
  await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('Access denied'));
  expect(screen.queryByRole('link', { name: 'Cashier Session' })).toBeNull();
});

it('does not mark a default first menu item active for an unknown deep link', async () => {
  window.history.replaceState({}, '', '/vendor/pos/not-a-route');
  grantPermissions(posPermissions);
  render(<Shell mode="pos"><section><h2>Unknown route content</h2></section></Shell>);
  await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('Access denied'));
  expect(screen.queryByRole('link', { name: 'Cashier Session' })?.getAttribute('aria-current')).toBeNull();
});
