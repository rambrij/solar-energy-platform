import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import OrderHistory from './OrderHistory';
import { api } from './api';
import { marketplaceApi } from './marketplaceApi';
vi.mock('./api', () => ({ api: vi.fn() }));
vi.mock('./marketplaceApi', () => ({ marketplaceApi: vi.fn() }));
const order = { id: '12345678-abcd-4321-abcd-123456789000', vendorName: 'Solar Partner', channel: 'IN_STORE', status: 'PAID', createdAt: '2026-11-13T09:00:00Z', expiresAt: null, total: 110, receiptNumber: 4, taxInvoice: true };
const page = { items: [order], page: 0, size: 10, totalElements: 11, totalPages: 2 };
afterEach(() => { cleanup(); vi.resetAllMocks(); });
it('does not request protected history until explicitly loaded', () => {
  render(<OrderHistory mode="online"/>);
  expect(api).not.toHaveBeenCalled(); expect(marketplaceApi).not.toHaveBeenCalled();
  expect(screen.getByText('My online orders')).toBeTruthy();
});
it('keeps pagination bound to applied filters rather than unsubmitted edits', async () => {
  vi.mocked(api).mockResolvedValue(page);
  render(<OrderHistory mode="pos"/>);
  fireEvent.change(screen.getByLabelText('Order status'), { target: { value: 'PAID' } });
  fireEvent.click(screen.getByText('Load order history'));
  await screen.findByText('Solar Partner');
  fireEvent.change(screen.getByLabelText('Order status'), { target: { value: 'CANCELLED' } });
  fireEvent.click(screen.getByText('Next orders'));
  await waitFor(() => expect(api).toHaveBeenLastCalledWith('/orders?status=PAID&reference=&page=1&size=10'));
});
it('loads customer snapshot details and never offers a POS receipt action', async () => {
  vi.mocked(marketplaceApi).mockImplementation(async path => path.includes('/orders/') ? { order: { ...order, channel: 'ONLINE', status: 'CANCELLED' }, items: [{ productId: 1, name: 'Original panel', quantity: 1, unitPrice: 100, tax: 10, total: 110 }], cashReceived: null, changeGiven: null, cancellationReason: 'Buying later', statusChangedAt: '2026-11-13T09:01:00Z' } : page);
  render(<OrderHistory mode="online"/>);
  fireEvent.click(screen.getByText('Load order history')); fireEvent.click(await screen.findByText(`View order ${order.id}`));
  await screen.findByText('Original panel'); await screen.findByText('Cancellation reason: Buying later');
  expect(marketplaceApi).toHaveBeenCalledWith(`/api/customer/orders/${order.id}`);
  expect(screen.queryByText('Open receipt 4')).toBeNull(); expect(api).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('Close order details')); expect(screen.queryByLabelText('Order details')).toBeNull();
});
it('recovers an existing paid receipt without creating a financial command', async () => {
  const receipt = { id: order.id, items: [], invoices: [] };
  vi.mocked(api).mockImplementation(async path => path.startsWith('/receipts/') ? receipt : page);
  const onReceipt = vi.fn(); render(<OrderHistory mode="pos" onReceipt={onReceipt}/>);
  fireEvent.click(screen.getByText('Load order history')); fireEvent.click(await screen.findByText('Open receipt 4'));
  await waitFor(() => expect(onReceipt).toHaveBeenCalledWith(receipt));
  expect(api).toHaveBeenCalledWith(`/receipts/${order.id}`);
});
it('rejects reversed dates locally and preserves the loaded page after a failed filter', async () => {
  vi.mocked(api).mockResolvedValueOnce(page).mockRejectedValueOnce(new Error('Denied'));
  render(<OrderHistory mode="pos"/>);
  fireEvent.click(screen.getByText('Load order history')); await screen.findByText('Solar Partner');
  fireEvent.change(screen.getByLabelText('From date (UTC)'), { target: { value: '2026-11-14' } });
  fireEvent.change(screen.getByLabelText('To date (UTC)'), { target: { value: '2026-11-13' } });
  fireEvent.click(screen.getByText('Apply history filters')); await screen.findByText('From date must not follow to date.'); expect(api).toHaveBeenCalledTimes(1);
  fireEvent.change(screen.getByLabelText('From date (UTC)'), { target: { value: '2026-11-12' } }); fireEvent.click(screen.getByText('Apply history filters'));
  await screen.findByRole('alert'); expect(screen.getByText('Solar Partner')).toBeTruthy();
});
it('shows an explicit empty result and disables both page controls', async () => {
  vi.mocked(marketplaceApi).mockResolvedValue({ ...page, items: [], totalElements: 0, totalPages: 0 });
  render(<OrderHistory mode="online"/>); fireEvent.click(screen.getByText('Load order history')); await screen.findByText('No orders found');
  expect((screen.getByText('Previous orders') as HTMLButtonElement).disabled).toBe(true); expect((screen.getByText('Next orders') as HTMLButtonElement).disabled).toBe(true);
});
it('reports receipt permission failures without discarding history', async () => {
  vi.mocked(api).mockResolvedValueOnce(page).mockRejectedValueOnce(new Error('Forbidden'));
  render(<OrderHistory mode="pos" onReceipt={vi.fn()}/>); fireEvent.click(screen.getByText('Load order history')); fireEvent.click(await screen.findByText('Open receipt 4'));
  await screen.findByText(/Receipt-view permission is required/); expect(screen.getByText('Solar Partner')).toBeTruthy();
});
