import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import CustomerDashboard from './CustomerDashboard';
import { marketplaceApi } from './marketplaceApi';

vi.mock('./marketplaceApi', async original => ({ ...await original<typeof import('./marketplaceApi')>(), marketplaceApi: vi.fn() }));

const profile = { id: 12, displayName: 'Asha Rao', email: 'asha@example.test' };
const orders = { page: 0, size: 5, totalElements: 2, totalPages: 1, items: [
  { id: 'order-a', vendorName: 'Vendor A', channel: 'ONLINE', status: 'PAID', paymentState: 'CONFIRMED', createdAt: '2026-11-20T10:00:00Z', total: 1250, receiptNumber: 301 },
  { id: 'order-b', vendorName: 'Vendor B', channel: 'IN_STORE', status: 'PAID', paymentState: 'CONFIRMED', createdAt: '2026-11-18T10:00:00Z', total: 840, receiptNumber: 77 },
] };
const cart = { id: 'cart-a', vendorSlug: 'vendor-a', vendorName: 'Vendor A', items: [{ productId: 1, name: 'Solar panel', quantity: 2, available: true }], total: 2200, checkoutAvailable: false, message: 'Payment is not configured.' };

beforeEach(() => vi.mocked(marketplaceApi).mockImplementation(async path => {
  if (path === '/api/v1/customer/profile') return profile;
  if (path.startsWith('/api/v1/customer/orders')) return orders;
  if (path === '/api/v1/customer/cart') return cart;
  if (path === '/api/v1/customer/shipments') return [{id:8,orderId:'order-a',vendorName:'Vendor A',carrier:'Solar Carrier',trackingReference:'TRK-8',trackingUrl:'https://carrier.test/TRK-8',createdAt:'2026-11-24T10:00:00Z',events:[{id:9,eventCode:'IN_TRANSIT',description:'Package moving',occurredAt:'2026-11-25T10:00:00Z'}]}];
  throw new Error('Unexpected dashboard request: ' + path);
}));
afterEach(() => { cleanup(); vi.resetAllMocks(); });

it('combines only the signed-in customer profile, cross-vendor orders and current cart', async () => {
  render(<CustomerDashboard/>);
  expect(await screen.findByRole('heading', { name: 'Welcome back, Asha Rao' })).toBeTruthy();
  expect(screen.getByText('Orders across vendors').parentElement?.textContent).toContain('2');
  expect(screen.getAllByText('Vendor A').length).toBeGreaterThan(0);
  expect(screen.getByText('Vendor B')).toBeTruthy();
  expect(screen.getByText('asha@example.test')).toBeTruthy();
  expect(screen.getByText('Current estimate: 2200.00')).toBeTruthy();
  expect(await screen.findByText('Shipment updates')).toBeTruthy();
  expect(screen.getByText('Solar Carrier')).toBeTruthy();
  expect(screen.getByText(/Package moving/)).toBeTruthy();
  expect(marketplaceApi).toHaveBeenCalledWith('/api/v1/customer/shipments');
  expect(marketplaceApi).toHaveBeenCalledWith('/api/v1/customer/profile');
  expect(marketplaceApi).toHaveBeenCalledWith('/api/v1/customer/orders?page=0&size=5&status=ALL&reference=');
  expect(marketplaceApi).toHaveBeenCalledWith('/api/v1/customer/cart');
});

it('explains empty order and cart states without implying a reservation', async () => {
  vi.mocked(marketplaceApi).mockImplementation(async path => {
    if (path === '/api/v1/customer/profile') return { ...profile, displayName: null };
    if (path.startsWith('/api/v1/customer/orders')) return { ...orders, items: [], totalElements: 0, totalPages: 0 };
    if (path === '/api/v1/customer/cart') return { ...cart, id: null, vendorSlug: null, vendorName: null, items: [], total: 0 };
    if (path === '/api/v1/customer/shipments') return [];
    throw new Error('Unexpected dashboard request: ' + path);
  });
  render(<CustomerDashboard/>);
  expect(await screen.findByText('No orders yet')).toBeTruthy();
  expect(screen.getByText('Your cart is empty. Choose a vendor to start a new cart.')).toBeTruthy();
  expect(screen.getByText('Display name not set')).toBeTruthy();
  expect(await screen.findByText('No shipments have been registered for your paid online orders yet.')).toBeTruthy();
});

it('keeps the dashboard usable when shipment feed fails and allows an independent retry',async()=>{
 vi.mocked(marketplaceApi).mockImplementation(async path=>path==='/api/v1/customer/profile'?profile:path.startsWith('/api/v1/customer/orders')?orders:path==='/api/v1/customer/cart'?cart:Promise.reject(new Error('feed unavailable')));
 render(<CustomerDashboard/>);expect(await screen.findByRole('heading',{name:'Welcome back, Asha Rao'})).toBeTruthy();
 expect((await screen.findByRole('alert')).textContent).toContain('Shipment updates are temporarily unavailable');
 vi.mocked(marketplaceApi).mockImplementation(async path=>path==='/api/v1/customer/shipments'?[{id:9,orderId:'order-b',vendorName:'Vendor B',carrier:'Carrier B',trackingReference:'B-9',trackingUrl:null,createdAt:'2026-11-25T10:00:00Z',events:[]}]:path==='/api/v1/customer/profile'?profile:path.startsWith('/api/v1/customer/orders')?orders:cart);
 fireEvent.click(screen.getByRole('button',{name:'Retry shipment updates'}));expect(await screen.findByText('Carrier B')).toBeTruthy();
});

it('provides a retry when any customer dashboard source is unavailable', async () => {
  vi.mocked(marketplaceApi).mockRejectedValueOnce(new Error('network unavailable'));
  render(<CustomerDashboard/>);
  expect((await screen.findByRole('alert')).textContent).toContain('Your dashboard could not be loaded');
  vi.mocked(marketplaceApi).mockImplementation(async path => path === '/api/v1/customer/profile' ? profile : path.startsWith('/api/v1/customer/orders') ? orders : cart);
  screen.getByRole('button', { name: 'Retry dashboard' }).click();
  await waitFor(() => expect(screen.getByRole('heading', { name: 'Welcome back, Asha Rao' })).toBeTruthy());
});
